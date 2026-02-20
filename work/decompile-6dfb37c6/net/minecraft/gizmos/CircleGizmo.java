package net.minecraft.gizmos;

import net.minecraft.world.phys.Vec3;

public record CircleGizmo(Vec3 pos, float radius, GizmoStyle style) implements Gizmo {

    private static final int CIRCLE_VERTICES = 20;
    private static final float SEGMENT_SIZE_RADIANS = ((float) Math.PI / 10F);

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        if (this.style.hasStroke() || this.style.hasFill()) {
            Vec3[] avec3 = new Vec3[21];

            for (int i = 0; i < 20; ++i) {
                float f1 = (float) i * ((float) Math.PI / 10F);
                Vec3 vec3 = this.pos.add((double) ((float) ((double) this.radius * Math.cos((double) f1))), 0.0D, (double) ((float) ((double) this.radius * Math.sin((double) f1))));

                avec3[i] = vec3;
            }

            avec3[20] = avec3[0];
            if (this.style.hasFill()) {
                int j = this.style.multipliedFill(alphaMultiplier);

                primitives.addTriangleFan(avec3, j);
            }

            if (this.style.hasStroke()) {
                int k = this.style.multipliedStroke(alphaMultiplier);

                for (int l = 0; l < 20; ++l) {
                    primitives.addLine(avec3[l], avec3[l + 1], k, this.style.strokeWidth());
                }
            }

        }
    }
}
