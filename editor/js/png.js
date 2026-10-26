// Minimal exact PNG codec (no canvas, so semi-transparent colors stay untouched).
// Encodes RGBA 8-bit. Decodes 8-bit grayscale, RGB, palette (+tRNS), gray+alpha and RGBA,
// non-interlaced. Works in browsers and Node (CompressionStream / DecompressionStream).

import { Pix } from './pix.js';
import { crc32 } from './zip.js';

const SIG = [137, 80, 78, 71, 13, 10, 26, 10];

async function pump(stream, data) {
  const out = new Response(new Blob([data]).stream().pipeThrough(stream));
  return new Uint8Array(await out.arrayBuffer());
}

const be32 = (v) => [(v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255];

function chunk(type, data) {
  const t = [...type].map((c) => c.charCodeAt(0));
  const body = new Uint8Array(4 + data.length);
  body.set(t, 0);
  body.set(data, 4);
  return [...be32(data.length), ...body, ...be32(crc32(body))];
}

export async function encodePng(pix) {
  const raw = new Uint8Array(pix.h * (1 + pix.w * 4));
  let o = 0;
  for (let y = 0; y < pix.h; y++) {
    raw[o++] = 0; // filter: none
    for (let x = 0; x < pix.w; x++) {
      const v = pix.get(x, y);
      raw[o++] = (v >>> 16) & 255;
      raw[o++] = (v >>> 8) & 255;
      raw[o++] = v & 255;
      raw[o++] = v >>> 24;
    }
  }
  const idat = await pump(new CompressionStream('deflate'), raw);
  const ihdr = new Uint8Array([...be32(pix.w), ...be32(pix.h), 8, 6, 0, 0, 0]);
  return new Uint8Array([...SIG, ...chunk('IHDR', ihdr), ...chunk('IDAT', idat), ...chunk('IEND', new Uint8Array(0))]);
}

export async function decodePng(bytes) {
  const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < 8; i++) if (b[i] !== SIG[i]) throw new Error('not a PNG');
  const dv = new DataView(b.buffer, b.byteOffset, b.byteLength);
  let p = 8, w = 0, h = 0, depth = 0, type = 0, interlace = 0, palette = null, trns = null;
  const idat = [];
  while (p < b.length) {
    const len = dv.getUint32(p);
    const t = String.fromCharCode(b[p + 4], b[p + 5], b[p + 6], b[p + 7]);
    const data = b.subarray(p + 8, p + 8 + len);
    if (t === 'IHDR') {
      w = dv.getUint32(p + 8); h = dv.getUint32(p + 12);
      depth = data[8]; type = data[9]; interlace = data[12];
    } else if (t === 'PLTE') palette = data;
    else if (t === 'tRNS') trns = data;
    else if (t === 'IDAT') idat.push(data);
    else if (t === 'IEND') break;
    p += 12 + len;
  }
  if (depth !== 8 || interlace !== 0) throw new Error(`unsupported PNG (bit depth ${depth}, interlace ${interlace}); save as 8-bit RGBA`);
  const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type];
  if (!ch) throw new Error('unsupported PNG color type ' + type);
  const all = new Uint8Array(idat.reduce((s, d) => s + d.length, 0));
  let off = 0;
  for (const d of idat) { all.set(d, off); off += d.length; }
  const raw = await pump(new DecompressionStream('deflate'), all);
  const stride = w * ch;
  const cur = new Uint8Array(stride), prev = new Uint8Array(stride);
  const pix = new Pix(w, h);
  let rp = 0;
  for (let y = 0; y < h; y++) {
    const f = raw[rp++];
    for (let i = 0; i < stride; i++) {
      const x = raw[rp++];
      const a = i >= ch ? cur[i - ch] : 0, up = prev[i], c = i >= ch ? prev[i - ch] : 0;
      let v;
      switch (f) {
        case 0: v = x; break;
        case 1: v = x + a; break;
        case 2: v = x + up; break;
        case 3: v = x + ((a + up) >> 1); break;
        case 4: {
          const pp = a + up - c, pa = Math.abs(pp - a), pb = Math.abs(pp - up), pc = Math.abs(pp - c);
          v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? up : c);
          break;
        }
        default: throw new Error('bad PNG filter ' + f);
      }
      cur[i] = v & 255;
    }
    for (let x = 0; x < w; x++) {
      const i = x * ch;
      let r, g, bl, al = 255;
      if (type === 6) { r = cur[i]; g = cur[i + 1]; bl = cur[i + 2]; al = cur[i + 3]; }
      else if (type === 2) {
        r = cur[i]; g = cur[i + 1]; bl = cur[i + 2];
        if (trns && trns.length >= 6 && r === trns[1] && g === trns[3] && bl === trns[5]) al = 0;
      } else if (type === 0) {
        r = g = bl = cur[i];
        if (trns && trns.length >= 2 && r === trns[1]) al = 0;
      } else if (type === 4) { r = g = bl = cur[i]; al = cur[i + 1]; }
      else {
        const k = cur[i];
        r = palette[k * 3]; g = palette[k * 3 + 1]; bl = palette[k * 3 + 2];
        al = trns && k < trns.length ? trns[k] : 255;
      }
      pix.set(x, y, ((al << 24) | (r << 16) | (g << 8) | bl) >>> 0);
    }
    prev.set(cur);
  }
  return pix;
}
