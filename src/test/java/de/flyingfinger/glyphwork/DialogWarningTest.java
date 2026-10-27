package de.flyingfinger.glyphwork;

import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.core.Messages;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.ImageCatalog;
import de.flyingfinger.glyphwork.pack.DialogWarning;
import de.flyingfinger.glyphwork.pack.PackFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dialog warning button: own sprites and tooltip text in the pack. */
class DialogWarningTest {

    private static final String SPRITES = "assets/minecraft/textures/gui/sprites/dialog/";

    private static Map<String, byte[]> unzip(PackFiles pack) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(pack.toZip()))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                files.put(e.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    private static ImageCatalog images() {
        ImageCatalog c = ImageCatalog.empty();
        BufferedImage a = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        a.setRGB(3, 3, 0xFFFF0000);
        BufferedImage b = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(3, 3, 0xFF00FF00);
        GlyphAllocator alloc = new GlyphAllocator();
        c.add("schild", a, alloc);
        c.add("schild-hell", b, alloc);
        return c;
    }

    @Test
    void ownSpritesAndTooltip(@TempDir Path lang) throws Exception {
        Files.writeString(lang.resolve("de.yml"), "dialog.warning-tooltip: \"<gold>Ein Menü von <b>MeinServer</b>\"\n");
        Files.writeString(lang.resolve("en.yml"), "dialog.warning-tooltip: \"A MeinServer menu\"\n");
        Messages m = Messages.load(lang, "auto", "en", Logger.getLogger("test"));
        ImageCatalog img = images();
        PackFiles pack = new PackFiles();
        DialogWarning.write(pack, "schild", "schild-hell", img, m, Logger.getLogger("test"));
        Map<String, byte[]> f = unzip(pack);
        assertArrayEquals(img.png("schild"), f.get(SPRITES + "warning_button.png"));
        assertArrayEquals(img.png("schild"), f.get(SPRITES + "warning_button_disabled.png"));
        assertArrayEquals(img.png("schild-hell"), f.get(SPRITES + "warning_button_highlighted.png"));
        for (String locale : new String[] {"de_de", "de_at", "de_ch"}) {
            String json = new String(f.get("assets/minecraft/lang/" + locale + ".json"), StandardCharsets.UTF_8);
            assertEquals("Ein Menü von MeinServer",          // MiniMessage tags removed: lang files are plain
                JsonParser.parseString(json).getAsJsonObject().get("menu.custom_screen_info.tooltip").getAsString());
        }
        assertTrue(f.containsKey("assets/minecraft/lang/en_us.json"));
        assertTrue(f.containsKey("assets/minecraft/lang/en_gb.json"));
    }

    @Test
    void nothingByDefault(@TempDir Path lang) throws Exception {
        Messages m = Messages.load(lang, "auto", "en", Logger.getLogger("test"));
        PackFiles pack = new PackFiles();
        DialogWarning.write(pack, "", "", images(), m, Logger.getLogger("test"));
        DialogWarning.write(pack, "gibt-es-nicht", "", images(), m, Logger.getLogger("test"));
        Map<String, byte[]> f = unzip(pack);
        assertFalse(f.keySet().stream().anyMatch(k -> k.startsWith(SPRITES) || k.startsWith("assets/minecraft/lang/")),
            f.keySet().toString());
    }
}
