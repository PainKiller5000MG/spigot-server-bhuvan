package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {

    public static final MapCodec<WeightedPressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.intRange(1, 1024).fieldOf("max_weight").forGetter((weightedpressureplateblock) -> {
            return weightedpressureplateblock.maxWeight;
        }), BlockSetType.CODEC.fieldOf("block_set_type").forGetter((weightedpressureplateblock) -> {
            return weightedpressureplateblock.type;
        }), propertiesCodec()).apply(instance, WeightedPressurePlateBlock::new);
    });
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;

    @Override
    public MapCodec<WeightedPressurePlateBlock> codec() {
        return WeightedPressurePlateBlock.CODEC;
    }

    protected WeightedPressurePlateBlock(int maxWeight, BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(WeightedPressurePlateBlock.POWER, 0));
        this.maxWeight = maxWeight;
    }

    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        int i = Math.min(getEntityCount(level, WeightedPressurePlateBlock.TOUCH_AABB.move(pos), Entity.class), this.maxWeight);

        if (i > 0) {
            float f = (float) Math.min(this.maxWeight, i) / (float) this.maxWeight;

            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return (Integer) state.getValue(WeightedPressurePlateBlock.POWER);
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int signal) {
        return (BlockState) state.setValue(WeightedPressurePlateBlock.POWER, signal);
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WeightedPressurePlateBlock.POWER);
    }
}
