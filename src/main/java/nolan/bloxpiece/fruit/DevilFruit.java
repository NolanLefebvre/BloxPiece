package nolan.bloxpiece.fruit;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.ability.Ability;

/**
 * Un fruit du démon = un id + sa faiblesse + sa résistance + la liste de ses attaques.
 * La position dans la liste des attaques correspond à la touche :
 * index 0 = touche 1, index 1 = touche 2, index 2 = touche 3, index 3 = touche 4.
 */
public class DevilFruit {
    public static final int MAX_ABILITIES = 4;

    private final String id;
    private final @Nullable Weakness weakness;
    private final boolean fireproof;
    private final List<Ability> abilities;

    /**
     * @param weakness  la faiblesse permanente liée au fruit (null si le fruit n'en a pas)
     * @param fireproof true = le porteur est immunisé au feu et à la lave
     */
    public DevilFruit(String id, @Nullable Weakness weakness, boolean fireproof, List<Ability> abilities) {
        if (abilities.size() > MAX_ABILITIES) {
            throw new IllegalArgumentException("Un fruit a au maximum " + MAX_ABILITIES + " attaques");
        }
        this.id = id;
        this.weakness = weakness;
        this.fireproof = fireproof;
        this.abilities = List.copyOf(abilities);
    }

    public String id() {
        return id;
    }

    /** Item de ce fruit. Convention : l'item d'un fruit d'id "fire" s'appelle "fire_fruit". */
    public Identifier itemId() {
        return BloxPiece.id(id + "_fruit");
    }

    /** Clé de traduction du nom du fruit (c'est le nom de son item), pour les interfaces. */
    public String translationKey() {
        return "item." + BloxPiece.MOD_ID + "." + id + "_fruit";
    }

    /** Renvoie l'attaque de ce slot, ou null si ce fruit n'en a pas à cet emplacement. */
    public Ability getAbility(int slot) {
        if (slot < 0 || slot >= abilities.size()) {
            return null;
        }
        return abilities.get(slot);
    }

    /** Ce fruit a-t-il au moins une attaque ? (le menu des attaques affiche un message sinon) */
    public boolean hasAbilities() {
        return !abilities.isEmpty();
    }

    public boolean isFireproof() {
        return fireproof;
    }

    /** Est-ce que le porteur de ce fruit ignore ce type de dégâts ? (feu, lave, boules de feu...) */
    public boolean isImmuneTo(DamageSource source) {
        return fireproof && source.is(DamageTypeTags.IS_FIRE);
    }

    /** Applique la faiblesse du fruit (appelé à chaque tick serveur pour le porteur). */
    public void tickWeakness(ServerPlayer player) {
        if (weakness != null) {
            weakness.tick(player);
        }
    }
}
