package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class DefaultBlockInteractionTrigger extends SimpleCriterionTrigger<DefaultBlockInteractionTrigger.TriggerInstance> {

    public DefaultBlockInteractionTrigger() {}

    @Override
    public Codec<DefaultBlockInteractionTrigger.TriggerInstance> codec() {
        return DefaultBlockInteractionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockPos pos) {
        ServerLevel serverlevel = player.level();
        BlockState blockstate = serverlevel.getBlockState(pos);
        LootParams lootparams = (new LootParams.Builder(serverlevel)).withParameter(LootContextParams.ORIGIN, pos.getCenter()).withParameter(LootContextParams.THIS_ENTITY, player).withParameter(LootContextParams.BLOCK_STATE, blockstate).create(LootContextParamSets.BLOCK_USE);
        LootContext lootcontext = (new LootContext.Builder(lootparams)).create(Optional.empty());

        this.trigger(player, (defaultblockinteractiontrigger_triggerinstance) -> {
            return defaultblockinteractiontrigger_triggerinstance.matches(lootcontext);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> location) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<DefaultBlockInteractionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(DefaultBlockInteractionTrigger.TriggerInstance::player), ContextAwarePredicate.CODEC.optionalFieldOf("location").forGetter(DefaultBlockInteractionTrigger.TriggerInstance::location)).apply(instance, DefaultBlockInteractionTrigger.TriggerInstance::new);
        });

        public boolean matches(LootContext locationContext) {
            return this.location.isEmpty() || ((ContextAwarePredicate) this.location.get()).matches(locationContext);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            this.location.ifPresent((contextawarepredicate) -> {
                validator.validate(contextawarepredicate, LootContextParamSets.BLOCK_USE, "location");
            });
        }
    }
}
