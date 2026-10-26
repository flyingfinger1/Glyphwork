package de.flyingfinger.glyphwork.api;

import org.bukkit.entity.Player;

/**
 * What a scene is rendered for: the viewer plus per-viewer state of the current scene. State lives
 * as long as the player is online and survives {@link Click#reopen()} and back navigation.
 */
public interface View {

    Player player();

    Scene scene();

    /** State value of this scene for this player, or {@code fallback}. */
    <T> T get(String key, T fallback);

    void set(String key, Object value);

    /** Current page (0-based) of a {@link ListView} or {@link Grid} with this id. */
    default int page(String listId) {
        return get("glyphwork:page:" + listId, 0);
    }
}
