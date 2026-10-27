package de.flyingfinger.glyphwork.render;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/** The faces of players for big heads: 8 x 8 pixels taken from their skins. */
public interface FaceSource {

    /**
     * The 64 face pixels (ARGB, row by row, hat layer already on top) of a player name or UUID, or
     * null while the skin is still loading or could not be found (then loading starts).
     */
    int @Nullable [] face(String who);

    /** Runs {@code then} on the main thread once one of these faces has finished loading. */
    default void whenLoaded(Set<String> who, Runnable then) {
    }

    /** Key under which a face is kept (lower-case name or UUID). */
    static String key(String who) {
        return who.trim().toLowerCase();
    }
}
