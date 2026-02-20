package net.minecraft.core;

import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

public interface Holder<T> {

    T value();

    boolean isBound();

    boolean is(Identifier key);

    boolean is(ResourceKey<T> key);

    boolean is(Predicate<ResourceKey<T>> predicate);

    boolean is(TagKey<T> tag);

    /** @deprecated */
    @Deprecated
    boolean is(Holder<T> holder);

    Stream<TagKey<T>> tags();

    Either<ResourceKey<T>, T> unwrap();

    Optional<ResourceKey<T>> unwrapKey();

    Holder.Kind kind();

    boolean canSerializeIn(HolderOwner<T> registry);

    default String getRegisteredName() {
        return (String) this.unwrapKey().map((resourcekey) -> {
            return resourcekey.identifier().toString();
        }).orElse("[unregistered]");
    }

    static <T> Holder<T> direct(T value) {
        return new Holder.Direct<T>(value);
    }

    public static enum Kind {

        REFERENCE, DIRECT;

        private Kind() {}
    }

    public static record Direct<T>(T value) implements Holder<T> {

        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean is(Identifier key) {
            return false;
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return false;
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return false;
        }

        @Override
        public boolean is(Holder<T> holder) {
            return this.value.equals(holder.value());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return false;
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.right(this.value);
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.empty();
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.DIRECT;
        }

        public String toString() {
            return "Direct{" + String.valueOf(this.value) + "}";
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> registry) {
            return true;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return Stream.of();
        }
    }

    public static class Reference<T> implements Holder<T> {

        private final HolderOwner<T> owner;
        private @Nullable Set<TagKey<T>> tags;
        private final Holder.Reference.Type type;
        private @Nullable ResourceKey<T> key;
        private @Nullable T value;

        protected Reference(Holder.Reference.Type type, HolderOwner<T> owner, @Nullable ResourceKey<T> key, @Nullable T value) {
            this.owner = owner;
            this.type = type;
            this.key = key;
            this.value = value;
        }

        public static <T> Holder.Reference<T> createStandAlone(HolderOwner<T> owner, ResourceKey<T> key) {
            return new Holder.Reference<T>(Holder.Reference.Type.STAND_ALONE, owner, key, (Object) null);
        }

        /** @deprecated */
        @Deprecated
        public static <T> Holder.Reference<T> createIntrusive(HolderOwner<T> owner, @Nullable T value) {
            return new Holder.Reference<T>(Holder.Reference.Type.INTRUSIVE, owner, (ResourceKey) null, value);
        }

        public ResourceKey<T> key() {
            if (this.key == null) {
                String s = String.valueOf(this.value);

                throw new IllegalStateException("Trying to access unbound value '" + s + "' from registry " + String.valueOf(this.owner));
            } else {
                return this.key;
            }
        }

        @Override
        public T value() {
            if (this.value == null) {
                String s = String.valueOf(this.key);

                throw new IllegalStateException("Trying to access unbound value '" + s + "' from registry " + String.valueOf(this.owner));
            } else {
                return this.value;
            }
        }

        @Override
        public boolean is(Identifier key) {
            return this.key().identifier().equals(key);
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return this.key() == key;
        }

        private Set<TagKey<T>> boundTags() {
            if (this.tags == null) {
                throw new IllegalStateException("Tags not bound");
            } else {
                return this.tags;
            }
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return this.boundTags().contains(tag);
        }

        @Override
        public boolean is(Holder<T> holder) {
            return holder.is(this.key());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return predicate.test(this.key());
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> context) {
            return this.owner.canSerializeIn(context);
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.left(this.key());
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(this.key());
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.REFERENCE;
        }

        @Override
        public boolean isBound() {
            return this.key != null && this.value != null;
        }

        void bindKey(ResourceKey<T> key) {
            if (this.key != null && key != this.key) {
                String s = String.valueOf(this.key);

                throw new IllegalStateException("Can't change holder key: existing=" + s + ", new=" + String.valueOf(key));
            } else {
                this.key = key;
            }
        }

        protected void bindValue(T value) {
            if (this.type == Holder.Reference.Type.INTRUSIVE && this.value != value) {
                String s = String.valueOf(this.key);

                throw new IllegalStateException("Can't change holder " + s + " value: existing=" + String.valueOf(this.value) + ", new=" + String.valueOf(value));
            } else {
                this.value = value;
            }
        }

        void bindTags(Collection<TagKey<T>> tags) {
            this.tags = Set.copyOf(tags);
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return this.boundTags().stream();
        }

        public String toString() {
            String s = String.valueOf(this.key);

            return "Reference{" + s + "=" + String.valueOf(this.value) + "}";
        }

        protected static enum Type {

            STAND_ALONE, INTRUSIVE;

            private Type() {}
        }
    }
}
