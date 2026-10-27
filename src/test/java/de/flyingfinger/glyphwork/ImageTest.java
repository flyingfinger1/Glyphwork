package de.flyingfinger.glyphwork;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Image;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ImageCatalog;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's own pictures: tiles, pack fonts and the image element. */
class ImageTest {

    private static BufferedImage picture(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    @Test
    void bigPicturesAreCutIntoTilesThatFitTheFontTexture() {
        ImageCatalog c = ImageCatalog.empty();
        c.add("banner", picture(300, 140, 0xFF336699), new GlyphAllocator());
        ImageCatalog.Picture p = c.get("banner");
        assertNotNull(p);
        assertEquals(300, p.width());
        assertEquals(6, p.tiles().size());                              // 3 x 2 tiles
        for (ImageCatalog.Tile t : p.tiles()) {
            assertTrue(t.cellHeight() <= 128 && t.cellHeight() >= 8);
            assertTrue(t.advance() - 1 <= 128);
        }
        assertEquals(List.of(0, 128, 256, 0, 128, 256), p.tiles().stream().map(ImageCatalog.Tile::dx).toList());
        assertEquals(45, p.tiles().get(2).advance());                   // 300 - 256 = 44 px + 1
        assertEquals(12, p.tiles().get(3).cellHeight());                // 140 - 128 = 12 px
        assertNotNull(c.get("BANNER.png"));                             // case and ".png" do not matter
    }

    @Test
    void smallAndTransparentParts() {
        ImageCatalog c = ImageCatalog.empty();
        c.add("dot", picture(4, 3, 0xFFFF0000), new GlyphAllocator());
        assertEquals(8, c.get("dot").tiles().getFirst().cellHeight());   // padded to 8 px (ascent rule)
        BufferedImage half = picture(256, 10, 0);
        for (int y = 0; y < 10; y++) {
            half.setRGB(5, y, 0xFFFFFFFF);
        }
        c.add("half", half, new GlyphAllocator());
        assertEquals(1, c.get("half").tiles().size());                  // the empty right tile is left out
    }

    @Test
    void loadsFoldersAndSkipsBadNames(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("ranks"));
        ImageIO.write(picture(16, 16, 0xFFFFD700), "png", dir.resolve("ranks/Gold.png").toFile());
        ImageIO.write(picture(16, 16, 0xFFFFD700), "png", dir.resolve("mit leerzeichen.png").toFile());
        Files.writeString(dir.resolve("kaputt.png"), "kein png");
        ImageCatalog c = ImageCatalog.load(dir, new GlyphAllocator(), Logger.getLogger("test"));
        assertEquals(List.of("ranks/gold"), c.ids());
    }

    @Test
    void packHasTexturesAndNineFonts() throws Exception {
        ImageCatalog c = ImageCatalog.empty();
        c.add("ranks/gold", picture(20, 20, 0xFFFFD700), new GlyphAllocator());
        PackFiles pack = new PackFiles();
        c.write(pack);
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(pack.toZip()))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                files.put(e.getName(), in.readAllBytes());
            }
        }
        assertTrue(files.containsKey("assets/glyphwork/textures/image/ranks/gold_0_0.png"), files.keySet().toString());
        for (int s = 0; s < 9; s++) {
            JsonObject font = JsonParser.parseString(new String(files.get("assets/glyphwork/font/m" + s + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray providers = font.getAsJsonArray("providers");
            JsonObject tile = providers.get(1).getAsJsonObject();
            assertEquals("glyphwork:image/ranks/gold_0_0.png", tile.get("file").getAsString());
            assertEquals(7 - s, tile.get("ascent").getAsInt());
            assertEquals(20, tile.get("height").getAsInt());
        }
    }

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

    /** Text components in picture fonts: [line, font, codepoint] in order. */
    private static void pictureGlyphs(Component c, List<String> out, int[] line) {
        if (c instanceof TextComponent tc) {
            if (tc.content().equals("\n")) {
                line[0]++;
            } else if (tc.font() != null && tc.font().value().startsWith("m")) {
                for (char ch : tc.content().toCharArray()) {
                    if (ch >= 0xE100) {
                        out.add(line[0] + ":" + tc.font().value() + ":" + Integer.toHexString(ch));
                    }
                }
            }
        }
        c.children().forEach(ch -> pictureGlyphs(ch, out, line));
    }

    @Test
    void imageElementPlacesTilesOnTheirLines() throws MenuException {
        GlyphAllocator alloc = new GlyphAllocator();
        Theme theme = Theme.builtin(alloc);
        Sheet hit = Sheet.invisible(alloc);
        ImageCatalog images = ImageCatalog.empty();
        images.add("logo", picture(140, 20, 0xFF00AAFF), alloc);
        SceneRenderer r = new SceneRenderer(VanillaText.load(), theme, hit.glyph(), 256, Logger.getLogger("test")).images(images);
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - { type: image, x: 4, y: 20, image: logo, tooltip: "Unser Logo", actions: ["close"] }
              - { type: image, x: 4, y: 60, image: gibt-es-nicht }
            """).scene();
        Component msg = r.render(s, new TestClick(s), (name, action) -> ClickEvent.runCommand(name)).build(s.height());
        List<String> glyphs = new ArrayList<>();
        pictureGlyphs(msg, glyphs, new int[] {0});
        ImageCatalog.Picture p = images.get("logo");
        // y = 20: line 2, 2 px below it -> font m2; both tiles (128 + 12 px wide)
        assertEquals(List.of("2:m2:" + Integer.toHexString(p.tiles().get(0).codepoint()),
            "2:m2:" + Integer.toHexString(p.tiles().get(1).codepoint())), glyphs);
        Layout l = new Layout(msg, List.of(hit), VanillaText.load());
        assertEquals("menu:t#0", l.hit(10, 25));
        assertEquals("menu:t#0", l.hit(140, 38));
        assertNull(l.hit(150, 25));
    }
}
