**Clickable, pixel-positioned GUIs inside vanilla Minecraft dialogs — no client mod.**

Glyphwork renders whole screens (panels, buttons, lists, grids, text) into the dialog screen that
Minecraft has had since 1.21.6. Players only need a resource pack, which Glyphwork generates and
sends by itself. Everything is clickable exactly where it is drawn.

![Demo screen in game](https://raw.githubusercontent.com/flyingfinger1/Glyphwork/main/docs/img/demo-ingame.png)

## For whom

- **Server admins:** describe menus in YAML files (buttons that run commands, open other menus or
  ask for input, PlaceholderAPI, a command per menu) - no coding. See the
  [menu guide](https://github.com/flyingfinger1/Glyphwork/blob/main/docs/menus.md).
- **Plugin developers:** build screens with the Java API.

```yaml
# plugins/Glyphwork/menus/warps.yml  ->  /warps
title: "<gold>Warps"
command: warps
elements:
  - type: button
    x: 8
    y: 24
    width: 72
    height: 16
    text: "Spawn"
    tint: "#50E050"
    actions: ["player: spawn", "close"]
```

## Features

- **Scene API** for plugin developers: panels of any tile shape, labels, buttons, invisible
  hitboxes, lists and grids with automatic paging, tooltips, colors, per-player state, navigation
  (open / back / reopen / close) and text input through a small native dialog.
- **Pixel-exact clicks**, also off the 9 px text grid.
- **Skins:** frames are autotiled from tiny sheets, so any shape joins seamlessly. A vanilla-style
  skin is built in; make your own with the browser-based
  [skin editor](https://flyingfinger1.github.io/Glyphwork/).
- **Resource pack handled for you:** generated on start, stable hash (no re-downloads), served by
  a built-in web server or written to disk for your own web server / CDN.
- **Fallback:** players who decline the pack get a plain native dialog built from the same screen.
- `/glyphwork reload` applies config and skin changes and sends the new pack to everyone online.

## Setup

1. Put the jar into `plugins/` of a **Paper 26.3** server (Java 25) and start it.
2. In `plugins/Glyphwork/config.yml` set `pack.url` to an address your players can reach
   (built-in server: open `pack-server.port`; or `pack.hosting: external` with your own web server).
3. `/glyphwork reload`, then `/glyphwork test` (demo) or `/glyphwork menu beispiel` (example menu).

## For developers

```java
Scene settings = Scene.builder("myplugin:settings", 176, 96)
    .background()
    .add(Label.of(8, 7, "Settings"))
    .add(Button.of(8, 24, 80, 16)
        .text(v -> soundOn(v.player()) ? "Sound: ON" : "Sound: OFF")
        .onClick(c -> { toggleSound(c.player()); c.reopen(); }))
    .build();
Glyphwork.get().open(player, settings);
```

`depend: [Glyphwork]`, compile against `glyphwork-api` from the
[GitHub release](https://github.com/flyingfinger1/Glyphwork/releases).
Documentation: [API](https://github.com/flyingfinger1/Glyphwork/blob/main/docs/api.md) ·
[skin format](https://github.com/flyingfinger1/Glyphwork/blob/main/docs/skin-format.md) ·
[how it works](https://github.com/flyingfinger1/Glyphwork#how-it-works-as-verified-against-the-263-client).

## Limits

- Paper 26.3 only for now; the API may still change before 1.0.
- Text is always drawn on top of frames; no item icons yet.

The technique goes back to a public description by the developer of the HugoSMP server; Glyphwork
is an independent, MIT-licensed implementation.
