# Menus without code

Every `*.yml` file in `plugins/Glyphwork/menus/` is a menu; the file name is its id. Files whose
name starts with `_` hold [templates](#templates) instead. On first start Glyphwork puts examples
there (`beispiel.yml`, `beispiel-regeln.yml`, and `beispiel-warps.yml` with `_beispiel-vorlagen.yml`
for templates and conditions). After editing,
`/glyphwork reload` applies the changes; problems are shown in chat and the console with the exact
place (`warps.yml.elements[2].x: number expected, got 'abc'`), and broken files are skipped.

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
elements: [...]
```

## Elements

Drawn in order (later ones on top). Text is always drawn above frames.

| type | settings |
|---|---|
| `label` | `x`, `y`, `text`, `color` (`"#RRGGBB"` or a name like `gold`, default dark gray), `width` (clips with "..."), `align` (`left`/`center`/`right`, needs `width`) |
| `button` | `x`, `y`, `width` (64), `height` (16), `text`, `tint` (button color), `text-color`, `tooltip`, `actions`, `permission` (otherwise grayed out), `frame`, `icon` (item at the left; use height 24) |
| `panel` | `x`, `y`, `width`, `height` **or** `shape: ["###.", "#..."]` (tiles), `frame` (`panel`, `slot`, `button` or a frame of your skin), `tint`, `tooltip`, `actions` |
| `hitbox` | invisible clickable area: `x`, `y`, `width`, `height`, `tooltip`, `actions` |
| `icon` | 16 x 16 item icon: `x`, `y`, `item` (e.g. `diamond`), `tint`, `tooltip`, `actions` |
| `list` | `x`, `y`, `width` (120), `rows` (4), `row-height` (16), `id`, `items` (`text`, `color`, `tooltip`, `actions`, `permission`, `icon`); page buttons appear below when needed (+20 px) |
| `grid` | `x`, `y`, `cols` (4), `rows` (2), `cell` (pitch, 18), `size` (16), `frame` (`slot`, or `none`), `id`, `items` (`text`, `color`, `tint`, `tooltip`, `actions`, `permission`, `icon`); page buttons below when needed |

**Item icons** (`icon:`) are 16 x 16 and use the textures every client already has: items look
like in the inventory, blocks show one face, and items drawn by special renderers (heads, chests,
beds, banners, shields, shulker boxes) have no icon. For a shop-like grid use `cell: 26`, `size: 24`.
Items of your server's resource pack work too (`icon: "myserver:ruby"`), see `icon-source-pack` in
`config.yml`.

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

`%input%` is cleaned (one line, no leading `/`, no `%`, at most 100 characters), but it is still
text typed by the player: do not put it into `console:` commands that could do harm.

## Placeholders

In texts, tooltips, titles and actions: `%player%`, `%world%`, `%online%`, and every
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
