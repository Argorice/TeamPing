#!/usr/bin/env python3
"""Генерация текстур мода. Иконки белые с серым контуром — их красит рендер
в цвет команды, поэтому цветного в них быть не должно."""

import math
import os

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "teamping")
PING_DIR = os.path.join(OUT, "textures", "ping")

SS = 8  # суперсэмплинг
WHITE = (255, 255, 255, 255)
OUTLINE = (48, 48, 48, 255)
SHADE = (170, 170, 170, 255)
# Настоящая дырка: ImageDraw пишет пиксели напрямую, а не смешивает, поэтому
# заливка нулевой альфой прорезает иконку насквозь.
HOLE = (0, 0, 0, 0)


def canvas(size):
    img = Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def finish(img, size, path):
    img = img.resize((size, size), Image.LANCZOS)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", path)


def poly(draw, points, fill, outline=None, width=0):
    draw.polygon(points, fill=fill, outline=outline, width=width)


def diamond(cx, cy, r):
    return [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]


def ngon(cx, cy, r, n, rot=0.0):
    return [
        (cx + r * math.cos(rot + 2 * math.pi * i / n),
         cy + r * math.sin(rot + 2 * math.pi * i / n))
        for i in range(n)
    ]


def make_normal(size=64):
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    # внешний ромб-контур
    poly(d, diamond(c, c, s * 0.46), fill=OUTLINE)
    poly(d, diamond(c, c, s * 0.40), fill=WHITE)
    poly(d, diamond(c, c, s * 0.26), fill=OUTLINE)
    poly(d, diamond(c, c, s * 0.18), fill=HOLE)
    finish(img, size, os.path.join(PING_DIR, "normal.png"))


def make_danger(size=64):
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    top = s * 0.08
    bottom = s * 0.90
    half = s * 0.46
    poly(d, [(c, top), (c + half, bottom), (c - half, bottom)], fill=OUTLINE)
    inset = s * 0.10
    poly(d, [(c, top + inset * 1.3), (c + half - inset, bottom - inset * 0.6),
             (c - half + inset, bottom - inset * 0.6)], fill=WHITE)
    # восклицательный знак
    bar_w = s * 0.075
    d.rounded_rectangle([c - bar_w, s * 0.34, c + bar_w, s * 0.63],
                        radius=bar_w, fill=OUTLINE)
    d.ellipse([c - bar_w, s * 0.68, c + bar_w, s * 0.68 + bar_w * 2], fill=OUTLINE)
    finish(img, size, os.path.join(PING_DIR, "danger.png"))


def make_resource(size=64):
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    poly(d, ngon(c, c, s * 0.46, 6, -math.pi / 2), fill=OUTLINE)
    poly(d, ngon(c, c, s * 0.39, 6, -math.pi / 2), fill=WHITE)
    # грани
    inner = ngon(c, c, s * 0.39, 6, -math.pi / 2)
    d.line([inner[0], (c, c)], fill=SHADE, width=int(s * 0.035))
    d.line([inner[2], (c, c)], fill=SHADE, width=int(s * 0.035))
    d.line([inner[4], (c, c)], fill=SHADE, width=int(s * 0.035))
    poly(d, ngon(c, c, s * 0.17, 6, -math.pi / 2), fill=OUTLINE)
    poly(d, ngon(c, c, s * 0.11, 6, -math.pi / 2), fill=HOLE)
    finish(img, size, os.path.join(PING_DIR, "resource.png"))


def make_waypoint(size=64):
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    # капля: круг + треугольный хвост
    r = s * 0.32
    cy = s * 0.36
    d.ellipse([c - r - s * 0.05, cy - r - s * 0.05, c + r + s * 0.05, cy + r + s * 0.05], fill=OUTLINE)
    poly(d, [(c - r * 0.85, cy + r * 0.62), (c + r * 0.85, cy + r * 0.62), (c, s * 0.96)], fill=OUTLINE)
    d.ellipse([c - r, cy - r, c + r, cy + r], fill=WHITE)
    poly(d, [(c - r * 0.66, cy + r * 0.60), (c + r * 0.66, cy + r * 0.60), (c, s * 0.90)], fill=WHITE)
    hole = r * 0.44
    d.ellipse([c - hole, cy - hole, c + hole, cy + hole], fill=OUTLINE)
    hole *= 0.68
    d.ellipse([c - hole, cy - hole, c + hole, cy + hole], fill=HOLE)
    finish(img, size, os.path.join(PING_DIR, "waypoint.png"))


def make_ally(size=64):
    """Щит с шевроном: свой."""
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    shield = [(c, s * 0.06), (s * 0.90, s * 0.24), (s * 0.90, s * 0.56),
              (c, s * 0.96), (s * 0.10, s * 0.56), (s * 0.10, s * 0.24)]
    poly(d, shield, fill=OUTLINE)
    inner = [(c, s * 0.17), (s * 0.81, s * 0.31), (s * 0.81, s * 0.55),
             (c, s * 0.86), (s * 0.19, s * 0.55), (s * 0.19, s * 0.31)]
    poly(d, inner, fill=WHITE)
    poly(d, [(c, s * 0.30), (s * 0.68, s * 0.55), (s * 0.57, s * 0.55),
             (c, s * 0.43), (s * 0.43, s * 0.55), (s * 0.32, s * 0.55)], fill=OUTLINE)
    finish(img, size, os.path.join(PING_DIR, "ally.png"))


def make_enemy(size=64):
    """Прицел с перекрестием: чужой. Тело белое — его красит рендер,
    иначе метка осталась бы тёмным пятном вместо красной."""
    img, d = canvas(size)
    s = size * SS
    c = s / 2

    def ring(r, fill):
        d.ellipse([c - r, c - r, c + r, c + r], fill=fill)

    # перекрестие рисуем первым, кольцо ляжет поверх и подрежет его
    outer, inner = s * 0.075, s * 0.038
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        d.rectangle(sorted_box(c + dx * s * 0.10, c + dy * s * 0.10,
                               c + dx * s * 0.48, c + dy * s * 0.48, outer), fill=OUTLINE)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        d.rectangle(sorted_box(c + dx * s * 0.10, c + dy * s * 0.10,
                               c + dx * s * 0.455, c + dy * s * 0.455, inner), fill=WHITE)

    ring(s * 0.40, OUTLINE)
    ring(s * 0.335, WHITE)
    ring(s * 0.245, OUTLINE)
    ring(s * 0.185, HOLE)

    ring(s * 0.105, OUTLINE)
    ring(s * 0.055, WHITE)
    finish(img, size, os.path.join(PING_DIR, "enemy.png"))


def sorted_box(x0, y0, x1, y1, half):
    """Прямоугольник-луч заданной толщины между двумя точками по оси."""
    if x0 == x1:
        return [x0 - half, min(y0, y1), x1 + half, max(y0, y1)]
    return [min(x0, x1), y0 - half, max(x0, x1), y1 + half]


def make_vessel(size=64):
    """Силуэт корпуса с мачтой: судно, оно же летающая техника."""
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    hull = [(s * 0.08, s * 0.56), (s * 0.92, s * 0.56),
            (s * 0.74, s * 0.90), (s * 0.26, s * 0.90)]
    poly(d, hull, fill=OUTLINE)
    poly(d, [(s * 0.18, s * 0.62), (s * 0.82, s * 0.62),
             (s * 0.70, s * 0.82), (s * 0.30, s * 0.82)], fill=WHITE)
    mast = s * 0.045
    d.rectangle([c - mast, s * 0.10, c + mast, s * 0.56], fill=OUTLINE)
    poly(d, [(c + s * 0.07, s * 0.14), (c + s * 0.40, s * 0.34),
             (c + s * 0.07, s * 0.50)], fill=OUTLINE)
    poly(d, [(c + s * 0.11, s * 0.21), (c + s * 0.31, s * 0.34),
             (c + s * 0.11, s * 0.44)], fill=WHITE)
    finish(img, size, os.path.join(PING_DIR, "vessel.png"))


def make_arrow(size=32):
    img, d = canvas(size)
    s = size * SS
    c = s / 2
    poly(d, [(c, s * 0.05), (s * 0.95, s * 0.88), (c, s * 0.66), (s * 0.05, s * 0.88)], fill=OUTLINE)
    poly(d, [(c, s * 0.19), (s * 0.81, s * 0.80), (c, s * 0.62), (s * 0.19, s * 0.80)], fill=WHITE)
    finish(img, size, os.path.join(PING_DIR, "arrow.png"))


def make_mod_icon(size=128):
    """Иконка мода: тот же ромб, что у обычной метки, только крупно и на фоне."""
    img, d = canvas(size)
    s = size * SS
    c = s / 2

    # фон: вертикальный градиент от тёмно-синего к почти чёрному
    grad = Image.new("RGBA", (1, s))
    px = grad.load()
    top, bottom = (13, 24, 48), (9, 16, 32)
    for y in range(s):
        t = y / (s - 1)
        px[0, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
    img.paste(grad.resize((s, s)), (0, 0))

    # свечение из центра
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse(
        [c - s * 0.42, c - s * 0.42, c + s * 0.42, c + s * 0.42], fill=(46, 108, 190, 110))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(s * 0.10)))

    d = ImageDraw.Draw(img)
    d.ellipse([c - s * 0.40, c - s * 0.40, c + s * 0.40, c + s * 0.40],
              outline=(34, 68, 118, 255), width=int(s * 0.012))
    d.ellipse([c - s * 0.325, c - s * 0.325, c + s * 0.325, c + s * 0.325],
              outline=(44, 92, 156, 255), width=int(s * 0.022))

    dark = (8, 14, 26, 255)
    poly(d, diamond(c, c, s * 0.285), fill=dark)
    poly(d, diamond(c, c, s * 0.245), fill=(242, 248, 255, 255))
    poly(d, diamond(c, c, s * 0.105), fill=dark)

    # скруглённые углы вырезаем маской: рисовать по ним сразу — грязные края
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1],
                                           radius=int(s * 0.185), fill=255)
    img.putalpha(mask)

    finish(img, size, os.path.join(OUT, "icon.png"))
    # та же иконка крупно — для страницы мода на CurseForge и Modrinth
    finish(img.copy(), 512, os.path.join(os.path.dirname(__file__),
                                         "..", "..", "docs", "icon.png"))


if __name__ == "__main__":
    make_normal()
    make_danger()
    make_resource()
    make_waypoint()
    make_ally()
    make_enemy()
    make_vessel()
    make_arrow()
    make_mod_icon()
