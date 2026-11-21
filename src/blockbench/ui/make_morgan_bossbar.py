# -*- coding: utf-8 -*-
"""
Barre de boss de Morgan « main de hache », capitaine de la Marine : même style que celle de Gol D. Roger
(make_roger_bossbar.py : barre fine de petits carrés coupée au centre par un grand emblème, décor aux deux bouts, nom en
grosses lettres pixel), mais à son thème :
  - la barre est un tube d'ACIER riveté (comme sa mâchoire), franges dorées de ses épaulettes dessous ;
  - au centre, son portrait : brosse blonde, sourcils froncés, et sa mâchoire d'acier à créneaux carrés et boulons, frappée
    de la mouette de la Marine ; derrière, deux grandes haches croisées (fer noir, demi-lune argentée, manche de bois) ;
  - aux deux bouts, les pans de son manteau bleu clair sans manches, doublure et revers magenta, épaulettes dorées, et les
    pointes de son haut col magenta qui se dressent ;
  - « Capitaine » / « Morgan » en lettres pixel couleur acier ;
  - carrés de vie magenta (la couleur de sa doublure).
Pixel art, 1 texel = 1 pixel d'interface.
  - assets/bloxpiece/textures/gui/morgan_bossbar.png        : décor complet (360 x 64)
  - assets/bloxpiece/textures/gui/morgan_bossbar_emblem.png : le premier plan seul, redessiné par-dessus les carrés (360 x 64)
  - assets/bloxpiece/textures/gui/morgan_bossbar_cells.png  : carrés de vie (plein, vide), 13 x 6
  - aperçu si un dossier est passé : python3 make_morgan_bossbar.py <dossier>
Les positions (FRAME_W, CELLS, CELL_X, CELL_Y...) doivent rester identiques à MorganBossBar.java.
"""
import math, os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'main', 'resources', 'assets', 'bloxpiece', 'textures', 'gui'))

FRAME_W, FRAME_H = 360, 64
CX = FRAME_W // 2
CELLS, CELL, GAP = 39, 6, 1
CELL_Y = 34
CELL_X = CX - (CELLS * (CELL + GAP) - GAP) // 2
CELL_END = CELL_X + CELLS * (CELL + GAP) - GAP
LEFT_ATTACH, RIGHT_ATTACH = CELL_X + 2, CELL_END - 3
EMBLEM_Y = 34

T = (0, 0, 0, 0)
GOLD, GOLD_L, GOLD_D, GOLD_XD = (236, 190, 56, 255), (255, 224, 120, 255), (176, 128, 28, 255), (112, 72, 18, 255)
STEEL, STEEL_L, STEEL_XL, STEEL_D, STEEL_XD = (162, 168, 178, 255), (212, 216, 224, 255), (244, 246, 250, 255), (108, 114, 126, 255), (56, 60, 70, 255)
IRON, IRON_L, IRON_D = (52, 54, 62, 255), (88, 92, 104, 255), (30, 32, 38, 255)
EDGE = (232, 236, 242, 255)
WOOD, WOOD_D = (156, 104, 60, 255), (110, 70, 38, 255)
COAT, COAT_L, COAT_D = (176, 222, 240, 255), (214, 242, 252, 255), (130, 184, 210, 255)
MAG, MAG_L, MAG_D, MAG_XD = (182, 40, 114, 255), (226, 92, 160, 255), (126, 22, 78, 255), (70, 10, 42, 255)
SKIN, SKIN_L, SKIN_D = (214, 150, 104, 255), (236, 182, 138, 255), (166, 106, 70, 255)
HAIR, HAIR_L, HAIR_D = (236, 214, 128, 255), (252, 238, 182, 255), (192, 164, 86, 255)
INK, INK_L = (22, 20, 30, 255), (58, 56, 70, 255)
WHITE = (248, 248, 244, 255)
OUTL = (16, 18, 34, 255)

img = Image.new('RGBA', (FRAME_W, FRAME_H), T)
px = img.load()
rnd = random.Random(7)


def put(x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < FRAME_W and 0 <= y < FRAME_H:
        px[x, y] = c


def get(x, y):
    return px[x, y] if 0 <= x < FRAME_W and 0 <= y < FRAME_H else T


def blob(cx, cy, r, c, ry=None):
    ry = r if ry is None else ry
    for y in range(int(cy - ry - 1), int(cy + ry + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if ((x + 0.5 - cx) / r) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                put(x, y, c)


def line(x0, y0, x1, y1, c, w=1.0):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(n + 1):
        t = i / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        if w <= 1.0:
            put(x, y, c)
        else:
            blob(x, y, w / 2, c)


def outline(color, layer=None):
    src = layer or img
    sp = src.load()
    pts = [(x, y) for y in range(FRAME_H) for x in range(FRAME_W) if sp[x, y][3] == 0
           and any(0 <= x + dx < FRAME_W and 0 <= y + dy < FRAME_H and sp[x + dx, y + dy][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for p in pts:
        sp[p] = color


def layer():
    global img, px
    base = img
    img = Image.new('RGBA', (FRAME_W, FRAME_H), T)
    px = img.load()
    return base


def merge(base, edge=OUTL):
    global img, px
    outline(edge)
    base.alpha_composite(img)
    img = base
    px = img.load()


# ---------------------------------------------------------------- 2) la barre fine : tube d'acier riveté, logements des carrés
base = layer()
for x in range(CELL_X - 2, CELL_END + 2):
    for y in range(CELL_Y - 2, CELL_Y + CELL + 2):
        put(x, y, INK if CELL_Y - 1 <= y <= CELL_Y + CELL else STEEL_D)
    put(x, CELL_Y - 2, STEEL_L)
    put(x, CELL_Y + CELL + 1, STEEL_XD)
for i in range(CELLS):
    sx = CELL_X + i * (CELL + GAP)
    for y in range(CELL_Y, CELL_Y + CELL):
        for x in range(sx, sx + CELL):
            put(x, y, (28, 14, 30, 255))
    if i % 5 == 2:                                    # rivets sur le tube
        put(sx + 2, CELL_Y - 2, STEEL_XL)
        put(sx + 2, CELL_Y + CELL + 1, STEEL)
# franges dorées des épaulettes, par touffes sous la barre
for fx in list(range(CELL_X + 14, CX - 30, 18)) + list(range(CX + 36, CELL_END - 12, 18)):
    for k in range(-2, 3):
        ln = 5 - abs(k)
        for j in range(ln):
            put(fx + k, CELL_Y + CELL + 2 + j, GOLD if j < ln - 1 else GOLD_D)
merge(base)

PRE_EMBLEM = img.copy()
# ---------------------------------------------------------------- 1) les pans du manteau bleu clair, doublure magenta, aux deux bouts
base = layer()
for side in (-1, 1):
    ex = LEFT_ATTACH if side < 0 else RIGHT_ATTACH
    for k in range(4):
        ang = 0.15 + k * 0.30
        ln = 40 - k * 5
        for i in range(int(ln)):
            t = i / ln
            x = ex + side * (math.cos(ang) * i + 1)
            y = 36 + math.sin(ang) * i * 0.8 + math.sin(t * 4 + k) * 0.8
            w = 9.0 * (1 - t) ** 0.8 + 1.5
            blob(x, y, w / 2, COAT if k % 2 == 0 else COAT_L)
            if i > 2:                                  # revers magenta sur le bord bas de chaque pan
                put(x, y + w / 2, MAG if k % 2 == 0 else MAG_D)
                put(x, y + w / 2 - 1, MAG_L if k % 2 == 0 else MAG)
    blob(ex, 35, 6, GOLD_D, 4.5)                       # épaulette dorée
    blob(ex, 34.5, 5, GOLD, 3.5)
    blob(ex - side, 33.5, 3, GOLD_L, 1.8)
merge(base)

# pointes de son haut col (magenta dedans, bleu clair dehors) qui se dressent aux deux bouts
base = layer()
for side in (-1, 1):
    ex = LEFT_ATTACH if side < 0 else RIGHT_ATTACH
    for f, (ang, ln, col, rim) in enumerate(((1.2, 20, MAG, COAT_L), (1.5, 16, MAG_D, COAT))):
        for i in range(ln):
            t = i / ln
            a = ang - t * 0.35
            x = ex + side * (math.cos(a) * i * 0.9 + 2)
            y = 31 - math.sin(a) * i * 0.95
            w = max(0.6, 3.0 * (1 - t) + 0.4)
            blob(x, y, w, col)
            put(x + side * w, y, rim)
merge(base)

# ---------------------------------------------------------------- 4) deux grandes haches croisées derrière le portrait
base = layer()
for side in (-1, 1):
    hx, hy = CX + side * 22, 60                       # bout du manche en bas
    tx, ty = CX - side * 20, 8                        # fer en haut
    line(hx, hy, tx, ty, WOOD_D, 3.0)                 # manche de bois
    line(hx - side * 0.5, hy - 0.5, tx - side * 0.5, ty - 0.5, WOOD)
    # fer au bout du manche : bloc noir (douille) et grande demi-lune argentée tournée vers l'extérieur et le haut
    ux, uy = (tx - hx), (ty - hy)
    L = math.hypot(ux, uy); ux, uy = ux / L, uy / L       # le long du manche (vers le haut)
    nx, ny = side * uy, -side * ux                          # perpendiculaire au manche
    if nx * side > 0:                                       # le fer est en haut du côté opposé : lame vers l'extérieur
        nx, ny = -nx, -ny
    cx_, cy_ = tx - ux * 6, ty - uy * 6
    k = -8.0
    while k <= 8.0:
        d = 0.0
        while d <= 15.0:
            reach = 9.0 + 4.0 * (1 - (k / 8.0) ** 2)          # bord de la lame, bombé (demi-lune)
            if d <= reach:
                x = cx_ + ux * k + nx * d
                y = cy_ + uy * k + ny * d
                if d < 2.5:
                    c = IRON_D if abs(k) > 6 else IRON_L       # douille autour du manche
                elif d > reach - 3.0:
                    c = EDGE if d > reach - 1.5 else STEEL_L   # demi-lune argentée, fil poli
                else:
                    c = IRON if (k + d) % 5 > 0.6 else IRON_L  # fer noir, reflets
                put(x, y, c)
            d += 0.4
        k += 0.4
merge(base)

# ---------------------------------------------------------------- 5) portrait de Morgan : brosse blonde, regard dur, mâchoire d'acier
base = layer()
cy = EMBLEM_Y
for y in range(cy - 18, cy + 2):                      # visage (carré, puissant)
    for x in range(CX - 12, CX + 13):
        d = (x - CX) * 0.5 + (y - cy) * 0.5
        put(x, y, SKIN_L if d < -8 else SKIN_D if d > 6 else SKIN)
for y in range(cy - 24, cy - 16):                     # brosse blonde, plate
    for x in range(CX - 13, CX + 14):
        if y == cy - 24 and (x + y) % 3 == 0:
            continue                                   # pointes de la brosse
        put(x, y, HAIR_L if y < cy - 22 else HAIR_D if (x * 3 + y) % 7 == 0 else HAIR)
for side in (-1, 1):                                  # sourcils froncés, petits yeux durs
    for i in range(8):
        put(CX + side * (2 + i), cy - 12 + (i // 3) * -1 + (1 if i < 2 else 0), INK)
        put(CX + side * (2 + i), cy - 11 + (i // 3) * -1 + (1 if i < 2 else 0), INK_L)
    for i in range(4):
        put(CX + side * (4 + i), cy - 8, WHITE)
    put(CX + side * 5, cy - 8, INK)
    put(CX + side * 6, cy - 8, INK)
line(CX, cy - 9, CX, cy - 4, SKIN_D)                  # nez
put(CX - 1, cy - 4, SKIN_D); put(CX + 1, cy - 4, SKIN_D)
for x in range(CX - 11, CX + 12):                     # bouche sombre, entre les créneaux
    put(x, cy - 2, INK)
# la mâchoire d'acier : plaque de la largeur du visage, créneaux carrés, bas arrondi, boulons, mouette de la Marine
top = cy - 1
for y in range(top, top + 14):
    half = 12 if y < top + 9 else 12 - (y - top - 8) * 2
    for x in range(CX - half, CX + half + 1):
        d = (x - CX) * 0.4 + (y - top) * 0.6
        put(x, y, STEEL_L if d < -2 else STEEL_D if d > 6 else STEEL)
for k in range(4):                                    # créneaux carrés au-dessus
    x0 = CX - 12 + k * 7
    for y in range(top - 4, top):
        for x in range(x0, x0 + 4):
            put(x, y, STEEL_XL if y == top - 4 else STEEL_L if x == x0 else STEEL)
for side in (-1, 1):                                  # boulons ronds des coins
    blob(CX + side * 12, top - 1, 2.0, STEEL_D)
    put(CX + side * 12, top - 2, STEEL_XL)
for i, row in enumerate(('.##.##.', '#..#..#')):      # mouette de la Marine
    for j, v in enumerate(row):
        if v == '#':
            put(CX - 3 + j, top + 6 + i, INK_L)
merge(base)

emblem = Image.new('RGBA', (FRAME_W, FRAME_H), T)
ep, pp = emblem.load(), PRE_EMBLEM.load()
for y in range(FRAME_H):
    for x in range(FRAME_W):
        if px[x, y] != pp[x, y] and px[x, y][3]:
            ep[x, y] = px[x, y]
emblem.save(os.path.join(OUT, 'morgan_bossbar_emblem.png'))

# ---------------------------------------------------------------- 7) le nom, en lettres pixel couleur acier
GLYPHS = {
    'C': [".####.", "##..##", "##....", "##....", "##....", "##..##", ".####."],
    'a': ["......", "......", ".####.", "....##", ".#####", "##..##", ".#####"],
    'p': ["......", "......", "#####.", "##..##", "##..##", "#####.", "##....", "##...."],
    'i': ["##", "..", "##", "##", "##", "##", "##"],
    't': [".##..", ".##..", "#####", ".##..", ".##..", ".##..", "..###"],
    'n': ["......", "......", "#####.", "##..##", "##..##", "##..##", "##..##"],
    'e': ["......", "......", ".####.", "##..##", "######", "##....", ".####."],
    'M': ["##...##", "###.###", "#######", "##.#.##", "##...##", "##...##", "##...##"],
    'o': ["......", "......", ".####.", "##..##", "##..##", "##..##", ".####."],
    'r': [".....", ".....", "##.##", "####.", "##...", "##...", "##..."],
    'g': ["......", "......", ".#####", "##..##", "##..##", ".#####", "....##", ".####."],
}


def word(text, cx, top):
    width = sum(len(GLYPHS[ch][0]) + 1 for ch in text) - 1
    x = cx - width // 2
    for ch in text:
        g = GLYPHS[ch]
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#':
                    col = STEEL_XL if r == 0 else STEEL_L if r < 3 else STEEL if r < 6 else STEEL_D
                    put(x + c, top + r, col)
        x += len(g[0]) + 1


base = layer()
word('Capitaine', (CELL_X + CX - 27) // 2, 22)
word('Morgan', (CX + 27 + CELL_END) // 2, 22)
outline(OUTL)
shadow = img.copy()
sp = shadow.load()
for y in range(FRAME_H - 1, 0, -1):
    for x in range(FRAME_W - 1, 0, -1):
        sp[x, y] = OUTL if sp[x - 1, y - 1][3] and not px[x, y][3] else (px[x, y] if px[x, y][3] else T)
img = shadow
px = img.load()
base.alpha_composite(img)
img = base
px = img.load()

img.save(os.path.join(OUT, 'morgan_bossbar.png'))

# ---------------------------------------------------------------- 8) carrés de vie magenta : plein (0..5), vide (7..12)
cells = Image.new('RGBA', (13, 6), T)
cp = cells.load()
for y in range(6):
    for x in range(6):
        if x in (0, 5) or y in (0, 5):
            c = MAG_D if y < 5 else MAG_XD
        elif y == 1:
            c = (250, 150, 200, 255)
        elif y == 4:
            c = MAG_D
        else:
            c = (220, 56, 140, 255)
        cp[x, y] = c
        cp[7 + x, y] = (64, 22, 48, 255) if x in (0, 5) or y in (0, 5) else (36, 12, 28, 255)
cp[1, 1] = (255, 214, 236, 255)
cells.save(os.path.join(OUT, 'morgan_bossbar_cells.png'))

if len(sys.argv) > 1:
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    frame = img.copy()
    life = 0.62 * CELLS
    for i in range(CELLS):
        x = CELL_X + i * (CELL + GAP)
        frame.alpha_composite(cells.crop((7, 0, 13, 6)), (x, CELL_Y))
        w = int(6 * max(0.0, min(1.0, life - i)))
        if w > 0:
            frame.alpha_composite(cells.crop((0, 0, w, 6)), (x, CELL_Y))
    frame.alpha_composite(emblem)
    prev = Image.new('RGBA', (FRAME_W + 40, FRAME_H + 20), (120, 116, 112, 255))
    prev.alpha_composite(frame, (20, 10))
    prev.resize((prev.width * 3, prev.height * 3), Image.NEAREST).save(os.path.join(out, 'morgan_bossbar_apercu.png'))
print('ok', CELL_X, CELL_END)
