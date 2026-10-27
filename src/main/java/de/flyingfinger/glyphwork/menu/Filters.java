package de.flyingfinger.glyphwork.menu;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters after a placeholder name: {@code %item_timestamp|date%}, {@code %item_content|discord%}.
 * Unknown filters leave the value as it is.
 */
final class Filters {

    private static volatile DateTimeFormatter dates = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Filters() {
    }

    static void dateFormat(DateTimeFormatter f) {
        dates = f;
    }

    static String apply(String filter, String value) {
        return switch (filter) {
            case "date" -> date(value, dates);
            case "discord" -> discord(value, dates);
            default -> value;
        };
    }

    /** ISO 8601, RFC 1123 or a Unix time (seconds or milliseconds), in the server's time zone. */
    static String date(String s, DateTimeFormatter out) {
        String t = s.trim();
        if (t.matches("\\d{9,13}")) {
            long n = Long.parseLong(t);
            Instant i = t.length() >= 12 ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
            return i.atZone(ZoneId.systemDefault()).format(out);
        }
        return Feeds.date(t, out);
    }

    private static final Pattern TIME = Pattern.compile("<t:(\\d{1,12})(?::[tTdDfFR])?>");
    private static final Pattern EMOJI = Pattern.compile("<a?:([A-Za-z0-9_~]+):\\d+>");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]+)]\\((?:<)?https?://[^)\\s]+(?:>)?\\)");

    /**
     * Discord message markup made readable: mentions, custom emojis and timestamps as text, markdown
     * markers removed (bold, italics, strikethrough, spoilers, code, headings, quotes, masked links).
     */
    static String discord(String s, DateTimeFormatter out) {
        String t = s;
        Matcher m = TIME.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(date(m.group(1), out)));
        }
        m.appendTail(sb);
        t = sb.toString();
        t = t.replaceAll("<@!?\\d+>", "@user")
            .replaceAll("<@&\\d+>", "@role")
            .replaceAll("<#\\d+>", "#channel")
            .replaceAll("</([\\w -]+):\\d+>", "/$1");
        t = EMOJI.matcher(t).replaceAll(":$1:");
        t = LINK.matcher(t).replaceAll("$1");
        t = t.replaceAll("(?s)```[a-zA-Z0-9]*\\n?(.*?)```", "$1")
            .replaceAll("`([^`\\n]+)`", "$1")
            .replaceAll("\\|\\|(.+?)\\|\\|", "$1")
            .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
            .replaceAll("__(.+?)__", "$1")
            .replaceAll("~~(.+?)~~", "$1")
            .replaceAll("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])", "$1")
            .replaceAll("(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])", "$1")
            .replaceAll("(?m)^#{1,3} +", "")
            .replaceAll("(?m)^-# +", "")
            .replaceAll("(?m)^>>> ?", "")
            .replaceAll("(?m)^> ?", "")
            .replaceAll("\\\\([*_~`|>#\\\\])", "$1");
        return t.trim();
    }
}
