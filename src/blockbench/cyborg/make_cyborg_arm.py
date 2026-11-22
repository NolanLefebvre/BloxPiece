# -*- coding: utf-8 -*-
"""
Race Cyborg : BRAS GAUCHE MÉCANIQUE du joueur. Génère le modèle Java (CyborgArmModels.java), sa texture (cyborg_arm.png,
64 x 64) et des aperçus (repos + bras transformé en canon). Même méthode que les autres modèles du mod (make_ener.py,
make_silvers_rayleigh.py) : des cubes accrochés les uns aux autres, peints texel par texel.

Repère : celui du bras gauche du joueur (pivot à l'épaule, cube vanilla x -1..3, y -2..10, z -2..2 ; y vers le bas = vers la
main). La couche de rendu (CyborgArmLayer) se place sur le vrai bras gauche (ModelPart.translateAndRotate) : le bras mécanique
suit toutes ses animations.

Pièces : épaulière blindée (rebord en laiton, crête), noyau à câbles, plaques du bras, deux pistons chromés, coude articulé,
avant-bras à QUATRE PLAQUES À CHARNIÈRE (elles s'ouvrent pour la transformation en canon) autour d'un noyau à évents lumineux,
canon caché dans l'avant-bras (deux fûts croisés + bouche en laiton : il sort pendant Cannon Arm), poignet en laiton, main à
quatre doigts à deux phalanges et un pouce (articulés).
Usage (depuis la racine du projet) : python3 src/blockbench/cyborg/make_cyborg_arm.py [dossier_apercus]
"""
import math, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
JAVA = os.path.join(ROOT, 'src/client/java/nolan/bloxpiece/client/cyborg/CyborgArmModels.java')
PNG = os.path.join(ROOT, 'src/main/resources/assets/bloxpiece/textures/entity/cyborg_arm.png')
TW = TH = 64
T = (0, 0, 0, 0)
STEEL, STEEL_D, STEEL_L = (150, 162, 178, 255), (104, 114, 130, 255), (196, 206, 218, 255)
DARK, DARK_L = (40, 44, 54, 255), (66, 72, 86, 255)
BRASS, BRASS_D, BRASS_L = (196, 150, 64, 255), (140, 100, 40, 255), (236, 196, 110, 255)
CHROME, CHROME_D, CHROME_L = (190, 196, 204, 255), (120, 126, 136, 255), (236, 240, 246, 255)
GLOW, GLOW_L = (60, 190, 255, 255), (190, 240, 255, 255)
GUN, GUN_D, GUN_L = (70, 76, 88, 255), (44, 48, 58, 255), (110, 118, 132, 255)
BORE = (12, 12, 16, 255)


class Part:
    def __init__(self, name, parent, box, pivot=(0, 0, 0), rot=(0, 0, 0), inflate=0.0, mat='steel'):
        self.name, self.parent, self.box, self.pivot, self.rot, self.inflate, self.mat = name, parent, box, pivot, rot, inflate, mat
        self.uv = None
        self.base = False
        self.mirror = False


PARTS = []


def part(*a, **k):
    p = Part(*a, **k)
    PARTS.append(p)
    return p


# ---------------------------------------------------------------- bras (repère du bras gauche vanilla)
part('arm', None, (-0.6, -1.4, -1.4, 3.2, 5.4, 2.8), mat='core')                 # noyau du haut du bras (câbles)
part('pauldron', 'arm', (-1.8, -2.9, -2.6, 5.6, 3.4, 5.2), mat='armor')            # épaulière blindée
part('pauldron_ridge', 'arm', (-1.2, -3.5, -1.0, 4.4, 0.7, 2.0), mat='armor_d')     # crête
part('pauldron_rim', 'arm', (-1.95, 0.3, -2.75, 5.9, 0.6, 5.5), mat='brass')       # rebord en laiton
part('upper_plate_outer', 'arm', (2.5, 1.0, -1.6, 0.9, 3.6, 3.2), mat='plate')
part('upper_plate_front', 'arm', (-0.4, 1.0, -2.1, 2.8, 3.4, 0.7), mat='plate')
part('upper_plate_back', 'arm', (-0.4, 1.0, 1.4, 2.8, 3.4, 0.7), mat='plate')
part('piston_a', 'arm', (0.0, 3.0, 1.9, 0.7, 4.6, 0.7), mat='chrome')               # pistons (arrière du bras)
part('piston_b', 'arm', (1.6, 3.0, 1.9, 0.7, 4.6, 0.7), mat='chrome')
part('piston_sleeve_a', 'arm', (-0.1, 2.6, 1.8, 0.9, 1.6, 0.9), mat='gun')
part('piston_sleeve_b', 'arm', (1.5, 2.6, 1.8, 0.9, 1.6, 0.9), mat='gun')
part('elbow', 'arm', (-0.9, 3.8, -1.8, 3.8, 2.0, 3.6), mat='core')                  # coude articulé
part('elbow_cap_outer', 'arm', (2.85, 4.0, -1.0, 0.7, 1.6, 2.0), mat='brass')
part('elbow_cap_inner', 'arm', (-1.25, 4.0, -1.0, 0.7, 1.6, 2.0), mat='brass')
part('forearm_core', 'arm', (-0.5, 5.6, -1.3, 3.0, 4.0, 2.6), mat='vent')           # noyau à évents (visible plaques ouvertes)
part('cable', 'arm', (-1.05, 5.8, -0.3, 0.5, 3.6, 0.6), mat='core')
# plaques de l'avant-bras, chacune sur une charnière en haut (elles s'ouvrent pour le canon)
part('plate_front', 'arm', (-1.9, 0, -0.75, 3.8, 4.2, 0.75), pivot=(1.0, 5.5, -1.55), mat='plate')
part('plate_back', 'arm', (-1.9, 0, 0, 3.8, 4.2, 0.75), pivot=(1.0, 5.5, 1.55), mat='plate')
part('plate_outer', 'arm', (0, 0, -1.6, 0.75, 4.2, 3.2), pivot=(2.55, 5.5, 0), mat='plate_vent')
part('plate_inner', 'arm', (-0.75, 0, -1.6, 0.75, 4.2, 3.2), pivot=(-0.55, 5.5, 0), mat='plate')
# canon caché dans l'avant-bras (sort pendant Cannon Arm) : deux fûts croisés (section octogonale) et la bouche
part('barrel', 'arm', (-1.2, 0, -1.2, 2.4, 7.0, 2.4), pivot=(1.0, 4.5, 0), mat='gun')
part('barrel_x', 'barrel', (-1.2, 0, -1.2, 2.4, 7.0, 2.4), rot=(0, 0.785, 0), mat='gun')
part('muzzle', 'barrel', (-1.55, 6.3, -1.55, 3.1, 1.2, 3.1), mat='muzzle')
part('barrel_band', 'barrel', (-1.4, 2.0, -1.4, 2.8, 0.8, 2.8), mat='brass')
# poignet et EMBOUT (pas de main ni de doigts : le bras finit comme un bras de joueur, à y = 10,4) : bague en laiton et un
# embout blindé en deux volets à charnière sur les côtés ; pour le canon, les volets s'écartent et le canon sort entre eux
part('wrist', 'arm', (-0.8, 9.0, -1.6, 3.6, 0.8, 3.2), mat='brass')
part('cap_left', 'arm', (0, 0, -1.55, 1.95, 1.1, 3.1), pivot=(-0.95, 9.4, 0), mat='armor')
part('cap_right', 'arm', (-1.95, 0, -1.55, 1.95, 1.1, 3.1), pivot=(2.95, 9.4, 0), mat='armor')
part('cap_vent_left', 'cap_left', (0.3, 1.1, -1.0, 1.4, 0.3, 2.0), mat='vent')
part('cap_vent_right', 'cap_right', (-1.7, 1.1, -1.0, 1.4, 0.3, 2.0), mat='vent')


# ---------------------------------------------------------------- texture : rangement des cubes (box UV), peinture
def uv_size(p):
    w, h, d = [math.ceil(v) for v in p.box[3:6]]
    return 2 * d + 2 * w, d + h


def pack():
    todo = sorted(PARTS, key=lambda p: -uv_size(p)[1])
    x, y, row = 0, 0, 0
    for p in todo:
        uw, uh = uv_size(p)
        if x + uw > TW:
            x, y, row = 0, y + row, 0
        assert y + uh <= TH, 'texture trop petite'
        p.uv = (x, y)
        x += uw
        row = max(row, uh)


pack()
img = Image.new('RGBA', (TW, TH), T)
px = img.load()


def faces(p):
    u, v = p.uv
    w, h, d = [math.ceil(q) for q in p.box[3:6]]
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


def paint(p, f, fn):
    u, v, w, h = faces(p)[f]
    for i in range(w):
        for j in range(h):
            c = fn(i, j, w, h)
            if c is not None:
                px[u + i, v + j] = c


def plate_fn(base, dark, light):
    def fn(i, j, w, h):
        edge = i == 0 or j == 0 or i == w - 1 or j == h - 1
        if w >= 3 and h >= 3 and (i, j) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            return light                                    # rivets
        if edge:
            return light if j == 0 else dark                 # arête du haut éclairée, autres arêtes sombres
        return base if (i * 3 + j * 5) % 11 else dark        # usure du métal
    return fn


def vent_fn(i, j, w, h):
    if i == 0 or i == w - 1:
        return DARK
    return (GLOW_L if i % 3 == 1 else GLOW) if j % 2 == 1 and h > 2 else DARK_L if (i + j) % 4 == 0 else DARK


def core_fn(i, j, w, h):
    return DARK_L if i % 2 == 0 else DARK


MAT = {
    'armor': plate_fn(STEEL, STEEL_D, STEEL_L), 'armor_d': plate_fn(STEEL_D, DARK_L, STEEL), 'plate': plate_fn(STEEL, STEEL_D, STEEL_L),
    'core': core_fn, 'vent': vent_fn,
    'chrome': lambda i, j, w, h: CHROME_L if i == 0 else CHROME_D if i == w - 1 or j % 4 == 3 else CHROME,
    'gun': lambda i, j, w, h: GUN_L if i == 0 else GUN_D if i == w - 1 or j % 3 == 2 else GUN,
    'brass': lambda i, j, w, h: BRASS_L if j == 0 else BRASS_D if j == h - 1 or i % 3 == 2 else BRASS,
    'muzzle': lambda i, j, w, h: BRASS_L if j == 0 else BRASS_D if i % 2 else BRASS,
    'finger': lambda i, j, w, h: BRASS if j == 0 else STEEL_L if i == 0 else STEEL_D,
    'finger_tip': lambda i, j, w, h: DARK_L if j == h - 1 else STEEL_L if i == 0 else STEEL,
}
for p in PARTS:
    fn = MAT[p.mat if p.mat in MAT else 'plate']
    for f in ('front', 'back', 'right', 'left', 'up', 'down'):
        paint(p, f, fn)
    if p.mat == 'plate_vent':
        base = plate_fn(STEEL, STEEL_D, STEEL_L)
        for f in ('front', 'back', 'up', 'down'):
            paint(p, f, base)
        for f in ('right', 'left'):   # face extérieure : trois évents lumineux
            paint(p, f, lambda i, j, w, h: (GLOW if i in (1, 2) else DARK) if j in (1, 2) and 0 < i < w - 1 else base(i, j, w, h))
# bouche du canon : âme noire au bout (face « down » = côté main, vers où il tire)
paint(next(p for p in PARTS if p.name == 'muzzle'), 'down',
      lambda i, j, w, h: BORE if 0 < i < w - 1 and 0 < j < h - 1 else BRASS_D)
for n in ('barrel', 'barrel_x'):
    paint(next(p for p in PARTS if p.name == n), 'down', lambda i, j, w, h: BORE)
os.makedirs(os.path.dirname(PNG), exist_ok=True)
img.save(PNG)


# ---------------------------------------------------------------- modèle Java
def jf(v):
    return f'{float(round(v, 4))}F'


def java():
    out = []
    for p in PARTS:
        parent = 'root' if p.parent is None else p.parent
        x, y, z, w, h, d = p.box
        deform = 'CubeDeformation.NONE' if not p.inflate else f'new CubeDeformation({jf(p.inflate)})'
        if p.rot == (0, 0, 0):
            pose = 'PartPose.ZERO' if p.pivot == (0, 0, 0) else f'PartPose.offset({", ".join(jf(v) for v in p.pivot)})'
        else:
            pose = f'PartPose.offsetAndRotation({", ".join(jf(v) for v in p.pivot)}, {", ".join(jf(v) for v in p.rot)})'
        out.append(f'        PartDefinition {p.name} = {parent}.addOrReplaceChild("{p.name}", CubeListBuilder.create().texOffs({p.uv[0]}, {p.uv[1]})\n'
                   f'                .addBox({jf(x)}, {jf(y)}, {jf(z)}, {jf(w)}, {jf(h)}, {jf(d)}, {deform}), {pose});')
    body = '\n'.join(out)
    return f'''package nolan.bloxpiece.client.cyborg;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Bras gauche mécanique de la race Cyborg (rendu et animations : CyborgArmLayer). FICHIER GÉNÉRÉ par
 * src/blockbench/cyborg/make_cyborg_arm.py (avec la texture cyborg_arm.png, 64 x 64). Repère : celui du bras gauche vanilla
 * (pivot à l'épaule). Pièces animées : plate_front / plate_back / plate_outer / plate_inner (charnière en haut), barrel (canon
 * caché qui sort de l'avant-bras), cap_left / cap_right (volets de l'embout, qui s'écartent pour laisser sortir le canon).
 */
public final class CyborgArmModels {{

    public static final ModelLayerLocation ARM = new ModelLayerLocation(BloxPiece.id("cyborg_arm"), "main");

    private CyborgArmModels() {{
    }}

    public static LayerDefinition arm() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TW}, {TH});
    }}
}}
'''


os.makedirs(os.path.dirname(JAVA), exist_ok=True)
open(JAVA, 'w').write(java())
P = {p.name: p for p in PARTS}
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
                pts.append((size[0] / 2 + rx * scale, 120 + (Y + 4) * scale))
                depth += rz
            polys.append((depth / 4, pts, tuple(min(255, int(k * shade[f])) for k in c[:3])))
    for _, pts, c in sorted(polys, key=lambda q: -q[0]):
        dr.polygon(pts, fill=c)
    im.save(path)


# ---------------------------------------------------------------- aperçus : repos, et bras transformé en canon
def cannon_pose(k):
    """Même pose que CyborgArmLayer pour une transformation k (0 = repos, 1 = canon déployé)."""
    def ramp(t, a, b):
        x = min(1.0, max(0.0, (t - a) / (b - a)))
        return x * x * (3 - 2 * x)
    a = 0.95 * ramp(k, 0.0, 0.55)
    P['plate_front'].rot = (-a, 0, 0)
    P['plate_back'].rot = (a, 0, 0)
    P['plate_outer'].rot = (0, 0, -0.95 * ramp(k, 0.08, 0.6))
    P['plate_inner'].rot = (0, 0, a)
    caps = 1.35 * ramp(k, 0.2, 0.7)
    P['cap_left'].rot = (0, 0, caps)
    P['cap_right'].rot = (0, 0, -caps)
    P['barrel'].pivot = (1.0, 4.5 + 4.2 * ramp(k, 0.45, 1.0), 0)


if len(sys.argv) > 1:
    out = sys.argv[1]
    HIDE = set()
    def render_set(path, yaw, k):
        cannon_pose(k)
        global PARTS
        keep = PARTS
        PARTS = [p for p in keep if not (k == 0 and p.name in ('barrel', 'barrel_x', 'muzzle', 'barrel_band'))]
        render(path, yaw, scale=22, size=(420, 520))
        PARTS = keep
    render_set(os.path.join(out, 'cyborg_arm_face.png'), 0.0, 0.0)
    render_set(os.path.join(out, 'cyborg_arm_34.png'), 0.7, 0.0)
    render_set(os.path.join(out, 'cyborg_arm_dos.png'), math.pi + 0.5, 0.0)
    render_set(os.path.join(out, 'cyborg_arm_canon.png'), 0.9, 1.0)
    img.resize((TW * 6, TH * 6), Image.NEAREST).save(os.path.join(out, 'cyborg_arm_texture.png'))
print('ok :', len(PARTS), 'pièces')
