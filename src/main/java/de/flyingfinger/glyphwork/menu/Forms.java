package de.flyingfinger.glyphwork.menu;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Form;
import de.flyingfinger.glyphwork.dialog.FormValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The {@code forms:} of a menu file: native dialogs with several fields, opened by the action
 * {@code form: name}. Every field belongs to a menu variable: it starts with the variable's value,
 * and OK writes the entered value back (then the form's own actions run and the menu is redrawn).
 * <pre>
 * forms:
 *   settings:
 *     title: "Settings"
 *     fields:
 *       - { type: text, var: name, label: "Name", max-length: 16 }
 *       - { type: slider, var: range, label: "Range", min: 1, max: 32 }
 *       - { type: checkbox, var: particles, label: "Particles" }
 *       - { type: choice, var: mode, label: "Mode", options: [easy, normal, hard] }
 *     actions: ["message: Saved"]
 * </pre>
 */
final class Forms {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> FORM_KEYS = Set.of("title", "width", "ok", "cancel", "fields", "actions");

    /** No forms (menus without a "forms:" section). */
    static final Forms NONE = new Forms(Map.of());

    private record FieldDef(String type, String var, String label, int maxLength, int lines,
                            float min, float max, float step, Map<String, String> options) {
    }

    record Def(String name, String title, int width, @Nullable String ok, @Nullable String cancel,
               List<FieldDef> fields, Actions.Chain actions) {
    }

    private final Map<String, Def> defs;

    private Forms(Map<String, Def> defs) {
        this.defs = defs;
    }

    @Nullable Def get(String name) {
        return defs.get(name);
    }

    Set<String> names() {
        return defs.keySet();
    }

    /** Parses the "forms:" section; actions of forms may not open forms themselves. */
    static Forms of(Node root, Vars vars, Function<String, Menus.@Nullable Menu> menus) throws MenuException {
        Object raw = Templates.plain(root.raw().get("forms"));
        if (raw == null) {
            return NONE;
        }
        if (!(raw instanceof Map<?, ?> m)) {
            throw root.error("forms", "map of forms expected (name: { title, fields, actions })");
        }
        Map<String, Def> defs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String name = String.valueOf(e.getKey()).toLowerCase();
            if (!(e.getValue() instanceof Map<?, ?> body)) {
                throw root.error("forms." + name, "settings expected (title, fields, actions)");
            }
            Node n = new Node(body, root.path + ".forms." + name);
            n.only(FORM_KEYS);
            List<FieldDef> fields = new ArrayList<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (Node f : n.nodes("fields")) {
                FieldDef fd = field(f);
                if (!used.add(fd.var())) {
                    throw f.error("var", "variable '" + fd.var() + "' has two fields in this form");
                }
                fields.add(fd);
            }
            if (fields.isEmpty()) {
                throw n.error("fields", "a form needs at least one field");
            }
            Actions.Chain actions = Actions.parse(n.strings("actions"), n, "actions", menus, vars, NONE);
            defs.put(name, new Def(name, n.optString("title", name), n.optInt("width", 200),
                n.optString("ok", null), n.optString("cancel", null), fields, actions));
        }
        return new Forms(defs);
    }

    private static FieldDef field(Node f) throws MenuException {
        String type = f.string("type").toLowerCase();
        String var = f.string("var").trim().toLowerCase();
        if (!Vars.NAME.matcher(var).matches()) {
            throw f.error("var", "variable name expected (a-z, 0-9, '_', '-'), got '" + var + "'");
        }
        String label = f.optString("label", var);
        return switch (type) {
            case "text" -> {
                f.only(Set.of("type", "var", "label", "max-length", "lines"));
                int max = f.optInt("max-length", 100);
                int lines = f.optInt("lines", 1);
                if (max < 1 || max > 100) {
                    throw f.error("max-length", "1 to 100 (values end up in commands and texts)");
                }
                if (lines < 1 || lines > 10) {
                    throw f.error("lines", "1 to 10");
                }
                yield new FieldDef(type, var, label, max, lines, 0, 0, 0, Map.of());
            }
            case "checkbox" -> {
                f.only(Set.of("type", "var", "label"));
                yield new FieldDef(type, var, label, 0, 0, 0, 0, 0, Map.of());
            }
            case "slider" -> {
                f.only(Set.of("type", "var", "label", "min", "max", "step"));
                float min = number(f, "min", 0);
                float max = number(f, "max", 10);
                float step = number(f, "step", 1);
                if (!(max > min) || !(step > 0)) {
                    throw f.error("max", "slider needs min < max and step > 0");
                }
                yield new FieldDef(type, var, label, 0, 0, min, max, step, Map.of());
            }
            case "choice" -> {
                f.only(Set.of("type", "var", "label", "options"));
                Map<String, String> options = new LinkedHashMap<>();
                Object o = Templates.plain(f.raw().get("options"));
                if (o instanceof List<?> list) {
                    for (Object x : list) {
                        options.put(String.valueOf(x), String.valueOf(x));
                    }
                } else if (o instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> x : map.entrySet()) {
                        options.put(String.valueOf(x.getKey()), String.valueOf(x.getValue()));
                    }
                }
                if (options.isEmpty()) {
                    throw f.error("options", "list of options expected ([easy, normal]) or value: text pairs");
                }
                yield new FieldDef(type, var, label, 0, 0, 0, 0, 0, options);
            }
            default -> throw f.error("type", "unknown field type '" + type + "' (text, checkbox, slider, choice)");
        };
    }

    private static float number(Node f, String key, float fallback) throws MenuException {
        String s = f.optString(key, null);
        if (s == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            throw f.error(key, "number expected, got '" + s + "'");
        }
    }

    /** Builds the form for this viewer: fields start with the current variable values. */
    static Form build(Def d, Click c, Vars vars, @Nullable Map<String, String> item) {
        Function<String, Component> mm = s -> MM.deserialize(vars.fill(c, s, item, null));
        Form.Builder b = Form.builder(mm.apply(d.title())).width(d.width());
        if (d.ok() != null || d.cancel() != null) {
            b.buttons(d.ok() != null ? mm.apply(d.ok()) : null, d.cancel() != null ? mm.apply(d.cancel()) : null);
        }
        for (int i = 0; i < d.fields().size(); i++) {
            FieldDef f = d.fields().get(i);
            String key = "f" + i;
            String value = vars.get(c, f.var());
            Component label = mm.apply(f.label());
            switch (f.type()) {
                case "text" -> b.text(key, label, value.length() > f.maxLength() ? value.substring(0, f.maxLength()) : value,
                    f.maxLength(), f.lines());
                case "checkbox" -> b.checkbox(key, label, "true".equalsIgnoreCase(value));
                case "slider" -> {
                    float v;
                    try {
                        v = Float.parseFloat(value.trim());
                    } catch (NumberFormatException e) {
                        v = f.min();
                    }
                    b.slider(key, label, f.min(), f.max(), f.step(), v);
                }
                default -> b.choice(key, label, value, f.options().entrySet().stream()
                    .map(o -> Form.option(o.getKey(), mm.apply(o.getValue()))).toList());
            }
        }
        return b.build();
    }

    /** Writes the entered values into the menu variables. */
    static void store(Def d, Click c, Form.Values v) {
        for (int i = 0; i < d.fields().size(); i++) {
            FieldDef f = d.fields().get(i);
            String key = "f" + i;
            String value = switch (f.type()) {
                case "text" -> Actions.clean(v.text(key));      // ends up in commands: one line, no '/', no '%'
                case "checkbox" -> String.valueOf(v.checked(key));
                case "slider" -> FormValues.format(v.number(key));
                default -> v.choice(key);
            };
            Vars.set(c, f.var(), value);
        }
    }
}
