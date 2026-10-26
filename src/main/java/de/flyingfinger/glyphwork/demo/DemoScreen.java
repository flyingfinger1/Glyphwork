package de.flyingfinger.glyphwork.demo;

import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.render.LineBuilder;
import de.flyingfinger.glyphwork.theme.Frame;
import de.flyingfinger.glyphwork.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

/**
 * Hard-coded test screen (M0 primitives + M1 autotiled frames). Opened with /glyphwork test.
 * <ul>
 *   <li>Every element is an autotiled frame from the active theme (panel, buttons, slots).</li>
 *   <li>Buttons sit at y = 22 and y = 31 (not multiples of 9).</li>
 *   <li>The L-shaped well exercises inner corners.</li>
 *   <li>The panel itself is clickable and must only fire where nothing else is drawn.</li>
 * </ul>
 */
public final class DemoScreen {

    public static final int W = 176;
    public static final int H = 128;
    public static final int SLOTS = 8;

    private static final TextColor DARK = TextColor.color(0x404040);
    private static final TextColor RED = TextColor.color(0xFF5050);
    private static final TextColor GREEN = TextColor.color(0x50E050);
    private static final TextColor BLUE = TextColor.color(0x6090FF);
    private static final TextColor GOLD = TextColor.color(0xFFE070);
    private static final boolean[][] WELL = LineBuilder.cells(
        "####################",
        "########............");

    private final VanillaText text;
    private final Theme theme;

    public DemoScreen(VanillaText text, Theme theme) {
        this.text = text;
        this.theme = theme;
    }

    public enum Target { PANEL, TOGGLE, COUNTER, CLOSE, WELL, SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7 }

    public static final class State {
        public boolean soundOn;
        public int count;
        public int selectedSlot = -1;
        public String lastClick = "-";
    }

    /** Vertical offsets the pack needs fonts for (rendered once without click events). */
    public Set<Integer> offsets() {
        return render(new State(), t -> null).offsets();
    }

    public LineBuilder render(State s, Function<Target, @Nullable ClickEvent<?>> clicks) {
        Frame panel = theme.frame("panel");
        Frame button = theme.frame("button");
        Frame slot = theme.frame("slot");
        LineBuilder line = new LineBuilder(text);

        line.frame(panel, 0, 0, W, H, null, clicks.apply(Target.PANEL), Component.text("Panel-Hintergrund"));
        line.text("Glyphwork - Demo (" + theme.name() + ")", 8, 7, DARK);

        line.frame(button, 152, 4, 16, 16, RED, clicks.apply(Target.CLOSE), Component.text("Schließen"));
        line.centered("x", 152, 16, 7, NamedTextColor.WHITE);

        line.frame(button, 8, 22, 72, 16, s.soundOn ? GREEN : RED, clicks.apply(Target.TOGGLE),
            Component.text("Klick zum Umschalten (y=22)"));
        line.centered(s.soundOn ? "Sound: AN" : "Sound: AUS", 8, 72, 26, NamedTextColor.WHITE);

        line.frame(button, 96, 31, 64, 16, BLUE, clicks.apply(Target.COUNTER), Component.text("Zähler +1 (y=31)"));
        line.centered("+1", 96, 64, 35, NamedTextColor.WHITE);

        line.text("Zähler: " + s.count, 8, 54, DARK);
        line.text("Letzter Klick: " + s.lastClick, 8, 65, DARK);

        for (int i = 0; i < SLOTS; i++) {
            Target t = Target.values()[Target.SLOT_0.ordinal() + i];
            line.frame(slot, 8 + i * 20, 78, 16, 16, i == s.selectedSlot ? GOLD : null, clicks.apply(t),
                Component.text("Zelle " + (i + 1)));
        }

        line.frame(slot, 8, 100, WELL, null, clicks.apply(Target.WELL), Component.text("L-Form (Innenecken)"));
        return line;
    }
}
