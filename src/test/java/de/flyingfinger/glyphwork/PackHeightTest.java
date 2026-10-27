package de.flyingfinger.glyphwork;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pack gets as many y-fonts as the tallest menu needs. */
class PackHeightTest {

    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void followsTheTallestMenu() {
        assertEquals(256, GlyphworkPlugin.packHeight(256, List.of(96, 200), LOG));       // max-height is the minimum
        assertEquals(608, GlyphworkPlugin.packHeight(256, List.of(96, 600), LOG));       // rounded up to 32 px
        assertEquals(512, GlyphworkPlugin.packHeight(512, List.of(300), LOG));
        assertEquals(GlyphworkPlugin.HEIGHT_LIMIT, GlyphworkPlugin.packHeight(256, List.of(5000), LOG));
    }
}
