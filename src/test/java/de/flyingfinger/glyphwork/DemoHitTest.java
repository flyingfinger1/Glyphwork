package de.flyingfinger.glyphwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.demo.DemoScreen;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoHitTest {

    private final VanillaText text = VanillaText.load();
    private final Theme theme = Theme.builtin(new GlyphAllocator());
    private final DemoScreen screen = new DemoScreen(text, theme);

    private Component message() {
        LineBuilder line = screen.render(new DemoScreen.State(), t -> ClickEvent.runCommand(t.name()));
        return line.build(DemoScreen.H);
    }

    private Layout layout() {
        return new Layout(message(), theme.sheets(), text);
    }

    @Test
    void clicksLandWhereFramesAreDrawn() {
        Layout l = layout();
        // toggle button: x 8..79, y 22..37
        assertEquals("TOGGLE", l.hit(8, 22));
        assertEquals("TOGGLE", l.hit(79, 37));
        assertEquals("PANEL", l.hit(50, 21));
        assertEquals("PANEL", l.hit(50, 38));
        // counter button: x 96..159, y 31..46 (not on a 9px line boundary)
        assertEquals("COUNTER", l.hit(96, 31));
        assertEquals("PANEL", l.hit(120, 30));
        assertEquals("COUNTER", l.hit(159, 46));
        assertEquals("PANEL", l.hit(120, 47));
        // close button and slots
        assertEquals("CLOSE", l.hit(160, 12));
        assertEquals("SLOT_0", l.hit(10, 85));
        assertEquals("SLOT_7", l.hit(8 + 7 * 20 + 8, 85));
        assertEquals("PANEL", l.hit(25, 85)); // gap between slot 0 and 1
        // L-shaped well: full first row, second row only 8 tiles
        assertEquals("WELL", l.hit(160, 104));
        assertEquals("WELL", l.hit(70, 112));
        assertEquals("PANEL", l.hit(100, 112));
        // text on top of a button has no click event and must not block it
        assertEquals("TOGGLE", l.hit(44, 30));
        // outside the panel
        assertNull(l.hit(50, DemoScreen.H + 1));
    }

    @Test
    void rowsOfTilesAreMergedIntoFewComponents() {
        Component msg = message();
        int parts = msg.children().getFirst().children().size();
        String json = GsonComponentSerializer.gson().serialize(msg);
        // ~370 tiles; without merging this would be several hundred components
        assertTrue(parts < 120, "components: " + parts);
        assertTrue(json.length() < 40_000, "json bytes: " + json.length());
    }

    @Test
    void lineEndsAtItsWidestPoint() {
        LineBuilder line = screen.render(new DemoScreen.State(), t -> null);
        assertEquals(DemoScreen.W + 1, line.width());
    }

    @Test
    void writesPreview() throws Exception {
        layout().writePreview(Path.of("build/preview/demo.png"), DemoScreen.W, DemoScreen.H, 4);
        theme.save(Path.of("build/preview/theme"));
    }

    @Test
    void packIsReproducibleAndFontsAreValid() throws Exception {
        PackFiles a = new PackFiles();
        ShiftFonts.write(a, theme.sheets(), screen.offsets(), text);
        PackFiles b = new PackFiles();
        Theme other = Theme.builtin(new GlyphAllocator());
        ShiftFonts.write(b, other.sheets(), new DemoScreen(text, other).offsets(), text);
        byte[] zip = a.toZip();
        assertEquals(PackFiles.sha1(zip), PackFiles.sha1(b.toZip()));

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
            assertEquals(screen.offsets().size() + 1, fonts);
        }
    }
}
