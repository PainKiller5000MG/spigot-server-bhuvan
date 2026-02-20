package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceDisk(LevelBasedValue radius, LevelBasedValue height, Vec3i offset, Optional<BlockPredicate> predicate, BlockStateProvider blockState, Optional<Holder<GameEvent>> triggerGameEvent) implements EnchantmentEntityEffect {

    public static final MapCodec<ReplaceDisk> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LevelBasedValue.CODEC.fieldOf("radius").forGetter(ReplaceDisk::radius), LevelBasedValue.CODEC.fieldOf("height").forGetter(ReplaceDisk::height), Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(ReplaceDisk::offset), BlockPredicate.CODEC.optionalFieldOf("predicate").forGetter(ReplaceDisk::predicate), BlockStateProvider.CODEC.fieldOf("block_state").forGetter(ReplaceDisk::blockState), GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(ReplaceDisk::triggerGameEvent)).apply(instance, ReplaceDisk::new);
    });

    @Override
    public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        BlockPos blockpos = BlockPos.containing(position).offset(this.offset);
        RandomSource randomsource = entity.getRandom();
        int j = (int) this.radius.calculate(enchantmentLevel);
        int k = (int) this.height.calculate(enchantmentLevel);

        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-j, 0, -j), blockpos.offset(j, Math.min(k - 1, 0), j))) {
            if (blockpos1.distToCenterSqr(position.x(), (double) blockpos1.getY() + 0.5D, position.z()) < (double) Mth.square(j) && (Boolean) this.predicate.map((blockpredicate) -> {
                return blockpredicate.test(serverLevel, blockpos1);
            }).orElse(true) && serverLevel.setBlockAndUpdate(blockpos1, this.blockState.getState(randomsource, blockpos1))) {
                this.triggerGameEvent.ifPresent((holder) -> {
                    serverLevel.gameEvent(entity, holder, blockpos1);
                });
            }
        }

    }

    @Override
    public MapCodec<ReplaceDisk> codec() {
        return ReplaceDisk.CODEC;
    }
}
