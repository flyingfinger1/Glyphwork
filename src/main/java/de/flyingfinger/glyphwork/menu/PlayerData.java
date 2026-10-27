package de.flyingfinger.glyphwork.menu;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Values kept in the player's own data (PersistentDataContainer): saved with the player, survive
 * logouts and restarts, need no database. Keys live under the "glyphwork" namespace.
 */
final class PlayerData {

    private PlayerData() {
    }

    private static @Nullable PersistentDataContainer pdc(@Nullable Player p) {
        try {
            return p == null ? null : p.getPersistentDataContainer();
        } catch (RuntimeException e) {
            return null;    // not a real player (tests)
        }
    }

    /** A key safe for NamespacedKey: lower case, a-z 0-9 . _ - / only. */
    static NamespacedKey key(String key) {
        return new NamespacedKey("glyphwork", key.toLowerCase().replaceAll("[^a-z0-9._/-]", "_"));
    }

    static @Nullable String get(@Nullable Player p, String key) {
        PersistentDataContainer c = pdc(p);
        return c == null ? null : c.get(key(key), PersistentDataType.STRING);
    }

    /** {@code value} null removes the entry. */
    static void set(@Nullable Player p, String key, @Nullable String value) {
        PersistentDataContainer c = pdc(p);
        if (c == null) {
            return;
        }
        if (value == null) {
            c.remove(key(key));
        } else {
            c.set(key(key), PersistentDataType.STRING, value);
        }
    }

    static long getLong(@Nullable Player p, String key) {
        PersistentDataContainer c = pdc(p);
        Long v = c == null ? null : c.get(key(key), PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    static void setLong(@Nullable Player p, String key, long value) {
        PersistentDataContainer c = pdc(p);
        if (c != null) {
            c.set(key(key), PersistentDataType.LONG, value);
        }
    }
}
