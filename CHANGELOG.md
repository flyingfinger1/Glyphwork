# Changelog

## 0.8.0 – player heads

- **Player heads as icons:** `head:<name or uuid>` wherever an item id goes (YAML `icon:`, API
  `Icon.head(...)`, grids, lists, buttons). The client draws heads as 8 x 8 px and only on its 9 px
  text grid; so that a head still sits in the middle of its button, row or cell, the element moves
  with it (up to 4 px) and the log names the `y` that avoids the move (table in docs/menus.md).
  Lists with heads want a row height of 9 or 18, grids a cell pitch of 18 or 27; lists of
  `online-players` default to 18 px rows. Online players send their skin along. Heads also appear in
  the plain dialog, in front of the label.
- Placeholders work in the `icon` of `icon` elements and buttons (`icon: "head:%player%"`).
- Buttons whose height is no multiple of the tile size center their text and icon on the height
  that is actually drawn (a 20 px button is drawn 16 px high); before, the text sat too low.
- Lists that mix heads and item icons put both in one 16 px column, so all texts start at the same x.
- Dynamic lists of `online-players` have `%item_head%` (and `%item_icon%`) for the player's head;
  `beispiel-spieler.yml` uses it.

## 0.7.0 – languages, menu variables and dynamic lists

- **Languages:** all messages, Glyphwork's own dialogs (display mode, plain view, text prompt) and
  command words in English and German. `language: auto` (default) uses each player's game language,
  everyone else gets `fallback-language` (en); or set a fixed language. Texts are MiniMessage files in
  `plugins/Glyphwork/lang/` (exported on start): change single keys or add a language as
  `lang/<code>.yml`. Command words work in every language (`/glyphwork display` and
  `/glyphwork darstellung`); tab completion suggests the player's own.
- `pack.prompt` empty (new default) = the prompt from the language files. The old German default
  prompt in existing configs is treated the same way.
- `/glyphwork pack` from the console answers instead of doing nothing; `/glyphwork status` shows
  each player's language.
- **Menu variables:** `variables:` with defaults, the actions `set: name value` and `toggle: name`,
  and `%menu_name%` in every text, condition and action. The menu redraws itself after `set`/`toggle`.
  Tabs, search fields and switches without code. `open:` takes placeholders.
- **Dynamic lists:** `items-from: online-players` or `worlds` fills a list or grid each time it is
  drawn; `item:` describes the entries with `%item%`, `%item_world%`, `%item_players%` ...
  (filterable with `show-if`). Misspelled `%item_...%` are reported on load.
- Conditions accept an empty text as `''` (`%menu_filter% != ''`).
- New example `beispiel-spieler.yml` (tabs, player search, worlds).

## 0.6.0 – conditions and templates for menus

- **Conditions in YAML menus:** `show-if` on every element and list/grid item, `enabled-if` and
  `disabled-tooltip` on buttons, and a `require:` action that stops the remaining actions when its
  condition does not hold. Conditions compare placeholders and values (`== != > >= < <= contains
  !contains`, numbers as numbers); a list means all must hold.
- **Templates for YAML menus:** reusable elements or groups in `menus/_*.yml` (for all menus) or
  under `templates:` (one menu), used with `{ use: name, x, y, key: value }` and filled in at
  `{key}` / `{key|default}`. Settings of the use entry replace those of one-element templates.
- New example `beispiel-warps.yml` with `_beispiel-vorlagen.yml` (put into `menus/` on first start; existing servers find them in
  [src/main/resources/menus/](https://github.com/flyingfinger1/Glyphwork/tree/main/src/main/resources/menus)).
- API: `visible(Function<View, Boolean>)` on every element.

## 0.5.0 – example plugin and more skins

- **Example plugin** ([example/](example/)): a trader (`/haendler`) built against `glyphwork-api`
  the way another plugin would use it: item icons in a grid, amount buttons and a text prompt, a buy
  button that is only active when the player can pay, and an options screen for the display mode.
  Releases attach it as `glyphwork-example-<version>.jar`.
- **Two more built-in skins:** `dunkel` (dark, light text) and `pergament` (parchment), next to
  `vanilla`. All built-in skins are exported to `themes/` on start when their folder is missing.
- **Text color per skin:** `"text": "#RRGGBB"` in `theme.json` colors labels that use the default
  color (`Label.DARK`) and the page numbers, so dark skins stay readable. The skin editor has a text
  color field and a template choice (Vanilla, Dunkel, Pergament) for *New*.
- CI: GitHub Actions updated to their current versions.

## 0.4.0 – icons from the server's resource pack

- **Icons match the server's resource pack:** `icon-source-pack: auto` (default) reads the pack
  from server.properties on start (download cached by hash; also a zip path or URL). Textures it
  replaces are measured, so icons line up; items it defines become icons (`"myserver:ruby"`), and
  vanilla items it re-models show the new texture. Icons are placed like in the inventory: the
  whole 16×16 texture at a fixed spot, so the art sits where the texture draws it.
- **No broken boxes without the server pack:** Glyphwork checks whether a player loaded the server
  pack and leaves out icons that need its textures for players who did not (grid cells show a
  short name instead).
- **Sturdier fonts:** text, vanilla icons and server-pack icons are separate fonts in the pack.
  The client throws away a whole font when one texture is missing, so a missing texture can no
  longer take text or vanilla icons with it.

Note for testing: client mods that take over server resource packs (e.g. "Server Resource Pack
Checker") can drop the server pack as soon as Glyphwork's pack arrives. Glyphwork cannot see that;
those players get boxes for server-pack icons, or can switch to "Ohne Icons".

## 0.3.0 – item icons and display modes

- **Item icons:** `Icon` element, `Grid.icon`, `Button.icon`, `ListView.icon`, and `icon:` in YAML
  menus (element, button, list and grid items). The pack only references the item and block
  textures every client already has (no Minecraft image is shipped); 1595 items have an icon, items
  drawn by special renderers (heads, chests, beds, banners, shields, shulker boxes) do not.
  `icons: false` in `config.yml` leaves them out of the pack. Third demo page "Items".
- Grids accept `frame("none")`.
- **Display mode per player** (`FULL`, `NO_ICONS`, `SIMPLE`) for players whose own resource pack
  tears the menus: `/glyphwork darstellung` (native dialog, works even when the pixel GUI breaks),
  API `Glyphwork#displayMode` and `#sendPack`, and `players.commands: false` to hide `/glyphwork`
  from players so servers can offer it in their own system.

Known limit: icon (and text) widths are taken from the vanilla textures. A resource pack that crops
item textures very differently can shift icons on a line by a few pixels; players can switch to
"Ohne Icons" or "Einfach".

## 0.2.0 – menus without code

### Plugin

- **YAML menus** for server admins: every `*.yml` in `plugins/Glyphwork/menus/` is a menu with
  labels, buttons, panels (any tile shape), hitboxes, lists and grids. See
  [docs/menus.md](docs/menus.md).
- **Actions:** run a command as the player or the console, chat message (MiniMessage), sound, open
  another menu, back, close, refresh, and `input:` to ask for a text that the next action uses as
  `%input%`.
- **Placeholders:** `%player%`, `%world%`, `%online%` and every PlaceholderAPI placeholder when it
  is installed (optional, no hard dependency).
- **A command per menu** (`command:`, `aliases:`, `permission:`), registered at runtime and on
  `/glyphwork reload`; admins open any menu with `/glyphwork menu <id> [player]`.
- **Permissions** per menu, per button (otherwise grayed out) and per list/grid entry (otherwise
  hidden).
- **Clear errors:** problems name the file and the setting, e.g.
  `warps.yml.elements[2].widht: unknown setting (allowed: ..., width, ...)`; broken files are
  skipped, the others still load. `/glyphwork reload` shows them in chat.
- Two German example menus are put into `menus/` on first start.

### Releases

- Releases are also published to Modrinth (as beta while the version is 0.x).

## 0.1.0 – first release

Clickable, pixel-positioned GUIs inside the vanilla dialog screen for Paper 26.3. No client mod;
players only need the resource pack that Glyphwork generates and sends itself.

### Plugin

- **Scene API** (`glyphwork-api`): panels (any tile shape), labels, buttons, invisible hitboxes,
  lists and grids with automatic paging, tooltips, tint colors, per-player state, navigation
  (`reopen`, `open`, `back`, `close`) and text input through a small native dialog. Other plugins
  depend on Glyphwork and compile against the API jar. See [docs/api.md](docs/api.md).
- **Exact clicks:** everything is clickable exactly where it is drawn, also off the 9 px text grid
  (verified against the 26.3 client's hit test).
- **Skins:** frames are autotiled from small A2 sheets (47 tiles per frame, any shape joins
  seamlessly). Built-in vanilla-style skin, exported to `plugins/Glyphwork/themes/vanilla/` as a
  template; own skins via `theme:` in `config.yml`. Format: [docs/skin-format.md](docs/skin-format.md).
- **Resource pack:** generated on start, byte-stable (same content, same SHA-1, no re-download),
  depends only on the skin, not on the scenes. Served by a built-in HTTP server or written to
  `plugins/Glyphwork/pack/<sha1>.zip` for external hosting (`pack.url` with `{sha1}`).
- **Fallback:** players without the pack (declined, failed, still loading) get a plain native
  dialog built from the same scene.
- **Commands:** `/glyphwork pack` (everyone), `/glyphwork test [fallback]`, `status`, `reload`
  (permission `glyphwork.admin`). Reload rebuilds theme and pack and sends it to everyone online.

### Skin editor

Browser tool at https://flyingfinger1.github.io/Glyphwork/: paint directly in a sample window
(linked quarters), generator with presets, mirroring, live preview of any shape with tint, the
plugin's demo scene, a seam view with a test shape covering all 68 quarter transitions, palettes,
ZIP import/export in the plugin's theme layout. German and English.
Guide: [docs/skin-editor.md](docs/skin-editor.md).

### Limits

- Paper 26.3, Java 25. Dialogs exist since 1.21.6; other versions are untested.
- Text is always drawn above frames; one color per label; Minecraft font without shadow.
- Scenes up to `max-height` px (default 256).
- No item icons yet (planned).
- The API may still change before 1.0.
