package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.font.VanillaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Text with formatting for the pixel GUI: pieces with color, bold, italic, underline and
 * strikethrough. Bold makes every character 1 px wider (the client draws it twice), so measuring,
 * cutting and wrapping work on the pieces.
 */
public final class RichText {

    /** How a piece looks. A null color means "the element's color". */
    public record Look(@Nullable TextColor color, boolean bold, boolean italic, boolean underline, boolean strike) {

        public static final Look PLAIN = new Look(null, false, false, false, false);

        Look over(Look base) {
            return new Look(color != null ? color : base.color, bold || base.bold, italic || base.italic,
                underline || base.underline, strike || base.strike);
        }
    }

    /** One piece of text with one look. */
    public record Span(String text, Look look) {
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private RichText() {
    }

    /** Plain text, every character with the same look. */
    public static List<Span> plain(String s, Look look) {
        return s.isEmpty() ? List.of() : List.of(new Span(s, look));
    }

    /**
     * MiniMessage ({@code <b>}, {@code <i>}, {@code <u>}, {@code <st>}, colors) in pieces; the
     * element's look is the base. Text without '<' stays one piece.
     */
    public static List<Span> parse(String s, Look base) {
        if (s.indexOf('<') < 0) {
            return plain(s, base);
        }
        List<Span> out = new ArrayList<>();
        flatten(MM.deserialize(s), Style.empty(), base, out);
        return merge(out);
    }

    private static void flatten(Component c, Style inherited, Look base, List<Span> out) {
        // the component's own style, missing parts from its parents
        Style own = c.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        String text = c instanceof TextComponent t ? t.content()
            : c.children().isEmpty() ? PlainTextComponentSerializer.plainText().serialize(c) : "";
        if (!text.isEmpty()) {
            Look look = new Look(own.color(), on(own, TextDecoration.BOLD), on(own, TextDecoration.ITALIC),
                on(own, TextDecoration.UNDERLINED), on(own, TextDecoration.STRIKETHROUGH)).over(base);
            out.add(new Span(text, look));
        }
        for (Component child : c.children()) {
            flatten(child, own, base, out);
        }
    }

    private static boolean on(Style s, TextDecoration d) {
        return s.decoration(d) == TextDecoration.State.TRUE;
    }

    /** Neighbors with the same look become one piece. */
    static List<Span> merge(List<Span> spans) {
        List<Span> out = new ArrayList<>();
        for (Span s : spans) {
            if (s.text().isEmpty()) {
                continue;
            }
            if (!out.isEmpty() && out.getLast().look().equals(s.look())) {
                out.set(out.size() - 1, new Span(out.getLast().text() + s.text(), s.look()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** Width in pixels as the client advances (bold: +1 per character). */
    public static int width(VanillaText text, List<Span> spans) {
        int w = 0;
        for (Span s : spans) {
            w += width(text, s);
        }
        return w;
    }

    static int width(VanillaText text, Span s) {
        return text.width(s.text()) + (s.look().bold() ? s.text().codePointCount(0, s.text().length()) : 0);
    }

    /** Visible width: the last advance pixel is empty. */
    public static int visibleWidth(VanillaText text, List<Span> spans) {
        return Math.max(0, width(text, spans) - 1);
    }

    /** All text without formatting. */
    public static String plainText(List<Span> spans) {
        StringBuilder b = new StringBuilder();
        for (Span s : spans) {
            b.append(s.text());
        }
        return b.toString();
    }

    /** Cut to {@code maxWidth} visible pixels, with "..." in the look of the last piece. */
    public static List<Span> fit(VanillaText text, List<Span> spans, int maxWidth) {
        if (visibleWidth(text, spans) <= maxWidth) {
            return spans;
        }
        List<Span> out = new ArrayList<>();
        int used = 0;
        Look last = spans.isEmpty() ? Look.PLAIN : spans.getFirst().look();
        outer:
        for (Span s : spans) {
            last = s.look();
            int dots = width(text, new Span("...", s.look()));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.text().length(); ) {
                int cp = s.text().codePointAt(i);
                int w = width(text, new Span(Character.toString(cp), s.look()));
                if (used + w + dots - 1 > maxWidth) {
                    out.add(new Span(b.toString(), s.look()));
                    break outer;
                }
                b.appendCodePoint(cp);
                used += w;
                i += Character.charCount(cp);
            }
            out.add(new Span(b.toString(), s.look()));
        }
        // the cut piece ends with "..."; trailing spaces before it go
        List<Span> cut = merge(out);
        if (!cut.isEmpty()) {
            Span end = cut.getLast();
            cut.set(cut.size() - 1, new Span(end.text().stripTrailing() + "...", end.look()));
        } else {
            cut.add(new Span("...", last));
        }
        return cut;
    }

    /**
     * Lines of at most {@code width} px: breaks at spaces, keeps '\n' breaks; a word longer than a
     * line is cut with "...".
     */
    public static List<List<Span>> wrap(VanillaText text, List<Span> spans, int width) {
        List<List<Span>> out = new ArrayList<>();
        for (List<Span> para : splitLines(spans)) {
            List<List<Span>> words = splitWords(para);
            List<Span> current = new ArrayList<>();
            for (List<Span> word : words) {
                List<Span> trial = new ArrayList<>(current);
                if (!current.isEmpty()) {
                    trial.add(new Span(" ", lookAtEnd(current)));
                }
                trial.addAll(word);
                if (current.isEmpty() || visibleWidth(text, trial) <= width) {
                    current = merge(trial);
                } else {
                    out.add(current);
                    current = new ArrayList<>(word);
                }
            }
            out.add(fit(text, merge(current), width));
        }
        return out;
    }

    private static Look lookAtEnd(List<Span> spans) {
        return spans.getLast().look();
    }

    /** Pieces per text line ('\n'). */
    static List<List<Span>> splitLines(List<Span> spans) {
        List<List<Span>> lines = new ArrayList<>();
        List<Span> current = new ArrayList<>();
        for (Span s : spans) {
            String[] parts = s.text().split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(current);
                    current = new ArrayList<>();
                }
                if (!parts[i].isEmpty()) {
                    current.add(new Span(parts[i], s.look()));
                }
            }
        }
        lines.add(current);
        return lines;
    }

    /** Words (split at spaces; a word may have several looks: "<b>Pre</b>fix"). */
    private static List<List<Span>> splitWords(List<Span> line) {
        List<List<Span>> words = new ArrayList<>();
        List<Span> current = new ArrayList<>();
        for (Span s : line) {
            String[] parts = s.text().split(" ", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0 && !current.isEmpty()) {
                    words.add(current);
                    current = new ArrayList<>();
                }
                if (!parts[i].isEmpty()) {
                    current.add(new Span(parts[i], s.look()));
                }
            }
        }
        if (!current.isEmpty()) {
            words.add(current);
        }
        return words;
    }
}
