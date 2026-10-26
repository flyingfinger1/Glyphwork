// Drawing primitives shared by the sheet editor and the in-window painter, plus A2 mirroring.

/** Calls plot(x, y) for every point of a Bresenham line. */
export function line(x0, y0, x1, y1, plot) {
  const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
  const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
  let err = dx + dy;
  for (;;) {
    plot(x0, y0);
    if (x0 === x1 && y0 === y1) break;
    const e2 = 2 * err;
    if (e2 >= dy) { err += dy; x0 += sx; }
    if (e2 <= dx) { err += dx; y0 += sy; }
  }
}

/** Calls plot for the outline of the rectangle spanned by two corners. */
export function rect(x0, y0, x1, y1, plot) {
  const [ax, bx] = [Math.min(x0, x1), Math.max(x0, x1)];
  const [ay, by] = [Math.min(y0, y1), Math.max(y0, y1)];
  for (let x = ax; x <= bx; x++) { plot(x, ay); plot(x, by); }
  for (let y = ay + 1; y < by; y++) { plot(ax, y); plot(bx, y); }
}

/** Pixels of the 4-connected same-color area at (x, y), limited to region [rx, ry, rw, rh]. */
export function floodArea(pix, x, y, [rx, ry, rw, rh]) {
  const target = pix.get(x, y);
  const seen = new Set();
  const out = [];
  const stack = [[x, y]];
  while (stack.length) {
    const [cx, cy] = stack.pop();
    if (cx < rx || cy < ry || cx >= rx + rw || cy >= ry + rh) continue;
    const k = cy * pix.w + cx;
    if (seen.has(k) || pix.get(cx, cy) !== target) continue;
    seen.add(k);
    out.push([cx, cy]);
    stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
  }
  return out;
}

/**
 * The A2 sheet consists of three independent blocks: the preview tile, the inner-corner tile and
 * the 2x2-tile frame. Mirroring happens inside the block a pixel belongs to.
 */
export function blockOf(x, y, t) {
  if (y < t) return x < t ? [0, 0, t, t] : [t, 0, t, t];
  return [0, t, 2 * t, 2 * t];
}

/** (x, y) plus its mirror images inside its block. */
export function mirrored(x, y, t, h, v) {
  const [bx, by, bw, bh] = blockOf(x, y, t);
  const mx = bx + bw - 1 - (x - bx);
  const my = by + bh - 1 - (y - by);
  const pts = [[x, y]];
  if (h) pts.push([mx, y]);
  if (v) pts.push([x, my]);
  if (h && v) pts.push([mx, my]);
  return pts;
}
