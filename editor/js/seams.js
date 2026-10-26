// Seams: which A2 quarters can end up next to each other in some shape. 28 of the 68 possible
// transitions are visible in the A2 sheet itself (the quarters are neighbours there); the other
// 40 are "hidden" and only show up in shapes. Neighbouring pixels may differ legitimately (bevel
// ends, inner corners), so there is no automatic verdict; the editor shows the hidden transitions
// and a test shape that contains every transition at least once.

import { MASKS, mask, quarterFor } from './autotile.js';

const key = (a, b, dir) => `${dir}:${a}:${b}`;

/** Quarter transitions inside one cell shape: Set of keys. */
export function transitions(cells) {
  const s = new Set();
  const q = (c, r, qx, qy) => quarterFor(mask(cells, c, r), qx, qy);
  for (let r = 0; r < cells.length; r++) {
    for (let c = 0; c < cells[r].length; c++) {
      if (!cells[r][c]) continue;
      s.add(key(q(c, r, 0, 0), q(c, r, 1, 0), 'h'));
      s.add(key(q(c, r, 0, 1), q(c, r, 1, 1), 'h'));
      s.add(key(q(c, r, 0, 0), q(c, r, 0, 1), 'v'));
      s.add(key(q(c, r, 1, 0), q(c, r, 1, 1), 'v'));
      if (cells[r][c + 1]) {
        s.add(key(q(c, r, 1, 0), q(c + 1, r, 0, 0), 'h'));
        s.add(key(q(c, r, 1, 1), q(c + 1, r, 0, 1), 'h'));
      }
      if (cells[r + 1]?.[c]) {
        s.add(key(q(c, r, 0, 1), q(c, r + 1, 0, 0), 'v'));
        s.add(key(q(c, r, 1, 1), q(c, r + 1, 1, 0), 'v'));
      }
    }
  }
  return s;
}

/** All transitions that can occur: {a:[col,row], b:[col,row], dir:'h'|'v', hidden}. */
export const PAIRS = (() => {
  const seen = new Map();
  const add = (a, b, dir) => {
    const k = key(a, b, dir);
    if (seen.has(k)) return;
    const natural = dir === 'h' ? b[0] === a[0] + 1 && b[1] === a[1] : b[1] === a[1] + 1 && b[0] === a[0];
    seen.set(k, { a, b, dir, hidden: !natural, key: k });
  };
  for (const m of MASKS) {
    const q = (qx, qy) => quarterFor(m, qx, qy);
    add(q(0, 0), q(1, 0), 'h');
    add(q(0, 1), q(1, 1), 'h');
    add(q(0, 0), q(0, 1), 'v');
    add(q(1, 0), q(1, 1), 'v');
  }
  // across two cells, every combination of the 10 surrounding cells
  for (let bits = 0; bits < 1024; bits++) {
    const h = Array.from({ length: 3 }, () => new Array(4).fill(false));
    let k = 0;
    for (let r = 0; r < 3; r++) for (let c = 0; c < 4; c++) h[r][c] = r === 1 && (c === 1 || c === 2) ? true : ((bits >> k++) & 1) === 1;
    const ma = mask(h, 1, 1), mb = mask(h, 2, 1);
    add(quarterFor(ma, 1, 0), quarterFor(mb, 0, 0), 'h');
    add(quarterFor(ma, 1, 1), quarterFor(mb, 0, 1), 'h');
    const v = Array.from({ length: 4 }, () => new Array(3).fill(false));
    k = 0;
    for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) v[r][c] = c === 1 && (r === 1 || r === 2) ? true : ((bits >> k++) & 1) === 1;
    const va = mask(v, 1, 1), vb = mask(v, 1, 2);
    add(quarterFor(va, 0, 1), quarterFor(vb, 0, 0), 'v');
    add(quarterFor(va, 1, 1), quarterFor(vb, 1, 0), 'v');
  }
  return [...seen.values()];
})();

export const HIDDEN = PAIRS.filter((p) => p.hidden);

/** A 14x9 shape that contains all 68 transitions (found by search, checked in tools/editor-parity.mjs). */
export const TEST_SHAPE = [
  '..............',
  '.....#..#.....',
  '...#..##.##...',
  '.####.##......',
  '..#..##..##...',
  '..#.####.###..',
  '..##.##..###..',
  '.###.##.##.##.',
  '..............',
].map((r) => [...r].map((c) => c === '#'));
