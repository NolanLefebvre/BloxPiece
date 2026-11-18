package nolan.bloxpiece.fruit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Bulle d'air du Corail à Bulles (objet BubbleCoralItem). Tant qu'elle entoure le joueur :
 * <ul>
 *   <li>il respire sous l'eau indéfiniment (sa barre d'air reste pleine) ;</li>
 *   <li>il casse les blocs sous l'eau à la vitesse normale (la pénalité de minage sous l'eau est annulée, comme avec
 *       l'enchantement Affinité aquatique) ;</li>
 *   <li>l'eau ne le touche plus : un porteur de fruit du démon n'est plus maudit (il ne coule plus, n'est plus détransformé,
 *       garde ses pouvoirs et son passif Logia). Tout le mod teste le contact avec l'eau par WaterCurse.touchesWater, qui tient
 *       compte de la bulle.</li>
 * </ul>
 * La bulle est extrêmement fragile : elle éclate au moindre dégât subi (coup, flèche, chute, feu...) avec un "pop", des
 * éclaboussures et des éclats translucides, et le joueur redevient vulnérable à l'eau sur-le-champ.
 * Côté client, elle est dessinée autour du joueur par BubbleCoralLayer (attachment BUBBLE_CORAL, synchronisé avec tous).
 */
public final class BubbleCoral {

    /** Durée de la formation de la bulle (ticks) : le joueur souffle dedans pendant ce temps (animation : PlayerModelMixin). */
    public static final int FORM_TICKS = 40;

    /**
     * Minage sous l'eau : Minecraft multiplie la vitesse par l'attribut SUBMERGED_MINING_SPEED (0,2 de base = 5 fois plus lent).
     * +400 % de la base : 0,2 -> 1,0, la vitesse normale.
     */
    private static final Identifier MINING_MODIFIER = BloxPiece.id("bubble_coral_mining");
    private static final double MINING_BONUS = 4.0;

    private BubbleCoral() {
    }

    /** Le joueur (ou toute entité) est-il protégé par une bulle d'air ? Valable côté serveur ET client (attachment synchronisé). */
    public static boolean isActive(Entity entity) {
        return entity.getAttached(ModAttachments.BUBBLE_CORAL) != null;
    }

    /** Forme la bulle autour du joueur (le corail vient d'être écrasé dans sa main). */
    public static void form(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.setAttached(ModAttachments.BUBBLE_CORAL, (int) level.getGameTime());
        player.setAirSupply(player.getMaxAirSupply());
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 60, 0.4, 0.8, 0.4, 0.15);
        level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 30, 0.5, 0.9, 0.5, 0.05);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.5F, 1.8F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.6F);
        player.sendSystemMessage(Component.translatable("message.bloxpiece.bubble_coral.formed"), true);
    }

    /** Fait éclater la bulle (dégât subi). Sans effet si le joueur n'en a pas. */
    public static void pop(ServerPlayer player) {
        if (!isActive(player)) {
            return;
        }
        player.removeAttached(ModAttachments.BUBBLE_CORAL);
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 120, 0.5, 0.9, 0.5, 0.4);
        level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 50, 0.6, 1.0, 0.6, 0.1);
        level.sendParticles(ParticleTypes.FALLING_WATER, x, y + 0.5, z, 40, 0.6, 0.6, 0.6, 0.0);
        level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 1.9F);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.8F, 1.5F);
        player.sendSystemMessage(Component.translatable("message.bloxpiece.bubble_coral.popped"), true);
    }

    /** Ajoute (bulle active) ou retire (pas de bulle) le bonus de minage sous l'eau. Vérifié à chaque tick : rien à oublier. */
    private static void updateMining(ServerPlayer player, boolean active) {
        AttributeInstance mining = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        if (mining == null) {
            return;
        }
        boolean has = mining.getModifier(MINING_MODIFIER) != null;
        if (active && !has) {
            mining.addTransientModifier(new AttributeModifier(MINING_MODIFIER, MINING_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!active && has) {
            mining.removeModifier(MINING_MODIFIER);
        }
    }

    /**
     * À chaque tick serveur : un joueur dans sa bulle garde sa barre d'air pleine, même sous l'eau. Pendant la formation, le joueur
     * souffle dans la bulle : petites bulles et souffle devant sa bouche.
     */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer formedAt = player.getAttached(ModAttachments.BUBBLE_CORAL);
            updateMining(player, formedAt != null);
            if (formedAt == null) {
                continue;
            }
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            ServerLevel level = (ServerLevel) player.level();
            long age = level.getGameTime() - formedAt;
            if (age >= 4 && age < FORM_TICKS && age % 2 == 0) {
                Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.45)).add(0.0, -0.2, 0.0);
                Vec3 blow = player.getLookAngle().scale(0.15);
                level.sendParticles(ParticleTypes.BUBBLE_POP, mouth.x, mouth.y, mouth.z, 3, 0.08, 0.08, 0.08, 0.02);
                level.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 0, blow.x, blow.y + 0.02, blow.z, 0.3);
            }
        }
    }
}
