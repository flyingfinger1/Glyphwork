// Checks that the editor's JavaScript (style + autotile + PNG codec) produces exactly what the
// plugin produces. Run after `./gradlew test` (which writes build/preview/):
//   node tools/editor-parity.mjs
import { readFileSync } from 'node:fs';
import { decodePng, encodePng } from '../editor/js/png.js';
import { STYLES, a2FromStyle } from '../editor/js/style.js';
import { tiles } from '../editor/js/autotile.js';

const T = 8;
let failures = 0;
const check = (ok, msg) => { console.log((ok ? 'ok   ' : 'FAIL ') + msg); if (!ok) failures++; };

for (const frame of ['panel', 'button', 'slot']) {
  const javaA2 = await decodePng(readFileSync(`build/preview/theme/${frame}.png`));
  const jsA2 = a2FromStyle(STYLES[frame], T);
  check(jsA2.equals(javaA2), `${frame}: A2 sheet from style`);

  const sheet = await decodePng(readFileSync(`build/preview/tiles/vanilla_${frame}.png`));
  const list = tiles(javaA2, T);
  let same = list.length === 47;
  list.forEach((tile, i) => {
    for (let y = 0; y < T; y++) for (let x = 0; x < T; x++) {
      if (tile.get(x, y) !== sheet.get((i % 8) * T + x, Math.floor(i / 8) * T + y)) same = false;
    }
  });
  check(same, `${frame}: 47 autotiles`);

  const round = await decodePng(await encodePng(jsA2));
  check(round.equals(jsA2), `${frame}: PNG round trip`);
}
process.exit(failures ? 1 : 0);
