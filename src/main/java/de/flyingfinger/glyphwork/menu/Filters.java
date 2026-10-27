package de.flyingfinger.glyphwork.menu;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters after a placeholder name: {@code %item_timestamp|date%}, {@code %item_content|discord%}.
 * Unknown filters leave the value as it is.
 */
final class Filters {

    private static volatile DateTimeFormatter dates = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Filters() {
    }

    static void dateFormat(DateTimeFormatter f) {
        dates = f;
    }

    static String apply(String filter, String value) {
        return switch (filter) {
            case "date" -> date(value, dates);
            case "discord" -> discord(value, dates, true);
            case "discord-plain" -> discord(value, dates, false);
            default -> value;
        };
    }

    /** ISO 8601, RFC 1123 or a Unix time (seconds or milliseconds), in the server's time zone. */
    static String date(String s, DateTimeFormatter out) {
        String t = s.trim();
        if (t.matches("\\d{9,13}")) {
            long n = Long.parseLong(t);
            Instant i = t.length() >= 12 ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
            return i.atZone(ZoneId.systemDefault()).format(out);
        }
        return Feeds.date(t, out);
    }

    private static final Pattern TIME = Pattern.compile("<t:(\\d{1,12})(?::[tTdDfFR])?>");
    private static final Pattern EMOJI = Pattern.compile("<a?:([A-Za-z0-9_~]+):\\d+>");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]+)]\\((?:<)?https?://[^)\\s]+(?:>)?\\)");

    /** Characters Discord escapes with '\', kept out of the way while markdown is converted. */
    private static final String ESCAPABLE = "*_~`|>#\\";
    private static final char HIDDEN = '';

    /**
     * Discord message markup made readable: mentions, custom emojis and timestamps as text; with
     * {@code markup} bold, italics, underline and strikethrough become MiniMessage tags (and '<' of
     * the message is escaped, so a message cannot format the menu), otherwise the markers go.
     * Spoilers, code, headings (bold), quotes and masked links lose their markers.
     */
    static String discord(String s, DateTimeFormatter out, boolean markup) {
        String t = s;
        StringBuilder hidden = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            int k = i + 1 < t.length() ? ESCAPABLE.indexOf(t.charAt(i + 1)) : -1;
            if (c == '\\' && k >= 0) {
                hidden.append((char) (HIDDEN + k));
                i++;
            } else {
                hidden.append(c);
            }
        }
        t = hidden.toString();
        Matcher m = TIME.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(date(m.group(1), out)));
        }
        m.appendTail(sb);
        t = sb.toString();
        t = t.replaceAll("<@!?\\d+>", "@user")
            .replaceAll("<@&\\d+>", "@role")
            .replaceAll("<#\\d+>", "#channel")
            .replaceAll("</([\\w -]+):\\d+>", "/$1");
        t = EMOJI.matcher(t).replaceAll(":$1:");
        t = LINK.matcher(t).replaceAll("$1");
        if (markup) {
            t = t.replace("<", "\\<");                       // the message's own '<' is text, not a tag
        }
        String b = markup ? "<b>$1</b>" : "$1";
        String i = markup ? "<i>$1</i>" : "$1";
        String u = markup ? "<u>$1</u>" : "$1";
        String st = markup ? "<st>$1</st>" : "$1";
        t = t.replaceAll("(?s)```[a-zA-Z0-9]*\\n?(.*?)```", "$1")
            .replaceAll("`([^`\\n]+)`", "$1")
            .replaceAll("\\|\\|(.+?)\\|\\|", "$1")
            .replaceAll("\\*\\*(.+?)\\*\\*", b)
            .replaceAll("__(.+?)__", u)
            .replaceAll("~~(.+?)~~", st)
            .replaceAll("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])", i)
            .replaceAll("(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])", i)
            .replaceAll("(?m)^#{1,3} +(.*)$", b)
            .replaceAll("(?m)^-# +", "")
            .replaceAll("(?m)^>>> ?", "")
            .replaceAll("(?m)^> ?", "");
        StringBuilder back = new StringBuilder();
        for (char c : t.toCharArray()) {
            int k = c - HIDDEN;
            back.append(k >= 0 && k < ESCAPABLE.length() ? (markup && ESCAPABLE.charAt(k) == '\\' ? "\\\\" : String.valueOf(ESCAPABLE.charAt(k))) : String.valueOf(c));
        }
        return back.toString().trim();
    }
}
