package nolan.bloxpiece.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * Première attaque du fruit du feu.
 * Pour l'instant elle ne fait rien : le contenu sera ajouté à l'étape "attaque".
 */
public class FireAbility implements Ability {

    @Override
    public String id() {
        return "fire_fist";
    }

    @Override
    public int cooldownTicks() {
        return 20 * 3; // 3 secondes
    }

    @Override
    public void use(ServerPlayer player) {
        // TODO : à remplir à l'étape suivante (projectile de feu, dash, etc.)
    }
}
