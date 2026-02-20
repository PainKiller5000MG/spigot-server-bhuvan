package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

@FunctionalInterface
public interface PoolAliasLookup {

    PoolAliasLookup EMPTY = (resourcekey) -> {
        return resourcekey;
    };

    ResourceKey<StructureTemplatePool> lookup(ResourceKey<StructureTemplatePool> alias);

    static PoolAliasLookup create(List<PoolAliasBinding> poolAliasBindings, BlockPos pos, long seed) {
        if (poolAliasBindings.isEmpty()) {
            return PoolAliasLookup.EMPTY;
        } else {
            RandomSource randomsource = RandomSource.create(seed).forkPositional().at(pos);
            ImmutableMap.Builder<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> immutablemap_builder = ImmutableMap.builder();

            poolAliasBindings.forEach((poolaliasbinding) -> {
                Objects.requireNonNull(immutablemap_builder);
                poolaliasbinding.forEachResolved(randomsource, immutablemap_builder::put);
            });
            Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = immutablemap_builder.build();

            return (resourcekey) -> {
                return (ResourceKey) Objects.requireNonNull((ResourceKey) map.getOrDefault(resourcekey, resourcekey), () -> {
                    return "alias " + String.valueOf(resourcekey.identifier()) + " was mapped to null value";
                });
            };
        }
    }
}
