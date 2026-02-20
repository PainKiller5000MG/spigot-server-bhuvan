package net.minecraft.util.parsing.packrat;

import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public abstract class CachedParseState<S> implements ParseState<S> {

    private @Nullable CachedParseState.PositionCache[] positionCache = new CachedParseState.PositionCache[256];
    private final ErrorCollector<S> errorCollector;
    private final Scope scope = new Scope();
    private @Nullable CachedParseState.SimpleControl[] controlCache = new CachedParseState.SimpleControl[16];
    private int nextControlToReturn;
    private final CachedParseState<S>.Silent silent = new CachedParseState.Silent();

    protected CachedParseState(ErrorCollector<S> errorCollector) {
        this.errorCollector = errorCollector;
    }

    @Override
    public Scope scope() {
        return this.scope;
    }

    @Override
    public ErrorCollector<S> errorCollector() {
        return this.errorCollector;
    }

    @Override
    public <T> @Nullable T parse(NamedRule<S, T> rule) {
        int i = this.mark();
        CachedParseState.PositionCache cachedparsestate_positioncache = this.getCacheForPosition(i);
        int j = cachedparsestate_positioncache.findKeyIndex(rule.name());

        if (j != -1) {
            CachedParseState.CacheEntry<T> cachedparsestate_cacheentry = cachedparsestate_positioncache.<T>getValue(j);

            if (cachedparsestate_cacheentry != null) {
                if (cachedparsestate_cacheentry == CachedParseState.CacheEntry.NEGATIVE) {
                    return null;
                }

                this.restore(cachedparsestate_cacheentry.markAfterParse);
                return cachedparsestate_cacheentry.value;
            }
        } else {
            j = cachedparsestate_positioncache.allocateNewEntry(rule.name());
        }

        T t0 = rule.value().parse(this);
        CachedParseState.CacheEntry<T> cachedparsestate_cacheentry1;

        if (t0 == null) {
            cachedparsestate_cacheentry1 = CachedParseState.CacheEntry.<T>negativeEntry();
        } else {
            int k = this.mark();

            cachedparsestate_cacheentry1 = new CachedParseState.CacheEntry<T>(t0, k);
        }

        cachedparsestate_positioncache.setValue(j, cachedparsestate_cacheentry1);
        return t0;
    }

    private CachedParseState.PositionCache getCacheForPosition(int index) {
        int j = this.positionCache.length;

        if (index >= j) {
            int k = Util.growByHalf(j, index + 1);
            CachedParseState.PositionCache[] acachedparsestate_positioncache = new CachedParseState.PositionCache[k];

            System.arraycopy(this.positionCache, 0, acachedparsestate_positioncache, 0, j);
            this.positionCache = acachedparsestate_positioncache;
        }

        CachedParseState.PositionCache cachedparsestate_positioncache = this.positionCache[index];

        if (cachedparsestate_positioncache == null) {
            cachedparsestate_positioncache = new CachedParseState.PositionCache();
            this.positionCache[index] = cachedparsestate_positioncache;
        }

        return cachedparsestate_positioncache;
    }

    @Override
    public Control acquireControl() {
        int i = this.controlCache.length;

        if (this.nextControlToReturn >= i) {
            int j = Util.growByHalf(i, this.nextControlToReturn + 1);
            CachedParseState.SimpleControl[] acachedparsestate_simplecontrol = new CachedParseState.SimpleControl[j];

            System.arraycopy(this.controlCache, 0, acachedparsestate_simplecontrol, 0, i);
            this.controlCache = acachedparsestate_simplecontrol;
        }

        int k = this.nextControlToReturn++;
        CachedParseState.SimpleControl cachedparsestate_simplecontrol = this.controlCache[k];

        if (cachedparsestate_simplecontrol == null) {
            cachedparsestate_simplecontrol = new CachedParseState.SimpleControl();
            this.controlCache[k] = cachedparsestate_simplecontrol;
        } else {
            cachedparsestate_simplecontrol.reset();
        }

        return cachedparsestate_simplecontrol;
    }

    @Override
    public void releaseControl() {
        --this.nextControlToReturn;
    }

    @Override
    public ParseState<S> silent() {
        return this.silent;
    }

    private static class PositionCache {

        public static final int ENTRY_STRIDE = 2;
        private static final int NOT_FOUND = -1;
        private Object[] atomCache = new Object[16];
        private int nextKey;

        private PositionCache() {}

        public int findKeyIndex(Atom<?> key) {
            for (int i = 0; i < this.nextKey; i += 2) {
                if (this.atomCache[i] == key) {
                    return i;
                }
            }

            return -1;
        }

        public int allocateNewEntry(Atom<?> key) {
            int i = this.nextKey;

            this.nextKey += 2;
            int j = i + 1;
            int k = this.atomCache.length;

            if (j >= k) {
                int l = Util.growByHalf(k, j + 1);
                Object[] aobject = new Object[l];

                System.arraycopy(this.atomCache, 0, aobject, 0, k);
                this.atomCache = aobject;
            }

            this.atomCache[i] = key;
            return i;
        }

        public <T> CachedParseState.@Nullable CacheEntry<T> getValue(int keyIndex) {
            return (CachedParseState.CacheEntry) this.atomCache[keyIndex + 1];
        }

        public void setValue(int keyIndex, CachedParseState.CacheEntry<?> entry) {
            this.atomCache[keyIndex + 1] = entry;
        }
    }

    private static record CacheEntry<T>(@Nullable T value, int markAfterParse) {

        public static final CachedParseState.CacheEntry<?> NEGATIVE = new CachedParseState.CacheEntry((Object) null, -1);

        public static <T> CachedParseState.CacheEntry<T> negativeEntry() {
            return CachedParseState.CacheEntry.NEGATIVE;
        }
    }

    private class Silent implements ParseState<S> {

        private final ErrorCollector<S> silentCollector = new ErrorCollector.Nop<S>();

        private Silent() {}

        @Override
        public ErrorCollector<S> errorCollector() {
            return this.silentCollector;
        }

        @Override
        public Scope scope() {
            return CachedParseState.this.scope();
        }

        @Override
        public <T> @Nullable T parse(NamedRule<S, T> rule) {
            return (T) CachedParseState.this.parse(rule);
        }

        @Override
        public S input() {
            return (S) CachedParseState.this.input();
        }

        @Override
        public int mark() {
            return CachedParseState.this.mark();
        }

        @Override
        public void restore(int mark) {
            CachedParseState.this.restore(mark);
        }

        @Override
        public Control acquireControl() {
            return CachedParseState.this.acquireControl();
        }

        @Override
        public void releaseControl() {
            CachedParseState.this.releaseControl();
        }

        @Override
        public ParseState<S> silent() {
            return this;
        }
    }

    private static class SimpleControl implements Control {

        private boolean hasCut;

        private SimpleControl() {}

        @Override
        public void cut() {
            this.hasCut = true;
        }

        @Override
        public boolean hasCut() {
            return this.hasCut;
        }

        public void reset() {
            this.hasCut = false;
        }
    }
}
