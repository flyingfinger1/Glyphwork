package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tabs: a row of tabs on top of a panel, and the elements of the active tab. The active tab and the
 * panel are drawn as one frame (no edge between them), the other tabs a bit lower and darker behind
 * it. Only the active tab's elements are drawn and take clicks. The elements of a tab use scene coordinates (like every
 * element); the panel starts {@link #HEADER} px below {@code y}. Without {@link #selected} the active
 * tab is kept in the scene state for each viewer ({@link Click#tab} sets it), starting with the first.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public final class Tabs implements Element {

    /** Height of the tabs. */
    public static final int HEADER = 16;
    /** How much darker the inactive tabs are (multiplies the frame colors). */
    public static final TextColor INACTIVE = TextColor.color(0xB8B8B8);

    /** One tab: id, title on its button, optional item icon, its elements. Made by {@link Tabs#tab}. */
    public static final class Tab {
        private final String id;
        private final String title;
        private final @Nullable String icon;
        private final List<Element> elements;

        private Tab(String id, String title, @Nullable String icon, List<Element> elements) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.elements = elements;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public @Nullable String icon() {
            return icon;
        }

        public List<Element> elements() {
            return elements;
        }
    }

    private Predicate<View> visible = v -> true;

    private final String id;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Tab> tabs = new ArrayList<>();
    private @Nullable Function<View, @Nullable String> selected;
    private @Nullable BiConsumer<Click, String> onChange;
    private String frame = "panel";
    private TextColor tint = INACTIVE;

    private Tabs(String id, int x, int y, int width, int height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * @param id     unique within the scene (state key of the active tab)
     * @param height of everything, tab buttons included
     */
    public static Tabs of(String id, int x, int y, int width, int height) {
        return new Tabs(id, x, y, width, height);
    }

    public Tabs tab(String id, String title, Element... elements) {
        return tab(id, title, null, List.of(elements));
    }

    /** A tab with an item icon left of its title ("emerald", "head:Steve"). */
    public Tabs tab(String id, String title, @Nullable String icon, List<Element> elements) {
        tabs.add(new Tab(id, title, icon, List.copyOf(elements)));
        return this;
    }

    /** The active tab id per viewer (an unknown id shows the first tab). */
    public Tabs selected(Function<View, @Nullable String> selected) {
        this.selected = selected;
        return this;
    }

    /** Called with the id of the clicked tab (the scene is drawn again afterwards). */
    public Tabs onChange(BiConsumer<Click, String> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Frame of the panel and the tabs (default "panel"). */
    public Tabs frame(String frame) {
        this.frame = frame;
        return this;
    }

    /** Color of the inactive tabs (multiplies the frame colors; default {@link #INACTIVE}). */
    public Tabs inactiveTint(TextColor tint) {
        this.tint = tint;
        return this;
    }

    /** Shown only while this returns true; hidden elements do not take clicks. */
    public Tabs visible(Predicate<View> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public Predicate<View> visible() {
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

    @org.jetbrains.annotations.ApiStatus.Internal
    public int width() {
        return width;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public int height() {
        return height;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public List<Tab> tabs() {
        return List.copyOf(tabs);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public String frame() {
        return frame;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public TextColor inactiveTint() {
        return tint;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable BiConsumer<Click, String> onChange() {
        return onChange;
    }

    /** The active tab for this viewer (the first one if none or an unknown id is set). */
    @org.jetbrains.annotations.ApiStatus.Internal
    public @Nullable Tab active(View view) {
        if (tabs.isEmpty()) {
            return null;
        }
        String want = selected != null ? selected.apply(view) : view.get("glyphwork:tab:" + id, (String) null);
        for (Tab t : tabs) {
            if (t.id().equals(want)) {
                return t;
            }
        }
        return tabs.getFirst();
    }

    /** Makes {@code tab} active: onChange, or the scene state when there is no selected(...). */
    @org.jetbrains.annotations.ApiStatus.Internal
    public void select(Click c, String tab) {
        if (onChange != null) {
            onChange.accept(c, tab);
        }
        if (selected == null) {
            c.set("glyphwork:tab:" + id, tab);
        }
    }
}
