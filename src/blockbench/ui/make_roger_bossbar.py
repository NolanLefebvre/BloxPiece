# -*- coding: utf-8 -*-
"""
Barre de boss de Gol D. Roger (4e concept, style « emblème central ») : une barre fine faite de petits carrés, coupée au
centre par un grand emblème : le Jolly Roger de Roger (crâne aux yeux rouges du Haki des Rois, grosse moustache, coiffé de
son tricorne rouge et or) devant deux sabres Ace croisés ; aux deux bouts, les pans de sa cape rouge bordés d'or et les
plumes blanches de son chapeau ; quelques éclairs noirs et rouges du Haki des Rois ; le nom « Gol D. » / « Roger » en
grosses lettres pixel dessinées à la main, de part et d'autre de l'emblème.
Pixel art, 1 texel = 1 pixel d'interface.
  - assets/bloxpiece/textures/gui/roger_bossbar.png       : décor complet (360 x 64)
  - assets/bloxpiece/textures/gui/roger_bossbar_emblem.png : l'emblème seul, redessiné par-dessus les carrés (360 x 64)
  - assets/bloxpiece/textures/gui/roger_bossbar_cells.png : carrés de vie (plein, vide), 13 x 6
  - aperçu si un dossier est passé : python3 make_roger_bossbar.py <dossier>
Les positions (FRAME_W, CELLS, CELL_X, CELL_Y...) doivent rester identiques à RogerBossBar.java.
"""
import math, os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'main', 'resources', 'assets', 'bloxpiece', 'textures', 'gui'))

FRAME_W, FRAME_H = 360, 64
CX = FRAME_W // 2
CELLS, CELL, GAP = 39, 6, 1                       # UNE seule rangée continue de carrés, d'un bout à l'autre
CELL_Y = 34
CELL_X = CX - (CELLS * (CELL + GAP) - GAP) // 2      # premier carré (44) ; dernier carré : CELL_X + 38 x 7 (310..315)
CELL_END = CELL_X + CELLS * (CELL + GAP) - GAP       # 316 (exclu)
LEFT_ATTACH, RIGHT_ATTACH = CELL_X + 2, CELL_END - 3 # les épaulettes recouvrent les bouts de la rangée
EMBLEM_Y = 36                                        # centre du crâne

T = (0, 0, 0, 0)
GOLD, GOLD_L, GOLD_XL, GOLD_D, GOLD_XD = (236, 186, 60, 255), (255, 220, 120, 255), (255, 244, 190, 255), (182, 128, 34, 255), (112, 72, 18, 255)
RED, RED_L, RED_D, RED_XD = (196, 34, 46, 255), (234, 82, 84, 255), (136, 18, 32, 255), (78, 8, 18, 255)
BONE, BONE_L, BONE_D, BONE_XD = (226, 218, 198, 255), (246, 242, 228, 255), (182, 170, 148, 255), (128, 116, 98, 255)
STEEL, STEEL_L, STEEL_D = (196, 202, 210, 255), (236, 240, 246, 255), (130, 138, 150, 255)
INK, INK_L = (24, 16, 18, 255), (62, 50, 54, 255)
WHITE, WHITE_D = (250, 248, 244, 255), (204, 200, 194, 255)
GLOW, GLOW_L = (255, 40, 40, 255), (255, 150, 140, 255)
OUTL = (40, 10, 14, 255)

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


# ---------------------------------------------------------------- 2) la barre fine : tube doré, logements des carrés
base = layer()
for x in range(CELL_X - 2, CELL_END + 2):
    for y in range(CELL_Y - 2, CELL_Y + CELL + 2):
        put(x, y, INK if CELL_Y - 1 <= y <= CELL_Y + CELL else GOLD_D)
    put(x, CELL_Y - 2, GOLD_L)
    put(x, CELL_Y + CELL + 1, GOLD_XD)
for i in range(CELLS):
    sx = CELL_X + i * (CELL + GAP)
    for y in range(CELL_Y, CELL_Y + CELL):
        for x in range(sx, sx + CELL):
            put(x, y, (46, 12, 16, 255))
# franges dorées qui pendent sous la barre, par touffes
for fx in list(range(CELL_X + 14, CX - 30, 18)) + list(range(CX + 36, CELL_END - 12, 18)):
    for k in range(-2, 3):
        ln = 5 - abs(k)
        for j in range(ln):
            put(fx + k, CELL_Y + CELL + 2 + j, GOLD if j < ln - 1 else GOLD_D)
merge(base)

PRE_EMBLEM = img.copy()   # tout ce qui est dessiné après (capes, plumes, sabres, crâne, chapeau, éclairs) passe DEVANT les carrés
# ---------------------------------------------------------------- 1) les pans de la cape aux deux bouts (comme des ailes)
base = layer()
for side in (-1, 1):
    ex = LEFT_ATTACH if side < 0 else RIGHT_ATTACH   # attache au bout de la barre (recouvre le bout de la rangée)
    for k in range(4):                               # 4 pans de cape qui s'évasent vers l'extérieur et le bas
        ang = 0.12 + k * 0.32
        ln = 40 - k * 5
        for i in range(int(ln)):
            t = i / ln
            x = ex + side * (math.cos(ang) * i + 1)
            y = 36 + math.sin(ang) * i * 0.8 + math.sin(t * 5 + k) * 1.0
            w = 9.0 * (1 - t) ** 0.8 + 1.5
            blob(x, y, w / 2, RED if k % 2 == 0 else RED_L)
            if i > 2:                                 # ourlet doré sur le bord bas de chaque pan
                put(x, y + w / 2, GOLD if k % 2 == 0 else GOLD_D)
    # attache dorée (épaulette) et ses franges
    blob(ex, 35, 6, GOLD_D, 4.5)
    blob(ex, 34.5, 5, GOLD, 3.5)
    blob(ex - side, 33.5, 3, GOLD_L, 1.8)
merge(base)

# plumes blanches du chapeau, qui jaillissent vers le haut et l'extérieur aux deux bouts
base = layer()
for side in (-1, 1):
    ex = LEFT_ATTACH if side < 0 else RIGHT_ATTACH
    for f, (ang, ln) in enumerate(((1.15, 22), (1.45, 19), (0.85, 16))):
        for i in range(ln):
            t = i / ln
            a = ang - t * 0.55
            x = ex + side * (math.cos(a) * i * 0.9 + 2)
            y = 31 - math.sin(a) * i * 0.95
            blob(x, y, max(0.6, 2.4 * (1 - t) + 0.5), WHITE if f != 1 else WHITE_D)
            if i % 3 == 1 and i < ln - 3:
                put(x + side * 1.5, y + 1, WHITE_D)
merge(base)

# ---------------------------------------------------------------- 4) deux sabres Ace croisés derrière le crâne
base = layer()
for side in (-1, 1):
    hx, hy = CX + side * 20, 58                       # poignée en bas
    tx, ty = CX - side * 22, 6                        # pointe en haut
    line(hx, hy, tx, ty, STEEL_D, 3.0)                # lame
    line(hx + side * -0.5, hy - 0.5, tx + side * -0.5, ty - 0.5, STEEL_L)
    gx, gy = hx + (tx - hx) * 0.16, hy + (ty - hy) * 0.16
    blob(gx, gy, 3.6, GOLD_D, 2.6)                    # garde en coupe dorée
    blob(gx, gy - 0.5, 2.4, GOLD_L, 1.6)
    line(hx, hy, gx, gy, INK, 2.4)                    # poignée noire
    put(hx, hy, GOLD)
merge(base)

# ---------------------------------------------------------------- 5) le crâne de Roger : yeux rouges, grosse moustache
base = layer()
cy = EMBLEM_Y
blob(CX, cy - 2, 13, BONE, 12)                        # boîte crânienne
blob(CX, cy + 7, 8.5, BONE, 6)                        # mâchoire
for y in range(int(cy - 14), int(cy + 14)):           # ombrage : clair en haut à gauche, sombre en bas à droite
    for x in range(CX - 14, CX + 15):
        if get(x, y) == BONE:
            d = (x - CX) * 0.6 + (y - cy) * 0.8
            put(x, y, BONE_L if d < -9 else BONE_D if d > 7 else BONE)
for ex in (-5, 5):                                    # orbites, yeux rouges du Haki
    blob(CX + ex + 0.5, cy - 1, 3.6, INK, 3.2)
    put(CX + ex, cy - 1, GLOW)
    put(CX + ex + 1, cy - 1, GLOW_L)
    put(CX + ex, cy, GLOW)
blob(CX + 0.5, cy + 4, 1.3, INK, 1.6)                 # nez
for x in range(CX - 5, CX + 7, 2):                    # dents
    line(x, cy + 9, x, cy + 11, BONE_XD)
for side in (-1, 1):                                  # la moustache de Roger, pointes recourbées
    for i in range(40):
        t = i / 39
        x = CX + side * (1 + 17 * t)
        y = cy + 7 + 1.5 * math.sin(math.pi * min(1.0, t / 0.7)) - (6 * ((t - 0.7) / 0.3) ** 2 if t > 0.7 else 0)
        blob(x, y, max(0.6, 2.6 * (1 - t) ** 0.8 + 0.5), INK)
        if i % 4 == 0 and t < 0.7:
            put(x, y - 1.5, INK_L)
merge(base)

# ---------------------------------------------------------------- 6) son tricorne rouge et or, posé sur le crâne
base = layer()
hy = cy - 13
blob(CX, hy - 3, 10, RED, 5)                          # calotte, basse
for x in range(CX - 25, CX + 26):                     # bord relevé, grandes cornes sur les côtés
    d = abs(x - CX) / 25.0
    top = hy - 1 - 10 * d ** 2.4
    bottom = hy + 4 - 4 * d ** 2
    for y in range(int(top), int(bottom) + 1):
        put(x, y, RED_L if y <= top + 1 else RED if y < bottom - 1 else RED_D)
    put(x, top, GOLD_L)                               # galon doré
    put(x, top + 1, GOLD)
    put(x, bottom, GOLD_D)
blob(CX + 0.5, hy - 3, 2.2, BONE_L)                   # petit Jolly Roger sur la calotte
put(CX, hy - 3, INK)
put(CX + 1, hy - 3, INK)
merge(base)

# ---------------------------------------------------------------- 3) éclairs du Haki des Rois autour de l'emblème (au-dessus de la barre)
base = layer()
for side in (-1, 1):
    for b in range(2):
        x, y = CX + side * 17, EMBLEM_Y - 6 + b * 4
        a = -0.5 - 0.4 * b
        for s_ in range(4):
            a += rnd.uniform(-0.6, 0.6)
            nx, ny = x + side * math.cos(a) * 4.5, y + math.sin(a) * 3
            if ny > CELL_Y - 4:
                ny = CELL_Y - 4
            line(x, y, nx, ny, INK, 2.0)
            line(x, y, nx, ny, GLOW)
            x, y = nx, ny
merge(base, (110, 10, 20, 255))

# le premier plan seul (pixels ajoutés depuis PRE_EMBLEM : capes, plumes, emblème), en calque à part : le jeu le redessine
# par-dessus les carrés de vie (ils passent derrière le crâne et sous les épaulettes)
emblem = Image.new('RGBA', (FRAME_W, FRAME_H), T)
ep, pp = emblem.load(), PRE_EMBLEM.load()
for y in range(FRAME_H):
    for x in range(FRAME_W):
        if px[x, y] != pp[x, y] and px[x, y][3]:
            ep[x, y] = px[x, y]
emblem.save(os.path.join(OUT, 'roger_bossbar_emblem.png'))

# ---------------------------------------------------------------- 7) le nom, en grosses lettres pixel faites main
GLYPHS = {
    'G': [".####.", "##..##", "##....", "##.###", "##..##", "##..##", ".####."],
    'o': ["......", "......", ".####.", "##..##", "##..##", "##..##", ".####."],
    'l': ["##", "##", "##", "##", "##", "##", "##"],
    'D': ["#####.", "##..##", "##..##", "##..##", "##..##", "##..##", "#####."],
    '.': ["..", "..", "..", "..", "..", "##", "##"],
    'R': ["#####.", "##..##", "##..##", "#####.", "##.##.", "##..##", "##..##"],
    'g': ["......", "......", ".#####", "##..##", "##..##", ".#####", "....##", ".####."],
    'e': ["......", "......", ".####.", "##..##", "######", "##....", ".####."],
    'r': [".....", ".....", "##.##", "####.", "##...", "##...", "##..."],
    ' ': ["..", "..", "..", "..", "..", "..", ".."],
}


def word(text, cx, top):
    width = sum(len(GLYPHS[ch][0]) + 1 for ch in text) - 1
    x = cx - width // 2
    for ch in text:
        g = GLYPHS[ch]
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#':
                    col = GOLD_XL if r == 0 else GOLD_L if r < 3 else GOLD if r < 6 else GOLD_D
                    put(x + c, top + r, col)
        x += len(g[0]) + 1


base = layer()
word('Gol D.', (CELL_X + CX - 25) // 2, 22)
word('Roger', (CX + 25 + CELL_END) // 2, 22)
outline(OUTL)
shadow = img.copy()                                    # ombre portée d'un pixel
sp = shadow.load()
for y in range(FRAME_H - 1, 0, -1):
    for x in range(FRAME_W - 1, 0, -1):
        sp[x, y] = OUTL if sp[x - 1, y - 1][3] and not px[x, y][3] else (px[x, y] if px[x, y][3] else T)
img = shadow
px = img.load()
base.alpha_composite(img)
img = base
px = img.load()

img.save(os.path.join(OUT, 'roger_bossbar.png'))

# ---------------------------------------------------------------- 8) carrés de vie : plein (0..5), vide (7..12)
cells = Image.new('RGBA', (13, 6), T)
cp = cells.load()
for y in range(6):
    for x in range(6):
        if x in (0, 5) or y in (0, 5):
            c = RED_D if y < 5 else RED_XD
        elif y == 1:
            c = (255, 140, 130, 255)
        elif y == 4:
            c = RED_D
        else:
            c = (226, 52, 58, 255)
        cp[x, y] = c
        cp[7 + x, y] = (70, 22, 28, 255) if x in (0, 5) or y in (0, 5) else (40, 10, 14, 255)
cp[1, 1] = (255, 210, 200, 255)
cells.save(os.path.join(OUT, 'roger_bossbar_cells.png'))

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
    prev.resize((prev.width * 3, prev.height * 3), Image.NEAREST).save(os.path.join(out, 'roger_bossbar_apercu.png'))
print('ok', CELL_X, CELL_END)
