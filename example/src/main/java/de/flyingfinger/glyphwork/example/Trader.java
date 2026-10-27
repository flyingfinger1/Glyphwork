package de.flyingfinger.glyphwork.example;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Click;
import de.flyingfinger.glyphwork.api.DisplayMode;
import de.flyingfinger.glyphwork.api.Glyphwork;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.Scene;
import de.flyingfinger.glyphwork.api.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A small trader: goods with item icons in a grid, an amount (buttons and a text prompt), a buy
 * button that is only active when the player can pay, and an options screen for the display mode.
 *
 * <p>Both scenes are built once and reused: everything that depends on the player is a function
 * of the {@link View}, evaluated each time the scene is shown.
 */
public final class Trader {

    /** One offer: {@code count} pieces of {@code material} for {@code price} emeralds. */
    record Offer(Material material, String name, int count, int price) {
        String item() {
            return material.key().value();   // "bread": icons take item ids like in /give
        }
    }

    private static final List<Offer> OFFERS = List.of(
        new Offer(Material.BREAD, "Brot", 8, 1),
        new Offer(Material.COOKED_BEEF, "Steak", 4, 1),
        new Offer(Material.TORCH, "Fackeln", 16, 1),
        new Offer(Material.ARROW, "Pfeile", 16, 2),
        new Offer(Material.IRON_INGOT, "Eisenbarren", 4, 3),
        new Offer(Material.ENDER_PEARL, "Enderperle", 1, 4),
        new Offer(Material.EXPERIENCE_BOTTLE, "Erfahrungsflasche", 8, 5),
        new Offer(Material.NAME_TAG, "Namensschild", 1, 6),
        new Offer(Material.SADDLE, "Sattel", 1, 8),
        new Offer(Material.DIAMOND, "Diamant", 1, 10));

    private static final int MAX_AMOUNT = 64;
    private static final TextColor GREEN = TextColor.color(0x50E050);
    private static final TextColor RED = TextColor.color(0xC03030);

    // per-player state keys (kept while the player is online, also across reopen and back)
    private static final String OFFER = "offer";
    private static final String AMOUNT = "amount";

    public final Scene main;
    private final Scene options;

    public Trader() {
        options = buildOptions();
        main = buildMain(options);
    }

    // --- state helpers -------------------------------------------------------------------------

    private static @Nullable Offer offer(View v) {
        return v.get(OFFER, (Offer) null);
    }

    private static int amount(View v) {
        return v.get(AMOUNT, 1);
    }

    private static int total(View v) {
        Offer o = offer(v);
        return o == null ? 0 : o.price() * amount(v);
    }

    private static int emeralds(Player p) {
        int n = 0;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.EMERALD) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private static boolean canBuy(View v) {
        return offer(v) != null && emeralds(v.player()) >= total(v);
    }

    private static void setAmount(Click c, int amount) {
        c.set(AMOUNT, Math.max(1, Math.min(MAX_AMOUNT, amount)));
    }

    // --- screens -------------------------------------------------------------------------------

    private static Scene buildMain(Scene options) {
        return Scene.builder("example:trader", 176, 200)
            .title(Component.text("Händler"))
            .background()                                   // panel frame over the whole scene
            .add(Label.of(8, 7, "Händler"))

            // the goods: 6 x 2 cells, each a 24 px slot frame on a 26 px pitch with the item icon
            .add(Grid.of("goods", 10, 20, 6, 2, v -> OFFERS)
                .cell(26, 24)
                .icon(Offer::item)
                .count(o -> String.valueOf(o.count()))     // pack size at the bottom right, like in the inventory
                .tooltip(o -> Component.text(o.count() + "x " + o.name() + " - " + o.price() + " Smaragde"))
                .selected(Trader::offer)
                .onClick((c, o) -> {
                    c.set(OFFER, o);
                    c.set(AMOUNT, 1);
                    c.reopen();                             // draw again with the new state
                }))

            // the selected offer
            .add(Icon.of(10, 80, v -> offer(v) == null ? null : offer(v).item())
                .count(v -> offer(v) == null ? null : String.valueOf(offer(v).count() * amount(v))))
            .add(Label.of(32, 85, v -> offer(v) == null ? "Wähle eine Ware" : offer(v).name()).width(136))
            .add(Label.of(10, 104, v -> offer(v) == null ? "" : "Packung: " + offer(v).count() + " Stück"))
            .add(Label.of(10, 116, v -> offer(v) == null ? "" : "Preis: " + offer(v).price() + " Smaragde"))

            // amount: - / + and a text field (dialogs cannot read typing live, so it is a prompt)
            .add(Label.of(10, 136, v -> "Menge: " + amount(v)).width(58))
            .add(Button.of(72, 132, 16, 16).text("-").onClick(c -> { setAmount(c, amount(c) - 1); c.reopen(); }))
            .add(Button.of(92, 132, 16, 16).text("+").onClick(c -> { setAmount(c, amount(c) + 1); c.reopen(); }))
            .add(Button.of(112, 132, 56, 16).text("Eingabe")
                .tooltip("Menge eintippen (1-" + MAX_AMOUNT + ")")
                .onClick(c -> c.prompt(Component.text("Wie viele Packungen?"), String.valueOf(amount(c)), text -> {
                    try {
                        setAmount(c, Integer.parseInt(text.trim()));
                    } catch (NumberFormatException e) {
                        c.player().sendMessage(Component.text("Bitte eine Zahl eingeben.", NamedTextColor.RED));
                    }
                })))                                        // after the prompt this scene is shown again

            // what it costs and what the player has (red when it is not enough)
            .add(Label.of(10, 156, v -> "Gesamt: " + total(v)).width(76))
            .add(Label.of(90, 156, v -> "Du hast: " + emeralds(v.player())).width(78)
                .color(v -> canBuy(v) || offer(v) == null ? Label.DARK : RED))

            .add(Button.of(8, 176, 64, 16).text("Optionen").onClick(c -> c.open(options)))
            .add(Button.of(96, 172, 72, 24).icon("emerald").text("Kaufen").tint(GREEN)
                .enabled(Trader::canBuy)                    // grayed out and not clickable otherwise
                .tooltip(v -> canBuy(v) ? null : Component.text("Nicht genug Smaragde"))
                .onClick(Trader::buy))
            .build();
    }

    private static void buy(Click c) {
        Offer o = offer(c);
        Player p = c.player();
        int cost = total(c);
        if (o == null || emeralds(p) < cost) {               // checked again: the inventory may have changed
            c.reopen();
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.EMERALD, cost));
        int pieces = o.count() * amount(c);
        // whatever does not fit is dropped at the player's feet
        p.getInventory().addItem(new ItemStack(o.material(), pieces)).values()
            .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.sendMessage(Component.text("Gekauft: " + pieces + "x " + o.name() + " für " + cost + " Smaragde", GREEN));
        c.reopen();
    }

    private static Scene buildOptions() {
        // the display mode belongs to Glyphwork and applies to every Glyphwork screen;
        // one button per mode, the active one is marked (also readable in the plain "Einfach" dialog)
        return Scene.builder("example:trader-options", 176, 136)
            .title(Component.text("Händler - Optionen"))
            .background()
            .add(Label.of(8, 7, "Optionen"))
            .add(Label.of(8, 22, "Darstellung der Menüs:"))
            .add(modeButton(DisplayMode.FULL, 34))
            .add(modeButton(DisplayMode.NO_ICONS, 52))
            .add(modeButton(DisplayMode.SIMPLE, 70))
            .add(Label.of(8, 92, "Icons oder Text verrutscht? Dann"))
            .add(Label.of(8, 102, "\"Ohne Icons\" oder \"Einfach\" nehmen."))
            .add(Button.of(8, 114, 64, 16).text("< Zurück").onClick(Click::back))
            .build();
    }

    private static Button modeButton(DisplayMode mode, int y) {
        return Button.of(8, y, 160, 16)
            .text(v -> Glyphwork.get().displayMode(v.player()) == mode ? "> " + modeName(mode) + " <" : modeName(mode))
            .tint(v -> Glyphwork.get().displayMode(v.player()) == mode ? GREEN : Button.GRAY)
            .onClick(c -> {
                Glyphwork.get().displayMode(c.player(), mode);
                c.reopen();                                 // shows this screen in the new mode
            });
    }

    private static String modeName(DisplayMode mode) {
        return switch (mode) {
            case FULL -> "Voll (mit Icons)";
            case NO_ICONS -> "Ohne Icons";
            case SIMPLE -> "Einfach";
        };
    }
}
