package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.IceEagle;
import nolan.bloxpiece.fx.IceEffects;
import nolan.bloxpiece.fruit.WaterCurse;

/**
 * Troisième attaque du fruit de la Glace : Pheasant Peck, l'aigle de glace.
 *
 * Les cristaux se rassemblent devant le joueur avec un bruit de glace qui se forme ({@link #FORM_TICKS} ticks), puis un aigle de
 * glace aux ailes déployées (IceEagle, modèle 3D animé) en jaillit et fonce droit devant sur une longue distance, en traversant
 * et repoussant les ennemis et en gelant le sol et l'eau sous lui.
 */
public class IceEagleAbility implements Ability {

    private static final int FORM_TICKS = 8;
    /** Le vol reste presque horizontal : la pente est limitée à 20°. */
    private static final double MAX_SLOPE = Math.sin(Math.toRadians(20.0));

    private static final class Forming {
        final ServerPlayer owner;
        int age;

        Forming(ServerPlayer owner) {
            this.owner = owner;
        }
    }

    private static final List<Forming> FORMING = new ArrayList<>();

    @Override
    public String id() {
        return "ice_eagle";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 16;
    }

    @Override
    public int requiredLevel() {
        return 5;
    }

    @Override
    public void use(ServerPlayer player) {
        FORMING.add(new Forming(player));
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.5F, 0.4F);
    }

    /** Point d'apparition de l'aigle : devant le joueur, à hauteur des épaules. */
    private static Vec3 spawnPoint(ServerPlayer player, Vec3 dir) {
        double size = player.getScale();
        return player.getEyePosition().add(dir.scale(3.0 * size)).add(0.0, -0.3 * size, 0.0);
    }

    private static Vec3 flightDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        double y = Math.max(-MAX_SLOPE, Math.min(MAX_SLOPE, look.y));
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize().scale(Math.sqrt(1.0 - y * y));
        return new Vec3(flat.x, y, flat.z);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Forming> it = FORMING.iterator();
        while (it.hasNext()) {
            Forming f = it.next();
            ServerPlayer player = f.owner;
            if (player.isRemoved() || !player.isAlive() || WaterCurse.touchesWater(player)) {
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            Vec3 dir = flightDirection(player);
            Vec3 at = spawnPoint(player, dir);
            if (++f.age < FORM_TICKS) {
                if (f.age % 2 == 0) {
                    IceEffects.eagleForm(level, at);
                }
                continue;
            }
            it.remove();
            IceEagle.spawn(level, player, at, dir); // explosion de givre et onde d'air glacée au lancement (IceEffects.eagleLaunch)
            BuddhaHits.shake(level, at, 28.0, 2.0F, 12);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 3.0F, 0.5F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.5F, 1.2F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.7F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
        }
    }

    public static void clear() {
        FORMING.clear();
    }
}
