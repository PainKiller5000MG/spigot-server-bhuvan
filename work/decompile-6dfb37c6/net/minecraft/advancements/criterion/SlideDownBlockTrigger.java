package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SlideDownBlockTrigger extends SimpleCriterionTrigger<SlideDownBlockTrigger.TriggerInstance> {

    public SlideDownBlockTrigger() {}

    @Override
    public Codec<SlideDownBlockTrigger.TriggerInstance> codec() {
        return SlideDownBlockTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockState state) {
        this.trigger(player, (slidedownblocktrigger_triggerinstance) -> {
            return slidedownblocktrigger_triggerinstance.matches(state);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Holder<Block>> block, Optional<StatePropertiesPredicate> state) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<SlideDownBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SlideDownBlockTrigger.TriggerInstance::player), BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("block").forGetter(SlideDownBlockTrigger.TriggerInstance::block), StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(SlideDownBlockTrigger.TriggerInstance::state)).apply(instance, SlideDownBlockTrigger.TriggerInstance::new);
        }).validate(SlideDownBlockTrigger.TriggerInstance::validate);

        private static DataResult<SlideDownBlockTrigger.TriggerInstance> validate(SlideDownBlockTrigger.TriggerInstance trigger) {
            return (DataResult) trigger.block.flatMap((holder) -> {
                return trigger.state.flatMap((statepropertiespredicate) -> {
                    return statepropertiespredicate.checkState(((Block) holder.value()).getStateDefinition());
                }).map((s) -> {
                    return DataResult.error(() -> {
                        String s1 = String.valueOf(holder);

                        return "Block" + s1 + " has no property " + s;
                    });
                });
            }).orElseGet(() -> {
                return DataResult.success(trigger);
            });
        }

        public static Criterion<SlideDownBlockTrigger.TriggerInstance> slidesDownBlock(Block block) {
            return CriteriaTriggers.HONEY_BLOCK_SLIDE.createCriterion(new SlideDownBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
        }

        public boolean matches(BlockState state) {
            return this.block.isPresent() && !state.is((Holder) this.block.get()) ? false : !this.state.isPresent() || ((StatePropertiesPredicate) this.state.get()).matches(state);
        }
    }
}
