package nolan.bloxpiece.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.EnteiSun;
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
    private static final double MAX_AIM_RANGE = 60.0;  // portée du raycast qui cherche le point visé (entités + blocs)
    private static final double MELEE_RANGE = 2.0;     // ennemi visé à moins de 2 blocs des yeux : touché immédiatement

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

        // Direction : le poing part TOUJOURS de la droite (start), mais converge vers le point exact visé par le viseur, même de très
        // près. Le point visé : raycast depuis les yeux sur MAX_AIM_RANGE blocs, entités ET blocs (le premier touché).
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(look.scale(MAX_AIM_RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 blockPoint = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(MAX_AIM_RANGE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, blockPoint, searchBox,
                e -> e.isAlive() && e.isPickable() && !e.isSpectator() && !(e instanceof Projectile) && !(e instanceof EnteiSun),
                eye.distanceToSqr(blockPoint));
        Entity aimed = entityHit != null ? entityHit.getEntity() : null;
        // Sur une entité : on vise le centre de son corps (pas le bord effleuré par le rayon)
        Vec3 targetPoint = aimed != null ? aimed.getBoundingBox().getCenter() : blockPoint;
        Vec3 toTarget = targetPoint.subtract(start);
        Vec3 direction = toTarget.lengthSqr() < 0.25 ? look : toTarget.normalize();

        // Le poing de feu est le projectile lui-même : le devant de ses phalanges part de `start` et fonce vers le point visé.
        // FireEffects suit cette entité (traînée de particules) et déclenche l'effet d'impact quand elle disparaît.
        HikenFist fist = HikenFist.spawn(level, player, start, direction, SPEED);
        FireEffects.track(fist, player, FireEffects.Kind.HIKEN);

        // Corps-à-corps : ennemi collé devant le viseur (moins de MELEE_RANGE blocs) -> touché immédiatement, sans risque que le poing,
        // parti de la droite, passe à côté ou derrière lui
        if (aimed != null && entityHit.getLocation().distanceTo(eye) < MELEE_RANGE) {
            fist.strikeNow(aimed);
        }

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
