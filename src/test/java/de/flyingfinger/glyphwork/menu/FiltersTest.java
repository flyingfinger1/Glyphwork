package de.flyingfinger.glyphwork.menu;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Placeholder filters: |date and |discord. */
class FiltersTest {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static String local(long epochSecond) {
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault()).format(DAY);
    }

    @Test
    void dates() {
        assertEquals(local(1793110000L), Filters.date("1793110000", DAY));          // Unix seconds
        assertEquals(local(1793110000L), Filters.date("1793110000000", DAY));       // milliseconds
        assertEquals("2026-10-27", Filters.date("2026-10-27T10:03:11.123000+00:00", DAY));
        assertEquals("not a date", Filters.date("not a date", DAY));
    }

    @Test
    void discordMarkup() {
        String in = "**Update** for <@123456789012> in <#987654321098>! <:pog:1234567890> "
            + "~~old~~ ||secret|| `code` *nice* __under__ [the docs](https://example.org/docs) <@&55555>";
        assertEquals("Update for @user in #channel! :pog: old secret code nice under the docs @role",
            Filters.discord(in, DAY, false));
        assertEquals("Title\nquoted\nsmall", Filters.discord("# Title\n> quoted\n-# small", DAY, false));
        assertEquals("Starts " + local(1793110000L), Filters.discord("Starts <t:1793110000:R>", DAY, false));
        assertEquals("snake_case_name and 2*3*4", Filters.discord("snake_case_name and 2*3*4", DAY, false));
    }

    @Test
    void inPlaceholders() {
        Vars vars = Vars.NONE;
        Filters.dateFormat(DAY);
        try {
            assertEquals("2026-10-27 / Hi there", vars.fill(null, "%item_t|date% / %item_c|discord-plain%",
                Map.of("t", "2026-10-27T10:00:00Z", "c", "**Hi** there"), null));
            assertEquals("x", vars.fill(null, "%item|nope%", Map.of("", "x"), null));     // unknown filter: as it is
        } finally {
            Filters.dateFormat(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        }
    }

    @Test
    void discordAsMiniMessage() {
        assertEquals("<b>Update</b> is <u>here</u>, <i>really</i> <st>old</st>",
            Filters.discord("**Update** is __here__, *really* ~~old~~", DAY, true));
        assertEquals("<b>News</b>\nline", Filters.discord("# News\nline", DAY, true));
        // the message's own tags stay text, Discord escapes stay characters
        assertEquals("\\<red>hi and *stars*", Filters.discord("<red>hi and \\*stars\\*", DAY, true));
    }
}
