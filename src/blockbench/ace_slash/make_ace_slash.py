# -*- coding: utf-8 -*-
"""
Générateur de la « Lame dévastatrice du Haki des Rois » (attaque spéciale de l'épée Ace) : un croissant d'énergie géant,
coeur NOIR profond, dégradé rouge sombre puis rouge incandescent, tranchant rouge vif, bords extérieurs déchiquetés (pointes
noires) et halo cramoisi translucide.

Sorties :
  - ace_slash.bbmodel           : modèle source (Blockbench), texture incluse ;
  - ace_slash.png               : la texture (aussi copiée dans assets/bloxpiece/textures/entity/ace_slash.png) ;
  - client/.../AceSlashModel.java : le modèle du jeu (liste de cubes générée ici, NE PAS éditer à la main) ;
  - aperçus PNG si un dossier est passé en argument : python3 make_ace_slash.py <dossier>

Repère (pixels de modèle, 16 px = 1 bloc) : X = envergure, Y = épaisseur, Z = sens du vol (+Z vers l'avant). Le croissant est
couché dans le plan X-Z : sa bosse courbée (tranchant rouge) MÈNE vers l'avant, ses pointes et son dos noir déchiqueté sont
rejetés vers l'arrière. L'inclinaison en diagonale (roulis autour de l'axe du vol) est appliquée par le renderer. La forme
(HS, BOW_O, BOW_I, CENTER_Z, THICK) doit rester identique à AceSlashShape.java (collisions).
"""
import base64, io, json, math, os, random, sys, uuid
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(SRC, 'main', 'resources', 'assets', 'bloxpiece')
JAVA = os.path.join(SRC, 'client', 'java', 'nolan', 'bloxpiece', 'client', 'entity', 'AceSlashModel.java')

# ---------------------------------------------------------------- forme (identique à AceSlashShape.java, en pixels)
# Le croissant est COUCHÉ dans le plan du vol (plan X-Z) : sa bosse (le bord courbé) est tournée vers l'avant (+Z), ses deux
# pointes rejetées vers l'arrière. Son épaisseur (Y) est bien visible et s'affine vers les pointes. Le roulis (diagonale)
# est appliqué par le renderer autour de l'axe du vol : le plan du croissant = le plan du coup d'épée.
HS = 64.0        # demi-envergure : 8 blocs d'une pointe à l'autre
BOW_O = 32.0     # flèche du bord avant (tranchant, la bosse qui mène)
BOW_I = 7.0      # flèche du bord arrière (dos noir, côté creux)
CENTER_Z = 14.0  # recentrage : la lame est centrée sur la position de l'entité
THICK = 9.0      # épaisseur maximale (Y) au centre
SEG = 28         # tronçons le long de l'envergure

def z_at(s, f):
    """Position avant-arrière à l'abscisse s (-1..1), fraction f de la largeur (0 = tranchant avant, 1 = dos arrière)."""
    return (BOW_O - f * (BOW_O - BOW_I)) * (1.0 - s * s) - CENTER_Z

def thick_at(s, f):
    """Épaisseur (Y) : maximale au centre et vers le dos, fine au tranchant, effilée aux pointes, légèrement ondulée."""
    k = (1.0 - s * s) ** 0.7
    edge = 0.25 + 0.75 * min(1.0, f / 0.6)                 # le tranchant est fin, le dos massif
    organic = 1.0 + 0.12 * math.sin(s * 7.0 + f * 3.0)     # légère variation : rendu organique
    return max(0.5, THICK * k * edge * organic)

# couches de la largeur : (texture, fraction début, fraction fin)
LAYERS = [
    ('edge', 0.00, 0.08),   # tranchant rouge vif
    ('hot', 0.08, 0.24),    # rouge incandescent
    ('dark', 0.24, 0.46),   # rouge sombre
    ('black', 0.46, 1.00),  # noir profond (dos)
]
# zones de texture (128 x 128) : une couleur par carré de 64 px
TEXOFF = {'black': (0, 0), 'dark': (64, 0), 'hot': (0, 64), 'edge': (64, 64)}

# ---------------------------------------------------------------- texture
def make_texture():
    rnd = random.Random(7)
    img = Image.new('RGBA', (128, 128))
    px = img.load()
    for y in range(128):
        for x in range(128):
            n = rnd.random()
            zone = ('black', 'dark', 'hot', 'edge')[(x >= 64) + 2 * (y >= 64)]
            vein = (math.sin(x * 0.9 + y * 0.35) + math.sin(x * 0.27 - y * 1.1)) * 0.5  # veines d'énergie
            if zone == 'black':
                c = (8 + int(n * 8), 1, 3) if vein < 0.75 else (60 + int(n * 30), 0, 8)  # noir, rares veines rouges
            elif zone == 'dark':
                c = (92 + int(n * 30 + vein * 14), 4, 10) if n > 0.08 else (20, 0, 3)
            elif zone == 'hot':
                c = (214 + int(n * 30), 18 + int(max(0, vein) * 30), 14)
            else:
                c = (255, 70 + int(n * 60 + max(0, vein) * 50), 58 + int(n * 40))  # rouge vif presque blanc par endroits
            px[x, y] = c + (255,)
    return img

# ---------------------------------------------------------------- cubes
CUBES = []  # dict(group, tex, c=(x,y,z) centre, size=(w,h,d), rot=(rx,ry,rz) degrés)

def add(group, tex, c, size, rot):
    CUBES.append(dict(group=group, tex=tex, c=c, size=tuple(max(0.25, v) for v in size), rot=rot))

def yaw_rot(x0, z0, x1, z1):
    """Longueur et rotation Y (degrés) qui alignent l'axe X d'un cube sur le segment (x0,z0)->(x1,z1) du plan X-Z."""
    dx, dz = x1 - x0, z1 - z0
    return math.hypot(dx, dz), -math.degrees(math.atan2(dz, dx))

def build():
    # corps : chaque tronçon, chaque couche (largeur = Z, épaisseur = Y)
    for i in range(SEG):
        s0, s1 = -1 + 2 * i / SEG, -1 + 2 * (i + 1) / SEG
        sm = (s0 + s1) / 2
        for tex, f0, f1 in LAYERS:
            fm = (f0 + f1) / 2
            l, ry = yaw_rot(s0 * HS, z_at(s0, fm), s1 * HS, z_at(s1, fm))
            w = (z_at(sm, f0) - z_at(sm, f1)) * math.cos(math.radians(ry)) + 0.35
            add('core', tex, (sm * HS, 0.0, z_at(sm, fm)), (l + 0.7, thick_at(sm, fm), w), (0.0, ry, 0.0))
        # halo : gaine translucide qui déborde de toute la lame
        l, ry = yaw_rot(s0 * HS, z_at(s0, 0.5), s1 * HS, z_at(s1, 0.5))
        w = (z_at(sm, 0) - z_at(sm, 1)) * math.cos(math.radians(ry)) + 5.0
        add('glow', 'edge', (sm * HS, 0.0, z_at(sm, 0.5)), (l + 1.2, thick_at(sm, 0.8) + 4.0, w), (0.0, ry, 0.0))
    rnd = random.Random(1337)
    # dos déchiqueté : pointes noires qui partent vers l'arrière (comme des flammes noires arrachées par la vitesse)
    n = 36
    for j in range(n):
        s = -0.95 + 1.9 * (j + rnd.uniform(0.15, 0.85)) / n
        k = 1 - s * s
        x, z = s * HS, z_at(s, 1.0)
        slope = math.degrees(math.atan2(-2 * s * BOW_I / HS, 1))   # pente du dos dans le plan X-Z
        a = slope - 90 + rnd.uniform(-30, 30) - s * 25            # vers l'arrière, évasé vers les pointes
        length = (3.0 + rnd.random() * 8.0) * (0.35 + 0.65 * k)
        cx = x + math.cos(math.radians(a)) * (length / 2 - 1.0)
        cz = z + math.sin(math.radians(a)) * (length / 2 - 1.0)
        yoff = rnd.uniform(-0.3, 0.3) * thick_at(s, 1.0)
        tex = 'black' if rnd.random() < 0.8 else 'dark'
        add('spikes', tex, (cx, yoff, cz), (length, 1.0 + 2.2 * k * rnd.random() + 0.6, 1.2 + rnd.random() * 1.6 * k),
            (rnd.uniform(-25, 25), -a, 0.0))
        if rnd.random() < 0.45 and k > 0.3:  # éclat secondaire plus fin
            a2 = a + rnd.choice((-1, 1)) * rnd.uniform(25, 45)
            l2 = length * 0.55
            add('spikes', 'black', (x + math.cos(math.radians(a2)) * (l2 / 2 - 0.5), yoff + rnd.uniform(-1, 1),
                                    z + math.sin(math.radians(a2)) * (l2 / 2 - 0.5)), (l2, 0.9, 0.9), (rnd.uniform(-30, 30), -a2, 0.0))
    # tranchant : éclats rouge vif qui crépitent juste devant le bord avant
    for j in range(16):
        s = -0.82 + 1.64 * (j + rnd.random()) / 16
        k = 1 - s * s
        x, z = s * HS, z_at(s, 0.0)
        a = 90 + rnd.uniform(-40, 40) - s * 30
        l = (1.2 + rnd.random() * 2.4) * k
        add('spikes', 'edge', (x + math.cos(math.radians(a)) * (l / 2 - 0.4), rnd.uniform(-1, 1) * k,
                               z + math.sin(math.radians(a)) * (l / 2 - 0.4)), (l, 0.6, 0.6), (0.0, -a, 0.0))

build()
TEX = make_texture()

# ---------------------------------------------------------------- géométrie (sommets d'un cube tourné : R = Rz·Ry·Rx)
def rotate(v, rot):
    x, y, z = v
    rx, ry, rz = (math.radians(a) for a in rot)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z

FACES = {  # coins locaux (signes) : ordre haut-gauche, haut-droit, bas-droit, bas-gauche
    'north': [(1, 1, -1), (-1, 1, -1), (-1, -1, -1), (1, -1, -1)], 'south': [(-1, 1, 1), (1, 1, 1), (1, -1, 1), (-1, -1, 1)],
    'west': [(-1, 1, -1), (-1, 1, 1), (-1, -1, 1), (-1, -1, -1)], 'east': [(1, 1, 1), (1, 1, -1), (1, -1, -1), (1, -1, 1)],
    'up': [(-1, 1, -1), (1, 1, -1), (1, 1, 1), (-1, 1, 1)], 'down': [(-1, -1, 1), (1, -1, 1), (1, -1, -1), (-1, -1, -1)],
}
SHADE = {'north': 0.85, 'south': 0.85, 'east': 0.7, 'west': 0.7, 'up': 1.0, 'down': 0.6}

def world_quads(cube, roll=0.0):
    w, h, d = cube['size']
    out = []
    for fn, corners in FACES.items():
        pts = []
        for sx, sy, sz in corners:
            p = rotate((sx * w / 2, sy * h / 2, sz * d / 2), cube['rot'])
            p = (p[0] + cube['c'][0], p[1] + cube['c'][1], p[2] + cube['c'][2])
            pts.append(rotate(p, (0, 0, roll)))
        out.append((fn, pts))
    return out

# ---------------------------------------------------------------- aperçus (rendu logiciel, peintre + texture échantillonnée)
def tex_color(tex, a, b, seed):
    u0, v0 = TEXOFF[tex]
    return TEX.getpixel((u0 + int(a * 63) % 64, v0 + int(b * 63 + seed) % 64))[:3]

def render(path, yaw, pitch, roll=-35.0, size=(1500, 900), sc=9.0, glow=True, bg=(14, 12, 16), afterimages=0):
    cyw, syw = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    off = [0.0, 0.0]

    def proj(p, shift=0.0):
        x, y, z = p[0], p[1], p[2] - shift
        # caméra : yaw autour de Y, puis pitch ; on regarde vers +Z (depuis le lanceur, derrière la lame) ; écran droite = -X
        x, z = x * cyw - z * syw, x * syw + z * cyw
        y, z = y * cp - z * sp, y * sp + z * cp
        return (off[0] - x * sc, off[1] - y * sc, z)

    # cadrage : la lame entière (et ses images rémanentes) centrée dans l'image
    pts = [proj(p) for cube in CUBES for _, q in world_quads(cube, roll) for p in q]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    fit = min(size[0] * 0.9 / (max(xs) - min(xs)), size[1] * 0.85 / (max(ys) - min(ys)), 1.0)
    sc *= fit
    off[0], off[1] = size[0] / 2 - (min(xs) + max(xs)) / 2 * fit, size[1] / 2 - (min(ys) + max(ys)) / 2 * fit

    base = Image.new('RGB', size, bg)
    layers = []
    for ghost in (0,):
        shift = 0.0
        alpha = 1.0
        polys, glows = [], []
        for ci, cube in enumerate(CUBES):
            for fn, pts in world_quads(cube, roll):
                if cube['group'] == 'glow':
                    glows.append([proj(p, shift)[:2] for p in pts])
                    continue
                n = 3 if max(cube['size']) > 4 else 1
                for i in range(n):
                    for j in range(n):
                        a0, a1, b0, b1 = i / n, (i + 1) / n, j / n, (j + 1) / n

                        def lerp(a, b):
                            top = [pts[0][k] + (pts[1][k] - pts[0][k]) * a for k in range(3)]
                            bot = [pts[3][k] + (pts[2][k] - pts[3][k]) * a for k in range(3)]
                            return [top[k] + (bot[k] - top[k]) * b for k in range(3)]
                        q = [proj(lerp(a0, b0), shift), proj(lerp(a1, b0), shift), proj(lerp(a1, b1), shift), proj(lerp(a0, b1), shift)]
                        c = tex_color(cube['tex'], (a0 + a1) / 2 + ci * 0.37, (b0 + b1) / 2, ci * 7)
                        sh = SHADE[fn] * (1.25 if cube['tex'] in ('edge', 'hot') else 1.0)
                        polys.append((sum(p[2] for p in q) / 4, [(p[0], p[1]) for p in q], tuple(min(255, int(v * sh)) for v in c)))
        layer = Image.new('RGBA', size, (0, 0, 0, 0))
        if glow:  # halo cramoisi flou
            g = Image.new('RGBA', size, (0, 0, 0, 0))
            gd = ImageDraw.Draw(g)
            for pts in glows:
                gd.polygon(pts, fill=(235, 20, 30, 40))
            g = g.filter(ImageFilter.GaussianBlur(sc * 1.6))
            layer = Image.alpha_composite(layer, g)
        body = Image.new('RGBA', size, (0, 0, 0, 0))
        bd = ImageDraw.Draw(body)
        for _, pts, c in sorted(polys, key=lambda p: -p[0]):  # du plus loin au plus proche
            bd.polygon(pts, fill=c + (255,))
        layer = Image.alpha_composite(layer, body)
        if alpha < 1.0:
            r, gch, b, a = layer.split()
            a = a.point(lambda v: int(v * alpha))
            layer = Image.merge('RGBA', (r, gch, b, a))
        layers.append(layer)
    out = base.convert('RGBA')
    for layer in layers:
        out = Image.alpha_composite(out, layer)
    if afterimages:  # éclairs du Haki des Rois autour de la lame (illustration)
        dr = ImageDraw.Draw(out)
        rnd = random.Random(5)
        for _ in range(9):
            s = rnd.uniform(-0.8, 0.8)
            p = proj(rotate((s * HS, 0.0, z_at(s, rnd.random())), (0, 0, roll)))
            x, y = p[0], p[1]
            pts = [(x, y)]
            ang = rnd.uniform(0, 6.28)
            for _ in range(7):
                ang += rnd.uniform(-0.9, 0.9)
                x += math.cos(ang) * sc * 3.2
                y += math.sin(ang) * sc * 3.2
                pts.append((x, y))
            dr.line(pts, fill=(255, 30, 30, 255), width=int(sc * 0.9))
            dr.line(pts, fill=(5, 0, 2, 255), width=max(1, int(sc * 0.35)))
    out.convert('RGB').save(path)

# ---------------------------------------------------------------- Blockbench (source éditable)
def write_bbmodel(path):
    buf = io.BytesIO()
    TEX.save(buf, 'PNG')
    elements, groups = [], {}
    for i, cube in enumerate(CUBES):
        w, h, d = cube['size']
        x, y, z = cube['c']
        u = str(uuid.UUID(int=random.Random(i).getrandbits(128)))
        elements.append({
            'name': f"{cube['group']}_{cube['tex']}_{i}", 'box_uv': True, 'rescale': False, 'locked': False,
            'from': [x - w / 2, y - h / 2, z - d / 2], 'to': [x + w / 2, y + h / 2, z + d / 2],
            'origin': [x, y, z], 'rotation': list(cube['rot']), 'uv_offset': list(TEXOFF[cube['tex']]),
            'autouv': 0, 'color': {'black': 0, 'dark': 1, 'hot': 2, 'edge': 3}[cube['tex']], 'inflate': 0.0,
            'type': 'cube', 'uuid': u, 'faces': {f: {'uv': [0, 0, 0, 0], 'texture': 0} for f in FACES},
        })
        groups.setdefault(cube['group'], []).append(u)
    outliner = [{'name': g, 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'uuid': str(uuid.UUID(int=random.Random(g).getrandbits(128))),
                 'export': True, 'isOpen': False, 'visibility': True, 'autouv': 0, 'children': ch} for g, ch in groups.items()]
    data = {
        'meta': {'format_version': '4.10', 'model_format': 'modded_entity', 'box_uv': True},
        'name': 'ace_slash', 'model_identifier': 'ace_slash', 'modded_entity_entity_class': 'AceSlash',
        'modded_entity_version': 'Mojmaps', 'visible_box': [8, 3, 0], 'resolution': {'width': 128, 'height': 128},
        'elements': elements, 'outliner': outliner,
        'textures': [{'name': 'ace_slash.png', 'id': '0', 'width': 128, 'height': 128, 'uv_width': 128, 'uv_height': 128,
                      'particle': True, 'render_mode': 'default', 'visible': True, 'saved': True,
                      'uuid': '7c1d2a6e-5b1a-4c3e-9f0a-ace5a5a5a5a5',
                      'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}],
    }
    with open(path, 'w') as f:
        json.dump(data, f, indent=1)

# ---------------------------------------------------------------- modèle Java (généré)
def write_java(path):
    rows = []
    for cube in CUBES:
        g = {'core': 0, 'glow': 1, 'spikes': 2}[cube['group']]
        u, v = TEXOFF[cube['tex']]
        vals = [*cube['c'], *cube['size'], *cube['rot']]
        rows.append('            {' + f'{g}, {u}, {v}, ' + ', '.join(f'{x:.3f}F' for x in vals) + '},')
    java = f'''package nolan.bloxpiece.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import nolan.bloxpiece.BloxPiece;

/**
 * Modèle 3D de la Lame dévastatrice du Haki des Rois (entité AceSlash) : un croissant d'énergie géant (8 blocs d'envergure).
 * FICHIER GÉNÉRÉ par src/blockbench/ace_slash/make_ace_slash.py (source Blockbench : ace_slash.bbmodel) : ne pas éditer ici,
 * modifier le générateur puis le relancer.
 *
 * Trois groupes : "core" (corps : tranchant rouge vif, rouge incandescent, rouge sombre, dos noir profond), "spikes" (bords
 * déchiquetés noirs et éclats rouges) et "glow" (gaine cramoisie translucide, dessinée à part par AceSlashRenderer).
 * Repère (pixels, 16 px = 1 bloc) : X = envergure, Y = haut, Z = sens du vol ; le renderer applique direction et roulis, et dessine
 * chaque groupe avec submitModelPart (couleur et transparence par groupe : la lame s'estompe en fin de course).
 */
public class AceSlashModel {{

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BloxPiece.id("ace_slash"), "main");

    /** groupe (0 corps, 1 halo, 2 pointes), u, v, centre x y z, taille x y z, rotation x y z (degrés). */
    private static final float[][] CUBES = {{
{chr(10).join(rows)}
    }};

    public final ModelPart core;
    public final ModelPart glow;
    public final ModelPart spikes;

    public AceSlashModel(ModelPart root) {{
        this.core = root.getChild("core");
        this.glow = root.getChild("glow");
        this.spikes = root.getChild("spikes");
    }}

    public static LayerDefinition createLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition[] groups = {{
                root.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.ZERO),
                root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.ZERO),
                root.addOrReplaceChild("spikes", CubeListBuilder.create(), PartPose.ZERO),
        }};
        float deg = (float) (Math.PI / 180.0);
        for (int i = 0; i < CUBES.length; i++) {{
            float[] c = CUBES[i];
            // chaque cube est centré sur le pivot de sa pièce, elle-même placée et tournée (Z, puis Y, puis X)
            groups[(int) c[0]].addOrReplaceChild("c" + i,
                    CubeListBuilder.create().texOffs((int) c[1], (int) c[2]).addBox(-c[6] / 2, -c[7] / 2, -c[8] / 2, c[6], c[7], c[8]),
                    PartPose.offsetAndRotation(c[3], c[4], c[5], c[9] * deg, c[10] * deg, c[11] * deg));
        }}
        return LayerDefinition.create(mesh, 128, 128);
    }}
}}
'''
    with open(path, 'w') as f:
        f.write(java)

TEX.save(os.path.join(HERE, 'ace_slash.png'))
TEX.save(os.path.join(ASSETS, 'textures', 'entity', 'ace_slash.png'))
write_bbmodel(os.path.join(HERE, 'ace_slash.bbmodel'))
write_java(JAVA)

if len(sys.argv) > 1:
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    views = [('1_vue_lanceur', 0, 12, -35, 0), ('2_trois_quarts', 35, 30, -35, 0), ('3_profil', 90, 0, 0, 0),
             ('4_dessus', 0, 89, 0, 0), ('5_face_cible', 180, 10, -35, 0), ('6_en_vol', 25, 40, -35, 1)]
    for name, yaw, pitch, roll, ghosts in views:
        render(os.path.join(out, f'ace_slash_{name}.png'), yaw, pitch, roll, afterimages=ghosts)
    # planche de texture agrandie
    TEX.resize((512, 512), Image.NEAREST).save(os.path.join(out, 'ace_slash_texture.png'))
print('ok :', len(CUBES), 'cubes')
