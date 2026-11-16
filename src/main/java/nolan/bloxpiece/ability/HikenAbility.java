package nolan.bloxpiece.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.fx.HikenFist;
import nolan.bloxpiece.registry.ModParticles;

/**
 * Deuxième attaque du fruit du feu : Hiken (le "poing de feu" de Portgas D. Ace). Remplace l'ancienne Grande boule de feu :
 * même emplacement (touche 2), même niveau requis, même recharge.
 *
 * Un gigantesque poing de feu (modèle 3D : poing fermé + avant-bras de flammes + masse de flammes, entité HikenFist) jaillit
 * devant le joueur et fonce dans la direction de son regard. Le poing EST le projectile : HikenFist est une entité projectile
 * personnalisée (déplacement, collisions et explosion propres), sans aucune boule de feu vanilla. Sa hitbox épouse le poing visible
 * (voir HikenFist). À l'impact : une seule explosion dont le rayon (HikenFist.BLAST_RADIUS) fixe à la fois les blocs détruits, les dégâts de
 * zone, la mise à feu, le recul et les particules (onde de choc, anneau et colonne de flammes : FireEffects.Kind.HIKEN).
 * Tout est géré côté serveur ; l'entité HikenFist (position, orientation) est synchronisée avec les clients par Minecraft.
 */
public class HikenAbility implements Ability {

    private static final double SPEED = 1.8;           // rapide : un bras propulsé (l'ancienne Grande boule de feu : 1.3)
    private static final double START_DISTANCE = 2.5;  // distance entre les yeux du joueur et le devant du poing au départ
    private static final double SIDE_OFFSET = 6.0;     // décalage du point d'apparition vers la DROITE du joueur (blocs) : le poing (~7 blocs de large) sort du champ de vision
    private static final double MAX_AIM_RANGE = 64.0;  // distance maximale du point visé
    private static final boolean AIM_AT_CROSSHAIR = true; // true : le poing part de la droite mais file vers le point visé ; false : même direction que le regard (trajet parallèle)

    @Override
    public String id() {
        return "hiken";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 8; // 8 secondes (comme l'attaque qu'il remplace)
    }

    @Override
    public int requiredLevel() {
        return 3; // comme l'attaque qu'il remplace
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        // Point de départ : devant les yeux du joueur, légèrement sous la ligne de visée (le bras sort de son épaule)
        Vec3 base = new Vec3(
                player.getX() + look.x * START_DISTANCE,
                player.getEyeY() - 0.3 + look.y * START_DISTANCE,
                player.getZ() + look.z * START_DISTANCE);

        // Décalage latéral : vers la droite du joueur (horizontal), réduit si un mur gêne pour que le poing n'apparaisse jamais dans un bloc
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        double side = SIDE_OFFSET;
        BlockHitResult wall = level.clip(new ClipContext(base, base.add(right.scale(SIDE_OFFSET)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            side = Math.max(0.0, wall.getLocation().distanceTo(base) - 0.5);
        }
        Vec3 start = base.add(right.scale(side));

        // Direction : celle du regard. Depuis la droite, un trajet strictement parallèle passerait à côté de la cible visée (le poing fait 7 blocs
        // de large mais est décalé de 6) : il file donc vers le point visé par le curseur (comme Dai Enkai: Entei), ou loin devant si rien n'est visé.
        Vec3 direction = look;
        if (AIM_AT_CROSSHAIR) {
            Vec3 aim = AimHelper.findTarget(player, MAX_AIM_RANGE);
            if (aim == null) {
                aim = player.getEyePosition().add(look.scale(MAX_AIM_RANGE));
            }
            Vec3 toAim = aim.subtract(start);
            direction = toAim.lengthSqr() < 4.0 ? look : toAim.normalize();
        }

        // Le poing de feu est le projectile lui-même : le devant de ses phalanges part de `start` et fonce vers le regard du joueur.
        // FireEffects suit cette entité (traînée de particules) et déclenche l'effet d'impact quand elle disparaît.
        HikenFist fist = HikenFist.spawn(level, player, start, direction, SPEED);
        FireEffects.track(fist, player, FireEffects.Kind.HIKEN);

        // Le joueur envoie son coup avec son bras principal
        InteractionHand punchingHand = player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(punchingHand, SwingAnimation.DEFAULT, true);

        // Lancement : déflagration à l'origine du poing, onde de choc et tourbillon de feu autour du joueur
        level.sendParticles(ModParticles.FIRE_FLAME, start.x, start.y, start.z, 50, 0.8, 0.8, 0.8, 0.15);
        level.sendParticles(ModParticles.FIRE_EMBER, start.x, start.y, start.z, 10, 1.0, 1.0, 1.0, 0.0);
        FireEffects.shockwave(level, ModParticles.FIRE_FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 56, 0.6);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.5, 3.0, 2);

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.8f);
    }
}
