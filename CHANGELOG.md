# Changelog

## Unreleased

- **Click sound:** `click-sound: ui.button.click 0.4` in config.yml plays a sound for every click
  in Glyphwork screens (off by default), in the players' *Interface* volume. Menus set their own
  with `click-sound:` (or `none`); a button with a `sound:` action plays only that one. API:
  `Scene.Builder.clickSound(Sound)`, `Click.noClickSound()`.
- **Bar colors by value:** `fill:` of a `bar` can be steps by share of `max`
  (`fill: { 0: "#FF5555", 25: gold, 50: "#80FF20" }`), e.g. a health bar that turns red. The
  designer takes them as `0: red, 25: gold, 50: green` and previews them.
- **`font:` on bars** (API `Bar.font`), like the other text elements since 1.4.
- **Text stays inside the menu:** a label without a `width` that would run past the right edge is
  cut there with "..." (4 px from the edge, inside the frame), and the log says once which label
  and what `width` fits. The designer shows it the same way (for 1.5 on). The German example menu
  `beispiel-regeln` had such a label; its text is shorter now.
- **Fix:** a button with `frame: none` (a link, a text button) took no clicks and showed no
  tooltip; it now has an invisible click area of its size.

## 1.4.0-mc1.21.11 – Paper 1.21.11, own fonts

The 1.21.11 build of 1.4.0 (see 1.4.0 below): own fonts for menu text, `font:` on menus and
elements, the font files for the menu designer. Tested in the 1.21.11 client; Java 21, pack
format 75, the 66 icons newer than 1.21.11 stay empty.

## 1.4.0 – own fonts

Menus, configs and plugins made for 1.0 to 1.3 keep working. Supports Paper 26.1 to 26.3 (and
1.21.11 as `1.4.0-mc1.21.11`).

- **Own fonts (experimental):** `fonts:` in config.yml declares TrueType/OpenType fonts: a file in
  `plugins/Glyphwork/fonts/` or a `url:`, the pixel size, optionally `line-height` and `chars`.
  Glyphwork draws each font into the pack itself (without smoothing, pixel fonts look best), so it
  knows every letter's width and wrapping, cutting and centering stay exact; letters a font lacks
  come from the Minecraft font.
- **`font:` everywhere text is drawn:** on the menu and on label, button, list, grid, checkbox,
  choice and tabs (API: `.font("name")` on `Scene.Builder` and these elements). Elements in tabs
  and scroll panes use the font around them; `font: minecraft` sets the Minecraft font on purpose.
  Text tooltips follow their element's font. Wrapped labels take the font's line height.
- **Menu designer:** the plugin writes `fonts/<name>.glyphwork-font` for every font. Loaded in the
  designer (*Own fonts*), the preview draws and measures text in it like the plugin, and *Font*
  offers the loaded fonts in a list (*As around it*, *Minecraft font*, own fonts).

## 1.3.0-mc1.21.11 – Paper 1.21.11

The 1.21.11 build of 1.3.0 (see 1.3.0 below): placeholders in menu variable defaults
(`nick: "%player%"` starts as the player's name). The 26.1 to 26.3 support of 1.3.0 is for the
other build; this one stays on Paper 1.21.11 (Java 21, pack format 75, 66 icons newer than 1.21.11
stay empty).

## 1.2.2-mc1.21.11 – Paper 1.21.11, example text

The 1.21.11 build with the state of 26.3 release 1.2.1; the plugin's code is unchanged since
1.2.1-mc1.21.11.

- Example plugin: the options text names the plain mode by its current name ("Plain", not
  "Simple").

## 1.2.1-mc1.21.11 – Paper 1.21.11

A build for **Paper 1.21.11** (Java 21) from the branch `mc/1.21.11`; `main` and the other releases
target 26.3. Menus, configs and plugins work as on 26.3.

- **Paper 1.21.11:** compiled against paper-api 1.21.11 with Java 21, resource pack format 75.
  Clicks need a 1.21.11 client (1.21.9 and 1.21.10 miss clicks after a negative shift, 1.21.8 has
  no clickable dialog text).
- **Icons with older clients:** vanilla textures newer than 1.21.11 get fonts of their own (a
  client drops a whole font when one texture is missing), so all other icons load; the 66 newer
  ones stay empty on 1.21.11.
- **Skin cache (optional):** `heads.skin-cache` (e.g. `7d`, default `0` = off) keeps the faces of
  big heads in `plugins/Glyphwork/cache/faces.json`, so restarts and reloads do not ask Mojang
  again and run into its rate limit.
- **Fixed:** `/glyphwork reload` could throw a ConcurrentModificationException while Paper built
  the command tree.

## 1.3.0 – Paper 26.1 to 26.3

Menus, configs and plugins made for 1.0 to 1.2 keep working.

- **Paper 26.1 to 26.3 with one jar:** the plugin declares api-version 26.1 and resource pack
  formats 84 to 97 (before: 26.3 only). Tested in game on Paper 26.1.2, 26.2 and 26.3; the 26.1
  and 26.1.1 clients have the same textures, pack format and click code as 26.1.2.
- **Icons on 26.1 and 26.2:** the 65 textures newer than 26.1 get fonts of their own, so all other
  icons load there; these 65 stay empty on 26.1 and 26.2.
- **Placeholders in variable defaults:** `nick: "%player%"` under `variables:` now starts as the
  player's name; before, `%player%` showed as text (also in the bundled form examples). Values set
  later (`set:`, forms, input) are used as they are.
- **Paper 1.21.11:** `1.3.0-mc1.21.11` is the same version for Paper 1.21.11 (branch
  `mc/1.21.11`, Java 21).
- Modrinth lists 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.

## 1.2.1 – skin cache, icons with older clients

Menus, configs and plugins made for 1.0 to 1.2 keep working.

- **Skin cache (optional):** `heads.skin-cache` in `config.yml` (e.g. `7d`, default `0` = off)
  keeps the faces of big heads in `plugins/Glyphwork/cache/faces.json`, so restarts and reloads do
  not ask Mojang again and run into its rate limit (then big heads show the small stand-in head).
- **Icons with older clients:** vanilla textures newer than Minecraft 1.21.11 get fonts of their
  own. A client drops a whole font when one of its textures is missing, so a 1.21.11 client lost
  every icon before; now only the 66 newer ones stay empty.
- **Fixed:** `/glyphwork reload` could throw a ConcurrentModificationException while Paper built
  the command tree.
- **Paper 1.21.11:** a separate build, `1.2.1-mc1.21.11`, is on the GitHub releases (branch
  `mc/1.21.11`, Java 21).
- **Menu designer** (online already): it reads the Minecraft version of the loaded client jar and
  frames icons in red that this version lacks; another client jar replaces everything loaded before.
- Example plugin: the options text names the plain mode by its current name.

## 1.2.0 – screens for several players, big heads on buttons

Found by building small games (Minesweeper, Tic-Tac-Toe and Battleship for two players) with the
API. Menus, configs and plugins made for 1.0 and 1.1 keep working; one drawing detail changed (big
elements with a player head, see below).

- **Other players' screens:** `Glyphwork.get().redraw(player)` draws a player's open screen again
  (state, pages and history stay), `redraw(sceneId)` does it for everyone who has that scene open,
  `openScene(player)` tells which scene a player has open. Before, a move in a two-player game had
  to reopen the other player's screen.
- **Why a screen closed:** `onClose((click, reason) -> ...)` gets `PLAYER`, `REPLACED` or
  `CLOSED_BY_CODE`; the old `onClose(click -> ...)` works as before.
- **Big elements keep their place with a player head:** buttons, list rows and grid cells 24 px
  high or more no longer move to put the head on the client's 9 px text grid; the head moves
  instead (at most 4 px from the middle). A board of big buttons with heads looked uneven before.
- **Big heads on buttons:** `Button.headSize(16|24|32)` / `head-size:` paints the face of a `head:`
  icon from the skin, in the middle of the button (without text) or at its left; like on lists and
  grids it sits at any pixel, not on the 9 px text grid.
- **Preloading heads (optional):** `Glyphwork.get().preloadHeads(names...)` loads skins for big
  heads ahead and completes when they are there, so a screen can open with the faces in place
  instead of the small stand-in head being replaced a moment later.
- **Hint for labels that run into buttons:** a label without a width whose text runs into a button
  next to it is logged once, with the width that would fit.
- **Menu designer:** the next version shows up as "not released yet" (never the default), and the
  preview draws like the chosen version where the behavior changed. New: `head-size` on buttons.
- Not possible in dialogs, now documented by trying: the server learns of clicks only, not of the
  mouse position, the mouse button or keys held while the dialog is open.

## 1.1.0 – GUI sprites, picture sizes and one pack for the client

Everything here is new; menus, configs and plugins made for 1.0 keep working unchanged.

- **GUI sprites as icons:** `icon: "gui:hud/heart/full"`, `gui:mob_effect/speed`,
  `gui:statistics/item_crafted`, `gui:icon/checkmark` and about 100 more (hearts, food, armor, air,
  every status effect, statistics, small icons) wherever an item id goes, in YAML and the API. Drawn
  from the client's own textures at their own size in the middle of the icon place; sprites in the
  plain dialog; the menu designer shows them after loading a client jar again.
- Docs: `<sprite:...>` and `<head:...>` work in tooltips, titles and messages.
- **Pictures in other sizes:** `width` / `height` (the other side follows the aspect ratio) or
  `scale` on an `image` element. Glyphwork resizes the picture when it builds the pack and adds it
  as an extra picture; YAML menus add their sizes by themselves, plugins announce theirs with
  `Glyphwork.get().imageSize("logo", 32, 0)` / `imageScale(...)` and draw them with
  `Image.size(w, h)` / `.scale(s)` (experimental). The menu designer shows the size. Without sizes
  the pack stays exactly as before.
- **One pack for the client:** `pack.include-source-pack: true` puts the server's own resource pack
  (`icons.source-pack`) into the Glyphwork pack; its pack.mcmeta and pack.png are kept, Glyphwork's
  own files win. Empty `resource-pack=` in server.properties then. Helps with client mods that keep
  only the last server pack, and saves merging by hand as with `hosting: provided`.
- **Menu designer: Glyphwork version.** Pick the version your server runs: newer settings are hidden
  or marked, and the check list names everything that needs a newer version. The docs mark what
  came after 1.0 with *since 1.1*.

## 1.0.0 – stable API, tabs, scroll panes, feeds and Bedrock

**1.0: the API, the menu format and `config.yml` are now stable** (see "Compatibility" in the
README). Before that promise some names were cleaned up; plugins built against 0.9 need these
changes, YAML menus and configs keep working (old keys are still read, with a hint in the log).

- **Renamed (API):** `ListView.onSelect` → `onClick`; `Choice.onSelect`, `Checkbox.onToggle` and
  `Tabs.onSelect` → `onChange`; `Button.textColor` and `Checkbox.textColor` → `color`;
  `Grid.label` / `labelColor` → `text` / `color`; `Bar.color` → `fill`; `Tabs.tint` →
  `inactiveTint`; `DisplayMode.SIMPLE` → `PLAIN` (stored choices carry over).
- **Changed (API):** `visible`, `enabled` and `checked` take a `Predicate<View>`; sliders and form
  numbers use `double` (`Form.Values.number` returns a double); form fields are the final classes
  `Form.TextField`, `CheckboxField`, `SliderField`, `ChoiceField`; `Label.DARK` is gone, a label
  without color (`null`, the default) uses the skin's text color; `Click.openUrl` accepts only
  http/https; `Scene.Builder.build()` rejects two lists, grids, tabs or scroll panes with the same id.
- **New (API):** `Click.page(listId, n)`, `tab(tabsId, tabId)` and `scroll(scrollId, offset)`;
  `@NullMarked` package; `@ApiStatus.Internal` on renderer getters, `@ApiStatus.Experimental` on
  tabs, scroll panes, big heads, text styles, item tooltips, the own plain view and `redrawAfter`.
- **Renamed (YAML):** button `text-color` → `color`, bar `color` → `fill`, tabs `tint` →
  `inactive-tint`. The `input:` action now splits at ` | ` with spaces, like `require:`.
- **Cooldowns need an `id`** (unique in the menu) or a `cooldown-key`: before, an element without
  them was keyed by its position, so adding an element moved cooldowns to other buttons. Stored is
  when a key was last used; each element waits its own time from then (a key used with different
  times is mentioned in the log). Running cooldowns of 0.9 start anew.
- **Values from outside are text:** feed and web entries, `%item_...%` and `%input%` can no longer
  add MiniMessage tags (colors, click events) or be filled in as another placeholder; `|raw` keeps
  the tags for a source you trust. Placeholders of a text are filled in one pass.
- **Checks when loading menus:** an unknown filter (`%x|dat%`) or an invalid alias is an error;
  frames the skin does not have, missing pictures, `open:` / `give-item:` targets without a menu
  and a command used by two menus are hints, in the log and after `/glyphwork reload`.
- **`config.yml` cleaned up** (`config-version: 1`): `max-height` → `pack.min-height`,
  `fallback` → `plain-dialog`, `icons` / `icon-source-pack` → `icons.enabled` /
  `icons.source-pack`, `feeds.date-format` → `date-format`; the pack prompt text lives only in the
  language files (`pack.prompt`). Unknown or misspelled keys are reported. "Simple" is called
  "plain" everywhere (`/glyphwork display plain`, `/glyphwork test plain`; the old words still work).
  Edited language files: `cmd.simple` → `cmd.plain`, `display.simple` / `display.info-simple` →
  `display.plain` / `display.info-plain`, `fallback.*` → `plain.*` (the log names old keys).
- **Lists, grids, tabs and scroll panes without an `id`** get one that is unique in the whole menu;
  before, the same element in several tabs shared its page.

- **Own plain view:** `plain:` in a menu (API `Scene.Builder.plain(...)`) defines the dialog for
  players without the pack and Bedrock players by hand, with `columns: 1|2`; same variables as the
  pixel GUI.
- **Grayed-out buttons in the plain dialog** stay visible in gray; a click sends their tooltip
  (e.g. the remaining cooldown) instead of doing nothing visible.
- **Pack from your server or proxy:** `pack.hosting: provided` sends no pack; merge
  `plugins/Glyphwork/pack/glyphwork.zip` into the pack your server or proxy (Velocity) sends.
  `pack.provided-loaded: always` (a required pack) or `server-pack` (the server.properties answer)
  tells Glyphwork who has it.
- **Designer:** a dashed area marks the room for the page buttons below lists and grids, with a hint
  when another element is in the way.
- **Plain dialog with pictures:** players without the pack see item icons in front of button, list
  and grid texts (sprite components the client draws from its own textures), and long button texts
  switch the buttons to one wide column instead of cutting them (measured over all tabs, so the
  layout stays when switching). Tabs become a clickable line above the buttons of the active tab
  (on Bedrock: tab buttons and a divider).
- **Bedrock players** (Geyser, optionally Floodgate; found without a hard dependency) always get the
  plain dialog, which Geyser turns into a Bedrock form, and no Java pack request; join menus open
  at once for them. Their buttons show the first tooltip line below the text (forms have no
  tooltips), and their forms close after a click until the server sends the next one, so a
  quick second click can no longer hit a button of the previous screen and closing a menu closes
  it (Geyser's waiting form could not be removed from the server). Timed redraws (cooldowns,
  refresh, loading feeds) send a new form only when something visible changed, instead of closing
  and reopening it every second (and at most one every 5 s).
- **Text formatting:** labels, buttons and list entries understand MiniMessage for parts of their
  text (`<b>`, `<i>`, `<u>`, `<st>`, colors); labels also have `bold`, `italic`, `underline` and
  `strikethrough` for the whole text. Bold counts 1 px per character when cutting, centering and
  wrapping; underline and strikethrough are drawn as pixel-exact lines of their own. API:
  `markup(true)` on Label, Button and ListView, plus the four switches on Label. `|discord` now turns
  Discord markdown into this formatting; `|discord-plain` keeps the old plain text.
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
  text, date, link, author), loaded in the background, cached (`feeds.cache`, `date-format`
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
  wheel included. `pack.min-height` is only the minimum (for scenes of plugins); small servers keep the
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
  (`line-height`, default 10); `\n` starts a new line in any label.
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
