package de.flyingfinger.glyphwork;

import de.flyingfinger.glyphwork.render.FallbackRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bedrock forms have no tooltips: the first tooltip line goes into the button. */
class BedrockButtonTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void tooltipBecomesSecondLine() {
        assertEquals("Diamond\n64 for 100 coins",
            plain(FallbackRenderer.withTooltipLine(Component.text("Diamond"), Component.text("64 for 100 coins\nmore lines"))));
        assertEquals("Shop", plain(FallbackRenderer.withTooltipLine(Component.text("Shop"), Component.text("shop"))));
        assertEquals("Shop", plain(FallbackRenderer.withTooltipLine(Component.text("Shop"), null)));
        String longTip = "x".repeat(80);
        assertEquals(48, plain(FallbackRenderer.withTooltipLine(Component.text("A"), Component.text(longTip))).split("\n")[1].length());
    }
}
