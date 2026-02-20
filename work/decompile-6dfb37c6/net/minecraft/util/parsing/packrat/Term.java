package net.minecraft.util.parsing.packrat;

import java.util.ArrayList;
import java.util.List;

public interface Term<S> {

    boolean parse(ParseState<S> state, Scope scope, Control control);

    static <S, T> Term<S> marker(Atom<T> name, T value) {
        return new Term.Marker(name, value);
    }

    @SafeVarargs
    static <S> Term<S> sequence(Term<S>... terms) {
        return new Term.Sequence<S>(terms);
    }

    @SafeVarargs
    static <S> Term<S> alternative(Term<S>... terms) {
        return new Term.Alternative<S>(terms);
    }

    static <S> Term<S> optional(Term<S> term) {
        return new Term.Maybe<S>(term);
    }

    static <S, T> Term<S> repeated(NamedRule<S, T> element, Atom<List<T>> listName) {
        return repeated(element, listName, 0);
    }

    static <S, T> Term<S> repeated(NamedRule<S, T> element, Atom<List<T>> listName, int minRepetitions) {
        return new Term.Repeated(element, listName, minRepetitions);
    }

    static <S, T> Term<S> repeatedWithTrailingSeparator(NamedRule<S, T> element, Atom<List<T>> listName, Term<S> separator) {
        return repeatedWithTrailingSeparator(element, listName, separator, 0);
    }

    static <S, T> Term<S> repeatedWithTrailingSeparator(NamedRule<S, T> element, Atom<List<T>> listName, Term<S> separator, int minRepetitions) {
        return new Term.RepeatedWithSeparator(element, listName, separator, minRepetitions, true);
    }

    static <S, T> Term<S> repeatedWithoutTrailingSeparator(NamedRule<S, T> element, Atom<List<T>> listName, Term<S> separator) {
        return repeatedWithoutTrailingSeparator(element, listName, separator, 0);
    }

    static <S, T> Term<S> repeatedWithoutTrailingSeparator(NamedRule<S, T> element, Atom<List<T>> listName, Term<S> separator, int minRepetitions) {
        return new Term.RepeatedWithSeparator(element, listName, separator, minRepetitions, false);
    }

    static <S> Term<S> positiveLookahead(Term<S> term) {
        return new Term.LookAhead<S>(term, true);
    }

    static <S> Term<S> negativeLookahead(Term<S> term) {
        return new Term.LookAhead<S>(term, false);
    }

    static <S> Term<S> cut() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> state, Scope scope, Control control) {
                control.cut();
                return true;
            }

            public String toString() {
                return "\u2191";
            }
        };
    }

    static <S> Term<S> empty() {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> state, Scope scope, Control control) {
                return true;
            }

            public String toString() {
                return "\u03b5";
            }
        };
    }

    static <S> Term<S> fail(final Object message) {
        return new Term<S>() {
            @Override
            public boolean parse(ParseState<S> state, Scope scope, Control control) {
                state.errorCollector().store(state.mark(), message);
                return false;
            }

            public String toString() {
                return "fail";
            }
        };
    }

    public static record Marker<S, T>(Atom<T> name, T value) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            scope.put(this.name, this.value);
            return true;
        }
    }

    public static record Sequence<S>(Term<S>[] elements) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            int i = state.mark();

            for (Term<S> term : this.elements) {
                if (!term.parse(state, scope, control)) {
                    state.restore(i);
                    return false;
                }
            }

            return true;
        }
    }

    public static record Alternative<S>(Term<S>[] elements) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            Control control1 = state.acquireControl();

            try {
                int i = state.mark();

                scope.splitFrame();

                for (Term<S> term : this.elements) {
                    if (term.parse(state, scope, control1)) {
                        scope.mergeFrame();
                        boolean flag = true;

                        return flag;
                    }

                    scope.clearFrameValues();
                    state.restore(i);
                    if (control1.hasCut()) {
                        break;
                    }
                }

                scope.popFrame();
                boolean flag1 = false;

                return flag1;
            } finally {
                state.releaseControl();
            }
        }
    }

    public static record Maybe<S>(Term<S> term) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            int i = state.mark();

            if (!this.term.parse(state, scope, control)) {
                state.restore(i);
            }

            return true;
        }
    }

    public static record Repeated<S, T>(NamedRule<S, T> element, Atom<List<T>> listName, int minRepetitions) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            int i = state.mark();
            List<T> list = new ArrayList(this.minRepetitions);

            while (true) {
                int j = state.mark();
                T t0 = (T) state.parse(this.element);

                if (t0 == null) {
                    state.restore(j);
                    if (list.size() < this.minRepetitions) {
                        state.restore(i);
                        return false;
                    } else {
                        scope.put(this.listName, list);
                        return true;
                    }
                }

                list.add(t0);
            }
        }
    }

    public static record RepeatedWithSeparator<S, T>(NamedRule<S, T> element, Atom<List<T>> listName, Term<S> separator, int minRepetitions, boolean allowTrailingSeparator) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            int i = state.mark();
            List<T> list = new ArrayList(this.minRepetitions);
            boolean flag = true;

            while (true) {
                int j = state.mark();

                if (!flag && !this.separator.parse(state, scope, control)) {
                    state.restore(j);
                    break;
                }

                int k = state.mark();
                T t0 = (T) state.parse(this.element);

                if (t0 == null) {
                    if (flag) {
                        state.restore(k);
                    } else {
                        if (!this.allowTrailingSeparator) {
                            state.restore(i);
                            return false;
                        }

                        state.restore(k);
                    }
                    break;
                }

                list.add(t0);
                flag = false;
            }

            if (list.size() < this.minRepetitions) {
                state.restore(i);
                return false;
            } else {
                scope.put(this.listName, list);
                return true;
            }
        }
    }

    public static record LookAhead<S>(Term<S> term, boolean positive) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            int i = state.mark();
            boolean flag = this.term.parse(state.silent(), scope, control);

            state.restore(i);
            return this.positive == flag;
        }
    }
}
