package nolan.bloxpiece.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import nolan.bloxpiece.client.entity.BuddhaPalmModel;
import nolan.bloxpiece.client.entity.BuddhaPalmRenderer;
import nolan.bloxpiece.client.entity.BuddhaSealModel;
import nolan.bloxpiece.client.entity.BuddhaSealRenderer;
import nolan.bloxpiece.client.entity.EnteiSunModel;
import nolan.bloxpiece.client.entity.IceModels;
import nolan.bloxpiece.client.entity.IceRenderers;
import nolan.bloxpiece.client.entity.EnteiSunRenderer;
import nolan.bloxpiece.client.entity.HikenFistModel;
import nolan.bloxpiece.client.entity.HandcuffsLayer;
import nolan.bloxpiece.client.handcuffs.HandcuffsClient;
import nolan.bloxpiece.client.entity.HikenFistRenderer;
import nolan.bloxpiece.client.entity.MeteorModel;
import nolan.bloxpiece.client.entity.MeteorRenderer;
import nolan.bloxpiece.client.entity.ArmedPirateRenderer;
import nolan.bloxpiece.client.entity.GuraModels;
import nolan.bloxpiece.client.entity.QuakeFxRenderer;
import nolan.bloxpiece.client.entity.PirateGearModels;
import nolan.bloxpiece.client.entity.PirateBulletRenderer;
import nolan.bloxpiece.client.hud.AbilityHud;
import nolan.bloxpiece.client.particle.ModParticle;
import nolan.bloxpiece.client.hud.ClientCooldowns;
import nolan.bloxpiece.client.hud.HealthHud;
import nolan.bloxpiece.client.screen.CharacterScreen;
import nolan.bloxpiece.client.screen.RouletteScreen;
import nolan.bloxpiece.network.CooldownPayload;
import nolan.bloxpiece.ability.IceSaberAbility;
import nolan.bloxpiece.network.SaberStrikePayload;
import nolan.bloxpiece.network.OpenRoulettePayload;
import nolan.bloxpiece.network.ShakePayload;
import nolan.bloxpiece.network.UseAbilityPayload;
import nolan.bloxpiece.registry.ModAttachments;
import nolan.bloxpiece.registry.ModEntities;
import nolan.bloxpiece.registry.ModParticles;

public class BloxPieceClient implements ClientModInitializer {

    // Les mêmes touches pour tous les fruits : c'est le serveur qui décide quelle attaque lancer.
    // Pour changer les touches par défaut, modifie les SDL_SCANCODE_* (le joueur peut aussi les changer dans Options > Contrôles).
    private static final KeyMapping[] ABILITY_KEYS = {
            registerKey("key.bloxpiece.ability_1", SDLScancode.SDL_SCANCODE_Z),
            registerKey("key.bloxpiece.ability_2", SDLScancode.SDL_SCANCODE_X),
            registerKey("key.bloxpiece.ability_3", SDLScancode.SDL_SCANCODE_C),
            registerKey("key.bloxpiece.ability_4", SDLScancode.SDL_SCANCODE_V)
    };

    /** Touches d'attaque enfoncées au tick précédent : pour prévenir le serveur quand l'une est relâchée (attaques à charge). */
    private static final boolean[] KEY_WAS_DOWN = new boolean[ABILITY_KEYS.length];

    /** Touche d'attaque enfoncée au tick précédent (pour détecter un nouveau coup avec le trident de glace). */
    private static boolean attackWasDown;

    // Menu du personnage (modifiable dans Options > Contrôles)
    private static final KeyMapping CHARACTER_KEY = registerKey("key.bloxpiece.character", SDLScancode.SDL_SCANCODE_G);

    /** Touche de l'attaque de ce slot (le HUD en affiche le nom), ou null si ce slot n'a pas de touche. */
    public static KeyMapping abilityKey(int slot) {
        return slot >= 0 && slot < ABILITY_KEYS.length ? ABILITY_KEYS[slot] : null;
    }

    private static KeyMapping registerKey(String translationKey, int scancode) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(translationKey, InputConstants.Type.KEYBOARD, scancode, KeyMapping.Category.MISC));
    }

    @Override
    public void onInitializeClient() {
        BloxXpTooltip.register(); // infobulle des objets d'XP BloxPiece
        // Apparence des mobs du mod (un renderer par type d'entité)
        // Pirates armés (arme 3D en main) et balle de pistolet 3D
        // (4 skins chacun, équipement 3D : bandana + fourreau / tricorne + bandoulière)
        // Tavernier du village de départ
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.entity.TavernKeeperModel.LAYER,
                nolan.bloxpiece.client.entity.TavernKeeperModel::createLayer);
        EntityRendererRegistry.register(ModEntities.TAVERN_KEEPER, nolan.bloxpiece.client.entity.TavernKeeperRenderer::new);
        ModelLayerRegistry.registerModelLayer(PirateGearModels.SWORDSMAN, PirateGearModels::swordsman);
        ModelLayerRegistry.registerModelLayer(PirateGearModels.GUNNER, PirateGearModels::gunner);
        EntityRendererRegistry.register(ModEntities.PIRATE_SWORDSMAN,
                context -> new ArmedPirateRenderer(context, PirateGearModels.SWORDSMAN, "pirate_swordsman", 4));
        EntityRendererRegistry.register(ModEntities.PIRATE_GUNNER,
                context -> new ArmedPirateRenderer(context, PirateGearModels.GUNNER, "pirate_gunner", 4));
        ModelLayerRegistry.registerModelLayer(PirateGearModels.CAPTAIN, PirateGearModels::captain);
        EntityRendererRegistry.register(ModEntities.PIRATE_CAPTAIN,
                context -> new ArmedPirateRenderer(context, PirateGearModels.CAPTAIN, "pirate_captain", 2));
        ModelLayerRegistry.registerModelLayer(PirateBulletRenderer.LAYER, PirateBulletRenderer.BulletModel::createLayer);
        EntityRendererRegistry.register(ModEntities.PIRATE_BULLET, PirateBulletRenderer::new);
        // Le calque du modèle doit être enregistré AVANT le renderer qui s'en sert
        ModelLayerRegistry.registerModelLayer(EnteiSunModel.LAYER, EnteiSunModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.ENTEI_SUN, EnteiSunRenderer::new);
        // Le poing de feu de Hiken (même principe : calque du modèle d'abord, renderer ensuite)
        ModelLayerRegistry.registerModelLayer(HikenFistModel.LAYER, HikenFistModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.HIKEN_FIST, HikenFistRenderer::new);
        // Les météores de la Pluie de météores (deux calques : noyau rocheux opaque + flammes translucides)
        ModelLayerRegistry.registerModelLayer(MeteorModel.CORE, MeteorModel::createCoreLayer);
        ModelLayerRegistry.registerModelLayer(MeteorModel.FLAMES, MeteorModel::createFlameLayer);
        EntityRendererRegistry.register(ModEntities.METEOR, MeteorRenderer::new);
        // La Paume du Bouddha (même principe)
        ModelLayerRegistry.registerModelLayer(BuddhaPalmModel.LAYER, BuddhaPalmModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.BUDDHA_PALM, BuddhaPalmRenderer::new);
        // Le sceau doré au sol de la Paume du Bouddha
        ModelLayerRegistry.registerModelLayer(BuddhaSealModel.LAYER, BuddhaSealModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.BUDDHA_SEAL, BuddhaSealRenderer::new);

        // Particules personnalisées du fruit du Bouddha (sprites : assets/bloxpiece/particles + textures/particle)
        ParticleProviderRegistry particles = ParticleProviderRegistry.getInstance();
        particles.register(ModParticles.BUDDHA_GLOW, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.GLOW));
        particles.register(ModParticles.BUDDHA_GLOW_PALE, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.GLOW_PALE));
        particles.register(ModParticles.BUDDHA_GLOW_ORANGE, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.GLOW_ORANGE));
        particles.register(ModParticles.BUDDHA_SPARK, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.SPARK));
        particles.register(ModParticles.BUDDHA_PETAL, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.PETAL));
        particles.register(ModParticles.BUDDHA_FLASH, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FLASH));
        particles.register(ModParticles.BUDDHA_MIST, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.MIST));
        // Particules personnalisées du fruit du Feu
        particles.register(ModParticles.FIRE_FLAME, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FIRE_FLAME));
        particles.register(ModParticles.FIRE_EMBER, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FIRE_EMBER));
        particles.register(ModParticles.FIRE_SMOKE, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FIRE_SMOKE));
        particles.register(ModParticles.FIRE_LIMB, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FIRE_LIMB));
        // Particules personnalisées du fruit de la Glace
        particles.register(ModParticles.ICE_SHARD, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.ICE_SHARD));
        particles.register(ModParticles.SNOWFLAKE, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.SNOWFLAKE));
        particles.register(ModParticles.FROST_MIST, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FROST_MIST));
        particles.register(ModParticles.FROST_GLOW, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.FROST_GLOW));

        // Entités du fruit de la Glace (calques des modèles d'abord, renderers ensuite)
        ModelLayerRegistry.registerModelLayer(IceModels.TridentModel.LAYER, IceModels.TridentModel::createLayer);
        ModelLayerRegistry.registerModelLayer(IceModels.EagleModel.LAYER, IceModels.EagleModel::createLayer);
        ModelLayerRegistry.registerModelLayer(IceModels.SpikeModel.LAYER, IceModels.SpikeModel::createLayer);
        ModelLayerRegistry.registerModelLayer(IceModels.StatueModel.LAYER, IceModels.StatueModel::createLayer);
        EntityRendererRegistry.register(ModEntities.ICE_TRIDENT, IceRenderers.Trident::new);
        EntityRendererRegistry.register(ModEntities.ICE_EAGLE, IceRenderers.Eagle::new);
        EntityRendererRegistry.register(ModEntities.ICE_SPIKE, IceRenderers.Spike::new);
        EntityRendererRegistry.register(ModEntities.ICE_STATUE, IceRenderers.Statue::new);
        // Gura Gura no Mi : fissures, sphères et ondes de choc sismiques 3D
        ModelLayerRegistry.registerModelLayer(GuraModels.CrackDiscModel.LAYER, GuraModels.CrackDiscModel::createLayer);
        ModelLayerRegistry.registerModelLayer(GuraModels.CrackOrbModel.LAYER, GuraModels.CrackOrbModel::createLayer);
        ModelLayerRegistry.registerModelLayer(GuraModels.CrackOrbModel.SHELL_LAYER, GuraModels.CrackOrbModel::createShellLayer);
        ModelLayerRegistry.registerModelLayer(GuraModels.GroundCrackModel.LAYER, GuraModels.GroundCrackModel::createLayer);
        ModelLayerRegistry.registerModelLayer(GuraModels.TsunamiModel.LAYER, GuraModels.TsunamiModel::createLayer);
        EntityRendererRegistry.register(ModEntities.QUAKE_FX, QuakeFxRenderer::new);

        // Menottes en Granite Marin : modèle des bracelets portés, cadenas sur la hotbar, inventaire verrouillé
        ModelLayerRegistry.registerModelLayer(HandcuffsLayer.LAYER, HandcuffsLayer::createLayer);
        HandcuffsClient.register();
        ClientTickEvents.START_CLIENT_TICK.register(HandcuffsClient::tick);

        // HUD des 4 attaques à côté de la hotbar
        AbilityHud.register();
        // Barre de vie à une seule rangée de cœurs colorés par palier de 20 PV (et barre d'armure juste au-dessus)
        HealthHud.register();

        // Le serveur nous indique les recharges en cours (affichage uniquement)
        ClientPlayNetworking.registerGlobalReceiver(CooldownPayload.TYPE, (payload, context) -> {
            ClientLevel level = context.client().level;
            if (level != null) {
                ClientCooldowns.set(payload.key(), payload.ticks(), level.getGameTime());
            }
        });
        // Tremblement de caméra demandé par le serveur (attaques du Bouddha)
        ClientPlayNetworking.registerGlobalReceiver(ShakePayload.TYPE,
                (payload, context) -> CameraShake.start(payload.strength(), payload.ticks()));
        // Coffre au Trésor : ouverture de la roulette, puis fruit tiré par le serveur au clic sur OUVRIR (il le donne lui-même)
        // PNJ : fenêtre de dialogue (clic droit sur le Tavernier)
        ClientPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.OpenDialoguePayload.TYPE, (payload, context) ->
                Minecraft.getInstance().gui.setScreen(new nolan.bloxpiece.client.screen.DialogueScreen(payload.npc())));
        ClientPlayNetworking.registerGlobalReceiver(OpenRoulettePayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            String id = payload.fruitItemId();
            if (OpenRoulettePayload.OPEN.equals(id)) {
                mc.gui.setScreen(new RouletteScreen()); // fenêtre ouverte, rien de consommé
            } else if (mc.gui.screen() instanceof RouletteScreen roulette) {
                if (OpenRoulettePayload.FAILED.equals(id)) {
                    roulette.fail(); // coffre introuvable
                } else {
                    Item fruit = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                    roulette.setWinner(fruit); // réponse au clic sur OUVRIR
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientCooldowns.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraShake.tick(client);
            // Trident de glace en main : chaque nouveau clic d'attaque déclenche l'estoc (chez tous les joueurs, via le serveur)
            boolean attackDown = client.options.keyAttack.isDown() && client.gui.screen() == null;
            if (attackDown && !attackWasDown && client.player != null
                    && IceSaberAbility.isHeld(client.player)) {
                ClientPlayNetworking.send(new SaberStrikePayload(0));
            }
            attackWasDown = attackDown;
            ClientWaterCurse.tick(client);
            LogiaClient.tick(client);
            if (client.player == null || client.getConnection() == null) {
                return;
            }
            // Touche G : ouvre le menu du personnage (le fermer se fait dans le menu lui-même)
            while (CHARACTER_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new CharacterScreen(CHARACTER_KEY));
                }
            }

            for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
                // while : au cas où la touche a été pressée plusieurs fois dans le même tick
                boolean pressed = false;
                while (ABILITY_KEYS[slot].consumeClick()) {
                    ClientPlayNetworking.send(new UseAbilityPayload(slot, true));
                    pressed = true;
                }
                boolean down = ABILITY_KEYS[slot].isDown();
                if (!down && (KEY_WAS_DOWN[slot] || pressed)) { // appui très bref : relâchée dans le même tick
                    ClientPlayNetworking.send(new UseAbilityPayload(slot, false)); // touche relâchée
                }
                KEY_WAS_DOWN[slot] = down;
            }
        });
    }
}
