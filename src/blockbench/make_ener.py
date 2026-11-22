# Ener : génère le modèle Java (EnerModels.java), la texture (ener.png), le fichier Blockbench (ener.bbmodel, format
# « Modded Entity ») et des aperçus. Même méthode que Gol D. Roger et Silvers Rayleigh (make_silvers_rayleigh.py) : corps
# humanoïde classique (peau en haut à gauche de la texture 128x128, disposition 64x32) + équipement 3D accroché aux membres,
# mêmes tailles de pièces, même peinture des texels.
# Touches originales BloxPiece : liseré à éclairs dorés et losanges sur le bas du pagne, gravures en zigzag sur l'or (bracelets,
# chevillères, boucles d'oreilles, bâton), tambours à virgules recourbées avec un éclat central doré et un cercle gravé.
# Usage (depuis la racine du projet) : python3 src/blockbench/make_ener.py [dossier_apercus]
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = os.path.join(ROOT, 'src/client/java/nolan/bloxpiece/client/entity/EnerModels.java')
PNG = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity/ener.png')
BB = os.path.join(ROOT, 'src/blockbench/ener.bbmodel')
TW = TH = 128

# ---------------------------------------------------------------- couleurs (d'après les images de référence)
T = (0, 0, 0, 0)
SKIN, SKIN_D, SKIN_L, SKIN_DD = (240, 214, 196, 255), (210, 178, 160, 255), (252, 234, 220, 255), (178, 146, 130, 255)
BAND, BAND_D, BAND_L = (238, 238, 232, 255), (200, 200, 198, 255), (252, 252, 248, 255)
HAIR, HAIR_D = (230, 206, 128, 255), (192, 162, 90, 255)
SASH, SASH_D, SASH_L = (80, 62, 172, 255), (52, 38, 124, 255), (112, 96, 204, 255)
WRAP, WRAP_D, WRAP_L = (92, 154, 154, 255), (62, 114, 120, 255), (126, 184, 180, 255)
PANT, PANT_D, PANT_L = (242, 160, 46, 255), (204, 118, 28, 255), (252, 198, 98, 255)
NAVY, NAVY_L = (40, 32, 76, 255), (62, 52, 108, 255)
GOLD, GOLD_D, GOLD_L = (238, 196, 54, 255), (178, 134, 28, 255), (252, 234, 122, 255)
RING, RING_D = (214, 218, 226, 255), (168, 174, 188, 255)
DRUM, DRUM_D = (238, 230, 186, 255), (204, 192, 140, 255)
CUFF, CUFF_D = (104, 150, 160, 255), (120, 82, 52, 255)
EYE, PUPIL, MOUTH, TEETH = (246, 246, 240, 255), (44, 34, 30, 255), (120, 62, 58, 255), (250, 250, 246, 255)


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


def aim(v):
    """Rotations (x, y, z) qui alignent l'axe X local d'un cube sur le vecteur v (ordre ZYX de ModelPart)."""
    vx, vy, vz = v
    L = math.sqrt(vx * vx + vy * vy + vz * vz)
    beta = -math.asin(vz / L)
    gamma = math.atan2(vy, vx)
    return (0.0, beta, gamma), L


# ---------------------------------------------------------------- corps humanoïde (HumanoidModel.createMesh)
part('head', None, (-4, -8, -4, 8, 8, 8), uv=(0, 0), base=True)
part('hat', 'head', (-4, -8, -4, 8, 8, 8), inflate=0.5, uv=(32, 0), base=True)
part('body', None, (-4, 0, -2, 8, 12, 4), uv=(16, 16), base=True)
part('right_arm', None, (-3, -2, -2, 4, 12, 4), pivot=(-5, 2, 0), rot=(-0.5, 0, 0), uv=(40, 16), base=True)  # pose de repos (EnerModel) : bâton en main
part('left_arm', None, (-1, -2, -2, 4, 12, 4), pivot=(5, 2, 0), uv=(40, 16), mirror=True, base=True)
part('right_leg', None, (-2, 0, -2, 4, 12, 4), pivot=(-1.9, 12, 0), uv=(0, 16), base=True)
part('left_leg', None, (-2, 0, -2, 4, 12, 4), pivot=(1.9, 12, 0), uv=(0, 16), mirror=True, base=True)

# ---------------------------------------------------------------- tête : bandeau blanc noué derrière, très longs lobes d'oreilles
# bandeau : 2e couche de la tête (dessus, front jusqu'aux sourcils, côtés, arrière) + noeud dans la nuque et deux pans
part('bandana_knot', 'head', (-1.5, -1.5, 0, 3, 3, 1), pivot=(0, -5.0, 4.3))
part('bandana_tail_right', 'bandana_knot', (-1, 0, 0, 2, 5, 1), pivot=(-0.6, 1.0, 0.3), rot=(0.25, 0, 0.2), inflate=-0.15)
part('bandana_tail_left', 'bandana_knot', (-1, 0, 0, 2, 4, 1), pivot=(0.7, 1.0, 0.3), rot=(0.3, 0, -0.25), inflate=-0.15)
# lobes d'oreilles démesurés : ils partent des oreilles et pendent devant les pectoraux ; boucle dorée en losange au bout
part('ear_right', 'head', (-1, 0, -0.5, 2, 11, 1), pivot=(-3.9, -3.6, -0.3), rot=(-0.30, 0, -0.15), inflate=-0.05)
part('ear_left', 'head', (-1, 0, -0.5, 2, 11, 1), pivot=(3.9, -3.6, -0.3), rot=(-0.30, 0, 0.15), inflate=-0.05)
part('earring_right', 'ear_right', (-1, -1, -0.5, 2, 2, 1), pivot=(0, 11.4, 0), rot=(0, 0, 0.785), inflate=-0.05)
part('earring_left', 'ear_left', (-1, -1, -0.5, 2, 2, 1), pivot=(0, 11.4, 0), rot=(0, 0, 0.785), inflate=-0.05)

# ---------------------------------------------------------------- taille : écharpe violette nouée, pagne bleu-vert
part('sash', 'body', (-4, 8, -2, 8, 3, 4), inflate=0.7)
part('sash_knot', 'body', (-1.5, -1.5, -1, 3, 3, 1), pivot=(2.4, 9.6, -2.7))
part('sash_tail', 'body', (-1, 0, -0.5, 2, 11, 1), pivot=(3.0, 10.4, -5.1), rot=(0, 0, -0.06))
part('sash_tail_low', 'sash_tail', (-1.5, 0, -0.5, 3, 3, 1), pivot=(0.3, 10.6, 0), rot=(0, 0, 0.1), inflate=-0.05)
part('wrap_waist', 'body', (-4, 10, -2, 8, 2, 4), inflate=0.85)
part('wrap_front_right', 'body', (-4.6, 0, 0, 5, 8, 1), pivot=(0, 11.2, -4.3))
part('wrap_front_left', 'body', (-0.4, 0, 0, 5, 7, 1), pivot=(0, 11.2, -4.5), rot=(0, 0, -0.04))
part('wrap_back', 'body', (-4.5, 0, 0, 9, 9, 1), pivot=(0, 11.2, 3.35))

# ---------------------------------------------------------------- bras nus : bracelets dorés empilés aux poignets ; bâton doré
# bracelets fins sur l'avant-bras, AU-DESSUS du poignet : la main (y 10 à 12) reste nue
for k, y in enumerate((5.6, 6.55, 7.5)):
    part(f'bangle_right_{k}', 'right_arm', (-3, y, -2, 4, 1, 4), inflate=0.3 - 0.03 * k)
    part(f'bangle_left_{k}', 'left_arm', (-1, y, -2, 4, 1, 4), inflate=0.3 - 0.03 * k)
# bâton doré tenu dans la main droite (bras un peu en avant), planté droit au sol devant le pied : EnerModel annule la
# rotation du bras sur le bâton, qui reste vertical pendant la marche
part('staff', 'right_arm', (-0.5, -22.5, -0.5, 1, 36, 1), pivot=(-1.0, 10.5, -1.2), rot=(0.5, 0, 0), inflate=0.08)
part('staff_cap_top', 'staff', (-1, -1, -1, 2, 2, 2), pivot=(0, -22.5, 0), inflate=-0.1)
part('staff_cap_bottom', 'staff', (-1, -1, -1, 2, 2, 2), pivot=(0, 12.8, 0), inflate=-0.2)

# ---------------------------------------------------------------- jambes : sarouel orange bouffant, revers, chevillères, pieds nus
for side in ('right', 'left'):
    leg = f'{side}_leg'
    part(f'pants_{side}', leg, (-2, 0, -2, 4, 9, 4), inflate=1.25)
    part(f'pants_puff_{side}', leg, (-2, 7, -2, 4, 2, 4), inflate=1.55)        # le bas qui gonfle au-dessus de la cheville
    part(f'cuff_{side}', leg, (-2, 9, -2, 4, 1, 4), inflate=0.45)
    part(f'anklet_{side}_0', leg, (-2, 10, -2, 4, 1, 4), inflate=0.3)
    part(f'anklet_{side}_1', leg, (-2, 10.9, -2, 4, 1, 4), inflate=0.22)

# ---------------------------------------------------------------- grand anneau et ses quatre tambours, ACCROCHÉS au dos
# Comme sur l'image de référence (vue de dos) : l'anneau passe juste derrière la tête (contre le bandeau, il ne flotte pas en
# arrière) et ne fait que le haut du cercle, des tambours du bas jusqu'au-dessus de la tête ; de chaque tambour du bas part une
# tige recourbée qui descend se fixer sur le haut du dos, entre la nuque et l'épaule.
RING_C = (0.0, -8.0, 5.1)      # centre (repère du torse) : au-dessus de la tête, juste derrière elle
RING_R = 10.5
RING_FROM, RING_TO, RING_N = -104, 104, 14   # arc (degrés, 0 = en haut) : il s'arrête aux tambours du bas
for k in range(RING_N):
    a = math.radians(RING_FROM + (RING_TO - RING_FROM) * (k + 0.5) / RING_N)
    mid = (RING_C[0] + RING_R * math.sin(a), RING_C[1] - RING_R * math.cos(a), RING_C[2])
    part(f'ring_{k}', 'body', (-2.5, -0.5, -0.5, 5, 1, 1), pivot=mid, rot=(0, 0, a), inflate=-0.05)
DRUM_ANGLES = (('upper_right', -38), ('upper_left', 38), ('lower_right', -100), ('lower_left', 100))
DRUM_POS = {}
for name, deg in DRUM_ANGLES:
    a = math.radians(deg)
    pos = (RING_C[0] + RING_R * math.sin(a), RING_C[1] - RING_R * math.cos(a), RING_C[2])
    DRUM_POS[name] = pos
    part(f'drum_{name}', 'body', (-4, -4, -1, 8, 8, 2), pivot=pos)
# tiges recourbées (3 tronçons) : du dessous de chaque tambour du bas jusqu'au haut du dos, où elles sont fixées
for side, sx in (('right', -1), ('left', 1)):
    d = DRUM_POS[f'lower_{side}']
    pts = [(d[0] - 0.8 * sx, d[1] + 3.6, d[2] - 0.2), (7.6 * sx, -0.4, 4.0), (5.2 * sx, 0.2, 2.9), (2.6 * sx, 0.6, 2.25)]
    for k in range(3):
        a0, a1 = pts[k], pts[k + 1]
        rot, L = aim(tuple(a1[i] - a0[i] for i in range(3)))
        part(f'strap_{side}_{k}', 'body', (-0.5, -0.5, -0.5, max(1, round(L + 1)), 1, 1), pivot=a0, rot=rot, inflate=0.05)
    part(f'strap_{side}_mount', 'body', (-1.5, -1, 0, 3, 2, 1), pivot=(2.6 * sx, 1.0, 1.7))   # platine fixée sur le dos


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


def gold(i, j, w, h, seed=0):
    """Or gravé (touche originale) : petit zigzag d'éclair sombre en continu, reflets clairs au-dessus."""
    zig = (i + seed) % 4
    row = (0, 1, 2, 1)[zig]
    if h >= 3:
        if j % 3 == row:
            return GOLD_D
        return GOLD_L if j % 3 == (row + 2) % 3 and i % 2 == 0 else GOLD
    return GOLD_D if (i + seed) % 4 == 0 else GOLD_L if (i + seed) % 4 == 2 else GOLD


def skin_shade(i, j, w, h):
    return SKIN_D if i in (0, w - 1) else SKIN


# --- tête : visage (paupières lourdes, sourire narquois), bandeau blanc bas sur le front, favoris et collier de barbe blonds
head = P['head']
fill(head, SKIN)
FACE = [  # 8 x 8, regard de face (colonne 0 = joue droite du personnage)
    'WWWWWWWW',   # bandeau
    'wwwwwwww',   # bord du bandeau (ombre)
    'YhHssHhY',   # sourcils fins, cheveux blonds aux tempes (sous le bandeau)
    'sKKsSKKs',   # paupières lourdes (regard mi-clos)
    'sEPsSPEs',   # yeux plissés
    'ssSnnSss',   # nez
    'SMTTTTMS',   # sourire narquois, dents
    'SssSSssS',   # menton rasé (pas de barbe)
]
FMAP = {'W': BAND, 'w': BAND_D, 'Y': HAIR, 'h': HAIR_D, 'H': HAIR, 's': SKIN, 'S': SKIN_D, 'K': SKIN_DD, 'E': EYE,
        'P': PUPIL, 'n': SKIN_D, 'M': MOUTH, 'T': TEETH, 'd': SKIN_D}
paint(head, 'front', lambda i, j, w, h: FMAP[FACE[j][i]])
paint(head, 'up', lambda i, j, w, h: BAND_D if (i + j) % 5 == 0 else BAND)
paint(head, 'back', lambda i, j, w, h: BAND if j < 5 else SKIN_D if j == 7 else HAIR)
for f in ('right', 'left'):
    front_col = 7 if f == 'right' else 0              # colonne côté visage
    paint(head, f, lambda i, j, w, h, fc=front_col: BAND if j < 2 else HAIR if (abs(i - fc) <= 1 and j < 4) else
          SKIN_D if (j in (3, 4) and i in (3, 4)) else SKIN)  # oreille
paint(head, 'down', lambda i, j, w, h: SKIN_D)
hat = P['hat']                                       # 2e couche : volume du bandeau (transparent sous le front)
paint(hat, 'up', lambda i, j, w, h: BAND_D if (i * 2 + j) % 7 == 0 else BAND_L if (i + j) % 6 == 2 else BAND)
paint(hat, 'front', lambda i, j, w, h: (BAND_D if j == 1 else BAND_L if (i + j) % 5 == 0 else BAND) if j < 2 else T)
paint(hat, 'right', lambda i, j, w, h: (BAND_D if j == 2 else BAND) if j < 3 else T)
paint(hat, 'left', lambda i, j, w, h: (BAND_D if j == 2 else BAND) if j < 3 else T)
paint(hat, 'back', lambda i, j, w, h: (BAND_D if j == 4 or (i + j) % 6 == 0 else BAND) if j < 5 else T)
paint(hat, 'down', lambda i, j, w, h: T)
for n in ('bandana_knot', 'bandana_tail_right', 'bandana_tail_left'):
    fill(P[n], BAND)
    for f in ('front', 'back', 'right', 'left', 'down'):
        paint(P[n], f, lambda i, j, w, h: BAND_D if (i + j) % 3 == 0 or j == h - 1 else BAND)
for n in ('ear_right', 'ear_left'):
    fill(P[n], SKIN)
    for f in ('right', 'left', 'back'):
        paint(P[n], f, lambda i, j, w, h: SKIN_D if j % 4 == 3 else SKIN)
    paint(P[n], 'front', lambda i, j, w, h: SKIN_DD if i == 0 else SKIN_L if j < 2 else SKIN)   # bord sombre : lisible devant la peau du torse
for n in ('earring_right', 'earring_left'):
    fill(P[n], GOLD)
    paint(P[n], 'front', lambda i, j, w, h: GOLD_D if (i + j) % 2 else GOLD_L)   # gravure en damier
    paint(P[n], 'back', lambda i, j, w, h: GOLD_D if (i + j) % 2 else GOLD)

# --- torse nu musclé : pectoraux, abdominaux, obliques
body = P['body']
fill(body, SKIN)
TORSO = [  # 8 x 12 (colonne 0 = côté droit du personnage)
    'ssSssSss',
    'sLLsSLLs',
    'sLLLsLLs',
    'SssSSssS',
    'sSssssSs',
    'ssLSSLss',
    'sSLSSLSs',
    'ssLSSLss',
    'sSLSSLSs',
    'SsLSSLsS',
    'sSsSSsSs',
    'ssSssSss',
]
TMAP = {'s': SKIN, 'S': SKIN_D, 'L': SKIN_L}
paint(body, 'front', lambda i, j, w, h: TMAP[TORSO[j][i]])
paint(body, 'back', lambda i, j, w, h: SKIN_D if (i in (3, 4) and 1 <= j <= 9) or (j == 2 and i in (1, 6)) else SKIN)
for f in ('right', 'left'):
    paint(body, f, lambda i, j, w, h: SKIN_D if (j % 3 == 1 and 4 <= j <= 10 and i in (1, 2)) else SKIN)

# --- bras nus (épaules et biceps ombrés, mains claires)
for name in ('right_arm',):
    a = P[name]
    for f in ('front', 'back', 'right', 'left'):
        paint(a, f, lambda i, j, w, h: SKIN_L if j >= 10 else SKIN_D if (j in (1, 5) and i in (1, 2)) else SKIN)
    fill(a, SKIN, ('up',))
    fill(a, SKIN_D, ('down',))
    paint(a, 'front', lambda i, j, w, h: SKIN_D if j == 11 and i % 2 else SKIN_L if j >= 10 else
          SKIN_D if (j in (1, 5) and i in (1, 2)) else SKIN)        # doigts
for k in range(3):
    for side in ('right', 'left'):
        n = f'bangle_{side}_{k}'
        fill(P[n], GOLD)
        for f in ('front', 'back', 'right', 'left'):
            paint(P[n], f, lambda i, j, w, h, s=k: gold(i, j, w, h, s))
        fill(P[n], T, ('up', 'down'))

# --- jambes : pieds nus (orteils), le reste est sous le sarouel
for name in ('right_leg',):
    l = P[name]
    for f in ('front', 'back', 'right', 'left'):
        paint(l, f, lambda i, j, w, h, f=f: (SKIN_D if f == 'front' and j == 11 and i % 2 else SKIN_L if j == 11 else SKIN) if j >= 10 else PANT)
    fill(l, PANT, ('up',))
    fill(l, SKIN_D, ('down',))


# --- sarouel orange bouffant : côtes en diagonale, bandes bleu nuit à pois orange
def pants(i, j, w, h, side_seed):
    if j in (3, 4):
        return PANT_L if (i + j + side_seed) % 4 == 0 and j == 4 else NAVY_L if j == 3 and i % 4 == 0 else NAVY
    diag = (i + j + side_seed) % 4
    return PANT_D if diag == 0 else PANT_L if diag == 2 else PANT


def puff(i, j, w, h, side_seed):
    if j == 0:
        return NAVY if (i + side_seed) % 3 else PANT_L    # bande bleu nuit à pois
    return PANT_D if (i - j + side_seed) % 4 == 0 else PANT_L if (i - j + side_seed) % 4 == 2 else PANT


for k, side in enumerate(('right', 'left')):
    n = f'pants_{side}'
    fill(P[n], PANT)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h, s=k: pants(i, j, w, h, s))
    fill(P[n], T, ('up', 'down'))
    n = f'pants_puff_{side}'
    fill(P[n], PANT)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h, s=k: puff(i, j, w, h, s))
    fill(P[n], PANT_D, ('down',))
    fill(P[n], T, ('up',))
    n = f'cuff_{side}'
    fill(P[n], CUFF)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: CUFF_D if i % 3 == 0 else CUFF)
    fill(P[n], T, ('up',))
    for m in range(2):
        n = f'anklet_{side}_{m}'
        fill(P[n], GOLD)
        for f in ('front', 'back', 'right', 'left'):
            paint(P[n], f, lambda i, j, w, h, s=m + 2: gold(i, j, w, h, s))
        fill(P[n], T, ('up', 'down'))


# --- écharpe violette : plis horizontaux ; noeud et pan qui tombe jusqu'aux chevilles
def sash(i, j, w, h):
    return SASH_D if j == h - 1 or (i + 2 * j) % 7 == 0 else SASH_L if j == 0 or (i + 2 * j) % 7 == 3 else SASH


for n in ('sash', 'sash_knot', 'sash_tail', 'sash_tail_low'):
    fill(P[n], SASH)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, sash)
paint(P['sash_tail'], 'front', lambda i, j, w, h: SASH_D if i == 0 or j % 4 == 3 else SASH)
paint(P['sash_tail_low'], 'front', lambda i, j, w, h: T if (j == h - 1 and i == 1) else SASH_D if i == 2 else SASH)
paint(P['sash_knot'], 'front', lambda i, j, w, h: SASH_D if i == 1 or j == 1 else SASH_L)


# --- pagne bleu-vert : plis verticaux ; liseré ORIGINAL en bas (éclairs dorés en zigzag entre deux filets, petits losanges)
def wrap(i, j, w, h, hem=True):
    if hem and j >= h - 3:
        k = j - (h - 3)
        if k == 0 or k == 2:
            return GOLD_D if k == 2 else WRAP_D                       # filets
        return GOLD if i % 4 in (0, 1) and (i // 2) % 2 == 0 else GOLD_L if i % 4 == 2 else WRAP_D  # zigzag d'éclair
    if j == h - 5 and i % 3 == 1:
        return WRAP_L                                                 # rangée de petits losanges clairs
    if j == h - 6 and i % 3 == 1:
        return WRAP_D
    return WRAP_D if i % 3 == 0 else WRAP_L if i % 5 == 2 else WRAP


for n in ('wrap_front_right', 'wrap_front_left', 'wrap_back'):
    fill(P[n], WRAP)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, wrap)
    fill(P[n], WRAP_D, ('up', 'down'))
fill(P['wrap_waist'], WRAP)
for f in ('front', 'back', 'right', 'left'):
    paint(P['wrap_waist'], f, lambda i, j, w, h: WRAP_D if i % 3 == 0 else WRAP)
fill(P['wrap_waist'], T, ('up', 'down'))

# --- bâton doré : bagues gravées tous les 6 texels
fill(P['staff'], GOLD)
for f in ('front', 'back', 'right', 'left'):
    paint(P['staff'], f, lambda i, j, w, h: GOLD_D if j % 6 == 0 else GOLD_L if j % 6 == 3 else GOLD)
for n in ('staff_cap_top', 'staff_cap_bottom'):
    fill(P[n], GOLD)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: GOLD_D if (i + j) % 2 else GOLD_L)

# --- anneau et sangles (métal clair)
for p in PARTS:
    if p.name.startswith('ring_') or p.name.startswith('strap_'):
        fill(p, RING)
        for f in ('down', 'back'):
            fill(p, RING_D, (f,))
        if p.name.endswith('_mount'):
            fill(p, GOLD)
            paint(p, 'back', lambda i, j, w, h: GOLD_D if (i + j) % 2 else GOLD_L)   # platine dorée gravée
            fill(p, T, ('front',))


# --- tambours : peau crème, trois virgules bleu nuit recourbées autour d'un éclat doré, cercle doré gravé
def drum_face(i, j, w, h, flip):
    dx, dy = i + 0.5 - w / 2, j + 0.5 - h / 2
    if flip:
        dx = -dx
    r = math.hypot(dx, dy)
    if r > 4.1:
        return T
    if r > 3.25:
        ang = math.atan2(dy, dx)
        return GOLD_D if int((ang + math.pi) / (2 * math.pi) * 12) % 3 == 0 else GOLD     # cercle gravé
    if r < 0.75:
        return GOLD_L                                                                 # éclat doré au centre
    th = math.atan2(dy, dx) - 0.9 * r
    m = th % (2 * math.pi / 3)
    if r > 1.2 and m < 0.95:
        return NAVY
    if r <= 1.2 and m < 0.5:
        return NAVY
    return DRUM


def drum_side(i, j, w, h, along):
    """Tranche du tambour : bords arrondis (les coins sont transparents), cerclage doré."""
    n = w if along == 'w' else h
    k = i if along == 'w' else j
    if k in (0, n - 1):
        return T
    return GOLD_D if k % 2 else GOLD


for name, _ in DRUM_ANGLES:
    p = P[f'drum_{name}']
    paint(p, 'front', lambda i, j, w, h: drum_face(i, j, w, h, False))
    paint(p, 'back', lambda i, j, w, h: drum_face(i, j, w, h, True))
    paint(p, 'up', lambda i, j, w, h: drum_side(i, j, w, h, 'w'))
    paint(p, 'down', lambda i, j, w, h: drum_side(i, j, w, h, 'w'))
    paint(p, 'right', lambda i, j, w, h: drum_side(i, j, w, h, 'h'))
    paint(p, 'left', lambda i, j, w, h: drum_side(i, j, w, h, 'h'))

os.makedirs(os.path.dirname(PNG), exist_ok=True)
img.save(PNG)


# ---------------------------------------------------------------- modèle Java
def jf(v):
    return f'{float(v)}F'


def dim(v):
    return str(int(v)) if float(v).is_integer() else jf(round(v, 4))


def java():
    out = []
    for p in PARTS:
        if p.base:
            continue
        parent = f'root.getChild("{p.parent}")' if P[p.parent].base else p.parent
        x, y, z, w, h, d = p.box
        deform = 'CubeDeformation.NONE' if not p.inflate else f'new CubeDeformation({jf(round(p.inflate, 4))})'
        if p.rot == (0, 0, 0):
            pose = 'PartPose.ZERO' if p.pivot == (0, 0, 0) else f'PartPose.offset({", ".join(jf(round(v, 4)) for v in p.pivot)})'
        else:
            pose = (f'PartPose.offsetAndRotation({", ".join(jf(round(v, 4)) for v in p.pivot)}, '
                    f'{", ".join(jf(round(v, 4)) for v in p.rot)})')
        out.append(f'        PartDefinition {p.name} = {parent}.addOrReplaceChild("{p.name}", CubeListBuilder.create().texOffs({p.uv[0]}, {p.uv[1]})\n'
                   f'                .addBox({jf(x)}, {jf(y)}, {jf(z)}, {dim(w)}, {dim(h)}, {dim(d)}, {deform}), {pose});')
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
 * Géométrie d'Ener : même construction que Gol D. Roger et Silvers Rayleigh (corps humanoïde classique + équipement 3D attaché
 * aux parties du corps, il suit les animations). Texture 128x128 : peau en haut à gauche (disposition classique 64x32),
 * équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_ener.py (avec la texture ener et le fichier Blockbench
 * src/blockbench/ener.bbmodel). Animations : EnerModel.
 *
 * <ul>
 *   <li>Tête : bandeau blanc bas sur le front, noué dans la nuque (deux pans) ; regard mi-clos, sourire narquois, favoris et
 *       cheveux blonds aux tempes, sans barbe ; lobes d'oreilles démesurés qui pendent devant les pectoraux, boucles dorées en losange gravées.</li>
 *   <li>Torse nu musclé ; écharpe violette nouée sur la hanche gauche, son pan tombe jusqu'aux chevilles ; pagne bleu-vert
 *       (devant en deux pans croisés, dos) à liseré original : zigzag d'éclair doré et rangée de losanges.</li>
 *   <li>Sarouel orange bouffant à côtes diagonales et bandes bleu nuit à pois ; revers, chevillères dorées gravées ; pieds nus.</li>
 *   <li>Bracelets dorés empilés aux poignets (gravure en zigzag) ; bâton doré à bagues dans la main droite.</li>
 *   <li>Dans le dos : grand anneau de métal clair juste derrière la tête, fixé sur le haut du dos par deux tiges recourbées
 *       (platines dorées), et ses quatre tambours
 *       (virgules bleu nuit autour d'un éclat doré, cercle doré gravé).</li>
 * </ul>
 */
public final class EnerModels {{

    public static final ModelLayerLocation ENER = new ModelLayerLocation(BloxPiece.id("ener"), "main");

    private EnerModels() {{
    }}

    public static LayerDefinition ener() {{
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
            'name': 'ener', 'model_identifier': 'ener', 'modded_entity_entity_class': 'Ener',
            'modded_entity_version': 'Mojmaps', 'visible_box': [1, 2, 0], 'resolution': {'width': TW, 'height': TH},
            'elements': elements, 'outliner': roots,
            'textures': [{'name': 'ener.png', 'id': '0', 'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH,
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


def render(path, yaw, scale=14, size=(560, 820)):
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
                pts.append((size[0] / 2 + rx * scale, 150 + (Y + 12) * scale))
                depth += rz
            polys.append((depth / 4, pts, tuple(min(255, int(k * shade[f])) for k in c[:3])))
    for _, pts, c in sorted(polys, key=lambda q: -q[0]):
        dr.polygon(pts, fill=c)
    im.save(path)


if len(sys.argv) > 1:
    out = sys.argv[1]
    render(os.path.join(out, 'ener_front.png'), 0.0)
    render(os.path.join(out, 'ener_34.png'), 0.6)
    render(os.path.join(out, 'ener_back.png'), math.pi)
    render(os.path.join(out, 'ener_profil_droit.png'), math.pi / 2)
    render(os.path.join(out, 'ener_profil_gauche.png'), -math.pi / 2)
    img.resize((TW * 4, TH * 4), Image.NEAREST).save(os.path.join(out, 'ener_texture.png'))
print('ok :', len(PARTS), 'parties')
