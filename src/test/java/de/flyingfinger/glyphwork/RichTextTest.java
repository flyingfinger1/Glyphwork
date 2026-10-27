package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.font.VanillaText;
import de.flyingfinger.glyphwork.render.RichText;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formatted pixel text: pieces, widths with bold, cutting and wrapping. */
class RichTextTest {

    private static final VanillaText TEXT = VanillaText.load();
    private static final RichText.Look BASE = new RichText.Look(TextColor.color(0x404040), false, false, false, false);

    @Test
    void piecesAndLooks() {
        List<RichText.Span> s = RichText.parse("Price: <b>100</b> <gold><u>coins", BASE);
        assertEquals(List.of("Price: ", "100", " ", "coins"), s.stream().map(RichText.Span::text).toList());
        assertTrue(s.get(1).look().bold());
        assertEquals(TextColor.color(0x404040), s.get(2).look().color());
        assertTrue(s.get(3).look().underline());
        assertEquals(TextColor.color(0xFFAA00), s.get(3).look().color());
        assertEquals("Price: 100 coins", RichText.plainText(s));
    }

    @Test
    void boldIsOnePixelWiderPerCharacter() {
        int plain = RichText.width(TEXT, RichText.plain("Hello", BASE));
        int bold = RichText.width(TEXT, RichText.parse("<b>Hello", BASE));
        assertEquals(plain + 5, bold);
    }

    @Test
    void fitAndWrapKeepTheLooks() {
        List<RichText.Span> s = RichText.parse("<b>Bold words</b> and plain words that go on", BASE);
        List<RichText.Span> cut = RichText.fit(TEXT, s, 40);
        assertTrue(RichText.visibleWidth(TEXT, cut) <= 40);
        assertTrue(RichText.plainText(cut).endsWith("..."));
        List<List<RichText.Span>> lines = RichText.wrap(TEXT, s, 60);
        assertTrue(lines.size() > 1);
        for (List<RichText.Span> line : lines) {
            assertTrue(RichText.visibleWidth(TEXT, line) <= 60, RichText.plainText(line));
        }
        assertTrue(lines.getFirst().getFirst().look().bold());
        assertEquals("Bold words and plain words that go on",
            String.join(" ", lines.stream().map(RichText::plainText).toList()));
    }
}
