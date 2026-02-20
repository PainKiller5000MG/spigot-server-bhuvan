package net.minecraft.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;

public class LayeredRegistryAccess<T> {

    private final List<T> keys;
    private final List<RegistryAccess.Frozen> values;
    private final RegistryAccess.Frozen composite;

    public LayeredRegistryAccess(List<T> keys) {
        this(keys, (List) Util.make(() -> {
            RegistryAccess.Frozen[] aregistryaccess_frozen = new RegistryAccess.Frozen[keys.size()];

            Arrays.fill(aregistryaccess_frozen, RegistryAccess.EMPTY);
            return Arrays.asList(aregistryaccess_frozen);
        }));
    }

    private LayeredRegistryAccess(List<T> keys, List<RegistryAccess.Frozen> values) {
        this.keys = List.copyOf(keys);
        this.values = List.copyOf(values);
        this.composite = (new RegistryAccess.ImmutableRegistryAccess(collectRegistries(values.stream()))).freeze();
    }

    private int getLayerIndexOrThrow(T layer) {
        int i = this.keys.indexOf(layer);

        if (i == -1) {
            String s = String.valueOf(layer);

            throw new IllegalStateException("Can't find " + s + " inside " + String.valueOf(this.keys));
        } else {
            return i;
        }
    }

    public RegistryAccess.Frozen getLayer(T layer) {
        int i = this.getLayerIndexOrThrow(layer);

        return (RegistryAccess.Frozen) this.values.get(i);
    }

    public RegistryAccess.Frozen getAccessForLoading(T forLayer) {
        int i = this.getLayerIndexOrThrow(forLayer);

        return this.getCompositeAccessForLayers(0, i);
    }

    public RegistryAccess.Frozen getAccessFrom(T forLayer) {
        int i = this.getLayerIndexOrThrow(forLayer);

        return this.getCompositeAccessForLayers(i, this.values.size());
    }

    private RegistryAccess.Frozen getCompositeAccessForLayers(int from, int to) {
        return (new RegistryAccess.ImmutableRegistryAccess(collectRegistries(this.values.subList(from, to).stream()))).freeze();
    }

    public LayeredRegistryAccess<T> replaceFrom(T fromLayer, RegistryAccess.Frozen... layers) {
        return this.replaceFrom(fromLayer, Arrays.asList(layers));
    }

    public LayeredRegistryAccess<T> replaceFrom(T fromLayer, List<RegistryAccess.Frozen> layers) {
        int i = this.getLayerIndexOrThrow(fromLayer);

        if (layers.size() > this.values.size() - i) {
            throw new IllegalStateException("Too many values to replace");
        } else {
            List<RegistryAccess.Frozen> list1 = new ArrayList();

            for (int j = 0; j < i; ++j) {
                list1.add((RegistryAccess.Frozen) this.values.get(j));
            }

            list1.addAll(layers);

            while (((List) list1).size() < this.values.size()) {
                list1.add(RegistryAccess.EMPTY);
            }

            return new LayeredRegistryAccess<T>(this.keys, list1);
        }
    }

    public RegistryAccess.Frozen compositeAccess() {
        return this.composite;
    }

    private static Map<ResourceKey<? extends Registry<?>>, Registry<?>> collectRegistries(Stream<? extends RegistryAccess> registries) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> map = new HashMap();

        registries.forEach((registryaccess) -> {
            registryaccess.registries().forEach((registryaccess_registryentry) -> {
                if (map.put(registryaccess_registryentry.key(), registryaccess_registryentry.value()) != null) {
                    throw new IllegalStateException("Duplicated registry " + String.valueOf(registryaccess_registryentry.key()));
                }
            });
        });
        return map;
    }
}
