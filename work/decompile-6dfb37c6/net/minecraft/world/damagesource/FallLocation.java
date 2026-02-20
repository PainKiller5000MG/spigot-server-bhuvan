package net.minecraft.world.damagesource;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public record FallLocation(String id) {

    public static final FallLocation GENERIC = new FallLocation("generic");
    public static final FallLocation LADDER = new FallLocation("ladder");
    public static final FallLocation VINES = new FallLocation("vines");
    public static final FallLocation WEEPING_VINES = new FallLocation("weeping_vines");
    public static final FallLocation TWISTING_VINES = new FallLocation("twisting_vines");
    public static final FallLocation SCAFFOLDING = new FallLocation("scaffolding");
    public static final FallLocation OTHER_CLIMBABLE = new FallLocation("other_climbable");
    public static final FallLocation WATER = new FallLocation("water");

    public static FallLocation blockToFallLocation(BlockState blockState) {
        return !blockState.is(Blocks.LADDER) && !blockState.is(BlockTags.TRAPDOORS) ? (blockState.is(Blocks.VINE) ? FallLocation.VINES : (!blockState.is(Blocks.WEEPING_VINES) && !blockState.is(Blocks.WEEPING_VINES_PLANT) ? (!blockState.is(Blocks.TWISTING_VINES) && !blockState.is(Blocks.TWISTING_VINES_PLANT) ? (blockState.is(Blocks.SCAFFOLDING) ? FallLocation.SCAFFOLDING : FallLocation.OTHER_CLIMBABLE) : FallLocation.TWISTING_VINES) : FallLocation.WEEPING_VINES)) : FallLocation.LADDER;
    }

    public static @Nullable FallLocation getCurrentFallLocation(LivingEntity mob) {
        Optional<BlockPos> optional = mob.getLastClimbablePos();

        if (optional.isPresent()) {
            BlockState blockstate = mob.level().getBlockState((BlockPos) optional.get());

            return blockToFallLocation(blockstate);
        } else {
            return mob.isInWater() ? FallLocation.WATER : null;
        }
    }

    public String languageKey() {
        return "death.fell.accident." + this.id;
    }
}
