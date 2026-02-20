package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public class RandomBlockStateMatchTest extends RuleTest {

    public static final MapCodec<RandomBlockStateMatchTest> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockState.CODEC.fieldOf("block_state").forGetter((randomblockstatematchtest) -> {
            return randomblockstatematchtest.blockState;
        }), Codec.FLOAT.fieldOf("probability").forGetter((randomblockstatematchtest) -> {
            return randomblockstatematchtest.probability;
        })).apply(instance, RandomBlockStateMatchTest::new);
    });
    private final BlockState blockState;
    private final float probability;

    public RandomBlockStateMatchTest(BlockState blockState, float probability) {
        this.blockState = blockState;
        this.probability = probability;
    }

    @Override
    public boolean test(BlockState blockState, RandomSource random) {
        return blockState == this.blockState && random.nextFloat() < this.probability;
    }

    @Override
    protected RuleTestType<?> getType() {
        return RuleTestType.RANDOM_BLOCKSTATE_TEST;
    }
}
