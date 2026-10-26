package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;

import java.util.function.Consumer;

/** Passed to click handlers. Handlers run on the main thread. */
public interface Click extends View {

    /** Renders the current scene again (after a state change). */
    void reopen();

    /** Opens another scene; {@link #back()} returns here. */
    void open(Scene next);

    /** Returns to the previous scene, or closes the screen if there is none. */
    void back();

    void close();

    /**
     * Text input: dialogs cannot read typing live, so this opens a small native dialog with a text
     * field. On confirm the handler gets the text; afterwards (also on cancel) the current scene is
     * shown again.
     */
    void prompt(Component title, String initial, Consumer<String> onSubmit);
}
