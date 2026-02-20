package net.minecraft.world.level.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<UUID> knownUuids = Sets.newHashSet();
    private final LevelCallback<T> callbacks;
    public final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage = new EntityLookup<T>();
    private final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    private final Long2ObjectMap<Visibility> chunkVisibility = new Long2ObjectOpenHashMap();
    private final Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new Long2ObjectOpenHashMap();
    private final LongSet chunksToUnload = new LongOpenHashSet();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(Class<T> entityClass, LevelCallback<T> callbacks, EntityPersistentStorage<T> permanentStorage) {
        this.sectionStorage = new EntitySectionStorage<T>(entityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = callbacks;
        this.permanentStorage = permanentStorage;
        this.entityGetter = new LevelEntityGetterAdapter<T>(this.visibleEntityStorage, this.sectionStorage);
    }

    private void removeSectionIfEmpty(long sectionPos, EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionPos);
        }

    }

    private boolean addEntityUuid(T entity) {
        if (!this.knownUuids.add(entity.getUUID())) {
            PersistentEntitySectionManager.LOGGER.warn("UUID of added entity already exists: {}", entity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(T entity) {
        return this.addEntity(entity, false);
    }

    private boolean addEntity(T entity, boolean loaded) {
        if (!this.addEntityUuid(entity)) {
            return false;
        } else {
            long i = SectionPos.asLong(entity.blockPosition());
            EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);

            entitysection.add(entity);
            entity.setLevelCallback(new PersistentEntitySectionManager.Callback(entity, i, entitysection));
            if (!loaded) {
                this.callbacks.onCreated(entity);
            }

            Visibility visibility = getEffectiveStatus(entity, entitysection.getStatus());

            if (visibility.isAccessible()) {
                this.startTracking(entity);
            }

            if (visibility.isTicking()) {
                this.startTicking(entity);
            }

            return true;
        }
    }

    private static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility status) {
        return entity.isAlwaysTicking() ? Visibility.TICKING : status;
    }

    public boolean isTicking(ChunkPos pos) {
        return ((Visibility) this.chunkVisibility.get(pos.toLong())).isTicking();
    }

    public void addLegacyChunkEntities(Stream<T> entities) {
        entities.forEach((entityaccess) -> {
            this.addEntity(entityaccess, true);
        });
    }

    public void addWorldGenChunkEntities(Stream<T> entities) {
        entities.forEach((entityaccess) -> {
            this.addEntity(entityaccess, false);
        });
    }

    private void startTicking(T entity) {
        this.callbacks.onTickingStart(entity);
    }

    private void stopTicking(T entity) {
        this.callbacks.onTickingEnd(entity);
    }

    private void startTracking(T entity) {
        this.visibleEntityStorage.add(entity);
        this.callbacks.onTrackingStart(entity);
    }

    private void stopTracking(T entity) {
        this.callbacks.onTrackingEnd(entity);
        this.visibleEntityStorage.remove(entity);
    }

    public void updateChunkStatus(ChunkPos pos, FullChunkStatus fullChunkStatus) {
        Visibility visibility = Visibility.fromFullChunkStatus(fullChunkStatus);

        this.updateChunkStatus(pos, visibility);
    }

    public void updateChunkStatus(ChunkPos pos, Visibility chunkStatus) {
        long i = pos.toLong();

        if (chunkStatus == Visibility.HIDDEN) {
            this.chunkVisibility.remove(i);
            this.chunksToUnload.add(i);
        } else {
            this.chunkVisibility.put(i, chunkStatus);
            this.chunksToUnload.remove(i);
            this.ensureChunkQueuedForLoad(i);
        }

        this.sectionStorage.getExistingSectionsInChunk(i).forEach((entitysection) -> {
            Visibility visibility1 = entitysection.updateChunkStatus(chunkStatus);
            boolean flag = visibility1.isAccessible();
            boolean flag1 = chunkStatus.isAccessible();
            boolean flag2 = visibility1.isTicking();
            boolean flag3 = chunkStatus.isTicking();

            if (flag2 && !flag3) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::stopTicking);
            }

            if (flag && !flag1) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::stopTracking);
            } else if (!flag && flag1) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::startTracking);
            }

            if (!flag2 && flag3) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::startTicking);
            }

        });
    }

    public void ensureChunkQueuedForLoad(long chunkPos) {
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager_chunkloadstatus = (PersistentEntitySectionManager.ChunkLoadStatus) this.chunkLoadStatuses.get(chunkPos);

        if (persistententitysectionmanager_chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPos);
        }

    }

    private boolean storeChunkSections(long chunkPos, Consumer<T> savedEntityVisitor) {
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager_chunkloadstatus = (PersistentEntitySectionManager.ChunkLoadStatus) this.chunkLoadStatuses.get(chunkPos);

        if (persistententitysectionmanager_chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        } else {
            List<T> list = (List) this.sectionStorage.getExistingSectionsInChunk(chunkPos).flatMap((entitysection) -> {
                return entitysection.getEntities().filter(EntityAccess::shouldBeSaved);
            }).collect(Collectors.toList());

            if (list.isEmpty()) {
                if (persistententitysectionmanager_chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                    this.permanentStorage.storeEntities(new ChunkEntities(new ChunkPos(chunkPos), ImmutableList.of()));
                }

                return true;
            } else if (persistententitysectionmanager_chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
                this.requestChunkLoad(chunkPos);
                return false;
            } else {
                this.permanentStorage.storeEntities(new ChunkEntities(new ChunkPos(chunkPos), list));
                list.forEach(savedEntityVisitor);
                return true;
            }
        }
    }

    private void requestChunkLoad(long chunkKey) {
        this.chunkLoadStatuses.put(chunkKey, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        ChunkPos chunkpos = new ChunkPos(chunkKey);
        CompletableFuture completablefuture = this.permanentStorage.loadEntities(chunkpos);
        Queue queue = this.loadingInbox;

        Objects.requireNonNull(this.loadingInbox);
        completablefuture.thenAccept(queue::add).exceptionally((throwable) -> {
            PersistentEntitySectionManager.LOGGER.error("Failed to read chunk {}", chunkpos, throwable);
            return null;
        });
    }

    private boolean processChunkUnload(long chunkKey) {
        boolean flag = this.storeChunkSections(chunkKey, (entityaccess) -> {
            entityaccess.getPassengersAndSelf().forEach(this::unloadEntity);
        });

        if (!flag) {
            return false;
        } else {
            this.chunkLoadStatuses.remove(chunkKey);
            return true;
        }
    }

    private void unloadEntity(EntityAccess e) {
        e.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        e.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload.removeIf((i) -> {
            return this.chunkVisibility.get(i) != Visibility.HIDDEN ? true : this.processChunkUnload(i);
        });
    }

    public void processPendingLoads() {
        ChunkEntities<T> chunkentities;

        while ((chunkentities = (ChunkEntities) this.loadingInbox.poll()) != null) {
            chunkentities.getEntities().forEach((entityaccess) -> {
                this.addEntity(entityaccess, true);
            });
            this.chunkLoadStatuses.put(chunkentities.getPos().toLong(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
        }

    }

    public void tick() {
        this.processPendingLoads();
        this.processUnloads();
    }

    private LongSet getAllChunksToSave() {
        LongSet longset = this.sectionStorage.getAllChunksWithExistingSections();
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(this.chunkLoadStatuses).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<PersistentEntitySectionManager.ChunkLoadStatus> long2objectmap_entry = (Entry) objectiterator.next();

            if (long2objectmap_entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                longset.add(long2objectmap_entry.getLongKey());
            }
        }

        return longset;
    }

    public void autoSave() {
        this.getAllChunksToSave().forEach((i) -> {
            boolean flag = this.chunkVisibility.get(i) == Visibility.HIDDEN;

            if (flag) {
                this.processChunkUnload(i);
            } else {
                this.storeChunkSections(i, (entityaccess) -> {
                });
            }

        });
    }

    public void saveAll() {
        LongSet longset = this.getAllChunksToSave();

        while (!longset.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            longset.removeIf((i) -> {
                boolean flag = this.chunkVisibility.get(i) == Visibility.HIDDEN;

                return flag ? this.processChunkUnload(i) : this.storeChunkSections(i, (entityaccess) -> {
                });
            });
        }

        this.permanentStorage.flush(true);
    }

    public void close() throws IOException {
        this.saveAll();
        this.permanentStorage.close();
    }

    public boolean isLoaded(UUID uuid) {
        return this.knownUuids.contains(uuid);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(BlockPos pos) {
        return ((Visibility) this.chunkVisibility.get(ChunkPos.asLong(pos))).isTicking();
    }

    public boolean canPositionTick(ChunkPos pos) {
        return ((Visibility) this.chunkVisibility.get(pos.toLong())).isTicking();
    }

    public boolean areEntitiesLoaded(long chunkKey) {
        return this.chunkLoadStatuses.get(chunkKey) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(Writer output) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("visibility").addColumn("load_status").addColumn("entity_count").build(output);

        this.sectionStorage.getAllChunksWithExistingSections().forEach((i) -> {
            PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager_chunkloadstatus = (PersistentEntitySectionManager.ChunkLoadStatus) this.chunkLoadStatuses.get(i);

            this.sectionStorage.getExistingSectionPositionsInChunk(i).forEach((j) -> {
                EntitySection<T> entitysection = this.sectionStorage.getSection(j);

                if (entitysection != null) {
                    try {
                        csvoutput.writeRow(SectionPos.x(j), SectionPos.y(j), SectionPos.z(j), entitysection.getStatus(), persistententitysectionmanager_chunkloadstatus, entitysection.size());
                    } catch (IOException ioexception) {
                        throw new UncheckedIOException(ioexception);
                    }
                }

            });
        });
    }

    @VisibleForDebug
    public String gatherStats() {
        int i = this.knownUuids.size();

        return i + "," + this.visibleEntityStorage.count() + "," + this.sectionStorage.count() + "," + this.chunkLoadStatuses.size() + "," + this.chunkVisibility.size() + "," + this.loadingInbox.size() + "," + this.chunksToUnload.size();
    }

    @VisibleForDebug
    public int count() {
        return this.visibleEntityStorage.count();
    }

    private static enum ChunkLoadStatus {

        FRESH, PENDING, LOADED;

        private ChunkLoadStatus() {}
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
                    PersistentEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (moving to {})", new Object[]{this.entity, SectionPos.of(this.currentSectionKey), i});
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(i);

                entitysection.add(this.entity);
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                this.updateStatus(visibility, entitysection.getStatus());
            }

        }

        private void updateStatus(Visibility previousStatus, Visibility newStatus) {
            Visibility visibility2 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, previousStatus);
            Visibility visibility3 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, newStatus);

            if (visibility2 == visibility3) {
                if (visibility3.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }

            } else {
                boolean flag = visibility2.isAccessible();
                boolean flag1 = visibility3.isAccessible();

                if (flag && !flag1) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!flag && flag1) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean flag2 = visibility2.isTicking();
                boolean flag3 = visibility3.isTicking();

                if (flag2 && !flag3) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!flag2 && flag3) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (flag1) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }

            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (destroying due to {})", new Object[]{this.entity, SectionPos.of(this.currentSectionKey), reason});
            }

            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());

            if (visibility.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (visibility.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (reason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(PersistentEntitySectionManager.Callback.NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }
}
