package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class FunctionReference extends LootItemConditionalFunction {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<FunctionReference> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(ResourceKey.codec(Registries.ITEM_MODIFIER).fieldOf("name").forGetter((functionreference) -> {
            return functionreference.name;
        })).apply(instance, FunctionReference::new);
    });
    private final ResourceKey<LootItemFunction> name;

    private FunctionReference(List<LootItemCondition> predicates, ResourceKey<LootItemFunction> name) {
        super(predicates);
        this.name = name;
    }

    @Override
    public LootItemFunctionType<FunctionReference> getType() {
        return LootItemFunctions.REFERENCE;
    }

    @Override
    public void validate(ValidationContext context) {
        if (!context.allowsReferences()) {
            context.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(this.name));
        } else if (context.hasVisitedElement(this.name)) {
            context.reportProblem(new ValidationContext.RecursiveReferenceProblem(this.name));
        } else {
            super.validate(context);
            context.resolver().get(this.name).ifPresentOrElse((holder_reference) -> {
                ((LootItemFunction) holder_reference.value()).validate(context.enterElement(new ProblemReporter.ElementReferencePathElement(this.name), this.name));
            }, () -> {
                context.reportProblem(new ValidationContext.MissingReferenceProblem(this.name));
            });
        }
    }

    @Override
    protected ItemStack run(ItemStack itemStack, LootContext context) {
        LootItemFunction lootitemfunction = (LootItemFunction) context.getResolver().get(this.name).map(Holder::value).orElse((Object) null);

        if (lootitemfunction == null) {
            FunctionReference.LOGGER.warn("Unknown function: {}", this.name.identifier());
            return itemStack;
        } else {
            LootContext.VisitedEntry<?> lootcontext_visitedentry = LootContext.createVisitedEntry(lootitemfunction);

            if (context.pushVisitedElement(lootcontext_visitedentry)) {
                ItemStack itemstack1;

                try {
                    itemstack1 = (ItemStack) lootitemfunction.apply(itemStack, context);
                } finally {
                    context.popVisitedElement(lootcontext_visitedentry);
                }

                return itemstack1;
            } else {
                FunctionReference.LOGGER.warn("Detected infinite loop in loot tables");
                return itemStack;
            }
        }
    }

    public static LootItemConditionalFunction.Builder<?> functionReference(ResourceKey<LootItemFunction> name) {
        return simpleBuilder((list) -> {
            return new FunctionReference(list, name);
        });
    }
}
