package de.flyingfinger.glyphwork.font;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.pack.PackFiles;
import net.kyori.adventure.key.Key;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Ascent matrix (primitive P2): one font per vertical offset y. Each contains the cursor spaces,
 * every sheet and the vanilla text glyphs with {@code ascent = normal - y}. Everything is drawn on
 * the first text line; y moves it down in pixels. Because spaces live in every font, a whole row of
 * tiles can be one text component.
 */
public final class ShiftFonts {

    private static final Gson GSON = new Gson();

    private ShiftFonts() {
    }

    public static Key key(int y) {
        return Key.key("glyphwork", "y" + y);
    }

    public static boolean isShiftFont(Key key) {
        return key != null && key.namespace().equals("glyphwork") && key.value().startsWith("y");
    }

    public static void write(PackFiles pack, Collection<Sheet> sheets, Collection<Integer> offsets, VanillaText text) {
        SortedSet<Integer> ys = new TreeSet<>(offsets);
        for (Sheet s : sheets) {
            pack.put(s.texturePath(), s.png());
        }
        for (int y : ys) {
            JsonArray providers = new JsonArray();
            providers.add(SpaceFont.provider());
            for (Sheet s : sheets) {
                providers.add(s.provider(y));
            }
            text.addShiftedProviders(providers, y);
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            pack.put("assets/glyphwork/font/y" + y + ".json", GSON.toJson(font));
        }
        pack.put("assets/glyphwork/font/space.json", GSON.toJson(SpaceFont.fontJson()));
    }
}
