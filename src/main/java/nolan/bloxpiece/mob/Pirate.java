package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Le Pirate : l'ennemi de base du mod. Zombie à l'identité de pirate (voir PirateRenderer côté client). */
public class Pirate extends BloxMob {

    /**
     * Statistiques du Pirate : modifie ici pour l'équilibrer.
     * Vitesse 0.2 = un peu plus lente qu'un zombie (0.23), donc légèrement plus lente qu'un joueur qui marche.
     */
    public static final BloxMobStats STATS = new BloxMobStats(
            20.0,  // points de vie (10 coeurs)
            3.0,   // dégâts par attaque
            0.2,   // vitesse
            16.0   // distance de détection / poursuite (blocs)
    );

    public Pirate(EntityType<? extends Pirate> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }
}
