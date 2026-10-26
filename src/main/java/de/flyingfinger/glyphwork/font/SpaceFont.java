package de.flyingfinger.glyphwork.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;

/**
 * Cursor movement (primitive P1): a space provider with advances of +-2^k pixels.
 * Any horizontal offset is the sum of at most ~11 of these characters. The provider is part of
 * every shifted font and also available alone as {@link #KEY}.
 */
public final class SpaceFont {

    public static final Key KEY = Key.key("glyphwork", "space");
    private static final int BITS = 11; // up to +-2047 px
    private static final int POS_BASE = 0xE000;
    private static final int NEG_BASE = 0xE020;

    private SpaceFont() {
    }

    /** Characters that move the cursor by exactly {@code dx} pixels (may be negative). */
    public static String of(int dx) {
        if (Math.abs(dx) >= (1 << BITS)) {
            throw new IllegalArgumentException("Offset too large: " + dx);
        }
        int base = dx >= 0 ? POS_BASE : NEG_BASE;
        int n = Math.abs(dx);
        StringBuilder sb = new StringBuilder();
        for (int k = BITS - 1; k >= 0; k--) {
            if ((n & (1 << k)) != 0) {
                sb.append((char) (base + k));
            }
        }
        return sb.toString();
    }

    /** Advance of a cursor-space character, or {@code null} if {@code c} is none. */
    public static Integer advance(char c) {
        if (c >= POS_BASE && c < POS_BASE + BITS) {
            return 1 << (c - POS_BASE);
        }
        if (c >= NEG_BASE && c < NEG_BASE + BITS) {
            return -(1 << (c - NEG_BASE));
        }
        return null;
    }

    public static JsonObject provider() {
        JsonObject advances = new JsonObject();
        for (int k = 0; k < BITS; k++) {
            advances.addProperty(String.valueOf((char) (POS_BASE + k)), 1 << k);
            advances.addProperty(String.valueOf((char) (NEG_BASE + k)), -(1 << k));
        }
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        provider.add("advances", advances);
        return provider;
    }

    public static JsonObject fontJson() {
        JsonArray providers = new JsonArray();
        providers.add(provider());
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        return font;
    }
}
