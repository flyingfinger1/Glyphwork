package de.flyingfinger.glyphwork.menu;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * RSS and Atom feeds for {@code items-from: rss:<url>}. Loaded in the background and kept for
 * {@link #ttl}; until the first answer the list is empty and the menu asks to be drawn again.
 * Each entry has title, text (with paragraphs), summary (one line), date, link, author and feed
 * (the feed's own title).
 */
public final class Feeds {

    /** Values of each entry. */
    static final Set<String> KEYS = Set.of("", "title", "text", "summary", "date", "link", "author", "feed");

    private static final int MAX_BYTES = 1 << 20;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_TEXT = 300;
    /** The full text (%item_text%): paragraphs kept, for a detail view. */
    private static final int MAX_FULL = 4000;

    /** Settings from config.yml ({@code feeds:}). */
    private static volatile Duration ttl = Duration.ofMinutes(10);
    private static volatile DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static volatile Logger log = Logger.getLogger("Glyphwork");

    private static final Map<String, State> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private Feeds() {
    }

    /** What a menu gets: the entries so far, and whether the first load is still running. */
    public record Result(List<Map<String, String>> items, boolean loading) {
    }

    private static final class State {
        volatile @Nullable List<Map<String, String>> items;
        volatile long fetched;
        volatile boolean loading;
    }

    public static void configure(Duration cache, String datePattern, Logger logger) {
        ttl = cache;
        log = logger;
        try {
            dateFormat = DateTimeFormatter.ofPattern(datePattern);
        } catch (IllegalArgumentException e) {
            logger.warning("feeds.date-format '" + datePattern + "' is not valid: " + e.getMessage());
        }
    }

    /** Forgets every feed (reload). */
    public static void clear() {
        CACHE.clear();
    }

    /** Checks a feed address when the menu is loaded: http or https with a host. */
    static @Nullable String problem(String url) {
        try {
            URI u = URI.create(url);
            if (!"http".equalsIgnoreCase(u.getScheme()) && !"https".equalsIgnoreCase(u.getScheme())) {
                return "only http:// and https:// addresses work";
            }
            if (u.getHost() == null) {
                return "the address has no host";
            }
            return null;
        } catch (IllegalArgumentException e) {
            return "not a valid address (" + e.getMessage() + ")";
        }
    }

    /** The cached entries; starts loading when there are none or they are older than the cache time. */
    public static Result get(String url) {
        State s = CACHE.computeIfAbsent(url, k -> new State());
        long now = System.currentTimeMillis();
        List<Map<String, String>> items = s.items;
        if (!s.loading && (items == null || now - s.fetched > ttl.toMillis())) {
            load(url, s);
        }
        return new Result(items == null ? List.of() : items, items == null && s.loading);
    }

    private static synchronized void load(String url, State s) {
        if (s.loading) {
            return;
        }
        s.loading = true;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Glyphwork (Minecraft server menu)")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5")
            .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((resp, err) -> {
            List<Map<String, String>> items = List.of();
            try {
                if (err != null) {
                    throw err;
                }
                if (resp.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + resp.statusCode());
                }
                try (InputStream in = resp.body()) {
                    items = parse(readLimited(in), dateFormat);
                }
            } catch (Throwable e) {
                log.warning("Feed " + url + " could not be loaded: " + e.getMessage());
                List<Map<String, String>> old = s.items;
                if (old != null) {
                    items = old;                         // keep what we had, try again after the cache time
                }
            }
            s.items = items;
            s.fetched = System.currentTimeMillis();
            s.loading = false;
        });
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BYTES + 1);
        if (data.length > MAX_BYTES) {
            throw new IOException("larger than " + MAX_BYTES / 1024 + " KB");
        }
        return data;
    }

    /** RSS 2.0 (channel/item) or Atom (feed/entry), at most 50 entries. */
    static List<Map<String, String>> parse(byte[] xml, DateTimeFormatter dates) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // no DTDs, no external entities: a feed must not read files or other addresses
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(null);
        Document doc = b.parse(new ByteArrayInputStream(xml));
        Element root = doc.getDocumentElement();
        boolean atom = "feed".equals(root.getLocalName());
        String feedTitle = atom ? text(child(root, "title")) : text(child(child(root, "channel"), "title"));
        List<Element> entries = atom ? children(root, "entry") : children(child(root, "channel"), "item");
        if (!atom && entries.isEmpty()) {
            entries = children(root, "item");                       // RSS 1.0 (RDF): items next to the channel
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (Element e : entries) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            Map<String, String> m = new LinkedHashMap<>();
            String title = clean(text(child(e, "title")), MAX_TEXT);
            m.put("", title);
            m.put("title", title);
            String shortText = atom ? first(text(child(e, "summary")), text(child(e, "content")))
                : first(text(child(e, "description")), text(child(e, "encoded")));
            String longText = atom ? first(text(child(e, "content")), text(child(e, "summary")))
                : first(text(child(e, "encoded")), text(child(e, "description")));
            m.put("summary", clean(shortText, MAX_TEXT));
            m.put("text", paragraphs(longText, MAX_FULL));
            String date = atom ? first(text(child(e, "updated")), text(child(e, "published")))
                : first(text(child(e, "pubDate")), text(child(e, "date")));
            m.put("date", date(date, dates));
            m.put("link", atom ? atomLink(e) : text(child(e, "link")).trim());
            String author = atom ? text(child(child(e, "author"), "name")) : first(text(child(e, "author")), text(child(e, "creator")));
            m.put("author", clean(author, 80));
            m.put("feed", clean(feedTitle, 80));
            out.add(m);
        }
        return out;
    }

    private static String atomLink(Element e) {
        String any = "";
        for (Element l : children(e, "link")) {
            String rel = l.getAttribute("rel");
            if (rel.isEmpty() || "alternate".equals(rel)) {
                return l.getAttribute("href").trim();
            }
            if (any.isEmpty()) {
                any = l.getAttribute("href").trim();
            }
        }
        return any;
    }

    /** RFC 1123 (RSS) or ISO 8601 (Atom) in the server's time zone; unknown formats stay as they are. */
    static String date(String s, DateTimeFormatter out) {
        s = s.trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault()).format(out);
        } catch (DateTimeParseException ignored) {
            // not RSS style
        }
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).format(out);
        } catch (DateTimeParseException ignored) {
            return s;
        }
    }

    /** HTML tags removed, entities decoded, whitespace collapsed, at most max characters. */
    static String clean(String s, int max) {
        String t = entities(s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("(?s)<[^>]*>", " "))
            .replaceAll("\\s+", " ").trim();
        return cut(t, max);
    }

    /** Like {@link #clean}, but paragraphs, line breaks and list items stay as lines (blank line between paragraphs). */
    static String paragraphs(String s, int max) {
        String t = s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)<li[^>]*>", "\n• ")
            .replaceAll("(?i)</(p|div|h[1-6]|ul|ol|blockquote|pre|table|tr)>", "\n\n")
            .replaceAll("(?s)<[^>]*>", " ");
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : entities(t).split("\n")) {
            String l = line.replaceAll("[ \\t\\x0B\\f\\r\\u00A0]+", " ").trim();
            if (l.isEmpty()) {
                blank++;
                continue;
            }
            if (!out.isEmpty()) {
                out.append(blank > 0 ? "\n\n" : "\n");
            }
            out.append(l);
            blank = 0;
        }
        return cut(out.toString(), max);
    }

    private static String cut(String t, int max) {
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }

    private static final java.util.regex.Pattern ENTITY =
        java.util.regex.Pattern.compile("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});");

    /** The common named entities and all numeric ones (&amp;#8211; &amp;#x2019;). */
    static String entities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        java.util.regex.Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String r;
            try {
                r = switch (e) {
                    case "nbsp" -> " ";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "amp" -> "&";
                    case "ndash" -> "–";
                    case "mdash" -> "—";
                    case "hellip" -> "…";
                    case "laquo" -> "«";
                    case "raquo" -> "»";
                    case "bdquo" -> "„";
                    case "ldquo" -> "“";
                    case "rdquo" -> "”";
                    case "lsquo" -> "‘";
                    case "rsquo" -> "’";
                    case "auml" -> "ä";
                    case "ouml" -> "ö";
                    case "uuml" -> "ü";
                    case "Auml" -> "Ä";
                    case "Ouml" -> "Ö";
                    case "Uuml" -> "Ü";
                    case "szlig" -> "ß";
                    case "euro" -> "€";
                    default -> e.startsWith("#x") ? Character.toString(Integer.parseInt(e.substring(2), 16))
                        : e.startsWith("#") ? Character.toString(Integer.parseInt(e.substring(1))) : m.group();
                };
            } catch (IllegalArgumentException ex) {
                r = m.group();
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String first(String a, String b) {
        return a.isBlank() ? b : a;
    }

    private static @Nullable Element child(@Nullable Element e, String name) {
        if (e == null) {
            return null;
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && name.equals(c.getLocalName() == null ? c.getNodeName() : c.getLocalName())) {
                return c;
            }
        }
        return null;
    }

    private static List<Element> children(@Nullable Element e, String name) {
        List<Element> out = new ArrayList<>();
        if (e == null) {
            return out;
        }
        NodeList list = e.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i) instanceof Element c && name.equals(c.getLocalName() == null ? c.getNodeName() : c.getLocalName())) {
                out.add(c);
            }
        }
        return out;
    }

    private static String text(@Nullable Element e) {
        return e == null ? "" : e.getTextContent();
    }
}
