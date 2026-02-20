package net.minecraft.world.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ElytraAnimationState {

    private static final float DEFAULT_X_ROT = 0.2617994F;
    private static final float DEFAULT_Z_ROT = -0.2617994F;
    private float rotX;
    private float rotY;
    private float rotZ;
    private float rotXOld;
    private float rotYOld;
    private float rotZOld;
    private final LivingEntity entity;

    public ElytraAnimationState(LivingEntity entity) {
        this.entity = entity;
    }

    public void tick() {
        this.rotXOld = this.rotX;
        this.rotYOld = this.rotY;
        this.rotZOld = this.rotZ;
        float f;
        float f1;
        float f2;

        if (this.entity.isFallFlying()) {
            float f3 = 1.0F;
            Vec3 vec3 = this.entity.getDeltaMovement();

            if (vec3.y < 0.0D) {
                Vec3 vec31 = vec3.normalize();

                f3 = 1.0F - (float) Math.pow(-vec31.y, 1.5D);
            }

            f = Mth.lerp(f3, 0.2617994F, 0.34906584F);
            f1 = Mth.lerp(f3, -0.2617994F, (-(float) Math.PI / 2F));
            f2 = 0.0F;
        } else if (this.entity.isCrouching()) {
            f = 0.6981317F;
            f1 = (-(float) Math.PI / 4F);
            f2 = 0.08726646F;
        } else {
            f = 0.2617994F;
            f1 = -0.2617994F;
            f2 = 0.0F;
        }

        this.rotX += (f - this.rotX) * 0.3F;
        this.rotY += (f2 - this.rotY) * 0.3F;
        this.rotZ += (f1 - this.rotZ) * 0.3F;
    }

    public float getRotX(float partialTicks) {
        return Mth.lerp(partialTicks, this.rotXOld, this.rotX);
    }

    public float getRotY(float partialTicks) {
        return Mth.lerp(partialTicks, this.rotYOld, this.rotY);
    }

    public float getRotZ(float partialTicks) {
        return Mth.lerp(partialTicks, this.rotZOld, this.rotZ);
    }
}
