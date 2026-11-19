# Gol D. Roger : génère le modèle Java (RogerModels.java), la texture (gol_d_roger_0.png), le fichier Blockbench
# (gol_d_roger.bbmodel, format « Modded Entity ») et des aperçus. Même méthode que les pirates et les Marines : corps
# humanoïde classique (peau en haut à gauche de la texture 128x128, disposition 64x32) + équipement 3D accroché aux membres.
# Usage (depuis la racine du projet) : python3 src/blockbench/make_gol_d_roger.py [dossier_apercus]
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = os.path.join(ROOT, 'src/client/java/nolan/bloxpiece/client/entity/RogerModels.java')
PNG = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity/gol_d_roger_0.png')
BB = os.path.join(ROOT, 'src/blockbench/gol_d_roger.bbmodel')
TW = TH = 128

# ---------------------------------------------------------------- couleurs (d'après les images de référence)
T = (0, 0, 0, 0)
SKIN, SKIN_D, SKIN_L = (224, 168, 122, 255), (190, 132, 92, 255), (238, 190, 148, 255)
HAIR, HAIR_L = (26, 22, 26, 255), (60, 52, 58, 255)
RED, RED_D, RED_L = (176, 30, 44, 255), (124, 18, 32, 255), (204, 56, 64, 255)
GOLD, GOLD_D, GOLD_L = (232, 184, 56, 255), (172, 124, 28, 255), (252, 222, 120, 255)
LINING, LINING_D = (74, 68, 78, 255), (52, 48, 56, 255)
PINK, PINK_D, PINK_L = (236, 150, 178, 255), (200, 108, 142, 255), (248, 186, 206, 255)
SASH, SASH_D = (242, 204, 74, 255), (204, 158, 40, 255)
PANTS, PANTS_D, PANTS_L = (240, 168, 40, 255), (198, 126, 18, 255), (252, 196, 84, 255)
WHITE, WHITE_D = (246, 243, 236, 255), (206, 202, 196, 255)
BOOT, BOOT_D = (100, 62, 36, 255), (66, 40, 22, 255)
BELT = (112, 70, 38, 255)
EYE, PUPIL, MOUTH = (240, 240, 236, 255), (30, 24, 22, 255), (90, 24, 28, 255)
BLADE_DARK = (40, 34, 30, 255)


class Part:
    """Une partie du modèle (= un ModelPart Java = un groupe Blockbench) avec un cube."""
    def __init__(self, name, parent, box, pivot=(0, 0, 0), rot=(0, 0, 0), inflate=0.0, uv=None, mirror=False, base=False):
        self.name, self.parent, self.box, self.pivot, self.rot = name, parent, box, pivot, rot
        self.inflate, self.uv, self.mirror, self.base = inflate, uv, mirror, base


PARTS = []


def part(*a, **k):
    p = Part(*a, **k)
    PARTS.append(p)
    return p


# ---------------------------------------------------------------- corps humanoïde (HumanoidModel.createMesh)
part('head', None, (-4, -8, -4, 8, 8, 8), uv=(0, 0), base=True)
part('hat', 'head', (-4, -8, -4, 8, 8, 8), inflate=0.5, uv=(32, 0), base=True)
part('body', None, (-4, 0, -2, 8, 12, 4), uv=(16, 16), base=True)
part('right_arm', None, (-3, -2, -2, 4, 12, 4), pivot=(-5, 2, 0), uv=(40, 16), base=True)
part('left_arm', None, (-1, -2, -2, 4, 12, 4), pivot=(5, 2, 0), uv=(40, 16), mirror=True, base=True)
part('right_leg', None, (-2, 0, -2, 4, 12, 4), pivot=(-1.9, 12, 0), uv=(0, 16), base=True)
part('left_leg', None, (-2, 0, -2, 4, 12, 4), pivot=(1.9, 12, 0), uv=(0, 16), mirror=True, base=True)

# ---------------------------------------------------------------- tête : cheveux, moustache, chapeau
# coiffure : épaisse masse de cheveux noirs mi-longs, gonflée sur les côtés, qui tombe jusqu'à la nuque et s'évase en pointes
# ondulées sur les épaules ; deux mèches encadrent le visage devant les oreilles
part('hair_back', 'head', (-4.5, -7.5, 0, 9, 10, 2), pivot=(0, 0, 3.6))
part('hair_back_low', 'hair_back', (-5, 0, 0, 10, 4, 2), pivot=(0, 2.0, 0.3), rot=(0.35, 0, 0))
part('hair_side_right', 'head', (-2, -7, -2.5, 2, 7, 7), pivot=(-3.6, 0, 0))
part('hair_side_right_low', 'hair_side_right', (-2, 0, -2, 2, 5, 6), pivot=(0, 0, 0.6), rot=(0, 0, 0.3))
part('hair_side_left', 'head', (0, -7, -2.5, 2, 7, 7), pivot=(3.6, 0, 0))
part('hair_side_left_low', 'hair_side_left', (0, 0, -2, 2, 5, 6), pivot=(0, 0, 0.6), rot=(0, 0, -0.3))
part('hair_lock_right', 'head', (-0.5, 0, -0.5, 1, 5, 1), pivot=(-3.9, -6.2, -3.6), rot=(0, 0, 0.1))
part('hair_lock_left', 'head', (-0.5, 0, -0.5, 1, 5, 1), pivot=(3.9, -6.2, -3.6), rot=(0, 0, -0.1))
part('stache', 'head', (-2.5, -0.5, -1, 5, 1, 1), pivot=(0, -2.6, -4.05), inflate=0.1)          # grande moustache
part('stache_right', 'head', (-4, -0.5, -0.5, 4, 1, 1), pivot=(-2.3, -2.6, -4.55), rot=(0, 0.25, 0.42))
part('stache_left', 'head', (0, -0.5, -0.5, 4, 1, 1), pivot=(2.3, -2.6, -4.55), rot=(0, -0.25, -0.42))
part('stache_tip_right', 'stache_right', (-1, -1.5, -0.5, 1, 1, 1), pivot=(-3.6, 0, 0), rot=(0, 0, 0.5))
part('stache_tip_left', 'stache_left', (0, -1.5, -0.5, 1, 1, 1), pivot=(3.6, 0, 0), rot=(0, 0, -0.5))
# chapeau de Roger : large bord plat, calotte haute à dessus plat, bord avant relevé en haut fronton (pointe au centre,
# Jolly Roger), côtés relevés en ailes qui filent vers l'arrière, bord arrière relevé et penché ; plume blanche sur le côté
part('hat_brim', 'head', (-6, -0.5, -6.5, 12, 1, 12), pivot=(0, -8.4, 0.4))
part('hat_crown', 'head', (-4, -5, -4, 8, 5, 8), pivot=(0, -8.6, 0.3), inflate=0.4)
part('hat_crown_top', 'head', (-3, -1, -3, 6, 1, 6), pivot=(0, -14.0, 0.3), inflate=0.2)
part('hat_front', 'head', (-3.5, -7, -0.5, 7, 7, 1), pivot=(0, -8.6, -6.0), rot=(0.2, 0, 0))     # fronton + Jolly Roger
part('hat_front_left', 'head', (0, -6, -0.5, 5, 6, 1), pivot=(3.4, -8.6, -6.0), rot=(0.2, -0.5, 0))
part('hat_front_right', 'head', (-5, -6, -0.5, 5, 6, 1), pivot=(-3.4, -8.6, -6.0), rot=(0.2, 0.5, 0))
part('hat_wing_left', 'head', (-0.5, -4, 0, 1, 4, 9), pivot=(7.6, -8.6, -3.8), rot=(0, 0.12, -0.3))
part('hat_wing_right', 'head', (-0.5, -4, 0, 1, 4, 9), pivot=(-7.6, -8.6, -3.8), rot=(0, -0.12, 0.3))
part('hat_back', 'head', (-6, -4, -0.5, 12, 4, 1), pivot=(0, -8.6, 6.6), rot=(-0.3, 0, 0))
part('plume', 'head', (-1, -9, -0.5, 2, 9, 1), pivot=(4.6, -12.4, 1.0), rot=(-0.55, 0, 0.55))   # plume blanche
part('plume_b', 'head', (-0.5, -7, -0.5, 1, 7, 1), pivot=(4.0, -12.2, 2.6), rot=(-0.9, 0, 0.35))

# ---------------------------------------------------------------- manteau de capitaine sur les épaules
part('coat_collar', 'body', (-5.5, -5, 0, 11, 5, 1), pivot=(0, 0.4, 2.6), rot=(-0.28, 0, 0))     # grand col relevé
part('collar_right', 'body', (-1, -4, -3, 1, 4, 3), pivot=(-5.0, 0.4, 2.8), rot=(0, 0.25, 0.15))
part('collar_left', 'body', (0, -4, -3, 1, 4, 3), pivot=(5.0, 0.4, 2.8), rot=(0, -0.25, -0.15))
part('mantle', 'body', (-5, -1.5, -3, 10, 2, 6), inflate=0.4)                                    # dessus des épaules
# manteau d'un seul tenant : un dos épais (2) sans couture, des coins arrondis qui le relient aux pans avant, une pièce
# d'épaules qui le raccorde au col et aux épaulettes, et deux plis peu saillants ; même ourlet doré tout autour
part('coat_back', 'body', (-7.5, 0, 0, 15, 23, 2), pivot=(0, -0.6, 3.0), rot=(0.12, 0, 0))
part('coat_yoke', 'body', (-7, 0, 0, 14, 4, 2), pivot=(0, -1.0, 2.4), rot=(0.05, 0, 0))
part('coat_fold_a', 'coat_back', (-1, 3, 0, 2, 15, 1), pivot=(-3.0, 0, 1.5))                       # plis : s'arrêtent avant l'ourlet
part('coat_fold_b', 'coat_back', (-1, 4, 0, 2, 14, 1), pivot=(3.0, 0, 1.5))
part('coat_corner_left', 'coat_back', (0, 0, 0, 2, 23, 2), pivot=(7.5, 0, 0), rot=(0, 0.63, 0))
part('coat_corner_right', 'coat_back', (-2, 0, 0, 2, 23, 2), pivot=(-7.5, 0, 0), rot=(0, -0.63, 0))
part('coat_front_left', 'coat_corner_left', (0, 0, -0.5, 5, 23, 1), pivot=(2.0, 0, 1.0), rot=(0, 0.64, 0))
part('coat_front_right', 'coat_corner_right', (-5, 0, -0.5, 5, 23, 1), pivot=(-2.0, 0, 1.0), rot=(0, -0.64, 0))
part('epaulette_right', 'right_arm', (-5, -3.4, -3, 7, 2, 6))                                    # épaulettes dorées à franges
part('fringe_right', 'right_arm', (-5, -1.4, -3, 7, 2, 6), inflate=0.05)
part('epaulette_left', 'left_arm', (-2, -3.4, -3, 7, 2, 6))
part('fringe_left', 'left_arm', (-2, -1.4, -3, 7, 2, 6), inflate=0.05)

# ---------------------------------------------------------------- chemise rose ouverte, ceinture, pantalon, bottes, sabre
part('sleeve_right', 'right_arm', (-3, -2, -2, 4, 9, 4), inflate=0.32)                          # manches bouffantes
part('sleeve_left', 'left_arm', (-1, -2, -2, 4, 9, 4), inflate=0.32)
part('shirt_collar_right', 'body', (-1, -1, -0.5, 2, 2, 1), pivot=(-2.4, 0.6, -2.4), rot=(0.2, 0, 0.5))
part('shirt_collar_left', 'body', (-1, -1, -0.5, 2, 2, 1), pivot=(2.4, 0.6, -2.4), rot=(0.2, 0, -0.5))
part('sash', 'body', (-4, 8.5, -2, 8, 3, 4), inflate=0.4)                                        # large ceinture de tissu jaune
part('belt', 'body', (-4, 9.5, -2, 8, 1, 4), inflate=0.5)                                        # ceinture de cuir
part('buckle', 'body', (-1, 9.5, -2.95, 2, 1, 1), inflate=0.12)
part('sash_knot', 'body', (-1, -1, -0.5, 2, 2, 1), pivot=(3.2, 10.2, -2.6), rot=(0, 0, -0.3))
part('sash_tail', 'body', (-1, 0, -0.5, 2, 9, 1), pivot=(3.4, 11.0, -2.5), rot=(-0.1, 0, -0.08))
part('sash_tail_b', 'body', (-0.5, 0, -0.5, 1, 7, 1), pivot=(4.6, 11.0, -2.2), rot=(-0.08, 0, -0.2))
part('pearls', 'body', (-0.5, 0, -0.5, 1, 5, 1), pivot=(-2.6, 10.8, -2.75), rot=(-0.12, 0, 0.18))   # collier de perles à la ceinture
part('pants_right', 'right_leg', (-2, 0, -2, 4, 8, 4), inflate=0.42)                             # pantalon orange ample
part('pants_left', 'left_leg', (-2, 0, -2, 4, 8, 4), inflate=0.42)
part('frill_right', 'right_leg', (-2, 8, -2, 4, 2, 4), inflate=0.62)                             # volants blancs aux chevilles
part('frill_left', 'left_leg', (-2, 8, -2, 4, 2, 4), inflate=0.62)
part('boot_right', 'right_leg', (-2, 10, -2, 4, 2, 4), inflate=0.32)                             # bottes de cuir
part('boot_left', 'left_leg', (-2, 10, -2, 4, 2, 4), inflate=0.32)
part('scabbard', 'body', (-0.5, -2, -1, 1, 11, 2), pivot=(4.6, 9.6, 0.4), rot=(0.95, 0, -0.22))   # fourreau du sabre, côté gauche
# poignée dorée du sabre : visible seulement quand le sabre est rangé (ArmedPirateModel, pièce « sheathed_hilt »)
part('sheathed_hilt', 'body', (-0.5, -5, -0.5, 1, 5, 1), pivot=(4.4, 9.2, -1.6), rot=(-0.6, 0, -0.22), inflate=0.05)
part('hilt_guard', 'sheathed_hilt', (-1.5, -0.2, -1.5, 3, 1, 3), pivot=(0, 0, 0))


# ---------------------------------------------------------------- placement des cubes dans la texture (sous la peau 64x32)
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


pack()
img = Image.new('RGBA', (TW, TH), T)
px = img.load()


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


P = {p.name: p for p in PARTS}

# --- tête (peau, visage)
head = P['head']
fill(head, SKIN)
paint(head, 'up', lambda i, j, w, h: HAIR_L if (i * 3 + j) % 7 == 0 else HAIR)
for f in ('right', 'left'):
    paint(head, f, lambda i, j, w, h: HAIR if j < 3 or (j < 7 and ((f == 'right' and i < 4) or (f == 'left' and i > 3))) else SKIN_D if j == 7 else SKIN)
paint(head, 'back', lambda i, j, w, h: HAIR_L if (i + j) % 5 == 0 else HAIR)
FACE = [  # 8 x 8, regard de face (colonne 0 = joue droite du personnage)
    'HHHHHHHH',
    'HHsHHsHH',
    'HbbsSbbH',   # sourcils épais, froncés vers le centre
    'swpSSpws',   # yeux
    'ssSnnSss',   # nez
    'dmmmmmmd',   # (sous la moustache 3D)
    'dMttttMd',   # grand sourire, dents serrées
    'HdDDDDdH',   # barbe de quelques jours, favoris
]
FMAP = {'H': HAIR, 's': SKIN, 'S': SKIN_D, 'b': HAIR, 'w': EYE, 'p': PUPIL, 'n': SKIN_D, 'm': HAIR, 'M': MOUTH, 't': WHITE,
        'd': SKIN_D, 'D': (150, 104, 72, 255)}
paint(head, 'front', lambda i, j, w, h: FMAP[FACE[j][i]])
hat = P['hat']                                  # 2e couche : volume des cheveux (transparent ailleurs)
paint(hat, 'up', lambda i, j, w, h: HAIR_L if (i + 2 * j) % 6 == 0 else HAIR)
paint(hat, 'front', lambda i, j, w, h: (HAIR if j == 0 or (j == 1 and i in (0, 1, 3, 4, 6, 7)) or (j == 2 and i in (0, 4, 7)) else T))
paint(hat, 'right', lambda i, j, w, h: HAIR if (j < 6 and i < 6) or (j < 2) else (HAIR_L if j < 7 and i < 3 else T))
paint(hat, 'left', lambda i, j, w, h: HAIR if (j < 6 and i > 1) or (j < 2) else (HAIR_L if j < 7 and i > 4 else T))
paint(hat, 'back', lambda i, j, w, h: HAIR_L if (i * 2 + j) % 5 == 0 else HAIR)
paint(hat, 'down', lambda i, j, w, h: T)

# --- torse : chemise rose ouverte, torse musclé, collier d'or
body = P['body']
fill(body, PINK)


def torso(i, j, w, h):
    if j >= 10:
        return PANTS
    if i in (0, 7):
        return PINK
    if i in (1, 6) and j > 1:
        return PINK_D                            # bord de la chemise ouverte
    if j == 0:
        return PINK if i in (1, 6) else SKIN
    if (j, i) in ((1, 2), (1, 5), (2, 3), (2, 4)):
        return GOLD
    if (j, i) == (3, 3):
        return GOLD_L                            # pendentif
    if j == 4 and i in (2, 5):
        return SKIN_D                            # pectoraux
    if j in (6, 8) and i in (2, 3, 4, 5):
        return SKIN_D if i in (3, 4) else SKIN   # abdominaux
    if j in (5, 7, 9) and i in (3, 4):
        return SKIN_L
    return SKIN


paint(body, 'front', torso)
for f in ('back', 'right', 'left'):
    paint(body, f, lambda i, j, w, h: PANTS if j >= 10 else PINK_L if j == 0 else PINK_D if (i == w // 2 and 2 <= j <= 8) else PINK)

# --- bras : manches roses, mains
for name in ('right_arm',):
    a = P[name]
    for f in ('front', 'back', 'right', 'left'):
        paint(a, f, lambda i, j, w, h: SKIN if j >= 9 else PINK_L if j == 8 else PINK_D if j == 7 else PINK)
    fill(a, PINK, ('up',))
    fill(a, SKIN_D, ('down',))
# --- jambes : pantalon, volants, bottes
for name in ('right_leg',):
    l = P[name]
    for f in ('front', 'back', 'right', 'left'):
        paint(l, f, lambda i, j, w, h: BOOT if j >= 10 else WHITE if j >= 8 else (PANTS_D if (i + 2 * j) % 5 == 0 else PANTS))
    fill(l, PANTS, ('up',))
    fill(l, BOOT_D, ('down',))

# --- cheveux 3D, moustache
def strands(i, j, w, h, ragged):
    """Cheveux : mèches verticales (reflets fins), pointes irrégulières en bas pour les parties qui tombent."""
    if ragged and j == h - 1 and i % 2 == 0:
        return T
    return HAIR_L if (i * 5 + j // 3) % 7 == 1 else HAIR    # reflets en mèches irrégulières


for n in ('hair_back', 'hair_back_low', 'hair_side_right', 'hair_side_right_low', 'hair_side_left', 'hair_side_left_low',
          'hair_lock_right', 'hair_lock_left'):
    fill(P[n], HAIR)
    ragged = n.endswith('_low') or n.startswith('hair_lock')
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h, r=ragged: strands(i, j, w, h, r))
    if ragged:
        fill(P[n], T, ('down',))
for n in ('stache', 'stache_right', 'stache_left', 'stache_tip_right', 'stache_tip_left'):
    fill(P[n], HAIR)
    paint(P[n], 'up', lambda i, j, w, h: HAIR_L if i % 2 == 0 else HAIR)

# --- chapeau rouge : liseré doré, Jolly Roger, plume
for n in ('hat_brim', 'hat_crown', 'hat_crown_top'):
    fill(P[n], RED)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: RED_D if j == 0 else RED)
paint(P['hat_crown'], 'front', lambda i, j, w, h: GOLD if j == h - 1 else RED_D if j == 0 else RED)
for f in ('back', 'right', 'left'):
    paint(P['hat_crown'], f, lambda i, j, w, h: GOLD if j == h - 1 else RED_D if j == 0 else RED)
for f in ('front', 'back', 'right', 'left'):
    paint(P['hat_brim'], f, lambda i, j, w, h: GOLD)
fill(P['hat_brim'], RED_D, ('down',))
JOLLY = ['X.....X',
         '.XXXXX.',
         '.X.X.X.',
         '..XXX..',
         '.X.X.X.',
         'X.....X']
for n in ('hat_front', 'hat_front_left', 'hat_front_right', 'hat_wing_left', 'hat_wing_right', 'hat_back'):
    fill(P[n], RED_D)
    fill(P[n], GOLD, ('up',))
paint(P['hat_front'], 'front', lambda i, j, w, h: GOLD if j == 0 else WHITE if 1 <= j <= 6 and JOLLY[j - 1][i] == 'X' else RED)
for n in ('hat_front_left', 'hat_front_right', 'hat_back'):
    paint(P[n], 'front', lambda i, j, w, h: GOLD if j == 0 else RED)
    paint(P[n], 'back', lambda i, j, w, h: GOLD if j == 0 else RED)
for n in ('hat_wing_left', 'hat_wing_right'):
    for f in ('right', 'left'):
        paint(P[n], f, lambda i, j, w, h: GOLD if j == 0 else RED)
for n in ('plume', 'plume_b'):
    fill(P[n], WHITE)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: WHITE_D if (i + j) % 3 == 0 else (230, 214, 226, 255) if j > h - 3 else WHITE)

# --- manteau rouge : galons dorés, boutons à brandebourgs, doublure grise
for n in ('coat_collar', 'collar_right', 'collar_left', 'mantle'):
    fill(P[n], RED)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: GOLD if j == 0 else RED_D if j == h - 1 else RED)
paint(P['coat_collar'], 'front', lambda i, j, w, h: GOLD if j == 0 else LINING)      # intérieur du col
def coat_cloth(i, j, w, h):
    """Drap rouge du manteau : uni, deux galons dorés à l'ourlet (à la même hauteur sur toutes les pièces)."""
    if j in (h - 1, h - 4):
        return GOLD
    if j == h - 3:
        return RED_D
    return RED


for n in ('coat_back', 'coat_corner_left', 'coat_corner_right', 'coat_yoke'):
    fill(P[n], RED)
    paint(P[n], 'front', lambda i, j, w, h: LINING)                     # doublure
for n in ('coat_back', 'coat_corner_left', 'coat_corner_right'):
    for f in ('back', 'right', 'left'):
        paint(P[n], f, coat_cloth)
for n in ('coat_fold_a', 'coat_fold_b'):                                 # plis : même drap, un peu plus clair au sommet
    fill(P[n], RED)
    paint(P[n], 'back', lambda i, j, w, h: RED_L if j < 3 else RED)
for n, outer, inner in (('coat_front_left', 'back', 'front'), ('coat_front_right', 'back', 'front')):   # face 'back' = dehors
    fill(P[n], RED)

    def panel(i, j, w, h, n=n):
        edge = 0 if n == 'coat_front_left' else w - 1       # bord avant du pan (côté torse)
        if i == edge or j == h - 1:
            return GOLD
        if j in (h - 4,):
            return GOLD
        if 3 <= j <= 16 and j % 3 == 0 and abs(i - edge) in (1, 2):
            return GOLD_L if abs(i - edge) == 1 else GOLD     # brandebourgs dorés
        return RED_L if abs(i - edge) == 3 else RED

    paint(P[n], outer, panel)
    paint(P[n], inner, lambda i, j, w, h: LINING_D if i == w // 2 else LINING)
    fill(P[n], GOLD, ('right', 'left'))
for n in ('epaulette_right', 'epaulette_left'):
    fill(P[n], GOLD)
    paint(P[n], 'up', lambda i, j, w, h: GOLD_L if (i + j) % 3 == 0 else GOLD)
for n in ('fringe_right', 'fringe_left'):
    fill(P[n], T, ('up', 'down'))
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: (GOLD if i % 2 == 0 else GOLD_D) if j == 0 or i % 2 == 0 else T)

# --- chemise, ceinture, pantalon, bottes, sabre
for n in ('sleeve_right', 'sleeve_left'):
    fill(P[n], PINK)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: PINK_L if j in (0, h - 1) else PINK_D if (j == h - 2 or (i == w // 2 and 3 <= j <= 6 and f in ('front', 'back'))) else PINK)
    fill(P[n], T, ('down',))
for n in ('shirt_collar_right', 'shirt_collar_left'):
    fill(P[n], PINK_L)
for n in ('sash', 'sash_knot'):
    fill(P[n], SASH)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: SASH_D if (i + j) % 3 == 0 else SASH)
fill(P['belt'], BELT); fill(P['buckle'], GOLD)
for n in ('sash_tail', 'sash_tail_b'):
    fill(P[n], SASH)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: (SASH if i % 2 else T) if j == h - 1 else SASH_D if j % 3 == 0 else SASH)
for n in ('pearls',):
    for f in ('front', 'back', 'right', 'left', 'up', 'down'):
        paint(P[n], f, lambda i, j, w, h: WHITE if j % 2 == 0 else WHITE_D)
for n in ('pants_right', 'pants_left'):
    fill(P[n], PANTS)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: PANTS_L if (i + j) % 5 == 0 else PANTS_D if (2 * i + j) % 7 == 0 else PANTS)
    fill(P[n], T, ('up', 'down'))
for n in ('frill_right', 'frill_left'):
    fill(P[n], WHITE)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: WHITE_D if (i + j) % 2 else WHITE)
    fill(P[n], T, ('up', 'down'))
for n in ('boot_right', 'boot_left'):
    fill(P[n], BOOT)
    paint(P[n], 'front', lambda i, j, w, h: BOOT_D if j == 1 else BOOT)
    fill(P[n], T, ('up',))
fill(P['scabbard'], BOOT_D)
for f in ('front', 'back', 'right', 'left'):
    paint(P['scabbard'], f, lambda i, j, w, h: GOLD if j in (0, h - 1) else BOOT_D)
fill(P['sheathed_hilt'], BLADE_DARK)
for f in ('front', 'back', 'right', 'left'):
    paint(P['sheathed_hilt'], f, lambda i, j, w, h: GOLD if j % 2 == 0 else BLADE_DARK)     # poignée tressée or et noir
fill(P['hilt_guard'], GOLD)
paint(P['hilt_guard'], 'up', lambda i, j, w, h: GOLD_L if (i, j) == (1, 1) else GOLD)

os.makedirs(os.path.dirname(PNG), exist_ok=True)
img.save(PNG)


# ---------------------------------------------------------------- modèle Java
def jf(v):
    return f'{float(v)}F'


def java():
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
                   f'                .addBox({jf(x)}, {jf(y)}, {jf(z)}, {w}, {h}, {d}, {deform}), {pose});')
    body = '\n'.join(out)
    return f'''package nolan.bloxpiece.client.entity;

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
 * Géométrie de Gol D. Roger, le Roi des Pirates : même construction que les pirates et les Marines (corps humanoïde classique +
 * équipement 3D attaché aux parties du corps, il suit les animations). Texture 128x128 : peau en haut à gauche (disposition
 * classique 64x32), équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_gol_d_roger.py (avec la texture
 * gol_d_roger_0 et le fichier Blockbench src/blockbench/gol_d_roger.bbmodel).
 *
 * <ul>
 *   <li>Tête : longs cheveux noirs (2e couche + mèches 3D sur les côtés et dans le dos), grande moustache noire relevée aux
 *       pointes, sourire carnassier ; chapeau rouge à liseré doré (large bord, calotte haute, fronton relevé avec le Jolly Roger,
 *       côtés et arrière relevés) et une plume blanche.</li>
 *   <li>Long manteau de capitaine rouge posé sur les épaules : grand col relevé, dos en 3 lés arrondis avec plis en relief et
 *       ourlet irrégulier, pans jusqu'aux mollets, galons et brandebourgs
 *       dorés, doublure grise, épaulettes dorées à franges.</li>
 *   <li>Chemise rose ouverte sur le torse musclé, collier d'or ; large ceinture de tissu jaune nouée sur le côté et ceinture de
 *       cuir ; collier de perles ; pantalon orange ample à volants blancs ; bottes de cuir.</li>
 *   <li>Sabre au côté gauche : fourreau, et poignée dorée « sheathed_hilt » affichée seulement quand le sabre est rangé.</li>
 * </ul>
 */
public final class RogerModels {{

    public static final ModelLayerLocation ROGER = new ModelLayerLocation(BloxPiece.id("gol_d_roger"), "main");

    private RogerModels() {{
    }}

    public static LayerDefinition roger() {{
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TW}, {TH});
    }}
}}
'''


open(JAVA, 'w').write(java())


# ---------------------------------------------------------------- fichier Blockbench (Modded Entity)
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
            'name': 'gol_d_roger', 'model_identifier': 'gol_d_roger', 'modded_entity_entity_class': 'GolDRoger',
            'modded_entity_version': 'Mojmaps', 'visible_box': [1, 2, 0], 'resolution': {'width': TW, 'height': TH},
            'elements': elements, 'outliner': roots,
            'textures': [{'name': 'gol_d_roger_0.png', 'id': '0', 'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH,
                          'particle': True, 'render_mode': 'default', 'visible': True, 'saved': True, 'uuid': str(uuid.uuid4()),
                          'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}]}


open(BB, 'w').write(json.dumps(bbmodel(), indent=1))


# ---------------------------------------------------------------- aperçu (rendu logiciel des texels, vues de face et 3/4)
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


if len(sys.argv) > 1:
    out = sys.argv[1]
    render(os.path.join(out, 'roger_front.png'), 0.0)
    render(os.path.join(out, 'roger_34.png'), 0.6)
    render(os.path.join(out, 'roger_back.png'), math.pi)
    img.resize((TW * 4, TH * 4), Image.NEAREST).save(os.path.join(out, 'roger_texture.png'))
print('ok :', len(PARTS), 'parties')
