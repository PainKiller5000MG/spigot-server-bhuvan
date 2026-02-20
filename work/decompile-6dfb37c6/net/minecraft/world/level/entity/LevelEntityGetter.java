package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface LevelEntityGetter<T extends EntityAccess> {

    @Nullable
    T get(int id);

    @Nullable
    T get(UUID id);

    Iterable<T> getAll();

    <U extends T> void get(EntityTypeTest<T, U> type, AbortableIterationConsumer<U> consumer);

    void get(AABB bb, Consumer<T> output);

    <U extends T> void get(EntityTypeTest<T, U> type, AABB bb, AbortableIterationConsumer<U> consumer);
}
