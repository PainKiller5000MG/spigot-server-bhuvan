package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Objects;
import java.util.PrimitiveIterator.OfLong;
import java.util.Spliterators;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class EntitySectionStorage<T extends EntityAccess> {

    public static final int CHONKY_ENTITY_SEARCH_GRACE = 2;
    public static final int MAX_NON_CHONKY_ENTITY_SIZE = 4;
    private final Class<T> entityClass;
    private final Long2ObjectFunction<Visibility> intialSectionVisibility;
    private final Long2ObjectMap<EntitySection<T>> sections = new Long2ObjectOpenHashMap();
    private final LongSortedSet sectionIds = new LongAVLTreeSet();

    public EntitySectionStorage(Class<T> entityClass, Long2ObjectFunction<Visibility> intialSectionVisibility) {
        this.entityClass = entityClass;
        this.intialSectionVisibility = intialSectionVisibility;
    }

    public void forEachAccessibleNonEmptySection(AABB bb, AbortableIterationConsumer<EntitySection<T>> output) {
        int i = SectionPos.posToSectionCoord(bb.minX - 2.0D);
        int j = SectionPos.posToSectionCoord(bb.minY - 4.0D);
        int k = SectionPos.posToSectionCoord(bb.minZ - 2.0D);
        int l = SectionPos.posToSectionCoord(bb.maxX + 2.0D);
        int i1 = SectionPos.posToSectionCoord(bb.maxY + 0.0D);
        int j1 = SectionPos.posToSectionCoord(bb.maxZ + 2.0D);

        for (int k1 = i; k1 <= l; ++k1) {
            long l1 = SectionPos.asLong(k1, 0, 0);
            long i2 = SectionPos.asLong(k1, -1, -1);
            LongIterator longiterator = this.sectionIds.subSet(l1, i2 + 1L).iterator();

            while (((LongIterator) longiterator).hasNext()) {
                long j2 = longiterator.nextLong();
                int k2 = SectionPos.y(j2);
                int l2 = SectionPos.z(j2);

                if (k2 >= j && k2 <= i1 && l2 >= k && l2 <= j1) {
                    EntitySection<T> entitysection = (EntitySection) this.sections.get(j2);

                    if (entitysection != null && !entitysection.isEmpty() && entitysection.getStatus().isAccessible() && output.accept(entitysection).shouldAbort()) {
                        return;
                    }
                }
            }
        }

    }

    public LongStream getExistingSectionPositionsInChunk(long chunkKey) {
        int j = ChunkPos.getX(chunkKey);
        int k = ChunkPos.getZ(chunkKey);
        LongSortedSet longsortedset = this.getChunkSections(j, k);

        if (longsortedset.isEmpty()) {
            return LongStream.empty();
        } else {
            OfLong oflong = longsortedset.iterator();

            return StreamSupport.longStream(Spliterators.spliteratorUnknownSize(oflong, 1301), false);
        }
    }

    private LongSortedSet getChunkSections(int x, int z) {
        long k = SectionPos.asLong(x, 0, z);
        long l = SectionPos.asLong(x, -1, z);

        return this.sectionIds.subSet(k, l + 1L);
    }

    public Stream<EntitySection<T>> getExistingSectionsInChunk(long chunkKey) {
        LongStream longstream = this.getExistingSectionPositionsInChunk(chunkKey);
        Long2ObjectMap long2objectmap = this.sections;

        Objects.requireNonNull(this.sections);
        return longstream.mapToObj(long2objectmap::get).filter(Objects::nonNull);
    }

    private static long getChunkKeyFromSectionKey(long sectionPos) {
        return ChunkPos.asLong(SectionPos.x(sectionPos), SectionPos.z(sectionPos));
    }

    public EntitySection<T> getOrCreateSection(long key) {
        return (EntitySection) this.sections.computeIfAbsent(key, this::createSection);
    }

    public @Nullable EntitySection<T> getSection(long key) {
        return (EntitySection) this.sections.get(key);
    }

    private EntitySection<T> createSection(long sectionPos) {
        long j = getChunkKeyFromSectionKey(sectionPos);
        Visibility visibility = (Visibility) this.intialSectionVisibility.get(j);

        this.sectionIds.add(sectionPos);
        return new EntitySection<T>(this.entityClass, visibility);
    }

    public LongSet getAllChunksWithExistingSections() {
        LongSet longset = new LongOpenHashSet();

        this.sections.keySet().forEach((i) -> {
            longset.add(getChunkKeyFromSectionKey(i));
        });
        return longset;
    }

    public void getEntities(AABB bb, AbortableIterationConsumer<T> output) {
        this.forEachAccessibleNonEmptySection(bb, (entitysection) -> {
            return entitysection.getEntities(bb, output);
        });
    }

    public <U extends T> void getEntities(EntityTypeTest<T, U> type, AABB bb, AbortableIterationConsumer<U> consumer) {
        this.forEachAccessibleNonEmptySection(bb, (entitysection) -> {
            return entitysection.getEntities(type, bb, consumer);
        });
    }

    public void remove(long sectionKey) {
        this.sections.remove(sectionKey);
        this.sectionIds.remove(sectionKey);
    }

    @VisibleForDebug
    public int count() {
        return this.sectionIds.size();
    }
}
