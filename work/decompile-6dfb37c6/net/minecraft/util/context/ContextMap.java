package net.minecraft.util.context;

import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public class ContextMap {

    private final Map<ContextKey<?>, Object> params;

    private ContextMap(Map<ContextKey<?>, Object> params) {
        this.params = params;
    }

    public boolean has(ContextKey<?> key) {
        return this.params.containsKey(key);
    }

    public <T> T getOrThrow(ContextKey<T> key) {
        T t0 = (T) this.params.get(key);

        if (t0 == null) {
            throw new NoSuchElementException(key.name().toString());
        } else {
            return t0;
        }
    }

    public <T> @Nullable T getOptional(ContextKey<T> key) {
        return (T) this.params.get(key);
    }

    @Contract("_,!null->!null; _,_->_")
    public <T> @Nullable T getOrDefault(ContextKey<T> param, @Nullable T _default) {
        return (T) this.params.getOrDefault(param, _default);
    }

    public static class Builder {

        private final Map<ContextKey<?>, Object> params = new IdentityHashMap();

        public Builder() {}

        public <T> ContextMap.Builder withParameter(ContextKey<T> param, T value) {
            this.params.put(param, value);
            return this;
        }

        public <T> ContextMap.Builder withOptionalParameter(ContextKey<T> param, @Nullable T value) {
            if (value == null) {
                this.params.remove(param);
            } else {
                this.params.put(param, value);
            }

            return this;
        }

        public <T> T getParameter(ContextKey<T> param) {
            T t0 = (T) this.params.get(param);

            if (t0 == null) {
                throw new NoSuchElementException(param.name().toString());
            } else {
                return t0;
            }
        }

        public <T> @Nullable T getOptionalParameter(ContextKey<T> param) {
            return (T) this.params.get(param);
        }

        public ContextMap create(ContextKeySet paramSet) {
            Set<ContextKey<?>> set = Sets.difference(this.params.keySet(), paramSet.allowed());

            if (!set.isEmpty()) {
                throw new IllegalArgumentException("Parameters not allowed in this parameter set: " + String.valueOf(set));
            } else {
                Set<ContextKey<?>> set1 = Sets.difference(paramSet.required(), this.params.keySet());

                if (!set1.isEmpty()) {
                    throw new IllegalArgumentException("Missing required parameters: " + String.valueOf(set1));
                } else {
                    return new ContextMap(this.params);
                }
            }
        }
    }
}
