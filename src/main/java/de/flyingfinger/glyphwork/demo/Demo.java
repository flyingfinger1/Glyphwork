package de.flyingfinger.glyphwork.demo;

import de.flyingfinger.glyphwork.api.Button;
import de.flyingfinger.glyphwork.api.Grid;
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

    private static final List<String> FRUITS = List.of("Apfel", "Aprikose", "Banane", "Birne", "Brombeere",
        "Cranberry", "Dattel", "Erdbeere", "Feige", "Granatapfel", "Grapefruit", "Heidelbeere", "Himbeere",
        "Holunder", "Johannisbeere", "Kaki", "Kirsche", "Kiwi", "Limette", "Litschi", "Mandarine", "Mango",
        "Maracuja", "Melone", "Mirabelle", "Nektarine", "Orange", "Papaya", "Pfirsich", "Pflaume", "Quitte",
        "Stachelbeere", "Sternfrucht", "Traube", "Zitrone", "Zwetschge");
    private static final List<Integer> NUMBERS = IntStream.rangeClosed(1, 30).boxed().toList();

    public final Scene main;
    public final Scene lists;

    public Demo() {
        lists = buildLists();
        main = buildMain(lists);
    }

    private static Scene buildMain(Scene lists) {
        return Scene.builder("glyphwork:demo", 176, 128)
            .title(Component.text("Glyphwork"))
            .add(Panel.of(0, 0, 176, 128)
                .onClick(c -> { c.set("last", "Panel"); c.reopen(); })
                .tooltip(Component.text("Panel-Hintergrund")))
            .add(Label.of(8, 7, "Glyphwork - Demo"))
            .add(Button.of(152, 4, 16, 16).text("x").tint(RED).tooltip("Schließen").onClick(c -> c.close()))
            .add(Button.of(8, 22, 72, 16)
                .text(v -> v.get("sound", false) ? "Sound: AN" : "Sound: AUS")
                .tint(v -> v.get("sound", false) ? GREEN : RED)
                .tooltip("Klick zum Umschalten (y=22)")
                .onClick(c -> {
                    c.set("sound", !c.get("sound", false));
                    c.set("last", "Umschalter");
                    c.reopen();
                }))
            .add(Button.of(96, 31, 64, 16).text("+1").tint(BLUE).tooltip("Zähler +1 (y=31)")
                .onClick(c -> {
                    c.set("count", c.get("count", 0) + 1);
                    c.set("last", "Zähler");
                    c.reopen();
                }))
            .add(Label.of(8, 54, v -> "Zähler: " + v.get("count", 0)))
            .add(Label.of(8, 65, v -> "Letzter Klick: " + v.get("last", "-")).width(160))
            .add(Grid.of("slots", 8, 78, 8, 1, v -> List.of(0, 1, 2, 3, 4, 5, 6, 7))
                .cell(20, 16)
                .selected(v -> v.get("selected", (Integer) null))
                .tooltip(i -> Component.text("Zelle " + (i + 1)))
                .onClick((c, i) -> {
                    c.set("selected", i);
                    c.set("last", "Zelle " + (i + 1));
                    c.reopen();
                }))
            .add(Panel.shape(8, 100, "##########", "#####.....")
                .frame("slot")
                .tooltip(Component.text("L-Form (Innenecken)"))
                .onClick(c -> { c.set("last", "L-Form"); c.reopen(); }))
            .add(Button.of(96, 104, 72, 16).text("Listen >").tooltip("Liste, Raster, Suche")
                .onClick(c -> c.open(lists)))
            .build();
    }

    private static Scene buildLists() {
        return Scene.builder("glyphwork:demo-lists", 176, 168)
            .title(Component.text("Glyphwork - Listen"))
            .add(Panel.of(0, 0, 176, 168))
            .add(Label.of(8, 7, "Liste, Raster, Suche"))
            .add(Button.of(8, 20, 160, 16)
                .text(v -> v.get("query", "").isEmpty() ? "Suchen..." : "Suche: " + v.get("query", ""))
                .tooltip("Öffnet ein Eingabefeld")
                .onClick(c -> c.prompt(Component.text("Obst suchen"), c.get("query", ""), text -> {
                    c.set("query", text.trim());
                    c.set("glyphwork:page:fruits", 0);
                })))
            .add(ListView.of("fruits", 8, 42, 80, 4, v -> {
                    String q = v.get("query", "").toLowerCase();
                    return FRUITS.stream().filter(f -> f.toLowerCase().contains(q)).toList();
                })
                .selected(v -> v.get("fruit", (String) null))
                .color(f -> TextColor.color(0xFFFFFF))
                .onSelect((c, f) -> { c.set("fruit", f); c.reopen(); }))
            .add(Grid.of("numbers", 96, 42, 4, 3, v -> NUMBERS)
                .label(String::valueOf)
                .tint(n -> n % 3 == 0 ? BLUE : n % 3 == 1 ? GREEN : null)
                .tooltip(n -> Component.text("Nummer " + n))
                .onClick((c, n) -> { c.set("number", n); c.reopen(); }))
            .add(Label.of(8, 132, v -> "Obst: " + v.get("fruit", "-")).width(80))
            .add(Label.of(96, 132, v -> "Nummer: " + v.get("number", (Object) "-")).width(72))
            .add(Button.of(8, 146, 56, 16).text("< Zurück").onClick(c -> c.back()))
            .build();
    }
}
