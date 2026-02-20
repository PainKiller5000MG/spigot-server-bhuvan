package net.minecraft.gizmos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Gizmos {

    private static final ThreadLocal<@Nullable GizmoCollector> collector = new ThreadLocal();

    private Gizmos() {}

    public static Gizmos.TemporaryCollection withCollector(GizmoCollector collector) {
        Gizmos.TemporaryCollection gizmos_temporarycollection = new Gizmos.TemporaryCollection();

        Gizmos.collector.set(collector);
        return gizmos_temporarycollection;
    }

    public static GizmoProperties addGizmo(Gizmo gizmo) {
        GizmoCollector gizmocollector = (GizmoCollector) Gizmos.collector.get();

        if (gizmocollector == null) {
            throw new IllegalStateException("Gizmos cannot be created here! No GizmoCollector has been registered.");
        } else {
            return gizmocollector.add(gizmo);
        }
    }

    public static GizmoProperties cuboid(AABB aabb, GizmoStyle style) {
        return cuboid(aabb, style, false);
    }

    public static GizmoProperties cuboid(AABB aabb, GizmoStyle style, boolean coloredCorner) {
        return addGizmo(new CuboidGizmo(aabb, style, coloredCorner));
    }

    public static GizmoProperties cuboid(BlockPos blockPos, GizmoStyle style) {
        return cuboid(new AABB(blockPos), style);
    }

    public static GizmoProperties cuboid(BlockPos blockPos, float padding, GizmoStyle style) {
        return cuboid((new AABB(blockPos)).inflate((double) padding), style);
    }

    public static GizmoProperties circle(Vec3 pos, float radius, GizmoStyle style) {
        return addGizmo(new CircleGizmo(pos, radius, style));
    }

    public static GizmoProperties line(Vec3 start, Vec3 end, int argb) {
        return addGizmo(new LineGizmo(start, end, argb, 3.0F));
    }

    public static GizmoProperties line(Vec3 start, Vec3 end, int argb, float width) {
        return addGizmo(new LineGizmo(start, end, argb, width));
    }

    public static GizmoProperties arrow(Vec3 start, Vec3 end, int argb) {
        return addGizmo(new ArrowGizmo(start, end, argb, 2.5F));
    }

    public static GizmoProperties arrow(Vec3 start, Vec3 end, int argb, float width) {
        return addGizmo(new ArrowGizmo(start, end, argb, width));
    }

    public static GizmoProperties rect(Vec3 cuboidCornerA, Vec3 cuboidCornerB, Direction face, GizmoStyle style) {
        return addGizmo(RectGizmo.fromCuboidFace(cuboidCornerA, cuboidCornerB, face, style));
    }

    public static GizmoProperties rect(Vec3 cornerA, Vec3 cornerB, Vec3 cornerC, Vec3 cornerD, GizmoStyle style) {
        return addGizmo(new RectGizmo(cornerA, cornerB, cornerC, cornerD, style));
    }

    public static GizmoProperties point(Vec3 position, int argb, float size) {
        return addGizmo(new PointGizmo(position, argb, size));
    }

    public static GizmoProperties billboardTextOverBlock(String text, BlockPos pos, int row, int color, float scale) {
        double d0 = 1.3D;
        double d1 = 0.2D;
        GizmoProperties gizmoproperties = billboardText(text, Vec3.atLowerCornerWithOffset(pos, 0.5D, 1.3D + (double) row * 0.2D, 0.5D), TextGizmo.Style.forColorAndCentered(color).withScale(scale));

        gizmoproperties.setAlwaysOnTop();
        return gizmoproperties;
    }

    public static GizmoProperties billboardTextOverMob(Entity entity, int row, String text, int color, float scale) {
        double d0 = 2.4D;
        double d1 = 0.25D;
        double d2 = (double) entity.getBlockX() + 0.5D;
        double d3 = entity.getY() + 2.4D + (double) row * 0.25D;
        double d4 = (double) entity.getBlockZ() + 0.5D;
        float f1 = 0.5F;
        GizmoProperties gizmoproperties = billboardText(text, new Vec3(d2, d3, d4), TextGizmo.Style.forColor(color).withScale(scale).withLeftAlignment(0.5F));

        gizmoproperties.setAlwaysOnTop();
        return gizmoproperties;
    }

    public static GizmoProperties billboardText(String name, Vec3 pos, TextGizmo.Style style) {
        return addGizmo(new TextGizmo(pos, name, style));
    }

    public static class TemporaryCollection implements AutoCloseable {

        private final @Nullable GizmoCollector old;
        private boolean closed;

        private TemporaryCollection() {
            this.old = (GizmoCollector) Gizmos.collector.get();
        }

        public void close() {
            if (!this.closed) {
                this.closed = true;
                Gizmos.collector.set(this.old);
            }

        }
    }
}
