package de.flyingfinger.dialogui.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;

/**
 * Cursor movement (primitive P1): a space provider with advances of +-2^k pixels.
 * Any horizontal offset is the sum of at most ~10 of these characters.
 */
public final class SpaceFont {

    public static final Key KEY = Key.key("dialogui", "space");
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

    public static JsonObject fontJson() {
        JsonObject advances = new JsonObject();
        for (int k = 0; k < BITS; k++) {
            advances.addProperty(String.valueOf((char) (POS_BASE + k)), 1 << k);
            advances.addProperty(String.valueOf((char) (NEG_BASE + k)), -(1 << k));
        }
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        provider.add("advances", advances);
        JsonArray providers = new JsonArray();
        providers.add(provider);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        return font;
    }
}
