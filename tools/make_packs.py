#!/usr/bin/env python3
"""
Builds the optional resource packs (one per theme) from the theme files.

Textures are drawn here, procedurally, from each gem's colour: original pixel art, no
third-party assets. Run:  python3 tools/make_packs.py
Output: resourcepacks/FacetGems-<theme>-pack.zip and their SHA-1 (paste into config.yml).
"""
import hashlib
import io
import json
import pathlib
import re
import zipfile

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
THEMES = ROOT / "gems" / "src" / "main" / "resources" / "themes"
OUT = ROOT / "resourcepacks"
GEM_IDS = ["strength", "speed", "life", "fire", "gravity", "ice", "wealth", "storm"]

# 16x16 cut gem. Letters pick a shade: O outline, D dark, M mid, L light, H highlight, . empty
GEM = [
    "................",
    "....OOOOOOOO....",
    "...OHLLMMLLDO...",
    "..OHLLMMMMLLDO..",
    ".OHLLMMMMMMLLDO.",
    "OOOOOOOOOOOOOOOO",
    "OLLMMMMDDMMMMDDO",
    ".OLMMMMDDMMMMDO.",
    "..OLMMMDDMMMDO..",
    "...OLMMDDMMDO...",
    "....OLMDDMDO....",
    ".....OLDDDO.....",
    "......OLDO......",
    ".......OO.......",
    "................",
    "................",
]

SHARD = [
    "................",
    "................",
    "........O.......",
    ".......OHO......",
    "......OHLDO.....",
    "......OLMDO.....",
    ".....OHLMDDO....",
    ".....OLMMDDO....",
    "....OHLMMDDO....",
    "....OLMMDDO.....",
    "....OLMMDDO.....",
    ".....OLMDO......",
    ".....OLDDO......",
    "......OOO.......",
    "................",
    "................",
]


def shade(rgb, f):
    if f >= 1:
        return tuple(min(255, int(c + (255 - c) * (f - 1))) for c in rgb)
    return tuple(int(c * f) for c in rgb)


def draw(shape, rgb):
    pal = {
        "O": shade(rgb, 0.35), "D": shade(rgb, 0.7), "M": rgb,
        "L": shade(rgb, 1.35), "H": shade(rgb, 1.8),
    }
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


def read_theme(path):
    """Tiny reader for the bits we need (name + colour per gem), so no YAML library is needed."""
    text = path.read_text(encoding="utf-8")
    name = re.search(r"^name:\s*(.+)$", text, re.M).group(1).strip()
    colours = {}
    current = None
    for line in text.splitlines():
        m = re.match(r"^  ([a-z_]+):\s*$", line)
        if m:
            current = m.group(1)
            continue
        m = re.match(r'^    color:\s*"?(#[0-9A-Fa-f]{6})"?', line)
        if m and current in GEM_IDS:
            colours[current] = m.group(1)
    return name, colours


def build(theme_file):
    theme = theme_file.stem
    name, colours = read_theme(theme_file)
    missing = [g for g in GEM_IDS if g not in colours]
    if missing:
        raise SystemExit(f"{theme_file.name}: no colour for {missing}")
    OUT.mkdir(exist_ok=True)
    target = OUT / f"FacetGems-{theme}-pack.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        # Fixed timestamps so the same input always gives the same zip (and SHA-1).
        def put(path, data):
            info = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)

        put("pack.mcmeta", json.dumps({"pack": {
            "description": f"Facet Gems - {name} textures",
            "pack_format": 76, "min_format": 70, "max_format": 999}}, indent=2))
        for gem in GEM_IDS:
            rgb = tuple(int(colours[gem][i:i + 2], 16) for i in (1, 3, 5))
            for kind, shape in (("gem", GEM), ("shard", SHARD)):
                key = f"{gem}_{kind}"
                put(f"assets/facet/items/{key}.json", json.dumps(
                    {"model": {"type": "minecraft:model", "model": f"facet:item/{key}"}}, indent=2))
                put(f"assets/facet/models/item/{key}.json", json.dumps(
                    {"parent": "minecraft:item/generated", "textures": {"layer0": f"facet:item/{key}"}}, indent=2))
                put(f"assets/facet/textures/item/{key}.png", draw(shape, rgb))
    sha1 = hashlib.sha1(target.read_bytes()).hexdigest()
    print(f"{target.relative_to(ROOT)}  sha1={sha1}")
    return target, sha1


if __name__ == "__main__":
    for f in sorted(THEMES.glob("*.yml")):
        build(f)
