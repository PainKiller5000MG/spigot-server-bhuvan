package net.minecraft.world.entity.player;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectIterable;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class StackedContents<T> {

    public final Reference2IntOpenHashMap<T> amounts = new Reference2IntOpenHashMap();

    public StackedContents() {}

    private boolean hasAtLeast(T item, int count) {
        return this.amounts.getInt(item) >= count;
    }

    private void take(T item, int amount) {
        int j = this.amounts.addTo(item, -amount);

        if (j < amount) {
            throw new IllegalStateException("Took " + amount + " items, but only had " + j);
        }
    }

    private void put(T item, int count) {
        this.amounts.addTo(item, count);
    }

    public boolean tryPick(List<? extends StackedContents.IngredientInfo<T>> ingredients, int amount, StackedContents.@Nullable Output<T> output) {
        return (new StackedContents.RecipePicker(ingredients)).tryPick(amount, output);
    }

    public int tryPickAll(List<? extends StackedContents.IngredientInfo<T>> ingredients, int maxSize, StackedContents.@Nullable Output<T> output) {
        return (new StackedContents.RecipePicker(ingredients)).tryPickAll(maxSize, output);
    }

    public void clear() {
        this.amounts.clear();
    }

    public void account(T item, int count) {
        this.put(item, count);
    }

    private List<T> getUniqueAvailableIngredientItems(Iterable<? extends StackedContents.IngredientInfo<T>> ingredients) {
        List<T> list = new ArrayList();
        ObjectIterator objectiterator = Reference2IntMaps.fastIterable(this.amounts).iterator();

        while (objectiterator.hasNext()) {
            Reference2IntMap.Entry<T> reference2intmap_entry = (Entry) objectiterator.next();

            if (reference2intmap_entry.getIntValue() > 0 && anyIngredientMatches(ingredients, reference2intmap_entry.getKey())) {
                list.add(reference2intmap_entry.getKey());
            }
        }

        return list;
    }

    private static <T> boolean anyIngredientMatches(Iterable<? extends StackedContents.IngredientInfo<T>> ingredients, T item) {
        for (StackedContents.IngredientInfo<T> stackedcontents_ingredientinfo : ingredients) {
            if (stackedcontents_ingredientinfo.acceptsItem(item)) {
                return true;
            }
        }

        return false;
    }

    @VisibleForTesting
    public int getResultUpperBound(List<? extends StackedContents.IngredientInfo<T>> ingredients) {
        int i = Integer.MAX_VALUE;
        ObjectIterable<Reference2IntMap.Entry<T>> objectiterable = Reference2IntMaps.fastIterable(this.amounts);

        label31:
        for (StackedContents.IngredientInfo<T> stackedcontents_ingredientinfo : ingredients) {
            int j = 0;
            ObjectIterator objectiterator = objectiterable.iterator();

            while (objectiterator.hasNext()) {
                Reference2IntMap.Entry<T> reference2intmap_entry = (Entry) objectiterator.next();
                int k = reference2intmap_entry.getIntValue();

                if (k > j) {
                    if (stackedcontents_ingredientinfo.acceptsItem(reference2intmap_entry.getKey())) {
                        j = k;
                    }

                    if (j >= i) {
                        continue label31;
                    }
                }
            }

            i = j;
            if (j == 0) {
                break;
            }
        }

        return i;
    }

    private class RecipePicker {

        private final List<? extends StackedContents.IngredientInfo<T>> ingredients;
        private final int ingredientCount;
        private final List<T> items;
        private final int itemCount;
        private final BitSet data;
        private final IntList path = new IntArrayList();

        public RecipePicker(List<? extends StackedContents.IngredientInfo<T>> ingredients) {
            this.ingredients = ingredients;
            this.ingredientCount = ingredients.size();
            this.items = StackedContents.this.getUniqueAvailableIngredientItems(ingredients);
            this.itemCount = this.items.size();
            this.data = new BitSet(this.visitedIngredientCount() + this.visitedItemCount() + this.satisfiedCount() + this.connectionCount() + this.residualCount());
            this.setInitialConnections();
        }

        private void setInitialConnections() {
            for (int i = 0; i < this.ingredientCount; ++i) {
                StackedContents.IngredientInfo<T> stackedcontents_ingredientinfo = (StackedContents.IngredientInfo) this.ingredients.get(i);

                for (int j = 0; j < this.itemCount; ++j) {
                    if (stackedcontents_ingredientinfo.acceptsItem(this.items.get(j))) {
                        this.setConnection(j, i);
                    }
                }
            }

        }

        public boolean tryPick(int capacity, StackedContents.@Nullable Output<T> output) {
            if (capacity <= 0) {
                return true;
            } else {
                int j = 0;

                while (true) {
                    IntList intlist = this.tryAssigningNewItem(capacity);

                    if (intlist == null) {
                        boolean flag = j == this.ingredientCount;
                        boolean flag1 = flag && output != null;

                        this.clearAllVisited();
                        this.clearSatisfied();

                        for (int k = 0; k < this.ingredientCount; ++k) {
                            for (int l = 0; l < this.itemCount; ++l) {
                                if (this.isAssigned(l, k)) {
                                    this.unassign(l, k);
                                    StackedContents.this.put(this.items.get(l), capacity);
                                    if (flag1) {
                                        output.accept(this.items.get(l));
                                    }
                                    break;
                                }
                            }
                        }

                        assert this.data.get(this.residualOffset(), this.residualOffset() + this.residualCount()).isEmpty();

                        return flag;
                    }

                    int i1 = intlist.getInt(0);

                    StackedContents.this.take(this.items.get(i1), capacity);
                    int j1 = intlist.size() - 1;

                    this.setSatisfied(intlist.getInt(j1));
                    ++j;

                    for (int k1 = 0; k1 < intlist.size() - 1; ++k1) {
                        if (isPathIndexItem(k1)) {
                            int l1 = intlist.getInt(k1);
                            int i2 = intlist.getInt(k1 + 1);

                            this.assign(l1, i2);
                        } else {
                            int j2 = intlist.getInt(k1 + 1);
                            int k2 = intlist.getInt(k1);

                            this.unassign(j2, k2);
                        }
                    }
                }
            }
        }

        private static boolean isPathIndexItem(int index) {
            return (index & 1) == 0;
        }

        private @Nullable IntList tryAssigningNewItem(int capacity) {
            this.clearAllVisited();

            for (int j = 0; j < this.itemCount; ++j) {
                if (StackedContents.this.hasAtLeast(this.items.get(j), capacity)) {
                    IntList intlist = this.findNewItemAssignmentPath(j);

                    if (intlist != null) {
                        return intlist;
                    }
                }
            }

            return null;
        }

        private @Nullable IntList findNewItemAssignmentPath(int startingItem) {
            this.path.clear();
            this.visitItem(startingItem);
            this.path.add(startingItem);

            while (!this.path.isEmpty()) {
                int j = this.path.size();

                if (isPathIndexItem(j - 1)) {
                    int k = this.path.getInt(j - 1);

                    for (int l = 0; l < this.ingredientCount; ++l) {
                        if (!this.hasVisitedIngredient(l) && this.hasConnection(k, l) && !this.isAssigned(k, l)) {
                            this.visitIngredient(l);
                            this.path.add(l);
                            break;
                        }
                    }
                } else {
                    int i1 = this.path.getInt(j - 1);

                    if (!this.isSatisfied(i1)) {
                        return this.path;
                    }

                    for (int j1 = 0; j1 < this.itemCount; ++j1) {
                        if (!this.hasVisitedItem(j1) && this.isAssigned(j1, i1)) {
                            assert this.hasConnection(j1, i1);

                            this.visitItem(j1);
                            this.path.add(j1);
                            break;
                        }
                    }
                }

                int k1 = this.path.size();

                if (k1 == j) {
                    this.path.removeInt(k1 - 1);
                }
            }

            return null;
        }

        private int visitedIngredientOffset() {
            return 0;
        }

        private int visitedIngredientCount() {
            return this.ingredientCount;
        }

        private int visitedItemOffset() {
            return this.visitedIngredientOffset() + this.visitedIngredientCount();
        }

        private int visitedItemCount() {
            return this.itemCount;
        }

        private int satisfiedOffset() {
            return this.visitedItemOffset() + this.visitedItemCount();
        }

        private int satisfiedCount() {
            return this.ingredientCount;
        }

        private int connectionOffset() {
            return this.satisfiedOffset() + this.satisfiedCount();
        }

        private int connectionCount() {
            return this.ingredientCount * this.itemCount;
        }

        private int residualOffset() {
            return this.connectionOffset() + this.connectionCount();
        }

        private int residualCount() {
            return this.ingredientCount * this.itemCount;
        }

        private boolean isSatisfied(int ingredient) {
            return this.data.get(this.getSatisfiedIndex(ingredient));
        }

        private void setSatisfied(int ingredient) {
            this.data.set(this.getSatisfiedIndex(ingredient));
        }

        private int getSatisfiedIndex(int ingredient) {
            assert ingredient >= 0 && ingredient < this.ingredientCount;

            return this.satisfiedOffset() + ingredient;
        }

        private void clearSatisfied() {
            this.clearRange(this.satisfiedOffset(), this.satisfiedCount());
        }

        private void setConnection(int item, int ingredient) {
            this.data.set(this.getConnectionIndex(item, ingredient));
        }

        private boolean hasConnection(int item, int ingredient) {
            return this.data.get(this.getConnectionIndex(item, ingredient));
        }

        private int getConnectionIndex(int item, int ingredient) {
            assert item >= 0 && item < this.itemCount;

            assert ingredient >= 0 && ingredient < this.ingredientCount;

            return this.connectionOffset() + item * this.ingredientCount + ingredient;
        }

        private boolean isAssigned(int item, int ingredient) {
            return this.data.get(this.getResidualIndex(item, ingredient));
        }

        private void assign(int item, int ingredient) {
            int k = this.getResidualIndex(item, ingredient);

            assert !this.data.get(k);

            this.data.set(k);
        }

        private void unassign(int item, int ingredient) {
            int k = this.getResidualIndex(item, ingredient);

            assert this.data.get(k);

            this.data.clear(k);
        }

        private int getResidualIndex(int item, int ingredient) {
            assert item >= 0 && item < this.itemCount;

            assert ingredient >= 0 && ingredient < this.ingredientCount;

            return this.residualOffset() + item * this.ingredientCount + ingredient;
        }

        private void visitIngredient(int item) {
            this.data.set(this.getVisitedIngredientIndex(item));
        }

        private boolean hasVisitedIngredient(int ingredient) {
            return this.data.get(this.getVisitedIngredientIndex(ingredient));
        }

        private int getVisitedIngredientIndex(int ingredient) {
            assert ingredient >= 0 && ingredient < this.ingredientCount;

            return this.visitedIngredientOffset() + ingredient;
        }

        private void visitItem(int item) {
            this.data.set(this.getVisitiedItemIndex(item));
        }

        private boolean hasVisitedItem(int item) {
            return this.data.get(this.getVisitiedItemIndex(item));
        }

        private int getVisitiedItemIndex(int item) {
            assert item >= 0 && item < this.itemCount;

            return this.visitedItemOffset() + item;
        }

        private void clearAllVisited() {
            this.clearRange(this.visitedIngredientOffset(), this.visitedIngredientCount());
            this.clearRange(this.visitedItemOffset(), this.visitedItemCount());
        }

        private void clearRange(int offset, int count) {
            this.data.clear(offset, offset + count);
        }

        public int tryPickAll(int maxSize, StackedContents.@Nullable Output<T> output) {
            int j = 0;
            int k = Math.min(maxSize, StackedContents.this.getResultUpperBound(this.ingredients)) + 1;

            while (true) {
                int l = (j + k) / 2;

                if (this.tryPick(l, (StackedContents.Output) null)) {
                    if (k - j <= 1) {
                        if (l > 0) {
                            this.tryPick(l, output);
                        }

                        return l;
                    }

                    j = l;
                } else {
                    k = l;
                }
            }
        }
    }

    @FunctionalInterface
    public interface IngredientInfo<T> {

        boolean acceptsItem(T item);
    }

    @FunctionalInterface
    public interface Output<T> {

        void accept(T item);
    }
}
