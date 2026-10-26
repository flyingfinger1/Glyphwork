package de.flyingfinger.glyphwork.menu;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A check like {@code %vault_eco_balance% >= 100} for show-if, enabled-if and require.
 *
 * <p>Both sides may contain placeholders; they are filled in for the viewer each time. If both
 * sides are numbers they are compared as numbers, otherwise as text (ignoring case). Operators:
 * {@code == != > >= < <= contains !contains}, with spaces around them. A list of conditions holds
 * when all of them hold.
 */
final class Condition {

    private static final Pattern OP = Pattern.compile("\\s(==|!=|>=|<=|>|<|!contains|contains)\\s");

    private final String left;
    private final String op;
    private final String right;
    private final String source;

    private Condition(String left, String op, String right, String source) {
        this.left = left;
        this.op = op;
        this.right = right;
        this.source = source;
    }

    /** One condition; {@code key} names the setting in error messages. */
    static Condition parse(String text, Node at, String key) throws MenuException {
        String s = text.trim();
        Matcher m = OP.matcher(s);
        if (!m.find()) {
            throw at.error(key, "condition like '%vault_eco_balance% >= 100' expected (operators "
                + "== != > >= < <= contains !contains, with spaces around them), got '" + s + "'");
        }
        String left = unquote(s.substring(0, m.start()).trim());
        String right = unquote(s.substring(m.end()).trim());
        if (left.isEmpty() || right.isEmpty()) {
            throw at.error(key, "condition needs a value on both sides of '" + m.group(1) + "', got '" + s + "'");
        }
        return new Condition(left, m.group(1), right, s);
    }

    /** All conditions of a setting that may be one string or a list; empty if the setting is missing. */
    static List<Condition> all(Node n, String key) throws MenuException {
        List<String> raw = n.strings(key);
        List<Condition> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            out.add(parse(raw.get(i), n, raw.size() == 1 ? key : key + "[" + i + "]"));
        }
        return out;
    }

    static boolean all(List<Condition> conditions, @Nullable Player player) {
        for (Condition c : conditions) {
            if (!c.test(player, null)) {
                return false;
            }
        }
        return true;
    }

    /** @param input text of an enclosing input action (for %input%), or null */
    boolean test(@Nullable Player player, @Nullable String input) {
        String l = fill(left, player, input);
        String r = fill(right, player, input);
        Double a = number(l);
        Double b = number(r);
        boolean numbers = a != null && b != null;
        return switch (op) {
            case "==" -> numbers ? a.doubleValue() == b.doubleValue() : l.equalsIgnoreCase(r);
            case "!=" -> numbers ? a.doubleValue() != b.doubleValue() : !l.equalsIgnoreCase(r);
            case ">" -> numbers && a > b;
            case ">=" -> numbers && a >= b;
            case "<" -> numbers && a < b;
            case "<=" -> numbers && a <= b;
            case "contains" -> l.toLowerCase(Locale.ROOT).contains(r.toLowerCase(Locale.ROOT));
            case "!contains" -> !l.toLowerCase(Locale.ROOT).contains(r.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    private static String fill(String s, @Nullable Player player, @Nullable String input) {
        String t = input == null ? s : s.replace("%input%", input);
        return Placeholders.apply(player, t).trim();
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** "1234.5", "1,234.5" (PlaceholderAPI's formatted numbers) or null. */
    static @Nullable Double number(String s) {
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            try {
                return s.matches("-?[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?") ? Double.parseDouble(s.replace(",", "")) : null;
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    @Override
    public String toString() {
        return source;
    }
}
