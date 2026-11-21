# Silvers Rayleigh : génère le modèle Java (RayleighModels.java), la texture (silvers_rayleigh.png), le fichier Blockbench
# (silvers_rayleigh.bbmodel, format « Modded Entity ») et des aperçus. Même méthode que Gol D. Roger (make_gol_d_roger.py) :
# corps humanoïde classique (peau en haut à gauche de la texture 128x128, disposition 64x32) + équipement 3D accroché aux
# membres, mêmes tailles de pièces, même peinture des texels.
# Usage (depuis la racine du projet) : python3 src/blockbench/make_silvers_rayleigh.py [dossier_apercus]
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = os.path.join(ROOT, 'src/client/java/nolan/bloxpiece/client/entity/RayleighModels.java')
PNG = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity/silvers_rayleigh.png')
BB = os.path.join(ROOT, 'src/blockbench/silvers_rayleigh.bbmodel')
TW = TH = 128

# ---------------------------------------------------------------- couleurs (d'après les images de référence)
T = (0, 0, 0, 0)
SKIN, SKIN_D, SKIN_L = (214, 156, 112, 255), (176, 120, 84, 255), (232, 182, 140, 255)
SCAR = (184, 108, 84, 255)
STUBBLE = (184, 150, 128, 255)
HAIR, HAIR_D, HAIR_L, HAIR_DD = (234, 234, 238, 255), (196, 198, 210, 255), (250, 250, 252, 255), (156, 160, 176, 255)
LENS = (234, 196, 140, 255)          # peau vue à travers les verres teintés
FRAME = (118, 110, 104, 255)
SHIRT, SHIRT_D, SHIRT_L = (230, 172, 64, 255), (194, 136, 38, 255), (244, 200, 106, 255)
BUTTON = (34, 30, 30, 255)
PANTS, PANTS_D, PANTS_L = (112, 32, 46, 255), (80, 20, 32, 255), (140, 50, 64, 255)
CAPE, CAPE_D, CAPE_L = (222, 224, 230, 255), (184, 188, 198, 255), (242, 243, 247, 255)
DIRT, DIRT_D = (122, 118, 108, 255), (86, 82, 76, 255)
LINING, LINING_D = (92, 100, 58, 255), (66, 74, 40, 255)
SANDAL, SANDAL_D = (122, 80, 46, 255), (86, 54, 30, 255)
EYE, PUPIL, MOUTH = (240, 240, 236, 255), (40, 30, 26, 255), (112, 58, 50, 255)


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

# ---------------------------------------------------------------- tête : cheveux blancs, lunettes rondes, moustache, bouc
# coiffure : longs cheveux blancs ondulés, plaqués vers l'arrière (front dégagé), gonflés sur le dessus, qui tombent dans la
# nuque et s'évasent en pointes ondulées sur les épaules ; deux longues mèches tombent devant le visage (l'une passe devant le
# verre droit et descend jusqu'au bouc, l'autre longe la joue gauche), chacune se recourbe en bas
part('hair_top', 'head', (-4, -1.5, -3.5, 8, 2, 8), pivot=(0, -7.8, 0.4), rot=(-0.1, 0, 0), inflate=0.35)
part('hair_back', 'head', (-4.5, -7.5, 0, 9, 11, 2), pivot=(0, 0, 3.6))
part('hair_back_low', 'hair_back', (-5, 0, 0, 10, 4, 2), pivot=(0, 3.0, 0.3), rot=(0.4, 0, 0))
part('hair_side_right', 'head', (-2, -7.5, -1.5, 2, 8, 6), pivot=(-3.6, 0, 0.5))
part('hair_side_right_low', 'hair_side_right', (-2, 0, -1.5, 2, 5, 5), pivot=(0, 0.5, 0.5), rot=(0.1, 0, 0.25))
part('hair_side_left', 'head', (0, -7.5, -1.5, 2, 8, 6), pivot=(3.6, 0, 0.5))
part('hair_side_left_low', 'hair_side_left', (0, 0, -1.5, 2, 5, 5), pivot=(0, 0.5, 0.5), rot=(0.1, 0, -0.25))
part('hair_lock_front_right', 'head', (-0.5, 0, -0.5, 1, 8, 1), pivot=(-1.3, -8.2, -4.6), rot=(0, 0, 0.06), inflate=-0.2)
part('hair_lock_front_right_low', 'hair_lock_front_right', (-0.5, 0, -0.5, 1, 3, 1), pivot=(0, 7.6, 0), rot=(0, 0, -0.45), inflate=-0.2)
part('hair_lock_front_left', 'head', (-0.5, 0, -0.5, 1, 7, 1), pivot=(4.1, -7.8, -4.2), rot=(0, 0, -0.06), inflate=-0.2)
part('hair_lock_front_left_low', 'hair_lock_front_left', (-0.5, 0, -0.5, 1, 3, 1), pivot=(0, 6.6, 0), rot=(0, 0, 0.4), inflate=-0.2)
# lunettes : deux verres, chacun un cube fin dont seule la tranche est peinte (cadre fin autour de l'oeil, rien devant ni
# derrière : on voit les yeux)
for side, sx in (('right', -1), ('left', 1)):
    part(f'lens_{side}', 'head', (-1.5, -1.5, -0.5, 3, 3, 1), pivot=(1.7 * sx, -4.5, -4.2), inflate=-0.3)

# moustache blanche courte qui rejoint le bouc par les coins de la bouche, bouc en pointe sous le menton
part('stache', 'head', (-2.5, -0.5, -1, 5, 1, 1), pivot=(0, -2.5, -4.0), inflate=-0.05)
part('stache_right', 'head', (-1, 0, -0.5, 1, 2, 1), pivot=(-2.2, -2.7, -4.3), rot=(0, 0, 0.15), inflate=-0.15)
part('stache_left', 'head', (0, 0, -0.5, 1, 2, 1), pivot=(2.2, -2.7, -4.3), rot=(0, 0, -0.15), inflate=-0.15)
part('goatee', 'head', (-1, 0, -1, 2, 2, 1), pivot=(0, -0.8, -4.0), rot=(-0.25, 0, 0))
part('goatee_tip', 'goatee', (-0.5, 0, -1, 1, 2, 1), pivot=(0, 2, 0), rot=(-0.15, 0, 0))

# ---------------------------------------------------------------- grande cape blanche à capuche, posée sur les épaules
# elle enveloppe les bras (mains dans les poches) et tombe jusqu'aux chevilles : dessus des épaules, capuche rabattue dans le
# dos (doublure vert olive visible autour du cou), dos d'un seul tenant avec deux plis, côtés qui couvrent les bras et pans
# avant ouverts sur la chemise ; ourlet déchiré, taches de crasse, doublure vert olive
part('cape_mantle', 'body', (-8.5, -1.5, -3.5, 17, 2, 7), inflate=0.15)
part('cape_hood', 'body', (-5, -4, 0, 10, 5, 3), pivot=(0, 0.5, 2.4), rot=(-0.25, 0, 0))
part('hood_rim_right', 'body', (-1, -3, -2.5, 2, 3, 5), pivot=(-4.8, 0.2, 0.6), rot=(0, 0, 0.25))
part('hood_rim_left', 'body', (-1, -3, -2.5, 2, 3, 5), pivot=(4.8, 0.2, 0.6), rot=(0, 0, -0.25))
part('cape_back', 'body', (-9.5, 0, 0, 19, 23, 1), pivot=(0, 0, 3.4))          # coins du dos fermés : recouvre les côtés
part('cape_fold_a', 'cape_back', (-1, 3, 0, 2, 16, 1), pivot=(-3.5, 0, 0.6))
part('cape_fold_b', 'cape_back', (-1, 5, 0, 2, 14, 1), pivot=(3.5, 0, 0.6))
part('cape_side_right', 'body', (-1, 0, -7, 1, 23, 8), pivot=(-8.5, 0, 3.4))      # rejoint le dos (même plan, sans écart)
part('cape_side_left', 'body', (0, 0, -7, 1, 23, 8), pivot=(8.5, 0, 3.4))
part('cape_front_right', 'cape_side_right', (-1, 0, 0, 6, 23, 1), pivot=(0, 0, -7.5), rot=(0, 0.1, 0))
part('cape_front_left', 'cape_side_left', (-4, 0, 0, 6, 23, 1), pivot=(0, 0, -7.5), rot=(0, -0.1, 0))

# ---------------------------------------------------------------- chemise moutarde, pantacourt bordeaux, sandales
part('shirt_collar_right', 'body', (-1, -1, -0.5, 2, 2, 1), pivot=(-2.2, 0.6, -2.4), rot=(0.2, 0, 0.45))
part('shirt_collar_left', 'body', (-1, -1, -0.5, 2, 2, 1), pivot=(2.2, 0.6, -2.4), rot=(0.2, 0, -0.45))
part('shirt_hem', 'body', (-4, 9, -2, 8, 3, 4), inflate=0.3)                    # bas de la chemise, par-dessus le pantalon
part('sleeve_right', 'right_arm', (-3, -2, -2, 4, 9, 4), inflate=0.25)
part('sleeve_left', 'left_arm', (-1, -2, -2, 4, 9, 4), inflate=0.25)
part('pants_right', 'right_leg', (-2, 0, -2, 4, 7, 4), inflate=0.38)          # pantacourt ample (s'arrête aux mollets)
part('pants_left', 'left_leg', (-2, 0, -2, 4, 7, 4), inflate=0.38)
part('cuff_right', 'right_leg', (-2, 6, -2, 4, 2, 4), inflate=0.5)            # revers roulé
part('cuff_left', 'left_leg', (-2, 6, -2, 4, 2, 4), inflate=0.5)
part('sandal_right', 'right_leg', (-2.5, 11, -3, 5, 1, 5), inflate=-0.2)        # semelle de la tong
part('sandal_left', 'left_leg', (-2.5, 11, -3, 5, 1, 5), inflate=-0.2)


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

def speck(i, j, seed):
    """Bruit déterministe (taches de crasse, accrocs) : même résultat à chaque génération."""
    return ((i * 73 + j * 151 + seed * 37) * 2654435761 >> 7) % 100


# --- tête (peau, visage âgé : rides, cicatrice sur l'oeil droit, yeux plissés derrière les verres)
head = P['head']
fill(head, SKIN)
paint(head, 'up', lambda i, j, w, h: HAIR_D if (i * 3 + j) % 7 == 0 else HAIR)
for f in ('right', 'left'):
    paint(head, f, lambda i, j, w, h: HAIR if j < 2 or (j < 8 and ((f == 'right' and i > 2) or (f == 'left' and i < 5))) else SKIN_D if j == 7 else SKIN)
paint(head, 'back', lambda i, j, w, h: HAIR_D if (i + j) % 5 == 0 else HAIR)
FACE = [  # 8 x 8, regard de face (colonne 0 = joue droite du personnage)
    'HHssssHH',   # front dégagé, cheveux plaqués sur les tempes
    'HskSkskH',   # rides du front
    'HXbsSbbH',   # sourcils blancs ; X : cicatrice qui barre l'oeil droit
    'sLpLLpLs',   # yeux plissés derrière les verres teintés
    'sXLnnLLs',   # nez (la cicatrice reprend sous l'oeil)
    'dSWWWWSd',   # (sous la moustache 3D)
    'dWWWMMWd',   # sourire en coin
    'dWWWWWWd',   # barbe blanche (le bouc 3D en dessous)
]
FMAP = {'H': HAIR, 's': SKIN, 'S': SKIN_D, 'k': SKIN_D, 'b': HAIR_D, 'X': SCAR, 'p': PUPIL, 'L': LENS, 'n': SKIN_D,
        'W': HAIR, 'M': MOUTH, 'd': STUBBLE}
paint(head, 'front', lambda i, j, w, h: FMAP[FACE[j][i]])
hat = P['hat']                                  # 2e couche : volume des cheveux (transparent ailleurs)
paint(hat, 'up', lambda i, j, w, h: HAIR_D if (i + 2 * j) % 6 == 0 else HAIR)
paint(hat, 'front', lambda i, j, w, h: (HAIR if (j == 0 and i in (0, 1, 2, 5, 6, 7)) or (j == 1 and i in (0, 7)) else T))
paint(hat, 'right', lambda i, j, w, h: (HAIR_D if (i + j) % 4 == 0 else HAIR) if (j < 6 and i > 1) or j < 2 else T)
paint(hat, 'left', lambda i, j, w, h: (HAIR_D if (i + j) % 4 == 0 else HAIR) if (j < 6 and i < 6) or j < 2 else T)
paint(hat, 'back', lambda i, j, w, h: HAIR_D if (i * 2 + j) % 5 == 0 else HAIR)
paint(hat, 'down', lambda i, j, w, h: T)

# --- torse : chemise moutarde boutonnée (boutons noirs), col ouvert en V sur le torse
body = P['body']
fill(body, SHIRT)


def torso(i, j, w, h):
    if j == 0 and 2 <= i <= 5:
        return SKIN                              # col ouvert
    if j == 1 and i in (3, 4):
        return SKIN
    if j == 1 and i in (2, 5):
        return SHIRT_D                           # bord du col
    if i == 3 and j >= 2:
        return BUTTON if j % 3 == 2 else SHIRT_D  # patte de boutonnage
    if i == 4 and j >= 2:
        return SHIRT_L
    if i in (0, 7):
        return SHIRT_D
    return SHIRT


paint(body, 'front', torso)
for f in ('back', 'right', 'left'):
    paint(body, f, lambda i, j, w, h: SHIRT_D if (i == w // 2 and 2 <= j <= 9) or i in (0, w - 1) else SHIRT)

# --- bras (sous la cape, mains dans les poches) : manches moutarde, mains
for name in ('right_arm',):
    a = P[name]
    for f in ('front', 'back', 'right', 'left'):
        paint(a, f, lambda i, j, w, h: SKIN if j >= 9 else SHIRT_D if j in (7, 8) else SHIRT)
    fill(a, SHIRT, ('up',))
    fill(a, SKIN_D, ('down',))
# --- jambes : pantacourt bordeaux, mollets nus (poils), pieds en tongs
for name in ('right_leg',):
    l = P[name]

    def leg(i, j, w, h, f):
        if j < 7:
            return PANTS_D if (i + 2 * j) % 5 == 0 else PANTS
        if j == 11:
            if f == 'front':
                return SANDAL if i in (1, 2) else SKIN_L       # bride de la tong entre les orteils
            return SKIN_D
        if j == 10 and f == 'front' and i in (0, 3):
            return SANDAL                                       # brides qui remontent sur le pied
        return SKIN_D if (i * 3 + j * 5) % 7 == 0 else SKIN    # mollets poilus
    for f in ('front', 'back', 'right', 'left'):
        paint(l, f, lambda i, j, w, h, f=f: leg(i, j, w, h, f))
    fill(l, PANTS, ('up',))
    fill(l, SANDAL_D, ('down',))


# --- cheveux 3D, moustache, bouc
def strands(i, j, w, h, ragged):
    """Cheveux : mèches verticales (reflets et traits gris), pointes irrégulières en bas pour les parties qui tombent."""
    if ragged and j == h - 1 and i % 2 == 0:
        return T
    k = (i * 5 + j // 3) % 7
    return HAIR_DD if k == 3 else HAIR_D if k == 1 else HAIR_L if k == 5 else HAIR


for n in ('hair_top', 'hair_back', 'hair_back_low', 'hair_side_right', 'hair_side_right_low', 'hair_side_left',
          'hair_side_left_low', 'hair_lock_front_right', 'hair_lock_front_right_low', 'hair_lock_front_left',
          'hair_lock_front_left_low'):
    fill(P[n], HAIR)
    ragged = n.endswith('_low')
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h, r=ragged: strands(i, j, w, h, r))
    if ragged:
        fill(P[n], T, ('down',))
paint(P['hair_top'], 'up', lambda i, j, w, h: HAIR_D if (i * 2 + j) % 5 == 0 else HAIR_L if (i + j) % 7 == 3 else HAIR)
for n in ('hair_lock_front_right', 'hair_lock_front_left'):
    fill(P[n], HAIR_L, ('front',))
    paint(P[n], 'front', lambda i, j, w, h: HAIR_D if j % 3 == 2 else HAIR_L)
for n in ('stache', 'stache_right', 'stache_left', 'goatee', 'goatee_tip'):
    fill(P[n], HAIR)
    for f in ('front', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: HAIR_D if (i + j) % 3 == 2 else HAIR)
paint(P['goatee_tip'], 'front', lambda i, j, w, h: T if (j == h - 1 and i == 0) else HAIR_D if i == 1 else HAIR)
# lunettes : anneau sombre, centre et coins transparents
for n in ('lens_right', 'lens_left'):
    fill(P[n], FRAME)
for n in ('lens_right', 'lens_left'):
    fill(P[n], T, ('front', 'back'))                      # verres : rien devant les yeux

# --- cape blanche : drap clair un peu sale, ourlet déchiré, doublure vert olive
def cloth(i, j, w, h, seed, torn=True):
    """Drap de la cape : plis verticaux, taches de crasse (plus nombreuses vers le bas), bas déchiré (texels transparents)."""
    if torn and j >= h - 3:
        r = speck(i, 0, seed)
        tear = 3 if r < 18 else 2 if r < 45 else 1 if r < 80 else 0
        if j >= h - tear:
            return T
    s = speck(i // 2, j // 2, seed)                   # taches de 2 x 2 texels
    dirty = (j * 9) // max(1, h) - 2                     # rien en haut, quelques taches vers le bas
    if s < dirty:
        return DIRT_D if speck(i, j, seed + 3) < 30 else DIRT
    if (i * 7 + j // 4) % 9 == 0:
        return CAPE_D
    if (i * 5 + j // 5) % 11 == 4:
        return CAPE_L
    return CAPE


def lining(i, j, w, h, seed, torn=True):
    if torn and j >= h - 3:
        r = speck(w - 1 - i, 0, seed)
        tear = 3 if r < 18 else 2 if r < 45 else 1 if r < 80 else 0
        if j >= h - tear:
            return T
    return LINING_D if (i * 3 + j // 4) % 7 == 0 else LINING


seed = 1
for n, outer, inner in (('cape_back', 'back', 'front'), ('cape_side_right', 'right', 'left'), ('cape_side_left', 'left', 'right'),
                        ('cape_front_right', 'front', 'back'), ('cape_front_left', 'front', 'back')):
    fill(P[n], CAPE)
    paint(P[n], outer, lambda i, j, w, h, s=seed: cloth(i, j, w, h, s))
    paint(P[n], inner, lambda i, j, w, h, s=seed: lining(i, j, w, h, s))
    for f in ('front', 'back', 'right', 'left'):
        if f not in (outer, inner):
            paint(P[n], f, lambda i, j, w, h, s=seed: cloth(i, j, w, h, s + 7))
    fill(P[n], T, ('down',))
    seed += 1
for n in ('cape_fold_a', 'cape_fold_b'):                                 # plis : même drap, un peu plus clair au sommet
    fill(P[n], CAPE)
    paint(P[n], 'back', lambda i, j, w, h: CAPE_L if j < 3 else cloth(i, j, w, h, 9, torn=False))
    fill(P[n], CAPE_D, ('right', 'left'))
fill(P['cape_mantle'], CAPE)
for f in ('front', 'back', 'right', 'left'):
    paint(P['cape_mantle'], f, lambda i, j, w, h: CAPE_D if j == h - 1 else cloth(i, j, w, h, 12, torn=False))
paint(P['cape_mantle'], 'up', lambda i, j, w, h: cloth(i, j, w, h, 13, torn=False))
paint(P['cape_mantle'], 'down', lambda i, j, w, h: LINING)
for n in ('cape_hood',):
    fill(P[n], CAPE)
    for f in ('back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: CAPE_D if j == 0 else cloth(i, j, w, h, 14, torn=False))
    fill(P[n], LINING, ('up', 'front'))
    paint(P[n], 'up', lambda i, j, w, h: LINING_D if j == 0 or i % 4 == 0 else LINING)
for n in ('hood_rim_right', 'hood_rim_left'):
    fill(P[n], LINING)
    paint(P[n], 'front', lambda i, j, w, h: LINING_D if j == h - 1 else LINING)
    fill(P[n], CAPE, ('down',))

# --- chemise, pantalon, sandales
for n in ('shirt_collar_right', 'shirt_collar_left'):
    fill(P[n], SHIRT_L)
    fill(P[n], SHIRT_D, ('back',))
fill(P['shirt_hem'], SHIRT)
paint(P['shirt_hem'], 'front', lambda i, j, w, h: (BUTTON if j == 1 else SHIRT_D) if i == 3 else SHIRT_L if i == 4 else SHIRT_D if j == h - 1 else SHIRT)
for f in ('back', 'right', 'left'):
    paint(P['shirt_hem'], f, lambda i, j, w, h: SHIRT_D if j == h - 1 or (i + j) % 6 == 0 else SHIRT)
fill(P['shirt_hem'], T, ('up', 'down'))
for n in ('sleeve_right', 'sleeve_left'):
    fill(P[n], SHIRT)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: SHIRT_D if j == h - 1 or (i == w // 2 and 2 <= j <= 6) else SHIRT)
    fill(P[n], T, ('down',))
for n in ('pants_right', 'pants_left'):
    fill(P[n], PANTS)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: PANTS_L if (i + j) % 5 == 0 else PANTS_D if (2 * i + j) % 7 == 0 else PANTS)
    fill(P[n], T, ('up', 'down'))
for n in ('cuff_right', 'cuff_left'):
    fill(P[n], PANTS_D)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: PANTS if j == 0 else PANTS_D)
    fill(P[n], T, ('up', 'down'))
for n in ('sandal_right', 'sandal_left'):
    fill(P[n], SANDAL)
    fill(P[n], SANDAL_D, ('down', 'right', 'left', 'front', 'back'))

os.makedirs(os.path.dirname(PNG), exist_ok=True)
img.save(PNG)


# ---------------------------------------------------------------- modèle Java
def jf(v):
    return f'{float(v)}F'


def dim(v):
    """Taille d'un cube : entier si possible (comme Roger), sinon float (tiges fines des lunettes)."""
    return str(int(v)) if float(v).is_integer() else jf(round(v, 4))


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
 * Géométrie de Silvers Rayleigh, le Second du Roi des Pirates : même construction que Gol D. Roger (corps humanoïde classique +
 * équipement 3D attaché aux parties du corps). Texture 128x128 : peau en haut à gauche (disposition classique 64x32),
 * équipement en dessous. FICHIER GÉNÉRÉ par src/blockbench/make_silvers_rayleigh.py (avec la texture silvers_rayleigh et le
 * fichier Blockbench src/blockbench/silvers_rayleigh.bbmodel). Animations : RayleighModel.
 *
 * <ul>
 *   <li>Tête : longs cheveux blancs ondulés plaqués vers l'arrière (2e couche + volume sur le dessus + mèches 3D dans le dos et
 *       sur les côtés), deux longues mèches devant le visage ; lunettes (deux verres à cadre fin) ; cicatrice sur
 *       l'oeil droit, rides ; moustache blanche et bouc en pointe.</li>
 *   <li>Grande cape blanche à capuche rabattue, doublure vert olive, posée sur les épaules : elle enveloppe les bras (mains
 *       dans les poches) et tombe jusqu'aux chevilles ; ourlet déchiré, taches de crasse.</li>
 *   <li>Chemise moutarde à boutons noirs, col ouvert ; pantacourt bordeaux à revers ; mollets nus ; tongs.</li>
 * </ul>
 */
public final class RayleighModels {{

    public static final ModelLayerLocation RAYLEIGH = new ModelLayerLocation(BloxPiece.id("silvers_rayleigh"), "main");

    private RayleighModels() {{
    }}

    public static LayerDefinition rayleigh() {{
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
            'name': 'silvers_rayleigh', 'model_identifier': 'silvers_rayleigh', 'modded_entity_entity_class': 'Rayleigh',
            'modded_entity_version': 'Mojmaps', 'visible_box': [1, 2, 0], 'resolution': {'width': TW, 'height': TH},
            'elements': elements, 'outliner': roots,
            'textures': [{'name': 'silvers_rayleigh.png', 'id': '0', 'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH,
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
    render(os.path.join(out, 'rayleigh_front.png'), 0.0)
    render(os.path.join(out, 'rayleigh_34.png'), 0.6)
    render(os.path.join(out, 'rayleigh_back.png'), math.pi)
    render(os.path.join(out, 'rayleigh_profil_gauche.png'), -math.pi / 2)
    img.resize((TW * 4, TH * 4), Image.NEAREST).save(os.path.join(out, 'rayleigh_texture.png'))
print('ok :', len(PARTS), 'parties')
