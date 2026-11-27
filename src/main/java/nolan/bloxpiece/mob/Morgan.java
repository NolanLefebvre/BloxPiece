package nolan.bloxpiece.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.mob.ai.MorganAxeGoal;

/**
 * Morgan « main de hache », capitaine de la Marine (modèle 3D : MorganModels / MorganModel). 3 blocs de haut : hitbox
 * 1,4 x 3,0 (ModEntities), modèle conçu à cette taille (pas d'attribut SCALE). Sa hache est emmanchée à son bras droit (elle fait
 * partie du modèle : rien en main). Marine : il attaque à vue joueurs et pirates, ses alliés Marines l'aident (ArmedMarine).
 * <p>
 * Trois attaques à la hache, choisies par MorganAxeGoal : coup classique (cible proche), rotation (plusieurs ennemis autour de
 * lui), coup vertical au sol (frappe puissante devant lui). Le début et le type de l'attaque en cours sont synchronisés
 * ({@link #ATTACK_AT}, {@link #ATTACK_KIND}) : tous les joueurs voient la même animation, au même moment que les dégâts
 * (calculés côté serveur). Invocation : /summon bloxpiece:morgan.
 */
public class Morgan extends ArmedMarine {

    /** Face au Haki des Rois, Morgan compte comme un niveau 10 : seul un joueur de niveau supérieur l'endort (ConquerorHaki). */
    public static final int CONQUEROR_LEVEL = 10;

    public static final BloxMobStats STATS = new BloxMobStats(
            220.0, // points de vie
            16.0,  // dégâts d'un coup de hache
            0.24,  // vitesse (lourd, mais de grandes enjambées)
            32.0   // détection / poursuite (blocs)
    );

    /** Heure du monde (ticks) du début du dernier coup de hache, synchronisée serveur -> clients pour l'animation. */
    private static final EntityDataAccessor<Integer> ATTACK_AT = SynchedEntityData.defineId(Morgan.class, EntityDataSerializers.INT);
    /** Type de cette attaque : MorganAxeGoal.CLASSIC, SPIN ou SLAM. */
    private static final EntityDataAccessor<Integer> ATTACK_KIND = SynchedEntityData.defineId(Morgan.class, EntityDataSerializers.INT);

    public Morgan(EntityType<? extends Morgan> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_AT, -100000);
        builder.define(ATTACK_KIND, 0);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    /** Début d'une attaque à la hache (serveur) : son type (MorganAxeGoal.CLASSIC, SPIN, SLAM). */
    public void startAxeSwing(int kind) {
        this.entityData.set(ATTACK_KIND, kind);
        this.entityData.set(ATTACK_AT, (int) this.level().getGameTime());
    }

    /** Type de la dernière attaque à la hache. */
    public int axeSwingKind() {
        return this.entityData.get(ATTACK_KIND);
    }

    /** Ticks écoulés depuis le début de la dernière attaque à la hache (avec la fraction de tick, pour le rendu). */
    public float axeSwingTicks(float partialTick) {
        return (int) this.level().getGameTime() - this.entityData.get(ATTACK_AT) + partialTick;
    }

    /** La hache fait partie de son bras (modèle) : jamais d'objet en main. */
    @Override
    protected boolean wantsWeaponInHand() {
        return false;
    }

    @Override
    protected ItemStack weapon() {
        return ItemStack.EMPTY;
    }

    @Override
    protected Goal combatGoal() {
        return new MorganAxeGoal(this);
    }

    /** Un personnage unique : jamais retiré du monde quand les joueurs s'éloignent. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
