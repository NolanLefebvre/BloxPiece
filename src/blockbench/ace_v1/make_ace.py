# ANCIEN modèle de l'Ace (v1), archivé : remplacé par src/blockbench/ace_v2/make_ace_v2.py. NE PAS relancer (il écraserait models/item/ace.json).
# Ace, le sabre de Gol D. Roger : génère le modèle 3D (models/item/ace.json, format « Java Block/Item » : s'ouvre tel quel
# dans Blockbench), ses 3 textures 16x16 (lame, or, poignée) et un aperçu. Même méthode que les sabres des pirates et de la
# Marine : le sabre est construit droit (axe y), puis tous les éléments sont tournés de -45° autour de z (diagonale d'icône).
# Usage (depuis la racine du projet) : python3 src/blockbench/make_ace.py [dossier_apercu]
import json, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/item')
MODEL = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/models/item/ace.json')
ITEM = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/items/ace.json')

# ---------------------------------------------------------------- textures (d'après les images de référence)
def tex(name, fn):
    im = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), fn(x, y))
    im.save(os.path.join(TEX, name + '.png'))
    return im


# lame : acier clair légèrement bleuté ; les colonnes = la largeur de la lame (dos sombre -> gouttière -> plat -> fil brillant).
# Aucune variation sur la hauteur : la lame paraît d'une seule pièce, sans couture entre ses éléments.
# D'après l'image de référence (anime) : lame blanche, une bande bleu-acier le long du dos, liseré sombre au dos, fil
# blanc éclatant bordé d'un fin trait gris. Les colonnes = la largeur de la lame (dos -> fil) ; rien ne varie sur la hauteur :
# le motif est identique sur toute la lame, pointe comprise.
BLADE_COLS = [(92, 102, 120),                                                      # liseré du dos
              (150, 166, 188), (166, 182, 202), (176, 192, 210), (186, 200, 216),  # bande bleu-acier
              (226, 232, 240),                                                     # transition
              (244, 246, 250), (246, 248, 251), (248, 249, 252), (248, 249, 252), (246, 248, 251), (244, 246, 250),
              (240, 243, 248), (236, 240, 246),                                    # plat blanc
              (250, 251, 253), (206, 212, 222)]                                    # fil éclatant, trait du tranchant
blade = tex('ace_blade', lambda x, y: BLADE_COLS[x] + (255,))


def gold(x, y):
    """Or poli : un seul reflet doux en diagonale, bords légèrement plus sombres (pas de bruit)."""
    d = abs((x + y) - 15)
    base = (252, 222, 120) if d <= 1 else (232, 186, 62) if d <= 5 else (204, 154, 40)
    if x in (0, 15) or y in (0, 15):
        base = tuple(int(c * 0.86) for c in base)
    return base + (255,)


goldim = tex('ace_gold', gold)


def grip(x, y):
    """Poignée : habillage doré, cordon enroulé en spirale régulière d'un brun doré doux (pas de points sombres),
    ombre sous le cordon et reflet au-dessus pour le relief."""
    k = (x + 2 * y) % 8
    if k in (0, 1):
        return (176, 120, 44, 255)          # cordon
    if k == 2:
        return (150, 100, 36, 255)          # ombre portée du cordon
    if k == 7:
        return (250, 214, 112, 255)         # reflet
    return (230, 182, 62, 255) if x not in (0, 15) else (210, 162, 48, 255)


gripim = tex('ace_grip', grip)

# ---------------------------------------------------------------- géométrie (sabre droit, axe y ; tranchant côté +x)
# Tout est centré sur l'axe de la lame (x = 8.2, z = 8) : garde, poignée et pommeau symétriques et alignés.
E = []
BX0, BX1 = 7.4, 9.0          # largeur de la lame (dos -> fil)


def el(f, t, texture, comment, angle=-45.0, origin=(8.0, 8.0, 8.0), ref=None, urange=None):
    """Un cube. Par défaut tourné de -45° autour de z (diagonale d'icône) ; la lame a ses propres angles (courbure)."""
    E.append({'f': f, 't': t, 'tx': texture, 'c': comment, 'angle': angle, 'origin': origin, 'ref': ref, 'urange': urange})


# pommeau : petit et fin — col, bouton en poire, perle au bout
el((7.7, -1.6, 7.7), (8.7, -1.3, 8.3), 'gold', 'pommeau : col')
el((7.6, -2.3, 7.4), (8.8, -1.6, 8.6), 'gold', 'pommeau : bouton')
el((7.9, -2.55, 7.7), (8.5, -2.3, 8.3), 'gold', 'pommeau : perle')
# poignée : deux bagues fines aux extrémités, trois anneaux en relief sur le cordon enroulé
el((7.5, -1.3, 7.3), (8.9, -1.05, 8.7), 'gold', 'bague basse')
el((7.55, -1.05, 7.35), (8.85, 3.3, 8.65), 'grip', 'poignée')
for y in (0.25, 1.4, 2.55):
    el((7.5, y, 7.3), (8.9, y + 0.2, 8.7), 'gold', 'anneau en relief')
el((7.5, 3.3, 7.3), (8.9, 3.55, 8.7), 'gold', 'bague haute')
# garde d'après l'image de référence : une rondelle plate contre la main, puis une grosse coquille dorée en dôme (bord
# large côté main, sommet arrondi côté lame), parfaitement ronde (chaque étage = une croix de deux pavés : un octogone),
# centrée sur l'axe de la lame (x = 8.2, z = 8) ; la grande branche de garde en D est construite plus bas (pièces inclinées)
CX, CZ = 8.2, 8.0


def disc(y0, y1, r, comment):
    k = r * 0.62
    el((CX - r, y0, CZ - k), (CX + r, y1, CZ + k), 'gold', comment)
    el((CX - k, y0, CZ - r), (CX + k, y1, CZ + r), 'gold', comment)


disc(3.35, 3.6, 1.35, 'rondelle')
for (y0, y1, r) in ((3.6, 3.85, 2.25), (3.85, 4.15, 2.15), (4.15, 4.45, 1.95), (4.45, 4.75, 1.65), (4.75, 5.05, 1.25),
                    (5.05, 5.3, 0.85)):
    disc(y0, y1, r, 'coquille en dôme')

# lame d'un seul tenant, légèrement courbe : des pans successifs, chacun un peu plus incliné que le précédent
# (le dos se creuse régulièrement jusqu'à la pointe), qui pivotent exactement sur leur point de jonction et se recouvrent
# d'un dixième de pixel pour ne laisser aucune fente ; même dessin de texture sur toute la largeur (aucune couture visible).
# Chaque cube ne peut avoir qu'une rotation : la courbure et la diagonale d'icône (-45°) sont combinées en une seule.
import math


def rot2(x, y, deg):
    a = math.radians(deg)
    return x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)


def combined(alpha, joint):
    """Rotation unique (angle, centre) = courbure alpha autour de joint, puis -45° autour de (8, 8)."""
    theta = -45.0 + alpha
    px, py = joint
    rx, ry = rot2(px, py, alpha)
    cx, cy = rot2(px - rx - 8, py - ry - 8, -45.0)
    cx, cy = cx + 8, cy + 8
    # q tel que q - R(theta) q = c
    a = math.radians(theta)
    m00, m01, m10, m11 = 1 - math.cos(a), math.sin(a), -math.sin(a), 1 - math.cos(a)
    det = m00 * m11 - m01 * m10
    qx, qy = (cx * m11 - m01 * cy) / det, (m00 * cy - m10 * cx) / det
    return round(theta, 3), (round(qx, 4), round(qy, 4), 8.0)


HALF = (BX1 - BX0) / 2
# corps de la lame : 7 pans de même longueur, chacun 2° plus incliné que le précédent. La courbure est répartie
# régulièrement de la garde à la pointe ; à chaque jonction la direction ne change que de 2° (aucun angle perceptible).
BODY_LEN, STEP, NSEG = 13.7, 2.2, 10                           # corps : 10 pans, +2,2° à chaque jonction
SEGMENTS = [(BODY_LEN / NSEG, STEP * k) for k in range(NSEG)]
joint = (BX0 + HALF, 4.85)
BLADE_Z = 0.15                                                  # même épaisseur partout : aucune marche sur la tranche
for k, (length, alpha) in enumerate(SEGMENTS):
    angle, origin = combined(alpha, joint)
    jx, jy = joint
    # pans qui se recouvrent très légèrement (pas de fente à la jonction) ; un pan sur deux plus épais de 2 millièmes
    # seulement (invisible) pour que deux faces ne soient jamais au même plan
    z = BLADE_Z + (0.002 if k % 2 else 0.0)
    lo = 0.0 if k == 0 else -0.06
    el((round(jx - HALF, 4), round(jy + lo, 4), 8 - z), (round(jx + HALF, 4), round(jy + length, 4), 8 + z),
       'blade', 'lame', angle, origin, ref=jx - HALF)
    dx, dy = rot2(0.0, length, alpha)
    joint = (jx + dx, jy + dy)
last_alpha = SEGMENTS[-1][1]
to_u = lambda d: round(d / (BX1 - BX0) * 16, 3)                 # distance (blocs) -> colonnes de texture, échelle de la lame

# Pointe : PAS de triangle ajouté. La lame continue sur ses derniers pans en poursuivant sa courbe (et même un peu plus,
# +3° par pan, comme le bout relevé de l'image), et c'est sa largeur qui diminue progressivement : le dos suit la courbe
# sans aucune cassure, le tranchant remonte en douceur vers lui (largeur 1,6 x (1 - t²) : départ tangent, bout effilé).
# Chaque pan = une bande côté dos (largeur restante, colonnes comptées depuis le dos : liseré + bande bleue) et une fine
# bande côté fil, légèrement inclinée, qui porte la ligne du tranchant d'un pan à l'autre (colonnes comptées depuis le
# fil). Même échelle de texture que la lame ; la bande du dos passe devant ; aucune face au même plan.
BAND = 0.5                                                      # largeur de la bande du dos (liseré + bleu) : 5 colonnes
TIP_LEN, TIP_CURVE = 5.1, 3.5                                   # longueur de la pointe ; courbure : degrés par bloc de modèle
width = lambda t: max(0.05, (BX1 - BX0) * (1 - t * t))
# découpage de plus en plus fin vers le bout : la bande du fil ne doit jamais être plus large que ce qui reste de lame
cuts, t = [0.0], 0.0
while t < 1.0 - 1e-6:
    dt = 0.2
    # la bande du fil (au plus la largeur de fin : elle ne dépasse jamais du dos) doit couvrir ce que la lame perd sur le pan
    while dt > 0.01 and width(t) - width(min(1.0, t + dt)) > width(min(1.0, t + dt)):
        dt *= 0.8
    t_band = math.sqrt(1 - BAND / (BX1 - BX0))                 # là où la lame n'est plus large que la bande du dos
    if t < t_band < t + dt:
        dt = t_band - t                                         # une jonction pile à cet endroit : le blanc finit en pointe
    t = min(1.0, t + dt)
    cuts.append(t)
alpha = last_alpha
for i, (t0, t1) in enumerate(zip(cuts, cuts[1:])):
    TIP_SEG_LEN = (t1 - t0) * TIP_LEN
    alpha += TIP_CURVE * TIP_SEG_LEN
    jx, jy = joint
    w0, w1 = width(t0), width(t1)
    spine_x = jx - HALF
    z_spine, z_edge = BLADE_Z, BLADE_Z - 0.006                    # le fil juste en retrait sous la bande du dos
    # bande du dos : une bande de LARGEUR CONSTANTE (le liseré et la bande bleue) qui suit le dos jusqu'au bout, sans marche ;
    # elle ne se rétrécit que là où la lame devient plus fine qu'elle
    band = min(w1, BAND)
    angle, origin = combined(alpha, joint)
    z_spine += 0.002 * ((i + NSEG) % 2)
    el((round(spine_x, 4), round(jy - 0.06, 4), 8 - z_spine), (round(spine_x + band, 4), round(jy + TIP_SEG_LEN, 4), 8 + z_spine),
       'blade', 'pointe : dos', angle, origin, urange=(0, to_u(band)))
    # bande du fil : sa ligne extérieure va de la largeur de début à la largeur de fin du pan
    dw = w0 - w1
    tilt = math.degrees(math.atan2(dw, TIP_SEG_LEN))
    sw = min(w0 - band + 0.05, w1)                               # du fil jusque sous la bande du dos, sans dépasser le dos
    px, py = rot2(spine_x + w0 - jx, 0.0, alpha)
    pivot = (jx + px, jy + py)
    angle, origin = combined(alpha + tilt, pivot)
    L = math.hypot(dw, TIP_SEG_LEN)
    el((round(pivot[0] - sw, 4), round(pivot[1] - 0.12, 4), 8 - z_edge), (round(pivot[0], 4), round(pivot[1] + L, 4), 8 + z_edge),
       'blade', 'pointe : fil', angle, origin,
       # tout au bout, la lame n'est plus que la bande du dos : le fil prend les mêmes couleurs (aucune marche)
       urange=(round(16 - to_u(sw), 3), 16) if w1 >= BAND - 1e-6 else (round(to_u(BAND) - to_u(sw), 3), to_u(BAND)))
    dx, dy = rot2(0.0, TIP_SEG_LEN, alpha)
    joint = (jx + dx, jy + dy)


# grande branche de garde en D (comme sur l'image) : un arc doré qui part du col du pommeau, s'écarte largement côté
# tranchant et rejoint le bord de la coquille. Arc fait de barres inclinées bout à bout (recouvrement aux jonctions) ;
# chaque barre est orientée en long (y) ou en large (x) pour que son angle reste dans les limites de Minecraft.
BOW = [(8.75, -1.75), (9.9, -1.55), (10.85, -0.75), (11.35, 0.45), (11.4, 1.7), (11.05, 2.85), (10.35, 3.7)]
T = 0.24                                                        # demi-épaisseur de la branche
for (x0, y0), (x1, y1) in zip(BOW, BOW[1:]):
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) + 0.3
    a = math.degrees(math.atan2(-dx, dy))                       # angle qui amène l'axe y du cube sur la barre
    if -45.0 + a < -45.0:                                       # trop incliné : on pose la barre en long sur x
        a_x = math.degrees(math.atan2(dy, dx))
        angle, origin = combined(a_x, (x0, y0))
        el((round(x0 - 0.15, 4), round(y0 - T, 4), CZ - T), (round(x0 + L - 0.15, 4), round(y0 + T, 4), CZ + T), 'gold',
           'branche de garde', angle, origin)
    else:
        angle, origin = combined(a, (x0, y0))
        el((round(x0 - T, 4), round(y0 - 0.15, 4), CZ - T), (round(x0 + T, 4), round(y0 + L - 0.15, 4), CZ + T), 'gold',
           'branche de garde', angle, origin)


def faces_for(e):
    f, t, tx = e['f'], e['t'], e['tx']
    """UV : pour la lame, chaque face reprend les colonnes de la texture qui correspondent à sa position dans la largeur
    (la pointe continue exactement le dessin de la lame) ; ailleurs, la texture entière."""
    if tx == 'gold':
        # toute la monture dorée partage une seule projection de la texture : le reflet se prolonge d'une pièce à l'autre
        U = lambda v, lo, hi: round(min(16, max(0, (v - lo) / (hi - lo) * 16)), 3)
        ux0, ux1 = U(f[0], 6.0, 10.6), U(t[0], 6.0, 10.6)
        vy0, vy1 = U(5.6 - t[1], 0, 8.6), U(5.6 - f[1], 0, 8.6)
        uz0, uz1 = U(f[2], 6.0, 10.0), U(t[2], 6.0, 10.0)
        g = lambda uv: {'uv': uv, 'texture': '#gold'}
        # face nord vue de face : l'est (x grand) est à gauche ; face sud : l'ouest est à gauche
        return {'north': g([ux1, vy0, ux0, vy1]), 'south': g([ux0, vy0, ux1, vy1]), 'east': g([uz0, vy0, uz1, vy1]),
                'west': g([uz1, vy0, uz0, vy1]), 'up': g([ux0, uz0, ux1, uz1]), 'down': g([ux0, uz1, ux1, uz0])}
    if tx != 'blade':
        return {k: {'uv': [0, 0, 16, 16], 'texture': '#' + tx} for k in ('north', 'east', 'south', 'west', 'up', 'down')}
    if e.get('urange') is not None:                                 # pointe : les colonnes de son bord de lame
        u0, u1 = e['urange']
        b = lambda uv: {'uv': uv, 'texture': '#blade'}
        # seulement les faces visibles : les deux plats, et la tranche extérieure (le dos pour une bande du dos, le fil
        # pour une bande du fil). Les tranches intérieures et les bouts, cachés dans la lame, ne sont pas dessinés : plus
        # de petites marches ni de lignes sombres sur la pointe.
        faces = {'north': b([u1, 0, u0, 16]), 'south': b([u0, 0, u1, 16])}      # dos (x petit) à droite vu du nord
        if 'dos' in e['c']:
            faces['west'] = b([u0, 0, min(16, u0 + 1), 16])
        else:
            faces['east'] = b([max(0, u1 - 1), 0, u1, 16])
        return faces
    ref = e['ref']
    u0 = round(max(0, (f[0] - ref) / (BX1 - BX0) * 16), 3)
    u1 = round(min(16, (t[0] - ref) / (BX1 - BX0) * 16), 3)
    # pans de la lame : les bouts (haut / bas) sont cachés dans le pan voisin ou dans la garde : non dessinés (sinon une
    # fine ligne apparaît à chaque jonction)
    # face nord vue de face : le dos (x petit) est à droite -> colonnes inversées ; face sud : dos à gauche
    return {'north': {'uv': [u1, 0, u0, 16], 'texture': '#blade'}, 'south': {'uv': [u0, 0, u1, 16], 'texture': '#blade'},
            'west': {'uv': [max(0, u0), 0, min(16, u0 + 1), 16], 'texture': '#blade'},
            'east': {'uv': [15, 0, 16, 16], 'texture': '#blade'}}


def place(e, x, y):
    """Position finale (x, y) d'un point du cube après sa rotation propre autour de z."""
    ox, oy = e['origin'][0], e['origin'][1]
    rx, ry = rot2(x - ox, y - oy, e['angle'])
    return rx + ox, ry + oy


def unplace(e, x, y):
    ox, oy = e['origin'][0], e['origin'][1]
    rx, ry = rot2(x - ox, y - oy, -e['angle'])
    return rx + ox, ry + oy


def framing(scale, extra, mirror=False):
    """Cadrage d'un contexte sans rotation (inventaire, sol) : l'épée entière, mise à l'échelle et recentrée sur la case
    (son centre réel, calculé d'après tous les cubes, n'est pas le centre du modèle 8, 8, 8)."""
    xs, ys = [], []
    for e in E:
        for x in (e['f'][0], e['t'][0]):
            for y in (e['f'][1], e['t'][1]):
                px, py = place(e, x, y)
                xs.append(px); ys.append(py)
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    tx = (cx - 8) * scale if mirror else -(cx - 8) * scale
    return {'rotation': [0, 180 if mirror else 0, 0], 'translation': [round(tx + extra[0], 3), round(-(cy - 8) * scale + extra[1], 3),
            extra[2]], 'scale': [scale] * 3}


def model():
    elements = [{'__comment': e['c'], 'from': list(e['f']), 'to': list(e['t']),
                 'rotation': {'angle': e['angle'], 'axis': 'z', 'origin': list(e['origin'])}, 'faces': faces_for(e)} for e in E]
    return {
        'textures': {'blade': 'bloxpiece:item/ace_blade', 'gold': 'bloxpiece:item/ace_gold', 'grip': 'bloxpiece:item/ace_grip',
                     'particle': 'bloxpiece:item/ace_blade'},
        # Tenue en main : même placement qu'une épée vanilla (poignée dans la main, lame dans son prolongement vers le haut),
        # mais tournée d'un demi-tour autour de l'axe de sa propre lame : le tranchant et la courbure sont tournés vers
        # l'avant / le bas (comme un sabre que l'on tient), et non vers le haut. Rotation calculée = rotation d'épée vanilla
        # [0, -90, 55] (3e personne) / [0, -90, 25] (1re personne) composée avec ce demi-tour ; main gauche = miroir.
        'display': {
            'thirdperson_righthand': {'rotation': [-180, 90, -145], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
            'thirdperson_lefthand': {'rotation': [-180, -90, 145], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
            # 1re personne : réglée avec une simulation de la vue du jeu (bras, objet, perspective ; vérifiée sur les captures).
            # L'épée est dressée dans le coin bas-droit, face à la caméra, la courbe vers le centre de l'écran : on voit la
            # lame entière, la coquille, la poignée et la branche de garde. Main gauche = miroir.
            'firstperson_righthand': {'rotation': [0, -80, 25], 'translation': [0, 5.0, 0], 'scale': [0.55, 0.55, 0.55]},
            'firstperson_lefthand': {'rotation': [0, 80, -25], 'translation': [0, 5.0, 0], 'scale': [0.55, 0.55, 0.55]},
            'ground': framing(0.42, (0, 2, 0)),                 # au sol : la même épée, centrée (elle tourne sur elle-même)
            'fixed': framing(0.72, (0, 0, 0), mirror=True),   # cadre
            'gui': framing(0.72, (0, 0, 0)),                   # inventaire / barre rapide : l'épée entière dans la case
        },
        'elements': elements,
    }


with open(MODEL, 'w') as f:
    json.dump(model(), f, indent=2, ensure_ascii=False)
with open(ITEM, 'w') as f:
    # le même modèle 3D partout : inventaire, barre rapide, main (1re/3e personne), sol, cadre ; seul le cadrage change
    json.dump({'model': {'type': 'minecraft:model', 'model': 'bloxpiece:item/ace'}}, f, indent=2)


# ---------------------------------------------------------------- aperçu : vue d'inventaire (face sud, après -45°)
def preview(path, sc=24):
    """Vue d'inventaire texturée (face avant), comme l'icône du jeu."""
    ims = {'blade': blade, 'gold': goldim, 'grip': gripim}
    W = 34 * sc
    im = Image.new('RGBA', (W, W), (139, 139, 139, 255))

    def P(e, x, y):
        rx, ry = place(e, x, y)
        return ((rx + 9) * sc, (25 - ry) * sc)
    for e in sorted(E, key=lambda e: e['t'][2]):
        f, t, tx = e['f'], e['t'], e['tx']
        quad = [P(e, f[0], t[1]), P(e, f[0], f[1]), P(e, t[0], f[1]), P(e, t[0], t[1])]
        src = ims[tx].resize((64, 64), Image.NEAREST)
        flat = [v for p in quad for v in p]
        mask = Image.new('L', (W, W), 0)
        ImageDraw.Draw(mask).polygon(quad, fill=255)
        for X in range(int(min(flat[0::2])), int(max(flat[0::2])) + 1):
            for Y in range(int(min(flat[1::2])), int(max(flat[1::2])) + 1):
                if 0 <= X < W and 0 <= Y < W and mask.getpixel((X, Y)):
                    ox, oy = unplace(e, X / sc - 9, 25 - Y / sc)
                    u = (ox - f[0]) / max(1e-6, t[0] - f[0]); v = (t[1] - oy) / max(1e-6, t[1] - f[1])
                    if tx == 'blade':
                        if e.get('urange') is None:
                            u = (ox - e['ref']) / (BX1 - BX0)
                        else:
                            u0, u1 = e['urange']
                            u = (u0 + (ox - f[0]) / max(1e-6, t[0] - f[0]) * (u1 - u0)) / 16
                    if tx == 'gold':
                        u, v = (ox - 6.0) / 4.6, (5.6 - oy) / 8.6
                    im.putpixel((X, Y), src.getpixel((min(63, max(0, int(u * 64))), min(63, max(0, int(v * 64))))))
    im.save(path)


def preview_3d(path, views=((0, 0), (90, 0), (35, 25), (-35, -20)), sc=22):
    """Silhouette sous plusieurs angles (couleur unie par matière, faces ombrées)."""
    ims = {'blade': blade, 'gold': goldim, 'grip': gripim}
    S = 30 * sc
    sheet = Image.new('RGB', (S * len(views), S), (139, 139, 139))
    d = ImageDraw.Draw(sheet)
    for vi, (yaw, pitch) in enumerate(views):
        cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

        def T(e, x, y, z):
            x, y = place(e, x, y)
            x, z = x - 8, z - 8
            x, z = x * cy + z * sy, -x * sy + z * cy
            y = y - 8
            y, z = y * cp - z * sp, y * sp + z * cp
            return vi * S + S / 2 + x * sc, S / 2 - y * sc, z
        polys = []
        for e in E:
            (x0, y0, z0), (x1, y1, z1) = e['f'], e['t']
            col = ims[e['tx']].getpixel((10, 8))[:3]
            for quad, shade in (([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 1.0),
                                ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], 0.8),
                                ([(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)], 0.7),
                                ([(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], 0.9),
                                ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 1.1),
                                ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], 0.6)):
                pts = [T(e, *p) for p in quad]
                polys.append((sum(p[2] for p in pts) / 4, [(p[0], p[1]) for p in pts], tuple(min(255, int(v * shade)) for v in col)))
        for _, pts, c in sorted(polys, key=lambda q: q[0]):
            d.polygon(pts, fill=c)
    sheet.save(path)


if len(sys.argv) > 1:
    preview(os.path.join(sys.argv[1], 'ace_preview.png'))
    preview_3d(os.path.join(sys.argv[1], 'ace_views.png'))
print('ok :', len(E), 'éléments')
