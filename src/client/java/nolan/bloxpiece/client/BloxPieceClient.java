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

    // Hakis : Armement, Observation, Rois (modifiables dans Options > Contrôles)
    private static final KeyMapping[] HAKI_KEYS = {
            registerKey("key.bloxpiece.haki_armament", SDLScancode.SDL_SCANCODE_B),
            registerKey("key.bloxpiece.haki_observation", SDLScancode.SDL_SCANCODE_N),
            registerKey("key.bloxpiece.haki_conqueror", SDLScancode.SDL_SCANCODE_M)
    };
    private static final boolean[] HAKI_WAS_DOWN = new boolean[HAKI_KEYS.length];
    /** Ticks depuis l'enfoncement de chaque touche de Haki, et l'état au moment de l'appui (maintien de l'Armement). */
    private static final int[] HAKI_HELD = new int[HAKI_KEYS.length];
    private static final boolean[] HAKI_HOLD_SENT = new boolean[HAKI_KEYS.length];
    private static boolean armamentWasOn;

    private static void sendHaki(int slot, int action) {
        ClientPlayNetworking.send(new nolan.bloxpiece.network.UseHakiPayload(slot, action));
    }

    /**
     * Touche de l'Armement : appui court = bras (activé tout de suite à l'appui, ou retiré au relâchement s'il était déjà actif) ;
     * maintenue {@link nolan.bloxpiece.haki.ArmamentHaki#HOLD_TICKS} ticks (0,6 s) = HOLD, le revêtement gagne tout le corps.
     */
    private static void tickArmamentKey(Minecraft client, boolean down, boolean was, boolean clicked) {
        int press = nolan.bloxpiece.network.UseHakiPayload.PRESS;
        if (down && !was) {
            HAKI_HELD[0] = 0;
            HAKI_HOLD_SENT[0] = false;
            armamentWasOn = client.player != null && nolan.bloxpiece.haki.ArmamentHaki.mode(client.player) != 0;
            if (!armamentWasOn) {
                sendHaki(0, press); // le bras se couvre dès l'appui (début de la fusion)
            }
        } else if (down) {
            HAKI_HELD[0]++;
            if (!HAKI_HOLD_SENT[0] && HAKI_HELD[0] >= nolan.bloxpiece.haki.ArmamentHaki.HOLD_TICKS) {
                HAKI_HOLD_SENT[0] = true;
                sendHaki(0, nolan.bloxpiece.network.UseHakiPayload.HOLD);
            }
        } else if (was) {
            if (!HAKI_HOLD_SENT[0] && armamentWasOn) {
                sendHaki(0, press); // appui court sur un Haki déjà actif : on le retire
            }
            sendHaki(0, nolan.bloxpiece.network.UseHakiPayload.RELEASE);
        } else if (clicked) {
            sendHaki(0, press); // appui et relâchement dans le même tick : bascule simple
            sendHaki(0, nolan.bloxpiece.network.UseHakiPayload.RELEASE);
        }
    }

    /** Touche du Haki de cette case (le HUD des Hakis en affiche le nom). */
    public static KeyMapping hakiKey(int slot) {
        return slot >= 0 && slot < HAKI_KEYS.length ? HAKI_KEYS[slot] : null;
    }

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
        // Marines (même rendu que les pirates : équipement 3D, arme 3D en main, pose de visée ; fusil à deux mains)
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.entity.MarineGearModels.SWORDSMAN,
                nolan.bloxpiece.client.entity.MarineGearModels::swordsman);
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.entity.MarineGearModels.RIFLEMAN,
                nolan.bloxpiece.client.entity.MarineGearModels::rifleman);
        EntityRendererRegistry.register(ModEntities.MARINE_SWORDSMAN, context -> new ArmedPirateRenderer(context,
                nolan.bloxpiece.client.entity.MarineGearModels.SWORDSMAN, "marine_swordsman", 4));
        EntityRendererRegistry.register(ModEntities.MARINE_RIFLEMAN, context -> new ArmedPirateRenderer(context,
                nolan.bloxpiece.client.entity.MarineGearModels.RIFLEMAN, "marine_rifleman", 4));
        // Gol D. Roger (même rendu que les pirates : équipement 3D, sabre 3D en main en combat, au fourreau sinon)
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.entity.RogerModels.ROGER,
                nolan.bloxpiece.client.entity.RogerModels::roger);
        EntityRendererRegistry.register(ModEntities.GOL_D_ROGER, context -> new ArmedPirateRenderer(context,
                nolan.bloxpiece.client.entity.RogerModels.ROGER, "gol_d_roger", 1));
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
        particles.register(ModParticles.HAKI_EYE, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.HAKI_EYE));
        particles.register(ModParticles.HAKI_EYE_GLOW, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.HAKI_EYE_GLOW));
        particles.register(ModParticles.HAKI_SHARD, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.HAKI_SHARD));
        particles.register(ModParticles.HAKI_SPARK, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.HAKI_SPARK));
        particles.register(ModParticles.HAKI_RING, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.HAKI_RING));
        particles.register(ModParticles.HAKI_MIST, sprites -> new ModParticle.Provider(sprites, ModParticle.Style.HAKI_MIST));

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
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.haki.HakiArmamentLayer.LAYER,
                nolan.bloxpiece.client.haki.HakiArmamentLayer::createLayer);
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.haki.HakiEyeTrailLayer.LAYER,
                nolan.bloxpiece.client.haki.HakiEyeTrailLayer::createLayer);
        HandcuffsClient.register();
        ClientTickEvents.START_CLIENT_TICK.register(HandcuffsClient::tick);

        // HUD des 4 attaques à côté de la hotbar
        AbilityHud.register();
        // HUD des 3 Hakis à gauche de la hotbar, éclairs du Haki des Rois
        nolan.bloxpiece.client.haki.HakiHud.register();
        nolan.bloxpiece.client.hud.JobUnlockBanner.register(); // bannière « Job débloqué » au niveau 5
        nolan.bloxpiece.client.hud.ConquerorFlash.register(); // flash rouge et vignettage à l'activation du Haki des Rois
        nolan.bloxpiece.client.hud.RogerBossBar.register(); // barre de boss de Gol D. Roger
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.haki.HakiBoltRenderer.LAYER,
                nolan.bloxpiece.client.haki.HakiBoltRenderer.BoltModel::createLayer);
        EntityRendererRegistry.register(ModEntities.HAKI_BOLT, nolan.bloxpiece.client.haki.HakiBoltRenderer::new);
        // La Lame dévastatrice du Haki des Rois (épée Ace) : calque du modèle d'abord, renderer ensuite
        ModelLayerRegistry.registerModelLayer(nolan.bloxpiece.client.entity.AceSlashModel.LAYER,
                nolan.bloxpiece.client.entity.AceSlashModel::createLayer);
        EntityRendererRegistry.register(ModEntities.ACE_SLASH, nolan.bloxpiece.client.entity.AceSlashRenderer::new);
        // L'onde de choc de l'activation du Haki des Rois (réutilise le calque des éclairs, déjà enregistré plus haut)
        EntityRendererRegistry.register(ModEntities.CONQUEROR_SHOCKWAVE, nolan.bloxpiece.client.haki.ConquerorShockwaveRenderer::new);
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
        // Haki de l'Observation : animation d'esquive (le joueur concerné et ceux qui le voient), petit à-coup de caméra chez lui
        ClientPlayNetworking.registerGlobalReceiver(nolan.bloxpiece.network.HakiDodgePayload.TYPE, (payload, context) -> {
            nolan.bloxpiece.client.haki.HakiClientState.startDodge(payload.entityId(), payload.variant(), payload.right());
            if (context.client().player != null && context.client().player.getId() == payload.entityId()) {
                CameraShake.start(1.2F, 5);
            }
        });
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
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            ClientCooldowns.clear();
            nolan.bloxpiece.client.haki.HakiClientState.clear();
            nolan.bloxpiece.client.haki.HakiHud.reset();
            nolan.bloxpiece.client.haki.HakiEyeTrail.clear();
            nolan.bloxpiece.client.haki.HakiAfterimage.clear();
            nolan.bloxpiece.client.haki.HakiItemCoat.clear();
        });

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
            nolan.bloxpiece.client.haki.HakiItemCoat.tick(client);
            nolan.bloxpiece.client.hud.RogerBossBar.tick(client); // barre de boss de Gol D. Roger (zone, vie, fondu)
            nolan.bloxpiece.client.hud.JobUnlockBanner.tick(client); // revêtement du Haki sur l'objet en main (et objet lâché)
            if (client.player == null || client.getConnection() == null) {
                return;
            }
            // Touche G : ouvre le menu du personnage (le fermer se fait dans le menu lui-même)
            while (CHARACTER_KEY.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new CharacterScreen(CHARACTER_KEY));
                }
            }

            // Hakis : appui et relâchement (l'Armement distingue l'appui simple du maintien)
            // On suit l'état enfoncé de la touche (et non les « clics », que la répétition du clavier multiplie pendant un
            // maintien : c'est ce qui empêchait le corps entier de se déclencher).
            for (int slot = 0; slot < HAKI_KEYS.length; slot++) {
                boolean clicked = false;
                while (HAKI_KEYS[slot].consumeClick()) {
                    clicked = true;
                }
                boolean down = HAKI_KEYS[slot].isDown() && client.gui.screen() == null;
                boolean was = HAKI_WAS_DOWN[slot];
                if (slot == 0) {
                    tickArmamentKey(client, down, was, clicked);
                } else if (down && !was) {
                    sendHaki(slot, nolan.bloxpiece.network.UseHakiPayload.PRESS);
                } else if (!down && was) {
                    sendHaki(slot, nolan.bloxpiece.network.UseHakiPayload.RELEASE);
                } else if (!down && clicked) {
                    // appui et relâchement dans le même tick
                    sendHaki(slot, nolan.bloxpiece.network.UseHakiPayload.PRESS);
                    sendHaki(slot, nolan.bloxpiece.network.UseHakiPayload.RELEASE);
                }
                HAKI_WAS_DOWN[slot] = down;
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
