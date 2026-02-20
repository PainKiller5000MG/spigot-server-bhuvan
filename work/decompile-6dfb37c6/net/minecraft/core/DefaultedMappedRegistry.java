package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {

    private final Identifier defaultKey;
    private Holder.Reference<T> defaultValue;

    public DefaultedMappedRegistry(String defaultKey, ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, boolean intrusiveHolders) {
        super(key, lifecycle, intrusiveHolders);
        this.defaultKey = Identifier.parse(defaultKey);
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        Holder.Reference<T> holder_reference = super.register(key, value, registrationInfo);

        if (this.defaultKey.equals(key.identifier())) {
            this.defaultValue = holder_reference;
        }

        return holder_reference;
    }

    @Override
    public int getId(@Nullable T thing) {
        int i = super.getId(thing);

        return i == -1 ? super.getId(this.defaultValue.value()) : i;
    }

    @Override
    public Identifier getKey(T thing) {
        Identifier identifier = super.getKey(thing);

        return identifier == null ? this.defaultKey : identifier;
    }

    @Override
    public T getValue(@Nullable Identifier key) {
        T t0 = (T) super.getValue(key);

        return (T) (t0 == null ? this.defaultValue.value() : t0);
    }

    @Override
    public Optional<T> getOptional(@Nullable Identifier key) {
        return Optional.ofNullable(super.getValue(key));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return Optional.ofNullable(this.defaultValue);
    }

    @Override
    public T byId(int id) {
        T t0 = (T) super.byId(id);

        return (T) (t0 == null ? this.defaultValue.value() : t0);
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return super.getRandom(random).or(() -> {
            return Optional.of(this.defaultValue);
        });
    }

    @Override
    public Identifier getDefaultKey() {
        return this.defaultKey;
    }
}
