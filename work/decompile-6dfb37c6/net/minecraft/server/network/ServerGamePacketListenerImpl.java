package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.HashedStack;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.StringUtil;
import net.minecraft.util.TickThrottler;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerGamePacketListenerImpl extends ServerCommonPacketListenerImpl implements ServerGamePacketListener, ServerPlayerConnection, TickablePacketListener, GameProtocols.Context {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
    private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
    private static final int MAXIMUM_FLYING_TICKS = 80;
    private static final int ATTACK_INDICATOR_TOLERANCE_TICKS = 5;
    public static final int CLIENT_LOADED_TIMEOUT_TIME = 60;
    private static final Component CHAT_VALIDATION_FAILED = Component.translatable("multiplayer.disconnect.chat_validation_failed");
    private static final Component INVALID_COMMAND_SIGNATURE = Component.translatable("chat.disabled.invalid_command_signature").withStyle(ChatFormatting.RED);
    private static final int MAX_COMMAND_SUGGESTIONS = 1000;
    public ServerPlayer player;
    public final PlayerChunkSender chunkSender;
    private int tickCount;
    private int ackBlockChangesUpTo = -1;
    private final TickThrottler chatSpamThrottler = new TickThrottler(20, 200);
    private final TickThrottler dropSpamThrottler = new TickThrottler(20, 1480);
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    private @Nullable Entity lastVehicle;
    private double vehicleFirstGoodX;
    private double vehicleFirstGoodY;
    private double vehicleFirstGoodZ;
    private double vehicleLastGoodX;
    private double vehicleLastGoodY;
    private double vehicleLastGoodZ;
    private @Nullable Vec3 awaitingPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;
    private boolean receivedMovementThisTick;
    private @Nullable RemoteChatSession chatSession;
    private SignedMessageChain.Decoder signedMessageDecoder;
    private final LastSeenMessagesValidator lastSeenMessages = new LastSeenMessagesValidator(20);
    private int nextChatIndex;
    private final MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
    private final FutureChain chatMessageChain;
    private boolean waitingForSwitchToConfig;
    private boolean waitingForRespawn;
    private int clientLoadedTimeoutTimer;

    public ServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        super(server, connection, cookie);
        this.restartClientLoadTimerAfterRespawn();
        this.chunkSender = new PlayerChunkSender(connection.isMemoryConnection());
        this.player = player;
        player.connection = this;
        player.getTextFilter().join();
        UUID uuid = player.getUUID();

        Objects.requireNonNull(server);
        this.signedMessageDecoder = SignedMessageChain.Decoder.unsigned(uuid, server::enforceSecureProfile);
        this.chatMessageChain = new FutureChain(server);
    }

    @Override
    public void tick() {
        if (this.ackBlockChangesUpTo > -1) {
            this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
            this.ackBlockChangesUpTo = -1;
        }

        if (this.server.isPaused() || !this.tickPlayer()) {
            this.keepConnectionAlive();
            this.chatSpamThrottler.tick();
            this.dropSpamThrottler.tick();
            if (this.player.getLastActionTime() > 0L && this.server.playerIdleTimeout() > 0 && Util.getMillis() - this.player.getLastActionTime() > TimeUnit.MINUTES.toMillis((long) this.server.playerIdleTimeout()) && !this.player.wonGame) {
                this.disconnect((Component) Component.translatable("multiplayer.disconnect.idling"));
            }

        }
    }

    private boolean tickPlayer() {
        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        this.player.absSnapTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
        ++this.tickCount;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
            if (++this.aboveGroundTickCount > this.getMaximumFlyingTicks(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked for floating too long!", this.player.getPlainTextName());
                this.disconnect((Component) Component.translatable("multiplayer.disconnect.flying"));
                return true;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
            if (this.clientVehicleIsFloating && this.lastVehicle.getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > this.getMaximumFlyingTicks(this.lastVehicle)) {
                    ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getPlainTextName());
                    this.disconnect((Component) Component.translatable("multiplayer.disconnect.flying"));
                    return true;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        return false;
    }

    private int getMaximumFlyingTicks(Entity entity) {
        double d0 = entity.getGravity();

        if (d0 < (double) 1.0E-5F) {
            return Integer.MAX_VALUE;
        } else {
            double d1 = 0.08D / d0;

            return Mth.ceil(80.0D * Math.max(d1, 1.0D));
        }
    }

    public void resetFlyingTicks() {
        this.aboveGroundTickCount = 0;
        this.aboveGroundVehicleTickCount = 0;
    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.waitingForSwitchToConfig;
    }

    @Override
    public boolean shouldHandleMessage(Packet<?> packet) {
        return super.shouldHandleMessage(packet) ? true : this.waitingForSwitchToConfig && this.connection.isConnected() && packet instanceof ServerboundConfigurationAcknowledgedPacket;
    }

    @Override
    protected GameProfile playerProfile() {
        return this.player.getGameProfile();
    }

    private <T, R> CompletableFuture<R> filterTextPacket(T message, BiFunction<TextFilter, T, CompletableFuture<R>> action) {
        return ((CompletableFuture) action.apply(this.player.getTextFilter(), message)).thenApply((object) -> {
            if (!this.isAcceptingMessages()) {
                ServerGamePacketListenerImpl.LOGGER.debug("Ignoring packet due to disconnection");
                throw new CancellationException("disconnected");
            } else {
                return object;
            }
        });
    }

    private CompletableFuture<FilteredText> filterTextPacket(String message) {
        return this.filterTextPacket(message, TextFilter::processStreamMessage);
    }

    private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> message) {
        return this.filterTextPacket(message, TextFilter::processMessageBundle);
    }

    @Override
    public void handlePlayerInput(ServerboundPlayerInputPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.setLastClientInput(packet.input());
        if (this.hasClientLoaded()) {
            this.player.resetLastActionTime();
            this.player.setShiftKeyDown(packet.input().shift());
        }

    }

    private static boolean containsInvalidValues(double x, double y, double z, float yRot, float xRot) {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Floats.isFinite(xRot) || !Floats.isFinite(yRot);
    }

    private static double clampHorizontal(double value) {
        return Mth.clamp(value, -3.0E7D, 3.0E7D);
    }

    private static double clampVertical(double value) {
        return Mth.clamp(value, -2.0E7D, 2.0E7D);
    }

    @Override
    public void handleMoveVehicle(ServerboundMoveVehiclePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (containsInvalidValues(packet.position().x(), packet.position().y(), packet.position().z(), packet.yRot(), packet.xRot())) {
            this.disconnect((Component) Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"));
        } else if (!this.updateAwaitingTeleport() && this.hasClientLoaded()) {
            Entity entity = this.player.getRootVehicle();

            if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
                ServerLevel serverlevel = this.player.level();
                double d0 = entity.getX();
                double d1 = entity.getY();
                double d2 = entity.getZ();
                double d3 = clampHorizontal(packet.position().x());
                double d4 = clampVertical(packet.position().y());
                double d5 = clampHorizontal(packet.position().z());
                float f = Mth.wrapDegrees(packet.yRot());
                float f1 = Mth.wrapDegrees(packet.xRot());
                double d6 = d3 - this.vehicleFirstGoodX;
                double d7 = d4 - this.vehicleFirstGoodY;
                double d8 = d5 - this.vehicleFirstGoodZ;
                double d9 = entity.getDeltaMovement().lengthSqr();
                double d10 = d6 * d6 + d7 * d7 + d8 * d8;

                if (d10 - d9 > 100.0D && !this.isSingleplayerOwner()) {
                    ServerGamePacketListenerImpl.LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", new Object[]{entity.getPlainTextName(), this.player.getPlainTextName(), d6, d7, d8});
                    this.send(ClientboundMoveVehiclePacket.fromEntity(entity));
                    return;
                }

                AABB aabb = entity.getBoundingBox();

                d6 = d3 - this.vehicleLastGoodX;
                d7 = d4 - this.vehicleLastGoodY;
                d8 = d5 - this.vehicleLastGoodZ;
                boolean flag = entity.verticalCollisionBelow;

                if (entity instanceof LivingEntity) {
                    LivingEntity livingentity = (LivingEntity) entity;

                    if (livingentity.onClimbable()) {
                        livingentity.resetFallDistance();
                    }
                }

                entity.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                double d11 = d7;

                d6 = d3 - entity.getX();
                d7 = d4 - entity.getY();
                if (d7 > -0.5D || d7 < 0.5D) {
                    d7 = 0.0D;
                }

                d8 = d5 - entity.getZ();
                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                boolean flag1 = false;

                if (d10 > 0.0625D) {
                    flag1 = true;
                    ServerGamePacketListenerImpl.LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", new Object[]{entity.getPlainTextName(), this.player.getPlainTextName(), Math.sqrt(d10)});
                }

                if (flag1 && serverlevel.noCollision(entity, aabb) || this.isEntityCollidingWithAnythingNew(serverlevel, entity, aabb, d3, d4, d5)) {
                    entity.absSnapTo(d0, d1, d2, f, f1);
                    this.send(ClientboundMoveVehiclePacket.fromEntity(entity));
                    entity.removeLatestMovementRecording();
                    return;
                }

                entity.absSnapTo(d3, d4, d5, f, f1);
                this.player.level().getChunkSource().move(this.player);
                Vec3 vec3 = new Vec3(entity.getX() - d0, entity.getY() - d1, entity.getZ() - d2);

                this.handlePlayerKnownMovement(vec3);
                entity.setOnGroundWithMovement(packet.onGround(), vec3);
                entity.doCheckFallDamage(vec3.x, vec3.y, vec3.z, packet.onGround());
                this.player.checkMovementStatistics(vec3.x, vec3.y, vec3.z);
                this.clientVehicleIsFloating = d11 >= -0.03125D && !flag && !this.server.allowFlight() && !entity.isFlyingVehicle() && !entity.isNoGravity() && this.noBlocksAround(entity);
                this.vehicleLastGoodX = entity.getX();
                this.vehicleLastGoodY = entity.getY();
                this.vehicleLastGoodZ = entity.getZ();
            }

        }
    }

    private boolean noBlocksAround(Entity entity) {
        return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625D).expandTowards(0.0D, -0.55D, 0.0D)).allMatch(BlockBehaviour.BlockStateBase::isAir);
    }

    @Override
    public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (packet.getId() == this.awaitingTeleport) {
            if (this.awaitingPositionFromClient == null) {
                this.disconnect((Component) Component.translatable("multiplayer.disconnect.invalid_player_movement"));
                return;
            }

            this.player.absSnapTo(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
            this.lastGoodX = this.awaitingPositionFromClient.x;
            this.lastGoodY = this.awaitingPositionFromClient.y;
            this.lastGoodZ = this.awaitingPositionFromClient.z;
            this.player.hasChangedDimension();
            this.awaitingPositionFromClient = null;
        }

    }

    @Override
    public void handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.markClientLoaded();
    }

    @Override
    public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        RecipeManager.ServerDisplayInfo recipemanager_serverdisplayinfo = this.server.getRecipeManager().getRecipeFromDisplay(packet.recipe());

        if (recipemanager_serverdisplayinfo != null) {
            this.player.getRecipeBook().removeHighlight(recipemanager_serverdisplayinfo.parent().id());
        }

    }

    @Override
    public void handleBundleItemSelectedPacket(ServerboundSelectBundleItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.containerMenu.setSelectedBundleItemIndex(packet.slotId(), packet.selectedItemIndex());
    }

    @Override
    public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.getRecipeBook().setBookSetting(packet.getBookType(), packet.isOpen(), packet.isFiltering());
    }

    @Override
    public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (packet.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
            Identifier identifier = (Identifier) Objects.requireNonNull(packet.getTab());
            AdvancementHolder advancementholder = this.server.getAdvancements().get(identifier);

            if (advancementholder != null) {
                this.player.getAdvancements().setSelectedTab(advancementholder);
            }
        }

    }

    @Override
    public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        StringReader stringreader = new StringReader(packet.getCommand());

        if (stringreader.canRead() && stringreader.peek() == '/') {
            stringreader.skip();
        }

        ParseResults<CommandSourceStack> parseresults = this.server.getCommands().getDispatcher().parse(stringreader, this.player.createCommandSourceStack());

        this.server.getCommands().getDispatcher().getCompletionSuggestions(parseresults).thenAccept((suggestions) -> {
            Suggestions suggestions1 = suggestions.getList().size() <= 1000 ? suggestions : new Suggestions(suggestions.getRange(), suggestions.getList().subList(0, 1000));

            this.send(new ClientboundCommandSuggestionsPacket(packet.getId(), suggestions1));
        });
    }

    @Override
    public void handleSetCommandBlock(ServerboundSetCommandBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock basecommandblock = null;
            CommandBlockEntity commandblockentity = null;
            BlockPos blockpos = packet.getPos();
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);

            if (blockentity instanceof CommandBlockEntity) {
                CommandBlockEntity commandblockentity1 = (CommandBlockEntity) blockentity;

                commandblockentity = commandblockentity1;
                basecommandblock = commandblockentity1.getCommandBlock();
            }

            String s = packet.getCommand();
            boolean flag = packet.isTrackOutput();

            if (basecommandblock != null) {
                CommandBlockEntity.Mode commandblockentity_mode = commandblockentity.getMode();
                BlockState blockstate = this.player.level().getBlockState(blockpos);
                Direction direction = (Direction) blockstate.getValue(CommandBlock.FACING);
                BlockState blockstate1;

                switch (packet.getMode()) {
                    case SEQUENCE:
                        blockstate1 = Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                        break;
                    case AUTO:
                        blockstate1 = Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                        break;
                    default:
                        blockstate1 = Blocks.COMMAND_BLOCK.defaultBlockState();
                }

                BlockState blockstate2 = blockstate1;
                BlockState blockstate3 = (BlockState) ((BlockState) blockstate2.setValue(CommandBlock.FACING, direction)).setValue(CommandBlock.CONDITIONAL, packet.isConditional());

                if (blockstate3 != blockstate) {
                    this.player.level().setBlock(blockpos, blockstate3, 2);
                    blockentity.setBlockState(blockstate3);
                    this.player.level().getChunkAt(blockpos).setBlockEntity(blockentity);
                }

                basecommandblock.setCommand(s);
                basecommandblock.setTrackOutput(flag);
                if (!flag) {
                    basecommandblock.setLastOutput((Component) null);
                }

                commandblockentity.setAutomatic(packet.isAutomatic());
                if (commandblockentity_mode != packet.getMode()) {
                    commandblockentity.onModeSwitch();
                }

                if (this.player.level().isCommandBlockEnabled()) {
                    basecommandblock.onUpdated(this.player.level());
                }

                if (!StringUtil.isNullOrEmpty(s)) {
                    this.player.sendSystemMessage(Component.translatable(this.player.level().isCommandBlockEnabled() ? "advMode.setCommand.success" : "advMode.setCommand.disabled", s));
                }
            }

        }
    }

    @Override
    public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock basecommandblock = packet.getCommandBlock(this.player.level());

            if (basecommandblock != null) {
                String s = packet.getCommand();

                basecommandblock.setCommand(s);
                basecommandblock.setTrackOutput(packet.isTrackOutput());
                if (!packet.isTrackOutput()) {
                    basecommandblock.setLastOutput((Component) null);
                }

                boolean flag = this.player.level().isCommandBlockEnabled();

                if (flag) {
                    basecommandblock.onUpdated(this.player.level());
                }

                if (!StringUtil.isNullOrEmpty(s)) {
                    this.player.sendSystemMessage(Component.translatable(flag ? "advMode.setCommand.success" : "advMode.setCommand.disabled", s));
                }
            }

        }
    }

    @Override
    public void handlePickItemFromBlock(ServerboundPickItemFromBlockPacket packet) {
        ServerLevel serverlevel = this.player.level();

        PacketUtils.ensureRunningOnSameThread(packet, this, serverlevel);
        BlockPos blockpos = packet.pos();

        if (this.player.isWithinBlockInteractionRange(blockpos, 1.0D)) {
            if (serverlevel.isLoaded(blockpos)) {
                BlockState blockstate = serverlevel.getBlockState(blockpos);
                boolean flag = this.player.hasInfiniteMaterials() && packet.includeData();
                ItemStack itemstack = blockstate.getCloneItemStack(serverlevel, blockpos, flag);

                if (!itemstack.isEmpty()) {
                    if (flag) {
                        addBlockDataToItem(blockstate, serverlevel, blockpos, itemstack);
                    }

                    this.tryPickItem(itemstack);
                }
            }
        }
    }

    private static void addBlockDataToItem(BlockState blockState, ServerLevel level, BlockPos pos, ItemStack itemStack) {
        BlockEntity blockentity = blockState.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        if (blockentity != null) {
            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(blockentity.problemPath(), ServerGamePacketListenerImpl.LOGGER)) {
                TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, level.registryAccess());

                blockentity.saveCustomOnly((ValueOutput) tagvalueoutput);
                blockentity.removeComponentsFromTag(tagvalueoutput);
                BlockItem.setBlockEntityData(itemStack, blockentity.getType(), tagvalueoutput);
                itemStack.applyComponents(blockentity.collectComponents());
            }
        }

    }

    @Override
    public void handlePickItemFromEntity(ServerboundPickItemFromEntityPacket packet) {
        ServerLevel serverlevel = this.player.level();

        PacketUtils.ensureRunningOnSameThread(packet, this, serverlevel);
        Entity entity = serverlevel.getEntityOrPart(packet.id());

        if (entity != null && this.player.isWithinEntityInteractionRange(entity, 3.0D)) {
            ItemStack itemstack = entity.getPickResult();

            if (itemstack != null && !itemstack.isEmpty()) {
                this.tryPickItem(itemstack);
            }

        }
    }

    private void tryPickItem(ItemStack itemStack) {
        if (itemStack.isItemEnabled(this.player.level().enabledFeatures())) {
            Inventory inventory = this.player.getInventory();
            int i = inventory.findSlotMatchingItem(itemStack);

            if (i != -1) {
                if (Inventory.isHotbarSlot(i)) {
                    inventory.setSelectedSlot(i);
                } else {
                    inventory.pickSlot(i);
                }
            } else if (this.player.hasInfiniteMaterials()) {
                inventory.addAndPickItem(itemStack);
            }

            this.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
            this.player.inventoryMenu.broadcastChanges();
        }
    }

    @Override
    public void handleRenameItem(ServerboundRenameItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;

        if (abstractcontainermenu instanceof AnvilMenu anvilmenu) {
            if (!anvilmenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, anvilmenu);
                return;
            }

            anvilmenu.setItemName(packet.getName());
        }

    }

    @Override
    public void handleSetBeaconPacket(ServerboundSetBeaconPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;

        if (abstractcontainermenu instanceof BeaconMenu beaconmenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
                return;
            }

            beaconmenu.updateEffects(packet.primary(), packet.secondary());
        }

    }

    @Override
    public void handleSetStructureBlock(ServerboundSetStructureBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = packet.getPos();
            BlockState blockstate = this.player.level().getBlockState(blockpos);
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);

            if (blockentity instanceof StructureBlockEntity) {
                StructureBlockEntity structureblockentity = (StructureBlockEntity) blockentity;

                structureblockentity.setMode(packet.getMode());
                structureblockentity.setStructureName(packet.getName());
                structureblockentity.setStructurePos(packet.getOffset());
                structureblockentity.setStructureSize(packet.getSize());
                structureblockentity.setMirror(packet.getMirror());
                structureblockentity.setRotation(packet.getRotation());
                structureblockentity.setMetaData(packet.getData());
                structureblockentity.setIgnoreEntities(packet.isIgnoreEntities());
                structureblockentity.setStrict(packet.isStrict());
                structureblockentity.setShowAir(packet.isShowAir());
                structureblockentity.setShowBoundingBox(packet.isShowBoundingBox());
                structureblockentity.setIntegrity(packet.getIntegrity());
                structureblockentity.setSeed(packet.getSeed());
                if (structureblockentity.hasStructureName()) {
                    String s = structureblockentity.getStructureName();

                    if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                        if (structureblockentity.saveStructure()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_failure", s), false);
                        }
                    } else if (packet.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                        if (!structureblockentity.isStructureLoadable()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", s), false);
                        } else if (structureblockentity.placeStructureIfSameSize(this.player.level())) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", s), false);
                        }
                    } else if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                        if (structureblockentity.detectSize()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", packet.getName()), false);
                }

                structureblockentity.setChanged();
                this.player.level().sendBlockUpdated(blockpos, blockstate, blockstate, 3);
            }

        }
    }

    @Override
    public void handleSetTestBlock(ServerboundSetTestBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = packet.position();
            BlockState blockstate = this.player.level().getBlockState(blockpos);
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);

            if (blockentity instanceof TestBlockEntity) {
                TestBlockEntity testblockentity = (TestBlockEntity) blockentity;

                testblockentity.setMode(packet.mode());
                testblockentity.setMessage(packet.message());
                testblockentity.setChanged();
                this.player.level().sendBlockUpdated(blockpos, blockstate, testblockentity.getBlockState(), 3);
            }

        }
    }

    @Override
    public void handleTestInstanceBlockAction(ServerboundTestInstanceBlockActionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        BlockPos blockpos = packet.pos();

        if (this.player.canUseGameMasterBlocks()) {
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);

            if (blockentity instanceof TestInstanceBlockEntity) {
                TestInstanceBlockEntity testinstanceblockentity = (TestInstanceBlockEntity) blockentity;

                if (packet.action() != ServerboundTestInstanceBlockActionPacket.Action.QUERY && packet.action() != ServerboundTestInstanceBlockActionPacket.Action.INIT) {
                    testinstanceblockentity.set(packet.data());
                    if (packet.action() == ServerboundTestInstanceBlockActionPacket.Action.RESET) {
                        ServerPlayer serverplayer = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.resetTest(serverplayer::sendSystemMessage);
                    } else if (packet.action() == ServerboundTestInstanceBlockActionPacket.Action.SAVE) {
                        ServerPlayer serverplayer1 = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.saveTest(serverplayer1::sendSystemMessage);
                    } else if (packet.action() == ServerboundTestInstanceBlockActionPacket.Action.EXPORT) {
                        ServerPlayer serverplayer2 = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.exportTest(serverplayer2::sendSystemMessage);
                    } else if (packet.action() == ServerboundTestInstanceBlockActionPacket.Action.RUN) {
                        ServerPlayer serverplayer3 = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.runTest(serverplayer3::sendSystemMessage);
                    }

                    BlockState blockstate = this.player.level().getBlockState(blockpos);

                    this.player.level().sendBlockUpdated(blockpos, Blocks.AIR.defaultBlockState(), blockstate, 3);
                } else {
                    Registry<GameTestInstance> registry = this.player.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
                    Optional optional = packet.data().test();

                    Objects.requireNonNull(registry);
                    Optional<Holder.Reference<GameTestInstance>> optional1 = optional.flatMap(registry::get);
                    Component component;

                    if (optional1.isPresent()) {
                        component = ((GameTestInstance) ((Holder.Reference) optional1.get()).value()).describe();
                    } else {
                        component = Component.translatable("test_instance.description.no_test").withStyle(ChatFormatting.RED);
                    }

                    Optional<Vec3i> optional2;

                    if (packet.action() == ServerboundTestInstanceBlockActionPacket.Action.QUERY) {
                        optional2 = packet.data().test().flatMap((resourcekey) -> {
                            return TestInstanceBlockEntity.getStructureSize(this.player.level(), resourcekey);
                        });
                    } else {
                        optional2 = Optional.empty();
                    }

                    this.connection.send(new ClientboundTestInstanceBlockStatus(component, optional2));
                }

                return;
            }
        }

    }

    @Override
    public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = packet.getPos();
            BlockState blockstate = this.player.level().getBlockState(blockpos);
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);

            if (blockentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity jigsawblockentity = (JigsawBlockEntity) blockentity;

                jigsawblockentity.setName(packet.getName());
                jigsawblockentity.setTarget(packet.getTarget());
                jigsawblockentity.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, packet.getPool()));
                jigsawblockentity.setFinalState(packet.getFinalState());
                jigsawblockentity.setJoint(packet.getJoint());
                jigsawblockentity.setPlacementPriority(packet.getPlacementPriority());
                jigsawblockentity.setSelectionPriority(packet.getSelectionPriority());
                jigsawblockentity.setChanged();
                this.player.level().sendBlockUpdated(blockpos, blockstate, blockstate, 3);
            }

        }
    }

    @Override
    public void handleJigsawGenerate(ServerboundJigsawGeneratePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockpos = packet.getPos();
            BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);

            if (blockentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity jigsawblockentity = (JigsawBlockEntity) blockentity;

                jigsawblockentity.generate(this.player.level(), packet.levels(), packet.keepJigsaws());
            }

        }
    }

    @Override
    public void handleSelectTrade(ServerboundSelectTradePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        int i = packet.getItem();
        AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;

        if (abstractcontainermenu instanceof MerchantMenu merchantmenu) {
            if (!merchantmenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, merchantmenu);
                return;
            }

            merchantmenu.setSelectionHint(i);
            merchantmenu.tryMoveItems(i);
        }

    }

    @Override
    public void handleEditBook(ServerboundEditBookPacket packet) {
        int i = packet.slot();

        if (Inventory.isHotbarSlot(i) || i == 40) {
            List<String> list = Lists.newArrayList();
            Optional<String> optional = packet.title();

            Objects.requireNonNull(list);
            optional.ifPresent(list::add);
            list.addAll(packet.pages());
            Consumer<List<FilteredText>> consumer = optional.isPresent() ? (list1) -> {
                this.signBook((FilteredText) list1.get(0), list1.subList(1, list1.size()), i);
            } : (list1) -> {
                this.updateBookContents(list1, i);
            };

            this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
        }
    }

    private void updateBookContents(List<FilteredText> contents, int slot) {
        ItemStack itemstack = this.player.getInventory().getItem(slot);

        if (itemstack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            List<Filterable<String>> list1 = contents.stream().map(this::filterableFromOutgoing).toList();

            itemstack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(list1));
        }
    }

    private void signBook(FilteredText title, List<FilteredText> contents, int slot) {
        ItemStack itemstack = this.player.getInventory().getItem(slot);

        if (itemstack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            ItemStack itemstack1 = itemstack.transmuteCopy(Items.WRITTEN_BOOK);

            itemstack1.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            List<Filterable<Component>> list1 = contents.stream().map((filteredtext1) -> {
                return this.filterableFromOutgoing(filteredtext1).map(Component::literal);
            }).toList();

            itemstack1.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(this.filterableFromOutgoing(title), this.player.getPlainTextName(), 0, list1, true));
            this.player.getInventory().setItem(slot, itemstack1);
        }
    }

    private Filterable<String> filterableFromOutgoing(FilteredText text) {
        return this.player.isTextFilteringEnabled() ? Filterable.passThrough(text.filteredOrEmpty()) : Filterable.from(text);
    }

    @Override
    public void handleEntityTagQuery(ServerboundEntityTagQueryPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            Entity entity = this.player.level().getEntity(packet.getEntityId());

            if (entity != null) {
                try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(entity.problemPath(), ServerGamePacketListenerImpl.LOGGER)) {
                    TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, entity.registryAccess());

                    entity.saveWithoutId(tagvalueoutput);
                    CompoundTag compoundtag = tagvalueoutput.buildResult();

                    this.send(new ClientboundTagQueryPacket(packet.getTransactionId(), compoundtag));
                }
            }

        }
    }

    @Override
    public void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (!this.player.isSpectator() && packet.containerId() == this.player.containerMenu.containerId) {
            AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;

            if (abstractcontainermenu instanceof CrafterMenu) {
                CrafterMenu craftermenu = (CrafterMenu) abstractcontainermenu;
                Container container = craftermenu.getContainer();

                if (container instanceof CrafterBlockEntity) {
                    CrafterBlockEntity crafterblockentity = (CrafterBlockEntity) container;

                    crafterblockentity.setSlotState(packet.slotId(), packet.newState());
                }
            }

        }
    }

    @Override
    public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            BlockEntity blockentity = this.player.level().getBlockEntity(packet.getPos());
            CompoundTag compoundtag = blockentity != null ? blockentity.saveWithoutMetadata((HolderLookup.Provider) this.player.registryAccess()) : null;

            this.send(new ClientboundTagQueryPacket(packet.getTransactionId(), compoundtag));
        }
    }

    @Override
    public void handleMovePlayer(ServerboundMovePlayerPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (containsInvalidValues(packet.getX(0.0D), packet.getY(0.0D), packet.getZ(0.0D), packet.getYRot(0.0F), packet.getXRot(0.0F))) {
            this.disconnect((Component) Component.translatable("multiplayer.disconnect.invalid_player_movement"));
        } else {
            ServerLevel serverlevel = this.player.level();

            if (!this.player.wonGame) {
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (this.hasClientLoaded()) {
                    float f = Mth.wrapDegrees(packet.getYRot(this.player.getYRot()));
                    float f1 = Mth.wrapDegrees(packet.getXRot(this.player.getXRot()));

                    if (this.updateAwaitingTeleport()) {
                        this.player.absSnapRotationTo(f, f1);
                    } else {
                        double d0 = clampHorizontal(packet.getX(this.player.getX()));
                        double d1 = clampVertical(packet.getY(this.player.getY()));
                        double d2 = clampHorizontal(packet.getZ(this.player.getZ()));

                        if (this.player.isPassenger()) {
                            this.player.absSnapTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                            this.player.level().getChunkSource().move(this.player);
                        } else {
                            double d3 = this.player.getX();
                            double d4 = this.player.getY();
                            double d5 = this.player.getZ();
                            double d6 = d0 - this.firstGoodX;
                            double d7 = d1 - this.firstGoodY;
                            double d8 = d2 - this.firstGoodZ;
                            double d9 = this.player.getDeltaMovement().lengthSqr();
                            double d10 = d6 * d6 + d7 * d7 + d8 * d8;

                            if (this.player.isSleeping()) {
                                if (d10 > 1.0D) {
                                    this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                                }

                            } else {
                                boolean flag = this.player.isFallFlying();

                                if (serverlevel.tickRateManager().runsNormally()) {
                                    ++this.receivedMovePacketCount;
                                    int i = this.receivedMovePacketCount - this.knownMovePacketCount;

                                    if (i > 5) {
                                        ServerGamePacketListenerImpl.LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getPlainTextName(), i);
                                        i = 1;
                                    }

                                    if (this.shouldCheckPlayerMovement(flag)) {
                                        float f2 = flag ? 300.0F : 100.0F;

                                        if (d10 - d9 > (double) (f2 * (float) i)) {
                                            ServerGamePacketListenerImpl.LOGGER.warn("{} moved too quickly! {},{},{}", new Object[]{this.player.getPlainTextName(), d6, d7, d8});
                                            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
                                            return;
                                        }
                                    }
                                }

                                AABB aabb = this.player.getBoundingBox();

                                d6 = d0 - this.lastGoodX;
                                d7 = d1 - this.lastGoodY;
                                d8 = d2 - this.lastGoodZ;
                                boolean flag1 = d7 > 0.0D;

                                if (this.player.onGround() && !packet.isOnGround() && flag1) {
                                    this.player.jumpFromGround();
                                }

                                boolean flag2 = this.player.verticalCollisionBelow;

                                this.player.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                                double d11 = d7;

                                d6 = d0 - this.player.getX();
                                d7 = d1 - this.player.getY();
                                if (d7 > -0.5D || d7 < 0.5D) {
                                    d7 = 0.0D;
                                }

                                d8 = d2 - this.player.getZ();
                                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                                boolean flag3 = false;

                                if (!this.player.isChangingDimension() && d10 > 0.0625D && !this.player.isSleeping() && !this.player.isCreative() && !this.player.isSpectator() && !this.player.isInPostImpulseGraceTime()) {
                                    flag3 = true;
                                    ServerGamePacketListenerImpl.LOGGER.warn("{} moved wrongly!", this.player.getPlainTextName());
                                }

                                if (this.player.noPhysics || this.player.isSleeping() || (!flag3 || !serverlevel.noCollision(this.player, aabb)) && !this.isEntityCollidingWithAnythingNew(serverlevel, this.player, aabb, d0, d1, d2)) {
                                    this.player.absSnapTo(d0, d1, d2, f, f1);
                                    boolean flag4 = this.player.isAutoSpinAttack();

                                    this.clientIsFloating = d11 >= -0.03125D && !flag2 && !this.player.isSpectator() && !this.server.allowFlight() && !this.player.getAbilities().mayfly && !this.player.hasEffect(MobEffects.LEVITATION) && !flag && !flag4 && this.noBlocksAround(this.player);
                                    this.player.level().getChunkSource().move(this.player);
                                    Vec3 vec3 = new Vec3(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);

                                    this.player.setOnGroundWithMovement(packet.isOnGround(), packet.horizontalCollision(), vec3);
                                    this.player.doCheckFallDamage(vec3.x, vec3.y, vec3.z, packet.isOnGround());
                                    this.handlePlayerKnownMovement(vec3);
                                    if (flag1) {
                                        this.player.resetFallDistance();
                                    }

                                    if (packet.isOnGround() || this.player.hasLandedInLiquid() || this.player.onClimbable() || this.player.isSpectator() || flag || flag4) {
                                        this.player.tryResetCurrentImpulseContext();
                                    }

                                    this.player.checkMovementStatistics(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                                    this.lastGoodX = this.player.getX();
                                    this.lastGoodY = this.player.getY();
                                    this.lastGoodZ = this.player.getZ();
                                } else {
                                    this.teleport(d3, d4, d5, f, f1);
                                    this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, packet.isOnGround());
                                    this.player.removeLatestMovementRecording();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldCheckPlayerMovement(boolean isFallFlying) {
        if (this.isSingleplayerOwner()) {
            return false;
        } else if (this.player.isChangingDimension()) {
            return false;
        } else {
            GameRules gamerules = this.player.level().getGameRules();

            return !(Boolean) gamerules.get(GameRules.PLAYER_MOVEMENT_CHECK) ? false : !isFallFlying || (Boolean) gamerules.get(GameRules.ELYTRA_MOVEMENT_CHECK);
        }
    }

    private boolean updateAwaitingTeleport() {
        if (this.awaitingPositionFromClient != null) {
            if (this.tickCount - this.awaitingTeleportTime > 20) {
                this.awaitingTeleportTime = this.tickCount;
                this.teleport(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
            }

            return true;
        } else {
            this.awaitingTeleportTime = this.tickCount;
            return false;
        }
    }

    private boolean isEntityCollidingWithAnythingNew(LevelReader level, Entity entity, AABB oldAABB, double newX, double newY, double newZ) {
        AABB aabb1 = entity.getBoundingBox().move(newX - entity.getX(), newY - entity.getY(), newZ - entity.getZ());
        Iterable<VoxelShape> iterable = level.getPreMoveCollisions(entity, aabb1.deflate((double) 1.0E-5F), oldAABB.getBottomCenter());
        VoxelShape voxelshape = Shapes.create(oldAABB.deflate((double) 1.0E-5F));

        for (VoxelShape voxelshape1 : iterable) {
            if (!Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.AND)) {
                return true;
            }
        }

        return false;
    }

    public void teleport(double x, double y, double z, float yRot, float xRot) {
        this.teleport(new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yRot, xRot), Collections.emptySet());
    }

    public void teleport(PositionMoveRotation destination, Set<Relative> relatives) {
        this.awaitingTeleportTime = this.tickCount;
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        this.player.teleportSetPosition(destination, relatives);
        this.awaitingPositionFromClient = this.player.position();
        this.send(ClientboundPlayerPositionPacket.of(this.awaitingTeleport, destination, relatives));
    }

    @Override
    public void handlePlayerAction(ServerboundPlayerActionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.hasClientLoaded()) {
            BlockPos blockpos = packet.getPos();

            this.player.resetLastActionTime();
            ServerboundPlayerActionPacket.Action serverboundplayeractionpacket_action = packet.getAction();

            switch (serverboundplayeractionpacket_action) {
                case STAB:
                    if (this.player.isSpectator()) {
                        return;
                    } else {
                        ItemStack itemstack = this.player.getItemInHand(InteractionHand.MAIN_HAND);

                        if (this.player.cannotAttackWithItem(itemstack, 5)) {
                            return;
                        }

                        PiercingWeapon piercingweapon = (PiercingWeapon) itemstack.get(DataComponents.PIERCING_WEAPON);

                        if (piercingweapon != null) {
                            piercingweapon.attack(this.player, EquipmentSlot.MAINHAND);
                        }

                        return;
                    }
                case SWAP_ITEM_WITH_OFFHAND:
                    if (!this.player.isSpectator()) {
                        ItemStack itemstack1 = this.player.getItemInHand(InteractionHand.OFF_HAND);

                        this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, itemstack1);
                        this.player.stopUsingItem();
                    }

                    return;
                case DROP_ITEM:
                    if (!this.player.isSpectator()) {
                        this.player.drop(false);
                    }

                    return;
                case DROP_ALL_ITEMS:
                    if (!this.player.isSpectator()) {
                        this.player.drop(true);
                    }

                    return;
                case RELEASE_USE_ITEM:
                    this.player.releaseUsingItem();
                    return;
                case START_DESTROY_BLOCK:
                case ABORT_DESTROY_BLOCK:
                case STOP_DESTROY_BLOCK:
                    this.player.gameMode.handleBlockBreakAction(blockpos, serverboundplayeractionpacket_action, packet.getDirection(), this.player.level().getMaxY(), packet.getSequence());
                    this.ackBlockChangesUpTo(packet.getSequence());
                    return;
                default:
                    throw new IllegalArgumentException("Invalid player action");
            }
        }
    }

    private static boolean wasBlockPlacementAttempt(ServerPlayer player, ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        } else {
            boolean flag;
            label21:
            {
                Item item = itemStack.getItem();

                if (!(item instanceof BlockItem)) {
                    if (!(item instanceof BucketItem)) {
                        break label21;
                    }

                    BucketItem bucketitem = (BucketItem) item;

                    if (bucketitem.getContent() == Fluids.EMPTY) {
                        break label21;
                    }
                }

                if (!player.getCooldowns().isOnCooldown(itemStack)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    @Override
    public void handleUseItemOn(ServerboundUseItemOnPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.hasClientLoaded()) {
            this.ackBlockChangesUpTo(packet.getSequence());
            ServerLevel serverlevel = this.player.level();
            InteractionHand interactionhand = packet.getHand();
            ItemStack itemstack = this.player.getItemInHand(interactionhand);

            if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                BlockHitResult blockhitresult = packet.getHitResult();
                Vec3 vec3 = blockhitresult.getLocation();
                BlockPos blockpos = blockhitresult.getBlockPos();

                if (this.player.isWithinBlockInteractionRange(blockpos, 1.0D)) {
                    Vec3 vec31 = vec3.subtract(Vec3.atCenterOf(blockpos));
                    double d0 = 1.0000001D;

                    if (Math.abs(vec31.x()) < 1.0000001D && Math.abs(vec31.y()) < 1.0000001D && Math.abs(vec31.z()) < 1.0000001D) {
                        Direction direction = blockhitresult.getDirection();

                        this.player.resetLastActionTime();
                        int i = this.player.level().getMaxY();

                        if (blockpos.getY() <= i) {
                            if (this.awaitingPositionFromClient == null && serverlevel.mayInteract(this.player, blockpos)) {
                                InteractionResult interactionresult = this.player.gameMode.useItemOn(this.player, serverlevel, itemstack, interactionhand, blockhitresult);

                                if (interactionresult.consumesAction()) {
                                    CriteriaTriggers.ANY_BLOCK_USE.trigger(this.player, blockhitresult.getBlockPos(), itemstack.copy());
                                }

                                if (direction == Direction.UP && !interactionresult.consumesAction() && blockpos.getY() >= i && wasBlockPlacementAttempt(this.player, itemstack)) {
                                    Component component = Component.translatable("build.tooHigh", i).withStyle(ChatFormatting.RED);

                                    this.player.sendSystemMessage(component, true);
                                } else if (interactionresult instanceof InteractionResult.Success) {
                                    InteractionResult.Success interactionresult_success = (InteractionResult.Success) interactionresult;

                                    if (interactionresult_success.swingSource() == InteractionResult.SwingSource.SERVER) {
                                        this.player.swing(interactionhand, true);
                                    }
                                }
                            }
                        } else {
                            Component component1 = Component.translatable("build.tooHigh", i).withStyle(ChatFormatting.RED);

                            this.player.sendSystemMessage(component1, true);
                        }

                        this.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos));
                        this.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos.relative(direction)));
                    } else {
                        ServerGamePacketListenerImpl.LOGGER.warn("Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.", new Object[]{this.player.getGameProfile().name(), vec3, blockpos});
                    }
                }
            }
        }
    }

    @Override
    public void handleUseItem(ServerboundUseItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.hasClientLoaded()) {
            this.ackBlockChangesUpTo(packet.getSequence());
            ServerLevel serverlevel = this.player.level();
            InteractionHand interactionhand = packet.getHand();
            ItemStack itemstack = this.player.getItemInHand(interactionhand);

            this.player.resetLastActionTime();
            if (!itemstack.isEmpty() && itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                float f = Mth.wrapDegrees(packet.getYRot());
                float f1 = Mth.wrapDegrees(packet.getXRot());

                if (f1 != this.player.getXRot() || f != this.player.getYRot()) {
                    this.player.absSnapRotationTo(f, f1);
                }

                InteractionResult interactionresult = this.player.gameMode.useItem(this.player, serverlevel, itemstack, interactionhand);

                if (interactionresult instanceof InteractionResult.Success) {
                    InteractionResult.Success interactionresult_success = (InteractionResult.Success) interactionresult;

                    if (interactionresult_success.swingSource() == InteractionResult.SwingSource.SERVER) {
                        this.player.swing(interactionhand, true);
                    }
                }

            }
        }
    }

    @Override
    public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.isSpectator()) {
            for (ServerLevel serverlevel : this.server.getAllLevels()) {
                Entity entity = packet.getEntity(serverlevel);

                if (entity != null) {
                    this.player.teleportTo(serverlevel, entity.getX(), entity.getY(), entity.getZ(), Set.of(), entity.getYRot(), entity.getXRot(), true);
                    return;
                }
            }
        }

    }

    @Override
    public void handlePaddleBoat(ServerboundPaddleBoatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        Entity entity = this.player.getControlledVehicle();

        if (entity instanceof AbstractBoat abstractboat) {
            abstractboat.setPaddleState(packet.getLeft(), packet.getRight());
        }

    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        ServerGamePacketListenerImpl.LOGGER.info("{} lost connection: {}", this.player.getPlainTextName(), details.reason().getString());
        this.removePlayerFromWorld();
        super.onDisconnect(details);
    }

    private void removePlayerFromWorld() {
        this.chatMessageChain.close();
        this.server.invalidateStatus();
        this.server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
        this.player.disconnect();
        this.server.getPlayerList().remove(this.player);
        this.player.getTextFilter().leave();
    }

    public void ackBlockChangesUpTo(int packetSequenceNr) {
        if (packetSequenceNr < 0) {
            throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        } else {
            this.ackBlockChangesUpTo = Math.max(packetSequenceNr, this.ackBlockChangesUpTo);
        }
    }

    @Override
    public void handleSetCarriedItem(ServerboundSetCarriedItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (packet.getSlot() >= 0 && packet.getSlot() < Inventory.getSelectionSize()) {
            if (this.player.getInventory().getSelectedSlot() != packet.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().setSelectedSlot(packet.getSlot());
            this.player.resetLastActionTime();
        } else {
            ServerGamePacketListenerImpl.LOGGER.warn("{} tried to set an invalid carried item", this.player.getPlainTextName());
        }
    }

    @Override
    public void handleChat(ServerboundChatPacket packet) {
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(packet.lastSeenMessages());

        if (!optional.isEmpty()) {
            this.tryHandleChat(packet.message(), false, () -> {
                PlayerChatMessage playerchatmessage;

                try {
                    playerchatmessage = this.getSignedMessage(packet, (LastSeenMessages) optional.get());
                } catch (SignedMessageChain.DecodeException signedmessagechain_decodeexception) {
                    this.handleMessageDecodeFailure(signedmessagechain_decodeexception);
                    return;
                }

                CompletableFuture<FilteredText> completablefuture = this.filterTextPacket(playerchatmessage.signedContent());
                Component component = this.server.getChatDecorator().decorate(this.player, playerchatmessage.decoratedContent());

                this.chatMessageChain.append(completablefuture, (filteredtext) -> {
                    PlayerChatMessage playerchatmessage1 = playerchatmessage.withUnsignedContent(component).filter(filteredtext.mask());

                    this.broadcastChatMessage(playerchatmessage1);
                });
            });
        }
    }

    @Override
    public void handleChatCommand(ServerboundChatCommandPacket packet) {
        this.tryHandleChat(packet.command(), true, () -> {
            this.performUnsignedChatCommand(packet.command());
            this.detectRateSpam();
        });
    }

    private void performUnsignedChatCommand(String command) {
        ParseResults<CommandSourceStack> parseresults = this.parseCommand(command);

        if (this.server.enforceSecureProfile() && SignableCommand.hasSignableArguments(parseresults)) {
            ServerGamePacketListenerImpl.LOGGER.error("Received unsigned command packet from {}, but the command requires signable arguments: {}", this.player.getGameProfile().name(), command);
            this.player.sendSystemMessage(ServerGamePacketListenerImpl.INVALID_COMMAND_SIGNATURE);
        } else {
            this.server.getCommands().performCommand(parseresults, command);
        }
    }

    @Override
    public void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet) {
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(packet.lastSeenMessages());

        if (!optional.isEmpty()) {
            this.tryHandleChat(packet.command(), true, () -> {
                this.performSignedChatCommand(packet, (LastSeenMessages) optional.get());
                this.detectRateSpam();
            });
        }
    }

    private void performSignedChatCommand(ServerboundChatCommandSignedPacket packet, LastSeenMessages lastSeenMessages) {
        ParseResults<CommandSourceStack> parseresults = this.parseCommand(packet.command());

        Map<String, PlayerChatMessage> map;

        try {
            map = this.collectSignedArguments(packet, SignableCommand.of(parseresults), lastSeenMessages);
        } catch (SignedMessageChain.DecodeException signedmessagechain_decodeexception) {
            this.handleMessageDecodeFailure(signedmessagechain_decodeexception);
            return;
        }

        CommandSigningContext commandsigningcontext = new CommandSigningContext.SignedArguments(map);

        parseresults = Commands.<CommandSourceStack>mapSource(parseresults, (commandsourcestack) -> {
            return commandsourcestack.withSigningContext(commandsigningcontext, this.chatMessageChain);
        });
        this.server.getCommands().performCommand(parseresults, packet.command());
    }

    private void handleMessageDecodeFailure(SignedMessageChain.DecodeException e) {
        ServerGamePacketListenerImpl.LOGGER.warn("Failed to update secure chat state for {}: '{}'", this.player.getGameProfile().name(), e.getComponent().getString());
        this.player.sendSystemMessage(e.getComponent().copy().withStyle(ChatFormatting.RED));
    }

    private <S> Map<String, PlayerChatMessage> collectSignedArguments(ServerboundChatCommandSignedPacket packet, SignableCommand<S> command, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
        List<ArgumentSignatures.Entry> list = packet.argumentSignatures().entries();
        List<SignableCommand.Argument<S>> list1 = command.arguments();

        if (list.isEmpty()) {
            return this.collectUnsignedArguments(list1);
        } else {
            Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap();

            for (ArgumentSignatures.Entry argumentsignatures_entry : list) {
                SignableCommand.Argument<S> signablecommand_argument = command.getArgument(argumentsignatures_entry.name());

                if (signablecommand_argument == null) {
                    this.signedMessageDecoder.setChainBroken();
                    throw createSignedArgumentMismatchException(packet.command(), list, list1);
                }

                SignedMessageBody signedmessagebody = new SignedMessageBody(signablecommand_argument.value(), packet.timeStamp(), packet.salt(), lastSeenMessages);

                map.put(signablecommand_argument.name(), this.signedMessageDecoder.unpack(argumentsignatures_entry.signature(), signedmessagebody));
            }

            for (SignableCommand.Argument<S> signablecommand_argument1 : list1) {
                if (!map.containsKey(signablecommand_argument1.name())) {
                    throw createSignedArgumentMismatchException(packet.command(), list, list1);
                }
            }

            return map;
        }
    }

    private <S> Map<String, PlayerChatMessage> collectUnsignedArguments(List<SignableCommand.Argument<S>> parsedArguments) throws SignedMessageChain.DecodeException {
        Map<String, PlayerChatMessage> map = new HashMap();

        for (SignableCommand.Argument<S> signablecommand_argument : parsedArguments) {
            SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(signablecommand_argument.value());

            map.put(signablecommand_argument.name(), this.signedMessageDecoder.unpack((MessageSignature) null, signedmessagebody));
        }

        return map;
    }

    private static <S> SignedMessageChain.DecodeException createSignedArgumentMismatchException(String command, List<ArgumentSignatures.Entry> clientArguments, List<SignableCommand.Argument<S>> expectedArguments) {
        String s1 = (String) clientArguments.stream().map(ArgumentSignatures.Entry::name).collect(Collectors.joining(", "));
        String s2 = (String) expectedArguments.stream().map(SignableCommand.Argument::name).collect(Collectors.joining(", "));

        ServerGamePacketListenerImpl.LOGGER.error("Signed command mismatch between server and client ('{}'): got [{}] from client, but expected [{}]", new Object[]{command, s1, s2});
        return new SignedMessageChain.DecodeException(ServerGamePacketListenerImpl.INVALID_COMMAND_SIGNATURE);
    }

    private ParseResults<CommandSourceStack> parseCommand(String command) {
        CommandDispatcher<CommandSourceStack> commanddispatcher = this.server.getCommands().getDispatcher();

        return commanddispatcher.parse(command, this.player.createCommandSourceStack());
    }

    private void tryHandleChat(String message, boolean isCommand, Runnable chatHandler) {
        if (isChatMessageIllegal(message)) {
            this.disconnect((Component) Component.translatable("multiplayer.disconnect.illegal_characters"));
        } else if (!isCommand && this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED), false));
        } else {
            this.player.resetLastActionTime();
            this.server.execute(chatHandler);
        }
    }

    private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update update) {
        synchronized (this.lastSeenMessages) {
            Optional optional;

            try {
                LastSeenMessages lastseenmessages = this.lastSeenMessages.applyUpdate(update);

                optional = Optional.of(lastseenmessages);
            } catch (LastSeenMessagesValidator.ValidationException lastseenmessagesvalidator_validationexception) {
                ServerGamePacketListenerImpl.LOGGER.error("Failed to validate message acknowledgements from {}: {}", this.player.getPlainTextName(), lastseenmessagesvalidator_validationexception.getMessage());
                this.disconnect(ServerGamePacketListenerImpl.CHAT_VALIDATION_FAILED);
                return Optional.empty();
            }

            return optional;
        }
    }

    private static boolean isChatMessageIllegal(String message) {
        for (int i = 0; i < message.length(); ++i) {
            if (!StringUtil.isAllowedChatCharacter(message.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    private PlayerChatMessage getSignedMessage(ServerboundChatPacket packet, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
        SignedMessageBody signedmessagebody = new SignedMessageBody(packet.message(), packet.timeStamp(), packet.salt(), lastSeenMessages);

        return this.signedMessageDecoder.unpack(packet.signature(), signedmessagebody);
    }

    private void broadcastChatMessage(PlayerChatMessage message) {
        this.server.getPlayerList().broadcastChatMessage(message, this.player, ChatType.bind(ChatType.CHAT, (Entity) this.player));
        this.detectRateSpam();
    }

    private void detectRateSpam() {
        this.chatSpamThrottler.increment();
        if (!this.chatSpamThrottler.isUnderThreshold() && !this.server.getPlayerList().isOp(this.player.nameAndId()) && !this.server.isSingleplayerOwner(this.player.nameAndId())) {
            this.disconnect((Component) Component.translatable("disconnect.spam"));
        }

    }

    @Override
    public void handleChatAck(ServerboundChatAckPacket packet) {
        synchronized (this.lastSeenMessages) {
            try {
                this.lastSeenMessages.applyOffset(packet.offset());
            } catch (LastSeenMessagesValidator.ValidationException lastseenmessagesvalidator_validationexception) {
                ServerGamePacketListenerImpl.LOGGER.error("Failed to validate message acknowledgement offset from {}: {}", this.player.getPlainTextName(), lastseenmessagesvalidator_validationexception.getMessage());
                this.disconnect(ServerGamePacketListenerImpl.CHAT_VALIDATION_FAILED);
            }

        }
    }

    @Override
    public void handleAnimate(ServerboundSwingPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.resetLastActionTime();
        this.player.swing(packet.getHand());
    }

    @Override
    public void handlePlayerCommand(ServerboundPlayerCommandPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.hasClientLoaded()) {
            this.player.resetLastActionTime();
            switch (packet.getAction()) {
                case START_SPRINTING:
                    this.player.setSprinting(true);
                    break;
                case STOP_SPRINTING:
                    this.player.setSprinting(false);
                    break;
                case STOP_SLEEPING:
                    if (this.player.isSleeping()) {
                        this.player.stopSleepInBed(false, true);
                        this.awaitingPositionFromClient = this.player.position();
                    }
                    break;
                case START_RIDING_JUMP:
                    Entity entity = this.player.getControlledVehicle();

                    if (entity instanceof PlayerRideableJumping) {
                        PlayerRideableJumping playerrideablejumping = (PlayerRideableJumping) entity;
                        int i = packet.getData();

                        if (playerrideablejumping.canJump() && i > 0) {
                            playerrideablejumping.handleStartJump(i);
                        }
                    }
                    break;
                case STOP_RIDING_JUMP:
                    Entity entity1 = this.player.getControlledVehicle();

                    if (entity1 instanceof PlayerRideableJumping) {
                        PlayerRideableJumping playerrideablejumping1 = (PlayerRideableJumping) entity1;

                        playerrideablejumping1.handleStopJump();
                    }
                    break;
                case OPEN_INVENTORY:
                    Entity entity2 = this.player.getVehicle();

                    if (entity2 instanceof HasCustomInventoryScreen) {
                        HasCustomInventoryScreen hascustominventoryscreen = (HasCustomInventoryScreen) entity2;

                        hascustominventoryscreen.openCustomInventoryScreen(this.player);
                    }
                    break;
                case START_FALL_FLYING:
                    if (!this.player.tryToStartFallFlying()) {
                        this.player.stopFallFlying();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid client command!");
            }

        }
    }

    public void sendPlayerChatMessage(PlayerChatMessage message, ChatType.Bound chatType) {
        this.send(new ClientboundPlayerChatPacket(this.nextChatIndex++, message.link().sender(), message.link().index(), message.signature(), message.signedBody().pack(this.messageSignatureCache), message.unsignedContent(), message.filterMask(), chatType));
        MessageSignature messagesignature = message.signature();

        if (messagesignature != null) {
            this.messageSignatureCache.push(message.signedBody(), message.signature());
            int i;

            synchronized (this.lastSeenMessages) {
                this.lastSeenMessages.addPending(messagesignature);
                i = this.lastSeenMessages.trackedMessagesCount();
            }

            if (i > 4096) {
                this.disconnect((Component) Component.translatable("multiplayer.disconnect.too_many_pending_chats"));
            }

        }
    }

    public void sendDisguisedChatMessage(Component content, ChatType.Bound chatType) {
        this.send(new ClientboundDisguisedChatPacket(content, chatType));
    }

    public SocketAddress getRemoteAddress() {
        return this.connection.getRemoteAddress();
    }

    public void switchToConfig() {
        this.waitingForSwitchToConfig = true;
        this.removePlayerFromWorld();
        this.send(ClientboundStartConfigurationPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket packet) {
        this.connection.send(new ClientboundPongResponsePacket(packet.getTime()));
    }

    @Override
    public void handleInteract(ServerboundInteractPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.hasClientLoaded()) {
            final ServerLevel serverlevel = this.player.level();
            final Entity entity = packet.getTarget(serverlevel);

            this.player.resetLastActionTime();
            this.player.setShiftKeyDown(packet.isUsingSecondaryAction());
            if (entity != null) {
                if (!serverlevel.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                    return;
                }

                AABB aabb = entity.getBoundingBox();

                if (packet.isWithinRange(this.player, aabb, 3.0D)) {
                    packet.dispatch(new ServerboundInteractPacket.Handler() {
                        private void performInteraction(InteractionHand hand, ServerGamePacketListenerImpl.EntityInteraction interaction) {
                            ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(hand);

                            if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                                ItemStack itemstack1 = itemstack.copy();
                                InteractionResult interactionresult = interaction.run(ServerGamePacketListenerImpl.this.player, entity, hand);

                                if (interactionresult instanceof InteractionResult.Success) {
                                    InteractionResult.Success interactionresult_success = (InteractionResult.Success) interactionresult;
                                    ItemStack itemstack2 = interactionresult_success.wasItemInteraction() ? itemstack1 : ItemStack.EMPTY;

                                    CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(ServerGamePacketListenerImpl.this.player, itemstack2, entity);
                                    if (interactionresult_success.swingSource() == InteractionResult.SwingSource.SERVER) {
                                        ServerGamePacketListenerImpl.this.player.swing(hand, true);
                                    }
                                }

                            }
                        }

                        @Override
                        public void onInteraction(InteractionHand hand) {
                            this.performInteraction(hand, Player::interactOn);
                        }

                        @Override
                        public void onInteraction(InteractionHand hand, Vec3 location) {
                            this.performInteraction(hand, (serverplayer, entity1, interactionhand1) -> {
                                return entity1.interactAt(serverplayer, location, interactionhand1);
                            });
                        }

                        @Override
                        public void onAttack() {
                            if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb) && entity != ServerGamePacketListenerImpl.this.player) {
                                label33:
                                {
                                    if (entity instanceof AbstractArrow) {
                                        AbstractArrow abstractarrow = (AbstractArrow) entity;

                                        if (!abstractarrow.isAttackable()) {
                                            break label33;
                                        }
                                    }

                                    ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(InteractionHand.MAIN_HAND);

                                    if (!itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                                        return;
                                    }

                                    if (ServerGamePacketListenerImpl.this.player.cannotAttackWithItem(itemstack, 5)) {
                                        return;
                                    }

                                    ServerGamePacketListenerImpl.this.player.attack(entity);
                                    return;
                                }
                            }

                            ServerGamePacketListenerImpl.this.disconnect((Component) Component.translatable("multiplayer.disconnect.invalid_entity_attacked"));
                            ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to attack an invalid entity", ServerGamePacketListenerImpl.this.player.getPlainTextName());
                        }
                    });
                }
            }

        }
    }

    @Override
    public void handleClientCommand(ServerboundClientCommandPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.resetLastActionTime();
        ServerboundClientCommandPacket.Action serverboundclientcommandpacket_action = packet.getAction();

        switch (serverboundclientcommandpacket_action) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, true, Entity.RemovalReason.CHANGED_DIMENSION);
                    this.resetPosition();
                    this.restartClientLoadTimerAfterRespawn();
                    CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    this.player = this.server.getPlayerList().respawn(this.player, false, Entity.RemovalReason.KILLED);
                    this.resetPosition();
                    this.restartClientLoadTimerAfterRespawn();
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(GameType.SPECTATOR);
                        this.player.level().getGameRules().set(GameRules.SPECTATORS_GENERATE_CHUNKS, false, this.server);
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }

    }

    @Override
    public void handleContainerClose(ServerboundContainerClosePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.doCloseContainer();
    }

    @Override
    public void handleContainerClick(ServerboundContainerClickPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packet.containerId()) {
            if (this.player.isSpectator()) {
                this.player.containerMenu.sendAllDataToRemote();
            } else if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                int i = packet.slotNum();

                if (!this.player.containerMenu.isValidSlotIndex(i)) {
                    ServerGamePacketListenerImpl.LOGGER.debug("Player {} clicked invalid slot index: {}, available slots: {}", new Object[]{this.player.getPlainTextName(), i, this.player.containerMenu.slots.size()});
                } else {
                    boolean flag = packet.stateId() != this.player.containerMenu.getStateId();

                    this.player.containerMenu.suppressRemoteUpdates();
                    this.player.containerMenu.clicked(i, packet.buttonNum(), packet.clickType(), this.player);
                    ObjectIterator objectiterator = Int2ObjectMaps.fastIterable(packet.changedSlots()).iterator();

                    while (objectiterator.hasNext()) {
                        Int2ObjectMap.Entry<HashedStack> int2objectmap_entry = (Entry) objectiterator.next();

                        this.player.containerMenu.setRemoteSlotUnsafe(int2objectmap_entry.getIntKey(), (HashedStack) int2objectmap_entry.getValue());
                    }

                    this.player.containerMenu.setRemoteCarried(packet.carriedItem());
                    this.player.containerMenu.resumeRemoteUpdates();
                    if (flag) {
                        this.player.containerMenu.broadcastFullState();
                    } else {
                        this.player.containerMenu.broadcastChanges();
                    }

                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(ServerboundPlaceRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator() && this.player.containerMenu.containerId == packet.containerId()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                RecipeManager.ServerDisplayInfo recipemanager_serverdisplayinfo = this.server.getRecipeManager().getRecipeFromDisplay(packet.recipe());

                if (recipemanager_serverdisplayinfo != null) {
                    RecipeHolder<?> recipeholder = recipemanager_serverdisplayinfo.parent();

                    if (this.player.getRecipeBook().contains(recipeholder.id())) {
                        AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;

                        if (abstractcontainermenu instanceof RecipeBookMenu) {
                            RecipeBookMenu recipebookmenu = (RecipeBookMenu) abstractcontainermenu;

                            if (recipeholder.value().placementInfo().isImpossibleToPlace()) {
                                ServerGamePacketListenerImpl.LOGGER.debug("Player {} tried to place impossible recipe {}", this.player, recipeholder.id().identifier());
                                return;
                            }

                            RecipeBookMenu.PostPlaceAction recipebookmenu_postplaceaction = recipebookmenu.handlePlacement(packet.useMaxItems(), this.player.isCreative(), recipeholder, this.player.level(), this.player.getInventory());

                            if (recipebookmenu_postplaceaction == RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE) {
                                this.send(new ClientboundPlaceGhostRecipePacket(this.player.containerMenu.containerId, recipemanager_serverdisplayinfo.display().display()));
                            }
                        }

                    }
                }
            }
        }
    }

    @Override
    public void handleContainerButtonClick(ServerboundContainerButtonClickPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packet.containerId() && !this.player.isSpectator()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                boolean flag = this.player.containerMenu.clickMenuButton(this.player, packet.buttonId());

                if (flag) {
                    this.player.containerMenu.broadcastChanges();
                }

            }
        }
    }

    @Override
    public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.hasInfiniteMaterials()) {
            boolean flag = packet.slotNum() < 0;
            ItemStack itemstack = packet.itemStack();

            if (!itemstack.isItemEnabled(this.player.level().enabledFeatures())) {
                return;
            }

            boolean flag1 = packet.slotNum() >= 1 && packet.slotNum() <= 45;
            boolean flag2 = itemstack.isEmpty() || itemstack.getCount() <= itemstack.getMaxStackSize();

            if (flag1 && flag2) {
                this.player.inventoryMenu.getSlot(packet.slotNum()).setByPlayer(itemstack);
                this.player.inventoryMenu.setRemoteSlot(packet.slotNum(), itemstack);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2) {
                if (this.dropSpamThrottler.isUnderThreshold()) {
                    this.dropSpamThrottler.increment();
                    this.player.drop(itemstack, true);
                } else {
                    ServerGamePacketListenerImpl.LOGGER.warn("Player {} was dropping items too fast in creative mode, ignoring.", this.player.getPlainTextName());
                }
            }
        }

    }

    @Override
    public void handleSignUpdate(ServerboundSignUpdatePacket packet) {
        List<String> list = (List) Stream.of(packet.getLines()).map(ChatFormatting::stripFormatting).collect(Collectors.toList());

        this.filterTextPacket(list).thenAcceptAsync((list1) -> {
            this.updateSignText(packet, list1);
        }, this.server);
    }

    private void updateSignText(ServerboundSignUpdatePacket packet, List<FilteredText> lines) {
        this.player.resetLastActionTime();
        ServerLevel serverlevel = this.player.level();
        BlockPos blockpos = packet.getPos();

        if (serverlevel.hasChunkAt(blockpos)) {
            BlockEntity blockentity = serverlevel.getBlockEntity(blockpos);

            if (!(blockentity instanceof SignBlockEntity)) {
                return;
            }

            SignBlockEntity signblockentity = (SignBlockEntity) blockentity;

            signblockentity.updateSignText(this.player, packet.isFrontText(), lines);
        }

    }

    @Override
    public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.getAbilities().flying = packet.isFlying() && this.player.getAbilities().mayfly;
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        boolean flag = this.player.isModelPartShown(PlayerModelPart.HAT);

        this.player.updateOptions(packet.information());
        if (this.player.isModelPartShown(PlayerModelPart.HAT) != flag) {
            this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT, this.player));
        }

    }

    @Override
    public void handleChangeDifficulty(ServerboundChangeDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (!this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !this.isSingleplayerOwner()) {
            ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to change difficulty to {} without required permissions", this.player.getGameProfile().name(), packet.difficulty().getDisplayName());
        } else {
            this.server.setDifficulty(packet.difficulty(), false);
        }
    }

    @Override
    public void handleChangeGameMode(ServerboundChangeGameModePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (!GameModeCommand.PERMISSION_CHECK.check(this.player.permissions())) {
            ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to change game mode to {} without required permissions", this.player.getGameProfile().name(), packet.mode().getShortDisplayName().getString());
        } else {
            GameModeCommand.setGameMode(this.player, packet.mode());
        }
    }

    @Override
    public void handleLockDifficulty(ServerboundLockDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(packet.isLocked());
        }
    }

    @Override
    public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        RemoteChatSession.Data remotechatsession_data = packet.chatSession();
        ProfilePublicKey.Data profilepublickey_data = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
        ProfilePublicKey.Data profilepublickey_data1 = remotechatsession_data.profilePublicKey();

        if (!Objects.equals(profilepublickey_data, profilepublickey_data1)) {
            if (profilepublickey_data != null && profilepublickey_data1.expiresAt().isBefore(profilepublickey_data.expiresAt())) {
                this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY);
            } else {
                try {
                    SignatureValidator signaturevalidator = this.server.services().profileKeySignatureValidator();

                    if (signaturevalidator == null) {
                        ServerGamePacketListenerImpl.LOGGER.warn("Ignoring chat session from {} due to missing Services public key", this.player.getGameProfile().name());
                        return;
                    }

                    this.resetPlayerChatState(remotechatsession_data.validate(this.player.getGameProfile(), signaturevalidator));
                } catch (ProfilePublicKey.ValidationException profilepublickey_validationexception) {
                    ServerGamePacketListenerImpl.LOGGER.error("Failed to validate profile key: {}", profilepublickey_validationexception.getMessage());
                    this.disconnect(profilepublickey_validationexception.getComponent());
                }

            }
        }
    }

    @Override
    public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket packet) {
        if (!this.waitingForSwitchToConfig) {
            throw new IllegalStateException("Client acknowledged config, but none was requested");
        } else {
            this.connection.setupInboundProtocol(ConfigurationProtocols.SERVERBOUND, new ServerConfigurationPacketListenerImpl(this.server, this.connection, this.createCookie(this.player.clientInformation())));
        }
    }

    @Override
    public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.chunkSender.onChunkBatchReceivedByClient(packet.desiredChunksPerTick());
    }

    @Override
    public void handleDebugSubscriptionRequest(ServerboundDebugSubscriptionRequestPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        this.player.requestDebugSubscriptions(packet.subscriptions());
    }

    private void resetPlayerChatState(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
        this.signedMessageDecoder = chatSession.createMessageDecoder(this.player.getUUID());
        this.chatMessageChain.append(() -> {
            this.player.setChatSession(chatSession);
            this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT), List.of(this.player)));
        });
    }

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {}

    @Override
    public void handleClientTickEnd(ServerboundClientTickEndPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.level());
        if (!this.receivedMovementThisTick) {
            this.player.setKnownMovement(Vec3.ZERO);
        }

        this.receivedMovementThisTick = false;
    }

    private void handlePlayerKnownMovement(Vec3 movement) {
        if (movement.lengthSqr() > (double) 1.0E-5F) {
            this.player.resetLastActionTime();
        }

        this.player.setKnownMovement(movement);
        this.receivedMovementThisTick = true;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.player.hasInfiniteMaterials();
    }

    @Override
    public ServerPlayer getPlayer() {
        return this.player;
    }

    public boolean hasClientLoaded() {
        return !this.waitingForRespawn && this.clientLoadedTimeoutTimer <= 0;
    }

    public void tickClientLoadTimeout() {
        if (this.clientLoadedTimeoutTimer > 0) {
            --this.clientLoadedTimeoutTimer;
        }

    }

    private void markClientLoaded() {
        this.clientLoadedTimeoutTimer = 0;
    }

    public void markClientUnloadedAfterDeath() {
        this.waitingForRespawn = true;
    }

    private void restartClientLoadTimerAfterRespawn() {
        this.waitingForRespawn = false;
        this.clientLoadedTimeoutTimer = 60;
    }

    @FunctionalInterface
    private interface EntityInteraction {

        InteractionResult run(ServerPlayer player, Entity target, InteractionHand hand);
    }
}
