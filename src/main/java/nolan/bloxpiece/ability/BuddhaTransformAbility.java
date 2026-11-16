package nolan.bloxpiece.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fruit.Fruits;
import nolan.bloxpiece.registry.ModAttachments;

/**
 * Première attaque du fruit du Bouddha : transformation en Bouddha géant (inspirée de Sengoku).
 *
 * C'est une attaque "interrupteur" : une première pression fait grandir le joueur petit à petit jusqu'à {@link #MAX_SCALE} fois sa taille (3 s),
 * une deuxième le fait rétrécir petit à petit jusqu'à sa taille normale (on peut inverser en plein milieu : la progression est continue).
 *
 * Tout passe par l'attribut vanilla SCALE (modificateur temporaire, jamais sauvegardé) : Minecraft agrandit alors tout seul le modèle
 * entier, la hitbox et la hauteur des yeux (donc la caméra), et synchronise l'attribut avec tous les clients (le joueur et ceux qui le
 * voient). On accompagne la taille de quelques modificateurs proportionnels (pas, saut, vitesse, portée, chute, recul de la caméra à la
 * troisième personne). La couche dorée est dessinée côté client d'après cette même taille (voir BuddhaGoldLayer) : rien d'autre à synchroniser.
 *
 * Au début de chaque transformation, une onde de choc dorée est lancée UNE seule fois depuis le joueur (voir Wave) : le serveur repousse
 * les entités proches (joueurs, créatures, objets au sol, projectiles...) avec une force qui diminue avec la distance, et envoie les
 * particules du cercle d'énergie à tous les joueurs qui voient le lanceur.
 *
 * Sécurités : la croissance se met en pause tant que le joueur n'a pas la place de grandir (jamais coincé dans un bloc), tout est retiré à
 * la mort / à la reconnexion / au retrait du fruit, et les pressions répétées sont limitées par la recharge.
 */
public class BuddhaTransformAbility implements Ability {

    /** Taille finale (multiple de la taille normale). 4 = environ 7,2 blocs de haut. */
    public static final double MAX_SCALE = 4.0;

    private static final int GROW_TICKS = 60;   // 3 s pour atteindre la taille maximale
    private static final int SHRINK_TICKS = 45; // 2,25 s pour retrouver la taille normale

    /**
     * Distance de la caméra en 3e personne à taille maximale (blocs ; normale : 4). Attention : Minecraft multiplie déjà la distance de la
     * caméra par la taille du joueur, donc on ajuste l'attribut CAMERA_DISTANCE pour que la distance TOTALE passe progressivement de 4 à cette
     * valeur (voir apply). La vue à la première personne n'utilise pas cette distance.
     */
    private static final double CAMERA_GIANT = 9.0;

    /** Durée (ticks) des vagues d'énergie qui suivent l'explosion de départ. */
    private static final int BURST_TICKS = 14;

    // ---- onde de choc de départ
    /** Rayon maximal de l'onde de choc (blocs). */
    private static final double WAVE_RADIUS = 16.0;
    /** Durée de propagation : l'onde atteint son rayon maximal en 8 ticks (0,4 s), donc très rapide. */
    private static final int WAVE_TICKS = 8;
    /** Vitesse de propulsion (blocs/tick) au contact du joueur ; elle diminue linéairement jusqu'à 0 au rayon maximal. */
    private static final double WAVE_MAX_PUSH = 2.2;
    /** Petite poussée vers le haut (blocs/tick), plus forte près du joueur, pour décoller les entités du sol. */
    private static final double WAVE_MAX_LIFT = 0.55;

    /** Un seul identifiant pour tous les modificateurs de la transformation (un par attribut). */
    private static final Identifier MODIFIER_ID = BloxPiece.id("buddha_form");

    /** État serveur d'un joueur transformé ou en cours de transformation. */
    private static final class Form {
        float progress;        // 0 = taille normale, 1 = taille maximale
        boolean wanted = true; // true = il grandit (ou reste géant), false = il rétrécit
        boolean warned;        // a déjà reçu le message "pas assez de place"
        int burstTicks;        // vagues d'énergie dorée encore à émettre après l'explosion de départ
    }

    private static final Map<UUID, Form> FORMS = new HashMap<>();

    /** Onde de choc en cours de propagation (état serveur). Chaque entité n'est repoussée qu'une seule fois par onde. */
    private static final class Wave {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final Set<Integer> hit = new HashSet<>();
        int step;

        Wave(ServerLevel level, ServerPlayer owner) {
            this.level = level;
            this.owner = owner.getUUID();
            this.center = new Vec3(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ());
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();

    @Override
    public String id() {
        return "buddha_transform";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 2; // 2 secondes : évite les activations en rafale
    }

    @Override
    public void use(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Form form = FORMS.get(player.getUUID());
        if (form == null) {
            Form created = new Form();
            created.burstTicks = BURST_TICKS;
            FORMS.put(player.getUUID(), created);
            burst(level, player, true);
            startShockwave(level, player);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.6f);
        } else {
            form.wanted = !form.wanted;
            form.warned = false;
            // Explosion dorée : grande quand le pouvoir se déclenche, plus discrète quand il se relâche
            form.burstTicks = form.wanted ? BURST_TICKS : 0;
            burst(level, player, form.wanted);
            if (form.wanted) {
                startShockwave(level, player); // nouvelle transformation (le joueur grandit de nouveau) : une onde de choc, une seule
            }
            level.playSound(null, player.blockPosition(),
                    form.wanted ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.6f);
        }
    }

    /** À appeler à chaque tick serveur (voir ModEvents). */
    public static void tick(MinecraftServer server) {
        tickWaves();
        if (FORMS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Form>> it = FORMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Form> entry = it.next();
            Form form = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            // Déconnecté : le modificateur est temporaire, il disparaît avec le joueur
            if (player == null) {
                it.remove();
                continue;
            }
            // Mort : on retire tout (le joueur réapparaît à taille normale)
            if (!player.isAlive()) {
                strip(player);
                it.remove();
                continue;
            }
            // Fruit retiré (/removef...) : retour progressif à la normale
            if (Fruits.get(player.getAttached(ModAttachments.FRUIT)) != Fruits.BUDDHA) {
                form.wanted = false;
            }

            // Vagues d'énergie qui s'élargissent après l'explosion de départ
            if (form.burstTicks > 0) {
                shockRings((ServerLevel) player.level(), player, BURST_TICKS - form.burstTicks);
                form.burstTicks--;
            }

            float before = form.progress;
            float next = form.wanted ? before + 1.0F / GROW_TICKS : before - 1.0F / SHRINK_TICKS;
            next = Math.max(0.0F, Math.min(1.0F, next));

            // Pas la place de grandir (plafond, mur...) : on attend, le joueur ne reste jamais coincé dans les blocs
            if (next > before && !fits(player, scaleFor(next))) {
                next = before;
                if (!form.warned) {
                    form.warned = true;
                    player.sendSystemMessage(Component.translatable("message.bloxpiece.buddha_no_room"), true);
                }
            } else if (next > before) {
                form.warned = false;
            }

            form.progress = next;
            if (next != before) {
                apply(player, next);
                transitionEffects((ServerLevel) player.level(), player);
            }

            // Retour à la taille normale terminé : on retire tout
            if (next <= 0.0F && !form.wanted) {
                strip(player);
                it.remove();
            }
        }
    }

    /** Retire la transformation d'un joueur tout de suite (mort, réapparition, connexion). */
    public static void reset(ServerPlayer player) {
        FORMS.remove(player.getUUID());
        strip(player);
    }

    public static void clear() {
        FORMS.clear();
        WAVES.clear();
    }

    // ---------------------------------------------------------------- onde de choc

    /** Lance l'onde de choc depuis le joueur (appelée une seule fois par transformation) : première étape tout de suite, le reste dans tick. */
    private static void startShockwave(ServerLevel level, ServerPlayer player) {
        Wave wave = new Wave(level, player);
        advance(wave);
        if (wave.step < WAVE_TICKS) {
            WAVES.add(wave);
        }
    }

    private static void tickWaves() {
        if (WAVES.isEmpty()) {
            return;
        }
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            advance(wave);
            if (wave.step >= WAVE_TICKS) {
                it.remove();
            }
        }
    }

    /**
     * Fait avancer le front de l'onde d'une étape : repousse les entités que le front vient d'atteindre (une seule fois chacune) et dessine
     * le cercle d'énergie dorée au rayon courant, pour que l'image suive exactement la propagation réelle.
     */
    private static void advance(Wave wave) {
        double previous = WAVE_RADIUS * wave.step / WAVE_TICKS;
        wave.step++;
        double radius = WAVE_RADIUS * wave.step / WAVE_TICKS;
        ServerLevel level = wave.level;
        Vec3 c = wave.center;

        // Entités atteintes par le front (distance mesurée du centre du joueur au centre de la boîte de l'entité)
        AABB area = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        for (Entity entity : level.getEntities((Entity) null, area, e -> canBePushed(e, wave))) {
            Vec3 at = entity.getBoundingBox().getCenter();
            double distance = at.distanceTo(c);
            if (distance > radius || wave.hit.contains(entity.getId())) {
                continue;
            }
            wave.hit.add(entity.getId());
            push(entity, at, c, distance);
        }

        // Cercle d'énergie : deux anneaux par étape pour une expansion fluide
        ring(level, c, (previous + radius) / 2.0);
        ring(level, c, radius);
    }

    private static boolean canBePushed(Entity entity, Wave wave) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger() || entity.getUUID().equals(wave.owner)) {
            return false;
        }
        if (entity instanceof Player player && player.getAbilities().flying) {
            return false; // comme les explosions vanilla : un joueur en vol créatif n'est pas bousculé
        }
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner == null || !owner.getUUID().equals(wave.owner); // ne dérange pas les attaques du lanceur lui-même
        }
        return entity instanceof LivingEntity || entity instanceof ItemEntity
                || entity instanceof PrimedTnt || entity instanceof FallingBlockEntity;
    }

    /** Propulse une entité à l'opposé du centre ; la force diminue avec la distance et tient compte de la résistance au recul. */
    private static void push(Entity entity, Vec3 at, Vec3 center, double distance) {
        double falloff = Mth.clamp(1.0 - distance / WAVE_RADIUS, 0.0, 1.0);
        double dx = at.x - center.x;
        double dz = at.z - center.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-3) {
            dx = 1.0; // pile au centre : direction arbitraire
            dz = 0.0;
            horizontal = 1.0;
        }
        double strength = WAVE_MAX_PUSH * falloff;
        double lift = WAVE_MAX_LIFT * falloff;
        if (entity instanceof LivingEntity living) {
            double resistance = Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
            strength *= 1.0 - resistance;
            lift *= 1.0 - resistance;
        }
        // Entity.push ajoute la vitesse ET demande la synchronisation avec les clients (indispensable pour les joueurs, y compris en multijoueur)
        entity.push(dx / horizontal * strength, lift, dz / horizontal * strength);
        entity.fallDistance = 0.0; // la propulsion ne doit pas faire de dégâts de chute
    }

    /** Un cercle d'énergie dorée horizontal de rayon donné, plus un voile de particules en dôme (l'onde part dans toutes les directions). */
    private static void ring(ServerLevel level, Vec3 c, double radius) {
        int points = Mth.clamp((int) (radius * 14.0), 24, 200);
        double groundY = c.y - 0.8; // un peu au-dessus du sol pour un joueur debout
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points;
            double x = c.x + Math.cos(a) * radius;
            double z = c.z + Math.sin(a) * radius;
            level.sendParticles(new DustParticleOptions(0xFFC83C, 2.2F), true, true, x, groundY, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, true, true, x, groundY + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.WAX_ON, true, true, x, groundY + 0.3, z, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        // Dôme : quelques points sur la sphère, au-dessus du sol
        int dome = Mth.clamp((int) (radius * 5.0), 12, 70);
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < dome; i++) {
            double dy = (i + 0.5) / dome;           // 0..1 : moitié supérieure de la sphère
            double ringR = Math.sqrt(1.0 - dy * dy);
            double a = i * goldenAngle;
            level.sendParticles(ParticleTypes.END_ROD, true, true,
                    c.x + Math.cos(a) * ringR * radius, c.y + dy * radius * 0.6, c.z + Math.sin(a) * ringR * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---------------------------------------------------------------- taille

    /** Taille correspondant à une progression : courbe douce (démarrage et arrivée en douceur, jamais de saut). */
    private static double scaleFor(float progress) {
        double eased = progress * progress * (3.0 - 2.0 * progress);
        return 1.0 + (MAX_SCALE - 1.0) * eased;
    }

    /** Le joueur peut-il passer à cette taille sans que son corps entre dans des blocs ? */
    private static boolean fits(ServerPlayer player, double newScale) {
        double ratio = newScale / player.getScale();
        if (ratio <= 1.0001) {
            return true;
        }
        AABB box = player.getBoundingBox();
        Vec3 center = box.getCenter();
        double halfX = box.getXsize() * ratio / 2.0;
        double halfZ = box.getZsize() * ratio / 2.0;
        AABB grown = new AABB(center.x - halfX, box.minY, center.z - halfZ,
                center.x + halfX, box.minY + box.getYsize() * ratio, center.z + halfZ);
        return player.level().noCollision(player, grown);
    }

    private static void apply(ServerPlayer player, float progress) {
        double extra = scaleFor(progress) - 1.0; // 0 = normal, 3 = taille maximale
        set(player, Attributes.SCALE, extra, Operation.ADD_VALUE);
        set(player, Attributes.STEP_HEIGHT, 0.6 * extra, Operation.ADD_VALUE);                // monte les blocs comme un géant
        set(player, Attributes.JUMP_STRENGTH, 0.35 * extra, Operation.ADD_MULTIPLIED_BASE);   // saut plus haut
        set(player, Attributes.MOVEMENT_SPEED, 0.15 * extra, Operation.ADD_MULTIPLIED_BASE);  // pas plus longs
        set(player, Attributes.BLOCK_INTERACTION_RANGE, 1.5 * extra, Operation.ADD_VALUE);    // les yeux sont bien plus haut
        set(player, Attributes.ENTITY_INTERACTION_RANGE, 1.0 * extra, Operation.ADD_VALUE);
        set(player, Attributes.SAFE_FALL_DISTANCE, 1.0 * extra, Operation.ADD_VALUE);
        // Caméra 3e personne : distance totale (taille x attribut) de 4 à CAMERA_GIANT blocs, sans exagérer l'éloignement
        AttributeInstance camera = player.getAttribute(Attributes.CAMERA_DISTANCE);
        if (camera != null) {
            double normal = camera.getBaseValue();
            double total = normal + (CAMERA_GIANT - normal) * (extra / (MAX_SCALE - 1.0));
            set(player, Attributes.CAMERA_DISTANCE, total / (1.0 + extra) - normal, Operation.ADD_VALUE);
        }
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, double amount, Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, amount, operation));
        }
    }

    private static void strip(ServerPlayer player) {
        for (Holder<Attribute> attribute : java.util.List.of(Attributes.SCALE, Attributes.STEP_HEIGHT, Attributes.JUMP_STRENGTH,
                Attributes.MOVEMENT_SPEED, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE,
                Attributes.SAFE_FALL_DISTANCE, Attributes.CAMERA_DISTANCE)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(MODIFIER_ID);
            }
        }
    }

    /** Petites particules dorées autour du corps pendant que la taille change. */
    private static void transitionEffects(ServerLevel level, ServerPlayer player) {
        double width = player.getBbWidth();
        double height = player.getBbHeight();
        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + height * 0.5, player.getZ(),
                4, width * 0.5, height * 0.4, width * 0.5, 0.0);
    }

    // ---------------------------------------------------------------- explosion dorée

    /**
     * Explosion d'énergie dorée autour du joueur : sphère de particules lumineuses qui part dans toutes les directions, étincelles, poussière
     * dorée et éclair. Les particules sont envoyées par le serveur à tous les joueurs qui voient le lanceur (pas seulement à lui).
     * {@code big} = false : version discrète pour la détransformation.
     */
    private static void burst(ServerLevel level, ServerPlayer player, boolean big) {
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.5;
        double z = player.getZ();
        int points = big ? 160 : 50;
        double speed = big ? 0.9 : 0.3;

        sphere(level, ParticleTypes.END_ROD, x, y, z, points, speed);
        sphere(level, ParticleTypes.WAX_ON, x, y, z, points, speed * 0.7);
        sphere(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, points / 2, speed * 1.2);
        // poussière dorée qui flotte autour du corps
        level.sendParticles(new DustParticleOptions(0xFFC83C, big ? 2.5F : 1.5F), true, true, x, y, z, big ? 120 : 40, 1.5, 1.5, 1.5, 0.0);
        if (big) {
            // éclair de lumière doré au centre
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE9A0), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Anneaux d'énergie au sol, de plus en plus rapides : l'onde de choc dorée s'élargit pendant les premiers ticks. */
    private static void shockRings(ServerLevel level, ServerPlayer player, int step) {
        double x = player.getX();
        double y = player.getY() + 0.2;
        double z = player.getZ();
        double speed = 0.5 + 0.12 * step;
        ring(level, ParticleTypes.END_ROD, x, y, z, 48, speed);
        if (step % 2 == 0) {
            ring(level, ParticleTypes.WAX_ON, x, y, z, 48, speed * 0.8);
        }
    }

    /** {@code points} particules réparties sur une sphère, qui partent vers l'extérieur (count = 0 : les "offsets" sont la direction). */
    private static void sphere(ServerLevel level, ParticleOptions particle, double x, double y, double z, int points, double speed) {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double dy = 1.0 - 2.0 * (i + 0.5) / points;
            double ring = Math.sqrt(1.0 - dy * dy);
            double a = i * goldenAngle;
            level.sendParticles(particle, true, true, x, y, z, 0, Math.cos(a) * ring, dy, Math.sin(a) * ring, speed);
        }
    }

    /** {@code points} particules réparties sur un cercle horizontal, qui partent vers l'extérieur. */
    private static void ring(ServerLevel level, ParticleOptions particle, double x, double y, double z, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points;
            level.sendParticles(particle, true, true, x, y, z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
        }
    }
}
