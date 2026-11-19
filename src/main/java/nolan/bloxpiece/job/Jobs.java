package nolan.bloxpiece.job;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.haki.ArmamentHaki;
import nolan.bloxpiece.progression.Progression;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Le job du joueur : choisi UNE FOIS dans le menu du personnage (ChooseJobPayload) dès le niveau {@link #REQUIRED_LEVEL}, puis
 * définitif (seul un administrateur peut le changer : /bloxfruit job set). Sauvegardé, gardé à la mort, synchronisé avec le
 * joueur (sa page Job et son menu).
 * Effets : Épéiste -> ArmamentHaki.isImbued ; Bagarreur -> {@link #tick} (bonus à mains nues) ; Sniper -> HakiEnergy.
 */
public final class Jobs {

    /** Niveau requis pour choisir un job. */
    public static final int REQUIRED_LEVEL = 5;

    /** Bagarreur : bonus des dégâts à mains nues (+60 %), et en plus avec le Haki de l'Armement actif (+40 %). */
    private static final double BRAWLER_BONUS = 0.6;
    private static final double BRAWLER_HAKI_BONUS = 0.4;
    private static final Identifier BRAWLER_MODIFIER = BloxPiece.id("job_brawler_damage");

    /** Sniper : consommation de la jauge de Haki (x 0,6), et encore divisée par deux pour l'Observation. */
    public static final float SNIPER_DRAIN = 0.6F;
    public static final float SNIPER_OBSERVATION_DRAIN = 0.5F;

    private Jobs() {
    }

    public static @Nullable Job get(Entity entity) {
        return Job.byId(entity.getAttached(ModAttachments.PLAYER_JOB));
    }

    public static boolean is(Entity entity, Job job) {
        return get(entity) == job;
    }

    /** Choix d'un job depuis le menu (le serveur vérifie le niveau, et qu'aucun job n'a déjà été choisi). */
    public static void choose(ServerPlayer player, String id) {
        Job job = Job.byId(id);
        if (job == null || get(player) != null) {
            return; // déjà un job : choix définitif
        }
        if (Progression.get(player).level() < REQUIRED_LEVEL) {
            player.sendSystemMessage(Component.translatable("message.bloxpiece.job.level", REQUIRED_LEVEL), true);
            return;
        }
        player.setAttached(ModAttachments.PLAYER_JOB, job.id);
        player.sendSystemMessage(Component.translatable("message.bloxpiece.job.chosen", Component.translatable(job.nameKey())), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.6F, 1.4F);
    }

    /** Commande administrateur : impose un job (ou aucun, {@code job} = null) sans condition de niveau. */
    public static void set(ServerPlayer player, @Nullable Job job) {
        if (job == null) {
            player.removeAttached(ModAttachments.PLAYER_JOB);
        } else {
            player.setAttached(ModAttachments.PLAYER_JOB, job.id);
        }
    }

    /** Chaque tick serveur et par joueur : bonus des poings du Bagarreur (main vide). */
    public static void tick(ServerPlayer player) {
        double bonus = 0.0;
        if (is(player, Job.BRAWLER) && player.getMainHandItem().isEmpty()) {
            bonus = BRAWLER_BONUS + (ArmamentHaki.mode(player) != 0 ? BRAWLER_HAKI_BONUS : 0.0);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        AttributeModifier current = attack.getModifier(BRAWLER_MODIFIER);
        if (current != null && current.amount() == bonus) {
            return;
        }
        if (current != null) {
            attack.removeModifier(BRAWLER_MODIFIER);
        }
        if (bonus > 0.0) {
            attack.addTransientModifier(new AttributeModifier(BRAWLER_MODIFIER, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
