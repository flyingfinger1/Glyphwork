// Minecraft textures for the designer, taken from the user's own client jar or resource packs.
// Nothing is uploaded: the archives are read in the browser, only the PNGs the designer needs (item
// textures of the plugin's icon table, the font sheets, items of packs) are kept in IndexedDB.

import { readZip } from '../zip.js';
import { Pix } from '../pix.js';

const DB = 'glyphwork-designer-assets';
const STORE = 'files';
const META = 'meta';

let catalogPromise = null;
let fontPromise = null;

/** The plugin's icon table: {textures: [[id, w, frame, frames, advance, native?]], items: {id: [[texture, tint]]}}. */
export function catalog() {
  catalogPromise ??= fetch(new URL('../../data/item_icons.json', import.meta.url)).then((r) => r.json());
  return catalogPromise;
}

/** The plugin's font table: {providers: [{file, height, ascent, chars}], advances, spaces}. */
export function fontTable() {
  fontPromise ??= fetch(new URL('../../data/vanilla_text.json', import.meta.url)).then((r) => r.json());
  return fontPromise;
}

/** "minecraft:item/diamond" -> "assets/minecraft/textures/item/diamond.png" */
export function texturePath(id) {
  const [ns, path] = id.includes(':') ? id.split(':', 2) : ['minecraft', id];
  return `assets/${ns}/textures/${path}${path.endsWith('.png') ? '' : '.png'}`;
}

function open() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => {
      req.result.createObjectStore(STORE);
      req.result.createObjectStore(META);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function tx(db, stores, mode, fn) {
  return new Promise((resolve, reject) => {
    const t = db.transaction(stores, mode);
    const out = fn(t);
    t.oncomplete = () => resolve(out);
    t.onerror = () => reject(t.error);
  });
}

const all = (store) => new Promise((resolve, reject) => {
  const keys = store.getAllKeys();
  const vals = store.getAll();
  vals.onsuccess = () => resolve(keys.result.map((k, i) => [k, vals.result[i]]));
  vals.onerror = () => reject(vals.error);
});

/**
 * Reads a client jar or resource pack and keeps the textures the designer can use. Later archives
 * replace textures of earlier ones (like resource packs on top of the game).
 * @returns {name, textures, font}
 */
export async function importArchive(buffer, name) {
  const cat = await catalog();
  const font = await fontTable();
  const wanted = new Set(cat.textures.map((t) => texturePath(t[0])));
  for (const p of font.providers) wanted.add(texturePath(p.file));
  // items of resource packs: every item texture outside the minecraft namespace
  const custom = (n) => /^assets\/[a-z0-9_.-]+\/textures\/item\/.+\.png$/.test(n) && !n.startsWith('assets/minecraft/');
  const files = await readZip(buffer, (n) => wanted.has(n) || custom(n));
  if (!files.size) throw new Error('no Minecraft textures found (client jar or resource pack expected)');
  const db = await open();
  await tx(db, [STORE, META], 'readwrite', (t) => {
    const s = t.objectStore(STORE);
    for (const [path, data] of files) s.put(data, path);
    const m = t.objectStore(META);
    const req = m.get('sources');
    req.onsuccess = () => m.put([...(req.result || []).filter((x) => x !== name), name], 'sources');
  });
  db.close();
  const fontFiles = font.providers.filter((p) => files.has(texturePath(p.file))).length;
  return { name, textures: files.size - fontFiles, font: fontFiles === font.providers.length };
}

/** Everything stored: {sources: [names], images: Map(path -> Pix)}; empty if nothing was loaded. */
export async function loadStored() {
  let db;
  try {
    db = await open();
  } catch {
    return { sources: [], images: new Map() };
  }
  const [entries, sources] = await tx(db, [STORE, META], 'readonly', (t) => {
    const p1 = all(t.objectStore(STORE));
    const r = t.objectStore(META).get('sources');
    const p2 = new Promise((res) => { r.onsuccess = () => res(r.result || []); r.onerror = () => res([]); });
    return Promise.all([p1, p2]);
  }).then((p) => p);
  db.close();
  const images = new Map();
  const pictures = new Map();
  for (const [path, data] of entries) {
    try {
      if (path.startsWith(PICTURE)) pictures.set(path.slice(PICTURE.length), await decode(data));
      else images.set(path, await decode(data));
    } catch { /* not a PNG the browser can read */ }
  }
  return { sources, images, pictures };
}

/** Key prefix of the server's own pictures (images/) loaded into the designer. */
const PICTURE = 'picture:';

/**
 * Stores PNGs of the server's images/ folder. Files from a folder pick keep their path below the
 * picked folder ("ranks/gold"), single files their name; ids are lower case without ".png".
 * @returns the ids stored
 */
export async function importPictures(files) {
  const db = await open();
  const ids = [];
  const items = [];
  for (const f of files) {
    if (!/\.png$/i.test(f.name)) continue;
    const rel = f.webkitRelativePath ? f.webkitRelativePath.split('/').slice(1).join('/') : f.name;
    const id = rel.replace(/\.png$/i, '').toLowerCase();
    items.push([id, new Uint8Array(await f.arrayBuffer())]);
  }
  await tx(db, [STORE], 'readwrite', (t) => {
    for (const [id, data] of items) {
      t.objectStore(STORE).put(data, PICTURE + id);
      ids.push(id);
    }
  });
  db.close();
  return ids;
}

/** PNG bytes -> Pix through the browser (handles every PNG type: palette, gray, 16 bit ...). */
async function decode(data) {
  const bmp = await createImageBitmap(new Blob([data], { type: 'image/png' }), { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
  const canvas = new OffscreenCanvas(bmp.width, bmp.height);
  const g = canvas.getContext('2d', { willReadFrequently: true });
  g.drawImage(bmp, 0, 0);
  const d = g.getImageData(0, 0, bmp.width, bmp.height).data;
  const pix = new Pix(bmp.width, bmp.height);
  for (let i = 0; i < pix.px.length; i++) {
    pix.px[i] = ((d[i * 4 + 3] << 24) | (d[i * 4] << 16) | (d[i * 4 + 1] << 8) | d[i * 4 + 2]) >>> 0;
  }
  bmp.close();
  return pix;
}

export async function clearStored() {
  const db = await open();
  await tx(db, [STORE, META], 'readwrite', (t) => {
    t.objectStore(STORE).clear();
    t.objectStore(META).clear();
  });
  db.close();
}

/**
 * Icon lookup for the renderer: item id -> [{img, frame, tint}] or null. Vanilla items use the
 * plugin's table; "ns:name" items of packs use their texture assets/ns/textures/item/name.png.
 */
export function iconSource(cat, images) {
  // t[5] === 1: a GUI sprite ("gui:hud/heart/full"), drawn at its own size
  const tex = cat.textures.map((t) => ({ path: texturePath(t[0]), frame: t[2], native: t[5] === 1 }));
  return (item) => {
    let id = String(item).toLowerCase();
    if (id.startsWith('minecraft:')) id = id.slice(10);
    const layers = cat.items[id];
    if (layers) {
      const out = [];
      for (const [i, tint] of layers) {
        const img = images.get(tex[i].path);
        if (!img) return null;
        out.push({ img, frame: tex[i].frame, native: tex[i].native, tint });
      }
      return out;
    }
    if (id.includes(':')) {
      const img = images.get(texturePath(id.replace(':', ':item/')));
      if (img) return [{ img, frame: img.w, tint: null }];
    }
    return null;
  };
}
