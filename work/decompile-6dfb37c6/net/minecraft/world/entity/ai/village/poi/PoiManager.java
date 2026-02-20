package net.minecraft.world.entity.ai.village.poi;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.jspecify.annotations.Nullable;

public class PoiManager extends SectionStorage<PoiSection, PoiSection.Packed> {

    public static final int MAX_VILLAGE_DISTANCE = 6;
    public static final int VILLAGE_SECTION_SIZE = 1;
    private final PoiManager.DistanceTracker distanceTracker = new PoiManager.DistanceTracker();
    private final LongSet loadedChunks = new LongOpenHashSet();

    public PoiManager(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, RegistryAccess registryAccess, ChunkIOErrorReporter errorReporter, LevelHeightAccessor levelHeightAccessor) {
        super(new SimpleRegionStorage(info, folder, fixerUpper, sync, DataFixTypes.POI_CHUNK), PoiSection.Packed.CODEC, PoiSection::pack, PoiSection.Packed::unpack, PoiSection::new, registryAccess, errorReporter, levelHeightAccessor);
    }

    public @Nullable PoiRecord add(BlockPos pos, Holder<PoiType> type) {
        return ((PoiSection) this.getOrCreate(SectionPos.asLong(pos))).add(pos, type);
    }

    public void remove(BlockPos pos) {
        this.getOrLoad(SectionPos.asLong(pos)).ifPresent((poisection) -> {
            poisection.remove(pos);
        });
    }

    public long getCountInRange(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.getInRange(predicate, center, radius, occupancy).count();
    }

    public boolean existsAtPosition(ResourceKey<PoiType> poiType, BlockPos blockPos) {
        return this.exists(blockPos, (holder) -> {
            return holder.is(poiType);
        });
    }

    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        int j = Math.floorDiv(radius, 16) + 1;

        return ChunkPos.rangeClosed(new ChunkPos(center), j).flatMap((chunkpos) -> {
            return this.getInChunk(predicate, chunkpos, occupancy);
        }).filter((poirecord) -> {
            BlockPos blockpos1 = poirecord.getPos();

            return Math.abs(blockpos1.getX() - center.getX()) <= radius && Math.abs(blockpos1.getZ() - center.getZ()) <= radius;
        });
    }

    public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        int j = radius * radius;

        return this.getInSquare(predicate, center, radius, occupancy).filter((poirecord) -> {
            return poirecord.getPos().distSqr(center) <= (double) j;
        });
    }

    @VisibleForDebug
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> predicate, ChunkPos chunkPos, PoiManager.Occupancy occupancy) {
        return IntStream.rangeClosed(this.levelHeightAccessor.getMinSectionY(), this.levelHeightAccessor.getMaxSectionY()).boxed().map((integer) -> {
            return this.getOrLoad(SectionPos.of(chunkPos, integer).asLong());
        }).filter(Optional::isPresent).flatMap((optional) -> {
            return ((PoiSection) optional.get()).getRecords(predicate, occupancy);
        });
    }

    public Stream<BlockPos> findAll(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.getInRange(predicate, center, radius, occupancy).map(PoiRecord::getPos).filter(filter);
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.getInRange(predicate, center, radius, occupancy).filter((poirecord) -> {
            return filter.test(poirecord.getPos());
        }).map((poirecord) -> {
            return Pair.of(poirecord.getPoiType(), poirecord.getPos());
        });
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.findAllWithType(predicate, filter, center, radius, occupancy).sorted(Comparator.comparingDouble((pair) -> {
            return ((BlockPos) pair.getSecond()).distSqr(center);
        }));
    }

    public Optional<BlockPos> find(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.findAll(predicate, filter, center, radius, occupancy).findFirst();
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.getInRange(predicate, center, radius, occupancy).map(PoiRecord::getPos).min(Comparator.comparingDouble((blockpos1) -> {
            return blockpos1.distSqr(center);
        }));
    }

    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.getInRange(predicate, center, radius, occupancy).min(Comparator.comparingDouble((poirecord) -> {
            return poirecord.getPos().distSqr(center);
        })).map((poirecord) -> {
            return Pair.of(poirecord.getPoiType(), poirecord.getPos());
        });
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        return this.getInRange(predicate, center, radius, occupancy).map(PoiRecord::getPos).filter(filter).min(Comparator.comparingDouble((blockpos1) -> {
            return blockpos1.distSqr(center);
        }));
    }

    public Optional<BlockPos> take(Predicate<Holder<PoiType>> predicate, BiPredicate<Holder<PoiType>, BlockPos> filter, BlockPos center, int radius) {
        return this.getInRange(predicate, center, radius, PoiManager.Occupancy.HAS_SPACE).filter((poirecord) -> {
            return filter.test(poirecord.getPoiType(), poirecord.getPos());
        }).findFirst().map((poirecord) -> {
            poirecord.acquireTicket();
            return poirecord.getPos();
        });
    }

    public Optional<BlockPos> getRandom(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, PoiManager.Occupancy occupancy, BlockPos center, int radius, RandomSource random) {
        List<PoiRecord> list = Util.toShuffledList(this.getInRange(predicate, center, radius, occupancy), random);

        return list.stream().filter((poirecord) -> {
            return filter.test(poirecord.getPos());
        }).findFirst().map(PoiRecord::getPos);
    }

    public boolean release(BlockPos pos) {
        return (Boolean) this.getOrLoad(SectionPos.asLong(pos)).map((poisection) -> {
            return poisection.release(pos);
        }).orElseThrow(() -> {
            return (IllegalStateException) Util.pauseInIde(new IllegalStateException("POI never registered at " + String.valueOf(pos)));
        });
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> predicate) {
        return (Boolean) this.getOrLoad(SectionPos.asLong(pos)).map((poisection) -> {
            return poisection.exists(pos, predicate);
        }).orElse(false);
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).flatMap((poisection) -> {
            return poisection.getType(pos);
        });
    }

    @VisibleForDebug
    public @Nullable DebugPoiInfo getDebugPoiInfo(BlockPos pos) {
        return (DebugPoiInfo) this.getOrLoad(SectionPos.asLong(pos)).flatMap((poisection) -> {
            return poisection.getDebugPoiInfo(pos);
        }).orElse((Object) null);
    }

    public int sectionsToVillage(SectionPos sectionPos) {
        this.distanceTracker.runAllUpdates();
        return this.distanceTracker.getLevel(sectionPos.asLong());
    }

    private boolean isVillageCenter(long sectionPos) {
        Optional<PoiSection> optional = this.get(sectionPos);

        return optional == null ? false : (Boolean) optional.map((poisection) -> {
            return poisection.getRecords((holder) -> {
                return holder.is(PoiTypeTags.VILLAGE);
            }, PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent();
        }).orElse(false);
    }

    @Override
    public void tick(BooleanSupplier haveTime) {
        super.tick(haveTime);
        this.distanceTracker.runAllUpdates();
    }

    @Override
    protected void setDirty(long sectionPos) {
        super.setDirty(sectionPos);
        this.distanceTracker.update(sectionPos, this.distanceTracker.getLevelFromSource(sectionPos), false);
    }

    @Override
    protected void onSectionLoad(long sectionPos) {
        this.distanceTracker.update(sectionPos, this.distanceTracker.getLevelFromSource(sectionPos), false);
    }

    public void checkConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection blockSection) {
        Util.ifElse(this.getOrLoad(sectionPos.asLong()), (poisection) -> {
            poisection.refresh((biconsumer) -> {
                if (mayHavePoi(blockSection)) {
                    this.updateFromSection(blockSection, sectionPos, biconsumer);
                }

            });
        }, () -> {
            if (mayHavePoi(blockSection)) {
                PoiSection poisection = (PoiSection) this.getOrCreate(sectionPos.asLong());

                Objects.requireNonNull(poisection);
                this.updateFromSection(blockSection, sectionPos, poisection::add);
            }

        });
    }

    private static boolean mayHavePoi(LevelChunkSection blockSection) {
        return blockSection.maybeHas(PoiTypes::hasPoi);
    }

    private void updateFromSection(LevelChunkSection blockSection, SectionPos pos, BiConsumer<BlockPos, Holder<PoiType>> output) {
        pos.blocksInside().forEach((blockpos) -> {
            BlockState blockstate = blockSection.getBlockState(SectionPos.sectionRelative(blockpos.getX()), SectionPos.sectionRelative(blockpos.getY()), SectionPos.sectionRelative(blockpos.getZ()));

            PoiTypes.forState(blockstate).ifPresent((holder) -> {
                output.accept(blockpos, holder);
            });
        });
    }

    public void ensureLoadedAndValid(LevelReader reader, BlockPos center, int radius) {
        SectionPos.aroundChunk(new ChunkPos(center), Math.floorDiv(radius, 16), this.levelHeightAccessor.getMinSectionY(), this.levelHeightAccessor.getMaxSectionY()).map((sectionpos) -> {
            return Pair.of(sectionpos, this.getOrLoad(sectionpos.asLong()));
        }).filter((pair) -> {
            return !(Boolean) ((Optional) pair.getSecond()).map(PoiSection::isValid).orElse(false);
        }).map((pair) -> {
            return ((SectionPos) pair.getFirst()).chunk();
        }).filter((chunkpos) -> {
            return this.loadedChunks.add(chunkpos.toLong());
        }).forEach((chunkpos) -> {
            reader.getChunk(chunkpos.x, chunkpos.z, ChunkStatus.EMPTY);
        });
    }

    public static enum Occupancy {

        HAS_SPACE(PoiRecord::hasSpace), IS_OCCUPIED(PoiRecord::isOccupied), ANY((poirecord) -> {
            return true;
        });

        private final Predicate<? super PoiRecord> test;

        private Occupancy(Predicate<? super PoiRecord> test) {
            this.test = test;
        }

        public Predicate<? super PoiRecord> getTest() {
            return this.test;
        }
    }

    private final class DistanceTracker extends SectionTracker {

        private final Long2ByteMap levels = new Long2ByteOpenHashMap();

        protected DistanceTracker() {
            super(7, 16, 256);
            this.levels.defaultReturnValue((byte) 7);
        }

        @Override
        protected int getLevelFromSource(long to) {
            return PoiManager.this.isVillageCenter(to) ? 0 : 7;
        }

        @Override
        protected int getLevel(long node) {
            return this.levels.get(node);
        }

        @Override
        protected void setLevel(long node, int level) {
            if (level > 6) {
                this.levels.remove(node);
            } else {
                this.levels.put(node, (byte) level);
            }

        }

        public void runAllUpdates() {
            super.runUpdates(Integer.MAX_VALUE);
        }
    }
}
