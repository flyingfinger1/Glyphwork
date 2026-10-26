package de.flyingfinger.dialogui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

import java.time.Duration;
import java.util.List;

/** The only place that touches the Paper dialog API (it is young and changes between versions). */
public final class DialogFactory {

    private DialogFactory() {
    }

    /**
     * A dialog whose body is the rendered GUI. {@code afterAction NONE}: text clicks keep the
     * screen open, the server answers with a freshly rendered dialog.
     */
    public static Dialog gui(Component title, Component body, int width) {
        ActionButton close = ActionButton.builder(Component.text("Schließen"))
            .action(DialogAction.customClick((view, audience) -> audience.closeDialog(), callbackOptions()))
            .build();
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(body, width)))
                .build())
            .type(DialogType.notice(close)));
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
