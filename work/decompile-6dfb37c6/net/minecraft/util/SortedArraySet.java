package net.minecraft.util;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;

public class SortedArraySet<T> extends AbstractSet<T> {

    private static final int DEFAULT_INITIAL_CAPACITY = 10;
    private final Comparator<T> comparator;
    private T[] contents;
    private int size;

    private SortedArraySet(int initialCapacity, Comparator<T> comparator) {
        this.comparator = comparator;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity (" + initialCapacity + ") is negative");
        } else {
            this.contents = (T[]) castRawArray(new Object[initialCapacity]);
        }
    }

    public static <T extends Comparable<T>> SortedArraySet<T> create() {
        return create(10);
    }

    public static <T extends Comparable<T>> SortedArraySet<T> create(int initialCapacity) {
        return new SortedArraySet<T>(initialCapacity, Comparator.naturalOrder());
    }

    public static <T> SortedArraySet<T> create(Comparator<T> comparator) {
        return create(comparator, 10);
    }

    public static <T> SortedArraySet<T> create(Comparator<T> comparator, int initialCapacity) {
        return new SortedArraySet<T>(initialCapacity, comparator);
    }

    private static <T> T[] castRawArray(Object[] array) {
        return (T[]) array;
    }

    private int findIndex(T t) {
        return Arrays.binarySearch(this.contents, 0, this.size, t, this.comparator);
    }

    private static int getInsertionPosition(int position) {
        return -position - 1;
    }

    public boolean add(T t) {
        int i = this.findIndex(t);

        if (i >= 0) {
            return false;
        } else {
            int j = getInsertionPosition(i);

            this.addInternal(t, j);
            return true;
        }
    }

    private void grow(int capacity) {
        if (capacity > this.contents.length) {
            if (this.contents != ObjectArrays.DEFAULT_EMPTY_ARRAY) {
                capacity = Util.growByHalf(this.contents.length, capacity);
            } else if (capacity < 10) {
                capacity = 10;
            }

            Object[] aobject = new Object[capacity];

            System.arraycopy(this.contents, 0, aobject, 0, this.size);
            this.contents = (T[]) castRawArray(aobject);
        }
    }

    private void addInternal(T t, int pos) {
        this.grow(this.size + 1);
        if (pos != this.size) {
            System.arraycopy(this.contents, pos, this.contents, pos + 1, this.size - pos);
        }

        this.contents[pos] = t;
        ++this.size;
    }

    private void removeInternal(int position) {
        --this.size;
        if (position != this.size) {
            System.arraycopy(this.contents, position + 1, this.contents, position, this.size - position);
        }

        this.contents[this.size] = null;
    }

    private T getInternal(int position) {
        return (T) this.contents[position];
    }

    public T addOrGet(T t) {
        int i = this.findIndex(t);

        if (i >= 0) {
            return (T) this.getInternal(i);
        } else {
            this.addInternal(t, getInsertionPosition(i));
            return t;
        }
    }

    public boolean remove(Object o) {
        int i = this.findIndex(o);

        if (i >= 0) {
            this.removeInternal(i);
            return true;
        } else {
            return false;
        }
    }

    public @Nullable T get(T t) {
        int i = this.findIndex(t);

        return (T) (i >= 0 ? this.getInternal(i) : null);
    }

    public T first() {
        return (T) this.getInternal(0);
    }

    public T last() {
        return (T) this.getInternal(this.size - 1);
    }

    public boolean contains(Object o) {
        int i = this.findIndex(o);

        return i >= 0;
    }

    public Iterator<T> iterator() {
        return new SortedArraySet.ArrayIterator();
    }

    public int size() {
        return this.size;
    }

    public Object[] toArray() {
        return Arrays.copyOf(this.contents, this.size, Object[].class);
    }

    public <U> U[] toArray(U[] a) {
        if (a.length < this.size) {
            return (U[]) Arrays.copyOf(this.contents, this.size, a.getClass());
        } else {
            System.arraycopy(this.contents, 0, a, 0, this.size);
            if (a.length > this.size) {
                a[this.size] = null;
            }

            return a;
        }
    }

    public void clear() {
        Arrays.fill(this.contents, 0, this.size, (Object) null);
        this.size = 0;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            if (o instanceof SortedArraySet) {
                SortedArraySet<?> sortedarrayset = (SortedArraySet) o;

                if (this.comparator.equals(sortedarrayset.comparator)) {
                    return this.size == sortedarrayset.size && Arrays.equals(this.contents, sortedarrayset.contents);
                }
            }

            return super.equals(o);
        }
    }

    private class ArrayIterator implements Iterator<T> {

        private int index;
        private int last = -1;

        private ArrayIterator() {}

        public boolean hasNext() {
            return this.index < SortedArraySet.this.size;
        }

        public T next() {
            if (this.index >= SortedArraySet.this.size) {
                throw new NoSuchElementException();
            } else {
                this.last = this.index++;
                return (T) SortedArraySet.this.contents[this.last];
            }
        }

        public void remove() {
            if (this.last == -1) {
                throw new IllegalStateException();
            } else {
                SortedArraySet.this.removeInternal(this.last);
                --this.index;
                this.last = -1;
            }
        }
    }
}
