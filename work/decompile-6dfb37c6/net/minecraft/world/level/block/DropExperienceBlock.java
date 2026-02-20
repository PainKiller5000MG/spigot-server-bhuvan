package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class DropExperienceBlock extends Block {

    public static final MapCodec<DropExperienceBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(IntProvider.codec(0, 10).fieldOf("experience").forGetter((dropexperienceblock) -> {
            return dropexperienceblock.xpRange;
        }), propertiesCodec()).apply(instance, DropExperienceBlock::new);
    });
    private final IntProvider xpRange;

    @Override
    public MapCodec<? extends DropExperienceBlock> codec() {
        return DropExperienceBlock.CODEC;
    }

    public DropExperienceBlock(IntProvider xpRange, BlockBehaviour.Properties properties) {
        super(properties);
        this.xpRange = xpRange;
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) {
            this.tryDropExperience(level, pos, tool, this.xpRange);
        }

    }
}
