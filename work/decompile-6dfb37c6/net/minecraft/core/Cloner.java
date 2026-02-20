package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

public class Cloner<T> {

    private final Codec<T> directCodec;

    private Cloner(Codec<T> directCodec) {
        this.directCodec = directCodec;
    }

    public T clone(T value, HolderLookup.Provider from, HolderLookup.Provider to) {
        DynamicOps<Object> dynamicops = from.<Object>createSerializationContext(JavaOps.INSTANCE);
        DynamicOps<Object> dynamicops1 = to.<Object>createSerializationContext(JavaOps.INSTANCE);
        Object object = this.directCodec.encodeStart(dynamicops, value).getOrThrow((s) -> {
            return new IllegalStateException("Failed to encode: " + s);
        });

        return (T) this.directCodec.parse(dynamicops1, object).getOrThrow((s) -> {
            return new IllegalStateException("Failed to decode: " + s);
        });
    }

    public static class Factory {

        private final Map<ResourceKey<? extends Registry<?>>, Cloner<?>> codecs = new HashMap();

        public Factory() {}

        public <T> Cloner.Factory addCodec(ResourceKey<? extends Registry<? extends T>> key, Codec<T> codec) {
            this.codecs.put(key, new Cloner(codec));
            return this;
        }

        public <T> @Nullable Cloner<T> cloner(ResourceKey<? extends Registry<? extends T>> key) {
            return (Cloner) this.codecs.get(key);
        }
    }
}
