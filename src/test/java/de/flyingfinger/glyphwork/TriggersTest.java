package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import net.kyori.adventure.text.Component;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Menus that open by themselves (open-on) and their on-open / on-close actions. */
class TriggersTest {

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();
        final List<String> log = new ArrayList<>();
        final Player player;

        TestClick(Scene scene) {
            this.scene = scene;
            World w = (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {World.class},
                (p, m, a) -> m.getName().equals("getName") ? "world" : null);
            this.player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getName" -> "Steve";
                    case "getWorld" -> w;
                    case "sendMessage" -> { log.add("msg " + PlainTextComponentSerializer.plainText().serialize((Component) a[0])); yield null; }
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    default -> null;
                });
        }

        @Override public Player player() { return player; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) {
            Object v = state.get(key);
            return v == null ? fallback : (T) v;
        }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { log.add("reopen"); }
        @Override public void open(Scene next) { log.add("open"); }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    @Test
    void openOnJoinAndItem() throws MenuException {
        Menus.Menu m = new Menus().parse("hub", """
            open-on:
              join: first
              item:
                material: minecraft:compass
                name: "<gold>Servermenü"
                lore: ["Rechtsklick"]
                slot: 4
            elements: []
            """);
        Menus.Trigger t = m.trigger();
        assertTrue(t.join());
        assertTrue(t.firstJoinOnly());
        assertEquals("compass", t.item().material());
        assertEquals("<gold>Servermenü", t.item().name());
        assertEquals(List.of("Rechtsklick"), t.item().lore());
        assertEquals(4, t.item().slot());
        assertEquals(Menus.Give.JOIN, t.item().give());          // defaults: hand out on every join, keep
        assertTrue(t.item().keep());

        Menus.Trigger shortForm = new Menus().parse("a", "open-on: { item: clock }\nelements: []\n").trigger();
        assertFalse(shortForm.join());
        assertEquals("clock", shortForm.item().material());
        assertEquals(8, shortForm.item().slot());
        assertNull(new Menus().parse("b", "open-on: { join: false }\nelements: []\n").trigger());
        Menus.MenuItem once = new Menus().parse("d",
            "open-on: { item: { material: clock, give: first, keep: false } }\nelements: []\n").trigger().item();
        assertEquals(Menus.Give.FIRST, once.give());
        assertFalse(once.keep());
        assertEquals(Menus.Give.NO, new Menus().parse("e",
            "open-on: { item: { material: clock, give: no } }\nelements: []\n").trigger().item().give());
        assertNull(new Menus().parse("c", "elements: []\n").trigger());
    }

    @Test
    void triggerMistakes() {
        assertThrows(MenuException.class, () -> new Menus().parse("a", "open-on: { join: manchmal }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "open-on: { item: { material: compass, slot: 9 } }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a", "open-on: { item: \"kein item\" }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a", "open-on: { kick: true }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "open-on: { item: { material: clock, give: manchmal } }\nelements: []\n"));
        // the give-item action is known (it hands out the item of a menu)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, actions: ['give-item: hub'] }]\n"));
    }

    @Test
    void onOpenSetsBeforeDrawingAndOnCloseRuns() throws MenuException {
        Scene s = new Menus().parse("t", """
            variables: { seite: 1 }
            on-open: ["set: seite 1", "message: Willkommen %player%"]
            on-close: ["message: Bis bald"]
            elements:
              - { type: label, x: 0, y: 0, text: "Seite %menu_seite%" }
            """).scene();
        TestClick c = new TestClick(s);
        c.state.put("menu:var:seite", "3");
        Label l = (Label) s.elements().get(1);
        assertEquals("Seite 3", l.text().apply(c));
        s.onOpen().accept(c);
        assertEquals("Seite 1", l.text().apply(c));
        s.onClose().accept(c);
        assertEquals(List.of("msg Willkommen Steve", "reopen", "msg Bis bald"), c.log);
    }
}
