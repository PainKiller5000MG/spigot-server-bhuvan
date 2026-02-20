package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class MultifaceSpreader {

    public static final MultifaceSpreader.SpreadType[] DEFAULT_SPREAD_ORDER = new MultifaceSpreader.SpreadType[]{MultifaceSpreader.SpreadType.SAME_POSITION, MultifaceSpreader.SpreadType.SAME_PLANE, MultifaceSpreader.SpreadType.WRAP_AROUND};
    private final MultifaceSpreader.SpreadConfig config;

    public MultifaceSpreader(MultifaceBlock multifaceBlock) {
        this((MultifaceSpreader.SpreadConfig) (new MultifaceSpreader.DefaultSpreaderConfig(multifaceBlock)));
    }

    public MultifaceSpreader(MultifaceSpreader.SpreadConfig config) {
        this.config = config;
    }

    public boolean canSpreadInAnyDirection(BlockState state, BlockGetter level, BlockPos pos, Direction startingFace) {
        return Direction.stream().anyMatch((direction1) -> {
            MultifaceSpreader.SpreadConfig multifacespreader_spreadconfig = this.config;

            Objects.requireNonNull(this.config);
            return this.getSpreadFromFaceTowardDirection(state, level, pos, startingFace, direction1, multifacespreader_spreadconfig::canSpreadInto).isPresent();
        });
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromRandomFaceTowardRandomDirection(BlockState state, LevelAccessor level, BlockPos pos, RandomSource random) {
        return (Optional) Direction.allShuffled(random).stream().filter((direction) -> {
            return this.config.canSpreadFrom(state, direction);
        }).map((direction) -> {
            return this.spreadFromFaceTowardRandomDirection(state, level, pos, direction, random, false);
        }).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    public long spreadAll(BlockState state, LevelAccessor level, BlockPos pos, boolean postProcess) {
        return (Long) Direction.stream().filter((direction) -> {
            return this.config.canSpreadFrom(state, direction);
        }).map((direction) -> {
            return this.spreadFromFaceTowardAllDirections(state, level, pos, direction, postProcess);
        }).reduce(0L, Long::sum);
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardRandomDirection(BlockState state, LevelAccessor level, BlockPos pos, Direction startingFace, RandomSource random, boolean postProcess) {
        return (Optional) Direction.allShuffled(random).stream().map((direction1) -> {
            return this.spreadFromFaceTowardDirection(state, level, pos, startingFace, direction1, postProcess);
        }).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    private long spreadFromFaceTowardAllDirections(BlockState state, LevelAccessor level, BlockPos pos, Direction startingFace, boolean postProcess) {
        return Direction.stream().map((direction1) -> {
            return this.spreadFromFaceTowardDirection(state, level, pos, startingFace, direction1, postProcess);
        }).filter(Optional::isPresent).count();
    }

    @VisibleForTesting
    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(BlockState state, LevelAccessor level, BlockPos pos, Direction fromFace, Direction spreadDirection, boolean postProcess) {
        MultifaceSpreader.SpreadConfig multifacespreader_spreadconfig = this.config;

        Objects.requireNonNull(this.config);
        return this.getSpreadFromFaceTowardDirection(state, level, pos, fromFace, spreadDirection, multifacespreader_spreadconfig::canSpreadInto).flatMap((multifacespreader_spreadpos) -> {
            return this.spreadToFace(level, multifacespreader_spreadpos, postProcess);
        });
    }

    public Optional<MultifaceSpreader.SpreadPos> getSpreadFromFaceTowardDirection(BlockState state, BlockGetter level, BlockPos pos, Direction startingFace, Direction spreadDirection, MultifaceSpreader.SpreadPredicate canSpreadInto) {
        if (spreadDirection.getAxis() == startingFace.getAxis()) {
            return Optional.empty();
        } else if (this.config.isOtherBlockValidAsSource(state) || this.config.hasFace(state, startingFace) && !this.config.hasFace(state, spreadDirection)) {
            for (MultifaceSpreader.SpreadType multifacespreader_spreadtype : this.config.getSpreadTypes()) {
                MultifaceSpreader.SpreadPos multifacespreader_spreadpos = multifacespreader_spreadtype.getSpreadPos(pos, spreadDirection, startingFace);

                if (canSpreadInto.test(level, pos, multifacespreader_spreadpos)) {
                    return Optional.of(multifacespreader_spreadpos);
                }
            }

            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadToFace(LevelAccessor level, MultifaceSpreader.SpreadPos spreadPos, boolean postProcess) {
        BlockState blockstate = level.getBlockState(spreadPos.pos());

        return this.config.placeBlock(level, spreadPos, blockstate, postProcess) ? Optional.of(spreadPos) : Optional.empty();
    }

    public static record SpreadPos(BlockPos pos, Direction face) {

    }

    public interface SpreadConfig {

        @Nullable
        BlockState getStateForPlacement(BlockState oldState, BlockGetter level, BlockPos placementPos, Direction placementDirection);

        boolean canSpreadInto(BlockGetter level, BlockPos sourcePos, MultifaceSpreader.SpreadPos spreadPos);

        default MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return MultifaceSpreader.DEFAULT_SPREAD_ORDER;
        }

        default boolean hasFace(BlockState state, Direction face) {
            return MultifaceBlock.hasFace(state, face);
        }

        default boolean isOtherBlockValidAsSource(BlockState state) {
            return false;
        }

        default boolean canSpreadFrom(BlockState state, Direction face) {
            return this.isOtherBlockValidAsSource(state) || this.hasFace(state, face);
        }

        default boolean placeBlock(LevelAccessor level, MultifaceSpreader.SpreadPos spreadPos, BlockState oldState, boolean postProcess) {
            BlockState blockstate1 = this.getStateForPlacement(oldState, level, spreadPos.pos(), spreadPos.face());

            if (blockstate1 != null) {
                if (postProcess) {
                    level.getChunk(spreadPos.pos()).markPosForPostprocessing(spreadPos.pos());
                }

                return level.setBlock(spreadPos.pos(), blockstate1, 2);
            } else {
                return false;
            }
        }
    }

    public static class DefaultSpreaderConfig implements MultifaceSpreader.SpreadConfig {

        protected MultifaceBlock block;

        public DefaultSpreaderConfig(MultifaceBlock block) {
            this.block = block;
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockState oldState, BlockGetter level, BlockPos placementPos, Direction placementDirection) {
            return this.block.getStateForPlacement(oldState, level, placementPos, placementDirection);
        }

        protected boolean stateCanBeReplaced(BlockGetter level, BlockPos sourcePos, BlockPos placementPos, Direction placementDirection, BlockState existingState) {
            return existingState.isAir() || existingState.is(this.block) || existingState.is(Blocks.WATER) && existingState.getFluidState().isSource();
        }

        @Override
        public boolean canSpreadInto(BlockGetter level, BlockPos sourcePos, MultifaceSpreader.SpreadPos spreadPos) {
            BlockState blockstate = level.getBlockState(spreadPos.pos());

            return this.stateCanBeReplaced(level, sourcePos, spreadPos.pos(), spreadPos.face(), blockstate) && this.block.isValidStateForPlacement(level, blockstate, spreadPos.pos(), spreadPos.face());
        }
    }

    public static enum SpreadType {

        SAME_POSITION {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace) {
                return new MultifaceSpreader.SpreadPos(pos, spreadDirection);
            }
        },
        SAME_PLANE {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace) {
                return new MultifaceSpreader.SpreadPos(pos.relative(spreadDirection), fromFace);
            }
        },
        WRAP_AROUND {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace) {
                return new MultifaceSpreader.SpreadPos(pos.relative(spreadDirection).relative(fromFace), spreadDirection.getOpposite());
            }
        };

        private SpreadType() {}

        public abstract MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace);
    }

    @FunctionalInterface
    public interface SpreadPredicate {

        boolean test(BlockGetter level, BlockPos sourcePos, MultifaceSpreader.SpreadPos spreadPos);
    }
}
