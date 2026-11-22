# -*- coding: utf-8 -*-
"""
Épées des bandits (camp de bandits) : deux armes 3D ORIGINALES, au niveau de qualité de l'épée Ace du mod (make_ace_v2.py :
vraie géométrie en cubes, lame épaisse, textures 16x16 « pixel » bruitées), mais avec leur propre design (rien d'Ace : ni la
lame courbe à deux tons, ni la coquille, ni la branche en D, ni la poignée baguée).
  - Bandit (bandit_sword) : un FAUCHON rustique de pirate de bas rang. Lame droite d'acier usé qui s'élargit vers l'avant et
    finit en pointe coupée (dos rabattu), gouttière gravée, taches de rouille ; garde en simple barre de fer droite à bouts
    carrés ; poignée de bois entourée de corde ; pommeau en écrou carré.
  - Chef des bandits (bandit_chief_sword) : un grand DAO de chef, plus long et plus large. Lame d'acier sombre bleui à fil
    clair, dos dentelé de trois crans près de la garde et ergot recourbé près de la pointe, talon en laiton riveté ; garde en
    S de bronze (un quillon recourbé vers la lame, l'autre vers la main) avec un écusson central serti d'un rubis ; poignée de
    cuir noir filée de laiton ; pommeau en anneau d'où pend un gland de corde rouge.
Même famille : même construction, même acier, même poignée au même endroit ; très différentes de silhouette.
La poignée est au même endroit que celle d'Ace (y -2,8 à 1,3 sur l'axe) : mêmes réglages d'affichage en main (validés en jeu
sur le joueur et sur Gol D. Roger), la main tient le milieu de la poignée, tranchant vers l'avant, lame vers le haut.
Sorties : models/item/<nom>.json et textures/item/<nom>_*.png (ressources du mod), <nom>.bbmodel (Blockbench, « Java
Block/Item », éditable) et des aperçus. Usage : python3 src/blockbench/bandit_swords/make_bandit_swords.py [aperçus]
"""
import base64, io, json, math, os, sys, uuid
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'main/resources/assets/bloxpiece')
E, TEX = [], {}
AX, AZ = 8.0, 8.0                    # axe de l'épée (comme Ace)
# Taille en 1re personne (échelle, translation) : 0,42 au départ (trop petites à l'écran). Le fauchon, plus court, est agrandi
# davantage que l'épée du chef (plus longue) pour que les deux restent cohérentes ; translation = poignée gardée en place.
FIRST_PERSON = {'bandit_sword': (0.68, [1.13, 3.6, 1.98]), 'bandit_chief_sword': (0.62, [1.13, 3.3, 1.55])}


def noise(x, y, seed):
    """Bruit déterministe 0..1 (pour le grain « pixel » de la référence)."""
    n = (x * 374761393 + y * 668265263 + seed * 982451653) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0



def rot2(x, y, deg):
    a = math.radians(deg)
    return x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)


def combined(alpha, joint):
    """Une seule rotation (angle, centre) = alpha autour de joint, puis -45° autour de (8, 8) (diagonale d'icône)."""
    theta = -45.0 + alpha
    px, py = joint
    rx, ry = rot2(px, py, alpha)
    cx, cy = rot2(px - rx - 8, py - ry - 8, -45.0)
    cx, cy = cx + 8, cy + 8
    a = math.radians(theta)
    m00, m01, m10, m11 = 1 - math.cos(a), math.sin(a), -math.sin(a), 1 - math.cos(a)
    det = m00 * m11 - m01 * m10
    return round(theta, 3), (round((cx * m11 - m01 * cy) / det, 4), round((m00 * cy - m10 * cx) / det, 4), 8.0)



def place(e, x, y):
    ox, oy = e['origin'][0], e['origin'][1]
    rx, ry = rot2(x - ox, y - oy, e['angle'])
    return rx + ox, ry + oy


def framing(scale, extra, mirror=False):
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



FACE_DEF = {  # face -> 4 coins (indices x,y,z : 0 = min, 1 = max) dans l'ordre haut-gauche, haut-droit, bas-droit, bas-gauche vu de face
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)], 'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)], 'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], 'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
SHADE = {'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6, 'up': 1.0, 'down': 0.5}


def render(path, yaw, pitch, size=(1400, 520), sc=40):
    """Épée posée à l'horizontale (poignée à gauche), comme sur l'image, puis tournée de yaw / pitch."""
    img = Image.new('RGB', size, (12, 12, 14))
    d = ImageDraw.Draw(img)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def P(e, x, y, z):
        x, y = place(e, x, y)
        x, y = rot2(x - 8, y - 8, 45.0)                           # annule la diagonale d'icône : épée verticale
        x, y = y, -x                                              # puis à l'horizontale, poignée à gauche
        z = z - 8
        x, z = x * cy_ + z * sy_, -x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return (size[0] / 2 + (x - 3.6) * sc, size[1] / 2 - y * sc, z)

    polys = []
    for e in E:
        F, T_ = e['f'], e['t']
        faces = faces_for(e)
        for fn, info in faces.items():
            u0, v0, u1, v1 = info['uv']
            corners = [tuple((F, T_)[c[i]][i] for i in range(3)) for c in FACE_DEF[fn]]
            tex = TEX[e['tx']]
            nu, nv = max(1, int(abs(u1 - u0) + 0.999)), max(1, int(abs(v1 - v0) + 0.999))
            nu, nv = min(nu, 16), min(nv, 16)
            for i in range(nu):
                for j in range(nv):
                    a0, a1, b0, b1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv

                    def lerp(a, b):
                        top = [corners[0][k] + (corners[1][k] - corners[0][k]) * a for k in range(3)]
                        bot = [corners[3][k] + (corners[2][k] - corners[3][k]) * a for k in range(3)]
                        return [top[k] + (bot[k] - top[k]) * b for k in range(3)]
                    q = [P(e, *lerp(a0, b0)), P(e, *lerp(a1, b0)), P(e, *lerp(a1, b1)), P(e, *lerp(a0, b1))]
                    tu = u0 + (u1 - u0) * (a0 + a1) / 2
                    tv = v0 + (v1 - v0) * (b0 + b1) / 2
                    c = tex.getpixel((min(15, max(0, int(tu))), min(15, max(0, int(tv)))))
                    sh = SHADE[fn]
                    polys.append((sum(p[2] for p in q) / 4, [(p[0], p[1]) for p in q], tuple(min(255, int(v * sh * 1.15)) for v in c[:3])))
    xs = [x for _, pts, _ in polys for x, _ in pts]
    ys = [y for _, pts, _ in polys for _, y in pts]
    ox, oy = size[0] / 2 - (min(xs) + max(xs)) / 2, size[1] / 2 - (min(ys) + max(ys)) / 2   # centré dans l'image
    for _, pts, c in sorted(polys, key=lambda p: p[0]):
        d.polygon([(x + ox, y + oy) for x, y in pts], fill=c)
    img.save(path)



def faces_for(e):
    keep = e['faces'] or ('north', 'south', 'east', 'west', 'up', 'down')
    tx = '#' + e['tx']
    if e['tx'] == 'blade':
        u0, u1, v0, v1 = e['uv']
        fc = {'north': [u1, v0, u0, v1], 'south': [u0, v0, u1, v1],           # nord vu de face : le dos (x petit) à droite
              'west': [u0, v0, min(16, u0 + 1), v1], 'east': [max(0, u1 - 1), v0, u1, v1]}
        return {k: {'uv': fc[k], 'texture': tx} for k in keep if k in fc}
    if e['tx'] == 'grip':
        return {k: {'uv': [0, 0, 16, 16], 'texture': tx, 'rotation': 0} for k in keep}
    f, t = e['f'], e['t']                         # monture (or, fer, gemme) : une seule projection
    U = lambda v, lo, hi: round(min(16, max(0, (v - lo) / (hi - lo) * 16)), 3)
    ux0, ux1 = U(f[0], 6.6, 10.2), U(t[0], 6.6, 10.2)
    vy0, vy1 = U(3.4 - t[1], 0, 7.7), U(3.4 - f[1], 0, 7.7)
    uz0, uz1 = U(f[2], 6.6, 9.4), U(t[2], 6.6, 9.4)
    fc = {'north': [ux1, vy0, ux0, vy1], 'south': [ux0, vy0, ux1, vy1], 'east': [uz0, vy0, uz1, vy1], 'west': [uz1, vy0, uz0, vy1],
          'up': [ux0, uz0, ux1, uz1], 'down': [ux0, uz1, ux1, uz0]}
    return {k: {'uv': fc[k], 'texture': tx} for k in keep}




def el(f, t, tx, c, angle=-45.0, origin=(8.0, 8.0, 8.0), uv=None, faces=None):
    E.append({'f': f, 't': t, 'tx': tx, 'c': c, 'angle': angle, 'origin': origin, 'uv': uv, 'faces': faces})


# ---------------------------------------------------------------- textures 16 x 16
def blade_tex(dark, light, edge_line, rust=False, fuller=False, blued=False, etched=False):
    """Colonnes = largeur de la lame (0 = dos, 15 = fil) : dos sombre, plat, fil clair ; gouttière (rainure le long du dos),
    rouille (bandit) ou acier bleui et petits motifs gravés (chef). Grain bruité."""
    def px(x, y):
        r = noise(x, y, 7)
        if x == 0:
            g = edge_line
        elif x >= 13:
            g = light + 25 * r                                # fil poli
        elif fuller and x in (3, 4):
            g = dark - 22 + 16 * r                            # gouttière
        else:
            g = dark + (light - dark) * (x / 13.0) * 0.55 + 30 * r
        g = int(max(0, min(255, g)))
        c = [g, g, min(255, g + 6)]
        if blued and x < 13:
            c = [int(g * 0.78), int(g * 0.86), min(255, int(g * 1.12) + 8)]
        if etched and x in (6, 8) and y % 4 == 1:
            c = [min(255, v + 46) for v in c]                 # motif gravé
        if rust and noise(x, y, 31) > 0.9 and x < 13:
            c = [150, 84, 46]                                 # taches de rouille
        return tuple(c) + (255,)
    return px


def rope_tex(rope, rope_d, light):
    def px(x, y):
        k = (x + 2 * y) % 6
        return (light if k == 0 else rope_d if k in (4, 5) else rope) + (255,)   # corde enroulée en diagonale
    return px


def wire_tex(leather, leather_d, wire):
    def px(x, y):
        if (x + y) % 5 == 0:
            return wire + (255,)                              # fil de laiton en spirale
        return (leather_d if (x * 3 + y) % 7 == 0 else leather) + (255,)
    return px


def tassel_tex(red, red_d):
    def px(x, y):
        return (red_d if x % 3 == 0 else red) + (255,)       # brins de corde
    return px


def metal_tex(hi, mid, lo):
    def px(x, y):
        d = abs((x + y) - 15)
        base = hi if d <= 1 else mid if d <= 6 else lo
        if x in (0, 15) or y in (0, 15):
            base = tuple(int(c * 0.82) for c in base)
        return tuple(base) + (255,)
    return px


def gem_px(x, y):
    d = math.hypot(x - 6, y - 6)
    c = (255, 150, 150) if d < 2 else (214, 30, 40) if d < 7 else (130, 10, 24)
    return c + (255,)


def make_tex(name, fn):
    im = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), fn(x, y))
    im.save(os.path.join(ASSETS, 'textures/item', name + '.png'))
    return im


# ---------------------------------------------------------------- lames à profil libre (pans droits empilés le long de l'axe)
def profile_blade(start, length, nseg, back, edge, comment='lame', z=0.18):
    """Lame droite (dans l'axe) de y = start à start + length : pour t de 0 à 1, le dos est à x = AX + back(t) et le fil à
    x = AX + edge(t). Chaque pan est un vrai pavé (épaisseur 2z) ; la texture couvre toute la largeur (dos -> fil)."""
    seg = length / nseg
    for k in range(nseg):
        t0, t1 = k / nseg, (k + 1) / nseg
        tm = (t0 + t1) / 2
        x0, x1 = AX + back(tm), AX + edge(tm)
        if x1 - x0 < 0.06:
            continue
        y0 = start + k * seg - (0.0 if k == 0 else 0.03)
        zz = z + (0.002 if k % 2 else 0.0)
        v0 = round((k * 5.3) % 6, 3)
        el((round(x0, 4), round(y0, 4), AZ - zz), (round(x1, 4), round(start + (k + 1) * seg, 4), AZ + zz), 'blade', comment,
           uv=(0, 16, v0, v0 + 10), faces=('north', 'south', 'east', 'west', 'up'))


def rod(p0, p1, T, tx, comment, extra=0.2):
    """Barre de section 2T qui va du point p0 au point p1 (plan de la lame), en long ou en large selon sa pente : Minecraft
    n'accepte qu'une rotation de -45° à 45° par cube (même méthode que la branche de garde d'Ace)."""
    (x0, y0), (x1, y1) = p0, p1
    if y1 < y0:
        x0, y0, x1, y1 = x1, y1, x0, y0
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) + extra
    uv = (0, 6, 2, 8) if tx == 'blade' else None
    fc = ('north', 'south', 'east', 'west', 'up', 'down')
    if dx <= 0:
        angle, origin = combined(math.degrees(math.atan2(-dx, dy)), (x0, y0))
        el((x0 - T, y0 - extra / 2, AZ - T), (x0 + T, y0 + L - extra / 2, AZ + T), tx, comment, angle, origin, uv=uv, faces=fc)
    else:
        angle, origin = combined(math.degrees(math.atan2(dy, dx)), (x0, y0))
        el((x0 - extra / 2, y0 - T, AZ - T), (x0 + L - extra / 2, y0 + T, AZ + T), tx, comment, angle, origin, uv=uv, faces=fc)


def bar(x0, y0, z0, x1, y1, z1, tx, comment, angle=-45.0, origin=(8.0, 8.0, 8.0)):
    if tx == 'blade':   # morceau de lame (crans, ergot) : bande sombre du dos de la texture
        el((x0, y0, z0), (x1, y1, z1), tx, comment, angle, origin, uv=(0, 6, 2, 8), faces=('north', 'south', 'east', 'west'))
    else:
        el((x0, y0, z0), (x1, y1, z1), tx, comment, angle, origin)


# ---------------------------------------------------------------- les deux épées
def bandit_sword():
    """Fauchon rustique : pommeau en écrou carré, poignée de bois à corde, garde en barre droite, lame qui s'élargit vers
    l'avant et finit en pointe coupée."""
    bar(AX - 0.65, -3.55, AZ - 0.65, AX + 0.65, -2.8, AZ + 0.65, 'metal', 'pommeau : écrou carré')
    bar(AX - 0.4, -3.8, AZ - 0.4, AX + 0.4, -3.55, AZ + 0.4, 'metal', 'pommeau : rivet')
    bar(AX - 0.42, -2.8, AZ - 0.42, AX + 0.42, 1.3, AZ + 0.42, 'grip', 'poignée : bois et corde')
    bar(AX - 2.5, 1.3, AZ - 0.42, AX + 2.5, 1.75, AZ + 0.42, 'metal', 'garde : barre droite')
    for sx in (-1, 1):
        bar(AX + sx * 2.5 - 0.35, 1.2, AZ - 0.5, AX + sx * 2.5 + 0.35, 1.85, AZ + 0.5, 'metal', 'garde : bout carré')
    bar(AX - 0.6, 1.75, AZ - 0.3, AX + 0.6, 2.05, AZ + 0.3, 'metal', 'garde : virole')
    # lame (fauchon) : 16 de long ; dos droit puis rabattu au dernier quart (pointe coupée) ; fil qui s'élargit
    back = lambda t: -0.55 if t < 0.74 else -0.55 + (t - 0.74) / 0.26 * 1.55
    edge = lambda t: 0.5 + 0.8 * min(1.0, t / 0.8) - (0.0 if t < 0.86 else (t - 0.86) / 0.14 * 0.9)
    profile_blade(2.05, 16.0, 16, back, edge)


def chief_sword():
    """Grand dao de chef : pommeau en anneau à gland rouge, poignée de cuir noir filée de laiton, garde en S de bronze à
    écusson et rubis, talon de laiton riveté, large lame d'acier bleui au dos dentelé et à l'ergot."""
    # pommeau en anneau (dans le plan de la lame) et gland de corde rouge
    RING = [(8.0, -2.85), (8.75, -3.15), (9.05, -3.85), (8.75, -4.55), (8.0, -4.85), (7.25, -4.55), (6.95, -3.85),
            (7.25, -3.15), (8.0, -2.85)]
    for p0, p1 in zip(RING, RING[1:]):
        rod(p0, p1, 0.22, 'gold', 'pommeau : anneau', extra=0.3)
    bar(AX - 0.18, -5.6, AZ - 0.18, AX + 0.18, -4.75, AZ + 0.18, 'tassel', 'gland : cordon')
    bar(AX - 0.42, -7.4, AZ - 0.42, AX + 0.42, -5.6, AZ + 0.42, 'tassel', 'gland : franges')
    bar(AX - 0.5, -5.9, AZ - 0.5, AX + 0.5, -5.55, AZ + 0.5, 'gold', 'gland : noeud doré')
    # poignée
    bar(AX - 0.5, -2.8, AZ - 0.5, AX + 0.5, 1.3, AZ + 0.5, 'grip', 'poignée : cuir noir filé de laiton')
    bar(AX - 0.62, -2.95, AZ - 0.62, AX + 0.62, -2.55, AZ + 0.62, 'gold', 'poignée : bague')
    # garde en S : barre, quillon recourbé vers la lame (côté fil), quillon recourbé vers la main (côté dos), écusson, rubis
    bar(AX - 2.3, 1.3, AZ - 0.45, AX + 2.3, 1.85, AZ + 0.45, 'gold', 'garde : barre')
    for pts, c in (([(10.2, 1.55), (10.9, 2.0), (11.25, 2.75), (11.1, 3.45)], 'garde : quillon vers la lame'),
                   ([(5.8, 1.55), (5.1, 1.1), (4.75, 0.35), (4.9, -0.35)], 'garde : quillon vers la main')):
        for p0, p1 in zip(pts, pts[1:]):
            rod(p0, p1, 0.27, 'gold', c)
        end = pts[-1]
        bar(end[0] - 0.38, end[1] - 0.38, AZ - 0.38, end[0] + 0.38, end[1] + 0.38, AZ + 0.38, 'gold', 'garde : bouton')
    bar(AX - 0.85, 0.9, AZ - 0.55, AX + 0.85, 2.5, AZ + 0.55, 'gold', 'garde : écusson')
    bar(AX - 0.4, 1.4, AZ - 0.68, AX + 0.4, 2.1, AZ + 0.68, 'gem', 'garde : rubis')
    # talon de laiton riveté à la base de la lame
    bar(AX - 0.8, 1.85, AZ - 0.32, AX + 0.8, 3.1, AZ + 0.32, 'gold', 'talon de laiton')
    for ry in (2.2, 2.75):
        bar(AX - 0.2, ry - 0.15, AZ - 0.38, AX + 0.2, ry + 0.15, AZ + 0.38, 'metal', 'talon : rivet')
    # lame (dao) : 19,5 de long, large ; dos droit, fil qui s'élargit puis remonte en courbe jusqu'à la pointe
    back = lambda t: -0.62 if t < 0.9 else -0.62 + (t - 0.9) / 0.1 * 0.5
    edge = lambda t: 0.68 + 0.7 * min(1.0, t / 0.78) - (0.0 if t < 0.8 else ((t - 0.8) / 0.2) ** 1.6 * 1.86)
    profile_blade(3.1, 19.5, 20, back, edge, z=0.2)
    # dos dentelé (trois crans près de la garde) et ergot recourbé près de la pointe
    for yy in (4.2, 5.3, 6.4):
        rod((AX - 0.45, yy), (AX - 1.05, yy + 0.55), 0.16, 'blade', 'dos : cran', extra=0.3)
    rod((AX - 0.4, 17.9), (AX - 1.15, 19.3), 0.17, 'blade', 'dos : ergot', extra=0.35)

SWORDS = {
    'bandit_sword': (bandit_sword, {
        'blade': blade_tex(96, 186, 52, rust=True, fuller=True),
        'metal': metal_tex((168, 166, 160), (122, 120, 116), (84, 82, 80)),
        'grip': rope_tex((150, 112, 66), (108, 76, 42), (196, 164, 112))}),
    'bandit_chief_sword': (chief_sword, {
        'blade': blade_tex(54, 200, 26, blued=True, etched=True),
        'gold': metal_tex((236, 196, 110), (190, 140, 62), (132, 90, 38)),
        'metal': metal_tex((210, 214, 222), (160, 164, 174), (110, 114, 124)),
        'grip': wire_tex((34, 30, 32), (22, 20, 22), (214, 170, 80)),
        'gem': gem_px,
        'tassel': tassel_tex((178, 30, 36), (120, 16, 24))}),
}


def bbmodel(name, model):
    """Fichier Blockbench « Java Block/Item » éditable : mêmes cubes, rotations, faces, réglages d'affichage, textures."""
    keys = list(model['textures'].keys())
    keys.remove('particle')
    elements = []
    for e in model['elements']:
        faces = {}
        for k, f in e['faces'].items():
            faces[k] = {'uv': f['uv'], 'texture': keys.index(f['texture'][1:])}
            if 'rotation' in f:
                faces[k]['rotation'] = f['rotation']
        r = e['rotation']
        elements.append({'name': e['__comment'], 'from': e['from'], 'to': e['to'], 'autouv': 0, 'color': 0, 'origin': r['origin'],
                         'rotation': [0, 0, r['angle']], 'faces': faces, 'type': 'cube', 'uuid': str(uuid.uuid4())})
    textures = []
    for i, k in enumerate(keys):
        buf = io.BytesIO(); TEX[k].save(buf, 'PNG')
        textures.append({'name': f'{name}_{k}.png', 'id': str(i), 'width': 16, 'height': 16, 'uv_width': 16, 'uv_height': 16,
                         'namespace': 'bloxpiece', 'folder': 'item', 'particle': i == 0, 'render_mode': 'default', 'visible': True,
                         'saved': True, 'uuid': str(uuid.uuid4()),
                         'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()})
    return {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False}, 'name': name, 'parent': '',
            'ambientocclusion': True, 'front_gui_light': False, 'visible_box': [1, 1, 0], 'resolution': {'width': 16, 'height': 16},
            'elements': elements, 'outliner': [e['uuid'] for e in elements], 'textures': textures, 'display': model['display']}


out = sys.argv[1] if len(sys.argv) > 1 else None
for name, (builder, texfns) in SWORDS.items():
    E.clear(); TEX.clear()
    builder()
    for k, fn in texfns.items():
        TEX[k] = make_tex(f'{name}_{k}', fn)
    model = {
        '__comment': f'Épée des bandits ({name}) : générée par src/blockbench/bandit_swords/make_bandit_swords.py',
        'textures': dict({k: f'bloxpiece:item/{name}_{k}' for k in texfns}, particle=f'bloxpiece:item/{name}_blade'),
        'display': {
            # mêmes réglages qu'Ace (poignée au même endroit) : en main, la main tient le milieu de la poignée, tranchant vers
            # l'avant, lame vers le haut ; main gauche en miroir ; 1re personne comme Ace
            'thirdperson_righthand': {'rotation': [-180, 90, -145], 'translation': [0, 5.56, 0.05], 'scale': [0.85, 0.85, 0.85]},
            'thirdperson_lefthand': {'rotation': [-180, -90, 145], 'translation': [0, 5.56, 0.05], 'scale': [0.85, 0.85, 0.85]},
            # 1re personne : même pose qu'Ace, agrandie AUTOUR DE LA POIGNÉE (translation recalculée : la main tient toujours le
            # milieu de la poignée), échelle propre à chaque épée (FIRST_PERSON) ; 3e personne inchangée
            'firstperson_righthand': {'rotation': [-180, 90, -125], 'translation': FIRST_PERSON[name][1], 'scale': [FIRST_PERSON[name][0]] * 3},
            'firstperson_lefthand': {'rotation': [-180, -90, 125], 'translation': FIRST_PERSON[name][1], 'scale': [FIRST_PERSON[name][0]] * 3},
            'ground': framing(0.42, (0, 2, 0)), 'fixed': framing(0.72, (0, 0, 0), mirror=True), 'gui': framing(0.72, (0, 0, 0)),
        },
        'elements': [{'__comment': e['c'], 'from': list(e['f']), 'to': list(e['t']),
                      'rotation': {'angle': e['angle'], 'axis': 'z', 'origin': list(e['origin'])}, 'faces': faces_for(e)} for e in E],
    }
    assert all(-45.0 <= e['angle'] <= 45.0 for e in E), 'angle hors limites'
    with open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w') as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
    with open(os.path.join(HERE, name + '.bbmodel'), 'w') as f:
        json.dump(bbmodel(name, model), f, indent=1, ensure_ascii=False)
    if out:
        for view, yaw, pitch in (('face', 0, 0), ('3-4', 35, 20), ('tranche', 0, 90)):
            render(os.path.join(out, f'{name}_{view}.png'), yaw, pitch)
    print(name, ':', len(E), 'éléments')
