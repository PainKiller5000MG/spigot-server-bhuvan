package net.minecraft.util.context;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;

public class ContextKeySet {

    private final Set<ContextKey<?>> required;
    private final Set<ContextKey<?>> allowed;

    private ContextKeySet(Set<ContextKey<?>> required, Set<ContextKey<?>> optional) {
        this.required = Set.copyOf(required);
        this.allowed = Set.copyOf(Sets.union(required, optional));
    }

    public Set<ContextKey<?>> required() {
        return this.required;
    }

    public Set<ContextKey<?>> allowed() {
        return this.allowed;
    }

    public String toString() {
        Joiner joiner = Joiner.on(", ");
        Iterator iterator = this.allowed.stream().map((contextkey) -> {
            String s = this.required.contains(contextkey) ? "!" : "";

            return s + String.valueOf(contextkey.name());
        }).iterator();

        return "[" + joiner.join(iterator) + "]";
    }

    public static class Builder {

        private final Set<ContextKey<?>> required = Sets.newIdentityHashSet();
        private final Set<ContextKey<?>> optional = Sets.newIdentityHashSet();

        public Builder() {}

        public ContextKeySet.Builder required(ContextKey<?> param) {
            if (this.optional.contains(param)) {
                throw new IllegalArgumentException("Parameter " + String.valueOf(param.name()) + " is already optional");
            } else {
                this.required.add(param);
                return this;
            }
        }

        public ContextKeySet.Builder optional(ContextKey<?> param) {
            if (this.required.contains(param)) {
                throw new IllegalArgumentException("Parameter " + String.valueOf(param.name()) + " is already required");
            } else {
                this.optional.add(param);
                return this;
            }
        }

        public ContextKeySet build() {
            return new ContextKeySet(this.required, this.optional);
        }
    }
}
