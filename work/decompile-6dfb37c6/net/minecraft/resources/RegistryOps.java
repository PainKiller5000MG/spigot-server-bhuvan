package net.minecraft.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.util.ExtraCodecs;

public class RegistryOps<T> extends DelegatingOps<T> {

    private final RegistryOps.RegistryInfoLookup lookupProvider;

    public static <T> RegistryOps<T> create(DynamicOps<T> parent, HolderLookup.Provider lookupProvider) {
        return create(parent, (RegistryOps.RegistryInfoLookup) (new RegistryOps.HolderLookupAdapter(lookupProvider)));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> parent, RegistryOps.RegistryInfoLookup lookupProvider) {
        return new RegistryOps<T>(parent, lookupProvider);
    }

    public static <T> Dynamic<T> injectRegistryContext(Dynamic<T> dynamic, HolderLookup.Provider lookupProvider) {
        return new Dynamic(lookupProvider.createSerializationContext(dynamic.getOps()), dynamic.getValue());
    }

    private RegistryOps(DynamicOps<T> parent, RegistryOps.RegistryInfoLookup lookupProvider) {
        super(parent);
        this.lookupProvider = lookupProvider;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> parent) {
        return parent == this.delegate ? this : new RegistryOps(parent, this.lookupProvider);
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::getter);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            RegistryOps<?> registryops = (RegistryOps) obj;

            return this.delegate.equals(registryops.delegate) && this.lookupProvider.equals(registryops.lookupProvider);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.delegate.hashCode() * 31 + this.lookupProvider.hashCode();
    }

    public static <E, O> RecordCodecBuilder<O, HolderGetter<E>> retrieveGetter(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return ExtraCodecs.retrieveContext((dynamicops) -> {
            if (dynamicops instanceof RegistryOps<?> registryops) {
                return (DataResult) registryops.lookupProvider.lookup(registryKey).map((registryops_registryinfo) -> {
                    return DataResult.success(registryops_registryinfo.getter(), registryops_registryinfo.elementsLifecycle());
                }).orElseGet(() -> {
                    return DataResult.error(() -> {
                        return "Unknown registry: " + String.valueOf(registryKey);
                    });
                });
            } else {
                return DataResult.error(() -> {
                    return "Not a registry ops";
                });
            }
        }).forGetter((object) -> {
            return null;
        });
    }

    public static <E, O> RecordCodecBuilder<O, Holder.Reference<E>> retrieveElement(ResourceKey<E> key) {
        ResourceKey<? extends Registry<E>> resourcekey1 = ResourceKey.createRegistryKey(key.registry());

        return ExtraCodecs.retrieveContext((dynamicops) -> {
            if (dynamicops instanceof RegistryOps<?> registryops) {
                return (DataResult) registryops.lookupProvider.lookup(resourcekey1).flatMap((registryops_registryinfo) -> {
                    return registryops_registryinfo.getter().get(key);
                }).map(DataResult::success).orElseGet(() -> {
                    return DataResult.error(() -> {
                        return "Can't find value: " + String.valueOf(key);
                    });
                });
            } else {
                return DataResult.error(() -> {
                    return "Not a registry ops";
                });
            }
        }).forGetter((object) -> {
            return null;
        });
    }

    public static record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {

        public static <T> RegistryOps.RegistryInfo<T> fromRegistryLookup(HolderLookup.RegistryLookup<T> registry) {
            return new RegistryOps.RegistryInfo<T>(registry, registry, registry.registryLifecycle());
        }
    }

    private static final class HolderLookupAdapter implements RegistryOps.RegistryInfoLookup {

        private final HolderLookup.Provider lookupProvider;
        private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap();

        public HolderLookupAdapter(HolderLookup.Provider lookupProvider) {
            this.lookupProvider = lookupProvider;
        }

        @Override
        public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryKey) {
            return (Optional) this.lookups.computeIfAbsent(registryKey, this::createLookup);
        }

        private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> key) {
            return this.lookupProvider.lookup(key).map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else {
                boolean flag;

                if (obj instanceof RegistryOps.HolderLookupAdapter) {
                    RegistryOps.HolderLookupAdapter registryops_holderlookupadapter = (RegistryOps.HolderLookupAdapter) obj;

                    if (this.lookupProvider.equals(registryops_holderlookupadapter.lookupProvider)) {
                        flag = true;
                        return flag;
                    }
                }

                flag = false;
                return flag;
            }
        }

        public int hashCode() {
            return this.lookupProvider.hashCode();
        }
    }

    public interface RegistryInfoLookup {

        <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey);
    }
}
