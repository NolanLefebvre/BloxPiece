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
import net.minecraft.world.entity.ai.attributes.Attributes;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.BuddhaPalm;
import nolan.bloxpiece.fx.BuddhaSeal;
import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.fx.IceEagle;
import nolan.bloxpiece.fx.IceSpike;
import nolan.bloxpiece.fx.IceStatue;
import nolan.bloxpiece.fx.IceTrident;
import nolan.bloxpiece.fx.HikenFist;
import nolan.bloxpiece.fx.Meteor;
import nolan.bloxpiece.mob.PirateCaptain;
import nolan.bloxpiece.mob.PirateGunner;
import nolan.bloxpiece.mob.PirateSwordsman;
import nolan.bloxpiece.fx.PirateBullet;
import nolan.bloxpiece.fx.QuakeFx;

/**
 * Tous les types d'entités du mod. Une entité enregistrée ici est automatiquement utilisable
 * avec la commande vanilla : /summon bloxpiece:<nom> (ex : /summon bloxpiece:pirate_swordsman).
 */
public class ModEntities {

    // PNJ : le Tavernier du village de départ (invulnérable, immobile, dialogue au clic droit)
    public static final ResourceKey<EntityType<?>> TAVERN_KEEPER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("tavern_keeper"));

    public static final EntityType<nolan.bloxpiece.mob.TavernKeeper> TAVERN_KEEPER =
            EntityType.Builder.of(nolan.bloxpiece.mob.TavernKeeper::new, MobCategory.CREATURE)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(TAVERN_KEEPER_KEY);

    // PNJ : Silvers Rayleigh, devant la taverne du village de départ (invulnérable, immobile, dialogue au clic droit ; futures quêtes)
    public static final ResourceKey<EntityType<?>> SILVERS_RAYLEIGH_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("silvers_rayleigh"));

    public static final EntityType<nolan.bloxpiece.mob.Rayleigh> SILVERS_RAYLEIGH =
            EntityType.Builder.of(nolan.bloxpiece.mob.Rayleigh::new, MobCategory.CREATURE)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(10)
            .build(SILVERS_RAYLEIGH_KEY);

    // Pirates armés (épéiste, tireur, capitaine). L'ancien pirate de base sans équipement a été supprimé.
    public static final ResourceKey<EntityType<?>> PIRATE_SWORDSMAN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("pirate_swordsman"));

    public static final EntityType<PirateSwordsman> PIRATE_SWORDSMAN = EntityType.Builder.of(PirateSwordsman::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(PIRATE_SWORDSMAN_KEY);

    public static final ResourceKey<EntityType<?>> PIRATE_GUNNER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("pirate_gunner"));

    public static final EntityType<PirateGunner> PIRATE_GUNNER = EntityType.Builder.of(PirateGunner::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(PIRATE_GUNNER_KEY);

    // Capitaine pirate : seulement dans la cabine du navire pirate (pas d'apparition naturelle)
    public static final ResourceKey<EntityType<?>> PIRATE_CAPTAIN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("pirate_captain"));

    public static final EntityType<PirateCaptain> PIRATE_CAPTAIN = EntityType.Builder.of(PirateCaptain::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(PIRATE_CAPTAIN_KEY);

    // Gol D. Roger, le Roi des Pirates : invoqué par commande (pas d'apparition naturelle). Hitbox de base ; échelle 1,1 (SCALE)
    public static final ResourceKey<EntityType<?>> GOL_D_ROGER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("gol_d_roger"));

    public static final EntityType<nolan.bloxpiece.mob.GolDRoger> GOL_D_ROGER =
            EntityType.Builder.of(nolan.bloxpiece.mob.GolDRoger::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(10)
            .build(GOL_D_ROGER_KEY);

    // Ener, le « dieu » de la foudre : invoqué par commande (pas d'apparition naturelle). Hitbox de base ; échelle 1,15 (SCALE)
    public static final ResourceKey<EntityType<?>> ENER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("ener"));

    public static final EntityType<nolan.bloxpiece.mob.Ener> ENER =
            EntityType.Builder.of(nolan.bloxpiece.mob.Ener::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(10)
            .build(ENER_KEY);

    // Morgan « main de hache », capitaine de la Marine : 3 blocs de haut (modèle conçu à cette taille), invoqué par commande
    public static final ResourceKey<EntityType<?>> MORGAN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("morgan"));

    public static final EntityType<nolan.bloxpiece.mob.Morgan> MORGAN =
            EntityType.Builder.of(nolan.bloxpiece.mob.Morgan::new, MobCategory.MONSTER)
            .sized(1.4F, 3.0F)
            .clientTrackingRange(10)
            .build(MORGAN_KEY);

    // Impact du coup de hache au sol de Morgan (effet visuel propre à Morgan, voir MorganQuake). Ni sauvegardé, ni invocable.
    public static final ResourceKey<EntityType<?>> MORGAN_QUAKE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("morgan_quake"));

    public static final EntityType<nolan.bloxpiece.fx.MorganQuake> MORGAN_QUAKE =
            EntityType.Builder.<nolan.bloxpiece.fx.MorganQuake>of(nolan.bloxpiece.fx.MorganQuake::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(20)
            .build(MORGAN_QUAKE_KEY);

    // Humandrills (île de Kuraigana) : babouins sabreurs d'environ 4 blocs, 1 à 3 épées chacun ; invoqués par commande
    public static final ResourceKey<EntityType<?>> HUMANDRILL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("humandrill"));

    public static final EntityType<nolan.bloxpiece.mob.Humandrill> HUMANDRILL =
            EntityType.Builder.of(nolan.bloxpiece.mob.Humandrill::new, MobCategory.MONSTER)
            .sized(1.75F, 4.0F)
            .clientTrackingRange(8)
            .build(HUMANDRILL_KEY);

    // Bandits du camp de bandits et leur chef (un par camp) : posés par la structure du camp, invocables aussi
    public static final ResourceKey<EntityType<?>> BANDIT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("bandit"));

    public static final EntityType<nolan.bloxpiece.mob.Bandit> BANDIT =
            EntityType.Builder.of(nolan.bloxpiece.mob.Bandit::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(BANDIT_KEY);

    public static final ResourceKey<EntityType<?>> BANDIT_CHIEF_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("bandit_chief"));

    public static final EntityType<nolan.bloxpiece.mob.BanditChief> BANDIT_CHIEF =
            EntityType.Builder.of(nolan.bloxpiece.mob.BanditChief::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(BANDIT_CHIEF_KEY);

    // Marines (Épéiste, Fusilier) : neutres envers les joueurs, ennemis des pirates
    public static final ResourceKey<EntityType<?>> MARINE_SWORDSMAN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("marine_swordsman"));

    public static final EntityType<nolan.bloxpiece.mob.MarineSwordsman> MARINE_SWORDSMAN =
            EntityType.Builder.of(nolan.bloxpiece.mob.MarineSwordsman::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(MARINE_SWORDSMAN_KEY);

    public static final ResourceKey<EntityType<?>> MARINE_RIFLEMAN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("marine_rifleman"));

    public static final EntityType<nolan.bloxpiece.mob.MarineRifleman> MARINE_RIFLEMAN =
            EntityType.Builder.of(nolan.bloxpiece.mob.MarineRifleman::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(8)
            .build(MARINE_RIFLEMAN_KEY);

    // Balle de pistolet à silex (Pirate Tireur, joueur) : ni invocable à la main, ni sauvegardée
    public static final ResourceKey<EntityType<?>> PIRATE_BULLET_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("pirate_bullet"));

    public static final EntityType<PirateBullet> PIRATE_BULLET = EntityType.Builder.<PirateBullet>of(PirateBullet::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .noSave()
            .noSummon()
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(PIRATE_BULLET_KEY);

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

    // Un météore de la Pluie de météores : boule de feu vanilla avec son propre type (modèle 3D côté client, voir Meteor).
    // Même hitbox que la boule de feu vanilla (1x1). Ni invocable à la main, ni sauvegardé.
    public static final ResourceKey<EntityType<?>> METEOR_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("meteor"));

    public static final EntityType<Meteor> METEOR = EntityType.Builder.<Meteor>of(Meteor::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .fireImmune()             // pas de flammes vanilla dessinées par-dessus le modèle
            .noSave()
            .noSummon()
            .clientTrackingRange(10)  // les météores tombent de ~45 blocs : on les voit de loin
            .updateInterval(1)        // position envoyée à chaque tick : chute fluide chez tous les clients
            .build(METEOR_KEY);

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

    // Gura Gura no Mi : fissures, sphères et ondes de choc 3D (visuel). Ni invocable à la main, ni sauvegardé.
    public static final ResourceKey<EntityType<?>> QUAKE_FX_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("quake_fx"));

    public static final EntityType<QuakeFx> QUAKE_FX = EntityType.Builder.<QuakeFx>of(QuakeFx::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(QUAKE_FX_KEY);

    // Haki des Rois : éclairs 3D rouge et noir (visuel). Ni invocable à la main, ni sauvegardé.
    // Goro Goro no Mi : un éclair bleu (visuel, voir GoroBolt). Ni sauvegardé, ni invocable.
    public static final ResourceKey<EntityType<?>> GORO_BOLT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("goro_bolt"));

    public static final EntityType<nolan.bloxpiece.fx.GoroBolt> GORO_BOLT =
            EntityType.Builder.<nolan.bloxpiece.fx.GoroBolt>of(nolan.bloxpiece.fx.GoroBolt::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(20)
            .build(GORO_BOLT_KEY);

    // Goro Goro no Mi : nuage d'orage (Mamaragan, El Thor ; visuel, voir GoroCloud). Ni sauvegardé, ni invocable.
    public static final ResourceKey<EntityType<?>> GORO_CLOUD_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("goro_cloud"));

    public static final EntityType<nolan.bloxpiece.fx.GoroCloud> GORO_CLOUD =
            EntityType.Builder.<nolan.bloxpiece.fx.GoroCloud>of(nolan.bloxpiece.fx.GoroCloud::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(GORO_CLOUD_KEY);

    // Goro Goro no Mi : sphère électrique du Raigo (visuel, voir GoroOrb). Ni sauvegardé, ni invocable.
    public static final ResourceKey<EntityType<?>> GORO_ORB_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("goro_orb"));

    public static final EntityType<nolan.bloxpiece.fx.GoroOrb> GORO_ORB =
            EntityType.Builder.<nolan.bloxpiece.fx.GoroOrb>of(nolan.bloxpiece.fx.GoroOrb::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(GORO_ORB_KEY);

    // Goro Goro no Mi : onde de choc au sol (visuel, voir GoroWave). Ni sauvegardée, ni invocable.
    public static final ResourceKey<EntityType<?>> GORO_WAVE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("goro_wave"));

    public static final EntityType<nolan.bloxpiece.fx.GoroWave> GORO_WAVE =
            EntityType.Builder.<nolan.bloxpiece.fx.GoroWave>of(nolan.bloxpiece.fx.GoroWave::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(20)
            .build(GORO_WAVE_KEY);

    // Goro Goro no Mi : colonne de foudre géante d'El Thor (visuel, voir GoroColumn). Ni sauvegardée, ni invocable.
    public static final ResourceKey<EntityType<?>> GORO_COLUMN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("goro_column"));

    public static final EntityType<nolan.bloxpiece.fx.GoroColumn> GORO_COLUMN =
            EntityType.Builder.<nolan.bloxpiece.fx.GoroColumn>of(nolan.bloxpiece.fx.GoroColumn::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(20)
            .build(GORO_COLUMN_KEY);

    // Goro Goro no Mi : électrisation d'une cible par le Mamaragan (visuel, voir GoroShock). Ni sauvegardée, ni invocable.
    public static final ResourceKey<EntityType<?>> GORO_SHOCK_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("goro_shock"));

    public static final EntityType<nolan.bloxpiece.fx.GoroShock> GORO_SHOCK =
            EntityType.Builder.<nolan.bloxpiece.fx.GoroShock>of(nolan.bloxpiece.fx.GoroShock::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(GORO_SHOCK_KEY);

    // Race Cyborg : cyborg_cannonball (entité d'effet, ni sauvegardée ni invocable)
    public static final ResourceKey<EntityType<?>> CYBORG_CANNONBALL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("cyborg_cannonball"));

    public static final EntityType<nolan.bloxpiece.fx.CyborgCannonball> CYBORG_CANNONBALL =
            EntityType.Builder.<nolan.bloxpiece.fx.CyborgCannonball>of(nolan.bloxpiece.fx.CyborgCannonball::new, MobCategory.MISC)
            .sized(0.6F, 0.6F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(CYBORG_CANNONBALL_KEY);

    // Race Cyborg : cyborg_shield (entité d'effet, ni sauvegardée ni invocable)
    public static final ResourceKey<EntityType<?>> CYBORG_SHIELD_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("cyborg_shield"));

    public static final EntityType<nolan.bloxpiece.fx.CyborgShield> CYBORG_SHIELD =
            EntityType.Builder.<nolan.bloxpiece.fx.CyborgShield>of(nolan.bloxpiece.fx.CyborgShield::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(CYBORG_SHIELD_KEY);

    // Race Cyborg : cyborg_beam (entité d'effet, ni sauvegardée ni invocable)
    public static final ResourceKey<EntityType<?>> CYBORG_BEAM_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("cyborg_beam"));

    public static final EntityType<nolan.bloxpiece.fx.CyborgBeam> CYBORG_BEAM =
            EntityType.Builder.<nolan.bloxpiece.fx.CyborgBeam>of(nolan.bloxpiece.fx.CyborgBeam::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(16)
            .updateInterval(1)
            .build(CYBORG_BEAM_KEY);

    // Race Humaine : human_fx (effets des quatre attaques, entité d'effet ni sauvegardée ni invocable)
    public static final ResourceKey<EntityType<?>> HUMAN_FX_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("human_fx"));

    public static final EntityType<nolan.bloxpiece.fx.HumanFx> HUMAN_FX =
            EntityType.Builder.<nolan.bloxpiece.fx.HumanFx>of(nolan.bloxpiece.fx.HumanFx::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(16)
            .updateInterval(1)
            .build(HUMAN_FX_KEY);

    // Race Minks : minks_fx (effets des quatre attaques, entité d'effet ni sauvegardée ni invocable)
    public static final ResourceKey<EntityType<?>> MINKS_FX_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("minks_fx"));

    public static final EntityType<nolan.bloxpiece.fx.MinksFx> MINKS_FX =
            EntityType.Builder.<nolan.bloxpiece.fx.MinksFx>of(nolan.bloxpiece.fx.MinksFx::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(16)
            .updateInterval(1)
            .build(MINKS_FX_KEY);

    public static final ResourceKey<EntityType<?>> LUNARIAN_FX_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("lunarian_fx"));

    /** Effets 3D (purement visuels) des attaques de la race Lunarian : plumes de feu, explosions, traînées, tornade de feu. */
    public static final EntityType<nolan.bloxpiece.fx.LunarianFx> LUNARIAN_FX =
            EntityType.Builder.<nolan.bloxpiece.fx.LunarianFx>of(nolan.bloxpiece.fx.LunarianFx::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(16)
            .updateInterval(1)
            .build(LUNARIAN_FX_KEY);

    public static final ResourceKey<EntityType<?>> FISHMAN_FX_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("fishman_fx"));

    /** Effets 3D (purement visuels) des attaques de la race Fishman : onde, gouttes, vagues, spirale, explosion d'eau. */
    public static final EntityType<nolan.bloxpiece.fx.FishmanFx> FISHMAN_FX =
            EntityType.Builder.<nolan.bloxpiece.fx.FishmanFx>of(nolan.bloxpiece.fx.FishmanFx::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(16)
            .updateInterval(1)
            .build(FISHMAN_FX_KEY);

    public static final ResourceKey<EntityType<?>> HAKI_BOLT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("haki_bolt"));

    public static final EntityType<nolan.bloxpiece.fx.HakiBolt> HAKI_BOLT =
            EntityType.Builder.<nolan.bloxpiece.fx.HakiBolt>of(nolan.bloxpiece.fx.HakiBolt::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(HAKI_BOLT_KEY);

    // La Lame dévastatrice du Haki des Rois (attaque spéciale de l'épée Ace) : la vraie collision est le croissant du modèle
    // (AceSlashShape), la taille ici n'est que nominale. Ni sauvegardée, ni invocable.
    public static final ResourceKey<EntityType<?>> ACE_SLASH_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("ace_slash"));

    public static final EntityType<nolan.bloxpiece.fx.AceSlash> ACE_SLASH =
            EntityType.Builder.<nolan.bloxpiece.fx.AceSlash>of(nolan.bloxpiece.fx.AceSlash::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(1)
            .build(ACE_SLASH_KEY);

    // Activation du Haki des Rois : la séquence visuelle (onde de choc, voir ConquerorShockwave). Ni sauvegardée, ni invocable.
    public static final ResourceKey<EntityType<?>> CONQUEROR_SHOCKWAVE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("conqueror_shockwave"));

    public static final EntityType<nolan.bloxpiece.fx.ConquerorShockwave> CONQUEROR_SHOCKWAVE =
            EntityType.Builder.<nolan.bloxpiece.fx.ConquerorShockwave>of(nolan.bloxpiece.fx.ConquerorShockwave::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(20)
            .build(CONQUEROR_SHOCKWAVE_KEY);

    // Carapace vide du Kame Kame no Mi, posée dans le monde pendant que son propriétaire est dans sa poche : SAUVEGARDÉE avec le
    // monde (elle l'attend), jamais invoquée à la main.
    public static final ResourceKey<EntityType<?>> KAME_SHELL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, BloxPiece.id("kame_shell"));

    public static final EntityType<nolan.bloxpiece.fx.KameShellEntity> KAME_SHELL =
            EntityType.Builder.<nolan.bloxpiece.fx.KameShellEntity>of(nolan.bloxpiece.fx.KameShellEntity::new, MobCategory.MISC)
            .sized(0.9F, 0.5F)
            .noSummon()
            .clientTrackingRange(8)
            .updateInterval(10)
            .build(KAME_SHELL_KEY);

    public static void initialize() {
        // Un nouveau mob : une ligne ici (type + attributs issus de ses stats)
        register(TAVERN_KEEPER_KEY, TAVERN_KEEPER, nolan.bloxpiece.mob.TavernKeeper.attributes());
        register(SILVERS_RAYLEIGH_KEY, SILVERS_RAYLEIGH, nolan.bloxpiece.mob.Rayleigh.attributes());
        register(PIRATE_SWORDSMAN_KEY, PIRATE_SWORDSMAN, PirateSwordsman.STATS.toAttributes());
        register(PIRATE_GUNNER_KEY, PIRATE_GUNNER, PirateGunner.STATS.toAttributes());
        register(PIRATE_CAPTAIN_KEY, PIRATE_CAPTAIN, PirateCaptain.STATS.toAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)); // difficile à repousser
        register(MORGAN_KEY, MORGAN, nolan.bloxpiece.mob.Morgan.STATS.toAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)   // massif
                .add(Attributes.ATTACK_KNOCKBACK, 1.2));     // le coup de hache repousse la cible
        register(HUMANDRILL_KEY, HUMANDRILL, nolan.bloxpiece.mob.Humandrill.STATS.toAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25));
        register(BANDIT_KEY, BANDIT, nolan.bloxpiece.mob.Bandit.STATS.toAttributes());
        register(BANDIT_CHIEF_KEY, BANDIT_CHIEF, nolan.bloxpiece.mob.BanditChief.STATS.toAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)   // dur à repousser
                .add(Attributes.SCALE, 1.1));                // un peu plus grand que ses hommes
        register(MARINE_SWORDSMAN_KEY, MARINE_SWORDSMAN, nolan.bloxpiece.mob.MarineSwordsman.STATS.toAttributes());
        register(MARINE_RIFLEMAN_KEY, MARINE_RIFLEMAN, nolan.bloxpiece.mob.MarineRifleman.STATS.toAttributes());
        register(GOL_D_ROGER_KEY, GOL_D_ROGER, nolan.bloxpiece.mob.GolDRoger.STATS.toAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)   // inébranlable
                .add(Attributes.SCALE, 1.1));                // un peu plus grand que les autres (hitbox et rendu)
        register(ENER_KEY, ENER, nolan.bloxpiece.mob.Ener.STATS.toAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)   // difficile à repousser
                .add(Attributes.SCALE, 1.15));               // grand et élancé (hitbox et rendu)
        Registry.register(BuiltInRegistries.ENTITY_TYPE, PIRATE_BULLET_KEY, PIRATE_BULLET);
        register(ENTEI_SUN_KEY, ENTEI_SUN, Mob.createMobAttributes());
        // Le poing est un projectile (pas un être vivant) : pas d'attributs à enregistrer
        Registry.register(BuiltInRegistries.ENTITY_TYPE, HIKEN_FIST_KEY, HIKEN_FIST);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, METEOR_KEY, METEOR);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, BUDDHA_PALM_KEY, BUDDHA_PALM);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, BUDDHA_SEAL_KEY, BUDDHA_SEAL);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_TRIDENT_KEY, ICE_TRIDENT);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_EAGLE_KEY, ICE_EAGLE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_SPIKE_KEY, ICE_SPIKE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ICE_STATUE_KEY, ICE_STATUE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, QUAKE_FX_KEY, QUAKE_FX);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, HAKI_BOLT_KEY, HAKI_BOLT);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ACE_SLASH_KEY, ACE_SLASH);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CONQUEROR_SHOCKWAVE_KEY, CONQUEROR_SHOCKWAVE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, MORGAN_QUAKE_KEY, MORGAN_QUAKE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, GORO_BOLT_KEY, GORO_BOLT);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, GORO_CLOUD_KEY, GORO_CLOUD);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, GORO_ORB_KEY, GORO_ORB);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, GORO_WAVE_KEY, GORO_WAVE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, GORO_COLUMN_KEY, GORO_COLUMN);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, GORO_SHOCK_KEY, GORO_SHOCK);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, KAME_SHELL_KEY, KAME_SHELL);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CYBORG_CANNONBALL_KEY, CYBORG_CANNONBALL);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CYBORG_SHIELD_KEY, CYBORG_SHIELD);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CYBORG_BEAM_KEY, CYBORG_BEAM);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, HUMAN_FX_KEY, HUMAN_FX);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, MINKS_FX_KEY, MINKS_FX);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, FISHMAN_FX_KEY, FISHMAN_FX);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, LUNARIAN_FX_KEY, LUNARIAN_FX);
    }

    private static <T extends LivingEntity> void register(
            ResourceKey<EntityType<?>> key, EntityType<T> type, AttributeSupplier.Builder attributes) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        FabricDefaultAttributeRegistry.register(type, attributes);
    }
}
