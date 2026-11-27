package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import nolan.bloxpiece.registry.ModItems;

/**
 * Gol D. Roger, le Roi des Pirates (modèle 3D : RogerModels). Première version : un pirate armé (même IA que les autres, voir
 * ArmedPirate : il attaque joueurs et Marines à vue) mais bien plus fort, un peu plus grand (échelle 1,1 : hitbox et rendu,
 * attribut SCALE dans ModEntities), qui ne se fait pas repousser et ne disparaît jamais au loin. Son épée Ace toujours en
 * main (fourreau vide au côté gauche) ; il la laisse tomber à sa mort (table de butin). En combat, il lance aussi la Lame dévastatrice du Haki des Rois avec Ace (même attaque
 * que le joueur : AceSlashGoal). À 50 % de vie, il libère son Haki des Rois, en bien plus puissant que le joueur
 * (RogerConquerorGoal). Résiste au Haki des Rois. Invocation : /summon bloxpiece:gol_d_roger.
 * Boss de l'Arène de Roger (structure roger_arena, marque ModAttachments.ARENA_BOSS posée par la structure) : il garde le centre de
 * l'arène (ArenaHomeGoal : il y reste sans cible, abandonne une poursuite trop lointaine et revient). Un Roger invoqué par
 * commande n'a pas cette marque : il se comporte comme avant.
 * Boss d'arène : il attend IMMOBILE au centre (il suit juste les joueurs du regard) et n'attaque personne tant qu'on ne l'a pas
 * frappé en premier ({@link #provoked}) ; frappé (corps-à-corps, projectile, pouvoir...), il combat normalement. Combat fini
 * (plus de cible depuis {@link #CALM_DOWN_TICKS} ticks), il revient au centre et attend de nouveau.
 */
public class GolDRoger extends ArmedPirate {

    public static final BloxMobStats STATS = new BloxMobStats(
            400.0, // points de vie
            18.0,  // dégâts par coup (sans le sabre)
            0.30,  // vitesse
            40.0   // détection / poursuite (blocs)
    );

    /** Boss d'arène : sans cible depuis ce temps après un combat (10 s), il se calme et attend de nouveau d'être frappé. */
    private static final int CALM_DOWN_TICKS = 200;

    /** Boss d'arène : frappé en premier par quelqu'un (il combat) ; faux : il attend sans bouger ni attaquer. */
    private boolean provoked;
    /** Boss d'arène provoqué : ticks passés sans cible (CALM_DOWN_TICKS : il se calme). */
    private int calmTicks;

    /** Boss d'arène : sans cible, il ne s'éloigne pas à plus de ce rayon du centre (l'arène fait 10,5 blocs de rayon). */
    private static final double ARENA_IDLE_RADIUS = 6.0;
    /** Boss d'arène : il abandonne une poursuite au-delà de ce rayon du centre (un peu au-delà des murs) et revient. */
    private static final double ARENA_CHASE_RADIUS = 30.0;

    /** Libération du Haki des Rois à 50 % de vie : déjà faite (une seule fois), et à lancer (RogerConquerorGoal). */
    private boolean conquerorUsed;
    private boolean conquerorPending;

    public GolDRoger(EntityType<? extends GolDRoger> type, Level level) {
        super(type, level);
        // Ace tombe à sa mort par sa table de butin (loot_table/entities/gol_d_roger.json), toujours, une seule fois : l'épée
        // tenue en main ne tombe jamais en plus (sinon elle pourrait tomber deux fois)
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    /** Ace toujours en main, même hors combat (le fourreau reste au côté, vide). */
    @Override
    public boolean stowsWeapon() {
        return false;
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModItems.ACE); // son sabre légendaire
    }

    /** En plus de ses coups d'épée : la Lame dévastatrice du Haki des Rois avec Ace (AceSlashGoal), prioritaire quand elle part. */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new nolan.bloxpiece.mob.ai.AceSlashGoal(this));
        this.goalSelector.addGoal(0, new nolan.bloxpiece.mob.ai.RogerConquerorGoal(this)); // libération du Haki des Rois
        // boss d'arène seulement : attente immobile tant qu'il n'est pas provoqué (avant tous ses autres déplacements)
        this.goalSelector.addGoal(0, new ArenaWaitGoal(this));
        // boss d'arène seulement (sans poste : le but ne fait rien)
        this.goalSelector.addGoal(1, new nolan.bloxpiece.mob.ai.ArenaHomeGoal(this, this::arenaHome, ARENA_IDLE_RADIUS,
                ARENA_CHASE_RADIUS));
    }

    /**
     * Boss d'une Arène de Roger ? Marque ModAttachments.ARENA_BOSS, écrite par la structure dans les données du Roger posé
     * (roger_arena.nbt : « fabric:attachments » -> « bloxpiece:arena_boss »), puis sauvegardée avec lui.
     */
    public boolean isArenaBoss() {
        return Boolean.TRUE.equals(this.getAttached(nolan.bloxpiece.registry.ModAttachments.ARENA_BOSS));
    }

    /**
     * Centre de l'arène gardée (null : Roger ordinaire). Enregistré à la première mise à jour du boss, là où la structure l'a posé
     * (le centre exact), puis sauvegardé avec lui (ModAttachments.ARENA_HOME).
     */
    public net.minecraft.core.BlockPos arenaHome() {
        net.minecraft.core.BlockPos home = this.getAttached(nolan.bloxpiece.registry.ModAttachments.ARENA_HOME);
        if (home == null && this.isArenaBoss() && !this.level().isClientSide()) {
            home = this.blockPosition();
            this.setAttached(nolan.bloxpiece.registry.ModAttachments.ARENA_HOME, home);
        }
        return home;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount == 1) {
            this.arenaHome(); // boss d'arène : enregistre le centre dès l'apparition
        }
        if (!this.level().isClientSide() && this.provoked) {
            // combat fini depuis un moment : il se calme et attend de nouveau qu'on le frappe
            this.calmTicks = this.getTarget() == null ? this.calmTicks + 1 : 0;
            if (this.calmTicks >= CALM_DOWN_TICKS) {
                this.provoked = false;
                this.calmTicks = 0;
            }
        }
    }

    /** Boss d'arène qui attend d'être frappé (immobile, n'attaque personne) ? */
    public boolean waitingInArena() {
        return !this.provoked && this.isArenaBoss();
    }

    /** Boss d'arène pas encore provoqué : il ne prend aucune cible (joueurs, Marines, alerte de ses alliés...). */
    @Override
    public void setTarget(net.minecraft.world.entity.LivingEntity target) {
        if (target != null && this.waitingInArena()) {
            return;
        }
        super.setTarget(target);
    }

    /**
     * Attente du boss d'arène : tant qu'il n'est pas provoqué, il ne bouge pas (aucune balade, aucune poursuite) ; s'il a été
     * écarté du centre, il y retourne. Il peut toujours tourner la tête vers les joueurs (LookAtPlayerGoal).
     */
    static final class ArenaWaitGoal extends Goal {

        private final GolDRoger roger;

        ArenaWaitGoal(GolDRoger roger) {
            this.roger = roger;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.roger.waitingInArena();
        }

        @Override
        public void start() {
            this.roger.setTarget(null);
            this.roger.setAggressive(false);
        }

        @Override
        public void tick() {
            net.minecraft.core.BlockPos home = this.roger.arenaHome();
            if (home != null && this.roger.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 4.0) {
                if (this.roger.getNavigation().isDone()) {
                    this.roger.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
                }
            } else {
                this.roger.getNavigation().stop(); // à sa place : il ne bouge pas
            }
        }
    }

    /**
     * Passage sous 50 % de vie PENDANT LE COMBAT (vie avant le coup au-dessus du seuil, après le coup en dessous, toujours en
     * vie) : il libère son Haki des Rois (RogerConquerorGoal), une seule fois. Plusieurs coups autour du seuil : une seule
     * libération. Apparu déjà sous 50 % : jamais (le seuil n'est pas franchi).
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
                              float amount) {
        if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity && this.isArenaBoss()) {
            this.provoked = true; // boss d'arène frappé en premier : il combat (avant super : la riposte vanilla peut le cibler)
            this.calmTicks = 0;
        }
        float half = this.getMaxHealth() * 0.5F;
        float before = this.getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !this.conquerorUsed && before > half && this.getHealth() <= half && this.isAlive()) {
            this.conquerorUsed = true;
            this.conquerorPending = true;
        }
        return hurt;
    }

    public boolean conquerorPending() {
        return this.conquerorPending;
    }

    public void clearConquerorPending() {
        this.conquerorPending = false;
    }

    @Override
    protected Goal combatGoal() {
        return new MeleeAttackGoal(this, 1.25, true);
    }

    /** Un personnage unique : jamais retiré du monde quand les joueurs s'éloignent. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
