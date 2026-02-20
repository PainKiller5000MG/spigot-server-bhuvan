package net.minecraft.world.level.chunk;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class ChunkAccess implements LightChunk, StructureAccess, BiomeManager.NoiseBiomeSource {

    public static final int NO_FILLED_SECTION = -1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LongSet EMPTY_REFERENCE_SET = new LongOpenHashSet();
    protected final @Nullable ShortList[] postProcessing;
    private volatile boolean unsaved;
    private volatile boolean isLightCorrect;
    protected final ChunkPos chunkPos;
    private long inhabitedTime;
    /** @deprecated */
    @Deprecated
    private @Nullable BiomeGenerationSettings carverBiomeSettings;
    protected @Nullable NoiseChunk noiseChunk;
    protected final UpgradeData upgradeData;
    protected @Nullable BlendingData blendingData;
    public final Map<Heightmap.Types, Heightmap> heightmaps = Maps.newEnumMap(Heightmap.Types.class);
    protected ChunkSkyLightSources skyLightSources;
    private final Map<Structure, StructureStart> structureStarts = Maps.newHashMap();
    private final Map<Structure, LongSet> structuresRefences = Maps.newHashMap();
    protected final Map<BlockPos, CompoundTag> pendingBlockEntities = Maps.newHashMap();
    public final Map<BlockPos, BlockEntity> blockEntities = new Object2ObjectOpenHashMap();
    protected final LevelHeightAccessor levelHeightAccessor;
    protected final LevelChunkSection[] sections;

    public ChunkAccess(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, long inhabitedTime, LevelChunkSection @Nullable [] sections, @Nullable BlendingData blendingData) {
        this.chunkPos = chunkPos;
        this.upgradeData = upgradeData;
        this.levelHeightAccessor = levelHeightAccessor;
        this.sections = new LevelChunkSection[levelHeightAccessor.getSectionsCount()];
        this.inhabitedTime = inhabitedTime;
        this.postProcessing = new ShortList[levelHeightAccessor.getSectionsCount()];
        this.blendingData = blendingData;
        this.skyLightSources = new ChunkSkyLightSources(levelHeightAccessor);
        if (sections != null) {
            if (this.sections.length == sections.length) {
                System.arraycopy(sections, 0, this.sections, 0, this.sections.length);
            } else {
                ChunkAccess.LOGGER.warn("Could not set level chunk sections, array length is {} instead of {}", sections.length, this.sections.length);
            }
        }

        replaceMissingSections(containerFactory, this.sections);
    }

    private static void replaceMissingSections(PalettedContainerFactory containerFactory, LevelChunkSection[] sections) {
        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(containerFactory);
            }
        }

    }

    public GameEventListenerRegistry getListenerRegistry(int section) {
        return GameEventListenerRegistry.NOOP;
    }

    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state) {
        return this.setBlockState(pos, state, 3);
    }

    public abstract @Nullable BlockState setBlockState(BlockPos pos, BlockState state, @Block.UpdateFlags int flags);

    public abstract void setBlockEntity(BlockEntity blockEntity);

    public abstract void addEntity(Entity entity);

    public int getHighestFilledSectionIndex() {
        LevelChunkSection[] alevelchunksection = this.getSections();

        for (int i = alevelchunksection.length - 1; i >= 0; --i) {
            LevelChunkSection levelchunksection = alevelchunksection[i];

            if (!levelchunksection.hasOnlyAir()) {
                return i;
            }
        }

        return -1;
    }

    /** @deprecated */
    @Deprecated(forRemoval = true)
    public int getHighestSectionPosition() {
        int i = this.getHighestFilledSectionIndex();

        return i == -1 ? this.getMinY() : SectionPos.sectionToBlockCoord(this.getSectionYFromSectionIndex(i));
    }

    public Set<BlockPos> getBlockEntitiesPos() {
        Set<BlockPos> set = Sets.newHashSet(this.pendingBlockEntities.keySet());

        set.addAll(this.blockEntities.keySet());
        return set;
    }

    public LevelChunkSection[] getSections() {
        return this.sections;
    }

    public LevelChunkSection getSection(int sectionIndex) {
        return this.getSections()[sectionIndex];
    }

    public Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        return Collections.unmodifiableSet(this.heightmaps.entrySet());
    }

    public void setHeightmap(Heightmap.Types key, long[] data) {
        this.getOrCreateHeightmapUnprimed(key).setRawData(this, key, data);
    }

    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return (Heightmap) this.heightmaps.computeIfAbsent(type, (heightmap_types1) -> {
            return new Heightmap(this, heightmap_types1);
        });
    }

    public boolean hasPrimedHeightmap(Heightmap.Types type) {
        return this.heightmaps.get(type) != null;
    }

    public int getHeight(Heightmap.Types type, int x, int z) {
        Heightmap heightmap = (Heightmap) this.heightmaps.get(type);

        if (heightmap == null) {
            if (SharedConstants.IS_RUNNING_IN_IDE && this instanceof LevelChunk) {
                ChunkAccess.LOGGER.error("Unprimed heightmap: {} {} {}", new Object[]{type, x, z});
            }

            Heightmap.primeHeightmaps(this, EnumSet.of(type));
            heightmap = (Heightmap) this.heightmaps.get(type);
        }

        return heightmap.getFirstAvailable(x & 15, z & 15) - 1;
    }

    public ChunkPos getPos() {
        return this.chunkPos;
    }

    @Override
    public @Nullable StructureStart getStartForStructure(Structure structure) {
        return (StructureStart) this.structureStarts.get(structure);
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
        this.structureStarts.put(structure, structureStart);
        this.markUnsaved();
    }

    public Map<Structure, StructureStart> getAllStarts() {
        return Collections.unmodifiableMap(this.structureStarts);
    }

    public void setAllStarts(Map<Structure, StructureStart> starts) {
        this.structureStarts.clear();
        this.structureStarts.putAll(starts);
        this.markUnsaved();
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return (LongSet) this.structuresRefences.getOrDefault(structure, ChunkAccess.EMPTY_REFERENCE_SET);
    }

    @Override
    public void addReferenceForStructure(Structure structure, long reference) {
        ((LongSet) this.structuresRefences.computeIfAbsent(structure, (structure1) -> {
            return new LongOpenHashSet();
        })).add(reference);
        this.markUnsaved();
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        return Collections.unmodifiableMap(this.structuresRefences);
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> data) {
        this.structuresRefences.clear();
        this.structuresRefences.putAll(data);
        this.markUnsaved();
    }

    public boolean isYSpaceEmpty(int yStartInclusive, int yEndInclusive) {
        if (yStartInclusive < this.getMinY()) {
            yStartInclusive = this.getMinY();
        }

        if (yEndInclusive > this.getMaxY()) {
            yEndInclusive = this.getMaxY();
        }

        for (int k = yStartInclusive; k <= yEndInclusive; k += 16) {
            if (!this.getSection(this.getSectionIndex(k)).hasOnlyAir()) {
                return false;
            }
        }

        return true;
    }

    public void markUnsaved() {
        this.unsaved = true;
    }

    public boolean tryMarkSaved() {
        if (this.unsaved) {
            this.unsaved = false;
            return true;
        } else {
            return false;
        }
    }

    public boolean isUnsaved() {
        return this.unsaved;
    }

    public abstract ChunkStatus getPersistedStatus();

    public ChunkStatus getHighestGeneratedStatus() {
        ChunkStatus chunkstatus = this.getPersistedStatus();
        BelowZeroRetrogen belowzeroretrogen = this.getBelowZeroRetrogen();

        if (belowzeroretrogen != null) {
            ChunkStatus chunkstatus1 = belowzeroretrogen.targetStatus();

            return ChunkStatus.max(chunkstatus1, chunkstatus);
        } else {
            return chunkstatus;
        }
    }

    public abstract void removeBlockEntity(BlockPos pos);

    public void markPosForPostprocessing(BlockPos blockPos) {
        ChunkAccess.LOGGER.warn("Trying to mark a block for PostProcessing @ {}, but this operation is not supported.", blockPos);
    }

    public @Nullable ShortList[] getPostProcessing() {
        return this.postProcessing;
    }

    public void addPackedPostProcess(ShortList packedOffsets, int sectionIndex) {
        getOrCreateOffsetList(this.getPostProcessing(), sectionIndex).addAll(packedOffsets);
    }

    public void setBlockEntityNbt(CompoundTag entityTag) {
        BlockPos blockpos = BlockEntity.getPosFromTag(this.chunkPos, entityTag);

        if (!this.blockEntities.containsKey(blockpos)) {
            this.pendingBlockEntities.put(blockpos, entityTag);
        }

    }

    public @Nullable CompoundTag getBlockEntityNbt(BlockPos blockPos) {
        return (CompoundTag) this.pendingBlockEntities.get(blockPos);
    }

    public abstract @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, HolderLookup.Provider registryAccess);

    @Override
    public final void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer) {
        this.findBlocks((blockstate) -> {
            return blockstate.getLightEmission() != 0;
        }, consumer);
    }

    public void findBlocks(Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> consumer) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = this.getMinSectionY(); i <= this.getMaxSectionY(); ++i) {
            LevelChunkSection levelchunksection = this.getSection(this.getSectionIndexFromSectionY(i));

            if (levelchunksection.maybeHas(predicate)) {
                BlockPos blockpos = SectionPos.of(this.chunkPos, i).origin();

                for (int j = 0; j < 16; ++j) {
                    for (int k = 0; k < 16; ++k) {
                        for (int l = 0; l < 16; ++l) {
                            BlockState blockstate = levelchunksection.getBlockState(l, j, k);

                            if (predicate.test(blockstate)) {
                                consumer.accept(blockpos_mutableblockpos.setWithOffset(blockpos, l, j, k), blockstate);
                            }
                        }
                    }
                }
            }
        }

    }

    public abstract TickContainerAccess<Block> getBlockTicks();

    public abstract TickContainerAccess<Fluid> getFluidTicks();

    public boolean canBeSerialized() {
        return true;
    }

    public abstract ChunkAccess.PackedTicks getTicksForSerialization(long currentTick);

    public UpgradeData getUpgradeData() {
        return this.upgradeData;
    }

    public boolean isOldNoiseGeneration() {
        return this.blendingData != null;
    }

    public @Nullable BlendingData getBlendingData() {
        return this.blendingData;
    }

    public long getInhabitedTime() {
        return this.inhabitedTime;
    }

    public void incrementInhabitedTime(long inhabitedTimeDelta) {
        this.inhabitedTime += inhabitedTimeDelta;
    }

    public void setInhabitedTime(long inhabitedTime) {
        this.inhabitedTime = inhabitedTime;
    }

    public static ShortList getOrCreateOffsetList(@Nullable ShortList[] list, int sectionIndex) {
        ShortList shortlist = list[sectionIndex];

        if (shortlist == null) {
            shortlist = new ShortArrayList();
            list[sectionIndex] = shortlist;
        }

        return shortlist;
    }

    public boolean isLightCorrect() {
        return this.isLightCorrect;
    }

    public void setLightCorrect(boolean isLightCorrect) {
        this.isLightCorrect = isLightCorrect;
        this.markUnsaved();
    }

    @Override
    public int getMinY() {
        return this.levelHeightAccessor.getMinY();
    }

    @Override
    public int getHeight() {
        return this.levelHeightAccessor.getHeight();
    }

    public NoiseChunk getOrCreateNoiseChunk(Function<ChunkAccess, NoiseChunk> factory) {
        if (this.noiseChunk == null) {
            this.noiseChunk = (NoiseChunk) factory.apply(this);
        }

        return this.noiseChunk;
    }

    /** @deprecated */
    @Deprecated
    public BiomeGenerationSettings carverBiome(Supplier<BiomeGenerationSettings> source) {
        if (this.carverBiomeSettings == null) {
            this.carverBiomeSettings = (BiomeGenerationSettings) source.get();
        }

        return this.carverBiomeSettings;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        try {
            int l = QuartPos.fromBlock(this.getMinY());
            int i1 = l + QuartPos.fromBlock(this.getHeight()) - 1;
            int j1 = Mth.clamp(quartY, l, i1);
            int k1 = this.getSectionIndex(QuartPos.toBlock(j1));

            return this.sections[k1].getNoiseBiome(quartX & 3, j1 & 3, quartZ & 3);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting biome");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Biome being got");

            crashreportcategory.setDetail("Location", () -> {
                return CrashReportCategory.formatLocation(this, quartX, quartY, quartZ);
            });
            throw new ReportedException(crashreport);
        }
    }

    public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler) {
        ChunkPos chunkpos = this.getPos();
        int i = QuartPos.fromBlock(chunkpos.getMinBlockX());
        int j = QuartPos.fromBlock(chunkpos.getMinBlockZ());
        LevelHeightAccessor levelheightaccessor = this.getHeightAccessorForGeneration();

        for (int k = levelheightaccessor.getMinSectionY(); k <= levelheightaccessor.getMaxSectionY(); ++k) {
            LevelChunkSection levelchunksection = this.getSection(this.getSectionIndexFromSectionY(k));
            int l = QuartPos.fromSection(k);

            levelchunksection.fillBiomesFromNoise(biomeResolver, sampler, i, l, j);
        }

    }

    public boolean hasAnyStructureReferences() {
        return !this.getAllReferences().isEmpty();
    }

    public @Nullable BelowZeroRetrogen getBelowZeroRetrogen() {
        return null;
    }

    public boolean isUpgrading() {
        return this.getBelowZeroRetrogen() != null;
    }

    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return this;
    }

    public void initializeLightSources() {
        this.skyLightSources.fillFrom(this);
    }

    @Override
    public ChunkSkyLightSources getSkyLightSources() {
        return this.skyLightSources;
    }

    public static ProblemReporter.PathElement problemPath(ChunkPos pos) {
        return new ChunkAccess.ChunkPathElement(pos);
    }

    public ProblemReporter.PathElement problemPath() {
        return problemPath(this.getPos());
    }

    public static record PackedTicks(List<SavedTick<Block>> blocks, List<SavedTick<Fluid>> fluids) {

    }

    private static record ChunkPathElement(ChunkPos pos) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return "chunk@" + String.valueOf(this.pos);
        }
    }
}
