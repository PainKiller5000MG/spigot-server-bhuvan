package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndPlatformFeature extends Feature<NoneFeatureConfiguration> {

    public EndPlatformFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        createEndPlatform(context.level(), context.origin(), false);
        return true;
    }

    public static void createEndPlatform(ServerLevelAccessor newLevel, BlockPos origin, boolean dropResources) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();

        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                for (int k = -1; k < 3; ++k) {
                    BlockPos blockpos1 = blockpos_mutableblockpos.set(origin).move(j, k, i);
                    Block block = k == -1 ? Blocks.OBSIDIAN : Blocks.AIR;

                    if (!newLevel.getBlockState(blockpos1).is(block)) {
                        if (dropResources) {
                            newLevel.destroyBlock(blockpos1, true, (Entity) null);
                        }

                        newLevel.setBlock(blockpos1, block.defaultBlockState(), 3);
                    }
                }
            }
        }

    }
}
