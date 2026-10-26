import { Pix, hex, toImageData } from './pix.js';
import { STYLES, a2FromStyle } from './style.js';
import { decodePng } from './png.js';
import { Theme, History, REQUIRED, slug, validateTileSize, checkSize } from './model.js';
import { SheetEditor } from './sheet-editor.js';
import { Preview, PRESET_KEYS } from './preview.js';
import { t, applyI18n, getLang, setLang } from './i18n.js';

const $ = (id) => document.getElementById(id);
const TILE_SIZES = [8, 10, 12, 14, 16, 20, 24, 32];

let theme = Theme.restore() || Theme.fresh();
let current = theme.frames.keys().next().value;
let tool = 'pencil';
let color = 0xffc6c6c6;
const history = new History();

const app = {
  frame: () => theme.frames.get(current),
  frameName: () => current,
  tileSize: () => theme.tileSize,
  history,
  color: () => color,
  tool: () => tool,
  changed: () => refresh(false),
  picked: (argb) => { setColor(argb); setTool('pencil'); },
};

const editor = new SheetEditor($('sheet'), $('status'), app);
const preview = new Preview($('preview'), $('tiles'), app);

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
    li.addEventListener('click', () => { current = name; refresh(true); });
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
    fitEditor();
  } else {
    editor.render();
    // keep the thumbnail of the active frame current
    const li = $('frames').querySelector('li.active canvas');
    if (li) li.getContext('2d').putImageData(toImageData(app.frame()), 0, 0);
  }
  preview.render();
  renderPalette();
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
  for (const name of theme.frames.keys()) next.frames.set(name, a2FromStyle(styleFor(name), size));
  replaceTheme(next);
});

$('newTheme').addEventListener('click', () => {
  if (confirm(t('confirmNew'))) replaceTheme(Theme.fresh(getLang() === 'de' ? 'Mein Skin' : 'My Skin', theme.tileSize));
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
$('undo').addEventListener('click', () => { const n = history.undo(theme); if (n) { current = n; refresh(true); } });
$('redo').addEventListener('click', () => { const n = history.redo(theme); if (n) { current = n; refresh(true); } });
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

$('zoom').addEventListener('change', (e) => { preview.zoom = Number(e.target.value); preview.render(); });
$('bg').addEventListener('change', (e) => { preview.bg = e.target.value; preview.render(); });
$('tintOn').addEventListener('change', (e) => { preview.tintOn = e.target.checked; preview.render(); });
$('tint').addEventListener('input', (e) => { preview.tint = parseInt(e.target.value.slice(1), 16); if (preview.tintOn) preview.render(); });
for (const key of PRESET_KEYS) {
  const b = document.createElement('button');
  b.dataset.i18n = key;
  b.addEventListener('click', () => preview.preset(key));
  $('presets').append(b);
}

// ---------- language & start ----------

function renderLangButton() {
  $('lang').textContent = t('language');
  document.title = t('title');
}
$('lang').addEventListener('click', () => {
  setLang(getLang() === 'de' ? 'en' : 'de');
  renderLangButton();
  $('status').textContent = t('hoverNone');
  refresh(true);
});

window.addEventListener('resize', () => fitEditor());

applyI18n();
renderLangButton();
$('status').textContent = t('hoverNone');
setTool('pencil');
setColor(color);
refresh(true);

// for debugging in the console
window.glyphwork = { get theme() { return theme; }, Pix };
