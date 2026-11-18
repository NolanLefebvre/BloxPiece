package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.EnteiFireball;
import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModParticles;
import nolan.bloxpiece.fruit.WaterCurse;

/**
 * Quatrième attaque (ultime) du fruit du feu : Dai Enkai: Entei.
 * Le joueur rassemble toutes ses flammes au-dessus de sa paume (le soleil, modèle Blockbench affiché par l'entité EnteiSun,
 * grossit et les flammes alentour sont aspirées), puis le propulse vers l'endroit visé. Il EXPLOSE à l'impact (énorme onde de
 * choc, colonne de feu, dégâts de zone, voir FireEffects.Kind.ENTEI).
 *
 * Charge, comme la Déflagration du Bouddha :
 * <ul>
 *   <li>appui court : le soleil se forme en {@link #MIN_CHARGE_TICKS} ticks et part à x{@link #MIN_POWER} de la taille d'origine ;</li>
 *   <li>touche maintenue : le soleil continue de grossir en temps réel jusqu'à x{@link #MAX_POWER} ({@link #MAX_CHARGE_TICKS}
 *       ticks), puis part tout seul. Taille du soleil, hitbox, dégâts, rayon de l'impact, mise à feu et particules sont
 *       multipliés par la puissance atteinte ; le cratère aussi.</li>
 * </ul>
 * La recharge démarre au moment du tir (cooldownOnRelease). Les dégâts gardent le bonus de niveau (LivingEntityMixin).
 *
 * Tout est géré côté serveur : la charge est avancée à chaque tick (voir tick(), appelé par ModEvents) et
 * s'annule si le joueur meurt, quitte la dimension ou le serveur, perd son fruit ou repasse sous le niveau requis.
 * Pendant la charge, le joueur LÈVE LE BRAS (attachment ENTEI_CHARGING, vu par tous les joueurs proches : le bras est
 * relevé côté client par PlayerModelMixin) ; au tir, il le rabat d'un geste. Particules et sons viennent du serveur.
 */
public class DaiEnkaiEnteiAbility implements Ability {

    private static final int MIN_CHARGE_TICKS = 10;        // appui court : le soleil se forme (0,5 s) puis part à taille normale
    private static final int MAX_CHARGE_TICKS = 60;        // charge pleine (3 s) : tir automatique
    private static final double MIN_POWER = 0.5;           // taille / puissance d'un appui court (x la taille d'origine)
    private static final double MAX_POWER = 1.25;          // taille / puissance à pleine charge
    private static final double SPEED = 1.0;               // plus lent que les autres boules de feu : c'est un soleil
    private static final int EXPLOSION_POWER = 36;         // 4 = TNT, 3 = Grande boule de feu / météores, 18 = version précédente (x2 = 36 : cratère colossal ; BAISSE-LA si le serveur lague au moment de l'impact)
    private static final double MAX_AIM_RANGE = 64.0;      // distance maximale du point visé
    private static final double START_BALL_RADIUS = 0.1;   // taille de la boule au début de la charge (elle grossit jusqu'à MAX_BALL_RADIUS)
    private static final double MAX_BALL_RADIUS = 5.4;     // rayon du soleil à charge normale (x1) rassemblé au-dessus du joueur (avant : 3.6, soit +50 %)
    private static final double BALL_HEIGHT = 3.0;         // hauteur du BAS de la boule au-dessus des pieds du joueur (avant : 2.0) : bien visible, sans chevaucher le personnage
    private static final double REQUIRED_HEADROOM = 11.0;  // hauteur libre nécessaire au-dessus du joueur (10.0 + 1.0 : la boule est montée d'un bloc)

    // joueur -> charge en cours (une seule à la fois par joueur)
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    private static final class Charge {
        final DaiEnkaiEnteiAbility ability;
        final ServerPlayer player;
        final ServerLevel level;
        final EnteiSun sun; // le soleil visible (modèle Blockbench) qui grossit pendant la charge
        int age;
        boolean released; // touche relâchée : le soleil part dès qu'il est formé

        Charge(DaiEnkaiEnteiAbility ability, ServerPlayer player, ServerLevel level, EnteiSun sun) {
            this.ability = ability;
            this.player = player;
            this.level = level;
            this.sun = sun;
        }

        /** Puissance : MIN_POWER une fois le soleil formé, MAX_POWER à pleine charge. */
        double power() {
            double t = (double) (this.age - MIN_CHARGE_TICKS) / (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS);
            return MIN_POWER + (MAX_POWER - MIN_POWER) * Math.max(0.0, Math.min(1.0, t));
        }

        /** Avancement de la surcharge (0 = appui court, 1 = pleine charge). */
        double overcharge() {
            return (this.power() - MIN_POWER) / (MAX_POWER - MIN_POWER);
        }

        /** Rayon actuel du soleil : il se forme jusqu'à MAX_BALL_RADIUS, puis grossit avec la puissance. */
        double radius() {
            if (this.age < MIN_CHARGE_TICKS) {
                return START_BALL_RADIUS + (MAX_BALL_RADIUS * MIN_POWER - START_BALL_RADIUS) * this.age / MIN_CHARGE_TICKS;
            }
            return MAX_BALL_RADIUS * this.power();
        }
    }

    @Override
    public String id() {
        return "dai_enkai_entei";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60; // 60 secondes : le double de la Pluie de météores (30 s)
    }

    @Override
    public int requiredLevel() {
        return 8; // après la Pluie de météores (niveau 5)
    }

    /** Pas de charge déjà en cours, et assez de place au-dessus du joueur pour la boule. Sinon rien ne part (et pas de recharge). */
    @Override
    public boolean canUse(ServerPlayer player) {
        if (CHARGES.containsKey(player.getUUID())) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (AimHelper.clearanceAbove(level, player.position(), REQUIRED_HEADROOM + 1.0) < REQUIRED_HEADROOM) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.no_room_above"));
            return false;
        }
        return true;
    }

    @Override
    public boolean cooldownOnRelease() {
        return true;
    }

    @Override
    public void release(ServerPlayer player) {
        Charge charge = CHARGES.get(player.getUUID());
        if (charge != null) {
            charge.released = true;
        }
    }

    /** Démarre la charge ; le tir part au relâchement de la touche, ou tout seul à pleine charge (tick()). */
    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        EnteiSun sun = EnteiSun.spawn(level, ballCenter(player, START_BALL_RADIUS), START_BALL_RADIUS);
        CHARGES.put(player.getUUID(), new Charge(this, player, level, sun));
        player.setAttached(ModAttachments.ENTEI_CHARGING, true); // le bras se lève (chez tout le monde)
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0f, 0.5f);
    }

    /** Appelé à chaque tick serveur (ModEvents) : fait avancer toutes les charges en cours. */
    public static void tick() {
        if (CHARGES.isEmpty()) {
            return;
        }
        Iterator<Charge> it = CHARGES.values().iterator();
        while (it.hasNext()) {
            Charge charge = it.next();
            if (!isStillValid(charge)) {
                charge.sun.discard(); // charge annulée : le soleil disparaît
                lowerArm(charge.player);
                it.remove();
                continue;
            }
            charge.age++;
            if (charge.age >= MIN_CHARGE_TICKS && (charge.released || charge.age >= MAX_CHARGE_TICKS)) {
                lowerArm(charge.player); // avant le tir : le geste de lancer prend le relais
                AbilityManager.startCooldown(charge.player, charge.ability); // la recharge démarre au tir
                launch(charge);
                it.remove();
            } else {
                gather(charge);
            }
        }
    }

    /** Oublie les charges en cours (arrêt du serveur). */
    public static void clear() {
        for (Charge charge : CHARGES.values()) {
            charge.sun.discard();
            lowerArm(charge.player);
        }
        CHARGES.clear();
    }

    /** Le joueur baisse le bras (fin ou annulation de la charge). */
    private static void lowerArm(ServerPlayer player) {
        player.removeAttached(ModAttachments.ENTEI_CHARGING);
    }

    /**
     * La charge continue-t-elle ? Non si le joueur est mort / déconnecté / dans une autre dimension,
     * a perdu son fruit (ex : /removedf) ou n'a plus le niveau requis (ex : XP retirée) : une attaque verrouillée ne part jamais.
     */
    private static boolean isStillValid(Charge charge) {
        ServerPlayer player = charge.player;
        if (player.isRemoved() || !player.isAlive() || player.level() != charge.level || WaterCurse.touchesWater(player)) {
            return false;
        }
        DevilFruit fruit = Fruits.get(player.getAttached(ModAttachments.FRUIT));
        if (fruit == null || !charge.ability.isUnlockedFor(player)) {
            return false;
        }
        for (int slot = 0; slot < DevilFruit.MAX_ABILITIES; slot++) {
            if (fruit.getAbility(slot) == charge.ability) {
                return true; // le fruit actuel possède bien cette attaque
            }
        }
        return false;
    }

    /** Centre de la boule : au-dessus du joueur côté main droite ; son bas reste BALL_HEIGHT blocs au-dessus des pieds (hors du champ de vision), elle monte en grossissant. */
    private static Vec3 ballCenter(ServerPlayer player, double radius) {
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        return new Vec3(
                player.getX() + rightX * 0.5 + forwardX * 0.5,
                player.getY() + BALL_HEIGHT + radius,
                player.getZ() + rightZ * 0.5 + forwardZ * 0.5);
    }

    // ---------------------------------------------------------------- charge : les flammes se rassemblent

    private static void gather(Charge charge) {
        ServerPlayer player = charge.player;
        ServerLevel level = charge.level;
        RandomSource random = level.getRandom();

        double progress = Math.min(1.0, (double) charge.age / MIN_CHARGE_TICKS); // formation du soleil
        double power = charge.power();                                           // puis surcharge (1 à MAX_POWER)
        double radius = charge.radius();
        double over = charge.overcharge();
        int extra = (int) Math.round(6 * over);                         // plus de flammes aspirées en surcharge
        Vec3 center = ballCenter(player, radius);
        Vec3 hand = ballCenter(player, 0.0); // la paume levée, d'où naît la boule

        // La boule : le modèle Blockbench suit le joueur et grossit (aucune particule dessus : on veut voir le modèle)
        charge.sun.setPos(center.x, center.y, center.z);
        charge.sun.setRadius(radius);

        // 1. Les flammes des environs sont aspirées vers la boule (plus nombreuses, de plus loin à mesure qu'elle grossit)
        for (int i = 0; i < 6 + extra; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 3.0 + radius + random.nextDouble() * 4.0 * power;
            double px = player.getX() + Math.cos(angle) * distance;
            double py = player.getY() + random.nextDouble() * 4.0;
            double pz = player.getZ() + Math.sin(angle) * distance;
            Vec3 toCenter = new Vec3(center.x - px, center.y - py, center.z - pz).normalize();
            // count = 0 : les "offsets" servent de direction à la particule
            level.sendParticles(ModParticles.FIRE_FLAME, px, py, pz, 0, toCenter.x, toCenter.y, toCenter.z, 0.45);
        }

        // 2. Deux anneaux de flammes inclinés qui tournent autour de la boule (juste à l'extérieur du modèle)
        double spin = charge.age * 0.35;
        int ringPoints = (int) (28 * power);
        FireEffects.orbitRing(level, center, radius * 1.25 + 0.2, ringPoints, spin, 0.0, 0.0);
        FireEffects.orbitRing(level, center, radius * 1.25 + 0.2, ringPoints, -spin, Math.PI / 3.0, 0.0);
        if (progress > 0.4) {
            FireEffects.orbitRing(level, center, radius * 1.4 + 0.3, ringPoints + 4, spin * 0.7, -Math.PI / 3.0, Math.PI / 4.0);
        }

        // 3. Double spirale de feu qui monte le long du corps et du bras levé
        for (int j = 0; j < 4; j++) {
            double angle = charge.age * 0.5 + j * Math.PI / 2.0;
            double height = ((charge.age * 0.12 + j * 0.25) % 1.0) * 2.4;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    player.getX() + Math.cos(angle) * 0.9, player.getY() + height, player.getZ() + Math.sin(angle) * 0.9,
                    1, 0.02, 0.02, 0.02, 0.0);
        }

        // 4. Braises qui montent tout autour du joueur
        for (int i = 0; i < 5; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 0.7 + random.nextDouble() * 1.8;
            level.sendParticles(ModParticles.FIRE_FLAME,
                    player.getX() + Math.cos(angle) * distance, player.getY() + random.nextDouble() * 0.5, player.getZ() + Math.sin(angle) * distance,
                    0, 0.0, 1.0, 0.0, 0.12 + random.nextDouble() * 0.1);
        }

        // 5. Étincelles dans la paume levée ; gouttes de lave sous la boule
        level.sendParticles(ModParticles.FIRE_FLAME, hand.x, hand.y, hand.z, 3, 0.2, 0.2, 0.2, 0.03);
        if (charge.age % 3 == 0) {
            level.sendParticles(ModParticles.FIRE_EMBER, hand.x, hand.y, hand.z, 1, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ModParticles.FIRE_EMBER, center.x, center.y - radius, center.z, 2, radius * 0.4, 0.2, radius * 0.4, 0.0);
        }

        // 6. Anneau de feu au sol qui se resserre + onde de chaleur qui part du joueur toutes les 10 ticks
        FireEffects.groundRing(level, player.getX(), player.getY(), player.getZ(), 1.0 + 6.0 * (1.0 - progress), 48);
        FireEffects.groundRing(level, player.getX(), player.getY(), player.getZ(), 0.5 + 3.0 * (1.0 - progress), 24);
        if (charge.age % 10 == 0) {
            FireEffects.shockwave(level, ModParticles.FIRE_FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 40, 0.3 + 0.3 * progress);
        }

        if (charge.age % 8 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                    1.5f, (float) (0.5 + 0.5 * progress + 0.3 * over));
        }
        if (over > 0.0 && charge.age % 10 == 0) {
            // Surcharge : la chaleur fait trembler la caméra, de plus en plus fort
            BuddhaHits.shake(level, center, 30.0 * power, 0.4F + 0.8F * (float) over, 10);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 2.0f,
                    (float) (0.5 + 0.5 * over));
        }
    }

    // ---------------------------------------------------------------- tir

    private static void launch(Charge charge) {
        ServerPlayer player = charge.player;
        ServerLevel level = charge.level;

        double power = charge.power();
        double radius = charge.radius();
        Vec3 center = ballCenter(player, radius);
        Vec3 look = player.getLookAngle();

        // Le soleil part de la paume vers le point visé (recalculé ici, côté serveur) ; rien visé = droit devant
        Vec3 aim = AimHelper.findTarget(player, MAX_AIM_RANGE);
        if (aim == null) {
            aim = player.getEyePosition().add(look.scale(MAX_AIM_RANGE));
        }
        Vec3 toAim = aim.subtract(center);
        Vec3 direction = toAim.lengthSqr() < 4.0 ? look : toAim.normalize();

        // Le projectile (invisible sous le soleil) porte les dégâts et l'explosion ; le soleil le suit jusqu'à l'impact
        int blast = (int) Math.round(EXPLOSION_POWER * power);
        EnteiFireball projectile = new EnteiFireball(level, player, direction, blast);
        projectile.setHitRadius(radius * 0.8); // touche ce que le soleil touche, pas seulement son centre
        projectile.setPos(center.x, center.y, center.z);
        projectile.setDeltaMovement(direction.scale(SPEED));
        level.addFreshEntity(projectile);

        charge.sun.setPos(center.x, center.y, center.z);
        charge.sun.setRadius(radius);

        // Traînée + énorme impact (dégâts, onde de choc, colonne de feu) ; le soleil disparaît dans l'explosion.
        // Tout est multiplié par la puissance de charge.
        FireEffects.track(projectile, player, FireEffects.Kind.ENTEI, charge.sun, power);

        // Le joueur fait un geste de lancer avec le bras qui était levé (le bras droit : c'est de ce côté qu'est la boule)
        InteractionHand throwingHand = player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(throwingHand, SwingAnimation.DEFAULT, true);

        // Lancement : déflagration au point de départ, ondes de choc et double tornade de feu autour du joueur
        // (peu de particules sur la boule elle-même, pour qu'on voie le modèle)
        level.sendParticles(ModParticles.FIRE_FLAME, center.x, center.y, center.z, 60, 1.2, 1.2, 1.2, 0.2);
        level.sendParticles(ModParticles.FIRE_EMBER, center.x, center.y, center.z, 20, 1.5, 1.5, 1.5, 0.0);
        FireEffects.shockwave(level, ModParticles.FIRE_FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 96, 0.9);
        FireEffects.shockwave(level, ModParticles.FIRE_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 64, 0.5);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 2.6, 8.0, 3);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 1.8, 8.0, -3);

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 3.0f, 0.6f);
    }
}
