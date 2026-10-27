package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A screen: a fixed-size area with elements, shown as the body of a vanilla dialog. Positions are
 * fixed; content can depend on the viewer through {@code Function<View, ...>} bindings, which are
 * evaluated every time the scene is rendered. A scene can be built once and shown to many players.
 */
public final class Scene {

    private final String id;
    private final int width;
    private final int height;
    private final Function<View, Component> title;
    private final @Nullable Component exitLabel;
    private final List<Element> elements;
    private final @Nullable Duration refresh;
    private final @Nullable Consumer<Click> onOpen;
    private final @Nullable Consumer<Click> onClose;
    private final @Nullable List<Element> plain;
    private final int plainColumns;

    private Scene(Builder b) {
        this.refresh = b.refresh;
        this.onOpen = b.onOpen;
        this.onClose = b.onClose;
        this.id = b.id;
        this.width = b.width;
        this.height = b.height;
        this.title = b.title;
        this.exitLabel = b.exitLabel;
        this.elements = List.copyOf(b.elements);
        this.plain = b.plain == null ? null : List.copyOf(b.plain);
        this.plainColumns = b.plainColumns;
    }

    /**
     * @param id     unique id (e.g. "myplugin:settings"); per-player state is kept per id
     * @param width  pixels
     * @param height pixels (at most {@link Glyphwork#maxHeight()})
     */
    public static Builder builder(String id, int width, int height) {
        return new Builder(id, width, height);
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Function<View, Component> title() {
        return title;
    }

    /** Label of the dialog's exit button, or null for the default. */
    public @Nullable Component exitLabel() {
        return exitLabel;
    }

    public List<Element> elements() {
        return elements;
    }

    /** Runs when the scene is opened (not when it is redrawn or returned to with back). */
    public @Nullable Consumer<Click> onOpen() {
        return onOpen;
    }

    /** Runs when the player closes the screen while this scene is shown. */
    public @Nullable Consumer<Click> onClose() {
        return onClose;
    }

    /** How often the open scene redraws itself, or null if only after clicks. */
    /**
     * Elements of the plain dialog (players without the pack, Bedrock) instead of the ones derived
     * from {@link #elements()}; null: derived. Positions do not matter there, the order does.
     */
    public @Nullable List<Element> plain() {
        return plain;
    }

    /** Button columns of the plain dialog: 1 or 2, 0 = chosen by the text lengths. */
    public int plainColumns() {
        return plainColumns;
    }

    public @Nullable Duration refresh() {
        return refresh;
    }

    public static final class Builder {
        private final String id;
        private final int width;
        private final int height;
        private Function<View, Component> title = v -> Component.empty();
        private @Nullable Component exitLabel;
        private final List<Element> elements = new ArrayList<>();
        private @Nullable Duration refresh;
        private @Nullable Consumer<Click> onOpen;
        private @Nullable Consumer<Click> onClose;
        private @Nullable List<Element> plain;
        private int plainColumns;

        private Builder(String id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        /** Dialog title (shown above the scene). */
        public Builder title(Component title) {
            this.title = v -> title;
            return this;
        }

        public Builder title(Function<View, Component> title) {
            this.title = title;
            return this;
        }

        public Builder exitLabel(Component label) {
            this.exitLabel = label;
            return this;
        }

        public Builder add(Element... elements) {
            this.elements.addAll(List.of(elements));
            return this;
        }

        /**
         * Redraws the scene every {@code every} while a player has it open (at least half a second),
         * so bindings show fresh values: online lists, countdowns, balances. Clicks on the previous
         * drawing still work. Stops when the player closes the scene, opens another one or an
         * inventory. Each redraw sends the whole dialog again, so keep it at a few seconds.
         */
        public Builder refresh(Duration every) {
            if (every.toMillis() < 500) {
                throw new IllegalArgumentException("refresh must be at least 500 ms, got " + every);
            }
            this.refresh = every;
            return this;
        }

        /**
         * Runs when the scene is opened (by open, a command or a trigger), before it is drawn: set
         * state here and it shows right away. {@code reopen()} is not needed; {@code open(...)} or
         * {@code close()} replace the scene instead. Not run on redraws or when returning with back.
         */
        public Builder onOpen(Consumer<Click> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        /**
         * Runs when the player closes the screen while this scene is shown (exit button, ESC or a
         * close() call), e.g. for a message or a sound. The screen is gone: open, back and reopen do
         * nothing here.
         */
        public Builder onClose(Consumer<Click> onClose) {
            this.onClose = onClose;
            return this;
        }

        /**
         * A hand-made plain dialog (for players without the pack and Bedrock players): labels become
         * its text, buttons, lists, checkboxes and so on its buttons, in this order. Same scene, same
         * state as the pixel GUI.
         */
        public Builder plain(List<Element> elements) {
            this.plain = new ArrayList<>(elements);
            return this;
        }

        public Builder plain(Element... elements) {
            return plain(List.of(elements));
        }

        /** Button columns of the plain dialog: 1 or 2 (0 = by the text lengths, the default). */
        public Builder plainColumns(int columns) {
            this.plainColumns = Math.max(0, Math.min(2, columns));
            return this;
        }

        /** Shortcut: a full-size "panel" frame as background. */
        public Builder background() {
            return add(Panel.of(0, 0, width, height));
        }

        public Scene build() {
            return new Scene(this);
        }
    }
}
