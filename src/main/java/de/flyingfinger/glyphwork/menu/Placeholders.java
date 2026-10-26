package de.flyingfinger.glyphwork.menu;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * %player%, %online%, %world%, %input% (in input actions) and, if PlaceholderAPI is installed,
 * all of its placeholders. PlaceholderAPI is called reflectively, so it is an optional dependency.
 */
final class Placeholders {

    private static volatile @Nullable Method papi;
    private static volatile boolean looked;

    private Placeholders() {
    }

    static String apply(@Nullable Player player, String text) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        String s = text;
        if (player != null) {
            s = s.replace("%player%", player.getName()).replace("%world%", player.getWorld().getName());
        }
        if (Bukkit.getServer() == null) {
            return s; // tests
        }
        s = s.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        Method m = papi();
        if (m != null && player != null && s.indexOf('%') >= 0) {
            try {
                s = (String) m.invoke(null, player, s);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // PlaceholderAPI failed for this text; keep it as is
            }
        }
        return s;
    }

    private static @Nullable Method papi() {
        if (!looked) {
            looked = true;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                        .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                } catch (ReflectiveOperationException ignored) {
                    papi = null;
                }
            }
        }
        return papi;
    }

    /** After a reload PlaceholderAPI may have been installed or removed. */
    static void reset() {
        looked = false;
        papi = null;
    }
}
