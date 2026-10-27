package de.flyingfinger.glyphwork.api;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A small native dialog with several input fields (text, checkbox, slider, choice) and OK /
 * Cancel, opened with {@link Click#form(Form, java.util.function.Consumer)}. Dialogs cannot place
 * native fields inside the pixel GUI, and the client only sends their values with a native button,
 * so they get a dialog of their own; afterwards the scene is shown again.
 *
 * <pre>{@code
 * c.form(Form.builder(Component.text("Settings"))
 *         .text("name", Component.text("Name"), currentName)
 *         .slider("range", Component.text("Range"), 1, 32, 1, range)
 *         .checkbox("particles", Component.text("Particles"), particles)
 *         .choice("mode", Component.text("Mode"), "normal",
 *             Form.option("easy", Component.text("Easy")), Form.option("normal", Component.text("Normal")))
 *         .build(),
 *     values -> save(values.text("name"), (int) values.number("range"), values.checked("particles"), values.choice("mode")));
 * }</pre>
 */
public final class Form {

    private static final Pattern KEY = Pattern.compile("[a-zA-Z0-9_]+");

    /**
     * One input field. Fields are made by the {@link Builder}; more kinds of fields may come in later
     * versions, so a switch over them should have a default branch.
     */
    public sealed interface Field permits TextField, CheckboxField, SliderField, ChoiceField {
        String key();

        Component label();
    }

    /** Text field; {@code lines > 1} makes it a multi-line box. */
    public static final class TextField implements Field {
        private final String key;
        private final Component label;
        private final String initial;
        private final int maxLength;
        private final int lines;

        private TextField(String key, Component label, String initial, int maxLength, int lines) {
            this.key = key;
            this.label = label;
            this.initial = initial;
            this.maxLength = maxLength;
            this.lines = lines;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Component label() {
            return label;
        }

        public String initial() {
            return initial;
        }

        public int maxLength() {
            return maxLength;
        }

        public int lines() {
            return lines;
        }
    }

    /** A checkbox; comes back in {@link Values#checked}. */
    public static final class CheckboxField implements Field {
        private final String key;
        private final Component label;
        private final boolean initial;

        private CheckboxField(String key, Component label, boolean initial) {
            this.key = key;
            this.label = label;
            this.initial = initial;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Component label() {
            return label;
        }

        public boolean initial() {
            return initial;
        }
    }

    /** Slider from {@code min} to {@code max} in steps of {@code step}; the client shows "label: value". */
    public static final class SliderField implements Field {
        private final String key;
        private final Component label;
        private final double min;
        private final double max;
        private final double step;
        private final double initial;

        private SliderField(String key, Component label, double min, double max, double step, double initial) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.initial = initial;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Component label() {
            return label;
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }

        public double step() {
            return step;
        }

        public double initial() {
            return initial;
        }
    }

    /** A button that cycles through the options ("label: option"). */
    public static final class ChoiceField implements Field {
        private final String key;
        private final Component label;
        private final List<Option> options;
        private final String initial;

        private ChoiceField(String key, Component label, List<Option> options, String initial) {
            this.key = key;
            this.label = label;
            this.options = options;
            this.initial = initial;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Component label() {
            return label;
        }

        public List<Option> options() {
            return options;
        }

        public String initial() {
            return initial;
        }
    }

    /** One option of a {@link ChoiceField}: the id comes back in {@link Values#choice}, the text is shown. Made by {@link #option}. */
    public static final class Option {
        private final String id;
        private final Component text;

        private Option(String id, Component text) {
            this.id = id;
            this.text = text;
        }

        public String id() {
            return id;
        }

        public Component text() {
            return text;
        }
    }

    /** What the player entered. Values are checked: numbers lie in range, choices are one of the ids. */
    @org.jetbrains.annotations.ApiStatus.NonExtendable
    public interface Values {
        /** Text of a text field ("" if missing). */
        String text(String key);

        boolean checked(String key);

        /** Value of a slider (its initial value if missing). */
        double number(String key);

        /** Id of the chosen option. */
        String choice(String key);
    }

    private final Component title;
    private final List<Field> fields;
    private final int width;
    private final @Nullable Component ok;
    private final @Nullable Component cancel;

    private Form(Builder b) {
        this.title = b.title;
        this.fields = List.copyOf(b.fields);
        this.width = b.width;
        this.ok = b.ok;
        this.cancel = b.cancel;
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public static Option option(String id, Component text) {
        return new Option(id, text);
    }

    public Component title() {
        return title;
    }

    public List<Field> fields() {
        return fields;
    }

    /** Width of the fields in pixels. */
    public int width() {
        return width;
    }

    /** Label of the OK button, or null for the default. */
    public @Nullable Component ok() {
        return ok;
    }

    /** Label of the Cancel button, or null for the default. */
    public @Nullable Component cancel() {
        return cancel;
    }

    public static final class Builder {
        private final Component title;
        private final List<Field> fields = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        private int width = 200;
        private @Nullable Component ok;
        private @Nullable Component cancel;

        private Builder(Component title) {
            this.title = title;
        }

        private Builder add(Field f) {
            if (!KEY.matcher(f.key()).matches()) {
                throw new IllegalArgumentException("form key may only use a-z, A-Z, 0-9 and '_': " + f.key());
            }
            if (!keys.add(f.key())) {
                throw new IllegalArgumentException("form key used twice: " + f.key());
            }
            fields.add(f);
            return this;
        }

        /** One-line text field, at most 256 characters. */
        public Builder text(String key, Component label, String initial) {
            return text(key, label, initial, 256, 1);
        }

        /** Text field with a length limit (1 to 32000) and {@code lines} lines (1 = one line). */
        public Builder text(String key, Component label, String initial, int maxLength, int lines) {
            if (maxLength < 1 || lines < 1) {
                throw new IllegalArgumentException("maxLength and lines must be at least 1");
            }
            return add(new TextField(key, label, initial, maxLength, lines));
        }

        public Builder checkbox(String key, Component label, boolean initial) {
            return add(new CheckboxField(key, label, initial));
        }

        public Builder slider(String key, Component label, double min, double max, double step, double initial) {
            if (!(max > min) || !(step > 0)) {
                throw new IllegalArgumentException("slider needs min < max and step > 0");
            }
            return add(new SliderField(key, label, min, max, step, Math.max(min, Math.min(max, initial))));
        }

        /** {@code initial} is the id of the preselected option (the first if unknown). */
        public Builder choice(String key, Component label, String initial, Option... options) {
            return choice(key, label, initial, List.of(options));
        }

        public Builder choice(String key, Component label, String initial, List<Option> options) {
            if (options.isEmpty()) {
                throw new IllegalArgumentException("choice needs at least one option");
            }
            String first = options.getFirst().id();
            boolean known = options.stream().anyMatch(o -> o.id().equals(initial));
            return add(new ChoiceField(key, label, List.copyOf(options), known ? initial : first));
        }

        /** Width of the fields in pixels (default 200). */
        public Builder width(int width) {
            this.width = Math.max(1, Math.min(1024, width));
            return this;
        }

        /** Labels of OK and Cancel (null keeps the default of that button). */
        public Builder buttons(@Nullable Component ok, @Nullable Component cancel) {
            this.ok = ok;
            this.cancel = cancel;
            return this;
        }

        public Form build() {
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("a form needs at least one field");
            }
            return new Form(this);
        }
    }
}
