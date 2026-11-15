package nolan.bloxpiece.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class WaterCurseEffect extends MobEffect {

    public WaterCurseEffect() {
        // HARMFUL = effet négatif, la couleur est en RGB
        super(MobEffectCategory.HARMFUL, 0x2e86c1);
    }

    // Appelé à chaque tick tant que l'effet est actif
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        // Dégâts uniquement si la tête est sous l'eau (entièrement immergé)
        if (entity.isUnderWater()) {
            entity.hurtServer(level, level.damageSources().drown(), 1.0f);
        }
        return super.applyEffectTick(level, entity, amplifier);
    }
}
