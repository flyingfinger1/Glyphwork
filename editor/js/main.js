import { Pix, hex, toImageData } from './pix.js';
import { STYLES, SKINS, a2FromStyle } from './style.js';
import { decodePng } from './png.js';
import { Theme, History, REQUIRED, slug, validateTileSize, checkSize } from './model.js';
import { SheetEditor } from './sheet-editor.js';
import { Preview, PRESET_KEYS } from './preview.js';
import { t, applyI18n, getLang, setLang } from './i18n.js';
import { mirrored } from './tools.js';
import { renderDemo, clickDemo, DemoState } from './demo.js';
import { HIDDEN, TEST_SHAPE } from './seams.js';
import { quarterRole } from './autotile.js';

const $ = (id) => document.getElementById(id);
const TILE_SIZES = [8, 10, 12, 14, 16, 20, 24, 32];

let theme = Theme.restore() || Theme.fresh();
let current = theme.frames.keys().next().value;
let tool = 'pencil';
let color = 0xffc6c6c6;
let mirrorH = false, mirrorV = false;
let genSession = false; // consecutive generator edits share one undo step
const history = new History();

const app = {
  frame: () => theme.frames.get(current),
  frameName: () => current,
  tileSize: () => theme.tileSize,
  history,
  color: () => color,
  tool: () => tool,
  changed: () => { genSession = false; refresh(false); },
  plot: (x, y, c) => {
    const f = theme.frames.get(current);
    for (const [px, py] of mirrored(x, y, theme.tileSize, mirrorH, mirrorV)) f.set(px, py, c);
  },
  picked: (argb) => { setColor(argb); setTool('pencil'); },
};

const editor = new SheetEditor($('sheet'), $('status'), app);
const preview = new Preview($('preview'), $('tiles'), $('previewStatus'), app);

// ---------- helpers ----------

let toastTimer;
function toast(msg, error = false) {
  const el = $('toast');
  el.textContent = msg;
  el.className = 'show' + (error ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = ''; }, error ? 6000 : 3500);
}

let saveTimer;
function scheduleSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => theme.save(), 400);
}

function styleFor(name) {
  return STYLES[name] || STYLES.panel;
}

function setColor(argb) {
  color = argb >>> 0;
  $('color').value = hex(color);
  $('alpha').value = color >>> 24;
  $('swatch').style.setProperty('--c', rgba(color));
}

function rgba(argb) {
  return `rgba(${(argb >>> 16) & 255},${(argb >>> 8) & 255},${argb & 255},${((argb >>> 24) / 255).toFixed(3)})`;
}

function setTool(name) {
  tool = name;
  document.querySelectorAll('[data-tool]').forEach((b) => b.classList.toggle('active', b.dataset.tool === name));
}

// ---------- rendering ----------

function renderFrames() {
  const ul = $('frames');
  ul.innerHTML = '';
  for (const [name, pix] of theme.frames) {
    const li = document.createElement('li');
    li.classList.toggle('active', name === current);
    const c = document.createElement('canvas');
    c.width = pix.w;
    c.height = pix.h;
    c.getContext('2d').putImageData(toImageData(pix), 0, 0);
    const label = document.createElement('span');
    label.textContent = name;
    li.append(c, label);
    if (REQUIRED.includes(name)) {
      const b = document.createElement('span');
      b.className = 'badge';
      b.textContent = t('required');
      li.append(b);
    }
    li.addEventListener('click', () => { current = name; genSession = false; loadGen(); refresh(true); });
    ul.append(li);
  }
}

function renderPalette() {
  const counts = new Map();
  for (const v of app.frame().px) if (v >>> 24) counts.set(v, (counts.get(v) || 0) + 1);
  const el = $('palette');
  el.innerHTML = '';
  [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 32).forEach(([v]) => {
    const s = document.createElement('span');
    s.className = 'swatch';
    s.style.setProperty('--c', rgba(v));
    s.title = hex(v) + ((v >>> 24) < 255 ? ` α${v >>> 24}` : '');
    s.addEventListener('click', () => setColor(v));
    el.append(s);
  });
}

/** Redraws everything; `structure` also rebuilds the frame list and refits the editor. */
function refresh(structure) {
  if (!theme.frames.has(current)) current = theme.frames.keys().next().value;
  $('frameTitle').textContent = current;
  if (structure) {
    renderFrames();
    $('themeName').value = theme.name;
    $('tileSize').value = theme.tileSize;
    $('textColor').value = '#' + (theme.text & 0xffffff).toString(16).padStart(6, '0');
    fitEditor();
  } else {
    editor.render();
    // keep the thumbnail of the active frame current
    const li = $('frames').querySelector('li.active canvas');
    if (li) li.getContext('2d').putImageData(toImageData(app.frame()), 0, 0);
  }
  preview.render();
  renderPalette();
  renderExtras();
  scheduleSave();
}

function fitEditor() {
  const wrap = $('sheetWrap');
  const w = Math.max(200, wrap.clientWidth - 8);
  const h = Math.max(240, Math.min(window.innerHeight * 0.68, 900));
  editor.fit(w, h);
}

// ---------- theme actions ----------

function replaceTheme(next) {
  theme = next;
  current = theme.frames.keys().next().value;
  history.clear();
  genParams.clear();
  genSession = false;
  loadGen();
  refresh(true);
}

$('themeName').addEventListener('input', (e) => { theme.name = e.target.value || 'Skin'; scheduleSave(); });

for (const s of TILE_SIZES) {
  const o = document.createElement('option');
  o.value = s;
  o.textContent = `${s} px`;
  $('tileSize').append(o);
}
$('tileSize').addEventListener('change', (e) => {
  const size = Number(e.target.value);
  if (size === theme.tileSize) return;
  if (!confirm(t('confirmTile'))) {
    e.target.value = theme.tileSize;
    return;
  }
  const next = new Theme(theme.name, size);
  next.text = theme.text;
  for (const name of theme.frames.keys()) next.frames.set(name, a2FromStyle(styleFor(name), size));
  replaceTheme(next);
});

for (const [id, s] of Object.entries(SKINS)) {
  const o = document.createElement('option');
  o.value = id;
  o.textContent = s.name;
  $('template').append(o);
}

$('textColor').addEventListener('input', (e) => {
  theme.text = (0xff000000 | parseInt(e.target.value.slice(1), 16)) >>> 0;
  refresh(false);
});

$('newTheme').addEventListener('click', () => {
  if (confirm(t('confirmNew'))) {
    replaceTheme(Theme.fresh(getLang() === 'de' ? 'Mein Skin' : 'My Skin', theme.tileSize, $('template').value));
  }
});

$('addFrame').addEventListener('click', () => {
  const raw = prompt(t('promptFrameName'));
  if (!raw) return;
  const name = slug(raw);
  if (theme.frames.has(name)) { toast(t('frameExists', { name }), true); return; }
  theme.frames.set(name, a2FromStyle(STYLES.panel, theme.tileSize));
  current = name;
  refresh(true);
});

$('renameFrame').addEventListener('click', () => {
  const raw = prompt(t('promptRename'), current);
  if (!raw) return;
  const name = slug(raw);
  if (name === current) return;
  if (theme.frames.has(name)) { toast(t('frameExists', { name }), true); return; }
  const entries = [...theme.frames].map(([k, v]) => [k === current ? name : k, v]);
  theme.frames = new Map(entries);
  current = name;
  history.clear();
  refresh(true);
});

$('deleteFrame').addEventListener('click', () => {
  if (theme.frames.size <= 1 || !confirm(t('confirmDelete', { name: current }))) return;
  theme.frames.delete(current);
  history.clear();
  refresh(true);
});

$('resetFrame').addEventListener('click', () => {
  if (!confirm(t('confirmReset', { name: current }))) return;
  history.record(current, app.frame());
  app.frame().px.set(a2FromStyle(styleFor(current), theme.tileSize).px);
  refresh(false);
});

$('importBtn').addEventListener('click', () => $('importFile').click());
$('importFile').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file) return;
  try {
    const buf = await file.arrayBuffer();
    if (file.name.toLowerCase().endsWith('.zip')) {
      const imported = await Theme.importZip(buf);
      replaceTheme(imported);
      toast(t('imported', { name: imported.name }));
    } else {
      const pix = await decodePng(new Uint8Array(buf));
      checkSize(pix, theme.tileSize, file.name);
      const name = slug(file.name.replace(/\.png$/i, ''));
      if (theme.frames.has(name)) history.record(name, theme.frames.get(name));
      theme.frames.set(name, pix);
      current = name;
      refresh(true);
      toast(t('imported', { name }));
    }
  } catch (err) {
    toast(t('errImport', { msg: err.message }), true);
  }
});

$('exportBtn').addEventListener('click', async () => {
  const missing = REQUIRED.filter((r) => !theme.frames.has(r));
  if (missing.length && !confirm(t('missingRequired', { list: missing.join(', ') }))) return;
  validateTileSize(theme.tileSize);
  const { id, bytes } = await theme.exportZip();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([bytes], { type: 'application/zip' }));
  a.download = `${id}.zip`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  toast(t('exported', { file: `${id}.zip` }));
});

// ---------- tools ----------

document.querySelectorAll('[data-tool]').forEach((b) => b.addEventListener('click', () => setTool(b.dataset.tool)));
$('undo').addEventListener('click', () => { genSession = false; const n = history.undo(theme); if (n) { current = n; restoreGen(n); refresh(true); } });
$('redo').addEventListener('click', () => { genSession = false; const n = history.redo(theme); if (n) { current = n; restoreGen(n); refresh(true); } });
$('color').addEventListener('input', (e) => {
  const rgb = parseInt(e.target.value.slice(1), 16);
  setColor((((color >>> 24) << 24) | rgb) >>> 0);
});
$('alpha').addEventListener('input', (e) => setColor(((Number(e.target.value) << 24) | (color & 0xffffff)) >>> 0));
$('grid').addEventListener('change', (e) => { editor.showGrid = e.target.checked; editor.render(); });

window.addEventListener('keydown', (e) => {
  if (e.target.matches('input[type=text], select')) return;
  const k = e.key.toLowerCase();
  if ((e.ctrlKey || e.metaKey) && k === 'z') { e.preventDefault(); $(e.shiftKey ? 'redo' : 'undo').click(); return; }
  if ((e.ctrlKey || e.metaKey) && k === 'y') { e.preventDefault(); $('redo').click(); return; }
  const map = { b: 'pencil', e: 'eraser', g: 'fill', l: 'line', r: 'rect', i: 'picker' };
  if (!e.ctrlKey && !e.metaKey && map[k]) setTool(map[k]);
});

// ---------- preview controls ----------

$('zoom').addEventListener('change', (e) => { preview.zoom = Number(e.target.value); preview.render(); renderExtras(); });
$('bg').addEventListener('change', (e) => { preview.bg = e.target.value; preview.render(); renderExtras(); });
$('tintOn').addEventListener('change', (e) => { preview.tintOn = e.target.checked; preview.render(); });
$('tint').addEventListener('input', (e) => { preview.tint = parseInt(e.target.value.slice(1), 16); if (preview.tintOn) preview.render(); });
for (const key of PRESET_KEYS) {
  const b = document.createElement('button');
  b.dataset.i18n = key;
  b.addEventListener('click', () => preview.preset(key));
  $('presets').append(b);
}

// ---------- mirror, preview mode ----------

$('mirrorH').addEventListener('change', (e) => { mirrorH = e.target.checked; });
$('mirrorV').addEventListener('change', (e) => { mirrorV = e.target.checked; });
function setMode(mode) {
  preview.setMode(mode);
  $('modeShape').classList.toggle('active', mode === 'shape');
  $('modePaint').classList.toggle('active', mode === 'paint');
}
$('modeShape').addEventListener('click', () => setMode('shape'));
$('modePaint').addEventListener('click', () => setMode('paint'));

// ---------- generator ----------

const genParams = new Map(); // frame -> style params (kept per frame for this session)
const toHex = (argbValue) => hex(argbValue);
const fromHex = (h) => (0xff000000 | parseInt(h.slice(1), 16)) >>> 0;

function gen() {
  if (!genParams.has(current)) genParams.set(current, { ...styleFor(current), outline: styleFor(current).outline ?? 0xff000000, outlineOn: styleFor(current).outline != null });
  return genParams.get(current);
}

function loadGen() {
  const g = gen();
  $('genFill').value = toHex(g.fill);
  $('genOutlineOn').checked = g.outlineOn;
  $('genOutline').value = toHex(g.outline);
  $('genRound').checked = g.round;
  $('genLightW').value = g.lightWidth;
  $('genLight').value = toHex(g.light);
  $('genShadeW').value = g.shadeWidth;
  $('genShade').value = toHex(g.shade);
}

function beginGen() {
  if (!genSession) history.record(current, app.frame());
  genSession = true;
}

function applyGen() {
  beginGen();
  const g = gen();
  g.fill = fromHex($('genFill').value);
  g.outlineOn = $('genOutlineOn').checked;
  g.outline = fromHex($('genOutline').value);
  g.round = $('genRound').checked;
  g.lightWidth = Math.max(0, Math.min(6, Number($('genLightW').value) || 0));
  g.light = fromHex($('genLight').value);
  g.shadeWidth = Math.max(0, Math.min(6, Number($('genShadeW').value) || 0));
  g.shade = fromHex($('genShade').value);
  app.frame().px.set(a2FromStyle({ ...g, outline: g.outlineOn ? g.outline : null }, theme.tileSize).px);
  refresh(false);
}

for (const id of ['genFill', 'genOutlineOn', 'genOutline', 'genRound', 'genLightW', 'genLight', 'genShadeW', 'genShade']) {
  $(id).addEventListener('input', applyGen);
}
document.querySelectorAll('[data-gen]').forEach((b) => b.addEventListener('click', () => {
  const st = STYLES[b.dataset.gen];
  genSession = false;
  beginGen();
  genParams.set(current, { ...st, outline: st.outline ?? 0xff000000, outlineOn: st.outline != null });
  loadGen();
  applyGen();
}));
history.meta = (name) => (genParams.has(name) ? { ...genParams.get(name) } : null);

function restoreGen(name) {
  if (history.lastMeta) genParams.set(name, history.lastMeta);
  else genParams.delete(name);
  loadGen();
}

// ---------- tabs: demo scene, seams ----------

let tab = 'shapes';
const demoState = new DemoState();
let demoTargets = [];

function renderDemoTab() {
  const z = preview.zoom;
  const { pix, targets } = renderDemo(theme.frames, theme.tileSize, demoState, getLang(), theme.text);
  demoTargets = targets;
  const c = $('demo');
  c.width = pix.w * z;
  c.height = pix.h * z;
  const ctx = c.getContext('2d');
  ctx.fillStyle = preview.bg === 'light' ? '#d8dce4' : '#1c1f26';
  ctx.fillRect(0, 0, c.width, c.height);
  const off = document.createElement('canvas');
  off.width = pix.w;
  off.height = pix.h;
  off.getContext('2d').putImageData(toImageData(pix), 0, 0);
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(off, 0, 0, c.width, c.height);
}

$('demo').addEventListener('click', (e) => {
  const c = $('demo');
  const r = c.getBoundingClientRect();
  const z = preview.zoom * (r.width / c.width);
  if (clickDemo(demoTargets, demoState, Math.floor((e.clientX - r.left) / z), Math.floor((e.clientY - r.top) / z), getLang())) renderDemoTab();
});

let activeSeam = null;
function roleName(q) {
  const role = quarterRole(q[0], q[1]);
  return t(role === 'preview' ? 'preview_role' : role);
}

function renderSeamsTab() {
  const list = $('seamList');
  list.innerHTML = '';
  const q = theme.tileSize / 2, z = Math.max(4, Math.floor(48 / q));
  const a2 = app.frame();
  for (const p of HIDDEN) {
    const item = document.createElement('div');
    item.className = 'seam' + (activeSeam === p.key ? ' active' : '');
    const c = document.createElement('canvas');
    const w = p.dir === 'h' ? 2 * q : q, h = p.dir === 'h' ? q : 2 * q;
    c.width = w * z;
    c.height = h * z;
    const pix = new Pix(w, h);
    pix.blit(a2, p.a[0] * q, p.a[1] * q, 0, 0, q, q);
    pix.blit(a2, p.b[0] * q, p.b[1] * q, p.dir === 'h' ? q : 0, p.dir === 'h' ? 0 : q, q, q);
    const off = document.createElement('canvas');
    off.width = w;
    off.height = h;
    off.getContext('2d').putImageData(toImageData(pix), 0, 0);
    const ctx = c.getContext('2d');
    for (let y = 0; y < h * 2; y++) {
      for (let x = 0; x < w * 2; x++) {
        ctx.fillStyle = (x + y) & 1 ? '#3a3a44' : '#4a4a56';
        ctx.fillRect(x * z / 2, y * z / 2, z / 2, z / 2);
      }
    }
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(off, 0, 0, c.width, c.height);
    ctx.strokeStyle = '#e040fb';
    ctx.setLineDash([3, 3]);
    ctx.beginPath();
    if (p.dir === 'h') { ctx.moveTo(q * z, 0); ctx.lineTo(q * z, c.height); } else { ctx.moveTo(0, q * z); ctx.lineTo(c.width, q * z); }
    ctx.stroke();
    const label = document.createElement('div');
    label.textContent = `${roleName(p.a)} ${p.dir === 'h' ? '→' : '↓'} ${roleName(p.b)}`;
    item.append(c, label);
    item.addEventListener('click', () => {
      activeSeam = activeSeam === p.key ? null : p.key;
      editor.marks = activeSeam ? [p.a, p.b] : [];
      editor.render();
      renderSeamsTab();
    });
    list.append(item);
  }
}

function renderExtras() {
  if (tab === 'demo') renderDemoTab();
  if (tab === 'seams') renderSeamsTab();
}

function setTab(name) {
  tab = name;
  document.querySelectorAll('[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  for (const n of ['shapes', 'demo', 'seams']) document.querySelectorAll('.tab-' + n).forEach((el) => { el.hidden = n !== name; });
  if (name !== 'seams' && editor.marks.length) { editor.marks = []; activeSeam = null; editor.render(); }
  renderExtras();
}
document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => setTab(b.dataset.tab)));
$('seamTest').addEventListener('click', () => {
  setTab('shapes');
  preview.setCells(TEST_SHAPE);
  toast(t('seamTestLoaded'));
});

// ---------- palettes ----------

const PALETTES = {
  gui: [0xff000000, 0xff373737, 0xff404040, 0xff555555, 0xff8b8b8b, 0xffa0a0a0, 0xffc6c6c6, 0xffdbdbdb, 0xffffffff, 0x00000000],
  chat: [0xff000000, 0xff0000aa, 0xff00aa00, 0xff00aaaa, 0xffaa0000, 0xffaa00aa, 0xffffaa00, 0xffaaaaaa,
    0xff555555, 0xff5555ff, 0xff55ff55, 0xff55ffff, 0xffff5555, 0xffff55ff, 0xffffff55, 0xffffffff],
};
let ownPalette = [];
try { ownPalette = JSON.parse(localStorage.getItem('glyphwork-editor-palette') || '[]'); } catch { ownPalette = []; }
const saveOwn = () => { try { localStorage.setItem('glyphwork-editor-palette', JSON.stringify(ownPalette)); } catch { /* ignore */ } };

function renderPalettes() {
  const which = $('paletteSel').value;
  const colors = which === 'own' ? ownPalette : PALETTES[which];
  const el = $('paletteSwatches');
  el.innerHTML = '';
  colors.forEach((v, i) => {
    const s = document.createElement('span');
    s.className = 'swatch';
    s.style.setProperty('--c', rgba(v));
    s.title = (v >>> 24) === 0 ? t('transparent') : hex(v) + ((v >>> 24) < 255 ? ` α${v >>> 24}` : '');
    s.addEventListener('click', () => setColor(v));
    if (which === 'own') {
      s.addEventListener('contextmenu', (e) => { e.preventDefault(); ownPalette.splice(i, 1); saveOwn(); renderPalettes(); });
    }
    el.append(s);
  });
  $('paletteAdd').hidden = which !== 'own';
}
$('paletteSel').addEventListener('change', renderPalettes);
$('paletteAdd').addEventListener('click', () => {
  if (!ownPalette.includes(color)) ownPalette.push(color);
  saveOwn();
  renderPalettes();
});

// ---------- language & start ----------

function renderLangButton() {
  $('lang').textContent = t('language');
  document.title = t('title');
}
$('lang').addEventListener('click', () => {
  setLang(getLang() === 'de' ? 'en' : 'de');
  renderLangButton();
  $('status').textContent = t('hoverNone');
  preview.setMode(preview.mode);
  refresh(true);
});

window.addEventListener('resize', () => fitEditor());

applyI18n();
renderLangButton();
$('status').textContent = t('hoverNone');
setTool('pencil');
setColor(color);
loadGen();
preview.setMode('shape');
renderPalettes();
refresh(true);

// for debugging in the console
window.glyphwork = { get theme() { return theme; }, Pix };
