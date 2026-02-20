package net.minecraft.world.effect;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

class WeavingMobEffect extends MobEffect {

    private final ToIntFunction<RandomSource> maxCobwebs;

    protected WeavingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> maxCobwebs) {
        super(category, color, ParticleTypes.ITEM_COBWEB);
        this.maxCobwebs = maxCobwebs;
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED && (mob instanceof Player || (Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING))) {
            this.spawnCobwebsRandomlyAround(level, mob.getRandom(), mob.blockPosition());
        }

    }

    private void spawnCobwebsRandomlyAround(ServerLevel level, RandomSource random, BlockPos pos) {
        Set<BlockPos> set = Sets.newHashSet();
        int i = this.maxCobwebs.applyAsInt(random);

        for (BlockPos blockpos1 : BlockPos.randomInCube(random, 15, pos, 1)) {
            BlockPos blockpos2 = blockpos1.below();

            if (!set.contains(blockpos1) && level.getBlockState(blockpos1).canBeReplaced() && level.getBlockState(blockpos2).isFaceSturdy(level, blockpos2, Direction.UP)) {
                set.add(blockpos1.immutable());
                if (set.size() >= i) {
                    break;
                }
            }
        }

        for (BlockPos blockpos3 : set) {
            level.setBlock(blockpos3, Blocks.COBWEB.defaultBlockState(), 3);
            level.levelEvent(3018, blockpos3, 0);
        }

    }
}
