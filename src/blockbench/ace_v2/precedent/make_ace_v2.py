# Ace — PROPOSITION v2 (modèle indépendant, NON utilisé par le mod) reconstruite d'après l'image de référence :
# longue lame de sabre légèrement courbe, moitié dos gris anthracite et moitié tranchant blanc (texture « pixel » bruitée),
# pointe qui suit la courbe ; garde en anneau doré épais à biseaux ; branche de garde en D, dorée, sous la poignée ;
# poignée brun foncé baguée de cuivre ; pommeau doré en étages.
# Sorties (dans ce dossier) : ace_v2.json (format Java Block/Item, mêmes conventions que models/item/ace.json),
# textures ace_v2_blade.png / ace_v2_gold.png / ace_v2_grip.png, et des aperçus texturés sous plusieurs angles.
# Usage : python3 src/blockbench/ace_v2/make_ace_v2.py [dossier_apercus]
import json, math, os, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
TEXN = {'blade': 'ace_v2_blade', 'gold': 'ace_v2_gold', 'grip': 'ace_v2_grip'}


def noise(x, y, seed):
    """Bruit déterministe 0..1 (pour le grain « pixel » de la référence)."""
    n = (x * 374761393 + y * 668265263 + seed * 982451653) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- textures 16 x 16
def blade_px(x, y):
    """Colonnes = largeur de la lame (0 = dos, 15 = tranchant) : liseré noir, moitié anthracite, ligne de séparation,
    moitié blanche, fil gris clair. Grain bruité comme sur l'image (variation par texel, pas de motif répété visible)."""
    r = noise(x, y, 1)
    if x == 0:
        g = 28
    elif x <= 6:
        g = int(58 + 34 * r + 4 * x)          # anthracite marbré
    elif x == 7:
        g = int(112 + 30 * r)
    elif x == 8:
        g = int(176 + 30 * r)
    elif x <= 14:
        g = int(214 + 38 * r)                  # blanc grené
    else:
        g = 196
    g = min(255, g)
    return (g, g, min(255, g + 6), 255)


def gold_px(x, y):
    d = abs((x + y) - 15)
    base = (255, 232, 96) if d <= 1 else (242, 200, 40) if d <= 6 else (204, 150, 18)
    if x in (0, 15) or y in (0, 15):
        base = tuple(int(c * 0.82) for c in base)
    return base + (255,)


def grip_px(x, y):
    """Poignée : cuir brun foncé, bagues cuivrées régulières (rangées = longueur de la poignée)."""
    band = (y % 4) == 1
    if band:
        c = (186, 96, 40) if x not in (0, 15) else (150, 74, 30)
    else:
        c = (74, 42, 26) if (y % 4) != 2 else (58, 32, 20)
        if x in (0, 15):
            c = tuple(int(v * 0.8) for v in c)
    return c + (255,)


TEX = {}
for k, fn in (('blade', blade_px), ('gold', gold_px), ('grip', grip_px)):
    im = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), fn(x, y))
    im.save(os.path.join(HERE, TEXN[k] + '.png'))
    TEX[k] = im


# ---------------------------------------------------------------- outils géométriques (mêmes conventions que make_ace.py)
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


E = []


def el(f, t, tx, c, angle=-45.0, origin=(8.0, 8.0, 8.0), uv=None, faces=None):
    E.append({'f': f, 't': t, 'tx': tx, 'c': c, 'angle': angle, 'origin': origin, 'uv': uv, 'faces': faces})


AX, AZ = 8.0, 8.0                    # axe de l'épée


def disc(y0, y1, d, comment, tx='gold'):
    r, k = d / 2, d / 2 * 0.62
    el((AX - r, y0, AZ - k), (AX + r, y1, AZ + k), tx, comment)
    el((AX - k, y0, AZ - r), (AX + k, y1, AZ + r), tx, comment)


# pommeau doré en étages
disc(-4.25, -4.0, 0.7, 'pommeau : bouton')
disc(-4.0, -3.7, 1.25, 'pommeau : chapeau')
disc(-3.7, -3.05, 1.65, 'pommeau')
disc(-3.05, -2.8, 1.1, 'pommeau : col')
# poignée baguée
el((AX - 0.45, -2.8, AZ - 0.45), (AX + 0.45, 1.3, AZ + 0.45), 'grip', 'poignée')
disc(1.3, 1.6, 1.2, 'virole')
# garde : anneau doré épais à biseaux
disc(1.6, 1.9, 2.1, 'garde : biseau')
disc(1.9, 3.1, 2.6, 'garde : anneau')
disc(3.1, 3.4, 2.1, 'garde : biseau')

# lame : pans courbés progressivement (la courbe s'accentue vers la pointe, comme sur l'image)
W = 1.2
HALF = W / 2
BODY_LEN, NSEG, CURVE = 19.0, 14, 13.0          # longueur du corps, nombre de pans, inclinaison au bout du corps (degrés)
TIP_LEN, TIP_CURVE = 5.0, 2.2                   # pointe : longueur, degrés supplémentaires par unité
BAND = 7 / 16 * W                                # moitié sombre (dos)
to_u = lambda d: round(d / W * 16, 3)
seg = BODY_LEN / NSEG
joint, s_cum = (AX, 3.2), 0.0
for k in range(NSEG):
    alpha = CURVE * (k / NSEG) ** 2
    angle, origin = combined(alpha, joint)
    jx, jy = joint
    z = 0.17 + (0.002 if k % 2 else 0.0)
    lo = 0.0 if k == 0 else -0.06
    v0 = round((k * 5.3) % 6, 3)                # grain décalé d'un pan à l'autre : pas de répétition visible
    el((round(jx - HALF, 4), round(jy + lo, 4), AZ - z), (round(jx + HALF, 4), round(jy + seg, 4), AZ + z), 'blade', 'lame',
       angle, origin, uv=(0, 16, v0, v0 + 10), faces=('north', 'south', 'east', 'west'))
    dx, dy = rot2(0.0, seg, alpha)
    joint = (jx + dx, jy + dy)
alpha = CURVE * ((NSEG - 1) / NSEG) ** 2
width = lambda t: max(0.04, W * (1 - t * t))
cuts, t = [0.0], 0.0
while t < 1.0 - 1e-6:
    dt = 0.2
    while dt > 0.01 and width(t) - width(min(1.0, t + dt)) > width(min(1.0, t + dt)):
        dt *= 0.8
    tb = math.sqrt(1 - BAND / W)
    if t < tb < t + dt:
        dt = tb - t
    t = min(1.0, t + dt)
    cuts.append(t)
for i, (t0, t1) in enumerate(zip(cuts, cuts[1:])):
    L = (t1 - t0) * TIP_LEN
    alpha += TIP_CURVE * L
    jx, jy = joint
    w0, w1 = width(t0), width(t1)
    sx = jx - HALF
    band = min(w1, BAND)
    zs = 0.17 + 0.002 * (i % 2)
    angle, origin = combined(alpha, joint)
    el((round(sx, 4), round(jy - 0.06, 4), AZ - zs), (round(sx + band, 4), round(jy + L, 4), AZ + zs), 'blade', 'pointe : dos',
       angle, origin, uv=(0, to_u(band), 3, 9), faces=('north', 'south', 'west'))
    dw = w0 - w1
    tilt = math.degrees(math.atan2(dw, L))
    sw = min(w0 - band + 0.05, w1)
    px_, py_ = rot2(sx + w0 - jx, 0.0, alpha)
    pivot = (jx + px_, jy + py_)
    angle, origin = combined(alpha + tilt, pivot)
    Ls = math.hypot(dw, L)
    urange = (round(16 - to_u(sw), 3), 16) if w1 >= BAND - 1e-6 else (round(to_u(BAND) - to_u(sw), 3), to_u(BAND))
    el((round(pivot[0] - sw, 4), round(pivot[1] - 0.12, 4), AZ - (zs - 0.006)), (round(pivot[0], 4), round(pivot[1] + Ls, 4), AZ + (zs - 0.006)),
       'blade', 'pointe : fil', angle, origin, uv=(urange[0], urange[1], 6, 12), faces=('north', 'south', 'east'))
    dx, dy = rot2(0.0, L, alpha)
    joint = (jx + dx, jy + dy)

# branche de garde en D, dorée, sous la poignée (côté tranchant), de la garde jusqu'au pommeau
BOW = [(9.15, 1.95), (9.62, 1.25), (9.82, 0.25), (9.85, -0.95), (9.75, -2.05), (9.35, -2.95), (8.7, -3.35)]
T = 0.2
for (x0, y0), (x1, y1) in zip(BOW, BOW[1:]):
    if y1 < y0:                                   # une barre est symétrique : on la décrit toujours vers le haut
        x0, y0, x1, y1 = x1, y1, x0, y0
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) + 0.25
    if dx <= 0:                                   # barre en long (y) : angle dans les limites de Minecraft
        angle, origin = combined(math.degrees(math.atan2(-dx, dy)), (x0, y0))
        el((x0 - T, y0 - 0.12, AZ - T), (x0 + T, y0 + L - 0.12, AZ + T), 'gold', 'branche de garde', angle, origin)
    else:                                         # sinon barre en large (x)
        angle, origin = combined(math.degrees(math.atan2(dy, dx)), (x0, y0))
        el((x0 - 0.12, y0 - T, AZ - T), (x0 + L - 0.12, y0 + T, AZ + T), 'gold', 'branche de garde', angle, origin)


# ---------------------------------------------------------------- UV, modèle, cadrages
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
    f, t = e['f'], e['t']                         # or : une seule projection pour toute la monture
    U = lambda v, lo, hi: round(min(16, max(0, (v - lo) / (hi - lo) * 16)), 3)
    ux0, ux1 = U(f[0], 6.6, 10.2), U(t[0], 6.6, 10.2)
    vy0, vy1 = U(3.4 - t[1], 0, 7.7), U(3.4 - f[1], 0, 7.7)
    uz0, uz1 = U(f[2], 6.6, 9.4), U(t[2], 6.6, 9.4)
    fc = {'north': [ux1, vy0, ux0, vy1], 'south': [ux0, vy0, ux1, vy1], 'east': [uz0, vy0, uz1, vy1], 'west': [uz1, vy0, uz0, vy1],
          'up': [ux0, uz0, ux1, uz1], 'down': [ux0, uz1, ux1, uz0]}
    return {k: {'uv': fc[k], 'texture': tx} for k in keep}


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


model = {
    '__comment': 'Ace v2 (proposition) : généré par src/blockbench/ace_v2/make_ace_v2.py',
    'textures': {'blade': 'bloxpiece:item/ace_v2_blade', 'gold': 'bloxpiece:item/ace_v2_gold', 'grip': 'bloxpiece:item/ace_v2_grip',
                 'particle': 'bloxpiece:item/ace_v2_blade'},
    'display': {  # mêmes réglages que le modèle actuel (main, 1re personne), cadrages recalculés pour cette forme
        'thirdperson_righthand': {'rotation': [-180, 90, -145], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [-180, -90, 145], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -80, 25], 'translation': [0, 5.0, 0], 'scale': [0.55, 0.55, 0.55]},
        'firstperson_lefthand': {'rotation': [0, 80, -25], 'translation': [0, 5.0, 0], 'scale': [0.55, 0.55, 0.55]},
        'ground': framing(0.42, (0, 2, 0)), 'fixed': framing(0.72, (0, 0, 0), mirror=True), 'gui': framing(0.72, (0, 0, 0)),
    },
    'elements': [{'__comment': e['c'], 'from': list(e['f']), 'to': list(e['t']),
                  'rotation': {'angle': e['angle'], 'axis': 'z', 'origin': list(e['origin'])}, 'faces': faces_for(e)} for e in E],
}
with open(os.path.join(HERE, 'ace_v2.json'), 'w') as f:
    json.dump(model, f, indent=2, ensure_ascii=False)
angles = [e['angle'] for e in E]
assert all(-45.0 <= a <= 45.0 for a in angles), 'angle hors limites'


# ---------------------------------------------------------------- aperçus texturés (rendu logiciel texel par texel)
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


if len(sys.argv) > 1:
    out = sys.argv[1]
    views = [('face', 0, 0), ('3-4', 35, 20), ('dessus_3-4', 0, 35), ('tranche_dos', 0, 90), ('autre_face', 180, 0)]
    for name, yaw, pitch in views:
        render(os.path.join(out, f'ace_v2_{name}.png'), yaw, pitch)
print('ok :', len(E), 'éléments, angles', min(angles), max(angles))
