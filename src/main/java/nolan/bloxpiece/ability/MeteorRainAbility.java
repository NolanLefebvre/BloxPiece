package nolan.bloxpiece.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FireEffects;

/**
 * Troisième attaque (ultime) du fruit du feu : une pluie de météores enflammés
 * qui s'abat sur l'endroit visé par le curseur du joueur.
 * La zone est marquée par des cercles de feu, puis les météores tombent à la verticale
 * (avec une traînée en spirale) et explosent à l'impact.
 */
public class MeteorRainAbility implements Ability {

    private static final double MAX_RANGE = 32.0;       // distance maximale à laquelle on peut viser
    private static final double SPREAD = 8.0;           // rayon de la zone touchée autour du point visé
    private static final int METEOR_COUNT = 14;
    private static final int EXPLOSION_POWER = 3;
    private static final double MAX_SKY_HEIGHT = 50.0;  // hauteur max d'où tombent les météores
    private static final double MIN_CLEARANCE = 3.0;    // hauteur libre minimale au-dessus de la cible

    @Override
    public String id() {
        return "meteor_rain";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 30; // 30 secondes
    }

    @Override
    public int requiredLevel() {
        return 5;
    }

    /** Il faut viser quelque chose à portée, avec un peu de place au-dessus. Sinon rien ne part (et pas de recharge). */
    @Override
    public boolean canUse(ServerPlayer player) {
        Vec3 target = AimHelper.findTarget(player, MAX_RANGE);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_target", (int) MAX_RANGE));
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (AimHelper.clearanceAbove(level, target, MAX_SKY_HEIGHT) < MIN_CLEARANCE) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_room"));
            return false;
        }
        return true;
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();

        // Le point visé est recalculé ici, côté serveur, à partir du regard actuel du joueur
        Vec3 target = AimHelper.findTarget(player, MAX_RANGE);
        if (target == null) {
            return;
        }
        double clearance = AimHelper.clearanceAbove(level, target, MAX_SKY_HEIGHT);

        // Lancement : triple onde de choc, double tornade de feu autour du joueur
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 64, 0.35);
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 64, 0.6);
        FireEffects.shockwave(level, ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 48, 0.45);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.8, 5.0, 3);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.2, 5.0, -3);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

        // Zone visée : cercles de feu au sol + faisceau de flammes montant vers le ciel
        FireEffects.groundRing(level, target.x, target.y, target.z, SPREAD, 72);
        FireEffects.groundRing(level, target.x, target.y, target.z, SPREAD * 0.6, 48);
        FireEffects.groundRing(level, target.x, target.y, target.z, SPREAD * 0.3, 24);
        for (int h = 0; h < Math.min(40, (int) clearance); h++) {
            level.sendParticles(ParticleTypes.FLAME, target.x, target.y + h, target.z, 3, 0.3, 0.3, 0.3, 0.02);
        }

        // Les météores apparaissent au-dessus de la zone, sous le plafond s'il y en a un (grotte, bâtiment, Nether)
        double maxHeight = Math.min(45.0, clearance - 1.0);
        double minHeight = Math.min(20.0, maxHeight * 0.5);
        double ceilingY = target.y + clearance - 0.5;

        for (int i = 0; i < METEOR_COUNT; i++) {
            double x = target.x + (random.nextDouble() * 2 - 1) * SPREAD;
            double z = target.z + (random.nextDouble() * 2 - 1) * SPREAD;
            // Hauteurs différentes : les météores ne touchent pas le sol tous en même temps
            double y = target.y + minHeight + random.nextDouble() * (maxHeight - minHeight);

            // Si le point d'apparition est dans un relief (colline), on le remonte au-dessus
            for (int tries = 0; tries < 10 && AimHelper.isBlocked(level, new Vec3(x, y, z)); tries++) {
                y += 1.0;
            }
            if (y > ceilingY || AimHelper.isBlocked(level, new Vec3(x, y, z))) {
                continue; // pas de place pour ce météore
            }

            // Chute à la verticale : un météore tombe exactement sur la colonne où il est apparu
            Vec3 fall = new Vec3(0.0, -1.0, 0.0);
            LargeFireball meteor = new LargeFireball(level, player, fall, EXPLOSION_POWER);
            meteor.setPos(x, y, z);
            meteor.setDeltaMovement(fall.scale(1.4));
            level.addFreshEntity(meteor);

            FireEffects.track(meteor, player, FireEffects.Kind.METEOR);

            // Éclat dans le ciel à l'apparition du météore
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 40, 0.8, 0.8, 0.8, 0.1);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.5f);
    }
}
