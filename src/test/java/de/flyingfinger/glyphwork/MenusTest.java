package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** YAML menus: loading, errors, layout and navigation actions. */
class MenusTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"));

    private static String resource(String name) throws Exception {
        try (InputStream in = MenusTest.class.getResourceAsStream("/menus/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Menus examples(Path dir) throws Exception {
        Files.writeString(dir.resolve("beispiel.yml"), resource("beispiel.yml"));
        Files.writeString(dir.resolve("beispiel-regeln.yml"), resource("beispiel-regeln.yml"));
        return Menus.load(dir);
    }

    /** Records navigation; player() is null (actions touching the player are not run here). */
    private static final class RecordingClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();
        final List<String> log = new ArrayList<>();

        RecordingClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return null; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { log.add("reopen"); }
        @Override public void open(Scene next) { log.add("open " + next.id()); }
        @Override public void back() { log.add("back"); }
        @Override public void close() { log.add("close"); }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { log.add("prompt"); }
    }

    private Layout layout(Scene scene) {
        View view = new RecordingClick(scene);
        LineBuilder line = renderer.render(scene, view, (name, action) -> ClickEvent.runCommand(name));
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        return new Layout(line.build(scene.height()), sheets, text);
    }

    @Test
    void exampleMenusLoadAndAreClickable(@TempDir Path dir) throws Exception {
        Menus menus = examples(dir);
        assertEquals(List.of(), menus.errors());
        assertEquals(2, menus.all().size(), menus.errors().toString());
        Menus.Menu main = menus.get("beispiel");
        assertNotNull(main);
        assertNull(main.command());

        Layout l = layout(main.scene());
        // elements: 0 background, 1-2 labels, 3 "Regeln", 4 "Hallo sagen", 5 input, 6 label, 7 grid, 8 close
        assertEquals("menu:beispiel#3", l.hit(20, 30));
        assertEquals("menu:beispiel#4", l.hit(120, 30));
        assertEquals("menu:beispiel#5", l.hit(80, 50));
        assertEquals("menu:beispiel#7:cell2", l.hit(8 + 2 * 20 + 8, 84));
        assertEquals("menu:beispiel#8", l.hit(140, 112));
        assertNull(l.hit(60, 112)); // background has no action

        Layout rules = layout(menus.get("beispiel-regeln").scene());
        assertEquals("menu:beispiel-regeln#2:row0", rules.hit(40, 28));
        assertEquals("menu:beispiel-regeln#2:next", rules.hit(160, 110)); // 7 rules, 5 rows -> pager
    }

    @Test
    void navigationActions(@TempDir Path dir) throws Exception {
        Menus menus = examples(dir);
        Menus.Menu main = menus.get("beispiel");
        RecordingClick c = new RecordingClick(main.scene());
        // the "Regeln" button opens the second menu
        de.flyingfinger.glyphwork.api.Button regeln = (de.flyingfinger.glyphwork.api.Button) main.scene().elements().get(3);
        regeln.onClick().accept(c);
        de.flyingfinger.glyphwork.api.Button close = (de.flyingfinger.glyphwork.api.Button) main.scene().elements().get(8);
        close.onClick().accept(c);
        de.flyingfinger.glyphwork.api.Button input = (de.flyingfinger.glyphwork.api.Button) main.scene().elements().get(5);
        input.onClick().accept(c);
        assertEquals(List.of("open menu:beispiel-regeln", "close", "prompt"), c.log);
    }

    @Test
    void errorsSayWhereAndWhat() {
        Menus m = new Menus();
        assertError(m, "elements:\n  - type: button\n    x: 1\n    y: 2\n    widht: 40\n",
            "t.yml.elements[0].widht: unknown setting");
        assertError(m, "elements:\n  - type: label\n    x: abc\n    y: 2\n    text: hi\n",
            "t.yml.elements[0].x: number expected, got 'abc'");
        assertError(m, "elements:\n  - type: button\n    x: 1\n    y: 2\n    actions: [\"teleport: spawn\"]\n",
            "t.yml.elements[0].actions[0]: unknown action 'teleport'");
        assertError(m, "elements:\n  - type: label\n    x: 1\n    y: 2\n    text: hi\n    color: purpleish\n",
            "t.yml.elements[0].color: color expected");
        assertError(m, "command: \"my menu\"\n", "t.yml.command: only letters");
        assertError(m, "elements:\n  - type: knob\n", "t.yml.elements[0].type: unknown type 'knob'");
        assertError(m, "title: [unclosed\n", "t.yml: not valid YAML");
    }

    private static void assertError(Menus m, String yaml, String expectedStart) {
        MenuException e = assertThrows(MenuException.class, () -> m.parse("t", yaml));
        assertTrue(e.getMessage().startsWith(expectedStart), e.getMessage());
    }

    @Test
    void brokenFilesAreSkippedOthersLoad(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("good.yml"), "title: ok\ncommand: Good\naliases: [g]\n");
        Files.writeString(dir.resolve("bad.yml"), "elements:\n  - type: nope\n");
        Menus menus = Menus.load(dir);
        assertEquals(1, menus.all().size());
        assertEquals("good", menus.get("good").command());
        assertEquals(List.of("g"), menus.get("GOOD").aliases());
        assertEquals(1, menus.errors().size());
        assertTrue(menus.errors().getFirst().startsWith("bad.yml"), menus.errors().getFirst());
    }

    @Test
    void listsInTabsGetTheirOwnIdsAndPlaceholderIdsMayRepeat() throws Exception {
        String pane = "{type: scroll, id: 'a-%menu_x%', x: 0, y: 30, width: 64, height: 48, elements: []}";
        String list = "{type: list, x: 0, y: 0, items: [{text: one}, {text: two}]}";
        Menus menus = new Menus();
        Menus.Menu m = menus.parse("t", String.join("\n",
            "background: none",
            "elements:",
            "  - type: tabs",
            "    x: 0", "    y: 0", "    width: 160", "    height: 120",
            "    tabs:",
            "      - {id: a, title: A, elements: [" + list + ", " + pane + "]}",
            "      - {id: b, title: B, elements: [" + list + ", " + pane + "]}", ""));
        de.flyingfinger.glyphwork.api.Tabs tabs = (de.flyingfinger.glyphwork.api.Tabs) m.scene().elements().getFirst();
        String first = ((de.flyingfinger.glyphwork.api.ListView<?>) tabs.tabs().get(0).elements().getFirst()).id();
        String second = ((de.flyingfinger.glyphwork.api.ListView<?>) tabs.tabs().get(1).elements().getFirst()).id();
        assertTrue(!first.equals(second), first + " / " + second);
    }

    @Test
    void loadTimeChecks(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.yml"), String.join("\n",
            "command: shop", "aliases: [s]", "background: fancy",
            "elements:",
            "  - {type: button, x: 4, y: 4, width: 40, text: Go, actions: ['open: nowhere']}",
            "  - {type: image, x: 4, y: 30, image: logo}",
            "  - {type: button, x: 4, y: 50, width: 40, text: Back, actions: ['open: b']}", ""));
        Files.writeString(dir.resolve("b.yml"), "command: s\n");
        Files.writeString(dir.resolve("c.yml"), "title: '%item_x|dat%'\n");
        Files.writeString(dir.resolve("d.yml"), "aliases: ['bad alias']\n");
        Menus menus = Menus.load(dir);
        assertEquals(2, menus.all().size(), menus.errors().toString());
        assertTrue(menus.errors().stream().anyMatch(e -> e.startsWith("c.yml") && e.contains("unknown filter '|dat'")),
            menus.errors().toString());
        assertTrue(menus.errors().stream().anyMatch(e -> e.startsWith("d.yml") && e.contains("alias")),
            menus.errors().toString());
        assertTrue(menus.warnings().stream().anyMatch(w -> w.contains("/s is also used")), menus.warnings().toString());
        List<String> refs = menus.refs().stream().map(r -> r.kind() + ":" + r.name()).toList();
        assertEquals(List.of("frame:fancy", "menu:nowhere", "image:logo", "menu:b"), refs);
        assertNotNull(renderer.unknown("frame", "fancy"));
        assertNull(renderer.unknown("frame", "button"));
        assertNotNull(renderer.unknown("image", "logo"));
    }
}
