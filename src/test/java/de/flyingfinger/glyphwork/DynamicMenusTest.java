package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** YAML menus: menu variables (set, toggle, %menu_x%) and dynamic lists (items-from). */
class DynamicMenusTest {

    private static Player fakePlayer(String name, List<String> log) {
        World w = (World) Proxy.newProxyInstance(DynamicMenusTest.class.getClassLoader(), new Class<?>[] {World.class},
            (p, m, a) -> m.getName().equals("getName") ? "world" : null);
        return (Player) Proxy.newProxyInstance(DynamicMenusTest.class.getClassLoader(), new Class<?>[] {Player.class},
            (p, m, a) -> switch (m.getName()) {
                case "getName" -> name;
                case "getWorld" -> w;
                case "hasPermission" -> false;
                case "performCommand" -> { log.add("cmd " + a[0]); yield true; }
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == a[0];
                default -> null;
            });
    }

    /** A click that keeps state like a real session and logs navigation. */
    private static final class TestClick implements Click {
        final Scene scene;
        final Player player;
        final Map<String, Object> state = new HashMap<>();
        final List<String> log;

        TestClick(Scene scene, String name, List<String> log) {
            this.scene = scene;
            this.player = fakePlayer(name, log);
            this.log = log;
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
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { log.add("prompt"); }
    }

    private static Button button(Scene s, int i) {
        return (Button) s.elements().get(i);
    }

    private static final String TABS = """
        background: none
        variables: { tab: shop, sound: "false" }
        elements:
          - { type: label, x: 0, y: 0, text: "Tab: %menu_tab%, Sound: %menu_sound%" }      # 0
          - { type: button, x: 0, y: 10, text: Shop, actions: ["set: tab shop"] }           # 1
          - { type: button, x: 0, y: 30, text: Info, actions: ["set: tab info"] }           # 2
          - { type: label, x: 0, y: 50, text: Waren, show-if: "%menu_tab% == shop" }        # 3
          - { type: label, x: 0, y: 50, text: Regeln, show-if: "%menu_tab% == info" }       # 4
          - { type: button, x: 0, y: 70, actions: ["toggle: sound"] }                      # 5
          - { type: button, x: 0, y: 90, actions: ["set: tab"] }                           # 6: back to the default
          - { type: button, x: 0, y: 110, actions: ["set: tab weg", "close"] }             # 7
        """;

    @Test
    void variablesHaveDefaultsAndSetRedraws() throws MenuException {
        Scene s = new Menus().parse("t", TABS).scene();
        List<String> log = new ArrayList<>();
        TestClick c = new TestClick(s, "Steve", log);
        Label status = (Label) s.elements().get(0);
        assertEquals("Tab: shop, Sound: false", status.text().apply(c));
        assertTrue(s.elements().get(3).visible().apply(c));
        assertFalse(s.elements().get(4).visible().apply(c));

        button(s, 2).onClick().accept(c);
        assertEquals("Tab: info, Sound: false", status.text().apply(c));
        assertFalse(s.elements().get(3).visible().apply(c));
        assertTrue(s.elements().get(4).visible().apply(c));
        assertEquals(List.of("reopen"), log);                      // set redraws by itself

        button(s, 5).onClick().accept(c);
        button(s, 5).onClick().accept(c);
        button(s, 5).onClick().accept(c);
        assertEquals("Tab: info, Sound: true", status.text().apply(c));

        button(s, 6).onClick().accept(c);
        assertEquals("Tab: shop, Sound: true", status.text().apply(c));

        log.clear();
        button(s, 7).onClick().accept(c);
        assertEquals(List.of("close"), log);                       // no redraw after close
    }

    @Test
    void dynamicListsFillFilterAndAct() throws MenuException {
        Menus menus = new Menus();
        menus.sources(src -> src.equals("online-players")
            ? List.of(Map.of("", "Alex", "name", "Alex", "world", "farm", "uuid", "1", "icon", ""),
                      Map.of("", "Steve", "name", "Steve", "world", "world", "uuid", "2", "icon", ""),
                      Map.of("", "Zoe", "name", "Zoe", "world", "world", "uuid", "3", "icon", ""))
            : List.of());
        Scene s = menus.parse("t", """
            background: none
            variables: { filter: "" }
            elements:
              - type: list
                x: 0
                y: 0
                items:
                  - { text: "Alle", actions: ["set: filter"] }
                items-from: online-players
                item:
                  text: "%item% (%item_world%)"
                  show-if: ["%item% != %player%", "%item% contains %menu_filter%"]
                  actions: ["player: tpa %item%", "set: filter %item%"]
            """).scene();
        @SuppressWarnings("unchecked")
        ListView<Object> list = (ListView<Object>) s.elements().get(0);
        List<String> log = new ArrayList<>();
        TestClick c = new TestClick(s, "Steve", log);

        List<Object> rows = list.items().apply(c);
        assertEquals(List.of("Alle", "Alex (farm)", "Zoe (world)"), rows.stream().map(r -> list.text().apply(r)).toList());

        list.onSelect().accept(c, rows.get(1));                    // Alex
        assertEquals(List.of("cmd tpa Alex", "reopen"), log);
        rows = list.items().apply(c);                              // filter = Alex now
        assertEquals(List.of("Alle", "Alex (farm)"), rows.stream().map(r -> list.text().apply(r)).toList());

        list.onSelect().accept(c, rows.get(0));                    // "Alle" resets the filter
        assertEquals(3, list.items().apply(c).size());
    }

    @Test
    void worldsHaveIcons() throws MenuException {
        Menus menus = new Menus();
        menus.sources(src -> List.of(Map.of("", "world_nether", "name", "world_nether", "players", "2",
            "environment", "nether", "icon", "netherrack")));
        Scene s = menus.parse("t", """
            elements:
              - { type: grid, x: 0, y: 0, items-from: worlds, item: { icon: "%item_icon%", tooltip: "%item_players% online" } }
            """).scene();
        @SuppressWarnings("unchecked")
        de.flyingfinger.glyphwork.api.Grid<Object> g = (de.flyingfinger.glyphwork.api.Grid<Object>) s.elements().get(1);
        Object cell = g.items().apply(new TestClick(s, "x", new ArrayList<>())).getFirst();
        assertEquals("netherrack", g.icon().apply(cell));
        assertEquals("world_nether", g.label().apply(cell));         // text defaults to %item%
    }

    @Test
    void errorsNameTheProblem() {
        Menus m = new Menus();
        assertError(m, "elements:\n  - { type: list, x: 0, y: 0, items-from: mobs }\n",
            "t.yml.elements[0].items-from: unknown source 'mobs' (online-players, worlds)");
        assertError(m, "elements:\n  - { type: list, x: 0, y: 0, items-from: worlds, item: { text: \"%item_wrold%\" } }\n",
            "t.yml.elements[0].item: %item_wrold% does not exist for worlds");
        assertError(m, "elements:\n  - { type: list, x: 0, y: 0, item: { text: a } }\n",
            "t.yml.elements[0].item: only together with items-from");
        assertError(m, "elements:\n  - { type: button, x: 0, y: 0, actions: [\"set: mein.tab x\"] }\n",
            "t.yml.elements[0].actions[0]: variable name expected");
        assertError(m, "variables: { \"a b\": 1 }\n", "t.yml.variables.a b: variable names use");
    }

    private static void assertError(Menus m, String yaml, String expectedStart) {
        MenuException e = assertThrows(MenuException.class, () -> m.parse("t", yaml));
        assertTrue(e.getMessage().startsWith(expectedStart), e.getMessage());
    }
}
