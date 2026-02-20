package net.minecraft.world.entity.ai.gossip;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;

public class GossipContainer {

    public static final Codec<GossipContainer> CODEC = GossipContainer.GossipEntry.CODEC.listOf().xmap(GossipContainer::new, (gossipcontainer) -> {
        return gossipcontainer.unpack().toList();
    });
    public static final int DISCARD_THRESHOLD = 2;
    private final Map<UUID, GossipContainer.EntityGossips> gossips = new HashMap();

    public GossipContainer() {}

    private GossipContainer(List<GossipContainer.GossipEntry> entries) {
        entries.forEach((gossipcontainer_gossipentry) -> {
            this.getOrCreate(gossipcontainer_gossipentry.target).entries.put(gossipcontainer_gossipentry.type, gossipcontainer_gossipentry.value);
        });
    }

    @VisibleForDebug
    public Map<UUID, Object2IntMap<GossipType>> getGossipEntries() {
        Map<UUID, Object2IntMap<GossipType>> map = Maps.newHashMap();

        this.gossips.keySet().forEach((uuid) -> {
            GossipContainer.EntityGossips gossipcontainer_entitygossips = (GossipContainer.EntityGossips) this.gossips.get(uuid);

            map.put(uuid, gossipcontainer_entitygossips.entries);
        });
        return map;
    }

    public void decay() {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while (iterator.hasNext()) {
            GossipContainer.EntityGossips gossipcontainer_entitygossips = (GossipContainer.EntityGossips) iterator.next();

            gossipcontainer_entitygossips.decay();
            if (gossipcontainer_entitygossips.isEmpty()) {
                iterator.remove();
            }
        }

    }

    private Stream<GossipContainer.GossipEntry> unpack() {
        return this.gossips.entrySet().stream().flatMap((entry) -> {
            return ((GossipContainer.EntityGossips) entry.getValue()).unpack((UUID) entry.getKey());
        });
    }

    private Collection<GossipContainer.GossipEntry> selectGossipsForTransfer(RandomSource random, int maxCount) {
        List<GossipContainer.GossipEntry> list = this.unpack().toList();

        if (list.isEmpty()) {
            return Collections.emptyList();
        } else {
            int[] aint = new int[list.size()];
            int j = 0;

            for (int k = 0; k < list.size(); ++k) {
                GossipContainer.GossipEntry gossipcontainer_gossipentry = (GossipContainer.GossipEntry) list.get(k);

                j += Math.abs(gossipcontainer_gossipentry.weightedValue());
                aint[k] = j - 1;
            }

            Set<GossipContainer.GossipEntry> set = Sets.newIdentityHashSet();

            for (int l = 0; l < maxCount; ++l) {
                int i1 = random.nextInt(j);
                int j1 = Arrays.binarySearch(aint, i1);

                set.add((GossipContainer.GossipEntry) list.get(j1 < 0 ? -j1 - 1 : j1));
            }

            return set;
        }
    }

    private GossipContainer.EntityGossips getOrCreate(UUID target) {
        return (GossipContainer.EntityGossips) this.gossips.computeIfAbsent(target, (uuid1) -> {
            return new GossipContainer.EntityGossips();
        });
    }

    public void transferFrom(GossipContainer source, RandomSource random, int maxCount) {
        Collection<GossipContainer.GossipEntry> collection = source.selectGossipsForTransfer(random, maxCount);

        collection.forEach((gossipcontainer_gossipentry) -> {
            int j = gossipcontainer_gossipentry.value - gossipcontainer_gossipentry.type.decayPerTransfer;

            if (j >= 2) {
                this.getOrCreate(gossipcontainer_gossipentry.target).entries.mergeInt(gossipcontainer_gossipentry.type, j, GossipContainer::mergeValuesForTransfer);
            }

        });
    }

    public int getReputation(UUID entity, Predicate<GossipType> types) {
        GossipContainer.EntityGossips gossipcontainer_entitygossips = (GossipContainer.EntityGossips) this.gossips.get(entity);

        return gossipcontainer_entitygossips != null ? gossipcontainer_entitygossips.weightedValue(types) : 0;
    }

    public long getCountForType(GossipType type, DoublePredicate valueTest) {
        return this.gossips.values().stream().filter((gossipcontainer_entitygossips) -> {
            return valueTest.test((double) (gossipcontainer_entitygossips.entries.getOrDefault(type, 0) * type.weight));
        }).count();
    }

    public void add(UUID target, GossipType type, int amountToAdd) {
        GossipContainer.EntityGossips gossipcontainer_entitygossips = this.getOrCreate(target);

        gossipcontainer_entitygossips.entries.mergeInt(type, amountToAdd, (j, k) -> {
            return this.mergeValuesForAddition(type, j, k);
        });
        gossipcontainer_entitygossips.makeSureValueIsntTooLowOrTooHigh(type);
        if (gossipcontainer_entitygossips.isEmpty()) {
            this.gossips.remove(target);
        }

    }

    public void remove(UUID target, GossipType type, int amountToRemove) {
        this.add(target, type, -amountToRemove);
    }

    public void remove(UUID target, GossipType type) {
        GossipContainer.EntityGossips gossipcontainer_entitygossips = (GossipContainer.EntityGossips) this.gossips.get(target);

        if (gossipcontainer_entitygossips != null) {
            gossipcontainer_entitygossips.remove(type);
            if (gossipcontainer_entitygossips.isEmpty()) {
                this.gossips.remove(target);
            }
        }

    }

    public void remove(GossipType type) {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while (iterator.hasNext()) {
            GossipContainer.EntityGossips gossipcontainer_entitygossips = (GossipContainer.EntityGossips) iterator.next();

            gossipcontainer_entitygossips.remove(type);
            if (gossipcontainer_entitygossips.isEmpty()) {
                iterator.remove();
            }
        }

    }

    public void clear() {
        this.gossips.clear();
    }

    public void putAll(GossipContainer container) {
        container.gossips.forEach((uuid, gossipcontainer_entitygossips) -> {
            this.getOrCreate(uuid).entries.putAll(gossipcontainer_entitygossips.entries);
        });
    }

    private static int mergeValuesForTransfer(int oldValue, int newValue) {
        return Math.max(oldValue, newValue);
    }

    private int mergeValuesForAddition(GossipType type, int oldValue, int newValue) {
        int k = oldValue + newValue;

        return k > type.max ? Math.max(type.max, oldValue) : k;
    }

    public GossipContainer copy() {
        GossipContainer gossipcontainer = new GossipContainer();

        gossipcontainer.putAll(this);
        return gossipcontainer;
    }

    private static record GossipEntry(UUID target, GossipType type, int value) {

        public static final Codec<GossipContainer.GossipEntry> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(UUIDUtil.CODEC.fieldOf("Target").forGetter(GossipContainer.GossipEntry::target), GossipType.CODEC.fieldOf("Type").forGetter(GossipContainer.GossipEntry::type), ExtraCodecs.POSITIVE_INT.fieldOf("Value").forGetter(GossipContainer.GossipEntry::value)).apply(instance, GossipContainer.GossipEntry::new);
        });

        public int weightedValue() {
            return this.value * this.type.weight;
        }
    }

    private static class EntityGossips {

        private final Object2IntMap<GossipType> entries = new Object2IntOpenHashMap();

        private EntityGossips() {}

        public int weightedValue(Predicate<GossipType> types) {
            return this.entries.object2IntEntrySet().stream().filter((entry) -> {
                return types.test((GossipType) entry.getKey());
            }).mapToInt((entry) -> {
                return entry.getIntValue() * ((GossipType) entry.getKey()).weight;
            }).sum();
        }

        public Stream<GossipContainer.GossipEntry> unpack(UUID target) {
            return this.entries.object2IntEntrySet().stream().map((entry) -> {
                return new GossipContainer.GossipEntry(target, (GossipType) entry.getKey(), entry.getIntValue());
            });
        }

        public void decay() {
            ObjectIterator<Object2IntMap.Entry<GossipType>> objectiterator = this.entries.object2IntEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Object2IntMap.Entry<GossipType> object2intmap_entry = (Entry) objectiterator.next();
                int i = object2intmap_entry.getIntValue() - ((GossipType) object2intmap_entry.getKey()).decayPerDay;

                if (i < 2) {
                    objectiterator.remove();
                } else {
                    object2intmap_entry.setValue(i);
                }
            }

        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        public void makeSureValueIsntTooLowOrTooHigh(GossipType type) {
            int i = this.entries.getInt(type);

            if (i > type.max) {
                this.entries.put(type, type.max);
            }

            if (i < 2) {
                this.remove(type);
            }

        }

        public void remove(GossipType type) {
            this.entries.removeInt(type);
        }
    }
}
