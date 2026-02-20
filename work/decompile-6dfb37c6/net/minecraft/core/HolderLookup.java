package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;

public interface HolderLookup<T> extends HolderGetter<T> {

    Stream<Holder.Reference<T>> listElements();

    default Stream<ResourceKey<T>> listElementIds() {
        return this.listElements().map(Holder.Reference::key);
    }

    Stream<HolderSet.Named<T>> listTags();

    default Stream<TagKey<T>> listTagIds() {
        return this.listTags().map(HolderSet.Named::key);
    }

    public interface RegistryLookup<T> extends HolderLookup<T>, HolderOwner<T> {

        ResourceKey<? extends Registry<? extends T>> key();

        Lifecycle registryLifecycle();

        default HolderLookup.RegistryLookup<T> filterFeatures(FeatureFlagSet enabledFeatures) {
            return FeatureElement.FILTERED_REGISTRIES.contains(this.key()) ? this.filterElements((object) -> {
                return ((FeatureElement) object).isEnabled(enabledFeatures);
            }) : this;
        }

        default HolderLookup.RegistryLookup<T> filterElements(final Predicate<T> filter) {
            return new HolderLookup.RegistryLookup.Delegate<T>() {
                @Override
                public HolderLookup.RegistryLookup<T> parent() {
                    return RegistryLookup.this;
                }

                @Override
                public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                    return this.parent().get(id).filter((holder_reference) -> {
                        return filter.test(holder_reference.value());
                    });
                }

                @Override
                public Stream<Holder.Reference<T>> listElements() {
                    return this.parent().listElements().filter((holder_reference) -> {
                        return filter.test(holder_reference.value());
                    });
                }
            };
        }

        public interface Delegate<T> extends HolderLookup.RegistryLookup<T> {

            HolderLookup.RegistryLookup<T> parent();

            @Override
            default ResourceKey<? extends Registry<? extends T>> key() {
                return this.parent().key();
            }

            @Override
            default Lifecycle registryLifecycle() {
                return this.parent().registryLifecycle();
            }

            @Override
            default Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                return this.parent().get(id);
            }

            @Override
            default Stream<Holder.Reference<T>> listElements() {
                return this.parent().listElements();
            }

            @Override
            default Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                return this.parent().get(id);
            }

            @Override
            default Stream<HolderSet.Named<T>> listTags() {
                return this.parent().listTags();
            }
        }
    }

    public interface Provider extends HolderGetter.Provider {

        Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys();

        default Stream<HolderLookup.RegistryLookup<?>> listRegistries() {
            return this.listRegistryKeys().map(this::lookupOrThrow);
        }

        @Override
        <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key);

        @Override
        default <T> HolderLookup.RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> key) {
            return (HolderLookup.RegistryLookup) this.lookup(key).orElseThrow(() -> {
                return new IllegalStateException("Registry " + String.valueOf(key.identifier()) + " not found");
            });
        }

        default <V> RegistryOps<V> createSerializationContext(DynamicOps<V> parent) {
            return RegistryOps.create(parent, this);
        }

        static HolderLookup.Provider create(Stream<HolderLookup.RegistryLookup<?>> lookups) {
            final Map<ResourceKey<? extends Registry<?>>, HolderLookup.RegistryLookup<?>> map = (Map) lookups.collect(Collectors.toUnmodifiableMap(HolderLookup.RegistryLookup::key, (holderlookup_registrylookup) -> {
                return holderlookup_registrylookup;
            }));

            return new HolderLookup.Provider() {
                @Override
                public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                    return map.keySet().stream();
                }

                @Override
                public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                    return Optional.ofNullable((HolderLookup.RegistryLookup) map.get(key));
                }
            };
        }

        default Lifecycle allRegistriesLifecycle() {
            return (Lifecycle) this.listRegistries().map(HolderLookup.RegistryLookup::registryLifecycle).reduce(Lifecycle.stable(), Lifecycle::add);
        }
    }
}
