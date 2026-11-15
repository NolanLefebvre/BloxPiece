package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import nolan.bloxpiece.BloxPiece;
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

    public static void initialize() {
        // Un nouveau mob : une ligne ici (type + attributs issus de ses stats)
        register(PIRATE_KEY, PIRATE, Pirate.STATS.toAttributes());
    }

    private static <T extends LivingEntity> void register(
            ResourceKey<EntityType<?>> key, EntityType<T> type, AttributeSupplier.Builder attributes) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        FabricDefaultAttributeRegistry.register(type, attributes);
    }
}
