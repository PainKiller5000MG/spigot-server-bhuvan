package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.slot.SlotSource;
import net.minecraft.world.item.slot.SlotSources;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SlotLoot extends LootPoolSingletonContainer {

    public static final MapCodec<SlotLoot> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(SlotSources.CODEC.fieldOf("slot_source").forGetter((slotloot) -> {
            return slotloot.slotSource;
        })).and(singletonFields(instance)).apply(instance, SlotLoot::new);
    });
    private final SlotSource slotSource;

    private SlotLoot(SlotSource slotSource, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.slotSource = slotSource;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.SLOTS;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> output, LootContext context) {
        this.slotSource.provide(context).itemCopies().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).forEach(output);
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        this.slotSource.validate(context.forChild(new ProblemReporter.FieldPathElement("slot_source")));
    }
}
