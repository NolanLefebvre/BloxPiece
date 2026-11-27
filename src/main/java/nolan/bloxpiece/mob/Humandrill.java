package nolan.bloxpiece.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import nolan.bloxpiece.mob.ai.HumandrillAttackGoal;
import nolan.bloxpiece.registry.ModItems;

/**
 * Humandrill (île de Kuraigana) : babouin d'environ 4 blocs, sabreur qui imite les guerriers humains (modèle 3D :
 * HumandrillModels / HumandrillModel, côté client). Hostile aux joueurs ; quand l'un d'eux est blessé, ceux du groupe
 * proche viennent l'aider.
 * <p>
 * <b>Configuration d'armes</b>, tirée au sort à l'apparition ({@link #WEIGHT_ONE}, {@link #WEIGHT_TWO}, {@link #WEIGHT_THREE} :
 * poids modifiables), puis sauvegardée : 1 épée (main droite), 2 épées (une par main) ou 3 épées (une par main + une serrée
 * entre les mâchoires). Le nombre d'épées est synchronisé ({@link #SWORDS}) : tous les joueurs voient la même chose. Les épées
 * en main sont de vrais objets (humandrill_sword, rarement lâchés à la mort) ; celle de la bouche est dessinée d'après
 * {@link #swords()}. Invocation avec une configuration imposée : /summon bloxpiece:humandrill ~ ~ ~ {Swords:3}.
 * <p>
 * <b>Combat</b> : HumandrillAttackGoal (coups horizontal, vertical, diagonal, rapide, puissant, combo à deux épées, coup des
 * trois épées). Le début et le type de l'attaque en cours sont synchronisés ({@link #ATTACK_AT}, {@link #ATTACK_KIND}), comme
 * pour Morgan : les animations (client) et les dégâts (serveur) partagent les mêmes ticks (HumandrillAttackGoal).
 */
public class Humandrill extends BloxMob {

    public static final BloxMobStats STATS = new BloxMobStats(
            60.0,  // points de vie
            7.0,   // dégâts de base d'un coup d'épée (multipliés selon l'attaque)
            0.3,   // vitesse
            24.0   // détection / poursuite (blocs)
    );

    /** Poids des configurations tirées à l'apparition (modifiables) : 1 épée, 2 épées, 3 épées (dont une dans la bouche). */
    public static int WEIGHT_ONE = 45;
    public static int WEIGHT_TWO = 35;
    public static int WEIGHT_THREE = 20;
    /** Chance qu'une épée tenue en main tombe à la mort. */
    public static final float SWORD_DROP_CHANCE = 0.06F;

    /** Nombre d'épées (1 à 3 ; 0 : pas encore tiré au sort). */
    private static final EntityDataAccessor<Integer> SWORDS = SynchedEntityData.defineId(Humandrill.class, EntityDataSerializers.INT);
    /** Heure du monde (ticks) du début de la dernière attaque, synchronisée serveur -> clients pour l'animation. */
    private static final EntityDataAccessor<Integer> ATTACK_AT = SynchedEntityData.defineId(Humandrill.class, EntityDataSerializers.INT);
    /** Type de cette attaque (HumandrillAttackGoal : type | LEFT si elle est portée de la main gauche). */
    private static final EntityDataAccessor<Integer> ATTACK_KIND = SynchedEntityData.defineId(Humandrill.class, EntityDataSerializers.INT);
    /**
     * En combat (synchronisé) : il a une cible (joueur ou mob), ou vient d'être frappé. Reste vrai {@link #COMBAT_HOLD} ticks
     * après la fin du combat. Faux = hors combat : il range ses lames à plat sur ses épaules (animation de repos).
     */
    private static final EntityDataAccessor<Boolean> IN_COMBAT = SynchedEntityData.defineId(Humandrill.class, EntityDataSerializers.BOOLEAN);
    /** Ticks pendant lesquels il reste en garde après avoir perdu sa cible (pas de va-et-vient entre deux coups ou deux cibles). */
    public static final int COMBAT_HOLD = 60;
    private int combatTimer;

    // client : réception après un saut ou une chute (détectée ici, aucune donnée en plus à synchroniser)
    private boolean wasOnGround = true;
    private int airTicks;
    private int landedAt = -1000;
    private float landingPower;
    private float guard, guardO;  // client : 0 -> 1 quand il passe en garde (en combat), lissé

    public Humandrill(EntityType<? extends Humandrill> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SWORDS, 0);
        builder.define(ATTACK_AT, -100000);
        builder.define(ATTACK_KIND, 0);
        builder.define(IN_COMBAT, false);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HumandrillAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Humandrill.class).setAlertOthers(Humandrill.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ épées

    /** Nombre d'épées : 1 (main droite), 2 (les deux mains) ou 3 (les deux mains et la bouche). */
    public int swords() {
        return Mth.clamp(this.entityData.get(SWORDS), 1, 3);
    }

    /** Une épée entre les mâchoires (configuration à 3 épées). */
    public boolean hasMouthSword() {
        return this.entityData.get(SWORDS) >= 3;
    }

    /** Fixe la configuration (serveur) et équipe les mains en conséquence. */
    public void setSwords(int count) {
        int n = Mth.clamp(count, 1, 3);
        this.entityData.set(SWORDS, n);
        this.equipSwords();
    }

    private void equipSwords() {
        int n = this.swords();
        if (this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.HUMANDRILL_SWORD));
        }
        if (n >= 2 && this.getOffhandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.HUMANDRILL_SWORD));
        } else if (n < 2 && !this.getOffhandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        this.setDropChance(EquipmentSlot.MAINHAND, SWORD_DROP_CHANCE);
        this.setDropChance(EquipmentSlot.OFFHAND, SWORD_DROP_CHANCE);
    }

    /** Tire la configuration au sort selon les poids. */
    private void rollSwords(RandomSource random) {
        int one = Math.max(0, WEIGHT_ONE), two = Math.max(0, WEIGHT_TWO), three = Math.max(0, WEIGHT_THREE);
        int total = one + two + three;
        if (total <= 0) {
            this.setSwords(1);
            return;
        }
        int r = random.nextInt(total);
        this.setSwords(r < one ? 1 : r < one + two ? 2 : 3);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
            SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.setLeftHanded(false);
        if (this.entityData.get(SWORDS) == 0) {
            this.rollSwords(this.getRandom());
        }
        return result;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Swords", this.entityData.get(SWORDS));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int n = input.getIntOr("Swords", 0);
        if (n >= 1 && n <= 3) {
            this.entityData.set(SWORDS, n);
        }
    }

    // ------------------------------------------------------------------ attaques (synchronisées)

    /** Début d'une attaque (serveur) : son type (HumandrillAttackGoal, avec le drapeau LEFT pour la main gauche). */
    public void startAttack(int kind) {
        this.entityData.set(ATTACK_KIND, kind);
        this.entityData.set(ATTACK_AT, (int) this.level().getGameTime());
    }

    /** Type de la dernière attaque (avec le drapeau LEFT). */
    public int attackKind() {
        return this.entityData.get(ATTACK_KIND);
    }

    /** Ticks écoulés depuis le début de la dernière attaque (avec la fraction de tick, pour le rendu). */
    public float attackTicks(float partialTick) {
        return (int) this.level().getGameTime() - this.entityData.get(ATTACK_AT) + partialTick;
    }

    // ------------------------------------------------------------------ réception (client)

    /** Ticks depuis la dernière réception au sol (avec la fraction de tick). */
    public float landingTicks(float partialTick) {
        return this.tickCount - this.landedAt + partialTick;
    }

    /** Force de la dernière réception (0 à 1, selon la durée passée en l'air). */
    public float landingPower() {
        return this.landingPower;
    }

    /** En combat (cible, coups reçus) ou hors combat (repos, lames sur les épaules) : synchronisé. */
    public boolean inCombat() {
        return this.entityData.get(IN_COMBAT);
    }

    /** Client : 0 au repos .. 1 en garde (lissé, avec la fraction de tick). */
    public float guardAmount(float partialTick) {
        return Mth.lerp(partialTick, this.guardO, this.guard);
    }

    /** Ticks passés en l'air (0 au sol). */
    public int airTicks() {
        return this.airTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.guardO = this.guard;
            this.guard += ((this.inCombat() ? 1.0F : 0.0F) - this.guard) * 0.2F;
            boolean ground = this.onGround();
            if (ground && !this.wasOnGround && this.airTicks > 4) {
                this.landedAt = this.tickCount;
                this.landingPower = Mth.clamp((this.airTicks - 4) / 14.0F, 0.25F, 1.0F);
            }
            this.airTicks = ground || this.isInWater() ? 0 : this.airTicks + 1;
            this.wasOnGround = ground;
            return;
        }
        // en combat : une cible vivante, ou frappé il y a moins de 5 s ; puis encore COMBAT_HOLD ticks (jamais entre deux coups)
        net.minecraft.world.entity.LivingEntity target = this.getTarget();
        boolean fighting = target != null && target.isAlive() || this.getLastHurtByMob() != null; // (oublié par Minecraft 5 s après le dernier coup reçu)
        this.combatTimer = fighting ? COMBAT_HOLD : Math.max(0, this.combatTimer - 1);
        if (this.entityData.get(IN_COMBAT) != this.combatTimer > 0) {
            this.entityData.set(IN_COMBAT, this.combatTimer > 0);
        }
        if (this.entityData.get(SWORDS) == 0) {
            this.rollSwords(this.getRandom()); // apparu sans finalizeSpawn (apparition en groupe, données imposées) : tirage dès le 1er tick
        } else if (this.tickCount % 20 == 0) {
            this.equipSwords(); // une épée perdue revient (comme les armes des combattants armés)
        }
    }

    @Override
    public int getMaxFallDistance() {
        return 5; // agile : saute de plus haut qu'un humain pour rattraper sa cible
    }

    // ------------------------------------------------------------------ sons (cris de primate : sons vanilla plus aigus)

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAggressive() ? SoundEvents.PIGLIN_BRUTE_ANGRY : SoundEvents.PANDA_AGGRESSIVE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_BRUTE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.35F;
    }
}
