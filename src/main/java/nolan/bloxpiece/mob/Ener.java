package nolan.bloxpiece.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Ener, le « dieu » de la foudre (modèle 3D : EnerModels / EnerModel, généré par src/blockbench/make_ener.py). Première
 * version : SEULEMENT le modèle et l'intégration. Il a le comportement de base des ennemis du mod (BloxMob : poursuite, coups au
 * corps-à-corps, balade, XP BloxPiece à sa mort), sans aucun pouvoir pour l'instant : ses attaques de foudre viendront plus tard.
 * Un peu plus grand que les autres (échelle 1,15 : hitbox et rendu, attribut SCALE dans ModEntities), difficile à repousser, jamais
 * retiré du monde quand les joueurs s'éloignent. Pas d'apparition naturelle : /summon bloxpiece:ener.
 */
public class Ener extends BloxMob {

    public static final BloxMobStats STATS = new BloxMobStats(
            300.0, // points de vie
            12.0,  // dégâts par coup (à mains nues, en attendant ses pouvoirs)
            0.30,  // vitesse
            40.0,  // détection / poursuite (blocs)
            400    // XP BloxPiece
    );

    public Ener(EntityType<? extends Ener> type, Level level) {
        super(type, level);
    }

    @Override
    public BloxMobStats stats() {
        return STATS;
    }

    /** Un personnage unique : jamais retiré du monde quand les joueurs s'éloignent. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
