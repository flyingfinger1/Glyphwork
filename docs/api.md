# Glyphwork API

Build clickable screens from code. They show up as a vanilla dialog; players only need the
resource pack, which Glyphwork generates and sends itself.

## Setup

- `plugin.yml`: `depend: [Glyphwork]`
- Compile against the `api` module (`glyphwork-api` jar, classes `de.flyingfinger.glyphwork.api.*`).
  The classes are shipped inside the Glyphwork plugin at runtime, so do not shade them.

## A screen

```java
Scene settings = Scene.builder("myplugin:settings", 176, 96)
    .title(Component.text("Einstellungen"))
    .background()                                   // "panel" frame over the whole scene
    .add(Label.of(8, 7, "Einstellungen"))
    .add(Button.of(8, 24, 80, 16)
        .text(v -> soundOn(v.player()) ? "Sound: AN" : "Sound: AUS")
        .tint(v -> soundOn(v.player()) ? GREEN : RED)
        .tooltip("Klick zum Umschalten")
        .onClick(c -> { toggleSound(c.player()); c.reopen(); }))
    .build();

Glyphwork.get().open(player, settings);
```

- Coordinates are GUI pixels from the scene's top-left corner; positions are fixed.
- Content can depend on the viewer: every `Function<View, ...>` is evaluated each time the scene is
  rendered. Build a scene once and reuse it.
- Elements are drawn in the order they are added; later ones are on top and win clicks.
  **Text is always drawn above frames.**
- Frame sizes are rounded down to whole tiles of the active skin (8 px for the built-in one).

## Elements

| Element | Use |
|---|---|
| `Panel.of(x, y, w, h)` / `Panel.shape(x, y, "##.", "###")` | autotiled frame (`panel` by default, `.frame("slot")` etc.), optional `tint`, `tooltip`, `onClick` |
| `Label.of(x, y, text)` | one line of text; `.color`, `.width` (clips with "..."), `.align` / `.center(width)` |
| `Button.of(x, y, w, h)` | frame + centered text; `.text`, `.tint`, `.textColor`, `.tooltip`, `.enabled`, `.onClick` |
| `Hitbox.of(x, y, w, h)` | invisible clickable area (min. 8 x 8), e.g. over a label |
| `ListView.of(id, x, y, w, rows, items)` | rows of text in a sunken frame; `.onSelect`, `.selected`, `.tooltip`, `.rowHeight`; page buttons appear below when needed (+20 px) |
| `Grid.of(id, x, y, cols, rows, items)` | cells with a short label; `.label`, `.tint`, `.selected`, `.tooltip`, `.onClick`, `.cell(pitch, size)`; page buttons below when needed |

`y` of a label is the top of the glyphs; capitals are 7 px high, so text centered in a 16 px
button starts 4 px below the button's top.

## Clicks and navigation

Handlers get a `Click` (runs on the main thread):

| Method | Effect |
|---|---|
| `reopen()` | render the current scene again (after changing state) |
| `open(scene)` | show another scene; `back()` returns |
| `back()` | previous scene, or close |
| `close()` | close the screen |
| `prompt(title, initial, text -> ...)` | small native dialog with a text field (dialogs cannot read typing live); afterwards the current scene is shown again |
| `get(key, fallback)` / `set(key, value)` | per-player state of this scene; survives `reopen` and `back` until the player quits |
| `page(listId)` | current page of a list/grid (state key `glyphwork:page:<id>`) |

Clicks from a screen that has been replaced in the meantime are ignored.

`get` returns the stored value unchecked, typed after the fallback: `v.get("count", 0)` is an
`Integer`. Read a value with the type you stored (`v.get("number", (Object) "-")` when it may be
missing and you only print it). If a binding throws while rendering, only that element is hidden and
a warning is logged.

## Limits

- Scenes may be up to `max-height` pixels high (`config.yml`, default 256).
- Text uses the Minecraft font without shadow, one style per label (color only).
- Item icons are not supported yet (planned: M4).
- Players without the pack (declined, failed, still loading, or a client that ignores server packs)
  get a plain native dialog built from the same scene, if `fallback: true` (default): labels become
  text lines, buttons, hitboxes, list rows, grid cells and page buttons become dialog buttons.
  **Panels are left out** (they are decoration), so put actions that must work without the pack on
  a `Button`, `Hitbox`, `ListView` or `Grid`. Admins can preview it with `/glyphwork test fallback`;
  `Glyphwork.get().hasPack(player)` tells you whether the pack loaded.

The demo (`/glyphwork test`) is built with this API only:
[Demo.java](../src/main/java/de/flyingfinger/glyphwork/demo/Demo.java).
