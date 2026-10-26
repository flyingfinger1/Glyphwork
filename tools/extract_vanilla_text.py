"""Extracts the vanilla text bitmap providers (texture path, ascent, height, char grid)
and per-character advances from a Minecraft client jar.

The plugin re-registers these textures with shifted ascents (one font per vertical
offset) and needs the advances to lay text out. No textures are copied; the client
already has them.

Usage: py tools/extract_vanilla_text.py <minecraft-client.jar> src/main/resources/vanilla_text.json
"""
import io
import json
import sys
import zipfile

from PIL import Image

jar_path, out_path = sys.argv[1], sys.argv[2]
jar = zipfile.ZipFile(jar_path)
include = json.loads(jar.read("assets/minecraft/font/include/default.json"))
space = json.loads(jar.read("assets/minecraft/font/include/space.json"))

providers = []
advances = {}
for p in include["providers"]:
    if p["type"] != "bitmap":
        continue
    ns, path = p["file"].split(":")
    img = Image.open(io.BytesIO(jar.read(f"assets/{ns}/textures/{path}"))).convert("RGBA")
    rows = p["chars"]
    height = p.get("height", 8)
    gw = img.width // len(rows[0])
    gh = img.height // len(rows)
    scale = height / gh
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch == "\0" or ch in advances:
                continue
            width = 0
            for x in range(gw - 1, -1, -1):
                if any(img.getpixel((rx * gw + x, ry * gh + y))[3] != 0 for y in range(gh)):
                    width = x + 1
                    break
            advances[ch] = int(0.5 + width * scale) + 1
    providers.append({"file": p["file"], "ascent": p["ascent"], "height": height, "chars": rows})

for ch, adv in space["providers"][0]["advances"].items():
    advances[ch] = adv

json.dump({"providers": providers, "spaces": space["providers"][0]["advances"], "advances": advances},
          open(out_path, "w", encoding="utf-8"), ensure_ascii=True)
print(f"{len(providers)} providers, {len(advances)} advances -> {out_path}")
