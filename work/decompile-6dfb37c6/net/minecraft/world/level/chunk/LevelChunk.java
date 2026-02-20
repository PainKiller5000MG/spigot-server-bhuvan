package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class LevelChunk extends ChunkAccess implements DebugValueSource {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
        @Override
        public void tick() {}

        @Override
        public boolean isRemoved() {
            return true;
        }

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public String getType() {
            return "<null>";
        }
    };
    private final Map<BlockPos, LevelChunk.RebindableTickingBlockEntityWrapper> tickersInLevel;
    public boolean loaded;
    public final Level level;
    private @Nullable Supplier<FullChunkStatus> fullStatus;
    private LevelChunk.@Nullable PostLoadProcessor postLoad;
    private final Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;
    private final LevelChunkTicks<Block> blockTicks;
    private final LevelChunkTicks<Fluid> fluidTicks;
    private LevelChunk.UnsavedListener unsavedListener;

    public LevelChunk(Level level, ChunkPos pos) {
        this(level, pos, UpgradeData.EMPTY, new LevelChunkTicks(), new LevelChunkTicks(), 0L, (LevelChunkSection[]) null, (LevelChunk.PostLoadProcessor) null, (BlendingData) null);
    }

    public LevelChunk(Level level, ChunkPos pos, UpgradeData upgradeData, LevelChunkTicks<Block> blockTicks, LevelChunkTicks<Fluid> fluidTicks, long inhabitedTime, LevelChunkSection @Nullable [] sections, LevelChunk.@Nullable PostLoadProcessor postLoad, @Nullable BlendingData blendingData) {
        super(pos, upgradeData, level, level.palettedContainerFactory(), inhabitedTime, sections, blendingData);
        this.tickersInLevel = Maps.newHashMap();
        this.unsavedListener = (chunkpos1) -> {
        };
        this.level = level;
        this.gameEventListenerRegistrySections = new Int2ObjectOpenHashMap();

        for (Heightmap.Types heightmap_types : Heightmap.Types.values()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(heightmap_types)) {
                this.heightmaps.put(heightmap_types, new Heightmap(this, heightmap_types));
            }
        }

        this.postLoad = postLoad;
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
    }

    public LevelChunk(ServerLevel level, ProtoChunk protoChunk, LevelChunk.@Nullable PostLoadProcessor postLoad) {
        this(level, protoChunk.getPos(), protoChunk.getUpgradeData(), protoChunk.unpackBlockTicks(), protoChunk.unpackFluidTicks(), protoChunk.getInhabitedTime(), protoChunk.getSections(), postLoad, protoChunk.getBlendingData());
        if (!Collections.disjoint(protoChunk.pendingBlockEntities.keySet(), protoChunk.blockEntities.keySet())) {
            LevelChunk.LOGGER.error("Chunk at {} contains duplicated block entities", protoChunk.getPos());
        }

        for (BlockEntity blockentity : protoChunk.getBlockEntities().values()) {
            this.setBlockEntity(blockentity);
        }

        this.pendingBlockEntities.putAll(protoChunk.getBlockEntityNbts());

        for (int i = 0; i < protoChunk.getPostProcessing().length; ++i) {
            this.postProcessing[i] = protoChunk.getPostProcessing()[i];
        }

        this.setAllStarts(protoChunk.getAllStarts());
        this.setAllReferences(protoChunk.getAllReferences());

        for (Map.Entry<Heightmap.Types, Heightmap> map_entry : protoChunk.getHeightmaps()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(map_entry.getKey())) {
                this.setHeightmap((Heightmap.Types) map_entry.getKey(), ((Heightmap) map_entry.getValue()).getRawData());
            }
        }

        this.skyLightSources = protoChunk.skyLightSources;
        this.setLightCorrect(protoChunk.isLightCorrect());
        this.markUnsaved();
    }

    public void setUnsavedListener(LevelChunk.UnsavedListener unsavedListener) {
        this.unsavedListener = unsavedListener;
        if (this.isUnsaved()) {
            unsavedListener.setUnsaved(this.chunkPos);
        }

    }

    @Override
    public void markUnsaved() {
        boolean flag = this.isUnsaved();

        super.markUnsaved();
        if (!flag) {
            this.unsavedListener.setUnsaved(this.chunkPos);
        }

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
    public GameEventListenerRegistry getListenerRegistry(int section) {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            return (GameEventListenerRegistry) this.gameEventListenerRegistrySections.computeIfAbsent(section, (j) -> {
                return new EuclideanGameEventListenerRegistry(serverlevel, section, this::removeGameEventListenerRegistry);
            });
        } else {
            return super.getListenerRegistry(section);
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        if (this.level.isDebug()) {
            BlockState blockstate = null;

            if (j == 60) {
                blockstate = Blocks.BARRIER.defaultBlockState();
            }

            if (j == 70) {
                blockstate = DebugLevelSource.getBlockStateFor(i, k);
            }

            return blockstate == null ? Blocks.AIR.defaultBlockState() : blockstate;
        } else {
            try {
                int l = this.getSectionIndex(j);

                if (l >= 0 && l < this.sections.length) {
                    LevelChunkSection levelchunksection = this.sections[l];

                    if (!levelchunksection.hasOnlyAir()) {
                        return levelchunksection.getBlockState(i & 15, j & 15, k & 15);
                    }
                }

                return Blocks.AIR.defaultBlockState();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting block state");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Block being got");

                crashreportcategory.setDetail("Location", () -> {
                    return CrashReportCategory.formatLocation(this, i, j, k);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }

    public FluidState getFluidState(int x, int y, int z) {
        try {
            int l = this.getSectionIndex(y);

            if (l >= 0 && l < this.sections.length) {
                LevelChunkSection levelchunksection = this.sections[l];

                if (!levelchunksection.hasOnlyAir()) {
                    return levelchunksection.getFluidState(x & 15, y & 15, z & 15);
                }
            }

            return Fluids.EMPTY.defaultFluidState();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting fluid state");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being got");

            crashreportcategory.setDetail("Location", () -> {
                return CrashReportCategory.formatLocation(this, x, y, z);
            });
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state, @Block.UpdateFlags int flags) {
        int j = pos.getY();
        LevelChunkSection levelchunksection = this.getSection(this.getSectionIndex(j));
        boolean flag = levelchunksection.hasOnlyAir();

        if (flag && state.isAir()) {
            return null;
        } else {
            int k = pos.getX() & 15;
            int l = j & 15;
            int i1 = pos.getZ() & 15;
            BlockState blockstate1 = levelchunksection.setBlockState(k, l, i1, state);

            if (blockstate1 == state) {
                return null;
            } else {
                Block block = state.getBlock();

                ((Heightmap) this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING)).update(k, j, i1, state);
                ((Heightmap) this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)).update(k, j, i1, state);
                ((Heightmap) this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR)).update(k, j, i1, state);
                ((Heightmap) this.heightmaps.get(Heightmap.Types.WORLD_SURFACE)).update(k, j, i1, state);
                boolean flag1 = levelchunksection.hasOnlyAir();

                if (flag != flag1) {
                    this.level.getChunkSource().getLightEngine().updateSectionStatus(pos, flag1);
                    this.level.getChunkSource().onSectionEmptinessChanged(this.chunkPos.x, SectionPos.blockToSectionCoord(j), this.chunkPos.z, flag1);
                }

                if (LightEngine.hasDifferentLightProperties(blockstate1, state)) {
                    ProfilerFiller profilerfiller = Profiler.get();

                    profilerfiller.push("updateSkyLightSources");
                    this.skyLightSources.update(this, k, j, i1);
                    profilerfiller.popPush("queueCheckLight");
                    this.level.getChunkSource().getLightEngine().checkBlock(pos);
                    profilerfiller.pop();
                }

                boolean flag2 = !blockstate1.is(block);
                boolean flag3 = (flags & 64) != 0;
                boolean flag4 = (flags & 256) == 0;

                if (flag2 && blockstate1.hasBlockEntity() && !state.shouldChangedStateKeepBlockEntity(blockstate1)) {
                    if (!this.level.isClientSide() && flag4) {
                        BlockEntity blockentity = this.level.getBlockEntity(pos);

                        if (blockentity != null) {
                            blockentity.preRemoveSideEffects(pos, blockstate1);
                        }
                    }

                    this.removeBlockEntity(pos);
                }

                if (flag2 || block instanceof BaseRailBlock) {
                    Level level = this.level;

                    if (level instanceof ServerLevel) {
                        ServerLevel serverlevel = (ServerLevel) level;

                        if ((flags & 1) != 0 || flag3) {
                            blockstate1.affectNeighborsAfterRemoval(serverlevel, pos, flag3);
                        }
                    }
                }

                if (!levelchunksection.getBlockState(k, l, i1).is(block)) {
                    return null;
                } else {
                    if (!this.level.isClientSide() && (flags & 512) == 0) {
                        state.onPlace(this.level, pos, blockstate1, flag3);
                    }

                    if (state.hasBlockEntity()) {
                        BlockEntity blockentity1 = this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);

                        if (blockentity1 != null && !blockentity1.isValidBlockState(state)) {
                            LevelChunk.LOGGER.warn("Found mismatched block entity @ {}: type = {}, state = {}", new Object[]{pos, blockentity1.getType().builtInRegistryHolder().key().identifier(), state});
                            this.removeBlockEntity(pos);
                            blockentity1 = null;
                        }

                        if (blockentity1 == null) {
                            blockentity1 = ((EntityBlock) block).newBlockEntity(pos, state);
                            if (blockentity1 != null) {
                                this.addAndRegisterBlockEntity(blockentity1);
                            }
                        } else {
                            blockentity1.setBlockState(state);
                            this.updateBlockEntityTicker(blockentity1);
                        }
                    }

                    this.markUnsaved();
                    return blockstate1;
                }
            }
        }
    }

    /** @deprecated */
    @Deprecated
    @Override
    public void addEntity(Entity entity) {}

    private @Nullable BlockEntity createBlockEntity(BlockPos pos) {
        BlockState blockstate = this.getBlockState(pos);

        return !blockstate.hasBlockEntity() ? null : ((EntityBlock) blockstate.getBlock()).newBlockEntity(pos, blockstate);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    public @Nullable BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType) {
        BlockEntity blockentity = (BlockEntity) this.blockEntities.get(pos);

        if (blockentity == null) {
            CompoundTag compoundtag = (CompoundTag) this.pendingBlockEntities.remove(pos);

            if (compoundtag != null) {
                BlockEntity blockentity1 = this.promotePendingBlockEntity(pos, compoundtag);

                if (blockentity1 != null) {
                    return blockentity1;
                }
            }
        }

        if (blockentity == null) {
            if (creationType == LevelChunk.EntityCreationType.IMMEDIATE) {
                blockentity = this.createBlockEntity(pos);
                if (blockentity != null) {
                    this.addAndRegisterBlockEntity(blockentity);
                }
            }
        } else if (blockentity.isRemoved()) {
            this.blockEntities.remove(pos);
            return null;
        }

        return blockentity;
    }

    public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
        this.setBlockEntity(blockEntity);
        if (this.isInLevel()) {
            Level level = this.level;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.addGameEventListener(blockEntity, serverlevel);
            }

            this.level.onBlockEntityAdded(blockEntity);
            this.updateBlockEntityTicker(blockEntity);
        }

    }

    private boolean isInLevel() {
        return this.loaded || this.level.isClientSide();
    }

    private boolean isTicking(BlockPos pos) {
        if (!this.level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        } else {
            Level level = this.level;

            if (!(level instanceof ServerLevel)) {
                return true;
            } else {
                ServerLevel serverlevel = (ServerLevel) level;

                return this.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && serverlevel.areEntitiesLoaded(ChunkPos.asLong(pos));
            }
        }
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockpos = blockEntity.getBlockPos();
        BlockState blockstate = this.getBlockState(blockpos);

        if (!blockstate.hasBlockEntity()) {
            LevelChunk.LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", new Object[]{blockEntity, blockpos, blockstate});
        } else {
            BlockState blockstate1 = blockEntity.getBlockState();

            if (blockstate != blockstate1) {
                if (!blockEntity.getType().isValid(blockstate)) {
                    LevelChunk.LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", new Object[]{blockEntity, blockpos, blockstate});
                    return;
                }

                if (blockstate.getBlock() != blockstate1.getBlock()) {
                    LevelChunk.LOGGER.warn("Block state mismatch on block entity {} in position {}, {} != {}, updating", new Object[]{blockEntity, blockpos, blockstate, blockstate1});
                }

                blockEntity.setBlockState(blockstate);
            }

            blockEntity.setLevel(this.level);
            blockEntity.clearRemoved();
            BlockEntity blockentity1 = (BlockEntity) this.blockEntities.put(blockpos.immutable(), blockEntity);

            if (blockentity1 != null && blockentity1 != blockEntity) {
                blockentity1.setRemoved();
            }

        }
    }

    @Override
    public @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, HolderLookup.Provider registryAccess) {
        BlockEntity blockentity = this.getBlockEntity(blockPos);

        if (blockentity != null && !blockentity.isRemoved()) {
            CompoundTag compoundtag = blockentity.saveWithFullMetadata((HolderLookup.Provider) this.level.registryAccess());

            compoundtag.putBoolean("keepPacked", false);
            return compoundtag;
        } else {
            CompoundTag compoundtag1 = (CompoundTag) this.pendingBlockEntities.get(blockPos);

            if (compoundtag1 != null) {
                compoundtag1 = compoundtag1.copy();
                compoundtag1.putBoolean("keepPacked", true);
            }

            return compoundtag1;
        }
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        if (this.isInLevel()) {
            BlockEntity blockentity = (BlockEntity) this.blockEntities.remove(pos);

            if (blockentity != null) {
                Level level = this.level;

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    this.removeGameEventListener(blockentity, serverlevel);
                    serverlevel.debugSynchronizers().dropBlockEntity(pos);
                }

                blockentity.setRemoved();
            }
        }

        this.removeBlockEntityTicker(pos);
    }

    private <T extends BlockEntity> void removeGameEventListener(T blockEntity, ServerLevel level) {
        Block block = blockEntity.getBlockState().getBlock();

        if (block instanceof EntityBlock) {
            GameEventListener gameeventlistener = ((EntityBlock) block).getListener(level, blockEntity);

            if (gameeventlistener != null) {
                int i = SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY());
                GameEventListenerRegistry gameeventlistenerregistry = this.getListenerRegistry(i);

                gameeventlistenerregistry.unregister(gameeventlistener);
            }
        }

    }

    private void removeGameEventListenerRegistry(int sectionY) {
        this.gameEventListenerRegistrySections.remove(sectionY);
    }

    private void removeBlockEntityTicker(BlockPos pos) {
        LevelChunk.RebindableTickingBlockEntityWrapper levelchunk_rebindabletickingblockentitywrapper = (LevelChunk.RebindableTickingBlockEntityWrapper) this.tickersInLevel.remove(pos);

        if (levelchunk_rebindabletickingblockentitywrapper != null) {
            levelchunk_rebindabletickingblockentitywrapper.rebind(LevelChunk.NULL_TICKER);
        }

    }

    public void runPostLoad() {
        if (this.postLoad != null) {
            this.postLoad.run(this);
            this.postLoad = null;
        }

    }

    public boolean isEmpty() {
        return false;
    }

    public void replaceWithPacketData(FriendlyByteBuf buffer, Map<Heightmap.Types, long[]> heightmaps, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities) {
        this.clearAllBlockEntities();

        for (LevelChunkSection levelchunksection : this.sections) {
            levelchunksection.read(buffer);
        }

        heightmaps.forEach(this::setHeightmap);
        this.initializeLightSources();

        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), LevelChunk.LOGGER)) {
            blockEntities.accept((ClientboundLevelChunkPacketData.BlockEntityTagOutput) (blockpos, blockentitytype, compoundtag) -> {
                BlockEntity blockentity = this.getBlockEntity(blockpos, LevelChunk.EntityCreationType.IMMEDIATE);

                if (blockentity != null && compoundtag != null && blockentity.getType() == blockentitytype) {
                    blockentity.loadWithComponents(TagValueInput.create(problemreporter_scopedcollector.forChild(blockentity.problemPath()), this.level.registryAccess(), compoundtag));
                }

            });
        }

    }

    public void replaceBiomes(FriendlyByteBuf buffer) {
        for (LevelChunkSection levelchunksection : this.sections) {
            levelchunksection.readBiomes(buffer);
        }

    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public Level getLevel() {
        return this.level;
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void postProcessGeneration(ServerLevel level) {
        ChunkPos chunkpos = this.getPos();

        for (int i = 0; i < this.postProcessing.length; ++i) {
            ShortList shortlist = this.postProcessing[i];

            if (shortlist != null) {
                ShortListIterator shortlistiterator = shortlist.iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();
                    BlockPos blockpos = ProtoChunk.unpackOffsetCoordinates(oshort, this.getSectionYFromSectionIndex(i), chunkpos);
                    BlockState blockstate = this.getBlockState(blockpos);
                    FluidState fluidstate = blockstate.getFluidState();

                    if (!fluidstate.isEmpty()) {
                        fluidstate.tick(level, blockpos, blockstate);
                    }

                    if (!(blockstate.getBlock() instanceof LiquidBlock)) {
                        BlockState blockstate1 = Block.updateFromNeighbourShapes(blockstate, level, blockpos);

                        if (blockstate1 != blockstate) {
                            level.setBlock(blockpos, blockstate1, 276);
                        }
                    }
                }

                shortlist.clear();
            }
        }

        UnmodifiableIterator unmodifiableiterator = ImmutableList.copyOf(this.pendingBlockEntities.keySet()).iterator();

        while (unmodifiableiterator.hasNext()) {
            BlockPos blockpos1 = (BlockPos) unmodifiableiterator.next();

            this.getBlockEntity(blockpos1);
        }

        this.pendingBlockEntities.clear();
        this.upgradeData.upgrade(this);
    }

    private @Nullable BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag tag) {
        BlockState blockstate = this.getBlockState(pos);
        BlockEntity blockentity;

        if ("DUMMY".equals(tag.getStringOr("id", ""))) {
            if (blockstate.hasBlockEntity()) {
                blockentity = ((EntityBlock) blockstate.getBlock()).newBlockEntity(pos, blockstate);
            } else {
                blockentity = null;
                LevelChunk.LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", pos, blockstate);
            }
        } else {
            blockentity = BlockEntity.loadStatic(pos, blockstate, tag, this.level.registryAccess());
        }

        if (blockentity != null) {
            blockentity.setLevel(this.level);
            this.addAndRegisterBlockEntity(blockentity);
        } else {
            LevelChunk.LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", blockstate, pos);
        }

        return blockentity;
    }

    public void unpackTicks(long currentTick) {
        this.blockTicks.unpack(currentTick);
        this.fluidTicks.unpack(currentTick);
    }

    public void registerTickContainerInLevel(ServerLevel level) {
        level.getBlockTicks().addContainer(this.chunkPos, this.blockTicks);
        level.getFluidTicks().addContainer(this.chunkPos, this.fluidTicks);
    }

    public void unregisterTickContainerFromLevel(ServerLevel level) {
        level.getBlockTicks().removeContainer(this.chunkPos);
        level.getFluidTicks().removeContainer(this.chunkPos);
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registration) {
        if (!this.getAllStarts().isEmpty()) {
            registration.register(DebugSubscriptions.STRUCTURES, () -> {
                List<DebugStructureInfo> list = new ArrayList();

                for (StructureStart structurestart : this.getAllStarts().values()) {
                    BoundingBox boundingbox = structurestart.getBoundingBox();
                    List<StructurePiece> list1 = structurestart.getPieces();
                    List<DebugStructureInfo.Piece> list2 = new ArrayList(list1.size());

                    for (int i = 0; i < list1.size(); ++i) {
                        boolean flag = i == 0;

                        list2.add(new DebugStructureInfo.Piece(((StructurePiece) list1.get(i)).getBoundingBox(), flag));
                    }

                    list.add(new DebugStructureInfo(boundingbox, list2));
                }

                return list;
            });
        }

        registration.register(DebugSubscriptions.RAIDS, () -> {
            return level.getRaids().getRaidCentersInChunk(this.chunkPos);
        });
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ChunkStatus.FULL;
    }

    public FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? FullChunkStatus.FULL : (FullChunkStatus) this.fullStatus.get();
    }

    public void setFullStatus(Supplier<FullChunkStatus> fullStatus) {
        this.fullStatus = fullStatus;
    }

    public void clearAllBlockEntities() {
        this.blockEntities.values().forEach(BlockEntity::setRemoved);
        this.blockEntities.clear();
        this.tickersInLevel.values().forEach((levelchunk_rebindabletickingblockentitywrapper) -> {
            levelchunk_rebindabletickingblockentitywrapper.rebind(LevelChunk.NULL_TICKER);
        });
        this.tickersInLevel.clear();
    }

    public void registerAllBlockEntitiesAfterLevelLoad() {
        this.blockEntities.values().forEach((blockentity) -> {
            Level level = this.level;

            if (level instanceof ServerLevel serverlevel) {
                this.addGameEventListener(blockentity, serverlevel);
            }

            this.level.onBlockEntityAdded(blockentity);
            this.updateBlockEntityTicker(blockentity);
        });
    }

    private <T extends BlockEntity> void addGameEventListener(T blockEntity, ServerLevel level) {
        Block block = blockEntity.getBlockState().getBlock();

        if (block instanceof EntityBlock) {
            GameEventListener gameeventlistener = ((EntityBlock) block).getListener(level, blockEntity);

            if (gameeventlistener != null) {
                this.getListenerRegistry(SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY())).register(gameeventlistener);
            }
        }

    }

    private <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity) {
        BlockState blockstate = blockEntity.getBlockState();
        BlockEntityTicker<T> blockentityticker = blockstate.<T>getTicker(this.level, ((BlockEntity) blockEntity).getType());

        if (blockentityticker == null) {
            this.removeBlockEntityTicker(blockEntity.getBlockPos());
        } else {
            this.tickersInLevel.compute(blockEntity.getBlockPos(), (blockpos, levelchunk_rebindabletickingblockentitywrapper) -> {
                TickingBlockEntity tickingblockentity = this.createTicker(blockEntity, blockentityticker);

                if (levelchunk_rebindabletickingblockentitywrapper != null) {
                    levelchunk_rebindabletickingblockentitywrapper.rebind(tickingblockentity);
                    return levelchunk_rebindabletickingblockentitywrapper;
                } else if (this.isInLevel()) {
                    LevelChunk.RebindableTickingBlockEntityWrapper levelchunk_rebindabletickingblockentitywrapper1 = new LevelChunk.RebindableTickingBlockEntityWrapper(tickingblockentity);

                    this.level.addBlockEntityTicker(levelchunk_rebindabletickingblockentitywrapper1);
                    return levelchunk_rebindabletickingblockentitywrapper1;
                } else {
                    return null;
                }
            });
        }

    }

    private <T extends BlockEntity> TickingBlockEntity createTicker(T blockEntity, BlockEntityTicker<T> ticker) {
        return new LevelChunk.BoundTickingBlockEntity(blockEntity, ticker);
    }

    public static enum EntityCreationType {

        IMMEDIATE, QUEUED, CHECK;

        private EntityCreationType() {}
    }

    private class BoundTickingBlockEntity<T extends BlockEntity> implements TickingBlockEntity {

        private final T blockEntity;
        private final BlockEntityTicker<T> ticker;
        private boolean loggedInvalidBlockState;

        private BoundTickingBlockEntity(T blockEntity, BlockEntityTicker<T> ticker) {
            this.blockEntity = blockEntity;
            this.ticker = ticker;
        }

        @Override
        public void tick() {
            if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
                BlockPos blockpos = this.blockEntity.getBlockPos();

                if (LevelChunk.this.isTicking(blockpos)) {
                    try {
                        ProfilerFiller profilerfiller = Profiler.get();

                        profilerfiller.push(this::getType);
                        BlockState blockstate = LevelChunk.this.getBlockState(blockpos);

                        if (this.blockEntity.getType().isValid(blockstate)) {
                            this.ticker.tick(LevelChunk.this.level, this.blockEntity.getBlockPos(), blockstate, this.blockEntity);
                            this.loggedInvalidBlockState = false;
                        } else if (!this.loggedInvalidBlockState) {
                            this.loggedInvalidBlockState = true;
                            LevelChunk.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", new Object[]{LogUtils.defer(this::getType), LogUtils.defer(this::getPos), blockstate});
                        }

                        profilerfiller.pop();
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking block entity");
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Block entity being ticked");

                        this.blockEntity.fillCrashReportCategory(crashreportcategory);
                        throw new ReportedException(crashreport);
                    }
                }
            }

        }

        @Override
        public boolean isRemoved() {
            return this.blockEntity.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return this.blockEntity.getBlockPos();
        }

        @Override
        public String getType() {
            return BlockEntityType.getKey(this.blockEntity.getType()).toString();
        }

        public String toString() {
            String s = this.getType();

            return "Level ticker for " + s + "@" + String.valueOf(this.getPos());
        }
    }

    private static class RebindableTickingBlockEntityWrapper implements TickingBlockEntity {

        private TickingBlockEntity ticker;

        private RebindableTickingBlockEntityWrapper(TickingBlockEntity ticker) {
            this.ticker = ticker;
        }

        private void rebind(TickingBlockEntity ticker) {
            this.ticker = ticker;
        }

        @Override
        public void tick() {
            this.ticker.tick();
        }

        @Override
        public boolean isRemoved() {
            return this.ticker.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return this.ticker.getPos();
        }

        @Override
        public String getType() {
            return this.ticker.getType();
        }

        public String toString() {
            return String.valueOf(this.ticker) + " <wrapped>";
        }
    }

    @FunctionalInterface
    public interface PostLoadProcessor {

        void run(LevelChunk levelChunk);
    }

    @FunctionalInterface
    public interface UnsavedListener {

        void setUnsaved(ChunkPos chunkPos);
    }
}
