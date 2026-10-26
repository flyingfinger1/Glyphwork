# PaperDialogUI — Font-basierte Klick-GUIs über das Dialog-System (Kickoff-Spec)

> **Arbeitsname:** `PaperDialogUI` (frei umbenennbar). Generisches, standalone Paper-Plugin —
> **keine** Abhängigkeit zu MixelPixel/Core. Soll als wiederverwendbare Bibliothek + Plugin
> für beliebige Server taugen.

---

## 0. Kickoff-Prompt für die neue Code-Session

> Kopiervorlage — das reicht als Einstieg in der getrennten Session:

```
Wir bauen ein standalone Paper-Plugin (Zielversion Minecraft 26.3, Paper, Java 21+/25),
das HugoSMP-artige, voll klickbare GUIs INNERHALB des vanilla Dialog-Systems rendert –
ohne Client-Mod, nur mit einem server-generierten Resource-Pack (Bitmap-Fonts +
Negative-Space + Ascent-Varianten). Kein Inventar-GUI, sondern das Dialog-API.

Lies SPEC.md komplett. Baue strikt nach Meilensteinen, M0 zuerst: EIN Panel + EIN
klickbarer Button, um die drei harten Primitive zu beweisen (Negative-Space-Layout,
Ascent-Shift-Vertikale, Klick-bindet-an-Zeile). Erst wenn M0 im vanilla Client mit Pack
funktioniert, weitermachen. Verifiziere alle Paper-Dialog-API-Signaturen gegen die echte
26.3-Javadoc (jd.papermc.io), nicht aus dem Gedächtnis.
```

---

## 1. Ziel & Scope

Ein Plugin, mit dem Entwickler **reichhaltige, klickbare Bildschirm-GUIs** bauen können
(Panels, Buttons, Icons, Listen, „JEI"-artige Raster), die auf einem **vanilla Client**
laufen — die einzige Voraussetzung ist ein **Resource-Pack**, das das Plugin selbst
generiert und ausliefert.

**Technik-Kern (bestätigt, Quelle: HugoSMP-Autor):**
- Die GUI ist **ein einziges großes `plain_message`** im Dialog.
- Keine Shader, keine neue API, **nur Fonts**: Bitmap-Glyphen + Negative-Space + mehrere
  Ascent-Varianten derselben Textur.
- Klick/Hover sitzen als Events auf den Glyph-Zeichen im Text.

### In Scope
- Resource-Pack-**Generator** (Fonts, Ascent-Matrix, Autotile-Sheet, optional Item-Atlas).
- **Runtime**: Pack bauen → ausliefern (embedded HTTP) → Dialog rendern → Klicks routen.
- **Public API**: Szene-Graph (Panels/Buttons/Labels/Icons/Listen) → Plugin rendert daraus
  das `plain_message` + Click-Handler.

### Out of Scope (bewusst)
- Keine Client-Mod, keine Shader.
- Kein Inventar-Container-Menü (das ist ein ganz anderer Mechanismus).
- Kein Text-Reflow/Word-Wrap-Engine in v1 (feste Raster-Zeilen).

---

## 2. Was es NICHT ist

- **Kein** „Website → Command"-Bridge (anderes Thema).
- **Kein** Bedrock-Support (Form-API ist dort komplett anders).
- Funktioniert nur, solange der Spieler das Pack akzeptiert (Fallback: Klartext-Dialog).

---

## 3. Glossar / Kernkonzepte

| Begriff | Bedeutung |
|---|---|
| **Dialog** | Vanilla-Bildschirm seit 1.21.6; hier als Träger genutzt (`plain_message`-Body). |
| **plain_message** | Body-Element des Dialogs = eine Text-Component. Die **ganze GUI** steckt hier drin. |
| **Zeile** | Eine Textzeile = eine Rasterzeile. Zeilenhöhe = Vielfaches von **9px**. |
| **Negative-Space-Glyph** | `space`-Font-Provider mappt Codepoints auf (auch negative) Pixel-Advances → Cursor horizontal bewegen. |
| **Ascent-Variante** | Dieselbe Textur als eigene Font mit anderem `ascent` → **pseudo-vertikale** Verschiebung (sub-9px). |
| **Autotile (48-Blob)** | Kachel-Sheet; aus den 8 Nachbarn wird per Bitmask die passende Kachel (Rand/Ecke/Füllung) gewählt → nahtlose Panels. |
| **Scene** | Logischer Bildschirm (Panels, Buttons, …), vom Entwickler definiert. |
| **Renderer** | Wandelt eine Scene in genau eine `Component` (die plain_message) + eine Klick-Map. |

---

## 4. Die drei harten Primitive (hier liegt das ganze Risiko)

### P1 — Horizontales Layout via Negative-Space
Der `space`-Font-Provider (seit 1.20) mappt Codepoints auf Pixel-Advances, **auch negativ**.
Damit bewegt man den Cursor frei nach links/rechts, um Layer auf derselben Zeile zu
überlagern. X-Offsets = Leerzeichen / Space-Glyphen.

```json
// in der Font-Definition (providers[])
{ "type": "space", "advances": { "": 8, "": -8, "": 1, "": -1 } }
```

### P2 — Vertikale Verschiebung via Ascent-Varianten
Text kann sich im Dialog **nicht** vertikal bewegen. Lösung: dieselbe Textur **mehrfach**
als Bitmap-Provider registrieren, jeweils mit anderem `ascent`. Jede Variante = ein
vertikaler Versatz. Alle Varianten werden beim **Startup generiert** und ins Pack gezippt.

```json
{ "type": "bitmap", "file": "ns:tile/panel_0.png", "ascent": 7, "height": 8, "chars": [""] },
{ "type": "bitmap", "file": "ns:tile/panel_0.png", "ascent": 6, "height": 8, "chars": [""] },
{ "type": "bitmap", "file": "ns:tile/panel_0.png", "ascent": 5, "height": 8, "chars": [""] }
// gleiche PNG, 3 Ascents → 3 vertikale Positionen desselben Tiles
```
→ Eine **Ascent-Matrix** (Tile × Shift) wird programmatisch erzeugt. Ohne Generator
unwartbar.

### P3 — Klick bindet an die ZEILE, nicht an die gezeichnete Position ⚠️
Das Click-Event hängt an der Text-Component (dem Glyph). Die **Klickfläche ist die
Bounding-Box der Zeile**, nicht der optische Ort nach Negative-Space-Verschiebung.

Konsequenzen:
- Klickbares muss in der **richtigen Zeile** liegen.
- Die übrigen Zeilen mit **unsichtbaren, gestylten Space-Glyphen mit eigenem (No-op- oder
  eigenem) Event** füllen, sonst „durchklickt" es auf die falsche Zeile.
- Hover-Tooltips analog über HoverEvent auf der Zeilen-Component.

> **Das ist die Stelle, die beim Debuggen die meisten Nerven kostet.** M0 existiert genau,
> um P1–P3 isoliert zu beweisen, bevor Content gebaut wird.

---

## 5. Architektur

```
PaperDialogUI (JavaPlugin)
├─ api/                      ← öffentliche API (eigenes Artefakt, für andere Plugins)
│   ├─ Scene, Panel, Button, Label, Icon, ListView, Grid
│   ├─ ClickHandler, HoverProvider
│   └─ DialogUI (Facade: open(player, scene), close(player))
│
├─ gen/  (Resource-Pack-Generator, läuft onEnable oder per CLI)
│   ├─ FontWriter        → *.json Font-Provider (bitmap/space)
│   ├─ AscentMatrix      → N Ascent-Varianten pro Tile (P2)
│   ├─ SpaceTable        → Negative-Space-Advances (P1)
│   ├─ AutotileBuilder   → 48-Blob-Sheet → Nachbar-Bitmask → Char-Lookup
│   ├─ ItemAtlas (Phase 2, optional) → Item-Render → PNG → Glyph
│   └─ PackZipper        → pack.mcmeta + assets/ → <hash>.zip
│
├─ render/
│   ├─ SceneRenderer     → Scene  →  Component (die eine plain_message) + ClickMap
│   ├─ LineBuffer        → Zeilen-Grid, legt Layer per Negative-Space übereinander
│   └─ GlyphCatalog      → logischer Name → Codepoint (aus gen/)
│
├─ serve/
│   ├─ PackServer        → embedded HTTP (Javalin/NanoHTTPD/com.sun) liefert <hash>.zip
│   └─ PackSender        → setResourcePack(url, sha1, required?) beim Join/Open
│
├─ dialog/
│   ├─ DialogFactory     → baut Paper-Dialog mit plain_message-Body + Aktionen
│   └─ ClickRouter       → custom/callback-Click → registrierter ClickHandler
│
└─ DialogUIPlugin        → onEnable: gen → serve → register; /dialogui reload|dump
```

**Artefakt-Split:** `api` als eigenes, schlankes Modul (ohne Impl-Deps), damit andere
Plugins **gegen die API** kompilieren, aber nur das Haupt-Plugin die Generierung enthält.

---

## 6. Runtime-Fluss

1. **onEnable** → Generator baut das Pack (nur wenn Config/Inhalte sich geändert haben →
   Hash-Cache), schreibt `<sha1>.zip` in den Plugin-Ordner.
2. **PackServer** startet embedded HTTP (Port aus Config), liefert das Zip unter
   `/<sha1>.zip`. (Alternativ: externe URL / vorhandener Webspace via Config.)
3. **Join** (oder erst beim ersten `open`) → `player.setResourcePack(url, sha1, required)`.
   Config: `required: true|false` + Fallback-Verhalten.
4. **DialogUI.open(player, scene)** → `SceneRenderer` → eine `Component` + `ClickMap` →
   `DialogFactory` baut den Dialog → an den Spieler.
5. **Klick** → Paper feuert Custom/Callback-Click → `ClickRouter` findet den Handler → führt
   serverseitig aus (kann Scene neu rendern & Dialog re-`open`en → „Navigation").

---

## 7. Public API (Entwurf, so soll sich's anfühlen)

```java
Scene settings = Scene.builder("settings")
    .panel(Panel.window(180, 90))                     // Autotile-Rahmen
    .add(Label.of("Einstellungen").at(8, 6).bold())
    .add(Button.of("Sound: AN")
            .at(8, 24).size(80, 16)
            .onClick(ctx -> { toggleSound(ctx.player()); ctx.reopen(); })
            .tooltip("Klick zum Umschalten"))
    .add(Icon.item("minecraft:diamond").at(100, 24))  // Phase 2 (Item-Atlas)
    .add(Button.close("X").at(168, 4))
    .build();

DialogUI.get().open(player, settings);
```

Leitplanken:
- Koordinaten in **Pixeln**; der Renderer rastert auf 9px-Zeilen + Ascent-Shift.
- `ctx.reopen()` rendert dieselbe Scene neu (State-Wechsel) — das ist die „Refresh"-Lösung.
- **Suchfeld/Eingabe:** Dialoge können bei Texteingabe nicht live refreshen → Pattern:
  klickbarer Button öffnet einen kleinen **Input-Dialog** (echtes Dialog-Input-Feld),
  bei Bestätigung Haupt-Scene mit Query neu öffnen. (So macht's HugoSMP beim JEI.)

---

## 8. Tech-Stack & Abhängigkeiten

- **Paper 26.3**, Java 21+ (dev-Umgebung des Users: JDK 25, siehe Hinweis unten).
- **Paper Dialog API** (`io.papermc.paper.registry.data.dialog.*`) — **Signaturen gegen
  26.3-Javadoc verifizieren** (jd.papermc.io), nicht raten.
- **Adventure** (Component/ClickEvent/HoverEvent) — bei Paper dabei.
- Klick-Rückkanal: bevorzugt **Paper Dialog-Action-Callback** bzw. **`ClickEvent.callback`**
  (kein Command-Registrieren). Prüfen, was in 26.3 für Dialog-interne Text-Klicks trägt;
  ggf. `custom` Click-Event + Listener.
- Embedded HTTP: **NanoHTTPD** oder `com.sun.net.httpserver` (keine Extra-Dep). Javalin nur
  wenn eh gewünscht.
- Bild/PNG: `javax.imageio` reicht für Font-Texturen/Autotile. Item-Render (Phase 2) ist das
  einzige schwere Tooling — separat halten.
- Build: Gradle, `paperweight-userdev` oder reine Paper-API (für Dialoge reicht i. d. R. die
  API ohne NMS). Lizenz: **MIT** vorschlagen (bewusst keine GPL-Fremdteile reinziehen).

---

## 9. Meilensteine

- **M0 — Beweis der Primitive (zuerst!):** 1 Panel + 1 Button, hart codiert. P1/P2/P3 müssen
  im **vanilla Client mit Pack** sichtbar & klickbar sein. Kein Generator, kein API-Zucker.
  *Gate: Button reagiert exakt dort, wo er gezeichnet ist.*
- **M1 — Generator:** FontWriter + AscentMatrix + SpaceTable + PackZipper. Pack wird aus
  Config/Assets reproduzierbar gebaut (Hash-Cache). Autotile-Lookup.
- **M2 — Runtime & Serving:** PackServer (HTTP + sha-1) + PackSender (Join/Open) +
  Reload-Command. Fallback bei abgelehntem Pack.
- **M3 — Public API & Renderer:** Scene-Graph → SceneRenderer → ClickRouter. Panels,
  Buttons, Labels, Grid/List, Navigation via reopen, Input-Dialog-Pattern.
- **M4 — Item-Icons (optional):** Item-Atlas-Tooling (Render aus Client-Jar → PNG → Glyph).
  **Build-Time-Tool**, nicht Runtime. Darf warten.
- **M5 — Politur:** Tooltips/Hover, Theming (mehrere Autotile-Skins), Doku + Beispiel-Plugin,
  Release.

---

## 10. Gotchas / Risiken (vorab bekannt)

1. **Klick-an-Zeile (P3)** — Hauptrisiko. Immer erst mit M0 absichern.
2. **Eigene Font-Namespace Pflicht** — **niemals** in `minecraft:default` schreiben. Die vielen
   krummen Advances/Ascents verseuchen sonst `default` (u. a. §k-Obfuscate-Breiten brechen).
   Alles in z. B. `dialogui:ui`.
3. **Pack-Größe** — Ascent-Varianten × Tiles × (später) Item-Atlas wächst schnell. Nur
   benötigte Varianten generieren; Hash-Cache gegen Neu-Download.
4. **Pack-Ablehnung** — Spieler kann Pack ablehnen → GUI unbrauchbar. `required` + Klartext-
   Fallback-Dialog definieren.
5. **Dialog-API-Drift** — API ist jung (1.21.6+), Signaturen ändern sich zwischen Versionen.
   An **einer** Stelle kapseln (`DialogFactory`), damit Version-Bumps billig sind.
6. **Zeilenhöhe = Vielfache von 9** — Layout-Mathe konsequent darauf aufbauen; Extra-Zeilen
   sind leer (mit unsichtbaren Event-Spacern, siehe P3).
7. **JDK** — Gradle braucht `JAVA_HOME` auf JDK 25 (Default beim User ist JDK 21).

---

## 11. Offene Entscheidungen (in der neuen Session klären)

- [ ] **Projektname** final (PaperDialogUI / DialogForge / PanelKit / …).
- [ ] **Public/MIT Repo** unter `flyingfinger1` + GHCR nicht nötig (ist ein Plugin-Jar, kein
      Container) — aber CI-Release (GitHub-Release → Jar-Asset, evtl. Modrinth/Hangar)?
- [ ] **Autotile-Format**: eigenes 48-Blob-Sheet zeichnen vs. Platzhalter-Skin zum Start.
- [ ] **Item-Atlas** überhaupt in v1? (schwer; M4/optional).
- [ ] **Pack-Hosting**: nur embedded HTTP, oder auch „externe URL"-Modus?
- [ ] **Klick-Mechanismus** in 26.3 für Dialog-interne Text-Klicks final verifizieren.

---

## 12. Referenzen

- **Original-Rezept (HugoSMP-Autor, via Paper Discord):** negative-space Glyphen, ein großes
  `plain_message`, Autotile-48-Blob als Bitmap-Font, Ascent-pro-Shift-Fonts (Startup-generiert,
  gezippt), Item-Render aus Client-Jar als Glyph, embedded HTTP + sha-1 beim Join,
  Events auf Tile-Glyphen (binden an Zeile), Suche via kleinem Input-Dialog. *(Zusammenfassung in Anhang A.)*
- Paper Dialog API Javadoc: jd.papermc.io (Version 26.3 wählen).
- Minecraft Font-Provider (`bitmap`, `space`): Wiki „Font" (resource pack).

---

### Anhang A — Zusammenfassung des Original-Rezepts

Eigene Zusammenfassung (kein Zitat) einer öffentlichen Beschreibung des HugoSMP-Entwicklers
im Paper-Discord:

- Die ganze GUI ist ein einziges `plain_message`; keine Shader, keine neue API, nur Fonts.
- Kacheln: ein 48er-Blob-Autotile-Sheet als Bitmap-Font; eine kleine Raster-DSL wählt die Kachel
  anhand der 8 Nachbarn, damit Panels und Buttons nahtlos verbunden sind.
- Layout: jede Rasterzeile ist eine Textzeile (Höhe ein Vielfaches von 9, Extra-Zeilen leer);
  Ebenen werden per Negative-Space auf derselben Zeile überlagert, x-Versatz über Leerzeichen.
- Vertikal: pro Versatz eine eigene Font (gleiche Texturen, anderer Ascent) plus skalierte
  Kachel-Varianten, alles beim Start generiert und ins Pack gezippt.
- Item-Icons: Items werden aus dem Client-Jar in Atlas-PNGs gerendert und als Glyphen registriert.
- Pack: vom Plugin gezippt, über einen kleinen eingebetteten HTTP-Server ausgeliefert, beim Join
  mit SHA-1 gesendet.
- Klick/Hover: Events auf den Kachel-Glyphen; sie binden an die Textzeile, deshalb decken
  unsichtbare gestylte Space-Glyphen die übrigen Zeilen ab. *(In 26.3 nicht mehr so, siehe README.)*
- Suche: ein klickbares Feld öffnet einen kleinen Eingabe-Dialog, der die Haupt-GUI mit der Suche
  neu öffnet.
