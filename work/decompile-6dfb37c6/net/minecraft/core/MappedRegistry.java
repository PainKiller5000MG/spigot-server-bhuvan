package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MappedRegistry<T> implements WritableRegistry<T> {

    private final ResourceKey<? extends Registry<T>> key;
    private final ObjectList<Holder.Reference<T>> byId;
    private final Reference2IntMap<T> toId;
    private final Map<Identifier, Holder.Reference<T>> byLocation;
    private final Map<ResourceKey<T>, Holder.Reference<T>> byKey;
    private final Map<T, Holder.Reference<T>> byValue;
    private final Map<ResourceKey<T>, RegistrationInfo> registrationInfos;
    private Lifecycle registryLifecycle;
    private final Map<TagKey<T>, HolderSet.Named<T>> frozenTags;
    private MappedRegistry.TagSet<T> allTags;
    private boolean frozen;
    private @Nullable Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

    @Override
    public Stream<HolderSet.Named<T>> listTags() {
        return this.getTags();
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle) {
        this(key, lifecycle, false);
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle initialLifecycle, boolean intrusiveHolders) {
        this.byId = new ObjectArrayList(256);
        this.toId = (Reference2IntMap) Util.make(new Reference2IntOpenHashMap(), (reference2intopenhashmap) -> {
            reference2intopenhashmap.defaultReturnValue(-1);
        });
        this.byLocation = new HashMap();
        this.byKey = new HashMap();
        this.byValue = new IdentityHashMap();
        this.registrationInfos = new IdentityHashMap();
        this.frozenTags = new IdentityHashMap();
        this.allTags = MappedRegistry.TagSet.<T>unbound();
        this.key = key;
        this.registryLifecycle = initialLifecycle;
        if (intrusiveHolders) {
            this.unregisteredIntrusiveHolders = new IdentityHashMap();
        }

    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return this.key;
    }

    public String toString() {
        String s = String.valueOf(this.key);

        return "Registry[" + s + " (" + String.valueOf(this.registryLifecycle) + ")]";
    }

    private void validateWrite() {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen");
        }
    }

    private void validateWrite(ResourceKey<T> key) {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen (trying to add key " + String.valueOf(key) + ")");
        }
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        this.validateWrite(key);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (this.byLocation.containsKey(key.identifier())) {
            throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + String.valueOf(key) + "' to registry"));
        } else if (this.byValue.containsKey(value)) {
            throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + String.valueOf(value) + "' to registry"));
        } else {
            Holder.Reference<T> holder_reference;

            if (this.unregisteredIntrusiveHolders != null) {
                holder_reference = (Holder.Reference) this.unregisteredIntrusiveHolders.remove(value);
                if (holder_reference == null) {
                    String s = String.valueOf(key);

                    throw new AssertionError("Missing intrusive holder for " + s + ":" + String.valueOf(value));
                }

                holder_reference.bindKey(key);
            } else {
                holder_reference = (Holder.Reference) this.byKey.computeIfAbsent(key, (resourcekey1) -> {
                    return Holder.Reference.createStandAlone(this, resourcekey1);
                });
            }

            this.byKey.put(key, holder_reference);
            this.byLocation.put(key.identifier(), holder_reference);
            this.byValue.put(value, holder_reference);
            int i = this.byId.size();

            this.byId.add(holder_reference);
            this.toId.put(value, i);
            this.registrationInfos.put(key, registrationInfo);
            this.registryLifecycle = this.registryLifecycle.add(registrationInfo.lifecycle());
            return holder_reference;
        }
    }

    @Override
    public @Nullable Identifier getKey(T thing) {
        Holder.Reference<T> holder_reference = (Holder.Reference) this.byValue.get(thing);

        return holder_reference != null ? holder_reference.key().identifier() : null;
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T thing) {
        return Optional.ofNullable((Holder.Reference) this.byValue.get(thing)).map(Holder.Reference::key);
    }

    @Override
    public int getId(@Nullable T thing) {
        return this.toId.getInt(thing);
    }

    @Override
    public @Nullable T getValue(@Nullable ResourceKey<T> key) {
        return (T) getValueFromNullable((Holder.Reference) this.byKey.get(key));
    }

    @Override
    public @Nullable T byId(int id) {
        return (T) (id >= 0 && id < this.byId.size() ? ((Holder.Reference) this.byId.get(id)).value() : null);
    }

    @Override
    public Optional<Holder.Reference<T>> get(int id) {
        return id >= 0 && id < this.byId.size() ? Optional.ofNullable((Holder.Reference) this.byId.get(id)) : Optional.empty();
    }

    @Override
    public Optional<Holder.Reference<T>> get(Identifier id) {
        return Optional.ofNullable((Holder.Reference) this.byLocation.get(id));
    }

    @Override
    public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
        return Optional.ofNullable((Holder.Reference) this.byKey.get(id));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return this.byId.isEmpty() ? Optional.empty() : Optional.of((Holder.Reference) this.byId.getFirst());
    }

    @Override
    public Holder<T> wrapAsHolder(T value) {
        Holder.Reference<T> holder_reference = (Holder.Reference) this.byValue.get(value);

        return (Holder<T>) (holder_reference != null ? holder_reference : Holder.direct(value));
    }

    private Holder.Reference<T> getOrCreateHolderOrThrow(ResourceKey<T> key) {
        return (Holder.Reference) this.byKey.computeIfAbsent(key, (resourcekey1) -> {
            if (this.unregisteredIntrusiveHolders != null) {
                throw new IllegalStateException("This registry can't create new holders without value");
            } else {
                this.validateWrite(resourcekey1);
                return Holder.Reference.createStandAlone(this, resourcekey1);
            }
        });
    }

    @Override
    public int size() {
        return this.byKey.size();
    }

    @Override
    public Optional<RegistrationInfo> registrationInfo(ResourceKey<T> element) {
        return Optional.ofNullable((RegistrationInfo) this.registrationInfos.get(element));
    }

    @Override
    public Lifecycle registryLifecycle() {
        return this.registryLifecycle;
    }

    public Iterator<T> iterator() {
        return Iterators.transform(this.byId.iterator(), Holder::value);
    }

    @Override
    public @Nullable T getValue(@Nullable Identifier key) {
        Holder.Reference<T> holder_reference = (Holder.Reference) this.byLocation.get(key);

        return (T) getValueFromNullable(holder_reference);
    }

    private static <T> @Nullable T getValueFromNullable(Holder.@Nullable Reference<T> result) {
        return (T) (result != null ? result.value() : null);
    }

    @Override
    public Set<Identifier> keySet() {
        return Collections.unmodifiableSet(this.byLocation.keySet());
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    @Override
    public Set<Map.Entry<ResourceKey<T>, T>> entrySet() {
        return Collections.unmodifiableSet(Util.mapValuesLazy(this.byKey, Holder::value).entrySet());
    }

    @Override
    public Stream<Holder.Reference<T>> listElements() {
        return this.byId.stream();
    }

    @Override
    public Stream<HolderSet.Named<T>> getTags() {
        return this.allTags.getTags();
    }

    private HolderSet.Named<T> getOrCreateTagForRegistration(TagKey<T> tag) {
        return (HolderSet.Named) this.frozenTags.computeIfAbsent(tag, this::createTag);
    }

    private HolderSet.Named<T> createTag(TagKey<T> tag) {
        return new HolderSet.Named<T>(this, tag);
    }

    @Override
    public boolean isEmpty() {
        return this.byKey.isEmpty();
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return Util.<Holder.Reference<T>>getRandomSafe(this.byId, random);
    }

    @Override
    public boolean containsKey(Identifier key) {
        return this.byLocation.containsKey(key);
    }

    @Override
    public boolean containsKey(ResourceKey<T> key) {
        return this.byKey.containsKey(key);
    }

    @Override
    public Registry<T> freeze() {
        if (this.frozen) {
            return this;
        } else {
            this.frozen = true;
            this.byValue.forEach((object, holder_reference) -> {
                holder_reference.bindValue(object);
            });
            List<Identifier> list = this.byKey.entrySet().stream().filter((entry) -> {
                return !((Holder.Reference) entry.getValue()).isBound();
            }).map((entry) -> {
                return ((ResourceKey) entry.getKey()).identifier();
            }).sorted().toList();

            if (!list.isEmpty()) {
                String s = String.valueOf(this.key());

                throw new IllegalStateException("Unbound values in registry " + s + ": " + String.valueOf(list));
            } else {
                if (this.unregisteredIntrusiveHolders != null) {
                    if (!this.unregisteredIntrusiveHolders.isEmpty()) {
                        throw new IllegalStateException("Some intrusive holders were not registered: " + String.valueOf(this.unregisteredIntrusiveHolders.values()));
                    }

                    this.unregisteredIntrusiveHolders = null;
                }

                if (this.allTags.isBound()) {
                    throw new IllegalStateException("Tags already present before freezing");
                } else {
                    List<Identifier> list1 = this.frozenTags.entrySet().stream().filter((entry) -> {
                        return !((HolderSet.Named) entry.getValue()).isBound();
                    }).map((entry) -> {
                        return ((TagKey) entry.getKey()).location();
                    }).sorted().toList();

                    if (!list1.isEmpty()) {
                        String s1 = String.valueOf(this.key());

                        throw new IllegalStateException("Unbound tags in registry " + s1 + ": " + String.valueOf(list1));
                    } else {
                        this.allTags = MappedRegistry.TagSet.<T>fromMap(this.frozenTags);
                        this.refreshTagsInHolders();
                        return this;
                    }
                }
            }
        }
    }

    @Override
    public Holder.Reference<T> createIntrusiveHolder(T value) {
        if (this.unregisteredIntrusiveHolders == null) {
            throw new IllegalStateException("This registry can't create intrusive holders");
        } else {
            this.validateWrite();
            return (Holder.Reference) this.unregisteredIntrusiveHolders.computeIfAbsent(value, (object) -> {
                return Holder.Reference.createIntrusive(this, object);
            });
        }
    }

    @Override
    public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
        return this.allTags.get(id);
    }

    private Holder.Reference<T> validateAndUnwrapTagElement(TagKey<T> id, Holder<T> value) {
        if (!value.canSerializeIn(this)) {
            String s = String.valueOf(id);

            throw new IllegalStateException("Can't create named set " + s + " containing value " + String.valueOf(value) + " from outside registry " + String.valueOf(this));
        } else if (value instanceof Holder.Reference) {
            Holder.Reference<T> holder_reference = (Holder.Reference) value;

            return holder_reference;
        } else {
            String s1 = String.valueOf(value);

            throw new IllegalStateException("Found direct holder " + s1 + " value in tag " + String.valueOf(id));
        }
    }

    @Override
    public void bindTag(TagKey<T> id, List<Holder<T>> values) {
        this.validateWrite();
        this.getOrCreateTagForRegistration(id).bind(values);
    }

    private void refreshTagsInHolders() {
        Map<Holder.Reference<T>, List<TagKey<T>>> map = new IdentityHashMap();

        this.byKey.values().forEach((holder_reference) -> {
            map.put(holder_reference, new ArrayList());
        });
        this.allTags.forEach((tagkey, holderset_named) -> {
            for (Holder<T> holder : holderset_named) {
                Holder.Reference<T> holder_reference = this.validateAndUnwrapTagElement(tagkey, holder);

                ((List) map.get(holder_reference)).add(tagkey);
            }

        });
        map.forEach(Holder.Reference::bindTags);
    }

    public void bindAllTagsToEmpty() {
        this.validateWrite();
        this.frozenTags.values().forEach((holderset_named) -> {
            holderset_named.bind(List.of());
        });
    }

    @Override
    public HolderGetter<T> createRegistrationLookup() {
        this.validateWrite();
        return new HolderGetter<T>() {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                return Optional.of(this.getOrThrow(id));
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> id) {
                return MappedRegistry.this.getOrCreateHolderOrThrow(id);
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                return Optional.of(this.getOrThrow(id));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> id) {
                return MappedRegistry.this.getOrCreateTagForRegistration(id);
            }
        };
    }

    @Override
    public Registry.PendingTags<T> prepareTagReload(TagLoader.LoadResult<T> tags) {
        if (!this.frozen) {
            throw new IllegalStateException("Invalid method used for tag loading");
        } else {
            ImmutableMap.Builder<TagKey<T>, HolderSet.Named<T>> immutablemap_builder = ImmutableMap.builder();
            final Map<TagKey<T>, List<Holder<T>>> map = new HashMap();

            tags.tags().forEach((tagkey, list) -> {
                HolderSet.Named<T> holderset_named = (HolderSet.Named) this.frozenTags.get(tagkey);

                if (holderset_named == null) {
                    holderset_named = this.createTag(tagkey);
                }

                immutablemap_builder.put(tagkey, holderset_named);
                map.put(tagkey, List.copyOf(list));
            });
            final ImmutableMap<TagKey<T>, HolderSet.Named<T>> immutablemap = immutablemap_builder.build();
            final HolderLookup.RegistryLookup<T> holderlookup_registrylookup = new HolderLookup.RegistryLookup.Delegate<T>() {
                @Override
                public HolderLookup.RegistryLookup<T> parent() {
                    return MappedRegistry.this;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                    return Optional.ofNullable((HolderSet.Named) immutablemap.get(id));
                }

                @Override
                public Stream<HolderSet.Named<T>> listTags() {
                    return immutablemap.values().stream();
                }
            };

            return new Registry.PendingTags<T>() {
                @Override
                public ResourceKey<? extends Registry<? extends T>> key() {
                    return MappedRegistry.this.key();
                }

                @Override
                public int size() {
                    return map.size();
                }

                @Override
                public HolderLookup.RegistryLookup<T> lookup() {
                    return holderlookup_registrylookup;
                }

                @Override
                public void apply() {
                    immutablemap.forEach((tagkey, holderset_named) -> {
                        List<Holder<T>> list = (List) map.getOrDefault(tagkey, List.of());

                        holderset_named.bind(list);
                    });
                    MappedRegistry.this.allTags = MappedRegistry.TagSet.<T>fromMap(immutablemap);
                    MappedRegistry.this.refreshTagsInHolders();
                }
            };
        }
    }

    private interface TagSet<T> {

        static <T> MappedRegistry.TagSet<T> unbound() {
            return new MappedRegistry.TagSet<T>() {
                @Override
                public boolean isBound() {
                    return false;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                    throw new IllegalStateException("Tags not bound, trying to access " + String.valueOf(id));
                }

                @Override
                public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
                    throw new IllegalStateException("Tags not bound");
                }

                @Override
                public Stream<HolderSet.Named<T>> getTags() {
                    throw new IllegalStateException("Tags not bound");
                }
            };
        }

        static <T> MappedRegistry.TagSet<T> fromMap(final Map<TagKey<T>, HolderSet.Named<T>> tags) {
            return new MappedRegistry.TagSet<T>() {
                @Override
                public boolean isBound() {
                    return true;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                    return Optional.ofNullable((HolderSet.Named) tags.get(id));
                }

                @Override
                public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
                    tags.forEach(action);
                }

                @Override
                public Stream<HolderSet.Named<T>> getTags() {
                    return tags.values().stream();
                }
            };
        }

        boolean isBound();

        Optional<HolderSet.Named<T>> get(TagKey<T> id);

        void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action);

        Stream<HolderSet.Named<T>> getTags();
    }
}
