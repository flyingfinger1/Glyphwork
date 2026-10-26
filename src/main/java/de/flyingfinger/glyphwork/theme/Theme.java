package de.flyingfinger.glyphwork.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import org.jspecify.annotations.Nullable;

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

    /** Default label color (vanilla GUI dark gray), used when theme.json has no "text". */
    public static final int DEFAULT_TEXT = 0x404040;

    /** A skin that ships with the plugin, generated from styles; exported to themes/<id>/ on start. */
    public record Builtin(String id, String name, Style panel, Style button, Style slot, int text) {
    }

    public static final List<Builtin> BUILTINS = List.of(
        new Builtin("vanilla", "Vanilla", Style.PANEL, Style.BUTTON, Style.SLOT, DEFAULT_TEXT),
        new Builtin("dunkel", "Dunkel", Style.DARK_PANEL, Style.DARK_BUTTON, Style.DARK_SLOT, 0xE0E0E0),
        new Builtin("pergament", "Pergament", Style.PARCHMENT_PANEL, Style.PARCHMENT_BUTTON, Style.PARCHMENT_SLOT, 0x4A3420));

    private final String id;
    private final String name;
    private final int tileSize;
    private final int text;
    private final Map<String, Frame> frames = new LinkedHashMap<>();

    private Theme(String id, String name, int tileSize, int text) {
        this.id = id;
        this.name = name;
        this.tileSize = tileSize;
        this.text = text;
    }

    /** The built-in code skin (vanilla look), generated procedurally. */
    public static Theme builtin(GlyphAllocator alloc) {
        return builtin(BUILTINS.getFirst(), alloc);
    }

    public static Theme builtin(Builtin b, GlyphAllocator alloc) {
        Theme t = new Theme(b.id(), b.name(), 8, b.text());
        t.frames.put("panel", new Frame(t.id, "panel", Autotile.a2(b.panel(), 8), 8, alloc));
        t.frames.put("button", new Frame(t.id, "button", Autotile.a2(b.button(), 8), 8, alloc));
        t.frames.put("slot", new Frame(t.id, "slot", Autotile.a2(b.slot(), 8), 8, alloc));
        return t;
    }

    public static Theme load(Path dir, GlyphAllocator alloc) throws IOException {
        JsonObject json = GSON.fromJson(Files.readString(dir.resolve("theme.json"), StandardCharsets.UTF_8), JsonObject.class);
        String id = dir.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        int tile = json.get("tileSize").getAsInt();
        if (tile < 8 || tile % 2 != 0) {
            throw new IOException("tileSize must be even and at least 8");
        }
        int text = DEFAULT_TEXT;
        if (json.has("text")) {
            String c = json.get("text").getAsString().trim();
            if (!c.matches("#[0-9a-fA-F]{6}")) {
                throw new IOException("text must be a color like \"#404040\", not \"" + c + "\"");
            }
            text = Integer.parseInt(c.substring(1), 16);
        }
        Theme t = new Theme(id, json.has("name") ? json.get("name").getAsString() : id, tile, text);
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
        json.addProperty("text", String.format("#%06X", text));
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

    /** Color of labels that use the default color ({@code Label.DARK}), as 0xRRGGBB. */
    public int text() {
        return text;
    }

    /** The frame, or null if this theme does not have it. */
    public @Nullable Frame find(String name) {
        return frames.get(name);
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
