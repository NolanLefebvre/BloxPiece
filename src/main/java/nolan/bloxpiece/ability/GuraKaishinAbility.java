package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.QuakeFx;

/**
 * Première attaque du Gura Gura no Mi : Kaishin (Fissure sismique). Le joueur frappe l'air devant lui : l'air se fissure (panneau
 * de fissures 3D qui s'agrandit en 0,5 s, un peu décalé pour ne pas boucher la vue) et une onde de choc rapide part en cône sur
 * {@link #RANGE} blocs : chaque entité du cône est blessée (plus fort près du joueur) et violemment repoussée. L'onde creuse le
 * terrain sur son passage (cratères en chapelet, blocs arrachés et projetés) si la règle mobGriefing est active.
 */
public class GuraKaishinAbility implements Ability {

    private static final double RANGE = 20.0;
    /** Demi-angle du cône (cosinus de 35°). */
    private static final double CONE_COS = Math.cos(Math.toRadians(35.0));
    private static final float NEAR_DAMAGE = 28.0F;
    private static final float FAR_DAMAGE = 14.0F;
    private static final double PUSH = 3.6;
    private static final double LIFT = 0.6;

    @Override
    public String id() {
        return "gura_kaishin";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 4;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);

        // Une seule fissure d'air, bien en face du joueur, à courte distance (fine et transparente : on voit à travers)
        Vec3 panel = eye.add(look.scale(2.2)).add(0.0, -0.1, 0.0);
        QuakeFx.spawn(level, QuakeFx.PANEL, panel, player.getYRot(), player.getXRot(), 2.4F, 26); // reste ~1 s après s'être formée

        // Onde de choc en cône
        AABB zone = new AABB(eye, eye).inflate(RANGE);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone, e -> BuddhaHits.canHit(e, player))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > RANGE || (distance > 1.5 && to.normalize().dot(look) < CONE_COS)) {
                continue;
            }
            double falloff = distance / RANGE;
            float damage = NEAR_DAMAGE + (FAR_DAMAGE - NEAR_DAMAGE) * (float) falloff;
            Vec3 push = look.scale(PUSH * (1.0 - 0.5 * falloff)).add(0.0, LIFT, 0.0);
            BuddhaHits.strike(level, player, target, damage, push);
        }

        // L'onde visible : explosions et souffle blanc le long du cône, de plus en plus large
        for (double d = 2.0; d <= RANGE; d += 1.5) {
            Vec3 p = eye.add(look.scale(d));
            double spread = 0.3 + d * Math.tan(Math.toRadians(35.0)) * 0.45;
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 6, spread, spread * 0.6, spread, 0.05);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 3, spread, spread * 0.6, spread, 0.02);
            if (((int) d) % 3 == 0) {
                level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, spread * 0.5, 0.2, spread * 0.5, 0.0);
            }
        }
        // Destruction : cratères en chapelet dans l'axe de l'onde, blocs arrachés autour
        for (double d = 5.0; d <= RANGE; d += 5.0) {
            Vec3 p = eye.add(look.scale(d));
            GuraTerrain.blast(level, player, p, 2.5F + (float) d * 0.12F);
            GuraTerrain.debris(level, p, 3.0, 4, 0.7);
        }
        level.playSound(null, panel.x, panel.y, panel.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.7F);
        level.playSound(null, panel.x, panel.y, panel.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6F, 0.6F);
        BuddhaHits.shake(level, eye, 26.0, 2.0F, 10);
    }
}
