package de.flyingfinger.glyphwork.font;

import net.kyori.adventure.key.Key;

/**
 * A font text is drawn in: the vanilla glyphs ({@link VanillaText}) or an own font from config.yml
 * ({@link CustomFont}). Both are placed the same way: {@code y} is the top of the capital letters,
 * and the font for {@code shift} draws the glyphs that many pixels lower (0..8, see {@link ShiftFonts}).
 */
public interface TextFont {

    /** Width in pixels as the client computes it (no bold). */
    int width(String text);

    /** The pack font for text {@code shift} (0..8) pixels below its text line. */
    Key key(int shift);

    /** Distance between the lines of wrapped text when a label sets none. */
    int lineHeight();

    /** Height of the glyphs from the top of the capitals, for clipping in scroll panes. */
    int height();
}
