package net.minecraft.world.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TicketStorage extends SavedData {

    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Pair<ChunkPos, Ticket>> TICKET_ENTRY = Codec.mapPair(ChunkPos.CODEC.fieldOf("chunk_pos"), Ticket.CODEC).codec();
    public static final Codec<TicketStorage> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(TicketStorage.TICKET_ENTRY.listOf().optionalFieldOf("tickets", List.of()).forGetter(TicketStorage::packTickets)).apply(instance, TicketStorage::fromPacked);
    });
    public static final SavedDataType<TicketStorage> TYPE = new SavedDataType<TicketStorage>("chunks", TicketStorage::new, TicketStorage.CODEC, DataFixTypes.SAVED_DATA_FORCED_CHUNKS);
    public final Long2ObjectOpenHashMap<List<Ticket>> tickets;
    private final Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets;
    private LongSet chunksWithForcedTickets;
    private TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener;
    private TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener;

    private TicketStorage(Long2ObjectOpenHashMap<List<Ticket>> tickets, Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets) {
        this.chunksWithForcedTickets = new LongOpenHashSet();
        this.tickets = tickets;
        this.deactivatedTickets = deactivatedTickets;
        this.updateForcedChunks();
    }

    public TicketStorage() {
        this(new Long2ObjectOpenHashMap(4), new Long2ObjectOpenHashMap());
    }

    private static TicketStorage fromPacked(List<Pair<ChunkPos, Ticket>> tickets) {
        Long2ObjectOpenHashMap<List<Ticket>> long2objectopenhashmap = new Long2ObjectOpenHashMap();

        for (Pair<ChunkPos, Ticket> pair : tickets) {
            ChunkPos chunkpos = (ChunkPos) pair.getFirst();
            List<Ticket> list1 = (List) long2objectopenhashmap.computeIfAbsent(chunkpos.toLong(), (i) -> {
                return new ObjectArrayList(4);
            });

            list1.add((Ticket) pair.getSecond());
        }

        return new TicketStorage(new Long2ObjectOpenHashMap(4), long2objectopenhashmap);
    }

    private List<Pair<ChunkPos, Ticket>> packTickets() {
        List<Pair<ChunkPos, Ticket>> list = new ArrayList();

        this.forEachTicket((chunkpos, ticket) -> {
            if (ticket.getType().persist()) {
                list.add(new Pair(chunkpos, ticket));
            }

        });
        return list;
    }

    private void forEachTicket(BiConsumer<ChunkPos, Ticket> output) {
        forEachTicket(output, this.tickets);
        forEachTicket(output, this.deactivatedTickets);
    }

    private static void forEachTicket(BiConsumer<ChunkPos, Ticket> output, Long2ObjectOpenHashMap<List<Ticket>> tickets) {
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(tickets).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();
            ChunkPos chunkpos = new ChunkPos(long2objectmap_entry.getLongKey());

            for (Ticket ticket : (List) long2objectmap_entry.getValue()) {
                output.accept(chunkpos, ticket);
            }
        }

    }

    public void activateAllDeactivatedTickets() {
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(this.deactivatedTickets).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();

            for (Ticket ticket : (List) long2objectmap_entry.getValue()) {
                this.addTicket(long2objectmap_entry.getLongKey(), ticket);
            }
        }

        this.deactivatedTickets.clear();
    }

    public void setLoadingChunkUpdatedListener(TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener) {
        this.loadingChunkUpdatedListener = loadingChunkUpdatedListener;
    }

    public void setSimulationChunkUpdatedListener(TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener) {
        this.simulationChunkUpdatedListener = simulationChunkUpdatedListener;
    }

    public boolean hasTickets() {
        return !this.tickets.isEmpty();
    }

    public boolean shouldKeepDimensionActive() {
        ObjectIterator objectiterator = this.tickets.values().iterator();

        while (objectiterator.hasNext()) {
            List<Ticket> list = (List) objectiterator.next();

            for (Ticket ticket : list) {
                if (ticket.getType().shouldKeepDimensionActive()) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<Ticket> getTickets(long key) {
        return (List) this.tickets.getOrDefault(key, List.of());
    }

    private List<Ticket> getOrCreateTickets(long key) {
        return (List) this.tickets.computeIfAbsent(key, (j) -> {
            return new ObjectArrayList(4);
        });
    }

    public void addTicketWithRadius(TicketType type, ChunkPos chunkPos, int radius) {
        Ticket ticket = new Ticket(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius);

        this.addTicket(chunkPos.toLong(), ticket);
    }

    public void addTicket(Ticket ticket, ChunkPos chunkPos) {
        this.addTicket(chunkPos.toLong(), ticket);
    }

    public boolean addTicket(long key, Ticket ticket) {
        List<Ticket> list = this.getOrCreateTickets(key);

        for (Ticket ticket1 : list) {
            if (isTicketSameTypeAndLevel(ticket, ticket1)) {
                ticket1.resetTicksLeft();
                this.setDirty();
                return false;
            }
        }

        int j = getTicketLevelAt(list, true);
        int k = getTicketLevelAt(list, false);

        list.add(ticket);
        if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
            TicketStorage.LOGGER.debug("ATI {} {}", new ChunkPos(key), ticket);
        }

        if (ticket.getType().doesSimulate() && ticket.getTicketLevel() < j && this.simulationChunkUpdatedListener != null) {
            this.simulationChunkUpdatedListener.update(key, ticket.getTicketLevel(), true);
        }

        if (ticket.getType().doesLoad() && ticket.getTicketLevel() < k && this.loadingChunkUpdatedListener != null) {
            this.loadingChunkUpdatedListener.update(key, ticket.getTicketLevel(), true);
        }

        if (ticket.getType().equals(TicketType.FORCED)) {
            this.chunksWithForcedTickets.add(key);
        }

        this.setDirty();
        return true;
    }

    private static boolean isTicketSameTypeAndLevel(Ticket ticket, Ticket t) {
        return t.getType() == ticket.getType() && t.getTicketLevel() == ticket.getTicketLevel();
    }

    public int getTicketLevelAt(long key, boolean simulation) {
        return getTicketLevelAt(this.getTickets(key), simulation);
    }

    private static int getTicketLevelAt(List<Ticket> tickets, boolean simulation) {
        Ticket ticket = getLowestTicket(tickets, simulation);

        return ticket == null ? ChunkLevel.MAX_LEVEL + 1 : ticket.getTicketLevel();
    }

    private static @Nullable Ticket getLowestTicket(@Nullable List<Ticket> tickets, boolean simulation) {
        if (tickets == null) {
            return null;
        } else {
            Ticket ticket = null;

            for (Ticket ticket1 : tickets) {
                if (ticket == null || ticket1.getTicketLevel() < ticket.getTicketLevel()) {
                    if (simulation && ticket1.getType().doesSimulate()) {
                        ticket = ticket1;
                    } else if (!simulation && ticket1.getType().doesLoad()) {
                        ticket = ticket1;
                    }
                }
            }

            return ticket;
        }
    }

    public void removeTicketWithRadius(TicketType type, ChunkPos chunkPos, int radius) {
        Ticket ticket = new Ticket(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius);

        this.removeTicket(chunkPos.toLong(), ticket);
    }

    public void removeTicket(Ticket ticket, ChunkPos chunkPos) {
        this.removeTicket(chunkPos.toLong(), ticket);
    }

    public boolean removeTicket(long key, Ticket ticket) {
        List<Ticket> list = (List) this.tickets.get(key);

        if (list == null) {
            return false;
        } else {
            boolean flag = false;
            Iterator<Ticket> iterator = list.iterator();

            while (iterator.hasNext()) {
                Ticket ticket1 = (Ticket) iterator.next();

                if (isTicketSameTypeAndLevel(ticket, ticket1)) {
                    iterator.remove();
                    if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
                        TicketStorage.LOGGER.debug("RTI {} {}", new ChunkPos(key), ticket1);
                    }

                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                if (list.isEmpty()) {
                    this.tickets.remove(key);
                }

                if (ticket.getType().doesSimulate() && this.simulationChunkUpdatedListener != null) {
                    this.simulationChunkUpdatedListener.update(key, getTicketLevelAt(list, true), false);
                }

                if (ticket.getType().doesLoad() && this.loadingChunkUpdatedListener != null) {
                    this.loadingChunkUpdatedListener.update(key, getTicketLevelAt(list, false), false);
                }

                if (ticket.getType().equals(TicketType.FORCED)) {
                    this.updateForcedChunks();
                }

                this.setDirty();
                return true;
            }
        }
    }

    private void updateForcedChunks() {
        this.chunksWithForcedTickets = this.getAllChunksWithTicketThat((ticket) -> {
            return ticket.getType().equals(TicketType.FORCED);
        });
    }

    public String getTicketDebugString(long key, boolean simulation) {
        List<Ticket> list = this.getTickets(key);
        Ticket ticket = getLowestTicket(list, simulation);

        return ticket == null ? "no_ticket" : ticket.toString();
    }

    public void purgeStaleTickets(ChunkMap chunkMap) {
        this.removeTicketIf((ticket, i) -> {
            if (this.canTicketExpire(chunkMap, ticket, i)) {
                ticket.decreaseTicksLeft();
                return ticket.isTimedOut();
            } else {
                return false;
            }
        }, (Long2ObjectOpenHashMap) null);
        this.setDirty();
    }

    private boolean canTicketExpire(ChunkMap chunkMap, Ticket ticket, long chunkPos) {
        if (!ticket.getType().hasTimeout()) {
            return false;
        } else if (ticket.getType().canExpireIfUnloaded()) {
            return true;
        } else {
            ChunkHolder chunkholder = chunkMap.getUpdatingChunkIfPresent(chunkPos);

            return chunkholder == null || chunkholder.isReadyForSaving();
        }
    }

    public void deactivateTicketsOnClosing() {
        this.removeTicketIf((ticket, i) -> {
            return ticket.getType() != TicketType.UNKNOWN;
        }, this.deactivatedTickets);
    }

    public void removeTicketIf(TicketStorage.TicketPredicate predicate, @Nullable Long2ObjectOpenHashMap<List<Ticket>> removedTickets) {
        ObjectIterator<Long2ObjectMap.Entry<List<Ticket>>> objectiterator = this.tickets.long2ObjectEntrySet().fastIterator();
        boolean flag = false;

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();
            Iterator<Ticket> iterator = ((List) long2objectmap_entry.getValue()).iterator();
            long i = long2objectmap_entry.getLongKey();
            boolean flag1 = false;
            boolean flag2 = false;

            while (iterator.hasNext()) {
                Ticket ticket = (Ticket) iterator.next();

                if (predicate.test(ticket, i)) {
                    if (removedTickets != null) {
                        List<Ticket> list = (List) removedTickets.computeIfAbsent(i, (j) -> {
                            return new ObjectArrayList(((List) long2objectmap_entry.getValue()).size());
                        });

                        list.add(ticket);
                    }

                    iterator.remove();
                    if (ticket.getType().doesLoad()) {
                        flag2 = true;
                    }

                    if (ticket.getType().doesSimulate()) {
                        flag1 = true;
                    }

                    if (ticket.getType().equals(TicketType.FORCED)) {
                        flag = true;
                    }
                }
            }

            if (flag2 || flag1) {
                if (flag2 && this.loadingChunkUpdatedListener != null) {
                    this.loadingChunkUpdatedListener.update(i, getTicketLevelAt((List) long2objectmap_entry.getValue(), false), false);
                }

                if (flag1 && this.simulationChunkUpdatedListener != null) {
                    this.simulationChunkUpdatedListener.update(i, getTicketLevelAt((List) long2objectmap_entry.getValue(), true), false);
                }

                this.setDirty();
                if (((List) long2objectmap_entry.getValue()).isEmpty()) {
                    objectiterator.remove();
                }
            }
        }

        if (flag) {
            this.updateForcedChunks();
        }

    }

    public void replaceTicketLevelOfType(int newLevel, TicketType ticketType) {
        List<Pair<Ticket, Long>> list = new ArrayList();
        ObjectIterator objectiterator = this.tickets.long2ObjectEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();

            for (Ticket ticket : (List) long2objectmap_entry.getValue()) {
                if (ticket.getType() == ticketType) {
                    list.add(Pair.of(ticket, long2objectmap_entry.getLongKey()));
                }
            }
        }

        for (Pair<Ticket, Long> pair : list) {
            Long olong = (Long) pair.getSecond();
            Ticket ticket1 = (Ticket) pair.getFirst();

            this.removeTicket(olong, ticket1);
            TicketType tickettype1 = ticket1.getType();

            this.addTicket(olong, new Ticket(tickettype1, newLevel));
        }

    }

    public boolean updateChunkForced(ChunkPos chunkPos, boolean forced) {
        Ticket ticket = new Ticket(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL);

        return forced ? this.addTicket(chunkPos.toLong(), ticket) : this.removeTicket(chunkPos.toLong(), ticket);
    }

    public LongSet getForceLoadedChunks() {
        return this.chunksWithForcedTickets;
    }

    private LongSet getAllChunksWithTicketThat(Predicate<Ticket> ticketCheck) {
        LongOpenHashSet longopenhashset = new LongOpenHashSet();
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(this.tickets).iterator();

        while (objectiterator.hasNext()) {
            Long2ObjectMap.Entry<List<Ticket>> long2objectmap_entry = (Entry) objectiterator.next();

            for (Ticket ticket : (List) long2objectmap_entry.getValue()) {
                if (ticketCheck.test(ticket)) {
                    longopenhashset.add(long2objectmap_entry.getLongKey());
                    break;
                }
            }
        }

        return longopenhashset;
    }

    @FunctionalInterface
    public interface ChunkUpdated {

        void update(long node, int newLevelFrom, boolean onlyDecreased);
    }

    public interface TicketPredicate {

        boolean test(Ticket ticket, long chunkPos);
    }
}
