package net.minecraft.util;

import net.minecraft.core.Direction;

public class SegmentedAnglePrecision {

    private final int mask;
    private final int precision;
    private final float degreeToAngle;
    private final float angleToDegree;

    public SegmentedAnglePrecision(int bitPrecision) {
        if (bitPrecision < 2) {
            throw new IllegalArgumentException("Precision cannot be less than 2 bits");
        } else if (bitPrecision > 30) {
            throw new IllegalArgumentException("Precision cannot be greater than 30 bits");
        } else {
            int j = 1 << bitPrecision;

            this.mask = j - 1;
            this.precision = bitPrecision;
            this.degreeToAngle = (float) j / 360.0F;
            this.angleToDegree = 360.0F / (float) j;
        }
    }

    public boolean isSameAxis(int binaryAngleA, int binaryAngleB) {
        int k = this.getMask() >> 1;

        return (binaryAngleA & k) == (binaryAngleB & k);
    }

    public int fromDirection(Direction direction) {
        if (direction.getAxis().isVertical()) {
            return 0;
        } else {
            int i = direction.get2DDataValue();

            return i << this.precision - 2;
        }
    }

    public int fromDegreesWithTurns(float degrees) {
        return Math.round(degrees * this.degreeToAngle);
    }

    public int fromDegrees(float degrees) {
        return this.normalize(this.fromDegreesWithTurns(degrees));
    }

    public float toDegreesWithTurns(int binaryAngle) {
        return (float) binaryAngle * this.angleToDegree;
    }

    public float toDegrees(int binaryAngle) {
        float f = this.toDegreesWithTurns(this.normalize(binaryAngle));

        return f >= 180.0F ? f - 360.0F : f;
    }

    public int normalize(int binaryAngle) {
        return binaryAngle & this.mask;
    }

    public int getMask() {
        return this.mask;
    }
}
