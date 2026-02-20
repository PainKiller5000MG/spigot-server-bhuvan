package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.LevelLightEngine;

public interface BlockAndTintGetter extends BlockGetter {

    float getShade(Direction direction, boolean shade);

    LevelLightEngine getLightEngine();

    int getBlockTint(BlockPos pos, ColorResolver color);

    default int getBrightness(LightLayer layer, BlockPos pos) {
        return this.getLightEngine().getLayerListener(layer).getLightValue(pos);
    }

    default int getRawBrightness(BlockPos pos, int darkening) {
        return this.getLightEngine().getRawBrightness(pos, darkening);
    }

    default boolean canSeeSky(BlockPos pos) {
        return this.getBrightness(LightLayer.SKY, pos) >= 15;
    }
}
