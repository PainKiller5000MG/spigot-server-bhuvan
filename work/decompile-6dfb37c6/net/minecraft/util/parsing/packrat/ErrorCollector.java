package net.minecraft.util.parsing.packrat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public interface ErrorCollector<S> {

    void store(int cursor, SuggestionSupplier<S> suggestions, Object reason);

    default void store(int cursor, Object reason) {
        this.store(cursor, SuggestionSupplier.empty(), reason);
    }

    void finish(int finalCursor);

    public static class Nop<S> implements ErrorCollector<S> {

        public Nop() {}

        @Override
        public void store(int cursor, SuggestionSupplier<S> suggestions, Object reason) {}

        @Override
        public void finish(int finalCursor) {}
    }

    public static class LongestOnly<S> implements ErrorCollector<S> {

        private @Nullable ErrorCollector.LongestOnly.MutableErrorEntry<S>[] entries = new ErrorCollector.LongestOnly.MutableErrorEntry[16];
        private int nextErrorEntry;
        private int lastCursor = -1;

        public LongestOnly() {}

        private void discardErrorsFromShorterParse(int cursor) {
            if (cursor > this.lastCursor) {
                this.lastCursor = cursor;
                this.nextErrorEntry = 0;
            }

        }

        @Override
        public void finish(int finalCursor) {
            this.discardErrorsFromShorterParse(finalCursor);
        }

        @Override
        public void store(int cursor, SuggestionSupplier<S> suggestions, Object reason) {
            this.discardErrorsFromShorterParse(cursor);
            if (cursor == this.lastCursor) {
                this.addErrorEntry(suggestions, reason);
            }

        }

        private void addErrorEntry(SuggestionSupplier<S> suggestions, Object reason) {
            int i = this.entries.length;

            if (this.nextErrorEntry >= i) {
                int j = Util.growByHalf(i, this.nextErrorEntry + 1);
                ErrorCollector.LongestOnly.MutableErrorEntry<S>[] aerrorcollector_longestonly_mutableerrorentry = new ErrorCollector.LongestOnly.MutableErrorEntry[j];

                System.arraycopy(this.entries, 0, aerrorcollector_longestonly_mutableerrorentry, 0, i);
                this.entries = aerrorcollector_longestonly_mutableerrorentry;
            }

            int k = this.nextErrorEntry++;
            ErrorCollector.LongestOnly.MutableErrorEntry<S> errorcollector_longestonly_mutableerrorentry = this.entries[k];

            if (errorcollector_longestonly_mutableerrorentry == null) {
                errorcollector_longestonly_mutableerrorentry = new ErrorCollector.LongestOnly.MutableErrorEntry<S>();
                this.entries[k] = errorcollector_longestonly_mutableerrorentry;
            }

            errorcollector_longestonly_mutableerrorentry.suggestions = suggestions;
            errorcollector_longestonly_mutableerrorentry.reason = reason;
        }

        public List<ErrorEntry<S>> entries() {
            int i = this.nextErrorEntry;

            if (i == 0) {
                return List.of();
            } else {
                List<ErrorEntry<S>> list = new ArrayList(i);

                for (int j = 0; j < i; ++j) {
                    ErrorCollector.LongestOnly.MutableErrorEntry<S> errorcollector_longestonly_mutableerrorentry = this.entries[j];

                    list.add(new ErrorEntry(this.lastCursor, errorcollector_longestonly_mutableerrorentry.suggestions, errorcollector_longestonly_mutableerrorentry.reason));
                }

                return list;
            }
        }

        public int cursor() {
            return this.lastCursor;
        }

        private static class MutableErrorEntry<S> {

            private SuggestionSupplier<S> suggestions = SuggestionSupplier.<S>empty();
            private Object reason = "empty";

            private MutableErrorEntry() {}
        }
    }
}
