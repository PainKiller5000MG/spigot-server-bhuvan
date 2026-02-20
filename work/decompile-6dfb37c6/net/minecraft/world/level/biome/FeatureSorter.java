package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Graph;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;

public class FeatureSorter {

    public FeatureSorter() {}

    public static <T> List<FeatureSorter.StepFeatureData> buildFeaturesPerStep(List<T> featureSources, Function<T, List<HolderSet<PlacedFeature>>> featureGetter, boolean tryReducingError) {
        Object2IntMap<PlacedFeature> object2intmap = new Object2IntOpenHashMap();
        MutableInt mutableint = new MutableInt(0);

        record 1FeatureData(int featureIndex, int step, PlacedFeature feature) {

        }

        Comparator<1FeatureData> comparator = Comparator.comparingInt(1FeatureData::step).thenComparingInt(1FeatureData::featureIndex);
        Map<1FeatureData, Set<1FeatureData>> map = new TreeMap(comparator);
        int i = 0;

        for(T t0 : featureSources) {
            List<1FeatureData> list1 = Lists.newArrayList();
            List<HolderSet<PlacedFeature>> list2 = (List)featureGetter.apply(t0);

            i = Math.max(i, list2.size());

            for(int j = 0; j < list2.size(); ++j) {
                for(Holder<PlacedFeature> holder : (HolderSet)list2.get(j)) {
                    PlacedFeature placedfeature = holder.value();

                    list1.add(new 1FeatureData(object2intmap.computeIfAbsent(placedfeature, (object) -> {
                        return mutableint.getAndIncrement();
                    }), j, placedfeature));
                }
            }

            for(int k = 0; k < ((List)list1).size(); ++k) {
                Set<1FeatureData> set = (Set)map.computeIfAbsent((1FeatureData)list1.get(k), (1featuredata) -> {
                    return new TreeSet(comparator);
                });

                if (k < list1.size() - 1) {
                    set.add((1FeatureData)list1.get(k + 1));
                }
            }
        }

        Set<1FeatureData> set1 = new TreeSet(comparator);
        Set<1FeatureData> set2 = new TreeSet(comparator);
        List<1FeatureData> list3 = Lists.newArrayList();

        for(1FeatureData 1featuredata : map.keySet()) {
            if (!set2.isEmpty()) {
                throw new IllegalStateException("You somehow broke the universe; DFS bork (iteration finished with non-empty in-progress vertex set");
            }

            if (!set1.contains(1featuredata)) {
                Objects.requireNonNull(list3);
                if (Graph.depthFirstSearch(map, set1, set2, list3::add, 1featuredata)) {
                    if (!tryReducingError) {
                        throw new IllegalStateException("Feature order cycle found");
                    }

                    List<T> list4 = new ArrayList(featureSources);

                    int l;

                    do {
                        l = list4.size();
                        ListIterator<T> listiterator = list4.listIterator();

                        while(listiterator.hasNext()) {
                            T t1 = (T)listiterator.next();

                            listiterator.remove();

                            try {
                                buildFeaturesPerStep(list4, featureGetter, false);
                            } catch (IllegalStateException illegalstateexception) {
                                continue;
                            }

                            listiterator.add(t1);
                        }
                    } while(l != ((List)list4).size());

                    throw new IllegalStateException("Feature order cycle found, involved sources: " + String.valueOf(list4));
                }
            }
        }

        Collections.reverse(list3);
        ImmutableList.Builder<FeatureSorter.StepFeatureData> immutablelist_builder = ImmutableList.builder();

        for(int i1 = 0; i1 < i; ++i1) {
            List<PlacedFeature> list5 = (List)list3.stream().filter((1featuredata1) -> {
                return 1featuredata1.step() == i1;
            }).map(1FeatureData::feature).collect(Collectors.toList());

            immutablelist_builder.add(new FeatureSorter.StepFeatureData(list5));
        }

        return immutablelist_builder.build();
    }

    public static record StepFeatureData(List<PlacedFeature> features, ToIntFunction<PlacedFeature> indexMapping) {

        private StepFeatureData(List<PlacedFeature> features) {
            this(features, Util.createIndexIdentityLookup(features));
        }
    }
}
