package de.flyingfinger.glyphwork.api;

/**
 * A part of a {@link Scene}. Elements are drawn in the order they were added (later = on top);
 * text is always drawn above frames. Coordinates are GUI pixels from the scene's top-left corner.
 * Elements are configured with fluent setters and must not be changed after the scene is built.
 */
public sealed interface Element permits Panel, Label, Button, Hitbox, Icon, ListView, Grid {

    int x();

    int y();
}
