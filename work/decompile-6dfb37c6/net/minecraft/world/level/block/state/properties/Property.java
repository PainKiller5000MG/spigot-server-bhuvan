package net.minecraft.world.level.block.state.properties;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.StateHolder;
import org.jspecify.annotations.Nullable;

public abstract class Property<T extends Comparable<T>> {

    private final Class<T> clazz;
    private final String name;
    private @Nullable Integer hashCode;
    private final Codec<T> codec;
    private final Codec<Property.Value<T>> valueCodec;

    protected Property(String name, Class<T> clazz) {
        this.codec = Codec.STRING.comapFlatMap((s1) -> {
            return (DataResult) this.getValue(s1).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    String s2 = String.valueOf(this);

                    return "Unable to read property: " + s2 + " with value: " + s1;
                });
            });
        }, this::getName);
        this.valueCodec = this.codec.xmap(this::value, Property.Value::value);
        this.clazz = clazz;
        this.name = name;
    }

    public Property.Value<T> value(T value) {
        return new Property.Value<T>(this, value);
    }

    public Property.Value<T> value(StateHolder<?, ?> stateHolder) {
        return new Property.Value<T>(this, stateHolder.getValue(this));
    }

    public Stream<Property.Value<T>> getAllValues() {
        return this.getPossibleValues().stream().map(this::value);
    }

    public Codec<T> codec() {
        return this.codec;
    }

    public Codec<Property.Value<T>> valueCodec() {
        return this.valueCodec;
    }

    public String getName() {
        return this.name;
    }

    public Class<T> getValueClass() {
        return this.clazz;
    }

    public abstract List<T> getPossibleValues();

    public abstract String getName(T value);

    public abstract Optional<T> getValue(String name);

    public abstract int getInternalIndex(T value);

    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", this.name).add("clazz", this.clazz).add("values", this.getPossibleValues()).toString();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Property)) {
            return false;
        } else {
            Property<?> property = (Property) o;

            return this.clazz.equals(property.clazz) && this.name.equals(property.name);
        }
    }

    public final int hashCode() {
        if (this.hashCode == null) {
            this.hashCode = this.generateHashCode();
        }

        return this.hashCode;
    }

    public int generateHashCode() {
        return 31 * this.clazz.hashCode() + this.name.hashCode();
    }

    public <U, S extends StateHolder<?, S>> DataResult<S> parseValue(DynamicOps<U> ops, S state, U value) {
        DataResult<T> dataresult = this.codec.parse(ops, value);

        return dataresult.map((comparable) -> {
            return (StateHolder) ((StateHolder) state).setValue(this, comparable);
        }).setPartial(state);
    }

    public static record Value<T extends Comparable<T>>(Property<T> property, T value) {

        public Value {
            if (!property.getPossibleValues().contains(value)) {
                String s = String.valueOf(value);

                throw new IllegalArgumentException("Value " + s + " does not belong to property " + String.valueOf(property));
            }
        }

        public String toString() {
            String s = this.property.getName();

            return s + "=" + this.property.getName(this.value);
        }
    }
}
