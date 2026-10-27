package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Scene;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A line break in data (chat messages, web values) must not break the text lines of the menu. */
class OneLineTextTest {

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();

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
    }

    private static long newlines(String message) throws MenuException {
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = Theme.builtin(alloc);
        Sheet hit = Sheet.invisible(alloc);
        SceneRenderer r = new SceneRenderer(VanillaText.load(), theme, hit.glyph(), 256, Logger.getLogger("test"));
        Menus menus = new Menus();
        menus.sources(src -> List.of(Map.of("", message, "name", message, "world", "w", "uuid", "1", "icon", "", "head", "")));
        Scene s = menus.parse("t", """
            background: none
            elements:
              - { type: list, x: 0, y: 0, width: 120, rows: 2, items-from: online-players }
              - { type: button, x: 0, y: 60, width: 80, text: "Close" }
            """).scene();
        Component c = r.render(s, new TestClick(s), (name, action) -> ClickEvent.runCommand(name)).build(s.height());
        return PlainTextComponentSerializer.plainText().serialize(c).chars().filter(ch -> ch == '\n').count();
    }

    @Test
    void lineBreaksInEntriesStayOnTheirLine() throws MenuException {
        assertEquals(newlines("Und so weiter"), newlines("Und\nso\r\nweiter"));
    }
}
