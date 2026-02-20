package net.minecraft.gizmos;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record RectGizmo(Vec3 a, Vec3 b, Vec3 c, Vec3 d, GizmoStyle style) implements Gizmo {

    public static RectGizmo fromCuboidFace(Vec3 cuboidCornerA, Vec3 cuboidCornerB, Direction face, GizmoStyle style) {
        RectGizmo rectgizmo;

        switch (face) {
            case DOWN:
                rectgizmo = new RectGizmo(new Vec3(cuboidCornerA.x, cuboidCornerA.y, cuboidCornerA.z), new Vec3(cuboidCornerB.x, cuboidCornerA.y, cuboidCornerA.z), new Vec3(cuboidCornerB.x, cuboidCornerA.y, cuboidCornerB.z), new Vec3(cuboidCornerA.x, cuboidCornerA.y, cuboidCornerB.z), style);
                break;
            case UP:
                rectgizmo = new RectGizmo(new Vec3(cuboidCornerA.x, cuboidCornerB.y, cuboidCornerA.z), new Vec3(cuboidCornerA.x, cuboidCornerB.y, cuboidCornerB.z), new Vec3(cuboidCornerB.x, cuboidCornerB.y, cuboidCornerB.z), new Vec3(cuboidCornerB.x, cuboidCornerB.y, cuboidCornerA.z), style);
                break;
            case NORTH:
                rectgizmo = new RectGizmo(new Vec3(cuboidCornerA.x, cuboidCornerA.y, cuboidCornerA.z), new Vec3(cuboidCornerA.x, cuboidCornerB.y, cuboidCornerA.z), new Vec3(cuboidCornerB.x, cuboidCornerB.y, cuboidCornerA.z), new Vec3(cuboidCornerB.x, cuboidCornerA.y, cuboidCornerA.z), style);
                break;
            case SOUTH:
                rectgizmo = new RectGizmo(new Vec3(cuboidCornerA.x, cuboidCornerA.y, cuboidCornerB.z), new Vec3(cuboidCornerB.x, cuboidCornerA.y, cuboidCornerB.z), new Vec3(cuboidCornerB.x, cuboidCornerB.y, cuboidCornerB.z), new Vec3(cuboidCornerA.x, cuboidCornerB.y, cuboidCornerB.z), style);
                break;
            case WEST:
                rectgizmo = new RectGizmo(new Vec3(cuboidCornerA.x, cuboidCornerA.y, cuboidCornerA.z), new Vec3(cuboidCornerA.x, cuboidCornerA.y, cuboidCornerB.z), new Vec3(cuboidCornerA.x, cuboidCornerB.y, cuboidCornerB.z), new Vec3(cuboidCornerA.x, cuboidCornerB.y, cuboidCornerA.z), style);
                break;
            case EAST:
                rectgizmo = new RectGizmo(new Vec3(cuboidCornerB.x, cuboidCornerA.y, cuboidCornerA.z), new Vec3(cuboidCornerB.x, cuboidCornerB.y, cuboidCornerA.z), new Vec3(cuboidCornerB.x, cuboidCornerB.y, cuboidCornerB.z), new Vec3(cuboidCornerB.x, cuboidCornerA.y, cuboidCornerB.z), style);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return rectgizmo;
    }

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        if (this.style.hasFill()) {
            int i = this.style.multipliedFill(alphaMultiplier);

            primitives.addQuad(this.a, this.b, this.c, this.d, i);
        }

        if (this.style.hasStroke()) {
            int j = this.style.multipliedStroke(alphaMultiplier);

            primitives.addLine(this.a, this.b, j, this.style.strokeWidth());
            primitives.addLine(this.b, this.c, j, this.style.strokeWidth());
            primitives.addLine(this.c, this.d, j, this.style.strokeWidth());
            primitives.addLine(this.d, this.a, j, this.style.strokeWidth());
        }

    }
}
