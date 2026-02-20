package net.minecraft.util.parsing.packrat;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface ParseState<S> {

    Scope scope();

    ErrorCollector<S> errorCollector();

    default <T> Optional<T> parseTopRule(NamedRule<S, T> rule) {
        T t0 = (T) this.parse(rule);

        if (t0 != null) {
            this.errorCollector().finish(this.mark());
        }

        if (!this.scope().hasOnlySingleFrame()) {
            throw new IllegalStateException("Malformed scope: " + String.valueOf(this.scope()));
        } else {
            return Optional.ofNullable(t0);
        }
    }

    <T> @Nullable T parse(NamedRule<S, T> rule);

    S input();

    int mark();

    void restore(int mark);

    Control acquireControl();

    void releaseControl();

    ParseState<S> silent();
}
