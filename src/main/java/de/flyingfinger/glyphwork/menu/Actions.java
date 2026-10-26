package de.flyingfinger.glyphwork.menu;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Click actions of menu files, one string each, run in order:
 * <pre>
 * player: spawn                 the player runs /spawn
 * console: give %player% bread  the console runs it
 * message: &lt;green&gt;Done!       chat message (MiniMessage)
 * sound: entity.experience_orb.pickup [volume] [pitch]
 * open: shop                    open menu "shop" ("back" returns)
 * back | close | refresh
 * input: Name? | player: msg %input% hi   ask for text, then run the action with %input%
 * require: %vault_eco_balance% >= 100 | message: Too poor   stop here unless the condition holds
 * </pre>
 */
final class Actions {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * One action; {@code input} is the text of an enclosing "input" action, or null.
     * Returns false to stop the remaining actions (a failed "require").
     */
    @FunctionalInterface
    private interface Step {
        boolean run(Click c, @Nullable String input);
    }

    /** An action that never stops the chain. */
    @FunctionalInterface
    private interface Simple {
        void run(Click c, @Nullable String input);
    }

    private static Step go(Simple s) {
        return (c, in) -> {
            s.run(c, in);
            return true;
        };
    }

    private Actions() {
    }

    /** @param menus looks up a menu by id when an "open" action runs (menus may be reloaded) */
    static Consumer<Click> parse(List<String> actions, Node at, String key, Function<String, Menus.@Nullable Menu> menus)
        throws MenuException {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            steps.add(one(actions.get(i).trim(), at, key + "[" + i + "]", menus));
        }
        return click -> {
            for (Step step : steps) {
                if (!step.run(click, null)) {
                    return;
                }
            }
        };
    }

    private static String text(Click c, String template, @Nullable String input) {
        String s = input == null ? template : template.replace("%input%", input);
        return Placeholders.apply(c.player(), s);
    }

    private static Step one(String action, Node at, String key, Function<String, Menus.@Nullable Menu> menus)
        throws MenuException {
        int colon = action.indexOf(':');
        String type = (colon < 0 ? action : action.substring(0, colon)).trim().toLowerCase();
        String arg = colon < 0 ? "" : action.substring(colon + 1).trim();
        return switch (type) {
            case "player" -> {
                String cmd = stripSlash(require(arg, at, key, "a command"));
                yield go((c, in) -> c.player().performCommand(text(c, cmd, in)));
            }
            case "console" -> {
                String cmd = stripSlash(require(arg, at, key, "a command"));
                yield go((c, in) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), text(c, cmd, in)));
            }
            case "message" -> {
                String msg = require(arg, at, key, "a text");
                yield go((c, in) -> c.player().sendMessage(MM.deserialize(text(c, msg, in))));
            }
            case "sound" -> {
                String[] p = require(arg, at, key, "a sound name").split("\\s+");
                float volume = p.length > 1 ? number(p[1], at, key) : 1f;
                float pitch = p.length > 2 ? number(p[2], at, key) : 1f;
                yield go((c, in) -> c.player().playSound(c.player(), p[0], volume, pitch));
            }
            case "open" -> {
                String id = require(arg, at, key, "a menu id");
                yield go((c, in) -> {
                    Menus.Menu next = menus.apply(id);
                    if (next == null) {
                        c.player().sendMessage(Messages.active().get(c.player(), "menu.unknown", "id", id));
                    } else if (!next.allowed(c.player())) {
                        c.player().sendMessage(Messages.active().get(c.player(), "no-permission"));
                    } else {
                        c.open(next.scene());
                    }
                });
            }
            case "back" -> go((c, in) -> c.back());
            case "close" -> go((c, in) -> c.close());
            case "refresh" -> go((c, in) -> c.reopen());
            case "input" -> {
                int bar = arg.indexOf('|');
                if (bar < 0) {
                    throw at.error(key, "input needs 'Title | action', e.g. 'input: Name? | player: msg %input% hi'");
                }
                String title = arg.substring(0, bar).trim();
                Step then = one(arg.substring(bar + 1).trim(), at, key, menus);
                yield go((c, in) -> c.prompt(MM.deserialize(text(c, title, in)), "", typed -> then.run(c, clean(typed))));
            }
            case "require" -> {
                int bar = arg.indexOf(" | ");
                Condition cond = Condition.parse(bar < 0 ? require(arg, at, key, "a condition") : arg.substring(0, bar), at, key);
                Step otherwise = bar < 0 ? null : one(arg.substring(bar + 3).trim(), at, key, menus);
                yield (c, in) -> {
                    if (cond.test(c.player(), in)) {
                        return true;
                    }
                    if (otherwise != null) {
                        otherwise.run(c, in);
                    }
                    return false;
                };
            }
            default -> throw at.error(key, "unknown action '" + type
                + "' (player, console, message, sound, open, back, close, refresh, input, require)");
        };
    }

    private static String require(String arg, Node at, String key, String what) throws MenuException {
        if (arg.isEmpty()) {
            throw at.error(key, "needs " + what);
        }
        return arg;
    }

    private static float number(String s, Node at, String key) throws MenuException {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            throw at.error(key, "number expected, got '" + s + "'");
        }
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    /** Input goes into commands: one line, no leading slash, no placeholders, limited length. */
    static String clean(String text) {
        String s = text.replaceAll("[\\r\\n]", " ").replace("%", "").trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
