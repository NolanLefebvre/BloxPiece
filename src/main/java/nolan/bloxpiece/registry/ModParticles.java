package nolan.bloxpiece.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import nolan.bloxpiece.BloxPiece;

/**
 * Particules personnalisées du mod, pour le fruit du Bouddha et le fruit du Feu (textures dans assets/bloxpiece/textures/particle, définitions dans
 * assets/bloxpiece/particles). Enregistrées des deux côtés : le serveur les envoie (ServerLevel.sendParticles), le client les dessine
 * (ModParticle).
 */
public class ModParticles {

    /** Lueur dorée douce (remplace la poussière dorée). */
    public static final SimpleParticleType BUDDHA_GLOW = register("buddha_glow");
    /** Lueur jaune pâle, presque blanche. */
    public static final SimpleParticleType BUDDHA_GLOW_PALE = register("buddha_glow_pale");
    /** Lueur orangée (relief, bord des fronts d'énergie). */
    public static final SimpleParticleType BUDDHA_GLOW_ORANGE = register("buddha_glow_orange");
    /** Étoile scintillante à 4 branches (remplace END_ROD et ELECTRIC_SPARK). */
    public static final SimpleParticleType BUDDHA_SPARK = register("buddha_spark");
    /** Pétale de lotus doré qui tournoie et s'élève (remplace WAX_ON). */
    public static final SimpleParticleType BUDDHA_PETAL = register("buddha_petal");
    /** Grand éclair de lumière dorée à rayons (remplace FLASH). */
    public static final SimpleParticleType BUDDHA_FLASH = register("buddha_flash");
    /** Nuage de poussière dorée qui gonfle et se dissipe (remplace CLOUD, POOF et la fumée). */
    public static final SimpleParticleType BUDDHA_MIST = register("buddha_mist");

    /** Flamme vive, animée, du jaune au rouge (remplace FLAME pour le fruit du Feu). */
    public static final SimpleParticleType FIRE_FLAME = register("fire_flame");
    /** Braise incandescente qui flotte et scintille (remplace LAVA pour le fruit du Feu). */
    public static final SimpleParticleType FIRE_EMBER = register("fire_ember");
    /** Volute de fumée noire qui gonfle et s'élève (remplace LARGE_SMOKE pour le fruit du Feu). */
    public static final SimpleParticleType FIRE_SMOKE = register("fire_smoke");

    /** Flamme très brève qui épouse un membre du corps Logia (émise côté client, voir LogiaClient). */
    public static final SimpleParticleType FIRE_LIMB = register("fire_limb");

    /** Fruit de la Glace : éclat de cristal de glace qui scintille et retombe. */
    public static final SimpleParticleType ICE_SHARD = register("ice_shard");
    /** Fruit de la Glace : flocon de neige à 6 branches qui tournoie en descendant. */
    public static final SimpleParticleType SNOWFLAKE = register("snowflake");
    /** Fruit de la Glace : brume glacée bleutée qui gonfle et se dissipe. */
    public static final SimpleParticleType FROST_MIST = register("frost_mist");
    /** Fruit de la Glace : lueur froide (blanc-cyan). */
    public static final SimpleParticleType FROST_GLOW = register("frost_glow");

    /** Haki de l'Observation : point lumineux rouge de la traînée qui suit les yeux pendant une esquive (émis côté client). */
    public static final SimpleParticleType HAKI_EYE = register("haki_eye");
    /** Halo rouge autour de la traînée des yeux, et pupille incandescente. */
    public static final SimpleParticleType HAKI_EYE_GLOW = register("haki_eye_glow");

    /** Haki des Rois : éclat d'énergie noir et rouge sombre, projeté hors du corps. */
    public static final SimpleParticleType HAKI_SHARD = register("haki_shard");
    /** Haki des Rois : étincelle de tension rouge vif, très brève. */
    public static final SimpleParticleType HAKI_SPARK = register("haki_spark");
    /** Haki des Rois : petite onde de choc rouge (anneau qui s'élargit et s'efface). */
    public static final SimpleParticleType HAKI_RING = register("haki_ring");
    /** Haki des Rois : brume d'aura noire teintée de rouge (remplace la fumée grise). */
    public static final SimpleParticleType HAKI_MIST = register("haki_mist");

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, BloxPiece.id(name), FabricParticleTypes.simple());
    }

    /** Charge la classe (les champs ci-dessus s'enregistrent alors). */
    public static void initialize() {
    }
}
