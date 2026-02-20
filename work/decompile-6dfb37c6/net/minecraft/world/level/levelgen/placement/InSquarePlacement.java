package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public class InSquarePlacement extends PlacementModifier {

    private static final InSquarePlacement INSTANCE = new InSquarePlacement();
    public static final MapCodec<InSquarePlacement> CODEC = MapCodec.unit(() -> {
        return InSquarePlacement.INSTANCE;
    });

    public InSquarePlacement() {}

    public static InSquarePlacement spread() {
        return InSquarePlacement.INSTANCE;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        int i = random.nextInt(16) + origin.getX();
        int j = random.nextInt(16) + origin.getZ();

        return Stream.of(new BlockPos(i, origin.getY(), j));
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.IN_SQUARE;
    }
}
