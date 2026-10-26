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

    /**
     * One referenced texture, registered as one glyph. {@code fromPack}: only the server's resource
     * pack has it (custom items), so it goes into its own fonts (see ShiftFonts).
     */
    public record Texture(String file, int frameSize, int frames, int advance, char codepoint, boolean fromPack) {
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
                c.textures.add(new Texture(t.get(0).getAsString(), frame, t.get(3).getAsInt(),
                    advance(t.get(4).getAsInt() - 1, frame), alloc.next(), false));
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

    /** The client scales the cell to the font height and rounds the visible width, then adds 1. */
    static int advance(int visibleWidth, int frame) {
        return (int) (0.5 + visibleWidth * (double) SIZE / frame) + 1;
    }

    /** What {@link #applyPack} changed. */
    public record PackResult(int remeasured, int packItems, int newTextures) {
    }

    /**
     * Adapts the icons to a resource pack (the server's): textures it replaces are measured again,
     * and its item definitions add or replace icons ("myserver:ruby", or vanilla ids it changes).
     */
    public PackResult applyPack(PackIcons pack, GlyphAllocator alloc) {
        Map<Character, Texture> replaced = new HashMap<>();
        int remeasured = 0;
        for (int i = 0; i < textures.size(); i++) {
            Texture t = textures.get(i);
            PackIcons.Measure m = pack.measure(t.file());
            if (m != null) {
                Texture n = new Texture(t.file(), m.frame(), m.frames(), advance(m.visibleWidth(), m.frame()), t.codepoint(),
                    t.fromPack());
                textures.set(i, n);
                replaced.put(t.codepoint(), n);
                remeasured++;
            }
        }
        if (!replaced.isEmpty()) {
            items.replaceAll((id, layers) -> layers.stream()
                .map(l -> new Layer(replaced.getOrDefault(l.texture().codepoint(), l.texture()), l.tint()))
                .toList());
        }
        Map<String, Texture> byFile = new HashMap<>();
        textures.forEach(t -> byFile.put(t.file(), t));
        int packItems = 0;
        int newTextures = 0;
        for (Map.Entry<String, List<PackIcons.RawLayer>> e : pack.items().entrySet()) {
            List<Layer> layers = new ArrayList<>();
            for (PackIcons.RawLayer r : e.getValue()) {
                Texture t = byFile.get(r.texture());
                if (t == null) {
                    PackIcons.Measure m = pack.measure(r.texture());
                    if (m == null) {
                        continue; // texture neither in the pack nor a known vanilla texture
                    }
                    t = new Texture(r.texture(), m.frame(), m.frames(), advance(m.visibleWidth(), m.frame()), alloc.next(), true);
                    textures.add(t);
                    byFile.put(t.file(), t);
                    newTextures++;
                }
                layers.add(new Layer(t, r.tint() == null ? null : TextColor.color(r.tint())));
            }
            if (!layers.isEmpty()) {
                items.put(e.getKey().toLowerCase(), List.copyOf(layers));
                packItems++;
            }
        }
        return new PackResult(remeasured, packItems, newTextures);
    }

    /** Icon layers of an item ("diamond", "minecraft:diamond" or "myserver:ruby"), or null if it has none. */
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

    /** Whether any texture comes only from the server's pack (then the pack needs the c fonts). */
    public boolean hasPackTextures() {
        return textures.stream().anyMatch(Texture::fromPack);
    }

    /**
     * Bitmap providers for an icon font shifted {@code shift} px down (see ShiftFonts).
     *
     * @param fromPack true: only textures from the server's pack; false: only vanilla textures
     */
    public void addProviders(JsonArray target, int shift, boolean fromPack) {
        for (Texture t : textures) {
            if (t.fromPack() != fromPack) {
                continue;
            }
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
