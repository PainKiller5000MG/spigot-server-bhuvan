package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PitcherCropBlock extends DoublePlantBlock implements BonemealableBlock {

    public static final MapCodec<PitcherCropBlock> CODEC = simpleCodec(PitcherCropBlock::new);
    public static final int MAX_AGE = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final EnumProperty<DoubleBlockHalf> HALF = DoublePlantBlock.HALF;
    private static final int DOUBLE_PLANT_AGE_INTERSECTION = 3;
    private static final int BONEMEAL_INCREASE = 1;
    private static final VoxelShape SHAPE_BULB = Block.column(6.0D, -1.0D, 3.0D);
    private static final VoxelShape SHAPE_CROP = Block.column(10.0D, -1.0D, 5.0D);
    private final Function<BlockState, VoxelShape> shapes = this.makeShapes();

    @Override
    public MapCodec<PitcherCropBlock> codec() {
        return PitcherCropBlock.CODEC;
    }

    public PitcherCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        int[] aint = new int[]{0, 9, 11, 22, 26};

        return this.getShapeForEachState((blockstate) -> {
            int i = ((Integer) blockstate.getValue(PitcherCropBlock.AGE) == 0 ? 4 : 6) + aint[(Integer) blockstate.getValue(PitcherCropBlock.AGE)];
            int j = (Integer) blockstate.getValue(PitcherCropBlock.AGE) == 0 ? 6 : 10;
            VoxelShape voxelshape;

            switch ((DoubleBlockHalf) blockstate.getValue(PitcherCropBlock.HALF)) {
                case LOWER:
                    voxelshape = Block.column((double) j, -1.0D, (double) Math.min(16, -1 + i));
                    break;
                case UPPER:
                    voxelshape = Block.column((double) j, 0.0D, (double) Math.max(0, -1 + i - 16));
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return voxelshape;
        });
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER ? ((Integer) state.getValue(PitcherCropBlock.AGE) == 0 ? PitcherCropBlock.SHAPE_BULB : PitcherCropBlock.SHAPE_CROP) : Shapes.empty();
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return isDouble((Integer) state.getValue(PitcherCropBlock.AGE)) ? super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random) : (state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isLower(state) && !sufficientLight(level, pos) ? false : super.canSurvive(state, level, pos);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PitcherCropBlock.AGE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel serverlevel) {
            if (entity instanceof Ravager && (Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                serverlevel.destroyBlock(pos, true, entity);
            }
        }

    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {}

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER && !this.isMaxAge(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float f = CropBlock.getGrowthSpeed(this, level, pos);
        boolean flag = random.nextInt((int) (25.0F / f) + 1) == 0;

        if (flag) {
            this.grow(level, state, pos, 1);
        }

    }

    private void grow(ServerLevel level, BlockState lowerState, BlockPos lowerPos, int increase) {
        int j = Math.min((Integer) lowerState.getValue(PitcherCropBlock.AGE) + increase, 4);

        if (this.canGrow(level, lowerPos, lowerState, j)) {
            BlockState blockstate1 = (BlockState) lowerState.setValue(PitcherCropBlock.AGE, j);

            level.setBlock(lowerPos, blockstate1, 2);
            if (isDouble(j)) {
                level.setBlock(lowerPos.above(), (BlockState) blockstate1.setValue(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER), 3);
            }

        }
    }

    private static boolean canGrowInto(LevelReader level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        return blockstate.isAir() || blockstate.is(Blocks.PITCHER_CROP);
    }

    private static boolean sufficientLight(LevelReader level, BlockPos pos) {
        return CropBlock.hasSufficientLight(level, pos);
    }

    private static boolean isLower(BlockState state) {
        return state.is(Blocks.PITCHER_CROP) && state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isDouble(int age) {
        return age >= 3;
    }

    private boolean canGrow(LevelReader level, BlockPos lowerPos, BlockState lowerState, int newAge) {
        return !this.isMaxAge(lowerState) && sufficientLight(level, lowerPos) && (!isDouble(newAge) || canGrowInto(level, lowerPos.above()));
    }

    private boolean isMaxAge(BlockState state) {
        return (Integer) state.getValue(PitcherCropBlock.AGE) >= 4;
    }

    private PitcherCropBlock.@Nullable PosAndState getLowerHalf(LevelReader level, BlockPos pos, BlockState state) {
        if (isLower(state)) {
            return new PitcherCropBlock.PosAndState(pos, state);
        } else {
            BlockPos blockpos1 = pos.below();
            BlockState blockstate1 = level.getBlockState(blockpos1);

            return isLower(blockstate1) ? new PitcherCropBlock.PosAndState(blockpos1, blockstate1) : null;
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        PitcherCropBlock.PosAndState pitchercropblock_posandstate = this.getLowerHalf(level, pos, state);

        return pitchercropblock_posandstate == null ? false : this.canGrow(level, pitchercropblock_posandstate.pos, pitchercropblock_posandstate.state, (Integer) pitchercropblock_posandstate.state.getValue(PitcherCropBlock.AGE) + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        PitcherCropBlock.PosAndState pitchercropblock_posandstate = this.getLowerHalf(level, pos, state);

        if (pitchercropblock_posandstate != null) {
            this.grow(level, pitchercropblock_posandstate.state, pitchercropblock_posandstate.pos, 1);
        }
    }

    private static record PosAndState(BlockPos pos, BlockState state) {

    }
}
