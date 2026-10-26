// Theme model: name, tile size, ordered frames (name -> A2 Pix). Undo/redo per pixel edit,
// autosave to localStorage, import/export in the plugin's on-disk format.

import { Pix } from './pix.js';
import { STYLES, a2FromStyle } from './style.js';
import { encodePng, decodePng } from './png.js';
import { writeZip, readZip } from './zip.js';
import { t as tr } from './i18n.js';

export const REQUIRED = ['panel', 'button', 'slot'];
const STORE_KEY = 'glyphwork-editor-theme';

export function slug(s) {
  return (s || 'theme').toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || 'theme';
}

export class Theme {
  constructor(name, tileSize) {
    this.name = name;
    this.tileSize = tileSize;
    this.frames = new Map();
  }

  static fresh(name = 'Mein Skin', tileSize = 8) {
    const t = new Theme(name, tileSize);
    for (const f of REQUIRED) t.frames.set(f, a2FromStyle(STYLES[f], tileSize));
    return t;
  }

  blankFrame() {
    return new Pix(2 * this.tileSize, 3 * this.tileSize);
  }

  // ---- persistence (browser) ----

  toJSON() {
    const frames = {};
    for (const [k, p] of this.frames) {
      const bytes = new Uint8Array(p.px.buffer.slice(0));
      let s = '';
      for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
      frames[k] = btoa(s);
    }
    return { name: this.name, tileSize: this.tileSize, frames };
  }

  static fromJSON(o) {
    const t = new Theme(o.name, o.tileSize);
    for (const [k, b64] of Object.entries(o.frames)) {
      const s = atob(b64);
      const bytes = new Uint8Array(s.length);
      for (let i = 0; i < s.length; i++) bytes[i] = s.charCodeAt(i);
      t.frames.set(k, new Pix(2 * o.tileSize, 3 * o.tileSize, new Uint32Array(bytes.buffer)));
    }
    return t;
  }

  save() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(this.toJSON())); } catch { /* storage unavailable */ }
  }

  static restore() {
    try {
      const s = localStorage.getItem(STORE_KEY);
      return s ? Theme.fromJSON(JSON.parse(s)) : null;
    } catch {
      return null;
    }
  }

  // ---- plugin format ----

  async exportZip() {
    const id = slug(this.name);
    const enc = new TextEncoder();
    const frames = {};
    const entries = [];
    for (const [k, p] of this.frames) {
      frames[k] = `${k}.png`;
      entries.push({ name: `${id}/${k}.png`, data: await encodePng(p) });
    }
    const json = { name: this.name, tileSize: this.tileSize, frames };
    entries.unshift({ name: `${id}/theme.json`, data: enc.encode(JSON.stringify(json, null, 2) + '\n') });
    return { id, bytes: writeZip(entries) };
  }

  /** Theme from a ZIP (theme.json anywhere inside, PNG paths relative to it). */
  static async importZip(buffer) {
    const files = await readZip(buffer);
    const jsonName = [...files.keys()].find((n) => n === 'theme.json' || n.endsWith('/theme.json'));
    if (!jsonName) throw new Error(tr('errNoThemeJson'));
    const base = jsonName.slice(0, jsonName.length - 'theme.json'.length);
    const json = JSON.parse(new TextDecoder().decode(files.get(jsonName)));
    const t = new Theme(json.name || 'Import', json.tileSize);
    validateTileSize(t.tileSize);
    for (const [k, file] of Object.entries(json.frames || {})) {
      const data = files.get(base + file);
      if (!data) throw new Error(tr('errMissingFile', { file }));
      const p = await decodePng(data);
      checkSize(p, t.tileSize, file);
      t.frames.set(slug(k), p);
    }
    return t;
  }
}

export function validateTileSize(t) {
  if (!Number.isInteger(t) || t < 8 || t % 2 !== 0) throw new Error(tr('errTileSize'));
}

export function checkSize(p, t, label) {
  if (p.w !== 2 * t || p.h !== 3 * t) throw new Error(tr('errSize', { label, w: p.w, h: p.h, ew: 2 * t, eh: 3 * t }));
}

/** Undo/redo of whole-frame snapshots. */
export class History {
  constructor(limit = 200) {
    this.undoStack = [];
    this.redoStack = [];
    this.limit = limit;
    this.meta = () => null; // extra state saved with each step (e.g. generator settings)
  }

  /** Call before changing frame `name`; pass its current pixels. */
  record(name, before) {
    this.undoStack.push({ name, px: new Uint32Array(before.px), meta: this.meta(name) });
    if (this.undoStack.length > this.limit) this.undoStack.shift();
    this.redoStack.length = 0;
  }

  undo(theme) {
    return this.#swap(theme, this.undoStack, this.redoStack);
  }

  redo(theme) {
    return this.#swap(theme, this.redoStack, this.undoStack);
  }

  #swap(theme, from, to) {
    const e = from.pop();
    if (!e) return null;
    const frame = theme.frames.get(e.name);
    if (!frame || frame.px.length !== e.px.length) return null;
    to.push({ name: e.name, px: new Uint32Array(frame.px), meta: this.meta(e.name) });
    frame.px.set(e.px);
    this.lastMeta = e.meta;
    return e.name;
  }

  clear() {
    this.undoStack.length = 0;
    this.redoStack.length = 0;
  }
}
