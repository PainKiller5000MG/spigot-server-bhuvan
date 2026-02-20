package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class Vec2 {

    public static final Vec2 ZERO = new Vec2(0.0F, 0.0F);
    public static final Vec2 ONE = new Vec2(1.0F, 1.0F);
    public static final Vec2 UNIT_X = new Vec2(1.0F, 0.0F);
    public static final Vec2 NEG_UNIT_X = new Vec2(-1.0F, 0.0F);
    public static final Vec2 UNIT_Y = new Vec2(0.0F, 1.0F);
    public static final Vec2 NEG_UNIT_Y = new Vec2(0.0F, -1.0F);
    public static final Vec2 MAX = new Vec2(Float.MAX_VALUE, Float.MAX_VALUE);
    public static final Vec2 MIN = new Vec2(Float.MIN_VALUE, Float.MIN_VALUE);
    public static final Codec<Vec2> CODEC = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 2).map((list1) -> {
            return new Vec2((Float) list1.get(0), (Float) list1.get(1));
        });
    }, (vec2) -> {
        return List.of(vec2.x, vec2.y);
    });
    public final float x;
    public final float y;

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 scale(float s) {
        return new Vec2(this.x * s, this.y * s);
    }

    public float dot(Vec2 v) {
        return this.x * v.x + this.y * v.y;
    }

    public Vec2 add(Vec2 rhs) {
        return new Vec2(this.x + rhs.x, this.y + rhs.y);
    }

    public Vec2 add(float v) {
        return new Vec2(this.x + v, this.y + v);
    }

    public boolean equals(Vec2 rhs) {
        return this.x == rhs.x && this.y == rhs.y;
    }

    public Vec2 normalized() {
        float f = Mth.sqrt(this.x * this.x + this.y * this.y);

        return f < 1.0E-4F ? Vec2.ZERO : new Vec2(this.x / f, this.y / f);
    }

    public float length() {
        return Mth.sqrt(this.x * this.x + this.y * this.y);
    }

    public float lengthSquared() {
        return this.x * this.x + this.y * this.y;
    }

    public float distanceToSqr(Vec2 p) {
        float f = p.x - this.x;
        float f1 = p.y - this.y;

        return f * f + f1 * f1;
    }

    public Vec2 negated() {
        return new Vec2(-this.x, -this.y);
    }
}
