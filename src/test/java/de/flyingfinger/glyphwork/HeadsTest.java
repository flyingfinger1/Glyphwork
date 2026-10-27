package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.render.FallbackRenderer;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player heads: 8 x 8, snapped to the 9 px line grid, text follows them, plain view shows them. */
class HeadsTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final IconCatalog icons = IconCatalog.load(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"), icons);

    private static View view(Scene s) {
        return new View() {
            public org.bukkit.entity.Player player() { return null; }
            public Scene scene() { return s; }
            public <T> T get(String key, T fallback) { return fallback; }
            public void set(String key, Object value) { }
        };
    }

    private Layout layout(Scene s) {
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        return new Layout(renderer.render(s, view(s), (n, a) -> ClickEvent.runCommand(n)).build(s.height()), sheets, text, icons);
    }

    private static void onGrid(int[] head) {
        assertEquals(0, (head[1] + 1) % 9, "head top " + head[1] + " is on the line grid (9 * n - 1)");
    }

    @Test
    void iconHeadSitsInTheMiddleOfItsPlaceWithinFourPixels() {
        for (int y = 10; y < 40; y++) {
            Scene s = Scene.builder("t", 64, 64).add(Icon.head(20, y, "Steve")).build();
            Layout l = layout(s);
            assertEquals(1, l.heads.size());
            int[] h = l.heads.getFirst();
            onGrid(h);
            assertEquals(24, h[0]);                                         // 4 px in from the icon's left
            assertTrue(Math.abs(h[1] - (y + 4)) <= 4, "y " + y + " -> top " + h[1]);
            assertEquals("Steve", ((PlayerHeadObjectContents) l.headContents.getFirst()).name());
        }
    }

    @Test
    void listRowTextFollowsTheHead() {
        Scene s = Scene.builder("t", 176, 120)
            .add(ListView.of("p", 8, 13, 120, 4, v -> List.of("Alex", "Steve", "Zoe")).icon(n -> "head:" + n)
                .onSelect((c, n) -> { }))
            .build();
        Layout l = layout(s);
        assertEquals(3, l.heads.size());
        for (int i = 0; i < 3; i++) {
            int[] h = l.heads.get(i);
            onGrid(h);
            int rowTop = 13 + 16 * i;
            assertTrue(h[1] >= rowTop - 4 && h[1] + 8 <= rowTop + 16 + 4, "row " + i + ": head " + h[1]);
            int headTop = h[1];
            assertTrue(l.textRuns.stream().anyMatch(t -> t[1] == headTop + 1 && t[0] == 8 + 4 + 8 + 3),
                "name next to head " + i);
        }
        assertEquals("t#0:row1", l.hit(60, 13 + 16 + 8));                // rows stay clickable
    }

    @Test
    void gridAndButtonHeads() {
        Scene s = Scene.builder("t", 176, 100)
            .add(Grid.of("g", 8, 8, 2, 1, v -> List.of("Alex", "Steve")).cell(26, 24).icon(n -> "head:" + n))
            .add(Button.of(8, 50, 100, 20).icon("head:Zoe").text("Zoe"))
            .build();
        Layout l = layout(s);
        assertEquals(3, l.heads.size());
        l.heads.forEach(HeadsTest::onGrid);
        int[] button = l.heads.get(2);
        assertEquals(12, button[0]);
        assertTrue(l.textRuns.stream().anyMatch(t -> t[1] == button[1] + 1), "button text follows the head");
    }

    @Test
    void namesAndUuidsAndNonsense() {
        assertNotNull(headOf("head:Steve"));
        Component byId = headOf("head:" + new UUID(1, 2));
        assertEquals(new UUID(1, 2), ((PlayerHeadObjectContents) ((ObjectComponent) byId).contents()).id());
        assertEquals(null, headOf("head:not a name!"));
        assertEquals(null, headOf("head:"));
        assertEquals(null, headOf("diamond"));
    }

    private static Component headOf(String item) {
        try {
            var m = SceneRenderer.class.getDeclaredMethod("headOf", String.class);
            m.setAccessible(true);
            return (Component) m.invoke(null, item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void plainViewPutsTheHeadInFrontOfTheLabel() {
        Scene s = Scene.builder("t", 176, 100)
            .add(ListView.of("p", 8, 8, 120, 4, v -> List.of("Alex")).icon(n -> "head:" + n).onSelect((c, n) -> { }))
            .build();
        var model = new FallbackRenderer(Logger.getLogger("test")).render(s, view(s));
        Component label = model.actions().getFirst().label();
        assertTrue(label.children().getFirst() instanceof ObjectComponent, "head first: " + label);
    }
}
