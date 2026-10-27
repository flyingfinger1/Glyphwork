// Element types of menu files: their settings (in the order they are written) and what a new one
// looks like. Field kinds: int, text, color, bool, select, lines (list of strings), shape,
// options (value: text), items (entries of lists/grids), from (items-from), values (use entries).

const COMMON_TAIL = ['show-if'];

export const FIELDS = {
  label: [['x', 'int'], ['y', 'int'], ['text', 'text'], ['color', 'color'], ['width', 'int'],
    ['align', 'select', ['left', 'center', 'right']], ['shadow', 'bool'], ['wrap', 'bool'], ['line-height', 'int']],
  button: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['text', 'text'], ['tint', 'color'],
    ['text-color', 'color'], ['icon', 'text'], ['tooltip', 'text'], ['actions', 'lines'], ['permission', 'text'],
    ['enabled-if', 'lines'], ['disabled-tooltip', 'text'], ['frame', 'text']],
  panel: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['shape', 'shape'], ['frame', 'text'],
    ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  hitbox: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['tooltip', 'text'], ['actions', 'lines'],
    ['permission', 'text']],
  icon: [['x', 'int'], ['y', 'int'], ['item', 'text'], ['count', 'text'], ['item-tooltip', 'bool'], ['tint', 'color'],
    ['tooltip', 'text'], ['actions', 'lines']],
  list: [['id', 'text'], ['x', 'int'], ['y', 'int'], ['width', 'int'], ['rows', 'int'], ['row-height', 'int'],
    ['item-tooltip', 'bool'], ['items', 'items'], ['items-from', 'from'], ['item', 'item']],
  grid: [['id', 'text'], ['x', 'int'], ['y', 'int'], ['cols', 'int'], ['rows', 'int'], ['cell', 'int'], ['size', 'int'],
    ['frame', 'text'], ['item-tooltip', 'bool'], ['items', 'items'], ['items-from', 'from'], ['item', 'item']],
  slider: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['var', 'text'], ['min', 'int'], ['max', 'int'], ['step', 'text'],
    ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  checkbox: [['x', 'int'], ['y', 'int'], ['var', 'text'], ['text', 'text'], ['color', 'color'], ['tooltip', 'text'],
    ['actions', 'lines']],
  choice: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'int'], ['var', 'text'], ['options', 'options'],
    ['tint', 'color'], ['tooltip', 'text'], ['actions', 'lines']],
  bar: [['x', 'int'], ['y', 'int'], ['width', 'int'], ['height', 'select', ['8', '16']], ['value', 'text'], ['max', 'text'],
    ['color', 'color'], ['text', 'text'], ['tooltip', 'text'], ['frame', 'text']],
  use: [['use', 'use'], ['x', 'int'], ['y', 'int'], ['values', 'values']],
};

for (const k of Object.keys(FIELDS)) FIELDS[k].push(['show-if', 'lines']);

/** Settings of one entry of a list (grids also have tint). */
export const ITEM_FIELDS = {
  list: [['text', 'text'], ['icon', 'text'], ['count', 'text'], ['color', 'color'], ['tooltip', 'text'],
    ['actions', 'lines'], ['permission', 'text'], ['show-if', 'lines']],
  grid: [['text', 'text'], ['icon', 'text'], ['count', 'text'], ['color', 'color'], ['tint', 'color'], ['tooltip', 'text'],
    ['actions', 'lines'], ['permission', 'text'], ['show-if', 'lines']],
};

export const TYPES = ['label', 'button', 'panel', 'hitbox', 'icon', 'list', 'grid', 'slider', 'checkbox', 'choice', 'bar'];

/** Field names that map to i18n keys (f_...). */
export const FIELD_KEY = {
  x: 'f_x', y: 'f_y', text: 'f_text', color: 'f_color', width: 'f_width', height: 'f_height', align: 'f_align',
  shadow: 'f_shadow', tint: 'f_tint', 'text-color': 'f_textColor', tooltip: 'f_tooltip', actions: 'f_actions',
  permission: 'f_permission', frame: 'f_frame', icon: 'f_icon', 'enabled-if': 'f_enabledIf',
  'disabled-tooltip': 'f_disabledTooltip', 'show-if': 'f_showIf', shape: 'f_shape', item: 'f_item', count: 'f_count',
  id: 'f_id', rows: 'f_rows', 'row-height': 'f_rowHeight', cols: 'f_cols', cell: 'f_cell', size: 'f_size', var: 'f_var',
  min: 'f_min', max: 'f_max', step: 'f_step', options: 'f_options', use: 'f_use', values: 'f_values',
  'items-from': 'itemsFrom', wrap: 'f_wrap', 'line-height': 'f_lineHeight', 'item-tooltip': 'f_itemTooltip',
  value: 'f_value',
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
