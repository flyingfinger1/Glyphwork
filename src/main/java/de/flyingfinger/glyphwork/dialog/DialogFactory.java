package de.flyingfinger.glyphwork.dialog;

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
    public static Dialog gui(Component title, Component body, int width, @Nullable Component exitLabel) {
        ActionButton exit = ActionButton.builder(exitLabel != null ? exitLabel : Component.text("Schließen"))
            .action(DialogAction.customClick((view, audience) -> audience.closeDialog(), callbackOptions()))
            .build();
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

    public static ClickCallback.Options callbackOptions() {
        return ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(30))
            .build();
    }

    public static void show(Audience audience, Dialog dialog) {
        audience.showDialog(dialog);
    }
}
