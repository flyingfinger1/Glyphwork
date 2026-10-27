package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.Scroll;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scroll panes: YAML, clipping, the scroll bar. */
class ScrollTest {

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();
        int reopened;

        TestClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return null; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { reopened++; }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    /** Ten rows A..J, 18 px apart, in a 80 px window: A..D fit at first. */
    private static String menu() {
        StringBuilder sb = new StringBuilder("""
            background: none
            height: 120
            elements:
              - type: scroll
                id: liste
                x: 10
                y: 20
                width: 100
                height: 80
                elements:
            """);
        for (int i = 0; i < 10; i++) {
            sb.append("          - { type: label, x: 2, y: ").append(i * 18 + 2).append(", text: Row").append((char) ('A' + i)).append(" }\n");
        }
        sb.append("  - { type: label, x: 0, y: 108, text: Footer }\n");
        return sb.toString();
    }

    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final SceneRenderer renderer = new SceneRenderer(VanillaText.load(), theme, hit.glyph(), 256, Logger.getLogger("test"));
    private final Map<String, Consumer<Click>> bound = new HashMap<>();

    private String draw(Scene s, TestClick c) {
        bound.clear();
        Component msg = renderer.render(s, c, (name, action) -> {
            bound.put(name, action);
            return ClickEvent.runCommand(name);
        }).build(s.height());
        return PlainTextComponentSerializer.plainText().serialize(msg);
    }

    private static List<String> rows(String text) {
        List<String> out = new ArrayList<>();
        for (char ch = 'A'; ch <= 'J'; ch++) {
            if (text.contains("Row" + ch)) {
                out.add(String.valueOf(ch));
            }
        }
        return out;
    }

    @Test
    void positionsAreRelativeToThePane() throws MenuException {
        Scene s = new Menus().parse("t", menu()).scene();
        Scroll sc = (Scroll) s.elements().getFirst();
        Label first = (Label) sc.elements().getFirst();
        assertEquals(12, first.x());
        assertEquals(22, first.y());
        assertEquals(18, sc.step());
    }

    @Test
    void onlyRowsInsideTheWindowAreDrawnAndTheBarScrolls() throws MenuException {
        Scene s = new Menus().parse("t", menu()).scene();
        TestClick c = new TestClick(s);
        String text = draw(s, c);
        assertEquals(List.of("A", "B", "C", "D"), rows(text));        // E (74..82) sticks out
        assertTrue(text.contains("Footer"));                         // outside the pane: untouched
        assertFalse(bound.keySet().stream().anyMatch(k -> k.endsWith(":up")));
        String down = bound.keySet().stream().filter(k -> k.endsWith(":down")).findFirst().orElseThrow();
        bound.get(down).accept(c);
        assertEquals(18, c.state.get("glyphwork:scroll:liste"));
        assertEquals(1, c.reopened);
        assertEquals(List.of("B", "C", "D", "E"), rows(draw(s, c)));
        assertTrue(bound.keySet().stream().anyMatch(k -> k.endsWith(":up")));
        // the end: J (164..172) is the last row; the position stays within the content
        c.state.put("glyphwork:scroll:liste", 999);
        assertEquals(List.of("G", "H", "I", "J"), rows(draw(s, c)));     // last position 108
        assertFalse(bound.keySet().stream().anyMatch(k -> k.endsWith(":down")));
        // a click on the top of the track goes back to the start
        String start = bound.keySet().stream().filter(k -> k.endsWith(":to0")).findFirst().orElseThrow();
        bound.get(start).accept(c);
        assertEquals(0, c.state.get("glyphwork:scroll:liste"));
    }

    @Test
    void shortContentHasNoBar() throws MenuException {
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - type: scroll
                x: 0
                y: 0
                height: 60
                elements:
                  - { type: label, x: 0, y: 0, text: Kurz }
            """).scene();
        draw(s, new TestClick(s));
        assertTrue(bound.isEmpty(), bound.keySet().toString());
    }

    @Test
    void mistakes() {
        assertThrows(MenuException.class, () -> new Menus().parse("a", """
            elements:
              - type: scroll
                x: 0
                y: 0
                elements:
                  - { type: scroll, x: 0, y: 0, elements: [] }
            """));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: scroll, x: 0, y: 0, step: 0, elements: [] }\n"));
    }
}
