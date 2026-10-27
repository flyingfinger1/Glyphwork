package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.Scene;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stack counts on icons (like in the inventory) and scenes that redraw themselves. */
class CountRefreshTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final IconCatalog icons = IconCatalog.load(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"), icons);

    private static Player fakePlayer() {
        World w = (World) Proxy.newProxyInstance(CountRefreshTest.class.getClassLoader(), new Class<?>[] {World.class},
            (p, m, a) -> m.getName().equals("getName") ? "world" : null);
        return (Player) Proxy.newProxyInstance(CountRefreshTest.class.getClassLoader(), new Class<?>[] {Player.class},
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
        @Override public void reopen() { }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    private Component render(Scene scene) {
        LineBuilder line = renderer.render(scene, new TestClick(scene), (name, action) -> ClickEvent.runCommand(name));
        return line.build(scene.height());
    }

    private Layout layout(Component message) {
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        return new Layout(message, sheets, text, icons);
    }

    /** Text components (with their shadow) whose content is exactly {@code s}. */
    private static List<TextComponent> find(Component c, String s, List<TextComponent> out) {
        if (c instanceof TextComponent tc && tc.content().equals(s)) {
            out.add(tc);
        }
        for (Component child : c.children()) {
            find(child, s, out);
        }
        return out;
    }

    @Test
    void countSitsAtTheBottomRightWithAShadow() {
        Scene s = Scene.builder("t", 100, 60).add(Icon.of(10, 20, "diamond").count(64)).build();
        Component msg = render(s);
        Layout l = layout(msg);
        assertEquals(1, l.icons.size());
        // like the inventory: right edge (with the 1 px gap) at x + 17, top at y + 9
        int width = text.width("64");
        assertEquals(2, l.textRuns.size());
        assertEquals(10 + 17 - width, l.textRuns.get(0)[0]);
        assertEquals(29, l.textRuns.get(0)[1]);
        assertEquals(0xFFFFFF, l.textRuns.get(0)[2 + 1]);
        List<TextComponent> found = find(msg, "64", new ArrayList<>());
        assertEquals(1, found.size());
        assertNotNull(found.get(0).shadowColor());
    }

    @Test
    void countOfOneOrEmptyShowsNothing() {
        Scene s = Scene.builder("t", 100, 60)
            .add(Icon.of(10, 20, "diamond").count(1))
            .add(Icon.of(40, 20, "diamond").count(v -> ""))
            .add(Icon.of(70, 20, "unknown_item_xyz").count(5))   // no icon: no count either
            .build();
        Layout l = layout(render(s));
        assertTrue(l.textRuns.isEmpty(), "text drawn: " + l.textRuns.size());
    }

    @Test
    void gridCellsShowTheirCount() {
        Scene s = Scene.builder("t", 100, 60)
            .add(Grid.of("g", 0, 0, 3, 1, v -> List.of("a", "b", "c"))
                .cell(26, 24)
                .icon(t -> "diamond")
                .count(t -> t.equals("b") ? "16" : null))
            .build();
        Layout l = layout(render(s));
        assertEquals(3, l.icons.size());
        assertEquals(2, l.textRuns.size());
        // cell b at x 26, icon centered in the 24 px cell (+4)
        assertEquals(26 + 4 + 17 - text.width("16"), l.textRuns.get(0)[0]);
        assertEquals(4 + 9, l.textRuns.get(0)[1]);
    }

    @Test
    void listCountsStayInsideTheirRow() {
        Scene s = Scene.builder("t", 120, 60)
            .add(de.flyingfinger.glyphwork.api.ListView.of("l", 0, 0, 100, 2, v -> List.of("a", "b"))
                .icon(t -> "diamond")
                .count(t -> "12"))
            .build();
        Layout l = layout(render(s));
        // rows of 16 px: count top at 8 (not 9), so with its shadow it ends at 15, inside the row
        List<int[]> digits = l.textRuns.stream().filter(r -> r[3] == 0xFFFFFF && r[0] < 22).toList();
        assertEquals(4, digits.size());
        assertEquals(8, digits.get(0)[1]);
        assertEquals(16 + 8, digits.get(2)[1]);
    }

    @Test
    void fallbackNamesTheCount() {
        Scene s = Scene.builder("t", 100, 60).add(Icon.of(10, 20, "diamond").count(64).onClick(c -> { })).build();
        FallbackRenderer.Model m = new FallbackRenderer(Logger.getLogger("test")).render(s, new TestClick(s));
        assertEquals("Diamond ×64", PlainTextComponentSerializer.plainText().serialize(m.actions().get(0).label()));
    }

    @Test
    void yamlCountWithPlaceholdersAndRefresh() throws MenuException {
        Menus menus = new Menus();
        menus.sources(src -> List.of(Map.of("", "world", "players", "3", "icon", "grass_block"),
            Map.of("", "world_nether", "players", "1", "icon", "netherrack")));
        Scene s = menus.parse("t", """
            background: none
            refresh: 2.5
            elements:
              - { type: icon, x: 0, y: 0, item: diamond, count: 32 }
              - type: grid
                x: 0
                y: 20
                cols: 2
                rows: 1
                items-from: worlds
                item: { text: "", icon: "%item_icon%", count: "%item_players%" }
            """).scene();
        assertEquals(Duration.ofMillis(2500), s.refresh());
        Layout l = layout(render(s));
        assertEquals(3, l.icons.size());
        // "32" and "3" (the nether's "1" is not shown)
        assertEquals(3, l.textRuns.size());
    }

    @Test
    void refreshNeedsAtLeastOneSecond() {
        MenuException e = assertThrows(MenuException.class, () -> new Menus().parse("t", "refresh: 0.5\nelements: []\n"));
        assertTrue(e.getMessage().contains("refresh"), e.getMessage());
        assertThrows(MenuException.class, () -> new Menus().parse("t", "refresh: oft\nelements: []\n"));
        assertNull(Scene.builder("t", 10, 10).build().refresh());
        assertThrows(IllegalArgumentException.class, () -> Scene.builder("t", 10, 10).refresh(Duration.ofMillis(100)));
    }
}
