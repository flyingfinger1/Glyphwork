package de.flyingfinger.glyphwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.demo.Demo;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.ShiftFonts;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item icons: catalog, rendering (positions, tints, layers) and the pack. */
class IconTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final IconCatalog icons = IconCatalog.load(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"), icons);

    private static final class TestClick implements Click {
        final Scene scene;
        final Map<String, Object> state = new HashMap<>();

        TestClick(Scene scene) {
            this.scene = scene;
        }

        @Override public Player player() { return null; }
        @Override public Scene scene() { return scene; }
        @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
        @Override public void set(String key, Object value) { state.put(key, value); }
        @Override public void reopen() { }
        @Override public void open(Scene next) { }
        @Override public void back() { }
        @Override public void close() { }
        @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
    }

    private Layout layout(Scene scene, TestClick view) {
        LineBuilder line = renderer.render(scene, view, (name, action) -> ClickEvent.runCommand(name));
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        return new Layout(line.build(scene.height()), sheets, text, icons);
    }

    @Test
    void catalogHasCommonItemsAndLeavesOutSpecialRenderers() {
        assertTrue(icons.itemCount() > 1500, "items: " + icons.itemCount());
        assertNotNull(icons.icon("diamond"));
        assertNotNull(icons.icon("minecraft:oak_log"));
        assertEquals(2, icons.icon("potion").size());          // overlay (tinted) + bottle
        assertNotNull(icons.icon("potion").getFirst().tint());
        assertNull(icons.icon("potion").get(1).tint());
        assertNotNull(icons.icon("oak_leaves").getFirst().tint()); // grayscale block face, tinted green
        assertNull(icons.icon("grass_block").getFirst().tint());   // side texture is already colored
        assertNull(icons.icon("player_head"));                     // special renderer
        assertNull(icons.icon("chest"));
        assertNull(icons.icon("no_such_item"));
    }

    @Test
    void gridIconsAreCenteredInTheirCells() {
        Demo demo = new Demo();
        TestClick view = new TestClick(demo.items);
        Layout l = layout(demo.items, view);
        // grid at (10, 20), pitch 26, cell 24 -> icon at cell + 4
        int[] first = l.icons.getFirst();
        assertEquals(14, first[0]);
        assertEquals(24, first[1]);
        // 24 cells, potion and leather items have two layers
        assertTrue(l.icons.size() >= 24, "icons: " + l.icons.size());
        // cells stay clickable where the slot frame is drawn, icons do not block
        assertEquals("glyphwork:demo-items#2:cell0", l.hit(20, 30));
        assertEquals("glyphwork:demo-items#2:cell7", l.hit(10 + 26 + 12, 20 + 26 + 12));
        // the grid's page buttons (126..142) stay free: nothing else is drawn there
        assertEquals("glyphwork:demo-items#2:next", l.hit(160, 134));
        assertTrue(l.icons.stream().noneMatch(i -> i[1] + 16 > 126 && i[1] < 142), "no icon over the pager");
        assertTrue(l.textRuns.stream().noneMatch(t -> t[0] == 8 && t[1] >= 120 && t[1] < 142),
            "no label in the pager row (the selection label used to sit there)");
        // the "Kaufen" button with an icon at its left
        assertEquals("glyphwork:demo-items#6", l.hit(140, 176));
        assertTrue(l.icons.stream().anyMatch(i -> i[0] == 99 && i[1] == 168), "button icon at 99,168");
    }

    @Test
    void tintsApplyToTintedLayersOnly() {
        Demo demo = new Demo();
        Layout l = layout(demo.items, new TestClick(demo.items));
        char overlay = icons.icon("potion").getFirst().texture().codepoint();
        char bottle = icons.icon("potion").get(1).texture().codepoint();
        int overlayTint = l.icons.stream().filter(i -> i[2] == overlay).findFirst().orElseThrow()[3];
        int bottleTint = l.icons.stream().filter(i -> i[2] == bottle).findFirst().orElseThrow()[3];
        assertEquals(icons.icon("potion").getFirst().tint().value(), overlayTint);
        assertEquals(0xFFFFFF, bottleTint);
    }

    @Test
    void yamlIcons() throws Exception {
        Menus m = new Menus();
        Menus.Menu menu = m.parse("shop", """
            elements:
              - type: icon
                x: 20
                y: 30
                item: diamond
                actions: ["close"]
              - type: grid
                x: 8
                y: 60
                cols: 2
                rows: 1
                cell: 26
                size: 24
                items:
                  - { icon: apple, tooltip: Apfel, actions: ["close"] }
                  - { icon: bread, actions: ["close"] }
              - type: button
                x: 80
                y: 30
                width: 72
                height: 24
                icon: emerald
                text: Kaufen
                actions: ["close"]
            """);
        Layout l = layout(menu.scene(), new TestClick(menu.scene()));
        assertTrue(l.icons.stream().anyMatch(i -> i[0] == 20 && i[1] == 30), "icon element");
        assertEquals("menu:shop#1", l.hit(25, 35));                       // icon element is clickable
        assertTrue(l.icons.stream().anyMatch(i -> i[0] == 12 && i[1] == 64), "grid icon");
        assertTrue(l.icons.stream().anyMatch(i -> i[0] == 83 && i[1] == 34), "button icon");
    }

    @Test
    void packWithIconsStaysReasonable() throws Exception {
        PackFiles pack = new PackFiles();
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        ShiftFonts.write(pack, sheets, 256, text, icons);
        byte[] zip = pack.toZip();
        assertTrue(zip.length < 2_500_000, "pack bytes: " + zip.length);
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.getName().equals("assets/glyphwork/font/t3.json")) {
                    JsonObject font = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    long refs = font.getAsJsonArray("providers").asList().stream()
                        .filter(p -> p.getAsJsonObject().get("file") != null
                            && p.getAsJsonObject().get("file").getAsString().startsWith("minecraft:item/"))
                        .count();
                    assertTrue(refs > 500, "item texture providers: " + refs);
                }
            }
        }
    }
}
