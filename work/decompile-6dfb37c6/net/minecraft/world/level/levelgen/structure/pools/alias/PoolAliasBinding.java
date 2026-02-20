package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public interface PoolAliasBinding {

    Codec<PoolAliasBinding> CODEC = BuiltInRegistries.POOL_ALIAS_BINDING_TYPE.byNameCodec().dispatch(PoolAliasBinding::codec, Function.identity());

    void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> aliasAndTargetConsumer);

    Stream<ResourceKey<StructureTemplatePool>> allTargets();

    static DirectPoolAlias direct(String id, String target) {
        return direct(Pools.createKey(id), Pools.createKey(target));
    }

    static DirectPoolAlias direct(ResourceKey<StructureTemplatePool> alias, ResourceKey<StructureTemplatePool> target) {
        return new DirectPoolAlias(alias, target);
    }

    static RandomPoolAlias random(String id, WeightedList<String> targets) {
        WeightedList.Builder<ResourceKey<StructureTemplatePool>> weightedlist_builder = WeightedList.<ResourceKey<StructureTemplatePool>>builder();

        targets.unwrap().forEach((weighted) -> {
            weightedlist_builder.add(Pools.createKey((String) weighted.value()), weighted.weight());
        });
        return random(Pools.createKey(id), weightedlist_builder.build());
    }

    static RandomPoolAlias random(ResourceKey<StructureTemplatePool> id, WeightedList<ResourceKey<StructureTemplatePool>> targets) {
        return new RandomPoolAlias(id, targets);
    }

    static RandomGroupPoolAlias randomGroup(WeightedList<List<PoolAliasBinding>> combinations) {
        return new RandomGroupPoolAlias(combinations);
    }

    MapCodec<? extends PoolAliasBinding> codec();
}
