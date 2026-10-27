package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Head;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.core.SkinFaces;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.SquareSheets;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.render.FaceSource;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Big heads painted from skin faces with tinted squares. */
class BigHeadTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final SquareSheets squares = new SquareSheets(alloc);

    /** Steve's face: pixel i has color 0x0100 * i + 0x10 (all different). Alex is still loading. */
    private static int[] steve() {
        int[] f = new int[64];
        for (int i = 0; i < 64; i++) {
            f[i] = 0xFF000000 | (0x0100 * i + 0x10);
        }
        return f;
    }

    private final FaceSource faces = who -> who.equalsIgnoreCase("Steve") ? steve() : null;
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"))
        .bigHeads(squares, faces);

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();

        TestClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return null; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    private Layout layout(Scene s) {
        Component msg = renderer.render(s, new TestClick(s), (name, action) -> ClickEvent.runCommand(name)).build(s.height());
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        sheets.add(squares.sheet());
        return new Layout(msg, sheets, text);
    }

    /** Squares drawn: x, y, rgb (color not white/null, i.e. face pixels). */
    private static List<int[]> squares(Layout l) {
        List<int[]> out = new ArrayList<>();
        for (Layout.Drawn d : l.drawn) {
            if (d.color() != null && d.img().getWidth() == 8 && d.img().getHeight() == 8 && (d.img().getRGB(0, 0) >>> 24) == 255
                && (d.img().getRGB(7, 7) >>> 24) == 0) {
                out.add(new int[] {d.x(), d.y(), d.color().value()});
            }
        }
        return out;
    }

    @Test
    void headIsPaintedPixelByPixelAtAnyPosition() {
        Scene s = Scene.builder("t", 120, 80).add(Head.of(10, 21, "Steve").size(32)).build();
        List<int[]> sq = squares(layout(s));
        assertEquals(64, sq.size());
        int[] f = steve();
        for (int i = 0; i < 64; i++) {
            int[] q = sq.get(i);
            assertEquals(10 + (i % 8) * 4, q[0]);
            assertEquals(21 + (i / 8) * 4, q[1]);          // y = 21: off the 9 px grid, no shift
            assertEquals(f[i] & 0xFFFFFF, q[2]);
        }
        assertTrue(renderer.takeMissingFaces().isEmpty());
    }

    @Test
    void missingFaceShowsTheSmallHeadAndIsReported() {
        Scene s = Scene.builder("t", 120, 80).add(Head.of(10, 20, "Alex").size(16)).build();
        Layout l = layout(s);
        assertEquals(0, squares(l).size());
        assertEquals(1, l.heads.size());                     // the client's 8 x 8 head stands in
        assertEquals(Set.of("Alex"), renderer.takeMissingFaces());
        assertTrue(renderer.takeMissingFaces().isEmpty());
    }

    @Test
    void gridAndListPaintBigHeads() throws MenuException {
        Scene g = Scene.builder("t", 120, 80)
            .add(Grid.of("g", 0, 0, 2, 1, v -> List.of("head:Steve", "diamond")).cell(26, 24).icon(t -> t).headSize(16))
            .build();
        List<int[]> sq = squares(layout(g));
        assertEquals(64, sq.size());
        assertEquals(4, sq.getFirst()[0]);                  // 16 px head centered in the 24 px cell
        assertEquals(4, sq.getFirst()[1]);

        Scene list = new Menus().parse("t", """
            background: none
            elements:
              - type: list
                x: 0
                y: 5
                rows: 2
                row-height: 20
                head-size: 16
                items: [{ text: Steve, icon: "head:Steve" }]
            """).scene();
        List<int[]> lq = squares(layout(list));
        assertEquals(64, lq.size());
        assertEquals(3, lq.getFirst()[0]);
        assertEquals(5 + 2, lq.getFirst()[1]);               // centered in the 20 px row, no grid shift
    }

    @Test
    void yamlHead() throws MenuException {
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - { type: head, x: 4, y: 4, player: Steve, size: 24, tooltip: "Steve" }
            """).scene();
        List<int[]> sq = squares(layout(s));
        assertEquals(64, sq.size());
        assertEquals(4 + 7 * 3, sq.getLast()[0]);           // 3 px squares
        assertThrows(() -> new Menus().parse("t", "elements: [{ type: head, x: 0, y: 0, size: 20 }]\n"));
    }

    private static void assertThrows(org.junit.jupiter.api.function.Executable e) {
        org.junit.jupiter.api.Assertions.assertThrows(MenuException.class, e);
    }

    @Test
    void skinAddressFromAMojangProfile() throws Exception {
        String textures = java.util.Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\"}}}".getBytes());
        String json = "{\"id\":\"x\",\"name\":\"Notch\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + textures + "\"}]}";
        var m = SkinFaces.class.getDeclaredMethod("skinOf", String.class);
        m.setAccessible(true);
        assertEquals("http://textures.minecraft.net/texture/abc", m.invoke(null, json));
        assertNull(m.invoke(null, "{\"properties\":[]}"));
        assertNull(m.invoke(null, "kein json"));
    }

    @Test
    void faceOfASkinWithHat() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, 0xFF102030);              // face
            }
        }
        skin.setRGB(40, 8, 0xFFFF0000);                      // hat pixel, opaque, top left
        skin.setRGB(41, 8, 0x80FFFFFF);                      // half transparent hat pixel
        int[] f = SkinFaces.extract(skin);
        assertEquals(0xFFFF0000, f[0]);
        assertEquals(0xFF102030, f[2]);
        int half = f[1];
        assertTrue((half & 0xFF) > 0x30 && (half & 0xFF) < 0xFF);
        assertNull(SkinFaces.extract(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)));
        assertArrayEquals(f, SkinFaces.extract(skin));
    }
}
