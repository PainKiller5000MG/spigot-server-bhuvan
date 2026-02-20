package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;
import org.jspecify.annotations.Nullable;

public class ReplaceBlobsFeature extends Feature<ReplaceSphereConfiguration> {

    public ReplaceBlobsFeature(Codec<ReplaceSphereConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ReplaceSphereConfiguration> context) {
        ReplaceSphereConfiguration replacesphereconfiguration = context.config();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        Block block = replacesphereconfiguration.targetState.getBlock();
        BlockPos blockpos = findTarget(worldgenlevel, context.origin().mutable().clamp(Direction.Axis.Y, worldgenlevel.getMinY() + 1, worldgenlevel.getMaxY()), block);

        if (blockpos == null) {
            return false;
        } else {
            int i = replacesphereconfiguration.radius().sample(randomsource);
            int j = replacesphereconfiguration.radius().sample(randomsource);
            int k = replacesphereconfiguration.radius().sample(randomsource);
            int l = Math.max(i, Math.max(j, k));
            boolean flag = false;

            for (BlockPos blockpos1 : BlockPos.withinManhattan(blockpos, i, j, k)) {
                if (blockpos1.distManhattan(blockpos) > l) {
                    break;
                }

                BlockState blockstate = worldgenlevel.getBlockState(blockpos1);

                if (blockstate.is(block)) {
                    this.setBlock(worldgenlevel, blockpos1, replacesphereconfiguration.replaceState);
                    flag = true;
                }
            }

            return flag;
        }
    }

    private static @Nullable BlockPos findTarget(LevelAccessor level, BlockPos.MutableBlockPos cursor, Block target) {
        while (cursor.getY() > level.getMinY() + 1) {
            BlockState blockstate = level.getBlockState(cursor);

            if (blockstate.is(target)) {
                return cursor;
            }

            cursor.move(Direction.DOWN);
        }

        return null;
    }
}
