# -*- coding: utf-8 -*-
"""
Race Cyborg : texture du boulet du Cannon Arm (textures/entity/cyborg_cannonball.png, 32 x 32) : fer sombre martelé (coeur et
blocs d'arrondi, en haut), bande rivetée en laiton (en bas). Pixel art, même palette que le bras mécanique.
Usage (depuis la racine du projet) : python3 src/blockbench/cyborg/make_cyborg_textures.py
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity/cyborg_cannonball.png')
im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
IRON, IRON_D, IRON_L = (58, 62, 70, 255), (38, 40, 46, 255), (92, 98, 110, 255)
BRASS, BRASS_D, BRASS_L, RIVET = (180, 136, 58, 255), (120, 88, 36, 255), (226, 186, 104, 255), (240, 222, 170, 255)
for y in range(16):
    for x in range(32):
        k = (x * 7 + y * 13 + (x * y) % 5) % 11          # fer martelé : taches sombres et reflets
        im.putpixel((x, y), IRON_D if k < 2 else IRON_L if k == 7 else IRON)
for y in range(16, 32):
    for x in range(32):
        row = y - 16
        c = BRASS_L if row % 8 == 0 else BRASS_D if row % 8 == 7 else BRASS
        if row % 8 in (3, 4) and x % 4 == 1:
            c = RIVET                                         # rivets
        im.putpixel((x, y), c)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
print('ok')


# ---------------------------------------------------------------- icônes des 4 attaques (16 x 16, contour sombre)
import math
ICON_DIR = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/gui/race/attack')
OUTLINE = (20, 18, 26, 255)


def outlined(ic):
    out = ic.copy()
    for y in range(16):
        for x in range(16):
            if ic.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and ic.getpixel((x + dx, y + dy))[3] > 0
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), OUTLINE)
    return out


def icon_from(rows, pal):
    ic = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                ic.putpixel((x, y), pal[ch])
    return outlined(ic)


PAL = {'s': (150, 162, 178, 255), 'S': (196, 206, 218, 255), 'd': (70, 76, 88, 255), 'b': (196, 150, 64, 255),
       'o': (255, 150, 40, 255), 'y': (255, 230, 90, 255), 'w': (255, 250, 220, 255), 'k': (20, 20, 24, 255),
       'c': (110, 200, 255, 255), 'C': (200, 240, 255, 255), 'B': (40, 120, 230, 255), 'r': (255, 90, 40, 255)}
ICONS = {
    'cyborg_cannon_arm': ['................',
                          '................',
                          '..ss............',
                          '.sSSs...........',
                          '.sSSsbbb........',
                          '.ssssddddddddb..',
                          '..ssdSSSSSSSSbk.',
                          '..sbdSSSSSSSSbk.oy',
                          '..ssdssssssssbk.',
                          '.....dddddddddb.',
                          '.......bb.......',
                          '................',
                          '................',
                          '................',
                          '................',
                          '................'],
    'cyborg_energy_shield': ['................',
                             '.....CCCCCC.....',
                             '...CcBcccBcC....',
                             '..CBcccBcccBC...',
                             '.CcccBcccBcccC..',
                             '.CBcccBcccBccC..',
                             'CcccBcccBcccBcC.',
                             'CcBcccBcccBcccC.',
                             'CcccBcccBcccBcC.',
                             'CBcccBcccBcccBC.',
                             'CcccBcccBcccBcC.',
                             'CCCCCCCCCCCCCCC.',
                             '................',
                             '................',
                             '................',
                             '................'],
    'cyborg_engine_overdrive': ['................',
                                '......bb........',
                                '...b.bbbb.b.....',
                                '..bbbbddbbbb....',
                                '...bbd..dbb.....',
                                '.bbbd.yy.dbbb...',
                                '.bbd..yo..dbb...',
                                '..bd.yoo..db....',
                                '.bbd..ooy.dbb...',
                                '.bbbd..yo.dbb...',
                                '...bbd.yodbb....',
                                '..bbbbddbbbb....',
                                '...b.bbbb.b.....',
                                '......bb........',
                                '................',
                                '................'],
    'cyborg_radical_beam': ['................',
                            '................',
                            '................',
                            '.....oooooooooo.',
                            '..ooyyyyyyyyyyyy',
                            '.oyywwwwwwwwwwww',
                            'oyywwwwwwwwwwwww',
                            'oyywwwwwwwwwwwww',
                            '.oyywwwwwwwwwwww',
                            '..ooyyyyyyyyyyyy',
                            '.....oooooooooo.',
                            '................',
                            '................',
                            '................',
                            '................',
                            '................'],
}
os.makedirs(ICON_DIR, exist_ok=True)
for name, rows in ICONS.items():
    rows = [(r + '................')[:16] for r in rows]
    icon_from(rows, PAL).save(os.path.join(ICON_DIR, name + '.png'))
print('icônes ok')
