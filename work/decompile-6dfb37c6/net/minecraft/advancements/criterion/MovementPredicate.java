package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

public record MovementPredicate(MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z, MinMaxBounds.Doubles speed, MinMaxBounds.Doubles horizontalSpeed, MinMaxBounds.Doubles verticalSpeed, MinMaxBounds.Doubles fallDistance) {

    public static final Codec<MovementPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(MinMaxBounds.Doubles.CODEC.optionalFieldOf("x", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::x), MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::y), MinMaxBounds.Doubles.CODEC.optionalFieldOf("z", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::z), MinMaxBounds.Doubles.CODEC.optionalFieldOf("speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::speed), MinMaxBounds.Doubles.CODEC.optionalFieldOf("horizontal_speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::horizontalSpeed), MinMaxBounds.Doubles.CODEC.optionalFieldOf("vertical_speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::verticalSpeed), MinMaxBounds.Doubles.CODEC.optionalFieldOf("fall_distance", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::fallDistance)).apply(instance, MovementPredicate::new);
    });

    public static MovementPredicate speed(MinMaxBounds.Doubles bounds) {
        return new MovementPredicate(MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, bounds, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY);
    }

    public static MovementPredicate horizontalSpeed(MinMaxBounds.Doubles bounds) {
        return new MovementPredicate(MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, bounds, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY);
    }

    public static MovementPredicate verticalSpeed(MinMaxBounds.Doubles bounds) {
        return new MovementPredicate(MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, bounds, MinMaxBounds.Doubles.ANY);
    }

    public static MovementPredicate fallDistance(MinMaxBounds.Doubles bounds) {
        return new MovementPredicate(MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, bounds);
    }

    public boolean matches(double x, double y, double z, double fallDistance) {
        if (this.x.matches(x) && this.y.matches(y) && this.z.matches(z)) {
            double d4 = Mth.lengthSquared(x, y, z);

            if (!this.speed.matchesSqr(d4)) {
                return false;
            } else {
                double d5 = Mth.lengthSquared(x, z);

                if (!this.horizontalSpeed.matchesSqr(d5)) {
                    return false;
                } else {
                    double d6 = Math.abs(y);

                    return !this.verticalSpeed.matches(d6) ? false : this.fallDistance.matches(fallDistance);
                }
            }
        } else {
            return false;
        }
    }
}
