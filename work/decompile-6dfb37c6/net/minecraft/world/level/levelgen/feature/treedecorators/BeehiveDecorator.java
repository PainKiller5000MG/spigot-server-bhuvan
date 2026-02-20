package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BeehiveDecorator extends TreeDecorator {

    public static final MapCodec<BeehiveDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability").xmap(BeehiveDecorator::new, (beehivedecorator) -> {
        return beehivedecorator.probability;
    });
    private static final Direction WORLDGEN_FACING = Direction.SOUTH;
    private static final Direction[] SPAWN_DIRECTIONS = (Direction[]) Direction.Plane.HORIZONTAL.stream().filter((direction) -> {
        return direction != BeehiveDecorator.WORLDGEN_FACING.getOpposite();
    }).toArray((i) -> {
        return new Direction[i];
    });
    private final float probability;

    public BeehiveDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.BEEHIVE;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> list = context.leaves();
        List<BlockPos> list1 = context.logs();

        if (!((List) list1).isEmpty()) {
            RandomSource randomsource = context.random();

            if (randomsource.nextFloat() < this.probability) {
                int i = !list.isEmpty() ? Math.max(((BlockPos) list.getFirst()).getY() - 1, ((BlockPos) list1.getFirst()).getY() + 1) : Math.min(((BlockPos) list1.getFirst()).getY() + 1 + randomsource.nextInt(3), ((BlockPos) list1.getLast()).getY());
                List<BlockPos> list2 = (List) list1.stream().filter((blockpos) -> {
                    return blockpos.getY() == i;
                }).flatMap((blockpos) -> {
                    Stream stream = Stream.of(BeehiveDecorator.SPAWN_DIRECTIONS);

                    Objects.requireNonNull(blockpos);
                    return stream.map(blockpos::relative);
                }).collect(Collectors.toList());

                if (!list2.isEmpty()) {
                    Util.shuffle(list2, randomsource);
                    Optional<BlockPos> optional = list2.stream().filter((blockpos) -> {
                        return context.isAir(blockpos) && context.isAir(blockpos.relative(BeehiveDecorator.WORLDGEN_FACING));
                    }).findFirst();

                    if (!optional.isEmpty()) {
                        context.setBlock((BlockPos) optional.get(), (BlockState) Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, BeehiveDecorator.WORLDGEN_FACING));
                        context.level().getBlockEntity((BlockPos) optional.get(), BlockEntityType.BEEHIVE).ifPresent((beehiveblockentity) -> {
                            int j = 2 + randomsource.nextInt(2);

                            for (int k = 0; k < j; ++k) {
                                beehiveblockentity.storeBee(BeehiveBlockEntity.Occupant.create(randomsource.nextInt(599)));
                            }

                        });
                    }
                }
            }
        }
    }
}
