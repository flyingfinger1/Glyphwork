package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hand-made plain dialog per menu, and grayed-out buttons in it. */
class PlainViewTest {

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
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static final String MENU = """
        variables: { seite: shop }
        elements:
          - { type: button, x: 0, y: 0, text: Pixel, actions: ["close"] }
        plain:
          columns: 1
          elements:
            - { type: label, text: "Du bist im %menu_seite%" }
            - { type: button, text: "Zu Info", actions: ["set: seite info"], show-if: "%menu_seite% == shop" }
            - { type: button, text: "Gesperrt", enabled-if: "%menu_seite% == nie", disabled-tooltip: "Geht nicht", actions: ["close"] }
        """;

    @Test
    void ownPlainViewReplacesTheDerivedOne() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        assertEquals(1, s.plainColumns());
        TestClick c = new TestClick(s);
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(s, c);
        assertEquals(List.of("Du bist im shop"), m.lines().stream().map(PlainViewTest::plain).toList());
        assertEquals(List.of("Zu Info", "Gesperrt"), m.actions().stream().map(a -> plain(a.label())).toList());
        // the same menu variables as the pixel GUI
        m.actions().getFirst().onClick().accept(c);
        m = new FallbackRenderer(Logger.getLogger("test")).render(s, c);
        assertEquals(List.of("Du bist im info"), m.lines().stream().map(PlainViewTest::plain).toList());
        assertEquals(List.of("Gesperrt"), m.actions().stream().map(a -> plain(a.label())).toList());
        // grayed out: shown, marked, its click does not run the actions
        assertTrue(m.actions().getFirst().disabled());
        assertEquals("Geht nicht", plain(m.actions().getFirst().tooltip()));
    }

    @Test
    void withoutPlainNothingChanges() throws MenuException {
        Scene s = new Menus().parse("t", "elements:\n  - { type: button, x: 0, y: 0, text: A, actions: [\"close\"] }\n").scene();
        assertNull(s.plain());
        assertEquals(0, s.plainColumns());
    }

    @Test
    void shortFormAndMistakes() throws MenuException {
        Scene s = new Menus().parse("t", "elements: []\nplain:\n  - { type: label, text: Hi }\n").scene();
        assertEquals(1, s.plain().size());
        assertThrows(MenuException.class, () -> new Menus().parse("t", "elements: []\nplain: { columns: 3, elements: [] }\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("t", "elements: []\nplain: hallo\n"));
    }
}
