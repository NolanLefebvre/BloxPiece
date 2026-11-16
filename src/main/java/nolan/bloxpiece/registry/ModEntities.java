package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.BuddhaPalm;
import nolan.bloxpiece.fx.BuddhaSeal;
import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.fx.IceEagle;
import nolan.bloxpiece.fx.IceSpike;
import nolan.bloxpiece.fx.IceStatue;
import nolan.bloxpiece.fx.IceTrident;
import nolan.bloxpiece.fx.HikenFist;
import nolan.bloxpiece.mob.Pirate;

/**
 * Tous les types d'entités du mod. Une entité enregistrée ici est automatiquement utilisable
 * avec la commande vanilla : /summon bloxpiece:<nom> (ex : /summon bloxpiece:pirate).
 */
public class ModEntities {

    public static final ResourceKey<EntityType<?>> PIRATE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("pirate"));

    public static final EntityType<Pirate> PIRATE = EntityType.Builder.of(Pirate::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)       // même hitbox qu'un joueur / zombie
            .clientTrackingRange(8)
            .build(PIRATE_KEY);

    // Le soleil de Dai Enkai: Entei (entité visuelle, voir EnteiSun) : ne s'invoque pas à la main
    public static final ResourceKey<EntityType<?>> ENTEI_SUN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("entei_sun"));

    public static final EntityType<EnteiSun> ENTEI_SUN = EntityType.Builder.of(EnteiSun::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)        // multipliée par l'attribut SCALE (taille du soleil)
            .fireImmune()
            .clientTrackingRange(10)
            .updateInterval(1)        // position envoyée à chaque tick : le soleil suit la boule de feu sans saccade
            .build(ENTEI_SUN_KEY);

    // Le poing de Hiken : le PROJECTILE de l'attaque (entité personnalisée, voir HikenFist). Ni invocable à la main, ni sauvegardée.
    public static final ResourceKey<EntityType<?>> HIKEN_FIST_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("hiken_fist"));

    public static final EntityType<HikenFist> HIKEN_FIST = EntityType.Builder.<HikenFist>of(HikenFist::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)        // dimensions nominales seulement : la vraie hitbox (celle du poing visible, orientée) est calculée par HikenFist.makeBoundingBox
            .fireImmune()
            .noSave()                 // un poing en vol n'est jamais écrit dans la sauvegarde du monde
            .noSummon()               // pas de /summon : seul Hiken crée un poing
            .clientTrackingRange(10)  // la masse de flammes s'étend à ~30 blocs derrière le poing : 10 chunks de suivi suffisent largement
            .updateInterval(1)        // position et orientation envoyées à chaque tick : le poing avance sans saccade chez tous les clients
            .build(HIKEN_FIST_KEY);

    // La Paume du Bouddha : le PROJECTILE de la deuxième attaque du fruit du Bouddha (voir BuddhaPalm). Ni invocable à la main, ni sauvegardée.
    public static final ResourceKey<EntityType<?>> BUDDHA_PALM_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("buddha_palm"));

    public static final EntityType<BuddhaPalm> BUDDHA_PALM = EntityType.Builder.<BuddhaPalm>of(BuddhaPalm::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)        // dimensions nominales seulement : la vraie hitbox (la main visible) est calculée par BuddhaPalm.makeBoundingBox
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)        // position et orientation envoyées à chaque tick : la main avance sans saccade chez tous les clients
            .build(BUDDHA_PALM_KEY);

    // Le sceau doré au sol de la Paume du Bouddha (entité visuelle, voir BuddhaSeal). Ni invocable à la main, ni sauvegardée.
    public static final ResourceKey<EntityType<?>> BUDDHA_SEAL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("buddha_seal"));

    public static final EntityType<BuddhaSeal> BUDDHA_SEAL = EntityType.Builder.<BuddhaSeal>of(BuddhaSeal::new, MobCategory.MISC)
            .sized(1.0F, 0.1F)        // nominal : le sceau visible (~8 blocs de rayon) est dessiné par le renderer
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)        // il suit le point visé pendant la charge sans saccade
            .build(BUDDHA_SEAL_KEY);

    // Fruit de la Glace : le trident de glace lancé (Partisan). Ni invocable à la main, ni sauvegardé.
    public static final ResourceKey<EntityType<?>> ICE_TRIDENT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("ice_trident"));

    public static final EntityType<IceTrident> ICE_TRIDENT = EntityType.Builder.<IceTrident>of(IceTrident::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(ICE_TRIDENT_KEY);

    // Fruit de la Glace : l'aigle de glace (Pheasant Peck). Ni invocable à la main, ni sauvegardé.
    public static final ResourceKey<EntityType<?>> ICE_EAGLE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("ice_eagle"));

    public static final EntityType<IceEagle> ICE_EAGLE = EntityType.Builder.<IceEagle>of(IceEagle::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(ICE_EAGLE_KEY);

    // Fruit de la Glace : un pic de glace qui jaillit du sol (visuel). Ni invocable à la main, ni sauvegardé.
    public static final ResourceKey<EntityType<?>> ICE_SPIKE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("ice_spike"));

    public static final EntityType<IceSpike> ICE_SPIKE = EntityType.Builder.<IceSpike>of(IceSpike::new, MobCategory.MISC)
            .sized(0.5F, 1.0F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(ICE_SPIKE_KEY);

    // Fruit de la Glace : la statue de glace d'une entité gelée (visuel). Ni invocable à la main, ni sauvegardé.
    public static final ResourceKey<EntityType<?>> ICE_STATUE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("ice_statue"));

    public static final EntityType<IceStatue> ICE_STATUE = EntityType.Builder.<IceStatue>of(IceStatue::new, MobCategory.MISC)
            .sized(0.6F, 1.8F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(ICE_STATUE_KEY);

    public static void initialize() {
        // Un nouveau mob : une ligne ici (type + attributs issus de ses stats)
        register(PIRATE_KEY, PIRATE, Pirate.STATS.toAttributes());
        register(ENTEI_SUN_KEY, ENTEI_SUN, Mob.createMobAttributes());
        // Le poing est un projectile (pas un être vivant) : pas d'attributs à enregistrer
        Registry.register(BuiltInRegistries.ENTITY_TYPE, HIKEN_FIST_KEY, HIKEN_FIST);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, BUDDHA_PALM_KEY, BUDDHA_PALM);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, BUDDHA_SEAL_KEY, BUDDHA_SEAL);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_TRIDENT_KEY, ICE_TRIDENT);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_EAGLE_KEY, ICE_EAGLE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_SPIKE_KEY, ICE_SPIKE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_STATUE_KEY, ICE_STATUE);
    }

    private static <T extends LivingEntity> void register(
            ResourceKey<EntityType<?>> key, EntityType<T> type, AttributeSupplier.Builder attributes) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        FabricDefaultAttributeRegistry.register(type, attributes);
    }
}
