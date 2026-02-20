package net.minecraft.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Node {

    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    public int heapIdx = -1;
    public float g;
    public float h;
    public float f;
    public @Nullable Node cameFrom;
    public boolean closed;
    public float walkedDistance;
    public float costMalus;
    public PathType type;

    public Node(int x, int y, int z) {
        this.type = PathType.BLOCKED;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = createHash(x, y, z);
    }

    public Node cloneAndMove(int x, int y, int z) {
        Node node = new Node(x, y, z);

        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }

    public static int createHash(int x, int y, int z) {
        return y & 255 | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? '\u8000' : 0);
    }

    public float distanceTo(Node to) {
        float f = (float) (to.x - this.x);
        float f1 = (float) (to.y - this.y);
        float f2 = (float) (to.z - this.z);

        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float distanceToXZ(Node to) {
        float f = (float) (to.x - this.x);
        float f1 = (float) (to.z - this.z);

        return Mth.sqrt(f * f + f1 * f1);
    }

    public float distanceTo(BlockPos pos) {
        float f = (float) (pos.getX() - this.x);
        float f1 = (float) (pos.getY() - this.y);
        float f2 = (float) (pos.getZ() - this.z);

        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float distanceToSqr(Node to) {
        float f = (float) (to.x - this.x);
        float f1 = (float) (to.y - this.y);
        float f2 = (float) (to.z - this.z);

        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceToSqr(BlockPos pos) {
        float f = (float) (pos.getX() - this.x);
        float f1 = (float) (pos.getY() - this.y);
        float f2 = (float) (pos.getZ() - this.z);

        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceManhattan(Node to) {
        float f = (float) Math.abs(to.x - this.x);
        float f1 = (float) Math.abs(to.y - this.y);
        float f2 = (float) Math.abs(to.z - this.z);

        return f + f1 + f2;
    }

    public float distanceManhattan(BlockPos pos) {
        float f = (float) Math.abs(pos.getX() - this.x);
        float f1 = (float) Math.abs(pos.getY() - this.y);
        float f2 = (float) Math.abs(pos.getZ() - this.z);

        return f + f1 + f2;
    }

    public BlockPos asBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public Vec3 asVec3() {
        return new Vec3((double) this.x, (double) this.y, (double) this.z);
    }

    public boolean equals(Object o) {
        if (!(o instanceof Node node)) {
            return false;
        } else {
            return this.hash == node.hash && this.x == node.x && this.y == node.y && this.z == node.z;
        }
    }

    public int hashCode() {
        return this.hash;
    }

    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }

    public void writeToStream(FriendlyByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeFloat(this.walkedDistance);
        buffer.writeFloat(this.costMalus);
        buffer.writeBoolean(this.closed);
        buffer.writeEnum(this.type);
        buffer.writeFloat(this.f);
    }

    public static Node createFromStream(FriendlyByteBuf buffer) {
        Node node = new Node(buffer.readInt(), buffer.readInt(), buffer.readInt());

        readContents(buffer, node);
        return node;
    }

    protected static void readContents(FriendlyByteBuf buffer, Node node) {
        node.walkedDistance = buffer.readFloat();
        node.costMalus = buffer.readFloat();
        node.closed = buffer.readBoolean();
        node.type = (PathType) buffer.readEnum(PathType.class);
        node.f = buffer.readFloat();
    }
}
