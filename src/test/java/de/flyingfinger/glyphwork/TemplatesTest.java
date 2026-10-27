package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Element;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** YAML menu templates: shared _*.yml files, templates:, values, defaults, groups and errors. */
class TemplatesTest {

    private static final String SHARED = """
        warp-button:
          type: button
          width: 72
          text: "{name}"
          tint: "{farbe|gray}"
          tooltip: "Teleport nach {name}"
          actions: ["player: warp {ziel}", "close"]
        """;

    /** A view without a player: placeholders stay as they are. */
    private static final de.flyingfinger.glyphwork.api.View NOBODY = new de.flyingfinger.glyphwork.api.View() {
        public org.bukkit.entity.Player player() { return null; }
        public Scene scene() { return null; }
        public <T> T get(String key, T fallback) { return fallback; }
        public void set(String key, Object value) { }
    };

    private static Menus withShared() throws MenuException {
        Menus m = new Menus();
        m.addTemplates("_vorlagen.yml", SHARED);
        return m;
    }

    private static Button button(Scene s, int i) {
        return (Button) s.elements().get(i);
    }

    @Test
    void sharedTemplateWithValuesAndDefaults() throws MenuException {
        Scene s = withShared().parse("warps", """
            elements:
              - { use: warp-button, x: 8, y: 24, name: Spawn, ziel: spawn }
              - { use: warp-button, x: 96, y: 24, name: Farm, ziel: farm, farbe: green }
            """).scene();
        assertEquals(3, s.elements().size());                      // background + 2 buttons
        Button spawn = button(s, 1);
        Button farm = button(s, 2);
        assertEquals(8, spawn.x());
        assertEquals(24, spawn.y());
        assertEquals(72, spawn.width());
        assertEquals("Spawn", spawn.text().apply(NOBODY));
        assertEquals(96, farm.x());
        assertEquals(NamedTextColor.GREEN, farm.tint().apply(NOBODY));
        assertEquals(NamedTextColor.GRAY, spawn.tint().apply(NOBODY));   // {farbe|gray}: the default
    }

    @Test
    void useEntriesReplaceSettingsOfOneElementTemplates() throws MenuException {
        Scene s = withShared().parse("t", """
            elements:
              - { use: warp-button, x: 8, y: 8, name: A, ziel: a, width: 100, enabled-if: "%world% == lobby" }
            """).scene();
        Button b = button(s, 1);
        assertEquals(100, b.width());
        assertEquals(false, b.enabled().test(NOBODY));               // "%world%" stays without a player
    }

    @Test
    void groupsMoveTogetherAndLocalTemplatesWin() throws MenuException {
        Scene s = withShared().parse("t", """
            background: none
            templates:
              eintrag:                       # a group: icon + label + button, positions relative
                - { type: icon, x: 0, y: 0, item: "{item}" }
                - { type: label, x: 20, y: 4, text: "{name}" }
                - { use: warp-button, x: 100, y: 0, name: "{name}", ziel: "{ziel}" }
              warp-button:                   # replaces the shared one in this menu
                type: button
                width: 40
                text: "> {name}"
                actions: ["player: warp {ziel}"]
            elements:
              - { use: eintrag, x: 8, y: 10, item: diamond, name: Mine, ziel: mine }
              - { use: eintrag, x: 8, y: 40, item: wheat, name: Farm, ziel: farm }
            """).scene();
        List<Element> e = s.elements();
        assertEquals(6, e.size());
        assertEquals(8, e.get(0).x());
        assertEquals(28, e.get(1).x());
        assertEquals(14, e.get(1).y());
        assertEquals("Mine", ((Label) e.get(1)).text().apply(NOBODY));
        Button b = button(s, 2);
        assertEquals(108, b.x());
        assertEquals(40, b.width());                                // local template
        assertEquals("> Mine", b.text().apply(NOBODY));
        assertEquals(40, e.get(3).y());
        assertEquals(108, e.get(5).x());                             // second group, nested template moved too
        assertEquals(40, e.get(5).y());
    }

    @Test
    void showIfOnUseAppliesToTheWholeGroup() throws MenuException {
        Scene s = withShared().parse("t", """
            background: none
            templates:
              paar:
                - { type: label, x: 0, y: 0, text: a }
                - { type: label, x: 0, y: 10, text: b, show-if: "%player% == x" }
            elements:
              - { use: paar, x: 0, y: 0, show-if: "%world% == lobby" }
            """).scene();
        assertNotNull(s.elements().get(0).visible());
        // visibility needs a player; with none, placeholders stay as they are and "%world%" != "lobby"
        assertEquals(false, s.elements().get(0).visible().test(NOBODY));
        assertEquals(false, s.elements().get(1).visible().test(NOBODY));
    }

    @Test
    void errorsNameTheTemplateAndTheMissingValue() throws MenuException {
        Menus m = withShared();
        assertError(m, "elements:\n  - { use: warp-buton, x: 1, y: 2 }\n",
            "t.yml.elements[0].use: unknown template 'warp-buton' (known: warp-button)");
        assertError(m, "elements:\n  - { use: warp-button, x: 1, y: 2, name: A }\n",
            "t.yml.elements[0] (template warp-button): value 'ziel' missing");
        // a key that is no value of a one-element template replaces a setting, so typos are caught there
        assertError(m, "elements:\n  - { use: warp-button, x: 1, y: 2, name: A, ziel: b, nmae: c }\n",
            "t.yml.elements[0] (template warp-button).nmae: unknown setting");
        assertError(m, "templates:\n  g: [{ type: label, x: 0, y: 0, text: '{t}' }]\nelements:\n  - { use: g, t: a, width: 9 }\n",
            "t.yml.elements[0].width: template 'g' is a group and has no value '{width}' (it uses: t)");
        assertError(m, "templates:\n  a: { use: b }\n  b: { use: a }\nelements:\n  - { use: a }\n",
            "t.yml.elements[0] (template a) (template b)");
        assertError(m, "templates:\n  kaputt: { type: button, x: 1, y: 2, widht: 3 }\nelements:\n  - { use: kaputt }\n",
            "t.yml.elements[0] (template kaputt).widht: unknown setting");
    }

    private static void assertError(Menus m, String yaml, String expectedStart) {
        MenuException e = assertThrows(MenuException.class, () -> m.parse("t", yaml));
        assertTrue(e.getMessage().startsWith(expectedStart), e.getMessage());
    }

    @Test
    void bundledExamplesLoad(@TempDir Path dir) throws Exception {
        for (String f : List.of("_beispiel-vorlagen.yml", "beispiel-warps.yml", "beispiel.yml", "beispiel-regeln.yml",
            "beispiel-spieler.yml", "beispiel-formular.yml")) {
            try (var in = getClass().getResourceAsStream("/menus/" + f)) {
                Files.write(dir.resolve(f), in.readAllBytes());
            }
        }
        Menus menus = Menus.load(dir);
        assertEquals(List.of(), menus.errors());
        assertEquals(5, menus.all().size());
        Scene warps = menus.get("beispiel-warps").scene();
        assertEquals("Spawn", button(warps, 2).text().apply(NOBODY));
        assertEquals(true, button(warps, 4).enabled().test(NOBODY));  // Nether: enabled-if via the use entry
        assertEquals(7, warps.elements().size());
    }

    /** The English set is exported instead of the German one (never both: the warp templates differ). */
    @Test
    void bundledEnglishExamplesLoad(@TempDir Path dir) throws Exception {
        for (String f : List.of("_example-templates.yml", "example-warps.yml", "example.yml", "example-rules.yml",
            "example-players.yml", "example-forms.yml")) {
            try (var in = getClass().getResourceAsStream("/menus/" + f)) {
                Files.write(dir.resolve(f), in.readAllBytes());
            }
        }
        Menus menus = Menus.load(dir);
        assertEquals(List.of(), menus.errors());
        assertEquals(5, menus.all().size());
        Scene warps = menus.get("example-warps").scene();
        assertEquals("Spawn", button(warps, 2).text().apply(NOBODY));
        assertEquals(true, button(warps, 4).enabled().test(NOBODY));
        assertEquals(7, warps.elements().size());
        // same layout as the German examples
        for (String[] pair : new String[][] {{"example-forms", "beispiel-formular"}, {"example-players", "beispiel-spieler"},
            {"example", "beispiel"}, {"example-rules", "beispiel-regeln"}}) {
            try (var in = getClass().getResourceAsStream("/menus/" + pair[1] + ".yml")) {
                Scene german = new Menus().parse(pair[1], new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).scene();
                Scene english = menus.get(pair[0]).scene();
                assertEquals(german.elements().size(), english.elements().size(), pair[0]);
                assertEquals(german.height(), english.height(), pair[0]);
            }
        }
    }

    @Test
    void underscoreFilesAreTemplatesNotMenus(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("_vorlagen.yml"), SHARED);
        Files.writeString(dir.resolve("warps.yml"), "elements:\n  - { use: warp-button, x: 8, y: 8, name: A, ziel: a }\n");
        Menus menus = Menus.load(dir);
        assertEquals(List.of(), menus.errors());
        assertEquals(1, menus.all().size());
        assertEquals("A", button(menus.get("warps").scene(), 1).text().apply(NOBODY));
    }
}
