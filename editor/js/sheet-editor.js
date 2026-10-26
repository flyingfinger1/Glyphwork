// Pixel editor for one A2 sheet: tools, zoomed canvas, quarter/tile grid, hover info.

import { toImageData } from './pix.js';
import { quarterRole } from './autotile.js';
import { t } from './i18n.js';

export class SheetEditor {
  /**
   * @param app { frame(): Pix, frameName(): string, tileSize(): number, history, color(): number,
   *              tool(): string, changed(): void, picked(argb): void }
   */
  constructor(canvas, status, app) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.status = status;
    this.app = app;
    this.zoom = 16;
    this.showGrid = true;
    this.hover = null;
    this.drag = null; // {button, start:[x,y], last:[x,y], erase}
    this.off = document.createElement('canvas');
    canvas.addEventListener('pointerdown', (e) => this.#down(e));
    canvas.addEventListener('pointermove', (e) => this.#move(e));
    canvas.addEventListener('pointerup', (e) => this.#up(e));
    canvas.addEventListener('pointerleave', () => { this.hover = null; this.render(); this.#status(); });
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
  }

  fit(maxW, maxH) {
    const p = this.app.frame();
    this.zoom = Math.max(4, Math.min(40, Math.floor(Math.min(maxW / p.w, maxH / p.h))));
    this.render();
  }

  #pos(e) {
    const r = this.canvas.getBoundingClientRect();
    const sx = this.canvas.width / r.width;
    return [Math.floor((e.clientX - r.left) * sx / this.zoom), Math.floor((e.clientY - r.top) * sx / this.zoom)];
  }

  #down(e) {
    const p = this.app.frame();
    const [x, y] = this.#pos(e);
    if (!p.inside(x, y)) return;
    const tool = this.app.tool();
    if (tool === 'picker') {
      this.app.picked(p.get(x, y));
      return;
    }
    this.canvas.setPointerCapture(e.pointerId);
    this.app.history.record(this.app.frameName(), p);
    const erase = tool === 'eraser' || e.button === 2;
    this.drag = { start: [x, y], last: [x, y], erase, tool, base: p.clone() };
    if (tool === 'fill') {
      this.#flood(p, x, y, erase ? 0 : this.app.color());
      this.drag = null;
      this.app.changed();
    } else if (tool === 'pencil' || tool === 'eraser') {
      p.set(x, y, erase ? 0 : this.app.color());
      this.app.changed();
    }
  }

  #move(e) {
    const p = this.app.frame();
    const [x, y] = this.#pos(e);
    this.hover = p.inside(x, y) ? [x, y] : null;
    this.#status();
    const d = this.drag;
    if (d) {
      const c = d.erase ? 0 : this.app.color();
      if (d.tool === 'pencil' || d.tool === 'eraser') {
        this.#line(p, d.last[0], d.last[1], x, y, c);
        d.last = [x, y];
      } else if (d.tool === 'line' || d.tool === 'rect') {
        p.px.set(d.base.px);
        if (d.tool === 'line') this.#line(p, d.start[0], d.start[1], x, y, c);
        else this.#rect(p, d.start[0], d.start[1], x, y, c);
      }
      this.app.changed();
      return;
    }
    this.render();
  }

  #up() {
    this.drag = null;
  }

  #line(p, x0, y0, x1, y1, c) {
    const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      p.set(x0, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  }

  #rect(p, x0, y0, x1, y1, c) {
    const [ax, bx] = [Math.min(x0, x1), Math.max(x0, x1)];
    const [ay, by] = [Math.min(y0, y1), Math.max(y0, y1)];
    for (let x = ax; x <= bx; x++) { p.set(x, ay, c); p.set(x, by, c); }
    for (let y = ay; y <= by; y++) { p.set(ax, y, c); p.set(bx, y, c); }
  }

  #flood(p, x, y, c) {
    const target = p.get(x, y);
    if (target === (c >>> 0)) return;
    const stack = [[x, y]];
    while (stack.length) {
      const [cx, cy] = stack.pop();
      if (!p.inside(cx, cy) || p.get(cx, cy) !== target) continue;
      p.set(cx, cy, c);
      stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
    }
  }

  #status() {
    if (!this.hover) {
      this.status.textContent = t('hoverNone');
      return;
    }
    const q = this.app.tileSize() / 2;
    const [x, y] = this.hover;
    const col = Math.floor(x / q), row = Math.floor(y / q);
    const role = quarterRole(col, row);
    this.status.textContent = t('hoverInfo', { x, y, col, row, role: t(role === 'preview' ? 'preview_role' : role) });
  }

  render() {
    const p = this.app.frame();
    const z = this.zoom;
    const T = this.app.tileSize(), q = T / 2;
    this.canvas.width = p.w * z;
    this.canvas.height = p.h * z;
    const ctx = this.ctx;
    // checkerboard for transparency
    for (let y = 0; y < p.h * 2; y++) {
      for (let x = 0; x < p.w * 2; x++) {
        ctx.fillStyle = (x + y) & 1 ? '#3a3a44' : '#4a4a56';
        ctx.fillRect(x * z / 2, y * z / 2, z / 2, z / 2);
      }
    }
    this.off.width = p.w;
    this.off.height = p.h;
    this.off.getContext('2d').putImageData(toImageData(p), 0, 0);
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(this.off, 0, 0, p.w * z, p.h * z);

    // dim the unused preview tile
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    ctx.fillRect(0, 0, T * z, T * z);

    if (this.showGrid) {
      const line = (x0, y0, x1, y1, style, width) => {
        ctx.strokeStyle = style;
        ctx.lineWidth = width;
        ctx.beginPath();
        ctx.moveTo(x0, y0);
        ctx.lineTo(x1, y1);
        ctx.stroke();
      };
      if (z >= 10) {
        for (let x = 1; x < p.w; x++) if (x % q) line(x * z + 0.5, 0, x * z + 0.5, p.h * z, 'rgba(255,255,255,0.08)', 1);
        for (let y = 1; y < p.h; y++) if (y % q) line(0, y * z + 0.5, p.w * z, y * z + 0.5, 'rgba(255,255,255,0.08)', 1);
      }
      for (let x = 0; x <= p.w; x += q) line(x * z, 0, x * z, p.h * z, x % T ? 'rgba(255,200,0,0.45)' : 'rgba(255,200,0,0.9)', x % T ? 1 : 2);
      for (let y = 0; y <= p.h; y += q) line(0, y * z, p.w * z, y * z, y % T ? 'rgba(255,200,0,0.45)' : 'rgba(255,200,0,0.9)', y % T ? 1 : 2);
    }
    if (this.hover && !this.drag) {
      const [x, y] = this.hover;
      const qx = Math.floor(x / q) * q, qy = Math.floor(y / q) * q;
      ctx.strokeStyle = 'rgba(80,200,255,0.9)';
      ctx.lineWidth = 2;
      ctx.strokeRect(qx * z + 1, qy * z + 1, q * z - 2, q * z - 2);
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 1;
      ctx.strokeRect(x * z + 0.5, y * z + 0.5, z - 1, z - 1);
    }
  }
}
