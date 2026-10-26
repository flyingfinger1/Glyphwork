package de.flyingfinger.glyphwork;

import com.google.gson.JsonArray;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.PackIcons;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** icon-source-pack: textures measured from the server's pack, its own items become icons. */
class SourcePackTest {

    private static byte[] png(int w, int h, int opaqueWidth) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < opaqueWidth; x++) {
                img.setRGB(x, y, 0xFFCC2244);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static Path samplePack(Path dir) throws Exception {
        Path file = dir.resolve("server-pack.zip");
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(os)) {
            // a narrower diamond (vanilla: 14 px wide) and an animated emerald (3 frames)
            put(zip, "assets/minecraft/textures/item/diamond.png", png(16, 16, 8));
            put(zip, "assets/minecraft/textures/item/emerald.png", png(16, 48, 16));
            // a custom item with its own texture
            put(zip, "assets/myserver/items/ruby.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"myserver:item/ruby\"}}".getBytes(StandardCharsets.UTF_8));
            put(zip, "assets/myserver/models/item/ruby.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"myserver:item/ruby\"}}".getBytes(StandardCharsets.UTF_8));
            put(zip, "assets/myserver/textures/item/ruby.png", png(16, 16, 12));
            // the pack changes a vanilla item to use the ruby model
            put(zip, "assets/minecraft/items/apple.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"myserver:item/ruby\"}}".getBytes(StandardCharsets.UTF_8));
            // an item whose model the pack does not contain stays as it is
            put(zip, "assets/minecraft/items/stick.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/not_in_pack\"}}".getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    @Test
    void measuresReplacedTexturesAndAddsPackItems(@TempDir Path dir) throws Exception {
        GlyphAllocator alloc = new GlyphAllocator();
        IconCatalog icons = IconCatalog.load(alloc);
        int before = icons.icon("diamond").getFirst().texture().advance();
        String stickBefore = icons.icon("stick").getFirst().texture().file();
        IconCatalog.PackResult r;
        try (ZipFile zip = new ZipFile(samplePack(dir).toFile())) {
            r = icons.applyPack(new PackIcons(zip), alloc);
        }
        assertEquals(2, r.remeasured());
        assertEquals(2, r.packItems());           // ruby, apple (stick could not be resolved)
        assertEquals(1, r.newTextures());         // myserver:item/ruby

        assertEquals(15, before);
        assertEquals(9, icons.icon("diamond").getFirst().texture().advance()); // 8 px visible + 1
        assertEquals(3, icons.icon("emerald").getFirst().texture().frames());

        assertNotNull(icons.icon("myserver:ruby"));
        assertEquals("myserver:item/ruby", icons.icon("myserver:ruby").getFirst().texture().file());
        assertEquals(13, icons.icon("myserver:ruby").getFirst().texture().advance());
        assertEquals("myserver:item/ruby", icons.icon("apple").getFirst().texture().file());
        assertEquals(stickBefore, icons.icon("stick").getFirst().texture().file());

        // the pack's fonts reference the new texture, animated textures use only their first frame
        JsonArray providers = new JsonArray();
        icons.addProviders(providers, 0);
        assertTrue(providers.asList().stream().anyMatch(p -> p.getAsJsonObject().get("file").getAsString()
            .equals("myserver:item/ruby.png")));
        var emerald = providers.asList().stream().map(p -> p.getAsJsonObject())
            .filter(p -> p.get("file").getAsString().equals("minecraft:item/emerald.png")).findFirst().orElseThrow();
        assertEquals(3, emerald.getAsJsonArray("chars").size());
    }
}
