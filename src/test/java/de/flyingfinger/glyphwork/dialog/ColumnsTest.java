package de.flyingfinger.glyphwork.dialog;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plain dialogs: two narrow columns for short texts, one wide column otherwise. */
class ColumnsTest {

    private static DialogFactory.FallbackButton b(String text) {
        return new DialogFactory.FallbackButton(Component.text(text), null, () -> { });
    }

    @Test
    void columnsByTextLength() {
        assertEquals(2, DialogFactory.columnsFor(List.of(b("Shop"), b("Info"), b("Warps"))));
        assertEquals(1, DialogFactory.columnsFor(List.of(b("Shop"), b("This text is far too long for a narrow button"))));
        assertEquals(1, DialogFactory.columnsFor(List.of(b("Only one"))));
        assertEquals(2, DialogFactory.columnsFor(List.of(b("Diamond\n64 for 100"), b("Gold"))));   // per line
    }
}
