package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record LootItemBlockStatePropertyCondition(Holder<Block> block, Optional<StatePropertiesPredicate> properties) implements LootItemCondition {

    public static final MapCodec<LootItemBlockStatePropertyCondition> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.holderByNameCodec().fieldOf("block").forGetter(LootItemBlockStatePropertyCondition::block), StatePropertiesPredicate.CODEC.optionalFieldOf("properties").forGetter(LootItemBlockStatePropertyCondition::properties)).apply(instance, LootItemBlockStatePropertyCondition::new);
    }).validate(LootItemBlockStatePropertyCondition::validate);

    private static DataResult<LootItemBlockStatePropertyCondition> validate(LootItemBlockStatePropertyCondition condition) {
        return (DataResult) condition.properties().flatMap((statepropertiespredicate) -> {
            return statepropertiespredicate.checkState(((Block) condition.block().value()).getStateDefinition());
        }).map((s) -> {
            return DataResult.error(() -> {
                String s1 = String.valueOf(condition.block());

                return "Block " + s1 + " has no property" + s;
            });
        }).orElse(DataResult.success(condition));
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.BLOCK_STATE_PROPERTY;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_STATE);
    }

    public boolean test(LootContext context) {
        BlockState blockstate = (BlockState) context.getOptionalParameter(LootContextParams.BLOCK_STATE);

        return blockstate != null && blockstate.is(this.block) && (this.properties.isEmpty() || ((StatePropertiesPredicate) this.properties.get()).matches(blockstate));
    }

    public static LootItemBlockStatePropertyCondition.Builder hasBlockStateProperties(Block block) {
        return new LootItemBlockStatePropertyCondition.Builder(block);
    }

    public static class Builder implements LootItemCondition.Builder {

        private final Holder<Block> block;
        private Optional<StatePropertiesPredicate> properties = Optional.empty();

        public Builder(Block block) {
            this.block = block.builtInRegistryHolder();
        }

        public LootItemBlockStatePropertyCondition.Builder setProperties(StatePropertiesPredicate.Builder properties) {
            this.properties = properties.build();
            return this;
        }

        @Override
        public LootItemCondition build() {
            return new LootItemBlockStatePropertyCondition(this.block, this.properties);
        }
    }
}
