package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class SculkVeinBlock extends MultifaceSpreadeableBlock implements SculkBehaviour {

    public static final MapCodec<SculkVeinBlock> CODEC = simpleCodec(SculkVeinBlock::new);
    private final MultifaceSpreader veinSpreader;
    private final MultifaceSpreader sameSpaceSpreader;

    @Override
    public MapCodec<SculkVeinBlock> codec() {
        return SculkVeinBlock.CODEC;
    }

    public SculkVeinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.veinSpreader = new MultifaceSpreader(new SculkVeinBlock.SculkVeinSpreaderConfig(MultifaceSpreader.DEFAULT_SPREAD_ORDER));
        this.sameSpaceSpreader = new MultifaceSpreader(new SculkVeinBlock.SculkVeinSpreaderConfig(new MultifaceSpreader.SpreadType[]{MultifaceSpreader.SpreadType.SAME_POSITION}));
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.veinSpreader;
    }

    public MultifaceSpreader getSameSpaceSpreader() {
        return this.sameSpaceSpreader;
    }

    public static boolean regrow(LevelAccessor level, BlockPos pos, BlockState existing, Collection<Direction> faces) {
        boolean flag = false;
        BlockState blockstate1 = Blocks.SCULK_VEIN.defaultBlockState();

        for (Direction direction : faces) {
            if (canAttachTo(level, pos, direction)) {
                blockstate1 = (BlockState) blockstate1.setValue(getFaceProperty(direction), true);
                flag = true;
            }
        }

        if (!flag) {
            return false;
        } else {
            if (!existing.getFluidState().isEmpty()) {
                blockstate1 = (BlockState) blockstate1.setValue(MultifaceBlock.WATERLOGGED, true);
            }

            level.setBlock(pos, blockstate1, 3);
            return true;
        }
    }

    @Override
    public void onDischarged(LevelAccessor level, BlockState state, BlockPos pos, RandomSource random) {
        if (state.is(this)) {
            for (Direction direction : SculkVeinBlock.DIRECTIONS) {
                BooleanProperty booleanproperty = getFaceProperty(direction);

                if ((Boolean) state.getValue(booleanproperty) && level.getBlockState(pos.relative(direction)).is(Blocks.SCULK)) {
                    state = (BlockState) state.setValue(booleanproperty, false);
                }
            }

            if (!hasAnyFace(state)) {
                FluidState fluidstate = level.getFluidState(pos);

                state = (fluidstate.isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState();
            }

            level.setBlock(pos, state, 3);
            SculkBehaviour.super.onDischarged(level, state, pos, random);
        }
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos, RandomSource random, SculkSpreader spreader, boolean spreadVeins) {
        return spreadVeins && this.attemptPlaceSculk(spreader, level, cursor.getPos(), random) ? cursor.getCharge() - 1 : (random.nextInt(spreader.chargeDecayRate()) == 0 ? Mth.floor((float) cursor.getCharge() * 0.5F) : cursor.getCharge());
    }

    private boolean attemptPlaceSculk(SculkSpreader spreader, LevelAccessor level, BlockPos pos, RandomSource random) {
        BlockState blockstate = level.getBlockState(pos);
        TagKey<Block> tagkey = spreader.replaceableBlocks();

        for (Direction direction : Direction.allShuffled(random)) {
            if (hasFace(blockstate, direction)) {
                BlockPos blockpos1 = pos.relative(direction);
                BlockState blockstate1 = level.getBlockState(blockpos1);

                if (blockstate1.is(tagkey)) {
                    BlockState blockstate2 = Blocks.SCULK.defaultBlockState();

                    level.setBlock(blockpos1, blockstate2, 3);
                    Block.pushEntitiesUp(blockstate1, blockstate2, level, blockpos1);
                    level.playSound((Entity) null, blockpos1, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    this.veinSpreader.spreadAll(blockstate2, level, blockpos1, spreader.isWorldGeneration());
                    Direction direction1 = direction.getOpposite();

                    for (Direction direction2 : SculkVeinBlock.DIRECTIONS) {
                        if (direction2 != direction1) {
                            BlockPos blockpos2 = blockpos1.relative(direction2);
                            BlockState blockstate3 = level.getBlockState(blockpos2);

                            if (blockstate3.is(this)) {
                                this.onDischarged(level, blockstate3, blockpos2, random);
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasSubstrateAccess(LevelAccessor level, BlockState state, BlockPos pos) {
        if (!state.is(Blocks.SCULK_VEIN)) {
            return false;
        } else {
            for (Direction direction : SculkVeinBlock.DIRECTIONS) {
                if (hasFace(state, direction) && level.getBlockState(pos.relative(direction)).is(BlockTags.SCULK_REPLACEABLE)) {
                    return true;
                }
            }

            return false;
        }
    }

    private class SculkVeinSpreaderConfig extends MultifaceSpreader.DefaultSpreaderConfig {

        private final MultifaceSpreader.SpreadType[] spreadTypes;

        public SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType... spreadTypes) {
            super(SculkVeinBlock.this);
            this.spreadTypes = spreadTypes;
        }

        @Override
        public boolean stateCanBeReplaced(BlockGetter level, BlockPos sourcePos, BlockPos placementPos, Direction placementDirection, BlockState existingState) {
            BlockState blockstate1 = level.getBlockState(placementPos.relative(placementDirection));

            if (!blockstate1.is(Blocks.SCULK) && !blockstate1.is(Blocks.SCULK_CATALYST) && !blockstate1.is(Blocks.MOVING_PISTON)) {
                if (sourcePos.distManhattan(placementPos) == 2) {
                    BlockPos blockpos2 = sourcePos.relative(placementDirection.getOpposite());

                    if (level.getBlockState(blockpos2).isFaceSturdy(level, blockpos2, placementDirection)) {
                        return false;
                    }
                }

                FluidState fluidstate = existingState.getFluidState();

                return !fluidstate.isEmpty() && !fluidstate.is(Fluids.WATER) ? false : (existingState.is(BlockTags.FIRE) ? false : existingState.canBeReplaced() || super.stateCanBeReplaced(level, sourcePos, placementPos, placementDirection, existingState));
            } else {
                return false;
            }
        }

        @Override
        public MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return this.spreadTypes;
        }

        @Override
        public boolean isOtherBlockValidAsSource(BlockState state) {
            return !state.is(Blocks.SCULK_VEIN);
        }
    }
}
