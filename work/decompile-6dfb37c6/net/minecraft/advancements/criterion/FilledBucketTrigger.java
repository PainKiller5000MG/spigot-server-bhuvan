package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class FilledBucketTrigger extends SimpleCriterionTrigger<FilledBucketTrigger.TriggerInstance> {

    public FilledBucketTrigger() {}

    @Override
    public Codec<FilledBucketTrigger.TriggerInstance> codec() {
        return FilledBucketTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item) {
        this.trigger(player, (filledbuckettrigger_triggerinstance) -> {
            return filledbuckettrigger_triggerinstance.matches(item);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<FilledBucketTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FilledBucketTrigger.TriggerInstance::player), ItemPredicate.CODEC.optionalFieldOf("item").forGetter(FilledBucketTrigger.TriggerInstance::item)).apply(instance, FilledBucketTrigger.TriggerInstance::new);
        });

        public static Criterion<FilledBucketTrigger.TriggerInstance> filledBucket(ItemPredicate.Builder item) {
            return CriteriaTriggers.FILLED_BUCKET.createCriterion(new FilledBucketTrigger.TriggerInstance(Optional.empty(), Optional.of(item.build())));
        }

        public boolean matches(ItemStack item) {
            return !this.item.isPresent() || ((ItemPredicate) this.item.get()).test(item);
        }
    }
}
