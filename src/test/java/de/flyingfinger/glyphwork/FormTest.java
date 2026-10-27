package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Form;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.dialog.FormValues;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forms: several native inputs at once (API, checked values, YAML forms with menu variables). */
class FormTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static Player fakePlayer(List<String> log) {
        World w = (World) Proxy.newProxyInstance(FormTest.class.getClassLoader(), new Class<?>[] {World.class},
            (p, m, a) -> m.getName().equals("getName") ? "world" : null);
        return (Player) Proxy.newProxyInstance(FormTest.class.getClassLoader(), new Class<?>[] {Player.class},
            (p, m, a) -> switch (m.getName()) {
                case "getName" -> "Steve";
                case "getWorld" -> w;
                case "hasPermission" -> false;
                case "sendMessage" -> { log.add("msg " + plain((Component) a[0])); yield null; }
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == a[0];
                default -> null;
            });
    }

    /** A click that answers forms with fixed values. */
    private static final class TestClick implements Click {
        final Scene scene;
        final Player player;
        final Map<String, Object> state = new HashMap<>();
        final List<String> log;
        Form shown;
        Form.Values answer;

        TestClick(Scene scene, List<String> log) {
            this.scene = scene;
            this.log = log;
            this.player = fakePlayer(log);
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
        @Override public void form(Form form, Consumer<Form.Values> onSubmit) {
            log.add("form " + plain(form.title()));
            shown = form;
            if (answer != null) {
                onSubmit.accept(answer);
            }
        }
    }

    @Test
    void builderChecksKeysAndRanges() {
        Component l = Component.text("x");
        assertThrows(IllegalArgumentException.class, () -> Form.builder(l).text("bad-key", l, ""));
        assertThrows(IllegalArgumentException.class, () -> Form.builder(l).checkbox("a", l, true).checkbox("a", l, false));
        assertThrows(IllegalArgumentException.class, () -> Form.builder(l).slider("a", l, 5, 1, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> Form.builder(l).build());
        Form f = Form.builder(l).slider("a", l, 1, 10, 1, 50)
            .choice("c", l, "nope", Form.option("x", l), Form.option("y", l)).build();
        assertEquals(10f, ((Form.SliderField) f.fields().get(0)).initial());          // clamped
        assertEquals("x", ((Form.ChoiceField) f.fields().get(1)).initial());         // unknown -> first
    }

    @Test
    void valuesFromTheClientAreChecked() {
        Component l = Component.text("x");
        Form f = Form.builder(l)
            .text("name", l, "Steve", 5, 1)
            .checkbox("on", l, true)
            .slider("n", l, 0, 10, 2, 4)
            .choice("mode", l, "b", Form.option("a", l), Form.option("b", l))
            .build();
        Map<String, String> texts = Map.of("name", "Alexander", "mode", "hacked");
        Map<String, Float> numbers = Map.of("n", 7.3f);
        FormValues v = FormValues.read(f, texts::get, k -> null, numbers::get);
        assertEquals("Alexa", v.text("name"));         // cut to max length
        assertTrue(v.checked("on"));                    // missing -> initial
        assertEquals(8f, v.number("n"));                // on the step grid
        assertEquals("b", v.choice("mode"));            // unknown id -> initial
        assertEquals(10f, FormValues.read(f, k -> null, k -> null, k -> 99f).number("n"));
        assertEquals("12", FormValues.format(12f));
        assertEquals("2.5", FormValues.format(2.5f));
    }

    private static final String MENU = """
        background: none
        variables: { name: Steve, weite: "8", partikel: "true", modus: normal }
        forms:
          einstellungen:
            title: "Einstellungen von %player%"
            ok: Speichern
            fields:
              - { type: text, var: name, label: "Name", max-length: 16 }
              - { type: slider, var: weite, label: "Weite", min: 1, max: 32 }
              - { type: checkbox, var: partikel, label: "Partikel" }
              - { type: choice, var: modus, label: "Modus", options: { leicht: Leicht, normal: Normal, schwer: Schwer } }
            actions: ["message: Gespeichert: %menu_name% %menu_weite% %menu_partikel% %menu_modus%"]
        elements:
          - { type: label, x: 0, y: 0, text: "%menu_name% / %menu_weite% / %menu_partikel% / %menu_modus%" }
          - { type: button, x: 0, y: 10, text: Ändern, actions: ["form: einstellungen"] }
        """;

    @Test
    void yamlFormStartsWithTheVariablesAndWritesThemBack() throws MenuException {
        Scene s = new Menus().parse("t", MENU).scene();
        List<String> log = new ArrayList<>();
        TestClick c = new TestClick(s, log);
        Label status = (Label) s.elements().get(0);
        assertEquals("Steve / 8 / true / normal", status.text().apply(c));

        // what the player enters ("%" and a leading "/" are removed: values go into commands)
        Component l = Component.text("x");
        Form probe = Form.builder(l).text("f0", l, "", 16, 1).slider("f1", l, 1, 32, 1, 1)
            .checkbox("f2", l, false).choice("f3", l, "leicht", Form.option("leicht", l), Form.option("schwer", l)).build();
        c.answer = FormValues.read(probe, Map.of("f0", "/Alex%x", "f3", "schwer")::get, k -> false, k -> 20f);

        ((Button) s.elements().get(1)).onClick().accept(c);
        Form shown = c.shown;
        assertEquals("Einstellungen von Steve", plain(shown.title()));
        assertEquals("Speichern", plain(shown.ok()));
        assertEquals("Steve", ((Form.TextField) shown.fields().get(0)).initial());
        assertEquals(8f, ((Form.SliderField) shown.fields().get(1)).initial());
        assertTrue(((Form.CheckboxField) shown.fields().get(2)).initial());
        assertEquals("normal", ((Form.ChoiceField) shown.fields().get(3)).initial());
        assertEquals(List.of("Leicht", "Normal", "Schwer"),
            ((Form.ChoiceField) shown.fields().get(3)).options().stream().map(o -> plain(o.text())).toList());

        assertEquals("Alexx / 20 / false / schwer", status.text().apply(c));
        assertEquals(List.of("form Einstellungen von Steve", "msg Gespeichert: Alexx 20 false schwer"), log);
    }

    @Test
    void formMistakesAreReportedWhenLoading() {
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "elements: [{ type: button, x: 0, y: 0, actions: ['form: gibtsnicht'] }]\n"));
        String nested = """
            forms:
              a: { fields: [{ type: checkbox, var: x }], actions: ["form: a"] }
            elements: []
            """;
        MenuException e = assertThrows(MenuException.class, () -> new Menus().parse("t", nested));
        assertTrue(e.getMessage().contains("cannot open another form"), e.getMessage());
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "forms: { a: { fields: [{ type: farbe, var: x }] } }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "forms: { a: { fields: [{ type: slider, var: x, min: 5, max: 1 }] } }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("t",
            "forms: { a: { fields: [{ type: checkbox, var: x }, { type: text, var: x }] } }\nelements: []\n"));
        assertThrows(MenuException.class, () -> new Menus().parse("t", "forms: { a: { fields: [] } }\nelements: []\n"));
    }

    @Test
    void clickWithoutFormSupportSaysSo() {
        Click bare = new Click() {
            @Override public Player player() { return null; }
            @Override public Scene scene() { return null; }
            @Override public <T> T get(String key, T fallback) { return fallback; }
            @Override public void set(String key, Object value) { }
            @Override public void reopen() { }
            @Override public void open(Scene next) { }
            @Override public void back() { }
            @Override public void close() { }
            @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
        };
        Form f = Form.builder(Component.text("t")).checkbox("a", Component.text("a"), true).build();
        assertInstanceOf(UnsupportedOperationException.class,
            assertThrows(RuntimeException.class, () -> bare.form(f, v -> { })));
        assertFalse(f.fields().isEmpty());
    }
}
