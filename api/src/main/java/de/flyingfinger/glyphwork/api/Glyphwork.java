package de.flyingfinger.glyphwork.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
}
