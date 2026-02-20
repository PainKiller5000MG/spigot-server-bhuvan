package net.minecraft.tags;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;

public class TagNetworkSerialization {

    public TagNetworkSerialization() {}

    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializeTagsToNetwork(LayeredRegistryAccess<RegistryLayer> registries) {
        return (Map) RegistrySynchronization.networkSafeRegistries(registries).map((registryaccess_registryentry) -> {
            return Pair.of(registryaccess_registryentry.key(), serializeToNetwork(registryaccess_registryentry.value()));
        }).filter((pair) -> {
            return !((TagNetworkSerialization.NetworkPayload) pair.getSecond()).isEmpty();
        }).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private static <T> TagNetworkSerialization.NetworkPayload serializeToNetwork(Registry<T> registry) {
        Map<Identifier, IntList> map = new HashMap();

        registry.getTags().forEach((holderset_named) -> {
            IntList intlist = new IntArrayList(holderset_named.size());

            for (Holder<T> holder : holderset_named) {
                if (holder.kind() != Holder.Kind.REFERENCE) {
                    throw new IllegalStateException("Can't serialize unregistered value " + String.valueOf(holder));
                }

                intlist.add(registry.getId(holder.value()));
            }

            map.put(holderset_named.key().location(), intlist);
        });
        return new TagNetworkSerialization.NetworkPayload(map);
    }

    private static <T> TagLoader.LoadResult<T> deserializeTagsFromNetwork(Registry<T> registry, TagNetworkSerialization.NetworkPayload payload) {
        ResourceKey<? extends Registry<T>> resourcekey = registry.key();
        Map<TagKey<T>, List<Holder<T>>> map = new HashMap();

        payload.tags.forEach((identifier, intlist) -> {
            TagKey<T> tagkey = TagKey.<T>create(resourcekey, identifier);
            IntStream intstream = intlist.intStream();

            Objects.requireNonNull(registry);
            List<Holder<T>> list = (List) intstream.mapToObj(registry::get).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());

            map.put(tagkey, list);
        });
        return new TagLoader.LoadResult<T>(resourcekey, map);
    }

    public static final class NetworkPayload {

        public static final TagNetworkSerialization.NetworkPayload EMPTY = new TagNetworkSerialization.NetworkPayload(Map.of());
        private final Map<Identifier, IntList> tags;

        NetworkPayload(Map<Identifier, IntList> tags) {
            this.tags = tags;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeMap(this.tags, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeIntIdList);
        }

        public static TagNetworkSerialization.NetworkPayload read(FriendlyByteBuf buf) {
            return new TagNetworkSerialization.NetworkPayload(buf.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readIntIdList));
        }

        public boolean isEmpty() {
            return this.tags.isEmpty();
        }

        public int size() {
            return this.tags.size();
        }

        public <T> TagLoader.LoadResult<T> resolve(Registry<T> registry) {
            return TagNetworkSerialization.<T>deserializeTagsFromNetwork(registry, this);
        }
    }
}
