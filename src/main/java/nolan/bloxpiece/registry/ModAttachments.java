package nolan.bloxpiece.registry;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

import nolan.bloxpiece.BloxPiece;

public class ModAttachments {

    /**
     * Le fruit mangé par le joueur (id du fruit, ex : "fire").
     * Sauvegardé avec le joueur et conservé après la mort.
     * Synchronisé vers le client du joueur lui-même (nécessaire pour afficher le HUD des attaques).
     * Pas de valeur = le joueur n'a mangé aucun fruit.
     */
    public static final AttachmentType<String> FRUIT = AttachmentRegistry.create(
            BloxPiece.id("fruit"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly())
    );

    public static void initialize() {
    }
}
