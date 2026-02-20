package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class EntitySection<T extends EntityAccess> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ClassInstanceMultiMap<T> storage;
    private Visibility chunkStatus;

    public EntitySection(Class<T> entityClass, Visibility chunkStatus) {
        this.chunkStatus = chunkStatus;
        this.storage = new ClassInstanceMultiMap<T>(entityClass);
    }

    public void add(T entity) {
        this.storage.add(entity);
    }

    public boolean remove(T entity) {
        return this.storage.remove(entity);
    }

    public AbortableIterationConsumer.Continuation getEntities(AABB bb, AbortableIterationConsumer<T> entities) {
        for (T t0 : this.storage) {
            if (t0.getBoundingBox().intersects(bb) && entities.accept(t0).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    public <U extends T> AbortableIterationConsumer.Continuation getEntities(EntityTypeTest<T, U> type, AABB bb, AbortableIterationConsumer<? super U> consumer) {
        Collection<? extends T> collection = this.storage.<T>find(type.getBaseClass());

        if (collection.isEmpty()) {
            return AbortableIterationConsumer.Continuation.CONTINUE;
        } else {
            for (T t0 : collection) {
                U u0 = (U) ((EntityAccess) type.tryCast(t0));

                if (u0 != null && t0.getBoundingBox().intersects(bb) && consumer.accept(u0).shouldAbort()) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        }
    }

    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    public Stream<T> getEntities() {
        return this.storage.stream();
    }

    public Visibility getStatus() {
        return this.chunkStatus;
    }

    public Visibility updateChunkStatus(Visibility chunkStatus) {
        Visibility visibility1 = this.chunkStatus;

        this.chunkStatus = chunkStatus;
        return visibility1;
    }

    @VisibleForDebug
    public int size() {
        return this.storage.size();
    }
}
