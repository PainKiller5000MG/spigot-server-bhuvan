package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceBlockConfiguration;

public class ReplaceBlockFeature extends Feature<ReplaceBlockConfiguration> {

    public ReplaceBlockFeature(Codec<ReplaceBlockConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ReplaceBlockConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        ReplaceBlockConfiguration replaceblockconfiguration = context.config();

        for (OreConfiguration.TargetBlockState oreconfiguration_targetblockstate : replaceblockconfiguration.targetStates) {
            if (oreconfiguration_targetblockstate.target.test(worldgenlevel.getBlockState(blockpos), context.random())) {
                worldgenlevel.setBlock(blockpos, oreconfiguration_targetblockstate.state, 2);
                break;
            }
        }

        return true;
    }
}
