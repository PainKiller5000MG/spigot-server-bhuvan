package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import org.slf4j.Logger;

public record ConditionReference(ResourceKey<LootItemCondition> name) implements LootItemCondition {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<ConditionReference> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ResourceKey.codec(Registries.PREDICATE).fieldOf("name").forGetter(ConditionReference::name)).apply(instance, ConditionReference::new);
    });

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.REFERENCE;
    }

    @Override
    public void validate(ValidationContext context) {
        if (!context.allowsReferences()) {
            context.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(this.name));
        } else if (context.hasVisitedElement(this.name)) {
            context.reportProblem(new ValidationContext.RecursiveReferenceProblem(this.name));
        } else {
            LootItemCondition.super.validate(context);
            context.resolver().get(this.name).ifPresentOrElse((holder_reference) -> {
                ((LootItemCondition) holder_reference.value()).validate(context.enterElement(new ProblemReporter.ElementReferencePathElement(this.name), this.name));
            }, () -> {
                context.reportProblem(new ValidationContext.MissingReferenceProblem(this.name));
            });
        }
    }

    public boolean test(LootContext lootContext) {
        LootItemCondition lootitemcondition = (LootItemCondition) lootContext.getResolver().get(this.name).map(Holder.Reference::value).orElse((Object) null);

        if (lootitemcondition == null) {
            ConditionReference.LOGGER.warn("Tried using unknown condition table called {}", this.name.identifier());
            return false;
        } else {
            LootContext.VisitedEntry<?> lootcontext_visitedentry = LootContext.createVisitedEntry(lootitemcondition);

            if (lootContext.pushVisitedElement(lootcontext_visitedentry)) {
                boolean flag;

                try {
                    flag = lootitemcondition.test(lootContext);
                } finally {
                    lootContext.popVisitedElement(lootcontext_visitedentry);
                }

                return flag;
            } else {
                ConditionReference.LOGGER.warn("Detected infinite loop in loot tables");
                return false;
            }
        }
    }

    public static LootItemCondition.Builder conditionReference(ResourceKey<LootItemCondition> name) {
        return () -> {
            return new ConditionReference(name);
        };
    }
}
