package de.flyingfinger.glyphwork.demo;

import de.flyingfinger.glyphwork.api.View;
import de.flyingfinger.glyphwork.core.Messages;
import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Grid;
import de.flyingfinger.glyphwork.api.Icon;
import de.flyingfinger.glyphwork.api.Label;
import de.flyingfinger.glyphwork.api.ListView;
import de.flyingfinger.glyphwork.api.Panel;
import de.flyingfinger.glyphwork.api.Scene;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.stream.IntStream;

/** The /glyphwork test screens, built with the public API only (also the reference example). */
public final class Demo {

    private static final TextColor RED = TextColor.color(0xFF5050);
    private static final TextColor GREEN = TextColor.color(0x50E050);
    private static final TextColor BLUE = TextColor.color(0x6090FF);

    private static final List<Integer> NUMBERS = IntStream.rangeClosed(1, 30).boxed().toList();

    private static final List<String> ITEMS = List.of("diamond", "emerald", "gold_ingot", "iron_ingot",
        "redstone", "lapis_lazuli", "apple", "bread", "cake", "diamond_sword", "bow", "crossbow", "trident",
        "fishing_rod", "potion", "splash_potion", "leather_helmet", "leather_chestplate", "diamond_chestplate",
        "elytra", "totem_of_undying", "ender_pearl", "compass", "clock", "spyglass", "torch", "lantern",
        "oak_sapling", "dandelion", "oak_leaves", "grass_block", "stone", "oak_log", "crafting_table", "furnace",
        "tnt", "glass", "sea_lantern", "water_bucket", "lava_bucket", "experience_bottle", "name_tag");

    public final Scene main;
    public final Scene lists;
    public final Scene items;

    public Demo() {
        items = buildItems();
        lists = buildLists(items);
        main = buildMain(lists);
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** A demo text in the viewer's language (lang/*.yml, keys demo.*). */
    private static String m(View v, String key, Object... args) {
        return Messages.active().plain(v.player(), key, args);
    }

    private static Component c(View v, String key, Object... args) {
        return Messages.active().get(v.player(), key, args);
    }

    /** The fruit list of the viewer's language (demo.fruits, comma-separated). */
    static List<String> fruits(View v) {
        return java.util.Arrays.stream(m(v, "demo.fruits").split(",")).map(String::trim).filter(f -> !f.isEmpty()).toList();
    }

    private static Scene buildItems() {
        // grid 20..124, its page buttons 126..142, selection and buttons below
        return Scene.builder("glyphwork:demo-items", 176, 196)
            .title(v -> Component.text(m(v, "demo.items-title")))
            .add(Panel.of(0, 0, 176, 196))
            .add(Label.of(8, 7, v -> m(v, "demo.items-head")))
            .add(Grid.of("items", 10, 20, 6, 4, v -> ITEMS)
                .cell(26, 24)
                .icon(id -> id)
                .tooltip(id -> Component.text(pretty(id)))
                .selected(v -> v.get("item", (String) null))
                .onClick((k, id) -> { k.set("item", id); k.reopen(); }))
            .add(Label.of(8, 150, v -> m(v, "demo.selection", "item", pretty(v.get("item", "-")))).width(136))
            .add(Icon.of(152, 146, v -> v.get("item", (String) null)))
            .add(Button.of(8, 168, 56, 16).text(v -> m(v, "demo.back")).onClick(k -> k.back()))
            .add(Button.of(96, 164, 72, 24).icon("emerald").text(v -> m(v, "demo.buy")).tint(TextColor.color(0x50E050))
                .tooltip(v -> c(v, "demo.buy-tip"))
                .onClick(k -> k.player().sendMessage(c(k, "demo.bought",
                    "item", k.get("item", (String) null) == null ? m(k, "demo.nothing") : pretty(k.get("item", "-"))))))
            .build();
    }

    private static Scene buildMain(Scene lists) {
        return Scene.builder("glyphwork:demo", 176, 128)
            .title(Component.text("Glyphwork"))
            .add(Panel.of(0, 0, 176, 128)
                .onClick(k -> { k.set("last", m(k, "demo.what.panel")); k.reopen(); })
                .tooltip(v -> c(v, "demo.panel-tip")))
            .add(Label.of(8, 7, "Glyphwork - Demo"))
            .add(Button.of(152, 4, 16, 16).text("x").tint(RED).tooltip(v -> c(v, "demo.close")).onClick(k -> k.close()))
            .add(Button.of(8, 22, 72, 16)
                .text(v -> m(v, v.get("sound", false) ? "demo.sound-on" : "demo.sound-off"))
                .tint(v -> v.get("sound", false) ? GREEN : RED)
                .tooltip(v -> c(v, "demo.toggle-tip"))
                .onClick(k -> {
                    k.set("sound", !k.get("sound", false));
                    k.set("last", m(k, "demo.what.toggle"));
                    k.reopen();
                }))
            .add(Button.of(96, 31, 64, 16).text("+1").tint(BLUE).tooltip(v -> c(v, "demo.counter-tip"))
                .onClick(k -> {
                    k.set("count", k.get("count", 0) + 1);
                    k.set("last", m(k, "demo.what.counter"));
                    k.reopen();
                }))
            .add(Label.of(8, 54, v -> m(v, "demo.counter", "count", v.get("count", 0))))
            .add(Label.of(8, 65, v -> m(v, "demo.last", "what", v.get("last", "-"))).width(160))
            .add(Grid.of("slots", 8, 78, 8, 1, v -> List.of(0, 1, 2, 3, 4, 5, 6, 7))
                .cell(20, 16)
                .selected(v -> v.get("selected", (Integer) null))
                .tooltip(i -> Component.text("#" + (i + 1)))
                .onClick((k, i) -> {
                    k.set("selected", i);
                    k.set("last", m(k, "demo.what.slot", "n", i + 1));
                    k.reopen();
                }))
            .add(Panel.shape(8, 100, "##########", "#####.....")
                .frame("slot")
                .tooltip(v -> c(v, "demo.shape-tip"))
                .onClick(k -> { k.set("last", m(k, "demo.what.shape")); k.reopen(); }))
            .add(Button.of(96, 104, 72, 16).text(v -> m(v, "demo.lists")).tooltip(v -> c(v, "demo.lists-head"))
                .onClick(k -> k.open(lists)))
            .build();
    }

    private static Scene buildLists(Scene items) {
        return Scene.builder("glyphwork:demo-lists", 176, 168)
            .title(v -> Component.text(m(v, "demo.lists-title")))
            .add(Panel.of(0, 0, 176, 168))
            .add(Label.of(8, 7, v -> m(v, "demo.lists-head")))
            .add(Button.of(8, 20, 160, 16)
                .text(v -> v.get("query", "").isEmpty() ? m(v, "demo.search") : m(v, "demo.search-for", "query", v.get("query", "")))
                .tooltip(v -> c(v, "demo.search-tip"))
                .onClick(k -> k.prompt(c(k, "demo.search-title"), k.get("query", ""), text -> {
                    k.set("query", text.trim());
                    k.set("glyphwork:page:fruits", 0);
                })))
            .add(ListView.of("fruits", 8, 42, 80, 4, v -> {
                    String q = v.get("query", "").toLowerCase();
                    return fruits(v).stream().filter(f -> f.toLowerCase().contains(q)).toList();
                })
                .selected(v -> v.get("fruit", (String) null))
                .color(f -> TextColor.color(0xFFFFFF))
                .onSelect((k, f) -> { k.set("fruit", f); k.reopen(); }))
            .add(Grid.of("numbers", 96, 42, 4, 3, v -> NUMBERS)
                .label(String::valueOf)
                .tint(n -> n % 3 == 0 ? BLUE : n % 3 == 1 ? GREEN : null)
                .tooltip(n -> Component.text("#" + n))
                .onClick((k, n) -> { k.set("number", n); k.reopen(); }))
            .add(Label.of(8, 132, v -> m(v, "demo.fruit", "fruit", v.get("fruit", "-"))).width(80))
            .add(Label.of(96, 132, v -> m(v, "demo.number", "number", v.get("number", (Object) "-"))).width(72))
            .add(Button.of(8, 146, 56, 16).text(v -> m(v, "demo.back")).onClick(k -> k.back()))
            .add(Button.of(112, 146, 56, 16).text(v -> m(v, "demo.items")).tooltip(v -> c(v, "demo.items-head"))
                .onClick(k -> k.open(items)))
            .build();
    }
}
