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
import nolan.bloxpiece.fx.EnteiSun;
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

    public static void initialize() {
        // Un nouveau mob : une ligne ici (type + attributs issus de ses stats)
        register(PIRATE_KEY, PIRATE, Pirate.STATS.toAttributes());
        register(ENTEI_SUN_KEY, ENTEI_SUN, Mob.createMobAttributes());
    }

    private static <T extends LivingEntity> void register(
            ResourceKey<EntityType<?>> key, EntityType<T> type, AttributeSupplier.Builder attributes) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        FabricDefaultAttributeRegistry.register(type, attributes);
    }
}
