package net.minecraft.world;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface RandomizableContainer extends Container {

    String LOOT_TABLE_TAG = "LootTable";
    String LOOT_TABLE_SEED_TAG = "LootTableSeed";

    @Nullable
    ResourceKey<LootTable> getLootTable();

    void setLootTable(@Nullable ResourceKey<LootTable> lootTable);

    default void setLootTable(ResourceKey<LootTable> lootTable, long seed) {
        this.setLootTable(lootTable);
        this.setLootTableSeed(seed);
    }

    long getLootTableSeed();

    void setLootTableSeed(long lootTableSeed);

    BlockPos getBlockPos();

    @Nullable
    Level getLevel();

    static void setBlockEntityLootTable(BlockGetter level, RandomSource random, BlockPos blockEntityPos, ResourceKey<LootTable> lootTable) {
        BlockEntity blockentity = level.getBlockEntity(blockEntityPos);

        if (blockentity instanceof RandomizableContainer randomizablecontainer) {
            randomizablecontainer.setLootTable(lootTable, random.nextLong());
        }

    }

    default boolean tryLoadLootTable(ValueInput base) {
        ResourceKey<LootTable> resourcekey = (ResourceKey) base.read("LootTable", LootTable.KEY_CODEC).orElse((Object) null);

        this.setLootTable(resourcekey);
        this.setLootTableSeed(base.getLongOr("LootTableSeed", 0L));
        return resourcekey != null;
    }

    default boolean trySaveLootTable(ValueOutput base) {
        ResourceKey<LootTable> resourcekey = this.getLootTable();

        if (resourcekey == null) {
            return false;
        } else {
            base.store("LootTable", LootTable.KEY_CODEC, resourcekey);
            long i = this.getLootTableSeed();

            if (i != 0L) {
                base.putLong("LootTableSeed", i);
            }

            return true;
        }
    }

    default void unpackLootTable(@Nullable Player player) {
        Level level = this.getLevel();
        BlockPos blockpos = this.getBlockPos();
        ResourceKey<LootTable> resourcekey = this.getLootTable();

        if (resourcekey != null && level != null && level.getServer() != null) {
            LootTable loottable = level.getServer().reloadableRegistries().getLootTable(resourcekey);

            if (player instanceof ServerPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer) player, resourcekey);
            }

            this.setLootTable((ResourceKey) null);
            LootParams.Builder lootparams_builder = (new LootParams.Builder((ServerLevel) level)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockpos));

            if (player != null) {
                lootparams_builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
            }

            loottable.fill(this, lootparams_builder.create(LootContextParamSets.CHEST), this.getLootTableSeed());
        }

    }
}
