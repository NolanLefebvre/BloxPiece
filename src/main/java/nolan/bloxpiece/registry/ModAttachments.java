package nolan.bloxpiece.registry;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.progression.PlayerProgress;
import nolan.bloxpiece.quest.QuestData;

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
     * Les quêtes du joueur (journalières pour l'instant, voir Quests). Sauvegardées, conservées à la mort, synchronisées avec
     * le client du joueur (affichage du menu Quêtes). Pas de valeur = pas encore tirées (elles le sont à la connexion).
     */
    public static final AttachmentType<QuestData> QUESTS = AttachmentRegistry.create(
            BloxPiece.id("quests"),
            builder -> builder.persistent(QuestData.CODEC).copyOnDeath()
                    .syncWith(QuestData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
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
     * Vrai pendant une attaque du Gura Gura no Mi qui porte une boule sismique dans la main (Gekishin, charge de Shima Yurashi) :
     * l'objet tenu en main n'est pas dessiné (ni en troisième personne, ni en première) pour ne pas traverser la boule. Pas
     * sauvegardé, synchronisé avec tous les joueurs.
     */
    public static final AttachmentType<Boolean> HIDE_HAND_ITEM = AttachmentRegistry.create(
            BloxPiece.id("hide_hand_item"),
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

    /**
     * Corail à Bulles : instant (temps de jeu) où la bulle d'air s'est formée autour du joueur ; absent = pas de bulle. Sauvegardé
     * (la bulle survit à une déconnexion) mais perdu à la mort, synchronisé avec TOUS les joueurs : chacun dessine la bulle et son
     * animation de formation à partir de cet instant (voir BubbleCoralLayer). Logique : BubbleCoral.
     */
    public static final AttachmentType<Integer> BUBBLE_CORAL = AttachmentRegistry.create(
            BloxPiece.id("bubble_coral"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * Menottes en Granite Marin : présent (true) tant que le joueur est menotté. Sauvegardé (il le reste après une déconnexion),
     * perdu à la mort, synchronisé avec TOUS les joueurs (mains dans le dos, menottes aux poignets, cadenas). Logique : Handcuffs.
     */
    public static final AttachmentType<Boolean> HANDCUFFED = AttachmentRegistry.create(
            BloxPiece.id("handcuffed"),
            builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /**
     * Menottes : secondes restantes avant qu'elles ne s'ouvrent d'elles-mêmes (joueurs et mobs). Sauvegardé, et envoyé au
     * joueur concerné pour son compteur à l'écran (HandcuffsClient).
     */
    public static final AttachmentType<Integer> HANDCUFF_SECONDS = AttachmentRegistry.create(
            BloxPiece.id("handcuff_seconds"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * Village de départ : présent (true) sur l'Overworld une fois la question réglée pour ce monde (village construit, ou monde
     * existant ignoré). Sauvegardé avec le monde : le village n'est jamais reconstruit. Voir StarterVillage.
     */
    public static final AttachmentType<Boolean> STARTER_VILLAGE = AttachmentRegistry.create(
            BloxPiece.id("starter_village"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Village de départ : centre de la place (Overworld), sauvegardé avec le monde. La Boussole du Village pointe dessus. */
    public static final AttachmentType<net.minecraft.core.BlockPos> VILLAGE_CENTER = AttachmentRegistry.create(
            BloxPiece.id("village_center"),
            builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC)
    );

    /** Village de départ au spawn : point d'apparition sûr (à l'air libre sur la place), vérifié à chaque apparition. */
    public static final AttachmentType<net.minecraft.core.BlockPos> VILLAGE_SPAWN = AttachmentRegistry.create(
            BloxPiece.id("village_spawn"),
            builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC)
    );

    /** Le joueur a déjà reçu sa Boussole du Village (première connexion). Sauvegardé, gardé à la mort. */
    public static final AttachmentType<Boolean> VILLAGE_COMPASS_GIVEN = AttachmentRegistry.create(
            BloxPiece.id("village_compass_given"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath()
    );

    public static void initialize() {
    }

    // ------------------------------------------------------------------ Haki

    /**
     * Hakis débloqués (bits : 1 Armement, 2 Observation, 4 Rois ; voir HakiType). Sauvegardé, conservé à la mort, envoyé au
     * joueur (HUD). Pas de valeur = jamais réglé : tout est débloqué à la connexion (première version : les 3 dès le départ).
     */
    public static final AttachmentType<Integer> HAKI_UNLOCKED = AttachmentRegistry.create(
            BloxPiece.id("haki_unlocked"),
            builder -> builder.persistent(Codec.INT).copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
    );

    /** Haki de l'Armement actif : 1 = bras, 2 = corps entier. Synchronisé avec tous les joueurs (revêtement noir visible). */
    public static final AttachmentType<Integer> HAKI_ARMAMENT = AttachmentRegistry.create(
            BloxPiece.id("haki_armament"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** Haki de l'Observation actif. Synchronisé avec tous les joueurs. */
    public static final AttachmentType<Boolean> HAKI_OBSERVATION = AttachmentRegistry.create(
            BloxPiece.id("haki_observation"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /** Haki des Rois en cours de libération. Envoyé au joueur (HUD). */
    public static final AttachmentType<Boolean> HAKI_CONQUEROR = AttachmentRegistry.create(
            BloxPiece.id("haki_conqueror"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /**
     * Esquive de l'Observation : compteur (x 2) + côté (0 = gauche, 1 = droite). Chaque nouvelle valeur déclenche l'animation
     * d'esquive chez tous les joueurs qui le voient (PlayerModelMixin).
     */
    public static final AttachmentType<Integer> HAKI_DODGE = AttachmentRegistry.create(
            BloxPiece.id("haki_dodge"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** Jauge de Haki (0 à HakiEnergy.MAX), gardée à la déconnexion ; envoyée seulement au joueur concerné (son HUD). */
    public static final AttachmentType<Integer> HAKI_ENERGY = AttachmentRegistry.create(
            BloxPiece.id("haki_energy"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * Évanoui par le Haki des Rois jusqu'à ce tick de jeu (joueurs et mobs). Sauvegardé, synchronisé avec tous (rendu allongé,
     * paralysie côté client).
     */
    public static final AttachmentType<Long> UNCONSCIOUS_UNTIL = AttachmentRegistry.create(
            BloxPiece.id("unconscious_until"),
            builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );
}
