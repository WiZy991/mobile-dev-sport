"""Картинка для описания в Google Play (1024x500) для flavor academyWrestling."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LOGO = ROOT / "app/src/academyWrestling/res/drawable-nodpi/ic_launcher_source.png"
OUT = ROOT / "docs/store/academy_wrestling_feature_graphic_1024x500.png"
FONTS = Path("C:/Windows/Fonts")

W, H = 1024, 500
NAVY_CENTER = (20, 34, 66)
NAVY_EDGE = (5, 10, 24)
GOLD = (224, 172, 72)
GOLD_LIGHT = (246, 206, 120)
WHITE = (255, 255, 255)


def radial_background() -> Image.Image:
    bg = Image.new("RGB", (W, H), NAVY_EDGE)
    px = bg.load()
    cx, cy = W * 0.28, H * 0.5
    max_d = (W**2 + H**2) ** 0.5 * 0.6
    for y in range(H):
        for x in range(W):
            t = min(1.0, (((x - cx) ** 2 + (y - cy) ** 2) ** 0.5) / max_d)
            px[x, y] = tuple(int(NAVY_CENTER[i] * (1 - t) + NAVY_EDGE[i] * t) for i in range(3))
    return bg


def glow(size: int, color: tuple[int, int, int], radius: int) -> Image.Image:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = 250, H // 2
    d.ellipse((cx - size, cy - size, cx + size, cy + size), fill=color + (150,))
    return layer.filter(ImageFilter.GaussianBlur(radius))


def main() -> None:
    canvas = radial_background().convert("RGBA")
    canvas.alpha_composite(glow(215, (200, 130, 40), 60))

    logo = Image.open(LOGO).convert("RGBA").resize((430, 430), Image.LANCZOS)
    canvas.alpha_composite(logo, (250 - 215, H // 2 - 215))

    d = ImageDraw.Draw(canvas)
    title_font = ImageFont.truetype(str(FONTS / "seguibl.ttf"), 76)
    tag_font = ImageFont.truetype(str(FONTS / "seguisb.ttf"), 24)

    x = 520
    d.text((x, 118), "АКАДЕМИЯ", font=title_font, fill=WHITE)
    d.text((x, 198), "БОРЬБЫ", font=title_font, fill=GOLD_LIGHT)
    d.rounded_rectangle((x + 4, 302, x + 120, 308), radius=3, fill=GOLD)

    tag = "Спортивная борьба для детей и взрослых"
    d.text((x, 332), tag, font=tag_font, fill=GOLD)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT, "PNG", optimize=True)
    print(OUT, OUT.stat().st_size)


if __name__ == "__main__":
    main()
