// Live preview: a cell grid the user shapes by clicking, rendered with the current frame's tiles.

import { Pix, toImageData } from './pix.js';
import { tiles, renderShape } from './autotile.js';

const COLS = 14, ROWS = 9;

const PRESETS = {
  presetRect: ['..........', '.########.', '.########.', '.########.', '.########.', '..........'],
  presetL: ['.######...', '.######...', '.##.......', '.##.......', '.##.......'],
  presetT: ['.########.', '.########.', '....##....', '....##....', '....##....'],
  presetRing: ['.########.', '.########.', '.##....##.', '.##....##.', '.########.', '.########.'],
  presetPlus: ['....##....', '....##....', '.########.', '.########.', '....##....', '....##....'],
  presetStairs: ['.##.......', '.####.....', '.######...', '.########.', '.########.'],
};

export const PRESET_KEYS = [...Object.keys(PRESETS), 'presetClear'];

export class Preview {
  /** @param app { frame(): Pix, tileSize(): number } */
  constructor(canvas, tilesCanvas, app) {
    this.canvas = canvas;
    this.tilesCanvas = tilesCanvas;
    this.app = app;
    this.zoom = 3;
    this.bg = 'dark';
    this.tintOn = false;
    this.tint = 0xff5050;
    this.off = document.createElement('canvas');
    this.paintValue = null;
    this.cells = Array.from({ length: ROWS }, () => new Array(COLS).fill(false));
    this.preset('presetL');
    canvas.addEventListener('pointerdown', (e) => {
      const c = this.#cell(e);
      if (!c) return;
      canvas.setPointerCapture(e.pointerId);
      this.paintValue = !this.cells[c[1]][c[0]];
      this.#paint(c);
    });
    canvas.addEventListener('pointermove', (e) => {
      if (this.paintValue === null) return;
      const c = this.#cell(e);
      if (c) this.#paint(c);
    });
    canvas.addEventListener('pointerup', () => { this.paintValue = null; });
  }

  preset(key) {
    for (const row of this.cells) row.fill(false);
    const p = PRESETS[key];
    if (p) {
      p.forEach((line, r) => [...line].forEach((ch, c) => {
        if (r + 1 < ROWS && c + 2 < COLS) this.cells[r + 1][c + 2] = ch === '#';
      }));
    }
    this.render();
  }

  #cell(e) {
    const r = this.canvas.getBoundingClientRect();
    const s = this.canvas.width / r.width;
    const size = this.app.tileSize() * this.zoom;
    const c = Math.floor((e.clientX - r.left) * s / size), row = Math.floor((e.clientY - r.top) * s / size);
    return c >= 0 && c < COLS && row >= 0 && row < ROWS ? [c, row] : null;
  }

  #paint([c, r]) {
    if (this.cells[r][c] === this.paintValue) return;
    this.cells[r][c] = this.paintValue;
    this.render();
  }

  #background(ctx, w, h, unit) {
    if (this.bg === 'light') {
      ctx.fillStyle = '#d8dce4';
      ctx.fillRect(0, 0, w, h);
    } else if (this.bg === 'dark') {
      ctx.fillStyle = '#1c1f26';
      ctx.fillRect(0, 0, w, h);
    } else {
      for (let y = 0; y < h; y += unit) {
        for (let x = 0; x < w; x += unit) {
          ctx.fillStyle = ((x + y) / unit) & 1 ? '#3a3a44' : '#4a4a56';
          ctx.fillRect(x, y, unit, unit);
        }
      }
    }
  }

  #draw(ctx, pix, x, y, z) {
    this.off.width = pix.w;
    this.off.height = pix.h;
    this.off.getContext('2d').putImageData(toImageData(pix, this.tintOn ? this.tint : 0xffffff), 0, 0);
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(this.off, x, y, pix.w * z, pix.h * z);
  }

  render() {
    const T = this.app.tileSize();
    let list;
    try {
      list = tiles(this.app.frame(), T);
    } catch {
      return;
    }
    const z = this.zoom, size = T * z;
    const ctx = this.canvas.getContext('2d');
    this.canvas.width = COLS * size;
    this.canvas.height = ROWS * size;
    this.#background(ctx, this.canvas.width, this.canvas.height, size / 2);
    ctx.strokeStyle = this.bg === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.06)';
    ctx.lineWidth = 1;
    for (let c = 0; c <= COLS; c++) { ctx.beginPath(); ctx.moveTo(c * size + 0.5, 0); ctx.lineTo(c * size + 0.5, ROWS * size); ctx.stroke(); }
    for (let r = 0; r <= ROWS; r++) { ctx.beginPath(); ctx.moveTo(0, r * size + 0.5); ctx.lineTo(COLS * size, r * size + 0.5); ctx.stroke(); }
    this.#draw(ctx, renderShape(list, this.cells, T), 0, 0, z);

    // all 47 tiles, 12 per row
    const tz = 3, gap = 3, per = 12, ts = T * tz;
    const tc = this.tilesCanvas;
    tc.width = per * (ts + gap) + gap;
    tc.height = Math.ceil(list.length / per) * (ts + gap) + gap;
    const tctx = tc.getContext('2d');
    this.#background(tctx, tc.width, tc.height, ts / 2);
    list.forEach((tile, i) => this.#draw(tctx, tile, gap + (i % per) * (ts + gap), gap + Math.floor(i / per) * (ts + gap), tz));
  }
}

export { Pix };
