package net.minecraft.world.level.biome;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class MultiNoiseBiomeSourceParameterLists {

    public static final ResourceKey<MultiNoiseBiomeSourceParameterList> NETHER = register("nether");
    public static final ResourceKey<MultiNoiseBiomeSourceParameterList> OVERWORLD = register("overworld");

    public MultiNoiseBiomeSourceParameterLists() {}

    public static void bootstrap(BootstrapContext<MultiNoiseBiomeSourceParameterList> context) {
        HolderGetter<Biome> holdergetter = context.<Biome>lookup(Registries.BIOME);

        context.register(MultiNoiseBiomeSourceParameterLists.NETHER, new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.NETHER, holdergetter));
        context.register(MultiNoiseBiomeSourceParameterLists.OVERWORLD, new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, holdergetter));
    }

    private static ResourceKey<MultiNoiseBiomeSourceParameterList> register(String name) {
        return ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, Identifier.withDefaultNamespace(name));
    }
}
