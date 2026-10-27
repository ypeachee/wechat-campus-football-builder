from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "assets" / "branding"
SCALE = 3
SIZE = 1024
S = SIZE * SCALE

DARK = (4, 61, 43, 255)
GREEN = (11, 107, 75, 255)
GREEN_LIGHT = (19, 132, 91, 255)
CREAM = (244, 241, 232, 255)
GOLD = (229, 181, 63, 255)
CHARCOAL = (18, 31, 26, 255)
WHITE = (255, 255, 255, 255)


def sc(value: float) -> int:
    return round(value * SCALE)


def polygon(cx: float, cy: float, radius: float, sides: int, rotation: float = -math.pi / 2) -> list[tuple[int, int]]:
    return [
        (
            round(cx + math.cos(rotation + 2 * math.pi * i / sides) * radius),
            round(cy + math.sin(rotation + 2 * math.pi * i / sides) * radius),
        )
        for i in range(sides)
    ]


def make_background() -> Image.Image:
    y, x = np.mgrid[0:S, 0:S]
    diagonal = (x + y) / (2 * S - 2)
    radial = np.clip(np.sqrt(((x - S / 2) / S) ** 2 + ((y - S / 2) / S) ** 2) * 1.55, 0, 1)
    dark = np.array(DARK[:3], dtype=float)
    light = np.array(GREEN_LIGHT[:3], dtype=float)
    rgb = dark[None, None, :] * (1 - diagonal[:, :, None]) + light[None, None, :] * diagonal[:, :, None]
    rgb *= (1 - radial[:, :, None] * 0.22)
    rgba = np.dstack([rgb, np.full((S, S, 1), 255.0)]).clip(0, 255).astype(np.uint8)
    image = Image.fromarray(rgba, "RGBA")

    overlay = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    line = (196, 232, 211, 20)
    d.line((sc(512), 0, sc(512), S), fill=line, width=sc(3))
    d.ellipse((sc(252), sc(252), sc(772), sc(772)), outline=line, width=sc(3))
    d.line((0, sc(512), S, sc(512)), fill=line, width=sc(2))
    for offset in range(-S, S * 2, sc(120)):
        d.line((offset, 0, offset + S, S), fill=(255, 255, 255, 8), width=sc(7))
    image.alpha_composite(overlay)
    return image


def draw_star(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, fill: tuple[int, int, int, int]) -> None:
    points: list[tuple[int, int]] = []
    for i in range(10):
        radius = r if i % 2 == 0 else r * 0.44
        angle = -math.pi / 2 + i * math.pi / 5
        points.append((sc(cx + math.cos(angle) * radius), sc(cy + math.sin(angle) * radius)))
    draw.polygon(points, fill=fill)


def draw_spaced_text(
    draw: ImageDraw.ImageDraw,
    center_x: float,
    center_y: float,
    text: str,
    font: ImageFont.FreeTypeFont,
    spacing: float,
    fill: tuple[int, int, int, int],
    stroke_width: int = 0,
    stroke_fill: tuple[int, int, int, int] | None = None,
) -> None:
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + spacing * SCALE * (len(text) - 1)
    x = center_x * SCALE - total / 2
    for ch, width in zip(text, widths):
        draw.text(
            (round(x), round(center_y * SCALE)),
            ch,
            font=font,
            fill=fill,
            anchor="lm",
            stroke_width=sc(stroke_width) if stroke_width else 0,
            stroke_fill=stroke_fill,
        )
        x += width + spacing * SCALE


def make_ball() -> Image.Image:
    size = sc(430)
    ball = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(ball)
    pad = sc(10)
    box = (pad, pad, size - pad, size - pad)
    d.ellipse(box, fill=CREAM, outline=CHARCOAL, width=sc(13))

    cx = cy = size / 2
    radius = (size - 2 * pad) / 2
    center_r = radius * 0.235
    patch_r = radius * 0.28
    ring_r = radius * 0.57
    center_poly = polygon(cx, cy, center_r, 5, -math.pi / 2)
    d.polygon(center_poly, fill=CHARCOAL)
    outer_centers: list[tuple[float, float]] = []
    for i in range(5):
        angle = -math.pi / 2 + i * 2 * math.pi / 5
        ox = cx + math.cos(angle) * ring_r
        oy = cy + math.sin(angle) * ring_r
        outer_centers.append((ox, oy))
        d.polygon(polygon(ox, oy, patch_r, 5, angle + math.pi / 2), fill=CHARCOAL)

    for i, angle in enumerate([-math.pi / 2 + j * 2 * math.pi / 5 for j in range(5)]):
        start = center_poly[i]
        end = (round(outer_centers[i][0]), round(outer_centers[i][1]))
        d.line((start, end), fill=CHARCOAL, width=sc(7))
        left_angle = angle - math.pi / 5
        right_angle = angle + math.pi / 5
        edge_left = (round(cx + math.cos(left_angle) * radius), round(cy + math.sin(left_angle) * radius))
        edge_right = (round(cx + math.cos(right_angle) * radius), round(cy + math.sin(right_angle) * radius))
        d.line((end, edge_left), fill=CHARCOAL, width=sc(5))
        d.line((end, edge_right), fill=CHARCOAL, width=sc(5))

    # mask seams cleanly inside the ball circle
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse((pad + sc(8), pad + sc(8), size - pad - sc(8), size - pad - sc(8)), fill=255)
    clipped = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    clipped.paste(ball, (0, 0), mask)

    # restore a crisp outer rim after clipping
    ImageDraw.Draw(clipped).ellipse(box, outline=CHARCOAL, width=sc(13))

    highlight = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hd = ImageDraw.Draw(highlight)
    hd.arc((pad + sc(42), pad + sc(42), size - pad - sc(42), size - pad - sc(42)), 205, 320, fill=(255, 255, 255, 155), width=sc(12))
    clipped.alpha_composite(highlight)
    return clipped


def make_logo() -> Image.Image:
    image = make_background()
    d = ImageDraw.Draw(image)

    # Badge rings
    d.ellipse((sc(66), sc(66), sc(958), sc(958)), outline=(255, 255, 255, 28), width=sc(27))
    d.ellipse((sc(72), sc(72), sc(952), sc(952)), outline=CREAM, width=sc(15))
    d.ellipse((sc(102), sc(102), sc(922), sc(922)), outline=GOLD, width=sc(5))
    d.ellipse((sc(116), sc(116), sc(908), sc(908)), outline=(244, 241, 232, 60), width=sc(2))

    for i in range(8):
        angle = -math.pi / 2 + i * math.pi / 4
        radius = 449
        x = 512 + math.cos(angle) * radius
        y = 512 + math.sin(angle) * radius
        if i not in (0, 2, 4, 6):
            d.ellipse((sc(x - 4), sc(y - 4), sc(x + 4), sc(y + 4)), fill=GOLD)

    draw_star(d, 512, 150, 20, GOLD)

    font_path = Path(r"C:\Windows\Fonts\arialbd.ttf")
    font = ImageFont.truetype(str(font_path), sc(178))
    draw_spaced_text(d, 512, 267, "CUGB", font, 8, (0, 0, 0, 95))
    draw_spaced_text(d, 512, 253, "CUGB", font, 8, CREAM, stroke_width=2, stroke_fill=GOLD)

    # Gold underline with end caps
    d.rounded_rectangle((sc(357), sc(354), sc(667), sc(365)), radius=sc(6), fill=GOLD)
    d.ellipse((sc(341), sc(346), sc(370), sc(375)), fill=GOLD)
    d.ellipse((sc(654), sc(346), sc(683), sc(375)), fill=GOLD)

    # Motion arcs
    d.arc((sc(145), sc(430), sc(879), sc(905)), 202, 338, fill=(229, 181, 63, 150), width=sc(8))
    d.arc((sc(175), sc(448), sc(849), sc(875)), 17, 60, fill=(244, 241, 232, 100), width=sc(5))

    ball = make_ball()
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse((sc(287), sc(460), sc(737), sc(910)), fill=(0, 0, 0, 90))
    shadow = shadow.filter(ImageFilter.GaussianBlur(sc(24)))
    image.alpha_composite(shadow)

    ball_x = round((S - ball.width) / 2)
    ball_y = sc(470)
    image.alpha_composite(ball, (ball_x, ball_y))

    # Small anchor marks below the ball
    for x in (458, 512, 566):
        d.ellipse((sc(x - 5), sc(876), sc(x + 5), sc(886)), fill=GOLD)

    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS).convert("RGBA")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    logo = make_logo()
    outputs = [
        ("cugb-football-avatar.png", 1024),
        ("cugb-football-avatar-512.png", 512),
        ("cugb-football-avatar-144.png", 144),
    ]
    for filename, size in outputs:
        resized = logo if size == 1024 else logo.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(OUT_DIR / filename, optimize=True)
    print(OUT_DIR / "cugb-football-avatar.png")


if __name__ == "__main__":
    main()
