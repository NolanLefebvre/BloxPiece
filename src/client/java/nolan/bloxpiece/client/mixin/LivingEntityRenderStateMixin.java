package nolan.bloxpiece.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import nolan.bloxpiece.client.entity.CuffedRenderState;

/** Ajoute l'indicateur "menotté" à l'état de rendu de toutes les entités vivantes (voir CuffedRenderState). */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements CuffedRenderState,
        nolan.bloxpiece.client.haki.UnconsciousRenderState {

    @Unique
    private boolean bloxpiece$cuffed;

    @Unique
    private boolean bloxpiece$unconscious;

    @Override
    public boolean bloxpiece$isUnconscious() {
        return this.bloxpiece$unconscious;
    }

    @Override
    public void bloxpiece$setUnconscious(boolean unconscious) {
        this.bloxpiece$unconscious = unconscious;
    }

    @Override
    public boolean bloxpiece$isCuffed() {
        return this.bloxpiece$cuffed;
    }

    @Override
    public void bloxpiece$setCuffed(boolean cuffed) {
        this.bloxpiece$cuffed = cuffed;
    }
}
