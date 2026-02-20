package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class TrapDoorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<TrapDoorBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter((trapdoorblock) -> {
            return trapdoorblock.type;
        }), propertiesCodec()).apply(instance, TrapDoorBlock::new);
    });
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.boxZ(16.0D, 13.0D, 16.0D));
    private final BlockSetType type;

    @Override
    public MapCodec<? extends TrapDoorBlock> codec() {
        return TrapDoorBlock.CODEC;
    }

    protected TrapDoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(TrapDoorBlock.FACING, Direction.NORTH)).setValue(TrapDoorBlock.OPEN, false)).setValue(TrapDoorBlock.HALF, Half.BOTTOM)).setValue(TrapDoorBlock.POWERED, false)).setValue(TrapDoorBlock.WATERLOGGED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) TrapDoorBlock.SHAPES.get((Boolean) state.getValue(TrapDoorBlock.OPEN) ? state.getValue(TrapDoorBlock.FACING) : (state.getValue(TrapDoorBlock.HALF) == Half.TOP ? Direction.DOWN : Direction.UP));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        switch (type) {
            case LAND:
                return (Boolean) state.getValue(TrapDoorBlock.OPEN);
            case WATER:
                return (Boolean) state.getValue(TrapDoorBlock.WATERLOGGED);
            case AIR:
                return (Boolean) state.getValue(TrapDoorBlock.OPEN);
            default:
                return false;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!this.type.canOpenByHand()) {
            return InteractionResult.PASS;
        } else {
            this.toggle(state, level, pos, player);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks() && this.type.canOpenByWindCharge() && !(Boolean) state.getValue(TrapDoorBlock.POWERED)) {
            this.toggle(state, level, pos, (Player) null);
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    private void toggle(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        BlockState blockstate1 = (BlockState) state.cycle(TrapDoorBlock.OPEN);

        level.setBlock(pos, blockstate1, 2);
        if ((Boolean) blockstate1.getValue(TrapDoorBlock.WATERLOGGED)) {
            level.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        this.playSound(player, level, pos, (Boolean) blockstate1.getValue(TrapDoorBlock.OPEN));
    }

    protected void playSound(@Nullable Player player, Level level, BlockPos pos, boolean opening) {
        level.playSound(player, pos, opening ? this.type.trapdoorOpen() : this.type.trapdoorClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(player, (Holder) (opening ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean flag1 = level.hasNeighborSignal(pos);

            if (flag1 != (Boolean) state.getValue(TrapDoorBlock.POWERED)) {
                if ((Boolean) state.getValue(TrapDoorBlock.OPEN) != flag1) {
                    state = (BlockState) state.setValue(TrapDoorBlock.OPEN, flag1);
                    this.playSound((Player) null, level, pos, flag1);
                }

                level.setBlock(pos, (BlockState) state.setValue(TrapDoorBlock.POWERED, flag1), 2);
                if ((Boolean) state.getValue(TrapDoorBlock.WATERLOGGED)) {
                    level.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
                }
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = this.defaultBlockState();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        Direction direction = context.getClickedFace();

        if (!context.replacingClickedOnBlock() && direction.getAxis().isHorizontal()) {
            blockstate = (BlockState) ((BlockState) blockstate.setValue(TrapDoorBlock.FACING, direction)).setValue(TrapDoorBlock.HALF, context.getClickLocation().y - (double) context.getClickedPos().getY() > 0.5D ? Half.TOP : Half.BOTTOM);
        } else {
            blockstate = (BlockState) ((BlockState) blockstate.setValue(TrapDoorBlock.FACING, context.getHorizontalDirection().getOpposite())).setValue(TrapDoorBlock.HALF, direction == Direction.UP ? Half.BOTTOM : Half.TOP);
        }

        if (context.getLevel().hasNeighborSignal(context.getClickedPos())) {
            blockstate = (BlockState) ((BlockState) blockstate.setValue(TrapDoorBlock.OPEN, true)).setValue(TrapDoorBlock.POWERED, true);
        }

        return (BlockState) blockstate.setValue(TrapDoorBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TrapDoorBlock.FACING, TrapDoorBlock.OPEN, TrapDoorBlock.HALF, TrapDoorBlock.POWERED, TrapDoorBlock.WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(TrapDoorBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(TrapDoorBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    protected BlockSetType getType() {
        return this.type;
    }
}
