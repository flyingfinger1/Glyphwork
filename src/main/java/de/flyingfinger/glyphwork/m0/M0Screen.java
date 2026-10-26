package de.flyingfinger.glyphwork.m0;

import de.flyingfinger.glyphwork.font.Art;
import de.flyingfinger.glyphwork.font.Sprite;
import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.render.LineBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * M0: one hard-coded screen that exercises the primitives.
 * <ul>
 *   <li>P1: everything is layered on one line via negative space (panel, then buttons, then text).</li>
 *   <li>P2: buttons sit at y = 22 and y = 31 (not multiples of 9); slots at y = 79.</li>
 *   <li>P3: every clickable sprite carries its own click event; the panel below has one too,
 *       which must only fire where no button is drawn.</li>
 * </ul>
 */
public final class M0Screen {

    public static final int W = 176;
    public static final int H = 96;
    public static final int SLOTS = 8;

    private static final TextColor DARK = TextColor.color(0x404040);

    public final Sprite panel = new Sprite("m0_panel", Art.panel(W, H), '');
    public final Sprite toggleOff = new Sprite("m0_toggle_off", Art.button(72, 20, 0xA03030), '');
    public final Sprite toggleOn = new Sprite("m0_toggle_on", Art.button(72, 20, 0x30A030), '');
    public final Sprite counter = new Sprite("m0_counter", Art.button(64, 20, 0x3060B0), '');
    public final Sprite close = new Sprite("m0_close", Art.button(11, 11, 0xB03030), '');
    public final Sprite slot = new Sprite("m0_slot", Art.slot(16, 12, 0x8B8B8B), '');
    public final Sprite slotSelected = new Sprite("m0_slot_sel", Art.slot(16, 12, 0xE0C040), '');

    private final VanillaText text;

    public M0Screen(VanillaText text) {
        this.text = text;
    }

    public List<Sprite> sprites() {
        return List.of(panel, toggleOff, toggleOn, counter, close, slot, slotSelected);
    }

    /** Click targets; the plugin maps them to callbacks. */
    public enum Target { PANEL, TOGGLE, COUNTER, CLOSE, SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7 }

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
        LineBuilder line = new LineBuilder(text);
        line.sprite(panel, 0, 0, clicks.apply(Target.PANEL), Component.text("Panel-Hintergrund"));
        line.text("Glyphwork - M0", 8, 7, DARK);

        line.sprite(s.soundOn ? toggleOn : toggleOff, 8, 22, clicks.apply(Target.TOGGLE),
            Component.text("Klick zum Umschalten (y=22)"));
        line.centered(s.soundOn ? "Sound: AN" : "Sound: AUS", 8, 72, 28, NamedTextColor.WHITE);

        line.sprite(counter, 96, 31, clicks.apply(Target.COUNTER), Component.text("Zähler +1 (y=31)"));
        line.centered("+1", 96, 64, 37, NamedTextColor.WHITE);

        line.sprite(close, 160, 5, clicks.apply(Target.CLOSE), Component.text("Schließen"));
        line.centered("x", 160, 11, 6, NamedTextColor.WHITE);

        line.text("Zähler: " + s.count, 8, 56, DARK);
        line.text("Letzter Klick: " + s.lastClick, 8, 67, DARK);

        for (int i = 0; i < SLOTS; i++) {
            Target t = Target.values()[Target.SLOT_0.ordinal() + i];
            line.sprite(i == s.selectedSlot ? slotSelected : slot, 8 + i * 20, 79, clicks.apply(t),
                Component.text("Zelle " + (i + 1)));
        }
        return line;
    }
}
