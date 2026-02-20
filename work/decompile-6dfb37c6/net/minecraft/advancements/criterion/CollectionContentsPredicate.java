package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public interface CollectionContentsPredicate<T, P extends Predicate<T>> extends Predicate<Iterable<T>> {

    List<P> unpack();

    static <T, P extends Predicate<T>> Codec<CollectionContentsPredicate<T, P>> codec(Codec<P> elementCodec) {
        return elementCodec.listOf().xmap(CollectionContentsPredicate::of, CollectionContentsPredicate::unpack);
    }

    @SafeVarargs
    static <T, P extends Predicate<T>> CollectionContentsPredicate<T, P> of(P... predicates) {
        return of(List.of(predicates));
    }

    static <T, P extends Predicate<T>> CollectionContentsPredicate<T, P> of(List<P> predicates) {
        Object object;

        switch (predicates.size()) {
            case 0:
                object = new CollectionContentsPredicate.Zero();
                break;
            case 1:
                object = new CollectionContentsPredicate.Single((Predicate) predicates.getFirst());
                break;
            default:
                object = new CollectionContentsPredicate.Multiple(predicates);
        }

        return (CollectionContentsPredicate<T, P>) object;
    }

    public static class Zero<T, P extends Predicate<T>> implements CollectionContentsPredicate<T, P> {

        public Zero() {}

        public boolean test(Iterable<T> values) {
            return true;
        }

        @Override
        public List<P> unpack() {
            return List.of();
        }
    }

    public static record Single<T, P extends Predicate<T>>(P test) implements CollectionContentsPredicate<T, P> {

        public boolean test(Iterable<T> values) {
            for (T t0 : values) {
                if (this.test.test(t0)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public List<P> unpack() {
            return List.of(this.test);
        }
    }

    public static record Multiple<T, P extends Predicate<T>>(List<P> tests) implements CollectionContentsPredicate<T, P> {

        public boolean test(Iterable<T> values) {
            List<Predicate<T>> list = new ArrayList(this.tests);

            for (T t0 : values) {
                list.removeIf((predicate) -> {
                    return predicate.test(t0);
                });
                if (list.isEmpty()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public List<P> unpack() {
            return this.tests;
        }
    }
}
