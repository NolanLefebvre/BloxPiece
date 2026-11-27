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

    /** Job du joueur (identifiant de nolan.bloxpiece.job.Job), choisi dès le niveau 5. Sauvegardé, gardé à la mort. */
    public static final AttachmentType<String> PLAYER_JOB = AttachmentRegistry.create(
            BloxPiece.id("job"),
            // envoyé à TOUS les joueurs : leur client doit savoir qui est Épéiste pour dessiner le Haki sur son arme
            builder -> builder.persistent(Codec.STRING).copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
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
     * Quêtes UNIQUES du joueur (quêtes de Haki de Silvers Rayleigh : acceptées, en cours, terminées), voir UniqueQuests. Même
     * fonctionnement que QUESTS : sauvegardé avec le joueur (son UUID), conservé à la mort, synchronisé avec son client seulement.
     */
    /**
     * Quêtes RÉPÉTABLES du joueur (missions du Tavernier : mission proposée, mission en cours, historique), voir
     * RepeatableQuests. Sauvegardé avec le joueur (son UUID), conservé à la mort, synchronisé avec son client seulement.
     */
    public static final AttachmentType<nolan.bloxpiece.quest.RepeatableQuest.Data> REPEATABLE_QUESTS = AttachmentRegistry.create(
            BloxPiece.id("repeatable_quests"),
            builder -> builder.persistent(nolan.bloxpiece.quest.RepeatableQuest.Data.CODEC).copyOnDeath()
                    .syncWith(nolan.bloxpiece.quest.RepeatableQuest.Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
    );

    public static final AttachmentType<nolan.bloxpiece.quest.UniqueQuestData> UNIQUE_QUESTS = AttachmentRegistry.create(
            BloxPiece.id("unique_quests"),
            builder -> builder.persistent(nolan.bloxpiece.quest.UniqueQuestData.CODEC).copyOnDeath()
                    .syncWith(nolan.bloxpiece.quest.UniqueQuestData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * La race du joueur (id de la race, ex : "human").
     * Pas de valeur = race par défaut (voir Races.DEFAULT). Lire avec Races.of(player).
     */
    public static final AttachmentType<String> RACE = AttachmentRegistry.create(
            BloxPiece.id("race"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath()
                    // tous les joueurs proches la reçoivent : chacun voit l'apparence de race des autres (bras du Cyborg)
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
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
     * Vrai tant que le joueur charge le Raigo (Goro Goro no Mi) : les DEUX bras se lèvent vers la boule électrique. Pas
     * sauvegardé, synchronisé avec tous les joueurs qui le voient (pose visible chez tout le monde). Posé et retiré par
     * GoroRaigoAbility (fin de charge, tir, annulation).
     */
    public static final AttachmentType<Boolean> RAIGO_CHARGING = AttachmentRegistry.create(
            BloxPiece.id("raigo_charging"),
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
     * Épée Ace : instant (temps de jeu) du début de la FRAPPE de la Lame dévastatrice du Haki des Rois (après la charge). Pas
     * sauvegardé, synchronisé avec TOUS les joueurs : chacun anime le coup en diagonale et le retour (voir PlayerModelMixin).
     */
    public static final AttachmentType<Integer> ACE_SLASH = AttachmentRegistry.create(
            BloxPiece.id("ace_slash"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * Épée Ace : instant (temps de jeu) où le joueur a commencé à CHARGER la Lame dévastatrice (clic droit maintenu) ; retiré au
     * lancer. Pas sauvegardé, synchronisé avec TOUS les joueurs : chacun voit l'épée levée pendant la charge (PlayerModelMixin).
     */
    public static final AttachmentType<Integer> ACE_CHARGE = AttachmentRegistry.create(
            BloxPiece.id("ace_charge"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * Épée Ace : diagonale de la Lame dévastatrice (vrai = deuxième diagonale, du haut à gauche vers le bas à droite ; tirée au
     * hasard par le serveur au début de chaque attaque). Pas sauvegardé, synchronisé avec TOUS les joueurs : chacun anime le bon geste.
     */
    /**
     * Haki des Rois : instant (temps de jeu) de la dernière activation. Pas sauvegardé, synchronisé avec TOUS les joueurs : chacun
     * anime la libération de puissance (PlayerModelMixin) et le flash rouge (ConquerorFlash).
     */
    public static final AttachmentType<Integer> CONQUEROR_RELEASE = AttachmentRegistry.create(
            BloxPiece.id("conqueror_release"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    // ------------------------------------------------------------------ Kame Kame no Mi (fruit de la Tortue)
    /**
     * Carapace portée : positif = instant (temps de jeu) où elle a commencé à pousser ; négatif = moins l'instant où elle a
     * commencé à rentrer (retrait) ; absent = pas de carapace. Sauvegardé (elle reste au retour du joueur), synchronisé avec
     * TOUS les joueurs : chacun la voit pousser, rester et rentrer (KameShellLayer).
     */
    public static final AttachmentType<Integer> KAME_SHELL = AttachmentRegistry.create(
            BloxPiece.id("kame_shell"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** Rétraction dans la carapace : instant de début (temps de jeu). Synchronisé avec tous (membres qui rentrent). */
    public static final AttachmentType<Integer> KAME_RETRACT = AttachmentRegistry.create(
            BloxPiece.id("kame_retract"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** Type de rétraction : 1 = vers la dimension de poche (lente), 2 = carapace tournoyante (rapide), 3 = sortie des membres. */
    public static final AttachmentType<Integer> KAME_RETRACT_KIND = AttachmentRegistry.create(
            BloxPiece.id("kame_retract_kind"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * Joueur dans sa dimension de poche : où l'attend sa carapace posée dans le monde (« dimension;x;y;z;uuid de la carapace »).
     * Sauvegardé (il peut se déconnecter dans sa poche), jamais envoyé aux clients.
     */
    public static final AttachmentType<String> KAME_RETURN = AttachmentRegistry.create(
            BloxPiece.id("kame_return"),
            builder -> builder.persistent(Codec.STRING)
    );

    /**
     * Visiteur dans la dimension de poche d'un autre joueur (KameVisits) : « uuid de l'hôte;dimension;x;y;z » du point de retour
     * sûr près de la carapace. Sauvegardé (reconnexion), jamais envoyé aux clients.
     */
    public static final AttachmentType<String> KAME_VISIT = AttachmentRegistry.create(
            BloxPiece.id("kame_visit"),
            builder -> builder.persistent(Codec.STRING)
    );

    /** Numéro de la parcelle personnelle du joueur dans la dimension de poche (attribué une fois pour toutes). */
    public static final AttachmentType<Integer> KAME_PLOT = AttachmentRegistry.create(
            BloxPiece.id("kame_plot"),
            builder -> builder.persistent(Codec.INT).copyOnDeath()
    );

    /** Monde principal : nombre de parcelles déjà attribuées (la suivante prend ce numéro). */
    public static final AttachmentType<Integer> KAME_PLOT_COUNT = AttachmentRegistry.create(
            BloxPiece.id("kame_plot_count"),
            builder -> builder.persistent(Codec.INT)
    );

    /** Carapace posée dans le monde (KameShellEntity) : UUID de son propriétaire. Sauvegardé avec l'entité. */
    public static final AttachmentType<String> KAME_SHELL_OWNER = AttachmentRegistry.create(
            BloxPiece.id("kame_shell_owner"),
            builder -> builder.persistent(Codec.STRING)
    );

    public static final AttachmentType<Boolean> ACE_REVERSE = AttachmentRegistry.create(
            BloxPiece.id("ace_reverse"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
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

    /**
     * Kit de départ du Tavernier déjà reçu par ce joueur (TavernKeeper) : sauvegardé avec le joueur (donc par UUID et par
     * monde), gardé à la mort ; une seule fois par joueur.
     */
    public static final AttachmentType<Boolean> STARTER_KIT_GIVEN = AttachmentRegistry.create(
            BloxPiece.id("starter_kit_given"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath()
    );

    /** Camp d'un bandit : l'endroit où il est apparu (ArmedBandit : il ne s'en éloigne pas trop). Sauvegardé avec lui. */
    public static final AttachmentType<net.minecraft.core.BlockPos> BANDIT_HOME = AttachmentRegistry.create(
            BloxPiece.id("bandit_home"),
            builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC)
    );

    /**
     * Gol D. Roger boss d'une Arène de Roger (GolDRoger.isArenaBoss) : marque écrite par la structure elle-même dans les données
     * du Roger qu'elle pose (roger_arena.nbt), puis sauvegardée avec lui. Absente sur un Roger invoqué par commande.
     */
    public static final AttachmentType<Boolean> ARENA_BOSS = AttachmentRegistry.create(
            BloxPiece.id("arena_boss"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /**
     * Poste de Gol D. Roger quand il est le boss d'une Arène de Roger (GolDRoger.arenaHome : centre de l'arène, enregistré à sa
     * première mise à jour) : il ne s'en éloigne pas trop. Sauvegardé avec lui.
     */
    public static final AttachmentType<net.minecraft.core.BlockPos> ARENA_HOME = AttachmentRegistry.create(
            BloxPiece.id("arena_home"),
            builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC)
    );

    /**
     * Toutes les Party (groupes de joueurs) du monde, gardées sur l'Overworld : sauvegardées avec le monde, relues au démarrage
     * du serveur (party/Parties).
     */
    public static final AttachmentType<java.util.List<nolan.bloxpiece.party.Party.Saved>> PARTIES = AttachmentRegistry.create(
            BloxPiece.id("parties"),
            builder -> builder.persistent(nolan.bloxpiece.party.Party.SAVE_CODEC)
    );

    /**
     * Race Cyborg : attaque en cours, pour les animations chez TOUS les joueurs proches (bras, canon, poses, aura) :
     * début (temps de jeu) x 8 + type (Cyborg.CANNON, SHIELD, OVERDRIVE, BEAM). Retiré à la fin de l'attaque.
     */
    public static final AttachmentType<Long> CYBORG_ACTION = AttachmentRegistry.create(
            BloxPiece.id("cyborg_action"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /**
     * Ensemble d'attaques des touches d'attaque : vrai = attaques de la RACE, absent/faux = attaques du fruit du démon (un joueur
     * sans fruit a toujours les attaques de sa race). Changé par la touche « Basculer fruit / race » (RaceAbilities.toggle),
     * sauvegardé, synchronisé avec le client du joueur (HUD des attaques).
     */
    public static final AttachmentType<Boolean> RACE_ATTACK_MODE = AttachmentRegistry.create(
            BloxPiece.id("race_attack_mode"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath()
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
    );

    /** Race Cyborg : Surcharge moteur active depuis ce temps de jeu (aura et bras incandescent chez tous les joueurs proches). */
    public static final AttachmentType<Long> CYBORG_OVERDRIVE = AttachmentRegistry.create(
            BloxPiece.id("cyborg_overdrive"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Cyborg : fin (temps de jeu de l'Overworld) de l'épuisement après une Surcharge moteur. Sauvegardé avec le joueur. */
    public static final AttachmentType<Long> CYBORG_EXHAUSTED_UNTIL = AttachmentRegistry.create(
            BloxPiece.id("cyborg_exhausted_until"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath()
    );

    /**
     * Race Humaine : attaque en cours, pour les animations et les effets chez TOUS les joueurs proches (poses, poing, aura,
     * traînées) : début (temps de jeu) x 8 + type (Human.IMPACT, RUSH, BREAKER, CONQUEROR, RUSH_END). Retiré à la fin de l'attaque.
     */
    public static final AttachmentType<Long> HUMAN_ACTION = AttachmentRegistry.create(
            BloxPiece.id("human_action"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Minks : attaque en cours (début x 8 + type, Minks.CLAWS...), pour les poses et effets chez tous les joueurs proches. */
    public static final AttachmentType<Long> MINKS_ACTION = AttachmentRegistry.create(
            BloxPiece.id("minks_action"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Lunarian : attaque en cours (début x 8 + type, Lunarian.FLAMES...), pour les poses, les ailes et les effets chez tous les joueurs proches. */
    public static final AttachmentType<Long> LUNARIAN_ACTION = AttachmentRegistry.create(
            BloxPiece.id("lunarian_action"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /**
     * Race Lunarian : mode Défense (flammes du dos allumées) : instant du dernier changement x 2 + 1 si allumées, 0 si éteintes ;
     * absent = mode Vitesse depuis toujours. Synchronisé avec tous : chacun voit les flammes grandir ou s'éteindre.
     */
    public static final AttachmentType<Long> LUNARIAN_FLAME = AttachmentRegistry.create(
            BloxPiece.id("lunarian_flame"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Fishman : attaque en cours (début x 8 + type, Fishman.WAVE...), pour les poses et effets chez tous les joueurs proches. */
    public static final AttachmentType<Long> FISHMAN_ACTION = AttachmentRegistry.create(
            BloxPiece.id("fishman_action"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Minks : fin (temps de jeu) des griffes électriques sorties ; visibles par tous les joueurs proches. */
    public static final AttachmentType<Long> MINKS_CLAWS = AttachmentRegistry.create(
            BloxPiece.id("minks_claws"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Minks, Danse des griffes éclair : mouvement en cours (début x 8 + type, MinksDanceAbility.FRONT...) : frappe adaptée. */
    public static final AttachmentType<Long> MINKS_MOVE = AttachmentRegistry.create(
            BloxPiece.id("minks_move"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );

    /** Race Minks : Sulong actif depuis ce temps de jeu (apparence Sulong et aura chez tous les joueurs proches). */
    public static final AttachmentType<Long> MINKS_SULONG = AttachmentRegistry.create(
            BloxPiece.id("minks_sulong"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
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

    /** Maîtrise du Haki : une case par type (0 = base, 1 = maîtrisé), voir HakiMastery. Sauvegardée, gardée à la mort. */
    public static final AttachmentType<java.util.List<Integer>> HAKI_MASTERY = AttachmentRegistry.create(
            BloxPiece.id("haki_mastered"),
            builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
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
    /**
     * Minks : transformations Sulong de la nuit en cours ({@code jour * 4 + nombre}, jour = horloge du monde / 24000). Sauvegardé,
     * gardé à la mort : on ne contourne pas la limite (MinksSulongAbility.PER_NIGHT) en se reconnectant ou en mourant.
     */
    public static final AttachmentType<Long> MINKS_SULONG_NIGHT = AttachmentRegistry.create(
            BloxPiece.id("minks_sulong_night"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath()
    );

    public static final AttachmentType<Long> UNCONSCIOUS_UNTIL = AttachmentRegistry.create(
            BloxPiece.id("unconscious_until"),
            builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
    );
}
