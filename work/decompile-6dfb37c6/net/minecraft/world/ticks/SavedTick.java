package net.minecraft.world.ticks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public record SavedTick<T>(T type, BlockPos pos, int delay, TickPriority priority) {

    public static final Hash.Strategy<SavedTick<?>> UNIQUE_TICK_HASH = new Hash.Strategy<SavedTick<?>>() {
        public int hashCode(SavedTick<?> o) {
            return 31 * o.pos().hashCode() + o.type().hashCode();
        }

        public boolean equals(@Nullable SavedTick<?> a, @Nullable SavedTick<?> b) {
            return a == b ? true : (a != null && b != null ? a.type() == b.type() && a.pos().equals(b.pos()) : false);
        }
    };

    public static <T> Codec<SavedTick<T>> codec(Codec<T> typeCodec) {
        MapCodec<BlockPos> mapcodec = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.INT.fieldOf("x").forGetter(Vec3i::getX), Codec.INT.fieldOf("y").forGetter(Vec3i::getY), Codec.INT.fieldOf("z").forGetter(Vec3i::getZ)).apply(instance, BlockPos::new);
        });

        return RecordCodecBuilder.create((instance) -> {
            return instance.group(typeCodec.fieldOf("i").forGetter(SavedTick::type), mapcodec.forGetter(SavedTick::pos), Codec.INT.fieldOf("t").forGetter(SavedTick::delay), TickPriority.CODEC.fieldOf("p").forGetter(SavedTick::priority)).apply(instance, SavedTick::new);
        });
    }

    public static <T> List<SavedTick<T>> filterTickListForChunk(List<SavedTick<T>> savedTicks, ChunkPos chunkPos) {
        long i = chunkPos.toLong();

        return savedTicks.stream().filter((savedtick) -> {
            return ChunkPos.asLong(savedtick.pos()) == i;
        }).toList();
    }

    public ScheduledTick<T> unpack(long currentTick, long currentSubTick) {
        return new ScheduledTick<T>(this.type, this.pos, currentTick + (long) this.delay, this.priority, currentSubTick);
    }

    public static <T> SavedTick<T> probe(T type, BlockPos pos) {
        return new SavedTick<T>(type, pos, 0, TickPriority.NORMAL);
    }
}
