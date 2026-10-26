# PaperDialogUI

Clickable, pixel-positioned GUIs inside the vanilla **dialog** screen (Paper 26.3), no client mod.
The whole GUI is one `plain_message`; the plugin generates a resource pack with bitmap fonts,
serves it from an embedded HTTP server and sends it on join. See [SPEC.md](SPEC.md).

**Status: M0** (proof of the primitives, one hard-coded screen). Open it with `/dialogui test`.

## How it works (as verified against the 26.3 client)

- **x (P1):** font `dialogui:space` maps private-use chars to advances of +-2^k px; any offset is a
  combination of them. All elements are layered on the **first text line** in paint order.
- **y (P2):** one font per vertical offset (`dialogui:y<N>`) registers every sprite texture and the
  vanilla text textures (`ascii.png` etc., not shipped) with `ascent = normal - N`. Only offsets that
  are used get a font. The remaining lines of the message are empty and only make the text widget
  tall enough to receive clicks.
- **Clicks (P3):** the 26.3 client (`ActiveTextCollector.ClickableStyleFinder`) hit-tests each glyph
  against its *drawn* box (space glyphs: their line box) and the **last** glyph under the cursor that
  has a click event wins. So a sprite with a click event is clickable exactly where it is drawn, and
  glyphs without events (text labels, decorations) never block clicks. The line-binding problem from
  the original HugoSMP recipe does not apply to 26.3. `src/test/.../M0HitTest` replays this logic.
- Text clicks in a dialog body run with the dialog's `afterAction`; we use `NONE`, so the screen
  stays open and the server answers with a re-rendered dialog.
- Bitmap advance = rightmost non-transparent column + 1 (same rule as the client).

## Build & test

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25.0.3" ./gradlew build
```

`tools/extract_vanilla_text.py <client.jar> src/main/resources/vanilla_text.json` regenerates the
vanilla char grids and advances when Minecraft changes its fonts.

Config (`config.yml`): pack server bind/port/public URL, send on join, required, prompt.
