// Menu designer: edit Glyphwork YAML menus visually. The menu is kept as the plain object that the
// YAML parses into, so settings the designer has no field for survive unchanged.

import * as jsyaml from 'https://cdn.jsdelivr.net/npm/js-yaml@4.1.0/dist/js-yaml.mjs';
import { toImageData } from '../pix.js';
import { SKINS, a2FromStyle } from '../style.js';
import { Theme } from '../model.js';
import { renderMenu, fill, varValue, allHold, plain, optionsOf, SAMPLES } from './menu-render.js';
import { FIELDS, ITEM_FIELDS, TYPES, FIELD_KEY, create, defaultFor, kindOf, setField } from './schema.js';
import { t, getLang, setLang, applyI18n } from './i18n.js';

const $ = (id) => document.getElementById(id);
const STORE = 'glyphwork-designer';
const de = () => getLang() === 'de';

// ------------------------------------------------------------------ state

const STARTER = () => ({
  title: de() ? '<gold>Mein Menü' : '<gold>My menu',
  width: 176,
  height: 96,
  elements: [
    { type: 'label', x: 8, y: 8, text: de() ? 'Hallo %player%!' : 'Hello %player%!' },
    { type: 'button', x: 8, y: 72, width: 64, text: de() ? 'Schließen' : 'Close', actions: ['close'] },
  ],
});

let menu = STARTER();
let fileId = de() ? 'mein-menue' : 'my-menu';
let selected = -1;
let preview = {};          // variable values set in the designer (not saved into the file)
let pages = {};
let shared = {};           // templates from menus/_*.yml, for the preview only
let skinKey = 'vanilla';
let mode = 'edit';
let last = null;           // last render result
const undoStack = [];
const redoStack = [];
let lastMerge = null;

function restore() {
  try {
    const s = JSON.parse(localStorage.getItem(STORE) || 'null');
    if (s && s.menu && typeof s.menu === 'object') {
      menu = s.menu;
      fileId = s.fileId || fileId;
      preview = s.preview || {};
      shared = s.shared || {};
      skinKey = s.skin || skinKey;
    }
  } catch { /* first start or storage unavailable */ }
}

function save() {
  try { localStorage.setItem(STORE, JSON.stringify({ menu, fileId, preview, shared, skin: skinKey })); } catch { /* ignore */ }
}

const snapshot = () => JSON.stringify({ menu, selected });

/** Records an undo step before a change; changes with the same merge key in a row share one step. */
function before(merge = null) {
  const now = Date.now();
  if (merge && lastMerge && lastMerge.key === merge && now - lastMerge.time < 1200) {
    lastMerge.time = now;
    return;
  }
  undoStack.push(snapshot());
  if (undoStack.length > 200) undoStack.shift();
  redoStack.length = 0;
  lastMerge = merge ? { key: merge, time: now } : null;
}

function undo() {
  if (!undoStack.length) return;
  redoStack.push(snapshot());
  const s = JSON.parse(undoStack.pop());
  menu = s.menu;
  selected = s.selected;
  lastMerge = null;
  refreshAll();
}

function redo() {
  if (!redoStack.length) return;
  undoStack.push(snapshot());
  const s = JSON.parse(redoStack.pop());
  menu = s.menu;
  selected = s.selected;
  lastMerge = null;
  refreshAll();
}

const elements = () => (menu.elements = Array.isArray(menu.elements) ? menu.elements : []);
const current = () => (selected >= 0 ? elements()[selected] : null);

function replaceSelected(el) {
  elements()[selected] = el;
}

// ------------------------------------------------------------------ YAML

/** Menu as YAML: elements one per line in flow style when short, everything else in block style. */
export function toYaml(m) {
  const opts = { lineWidth: -1, noRefs: true };
  let out = '';
  for (const [k, v] of Object.entries(m)) {
    if (k === 'elements' && Array.isArray(v)) {
      out += 'elements:\n';
      for (const el of v) {
        let flow = jsyaml.dump(el, { ...opts, flowLevel: 0 }).trim();
        if (flow.startsWith('{') && flow.endsWith('}')) flow = '{ ' + flow.slice(1, -1) + ' }';
        if (flow.length <= 150 && !flow.includes('\n')) {
          out += '  - ' + flow + '\n';
        } else {
          const block = jsyaml.dump(el, opts).trimEnd().split('\n');
          out += block.map((l, i) => (i === 0 ? '  - ' : '    ') + l).join('\n') + '\n';
        }
      }
      if (!v.length) out = out.replace(/elements:\n$/, 'elements: []\n');
    } else {
      out += jsyaml.dump({ [k]: v }, opts);
    }
  }
  // js-yaml quotes the key y (YAML 1.1 reads a bare y as "yes"); Bukkit's SnakeYAML reads it as text
  return out.replace(/(^|[{,]\s?|\n\s*-?\s*)'y':/g, '$1y:');
}

function parseYaml(text) {
  const m = jsyaml.load(text);
  if (!m || typeof m !== 'object' || Array.isArray(m)) throw new Error(de() ? 'Ein Menü ist eine Zuordnung (title, elements …).' : 'A menu is a mapping (title, elements …).');
  return m;
}

// ------------------------------------------------------------------ skin

let skinCache = null;
function skin() {
  if (skinCache && skinCache.key === skinKey) return skinCache.value;
  let value = null;
  if (skinKey === 'editor') {
    const th = Theme.restore();
    if (th) value = { frames: th.frames, tileSize: th.tileSize, text: th.text };
    else toast(t('noEditorSkin'), true);
  }
  if (!value) {
    const s = SKINS[skinKey] || SKINS.vanilla;
    value = { frames: new Map(['panel', 'button', 'slot'].map((f) => [f, a2FromStyle(s[f], 8)])), tileSize: 8, text: s.text };
  }
  skinCache = { key: skinKey, value };
  return value;
}

// ------------------------------------------------------------------ stage

const stage = $('stage');
const ctx2d = stage.getContext('2d');
const off = document.createElement('canvas');
let zoom = 3;
let snap = 1;
let drag = null;
let hoverRegion = null;

function draw() {
  last = renderMenu(menu, skin(), { vars: preview, pages, shared });
  const { pix } = last;
  off.width = pix.w;
  off.height = pix.h;
  off.getContext('2d').putImageData(toImageData(pix), 0, 0);
  stage.width = pix.w * zoom;
  stage.height = pix.h * zoom;
  ctx2d.imageSmoothingEnabled = false;
  ctx2d.clearRect(0, 0, stage.width, stage.height);
  ctx2d.drawImage(off, 0, 0, stage.width, stage.height);
  if ($('textGrid').checked) {
    ctx2d.strokeStyle = 'rgba(80, 160, 255, 0.35)';
    ctx2d.lineWidth = 1;
    for (let y = 8; y < pix.h; y += 9) {
      ctx2d.beginPath();
      ctx2d.moveTo(0, y * zoom + 0.5);
      ctx2d.lineTo(stage.width, y * zoom + 0.5);
      ctx2d.stroke();
    }
  }
  if (mode === 'edit' && $('outlines').checked) {
    ctx2d.setLineDash([3, 3]);
    for (const b of last.boxes) {
      if (b.invisible || b.missing) {
        ctx2d.strokeStyle = b.missing ? 'rgba(229, 83, 75, .9)' : 'rgba(240, 180, 41, .8)';
        ctx2d.strokeRect(b.x * zoom + 0.5, b.y * zoom + 0.5, b.w * zoom - 1, b.h * zoom - 1);
      }
    }
    ctx2d.setLineDash([]);
  }
  if (mode === 'edit' && selected >= 0) {
    const r = selectionBox();
    if (r) {
      ctx2d.strokeStyle = '#f0b429';
      ctx2d.lineWidth = 2;
      ctx2d.strokeRect(r.x * zoom, r.y * zoom, Math.max(2, r.w * zoom), Math.max(2, r.h * zoom));
      if (resizable(current())) {
        ctx2d.fillStyle = '#f0b429';
        ctx2d.fillRect((r.x + r.w) * zoom - 5, (r.y + r.h) * zoom - 5, 8, 8);
      }
    }
  }
  showProblems();
}

/** Union of the boxes drawn for the selected entry (a template can draw several). */
function selectionBox() {
  const bs = (last?.boxes || []).filter((b) => b.top === selected && !b.hidden);
  if (!bs.length) {
    const el = current();
    return el ? { x: Number(el.x) || 0, y: Number(el.y) || 0, w: 8, h: 8 } : null;
  }
  const x0 = Math.min(...bs.map((b) => b.x)), y0 = Math.min(...bs.map((b) => b.y));
  const x1 = Math.max(...bs.map((b) => b.x + b.w)), y1 = Math.max(...bs.map((b) => b.y + b.h));
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}

function pixelOf(e) {
  const r = stage.getBoundingClientRect();
  return [(e.clientX - r.left) * (stage.width / r.width) / zoom, (e.clientY - r.top) * (stage.height / r.height) / zoom];
}

const RESIZABLE = new Set(['button', 'panel', 'hitbox', 'label', 'slider', 'choice', 'list', 'grid']);
const resizable = (el) => el && el.use == null && RESIZABLE.has(kindOf(el)) && !(kindOf(el) === 'panel' && el.shape != null);

function hitElement(px, py) {
  const boxes = last?.boxes || [];
  for (let i = boxes.length - 1; i >= 0; i--) {
    const b = boxes[i];
    if (b.hidden) continue;
    if (px >= b.x && py >= b.y && px < b.x + Math.max(b.w, 4) && py < b.y + Math.max(b.h, 4)) return b.top;
  }
  return -1;
}

function hitRegion(px, py) {
  const rs = last?.regions || [];
  for (let i = rs.length - 1; i >= 0; i--) {
    const r = rs[i];
    if (px >= r.x && py >= r.y && px < r.x + r.w && py < r.y + r.h) return r;
  }
  return null;
}

const snapTo = (v) => Math.round(v / snap) * snap;

stage.addEventListener('pointerdown', (e) => {
  stage.focus();
  const [px, py] = pixelOf(e);
  if (mode === 'play') {
    const r = hitRegion(px, py);
    if (r?.act) act(r.act);
    return;
  }
  const sel = selectionBox();
  const el = current();
  if (el && sel && resizable(el) && Math.abs(px - (sel.x + sel.w)) <= 6 / zoom + 1 && Math.abs(py - (sel.y + sel.h)) <= 6 / zoom + 1) {
    drag = { kind: 'resize', px, py, start: { ...el }, box: sel };
    stage.setPointerCapture(e.pointerId);
    return;
  }
  const hit = hitElement(px, py);
  if (hit !== selected) {
    selected = hit;
    refreshSelection();
  }
  if (hit >= 0) {
    const cur = current();
    drag = { kind: 'move', px, py, x: Number(cur.x) || 0, y: Number(cur.y) || 0 };
    stage.setPointerCapture(e.pointerId);
  }
});

stage.addEventListener('pointermove', (e) => {
  const [px, py] = pixelOf(e);
  status(px, py);
  if (mode === 'play' || !drag) {
    const r = mode === 'play' ? hitRegion(px, py) : null;
    if (r !== hoverRegion) {
      hoverRegion = r;
      showTip(r, e);
    } else if (r) {
      showTip(r, e);
    }
    return;
  }
  const el = current();
  if (!el) return;
  if (drag.kind === 'move') {
    const nx = snapTo(drag.x + (px - drag.px)), ny = snapTo(drag.y + (py - drag.py));
    if (nx === Number(el.x) && ny === Number(el.y)) return;
    before('drag');
    let next = setField(el, 'x', nx);
    next = setField(next, 'y', ny);
    replaceSelected(next);
  } else {
    before('resize');
    replaceSelected(resize(drag.start, Math.round(px - drag.px), Math.round(py - drag.py)));
  }
  changed(false);
  updateXYInputs();
});

stage.addEventListener('pointerup', () => {
  if (drag) {
    drag = null;
    lastMerge = null;
    refreshProps();
  }
});

stage.addEventListener('pointerleave', () => {
  hoverRegion = null;
  showTip(null);
});

function resize(start, dw, dh) {
  const k = kindOf(start);
  const n = (v, d) => (Number.isFinite(Number(v)) ? Number(v) : d);
  let el = start;
  const set = (key, v) => { el = setField(el, key, v); };
  switch (k) {
    case 'button': case 'panel': case 'hitbox': {
      const w0 = n(start.width, k === 'button' ? 64 : 16), h0 = n(start.height, 16);
      set('width', Math.max(8, snapTo(w0 + dw)));
      set('height', Math.max(8, snapTo(h0 + dh)));
      break;
    }
    case 'label': set('width', Math.max(8, snapTo(n(start.width, 60) + dw))); break;
    case 'slider': set('width', Math.max(16, snapTo(n(start.width, 100) + dw))); break;
    case 'choice': {
      set('width', Math.max(24, snapTo(n(start.width, 80) + dw)));
      set('height', Math.max(8, snapTo(n(start.height, 16) + dh)));
      break;
    }
    case 'list': {
      const rh = n(start['row-height'], 16);
      set('width', Math.max(24, snapTo(n(start.width, 120) + dw)));
      set('rows', Math.max(1, Math.round((n(start.rows, 4) * rh + dh) / rh)));
      break;
    }
    case 'grid': {
      const pitch = n(start.cell, 18);
      set('cols', Math.max(1, Math.round((n(start.cols, 4) * pitch + dw) / pitch)));
      set('rows', Math.max(1, Math.round((n(start.rows, 2) * pitch + dh) / pitch)));
      break;
    }
    default: break;
  }
  return el;
}

function status(px, py) {
  const el = current();
  const pos = `x ${Math.floor(px)}, y ${Math.floor(py)}`;
  $('status').textContent = el ? `${pos} · ${layerName(el)} (${el.x ?? 0}, ${el.y ?? 0})` : pos;
}

function showTip(r, e) {
  const tip = $('tip');
  if (!r || !r.tip) { tip.hidden = true; return; }
  tip.textContent = plain(r.tip);
  tip.hidden = false;
  const wrap = stage.parentElement.getBoundingClientRect();
  tip.style.left = `${e.clientX - wrap.left + stage.parentElement.scrollLeft + 14}px`;
  tip.style.top = `${e.clientY - wrap.top + stage.parentElement.scrollTop + 14}px`;
}

function showProblems() {
  const box = $('problems');
  box.innerHTML = '';
  for (const er of last?.errors || []) {
    const d = document.createElement('div');
    d.className = 'err';
    d.textContent = `⚠ ${er.top >= 0 ? '#' + (er.top + 1) + ': ' : ''}${er.text}`;
    box.append(d);
  }
}

// ------------------------------------------------------------------ keyboard

document.addEventListener('keydown', (e) => {
  const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);
  if ((e.ctrlKey || e.metaKey) && !typing) {
    if (e.key === 'z' && !e.shiftKey) { e.preventDefault(); undo(); return; }
    if (e.key === 'y' || (e.key === 'Z' && e.shiftKey) || (e.key === 'z' && e.shiftKey)) { e.preventDefault(); redo(); return; }
    if (e.key === 'd') { e.preventDefault(); duplicate(); return; }
  }
  if (typing || mode !== 'edit' || selected < 0) return;
  const step = e.shiftKey ? 8 : snap;
  const moves = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
  if (moves[e.key]) {
    e.preventDefault();
    const el = current();
    before('keys');
    let next = setField(el, 'x', (Number(el.x) || 0) + moves[e.key][0]);
    next = setField(next, 'y', (Number(el.y) || 0) + moves[e.key][1]);
    replaceSelected(next);
    changed(false);
    updateXYInputs();
  } else if (e.key === 'Delete') {
    e.preventDefault();
    removeSelected();
  }
});

// ------------------------------------------------------------------ layers and palette

function layerName(el) {
  const k = kindOf(el);
  const name = t(k === 'use' ? 't_use' : 't_' + k);
  const detail = el.use ?? el.text ?? el.item ?? el.var ?? el.id ?? '';
  return `${name}${detail !== '' ? ': ' + plain(String(detail)).slice(0, 24) : ''}`;
}

function refreshLayers() {
  const ul = $('layers');
  ul.innerHTML = '';
  elements().forEach((el, i) => {
    const li = document.createElement('li');
    if (i === selected) li.className = 'active';
    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = `${i + 1}. ${layerName(el)}`;
    li.append(name);
    const hidden = (last?.boxes || []).some((b) => b.top === i && b.hidden);
    if (hidden) {
      const m = document.createElement('span');
      m.className = 'muted';
      m.textContent = t('hidden');
      li.append(m);
    }
    li.onclick = () => { selected = i; refreshSelection(); };
    ul.append(li);
  });
}

function addElement(type) {
  before();
  const el = create(type, 8, 8, de());
  // place it below the lowest element so it does not cover others
  const bottom = Math.max(8, ...((last?.boxes || []).filter((b) => !b.hidden).map((b) => b.y + b.h + 4)));
  el.y = bottom;
  if (el.var != null) {
    menu.variables = menu.variables && typeof menu.variables === 'object' ? menu.variables : {};
    if (!(el.var in menu.variables)) menu.variables[el.var] = defaultFor(el);
  }
  elements().push(el);
  selected = elements().length - 1;
  // the menu grows when the new element does not fit
  draw();
  const box = selectionBox();
  const need = box ? Math.ceil((box.y + box.h + 8) / 8) * 8 : 0;
  if (need > (Number(menu.height) || 96)) menu.height = need;
  refreshAll();
  showTab('element');
}

function moveLayer(delta) {
  const i = selected, j = selected + delta;
  if (i < 0 || j < 0 || j >= elements().length) return;
  before();
  const list = elements();
  [list[i], list[j]] = [list[j], list[i]];
  selected = j;
  refreshAll();
}

function duplicate() {
  const el = current();
  if (!el) return;
  before();
  const copy = JSON.parse(JSON.stringify(el));
  copy.x = (Number(el.x) || 0) + 8;
  copy.y = (Number(el.y) || 0) + 8;
  elements().splice(selected + 1, 0, copy);
  selected++;
  refreshAll();
}

function removeSelected() {
  if (selected < 0) return;
  before();
  elements().splice(selected, 1);
  selected = Math.min(selected, elements().length - 1);
  refreshAll();
}

// ------------------------------------------------------------------ property editors

const ACTION_EXAMPLES = () => [
  'message: <green>' + (de() ? 'Hallo!' : 'Hello!'), 'player: spawn', 'console: say %player%', 'open: menu-id', 'back', 'close',
  'refresh', 'set: tab shop', 'toggle: sound', (de() ? 'input: Wie heißt du? | message: Hallo %input%' : 'input: Your name? | message: Hi %input%'),
  'require: %menu_tab% == shop | message: <red>' + (de() ? 'Nicht hier' : 'Not here'), 'form: name', 'sound: ui.button.click',
];

function labelFor(key) {
  return FIELD_KEY[key] ? t(FIELD_KEY[key]) : key;
}

/**
 * Builds an input for one setting. get() reads the current value, put(v) writes it (undefined =
 * remove). Returns the element to append.
 */
function field(key, kind, extra, get, put, mergeKey) {
  const wrap = document.createElement('label');
  const caption = document.createElement('span');
  caption.textContent = labelFor(key);
  wrap.append(caption);
  const commit = (v) => { before(mergeKey + ':' + key); put(v); changed(false); };
  const value = get();
  switch (kind) {
    case 'int': {
      const inp = document.createElement('input');
      inp.type = 'number';
      inp.value = value ?? '';
      inp.dataset.key = key;
      inp.oninput = () => commit(inp.value === '' ? undefined : Number(inp.value));
      wrap.append(inp);
      break;
    }
    case 'bool': {
      wrap.className = 'inline';
      const inp = document.createElement('input');
      inp.type = 'checkbox';
      inp.checked = value === true || value === 'true';
      inp.onchange = () => commit(inp.checked ? true : undefined);
      wrap.prepend(inp);
      break;
    }
    case 'select': {
      const sel = document.createElement('select');
      for (const o of ['', ...extra]) {
        const op = document.createElement('option');
        op.value = o;
        op.textContent = o || t('none');
        sel.append(op);
      }
      sel.value = value ?? '';
      sel.onchange = () => commit(sel.value || undefined);
      wrap.append(sel);
      break;
    }
    case 'color': {
      const row = document.createElement('div');
      row.className = 'colorRow';
      const txt = document.createElement('input');
      txt.type = 'text';
      txt.value = value ?? '';
      txt.setAttribute('list', 'colorNames');
      const pick = document.createElement('input');
      pick.type = 'color';
      const sync = () => { if (/^#[0-9a-f]{6}$/i.test(txt.value.trim())) pick.value = txt.value.trim(); };
      sync();
      txt.oninput = () => { sync(); commit(txt.value.trim() || undefined); };
      pick.oninput = () => { txt.value = pick.value; commit(pick.value); };
      row.append(txt, pick);
      wrap.append(row);
      break;
    }
    case 'lines': case 'shape': {
      const ta = document.createElement('textarea');
      const arr = value == null ? [] : Array.isArray(value) ? value : [value];
      ta.rows = Math.max(2, Math.min(8, arr.length + 1));
      ta.value = arr.join('\n');
      ta.spellcheck = false;
      ta.oninput = () => commit(ta.value.split('\n').map((s) => (kind === 'shape' ? s.trimEnd() : s.trim())).filter(Boolean));
      wrap.append(ta);
      if (key === 'actions' || key === 'then') {
        const sel = document.createElement('select');
        sel.innerHTML = `<option value="">${t('insertAction')}</option>`;
        for (const a of ACTION_EXAMPLES()) {
          const op = document.createElement('option');
          op.value = a;
          op.textContent = a;
          sel.append(op);
        }
        sel.onchange = () => {
          if (!sel.value) return;
          ta.value = (ta.value.trim() ? ta.value.trimEnd() + '\n' : '') + sel.value;
          sel.value = '';
          ta.oninput();
        };
        wrap.append(sel);
      }
      break;
    }
    case 'options': {
      const ta = document.createElement('textarea');
      const opts = optionsOf(value);
      ta.rows = Math.max(3, Object.keys(opts).length + 1);
      ta.value = Object.entries(opts).map(([k, v]) => (k === v ? k : `${k}: ${v}`)).join('\n');
      ta.oninput = () => commit(linesToOptions(ta.value));
      wrap.append(ta);
      break;
    }
    default: {
      const inp = document.createElement('input');
      inp.type = 'text';
      inp.value = value ?? '';
      inp.oninput = () => commit(inp.value === '' ? undefined : inp.value);
      wrap.append(inp);
    }
  }
  return wrap;
}

/** "a\nb" -> [a, b]; "easy: <green>Easy\nhard" -> {easy: '<green>Easy', hard: 'hard'}. */
function linesToOptions(text) {
  const lines = text.split('\n').map((s) => s.trim()).filter(Boolean);
  const pairs = lines.map((l) => {
    const i = l.indexOf(':');
    return i > 0 ? [l.slice(0, i).trim(), l.slice(i + 1).trim()] : [l, l];
  });
  if (pairs.every(([k, v]) => k === v)) return pairs.map(([k]) => k);
  return Object.fromEntries(pairs);
}

function refreshProps() {
  const box = $('elementProps');
  box.innerHTML = '';
  const el = current();
  if (!el) {
    const p = document.createElement('p');
    p.className = 'hint';
    p.textContent = t('noSelection');
    box.append(p);
    return;
  }
  const k = kindOf(el);
  const head = document.createElement('div');
  head.className = 'typeHead';
  head.textContent = `${selected + 1}. ${t(k === 'use' ? 't_use' : 't_' + k)}`;
  box.append(head);
  for (const h of (last?.hints || []).filter((x) => x.top === selected)) {
    const d = document.createElement('div');
    d.className = 'hintLine';
    d.textContent = 'ℹ ' + h.text;
    box.append(d);
  }
  const put = (key) => (v) => replaceSelected(setField(current(), key, v));
  const get = (key) => () => current()[key];
  const fields = FIELDS[k] || [];
  let pairOpen = null;
  for (const [key, kind, extra] of fields) {
    if (kind === 'items') { box.append(itemsEditor(k)); continue; }
    if (kind === 'from') { box.append(field('items-from', 'select', Object.keys(SAMPLES), get('items-from'), put('items-from'), 'el' + selected)); continue; }
    if (kind === 'item') { box.append(itemTemplateEditor(k)); continue; }
    if (kind === 'use') { box.append(field('use', 'select', templateNames(), get('use'), put('use'), 'el' + selected)); continue; }
    if (kind === 'values') { box.append(valuesEditor()); continue; }
    const f = field(key, kind, extra, get(key), put(key), 'el' + selected);
    // x/y and width/height side by side
    if (['x', 'width', 'cols', 'min'].includes(key)) {
      pairOpen = document.createElement('div');
      pairOpen.className = 'pair';
      pairOpen.append(f);
      box.append(pairOpen);
    } else if (pairOpen && ['y', 'height', 'rows', 'max'].includes(key)) {
      pairOpen.append(f);
      pairOpen = null;
    } else {
      pairOpen = null;
      box.append(f);
    }
  }
  box.append(rawEditor());
}

function updateXYInputs() {
  const el = current();
  if (!el) return;
  for (const key of ['x', 'y', 'width', 'height', 'rows', 'cols']) {
    const inp = $('elementProps').querySelector(`input[data-key="${key}"]`);
    if (inp && document.activeElement !== inp) inp.value = el[key] ?? '';
  }
}

function itemsEditor(k) {
  const wrap = document.createElement('div');
  const h = document.createElement('h3');
  h.textContent = t('items');
  wrap.append(h);
  const items = () => (Array.isArray(current().items) ? current().items : []);
  items().forEach((item, i) => {
    const d = document.createElement('details');
    const s = document.createElement('summary');
    s.textContent = `${i + 1}. ${plain(item?.text ?? item?.icon ?? '…')}`;
    d.append(s);
    for (const [key, kind] of ITEM_FIELDS[k]) {
      d.append(field(key, kind, null, () => items()[i]?.[key], (v) => {
        const list = items().slice();
        list[i] = setItem(list[i] || {}, key, v, ITEM_FIELDS[k]);
        replaceSelected(setField(current(), 'items', list));
      }, `item${selected}.${i}`));
    }
    const row = document.createElement('div');
    row.className = 'row';
    const mk = (label, fn) => { const b = document.createElement('button'); b.className = 'small'; b.textContent = label; b.onclick = (e) => { e.preventDefault(); fn(); }; return b; };
    row.append(
      mk('↑', () => { if (i > 0) { before(); const l = items().slice(); [l[i - 1], l[i]] = [l[i], l[i - 1]]; replaceSelected(setField(current(), 'items', l)); refreshAll(); } }),
      mk('↓', () => { const l = items().slice(); if (i < l.length - 1) { before(); [l[i + 1], l[i]] = [l[i], l[i + 1]]; replaceSelected(setField(current(), 'items', l)); refreshAll(); } }),
      mk(t('remove'), () => { before(); const l = items().slice(); l.splice(i, 1); replaceSelected(setField(current(), 'items', l)); refreshAll(); }),
    );
    d.append(row);
    wrap.append(d);
  });
  const add = document.createElement('button');
  add.className = 'small';
  add.textContent = t('addItem');
  add.onclick = () => {
    before();
    const l = items().slice();
    l.push(k === 'grid' ? { icon: 'stone' } : { text: (de() ? 'Eintrag ' : 'Entry ') + (l.length + 1) });
    replaceSelected(setField(current(), 'items', l));
    refreshAll();
  };
  wrap.append(add);
  return wrap;
}

function setItem(item, key, v, order) {
  const next = { ...item };
  if (v === undefined || v === '' || (Array.isArray(v) && !v.length)) delete next[key];
  else next[key] = v;
  const out = {};
  for (const [k] of order) if (k in next) out[k] = next[k];
  for (const k of Object.keys(next)) if (!(k in out)) out[k] = next[k];
  return out;
}

function itemTemplateEditor(k) {
  const d = document.createElement('details');
  const s = document.createElement('summary');
  s.textContent = t('itemTemplate');
  d.append(s);
  d.open = current()['items-from'] != null;
  for (const [key, kind] of ITEM_FIELDS[k]) {
    if (key === 'permission') continue;
    d.append(field(key, kind, null, () => current().item?.[key], (v) => {
      const item = setItem(current().item || {}, key, v, ITEM_FIELDS[k]);
      replaceSelected(setField(current(), 'item', Object.keys(item).length ? item : undefined));
    }, `tpl${selected}`));
  }
  return d;
}

function templateNames() {
  return [...new Set([...Object.keys(menu.templates || {}), ...Object.keys(shared)])];
}

function valuesEditor() {
  const el = current();
  const wrap = document.createElement('label');
  wrap.append(document.createTextNode(t('f_values')));
  const ta = document.createElement('textarea');
  const extra = Object.entries(el).filter(([k]) => !['use', 'x', 'y', 'show-if'].includes(k));
  ta.rows = Math.max(3, extra.length + 1);
  ta.value = extra.map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}`).join('\n');
  ta.oninput = () => {
    before('values' + selected);
    const base = {};
    for (const k of ['use', 'x', 'y']) if (current()[k] != null) base[k] = current()[k];
    for (const line of ta.value.split('\n')) {
      const i = line.indexOf(':');
      if (i > 0) {
        const raw = line.slice(i + 1).trim();
        let v = raw;
        try { v = raw === '' ? '' : jsyaml.load(raw); } catch { v = raw; }
        base[line.slice(0, i).trim()] = v;
      }
    }
    if (current()['show-if'] != null) base['show-if'] = current()['show-if'];
    replaceSelected(base);
    changed(false);
  };
  wrap.append(ta);
  return wrap;
}

function rawEditor() {
  const d = document.createElement('details');
  const s = document.createElement('summary');
  s.textContent = t('rawElement');
  d.append(s);
  const ta = document.createElement('textarea');
  ta.rows = 10;
  ta.spellcheck = false;
  ta.value = jsyaml.dump(current(), { lineWidth: -1 });
  const btn = document.createElement('button');
  btn.className = 'small';
  btn.textContent = t('apply');
  btn.onclick = () => {
    try {
      const v = jsyaml.load(ta.value);
      if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('mapping expected');
      before();
      replaceSelected(v);
      refreshAll();
    } catch (e) {
      toast(`${t('yamlError')}: ${e.message}`, true);
    }
  };
  d.append(ta, btn);
  return d;
}

// ------------------------------------------------------------------ menu settings

const MENU_FIELDS = [
  ['title', 'text', 'menuTitle'], ['width', 'int', 'width'], ['height', 'int', 'height'],
  ['background', 'text', 'background'], ['command', 'text', 'command'], ['aliases', 'lines', 'aliases'],
  ['permission', 'text', 'permission'], ['description', 'text', 'description'], ['exit-label', 'text', 'exitLabel'],
  ['refresh', 'text', 'refresh'],
];

function refreshMenuProps() {
  const box = $('menuProps');
  box.innerHTML = '';
  const id = document.createElement('label');
  id.append(document.createTextNode(t('fileName')));
  const idIn = document.createElement('input');
  idIn.type = 'text';
  idIn.value = fileId;
  idIn.oninput = () => { fileId = idIn.value.trim().toLowerCase().replace(/[^a-z0-9_.-]+/g, '-') || 'menu'; save(); };
  id.append(idIn);
  box.append(id);
  let pair = null;
  for (const [key, kind, label] of MENU_FIELDS) {
    const f = field(key, kind, null, () => menu[key], (v) => {
      const next = {};
      const order = ['title', 'width', 'height', 'background', 'command', 'aliases', 'permission', 'description',
        'exit-label', 'refresh', 'templates', 'variables', 'forms', 'elements'];
      const m = { ...menu };
      if (v === undefined || (Array.isArray(v) && !v.length)) delete m[key];
      else m[key] = v;
      for (const k of order) if (k in m) next[k] = m[k];
      for (const k of Object.keys(m)) if (!(k in next)) next[k] = m[k];
      menu = next;
    }, 'menu');
    f.querySelector('span').textContent = t(label);
    if (key === 'width') { pair = document.createElement('div'); pair.className = 'pair'; pair.append(f); box.append(pair); }
    else if (key === 'height' && pair) { pair.append(f); pair = null; }
    else box.append(f);
  }
}

// ------------------------------------------------------------------ variables

function refreshVars() {
  const box = $('varsTable');
  box.innerHTML = '';
  const grid = document.createElement('div');
  grid.className = 'vars';
  for (const h of [t('varName'), t('varDefault'), t('varPreview'), '']) {
    const d = document.createElement('div');
    d.className = 'head';
    d.textContent = h;
    grid.append(d);
  }
  const vars = menu.variables && typeof menu.variables === 'object' ? menu.variables : {};
  for (const name of Object.keys(vars)) {
    const nameIn = document.createElement('input');
    nameIn.value = name;
    nameIn.onchange = () => {
      const nn = nameIn.value.trim().toLowerCase();
      if (!nn || nn === name || nn in vars) { nameIn.value = name; return; }
      before();
      menu.variables = Object.fromEntries(Object.entries(vars).map(([k, v]) => [k === name ? nn : k, v]));
      if (name in preview) { preview[nn] = preview[name]; delete preview[name]; }
      changed(true);
    };
    const defIn = document.createElement('input');
    defIn.value = vars[name] ?? '';
    defIn.oninput = () => { before('var:' + name); menu.variables[name] = defIn.value; changed(false); };
    const prevIn = document.createElement('input');
    prevIn.value = preview[name] ?? '';
    prevIn.placeholder = String(vars[name] ?? '');
    prevIn.oninput = () => {
      if (prevIn.value === '') delete preview[name];
      else preview[name] = prevIn.value;
      changed(false);
    };
    const del = document.createElement('button');
    del.className = 'small';
    del.textContent = '×';
    del.onclick = () => {
      before();
      delete menu.variables[name];
      delete preview[name];
      if (!Object.keys(menu.variables).length) delete menu.variables;
      changed(true);
    };
    grid.append(nameIn, defIn, prevIn, del);
  }
  box.append(grid);
}

// ------------------------------------------------------------------ forms

const FIELD_TYPES = ['text', 'slider', 'checkbox', 'choice'];

function refreshForms() {
  const box = $('formsEditor');
  box.innerHTML = '';
  const forms = menu.forms && typeof menu.forms === 'object' ? menu.forms : {};
  const setForm = (name, form) => {
    menu.forms = { ...(menu.forms || {}), [name]: form };
  };
  for (const [name, form] of Object.entries(forms)) {
    const card = document.createElement('div');
    card.className = 'formCard props';
    const nameF = field('name', 'text', null, () => name, () => {}, 'form');
    nameF.querySelector('span').textContent = t('formName');
    const nameIn = nameF.querySelector('input');
    nameIn.oninput = null;
    nameIn.onchange = () => {
      const nn = nameIn.value.trim().toLowerCase();
      if (!nn || nn === name || nn in forms) { nameIn.value = name; return; }
      before();
      menu.forms = Object.fromEntries(Object.entries(forms).map(([k, v]) => [k === name ? nn : k, v]));
      changed(true);
    };
    card.append(nameF);
    const fget = (key) => () => (menu.forms[name] || {})[key];
    const fput = (key) => (v) => {
      const f = { ...(menu.forms[name] || {}) };
      if (v === undefined || (Array.isArray(v) && !v.length)) delete f[key];
      else f[key] = v;
      const order = ['title', 'width', 'ok', 'cancel', 'fields', 'actions'];
      const out = {};
      for (const k of order) if (k in f) out[k] = f[k];
      for (const k of Object.keys(f)) if (!(k in out)) out[k] = f[k];
      setForm(name, out);
    };
    for (const [key, kind, label] of [['title', 'text', 'formTitle'], ['ok', 'text', 'ok'], ['cancel', 'text', 'cancel'], ['width', 'int', 'width']]) {
      const f = field(key, kind, null, fget(key), fput(key), 'form:' + name);
      f.querySelector('span').textContent = t(label);
      card.append(f);
    }
    const h = document.createElement('h3');
    h.textContent = t('fields');
    card.append(h);
    const fields = () => (Array.isArray(menu.forms[name]?.fields) ? menu.forms[name].fields : []);
    fields().forEach((fd, i) => {
      const row = document.createElement('div');
      row.className = 'fieldRow';
      const setFd = (key) => (v) => {
        const list = fields().slice();
        const next = { ...list[i] };
        if (v === undefined || v === '' || (Array.isArray(v) && !v.length)) delete next[key];
        else next[key] = v;
        list[i] = next;
        fput('fields')(list);
      };
      const getFd = (key) => () => fields()[i]?.[key];
      const typeF = field('type', 'select', FIELD_TYPES, getFd('type'), (v) => { setFd('type')(v); }, `ff:${name}.${i}`);
      typeF.querySelector('span').textContent = t('fieldType');
      typeF.querySelector('select').addEventListener('change', () => refreshForms());
      row.append(typeF);
      for (const [key, kind, label] of [['var', 'text', 'fieldVar'], ['label', 'text', 'fieldLabel']]) {
        const f = field(key, kind, null, getFd(key), setFd(key), `ff:${name}.${i}`);
        f.querySelector('span').textContent = t(label);
        row.append(f);
      }
      const type = String(fd?.type ?? 'text');
      const extra = type === 'text' ? [['max-length', 'int', 'maxLength'], ['lines', 'int', 'lines']]
        : type === 'slider' ? [['min', 'int', 'min'], ['max', 'int', 'max'], ['step', 'text', 'step']]
          : type === 'choice' ? [['options', 'options', 'options']] : [];
      for (const [key, kind, label] of extra) {
        const f = field(key, kind, null, getFd(key), setFd(key), `ff:${name}.${i}`);
        f.querySelector('span').textContent = t(label);
        row.append(f);
      }
      const del = document.createElement('button');
      del.className = 'small';
      del.textContent = t('remove');
      del.onclick = () => { before(); const l = fields().slice(); l.splice(i, 1); fput('fields')(l); changed(true); };
      row.append(del);
      card.append(row);
    });
    const buttons = document.createElement('div');
    buttons.className = 'row wrap';
    const addF = document.createElement('button');
    addF.className = 'small';
    addF.textContent = t('addField');
    addF.onclick = () => { before(); fput('fields')([...fields(), { type: 'text', var: 'eingabe', label: de() ? 'Eingabe' : 'Input' }]); changed(true); };
    const test = document.createElement('button');
    test.className = 'small';
    test.textContent = t('play');
    test.onclick = () => openForm(name, null);
    const delForm = document.createElement('button');
    delForm.className = 'small';
    delForm.textContent = t('remove');
    delForm.onclick = () => {
      before();
      delete menu.forms[name];
      if (!Object.keys(menu.forms).length) delete menu.forms;
      changed(true);
    };
    buttons.append(addF, test, delForm);
    card.append(buttons);
    const actF = field('actions', 'lines', null, fget('actions'), fput('actions'), 'form:' + name);
    actF.querySelector('span').textContent = t('actionsAfter');
    card.append(actF);
    box.append(card);
  }
  const add = document.createElement('button');
  add.textContent = t('addForm');
  add.onclick = () => {
    before();
    let n = 'formular', i = 2;
    while (menu.forms && n in menu.forms) n = 'formular' + i++;
    setForm(n, { title: de() ? 'Einstellungen' : 'Settings', fields: [{ type: 'text', var: 'name', label: 'Name' }] });
    changed(true);
  };
  box.append(add);
}

// ------------------------------------------------------------------ try it: actions, forms

function log(text) {
  $('logBox').hidden = false;
  const li = document.createElement('li');
  li.textContent = text;
  $('log').prepend(li);
}

const vctx = () => ({ vars: preview, menu });

/** Runs a click in the preview: actions (subset of the plugin's), variable changes, pages. */
async function act(a) {
  if (a.type === 'page') {
    pages[a.id] = a.page;
  } else if (a.type === 'set') {
    preview[a.var] = a.value;
    await runActions(a.then || [], null);
  } else if (a.type === 'actions') {
    await runActions(a.list, a.item);
  }
  changed(false);
  refreshVars();
}

async function runActions(list, item, input = null) {
  for (const raw of list) {
    const ok = await runOne(String(raw).trim(), item, input);
    if (ok === false) break;
  }
}

async function runOne(action, item, input) {
  const i = action.indexOf(':');
  const type = (i < 0 ? action : action.slice(0, i)).trim().toLowerCase();
  const arg = i < 0 ? '' : action.slice(i + 1).trim();
  const f = (s) => fill(input == null ? s : s.replace(/%input%/g, input), vctx(), item);
  switch (type) {
    case 'message': log('💬 ' + plain(f(arg))); break;
    case 'player': log('/' + f(arg).replace(/^\//, '')); break;
    case 'console': log('[console] ' + f(arg).replace(/^\//, '')); break;
    case 'sound': log('♪ ' + arg); break;
    case 'open': log('→ open ' + f(arg)); break;
    case 'back': case 'close': case 'refresh': log('→ ' + type); break;
    case 'set': {
      const [name, ...rest] = arg.split(/\s+/);
      if (rest.length) preview[name.toLowerCase()] = f(rest.join(' '));
      else delete preview[name.toLowerCase()];
      break;
    }
    case 'toggle': {
      const name = arg.toLowerCase();
      preview[name] = varValue(vctx(), name).toLowerCase() === 'true' ? 'false' : 'true';
      break;
    }
    case 'input': {
      const bar = arg.indexOf('|');
      if (bar < 0) break;
      const typed = window.prompt(plain(f(arg.slice(0, bar).trim())), '');
      if (typed != null) await runOne(arg.slice(bar + 1).trim(), item, typed.replace(/[\r\n%]/g, '').replace(/^\/+/, '').slice(0, 100));
      break;
    }
    case 'require': {
      const bar = arg.indexOf(' | ');
      const cond = bar < 0 ? arg : arg.slice(0, bar);
      if (allHold([cond], (s) => f(s))) return true;
      if (bar >= 0) await runOne(arg.slice(bar + 3).trim(), item, input);
      return false;
    }
    case 'form': await openForm(arg.toLowerCase(), item); break;
    default: log('? ' + action);
  }
  return true;
}

/** Shows a form like the client would (native widgets), writes the variables on OK. */
function openForm(name, item) {
  const form = menu.forms?.[name];
  if (!form) { log('? form: ' + name); return Promise.resolve(); }
  const dlg = $('formDialog');
  $('formTitle').textContent = plain(fill(form.title ?? name, vctx(), item));
  $('formOk').textContent = plain(form.ok ?? t('formOk'));
  $('formCancel').textContent = plain(form.cancel ?? t('formCancel'));
  const box = $('formFields');
  box.innerHTML = '';
  const readers = [];
  for (const fd of Array.isArray(form.fields) ? form.fields : []) {
    const name2 = String(fd.var ?? '').toLowerCase();
    const cur = varValue(vctx(), name2);
    const lab = document.createElement('label');
    lab.textContent = plain(fill(fd.label ?? name2, vctx(), item));
    const type = String(fd.type ?? 'text');
    if (type === 'checkbox') {
      const c = document.createElement('input');
      c.type = 'checkbox';
      c.checked = cur.toLowerCase() === 'true';
      lab.prepend(c);
      readers.push(() => [name2, String(c.checked)]);
    } else if (type === 'slider') {
      const r = document.createElement('input');
      r.type = 'range';
      r.min = fd.min ?? 0; r.max = fd.max ?? 10; r.step = fd.step ?? 1;
      r.value = Number.isFinite(Number(cur)) && cur !== '' ? cur : r.min;
      const out = document.createElement('span');
      out.className = 'rangeVal';
      out.textContent = r.value;
      r.oninput = () => { out.textContent = r.value; };
      lab.append(document.createElement('br'), r, out);
      readers.push(() => [name2, String(Number(r.value))]);
    } else if (type === 'choice') {
      const s = document.createElement('select');
      for (const [k, v] of Object.entries(optionsOf(fd.options))) {
        const o = document.createElement('option');
        o.value = k;
        o.textContent = plain(v);
        s.append(o);
      }
      s.value = cur;
      lab.append(s);
      readers.push(() => [name2, s.value]);
    } else {
      const lines = Number(fd.lines) || 1;
      const inp = document.createElement(lines > 1 ? 'textarea' : 'input');
      if (lines > 1) inp.rows = lines; else inp.type = 'text';
      inp.maxLength = Number(fd['max-length']) || 100;
      inp.value = cur;
      lab.append(inp);
      readers.push(() => [name2, inp.value.replace(/[\r\n]/g, ' ').replace(/%/g, '').replace(/^\/+/, '').trim()]);
    }
    box.append(lab);
  }
  return new Promise((resolve) => {
    dlg.onclose = async () => {
      if (dlg.returnValue === 'ok') {
        for (const r of readers) { const [k, v] = r(); preview[k] = v; }
        await runActions(Array.isArray(form.actions) ? form.actions : form.actions ? [form.actions] : [], item);
      }
      changed(false);
      refreshVars();
      resolve();
    };
    dlg.showModal();
  });
}

// ------------------------------------------------------------------ refresh

function refreshYaml(force = false) {
  const ta = $('yamlText');
  if (!force && document.activeElement === ta) return;
  try { ta.value = toYaml(menu); } catch (e) { ta.value = `# ${e.message}`; }
}

function refreshTemplates() {
  const ta = $('templatesYaml');
  if (document.activeElement === ta) return;
  ta.value = menu.templates ? jsyaml.dump(menu.templates, { lineWidth: -1 }) : '';
  $('sharedInfo').textContent = Object.keys(shared).length ? t('sharedLoaded', { n: Object.keys(shared).join(', ') }) : '';
}

/** After a change: redraw, layers, YAML; full=true also rebuilds the side panels. */
function changed(full) {
  save();
  draw();
  refreshLayers();
  if (!$('yamlText').closest('[hidden]')) refreshYaml();
  if (full) {
    refreshProps();
    refreshVars();
    refreshForms();
    refreshTemplates();
  }
}

function refreshSelection() {
  draw();
  refreshLayers();
  refreshProps();
  showTab('element');
}

function refreshAll() {
  save();
  draw();
  refreshLayers();
  refreshMenuProps();
  refreshProps();
  refreshVars();
  refreshForms();
  refreshTemplates();
  refreshYaml(true);
}

function showTab(name) {
  document.querySelectorAll('.side .tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  for (const tab of ['element', 'menu', 'vars', 'forms', 'templates', 'yaml']) {
    document.querySelector('.tab-' + tab).hidden = tab !== name;
  }
  if (name === 'yaml') refreshYaml(true);
}

let toastTimer = 0;
function toast(text, error = false) {
  const el = $('toast');
  el.textContent = text;
  el.classList.toggle('error', error);
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3000);
}

function loadMenu(text, id) {
  try {
    const m = parseYaml(text);
    before();
    menu = m;
    if (id) fileId = id;
    selected = -1;
    preview = {};
    pages = {};
    refreshAll();
    return true;
  } catch (e) {
    toast(`${t('yamlError')}: ${e.message}`, true);
    return false;
  }
}

// ------------------------------------------------------------------ wiring

function init() {
  restore();
  applyI18n();
  document.title = t('title');
  $('lang').textContent = getLang() === 'de' ? 'English' : 'Deutsch';
  $('lang').onclick = () => { setLang(getLang() === 'de' ? 'en' : 'de'); location.reload(); };

  const colors = document.createElement('datalist');
  colors.id = 'colorNames';
  for (const n of ['black', 'dark_blue', 'dark_green', 'dark_aqua', 'dark_red', 'dark_purple', 'gold', 'gray', 'dark_gray',
    'blue', 'green', 'aqua', 'red', 'light_purple', 'yellow', 'white']) {
    const o = document.createElement('option');
    o.value = n;
    colors.append(o);
  }
  document.body.append(colors);

  const skinSel = $('skin');
  for (const [k, s] of Object.entries(SKINS)) skinSel.add(new Option(s.name, k));
  skinSel.add(new Option(t('skinFromEditor'), 'editor'));
  skinSel.value = skinKey;
  skinSel.onchange = () => { skinKey = skinSel.value; skinCache = null; changed(false); };

  for (const type of TYPES) {
    const b = document.createElement('button');
    b.textContent = t('t_' + type);
    b.onclick = () => addElement(type);
    $('paletteBtns').append(b);
  }
  $('layerUp').onclick = () => moveLayer(-1);
  $('layerDown').onclick = () => moveLayer(1);
  $('layerDup').onclick = duplicate;
  $('layerDel').onclick = removeSelected;
  $('undo').onclick = undo;
  $('redo').onclick = redo;

  const setMode = (m) => {
    mode = m;
    $('modeEdit').classList.toggle('active', m === 'edit');
    $('modePlay').classList.toggle('active', m === 'play');
    stage.classList.toggle('play', m === 'play');
    $('tip').hidden = true;
    draw();
  };
  $('modeEdit').onclick = () => setMode('edit');
  $('modePlay').onclick = () => setMode('play');
  $('zoom').onchange = () => { zoom = Number($('zoom').value); draw(); };
  $('snap').onchange = () => { snap = Number($('snap').value); };
  $('textGrid').onchange = draw;
  $('outlines').onchange = draw;
  $('clearLog').onclick = () => { $('log').innerHTML = ''; };

  document.querySelectorAll('.side .tabs button').forEach((b) => { b.onclick = () => showTab(b.dataset.tab); });

  $('newMenu').onclick = () => {
    if (!confirm(t('confirmNew'))) return;
    before();
    menu = STARTER();
    fileId = de() ? 'mein-menue' : 'my-menu';
    selected = -1;
    preview = {};
    refreshAll();
  };
  $('openBtn').onclick = () => $('openFile').click();
  $('openFile').onchange = async () => {
    const f = $('openFile').files[0];
    if (f) loadMenu(await f.text(), f.name.replace(/\.ya?ml$/i, '').toLowerCase());
    $('openFile').value = '';
  };
  $('pasteBtn').onclick = () => {
    showTab('yaml');
    const ta = $('yamlText');
    ta.value = '';
    ta.placeholder = t('pastePrompt');
    ta.focus();
  };
  $('downloadBtn').onclick = () => {
    const blob = new Blob([toYaml(menu)], { type: 'text/yaml' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${fileId || 'menu'}.yml`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  };
  $('applyYaml').onclick = () => { if (loadMenu($('yamlText').value)) toast('✔'); };
  $('copyYaml').onclick = async () => {
    try { await navigator.clipboard.writeText(toYaml(menu)); toast(t('copied')); } catch { $('yamlText').select(); }
  };
  $('applyTemplates').onclick = () => {
    try {
      const text = $('templatesYaml').value.trim();
      const v = text ? jsyaml.load(text) : null;
      if (v != null && (typeof v !== 'object' || Array.isArray(v))) throw new Error('name: element');
      before();
      if (v && Object.keys(v).length) menu.templates = v; else delete menu.templates;
      changed(true);
    } catch (e) {
      toast(`${t('yamlError')}: ${e.message}`, true);
    }
  };
  $('loadShared').onclick = () => $('sharedFile').click();
  $('sharedFile').onchange = async () => {
    for (const f of $('sharedFile').files) {
      try {
        const v = jsyaml.load(await f.text());
        if (v && typeof v === 'object') Object.assign(shared, v);
      } catch (e) {
        toast(`${f.name}: ${e.message}`, true);
      }
    }
    $('sharedFile').value = '';
    changed(true);
  };
  $('addVar').onclick = () => {
    before();
    menu.variables = menu.variables && typeof menu.variables === 'object' ? menu.variables : {};
    let n = 'var', i = 2;
    while (n in menu.variables) n = 'var' + i++;
    menu.variables[n] = '';
    changed(true);
  };
  $('resetPreview').onclick = () => { preview = {}; pages = {}; changed(true); };

  zoom = Number($('zoom').value);
  refreshAll();
}

init();
