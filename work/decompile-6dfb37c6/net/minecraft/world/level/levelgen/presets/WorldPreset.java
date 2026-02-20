package net.minecraft.world.level.levelgen.presets;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

public class WorldPreset {

    public static final Codec<WorldPreset> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.unboundedMap(ResourceKey.codec(Registries.LEVEL_STEM), LevelStem.CODEC).fieldOf("dimensions").forGetter((worldpreset) -> {
            return worldpreset.dimensions;
        })).apply(instance, WorldPreset::new);
    }).validate(WorldPreset::requireOverworld);
    public static final Codec<Holder<WorldPreset>> CODEC = RegistryFileCodec.<Holder<WorldPreset>>create(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC);
    private final Map<ResourceKey<LevelStem>, LevelStem> dimensions;

    public WorldPreset(Map<ResourceKey<LevelStem>, LevelStem> dimensions) {
        this.dimensions = dimensions;
    }

    private ImmutableMap<ResourceKey<LevelStem>, LevelStem> dimensionsInOrder() {
        ImmutableMap.Builder<ResourceKey<LevelStem>, LevelStem> immutablemap_builder = ImmutableMap.builder();

        WorldDimensions.keysInOrder(this.dimensions.keySet().stream()).forEach((resourcekey) -> {
            LevelStem levelstem = (LevelStem) this.dimensions.get(resourcekey);

            if (levelstem != null) {
                immutablemap_builder.put(resourcekey, levelstem);
            }

        });
        return immutablemap_builder.build();
    }

    public WorldDimensions createWorldDimensions() {
        return new WorldDimensions(this.dimensionsInOrder());
    }

    public Optional<LevelStem> overworld() {
        return Optional.ofNullable((LevelStem) this.dimensions.get(LevelStem.OVERWORLD));
    }

    private static DataResult<WorldPreset> requireOverworld(WorldPreset preset) {
        return preset.overworld().isEmpty() ? DataResult.error(() -> {
            return "Missing overworld dimension";
        }) : DataResult.success(preset, Lifecycle.stable());
    }
}
