package net.minecraft.world.entity.ai.control;

import net.minecraft.util.Mth;

public interface Control {

    default float rotateTowards(float fromAngle, float toAngle, float maxRot) {
        float f3 = Mth.degreesDifference(fromAngle, toAngle);
        float f4 = Mth.clamp(f3, -maxRot, maxRot);

        return fromAngle + f4;
    }
}
