package net.minecraft.world.entity.npc.villager;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public final class VillagerType {

    public static final ResourceKey<VillagerType> DESERT = createKey("desert");
    public static final ResourceKey<VillagerType> JUNGLE = createKey("jungle");
    public static final ResourceKey<VillagerType> PLAINS = createKey("plains");
    public static final ResourceKey<VillagerType> SAVANNA = createKey("savanna");
    public static final ResourceKey<VillagerType> SNOW = createKey("snow");
    public static final ResourceKey<VillagerType> SWAMP = createKey("swamp");
    public static final ResourceKey<VillagerType> TAIGA = createKey("taiga");
    public static final Codec<Holder<VillagerType>> CODEC = RegistryFixedCodec.<Holder<VillagerType>>create(Registries.VILLAGER_TYPE);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<VillagerType>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.VILLAGER_TYPE);
    private static final Map<ResourceKey<Biome>, ResourceKey<VillagerType>> BY_BIOME = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put(Biomes.BADLANDS, VillagerType.DESERT);
        hashmap.put(Biomes.DESERT, VillagerType.DESERT);
        hashmap.put(Biomes.ERODED_BADLANDS, VillagerType.DESERT);
        hashmap.put(Biomes.WOODED_BADLANDS, VillagerType.DESERT);
        hashmap.put(Biomes.BAMBOO_JUNGLE, VillagerType.JUNGLE);
        hashmap.put(Biomes.JUNGLE, VillagerType.JUNGLE);
        hashmap.put(Biomes.SPARSE_JUNGLE, VillagerType.JUNGLE);
        hashmap.put(Biomes.SAVANNA_PLATEAU, VillagerType.SAVANNA);
        hashmap.put(Biomes.SAVANNA, VillagerType.SAVANNA);
        hashmap.put(Biomes.WINDSWEPT_SAVANNA, VillagerType.SAVANNA);
        hashmap.put(Biomes.DEEP_FROZEN_OCEAN, VillagerType.SNOW);
        hashmap.put(Biomes.FROZEN_OCEAN, VillagerType.SNOW);
        hashmap.put(Biomes.FROZEN_RIVER, VillagerType.SNOW);
        hashmap.put(Biomes.ICE_SPIKES, VillagerType.SNOW);
        hashmap.put(Biomes.SNOWY_BEACH, VillagerType.SNOW);
        hashmap.put(Biomes.SNOWY_TAIGA, VillagerType.SNOW);
        hashmap.put(Biomes.SNOWY_PLAINS, VillagerType.SNOW);
        hashmap.put(Biomes.GROVE, VillagerType.SNOW);
        hashmap.put(Biomes.SNOWY_SLOPES, VillagerType.SNOW);
        hashmap.put(Biomes.FROZEN_PEAKS, VillagerType.SNOW);
        hashmap.put(Biomes.JAGGED_PEAKS, VillagerType.SNOW);
        hashmap.put(Biomes.SWAMP, VillagerType.SWAMP);
        hashmap.put(Biomes.MANGROVE_SWAMP, VillagerType.SWAMP);
        hashmap.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, VillagerType.TAIGA);
        hashmap.put(Biomes.OLD_GROWTH_PINE_TAIGA, VillagerType.TAIGA);
        hashmap.put(Biomes.WINDSWEPT_GRAVELLY_HILLS, VillagerType.TAIGA);
        hashmap.put(Biomes.WINDSWEPT_HILLS, VillagerType.TAIGA);
        hashmap.put(Biomes.TAIGA, VillagerType.TAIGA);
        hashmap.put(Biomes.WINDSWEPT_FOREST, VillagerType.TAIGA);
    });

    public VillagerType() {}

    private static ResourceKey<VillagerType> createKey(String name) {
        return ResourceKey.create(Registries.VILLAGER_TYPE, Identifier.withDefaultNamespace(name));
    }

    private static VillagerType register(Registry<VillagerType> registry, ResourceKey<VillagerType> name) {
        return (VillagerType) Registry.register(registry, name, new VillagerType());
    }

    public static VillagerType bootstrap(Registry<VillagerType> registry) {
        register(registry, VillagerType.DESERT);
        register(registry, VillagerType.JUNGLE);
        register(registry, VillagerType.PLAINS);
        register(registry, VillagerType.SAVANNA);
        register(registry, VillagerType.SNOW);
        register(registry, VillagerType.SWAMP);
        return register(registry, VillagerType.TAIGA);
    }

    public static ResourceKey<VillagerType> byBiome(Holder<Biome> biome) {
        Optional optional = biome.unwrapKey();
        Map map = VillagerType.BY_BIOME;

        Objects.requireNonNull(map);
        return (ResourceKey) optional.map(map::get).orElse(VillagerType.PLAINS);
    }
}
