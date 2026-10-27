package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;

import java.util.function.Consumer;

/**
 * Passed to click handlers. Handlers run on the main thread.
 *
 * <p>Implemented by Glyphwork; new methods may be added.
 */
@org.jetbrains.annotations.ApiStatus.NonExtendable
public interface Click extends View {

    /** Shows page {@code page} (0 = the first) of the {@link ListView} or {@link Grid} with this id. */
    default void page(String listId, int page) {
        set("glyphwork:page:" + listId, Math.max(0, page));
    }

    /**
     * Switches the {@link Tabs} with this id to the tab {@code tabId}. Tabs whose active tab comes
     * from {@link Tabs#selected} follow their own function instead.
     */
    default void tab(String tabsId, String tabId) {
        set("glyphwork:tab:" + tabsId, tabId);
    }

    /**
     * Scrolls the {@link Scroll} pane with this id to {@code offset} px (0 = the top; rounded down
     * to whole steps and limited to the content when drawn). For panes with
     * {@link Scroll#position} the key of that function counts.
     */
    default void scroll(String scrollId, int offset) {
        set("glyphwork:scroll:" + scrollId, Math.max(0, offset));
    }

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

    /**
     * Offers a web address: a small native dialog shows it with "Open" (the client then asks as for
     * chat links) and "Back" to the current scene.
     *
     * @throws IllegalArgumentException for other addresses than http and https
     */
    default void openUrl(String url) {
        player().sendMessage(Component.text(url).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url)));
    }
}
