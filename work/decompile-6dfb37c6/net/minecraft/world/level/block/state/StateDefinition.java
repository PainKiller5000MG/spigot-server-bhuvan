package net.minecraft.world.level.block.state;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class StateDefinition<O, S extends StateHolder<O, S>> {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private final O owner;
    private final ImmutableSortedMap<String, Property<?>> propertiesByName;
    private final ImmutableList<S> states;

    protected StateDefinition(Function<O, S> defaultState, O owner, StateDefinition.Factory<O, S> factory, Map<String, Property<?>> properties) {
        this.owner = owner;
        this.propertiesByName = ImmutableSortedMap.copyOf(properties);
        Supplier<S> supplier = () -> {
            return (StateHolder) defaultState.apply(owner);
        };
        MapCodec<S> mapcodec = MapCodec.of(Encoder.empty(), Decoder.unit(supplier));

        Map.Entry<String, Property<?>> map_entry;

        for (UnmodifiableIterator unmodifiableiterator = this.propertiesByName.entrySet().iterator(); unmodifiableiterator.hasNext(); mapcodec = appendPropertyCodec(mapcodec, supplier, (String) map_entry.getKey(), (Property) map_entry.getValue())) {
            map_entry = (Entry) unmodifiableiterator.next();
        }

        Map<Map<Property<?>, Comparable<?>>, S> map1 = Maps.newLinkedHashMap();
        List<S> list = Lists.newArrayList();
        Stream<List<Pair<Property<?>, Comparable<?>>>> stream = Stream.of(Collections.emptyList());

        Property<?> property;

        for (UnmodifiableIterator unmodifiableiterator1 = this.propertiesByName.values().iterator(); unmodifiableiterator1.hasNext();stream = stream.flatMap((list1) -> {
            return property.getPossibleValues().stream().map((comparable) -> {
                List<Pair<Property<?>, Comparable<?>>> list2 = Lists.newArrayList(list1);

                list2.add(Pair.of(property, comparable));
                return list2;
            });
        })) {
            property = (Property) unmodifiableiterator1.next();
        }

        stream.forEach((list1) -> {
            Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2objectarraymap = new Reference2ObjectArrayMap(list1.size());

            for (Pair<Property<?>, Comparable<?>> pair : list1) {
                reference2objectarraymap.put((Property) pair.getFirst(), (Comparable) pair.getSecond());
            }

            S s0 = factory.create(owner, reference2objectarraymap, mapcodec);

            map1.put(reference2objectarraymap, s0);
            list.add(s0);
        });

        for (S s0 : list) {
            ((StateHolder) s0).populateNeighbours(map1);
        }

        this.states = ImmutableList.copyOf(list);
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> appendPropertyCodec(MapCodec<S> codec, Supplier<S> defaultSupplier, String name, Property<T> property) {
        return Codec.mapPair(codec, property.valueCodec().fieldOf(name).orElseGet((s1) -> {
        }, () -> {
            return property.value((StateHolder) defaultSupplier.get());
        })).xmap((pair) -> {
            return (StateHolder) ((StateHolder) pair.getFirst()).setValue(property, ((Property.Value) pair.getSecond()).value());
        }, (stateholder) -> {
            return Pair.of(stateholder, property.value(stateholder));
        });
    }

    public ImmutableList<S> getPossibleStates() {
        return this.states;
    }

    public S any() {
        return (S) (this.states.get(0));
    }

    public O getOwner() {
        return this.owner;
    }

    public Collection<Property<?>> getProperties() {
        return this.propertiesByName.values();
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("block", this.owner).add("properties", this.propertiesByName.values().stream().map(Property::getName).collect(Collectors.toList())).toString();
    }

    public @Nullable Property<?> getProperty(String name) {
        return (Property) this.propertiesByName.get(name);
    }

    public static class Builder<O, S extends StateHolder<O, S>> {

        private final O owner;
        private final Map<String, Property<?>> properties = Maps.newHashMap();

        public Builder(O owner) {
            this.owner = owner;
        }

        public StateDefinition.Builder<O, S> add(Property<?>... properties) {
            for (Property<?> property : properties) {
                this.validateProperty(property);
                this.properties.put(property.getName(), property);
            }

            return this;
        }

        private <T extends Comparable<T>> void validateProperty(Property<T> property) {
            String s = property.getName();

            if (!StateDefinition.NAME_PATTERN.matcher(s).matches()) {
                String s1 = String.valueOf(this.owner);

                throw new IllegalArgumentException(s1 + " has invalidly named property: " + s);
            } else {
                Collection<T> collection = property.getPossibleValues();

                if (collection.size() <= 1) {
                    String s2 = String.valueOf(this.owner);

                    throw new IllegalArgumentException(s2 + " attempted use property " + s + " with <= 1 possible values");
                } else {
                    for (T t0 : collection) {
                        String s3 = property.getName(t0);

                        if (!StateDefinition.NAME_PATTERN.matcher(s3).matches()) {
                            throw new IllegalArgumentException(String.valueOf(this.owner) + " has property: " + s + " with invalidly named value: " + s3);
                        }
                    }

                    if (this.properties.containsKey(s)) {
                        String s4 = String.valueOf(this.owner);

                        throw new IllegalArgumentException(s4 + " has duplicate property: " + s);
                    }
                }
            }
        }

        public StateDefinition<O, S> create(Function<O, S> defaultState, StateDefinition.Factory<O, S> factory) {
            return new StateDefinition<O, S>(defaultState, this.owner, factory, this.properties);
        }
    }

    public interface Factory<O, S> {

        S create(O type, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec);
    }
}
