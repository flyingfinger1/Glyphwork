# Changelog

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
