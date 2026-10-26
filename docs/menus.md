# Menus without code

Every `*.yml` file in `plugins/Glyphwork/menus/` is a menu; the file name is its id. On first start
Glyphwork puts two examples there (`beispiel.yml`, `beispiel-regeln.yml`). After editing,
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
elements: [...]
```

## Elements

Drawn in order (later ones on top). Text is always drawn above frames.

| type | settings |
|---|---|
| `label` | `x`, `y`, `text`, `color` (`"#RRGGBB"` or a name like `gold`, default dark gray), `width` (clips with "..."), `align` (`left`/`center`/`right`, needs `width`) |
| `button` | `x`, `y`, `width` (64), `height` (16), `text`, `tint` (button color), `text-color`, `tooltip`, `actions`, `permission` (otherwise grayed out), `frame` |
| `panel` | `x`, `y`, `width`, `height` **or** `shape: ["###.", "#..."]` (tiles), `frame` (`panel`, `slot`, `button` or a frame of your skin), `tint`, `tooltip`, `actions` |
| `hitbox` | invisible clickable area: `x`, `y`, `width`, `height`, `tooltip`, `actions` |
| `list` | `x`, `y`, `width` (120), `rows` (4), `row-height` (16), `id`, `items` (`text`, `color`, `tooltip`, `actions`, `permission`); page buttons appear below when needed (+20 px) |
| `grid` | `x`, `y`, `cols` (4), `rows` (2), `cell` (pitch, 18), `size` (16), `id`, `items` (`text`, `color`, `tint`, `tooltip`, `actions`, `permission`); page buttons below when needed |

`y` of a text is the top of the letters; in a 16 px button the text starts 4 px below the top.
Items with a `permission` are only shown to players who have it.

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

`%input%` is cleaned (one line, no leading `/`, no `%`, at most 100 characters), but it is still
text typed by the player: do not put it into `console:` commands that could do harm.

## Placeholders

In texts, tooltips, titles and actions: `%player%`, `%world%`, `%online%`, and every
[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) placeholder if that plugin is
installed. Values are filled in each time the menu is drawn; use `refresh` to update after an
action.

## Without the pack

Players without the resource pack get a plain dialog: labels as text, buttons, list rows and grid
cells as dialog buttons. Panels are decoration and are left out, so put every action on a button,
hitbox, list or grid.
