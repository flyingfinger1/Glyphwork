package de.flyingfinger.glyphwork.font;

/**
 * One drawable character.
 *
 * @param codepoint private-use character registered in every shifted font
 * @param advance   cursor advance after drawing (visible width + 1, same rule as the client)
 * @param width     cell width in pixels
 * @param height    cell height in pixels (the drawn box is {@code width x height})
 */
public record Glyph(char codepoint, int advance, int width, int height) {
}
