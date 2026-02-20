package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class TransientEntitySectionManager<T extends EntityAccess> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final LevelCallback<T> callbacks;
    private final EntityLookup<T> entityStorage = new EntityLookup<T>();
    private final EntitySectionStorage<T> sectionStorage;
    private final LongSet tickingChunks = new LongOpenHashSet();
    private final LevelEntityGetter<T> entityGetter;

    public TransientEntitySectionManager(Class<T> entityClass, LevelCallback<T> callbacks) {
        this.sectionStorage = new EntitySectionStorage<T>(entityClass, (i) -> {
            return this.tickingChunks.contains(i) ? Visibility.TICKING : Visibility.TRACKED;
        });
        this.callbacks = callbacks;
        this.entityGetter = new LevelEntityGetterAdapter<T>(this.entityStorage, this.sectionStorage);
    }

    public void startTicking(ChunkPos pos) {
        long i = pos.toLong();

        this.tickingChunks.add(i);
        this.sectionStorage.getExistingSectionsInChunk(i).forEach((entitysection) -> {
            Visibility visibility = entitysection.updateChunkStatus(Visibility.TICKING);

            if (!visibility.isTicking()) {
                Stream stream = entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                });
                LevelCallback levelcallback = this.callbacks;

                Objects.requireNonNull(this.callbacks);
                stream.forEach(levelcallback::onTickingStart);
            }

        });
    }

    public void stopTicking(ChunkPos pos) {
        long i = pos.toLong();

        this.tickingChunks.remove(i);
        this.sectionStorage.getExistingSectionsInChunk(i).forEach((entitysection) -> {
            Visibility visibility = entitysection.updateChunkStatus(Visibility.TRACKED);

            if (visibility.isTicking()) {
                Stream stream = entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                });
                LevelCallback levelcallback = this.callbacks;

                Objects.requireNonNull(this.callbacks);
                stream.forEach(levelcallback::onTickingEnd);
            }

        });
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public void addEntity(T entity) {
        this.entityStorage.add(entity);
        long i = SectionPos.asLong(entity.blockPosition());
        EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);

        entitysection.add(entity);
        entity.setLevelCallback(new TransientEntitySectionManager.Callback(entity, i, entitysection));
        this.callbacks.onCreated(entity);
        this.callbacks.onTrackingStart(entity);
        if (entity.isAlwaysTicking() || entitysection.getStatus().isTicking()) {
            this.callbacks.onTickingStart(entity);
        }

    }

    @VisibleForDebug
    public int count() {
        return this.entityStorage.count();
    }

    private void removeSectionIfEmpty(long sectionPos, EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionPos);
        }

    }

    @VisibleForDebug
    public String gatherStats() {
        int i = this.entityStorage.count();

        return i + "," + this.sectionStorage.count() + "," + this.tickingChunks.size();
    }

    private class Callback implements EntityInLevelCallback {

        private final T entity;
        private long currentSectionKey;
        private EntitySection<T> currentSection;

        private Callback(T entity, long currentSectionKey, EntitySection<T> currentSection) {
            this.entity = entity;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }

        @Override
        public void onMove() {
            BlockPos blockpos = this.entity.blockPosition();
            long i = SectionPos.asLong(blockpos);

            if (i != this.currentSectionKey) {
                Visibility visibility = this.currentSection.getStatus();

                if (!this.currentSection.remove(this.entity)) {
                    TransientEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (moving to {})", new Object[]{this.entity, SectionPos.of(this.currentSectionKey), i});
                }

                TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = TransientEntitySectionManager.this.sectionStorage.getOrCreateSection(i);

                entitysection.add(this.entity);
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                TransientEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                if (!this.entity.isAlwaysTicking()) {
                    boolean flag = visibility.isTicking();
                    boolean flag1 = entitysection.getStatus().isTicking();

                    if (flag && !flag1) {
                        TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
                    } else if (!flag && flag1) {
                        TransientEntitySectionManager.this.callbacks.onTickingStart(this.entity);
                    }
                }
            }

        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                TransientEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (destroying due to {})", new Object[]{this.entity, SectionPos.of(this.currentSectionKey), reason});
            }

            Visibility visibility = this.currentSection.getStatus();

            if (visibility.isTicking() || this.entity.isAlwaysTicking()) {
                TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
            }

            TransientEntitySectionManager.this.callbacks.onTrackingEnd(this.entity);
            TransientEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            TransientEntitySectionManager.this.entityStorage.remove(this.entity);
            this.entity.setLevelCallback(TransientEntitySectionManager.Callback.NULL);
            TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }
}
