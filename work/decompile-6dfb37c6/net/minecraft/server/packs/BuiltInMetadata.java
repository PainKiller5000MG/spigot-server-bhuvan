package net.minecraft.server.packs;

import java.util.Map;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public class BuiltInMetadata {

    private static final BuiltInMetadata EMPTY = new BuiltInMetadata(Map.of());
    private final Map<MetadataSectionType<?>, ?> values;

    private BuiltInMetadata(Map<MetadataSectionType<?>, ?> values) {
        this.values = values;
    }

    public <T> T get(MetadataSectionType<T> section) {
        return (T) this.values.get(section);
    }

    public static BuiltInMetadata of() {
        return BuiltInMetadata.EMPTY;
    }

    public static <T> BuiltInMetadata of(MetadataSectionType<T> k, T v) {
        return new BuiltInMetadata(Map.of(k, v));
    }

    public static <T1, T2> BuiltInMetadata of(MetadataSectionType<T1> k1, T1 v1, MetadataSectionType<T2> k2, T2 v2) {
        return new BuiltInMetadata(Map.of(k1, v1, k2, v2));
    }
}
