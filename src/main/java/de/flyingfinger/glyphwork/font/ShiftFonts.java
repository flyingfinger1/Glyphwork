package de.flyingfinger.glyphwork.font;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.pack.PackFiles;
import net.kyori.adventure.key.Key;

import java.util.Collection;

/**
 * Ascent matrix (primitive P2). Two families of fonts, both containing the cursor spaces:
 * <ul>
 *   <li>{@code glyphwork:y<N>} for N in [0, maxHeight): every sheet with {@code ascent = 7 - N}.
 *       Frames are all drawn on the first text line, N pixels down, so their paint order is exact.
 *       Sheets are tiny, so a font for every row is cheap.</li>
 *   <li>{@code glyphwork:t<S>} for S in [0, 8]: the vanilla text glyphs shifted S pixels down.
 *       Text is drawn on its own text line (y / 9) with the remainder as S. Vanilla font textures
 *       are large and each font loads them again, so only these 9 exist. Text lines come after the
 *       first line, so text is always drawn on top of frames.</li>
 * </ul>
 * The pack therefore depends only on the theme, not on the scenes.
 */
public final class ShiftFonts {

    public static final int LINE = 9;
    private static final Gson GSON = new Gson();

    private ShiftFonts() {
    }

    /** Font for sheet glyphs whose top is y pixels below the first line. */
    public static Key sprite(int y) {
        return Key.key("glyphwork", "y" + y);
    }

    /** Font for text whose top is s (0..8) pixels below its own text line. */
    public static Key text(int s) {
        return Key.key("glyphwork", "t" + s);
    }

    /**
     * Font for item icons, s (0..8) px below their text line. The client rejects a whole font when
     * one texture is missing, so each {@link IconCatalog.Group} has fonts of its own: a missing
     * server pack, or an older client without the newest textures, must not take the other icons
     * (or text) with it.
     */
    public static Key icon(int s, IconCatalog.Group group) {
        return Key.key("glyphwork", group.prefix() + String.valueOf(s));
    }

    /** Font for item icons, s px down: server-pack textures or vanilla ones every client has. */
    public static Key icon(int s, boolean fromPack) {
        return icon(s, fromPack ? IconCatalog.Group.PACK : IconCatalog.Group.VANILLA);
    }

    public static boolean isShiftFont(Key key) {
        if (key == null || !key.namespace().equals("glyphwork") || key.value().isEmpty()) {
            return false;
        }
        char c = key.value().charAt(0);
        return c == 'y' || c == 't' || c == 'i' || c == 'n' || c == 'c' || c == 'm' || c == 'f';
    }

    public static void write(PackFiles pack, Collection<Sheet> sheets, int maxHeight, VanillaText text,
                             @org.jspecify.annotations.Nullable IconCatalog icons) {
        for (Sheet s : sheets) {
            pack.put(s.texturePath(), s.png());
        }
        for (int y = 0; y < maxHeight; y++) {
            JsonArray providers = new JsonArray();
            providers.add(SpaceFont.provider());
            for (Sheet s : sheets) {
                providers.add(s.provider(y));
            }
            put(pack, "y" + y, providers);
        }
        for (int s = 0; s < LINE; s++) {
            JsonArray providers = new JsonArray();
            providers.add(SpaceFont.provider());
            text.addShiftedProviders(providers, s);
            put(pack, "t" + s, providers);
            if (icons != null) {
                for (IconCatalog.Group g : IconCatalog.Group.values()) {
                    if (g != IconCatalog.Group.VANILLA && !icons.has(g)) {
                        continue;
                    }
                    JsonArray iconProviders = new JsonArray();
                    iconProviders.add(SpaceFont.provider());
                    icons.addProviders(iconProviders, s, g);
                    put(pack, String.valueOf(g.prefix()) + s, iconProviders);
                }
            }
        }
        pack.put("assets/glyphwork/font/space.json", GSON.toJson(SpaceFont.fontJson()));
    }

    private static void put(PackFiles pack, String name, JsonArray providers) {
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        pack.put("assets/glyphwork/font/" + name + ".json", GSON.toJson(font));
    }
}
