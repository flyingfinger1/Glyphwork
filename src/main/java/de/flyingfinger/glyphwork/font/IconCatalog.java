package de.flyingfinger.glyphwork.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item icons as glyphs. The pack only references the item and block textures every client already
 * has (no Minecraft image is shipped); {@code item_icons.json} (tools/extract_item_icons.py) says
 * which texture layers and tints make up each item's icon. Icons are 16 x 16 px and live in the 9
 * text fonts, like text: they are drawn on the text line they fall into, above all frames.
 */
public final class IconCatalog {

    public static final int SIZE = 16;

    /** One referenced texture, registered as one glyph. */
    public record Texture(String file, int frameSize, int frames, int advance, char codepoint) {
    }

    public record Layer(Texture texture, @Nullable TextColor tint) {
    }

    private final List<Texture> textures = new ArrayList<>();
    private final Map<String, List<Layer>> items = new HashMap<>();

    private IconCatalog() {
    }

    public static IconCatalog load(GlyphAllocator alloc) {
        try (InputStream in = IconCatalog.class.getResourceAsStream("/item_icons.json")) {
            if (in == null) {
                throw new IllegalStateException("item_icons.json missing");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            IconCatalog c = new IconCatalog();
            for (JsonElement e : root.getAsJsonArray("textures")) {
                JsonArray t = e.getAsJsonArray();
                int frame = t.get(2).getAsInt();
                int actualWidth = t.get(4).getAsInt() - 1;
                // the client scales the cell to the font height and rounds the visible width
                int advance = (int) (0.5 + actualWidth * (double) SIZE / frame) + 1;
                c.textures.add(new Texture(t.get(0).getAsString(), frame, t.get(3).getAsInt(), advance, alloc.next()));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
                List<Layer> layers = new ArrayList<>();
                for (JsonElement l : e.getValue().getAsJsonArray()) {
                    JsonArray a = l.getAsJsonArray();
                    TextColor tint = a.get(1).isJsonNull() ? null : TextColor.color(a.get(1).getAsInt());
                    layers.add(new Layer(c.textures.get(a.get(0).getAsInt()), tint));
                }
                c.items.put(e.getKey(), List.copyOf(layers));
            }
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Icon layers of an item ("diamond" or "minecraft:diamond"), or null if it has none. */
    public @Nullable List<Layer> icon(String item) {
        String id = item.toLowerCase();
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        return items.get(id);
    }

    public List<Texture> textures() {
        return textures;
    }

    public int itemCount() {
        return items.size();
    }

    public int textureCount() {
        return textures.size();
    }

    /** Bitmap providers for the text font shifted {@code shift} px down (see ShiftFonts). */
    public void addProviders(JsonArray target, int shift) {
        for (Texture t : textures) {
            JsonObject p = new JsonObject();
            p.addProperty("type", "bitmap");
            p.addProperty("file", t.file() + ".png");
            p.addProperty("height", SIZE);
            p.addProperty("ascent", 7 - shift);
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(t.codepoint()));
            for (int i = 1; i < t.frames(); i++) {
                chars.add("\0"); // animated textures are strips; only the first frame is used
            }
            p.add("chars", chars);
            target.add(p);
        }
    }
}
