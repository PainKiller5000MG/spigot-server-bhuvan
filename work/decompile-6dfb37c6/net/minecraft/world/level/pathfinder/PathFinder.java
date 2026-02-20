package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import org.jspecify.annotations.Nullable;

public class PathFinder {

    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private int maxVisitedNodes;
    private final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();
    private BooleanSupplier captureDebug = () -> {
        return false;
    };

    public PathFinder(NodeEvaluator nodeEvaluator, int maxVisitedNodes) {
        this.nodeEvaluator = nodeEvaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public void setCaptureDebug(BooleanSupplier captureDebug) {
        this.captureDebug = captureDebug;
    }

    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public @Nullable Path findPath(PathNavigationRegion level, Mob entity, Set<BlockPos> targets, float maxPathLength, int reachRange, float maxVisitedNodesMultiplier) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(level, entity);
        Node node = this.nodeEvaluator.getStart();

        if (node == null) {
            return null;
        } else {
            Map<Target, BlockPos> map = (Map) targets.stream().collect(Collectors.toMap((blockpos) -> {
                return this.nodeEvaluator.getTarget((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());
            }, Function.identity()));
            Path path = this.findPath(node, map, maxPathLength, reachRange, maxVisitedNodesMultiplier);

            this.nodeEvaluator.done();
            return path;
        }
    }

    private @Nullable Path findPath(Node from, Map<Target, BlockPos> targetMap, float maxPathLength, int reachRange, float maxVisitedNodesMultiplier) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("find_path");
        profilerfiller.markForCharting(MetricCategory.PATH_FINDING);
        Set<Target> set = targetMap.keySet();

        from.g = 0.0F;
        from.h = this.getBestH(from, set);
        from.f = from.h;
        this.openSet.clear();
        this.openSet.insert(from);
        boolean flag = this.captureDebug.getAsBoolean();
        Set<Node> set1 = (Set<Node>) (flag ? new HashSet() : Set.of());
        int j = 0;
        Set<Target> set2 = Sets.newHashSetWithExpectedSize(set.size());
        int k = (int) ((float) this.maxVisitedNodes * maxVisitedNodesMultiplier);

        while (!this.openSet.isEmpty()) {
            ++j;
            if (j >= k) {
                break;
            }

            Node node1 = this.openSet.pop();

            node1.closed = true;

            for (Target target : set) {
                if (node1.distanceManhattan((Node) target) <= (float) reachRange) {
                    target.setReached();
                    set2.add(target);
                }
            }

            if (!set2.isEmpty()) {
                break;
            }

            if (flag) {
                set1.add(node1);
            }

            if (node1.distanceTo(from) < maxPathLength) {
                int l = this.nodeEvaluator.getNeighbors(this.neighbors, node1);

                for (int i1 = 0; i1 < l; ++i1) {
                    Node node2 = this.neighbors[i1];
                    float f2 = this.distance(node1, node2);

                    node2.walkedDistance = node1.walkedDistance + f2;
                    float f3 = node1.g + f2 + node2.costMalus;

                    if (node2.walkedDistance < maxPathLength && (!node2.inOpenSet() || f3 < node2.g)) {
                        node2.cameFrom = node1;
                        node2.g = f3;
                        node2.h = this.getBestH(node2, set) * 1.5F;
                        if (node2.inOpenSet()) {
                            this.openSet.changeCost(node2, node2.g + node2.h);
                        } else {
                            node2.f = node2.g + node2.h;
                            this.openSet.insert(node2);
                        }
                    }
                }
            }
        }

        Optional<Path> optional = !set2.isEmpty() ? set2.stream().map((target1) -> {
            return this.reconstructPath(target1.getBestNode(), (BlockPos) targetMap.get(target1), true);
        }).min(Comparator.comparingInt(Path::getNodeCount)) : set.stream().map((target1) -> {
            return this.reconstructPath(target1.getBestNode(), (BlockPos) targetMap.get(target1), false);
        }).min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));

        profilerfiller.pop();
        if (optional.isEmpty()) {
            return null;
        } else {
            Path path = (Path) optional.get();

            if (flag) {
                path.setDebug(this.openSet.getHeap(), (Node[]) set1.toArray((j1) -> {
                    return new Node[j1];
                }), set);
            }

            return path;
        }
    }

    protected float distance(Node from, Node to) {
        return from.distanceTo(to);
    }

    private float getBestH(Node from, Set<Target> targets) {
        float f = Float.MAX_VALUE;

        for (Target target : targets) {
            float f1 = from.distanceTo((Node) target);

            target.updateBest(f1, from);
            f = Math.min(f1, f);
        }

        return f;
    }

    private Path reconstructPath(Node closest, BlockPos target, boolean reached) {
        List<Node> list = Lists.newArrayList();
        Node node1 = closest;

        list.add(0, closest);

        while (node1.cameFrom != null) {
            node1 = node1.cameFrom;
            list.add(0, node1);
        }

        return new Path(list, target, reached);
    }
}
