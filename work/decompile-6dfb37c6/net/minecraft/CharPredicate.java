package net.minecraft;

import java.util.Objects;

@FunctionalInterface
public interface CharPredicate {

    boolean test(char value);

    default CharPredicate and(CharPredicate other) {
        Objects.requireNonNull(other);
        return (c0) -> {
            return this.test(c0) && other.test(c0);
        };
    }

    default CharPredicate negate() {
        return (c0) -> {
            return !this.test(c0);
        };
    }

    default CharPredicate or(CharPredicate other) {
        Objects.requireNonNull(other);
        return (c0) -> {
            return this.test(c0) || other.test(c0);
        };
    }
}
