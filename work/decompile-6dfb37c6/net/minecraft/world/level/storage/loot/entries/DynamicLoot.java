package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class DynamicLoot extends LootPoolSingletonContainer {

    public static final MapCodec<DynamicLoot> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Identifier.CODEC.fieldOf("name").forGetter((dynamicloot) -> {
            return dynamicloot.name;
        })).and(singletonFields(instance)).apply(instance, DynamicLoot::new);
    });
    private final Identifier name;

    private DynamicLoot(Identifier name, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.name = name;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.DYNAMIC;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> output, LootContext context) {
        context.addDynamicDrops(this.name, output);
    }

    public static LootPoolSingletonContainer.Builder<?> dynamicEntry(Identifier name) {
        return simpleBuilder((i, j, list, list1) -> {
            return new DynamicLoot(name, i, j, list, list1);
        });
    }
}
