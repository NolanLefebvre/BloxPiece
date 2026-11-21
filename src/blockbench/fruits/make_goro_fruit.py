# -*- coding: utf-8 -*-
"""
Goro Goro no Mi (fruit de la foudre d'Ener) : modèle 3D de l'objet (models/item/goro_goro_no_mi.json), ses textures pixel
(textures/item/goro_goro_no_mi_*.png), le fichier Blockbench éditable (goro_goro_no_mi.bbmodel, format « Java Block/Item ») et
des aperçus. Même style que les fruits du mod (fruit rond couvert de spirales, tige recourbée, pixel art), mais en VRAI relief :
  - corps arrondi fait de pavés emboîtés (silhouette ronde sous tous les angles, légèrement aplati en haut avec un petit creux
    autour de la tige), couvert de spirales or et jaune ;
  - 8 spirales en relief (plaques tourbillonnantes à fleur de peau) sur deux rangées décalées, plus une au sommet ;
  - collet sombre au sommet, tige olive qui monte, se coude et s'enroule en vrille, avec une petite feuille.
Palette jaune et dorée (fruit original), contours orangés, reflets clairs.
Usage : python3 src/blockbench/fruits/make_goro_fruit.py [dossier_apercus]
"""
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'main/resources/assets/bloxpiece')
NAME = 'goro_goro_no_mi'
TS = 32


# ---------------------------------------------------------------- textures (32 x 32)
def noise(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


GOLD_D, GOLD, YEL, YEL_L, SHINE = (176, 112, 14), (222, 160, 26), (246, 204, 52), (255, 228, 104), (255, 246, 196)


def swirl_value(x, y, cx, cy, turns=2.2, r0=7.0):
    """Distance au bras de spirale le plus proche (0 = sur le trait) autour de (cx, cy)."""
    dx, dy = x - cx, y - cy
    r = math.hypot(dx, dy)
    if r > r0 or r < 0.3:
        return 9.0
    a = math.atan2(dy, dx)
    k = r / r0 * turns * 2 * math.pi - a
    return abs(((k / (2 * math.pi)) % 1.0) - 0.5) * 2 * math.pi * r / (turns * 2 * math.pi) * 2.0


def spiral(draw, cx, cy, r0, turns, color, width, sign=1):
    """Trace une spirale d'Archimède nette (traits pixel) autour de (cx, cy)."""
    pts = []
    steps = int(turns * 60)
    for i in range(steps + 1):
        t = i / steps
        a = sign * t * turns * 2 * math.pi
        r = t * r0
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    draw.line(pts, fill=color, width=width)


def skin():
    """Peau : jaune doré grenu, grandes spirales brun-or bordées d'un reflet clair (comme les fruits du mod, mais en jaune)."""
    im = Image.new('RGBA', (TS, TS))
    for x in range(TS):
        for y in range(TS):
            r = noise(x, y, 3)
            im.putpixel((x, y), (YEL if r > 0.3 else YEL_L if r > 0.12 else SHINE) + (255,))
    d = ImageDraw.Draw(im)
    for cx, cy, sg in ((8, 8, 1), (24, 9, -1), (9, 24, -1), (24, 24, 1), (16, 16, 1)):
        spiral(d, cx + 0.6, cy + 0.6, 7.0, 1.7, SHINE + (255,), 1, sg)   # reflet, décalé
        spiral(d, cx, cy, 7.0, 1.7, GOLD_D + (255,), 2, sg)
    return im


def knob():
    """Spirale en relief : grosse spirale brun-or sur fond doré, bord orangé."""
    im = Image.new('RGBA', (TS, TS), GOLD + (255,))
    d = ImageDraw.Draw(im)
    d.ellipse((2, 2, 29, 29), fill=YEL + (255,))
    spiral(d, 16.5, 16.5, 12.5, 2.3, SHINE + (255,), 2)
    spiral(d, 16.0, 16.0, 12.5, 2.3, GOLD_D + (255,), 3)
    d.rectangle((0, 0, TS - 1, TS - 1), outline=GOLD + (255,))
    return im


def stem():
    im = Image.new('RGBA', (TS, TS))
    for x in range(TS):
        for y in range(TS):
            r = noise(x, y, 9)
            c = (122, 132, 44) if r > 0.3 else (98, 108, 34)
            if x % 8 in (0, 7):
                c = (78, 86, 26)
            im.putpixel((x, y), c + (255,))
    return im


def collar():
    im = Image.new('RGBA', (TS, TS))
    for x in range(TS):
        for y in range(TS):
            im.putpixel((x, y), ((150, 96, 18) if noise(x, y, 5) > 0.4 else (126, 78, 12)) + (255,))
    return im


TEX = {'skin': skin(), 'knob': knob(), 'stem': stem(), 'collar': collar()}

# ---------------------------------------------------------------- géométrie (repère du modèle : 0..16, centre 8, 8, 8)
E = []


def el(f, t, tx, name, rot=None):
    E.append({'f': [round(v, 3) for v in f], 't': [round(v, 3) for v in t], 'tx': tx, 'name': name, 'rot': rot})


C = 8.0
# corps rond : tranches octogonales empilées (deux pavés croisés par tranche, rayon d'une sphère à chaque hauteur), un peu
# aplati en haut ; rond sous tous les angles, sans coin qui dépasse
R, CY = 5.6, 7.3
CUTS = [1.8, 2.6, 3.6, 4.9, 6.4, 8.2, 9.7, 10.9, 11.9, 12.8, 13.4]
for y0, y1 in zip(CUTS, CUTS[1:]):
    ym = (y0 + y1) / 2
    r = math.sqrt(max(0.8, R * R - ((ym - CY) * (1.12 if ym > CY else 1.0)) ** 2))
    k = r * 0.66
    el((C - r, y0, C - k), (C + r, y1, C + k), 'skin', 'corps : tranche')
    el((C - k, y0, C - r), (C + k, y1, C + r), 'skin', 'corps : tranche')
# spirales en relief : plaques tourbillonnantes posées à fleur de peau (épaisseur 0,9), 8 directions sur deux rangées décalées
for row, (yc, angles) in enumerate(((5.0, (0, 90, 180, 270)), (8.6, (45, 135, 225, 315)))):
    for ang in angles:
        a = math.radians(ang)
        rb = math.sqrt(R * R - ((yc - CY) * (1.12 if yc > CY else 1.0)) ** 2)   # rayon du corps à cette hauteur
        r = (rb if ang % 90 == 0 else rb * 1.17) + 0.25                          # posée sur la peau (octogone : x1,17 en diagonale)
        cx, cz = C + math.cos(a) * r, C + math.sin(a) * r
        w, h, t = 1.45, 1.35, 0.45                     # demi-largeur, demi-hauteur, demi-épaisseur
        if ang % 90 == 0:
            if ang in (0, 180):
                f, to = (cx - t, yc - h, cz - w), (cx + t, yc + h, cz + w)
            else:
                f, to = (cx - w, yc - h, cz - t), (cx + w, yc + h, cz + t)
            el(f, to, 'knob', f'spirale {row + 1} ({ang} deg)')
        else:
            el((cx - w, yc - h, cz - t), (cx + w, yc + h, cz + t), 'knob', f'spirale {row + 1} ({ang} deg)',
               rot={'axis': 'y', 'angle': 45.0 if ang in (45, 225) else -45.0})
el((6.3, 12.7, 6.3), (9.7, 13.5, 9.7), 'knob', 'spirale du sommet')
# collet et tige : monte, se coude, s'enroule en vrille ; petite feuille
el((6.9, 13.4, 6.9), (9.1, 14.2, 9.1), 'collar', 'collet')
el((7.45, 13.9, 7.45), (8.55, 16.6, 8.55), 'stem', 'tige')
el((7.9, 15.9, 7.45), (9.0, 18.0, 8.55), 'stem', 'tige : coude', rot={'axis': 'z', 'angle': -22.5, 'origin': [8.0, 16.0, 8.0]})
el((8.6, 17.6, 7.5), (11.4, 18.5, 8.5), 'stem', 'vrille : haut')
el((10.6, 16.0, 7.5), (11.5, 18.3, 8.5), 'stem', 'vrille : descente')
el((9.4, 15.6, 7.5), (11.2, 16.4, 8.5), 'stem', 'vrille : boucle')
el((9.3, 16.1, 7.5), (10.1, 17.2, 8.5), 'stem', 'vrille : bout')
el((6.0, 14.6, 7.6), (7.6, 15.2, 9.6), 'stem', 'feuille', rot={'axis': 'x', 'angle': 22.5, 'origin': [7.0, 14.8, 8.0]})


# ---------------------------------------------------------------- modèle
def faces(e):
    f, t = e['f'], e['t']
    if e['tx'] == 'knob':
        return {k: {'uv': [0, 0, 16, 16], 'texture': '#knob'} for k in ('north', 'south', 'east', 'west', 'up', 'down')}
    # une seule projection pour tout le corps : la peau garde ses spirales continues d'un pavé à l'autre
    U = lambda v: round(min(16, max(0, v)), 3)
    fc = {'north': [U(16 - t[0]), U(16 - t[1]), U(16 - f[0]), U(16 - f[1])], 'south': [U(f[0]), U(16 - t[1]), U(t[0]), U(16 - f[1])],
          'east': [U(16 - t[2]), U(16 - t[1]), U(16 - f[2]), U(16 - f[1])], 'west': [U(f[2]), U(16 - t[1]), U(t[2]), U(16 - f[1])],
          'up': [U(f[0]), U(f[2]), U(t[0]), U(t[2])], 'down': [U(f[0]), U(16 - t[2]), U(t[0]), U(16 - f[2])]}
    if e['tx'] in ('stem', 'collar'):
        fc = {k: [0, 0, 4, 4] for k in fc}
    return {k: {'uv': v, 'texture': '#' + e['tx']} for k, v in fc.items()}


model = {
    '__comment': 'Goro Goro no Mi (modèle 3D) : généré par src/blockbench/fruits/make_goro_fruit.py',
    'textures': dict({k: f'bloxpiece:item/{NAME}_{k}' for k in TEX}, particle=f'bloxpiece:item/{NAME}_skin'),
    'display': {
        'gui': {'rotation': [20, -30, 0], 'translation': [0, -1.25, 0], 'scale': [0.78, 0.78, 0.78]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.45, 0.45, 0.45]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, -1, 0], 'scale': [0.75, 0.75, 0.75]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 6, 0], 'scale': [0.9, 0.9, 0.9]},
        'thirdperson_righthand': {'rotation': [0, -30, 0], 'translation': [0, 2.5, 1.5], 'scale': [0.42, 0.42, 0.42]},
        'thirdperson_lefthand': {'rotation': [0, 30, 0], 'translation': [0, 2.5, 1.5], 'scale': [0.42, 0.42, 0.42]},
        'firstperson_righthand': {'rotation': [0, -40, 0], 'translation': [1.5, 3.0, 0.5], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 40, 0], 'translation': [1.5, 3.0, 0.5], 'scale': [0.5, 0.5, 0.5]},
    },
    'elements': [],
}
for e in E:
    out = {'__comment': e['name'], 'from': e['f'], 'to': e['t'], 'faces': faces(e)}
    if e['rot']:
        r = e['rot']
        center = r.get('origin', [round((e['f'][0] + e['t'][0]) / 2, 3), round((e['f'][1] + e['t'][1]) / 2, 3),
                                  round((e['f'][2] + e['t'][2]) / 2, 3)])
        out['rotation'] = {'angle': r['angle'], 'axis': r['axis'], 'origin': center}
    model['elements'].append(out)
assert all(-45 <= el_.get('rotation', {'angle': 0})['angle'] <= 45 for el_ in model['elements'])

for k, im in TEX.items():
    im.save(os.path.join(ASSETS, 'textures/item', f'{NAME}_{k}.png'))
with open(os.path.join(ASSETS, 'models/item', NAME + '.json'), 'w') as fh:
    json.dump(model, fh, indent=2, ensure_ascii=False)

# fichier Blockbench éditable
keys = list(TEX)
elements = []
for e in model['elements']:
    fcs = {k: {'uv': v['uv'], 'texture': keys.index(v['texture'][1:])} for k, v in e['faces'].items()}
    r = e.get('rotation')
    rot = [0, 0, 0]
    if r:
        rot['xyz'.index(r['axis'])] = r['angle']
    elements.append({'name': e['__comment'], 'from': e['from'], 'to': e['to'], 'autouv': 0, 'color': 0,
                     'origin': r['origin'] if r else [8, 8, 8], 'rotation': rot, 'faces': fcs, 'type': 'cube', 'uuid': str(uuid.uuid4())})
textures = []
for i, k in enumerate(keys):
    buf = io.BytesIO(); TEX[k].save(buf, 'PNG')
    textures.append({'name': f'{NAME}_{k}.png', 'id': str(i), 'width': TS, 'height': TS, 'uv_width': 16, 'uv_height': 16,
                     'namespace': 'bloxpiece', 'folder': 'item', 'particle': i == 0, 'render_mode': 'default', 'visible': True,
                     'saved': True, 'uuid': str(uuid.uuid4()), 'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()})
with open(os.path.join(HERE, NAME + '.bbmodel'), 'w') as fh:
    json.dump({'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False}, 'name': NAME, 'parent': '',
               'ambientocclusion': True, 'front_gui_light': False, 'visible_box': [1, 1, 0], 'resolution': {'width': 16, 'height': 16},
               'elements': elements, 'outliner': [e['uuid'] for e in elements], 'textures': textures, 'display': model['display']},
              fh, indent=1, ensure_ascii=False)


# ---------------------------------------------------------------- aperçus texturés (rendu logiciel)
CORNERS = {'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)], 'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
           'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)], 'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
           'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], 'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)]}
SHADE = {'north': 0.82, 'south': 0.82, 'east': 0.65, 'west': 0.65, 'up': 1.0, 'down': 0.5}


def rotate(p, rot):
    if not rot:
        return p
    o = rot['origin']
    a = math.radians(rot['angle'])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if rot['axis'] == 'y':
        x, z = x * c + z * s, -x * s + z * c
    elif rot['axis'] == 'x':
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def render(path, yaw, pitch, size=520, sc=26):
    im = Image.new('RGB', (size, size), (40, 40, 46))
    d = ImageDraw.Draw(im)
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def P(p):
        x, y, z = p[0] - 8, p[1] - 9, p[2] - 8
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (size / 2 + x * sc, size / 2 - y * sc, z)
    polys = []
    for e in model['elements']:
        F, T_ = e['from'], e['to']
        rot = e.get('rotation')
        for fn, info in e['faces'].items():
            u0, v0, u1, v1 = info['uv']
            tex = TEX[info['texture'][1:]]
            cs = [tuple((F, T_)[c[i]][i] for i in range(3)) for c in CORNERS[fn]]
            n = 8
            for i in range(n):
                for j in range(n):
                    def lerp(a, b):
                        top = [cs[0][k] + (cs[1][k] - cs[0][k]) * a for k in range(3)]
                        bot = [cs[3][k] + (cs[2][k] - cs[3][k]) * a for k in range(3)]
                        return rotate([top[k] + (bot[k] - top[k]) * b for k in range(3)], rot)
                    q = [P(lerp(i / n, j / n)), P(lerp((i + 1) / n, j / n)), P(lerp((i + 1) / n, (j + 1) / n)), P(lerp(i / n, (j + 1) / n))]
                    tu = (u0 + (u1 - u0) * (i + 0.5) / n) / 16 * TS
                    tv = (v0 + (v1 - v0) * (j + 0.5) / n) / 16 * TS
                    c = tex.getpixel((min(TS - 1, max(0, int(tu))), min(TS - 1, max(0, int(tv)))))
                    polys.append((sum(p[2] for p in q) / 4, [(p[0], p[1]) for p in q], tuple(int(v * SHADE[fn]) for v in c[:3])))
    for _, pts, c in sorted(polys, key=lambda p: p[0]):
        d.polygon(pts, fill=c)
    im.save(path)


if len(sys.argv) > 1:
    out = sys.argv[1]
    render(os.path.join(out, 'goro_fruit_face.png'), 0, 0)
    render(os.path.join(out, 'goro_fruit_34.png'), 35, 22)
    render(os.path.join(out, 'goro_fruit_dessus.png'), 20, 55)
print('ok :', len(model['elements']), 'éléments')
