package nolan.bloxpiece.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.fx.EnteiSun;
import nolan.bloxpiece.fx.IceEffects;
import nolan.bloxpiece.fx.IceStatue;

/**
 * Le gel du fruit de la Glace, commun à ses 4 attaques :
 * <ul>
 *   <li>{@link #hit} : dégâts de glace (type FREEZE : ce ne sont pas des dégâts physiques, le passif Logia du feu ne les annule
 *       pas) + ralentissement intense ;</li>
 *   <li>{@link #freeze} : la cible est immobilisée sur place (vitesse et saut à zéro par attributs, synchronisés avec son client ;
 *       IA coupée pour un mob), recouverte de givre (effet vanilla de la neige poudreuse : écran gelé pour un joueur) et, si demandé,
 *       emprisonnée dans une statue de glace (IceStatue). Tout est retiré à la fin, à la mort ou à l'arrêt du serveur.</li>
 * </ul>
 */
public final class IceFreeze {

    private static final Identifier MODIFIER_ID = BloxPiece.id("ice_frozen");

    private static final class Frozen {
        final LivingEntity target;
        final IceStatue statue; // peut être null
        final boolean hadNoAi;
        final Vec3 at;
        long until;

        Frozen(LivingEntity target, IceStatue statue, boolean hadNoAi, long until) {
            this.target = target;
            this.statue = statue;
            this.hadNoAi = hadNoAi;
            this.at = target.position();
            this.until = until;
        }
    }

    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();

    private IceFreeze() {
    }

    /** Une entité vivante que l'attaque de {@code owner} peut toucher (pas le lanceur, ni un joueur en créatif ou spectateur). */
    public static boolean canHit(LivingEntity entity, ServerPlayer owner) {
        if (entity == owner || !entity.isAlive() || entity.isSpectator() || entity instanceof EnteiSun) {
            return false;
        }
        return !(entity instanceof Player player && player.isCreative());
    }

    /** Dégâts de glace + ralentissement intense ({@code slowTicks} ticks, 0 = aucun). Attribués au lanceur (XP, riposte). */
    public static void hit(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage, int slowTicks) {
        DamageSource source = level.damageSources().source(DamageTypes.FREEZE, owner, owner);
        target.setLastHurtByMob(owner);
        target.hurtServer(level, source, damage);
        if (slowTicks > 0 && target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowTicks, 3, false, true, true));
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), Math.min(140, slowTicks + 40)));
        }
        IceEffects.frostBurst(level, target.getBoundingBox().getCenter(), target.getBbWidth());
    }

    /** Gèle la cible sur place pendant {@code ticks} ticks ; {@code statue} : l'enferme aussi dans un bloc de glace. */
    public static void freeze(ServerLevel level, LivingEntity target, int ticks, boolean statue) {
        if (!target.isAlive()) {
            return;
        }
        long until = level.getGameTime() + ticks;
        Frozen current = FROZEN.get(target.getId());
        if (current != null) {
            current.until = Math.max(current.until, until); // déjà gelé : prolongé
            return;
        }
        boolean hadNoAi = target instanceof Mob mob && mob.isNoAi();
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
        }
        set(target, Attributes.MOVEMENT_SPEED);
        set(target, Attributes.JUMP_STRENGTH);
        target.setDeltaMovement(Vec3.ZERO);
        IceStatue shell = statue ? IceStatue.spawn(level, target) : null;
        FROZEN.put(target.getId(), new Frozen(target, shell, hadNoAi, until));
        level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_PLACE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /** À appeler à chaque tick serveur (ModEvents) : maintient les cibles gelées immobiles, puis les libère. */
    public static void tick(MinecraftServer server) {
        Iterator<Frozen> it = FROZEN.values().iterator();
        while (it.hasNext()) {
            Frozen f = it.next();
            LivingEntity t = f.target;
            if (!t.isAlive() || t.isRemoved() || t.level().getGameTime() >= f.until) {
                release(f);
                it.remove();
                continue;
            }
            t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0); // il peut tomber, pas se déplacer
            t.setTicksFrozen(Math.max(t.getTicksFrozen(), 140));                    // givre (écran gelé pour un joueur)
            if (f.statue != null) {
                f.statue.setPos(t.getX(), t.getY(), t.getZ());
            }
        }
    }

    private static void release(Frozen f) {
        LivingEntity t = f.target;
        for (Holder<Attribute> attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = t.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(MODIFIER_ID);
            }
        }
        if (t instanceof Mob mob) {
            mob.setNoAi(f.hadNoAi);
        }
        if (f.statue != null) {
            if (t.level() instanceof ServerLevel level) {
                IceEffects.shatter(level, f.statue.position().add(0.0, t.getBbHeight() * 0.5, 0.0), Math.max(1.0F, t.getBbHeight() * 0.6F));
                level.playSound(null, t.blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            f.statue.discard();
        }
    }

    /** Annule tout gel en cours (arrêt du serveur). */
    public static void clear() {
        for (Frozen f : FROZEN.values()) {
            release(f);
        }
        FROZEN.clear();
    }

    /** Modificateur temporaire qui ramène l'attribut à zéro (x0). */
    private static void set(LivingEntity target, Holder<Attribute> attribute) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
