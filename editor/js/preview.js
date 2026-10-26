// Live preview: a cell grid rendered with the current frame's tiles. Two modes:
//  - shape: click/drag toggles cells
//  - paint: draw directly on the rendered window; every pixel maps to its A2 quarter, so one
//           stroke changes all places that use the same quarter.

import { Pix, toImageData } from './pix.js';
import { tiles, renderShape, mask, quarterFor, quarterRole } from './autotile.js';
import { line, rect, floodArea } from './tools.js';
import { t } from './i18n.js';

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
  /**
   * @param app { frame(), frameName(), tileSize(), tool(), color(), history, plot(x,y,c),
   *              changed(), picked(argb) }
   */
  constructor(canvas, tilesCanvas, status, app) {
    this.canvas = canvas;
    this.tilesCanvas = tilesCanvas;
    this.status = status;
    this.app = app;
    this.zoom = 3;
    this.bg = 'dark';
    this.tintOn = false;
    this.tint = 0xff5050;
    this.mode = 'shape';
    this.hover = null; // preview pixel [x, y]
    this.off = document.createElement('canvas');
    this.paintValue = null;
    this.drag = null;
    this.cells = Array.from({ length: ROWS }, () => new Array(COLS).fill(false));
    this.preset('presetRing');
    canvas.addEventListener('pointerdown', (e) => this.#down(e));
    canvas.addEventListener('pointermove', (e) => this.#move(e));
    canvas.addEventListener('pointerup', () => { this.paintValue = null; this.drag = null; });
    canvas.addEventListener('pointerleave', () => { this.hover = null; this.render(); this.#status(); });
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
  }

  setMode(mode) {
    this.mode = mode;
    this.canvas.classList.toggle('painting', mode === 'paint');
    this.render();
    this.#status();
  }

  setCells(cells) {
    for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) this.cells[r][c] = !!cells[r]?.[c];
    this.render();
  }

  preset(key) {
    for (const row of this.cells) row.fill(false);
    const p = PRESETS[key];
    if (p) {
      p.forEach((ln, r) => [...ln].forEach((ch, c) => {
        if (r + 1 < ROWS && c + 2 < COLS) this.cells[r + 1][c + 2] = ch === '#';
      }));
    }
    this.render();
  }

  // ---- coordinates ----

  #pixel(e) {
    const r = this.canvas.getBoundingClientRect();
    const s = this.canvas.width / r.width;
    return [Math.floor((e.clientX - r.left) * s / this.zoom), Math.floor((e.clientY - r.top) * s / this.zoom)];
  }

  /** Preview pixel -> {x, y, col, row} in the A2 sheet, or null outside the shape. */
  toA2(px, py) {
    const T = this.app.tileSize(), q = T / 2;
    const c = Math.floor(px / T), r = Math.floor(py / T);
    if (c < 0 || r < 0 || c >= COLS || r >= ROWS || !this.cells[r][c]) return null;
    const tx = px - c * T, ty = py - r * T;
    const [col, row] = quarterFor(mask(this.cells, c, r), tx < q ? 0 : 1, ty < q ? 0 : 1);
    return { x: col * q + (tx % q), y: row * q + (ty % q), col, row };
  }

  // ---- input ----

  #down(e) {
    const [px, py] = this.#pixel(e);
    if (this.mode === 'shape') {
      const T = this.app.tileSize();
      const c = Math.floor(px / T), r = Math.floor(py / T);
      if (c < 0 || r < 0 || c >= COLS || r >= ROWS) return;
      try { this.canvas.setPointerCapture(e.pointerId); } catch { /* synthetic or lost pointer */ }
      this.paintValue = !this.cells[r][c];
      this.#toggle(c, r);
      return;
    }
    const hit = this.toA2(px, py);
    if (!hit) return;
    const tool = this.app.tool();
    const frame = this.app.frame();
    if (tool === 'picker') { this.app.picked(frame.get(hit.x, hit.y)); return; }
    try { this.canvas.setPointerCapture(e.pointerId); } catch { /* synthetic or lost pointer */ }
    this.app.history.record(this.app.frameName(), frame);
    const erase = tool === 'eraser' || e.button === 2;
    const color = erase ? 0 : this.app.color();
    if (tool === 'fill') {
      const q = this.app.tileSize() / 2;
      for (const [fx, fy] of floodArea(frame, hit.x, hit.y, [hit.col * q, hit.row * q, q, q])) this.app.plot(fx, fy, color);
      this.app.changed();
      return;
    }
    this.drag = { tool, color, start: [px, py], last: [px, py], base: frame.clone() };
    if (tool === 'pencil' || tool === 'eraser') {
      this.app.plot(hit.x, hit.y, color);
      this.app.changed();
    }
  }

  #move(e) {
    const [px, py] = this.#pixel(e);
    this.hover = [px, py];
    if (this.mode === 'shape') {
      if (this.paintValue !== null) {
        const T = this.app.tileSize();
        const c = Math.floor(px / T), r = Math.floor(py / T);
        if (c >= 0 && r >= 0 && c < COLS && r < ROWS) this.#toggle(c, r);
      }
      return;
    }
    const d = this.drag;
    if (d) {
      const plot = (x, y) => { const h = this.toA2(x, y); if (h) this.app.plot(h.x, h.y, d.color); };
      if (d.tool === 'pencil' || d.tool === 'eraser') {
        line(d.last[0], d.last[1], px, py, plot);
        d.last = [px, py];
      } else if (d.tool === 'line' || d.tool === 'rect') {
        this.app.frame().px.set(d.base.px);
        (d.tool === 'line' ? line : rect)(d.start[0], d.start[1], px, py, plot);
      }
      this.app.changed();
      return;
    }
    this.#status();
    this.render();
  }

  #toggle(c, r) {
    if (this.cells[r][c] === this.paintValue) return;
    this.cells[r][c] = this.paintValue;
    this.render();
  }

  #status() {
    if (this.mode === 'shape') {
      this.status.textContent = t('shapeHint');
      return;
    }
    const hit = this.hover && this.toA2(...this.hover);
    if (!hit) {
      this.status.textContent = t('paintHint');
      return;
    }
    const role = quarterRole(hit.col, hit.row);
    this.status.textContent = t('paintInfo', { col: hit.col, row: hit.row, role: t(role), n: this.#sameQuarter(hit).length });
  }

  /** Top-left preview pixels of all quadrants that use the hovered quarter. */
  #sameQuarter(hit) {
    const T = this.app.tileSize(), q = T / 2;
    const out = [];
    for (let r = 0; r < ROWS; r++) {
      for (let c = 0; c < COLS; c++) {
        if (!this.cells[r][c]) continue;
        const m = mask(this.cells, c, r);
        for (let qy = 0; qy < 2; qy++) {
          for (let qx = 0; qx < 2; qx++) {
            const [col, row] = quarterFor(m, qx, qy);
            if (col === hit.col && row === hit.row) out.push([c * T + qx * q, r * T + qy * q]);
          }
        }
      }
    }
    return out;
  }

  // ---- drawing ----

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
    const T = this.app.tileSize(), q = T / 2;
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
    if (this.mode === 'shape') {
      ctx.strokeStyle = this.bg === 'light' ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.06)';
      ctx.lineWidth = 1;
      for (let c = 0; c <= COLS; c++) { ctx.beginPath(); ctx.moveTo(c * size + 0.5, 0); ctx.lineTo(c * size + 0.5, ROWS * size); ctx.stroke(); }
      for (let r = 0; r <= ROWS; r++) { ctx.beginPath(); ctx.moveTo(0, r * size + 0.5); ctx.lineTo(COLS * size, r * size + 0.5); ctx.stroke(); }
    }
    this.#draw(ctx, renderShape(list, this.cells, T), 0, 0, z);

    if (this.mode === 'paint' && this.hover && !this.drag) {
      const hit = this.toA2(...this.hover);
      if (hit) {
        ctx.lineWidth = 2;
        ctx.strokeStyle = 'rgba(80,200,255,0.95)';
        for (const [x, y] of this.#sameQuarter(hit)) ctx.strokeRect(x * z + 1, y * z + 1, q * z - 2, q * z - 2);
        ctx.lineWidth = 1;
        ctx.strokeStyle = '#fff';
        ctx.strokeRect(this.hover[0] * z + 0.5, this.hover[1] * z + 0.5, z - 1, z - 1);
      }
    }

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
