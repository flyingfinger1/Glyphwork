// Draws a YAML menu (as parsed into plain objects) like the plugin does (SceneRenderer.java):
// frames from the skin, text with the editor's stand-in font, icons and heads as placeholders
// (Minecraft textures cannot be shipped). No DOM: returns pixels plus boxes for selection and
// click/hover regions for the preview.

import { Pix, tint } from '../pix.js';
import { tiles, renderShape } from '../autotile.js';
import { drawText, textWidth, blend } from '../font.js';


export const LINE = 9;
export const HEAD = 8;
export const ICON = 16;
const WHITE = 0xffffff;
const DARK = 0x404040;
const BUTTON_GRAY = 0x9c9c9c;
const DISABLED = 0x5a5a5a;
const DISABLED_TEXT = 0xa0a0a0;

/** Adventure's named colors (NamedTextColor.NAMES). */
export const NAMED = {
  black: 0x000000, dark_blue: 0x0000aa, dark_green: 0x00aa00, dark_aqua: 0x00aaaa, dark_red: 0xaa0000,
  dark_purple: 0xaa00aa, gold: 0xffaa00, gray: 0xaaaaaa, dark_gray: 0x555555, blue: 0x5555ff,
  green: 0x55ff55, aqua: 0x55ffff, red: 0xff5555, light_purple: 0xff55ff, yellow: 0xffff55, white: 0xffffff,
};

/** "#RRGGBB" or a color name -> 0xRRGGBB, or undefined if missing/invalid. */
export function color(v) {
  if (v == null) return undefined;
  const s = String(v).trim();
  if (/^#[0-9a-f]{6}$/i.test(s)) return parseInt(s.slice(1), 16);
  return NAMED[s.toLowerCase()];
}

const int = (v, d) => {
  if (v == null || v === '') return d;
  const n = Number(v);
  return Number.isFinite(n) ? Math.trunc(n) : d;
};
const num = (v, d) => {
  if (v == null || v === '') return d;
  const n = Number(v);
  return Number.isFinite(n) ? n : d;
};
const list = (v) => (v == null ? [] : Array.isArray(v) ? v.map(String) : [String(v)]);

/** Sample entries for dynamic lists (items-from), keys like Sources.java. */
export const SAMPLES = {
  'online-players': [
    { '': 'Steve', name: 'Steve', world: 'world', uuid: '8667ba71-b85a-4004-af54-457a9734eed7', icon: 'head:Steve', head: 'head:Steve' },
    { '': 'Alex', name: 'Alex', world: 'world_nether', uuid: 'ec561538-f3fd-461d-aff5-086b22154bce', icon: 'head:Alex', head: 'head:Alex' },
    { '': 'Notch', name: 'Notch', world: 'world', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', icon: 'head:Notch', head: 'head:Notch' },
  ],
  worlds: [
    { '': 'world', name: 'world', players: '2', environment: 'normal', icon: 'grass_block' },
    { '': 'world_nether', name: 'world_nether', players: '1', environment: 'nether', icon: 'netherrack' },
    { '': 'world_the_end', name: 'world_the_end', players: '0', environment: 'the_end', icon: 'end_stone' },
  ],
  // stands in for every rss:<address> (the designer does not load feeds)
  rss: [1, 2, 3, 4, 5].map((i) => ({
    '': `News ${i}`, title: `News ${i}`, summary: `Text of entry ${i}`, text: `Text of entry ${i}.

A second paragraph with more words, so that the detail view has something to wrap and scroll.`,
    date: `2${i}.10.2026 18:00`,
    link: `https://example.org/news/${i}`, author: 'Admin', feed: 'Server News',
  })),
};

/** Sample key of an items-from value ("rss:..." → "rss"). */
export const sampleKey = (from) => (/^rss:/i.test(String(from ?? '').trim()) ? 'rss' : String(from ?? '').trim().toLowerCase());

// ---------------------------------------------------------------- placeholders and conditions

/**
 * Fills a text for the preview: %item...% from a list entry, %menu_x% from the preview variables
 * (or the menu's defaults), %player%, %world%, %online%, %time%. Other placeholders stay.
 */
export function fill(text, ctx, item) {
  let s = String(text ?? '');
  if (!s.includes('%')) return s;
  if (item) s = s.replace(/%item(?:_([a-z0-9_-]+))?%/g, (m, k) => item[k ?? ''] ?? m);
  s = s.replace(/%menu_([a-z0-9_-]+)%/g, (m, k) => varValue(ctx, k));
  const now = new Date();
  const p2 = (n) => String(n).padStart(2, '0');
  return s.replace(/%player%/g, ctx.player ?? 'Steve').replace(/%world%/g, 'world')
    .replace(/%online%/g, String(ctx.online ?? 1))
    .replace(/%time%/g, `${p2(now.getHours())}:${p2(now.getMinutes())}:${p2(now.getSeconds())}`);
}

export function varValue(ctx, name) {
  if (ctx.vars && Object.prototype.hasOwnProperty.call(ctx.vars, name)) return String(ctx.vars[name] ?? '');
  const d = ctx.menu?.variables?.[name];
  return d == null ? '' : fill(String(d), { ...ctx, vars: {} });
}

const OP = /\s(==|!=|>=|<=|>|<|!contains|contains)\s/;
const unquote = (s) => (s.length >= 2 && ((s[0] === '"' && s.at(-1) === '"') || (s[0] === "'" && s.at(-1) === "'")) ? s.slice(1, -1) : s);
const number = (s) => {
  if (s === '') return null;
  if (/^-?[0-9]+(\.[0-9]+)?$/.test(s) || /^-?\.[0-9]+$/.test(s)) return Number(s);
  if (/^-?[0-9]{1,3}(,[0-9]{3})+(\.[0-9]+)?$/.test(s)) return Number(s.replace(/,/g, ''));
  return null;
};

/** Parses one condition; returns null if it is malformed. */
export function parseCondition(text) {
  const s = String(text).trim();
  const m = OP.exec(s);
  if (!m) return null;
  const l = s.slice(0, m.index).trim(), r = s.slice(m.index + m[0].length).trim();
  if (!l || !r) return null;
  return { left: unquote(l), op: m[1], right: unquote(r) };
}

export function test(cond, fillFn) {
  const l = fillFn(cond.left).trim(), r = fillFn(cond.right).trim();
  const a = number(l), b = number(r);
  const nums = a != null && b != null;
  switch (cond.op) {
    case '==': return nums ? a === b : l.toLowerCase() === r.toLowerCase();
    case '!=': return nums ? a !== b : l.toLowerCase() !== r.toLowerCase();
    case '>': return nums && a > b;
    case '>=': return nums && a >= b;
    case '<': return nums && a < b;
    case '<=': return nums && a <= b;
    case 'contains': return l.toLowerCase().includes(r.toLowerCase());
    case '!contains': return !l.toLowerCase().includes(r.toLowerCase());
    default: return false;
  }
}

/** All conditions hold (malformed ones count as false). */
export function allHold(conds, fillFn) {
  for (const c of list(conds)) {
    const p = parseCondition(c);
    if (!p || !test(p, fillFn)) return false;
  }
  return true;
}

// ---------------------------------------------------------------- templates

const TVAR = /\{([a-zA-Z0-9_-]+)(?:\|([^}]*))?}/g;

function fillTemplate(v, values, used) {
  if (typeof v === 'string') {
    return v.replace(TVAR, (m, k, d) => {
      if (values[k] != null) { used.add(k); return String(values[k]); }
      return d != null ? d : m;
    });
  }
  if (Array.isArray(v)) return v.map((x) => fillTemplate(x, values, used));
  if (v && typeof v === 'object') {
    const o = {};
    for (const [k, x] of Object.entries(v)) o[k] = fillTemplate(x, values, used);
    return o;
  }
  return v;
}

/**
 * Expands "use:" entries like Templates.java. Returns [{el, top, errors}] where top is the index
 * of the entry in menu.elements it came from.
 */
export const TAB_HEADER = 16;
const tabsKey = (el, top) => String(el.id ?? 'tabs' + top);
const tabId = (tb, i) => String(tb?.id ?? 'tab' + (i + 1)).trim().toLowerCase();

/** The id of the active tab (the first one when nothing or an unknown id is set). */
export function activeTab(el, key, ctx) {
  const ids = (el._tabs || []).map((t) => t.id);
  const want = el.var != null ? varValue(ctx, String(el.var).toLowerCase()).trim() : ctx.pages?.['tab:' + key];
  return ids.includes(want) ? want : ids[0];
}

export function expand(menu, shared = {}) {
  const templates = {};
  for (const [k, v] of Object.entries(shared || {})) templates[k.toLowerCase()] = v;
  for (const [k, v] of Object.entries(menu.templates || {})) templates[k.toLowerCase()] = v;
  const out = [];
  const errors = [];
  const walk = (el, dx, dy, showIf, depth, top, inTab = []) => {
    if (!el || typeof el !== 'object') return;
    if (el.use == null) {
      const m = { ...el };
      if (dx || dy) { m.x = int(el.x, 0) + dx; m.y = int(el.y, 0) + dy; }
      if (showIf.length) m['show-if'] = [...showIf, ...list(el['show-if'])];
      const kids = String(el.type ?? '').toLowerCase() === 'tabs' ? m.tabs : null;
      if (kids) delete m.tabs;
      out.push({ el: m, top, inTab });
      if (Array.isArray(kids)) {
        // like Menus.java: tab elements are placed relative to the panel below the tab buttons
        const key = tabsKey(el, top);
        m._tabs = kids.map((tb, i) => ({ id: tabId(tb, i), title: tb?.title, icon: tb?.icon }));
        kids.forEach((tb, i) => {
          const mark = [...inTab, { key, id: tabId(tb, i) }];
          for (const child of (Array.isArray(tb?.elements) ? tb.elements : [])) {
            walk(child, int(m.x, 0), int(m.y, 0) + TAB_HEADER, [], depth + 1, top, mark);
          }
        });
      }
      return;
    }
    const name = String(el.use).toLowerCase();
    const tpl = templates[name];
    if (tpl == null || depth >= 8) { errors.push({ top, text: `use: ${name}` }); out.push({ el: { type: '_missing', x: el.x, y: el.y, name }, top }); return; }
    const values = {};
    for (const [k, v] of Object.entries(el)) if (!['use', 'x', 'y', 'show-if'].includes(k) && v != null) values[k] = v;
    const used = new Set();
    let filled = fillTemplate(tpl, values, used);
    const unused = Object.keys(values).filter((k) => !used.has(k));
    if (!Array.isArray(filled) && filled && typeof filled === 'object' && unused.length) {
      filled = { ...filled };
      for (const k of unused) filled[k] = el[k];
    }
    const cond = [...showIf, ...list(el['show-if'])];
    const x = dx + int(el.x, 0), y = dy + int(el.y, 0);
    if (Array.isArray(filled)) filled.forEach((g) => walk(g, x, y, cond, depth + 1, top, inTab));
    else walk(filled, x, y, cond, depth + 1, top, inTab);
  };
  (menu.elements || []).forEach((el, i) => walk(el, 0, 0, [], 0, i));
  return { items: out, errors };
}

// ---------------------------------------------------------------- drawing helpers

class Canvas {
  constructor(w, h, frames, t, text) {
    this.pix = new Pix(Math.max(1, w), Math.max(1, h));
    this.frames = frames;
    this.t = t;
    this.text = text;
    this.cache = new Map();
  }

  tileList(name) {
    if (!this.cache.has(name)) {
      let v = null;
      try { v = this.frames.has(name) ? tiles(this.frames.get(name), this.t) : null; } catch { v = null; }
      this.cache.set(name, v);
    }
    return this.cache.get(name);
  }

  tileSize() {
    return this.t;
  }

  /** Autotiled shape at (x, y); returns false if the skin has no such frame. */
  shape(name, x, y, cells, tintRgb) {
    const tl = this.tileList(name);
    if (!tl || !cells.length) return false;
    const img = renderShape(tl, cells, this.t);
    for (let yy = 0; yy < img.h; yy++) {
      for (let xx = 0; xx < img.w; xx++) {
        const v = img.get(xx, yy);
        if (v >>> 24) blend(this.pix, x + xx, y + yy, tintRgb == null ? v : tint(v, tintRgb));
      }
    }
    return true;
  }

  /** Rectangle frame, rounded down (or up) to whole tiles, at least one. */
  rect(name, x, y, w, h, tintRgb, up = false) {
    const t = this.t;
    const cols = Math.max(1, up ? Math.ceil(w / t) : Math.floor(w / t));
    const rows = Math.max(1, up ? Math.ceil(h / t) : Math.floor(h / t));
    return this.shape(name, x, y, Array.from({ length: rows }, () => new Array(cols).fill(true)), tintRgb);
  }

  textOut(s, x, y, rgb, shadow = false) {
    if (!s) return;
    if (shadow) drawText(this.pix, s, x + 1, y + 1, (0xff000000 | ((rgb & 0xfcfcfc) >> 2)) >>> 0);
    drawText(this.pix, s, x, y, (0xff000000 | rgb) >>> 0);
  }

  centered(s, x, w, y, rgb, shadow = false) {
    this.textOut(s, x + Math.floor((w - visibleWidth(s)) / 2), y, rgb, shadow);
  }

  fillRect(x, y, w, h, argb) {
    for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) blend(this.pix, xx, yy, argb);
  }
}

export const visibleWidth = (s) => Math.max(0, textWidth(s) - 1);

/** Cuts s to maxWidth pixels with "..." like LineBuilder.fit. */
export function fit(s, maxWidth) {
  if (textWidth(s) - 1 <= maxWidth) return s;
  const budget = maxWidth - textWidth('...');
  let out = '', w = 0;
  for (const ch of s) {
    const cw = textWidth(ch);
    if (w + cw > budget) break;
    out += ch;
    w += cw;
  }
  return out.trimEnd() + '...';
}

/** Words in lines no wider than width px (SceneRenderer.wrapText). */
export function wrapText(text, width) {
  const out = [];
  for (const para of String(text).split('\n')) {
    let cur = '';
    for (const word of para.split(' ')) {
      const next = cur === '' ? word : cur + ' ' + word;
      if (visibleWidth(next) <= width) { cur = next; continue; }
      if (cur !== '') { out.push(cur); cur = ''; }
      let w = word;
      while (visibleWidth(w) > width && w.length > 1) {
        let n = w.length - 1;
        while (n > 1 && visibleWidth(w.slice(0, n)) > width) n--;
        out.push(w.slice(0, n));
        w = w.slice(n);
      }
      cur = w;
    }
    out.push(cur);
  }
  return out;
}

/** "diamond" -> "Dia", "gold_ingot" -> "GI" (SceneRenderer.shortName). */
export function shortName(item) {
  if (!item) return '';
  const id = item.includes(':') ? item.slice(item.indexOf(':') + 1) : item;
  const parts = id.split('_').filter(Boolean);
  if (parts.length === 1) return parts[0][0].toUpperCase() + parts[0].slice(1, 3);
  return parts.slice(0, 3).map((p) => p[0].toUpperCase()).join('');
}

export const isHead = (item) => typeof item === 'string' && /^head:/i.test(item) && item.slice(5).trim() !== '';

/** Pixels the plugin moves an element so that its head (wanted at headTop) sits on the 9 px grid. */
export function headShift(headTop) {
  const index = Math.max(1, Math.round((headTop + 1) / LINE));
  return index * LINE - 1 - headTop;
}

function drawHead(c, x, top, s = 1) {
  // a stand-in face (s px per face pixel): the server paints the real skin here
  c.fillRect(x, top, 8 * s, 8 * s, 0xff6b4a2f);
  c.fillRect(x, top + 2 * s, 8 * s, 6 * s, 0xffc7926b);
  c.fillRect(x + s, top + 4 * s, 2 * s, s, 0xffffffff);
  c.fillRect(x + 5 * s, top + 4 * s, 2 * s, s, 0xffffffff);
  c.fillRect(x + 2 * s, top + 4 * s, s, s, 0xff3a3a8a);
  c.fillRect(x + 5 * s, top + 4 * s, s, s, 0xff3a3a8a);
  c.fillRect(x + 3 * s, top + 6 * s, 2 * s, s, 0xff7a4a3a);
}

const headSizeOf = (v) => Math.max(8, Math.min(32, Math.floor(int(v, 8) / 8) * 8));

/**
 * Item icon (16 x 16): the real textures when the user loaded a client jar (c.icons), otherwise a
 * framed box with the short name. tintRgb replaces the tint of tinted layers (like Icon.tint).
 */
function drawIcon(c, item, x, y, tintRgb = null) {
  if (!item) return false;
  if (isHead(item)) {
    const top = (Math.max(1, Math.round((y + ICON / 2 - HEAD / 2 + 1) / LINE))) * LINE - 1;
    drawHead(c, x + (ICON - HEAD) / 2, top);
    return true;
  }
  const layers = c.icons ? c.icons(item) : null;
  if (layers) {
    for (const l of layers) {
      const tnt = l.tint == null ? null : (tintRgb ?? l.tint);
      for (let yy = 0; yy < ICON; yy++) {
        for (let xx = 0; xx < ICON; xx++) {
          const v = l.img.get(Math.floor(xx * l.frame / ICON), Math.floor(yy * l.frame / ICON));
          if (v >>> 24) blend(c.pix, x + xx, y + yy, tnt == null ? v : tint(v, tnt));
        }
      }
    }
    return true;
  }
  c.fillRect(x + 1, y + 1, 14, 14, 0x55ffffff);
  c.fillRect(x + 1, y + 1, 14, 1, 0x88ffffff);
  c.fillRect(x + 1, y + 14, 14, 1, 0x55000000);
  const n = shortName(item).slice(0, 2);
  c.centered(n, x, ICON, y + 4, 0x303030);
  return true;
}

function drawCount(c, count, x, y, bottom = Infinity) {
  const s = count == null ? '' : String(count).trim();
  if (!s || s === '1') return;
  c.textOut(s, x + 17 - textWidth(s), Math.min(y + 9, bottom - 8), WHITE, true);
}

// ---------------------------------------------------------------- the menu

/**
 * @param menu   parsed YAML menu
 * @param skin   { frames: Map(name -> A2 Pix), tileSize, text (ARGB) }
 * @param ctx    { vars: {name: value}, pages: {id: n}, player, online, shared (templates), maxHeight }
 * @returns {pix, boxes: [{top, x, y, w, h}], regions: [{x, y, w, h, tip, act}], hints: [{top, text}],
 *           errors: [{top, text}]}
 *   act: {type:'actions', list, item} | {type:'set', var, value, then} | {type:'page', id, page}
 */
export function renderMenu(menu, skin, ctx = {}) {
  const W = int(menu.width, 176), H = int(menu.height, 96);
  const c = new Canvas(W, H, skin.frames, skin.tileSize, skin.text);
  c.icons = ctx.icons || null;
  const skinText = (rgb) => (rgb === DARK ? (skin.text & 0xffffff) : rgb);
  ctx = { ...ctx, menu };
  const boxes = [], regions = [], hints = [];
  const { items, errors } = expand(menu, ctx.shared);
  const bg = menu.background == null ? 'panel' : String(menu.background);
  if (bg.toLowerCase() !== 'none') {
    if (!c.rect(bg, 0, 0, W, H, null, true)) errors.push({ top: -1, text: `frame '${bg}'` });
  }
  const region = (x, y, w, h, tip, act) => { if (tip || act) regions.push({ x, y, w, h, tip, act }); };
  const actionsOf = (el, item) => (el.actions == null ? null : { type: 'actions', list: list(el.actions), item });

  const active = {};
  for (const { el, top, inTab } of items) {
    if (inTab && inTab.some((m) => active[m.key] !== m.id)) continue;
    const f = (s, item) => fill(s, ctx, item);
    const type = String(el.type ?? '').toLowerCase();
    if (type === '_missing') {
      boxes.push({ top, x: int(el.x, 0), y: int(el.y, 0), w: 32, h: 16, missing: true });
      continue;
    }
    if (el['show-if'] != null && !allHold(el['show-if'], (s) => f(s))) {
      boxes.push({ top, x: int(el.x, 0), y: int(el.y, 0), w: 0, h: 0, hidden: true });
      continue;
    }
    const x = int(el.x, 0), y = int(el.y, 0);
    const tip = el.tooltip != null ? f(el.tooltip) : null;
    try {
      switch (type) {
        case 'label': {
          const w0 = int(el.width, -1);
          const wrap = el.wrap === true || el.wrap === 'true';
          // inline MiniMessage tags are left out in the preview; the switches are shown
          const full = plain(f(el.text ?? ''));
          const look = { bold: on(el.bold), italic: on(el.italic), underline: on(el.underline), strike: on(el.strikethrough) };
          if ((wrap && w0 > 0) || full.includes('\n')) {
            const lh = Math.max(8, int(el['line-height'], 10));
            const lines = wrap && w0 > 0 ? wrapText(full, w0) : full.split('\n');
            const rgb = skinText(color(el.color) ?? DARK);
            const align = String(el.align ?? 'left').toLowerCase();
            lines.forEach((ln, i) => {
              const part = w0 > 0 ? fit(ln, w0) : ln;
              const vw = visibleWidth(part);
              const lx = x + (w0 <= 0 ? 0 : align === 'center' ? Math.floor((w0 - vw) / 2) : align === 'right' ? w0 - vw : 0);
              styledOut(c, part, lx, y + i * lh, rgb, el.shadow === true || el.shadow === 'true', look);
            });
            const bw = w0 > 0 ? w0 : Math.max(4, ...lines.map((l) => visibleWidth(l)));
            boxes.push({ top, x, y, w: bw, h: (lines.length - 1) * lh + 8 });
            break;
          }
          let s = full;
          let lx = x;
          const w = w0;
          if (w > 0) {
            s = fit(s, w);
            const vw = visibleWidth(s);
            const align = String(el.align ?? 'left').toLowerCase();
            lx += align === 'center' ? Math.floor((w - vw) / 2) : align === 'right' ? w - vw : 0;
          }
          const rgb = skinText(color(el.color) ?? DARK);
          styledOut(c, s, lx, y, rgb, el.shadow === true || el.shadow === 'true', look);
          boxes.push({ top, x, y, w: w > 0 ? w : Math.max(4, visibleWidth(s) + (look.bold ? s.length : 0)), h: 8 });
          break;
        }
        case 'button': {
          const bw = int(el.width, 64), bh = int(el.height, 16);
          const frameName = String(el.frame ?? 'button');
          const t = c.tileSize();
          const h = Math.max(t, Math.floor(bh / t) * t);
          const icon = el.icon != null ? f(el.icon).trim() : null;
          let by = y;
          if (isHead(icon)) {
            const dy = headShift(y + Math.floor((h - HEAD) / 2));
            if (dy) hints.push({ top, text: `head: y ${dy > 0 ? '+' : ''}${dy} → y=${y + dy}` });
            by += dy;
          }
          const enabled = allHold(el['enabled-if'] ?? [], (s) => f(s));
          c.rect(frameName, x, by, bw, bh, enabled ? (color(el.tint) ?? BUTTON_GRAY) : DISABLED);
          let tx = x, tw = Math.max(t, Math.floor(bw / t) * t);   // centered on the drawn width
          if (isHead(icon)) {
            drawHead(c, x + 4, by + Math.floor((h - HEAD) / 2));
            tx += HEAD + 4; tw -= HEAD + 4;
          } else if (icon) {
            drawIcon(c, icon, x + 3, by + Math.floor((h - ICON) / 2));
            tx += ICON + 4; tw -= ICON + 4;
          }
          const s = f(el.text ?? '');
          if (s) {
            const rgb = enabled ? (color(el['text-color']) ?? WHITE) : DISABLED_TEXT;
            c.centered(fit(s, tw - 6), tx, tw, by + Math.floor((h - 7) / 2), rgb, enabled);
          }
          const offTip = !enabled && el['disabled-tooltip'] != null ? f(el['disabled-tooltip']) : tip;
          region(x, by, bw, h, offTip, enabled ? actionsOf(el) : null);
          boxes.push({ top, x, y: by, w: bw, h: bh });
          break;
        }
        case 'panel': {
          const tintRgb = color(el.tint);
          const frameName = String(el.frame ?? 'panel');
          if (el.shape != null) {
            const rows = list(el.shape);
            const cells = rows.map((r) => [...r].map((ch) => ch === '#'));
            c.shape(frameName, x, y, cells, tintRgb);
            const t = c.tileSize();
            const w = Math.max(...rows.map((r) => r.length)) * t, h = rows.length * t;
            region(x, y, w, h, tip, actionsOf(el));
            boxes.push({ top, x, y, w, h });
          } else {
            const w = int(el.width, 32), h = int(el.height, 32);
            c.rect(frameName, x, y, w, h, tintRgb);
            region(x, y, w, h, tip, actionsOf(el));
            boxes.push({ top, x, y, w, h });
          }
          break;
        }
        case 'hitbox': {
          const w = int(el.width, 16), h = int(el.height, 16);
          region(x, y, w, h, tip, actionsOf(el));
          boxes.push({ top, x, y, w, h, invisible: true });
          break;
        }
        case 'icon': {
          const item = f(el.item ?? '').trim();
          let iy = y;
          if (isHead(item)) {
            const dy = headShift(y + (ICON - HEAD) / 2);
            if (dy) hints.push({ top, text: `head: y ${dy > 0 ? '+' : ''}${dy} → y=${y + dy}` });
            iy += dy;
          }
          if (drawIcon(c, item, x, iy, color(el.tint) ?? null)) drawCount(c, el.count != null ? f(el.count) : null, x, iy);
          region(x, iy, ICON, ICON, tip, actionsOf(el));
          boxes.push({ top, x, y: iy, w: ICON, h: ICON });
          break;
        }
        case 'list': listEl(c, el, top, ctx, boxes, regions, hints, skinText); break;
        case 'grid': gridEl(c, el, top, ctx, boxes, regions, hints); break;
        case 'slider': sliderEl(c, el, top, ctx, boxes, regions); break;
        case 'checkbox': {
          const v = varValue(ctx, String(el.var ?? '').toLowerCase()).trim().toLowerCase() === 'true';
          c.rect('slot', x, y, 16, 16, null);
          if (v) c.centered('✔', x, 16, y + 4, WHITE, true);
          const s = f(el.text ?? '');
          if (s) c.textOut(s, x + 20, y + 4, skinText(color(el.color) ?? DARK));
          const w = s ? 20 + textWidth(s) : 16;
          region(x, y, w, 16, tip, { type: 'set', var: String(el.var ?? '').toLowerCase(), value: String(!v), then: list(el.actions) });
          boxes.push({ top, x, y, w, h: 16 });
          break;
        }
        case 'head': {
          const size = headSizeOf(el.size ?? 32);
          drawHead(c, x, y, size / 8);
          region(x, y, size, size, tip, actionsOf(el));
          boxes.push({ top, x, y, w: size, h: size });
          break;
        }
        case 'image': {
          const id = f(el.image ?? '').trim().toLowerCase().replace(/\.png$/, '');
          const pic = ctx.pictures?.get(id);
          if (pic) {
            const tnt = color(el.tint);
            for (let yy = 0; yy < pic.h; yy++) {
              for (let xx = 0; xx < pic.w; xx++) {
                const v = pic.get(xx, yy);
                if (v >>> 24) blend(c.pix, x + xx, y + yy, tnt == null ? v : tint(v, tnt));
              }
            }
            region(x, y, pic.w, pic.h, tip, actionsOf(el));
            boxes.push({ top, x, y, w: pic.w, h: pic.h });
          } else {
            // not loaded into the designer: a box with the name (the server knows the real size)
            c.fillRect(x, y, 48, 24, 0x40ffffff);
            c.textOut(fit(id || '?', 44), x + 2, y + 8, 0x303030);
            region(x, y, 48, 24, tip, actionsOf(el));
            boxes.push({ top, x, y, w: 48, h: 24, placeholder: true });
          }
          break;
        }
        case 'bar': {
          const t = c.tileSize();
          const w = Math.max(t, Math.floor(int(el.width, 100) / t) * t);
          const h = Math.max(t, Math.floor(int(el.height, 8) / t) * t);
          c.rect(String(el.frame ?? 'slot'), x, y, w, h, null);
          const toNum = (v) => {
            const s = f(v ?? '').trim();
            const n = /^-?[0-9]{1,3}(,[0-9]{3})+(\.[0-9]+)?$/.test(s) ? Number(s.replace(/,/g, '')) : Number(s);
            return Number.isFinite(n) ? n : 0;
          };
          const max = toNum(el.max ?? 100);
          const ratio = max > 0 ? Math.max(0, Math.min(1, toNum(el.value) / max)) : 0;
          const fillW = Math.round(ratio * (w - 2));
          if (fillW > 0) c.fillRect(x + 1, y + 1, fillW, h - 2, (0xff000000 | (color(el.color) ?? 0x80ff20)) >>> 0);
          const s = f(el.text ?? '');
          if (s) c.centered(fit(s, w - 4), x, w, h <= 8 ? y - 9 : y + Math.floor((h - 7) / 2), WHITE, true);
          region(x, y, w, h, tip, null);
          boxes.push({ top, x, y, w, h });
          break;
        }
        case 'tabs': {
          const w = int(el.width, 160), h = int(el.height, 100);
          const t = c.tileSize();
          const key = String(el.id ?? 'tabs' + top);
          const tabs = el._tabs || [];
          const on = activeTab(el, key, ctx);
          active[key] = on;
          // like SceneRenderer.tabs: the active tab and the panel are one shape, the others 2 px lower behind it
          const frameName = String(el.frame ?? 'panel');
          const n = tabs.length;
          const cols = Math.max(n, Math.floor(w / t));
          const head = Math.max(1, Math.floor(TAB_HEADER / t));
          const body = Math.max(1, Math.floor((h - TAB_HEADER) / t));
          const col = Array.from({ length: n + 1 }, (_, i) => Math.floor(i * cols / n));
          const ai = tabs.findIndex((tb) => tb.id === on);
          tabs.forEach((tb, i) => {
            if (i === ai) return;
            const cw = col[i + 1] - col[i];
            c.shape(frameName, x + col[i] * t, y + 2, Array.from({ length: head }, () => new Array(cw).fill(true)), color(el.tint) ?? 0xB8B8B8);
            region(x + col[i] * t, y + 2, cw * t, TAB_HEADER - 2, null, el.var != null
              ? { type: 'set', var: String(el.var).toLowerCase(), value: tb.id, then: [] }
              : { type: 'page', id: 'tab:' + key, page: tb.id });
          });
          const cells = Array.from({ length: head + body }, (_, r) => Array.from({ length: cols },
            (_, cc) => r >= head || (ai >= 0 && cc >= col[ai] && cc < col[ai + 1])));
          if (!c.shape(frameName, x, y, cells, null)) errors.push({ top, text: `frame '${frameName}'` });
          tabs.forEach((tb, i) => {
            const isOn = i === ai;
            const ty = y + (isOn ? 0 : 2);
            let tx = x + col[i] * t, tw = (col[i + 1] - col[i]) * t;
            if (tb.icon != null && drawIcon(c, String(tb.icon).trim(), tx + 3, ty)) { tx += ICON + 3; tw -= ICON + 3; }
            c.centered(fit(String(tb.title ?? tb.id), tw - 6), tx, tw, ty + 5, skin.text & 0xffffff);
          });
          boxes.push({ top, x, y, w, h });
          break;
        }
        case 'scroll': {
          // the content is drawn as a menu of its own, then the visible rows are copied in
          // (the game leaves out what does not fit completely; the preview cuts by pixel)
          const w = int(el.width, 120), h = Math.max(48, int(el.height, 80));
          const key = f(String(el.id ?? 'scroll' + top));   // placeholders: one position per content
          const step = Math.max(1, int(el.step, 18));
          if (el.frame != null && !c.rect(String(el.frame), x, y, w, h, null)) errors.push({ top, text: `frame '${el.frame}'` });
          const kids = Array.isArray(el.elements) ? el.elements : [];
          const sub = renderMenu({ width: w, height: 2048, background: 'none', templates: menu.templates,
            variables: menu.variables, elements: kids }, skin, { ...ctx, menu: undefined });
          const bottom = Math.max(0, ...sub.boxes.filter((b) => !b.hidden).map((b) => b.y + b.h));
          const max = Math.max(0, Math.ceil((bottom - h) / step) * step);
          const off = Math.floor(Math.min(Math.max(0, int(ctx.pages?.['scroll:' + key], 0)), max) / step) * step;
          for (let yy = 0; yy < h; yy++) {
            for (let xx = 0; xx < w; xx++) {
              const v = sub.pix.get(xx, off + yy);
              if (v >>> 24) blend(c.pix, x + xx, y + yy, v);
            }
          }
          for (const r of sub.regions) {
            const ry = r.y - off + y;
            if (ry >= y && ry + r.h <= y + h) regions.push({ ...r, x: r.x + x, y: ry });
          }
          for (const e of sub.errors) errors.push({ top, text: e.text });
          if (max > 0) {
            const bx = x + w - 8;
            const go = (v) => ({ type: 'page', id: 'scroll:' + key, page: v });
            const up = off > 0, down = off < max;
            c.rect('button', bx, y, 8, 16, up ? BUTTON_GRAY : DISABLED);
            c.rect('button', bx, y + h - 16, 8, 16, down ? BUTTON_GRAY : DISABLED);
            c.centered('▲', bx, 8, y + 4, up ? WHITE : DISABLED_TEXT, up);
            c.centered('▼', bx, 8, y + h - 11, down ? WHITE : DISABLED_TEXT, down);
            if (up) region(bx, y, 8, 16, null, go(Math.max(0, off - step)));
            if (down) region(bx, y + h - 16, 8, 16, null, go(Math.min(max, off + step)));
            const t = c.tileSize();
            const track = Math.floor((h - 32) / t) * t;
            if (track >= t) {
              c.rect('slot', bx, y + 16, 8, track, null);
              const knob = Math.max(t, Math.min(track, Math.floor(track * h / Math.max(1, bottom) / t) * t));
              c.rect('button', bx, y + 16 + Math.floor((track - knob) * off / max), 8, knob, null);
              const segs = Math.floor(track / 8);
              for (let i = 0; i < segs; i++) {
                const target = segs === 1 ? 0 : Math.round(i / (segs - 1) * max / step) * step;
                if (target !== off) region(bx, y + 16 + i * 8, 8, 8, null, go(target));
              }
            }
          }
          boxes.push({ top, x, y, w, h });
          break;
        }
        case 'choice': {
          const opts = optionsOf(el.options);
          const ids = Object.keys(opts);
          const w = int(el.width, 80), bh = int(el.height, 16);
          const t = c.tileSize();
          const h = Math.max(t, Math.floor(bh / t) * t);
          c.rect('button', x, y, w, bh, color(el.tint) ?? BUTTON_GRAY);
          const cur = Math.max(0, ids.indexOf(varValue(ctx, String(el.var ?? '').toLowerCase()).trim()));
          const ty = y + Math.floor((h - 7) / 2);
          if (ids.length > 1) {
            c.centered('<', x, 12, ty, WHITE, true);
            c.centered('>', x + w - 12, 12, ty, WHITE, true);
          }
          const shown = ids.length ? plain(opts[ids[cur]]) : '';
          c.centered(fit(shown, w - 26), x + 12, w - 24, ty, WHITE, true);
          if (ids.length) {
            const v = String(el.var ?? '').toLowerCase();
            const half = Math.floor(w / 2);
            region(x, y, half, h, tip, { type: 'set', var: v, value: ids[(cur - 1 + ids.length) % ids.length], then: list(el.actions) });
            region(x + half, y, w - half, h, tip, { type: 'set', var: v, value: ids[(cur + 1) % ids.length], then: list(el.actions) });
          }
          boxes.push({ top, x, y, w, h: bh });
          break;
        }
        default:
          errors.push({ top, text: `type '${el.type}'` });
          boxes.push({ top, x, y, w: 16, h: 16, missing: true });
      }
    } catch (e) {
      errors.push({ top, text: String(e?.message ?? e) });
    }
  }
  // page buttons would cover other elements once a list gets more entries
  for (const r of boxes.filter((b) => b.reserve)) {
    const hit = boxes.find((b) => !b.reserve && !b.hidden && b.top !== r.top && b.w > 0 && b.h > 0
      && b.x < r.x + r.w && r.x < b.x + b.w && b.y < r.y + r.h && r.y < b.y + b.h);
    if (hit) hints.push({ top: r.top, text: `pager → ${hit.top + 1}.`, pagerOver: hit.top + 1 });
  }
  return { pix: c.pix, boxes, regions, hints, errors };
}

const on = (v) => v === true || v === 'true';

/** Label text with the label's switches: bold drawn twice 1 px apart, lines 1 px high like the plugin. */
function styledOut(c, s, x, y, rgb, shadow, look) {
  if (!s) return;
  const w = textWidth(s) + (look.bold ? [...s].length : 0);
  const line = (ly) => {
    if (shadow) c.fillRect(x, ly + 1, w + 1, 1, (0xff000000 | ((rgb & 0xfcfcfc) >> 2)) >>> 0);
    c.fillRect(x - 1, ly, w + 1, 1, (0xff000000 | rgb) >>> 0);
  };
  if (look.bold) {
    // the client widens every character by 1 px; drawn character by character like that
    let cx = x;
    for (const ch of s) {
      c.textOut(ch, cx, y, rgb, shadow);
      c.textOut(ch, cx + 1, y, rgb, false);
      cx += textWidth(ch) + 1;
    }
  } else {
    c.textOut(s, x, y, rgb, shadow);
  }
  if (look.underline) line(y + 8);
  if (look.strike) line(y + 4);
}

/** MiniMessage tags removed (pixel text has no formatting). */
export const plain = (s) => String(s ?? '').replace(/<[^<>]*>/g, '');

export function optionsOf(o) {
  const out = {};
  if (Array.isArray(o)) for (const x of o) out[String(x)] = String(x);
  else if (o && typeof o === 'object') for (const [k, v] of Object.entries(o)) out[k] = String(v);
  return out;
}

/** Rows of a list or grid: fixed items, then the sample entries of items-from. */
function rows(el, ctx) {
  const out = [];
  const add = (def, item) => {
    const f = (s) => fill(s, ctx, item);
    if (def['show-if'] != null && !allHold(def['show-if'], f)) return;
    out.push({
      text: f(def.text ?? (item ? '%item%' : '')),
      color: color(def.color) ?? WHITE,
      tint: color(def.tint),
      tip: def.tooltip != null ? f(def.tooltip) : null,
      icon: def.icon != null ? f(def.icon).trim() || null : null,
      count: def.count != null ? f(def.count) : null,
      act: def.actions == null ? null : { type: 'actions', list: list(def.actions), item },
      selected: def['selected-if'] != null && allHold(def['selected-if'], f),
    });
  };
  for (const def of el.items || []) if (def && typeof def === 'object') add(def, null);
  const from = el['items-from'] != null ? sampleKey(el['items-from']) : null;
  if (from && SAMPLES[from]) for (const item of SAMPLES[from]) add(el.item || {}, item);
  return out;
}

function pager(c, id, page, pages, x, y, w, regions) {
  if (pages <= 1) return;
  const prev = page > 0, next = page < pages - 1;
  c.rect('button', x, y, 16, 16, prev ? BUTTON_GRAY : DISABLED);
  c.rect('button', x + w - 16, y, 16, 16, next ? BUTTON_GRAY : DISABLED);
  c.centered('<', x, 16, y + 4, prev ? WHITE : DISABLED_TEXT, prev);
  c.centered('>', x + w - 16, 16, y + 4, next ? WHITE : DISABLED_TEXT, next);
  c.centered(`${page + 1}/${pages}`, x, w, y + 4, c.text & 0xffffff);
  if (prev) regions.push({ x, y, w: 16, h: 16, act: { type: 'page', id, page: page - 1 } });
  if (next) regions.push({ x: x + w - 16, y, w: 16, h: 16, act: { type: 'page', id, page: page + 1 } });
}

function listEl(c, el, top, ctx, boxes, regions, hints, skinText) {
  const x = int(el.x, 0), y = int(el.y, 0);
  const id = String(el.id ?? `list${top}`);
  const w = int(el.width, 120), nRows = Math.max(1, int(el.rows, 4));
  const fromPlayers = String(el['items-from'] ?? '').toLowerCase() === 'online-players';
  const rh = int(el['row-height'], fromPlayers ? 18 : 16);
  const all = rows(el, ctx);
  const pages = Math.max(1, Math.ceil(all.length / nRows));
  const page = Math.max(0, Math.min(ctx.pages?.[id] ?? 0, pages - 1));
  const shown = all.slice(page * nRows, page * nRows + nRows);
  const hsize = headSizeOf(el['head-size']);
  const heads = hsize <= 8 && shown.some((r) => isHead(r.icon));
  const itemIcons = shown.some((r) => r.icon && !isHead(r.icon));
  const wide = heads && itemIcons;
  let ly = y;
  if (heads) {
    const dy = headShift(y + Math.floor((rh - HEAD) / 2));
    if (dy) hints.push({ top, text: `head: y ${dy > 0 ? '+' : ''}${dy} → y=${y + dy}` });
    if (rh % LINE) hints.push({ top, text: `row-height ${rh}: 9 / 18` });
    ly += dy;
  }
  const height = nRows * rh;
  const t = c.tileSize();
  const frameH = Math.max(t, Math.ceil(height / t) * t);
  c.rect(String(el.frame ?? 'slot'), x, ly, w, frameH, null, true);
  shown.forEach((r, i) => {
    const rt = ly + i * rh;
    if (r.selected) c.rect('button', x, rt, w, rh, 0xFFD25A);
    let tx = x + 4;
    const ty = rt + Math.floor((rh - 7) / 2);
    if (isHead(r.icon) && hsize > 8) {
      const hs = Math.min(hsize, rh);
      drawHead(c, x + 3, rt + Math.floor((rh - hs) / 2), Math.max(1, Math.floor(hs / 8)));
      tx += hs + 3;
    } else if (isHead(r.icon)) {
      const hx = wide ? x + 3 + (ICON - HEAD) / 2 : x + 4;
      drawHead(c, hx, rt + Math.floor((rh - HEAD) / 2));
      tx += wide ? ICON + 2 : HEAD + 3;
    } else if (r.icon) {
      const iy = rt + Math.floor((rh - ICON) / 2);
      drawIcon(c, r.icon, x + 3, iy);
      drawCount(c, r.count, x + 3, iy, rt + rh);
      tx += ICON + 2;
    }
    c.textOut(fit(r.text, x + w - 4 - tx), tx, ty, r.color, true);
    regions.push({ x, y: rt, w, h: rh, tip: r.tip, act: r.act });
  });
  pager(c, id, page, pages, x, ly + frameH + 4, w, regions);
  boxes.push({ top, x, y: ly, w, h: frameH + (pages > 1 ? 20 : 0) });
  // room for the page buttons, which appear as soon as there are more entries than rows
  if (pages <= 1) boxes.push({ top, x, y: ly + frameH + 4, w, h: 16, reserve: true });
}

function gridEl(c, el, top, ctx, boxes, regions, hints) {
  const x = int(el.x, 0), y = int(el.y, 0);
  const id = String(el.id ?? `grid${top}`);
  const cols = Math.max(1, int(el.cols, 4)), nRows = Math.max(1, int(el.rows, 2));
  const pitch = int(el.cell, 18), size = int(el.size, 16);
  const noFrame = String(el.frame ?? 'slot').toLowerCase() === 'none';
  const all = rows(el, ctx);
  const per = cols * nRows;
  const pages = Math.max(1, Math.ceil(all.length / per));
  const page = Math.max(0, Math.min(ctx.pages?.[id] ?? 0, pages - 1));
  const shown = all.slice(page * per, page * per + per);
  let gy = y;
  const hsize = headSizeOf(el['head-size']);
  if (hsize <= 8 && shown.some((r) => isHead(r.icon))) {
    const dy = headShift(y + Math.floor((size - HEAD) / 2));
    if (dy) hints.push({ top, text: `head: y ${dy > 0 ? '+' : ''}${dy} → y=${y + dy}` });
    gy += dy;
  }
  for (let r = 0; r < nRows; r++) {
    for (let col = 0; col < cols; col++) {
      const cell = shown[r * cols + col];
      const cx = x + col * pitch, cy = gy + r * pitch;
      if (!noFrame) c.rect(String(el.frame ?? 'slot'), cx, cy, size, size, cell ? (cell.selected ? 0xFFE070 : cell.tint) : null);
      if (!cell) continue;
      let drew = false;
      if (isHead(cell.icon) && hsize > 8) {
        const hs = Math.min(hsize, size);
        const off = Math.floor((size - hs) / 2);
        drawHead(c, cx + off, cy + off, Math.max(1, Math.floor(hs / 8)));
        drew = true;
      } else if (isHead(cell.icon)) {
        const off = Math.floor((size - HEAD) / 2);
        drawHead(c, cx + off, cy + off);
        drew = true;
      } else if (cell.icon) {
        const off = Math.floor((size - ICON) / 2);
        drew = drawIcon(c, cell.icon, cx + off, cy + off);
        if (drew) drawCount(c, cell.count, cx + off, cy + off);
      }
      let label = cell.text;
      if (!label && !drew && cell.icon) label = shortName(cell.icon);
      if (label) c.centered(fit(label, size - 2), cx, size, cy + Math.floor((size - 7) / 2), cell.color, true);
      regions.push({ x: cx, y: cy, w: size, h: size, tip: cell.tip, act: cell.act });
    }
  }
  const gw = cols * pitch - (pitch - size);
  pager(c, id, page, pages, x, gy + nRows * pitch + 2, gw, regions);
  boxes.push({ top, x, y: gy, w: gw, h: nRows * pitch - (pitch - size) + (pages > 1 ? 18 : 0) });
  if (pages <= 1) boxes.push({ top, x, y: gy + nRows * pitch + 2, w: gw, h: 16, reserve: true });
}

export function format(n) {
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 1000) / 1000);
}

function sliderEl(c, el, top, ctx, boxes, regions) {
  const x = int(el.x, 0), y = int(el.y, 0);
  const min = num(el.min, 0), max = num(el.max, 10), step = num(el.step, 1);
  const t = c.tileSize();
  const w = Math.max(2 * t, Math.floor(int(el.width, 100) / t) * t);
  const knobW = t;
  c.rect('slot', x, y + Math.floor((16 - t) / 2), w, t, null);
  if (!(max > min) || !(step > 0)) { boxes.push({ top, x, y, w, h: 16 }); return; }
  const n = Math.round((max - min) / step) + 1;
  const span = Math.max(1, w - knobW);
  const v = Number(varValue(ctx, String(el.var ?? '').toLowerCase()));
  const clamped = Number.isFinite(v) ? Math.max(min, Math.min(max, v)) : min;
  const pos = Math.round((clamped - min) / step);
  const kx = x + (n <= 1 ? 0 : Math.round(pos * span / (n - 1)));
  c.rect('button', kx, y, knobW, 16, color(el.tint) ?? BUTTON_GRAY);
  const at = (col) => Math.max(0, Math.min(n - 1, Math.round(((col - knobW / 2 + 0.5) / span) * (n - 1))));
  const valueAt = (p) => Math.min(max, min + p * step);
  let start = 0, sp = at(0);
  for (let col = 1; col <= w; col++) {
    const p = col < w ? at(col) : -1;
    if (p === sp) continue;
    const val = format(valueAt(sp));
    const tip = el.tooltip != null ? fill(String(el.tooltip).replace(/%value%/g, val), ctx) : val;
    regions.push({ x: x + start, y, w: col - start, h: 16, tip,
      act: { type: 'set', var: String(el.var ?? '').toLowerCase(), value: val, then: list(el.actions) } });
    start = col;
    sp = p;
  }
  boxes.push({ top, x, y, w, h: 16 });
}
