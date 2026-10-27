package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item sprites in front of the texts of the plain dialog. */
class PlainPicturesTest {

    @AfterEach
    void tearDown() {
        FallbackRenderer.icons(null);
    }

    private static boolean isText(Component c) {
        return c instanceof TextComponent t && t.content().contains("<sprite");
    }

    @Test
    void atlasFollowsTheTexture() {
        Component item = FallbackRenderer.spriteOf("minecraft:item/diamond");
        Component block = FallbackRenderer.spriteOf("minecraft:block/grass_block_side");
        assertNotNull(item);
        assertNotNull(block);
        // MiniMessage knows the tag: an object component, not the tag as text
        assertFalse(isText(item), item.toString());
        assertTrue(item.toString().contains("minecraft:items") && item.toString().contains("item/diamond"), item.toString());
        assertTrue(block.toString().contains("minecraft:blocks"), block.toString());
        assertNull(FallbackRenderer.spriteOf("myserver:item/ruby"));       // only textures every client has
    }

    @Test
    void itemsFromTheCatalog() {
        FallbackRenderer.icons(IconCatalog.load(new GlyphAllocator()));
        assertNotNull(FallbackRenderer.sprite("diamond"));
        assertNotNull(FallbackRenderer.sprite("minecraft:golden_apple"));
        assertNull(FallbackRenderer.sprite("does_not_exist"));
        assertNull(FallbackRenderer.sprite(null));
    }
}
