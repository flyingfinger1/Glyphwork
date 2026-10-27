package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
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

/** Menu variables kept in the player's data, and cooldowns. */
class PersistCooldownTest {

    /** A player with a map as persistent data (survives "sessions" = new TestClicks). */
    private static final class FakePlayer {
        final Map<NamespacedKey, Object> data = new HashMap<>();
        final List<String> log = new ArrayList<>();
        final Player player;

        FakePlayer() {
            PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class}, (p, m, a) -> switch (m.getName()) {
                    case "get" -> data.get((NamespacedKey) a[0]);
                    case "set" -> { data.put((NamespacedKey) a[0], a[2]); yield null; }
                    case "remove" -> { data.remove((NamespacedKey) a[0]); yield null; }
                    case "has" -> data.containsKey((NamespacedKey) a[0]);
                    default -> null;
                });
            World w = (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {World.class},
                (p, m, a) -> m.getName().equals("getName") ? "world" : null);
            player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getName" -> "Steve";
                    case "getWorld" -> w;
                    case "getPersistentDataContainer" -> pdc;
                    case "hasPermission" -> true;
                    case "sendMessage" -> { log.add(PlainTextComponentSerializer.plainText().serialize((Component) a[0])); yield null; }
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    default -> null;
                });
        }
    }

    /** One menu session: own state, shared player data. */
    private static final class TestClick implements Click {
        final Scene scene;
        final FakePlayer who;
        final Map<String, Object> state = new HashMap<>();
        int reopened;

        TestClick(Scene scene, FakePlayer who) {
            this.scene = scene;
            this.who = who;
        }

        @Override public Player player() { return who.player; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) {
            Object v = state.get(key);
            return v == null ? fallback : (T) v;
        }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { reopened++; }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
        final List<Long> redraws = new ArrayList<>();
        @Override public void redrawAfter(java.time.Duration delay) { redraws.add(delay.toMillis()); }
    }

    @Test
    void runningCooldownAsksForRedraws() throws MenuException {
        Scene s = new Menus().parse("shop", REWARD).scene();
        FakePlayer p = new FakePlayer();
        TestClick c = new TestClick(s, p);
        Button reward = (Button) s.elements().get(0);
        assertTrue(reward.enabled().apply(c));
        assertTrue(c.redraws.isEmpty());                            // nothing running: no redraw
        reward.onClick().accept(c);
        reward.enabled().apply(c);                                  // 1 h left: redraw at the next minute
        long next = c.redraws.getLast();
        assertTrue(next > 50 && next <= 60_050, "next " + next);
        // 30 s left: every second
        p.data.replaceAll((k, v) -> System.currentTimeMillis() + 30_000);
        reward.enabled().apply(c);
        assertTrue(c.redraws.getLast() <= 1050, "last " + c.redraws.getLast());
    }

    @Test
    void persistedVariablesSurviveANewSession() throws MenuException {
        Scene s = new Menus().parse("profil", """
            background: none
            variables: { nick: Steve, tab: start }
            persist: [nick]
            elements:
              - { type: label, x: 0, y: 0, text: "%menu_nick% %menu_tab%" }
              - { type: button, x: 0, y: 10, actions: ["set: nick Alex", "set: tab zwei"] }
              - { type: button, x: 0, y: 30, actions: ["set: nick"] }
            """).scene();
        FakePlayer p = new FakePlayer();
        TestClick first = new TestClick(s, p);
        Label status = (Label) s.elements().get(0);
        ((Button) s.elements().get(1)).onClick().accept(first);
        assertEquals("Alex zwei", status.text().apply(first));
        TestClick again = new TestClick(s, p);                    // logged out and back in
        assertEquals("Alex start", status.text().apply(again));   // nick was kept, tab was not
        ((Button) s.elements().get(2)).onClick().accept(again);   // set: nick -> back to the default, also saved
        assertEquals("Steve start", status.text().apply(new TestClick(s, p)));
        assertTrue(p.data.isEmpty());
    }

    @Test
    void persistAllAndMistakes() throws MenuException {
        Scene s = new Menus().parse("a", """
            background: none
            variables: { x: "1" }
            persist: true
            elements:
              - { type: button, x: 0, y: 0, actions: ["toggle: x"] }
            """).scene();
        FakePlayer p = new FakePlayer();
        ((Button) s.elements().getFirst()).onClick().accept(new TestClick(s, p));
        assertEquals(1, p.data.size());
        assertThrows(MenuException.class, () -> new Menus().parse("a", "variables: { x: 1 }\npersist: [y]\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a", "persist: true\nelements: []\n"));
    }

    private static final String REWARD = """
        background: none
        variables: { online: "3" }
        elements:
          - { type: button, x: 0, y: 0, text: Belohnung, cooldown: 1h, actions: ["message: Diamant!"] }
          - { type: button, x: 0, y: 20, text: Streng, cooldown: 10s, actions: ["require: %menu_online% >= 5 | message: zu wenige", "message: geschafft"] }
          - { type: button, x: 0, y: 40, text: A, cooldown: 1d, cooldown-key: tagesbonus, actions: ["message: A"] }
          - { type: button, x: 0, y: 60, text: B, cooldown: 1d, cooldown-key: tagesbonus, actions: ["message: B"] }
        """;

    @Test
    void cooldownGraysTheButtonAndBlocksAgain() throws MenuException {
        Scene s = new Menus().parse("shop", REWARD).scene();
        FakePlayer p = new FakePlayer();
        TestClick c = new TestClick(s, p);
        Button reward = (Button) s.elements().get(0);
        assertTrue(reward.enabled().apply(c));
        reward.onClick().accept(c);
        assertEquals(List.of("Diamant!"), p.log);
        assertEquals(1, c.reopened);                                // drawn again: now grayed out
        assertFalse(reward.enabled().apply(c));
        assertEquals("Available again in 1 h", PlainTextComponentSerializer.plainText().serialize(reward.tooltip().apply(c)));
        reward.onClick().accept(new TestClick(s, p));               // relogging does not help
        assertEquals("Not yet: available again in 1 h.", p.log.get(1));
        // the time runs out
        p.data.replaceAll((k, v) -> 1L);
        assertTrue(reward.enabled().apply(c));
    }

    @Test
    void failedRequireUsesNothingUpAndKeysAreShared() throws MenuException {
        Scene s = new Menus().parse("shop", REWARD).scene();
        FakePlayer p = new FakePlayer();
        TestClick c = new TestClick(s, p);
        Button strict = (Button) s.elements().get(1);
        strict.onClick().accept(c);
        assertEquals(List.of("zu wenige"), p.log);
        assertTrue(strict.enabled().apply(c));                     // require failed: no cooldown
        ((Button) s.elements().get(2)).onClick().accept(c);
        assertFalse(((Button) s.elements().get(3)).enabled().apply(c));   // same cooldown-key
    }

    @Test
    void ownCooldownTexts() throws MenuException {
        Scene s = new Menus().parse("shop", """
            background: none
            elements:
              - type: button
                x: 0
                y: 0
                cooldown: 2h
                cooldown-tooltip: "<gold>Morgen wieder, %player%! (%cooldown%)"
                cooldown-message: "<red>Geduld: %cooldown%"
                actions: ["message: ok"]
            """).scene();
        FakePlayer p = new FakePlayer();
        TestClick c = new TestClick(s, p);
        Button b = (Button) s.elements().getFirst();
        b.onClick().accept(c);
        assertEquals("Morgen wieder, Steve! (2 h)", PlainTextComponentSerializer.plainText().serialize(b.tooltip().apply(c)));
        b.onClick().accept(c);
        assertEquals(List.of("ok", "Geduld: 2 h"), p.log);
    }

    @Test
    void addCounts() throws MenuException {
        Scene s = new Menus().parse("z", """
            background: none
            variables: { n: "0", schritt: "5", preis: "2.5" }
            persist: [n]
            elements:
              - { type: label, x: 0, y: 0, text: "%menu_n% %menu_preis%" }
              - { type: button, x: 0, y: 10, actions: ["add: n 1"] }
              - { type: button, x: 0, y: 30, actions: ["add: n -1 min 0"] }
              - { type: button, x: 0, y: 50, actions: ["add: n %menu_schritt% max 12"] }
              - { type: button, x: 0, y: 70, actions: ["add: preis 0.1"] }
            """).scene();
        FakePlayer p = new FakePlayer();
        TestClick c = new TestClick(s, p);
        Label l = (Label) s.elements().get(0);
        Consumer<Integer> press = i -> ((Button) s.elements().get(i)).onClick().accept(c);
        press.accept(1);
        press.accept(1);
        assertEquals("2 2.5", l.text().apply(c));
        press.accept(2);
        press.accept(2);
        press.accept(2);
        assertEquals("0 2.5", l.text().apply(c));            // 2 - 3, but not below 0
        press.accept(3);
        press.accept(3);
        press.accept(3);
        assertEquals("12 2.5", l.text().apply(c));           // 0 + 5 + 5 + 5, capped at 12
        press.accept(4);
        assertEquals("12 2.6", l.text().apply(c));           // no 2.6000000000000001
        assertEquals("12", l.text().apply(new TestClick(s, p)).split(" ")[0]);   // saved
        assertEquals(9, c.reopened);                          // every add redraws (9 clicks)
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, actions: ['add: n viel'] }]\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, actions: ['add: n 1 bis 5'] }]\n"));
    }

    @Test
    void durations() {
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, cooldown: bald }]\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, cooldown: 0s }]\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, cooldown-key: x }]\n"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new Menus().parse("a",
            "elements: [{ type: button, x: 0, y: 0, cooldown: 1d 12h }, { type: hitbox, x: 0, y: 0, width: 8, height: 8, cooldown: 90 }]\n"));
    }
}
