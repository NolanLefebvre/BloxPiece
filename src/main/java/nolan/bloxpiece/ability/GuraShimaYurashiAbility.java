package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.QuakeFx;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Quatrième attaque (ultime) du Gura Gura no Mi : Shima Yurashi (Secousse insulaire), à charger comme Ice Age et Dai Enkai.
 * <ul>
 *   <li>Charge (touche maintenue) : bras levés, une boule sismique 3D propre grossit dans chaque main (aucune fissure autour). Appui court : départ après {@link #MIN_CHARGE_TICKS}
 *       ticks ; charge pleine en {@link #MAX_CHARGE_TICKS} ticks (départ automatique).</li>
 *   <li>Fin de charge : les bras s'écartent d'un coup sec, comme s'ils frappaient l'air avec une force colossale (accélération
 *       brutale, {@link #SPREAD_TICKS} ticks, pose 600 à 700), les boules toujours dans les mains. En T-pose : craquement
 *       sismique et tremblement d'écran.</li>
 *   <li>Au moment de la T-pose : deux immenses fissures d'air verticales se déchirent de chaque côté. La secousse soulève deux
 *       tsunamis colossaux (modèles 3D d'eau et d'écume) qui surgissent de chaque côté et foncent l'un vers l'autre en ravageant
 *       le terrain (tranchée, blocs arrachés) et en emportant les ennemis ; ils se percutent devant le joueur : onde de choc
 *       frontale dévastatrice et gerbe d'eau géante (sans fissure ni cratère au sol), ennemis projetés au loin. Le
 *       point de collision est celui visé par le joueur, jusqu'à 3 fois plus loin qu'avant.</li>
 *   <li>Taille selon la charge : les vagues partent à {@link #MIN_RADIUS} à {@link #MAX_RADIUS} blocs du point de collision ;
 *       dégâts de {@link #MIN_DAMAGE} à {@link #MAX_DAMAGE} (plus Lenteur et Nausée), destruction seulement si mobGriefing est actif.</li>
 * </ul>
 * La recharge démarre au relâchement (cooldownOnRelease) ; une charge annulée (eau, mort, perte du fruit) ne la consomme pas.
 */
public class GuraShimaYurashiAbility implements Ability {

    private static final int MIN_CHARGE_TICKS = 14;
    private static final int MAX_CHARGE_TICKS = 60;
    /** Les bras s'écartent jusqu'à la T-pose (ticks), puis la tiennent un instant avant l'explosion. */
    private static final int SPREAD_TICKS = 4;  // mouvement sec et brutal : les bras frappent l'air
    private static final int HOLD_TICKS = 5;    // T-pose tenue (impact, tremblement) avant l'explosion
    private static final double MIN_RADIUS = 12.0;
    private static final double MAX_RADIUS = 28.0;
    private static final float MIN_DAMAGE = 34.0F;
    private static final float MAX_DAMAGE = 70.0F;
    private static final double PUSH = 4.5;
    private static final double LIFT = 1.2;
    /** Puissance du cratère central (4 = TNT), de l'appui court à la pleine charge. */
    private static final float MIN_CRATER = 6.0F;
    private static final float MAX_CRATER = 11.0F;

    private static final class Charge {
        final ServerPlayer player;
        final Ability ability;
        final QuakeFx left;
        final QuakeFx right;
        final List<QuakeFx> cracks = new ArrayList<>();
        int age;
        boolean released;
        /** -1 = en charge ; sinon ticks passés depuis le début de l'écartement des bras. */
        int spread = -1;
        double power;

        Charge(ServerPlayer player, Ability ability, QuakeFx left, QuakeFx right) {
            this.player = player;
            this.ability = ability;
            this.left = left;
            this.right = right;
        }

        /** 0 = appui court, 1 = pleine charge. */
        double over() {
            return Math.max(0.0, Math.min(1.0, (double) (this.age - MIN_CHARGE_TICKS) / (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS)));
        }

        void discardAll() {
            this.left.discard();
            this.right.discard();
            this.cracks.forEach(fx -> fx.discard());
        }
    }

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    @Override
    public String id() {
        return "gura_shima_yurashi";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60;
    }

    @Override
    public int requiredLevel() {
        return 8;
    }

    @Override
    public boolean cooldownOnRelease() {
        return true;
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !CHARGES.containsKey(player.getUUID());
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        // Une bulle sur chaque main, placée exactement sur les poings par le client (bras levés : pose 2)
        QuakeFx left = QuakeFx.spawn(level, QuakeFx.ORB, hand(player, -1), player.getYRot(), 0.0F, 0.3F, 20).anchorTo(player, -1);
        QuakeFx right = QuakeFx.spawn(level, QuakeFx.ORB, hand(player, 1), player.getYRot(), 0.0F, 0.3F, 20).anchorTo(player, 1);
        CHARGES.put(player.getUUID(), new Charge(player, this, left, right));
        player.setAttached(ModAttachments.BUDDHA_POSE, 2); // bras levés et écartés (pose partagée avec le Bouddha)
        player.setAttached(ModAttachments.HIDE_HAND_ITEM, true); // l'objet en main ne traverse pas les boules
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0F, 0.6F);
    }

    @Override
    public void release(ServerPlayer player) {
        Charge c = CHARGES.get(player.getUUID());
        if (c != null) {
            c.released = true;
        }
    }

    /** Main levée du joueur (côté -1 = gauche, 1 = droite). */
    private static Vec3 hand(ServerPlayer player, int side) {
        double size = player.getScale();
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        return player.position().add(0.0, 2.05 * size, 0.0).add(right.scale(0.85 * size * side));
    }

    public static void tick(MinecraftServer server) {
        tickWaves();
        Iterator<Charge> it = CHARGES.values().iterator();
        while (it.hasNext()) {
            Charge c = it.next();
            ServerPlayer player = c.player;
            if (player.isRemoved() || !player.isAlive() || player.isInWater()
                    || Fruits.get(player.getAttached(ModAttachments.FRUIT)) != Fruits.GURA) {
                c.discardAll();
                player.removeAttached(ModAttachments.BUDDHA_POSE);
                player.removeAttached(ModAttachments.HIDE_HAND_ITEM);
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            if (c.spread >= 0) {
                // Les bras s'écartent jusqu'à la T-pose, boules dans les mains, puis l'explosion part
                c.spread++;
                double t = Math.min(1.0, (double) c.spread / SPREAD_TICKS);
                int percent = (int) Math.round(100 * t * t * t); // accélération brutale : lent au départ, claque à la fin
                player.setAttached(ModAttachments.BUDDHA_POSE, 600 + percent);
                if (c.spread == SPREAD_TICKS) {
                    clap(level, player); // les bras atteignent la T-pose : l'air se fissure
                }
                for (QuakeFx orb : new QuakeFx[] {c.left, c.right}) {
                    orb.setLife(orb.tickCount + 10);
                }
                if (c.spread >= SPREAD_TICKS + HOLD_TICKS) {
                    it.remove();
                    c.discardAll();
                    player.removeAttached(ModAttachments.BUDDHA_POSE);
                    player.removeAttached(ModAttachments.HIDE_HAND_ITEM);
                    startWaves(level, player, c.power); // les deux vagues sismiques surgissent de chaque côté
                }
                continue;
            }
            c.age++;
            double over = c.over();
            double progress = Math.min(1.0, (double) c.age / MIN_CHARGE_TICKS);
            float orbSize = (float) (0.28 + 0.08 * progress + 0.14 * over); // grossissent peu : restent proportionnées aux mains
            for (QuakeFx orb : new QuakeFx[] {c.left, c.right}) {
                orb.setSize(orbSize);
                orb.setLife(orb.tickCount + 10); // tenue tant que la charge dure
            }
            c.left.follow(hand(player, -1));
            c.right.follow(hand(player, 1));

            if (c.age % 10 == 0) {
                BuddhaHits.shake(level, player.position(), 24.0 * (1.0 + over), 0.4F + 1.2F * (float) over, 10);
            }

            if (c.age >= MIN_CHARGE_TICKS && (c.released || c.age >= MAX_CHARGE_TICKS)) {
                // Fin de charge : la puissance est figée, les bras commencent à s'écarter vers la T-pose
                c.spread = 0;
                c.power = over;
                AbilityManager.startCooldown(player, c.ability); // la recharge démarre au relâchement
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0F, 0.5F);
            }
        }
    }

    /** Les bras arrivent en T-pose : craquement sismique, fissures d'air sur les deux mains, tremblement d'écran. */
    private static void clap(ServerLevel level, ServerPlayer player) {
        double size = player.getScale();
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        for (int side = -1; side <= 1; side += 2) {
            // Main tendue à l'horizontale : à hauteur d'épaule, un bras de long sur le côté
            Vec3 hand = player.position().add(0.0, 1.4 * size, 0.0).add(right.scale(1.05 * size * side));
            float yawOut = player.yBodyRot + (side > 0 ? 90.0F : -90.0F); // la fissure fait face vers l'extérieur
            // Immense fissure d'air verticale qui se déchire sur le côté (face à l'avant du joueur)
            QuakeFx.spawn(level, QuakeFx.TEAR, player.position().add(right.scale(3.2 * size * side)).add(0.0, 3.0 * size, 0.0),
                    player.getYRot(), 0.0F, (float) (2.6 * size), WAVE_TICKS + 14);
            level.sendParticles(ParticleTypes.EXPLOSION, hand.x, hand.y, hand.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, 10, 0.2, 0.2, 0.2, 0.15);
        }
        Vec3 p = player.position();
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5F, 0.35F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.6F, 0.4F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.45F);
        BuddhaHits.shake(level, p, 30.0, 1.6F, 6);
    }

    // ------------------------------------------------------------------ les deux vagues sismiques

    /** Durée de la course des vagues jusqu'à leur collision (ticks). */
    private static final int WAVE_TICKS = 32; // tsunamis lents, lourds et menaçants

    /** Une attaque en cours : deux vagues qui foncent l'une vers l'autre jusqu'au point de collision. */
    private static final class Waves {
        final ServerPlayer player;
        final ServerLevel level;
        final double over;
        final Vec3 center;    // point de collision, devant le joueur
        final Vec3 forward;   // axe du regard (horizontal) : largeur des vagues
        final Vec3 right;     // axe de course des vagues
        final double distance;
        final double width;
        final QuakeFx[] fx = new QuakeFx[2];
        final Vec3[] last = new Vec3[2];
        final java.util.Set<Integer> hit = new java.util.HashSet<>();
        int age;

        Waves(ServerPlayer player, double over) {
            this.player = player;
            this.level = (ServerLevel) player.level();
            this.over = over;
            double radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * over;
            double yaw = Math.toRadians(player.getYRot());
            this.forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            this.right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
            this.distance = radius * 1.2;              // chaque vague part à 1,2 x "radius" blocs du point de collision
            this.width = radius * 1.1;                 // largeur du front de chaque vague (colossale)
            // Point de collision : là où le joueur vise, jusqu'à 3 fois plus loin qu'avant ; rien de visé = au bout de la portée
            double range = radius * 0.75 * 3.0;
            Vec3 aimed = AimHelper.findTarget(player, range);
            Vec3 ahead = player.position().add(this.forward.scale(range));
            Vec3 target = aimed != null ? aimed : ahead;
            // Jamais dans le ciel : on redescend jusqu'au sol (ou à la surface de l'eau) sous le point visé
            this.center = groundBelow(this.level, target, player.getY());
        }

        /** Hauteur du tsunami (blocs) : le modèle fait ~27 px de haut pour 34 de large. */
        double waveHeight() {
            return this.width * 27.0 / 34.0;
        }

        /** Position de la vague {@code i} (0 = gauche, 1 = droite) au tick {@code t}, la base toujours posée sur le sol. */
        Vec3 position(int i, double t) {
            double side = i == 0 ? -1.0 : 1.0;
            Vec3 flat = this.center.add(this.right.scale(side * this.distance * (1.0 - t)));
            return groundBelow(this.level, flat, this.center.y);
        }
    }

    private static final List<Waves> WAVES = new ArrayList<>();

    /**
     * Le sol sous la colonne (x, z) de {@code at} : rayon vers le bas depuis un peu au-dessus (blocs solides et surface des
     * liquides). D'abord la surface la plus proche de la hauteur de référence (relief, creux, collines), sinon un long rayon vers le
     * bas (point visé dans le ciel ou sur une entité volante) ; en dernier recours, la hauteur de référence (le vide).
     */
    private static Vec3 groundBelow(ServerLevel level, Vec3 at, double referenceY) {
        int x = net.minecraft.util.Mth.floor(at.x), z = net.minecraft.util.Mth.floor(at.z);
        int gy = IceTerrain.findSurface(level, x, net.minecraft.util.Mth.floor(Math.min(at.y, referenceY + 4.0)) - 1, z, 12);
        if (gy != IceTerrain.NO_SURFACE) {
            return new Vec3(at.x, gy + 1.0, at.z);
        }
        Vec3 from = new Vec3(at.x, Math.max(at.y, referenceY) + 2.0, at.z);
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, from.add(0.0, -96.0, 0.0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return new Vec3(at.x, hit.getLocation().y, at.z);
        }
        return new Vec3(at.x, referenceY, at.z);
    }

    /** Les deux vagues surgissent de chaque côté (modèles 3D de blocs soulevés et d'énergie sismique). */
    private static void startWaves(ServerLevel level, ServerPlayer player, double over) {
        Waves w = new Waves(player, over);
        float size = (float) (w.width / 2.0); // le tsunami fait 2 blocs de large et ~1,8 de haut à la taille 1
        for (int i = 0; i < 2; i++) {
            Vec3 start = w.position(i, 0.0);
            Vec3 dir = w.right.scale(i == 0 ? 1.0 : -1.0); // vers le centre
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            w.fx[i] = QuakeFx.spawn(level, QuakeFx.WAVE, start, yaw, 0.0F, size, WAVE_TICKS + 8);
            w.last[i] = start;
            level.sendParticles(ParticleTypes.SPLASH, start.x, start.y + 1.0, start.z, 120, size * 0.5, 2.0, size * 0.5, 0.5);
            level.playSound(null, start.x, start.y, start.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.4F);
            level.playSound(null, start.x, start.y, start.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 4.0F, 0.5F);
        }
        WAVES.add(w);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 6, 0, false, false)); // ne se tue pas dans la tranchée
    }

    /** Fait avancer les vagues ; à la fin de leur course, elles se percutent. */
    private static void tickWaves() {
        Iterator<Waves> it = WAVES.iterator();
        while (it.hasNext()) {
            Waves w = it.next();
            if (w.player.isRemoved()) {
                for (QuakeFx fx : w.fx) {
                    fx.discard();
                }
                it.remove();
                continue;
            }
            w.age++;
            double t = Math.min(1.0, (double) w.age / WAVE_TICKS);
            t = t * t * (3.0 - 2.0 * t) * 0.15 + t * 0.85; // départ franc, légère accélération au centre
            for (int i = 0; i < 2; i++) {
                Vec3 pos = w.position(i, t);
                w.fx[i].follow(pos);
                Vec3 dir = w.right.scale(i == 0 ? 1.0 : -1.0);
                sweep(w, w.last[i], pos, dir);
                // Écume et embruns : au pied, en suspension autour de la crête, et pluie d'eau sous le rouleau
                double half = w.width / 2;
                double crest = pos.y + w.waveHeight() * 0.9;
                Vec3 lip = pos.add(dir.scale(w.waveHeight() * 0.35)); // le rouleau surplombe l'avant
                double sx = Math.abs(w.forward.x) * half, sz = Math.abs(w.forward.z) * half;
                w.level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.5, pos.z, 50, sx + 1.0, 0.5, sz + 1.0, 0.3);
                w.level.sendParticles(ParticleTypes.SPLASH, lip.x, crest, lip.z, 70, sx, 1.5, sz, 0.4);
                w.level.sendParticles(ParticleTypes.CLOUD, lip.x, crest + 1.0, lip.z, 18, sx, 1.2, sz, 0.03);
                w.level.sendParticles(ParticleTypes.FALLING_WATER, lip.x, crest - 2.0, lip.z, 40, sx, 2.0, sz, 0.0);
                w.last[i] = pos;
            }
            if (w.age % 4 == 0) {
                BuddhaHits.shake(w.level, w.center, w.distance * 2.5, 1.5F + (float) w.over, 6);
                w.level.playSound(null, w.center.x, w.center.y, w.center.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 3.0F, 0.4F);
            }
            if (w.age >= WAVE_TICKS) {
                collide(w);
                it.remove();
            }
        }
    }

    /**
     * La vague passe de {@code from} à {@code to} : elle creuse une tranchée (blocs arrachés et projetés en l'air, sol ouvert sur
     * 1 à 2 blocs) sur toute sa largeur, et emporte les ennemis touchés (une fois chacun) dans son sens de course.
     */
    private static void sweep(Waves w, Vec3 from, Vec3 to, Vec3 dir) {
        ServerLevel level = w.level;
        float maxDamage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * (float) w.over;
        // Ennemis emportés : tout le volume balayé par la vague depuis le tick précédent, sur toute sa largeur et toute sa hauteur
        // (et même au-dessus de la crête) : impossible de l'esquiver en sautant ou en volant
        double top = w.waveHeight() + 6.0;
        double reach = to.subtract(from).horizontalDistance() + 3.0;
        Vec3 mid = from.add(to).scale(0.5);
        AABB box = new AABB(mid, mid).inflate(reach + w.width, 0.0, reach + w.width).expandTowards(0.0, top, 0.0).move(0.0, -4.0, 0.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> BuddhaHits.canHit(e, w.player))) {
            Vec3 rel = target.position().subtract(mid);
            double across = Math.abs(rel.dot(w.forward));
            double along = Math.abs(rel.dot(dir));
            double up = target.getY() - Math.min(from.y, to.y);
            if (across > w.width / 2 + 1.0 || along > reach / 2 + 1.5 || up < -4.0 || up > top || !w.hit.add(target.getId())) {
                continue;
            }
            BuddhaHits.strike(level, w.player, target, maxDamage * 0.8F, dir.scale(PUSH * 1.1).add(0.0, LIFT * 1.5, 0.0));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), w.player);
        }

        // Tranchée sur toute la largeur du front
        if (!GuraTerrain.griefing(level)) {
            return;
        }
        RandomSource random = level.getRandom();
        double length = to.subtract(from).horizontalDistance();
        for (double d = 0.0; d <= length; d += 1.0) {
            Vec3 line = from.add(dir.scale(d));
            double along = line.subtract(w.center).dot(dir); // position le long de la course (pour un bruit continu)
            for (double j = -w.width / 2; j <= w.width / 2; j += 1.0) {
                double edge = Math.abs(j) / (w.width / 2);       // 0 au milieu, 1 au bord
                // Bord déchiqueté : la limite de la tranchée ondule et s'effrite au hasard (jamais une bande droite)
                double limit = 0.86 + 0.14 * Math.sin(along * 0.45 + j * 0.9) + 0.1 * Math.sin(along * 1.3 - j * 0.4);
                if (edge > limit || (edge > limit - 0.15 && random.nextFloat() < 0.5F)) {
                    continue;
                }
                // Profondeur irrégulière : creux et bosses, plus profond vers le milieu
                double noise = Math.sin(along * 0.8 + j * 1.7) + Math.sin(along * 0.31 - j * 0.6) + random.nextGaussian() * 0.6;
                int depth = Math.max(0, (int) Math.round((1.0 - edge) * 4.5 + noise * 1.1)); // jusqu'à ~6 blocs au milieu
                if (depth == 0) {
                    continue; // îlots de terrain intacts au milieu de la tranchée
                }
                Vec3 p = line.add(w.forward.scale(j + (random.nextDouble() - 0.5) * 0.8));
                Vec3 launch = random.nextFloat() < 0.55F
                        ? dir.scale(0.15 + random.nextDouble() * 0.45).add(w.forward.scale((random.nextDouble() - 0.5) * 0.6))
                                .add(0.0, 0.5 + random.nextDouble() * 1.0, 0.0)
                        : null;
                GuraTerrain.gouge(level, p, depth, launch);
            }
        }
    }

    /** Les deux tsunamis se percutent : choc d'eau massif, terrain pulvérisé (sans fissures au sol), tout est projeté au loin. */
    private static void collide(Waves w) {
        ServerLevel level = w.level;
        ServerPlayer player = w.player;
        Vec3 c = w.position(0, 1.0);
        float maxDamage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * (float) w.over;
        double radius = w.width * 1.2;            // zone du choc élargie
        maxDamage *= 1.6F;                         // le choc des deux tsunamis est dévastateur
        for (QuakeFx fx : w.fx) {
            fx.setLife(fx.tickCount + 4); // les vagues s'écrasent l'une contre l'autre
        }

        // Onde de choc frontale : sphère fissurée et grande fissure d'air face au joueur (aucune fissure au sol)
        QuakeFx.spawn(level, QuakeFx.BLAST, c.add(0.0, 1.5, 0.0), player.getYRot(), 0.0F, (float) radius, 20);
        QuakeFx.spawn(level, QuakeFx.PANEL, c.add(0.0, radius * 0.4, 0.0), player.getYRot(), 0.0F, (float) (radius * 0.5), 26);

        // Tout ce qui se trouve ENTRE les deux tsunamis (sur toute leur course, leur largeur et leur hauteur, et autour du point
        // de choc) est frappé et projeté au loin, vers l'avant (dans l'axe du choc) et vers le haut
        double top = w.waveHeight() + 8.0;
        AABB zone = new AABB(c, c).inflate(w.distance + w.width, 0.0, w.distance + w.width).expandTowards(0.0, top, 0.0).move(0.0, -5.0, 0.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> BuddhaHits.canHit(e, player))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(c);
            double lateral = Math.abs(to.dot(w.right));     // le long de la course des vagues
            double across = Math.abs(to.dot(w.forward));    // sur la largeur des vagues
            double dist = to.length();
            boolean between = lateral <= w.distance + 2.0 && across <= w.width / 2 + 2.0 && to.y >= -5.0 && to.y <= top;
            if (!between && dist > radius * 1.3) {
                continue;
            }
            double falloff = Math.min(1.0, dist / (w.distance + w.width));
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            Vec3 out = flat.lengthSqr() < 1.0E-4 ? w.forward : flat.normalize().add(w.forward).normalize();
            BuddhaHits.strike(level, player, target, maxDamage * (float) (1.0 - 0.4 * falloff),
                    out.scale(PUSH * 1.3 * (1.0 - 0.4 * falloff)).add(0.0, LIFT * 1.4, 0.0));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), player);
            target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0), player);
        }

        // Le choc pulvérise le terrain : cratères aux bords irréguliers (explosions de tailles et positions variées, sans fissures),
        // grande quantité de débris projetés
        RandomSource rnd = level.getRandom();
        GuraTerrain.blast(level, player, c, MIN_CRATER + (MAX_CRATER - MIN_CRATER) * (float) w.over);
        int craters = 6 + (int) (6 * w.over);
        for (int i = 0; i < craters; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = radius * (0.2 + rnd.nextDouble() * 0.6);
            GuraTerrain.blast(level, player, c.add(Math.cos(a) * r, rnd.nextDouble() * 2.0 - 1.0, Math.sin(a) * r), 3.5F + rnd.nextFloat() * 3.5F);
        }
        GuraTerrain.debris(level, c, radius * 1.1, 80 + (int) (100 * w.over), 1.3 + 0.6 * w.over);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 4, radius * 0.2, 1.0, radius * 0.2, 0.0);
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI * 2.0 / 96;
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a), c.y + 0.3, c.z + Math.sin(a),
                    0, Math.cos(a), 0.05, Math.sin(a), 0.6 + radius * 0.06);
        }
        // Gerbe d'eau géante au point de collision
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 2.0, c.z, 600, radius * 0.4, radius * 0.6, radius * 0.4, 1.0);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + radius * 0.6, c.z, 120, radius * 0.4, radius * 0.4, radius * 0.4, 0.15);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 5.0F, 0.3F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0F, 0.35F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4.0F, 0.35F);
        BuddhaHits.shake(level, c, radius * 4.0, 4.0F + 2.0F * (float) w.over, 28);
    }

    public static void clear() {
        for (Charge c : CHARGES.values()) {
            c.discardAll();
            c.player.removeAttached(ModAttachments.BUDDHA_POSE);
            c.player.removeAttached(ModAttachments.HIDE_HAND_ITEM);
        }
        CHARGES.clear();
        WAVES.forEach(w -> {
            for (QuakeFx fx : w.fx) {
                if (fx != null) {
                    fx.discard();
                }
            }
        });
        WAVES.clear();
    }
}
