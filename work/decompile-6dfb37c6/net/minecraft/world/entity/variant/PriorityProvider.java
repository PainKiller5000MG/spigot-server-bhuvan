package net.minecraft.world.entity.variant;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public interface PriorityProvider<Context, Condition extends PriorityProvider.SelectorCondition<Context>> {

    List<PriorityProvider.Selector<Context, Condition>> selectors();

    static <C, T> Stream<T> select(Stream<T> entries, Function<T, PriorityProvider<C, ?>> extractor, C context) {
        List<PriorityProvider.UnpackedEntry<C, T>> list = new ArrayList();

        entries.forEach((object) -> {
            PriorityProvider<C, ?> priorityprovider = (PriorityProvider) extractor.apply(object);

            for (PriorityProvider.Selector<C, ?> priorityprovider_selector : priorityprovider.selectors()) {
                list.add(new PriorityProvider.UnpackedEntry(object, priorityprovider_selector.priority(), (PriorityProvider.SelectorCondition) DataFixUtils.orElseGet(priorityprovider_selector.condition(), PriorityProvider.SelectorCondition::alwaysTrue)));
            }

        });
        list.sort(PriorityProvider.UnpackedEntry.HIGHEST_PRIORITY_FIRST);
        Iterator<PriorityProvider.UnpackedEntry<C, T>> iterator = list.iterator();
        int i = Integer.MIN_VALUE;

        while (iterator.hasNext()) {
            PriorityProvider.UnpackedEntry<C, T> priorityprovider_unpackedentry = (PriorityProvider.UnpackedEntry) iterator.next();

            if (priorityprovider_unpackedentry.priority < i) {
                iterator.remove();
            } else if (priorityprovider_unpackedentry.condition.test(context)) {
                i = priorityprovider_unpackedentry.priority;
            } else {
                iterator.remove();
            }
        }

        return list.stream().map(PriorityProvider.UnpackedEntry::entry);
    }

    static <C, T> Optional<T> pick(Stream<T> entries, Function<T, PriorityProvider<C, ?>> extractor, RandomSource randomSource, C context) {
        List<T> list = select(entries, extractor, context).toList();

        return Util.<T>getRandomSafe(list, randomSource);
    }

    static <Context, Condition extends PriorityProvider.SelectorCondition<Context>> List<PriorityProvider.Selector<Context, Condition>> single(Condition check, int priority) {
        return List.of(new PriorityProvider.Selector(check, priority));
    }

    static <Context, Condition extends PriorityProvider.SelectorCondition<Context>> List<PriorityProvider.Selector<Context, Condition>> alwaysTrue(int priority) {
        return List.of(new PriorityProvider.Selector(Optional.empty(), priority));
    }

    public static record Selector<Context, Condition extends PriorityProvider.SelectorCondition<Context>>(Optional<Condition> condition, int priority) {

        public Selector(Condition condition, int priority) {
            this(Optional.of(condition), priority);
        }

        public Selector(int priority) {
            this(Optional.empty(), priority);
        }

        public static <Context, Condition extends PriorityProvider.SelectorCondition<Context>> Codec<PriorityProvider.Selector<Context, Condition>> codec(Codec<Condition> conditionCodec) {
            return RecordCodecBuilder.create((instance) -> {
                return instance.group(conditionCodec.optionalFieldOf("condition").forGetter(PriorityProvider.Selector::condition), Codec.INT.fieldOf("priority").forGetter(PriorityProvider.Selector::priority)).apply(instance, PriorityProvider.Selector::new);
            });
        }
    }

    @FunctionalInterface
    public interface SelectorCondition<C> extends Predicate<C> {

        static <C> PriorityProvider.SelectorCondition<C> alwaysTrue() {
            return (object) -> {
                return true;
            };
        }
    }

    public static record UnpackedEntry<C, T>(T entry, int priority, PriorityProvider.SelectorCondition<C> condition) {

        public static final Comparator<PriorityProvider.UnpackedEntry<?, ?>> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(PriorityProvider.UnpackedEntry::priority).reversed();
    }
}
