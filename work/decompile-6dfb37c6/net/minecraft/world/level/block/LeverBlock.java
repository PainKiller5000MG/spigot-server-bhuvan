package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final MapCodec<LeverBlock> CODEC = simpleCodec(LeverBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<LeverBlock> codec() {
        return LeverBlock.CODEC;
    }

    protected LeverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(LeverBlock.FACING, Direction.NORTH)).setValue(LeverBlock.POWERED, false)).setValue(LeverBlock.FACE, AttachFace.WALL));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<AttachFace, Map<Direction, VoxelShape>> map = Shapes.rotateAttachFace(Block.boxZ(6.0D, 8.0D, 10.0D, 16.0D));

        return this.getShapeForEachState((blockstate) -> {
            return (VoxelShape) ((Map) map.get(blockstate.getValue(LeverBlock.FACE))).get(blockstate.getValue(LeverBlock.FACING));
        }, new Property[]{LeverBlock.POWERED});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState stateBefore, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            BlockState blockstate1 = (BlockState) stateBefore.cycle(LeverBlock.POWERED);

            if ((Boolean) blockstate1.getValue(LeverBlock.POWERED)) {
                makeParticle(blockstate1, level, pos, 1.0F);
            }
        } else {
            this.pull(stateBefore, level, pos, (Player) null);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks()) {
            this.pull(state, level, pos, (Player) null);
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    public void pull(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        state = (BlockState) state.cycle(LeverBlock.POWERED);
        level.setBlock(pos, state, 3);
        this.updateNeighbours(state, level, pos);
        playSound(player, level, pos, state);
        level.gameEvent(player, (Holder) ((Boolean) state.getValue(LeverBlock.POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE), pos);
    }

    protected static void playSound(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState stateAfter) {
        float f = (Boolean) stateAfter.getValue(LeverBlock.POWERED) ? 0.6F : 0.5F;

        level.playSound(player, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
    }

    private static void makeParticle(BlockState state, LevelAccessor level, BlockPos pos, float scale) {
        Direction direction = ((Direction) state.getValue(LeverBlock.FACING)).getOpposite();
        Direction direction1 = getConnectedDirection(state).getOpposite();
        double d0 = (double) pos.getX() + 0.5D + 0.1D * (double) direction.getStepX() + 0.2D * (double) direction1.getStepX();
        double d1 = (double) pos.getY() + 0.5D + 0.1D * (double) direction.getStepY() + 0.2D * (double) direction1.getStepY();
        double d2 = (double) pos.getZ() + 0.5D + 0.1D * (double) direction.getStepZ() + 0.2D * (double) direction1.getStepZ();

        level.addParticle(new DustParticleOptions(16711680, scale), d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(LeverBlock.POWERED) && random.nextFloat() < 0.25F) {
            makeParticle(state, level, pos, 0.5F);
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && (Boolean) state.getValue(LeverBlock.POWERED)) {
            this.updateNeighbours(state, level, pos);
        }

    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LeverBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LeverBlock.POWERED) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction direction = getConnectedDirection(state).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction, direction.getAxis().isHorizontal() ? Direction.UP : (Direction) state.getValue(LeverBlock.FACING));

        level.updateNeighborsAt(pos, this, orientation);
        level.updateNeighborsAt(pos.relative(direction), this, orientation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LeverBlock.FACE, LeverBlock.FACING, LeverBlock.POWERED);
    }
}
