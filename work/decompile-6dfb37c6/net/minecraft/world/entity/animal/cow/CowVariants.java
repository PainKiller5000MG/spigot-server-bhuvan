package net.minecraft.world.entity.animal.cow;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.TemperatureVariants;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;

public class CowVariants {

    public static final ResourceKey<CowVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    public static final ResourceKey<CowVariant> WARM = createKey(TemperatureVariants.WARM);
    public static final ResourceKey<CowVariant> COLD = createKey(TemperatureVariants.COLD);
    public static final ResourceKey<CowVariant> DEFAULT = CowVariants.TEMPERATE;

    public CowVariants() {}

    private static ResourceKey<CowVariant> createKey(Identifier id) {
        return ResourceKey.create(Registries.COW_VARIANT, id);
    }

    public static void bootstrap(BootstrapContext<CowVariant> context) {
        register(context, CowVariants.TEMPERATE, CowVariant.ModelType.NORMAL, "temperate_cow", SpawnPrioritySelectors.fallback(0));
        register(context, CowVariants.WARM, CowVariant.ModelType.WARM, "warm_cow", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
        register(context, CowVariants.COLD, CowVariant.ModelType.COLD, "cold_cow", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
    }

    private static void register(BootstrapContext<CowVariant> context, ResourceKey<CowVariant> name, CowVariant.ModelType modelType, String textureName, TagKey<Biome> spawnBiome) {
        HolderSet<Biome> holderset = context.lookup(Registries.BIOME).getOrThrow(spawnBiome);

        register(context, name, modelType, textureName, SpawnPrioritySelectors.single(new BiomeCheck(holderset), 1));
    }

    private static void register(BootstrapContext<CowVariant> context, ResourceKey<CowVariant> name, CowVariant.ModelType modelType, String textureName, SpawnPrioritySelectors selectors) {
        Identifier identifier = Identifier.withDefaultNamespace("entity/cow/" + textureName);

        context.register(name, new CowVariant(new ModelAndTexture(modelType, identifier), selectors));
    }
}
