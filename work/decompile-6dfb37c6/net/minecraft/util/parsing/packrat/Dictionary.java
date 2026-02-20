package net.minecraft.util.parsing.packrat;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public class Dictionary<S> {

    private final Map<Atom<?>, Dictionary.Entry<S, ?>> terms = new IdentityHashMap();

    public Dictionary() {}

    public <T> NamedRule<S, T> put(Atom<T> name, Rule<S, T> entry) {
        Dictionary.Entry<S, T> dictionary_entry = (Dictionary.Entry) this.terms.computeIfAbsent(name, Dictionary.Entry::new);

        if (dictionary_entry.value != null) {
            throw new IllegalArgumentException("Trying to override rule: " + String.valueOf(name));
        } else {
            dictionary_entry.value = entry;
            return dictionary_entry;
        }
    }

    public <T> NamedRule<S, T> putComplex(Atom<T> name, Term<S> term, Rule.RuleAction<S, T> action) {
        return this.put(name, Rule.fromTerm(term, action));
    }

    public <T> NamedRule<S, T> put(Atom<T> name, Term<S> term, Rule.SimpleRuleAction<S, T> action) {
        return this.put(name, Rule.fromTerm(term, action));
    }

    public void checkAllBound() {
        List<? extends Atom<?>> list = this.terms.entrySet().stream().filter((java_util_map_entry) -> {
            return ((Dictionary.Entry) java_util_map_entry.getValue()).value == null;
        }).map(java.util.Map.Entry::getKey).toList();

        if (!list.isEmpty()) {
            throw new IllegalStateException("Unbound names: " + String.valueOf(list));
        }
    }

    public <T> NamedRule<S, T> getOrThrow(Atom<T> name) {
        return (NamedRule) Objects.requireNonNull((Dictionary.Entry) this.terms.get(name), () -> {
            return "No rule called " + String.valueOf(name);
        });
    }

    public <T> NamedRule<S, T> forward(Atom<T> name) {
        return this.getOrCreateEntry(name);
    }

    private <T> Dictionary.Entry<S, T> getOrCreateEntry(Atom<T> name) {
        return (Dictionary.Entry) this.terms.computeIfAbsent(name, Dictionary.Entry::new);
    }

    public <T> Term<S> named(Atom<T> name) {
        return new Dictionary.Reference(this.getOrCreateEntry(name), name);
    }

    public <T> Term<S> namedWithAlias(Atom<T> nameToParse, Atom<T> nameToStore) {
        return new Dictionary.Reference(this.getOrCreateEntry(nameToParse), nameToStore);
    }

    private static record Reference<S, T>(Dictionary.Entry<S, T> ruleToParse, Atom<T> nameToStore) implements Term<S> {

        @Override
        public boolean parse(ParseState<S> state, Scope scope, Control control) {
            T t0 = (T) state.parse(this.ruleToParse);

            if (t0 == null) {
                return false;
            } else {
                scope.put(this.nameToStore, t0);
                return true;
            }
        }
    }

    private static class Entry<S, T> implements NamedRule<S, T>, Supplier<String> {

        private final Atom<T> name;
        private @Nullable Rule<S, T> value;

        private Entry(Atom<T> name) {
            this.name = name;
        }

        @Override
        public Atom<T> name() {
            return this.name;
        }

        @Override
        public Rule<S, T> value() {
            return (Rule) Objects.requireNonNull(this.value, this);
        }

        public String get() {
            return "Unbound rule " + String.valueOf(this.name);
        }
    }
}
