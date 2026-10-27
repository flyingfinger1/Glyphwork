# Menus without code

Every `*.yml` file in `plugins/Glyphwork/menus/` is a menu; the file name is its id. Files whose
name starts with `_` hold [templates](#templates) instead. On first start Glyphwork puts examples
there, in the server's language (`language`, or `fallback-language` when it is `auto`): `example.yml`,
`example-rules.yml`, `example-warps.yml` with `_example-templates.yml` for templates and conditions,
`example-players.yml` for variables and dynamic lists, `example-forms.yml` for forms; German servers
get the same as `beispiel.yml`, `beispiel-regeln.yml`, `beispiel-warps.yml`, `_beispiel-vorlagen.yml`,
`beispiel-spieler.yml` and `beispiel-formular.yml`. After editing,
`/glyphwork reload` applies the changes; problems are shown in chat and the console with the exact
place (`warps.yml.elements[2].x: number expected, got 'abc'`), and broken files are skipped.

Prefer the mouse? The [menu designer](menu-designer.md) builds these files in the browser.

Open a menu with its own command (see `command`) or `/glyphwork menu <id> [player]` (admins).

Positions are GUI pixels from the top-left corner of the menu. Frames snap to the skin's tiles
(8 px for the built-in skin), so sizes like 16, 24, 72 work best. Design tip: the
[skin editor](https://flyingfinger1.github.io/Glyphwork/)'s *Demo scene* shows what frames look like.

## Menu settings

```yaml
title: "<gold>Warps"          # dialog title, MiniMessage
width: 176                    # default 176
height: 96                    # default 96, at most max-height (config.yml)
background: panel             # frame drawn over the whole menu, or "none"
command: warps                # optional: /warps opens it (registered at runtime, also on reload)
aliases: [w]                  # optional
permission: myserver.warps    # optional: who may open it (command and "open:" actions)
description: "Warp menu"      # optional: shown in /help
exit-label: "Close"           # optional: label of the dialog's exit button
templates: {...}              # optional: templates only for this menu, see below
variables: { tab: shop }      # optional: menu variables and their defaults, see below
refresh: 5                    # optional: redraw every 5 seconds while open (at least 1), see below
persist: [nick]               # optional: variables kept in the player's data (true = all), see Variables
forms: {...}                  # optional: forms with several input fields, see below
open-on: {...}                # optional: open by itself on joining or with a menu item, see below
on-open: [...]                # optional: actions when the menu opens (before it is drawn)
on-close: [...]               # optional: actions when the player closes it
elements: [...]
```

**Opening by itself:**

```yaml
open-on:
  join: true            # open when a player joins (first = only on their very first join)
  item:                 # a menu item in the hotbar: right-click opens the menu
    material: compass
    name: "<gold>Server menu"
    lore: ["<gray>Right-click to open"]
    slot: 8             # hotbar slot it is put into (0-8, default 8)
    give: join          # join (default) = on every join if missing, first = only on the first join,
                        # no = only with /glyphwork item or the action give-item
    keep: true          # true (default) = cannot be dropped, comes back after death; false = a normal item
on-open: ["sound: block.note_block.chime"]
on-close: ["message: <gray>See you!"]
```

The join menu waits until the player's Glyphwork pack is loaded (or declined): the pack reload
would close a menu opened earlier. If several menus open on joining, the first by file name wins.
The menu item carries the menu id, so only it opens the menu (not every compass); `item: compass`
is short for a plain compass in slot 8 with the defaults. Players who lack the menu's `permission`
never get it. With `give: join` it is handed out on every join and after `/glyphwork reload` if
missing; with `keep: true` it cannot be dropped and comes back after death (it can still be moved in
the inventory). Not everyone wants it: `give: first` + `keep: false` gives newcomers one they may
throw away, and a button with `give-item: hub` (or `/glyphwork item hub [player]`) hands it out
again on request. `on-open` runs before the menu is drawn (so `set:` shows at once), whenever it
is opened (command, `open:`, trigger), not on redraws or `back`. `on-close` runs when the player
closes the menu (exit button, ESC, `close`); the screen is gone by then, so use it for messages,
sounds or commands.

**`refresh`** redraws the open menu every few seconds, so placeholders and dynamic lists stay
current (players online, balances, a countdown). Clicks on the previous drawing still work. It stops
when the player closes the menu (also with ESC), opens another one or an inventory. Every redraw
sends the whole menu again, so a few seconds are better than one.

## Elements

Drawn in order (later ones on top). Text is always drawn above frames. Like in vanilla, the text
of buttons, lists and grids has a drop shadow (grayed-out buttons do not); labels have none unless
`shadow: true`, like inventory titles.

| type | settings |
|---|---|
| `label` | `x`, `y`, `text`, `color` (`"#RRGGBB"` or a name like `gold`, default dark gray), `width` (clips with "..."), `align` (`left`/`center`/`right`, needs `width`), `shadow` (`true`: drop shadow, for light text on dark ground), `wrap` (`true`: break into lines at spaces, needs `width`), `line-height` (10) |
| `button` | `x`, `y`, `width` (64), `height` (16), `text`, `tint` (button color), `text-color`, `tooltip`, `actions`, `permission` (otherwise grayed out), `frame`, `icon` (item at the left; use height 24) |
| `panel` | `x`, `y`, `width`, `height` **or** `shape: ["###.", "#..."]` (tiles), `frame` (`panel`, `slot`, `button` or a frame of your skin), `tint`, `tooltip`, `actions` |
| `hitbox` | invisible clickable area: `x`, `y`, `width`, `height`, `tooltip`, `actions` |
| `icon` | 16 x 16 item icon: `x`, `y`, `item` (e.g. `diamond`), `count`, `item-tooltip`, `tint`, `tooltip`, `actions` |
| `head` | a player's face painted from the skin: `x`, `y`, `player` (name, UUID or placeholder, default `%player%`), `size` (8, 16, 24 or 32; default 32), `tooltip`, `actions` |
| `image` | one of your pictures: `x`, `y`, `image` (name in `images/` without `.png`, placeholders allowed), `tint`, `tooltip`, `actions` |
| `bar` | progress bar: `x`, `y`, `width` (100), `height` (8 or 16), `value`, `max` (100; both numbers or placeholders like `%player_level%`), `color` (fill), `text` (in the middle), `tooltip`, `frame` (`slot`) |
| `list` | `x`, `y`, `width` (120), `rows` (4), `row-height` (16), `id`, `items` (`text`, `color`, `tooltip`, `actions`, `permission`, `icon`, `count`), `items-from` + `item` ([dynamic lists](#dynamic-lists)), `item-tooltip`, `head-size`; page buttons appear below when needed (+20 px) |
| `grid` | `x`, `y`, `cols` (4), `rows` (2), `cell` (pitch, 18), `size` (16), `frame` (`slot`, or `none`), `id`, `items` (`text`, `color`, `tint`, `tooltip`, `actions`, `permission`, `icon`, `count`), `items-from` + `item`, `item-tooltip`, `head-size`; page buttons below when needed |
| `slider` | `x`, `y`, `width` (100), `var`, `min` (0), `max` (10), `step` (1), `tint` (knob), `tooltip` (`%value%` = the value at that spot), `actions` (after a change) |
| `checkbox` | `x`, `y`, `var` (`true`/`false`), `text`, `color`, `tooltip`, `actions` |
| `choice` | `x`, `y`, `width` (80), `height` (16), `var`, `options` (list, or `value: text` pairs), `tint`, `tooltip`, `actions` |
| `tabs` | tabs on top of a panel: `x`, `y`, `width` (160), `height` (100, tabs included), `tabs` (list of `id`, `title`, `icon`, `elements`), `var` (keeps the active tab in a [menu variable](#variables)), `id`, `frame` (`panel`, for the panel and the tabs), `tint` (inactive tabs, default a bit darker) |

**Item icons** (`icon:`) are 16 x 16 and use the textures every client already has: items look
like in the inventory, blocks show one face, and items drawn by special renderers (heads, chests,
beds, banners, shields, shulker boxes) have no icon. For a shop-like grid use `cell: 26`, `size: 24`.
Items of your server's resource pack work too (`icon: "myserver:ruby"`), see `icon-source-pack` in
`config.yml`.

**Big heads:** `type: head` (and `head-size: 16`, `24` or `32` on lists and grids, for their
`head:` icons) paints a player's face pixel by pixel in the colors of the skin: no pack change, so
it works for every player, also ones who just joined, and at any position (no 9 px grid). The
server fetches the skin in the background (online players carry it, other names are looked up at
Mojang) and keeps the faces in memory; until a face is there, the small client head stands in, and
the open menu is drawn again when it arrives. A big head is 64 colored glyphs, so a list with many
of them makes the dialog bigger; that is fine for a few dozen.

**Pictures:** put PNG files into `plugins/Glyphwork/images/` (folders work: `images/ranks/gold.png`
is `image: ranks/gold`; names use a-z, 0-9, `_`, `-`, `.`), then `/glyphwork reload`. They are
drawn at their own size, pixel exact, above frames like text, and become part of the pack (players
download them once). Any size works: Glyphwork cuts pictures into tiles of at most 128 x 128 px,
because the client cannot load bigger glyphs. Transparent parts stay transparent; `tint` multiplies
the colors. A big player head, a logo, a banner or a rank badge are all just pictures. The plain
dialog leaves pictures out (a clickable one becomes a button named by its tooltip).

**Item tooltips:** `item-tooltip: true` (on an `icon`, or on a `list` / `grid` for all its entries)
shows the item's own tooltip when hovering, like in the inventory, instead of the text tooltip.
Vanilla items only; with the API a plugin can pass a full item with enchantments and lore.

**Text blocks:** a label with `width` and `wrap: true` breaks its text into lines at spaces; `
`
in the text (or a YAML block with `|`) always starts a new line, also without `wrap`. Lines are
`line-height` (10) px apart.

**Bars** fill pixel exact from the left (`value` / `max`, clamped), in `color` (default green),
on the skin's `slot` frame. `height: 8` fills 6 px, `height: 16` fills 14 px; the width is rounded
down to whole tiles like other frames. Numbers may come from placeholders (`1,234.5` works too); a
value that is no number counts as 0. The `text` of an 8 px bar is drawn above it (like the
experience level in vanilla; keep 9 px free there), in a 16 px bar inside. The plain dialog shows a
bar as ten blocks plus its text.

**Counts:** `count: 64` writes a number at the bottom right of the icon, white with a shadow like in
the inventory. Any short text works, also placeholders (`count: "%item_players%"`); `1` and empty
show nothing, like in the inventory. The count is drawn only together with an icon (not in the
display mode "no icons"); the plain dialog writes it after the name ("Diamond ×64").

**Player heads:** `icon: "head:Steve"` (name or UUID; `%player%` and `%item_head%` work too). The
client draws heads as 8 x 8 px, they cannot be scaled, and only on its 9 px text grid. So that a head
still sits exactly in the middle of its button, row or cell, Glyphwork moves that element (frame,
head and text together) by up to 4 px and logs which `y` avoids the move. Positions where nothing
moves:

| element with a head | `y` that needs no move |
|---|---|
| `icon`, button 16 px high, grid cell `size: 16` | 4, 13, 22, 31, 40, 49, 58, 67, 76, 85 ... (`y` = 9n + 4) |
| button 24 px high, grid cell `size: 24` | 0, 9, 18, 27, 36, 45, 54, 63 ... (9n) |
| list with `row-height: 18` | 3, 12, 21, 30, 39, 48, 57, 66 ... (9n + 3) |
| list with `row-height: 9` (compact, like the tab list) | 8, 17, 26, 35, 44, 53 ... (9n + 8) |

The menu background and list frames are rounded up to whole tiles (8 px), so a 134 px high menu
gets a 136 px background and a list with 2 rows of 18 px a 40 px frame; other frames are rounded
down. Button sizes count as drawn: frames are whole tiles, so a 20 px button is drawn (and centered) as
16 px. Lists need a row height of 9 or 18 and grids a cell pitch of 18 or 27, otherwise later rows sit
differently from the first (Glyphwork logs a hint). Lists with `items-from: online-players` use 18 px
rows by default. Heads show in every display mode and in the plain dialog (in front of the button
text).

**Tabs:** the active tab and the panel are drawn as one frame, without an edge between them; the
other tabs sit 2 px lower behind it, a bit darker. Only the elements of the active tab are drawn
and clickable; a click on another tab switches to it and draws the menu again. Tabs cover whole
tiles (8 px), so their widths are rounded. Positions of elements inside a tab are relative to
the panel below the 16 px tabs, so `x: 4, y: 4` is the top left corner of the panel. Every
element type works inside a tab, also templates, `show-if` and cooldowns. Without `var` each menu
opens on the first tab; with `var: page` the active tab is a menu variable (so `persist`, `show-if:
"%menu_page% == shop"` and `set: page shop` work with it). Tabs share the panel width evenly; a tab
`icon` stands left of its title, which is written in the skin's text color. The plain dialog lists the tabs as buttons (▶ marks the active one)
followed by the elements of the active tab.

```yaml
- type: tabs
  x: 8
  y: 20
  width: 160
  height: 96
  var: page
  tabs:
    - id: shop
      title: Shop
      icon: emerald
      elements:
        - { type: label, x: 6, y: 6, text: "Today's offers" }
        - { type: button, x: 6, y: 20, width: 80, text: Buy, actions: ["message: Thanks!"] }
    - id: info
      title: Info
      elements:
        - { type: label, x: 6, y: 6, width: 144, wrap: true, text: "Open daily from 8 to 20." }
```

**Slider, checkbox, choice** write a [menu variable](#variables) (`var`) and are drawn from the
skin, so they sit anywhere in the menu like buttons. Native dialog fields cannot be placed inside the
pixel GUI; these are their counterparts. The server cannot follow the mouse, so a slider has no
dragging: a click on its track moves the knob there, and the tooltip shows the value at that spot
before you click. A checkbox (16 x 16 box, text to its right) switches between `true` and `false`; a
choice button steps through its options, back with a click on its left half, forward on the right
half. After a change the variable is set, the element's `actions` run, and the menu is redrawn.
Text fields need the keyboard, so they stay in [forms](#forms) and `input:`.

**Cooldowns:** `cooldown: 1h` on a button, hitbox, icon, panel, picture or head lets each player
use it once per hour (`30s`, `5m`, `2h`, `1d`, `1d 12h`; a plain number is seconds). A button on
cooldown is grayed out and its tooltip says when it is back; other elements answer a click with a
chat message. While a cooldown runs, the open menu redraws by itself: every second in the last
minute (the tooltip counts down), at each new minute before, and when it runs out (the button comes
back). The time is kept in the player's data, so logging out does not reset it.
`cooldown-key: daily` lets several elements (also in different menus) share one cooldown; without
it every element has its own. If a `require` action stops the click, the cooldown is not used up.
The texts come from the language files (`cooldown.tooltip`, `cooldown.wait`, with `<time>`); one
element can have its own: `cooldown-tooltip` (buttons) and `cooldown-message`, MiniMessage with
placeholders and `%cooldown%` for the time left.

`y` of a text is the top of the letters; in a 16 px button the text starts 4 px below the top.
Items with a `permission` are only shown to players who have it. Every element and every list or
grid item can have `show-if`; buttons also `enabled-if` and `disabled-tooltip`, see
[Conditions](#conditions).

## Actions

A list of strings, run in order:

| action | effect |
|---|---|
| `player: spawn` | the player runs `/spawn` |
| `console: give %player% bread` | the console runs it |
| `message: <green>Done!` | chat message to the player ([MiniMessage](https://docs.advntr.dev/minimessage/format.html)) |
| `sound: entity.experience_orb.pickup 1 1.2` | sound with optional volume and pitch |
| `open: shop` | opens menu `shop`; `back` returns |
| `back` / `close` / `refresh` | previous menu / close / redraw |
| `input: Name? \| player: msg %input% hi` | asks for a text, then runs the action after `\|` with `%input%` |
| `require: %vault_eco_balance% >= 100 \| message: <red>Too poor` | checks a [condition](#conditions); if it does not hold, runs the action after `\|` (optional) and **skips the remaining actions** |
| `set: tab shop` | sets the [menu variable](#variables) `tab` to `shop` (placeholders allowed: `set: filter %input%`); `set: tab` resets it to its default |
| `toggle: sound` | switches the variable between `true` and `false` |
| `add: points 1` | adds a number to the variable (negative subtracts, decimals work, a missing or non-number value counts as 0); optional limits: `add: amount -1 min 1`, `add: points %menu_step% max 100` |
| `form: settings` | opens the [form](#forms) `settings` of this menu |
| `give-item: hub` | gives the player the menu item of menu `hub` (`open-on.item`), unless they carry it already |

After `set` or `toggle` the menu is drawn again by itself, unless an action already opened, closed,
or redrew it. `open:` takes placeholders too (`open: shop-%menu_tab%`).

`%input%` is cleaned (one line, no leading `/`, no `%`, at most 100 characters), but it is still
text typed by the player: do not put it into `console:` commands that could do harm.

## Placeholders

In texts, tooltips, titles and actions: `%player%`, `%world%`, `%online%`, `%time%` (server clock,
HH:MM:SS), and every
[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) placeholder if that plugin is
installed. Values are filled in each time the menu is drawn; use `refresh` to update after an
action.

## Conditions

A condition compares two values: `<left> <operator> <right>`, with spaces around the operator.

```yaml
- type: button
  text: "VIP-Lounge"
  show-if: "%world% == lobby"                 # hidden otherwise
  enabled-if: "%vault_eco_balance% >= 100"    # grayed out otherwise
  disabled-tooltip: "<red>Costs 100 coins"    # tooltip while grayed out
  actions:
    - "require: %vault_eco_balance% >= 100 | message: <red>Too poor"
    - "console: eco take %player% 100"
    - "player: warp vip"
```

| operator | holds when |
|---|---|
| `==`, `!=` | equal / not equal (numbers as numbers, text ignoring case) |
| `>`, `>=`, `<`, `<=` | both sides are numbers and compare so (`1,234.5` counts as a number) |
| `contains`, `!contains` | the left text contains / does not contain the right one (ignoring case) |

- Both sides may contain placeholders; they are filled in for the viewer each time the menu is drawn
  (`show-if`, `enabled-if`) or clicked (`require`). Quotes around a side are removed
  (`"%player%" != ""`).
- A **list** of conditions holds when all of them hold: `show-if: ["%world% == lobby", "%online% >= 2"]`.
- `show-if` works on every element and on list rows and grid cells (hidden rows close up).
  `enabled-if` and `disabled-tooltip` are for buttons, together with `permission`.
- `show-if`/`enabled-if` only change what the player sees. When an action costs something, check
  again with `require`: values can change while the menu is open.

## Variables

A menu can keep values per player: which tab is open, a search text, an on/off switch.

```yaml
variables:            # defaults
  tab: shop
  filter: ""
elements:
  - { type: button, x: 8, y: 20, text: "Shop", actions: ["set: tab shop"] }
  - { type: button, x: 92, y: 20, text: "Info", actions: ["set: tab info"] }
  - { type: label, x: 8, y: 44, text: "Angebote", show-if: "%menu_tab% == shop" }
  - { type: button, x: 8, y: 60, text: "Suche: %menu_filter%", actions: ["input: Name? | set: filter %input%"] }
```

- `%menu_<name>%` works in every text, tooltip, condition and action; a variable without a value
  and without a default is empty.
- Values are kept per player and menu until the player leaves (also when the menu is closed and
  opened again).
- To highlight the active tab, add the button twice with opposite `show-if` and different `tint`
  (see `example-players.yml`).

**Saved variables:** normally variables keep their value until the player logs out. `persist: true`
keeps all variables of the menu in the player's data (they survive logouts and restarts), or list
some: `persist: [nick, range]`. `set: nick` without a value goes back to the default and removes
the saved value. Forms, sliders, checkboxes and choices save like `set:`. The data stays on the
server the player plays on (no database, no sync between servers of a network).

## Dynamic lists

`items-from` fills a list or grid from the server each time the menu is drawn; `item` describes
every entry, with the entry's values as `%item...%` placeholders. Fixed `items` come first.

```yaml
- type: list
  x: 8
  y: 40
  width: 160
  items-from: online-players
  item:
    text: "%item%"                                   # default: %item%
    tooltip: "World: %item_world%"
    show-if: ["%item% != %player%", "%item% contains %menu_filter%"]
    actions: ["player: tpa %item%"]
```

| `items-from` | values |
|---|---|
| `online-players` | `%item%` / `%item_name%` (name), `%item_world%`, `%item_uuid%`, `%item_head%` / `%item_icon%` (the head, `head:<uuid>`) (sorted by name) |
| `worlds` | `%item%` / `%item_name%`, `%item_players%` (count), `%item_environment%` (`normal`, `nether`, `the_end`), `%item_icon%` (grass_block, netherrack, end_stone) |

`item` takes the settings of `items` entries (`text`, `color`, `tint`, `tooltip`, `actions`,
`permission`, `icon`, `show-if`). A misspelled `%item_...%` is reported when the menu is loaded.
`icon: "%item_head%"` puts each player's head in front of the name.

## Forms

A form is a native dialog with several fields and OK / Cancel. Dialogs cannot put native fields
into the pixel GUI, and the client sends their values only with a native button, so a form opens as
a dialog of its own; afterwards the menu is shown again. Every field belongs to a
[menu variable](#variables): it starts with the variable's value, and OK writes the new value back.
Then the form's `actions` run, and the menu is redrawn with the new values.

```yaml
variables: { nick: "%player%", range: "8", particles: "true", mode: normal }
forms:
  profile:
    title: "Edit profile"          # MiniMessage, placeholders allowed
    ok: "Save"                     # optional, also cancel: "..."
    width: 200                     # optional: width of the fields
    fields:
      - { type: text, var: nick, label: "Nickname", max-length: 16 }
      - { type: text, var: motto, label: "Motto", max-length: 80, lines: 3 }
      - { type: slider, var: range, label: "Range", min: 2, max: 32, step: 1 }
      - { type: checkbox, var: particles, label: "Particles" }
      - { type: choice, var: mode, label: "Mode", options: { easy: "<green>Easy", normal: Normal } }
    actions: ["message: <green>Saved, %menu_nick%!"]
elements:
  - { type: button, x: 8, y: 76, width: 96, text: "Edit", actions: ["form: profile"] }
```

| field | settings | value in the variable |
|---|---|---|
| `text` | `max-length` (1 to 100, default 100), `lines` (1 to 10; more than 1 = text box) | the text, cleaned like `%input%` |
| `slider` | `min` (0), `max` (10), `step` (1) | the number (`12`, or `2.5` with steps of 0.5) |
| `checkbox` | | `true` or `false` |
| `choice` | `options`: a list (`[easy, normal]`) or `value: text` pairs | the chosen value |

Values come from the client and are checked: texts are cut and cleaned, numbers stay in range,
choices must be one of the options. Cancel and ESC change nothing. `form:` works in buttons, list
and grid items (with `%item%` in the form's texts and actions), but not inside a form's own actions.
For a single text, `input:` is shorter.

## Templates

A template is a reusable element, or a group of elements, with `{values}` filled in where it is
used. Put templates into a file whose name starts with `_` (for all menus) or under `templates:` in
a menu (only there; it replaces a shared template of the same name).

```yaml
# menus/_vorlagen.yml
warp:
  type: button
  width: 160
  height: 24
  icon: "{item}"
  text: "{name}"
  tint: "{farbe|gray}"          # {key|default}
  actions: ["player: warp {ziel}", "close"]

eintrag:                        # a group: positions relative to the use entry
  - { type: icon, x: 0, y: 4, item: "{item}" }
  - { type: label, x: 20, y: 8, text: "{name}" }
```

```yaml
# menus/warps.yml
elements:
  - { use: warp, x: 8, y: 20, name: Spawn, ziel: spawn, item: compass }
  - { use: warp, x: 8, y: 48, name: Nether, ziel: nether, item: netherrack, farbe: red,
      enabled-if: "%world% != world_nether" }
  - { use: eintrag, x: 8, y: 80, item: wheat, name: Farm }
```

- `x`/`y` of the `use` entry move the template; its own positions are relative (default 0).
- Every other key is a value for `{key}`. A key the template does not use as a value replaces that
  setting of a one-element template (above: `enabled-if` on the Nether button); groups only take
  values.
- `show-if` on the `use` entry applies to every element of the template.
- Templates may use other templates. A `{key}` without a value and without a default is an error
  that names the menu, the element and the template.

## Without the pack

Players without the resource pack get a plain dialog: labels as text, buttons, list rows and grid
cells as dialog buttons. Panels are decoration and are left out, so put every action on a button,
hitbox, list or grid.
