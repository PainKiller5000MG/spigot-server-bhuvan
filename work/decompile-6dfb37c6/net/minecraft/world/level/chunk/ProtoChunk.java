package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ProtoChunk extends ChunkAccess {

    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile @Nullable LevelLightEngine lightEngine;
    private volatile ChunkStatus status;
    private final List<CompoundTag> entities;
    private @Nullable CarvingMask carvingMask;
    private @Nullable BelowZeroRetrogen belowZeroRetrogen;
    private final ProtoChunkTicks<Block> blockTicks;
    private final ProtoChunkTicks<Fluid> fluidTicks;

    public ProtoChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, @Nullable BlendingData blendingData) {
        this(chunkPos, upgradeData, (LevelChunkSection[]) null, new ProtoChunkTicks(), new ProtoChunkTicks(), levelHeightAccessor, containerFactory, blendingData);
    }

    public ProtoChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkSection @Nullable [] sections, ProtoChunkTicks<Block> blockTicks, ProtoChunkTicks<Fluid> fluidTicks, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, 0L, sections, blendingData);
        this.status = ChunkStatus.EMPTY;
        this.entities = Lists.newArrayList();
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public ChunkAccess.PackedTicks getTicksForSerialization(long currentTick) {
        return new ChunkAccess.PackedTicks(this.blockTicks.pack(currentTick), this.fluidTicks.pack(currentTick));
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int i = pos.getY();

        if (this.isOutsideBuildHeight(i)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            LevelChunkSection levelchunksection = this.getSection(this.getSectionIndex(i));

            return levelchunksection.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : levelchunksection.getBlockState(pos.getX() & 15, i & 15, pos.getZ() & 15);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int i = pos.getY();

        if (this.isOutsideBuildHeight(i)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunkSection levelchunksection = this.getSection(this.getSectionIndex(i));

            return levelchunksection.hasOnlyAir() ? Fluids.EMPTY.defaultFluidState() : levelchunksection.getFluidState(pos.getX() & 15, i & 15, pos.getZ() & 15);
        }
    }

    @Override
    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state, @Block.UpdateFlags int flags) {
        int j = pos.getX();
        int k = pos.getY();
        int l = pos.getZ();

        if (this.isOutsideBuildHeight(k)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            int i1 = this.getSectionIndex(k);
            LevelChunkSection levelchunksection = this.getSection(i1);
            boolean flag = levelchunksection.hasOnlyAir();

            if (flag && state.is(Blocks.AIR)) {
                return state;
            } else {
                int j1 = SectionPos.sectionRelative(j);
                int k1 = SectionPos.sectionRelative(k);
                int l1 = SectionPos.sectionRelative(l);
                BlockState blockstate1 = levelchunksection.setBlockState(j1, k1, l1, state);

                if (this.status.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                    boolean flag1 = levelchunksection.hasOnlyAir();

                    if (flag1 != flag) {
                        this.lightEngine.updateSectionStatus(pos, flag1);
                    }

                    if (LightEngine.hasDifferentLightProperties(blockstate1, state)) {
                        this.skyLightSources.update(this, j1, k, l1);
                        this.lightEngine.checkBlock(pos);
                    }
                }

                EnumSet<Heightmap.Types> enumset = this.getPersistedStatus().heightmapsAfter();
                EnumSet<Heightmap.Types> enumset1 = null;

                for (Heightmap.Types heightmap_types : enumset) {
                    Heightmap heightmap = (Heightmap) this.heightmaps.get(heightmap_types);

                    if (heightmap == null) {
                        if (enumset1 == null) {
                            enumset1 = EnumSet.noneOf(Heightmap.Types.class);
                        }

                        enumset1.add(heightmap_types);
                    }
                }

                if (enumset1 != null) {
                    Heightmap.primeHeightmaps(this, enumset1);
                }

                for (Heightmap.Types heightmap_types1 : enumset) {
                    ((Heightmap) this.heightmaps.get(heightmap_types1)).update(j1, k, l1, state);
                }

                return blockstate1;
            }
        }
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        this.pendingBlockEntities.remove(blockEntity.getBlockPos());
        this.blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return (BlockEntity) this.blockEntities.get(pos);
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void addEntity(CompoundTag tag) {
        this.entities.add(tag);
    }

    @Override
    public void addEntity(Entity entity) {
        if (!entity.isPassenger()) {
            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(entity.problemPath(), ProtoChunk.LOGGER)) {
                TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, entity.registryAccess());

                entity.save(tagvalueoutput);
                this.addEntity(tagvalueoutput.buildResult());
            }

        }
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
        BelowZeroRetrogen belowzeroretrogen = this.getBelowZeroRetrogen();

        if (belowzeroretrogen != null && structureStart.isValid()) {
            BoundingBox boundingbox = structureStart.getBoundingBox();
            LevelHeightAccessor levelheightaccessor = this.getHeightAccessorForGeneration();

            if (boundingbox.minY() < levelheightaccessor.getMinY() || boundingbox.maxY() > levelheightaccessor.getMaxY()) {
                return;
            }
        }

        super.setStartForStructure(structure, structureStart);
    }

    public List<CompoundTag> getEntities() {
        return this.entities;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return this.status;
    }

    public void setPersistedStatus(ChunkStatus status) {
        this.status = status;
        if (this.belowZeroRetrogen != null && status.isOrAfter(this.belowZeroRetrogen.targetStatus())) {
            this.setBelowZeroRetrogen((BelowZeroRetrogen) null);
        }

        this.markUnsaved();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        if (this.getHighestGeneratedStatus().isOrAfter(ChunkStatus.BIOMES)) {
            return super.getNoiseBiome(quartX, quartY, quartZ);
        } else {
            throw new IllegalStateException("Asking for biomes before we have biomes");
        }
    }

    public static short packOffsetCoordinates(BlockPos blockPos) {
        int i = blockPos.getX();
        int j = blockPos.getY();
        int k = blockPos.getZ();
        int l = i & 15;
        int i1 = j & 15;
        int j1 = k & 15;

        return (short) (l | i1 << 4 | j1 << 8);
    }

    public static BlockPos unpackOffsetCoordinates(short packedCoord, int sectionY, ChunkPos chunkPos) {
        int j = SectionPos.sectionToBlockCoord(chunkPos.x, packedCoord & 15);
        int k = SectionPos.sectionToBlockCoord(sectionY, packedCoord >>> 4 & 15);
        int l = SectionPos.sectionToBlockCoord(chunkPos.z, packedCoord >>> 8 & 15);

        return new BlockPos(j, k, l);
    }

    @Override
    public void markPosForPostprocessing(BlockPos blockPos) {
        if (!this.isOutsideBuildHeight(blockPos)) {
            ChunkAccess.getOrCreateOffsetList(this.postProcessing, this.getSectionIndex(blockPos.getY())).add(packOffsetCoordinates(blockPos));
        }

    }

    @Override
    public void addPackedPostProcess(ShortList packedOffsets, int sectionIndex) {
        ChunkAccess.getOrCreateOffsetList(this.postProcessing, sectionIndex).addAll(packedOffsets);
    }

    public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
        return Collections.unmodifiableMap(this.pendingBlockEntities);
    }

    @Override
    public @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, HolderLookup.Provider registryAccess) {
        BlockEntity blockentity = this.getBlockEntity(blockPos);

        return blockentity != null ? blockentity.saveWithFullMetadata(registryAccess) : (CompoundTag) this.pendingBlockEntities.get(blockPos);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        this.blockEntities.remove(pos);
        this.pendingBlockEntities.remove(pos);
    }

    public @Nullable CarvingMask getCarvingMask() {
        return this.carvingMask;
    }

    public CarvingMask getOrCreateCarvingMask() {
        if (this.carvingMask == null) {
            this.carvingMask = new CarvingMask(this.getHeight(), this.getMinY());
        }

        return this.carvingMask;
    }

    public void setCarvingMask(CarvingMask data) {
        this.carvingMask = data;
    }

    public void setLightEngine(LevelLightEngine lightEngine) {
        this.lightEngine = lightEngine;
    }

    public void setBelowZeroRetrogen(@Nullable BelowZeroRetrogen belowZeroRetrogen) {
        this.belowZeroRetrogen = belowZeroRetrogen;
    }

    @Override
    public @Nullable BelowZeroRetrogen getBelowZeroRetrogen() {
        return this.belowZeroRetrogen;
    }

    private static <T> LevelChunkTicks<T> unpackTicks(ProtoChunkTicks<T> ticks) {
        return new LevelChunkTicks<T>(ticks.scheduledTicks());
    }

    public LevelChunkTicks<Block> unpackBlockTicks() {
        return unpackTicks(this.blockTicks);
    }

    public LevelChunkTicks<Fluid> unpackFluidTicks() {
        return unpackTicks(this.fluidTicks);
    }

    @Override
    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return (LevelHeightAccessor) (this.isUpgrading() ? BelowZeroRetrogen.UPGRADE_HEIGHT_ACCESSOR : this);
    }
}
