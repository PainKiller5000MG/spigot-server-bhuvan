package net.minecraft.network.protocol.game;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.world.phys.Vec3;

public class VecDeltaCodec {

    private static final double TRUNCATION_STEPS = 4096.0D;
    private Vec3 base;

    public VecDeltaCodec() {
        this.base = Vec3.ZERO;
    }

    @VisibleForTesting
    static long encode(double input) {
        return Math.round(input * 4096.0D);
    }

    @VisibleForTesting
    static double decode(long v) {
        return (double) v / 4096.0D;
    }

    public Vec3 decode(long xa, long ya, long za) {
        if (xa == 0L && ya == 0L && za == 0L) {
            return this.base;
        } else {
            double d0 = xa == 0L ? this.base.x : decode(encode(this.base.x) + xa);
            double d1 = ya == 0L ? this.base.y : decode(encode(this.base.y) + ya);
            double d2 = za == 0L ? this.base.z : decode(encode(this.base.z) + za);

            return new Vec3(d0, d1, d2);
        }
    }

    public long encodeX(Vec3 pos) {
        return encode(pos.x) - encode(this.base.x);
    }

    public long encodeY(Vec3 pos) {
        return encode(pos.y) - encode(this.base.y);
    }

    public long encodeZ(Vec3 pos) {
        return encode(pos.z) - encode(this.base.z);
    }

    public Vec3 delta(Vec3 pos) {
        return pos.subtract(this.base);
    }

    public void setBase(Vec3 base) {
        this.base = base;
    }

    public Vec3 getBase() {
        return this.base;
    }
}
