package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

public class HashMapPalette<T> implements Palette<T> {

    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final int bits;

    public HashMapPalette(int bits, List<T> values) {
        this(bits);
        CrudeIncrementalIntIdentityHashBiMap crudeincrementalintidentityhashbimap = this.values;

        Objects.requireNonNull(this.values);
        values.forEach(crudeincrementalintidentityhashbimap::add);
    }

    public HashMapPalette(int bits) {
        this(bits, CrudeIncrementalIntIdentityHashBiMap.create(1 << bits));
    }

    private HashMapPalette(int bits, CrudeIncrementalIntIdentityHashBiMap<T> values) {
        this.bits = bits;
        this.values = values;
    }

    public static <A> Palette<A> create(int bits, List<A> paletteEntries) {
        return new HashMapPalette<A>(bits, paletteEntries);
    }

    @Override
    public int idFor(T value, PaletteResize<T> resizeHandler) {
        int i = this.values.getId(value);

        if (i == -1) {
            i = this.values.add(value);
            if (i >= 1 << this.bits) {
                i = resizeHandler.onResize(this.bits + 1, value);
            }
        }

        return i;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        for (int i = 0; i < this.getSize(); ++i) {
            if (predicate.test(this.values.byId(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int index) {
        T t0 = (T) this.values.byId(index);

        if (t0 == null) {
            throw new MissingPaletteEntryException(index);
        } else {
            return t0;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer, IdMap<T> globalMap) {
        this.values.clear();
        int i = buffer.readVarInt();

        for (int j = 0; j < i; ++j) {
            this.values.add(globalMap.byIdOrThrow(buffer.readVarInt()));
        }

    }

    @Override
    public void write(FriendlyByteBuf buffer, IdMap<T> globalMap) {
        int i = this.getSize();

        buffer.writeVarInt(i);

        for (int j = 0; j < i; ++j) {
            buffer.writeVarInt(globalMap.getId(this.values.byId(j)));
        }

    }

    @Override
    public int getSerializedSize(IdMap<T> globalMap) {
        int i = VarInt.getByteSize(this.getSize());

        for (int j = 0; j < this.getSize(); ++j) {
            i += VarInt.getByteSize(globalMap.getId(this.values.byId(j)));
        }

        return i;
    }

    public List<T> getEntries() {
        ArrayList<T> arraylist = new ArrayList();
        Iterator iterator = this.values.iterator();

        Objects.requireNonNull(arraylist);
        iterator.forEachRemaining(arraylist::add);
        return arraylist;
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public Palette<T> copy() {
        return new HashMapPalette<T>(this.bits, this.values.copy());
    }
}
