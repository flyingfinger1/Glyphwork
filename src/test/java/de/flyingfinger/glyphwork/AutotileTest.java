package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.theme.Autotile;
import de.flyingfinger.glyphwork.theme.Style;
import de.flyingfinger.glyphwork.theme.Theme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutotileTest {

    @Test
    void thereAre47Tiles() {
        assertEquals(47, Autotile.MASKS.size());
        assertEquals(47, Autotile.tiles(Autotile.a2(Style.PANEL, 8), 8).size());
    }

    @Test
    void diagonalsOnlyCountBetweenTwoSides() {
        assertEquals(Autotile.N, Autotile.canonical(Autotile.N | Autotile.NE));
        int full = 0xFF;
        assertEquals(full, Autotile.canonical(full));
    }

    @Test
    void autotiledRectangleEqualsTheStyleRenderedDirectly() {
        // A 4x3-tile window built from tiles must look exactly like the style rendered on 32x24 px,
        // apart from inner pixels the quarters do not cover (none for a rectangle).
        int t = 8;
        List<BufferedImage> tiles = Autotile.tiles(Autotile.a2(Style.PANEL, t), t);
        boolean[][] cells = new boolean[3][4];
        for (boolean[] row : cells) {
            java.util.Arrays.fill(row, true);
        }
        boolean[][] px = new boolean[24][32];
        for (boolean[] row : px) {
            java.util.Arrays.fill(row, true);
        }
        BufferedImage direct = Style.PANEL.render(px);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                BufferedImage tile = tiles.get(Autotile.index(Autotile.mask(cells, c, r)));
                for (int y = 0; y < t; y++) {
                    for (int x = 0; x < t; x++) {
                        assertEquals(direct.getRGB(c * t + x, r * t + y), tile.getRGB(x, y),
                            "pixel " + (c * t + x) + "," + (r * t + y));
                    }
                }
            }
        }
    }

    @Test
    void builtinThemeRoundTripsThroughDisk(@TempDir Path dir) throws Exception {
        Theme.builtin(new GlyphAllocator()).save(dir.resolve("vanilla"));
        assertTrue(Files.exists(dir.resolve("vanilla/theme.json")));
        BufferedImage panel = ImageIO.read(dir.resolve("vanilla/panel.png").toFile());
        assertEquals(16, panel.getWidth());
        assertEquals(24, panel.getHeight());
        Theme loaded = Theme.load(dir.resolve("vanilla"), new GlyphAllocator());
        assertEquals("Vanilla", loaded.name());
        assertEquals(47, loaded.frame("button").sheet().size());
    }

    @Test
    void loadsAThemeExportedByTheEditor() throws Exception {
        // src/test/resources/editor-export/mein_skin was exported by editor/ (red line on the panel's top edge)
        Path dir = Path.of(getClass().getResource("/editor-export/mein_skin").toURI());
        Theme t = Theme.load(dir, new GlyphAllocator());
        assertEquals("Mein Skin", t.name());
        BufferedImage a2 = t.frame("panel").a2();
        assertEquals(0xFFFF0000, a2.getRGB(6, 10)); // painted pixel in the top-edge quarter
        assertEquals(47, t.frame("slot").sheet().size());
    }
}
