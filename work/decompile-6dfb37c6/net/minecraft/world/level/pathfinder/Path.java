package net.minecraft.world.level.pathfinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class Path {

    public static final StreamCodec<FriendlyByteBuf, Path> STREAM_CODEC = StreamCodec.<FriendlyByteBuf, Path>of((friendlybytebuf, path) -> {
        path.writeToStream(friendlybytebuf);
    }, Path::createFromStream);
    private final List<Node> nodes;
    private Path.@Nullable DebugData debugData;
    private int nextNodeIndex;
    private final BlockPos target;
    private final float distToTarget;
    private final boolean reached;

    public Path(List<Node> nodes, BlockPos target, boolean reached) {
        this.nodes = nodes;
        this.target = target;
        this.distToTarget = nodes.isEmpty() ? Float.MAX_VALUE : ((Node) this.nodes.get(this.nodes.size() - 1)).distanceManhattan(this.target);
        this.reached = reached;
    }

    public void advance() {
        ++this.nextNodeIndex;
    }

    public boolean notStarted() {
        return this.nextNodeIndex <= 0;
    }

    public boolean isDone() {
        return this.nextNodeIndex >= this.nodes.size();
    }

    public @Nullable Node getEndNode() {
        return !this.nodes.isEmpty() ? (Node) this.nodes.get(this.nodes.size() - 1) : null;
    }

    public Node getNode(int i) {
        return (Node) this.nodes.get(i);
    }

    public void truncateNodes(int index) {
        if (this.nodes.size() > index) {
            this.nodes.subList(index, this.nodes.size()).clear();
        }

    }

    public void replaceNode(int index, Node replaceWith) {
        this.nodes.set(index, replaceWith);
    }

    public int getNodeCount() {
        return this.nodes.size();
    }

    public int getNextNodeIndex() {
        return this.nextNodeIndex;
    }

    public void setNextNodeIndex(int nextNodeIndex) {
        this.nextNodeIndex = nextNodeIndex;
    }

    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        Node node = (Node) this.nodes.get(index);
        double d0 = (double) node.x + (double) ((int) (entity.getBbWidth() + 1.0F)) * 0.5D;
        double d1 = (double) node.y;
        double d2 = (double) node.z + (double) ((int) (entity.getBbWidth() + 1.0F)) * 0.5D;

        return new Vec3(d0, d1, d2);
    }

    public BlockPos getNodePos(int index) {
        return ((Node) this.nodes.get(index)).asBlockPos();
    }

    public Vec3 getNextEntityPos(Entity entity) {
        return this.getEntityPosAtNode(entity, this.nextNodeIndex);
    }

    public BlockPos getNextNodePos() {
        return ((Node) this.nodes.get(this.nextNodeIndex)).asBlockPos();
    }

    public Node getNextNode() {
        return (Node) this.nodes.get(this.nextNodeIndex);
    }

    public @Nullable Node getPreviousNode() {
        return this.nextNodeIndex > 0 ? (Node) this.nodes.get(this.nextNodeIndex - 1) : null;
    }

    public boolean sameAs(@Nullable Path path) {
        return path != null && this.nodes.equals(path.nodes);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Path path)) {
            return false;
        } else {
            return this.nextNodeIndex == path.nextNodeIndex && this.debugData == path.debugData && this.reached == path.reached && this.target.equals(path.target) && this.nodes.equals(path.nodes);
        }
    }

    public int hashCode() {
        return this.nextNodeIndex + this.nodes.hashCode() * 31;
    }

    public boolean canReach() {
        return this.reached;
    }

    @VisibleForDebug
    void setDebug(Node[] openSet, Node[] closedSet, Set<Target> targets) {
        this.debugData = new Path.DebugData(openSet, closedSet, targets);
    }

    public Path.@Nullable DebugData debugData() {
        return this.debugData;
    }

    public void writeToStream(FriendlyByteBuf buffer) {
        if (this.debugData != null && !this.debugData.targetNodes.isEmpty()) {
            buffer.writeBoolean(this.reached);
            buffer.writeInt(this.nextNodeIndex);
            buffer.writeBlockPos(this.target);
            buffer.writeCollection(this.nodes, (friendlybytebuf1, node) -> {
                node.writeToStream(friendlybytebuf1);
            });
            this.debugData.write(buffer);
        } else {
            throw new IllegalStateException("Missing debug data");
        }
    }

    public static Path createFromStream(FriendlyByteBuf buffer) {
        boolean flag = buffer.readBoolean();
        int i = buffer.readInt();
        BlockPos blockpos = buffer.readBlockPos();
        List<Node> list = buffer.<Node>readList(Node::createFromStream);
        Path.DebugData path_debugdata = Path.DebugData.read(buffer);
        Path path = new Path(list, blockpos, flag);

        path.debugData = path_debugdata;
        path.nextNodeIndex = i;
        return path;
    }

    public String toString() {
        return "Path(length=" + this.nodes.size() + ")";
    }

    public BlockPos getTarget() {
        return this.target;
    }

    public float getDistToTarget() {
        return this.distToTarget;
    }

    private static Node[] readNodeArray(FriendlyByteBuf input) {
        Node[] anode = new Node[input.readVarInt()];

        for (int i = 0; i < anode.length; ++i) {
            anode[i] = Node.createFromStream(input);
        }

        return anode;
    }

    private static void writeNodeArray(FriendlyByteBuf output, Node[] nodes) {
        output.writeVarInt(nodes.length);

        for (Node node : nodes) {
            node.writeToStream(output);
        }

    }

    public Path copy() {
        Path path = new Path(this.nodes, this.target, this.reached);

        path.debugData = this.debugData;
        path.nextNodeIndex = this.nextNodeIndex;
        return path;
    }

    public static record DebugData(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {

        public void write(FriendlyByteBuf output) {
            output.writeCollection(this.targetNodes, (friendlybytebuf1, target) -> {
                target.writeToStream(friendlybytebuf1);
            });
            Path.writeNodeArray(output, this.openSet);
            Path.writeNodeArray(output, this.closedSet);
        }

        public static Path.DebugData read(FriendlyByteBuf input) {
            HashSet<Target> hashset = (HashSet) input.readCollection(HashSet::new, Target::createFromStream);
            Node[] anode = Path.readNodeArray(input);
            Node[] anode1 = Path.readNodeArray(input);

            return new Path.DebugData(anode, anode1, hashset);
        }
    }
}
