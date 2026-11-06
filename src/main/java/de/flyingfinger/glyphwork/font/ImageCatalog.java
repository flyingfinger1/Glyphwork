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
 *
 * <p>Other sizes of a picture ({@link SizeRequest}) are pictures of their own, resized when the pack
 * is built and added after all originals, so a pack without sizes stays exactly as before.
 */
public final class ImageCatalog {

    public static final int TILE = 128;
    private static final Gson GSON = new Gson();

    /** One tile: its glyph, its place inside the picture, its cell height (at least 8) and advance. */
    public record Tile(char codepoint, int dx, int dy, int cellHeight, int advance, String texture) {
    }

    public record Picture(String id, int width, int height, List<Tile> tiles) {
    }

    /** Largest side of a resized picture. */
    public static final int MAX_SIZE = 1024;

    /**
     * A size wanted for the pictures matching {@code pattern} ({@code *} = any text): width and
     * height (0 = from the aspect ratio), or a scale.
     */
    public record SizeRequest(String pattern, int width, int height, double scale) {

        public SizeRequest {
            pattern = pattern.trim().toLowerCase();
            if (pattern.endsWith(".png")) {
                pattern = pattern.substring(0, pattern.length() - 4);
            }
        }

        public static SizeRequest size(String pattern, int width, int height) {
            return new SizeRequest(pattern, width, height, 0);
        }

        public static SizeRequest scale(String pattern, double scale) {
            return new SizeRequest(pattern, 0, 0, scale);
        }

        public boolean matches(String id) {
            StringBuilder re = new StringBuilder();
            String[] parts = pattern.split("\\*", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    re.append(".*");
                }
                re.append(java.util.regex.Pattern.quote(parts[i]));
            }
            return id.matches(re.toString());
        }

        /** Width and height for a picture of this size, or null if nothing is asked. */
        public int @Nullable [] dims(int w, int h) {
            return ImageCatalog.dims(w, h, width, height, scale);
        }

        @Override
        public String toString() {
            return "'" + pattern + "' " + (scale > 0 ? "scale " + scale : width + " x " + height);
        }
    }

    /** Size of a w x h picture drawn with the asked width/height (0 = keep ratio) or scale; null = own size. */
    public static int @Nullable [] dims(int w, int h, int width, int height, double scale) {
        if (scale > 0) {
            return new int[] {Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale))};
        }
        if (width > 0 && height > 0) {
            return new int[] {width, height};
        }
        if (width > 0) {
            return new int[] {width, Math.max(1, (int) Math.round(h * (double) width / w))};
        }
        if (height > 0) {
            return new int[] {Math.max(1, (int) Math.round(w * (double) height / h)), height};
        }
        return null;
    }

    private final Map<String, Picture> pictures = new LinkedHashMap<>();
    /** Other sizes, by "id@WxH". */
    private final Map<String, Picture> sized = new LinkedHashMap<>();
    private final Map<String, BufferedImage> sources = new LinkedHashMap<>();
    private final Map<String, byte[]> textures = new LinkedHashMap<>();

    /** No pictures. */
    public static ImageCatalog empty() {
        return new ImageCatalog();
    }

    /** Reads every PNG under {@code dir}; files that cannot be used are reported and skipped. */
    public static ImageCatalog load(Path dir, GlyphAllocator alloc, Logger log) {
        return load(dir, alloc, log, List.of());
    }

    /** Like {@link #load(Path, GlyphAllocator, Logger)}, plus the sizes asked for. */
    public static ImageCatalog load(Path dir, GlyphAllocator alloc, Logger log, List<SizeRequest> sizes) {
        ImageCatalog c = loadOriginals(dir, alloc, log);
        c.addSizes(sizes, alloc, log);
        return c;
    }

    private static ImageCatalog loadOriginals(Path dir, GlyphAllocator alloc, Logger log) {
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
        pictures.put(id, cut(id, id, img, alloc));
        sources.put(id, img);
    }

    /**
     * Adds the asked sizes of the pictures loaded so far (in a fixed order, after all originals).
     * A request that matches no picture, or a size above {@link #MAX_SIZE}, is reported.
     */
    public void addSizes(List<SizeRequest> requests, GlyphAllocator alloc, Logger log) {
        java.util.Set<String> reported = new java.util.HashSet<>();
        for (SizeRequest r : requests) {
            if (pictures.keySet().stream().noneMatch(r::matches) && reported.add("none:" + r.pattern())) {
                log.warning("Picture size " + r + ": no matching picture in images/");
            }
        }
        for (Picture p : List.copyOf(pictures.values())) {
            for (SizeRequest r : requests) {
                if (!r.matches(p.id())) {
                    continue;
                }
                int[] d = r.dims(p.width(), p.height());
                if (d == null || d[0] == p.width() && d[1] == p.height() || sized.containsKey(key(p.id(), d[0], d[1]))) {
                    continue;
                }
                if (d[0] > MAX_SIZE || d[1] > MAX_SIZE) {
                    if (reported.add("big:" + p.id() + "@" + d[0] + "x" + d[1])) {
                        log.warning("Picture '" + p.id() + "' at " + d[0] + " x " + d[1] + ": larger than "
                            + MAX_SIZE + " px (left out)");
                    }
                    continue;
                }
                BufferedImage img = resize(sources.get(p.id()), d[0], d[1]);
                sized.put(key(p.id(), d[0], d[1]), cut(p.id(), "_size/" + d[0] + "x" + d[1] + "/" + p.id(), img, alloc));
            }
        }
    }

    private static String key(String id, int w, int h) {
        return id + "@" + w + "x" + h;
    }

    /** Cuts a picture into tiles; their textures are named after {@code textureBase}. */
    private Picture cut(String id, String textureBase, BufferedImage img, GlyphAllocator alloc) {
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
                String texture = textureBase + "_" + tx / TILE + "_" + ty / TILE;
                textures.put("assets/glyphwork/textures/image/" + texture + ".png", png(cell));
                tiles.add(new Tile(alloc.next(), tx, ty, cell.getHeight(), visible + 1, texture));
            }
        }
        return new Picture(id, img.getWidth(), img.getHeight(), List.copyOf(tiles));
    }

    /**
     * The picture at the asked size (width/height with 0 = keep ratio, or scale); the picture itself
     * when nothing is asked. Null if that size is not in the pack.
     */
    public @Nullable Picture sized(Picture base, int width, int height, double scale) {
        int[] d = dims(base.width(), base.height(), width, height, scale);
        if (d == null || d[0] == base.width() && d[1] == base.height()) {
            return base;
        }
        return sized.get(key(base.id(), d[0], d[1]));
    }

    /**
     * Resizes by the area each target pixel covers (premultiplied, so transparent pixels do not
     * darken the edges); enlarging keeps hard pixels (nearest neighbour).
     */
    static BufferedImage resize(BufferedImage src, int w, int h) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double fx = (double) sw / w;
        double fy = (double) sh / h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (fx <= 1 && fy <= 1) {
                    out.setRGB(x, y, src.getRGB(Math.min(sw - 1, (int) ((x + 0.5) * fx)),
                        Math.min(sh - 1, (int) ((y + 0.5) * fy))));
                    continue;
                }
                double x0 = x * fx;
                double x1 = (x + 1) * fx;
                double y0 = y * fy;
                double y1 = (y + 1) * fy;
                double sa = 0;
                double sr = 0;
                double sg = 0;
                double sb = 0;
                double sum = 0;
                for (int sy = (int) y0; sy < Math.min(sh, Math.ceil(y1)); sy++) {
                    double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) x0; sx < Math.min(sw, Math.ceil(x1)); sx++) {
                        double wgt = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        if (wgt <= 0) {
                            continue;
                        }
                        int argb = src.getRGB(sx, sy);
                        double a = (argb >>> 24) / 255.0 * wgt;
                        sa += a;
                        sr += ((argb >> 16) & 0xFF) * a;
                        sg += ((argb >> 8) & 0xFF) * a;
                        sb += (argb & 0xFF) * a;
                        sum += wgt;
                    }
                }
                int a = sum <= 0 ? 0 : (int) Math.round(sa / sum * 255);
                if (a == 0) {
                    continue;
                }
                int r = (int) Math.round(sr / sa);
                int g = (int) Math.round(sg / sa);
                int b = (int) Math.round(sb / sa);
                out.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return out;
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
            for (Picture p : allPictures()) {
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

    private List<Picture> allPictures() {
        List<Picture> all = new ArrayList<>(pictures.values());
        all.addAll(sized.values());
        return all;
    }

    /** Number of extra sizes in the pack. */
    public int sizes() {
        return sized.size();
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
