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
 * (DialogueScreen). Jamais supprimé à distance.
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
            ServerPlayNetworking.send(server, new OpenDialoguePayload(NPC_ID, this.getId()));
        }
        return InteractionResult.SUCCESS;
    }
}
