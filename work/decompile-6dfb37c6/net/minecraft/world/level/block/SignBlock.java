package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class SignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.column(8.0D, 0.0D, 16.0D);
    private final WoodType type;

    protected SignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    protected abstract MapCodec<? extends SignBlock> codec();

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(SignBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SignBlock.SHAPE;
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new SignBlockEntity(worldPosition, blockState);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof SignBlockEntity signblockentity) {
            Item item = itemStack.getItem();
            SignApplicator signapplicator;

            if (item instanceof SignApplicator signapplicator1) {
                signapplicator = signapplicator1;
            } else {
                signapplicator = null;
            }

            SignApplicator signapplicator2 = signapplicator;
            boolean flag = signapplicator2 != null && player.mayBuild();

            if (level instanceof ServerLevel serverlevel) {
                if (flag && !signblockentity.isWaxed() && !this.otherPlayerIsEditingSign(player, signblockentity)) {
                    boolean flag1 = signblockentity.isFacingFrontText(player);

                    if (signapplicator2.canApplyToSign(signblockentity.getText(flag1), player) && signapplicator2.tryApplyToSign(serverlevel, signblockentity, flag1, player)) {
                        signblockentity.executeClickCommandsIfPresent(serverlevel, player, pos, flag1);
                        player.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
                        serverlevel.gameEvent(GameEvent.BLOCK_CHANGE, signblockentity.getBlockPos(), GameEvent.Context.of(player, signblockentity.getBlockState()));
                        itemStack.consume(1, player);
                        return InteractionResult.SUCCESS;
                    } else {
                        return InteractionResult.TRY_WITH_EMPTY_HAND;
                    }
                } else {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
            } else {
                return !flag && !signblockentity.isWaxed() ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof SignBlockEntity signblockentity) {
            if (level instanceof ServerLevel serverlevel) {
                boolean flag = signblockentity.isFacingFrontText(player);
                boolean flag1 = signblockentity.executeClickCommandsIfPresent(serverlevel, player, pos, flag);

                if (signblockentity.isWaxed()) {
                    serverlevel.playSound((Entity) null, signblockentity.getBlockPos(), signblockentity.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
                    return InteractionResult.SUCCESS_SERVER;
                } else if (flag1) {
                    return InteractionResult.SUCCESS_SERVER;
                } else if (!this.otherPlayerIsEditingSign(player, signblockentity) && player.mayBuild() && this.hasEditableText(player, signblockentity, flag)) {
                    this.openTextEdit(player, signblockentity, flag);
                    return InteractionResult.SUCCESS_SERVER;
                } else {
                    return InteractionResult.PASS;
                }
            } else {
                Util.pauseInIde(new IllegalStateException("Expected to only call this on server"));
                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private boolean hasEditableText(Player player, SignBlockEntity sign, boolean isFrontText) {
        SignText signtext = sign.getText(isFrontText);

        return Arrays.stream(signtext.getMessages(player.isTextFilteringEnabled())).allMatch((component) -> {
            return component.equals(CommonComponents.EMPTY) || component.getContents() instanceof PlainTextContents;
        });
    }

    public abstract float getYRotationDegrees(BlockState state);

    public Vec3 getSignHitboxCenterPosition(BlockState state) {
        return new Vec3(0.5D, 0.5D, 0.5D);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SignBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    public WoodType type() {
        return this.type;
    }

    public static WoodType getWoodType(Block block) {
        WoodType woodtype;

        if (block instanceof SignBlock) {
            woodtype = ((SignBlock) block).type();
        } else {
            woodtype = WoodType.OAK;
        }

        return woodtype;
    }

    public void openTextEdit(Player player, SignBlockEntity sign, boolean isFrontText) {
        sign.setAllowedPlayerEditor(player.getUUID());
        player.openTextEdit(sign, isFrontText);
    }

    private boolean otherPlayerIsEditingSign(Player player, SignBlockEntity sign) {
        UUID uuid = sign.getPlayerWhoMayEdit();

        return uuid != null && !uuid.equals(player.getUUID());
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.SIGN, SignBlockEntity::tick);
    }
}
