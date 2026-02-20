package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ChorusFlowerBlock extends Block {

    public static final MapCodec<ChorusFlowerBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("plant").forGetter((chorusflowerblock) -> {
            return chorusflowerblock.plant;
        }), propertiesCodec()).apply(instance, ChorusFlowerBlock::new);
    });
    public static final int DEAD_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    private static final VoxelShape SHAPE_BLOCK_SUPPORT = Block.column(14.0D, 0.0D, 15.0D);
    private final Block plant;

    @Override
    public MapCodec<ChorusFlowerBlock> codec() {
        return ChorusFlowerBlock.CODEC;
    }

    protected ChorusFlowerBlock(Block plant, BlockBehaviour.Properties properties) {
        super(properties);
        this.plant = plant;
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(ChorusFlowerBlock.AGE, 0));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }

    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(ChorusFlowerBlock.AGE) < 5;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return ChorusFlowerBlock.SHAPE_BLOCK_SUPPORT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockpos1 = pos.above();

        if (level.isEmptyBlock(blockpos1) && blockpos1.getY() <= level.getMaxY()) {
            int i = (Integer) state.getValue(ChorusFlowerBlock.AGE);

            if (i < 5) {
                boolean flag = false;
                boolean flag1 = false;
                BlockState blockstate1 = level.getBlockState(pos.below());

                if (blockstate1.is(Blocks.END_STONE)) {
                    flag = true;
                } else if (blockstate1.is(this.plant)) {
                    int j = 1;

                    for (int k = 0; k < 4; ++k) {
                        BlockState blockstate2 = level.getBlockState(pos.below(j + 1));

                        if (!blockstate2.is(this.plant)) {
                            if (blockstate2.is(Blocks.END_STONE)) {
                                flag1 = true;
                            }
                            break;
                        }

                        ++j;
                    }

                    if (j < 2 || j <= random.nextInt(flag1 ? 5 : 4)) {
                        flag = true;
                    }
                } else if (blockstate1.isAir()) {
                    flag = true;
                }

                if (flag && allNeighborsEmpty(level, blockpos1, (Direction) null) && level.isEmptyBlock(pos.above(2))) {
                    level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), 2);
                    this.placeGrownFlower(level, blockpos1, i);
                } else if (i < 4) {
                    int l = random.nextInt(4);

                    if (flag1) {
                        ++l;
                    }

                    boolean flag2 = false;

                    for (int i1 = 0; i1 < l; ++i1) {
                        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos blockpos2 = pos.relative(direction);

                        if (level.isEmptyBlock(blockpos2) && level.isEmptyBlock(blockpos2.below()) && allNeighborsEmpty(level, blockpos2, direction.getOpposite())) {
                            this.placeGrownFlower(level, blockpos2, i + 1);
                            flag2 = true;
                        }
                    }

                    if (flag2) {
                        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), 2);
                    } else {
                        this.placeDeadFlower(level, pos);
                    }
                } else {
                    this.placeDeadFlower(level, pos);
                }

            }
        }
    }

    private void placeGrownFlower(Level level, BlockPos pos, int age) {
        level.setBlock(pos, (BlockState) this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, age), 2);
        level.levelEvent(1033, pos, 0);
    }

    private void placeDeadFlower(Level level, BlockPos pos) {
        level.setBlock(pos, (BlockState) this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5), 2);
        level.levelEvent(1034, pos, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction ignore) {
        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            if (direction1 != ignore && !level.isEmptyBlock(pos.relative(direction1))) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour != Direction.UP && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.below());

        if (!blockstate1.is(this.plant) && !blockstate1.is(Blocks.END_STONE)) {
            if (!blockstate1.isAir()) {
                return false;
            } else {
                boolean flag = false;

                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState blockstate2 = level.getBlockState(pos.relative(direction));

                    if (blockstate2.is(this.plant)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (!blockstate2.isAir()) {
                        return false;
                    }
                }

                return flag;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ChorusFlowerBlock.AGE);
    }

    public static void generatePlant(LevelAccessor level, BlockPos target, RandomSource random, int maxHorizontalSpread) {
        level.setBlock(target, ChorusPlantBlock.getStateWithConnections(level, target, Blocks.CHORUS_PLANT.defaultBlockState()), 2);
        growTreeRecursive(level, target, random, target, maxHorizontalSpread, 0);
    }

    private static void growTreeRecursive(LevelAccessor level, BlockPos current, RandomSource random, BlockPos startPos, int maxHorizontalSpread, int depth) {
        Block block = Blocks.CHORUS_PLANT;
        int k = random.nextInt(4) + 1;

        if (depth == 0) {
            ++k;
        }

        for (int l = 0; l < k; ++l) {
            BlockPos blockpos2 = current.above(l + 1);

            if (!allNeighborsEmpty(level, blockpos2, (Direction) null)) {
                return;
            }

            level.setBlock(blockpos2, ChorusPlantBlock.getStateWithConnections(level, blockpos2, block.defaultBlockState()), 2);
            level.setBlock(blockpos2.below(), ChorusPlantBlock.getStateWithConnections(level, blockpos2.below(), block.defaultBlockState()), 2);
        }

        boolean flag = false;

        if (depth < 4) {
            int i1 = random.nextInt(4);

            if (depth == 0) {
                ++i1;
            }

            for (int j1 = 0; j1 < i1; ++j1) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockpos3 = current.above(k).relative(direction);

                if (Math.abs(blockpos3.getX() - startPos.getX()) < maxHorizontalSpread && Math.abs(blockpos3.getZ() - startPos.getZ()) < maxHorizontalSpread && level.isEmptyBlock(blockpos3) && level.isEmptyBlock(blockpos3.below()) && allNeighborsEmpty(level, blockpos3, direction.getOpposite())) {
                    flag = true;
                    level.setBlock(blockpos3, ChorusPlantBlock.getStateWithConnections(level, blockpos3, block.defaultBlockState()), 2);
                    level.setBlock(blockpos3.relative(direction.getOpposite()), ChorusPlantBlock.getStateWithConnections(level, blockpos3.relative(direction.getOpposite()), block.defaultBlockState()), 2);
                    growTreeRecursive(level, blockpos3, random, startPos, maxHorizontalSpread, depth + 1);
                }
            }
        }

        if (!flag) {
            level.setBlock(current.above(k), (BlockState) Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5), 2);
        }

    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
        BlockPos blockpos = blockHit.getBlockPos();

        if (level instanceof ServerLevel serverlevel) {
            if (projectile.mayInteract(serverlevel, blockpos) && projectile.mayBreak(serverlevel)) {
                level.destroyBlock(blockpos, true, projectile);
            }
        }

    }
}
