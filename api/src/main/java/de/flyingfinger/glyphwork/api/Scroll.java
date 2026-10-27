package de.flyingfinger.glyphwork.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A scroll pane: a window of {@code width x height} px that shows part of its elements, with a
 * scroll bar on the right (▲, track, ▼). Clicks move the content by {@link #step} px; a click on the
 * track jumps there. Only what lies completely inside the window is drawn and takes clicks. The
 * elements use scene coordinates as if the pane were not scrolled. The position is kept in the scene
 * state ("glyphwork:scroll:&lt;id&gt;").
 */
public final class Scroll implements Element {

    /** Width of the scroll bar at the right edge of the pane. */
    public static final int BAR = 8;

    private Function<View, Boolean> visible = v -> true;

    private final String id;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Element> elements;
    private int step = 18;
    private @Nullable String frame;

    private Scroll(String id, int x, int y, int width, int height, List<Element> elements) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = Math.max(48, height);
        this.elements = List.copyOf(elements);
    }

    /**
     * @param id     unique within the scene (state key of the position)
     * @param height of the window (at least 48)
     */
    public static Scroll of(String id, int x, int y, int width, int height, List<Element> elements) {
        return new Scroll(id, x, y, width, height, elements);
    }

    public static Scroll of(String id, int x, int y, int width, int height, Element... elements) {
        return of(id, x, y, width, height, List.of(elements));
    }

    /** Pixels per click on ▲ / ▼ (default 18: two text lines; multiples of 9 keep player heads in place). */
    public Scroll step(int step) {
        this.step = Math.max(1, step);
        return this;
    }

    /** Frame drawn around the window (e.g. "slot"; default none). */
    public Scroll frame(@Nullable String frame) {
        this.frame = frame;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Scroll visible(Function<View, Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Function<View, Boolean> visible() {
        return visible;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
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

    public List<Element> elements() {
        return elements;
    }

    public int step() {
        return step;
    }

    public @Nullable String frame() {
        return frame;
    }

    /** State key of the scroll position. */
    public String key() {
        return "glyphwork:scroll:" + id;
    }

    /** The stored position (not yet limited to the content). */
    public int offset(View view) {
        Object o = view.get(key(), (Object) 0);
        return o instanceof Number n ? Math.max(0, n.intValue()) : 0;
    }
}
