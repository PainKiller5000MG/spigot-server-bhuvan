package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SectionStorage<R, P> implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    private final SimpleRegionStorage simpleRegionStorage;
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectOpenHashMap();
    private final LongLinkedOpenHashSet dirtyChunks = new LongLinkedOpenHashSet();
    private final Codec<P> codec;
    private final Function<R, P> packer;
    private final BiFunction<P, Runnable, R> unpacker;
    private final Function<Runnable, R> factory;
    private final RegistryAccess registryAccess;
    private final ChunkIOErrorReporter errorReporter;
    protected final LevelHeightAccessor levelHeightAccessor;
    private final LongSet loadedChunks = new LongOpenHashSet();
    private final Long2ObjectMap<CompletableFuture<Optional<SectionStorage.PackedChunk<P>>>> pendingLoads = new Long2ObjectOpenHashMap();
    private final Object loadLock = new Object();

    public SectionStorage(SimpleRegionStorage simpleRegionStorage, Codec<P> codec, Function<R, P> packer, BiFunction<P, Runnable, R> unpacker, Function<Runnable, R> factory, RegistryAccess registryAccess, ChunkIOErrorReporter errorReporter, LevelHeightAccessor levelHeightAccessor) {
        this.simpleRegionStorage = simpleRegionStorage;
        this.codec = codec;
        this.packer = packer;
        this.unpacker = unpacker;
        this.factory = factory;
        this.registryAccess = registryAccess;
        this.errorReporter = errorReporter;
        this.levelHeightAccessor = levelHeightAccessor;
    }

    protected void tick(BooleanSupplier haveTime) {
        LongIterator longiterator = this.dirtyChunks.iterator();

        while (((LongIterator) longiterator).hasNext() && haveTime.getAsBoolean()) {
            ChunkPos chunkpos = new ChunkPos(longiterator.nextLong());

            longiterator.remove();
            this.writeChunk(chunkpos);
        }

        this.unpackPendingLoads();
    }

    private void unpackPendingLoads() {
        synchronized (this.loadLock) {
            Iterator<Long2ObjectMap.Entry<CompletableFuture<Optional<SectionStorage.PackedChunk<P>>>>> iterator = Long2ObjectMaps.fastIterator(this.pendingLoads);

            while (((Iterator) iterator).hasNext()) {
                Long2ObjectMap.Entry<CompletableFuture<Optional<SectionStorage.PackedChunk<P>>>> long2objectmap_entry = (Entry) iterator.next();
                Optional<SectionStorage.PackedChunk<P>> optional = (Optional) ((CompletableFuture) long2objectmap_entry.getValue()).getNow((Object) null);

                if (optional != null) {
                    long i = long2objectmap_entry.getLongKey();

                    this.unpackChunk(new ChunkPos(i), (SectionStorage.PackedChunk) optional.orElse((Object) null));
                    iterator.remove();
                    this.loadedChunks.add(i);
                }
            }

        }
    }

    public void flushAll() {
        if (!this.dirtyChunks.isEmpty()) {
            this.dirtyChunks.forEach((i) -> {
                this.writeChunk(new ChunkPos(i));
            });
            this.dirtyChunks.clear();
        }

    }

    public boolean hasWork() {
        return !this.dirtyChunks.isEmpty();
    }

    protected @Nullable Optional<R> get(long sectionPos) {
        return (Optional) this.storage.get(sectionPos);
    }

    protected Optional<R> getOrLoad(long sectionPos) {
        if (this.outsideStoredRange(sectionPos)) {
            return Optional.empty();
        } else {
            Optional<R> optional = this.get(sectionPos);

            if (optional != null) {
                return optional;
            } else {
                this.unpackChunk(SectionPos.of(sectionPos).chunk());
                optional = this.get(sectionPos);
                if (optional == null) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException());
                } else {
                    return optional;
                }
            }
        }
    }

    protected boolean outsideStoredRange(long sectionPos) {
        int j = SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos));

        return this.levelHeightAccessor.isOutsideBuildHeight(j);
    }

    protected R getOrCreate(long sectionPos) {
        if (this.outsideStoredRange(sectionPos)) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException("sectionPos out of bounds"));
        } else {
            Optional<R> optional = this.getOrLoad(sectionPos);

            if (optional.isPresent()) {
                return (R) optional.get();
            } else {
                R r0 = (R) this.factory.apply((Runnable) () -> {
                    this.setDirty(sectionPos);
                });

                this.storage.put(sectionPos, Optional.of(r0));
                return r0;
            }
        }
    }

    public CompletableFuture<?> prefetch(ChunkPos chunkPos) {
        synchronized (this.loadLock) {
            long i = chunkPos.toLong();

            return this.loadedChunks.contains(i) ? CompletableFuture.completedFuture((Object) null) : (CompletableFuture) this.pendingLoads.computeIfAbsent(i, (j) -> {
                return this.tryRead(chunkPos);
            });
        }
    }

    private void unpackChunk(ChunkPos chunkPos) {
        long i = chunkPos.toLong();
        CompletableFuture<Optional<SectionStorage.PackedChunk<P>>> completablefuture;

        synchronized (this.loadLock) {
            if (!this.loadedChunks.add(i)) {
                return;
            }

            completablefuture = (CompletableFuture) this.pendingLoads.computeIfAbsent(i, (j) -> {
                return this.tryRead(chunkPos);
            });
        }

        this.unpackChunk(chunkPos, (SectionStorage.PackedChunk) ((Optional) completablefuture.join()).orElse((Object) null));
        synchronized (this.loadLock) {
            this.pendingLoads.remove(i);
        }
    }

    private CompletableFuture<Optional<SectionStorage.PackedChunk<P>>> tryRead(ChunkPos chunkPos) {
        RegistryOps<Tag> registryops = this.registryAccess.<Tag>createSerializationContext(NbtOps.INSTANCE);

        return this.simpleRegionStorage.read(chunkPos).thenApplyAsync((optional) -> {
            return optional.map((compoundtag) -> {
                return SectionStorage.PackedChunk.parse(this.codec, registryops, compoundtag, this.simpleRegionStorage, this.levelHeightAccessor);
            });
        }, Util.backgroundExecutor().forName("parseSection")).exceptionally((throwable) -> {
            if (throwable instanceof CompletionException) {
                throwable = throwable.getCause();
            }

            if (throwable instanceof IOException ioexception) {
                SectionStorage.LOGGER.error("Error reading chunk {} data from disk", chunkPos, ioexception);
                this.errorReporter.reportChunkLoadFailure(ioexception, this.simpleRegionStorage.storageInfo(), chunkPos);
                return Optional.empty();
            } else {
                throw new CompletionException(throwable);
            }
        });
    }

    private void unpackChunk(ChunkPos pos, SectionStorage.@Nullable PackedChunk<P> packedChunk) {
        if (packedChunk == null) {
            for (int i = this.levelHeightAccessor.getMinSectionY(); i <= this.levelHeightAccessor.getMaxSectionY(); ++i) {
                this.storage.put(getKey(pos, i), Optional.empty());
            }
        } else {
            boolean flag = packedChunk.versionChanged();

            for (int j = this.levelHeightAccessor.getMinSectionY(); j <= this.levelHeightAccessor.getMaxSectionY(); ++j) {
                long k = getKey(pos, j);
                Optional<R> optional = Optional.ofNullable(packedChunk.sectionsByY.get(j)).map((object) -> {
                    return this.unpacker.apply(object, (Runnable) () -> {
                        this.setDirty(k);
                    });
                });

                this.storage.put(k, optional);
                optional.ifPresent((object) -> {
                    this.onSectionLoad(k);
                    if (flag) {
                        this.setDirty(k);
                    }

                });
            }
        }

    }

    private void writeChunk(ChunkPos chunkPos) {
        RegistryOps<Tag> registryops = this.registryAccess.<Tag>createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> dynamic = this.<Tag>writeChunk(chunkPos, registryops);
        Tag tag = (Tag) dynamic.getValue();

        if (tag instanceof CompoundTag compoundtag) {
            this.simpleRegionStorage.write(chunkPos, compoundtag).exceptionally((throwable) -> {
                this.errorReporter.reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), chunkPos);
                return null;
            });
        } else {
            SectionStorage.LOGGER.error("Expected compound tag, got {}", tag);
        }

    }

    private <T> Dynamic<T> writeChunk(ChunkPos chunkPos, DynamicOps<T> ops) {
        Map<T, T> map = Maps.newHashMap();

        for (int i = this.levelHeightAccessor.getMinSectionY(); i <= this.levelHeightAccessor.getMaxSectionY(); ++i) {
            long j = getKey(chunkPos, i);
            Optional<R> optional = (Optional) this.storage.get(j);

            if (optional != null && !optional.isEmpty()) {
                DataResult<T> dataresult = this.codec.encodeStart(ops, this.packer.apply(optional.get()));
                String s = Integer.toString(i);
                Logger logger = SectionStorage.LOGGER;

                Objects.requireNonNull(logger);
                dataresult.resultOrPartial(logger::error).ifPresent((object) -> {
                    map.put(ops.createString(s), object);
                });
            }
        }

        return new Dynamic(ops, ops.createMap(ImmutableMap.of(ops.createString("Sections"), ops.createMap(map), ops.createString("DataVersion"), ops.createInt(SharedConstants.getCurrentVersion().dataVersion().version()))));
    }

    private static long getKey(ChunkPos chunkPos, int sectionY) {
        return SectionPos.asLong(chunkPos.x, sectionY, chunkPos.z);
    }

    protected void onSectionLoad(long sectionPos) {}

    protected void setDirty(long sectionPos) {
        Optional<R> optional = (Optional) this.storage.get(sectionPos);

        if (optional != null && !optional.isEmpty()) {
            this.dirtyChunks.add(ChunkPos.asLong(SectionPos.x(sectionPos), SectionPos.z(sectionPos)));
        } else {
            SectionStorage.LOGGER.warn("No data for position: {}", SectionPos.of(sectionPos));
        }
    }

    public void flush(ChunkPos chunkPos) {
        if (this.dirtyChunks.remove(chunkPos.toLong())) {
            this.writeChunk(chunkPos);
        }

    }

    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }

    private static record PackedChunk<T>(Int2ObjectMap<T> sectionsByY, boolean versionChanged) {

        public static <T> SectionStorage.PackedChunk<T> parse(Codec<T> codec, DynamicOps<Tag> ops, Tag tag, SimpleRegionStorage simpleRegionStorage, LevelHeightAccessor levelHeightAccessor) {
            Dynamic<Tag> dynamic = new Dynamic(ops, tag);
            Dynamic<Tag> dynamic1 = simpleRegionStorage.upgradeChunkTag(dynamic, 1945);
            boolean flag = dynamic != dynamic1;
            OptionalDynamic<Tag> optionaldynamic = dynamic1.get("Sections");
            Int2ObjectMap<T> int2objectmap = new Int2ObjectOpenHashMap();

            for (int i = levelHeightAccessor.getMinSectionY(); i <= levelHeightAccessor.getMaxSectionY(); ++i) {
                Optional<T> optional = optionaldynamic.get(Integer.toString(i)).result().flatMap((dynamic2) -> {
                    DataResult dataresult = codec.parse(dynamic2);
                    Logger logger = SectionStorage.LOGGER;

                    Objects.requireNonNull(logger);
                    return dataresult.resultOrPartial(logger::error);
                });

                if (optional.isPresent()) {
                    int2objectmap.put(i, optional.get());
                }
            }

            return new SectionStorage.PackedChunk<T>(int2objectmap, flag);
        }
    }
}
