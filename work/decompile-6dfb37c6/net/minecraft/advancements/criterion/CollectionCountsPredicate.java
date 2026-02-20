package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Predicate;

public interface CollectionCountsPredicate<T, P extends Predicate<T>> extends Predicate<Iterable<T>> {

    List<CollectionCountsPredicate.Entry<T, P>> unpack();

    static <T, P extends Predicate<T>> Codec<CollectionCountsPredicate<T, P>> codec(Codec<P> elementCodec) {
        return CollectionCountsPredicate.Entry.codec(elementCodec).listOf().xmap(CollectionCountsPredicate::of, CollectionCountsPredicate::unpack);
    }

    @SafeVarargs
    static <T, P extends Predicate<T>> CollectionCountsPredicate<T, P> of(CollectionCountsPredicate.Entry<T, P>... predicates) {
        return of(List.of(predicates));
    }

    static <T, P extends Predicate<T>> CollectionCountsPredicate<T, P> of(List<CollectionCountsPredicate.Entry<T, P>> predicates) {
        Object object;

        switch (predicates.size()) {
            case 0:
                object = new CollectionCountsPredicate.Zero();
                break;
            case 1:
                object = new CollectionCountsPredicate.Single((CollectionCountsPredicate.Entry) predicates.getFirst());
                break;
            default:
                object = new CollectionCountsPredicate.Multiple(predicates);
        }

        return (CollectionCountsPredicate<T, P>) object;
    }

    public static class Zero<T, P extends Predicate<T>> implements CollectionCountsPredicate<T, P> {

        public Zero() {}

        public boolean test(Iterable<T> values) {
            return true;
        }

        @Override
        public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
            return List.of();
        }
    }

    public static record Single<T, P extends Predicate<T>>(CollectionCountsPredicate.Entry<T, P> entry) implements CollectionCountsPredicate<T, P> {

        public boolean test(Iterable<T> values) {
            return this.entry.test(values);
        }

        @Override
        public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
            return List.of(this.entry);
        }
    }

    public static record Multiple<T, P extends Predicate<T>>(List<CollectionCountsPredicate.Entry<T, P>> entries) implements CollectionCountsPredicate<T, P> {

        public boolean test(Iterable<T> values) {
            for (CollectionCountsPredicate.Entry<T, P> collectioncountspredicate_entry : this.entries) {
                if (!collectioncountspredicate_entry.test(values)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
            return this.entries;
        }
    }

    public static record Entry<T, P extends Predicate<T>>(P test, MinMaxBounds.Ints count) {

        public static <T, P extends Predicate<T>> Codec<CollectionCountsPredicate.Entry<T, P>> codec(Codec<P> elementCodec) {
            return RecordCodecBuilder.create((instance) -> {
                return instance.group(elementCodec.fieldOf("test").forGetter(CollectionCountsPredicate.Entry::test), MinMaxBounds.Ints.CODEC.fieldOf("count").forGetter(CollectionCountsPredicate.Entry::count)).apply(instance, CollectionCountsPredicate.Entry::new);
            });
        }

        public boolean test(Iterable<T> values) {
            int i = 0;

            for (T t0 : values) {
                if (this.test.test(t0)) {
                    ++i;
                }
            }

            return this.count.matches(i);
        }
    }
}
