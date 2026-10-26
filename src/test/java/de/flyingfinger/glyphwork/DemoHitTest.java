package de.flyingfinger.glyphwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.demo.Demo;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo scenes (built with the public API) rendered and hit-tested like the client does. */
class DemoHitTest {

    private static final int MAX_HEIGHT = 256;

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), MAX_HEIGHT, Logger.getLogger("test"));
    private final Demo demo = new Demo();

    /** View without a player, state in a map. */
    private static final class TestView implements View {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();

        TestView(Scene scene) {
            this.scene = scene;
        }

        @Override
        public Player player() {
            return null;
        }

        @Override
        public Scene scene() {
            return scene;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, T fallback) {
            return (T) state.getOrDefault(key, fallback);
        }

        @Override
        public void set(String key, Object value) {
            state.put(key, value);
        }
    }

    private List<Sheet> sheets() {
        List<Sheet> s = new ArrayList<>(theme.sheets());
        s.add(hit);
        return s;
    }

    private Component message(Scene scene, TestView view) {
        LineBuilder line = renderer.render(scene, view, (name, action) -> ClickEvent.runCommand(name));
        return line.build(scene.height());
    }

    private Layout layout(Scene scene) {
        return layout(scene, new TestView(scene));
    }

    private Layout layout(Scene scene, TestView view) {
        return new Layout(message(scene, view), sheets(), text);
    }

    @Test
    void mainSceneClicksLandWhereThingsAreDrawn() {
        Layout l = layout(demo.main);
        String p = "glyphwork:demo#";
        assertEquals(p + 3, l.hit(8, 22));          // toggle x 8..79, y 22..37
        assertEquals(p + 3, l.hit(79, 37));
        assertEquals(p + 0, l.hit(50, 21));         // panel around it
        assertEquals(p + 0, l.hit(50, 38));
        assertEquals(p + 4, l.hit(96, 31));         // counter at y = 31 (off the 9 px grid)
        assertEquals(p + 0, l.hit(120, 30));
        assertEquals(p + 4, l.hit(159, 46));
        assertEquals(p + 2, l.hit(160, 12));        // close
        assertEquals(p + "7:cell0", l.hit(10, 85)); // grid cells, 20 px pitch
        assertEquals(p + "7:cell7", l.hit(156, 85));
        assertEquals(p + 0, l.hit(25, 85));         // gap between cells
        assertEquals(p + 8, l.hit(80, 104));        // L shape
        assertEquals(p + 8, l.hit(30, 112));
        assertEquals(p + 0, l.hit(60, 112));
        assertEquals(p + 9, l.hit(130, 110));       // "Listen >" button
        assertEquals(p + 3, l.hit(44, 30));         // text on a button does not block it
        assertNull(l.hit(50, 129));                 // outside the panel
    }

    @Test
    void listsSceneRowsGridAndPagers() {
        Layout l = layout(demo.lists);
        String p = "glyphwork:demo-lists#";
        assertEquals(p + 2, l.hit(100, 28));               // search button
        assertEquals(p + "3:row0", l.hit(40, 50));          // list rows: invisible hitboxes
        assertEquals(p + "3:row3", l.hit(40, 42 + 16 * 3 + 8));
        assertEquals(p + "3:next", l.hit(80, 115));         // list pager (36 fruits, 4 rows)
        assertNull(l.hit(12, 115));                         // "previous" is disabled on page 1
        assertEquals(p + "4:cell0", l.hit(98, 44));         // grid
        assertEquals(p + "4:cell11", l.hit(96 + 3 * 18 + 8, 42 + 2 * 18 + 8));
        assertEquals(p + "4:next", l.hit(155, 103));        // grid pager (30 numbers, 12 cells)
        assertEquals(p + 7, l.hit(20, 150));                // back
    }

    @Test
    void pagingShowsTheRightItems() {
        TestView view = new TestView(demo.lists);
        view.set("glyphwork:page:fruits", 8);               // beyond the last page -> clamped to 9/9
        Layout l = layout(demo.lists, view);
        assertEquals("glyphwork:demo-lists#3:row32", l.hit(40, 50));
        assertEquals("glyphwork:demo-lists#3:row35", l.hit(40, 42 + 16 * 3 + 8)); // 36 items fill page 9
        view.set("query", "beere");                         // filter: 6 items, page clamped to 2/2
        Layout f = layout(demo.lists, view);
        assertEquals("glyphwork:demo-lists#3:row4", f.hit(40, 50));
        view.set("glyphwork:page:fruits", 0);
        assertEquals("glyphwork:demo-lists#3:row0", layout(demo.lists, view).hit(40, 50));
    }

    @Test
    void textSitsOnItsOwnLineAndAllLinesEndAtTheSameX() {
        Layout l = layout(demo.main);
        // "Zähler: 0" at (8, 54): line 6, shift 0
        assertTrue(l.textRuns.stream().anyMatch(t -> t[0] == 8 && t[1] == 54), "label at 8,54");
        // button text centered vertically: "+1" at y = 31 + 4
        assertTrue(l.textRuns.stream().anyMatch(t -> t[1] == 35), "button label at y 35");
        int w = l.lineWidths.getFirst();
        assertTrue(l.lineWidths.stream().allMatch(x -> x == 0 || x == w), "line widths " + l.lineWidths);
    }

    @Test
    void stateValuesOfAnyTypeRender() {
        TestView view = new TestView(demo.lists);
        view.set("number", 7);          // Integer, as the grid click stores it
        view.set("fruit", "Kiwi");
        Layout l = layout(demo.lists, view);
        assertTrue(l.textRuns.stream().anyMatch(t -> t[1] == 132 && t[0] >= 96), "Nummer label drawn");
    }

    @Test
    void messagesStaySmall() {
        String json = GsonComponentSerializer.gson().serialize(message(demo.lists, new TestView(demo.lists)));
        assertTrue(json.length() < 60_000, "json bytes: " + json.length());
    }

    @Test
    void writesPreview() throws Exception {
        layout(demo.main).writePreview(Path.of("build/preview/demo.png"), 176, 128, 4);
        layout(demo.lists).writePreview(Path.of("build/preview/demo-lists.png"), 176, 168, 4);
        theme.save(Path.of("build/preview/theme"));
        // the 47 generated tiles per frame (8 per row), for tools/editor-parity.mjs
        java.nio.file.Files.createDirectories(Path.of("build/preview/tiles"));
        for (var sheet : theme.sheets()) {
            java.nio.file.Files.write(Path.of("build/preview/tiles/" + sheet.name() + ".png"), sheet.png());
        }
    }

    @Test
    void packIsReproducibleAndFontsAreValid() throws Exception {
        PackFiles a = new PackFiles();
        ShiftFonts.write(a, sheets(), MAX_HEIGHT, text);
        GlyphAllocator alloc2 = new GlyphAllocator();
        Theme other = Theme.builtin(alloc2);
        List<Sheet> otherSheets = new ArrayList<>(other.sheets());
        otherSheets.add(Sheet.invisible(alloc2));
        PackFiles b = new PackFiles();
        ShiftFonts.write(b, otherSheets, MAX_HEIGHT, text);
        byte[] zip = a.toZip();
        assertEquals(PackFiles.sha1(zip), PackFiles.sha1(b.toZip()));
        assertTrue(zip.length < 2_000_000, "pack bytes: " + zip.length);

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            int fonts = 0;
            while ((e = in.getNextEntry()) != null) {
                if (!e.getName().endsWith(".json")) {
                    continue;
                }
                fonts++;
                JsonObject font = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (var p : font.getAsJsonArray("providers")) {
                    JsonObject o = p.getAsJsonObject();
                    if (o.get("type").getAsString().equals("bitmap")) {
                        assertTrue(o.get("ascent").getAsInt() <= o.get("height").getAsInt(), e.getName() + " " + o);
                    }
                }
            }
            assertEquals(MAX_HEIGHT + ShiftFonts.LINE + 1, fonts);
        }
    }
}
