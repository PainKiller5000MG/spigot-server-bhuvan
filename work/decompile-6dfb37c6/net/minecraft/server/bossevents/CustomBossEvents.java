package net.minecraft.server.bossevents;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CustomBossEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<Identifier, CustomBossEvent.Packed>> EVENTS_CODEC = Codec.unboundedMap(Identifier.CODEC, CustomBossEvent.Packed.CODEC);
    private final Map<Identifier, CustomBossEvent> events = Maps.newHashMap();

    public CustomBossEvents() {}

    public @Nullable CustomBossEvent get(Identifier id) {
        return (CustomBossEvent) this.events.get(id);
    }

    public CustomBossEvent create(Identifier id, Component name) {
        CustomBossEvent custombossevent = new CustomBossEvent(id, name);

        this.events.put(id, custombossevent);
        return custombossevent;
    }

    public void remove(CustomBossEvent event) {
        this.events.remove(event.getTextId());
    }

    public Collection<Identifier> getIds() {
        return this.events.keySet();
    }

    public Collection<CustomBossEvent> getEvents() {
        return this.events.values();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        Map<Identifier, CustomBossEvent.Packed> map = Util.mapValues(this.events, CustomBossEvent::pack);

        return (CompoundTag) CustomBossEvents.EVENTS_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        Map<Identifier, CustomBossEvent.Packed> map = (Map) CustomBossEvents.EVENTS_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).resultOrPartial((s) -> {
            CustomBossEvents.LOGGER.error("Failed to parse boss bar events: {}", s);
        }).orElse(Map.of());

        map.forEach((identifier, custombossevent_packed) -> {
            this.events.put(identifier, CustomBossEvent.load(identifier, custombossevent_packed));
        });
    }

    public void onPlayerConnect(ServerPlayer player) {
        for (CustomBossEvent custombossevent : this.events.values()) {
            custombossevent.onPlayerConnect(player);
        }

    }

    public void onPlayerDisconnect(ServerPlayer player) {
        for (CustomBossEvent custombossevent : this.events.values()) {
            custombossevent.onPlayerDisconnect(player);
        }

    }
}
