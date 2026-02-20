package net.minecraft.world.level.block.state.properties;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;

public final class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> {

    private final List<T> values;
    private final Map<String, T> names;
    private final int[] ordinalToIndex;

    private EnumProperty(String name, Class<T> clazz, List<T> values) {
        super(name, clazz);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Trying to make empty EnumProperty '" + name + "'");
        } else {
            this.values = List.copyOf(values);
            T[] at = (T[]) (clazz.getEnumConstants());

            this.ordinalToIndex = new int[at.length];

            for (T t0 : at) {
                this.ordinalToIndex[t0.ordinal()] = values.indexOf(t0);
            }

            ImmutableMap.Builder<String, T> immutablemap_builder = ImmutableMap.builder();

            for (T t1 : values) {
                String s1 = ((StringRepresentable) t1).getSerializedName();

                immutablemap_builder.put(s1, t1);
            }

            this.names = immutablemap_builder.buildOrThrow();
        }
    }

    @Override
    public List<T> getPossibleValues() {
        return this.values;
    }

    @Override
    public Optional<T> getValue(String name) {
        return Optional.ofNullable((Enum) this.names.get(name));
    }

    public String getName(T value) {
        return ((StringRepresentable) value).getSerializedName();
    }

    public int getInternalIndex(T value) {
        return this.ordinalToIndex[value.ordinal()];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            if (o instanceof EnumProperty) {
                EnumProperty<?> enumproperty = (EnumProperty) o;

                if (super.equals(o)) {
                    return this.values.equals(enumproperty.values);
                }
            }

            return false;
        }
    }

    @Override
    public int generateHashCode() {
        int i = super.generateHashCode();

        i = 31 * i + this.values.hashCode();
        return i;
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, (oenum) -> {
            return true;
        });
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Predicate<T> filter) {
        return create(name, clazz, (List) Arrays.stream((Enum[]) clazz.getEnumConstants()).filter(filter).collect(Collectors.toList()));
    }

    @SafeVarargs
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, T... values) {
        return create(name, clazz, List.of(values));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, List<T> values) {
        return new EnumProperty<T>(name, clazz, values);
    }
}
