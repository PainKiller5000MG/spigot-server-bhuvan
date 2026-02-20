package net.minecraft.util;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Iterator;
import net.minecraft.core.IdMap;
import org.jspecify.annotations.Nullable;

public class CrudeIncrementalIntIdentityHashBiMap<K> implements IdMap<K> {

    private static final int NOT_FOUND = -1;
    private static final Object EMPTY_SLOT = null;
    private static final float LOADFACTOR = 0.8F;
    private @Nullable K[] keys;
    private int[] values;
    private @Nullable K[] byId;
    private int nextId;
    private int size;

    private CrudeIncrementalIntIdentityHashBiMap(int capacity) {
        this.keys = (K[]) (new Object[capacity]);
        this.values = new int[capacity];
        this.byId = (K[]) (new Object[capacity]);
    }

    private CrudeIncrementalIntIdentityHashBiMap(K[] keys, int[] values, K[] byId, int nextId, int size) {
        this.keys = keys;
        this.values = values;
        this.byId = byId;
        this.nextId = nextId;
        this.size = size;
    }

    public static <A> CrudeIncrementalIntIdentityHashBiMap<A> create(int initialCapacity) {
        return new CrudeIncrementalIntIdentityHashBiMap<A>((int) ((float) initialCapacity / 0.8F));
    }

    @Override
    public int getId(@Nullable K thing) {
        return this.getValue(this.indexOf(thing, this.hash(thing)));
    }

    @Override
    public @Nullable K byId(int id) {
        return (K) (id >= 0 && id < this.byId.length ? this.byId[id] : null);
    }

    private int getValue(int index) {
        return index == -1 ? -1 : this.values[index];
    }

    public boolean contains(K key) {
        return this.getId(key) != -1;
    }

    public boolean contains(int id) {
        return this.byId(id) != null;
    }

    public int add(K key) {
        int i = this.nextId();

        this.addMapping(key, i);
        return i;
    }

    private int nextId() {
        while (this.nextId < this.byId.length && this.byId[this.nextId] != null) {
            ++this.nextId;
        }

        return this.nextId;
    }

    private void grow(int newSize) {
        K[] ak = this.keys;
        int[] aint = this.values;
        CrudeIncrementalIntIdentityHashBiMap<K> crudeincrementalintidentityhashbimap = new CrudeIncrementalIntIdentityHashBiMap<K>(newSize);

        for (int j = 0; j < ak.length; ++j) {
            if (ak[j] != null) {
                crudeincrementalintidentityhashbimap.addMapping(ak[j], aint[j]);
            }
        }

        this.keys = crudeincrementalintidentityhashbimap.keys;
        this.values = crudeincrementalintidentityhashbimap.values;
        this.byId = crudeincrementalintidentityhashbimap.byId;
        this.nextId = crudeincrementalintidentityhashbimap.nextId;
        this.size = crudeincrementalintidentityhashbimap.size;
    }

    public void addMapping(K key, int id) {
        int j = Math.max(id, this.size + 1);

        if ((float) j >= (float) this.keys.length * 0.8F) {
            int k;

            for (k = this.keys.length << 1; k < id; k <<= 1) {
                ;
            }

            this.grow(k);
        }

        int l = this.findEmpty(this.hash(key));

        this.keys[l] = key;
        this.values[l] = id;
        this.byId[id] = key;
        ++this.size;
        if (id == this.nextId) {
            ++this.nextId;
        }

    }

    private int hash(@Nullable K key) {
        return (Mth.murmurHash3Mixer(System.identityHashCode(key)) & Integer.MAX_VALUE) % this.keys.length;
    }

    private int indexOf(@Nullable K key, int startFrom) {
        for (int j = startFrom; j < this.keys.length; ++j) {
            if (this.keys[j] == key) {
                return j;
            }

            if (this.keys[j] == CrudeIncrementalIntIdentityHashBiMap.EMPTY_SLOT) {
                return -1;
            }
        }

        for (int k = 0; k < startFrom; ++k) {
            if (this.keys[k] == key) {
                return k;
            }

            if (this.keys[k] == CrudeIncrementalIntIdentityHashBiMap.EMPTY_SLOT) {
                return -1;
            }
        }

        return -1;
    }

    private int findEmpty(int startFrom) {
        for (int j = startFrom; j < this.keys.length; ++j) {
            if (this.keys[j] == CrudeIncrementalIntIdentityHashBiMap.EMPTY_SLOT) {
                return j;
            }
        }

        for (int k = 0; k < startFrom; ++k) {
            if (this.keys[k] == CrudeIncrementalIntIdentityHashBiMap.EMPTY_SLOT) {
                return k;
            }
        }

        throw new RuntimeException("Overflowed :(");
    }

    public Iterator<K> iterator() {
        return Iterators.filter(Iterators.forArray(this.byId), Predicates.notNull());
    }

    public void clear() {
        Arrays.fill(this.keys, (Object) null);
        Arrays.fill(this.byId, (Object) null);
        this.nextId = 0;
        this.size = 0;
    }

    @Override
    public int size() {
        return this.size;
    }

    public CrudeIncrementalIntIdentityHashBiMap<K> copy() {
        return new CrudeIncrementalIntIdentityHashBiMap<K>(this.keys.clone(), (int[]) this.values.clone(), this.byId.clone(), this.nextId, this.size);
    }
}
