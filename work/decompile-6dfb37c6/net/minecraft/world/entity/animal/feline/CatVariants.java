package net.minecraft.world.entity.animal.feline;

import java.util.List;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.variant.MoonBrightnessCheck;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface CatVariants {

    ResourceKey<CatVariant> TABBY = createKey("tabby");
    ResourceKey<CatVariant> BLACK = createKey("black");
    ResourceKey<CatVariant> RED = createKey("red");
    ResourceKey<CatVariant> SIAMESE = createKey("siamese");
    ResourceKey<CatVariant> BRITISH_SHORTHAIR = createKey("british_shorthair");
    ResourceKey<CatVariant> CALICO = createKey("calico");
    ResourceKey<CatVariant> PERSIAN = createKey("persian");
    ResourceKey<CatVariant> RAGDOLL = createKey("ragdoll");
    ResourceKey<CatVariant> WHITE = createKey("white");
    ResourceKey<CatVariant> JELLIE = createKey("jellie");
    ResourceKey<CatVariant> ALL_BLACK = createKey("all_black");

    private static ResourceKey<CatVariant> createKey(String name) {
        return ResourceKey.create(Registries.CAT_VARIANT, Identifier.withDefaultNamespace(name));
    }

    static void bootstrap(BootstrapContext<CatVariant> context) {
        HolderGetter<Structure> holdergetter = context.<Structure>lookup(Registries.STRUCTURE);

        registerForAnyConditions(context, CatVariants.TABBY, "entity/cat/tabby");
        registerForAnyConditions(context, CatVariants.BLACK, "entity/cat/black");
        registerForAnyConditions(context, CatVariants.RED, "entity/cat/red");
        registerForAnyConditions(context, CatVariants.SIAMESE, "entity/cat/siamese");
        registerForAnyConditions(context, CatVariants.BRITISH_SHORTHAIR, "entity/cat/british_shorthair");
        registerForAnyConditions(context, CatVariants.CALICO, "entity/cat/calico");
        registerForAnyConditions(context, CatVariants.PERSIAN, "entity/cat/persian");
        registerForAnyConditions(context, CatVariants.RAGDOLL, "entity/cat/ragdoll");
        registerForAnyConditions(context, CatVariants.WHITE, "entity/cat/white");
        registerForAnyConditions(context, CatVariants.JELLIE, "entity/cat/jellie");
        register(context, CatVariants.ALL_BLACK, "entity/cat/all_black", new SpawnPrioritySelectors(List.of(new PriorityProvider.Selector(new StructureCheck(holdergetter.getOrThrow(StructureTags.CATS_SPAWN_AS_BLACK)), 1), new PriorityProvider.Selector(new MoonBrightnessCheck(MinMaxBounds.Doubles.atLeast(0.9D)), 0))));
    }

    private static void registerForAnyConditions(BootstrapContext<CatVariant> context, ResourceKey<CatVariant> name, String texture) {
        register(context, name, texture, SpawnPrioritySelectors.fallback(0));
    }

    private static void register(BootstrapContext<CatVariant> context, ResourceKey<CatVariant> name, String texture, SpawnPrioritySelectors spawnConditions) {
        context.register(name, new CatVariant(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(texture)), spawnConditions));
    }
}
