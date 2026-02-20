package net.minecraft.util.parsing.packrat;

import org.jspecify.annotations.Nullable;

public interface Rule<S, T> {

    @Nullable
    T parse(ParseState<S> state);

    static <S, T> Rule<S, T> fromTerm(Term<S> child, Rule.RuleAction<S, T> action) {
        return new Rule.WrappedTerm<S, T>(action, child);
    }

    static <S, T> Rule<S, T> fromTerm(Term<S> child, Rule.SimpleRuleAction<S, T> action) {
        return new Rule.WrappedTerm<S, T>(action, child);
    }

    @FunctionalInterface
    public interface SimpleRuleAction<S, T> extends Rule.RuleAction<S, T> {

        T run(Scope ruleScope);

        @Override
        default T run(ParseState<S> state) {
            return (T) this.run(state.scope());
        }
    }

    public static record WrappedTerm<S, T>(Rule.RuleAction<S, T> action, Term<S> child) implements Rule<S, T> {

        @Override
        public @Nullable T parse(ParseState<S> state) {
            Scope scope = state.scope();

            scope.pushFrame();

            Object object;

            try {
                if (!this.child.parse(state, scope, Control.UNBOUND)) {
                    object = null;
                    return (T) object;
                }

                object = this.action.run(state);
            } finally {
                scope.popFrame();
            }

            return (T) object;
        }
    }

    @FunctionalInterface
    public interface RuleAction<S, T> {

        @Nullable
        T run(ParseState<S> state);
    }
}
