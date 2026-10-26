package de.flyingfinger.glyphwork.font;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.pack.PackFiles;
import net.kyori.adventure.key.Key;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Ascent matrix (primitive P2): one font per vertical offset y, each containing every sprite
 * and the vanilla text glyphs with {@code ascent = normal - y}. Everything is drawn on the first
 * text line; y moves it down in pixels.
 */
public final class ShiftFonts {

    private static final Gson GSON = new Gson();

    private ShiftFonts() {
    }

    public static Key key(int y) {
        return Key.key("glyphwork", "y" + y);
    }

    public static void write(PackFiles pack, Collection<Sprite> sprites, Collection<Integer> offsets, VanillaText text) {
        SortedSet<Integer> ys = new TreeSet<>(offsets);
        for (Sprite s : sprites) {
            pack.put(s.texturePath(), s.png());
        }
        for (int y : ys) {
            JsonArray providers = new JsonArray();
            for (Sprite s : sprites) {
                JsonObject p = new JsonObject();
                p.addProperty("type", "bitmap");
                p.addProperty("file", s.textureId());
                p.addProperty("height", s.height());
                p.addProperty("ascent", 7 - y);
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf(s.codepoint()));
                p.add("chars", chars);
                providers.add(p);
            }
            text.addShiftedProviders(providers, y);
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            pack.put("assets/glyphwork/font/y" + y + ".json", GSON.toJson(font));
        }
        pack.put("assets/glyphwork/font/space.json", GSON.toJson(SpaceFont.fontJson()));
    }

    public static List<Integer> sorted(Collection<Integer> offsets) {
        return List.copyOf(new TreeSet<>(offsets));
    }
}
