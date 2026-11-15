package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.api.CloseReason;
import de.flyingfinger.glyphwork.api.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** onClose with and without a reason: old handlers keep working, the new one hears why. */
class SceneCloseTest {

    @Test
    void oldHandlerRunsForEveryReason() {
        List<String> heard = new ArrayList<>();
        Scene s = Scene.builder("t", 16, 16).onClose(c -> heard.add("closed")).build();
        for (CloseReason r : CloseReason.values()) {
            s.closeHandler().accept(null, r);
        }
        assertEquals(3, heard.size());
        s.onClose().accept(null);
        assertEquals(4, heard.size());
    }

    @Test
    void newHandlerGetsTheReason() {
        List<CloseReason> heard = new ArrayList<>();
        Scene s = Scene.builder("t", 16, 16).onClose((c, reason) -> heard.add(reason)).build();
        s.closeHandler().accept(null, CloseReason.REPLACED);
        s.onClose().accept(null);                                   // old getter: a close by the player
        assertEquals(List.of(CloseReason.REPLACED, CloseReason.PLAYER), heard);
    }

    @Test
    void noHandler() {
        Scene s = Scene.builder("t", 16, 16).build();
        assertNull(s.closeHandler());
        assertNull(s.onClose());
    }
}
