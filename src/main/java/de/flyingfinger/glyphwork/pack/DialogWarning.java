package de.flyingfinger.glyphwork.pack;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.flyingfinger.glyphwork.core.Messages;
import de.flyingfinger.glyphwork.font.ImageCatalog;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Replaces the warning button that the client puts next to the title of every server dialog: its
 * three sprites (gui/sprites/dialog/warning_button*.png) with one of the server's pictures, and its
 * tooltip (menu.custom_screen_info.tooltip) with a text from the language files. The button stays:
 * it tells players that the screen comes from the server.
 */
public final class DialogWarning {

    private static final Gson GSON = new Gson();
    private static final String SPRITES = "assets/minecraft/textures/gui/sprites/dialog/";

    /** Minecraft locales of a Glyphwork language ("de" -> de_de, de_at, de_ch). */
    static final Map<String, List<String>> LOCALES = Map.of(
        "de", List.of("de_de", "de_at", "de_ch"),
        "en", List.of("en_us", "en_gb", "en_au", "en_ca", "en_nz"));

    private DialogWarning() {
    }

    public static void write(PackFiles pack, String icon, String iconHover, ImageCatalog images, Messages messages, Logger log) {
        if (!icon.isEmpty()) {
            byte[] normal = images.png(icon);
            if (normal == null) {
                log.warning("dialog-warning.icon: no picture '" + icon + "' in images/ (the vanilla sign stays)");
            } else {
                ImageCatalog.Picture p = images.get(icon);
                if (p.width() != p.height()) {
                    log.warning("dialog-warning.icon '" + icon + "' is " + p.width() + " x " + p.height()
                        + " px; the client stretches it to 20 x 20");
                }
                byte[] hover = iconHover.isEmpty() ? normal : images.png(iconHover);
                if (hover == null) {
                    log.warning("dialog-warning.icon-hover: no picture '" + iconHover + "' in images/ (using the icon)");
                    hover = normal;
                }
                pack.put(SPRITES + "warning_button.png", normal);
                pack.put(SPRITES + "warning_button_disabled.png", normal);
                pack.put(SPRITES + "warning_button_highlighted.png", hover);
            }
        }
        for (String lang : messages.languages()) {
            String text = messages.ownRaw(lang, "dialog.warning-tooltip");
            if (text == null || text.isBlank()) {
                continue;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(text));
            JsonObject json = new JsonObject();
            json.addProperty("menu.custom_screen_info.tooltip", plain);
            for (String locale : LOCALES.getOrDefault(lang, List.of(lang + "_" + lang))) {
                pack.put("assets/minecraft/lang/" + locale + ".json", GSON.toJson(json));
            }
        }
    }
}
