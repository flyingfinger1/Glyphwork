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
  **Text is always drawn above frames.** Button, list and grid text has a drop shadow like in
  vanilla (not on grayed-out buttons); labels are flat unless `.shadow(true)`.
- Frame sizes are rounded down to whole tiles of the active skin (8 px for the built-in one).
  Two exceptions round up, so that their content lies inside: the background of a list (2 rows of
  18 px get a 40 px frame) and a panel covering the whole scene (`background()`).
- `.onOpen(c -> ...)` runs when the scene is opened, before it is drawn (set state there);
  `.onClose(c -> ...)` when the player closes the screen while it is shown (messages, sounds).
- `.refresh(Duration.ofSeconds(5))` on the builder redraws the open scene every 5 seconds (at least
  half a second), for content that changes by itself: players online, balances, countdowns. Clicks
  on the previous drawing still work; it stops when the player closes the scene (also with ESC),
  opens another one or an inventory. Every redraw sends the whole dialog, so keep it at a few seconds.

## Elements

| Element | Use |
|---|---|
| `Panel.of(x, y, w, h)` / `Panel.shape(x, y, "##.", "###")` | autotiled frame (`panel` by default, `.frame("slot")` etc.), optional `tint`, `tooltip`, `onClick` |
| `Label.of(x, y, text)` | one line of text; `.color`, `.width` (clips with "..."), `.align` / `.center(width)`, `.shadow(true)` |
| `Button.of(x, y, w, h)` | frame + centered text; `.text`, `.tint`, `.textColor`, `.tooltip`, `.enabled`, `.onClick` |
| `Hitbox.of(x, y, w, h)` | invisible clickable area (min. 8 x 8), e.g. over a label |
| `Icon.of(x, y, "diamond")` | 16 x 16 item icon (also `Material`); `.count`, `.tint`, `.tooltip`, `.onClick` |
| `ListView.of(id, x, y, w, rows, items)` | rows of text in a sunken frame; `.onSelect`, `.selected`, `.tooltip`, `.rowHeight`; page buttons appear below when needed (+20 px) |
| `Grid.of(id, x, y, cols, rows, items)` | cells with a short label; `.label`, `.tint`, `.selected`, `.tooltip`, `.onClick`, `.cell(pitch, size)`; page buttons below when needed |
| `Head.of(x, y, "Steve")` | a face painted from the skin, `.size(8/16/24/32)`, also `v -> name`; `.tooltip`, `.onClick`. `Grid` / `ListView`: `.headSize(16)` for their `head:` icons |
| `Image.of(x, y, "logo")` | a picture from `plugins/Glyphwork/images/` (also `v -> "ranks/" + rank(v)`); `.tint`, `.tooltip`, `.onClick` |
| `Bar.of(x, y, w)` | progress bar, 8 or 16 px high; `.value(v -> ...)`, `.max(...)`, `.color`, `.text`, `.tooltip`: fills pixel exact |
| `Slider.of(x, y, w, min, max, step)` | track + knob, 16 px high; `.value`, `.onChange((click, value) -> ...)`, `.tint`, `.tooltip(value -> ...)`: a click on the track picks the value under the cursor (no dragging) |
| `Checkbox.of(x, y)` | 16 x 16 box with a check mark and text; `.text`, `.checked`, `.onToggle((click, on) -> ...)` |
| `Choice.of(x, y, w, "a", "b", ...)` | button "&lt; option &gt;"; left half = previous, right half = next; `.selected`, `.text(id -> shown)`, `.onSelect((click, id) -> ...)` |

Slider, checkbox and choice redraw the scene after their handler (unless it opened, closed or
redrew something itself), so the handler only stores the new value.

Item icons: `Grid.icon(item -> "diamond")` (centered in the cell; `cell(26, 24)` keeps the slot
frame visible around it, `frame("none")` draws no frame), `Button.icon("emerald")` (left of the
text, best with height 24) and `ListView.icon(...)`. Icons use the textures the client already
has: flat items look like in the inventory, block items show one face (no 3D), and items drawn by
special renderers (heads, chests, beds, banners, shields, shulker boxes) have no icon. Like text,
icons are drawn above frames.

**Item tooltips:** `Icon.itemTooltip(true)` (also `Grid` / `ListView`) shows the vanilla tooltip of
the icon's item when hovering. For enchantments, names and lore pass the item itself:
`Icon.itemTooltip(v -> myItemStack)` / `Grid.itemTooltip(offer -> offer.stack())` (Paper's
`ItemStack` is a `HoverEventSource`). Item ids the client does not know are dropped, because the
client would reject the whole screen.

**Text blocks:** `Label.of(x, y, longText).width(160).wrap(true)` breaks at spaces; `.lineHeight(10)`;
`"
"` starts a new line.

**Counts:** `Icon.count(64)`, `Grid.count(item -> String.valueOf(item.amount()))` and
`ListView.count(...)` write a stack size at the bottom right of the icon, white with a shadow like in
the inventory. Any short text works; null, empty and "1" show nothing. Counts are drawn only with an
icon (not in `NO_ICONS`); the plain dialog writes them after the name ("Diamond ×64").

**Player heads:** `Icon.head(x, y, "Steve")` or `"head:<name or uuid>"` wherever an item id goes
(`Grid.icon(p -> "head:" + p.getUniqueId())`, `Button.icon(...)`, `ListView.icon(...)`). The
client draws heads as 8 x 8 px (no scaling) and only on its 9 px text grid. So that a head still sits
in the middle of its button, row or cell, the element moves with it (by up to 4 px; the log says which
`y` avoids that, see the table in [menus.md](menus.md#elements)). Use `rowHeight(18)` (or 9) for lists
and a cell pitch of 18 or 27 for grids with heads, so that every row fits the same way. Online players send their skin along,
others are looked up by the client. Heads are not part of the pack: they show in every display mode
and in the plain dialog.

If the server sends its own resource pack (`resource-pack=` in server.properties), Glyphwork reads
it on start (`icon-source-pack: auto`): textures it replaces are measured so icons line up, and the
pack's own items work as icons, e.g. `Icon.of(x, y, "myserver:ruby")` for
`assets/myserver/items/ruby.json`. Players see those only with the server pack loaded; for players
who declined it they are left out (a grid cell shows a short name instead). Like in the inventory,
the whole 16×16 texture is drawn at a fixed spot, so art that the pack draws off-center stays
off-center.

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
| `form(form, values -> ...)` | native dialog with several fields (text, checkbox, slider, choice) and OK / Cancel, see below |
| `get(key, fallback)` / `set(key, value)` | per-player state of this scene; survives `reopen` and `back` until the player quits |
| `page(listId)` | current page of a list/grid (state key `glyphwork:page:<id>`) |

Clicks from a screen that has been replaced in the meantime are ignored.

Every element can be shown per viewer: `.visible(v -> v.player().hasPermission("x"))`. Hidden
elements are not drawn, take no clicks and are left out of the plain dialog; list rows and grid
cells come from the items function, so filter those there.

`get` returns the stored value unchecked, typed after the fallback: `v.get("count", 0)` is an
`Integer`. Read a value with the type you stored (`v.get("number", (Object) "-")` when it may be
missing and you only print it). If a binding throws while rendering, only that element is hidden and
a warning is logged.

## Forms

Native input fields cannot sit inside the pixel GUI, and the client sends their values only with a
native button. `Click.form` therefore opens them as a dialog of their own, with OK and Cancel;
afterwards (also on Cancel or ESC) the current scene is shown again.

```java
.onClick(c -> c.form(Form.builder(Component.text("Settings"))
        .text("name", Component.text("Name"), name(c), 16, 1)          // key, label, initial, max length, lines
        .slider("range", Component.text("Range"), 2, 32, 1, range(c))  // min, max, step, initial
        .checkbox("particles", Component.text("Particles"), particles(c))
        .choice("mode", Component.text("Mode"), mode(c),
            Form.option("easy", Component.text("Easy")), Form.option("hard", Component.text("Hard")))
        .buttons(Component.text("Save"), null)                          // null keeps the default label
        .build(),
    values -> save(c, values.text("name"), (int) values.number("range"),
        values.checked("particles"), values.choice("mode"))))
```

Keys use `a-z`, `A-Z`, `0-9` and `_`. The values come from the client and are checked before the
handler sees them: texts are cut to their length, numbers clamped and put on the step, choices are one
of the option ids (otherwise the initial value). The example trader picks its amount with a slider.

## Display mode per player

Some players use resource packs that move item icons or replace the Minecraft font, which can tear
the pixel GUI. Each player has a display mode, stored with the player:

| `DisplayMode` | |
|---|---|
| `FULL` | pixel GUI with item icons (default) |
| `NO_ICONS` | pixel GUI, grid cells show a short name instead of the icon |
| `SIMPLE` | the plain native dialog (the same as without the pack) |

Players choose it with `/glyphwork display` (German: `/glyphwork darstellung`). To offer it in your own settings instead, use
`Glyphwork.get().displayMode(player)` / `displayMode(player, mode)` and `sendPack(player)`, and set
`players.commands: false` in `config.yml`: then `/glyphwork` is hidden from players (admins keep it).

## Limits

- Scenes may be up to `max-height` pixels high (`config.yml`, default 256).
- Text uses the Minecraft font without shadow, one style per label (color only).
- Players without the pack (declined, failed, still loading, or a client that ignores server packs)
  get a plain native dialog built from the same scene, if `fallback: true` (default): labels become
  text lines, buttons, hitboxes, list rows, grid cells and page buttons become dialog buttons.
  **Panels are left out** (they are decoration), so put actions that must work without the pack on
  a `Button`, `Hitbox`, `ListView` or `Grid`. Admins can preview it with `/glyphwork test fallback`;
  `Glyphwork.get().hasPack(player)` tells you whether the pack loaded.

The demo (`/glyphwork test`) is built with this API only:
[Demo.java](../src/main/java/de/flyingfinger/glyphwork/demo/Demo.java). A separate plugin that uses
the API the way yours would is in [example/](../example/): a trader (`/trader`) with
[Trader.java](../example/src/main/java/de/flyingfinger/glyphwork/example/Trader.java) as the
screens and [ExamplePlugin.java](../example/src/main/java/de/flyingfinger/glyphwork/example/ExamplePlugin.java)
as the command.
