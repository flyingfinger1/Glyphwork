// Checks that the editor's JavaScript (style + autotile + PNG codec) produces exactly what the
// plugin produces. Run after `./gradlew test` (which writes build/preview/):
//   node tools/editor-parity.mjs
import { readFileSync } from 'node:fs';
import { decodePng, encodePng } from '../editor/js/png.js';
import { SKINS, a2FromStyle } from '../editor/js/style.js';
import { tiles } from '../editor/js/autotile.js';
import { PAIRS, HIDDEN, TEST_SHAPE, transitions } from '../editor/js/seams.js';

const T = 8;
let failures = 0;
const check = (ok, msg) => { console.log((ok ? 'ok   ' : 'FAIL ') + msg); if (!ok) failures++; };

// every built-in skin: the editor's styles must give the same A2 sheets, tiles and text color
for (const [id, skin] of Object.entries(SKINS)) {
  const dir = `build/preview/themes/${id}`;
  const json = JSON.parse(readFileSync(`${dir}/theme.json`, 'utf8'));
  check(json.text.toLowerCase() === '#' + (skin.text & 0xffffff).toString(16).padStart(6, '0'), `${id}: text color`);
  check(json.name === skin.name, `${id}: name`);
  for (const frame of ['panel', 'button', 'slot']) {
    const javaA2 = await decodePng(readFileSync(`${dir}/${frame}.png`));
    const jsA2 = a2FromStyle(skin[frame], T);
    check(jsA2.equals(javaA2), `${id} ${frame}: A2 sheet from style`);

    const sheet = await decodePng(readFileSync(`build/preview/tiles/${id}_${frame}.png`));
    const list = tiles(javaA2, T);
    let same = list.length === 47;
    list.forEach((tile, i) => {
      for (let y = 0; y < T; y++) for (let x = 0; x < T; x++) {
        if (tile.get(x, y) !== sheet.get((i % 8) * T + x, Math.floor(i / 8) * T + y)) same = false;
      }
    });
    check(same, `${id} ${frame}: 47 autotiles`);

    const round = await decodePng(await encodePng(jsA2));
    check(round.equals(jsA2), `${id} ${frame}: PNG round trip`);
  }
}
const covered = transitions(TEST_SHAPE);
check(PAIRS.length === 68 && HIDDEN.length === 40, `seams: ${PAIRS.length} transitions, ${HIDDEN.length} hidden`);
check(PAIRS.every((p) => covered.has(p.key)), 'seams: test shape contains every transition');
process.exit(failures ? 1 : 0);
