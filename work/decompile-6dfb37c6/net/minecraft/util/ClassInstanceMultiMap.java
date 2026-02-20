package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {

    private final Map<Class<?>, List<T>> byClass = Maps.newHashMap();
    private final Class<T> baseClass;
    private final List<T> allInstances = Lists.newArrayList();

    public ClassInstanceMultiMap(Class<T> baseClass) {
        this.baseClass = baseClass;
        this.byClass.put(baseClass, this.allInstances);
    }

    public boolean add(T instance) {
        boolean flag = false;

        for (Map.Entry<Class<?>, List<T>> map_entry : this.byClass.entrySet()) {
            if (((Class) map_entry.getKey()).isInstance(instance)) {
                flag |= ((List) map_entry.getValue()).add(instance);
            }
        }

        return flag;
    }

    public boolean remove(Object object) {
        boolean flag = false;

        for (Map.Entry<Class<?>, List<T>> map_entry : this.byClass.entrySet()) {
            if (((Class) map_entry.getKey()).isInstance(object)) {
                List<T> list = (List) map_entry.getValue();

                flag |= list.remove(object);
            }
        }

        return flag;
    }

    public boolean contains(Object o) {
        return this.find(o.getClass()).contains(o);
    }

    public <S> Collection<S> find(Class<S> index) {
        if (!this.baseClass.isAssignableFrom(index)) {
            throw new IllegalArgumentException("Don't know how to search for " + String.valueOf(index));
        } else {
            List<? extends T> list = (List) this.byClass.computeIfAbsent(index, (oclass1) -> {
                Stream stream = this.allInstances.stream();

                Objects.requireNonNull(oclass1);
                return (List) stream.filter(oclass1::isInstance).collect(Util.toMutableList());
            });

            return Collections.unmodifiableCollection(list);
        }
    }

    public Iterator<T> iterator() {
        return (Iterator<T>) (this.allInstances.isEmpty() ? Collections.emptyIterator() : Iterators.unmodifiableIterator(this.allInstances.iterator()));
    }

    public List<T> getAllInstances() {
        return ImmutableList.copyOf(this.allInstances);
    }

    public int size() {
        return this.allInstances.size();
    }
}
