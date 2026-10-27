# Menu designer

**Open it:** https://flyingfinger1.github.io/Glyphwork/designer.html (runs in the browser, nothing
to install; the menu is saved in that browser only, so download it regularly). The skin editor
links to it and back.

The designer edits the YAML menus of [menus.md](menus.md) visually: place elements with the mouse,
set their options in a side panel, try the menu out, and download the `.yml` file for
`plugins/Glyphwork/menus/`.

## Real icons and font

Without help the designer shows item icons as placeholders and uses a stand-in font: Minecraft's
textures may not be published on a web page. Load your own copy instead: *Load jar / resource pack*
under *Minecraft textures* reads a client jar in the browser (nothing is uploaded) and keeps only
what the designer needs, about 1,200 item textures and the font sheets, in the browser's storage.
After that icons, tints and text look like in game, including the text widths that decide where
"..." cuts a label.

- Minecraft launcher: `.minecraft/versions/<version>/<version>.jar`
- Prism Launcher / MultiMC: `libraries/com/mojang/minecraft/<version>/minecraft-<version>-client.jar`

Your own pictures (`plugins/Glyphwork/images/`) show as a box with their name until you load them
under *Own pictures*: single PNGs, or the whole `images` folder (sub folders keep their path,
`ranks/gold`). They also stay in the browser only.

Resource packs (ZIP) loaded afterwards replace textures of the jar, like in game, and items of their
own (`myserver:ruby` from `assets/myserver/textures/item/ruby.png`) work as icons. Player heads stay
placeholders (skins come from Mojang's servers). *Remove* deletes the stored textures.

## Workflow

1. **Start or open.** *New* starts a small menu, *Open* loads a `.yml` file, *Paste YAML* takes a
   menu from the clipboard (YAML tab, then *Apply*). The *Menu* tab holds title, size, command,
   permission, refresh and the file name used for the download.
2. **Add elements** from the palette: text, button, panel, click area, icon, list, grid, slider,
   checkbox, choice, bar, picture, head, tabs, scroll pane. A new element goes below the others,
   and the menu grows if needed. The elements of a tab or scroll pane are edited as YAML in that
   element; *Try it* switches tabs and scrolls with a click. The preview cuts a scroll pane by
   pixel, the game leaves out what does not fit completely.
3. **Arrange them.** Drag an element to move it, drag the yellow corner to resize it (buttons,
   panels, lists, grids, sliders ...). Arrow keys move by the snap size (Shift: 8 px), Delete
   removes, Ctrl+D duplicates, Ctrl+Z / Ctrl+Y undo and redo. *Layers* lists the elements in
   drawing order (later ones are on top); ↑ ↓ change it. *9 px grid* shows the text lines that
   player heads sit on; the element panel says when a head moves an element and which `y` avoids it.
4. **Set the options** in the *Element* tab: texts, colors, tooltips, actions (one per line, with a
   list of examples), conditions (`show-if`, `enabled-if`), entries of lists and grids, dynamic
   lists (`items-from` with sample players, worlds and feed entries; `rss:` feeds are not loaded,
   five sample news stand in; `web:` sources and `%web_...%` placeholders are not loaded either and
   stay empty or as they are). *YAML of the element* edits everything else
   directly.
5. **Variables, forms, templates.** The *Variables* tab lists the menu variables with their
   defaults; *Preview* sets a value only in the designer, to check `show-if` and `%menu_…%` texts.
   The *Forms* tab edits forms and their fields. The *Templates* tab holds the menu's `templates:`;
   templates shared from `menus/_*.yml` can be loaded for the preview.
6. **Try it.** In *Try it* mode clicks do what they would in game, as far as the browser can:
   `set`, `toggle`, sliders, checkboxes, choices, page buttons, `require`, `input` (asks with a
   browser prompt) and `form` (shows the form) change the preview; messages, commands, sounds and
   navigation are written to the log.
7. **Download** the file and put it into `plugins/Glyphwork/menus/`, then `/glyphwork reload`.

## Limits

- Minecraft's textures and font cannot be shipped: without a loaded jar, item icons are
  placeholders and text uses a stand-in font. Player heads are always placeholders. Items of
  resource packs are found by their texture `assets/<ns>/textures/item/<name>.png`; items whose
  model points elsewhere show a placeholder. The skins are the plugin's own (*From the skin editor*
  uses the skin last edited there).
- Comments in a `.yml` file are lost when it is opened in the designer.
- PlaceholderAPI placeholders are shown as they are; `%player%` is "Steve", `%online%` is 1.
- Permissions are not checked in the preview (everything is shown as allowed).

## For developers

`editor/designer.html` with `editor/js/designer/` (no build step, js-yaml 4.1.0 from jsDelivr).
`assets.js` reads jars and packs (only the wanted entries) into IndexedDB; the icon and font tables
are copies of the plugin's `item_icons.json` and `vanilla_text.json` in `editor/data/`, and
`tools/editor-parity.mjs` checks that they match.
`menu-render.js` draws a menu like the plugin's `SceneRenderer` (templates, conditions,
variables, heads on the 9 px grid, rounded frames) and has no DOM, so it also runs in Node.
