package de.flyingfinger.glyphwork.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a resource pack (the one the server sends) so icons match it: textures it replaces are
 * measured again (width, animation frames), and item definitions it adds or changes
 * ({@code assets/<ns>/items/<id>.json}) become icons, e.g. "myserver:ruby". Same rules as
 * tools/extract_item_icons.py.
 */
public final class PackIcons {

    /** Measured texture: frame size, frame count and visible width (in texture pixels). */
    public record Measure(int width, int frame, int frames, int visibleWidth) {
    }

    /** One icon layer before glyphs are assigned: texture id ("minecraft:item/x") and tint. */
    public record RawLayer(String texture, @Nullable Integer tint) {
    }

    private static final List<String> FACES = List.of("layer0", "all", "front", "side", "texture", "top", "end",
        "cross", "plant", "pattern", "wool", "fan");
    private static final List<String> PREFER = List.of("north", "east", "south", "west", "wall", "up", "flower");

    private final ZipFile zip;

    public PackIcons(ZipFile zip) {
        this.zip = zip;
    }

    private @Nullable byte[] bytes(String path) {
        ZipEntry e = zip.getEntry(path);
        if (e == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(e)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            return null;
        }
    }

    private @Nullable JsonObject json(String path) {
        byte[] b = bytes(path);
        if (b == null) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(new String(b, StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String[] split(String id) {
        int c = id.indexOf(':');
        return c < 0 ? new String[]{"minecraft", id} : new String[]{id.substring(0, c), id.substring(c + 1)};
    }

    /** Texture id ("minecraft:item/diamond") -> measure, if the pack contains that texture. */
    public @Nullable Measure measure(String texture) {
        String[] id = split(texture);
        byte[] png = bytes("assets/" + id[0] + "/textures/" + id[1] + ".png");
        if (png == null) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            int frame = h > w && h % w == 0 ? w : h;
            int visible = 0;
            for (int x = w - 1; x >= 0 && visible == 0; x--) {
                for (int y = 0; y < frame; y++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) {
                        visible = x + 1;
                        break;
                    }
                }
            }
            return new Measure(w, frame, h / frame, visible);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /** All item definitions in the pack: "ruby" (minecraft) or "myserver:ruby" -> layers. */
    public Map<String, List<RawLayer>> items() {
        Map<String, List<RawLayer>> out = new LinkedHashMap<>();
        for (ZipEntry e : Collections.list(zip.entries())) {
            String n = e.getName();
            if (!n.startsWith("assets/") || !n.endsWith(".json")) {
                continue;
            }
            String[] parts = n.split("/", 4); // assets, ns, items, rest
            if (parts.length < 4 || !parts[2].equals("items")) {
                continue;
            }
            String ns = parts[1];
            String id = parts[3].substring(0, parts[3].length() - ".json".length());
            JsonObject def = json(n);
            if (def == null || !def.has("model") || !def.get("model").isJsonObject()) {
                continue;
            }
            List<RawLayer> layers = resolve(def.getAsJsonObject("model"));
            if (!layers.isEmpty()) {
                out.put(ns.equals("minecraft") ? id : ns + ":" + id, layers);
            }
        }
        return out;
    }

    private record Leaf(String model, JsonArray tints) {
    }

    private static @Nullable Leaf leaf(JsonObject m) {
        String type = m.has("type") ? m.get("type").getAsString().replace("minecraft:", "") : "";
        switch (type) {
            case "model" -> {
                return new Leaf(m.get("model").getAsString(), m.has("tints") ? m.getAsJsonArray("tints") : new JsonArray());
            }
            case "select" -> {
                if (m.has("property") && m.get("property").getAsString().replace("minecraft:", "").equals("display_context")
                    && m.has("cases")) {
                    for (JsonElement c : m.getAsJsonArray("cases")) {
                        JsonElement when = c.getAsJsonObject().get("when");
                        boolean gui = when.isJsonArray() ? when.getAsJsonArray().contains(new com.google.gson.JsonPrimitive("gui"))
                            : "gui".equals(when.getAsString());
                        if (gui) {
                            return leaf(c.getAsJsonObject().getAsJsonObject("model"));
                        }
                    }
                }
                if (m.has("fallback")) {
                    return leaf(m.getAsJsonObject("fallback"));
                }
                JsonArray cases = m.has("cases") ? m.getAsJsonArray("cases") : new JsonArray();
                return cases.isEmpty() ? null : leaf(cases.get(0).getAsJsonObject().getAsJsonObject("model"));
            }
            case "condition" -> {
                return m.has("on_false") ? leaf(m.getAsJsonObject("on_false")) : null;
            }
            case "range_dispatch" -> {
                if (m.has("fallback")) {
                    return leaf(m.getAsJsonObject("fallback"));
                }
                JsonArray entries = m.has("entries") ? m.getAsJsonArray("entries") : new JsonArray();
                return entries.isEmpty() ? null : leaf(entries.get(0).getAsJsonObject().getAsJsonObject("model"));
            }
            case "composite" -> {
                JsonArray models = m.has("models") ? m.getAsJsonArray("models") : new JsonArray();
                return models.isEmpty() ? null : leaf(models.get(0).getAsJsonObject());
            }
            case "special" -> {
                return null;
            }
            default -> {
                return m.has("fallback") ? leaf(m.getAsJsonObject("fallback")) : null;
            }
        }
    }

    private List<RawLayer> resolve(JsonObject model) {
        Leaf leaf;
        try {
            leaf = leaf(model);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (leaf == null) {
            return List.of();
        }
        // follow parents inside the pack; vanilla parents are not available, only "generated" matters
        Map<String, String> textures = new LinkedHashMap<>();
        boolean generated = false;
        String cur = leaf.model();
        for (int depth = 0; cur != null && depth < 16; depth++) {
            String[] id = split(cur);
            if (id[1].equals("item/generated") || id[1].startsWith("item/handheld")) {
                generated = true;
                break;
            }
            JsonObject m = json("assets/" + id[0] + "/models/" + id[1] + ".json");
            if (m == null) {
                break;
            }
            if (m.has("textures")) {
                for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
                    JsonElement v = t.getValue();
                    if (v.isJsonObject() && v.getAsJsonObject().has("sprite")) {
                        v = v.getAsJsonObject().get("sprite");
                    }
                    if (v.isJsonPrimitive()) {
                        textures.putIfAbsent(t.getKey(), v.getAsString());
                    }
                }
            }
            cur = m.has("parent") ? m.get("parent").getAsString() : null;
        }
        textures.replaceAll((k, v) -> deref(textures, v));
        textures.values().removeIf(v -> v == null || v.startsWith("#"));

        List<RawLayer> layers = new ArrayList<>();
        if (generated) {
            for (int i = 0; textures.containsKey("layer" + i); i++) {
                layers.add(new RawLayer(full(textures.get("layer" + i)), tint(leaf.tints(), i)));
            }
        }
        if (layers.isEmpty()) {
            String face = FACES.stream().filter(textures::containsKey).findFirst().orElse(null);
            if (face == null) {
                face = textures.keySet().stream().filter(f -> !f.equals("particle"))
                    .sorted((a, b) -> Integer.compare(rank(a), rank(b))).findFirst().orElse(null);
            }
            if (face != null) {
                layers.add(new RawLayer(full(textures.get(face)), tint(leaf.tints(), 0)));
            }
        }
        return layers;
    }

    private static int rank(String face) {
        int i = PREFER.indexOf(face);
        return (i < 0 ? PREFER.size() : i) * 2 + (face.equals("down") || face.equals("bottom") ? 1 : 0);
    }

    private static @Nullable String deref(Map<String, String> textures, @Nullable String v) {
        for (int i = 0; v != null && v.startsWith("#") && i < 10; i++) {
            v = textures.get(v.substring(1));
        }
        return v;
    }

    private static String full(String texture) {
        return texture.contains(":") ? texture : "minecraft:" + texture;
    }

    private static @Nullable Integer tint(JsonArray tints, int index) {
        if (index >= tints.size() || !tints.get(index).isJsonObject()) {
            return null;
        }
        JsonObject t = tints.get(index).getAsJsonObject();
        String type = t.has("type") ? t.get("type").getAsString().replace("minecraft:", "") : "";
        if (type.equals("constant") && t.has("value")) {
            return t.get("value").getAsInt() & 0xFFFFFF;
        }
        if (t.has("default")) {
            return t.get("default").getAsInt() & 0xFFFFFF;
        }
        return type.equals("grass") ? 0x7CBD6B : null;
    }
}
