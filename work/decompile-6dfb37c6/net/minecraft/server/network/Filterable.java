package net.minecraft.server.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Filterable<T>(T raw, Optional<T> filtered) {

    public static <T> Codec<Filterable<T>> codec(Codec<T> valueCodec) {
        Codec<Filterable<T>> codec1 = RecordCodecBuilder.create((instance) -> {
            return instance.group(valueCodec.fieldOf("raw").forGetter(Filterable::raw), valueCodec.optionalFieldOf("filtered").forGetter(Filterable::filtered)).apply(instance, Filterable::new);
        });
        Codec<Filterable<T>> codec2 = valueCodec.xmap(Filterable::passThrough, Filterable::raw);

        return Codec.withAlternative(codec1, codec2);
    }

    public static <B extends ByteBuf, T> StreamCodec<B, Filterable<T>> streamCodec(StreamCodec<B, T> valueCodec) {
        return StreamCodec.composite(valueCodec, Filterable::raw, valueCodec.apply(ByteBufCodecs::optional), Filterable::filtered, Filterable::new);
    }

    public static <T> Filterable<T> passThrough(T value) {
        return new Filterable<T>(value, Optional.empty());
    }

    public static Filterable<String> from(FilteredText text) {
        return new Filterable<String>(text.raw(), text.isFiltered() ? Optional.of(text.filteredOrEmpty()) : Optional.empty());
    }

    public T get(boolean filterEnabled) {
        return (T) (filterEnabled ? this.filtered.orElse(this.raw) : this.raw);
    }

    public <U> Filterable<U> map(Function<T, U> function) {
        return new Filterable<U>(function.apply(this.raw), this.filtered.map(function));
    }

    public <U> Optional<Filterable<U>> resolve(Function<T, Optional<U>> function) {
        Optional<U> optional = (Optional) function.apply(this.raw);

        if (optional.isEmpty()) {
            return Optional.empty();
        } else if (this.filtered.isPresent()) {
            Optional<U> optional1 = (Optional) function.apply(this.filtered.get());

            return optional1.isEmpty() ? Optional.empty() : Optional.of(new Filterable(optional.get(), optional1));
        } else {
            return Optional.of(new Filterable(optional.get(), Optional.empty()));
        }
    }
}
