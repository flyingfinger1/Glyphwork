package de.flyingfinger.glyphwork.dialog;

import de.flyingfinger.glyphwork.api.Form;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The checked answers of a form. The client sends them, so nothing is trusted: texts are cut to
 * their length limit, numbers are clamped and put on the step grid, choices must be one of the ids.
 */
public final class FormValues implements Form.Values {

    private final Map<String, Object> values = new HashMap<>();

    private FormValues() {
    }

    /** Reads every field of {@code form} through the given getters (null = not sent). */
    public static FormValues read(Form form, Function<String, @Nullable String> text,
                                  Function<String, @Nullable Boolean> bool, Function<String, @Nullable Float> number) {
        FormValues v = new FormValues();
        for (Form.Field f : form.fields()) {
            switch (f) {
                case Form.Text t -> {
                    String s = text.apply(t.key());
                    s = s == null ? t.initial() : s;
                    v.values.put(t.key(), s.length() > t.maxLength() ? s.substring(0, t.maxLength()) : s);
                }
                case Form.Checkbox c -> {
                    Boolean b = bool.apply(c.key());
                    v.values.put(c.key(), b == null ? c.initial() : b);
                }
                case Form.Slider s -> {
                    Float n = number.apply(s.key());
                    float x = n == null || n.isNaN() ? s.initial() : n;
                    x = Math.max(s.min(), Math.min(s.max(), x));
                    x = s.min() + Math.round((x - s.min()) / s.step()) * s.step();
                    v.values.put(s.key(), Math.min(s.max(), x));
                }
                case Form.Choice c -> {
                    String id = text.apply(c.key());
                    boolean known = id != null && c.options().stream().anyMatch(o -> o.id().equals(id));
                    v.values.put(c.key(), known ? id : c.initial());
                }
            }
        }
        return v;
    }

    @Override
    public String text(String key) {
        return values.get(key) instanceof String s ? s : "";
    }

    @Override
    public boolean checked(String key) {
        return values.get(key) instanceof Boolean b && b;
    }

    @Override
    public float number(String key) {
        return values.get(key) instanceof Float f ? f : 0f;
    }

    @Override
    public String choice(String key) {
        return text(key);
    }

    /** A number as menus show it: "12" for whole numbers, "2.5" otherwise. */
    public static String format(float n) {
        return n == Math.rint(n) && Math.abs(n) < 1e9 ? String.valueOf((long) n) : String.valueOf(n);
    }
}
