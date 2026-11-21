# -*- coding: utf-8 -*-
# Bandits (camp de bandits) : génère le modèle Java (BanditModels.java : bandit, chef des bandits), les textures
# (bandit_0..3.png, bandit_chief_0.png), les fichiers Blockbench (bandit.bbmodel, bandit_chief.bbmodel) et des aperçus.
# Même méthode et même style que les pirates du mod : corps humanoïde classique (peau en haut à gauche de la texture 128x128,
# disposition 64x32, membres gauches en miroir) + équipement 3D en relief accroché aux membres ; tissu granuleux, tons sourds.
# Usage (depuis la racine du projet) : python3 src/blockbench/make_bandits.py [dossier_apercus]
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = os.path.join(ROOT, 'src/client/java/nolan/bloxpiece/client/entity/BanditModels.java')
TEX_DIR = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity')
BB_DIR = os.path.join(ROOT, 'src/blockbench')
TW = TH = 128
T = (0, 0, 0, 0)
PARTS, P, img, px = [], {}, None, None
BB_NAME = BB_CLASS = BB_TEX = ''


class Part:
    """Une partie du modèle (= un ModelPart Java = un groupe Blockbench) avec un cube."""
    def __init__(self, name, parent, box, pivot=(0, 0, 0), rot=(0, 0, 0), inflate=0.0, uv=None, mirror=False, base=False):
        self.name, self.parent, self.box, self.pivot, self.rot = name, parent, box, pivot, rot
        self.inflate, self.uv, self.mirror, self.base = inflate, uv, mirror, base


def part(*a, **k):
    p = Part(*a, **k)
    PARTS.append(p)
    return p


# pack
def uv_size(p):
    w, h, d = [math.ceil(v) for v in p.box[3:6]]
    return 2 * d + 2 * w, d + h


def pack():
    """Rangement en étagères, les plus hautes d'abord : à droite de la peau (x 64-127, y 0-31), puis sous la peau."""
    todo = sorted((p for p in PARTS if p.uv is None), key=lambda p: -uv_size(p)[1])
    for x0, y0, x1, y1 in ((64, 0, TW, 32), (0, 32, TW, TH)):
        x, y, row_h, rest = x0, y0, 0, []
        for p in todo:
            uw, uh = uv_size(p)
            if x + uw > x1:
                x, y, row_h = x0, y + row_h + 1, 0
            if y + uh > y1:
                rest.append(p)
                continue
            p.uv = (x, y)
            x += uw + 1
            row_h = max(row_h, uh)
        todo = rest
    assert not todo, 'texture trop petite : ' + ', '.join(p.name for p in todo)



# paint
def faces(p):
    """Rectangles (u, v, largeur, hauteur) de chaque face, disposition de cube Minecraft."""
    u, v = p.uv
    w, h, d = [math.ceil(q) for q in p.box[3:6]]
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


def fill(p, color, which=None):
    for f, (u, v, w, h) in faces(p).items():
        if which is None or f in which:
            for i in range(w):
                for j in range(h):
                    px[u + i, v + j] = color


def paint(p, f, fn):
    """fn(i, j, w, h) -> couleur (ou None pour laisser) pour chaque texel de la face f."""
    u, v, w, h = faces(p)[f]
    for i in range(w):
        for j in range(h):
            c = fn(i, j, w, h)
            if c is not None:
                px[u + i, v + j] = c



# bb
def abs_pivot(p):
    x, y, z = 0.0, 0.0, 0.0
    q = p
    while q is not None:
        x, y, z = x + q.pivot[0], y + q.pivot[1], z + q.pivot[2]
        q = P[q.parent] if q.parent else None
    return x, y, z


def bbmodel():
    buf = io.BytesIO(); img.save(buf, 'PNG')
    elements, groups = [], {}
    for p in PARTS:
        ax, ay, az = abs_pivot(p)
        x, y, z, w, h, d = p.box
        e = {'name': p.name, 'box_uv': True, 'rescale': False, 'locked': False, 'render_order': 'default', 'allow_mirror_modeling': True,
             'from': [-(ax + x + w), 24 - (ay + y + h), az + z], 'to': [-(ax + x), 24 - (ay + y), az + z + d],
             'autouv': 0, 'color': 0, 'inflate': p.inflate, 'origin': [-ax, 24 - ay, az], 'uv_offset': list(p.uv),
             'mirror_uv': p.mirror, 'faces': {f: {'uv': [0, 0, 0, 0], 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')},
             'type': 'cube', 'uuid': str(uuid.uuid4())}
        elements.append(e)
        groups[p.name] = {'name': p.name, 'origin': [-ax, 24 - ay, az],
                          'rotation': [-math.degrees(p.rot[0]), -math.degrees(p.rot[1]), math.degrees(p.rot[2])],
                          'uuid': str(uuid.uuid4()), 'export': True, 'isOpen': False, 'visibility': True, 'autouv': 0,
                          'children': [e['uuid']]}
    roots = []
    for p in PARTS:
        g = groups[p.name]
        (groups[p.parent]['children'] if p.parent else roots).append(g)
    return {'meta': {'format_version': '4.10', 'model_format': 'modded_entity', 'box_uv': True},
            'name': BB_NAME, 'model_identifier': BB_NAME, 'modded_entity_entity_class': BB_CLASS,
            'modded_entity_version': 'Mojmaps', 'visible_box': [1, 2, 0], 'resolution': {'width': TW, 'height': TH},
            'elements': elements, 'outliner': roots,
            'textures': [{'name': BB_TEX, 'id': '0', 'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH,
                          'particle': True, 'render_mode': 'default', 'visible': True, 'saved': True, 'uuid': str(uuid.uuid4()),
                          'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}]}



# render
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rot_zyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(mat_mul(Z, Y), X)


def world_tf(p):
    """(matrice, translation) de la partie : comme ModelPart.translateAndRotate, de la racine vers la partie."""
    chain = []
    q = p
    while q is not None:
        chain.append(q)
        q = P[q.parent] if q.parent else None
    M, t = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0]
    for q in reversed(chain):
        off = [sum(M[i][k] * q.pivot[k] for k in range(3)) for i in range(3)]
        t = [t[i] + off[i] for i in range(3)]
        M = mat_mul(M, rot_zyx(*q.rot))
    return M, t


def quads(p):
    M, t = world_tf(p)
    x, y, z, w, h, d = p.box
    g = p.inflate
    x0, y0, z0, x1, y1, z1 = x - g, y - g, z - g, x + w + g, y + h + g, z + d + g
    W_, H_, D_ = [math.ceil(q) for q in (w, h, d)]
    fs = faces(p)

    def pt(a, b, c):
        return [M[i][0] * a + M[i][1] * b + M[i][2] * c + t[i] for i in range(3)]
    spec = {  # face -> (fonction (s, r) -> point local, nb colonnes, nb lignes) ; s gauche->droite, r haut->bas de la texture
        'front': (lambda s, r: (x0 + (x1 - x0) * s, y0 + (y1 - y0) * r, z0), W_, H_),
        'back': (lambda s, r: (x1 - (x1 - x0) * s, y0 + (y1 - y0) * r, z1), W_, H_),
        'right': (lambda s, r: (x0, y0 + (y1 - y0) * r, z1 - (z1 - z0) * s), D_, H_),
        'left': (lambda s, r: (x1, y0 + (y1 - y0) * r, z0 + (z1 - z0) * s), D_, H_),
        'up': (lambda s, r: (x0 + (x1 - x0) * s, y0, z1 - (z1 - z0) * r), W_, D_),
        'down': (lambda s, r: (x0 + (x1 - x0) * s, y1, z0 + (z1 - z0) * r), W_, D_),
    }
    for f, (fn, nc, nr) in spec.items():
        u, v, fw, fh = fs[f if not p.mirror or f not in ('right', 'left') else ('left' if f == 'right' else 'right')]
        for i in range(nc):
            for j in range(nr):
                ti = (nc - 1 - i) if p.mirror else i
                c = px[u + ti, v + j]
                if c[3] < 128:
                    continue
                corners = [pt(*fn(i / nc, j / nr)), pt(*fn((i + 1) / nc, j / nr)), pt(*fn((i + 1) / nc, (j + 1) / nr)), pt(*fn(i / nc, (j + 1) / nr))]
                yield corners, c, f


def render(path, yaw, scale=14, size=(520, 700)):
    im = Image.new('RGB', size, (150, 190, 230))
    dr = ImageDraw.Draw(im)
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    polys = []
    shade = {'front': 1.0, 'back': 0.7, 'right': 0.82, 'left': 0.82, 'up': 1.08, 'down': 0.6}
    for p in PARTS:
        for corners, c, f in quads(p):
            pts, depth = [], 0
            for X, Y, Z in corners:
                rx = X * cy_ - Z * sy_
                rz = X * sy_ + Z * cy_
                pts.append((size[0] / 2 + rx * scale, 60 + (Y + 12) * scale))
                depth += rz
            polys.append((depth / 4, pts, tuple(min(255, int(k * shade[f])) for k in c[:3])))
    for _, pts, c in sorted(polys, key=lambda q: -q[0]):
        dr.polygon(pts, fill=c)
    im.save(path)




# =====================================================================================================================
# palettes (tons sourds, comme les pirates du mod) et bruit de tissu
SKINS = [((226, 176, 136), (192, 140, 104)), ((198, 146, 104), (164, 112, 76)), ((160, 108, 72), (126, 82, 52)),
         ((236, 196, 160), (204, 160, 124))]
HAIRS = [(40, 30, 24), (92, 60, 34), (24, 22, 26), (120, 84, 44)]
HOODS = [(110, 82, 56), (86, 92, 58), (96, 92, 88), (112, 46, 40)]          # brun, olive, gris, rouge sombre
MASKS = [(70, 40, 36), (52, 50, 56), (120, 104, 80), (40, 42, 50)]
LEATHER, LEATHER_D = (104, 68, 40), (74, 46, 26)
TUNIC = (70, 64, 60)
PANTS = (66, 54, 44)
WRAP = (190, 178, 150)
IRON, BRASS = (150, 150, 156), (200, 160, 70)
EYE, PUPIL = (236, 234, 228), (34, 28, 26)
FUR, FUR_D, FUR_L = (122, 104, 84), (88, 74, 60), (160, 142, 118)
BLACK, BLACK_L = (30, 28, 30), (58, 54, 58)
CAPE, CAPE_D = (110, 30, 30), (78, 20, 22)
GOLD = (220, 176, 70)
SCAR = (160, 90, 80)
T4 = (0, 0, 0, 0)


def noisy(c, i, j, seed, amp=8):
    """Grain de tissu (comme les textures des pirates) : petite variation déterministe par texel."""
    h = ((i * 73856093) ^ (j * 19349663) ^ (seed * 83492791)) & 0xFFFF
    d = (h % (2 * amp + 1)) - amp
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def cloth(p, color, seed, faces_=None, edge=None):
    """Remplit une pièce d'un tissu granuleux ; bord inférieur plus sombre (edge)."""
    for f in ('front', 'back', 'right', 'left', 'up', 'down'):
        if faces_ is not None and f not in faces_:
            continue
        paint(p, f, lambda i, j, w, h, f=f: noisy(shade(color, 0.82) if (edge and j == h - 1 and f not in ('up', 'down'))
                                                   else shade(color, 0.92) if f == 'down' else color, i, j, seed + hash(f) % 97))


# =====================================================================================================================
def new_model():
    global PARTS, P
    PARTS = []
    part('head', None, (-4, -8, -4, 8, 8, 8), uv=(0, 0), base=True)
    part('hat', 'head', (-4, -8, -4, 8, 8, 8), inflate=0.5, uv=(32, 0), base=True)
    part('body', None, (-4, 0, -2, 8, 12, 4), uv=(16, 16), base=True)
    part('right_arm', None, (-3, -2, -2, 4, 12, 4), pivot=(-5, 2, 0), uv=(40, 16), base=True)
    part('left_arm', None, (-1, -2, -2, 4, 12, 4), pivot=(5, 2, 0), uv=(40, 16), mirror=True, base=True)
    part('right_leg', None, (-2, 0, -2, 4, 12, 4), pivot=(-1.9, 12, 0), uv=(0, 16), base=True)
    part('left_leg', None, (-2, 0, -2, 4, 12, 4), pivot=(1.9, 12, 0), uv=(0, 16), mirror=True, base=True)


def common_gear():
    """Équipement commun aux bandits et au chef : ceinture à boucle, brassards, bandes molletières, fourreau d'épée."""
    part('belt', 'body', (-4, 9, -2, 8, 2, 4), inflate=0.42)
    part('buckle', 'body', (-1, 9, -2.95, 2, 2, 1), inflate=0.12)
    part('bracer_right', 'right_arm', (-3, 6, -2, 4, 4, 4), inflate=0.35)
    part('bracer_left', 'left_arm', (-1, 6, -2, 4, 4, 4), inflate=0.35)
    part('wrap_right', 'right_leg', (-2, 6, -2, 4, 5, 4), inflate=0.38)
    part('wrap_left', 'left_leg', (-2, 6, -2, 4, 5, 4), inflate=0.38)
    part('scabbard', 'body', (-0.5, -2, -1, 1, 11, 2), pivot=(4.5, 9.5, 0.4), rot=(1.0, 0, -0.22))
    part('scabbard_chape', 'scabbard', (-0.5, 8, -1, 1, 1, 2), inflate=0.15)


def paint_body(skin, hair, tunic, pants, seed, chest_open=False):
    """Peau, visage, tunique, pantalon, chaussures (disposition 64x32 des pirates)."""
    sk, skd = skin
    fill(P['head'], sk + (255,))
    paint(P['head'], 'up', lambda i, j, w, h: noisy(hair, i, j, seed))
    for f in ('right', 'left', 'back'):
        paint(P['head'], f, lambda i, j, w, h: noisy(hair, i, j, seed) if j < 3 or f == 'back' and j < 6 else noisy(sk, i, j, seed, 4))
    paint(P['hat'], 'up', lambda i, j, w, h: T4)
    for f in ('front', 'back', 'right', 'left', 'down', 'up'):
        paint(P['hat'], f, lambda i, j, w, h: T4)
    body = P['body']
    for f in ('front', 'back', 'right', 'left'):
        paint(body, f, lambda i, j, w, h, f=f: noisy(pants, i, j, seed) if j >= 10 else
              (noisy(skd if j < 6 and 3 <= i <= 4 else sk, i, j, seed, 4) if (chest_open and f == 'front' and j < 7 and 2 <= i <= 5)
               else noisy(tunic, i, j, seed)))
    fill(body, sk + (255,), ('up',))
    arm = P['right_arm']
    for f in ('front', 'back', 'right', 'left'):
        paint(arm, f, lambda i, j, w, h: noisy(sk, i, j, seed, 4) if j >= 9 else noisy(tunic, i, j, seed))
    fill(arm, tunic + (255,), ('up',))
    fill(arm, skd + (255,), ('down',))
    leg = P['right_leg']
    for f in ('front', 'back', 'right', 'left'):
        paint(leg, f, lambda i, j, w, h: noisy(LEATHER_D, i, j, seed) if j >= 10 else noisy(pants, i, j, seed))
    fill(leg, pants + (255,), ('up',))
    fill(leg, LEATHER_D + (255,), ('down',))


def face(rows, cmap):
    paint(P['head'], 'front', lambda i, j, w, h: noisy(cmap[rows[j][i]], i, j, 5, 3) if rows[j][i] != '.' else None)


def paint_common(seed):
    cloth(P['belt'], LEATHER, seed, edge=True)
    fill(P['buckle'], BRASS + (255,))
    for n in ('bracer_right', 'bracer_left'):
        cloth(P[n], LEATHER, seed + 3)
        fill(P[n], T4, ('up', 'down'))
    for n in ('wrap_right', 'wrap_left'):
        for f in ('front', 'back', 'right', 'left'):
            paint(P[n], f, lambda i, j, w, h: noisy(shade(WRAP, 0.8) if (i + j) % 3 == 0 else WRAP, i, j, seed))   # bandes croisées
        fill(P[n], T4, ('up', 'down'))
    cloth(P['scabbard'], LEATHER_D, seed + 5)
    fill(P['scabbard_chape'], IRON + (255,))


# =====================================================================================================================
def bandit_model():
    """Bandit : capuche ample, foulard sur le bas du visage, gilet de cuir ouvert, bandoulière, épaulière cloutée."""
    new_model()
    part('hood', 'head', (-4, -8, -4, 8, 8, 8), inflate=0.6)
    part('hood_drape', 'head', (-3.5, 0, 0, 7, 3, 2), pivot=(0, -0.5, 2.6), rot=(0.35, 0, 0))
    part('mask', 'head', (-4, -4, -0.5, 8, 4, 1), pivot=(0, 0, -4.1), inflate=0.12)
    part('mask_knot', 'head', (-1, -1, 0, 2, 2, 1), pivot=(0, -2.5, 4.5))
    part('vest', 'body', (-4, 0, -2, 8, 10, 4), inflate=0.3)
    part('strap', 'body', (-1, -6.5, -2, 2, 13, 4), pivot=(0, 6, 0), rot=(0, 0, -0.588), inflate=0.45)
    part('pauldron', 'right_arm', (-4, -2.6, -2.5, 5, 3, 5), inflate=0.1)
    common_gear()


def paint_bandit(v):
    seed = 11 + v * 7
    skin, hair, hood, mask = SKINS[v], HAIRS[v], HOODS[v], MASKS[v]
    paint_body(skin, hair, TUNIC, PANTS, seed)
    sk, skd = skin
    face(['HHHHHHHH',
          'HssssssH',
          'sbbssbbs',
          'swpsspws',
          'mmmmmmmm',
          'mmmmmmmm',
          'mmmmmmmm',
          'mmmmmmmm'], {'H': hair, 's': sk, 'b': hair, 'w': EYE, 'p': PUPIL, 'm': mask})
    hp = P['hood']
    cloth(hp, hood, seed, edge=True)
    paint(hp, 'front', lambda i, j, w, h: noisy(hood, i, j, seed) if j < 2 or i in (0, w - 1) else T4)   # ouverture du visage
    fill(hp, T4, ('down',))
    cloth(P['hood_drape'], hood, seed + 1, edge=True)
    cloth(P['mask'], mask, seed + 2)
    paint(P['mask'], 'front', lambda i, j, w, h: noisy(shade(mask, 0.8) if j == 0 else mask, i, j, seed + 2))
    cloth(P['mask_knot'], mask, seed + 2)
    vp = P['vest']
    cloth(vp, LEATHER, seed + 4, edge=True)
    paint(vp, 'front', lambda i, j, w, h: T4 if 3 <= i <= 4 else noisy(LEATHER_D if i in (2, 5) else LEATHER, i, j, seed + 4))
    fill(vp, T4, ('up', 'down'))
    cloth(P['strap'], LEATHER_D, seed + 6)
    pp = P['pauldron']
    cloth(pp, LEATHER, seed + 8)
    paint(pp, 'up', lambda i, j, w, h: IRON + (255,) if (i + j) % 3 == 0 else noisy(LEATHER, i, j, seed + 8))   # clous
    paint_common(seed)


def chief_model():
    """Chef des bandits : bandana noir noué, barbe noire, cicatrice ; manteau de fourrure sur les épaules et courte cape ;
    gilet ouvert sur un torse velu, grosse ceinture à boucle dorée, brassards et bandes molletières."""
    new_model()
    part('bandana', 'head', (-4, -8, -4, 8, 3, 8), inflate=0.4)
    part('bandana_knot', 'head', (-1, -1, 0, 2, 2, 1), pivot=(0, -6.6, 4.5), rot=(0, 0, 0.785))
    part('bandana_tail_a', 'head', (-0.5, 0, -0.5, 1, 5, 1), pivot=(-0.4, -6.2, 4.9), rot=(0.35, 0, 0.3))
    part('bandana_tail_b', 'head', (-0.5, 0, -0.5, 1, 4, 1), pivot=(0.5, -6.2, 4.9), rot=(0.25, 0, -0.32))
    part('beard', 'head', (-3, -2.2, -1, 6, 3, 1), pivot=(0, 0, -4.0), inflate=0.1)
    part('beard_tip', 'beard', (-1.5, 0, -1, 3, 2, 1), pivot=(0, 0.8, 0), rot=(-0.2, 0, 0))
    part('fur_mantle', 'body', (-5.5, -2, -3.5, 11, 3, 7), inflate=0.35)
    part('fur_cape', 'body', (-5, 0, 0, 10, 14, 1), pivot=(0, 0.5, 2.8), rot=(0.15, 0, 0))
    part('vest', 'body', (-4, 0, -2, 8, 10, 4), inflate=0.3)
    part('fur_cuff_right', 'right_arm', (-3, -2.5, -2, 4, 2, 4), inflate=0.6)
    part('fur_cuff_left', 'left_arm', (-1, -2.5, -2, 4, 2, 4), inflate=0.6)
    common_gear()
    part('big_buckle', 'body', (-1.5, 8.6, -3.1, 3, 3, 1), inflate=0.1)


def paint_chief():
    seed = 101
    skin = ((196, 140, 98), (160, 106, 70))
    sk, skd = skin
    paint_body(skin, BLACK, BLACK_L, (54, 46, 40), seed, chest_open=True)
    face(['HHHHHHHH',
          'HssssssH',
          'sbbssbbs',
          'swpssXws',
          'ssSnnSXs',
          'BBsMMsBB',
          'BBBBBBBB',
          'BBBBBBBB'], {'H': BLACK, 's': sk, 'b': BLACK, 'w': EYE, 'p': PUPIL, 'S': skd, 'n': skd, 'X': SCAR,
                        'M': (110, 50, 46), 'B': BLACK})
    paint(P['head'], 'front', lambda i, j, w, h: PUPIL + (255,) if (i, j) == (5, 3) else None)   # œil gauche sous la cicatrice
    cloth(P['bandana'], BLACK, seed, edge=True)
    paint(P['bandana'], 'front', lambda i, j, w, h: noisy(BLACK_L if j == 0 else BLACK, i, j, seed))
    fill(P['bandana'], T4, ('down',))
    for n in ('bandana_knot', 'bandana_tail_a', 'bandana_tail_b'):
        cloth(P[n], BLACK, seed + 1)
    for n in ('beard', 'beard_tip'):
        cloth(P[n], BLACK, seed + 2)
        paint(P[n], 'front', lambda i, j, w, h: noisy(BLACK_L if (i + j) % 3 == 0 else BLACK, i, j, seed + 2))
    for n in ('fur_mantle', 'fur_cuff_right', 'fur_cuff_left'):
        for f in ('front', 'back', 'right', 'left', 'up', 'down'):
            paint(P[n], f, lambda i, j, w, h: noisy(FUR_L if (i * 3 + j) % 5 == 0 else FUR_D if (i + 2 * j) % 4 == 0 else FUR,
                                                     i, j, seed + 3, 10))
    paint(P['fur_mantle'], 'down', lambda i, j, w, h: T4 if 2 <= i <= 8 and 2 <= j <= 4 else None)
    fp = P['fur_cape']
    cloth(fp, CAPE, seed + 4, edge=True)
    paint(fp, 'back', lambda i, j, w, h: noisy(FUR if j < 2 else CAPE_D if j == h - 1 or i in (0, w - 1) else CAPE, i, j, seed + 4))
    paint(fp, 'front', lambda i, j, w, h: noisy(CAPE_D, i, j, seed + 4))
    vp = P['vest']
    cloth(vp, LEATHER_D, seed + 5, edge=True)
    paint(vp, 'front', lambda i, j, w, h: T4 if 2 <= i <= 5 else noisy(LEATHER_D, i, j, seed + 5))
    fill(vp, T4, ('up', 'down'))
    paint_common(seed)
    fill(P['big_buckle'], GOLD + (255,))
    paint(P['big_buckle'], 'front', lambda i, j, w, h: (90, 70, 30, 255) if (i, j) == (1, 1) else GOLD + (255,))


# =====================================================================================================================
def jf(v):
    return f'{float(v)}F'


def dim(v):
    return str(int(v)) if float(v).is_integer() else jf(round(v, 4))


def java_method(name, doc):
    out = []
    for p in PARTS:
        if p.base:
            continue
        parent = f'root.getChild("{p.parent}")' if P[p.parent].base else p.parent
        x, y, z, w, h, d = p.box
        deform = 'CubeDeformation.NONE' if not p.inflate else f'new CubeDeformation({jf(p.inflate)})'
        if p.rot == (0, 0, 0):
            pose = 'PartPose.ZERO' if p.pivot == (0, 0, 0) else f'PartPose.offset({", ".join(jf(v) for v in p.pivot)})'
        else:
            pose = f'PartPose.offsetAndRotation({", ".join(jf(v) for v in p.pivot)}, {", ".join(jf(round(v, 4)) for v in p.rot)})'
        out.append(f'        PartDefinition {p.name} = {parent}.addOrReplaceChild("{p.name}", CubeListBuilder.create().texOffs({p.uv[0]}, {p.uv[1]})\n'
                   f'                .addBox({jf(x)}, {jf(y)}, {jf(z)}, {dim(w)}, {dim(h)}, {dim(d)}, {deform}), {pose});')
    body = '\n'.join(out)
    return f'''    /** {doc} */
    public static LayerDefinition {name}() {{
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TW}, {TH});
    }}
'''


def build(model_fn, painter, tex_names, bb_name, entity_class, preview_dir, preview_tag):
    global img, px, PARTS, P, BB_NAME, BB_CLASS, BB_TEX
    model_fn()
    pack()
    P = {p.name: p for p in PARTS}
    for k, tex in enumerate(tex_names):
        img = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
        px = img.load()
        painter(k)
        img.save(os.path.join(TEX_DIR, tex))
        if k == 0:
            BB_NAME, BB_CLASS, BB_TEX = bb_name, entity_class, tex
            open(os.path.join(BB_DIR, bb_name + '.bbmodel'), 'w').write(json.dumps(bbmodel(), indent=1))
        if preview_dir:
            render(os.path.join(preview_dir, f'{preview_tag}_{k}_face.png'), 0.0)
            render(os.path.join(preview_dir, f'{preview_tag}_{k}_34.png'), 0.7)
            render(os.path.join(preview_dir, f'{preview_tag}_{k}_dos.png'), math.pi)
    return PARTS


# =====================================================================================================================
out_dir = sys.argv[1] if len(sys.argv) > 1 else None
methods = []
build(bandit_model, paint_bandit, [f'bandit_{k}.png' for k in range(4)], 'bandit', 'Bandit', out_dir, 'bandit')
methods.append(java_method('bandit', 'Bandit : capuche, foulard sur le bas du visage, gilet de cuir, bandoulière, épaulière cloutée.'))
build(chief_model, lambda k: paint_chief(), ['bandit_chief_0.png'], 'bandit_chief', 'BanditChief', out_dir, 'chef')
methods.append(java_method('chief', 'Chef des bandits : bandana noir, barbe, cicatrice, manteau de fourrure, courte cape, gilet ouvert.'))
java = """package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Géométrie des bandits (camp de bandits) : même construction que les pirates (PirateGearModels) : corps humanoïde classique +
 * équipement 3D en relief attaché aux parties du corps (il suit les animations). Texture 128x128 : peau en haut à gauche
 * (disposition classique 64x32), équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_bandits.py (avec les textures
 * bandit_N / bandit_chief_0 et les fichiers Blockbench src/blockbench/bandit.bbmodel, bandit_chief.bbmodel).
 *
 * <ul>
 *   <li>Bandit : capuche ample (brune, olive, grise ou rouge sombre selon la variante), foulard sur le bas du visage noué
 *       derrière la tête, gilet de cuir ouvert, bandoulière, épaulière cloutée, brassards, bandes molletières, fourreau.</li>
 *   <li>Chef : bandana noir noué à longs pans, barbe noire, cicatrice sur l'oeil gauche, manteau de fourrure sur les épaules et
 *       courte cape rouge sombre doublée de fourrure, gilet ouvert sur le torse, grosse boucle dorée, brassards à fourrure.</li>
 * </ul>
 */
public final class BanditModels {

    public static final ModelLayerLocation BANDIT = new ModelLayerLocation(BloxPiece.id("bandit"), "main");
    public static final ModelLayerLocation CHIEF = new ModelLayerLocation(BloxPiece.id("bandit_chief"), "main");

    private BanditModels() {
    }

""" + "\n".join(methods) + "}\n"
open(JAVA, 'w').write(java)
print('ok')
