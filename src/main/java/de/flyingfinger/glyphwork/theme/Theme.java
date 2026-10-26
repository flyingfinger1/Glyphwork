package de.flyingfinger.glyphwork.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A skin: a tile size plus named frames, each an A2 autotile sheet.
 *
 * <p>On disk ({@code plugins/Glyphwork/themes/<id>/}): {@code theme.json}
 * <pre>{"name": "Vanilla", "tileSize": 8, "frames": {"panel": "panel.png", ...}}</pre>
 * plus one PNG per frame, each {@code 2*tileSize x 3*tileSize} px. See docs/skin-format.md.
 */
public final class Theme {

    /** Frames every theme must provide. */
    public static final List<String> REQUIRED = List.of("panel", "button", "slot");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String id;
    private final String name;
    private final int tileSize;
    private final Map<String, Frame> frames = new LinkedHashMap<>();

    private Theme(String id, String name, int tileSize) {
        this.id = id;
        this.name = name;
        this.tileSize = tileSize;
    }

    /** The built-in code skin (vanilla look), generated procedurally. */
    public static Theme builtin(GlyphAllocator alloc) {
        Theme t = new Theme("vanilla", "Vanilla", 8);
        t.frames.put("panel", new Frame(t.id, "panel", Autotile.a2(Style.PANEL, 8), 8, alloc));
        t.frames.put("button", new Frame(t.id, "button", Autotile.a2(Style.BUTTON, 8), 8, alloc));
        t.frames.put("slot", new Frame(t.id, "slot", Autotile.a2(Style.SLOT, 8), 8, alloc));
        return t;
    }

    public static Theme load(Path dir, GlyphAllocator alloc) throws IOException {
        JsonObject json = GSON.fromJson(Files.readString(dir.resolve("theme.json"), StandardCharsets.UTF_8), JsonObject.class);
        String id = dir.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        int tile = json.get("tileSize").getAsInt();
        if (tile < 8 || tile % 2 != 0) {
            throw new IOException("tileSize must be even and at least 8");
        }
        Theme t = new Theme(id, json.has("name") ? json.get("name").getAsString() : id, tile);
        for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("frames").entrySet()) {
            Path png = dir.resolve(e.getValue().getAsString());
            BufferedImage raw = ImageIO.read(png.toFile());
            if (raw == null) {
                throw new IOException("not a PNG: " + png);
            }
            BufferedImage argb = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(raw, 0, 0, null);
            String frame = e.getKey().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            try {
                t.frames.put(frame, new Frame(id, frame, argb, tile, alloc));
            } catch (IllegalArgumentException ex) {
                throw new IOException(png.getFileName() + ": " + ex.getMessage());
            }
        }
        for (String req : REQUIRED) {
            if (!t.frames.containsKey(req)) {
                throw new IOException("missing frame '" + req + "'");
            }
        }
        return t;
    }

    /** Writes this theme in the on-disk format (used to export the built-in skin as a starting point). */
    public void save(Path dir) throws IOException {
        Files.createDirectories(dir);
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("tileSize", tileSize);
        JsonObject f = new JsonObject();
        for (Frame frame : frames.values()) {
            String file = frame.name() + ".png";
            ImageIO.write(frame.a2(), "png", dir.resolve(file).toFile());
            f.addProperty(frame.name(), file);
        }
        json.add("frames", f);
        Files.writeString(dir.resolve("theme.json"), GSON.toJson(json), StandardCharsets.UTF_8);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int tileSize() {
        return tileSize;
    }

    public Frame frame(String name) {
        Frame f = frames.get(name);
        if (f == null) {
            throw new IllegalArgumentException("Theme " + id + " has no frame " + name);
        }
        return f;
    }

    public List<Sheet> sheets() {
        List<Sheet> s = new ArrayList<>();
        frames.values().forEach(f -> s.add(f.sheet()));
        return s;
    }
}
