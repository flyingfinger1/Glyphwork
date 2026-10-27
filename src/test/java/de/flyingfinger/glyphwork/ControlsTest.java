package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Checkbox;
import de.flyingfinger.glyphwork.api.Choice;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.Slider;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slider, checkbox and choice drawn from the skin, in the API, YAML and the plain dialog. */
class ControlsTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"));

    private static Player fakePlayer() {
        World w = (World) Proxy.newProxyInstance(ControlsTest.class.getClassLoader(), new Class<?>[] {World.class},
            (p, m, a) -> m.getName().equals("getName") ? "world" : null);
        return (Player) Proxy.newProxyInstance(ControlsTest.class.getClassLoader(), new Class<?>[] {Player.class},
            (p, m, a) -> switch (m.getName()) {
                case "getName" -> "Steve";
                case "getWorld" -> w;
                case "hasPermission" -> false;
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == a[0];
                default -> null;
            });
    }

    private static final class TestClick implements Click {
        final Scene scene;
        final Player player = fakePlayer();
        final Map<String, Object> state = new HashMap<>();
        final List<String> log = new ArrayList<>();

        TestClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return player; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) {
            Object v = state.get(key);
            return v == null ? fallback : (T) v;
        }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { log.add("reopen"); }
        @Override public void open(Scene next) { log.add("open " + next.id()); }
        @Override public void back() { log.add("back"); }
        @Override public void close() { log.add("close"); }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    /** Renders and keeps every bound action by its name. */
    private final Map<String, Consumer<Click>> bound = new HashMap<>();

    private Layout layout(Scene scene, TestClick view) {
        LineBuilder line = renderer.render(scene, view, (name, action) -> {
            bound.put(name, action);
            return ClickEvent.runCommand(name);
        });
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        return new Layout(line.build(scene.height()), sheets, text);
    }

    private Component message(Scene scene, TestClick view) {
        return renderer.render(scene, view, (name, action) -> ClickEvent.runCommand(name)).build(scene.height());
    }

    private static boolean contains(Component c, String s) {
        if (c instanceof TextComponent tc && tc.content().contains(s)) {
            return true;
        }
        return c.children().stream().anyMatch(ch -> contains(ch, s));
    }

    @Test
    void sliderClickPicksTheValueUnderTheCursor() {
        double[] value = {3};
        Scene s = Scene.builder("t", 120, 40)
            .add(Slider.of(0, 0, 96, 0, 10, 1).value(v -> value[0]).onChange((c, v) -> value[0] = v))
            .build();
        TestClick c = new TestClick(s);
        Layout l = layout(s, c);
        assertEquals("t#0:v0", l.hit(0, 4));
        assertEquals("t#0:v10", l.hit(95, 12));
        assertEquals("t#0:v5", l.hit(48, 8));
        // every column picks the position whose knob (8 px wide) would be centered nearest to it
        int previous = 0;
        for (int col = 0; col < 96; col++) {
            String h = l.hit(col + 0.5f, 8);
            int p = Integer.parseInt(h.substring(h.indexOf(":v") + 2));
            int expected = Math.max(0, Math.min(10, Math.round((col - 4 + 0.5f) / 88f * 10)));
            assertEquals(expected, p, "column " + col);
            assertTrue(p >= previous);
            previous = p;
        }
        bound.get("t#0:v7").accept(c);
        assertEquals(7.0, value[0]);
        assertEquals(List.of("reopen"), c.log);       // redrawn by itself
    }

    @Test
    void sliderOnlyRedrawsWhenTheHandlerDidNotNavigate() {
        Scene other = Scene.builder("other", 10, 10).build();
        Scene s = Scene.builder("t", 120, 40)
            .add(Slider.of(0, 0, 64, 0, 3, 1).onChange((c, v) -> c.open(other)))
            .build();
        TestClick c = new TestClick(s);
        layout(s, c);
        bound.get("t#0:v2").accept(c);
        assertEquals(List.of("open other"), c.log);
    }

    @Test
    void checkboxShowsAMarkAndToggles() {
        boolean[] on = {false};
        Scene s = Scene.builder("t", 120, 40)
            .add(Checkbox.of(4, 4).text("Partikel").checked(v -> on[0]).onChange((c, v) -> on[0] = v))
            .build();
        TestClick c = new TestClick(s);
        assertFalse(contains(message(s, c), "✔"));
        Layout l = layout(s, c);
        assertEquals("t#0", l.hit(10, 10));          // the box
        assertEquals("t#0", l.hit(40, 10));          // the text
        assertNull(l.hit(100, 10));
        bound.get("t#0").accept(c);
        assertTrue(on[0]);
        assertTrue(contains(message(s, c), "✔"));
    }

    @Test
    void choiceStepsBackAndForthAndWraps() {
        String[] mode = {"leicht"};
        Scene s = Scene.builder("t", 120, 40)
            .add(Choice.of(0, 0, 80, "leicht", "normal", "schwer").selected(v -> mode[0]).onChange((c, id) -> mode[0] = id))
            .build();
        TestClick c = new TestClick(s);
        Layout l = layout(s, c);
        assertEquals("t#0:prev", l.hit(10, 8));
        assertEquals("t#0:next", l.hit(70, 8));
        bound.get("t#0:prev").accept(c);
        assertEquals("schwer", mode[0]);              // wraps around
        bound.get("t#0:next").accept(c);
        assertEquals("leicht", mode[0]);
        assertTrue(contains(message(s, c), "leicht"));
    }

    private static final String MENU = """
        background: none
        variables: { weite: "8", partikel: "false", stufe: normal }
        elements:
          - { type: label, x: 0, y: 0, text: "%menu_weite% %menu_partikel% %menu_stufe%" }
          - { type: slider, x: 0, y: 10, width: 96, var: weite, min: 2, max: 32, step: 2, tooltip: "Weite %value%" }
          - { type: checkbox, x: 0, y: 30, var: partikel, text: Partikel, actions: ["set: stufe schwer"] }
          - { type: choice, x: 0, y: 50, width: 80, var: stufe, options: { leicht: "<green>Leicht", normal: Normal, schwer: Schwer } }
        """;

    @Test
    void yamlControlsWriteTheirVariables() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        TestClick c = new TestClick(s);
        Label status = (Label) s.elements().get(0);
        assertEquals("8 false normal", status.text().apply(c));
        layout(s, c);
        bound.get("menu:t#1:v15").accept(c);           // position 15 = 2 + 15 * 2
        assertEquals("32 false normal", status.text().apply(c));
        bound.get("menu:t#2").accept(c);                // checkbox, then its action sets stufe
        assertEquals("32 true schwer", status.text().apply(c));
        bound.get("menu:t#3:prev").accept(c);
        assertEquals("32 true normal", status.text().apply(c));
        assertEquals(List.of("reopen", "reopen", "reopen"), c.log);   // one redraw per change
        assertTrue(contains(message(s, c), "Normal"));
        assertFalse(contains(message(s, c), "<green>"));
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "elements: [{ type: slider, x: 0, y: 0, var: x, min: 5, max: 1 }]\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "elements: [{ type: checkbox, x: 0, y: 0 }]\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "elements: [{ type: choice, x: 0, y: 0, var: x }]\n"));
    }

    @Test
    void plainDialogOffersStepsAndToggles() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        TestClick c = new TestClick(s);
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(s, c);
        List<String> labels = m.actions().stream()
            .map(a -> PlainTextComponentSerializer.plainText().serialize(a.label())).toList();
        assertEquals(List.of("◀ Weite 6", "Weite 10 ▶", "☐ Partikel", "Normal  ▶"), labels);
        m.actions().get(1).onClick().accept(c);
        assertEquals("10 false normal", ((Label) s.elements().get(0)).text().apply(c));
    }
}
