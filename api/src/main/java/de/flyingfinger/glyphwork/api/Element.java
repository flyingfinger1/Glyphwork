package de.flyingfinger.glyphwork.api;

import java.util.function.Function;

/**
 * A part of a {@link Scene}. Elements are drawn in the order they were added (later = on top);
 * text is always drawn above frames. Coordinates are GUI pixels from the scene's top-left corner.
 * Elements are configured with fluent setters and must not be changed after the scene is built.
 */
public sealed interface Element permits Panel, Label, Button, Hitbox, Icon, ListView, Grid, Slider, Checkbox, Choice, Bar, Image, Head, Tabs {

    int x();

    int y();

    /** Whether the element is shown for this view (default: always); set with {@code .visible(v -> ...)}. */
    Function<View, Boolean> visible();
}
