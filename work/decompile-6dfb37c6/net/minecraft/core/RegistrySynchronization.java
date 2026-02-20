package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.KnownPack;

public class RegistrySynchronization {

    private static final Set<ResourceKey<? extends Registry<?>>> NETWORKABLE_REGISTRIES = (Set) RegistryDataLoader.SYNCHRONIZED_REGISTRIES.stream().map(RegistryDataLoader.RegistryData::key).collect(Collectors.toUnmodifiableSet());

    public RegistrySynchronization() {}

    public static void packRegistries(DynamicOps<Tag> ops, RegistryAccess registries, Set<KnownPack> clientKnownPacks, BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> output) {
        RegistryDataLoader.SYNCHRONIZED_REGISTRIES.forEach((registrydataloader_registrydata) -> {
            packRegistry(ops, registrydataloader_registrydata, registries, clientKnownPacks, output);
        });
    }

    private static <T> void packRegistry(DynamicOps<Tag> ops, RegistryDataLoader.RegistryData<T> registryData, RegistryAccess registries, Set<KnownPack> clientKnownPacks, BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> output) {
        registries.lookup(registryData.key()).ifPresent((registry) -> {
            List<RegistrySynchronization.PackedRegistryEntry> list = new ArrayList(registry.size());

            registry.listElements().forEach((holder_reference) -> {
                Optional optional = registry.registrationInfo(holder_reference.key()).flatMap(RegistrationInfo::knownPackInfo);

                Objects.requireNonNull(clientKnownPacks);
                boolean flag = optional.filter(clientKnownPacks::contains).isPresent();
                Optional<Tag> optional1;

                if (flag) {
                    optional1 = Optional.empty();
                } else {
                    Tag tag = (Tag) registryData.elementCodec().encodeStart(ops, holder_reference.value()).getOrThrow((s) -> {
                        String s1 = String.valueOf(holder_reference.key());

                        return new IllegalArgumentException("Failed to serialize " + s1 + ": " + s);
                    });

                    optional1 = Optional.of(tag);
                }

                list.add(new RegistrySynchronization.PackedRegistryEntry(holder_reference.key().identifier(), optional1));
            });
            output.accept(registry.key(), list);
        });
    }

    private static Stream<RegistryAccess.RegistryEntry<?>> ownedNetworkableRegistries(RegistryAccess access) {
        return access.registries().filter((registryaccess_registryentry) -> {
            return isNetworkable(registryaccess_registryentry.key());
        });
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkedRegistries(LayeredRegistryAccess<RegistryLayer> registries) {
        return ownedNetworkableRegistries(registries.getAccessFrom(RegistryLayer.WORLDGEN));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkSafeRegistries(LayeredRegistryAccess<RegistryLayer> registries) {
        Stream<RegistryAccess.RegistryEntry<?>> stream = registries.getLayer(RegistryLayer.STATIC).registries();
        Stream<RegistryAccess.RegistryEntry<?>> stream1 = networkedRegistries(registries);

        return Stream.concat(stream1, stream);
    }

    public static boolean isNetworkable(ResourceKey<? extends Registry<?>> key) {
        return RegistrySynchronization.NETWORKABLE_REGISTRIES.contains(key);
    }

    public static record PackedRegistryEntry(Identifier id, Optional<Tag> data) {

        public static final StreamCodec<ByteBuf, RegistrySynchronization.PackedRegistryEntry> STREAM_CODEC = StreamCodec.composite(Identifier.STREAM_CODEC, RegistrySynchronization.PackedRegistryEntry::id, ByteBufCodecs.TAG.apply(ByteBufCodecs::optional), RegistrySynchronization.PackedRegistryEntry::data, RegistrySynchronization.PackedRegistryEntry::new);
    }
}
