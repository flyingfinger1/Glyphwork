# Changelog

## Unreleased

- **Fix:** a line break inside a value (chat messages, web data, PlaceholderAPI, `%input%`) no
  longer breaks the layout of the whole menu; single-line text shows it as a space.
- **Clearer web errors:** refused requests (401/403) say what to check, e.g. that Discord needs
  `Authorization: "Bot <token>"`, without showing the token.
- **Placeholder filters:** `|date` formats dates (ISO, RFC, Unix time) and `|discord` makes Discord
  markup readable (mentions, emojis, timestamps, markdown), for `%item_...%`, `%menu_...%` and
  `%web_...%`. docs/menus.md shows a Discord channel as a web source.
- **JSON web sources:** `web:` in `config.yml` (url, `every`, optional headers) and
  `%web_<name>_<path>%` in any text (`%web_status_players.online%`, indexes and `length` for arrays);
  `items-from: "web:<name>.<path>"` turns a JSON array into list entries (`%item_<key>%`, nested keys
  with dots). Loaded in the background with the same limits as feeds.
- **Feeds:** `items-from: "rss:https://..."` fills lists and grids from an RSS or Atom feed (title,
  text, date, link, author), loaded in the background, cached (`feeds.cache`, `feeds.date-format`
  in `config.yml`), safe against external entities and limited to 1 MB / 50 entries. The menu
  draws itself again when a feed arrives for the first time.
- **Detail views:** `%item_text%` of a feed is the whole text with paragraphs (up to 4000
  characters), `%item_summary%` the short one-line version; `selected-if` on list and grid entries
  highlights e.g. the clicked one (API `ListView/Grid.selectedIf`); a scroll pane `id` with
  placeholders keeps one position per content. Together: list on the left, article on the right.
- **Action `url:`** shows a web address in a small native dialog with *Open* and *Back*
  (`url: %item_link%` for feed entries); API `Click.openUrl`.
- **Scroll panes:** `type: scroll` / `Scroll.of(...)` shows part of its elements in a window with a
  scroll bar (▲ / ▼ by `step` px, a click on the track jumps there) while the rest of the menu stays
  in place. Only what fits completely is drawn and clickable; any element works inside. The plain
  dialog lists all of them, the designer shows and scrolls the pane.
- **Long menus:** the pack height now follows the tallest YAML menu (rounded up to 32 px, at most
  2048 px), so frames are drawn in menus of any height and the dialog scrolls them natively, mouse
  wheel included. `max-height` is only the minimum (for scenes of plugins); small servers keep the
  small pack.
- **Tabs:** `type: tabs` / `Tabs.of(...)` puts tabs on top of a panel; the active tab merges with
  the panel into one frame, the others sit slightly lower and darker behind it. Only the active
  tab's elements are drawn and clickable, a click on another tab switches. Elements inside a tab are
  placed relative to the panel and can be anything (templates, `show-if` and cooldowns included);
  `var` keeps the active tab in a menu variable. The designer draws and switches tabs too.
- **Action `add:`** counts: `add: points 1`, `add: amount -1 min 1 max 64` (amounts and limits may
  be placeholders, decimals work) and redraws the menu like `set:`.
- **Saved variables:** `persist: true` (or a list of names) keeps menu variables in the player's
  data, so they survive logouts and restarts; `set: name` without a value removes the saved value.
- **Cooldowns:** `cooldown: 1h` (`30s`, `5m`, `1d 12h` …) on buttons, hitboxes, icons, panels,
  pictures and heads: each player can use the element once per period, kept in the player's data.
  A button on cooldown is grayed out with the remaining time as tooltip; `cooldown-key` shares one
  cooldown between elements; a failed `require` does not use it up. While a cooldown runs, the
  open menu redraws itself when the shown time changes and when it ends (API: `View.redrawAfter`).
  Texts: `cooldown.tooltip` / `cooldown.wait` in the language files, or per element
  `cooldown-tooltip` / `cooldown-message` with `%cooldown%`.
- **Menus that open by themselves:** `open-on: { join: true }` (or `first`) opens a menu when a
  player joins, once the Glyphwork pack is loaded; `open-on: { item: compass }` (or with name,
  lore, slot) hands out a menu item that opens it on right-click, cannot be dropped and comes back
  after death (`give: join|first|no`, `keep: true|false`; `/glyphwork item <menu> [player]` and the
  action `give-item:` hand it out on request). `on-open` / `on-close` actions run when the menu opens (before drawing) and when the
  player closes it; API `Scene.Builder.onOpen` / `onClose`. The designer has fields for all of it.
- **Own dialog warning button:** `dialog-warning.icon` / `icon-hover` in `config.yml` replace the
  warning sign next to every dialog title with one of your pictures, and
  `dialog.warning-tooltip` in the language files replaces its tooltip text (written into the pack
  for every locale of that language). See docs/skin-format.md.
- **Big heads:** `type: head` / `Head.of(x, y, player).size(32)` paints a player's face pixel by
  pixel from the skin, 8 to 32 px, at any position, for any player without changing the pack;
  `head-size` on lists and grids does the same for their `head:` icons. Skins load in the
  background (Paper profile, Mojang lookup for other names) and are cached; until then the small
  client head stands in and the menu redraws when the face arrives.
- **Own pictures:** PNGs in `plugins/Glyphwork/images/` (folders too) go into the pack and are
  placed with `type: image` / `Image.of(x, y, "logo")`, pixel exact and at their own size; names
  may use placeholders (`ranks/%menu_rank%`), `tint` multiplies the colors. Pictures of any size
  are cut into tiles of at most 128 x 128 px (the client cannot load bigger glyphs). Logos,
  banners, rank badges and big player heads are all just pictures. The designer shows them once you
  load the PNGs (or the folder) into it.
- **Progress bars:** `type: bar` / `Bar.of(...)` fills pixel exact from `value` / `max`
  (placeholders work), in any color, 8 or 16 px high, with an optional text in the middle. The
  plain dialog shows ten blocks plus the text.
- **Item tooltips:** `item-tooltip: true` on icons, lists and grids (API `itemTooltip(true)`)
  shows the item's own vanilla tooltip when hovering; plugins can pass a full `ItemStack` with
  enchantments and lore. The example trader uses it.
- **Text blocks:** labels with `wrap: true` and a `width` break into lines at spaces
  (`line-height`, default 10); `
` starts a new line in any label.
- The menu designer knows all three.
- Button text is centered on the width that is drawn (frames are whole tiles: a 22 px button is
  16 px wide); before, it sat to the right.

## 0.9.0 – forms, menu controls and the menu designer

- **Menu designer** in the browser (https://flyingfinger1.github.io/Glyphwork/designer.html, next
  to the skin editor): place and resize elements with the mouse, edit their options, variables,
  forms and templates in side panels, try the menu out (actions, controls, forms work on preview
  variables) and download the `.yml` file. Load your own Minecraft client jar (read only in the
  browser) for real item icons and the real font; resource packs add their textures and items.
  Guide in docs/menu-designer.md.
- **Forms:** several native input fields at once (text, multi-line text, slider, checkbox,
  choice) in a dialog of their own with OK / Cancel; afterwards the menu is shown again. In YAML a
  menu lists them under `forms:` and opens one with the action `form: name`; every field belongs to
  a menu variable (starts with its value, OK writes it back), then the form's `actions` run. API:
  `Click.form(Form, values -> ...)`; values are checked (length, range, step, known options).
  New example `example-forms.yml`; the example trader picks its amount with a slider.
- **Slider, checkbox and choice in the menu:** new elements drawn from the skin (`slider`,
  `checkbox`, `choice` in YAML with `var:`; `Slider`, `Checkbox`, `Choice` in the API). A click on a
  slider's track moves the knob there (hovering shows the value first), a checkbox toggles, a choice
  button steps back (left half) or forward (right half). The variable is set, the element's
  `actions` run and the menu redraws. The plain dialog offers them as buttons (one step down/up,
  toggle, next option). `example-forms.yml` shows them next to a form.
- **Counts on icons:** `count: 64` (YAML, also for list and grid items, placeholders allowed) and
  `Icon.count(...)`, `Grid.count(...)`, `ListView.count(...)` write a stack size at the bottom right
  of the icon, white with a shadow like in the inventory; `1` and empty show nothing. The plain
  dialog writes it after the name ("Diamond ×64"). The example trader shows its pack sizes.
- **Auto-refresh:** `refresh: 5` in a menu (or `Scene.Builder.refresh(Duration)`) redraws the open
  menu every few seconds, so placeholders and dynamic lists stay current. Clicks on the previous
  drawing still work; it stops when the player closes the menu (also with ESC), opens another one or
  an inventory. `example-players.yml` refreshes every 5 seconds and shows the players per world as
  counts.
- **Text shadows like in vanilla:** the text of buttons, lists, grid cells and the page arrows now
  has a drop shadow (before, all text was flat). Grayed-out buttons stay flat, and so do labels,
  like inventory titles; `shadow: true` (YAML) or `Label.shadow(true)` turns it on for a label.
  Arrows of a page button that cannot be used are gray now.
- **English examples:** the example menus exist in English (`example*.yml`) and German
  (`beispiel*.yml`); on first start the set of the server's language is exported (`language`, or
  `fallback-language` when it is `auto`, default English). The demo (`/glyphwork test`) speaks the
  player's language (keys `demo.*` in the language files), and the example plugin is in English
  (`/trader`, `/haendler` still works).
- **Skins renamed:** the built-in skins are now `dark` and `parchment` (were `dunkel` and
  `pergament`).
- List frames and the menu background are rounded **up** to whole tiles: rows of a list whose
  height is no multiple of 8 (2 rows of 18 px) stuck out of the frame, and content near the bottom
  of a menu with such a height sat on or past the edge. Other frames still round down.
- New placeholder `%time%`: the server clock (HH:MM:SS).
- Closing a menu with its exit button or ESC now ends the player's session right away (before, it
  stayed until the next menu).

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
