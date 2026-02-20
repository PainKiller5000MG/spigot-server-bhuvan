package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.apache.commons.lang3.Validate;

public class LinearPalette<T> implements Palette<T> {

    private final T[] values;
    private final int bits;
    private int size;

    private LinearPalette(int bits, List<T> paletteEntries) {
        this.values = (T[]) (new Object[1 << bits]);
        this.bits = bits;
        Validate.isTrue(paletteEntries.size() <= this.values.length, "Can't initialize LinearPalette of size %d with %d entries", new Object[]{this.values.length, paletteEntries.size()});

        for (int j = 0; j < paletteEntries.size(); ++j) {
            this.values[j] = paletteEntries.get(j);
        }

        this.size = paletteEntries.size();
    }

    private LinearPalette(T[] values, int bits, int size) {
        this.values = values;
        this.bits = bits;
        this.size = size;
    }

    public static <A> Palette<A> create(int bits, List<A> paletteEntries) {
        return new LinearPalette<A>(bits, paletteEntries);
    }

    @Override
    public int idFor(T value, PaletteResize<T> resizeHandler) {
        for (int i = 0; i < this.size; ++i) {
            if (this.values[i] == value) {
                return i;
            }
        }

        int j = this.size;

        if (j < this.values.length) {
            this.values[j] = value;
            ++this.size;
            return j;
        } else {
            return resizeHandler.onResize(this.bits + 1, value);
        }
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        for (int i = 0; i < this.size; ++i) {
            if (predicate.test(this.values[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int index) {
        if (index >= 0 && index < this.size) {
            return (T) this.values[index];
        } else {
            throw new MissingPaletteEntryException(index);
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer, IdMap<T> globalMap) {
        this.size = buffer.readVarInt();

        for (int i = 0; i < this.size; ++i) {
            this.values[i] = globalMap.byIdOrThrow(buffer.readVarInt());
        }

    }

    @Override
    public void write(FriendlyByteBuf buffer, IdMap<T> globalMap) {
        buffer.writeVarInt(this.size);

        for (int i = 0; i < this.size; ++i) {
            buffer.writeVarInt(globalMap.getId(this.values[i]));
        }

    }

    @Override
    public int getSerializedSize(IdMap<T> globalMap) {
        int i = VarInt.getByteSize(this.getSize());

        for (int j = 0; j < this.getSize(); ++j) {
            i += VarInt.getByteSize(globalMap.getId(this.values[j]));
        }

        return i;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public Palette<T> copy() {
        return new LinearPalette<T>(this.values.clone(), this.bits, this.size);
    }
}
