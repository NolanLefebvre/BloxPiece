package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
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

import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.fx.BuddhaEffects;
import nolan.bloxpiece.fx.BuddhaPalm;
import nolan.bloxpiece.fx.BuddhaSeal;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Deuxième attaque du fruit du Bouddha : la Paume du Bouddha (l'onde de choc de Sengoku, frappée paume ouverte).
 *
 * 1. Charge (0,7 s) : le joueur lève la main, un faisceau de lumière monte vers le ciel et un sceau doré se dessine haut
 *    au-dessus de la cible ; un second sceau marque déjà la zone au sol.
 * 2. Une gigantesque main ouverte dorée (modèle 3D BuddhaPalmModel, entité BuddhaPalm), auréolée, jaillit du sceau et s'abat sur la
 *    cible en accélérant. Elle arrive en biais depuis le côté du joueur : à la 1re personne, elle passe au-dessus de lui et entre
 *    par le haut de l'écran ; les autres joueurs la voient tomber du ciel.
 * 3. Impact : explosion d'énergie dorée (blocs détruits, dégâts une fois par entité, recul), débris, poussière, onde de choc,
 *    dôme et colonne de lumière, auréole géante ; la main reste un instant au sol puis se dissout (voir BuddhaEffects).
 *
 * Utilisable sous forme normale ou transformée (première attaque). Tout est géré côté serveur : la charge avance dans tick()
 * (appelé par ModEvents), l'entité BuddhaPalm est synchronisée par Minecraft et les particules sont envoyées à tous les joueurs proches.
 */
public class BuddhaPalmAbility implements Ability {

    private static final int CHARGE_TICKS = 14;
    private static final double MAX_AIM_RANGE = 64.0;
    /** Hauteur d'apparition de la main au-dessus du point d'impact (blocs). */
    private static final double DROP_HEIGHT = 28.0;
    /** Recul horizontal du point d'apparition vers le joueur (fraction de la hauteur) : la chute est inclinée (~65°), pas verticale. */
    private static final double DROP_SLANT = 0.45;
    /** Hauteur minimale de chute sous un plafond (blocs). */
    private static final double MIN_DROP = 6.0;

    private static final class Charge {
        int age;
        BuddhaSeal seal; // le cercle doré au sol, qui suit le point visé pendant la charge
    }

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    @Override
    public String id() {
        return "buddha_palm";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 12; // 12 secondes : attaque très destructrice
    }

    @Override
    public int requiredLevel() {
        return 3; // comme la deuxième attaque du fruit du feu (Hiken)
    }

    @Override
    public boolean canUse(ServerPlayer player) {
        return !CHARGES.containsKey(player.getUUID()) // une seule charge à la fois
                && BuddhaHits.requireTransformed(player); // réservée à la forme transformée
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Charge charge = new Charge();
        charge.seal = BuddhaSeal.spawn(level, landingPoint(level, player, player.getLookAngle()));
        CHARGES.put(player.getUUID(), charge);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
    }

    /** À appeler à chaque tick serveur (voir ModEvents) : fait avancer les charges, puis lance la main. */
    public static void tick(MinecraftServer server) {
        if (CHARGES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            // Déconnecté, mort ou fruit retiré pendant la charge : l'attaque est annulée
            if (player == null || !player.isAlive() || player.isInWater()
                    || Fruits.get(player.getAttached(ModAttachments.FRUIT)) != Fruits.BUDDHA) {
                entry.getValue().seal.discard();
                it.remove();
                continue;
            }
            Charge charge = entry.getValue();
            charge.age++;
            ServerLevel level = (ServerLevel) player.level();
            double size = player.getScale();
            Vec3 look = player.getLookAngle();
            // La cible suit le regard pendant la charge, puis est figée au lancer
            Vec3 landing = landingPoint(level, player, look);
            Vec3 sky = skyPoint(level, player, landing, look);
            Vec3 fall = landing.subtract(sky).normalize();
            if (charge.age < CHARGE_TICKS) {
                Vec3 hand = player.getEyePosition().add(0.0, 0.9 * size, 0.0).add(look.scale(0.3 * size));
                Vec3 back = player.getEyePosition().subtract(look.scale(0.8 * size)).add(0.0, -0.2 * size, 0.0);
                double progress = (double) charge.age / CHARGE_TICKS;
                BuddhaEffects.charge(level, hand, sky, fall, back, look, size, progress, charge.age);
                charge.seal.place(landing);
                continue;
            }
            it.remove();
            charge.seal.place(landing);
            launch(level, player, sky, landing, fall, charge.seal);
        }
    }

    public static void clear() {
        for (Charge charge : CHARGES.values()) {
            charge.seal.discard();
        }
        CHARGES.clear();
    }

    /**
     * Point d'impact : la surface visée (ou loin devant si rien n'est visé), ramenée au sol en dessous (la main s'écrase toujours
     * sur le sol, même si le joueur vise un mur ou le ciel).
     */
    private static Vec3 landingPoint(ServerLevel level, ServerPlayer player, Vec3 look) {
        Vec3 aim = AimHelper.findTarget(player, MAX_AIM_RANGE);
        if (aim == null) {
            aim = player.getEyePosition().add(look.scale(MAX_AIM_RANGE));
        }
        BlockHitResult down = level.clip(new ClipContext(aim.add(0.0, 0.5, 0.0), aim.add(0.0, -MAX_AIM_RANGE, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return down.getType() == HitResult.Type.MISS ? aim : down.getLocation();
    }

    /** Point d'apparition dans le ciel : au-dessus de la cible, décalé vers le joueur, et toujours sous un éventuel plafond. */
    private static Vec3 skyPoint(ServerLevel level, ServerPlayer player, Vec3 landing, Vec3 look) {
        Vec3 toPlayer = new Vec3(player.getX() - landing.x, 0.0, player.getZ() - landing.z);
        if (toPlayer.lengthSqr() < 1.0E-3) {
            toPlayer = new Vec3(-look.x, 0.0, -look.z);
        }
        toPlayer = toPlayer.lengthSqr() < 1.0E-3 ? new Vec3(0.0, 0.0, 1.0) : toPlayer.normalize();
        Vec3 sky = landing.add(toPlayer.scale(DROP_HEIGHT * DROP_SLANT)).add(0.0, DROP_HEIGHT, 0.0);

        // Plafond, falaise, arbre : la main apparaît juste sous l'obstacle (au moins MIN_DROP blocs de chute)
        Vec3 from = landing.add(0.0, 0.5, 0.0);
        BlockHitResult blocked = level.clip(new ClipContext(from, sky, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blocked.getType() != HitResult.Type.MISS) {
            Vec3 dir = sky.subtract(from).normalize();
            double free = Math.max(MIN_DROP, blocked.getLocation().distanceTo(from) - 3.0);
            sky = from.add(dir.scale(free));
        }
        return sky;
    }

    private static void launch(ServerLevel level, ServerPlayer player, Vec3 sky, Vec3 landing, Vec3 fall, BuddhaSeal seal) {
        BuddhaPalm.spawn(level, player, sky, landing, seal);

        // Le joueur abat sa main avec son bras principal
        InteractionHand hand = player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        player.swing(hand, SwingAnimation.DEFAULT, true);

        BuddhaEffects.launch(level, sky, player.position(), fall);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.2F);
    }
}
