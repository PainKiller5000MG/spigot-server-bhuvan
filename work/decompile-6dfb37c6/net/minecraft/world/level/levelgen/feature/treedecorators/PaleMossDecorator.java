package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public class PaleMossDecorator extends TreeDecorator {

    public static final MapCodec<PaleMossDecorator> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.floatRange(0.0F, 1.0F).fieldOf("leaves_probability").forGetter((palemossdecorator) -> {
            return palemossdecorator.leavesProbability;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("trunk_probability").forGetter((palemossdecorator) -> {
            return palemossdecorator.trunkProbability;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("ground_probability").forGetter((palemossdecorator) -> {
            return palemossdecorator.groundProbability;
        })).apply(instance, PaleMossDecorator::new);
    });
    private final float leavesProbability;
    private final float trunkProbability;
    private final float groundProbability;

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.PALE_MOSS;
    }

    public PaleMossDecorator(float leavesProbability, float trunkProbability, float groundProbability) {
        this.leavesProbability = leavesProbability;
        this.trunkProbability = trunkProbability;
        this.groundProbability = groundProbability;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomsource = context.random();
        WorldGenLevel worldgenlevel = (WorldGenLevel) context.level();
        List<BlockPos> list = Util.shuffledCopy(context.logs(), randomsource);

        if (!list.isEmpty()) {
            BlockPos blockpos = (BlockPos) Collections.min(list, Comparator.comparingInt(Vec3i::getY));

            if (randomsource.nextFloat() < this.groundProbability) {
                worldgenlevel.registryAccess().lookup(Registries.CONFIGURED_FEATURE).flatMap((registry) -> {
                    return registry.get(VegetationFeatures.PALE_MOSS_PATCH);
                }).ifPresent((holder_reference) -> {
                    ((ConfiguredFeature) holder_reference.value()).place(worldgenlevel, worldgenlevel.getLevel().getChunkSource().getGenerator(), randomsource, blockpos.above());
                });
            }

            context.logs().forEach((blockpos1) -> {
                if (randomsource.nextFloat() < this.trunkProbability) {
                    BlockPos blockpos2 = blockpos1.below();

                    if (context.isAir(blockpos2)) {
                        addMossHanger(blockpos2, context);
                    }
                }

            });
            context.leaves().forEach((blockpos1) -> {
                if (randomsource.nextFloat() < this.leavesProbability) {
                    BlockPos blockpos2 = blockpos1.below();

                    if (context.isAir(blockpos2)) {
                        addMossHanger(blockpos2, context);
                    }
                }

            });
        }
    }

    private static void addMossHanger(BlockPos pos, TreeDecorator.Context context) {
        while (context.isAir(pos.below()) && (double) context.random().nextFloat() >= 0.5D) {
            context.setBlock(pos, (BlockState) Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, false));
            pos = pos.below();
        }

        context.setBlock(pos, (BlockState) Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, true));
    }
}
