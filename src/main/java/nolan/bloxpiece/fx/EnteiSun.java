package nolan.bloxpiece.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.registry.ModEntities;

/**
 * Le "corps" visible de Dai Enkai: Entei : un soleil qui affiche le modèle Blockbench (voir EnteiSunModel / EnteiSunRenderer côté client).
 *
 * C'est une entité purement VISUELLE : pas de collision, invulnérable, sans IA, jamais sauvegardée.
 * Les dégâts et l'explosion restent ceux de la boule de feu vanilla (LargeFireball) lancée par DaiEnkaiEnteiAbility ;
 * ce soleil se contente de la suivre (FireEffects) et de disparaître à l'impact.
 *
 * La taille passe par l'attribut vanilla SCALE : il est synchronisé automatiquement avec les clients
 * et le renderer vanilla applique le facteur tout seul (c'est ce qui permet à la boule de grossir).
 */
public class EnteiSun extends Mob {

    /** Rayon (en blocs) du corps du soleil Blockbench à l'échelle 1 (noyau + coques de feu, sans les langues de flamme ni les braises). */
    public static final double MODEL_RADIUS = 0.9;

    /** Vrai seulement pour un soleil créé par l'attaque : un soleil rechargé depuis une sauvegarde est supprimé. */
    private boolean managed;

    public EnteiSun(EntityType<? extends EnteiSun> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    /** Fait apparaître un soleil de ce rayon (en blocs) centré sur cette position. */
    public static EnteiSun spawn(ServerLevel level, Vec3 center, double radius) {
        EnteiSun sun = new EnteiSun(ModEntities.ENTEI_SUN, level);
        sun.managed = true;
        sun.setRadius(radius);
        sun.setPos(center.x, center.y, center.z);
        level.addFreshEntity(sun);
        return sun;
    }

    /** Rayon visible du soleil, en blocs (le centre du modèle est la position de l'entité). */
    public void setRadius(double radius) {
        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(Math.max(0.0625, radius / MODEL_RADIUS));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.managed && this.level() instanceof ServerLevel) {
            this.discard(); // soleil orphelin (ancienne sauvegarde)
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // l'explosion de la boule de feu ne touche jamais son propre soleil
    }

    public boolean isPickable() {
        return false; // ni cible de projectile, ni de curseur
    }

    public boolean isPushable() {
        return false;
    }

    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
