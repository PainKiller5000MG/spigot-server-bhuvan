package net.minecraft.world.level.levelgen.placement;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public abstract class RepeatingPlacement extends PlacementModifier {

    public RepeatingPlacement() {}

    protected abstract int count(RandomSource random, BlockPos origin);

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        return IntStream.range(0, this.count(random, origin)).mapToObj((i) -> {
            return origin;
        });
    }
}
