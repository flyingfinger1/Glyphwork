package de.flyingfinger.glyphwork.api;

/**
 * How a player sees Glyphwork screens. Players can choose it with {@code /glyphwork darstellung};
 * servers can set it through {@link Glyphwork#displayMode(org.bukkit.entity.Player, DisplayMode)}
 * (e.g. from their own settings menu). Stored with the player, survives restarts.
 */
public enum DisplayMode {

    /** Pixel GUI with item icons (default). */
    FULL,

    /**
     * Pixel GUI without item icons; grid cells show a short name instead. For players whose own
     * resource pack changes item textures so that icons no longer line up.
     */
    NO_ICONS,

    /**
     * The plain native dialog (the same as without the pack). For players whose resource pack
     * breaks the pixel GUI, e.g. by replacing the Minecraft font.
     */
    SIMPLE
}
