package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record ConditionalEffect<T>(T effect, Optional<LootItemCondition> requirements) {

    public static Codec<LootItemCondition> conditionCodec(ContextKeySet paramsSet) {
        return LootItemCondition.DIRECT_CODEC.validate((lootitemcondition) -> {
            ProblemReporter.Collector problemreporter_collector = new ProblemReporter.Collector();
            ValidationContext validationcontext = new ValidationContext(problemreporter_collector, paramsSet);

            lootitemcondition.validate(validationcontext);
            return !problemreporter_collector.isEmpty() ? DataResult.error(() -> {
                return "Validation error in enchantment effect condition: " + problemreporter_collector.getReport();
            }) : DataResult.success(lootitemcondition);
        });
    }

    public static <T> Codec<ConditionalEffect<T>> codec(Codec<T> effectCodec, ContextKeySet paramsSet) {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(effectCodec.fieldOf("effect").forGetter(ConditionalEffect::effect), conditionCodec(paramsSet).optionalFieldOf("requirements").forGetter(ConditionalEffect::requirements)).apply(instance, ConditionalEffect::new);
        });
    }

    public boolean matches(LootContext context) {
        return this.requirements.isEmpty() ? true : ((LootItemCondition) this.requirements.get()).test(context);
    }
}
