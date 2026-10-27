package de.flyingfinger.glyphwork.menu;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Scene;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RSS and Atom feeds as list entries, and the url: action. */
class FeedsTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static List<Map<String, String>> parse(String xml) throws Exception {
        return Feeds.parse(xml.getBytes(StandardCharsets.UTF_8), ISO);
    }

    @Test
    void rss() throws Exception {
        List<Map<String, String>> items = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Server News</title>
                <item>
                  <title>Update 1.2 &amp; more</title>
                  <link>https://example.org/news/12</link>
                  <description><![CDATA[<p>New <b>warps</b>&nbsp;and   shops.</p>]]></description>
                  <pubDate>Mon, 26 Oct 2026 18:30:00 GMT</pubDate>
                  <dc:creator>Admin</dc:creator>
                </item>
                <item><title>Second</title></item>
              </channel>
            </rss>
            """.strip());
        assertEquals(2, items.size());
        Map<String, String> a = items.getFirst();
        assertEquals("Update 1.2 & more", a.get("title"));
        assertEquals("Update 1.2 & more", a.get(""));
        assertEquals("https://example.org/news/12", a.get("link"));
        assertEquals("New warps and shops.", a.get("text"));
        assertTrue(a.get("date").startsWith("2026-10-2"), a.get("date"));
        assertEquals("Admin", a.get("author"));
        assertEquals("Server News", a.get("feed"));
        assertEquals("", items.get(1).get("link"));
    }

    @Test
    void atom() throws Exception {
        List<Map<String, String>> items = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Blog</title>
              <entry>
                <title type="html">Hello</title>
                <link rel="self" href="https://example.org/self"/>
                <link href="https://example.org/hello"/>
                <updated>2026-10-20T10:00:00Z</updated>
                <summary>Short text</summary>
                <author><name>Finn</name></author>
              </entry>
            </feed>
            """);
        Map<String, String> e = items.getFirst();
        assertEquals("Hello", e.get("title"));
        assertEquals("https://example.org/hello", e.get("link"));
        assertEquals("2026-10-20", e.get("date"));
        assertEquals("Short text", e.get("text"));
        assertEquals("Finn", e.get("author"));
        assertEquals("Blog", e.get("feed"));
    }

    @Test
    void noEntitiesFromOutside() {
        assertThrows(Exception.class, () -> parse("""
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>&x;</title></item></channel></rss>
            """.strip()));
    }

    @Test
    void longTextsAreCut() {
        String s = Feeds.clean("a".repeat(400), 300);
        assertEquals(300, s.length());
        assertTrue(s.endsWith("…"));
        assertEquals("unknown date", Feeds.date("unknown date", ISO));
    }

    @Test
    void addresses() {
        assertNull(Feeds.problem("https://example.org/feed.xml"));
        assertNotNull(Feeds.problem("file:///etc/passwd"));
        assertNotNull(Feeds.problem("ftp://example.org/x"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: list, x: 0, y: 0, items-from: \"rss:file:///x\" }\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: list, x: 0, y: 0, items-from: \"rss:https://e.org/f\", item: { text: \"%item_name%\" } }\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: button, x: 0, y: 0, actions: [\"url: javascript:alert(1)\"] }\n"));
    }

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();
        final List<String> log = new ArrayList<>();

        TestClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return null; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { log.add("reopen"); }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
        @Override public void openUrl(String url) { log.add("url " + url); }
        @Override public void redrawAfter(Duration delay) { log.add("redraw " + delay.toMillis()); }
    }

    @Test
    void feedListWithLinks() throws MenuException {
        Menus menus = new Menus();
        List<String> asked = new ArrayList<>();
        boolean[] loading = {true};
        menus.feeds(url -> {
            asked.add(url);
            return loading[0] ? new Feeds.Result(List.of(), true)
                : new Feeds.Result(List.of(Map.of("", "News", "title", "News", "link", "https://example.org/a",
                    "date", "", "text", "", "author", "", "feed", "")), false);
        });
        Scene s = menus.parse("t", """
            background: none
            elements:
              - type: list
                x: 0
                y: 0
                items-from: "rss:https://Example.org/Feed.xml"
                item: { text: "%item_title%", actions: ["url: %item_link%"] }
            """).scene();
        @SuppressWarnings("unchecked")
        ListView<Object> list = (ListView<Object>) s.elements().getFirst();
        TestClick c = new TestClick(s);
        assertTrue(list.items().apply(c).isEmpty());
        assertEquals(List.of("redraw 500"), c.log);                     // still loading: draw again soon
        assertEquals(List.of("https://Example.org/Feed.xml"), asked);    // the address keeps its case
        loading[0] = false;
        c.log.clear();
        List<Object> rows = list.items().apply(c);
        assertEquals("News", list.text().apply(rows.getFirst()));
        list.onSelect().accept(c, rows.getFirst());
        assertEquals(List.of("url https://example.org/a"), c.log);       // no redraw: the link dialog is shown
    }

    @Test
    void fullTextKeepsParagraphs() {
        assertEquals("First line\nsecond line\n\nNext paragraph – ok\n\n• one\n• two",
            Feeds.paragraphs("<p>First line<br/>second   line</p><p>Next paragraph &ndash; ok</p><ul><li>one</li><li>two</li></ul>", 4000));
        assertEquals("Grüße “x” é", Feeds.entities("Gr&uuml;&szlig;e &#8220;x&#x201D; &#233;"));
    }

    @Test
    void selectedIfAndOwnScrollPositions() throws MenuException {
        Menus menus = new Menus();
        menus.feeds(url -> new Feeds.Result(List.of(
            Map.of("", "A", "title", "A", "link", "https://e.org/a", "date", "", "text", "Text A", "summary", "", "author", "", "feed", ""),
            Map.of("", "B", "title", "B", "link", "https://e.org/b", "date", "", "text", "Text B", "summary", "", "author", "", "feed", "")), false));
        Scene s = menus.parse("t", """
            background: none
            elements:
              - type: list
                x: 0
                y: 0
                items-from: "rss:https://e.org/feed"
                item:
                  selected-if: "%menu_link% == %item_link%"
                  actions: ["set: link %item_link%", "set: text %item_text%"]
              - type: scroll
                id: "art-%menu_link%"
                x: 130
                y: 0
                height: 60
                elements:
                  - { type: label, x: 0, y: 0, text: "%menu_text%" }
            """).scene();
        @SuppressWarnings("unchecked")
        ListView<Object> list = (ListView<Object>) s.elements().getFirst();
        de.flyingfinger.glyphwork.api.Scroll pane = (de.flyingfinger.glyphwork.api.Scroll) s.elements().get(1);
        TestClick c = new TestClick(s);
        List<Object> rows = list.items().apply(c);
        assertTrue(rows.stream().noneMatch(r -> list.isSelected(c, r)));
        list.onSelect().accept(c, rows.get(1));
        rows = list.items().apply(c);
        assertTrue(!list.isSelected(c, rows.get(0)) && list.isSelected(c, rows.get(1)));
        assertEquals("glyphwork:scroll:art-https://e.org/b", pane.key(c));
    }
}
