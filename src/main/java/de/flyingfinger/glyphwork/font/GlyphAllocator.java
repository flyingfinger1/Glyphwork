package de.flyingfinger.glyphwork.font;

/** Hands out private-use codepoints for sheet glyphs (U+E100..U+F8FF; U+E000..U+E0FF are spaces). */
public final class GlyphAllocator {

    private static final int FIRST = 0xE100;
    private static final int LAST = 0xF8FF;
    private int next = FIRST;

    public char next() {
        if (next > LAST) {
            throw new IllegalStateException("Out of private-use codepoints");
        }
        return (char) next++;
    }
}
