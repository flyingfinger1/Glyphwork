// Port of de.flyingfinger.glyphwork.theme.Autotile. Must produce the same 47 tiles as the plugin
// (checked by tools/editor-parity.mjs).
//
// A2 sheet (tile size T, quarter q = T/2), 2 x 3 tiles = 4 x 6 quarters:
//   tile (0,0): preview, not used for lookup
//   tile (1,0): inner corners; quarter (2,0) TL, (3,0) TR, (2,1) BL, (3,1) BR
//   tiles (0..1, 1..2): a 2x2-tile frame: outer corners, edges, fill

import { Pix } from './pix.js';

export const N = 1, NE = 2, E = 4, SE = 8, S = 16, SW = 32, W = 64, NW = 128;

export function canonical(m) {
  let r = m & (N | E | S | W);
  if (m & NE && m & N && m & E) r |= NE;
  if (m & SE && m & S && m & E) r |= SE;
  if (m & SW && m & S && m & W) r |= SW;
  if (m & NW && m & N && m & W) r |= NW;
  return r;
}

export const MASKS = [...new Set(Array.from({ length: 256 }, (_, m) => canonical(m)))].sort((a, b) => a - b);
const INDEX = Array.from({ length: 256 }, (_, m) => MASKS.indexOf(canonical(m)));

export const index = (mask) => INDEX[mask & 255];

const cell = (cells, c, r) => r >= 0 && r < cells.length && c >= 0 && c < cells[r].length && cells[r][c];

/** Neighbour mask of cell (c, r) in cells[row][col]. */
export function mask(cells, c, r) {
  let m = 0;
  if (cell(cells, c, r - 1)) m |= N;
  if (cell(cells, c + 1, r - 1)) m |= NE;
  if (cell(cells, c + 1, r)) m |= E;
  if (cell(cells, c + 1, r + 1)) m |= SE;
  if (cell(cells, c, r + 1)) m |= S;
  if (cell(cells, c - 1, r + 1)) m |= SW;
  if (cell(cells, c - 1, r)) m |= W;
  if (cell(cells, c - 1, r - 1)) m |= NW;
  return m;
}

/**
 * Source quarter (col, row) in the A2 sheet for quadrant (qx, qy) of a tile with the given mask.
 * This is the single rule both the tile builder and the editor's linked painting use.
 */
export function quarterFor(m, qx, qy) {
  const h = (m & (qx === 0 ? W : E)) !== 0;
  const v = (m & (qy === 0 ? N : S)) !== 0;
  const diag = qy === 0 ? (qx === 0 ? NW : NE) : (qx === 0 ? SW : SE);
  const d = (m & diag) !== 0;
  if (h && v && !d) return [2 + qx, qy];
  return [qx === 0 ? (h ? 2 : 0) : (h ? 1 : 3), qy === 0 ? (v ? 4 : 2) : (v ? 3 : 5)];
}

/** The 47 tiles (order = MASKS) from an A2 sheet of 2T x 3T. */
export function tiles(a2, t) {
  if (t % 2 !== 0 || a2.w !== 2 * t || a2.h !== 3 * t) {
    throw new Error(`A2 sheet must be ${2 * t}x${3 * t} px for tile size ${t}`);
  }
  const q = t / 2;
  return MASKS.map((m) => {
    const tile = new Pix(t, t);
    for (let qy = 0; qy < 2; qy++) {
      for (let qx = 0; qx < 2; qx++) {
        const [col, row] = quarterFor(m, qx, qy);
        tile.blit(a2, col * q, row * q, qx * q, qy * q, q, q);
      }
    }
    return tile;
  });
}

/** Renders a cell shape with the given tiles into one image (cols*T x rows*T). */
export function renderShape(tileList, cells, t) {
  const rows = cells.length;
  const cols = Math.max(...cells.map((r) => r.length));
  const out = new Pix(cols * t, rows * t);
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cells[r].length; c++) {
      if (cells[r][c]) out.blit(tileList[index(mask(cells, c, r))], 0, 0, c * t, r * t, t, t);
    }
  }
  return out;
}

/** Quarter names for the UI (key = "col,row"). */
export function quarterRole(col, row) {
  if (row < 2) {
    if (col < 2) return 'preview';
    return ['innerTL', 'innerTR', 'innerBL', 'innerBR'][(col - 2) + row * 2];
  }
  const top = row === 2, bottom = row === 5, left = col === 0, right = col === 3;
  if (top && left) return 'cornerTL';
  if (top && right) return 'cornerTR';
  if (bottom && left) return 'cornerBL';
  if (bottom && right) return 'cornerBR';
  if (top) return 'edgeTop';
  if (bottom) return 'edgeBottom';
  if (left) return 'edgeLeft';
  if (right) return 'edgeRight';
  return 'fill';
}
