package de.flyingfinger.glyphwork.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Durations as written in menus and config.yml: "30s", "5m", "2h", "1d 12h"; a plain number is seconds. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(d|h|min|m|s)?");

    private Durations() {
    }

    /**
     * Milliseconds of a duration.
     *
     * @throws IllegalArgumentException if it is no duration or shorter than a second
     */
    public static long parse(String text) {
        String s = text.trim().toLowerCase();
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (!s.substring(end, m.start()).isBlank()) {
                break;
            }
            long v = Long.parseLong(m.group(1));
            String unit = m.group(2) == null ? "s" : m.group(2);
            total += v * switch (unit) {
                case "d" -> 86_400_000L;
                case "h" -> 3_600_000L;
                case "m", "min" -> 60_000L;
                default -> 1000L;
            };
            end = m.end();
        }
        if (end == 0 || !s.substring(end).isBlank() || total < 1000) {
            throw new IllegalArgumentException("duration expected, e.g. 30s, 5m, 2h, 1d or 1d 12h; got '" + text + "'");
        }
        return total;
    }
}
