// Pixel buffer with ARGB uint32 values (same layout as Java's BufferedImage.getRGB).

export class Pix {
  constructor(w, h, px) {
    this.w = w;
    this.h = h;
    this.px = px || new Uint32Array(w * h);
  }

  get(x, y) {
    return this.px[y * this.w + x];
  }

  set(x, y, argb) {
    if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.px[y * this.w + x] = argb >>> 0;
  }

  inside(x, y) {
    return x >= 0 && y >= 0 && x < this.w && y < this.h;
  }

  clone() {
    return new Pix(this.w, this.h, new Uint32Array(this.px));
  }

  /** Copies a w x h block from src (sx, sy) to this (dx, dy). */
  blit(src, sx, sy, dx, dy, w, h) {
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) this.set(dx + x, dy + y, src.get(sx + x, sy + y));
    }
  }

  equals(o) {
    if (o.w !== this.w || o.h !== this.h) return false;
    for (let i = 0; i < this.px.length; i++) if (this.px[i] !== o.px[i]) return false;
    return true;
  }
}

export const alpha = (argb) => argb >>> 24;

export function argb(a, r, g, b) {
  return ((a << 24) | (r << 16) | (g << 8) | b) >>> 0;
}

export function hex(argbValue) {
  return '#' + (argbValue & 0xffffff).toString(16).padStart(6, '0');
}

/** Multiplies the RGB of a pixel with a tint (0xRRGGBB), like the client's text color. */
export function tint(argbValue, rgb) {
  const r = (((argbValue >>> 16) & 255) * ((rgb >>> 16) & 255) / 255) | 0;
  const g = (((argbValue >>> 8) & 255) * ((rgb >>> 8) & 255) / 255) | 0;
  const b = ((argbValue & 255) * (rgb & 255) / 255) | 0;
  return argb(argbValue >>> 24, r, g, b);
}

/** Browser only: Pix -> ImageData (optionally tinted). */
export function toImageData(p, tintRgb = 0xffffff) {
  const id = new ImageData(p.w, p.h);
  const d = id.data;
  for (let i = 0; i < p.px.length; i++) {
    const v = tintRgb === 0xffffff ? p.px[i] : tint(p.px[i], tintRgb);
    d[i * 4] = (v >>> 16) & 255;
    d[i * 4 + 1] = (v >>> 8) & 255;
    d[i * 4 + 2] = v & 255;
    d[i * 4 + 3] = v >>> 24;
  }
  return id;
}
