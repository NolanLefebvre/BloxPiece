# Morgan « main de hache », capitaine de la Marine : génère le modèle Java (MorganModels.java), la texture (morgan.png), le
# fichier Blockbench (morgan.bbmodel, format « Modded Entity ») et des aperçus (repos + poses de l'attaque).
# Même méthode et même style que Gol D. Roger et Silvers Rayleigh (make_gol_d_roger.py, make_silvers_rayleigh.py) : corps
# humanoïde + équipement 3D accroché aux membres, 1 texel par pixel, même peinture des texels. Mais le corps est CONÇU à
# 48 px de haut (3 blocs, pieds à y = 24, sommet du crâne à y = -24), pas agrandi : tête 10, torse 17 x 16 de large, bras de
# 19 (6 d'épaisseur), jambes de 19 (7 d'épaisseur). La hache remplace sa main droite.
# Usage (depuis la racine du projet) : python3 src/blockbench/make_morgan.py [dossier_apercus]
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = os.path.join(ROOT, 'src/client/java/nolan/bloxpiece/client/entity/MorganModels.java')
PNG = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity/morgan.png')
BB = os.path.join(ROOT, 'src/blockbench/morgan.bbmodel')
TW, TH = 256, 128

# ---------------------------------------------------------------- couleurs (d'après les images de référence)
T = (0, 0, 0, 0)
SKIN, SKIN_D, SKIN_L = (214, 150, 104, 255), (176, 114, 76, 255), (232, 178, 134, 255)
STUBBLE = (150, 112, 86, 255)
HAIR, HAIR_D, HAIR_L = (236, 214, 128, 255), (196, 170, 92, 255), (250, 236, 176, 255)
STEEL, STEEL_D, STEEL_L, STEEL_DD = (162, 168, 176, 255), (110, 116, 128, 255), (212, 216, 222, 255), (60, 64, 74, 255)
IRON, IRON_D, IRON_L = (52, 54, 62, 255), (34, 36, 42, 255), (84, 88, 98, 255)       # corps noir du fer de la hache
EDGE, EDGE_D = (226, 230, 236, 255), (176, 182, 192, 255)                            # demi-lune argentée, polie
WOOD, WOOD_D = (156, 104, 60, 255), (116, 74, 40, 255)
COAT, COAT_D, COAT_L = (176, 222, 240, 255), (138, 190, 214, 255), (210, 240, 250, 255)   # manteau bleu clair
LINING, LINING_D = (176, 40, 112, 255), (128, 24, 80, 255)                            # doublure et revers magenta
STUD = (226, 214, 188, 255)
VEST, VEST_D, VEST_L = (30, 40, 88, 255), (20, 26, 60, 255), (50, 62, 118, 255)        # gilet bleu nuit
PANTS, PANTS_D, PANTS_L = (62, 110, 178, 255), (26, 40, 86, 255), (92, 140, 204, 255)  # pantalon bleu à rayures sombres
GOLD, GOLD_D, GOLD_L = (236, 190, 56, 255), (176, 128, 28, 255), (252, 224, 120, 255)
BOOT, BOOT_D, BOOT_L, LACE = (176, 130, 88, 255), (134, 94, 60, 255), (204, 160, 116, 255), (56, 40, 30, 255)
BUTTON = (120, 124, 140, 255)
EYE, PUPIL = (240, 240, 236, 255), (40, 40, 46, 255)


class Part:
    """Une partie du modèle (= un ModelPart Java = un groupe Blockbench) avec un cube."""
    def __init__(self, name, parent, box, pivot=(0, 0, 0), rot=(0, 0, 0), inflate=0.0, uv=None, mirror=False, base=False,
                 share=None):
        self.name, self.parent, self.box, self.pivot, self.rot = name, parent, box, pivot, rot
        self.inflate, self.uv, self.mirror, self.base, self.share = inflate, uv, mirror, base, share


PARTS = []


def part(*a, **k):
    p = Part(*a, **k)
    PARTS.append(p)
    return p


# ---------------------------------------------------------------- corps (mêmes pièces que HumanoidModel, à la taille de Morgan)
# bras écartés (pivots à x = ±12,3) : les bras nus passent À CÔTÉ du manteau sans manches, jamais au travers
part('head', None, (-5, -10, -5, 10, 10, 10), pivot=(0, -12, 0), base=True)
part('hat', 'head', (-5, -10, -5, 10, 10, 10), inflate=0.5, base=True)
part('body', None, (-8, 0, -4, 16, 17, 8), pivot=(0, -12, 0), base=True)
part('right_arm', None, (-3, -2, -3, 6, 11, 6), pivot=(-12.3, -10, 0), base=True)            # bras (épaule -> coude)
part('forearm_right', 'right_arm', (-2.5, 0, -2.5, 5, 12, 5), pivot=(0, 9, 0))                # avant-bras (coude -> poignet)
part('left_arm', None, (-3, -2, -3, 6, 19, 6), pivot=(12.3, -10, 0), base=True)
part('right_leg', None, (-3.5, 0, -3.5, 7, 19, 7), pivot=(-4, 5, 0), base=True)
part('left_leg', None, (-3.5, 0, -3.5, 7, 19, 7), pivot=(4, 5, 0), mirror=True, base=True, share='right_leg')

# ---------------------------------------------------------------- tête : cheveux blonds courts, mâchoire d'acier
# cheveux blonds très courts, plaqués vers l'arrière (le sommet est à y = -24 : 3 blocs pile) ; barbe de quelques jours
part('hair_top', 'head', (-5.5, -2, -5.5, 11, 2, 11), pivot=(0, -10, 0))
part('hair_front', 'head', (-4.5, -1, -1, 9, 1, 2), pivot=(0, -9.8, -5.0), rot=(0.3, 0, 0))
# mâchoire inférieure métallique, comme sur l'image : une plaque d'acier de la largeur du visage (elle n'élargit pas la tête)
# sur la bouche et le menton, bord inférieur arrondi, et EN HAUT une rangée de créneaux carrés (les « dents » de la prothèse)
# entre lesquels on aperçoit la bouche sombre ; un boulon rond à chaque coin du haut, qui la fixe aux joues ; petits côtés
# qui suivent les joues ; la mouette de la Marine gravée au centre
part('jaw', 'head', (-4.5, -3, -1, 9, 3.5, 1), pivot=(0, 0, -5.0))                         # plaque (bas du visage)
part('jaw_round', 'jaw', (-3.5, 0, -1, 7, 1, 1), pivot=(0, 0.5, 0))                         # bas arrondi du menton
part('jaw_under', 'jaw', (-4, 0, 0, 8, 0.6, 3.5), pivot=(0, 0.2, 0))                         # dessous du menton
for k, x0 in enumerate((-4.5, -2.0, 0.5, 3.0)):                                             # créneaux carrés du haut
    part(f'jaw_tooth_{k}', 'jaw', (0, -1.25, -1, 1.5, 1.25, 1), pivot=(x0, -3, 0))
part('jaw_side_right', 'head', (0, -3, 0, 0.6, 3.5, 2.5), pivot=(-5.05, 0, -5.0))           # côtés le long des joues
part('jaw_side_left', 'head', (-0.6, -3, 0, 0.6, 3.5, 2.5), pivot=(5.05, 0, -5.0))
part('jaw_bolt_right', 'head', (-0.6, -0.6, -0.6, 1.2, 1.2, 1.2), pivot=(-4.4, -3.6, -5.6))   # boulons ronds des coins
part('jaw_bolt_left', 'head', (-0.6, -0.6, -0.6, 1.2, 1.2, 1.2), pivot=(4.4, -3.6, -5.6))
# accroches qui maintiennent la mâchoire (comme sur l'image), PAR EN DESSOUS : de chaque côté, une tige d'acier part de l'arrière
# de la plaque et file le long de la ligne de la mâchoire, en bas du côté de la tête, jusque sous l'oreille, où un gros boulon
# rond la fixe au crâne
for side, sx in (('right', -1), ('left', 1)):
    part(f'jaw_strap_{side}', 'head', (-0.4, -0.4, 0, 0.8, 0.8, 4.0), pivot=(5.2 * sx, -1.6, -4.6), rot=(0.12, 0, 0))
    part(f'jaw_anchor_{side}', 'head', (-0.6, -0.9, -0.9, 1.2, 1.8, 1.8), pivot=(5.3 * sx, -2.1, -0.6))

# ---------------------------------------------------------------- tenue
# gilet bleu nuit à boutons, décolleté profond sur le torse (peint sur le corps)
# grand manteau bleu clair SANS MANCHES de capitaine, ouvert devant : doublure et revers magenta cloutés, très haut col
# relevé (magenta dedans), épaulettes dorées à franges, ceinture du manteau à boucle dorée ; il descend jusqu'aux bottes.
# Haut (épaules -> taille) : pièces fixes sur le torse. Bas (taille -> bottes, « pans ») : pivots à la taille, ils s'écartent
# avec les jambes en marchant (MorganModel) : les jambes ne les traversent pas.
part('coat_back', 'body', (-9.2, 0, 0, 18.4, 17, 1), pivot=(0, 0, 4.0))
part('coat_side_right', 'body', (-1, 0, -9.5, 1, 17, 10), pivot=(-8.2, 0, 5.0))
part('coat_side_left', 'body', (0, 0, -9.5, 1, 17, 10), pivot=(8.2, 0, 5.0))
part('coat_front_right', 'body', (0, 0, -1, 5, 17, 1), pivot=(-9.2, 0, -4.5))
part('coat_front_left', 'body', (-5, 0, -1, 5, 17, 1), pivot=(9.2, 0, -4.5))
part('coat_collar', 'body', (-7, -8, 0, 14, 8, 1), pivot=(0, 0.6, 3.4), rot=(-0.22, 0, 0))
part('collar_right', 'body', (-1, -7, -5, 1, 7, 5), pivot=(-6.4, 0.6, 3.6), rot=(0, 0.3, 0.22))
part('collar_left', 'body', (0, -7, -5, 1, 7, 5), pivot=(6.4, 0.6, 3.6), rot=(0, -0.3, -0.22))
part('coat_belt', 'body', (-9.2, 13.5, -5.5, 18.4, 2, 11), inflate=0.1)   # devant : seulement sur les pans (ouvert au milieu)
part('coat_buckle', 'body', (-1, -1, -0.5, 2, 2, 1), pivot=(6.7, 14.5, -5.9))
part('skirt_back', 'body', (-9.2, 0, 0, 18.4, 15, 1), pivot=(0, 17, 4.0))
part('skirt_side_right', 'body', (-1, 0, -9.5, 1, 15, 10), pivot=(-8.2, 17, 5.0))
part('skirt_side_left', 'body', (0, 0, -9.5, 1, 15, 10), pivot=(8.2, 17, 5.0))
part('skirt_front_right', 'body', (0, 0, -1, 5, 15, 1), pivot=(-9.2, 17, -4.5))
part('skirt_front_left', 'body', (-5, 0, -1, 5, 15, 1), pivot=(9.2, 17, -4.5))
# épaulettes dorées à franges, posées sur les épaules (sur le torse : elles restent en place quand le bras porte la hache)
part('epaulette_right', 'body', (-4, -3.4, -3.5, 7, 2, 7), pivot=(-12.3, 2, 0))
part('fringe_right', 'body', (-4, -1.4, -3.5, 7, 2, 7), pivot=(-12.3, 2, 0), inflate=0.05)
part('epaulette_left', 'body', (-3, -3.4, -3.5, 7, 2, 7), pivot=(12.3, 2, 0))
part('fringe_left', 'body', (-3, -1.4, -3.5, 7, 2, 7), pivot=(12.3, 2, 0), inflate=0.05)
# bras nus très musclés : biceps et avant-bras en relief ; poing gauche
part('bicep_right', 'right_arm', (-3, 0, -3, 6, 7, 6), inflate=0.5)
part('bicep_left', 'left_arm', (-3, 0, -3, 6, 7, 6), inflate=0.5)
part('forearm_left', 'left_arm', (-3, 9, -3, 6, 5, 6), inflate=0.35)
part('fist_left', 'left_arm', (-3, 14, -3, 6, 3, 6), inflate=0.45)
# pantalon bleu ample à rayures sombres, rentré dans de hautes bottes de cuir lacées (semelle pile au sol : y = 24)
part('pants_right', 'right_leg', (-3.5, 0, -3.5, 7, 11, 7), inflate=0.5)
part('pants_left', 'left_leg', (-3.5, 0, -3.5, 7, 11, 7), inflate=0.5)
part('boot_right', 'right_leg', (-3.5, 11.3, -3.5, 7, 7, 7), inflate=0.3)
part('boot_left', 'left_leg', (-3.5, 11.3, -3.5, 7, 7, 7), inflate=0.3)
part('boot_toe_right', 'right_leg', (-3.5, 16, -5.5, 7, 3, 2))
part('boot_toe_left', 'left_leg', (-3.5, 16, -5.5, 7, 3, 2))

# ---------------------------------------------------------------- la hache, emmanchée au bras droit
# comme sur l'image : l'avant-bras est pris dans une longue gaine effilée et cloutée (fine au coude, épaisse au poignet) ; au
# poignet, la douille du gros fer noir, avec la demi-lune argentée d'un côté, et le manche de bois qui dépasse de l'autre côté
# du fer, dans le prolongement de l'avant-bras. Au repos, le bras plié pose la hache sur l'épaule, derrière la tête, le fer en
# l'air (MorganModel). Le fer et le manche sont tout entiers AU-DELÀ du poignet : ils ne traversent jamais le bras.
part('socket_tip', 'forearm_right', (-2, 0, -2, 4, 5, 4), inflate=0.2)                   # gaine, fine au coude
part('socket', 'forearm_right', (-3, 5, -3, 6, 7, 6), inflate=0.3)                       # gaine, épaisse au poignet
part('axe', 'forearm_right', (-1, 0, -1, 2, 16, 2), pivot=(0, 12, 0))                     # manche de bois
part('axe_head', 'axe', (-2.5, 0, -3, 5, 7, 6), pivot=(0, 0.5, 0))                       # douille du fer, autour du manche
part('axe_blade', 'axe', (-1, 0, -10, 2, 15, 10), pivot=(0, -3.5, -3.0))                 # fer noir
part('axe_edge', 'axe', (-0.75, 0, -3, 1.5, 21, 3), pivot=(0, -6.5, -13.0))              # demi-lune argentée
part('axe_horn_top', 'axe_edge', (-0.75, -3, -2, 1.5, 4, 3), pivot=(0, 0.8, -0.5), rot=(0.6, 0, 0))
part('axe_horn_bottom', 'axe_edge', (-0.75, -1, -2, 1.5, 4, 3), pivot=(0, 20.2, -0.5), rot=(-0.6, 0, 0))


# ---------------------------------------------------------------- placement des cubes dans la texture
def uv_size(p):
    w, h, d = [math.ceil(v) for v in p.box[3:6]]
    return 2 * d + 2 * w, d + h


def pack():
    """Rangement en étagères, les plus hautes d'abord (les membres gauches « share » réutilisent la zone du membre droit, en miroir)."""
    todo = sorted((p for p in PARTS if p.uv is None and p.share is None), key=lambda p: -uv_size(p)[1])
    x, y, row_h = 0, 0, 0
    for p in todo:
        uw, uh = uv_size(p)
        if x + uw > TW:
            x, y, row_h = 0, y + row_h + 1, 0
        assert y + uh <= TH, 'texture trop petite : ' + p.name
        p.uv = (x, y)
        x += uw + 1
        row_h = max(row_h, uh)
    for p in PARTS:
        if p.share:
            p.uv = next(q for q in PARTS if q.name == p.share).uv


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

SOCKET, SOCKET_D = (198, 144, 102, 255), (160, 108, 74, 255)
BROW = (150, 118, 66, 255)


def faces_all(p, fn):
    for f in ('front', 'back', 'right', 'left', 'up', 'down'):
        paint(p, f, lambda i, j, w, h, f=f: fn(i, j, w, h, f))


# --- tête : brosse blonde, visage dur, barbe de quelques jours (la mâchoire d'acier est en 3D)
head = P['head']
fill(head, SKIN)
paint(head, 'up', lambda i, j, w, h: HAIR_D if (i * 3 + j) % 7 == 0 else HAIR)
for f in ('right', 'left'):
    paint(head, f, lambda i, j, w, h: HAIR if j < 3 else STUBBLE if j >= 5 and ((f == 'right' and i < 6) or (f == 'left' and i > 3)) else SKIN_D if j == 9 else SKIN)
paint(head, 'back', lambda i, j, w, h: (HAIR_D if (i + j) % 5 == 0 else HAIR) if j < 5 else SKIN_D if j == 5 else SKIN)
paint(head, 'down', lambda i, j, w, h: STUBBLE)
FACE = [  # 10 x 10, regard de face (colonne 0 = joue droite du personnage)
    'HHHHHHHHHH',
    'ssSSssSSss',   # rides du front
    'sBBssssBBs',   # sourcils épais, froncés
    'ssBBssBBss',
    'sEpSssSpEs',   # petits yeux durs
    'ssSsnnsSss',   # nez
    'dSMMMMMMSd',   # bouche sombre, visible entre les créneaux de la mâchoire
    'dSSSSSSSSd',   # (sous la mâchoire d'acier)
    'ddSSSSSSdd',
    'dSSSSSSSSd',
]
FMAP = {'H': HAIR, 's': SKIN, 'S': SKIN_D, 'B': BROW, 'E': EYE, 'p': PUPIL, 'n': SKIN_D, 'd': STUBBLE, 'M': (92, 52, 46, 255), 'L': (176, 106, 84, 255), 't': (226, 222, 206, 255)}
paint(head, 'front', lambda i, j, w, h: FMAP[FACE[j][i]])
hat = P['hat']                                  # 2e couche : volume des cheveux sur le haut du crâne (transparent ailleurs)
paint(hat, 'up', lambda i, j, w, h: HAIR_D if (i + 2 * j) % 6 == 0 else HAIR)
paint(hat, 'front', lambda i, j, w, h: HAIR if j == 0 else T)
paint(hat, 'right', lambda i, j, w, h: (HAIR_D if (i + j) % 4 == 0 else HAIR) if j < 2 or (j < 4 and i > 4) else T)
paint(hat, 'left', lambda i, j, w, h: (HAIR_D if (i + j) % 4 == 0 else HAIR) if j < 2 or (j < 4 and i < 5) else T)
paint(hat, 'back', lambda i, j, w, h: (HAIR_D if (i * 2 + j) % 5 == 0 else HAIR) if j < 4 else T)
paint(hat, 'down', lambda i, j, w, h: T)
for n in ('hair_top', 'hair_front'):
    fill(P[n], HAIR)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: HAIR_D if (i * 3 + j) % 5 == 0 else HAIR_L if (i + j) % 7 == 3 else HAIR)
    paint(P[n], 'up', lambda i, j, w, h: HAIR_D if i % 3 == 0 else HAIR_L if (i + j) % 5 == 0 else HAIR)   # coiffé en arrière
    fill(P[n], T, ('down',))

# --- mâchoire d'acier : plaque grise, rebord clair, mouette de la Marine gravée, boulons ronds
GULL = ['.XX.XX.']                                     # mouette de la Marine (deux ailes), gravure discrète


def plate(i, j, w, h):
    if j == h - 1:
        return STEEL_D
    if j == 2:
        gi = i - (w - 7) // 2
        if 0 <= gi < 7 and GULL[0][gi] == 'X':
            return STEEL_D
    if i in (0, w - 1):
        return STEEL_D
    return STEEL_L if j == 0 else STEEL


fill(P['jaw'], STEEL)
paint(P['jaw'], 'front', plate)
fill(P['jaw'], STEEL_L, ('up',))
fill(P['jaw_round'], STEEL)
paint(P['jaw_round'], 'front', lambda i, j, w, h: STEEL_D)
fill(P['jaw_under'], STEEL_D)
for k in range(4):
    fill(P[f'jaw_tooth_{k}'], STEEL)
    paint(P[f'jaw_tooth_{k}'], 'front', lambda i, j, w, h: STEEL_L if j == 0 else STEEL)
    fill(P[f'jaw_tooth_{k}'], STEEL_L, ('up',))
for n in ('jaw_side_right', 'jaw_side_left'):
    fill(P[n], STEEL_D)
for n in ('jaw_bolt_right', 'jaw_bolt_left'):
    fill(P[n], STEEL_D)
    paint(P[n], 'front', lambda i, j, w, h: STEEL_L)
for side in ('right', 'left'):
    for n in (f'jaw_strap_{side}',):
        fill(P[n], STEEL)
        fill(P[n], STEEL_L, ('front', 'up'))
    fill(P[f'jaw_anchor_{side}'], STEEL_D)
    paint(P[f'jaw_anchor_{side}'], side, lambda i, j, w, h: STEEL_L if (i, j) == (0, 0) else STEEL)   # tête du boulon

# --- torse : gilet bleu nuit au décolleté profond, torse musclé, boutons
body = P['body']
fill(body, VEST)


def torso(i, j, w, h):
    c = (w - 1) / 2.0
    if j >= 15:
        return PANTS_D if j == 15 else PANTS          # taille du pantalon
    half = 5.2 - j * 0.62                             # décolleté en V
    if j < 9 and abs(i - c) < half:
        if abs(i - c) < 0.6 and j >= 2:
            return SKIN_D                             # sillon entre les pectoraux
        if j == 4 and 1.5 < abs(i - c) < half - 0.5:
            return SKIN_D                             # bas des pectoraux
        return SKIN_L if j < 2 else SKIN
    if j < 9 and abs(i - c) < half + 1:
        return VEST_L                                 # bord du gilet
    if abs(i - c) < 0.6 and j % 2 == 1:
        return BUTTON
    return VEST_D if i in (0, w - 1) else VEST


paint(body, 'front', torso)
for f in ('back', 'right', 'left'):
    paint(body, f, lambda i, j, w, h: PANTS if j >= 15 else VEST_D if j == 0 else VEST)
fill(body, SKIN, ('up',))

# --- bras nus, très musclés
def arm_skin(i, j, w, h, f):
    if f in ('up',):
        return SKIN
    if f == 'down':
        return SKIN_D
    if j in (8, 9) or (i == w // 2 and 10 <= j <= 15 and f in ('front', 'back')):
        return SKIN_D                                 # pli du coude, tendon de l'avant-bras
    if j == 1:
        return SKIN_L
    return SKIN


for n in ('right_arm', 'forearm_right', 'left_arm'):
    faces_all(P[n], arm_skin)
for n in ('bicep_right', 'bicep_left'):
    faces_all(P[n], lambda i, j, w, h, f: SKIN_L if f in ('front', 'right', 'left') and 1 <= i <= w - 2 and 1 <= j <= 3
              else SKIN_D if j == h - 1 or (f in ('front', 'back') and i in (0, w - 1)) else SKIN)
    fill(P[n], T, ('up',))
fill(P['forearm_left'], SKIN)
for f in ('front', 'back', 'right', 'left'):
    paint(P['forearm_left'], f, lambda i, j, w, h: SKIN_D if i == w // 2 or j == h - 1 else SKIN)
fill(P['fist_left'], SKIN)
paint(P['fist_left'], 'front', lambda i, j, w, h: SKIN_D if j == 0 or (j == 1 and i % 2 == 0) else SKIN_L if j == 2 else SKIN)
paint(P['fist_left'], 'down', lambda i, j, w, h: SKIN_D if i % 2 == 0 else SKIN)

# --- épaulettes dorées à franges (comme celles de Roger)
for n in ('epaulette_right', 'epaulette_left'):
    fill(P[n], GOLD)
    paint(P[n], 'up', lambda i, j, w, h: GOLD_L if (i + j) % 3 == 0 else GOLD)
for n in ('fringe_right', 'fringe_left'):
    fill(P[n], T, ('up', 'down'))
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: (GOLD if i % 2 == 0 else GOLD_D) if j == 0 or i % 2 == 0 else T)

# --- jambes : pantalon bleu à rayures sombres, hautes bottes de cuir lacées
def stripes(i, j):
    k = (i * 2 + j * 3 + (j // 4) * 5) % 9
    return PANTS_D if k in (0, 1) else PANTS_L if k == 5 else PANTS


def boot(i, j, w, h, f):
    if j == 0:
        return BOOT_D                                 # haut de la tige
    if f == 'front' and 1 <= i <= w - 2:
        mid = (w - 1) / 2.0
        if abs(i - mid) <= 0.6 and j % 2 == 1:
            return LACE                               # laçage croisé
        if abs(abs(i - mid) - 1.5) <= 0.6 and j % 2 == 0:
            return LACE
    return BOOT_L if (i + j) % 6 == 0 else BOOT


def leg(i, j, w, h, f):
    if f == 'up':
        return PANTS
    if f == 'down':
        return LACE
    if j >= 11:
        return boot(i, j - 11, w, h, f)
    return stripes(i, j)


faces_all(P['right_leg'], leg)
for n in ('pants_right', 'pants_left'):
    fill(P[n], PANTS)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: PANTS_D if j == h - 1 else stripes(i + 3, j))
    fill(P[n], T, ('up', 'down'))
for n in ('boot_right', 'boot_left'):
    fill(P[n], BOOT)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h, f=f: boot(i, j, w, h, f))
    fill(P[n], T, ('up',))
    fill(P[n], LACE, ('down',))
for n in ('boot_toe_right', 'boot_toe_left'):
    fill(P[n], BOOT)
    paint(P[n], 'up', lambda i, j, w, h: BOOT_L if j == 0 else BOOT)
    fill(P[n], LACE, ('down',))
    paint(P[n], 'front', lambda i, j, w, h: BOOT_D if j == h - 1 else BOOT)

# --- manteau bleu clair sans manches : plis, ourlet, doublure magenta, revers cloutés
def cloth(i, j, w, h):
    if j == h - 1:
        return COAT_D                                 # ourlet
    if (i * 7 + j // 5) % 9 == 0:
        return COAT_D                                 # plis
    if (i * 5 + j // 4) % 11 == 4:
        return COAT_L
    return COAT


def lining(i, j, w, h):
    return LINING_D if (i * 3 + j // 4) % 7 == 0 else LINING


for n, outer, inner in (('coat_back', 'back', 'front'), ('skirt_back', 'back', 'front'),
                        ('coat_side_right', 'right', 'left'), ('skirt_side_right', 'right', 'left'),
                        ('coat_side_left', 'left', 'right'), ('skirt_side_left', 'left', 'right')):
    fill(P[n], COAT)
    paint(P[n], outer, cloth)
    paint(P[n], inner, lining)
for n, edge in (('coat_front_right', 'right_edge'), ('skirt_front_right', 'right_edge'), ('coat_front_left', 'left_edge'),
                ('skirt_front_left', 'left_edge')):
    fill(P[n], COAT)

    def panel(i, j, w, h, n=n):
        inner_col = w - 1 if n.endswith('right') else 0      # bord de l'ouverture (côté torse) : revers magenta
        if i == inner_col:
            return LINING
        if abs(i - inner_col) == 1:
            return STUD if j % 3 == 1 else LINING_D           # clous le long du revers
        return cloth(i, j, w, h)

    paint(P[n], 'front', panel)
    paint(P[n], 'back', lining)
    fill(P[n], LINING, ('right', 'left'))
for n in ('coat_back', 'coat_side_right', 'coat_side_left', 'coat_front_right', 'coat_front_left'):
    fill(P[n], COAT_L, ('up',))
for n in ('skirt_back', 'skirt_side_right', 'skirt_side_left', 'skirt_front_right', 'skirt_front_left'):
    fill(P[n], COAT_D, ('down',))
fill(P['coat_collar'], COAT_L)
paint(P['coat_collar'], 'front', lambda i, j, w, h: COAT_L if j == 0 else LINING if j > 1 else LINING_D)   # magenta dedans
for n in ('collar_right', 'collar_left'):
    fill(P[n], COAT_L)
    inner = 'left' if n == 'collar_right' else 'right'
    paint(P[n], inner, lambda i, j, w, h: COAT_L if j == 0 else LINING)
fill(P['coat_belt'], COAT_D)
for f in ('back', 'right', 'left'):
    paint(P['coat_belt'], f, lambda i, j, w, h: COAT if j == 0 else COAT_D)
paint(P['coat_belt'], 'front', lambda i, j, w, h: (COAT if j == 0 else COAT_D) if i <= 4 or i >= w - 5 else T)   # ouvert devant
fill(P['coat_belt'], T, ('up', 'down'))
fill(P['coat_buckle'], GOLD)
paint(P['coat_buckle'], 'front', lambda i, j, w, h: GOLD_L if (i, j) == (0, 0) else GOLD_D if (i, j) == (1, 1) else GOLD)

# --- la hache : gaine cloutée, manche de bois, fer noir, demi-lune argentée
for n in ('socket', 'socket_tip'):
    fill(P[n], SOCKET)
    for f in ('front', 'back', 'right', 'left'):
        paint(P[n], f, lambda i, j, w, h: SOCKET_D if j == h - 1 else SOCKET)
paint(P['socket'], 'right', lambda i, j, w, h: STEEL_DD if (j == 2 and i in (2, 4)) else SOCKET_D if j == 5 else SOCKET)   # clous
fill(P['axe'], WOOD)
for f in ('front', 'back', 'right', 'left'):
    paint(P['axe'], f, lambda i, j, w, h: WOOD_D if (i + j) % 4 == 0 else WOOD)
fill(P['axe_head'], IRON)
for f in ('front', 'back', 'right', 'left'):
    paint(P['axe_head'], f, lambda i, j, w, h: IRON_L if j == 0 else IRON_D if j == h - 1 else IRON)
fill(P['axe_blade'], IRON)
for f in ('right', 'left'):
    paint(P['axe_blade'], f, lambda i, j, w, h: IRON_L if (i + j) % 7 == 0 or j == 0 else IRON_D if j == h - 1 else IRON)
for n in ('axe_edge', 'axe_horn_top', 'axe_horn_bottom'):
    fill(P[n], EDGE)
    paint(P[n], 'right', lambda i, j, w, h: EDGE_D if i == 0 else EDGE)        # ligne plus sombre côté fer, fil poli devant
    paint(P[n], 'left', lambda i, j, w, h: EDGE_D if i == w - 1 else EDGE)


os.makedirs(os.path.dirname(PNG), exist_ok=True)
img.save(PNG)


# ---------------------------------------------------------------- modèle Java
def jf(v):
    return f'{float(v)}F'


def dim(v):
    """Taille d'un cube : entier si possible, sinon float."""
    return str(int(v)) if float(v).is_integer() else jf(round(v, 4))


def java():
    out = []
    for p in PARTS:
        parent = 'root' if p.parent is None else p.parent
        x, y, z, w, h, d = p.box
        deform = 'CubeDeformation.NONE' if not p.inflate else f'new CubeDeformation({jf(p.inflate)})'
        if p.rot == (0, 0, 0):
            pose = 'PartPose.ZERO' if p.pivot == (0, 0, 0) else f'PartPose.offset({", ".join(jf(v) for v in p.pivot)})'
        else:
            pose = f'PartPose.offsetAndRotation({", ".join(jf(v) for v in p.pivot)}, {", ".join(jf(round(v, 4)) for v in p.rot)})'
        mirror = '.mirror()' if p.mirror else ''
        out.append(f'        PartDefinition {p.name} = {parent}.addOrReplaceChild("{p.name}", CubeListBuilder.create().texOffs({p.uv[0]}, {p.uv[1]}){mirror}\n'
                   f'                .addBox({jf(x)}, {jf(y)}, {jf(z)}, {dim(w)}, {dim(h)}, {dim(d)}, {deform}), {pose});')
    body = '\n'.join(out)
    return f'''package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Géométrie de Morgan « main de hache », capitaine de la Marine : même style que Gol D. Roger et Silvers Rayleigh (corps
 * humanoïde + équipement 3D attaché aux membres, 1 texel par pixel), mais conçue à 48 px de haut = 3 blocs (pieds à y = 24,
 * sommet du crâne à y = -24) : tête 10, torse 16 x 17 x 8, bras de 19 (6 d'épaisseur, très écartés), jambes de 19 (7 d'épaisseur).
 * Mêmes noms de pièces que HumanoidModel (head, hat, body, bras, jambes). Texture morgan.png (256x128).
 * FICHIER GÉNÉRÉ par src/blockbench/make_morgan.py (avec la texture et src/blockbench/morgan.bbmodel). Animations : MorganModel.
 *
 * <ul>
 *   <li>Tête : cheveux blonds courts plaqués, sourcils froncés, barbe de quelques jours, mâchoire inférieure d'acier (plaque de la
 *       largeur du visage, créneaux carrés en haut, boulons ronds, mouette de la Marine ;
 *       accroches par en dessous, le long de la mâchoire jusqu'aux boulons sous les oreilles).</li>
 *   <li>Grand manteau bleu clair sans manches, ouvert, doublure et revers magenta cloutés, très haut col, ceinture à boucle
 *       dorée ; épaulettes dorées à franges ; ses pans (skirt_*) s'écartent avec les jambes.</li>
 *   <li>Gilet bleu nuit au décolleté profond ; bras nus très musclés ; pantalon bleu à rayures sombres ; hautes bottes lacées.</li>
 *   <li>Bras droit : gaine cloutée, manche de bois et énorme fer noir à demi-lune argentée (pièce « axe », pivot au poignet).</li>
 * </ul>
 */
public final class MorganModels {{

    public static final ModelLayerLocation MORGAN = new ModelLayerLocation(BloxPiece.id("morgan"), "main");

    private MorganModels() {{
    }}

    public static LayerDefinition morgan() {{
        MeshDefinition mesh = new MeshDefinition();
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
            'name': 'morgan', 'model_identifier': 'morgan', 'modded_entity_entity_class': 'Morgan',
            'modded_entity_version': 'Mojmaps', 'visible_box': [1, 2, 0], 'resolution': {'width': TW, 'height': TH},
            'elements': elements, 'outliner': roots,
            'textures': [{'name': 'morgan.png', 'id': '0', 'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH,
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
                pts.append((size[0] / 2 + rx * scale, 110 + (Y + 26) * scale))
                depth += rz
            polys.append((depth / 4, pts, tuple(min(255, int(k * shade[f])) for k in c[:3])))
    for _, pts, c in sorted(polys, key=lambda q: -q[0]):
        dr.polygon(pts, fill=c)
    im.save(path)




def pose(**rots):
    """Applique des rotations (nom de pièce -> (x, y, z)) et renvoie l'état précédent, pour les aperçus des poses."""
    old = {n: P[n].rot for n in rots}
    for n, r in rots.items():
        P[n].rot = r
    return old


if len(sys.argv) > 1:
    out = sys.argv[1]
    # poses clés (mêmes valeurs que MorganModel) : repos (hache sur l'épaule), préparation, impact, rotation, coup au sol
    REST = ((-1.088, -1.872, -3.14), (-0.635, 2.888, -2.643))
    PREP = ((0.648, -1.032, -4.044), (-2.039, 2.113, -1.946))
    HIT = ((1.557, -3.245, -4.71), (-1.801, 1.689, -1.694))
    SPIN = ((-1.137, -1.536, -3.667), (-1.944, 1.63, -1.991))
    SLAM = ((1.616, -4.138, -4.689), (-1.405, 1.593, -1.674))
    old = pose(right_arm=REST[0], forearm_right=REST[1])
    for tag, (arm, fore) in (('repos', REST), ('prep', PREP), ('impact', HIT), ('rotation', SPIN), ('sol', SLAM)):
        pose(right_arm=arm, forearm_right=fore)
        render(os.path.join(out, f'morgan_{tag}_face.png'), 0.0, scale=12, size=(520, 760))
        render(os.path.join(out, f'morgan_{tag}_profil.png'), -1.2, scale=12, size=(520, 760))
    pose(**old)
    img.resize((TW * 4, TH * 4), Image.NEAREST).save(os.path.join(out, 'morgan_texture.png'))
print('ok :', len(PARTS), 'parties')
