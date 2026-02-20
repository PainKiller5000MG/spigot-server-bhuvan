package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLogsDecorator extends TreeDecorator {

    public static final MapCodec<AttachedToLogsDecorator> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter((attachedtologsdecorator) -> {
            return attachedtologsdecorator.probability;
        }), BlockStateProvider.CODEC.fieldOf("block_provider").forGetter((attachedtologsdecorator) -> {
            return attachedtologsdecorator.blockProvider;
        }), ExtraCodecs.nonEmptyList(Direction.CODEC.listOf()).fieldOf("directions").forGetter((attachedtologsdecorator) -> {
            return attachedtologsdecorator.directions;
        })).apply(instance, AttachedToLogsDecorator::new);
    });
    private final float probability;
    private final BlockStateProvider blockProvider;
    private final List<Direction> directions;

    public AttachedToLogsDecorator(float probability, BlockStateProvider blockProvider, List<Direction> directions) {
        this.probability = probability;
        this.blockProvider = blockProvider;
        this.directions = directions;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomsource = context.random();

        for (BlockPos blockpos : Util.shuffledCopy(context.logs(), randomsource)) {
            Direction direction = (Direction) Util.getRandom(this.directions, randomsource);
            BlockPos blockpos1 = blockpos.relative(direction);

            if (randomsource.nextFloat() <= this.probability && context.isAir(blockpos1)) {
                context.setBlock(blockpos1, this.blockProvider.getState(randomsource, blockpos1));
            }
        }

    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ATTACHED_TO_LOGS;
    }
}
