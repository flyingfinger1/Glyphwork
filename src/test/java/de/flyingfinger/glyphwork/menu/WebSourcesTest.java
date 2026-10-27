package de.flyingfinger.glyphwork.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Scene;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JSON web sources: paths, placeholders, lists. */
class WebSourcesTest {

    private static final JsonElement STATUS = JsonParser.parseString("""
        {"online": true, "players": {"online": 42, "max": 100.0,
          "list": [{"name": "Alex", "uuid": "a1"}, {"name": "Steve", "uuid": "s2"}]},
         "motd": {"clean": ["Hello", "World"]}, "version": "26.3", "ratio": 0.25}
        """);

    private boolean loading;

    @BeforeEach
    void setUp() {
        WebSources.configure(Map.of("status", new WebSources.Source("status", "https://e.org/s", Duration.ofMinutes(1), Map.of())));
        WebSources.loader(s -> loading ? new Web.Result<>(null, true) : new Web.Result<>(STATUS, false));
    }

    @AfterEach
    void tearDown() {
        WebSources.configure(Map.of());
    }

    @Test
    void paths() {
        assertEquals("42", WebSources.format(WebSources.at(STATUS, "players.online")));
        assertEquals("100", WebSources.format(WebSources.at(STATUS, "players.max")));      // no ".0"
        assertEquals("0.25", WebSources.format(WebSources.at(STATUS, "ratio")));
        assertEquals("Steve", WebSources.format(WebSources.at(STATUS, "players.list.1.name")));
        assertEquals("2", WebSources.format(WebSources.at(STATUS, "players.list.length")));
        assertEquals("true", WebSources.format(WebSources.at(STATUS, "online")));
        assertEquals("[\"Hello\",\"World\"]", WebSources.format(WebSources.at(STATUS, "motd.clean")));
        assertNull(WebSources.at(STATUS, "players.nope.x"));
        assertNull(WebSources.at(STATUS, "players.list.7"));
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
        @Override public void reopen() { }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
        @Override public void redrawAfter(Duration delay) { log.add("redraw " + delay.toMillis()); }
    }

    @Test
    void placeholdersAndLists() throws MenuException {
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - { type: label, x: 0, y: 0, text: "%web_status_players.online%/%web_status_players.max% – %web_status_version% %web_other_x%" }
              - type: list
                x: 0
                y: 20
                items-from: "web:status.players.list"
                item: { text: "%item_name% (%item_uuid%)" }
            """).scene();
        Label label = (Label) s.elements().get(0);
        @SuppressWarnings("unchecked")
        ListView<Object> list = (ListView<Object>) s.elements().get(1);
        TestClick c = new TestClick(s);
        loading = true;
        assertEquals("/ –  %web_other_x%", label.text().apply(c));      // unknown sources stay visible
        assertEquals(List.of("redraw 500"), c.log.subList(0, 1));
        loading = false;
        assertEquals("42/100 – 26.3 %web_other_x%", label.text().apply(c));
        assertEquals(List.of("Alex (a1)", "Steve (s2)"),
            list.items().apply(c).stream().map(r -> list.text().apply(r)).toList());
    }

    @Test
    void unknownListSource() {
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: list, x: 0, y: 0, items-from: \"web:nope.list\" }\n"));
    }

    @Test
    void flattenNested() {
        Map<String, String> m = WebSources.flatten(JsonParser.parseString("""
            {"user": {"name": "Finn", "tags": ["a", "b"]}, "n": 3}
            """));
        assertEquals("Finn", m.get("user.name"));
        assertEquals("b", m.get("user.tags.1"));
        assertEquals("2", m.get("user.tags.length"));
        assertEquals("3", m.get("n"));
        assertEquals("", m.get(""));
        assertEquals("", m.get("embeds.0.title"));                 // missing in this entry: empty
    }

    @Test
    void hintsForRefusedRequests() {
        String d = "https://discord.com/api/v10/channels/1/messages";
        assertEquals(" (Discord needs the header Authorization: \"Bot <token>\")", Web.hint(401, d, Map.of("Authorization", "abc")));
        assertEquals(" (the bot may not read this channel: View Channels + Read Message History)",
            Web.hint(403, d, Map.of("Authorization", "Bot abc")));
        assertEquals(" (no Authorization header set)", Web.hint(401, "https://e.org/x", Map.of()));
        assertEquals("", Web.hint(404, d, Map.of()));
    }
}
