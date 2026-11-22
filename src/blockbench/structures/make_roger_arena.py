# -*- coding: utf-8 -*-
"""
Arène antique en ruine (colisée) de Gol D. Roger (structure générée naturellement) : écrit
data/bloxpiece/structure/roger_arena.nbt et des aperçus. Même méthode que le Camp de bandits (make_bandit_camp.py).

Repère : x, z 0..44 (centre de l'arène : 22, 22), y = 0 : couche du sol (remplace la surface du terrain), y >= 1 : au-dessus.
L'entrée principale est au SUD (+z). Seul le disque de rayon 22,5 est écrit : les coins de la boîte sont du « vide de structure »
(le terrain d'origine n'y est pas touché).

Plan (r = distance au centre, en blocs) :
  - r <= 10,5 : ARÈNE (diamètre 21), sol plat à y = 0 en pierre variée (pierre lisse, pierres taillées, andésite polie),
    motifs : cercle de pierres sculptées au centre (repère du boss, au ras du sol), anneau de pierre sculptée à r = 5,
    8 rayons d'andésite polie, fissures (pierres taillées fissurées), quelques demi-dalles (relief léger, on marche dessus) et
    blocs manquants au bord (gravier, terre). Rien au-dessus du sol : zone de combat entièrement dégagée.
  - 11 <= r < 18 : GRADINS, 4 rangées qui montent d'un bloc chacune (y 1 à 4) ; le devant de chaque rangée est en escaliers
    tournés vers l'extérieur (sièges, et on monte d'un gradin à l'autre partout) ; trous, mousse, herbe ; un secteur effondré
    (gravats) au nord-ouest ; 4 braseros (feux de camp) sur le premier gradin.
  - 18 <= r < 20 : MUR EXTÉRIEUR épais (2 blocs), haut de 8 (sommet irrégulier), arches tout autour (ouvertures cintrées
    en bas, fenêtres en haut), deux brèches où il s'effondre jusqu'à 3 blocs, blocs tombés au pied.
  - r ~ 21,3 : 8 PILIERS antiques adossés au mur (base 3x3, fût, chapiteau 3x3 sculpté) ; deux sont cassés, leurs tronçons
    gisent au sol.
  - ENTRÉES : principale au sud (5 de large, 5 de haut) entre deux grandes colonnes de 11 blocs à bannières rouges, sous une
    arche ; 3 entrées secondaires (nord, est, ouest : 3 de large, 4 de haut) ; les couloirs traversent mur et gradins jusqu'à
    l'arène ; lanternes, tonneaux près de l'entrée principale.
Gol D. Roger (bloxpiece:gol_d_roger, l'entité existante) : UN seul, au centre exact (22,5 ; 1 ; 22,5), pieds sur le sol,
tourné vers l'entrée principale (Rotation 0 = sud), persistant, avec la marque Fabric « bloxpiece:arena_boss » (ModAttachments.ARENA_BOSS ; GolDRoger :
il garde l'arène). Minecraft le pose UNE fois, à la génération du tronçon qui le contient, puis il est sauvegardé avec ce
tronçon : jamais recréé au rechargement, jamais réapparu après sa mort.
Usage (depuis la racine du projet) : python3 src/blockbench/structures/make_roger_arena.py [dossier_apercus]
"""
import math, os, random, sys
import nbtlib
from nbtlib import Compound, List, Int, Double, String, Byte, Float

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, 'src/main/resources/data/bloxpiece/structure/roger_arena.nbt')
SX, SY, SZ = 45, 13, 45
C = 22                      # centre (bloc 22 : centre exact 22,5)
R_ARENA, R_TIERS, R_WALL, R_OUT, R_MAX = 10.5, 11.0, 18.0, 20.0, 22.5
TIER_W = (R_WALL - R_TIERS) / 4.0
rnd = random.Random(20261121)
G = {}


def put(x, y, z, b):
    assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z, b)
    G[(x, y, z)] = b


def get(x, y, z):
    return G.get((x, y, z))


def polar(x, z):
    dx, dz = x + 0.5 - (C + 0.5), z + 0.5 - (C + 0.5)
    return math.hypot(dx, dz), math.degrees(math.atan2(dz, dx)) % 360.0   # angle : 0 = est, 90 = sud, 180 = ouest, 270 = nord


def ang_diff(a, b):
    return abs((a - b + 180.0) % 360.0 - 180.0)


def outward(x, z):
    """Direction (n/s/e/w) qui s'éloigne du centre (escaliers tournés vers l'extérieur)."""
    dx, dz = x - C, z - C
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


def stairs(block, facing, half='bottom'):
    return f'minecraft:{block}[facing={facing},half={half},shape=straight,waterlogged=false]'


def slab(block, kind='bottom'):
    return f'minecraft:{block}[type={kind},waterlogged=false]'


def brick():
    """Pierre taillée vieillie : surtout normale, souvent moussue ou fissurée."""
    r = rnd.random()
    return ('minecraft:stone_bricks' if r < 0.52 else 'minecraft:mossy_stone_bricks' if r < 0.74
            else 'minecraft:cracked_stone_bricks' if r < 0.95 else 'minecraft:chiseled_stone_bricks')


DISC = [(x, z) for x in range(SX) for z in range(SZ) if polar(x, z)[0] <= R_MAX]

# ---------------------------------------------------------------- 0) air sur tout le disque (dégage arbres, herbes, terrain)
for x, z in DISC:
    for y in range(1, SY):
        put(x, y, z, 'minecraft:air')

# ---------------------------------------------------------------- entrées (angles) : on les garde libres partout
ENTRANCES = {'south': (90.0, 2), 'north': (270.0, 1), 'east': (0.0, 1), 'west': (180.0, 1)}   # angle, demi-largeur


def in_corridor(x, z):
    """Case dans un couloir d'entrée (bande droite de 3 ou 5 blocs, de l'arène jusqu'au bord) ?"""
    dx, dz = x - C, z - C
    for name, (a, half) in ENTRANCES.items():
        if name in ('south', 'north') and abs(dx) <= half and (dz > 0) == (name == 'south') and abs(dz) >= 9:
            return name
        if name in ('east', 'west') and abs(dz) <= half and (dx > 0) == (name == 'east') and abs(dx) >= 9:
            return name
    return None


# ---------------------------------------------------------------- 1) sol (y = 0)
for x, z in DISC:
    r, a = polar(x, z)
    if r <= R_ARENA:
        # sol de l'arène : mélange de pierres
        k = rnd.random()
        b = ('minecraft:smooth_stone' if k < 0.34 else 'minecraft:stone_bricks' if k < 0.62
             else 'minecraft:polished_andesite' if k < 0.76 else 'minecraft:cracked_stone_bricks' if k < 0.9
             else 'minecraft:andesite' if k < 0.96 else 'minecraft:mossy_stone_bricks')
        if 4.5 <= r <= 5.5:
            b = 'minecraft:chiseled_stone_bricks' if int(a / 15) % 2 == 0 else 'minecraft:polished_andesite'   # anneau
        elif r > 5.5 and min(ang_diff(a, s) for s in range(0, 360, 45)) * math.pi / 180 * r < 0.6:
            b = 'minecraft:polished_andesite'                                                             # 8 rayons
        if r <= 1.0:
            b = 'minecraft:chiseled_stone_bricks'                                                          # repère du boss
        elif r <= 2.3:
            b = 'minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:chiseled_stone_bricks'
        elif r <= 2.9:
            b = 'minecraft:stone_bricks'
        put(x, 0, z, b)
    elif r < R_OUT:
        put(x, 0, z, 'minecraft:stone_bricks' if rnd.random() < 0.7 else 'minecraft:cobblestone')
    else:
        k = rnd.random()
        put(x, 0, z, 'minecraft:grass_block[snowy=false]' if k < 0.62 else 'minecraft:coarse_dirt' if k < 0.78
            else 'minecraft:gravel' if k < 0.88 else 'minecraft:cobblestone')

# fissures dans l'arène : 5 traînées de pierres fissurées qui partent du bord
for _ in range(5):
    a = math.radians(rnd.uniform(0, 360))
    r = rnd.uniform(6.0, 9.5)
    for step in range(rnd.randint(4, 7)):
        x, z = int(C + 0.5 + r * math.cos(a)), int(C + 0.5 + r * math.sin(a))
        if polar(x, z)[0] <= R_ARENA and polar(x, z)[0] > 2.9:
            put(x, 0, z, 'minecraft:cracked_stone_bricks')
        r -= 1.0
        a += rnd.uniform(-0.18, 0.18)
# bord abîmé : blocs manquants (gravier, terre) et demi-dalles (relief très léger)
for x, z in DISC:
    r, a = polar(x, z)
    if 9.3 <= r <= R_ARENA and rnd.random() < 0.18:
        put(x, 0, z, rnd.choice(['minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:moss_block']))
    elif 7.5 <= r <= R_ARENA and rnd.random() < 0.06 and not in_corridor(x, z):
        put(x, 1, z, slab(rnd.choice(['stone_brick_slab', 'smooth_stone_slab', 'cobblestone_slab'])))

# ---------------------------------------------------------------- 2) gradins (4 rangées, y 1 à 4)
COLLAPSE = (215.0, 250.0)          # secteur effondré (nord-ouest)
for x, z in DISC:
    r, a = polar(x, z)
    if not (R_TIERS <= r < R_WALL) or in_corridor(x, z):
        continue
    k = min(3, int((r - R_TIERS) / TIER_W))
    top = k + 1
    front = (r - R_TIERS) - k * TIER_W < 0.95          # devant de la rangée : sièges en escaliers
    collapsed = COLLAPSE[0] <= a <= COLLAPSE[1]
    if collapsed:
        top = max(1, top - 2 - (1 if rnd.random() < 0.5 else 0))
    for y in range(1, top):
        put(x, y, z, brick())
    if collapsed:
        put(x, top, z, rnd.choice(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:gravel',
                                   'minecraft:cracked_stone_bricks']))
        if rnd.random() < 0.25:
            put(x, top + 1, z, rnd.choice(['minecraft:cobblestone', 'minecraft:mossy_cobblestone']))
        continue
    if rnd.random() < 0.06 and top > 1:
        continue                                        # trou : il manque le bloc du dessus
    if front:
        put(x, top, z, stairs('mossy_stone_brick_stairs' if rnd.random() < 0.3 else 'stone_brick_stairs', outward(x, z)))
    else:
        put(x, top, z, brick())
        if rnd.random() < 0.10:
            put(x, top + 1, z, 'minecraft:moss_carpet')
        elif rnd.random() < 0.07:
            put(x, top + 1, z, rnd.choice(['minecraft:short_grass', 'minecraft:fern']))

# braseros sur le premier gradin (diagonales)
for deg in (45, 135, 225, 315):
    x = int(C + 0.5 + 11.6 * math.cos(math.radians(deg)))
    z = int(C + 0.5 + 11.6 * math.sin(math.radians(deg)))
    put(x, 1, z, 'minecraft:chiseled_stone_bricks')
    put(x, 2, z, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
# crânes sur les gradins (traces d'anciens combats)
for deg, rr in ((160, 15.5), (20, 13.0), (300, 16.6)):
    x = int(C + 0.5 + rr * math.cos(math.radians(deg)))
    z = int(C + 0.5 + rr * math.sin(math.radians(deg)))
    y = max(y for y in range(SY) if (get(x, y, z) or 'minecraft:air') not in ('minecraft:air', 'minecraft:moss_carpet',
                                                                            'minecraft:short_grass', 'minecraft:fern'))
    if 'stairs' not in get(x, y, z):
        put(x, y + 1, z, 'minecraft:skeleton_skull[powered=false,rotation=%d]' % rnd.randint(0, 15))

# ---------------------------------------------------------------- 3) mur extérieur (2 d'épaisseur, haut de 8, ruiné)
BREACHES = ((140.0, 14.0), (320.0, 11.0))   # (angle, demi-largeur en degrés) : le mur s'effondre


def wall_height(a):
    h = 8
    for c, w in BREACHES:
        d = ang_diff(a, c)
        if d < w:
            h = min(h, 3 + int(5 * d / w))
    if (int(a / 7.5) * 37) % 11 == 0:
        h -= 1                                   # sommet irrégulier
    return h


ARCH_STEP = 22.5
for x, z in DISC:
    r, a = polar(x, z)
    if not (R_WALL <= r < R_OUT):
        continue
    corridor = in_corridor(x, z)
    h = wall_height(a)
    # arche la plus proche : décalage tangentiel (en blocs) à son axe
    na = round(a / ARCH_STEP) * ARCH_STEP
    t = ang_diff(a, na) * math.pi / 180.0 * r
    is_arch = not any(ang_diff(na, e[0]) < 10 for e in ENTRANCES.values())
    is_window = ang_diff(a, na + ARCH_STEP / 2) * math.pi / 180.0 * r < 0.6
    for y in range(1, h + 1):
        if corridor:
            half = ENTRANCES[corridor][1]
            if y <= (5 if corridor == 'south' else 4):
                continue
        if is_arch and r >= 19.0 and ((y <= 3 and t < 1.5) or (y == 4 and t < 0.6)):
            continue                                                     # arche (niche cintrée, côté extérieur)
        if is_window and h >= 7 and y in (6, 7):
            continue                                                     # fenêtres de l'étage
        put(x, y, z, brick())
    if h >= 7 and r >= 19.0 and not corridor:
        if rnd.random() < 0.5:
            put(x, h + 1, z, slab('stone_brick_slab'))                   # couronnement
        if (int(a) % 30) < 2 and rnd.random() < 0.6:
            put(x, h + 1, z, 'minecraft:lantern[hanging=false,waterlogged=false]')
# corniche entre les deux niveaux (y = 5), côté extérieur
for x, z in DISC:
    r, a = polar(x, z)
    if 19.0 <= r < R_OUT and get(x, 5, z) and 'stone_bricks' in get(x, 5, z) and rnd.random() < 0.85:
        put(x, 5, z, 'minecraft:polished_andesite')

# gravats au pied des brèches et du mur
for x, z in DISC:
    r, a = polar(x, z)
    if R_OUT <= r <= R_MAX and not in_corridor(x, z):
        near_breach = min(ang_diff(a, c) for c, _ in BREACHES) < 20
        if rnd.random() < (0.30 if near_breach else 0.05):
            put(x, 1, z, rnd.choice(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:cracked_stone_bricks',
                                     'minecraft:mossy_stone_bricks', slab('stone_brick_slab'), slab('cobblestone_slab')]))
        elif rnd.random() < 0.12:
            put(x, 1, z, rnd.choice(['minecraft:short_grass', 'minecraft:fern', 'minecraft:short_grass']))

# ---------------------------------------------------------------- 4) piliers adossés au mur
PILLAR_ANGLES = [a for a in (22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5)]
BROKEN = {157.5: 4, 292.5: 3}                    # piliers cassés : hauteur du fût restant
for deg in PILLAR_ANGLES:
    px = int(round(C + 21.0 * math.cos(math.radians(deg))))
    pz = int(round(C + 21.0 * math.sin(math.radians(deg))))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if polar(px + dx, pz + dz)[0] <= R_MAX:
                put(px + dx, 1, pz + dz, 'minecraft:stone_bricks' if dx == 0 or dz == 0 else 'minecraft:chiseled_stone_bricks')
    put(px, 2, pz, 'minecraft:polished_andesite')
    shaft = BROKEN.get(deg, 7)
    for y in range(3, 3 + shaft):
        put(px, y, pz, 'minecraft:stone' if y % 3 else 'minecraft:polished_andesite')
    if deg in BROKEN:
        put(px, 3 + shaft, pz, 'minecraft:cracked_stone_bricks')
        # tronçon tombé à côté (couché vers l'extérieur et le long du mur)
        tdx, tdz = (1, 0) if abs(math.sin(math.radians(deg))) > 0.7 else (0, 1)
        for i in range(2, 5):
            qx, qz = px + tdx * i, pz + tdz * i
            if polar(qx, qz)[0] <= R_MAX:
                put(qx, 1, qz, 'minecraft:stone' if i < 4 else 'minecraft:polished_andesite')
    else:
        ytop = 3 + shaft
        put(px, ytop, pz, 'minecraft:chiseled_stone_bricks')
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if polar(px + dx, pz + dz)[0] <= R_MAX:
                put(px + dx, ytop, pz + dz, slab('stone_brick_slab', 'top'))   # chapiteau
        put(px, ytop + 1, pz, slab('smooth_stone_slab'))

# ---------------------------------------------------------------- 5) entrées
for x, z in DISC:
    c = in_corridor(x, z)
    if c:
        r = polar(x, z)[0]
        if r > R_ARENA:
            put(x, 0, z, 'minecraft:stone_bricks' if rnd.random() < 0.65 else 'minecraft:cracked_stone_bricks')
        top = 5 if c == 'south' else 4
        for y in range(1, top + 1):
            put(x, y, z, 'minecraft:air')
# entrée principale : deux grandes colonnes (11 blocs) de part et d'autre, arche au-dessus, bannières rouges
for side in (-1, 1):
    cx, cz = C + side * 4, 42
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(cx + dx, 1, cz + dz, 'minecraft:chiseled_stone_bricks' if dx and dz else 'minecraft:stone_bricks')
    for y in range(2, 10):
        put(cx, y, cz, 'minecraft:polished_andesite' if y in (2, 9) else 'minecraft:stone')
    put(cx, 10, cz, 'minecraft:chiseled_stone_bricks')
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        put(cx + dx, 10, cz + dz, slab('stone_brick_slab', 'top'))
    put(cx, 11, cz, slab('smooth_stone_slab'))
    put(cx, 7, cz + 1, 'minecraft:red_wall_banner[facing=south]')                 # bannière (côté extérieur)
# arche entre les colonnes, au-dessus du passage (y 6 à 9), clé de voûte sculptée
for x in range(C - 3, C + 4):
    for z in (41, 42):
        put(x, 9, z, 'minecraft:stone_bricks')
        if abs(x - C) >= 2:
            put(x, 8, z, stairs('stone_brick_stairs', 'west' if x > C else 'east', 'top') if abs(x - C) == 2
                else 'minecraft:stone_bricks')
    put(x, 10, 42, slab('stone_brick_slab') if abs(x - C) != 0 else 'minecraft:chiseled_stone_bricks')
put(C, 9, 42, 'minecraft:chiseled_stone_bricks')
for x in range(C - 3, C + 4):                  # mur plein entre le haut du passage (y 5) et l'arche
    for y in (6, 7):
        for z in (41, 42):
            if abs(x - C) >= 3 or y == 7:
                put(x, y, z, brick())
# lanternes de part et d'autre du couloir principal, tonneaux et caisse
for side in (-1, 1):
    put(C + side * 3, 1, 37, 'minecraft:chiseled_stone_bricks')
    put(C + side * 3, 2, 37, 'minecraft:lantern[hanging=false,waterlogged=false]')
put(C + 4, 1, 39, 'minecraft:barrel[facing=up,open=false]')
put(C + 4, 2, 39, 'minecraft:barrel[facing=north,open=false]')
put(C - 4, 1, 39, 'minecraft:barrel[facing=up,open=false]')
put(C - 4, 1, 40, 'minecraft:spruce_planks')
put(C - 4, 2, 40, 'minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
for x, z in ((C - 19, C - 2), (C - 19, C + 2), (C + 19, C - 2), (C + 19, C + 2), (C - 2, C - 19), (C + 2, C - 19)):
    if get(x, 5, z) and get(x, 5, z) != 'minecraft:air' and get(x, 6, z) == 'minecraft:air':
        put(x, 6, z, 'minecraft:lantern[hanging=false,waterlogged=false]')   # entrées secondaires : lanternes de part et d'autre

# ---------------------------------------------------------------- vérifications : arène dégagée, centre libre
for x, z in DISC:
    r, _ = polar(x, z)
    if r <= R_ARENA:
        for y in range(2, SY):
            assert get(x, y, z) == 'minecraft:air', ('obstacle dans l\'arène', x, y, z, get(x, y, z))
        if r <= 7.5:
            assert get(x, 1, z) == 'minecraft:air', ('obstacle près du centre', x, z, get(x, 1, z))
for y in range(1, 5):
    assert get(C, y, C) == 'minecraft:air'

# ---------------------------------------------------------------- Gol D. Roger au centre exact
entities = List[Compound]([Compound({
    'pos': List[Double]([Double(C + 0.5), Double(1.0), Double(C + 0.5)]),
    'blockPos': List[Int]([Int(C), Int(1), Int(C)]),
    'nbt': Compound({'id': String('bloxpiece:gol_d_roger'), 'PersistenceRequired': Byte(1),
                     'Rotation': List[Float]([Float(0.0), Float(0.0)]),          # regarde le sud : l'entrée principale
                     # marque de boss d'arène (pièce jointe Fabric ModAttachments.ARENA_BOSS, lue par GolDRoger.isArenaBoss)
                     'fabric:attachments': Compound({'bloxpiece:arena_boss': Byte(1)})})})])

# ---------------------------------------------------------------- écriture
palette, index, blocks = [], {}, []
for (x, y, z), block in sorted(G.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    if block not in index:
        index[block] = len(palette)
        nm, _, props = block.partition('[')
        entry = {'Name': String(nm)}
        if props:
            entry['Properties'] = Compound({k: String(v) for k, v in (p.split('=') for p in props[:-1].split(','))})
        palette.append(Compound(entry))
    blocks.append(Compound({'pos': List[Int]([Int(x), Int(y), Int(z)]), 'state': Int(index[block])}))
nbtlib.File({'DataVersion': Int(3953), 'size': List[Int]([Int(SX), Int(SY), Int(SZ)]), 'palette': List[Compound](palette),
             'blocks': List[Compound](blocks), 'entities': entities}, gzipped=True).save(OUT)
solid = sum(1 for b in G.values() if b != 'minecraft:air')
print(f'{solid} blocs (+ air), {len(palette)} états, {len(entities)} entité(s)')

# ---------------------------------------------------------------- aperçus
if len(sys.argv) > 1:
    from PIL import Image, ImageDraw
    COL = {'stone_bricks': (132, 132, 132), 'mossy_stone_bricks': (112, 128, 96), 'cracked_stone_bricks': (118, 118, 116),
           'chiseled_stone_bricks': (150, 150, 150), 'smooth_stone': (164, 164, 164), 'polished_andesite': (136, 140, 140),
           'andesite': (128, 128, 128), 'stone': (125, 125, 125), 'cobblestone': (110, 110, 110),
           'mossy_cobblestone': (100, 116, 92), 'gravel': (130, 124, 122), 'coarse_dirt': (120, 86, 58),
           'grass_block': (96, 160, 64), 'moss_block': (90, 120, 50), 'moss_carpet': (90, 120, 50), 'short_grass': (90, 150, 60),
           'fern': (80, 130, 60), 'stone_brick_stairs': (140, 140, 140), 'mossy_stone_brick_stairs': (118, 134, 100),
           'stone_brick_slab': (140, 140, 140), 'smooth_stone_slab': (168, 168, 168), 'cobblestone_slab': (112, 112, 112),
           'campfire': (240, 140, 40), 'lantern': (255, 210, 90), 'red_wall_banner': (190, 30, 30), 'barrel': (140, 100, 56),
           'spruce_planks': (110, 80, 50), 'spruce_trapdoor': (110, 80, 50), 'skeleton_skull': (230, 230, 220)}

    def color(b):
        return COL.get(b.split('[')[0].split(':')[1], (255, 0, 255))

    def iso(out, s, turn):
        pts = [(p, b) for p, b in G.items() if b != 'minecraft:air']
        def tr(x, z):
            for _ in range(turn):
                x, z = SX - 1 - z, x
            return x, z
        pts = [((tr(x, z)[0], y, tr(x, z)[1]), b) for (x, y, z), b in pts]
        occ = {p for p, _ in pts}
        W, H = int((SX + SZ) * s * 0.9 + 60), int((SX + SZ) * s * 0.45 + SY * s + 80)
        im = Image.new('RGB', (W, H), (150, 195, 235))
        d = ImageDraw.Draw(im)

        def P(x, y, z):
            return (W / 2 + (x - z) * s * 0.9, 40 + (x + z) * s * 0.45 - y * s + SY * s)
        pts.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))
        for (x, y, z), b in pts:
            c = color(b)
            dk = lambda f: tuple(int(v * f) for v in c)
            if (x, y + 1, z) not in occ:
                d.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=c)
            if (x, y, z + 1) not in occ:
                d.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=dk(0.8))
            if (x + 1, y, z) not in occ:
                d.polygon([P(x + 1, y, z), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x + 1, y + 1, z)], fill=dk(0.62))
        x, z = tr(C, C)
        a, b2 = P(x + 0.5, 3.2, z + 0.5), P(x + 0.5, 1, z + 0.5)
        d.rectangle([a[0] - 4, a[1], a[0] + 4, b2[1]], fill=(200, 30, 30), outline=(0, 0, 0))   # Roger
        im.save(out)

    def top(out, s):
        im = Image.new('RGB', (SX * s, SZ * s), (150, 195, 235))
        d = ImageDraw.Draw(im)
        for x in range(SX):
            for z in range(SZ):
                ys = [y for y in range(SY) if G.get((x, y, z), 'minecraft:air') != 'minecraft:air']
                if ys:
                    y = max(ys)
                    c = color(G[(x, y, z)])
                    f = 0.75 + 0.05 * y
                    d.rectangle([x * s, z * s, x * s + s - 1, z * s + s - 1], fill=tuple(min(255, int(v * f)) for v in c))
        d.ellipse([C * s + 1, C * s + 1, C * s + s - 2, C * s + s - 2], fill=(200, 30, 30))
        im.save(out)

    iso(os.path.join(sys.argv[1], 'arena_iso_sud.png'), 12, 0)
    iso(os.path.join(sys.argv[1], 'arena_iso_nord.png'), 12, 2)
    top(os.path.join(sys.argv[1], 'arena_dessus.png'), 12)
