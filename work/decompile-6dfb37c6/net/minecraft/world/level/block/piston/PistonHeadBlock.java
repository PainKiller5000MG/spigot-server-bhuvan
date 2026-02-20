package net.minecraft.world.level.block.piston;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PistonHeadBlock extends DirectionalBlock {

    public static final MapCodec<PistonHeadBlock> CODEC = simpleCodec(PistonHeadBlock::new);
    public static final EnumProperty<PistonType> TYPE = BlockStateProperties.PISTON_TYPE;
    public static final BooleanProperty SHORT = BlockStateProperties.SHORT;
    public static final int PLATFORM_THICKNESS = 4;
    private static final VoxelShape SHAPE_PLATFORM = Block.boxZ(16.0D, 0.0D, 4.0D);
    private static final Map<Direction, VoxelShape> SHAPES_SHORT = Shapes.rotateAll(Shapes.or(PistonHeadBlock.SHAPE_PLATFORM, Block.boxZ(4.0D, 4.0D, 16.0D)));
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Shapes.or(PistonHeadBlock.SHAPE_PLATFORM, Block.boxZ(4.0D, 4.0D, 20.0D)));

    @Override
    protected MapCodec<PistonHeadBlock> codec() {
        return PistonHeadBlock.CODEC;
    }

    public PistonHeadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(PistonHeadBlock.FACING, Direction.NORTH)).setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT)).setValue(PistonHeadBlock.SHORT, false));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) ((Boolean) state.getValue(PistonHeadBlock.SHORT) ? PistonHeadBlock.SHAPES_SHORT : PistonHeadBlock.SHAPES).get(state.getValue(PistonHeadBlock.FACING));
    }

    private boolean isFittingBase(BlockState armState, BlockState potentialBase) {
        Block block = armState.getValue(PistonHeadBlock.TYPE) == PistonType.DEFAULT ? Blocks.PISTON : Blocks.STICKY_PISTON;

        return potentialBase.is(block) && (Boolean) potentialBase.getValue(PistonBaseBlock.EXTENDED) && potentialBase.getValue(PistonHeadBlock.FACING) == armState.getValue(PistonHeadBlock.FACING);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            BlockPos blockpos1 = pos.relative(((Direction) state.getValue(PistonHeadBlock.FACING)).getOpposite());

            if (this.isFittingBase(state, level.getBlockState(blockpos1))) {
                level.destroyBlock(blockpos1, false);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockPos blockpos1 = pos.relative(((Direction) state.getValue(PistonHeadBlock.FACING)).getOpposite());

        if (this.isFittingBase(state, level.getBlockState(blockpos1))) {
            level.destroyBlock(blockpos1, true);
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour.getOpposite() == state.getValue(PistonHeadBlock.FACING) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.relative(((Direction) state.getValue(PistonHeadBlock.FACING)).getOpposite()));

        return this.isFittingBase(state, blockstate1) || blockstate1.is(Blocks.MOVING_PISTON) && blockstate1.getValue(PistonHeadBlock.FACING) == state.getValue(PistonHeadBlock.FACING);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (state.canSurvive(level, pos)) {
            level.neighborChanged(pos.relative(((Direction) state.getValue(PistonHeadBlock.FACING)).getOpposite()), block, ExperimentalRedstoneUtils.withFront(orientation, ((Direction) state.getValue(PistonHeadBlock.FACING)).getOpposite()));
        }

    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(PistonHeadBlock.TYPE) == PistonType.STICKY ? Blocks.STICKY_PISTON : Blocks.PISTON);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(PistonHeadBlock.FACING, rotation.rotate((Direction) state.getValue(PistonHeadBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(PistonHeadBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PistonHeadBlock.FACING, PistonHeadBlock.TYPE, PistonHeadBlock.SHORT);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
