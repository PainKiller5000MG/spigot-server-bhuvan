package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public abstract class StateHolder<O, S> {

    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";
    public static final Function<Map.Entry<Property<?>, Comparable<?>>, String> PROPERTY_ENTRY_TO_STRING_FUNCTION = new Function<Map.Entry<Property<?>, Comparable<?>>, String>() {
        public String apply(Map.@Nullable Entry<Property<?>, Comparable<?>> entry) {
            if (entry == null) {
                return "<NULL>";
            } else {
                Property<?> property = (Property) entry.getKey();
                String s = property.getName();

                return s + "=" + this.getName(property, (Comparable) entry.getValue());
            }
        }

        private <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
            return property.getName(value);
        }
    };
    protected final O owner;
    private final Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;
    private Map<Property<?>, S[]> neighbours;
    protected final MapCodec<S> propertiesCodec;

    protected StateHolder(O owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec) {
        this.owner = owner;
        this.values = values;
        this.propertiesCodec = propertiesCodec;
    }

    public <T extends Comparable<T>> S cycle(Property<T> property) {
        return (S) this.setValue(property, (Comparable) findNextInCollection(property.getPossibleValues(), this.getValue(property)));
    }

    protected static <T> T findNextInCollection(List<T> values, T current) {
        int i = values.indexOf(current) + 1;

        return (T) (i == values.size() ? values.getFirst() : values.get(i));
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();

        stringbuilder.append(this.owner);
        if (!this.getValues().isEmpty()) {
            stringbuilder.append('[');
            stringbuilder.append((String) this.getValues().entrySet().stream().map(StateHolder.PROPERTY_ENTRY_TO_STRING_FUNCTION).collect(Collectors.joining(",")));
            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    public int hashCode() {
        return super.hashCode();
    }

    public Collection<Property<?>> getProperties() {
        return Collections.unmodifiableCollection(this.values.keySet());
    }

    public boolean hasProperty(Property<?> property) {
        return this.values.containsKey(property);
    }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        Comparable<?> comparable = (Comparable) this.values.get(property);

        if (comparable == null) {
            String s = String.valueOf(property);

            throw new IllegalArgumentException("Cannot get property " + s + " as it does not exist in " + String.valueOf(this.owner));
        } else {
            return (T) (property.getValueClass().cast(comparable));
        }
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        return Optional.ofNullable(this.getNullableValue(property));
    }

    public <T extends Comparable<T>> T getValueOrElse(Property<T> property, T defaultValue) {
        return (T) (Objects.requireNonNullElse(this.getNullableValue(property), defaultValue));
    }

    private <T extends Comparable<T>> @Nullable T getNullableValue(Property<T> property) {
        Comparable<?> comparable = (Comparable) this.values.get(property);

        return (T) (comparable == null ? null : (Comparable) property.getValueClass().cast(comparable));
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        Comparable<?> comparable = (Comparable) this.values.get(property);

        if (comparable == null) {
            String s = String.valueOf(property);

            throw new IllegalArgumentException("Cannot set property " + s + " as it does not exist in " + String.valueOf(this.owner));
        } else {
            return (S) this.setValueInternal(property, value, comparable);
        }
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        Comparable<?> comparable = (Comparable) this.values.get(property);

        return (S) (comparable == null ? this : this.setValueInternal(property, value, comparable));
    }

    private <T extends Comparable<T>, V extends T> S setValueInternal(Property<T> property, V value, Comparable<?> oldValue) {
        if (oldValue.equals(value)) {
            return (S) this;
        } else {
            int i = property.getInternalIndex(value);

            if (i < 0) {
                String s = String.valueOf(property);

                throw new IllegalArgumentException("Cannot set property " + s + " to " + String.valueOf(value) + " on " + String.valueOf(this.owner) + ", it is not an allowed value");
            } else {
                return (S) ((Object[]) this.neighbours.get(property))[i];
            }
        }
    }

    public void populateNeighbours(Map<Map<Property<?>, Comparable<?>>, S> statesByValues) {
        if (this.neighbours != null) {
            throw new IllegalStateException();
        } else {
            Map<Property<?>, S[]> map1 = new Reference2ObjectArrayMap(this.values.size());
            ObjectIterator objectiterator = this.values.entrySet().iterator();

            while (objectiterator.hasNext()) {
                Map.Entry<Property<?>, Comparable<?>> map_entry = (Entry) objectiterator.next();
                Property<?> property = (Property) map_entry.getKey();

                map1.put(property, property.getPossibleValues().stream().map((comparable) -> {
                    return statesByValues.get(this.makeNeighbourValues(property, comparable));
                }).toArray());
            }

            this.neighbours = map1;
        }
    }

    private Map<Property<?>, Comparable<?>> makeNeighbourValues(Property<?> property, Comparable<?> value) {
        Map<Property<?>, Comparable<?>> map = new Reference2ObjectArrayMap(this.values);

        map.put(property, value);
        return map;
    }

    public Map<Property<?>, Comparable<?>> getValues() {
        return this.values;
    }

    protected static <O, S extends StateHolder<O, S>> Codec<S> codec(Codec<O> ownerCodec, Function<O, S> defaultState) {
        return ownerCodec.dispatch("Name", (stateholder) -> {
            return stateholder.owner;
        }, (object) -> {
            S s0 = (StateHolder) defaultState.apply(object);

            return s0.getValues().isEmpty() ? MapCodec.unit(s0) : s0.propertiesCodec.codec().lenientOptionalFieldOf("Properties").xmap((optional) -> {
                return (StateHolder) optional.orElse(s0);
            }, Optional::of);
        });
    }
}
