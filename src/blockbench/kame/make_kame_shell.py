# -*- coding: utf-8 -*-
"""
Carapace du Kame Kame no Mi (fruit de la Tortue) : modèle 3D en cubes, ajusté au torse du joueur Minecraft (pièce « body » :
x -4..4, y 0..12 vers le bas, z -2..2, l'avant en -z).
  - DOS (dorsale) : grande carapace bombée en gradins, écailles vertébrales au centre, écailles costales sur les côtés,
    bordure d'écailles marginales tout autour ;
  - VENTRE (plastron) : plaque devant le torse, 6 écailles claires en 2 colonnes, reliée au dos par deux ponts sur les flancs.
Sorties : client/.../KameShellModel.java (généré, ne pas éditer), textures/entity/kame_shell.png, kame_shell.bbmodel (source),
icônes (fruit et 3 attaques), aperçus si un dossier est passé : python3 make_kame_shell.py <dossier>
"""
import base64, io, json, math, os, random, sys, uuid
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(SRC, 'main', 'resources', 'assets', 'bloxpiece')
JAVA = os.path.join(SRC, 'client', 'java', 'nolan', 'bloxpiece', 'client', 'entity', 'KameShellModel.java')
TW = TH = 128

# pivots des deux groupes (repère du torse) : la croissance part du dos et du ventre
PIVOT = {'dorsal': (0.0, 6.0, 2.0), 'ventral': (0.0, 6.0, -2.0)}

# couleurs par type d'écaille : (base, clair, sombre, liseré)
PAL = {
    'rim':   ((96, 74, 40), (128, 100, 56), (66, 50, 26), (40, 30, 16)),
    'base':  ((84, 98, 46), (110, 126, 62), (58, 70, 30), (36, 44, 18)),
    'mid':   ((104, 120, 54), (138, 152, 74), (74, 88, 36), (42, 52, 20)),
    'top':   ((124, 138, 62), (160, 172, 86), (88, 102, 42), (48, 58, 22)),
    'scute': ((150, 124, 58), (196, 168, 90), (110, 88, 38), (60, 44, 18)),   # écailles en relief (ambrées)
    'plast': ((222, 204, 140), (244, 232, 182), (186, 164, 100), (120, 98, 52)),  # plastron crème
    'pbase': ((168, 146, 88), (196, 176, 116), (130, 110, 62), (80, 64, 32)),
    'bridge': ((104, 86, 46), (130, 110, 62), (76, 60, 30), (46, 36, 18)),
}

CUBES = []   # dict(group, kind, f=(x0,y0,z0), t=(x1,y1,z1))


def cube(group, kind, f, t):
    CUBES.append(dict(group=group, kind=kind, f=f, t=t))


# ---------------------------------------------------------------- DOS : gradins bombés aux coins arrondis (z vers l'arrière)
def rounded(group, kind, hx, y0, y1, z0, z1, c):
    """Gradin ovale : une plaque pleine hauteur moins large, et une plaque pleine largeur moins haute (coins arrondis)."""
    cube(group, kind, (-hx + c, y0, z0), (hx - c, y1, z1))
    cube(group, kind, (-hx, y0 + c, z0), (hx, y1 - c, z1))


rounded('dorsal', 'rim', 5.6, -1.4, 13.4, 2.0, 2.9, 1.2)       # bordure marginale (le plus large)
rounded('dorsal', 'base', 5.0, -0.8, 12.8, 2.9, 4.0, 1.4)
rounded('dorsal', 'mid', 4.2, 0.2, 11.8, 4.0, 5.0, 1.5)
rounded('dorsal', 'top', 3.1, 1.4, 10.6, 5.0, 5.9, 1.4)
rounded('dorsal', 'top', 1.9, 2.8, 9.2, 5.9, 6.5, 1.0)          # sommet du dôme
# écailles vertébrales (au centre, en relief, arrondies)
for (y0, y1) in ((1.6, 3.9), (4.1, 6.4), (6.6, 8.9), (9.1, 10.9)):
    rounded('dorsal', 'scute', 1.5, y0, y1, 6.3, 6.9, 0.5)
# écailles costales (de chaque côté, sur les gradins)
for sx in (-1, 1):
    for (y0, y1) in ((0.8, 3.7), (4.0, 6.9), (7.2, 10.1), (10.4, 11.6)):
        x0, x1 = (2.1, 3.9) if sx > 0 else (-3.9, -2.1)
        cube('dorsal', 'scute', (x0, y0, 4.9), (x1, y1, 5.6))
# écailles marginales : petites dents le long du bord bas et des flancs
for i in range(5):
    x = -4.4 + i * 1.95
    cube('dorsal', 'rim', (x, 13.1, 2.2), (x + 1.6, 14.0, 3.4))
for sx in (-1, 1):
    for (y0, y1) in ((1.2, 3.8), (4.8, 7.4), (8.4, 11.0)):
        x0, x1 = (5.5, 6.1) if sx > 0 else (-6.1, -5.5)
        cube('dorsal', 'rim', (x0, y0, 2.1), (x1, y1, 3.3))

# ---------------------------------------------------------------- VENTRE : plastron (z vers l'avant = négatif)
cube('ventral', 'pbase', (-4.3, 0.3, -3.0), (4.3, 11.9, -2.0))
for (x0, x1) in ((-4.0, -0.15), (0.15, 4.0)):
    for (y0, y1) in ((0.7, 4.0), (4.3, 7.8), (8.1, 11.5)):
        cube('ventral', 'plast', (x0, y0, -3.5), (x1, y1, -2.9))
# ponts sur les flancs (relient le plastron au dos, à peine en dehors du torse)
for sx in (-1, 1):
    x0, x1 = (3.9, 4.3) if sx > 0 else (-4.3, -3.9)
    cube('ventral', 'bridge', (x0, 3.0, -2.6), (x1, 10.0, 2.4))

# ---------------------------------------------------------------- texture : une zone de dépliage par cube, écailles peintes
def size(c):
    return tuple(round(c['t'][i] - c['f'][i], 3) for i in range(3))


def region(c):
    w, h, d = (max(1, math.ceil(v)) for v in size(c))
    return 2 * (w + d), d + h, (w, h, d)


# rangement en étagères
x = y = row = 0
for c in sorted(CUBES, key=lambda c: -region(c)[1]):
    rw, rh, _ = region(c)
    if x + rw > TW:
        x, y, row = 0, y + row, 0
    c['uv'] = (x, y)
    x += rw
    row = max(row, rh)
assert y + row <= TH, 'texture trop petite'

tex = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
tp = tex.load()
rnd = random.Random(5)


def paint_face(x0, y0, w, h, kind):
    base, light, dark, line = PAL[kind]
    for j in range(h):
        for i in range(w):
            edge = i == 0 or j == 0 or i == w - 1 or j == h - 1
            if edge:
                c = line                                         # sillon entre les écailles
            else:
                # dégradé : clair en haut à gauche, sombre en bas à droite (volume)
                t = (i / max(1, w - 1) + j / max(1, h - 1)) / 2
                c = light if t < 0.3 else base if t < 0.7 else dark
                # cernes de croissance concentriques (motif naturel des écailles)
                ring = min(i, j, w - 1 - i, h - 1 - j)
                if w >= 5 and h >= 5 and ring in (2, 4) and (i + j) % 3:
                    c = tuple(int(v * 0.86) for v in base)
                if rnd.random() < 0.08:
                    c = tuple(max(0, v - 14) for v in c)
            tp[x0 + i, y0 + j] = c + (255,)


for c in CUBES:
    u, v = c['uv']
    w, h, d = region(c)[2]
    kind = c['kind']
    # dépliage Minecraft : [haut d x w][bas] sur la 1re ligne, puis [ouest][nord][est][sud] sur la seconde
    paint_face(u + d, v, w, d, kind)
    paint_face(u + d + w, v, w, d, kind)
    paint_face(u, v + d, d, h, kind)
    paint_face(u + d, v + d, w, h, kind)
    paint_face(u + d + w, v + d, d, h, kind)
    paint_face(u + d + w + d, v + d, w, h, kind)

tex.save(os.path.join(ASSETS, 'textures', 'entity', 'kame_shell.png'))

# ---------------------------------------------------------------- modèle Java (généré)
rows = []
for c in CUBES:
    g = 0 if c['group'] == 'dorsal' else 1
    px_, py_, pz_ = PIVOT[c['group']]
    f = (c['f'][0] - px_, c['f'][1] - py_, c['f'][2] - pz_)
    s = size(c)
    rows.append('            {' + f"{g}, {c['uv'][0]}, {c['uv'][1]}, " + ', '.join(f'{v:.3f}F' for v in (*f, *s)) + '},')
java = '''package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Carapace du Kame Kame no Mi (fruit de la Tortue), ajustée au torse du joueur (repère de la pièce « body » du joueur : x -4..4,
 * y 0..12 vers le bas, l'avant en -z). FICHIER GÉNÉRÉ par src/blockbench/kame/make_kame_shell.py (source Blockbench :
 * kame_shell.bbmodel) : ne pas éditer ici, modifier le générateur puis le relancer.
 * <ul>
 *   <li>{@link #dorsal} : grande carapace bombée dans le dos (gradins, écailles vertébrales et costales, bordure marginale),
 *       pivot au milieu du dos (pour la faire pousser) ;</li>
 *   <li>{@link #ventral} : plastron devant le torse (6 écailles claires) et ponts sur les flancs, pivot au milieu du ventre.</li>
 * </ul>
 * Utilisé sur le joueur (KameShellLayer) et seul dans le monde (KameShellRenderer, carapace posée ou tournoyante).
 */
public class KameShellModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("kame_shell"), "main");

    /** groupe (0 dos, 1 ventre), u, v, coin x y z (relatif au pivot du groupe), taille x y z. */
    private static final float[][] CUBES = {
%s
    };

    public final ModelPart root;
    public final ModelPart dorsal;
    public final ModelPart ventral;

    public KameShellModel(ModelPart root) {
        this.root = root;
        this.dorsal = root.getChild("dorsal");
        this.ventral = root.getChild("ventral");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder dorsal = CubeListBuilder.create();
        CubeListBuilder ventral = CubeListBuilder.create();
        for (float[] c : CUBES) {
            (c[0] == 0 ? dorsal : ventral).texOffs((int) c[1], (int) c[2]).addBox(c[3], c[4], c[5], c[6], c[7], c[8]);
        }
        root.addOrReplaceChild("dorsal", dorsal, PartPose.offset(%sF, %sF, %sF));
        root.addOrReplaceChild("ventral", ventral, PartPose.offset(%sF, %sF, %sF));
        return LayerDefinition.create(mesh, %d, %d);
    }
}
''' % ('\n'.join(rows), *PIVOT['dorsal'], *PIVOT['ventral'], TW, TH)
open(JAVA, 'w').write(java)

# ---------------------------------------------------------------- Blockbench (source)
buf = io.BytesIO()
tex.save(buf, 'PNG')
elements, groups = [], {'dorsal': [], 'ventral': []}
for i, c in enumerate(CUBES):
    u = str(uuid.UUID(int=random.Random(i * 7 + 1).getrandbits(128)))
    # Blockbench (Y vers le haut, pieds en 0) : y_bb = 24 - y
    f = [-c['t'][0], 24 - c['t'][1], c['f'][2]]
    t = [-c['f'][0], 24 - c['f'][1], c['t'][2]]
    elements.append({'name': f"{c['group']}_{c['kind']}_{i}", 'box_uv': True, 'rescale': False, 'from': f, 'to': t,
                     'origin': [0, 18, 0], 'uv_offset': list(c['uv']), 'type': 'cube', 'uuid': u,
                     'faces': {k: {'uv': [0, 0, 0, 0], 'texture': 0} for k in ('north', 'east', 'south', 'west', 'up', 'down')}})
    groups[c['group']].append(u)
bb = {'meta': {'format_version': '4.10', 'model_format': 'modded_entity', 'box_uv': True}, 'name': 'kame_shell',
      'model_identifier': 'kame_shell', 'resolution': {'width': TW, 'height': TH}, 'elements': elements,
      'outliner': [{'name': g, 'origin': [-PIVOT[g][0], 24 - PIVOT[g][1], PIVOT[g][2]], 'rotation': [0, 0, 0],
                    'uuid': str(uuid.UUID(int=random.Random(g).getrandbits(128))), 'export': True, 'isOpen': False,
                    'visibility': True, 'children': ch} for g, ch in groups.items()],
      'textures': [{'name': 'kame_shell.png', 'id': '0', 'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH,
                    'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}]}
json.dump(bb, open(os.path.join(HERE, 'kame_shell.bbmodel'), 'w'), indent=1)

# ---------------------------------------------------------------- icônes 16x16 : fruit et 3 attaques
def icon(draw_fn, path):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im), im.load())
    im.save(path)


GREEN, GREEN_L, GREEN_D, BROWN, CREAM, INK = (84, 130, 56, 255), (130, 176, 84, 255), (46, 78, 30, 255), (120, 88, 40, 255), (232, 214, 150, 255), (28, 30, 20, 255)


def shell_icon(d, p, cx=8, cy=9):
    d.ellipse([cx - 6, cy - 5, cx + 6, cy + 5], fill=BROWN)
    d.ellipse([cx - 5, cy - 4, cx + 5, cy + 4], fill=GREEN)
    for (x, y) in ((cx - 2, cy - 2), (cx + 1, cy - 2), (cx - 3, cy + 1), (cx, cy + 1), (cx + 2, cy + 1)):
        d.rectangle([x, y, x + 1, y + 1], fill=GREEN_L)
    d.line([cx - 4, cy, cx + 4, cy], fill=GREEN_D)
    d.line([cx, cy - 4, cx, cy + 4], fill=GREEN_D)


def fruit(d, p):                     # fruit du démon vert à motifs d'écailles, tige recourbée
    d.ellipse([2, 3, 14, 15], fill=GREEN_D)
    d.ellipse([3, 4, 13, 14], fill=GREEN)
    for (x, y) in ((5, 6), (9, 6), (4, 10), (8, 9), (11, 11), (7, 12)):
        d.ellipse([x, y, x + 2, y + 2], outline=GREEN_L)
    d.point([(6, 6), (5, 7)], fill=(190, 220, 140, 255))
    d.line([8, 3, 8, 1], fill=BROWN)
    d.line([8, 1, 11, 0], fill=BROWN)
    d.point([(12, 1), (13, 1)], fill=GREEN_L)


def ab_shell(d, p):
    shell_icon(d, p)
    d.line([1, 2, 3, 4], fill=CREAM)
    d.line([14, 2, 12, 4], fill=CREAM)


def ab_pocket(d, p):
    shell_icon(d, p, 8, 10)
    d.rectangle([6, 1, 10, 5], fill=(80, 60, 160, 255))
    d.rectangle([7, 2, 9, 4], fill=(170, 150, 255, 255))


def ab_spin(d, p):
    shell_icon(d, p)
    for a in range(0, 360, 45):
        r = math.radians(a)
        d.point((8 + round(7.4 * math.cos(r)), 9 + round(6.4 * math.sin(r))), fill=CREAM)
    d.arc([0, 1, 15, 16], 200, 300, fill=(240, 240, 240, 255))


# Icônes du fruit et des attaques : remplacées par les images fournies (128 x 128) ; le générateur ne les écrit plus.
# Pour revenir aux icônes générées : python3 make_kame_shell.py --icones
if '--icones' in sys.argv:
    sys.argv.remove('--icones')
    icon(fruit, os.path.join(ASSETS, 'textures', 'item', 'kame_kame_no_mi.png'))
    ab = os.path.join(ASSETS, 'textures', 'gui', 'ability', 'kame')
    os.makedirs(ab, exist_ok=True)
    icon(ab_shell, os.path.join(ab, 'kame_shell.png'))
    icon(ab_pocket, os.path.join(ab, 'kame_pocket.png'))
    icon(ab_spin, os.path.join(ab, 'kame_spin.png'))

# ---------------------------------------------------------------- aperçus (torse du joueur + carapace)
if len(sys.argv) > 1:
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    FACES = {'north': (2, 0), 'south': (2, 1), 'west': (0, 0), 'east': (0, 1), 'up': (1, 0), 'down': (1, 1)}

    def boxes_of():
        bx = [dict(f=(-4, 0, -2), t=(4, 12, 2), col=(70, 120, 200)), dict(f=(-4, -8, -4), t=(4, 0, 4), col=(220, 170, 130)),
              dict(f=(4, 0, -2), t=(8, 12, 2), col=(220, 170, 130)), dict(f=(-8, 0, -2), t=(-4, 12, 2), col=(220, 170, 130)),
              dict(f=(-4, 12, -2), t=(0, 24, 2), col=(60, 60, 140)), dict(f=(0, 12, -2), t=(4, 24, 2), col=(60, 60, 140))]
        for c in CUBES:
            base = PAL[c['kind']][0]
            bx.append(dict(f=c['f'], t=c['t'], col=base, shell=c))
        return bx

    def render(path, yaw, pitch=12):
        S = 14
        im = Image.new('RGB', (420, 460), (150, 180, 210))
        d = ImageDraw.Draw(im)
        cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

        def P(x, y, z):
            y = -y                                   # repère du modèle : y vers le bas
            x, z = x * cy - z * sy, x * sy + z * cy
            y, z = y * cp - z * sp, y * sp + z * cp
            return 210 + x * S, 150 - y * S, z
        polys = []
        for b in boxes_of():
            (x0, y0, z0), (x1, y1, z1) = b['f'], b['t']
            corners = {(i, j, k): (x1 if i else x0, y1 if j else y0, z1 if k else z0) for i in (0, 1) for j in (0, 1) for k in (0, 1)}
            faces = [((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)), ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
                     ((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)), ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
                     ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1))]
            for fi, f in enumerate(faces):
                pts = [P(*corners[c]) for c in f]
                shade = (0.8, 0.8, 0.65, 0.65, 1.0, 0.55)[fi]
                col = tuple(int(v * shade) for v in b['col'])
                polys.append((sum(p[2] for p in pts) / 4, [(p[0], p[1]) for p in pts], col, 'shell' in b))
        for _, pts, col, shell in sorted(polys, key=lambda p: -p[0]):
            d.polygon(pts, fill=col, outline=(30, 30, 20) if shell else None)
        im.save(path)

    for name, yaw in (('dos', 180), ('trois_quarts_dos', 145), ('profil', 90), ('face', 0), ('trois_quarts_face', 35)):
        render(os.path.join(out, f'kame_{name}.png'), yaw)
    tex.resize((TW * 3, TH * 3), Image.NEAREST).save(os.path.join(out, 'kame_texture.png'))
print('ok', len(CUBES), 'cubes')
