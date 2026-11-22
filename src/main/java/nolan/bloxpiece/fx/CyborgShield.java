package nolan.bloxpiece.fx;

import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.party.Parties;
import nolan.bloxpiece.registry.ModEntities;

/**
 * Bouclier énergétique (race Cyborg) : une sphère centrée sur le milieu du corps de son lanceur ({@link #CENTER_Y} ; elle le
 * suit), qui grandit en {@code grow} ticks jusqu'à
 * {@code radius} blocs, tient jusqu'à {@code life} ticks puis se dissipe (rendu : CyborgShieldRenderer, hexagones bleus).
 * Côté SERVEUR, il rend la barrière réelle :
 * <ul>
 *   <li>toute créature (hors lanceur et membres de sa Party) qui entre dans le dôme en est repoussée au bord ;</li>
 *   <li>tout projectile qui l'atteint (flèche, boule de feu, balle, trident...) est renvoyé vers l'extérieur ;</li>
 *   <li>les dégâts sont refusés au lanceur tant qu'il est dedans (Cyborg.shielded, via {@link #protects}).</li>
 * </ul>
 * Jamais sauvegardé.
 */
public class CyborgShield extends ModFxEntity {

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(CyborgShield.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GROW = SynchedEntityData.defineId(CyborgShield.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(CyborgShield.class, EntityDataSerializers.INT);
    /** Durée de la disparition (ticks). */
    public static final int FADE = 12;
    /** Hauteur du centre de la sphère au-dessus des pieds du lanceur : le milieu de son corps (il est au centre du bouclier). */
    public static final float CENTER_Y = 0.9F;

    private UUID ownerId;

    public CyborgShield(EntityType<? extends CyborgShield> type, Level level) {
        super(type, level);
    }

    public static CyborgShield spawn(ServerLevel level, ServerPlayer owner, float radius, int grow, int life) {
        CyborgShield shield = new CyborgShield(ModEntities.CYBORG_SHIELD, level);
        shield.managed = true;
        shield.ownerId = owner.getUUID();
        shield.setOwner(owner);
        shield.entityData.set(RADIUS, radius);
        shield.entityData.set(GROW, Math.max(1, grow));
        shield.entityData.set(LIFE, life);
        shield.setPos(owner.getX(), owner.getY(), owner.getZ());
        level.addFreshEntity(shield);
        return shield;
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public int grow() {
        return this.entityData.get(GROW);
    }

    public int life() {
        return this.entityData.get(LIFE);
    }

    /** Rayon actuel (il grandit pendant le déploiement, rétrécit pendant la disparition). */
    public float currentRadius(float age) {
        float g = Math.min(1.0F, age / grow());
        float out = Math.max(0.0F, Math.min(1.0F, (life() - age) / FADE));
        float ease = 1.0F - (1.0F - g) * (1.0F - g) * (1.0F - g);
        return radius() * ease * (0.6F + 0.4F * out);
    }

    /** Le dôme protège-t-il ce joueur (dôme formé, pas en train de disparaître, joueur dedans) ? */
    public boolean protects(ServerPlayer player) {
        return this.tickCount >= 4 && this.tickCount < life() - 2 && player.getUUID().equals(this.ownerId)
                && player.position().distanceTo(this.position()) <= radius(); // (la sphère le suit : il est toujours au centre)
    }

    /** Fin anticipée (lanceur mort, déconnecté...) : le dôme se dissipe. */
    public void collapse() {
        int end = this.tickCount + FADE;
        if (end < life()) {
            this.entityData.set(LIFE, end);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        if (this.tickCount >= life()) {
            this.discard();
            return;
        }
        ServerPlayer owner = this.ownerId == null ? null : level.getServer().getPlayerList().getPlayer(this.ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            collapse();
            return;
        }
        this.setPos(owner.getX(), owner.getY(), owner.getZ()); // le dôme suit son lanceur
        float r = currentRadius(this.tickCount);
        if (r < 0.5F || this.tickCount >= life() - FADE) {
            return;
        }
        Vec3 c = this.position().add(0.0, CENTER_Y, 0.0); // centre de la sphère : le milieu du corps du lanceur
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(r + 2.5), e -> e != owner && e.isAlive() && !e.isSpectator())) {
            if (e instanceof ModFxEntity || Parties.allies(e, owner)) {
                continue; // effets d'attaques (gérés par leurs dégâts) et alliés : libres
            }
            if (e instanceof Projectile projectile) {
                Entity shooter = projectile.getOwner();
                if (shooter == owner || Parties.allies(shooter, owner)) {
                    continue; // ses propres tirs (et ceux de sa Party) sortent librement
                }
                Vec3 d = e.position().subtract(c);
                if (d.length() < r + 0.6) {
                    reflect(level, projectile, c, d, r);
                }
                continue;
            }
            if (!(e instanceof LivingEntity living)) {
                continue;
            }
            Vec3 d = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0).subtract(c);
            double limit = r + living.getBbWidth() * 0.5 + 0.1;
            if (d.length() >= limit) {
                continue; // dehors
            }
            // repoussée au bord de la sphère, à sa hauteur : distance horizontale nécessaire à cette hauteur
            double ring = Math.sqrt(Math.max(0.0, limit * limit - d.y * d.y)) + 0.05;
            Vec3 dir = new Vec3(d.x, 0.0, d.z);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
            Vec3 out = c.add(dir.x * ring, 0.0, dir.z * ring);
            if (living instanceof ServerPlayer pushed) {
                // un joueur : repoussé fort (sa position est gérée par son client : on lui envoie sa nouvelle vitesse)
                pushed.push(dir.x * 0.8, 0.2, dir.z * 0.8);
                pushed.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(pushed));
            } else {
                living.setPos(out.x, living.getY(), out.z);
                living.setDeltaMovement(Vec3.ZERO);
                living.push(dir.x * 0.5, 0.15, dir.z * 0.5); // (push demande aussi la synchronisation de la vitesse)
            }
            if (this.tickCount % 4 == 0) {
                Vec3 p = c.add(dir.scale(r));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, living.getY() + 1.0, p.z, 6, 0.2, 0.4, 0.2, 0.15);
            }
        }
    }

    private void reflect(ServerLevel level, Projectile projectile, Vec3 c, Vec3 d, float r) {
        Vec3 n = d.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : d.normalize();
        Vec3 v = projectile.getDeltaMovement();
        double dot = v.dot(n);
        Vec3 bounced = dot < 0.0 ? v.subtract(n.scale(2.0 * dot)) : v.add(n.scale(0.3));
        Vec3 at = c.add(n.scale(r + 0.7));
        projectile.setPos(at.x, at.y, at.z);
        projectile.setDeltaMovement(bounced.scale(0.7));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 4.0F);
        builder.define(GROW, 20);
        builder.define(LIFE, 180);
    }
}
