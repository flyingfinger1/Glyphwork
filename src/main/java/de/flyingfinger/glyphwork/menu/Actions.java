package de.flyingfinger.glyphwork.menu;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.core.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * set: tab shop                 menu variable %menu_tab% = shop ("set: tab" = back to the default)
 * toggle: sound                 %menu_sound% switches between true and false
 * add: points 5 [min 0] [max 100]  adds a number (negative subtracts) to a variable, within limits
 * form: settings                open the form "settings" of this menu (fields = menu variables)
 * give-item: hub                hand out the menu item of menu "hub" (open-on.item)
 * </pre>
 * After set/toggle the menu is drawn again, unless an action already opened, closed or redrew it.
 */
final class Actions {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** A parsed action list; {@code item} is the entry of a dynamic list (or null). */
    @FunctionalInterface
    interface Chain {
        /** @return false if a "require" stopped the actions */
        boolean run(Click c, @Nullable Map<String, String> item);
    }

    /** What happened during one click. */
    private static final class Run {
        final @Nullable Map<String, String> item;
        boolean changed;
        boolean navigated;

        Run(@Nullable Map<String, String> item) {
            this.item = item;
        }
    }

    /**
     * One action; {@code input} is the text of an enclosing "input" action, or null.
     * Returns false to stop the remaining actions (a failed "require").
     */
    @FunctionalInterface
    private interface Step {
        boolean run(Click c, @Nullable String input, Run r);
    }

    /** An action that never stops the chain. */
    @FunctionalInterface
    private interface Simple {
        void run(Click c, @Nullable String input, Run r);
    }

    private static Step go(Simple s) {
        return (c, in, r) -> {
            s.run(c, in, r);
            return true;
        };
    }

    private Actions() {
    }

    /** @param menus looks up a menu by id when an "open" action runs (menus may be reloaded) */
    static Chain parse(List<String> actions, Node at, String key, Function<String, Menus.@Nullable Menu> menus, Vars vars,
                       Forms forms) throws MenuException {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            steps.add(one(actions.get(i).trim(), at, key + "[" + i + "]", menus, vars, forms));
        }
        return (click, item) -> {
            Run r = new Run(item);
            boolean all = true;
            for (Step step : steps) {
                if (!step.run(click, null, r)) {
                    all = false;
                    break;
                }
            }
            if (r.changed && !r.navigated) {
                click.reopen();
            }
            return all;
        };
    }

    private static Step one(String action, Node at, String key, Function<String, Menus.@Nullable Menu> menus, Vars vars,
                            Forms forms) throws MenuException {
        int colon = action.indexOf(':');
        String type = (colon < 0 ? action : action.substring(0, colon)).trim().toLowerCase();
        String arg = colon < 0 ? "" : action.substring(colon + 1).trim();
        return switch (type) {
            case "player" -> {
                String cmd = stripSlash(require(arg, at, key, "a command"));
                yield go((c, in, r) -> c.player().performCommand(vars.fill(c, cmd, r.item, in)));
            }
            case "console" -> {
                String cmd = stripSlash(require(arg, at, key, "a command"));
                yield go((c, in, r) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), vars.fill(c, cmd, r.item, in)));
            }
            case "message" -> {
                String msg = require(arg, at, key, "a text");
                yield go((c, in, r) -> c.player().sendMessage(MM.deserialize(vars.fill(c, msg, r.item, in))));
            }
            case "sound" -> {
                String[] p = require(arg, at, key, "a sound name").split("\\s+");
                float volume = p.length > 1 ? number(p[1], at, key) : 1f;
                float pitch = p.length > 2 ? number(p[2], at, key) : 1f;
                yield go((c, in, r) -> c.player().playSound(c.player(), p[0], volume, pitch));
            }
            case "open" -> {
                String id = require(arg, at, key, "a menu id");
                yield go((c, in, r) -> {
                    r.navigated = true;
                    String target = vars.fill(c, id, r.item, in).trim().toLowerCase();
                    Menus.Menu next = menus.apply(target);
                    if (next == null) {
                        c.player().sendMessage(Messages.active().get(c.player(), "menu.unknown", "id", target));
                    } else if (!next.allowed(c.player())) {
                        c.player().sendMessage(Messages.active().get(c.player(), "no-permission"));
                    } else {
                        c.open(next.scene());
                    }
                });
            }
            case "url" -> {
                String url = require(arg, at, key, "a web address (http:// or https://)");
                if (!url.contains("%") && Feeds.problem(url) != null) {
                    throw at.error(key, "url: " + Feeds.problem(url));
                }
                yield go((c, in, r) -> {
                    String target = vars.fill(c, url, r.item, in).trim();
                    if (Feeds.problem(target) != null) {
                        c.player().sendMessage(Messages.active().get(c.player(), "url.invalid", "url", target));
                        return;
                    }
                    r.navigated = true;
                    c.openUrl(target);
                });
            }
            case "back" -> go((c, in, r) -> { r.navigated = true; c.back(); });
            case "close" -> go((c, in, r) -> { r.navigated = true; c.close(); });
            case "refresh" -> go((c, in, r) -> { r.navigated = true; c.reopen(); });
            case "input" -> {
                int bar = arg.indexOf('|');
                if (bar < 0) {
                    throw at.error(key, "input needs 'Title | action', e.g. 'input: Name? | player: msg %input% hi'");
                }
                String title = arg.substring(0, bar).trim();
                Step then = one(arg.substring(bar + 1).trim(), at, key, menus, vars, forms);
                // the prompt shows the menu again afterwards, so no extra redraw is needed
                yield go((c, in, r) -> {
                    r.navigated = true;
                    c.prompt(MM.deserialize(vars.fill(c, title, r.item, in)), "",
                        typed -> then.run(c, clean(typed), new Run(r.item)));
                });
            }
            case "require" -> {
                int bar = arg.indexOf(" | ");
                Condition cond = Condition.parse(bar < 0 ? require(arg, at, key, "a condition") : arg.substring(0, bar), at, key);
                Step otherwise = bar < 0 ? null : one(arg.substring(bar + 3).trim(), at, key, menus, vars, forms);
                yield (c, in, r) -> {
                    if (cond.test(s -> vars.fill(c, s, r.item, in))) {
                        return true;
                    }
                    if (otherwise != null) {
                        otherwise.run(c, in, r);
                    }
                    return false;
                };
            }
            case "set" -> {
                String[] p = require(arg, at, key, "a variable name, e.g. 'set: tab shop'").split("\\s+", 2);
                String name = variable(p[0], at, key);
                String value = p.length > 1 ? p[1] : null;
                yield go((c, in, r) -> {
                    vars.set(c, name, value == null ? null : vars.fill(c, value, r.item, in));
                    r.changed = true;
                });
            }
            case "add" -> {
                // add: name amount [min X] [max Y]; amount and limits may be placeholders
                String[] p = require(arg, at, key, "a variable and a number, e.g. 'add: points 1'").trim().split("\\s+");
                if (p.length != 2 && p.length != 4 && p.length != 6) {
                    throw at.error(key, "add needs 'name amount', optionally with 'min X' and/or 'max Y'; got '" + arg + "'");
                }
                String name = variable(p[0], at, key);
                String amount = p[1];
                String min = null;
                String max = null;
                for (int i = 2; i + 1 < p.length; i += 2) {
                    switch (p[i].toLowerCase()) {
                        case "min" -> min = p[i + 1];
                        case "max" -> max = p[i + 1];
                        default -> throw at.error(key, "'min' or 'max' expected, got '" + p[i] + "'");
                    }
                }
                checkNumber(amount, at, key);
                if (min != null) {
                    checkNumber(min, at, key);
                }
                if (max != null) {
                    checkNumber(max, at, key);
                }
                String lo = min;
                String hi = max;
                yield go((c, in, r) -> {
                    Double now = Condition.number(vars.get(c, name).trim());
                    Double add = Condition.number(vars.fill(c, amount, r.item, in).trim());
                    if (add == null) {
                        return;     // the placeholder was no number: change nothing
                    }
                    double v = (now == null ? 0 : now) + add;
                    Double l = lo == null ? null : Condition.number(vars.fill(c, lo, r.item, in).trim());
                    Double h = hi == null ? null : Condition.number(vars.fill(c, hi, r.item, in).trim());
                    if (l != null) {
                        v = Math.max(l, v);
                    }
                    if (h != null) {
                        v = Math.min(h, v);
                    }
                    vars.set(c, name, formatNumber(v));
                    r.changed = true;
                });
            }
            case "toggle" -> {
                String name = variable(require(arg, at, key, "a variable name"), at, key);
                yield go((c, in, r) -> {
                    vars.set(c, name, "true".equalsIgnoreCase(vars.get(c, name)) ? "false" : "true");
                    r.changed = true;
                });
            }
            case "give-item" -> {
                String id = require(arg, at, key, "a menu id").toLowerCase();
                yield go((c, in, r) -> {
                    MenuTriggers t = MenuTriggers.active();
                    if (t != null && c.player() != null) {
                        t.give(c.player(), vars.fill(c, id, r.item, in).trim().toLowerCase(), true);
                    }
                });
            }
            case "form" -> {
                String name = require(arg, at, key, "a form name").toLowerCase();
                Forms.Def def = forms.get(name);
                if (def == null) {
                    throw at.error(key, forms == Forms.NONE && !name.isEmpty()
                        ? "no form '" + name + "' (forms are listed under 'forms:' of the menu; a form cannot open another form)"
                        : "no form '" + name + "' (there are: " + String.join(", ", forms.names()) + ")");
                }
                // the form shows the menu again afterwards, so no extra redraw is needed
                yield go((c, in, r) -> {
                    r.navigated = true;
                    Map<String, String> item = r.item;
                    c.form(Forms.build(def, c, vars, item), values -> {
                        Forms.store(def, c, values, vars);
                        def.actions().run(c, item);
                    });
                });
            }
            default -> throw at.error(key, "unknown action '" + type
                + "' (player, console, message, sound, open, back, close, refresh, input, require, set, add, toggle, form, give-item)");
        };
    }

    /** A number or a placeholder that becomes one when the action runs. */
    private static void checkNumber(String s, Node at, String key) throws MenuException {
        if (s.indexOf('%') < 0 && Condition.number(s) == null) {
            throw at.error(key, "number expected, got '" + s + "'");
        }
    }

    /** 3 -> "3", 2.5 -> "2.5", 0.1 + 0.2 -> "0.3" (rounded to 6 places). */
    static String formatNumber(double v) {
        double r = Math.round(v * 1_000_000d) / 1_000_000d;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) {
            return String.valueOf((long) r);
        }
        return java.math.BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
    }

    private static String variable(String name, Node at, String key) throws MenuException {
        String n = name.trim().toLowerCase();
        if (!Vars.NAME.matcher(n).matches()) {
            throw at.error(key, "variable name expected (a-z, 0-9, '_', '-'), got '" + name + "'");
        }
        return n;
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
