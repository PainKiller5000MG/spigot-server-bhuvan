package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerPlayerGameMode {

    private static final double FLIGHT_DISABLE_RANGE = 1.0D;
    private static final Logger LOGGER = LogUtils.getLogger();
    protected ServerLevel level;
    protected final ServerPlayer player;
    private GameType gameModeForPlayer;
    private @Nullable GameType previousGameModeForPlayer;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private BlockPos destroyPos;
    private int gameTicks;
    private boolean hasDelayedDestroy;
    private BlockPos delayedDestroyPos;
    private int delayedTickStart;
    private int lastSentState;

    public ServerPlayerGameMode(ServerPlayer player) {
        this.gameModeForPlayer = GameType.DEFAULT_MODE;
        this.destroyPos = BlockPos.ZERO;
        this.delayedDestroyPos = BlockPos.ZERO;
        this.lastSentState = -1;
        this.player = player;
        this.level = player.level();
    }

    public boolean changeGameModeForPlayer(GameType gameModeForPlayer) {
        if (gameModeForPlayer == this.gameModeForPlayer) {
            return false;
        } else {
            Abilities abilities = this.player.getAbilities();

            this.setGameModeForPlayer(gameModeForPlayer, this.gameModeForPlayer);
            if (abilities.flying && gameModeForPlayer != GameType.SPECTATOR && this.isInRangeOfGround()) {
                abilities.flying = false;
            }

            this.player.onUpdateAbilities();
            this.level.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, this.player));
            this.level.updateSleepingPlayerList();
            if (gameModeForPlayer == GameType.CREATIVE) {
                this.player.resetCurrentImpulseContext();
            }

            return true;
        }
    }

    protected void setGameModeForPlayer(GameType gameModeForPlayer, @Nullable GameType previousGameModeForPlayer) {
        this.previousGameModeForPlayer = previousGameModeForPlayer;
        this.gameModeForPlayer = gameModeForPlayer;
        Abilities abilities = this.player.getAbilities();

        gameModeForPlayer.updatePlayerAbilities(abilities);
    }

    private boolean isInRangeOfGround() {
        List<VoxelShape> list = Entity.collectAllColliders(this.player, this.level, this.player.getBoundingBox());

        return list.isEmpty() && this.player.getAvailableSpaceBelow(1.0D) < 1.0D;
    }

    public GameType getGameModeForPlayer() {
        return this.gameModeForPlayer;
    }

    public @Nullable GameType getPreviousGameModeForPlayer() {
        return this.previousGameModeForPlayer;
    }

    public boolean isSurvival() {
        return this.gameModeForPlayer.isSurvival();
    }

    public boolean isCreative() {
        return this.gameModeForPlayer.isCreative();
    }

    public void tick() {
        ++this.gameTicks;
        if (this.hasDelayedDestroy) {
            BlockState blockstate = this.level.getBlockState(this.delayedDestroyPos);

            if (blockstate.isAir()) {
                this.hasDelayedDestroy = false;
            } else {
                float f = this.incrementDestroyProgress(blockstate, this.delayedDestroyPos, this.delayedTickStart);

                if (f >= 1.0F) {
                    this.hasDelayedDestroy = false;
                    this.destroyBlock(this.delayedDestroyPos);
                }
            }
        } else if (this.isDestroyingBlock) {
            BlockState blockstate1 = this.level.getBlockState(this.destroyPos);

            if (blockstate1.isAir()) {
                this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                this.lastSentState = -1;
                this.isDestroyingBlock = false;
            } else {
                this.incrementDestroyProgress(blockstate1, this.destroyPos, this.destroyProgressStart);
            }
        }

    }

    private float incrementDestroyProgress(BlockState blockState, BlockPos delayedDestroyPos, int destroyStartTick) {
        int j = this.gameTicks - destroyStartTick;
        float f = blockState.getDestroyProgress(this.player, this.player.level(), delayedDestroyPos) * (float) (j + 1);
        int k = (int) (f * 10.0F);

        if (k != this.lastSentState) {
            this.level.destroyBlockProgress(this.player.getId(), delayedDestroyPos, k);
            this.lastSentState = k;
        }

        return f;
    }

    private void debugLogging(BlockPos pos, boolean allGood, int sequence, String message) {
        if (SharedConstants.DEBUG_BLOCK_BREAK) {
            ServerPlayerGameMode.LOGGER.debug("Server ACK {} {} {} {}", new Object[]{sequence, pos, allGood, message});
        }

    }

    public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence) {
        if (!this.player.isWithinBlockInteractionRange(pos, 1.0D)) {
            this.debugLogging(pos, false, sequence, "too far");
        } else if (pos.getY() > maxY) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, "too high");
        } else {
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract(this.player, pos)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "may not interact");
                    return;
                }

                if (this.player.getAbilities().instabuild) {
                    this.destroyAndAck(pos, sequence, "creative destroy");
                    return;
                }

                if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "block action restricted");
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;
                BlockState blockstate = this.level.getBlockState(pos);

                if (!blockstate.isAir()) {
                    EnchantmentHelper.onHitBlock(this.level, this.player.getMainHandItem(), this.player, this.player, EquipmentSlot.MAINHAND, Vec3.atCenterOf(pos), blockstate, (item) -> {
                        this.player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND);
                    });
                    blockstate.attack(this.level, pos, this.player);
                    f = blockstate.getDestroyProgress(this.player, this.player.level(), pos);
                }

                if (!blockstate.isAir() && f >= 1.0F) {
                    this.destroyAndAck(pos, sequence, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.destroyPos, this.level.getBlockState(this.destroyPos)));
                        this.debugLogging(pos, false, sequence, "abort destroying since another started (client insta mine, server disagreed)");
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = pos.immutable();
                    int k = (int) (f * 10.0F);

                    this.level.destroyBlockProgress(this.player.getId(), pos, k);
                    this.debugLogging(pos, true, sequence, "actual start of destroying");
                    this.lastSentState = k;
                }
            } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (pos.equals(this.destroyPos)) {
                    int l = this.gameTicks - this.destroyProgressStart;
                    BlockState blockstate1 = this.level.getBlockState(pos);

                    if (!blockstate1.isAir()) {
                        float f1 = blockstate1.getDestroyProgress(this.player, this.player.level(), pos) * (float) (l + 1);

                        if (f1 >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                            this.destroyAndAck(pos, sequence, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = pos;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.debugLogging(pos, true, sequence, "stopped destroying");
            } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                if (!Objects.equals(this.destroyPos, pos)) {
                    ServerPlayerGameMode.LOGGER.warn("Mismatch in destroy block pos: {} {}", this.destroyPos, pos);
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.debugLogging(pos, true, sequence, "aborted mismatched destroying");
                }

                this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                this.debugLogging(pos, true, sequence, "aborted destroying");
            }

        }
    }

    public void destroyAndAck(BlockPos pos, int sequence, String exitId) {
        if (this.destroyBlock(pos)) {
            this.debugLogging(pos, true, sequence, exitId);
        } else {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, exitId);
        }

    }

    public boolean destroyBlock(BlockPos pos) {
        BlockState blockstate = this.level.getBlockState(pos);

        if (!this.player.getMainHandItem().canDestroyBlock(blockstate, this.level, pos, this.player)) {
            return false;
        } else {
            BlockEntity blockentity = this.level.getBlockEntity(pos);
            Block block = blockstate.getBlock();

            if (block instanceof GameMasterBlock && !this.player.canUseGameMasterBlocks()) {
                this.level.sendBlockUpdated(pos, blockstate, blockstate, 3);
                return false;
            } else if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                return false;
            } else {
                BlockState blockstate1 = block.playerWillDestroy(this.level, pos, blockstate, this.player);
                boolean flag = this.level.removeBlock(pos, false);

                if (SharedConstants.DEBUG_BLOCK_BREAK) {
                    ServerPlayerGameMode.LOGGER.info("server broke {} {} -> {}", new Object[]{pos, blockstate1, this.level.getBlockState(pos)});
                }

                if (flag) {
                    block.destroy(this.level, pos, blockstate1);
                }

                if (this.player.preventsBlockDrops()) {
                    return true;
                } else {
                    ItemStack itemstack = this.player.getMainHandItem();
                    ItemStack itemstack1 = itemstack.copy();
                    boolean flag1 = this.player.hasCorrectToolForDrops(blockstate1);

                    itemstack.mineBlock(this.level, blockstate1, pos, this.player);
                    if (flag && flag1) {
                        block.playerDestroy(this.level, this.player, pos, blockstate1, blockentity, itemstack1);
                    }

                    return true;
                }
            }
        }
    }

    public InteractionResult useItem(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand) {
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        } else if (player.getCooldowns().isOnCooldown(itemStack)) {
            return InteractionResult.PASS;
        } else {
            int i = itemStack.getCount();
            int j = itemStack.getDamageValue();
            InteractionResult interactionresult = itemStack.use(level, player, hand);
            ItemStack itemstack1;

            if (interactionresult instanceof InteractionResult.Success) {
                InteractionResult.Success interactionresult_success = (InteractionResult.Success) interactionresult;

                itemstack1 = (ItemStack) Objects.requireNonNullElse(interactionresult_success.heldItemTransformedTo(), player.getItemInHand(hand));
            } else {
                itemstack1 = player.getItemInHand(hand);
            }

            if (itemstack1 == itemStack && itemstack1.getCount() == i && itemstack1.getUseDuration(player) <= 0 && itemstack1.getDamageValue() == j) {
                return interactionresult;
            } else if (interactionresult instanceof InteractionResult.Fail && itemstack1.getUseDuration(player) > 0 && !player.isUsingItem()) {
                return interactionresult;
            } else {
                if (itemStack != itemstack1) {
                    player.setItemInHand(hand, itemstack1);
                }

                if (itemstack1.isEmpty()) {
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }

                if (!player.isUsingItem()) {
                    player.inventoryMenu.sendAllDataToRemote();
                }

                return interactionresult;
            }
        }
    }

    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos blockpos = hitResult.getBlockPos();
        BlockState blockstate = level.getBlockState(blockpos);

        if (!blockstate.getBlock().isEnabled(level.enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuprovider = blockstate.getMenuProvider(level, blockpos);

            if (menuprovider != null) {
                player.openMenu(menuprovider);
                return InteractionResult.CONSUME;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            boolean flag = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            boolean flag1 = player.isSecondaryUseActive() && flag;
            ItemStack itemstack1 = itemStack.copy();

            if (!flag1) {
                InteractionResult interactionresult = blockstate.useItemOn(player.getItemInHand(hand), level, player, hand, hitResult);

                if (interactionresult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockpos, itemstack1);
                    return interactionresult;
                }

                if (interactionresult instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
                    InteractionResult interactionresult1 = blockstate.useWithoutItem(level, player, hitResult);

                    if (interactionresult1.consumesAction()) {
                        CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, blockpos);
                        return interactionresult1;
                    }
                }
            }

            if (!itemStack.isEmpty() && !player.getCooldowns().isOnCooldown(itemStack)) {
                UseOnContext useoncontext = new UseOnContext(player, hand, hitResult);
                InteractionResult interactionresult2;

                if (player.hasInfiniteMaterials()) {
                    int i = itemStack.getCount();

                    interactionresult2 = itemStack.useOn(useoncontext);
                    itemStack.setCount(i);
                } else {
                    interactionresult2 = itemStack.useOn(useoncontext);
                }

                if (interactionresult2.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockpos, itemstack1);
                }

                return interactionresult2;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public void setLevel(ServerLevel newLevel) {
        this.level = newLevel;
    }
}
