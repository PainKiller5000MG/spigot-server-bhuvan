package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public class GlobalPalette<T> implements Palette<T> {

    private final IdMap<T> registry;

    public GlobalPalette(IdMap<T> registry) {
        this.registry = registry;
    }

    @Override
    public int idFor(T value, PaletteResize<T> resizeHandler) {
        int i = this.registry.getId(value);

        return i == -1 ? 0 : i;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        return true;
    }

    @Override
    public T valueFor(int index) {
        T t0 = this.registry.byId(index);

        if (t0 == null) {
            throw new MissingPaletteEntryException(index);
        } else {
            return t0;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer, IdMap<T> globalMap) {}

    @Override
    public void write(FriendlyByteBuf buffer, IdMap<T> globalMap) {}

    @Override
    public int getSerializedSize(IdMap<T> globalMap) {
        return 0;
    }

    @Override
    public int getSize() {
        return this.registry.size();
    }

    @Override
    public Palette<T> copy() {
        return this;
    }
}
