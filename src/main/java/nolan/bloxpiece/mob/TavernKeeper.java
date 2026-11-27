package nolan.bloxpiece.mob;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.network.OpenDialoguePayload;

/**
 * Le Tavernier : PNJ de la taverne du village de départ, derrière son comptoir. Invulnérable, immobile (aucun déplacement, ni
 * poussé ni repoussé), il suit simplement les joueurs du regard. Clic droit : fenêtre de dialogue (guide du mod), côté client
 * (DialogueScreen). Jamais supprimé à distance. Au premier clic droit de chaque joueur, il lui donne son kit de départ (64 steaks
 * et un sabre de pirate), une seule fois par joueur et par monde (voir giveStarterKit).
 */
public class TavernKeeper extends PathfinderMob {

    /** Identifiant du PNJ dans les dialogues (clés de traduction « npc.bloxpiece.tavern_keeper.* »). */
    public static final String NPC_ID = "tavern_keeper";

    public TavernKeeper(EntityType<? extends TavernKeeper> type, Level level) {
        super(type, level);
        // invulnérabilité : voir hurtServer ; jamais supprimé à distance : voir requiresCustomPersistence / removeWhenFarAway
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)       // il ne quitte jamais son comptoir
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 1.0F)); // regarde le joueur proche
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ invulnérable et fixe
    // (méthodes sans @Override : si l'une d'elles est renommée dans une version de Minecraft, la compilation passe quand même)

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount); // /kill, vide
        }
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    protected void doPush(Entity entity) {
        // ne pousse personne
    }

    public boolean isPushedByFluid() {
        return false;
    }

    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    public boolean requiresCustomPersistence() {
        return true;
    }

    public boolean canBeLeashed() {
        return false;
    }

    // ------------------------------------------------------------------ dialogue

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            // quêtes répétables : une mission lui est tirée (et gardée) s'il n'en a pas, pour que le dialogue la propose
            nolan.bloxpiece.quest.RepeatableQuests.prepareOffer(server);
            // le dialogue s'ouvre comme avant ; sa première réplique annonce le kit de départ (reçu, déjà reçu, inventaire plein)
            ServerPlayNetworking.send(server, new OpenDialoguePayload(NPC_ID, this.getId(), giveStarterKit(server)));
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ kit de départ (une fois par joueur et par monde)

    /** Steaks du kit (un stack). */
    private static final int KIT_STEAKS = 64;
    /** Emplacements de l'inventaire principal (barre d'action + sac), sans l'armure ni la main secondaire. */
    private static final int MAIN_SLOTS = 36;

    /**
     * Donne le kit de départ (64 steaks + le sabre des Pirates Sabreurs) si ce joueur ne l'a jamais reçu ; renvoie la réplique
     * du Tavernier (suffixe de clé de dialogue). Côté serveur, sur le fil du serveur (un clic à la fois) : le droit est vérifié
     * puis enregistré AVANT de donner les objets, donc deux clics rapides ne donnent jamais deux kits. Inventaire trop plein :
     * rien n'est donné ni enregistré, le joueur pourra réessayer. Enregistrement : attachment persistant du joueur
     * (STARTER_KIT_GIVEN), lié à son UUID et sauvegardé avec le monde.
     */
    private static String giveStarterKit(ServerPlayer player) {
        if (Boolean.TRUE.equals(player.getAttached(nolan.bloxpiece.registry.ModAttachments.STARTER_KIT_GIVEN))) {
            return "kit.already";
        }
        if (!hasRoomForKit(player)) {
            return "kit.full";
        }
        player.setAttached(nolan.bloxpiece.registry.ModAttachments.STARTER_KIT_GIVEN, true);
        player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, KIT_STEAKS));
        player.getInventory().add(new net.minecraft.world.item.ItemStack(nolan.bloxpiece.registry.ModItems.PIRATE_SABER));
        player.containerMenu.broadcastChanges();
        return "kit.received";
    }

    /**
     * Assez de place pour TOUT le kit, sans rien remplacer : une case vide pour le sabre, et la place pour 64 steaks (dans les
     * piles de steaks déjà commencées et/ou une autre case vide).
     */
    private static boolean hasRoomForKit(ServerPlayer player) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int empty = 0, steakRoom = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                empty++;
            } else if (stack.is(net.minecraft.world.item.Items.COOKED_BEEF)
                    && net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF))) {
                steakRoom += stack.getMaxStackSize() - stack.getCount();
            }
        }
        return steakRoom >= KIT_STEAKS ? empty >= 1 : empty >= 2;
    }
}
