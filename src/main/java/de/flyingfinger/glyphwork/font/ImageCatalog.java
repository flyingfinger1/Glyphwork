package de.flyingfinger.glyphwork.font;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.pack.PackFiles;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The server's own pictures: every PNG under {@code plugins/Glyphwork/images/} (also in folders;
 * id = path without ".png", e.g. "logo" or "ranks/gold"). Each picture is cut into tiles of at most
 * 128 x 128 px, because the client puts every glyph into a 256 x 256 font texture and a larger one
 * shows as a missing-character box. Tiles live in 9 fonts {@code glyphwork:m<S>}, like item icons:
 * drawn on the text line they fall into, S px below it, so pictures sit at any pixel and above frames.
 */
public final class ImageCatalog {

    public static final int TILE = 128;
    private static final Gson GSON = new Gson();

    /** One tile: its glyph, its place inside the picture, its cell height (at least 8) and advance. */
    public record Tile(char codepoint, int dx, int dy, int cellHeight, int advance, String texture) {
    }

    public record Picture(String id, int width, int height, List<Tile> tiles) {
    }

    private final Map<String, Picture> pictures = new LinkedHashMap<>();
    private final Map<String, BufferedImage> sources = new LinkedHashMap<>();
    private final Map<String, byte[]> textures = new LinkedHashMap<>();

    /** No pictures. */
    public static ImageCatalog empty() {
        return new ImageCatalog();
    }

    /** Reads every PNG under {@code dir}; files that cannot be used are reported and skipped. */
    public static ImageCatalog load(Path dir, GlyphAllocator alloc, Logger log) {
        ImageCatalog c = new ImageCatalog();
        if (!Files.isDirectory(dir)) {
            return c;
        }
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().toList();
        } catch (IOException e) {
            log.warning("images/ could not be read: " + e.getMessage());
            return c;
        }
        for (Path f : files) {
            String id = dir.relativize(f).toString().replace('\\', '/');
            id = id.substring(0, id.length() - 4).toLowerCase();
            if (!id.matches("[a-z0-9_.\\-/]+")) {
                log.warning("images/" + dir.relativize(f) + ": file and folder names may only use a-z, 0-9, '_', '-', '.' (skipped)");
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(f.toFile());
                if (img == null) {
                    log.warning("images/" + dir.relativize(f) + ": not a PNG the server can read (skipped)");
                    continue;
                }
                c.add(id, img, alloc);
            } catch (IOException | RuntimeException e) {
                log.warning("images/" + dir.relativize(f) + ": " + e.getMessage() + " (skipped)");
            }
        }
        return c;
    }

    /** Adds one picture (also used by tests). */
    public void add(String id, BufferedImage source, GlyphAllocator alloc) {
        BufferedImage img = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(source, 0, 0, null);
        List<Tile> tiles = new ArrayList<>();
        for (int ty = 0; ty < img.getHeight(); ty += TILE) {
            for (int tx = 0; tx < img.getWidth(); tx += TILE) {
                int w = Math.min(TILE, img.getWidth() - tx);
                int h = Math.min(TILE, img.getHeight() - ty);
                // cells shorter than 8 px get transparent rows (the font's ascent may not exceed the height)
                BufferedImage cell = new BufferedImage(w, Math.max(8, h), BufferedImage.TYPE_INT_ARGB);
                cell.getGraphics().drawImage(img.getSubimage(tx, ty, w, h), 0, 0, null);
                int visible = Sheet.actualWidth(cell);
                if (visible == 0) {
                    continue; // fully transparent tile: nothing to draw
                }
                String texture = id + "_" + tx / TILE + "_" + ty / TILE;
                textures.put("assets/glyphwork/textures/image/" + texture + ".png", png(cell));
                tiles.add(new Tile(alloc.next(), tx, ty, cell.getHeight(), visible + 1, texture));
            }
        }
        pictures.put(id, new Picture(id, img.getWidth(), img.getHeight(), List.copyOf(tiles)));
        sources.put(id, img);
    }

    public @Nullable Picture get(String id) {
        String key = id.trim().toLowerCase();
        if (key.endsWith(".png")) {
            key = key.substring(0, key.length() - 4);
        }
        return pictures.get(key);
    }

    /** The whole picture as PNG (for uses outside fonts, e.g. GUI sprites), or null if unknown. */
    public byte @Nullable [] png(String id) {
        Picture p = get(id);
        return p == null ? null : png(sources.get(p.id()));
    }

    public int size() {
        return pictures.size();
    }

    public List<String> ids() {
        return List.copyOf(pictures.keySet());
    }

    /** Font of picture tiles s (0..8) px below their text line. */
    public static Key font(int s) {
        return Key.key("glyphwork", "m" + s);
    }

    /** Textures and the 9 picture fonts (always written, so the pack layout does not depend on them). */
    public void write(PackFiles pack) {
        textures.forEach(pack::put);
        for (int s = 0; s < ShiftFonts.LINE; s++) {
            JsonArray providers = new JsonArray();
            providers.add(SpaceFont.provider());
            for (Picture p : pictures.values()) {
                for (Tile t : p.tiles()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("type", "bitmap");
                    o.addProperty("file", "glyphwork:image/" + t.texture() + ".png");
                    o.addProperty("height", t.cellHeight());
                    o.addProperty("ascent", 7 - s);
                    JsonArray chars = new JsonArray();
                    chars.add(String.valueOf(t.codepoint()));
                    o.add("chars", chars);
                    providers.add(o);
                }
            }
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            pack.put("assets/glyphwork/font/m" + s + ".json", GSON.toJson(font));
        }
    }

    private static byte[] png(BufferedImage img) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
