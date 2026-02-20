package net.minecraft.world.level.biome;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jspecify.annotations.Nullable;

public class Climate {

    private static final boolean DEBUG_SLOW_BIOME_SEARCH = false;
    private static final float QUANTIZATION_FACTOR = 10000.0F;
    @VisibleForTesting
    protected static final int PARAMETER_COUNT = 7;

    public Climate() {}

    public static Climate.TargetPoint target(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness) {
        return new Climate.TargetPoint(quantizeCoord(temperature), quantizeCoord(humidity), quantizeCoord(continentalness), quantizeCoord(erosion), quantizeCoord(depth), quantizeCoord(weirdness));
    }

    public static Climate.ParameterPoint parameters(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness, float offset) {
        return new Climate.ParameterPoint(Climate.Parameter.point(temperature), Climate.Parameter.point(humidity), Climate.Parameter.point(continentalness), Climate.Parameter.point(erosion), Climate.Parameter.point(depth), Climate.Parameter.point(weirdness), quantizeCoord(offset));
    }

    public static Climate.ParameterPoint parameters(Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter depth, Climate.Parameter weirdness, float offset) {
        return new Climate.ParameterPoint(temperature, humidity, continentalness, erosion, depth, weirdness, quantizeCoord(offset));
    }

    public static long quantizeCoord(float coord) {
        return (long) (coord * 10000.0F);
    }

    public static float unquantizeCoord(long coord) {
        return (float) coord / 10000.0F;
    }

    public static Climate.Sampler empty() {
        DensityFunction densityfunction = DensityFunctions.zero();

        return new Climate.Sampler(densityfunction, densityfunction, densityfunction, densityfunction, densityfunction, densityfunction, List.of());
    }

    public static BlockPos findSpawnPosition(List<Climate.ParameterPoint> targetClimates, Climate.Sampler sampler) {
        return (new Climate.SpawnFinder(targetClimates, sampler)).result.location();
    }

    protected static final class RTree<T> {

        private static final int CHILDREN_PER_NODE = 6;
        private final Climate.RTree.Node<T> root;
        private final ThreadLocal<@Nullable Climate.RTree.Leaf<T>> lastResult = new ThreadLocal();

        private RTree(Climate.RTree.Node<T> root) {
            this.root = root;
        }

        public static <T> Climate.RTree<T> create(List<Pair<Climate.ParameterPoint, T>> values) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Need at least one value to build the search tree.");
            } else {
                int i = ((Climate.ParameterPoint) ((Pair) values.get(0)).getFirst()).parameterSpace().size();

                if (i != 7) {
                    throw new IllegalStateException("Expecting parameter space to be 7, got " + i);
                } else {
                    List<Climate.RTree.Leaf<T>> list1 = (List) values.stream().map((pair) -> {
                        return new Climate.RTree.Leaf((Climate.ParameterPoint) pair.getFirst(), pair.getSecond());
                    }).collect(Collectors.toCollection(ArrayList::new));

                    return new Climate.RTree<T>(build(i, list1));
                }
            }
        }

        private static <T> Climate.RTree.Node<T> build(int dimensions, List<? extends Climate.RTree.Node<T>> children) {
            if (children.isEmpty()) {
                throw new IllegalStateException("Need at least one child to build a node");
            } else if (children.size() == 1) {
                return (Climate.RTree.Node) children.get(0);
            } else if (children.size() <= 6) {
                children.sort(Comparator.comparingLong((climate_rtree_node) -> {
                    long j = 0L;

                    for (int k = 0; k < dimensions; ++k) {
                        Climate.Parameter climate_parameter = climate_rtree_node.parameterSpace[k];

                        j += Math.abs((climate_parameter.min() + climate_parameter.max()) / 2L);
                    }

                    return j;
                }));
                return new Climate.RTree.SubTree<T>(children);
            } else {
                long j = Long.MAX_VALUE;
                int k = -1;
                List<Climate.RTree.SubTree<T>> list1 = null;

                for (int l = 0; l < dimensions; ++l) {
                    sort(children, dimensions, l, false);
                    List<Climate.RTree.SubTree<T>> list2 = bucketize(children);
                    long i1 = 0L;

                    for (Climate.RTree.SubTree<T> climate_rtree_subtree : list2) {
                        i1 += cost(climate_rtree_subtree.parameterSpace);
                    }

                    if (j > i1) {
                        j = i1;
                        k = l;
                        list1 = list2;
                    }
                }

                sort(list1, dimensions, k, true);
                return new Climate.RTree.SubTree<T>((List) list1.stream().map((climate_rtree_subtree1) -> {
                    return build(dimensions, Arrays.asList(climate_rtree_subtree1.children));
                }).collect(Collectors.toList()));
            }
        }

        private static <T> void sort(List<? extends Climate.RTree.Node<T>> children, int dimensions, int dimension, boolean absolute) {
            Comparator<Climate.RTree.Node<T>> comparator = comparator(dimension, absolute);

            for (int k = 1; k < dimensions; ++k) {
                comparator = comparator.thenComparing(comparator((dimension + k) % dimensions, absolute));
            }

            children.sort(comparator);
        }

        private static <T> Comparator<Climate.RTree.Node<T>> comparator(int dimension, boolean absolute) {
            return Comparator.comparingLong((climate_rtree_node) -> {
                Climate.Parameter climate_parameter = climate_rtree_node.parameterSpace[dimension];
                long j = (climate_parameter.min() + climate_parameter.max()) / 2L;

                return absolute ? Math.abs(j) : j;
            });
        }

        private static <T> List<Climate.RTree.SubTree<T>> bucketize(List<? extends Climate.RTree.Node<T>> nodes) {
            List<Climate.RTree.SubTree<T>> list1 = Lists.newArrayList();
            List<Climate.RTree.Node<T>> list2 = Lists.newArrayList();
            int i = (int) Math.pow(6.0D, Math.floor(Math.log((double) nodes.size() - 0.01D) / Math.log(6.0D)));

            for (Climate.RTree.Node<T> climate_rtree_node : nodes) {
                list2.add(climate_rtree_node);
                if (list2.size() >= i) {
                    list1.add(new Climate.RTree.SubTree(list2));
                    list2 = Lists.newArrayList();
                }
            }

            if (!list2.isEmpty()) {
                list1.add(new Climate.RTree.SubTree(list2));
            }

            return list1;
        }

        private static long cost(Climate.Parameter[] parameterSpace) {
            long i = 0L;

            for (Climate.Parameter climate_parameter : parameterSpace) {
                i += Math.abs(climate_parameter.max() - climate_parameter.min());
            }

            return i;
        }

        private static <T> List<Climate.Parameter> buildParameterSpace(List<? extends Climate.RTree.Node<T>> children) {
            if (children.isEmpty()) {
                throw new IllegalArgumentException("SubTree needs at least one child");
            } else {
                int i = 7;
                List<Climate.Parameter> list1 = Lists.newArrayList();

                for (int j = 0; j < 7; ++j) {
                    list1.add((Object) null);
                }

                for (Climate.RTree.Node<T> climate_rtree_node : children) {
                    for (int k = 0; k < 7; ++k) {
                        list1.set(k, climate_rtree_node.parameterSpace[k].span((Climate.Parameter) list1.get(k)));
                    }
                }

                return list1;
            }
        }

        public T search(Climate.TargetPoint target, Climate.DistanceMetric<T> distanceMetric) {
            long[] along = target.toParameterArray();
            Climate.RTree.Leaf<T> climate_rtree_leaf = this.root.search(along, (Climate.RTree.Leaf) this.lastResult.get(), distanceMetric);

            this.lastResult.set(climate_rtree_leaf);
            return climate_rtree_leaf.value;
        }

        abstract static class Node<T> {

            protected final Climate.Parameter[] parameterSpace;

            protected Node(List<Climate.Parameter> parameterSpace) {
                this.parameterSpace = (Climate.Parameter[]) parameterSpace.toArray(new Climate.Parameter[0]);
            }

            protected abstract Climate.RTree.Leaf<T> search(long[] target, Climate.RTree.@Nullable Leaf<T> candidate, Climate.DistanceMetric<T> distanceMetric);

            protected long distance(long[] target) {
                long i = 0L;

                for (int j = 0; j < 7; ++j) {
                    i += Mth.square(this.parameterSpace[j].distance(target[j]));
                }

                return i;
            }

            public String toString() {
                return Arrays.toString(this.parameterSpace);
            }
        }

        private static final class Leaf<T> extends Climate.RTree.Node<T> {

            private final T value;

            private Leaf(Climate.ParameterPoint parameterPoint, T value) {
                super(parameterPoint.parameterSpace());
                this.value = value;
            }

            @Override
            protected Climate.RTree.Leaf<T> search(long[] target, Climate.RTree.@Nullable Leaf<T> candidate, Climate.DistanceMetric<T> distanceMetric) {
                return this;
            }
        }

        private static final class SubTree<T> extends Climate.RTree.Node<T> {

            private final Climate.RTree.Node<T>[] children;

            protected SubTree(List<? extends Climate.RTree.Node<T>> children) {
                this(Climate.RTree.buildParameterSpace(children), children);
            }

            protected SubTree(List<Climate.Parameter> parameterSpace, List<? extends Climate.RTree.Node<T>> children) {
                super(parameterSpace);
                this.children = (Climate.RTree.Node[]) children.toArray(new Climate.RTree.Node[0]);
            }

            @Override
            protected Climate.RTree.Leaf<T> search(long[] target, Climate.RTree.@Nullable Leaf<T> candidate, Climate.DistanceMetric<T> distanceMetric) {
                long i = candidate == null ? Long.MAX_VALUE : distanceMetric.distance(candidate, target);
                Climate.RTree.Leaf<T> climate_rtree_leaf1 = candidate;

                for (Climate.RTree.Node<T> climate_rtree_node : this.children) {
                    long j = distanceMetric.distance(climate_rtree_node, target);

                    if (i > j) {
                        Climate.RTree.Leaf<T> climate_rtree_leaf2 = climate_rtree_node.search(target, climate_rtree_leaf1, distanceMetric);
                        long k = climate_rtree_node == climate_rtree_leaf2 ? j : distanceMetric.distance(climate_rtree_leaf2, target);

                        if (i > k) {
                            i = k;
                            climate_rtree_leaf1 = climate_rtree_leaf2;
                        }
                    }
                }

                return climate_rtree_leaf1;
            }
        }
    }

    public static class ParameterList<T> {

        private final List<Pair<Climate.ParameterPoint, T>> values;
        private final Climate.RTree<T> index;

        public static <T> Codec<Climate.ParameterList<T>> codec(MapCodec<T> valueCodec) {
            return ExtraCodecs.nonEmptyList(RecordCodecBuilder.create((instance) -> {
                return instance.group(Climate.ParameterPoint.CODEC.fieldOf("parameters").forGetter(Pair::getFirst), valueCodec.forGetter(Pair::getSecond)).apply(instance, Pair::of);
            }).listOf()).xmap(Climate.ParameterList::new, Climate.ParameterList::values);
        }

        public ParameterList(List<Pair<Climate.ParameterPoint, T>> values) {
            this.values = values;
            this.index = Climate.RTree.<T>create(values);
        }

        public List<Pair<Climate.ParameterPoint, T>> values() {
            return this.values;
        }

        public T findValue(Climate.TargetPoint target) {
            return (T) this.findValueIndex(target);
        }

        @VisibleForTesting
        public T findValueBruteForce(Climate.TargetPoint target) {
            Iterator<Pair<Climate.ParameterPoint, T>> iterator = this.values().iterator();
            Pair<Climate.ParameterPoint, T> pair = (Pair) iterator.next();
            long i = ((Climate.ParameterPoint) pair.getFirst()).fitness(target);
            T t0 = (T) pair.getSecond();

            while (iterator.hasNext()) {
                Pair<Climate.ParameterPoint, T> pair1 = (Pair) iterator.next();
                long j = ((Climate.ParameterPoint) pair1.getFirst()).fitness(target);

                if (j < i) {
                    i = j;
                    t0 = (T) pair1.getSecond();
                }
            }

            return t0;
        }

        public T findValueIndex(Climate.TargetPoint target) {
            return (T) this.findValueIndex(target, Climate.RTree.Node::distance);
        }

        protected T findValueIndex(Climate.TargetPoint target, Climate.DistanceMetric<T> distanceMetric) {
            return this.index.search(target, distanceMetric);
        }
    }

    public static record TargetPoint(long temperature, long humidity, long continentalness, long erosion, long depth, long weirdness) {

        @VisibleForTesting
        protected long[] toParameterArray() {
            return new long[]{this.temperature, this.humidity, this.continentalness, this.erosion, this.depth, this.weirdness, 0L};
        }
    }

    public static record ParameterPoint(Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter depth, Climate.Parameter weirdness, long offset) {

        public static final Codec<Climate.ParameterPoint> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Climate.Parameter.CODEC.fieldOf("temperature").forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.temperature;
            }), Climate.Parameter.CODEC.fieldOf("humidity").forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.humidity;
            }), Climate.Parameter.CODEC.fieldOf("continentalness").forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.continentalness;
            }), Climate.Parameter.CODEC.fieldOf("erosion").forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.erosion;
            }), Climate.Parameter.CODEC.fieldOf("depth").forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.depth;
            }), Climate.Parameter.CODEC.fieldOf("weirdness").forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.weirdness;
            }), Codec.floatRange(0.0F, 1.0F).fieldOf("offset").xmap(Climate::quantizeCoord, Climate::unquantizeCoord).forGetter((climate_parameterpoint) -> {
                return climate_parameterpoint.offset;
            })).apply(instance, Climate.ParameterPoint::new);
        });

        private long fitness(Climate.TargetPoint target) {
            return Mth.square(this.temperature.distance(target.temperature)) + Mth.square(this.humidity.distance(target.humidity)) + Mth.square(this.continentalness.distance(target.continentalness)) + Mth.square(this.erosion.distance(target.erosion)) + Mth.square(this.depth.distance(target.depth)) + Mth.square(this.weirdness.distance(target.weirdness)) + Mth.square(this.offset);
        }

        protected List<Climate.Parameter> parameterSpace() {
            return ImmutableList.of(this.temperature, this.humidity, this.continentalness, this.erosion, this.depth, this.weirdness, new Climate.Parameter(this.offset, this.offset));
        }
    }

    public static record Parameter(long min, long max) {

        public static final Codec<Climate.Parameter> CODEC = ExtraCodecs.intervalCodec(Codec.floatRange(-2.0F, 2.0F), "min", "max", (ofloat, ofloat1) -> {
            return ofloat.compareTo(ofloat1) > 0 ? DataResult.error(() -> {
                return "Cannon construct interval, min > max (" + ofloat + " > " + ofloat1 + ")";
            }) : DataResult.success(new Climate.Parameter(Climate.quantizeCoord(ofloat), Climate.quantizeCoord(ofloat1)));
        }, (climate_parameter) -> {
            return Climate.unquantizeCoord(climate_parameter.min());
        }, (climate_parameter) -> {
            return Climate.unquantizeCoord(climate_parameter.max());
        });

        public static Climate.Parameter point(float min) {
            return span(min, min);
        }

        public static Climate.Parameter span(float min, float max) {
            if (min > max) {
                throw new IllegalArgumentException("min > max: " + min + " " + max);
            } else {
                return new Climate.Parameter(Climate.quantizeCoord(min), Climate.quantizeCoord(max));
            }
        }

        public static Climate.Parameter span(Climate.Parameter min, Climate.Parameter max) {
            if (min.min() > max.max()) {
                String s = String.valueOf(min);

                throw new IllegalArgumentException("min > max: " + s + " " + String.valueOf(max));
            } else {
                return new Climate.Parameter(min.min(), max.max());
            }
        }

        public String toString() {
            return this.min == this.max ? String.format(Locale.ROOT, "%d", this.min) : String.format(Locale.ROOT, "[%d-%d]", this.min, this.max);
        }

        public long distance(long target) {
            long j = target - this.max;
            long k = this.min - target;

            return j > 0L ? j : Math.max(k, 0L);
        }

        public long distance(Climate.Parameter target) {
            long i = target.min() - this.max;
            long j = this.min - target.max();

            return i > 0L ? i : Math.max(j, 0L);
        }

        public Climate.Parameter span(Climate.@Nullable Parameter other) {
            return other == null ? this : new Climate.Parameter(Math.min(this.min, other.min()), Math.max(this.max, other.max()));
        }
    }

    public static record Sampler(DensityFunction temperature, DensityFunction humidity, DensityFunction continentalness, DensityFunction erosion, DensityFunction depth, DensityFunction weirdness, List<Climate.ParameterPoint> spawnTarget) {

        public Climate.TargetPoint sample(int quartX, int quartY, int quartZ) {
            int l = QuartPos.toBlock(quartX);
            int i1 = QuartPos.toBlock(quartY);
            int j1 = QuartPos.toBlock(quartZ);
            DensityFunction.SinglePointContext densityfunction_singlepointcontext = new DensityFunction.SinglePointContext(l, i1, j1);

            return Climate.target((float) this.temperature.compute(densityfunction_singlepointcontext), (float) this.humidity.compute(densityfunction_singlepointcontext), (float) this.continentalness.compute(densityfunction_singlepointcontext), (float) this.erosion.compute(densityfunction_singlepointcontext), (float) this.depth.compute(densityfunction_singlepointcontext), (float) this.weirdness.compute(densityfunction_singlepointcontext));
        }

        public BlockPos findSpawnPosition() {
            return this.spawnTarget.isEmpty() ? BlockPos.ZERO : Climate.findSpawnPosition(this.spawnTarget, this);
        }
    }

    private static class SpawnFinder {

        private static final long MAX_RADIUS = 2048L;
        private Climate.SpawnFinder.Result result;

        private SpawnFinder(List<Climate.ParameterPoint> targetClimates, Climate.Sampler sampler) {
            this.result = getSpawnPositionAndFitness(targetClimates, sampler, 0, 0);
            this.radialSearch(targetClimates, sampler, 2048.0F, 512.0F);
            this.radialSearch(targetClimates, sampler, 512.0F, 32.0F);
        }

        private void radialSearch(List<Climate.ParameterPoint> targetClimates, Climate.Sampler sampler, float maxRadius, float radiusIncrement) {
            float f2 = 0.0F;
            float f3 = radiusIncrement;
            BlockPos blockpos = this.result.location();

            while (f3 <= maxRadius) {
                int i = blockpos.getX() + (int) (Math.sin((double) f2) * (double) f3);
                int j = blockpos.getZ() + (int) (Math.cos((double) f2) * (double) f3);
                Climate.SpawnFinder.Result climate_spawnfinder_result = getSpawnPositionAndFitness(targetClimates, sampler, i, j);

                if (climate_spawnfinder_result.fitness() < this.result.fitness()) {
                    this.result = climate_spawnfinder_result;
                }

                f2 += radiusIncrement / f3;
                if ((double) f2 > (Math.PI * 2D)) {
                    f2 = 0.0F;
                    f3 += radiusIncrement;
                }
            }

        }

        private static Climate.SpawnFinder.Result getSpawnPositionAndFitness(List<Climate.ParameterPoint> targetClimates, Climate.Sampler sampler, int blockX, int blockZ) {
            Climate.TargetPoint climate_targetpoint = sampler.sample(QuartPos.fromBlock(blockX), 0, QuartPos.fromBlock(blockZ));
            Climate.TargetPoint climate_targetpoint1 = new Climate.TargetPoint(climate_targetpoint.temperature(), climate_targetpoint.humidity(), climate_targetpoint.continentalness(), climate_targetpoint.erosion(), 0L, climate_targetpoint.weirdness());
            long k = Long.MAX_VALUE;

            for (Climate.ParameterPoint climate_parameterpoint : targetClimates) {
                k = Math.min(k, climate_parameterpoint.fitness(climate_targetpoint1));
            }

            long l = Mth.square((long) blockX) + Mth.square((long) blockZ);
            long i1 = k * Mth.square(2048L) + l;

            return new Climate.SpawnFinder.Result(new BlockPos(blockX, 0, blockZ), i1);
        }

        private static record Result(BlockPos location, long fitness) {

        }
    }

    interface DistanceMetric<T> {

        long distance(Climate.RTree.Node<T> node, long[] target);
    }
}
