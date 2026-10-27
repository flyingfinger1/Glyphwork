package de.flyingfinger.glyphwork.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * What a scene is rendered for: the viewer plus per-viewer state of the current scene. State lives
 * as long as the player is online and survives {@link Click#reopen()} and back navigation.
 *
 * <p>Implemented by Glyphwork; new methods may be added. Keys starting with {@code "glyphwork:"}
 * are reserved for Glyphwork's own state (pages, tabs, scroll positions).
 */
@ApiStatus.NonExtendable
public interface View {

    Player player();

    Scene scene();

    /** State value of this scene for this player, or {@code fallback} if there is none (unchecked cast). */
    <T extends @Nullable Object> T get(String key, T fallback);

    /** Sets a state value of this scene for this player; null removes it. */
    void set(String key, @Nullable Object value);

    /**
     * Asks for the scene to be drawn again after {@code delay} while it stays open, because what a
     * binding shows will change by itself then (a countdown, a cooldown running out). Called while
     * rendering; the shortest delay of a drawing wins. Does nothing outside a drawing.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default void redrawAfter(java.time.Duration delay) {
    }

    /** Current page (0-based) of a {@link ListView} or {@link Grid} with this id. */
    default int page(String listId) {
        return get("glyphwork:page:" + listId, 0);
    }
}
