package de.flyingfinger.glyphwork;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.core.Settings;
import de.flyingfinger.glyphwork.font.CustomFont;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.menu.MenuException;
import de.flyingfinger.glyphwork.menu.Menus;
import de.flyingfinger.glyphwork.pack.PackFiles;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Own fonts (config.yml fonts:): rasterized to a bitmap sheet whose widths the server knows exactly. */
class FontTest {

    private final VanillaText vanilla = VanillaText.load();

    private CustomFont pixelify() throws IOException {
        try (InputStream in = FontTest.class.getResourceAsStream("/fonts/PixelifySans.ttf")) {
            return CustomFont.rasterize("pixel", in.readAllBytes(), 10, 0, null, vanilla);
        }
    }

    @Test
    void widthsAreTheOnesTheClientShowed() throws IOException {
        CustomFont f = pixelify();
        assertTrue(f.glyphs() > 150, "Latin letters, umlauts and punctuation: " + f.glyphs());
        // measured in the client (Pixelify Sans 10 px, 2026-11-16): words placed by these widths lined up
        assertEquals(25, f.width("Hallo "));
        assertEquals(26, f.width("Welt, "));
        assertEquals(f.width("Hallo") + f.width(" ") + f.width("Welt"), f.width("Hallo Welt"));
        assertEquals(f.width(" "), f.width(String.valueOf(VanillaText.NB_SPACE)));
        // not in the font: the vanilla glyph and its width
        assertEquals(vanilla.width("→"), f.width("→"));
        assertEquals(13, f.lineHeight());                   // capitals 7 + descent 4 + 2
    }

    @Test
    void packFontsHaveTheSheetThenTheVanillaGlyphs() throws IOException {
        CustomFont f = pixelify();
        PackFiles pack = new PackFiles();
        f.write(pack);
        assertTrue(pack.has("assets/glyphwork/textures/font/f_pixel.png"));
        for (int s = 0; s < 9; s++) {
            JsonObject font = JsonParser.parseString(new String(pack.get("assets/glyphwork/font/f_pixel_" + s + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray p = font.getAsJsonArray("providers");
            assertEquals("space", p.get(0).getAsJsonObject().get("type").getAsString());       // cursor spaces
            JsonObject bitmap = p.get(2).getAsJsonObject();
            assertEquals("glyphwork:font/f_pixel.png", bitmap.get("file").getAsString());
            int ascent = bitmap.get("ascent").getAsInt();
            assertTrue(ascent <= bitmap.get("height").getAsInt(), "the client wants ascent <= height");
            assertEquals(ascent, JsonParser.parseString(new String(pack.get("assets/glyphwork/font/f_pixel_0.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers").get(2).getAsJsonObject()
                .get("ascent").getAsInt() - s);
            assertTrue(p.size() > 4, "vanilla glyphs follow as fallback");
        }
    }

    @Test
    void designerFileHasTheSheetAndTheWidths() throws IOException {
        CustomFont f = pixelify();
        String json = f.designerFile();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("glyphwork-font", o.get("format").getAsString());
        assertEquals("pixel", o.get("id").getAsString());
        assertEquals(f.lineHeight(), o.get("lineHeight").getAsInt());
        assertEquals(f.width("H") , o.getAsJsonObject("advances").get("H").getAsInt());
        byte[] png = java.util.Base64.getDecoder().decode(o.get("png").getAsString());
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertEquals(0, img.getWidth() % 16);
        assertEquals(o.getAsJsonArray("chars").size() * o.get("cellHeight").getAsInt(), img.getHeight());
        // for trying the menu designer: build/preview/pixel.glyphwork-font
        java.nio.file.Path out = java.nio.file.Path.of("build", "preview");
        java.nio.file.Files.createDirectories(out);
        java.nio.file.Files.writeString(out.resolve("pixel.glyphwork-font"), json);
    }

    @Test
    void notAFontIsARefusal() {
        IOException e = assertThrows(IOException.class,
            () -> CustomFont.rasterize("x", "kein font".getBytes(), 10, 0, null, vanilla));
        assertTrue(e.getMessage().contains("TrueType"), e.getMessage());
        assertThrows(IOException.class, () -> CustomFont.rasterize("Gross", new byte[0], 10, 0, null, vanilla));
        assertThrows(IOException.class, () -> CustomFont.rasterize("x", new byte[0], 99, 0, null, vanilla));
    }

    // --- drawing --------------------------------------------------------------------------------

    private record Drawn(String text, String font) {
    }

    private static void collect(Component c, List<Drawn> out) {
        if (c instanceof TextComponent t && !t.content().isEmpty() && c.font() != null) {
            out.add(new Drawn(t.content(), c.font().value()));
        }
        if (c.hoverEvent() != null && c.hoverEvent().value() instanceof Component tip) {
            out.add(new Drawn("tooltip:" + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(tip), tip.font() == null ? "default" : tip.font().value()));
        }
        for (Component child : c.children()) {
            collect(child, out);
        }
    }

    private List<Drawn> draw(Scene s, Map<String, CustomFont> fonts, Logger log) {
        GlyphAllocator alloc = new GlyphAllocator();
        SceneRenderer r = new SceneRenderer(vanilla, Theme.builtin(alloc), Sheet.invisible(alloc).glyph(), 256, log)
            .fonts(fonts::get);
        Component msg = r.render(s, click(s), (name, action) -> ClickEvent.runCommand(name)).build(s.height());
        List<Drawn> out = new ArrayList<>();
        collect(msg, out);
        return out;
    }

    @Test
    void elementsAndScenesChooseTheirFont() throws IOException {
        Map<String, CustomFont> fonts = Map.of("pixel", pixelify());
        Scene s = Scene.builder("t", 120, 60)
            .add(Label.of(4, 4, "Eigene"), Label.of(4, 20, "Minecraft").font("unbekannt"))
            .font("pixel")
            .build();
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        Set<String> used = new LinkedHashSet<>();
        for (Drawn d : draw(s, fonts, log)) {
            if (d.text().contains("Eigene")) {
                used.add("own:" + d.font());
            } else if (d.text().contains("Minecraft")) {
                used.add("vanilla:" + d.font());
            }
        }
        assertTrue(used.contains("own:f_pixel_4"), used.toString());               // y 4 -> shift 4
        assertTrue(used.stream().anyMatch(u -> u.startsWith("vanilla:t")), used.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unbekannt")), warnings.toString());
    }

    @Test
    void yamlNamesAFont() throws MenuException, IOException {
        Scene s = new Menus().parse("t", """
            font: Pixel
            elements:
              - { type: label, x: 4, y: 4, text: Hallo }
              - { type: button, x: 4, y: 20, text: Knopf, font: pixel }
            """).scene();
        assertEquals("pixel", s.font());
        List<Drawn> d = draw(s, Map.of("pixel", pixelify()), Logger.getAnonymousLogger());
        assertTrue(d.stream().anyMatch(x -> x.text().contains("Hallo") && x.font().startsWith("f_pixel_")), d.toString());
        assertTrue(d.stream().anyMatch(x -> x.text().contains("Knopf") && x.font().startsWith("f_pixel_")), d.toString());
        MenuException e = assertThrows(MenuException.class, () -> new Menus().parse("t", """
            elements:
              - { type: label, x: 4, y: 4, text: Hallo, font: "Mein Font" }
            """));
        assertTrue(e.getMessage().contains("font"), e.getMessage());
    }

    @Test
    void tabsHandTheirFontOnAndTooltipsFollow() throws MenuException, IOException {
        Scene s = new Menus().parse("t", """
            background: none
            elements:
              - type: tabs
                x: 0
                y: 0
                width: 160
                height: 60
                font: pixel
                tabs:
                  - { id: a, title: Reiter, elements: [{ type: label, x: 4, y: 4, text: Innen }] }
              - { type: checkbox, x: 4, y: 70, var: c, text: Haken, font: pixel, tooltip: Tipp }
              - { type: choice, x: 4, y: 90, width: 80, var: w, options: { a: Alpha }, font: pixel }
              - { type: button, x: 4, y: 110, text: Knopf, tooltip: Minecraft }
              - { type: grid, x: 100, y: 70, cols: 1, rows: 1, items: [{ text: Zelle }], font: pixel }
            """).scene();
        List<Drawn> d = draw(s, Map.of("pixel", pixelify()), Logger.getAnonymousLogger());
        for (String text : List.of("Reiter", "Innen", "Haken", "Alpha")) {
            assertTrue(d.stream().anyMatch(x -> x.text().contains(text) && x.font().startsWith("f_pixel_")), text + ": " + d);
        }
        assertTrue(d.stream().anyMatch(x -> x.text().equals("tooltip:Tipp") && x.font().equals("f_pixel_0")), d.toString());
        assertTrue(d.stream().anyMatch(x -> x.text().equals("tooltip:Minecraft") && x.font().equals("default")), d.toString());
        assertTrue(d.stream().noneMatch(x -> x.text().contains("Knopf") && x.font().startsWith("f_")), d.toString());
    }

    @Test
    void configReadsFonts() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("""
            fonts:
              titel: { file: Pixelify.ttf, size: 10 }
              web: { url: "https://example.org/x.ttf", size: 8, line-height: 11, chars: "ĀŌ" }
              kaputt: { size: 10 }
              zugross: { file: a.ttf, size: 99 }
              "Gross": { file: a.ttf, size: 10 }
            """);
        List<String> warn = new ArrayList<>();
        Consumer<String> w = warn::add;
        var m = Settings.class.getDeclaredMethod("fonts", org.bukkit.configuration.ConfigurationSection.class, Consumer.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Settings.FontSpec> fonts = (List<Settings.FontSpec>) m.invoke(null, c.getConfigurationSection("fonts"), w);
        assertEquals(List.of("titel", "web"), fonts.stream().map(Settings.FontSpec::id).toList());
        assertEquals(11, fonts.get(1).lineHeight());
        assertEquals("ĀŌ", fonts.get(1).chars());
        assertEquals(3, warn.size(), warn.toString());
        assertFalse(warn.isEmpty());
    }

    private static Click click(Scene scene) {
        Map<String, Object> state = new HashMap<>();
        return new Click() {
            @Override public Player player() { return null; }
            @Override public Scene scene() { return scene; }
            @Override @SuppressWarnings("unchecked") public <T> T get(String key, T fallback) { return (T) state.getOrDefault(key, fallback); }
            @Override public void set(String key, Object value) { state.put(key, value); }
            @Override public void reopen() { }
            @Override public void open(Scene next) { }
            @Override public void back() { }
            @Override public void close() { }
            @Override public void prompt(Component title, String initial, Consumer<String> onSubmit) { }
        };
    }
}
