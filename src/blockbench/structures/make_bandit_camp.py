# -*- coding: utf-8 -*-
"""
Camp de bandits (structure générée naturellement) : écrit data/bloxpiece/structure/bandit_camp.nbt et des aperçus.
Repère : x 0..30 (ouest -> est), z 0..26 (nord : fond du camp -> sud : entrée), y = 0 : couche du sol (remplace la surface du
terrain), y >= 1 : au-dessus. Le camp regarde vers le sud (comme l'image de référence vue de face).

D'après l'image de référence :
  - deux grandes tentes à toit en escalier (profil arrondi en marches), une ROUGE à gauche (laine rouge) et une BLANCHE à
    droite (laine blanche), à côtes en planches et escaliers de chêne, entrée encadrée de poteaux en rondins, sol gris ;
    dans la rouge : coffre, établi, table de forgeron ; dans la blanche : deux coffres, tonneau, lit ;
  - entre les deux tentes, un petit enclos avec deux chevaux (brun et blanc) ;
  - au centre, une grande place de terre battue (chemin) aux bords irréguliers, avec le feu de camp entouré de pierres et un
    chaudron d'eau posé dessus, deux bancs en rondins de part et d'autre (chêne à gauche, chêne écorcé à droite) ;
  - au coin avant gauche (à l'écart de l'entrée des tentes), un champ de blé mûr entouré d'une barrière de chêne, lanternes
    sur les poteaux d'angle ;
  - à droite, un enclos à cochons (barrière de chêne, piliers de pierre coiffés de lanternes) ;
  - à droite devant, une pile de caisses : tonneaux, coffres, trappes et escaliers de chêne ;
  - herbe haute et quelques fleurs autour.
Bandits : un Chef des bandits (bloxpiece:bandit_chief) et 5 Bandits à l'épée (bloxpiece:bandit), sur des cases libres de la
place, assez écartés. Coffres et tonneaux : tables de butin bloxpiece:chests/bandit_camp/* (remplies une seule fois, à
l'ouverture, par Minecraft).
Usage (depuis la racine du projet) : python3 src/blockbench/structures/make_bandit_camp.py [dossier_apercus]
"""
import os, random, sys
import nbtlib
from nbtlib import Compound, List, Int, Double, String, Byte, Float

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, 'src/main/resources/data/bloxpiece/structure/bandit_camp.nbt')
SX, SY, SZ = 31, 9, 27
rnd = random.Random(20251121)
G = {}            # (x, y, z) -> état de bloc
LOOT = {}         # (x, y, z) -> table de butin


def put(x, y, z, b):
    assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z, b)
    G[(x, y, z)] = b


def get(x, y, z):
    return G.get((x, y, z), 'minecraft:air')


def box(x0, y0, z0, x1, y1, z1, b):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, b)


def container(x, y, z, b, table):
    put(x, y, z, b)
    LOOT[(x, y, z)] = 'bloxpiece:chests/bandit_camp/' + table


# ---------------------------------------------------------------- 1) sol : herbe, place de terre battue aux bords irréguliers
for x in range(SX):
    for z in range(SZ):
        put(x, 0, z, 'minecraft:grass_block[snowy=false]')
        for y in range(1, SY):
            put(x, y, z, 'minecraft:air')   # dégage le terrain d'origine (herbes, buissons) sur toute l'emprise


def path(x, z):
    put(x, 0, z, 'minecraft:dirt_path')


for x in range(6, 26):
    for z in range(9, 23):
        edge = min(x - 6, 25 - x, z - 9, 22 - z)
        if edge >= 1 or rnd.random() < 0.55:
            path(x, z)
for x, z in ((12, 23), (13, 23), (14, 23), (15, 23), (16, 23), (13, 24), (14, 24), (15, 24), (14, 25), (15, 25), (15, 26),
             (8, 21), (9, 23), (10, 23), (10, 24), (20, 23), (21, 23), (21, 24), (22, 24), (26, 16), (27, 16)):
    path(x, z)                      # sentiers qui partent de la place
for x in range(8, 23):              # devant les tentes
    for z in (9, 10):
        path(x, z)


# ---------------------------------------------------------------- 2) les deux tentes à toit en marches
# profil (demi-largeur 3) : murs à |dx| = 3 sur 3 blocs, puis une marche par colonne jusqu'au faîte (y = 6)
PROFILE = {3: (1, 3), 2: (4, 4), 1: (5, 5), 0: (6, 6)}


def tent(cx, cloth, z0=1, z1=9):
    ribs = {z0, (z0 + z1) // 2, z1}
    for z in range(z0, z1 + 1):
        rib = z in ribs
        for dx in range(-3, 4):
            lo, hi = PROFILE[abs(dx)]
            for y in range(lo, hi + 1):
                x = cx + dx
                if rib and y == hi and dx != 0:
                    facing = 'east' if dx < 0 else 'west'   # marches qui montent vers le faîte
                    put(x, y, z, f'minecraft:oak_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]')
                elif rib:
                    put(x, y, z, 'minecraft:oak_planks')
                else:
                    put(x, y, z, cloth)
        # sol gris à l'intérieur
        for dx in range(-2, 3):
            put(cx + dx, 0, z, 'minecraft:gray_concrete')
    # le fond est fermé (toile pleine), l'entrée (au sud) est ouverte entre deux poteaux en rondins
    for dx in range(-2, 3):
        lo, hi = PROFILE[abs(dx)]
        for y in range(1, lo):
            put(cx + dx, y, z0, cloth)
        if abs(dx) <= 1:
            for y in range(1, 4):
                put(cx + dx, y, z1, 'minecraft:air')
    for dx in (-2, 2):
        for y in (1, 2, 3):
            put(cx + dx, y, z1, 'minecraft:oak_log[axis=y]')
    for dx in range(-1, 2):
        put(cx + dx, 4, z1, 'minecraft:oak_log[axis=x]')       # linteau
    put(cx, 5, z1, cloth)                                        # haut du pignon d'entrée
    put(cx, 3, z1 - 1, 'minecraft:lantern[hanging=true,waterlogged=false]')


RED, WHITE = 'minecraft:red_wool', 'minecraft:white_wool'
tent(7, RED)
tent(21, WHITE)
# tente rouge : coffre, établi, table de forgeron ; tente blanche : coffres, tonneau, lit
container(5, 1, 2, 'minecraft:chest[facing=south,type=single,waterlogged=false]', 'stash')
put(9, 1, 2, 'minecraft:crafting_table')
put(9, 1, 4, 'minecraft:smithing_table')
container(19, 1, 2, 'minecraft:chest[facing=south,type=single,waterlogged=false]', 'stash')
container(19, 1, 5, 'minecraft:chest[facing=east,type=single,waterlogged=false]', 'supplies')
container(23, 1, 2, 'minecraft:barrel[facing=up,open=false]', 'supplies')
put(23, 1, 5, 'minecraft:white_bed[facing=north,occupied=false,part=head]')
put(23, 1, 6, 'minecraft:white_bed[facing=north,occupied=false,part=foot]')

# ---------------------------------------------------------------- 3) enclos des chevaux entre les tentes
for x in range(11, 18):
    put(x, 1, 1, 'minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]')
    put(x, 1, 7, 'minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]')
put(14, 1, 7, 'minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]')
for z in range(1, 8):
    put(14, 0, z, 'minecraft:coarse_dirt')
put(13, 0, 3, 'minecraft:coarse_dirt'); put(15, 0, 5, 'minecraft:coarse_dirt')
put(16, 1, 2, 'minecraft:hay_block[axis=y]')
put(11, 2, 7, 'minecraft:lantern[hanging=false,waterlogged=false]')
put(17, 2, 7, 'minecraft:lantern[hanging=false,waterlogged=false]')

# ---------------------------------------------------------------- 4) le feu de camp, ses pierres, le chaudron, les bancs
FX, FZ = 15, 15
put(FX, 1, FZ, 'minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]')
put(FX, 2, FZ, 'minecraft:water_cauldron[level=3]')
for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
    put(FX + dx, 1, FZ + dz, 'minecraft:cobblestone_slab[type=bottom,waterlogged=false]')
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    put(FX + dx, 1, FZ + dz, 'minecraft:cobblestone' if (dx + dz) % 4 == 0 else 'minecraft:andesite')
put(FX - 1, 2, FZ - 1, 'minecraft:cobblestone_slab[type=bottom,waterlogged=false]')
for x in range(9, 12):
    put(x, 1, 17, 'minecraft:oak_log[axis=x]')               # banc gauche
for x in range(18, 21):
    put(x, 1, 16, 'minecraft:stripped_oak_log[axis=x]')      # banc droit

# ---------------------------------------------------------------- 5) champ de blé clôturé (coin avant gauche, à l'écart de l'entrée
# des tentes : la place devant la tente rouge reste libre)
F_X0, F_X1, F_Z0, F_Z1 = 0, 7, 17, 26
for x in range(F_X0 + 1, F_X1):
    for z in range(F_Z0 + 1, F_Z1):
        if (x, z) == (3, 21) or (x, z) == (4, 21):
            put(x, 0, z, 'minecraft:water')                  # rigole d'eau : le champ reste humide
            continue
        put(x, 0, z, 'minecraft:farmland[moisture=7]')
        put(x, 1, z, 'minecraft:wheat[age=7]')
put(3, 1, 21, 'minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
put(4, 1, 21, 'minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
for x in range(F_X0, F_X1 + 1):
    for z in (F_Z0, F_Z1):
        put(x, 1, z, 'minecraft:oak_fence')
for z in range(F_Z0, F_Z1 + 1):
    for x in (F_X0, F_X1):
        put(x, 1, z, 'minecraft:oak_fence')
put(F_X1, 1, 21, 'minecraft:oak_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
for x, z in ((F_X0, F_Z0), (F_X1, F_Z0), (F_X0, F_Z1), (F_X1, F_Z1)):
    put(x, 2, z, 'minecraft:lantern[hanging=false,waterlogged=false]')

# ---------------------------------------------------------------- 6) enclos à cochons (droite) : barrière, piliers de pierre
P_X0, P_X1, P_Z0, P_Z1 = 25, 30, 8, 15
for x in range(P_X0, P_X1 + 1):
    for z in (P_Z0, P_Z1):
        put(x, 1, z, 'minecraft:oak_fence')
for z in range(P_Z0, P_Z1 + 1):
    for x in (P_X0, P_X1):
        put(x, 1, z, 'minecraft:oak_fence')
for x in range(P_X0 + 1, P_X1):
    for z in range(P_Z0 + 1, P_Z1):
        put(x, 0, z, 'minecraft:coarse_dirt' if rnd.random() < 0.6 else 'minecraft:podzol[snowy=false]')
put(P_X0, 1, 12, 'minecraft:oak_fence_gate[facing=west,in_wall=false,open=false,powered=false]')
for x, z in ((P_X0, P_Z0), (P_X1, P_Z0), (P_X0, P_Z1), (P_X1, P_Z1)):
    put(x, 1, z, 'minecraft:cobblestone')
    put(x, 2, z, 'minecraft:stone_bricks')
    put(x, 3, z, 'minecraft:lantern[hanging=false,waterlogged=false]')
put(28, 1, 10, 'minecraft:composter[level=0]')

# ---------------------------------------------------------------- 7) pile de caisses (droite, devant)
container(22, 1, 18, 'minecraft:barrel[facing=up,open=false]', 'supplies')
container(23, 1, 18, 'minecraft:barrel[facing=up,open=false]', 'supplies')
container(22, 2, 18, 'minecraft:barrel[facing=up,open=false]', 'supplies')
put(23, 2, 18, 'minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
put(22, 3, 18, 'minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
container(24, 1, 19, 'minecraft:chest[facing=south,type=single,waterlogged=false]', 'loot')
put(24, 2, 19, 'minecraft:oak_slab[type=bottom,waterlogged=false]')
container(22, 1, 20, 'minecraft:chest[facing=west,type=single,waterlogged=false]', 'supplies')
put(21, 1, 18, 'minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
put(23, 1, 20, 'minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
put(25, 1, 18, 'minecraft:oak_fence')
put(25, 2, 18, 'minecraft:lantern[hanging=false,waterlogged=false]')

# lanternes sur poteaux devant les tentes (comme sur l'image)
for x in (3, 11, 17, 25):
    put(x, 1, 10, 'minecraft:oak_fence')
    put(x, 2, 10, 'minecraft:lantern[hanging=false,waterlogged=false]')

# ---------------------------------------------------------------- 8) herbe haute et fleurs sur l'herbe libre
for x in range(SX):
    for z in range(SZ):
        if get(x, 0, z).startswith('minecraft:grass_block') and get(x, 1, z) == 'minecraft:air':
            r = rnd.random()
            if r < 0.22:
                put(x, 1, z, 'minecraft:short_grass')
            elif r < 0.25:
                put(x, 1, z, rnd.choice(['minecraft:dandelion', 'minecraft:poppy', 'minecraft:oxeye_daisy']))

# ---------------------------------------------------------------- 9) bandits et animaux (sur des cases libres, assez écartées)
B, C = 'bloxpiece:bandit', 'bloxpiece:bandit_chief'
SPOTS = [(C, 15, 12), (B, 11, 15), (B, 19, 13), (B, 16, 20), (B, 9, 11), (B, 20, 21)]   # un chef et sa bande
ANIMALS = [('minecraft:pig', 27, 10), ('minecraft:pig', 28, 13), ('minecraft:pig', 26, 12),
           ('minecraft:horse', 12, 3, 3), ('minecraft:horse', 16, 5, 0)]   # variante : 3 brun, 0 blanc
entities = List[Compound]()


def free(x, z):
    return (not get(x, 0, z).startswith('minecraft:air') and get(x, 0, z) != 'minecraft:water'
            and get(x, 1, z) in ('minecraft:air', 'minecraft:short_grass') and get(x, 2, z) == 'minecraft:air'
            and get(x, 3, z) == 'minecraft:air')


for i, (eid, x, z) in enumerate(SPOTS):
    assert free(x, z), (eid, x, z)
    for e2, x2, z2 in SPOTS[:i]:
        assert abs(x - x2) + abs(z - z2) >= 4, 'bandits trop proches'
    put(x, 1, z, 'minecraft:air') if get(x, 1, z) == 'minecraft:short_grass' else None
    entities.append(Compound({'pos': List[Double]([Double(x + 0.5), Double(1.0), Double(z + 0.5)]),
                              'blockPos': List[Int]([Int(x), Int(1), Int(z)]),
                              'nbt': Compound({'id': String(eid), 'PersistenceRequired': Byte(1),
                                               'Rotation': List[Float]([Float(rnd.uniform(0, 360)), Float(0.0)])})}))
for a in ANIMALS:
    eid, x, z = a[:3]
    assert free(x, z), (eid, x, z)
    tag = {'id': String(eid), 'PersistenceRequired': Byte(1)}
    if eid == 'minecraft:horse':
        tag['Variant'] = Int(a[3])
        tag['Tame'] = Byte(1)
    entities.append(Compound({'pos': List[Double]([Double(x + 0.5), Double(1.0), Double(z + 0.5)]),
                              'blockPos': List[Int]([Int(x), Int(1), Int(z)]), 'nbt': Compound(tag)}))

# ---------------------------------------------------------------- liaisons des barrières
# La structure est posée telle quelle (Minecraft ne recalcule pas la forme des blocs posés par une structure) : on écrit donc
# les liaisons de chaque barrière, comme en jeu : vers une autre barrière, vers un portillon orienté dans le même alignement,
# ou vers la face pleine d'un bloc solide (piliers de pierre, toile et côtes des tentes, rondins, tonneaux...).
FULL = ('cobblestone', 'stone_bricks', 'andesite', 'red_wool', 'white_wool', 'oak_planks', 'oak_log', 'stripped_oak_log',
        'barrel', 'hay_block', 'crafting_table', 'smithing_table')
DIRS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


def base(b):
    return b.split('[')[0].split(':')[1]


def connects(b, side):
    n = base(b)
    if n == 'oak_fence':
        return True
    if n == 'oak_fence_gate':
        facing = b.split('facing=')[1].split(',')[0].rstrip(']')
        # un portillon orienté nord/sud s'étend d'est en ouest : il se lie aux barrières à l'est et à l'ouest (et inversement)
        return (facing in ('north', 'south')) == (side in ('east', 'west'))
    return n in FULL


for (x, y, z), bl in list(G.items()):
    if base(bl) != 'oak_fence':
        continue
    props = {d: str(connects(get(x + dx, y, z + dz), d)).lower() for d, (dx, dz) in DIRS.items()}
    G[(x, y, z)] = ('minecraft:oak_fence[east=%(east)s,north=%(north)s,south=%(south)s,waterlogged=false,west=%(west)s]'
                    % props)

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
    b = {'pos': List[Int]([Int(x), Int(y), Int(z)]), 'state': Int(index[block])}
    if (x, y, z) in LOOT:
        b['nbt'] = Compound({'id': String(block.split('[')[0]), 'LootTable': String(LOOT[(x, y, z)])})
    blocks.append(Compound(b))
nbtlib.File({'DataVersion': Int(3953), 'size': List[Int]([Int(SX), Int(SY), Int(SZ)]), 'palette': List[Compound](palette),
             'blocks': List[Compound](blocks), 'entities': entities}, gzipped=True).save(OUT)
solid = sum(1 for b in G.values() if b != 'minecraft:air')
print(f'{solid} blocs (+ air), {len(palette)} états, {len(LOOT)} coffres/tonneaux, {len(entities)} entités')

# ---------------------------------------------------------------- aperçus (vue isométrique, couleurs des blocs)
if len(sys.argv) > 1:
    from PIL import Image, ImageDraw
    COL = {'grass_block': (96, 160, 64), 'dirt_path': (214, 162, 86), 'red_wool': (196, 40, 40), 'white_wool': (236, 236, 236),
           'oak_planks': (176, 132, 74), 'oak_stairs': (186, 140, 80), 'oak_log': (120, 90, 50), 'stripped_oak_log': (190, 152, 96),
           'gray_concrete': (80, 84, 90), 'oak_fence': (170, 128, 72), 'oak_fence_gate': (170, 128, 72), 'lantern': (255, 210, 90),
           'campfire': (240, 140, 40), 'water_cauldron': (60, 90, 220), 'cobblestone_slab': (128, 128, 128), 'cobblestone': (120, 120, 120),
           'andesite': (140, 140, 136), 'farmland': (110, 72, 40), 'wheat': (220, 196, 80), 'water': (50, 90, 220),
           'oak_trapdoor': (160, 120, 66), 'coarse_dirt': (120, 86, 58), 'podzol': (100, 70, 40), 'stone_bricks': (150, 150, 150),
           'composter': (120, 90, 50), 'barrel': (140, 100, 56), 'chest': (200, 150, 50), 'oak_slab': (176, 132, 74),
           'crafting_table': (150, 100, 60), 'smithing_table': (60, 60, 70), 'white_bed': (230, 230, 230), 'hay_block': (210, 180, 60),
           'short_grass': (90, 150, 60), 'dandelion': (240, 220, 60), 'poppy': (220, 40, 40), 'oxeye_daisy': (240, 240, 230)}

    def color(b):
        return COL.get(b.split('[')[0].split(':')[1], (255, 0, 255))

    def iso(out, s, flip):
        pts = [(p, b) for p, b in G.items() if b != 'minecraft:air']
        occ = {p for p, _ in pts}
        W, H = int((SX + SZ) * s * 0.9 + 60), int((SX + SZ) * s * 0.45 + SY * s + 80)
        im = Image.new('RGB', (W, H), (150, 195, 235))
        d = ImageDraw.Draw(im)

        def P(x, y, z):
            if flip:
                x = SX - x
            return (W / 2 + (x - z) * s * 0.9 - (SX - SZ) * s * 0.45, 40 + (x + z) * s * 0.45 - y * s + SY * s * 0.6)
        pts.sort(key=lambda t: ((SX - t[0][0] if flip else t[0][0]) + t[0][2], t[0][1]))
        for (x, y, z), b in pts:
            c = color(b)
            dk = lambda f: tuple(int(v * f) for v in c)
            x0, x1 = (x, x + 1)
            if (x, y + 1, z) not in occ:
                d.polygon([P(x0, y + 1, z), P(x1, y + 1, z), P(x1, y + 1, z + 1), P(x0, y + 1, z + 1)], fill=c)
            if (x, y, z + 1) not in occ:
                d.polygon([P(x0, y, z + 1), P(x1, y, z + 1), P(x1, y + 1, z + 1), P(x0, y + 1, z + 1)], fill=dk(0.8))
            sx = x + 1 if not flip else x
            if ((x + 1 if not flip else x - 1), y, z) not in occ:
                d.polygon([P(sx, y, z), P(sx, y, z + 1), P(sx, y + 1, z + 1), P(sx, y + 1, z)], fill=dk(0.62))
        for e in entities:
            x, z = float(e['pos'][0]), float(e['pos'][2])
            eid = str(e['nbt']['id'])
            c = (160, 20, 20) if 'chief' in eid else (255, 230, 0) if 'bandit' in eid else (255, 170, 190) if 'pig' in eid else (130, 80, 40)
            hgt = 1.9 if 'bandit' in eid else 1.0
            a, b2 = P(x, 1 + hgt, z), P(x, 1, z)
            d.rectangle([a[0] - 3, a[1], a[0] + 3, b2[1]], fill=c, outline=(0, 0, 0))
        im.save(out)

    def front(out, s):
        """Vue de face légèrement plongeante (comme l'image de référence : on regarde vers le nord)."""
        pts = [(p, bl) for p, bl in G.items() if bl != 'minecraft:air']
        occ = {p for p, _ in pts}
        W, H = int(SX * s + 40), int(SZ * s * 0.55 + SY * s + 60)
        im = Image.new('RGB', (W, H), (150, 195, 235))
        d = ImageDraw.Draw(im)

        def P(x, y, z):
            return (20 + x * s, 30 + z * s * 0.55 - y * s + SY * s * 0.5)
        pts.sort(key=lambda t: (t[0][2], t[0][1]))
        for (x, y, z), bl in pts:
            c = color(bl)
            dk = lambda f: tuple(int(v * f) for v in c)
            if (x, y + 1, z) not in occ:
                d.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=c)
            if (x, y, z + 1) not in occ:
                d.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=dk(0.78))
        for e in entities:
            x, z = float(e['pos'][0]), float(e['pos'][2])
            eid = str(e['nbt']['id'])
            c = (160, 20, 20) if 'chief' in eid else (255, 230, 0) if 'bandit' in eid else (255, 170, 190) if 'pig' in eid else (130, 80, 40)
            hgt = 1.9 if 'bandit' in eid else 1.0
            a_, b_ = P(x - 0.3, 1 + hgt, z), P(x + 0.3, 1, z)
            d.rectangle([a_[0], a_[1], b_[0], b_[1]], fill=c, outline=(0, 0, 0))
        im.save(out)

    front(os.path.join(sys.argv[1], 'bandit_camp_face.png'), 22)
    iso(os.path.join(sys.argv[1], 'bandit_camp_iso.png'), 14, False)
    iso(os.path.join(sys.argv[1], 'bandit_camp_iso_dos.png'), 14, True)
