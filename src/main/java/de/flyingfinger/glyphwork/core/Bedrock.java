package de.flyingfinger.glyphwork.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock players (Geyser, optionally Floodgate). They cannot load the Java resource pack and their
 * forms have no clickable text, so they always get the plain dialog, which Geyser turns into a
 * Bedrock form. Asked by reflection: neither plugin is needed.
 */
public final class Bedrock {

    private static volatile boolean looked;
    private static volatile @Nullable Object floodgate;
    private static volatile @Nullable Method isFloodgatePlayer;
    private static volatile @Nullable Object geyser;
    private static volatile @Nullable Method isBedrockPlayer;

    private Bedrock() {
    }

    /** Whether the player plays Bedrock Edition through Geyser. */
    public static boolean is(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        lookUp();
        UUID id = player.getUniqueId();
        try {
            if (isFloodgatePlayer != null && Boolean.TRUE.equals(isFloodgatePlayer.invoke(floodgate, id))) {
                return true;
            }
            if (isBedrockPlayer != null && Boolean.TRUE.equals(isBedrockPlayer.invoke(geyser, id))) {
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // an API that changed: treat as Java
        }
        return false;
    }

    private static synchronized void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        if (Bukkit.getServer() == null) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgate = api.getMethod("getInstance").invoke(null);
                isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                isFloodgatePlayer = null;
            }
        }
        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            try {
                Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyser = api.getMethod("api").invoke(null);
                isBedrockPlayer = api.getMethod("isBedrockPlayer", UUID.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                isBedrockPlayer = null;
            }
        }
    }

    /** Looks the plugins up again (after /glyphwork reload, if they were added). */
    public static synchronized void reset() {
        looked = false;
        floodgate = null;
        isFloodgatePlayer = null;
        geyser = null;
        isBedrockPlayer = null;
    }
}
