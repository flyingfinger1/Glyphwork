package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.Tabs;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tabbed panes: YAML, drawing, switching. */
class TabsTest {

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

    private static final String MENU = """
        background: none
        templates:
          zeile: { type: label, x: 0, text: "{text}" }
        elements:
          - type: tabs
            x: 10
            y: 20
            width: 120
            height: 80
            tabs:
              - id: shop
                title: Shop
                elements:
                  - { type: label, x: 4, y: 4, text: Angebote }
                  - { use: zeile, y: 20, text: Zeile }
              - id: info
                title: Info
                icon: book
                elements:
                  - { type: button, x: 4, y: 4, width: 60, text: Hilfe, actions: ["close"] }
          - { type: label, x: 0, y: 120, text: Fuss }
        """;

    @Test
    void childPositionsAreRelativeToThePanel() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        Tabs t = (Tabs) s.elements().getFirst();
        assertEquals(List.of("shop", "info"), t.tabs().stream().map(Tabs.Tab::id).toList());
        Label a = (Label) t.tabs().get(0).elements().get(0);
        assertEquals(14, a.x());
        assertEquals(20 + Tabs.HEADER + 4, a.y());
        Label z = (Label) t.tabs().get(0).elements().get(1);    // from a template
        assertEquals(10, z.x());
        assertEquals(20 + Tabs.HEADER + 20, z.y());
        assertEquals("book", t.tabs().get(1).icon());
    }

    @Test
    void clickingATabSwitchesAndDrawsAgain() throws MenuException {
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = Theme.builtin(alloc);
        Sheet hit = Sheet.invisible(alloc);
        SceneRenderer r = new SceneRenderer(VanillaText.load(), theme, hit.glyph(), 256, Logger.getLogger("test"));
        List<Sheet> sheets = new java.util.ArrayList<>(theme.sheets());
        sheets.add(hit);
        Scene s = new Menus().parse("t", MENU).scene();
        TestClick c = new TestClick(s);
        Map<String, Consumer<Click>> bound = new HashMap<>();
        SceneRenderer.Binder binder = (name, action) -> {
            bound.put(name, action);
            return ClickEvent.runCommand(name);
        };
        Layout l = new Layout(r.render(s, c, binder).build(s.height()), sheets, VanillaText.load());
        // the active tab has no click, the other one does
        assertNull(l.hit(30, 28));
        String info = l.hit(100, 28);
        assertTrue(info != null && info.endsWith(":tab:info"), info);
        assertNull(bound.keySet().stream().filter(k -> k.contains("/info#")).findAny().orElse(null));
        bound.get(info).accept(c);
        assertEquals(1, c.reopened);
        bound.clear();
        Layout l2 = new Layout(r.render(s, c, binder).build(s.height()), sheets, VanillaText.load());
        assertTrue(l2.hit(30, 28).endsWith(":tab:shop"));
        assertNull(l2.hit(100, 28));
        // the button of the info tab is there now
        assertTrue(bound.keySet().stream().anyMatch(k -> k.contains("/info#0")), bound.keySet().toString());
    }

    @Test
    void varKeepsTheActiveTab() throws MenuException {
        Scene s = new Menus().parse("t", """
            background: none
            variables: { seite: info }
            elements:
              - type: tabs
                x: 0
                y: 0
                var: seite
                tabs:
                  - { id: shop, elements: [] }
                  - { id: info, elements: [] }
              - { type: label, x: 0, y: 120, text: "{seite}" }
            """).scene();
        Tabs t = (Tabs) s.elements().getFirst();
        TestClick c = new TestClick(s);
        assertEquals("info", t.active(c).id());
        t.select(c, "shop");
        assertEquals("shop", t.active(c).id());
    }

    @Test
    void mistakes() {
        assertThrows(MenuException.class, () -> new Menus().parse("a", "elements:\n  - { type: tabs, x: 0, y: 0, tabs: [] }\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: tabs, x: 0, y: 0, tabs: [ { id: a }, { id: A } ] }\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements:\n  - { type: tabs, x: 0, y: 0, tabs: [ { id: a, farbe: rot } ] }\n"));
    }

    @Test
    void plainDialogShowsTabsApartAndMeasuresTheOthers() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        de.flyingfinger.glyphwork.render.FallbackRenderer.Model m =
            new de.flyingfinger.glyphwork.render.FallbackRenderer(Logger.getLogger("test")).render(s, new TestClick(s));
        assertEquals(List.of("Shop", "Info"), m.tabs().stream().map(de.flyingfinger.glyphwork.render.FallbackRenderer.Tab::title).toList());
        assertTrue(m.tabs().getFirst().active());
        // the tabs are no longer among the buttons; the info tab's "Hilfe" button is measured
        assertTrue(m.actions().stream().noneMatch(a -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(a.label()).contains("Info")));
        assertEquals(List.of("Hilfe"), m.measure().stream().map(c -> net.kyori.adventure.text.serializer.plain
            .PlainTextComponentSerializer.plainText().serialize(c)).toList());
    }
}
