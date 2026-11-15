package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A label without a width that runs into a button is reported once. */
class LabelOverlapTest {

    private final List<String> warnings = new ArrayList<>();
    private final SceneRenderer renderer;

    LabelOverlapTest() {
        Logger log = Logger.getLogger("label-overlap-" + System.nanoTime());
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        GlyphAllocator alloc = new GlyphAllocator();
        renderer = new SceneRenderer(VanillaText.load(), Theme.builtin(alloc), Sheet.invisible(alloc).glyph(), 256, log);
    }

    private void draw(Scene s) {
        View v = new View() {
            public org.bukkit.entity.Player player() { return null; }
            public Scene scene() { return s; }
            public <T> T get(String key, T fallback) { return fallback; }
            public void set(String key, Object value) { }
        };
        renderer.render(s, v, (n, a) -> ClickEvent.runCommand(n)).build(s.height());
    }

    @Test
    void labelRunningIntoAButtonIsReportedOnce() {
        // the Minesweeper header: the buttons sat on the text
        Scene s = Scene.builder("mines", 200, 60)
            .add(Label.of(8, 7, "Minen: 10   Zeit: 0 s"))
            .add(Button.of(72, 4, 64, 16).text("Aufdecken"))
            .build();
        draw(s);
        draw(s);
        assertEquals(1, warnings.size(), warnings.toString());
        String w = warnings.getFirst();
        assertTrue(w.contains("mines#0") && w.contains("mines#1") && w.contains("62 fits"), w);
    }

    @Test
    void labelsThatFitOrHaveAWidthAreQuiet() {
        Scene s = Scene.builder("ok", 200, 60)
            .add(Label.of(8, 7, "Minen: 10   Zeit: 0 s").width(60))      // cut to fit: wanted
            .add(Label.of(8, 30, "Kurz"))                                   // ends before the button
            .add(Button.of(72, 4, 64, 16).text("Aufdecken"))
            .add(Button.of(72, 26, 64, 16).text("Neu"))
            .add(Label.of(140, 7, "rechts vom Button"))                     // starts right of it
            .add(Button.of(8, 40, 64, 16).text("unten").visible(v -> false)) // hidden: not checked
            .add(Label.of(8, 44, "Text unter einem versteckten Button"))
            .build();
        draw(s);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
