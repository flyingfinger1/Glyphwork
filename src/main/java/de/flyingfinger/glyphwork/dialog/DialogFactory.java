package de.flyingfinger.glyphwork.dialog;

import de.flyingfinger.glyphwork.api.Form;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** The only place that touches the Paper dialog API (it is young and changes between versions). */
public final class DialogFactory {

    /** FocusableTextWidget.DEFAULT_PADDING in the 26.3 client. */
    private static final int TEXT_PADDING = 4;
    private static final String INPUT_KEY = "glyphwork_text";

    private DialogFactory() {
    }

    /**
     * A dialog whose body is the rendered GUI. {@code afterAction NONE}: text clicks keep the
     * screen open, the server answers with a freshly rendered dialog.
     */
    public static Dialog gui(Component title, Component body, int width, @Nullable Component exitLabel, Runnable onExit,
                             ClickCallback.Options callbacks) {
        ActionButton exit = exitButton(exitLabel, onExit, callbacks);
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                // the client's FocusableTextWidget wraps at width - 2 * 4 px padding
                .body(List.of(DialogBody.plainMessage(body, Math.min(1024, width + 2 * TEXT_PADDING))))
                .build())
            .type(DialogType.notice(exit)));
    }

    /** One native button of a fallback dialog. */
    public record FallbackButton(Component label, @Nullable Component tooltip, Runnable onClick) {
    }

    /**
     * Plain dialog for players without the pack: text lines as body, actions as native buttons
     * (two columns), plus the exit button.
     */
    public static Dialog fallback(Component title, List<Component> lines, List<FallbackButton> buttons,
                                  @Nullable Component exitLabel, Runnable onExit) {
        return fallback(title, lines, buttons, List.of(), exitLabel, onExit, false);
    }

    /**
     * @param bedrock Bedrock (Geyser): the form closes after a click and the server sends the next
     *                one. With NONE Geyser reopens the old form at once, and a quick click on it is
     *                matched to the buttons of the new one; WAIT_FOR_RESPONSE leaves a "waiting" form
     *                that a closing menu cannot remove.
     */
    public static Dialog fallback(Component title, List<Component> lines, List<FallbackButton> buttons,
                                  List<Component> measure, @Nullable Component exitLabel, Runnable onExit, boolean bedrock) {
        ActionButton exit = exitButton(exitLabel, onExit, callbackOptions());
        Component body = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
        // like hand-made server dialogs: short texts in two columns, long ones in one wide column
        int columns = columnsFor(buttons, measure);
        int width = columns == 1 ? 300 : 150;
        List<ActionButton> actions = buttons.stream()
            .map(b -> {
                ActionButton.Builder ab = ActionButton.builder(b.label()).width(width)
                    .action(DialogAction.customClick((view, audience) -> b.onClick().run(), callbackOptions()));
                if (b.tooltip() != null) {
                    ab.tooltip(b.tooltip());
                }
                return ab.build();
            })
            .toList();
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(bedrock ? DialogBase.DialogAfterAction.CLOSE : DialogBase.DialogAfterAction.NONE)
                .body(lines.isEmpty() ? List.of() : List.of(DialogBody.plainMessage(body, 300)))
                .build())
            .type(actions.isEmpty() ? DialogType.notice(exit)
                : DialogType.multiAction(actions).columns(columns).exitAction(exit).build()));
    }

    /** Longest characters per line that still fit a 150 px button (the client cuts longer ones). */
    static final int NARROW_TEXT = 22;

    static int columnsFor(List<FallbackButton> buttons) {
        return columnsFor(buttons, List.of());
    }

    /**
     * 1 when a button text would not fit a narrow button (or there is only one button), else 2.
     * {@code measure}: texts of buttons shown elsewhere (other tabs), so every tab gets the same layout.
     */
    static int columnsFor(List<FallbackButton> buttons, List<Component> measure) {
        if (buttons.size() <= 1 && measure.isEmpty()) {
            return 1;
        }
        List<Component> all = new java.util.ArrayList<>(measure);
        buttons.forEach(b -> all.add(b.label()));
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plain =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (Component c : all) {
            for (String line : plain.serialize(c).split("\n")) {
                if (line.strip().length() > NARROW_TEXT) {
                    return 1;
                }
            }
        }
        return 2;
    }

    /** A small native dialog with one text field, OK and Cancel. */
    public static Dialog prompt(Component title, String initial, Component ok, Component cancel,
                                Consumer<String> onSubmit, Runnable onCancel) {
        ActionButton yes = ActionButton.builder(ok)
            .action(DialogAction.customClick((view, audience) -> {
                String text = view.getText(INPUT_KEY);
                onSubmit.accept(text == null ? "" : text);
            }, callbackOptions()))
            .build();
        ActionButton no = ActionButton.builder(cancel)
            .action(DialogAction.customClick((view, audience) -> onCancel.run(), callbackOptions()))
            .build();
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .inputs(List.of(DialogInput.text(INPUT_KEY, Component.empty())
                    .labelVisible(false)
                    .initial(initial)
                    .maxLength(256)
                    .width(200)
                    .build()))
                .build())
            .type(DialogType.confirmation(yes, no)));
    }

    /** A web address with "Open" (handled by the client, which asks first) and "Back". */
    public static Dialog link(Component title, String url, Component open, Component back, Runnable onBack) {
        ActionButton yes = ActionButton.builder(open)
            .action(DialogAction.staticAction(net.kyori.adventure.text.event.ClickEvent.openUrl(url)))
            .build();
        ActionButton no = ActionButton.builder(back)
            .action(DialogAction.customClick((view, audience) -> onBack.run(), callbackOptions()))
            .build();
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(Component.text(url, net.kyori.adventure.text.format.NamedTextColor.GRAY), 300)))
                .build())
            .type(DialogType.confirmation(yes, no)));
    }

    /**
     * A form: its fields as native inputs, OK and Cancel. ESC runs Cancel (the client's onCancel of
     * a confirmation dialog is its "no" button).
     */
    public static Dialog form(Form form, Component ok, Component cancel, Consumer<FormValues> onSubmit, Runnable onCancel) {
        ActionButton yes = ActionButton.builder(form.ok() != null ? form.ok() : ok)
            .action(DialogAction.customClick((view, audience) ->
                onSubmit.accept(FormValues.read(form, view::getText, view::getBoolean, view::getFloat)), callbackOptions()))
            .build();
        ActionButton no = ActionButton.builder(form.cancel() != null ? form.cancel() : cancel)
            .action(DialogAction.customClick((view, audience) -> onCancel.run(), callbackOptions()))
            .build();
        List<DialogInput> inputs = new java.util.ArrayList<>();
        for (Form.Field f : form.fields()) {
            inputs.add(switch (f) {
                case Form.Text t -> {
                    var b = DialogInput.text(t.key(), t.label()).initial(t.initial()).maxLength(t.maxLength()).width(form.width());
                    if (t.lines() > 1) {
                        b.multiline(io.papermc.paper.registry.data.dialog.input.TextDialogInput.MultilineOptions
                            .create(t.lines(), Math.min(512, 9 * t.lines() + 8)));
                    }
                    yield b.build();
                }
                case Form.Checkbox c -> DialogInput.bool(c.key(), c.label()).initial(c.initial()).build();
                case Form.Slider s -> DialogInput.numberRange(s.key(), s.label(), s.min(), s.max())
                    .step(s.step()).initial(s.initial()).width(form.width()).build();
                case Form.Choice c -> DialogInput.singleOption(c.key(), c.label(), c.options().stream()
                        .map(o -> io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry
                            .create(o.id(), o.text(), o.id().equals(c.initial())))
                        .toList())
                    .width(form.width()).build();
            });
        }
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(form.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .inputs(inputs)
                .build())
            .type(DialogType.confirmation(yes, no)));
    }

    /**
     * The exit button. The client also runs its action when the player presses ESC, so
     * {@code onExit} hears about every way the dialog is closed by the player.
     */
    private static ActionButton exitButton(@Nullable Component label, Runnable onExit, ClickCallback.Options callbacks) {
        return ActionButton.builder(label != null ? label : Component.text("Close"))
            .action(DialogAction.customClick((view, audience) -> {
                audience.closeDialog();
                onExit.run();
            }, callbacks))
            .build();
    }

    public static ClickCallback.Options callbackOptions() {
        return callbackOptions(Duration.ofMinutes(30));
    }

    /** Callbacks of scenes that redraw themselves live shorter: each redraw makes new ones. */
    public static ClickCallback.Options callbackOptions(Duration lifetime) {
        return ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(lifetime)
            .build();
    }

    public static void show(Audience audience, Dialog dialog) {
        audience.showDialog(dialog);
    }
}
