package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

public interface RegistryAccess extends HolderLookup.Provider {

    Logger LOGGER = LogUtils.getLogger();
    RegistryAccess.Frozen EMPTY = (new RegistryAccess.ImmutableRegistryAccess(Map.of())).freeze();

    @Override
    <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryKey);

    @Override
    default <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>> name) {
        return (Registry) this.lookup(name).orElseThrow(() -> {
            return new IllegalStateException("Missing registry: " + String.valueOf(name));
        });
    }

    Stream<RegistryAccess.RegistryEntry<?>> registries();

    @Override
    default Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
        return this.registries().map((registryaccess_registryentry) -> {
            return registryaccess_registryentry.key;
        });
    }

    static RegistryAccess.Frozen fromRegistryOfRegistries(final Registry<? extends Registry<?>> registries) {
        return new RegistryAccess.Frozen() {
            @Override
            public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                Registry<Registry<T>> registry1 = registries;

                return registry1.getOptional(registryKey);
            }

            @Override
            public Stream<RegistryAccess.RegistryEntry<?>> registries() {
                return registries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
            }

            @Override
            public RegistryAccess.Frozen freeze() {
                return this;
            }
        };
    }

    default RegistryAccess.Frozen freeze() {
        class 1FrozenAccess extends RegistryAccess.ImmutableRegistryAccess implements RegistryAccess.Frozen {

            protected _FrozenAccess/* $FF was: 1FrozenAccess*/(Stream<RegistryAccess.RegistryEntry<?>> entries) {
                super(entries);
            }
        }


        return new 1FrozenAccess(this.registries().map(RegistryAccess.RegistryEntry::freeze));
    }

    public static record RegistryEntry<T>(ResourceKey<? extends Registry<T>> key, Registry<T> value) {

        private static <T, R extends Registry<? extends T>> RegistryAccess.RegistryEntry<T> fromMapEntry(Map.Entry<? extends ResourceKey<? extends Registry<?>>, R> e) {
            return fromUntyped((ResourceKey) e.getKey(), (Registry) e.getValue());
        }

        private static <T> RegistryAccess.RegistryEntry<T> fromUntyped(ResourceKey<? extends Registry<?>> key, Registry<?> value) {
            return new RegistryAccess.RegistryEntry<T>(key, value);
        }

        private RegistryAccess.RegistryEntry<T> freeze() {
            return new RegistryAccess.RegistryEntry<T>(this.key, this.value.freeze());
        }
    }

    public static class ImmutableRegistryAccess implements RegistryAccess {

        private final Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries;

        public ImmutableRegistryAccess(List<? extends Registry<?>> registries) {
            this.registries = (Map) registries.stream().collect(Collectors.toUnmodifiableMap(Registry::key, (registry) -> {
                return registry;
            }));
        }

        public ImmutableRegistryAccess(Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries) {
            this.registries = Map.copyOf(registries);
        }

        public ImmutableRegistryAccess(Stream<RegistryAccess.RegistryEntry<?>> entries) {
            this.registries = (Map) entries.collect(ImmutableMap.toImmutableMap(RegistryAccess.RegistryEntry::key, RegistryAccess.RegistryEntry::value));
        }

        @Override
        public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryKey) {
            return Optional.ofNullable((Registry) this.registries.get(registryKey)).map((registry) -> {
                return registry;
            });
        }

        @Override
        public Stream<RegistryAccess.RegistryEntry<?>> registries() {
            return this.registries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
        }
    }

    public interface Frozen extends RegistryAccess {}
}
