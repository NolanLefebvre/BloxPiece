# -*- coding: utf-8 -*-
"""
Système de races : génère les icônes des 5 races (textures/gui/race/<id>.png, 16 x 16) et la texture du Race Reroll Token
(textures/item/race_reroll_token.png, 32 x 32), en pixel art, avec un contour sombre comme les autres objets du mod.
Usage (depuis la racine du projet) : python3 src/blockbench/ui/make_race_icons.py [dossier_apercus]
"""
import math, os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
GUI = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/gui/race')
ITEM = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/item/race_reroll_token.png')
T = (0, 0, 0, 0)


def from_map(rows, pal):
    im = Image.new('RGBA', (len(rows[0]), len(rows)), T)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), pal[ch])
    return im


def outline(im, color=(24, 18, 22, 255)):
    """Contour sombre d'un pixel autour de la forme (comme les autres objets du mod)."""
    out = im.copy()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if im.getpixel((x, y))[3] == 0 and any(0 <= x + dx < w and 0 <= y + dy < h and im.getpixel((x + dx, y + dy))[3] > 0
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), color)
    return out


ICONS = {
    # Humain : visage, cheveux bruns, bandeau rouge
    'human': (['................',
               '................',
               '.....hhhhhh.....',
               '....hhhhhhhh....',
               '...hhhhhhhhhh...',
               '...rrrrrrrrrr...',
               '...ssssssssss...',
               '...sseessees s..'.replace(' ', 's'),
               '...sseessees s..'.replace(' ', 's'),
               '...ssssddssss...',
               '...ssssssssss...',
               '....ssmmmmss....',
               '.....ssssss.....',
               '......SSSS......',
               '................',
               '................'],
              {'h': (92, 58, 34, 255), 'r': (196, 44, 44, 255), 's': (236, 190, 150, 255), 'S': (200, 150, 112, 255),
               'e': (40, 30, 30, 255), 'd': (210, 160, 120, 255), 'm': (150, 70, 60, 255)}),
    # Fishman : tête de poisson bleue, nageoire, ouïes
    'fishman': (['................',
                 '.......ff.......',
                 '......fFFf......',
                 '.....bbbbbb.....',
                 '....bbbbbbbb....',
                 '...bbbbbbbbbb...',
                 '...bwkbbbbkwb...',
                 '...bbbbbbbbbb...',
                 '..gbbbbbbbbbbg..',
                 '..gbbllllllbbg..',
                 '...bblWWWWlbb...',
                 '...bbllllllbb...',
                 '....bbbbbbbb....',
                 '.....bbbbbb.....',
                 '................',
                 '................'],
                {'b': (64, 140, 210, 255), 'f': (40, 96, 170, 255), 'F': (90, 170, 230, 255), 'w': (240, 240, 240, 255),
                 'k': (20, 20, 40, 255), 'g': (30, 80, 150, 255), 'l': (150, 200, 240, 255), 'W': (250, 250, 250, 255)}),
    # Lunarian : aile noire et flamme dans le dos
    'lunarian': (['................',
                  '.........oo.....',
                  '........oyyo....',
                  '.......oyyyo....',
                  '..kk...oyyo.....',
                  '.kkkk...oo......',
                  'kkkkkk..........',
                  'kkkkkkk..wwww...',
                  '.kkkkkk.wwwwww..',
                  '..kkkkkkwsssw...',
                  '...kkkkkwsesw...',
                  '....kkkkwsssw...',
                  '.....kkkKKKK....',
                  '......kkKKKK....',
                  '.......KKKKKK...',
                  '................'],
                 {'k': (30, 26, 36, 255), 'K': (70, 60, 80, 255), 'o': (240, 110, 30, 255), 'y': (255, 210, 80, 255),
                  'w': (240, 240, 246, 255), 's': (150, 100, 80, 255), 'e': (30, 20, 20, 255)}),
    # Cyborg : tête de métal, visière bleue, rivets
    'cyborg': (['................',
                '.....mmmmmm.....',
                '....mMMMMMMm....',
                '...mMMMMMMMMm...',
                '...mMrMMMMrMm...',
                '...mMMMMMMMMm...',
                '...mvvvvvvvvm...',
                '...mVVvvvvVVm...',
                '...mMMMMMMMMm...',
                '...mMMssssMMm...',
                '...mMsMMMMsMm...',
                '....mMMMMMMm....',
                '.....mmmmmm.....',
                '......dddd......',
                '.....dddddd.....',
                '................'],
               {'m': (90, 100, 116, 255), 'M': (170, 182, 196, 255), 'r': (110, 116, 126, 255), 'v': (60, 170, 255, 255),
                'V': (170, 230, 255, 255), 's': (110, 120, 134, 255), 'd': (70, 76, 88, 255)}),
    # Minks : tête d'animal à oreilles, éclair
    'minks': (['................',
               '..aa........aa..',
               '..aAa......aAa..',
               '..aAAa....aAAa..',
               '...aaaaaaaaaa...',
               '...aaaaaaaaaa...',
               '..aaeaaaaaaeaa..',
               '..aaeaaaaaaeaa..',
               '..aaaaacnaaaaa..',
               '..aaaawwwwaaaa..',
               '...aawwttwwaa...',
               '....aawwwwaa..l.',
               '.....aaaaaa..ll.',
               '............lll.',
               '.............l..',
               '................'],
              {'a': (232, 196, 90, 255), 'A': (250, 170, 170, 255), 'e': (30, 24, 20, 255), 'c': (90, 50, 40, 255),
               'n': (90, 50, 40, 255), 'w': (250, 240, 220, 255), 't': (200, 120, 110, 255), 'l': (120, 200, 255, 255)}),
}


def token():
    """Race Reroll Token : jeton d'or, face bleu nuit, deux flèches qui tournent (le reroll), étoile au centre."""
    n = 32
    im = Image.new('RGBA', (n, n), T)
    c = (n - 1) / 2.0
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - c, y - c)
            if r <= 14.2:
                if r > 12.2:
                    shade = 0.5 + 0.5 * ((x - y) / (2 * n) + 0.5)
                    col = (int(200 + 50 * shade), int(150 + 60 * shade), int(40 + 30 * shade), 255)   # tranche d'or
                    if int(math.degrees(math.atan2(y - c, x - c)) / 15) % 2 == 0:
                        col = tuple(max(0, v - 30) if i < 3 else v for i, v in enumerate(col))       # crénelure
                elif r > 11.2:
                    col = (150, 104, 30, 255)
                else:
                    t = (y / n)
                    col = (int(28 + 20 * (1 - t)), int(30 + 30 * (1 - t)), int(80 + 60 * (1 - t)), 255)   # face bleu nuit
                im.putpixel((x, y), col)
    # deux flèches courbes (cercle de rayon 7) avec pointes
    for i in range(0, 360, 3):
        a = math.radians(i)
        if 30 < i % 180 < 165:
            x, y = c + 7.2 * math.cos(a), c + 7.2 * math.sin(a)
            for dx in (0, 1):
                im.putpixel((int(round(x)) , int(round(y))), (240, 236, 220, 255))
    for end in (165, 345):
        a = math.radians(end)
        x, y = c + 7.2 * math.cos(a), c + 7.2 * math.sin(a)
        tx, ty = -math.sin(a), math.cos(a)                                   # sens de rotation
        for k in range(0, 4):
            for s in range(-k, k + 1):
                px = x + tx * (3 - k) + math.cos(a) * s * 0.7
                py = y + ty * (3 - k) + math.sin(a) * s * 0.7
                im.putpixel((int(round(px)), int(round(py))), (255, 214, 90, 255))
    # petite étoile dorée au centre
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (0, 2), (0, -2), (2, 0), (-2, 0)):
        im.putpixel((int(c + 0.5) + dx, int(c + 0.5) + dy), (255, 226, 110, 255))
    im.putpixel((int(c + 0.5), int(c + 0.5)), (255, 250, 220, 255))
    return outline(im)


os.makedirs(GUI, exist_ok=True)
for rid, (rows, pal) in ICONS.items():
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rid
    outline(from_map(rows, pal)).save(os.path.join(GUI, rid + '.png'))
token().save(ITEM)
if len(sys.argv) > 1:
    sheet = Image.new('RGBA', (16 * 8 * 5 + 32 * 8 + 60, 32 * 8), (60, 62, 70, 255))
    for i, rid in enumerate(ICONS):
        sheet.alpha_composite(Image.open(os.path.join(GUI, rid + '.png')).resize((128, 128), Image.NEAREST), (i * 136, 64))
    sheet.alpha_composite(Image.open(ITEM).resize((256, 256), Image.NEAREST), (5 * 136 + 10, 0))
    sheet.save(os.path.join(sys.argv[1], 'races.png'))
print('ok')
