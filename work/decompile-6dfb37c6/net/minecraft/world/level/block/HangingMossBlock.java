package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HangingMossBlock extends Block implements BonemealableBlock {

    public static final MapCodec<HangingMossBlock> CODEC = simpleCodec(HangingMossBlock::new);
    private static final VoxelShape SHAPE_BASE = Block.column(14.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_TIP = Block.column(14.0D, 2.0D, 16.0D);
    public static final BooleanProperty TIP = BlockStateProperties.TIP;

    @Override
    public MapCodec<HangingMossBlock> codec() {
        return HangingMossBlock.CODEC;
    }

    public HangingMossBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(HangingMossBlock.TIP, true));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(HangingMossBlock.TIP) ? HangingMossBlock.SHAPE_TIP : HangingMossBlock.SHAPE_BASE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(500) == 0) {
            BlockState blockstate1 = level.getBlockState(pos.above());

            if (blockstate1.is(BlockTags.PALE_OAK_LOGS) || blockstate1.is(Blocks.PALE_OAK_LEAVES)) {
                level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), SoundEvents.PALE_HANGING_MOSS_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }

    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.canStayAtPosition(level, pos);
    }

    private boolean canStayAtPosition(BlockGetter level, BlockPos pos) {
        BlockPos blockpos1 = pos.relative(Direction.UP);
        BlockState blockstate = level.getBlockState(blockpos1);

        return MultifaceBlock.canAttachTo(level, Direction.UP, blockpos1, blockstate) || blockstate.is(Blocks.PALE_HANGING_MOSS);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!this.canStayAtPosition(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        return (BlockState) state.setValue(HangingMossBlock.TIP, !level.getBlockState(pos.below()).is(this));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.canStayAtPosition(level, pos)) {
            level.destroyBlock(pos, true);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HangingMossBlock.TIP);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return this.canGrowInto(level.getBlockState(this.getTip(level, pos).below()));
    }

    private boolean canGrowInto(BlockState state) {
        return state.isAir();
    }

    public BlockPos getTip(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        BlockState blockstate;

        do {
            blockpos_mutableblockpos.move(Direction.DOWN);
            blockstate = level.getBlockState(blockpos_mutableblockpos);
        } while (blockstate.is(this));

        return blockpos_mutableblockpos.relative(Direction.UP).immutable();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockpos1 = this.getTip(level, pos).below();

        if (this.canGrowInto(level.getBlockState(blockpos1))) {
            level.setBlockAndUpdate(blockpos1, (BlockState) state.setValue(HangingMossBlock.TIP, true));
        }
    }
}
