// The plugin's demo screen (DemoScreen.java, /glyphwork test) rendered with the current theme.
// Coordinates and sizes match the plugin; frames are rounded down to whole tiles like
// LineBuilder.frame(w, h).

import { Pix, tint } from './pix.js';
import { tiles, renderShape } from './autotile.js';
import { drawText, textWidth, blend } from './font.js';

export const W = 176, H = 128;

const DARK = 0xff404040, WHITE = 0xffffffff;
const RED = 0xff5050, GREEN = 0x50e050, BLUE = 0x6090ff, GOLD = 0xffe070;
const WELL = ['####################', '########............'];

export class DemoState {
  constructor() {
    this.soundOn = false;
    this.count = 0;
    this.selected = -1;
    this.last = '-';
  }
}

const full = (cols, rows) => Array.from({ length: rows }, () => new Array(cols).fill(true));

/**
 * @param frames Map(name -> A2 Pix)
 * @returns { pix, targets: [{x, y, w, h, id}] }  later targets are on top
 */
export function renderDemo(frames, t, s, lang = 'de') {
  const pix = new Pix(W, H);
  const targets = [];
  const cache = new Map();
  const tileList = (name) => {
    if (!cache.has(name)) {
      try { cache.set(name, frames.has(name) ? tiles(frames.get(name), t) : null); } catch { cache.set(name, null); }
    }
    return cache.get(name);
  };
  const frame = (name, x, y, cells, tintRgb, id) => {
    const list = tileList(name);
    if (!list) return;
    const img = renderShape(list, cells, t);
    for (let yy = 0; yy < img.h; yy++) {
      for (let xx = 0; xx < img.w; xx++) {
        const v = img.get(xx, yy);
        if (v >>> 24) blend(pix, x + xx, y + yy, tintRgb == null ? v : tint(v, tintRgb));
      }
    }
    if (id) targets.push({ x, y, w: img.w, h: img.h, id });
  };
  const rect = (name, x, y, w, h, tintRgb, id) => frame(name, x, y, full(Math.floor(w / t), Math.floor(h / t)), tintRgb, id);
  const centered = (str, x, w, y, c) => drawText(pix, str, x + Math.floor((w - (textWidth(str) - 1)) / 2), y, c);
  const de = lang === 'de';

  rect('panel', 0, 0, W, H, null, 'panel');
  drawText(pix, 'Glyphwork - Demo', 8, 7, DARK);

  rect('button', 152, 4, 16, 16, RED, 'close');
  centered('x', 152, 16, 7, WHITE);

  rect('button', 8, 22, 72, 16, s.soundOn ? GREEN : RED, 'toggle');
  centered(s.soundOn ? 'Sound: ' + (de ? 'AN' : 'ON') : 'Sound: ' + (de ? 'AUS' : 'OFF'), 8, 72, 26, WHITE);

  rect('button', 96, 31, 64, 16, BLUE, 'counter');
  centered('+1', 96, 64, 35, WHITE);

  drawText(pix, (de ? 'Zähler: ' : 'Counter: ') + s.count, 8, 54, DARK);
  drawText(pix, (de ? 'Letzter Klick: ' : 'Last click: ') + s.last, 8, 65, DARK);

  for (let i = 0; i < 8; i++) rect('slot', 8 + i * 20, 78, 16, 16, i === s.selected ? GOLD : null, 'slot' + i);
  frame('slot', 8, 100, WELL.map((r) => [...r].map((c) => c === '#')), null, 'well');
  return { pix, targets };
}

/** Applies a click at demo pixel (x, y) like the plugin does; returns true if something changed. */
export function clickDemo(targets, s, x, y, lang = 'de') {
  let hit = null;
  for (const tg of targets) if (x >= tg.x && y >= tg.y && x < tg.x + tg.w && y < tg.y + tg.h) hit = tg.id;
  if (!hit) return false;
  const de = lang === 'de';
  if (hit === 'toggle') { s.soundOn = !s.soundOn; s.last = de ? 'Umschalter' : 'Toggle'; }
  else if (hit === 'counter') { s.count++; s.last = de ? 'Zähler' : 'Counter'; }
  else if (hit.startsWith('slot')) { s.selected = Number(hit.slice(4)); s.last = (de ? 'Zelle ' : 'Slot ') + (s.selected + 1); }
  else if (hit === 'well') s.last = de ? 'L-Form' : 'L shape';
  else if (hit === 'panel') s.last = 'Panel';
  else if (hit === 'close') s.last = de ? 'Schließen' : 'Close';
  return true;
}
