package de.flyingfinger.glyphwork.api;

/**
 * Why a scene's screen ended, for {@link Scene.Builder#onClose(java.util.function.BiConsumer)}.
 * More reasons may be added later, so give a {@code switch} over them a default branch.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public enum CloseReason {
    /** The player closed it: Esc or the dialog's exit button. */
    PLAYER,
    /** Another screen took its place: {@link Glyphwork#open} while it was shown. */
    REPLACED,
    /** Code closed it: {@link Glyphwork#close} or {@link Click#close()}. */
    CLOSED_BY_CODE
}
