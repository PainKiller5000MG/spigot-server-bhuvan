package net.minecraft.world.entity.ai.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.VisibleForDebug;

public class ExpirableValue<T> {

    private final T value;
    private long timeToLive;

    public ExpirableValue(T value, long timeToLive) {
        this.value = value;
        this.timeToLive = timeToLive;
    }

    public void tick() {
        if (this.canExpire()) {
            --this.timeToLive;
        }

    }

    public static <T> ExpirableValue<T> of(T value) {
        return new ExpirableValue<T>(value, Long.MAX_VALUE);
    }

    public static <T> ExpirableValue<T> of(T value, long ticksUntilExpiry) {
        return new ExpirableValue<T>(value, ticksUntilExpiry);
    }

    public long getTimeToLive() {
        return this.timeToLive;
    }

    public T getValue() {
        return this.value;
    }

    public boolean hasExpired() {
        return this.timeToLive <= 0L;
    }

    public String toString() {
        String s = String.valueOf(this.value);

        return s + (this.canExpire() ? " (ttl: " + this.timeToLive + ")" : "");
    }

    @VisibleForDebug
    public boolean canExpire() {
        return this.timeToLive != Long.MAX_VALUE;
    }

    public static <T> Codec<ExpirableValue<T>> codec(Codec<T> valueCodec) {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(valueCodec.fieldOf("value").forGetter((expirablevalue) -> {
                return expirablevalue.value;
            }), Codec.LONG.lenientOptionalFieldOf("ttl").forGetter((expirablevalue) -> {
                return expirablevalue.canExpire() ? Optional.of(expirablevalue.timeToLive) : Optional.empty();
            })).apply(instance, (object, optional) -> {
                return new ExpirableValue(object, (Long) optional.orElse(Long.MAX_VALUE));
            });
        });
    }
}
