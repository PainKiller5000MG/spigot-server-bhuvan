package net.minecraft.server.level;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.HashOps;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.NautilusInventoryMenu;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerPlayer extends Player {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double BLOCK_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0D;
    public static final double ENTITY_INTERACTION_DISTANCE_VERIFICATION_BUFFER = 3.0D;
    public static final int ENDER_PEARL_TICKET_RADIUS = 2;
    public static final String ENDER_PEARLS_TAG = "ender_pearls";
    public static final String ENDER_PEARL_DIMENSION_TAG = "ender_pearl_dimension";
    public static final String TAG_DIMENSION = "Dimension";
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(Identifier.withDefaultNamespace("creative_mode_block_range"), 0.5D, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(Identifier.withDefaultNamespace("creative_mode_entity_range"), 2.0D, AttributeModifier.Operation.ADD_VALUE);
    private static final Component SPAWN_SET_MESSAGE = Component.translatable("block.minecraft.set_spawn");
    private static final AttributeModifier WAYPOINT_TRANSMIT_RANGE_CROUCH_MODIFIER = new AttributeModifier(Identifier.withDefaultNamespace("waypoint_transmit_range_crouch"), -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final boolean DEFAULT_SEEN_CREDITS = false;
    private static final boolean DEFAULT_SPAWN_EXTRA_PARTICLES_ON_FALL = false;
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    private ChatVisiblity chatVisibility;
    private ParticleStatus particleStatus;
    private boolean canChatColor;
    private long lastActionTime;
    private @Nullable Entity camera;
    public boolean isChangingDimension;
    public boolean seenCredits;
    private final ServerRecipeBook recipeBook;
    private @Nullable Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance;
    public String language;
    private @Nullable Vec3 startingToFallPosition;
    private @Nullable Vec3 enteredNetherPosition;
    private @Nullable Vec3 enteredLavaOnVehiclePosition;
    private SectionPos lastSectionPos;
    private ChunkTrackingView chunkTrackingView;
    private ServerPlayer.@Nullable RespawnConfig respawnConfig;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall;
    private WardenSpawnTracker wardenSpawnTracker;
    private @Nullable BlockPos raidOmenPosition;
    private Vec3 lastKnownClientMovement;
    private Input lastClientInput;
    private final Set<ThrownEnderpearl> enderPearls;
    private long timeEntitySatOnShoulder;
    private CompoundTag shoulderEntityLeft;
    private CompoundTag shoulderEntityRight;
    private final ContainerSynchronizer containerSynchronizer;
    private final ContainerListener containerListener;
    private @Nullable RemoteChatSession chatSession;
    public final @Nullable Object object;
    private final CommandSource commandSource;
    private Set<DebugSubscription<?>> requestedDebugSubscriptions;
    private int containerCounter;
    public boolean wonGame;

    public ServerPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(level, gameProfile);
        this.chatVisibility = ChatVisiblity.FULL;
        this.particleStatus = ParticleStatus.ALL;
        this.canChatColor = true;
        this.lastActionTime = Util.getMillis();
        this.seenCredits = false;
        this.requestedViewDistance = 2;
        this.language = "en_us";
        this.lastSectionPos = SectionPos.of(0, 0, 0);
        this.chunkTrackingView = ChunkTrackingView.EMPTY;
        this.spawnExtraParticlesOnFall = false;
        this.wardenSpawnTracker = new WardenSpawnTracker();
        this.lastKnownClientMovement = Vec3.ZERO;
        this.lastClientInput = Input.EMPTY;
        this.enderPearls = new HashSet();
        this.shoulderEntityLeft = new CompoundTag();
        this.shoulderEntityRight = new CompoundTag();
        this.containerSynchronizer = new ContainerSynchronizer() {
            private final LoadingCache<TypedDataComponent<?>, Integer> cache = CacheBuilder.newBuilder().maximumSize(256L).build(new CacheLoader<TypedDataComponent<?>, Integer>() {
                private final DynamicOps<HashCode> registryHashOps;

                {
                    this.registryHashOps = ServerPlayer.this.registryAccess().<HashCode>createSerializationContext(HashOps.CRC32C_INSTANCE);
                }

                public Integer load(TypedDataComponent<?> component) {
                    return ((HashCode) component.encodeValue(this.registryHashOps).getOrThrow((s) -> {
                        String s1 = String.valueOf(component);

                        return new IllegalArgumentException("Failed to hash " + s1 + ": " + s);
                    })).asInt();
                }
            });

            @Override
            public void sendInitialData(AbstractContainerMenu container, List<ItemStack> slotItems, ItemStack carriedItem, int[] dataSlots) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetContentPacket(container.containerId, container.incrementStateId(), slotItems, carriedItem));

                for (int i = 0; i < dataSlots.length; ++i) {
                    this.broadcastDataValue(container, i, dataSlots[i]);
                }

            }

            @Override
            public void sendSlotChange(AbstractContainerMenu container, int slotIndex, ItemStack itemStack) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(container.containerId, container.incrementStateId(), slotIndex, itemStack));
            }

            @Override
            public void sendCarriedChange(AbstractContainerMenu container, ItemStack itemStack) {
                ServerPlayer.this.connection.send(new ClientboundSetCursorItemPacket(itemStack));
            }

            @Override
            public void sendDataChange(AbstractContainerMenu container, int id, int value) {
                this.broadcastDataValue(container, id, value);
            }

            private void broadcastDataValue(AbstractContainerMenu container, int id, int value) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(container.containerId, id, value));
            }

            @Override
            public RemoteSlot createSlot() {
                LoadingCache loadingcache = this.cache;

                Objects.requireNonNull(this.cache);
                return new RemoteSlot.Synchronized(loadingcache::getUnchecked);
            }
        };
        this.containerListener = new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack changedItem) {
                Slot slot = container.getSlot(slotIndex);

                if (!(slot instanceof ResultSlot)) {
                    if (slot.container == ServerPlayer.this.getInventory()) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), changedItem);
                    }

                }
            }

            @Override
            public void dataChanged(AbstractContainerMenu container, int id, int value) {}
        };
        this.commandSource = new CommandSource() {
            @Override
            public boolean acceptsSuccess() {
                return (Boolean) ServerPlayer.this.level().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return true;
            }

            @Override
            public void sendSystemMessage(Component message) {
                ServerPlayer.this.sendSystemMessage(message);
            }
        };
        this.requestedDebugSubscriptions = Set.of();
        this.server = server;
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer((GameType) null), (GameType) null);
        this.recipeBook = new ServerRecipeBook((resourcekey, consumer) -> {
            server.getRecipeManager().listDisplaysForRecipe(resourcekey, consumer);
        });
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        this.updateOptions(clientInformation);
        this.object = null;
    }

    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos spawnSuggestion) {
        CompletableFuture<Vec3> completablefuture = PlayerSpawnFinder.findSpawn(level, spawnSuggestion);
        MinecraftServer minecraftserver = this.server;

        Objects.requireNonNull(completablefuture);
        minecraftserver.managedBlock(completablefuture::isDone);
        return BlockPos.containing((Position) completablefuture.join());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.wardenSpawnTracker = (WardenSpawnTracker) input.read("warden_spawn_tracker", WardenSpawnTracker.CODEC).orElseGet(WardenSpawnTracker::new);
        this.enteredNetherPosition = (Vec3) input.read("entered_nether_pos", Vec3.CODEC).orElse((Object) null);
        this.seenCredits = input.getBooleanOr("seenCredits", false);
        input.read("recipeBook", ServerRecipeBook.Packed.CODEC).ifPresent((serverrecipebook_packed) -> {
            this.recipeBook.loadUntrusted(serverrecipebook_packed, (resourcekey) -> {
                return this.server.getRecipeManager().byKey(resourcekey).isPresent();
            });
        });
        if (this.isSleeping()) {
            this.stopSleeping();
        }

        this.respawnConfig = (ServerPlayer.RespawnConfig) input.read("respawn", ServerPlayer.RespawnConfig.CODEC).orElse((Object) null);
        this.spawnExtraParticlesOnFall = input.getBooleanOr("spawn_extra_particles_on_fall", false);
        this.raidOmenPosition = (BlockPos) input.read("raid_omen_position", BlockPos.CODEC).orElse((Object) null);
        this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer(readPlayerMode(input, "playerGameType")), readPlayerMode(input, "previousPlayerGameType"));
        this.setShoulderEntityLeft((CompoundTag) input.read("ShoulderEntityLeft", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        this.setShoulderEntityRight((CompoundTag) input.read("ShoulderEntityRight", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("warden_spawn_tracker", WardenSpawnTracker.CODEC, this.wardenSpawnTracker);
        this.storeGameTypes(output);
        output.putBoolean("seenCredits", this.seenCredits);
        output.storeNullable("entered_nether_pos", Vec3.CODEC, this.enteredNetherPosition);
        this.saveParentVehicle(output);
        output.store("recipeBook", ServerRecipeBook.Packed.CODEC, this.recipeBook.pack());
        output.putString("Dimension", this.level().dimension().identifier().toString());
        output.storeNullable("respawn", ServerPlayer.RespawnConfig.CODEC, this.respawnConfig);
        output.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        output.storeNullable("raid_omen_position", BlockPos.CODEC, this.raidOmenPosition);
        this.saveEnderPearls(output);
        if (!this.getShoulderEntityLeft().isEmpty()) {
            output.store("ShoulderEntityLeft", CompoundTag.CODEC, this.getShoulderEntityLeft());
        }

        if (!this.getShoulderEntityRight().isEmpty()) {
            output.store("ShoulderEntityRight", CompoundTag.CODEC, this.getShoulderEntityRight());
        }

    }

    private void saveParentVehicle(ValueOutput playerOutput) {
        Entity entity = this.getRootVehicle();
        Entity entity1 = this.getVehicle();

        if (entity1 != null && entity != this && entity.hasExactlyOnePlayerPassenger()) {
            ValueOutput valueoutput1 = playerOutput.child("RootVehicle");

            valueoutput1.store("Attach", UUIDUtil.CODEC, entity1.getUUID());
            entity.save(valueoutput1.child("Entity"));
        }

    }

    public void loadAndSpawnParentVehicle(ValueInput playerInput) {
        Optional<ValueInput> optional = playerInput.child("RootVehicle");

        if (!optional.isEmpty()) {
            ServerLevel serverlevel = this.level();
            Entity entity = EntityType.loadEntityRecursive(((ValueInput) optional.get()).childOrEmpty("Entity"), serverlevel, EntitySpawnReason.LOAD, (entity1) -> {
                return !serverlevel.addWithUUID(entity1) ? null : entity1;
            });

            if (entity != null) {
                UUID uuid = (UUID) ((ValueInput) optional.get()).read("Attach", UUIDUtil.CODEC).orElse((Object) null);

                if (entity.getUUID().equals(uuid)) {
                    this.startRiding(entity, true, false);
                } else {
                    for (Entity entity1 : entity.getIndirectPassengers()) {
                        if (entity1.getUUID().equals(uuid)) {
                            this.startRiding(entity1, true, false);
                            break;
                        }
                    }
                }

                if (!this.isPassenger()) {
                    ServerPlayer.LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard();

                    for (Entity entity2 : entity.getIndirectPassengers()) {
                        entity2.discard();
                    }
                }

            }
        }
    }

    private void saveEnderPearls(ValueOutput playerOutput) {
        if (!this.enderPearls.isEmpty()) {
            ValueOutput.ValueOutputList valueoutput_valueoutputlist = playerOutput.childrenList("ender_pearls");

            for (ThrownEnderpearl thrownenderpearl : this.enderPearls) {
                if (thrownenderpearl.isRemoved()) {
                    ServerPlayer.LOGGER.warn("Trying to save removed ender pearl, skipping");
                } else {
                    ValueOutput valueoutput1 = valueoutput_valueoutputlist.addChild();

                    thrownenderpearl.save(valueoutput1);
                    valueoutput1.store("ender_pearl_dimension", Level.RESOURCE_KEY_CODEC, thrownenderpearl.level().dimension());
                }
            }
        }

    }

    public void loadAndSpawnEnderPearls(ValueInput playerInput) {
        playerInput.childrenListOrEmpty("ender_pearls").forEach(this::loadAndSpawnEnderPearl);
    }

    private void loadAndSpawnEnderPearl(ValueInput pearlInput) {
        Optional<ResourceKey<Level>> optional = pearlInput.<ResourceKey<Level>>read("ender_pearl_dimension", Level.RESOURCE_KEY_CODEC);

        if (!optional.isEmpty()) {
            ServerLevel serverlevel = this.level().getServer().getLevel((ResourceKey) optional.get());

            if (serverlevel != null) {
                Entity entity = EntityType.loadEntityRecursive(pearlInput, serverlevel, EntitySpawnReason.LOAD, (entity1) -> {
                    return !serverlevel.addWithUUID(entity1) ? null : entity1;
                });

                if (entity != null) {
                    placeEnderPearlTicket(serverlevel, entity.chunkPosition());
                } else {
                    ServerPlayer.LOGGER.warn("Failed to spawn player ender pearl in level ({}), skipping", optional.get());
                }
            } else {
                ServerPlayer.LOGGER.warn("Trying to load ender pearl without level ({}) being loaded, skipping", optional.get());
            }

        }
    }

    public void setExperiencePoints(int amount) {
        float f = (float) this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;
        float f2 = Mth.clamp((float) amount / f, 0.0F, f1);

        if (f2 != this.experienceProgress) {
            this.experienceProgress = f2;
            this.lastSentExp = -1;
        }
    }

    public void setExperienceLevels(int amount) {
        if (amount != this.experienceLevel) {
            this.experienceLevel = amount;
            this.lastSentExp = -1;
        }
    }

    @Override
    public void giveExperienceLevels(int amount) {
        if (amount != 0) {
            super.giveExperienceLevels(amount);
            this.lastSentExp = -1;
        }
    }

    @Override
    public void onEnchantmentPerformed(ItemStack itemStack, int enchantmentCost) {
        super.onEnchantmentPerformed(itemStack, enchantmentCost);
        this.lastSentExp = -1;
    }

    public void initMenu(AbstractContainerMenu container) {
        container.addSlotListener(this.containerListener);
        container.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    public void onInsideBlock(BlockState state) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, state);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        this.connection.tickClientLoadTimeout();
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        if (this.invulnerableTime > 0) {
            --this.invulnerableTime;
        }

        this.containerMenu.broadcastChanges();
        if (!this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        Entity entity = this.getCamera();

        if (entity != this) {
            if (entity.isAlive()) {
                this.absSnapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                this.level().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this, true);
    }

    private void updatePlayerAttributes() {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);

        if (attributeinstance != null) {
            if (this.isCreative()) {
                attributeinstance.addOrUpdateTransientModifier(ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attributeinstance.removeModifier(ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attributeinstance1 = this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);

        if (attributeinstance1 != null) {
            if (this.isCreative()) {
                attributeinstance1.addOrUpdateTransientModifier(ServerPlayer.CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attributeinstance1.removeModifier(ServerPlayer.CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attributeinstance2 = this.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE);

        if (attributeinstance2 != null) {
            if (this.isCrouching()) {
                attributeinstance2.addOrUpdateTransientModifier(ServerPlayer.WAYPOINT_TRANSMIT_RANGE_CROUCH_MODIFIER);
            } else {
                attributeinstance2.removeModifier(ServerPlayer.WAYPOINT_TRANSMIT_RANGE_CROUCH_MODIFIER);
            }
        }

    }

    public void doTick() {
        try {
            if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
                super.tick();
                if (!this.containerMenu.stillValid(this)) {
                    this.closeContainer();
                    this.containerMenu = this.inventoryMenu;
                }

                this.foodData.tick(this);
                this.awardStat(Stats.PLAY_TIME);
                this.awardStat(Stats.TOTAL_WORLD_TIME);
                if (this.isAlive()) {
                    this.awardStat(Stats.TIME_SINCE_DEATH);
                }

                if (this.isDiscrete()) {
                    this.awardStat(Stats.CROUCH_TIME);
                }

                if (!this.isSleeping()) {
                    this.awardStat(Stats.TIME_SINCE_REST);
                }
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                ItemStack itemstack = this.getInventory().getItem(i);

                if (!itemstack.isEmpty()) {
                    this.synchronizeSpecialItemUpdates(itemstack);
                }
            }

            if (this.getHealth() != this.lastSentHealth || this.lastSentFood != this.foodData.getFoodLevel() || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel()));
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float) this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float) this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float) this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float) this.lastRecordedExperience));
            }

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float) this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Player being ticked");

            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    private void synchronizeSpecialItemUpdates(ItemStack itemStack) {
        MapId mapid = (MapId) itemStack.get(DataComponents.MAP_ID);
        MapItemSavedData mapitemsaveddata = MapItem.getSavedData(mapid, this.level());

        if (mapitemsaveddata != null) {
            Packet<?> packet = mapitemsaveddata.getUpdatePacket(mapid, this);

            if (packet != null) {
                this.connection.send(packet);
            }
        }

    }

    @Override
    protected void tickRegeneration() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && (Boolean) this.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
            if (this.tickCount % 20 == 0) {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(1.0F);
                }

                float f = this.foodData.getSaturationLevel();

                if (f < 20.0F) {
                    this.foodData.setSaturation(f + 1.0F);
                }
            }

            if (this.tickCount % 10 == 0 && this.foodData.needsFood()) {
                this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
            }
        }

    }

    @Override
    public void handleShoulderEntities() {
        this.playShoulderEntityAmbientSound(this.getShoulderEntityLeft());
        this.playShoulderEntityAmbientSound(this.getShoulderEntityRight());
        if (this.fallDistance > 0.5D || this.isInWater() || this.getAbilities().flying || this.isSleeping() || this.isInPowderSnow) {
            this.removeEntitiesOnShoulder();
        }

    }

    private void playShoulderEntityAmbientSound(CompoundTag shoulderEntityTag) {
        if (!shoulderEntityTag.isEmpty() && !shoulderEntityTag.getBooleanOr("Silent", false)) {
            if (this.random.nextInt(200) == 0) {
                EntityType<?> entitytype = (EntityType) shoulderEntityTag.read("id", EntityType.CODEC).orElse((Object) null);

                if (entitytype == EntityType.PARROT && !Parrot.imitateNearbyMobs(this.level(), this)) {
                    this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), Parrot.getAmbient(this.level(), this.random), this.getSoundSource(), 1.0F, Parrot.getPitch(this.random));
                }
            }

        }
    }

    public boolean setEntityOnShoulder(CompoundTag entityTag) {
        if (!this.isPassenger() && this.onGround() && !this.isInWater() && !this.isInPowderSnow) {
            if (this.getShoulderEntityLeft().isEmpty()) {
                this.setShoulderEntityLeft(entityTag);
                this.timeEntitySatOnShoulder = this.level().getGameTime();
                return true;
            } else if (this.getShoulderEntityRight().isEmpty()) {
                this.setShoulderEntityRight(entityTag);
                this.timeEntitySatOnShoulder = this.level().getGameTime();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    protected void removeEntitiesOnShoulder() {
        if (this.timeEntitySatOnShoulder + 20L < this.level().getGameTime()) {
            this.respawnEntityOnShoulder(this.getShoulderEntityLeft());
            this.setShoulderEntityLeft(new CompoundTag());
            this.respawnEntityOnShoulder(this.getShoulderEntityRight());
            this.setShoulderEntityRight(new CompoundTag());
        }

    }

    private void respawnEntityOnShoulder(CompoundTag tag) {
        ServerLevel serverlevel = this.level();

        if (serverlevel instanceof ServerLevel) {
            ServerLevel serverlevel1 = serverlevel;

            if (!tag.isEmpty()) {
                try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), ServerPlayer.LOGGER)) {
                    EntityType.create(TagValueInput.create(problemreporter_scopedcollector.forChild(() -> {
                        return ".shoulder";
                    }), serverlevel1.registryAccess(), tag), serverlevel1, EntitySpawnReason.LOAD).ifPresent((entity) -> {
                        if (entity instanceof TamableAnimal tamableanimal) {
                            tamableanimal.setOwner(this);
                        }

                        entity.setPos(this.getX(), this.getY() + (double) 0.7F, this.getZ());
                        serverlevel1.addWithUUID(entity);
                    });
                }
            }
        }

    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0D && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null && this.currentImpulseImpactPos.y <= this.startingToFallPosition.y) {
                CriteriaTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }

    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }

    }

    private void updateScoreForCriteria(ObjectiveCriteria criteria, int value) {
        this.level().getScoreboard().forAllObjectives(criteria, this, (scoreaccess) -> {
            scoreaccess.set(value);
        });
    }

    @Override
    public void die(DamageSource source) {
        this.gameEvent(GameEvent.ENTITY_DIE);
        boolean flag = (Boolean) this.level().getGameRules().get(GameRules.SHOW_DEATH_MESSAGES);

        if (flag) {
            Component component = this.getCombatTracker().getDeathMessage();

            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), component), PacketSendListener.exceptionallySend(() -> {
                int i = 256;
                String s = component.getString(256);
                Component component1 = Component.translatable("death.attack.message_too_long", Component.literal(s).withStyle(ChatFormatting.YELLOW));
                Component component2 = Component.translatable("death.attack.even_more_magic", this.getDisplayName()).withStyle((style) -> {
                    return style.withHoverEvent(new HoverEvent.ShowText(component1));
                });

                return new ClientboundPlayerCombatKillPacket(this.getId(), component2);
            }));
            Team team = this.getTeam();

            if (team != null && team.getDeathMessageVisibility() != Team.Visibility.ALWAYS) {
                if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                    this.server.getPlayerList().broadcastSystemToTeam(this, component);
                } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                    this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, component);
                }
            } else {
                this.server.getPlayerList().broadcastSystemMessage(component, false);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if ((Boolean) this.level().getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }

        if (!this.isSpectator()) {
            this.dropAllDeathLoot(this.level(), source);
        }

        this.level().getScoreboard().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity livingentity = this.getKillCredit();

        if (livingentity != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(livingentity.getType()));
            livingentity.awardKillScore(this, source);
            this.createWitherRose(livingentity);
        }

        this.level().broadcastEntityEvent(this, (byte) 3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
        this.connection.markClientUnloadedAfterDeath();
    }

    private void tellNeutralMobsThatIDied() {
        AABB aabb = (new AABB(this.blockPosition())).inflate(32.0D, 10.0D, 32.0D);

        this.level().getEntitiesOfClass(Mob.class, aabb, EntitySelector.NO_SPECTATORS).stream().filter((mob) -> {
            return mob instanceof NeutralMob;
        }).forEach((mob) -> {
            ((NeutralMob) mob).playerDied(this.level(), this);
        });
    }

    @Override
    public void awardKillScore(Entity victim, DamageSource killingBlow) {
        if (victim != this) {
            super.awardKillScore(victim, killingBlow);
            Scoreboard scoreboard = this.level().getScoreboard();

            scoreboard.forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (victim instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                scoreboard.forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, victim, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(victim, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, victim, killingBlow);
        }
    }

    private void handleTeamKill(ScoreHolder source, ScoreHolder target, ObjectiveCriteria[] criteriaByTeam) {
        Scoreboard scoreboard = this.level().getScoreboard();
        PlayerTeam playerteam = scoreboard.getPlayersTeam(target.getScoreboardName());

        if (playerteam != null) {
            int i = playerteam.getColor().getId();

            if (i >= 0 && i < criteriaByTeam.length) {
                scoreboard.forAllObjectives(criteriaByTeam[i], source, ScoreAccess::increment);
            }
        }

    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        } else {
            Entity entity = source.getEntity();

            if (entity instanceof Player) {
                Player player = (Player) entity;

                if (!this.canHarmPlayer(player)) {
                    return false;
                }
            }

            if (entity instanceof AbstractArrow) {
                AbstractArrow abstractarrow = (AbstractArrow) entity;
                Entity entity1 = abstractarrow.getOwner();

                if (entity1 instanceof Player) {
                    Player player1 = (Player) entity1;

                    if (!this.canHarmPlayer(player1)) {
                        return false;
                    }
                }
            }

            return super.hurtServer(level, source, damage);
        }
    }

    @Override
    public boolean canHarmPlayer(Player target) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(target);
    }

    private boolean isPvpAllowed() {
        return this.level().isPvpAllowed();
    }

    public TeleportTransition findRespawnPositionAndUseSpawnBlock(boolean consumeSpawnBlock, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        ServerPlayer.RespawnConfig serverplayer_respawnconfig = this.getRespawnConfig();
        ServerLevel serverlevel = this.server.getLevel(ServerPlayer.RespawnConfig.getDimensionOrDefault(serverplayer_respawnconfig));

        if (serverlevel != null && serverplayer_respawnconfig != null) {
            Optional<ServerPlayer.RespawnPosAngle> optional = findRespawnAndUseSpawnBlock(serverlevel, serverplayer_respawnconfig, consumeSpawnBlock);

            if (optional.isPresent()) {
                ServerPlayer.RespawnPosAngle serverplayer_respawnposangle = (ServerPlayer.RespawnPosAngle) optional.get();

                return new TeleportTransition(serverlevel, serverplayer_respawnposangle.position(), Vec3.ZERO, serverplayer_respawnposangle.yaw(), serverplayer_respawnposangle.pitch(), postTeleportTransition);
            } else {
                return TeleportTransition.missingRespawnBlock(this, postTeleportTransition);
            }
        } else {
            return TeleportTransition.createDefault(this, postTeleportTransition);
        }
    }

    public boolean isReceivingWaypoints() {
        return this.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE) > 0.0D;
    }

    @Override
    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.WAYPOINT_RECEIVE_RANGE)) {
            ServerWaypointManager serverwaypointmanager = this.level().getWaypointManager();

            if (this.getAttributes().getValue(attribute) > 0.0D) {
                serverwaypointmanager.addPlayer(this);
            } else {
                serverwaypointmanager.removePlayer(this);
            }
        }

        super.onAttributeUpdated(attribute);
    }

    public static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(ServerLevel level, ServerPlayer.RespawnConfig respawnConfig, boolean consumeSpawnBlock) {
        LevelData.RespawnData leveldata_respawndata = respawnConfig.respawnData;
        BlockPos blockpos = leveldata_respawndata.pos();
        float f = leveldata_respawndata.yaw();
        float f1 = leveldata_respawndata.pitch();
        boolean flag1 = respawnConfig.forced;
        BlockState blockstate = level.getBlockState(blockpos);
        Block block = blockstate.getBlock();

        if (block instanceof RespawnAnchorBlock && (flag1 || (Integer) blockstate.getValue(RespawnAnchorBlock.CHARGE) > 0) && RespawnAnchorBlock.canSetSpawn(level, blockpos)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, blockpos);

            if (!flag1 && consumeSpawnBlock && optional.isPresent()) {
                level.setBlock(blockpos, (BlockState) blockstate.setValue(RespawnAnchorBlock.CHARGE, (Integer) blockstate.getValue(RespawnAnchorBlock.CHARGE) - 1), 3);
            }

            return optional.map((vec3) -> {
                return ServerPlayer.RespawnPosAngle.of(vec3, blockpos, 0.0F);
            });
        } else if (block instanceof BedBlock && ((BedRule) level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, blockpos)).canSetSpawn(level)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, blockpos, (Direction) blockstate.getValue(BedBlock.FACING), f).map((vec3) -> {
                return ServerPlayer.RespawnPosAngle.of(vec3, blockpos, 0.0F);
            });
        } else if (!flag1) {
            return Optional.empty();
        } else {
            boolean flag2 = block.isPossibleToRespawnInThis(blockstate);
            BlockState blockstate1 = level.getBlockState(blockpos.above());
            boolean flag3 = blockstate1.getBlock().isPossibleToRespawnInThis(blockstate1);

            return flag2 && flag3 ? Optional.of(new ServerPlayer.RespawnPosAngle(new Vec3((double) blockpos.getX() + 0.5D, (double) blockpos.getY() + 0.1D, (double) blockpos.getZ() + 0.5D), f, f1)) : Optional.empty();
        }
    }

    public void showEndCredits() {
        this.unRide();
        this.level().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 0.0F));
            this.seenCredits = true;
        }

    }

    @Override
    public @Nullable ServerPlayer teleport(TeleportTransition transition) {
        if (this.isRemoved()) {
            return null;
        } else {
            if (transition.missingRespawnBlock()) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            ServerLevel serverlevel = transition.newLevel();
            ServerLevel serverlevel1 = this.level();
            ResourceKey<Level> resourcekey = serverlevel1.dimension();

            if (!transition.asPassenger()) {
                this.removeVehicle();
            }

            if (serverlevel.dimension() == resourcekey) {
                this.connection.teleport(PositionMoveRotation.of(transition), transition.relatives());
                this.connection.resetPosition();
                transition.postTeleportTransition().onTransition(this);
                return this;
            } else {
                this.isChangingDimension = true;
                LevelData leveldata = serverlevel.getLevelData();

                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(serverlevel), (byte) 3));
                this.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();

                playerlist.sendPlayerPermissionLevel(this);
                serverlevel1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                ProfilerFiller profilerfiller = Profiler.get();

                profilerfiller.push("moving");
                if (resourcekey == Level.OVERWORLD && serverlevel.dimension() == Level.NETHER) {
                    this.enteredNetherPosition = this.position();
                }

                profilerfiller.pop();
                profilerfiller.push("placing");
                this.setServerLevel(serverlevel);
                this.connection.teleport(PositionMoveRotation.of(transition), transition.relatives());
                this.connection.resetPosition();
                serverlevel.addDuringTeleport(this);
                profilerfiller.pop();
                this.triggerDimensionChangeTriggers(serverlevel1);
                this.stopUsingItem();
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerlist.sendLevelInfo(this, serverlevel);
                playerlist.sendAllPlayerInfo(this);
                playerlist.sendActivePlayerEffects(this);
                transition.postTeleportTransition().onTransition(this);
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;
                this.teleportSpectators(transition, serverlevel1);
                return this;
            }
        }
    }

    @Override
    public void forceSetRotation(float yRot, boolean relativeY, float xRot, boolean relativeX) {
        super.forceSetRotation(yRot, relativeY, xRot, relativeX);
        this.connection.send(new ClientboundPlayerRotationPacket(yRot, relativeY, xRot, relativeX));
    }

    public void triggerDimensionChangeTriggers(ServerLevel oldLevel) {
        ResourceKey<Level> resourcekey = oldLevel.dimension();
        ResourceKey<Level> resourcekey1 = this.level().dimension();

        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        if (resourcekey == Level.NETHER && resourcekey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (resourcekey1 != Level.NETHER) {
            this.enteredNetherPosition = null;
        }

    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return player.isSpectator() ? this.getCamera() == this : (this.isSpectator() ? false : super.broadcastToPlayer(player));
    }

    @Override
    public void take(Entity entity, int orgCount) {
        super.take(entity, orgCount);
        this.containerMenu.broadcastChanges();
    }

    @Override
    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos pos) {
        Direction direction = (Direction) this.level().getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING);

        if (!this.isSleeping() && this.isAlive()) {
            BedRule bedrule = (BedRule) this.level().environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
            boolean flag = bedrule.canSleep(this.level());
            boolean flag1 = bedrule.canSetSpawn(this.level());

            if (!flag1 && !flag) {
                return Either.left(bedrule.asProblem());
            } else if (!this.bedInRange(pos, direction)) {
                return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(pos, direction)) {
                return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                if (flag1) {
                    this.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(this.level().dimension(), pos, this.getYRot(), this.getXRot()), false), true);
                }

                if (!flag) {
                    return Either.left(bedrule.asProblem());
                } else {
                    if (!this.isCreative()) {
                        double d0 = 8.0D;
                        double d1 = 5.0D;
                        Vec3 vec3 = Vec3.atBottomCenterOf(pos);
                        List<Monster> list = this.level().<Monster>getEntitiesOfClass(Monster.class, new AABB(vec3.x() - 8.0D, vec3.y() - 5.0D, vec3.z() - 8.0D, vec3.x() + 8.0D, vec3.y() + 5.0D, vec3.z() + 8.0D), (monster) -> {
                            return monster.isPreventingPlayerRest(this.level(), this);
                        });

                        if (!list.isEmpty()) {
                            return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                        }
                    }

                    Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(pos).ifRight((unit) -> {
                        this.awardStat(Stats.SLEEP_IN_BED);
                        CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                    });

                    if (!this.level().canSleepThroughNights()) {
                        this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
                    }

                    this.level().updateSleepingPlayerList();
                    return either;
                }
            }
        } else {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @Override
    public void startSleeping(BlockPos bedPosition) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(bedPosition);
    }

    private boolean bedInRange(BlockPos pos, Direction direction) {
        return this.isReachableBedBlock(pos) || this.isReachableBedBlock(pos.relative(direction.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos bedBlockPos) {
        Vec3 vec3 = Vec3.atBottomCenterOf(bedBlockPos);

        return Math.abs(this.getX() - vec3.x()) <= 3.0D && Math.abs(this.getY() - vec3.y()) <= 2.0D && Math.abs(this.getZ() - vec3.z()) <= 3.0D;
    }

    private boolean bedBlocked(BlockPos pos, Direction direction) {
        BlockPos blockpos1 = pos.above();

        return !this.freeAt(blockpos1) || !this.freeAt(blockpos1.relative(direction.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList) {
        if (this.isSleeping()) {
            this.level().getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(forcefulWakeUp, updateLevelList);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        }

    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(level, source) || this.isChangingDimension() && !source.is(DamageTypes.ENDER_PEARL) || !this.connection.hasClientLoaded();
    }

    @Override
    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        if (!this.isSpectator()) {
            super.onChangedBlock(level, pos);
        }

    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (this.spawnExtraParticlesOnFall && onGround && this.fallDistance > 0.0D) {
            Vec3 vec3 = pos.getCenter().add(0.0D, 0.5D, 0.0D);
            int i = (int) Mth.clamp(50.0D * this.fallDistance, 0.0D, 200.0D);

            this.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, onState), vec3.x, vec3.y, vec3.z, i, (double) 0.3F, (double) 0.3F, (double) 0.3F, (double) 0.15F);
            this.spawnExtraParticlesOnFall = false;
        }

        super.checkFallDamage(ya, onGround, onState, pos);
    }

    @Override
    public void onExplosionHit(@Nullable Entity explosionCausedBy) {
        super.onExplosionHit(explosionCausedBy);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = explosionCausedBy;
        this.setIgnoreFallDamageFromCurrentImpulse(explosionCausedBy != null && explosionCausedBy.getType() == EntityType.WIND_CHARGE);
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }

    }

    @Override
    public void openTextEdit(SignBlockEntity sign, boolean isFrontText) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), sign.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(sign.getBlockPos(), isFrontText));
    }

    @Override
    public void openDialog(Holder<Dialog> dialog) {
        this.connection.send(new ClientboundShowDialogPacket(dialog));
    }

    public void nextContainerCounter() {
        this.containerCounter = this.containerCounter % 100 + 1;
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider provider) {
        if (provider == null) {
            return OptionalInt.empty();
        } else {
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu abstractcontainermenu = provider.createMenu(this.containerCounter, this.getInventory(), this);

            if (abstractcontainermenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                this.connection.send(new ClientboundOpenScreenPacket(abstractcontainermenu.containerId, abstractcontainermenu.getType(), provider.getDisplayName()));
                this.initMenu(abstractcontainermenu);
                this.containerMenu = abstractcontainermenu;
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int containerId, MerchantOffers offers, int merchantLevel, int merchantXp, boolean showProgressBar, boolean canRestock) {
        this.connection.send(new ClientboundMerchantOffersPacket(containerId, offers, merchantLevel, merchantXp, showProgressBar, canRestock));
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container container) {
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        this.nextContainerCounter();
        int i = horse.getInventoryColumns();

        this.connection.send(new ClientboundMountScreenOpenPacket(this.containerCounter, i, horse.getId()));
        this.containerMenu = new HorseInventoryMenu(this.containerCounter, this.getInventory(), container, horse, i);
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openNautilusInventory(AbstractNautilus nautilus, Container container) {
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        this.nextContainerCounter();
        int i = nautilus.getInventoryColumns();

        this.connection.send(new ClientboundMountScreenOpenPacket(this.containerCounter, i, nautilus.getId()));
        this.containerMenu = new NautilusInventoryMenu(this.containerCounter, this.getInventory(), container, nautilus, i);
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openItemGui(ItemStack itemStack, InteractionHand hand) {
        if (itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            if (WrittenBookContent.resolveForItem(itemStack, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(hand));
        }

    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(commandBlock, BlockEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    public void rideTick() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.rideTick();
        this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    public void checkMovementStatistics(double dx, double dy, double dz) {
        if (!this.isPassenger() && !didNotMove(dx, dy, dz)) {
            if (this.isSwimming()) {
                int i = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);

                if (i > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(0.01F * (float) i * 0.01F);
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                int j = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);

                if (j > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, j);
                    this.causeFoodExhaustion(0.01F * (float) j * 0.01F);
                }
            } else if (this.isInWater()) {
                int k = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0F);

                if (k > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, k);
                    this.causeFoodExhaustion(0.01F * (float) k * 0.01F);
                }
            } else if (this.onClimbable()) {
                if (dy > 0.0D) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int) Math.round(dy * 100.0D));
                }
            } else if (this.onGround()) {
                int l = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0F);

                if (l > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, l);
                        this.causeFoodExhaustion(0.1F * (float) l * 0.01F);
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, l);
                        this.causeFoodExhaustion(0.0F * (float) l * 0.01F);
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, l);
                        this.causeFoodExhaustion(0.0F * (float) l * 0.01F);
                    }
                }
            } else if (this.isFallFlying()) {
                int i1 = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);

                this.awardStat(Stats.AVIATE_ONE_CM, i1);
            } else {
                int j1 = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0F);

                if (j1 > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, j1);
                }
            }

        }
    }

    private void checkRidingStatistics(double dx, double dy, double dz) {
        if (this.isPassenger() && !didNotMove(dx, dy, dz)) {
            int i = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
            Entity entity = this.getVehicle();

            if (entity instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, i);
            } else if (entity instanceof AbstractBoat) {
                this.awardStat(Stats.BOAT_ONE_CM, i);
            } else if (entity instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, i);
            } else if (entity instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, i);
            } else if (entity instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, i);
            } else if (entity instanceof HappyGhast) {
                this.awardStat(Stats.HAPPY_GHAST_ONE_CM, i);
            } else if (entity instanceof AbstractNautilus) {
                this.awardStat(Stats.NAUTILUS_ONE_CM, i);
            }

        }
    }

    private static boolean didNotMove(double dx, double dy, double dz) {
        return dx == 0.0D && dy == 0.0D && dz == 0.0D;
    }

    @Override
    public void awardStat(Stat<?> stat, int count) {
        this.stats.increment(this, stat, count);
        this.level().getScoreboard().forAllObjectives(stat, this, (scoreaccess) -> {
            scoreaccess.add(count);
        });
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.level().getScoreboard().forAllObjectives(stat, this, ScoreAccess::reset);
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.addRecipes(recipes, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> itemStacks) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, recipe.id(), itemStacks);
    }

    @Override
    public void awardRecipesByKey(List<ResourceKey<Recipe<?>>> recipeIds) {
        List<RecipeHolder<?>> list1 = (List) recipeIds.stream().flatMap((resourcekey) -> {
            return this.server.getRecipeManager().byKey(resourcekey).stream();
        }).collect(Collectors.toList());

        this.awardRecipes(list1);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> recipe) {
        return this.recipeBook.removeRecipes(recipe, this);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        this.awardStat(Stats.JUMP);
        if (this.isSprinting()) {
            this.causeFoodExhaustion(0.2F);
        } else {
            this.causeFoodExhaustion(0.05F);
        }

    }

    @Override
    public void giveExperiencePoints(int i) {
        if (i != 0) {
            super.giveExperiencePoints(i);
            this.lastSentExp = -1;
        }
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }

    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
    }

    @Override
    public void displayClientMessage(Component component, boolean overlayMessage) {
        this.sendSystemMessage(component, overlayMessage);
    }

    @Override
    protected void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, (byte) 9));
            super.completeUsingItem();
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 pos) {
        super.lookAt(anchor, pos);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchor, pos.x, pos.y, pos.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor fromAnchor, Entity entity, EntityAnchorArgument.Anchor toAnchor) {
        Vec3 vec3 = toAnchor.apply(entity);

        super.lookAt(fromAnchor, vec3);
        this.connection.send(new ClientboundPlayerLookAtPacket(fromAnchor, entity, toAnchor));
    }

    public void restoreFrom(ServerPlayer oldPlayer, boolean restoreAll) {
        this.wardenSpawnTracker = oldPlayer.wardenSpawnTracker;
        this.chatSession = oldPlayer.chatSession;
        this.gameMode.setGameModeForPlayer(oldPlayer.gameMode.getGameModeForPlayer(), oldPlayer.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        this.getAttributes().assignBaseValues(oldPlayer.getAttributes());
        if (restoreAll) {
            this.getAttributes().assignPermanentModifiers(oldPlayer.getAttributes());
            this.setHealth(oldPlayer.getHealth());
            this.foodData = oldPlayer.foodData;

            for (MobEffectInstance mobeffectinstance : oldPlayer.getActiveEffects()) {
                this.addEffect(new MobEffectInstance(mobeffectinstance));
            }

            this.transferInventoryXpAndScore(oldPlayer);
            this.portalProcess = oldPlayer.portalProcess;
        } else {
            this.setHealth(this.getMaxHealth());
            if ((Boolean) this.level().getGameRules().get(GameRules.KEEP_INVENTORY) || oldPlayer.isSpectator()) {
                this.transferInventoryXpAndScore(oldPlayer);
            }
        }

        this.enchantmentSeed = oldPlayer.enchantmentSeed;
        this.enderChestInventory = oldPlayer.enderChestInventory;
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (Byte) oldPlayer.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        this.recipeBook.copyOverData(oldPlayer.recipeBook);
        this.seenCredits = oldPlayer.seenCredits;
        this.enteredNetherPosition = oldPlayer.enteredNetherPosition;
        this.chunkTrackingView = oldPlayer.chunkTrackingView;
        this.requestedDebugSubscriptions = oldPlayer.requestedDebugSubscriptions;
        this.setShoulderEntityLeft(oldPlayer.getShoulderEntityLeft());
        this.setShoulderEntityRight(oldPlayer.getShoulderEntityRight());
        this.setLastDeathLocation(oldPlayer.getLastDeathLocation());
        this.waypointIcon().copyFrom(oldPlayer.waypointIcon());
    }

    private void transferInventoryXpAndScore(Player oldPlayer) {
        this.getInventory().replaceWith(oldPlayer.getInventory());
        this.experienceLevel = oldPlayer.experienceLevel;
        this.totalExperience = oldPlayer.totalExperience;
        this.experienceProgress = oldPlayer.experienceProgress;
        this.setScore(oldPlayer.getScore());
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        super.onEffectAdded(effect, source);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect, true));
        if (effect.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, source);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, @Nullable Entity source) {
        super.onEffectUpdated(effect, doRefreshAttributes, source);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect, false));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, source);
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        super.onEffectsRemoved(effects);

        for (MobEffectInstance mobeffectinstance : effects) {
            this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), mobeffectinstance.getEffect()));
            if (mobeffectinstance.is(MobEffects.LEVITATION)) {
                this.levitationStartPos = null;
            }
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, (Entity) null);
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        this.connection.teleport(new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, 0.0F, 0.0F), Relative.union(Relative.DELTA, Relative.ROTATION));
    }

    @Override
    public void teleportRelative(double dx, double dy, double dz) {
        this.connection.teleport(new PositionMoveRotation(new Vec3(dx, dy, dz), Vec3.ZERO, 0.0F, 0.0F), Relative.ALL);
    }

    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relatives, float newYRot, float newXRot, boolean resetCamera) {
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (resetCamera) {
            this.setCamera(this);
        }

        boolean flag1 = super.teleportTo(level, x, y, z, relatives, newYRot, newXRot, resetCamera);

        if (flag1) {
            this.setYHeadRot(relatives.contains(Relative.Y_ROT) ? this.getYHeadRot() + newYRot : newYRot);
            this.connection.resetFlyingTicks();
        }

        return flag1;
    }

    @Override
    public void snapTo(double x, double y, double z) {
        super.snapTo(x, y, z);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity entity) {
        this.level().getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundAnimatePacket(entity, 4));
    }

    @Override
    public void magicCrit(Entity entity) {
        this.level().getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundAnimatePacket(entity, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) super.level();
    }

    public boolean setGameMode(GameType mode) {
        boolean flag = this.isSpectator();

        if (!this.gameMode.changeGameModeForPlayer(mode)) {
            return false;
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float) mode.getId()));
            if (mode == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
                this.stopUsingItem();
                EnchantmentHelper.stopLocationBasedEffects(this);
            } else {
                this.setCamera(this);
                if (flag) {
                    EnchantmentHelper.runLocationChangedEffects(this.level(), this);
                }
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return true;
        }
    }

    @Override
    public GameType gameMode() {
        return this.gameMode.getGameModeForPlayer();
    }

    public CommandSource commandSource() {
        return this.commandSource;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(this.commandSource(), this.position(), this.getRotationVector(), this.level(), this.permissions(), this.getPlainTextName(), this.getDisplayName(), this.server, this);
    }

    public void sendSystemMessage(Component message) {
        this.sendSystemMessage(message, false);
    }

    public void sendSystemMessage(Component message, boolean overlay) {
        if (this.acceptsSystemMessages(overlay)) {
            this.connection.send(new ClientboundSystemChatPacket(message, overlay), PacketSendListener.exceptionallySend(() -> {
                if (this.acceptsSystemMessages(false)) {
                    int i = 256;
                    String s = message.getString(256);
                    Component component1 = Component.literal(s).withStyle(ChatFormatting.YELLOW);

                    return new ClientboundSystemChatPacket(Component.translatable("multiplayer.message_not_delivered", component1).withStyle(ChatFormatting.RED), false);
                } else {
                    return null;
                }
            }));
        }
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound chatType) {
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filtered, chatType);
        }

    }

    public String getIpAddress() {
        SocketAddress socketaddress = this.connection.getRemoteAddress();

        if (socketaddress instanceof InetSocketAddress inetsocketaddress) {
            return InetAddresses.toAddrString(inetsocketaddress.getAddress());
        } else {
            return "<unknown>";
        }
    }

    public void updateOptions(ClientInformation information) {
        this.language = information.language();
        this.requestedViewDistance = information.viewDistance();
        this.chatVisibility = information.chatVisibility();
        this.canChatColor = information.chatColors();
        this.textFilteringEnabled = information.textFilteringEnabled();
        this.allowsListing = information.allowsListing();
        this.particleStatus = information.particleStatus();
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) information.modelCustomisation());
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MAIN_HAND, information.mainHand());
    }

    public ClientInformation clientInformation() {
        int i = (Byte) this.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION);

        return new ClientInformation(this.language, this.requestedViewDistance, this.chatVisibility, this.canChatColor, i, this.getMainArm(), this.textFilteringEnabled, this.allowsListing, this.particleStatus);
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean overlay) {
        return this.chatVisibility == ChatVisiblity.HIDDEN ? overlay : true;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus status) {
        this.connection.send(new ClientboundServerDataPacket(status.description(), status.favicon().map(ServerStatus.Favicon::iconBytes)));
    }

    @Override
    public PermissionSet permissions() {
        return this.server.getProfilePermissions(this.nameAndId());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }

    }

    public Entity getCamera() {
        return (Entity) (this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity newCamera) {
        Entity entity1 = this.getCamera();

        this.camera = (Entity) (newCamera == null ? this : newCamera);
        if (entity1 != this.camera) {
            Level level = this.camera.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.teleportTo(serverlevel, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot(), false);
            }

            if (newCamera != null) {
                this.level().getChunkSource().move(this);
            }

            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.connection.resetPosition();
        }

    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }

    }

    @Override
    public void attack(Entity entity) {
        if (this.isSpectator()) {
            this.setCamera(entity);
        } else {
            super.attack(entity);
        }

    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    public @Nullable Component getTabListDisplayName() {
        return null;
    }

    public int getTabListOrder() {
        return 0;
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    public ServerPlayer.@Nullable RespawnConfig getRespawnConfig() {
        return this.respawnConfig;
    }

    public void copyRespawnPosition(ServerPlayer player) {
        this.setRespawnPosition(player.respawnConfig, false);
    }

    public void setRespawnPosition(ServerPlayer.@Nullable RespawnConfig respawnConfig, boolean showMessage) {
        if (showMessage && respawnConfig != null && !respawnConfig.isSamePosition(this.respawnConfig)) {
            this.sendSystemMessage(ServerPlayer.SPAWN_SET_MESSAGE);
        }

        this.respawnConfig = respawnConfig;
    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos lastSectionPos) {
        this.lastSectionPos = lastSectionPos;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunkTrackingView) {
        this.chunkTrackingView = chunkTrackingView;
    }

    @Override
    public ItemEntity drop(ItemStack itemStack, boolean randomly, boolean thrownFromHand) {
        ItemEntity itementity = super.drop(itemStack, randomly, thrownFromHand);

        if (thrownFromHand) {
            ItemStack itemstack1 = itementity != null ? itementity.getItem() : ItemStack.EMPTY;

            if (!itemstack1.isEmpty()) {
                this.awardStat(Stats.ITEM_DROPPED.get(itemstack1.getItem()), itemStack.getCount());
                this.awardStat(Stats.DROP);
            }
        }

        return itementity;
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel level) {
        this.setLevel(level);
        this.gameMode.setLevel(level);
    }

    private static @Nullable GameType readPlayerMode(ValueInput playerInput, String modeTag) {
        return (GameType) playerInput.read(modeTag, GameType.LEGACY_ID_CODEC).orElse((Object) null);
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType loadedGameType) {
        GameType gametype1 = this.server.getForcedGameType();

        return gametype1 != null ? gametype1 : (loadedGameType != null ? loadedGameType : this.server.getDefaultGameType());
    }

    private void storeGameTypes(ValueOutput playerOutput) {
        playerOutput.store("playerGameType", GameType.LEGACY_ID_CODEC, this.gameMode.getGameModeForPlayer());
        GameType gametype = this.gameMode.getPreviousGameModeForPlayer();

        playerOutput.storeNullable("previousPlayerGameType", GameType.LEGACY_ID_CODEC, gametype);
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer serverPlayer) {
        return serverPlayer == this ? false : this.textFilteringEnabled || serverPlayer.textFilteringEnabled;
    }

    @Override
    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        return super.mayInteract(level, pos) && level.mayInteract(this, pos);
    }

    @Override
    protected void updateUsingItem(ItemStack useItem) {
        CriteriaTriggers.USING_ITEM.trigger(this, useItem);
        super.updateUsingItem(useItem);
    }

    public void drop(boolean all) {
        Inventory inventory = this.getInventory();
        ItemStack itemstack = inventory.removeFromSelected(all);

        this.containerMenu.findSlot(inventory, inventory.getSelectedSlot()).ifPresent((i) -> {
            this.containerMenu.setRemoteSlot(i, inventory.getSelectedItem());
        });
        if (this.useItem.isEmpty()) {
            this.stopUsingItem();
        }

        this.drop(itemstack, false, true);
    }

    @Override
    public void handleExtraItemsCreatedOnUse(ItemStack extraItems) {
        if (!this.getInventory().add(extraItems)) {
            this.drop(extraItems, false);
        }

    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean toggle) {
        this.spawnExtraParticlesOnFall = toggle;
    }

    @Override
    public void onItemPickup(ItemEntity entity) {
        super.onItemPickup(entity);
        Entity entity1 = entity.getOwner();

        if (entity1 != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, entity.getItem(), entity1);
        }

    }

    public void setChatSession(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
    }

    public @Nullable RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double xd, double zd) {
        this.hurtDir = (float) (Mth.atan2(zd, xd) * (double) (180F / (float) Math.PI) - (double) this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        if (super.startRiding(entityToRide, force, sendEventAndTriggers)) {
            entityToRide.positionRider(this);
            this.connection.teleport(new PositionMoveRotation(this.position(), Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            if (entityToRide instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity) entityToRide;

                this.server.getPlayerList().sendActiveEffects(livingentity, this.connection);
            }

            this.connection.send(new ClientboundSetPassengersPacket(entityToRide));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removeVehicle() {
        Entity entity = this.getVehicle();

        super.removeVehicle();
        if (entity instanceof LivingEntity livingentity) {
            for (MobEffectInstance mobeffectinstance : livingentity.getActiveEffects()) {
                this.connection.send(new ClientboundRemoveMobEffectPacket(entity.getId(), mobeffectinstance.getEffect()));
            }
        }

        if (entity != null) {
            this.connection.send(new ClientboundSetPassengersPacket(entity));
        }

    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel level) {
        return new CommonPlayerSpawnInfo(level.dimensionTypeRegistration(), level.dimension(), BiomeManager.obfuscateSeed(level.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), level.isDebug(), level.isFlat(), this.getLastDeathLocation(), this.getPortalCooldown(), level.getSeaLevel());
    }

    public void setRaidOmenPosition(BlockPos raidOmenPosition) {
        this.raidOmenPosition = raidOmenPosition;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    public @Nullable BlockPos getRaidOmenPosition() {
        return this.raidOmenPosition;
    }

    @Override
    public Vec3 getKnownMovement() {
        Entity entity = this.getVehicle();

        return entity != null && entity.getControllingPassenger() != this ? entity.getKnownMovement() : this.lastKnownClientMovement;
    }

    @Override
    public Vec3 getKnownSpeed() {
        Entity entity = this.getVehicle();

        return entity != null && entity.getControllingPassenger() != this ? entity.getKnownSpeed() : this.lastKnownClientMovement;
    }

    public void setKnownMovement(Vec3 lastKnownClientMovement) {
        this.lastKnownClientMovement = lastKnownClientMovement;
    }

    @Override
    protected float getEnchantedDamage(Entity entity, float dmg, DamageSource damageSource) {
        return EnchantmentHelper.modifyDamage(this.level(), this.getWeaponItem(), entity, damageSource, dmg);
    }

    @Override
    public void onEquippedItemBroken(Item brokenItem, EquipmentSlot inSlot) {
        super.onEquippedItemBroken(brokenItem, inSlot);
        this.awardStat(Stats.ITEM_BROKEN.get(brokenItem));
    }

    public Input getLastClientInput() {
        return this.lastClientInput;
    }

    public void setLastClientInput(Input lastClientInput) {
        this.lastClientInput = lastClientInput;
    }

    public Vec3 getLastClientMoveIntent() {
        float f = this.lastClientInput.left() == this.lastClientInput.right() ? 0.0F : (this.lastClientInput.left() ? 1.0F : -1.0F);
        float f1 = this.lastClientInput.forward() == this.lastClientInput.backward() ? 0.0F : (this.lastClientInput.forward() ? 1.0F : -1.0F);

        return getInputVector(new Vec3((double) f, 0.0D, (double) f1), 1.0F, this.getYRot());
    }

    public void registerEnderPearl(ThrownEnderpearl enderPearl) {
        this.enderPearls.add(enderPearl);
    }

    public void deregisterEnderPearl(ThrownEnderpearl enderPearl) {
        this.enderPearls.remove(enderPearl);
    }

    public Set<ThrownEnderpearl> getEnderPearls() {
        return this.enderPearls;
    }

    public CompoundTag getShoulderEntityLeft() {
        return this.shoulderEntityLeft;
    }

    public void setShoulderEntityLeft(CompoundTag tag) {
        this.shoulderEntityLeft = tag;
        this.setShoulderParrotLeft(extractParrotVariant(tag));
    }

    public CompoundTag getShoulderEntityRight() {
        return this.shoulderEntityRight;
    }

    public void setShoulderEntityRight(CompoundTag tag) {
        this.shoulderEntityRight = tag;
        this.setShoulderParrotRight(extractParrotVariant(tag));
    }

    public long registerAndUpdateEnderPearlTicket(ThrownEnderpearl enderpearl) {
        Level level = enderpearl.level();

        if (level instanceof ServerLevel serverlevel) {
            ChunkPos chunkpos = enderpearl.chunkPosition();

            this.registerEnderPearl(enderpearl);
            serverlevel.resetEmptyTime();
            return placeEnderPearlTicket(serverlevel, chunkpos) - 1L;
        } else {
            return 0L;
        }
    }

    public static long placeEnderPearlTicket(ServerLevel level, ChunkPos chunk) {
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunk, 2);
        return TicketType.ENDER_PEARL.timeout();
    }

    public void requestDebugSubscriptions(Set<DebugSubscription<?>> subscriptions) {
        this.requestedDebugSubscriptions = Set.copyOf(subscriptions);
    }

    public Set<DebugSubscription<?>> debugSubscriptions() {
        return !this.server.debugSubscribers().hasRequiredPermissions(this) ? Set.of() : this.requestedDebugSubscriptions;
    }

    public static record RespawnPosAngle(Vec3 position, float yaw, float pitch) {

        public static ServerPlayer.RespawnPosAngle of(Vec3 position, BlockPos lookAtBlockPos, float pitch) {
            return new ServerPlayer.RespawnPosAngle(position, calculateLookAtYaw(position, lookAtBlockPos), pitch);
        }

        private static float calculateLookAtYaw(Vec3 position, BlockPos lookAtBlockPos) {
            Vec3 vec31 = Vec3.atBottomCenterOf(lookAtBlockPos).subtract(position).normalize();

            return (float) Mth.wrapDegrees(Mth.atan2(vec31.z, vec31.x) * (double) (180F / (float) Math.PI) - 90.0D);
        }
    }

    public static record RespawnConfig(LevelData.RespawnData respawnData, boolean forced) {

        public static final Codec<ServerPlayer.RespawnConfig> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(LevelData.RespawnData.MAP_CODEC.forGetter(ServerPlayer.RespawnConfig::respawnData), Codec.BOOL.optionalFieldOf("forced", false).forGetter(ServerPlayer.RespawnConfig::forced)).apply(instance, ServerPlayer.RespawnConfig::new);
        });

        private static ResourceKey<Level> getDimensionOrDefault(ServerPlayer.@Nullable RespawnConfig respawnConfig) {
            return respawnConfig != null ? respawnConfig.respawnData().dimension() : Level.OVERWORLD;
        }

        public boolean isSamePosition(ServerPlayer.@Nullable RespawnConfig other) {
            return other != null && this.respawnData.globalPos().equals(other.respawnData.globalPos());
        }
    }

    public static record SavedPosition(Optional<ResourceKey<Level>> dimension, Optional<Vec3> position, Optional<Vec2> rotation) {

        public static final MapCodec<ServerPlayer.SavedPosition> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Level.RESOURCE_KEY_CODEC.optionalFieldOf("Dimension").forGetter(ServerPlayer.SavedPosition::dimension), Vec3.CODEC.optionalFieldOf("Pos").forGetter(ServerPlayer.SavedPosition::position), Vec2.CODEC.optionalFieldOf("Rotation").forGetter(ServerPlayer.SavedPosition::rotation)).apply(instance, ServerPlayer.SavedPosition::new);
        });
        public static final ServerPlayer.SavedPosition EMPTY = new ServerPlayer.SavedPosition(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
