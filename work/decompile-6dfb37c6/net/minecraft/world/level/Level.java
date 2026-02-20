package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

public abstract class Level implements LevelAccessor, AutoCloseable {

    public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));
    public static final int MAX_LEVEL_SIZE = 30000000;
    public static final int LONG_PARTICLE_CLIP_RANGE = 512;
    public static final int SHORT_PARTICLE_CLIP_RANGE = 32;
    public static final int MAX_BRIGHTNESS = 15;
    public static final int MAX_ENTITY_SPAWN_Y = 20000000;
    public static final int MIN_ENTITY_SPAWN_Y = -20000000;
    public static final WeightedList<ExplosionParticleInfo> DEFAULT_EXPLOSION_BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder().add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F)).add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F)).build();
    protected final List<TickingBlockEntity> blockEntityTickers = Lists.newArrayList();
    protected final CollectingNeighborUpdater neighborUpdater;
    private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
    private boolean tickingBlockEntities;
    public final Thread thread;
    private final boolean isDebug;
    private int skyDarken;
    protected int randValue = RandomSource.create().nextInt();
    protected final int addend = 1013904223;
    protected float oRainLevel;
    public float rainLevel;
    protected float oThunderLevel;
    public float thunderLevel;
    public final RandomSource random = RandomSource.create();
    /** @deprecated */
    @Deprecated
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
    private final Holder<DimensionType> dimensionTypeRegistration;
    public final WritableLevelData levelData;
    private final boolean isClientSide;
    private final BiomeManager biomeManager;
    private final ResourceKey<Level> dimension;
    private final RegistryAccess registryAccess;
    private final DamageSources damageSources;
    private final PalettedContainerFactory palettedContainerFactory;
    private long subTickCount;

    protected Level(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        this.levelData = levelData;
        this.dimensionTypeRegistration = dimensionTypeRegistration;
        this.dimension = dimension;
        this.isClientSide = isClientSide;
        this.thread = Thread.currentThread();
        this.biomeManager = new BiomeManager(this, biomeZoomSeed);
        this.isDebug = isDebug;
        this.neighborUpdater = new CollectingNeighborUpdater(this, maxChainedNeighborUpdates);
        this.registryAccess = registryAccess;
        this.palettedContainerFactory = PalettedContainerFactory.create(registryAccess);
        this.damageSources = new DamageSources(registryAccess);
    }

    @Override
    public boolean isClientSide() {
        return this.isClientSide;
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return null;
    }

    public boolean isInWorldBounds(BlockPos pos) {
        return !this.isOutsideBuildHeight(pos) && isInWorldBoundsHorizontal(pos);
    }

    public boolean isInValidBounds(BlockPos pos) {
        return !this.isOutsideBuildHeight(pos) && isInValidBoundsHorizontal(pos);
    }

    public static boolean isInSpawnableBounds(BlockPos pos) {
        return !isOutsideSpawnableHeight(pos.getY()) && isInWorldBoundsHorizontal(pos);
    }

    private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000;
    }

    private static boolean isInValidBoundsHorizontal(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());

        return ChunkPos.isValid(i, j);
    }

    private static boolean isOutsideSpawnableHeight(int y) {
        return y < -20000000 || y >= 20000000;
    }

    public LevelChunk getChunkAt(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        return (LevelChunk) this.getChunk(chunkX, chunkZ, ChunkStatus.FULL);
    }

    @Override
    public @Nullable ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean loadOrGenerate) {
        ChunkAccess chunkaccess = this.getChunkSource().getChunk(chunkX, chunkZ, status, loadOrGenerate);

        if (chunkaccess == null && loadOrGenerate) {
            throw new IllegalStateException("Should always be able to create a chunk!");
        } else {
            return chunkaccess;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState blockState, @Block.UpdateFlags int updateFlags) {
        return this.setBlock(pos, blockState, updateFlags, 512);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState blockState, @Block.UpdateFlags int updateFlags, int updateLimit) {
        if (!this.isInValidBounds(pos)) {
            return false;
        } else if (!this.isClientSide() && this.isDebug()) {
            return false;
        } else {
            LevelChunk levelchunk = this.getChunkAt(pos);
            Block block = blockState.getBlock();
            BlockState blockstate1 = levelchunk.setBlockState(pos, blockState, updateFlags);

            if (blockstate1 == null) {
                return false;
            } else {
                BlockState blockstate2 = this.getBlockState(pos);

                if (blockstate2 == blockState) {
                    if (blockstate1 != blockstate2) {
                        this.setBlocksDirty(pos, blockstate1, blockstate2);
                    }

                    if ((updateFlags & 2) != 0 && (!this.isClientSide() || (updateFlags & 4) == 0) && (this.isClientSide() || levelchunk.getFullStatus() != null && levelchunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
                        this.sendBlockUpdated(pos, blockstate1, blockState, updateFlags);
                    }

                    if ((updateFlags & 1) != 0) {
                        this.updateNeighborsAt(pos, blockstate1.getBlock());
                        if (!this.isClientSide() && blockState.hasAnalogOutputSignal()) {
                            this.updateNeighbourForOutputSignal(pos, block);
                        }
                    }

                    if ((updateFlags & 16) == 0 && updateLimit > 0) {
                        int k = updateFlags & -34;

                        blockstate1.updateIndirectNeighbourShapes(this, pos, k, updateLimit - 1);
                        blockState.updateNeighbourShapes(this, pos, k, updateLimit - 1);
                        blockState.updateIndirectNeighbourShapes(this, pos, k, updateLimit - 1);
                    }

                    this.updatePOIOnBlockStateChange(pos, blockstate1, blockstate2);
                }

                return true;
            }
        }
    }

    public void updatePOIOnBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState) {}

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        FluidState fluidstate = this.getFluidState(pos);

        return this.setBlock(pos, fluidstate.createLegacyBlock(), 3 | (movedByPiston ? 64 : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int updateLimit) {
        BlockState blockstate = this.getBlockState(pos);

        if (blockstate.isAir()) {
            return false;
        } else {
            FluidState fluidstate = this.getFluidState(pos);

            if (!(blockstate.getBlock() instanceof BaseFireBlock)) {
                this.levelEvent(2001, pos, Block.getId(blockstate));
            }

            if (dropResources) {
                BlockEntity blockentity = blockstate.hasBlockEntity() ? this.getBlockEntity(pos) : null;

                Block.dropResources(blockstate, this, pos, blockentity, breaker, ItemStack.EMPTY);
            }

            boolean flag1 = this.setBlock(pos, fluidstate.createLegacyBlock(), 3, updateLimit);

            if (flag1) {
                this.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(breaker, blockstate));
            }

            return flag1;
        }
    }

    public void addDestroyBlockEffect(BlockPos pos, BlockState blockState) {}

    public boolean setBlockAndUpdate(BlockPos pos, BlockState blockState) {
        return this.setBlock(pos, blockState, 3);
    }

    public abstract void sendBlockUpdated(BlockPos pos, BlockState old, BlockState current, @Block.UpdateFlags int updateFlags);

    public void setBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState) {}

    public void updateNeighborsAt(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation) {}

    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block blockObject, Direction skipDirection, @Nullable Orientation orientation) {}

    public void neighborChanged(BlockPos pos, Block changedBlock, @Nullable Orientation orientation) {}

    public void neighborChanged(BlockState state, BlockPos pos, Block changedBlock, @Nullable Orientation orientation, boolean movedByPiston) {}

    @Override
    public void neighborShapeChanged(Direction direction, BlockPos pos, BlockPos neighborPos, BlockState neighborState, @Block.UpdateFlags int updateFlags, int updateLimit) {
        this.neighborUpdater.shapeUpdate(direction, neighborState, pos, neighborPos, updateFlags, updateLimit);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        int k;

        if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
            if (this.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                k = this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(type, x & 15, z & 15) + 1;
            } else {
                k = this.getMinY();
            }
        } else {
            k = this.getSeaLevel() + 1;
        }

        return k;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.getChunkSource().getLightEngine();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!this.isInValidBounds(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            LevelChunk levelchunk = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));

            return levelchunk.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (!this.isInValidBounds(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunk levelchunk = this.getChunkAt(pos);

            return levelchunk.getFluidState(pos);
        }
    }

    public boolean isBrightOutside() {
        return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
    }

    public boolean isDarkOutside() {
        return !this.dimensionType().hasFixedTime() && !this.isBrightOutside();
    }

    @Override
    public void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.playSound(except, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, sound, source, volume, pitch);
    }

    public abstract void playSeededSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed);

    public void playSeededSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, long seed) {
        this.playSeededSound(except, x, y, z, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, volume, pitch, seed);
    }

    public abstract void playSeededSound(@Nullable Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed);

    public void playSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source) {
        this.playSound(except, x, y, z, sound, source, 1.0F, 1.0F);
    }

    public void playSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.playSeededSound(except, x, y, z, sound, source, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        this.playSeededSound(except, x, y, z, sound, source, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Entity except, Entity sourceEntity, SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.playSeededSound(except, sourceEntity, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playLocalSound(BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay) {
        this.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, sound, source, volume, pitch, distanceDelay);
    }

    public void playLocalSound(Entity sourceEntity, SoundEvent sound, SoundSource source, float volume, float pitch) {}

    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay) {}

    public void playPlayerSound(SoundEvent sound, SoundSource source, float volume, float pitch) {}

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {}

    public void addParticle(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double xd, double yd, double zd) {}

    public void addAlwaysVisibleParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {}

    public void addAlwaysVisibleParticle(ParticleOptions particle, boolean overrideLimiter, double x, double y, double z, double xd, double yd, double zd) {}

    public void addBlockEntityTicker(TickingBlockEntity ticker) {
        (this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(ticker);
    }

    public void tickBlockEntities() {
        this.tickingBlockEntities = true;
        if (!this.pendingBlockEntityTickers.isEmpty()) {
            this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
            this.pendingBlockEntityTickers.clear();
        }

        Iterator<TickingBlockEntity> iterator = this.blockEntityTickers.iterator();
        boolean flag = this.tickRateManager().runsNormally();

        while (iterator.hasNext()) {
            TickingBlockEntity tickingblockentity = (TickingBlockEntity) iterator.next();

            if (tickingblockentity.isRemoved()) {
                iterator.remove();
            } else if (flag && this.shouldTickBlocksAt(tickingblockentity.getPos())) {
                tickingblockentity.tick();
            }
        }

        this.tickingBlockEntities = false;
    }

    public <T extends Entity> void guardEntityTick(Consumer<T> tick, T entity) {
        try {
            tick.accept(entity);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking entity");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being ticked");

            entity.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    public boolean shouldTickDeath(Entity entity) {
        return true;
    }

    public boolean shouldTickBlocksAt(long chunkPos) {
        return true;
    }

    public boolean shouldTickBlocksAt(BlockPos pos) {
        return this.shouldTickBlocksAt(ChunkPos.asLong(pos));
    }

    public void explode(@Nullable Entity source, double x, double y, double z, float r, Level.ExplosionInteraction blockInteraction) {
        this.explode(source, Explosion.getDefaultDamageSource(this, source), (ExplosionDamageCalculator) null, x, y, z, r, false, blockInteraction, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, Level.DEFAULT_EXPLOSION_BLOCK_PARTICLES, SoundEvents.GENERIC_EXPLODE);
    }

    public void explode(@Nullable Entity source, double x, double y, double z, float r, boolean fire, Level.ExplosionInteraction blockInteraction) {
        this.explode(source, Explosion.getDefaultDamageSource(this, source), (ExplosionDamageCalculator) null, x, y, z, r, fire, blockInteraction, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, Level.DEFAULT_EXPLOSION_BLOCK_PARTICLES, SoundEvents.GENERIC_EXPLODE);
    }

    public void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, Vec3 boomPos, float r, boolean fire, Level.ExplosionInteraction blockInteraction) {
        this.explode(source, damageSource, damageCalculator, boomPos.x(), boomPos.y(), boomPos.z(), r, fire, blockInteraction, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, Level.DEFAULT_EXPLOSION_BLOCK_PARTICLES, SoundEvents.GENERIC_EXPLODE);
    }

    public void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float r, boolean fire, Level.ExplosionInteraction interactionType) {
        this.explode(source, damageSource, damageCalculator, x, y, z, r, fire, interactionType, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, Level.DEFAULT_EXPLOSION_BLOCK_PARTICLES, SoundEvents.GENERIC_EXPLODE);
    }

    public abstract void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float r, boolean fire, Level.ExplosionInteraction interactionType, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> explosionSound);

    public abstract String gatherChunkSourceStats();

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return !this.isInValidBounds(pos) ? null : (!this.isClientSide() && Thread.currentThread() != this.thread ? null : this.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE));
    }

    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockpos = blockEntity.getBlockPos();

        if (this.isInValidBounds(blockpos)) {
            this.getChunkAt(blockpos).addAndRegisterBlockEntity(blockEntity);
        }
    }

    public void removeBlockEntity(BlockPos pos) {
        if (this.isInValidBounds(pos)) {
            this.getChunkAt(pos).removeBlockEntity(pos);
        }
    }

    public boolean isLoaded(BlockPos pos) {
        return !this.isInValidBounds(pos) ? false : this.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public boolean loadedAndEntityCanStandOnFace(BlockPos pos, Entity entity, Direction faceDirection) {
        if (!this.isInValidBounds(pos)) {
            return false;
        } else {
            ChunkAccess chunkaccess = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);

            return chunkaccess == null ? false : chunkaccess.getBlockState(pos).entityCanStandOnFace(this, pos, entity, faceDirection);
        }
    }

    public boolean loadedAndEntityCanStandOn(BlockPos pos, Entity entity) {
        return this.loadedAndEntityCanStandOnFace(pos, entity, Direction.UP);
    }

    public void updateSkyBrightness() {
        this.skyDarken = (int) (15.0F - (Float) this.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL));
    }

    public void setSpawnSettings(boolean spawnEnemies) {
        this.getChunkSource().setSpawnSettings(spawnEnemies);
    }

    public abstract void setRespawnData(LevelData.RespawnData respawnData);

    public abstract LevelData.RespawnData getRespawnData();

    public LevelData.RespawnData getWorldBorderAdjustedRespawnData(LevelData.RespawnData respawnData) {
        WorldBorder worldborder = this.getWorldBorder();

        if (!worldborder.isWithinBounds(respawnData.pos())) {
            BlockPos blockpos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(worldborder.getCenterX(), 0.0D, worldborder.getCenterZ()));

            return LevelData.RespawnData.of(respawnData.dimension(), blockpos, respawnData.yaw(), respawnData.pitch());
        } else {
            return respawnData;
        }
    }

    protected void prepareWeather() {
        if (this.levelData.isRaining()) {
            this.rainLevel = 1.0F;
            if (this.levelData.isThundering()) {
                this.thunderLevel = 1.0F;
            }
        }

    }

    public void close() throws IOException {
        this.getChunkSource().close();
    }

    @Override
    public @Nullable BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB bb, Predicate<? super Entity> selector) {
        Profiler.get().incrementCounter("getEntities");
        List<Entity> list = Lists.newArrayList();

        this.getEntities().get(bb, (entity1) -> {
            if (entity1 != except && selector.test(entity1)) {
                list.add(entity1);
            }

        });

        for (EnderDragonPart enderdragonpart : this.dragonParts()) {
            if (enderdragonpart != except && enderdragonpart.parentMob != except && selector.test(enderdragonpart) && bb.intersects(enderdragonpart.getBoundingBox())) {
                list.add(enderdragonpart);
            }
        }

        return list;
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector) {
        List<T> list = Lists.newArrayList();

        this.getEntities(type, bb, selector, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector, List<? super T> output) {
        this.getEntities(type, bb, selector, output, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector, List<? super T> output, int maxResults) {
        Profiler.get().incrementCounter("getEntities");
        this.getEntities().get(type, bb, (entity) -> {
            if (selector.test(entity)) {
                output.add(entity);
                if (output.size() >= maxResults) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            if (entity instanceof EnderDragon enderdragon) {
                for (EnderDragonPart enderdragonpart : enderdragon.getSubEntities()) {
                    T t0 = type.tryCast(enderdragonpart);

                    if (t0 != null && selector.test(t0)) {
                        output.add(t0);
                        if (output.size() >= maxResults) {
                            return AbortableIterationConsumer.Continuation.ABORT;
                        }
                    }
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public <T extends Entity> boolean hasEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector) {
        Profiler.get().incrementCounter("hasEntities");
        MutableBoolean mutableboolean = new MutableBoolean();

        this.getEntities().get(type, bb, (entity) -> {
            if (selector.test(entity)) {
                mutableboolean.setTrue();
                return AbortableIterationConsumer.Continuation.ABORT;
            } else {
                if (entity instanceof EnderDragon) {
                    EnderDragon enderdragon = (EnderDragon) entity;

                    for (EnderDragonPart enderdragonpart : enderdragon.getSubEntities()) {
                        T t0 = type.tryCast(enderdragonpart);

                        if (t0 != null && selector.test(t0)) {
                            mutableboolean.setTrue();
                            return AbortableIterationConsumer.Continuation.ABORT;
                        }
                    }
                }

                return AbortableIterationConsumer.Continuation.CONTINUE;
            }
        });
        return mutableboolean.isTrue();
    }

    public List<Entity> getPushableEntities(Entity pusher, AABB boundingBox) {
        return this.getEntities(pusher, boundingBox, EntitySelector.pushableBy(pusher));
    }

    public abstract @Nullable Entity getEntity(int id);

    public @Nullable Entity getEntity(UUID uuid) {
        return (Entity) this.getEntities().get(uuid);
    }

    public @Nullable Entity getEntityInAnyDimension(UUID uuid) {
        return this.getEntity(uuid);
    }

    public @Nullable Player getPlayerInAnyDimension(UUID uuid) {
        return this.getPlayerByUUID(uuid);
    }

    public abstract Collection<EnderDragonPart> dragonParts();

    public void blockEntityChanged(BlockPos pos) {
        if (this.hasChunkAt(pos)) {
            this.getChunkAt(pos).markUnsaved();
        }

    }

    public void onBlockEntityAdded(BlockEntity blockEntity) {}

    public long getDayTime() {
        return this.levelData.getDayTime();
    }

    public boolean mayInteract(Entity entity, BlockPos pos) {
        return true;
    }

    public void broadcastEntityEvent(Entity entity, byte event) {}

    public void broadcastDamageEvent(Entity entity, DamageSource source) {}

    public void blockEvent(BlockPos pos, Block block, int b0, int b1) {
        this.getBlockState(pos).triggerEvent(this, pos, b0, b1);
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    public abstract TickRateManager tickRateManager();

    public float getThunderLevel(float a) {
        return Mth.lerp(a, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(a);
    }

    public void setThunderLevel(float thunderLevel) {
        float f1 = Mth.clamp(thunderLevel, 0.0F, 1.0F);

        this.oThunderLevel = f1;
        this.thunderLevel = f1;
    }

    public float getRainLevel(float a) {
        return Mth.lerp(a, this.oRainLevel, this.rainLevel);
    }

    public void setRainLevel(float rainLevel) {
        float f1 = Mth.clamp(rainLevel, 0.0F, 1.0F);

        this.oRainLevel = f1;
        this.rainLevel = f1;
    }

    public boolean canHaveWeather() {
        return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling() && this.dimension() != Level.END;
    }

    public boolean isThundering() {
        return this.canHaveWeather() && (double) this.getThunderLevel(1.0F) > 0.9D;
    }

    public boolean isRaining() {
        return this.canHaveWeather() && (double) this.getRainLevel(1.0F) > 0.2D;
    }

    public boolean isRainingAt(BlockPos pos) {
        return this.precipitationAt(pos) == Biome.Precipitation.RAIN;
    }

    public Biome.Precipitation precipitationAt(BlockPos pos) {
        if (!this.isRaining()) {
            return Biome.Precipitation.NONE;
        } else if (!this.canSeeSky(pos)) {
            return Biome.Precipitation.NONE;
        } else if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            return Biome.Precipitation.NONE;
        } else {
            Biome biome = (Biome) this.getBiome(pos).value();

            return biome.getPrecipitationAt(pos, this.getSeaLevel());
        }
    }

    public abstract @Nullable MapItemSavedData getMapData(MapId id);

    public void globalLevelEvent(int type, BlockPos pos, int data) {}

    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashreportcategory = report.addCategory("Affected level", 1);

        crashreportcategory.setDetail("All players", () -> {
            List<? extends Player> list = this.players();
            int i = list.size();

            return i + " total; " + (String) list.stream().map(Player::debugInfo).collect(Collectors.joining(", "));
        });
        ChunkSource chunksource = this.getChunkSource();

        Objects.requireNonNull(chunksource);
        crashreportcategory.setDetail("Chunk stats", chunksource::gatherStats);
        crashreportcategory.setDetail("Level dimension", () -> {
            return this.dimension().identifier().toString();
        });

        try {
            this.levelData.fillCrashReportCategory(crashreportcategory, this);
        } catch (Throwable throwable) {
            crashreportcategory.setDetailError("Level Data Unobtainable", throwable);
        }

        return crashreportcategory;
    }

    public abstract void destroyBlockProgress(int id, BlockPos blockPos, int progress);

    public void createFireworks(double x, double y, double z, double xd, double yd, double zd, List<FireworkExplosion> explosions) {}

    public abstract Scoreboard getScoreboard();

    public void updateNeighbourForOutputSignal(BlockPos pos, Block changedBlock) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction);

            if (this.hasChunkAt(blockpos1)) {
                BlockState blockstate = this.getBlockState(blockpos1);

                if (blockstate.is(Blocks.COMPARATOR)) {
                    this.neighborChanged(blockstate, blockpos1, changedBlock, (Orientation) null, false);
                } else if (blockstate.isRedstoneConductor(this, blockpos1)) {
                    blockpos1 = blockpos1.relative(direction);
                    blockstate = this.getBlockState(blockpos1);
                    if (blockstate.is(Blocks.COMPARATOR)) {
                        this.neighborChanged(blockstate, blockpos1, changedBlock, (Orientation) null, false);
                    }
                }
            }
        }

    }

    @Override
    public int getSkyDarken() {
        return this.skyDarken;
    }

    public void setSkyFlashTime(int skyFlashTime) {}

    public void sendPacketToServer(Packet<?> packet) {
        throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
    }

    @Override
    public DimensionType dimensionType() {
        return this.dimensionTypeRegistration.value();
    }

    public Holder<DimensionType> dimensionTypeRegistration() {
        return this.dimensionTypeRegistration;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(this.getFluidState(pos));
    }

    public abstract RecipeAccess recipeAccess();

    public BlockPos getBlockRandomPos(int xo, int yo, int zo, int yMask) {
        this.randValue = this.randValue * 3 + 1013904223;
        int i1 = this.randValue >> 2;

        return new BlockPos(xo + (i1 & 15), yo + (i1 >> 16 & yMask), zo + (i1 >> 8 & 15));
    }

    public boolean noSave() {
        return false;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    public final boolean isDebug() {
        return this.isDebug;
    }

    public abstract LevelEntityGetter<Entity> getEntities();

    @Override
    public long nextSubTickCount() {
        return (long) (this.subTickCount++);
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public DamageSources damageSources() {
        return this.damageSources;
    }

    @Override
    public abstract EnvironmentAttributeSystem environmentAttributes();

    public abstract PotionBrewing potionBrewing();

    public abstract FuelValues fuelValues();

    public int getClientLeafTintColor(BlockPos pos) {
        return 0;
    }

    public PalettedContainerFactory palettedContainerFactory() {
        return this.palettedContainerFactory;
    }

    public static enum ExplosionInteraction implements StringRepresentable {

        NONE("none"), BLOCK("block"), MOB("mob"), TNT("tnt"), TRIGGER("trigger");

        public static final Codec<Level.ExplosionInteraction> CODEC = StringRepresentable.<Level.ExplosionInteraction>fromEnum(Level.ExplosionInteraction::values);
        private final String id;

        private ExplosionInteraction(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }
}
