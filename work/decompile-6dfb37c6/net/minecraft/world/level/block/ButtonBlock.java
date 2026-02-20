package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ButtonBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final MapCodec<ButtonBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter((buttonblock) -> {
            return buttonblock.type;
        }), Codec.intRange(1, 1024).fieldOf("ticks_to_stay_pressed").forGetter((buttonblock) -> {
            return buttonblock.ticksToStayPressed;
        }), propertiesCodec()).apply(instance, ButtonBlock::new);
    });
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final BlockSetType type;
    private final int ticksToStayPressed;
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<ButtonBlock> codec() {
        return ButtonBlock.CODEC;
    }

    protected ButtonBlock(BlockSetType type, int ticksToStayPressed, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ButtonBlock.FACING, Direction.NORTH)).setValue(ButtonBlock.POWERED, false)).setValue(ButtonBlock.FACE, AttachFace.WALL));
        this.ticksToStayPressed = ticksToStayPressed;
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        VoxelShape voxelshape = Block.cube(14.0D);
        VoxelShape voxelshape1 = Block.cube(12.0D);
        Map<AttachFace, Map<Direction, VoxelShape>> map = Shapes.rotateAttachFace(Block.boxZ(6.0D, 4.0D, 8.0D, 16.0D));

        return this.getShapeForEachState((blockstate) -> {
            return Shapes.join((VoxelShape) ((Map) map.get(blockstate.getValue(ButtonBlock.FACE))).get(blockstate.getValue(ButtonBlock.FACING)), (Boolean) blockstate.getValue(ButtonBlock.POWERED) ? voxelshape : voxelshape1, BooleanOp.ONLY_FIRST);
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if ((Boolean) state.getValue(ButtonBlock.POWERED)) {
            return InteractionResult.CONSUME;
        } else {
            this.press(state, level, pos, player);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks() && !(Boolean) state.getValue(ButtonBlock.POWERED)) {
            this.press(state, level, pos, (Player) null);
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        level.setBlock(pos, (BlockState) state.setValue(ButtonBlock.POWERED, true), 3);
        this.updateNeighbours(state, level, pos);
        level.scheduleTick(pos, (Block) this, this.ticksToStayPressed);
        this.playSound(player, level, pos, true);
        level.gameEvent(player, (Holder) GameEvent.BLOCK_ACTIVATE, pos);
    }

    protected void playSound(@Nullable Player player, LevelAccessor level, BlockPos pos, boolean pressed) {
        level.playSound(pressed ? player : null, pos, this.getSound(pressed), SoundSource.BLOCKS);
    }

    protected SoundEvent getSound(boolean pressed) {
        return pressed ? this.type.buttonClickOn() : this.type.buttonClickOff();
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && (Boolean) state.getValue(ButtonBlock.POWERED)) {
            this.updateNeighbours(state, level, pos);
        }

    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(ButtonBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(ButtonBlock.POWERED) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(ButtonBlock.POWERED)) {
            this.checkPressed(state, level, pos);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide() && this.type.canButtonBeActivatedByArrows() && !(Boolean) state.getValue(ButtonBlock.POWERED)) {
            this.checkPressed(state, level, pos);
        }
    }

    protected void checkPressed(BlockState state, Level level, BlockPos pos) {
        AbstractArrow abstractarrow = this.type.canButtonBeActivatedByArrows() ? (AbstractArrow) level.getEntitiesOfClass(AbstractArrow.class, state.getShape(level, pos).bounds().move(pos)).stream().findFirst().orElse((Object) null) : null;
        boolean flag = abstractarrow != null;
        boolean flag1 = (Boolean) state.getValue(ButtonBlock.POWERED);

        if (flag != flag1) {
            level.setBlock(pos, (BlockState) state.setValue(ButtonBlock.POWERED, flag), 3);
            this.updateNeighbours(state, level, pos);
            this.playSound((Player) null, level, pos, flag);
            level.gameEvent(abstractarrow, (Holder) (flag ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE), pos);
        }

        if (flag) {
            level.scheduleTick(new BlockPos(pos), (Block) this, this.ticksToStayPressed);
        }

    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction direction = getConnectedDirection(state).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction, direction.getAxis().isHorizontal() ? Direction.UP : (Direction) state.getValue(ButtonBlock.FACING));

        level.updateNeighborsAt(pos, this, orientation);
        level.updateNeighborsAt(pos.relative(direction), this, orientation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ButtonBlock.FACING, ButtonBlock.POWERED, ButtonBlock.FACE);
    }
}
