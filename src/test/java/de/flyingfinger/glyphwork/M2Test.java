package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.core.Settings;
import de.flyingfinger.glyphwork.demo.Demo;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2: settings (hosting, URL template) and the fallback dialog model. */
class M2Test {

    private static Settings settings(String yaml) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return Settings.read(c);
    }

    @Test
    void urlTemplate() throws Exception {
        assertEquals("https://cdn.example.org/packs/abc.zip",
            settings("pack: {url: 'https://cdn.example.org/packs/{sha1}.zip', hosting: external}").url("abc"));
        assertTrue(settings("pack: {hosting: external}").external());
        // without placeholder the hash is appended
        assertEquals("https://x.org/p/abc.zip", settings("pack: {url: 'https://x.org/p'}").url("abc"));
        // config from M0/M1: pack-server.public-url
        assertEquals("http://1.2.3.4:8765/abc.zip", settings("pack-server: {public-url: 'http://1.2.3.4:8765/'}").url("abc"));
        Settings d = settings("");
        assertFalse(d.external());
        assertEquals("http://127.0.0.1:8765/abc.zip", d.url("abc"));
        assertTrue(d.fallback());
        assertEquals(256, d.maxHeight());
    }

    /** Minimal Click for running fallback actions. */
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
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { onSubmit.accept("berry"); }
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void fallbackOfTheListsScene() {
        Demo demo = new Demo();
        TestClick view = new TestClick(demo.lists);
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(demo.lists, view);
        List<String> labels = m.actions().stream().map(a -> plain(a.label())).toList();
        // search button, 4 fruits, "next" for the fruits, 12 numbers (tooltips), "next" for the numbers, back
        assertEquals("Search...", labels.getFirst());
        assertTrue(labels.containsAll(List.of("Apple", "Apricot", "Banana", "Blackberry", "#1", "#12", "< Back")), labels.toString());
        assertEquals(2, labels.stream().filter(l -> l.startsWith("next")).count());
        assertTrue(m.lines().stream().map(M2Test::plain).anyMatch(l -> l.startsWith("Fruit:")));

        // clicking a fruit selects it and re-renders; the selection shows up as "▶ "
        m.actions().get(1).onClick().accept(view);
        assertEquals("Apple", view.state.get("fruit"));
        assertEquals(1, view.reopened);
        List<String> again = new FallbackRenderer(Logger.getLogger("test")).render(demo.lists, view).actions().stream()
            .map(a -> plain(a.label())).toList();
        assertTrue(again.contains("▶ Apple"), again.toString());

        // paging
        m.actions().stream().filter(a -> plain(a.label()).startsWith("next")).findFirst().orElseThrow().onClick().accept(view);
        assertEquals(1, view.state.get("glyphwork:page:fruits"));
    }

    @Test
    void fallbackOfTheMainScene() {
        Demo demo = new Demo();
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(demo.main, new TestClick(demo.main));
        List<String> labels = m.actions().stream().map(a -> plain(a.label())).toList();
        assertEquals(List.of("x", "Sound: OFF", "+1", "#1", "#2", "#3", "#4", "#5", "#6",
            "#7", "#8", "Lists >"), labels);   // panels (background, L shape) are left out
        // dark label colors are lightened for the dark dialog background
        assertTrue(m.lines().stream().allMatch(l -> {
            TextColor c = l.color();
            return c != null && (0.299 * c.red() + 0.587 * c.green() + 0.114 * c.blue()) / 255 >= 0.45;
        }));
    }
}
