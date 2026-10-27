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

    /**
     * Several inputs at once (text, checkbox, slider, choice): opens the form as a native dialog with
     * OK and Cancel. On OK the handler gets the values; afterwards (also on Cancel or ESC) the current
     * scene is shown again.
     */
    default void form(Form form, Consumer<Form.Values> onSubmit) {
        throw new UnsupportedOperationException("forms are not supported by this Click");
    }
}
