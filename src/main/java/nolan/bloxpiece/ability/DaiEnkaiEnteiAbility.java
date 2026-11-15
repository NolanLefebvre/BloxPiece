package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fruit.DevilFruit;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.fx.FireEffects;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Quatrième attaque (ultime) du fruit du feu : Dai Enkai: Entei.
 * Le joueur rassemble toutes ses flammes au-dessus de sa paume (charge d'environ 1,5 s : le soleil, modèle Blockbench
 * affiché par l'entité EnteiSun, grossit petit à petit et les flammes alentour sont aspirées), puis le propulse
 * vers l'endroit visé.
 * Il EXPLOSE à l'impact (énorme onde de choc, colonne de feu, dégâts de zone, voir FireEffects.Kind.ENTEI).
 *
 * Tout est géré côté serveur : la charge est avancée à chaque tick (voir tick(), appelé par ModEvents) et
 * s'annule si le joueur meurt, quitte la dimension ou le serveur, perd son fruit ou repasse sous le niveau requis.
 * Le client n'a rien de spécial à faire : particules et sons viennent du serveur.
 */
public class DaiEnkaiEnteiAbility implements Ability {

    private static final int CHARGE_TICKS = 30;            // durée de la charge (20 ticks = 1 seconde)
    private static final double SPEED = 1.0;               // plus lent que les autres boules de feu : c'est un soleil
    private static final int EXPLOSION_POWER = 5;          // 3 = TNT (Grande boule de feu, météores), 5 = bien au-dessus
    private static final double MAX_AIM_RANGE = 64.0;      // distance maximale du point visé
    private static final double START_BALL_RADIUS = 0.1;   // taille de la boule au début de la charge (elle grossit jusqu'à MAX_BALL_RADIUS)
    private static final double MAX_BALL_RADIUS = 3.6;     // rayon final du soleil rassemblé au-dessus du joueur (les rayons de plasma dépassent encore)
    private static final double REQUIRED_HEADROOM = 7.0;   // hauteur libre nécessaire au-dessus du joueur (le soleil est gros)

    // joueur -> charge en cours (une seule à la fois par joueur)
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    private static final class Charge {
        final DaiEnkaiEnteiAbility ability;
        final ServerPlayer player;
        final ServerLevel level;
        final EnteiSun sun; // le soleil visible (modèle Blockbench) qui grossit pendant la charge
        int age;

        Charge(DaiEnkaiEnteiAbility ability, ServerPlayer player, ServerLevel level, EnteiSun sun) {
            this.ability = ability;
            this.player = player;
            this.level = level;
            this.sun = sun;
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

    /** Démarre la charge ; le tir part tout seul à la fin (tick()). */
    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        EnteiSun sun = EnteiSun.spawn(level, ballCenter(player, START_BALL_RADIUS), START_BALL_RADIUS);
        CHARGES.put(player.getUUID(), new Charge(this, player, level, sun));
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
                it.remove();
                continue;
            }
            charge.age++;
            if (charge.age >= CHARGE_TICKS) {
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
        }
        CHARGES.clear();
    }

    /**
     * La charge continue-t-elle ? Non si le joueur est mort / déconnecté / dans une autre dimension,
     * a perdu son fruit (ex : /removedf) ou n'a plus le niveau requis (ex : XP retirée) : une attaque verrouillée ne part jamais.
     */
    private static boolean isStillValid(Charge charge) {
        ServerPlayer player = charge.player;
        if (player.isRemoved() || !player.isAlive() || player.level() != charge.level) {
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

    /** Centre de la boule : au-dessus du joueur côté main droite ; son bas reste 2 blocs au-dessus des pieds (hors du champ de vision), elle monte en grossissant. */
    private static Vec3 ballCenter(ServerPlayer player, double radius) {
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        return new Vec3(
                player.getX() + rightX * 0.5 + forwardX * 0.5,
                player.getY() + 2.0 + radius,
                player.getZ() + rightZ * 0.5 + forwardZ * 0.5);
    }

    // ---------------------------------------------------------------- charge : les flammes se rassemblent

    private static void gather(Charge charge) {
        ServerPlayer player = charge.player;
        ServerLevel level = charge.level;
        RandomSource random = level.getRandom();

        double progress = (double) charge.age / CHARGE_TICKS;
        double radius = START_BALL_RADIUS + (MAX_BALL_RADIUS - START_BALL_RADIUS) * progress;
        Vec3 center = ballCenter(player, radius);

        // La boule : le modèle Blockbench suit le joueur et grossit (aucune particule dessus : on veut voir le modèle)
        charge.sun.setPos(center.x, center.y, center.z);
        charge.sun.setRadius(radius);

        // Les flammes des environs sont aspirées vers la boule
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 3.0 + random.nextDouble() * 3.0;
            double px = player.getX() + Math.cos(angle) * distance;
            double py = player.getY() + random.nextDouble() * 3.0;
            double pz = player.getZ() + Math.sin(angle) * distance;
            Vec3 toCenter = new Vec3(center.x - px, center.y - py, center.z - pz).normalize();
            // count = 0 : les "offsets" servent de direction à la particule
            level.sendParticles(ParticleTypes.FLAME, px, py, pz, 0, toCenter.x, toCenter.y, toCenter.z, 0.35);
        }

        // Anneau de feu au sol qui se resserre autour du joueur
        FireEffects.groundRing(level, player.getX(), player.getY(), player.getZ(), 1.0 + 5.0 * (1.0 - progress), 40);

        if (charge.age % 8 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                    1.5f, (float) (0.5 + 0.5 * progress));
        }
    }

    // ---------------------------------------------------------------- tir

    private static void launch(Charge charge) {
        ServerPlayer player = charge.player;
        ServerLevel level = charge.level;

        Vec3 center = ballCenter(player, MAX_BALL_RADIUS);
        Vec3 look = player.getLookAngle();

        // Le soleil part de la paume vers le point visé (recalculé ici, côté serveur) ; rien visé = droit devant
        Vec3 aim = AimHelper.findTarget(player, MAX_AIM_RANGE);
        if (aim == null) {
            aim = player.getEyePosition().add(look.scale(MAX_AIM_RANGE));
        }
        Vec3 toAim = aim.subtract(center);
        Vec3 direction = toAim.lengthSqr() < 4.0 ? look : toAim.normalize();

        // Le projectile (invisible sous le soleil) porte les dégâts et l'explosion ; le soleil le suit jusqu'à l'impact
        LargeFireball projectile = new LargeFireball(level, player, direction, EXPLOSION_POWER);
        projectile.setPos(center.x, center.y, center.z);
        projectile.setDeltaMovement(direction.scale(SPEED));
        level.addFreshEntity(projectile);

        charge.sun.setPos(center.x, center.y, center.z);
        charge.sun.setRadius(MAX_BALL_RADIUS);

        // Traînée + énorme impact (dégâts, onde de choc, colonne de feu) ; le soleil disparaît dans l'explosion
        FireEffects.track(projectile, player, FireEffects.Kind.ENTEI, charge.sun);

        // Lancement : déflagration au point de départ, ondes de choc et double tornade de feu autour du joueur
        // (peu de particules : ni fumée ni déflagration sur la boule, pour qu'on voie le modèle)
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 20, 0.6, 0.6, 0.6, 0.1);
        FireEffects.shockwave(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 64, 0.7);
        FireEffects.fireTornado(level, player.getX(), player.getY(), player.getZ(), 2.0, 5.0, 3);

        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 3.0f, 0.6f);
    }
}
