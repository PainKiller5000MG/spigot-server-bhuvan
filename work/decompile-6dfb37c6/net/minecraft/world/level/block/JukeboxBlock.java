package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class JukeboxBlock extends BaseEntityBlock {

    public static final MapCodec<JukeboxBlock> CODEC = simpleCodec(JukeboxBlock::new);
    public static final BooleanProperty HAS_RECORD = BlockStateProperties.HAS_RECORD;

    @Override
    public MapCodec<JukeboxBlock> codec() {
        return JukeboxBlock.CODEC;
    }

    protected JukeboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(JukeboxBlock.HAS_RECORD, false));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        TypedEntityData<BlockEntityType<?>> typedentitydata = (TypedEntityData) itemStack.get(DataComponents.BLOCK_ENTITY_DATA);

        if (typedentitydata != null && typedentitydata.contains("RecordItem")) {
            level.setBlock(pos, (BlockState) state.setValue(JukeboxBlock.HAS_RECORD, true), 2);
        }

    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if ((Boolean) state.getValue(JukeboxBlock.HAS_RECORD)) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof JukeboxBlockEntity) {
                JukeboxBlockEntity jukeboxblockentity = (JukeboxBlockEntity) blockentity;

                jukeboxblockentity.popOutTheItem();
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if ((Boolean) state.getValue(JukeboxBlock.HAS_RECORD)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            ItemStack itemstack1 = player.getItemInHand(hand);
            InteractionResult interactionresult = JukeboxPlayable.tryInsertIntoJukebox(level, pos, itemstack1, player);

            return (InteractionResult) (!interactionresult.consumesAction() ? InteractionResult.TRY_WITH_EMPTY_HAND : interactionresult);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new JukeboxBlockEntity(worldPosition, blockState);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof JukeboxBlockEntity jukeboxblockentity) {
            if (jukeboxblockentity.getSongPlayer().isPlaying()) {
                return 15;
            }
        }

        return 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof JukeboxBlockEntity jukeboxblockentity) {
            return jukeboxblockentity.getComparatorOutput();
        } else {
            return 0;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JukeboxBlock.HAS_RECORD);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return (Boolean) blockState.getValue(JukeboxBlock.HAS_RECORD) ? createTickerHelper(type, BlockEntityType.JUKEBOX, JukeboxBlockEntity::tick) : null;
    }
}
