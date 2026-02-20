package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class WorldGenRegion implements WorldGenLevel {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private final ChunkAccess center;
    private final ServerLevel level;
    private final long seed;
    private final LevelData levelData;
    private final RandomSource random;
    private final DimensionType dimensionType;
    private final WorldGenTickAccess<Block> blockTicks = new WorldGenTickAccess<Block>((blockpos) -> {
        return this.getChunk(blockpos).getBlockTicks();
    });
    private final WorldGenTickAccess<Fluid> fluidTicks = new WorldGenTickAccess<Fluid>((blockpos) -> {
        return this.getChunk(blockpos).getFluidTicks();
    });
    private final BiomeManager biomeManager;
    private final ChunkStep generatingStep;
    private @Nullable Supplier<String> currentlyGenerating;
    private final AtomicLong subTickCount = new AtomicLong();
    private static final Identifier WORLDGEN_REGION_RANDOM = Identifier.withDefaultNamespace("worldgen_region_random");

    public WorldGenRegion(ServerLevel level, StaticCache2D<GenerationChunkHolder> cache, ChunkStep generatingStep, ChunkAccess center) {
        this.generatingStep = generatingStep;
        this.cache = cache;
        this.center = center;
        this.level = level;
        this.seed = level.getSeed();
        this.levelData = level.getLevelData();
        this.random = level.getChunkSource().randomState().getOrCreateRandomFactory(WorldGenRegion.WORLDGEN_REGION_RANDOM).at(this.center.getPos().getWorldPosition());
        this.dimensionType = level.dimensionType();
        this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed));
    }

    public boolean isOldChunkAround(ChunkPos pos, int range) {
        return this.level.getChunkSource().chunkMap.isOldChunkAround(pos, range);
    }

    public ChunkPos getCenter() {
        return this.center.getPos();
    }

    @Override
    public void setCurrentlyGenerating(@Nullable Supplier<String> currentlyGenerating) {
        this.currentlyGenerating = currentlyGenerating;
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY);
    }

    @Override
    public @Nullable ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus targetStatus, boolean loadOrGenerate) {
        int k = this.center.getPos().getChessboardDistance(chunkX, chunkZ);
        ChunkStatus chunkstatus1 = k >= this.generatingStep.directDependencies().size() ? null : this.generatingStep.directDependencies().get(k);
        GenerationChunkHolder generationchunkholder;

        if (chunkstatus1 != null) {
            generationchunkholder = this.cache.get(chunkX, chunkZ);
            if (targetStatus.isOrBefore(chunkstatus1)) {
                ChunkAccess chunkaccess = generationchunkholder.getChunkIfPresentUnchecked(chunkstatus1);

                if (chunkaccess != null) {
                    return chunkaccess;
                }
            }
        } else {
            generationchunkholder = null;
        }

        CrashReport crashreport = CrashReport.forThrowable(new IllegalStateException("Requested chunk unavailable during world generation"), "Exception generating new chunk");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk request details");

        crashreportcategory.setDetail("Requested chunk", String.format(Locale.ROOT, "%d, %d", chunkX, chunkZ));
        crashreportcategory.setDetail("Generating status", () -> {
            return this.generatingStep.targetStatus().getName();
        });
        Objects.requireNonNull(targetStatus);
        crashreportcategory.setDetail("Requested status", targetStatus::getName);
        crashreportcategory.setDetail("Actual status", () -> {
            return generationchunkholder == null ? "[out of cache bounds]" : generationchunkholder.getPersistedStatus().getName();
        });
        crashreportcategory.setDetail("Maximum allowed status", () -> {
            return chunkstatus1 == null ? "null" : chunkstatus1.getName();
        });
        ChunkDependencies chunkdependencies = this.generatingStep.directDependencies();

        Objects.requireNonNull(chunkdependencies);
        crashreportcategory.setDetail("Dependencies", chunkdependencies::toString);
        crashreportcategory.setDetail("Requested distance", k);
        ChunkPos chunkpos = this.center.getPos();

        Objects.requireNonNull(chunkpos);
        crashreportcategory.setDetail("Generating chunk", chunkpos::toString);
        throw new ReportedException(crashreport);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        int k = this.center.getPos().getChessboardDistance(chunkX, chunkZ);

        return k < this.generatingStep.directDependencies().size();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getChunk(pos).getFluidState(pos);
    }

    @Override
    public @Nullable Player getNearestPlayer(double x, double y, double z, double maxDist, @Nullable Predicate<Entity> predicate) {
        return null;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return this.level.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int updateLimit) {
        BlockState blockstate = this.getBlockState(pos);

        if (blockstate.isAir()) {
            return false;
        } else {
            if (dropResources) {
                BlockEntity blockentity = blockstate.hasBlockEntity() ? this.getBlockEntity(pos) : null;

                Block.dropResources(blockstate, this.level, pos, blockentity, breaker, ItemStack.EMPTY);
            }

            return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3, updateLimit);
        }
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunkaccess = this.getChunk(pos);
        BlockEntity blockentity = chunkaccess.getBlockEntity(pos);

        if (blockentity != null) {
            return blockentity;
        } else {
            CompoundTag compoundtag = chunkaccess.getBlockEntityNbt(pos);
            BlockState blockstate = chunkaccess.getBlockState(pos);

            if (compoundtag != null) {
                if ("DUMMY".equals(compoundtag.getStringOr("id", ""))) {
                    if (!blockstate.hasBlockEntity()) {
                        return null;
                    }

                    blockentity = ((EntityBlock) blockstate.getBlock()).newBlockEntity(pos, blockstate);
                } else {
                    blockentity = BlockEntity.loadStatic(pos, blockstate, compoundtag, this.level.registryAccess());
                }

                if (blockentity != null) {
                    chunkaccess.setBlockEntity(blockentity);
                    return blockentity;
                }
            }

            if (blockstate.hasBlockEntity()) {
                WorldGenRegion.LOGGER.warn("Tried to access a block entity before it was created. {}", pos);
            }

            return null;
        }
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkPos chunkpos = this.getCenter();
        int k = Math.abs(chunkpos.x - i);
        int l = Math.abs(chunkpos.z - j);

        if (k <= this.generatingStep.blockStateWriteRadius() && l <= this.generatingStep.blockStateWriteRadius()) {
            if (this.center.isUpgrading()) {
                LevelHeightAccessor levelheightaccessor = this.center.getHeightAccessorForGeneration();

                if (levelheightaccessor.isOutsideBuildHeight(pos.getY())) {
                    return false;
                }
            }

            return true;
        } else {
            Util.logAndPauseIfInIde("Detected setBlock in a far chunk [" + i + ", " + j + "], pos: " + String.valueOf(pos) + ", status: " + String.valueOf(this.generatingStep.targetStatus()) + (this.currentlyGenerating == null ? "" : ", currently generating: " + (String) this.currentlyGenerating.get()));
            return false;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState blockState, @Block.UpdateFlags int updateFlags, int updateLimit) {
        if (!this.ensureCanWrite(pos)) {
            return false;
        } else {
            ChunkAccess chunkaccess = this.getChunk(pos);
            BlockState blockstate1 = chunkaccess.setBlockState(pos, blockState, updateFlags);

            if (blockstate1 != null) {
                this.level.updatePOIOnBlockStateChange(pos, blockstate1, blockState);
            }

            if (blockState.hasBlockEntity()) {
                if (chunkaccess.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK) {
                    BlockEntity blockentity = ((EntityBlock) blockState.getBlock()).newBlockEntity(pos, blockState);

                    if (blockentity != null) {
                        chunkaccess.setBlockEntity(blockentity);
                    } else {
                        chunkaccess.removeBlockEntity(pos);
                    }
                } else {
                    CompoundTag compoundtag = new CompoundTag();

                    compoundtag.putInt("x", pos.getX());
                    compoundtag.putInt("y", pos.getY());
                    compoundtag.putInt("z", pos.getZ());
                    compoundtag.putString("id", "DUMMY");
                    chunkaccess.setBlockEntityNbt(compoundtag);
                }
            } else if (blockstate1 != null && blockstate1.hasBlockEntity()) {
                chunkaccess.removeBlockEntity(pos);
            }

            if (blockState.hasPostProcess(this, pos) && (updateFlags & 16) == 0) {
                this.markPosForPostprocessing(pos);
            }

            return true;
        }
    }

    private void markPosForPostprocessing(BlockPos blockPos) {
        this.getChunk(blockPos).markPosForPostprocessing(blockPos);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        int i = SectionPos.blockToSectionCoord(entity.getBlockX());
        int j = SectionPos.blockToSectionCoord(entity.getBlockZ());

        this.getChunk(i, j).addEntity(entity);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** @deprecated */
    @Deprecated
    @Override
    public ServerLevel getLevel() {
        return this.level;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        if (!this.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            throw new RuntimeException("We are asking a region for a chunk out of bound");
        } else {
            return new DifficultyInstance(this.level.getDifficulty(), this.level.getDayTime(), 0L, this.level.getMoonBrightness(pos));
        }
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return this.level.getServer();
    }

    @Override
    public ChunkSource getChunkSource() {
        return this.level.getChunkSource();
    }

    @Override
    public long getSeed() {
        return this.seed;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public int getSeaLevel() {
        return this.level.getSeaLevel();
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(type, x & 15, z & 15) + 1;
    }

    @Override
    public void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {}

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {}

    @Override
    public void levelEvent(@Nullable Entity source, int type, BlockPos pos, int data) {}

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {}

    @Override
    public DimensionType dimensionType() {
        return this.dimensionType;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(this.getFluidState(pos));
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector) {
        return Collections.emptyList();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB bb, @Nullable Predicate<? super Entity> selector) {
        return Collections.emptyList();
    }

    @Override
    public List<Player> players() {
        return Collections.emptyList();
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public long nextSubTickCount() {
        return this.subTickCount.getAndIncrement();
    }

    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        return EnvironmentAttributeReader.EMPTY;
    }
}
