package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyBlockState extends LootItemConditionalFunction {

    public static final MapCodec<CopyBlockState> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(BuiltInRegistries.BLOCK.holderByNameCodec().fieldOf("block").forGetter((copyblockstate) -> {
            return copyblockstate.block;
        }), Codec.STRING.listOf().fieldOf("properties").forGetter((copyblockstate) -> {
            return copyblockstate.properties.stream().map(Property::getName).toList();
        }))).apply(instance, CopyBlockState::new);
    });
    private final Holder<Block> block;
    private final Set<Property<?>> properties;

    private CopyBlockState(List<LootItemCondition> predicates, Holder<Block> block, Set<Property<?>> properties) {
        super(predicates);
        this.block = block;
        this.properties = properties;
    }

    private CopyBlockState(List<LootItemCondition> predicates, Holder<Block> block, List<String> propertyNames) {
        Stream stream = propertyNames.stream();
        StateDefinition statedefinition = (block.value()).getStateDefinition();

        Objects.requireNonNull(statedefinition);
        this(predicates, block, (Set) stream.map(statedefinition::getProperty).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    @Override
    public LootItemFunctionType<CopyBlockState> getType() {
        return LootItemFunctions.COPY_STATE;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_STATE);
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        BlockState blockstate = (BlockState) context.getOptionalParameter(LootContextParams.BLOCK_STATE);

        if (blockstate != null) {
            itemStack.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, (blockitemstateproperties) -> {
                for (Property<?> property : this.properties) {
                    if (blockstate.hasProperty(property)) {
                        blockitemstateproperties = blockitemstateproperties.with(property, blockstate);
                    }
                }

                return blockitemstateproperties;
            });
        }

        return itemStack;
    }

    public static CopyBlockState.Builder copyState(Block block) {
        return new CopyBlockState.Builder(block);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyBlockState.Builder> {

        private final Holder<Block> block;
        private final ImmutableSet.Builder<Property<?>> properties = ImmutableSet.builder();

        private Builder(Block block) {
            this.block = block.builtInRegistryHolder();
        }

        public CopyBlockState.Builder copy(Property<?> property) {
            if (!((Block) this.block.value()).getStateDefinition().getProperties().contains(property)) {
                String s = String.valueOf(property);

                throw new IllegalStateException("Property " + s + " is not present on block " + String.valueOf(this.block));
            } else {
                this.properties.add(property);
                return this;
            }
        }

        @Override
        protected CopyBlockState.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyBlockState(this.getConditions(), this.block, this.properties.build());
        }
    }
}
