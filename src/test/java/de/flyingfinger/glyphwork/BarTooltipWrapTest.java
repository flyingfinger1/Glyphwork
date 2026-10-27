package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Bar;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.font.FillSheets;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Progress bars, item tooltips and wrapped text. */
class BarTooltipWrapTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final IconCatalog icons = IconCatalog.load(alloc);
    private final FillSheets fills = new FillSheets(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"), icons)
        .fills(fills);

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();

        TestClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return null; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) {
            Object v = state.get(key);
            return v == null ? fallback : (T) v;
        }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    private Component render(Scene s) {
        return renderer.render(s, new TestClick(s), (name, action) -> ClickEvent.runCommand(name)).build(s.height());
    }

    private Layout layout(Component msg) {
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        sheets.addAll(fills.sheets());
        return new Layout(msg, sheets, text, icons);
    }

    private static int opaqueWidth(BufferedImage img) {
        for (int x = img.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < img.getHeight(); y++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    /** Fill pixels of a bar: the strips drawn in the fill color, as [x, width] runs. */
    private List<int[]> fill(Layout l, int rgb) {
        List<int[]> out = new ArrayList<>();
        for (Layout.Drawn d : l.drawn) {
            if (d.color() != null && d.color().value() == rgb) {
                out.add(new int[] {d.x(), opaqueWidth(d.img())});
            }
        }
        return out;
    }

    @Test
    void barFillsPixelExact() {
        TextColor red = TextColor.color(0xFF2020);
        Scene s = Scene.builder("t", 120, 40)
            .add(Bar.of(4, 8, 104).value(v -> 37).max(100).fill(red))
            .build();
        List<int[]> runs = fill(layout(render(s)), 0xFF2020);
        int total = runs.stream().mapToInt(r -> r[1]).sum();
        assertEquals(Math.round(0.37 * 102), total);                  // inner width = 104 - 2
        assertEquals(5, runs.getFirst()[0]);                          // 1 px inset
        int x = 5;
        for (int[] r : runs) {
            assertEquals(x, r[0]);                                    // strips follow each other without gaps
            x += r[1];
        }
        assertTrue(runs.size() <= 10);
    }

    @Test
    void barIsEmptyOrFullAtTheEnds() {
        Scene empty = Scene.builder("t", 120, 40).add(Bar.of(0, 0, 64).value(v -> -5).max(10)).build();
        assertEquals(0, fill(layout(render(empty)), Bar.GREEN.value()).size());
        Scene full = Scene.builder("t", 120, 40).add(Bar.of(0, 0, 64).height(16).value(v -> 999).max(10)).build();
        assertEquals(62, fill(layout(render(full)), Bar.GREEN.value()).stream().mapToInt(r -> r[1]).sum());
        assertEquals(0.0, Bar.ratio(5, 0));
    }

    @Test
    void yamlBarWithPlaceholdersAndPlainView() throws MenuException {
        Scene s = new Menus().parse("t", """
            background: none
            variables: { xp: "1,500", goal: "3000" }
            elements:
              - { type: bar, x: 0, y: 12, width: 82, value: "%menu_xp%", max: "%menu_goal%", fill: gold, text: "%menu_xp% / %menu_goal%" }
            """).scene();
        List<int[]> runs = fill(layout(render(s)), 0xFFAA00);
        assertEquals(39, runs.stream().mapToInt(r -> r[1]).sum());    // 82 px -> drawn 80, inner 78; half of it
        assertTrue(PlainTextComponentSerializer.plainText().serialize(render(s)).contains("1,500"));
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(s, new TestClick(s));
        String line = PlainTextComponentSerializer.plainText().serialize(m.lines().getFirst());
        assertEquals("█".repeat(5) + "█".repeat(5) + " 1,500 / 3000", line);
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "elements: [{ type: bar, x: 0, y: 0, value: 1, height: 12 }]\n"));
    }

    /** All hover events in the message. */
    private static List<HoverEvent<?>> hovers(Component c, List<HoverEvent<?>> out) {
        if (c.hoverEvent() != null) {
            out.add(c.hoverEvent());
        }
        c.children().forEach(ch -> hovers(ch, out));
        return out;
    }

    private static List<Key> shownItems(Component c) {
        return hovers(c, new ArrayList<>()).stream()
            .filter(h -> h.action() == HoverEvent.Action.SHOW_ITEM)
            .map(h -> ((HoverEvent.ShowItem) h.value()).item())
            .distinct().toList();
    }

    @Test
    void itemTooltipsOnlyForKnownVanillaItems() {
        Scene s = Scene.builder("t", 120, 60)
            .add(Icon.of(0, 0, "diamond_sword").itemTooltip(true))
            .add(Icon.of(20, 0, "minecraft:emerald").itemTooltip(true).tooltip(Component.text("text")))
            .add(Icon.of(40, 0, "head:Steve").itemTooltip(true))
            .add(Icon.of(60, 0, "not_an_item").itemTooltip(true))
            .add(Icon.of(80, 0, "apple").itemTooltip(v -> HoverEvent.showItem(Key.key("minecraft:nothing_like_this"), 1)))
            .add(Grid.of("g", 0, 20, 2, 1, v -> List.of("bread", "torch")).icon(t -> t).itemTooltip(true))
            .build();
        assertEquals(List.of(Key.key("minecraft:diamond_sword"), Key.key("minecraft:emerald"),
            Key.key("minecraft:bread"), Key.key("minecraft:torch")), shownItems(render(s)));
    }

    @Test
    void yamlItemTooltip() throws MenuException {
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - { type: icon, x: 0, y: 0, item: golden_apple, item-tooltip: true }
              - type: list
                x: 0
                y: 20
                item-tooltip: true
                items: [{ text: Stone, icon: stone }]
            """).scene();
        assertEquals(List.of(Key.key("minecraft:golden_apple"), Key.key("minecraft:stone")), shownItems(render(s)));
    }

    @Test
    void wrappedLabelsBreakAtSpaces() throws MenuException {
        LineBuilder lb = new LineBuilder(text, 256);
        List<String> lines = SceneRenderer.class.getName().isEmpty() ? null : wrap(lb, "Hallo Welt, dies ist ein langer Satz", 60);
        assertTrue(lines.size() > 1, lines.toString());
        for (String l : lines) {
            assertTrue(lb.visibleWidth(l) <= 60, l);
        }
        assertEquals("Hallo Welt, dies ist ein langer Satz", String.join(" ", lines));
        assertEquals(List.of("a", "", "b"), wrap(lb, "a\n\nb", 60));

        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - { type: label, x: 0, y: 0, width: 60, wrap: true, line-height: 12, text: "Hallo Welt, dies ist ein langer Satz" }
            """).scene();
        Layout l = layout(render(s));
        List<Integer> rows = l.textRuns.stream().map(r -> r[1]).distinct().sorted().toList();
        assertEquals(lines.size(), rows.size());
        assertEquals(List.of(0, 12, 24, 36).subList(0, rows.size()), rows);
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "elements: [{ type: label, x: 0, y: 0, wrap: true, text: a }]\n"));
    }

    @Test
    void newlinesWorkWithoutWrapToo() {
        Scene s = Scene.builder("t", 120, 40).add(Label.of(0, 0, "eins\nzwei")).build();
        Layout l = layout(render(s));
        assertEquals(List.of(0, 10), l.textRuns.stream().map(r -> r[1]).distinct().sorted().toList());
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(s, new TestClick(s));
        assertEquals(2, m.lines().size());
    }

    private static List<String> wrap(LineBuilder lb, String s, int w) {
        try {
            var mth = SceneRenderer.class.getDeclaredMethod("wrapText", LineBuilder.class, String.class, int.class);
            mth.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> r = (List<String>) mth.invoke(null, lb, s, w);
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
