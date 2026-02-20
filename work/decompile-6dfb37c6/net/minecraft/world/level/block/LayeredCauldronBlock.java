package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LayeredCauldronBlock extends AbstractCauldronBlock {

    public static final MapCodec<LayeredCauldronBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Biome.Precipitation.CODEC.fieldOf("precipitation").forGetter((layeredcauldronblock) -> {
            return layeredcauldronblock.precipitationType;
        }), CauldronInteraction.CODEC.fieldOf("interactions").forGetter((layeredcauldronblock) -> {
            return layeredcauldronblock.interactions;
        }), propertiesCodec()).apply(instance, LayeredCauldronBlock::new);
    });
    public static final int MIN_FILL_LEVEL = 1;
    public static final int MAX_FILL_LEVEL = 3;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
    private static final int BASE_CONTENT_HEIGHT = 6;
    private static final double HEIGHT_PER_LEVEL = 3.0D;
    private static final VoxelShape[] FILLED_SHAPES = (VoxelShape[]) Util.make(() -> {
        return Block.boxes(2, (i) -> {
            return Shapes.or(AbstractCauldronBlock.SHAPE, Block.column(12.0D, 4.0D, getPixelContentHeight(i + 1)));
        });
    });
    private final Biome.Precipitation precipitationType;

    @Override
    public MapCodec<LayeredCauldronBlock> codec() {
        return LayeredCauldronBlock.CODEC;
    }

    public LayeredCauldronBlock(Biome.Precipitation precipitationType, CauldronInteraction.InteractionMap interactionMap, BlockBehaviour.Properties properties) {
        super(properties, interactionMap);
        this.precipitationType = precipitationType;
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(LayeredCauldronBlock.LEVEL, 1));
    }

    @Override
    public boolean isFull(BlockState state) {
        return (Integer) state.getValue(LayeredCauldronBlock.LEVEL) == 3;
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return fluid == Fluids.WATER && this.precipitationType == Biome.Precipitation.RAIN;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return getPixelContentHeight((Integer) state.getValue(LayeredCauldronBlock.LEVEL)) / 16.0D;
    }

    private static double getPixelContentHeight(int level) {
        return 6.0D + (double) level * 3.0D;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return LayeredCauldronBlock.FILLED_SHAPES[(Integer) state.getValue(LayeredCauldronBlock.LEVEL) - 1];
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel serverlevel) {
            BlockPos blockpos1 = pos.immutable();

            effectApplier.runBefore(InsideBlockEffectType.EXTINGUISH, (entity1) -> {
                if (entity1.isOnFire() && entity1.mayInteract(serverlevel, blockpos1)) {
                    this.handleEntityOnFireInside(state, level, blockpos1);
                }

            });
        }

        effectApplier.apply(InsideBlockEffectType.EXTINGUISH);
    }

    private void handleEntityOnFireInside(BlockState state, Level level, BlockPos pos) {
        if (this.precipitationType == Biome.Precipitation.SNOW) {
            lowerFillLevel((BlockState) Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, (Integer) state.getValue(LayeredCauldronBlock.LEVEL)), level, pos);
        } else {
            lowerFillLevel(state, level, pos);
        }

    }

    public static void lowerFillLevel(BlockState state, Level level, BlockPos pos) {
        int i = (Integer) state.getValue(LayeredCauldronBlock.LEVEL) - 1;
        BlockState blockstate1 = i == 0 ? Blocks.CAULDRON.defaultBlockState() : (BlockState) state.setValue(LayeredCauldronBlock.LEVEL, i);

        level.setBlockAndUpdate(pos, blockstate1);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockstate1));
    }

    @Override
    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(level, precipitation) && (Integer) state.getValue(LayeredCauldronBlock.LEVEL) != 3 && precipitation == this.precipitationType) {
            BlockState blockstate1 = (BlockState) state.cycle(LayeredCauldronBlock.LEVEL);

            level.setBlockAndUpdate(pos, blockstate1);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockstate1));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return (Integer) state.getValue(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid) {
        if (!this.isFull(state)) {
            BlockState blockstate1 = (BlockState) state.setValue(LayeredCauldronBlock.LEVEL, (Integer) state.getValue(LayeredCauldronBlock.LEVEL) + 1);

            level.setBlockAndUpdate(pos, blockstate1);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockstate1));
            level.levelEvent(1047, pos, 0);
        }
    }
}
