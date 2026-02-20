package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class PatchedDataComponentMap implements DataComponentMap {

    private final DataComponentMap prototype;
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;
    private boolean copyOnWrite;

    public PatchedDataComponentMap(DataComponentMap prototype) {
        this(prototype, Reference2ObjectMaps.emptyMap(), true);
    }

    private PatchedDataComponentMap(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch, boolean copyOnWrite) {
        this.prototype = prototype;
        this.patch = patch;
        this.copyOnWrite = copyOnWrite;
    }

    public static PatchedDataComponentMap fromPatch(DataComponentMap prototype, DataComponentPatch patch) {
        if (isPatchSanitized(prototype, patch.map)) {
            return new PatchedDataComponentMap(prototype, patch.map, true);
        } else {
            PatchedDataComponentMap patcheddatacomponentmap = new PatchedDataComponentMap(prototype);

            patcheddatacomponentmap.applyPatch(patch);
            return patcheddatacomponentmap;
        }
    }

    private static boolean isPatchSanitized(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch) {
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(patch).iterator();

        while (objectiterator.hasNext()) {
            Map.Entry<DataComponentType<?>, Optional<?>> map_entry = (Entry) objectiterator.next();
            Object object = prototype.get((DataComponentType) map_entry.getKey());
            Optional<?> optional = (Optional) map_entry.getValue();

            if (optional.isPresent() && optional.get().equals(object)) {
                return false;
            }

            if (optional.isEmpty() && object == null) {
                return false;
            }
        }

        return true;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        Optional<? extends T> optional = (Optional) this.patch.get(type);

        return (T) (optional != null ? optional.orElse((Object) null) : this.prototype.get(type));
    }

    public boolean hasNonDefault(DataComponentType<?> type) {
        return this.patch.containsKey(type);
    }

    public <T> @Nullable T set(DataComponentType<T> type, @Nullable T value) {
        this.ensureMapOwnership();
        T t1 = (T) this.prototype.get(type);
        Optional<T> optional;

        if (Objects.equals(value, t1)) {
            optional = (Optional) this.patch.remove(type);
        } else {
            optional = (Optional) this.patch.put(type, Optional.ofNullable(value));
        }

        return (T) (optional != null ? optional.orElse(t1) : t1);
    }

    public <T> @Nullable T set(TypedDataComponent<T> value) {
        return (T) this.set(value.type(), value.value());
    }

    public <T> @Nullable T remove(DataComponentType<? extends T> type) {
        this.ensureMapOwnership();
        T t0 = (T) this.prototype.get(type);
        Optional<? extends T> optional;

        if (t0 != null) {
            optional = (Optional) this.patch.put(type, Optional.empty());
        } else {
            optional = (Optional) this.patch.remove(type);
        }

        return (T) (optional != null ? optional.orElse((Object) null) : t0);
    }

    public void applyPatch(DataComponentPatch patch) {
        this.ensureMapOwnership();
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(patch.map).iterator();

        while (objectiterator.hasNext()) {
            Map.Entry<DataComponentType<?>, Optional<?>> map_entry = (Entry) objectiterator.next();

            this.applyPatch((DataComponentType) map_entry.getKey(), (Optional) map_entry.getValue());
        }

    }

    private void applyPatch(DataComponentType<?> type, Optional<?> value) {
        Object object = this.prototype.get(type);

        if (value.isPresent()) {
            if (value.get().equals(object)) {
                this.patch.remove(type);
            } else {
                this.patch.put(type, value);
            }
        } else if (object != null) {
            this.patch.put(type, Optional.empty());
        } else {
            this.patch.remove(type);
        }

    }

    public void restorePatch(DataComponentPatch patch) {
        this.ensureMapOwnership();
        this.patch.clear();
        this.patch.putAll(patch.map);
    }

    public void clearPatch() {
        this.ensureMapOwnership();
        this.patch.clear();
    }

    public void setAll(DataComponentMap components) {
        for (TypedDataComponent<?> typeddatacomponent : components) {
            typeddatacomponent.applyTo(this);
        }

    }

    private void ensureMapOwnership() {
        if (this.copyOnWrite) {
            this.patch = new Reference2ObjectArrayMap(this.patch);
            this.copyOnWrite = false;
        }

    }

    @Override
    public Set<DataComponentType<?>> keySet() {
        if (this.patch.isEmpty()) {
            return this.prototype.keySet();
        } else {
            Set<DataComponentType<?>> set = new ReferenceArraySet(this.prototype.keySet());
            ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(this.patch).iterator();

            while (objectiterator.hasNext()) {
                Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
                Optional<?> optional = (Optional) reference2objectmap_entry.getValue();

                if (optional.isPresent()) {
                    set.add((DataComponentType) reference2objectmap_entry.getKey());
                } else {
                    set.remove(reference2objectmap_entry.getKey());
                }
            }

            return set;
        }
    }

    @Override
    public Iterator<TypedDataComponent<?>> iterator() {
        if (this.patch.isEmpty()) {
            return this.prototype.iterator();
        } else {
            List<TypedDataComponent<?>> list = new ArrayList(this.patch.size() + this.prototype.size());
            ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(this.patch).iterator();

            while (objectiterator.hasNext()) {
                Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();

                if (((Optional) reference2objectmap_entry.getValue()).isPresent()) {
                    list.add(TypedDataComponent.createUnchecked((DataComponentType) reference2objectmap_entry.getKey(), ((Optional) reference2objectmap_entry.getValue()).get()));
                }
            }

            for (TypedDataComponent<?> typeddatacomponent : this.prototype) {
                if (!this.patch.containsKey(typeddatacomponent.type())) {
                    list.add(typeddatacomponent);
                }
            }

            return list.iterator();
        }
    }

    @Override
    public int size() {
        int i = this.prototype.size();
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(this.patch).iterator();

        while (objectiterator.hasNext()) {
            Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
            boolean flag = ((Optional) reference2objectmap_entry.getValue()).isPresent();
            boolean flag1 = this.prototype.has((DataComponentType) reference2objectmap_entry.getKey());

            if (flag != flag1) {
                i += flag ? 1 : -1;
            }
        }

        return i;
    }

    public DataComponentPatch asPatch() {
        if (this.patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            this.copyOnWrite = true;
            return new DataComponentPatch(this.patch);
        }
    }

    public PatchedDataComponentMap copy() {
        this.copyOnWrite = true;
        return new PatchedDataComponentMap(this.prototype, this.patch, true);
    }

    public DataComponentMap toImmutableMap() {
        return (DataComponentMap) (this.patch.isEmpty() ? this.prototype : this.copy());
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof PatchedDataComponentMap) {
                PatchedDataComponentMap patcheddatacomponentmap = (PatchedDataComponentMap) obj;

                if (this.prototype.equals(patcheddatacomponentmap.prototype) && this.patch.equals(patcheddatacomponentmap.patch)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.prototype.hashCode() + this.patch.hashCode() * 31;
    }

    public String toString() {
        Stream stream = this.stream().map(TypedDataComponent::toString);

        return "{" + (String) stream.collect(Collectors.joining(", ")) + "}";
    }
}
