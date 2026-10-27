// Port of de.flyingfinger.glyphwork.theme.Style (procedural outline/bevel/fill) and Autotile.a2.

import { Pix } from './pix.js';

const st = (fill, outline, round, lightWidth, light, shadeWidth, shade) =>
  ({ fill, outline, round, lightWidth, light, shadeWidth, shade });

/** The skins built into the plugin (Theme.BUILTINS, Style.java); text = default label color. */
export const SKINS = {
  vanilla: {
    name: 'Vanilla', text: 0xff404040,
    panel: st(0xffc6c6c6, 0xff000000, true, 2, 0xffffffff, 2, 0xff555555),
    button: st(0xffc8c8c8, 0xff000000, false, 1, 0xffffffff, 2, 0xff6e6e6e),
    slot: st(0xff8b8b8b, null, false, 1, 0xff373737, 1, 0xffffffff),
  },
  dark: {
    name: 'Dark', text: 0xffe0e0e0,
    panel: st(0xff2e3138, 0xff0a0b0d, true, 2, 0xff4a4f5a, 2, 0xff1a1c20),
    button: st(0xff7a7f8a, 0xff0a0b0d, false, 1, 0xffa8aeb8, 2, 0xff464a52),
    slot: st(0xff484c55, null, false, 1, 0xff1a1c20, 1, 0xff6a707a),
  },
  parchment: {
    name: 'Parchment', text: 0xff4a3420,
    panel: st(0xffe9ddb8, 0xff4a3420, true, 2, 0xfffbf3dc, 2, 0xffb79f6e),
    button: st(0xffd6c49a, 0xff4a3420, false, 1, 0xfff4ead0, 2, 0xff9c8458),
    slot: st(0xffb8a27a, null, false, 1, 0xff7a6040, 1, 0xfff4ead0),
  },
};

/** Styles of the vanilla skin (defaults for new frames and the generator). */
export const STYLES = { panel: SKINS.vanilla.panel, button: SKINS.vanilla.button, slot: SKINS.vanilla.slot };

const at = (a, x, y) => y >= 0 && y < a.length && x >= 0 && x < a[0].length && a[y][x];

/** Renders the shape inside[y][x]; pixels outside the array count as outside. */
export function renderStyle(style, inside) {
  const h = inside.length, w = inside[0].length;
  const inn = inside.map((r) => r.slice());
  if (style.round && style.outline != null) {
    const cut = [];
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        if (inn[y][x] && (!at(inn, x - 1, y) || !at(inn, x + 1, y)) && (!at(inn, x, y - 1) || !at(inn, x, y + 1))) cut.push([x, y]);
      }
    }
    for (const [x, y] of cut) inn[y][x] = false;
  }
  const edge = Array.from({ length: h }, () => new Array(w).fill(false));
  if (style.outline != null) {
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        if (!inn[y][x]) continue;
        outer: for (let dy = -1; dy <= 1; dy++) {
          for (let dx = -1; dx <= 1; dx++) {
            if (!at(inn, x + dx, y + dy)) { edge[y][x] = true; break outer; }
          }
        }
      }
    }
  }
  const border = (x, y) => !at(inn, x, y) || edge[y][x];
  const img = new Pix(w, h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (!inn[y][x]) continue;
      if (edge[y][x]) { img.set(x, y, style.outline); continue; }
      let lit = false, shaded = false;
      for (let k = 1; k <= style.lightWidth; k++) lit = lit || border(x - k, y) || border(x, y - k);
      for (let k = 1; k <= style.shadeWidth; k++) shaded = shaded || border(x + k, y) || border(x, y + k);
      img.set(x, y, lit && shaded ? style.fill : lit ? style.light : shaded ? style.shade : style.fill);
    }
  }
  return img;
}

const rect = (w, h) => Array.from({ length: h }, () => new Array(w).fill(true));

/** A2 sheet from a style: sample shapes rendered and cut into quarters (same as the plugin). */
export function a2FromStyle(style, t) {
  const q = t / 2;
  const a2 = new Pix(2 * t, 3 * t);
  a2.blit(renderStyle(style, rect(t, t)), 0, 0, 0, 0, t, t);
  a2.blit(renderStyle(style, rect(2 * t, 2 * t)), 0, 0, 0, t, 2 * t, 2 * t);
  for (let qy = 0; qy < 2; qy++) {
    for (let qx = 0; qx < 2; qx++) {
      const shape = rect(3 * t, 3 * t);
      const cx = qx === 0 ? 0 : 2, cy = qy === 0 ? 0 : 2;
      for (let y = cy * t; y < (cy + 1) * t; y++) for (let x = cx * t; x < (cx + 1) * t; x++) shape[y][x] = false;
      a2.blit(renderStyle(style, shape), t + qx * q, t + qy * q, t + qx * q, qy * q, q, q);
    }
  }
  return a2;
}
