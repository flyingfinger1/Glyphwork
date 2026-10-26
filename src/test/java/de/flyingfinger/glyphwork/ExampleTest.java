package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.example.Trader;
import de.flyingfinger.glyphwork.font.GlyphAllocator;
import de.flyingfinger.glyphwork.font.IconCatalog;
import de.flyingfinger.glyphwork.font.Sheet;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.render.SceneRenderer;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The example plugin's trader screen, rendered and hit-tested like the client does. */
class ExampleTest {

    private final VanillaText text = VanillaText.load();
    private final GlyphAllocator alloc = new GlyphAllocator();
    private final Theme theme = Theme.builtin(alloc);
    private final Sheet hit = Sheet.invisible(alloc);
    private final IconCatalog icons = IconCatalog.load(alloc);
    private final SceneRenderer renderer = new SceneRenderer(text, theme, hit.glyph(), 256, Logger.getLogger("test"), icons);
    private final Trader trader = new Trader();

    /** A player with an empty inventory (the trader counts emeralds). */
    private static Player fakePlayer() {
        PlayerInventory inv = (PlayerInventory) Proxy.newProxyInstance(ExampleTest.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class},
            (p, m, a) -> m.getName().equals("getContents") ? new ItemStack[0] : null);
        return (Player) Proxy.newProxyInstance(ExampleTest.class.getClassLoader(), new Class<?>[] {Player.class},
            (p, m, a) -> m.getName().equals("getInventory") ? inv : null);
    }

    private static final class TestView implements View {
        final Scene scene;
        final Player player = fakePlayer();
        final Map<String, Object> state = new HashMap<>();

        TestView(Scene scene) {
            this.scene = scene;
        }

        public Player player() {
            return player;
        }

        public Scene scene() {
            return scene;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key, T fallback) {
            return (T) state.getOrDefault(key, fallback);
        }

        public void set(String key, Object value) {
            state.put(key, value);
        }
    }

    private Layout layout(TestView view) {
        List<Sheet> sheets = new ArrayList<>(theme.sheets());
        sheets.add(hit);
        var line = renderer.render(view.scene, view, (name, action) -> ClickEvent.runCommand(name));
        return new Layout(line.build(view.scene.height()), sheets, text, icons);
    }

    @Test
    void traderClicksLandWhereThingsAreDrawn() throws Exception {
        Layout l = layout(new TestView(trader.main));
        l.writePreview(Path.of("build/preview/example-trader.png"), 176, 200, 4);
        String p = "example:trader#";
        assertEquals(p + "2:cell0", l.hit(22, 32));          // goods grid, 26 px pitch
        assertEquals(p + "2:cell9", l.hit(10 + 3 * 26 + 12, 20 + 26 + 12));
        assertNull(l.hit(10 + 4 * 26 + 12, 20 + 26 + 12));   // empty cell (10 offers in 12 cells)
        assertEquals(p + 8, l.hit(80, 140));                 // -
        assertEquals(p + 9, l.hit(100, 140));                // +
        assertEquals(p + 10, l.hit(140, 140));               // Eingabe
        assertEquals(p + 13, l.hit(40, 184));                // Optionen
        assertNull(l.hit(130, 184));                         // Kaufen: nothing selected, disabled
        assertEquals(11, l.icons.size());                    // one per offer + the emerald on "Kaufen"
    }

    @Test
    void notEnoughEmeraldsShowsRedAndKeepsBuyDisabled() {
        TestView view = new TestView(trader.main);
        view.set("offer", null);
        Layout empty = layout(view);
        assertTrue(empty.textRuns.stream().noneMatch(t -> t[3] == 0xC03030), "no red without a selection");

        Layout first = layout(new TestView(trader.main));
        // select the first offer the way a click does: through the grid's own handler
        TestView selected = new TestView(trader.main);
        selectFirst(selected);
        Layout l = layout(selected);
        assertTrue(l.textRuns.stream().anyMatch(t -> t[1] == 156 && t[3] == 0xC03030), "\"Du hast\" in red");
        assertNull(l.hit(130, 184));                         // still disabled: the player has 0 emeralds
        assertEquals(first.icons.size() + 1, l.icons.size()); // the selected item is shown as well
    }

    @SuppressWarnings("unchecked")
    private void selectFirst(TestView view) {
        var grid = (de.flyingfinger.glyphwork.api.Grid<Object>) trader.main.elements().stream()
            .filter(e -> e instanceof de.flyingfinger.glyphwork.api.Grid<?>).findFirst().orElseThrow();
        Object offer = grid.items().apply(view).getFirst();
        view.set("offer", offer);
    }
}
