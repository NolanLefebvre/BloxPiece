package nolan.bloxpiece.progression;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Enemy;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.registry.ModEntities;

/**
 * XP BloxPiece donnée quand un mob du mod est tué : système CENTRALISÉ et automatique (formule : XpBalance).
 * <ul>
 *   <li><b>Quels mobs</b> : toute entité HOSTILE (Enemy) enregistrée par BloxPiece (espace de noms « bloxpiece ») : pirates,
 *       bandits, Marines, Humandrills, boss... et tous les futurs mobs, sans rien ajouter.</li>
 *   <li><b>Calcul</b> : PV max et dégâts de BASE du type (attributs par défaut enregistrés avec l'entité ; l'arme tenue n'est
 *       pas comptée : XP stable), rôle (déclaré ci-dessous, sinon déduit des PV : XpBalance.autoRole), coefficient propre
 *       facultatif (mobs qui attaquent à distance, boss sous-estimés...), ou valeur FIXE facultative. Mis en cache par type.</li>
 *   <li><b>À qui</b> : au joueur responsable de la mort (coup fatal, y compris par projectile ; sinon le joueur qui l'a blessé
 *       dans les dernières secondes). UNE seule récompense par mort, au seul responsable : plusieurs joueurs qui frappent le
 *       même mob ne multiplient pas l'XP. Rien si aucun joueur n'est responsable, si le mob disparaît ou est retiré par /kill.
 *       Toujours côté serveur, dans la réserve d'XP habituelle (Progression.addXp).</li>
 * </ul>
 * Pour un nouveau mob : rien à faire. Pour le régler : {@link #ROLES}, {@link #COEFFICIENTS} ou {@link #FIXED}.
 */
public final class MobXp {

    /** Rôle des mobs connus (les autres : déduit de leurs PV). */
    private static final Map<EntityType<?>, XpBalance.Role> ROLES = new HashMap<>();
    /** Coefficient propre (1 par défaut) : ce que la formule ne voit pas. */
    private static final Map<EntityType<?>, Double> COEFFICIENTS = new HashMap<>();
    /** Valeur imposée (remplace le calcul). Vide pour l'instant : à utiliser si un boss est vraiment mal estimé. */
    private static final Map<EntityType<?>, Integer> FIXED = new HashMap<>();
    private static final Map<EntityType<?>, Integer> CACHE = new HashMap<>();

    static {
        ROLES.put(ModEntities.PIRATE_SWORDSMAN, XpBalance.Role.NORMAL);
        ROLES.put(ModEntities.PIRATE_GUNNER, XpBalance.Role.NORMAL);
        ROLES.put(ModEntities.MARINE_SWORDSMAN, XpBalance.Role.NORMAL);
        ROLES.put(ModEntities.MARINE_RIFLEMAN, XpBalance.Role.NORMAL);
        ROLES.put(ModEntities.BANDIT, XpBalance.Role.NORMAL);
        ROLES.put(ModEntities.HUMANDRILL, XpBalance.Role.ELITE);
        ROLES.put(ModEntities.PIRATE_CAPTAIN, XpBalance.Role.CAPTAIN);
        ROLES.put(ModEntities.BANDIT_CHIEF, XpBalance.Role.CAPTAIN);
        ROLES.put(ModEntities.MORGAN, XpBalance.Role.BOSS);
        ROLES.put(ModEntities.ENER, XpBalance.Role.BOSS);
        ROLES.put(ModEntities.GOL_D_ROGER, XpBalance.Role.BOSS);
        // tireurs : leurs dégâts au corps-à-corps sont faibles, mais ils tirent (dégâts des balles non comptés par la formule)
        COEFFICIENTS.put(ModEntities.PIRATE_GUNNER, 1.6);
        COEFFICIENTS.put(ModEntities.MARINE_RIFLEMAN, 1.6);
        // Gol D. Roger : Haki des Rois, Ace et ses attaques spéciales rendent le combat bien plus dur que ses stats
        COEFFICIENTS.put(ModEntities.GOL_D_ROGER, 1.4);
    }

    private MobXp() {
    }

    /** Ce mob rapporte-t-il de l'XP BloxPiece ? (hostile et enregistré par le mod) */
    public static boolean rewards(EntityType<?> type) {
        return BloxPiece.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
    }

    /** XP rapportée par un mob de ce type (0 s'il n'a pas d'attributs). */
    @SuppressWarnings("unchecked")
    public static int xpFor(EntityType<?> type) {
        Integer cached = CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        int xp;
        if (FIXED.containsKey(type)) {
            xp = FIXED.get(type);
        } else if (!DefaultAttributes.hasSupplier(type)) {
            xp = 0;
        } else {
            AttributeSupplier attributes = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            double health = attributes.hasAttribute(Attributes.MAX_HEALTH) ? attributes.getBaseValue(Attributes.MAX_HEALTH) : 0.0;
            double damage = attributes.hasAttribute(Attributes.ATTACK_DAMAGE) ? attributes.getBaseValue(Attributes.ATTACK_DAMAGE) : 0.0;
            XpBalance.Role role = ROLES.getOrDefault(type, XpBalance.autoRole(health));
            xp = XpBalance.mobXp(health, damage, role, COEFFICIENTS.getOrDefault(type, 1.0));
        }
        CACHE.put(type, xp);
        return xp;
    }

    /** Mort d'une entité (ModEvents, une fois par mort) : XP au joueur responsable, s'il y en a un. */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Enemy) || !rewards(entity.getType()) || source.is(DamageTypes.GENERIC_KILL)) {
            return; // pas un mob hostile du mod, ou retiré par /kill
        }
        ServerPlayer killer = responsible(entity, source);
        if (killer != null) {
            int xp = xpFor(entity.getType());
            if (xp > 0) {
                Progression.addXp(killer, xp);
            }
        }
    }

    /** Le joueur qui porte le coup fatal (même par projectile), ou à défaut celui qui l'a blessé dans les dernières secondes. */
    public static ServerPlayer responsible(LivingEntity entity, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player && player != entity) {
            return player;
        }
        if (entity.getLastHurtByMob() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
