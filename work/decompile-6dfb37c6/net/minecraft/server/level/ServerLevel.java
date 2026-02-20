package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.LevelDebugSynchronizers;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerLevel extends Level implements WorldGenLevel, ServerEntityGetter {

    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    private final List<ServerPlayer> players = Lists.newArrayList();
    private final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    public final ServerLevelData serverLevelData;
    private final EntityTickList entityTickList = new EntityTickList();
    private final ServerWaypointManager waypointManager;
    private final EnvironmentAttributeSystem environmentAttributes;
    public final PersistentEntitySectionManager<Entity> entityManager;
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    private final LevelTicks<Block> blockTicks = new LevelTicks<Block>(this::isPositionTickingWithEntitiesLoaded);
    private final LevelTicks<Fluid> fluidTicks = new LevelTicks<Fluid>(this::isPositionTickingWithEntitiesLoaded);
    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    private final Set<Mob> navigatingMobs = new ObjectOpenHashSet();
    private volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet();
    private final List<BlockEventData> blockEventsToReschedule = new ArrayList(64);
    private boolean handlingTick;
    private final List<CustomSpawner> customSpawners;
    private @Nullable EndDragonFight dragonFight;
    private final Int2ObjectMap<EnderDragonPart> dragonParts = new Int2ObjectOpenHashMap();
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    private final boolean tickTime;
    private final RandomSequences randomSequences;
    private final LevelDebugSynchronizers debugSynchronizers = new LevelDebugSynchronizers(this);

    public ServerLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess levelStorage, ServerLevelData levelData, ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner> customSpawners, boolean tickTime, @Nullable RandomSequences randomSequences) {
        super(levelData, dimension, server.registryAccess(), levelStem.type(), false, isDebug, biomeZoomSeed, server.getMaxChainedNeighborUpdates());
        this.tickTime = tickTime;
        this.server = server;
        this.customSpawners = customSpawners;
        this.serverLevelData = levelData;
        ChunkGenerator chunkgenerator = levelStem.generator();
        boolean flag2 = server.forceSynchronousWrites();
        DataFixer datafixer = server.getFixerUpper();
        EntityPersistentStorage<Entity> entitypersistentstorage = new EntityStorage(new SimpleRegionStorage(new RegionStorageInfo(levelStorage.getLevelId(), dimension, "entities"), levelStorage.getDimensionPath(dimension).resolve("entities"), datafixer, flag2, DataFixTypes.ENTITY_CHUNK), this, server);

        this.entityManager = new PersistentEntitySectionManager<Entity>(Entity.class, new ServerLevel.EntityCallbacks(), entitypersistentstorage);
        StructureTemplateManager structuretemplatemanager = server.getStructureManager();
        int j = server.getPlayerList().getViewDistance();
        int k = server.getPlayerList().getSimulationDistance();
        PersistentEntitySectionManager persistententitysectionmanager = this.entityManager;

        Objects.requireNonNull(this.entityManager);
        this.chunkSource = new ServerChunkCache(this, levelStorage, datafixer, structuretemplatemanager, executor, chunkgenerator, j, k, flag2, persistententitysectionmanager::updateChunkStatus, () -> {
            return server.overworld().getDataStorage();
        });
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        if (this.canHaveWeather()) {
            this.prepareWeather();
        }

        this.raids = (Raids) this.getDataStorage().computeIfAbsent(Raids.getType(this.dimensionTypeRegistration()));
        if (!server.isSingleplayer()) {
            levelData.setGameType(server.getDefaultGameType());
        }

        long l = server.getWorldData().worldGenOptions().seed();

        this.structureCheck = new StructureCheck(this.chunkSource.chunkScanner(), this.registryAccess(), server.getStructureManager(), dimension, chunkgenerator, this.chunkSource.randomState(), this, chunkgenerator.getBiomeSource(), l, datafixer);
        this.structureManager = new StructureManager(this, server.getWorldData().worldGenOptions(), this.structureCheck);
        if (this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) {
            this.dragonFight = new EndDragonFight(this, l, server.getWorldData().endDragonFightData());
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = (RandomSequences) Objects.requireNonNullElseGet(randomSequences, () -> {
            return (RandomSequences) this.getDataStorage().computeIfAbsent(RandomSequences.TYPE);
        });
        this.waypointManager = new ServerWaypointManager();
        this.environmentAttributes = EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
        this.updateSkyBrightness();
    }

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EndDragonFight fight) {
        this.dragonFight = fight;
    }

    public void setWeatherParameters(int clearTime, int rainTime, boolean raining, boolean thundering) {
        this.serverLevelData.setClearWeatherTime(clearTime);
        this.serverLevelData.setRainTime(rainTime);
        this.serverLevelData.setThunderTime(rainTime);
        this.serverLevelData.setRaining(raining);
        this.serverLevelData.setThundering(thundering);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(quartX, quartY, quartZ, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return this.environmentAttributes;
    }

    public void tick(BooleanSupplier haveTime) {
        ProfilerFiller profilerfiller = Profiler.get();

        this.handlingTick = true;
        TickRateManager tickratemanager = this.tickRateManager();
        boolean flag = tickratemanager.runsNormally();

        if (flag) {
            profilerfiller.push("world border");
            this.getWorldBorder().tick();
            profilerfiller.popPush("weather");
            this.advanceWeatherCycle();
            profilerfiller.pop();
        }

        int i = (Integer) this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);

        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            if ((Boolean) this.getGameRules().get(GameRules.ADVANCE_TIME)) {
                long j = this.levelData.getDayTime() + 24000L;

                this.setDayTime(j - j % 24000L);
            }

            this.wakeUpAllPlayers();
            if ((Boolean) this.getGameRules().get(GameRules.ADVANCE_WEATHER) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }

        this.updateSkyBrightness();
        if (flag) {
            this.tickTime();
        }

        profilerfiller.push("tickPending");
        if (!this.isDebug() && flag) {
            long k = this.getGameTime();

            profilerfiller.push("blockTicks");
            this.blockTicks.tick(k, 65536, this::tickBlock);
            profilerfiller.popPush("fluidTicks");
            this.fluidTicks.tick(k, 65536, this::tickFluid);
            profilerfiller.pop();
        }

        profilerfiller.popPush("raid");
        if (flag) {
            this.raids.tick(this);
        }

        profilerfiller.popPush("chunkSource");
        this.getChunkSource().tick(haveTime, true);
        profilerfiller.popPush("blockEvents");
        if (flag) {
            this.runBlockEvents();
        }

        this.handlingTick = false;
        profilerfiller.pop();
        boolean flag1 = this.chunkSource.hasActiveTickets();

        if (flag1) {
            this.resetEmptyTime();
        }

        if (flag) {
            ++this.emptyTime;
        }

        if (this.emptyTime < 300) {
            profilerfiller.push("entities");
            if (this.dragonFight != null && flag) {
                profilerfiller.push("dragonFight");
                this.dragonFight.tick();
                profilerfiller.pop();
            }

            this.entityTickList.forEach((entity) -> {
                if (!entity.isRemoved()) {
                    if (!tickratemanager.isEntityFrozen(entity)) {
                        profilerfiller.push("checkDespawn");
                        entity.checkDespawn();
                        profilerfiller.pop();
                        if (entity instanceof ServerPlayer || this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) {
                            Entity entity1 = entity.getVehicle();

                            if (entity1 != null) {
                                if (!entity1.isRemoved() && entity1.hasPassenger(entity)) {
                                    return;
                                }

                                entity.stopRiding();
                            }

                            profilerfiller.push("tick");
                            this.guardEntityTick(this::tickNonPassenger, entity);
                            profilerfiller.pop();
                        }
                    }
                }
            });
            profilerfiller.popPush("blockEntities");
            this.tickBlockEntities();
            profilerfiller.pop();
        }

        profilerfiller.push("entityManagement");
        this.entityManager.tick();
        profilerfiller.pop();
        profilerfiller.push("debugSynchronizers");
        if (this.debugSynchronizers.hasAnySubscriberFor(DebugSubscriptions.NEIGHBOR_UPDATES)) {
            this.neighborUpdater.setDebugListener((blockpos) -> {
                this.debugSynchronizers.broadcastEventToTracking(blockpos, DebugSubscriptions.NEIGHBOR_UPDATES, blockpos);
            });
        } else {
            this.neighborUpdater.setDebugListener((Consumer) null);
        }

        this.debugSynchronizers.tick(this.server.debugSubscribers());
        profilerfiller.pop();
        this.environmentAttributes().invalidateTickCache();
    }

    @Override
    public boolean shouldTickBlocksAt(long chunkPos) {
        return this.chunkSource.chunkMap.getDistanceManager().inBlockTickingRange(chunkPos);
    }

    protected void tickTime() {
        if (this.tickTime) {
            long i = this.levelData.getGameTime() + 1L;

            this.serverLevelData.setGameTime(i);
            Profiler.get().push("scheduledFunctions");
            this.serverLevelData.getScheduledEvents().tick(this.server, i);
            Profiler.get().pop();
            if ((Boolean) this.getGameRules().get(GameRules.ADVANCE_TIME)) {
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }

        }
    }

    public void setDayTime(long newTime) {
        this.serverLevelData.setDayTime(newTime);
    }

    public long getDayCount() {
        return this.getDayTime() / 24000L;
    }

    public void tickCustomSpawners(boolean spawnEnemies) {
        for (CustomSpawner customspawner : this.customSpawners) {
            customspawner.tick(this, spawnEnemies);
        }

    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        ((List) this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList())).forEach((serverplayer) -> {
            serverplayer.stopSleepInBed(false, false);
        });
    }

    public void tickChunk(LevelChunk chunk, int tickSpeed) {
        ChunkPos chunkpos = chunk.getPos();
        int j = chunkpos.getMinBlockX();
        int k = chunkpos.getMinBlockZ();
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("iceandsnow");

        for (int l = 0; l < tickSpeed; ++l) {
            if (this.random.nextInt(48) == 0) {
                this.tickPrecipitation(this.getBlockRandomPos(j, 0, k, 15));
            }
        }

        profilerfiller.popPush("tickBlocks");
        if (tickSpeed > 0) {
            LevelChunkSection[] alevelchunksection = chunk.getSections();

            for (int i1 = 0; i1 < alevelchunksection.length; ++i1) {
                LevelChunkSection levelchunksection = alevelchunksection[i1];

                if (levelchunksection.isRandomlyTicking()) {
                    int j1 = chunk.getSectionYFromSectionIndex(i1);
                    int k1 = SectionPos.sectionToBlockCoord(j1);

                    for (int l1 = 0; l1 < tickSpeed; ++l1) {
                        BlockPos blockpos = this.getBlockRandomPos(j, k1, k, 15);

                        profilerfiller.push("randomTick");
                        BlockState blockstate = levelchunksection.getBlockState(blockpos.getX() - j, blockpos.getY() - k1, blockpos.getZ() - k);

                        if (blockstate.isRandomlyTicking()) {
                            blockstate.randomTick(this, blockpos, this.random);
                        }

                        FluidState fluidstate = blockstate.getFluidState();

                        if (fluidstate.isRandomlyTicking()) {
                            fluidstate.randomTick(this, blockpos, this.random);
                        }

                        profilerfiller.pop();
                    }
                }
            }
        }

        profilerfiller.pop();
    }

    public void tickThunder(LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        boolean flag = this.isRaining();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("thunder");
        if (flag && this.isThundering() && this.random.nextInt(100000) == 0) {
            BlockPos blockpos = this.findLightningTargetAround(this.getBlockRandomPos(i, 0, j, 15));

            if (this.isRainingAt(blockpos)) {
                DifficultyInstance difficultyinstance = this.getCurrentDifficultyAt(blockpos);
                boolean flag1 = (Boolean) this.getGameRules().get(GameRules.SPAWN_MOBS) && this.random.nextDouble() < (double) difficultyinstance.getEffectiveDifficulty() * 0.01D && !this.getBlockState(blockpos.below()).is(BlockTags.LIGHTNING_RODS);

                if (flag1) {
                    SkeletonHorse skeletonhorse = EntityType.SKELETON_HORSE.create(this, EntitySpawnReason.EVENT);

                    if (skeletonhorse != null) {
                        skeletonhorse.setTrap(true);
                        skeletonhorse.setAge(0);
                        skeletonhorse.setPos((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());
                        this.addFreshEntity(skeletonhorse);
                    }
                }

                LightningBolt lightningbolt = EntityType.LIGHTNING_BOLT.create(this, EntitySpawnReason.EVENT);

                if (lightningbolt != null) {
                    lightningbolt.snapTo(Vec3.atBottomCenterOf(blockpos));
                    lightningbolt.setVisualOnly(flag1);
                    this.addFreshEntity(lightningbolt);
                }
            }
        }

        profilerfiller.pop();
    }

    @VisibleForTesting
    public void tickPrecipitation(BlockPos pos) {
        BlockPos blockpos1 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        BlockPos blockpos2 = blockpos1.below();
        Biome biome = (Biome) this.getBiome(blockpos1).value();

        if (biome.shouldFreeze(this, blockpos2)) {
            this.setBlockAndUpdate(blockpos2, Blocks.ICE.defaultBlockState());
        }

        if (this.isRaining()) {
            int i = (Integer) this.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);

            if (i > 0 && biome.shouldSnow(this, blockpos1)) {
                BlockState blockstate = this.getBlockState(blockpos1);

                if (blockstate.is(Blocks.SNOW)) {
                    int j = (Integer) blockstate.getValue(SnowLayerBlock.LAYERS);

                    if (j < Math.min(i, 8)) {
                        BlockState blockstate1 = (BlockState) blockstate.setValue(SnowLayerBlock.LAYERS, j + 1);

                        Block.pushEntitiesUp(blockstate, blockstate1, this, blockpos1);
                        this.setBlockAndUpdate(blockpos1, blockstate1);
                    }
                } else {
                    this.setBlockAndUpdate(blockpos1, Blocks.SNOW.defaultBlockState());
                }
            }

            Biome.Precipitation biome_precipitation = biome.getPrecipitationAt(blockpos2, this.getSeaLevel());

            if (biome_precipitation != Biome.Precipitation.NONE) {
                BlockState blockstate2 = this.getBlockState(blockpos2);

                blockstate2.getBlock().handlePrecipitation(blockstate2, this, blockpos2, biome_precipitation);
            }
        }

    }

    private Optional<BlockPos> findLightningRod(BlockPos center) {
        Optional<BlockPos> optional = this.getPoiManager().findClosest((holder) -> {
            return holder.is(PoiTypes.LIGHTNING_ROD);
        }, (blockpos1) -> {
            return blockpos1.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockpos1.getX(), blockpos1.getZ()) - 1;
        }, center, 128, PoiManager.Occupancy.ANY);

        return optional.map((blockpos1) -> {
            return blockpos1.above(1);
        });
    }

    protected BlockPos findLightningTargetAround(BlockPos pos) {
        BlockPos blockpos1 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        Optional<BlockPos> optional = this.findLightningRod(blockpos1);

        if (optional.isPresent()) {
            return (BlockPos) optional.get();
        } else {
            AABB aabb = AABB.encapsulatingFullBlocks(blockpos1, blockpos1.atY(this.getMaxY() + 1)).inflate(3.0D);
            List<LivingEntity> list = this.<LivingEntity>getEntitiesOfClass(LivingEntity.class, aabb, (livingentity) -> {
                return livingentity.isAlive() && this.canSeeSky(livingentity.blockPosition());
            });

            if (!list.isEmpty()) {
                return ((LivingEntity) list.get(this.random.nextInt(list.size()))).blockPosition();
            } else {
                if (blockpos1.getY() == this.getMinY() - 1) {
                    blockpos1 = blockpos1.above(2);
                }

                return blockpos1;
            }
        }
    }

    public boolean isHandlingTick() {
        return this.handlingTick;
    }

    public boolean canSleepThroughNights() {
        return (Integer) this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = (Integer) this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
                Component component;

                if (this.sleepStatus.areEnoughSleeping(i)) {
                    component = Component.translatable("sleep.skipping_night");
                } else {
                    component = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                for (ServerPlayer serverplayer : this.players) {
                    serverplayer.displayClientMessage(component, true);
                }

            }
        }
    }

    public void updateSleepingPlayerList() {
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }

    }

    @Override
    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    public ServerWaypointManager getWaypointManager() {
        return this.waypointManager;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        long i = 0L;
        float f = 0.0F;
        ChunkAccess chunkaccess = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);

        if (chunkaccess != null) {
            i = chunkaccess.getInhabitedTime();
            f = this.getMoonBrightness(pos);
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), i, f);
    }

    public float getMoonBrightness(BlockPos pos) {
        MoonPhase moonphase = (MoonPhase) this.environmentAttributes.getValue(EnvironmentAttributes.MOON_PHASE, pos);

        return DimensionType.MOON_BRIGHTNESS_PER_PHASE[moonphase.index()];
    }

    private void advanceWeatherCycle() {
        boolean flag = this.isRaining();

        if (this.canHaveWeather()) {
            if ((Boolean) this.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
                int i = this.serverLevelData.getClearWeatherTime();
                int j = this.serverLevelData.getThunderTime();
                int k = this.serverLevelData.getRainTime();
                boolean flag1 = this.levelData.isThundering();
                boolean flag2 = this.levelData.isRaining();

                if (i > 0) {
                    --i;
                    j = flag1 ? 0 : 1;
                    k = flag2 ? 0 : 1;
                    flag1 = false;
                    flag2 = false;
                } else {
                    if (j > 0) {
                        --j;
                        if (j == 0) {
                            flag1 = !flag1;
                        }
                    } else if (flag1) {
                        j = ServerLevel.THUNDER_DURATION.sample(this.random);
                    } else {
                        j = ServerLevel.THUNDER_DELAY.sample(this.random);
                    }

                    if (k > 0) {
                        --k;
                        if (k == 0) {
                            flag2 = !flag2;
                        }
                    } else if (flag2) {
                        k = ServerLevel.RAIN_DURATION.sample(this.random);
                    } else {
                        k = ServerLevel.RAIN_DELAY.sample(this.random);
                    }
                }

                this.serverLevelData.setThunderTime(j);
                this.serverLevelData.setRainTime(k);
                this.serverLevelData.setClearWeatherTime(i);
                this.serverLevelData.setThundering(flag1);
                this.serverLevelData.setRaining(flag2);
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        if (this.oRainLevel != this.rainLevel) {
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }

    }

    @VisibleForTesting
    public void resetWeatherCycle() {
        this.serverLevelData.setRainTime(0);
        this.serverLevelData.setRaining(false);
        this.serverLevelData.setThunderTime(0);
        this.serverLevelData.setThundering(false);
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos pos, Fluid type) {
        BlockState blockstate = this.getBlockState(pos);
        FluidState fluidstate = blockstate.getFluidState();

        if (fluidstate.is(type)) {
            fluidstate.tick(this, pos, blockstate);
        }

    }

    private void tickBlock(BlockPos pos, Block type) {
        BlockState blockstate = this.getBlockState(pos);

        if (blockstate.is(type)) {
            blockstate.tick(this, pos, this.random);
        }

    }

    public void tickNonPassenger(Entity entity) {
        entity.setOldPosAndRot();
        ProfilerFiller profilerfiller = Profiler.get();

        ++entity.tickCount;
        profilerfiller.push(() -> {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        });
        profilerfiller.incrementCounter("tickNonPassenger");
        entity.tick();
        profilerfiller.pop();

        for (Entity entity1 : entity.getPassengers()) {
            this.tickPassenger(entity, entity1);
        }

    }

    private void tickPassenger(Entity vehicle, Entity entity) {
        if (!entity.isRemoved() && entity.getVehicle() == vehicle) {
            if (entity instanceof Player || this.entityTickList.contains(entity)) {
                entity.setOldPosAndRot();
                ++entity.tickCount;
                ProfilerFiller profilerfiller = Profiler.get();

                profilerfiller.push(() -> {
                    return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                });
                profilerfiller.incrementCounter("tickPassenger");
                entity.rideTick();
                profilerfiller.pop();

                for (Entity entity2 : entity.getPassengers()) {
                    this.tickPassenger(entity, entity2);
                }

            }
        } else {
            entity.stopRiding();
        }
    }

    public void updateNeighboursOnBlockSet(BlockPos pos, BlockState oldState) {
        BlockState blockstate1 = this.getBlockState(pos);
        Block block = blockstate1.getBlock();
        boolean flag = !oldState.is(block);

        if (flag) {
            oldState.affectNeighborsAfterRemoval(this, pos, false);
        }

        this.updateNeighborsAt(pos, blockstate1.getBlock());
        if (blockstate1.hasAnalogOutputSignal()) {
            this.updateNeighbourForOutputSignal(pos, block);
        }

    }

    @Override
    public boolean mayInteract(Entity entity, BlockPos pos) {
        boolean flag;

        if (entity instanceof Player player) {
            if (this.server.isUnderSpawnProtection(this, pos, player) || !this.getWorldBorder().isWithinBounds(pos)) {
                flag = false;
                return flag;
            }
        }

        flag = true;
        return flag;
    }

    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean noSave) {
        ServerChunkCache serverchunkcache = this.getChunkSource();

        if (!noSave) {
            if (progressListener != null) {
                progressListener.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData(flush);
            if (progressListener != null) {
                progressListener.progressStage(Component.translatable("menu.savingChunks"));
            }

            serverchunkcache.save(flush);
            if (flush) {
                this.entityManager.saveAll();
            } else {
                this.entityManager.autoSave();
            }

        }
    }

    private void saveLevelData(boolean sync) {
        if (this.dragonFight != null) {
            this.server.getWorldData().setEndDragonFightData(this.dragonFight.saveData());
        }

        DimensionDataStorage dimensiondatastorage = this.getChunkSource().getDataStorage();

        if (sync) {
            dimensiondatastorage.saveAndJoin();
        } else {
            dimensiondatastorage.scheduleSave();
        }

    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> type, Predicate<? super T> selector) {
        List<T> list = Lists.newArrayList();

        this.getEntities(type, selector, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, Predicate<? super T> selector, List<? super T> result) {
        this.getEntities(type, selector, result, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, Predicate<? super T> selector, List<? super T> result, int maxResults) {
        this.getEntities().get(type, (entity) -> {
            if (selector.test(entity)) {
                result.add(entity);
                if (result.size() >= maxResults) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities(EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> selector) {
        return this.getPlayers(selector, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> selector, int maxResults) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverplayer : this.players) {
            if (selector.test(serverplayer)) {
                list.add(serverplayer);
                if (list.size() >= maxResults) {
                    return list;
                }
            }
        }

        return list;
    }

    public @Nullable ServerPlayer getRandomPlayer() {
        List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return this.addEntity(entity);
    }

    public boolean addWithUUID(Entity entity) {
        return this.addEntity(entity);
    }

    public void addDuringTeleport(Entity entity) {
        if (entity instanceof ServerPlayer serverplayer) {
            this.addPlayer(serverplayer);
        } else {
            this.addEntity(entity);
        }

    }

    public void addNewPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addRespawnedPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    private void addPlayer(ServerPlayer player) {
        Entity entity = this.getEntity(player.getUUID());

        if (entity != null) {
            ServerLevel.LOGGER.warn("Force-added player with duplicate UUID {}", player.getUUID());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer) entity, Entity.RemovalReason.DISCARDED);
        }

        this.entityManager.addNewEntity(player);
    }

    private boolean addEntity(Entity entity) {
        if (entity.isRemoved()) {
            ServerLevel.LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityType.getKey(entity.getType()));
            return false;
        } else {
            return this.entityManager.addNewEntity(entity);
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        Stream stream = entity.getSelfAndPassengers().map(Entity::getUUID);
        PersistentEntitySectionManager persistententitysectionmanager = this.entityManager;

        Objects.requireNonNull(this.entityManager);
        if (stream.anyMatch(persistententitysectionmanager::isLoaded)) {
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity);
            return true;
        }
    }

    public void unload(LevelChunk levelChunk) {
        levelChunk.clearAllBlockEntities();
        levelChunk.unregisterTickContainerFromLevel(this);
        this.debugSynchronizers.dropChunk(levelChunk.getPos());
    }

    public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        player.remove(reason);
    }

    @Override
    public void destroyBlockProgress(int id, BlockPos blockPos, int progress) {
        for (ServerPlayer serverplayer : this.server.getPlayerList().getPlayers()) {
            if (serverplayer.level() == this && serverplayer.getId() != id) {
                double d0 = (double) blockPos.getX() - serverplayer.getX();
                double d1 = (double) blockPos.getY() - serverplayer.getY();
                double d2 = (double) blockPos.getZ() - serverplayer.getZ();

                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0D) {
                    serverplayer.connection.send(new ClientboundBlockDestructionPacket(id, blockPos, progress));
                }
            }
        }

    }

    @Override
    public void playSeededSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
        PlayerList playerlist = this.server.getPlayerList();
        Player player;

        if (except instanceof Player player1) {
            player = player1;
        } else {
            player = null;
        }

        playerlist.broadcast(player, x, y, z, (double) ((SoundEvent) sound.value()).getRange(volume), this.dimension(), new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed));
    }

    @Override
    public void playSeededSound(@Nullable Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
        PlayerList playerlist = this.server.getPlayerList();
        Player player;

        if (except instanceof Player player1) {
            player = player1;
        } else {
            player = null;
        }

        playerlist.broadcast(player, sourceEntity.getX(), sourceEntity.getY(), sourceEntity.getZ(), (double) ((SoundEvent) sound.value()).getRange(volume), this.dimension(), new ClientboundSoundEntityPacket(sound, source, sourceEntity, volume, pitch, seed));
    }

    @Override
    public void globalLevelEvent(int type, BlockPos pos, int data) {
        if ((Boolean) this.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().getPlayers().forEach((serverplayer) -> {
                Vec3 vec3;

                if (serverplayer.level() == this) {
                    Vec3 vec31 = Vec3.atCenterOf(pos);

                    if (serverplayer.distanceToSqr(vec31) < (double) Mth.square(32)) {
                        vec3 = vec31;
                    } else {
                        Vec3 vec32 = vec31.subtract(serverplayer.position()).normalize();

                        vec3 = serverplayer.position().add(vec32.scale(32.0D));
                    }
                } else {
                    vec3 = serverplayer.position();
                }

                serverplayer.connection.send(new ClientboundLevelEventPacket(type, BlockPos.containing(vec3), data, true));
            });
        } else {
            this.levelEvent((Entity) null, type, pos, data);
        }

    }

    @Override
    public void levelEvent(@Nullable Entity source, int type, BlockPos pos, int data) {
        PlayerList playerlist = this.server.getPlayerList();
        Player player;

        if (source instanceof Player player1) {
            player = player1;
        } else {
            player = null;
        }

        playerlist.broadcast(player, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), 64.0D, this.dimension(), new ClientboundLevelEventPacket(type, pos, data, false));
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {
        this.gameEventDispatcher.post(gameEvent, position, context);
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState old, BlockState current, int updateFlags) {
        if (this.isUpdatingNavigations) {
            String s = "recursive call to sendBlockUpdated";

            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        this.pathTypesByPosCache.invalidate(pos);
        VoxelShape voxelshape = old.getCollisionShape(this, pos);
        VoxelShape voxelshape1 = current.getCollisionShape(this, pos);

        if (Shapes.joinIsNotEmpty(voxelshape, voxelshape1, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList();

            for (Mob mob : this.navigatingMobs) {
                PathNavigation pathnavigation = mob.getNavigation();

                if (pathnavigation.shouldRecomputePath(pos)) {
                    list.add(pathnavigation);
                }
            }

            try {
                this.isUpdatingNavigations = true;

                for (PathNavigation pathnavigation1 : list) {
                    pathnavigation1.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }

        }
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block sourceBlock) {
        this.updateNeighborsAt(pos, sourceBlock, ExperimentalRedstoneUtils.initialOrientation(this, (Direction) null, (Direction) null));
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, (Direction) null, orientation);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block blockObject, Direction skipDirection, @Nullable Orientation orientation) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, blockObject, skipDirection, orientation);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block changedBlock, @Nullable Orientation orientation) {
        this.neighborUpdater.neighborChanged(pos, changedBlock, orientation);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block changedBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        this.neighborUpdater.neighborChanged(state, pos, changedBlock, orientation, movedByPiston);
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte event) {
        this.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundEntityEventPacket(entity, event));
    }

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource source) {
        this.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundDamageEventPacket(entity, source));
    }

    @Override
    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float r, boolean fire, Level.ExplosionInteraction interactionType, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> explosionSound) {
        Explosion.BlockInteraction explosion_blockinteraction;

        switch (interactionType) {
            case NONE:
                explosion_blockinteraction = Explosion.BlockInteraction.KEEP;
                break;
            case BLOCK:
                explosion_blockinteraction = this.getDestroyType(GameRules.BLOCK_EXPLOSION_DROP_DECAY);
                break;
            case MOB:
                explosion_blockinteraction = (Boolean) this.getGameRules().get(GameRules.MOB_GRIEFING) ? this.getDestroyType(GameRules.MOB_EXPLOSION_DROP_DECAY) : Explosion.BlockInteraction.KEEP;
                break;
            case TNT:
                explosion_blockinteraction = this.getDestroyType(GameRules.TNT_EXPLOSION_DROP_DECAY);
                break;
            case TRIGGER:
                explosion_blockinteraction = Explosion.BlockInteraction.TRIGGER_BLOCK;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Explosion.BlockInteraction explosion_blockinteraction1 = explosion_blockinteraction;
        Vec3 vec3 = new Vec3(x, y, z);
        ServerExplosion serverexplosion = new ServerExplosion(this, source, damageSource, damageCalculator, vec3, r, fire, explosion_blockinteraction1);
        int i = serverexplosion.explode();
        ParticleOptions particleoptions2 = serverexplosion.isSmall() ? smallExplosionParticles : largeExplosionParticles;

        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.distanceToSqr(vec3) < 4096.0D) {
                Optional<Vec3> optional = Optional.ofNullable((Vec3) serverexplosion.getHitPlayers().get(serverplayer));

                serverplayer.connection.send(new ClientboundExplodePacket(vec3, r, i, optional, particleoptions2, explosionSound, blockParticles));
            }
        }

    }

    private Explosion.BlockInteraction getDestroyType(GameRule<Boolean> gameRule) {
        return (Boolean) this.getGameRules().get(gameRule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    @Override
    public void blockEvent(BlockPos pos, Block block, int b0, int b1) {
        this.blockEvents.add(new BlockEventData(pos, block, b0, b1));
    }

    private void runBlockEvents() {
        this.blockEventsToReschedule.clear();

        while (!this.blockEvents.isEmpty()) {
            BlockEventData blockeventdata = (BlockEventData) this.blockEvents.removeFirst();

            if (this.shouldTickBlocksAt(blockeventdata.pos())) {
                if (this.doBlockEvent(blockeventdata)) {
                    this.server.getPlayerList().broadcast((Player) null, (double) blockeventdata.pos().getX(), (double) blockeventdata.pos().getY(), (double) blockeventdata.pos().getZ(), 64.0D, this.dimension(), new ClientboundBlockEventPacket(blockeventdata.pos(), blockeventdata.block(), blockeventdata.paramA(), blockeventdata.paramB()));
                }
            } else {
                this.blockEventsToReschedule.add(blockeventdata);
            }
        }

        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

    private boolean doBlockEvent(BlockEventData eventData) {
        BlockState blockstate = this.getBlockState(eventData.pos());

        return blockstate.is(eventData.block()) ? blockstate.triggerEvent(this, eventData.pos(), eventData.paramA(), eventData.paramB()) : false;
    }

    @Override
    public LevelTicks<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTicks<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed) {
        return this.sendParticles(particle, false, false, x, y, z, count, xDist, yDist, zDist, speed);
    }

    public <T extends ParticleOptions> int sendParticles(T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed) {
        ClientboundLevelParticlesPacket clientboundlevelparticlespacket = new ClientboundLevelParticlesPacket(particle, overrideLimiter, alwaysShow, x, y, z, (float) xDist, (float) yDist, (float) zDist, (float) speed, count);
        int j = 0;

        for (int k = 0; k < this.players.size(); ++k) {
            ServerPlayer serverplayer = (ServerPlayer) this.players.get(k);

            if (this.sendParticles(serverplayer, overrideLimiter, x, y, z, clientboundlevelparticlespacket)) {
                ++j;
            }
        }

        return j;
    }

    public <T extends ParticleOptions> boolean sendParticles(ServerPlayer player, T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(particle, overrideLimiter, alwaysShow, x, y, z, (float) xDist, (float) yDist, (float) zDist, (float) speed, count);

        return this.sendParticles(player, overrideLimiter, x, y, z, packet);
    }

    private boolean sendParticles(ServerPlayer player, boolean overrideLimiter, double x, double y, double z, Packet<?> packet) {
        if (player.level() != this) {
            return false;
        } else {
            BlockPos blockpos = player.blockPosition();

            if (blockpos.closerToCenterThan(new Vec3(x, y, z), overrideLimiter ? 512.0D : 32.0D)) {
                player.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public @Nullable Entity getEntity(int id) {
        return (Entity) this.getEntities().get(id);
    }

    @Override
    public @Nullable Entity getEntityInAnyDimension(UUID uuid) {
        Entity entity = this.getEntity(uuid);

        if (entity != null) {
            return entity;
        } else {
            for (ServerLevel serverlevel : this.getServer().getAllLevels()) {
                if (serverlevel != this) {
                    Entity entity1 = serverlevel.getEntity(uuid);

                    if (entity1 != null) {
                        return entity1;
                    }
                }
            }

            return null;
        }
    }

    @Override
    public @Nullable Player getPlayerInAnyDimension(UUID uuid) {
        return this.getServer().getPlayerList().getPlayer(uuid);
    }

    /** @deprecated */
    @Deprecated
    public @Nullable Entity getEntityOrPart(int id) {
        Entity entity = (Entity) this.getEntities().get(id);

        return entity != null ? entity : (Entity) this.dragonParts.get(id);
    }

    @Override
    public Collection<EnderDragonPart> dragonParts() {
        return this.dragonParts.values();
    }

    public @Nullable BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos origin, int maxSearchRadius, boolean createReference) {
        if (!this.server.getWorldData().worldGenOptions().generateStructures()) {
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structureTag);

            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource().getGenerator().findNearestMapStructure(this, (HolderSet) optional.get(), origin, maxSearchRadius, createReference);

                return pair != null ? (BlockPos) pair.getFirst() : null;
            }
        }
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(Predicate<Holder<Biome>> biomeTest, BlockPos origin, int maxSearchRadius, int sampleResolutionHorizontal, int sampleResolutionVertical) {
        return this.getChunkSource().getGenerator().getBiomeSource().findClosestBiome3d(origin, maxSearchRadius, sampleResolutionHorizontal, sampleResolutionVertical, biomeTest, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public WorldBorder getWorldBorder() {
        WorldBorder worldborder = (WorldBorder) this.getDataStorage().computeIfAbsent(WorldBorder.TYPE);

        worldborder.applyInitialSettings(this.levelData.getGameTime());
        return worldborder;
    }

    @Override
    public RecipeManager recipeAccess() {
        return this.server.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.server.tickRateManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Override
    public @Nullable MapItemSavedData getMapData(MapId id) {
        return (MapItemSavedData) this.getServer().overworld().getDataStorage().get(MapItemSavedData.type(id));
    }

    public void setMapData(MapId id, MapItemSavedData data) {
        this.getServer().overworld().getDataStorage().set(MapItemSavedData.type(id), data);
    }

    public MapId getFreeMapId() {
        return ((MapIndex) this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.TYPE)).getNextMapId();
    }

    @Override
    public void setRespawnData(LevelData.RespawnData respawnData) {
        this.getServer().setRespawnData(respawnData);
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return this.getServer().getRespawnData();
    }

    public LongSet getForceLoadedChunks() {
        return this.chunkSource.getForceLoadedChunks();
    }

    public boolean setChunkForced(int chunkX, int chunkZ, boolean forced) {
        boolean flag1 = this.chunkSource.updateChunkForced(new ChunkPos(chunkX, chunkZ), forced);

        if (forced && flag1) {
            this.getChunk(chunkX, chunkZ);
        }

        return flag1;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void updatePOIOnBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(oldState);
        Optional<Holder<PoiType>> optional1 = PoiTypes.forState(newState);

        if (!Objects.equals(optional, optional1)) {
            BlockPos blockpos1 = pos.immutable();

            optional.ifPresent((holder) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().remove(blockpos1);
                    this.debugSynchronizers.dropPoi(blockpos1);
                });
            });
            optional1.ifPresent((holder) -> {
                this.getServer().execute(() -> {
                    PoiRecord poirecord = this.getPoiManager().add(blockpos1, holder);

                    if (poirecord != null) {
                        this.debugSynchronizers.registerPoi(poirecord);
                    }

                });
            });
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos pos) {
        return this.isCloseToVillage(pos, 1);
    }

    public boolean isVillage(SectionPos sectionPos) {
        return this.isVillage(sectionPos.center());
    }

    public boolean isCloseToVillage(BlockPos pos, int sectionDistance) {
        return sectionDistance > 6 ? false : this.sectionsToVillage(SectionPos.of(pos)) <= sectionDistance;
    }

    public int sectionsToVillage(SectionPos pos) {
        return this.getPoiManager().sectionsToVillage(pos);
    }

    public Raids getRaids() {
        return this.raids;
    }

    public @Nullable Raid getRaidAt(BlockPos pos) {
        return this.raids.getNearbyRaid(pos, 9216);
    }

    public boolean isRaided(BlockPos pos) {
        return this.getRaidAt(pos) != null;
    }

    public void onReputationEvent(ReputationEventType type, Entity source, ReputationEventHandler target) {
        target.onReputationEventFrom(type, source);
    }

    public void saveDebugReport(Path rootDir) throws IOException {
        ChunkMap chunkmap = this.getChunkSource().chunkMap;

        try (Writer writer = Files.newBufferedWriter(rootDir.resolve("stats.txt"))) {
            writer.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", chunkmap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState naturalspawner_spawnstate = this.getChunkSource().getLastSpawnState();

            if (naturalspawner_spawnstate != null) {
                ObjectIterator objectiterator = naturalspawner_spawnstate.getMobCategoryCounts().object2IntEntrySet().iterator();

                while (objectiterator.hasNext()) {
                    Object2IntMap.Entry<MobCategory> object2intmap_entry = (Entry) objectiterator.next();

                    writer.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", ((MobCategory) object2intmap_entry.getKey()).getName(), object2intmap_entry.getIntValue()));
                }
            }

            writer.write(String.format(Locale.ROOT, "entities: %s\n", this.entityManager.gatherStats()));
            writer.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
            writer.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            writer.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            writer.write("distance_manager: " + chunkmap.getDistanceManager().getDebugStatus() + "\n");
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        }

        CrashReport crashreport = new CrashReport("Level dump", new Exception("dummy"));

        this.fillReportDetails(crashreport);

        try (Writer writer1 = Files.newBufferedWriter(rootDir.resolve("example_crash.txt"))) {
            writer1.write(crashreport.getFriendlyReport(ReportType.TEST));
        }

        Path path1 = rootDir.resolve("chunks.csv");

        try (Writer writer2 = Files.newBufferedWriter(path1)) {
            chunkmap.dumpChunks(writer2);
        }

        Path path2 = rootDir.resolve("entity_chunks.csv");

        try (Writer writer3 = Files.newBufferedWriter(path2)) {
            this.entityManager.dumpSections(writer3);
        }

        Path path3 = rootDir.resolve("entities.csv");

        try (Writer writer4 = Files.newBufferedWriter(path3)) {
            dumpEntities(writer4, this.getEntities().getAll());
        }

        Path path4 = rootDir.resolve("block_entities.csv");

        try (Writer writer5 = Files.newBufferedWriter(path4)) {
            this.dumpBlockEntityTickers(writer5);
        }

    }

    private static void dumpEntities(Writer output, Iterable<Entity> entities) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("uuid").addColumn("type").addColumn("alive").addColumn("display_name").addColumn("custom_name").build(output);

        for (Entity entity : entities) {
            Component component = entity.getCustomName();
            Component component1 = entity.getDisplayName();

            csvoutput.writeRow(entity.getX(), entity.getY(), entity.getZ(), entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.isAlive(), component1.getString(), component != null ? component.getString() : null);
        }

    }

    private void dumpBlockEntityTickers(Writer output) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(output);

        for (TickingBlockEntity tickingblockentity : this.blockEntityTickers) {
            BlockPos blockpos = tickingblockentity.getPos();

            csvoutput.writeRow(blockpos.getX(), blockpos.getY(), blockpos.getZ(), tickingblockentity.getType());
        }

    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox bb) {
        this.blockEvents.removeIf((blockeventdata) -> {
            return bb.isInside(blockeventdata.pos());
        });
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.server.getWorldData().isFlatWorld();
    }

    @Override
    public long getSeed() {
        return this.server.getWorldData().worldGenOptions().seed();
    }

    public @Nullable EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(Locale.ROOT, "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s", this.players.size(), this.entityManager.gatherStats(), getTypeCount(this.entityManager.getEntityGetter().getAll(), (entity) -> {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }), this.blockEntityTickers.size(), getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType), this.getBlockTicks().count(), this.getFluidTicks().count(), this.gatherChunkSourceStats());
    }

    private static <T> String getTypeCount(Iterable<T> values, Function<T, String> typeGetter) {
        try {
            Object2IntOpenHashMap<String> object2intopenhashmap = new Object2IntOpenHashMap();

            for (T t0 : values) {
                String s = (String) typeGetter.apply(t0);

                object2intopenhashmap.addTo(s, 1);
            }

            return (String) object2intopenhashmap.object2IntEntrySet().stream().sorted(Comparator.comparing(Entry::getIntValue).reversed()).limit(5L).map((entry) -> {
                String s1 = (String) entry.getKey();

                return s1 + ":" + entry.getIntValue();
            }).collect(Collectors.joining(","));
        } catch (Exception exception) {
            return "";
        }
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        return this.entityManager.getEntityGetter();
    }

    public void addLegacyChunkEntities(Stream<Entity> loaded) {
        this.entityManager.addLegacyChunkEntities(loaded);
    }

    public void addWorldGenChunkEntities(Stream<Entity> loaded) {
        this.entityManager.addWorldGenChunkEntities(loaded);
    }

    public void startTickingChunk(LevelChunk levelChunk) {
        levelChunk.unpackTicks(this.getGameTime());
    }

    public void onStructureStartsAvailable(ChunkAccess chunk) {
        this.server.execute(() -> {
            this.structureCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        });
    }

    public PathTypeCache getPathTypeCache() {
        return this.pathTypesByPosCache;
    }

    public void waitForEntities(ChunkPos centerChunk, int radius) {
        List<ChunkPos> list = ChunkPos.rangeClosed(centerChunk, radius).toList();

        this.server.managedBlock(() -> {
            this.entityManager.processPendingLoads();

            for (ChunkPos chunkpos1 : list) {
                if (!this.areEntitiesLoaded(chunkpos1.toLong())) {
                    return false;
                }
            }

            return true;
        });
    }

    public boolean isSpawningMonsters() {
        return this.getLevelData().getDifficulty() != Difficulty.PEACEFUL && (Boolean) this.getGameRules().get(GameRules.SPAWN_MOBS) && (Boolean) this.getGameRules().get(GameRules.SPAWN_MONSTERS);
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.entityManager.close();
    }

    @Override
    public String gatherChunkSourceStats() {
        String s = this.chunkSource.gatherStats();

        return "Chunks[S] W: " + s + " E: " + this.entityManager.gatherStats();
    }

    public boolean areEntitiesLoaded(long chunkKey) {
        return this.entityManager.areEntitiesLoaded(chunkKey);
    }

    public boolean isPositionTickingWithEntitiesLoaded(long key) {
        return this.areEntitiesLoaded(key) && this.chunkSource.isPositionTicking(key);
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        return this.entityManager.canPositionTick(pos) && this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.asLong(pos));
    }

    public boolean areEntitiesActuallyLoadedAndTicking(ChunkPos pos) {
        return this.entityManager.isTicking(pos) && this.entityManager.areEntitiesLoaded(pos.toLong());
    }

    public boolean anyPlayerCloseEnoughForSpawning(BlockPos pos) {
        return this.anyPlayerCloseEnoughForSpawning(new ChunkPos(pos));
    }

    public boolean anyPlayerCloseEnoughForSpawning(ChunkPos pos) {
        return this.chunkSource.chunkMap.anyPlayerCloseEnoughForSpawning(pos);
    }

    public boolean canSpreadFireAround(BlockPos pos) {
        int i = (Integer) this.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);

        return i == -1 || this.chunkSource.chunkMap.anyPlayerCloseEnoughTo(pos, i);
    }

    public boolean canSpawnEntitiesInChunk(ChunkPos pos) {
        return this.entityManager.canPositionTick(pos) && this.getWorldBorder().isWithinBounds(pos);
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.server.potionBrewing();
    }

    @Override
    public FuelValues fuelValues() {
        return this.server.fuelValues();
    }

    public RandomSource getRandomSequence(Identifier key) {
        return this.randomSequences.get(key, this.getSeed());
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    public GameRules getGameRules() {
        return this.serverLevelData.getGameRules();
    }

    @Override
    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashreportcategory = super.fillReportDetails(report);

        crashreportcategory.setDetail("Loaded entity count", () -> {
            return String.valueOf(this.entityManager.count());
        });
        return crashreportcategory;
    }

    @Override
    public int getSeaLevel() {
        return this.chunkSource.getGenerator().getSeaLevel();
    }

    @Override
    public void onBlockEntityAdded(BlockEntity blockEntity) {
        super.onBlockEntityAdded(blockEntity);
        this.debugSynchronizers.registerBlockEntity(blockEntity);
    }

    public LevelDebugSynchronizers debugSynchronizers() {
        return this.debugSynchronizers;
    }

    public boolean isAllowedToEnterPortal(Level toLevel) {
        return toLevel.dimension() == Level.NETHER ? (Boolean) this.getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS) : true;
    }

    public boolean isPvpAllowed() {
        return (Boolean) this.getGameRules().get(GameRules.PVP);
    }

    public boolean isCommandBlockEnabled() {
        return (Boolean) this.getGameRules().get(GameRules.COMMAND_BLOCKS_WORK);
    }

    public boolean isSpawnerBlockEnabled() {
        return (Boolean) this.getGameRules().get(GameRules.SPAWNER_BLOCKS_WORK);
    }

    private final class EntityCallbacks implements LevelCallback<Entity> {

        private EntityCallbacks() {}

        public void onCreated(Entity entity) {
            if (entity instanceof WaypointTransmitter waypointtransmitter) {
                if (waypointtransmitter.isTransmittingWaypoint()) {
                    ServerLevel.this.getWaypointManager().trackWaypoint(waypointtransmitter);
                }
            }

        }

        public void onDestroyed(Entity entity) {
            if (entity instanceof WaypointTransmitter waypointtransmitter) {
                ServerLevel.this.getWaypointManager().untrackWaypoint(waypointtransmitter);
            }

            ServerLevel.this.getScoreboard().entityRemoved(entity);
        }

        public void onTickingStart(Entity entity) {
            ServerLevel.this.entityTickList.add(entity);
        }

        public void onTickingEnd(Entity entity) {
            ServerLevel.this.entityTickList.remove(entity);
        }

        public void onTrackingStart(Entity entity) {
            ServerLevel.this.getChunkSource().addEntity(entity);
            if (entity instanceof ServerPlayer serverplayer) {
                ServerLevel.this.players.add(serverplayer);
                if (serverplayer.isReceivingWaypoints()) {
                    ServerLevel.this.getWaypointManager().addPlayer(serverplayer);
                }

                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof WaypointTransmitter waypointtransmitter) {
                if (waypointtransmitter.isTransmittingWaypoint()) {
                    ServerLevel.this.getWaypointManager().trackWaypoint(waypointtransmitter);
                }
            }

            if (entity instanceof Mob mob) {
                if (ServerLevel.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";

                    Util.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                ServerLevel.this.navigatingMobs.add(mob);
            }

            if (entity instanceof EnderDragon enderdragon) {
                for (EnderDragonPart enderdragonpart : enderdragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.put(enderdragonpart.getId(), enderdragonpart);
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
        }

        public void onTrackingEnd(Entity entity) {
            ServerLevel.this.getChunkSource().removeEntity(entity);
            if (entity instanceof ServerPlayer serverplayer) {
                ServerLevel.this.players.remove(serverplayer);
                ServerLevel.this.getWaypointManager().removePlayer(serverplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob mob) {
                if (ServerLevel.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";

                    Util.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                ServerLevel.this.navigatingMobs.remove(mob);
            }

            if (entity instanceof EnderDragon enderdragon) {
                for (EnderDragonPart enderdragonpart : enderdragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.remove(enderdragonpart.getId());
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
            ServerLevel.this.debugSynchronizers.dropEntity(entity);
        }

        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }
}
