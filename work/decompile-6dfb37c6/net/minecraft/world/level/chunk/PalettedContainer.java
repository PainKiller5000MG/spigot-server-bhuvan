package net.minecraft.world.level.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;
import org.jspecify.annotations.Nullable;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {

    private static final int MIN_PALETTE_BITS = 0;
    private volatile PalettedContainer.Data<T> data;
    private final Strategy<T> strategy;
    private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer");

    public void acquire() {
        this.threadingDetector.checkAndLock();
    }

    public void release() {
        this.threadingDetector.checkAndUnlock();
    }

    public static <T> Codec<PalettedContainer<T>> codecRW(Codec<T> elementCodec, Strategy<T> strategy, T defaultValue) {
        PalettedContainerRO.Unpacker<T, PalettedContainer<T>> palettedcontainerro_unpacker = PalettedContainer::unpack;

        return codec(elementCodec, strategy, defaultValue, palettedcontainerro_unpacker);
    }

    public static <T> Codec<PalettedContainerRO<T>> codecRO(Codec<T> elementCodec, Strategy<T> strategy, T defaultValue) {
        PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> palettedcontainerro_unpacker = (strategy1, palettedcontainerro_packeddata) -> {
            return unpack(strategy1, palettedcontainerro_packeddata).map((palettedcontainer) -> {
                return palettedcontainer;
            });
        };

        return codec(elementCodec, strategy, defaultValue, palettedcontainerro_unpacker);
    }

    private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(Codec<T> elementCodec, Strategy<T> strategy, T defaultValue, PalettedContainerRO.Unpacker<T, C> unpacker) {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(elementCodec.mapResult(ExtraCodecs.orElsePartial(defaultValue)).listOf().fieldOf("palette").forGetter(PalettedContainerRO.PackedData::paletteEntries), Codec.LONG_STREAM.lenientOptionalFieldOf("data").forGetter(PalettedContainerRO.PackedData::storage)).apply(instance, PalettedContainerRO.PackedData::new);
        }).comapFlatMap((palettedcontainerro_packeddata) -> {
            return unpacker.read(strategy, palettedcontainerro_packeddata);
        }, (palettedcontainerro) -> {
            return palettedcontainerro.pack(strategy);
        });
    }

    private PalettedContainer(Strategy<T> strategy, Configuration dataConfiguration, BitStorage storage, Palette<T> palette) {
        this.strategy = strategy;
        this.data = new PalettedContainer.Data<T>(dataConfiguration, storage, palette);
    }

    private PalettedContainer(PalettedContainer<T> source) {
        this.strategy = source.strategy;
        this.data = source.data.copy();
    }

    public PalettedContainer(T initialValue, Strategy<T> strategy) {
        this.strategy = strategy;
        this.data = this.createOrReuseData((PalettedContainer.Data) null, 0);
        this.data.palette.idFor(initialValue, this);
    }

    private PalettedContainer.Data<T> createOrReuseData(PalettedContainer.@Nullable Data<T> oldData, int targetBits) {
        Configuration configuration = this.strategy.getConfigurationForBitCount(targetBits);

        if (oldData != null && configuration.equals(oldData.configuration())) {
            return oldData;
        } else {
            BitStorage bitstorage = (BitStorage) (configuration.bitsInMemory() == 0 ? new ZeroBitStorage(this.strategy.entryCount()) : new SimpleBitStorage(configuration.bitsInMemory(), this.strategy.entryCount()));
            Palette<T> palette = configuration.<T>createPalette(this.strategy, List.of());

            return new PalettedContainer.Data<T>(configuration, bitstorage, palette);
        }
    }

    @Override
    public int onResize(int bits, T lastAddedValue) {
        PalettedContainer.Data<T> palettedcontainer_data = this.data;
        PalettedContainer.Data<T> palettedcontainer_data1 = this.createOrReuseData(palettedcontainer_data, bits);

        palettedcontainer_data1.copyFrom(palettedcontainer_data.palette, palettedcontainer_data.storage);
        this.data = palettedcontainer_data1;
        return palettedcontainer_data1.palette.idFor(lastAddedValue, PaletteResize.noResizeExpected());
    }

    public T getAndSet(int x, int y, int z, T value) {
        this.acquire();

        Object object;

        try {
            object = this.getAndSet(this.strategy.getIndex(x, y, z), value);
        } finally {
            this.release();
        }

        return (T) object;
    }

    public T getAndSetUnchecked(int x, int y, int z, T value) {
        return (T) this.getAndSet(this.strategy.getIndex(x, y, z), value);
    }

    private T getAndSet(int index, T value) {
        int j = this.data.palette.idFor(value, this);
        int k = this.data.storage.getAndSet(index, j);

        return this.data.palette.valueFor(k);
    }

    public void set(int x, int y, int z, T value) {
        this.acquire();

        try {
            this.set(this.strategy.getIndex(x, y, z), value);
        } finally {
            this.release();
        }

    }

    private void set(int index, T value) {
        int j = this.data.palette.idFor(value, this);

        this.data.storage.set(index, j);
    }

    @Override
    public T get(int x, int y, int z) {
        return (T) this.get(this.strategy.getIndex(x, y, z));
    }

    protected T get(int index) {
        PalettedContainer.Data<T> palettedcontainer_data = this.data;

        return palettedcontainer_data.palette.valueFor(palettedcontainer_data.storage.get(index));
    }

    @Override
    public void getAll(Consumer<T> consumer) {
        Palette<T> palette = this.data.palette();
        IntSet intset = new IntArraySet();
        BitStorage bitstorage = this.data.storage;

        Objects.requireNonNull(intset);
        bitstorage.getAll(intset::add);
        intset.forEach((i) -> {
            consumer.accept(palette.valueFor(i));
        });
    }

    public void read(FriendlyByteBuf buffer) {
        this.acquire();

        try {
            int i = buffer.readByte();
            PalettedContainer.Data<T> palettedcontainer_data = this.createOrReuseData(this.data, i);

            palettedcontainer_data.palette.read(buffer, this.strategy.globalMap());
            buffer.readFixedSizeLongArray(palettedcontainer_data.storage.getRaw());
            this.data = palettedcontainer_data;
        } finally {
            this.release();
        }

    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        this.acquire();

        try {
            this.data.write(buffer, this.strategy.globalMap());
        } finally {
            this.release();
        }

    }

    @VisibleForTesting
    public static <T> DataResult<PalettedContainer<T>> unpack(Strategy<T> strategy, PalettedContainerRO.PackedData<T> discData) {
        List<T> list = discData.paletteEntries();
        int i = strategy.entryCount();
        Configuration configuration = strategy.getConfigurationForPaletteSize(list.size());
        int j = configuration.bitsInStorage();

        if (discData.bitsPerEntry() != -1 && j != discData.bitsPerEntry()) {
            return DataResult.error(() -> {
                return "Invalid bit count, calculated " + j + ", but container declared " + discData.bitsPerEntry();
            });
        } else {
            BitStorage bitstorage;
            Palette<T> palette;

            if (configuration.bitsInMemory() == 0) {
                palette = configuration.<T>createPalette(strategy, list);
                bitstorage = new ZeroBitStorage(i);
            } else {
                Optional<LongStream> optional = discData.storage();

                if (optional.isEmpty()) {
                    return DataResult.error(() -> {
                        return "Missing values for non-zero storage";
                    });
                }

                long[] along = ((LongStream) optional.get()).toArray();

                try {
                    if (!configuration.alwaysRepack() && configuration.bitsInMemory() == j) {
                        palette = configuration.<T>createPalette(strategy, list);
                        bitstorage = new SimpleBitStorage(configuration.bitsInMemory(), i, along);
                    } else {
                        Palette<T> palette1 = new HashMapPalette<T>(j, list);
                        SimpleBitStorage simplebitstorage = new SimpleBitStorage(j, i, along);
                        Palette<T> palette2 = configuration.<T>createPalette(strategy, list);
                        int[] aint = reencodeContents(simplebitstorage, palette1, palette2);

                        palette = palette2;
                        bitstorage = new SimpleBitStorage(configuration.bitsInMemory(), i, aint);
                    }
                } catch (SimpleBitStorage.InitializationException simplebitstorage_initializationexception) {
                    return DataResult.error(() -> {
                        return "Failed to read PalettedContainer: " + simplebitstorage_initializationexception.getMessage();
                    });
                }
            }

            return DataResult.success(new PalettedContainer(strategy, configuration, bitstorage, palette));
        }
    }

    @Override
    public PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy) {
        this.acquire();

        PalettedContainerRO.PackedData palettedcontainerro_packeddata;

        try {
            BitStorage bitstorage = this.data.storage;
            Palette<T> palette = this.data.palette;
            HashMapPalette<T> hashmappalette = new HashMapPalette<T>(bitstorage.getBits());
            int i = strategy.entryCount();
            int[] aint = reencodeContents(bitstorage, palette, hashmappalette);
            Configuration configuration = strategy.getConfigurationForPaletteSize(hashmappalette.getSize());
            int j = configuration.bitsInStorage();
            Optional<LongStream> optional;

            if (j != 0) {
                SimpleBitStorage simplebitstorage = new SimpleBitStorage(j, i, aint);

                optional = Optional.of(Arrays.stream(simplebitstorage.getRaw()));
            } else {
                optional = Optional.empty();
            }

            palettedcontainerro_packeddata = new PalettedContainerRO.PackedData(hashmappalette.getEntries(), optional, j);
        } finally {
            this.release();
        }

        return palettedcontainerro_packeddata;
    }

    private static <T> int[] reencodeContents(BitStorage storage, Palette<T> oldPalette, Palette<T> newPalette) {
        int[] aint = new int[storage.getSize()];

        storage.unpack(aint);
        PaletteResize<T> paletteresize = PaletteResize.<T>noResizeExpected();
        int i = -1;
        int j = -1;

        for (int k = 0; k < aint.length; ++k) {
            int l = aint[k];

            if (l != i) {
                i = l;
                j = newPalette.idFor(oldPalette.valueFor(l), paletteresize);
            }

            aint[k] = j;
        }

        return aint;
    }

    @Override
    public int getSerializedSize() {
        return this.data.getSerializedSize(this.strategy.globalMap());
    }

    @Override
    public int bitsPerEntry() {
        return this.data.storage().getBits();
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        return this.data.palette.maybeHas(predicate);
    }

    @Override
    public PalettedContainer<T> copy() {
        return new PalettedContainer<T>(this);
    }

    @Override
    public PalettedContainer<T> recreate() {
        return new PalettedContainer<T>(this.data.palette.valueFor(0), this.strategy);
    }

    @Override
    public void count(PalettedContainer.CountConsumer<T> output) {
        if (this.data.palette.getSize() == 1) {
            output.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
        } else {
            Int2IntOpenHashMap int2intopenhashmap = new Int2IntOpenHashMap();

            this.data.storage.getAll((i) -> {
                int2intopenhashmap.addTo(i, 1);
            });
            int2intopenhashmap.int2IntEntrySet().forEach((entry) -> {
                output.accept(this.data.palette.valueFor(entry.getIntKey()), entry.getIntValue());
            });
        }
    }

    private static record Data<T>(Configuration configuration, BitStorage storage, Palette<T> palette) {

        public void copyFrom(Palette<T> oldPalette, BitStorage oldStorage) {
            PaletteResize<T> paletteresize = PaletteResize.<T>noResizeExpected();

            for (int i = 0; i < oldStorage.getSize(); ++i) {
                T t0 = oldPalette.valueFor(oldStorage.get(i));

                this.storage.set(i, this.palette.idFor(t0, paletteresize));
            }

        }

        public int getSerializedSize(IdMap<T> globalMap) {
            return 1 + this.palette.getSerializedSize(globalMap) + this.storage.getRaw().length * 8;
        }

        public void write(FriendlyByteBuf buffer, IdMap<T> globalMap) {
            buffer.writeByte(this.storage.getBits());
            this.palette.write(buffer, globalMap);
            buffer.writeFixedSizeLongArray(this.storage.getRaw());
        }

        public PalettedContainer.Data<T> copy() {
            return new PalettedContainer.Data<T>(this.configuration, this.storage.copy(), this.palette.copy());
        }
    }

    @FunctionalInterface
    public interface CountConsumer<T> {

        void accept(T entry, int count);
    }
}
