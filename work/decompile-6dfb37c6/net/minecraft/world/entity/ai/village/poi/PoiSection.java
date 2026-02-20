package net.minecraft.world.entity.ai.village.poi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.debug.DebugPoiInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PoiSection {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Short2ObjectMap<PoiRecord> records;
    private final Map<Holder<PoiType>, Set<PoiRecord>> byType;
    private final Runnable setDirty;
    private boolean isValid;

    public PoiSection(Runnable setDirty) {
        this(setDirty, true, ImmutableList.of());
    }

    private PoiSection(Runnable setDirty, boolean isValid, List<PoiRecord> records) {
        this.records = new Short2ObjectOpenHashMap();
        this.byType = Maps.newHashMap();
        this.setDirty = setDirty;
        this.isValid = isValid;
        records.forEach(this::add);
    }

    public PoiSection.Packed pack() {
        return new PoiSection.Packed(this.isValid, this.records.values().stream().map(PoiRecord::pack).toList());
    }

    public Stream<PoiRecord> getRecords(Predicate<Holder<PoiType>> predicate, PoiManager.Occupancy occupancy) {
        return this.byType.entrySet().stream().filter((entry) -> {
            return predicate.test((Holder) entry.getKey());
        }).flatMap((entry) -> {
            return ((Set) entry.getValue()).stream();
        }).filter(occupancy.getTest());
    }

    public @Nullable PoiRecord add(BlockPos blockPos, Holder<PoiType> type) {
        PoiRecord poirecord = new PoiRecord(blockPos, type, this.setDirty);

        if (this.add(poirecord)) {
            PoiSection.LOGGER.debug("Added POI of type {} @ {}", type.getRegisteredName(), blockPos);
            this.setDirty.run();
            return poirecord;
        } else {
            return null;
        }
    }

    private boolean add(PoiRecord record) {
        BlockPos blockpos = record.getPos();
        Holder<PoiType> holder = record.getPoiType();
        short short0 = SectionPos.sectionRelativePos(blockpos);
        PoiRecord poirecord1 = (PoiRecord) this.records.get(short0);

        if (poirecord1 != null) {
            if (holder.equals(poirecord1.getPoiType())) {
                return false;
            }

            Util.logAndPauseIfInIde("POI data mismatch: already registered at " + String.valueOf(blockpos));
        }

        this.records.put(short0, record);
        ((Set) this.byType.computeIfAbsent(holder, (holder1) -> {
            return Sets.newHashSet();
        })).add(record);
        return true;
    }

    public void remove(BlockPos pos) {
        PoiRecord poirecord = (PoiRecord) this.records.remove(SectionPos.sectionRelativePos(pos));

        if (poirecord == null) {
            PoiSection.LOGGER.error("POI data mismatch: never registered at {}", pos);
        } else {
            ((Set) this.byType.get(poirecord.getPoiType())).remove(poirecord);
            Logger logger = PoiSection.LOGGER;

            Objects.requireNonNull(poirecord);
            Object object = LogUtils.defer(poirecord::getPoiType);

            Objects.requireNonNull(poirecord);
            logger.debug("Removed POI of type {} @ {}", object, LogUtils.defer(poirecord::getPos));
            this.setDirty.run();
        }
    }

    /** @deprecated */
    @Deprecated
    @VisibleForDebug
    public int getFreeTickets(BlockPos pos) {
        return (Integer) this.getPoiRecord(pos).map(PoiRecord::getFreeTickets).orElse(0);
    }

    public boolean release(BlockPos pos) {
        PoiRecord poirecord = (PoiRecord) this.records.get(SectionPos.sectionRelativePos(pos));

        if (poirecord == null) {
            throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("POI never registered at " + String.valueOf(pos)));
        } else {
            boolean flag = poirecord.releaseTicket();

            this.setDirty.run();
            return flag;
        }
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> predicate) {
        return this.getType(pos).filter(predicate).isPresent();
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getPoiRecord(pos).map(PoiRecord::getPoiType);
    }

    private Optional<PoiRecord> getPoiRecord(BlockPos pos) {
        return Optional.ofNullable((PoiRecord) this.records.get(SectionPos.sectionRelativePos(pos)));
    }

    public Optional<DebugPoiInfo> getDebugPoiInfo(BlockPos pos) {
        return this.getPoiRecord(pos).map(DebugPoiInfo::new);
    }

    public void refresh(Consumer<BiConsumer<BlockPos, Holder<PoiType>>> updater) {
        if (!this.isValid) {
            Short2ObjectMap<PoiRecord> short2objectmap = new Short2ObjectOpenHashMap(this.records);

            this.clear();
            updater.accept((BiConsumer) (blockpos, holder) -> {
                short short0 = SectionPos.sectionRelativePos(blockpos);
                PoiRecord poirecord = (PoiRecord) short2objectmap.computeIfAbsent(short0, (short1) -> {
                    return new PoiRecord(blockpos, holder, this.setDirty);
                });

                this.add(poirecord);
            });
            this.isValid = true;
            this.setDirty.run();
        }

    }

    private void clear() {
        this.records.clear();
        this.byType.clear();
    }

    boolean isValid() {
        return this.isValid;
    }

    public static record Packed(boolean isValid, List<PoiRecord.Packed> records) {

        public static final Codec<PoiSection.Packed> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.BOOL.lenientOptionalFieldOf("Valid", false).forGetter(PoiSection.Packed::isValid), PoiRecord.Packed.CODEC.listOf().fieldOf("Records").forGetter(PoiSection.Packed::records)).apply(instance, PoiSection.Packed::new);
        });

        public PoiSection unpack(Runnable setDirty) {
            return new PoiSection(setDirty, this.isValid, this.records.stream().map((poirecord_packed) -> {
                return poirecord_packed.unpack(setDirty);
            }).toList());
        }
    }
}
