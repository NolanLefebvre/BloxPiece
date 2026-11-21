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
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.network.OpenDialoguePayload;

/**
 * Silvers Rayleigh, le « Seigneur des Ténèbres », Second du Roi des Pirates : PNJ du village de départ, debout devant la
 * taverne (placé par village/RayleighPlacement). Futur donneur de quêtes principal du serveur.
 * <ul>
 *   <li>Invulnérable (sauf /kill et le vide), ni poussé ni repoussé, et IMMOBILE : s'il est déplacé malgré tout (pouvoir d'un
 *       fruit, explosion...), il revient aussitôt à sa place. Il suit simplement les joueurs du regard.</li>
 *   <li>Jamais supprimé à distance : sauvegardé avec le monde, il est toujours là après un redémarrage.</li>
 *   <li>Clic droit : fenêtre de dialogue (DialogueScreen, PNJ « silvers_rayleigh »). Le système de quêtes viendra se brancher
 *       ici ({@link #mobInteract}).</li>
 * </ul>
 */
public class Rayleigh extends PathfinderMob {

    /** Identifiant du PNJ dans les dialogues (clés de traduction « npc.bloxpiece.silvers_rayleigh.* »). */
    public static final String NPC_ID = "silvers_rayleigh";

    /** Sa place (position au premier tick après son apparition ou son chargement) : il y revient s'il en est écarté. */
    private Vec3 home;

    public Rayleigh(EntityType<? extends Rayleigh> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)       // il ne quitte jamais sa place
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F)); // regarde le joueur proche
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.home == null) {
            if (this.onGround()) {
                this.home = this.position(); // posé au sol : c'est sa place
            }
            return;
        }
        if (this.position().distanceToSqr(this.home) > 0.01) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(this.home.x, this.home.y, this.home.z);
        }
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

    // ------------------------------------------------------------------ dialogue (point d'entrée du futur système de quêtes)

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            ServerPlayNetworking.send(server, new OpenDialoguePayload(NPC_ID, this.getId()));
        }
        return InteractionResult.SUCCESS;
    }
}
