package nolan.bloxpiece.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import nolan.bloxpiece.BloxPiece;
import nolan.bloxpiece.effect.WaterCurseEffect;

public class ModEffects {

    public static final Holder<MobEffect> WATER_CURSE = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            BloxPiece.id("water_curse"),
            new WaterCurseEffect()
    );

    public static void initialize() {
    }
}
