package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DependencySorter<K, V extends DependencySorter.Entry<K>> {

    private final Map<K, V> contents = new HashMap();

    public DependencySorter() {}

    public DependencySorter<K, V> addEntry(K id, V value) {
        this.contents.put(id, value);
        return this;
    }

    private void visitDependenciesAndElement(Multimap<K, K> dependencies, Set<K> alreadyVisited, K id, BiConsumer<K, V> output) {
        if (alreadyVisited.add(id)) {
            dependencies.get(id).forEach((object) -> {
                this.visitDependenciesAndElement(dependencies, alreadyVisited, object, output);
            });
            V v0 = (V) (this.contents.get(id));

            if (v0 != null) {
                output.accept(id, v0);
            }

        }
    }

    private static <K> boolean isCyclic(Multimap<K, K> directDependencies, K from, K to) {
        Collection<K> collection = directDependencies.get(to);

        return collection.contains(from) ? true : collection.stream().anyMatch((object) -> {
            return isCyclic(directDependencies, from, object);
        });
    }

    private static <K> void addDependencyIfNotCyclic(Multimap<K, K> directDependencies, K from, K to) {
        if (!isCyclic(directDependencies, from, to)) {
            directDependencies.put(from, to);
        }

    }

    public void orderByDependencies(BiConsumer<K, V> output) {
        Multimap<K, K> multimap = HashMultimap.create();

        this.contents.forEach((object, dependencysorter_entry) -> {
            dependencysorter_entry.visitRequiredDependencies((object1) -> {
                addDependencyIfNotCyclic(multimap, object, object1);
            });
        });
        this.contents.forEach((object, dependencysorter_entry) -> {
            dependencysorter_entry.visitOptionalDependencies((object1) -> {
                addDependencyIfNotCyclic(multimap, object, object1);
            });
        });
        Set<K> set = new HashSet();

        this.contents.keySet().forEach((object) -> {
            this.visitDependenciesAndElement(multimap, set, object, output);
        });
    }

    public interface Entry<K> {

        void visitRequiredDependencies(Consumer<K> output);

        void visitOptionalDependencies(Consumer<K> output);
    }
}
