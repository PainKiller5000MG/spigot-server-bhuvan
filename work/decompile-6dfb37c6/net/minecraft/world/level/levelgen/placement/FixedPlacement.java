package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;

public class FixedPlacement extends PlacementModifier {

    public static final MapCodec<FixedPlacement> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockPos.CODEC.listOf().fieldOf("positions").forGetter((fixedplacement) -> {
            return fixedplacement.positions;
        })).apply(instance, FixedPlacement::new);
    });
    private final List<BlockPos> positions;

    public static FixedPlacement of(BlockPos... pos) {
        return new FixedPlacement(List.of(pos));
    }

    private FixedPlacement(List<BlockPos> positions) {
        this.positions = positions;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        int i = SectionPos.blockToSectionCoord(origin.getX());
        int j = SectionPos.blockToSectionCoord(origin.getZ());
        boolean flag = false;

        for (BlockPos blockpos1 : this.positions) {
            if (isSameChunk(i, j, blockpos1)) {
                flag = true;
                break;
            }
        }

        return !flag ? Stream.empty() : this.positions.stream().filter((blockpos2) -> {
            return isSameChunk(i, j, blockpos2);
        });
    }

    private static boolean isSameChunk(int chunkX, int chunkZ, BlockPos position) {
        return chunkX == SectionPos.blockToSectionCoord(position.getX()) && chunkZ == SectionPos.blockToSectionCoord(position.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.FIXED_PLACEMENT;
    }
}
