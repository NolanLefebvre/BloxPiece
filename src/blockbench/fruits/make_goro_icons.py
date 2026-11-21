# -*- coding: utf-8 -*-
"""Icônes des 4 attaques du Goro Goro no Mi (textures/gui/ability/goro/<attaque>.png, 128x128) : pixel art dessiné sur une
grille de 32 x 32 puis agrandi x4, contour sombre épais, foudre bleue (même famille que les icônes des autres fruits).
Usage : python3 src/blockbench/fruits/make_goro_icons.py"""
import math, os, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'main/resources/assets/bloxpiece/textures/gui/ability/goro')
os.makedirs(OUT, exist_ok=True)
N = 32
INK, CORE, BLUE, BLUE_D, CLOUD, CLOUD_D, CLOUD_L = (12, 16, 40), (236, 248, 255), (64, 150, 255), (30, 80, 210), (88, 96, 120), (56, 62, 84), (130, 138, 160)


def bolt(d, pts, w=2):
    d.line(pts, fill=BLUE, width=w + 2)
    d.line(pts, fill=CORE, width=max(1, w - 1))


def zig(x0, y0, x1, y1, n, amp, rnd):
    pts = [(x0, y0)]
    for i in range(1, n):
        t = i / n
        pts.append((x0 + (x1 - x0) * t + rnd.uniform(-amp, amp), y0 + (y1 - y0) * t))
    pts.append((x1, y1))
    return pts


def cloud(d, cx, cy, w, h):
    for dx, dy, r in ((-0.35, 0.1, 0.32), (0.0, -0.15, 0.4), (0.35, 0.1, 0.32), (0.15, 0.2, 0.3), (-0.15, 0.2, 0.3)):
        d.ellipse((cx + dx * w - r * w, cy + dy * h - r * h * 1.2, cx + dx * w + r * w, cy + dy * h + r * h * 1.2), fill=CLOUD)
    for dx, dy, r in ((-0.1, -0.2, 0.2), (0.2, -0.1, 0.15)):
        d.ellipse((cx + dx * w - r * w, cy + dy * h - r * h, cx + dx * w + r * w, cy + dy * h + r * h), fill=CLOUD_L)
    d.rectangle((cx - 0.55 * w, cy + 0.28 * h, cx + 0.55 * w, cy + 0.38 * h), fill=CLOUD_D)


def outline(im):
    px = im.load()
    out = im.copy()
    po = out.load()
    for x in range(N):
        for y in range(N):
            if px[x, y][3] == 0 and any(0 <= x + a < N and 0 <= y + b < N and px[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = INK + (255,)
    return out


def save(name, draw_fn):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    draw_fn(d, random.Random(name))
    outline(im).resize((128, 128), Image.NEAREST).save(os.path.join(OUT, name + '.png'))


def mamaragan(d, r):
    cloud(d, 16, 9, 26, 12)
    for x1 in (5, 16, 27):
        bolt(d, zig(16 + (x1 - 16) * 0.3, 13, x1, 29, 4, 2.2, r), 2)


def kari(d, r):
    for x, y, l in ((7, 2, 22), (17, 1, 26), (26, 4, 20)):
        bolt(d, zig(x + 2, y, x - 1, y + l, 5, 1.8, r), 3 if x == 17 else 2)
    d.ellipse((3, 27, 29, 31), fill=BLUE_D)


def el_thor(d, r):
    cloud(d, 16, 7, 30, 11)
    d.rectangle((12, 11, 20, 30), fill=BLUE)
    d.rectangle((14, 11, 18, 30), fill=CORE)
    for side in (-1, 1):
        bolt(d, zig(16 + side * 3, 14, 16 + side * 10, 26, 3, 1.5, r), 1)
    d.ellipse((5, 27, 27, 31), fill=BLUE_D)


def raigo(d, r):
    d.ellipse((4, 4, 28, 28), fill=BLUE_D)
    d.ellipse((7, 7, 25, 25), fill=BLUE)
    d.ellipse((11, 10, 21, 20), fill=CORE)
    for a0 in range(0, 360, 60):
        a = math.radians(a0 + r.uniform(-10, 10))
        bolt(d, zig(16 + math.cos(a) * 11, 16 + math.sin(a) * 11, 16 + math.cos(a) * 15.5, 16 + math.sin(a) * 15.5, 2, 1.0, r), 1)
    for k in range(3):
        a = r.uniform(0, 6.28)
        bolt(d, [(16 + math.cos(a) * 9, 16 + math.sin(a) * 9), (16 + math.cos(a + 0.8) * 10, 16 + math.sin(a + 0.8) * 10)], 1)


for name, fn in (('goro_mamaragan', mamaragan), ('goro_kari', kari), ('goro_el_thor', el_thor), ('goro_raigo', raigo)):
    save(name, fn)
print('ok')
