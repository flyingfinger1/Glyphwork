package de.flyingfinger.glyphwork.render;

import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.Form;
import de.flyingfinger.glyphwork.api.Scene;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Runs a handler and then draws the scene again, unless the handler already showed something
 * (reopen, open, back, close, prompt, form). Used by sliders, checkboxes and choices, whose change
 * should always be visible.
 */
final class Redraw implements Click {

    private final Click click;
    private boolean shown;

    private Redraw(Click click) {
        this.click = click;
    }

    static Consumer<Click> after(Consumer<Click> handler) {
        return c -> {
            Redraw r = new Redraw(c);
            handler.accept(r);
            if (!r.shown) {
                c.reopen();
            }
        };
    }

    @Override public Player player() { return click.player(); }
    @Override public Scene scene() { return click.scene(); }
    @Override public <T> T get(String key, T fallback) { return click.get(key, fallback); }
    @Override public void set(String key, Object value) { click.set(key, value); }
    @Override public void reopen() { shown = true; click.reopen(); }
    @Override public void open(Scene next) { shown = true; click.open(next); }
    @Override public void back() { shown = true; click.back(); }
    @Override public void close() { shown = true; click.close(); }

    @Override
    public void prompt(Component title, String initial, Consumer<String> onSubmit) {
        shown = true;
        click.prompt(title, initial, onSubmit);
    }

    @Override
    public void form(Form form, Consumer<Form.Values> onSubmit) {
        shown = true;
        click.form(form, onSubmit);
    }
}
