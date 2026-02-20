package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class VaultBlock extends BaseEntityBlock {

    public static final MapCodec<VaultBlock> CODEC = simpleCodec(VaultBlock::new);
    public static final Property<VaultState> STATE = BlockStateProperties.VAULT_STATE;
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OMINOUS = BlockStateProperties.OMINOUS;

    @Override
    public MapCodec<VaultBlock> codec() {
        return VaultBlock.CODEC;
    }

    public VaultBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(VaultBlock.FACING, Direction.NORTH)).setValue(VaultBlock.STATE, VaultState.INACTIVE)).setValue(VaultBlock.OMINOUS, false));
    }

    @Override
    public InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!itemStack.isEmpty() && state.getValue(VaultBlock.STATE) == VaultState.ACTIVE) {
            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;
                BlockEntity blockentity = serverlevel.getBlockEntity(pos);

                if (!(blockentity instanceof VaultBlockEntity)) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }

                VaultBlockEntity vaultblockentity = (VaultBlockEntity) blockentity;

                VaultBlockEntity.Server.tryInsertKey(serverlevel, pos, state, vaultblockentity.getConfig(), vaultblockentity.getServerData(), vaultblockentity.getSharedData(), player, itemStack);
            }

            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VaultBlock.FACING, VaultBlock.STATE, VaultBlock.OMINOUS);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        BlockEntityTicker blockentityticker;

        if (level instanceof ServerLevel serverlevel) {
            blockentityticker = createTickerHelper(type, BlockEntityType.VAULT, (level1, blockpos, blockstate1, vaultblockentity) -> {
                VaultBlockEntity.Server.tick(serverlevel, blockpos, blockstate1, vaultblockentity.getConfig(), vaultblockentity.getServerData(), vaultblockentity.getSharedData());
            });
        } else {
            blockentityticker = createTickerHelper(type, BlockEntityType.VAULT, (level1, blockpos, blockstate1, vaultblockentity) -> {
                VaultBlockEntity.Client.tick(level1, blockpos, blockstate1, vaultblockentity.getClientData(), vaultblockentity.getSharedData());
            });
        }

        return blockentityticker;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(VaultBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(VaultBlock.FACING, rotation.rotate((Direction) state.getValue(VaultBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(VaultBlock.FACING)));
    }
}
