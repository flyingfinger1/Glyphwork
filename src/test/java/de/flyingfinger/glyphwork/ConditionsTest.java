package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** YAML menus: show-if, enabled-if, disabled-tooltip and the require action. */
class ConditionsTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"));

    /** A player named {@code name} in world {@code world}; records commands and messages. */
    private static Player player(String name, String world, Set<String> perms, List<String> log) {
        World w = (World) Proxy.newProxyInstance(ConditionsTest.class.getClassLoader(), new Class<?>[] {World.class},
            (p, m, a) -> m.getName().equals("getName") ? world : null);
        return (Player) Proxy.newProxyInstance(ConditionsTest.class.getClassLoader(), new Class<?>[] {Player.class},
            (p, m, a) -> switch (m.getName()) {
                case "getName" -> name;
                case "getWorld" -> w;
                case "hasPermission" -> perms.contains(String.valueOf(a[0]));
                case "performCommand" -> { log.add("cmd " + a[0]); yield true; }
                case "sendMessage" -> {
                    log.add("msg " + PlainTextComponentSerializer.plainText().serialize((Component) a[0]));
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == a[0];
                default -> null;
            });
    }

    private static final class TestClick implements Click {
        final Scene scene;
        final Player player;
        final Map<String, Object> state = new HashMap<>();

        TestClick(Scene scene, Player player) {
            this.scene = scene;
            this.player = player;
        }

        @Override public Player player() { return player; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    private Layout layout(Scene scene, Player player) {
        View view = new TestClick(scene, player);
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        return new Layout(renderer.render(scene, view, (name, action) -> ClickEvent.runCommand(name)).build(scene.height()),
            sheets, text);
    }

    private static final String MENU = """
        background: none
        elements:
          - type: button        # 0: only in the lobby
            x: 8
            y: 8
            text: Lobby
            show-if: "%world% == lobby"
          - type: button        # 1: only for Steve, and only with the permission
            x: 8
            y: 32
            text: Steve
            show-if: "%player% == steve"
            enabled-if: ["%player% contains ev", "%world% != nether"]
            disabled-tooltip: "<red>Nicht in der Nether"
            tooltip: Hallo
          - type: list          # 2: rows filtered per viewer
            x: 80
            y: 8
            width: 80
            rows: 3
            items:
              - text: Alle
              - text: Nur Lobby
                show-if: "%world% == lobby"
              - text: Nur Alex
                show-if: "%player% == Alex"
        """;

    @Test
    void showIfHidesElementsAndRows() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        Layout steve = layout(s, player("Steve", "lobby", Set.of(), new ArrayList<>()));
        assertEquals("menu:t#0", steve.hit(20, 12));
        assertEquals("menu:t#1", steve.hit(20, 36));
        assertEquals("menu:t#2:row1", steve.hit(100, 8 + 16 + 8));      // "Nur Lobby" is the second row
        assertNull(steve.hit(100, 8 + 32 + 8));                          // no third row for Steve

        Layout alex = layout(s, player("Alex", "farm", Set.of(), new ArrayList<>()));
        assertNull(alex.hit(20, 12));                                     // not in the lobby: hidden
        assertNull(alex.hit(20, 36));                                     // not Steve: hidden
        assertEquals("menu:t#2:row1", alex.hit(100, 8 + 16 + 8));       // "Nur Alex" moved up
    }

    @Test
    void enabledIfGraysOutAndSwapsTheTooltip() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        Button b = (Button) s.elements().get(1);
        View lobby = new TestClick(s, player("Steve", "lobby", Set.of(), new ArrayList<>()));
        View nether = new TestClick(s, player("Steve", "nether", Set.of(), new ArrayList<>()));
        assertTrue(b.enabled().apply(lobby));
        assertFalse(b.enabled().apply(nether));
        assertEquals("Hallo", PlainTextComponentSerializer.plainText().serialize(b.tooltip().apply(lobby)));
        assertEquals("Nicht in der Nether", PlainTextComponentSerializer.plainText().serialize(b.tooltip().apply(nether)));
        assertNull(layout(s, player("Steve", "nether", Set.of(), new ArrayList<>())).hit(20, 36)); // shown, not clickable
    }

    @Test
    void hiddenElementsAreLeftOutOfTheFallbackToo() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        FallbackRenderer f = new FallbackRenderer(Logger.getLogger("test"));
        int steve = f.render(s, new TestClick(s, player("Steve", "lobby", Set.of(), new ArrayList<>()))).actions().size();
        int alex = f.render(s, new TestClick(s, player("Alex", "farm", Set.of(), new ArrayList<>()))).actions().size();
        assertEquals(steve - 2, alex);                                     // two buttons hidden, rows: 2 each
    }

    @Test
    void requireStopsTheRemainingActions() throws MenuException {
        Scene s = new Menus().parse("t", """
            elements:
              - type: button
                x: 8
                y: 8
                actions:
                  - "require: %world% == lobby | message: Nur in der Lobby"
                  - "player: spawn"
                  - "require: '%player%' != 'Steve'"
                  - "player: never-for-steve"
            """).scene();
        Button b = (Button) s.elements().get(1);

        List<String> farm = new ArrayList<>();
        b.onClick().accept(new TestClick(s, player("Steve", "farm", Set.of(), farm)));
        assertEquals(List.of("msg Nur in der Lobby"), farm);

        List<String> lobby = new ArrayList<>();
        b.onClick().accept(new TestClick(s, player("Steve", "lobby", Set.of(), lobby)));
        assertEquals(List.of("cmd spawn"), lobby);                       // the second require stops silently

        List<String> alex = new ArrayList<>();
        b.onClick().accept(new TestClick(s, player("Alex", "lobby", Set.of(), alex)));
        assertEquals(List.of("cmd spawn", "cmd never-for-steve"), alex);
    }

    @Test
    void numbersCompareAsNumbers() throws MenuException {
        // "%world%" stands in for a PlaceholderAPI value here
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - { type: button, x: 0,  y: 0, show-if: "%world% >= 100" }
              - { type: button, x: 0, y: 20, show-if: "%world% < 9.5" }
              - { type: button, x: 0, y: 40, show-if: "%world% == 1234.5" }
            """).scene();
        View big = new TestClick(s, player("x", "1,234.50", Set.of(), new ArrayList<>()));
        View small = new TestClick(s, player("x", "9", Set.of(), new ArrayList<>()));
        assertTrue(s.elements().get(0).visible().apply(big));               // "1,234.50" is a number
        assertFalse(s.elements().get(1).visible().apply(big));
        assertTrue(s.elements().get(2).visible().apply(big));
        assertFalse(s.elements().get(0).visible().apply(small));            // 9 < 100 (not as text: "9" > "100")
        assertTrue(s.elements().get(1).visible().apply(small));
    }

    @Test
    void conditionErrorsSayWhere() {
        Menus m = new Menus();
        MenuException e = assertThrows(MenuException.class, () -> m.parse("t",
            "elements:\n  - type: button\n    x: 1\n    y: 2\n    show-if: \"%world%>=3\"\n"));
        assertTrue(e.getMessage().startsWith("t.yml.elements[0].show-if: condition like"), e.getMessage());
        e = assertThrows(MenuException.class, () -> m.parse("t",
            "elements:\n  - type: button\n    x: 1\n    y: 2\n    actions: [\"require: ok | teleport: x\"]\n"));
        assertTrue(e.getMessage().contains("condition like"), e.getMessage());
        e = assertThrows(MenuException.class, () -> m.parse("t",
            "elements:\n  - type: label\n    x: 1\n    y: 2\n    text: a\n    enabled-if: \"a == b\"\n"));
        assertTrue(e.getMessage().startsWith("t.yml.elements[0].enabled-if: unknown setting"), e.getMessage());
    }
}
