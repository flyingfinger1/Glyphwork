package de.flyingfinger.glyphwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.SpaceFont;
import de.flyingfinger.glyphwork.font.Sprite;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.m0.M0Screen;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.LineBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the 26.3 client hit test (ActiveTextCollector.ClickableStyleFinder): every glyph has
 * an active box (bitmap: drawn box, right edge = x + advance; space: [x, x+advance) x line),
 * and the last box under the cursor with a click event wins.
 */
class M0HitTest {

    private final VanillaText text = VanillaText.load();
    private final M0Screen screen = new M0Screen(text);

    private record Box(float l, float t, float r, float b, ClickEvent<?> click) {
    }

    private List<Box> layout(Component line) {
        Map<Character, Sprite> sprites = new HashMap<>();
        screen.sprites().forEach(s -> sprites.put(s.codepoint(), s));
        List<Box> boxes = new ArrayList<>();
        float x = 0;
        for (Component child : line.children()) {
            String s = ((TextComponent) child).content();
            String font = child.font() == null ? "" : child.font().asString();
            for (char c : s.toCharArray()) {
                float adv;
                if (font.equals(SpaceFont.KEY.asString())) {
                    adv = spaceAdvance(c);
                    boxes.add(new Box(x, 0, x + adv, 9, child.clickEvent()));
                } else if (sprites.containsKey(c)) {
                    Sprite sp = sprites.get(c);
                    int y = Integer.parseInt(font.substring("glyphwork:y".length()));
                    adv = sp.advance();
                    boxes.add(new Box(x, y, x + adv, y + sp.height(), child.clickEvent()));
                } else {
                    adv = text.width(String.valueOf(c));
                }
                x += adv;
            }
        }
        return boxes;
    }

    private static int spaceAdvance(char c) {
        String one = String.valueOf(c);
        for (int k = 0; k < 11; k++) {
            if (SpaceFont.of(1 << k).equals(one)) {
                return 1 << k;
            }
            if (SpaceFont.of(-(1 << k)).equals(one)) {
                return -(1 << k);
            }
        }
        throw new IllegalStateException("not a space: " + Integer.toHexString(c));
    }

    private String hit(List<Box> boxes, float px, float py) {
        ClickEvent<?> result = null;
        for (Box b : boxes) {
            if (px >= b.l && px < b.r && py >= b.t && py < b.b && b.click != null) {
                result = b.click;
            }
        }
        return result == null ? null : ((ClickEvent.Payload.Text) result.payload()).value();
    }

    private List<Box> render() {
        LineBuilder line = screen.render(new M0Screen.State(), t -> ClickEvent.runCommand(t.name()));
        Component msg = line.build(M0Screen.H);
        return layout(msg.children().getFirst());
    }

    @Test
    void clicksLandWhereSpritesAreDrawn() {
        List<Box> b = render();
        // toggle button: x 8..79, y 22..41
        assertEquals("TOGGLE", hit(b, 8, 22));
        assertEquals("TOGGLE", hit(b, 79, 41));
        assertEquals("PANEL", hit(b, 50, 21));
        assertEquals("PANEL", hit(b, 50, 42));
        // counter button: x 96..159, y 31..50 (not on a 9px line boundary)
        assertEquals("COUNTER", hit(b, 96, 31));
        assertEquals("PANEL", hit(b, 120, 30));
        assertEquals("COUNTER", hit(b, 159, 50));
        assertEquals("PANEL", hit(b, 120, 51));
        // close button and slots
        assertEquals("CLOSE", hit(b, 165, 10));
        assertEquals("SLOT_0", hit(b, 10, 85));
        assertEquals("SLOT_7", hit(b, 8 + 7 * 20 + 8, 85));
        // text glyphs on top of a button have no click event and must not block it
        assertEquals("TOGGLE", hit(b, 44, 31));
        // outside the panel
        assertNull(hit(b, 50, 97));
    }

    @Test
    void lineEndsAtItsMaximumSoAllLayersShareOneOrigin() {
        LineBuilder line = screen.render(new M0Screen.State(), t -> null);
        assertEquals(M0Screen.W + 1, line.width());
        Component msg = line.build(M0Screen.H);
        float x = 0;
        for (Box box : layout(msg.children().getFirst())) {
            x = Math.max(x, box.r);
        }
        assertTrue(x <= line.width());
    }

    @Test
    void packIsReproducibleAndFontsAreValid() throws Exception {
        PackFiles a = new PackFiles();
        ShiftFonts.write(a, screen.sprites(), screen.offsets(), text);
        PackFiles b = new PackFiles();
        ShiftFonts.write(b, screen.sprites(), screen.offsets(), text);
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
