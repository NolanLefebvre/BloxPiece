package nolan.bloxpiece.haki;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import nolan.bloxpiece.network.HakiDodgePayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModAttachments;

/**
 * Haki de l'Observation (Kenbunshoku). Appui : activé (ou coupé) ; dure {@link #DURATION} ticks. Actif, il esquive TOUTES les
 * attaques dirigées contre le joueur : coups de mobs ou de joueurs, projectiles (flèches, balles...), attaques de fruits du
 * démon, explosions déclenchées par un ennemi. Jamais les dégâts de l'environnement : chute, noyade, lave, feu ou brûlure,
 * poison, faim, gel, vide.
 * Chaque esquive : animation d'esquive (le buste et la tête se penchent d'un côté, PlayerModelMixin), un souffle et un son
 * d'anticipation.
 */
public final class ObservationHaki implements Haki {

    private static final int DURATION = 20 * 20;
    private static final int COOLDOWN_MANUAL = 20 * 6;
    private static final int COOLDOWN_EXPIRED = 20 * 20;

    private final Map<UUID, Long> until = new HashMap<>();
    /** Dernier type d'esquive joué par chaque joueur. */
    private final Map<UUID, Integer> lastVariant = new HashMap<>();

    @Override
    public HakiType type() {
        return HakiType.OBSERVATION;
    }

    @Override
    public void press(ServerPlayer player) {
        if (isActive(player)) {
            stop(player);
            HakiManager.startCooldown(player, type(), COOLDOWN_MANUAL);
            return;
        }
        player.setAttached(ModAttachments.HAKI_OBSERVATION, true);
        this.until.put(player.getUUID(), player.level().getGameTime() + DURATION);
        player.level().playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
        player.level().playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
        if (player.level() instanceof ServerLevel level) {
            Vec3 eye = player.getEyePosition();
            level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 14, 0.5, 0.3, 0.5, 0.02);
        }
    }

    @Override
    public void tick(ServerPlayer player) {
        Long end = this.until.get(player.getUUID());
        if (end != null && player.level().getGameTime() >= end) {
            stop(player);
            HakiManager.startCooldown(player, type(), COOLDOWN_EXPIRED);
        }
    }

    @Override
    public boolean isActive(ServerPlayer player) {
        return Boolean.TRUE.equals(player.getAttached(ModAttachments.HAKI_OBSERVATION));
    }

    @Override
    public void stop(ServerPlayer player) {
        player.removeAttached(ModAttachments.HAKI_OBSERVATION);
        this.until.remove(player.getUUID());
    }

    /** Une attaque (venant d'un ennemi), pas un dégât de l'environnement ? */
    static boolean isAttack(DamageSource source) {
        // chute, noyade, gel, lave, faim, poison... n'ont pas d'auteur : seul ce qui vient d'une entité est esquivé
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return source.getEntity() != null || source.getDirectEntity() != null;
    }

    // Types d'esquive (même numérotation que HakiClientState / PlayerModelMixin)
    public static final int DODGE_SIDE = 0;      // pas de côté / grande esquive latérale
    public static final int DODGE_LEAN_BACK = 1; // recul du buste
    public static final int DODGE_TWIST = 2;     // pivot court des épaules
    public static final int DODGE_HEAD = 3;      // la tête seule s'écarte
    public static final int DODGE_DUCK = 4;      // plongeon sous le coup
    public static final int DODGE_MATRIX = 5;    // recul profond + torsion

    /**
     * Esquive selon le TYPE de coup (deux variantes de la MÊME logique par catégorie, jouées à tour de rôle pour ne pas se
     * répéter) :
     * <ul>
     *   <li>coup d'épée / corps à corps (l'attaquant frappe lui-même, à portée de bras) : le joueur se penche EN ARRIÈRE pour
     *       laisser passer la lame (recul du buste, ou recul profond façon « matrice ») ;</li>
     *   <li>balle / projectile / attaque lancée de loin : esquive SUR LE CÔTÉ (grand écart latéral du buste, ou la tête seule qui
     *       glisse de côté) ;</li>
     *   <li>explosion : plongeon sous l'onde, ou grand écart latéral.</li>
     * </ul>
     */
    private static final int[][] DODGES_BY_CATEGORY = {
            {DODGE_LEAN_BACK, DODGE_MATRIX},
            {DODGE_SIDE, DODGE_HEAD},
            {DODGE_DUCK, DODGE_SIDE}
    };
    private static final int MELEE = 0;
    private static final int PROJECTILE = 1;
    private static final int EXPLOSION = 2;
    /** Au-delà de cette distance, un coup porté directement par l'attaquant (pouvoir lancé de loin) compte comme un tir. */
    private static final double MELEE_REACH = 4.5;

    /**
     * Catégorie du coup, de la plus sûre à la moins sûre : explosion (tag), projectile (entité directe = projectile, ou différente
     * de l'attaquant), attaque lointaine (attaquant hors de portée de bras), sinon corps à corps. Les dégâts n'entrent pas en
     * compte : un gros coup d'épée reste un coup d'épée.
     */
    private static int category(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return EXPLOSION;
        }
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (direct instanceof Projectile || (direct != null && attacker != null && direct != attacker)) {
            return PROJECTILE;
        }
        Entity from = direct != null ? direct : attacker;
        if (from == null || from.distanceToSqr(player) > MELEE_REACH * MELEE_REACH) {
            return PROJECTILE;
        }
        return MELEE;
    }

    /** true : le joueur s'écarte vers SA droite (le coup arrive par sa gauche). Hasard si l'origine est inconnue. */
    private static boolean dodgeRight(ServerPlayer player, DamageSource source) {
        Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (from == null || from == player) {
            return player.getRandom().nextBoolean();
        }
        double yaw = Math.toRadians(player.getYRot());
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double dot = (from.getX() - player.getX()) * rightX + (from.getZ() - player.getZ()) * rightZ;
        if (Math.abs(dot) < 0.15) {
            return player.getRandom().nextBoolean(); // pile en face : un côté au hasard
        }
        return dot < 0.0; // attaquant à gauche -> on part à droite
    }

    /** Appelé avant chaque dégât : true = l'attaque est esquivée (annulée). */
    boolean tryDodge(ServerPlayer player, DamageSource source, float amount) {
        if (!isActive(player) || !isAttack(source)) {
            return false;
        }
        // esquive dédiée au type de coup ; dans une catégorie, les deux esquives alternent
        int[] pool = DODGES_BY_CATEGORY[category(player, source)];
        Integer previous = this.lastVariant.get(player.getUUID());
        int variant = pool[0];
        if (previous != null && previous == pool[0]) {
            variant = pool[1];
        }
        // (amount n'est plus utilisé pour choisir : gardé dans la signature pour d'éventuels réglages)
        this.lastVariant.put(player.getUUID(), variant);
        boolean right = dodgeRight(player, source);
        // animation : paquet immédiat au joueur et à tous ceux qui le voient (64 blocs)
        if (player.level() instanceof ServerLevel serverLevel) {
            HakiDodgePayload payload = new HakiDodgePayload(player.getId(), variant, right);
            for (ServerPlayer viewer : serverLevel.players()) {
                if (viewer == player || viewer.distanceToSqr(player) < 64.0 * 64.0) {
                    ServerPlayNetworking.send(viewer, payload);
                }
            }
        }
        // plus de particules ici : la traînée rouge des yeux est dessinée par chaque client (HakiEyeTrail)
        player.level().playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7F, 1.9F);
        return true;
    }
}
