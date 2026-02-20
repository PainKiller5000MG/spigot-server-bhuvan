package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public class ArrayListDeque<T> extends AbstractList<T> implements ListAndDeque<T> {

    private static final int MIN_GROWTH = 1;
    private @Nullable Object[] contents;
    private int head;
    private int size;

    public ArrayListDeque() {
        this(1);
    }

    public ArrayListDeque(int capacity) {
        this.contents = new Object[capacity];
        this.head = 0;
        this.size = 0;
    }

    public int size() {
        return this.size;
    }

    @VisibleForTesting
    public int capacity() {
        return this.contents.length;
    }

    private int getIndex(int index) {
        return (index + this.head) % this.contents.length;
    }

    public T get(int index) {
        this.verifyIndexInRange(index);
        return (T) this.getInner(this.getIndex(index));
    }

    private static void verifyIndexInRange(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private void verifyIndexInRange(int index) {
        verifyIndexInRange(index, this.size);
    }

    private T getInner(int innerIndex) {
        return (T) this.contents[innerIndex];
    }

    public T set(int index, T element) {
        this.verifyIndexInRange(index);
        Objects.requireNonNull(element);
        int j = this.getIndex(index);
        T t1 = (T) this.getInner(j);

        this.contents[j] = element;
        return t1;
    }

    public void add(int index, T element) {
        verifyIndexInRange(index, this.size + 1);
        Objects.requireNonNull(element);
        if (this.size == this.contents.length) {
            this.grow();
        }

        int j = this.getIndex(index);

        if (index == this.size) {
            this.contents[j] = element;
        } else if (index == 0) {
            --this.head;
            if (this.head < 0) {
                this.head += this.contents.length;
            }

            this.contents[this.getIndex(0)] = element;
        } else {
            for (int k = this.size - 1; k >= index; --k) {
                this.contents[this.getIndex(k + 1)] = this.contents[this.getIndex(k)];
            }

            this.contents[j] = element;
        }

        ++this.modCount;
        ++this.size;
    }

    private void grow() {
        int i = this.contents.length + Math.max(this.contents.length >> 1, 1);
        Object[] aobject = new Object[i];

        this.copyCount(aobject, this.size);
        this.head = 0;
        this.contents = aobject;
    }

    public T remove(int index) {
        this.verifyIndexInRange(index);
        int j = this.getIndex(index);
        T t0 = (T) this.getInner(j);

        if (index == 0) {
            this.contents[j] = null;
            ++this.head;
        } else if (index == this.size - 1) {
            this.contents[j] = null;
        } else {
            for (int k = index + 1; k < this.size; ++k) {
                this.contents[this.getIndex(k - 1)] = this.get(k);
            }

            this.contents[this.getIndex(this.size - 1)] = null;
        }

        ++this.modCount;
        --this.size;
        return t0;
    }

    public boolean removeIf(Predicate<? super T> filter) {
        int i = 0;

        for (int j = 0; j < this.size; ++j) {
            T t0 = (T) this.get(j);

            if (filter.test(t0)) {
                ++i;
            } else if (i != 0) {
                this.contents[this.getIndex(j - i)] = t0;
                this.contents[this.getIndex(j)] = null;
            }
        }

        this.modCount += i;
        this.size -= i;
        return i != 0;
    }

    private void copyCount(Object[] newContents, int count) {
        for (int j = 0; j < count; ++j) {
            newContents[j] = this.get(j);
        }

    }

    public void replaceAll(UnaryOperator<T> operator) {
        for (int i = 0; i < this.size; ++i) {
            int j = this.getIndex(i);

            this.contents[j] = Objects.requireNonNull(operator.apply(this.getInner(i)));
        }

    }

    public void forEach(Consumer<? super T> action) {
        for (int i = 0; i < this.size; ++i) {
            action.accept(this.get(i));
        }

    }

    @Override
    public void addFirst(T value) {
        this.add(0, value);
    }

    @Override
    public void addLast(T value) {
        this.add(this.size, value);
    }

    public boolean offerFirst(T value) {
        this.addFirst(value);
        return true;
    }

    public boolean offerLast(T value) {
        this.addLast(value);
        return true;
    }

    @Override
    public T removeFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return (T) this.remove(0);
        }
    }

    @Override
    public T removeLast() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return (T) this.remove(this.size - 1);
        }
    }

    @Override
    public ListAndDeque<T> reversed() {
        return new ArrayListDeque.ReversedView(this);
    }

    public @Nullable T pollFirst() {
        return (T) (this.size == 0 ? null : this.removeFirst());
    }

    public @Nullable T pollLast() {
        return (T) (this.size == 0 ? null : this.removeLast());
    }

    @Override
    public T getFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return (T) this.get(0);
        }
    }

    @Override
    public T getLast() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return (T) this.get(this.size - 1);
        }
    }

    public @Nullable T peekFirst() {
        return (T) (this.size == 0 ? null : this.getFirst());
    }

    public @Nullable T peekLast() {
        return (T) (this.size == 0 ? null : this.getLast());
    }

    public boolean removeFirstOccurrence(Object o) {
        for (int i = 0; i < this.size; ++i) {
            T t0 = (T) this.get(i);

            if (Objects.equals(o, t0)) {
                this.remove(i);
                return true;
            }
        }

        return false;
    }

    public boolean removeLastOccurrence(Object o) {
        for (int i = this.size - 1; i >= 0; --i) {
            T t0 = (T) this.get(i);

            if (Objects.equals(o, t0)) {
                this.remove(i);
                return true;
            }
        }

        return false;
    }

    public Iterator<T> descendingIterator() {
        return new ArrayListDeque.DescendingIterator();
    }

    private class DescendingIterator implements Iterator<T> {

        private int index = ArrayListDeque.this.size() - 1;

        public DescendingIterator() {}

        public boolean hasNext() {
            return this.index >= 0;
        }

        public T next() {
            return (T) ArrayListDeque.this.get(this.index--);
        }

        public void remove() {
            ArrayListDeque.this.remove(this.index + 1);
        }
    }

    private class ReversedView extends AbstractList<T> implements ListAndDeque<T> {

        private final ArrayListDeque<T> source;

        public ReversedView(ArrayListDeque<T> source) {
            this.source = source;
        }

        @Override
        public ListAndDeque<T> reversed() {
            return this.source;
        }

        @Override
        public T getFirst() {
            return (T) this.source.getLast();
        }

        @Override
        public T getLast() {
            return (T) this.source.getFirst();
        }

        @Override
        public void addFirst(T t) {
            this.source.addLast(t);
        }

        @Override
        public void addLast(T t) {
            this.source.addFirst(t);
        }

        public boolean offerFirst(T t) {
            return this.source.offerLast(t);
        }

        public boolean offerLast(T t) {
            return this.source.offerFirst(t);
        }

        public @Nullable T pollFirst() {
            return this.source.pollLast();
        }

        public @Nullable T pollLast() {
            return this.source.pollFirst();
        }

        public @Nullable T peekFirst() {
            return this.source.peekLast();
        }

        public @Nullable T peekLast() {
            return this.source.peekFirst();
        }

        @Override
        public T removeFirst() {
            return (T) this.source.removeLast();
        }

        @Override
        public T removeLast() {
            return (T) this.source.removeFirst();
        }

        public boolean removeFirstOccurrence(Object o) {
            return this.source.removeLastOccurrence(o);
        }

        public boolean removeLastOccurrence(Object o) {
            return this.source.removeFirstOccurrence(o);
        }

        public Iterator<T> descendingIterator() {
            return this.source.iterator();
        }

        public int size() {
            return this.source.size();
        }

        public boolean isEmpty() {
            return this.source.isEmpty();
        }

        public boolean contains(Object o) {
            return this.source.contains(o);
        }

        public T get(int index) {
            return this.source.get(this.reverseIndex(index));
        }

        public T set(int index, T element) {
            return this.source.set(this.reverseIndex(index), element);
        }

        public void add(int index, T element) {
            this.source.add(this.reverseIndex(index) + 1, element);
        }

        public T remove(int index) {
            return this.source.remove(this.reverseIndex(index));
        }

        public int indexOf(Object o) {
            return this.reverseIndex(this.source.lastIndexOf(o));
        }

        public int lastIndexOf(Object o) {
            return this.reverseIndex(this.source.indexOf(o));
        }

        public List<T> subList(int fromIndex, int toIndex) {
            return this.source.subList(this.reverseIndex(toIndex) + 1, this.reverseIndex(fromIndex) + 1).reversed();
        }

        public Iterator<T> iterator() {
            return this.source.descendingIterator();
        }

        public void clear() {
            this.source.clear();
        }

        private int reverseIndex(int index) {
            return index == -1 ? -1 : this.source.size() - 1 - index;
        }
    }
}
