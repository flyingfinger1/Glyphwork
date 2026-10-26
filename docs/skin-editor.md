# Skin editor

**Open it:** https://flyingfinger1.github.io/Glyphwork/ (runs in the browser, nothing to install;
work is saved in that browser only, so export regularly).

The editor draws Glyphwork skins in the [A2 format](skin-format.md). You never have to think in
quarters: paint on a real window and the editor writes the pixels to the right place.

## Workflow

1. **Start from the generator** (left column). Pick *Window*, *Button* or *Slot* and adjust fill,
   outline, rounded corners, light and shadow. Every change is applied live to the current frame;
   one *Undo* reverts a whole generator session.
2. **Paint in the window.** In the preview choose *Paint in window* and draw directly on the
   sample shape with the usual tools (pencil, eraser, fill, line, rectangle, picker). Hovering
   highlights every place that uses the same quarter, so you see what a stroke will change.
   Use *Build shape* and the shape presets (rectangle, L, T, ring, plus, stairs) to try other
   shapes.
3. **Fine-tune on the A2 sheet** (middle). The overlay names each quarter ("top edge",
   "inner corner bottom right" ...). *Mirror* ↔/↕ copies strokes to the mirrored position inside
   the same block, so you draw one corner and get four.
4. **Check the seams.** The *Seams* tab lists the 40 transitions whose quarters are not
   neighbours in the sheet and are easy to miss. *Show test shape* loads a shape that contains
   all 68 possible transitions. Different pixels at a seam can be intended (where light meets
   shadow), so there is no automatic verdict.
5. **Look at the demo scene.** The *Demo scene* tab shows the plugin's `/glyphwork test` screen
   with your skin (clickable). Buttons are tinted in game, so keep `button` light gray;
   *Tint* in the preview shows the effect.
6. **Export** the ZIP, unpack it into `plugins/Glyphwork/themes/`, set `theme: <folder name>` in
   `plugins/Glyphwork/config.yml` and restart the server.

Every frame needs at least `panel`, `button` and `slot`. *+ Frame* adds more (they become
available to scenes under their name). Import accepts a theme ZIP or a single A2 PNG.

## Shortcuts

| Key | Action |
|---|---|
| B / E / G / L / R / I | pencil / eraser / fill / line / rectangle / picker |
| Ctrl+Z, Ctrl+Y (Ctrl+Shift+Z) | undo, redo |
| Right mouse button | erase while painting |

## For developers

`editor/` is plain HTML and ES modules, no build step:

```bash
py -m http.server 5280 --directory editor
```

The autotile and style code is a port of the plugin's Java. `tools/editor-parity.mjs` (run after
`./gradlew test`, also in CI) checks that both produce identical A2 sheets and tiles, that the
PNG codec round-trips exactly, and that the seam test shape covers every transition. A theme
exported by the editor lives in `src/test/resources/editor-export/` and is loaded by a plugin
test. Pushing changes under `editor/` deploys the page via `.github/workflows/pages.yml`.
