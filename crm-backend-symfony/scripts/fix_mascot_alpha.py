"""Strip near-uniform dark background from mascot PNGs (user cuts without alpha)."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image


def corner_bg(img: Image.Image) -> tuple[int, int, int]:
    w, h = img.size
    pts = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
    rs, gs, bs = [], [], []
    for x, y in pts:
        r, g, b, _a = img.getpixel((x, y))
        rs.append(r)
        gs.append(g)
        bs.append(b)
    return sum(rs) // 4, sum(gs) // 4, sum(bs) // 4


def color_dist(c1: tuple[int, int, int], c2: tuple[int, int, int]) -> float:
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(c1, c2)))


def process(path: Path, tol: float = 22, feather: float = 18) -> tuple[tuple[int, int, int], int, int]:
    img = Image.open(path).convert("RGBA")
    bg = corner_bg(img)
    data: list[tuple[int, int, int, int]] = []
    for r, g, b, a in img.getdata():
        d = color_dist((r, g, b), bg)
        if d <= tol:
            data.append((r, g, b, 0))
        elif d <= tol + feather:
            t = (d - tol) / feather
            data.append((r, g, b, int(a * t)))
        else:
            data.append((r, g, b, a))
    img.putdata(data)
    img.save(path, "PNG")
    transparent = sum(1 for px in data if px[3] == 0)
    return bg, transparent, len(data)


def main() -> None:
    base = Path(__file__).resolve().parents[1] / "public" / "img" / "mascot"
    files = sorted((base / "heads").glob("zalka-head-*.png")) + sorted(base.glob("zalka-paw-*.png"))
    for f in files:
        bg, t, total = process(f)
        pct = 100 * t / total if total else 0
        print(f"{f.name}: bg={bg} transparent={t}/{total} ({pct:.1f}%)")


if __name__ == "__main__":
    main()
