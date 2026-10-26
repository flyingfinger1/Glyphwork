// Port of de.flyingfinger.glyphwork.theme.Style (procedural outline/bevel/fill) and Autotile.a2.

import { Pix } from './pix.js';

export const STYLES = {
  panel: { fill: 0xffc6c6c6, outline: 0xff000000, round: true, lightWidth: 2, light: 0xffffffff, shadeWidth: 2, shade: 0xff555555 },
  button: { fill: 0xffc8c8c8, outline: 0xff000000, round: false, lightWidth: 1, light: 0xffffffff, shadeWidth: 2, shade: 0xff6e6e6e },
  slot: { fill: 0xff8b8b8b, outline: null, round: false, lightWidth: 1, light: 0xff373737, shadeWidth: 1, shade: 0xffffffff },
};

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
