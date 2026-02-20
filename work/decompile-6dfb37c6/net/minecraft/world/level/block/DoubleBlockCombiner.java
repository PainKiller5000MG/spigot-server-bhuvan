package net.minecraft.world.level.block;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class DoubleBlockCombiner {

    public DoubleBlockCombiner() {}

    public static <S extends BlockEntity> DoubleBlockCombiner.NeighborCombineResult<S> combineWithNeigbour(BlockEntityType<S> entityType, Function<BlockState, DoubleBlockCombiner.BlockType> typeResolver, Function<BlockState, Direction> connectionResolver, Property<Direction> facingProperty, BlockState state, LevelAccessor level, BlockPos pos, BiPredicate<LevelAccessor, BlockPos> blockedChecker) {
        S s0 = entityType.getBlockEntity(level, pos);

        if (s0 == null) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else if (blockedChecker.test(level, pos)) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else {
            DoubleBlockCombiner.BlockType doubleblockcombiner_blocktype = (DoubleBlockCombiner.BlockType) typeResolver.apply(state);
            boolean flag = doubleblockcombiner_blocktype == DoubleBlockCombiner.BlockType.SINGLE;
            boolean flag1 = doubleblockcombiner_blocktype == DoubleBlockCombiner.BlockType.FIRST;

            if (flag) {
                return new DoubleBlockCombiner.NeighborCombineResult.Single<S>(s0);
            } else {
                BlockPos blockpos1 = pos.relative((Direction) connectionResolver.apply(state));
                BlockState blockstate1 = level.getBlockState(blockpos1);

                if (blockstate1.is(state.getBlock())) {
                    DoubleBlockCombiner.BlockType doubleblockcombiner_blocktype1 = (DoubleBlockCombiner.BlockType) typeResolver.apply(blockstate1);

                    if (doubleblockcombiner_blocktype1 != DoubleBlockCombiner.BlockType.SINGLE && doubleblockcombiner_blocktype != doubleblockcombiner_blocktype1 && blockstate1.getValue(facingProperty) == state.getValue(facingProperty)) {
                        if (blockedChecker.test(level, blockpos1)) {
                            return DoubleBlockCombiner.Combiner::acceptNone;
                        }

                        S s1 = entityType.getBlockEntity(level, blockpos1);

                        if (s1 != null) {
                            S s2 = flag1 ? s0 : s1;
                            S s3 = flag1 ? s1 : s0;

                            return new DoubleBlockCombiner.NeighborCombineResult.Double<S>(s2, s3);
                        }
                    }
                }

                return new DoubleBlockCombiner.NeighborCombineResult.Single<S>(s0);
            }
        }
    }

    public static enum BlockType {

        SINGLE, FIRST, SECOND;

        private BlockType() {}
    }

    public interface Combiner<S, T> {

        T acceptDouble(S first, S second);

        T acceptSingle(S single);

        T acceptNone();
    }

    public interface NeighborCombineResult<S> {

        <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> callback);

        public static final class Double<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {

            private final S first;
            private final S second;

            public Double(S first, S second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> callback) {
                return callback.acceptDouble(this.first, this.second);
            }
        }

        public static final class Single<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {

            private final S single;

            public Single(S single) {
                this.single = single;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> callback) {
                return callback.acceptSingle(this.single);
            }
        }
    }
}
