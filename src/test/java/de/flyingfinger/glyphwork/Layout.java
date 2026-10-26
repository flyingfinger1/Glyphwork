package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.SpaceFont;
import de.flyingfinger.glyphwork.font.VanillaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays how the 26.3 client lays out and hit-tests the first line of a Glyphwork message
 * (Font.PreparedTextBuilder + ActiveTextCollector.ClickableStyleFinder):
 * bitmap glyphs get their drawn box (right edge = x + advance), space glyphs an EmptyArea on the
 * first line, and the last box under the cursor with a click event wins.
 */
final class Layout {

    record Box(float l, float t, float r, float b, ClickEvent<?> click) {
    }

    record Drawn(BufferedImage img, int x, int y, TextColor color) {
    }

    final List<Box> boxes = new ArrayList<>();
    final List<Drawn> drawn = new ArrayList<>();
    final List<int[]> textRuns = new ArrayList<>(); // x, y, width, rgb

    Layout(Component message, List<Sheet> sheets, VanillaText text) {
        Map<Character, BufferedImage> cells = new HashMap<>();
        for (Sheet s : sheets) {
            for (int i = 0; i < s.size(); i++) {
                cells.put(s.glyph(i).codepoint(), s.cell(i));
            }
        }
        float x = 0;
        for (Component child : message.children().getFirst().children()) {
            String s = ((TextComponent) child).content();
            String font = child.font() == null ? "" : child.font().asString();
            int y = font.startsWith("glyphwork:y") ? Integer.parseInt(font.substring("glyphwork:y".length())) : 0;
            for (char c : s.toCharArray()) {
                Integer space = SpaceFont.advance(c);
                float adv;
                if (space != null) {
                    adv = space;
                    boxes.add(new Box(x, 0, x + adv, 9, child.clickEvent()));
                } else if (cells.containsKey(c)) {
                    BufferedImage img = cells.get(c);
                    adv = advance(img);
                    boxes.add(new Box(x, y, x + adv, y + img.getHeight(), child.clickEvent()));
                    drawn.add(new Drawn(img, (int) x, y, child.color()));
                } else {
                    adv = text.width(String.valueOf(c));
                    if (c != VanillaText.NB_SPACE && c != ' ') {
                        textRuns.add(new int[]{(int) x, y, (int) adv - 1, child.color() == null ? 0xFFFFFF : child.color().value()});
                    }
                }
                x += adv;
            }
        }
    }

    private static int advance(BufferedImage img) {
        for (int x = img.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < img.getHeight(); y++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return x + 2;
                }
            }
        }
        return 1;
    }

    /** Payload of the winning click event at (px, py), or null. */
    String hit(float px, float py) {
        ClickEvent<?> result = null;
        for (Box b : boxes) {
            if (px >= b.l && px < b.r && py >= b.t && py < b.b && b.click != null) {
                result = b.click;
            }
        }
        return result == null ? null : ((ClickEvent.Payload.Text) result.payload()).value();
    }

    /** Draws glyphs with tint like the client; text is shown as solid bars (no font textures here). */
    void writePreview(Path file, int w, int h, int scale) throws IOException {
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xFF2B2B33 : 0xFF33333D);
            }
        }
        for (Drawn d : drawn) {
            int tint = d.color == null ? 0xFFFFFF : d.color.value();
            for (int y = 0; y < d.img.getHeight(); y++) {
                for (int x = 0; x < d.img.getWidth(); x++) {
                    int argb = d.img.getRGB(x, y);
                    if ((argb >>> 24) < 26) {
                        continue;
                    }
                    int r = ((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
                    int g = ((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
                    int b = (argb & 0xFF) * (tint & 0xFF) / 255;
                    fill(img, (d.x + x) * scale, (d.y + y) * scale, scale, scale, 0xFF000000 | (r << 16) | (g << 8) | b);
                }
            }
        }
        for (int[] t : textRuns) {
            fill(img, t[0] * scale, (t[1] + 1) * scale, t[2] * scale, 6 * scale, 0xFF000000 | t[3]);
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(img, "png", file.toFile());
    }

    private static void fill(BufferedImage img, int x0, int y0, int w, int h, int argb) {
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y0 + h); y++) {
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x0 + w); x++) {
                img.setRGB(x, y, argb);
            }
        }
    }
}
