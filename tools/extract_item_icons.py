"""Extracts, for every item of a Minecraft client jar, the textures that make up its inventory icon.

The plugin registers those textures (which every client already has) as glyphs; it does not ship
any Minecraft image. Output per item: layers of (texture, tint). Flat items ("item/generated",
"item/handheld") keep all layers with their tints (potions, leather armor); block items and special
get one representative face (blocks cannot be shown in 3D with glyphs). Items drawn by special
renderers (heads, chests, beds, shields, banners) have no usable texture and are left out.

Usage: py tools/extract_item_icons.py <minecraft-client.jar> src/main/resources/item_icons.json
"""
import io
import json
import sys
import zipfile

from PIL import Image

jar_path, out_path = sys.argv[1], sys.argv[2]
jar = zipfile.ZipFile(jar_path)
names = set(jar.namelist())


def load(path):
    return json.loads(jar.read(path))


def rid(s):
    """'minecraft:item/x' or 'item/x' -> ('minecraft', 'item/x')"""
    if ":" in s:
        ns, p = s.split(":", 1)
    else:
        ns, p = "minecraft", s
    return ns, p


def leaf(model):
    """Item model definition -> (model id, tints) of a plain 'minecraft:model', or None."""
    t = model.get("type", "").replace("minecraft:", "")
    if t == "model":
        return model["model"], model.get("tints", [])
    if t == "select":
        # items with a separate inventory look (spyglass, trident, spears) select on display_context
        if model.get("property", "").replace("minecraft:", "") == "display_context":
            for case in model.get("cases", []):
                when = case.get("when")
                when = when if isinstance(when, list) else [when]
                if "gui" in when:
                    return leaf(case["model"])
        if "fallback" in model:
            return leaf(model["fallback"])
        return leaf(model["cases"][0]["model"]) if model.get("cases") else None
    if t == "condition":
        return leaf(model["on_false"])
    if t == "range_dispatch":
        if "fallback" in model:
            return leaf(model["fallback"])
        return leaf(model["entries"][0]["model"]) if model.get("entries") else None
    if t == "composite":
        return leaf(model["models"][0]) if model.get("models") else None
    if t == "special":
        return None  # drawn by a special renderer (heads, chests, beds, shields): no usable texture
    if "fallback" in model:
        return leaf(model["fallback"])
    return None


def resolve_model(model_id):
    """Follows parents; returns (textures dict, generated?)."""
    textures, generated, seen = {}, False, set()
    cur = model_id
    while cur and cur not in seen:
        seen.add(cur)
        ns, p = rid(cur)
        if p in ("item/generated",) or p.startswith("item/handheld"):
            generated = True
        if p.startswith("builtin/"):
            break
        path = f"assets/{ns}/models/{p}.json"
        if path not in names:
            break
        m = load(path)
        for k, v in m.get("textures", {}).items():
            if isinstance(v, dict):  # 26.x: {"sprite": "...", "force_translucent": ...}
                v = v.get("sprite")
            if isinstance(v, str):
                textures.setdefault(k, v)
        cur = m.get("parent")
    # resolve "#ref" chains
    def deref(v, depth=0):
        while isinstance(v, str) and v.startswith("#") and depth < 10:
            v = textures.get(v[1:])
            depth += 1
        return v
    return {k: deref(v) for k, v in textures.items() if deref(v)}, generated


def tint_rgb(tint):
    if not tint:
        return None
    t = tint.get("type", "").replace("minecraft:", "")
    if t == "constant":
        v = tint["value"]
    elif "default" in tint:
        v = tint["default"]
    elif t == "grass":
        v = 0x7CBD6B
    else:
        return None
    return v & 0xFFFFFF


tex_index, textures, gray_tex = {}, [], {}


def texture(tex):
    ns, p = rid(tex)
    if tex in tex_index:
        return tex_index[tex]
    path = f"assets/{ns}/textures/{p}.png"
    if path not in names:
        return None
    img = Image.open(io.BytesIO(jar.read(path))).convert("RGBA")
    w, h = img.size
    frame = w if h > w and h % w == 0 else h
    frames = h // frame
    adv = 0
    for x in range(w - 1, -1, -1):
        if any(img.getpixel((x, y))[3] != 0 for y in range(frame)):
            adv = x + 1
            break
    raw = img.tobytes()
    gray_tex[tex] = all(abs(raw[i] - raw[i + 1]) < 8 and abs(raw[i + 1] - raw[i + 2]) < 8
                        for i in range(0, len(raw), 4) if raw[i + 3])
    tex_index[tex] = len(textures)
    textures.append([f"{ns}:{p}", w, frame, frames, adv + 1])
    return tex_index[tex]


def block_tint(tex, tints):
    """Block tints apply to some faces only; tint the face only if its texture is grayscale."""
    return tint_rgb(tints[0]) if tints and gray_tex.get(tex) else None


FACES = ["layer0", "all", "front", "side", "texture", "top", "end", "cross", "plant", "pattern", "wool",
         "fan"]  # not "particle": for beds/special models it is a misleading stand-in
items, skipped = {}, []
for path in sorted(n for n in names if n.startswith("assets/minecraft/items/") and n.endswith(".json")):
    item = path[len("assets/minecraft/items/"):-len(".json")]
    lf = leaf(load(path)["model"])
    if lf is None:
        skipped.append(item)
        continue
    model_id, tints = lf
    tex, generated = resolve_model(model_id)
    layers = []
    if generated:
        i = 0
        while f"layer{i}" in tex:
            idx = texture(tex[f"layer{i}"])
            if idx is not None:
                layers.append([idx, tint_rgb(tints[i]) if i < len(tints) else None])
            i += 1
    if not layers:
        for face in FACES:
            if face in tex:
                idx = texture(tex[face])
                if idx is not None:
                    layers.append([idx, block_tint(tex[face], tints)])
                    break
    if not layers:
        # other face names (walls: "wall", crafting table: "north"/"up", ...): first real face
        prefer = ["north", "east", "south", "west", "wall", "up", "flower"]
        order = sorted((f for f in tex if f != "particle"),
                       key=lambda f: (prefer.index(f) if f in prefer else len(prefer),
                                      f in ("down", "bottom"), f))
        for face in order:
            value = tex[face]
            if True:
                idx = texture(value)
                if idx is not None:
                    layers.append([idx, block_tint(value, tints)])
                    break
    if layers:
        items[item] = layers
    else:
        skipped.append(item)

# GUI sprites ("gui:hud/heart/full", "gui:mob_effect/speed"): small pictures of the gui atlas, drawn
# at their own size (a 9 px heart stays 9 px) in the middle of the 16 x 16 icon place.
GUI = [("gui/sprites/", "hud/heart/"), ("gui/sprites/", "hud/"), ("gui/sprites/", "icon/"),
       ("gui/sprites/", "statistics/"), ("", "mob_effect/")]
gui = 0
for base, folder in GUI:
    prefix = f"assets/minecraft/textures/{base}{folder}"
    for path in sorted(n for n in names if n.startswith(prefix) and n.endswith(".png")):
        sprite = path[len(f"assets/minecraft/textures/{base}"):-len(".png")]
        if ("/" in sprite[len(folder):] or sprite.endswith("_blinking") or f"gui:{sprite}" in items
                or any(s in sprite for s in ("crosshair", "hotbar_", "locator_bar", "statistics/header"))):
            continue
        idx = texture(f"minecraft:{base}{sprite}")
        w, frame = textures[idx][1], textures[idx][2]
        if w > 18 or frame > 18:
            textures.pop()  # bars, hotbar, crosshair parts: too big for an icon
            del tex_index[f"minecraft:{base}{sprite}"]
            continue
        if len(textures[idx]) == 5:
            textures[idx].append(1)  # native size
        items[f"gui:{sprite}"] = [[idx, None]]
        gui += 1
print(f"{gui} gui sprites")

json.dump({"textures": textures, "items": items},open(out_path, "w", encoding="utf-8"), separators=(",", ":"))
print(f"{len(items)} items, {len(textures)} textures, skipped {len(skipped)}: {skipped[:20]}")
