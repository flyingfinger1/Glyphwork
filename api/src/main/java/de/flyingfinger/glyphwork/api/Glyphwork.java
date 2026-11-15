package de.flyingfinger.glyphwork.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for other plugins. Add {@code depend: [Glyphwork]} to your plugin.yml and compile
 * against the {@code glyphwork-api} artifact.
 *
 * <pre>{@code
 * Scene settings = Scene.builder("myplugin:settings", 176, 96)
 *     .title(Component.text("Einstellungen"))
 *     .add(Panel.of(0, 0, 176, 96))
 *     .add(Label.of(8, 7, "Einstellungen"))
 *     .add(Button.of(8, 24, 80, 16)
 *         .text(v -> "Sound: " + (soundOn(v.player()) ? "AN" : "AUS"))
 *         .onClick(click -> { toggleSound(click.player()); click.reopen(); }))
 *     .build();
 * Glyphwork.get().open(player, settings);
 * }</pre>
 */
@org.jetbrains.annotations.ApiStatus.NonExtendable
public interface Glyphwork {

    /** The running Glyphwork instance. */
    static Glyphwork get() {
        Glyphwork g = Bukkit.getServicesManager().load(Glyphwork.class);
        if (g == null) {
            throw new IllegalStateException("Glyphwork is not enabled");
        }
        return g;
    }

    /** Opens the scene as a new screen (clears the back history). */
    void open(Player player, Scene scene);

    /** Closes the Glyphwork screen of this player, if any. */
    void close(Player player);

    /** Whether the player has loaded the Glyphwork resource pack (without it, scenes are unreadable). */
    boolean hasPack(Player player);

    /** Tallest scene the generated pack can show, in pixels; frames below are not drawn. */
    int maxHeight();

    /** The player's display mode (default {@link DisplayMode#FULL}). */
    DisplayMode displayMode(Player player);

    /**
     * Sets the player's display mode; stored with the player. Use this to offer the choice in your
     * own settings (and set {@code players.commands: false} to hide {@code /glyphwork}).
     */
    void displayMode(Player player, DisplayMode mode);

    /** Sends the Glyphwork resource pack to the player (again), e.g. from your own command. */
    void sendPack(Player player);

    /**
     * Draws the player's open Glyphwork screen again, e.g. after another player changed what it
     * shows (a move in a game, a new bid). History, state and pages stay, clicks on the old drawing
     * still count. Nothing happens while the player is in a text prompt, a form or an inventory
     * (the screen is drawn fresh when they come back). Call it on the main thread.
     *
     * @return whether the screen was drawn again
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    boolean redraw(Player player);

    /**
     * {@link #redraw(Player)} for everyone who has the scene with this id open (shown, not only in
     * their back history), e.g. a lobby board. Call it on the main thread.
     *
     * @return how many screens were drawn again
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    int redraw(String sceneId);

    /**
     * The scene the player has open, or null: no Glyphwork screen, or it was closed. While the
     * player is in a text prompt or a form of that scene, it still counts as open.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    @Nullable Scene openScene(Player player);

    /**
     * Puts a size of a picture into the pack, for {@link Image#size(int, int)}. 0 for one side keeps
     * the aspect ratio; {@code *} in the name matches any text ({@code "ranks/*"}). Call it in your
     * {@code onEnable}: a new size makes Glyphwork build the pack again one tick later, before
     * players join. Later calls work too, but players then download the pack again.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    void imageSize(String image, int width, int height);

    /** Like {@link #imageSize(String, int, int)}, for {@link Image#scale(double)}. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    void imageScale(String image, double scale);
}
