// Element types of menu files: their settings (in the order they are written) and what a new one
// looks like. Field kinds: int, text, color, bool, select, lines (list of strings), shape,
// options (value: text), items (entries of lists/grids), from (items-from), values (use entries).

const COMMON_TAIL = ['show-if'];

export const FIELDS = {
  label: [['x', 'int'], ['y', 'int'], ['text', 'text'], ['color', 'color'], ['width', 'int'],
    ['align', 'select', ['left', 'center', 'right']], ['shadow', 'bool'], ['wrap', 'bool'], ['line-height', 'int'],
    ['bold', 'bool'], ['italic', 'bool'], ['underline', 'bool'], ['strikethrough', 'bool']],
  button: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['text', 'text'], ['tint', 'color'],
    ['color', 'color'], ['icon', 'text'], ['tooltip', 'text'], ['actions', 'lines'], ['permission', 'text'],
    ['enabled-if', 'lines'], ['disabled-tooltip', 'text'], ['frame', 'text']],
  panel: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['shape', 'shape'], ['frame', 'text'],
    ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  hitbox: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['tooltip', 'text'], ['actions', 'lines'],
    ['permission', 'text']],
  icon: [['x', 'int'], ['y', 'int'], ['item', 'text'], ['count', 'text'], ['item-tooltip', 'bool'], ['tint', 'color'],
    ['tooltip', 'text'], ['actions', 'lines']],
  list: [['id', 'text'], ['x', 'int'], ['y', 'int'], ['width', 'int'], ['rows', 'int'], ['row-height', 'int'],
    ['item-tooltip', 'bool'], ['head-size', 'select', ['8', '16', '24', '32']], ['items', 'items'], ['items-from', 'from'],
    ['item', 'item']],
  grid: [['id', 'text'], ['x', 'int'], ['y', 'int'], ['cols', 'int'], ['rows', 'int'], ['cell', 'int'], ['size', 'int'],
    ['frame', 'text'], ['item-tooltip', 'bool'], ['head-size', 'select', ['8', '16', '24', '32']], ['items', 'items'],
    ['items-from', 'from'], ['item', 'item']],
  slider: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['var', 'text'], ['min', 'int'], ['max', 'int'], ['step', 'text'],
    ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  checkbox: [['x', 'int'], ['y', 'int'], ['var', 'text'], ['text', 'text'], ['color', 'color'], ['tooltip', 'text'],
    ['actions', 'lines']],
  choice: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['var', 'text'], ['options', 'options'],
    ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  bar: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'select', ['8', '16']], ['value', 'text'], ['max', 'text'],
    ['fill', 'color'], ['text', 'text'], ['tooltip', 'text'], ['frame', 'text']],
  image: [['x', 'int'], ['y', 'int'], ['image', 'text'], ['width', 'int'], ['height', 'int'], ['scale', 'text'], ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  head: [['x', 'int'], ['y', 'int'], ['player', 'text'], ['size', 'select', ['8', '16', '24', '32']], ['tooltip', 'text'],
    ['actions', 'lines']],
  tabs: [['id', 'text'], ['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['var', 'text'], ['frame', 'text'],
    ['inactive-tint', 'color'], ['tabs', 'yaml']],
  scroll: [['id', 'text'], ['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['step', 'int'], ['frame', 'text'],
    ['elements', 'yaml']],
  use: [['use', 'use'], ['x', 'int'], ['y', 'int'], ['values', 'values']],
};

// clickable elements can have a cooldown (wait time per player, kept in the player's data)
for (const k of ['button', 'panel', 'hitbox', 'icon', 'image', 'head']) {
  // a cooldown is stored under the element's id (or a shared cooldown-key)
  if (!FIELDS[k].some((f) => f[0] === 'id')) FIELDS[k].unshift(['id', 'text']);
  FIELDS[k].push(['cooldown', 'text'], ['cooldown-key', 'text']);
  if (k === 'button') FIELDS[k].push(['cooldown-tooltip', 'text']);
  FIELDS[k].push(['cooldown-message', 'text']);
}
for (const k of Object.keys(FIELDS)) FIELDS[k].push(['show-if', 'lines']);

/** Settings of one entry of a list (grids also have tint). */
export const ITEM_FIELDS = {
  list: [['text', 'text'], ['icon', 'text'], ['count', 'text'], ['color', 'color'], ['tooltip', 'text'],
    ['actions', 'lines'], ['permission', 'text'], ['show-if', 'lines'], ['selected-if', 'lines']],
  grid: [['text', 'text'], ['icon', 'text'], ['count', 'text'], ['color', 'color'], ['tint', 'color'], ['tooltip', 'text'],
    ['actions', 'lines'], ['permission', 'text'], ['show-if', 'lines'], ['selected-if', 'lines']],
};

export const TYPES = ['label', 'button', 'panel', 'hitbox', 'icon', 'list', 'grid', 'slider', 'checkbox', 'choice', 'bar', 'image', 'head', 'tabs', 'scroll'];

/** Field names that map to i18n keys (f_...). */
export const FIELD_KEY = {
  x: 'f_x', y: 'f_y', text: 'f_text', color: 'f_color', width: 'f_width', height: 'f_height', scale: 'f_scale', align: 'f_align',
  shadow: 'f_shadow', tint: 'f_tint', 'text-color': 'f_textColor', 'inactive-tint': 'f_inactiveTint', fill: 'f_fill', tooltip: 'f_tooltip', actions: 'f_actions',
  permission: 'f_permission', frame: 'f_frame', icon: 'f_icon', 'enabled-if': 'f_enabledIf',
  'disabled-tooltip': 'f_disabledTooltip', 'show-if': 'f_showIf', shape: 'f_shape', item: 'f_item', count: 'f_count',
  id: 'f_id', rows: 'f_rows', 'row-height': 'f_rowHeight', cols: 'f_cols', cell: 'f_cell', size: 'f_size', var: 'f_var',
  min: 'f_min', max: 'f_max', step: 'f_step', options: 'f_options', use: 'f_use', values: 'f_values',
  'items-from': 'itemsFrom', wrap: 'f_wrap', 'line-height': 'f_lineHeight', 'item-tooltip': 'f_itemTooltip',
  value: 'f_value', image: 'f_image', player: 'f_player', 'head-size': 'f_headSize', cooldown: 'f_cooldown',
  'cooldown-key': 'f_cooldownKey', 'selected-if': 'f_selectedIf', bold: 'f_bold', italic: 'f_italic', underline: 'f_underline', strikethrough: 'f_strike', tabs: 'f_tabs', elements: 'f_elements', 'cooldown-tooltip': 'f_cooldownTooltip', 'cooldown-message': 'f_cooldownMessage',
};

/** A new element of this type at (x, y). */
export function create(type, x, y, de) {
  const tx = (d, e) => (de ? d : e);
  switch (type) {
    case 'label': return { type, x, y, text: tx('Text', 'Text') };
    case 'button': return { type, x, y, width: 64, text: 'Button', actions: [tx('message: Klick!', 'message: Click!')] };
    case 'panel': return { type, x, y, width: 48, height: 32, frame: 'slot' };
    case 'hitbox': return { type, x, y, width: 16, height: 16, tooltip: tx('Klickfläche', 'Click area') };
    case 'icon': return { type, x, y, item: 'diamond' };
    case 'list': return { type, x, y, width: 120, rows: 3, items: [{ text: tx('Eintrag 1', 'Entry 1') }, { text: tx('Eintrag 2', 'Entry 2') }] };
    case 'grid': return { type, x, y, cols: 4, rows: 1, items: [{ icon: 'diamond', count: 64 }, { icon: 'emerald' }] };
    case 'slider': return { type, x, y, width: 96, var: 'wert', min: 0, max: 10 };
    case 'checkbox': return { type, x, y, var: 'option', text: tx('Option', 'Option') };
    case 'choice': return { type, x, y, width: 80, var: 'modus', options: tx(['leicht', 'normal', 'schwer'], ['easy', 'normal', 'hard']) };
    case 'bar': return { type, x, y, width: 100, value: 30, max: 100, text: '30 / 100' };
    case 'image': return { type, x, y, image: 'logo' };
    case 'head': return { type, x, y, player: '%player%', size: 32 };
    case 'scroll': return {
      type, x, y, width: 120, height: 64, frame: 'slot', elements: Array.from({ length: 8 }, (_, i) => (
        { type: 'label', x: 4, y: 4 + i * 18, text: tx('Zeile ', 'Row ') + (i + 1) })),
    };
    case 'tabs': return {
      type, x, y, width: 160, height: 96, tabs: [
        { id: 'one', title: tx('Erster', 'First'), elements: [{ type: 'label', x: 6, y: 6, text: tx('Inhalt 1', 'Content 1') }] },
        { id: 'two', title: tx('Zweiter', 'Second'), elements: [{ type: 'label', x: 6, y: 6, text: tx('Inhalt 2', 'Content 2') }] },
      ],
    };
    default: return { type, x, y };
  }
}

/** Default value a control's variable gets when it is created. */
export function defaultFor(el) {
  if (el.type === 'slider') return String(el.min ?? 0);
  if (el.type === 'checkbox') return 'false';
  if (el.type === 'choice') {
    const o = el.options;
    return Array.isArray(o) ? String(o[0] ?? '') : o && typeof o === 'object' ? Object.keys(o)[0] ?? '' : '';
  }
  return '';
}

/** Kind of an element for the schema ("use" entries, unknown types). */
export const kindOf = (el) => (el && el.use != null ? 'use' : String(el?.type ?? '').toLowerCase());

/**
 * Sets key to value and keeps the keys in schema order (type first, unknown keys at the end).
 * Empty values (undefined, '', empty list) remove the key.
 */
export function setField(el, key, value) {
  const empty = value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0);
  const order = ['type', ...(FIELDS[kindOf(el)] || []).map((f) => f[0])];
  const next = { ...el };
  if (empty) delete next[key];
  else next[key] = value;
  const out = {};
  for (const k of order) if (k in next) out[k] = next[k];
  for (const k of Object.keys(next)) if (!(k in out)) out[k] = next[k];
  return out;
}

// ------------------------------------------------------------------ Glyphwork versions

/** Glyphwork versions the designer can write menus for (oldest first); the last one is the newest. */
export const VERSIONS = ['1.0', '1.1'];
export const LATEST = VERSIONS[VERSIONS.length - 1];

/** Settings that came after 1.0: "type.key" -> version. Everything else works since 1.0. */
export const SINCE = {
  'image.width': '1.1',
  'image.height': '1.1',
  'image.scale': '1.1',
};

/** Values that came after 1.0: icons from the GUI atlas ("gui:hud/heart/full"). */
const VALUE_SINCE = [
  { keys: ['icon', 'item'], test: (v) => /^\s*gui:/i.test(v), since: '1.1', what: 'gui:' },
];

/** -1, 0 or 1 like a comparator ("1.10" is newer than "1.9"). */
export function compareVersions(a, b) {
  const pa = String(a).split('.').map(Number);
  const pb = String(b).split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return Math.sign(d);
  }
  return 0;
}

/** The version a setting of this element type needs, or null if it works since 1.0. */
export function sinceOf(type, key) {
  return SINCE[`${type}.${key}`] || null;
}

/**
 * Everything in the menu that needs a newer Glyphwork than target: [{ top, key, since }], top =
 * index of the element at the top level (nested elements of scroll panes and tabs count to theirs).
 */
export function newerThan(menu, target) {
  const out = [];
  const seen = new Set();
  const note = (top, key, since) => {
    const k = `${top}|${key}|${since}`;
    if (compareVersions(since, target) > 0 && !seen.has(k)) {
      seen.add(k);
      out.push({ top, key, since });
    }
  };
  const visit = (node, top, type) => {
    if (Array.isArray(node)) { node.forEach((n) => visit(n, top, type)); return; }
    if (!node || typeof node !== 'object') return;
    const t = typeof node.type === 'string' ? node.type.toLowerCase() : type;
    for (const [k, v] of Object.entries(node)) {
      const s = node.type ? sinceOf(t, k) : null;
      if (s) note(top, k, s);
      if (typeof v === 'string') {
        for (const r of VALUE_SINCE) if (r.keys.includes(k) && r.test(v)) note(top, `${k}: ${r.what}`, r.since);
      } else {
        visit(v, top, k === 'elements' || k === 'tabs' ? null : t);
      }
    }
  };
  (menu?.elements || []).forEach((el, i) => visit(el, i, null));
  // templates and forms are shared by all elements
  for (const key of ['templates', 'forms']) if (menu?.[key]) visit(menu[key], -1, null);
  return out;
}
