package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;

public class SetLoreFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetLoreFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(ComponentSerialization.CODEC.sizeLimitedListOf(256).fieldOf("lore").forGetter((setlorefunction) -> {
            return setlorefunction.lore;
        }), ListOperation.codec(256).forGetter((setlorefunction) -> {
            return setlorefunction.mode;
        }), LootContext.EntityTarget.CODEC.optionalFieldOf("entity").forGetter((setlorefunction) -> {
            return setlorefunction.resolutionContext;
        }))).apply(instance, SetLoreFunction::new);
    });
    private final List<Component> lore;
    private final ListOperation mode;
    private final Optional<LootContext.EntityTarget> resolutionContext;

    public SetLoreFunction(List<LootItemCondition> predicates, List<Component> lore, ListOperation mode, Optional<LootContext.EntityTarget> resolutionContext) {
        super(predicates);
        this.lore = List.copyOf(lore);
        this.mode = mode;
        this.resolutionContext = resolutionContext;
    }

    @Override
    public LootItemFunctionType<SetLoreFunction> getType() {
        return LootItemFunctions.SET_LORE;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return (Set) this.resolutionContext.map((lootcontext_entitytarget) -> {
            return Set.of(lootcontext_entitytarget.contextParam());
        }).orElseGet(Set::of);
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.update(DataComponents.LORE, ItemLore.EMPTY, (itemlore) -> {
            return new ItemLore(this.updateLore(itemlore, context));
        });
        return itemStack;
    }

    private List<Component> updateLore(@Nullable ItemLore itemLore, LootContext context) {
        if (itemLore == null && this.lore.isEmpty()) {
            return List.of();
        } else {
            UnaryOperator<Component> unaryoperator = SetNameFunction.createResolver(context, (LootContext.EntityTarget) this.resolutionContext.orElse((Object) null));
            List<Component> list = this.lore.stream().map(unaryoperator).toList();

            return this.mode.<Component>apply(itemLore.lines(), list, 256);
        }
    }

    public static SetLoreFunction.Builder setLore() {
        return new SetLoreFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetLoreFunction.Builder> {

        private Optional<LootContext.EntityTarget> resolutionContext = Optional.empty();
        private final ImmutableList.Builder<Component> lore = ImmutableList.builder();
        private ListOperation mode;

        public Builder() {
            this.mode = ListOperation.Append.INSTANCE;
        }

        public SetLoreFunction.Builder setMode(ListOperation mode) {
            this.mode = mode;
            return this;
        }

        public SetLoreFunction.Builder setResolutionContext(LootContext.EntityTarget resolutionContext) {
            this.resolutionContext = Optional.of(resolutionContext);
            return this;
        }

        public SetLoreFunction.Builder addLine(Component line) {
            this.lore.add(line);
            return this;
        }

        @Override
        protected SetLoreFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetLoreFunction(this.getConditions(), this.lore.build(), this.mode, this.resolutionContext);
        }
    }
}
