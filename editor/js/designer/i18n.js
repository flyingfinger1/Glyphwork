// Texts of the menu designer (German / English), same language setting as the skin editor.

const T = {
  de: {
    title: 'Glyphwork Menü-Designer',
    skinEditor: 'Skin-Editor',
    newMenu: 'Neu', open: 'Öffnen', paste: 'YAML einfügen', download: 'Herunterladen', copy: 'Kopieren',
    copied: 'Kopiert', confirmNew: 'Aktuelles Menü verwerfen und neu beginnen?',
    pastePrompt: 'Menü-YAML hier einfügen:',
    saved: 'Wird in diesem Browser gespeichert.',
    menu: 'Menü', menuTitle: 'Titel', width: 'Breite', height: 'Höhe', background: 'Hintergrund',
    command: 'Befehl', aliases: 'Aliase', permission: 'Recht', description: 'Beschreibung',
    exitLabel: 'Schließen-Button', refresh: 'Refresh (s)', fileName: 'Dateiname (id)',
    skin: 'Skin', skinFromEditor: 'Aus dem Skin-Editor', noEditorSkin: 'Im Skin-Editor ist noch kein Skin gespeichert.',
    palette: 'Element hinzufügen', layers: 'Ebenen', up: '↑', down: '↓', duplicate: 'Duplizieren', remove: 'Löschen',
    edit: 'Bearbeiten', play: 'Ausprobieren', zoom: 'Zoom', snap: 'Raster', textGrid: '9-px-Raster',
    outlines: 'Umrisse', hidden: 'ausgeblendet', noSelection: 'Kein Element gewählt. Klicke ins Menü oder in die Ebenen.',
    tabElement: 'Element', tabVars: 'Variablen', tabForms: 'Formulare', tabTemplates: 'Vorlagen', tabYaml: 'YAML',
    rawElement: 'YAML des Elements', apply: 'Übernehmen', yamlError: 'YAML-Fehler',
    varsHint: 'Menü-Variablen mit Standardwert. „Vorschau“ setzt den Wert nur hier im Designer (für show-if, %menu_…%).',
    varName: 'Name', varDefault: 'Standard', varPreview: 'Vorschau', addVar: '+ Variable', resetPreview: 'Vorschau zurücksetzen',
    formsHint: 'Formulare öffnen sich mit der Aktion „form: name“. Jedes Feld schreibt eine Menü-Variable.',
    addForm: '+ Formular', formName: 'Name', formTitle: 'Titel', ok: 'OK-Text', cancel: 'Abbrechen-Text',
    fields: 'Felder', addField: '+ Feld', fieldType: 'Typ', fieldVar: 'Variable', fieldLabel: 'Beschriftung',
    maxLength: 'Max. Länge', lines: 'Zeilen', min: 'Min', max: 'Max', step: 'Schritt',
    options: 'Optionen (eine pro Zeile: wert: Text)', actionsAfter: 'Aktionen nach OK (eine pro Zeile)',
    templatesHint: 'Vorlagen dieses Menüs (templates:) als YAML. Geteilte Vorlagen aus menus/_*.yml kannst du für die Vorschau laden.',
    loadShared: 'Geteilte Vorlagen laden', sharedLoaded: 'geteilte Vorlagen geladen: {n}',
    yamlHint: 'So sieht die Datei aus. Du kannst sie hier auch direkt ändern und übernehmen.',
    items: 'Einträge', addItem: '+ Eintrag', itemsFrom: 'Einträge aus', itemTemplate: 'Vorlage je Eintrag (item:)',
    none: '(keine)', insertAction: 'Aktion einfügen …',
    log: 'Protokoll', clearLog: 'Leeren', formOk: 'OK', formCancel: 'Abbrechen',
    errors: 'Probleme', hints: 'Hinweise', iconNote: 'Item-Icons und Köpfe sind Platzhalter (Minecraft-Texturen dürfen nicht mitgeliefert werden); die Schrift ist eine Ersatzschrift.',
    // element types
    t_label: 'Text', t_button: 'Button', t_panel: 'Panel', t_hitbox: 'Klickfläche', t_icon: 'Icon', t_list: 'Liste',
    t_grid: 'Raster', t_slider: 'Schieberegler', t_checkbox: 'Checkbox', t_choice: 'Auswahl', t_use: 'Vorlage (use)',
    // fields
    f_x: 'x', f_y: 'y', f_text: 'Text', f_color: 'Farbe', f_width: 'Breite', f_height: 'Höhe', f_align: 'Ausrichtung',
    f_shadow: 'Schatten', f_tint: 'Tönung', f_textColor: 'Textfarbe', f_tooltip: 'Tooltip', f_actions: 'Aktionen (eine pro Zeile)',
    f_permission: 'Recht', f_frame: 'Rahmen', f_icon: 'Icon (Item-ID oder head:Name)', f_enabledIf: 'Aktiv wenn (eine pro Zeile)',
    f_disabledTooltip: 'Tooltip wenn inaktiv', f_showIf: 'Sichtbar wenn (eine pro Zeile)', f_shape: 'Form (# = Kachel, eine Zeile pro Reihe)',
    f_item: 'Item', f_count: 'Anzahl', f_id: 'id', f_rows: 'Zeilen', f_rowHeight: 'Zeilenhöhe', f_cols: 'Spalten',
    f_cell: 'Abstand (cell)', f_size: 'Zellgröße (size)', f_var: 'Variable', f_min: 'Min', f_max: 'Max', f_step: 'Schritt',
    f_options: 'Optionen (eine pro Zeile: wert: Text)', f_use: 'Vorlage', f_values: 'Werte (eine pro Zeile: schlüssel: wert)',
  },
  en: {
    title: 'Glyphwork Menu Designer',
    skinEditor: 'Skin editor',
    newMenu: 'New', open: 'Open', paste: 'Paste YAML', download: 'Download', copy: 'Copy',
    copied: 'Copied', confirmNew: 'Discard the current menu and start over?',
    pastePrompt: 'Paste the menu YAML here:',
    saved: 'Saved in this browser.',
    menu: 'Menu', menuTitle: 'Title', width: 'Width', height: 'Height', background: 'Background',
    command: 'Command', aliases: 'Aliases', permission: 'Permission', description: 'Description',
    exitLabel: 'Close button', refresh: 'Refresh (s)', fileName: 'File name (id)',
    skin: 'Skin', skinFromEditor: 'From the skin editor', noEditorSkin: 'The skin editor has no saved skin yet.',
    palette: 'Add element', layers: 'Layers', up: '↑', down: '↓', duplicate: 'Duplicate', remove: 'Delete',
    edit: 'Edit', play: 'Try it', zoom: 'Zoom', snap: 'Snap', textGrid: '9 px grid',
    outlines: 'Outlines', hidden: 'hidden', noSelection: 'No element selected. Click into the menu or the layers.',
    tabElement: 'Element', tabVars: 'Variables', tabForms: 'Forms', tabTemplates: 'Templates', tabYaml: 'YAML',
    rawElement: 'YAML of the element', apply: 'Apply', yamlError: 'YAML error',
    varsHint: 'Menu variables with their default. "Preview" sets the value only here in the designer (for show-if, %menu_…%).',
    varName: 'Name', varDefault: 'Default', varPreview: 'Preview', addVar: '+ Variable', resetPreview: 'Reset preview',
    formsHint: 'Forms open with the action "form: name". Every field writes a menu variable.',
    addForm: '+ Form', formName: 'Name', formTitle: 'Title', ok: 'OK label', cancel: 'Cancel label',
    fields: 'Fields', addField: '+ Field', fieldType: 'Type', fieldVar: 'Variable', fieldLabel: 'Label',
    maxLength: 'Max length', lines: 'Lines', min: 'Min', max: 'Max', step: 'Step',
    options: 'Options (one per line: value: Text)', actionsAfter: 'Actions after OK (one per line)',
    templatesHint: 'Templates of this menu (templates:) as YAML. Shared templates from menus/_*.yml can be loaded for the preview.',
    loadShared: 'Load shared templates', sharedLoaded: 'shared templates loaded: {n}',
    yamlHint: 'This is the file. You can also edit it here and apply.',
    items: 'Entries', addItem: '+ Entry', itemsFrom: 'Entries from', itemTemplate: 'Settings per entry (item:)',
    none: '(none)', insertAction: 'Insert action …',
    log: 'Log', clearLog: 'Clear', formOk: 'OK', formCancel: 'Cancel',
    errors: 'Problems', hints: 'Hints', iconNote: 'Item icons and heads are placeholders (Minecraft textures cannot be shipped); the font is a stand-in.',
    t_label: 'Text', t_button: 'Button', t_panel: 'Panel', t_hitbox: 'Click area', t_icon: 'Icon', t_list: 'List',
    t_grid: 'Grid', t_slider: 'Slider', t_checkbox: 'Checkbox', t_choice: 'Choice', t_use: 'Template (use)',
    f_x: 'x', f_y: 'y', f_text: 'Text', f_color: 'Color', f_width: 'Width', f_height: 'Height', f_align: 'Align',
    f_shadow: 'Shadow', f_tint: 'Tint', f_textColor: 'Text color', f_tooltip: 'Tooltip', f_actions: 'Actions (one per line)',
    f_permission: 'Permission', f_frame: 'Frame', f_icon: 'Icon (item id or head:Name)', f_enabledIf: 'Enabled if (one per line)',
    f_disabledTooltip: 'Tooltip when disabled', f_showIf: 'Shown if (one per line)', f_shape: 'Shape (# = tile, one line per row)',
    f_item: 'Item', f_count: 'Count', f_id: 'id', f_rows: 'Rows', f_rowHeight: 'Row height', f_cols: 'Columns',
    f_cell: 'Pitch (cell)', f_size: 'Cell size (size)', f_var: 'Variable', f_min: 'Min', f_max: 'Max', f_step: 'Step',
    f_options: 'Options (one per line: value: Text)', f_use: 'Template', f_values: 'Values (one per line: key: value)',
  },
};

let lang = 'de';
try {
  lang = localStorage.getItem('glyphwork-editor-lang') || (navigator.language?.startsWith('de') ? 'de' : 'en');
} catch { /* storage unavailable */ }

export function t(key, params = {}) {
  const s = T[lang]?.[key] ?? T.en[key] ?? key;
  return s.replace(/\{(\w+)}/g, (m, k) => params[k] ?? m);
}

export const getLang = () => lang;

export function setLang(l) {
  lang = l;
  try { localStorage.setItem('glyphwork-editor-lang', l); } catch { /* ignore */ }
}

export function applyI18n(root = document) {
  root.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t(el.dataset.i18n); });
  root.querySelectorAll('[data-i18n-title]').forEach((el) => { el.title = t(el.dataset.i18nTitle); });
}
