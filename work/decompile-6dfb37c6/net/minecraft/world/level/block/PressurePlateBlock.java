package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PressurePlateBlock extends BasePressurePlateBlock {

    public static final MapCodec<PressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter((pressureplateblock) -> {
            return pressureplateblock.type;
        }), propertiesCodec()).apply(instance, PressurePlateBlock::new);
    });
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<PressurePlateBlock> codec() {
        return PressurePlateBlock.CODEC;
    }

    protected PressurePlateBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(PressurePlateBlock.POWERED, false));
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return (Boolean) state.getValue(PressurePlateBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int signal) {
        return (BlockState) state.setValue(PressurePlateBlock.POWERED, signal > 0);
    }

    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        Class oclass;

        switch (this.type.pressurePlateSensitivity()) {
            case EVERYTHING:
                oclass = Entity.class;
                break;
            case MOBS:
                oclass = LivingEntity.class;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Class<? extends Entity> oclass1 = oclass;

        return getEntityCount(level, PressurePlateBlock.TOUCH_AABB.move(pos), oclass1) > 0 ? 15 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PressurePlateBlock.POWERED);
    }
}
