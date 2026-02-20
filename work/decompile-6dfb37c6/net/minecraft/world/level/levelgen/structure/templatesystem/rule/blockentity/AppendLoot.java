package net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public class AppendLoot implements RuleBlockEntityModifier {

    public static final MapCodec<AppendLoot> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LootTable.KEY_CODEC.fieldOf("loot_table").forGetter((appendloot) -> {
            return appendloot.lootTable;
        })).apply(instance, AppendLoot::new);
    });
    private final ResourceKey<LootTable> lootTable;

    public AppendLoot(ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public CompoundTag apply(RandomSource random, @Nullable CompoundTag existingTag) {
        CompoundTag compoundtag1 = existingTag == null ? new CompoundTag() : existingTag.copy();

        compoundtag1.store("LootTable", LootTable.KEY_CODEC, this.lootTable);
        compoundtag1.putLong("LootTableSeed", random.nextLong());
        return compoundtag1;
    }

    @Override
    public RuleBlockEntityModifierType<?> getType() {
        return RuleBlockEntityModifierType.APPEND_LOOT;
    }
}
