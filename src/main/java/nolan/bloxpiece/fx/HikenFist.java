package nolan.bloxpiece.fx;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Le projectile de Hiken : le poing de feu LUI-MÊME (modèle 3D HikenFistModel côté client : poing + avant-bras + masse de flammes).
 *
 * C'est une vraie entité projectile du mod (elle étend {@link Projectile}, PAS une boule de feu) avec son propre déplacement,
 * ses propres collisions et sa propre explosion : plus aucune LargeFireball n'est créée par Hiken, donc plus aucune boule de feu
 * visible (ni pendant le vol, ni à l'impact).
 *
 * - Déplacement : ligne droite à vitesse constante, calculé côté serveur dans {@link #tick()} (pas de physique, pas de gravité).
 * - Collision : un rayon part du DEVANT des phalanges ({@link #front()}) et balaie le trajet du tick ; le poing touche donc
 *   visuellement l'obstacle au moment de l'impact. Blocs solides et entités vivantes sont gérés ; le lanceur est ignoré.
 * - Impact : dégâts directs sur l'entité touchée, explosion (mêmes réglages que l'ancienne boule de feu : MOB_GRIEFING, puissance
 *   donnée par la capacité), puis le poing disparaît. FireEffects (traînée de particules, onde de choc, dégâts de zone) suit
 *   cette entité et déclenche son effet d'impact quand elle disparaît, exactement comme avant.
 * - Synchronisation : position, orientation et vitesse sont envoyées par Minecraft à chaque tick (updateInterval 1 dans ModEntities) ;
 *   le client ne simule rien, il affiche simplement ce que le serveur envoie. Jamais sauvegardée (noSave), jamais invocable (noSummon).
 *
 * Repère : la position de l'entité = le CENTRE du poing (pivot du modèle). Le point de collision/d'impact est le devant des phalanges,
 * à {@link #FRONT_OFFSET} blocs devant ce centre, dans le sens du vol.
 */
public class HikenFist extends Projectile {

    /** Échelle du modèle : 1 pixel de modèle = MODEL_SCALE / 16 bloc (le poing fait ~6,75 blocs de large). Lue aussi par le renderer client. */
    public static final double MODEL_SCALE = 6.0;

    /** Distance (en blocs) entre le centre du poing et le devant de ses phalanges, à l'échelle MODEL_SCALE (13 px de modèle). */
    public static final double FRONT_OFFSET = 13.0 / 16.0 * MODEL_SCALE;

    /** Dégâts directs sur l'entité touchée (comme l'ancienne boule de feu vanilla), avant l'explosion et les dégâts de zone. */
    private static final float DIRECT_DAMAGE = 6.0F;

    /** Marge de collision autour du rayon (blocs) : équivalent de la hitbox 1x1 de l'ancienne boule de feu + la marge vanilla. */
    private static final float ENTITY_MARGIN = 0.8F;

    /** Zone de recherche des entités autour du trajet du tick (blocs). */
    private static final double SEARCH_INFLATE = 1.5;

    /** Sécurité : au bout de 10 s de vol sans rien toucher, le poing explose là où il est (évite un projectile éternel). */
    private static final int MAX_AGE = 20 * 10;

    /** Vrai seulement pour un poing créé par l'attaque : un poing orphelin (ancienne sauvegarde) est supprimé. */
    private boolean managed;

    private int explosionPower = 4;
    private int age;

    /** Sens du vol (serveur uniquement : le client reçoit l'orientation via la rotation de l'entité). */
    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);

    public HikenFist(EntityType<? extends HikenFist> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Lance le poing : le DEVANT de ses phalanges part de {@code front}, il fonce vers {@code direction} à {@code speed} blocs/tick,
     * puis explose avec la puissance {@code explosionPower} à l'impact.
     */
    public static HikenFist spawn(ServerLevel level, ServerPlayer owner, Vec3 front, Vec3 direction,
                                  double speed, int explosionPower) {
        HikenFist fist = new HikenFist(ModEntities.HIKEN_FIST, level);
        fist.managed = true;
        fist.explosionPower = explosionPower;
        fist.setOwner(owner);
        fist.follow(front, direction);
        fist.setDeltaMovement(fist.heading.scale(speed));
        level.addFreshEntity(fist);
        return fist;
    }

    /** Place le poing de sorte que le devant de ses phalanges soit sur {@code front}, et l'oriente vers {@code direction}. */
    private void follow(Vec3 front, Vec3 direction) {
        Vec3 dir = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        this.heading = dir;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.setPos(front.x - dir.x * FRONT_OFFSET, front.y - dir.y * FRONT_OFFSET, front.z - dir.z * FRONT_OFFSET);
    }

    /** Le devant des phalanges : le point qui touche les obstacles, et autour duquel FireEffects dessine la traînée et l'impact. */
    public Vec3 front() {
        return this.position().add(this.heading.scale(FRONT_OFFSET));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return; // client : rien à simuler, la position et l'orientation arrivent du serveur
        }
        if (!this.managed) {
            this.discard(); // poing orphelin
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.front();
        Vec3 to = from.add(motion);

        // 1) premier bloc solide sur le trajet du tick, 2) première entité touchée avant ce bloc
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : to;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, this, from, end, new AABB(from, to).inflate(SEARCH_INFLATE), this::canHitEntity, ENTITY_MARGIN);
        if (entityHit != null) {
            hit = entityHit;
        }

        if (hit.getType() != HitResult.Type.MISS) {
            Entity target = hit instanceof EntityHitResult entityResult ? entityResult.getEntity() : null;
            this.detonate(level, hit.getLocation(), target);
            return;
        }

        this.age++;
        if (this.age >= MAX_AGE) {
            this.detonate(level, from, null);
            return;
        }
        this.setPos(this.position().add(motion));
    }

    /** Impact : le poing se cale sur le point touché, blesse la cible, explose, puis disparaît (FireEffects prend le relais). */
    private void detonate(ServerLevel level, Vec3 point, Entity target) {
        // Léger recul : le centre de l'explosion ne doit pas être enfoui dans le bloc touché (sinon il absorberait l'onde)
        Vec3 center = point.subtract(this.heading.scale(0.15));
        this.follow(center, this.heading);

        Entity owner = this.getOwner();
        if (target != null) {
            DamageSource source = level.damageSources().source(
                    owner == null ? DamageTypes.UNATTRIBUTED_FIREBALL : DamageTypes.FIREBALL, this, owner);
            if (target.hurtServer(level, source, DIRECT_DAMAGE)) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
        }

        boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        level.explode(this, center.x, center.y, center.z, (float) this.explosionPower, griefing, Level.ExplosionInteraction.MOB);
        this.discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != this.getOwner() && !(entity instanceof HikenFist) && entity.canBeHitByProjectile();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // rien à synchroniser en plus : position, rotation et vitesse sont envoyées par Minecraft
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // le poing est indestructible : l'explosion de sa propre attaque ne le touche jamais
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true; // ni poussé ni détruit par les explosions (la sienne, celles des autres attaques de feu...)
    }

    @Override
    public boolean isPickable() {
        return false; // ni cible de projectile, ni de curseur
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // Hitbox 1x1 : la distance d'affichage par défaut (~64 blocs) serait bien trop courte pour un poing de ~7 blocs de large
        return distance < 256.0 * 256.0;
    }
}
