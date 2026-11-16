package nolan.bloxpiece.registry;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.progression.PlayerProgress;

public class ModAttachments {

    /**
     * Le fruit mangé par le joueur (id du fruit, ex : "fire").
     * Sauvegardé avec le joueur et conservé après la mort.
     * Synchronisé vers le client du joueur lui-même (nécessaire pour afficher le HUD des attaques).
     * Pas de valeur = le joueur n'a mangé aucun fruit.
     */
    public static final AttachmentType<String> FRUIT = AttachmentRegistry.create(
            BloxPiece.id("fruit"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * La race du joueur (id de la race, ex : "human").
     * Pas de valeur = race par défaut (voir Races.DEFAULT). Lire avec Races.of(player).
     */
    public static final AttachmentType<String> RACE = AttachmentRegistry.create(
            BloxPiece.id("race"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * Niveau et XP du joueur. Sauvegardé, conservé après la mort, synchronisé avec le client du joueur.
     * Pas de valeur = niveau 1 sans XP. Lire/modifier avec Progression.
     */
    public static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry.create(
            BloxPiece.id("progress"),
            builder -> builder.persistent(PlayerProgress.CODEC).copyOnDeath()
                    .syncWith(PlayerProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * Vrai tant que le joueur charge Dai Enkai: Entei (bras levé). Pas sauvegardé (une charge ne survit pas à un arrêt),
     * mais synchronisé avec TOUS les joueurs qui le voient : c'est ce qui permet d'afficher le bras levé chez les autres.
     * Le serveur le met en début de charge et le retire à la fin / à l'annulation (voir DaiEnkaiEnteiAbility).
     */
    public static final AttachmentType<Boolean> ENTEI_CHARGING = AttachmentRegistry.create(
            BloxPiece.id("entei_charging"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /**
     * Pose des bras pendant une attaque du fruit du Bouddha (1 = bras tendu vers l'avant, paume ouverte ; 2 = bras levés et
     * écartés). Pas sauvegardé, synchronisé avec TOUS les joueurs qui le voient (pose appliquée par PlayerModelMixin).
     */
    public static final AttachmentType<Integer> BUDDHA_POSE = AttachmentRegistry.create(
            BloxPiece.id("buddha_pose"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * Passif Logia du fruit du Feu : parties du corps actuellement dispersées en flammes (bits de LogiaIntangibility : tête,
     * torse, bras, jambes). Pas sauvegardé, synchronisé avec TOUS les joueurs (le client cache ces membres, voir PlayerModelMixin).
     */
    public static final AttachmentType<Integer> LOGIA_PARTS = AttachmentRegistry.create(
            BloxPiece.id("logia_parts"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * Fruit de la Glace : instant (temps de jeu) du dernier coup porté avec le trident de glace. Pas sauvegardé, synchronisé avec
     * TOUS les joueurs : chacun anime l'estoc à partir de cet instant (voir PlayerModelMixin).
     */
    public static final AttachmentType<Integer> ICE_SABER_STRIKE = AttachmentRegistry.create(
            BloxPiece.id("ice_saber_strike"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    public static void initialize() {
    }
}
