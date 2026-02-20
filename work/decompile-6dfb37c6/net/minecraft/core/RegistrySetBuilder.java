package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class RegistrySetBuilder {

    private final List<RegistrySetBuilder.RegistryStub<?>> entries = new ArrayList();

    public RegistrySetBuilder() {}

    private static <T> HolderGetter<T> wrapContextLookup(final HolderLookup.RegistryLookup<T> original) {
        return new RegistrySetBuilder.EmptyTagLookup<T>(original) {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                return original.get(id);
            }
        };
    }

    private static <T> HolderLookup.RegistryLookup<T> lookupFromMap(final ResourceKey<? extends Registry<? extends T>> key, final Lifecycle lifecycle, HolderOwner<T> owner, final Map<ResourceKey<T>, Holder.Reference<T>> entries) {
        return new RegistrySetBuilder.EmptyTagRegistryLookup<T>(owner) {
            @Override
            public ResourceKey<? extends Registry<? extends T>> key() {
                return key;
            }

            @Override
            public Lifecycle registryLifecycle() {
                return lifecycle;
            }

            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                return Optional.ofNullable((Holder.Reference) entries.get(id));
            }

            @Override
            public Stream<Holder.Reference<T>> listElements() {
                return entries.values().stream();
            }
        };
    }

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        this.entries.add(new RegistrySetBuilder.RegistryStub(key, lifecycle, bootstrap));
        return this;
    }

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> key, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        return this.add(key, Lifecycle.stable(), bootstrap);
    }

    private RegistrySetBuilder.BuildState createState(RegistryAccess context) {
        RegistrySetBuilder.BuildState registrysetbuilder_buildstate = RegistrySetBuilder.BuildState.create(context, this.entries.stream().map(RegistrySetBuilder.RegistryStub::key));

        this.entries.forEach((registrysetbuilder_registrystub) -> {
            registrysetbuilder_registrystub.apply(registrysetbuilder_buildstate);
        });
        return registrysetbuilder_buildstate;
    }

    private static HolderLookup.Provider buildProviderWithContext(RegistrySetBuilder.UniversalOwner owner, RegistryAccess context, Stream<HolderLookup.RegistryLookup<?>> newRegistries) {
        record 1Entry<T>(HolderLookup.RegistryLookup<T> lookup, RegistryOps.RegistryInfo<T> opsInfo) {

            public static <T> 1Entry<T> createForContextRegistry(HolderLookup.RegistryLookup<T> registryLookup) {
                return new 1Entry<T>(new RegistrySetBuilder.EmptyTagLookupWrapper(registryLookup, registryLookup), RegistryOps.RegistryInfo.fromRegistryLookup(registryLookup));
            }

            public static <T> 1Entry<T> createForNewRegistry(RegistrySetBuilder.UniversalOwner owner, HolderLookup.RegistryLookup<T> registryLookup) {
                return new 1Entry<T>(new RegistrySetBuilder.EmptyTagLookupWrapper(owner.cast(), registryLookup), new RegistryOps.RegistryInfo(owner.cast(), registryLookup, registryLookup.registryLifecycle()));
            }
        }

        final Map<ResourceKey<? extends Registry<?>>, 1Entry<?>> map = new HashMap();

        context.registries().forEach((registryaccess_registryentry) -> {
            map.put(registryaccess_registryentry.key(), 1Entry.createForContextRegistry(registryaccess_registryentry.value()));
        });
        newRegistries.forEach((holderlookup_registrylookup) -> {
            map.put(holderlookup_registrylookup.key(), 1Entry.createForNewRegistry(owner, holderlookup_registrylookup));
        });
        return new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return map.keySet().stream();
            }

            private <T> Optional<1Entry<T>> getEntry(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.ofNullable((1Entry)map.get(key));
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return this.getEntry(key).map(1Entry::lookup);
            }

            @Override
            public <V> RegistryOps<V> createSerializationContext(DynamicOps<V> parent) {
                return RegistryOps.create(parent, new RegistryOps.RegistryInfoLookup() {
                    @Override
                    public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                        return getEntry(registryKey).map(1Entry::opsInfo);
                    }
                });
            }
        };
    }

    public HolderLookup.Provider build(RegistryAccess context) {
        RegistrySetBuilder.BuildState registrysetbuilder_buildstate = this.createState(context);
        Stream<HolderLookup.RegistryLookup<?>> stream = this.entries.stream().map((registrysetbuilder_registrystub) -> {
            return registrysetbuilder_registrystub.collectRegisteredValues(registrysetbuilder_buildstate).buildAsLookup(registrysetbuilder_buildstate.owner);
        });
        HolderLookup.Provider holderlookup_provider = buildProviderWithContext(registrysetbuilder_buildstate.owner, context, stream);

        registrysetbuilder_buildstate.reportNotCollectedHolders();
        registrysetbuilder_buildstate.reportUnclaimedRegisteredValues();
        registrysetbuilder_buildstate.throwOnError();
        return holderlookup_provider;
    }

    private HolderLookup.Provider createLazyFullPatchedRegistries(RegistryAccess context, HolderLookup.Provider fallbackProvider, Cloner.Factory clonerFactory, Map<ResourceKey<? extends Registry<?>>, RegistrySetBuilder.RegistryContents<?>> newRegistries, HolderLookup.Provider patchOnlyRegistries) {
        RegistrySetBuilder.UniversalOwner registrysetbuilder_universalowner = new RegistrySetBuilder.UniversalOwner();
        MutableObject<HolderLookup.Provider> mutableobject = new MutableObject();
        List<HolderLookup.RegistryLookup<?>> list = (List) newRegistries.keySet().stream().map((resourcekey) -> {
            return this.createLazyFullPatchedRegistries(registrysetbuilder_universalowner, clonerFactory, resourcekey, patchOnlyRegistries, fallbackProvider, mutableobject);
        }).collect(Collectors.toUnmodifiableList());
        HolderLookup.Provider holderlookup_provider2 = buildProviderWithContext(registrysetbuilder_universalowner, context, list.stream());

        mutableobject.setValue(holderlookup_provider2);
        return holderlookup_provider2;
    }

    private <T> HolderLookup.RegistryLookup<T> createLazyFullPatchedRegistries(HolderOwner<T> owner, Cloner.Factory clonerFactory, ResourceKey<? extends Registry<? extends T>> registryKey, HolderLookup.Provider patchProvider, HolderLookup.Provider fallbackProvider, MutableObject<HolderLookup.Provider> targetProvider) {
        Cloner<T> cloner = clonerFactory.<T>cloner(registryKey);

        if (cloner == null) {
            throw new NullPointerException("No cloner for " + String.valueOf(registryKey.identifier()));
        } else {
            Map<ResourceKey<T>, Holder.Reference<T>> map = new HashMap();
            HolderLookup.RegistryLookup<T> holderlookup_registrylookup = patchProvider.lookupOrThrow(registryKey);

            holderlookup_registrylookup.listElements().forEach((holder_reference) -> {
                ResourceKey<T> resourcekey1 = holder_reference.key();
                RegistrySetBuilder.LazyHolder<T> registrysetbuilder_lazyholder = new RegistrySetBuilder.LazyHolder<T>(owner, resourcekey1);

                registrysetbuilder_lazyholder.supplier = () -> {
                    return cloner.clone(holder_reference.value(), patchProvider, (HolderLookup.Provider) targetProvider.get());
                };
                map.put(resourcekey1, registrysetbuilder_lazyholder);
            });
            HolderLookup.RegistryLookup<T> holderlookup_registrylookup1 = fallbackProvider.lookupOrThrow(registryKey);

            holderlookup_registrylookup1.listElements().forEach((holder_reference) -> {
                ResourceKey<T> resourcekey1 = holder_reference.key();

                map.computeIfAbsent(resourcekey1, (resourcekey2) -> {
                    RegistrySetBuilder.LazyHolder<T> registrysetbuilder_lazyholder = new RegistrySetBuilder.LazyHolder<T>(owner, resourcekey1);

                    registrysetbuilder_lazyholder.supplier = () -> {
                        return cloner.clone(holder_reference.value(), fallbackProvider, (HolderLookup.Provider) targetProvider.get());
                    };
                    return registrysetbuilder_lazyholder;
                });
            });
            Lifecycle lifecycle = holderlookup_registrylookup.registryLifecycle().add(holderlookup_registrylookup1.registryLifecycle());

            return lookupFromMap(registryKey, lifecycle, owner, map);
        }
    }

    public RegistrySetBuilder.PatchedRegistries buildPatch(RegistryAccess context, HolderLookup.Provider fallbackProvider, Cloner.Factory clonerFactory) {
        RegistrySetBuilder.BuildState registrysetbuilder_buildstate = this.createState(context);
        Map<ResourceKey<? extends Registry<?>>, RegistrySetBuilder.RegistryContents<?>> map = new HashMap();

        this.entries.stream().map((registrysetbuilder_registrystub) -> {
            return registrysetbuilder_registrystub.collectRegisteredValues(registrysetbuilder_buildstate);
        }).forEach((registrysetbuilder_registrycontents) -> {
            map.put(registrysetbuilder_registrycontents.key, registrysetbuilder_registrycontents);
        });
        Set<ResourceKey<? extends Registry<?>>> set = (Set) context.listRegistryKeys().collect(Collectors.toUnmodifiableSet());

        fallbackProvider.listRegistryKeys().filter((resourcekey) -> {
            return !set.contains(resourcekey);
        }).forEach((resourcekey) -> {
            map.putIfAbsent(resourcekey, new RegistrySetBuilder.RegistryContents(resourcekey, Lifecycle.stable(), Map.of()));
        });
        Stream<HolderLookup.RegistryLookup<?>> stream = map.values().stream().map((registrysetbuilder_registrycontents) -> {
            return registrysetbuilder_registrycontents.buildAsLookup(registrysetbuilder_buildstate.owner);
        });
        HolderLookup.Provider holderlookup_provider1 = buildProviderWithContext(registrysetbuilder_buildstate.owner, context, stream);

        registrysetbuilder_buildstate.reportUnclaimedRegisteredValues();
        registrysetbuilder_buildstate.throwOnError();
        HolderLookup.Provider holderlookup_provider2 = this.createLazyFullPatchedRegistries(context, fallbackProvider, clonerFactory, map, holderlookup_provider1);

        return new RegistrySetBuilder.PatchedRegistries(holderlookup_provider2, holderlookup_provider1);
    }

    private static class LazyHolder<T> extends Holder.Reference<T> {

        private @Nullable Supplier<T> supplier;

        protected LazyHolder(HolderOwner<T> owner, @Nullable ResourceKey<T> key) {
            super(Holder.Reference.Type.STAND_ALONE, owner, key, (Object) null);
        }

        @Override
        protected void bindValue(T value) {
            super.bindValue(value);
            this.supplier = null;
        }

        @Override
        public T value() {
            if (this.supplier != null) {
                this.bindValue(this.supplier.get());
            }

            return (T) super.value();
        }
    }

    private abstract static class EmptyTagLookup<T> implements HolderGetter<T> {

        protected final HolderOwner<T> owner;

        protected EmptyTagLookup(HolderOwner<T> owner) {
            this.owner = owner;
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
            return Optional.of(HolderSet.emptyNamed(this.owner, id));
        }
    }

    private abstract static class EmptyTagRegistryLookup<T> extends RegistrySetBuilder.EmptyTagLookup<T> implements HolderLookup.RegistryLookup<T> {

        protected EmptyTagRegistryLookup(HolderOwner<T> owner) {
            super(owner);
        }

        @Override
        public Stream<HolderSet.Named<T>> listTags() {
            throw new UnsupportedOperationException("Tags are not available in datagen");
        }
    }

    private static class EmptyTagLookupWrapper<T> extends RegistrySetBuilder.EmptyTagRegistryLookup<T> implements HolderLookup.RegistryLookup.Delegate<T> {

        private final HolderLookup.RegistryLookup<T> parent;

        private EmptyTagLookupWrapper(HolderOwner<T> owner, HolderLookup.RegistryLookup<T> parent) {
            super(owner);
            this.parent = parent;
        }

        @Override
        public HolderLookup.RegistryLookup<T> parent() {
            return this.parent;
        }
    }

    private static class UniversalOwner implements HolderOwner<Object> {

        private UniversalOwner() {}

        public <T> HolderOwner<T> cast() {
            return this;
        }
    }

    private static class UniversalLookup extends RegistrySetBuilder.EmptyTagLookup<Object> {

        private final Map<ResourceKey<Object>, Holder.Reference<Object>> holders = new HashMap();

        public UniversalLookup(HolderOwner<Object> owner) {
            super(owner);
        }

        @Override
        public Optional<Holder.Reference<Object>> get(ResourceKey<Object> id) {
            return Optional.of(this.getOrCreate(id));
        }

        private <T> Holder.Reference<T> getOrCreate(ResourceKey<T> id) {
            return (Holder.Reference) this.holders.computeIfAbsent(id, (resourcekey1) -> {
                return Holder.Reference.createStandAlone(this.owner, resourcekey1);
            });
        }
    }

    private static record RegisteredValue<T>(T value, Lifecycle lifecycle) {

    }

    private static record BuildState(RegistrySetBuilder.UniversalOwner owner, RegistrySetBuilder.UniversalLookup lookup, Map<Identifier, HolderGetter<?>> registries, Map<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>> registeredValues, List<RuntimeException> errors) {

        public static RegistrySetBuilder.BuildState create(RegistryAccess context, Stream<ResourceKey<? extends Registry<?>>> newRegistries) {
            RegistrySetBuilder.UniversalOwner registrysetbuilder_universalowner = new RegistrySetBuilder.UniversalOwner();
            List<RuntimeException> list = new ArrayList();
            RegistrySetBuilder.UniversalLookup registrysetbuilder_universallookup = new RegistrySetBuilder.UniversalLookup(registrysetbuilder_universalowner);
            ImmutableMap.Builder<Identifier, HolderGetter<?>> immutablemap_builder = ImmutableMap.builder();

            context.registries().forEach((registryaccess_registryentry) -> {
                immutablemap_builder.put(registryaccess_registryentry.key().identifier(), RegistrySetBuilder.wrapContextLookup(registryaccess_registryentry.value()));
            });
            newRegistries.forEach((resourcekey) -> {
                immutablemap_builder.put(resourcekey.identifier(), registrysetbuilder_universallookup);
            });
            return new RegistrySetBuilder.BuildState(registrysetbuilder_universalowner, registrysetbuilder_universallookup, immutablemap_builder.build(), new HashMap(), list);
        }

        public <T> BootstrapContext<T> bootstrapContext() {
            return new BootstrapContext<T>() {
                @Override
                public Holder.Reference<T> register(ResourceKey<T> key, T value, Lifecycle lifecycle) {
                    RegistrySetBuilder.RegisteredValue<?> registrysetbuilder_registeredvalue = (RegistrySetBuilder.RegisteredValue) BuildState.this.registeredValues.put(key, new RegistrySetBuilder.RegisteredValue(value, lifecycle));

                    if (registrysetbuilder_registeredvalue != null) {
                        List list = BuildState.this.errors;
                        String s = String.valueOf(key);

                        list.add(new IllegalStateException("Duplicate registration for " + s + ", new=" + String.valueOf(value) + ", old=" + String.valueOf(registrysetbuilder_registeredvalue.value)));
                    }

                    return BuildState.this.lookup.<T>getOrCreate(key);
                }

                @Override
                public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                    return (HolderGetter) BuildState.this.registries.getOrDefault(key.identifier(), BuildState.this.lookup);
                }
            };
        }

        public void reportUnclaimedRegisteredValues() {
            this.registeredValues.forEach((resourcekey, registrysetbuilder_registeredvalue) -> {
                List list = this.errors;
                String s = String.valueOf(registrysetbuilder_registeredvalue.value);

                list.add(new IllegalStateException("Orpaned value " + s + " for key " + String.valueOf(resourcekey)));
            });
        }

        public void reportNotCollectedHolders() {
            for (ResourceKey<Object> resourcekey : this.lookup.holders.keySet()) {
                this.errors.add(new IllegalStateException("Unreferenced key: " + String.valueOf(resourcekey)));
            }

        }

        public void throwOnError() {
            if (!this.errors.isEmpty()) {
                IllegalStateException illegalstateexception = new IllegalStateException("Errors during registry creation");

                for (RuntimeException runtimeexception : this.errors) {
                    illegalstateexception.addSuppressed(runtimeexception);
                }

                throw illegalstateexception;
            }
        }
    }

    private static record ValueAndHolder<T>(RegistrySetBuilder.RegisteredValue<T> value, Optional<Holder.Reference<T>> holder) {

    }

    private static record RegistryStub<T>(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {

        private void apply(RegistrySetBuilder.BuildState state) {
            this.bootstrap.run(state.bootstrapContext());
        }

        public RegistrySetBuilder.RegistryContents<T> collectRegisteredValues(RegistrySetBuilder.BuildState state) {
            Map<ResourceKey<T>, RegistrySetBuilder.ValueAndHolder<T>> map = new HashMap();
            Iterator<Map.Entry<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>>> iterator = state.registeredValues.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>> map_entry = (Entry) iterator.next();
                ResourceKey<?> resourcekey = (ResourceKey) map_entry.getKey();

                if (resourcekey.isFor(this.key)) {
                    RegistrySetBuilder.RegisteredValue<T> registrysetbuilder_registeredvalue = (RegistrySetBuilder.RegisteredValue) map_entry.getValue();
                    Holder.Reference<T> holder_reference = (Holder.Reference) state.lookup.holders.remove(resourcekey);

                    map.put(resourcekey, new RegistrySetBuilder.ValueAndHolder(registrysetbuilder_registeredvalue, Optional.ofNullable(holder_reference)));
                    iterator.remove();
                }
            }

            return new RegistrySetBuilder.RegistryContents<T>(this.key, this.lifecycle, map);
        }
    }

    private static record RegistryContents<T>(ResourceKey<? extends Registry<? extends T>> key, Lifecycle lifecycle, Map<ResourceKey<T>, RegistrySetBuilder.ValueAndHolder<T>> values) {

        public HolderLookup.RegistryLookup<T> buildAsLookup(RegistrySetBuilder.UniversalOwner owner) {
            Map<ResourceKey<T>, Holder.Reference<T>> map = (Map) this.values.entrySet().stream().collect(Collectors.toUnmodifiableMap(Entry::getKey, (entry) -> {
                RegistrySetBuilder.ValueAndHolder<T> registrysetbuilder_valueandholder = (RegistrySetBuilder.ValueAndHolder) entry.getValue();
                Holder.Reference<T> holder_reference = (Holder.Reference) registrysetbuilder_valueandholder.holder().orElseGet(() -> {
                    return Holder.Reference.createStandAlone(owner.cast(), (ResourceKey) entry.getKey());
                });

                holder_reference.bindValue(registrysetbuilder_valueandholder.value().value());
                return holder_reference;
            }));

            return RegistrySetBuilder.<T>lookupFromMap(this.key, this.lifecycle, owner.cast(), map);
        }
    }

    public static record PatchedRegistries(HolderLookup.Provider full, HolderLookup.Provider patches) {

    }

    @FunctionalInterface
    public interface RegistryBootstrap<T> {

        void run(BootstrapContext<T> registry);
    }
}
