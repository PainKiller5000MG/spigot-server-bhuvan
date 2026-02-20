package net.minecraft.world.level.block;

import com.google.common.collect.BiMap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class CopperChestBlock extends ChestBlock {

    public static final MapCodec<CopperChestBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperChestBlock::getState), BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("open_sound").forGetter(ChestBlock::getOpenChestSound), BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("close_sound").forGetter(ChestBlock::getCloseChestSound), propertiesCodec()).apply(instance, CopperChestBlock::new);
    });
    private static final Map<Block, Supplier<Block>> COPPER_TO_COPPER_CHEST_MAPPING = Map.of(Blocks.COPPER_BLOCK, (Supplier) () -> {
        return Blocks.COPPER_CHEST;
    }, Blocks.EXPOSED_COPPER, (Supplier) () -> {
        return Blocks.EXPOSED_COPPER_CHEST;
    }, Blocks.WEATHERED_COPPER, (Supplier) () -> {
        return Blocks.WEATHERED_COPPER_CHEST;
    }, Blocks.OXIDIZED_COPPER, (Supplier) () -> {
        return Blocks.OXIDIZED_COPPER_CHEST;
    }, Blocks.WAXED_COPPER_BLOCK, (Supplier) () -> {
        return Blocks.COPPER_CHEST;
    }, Blocks.WAXED_EXPOSED_COPPER, (Supplier) () -> {
        return Blocks.EXPOSED_COPPER_CHEST;
    }, Blocks.WAXED_WEATHERED_COPPER, (Supplier) () -> {
        return Blocks.WEATHERED_COPPER_CHEST;
    }, Blocks.WAXED_OXIDIZED_COPPER, (Supplier) () -> {
        return Blocks.OXIDIZED_COPPER_CHEST;
    });
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    public MapCodec<? extends CopperChestBlock> codec() {
        return CopperChestBlock.CODEC;
    }

    public CopperChestBlock(WeatheringCopper.WeatherState weatherState, SoundEvent openSound, SoundEvent closeSound, BlockBehaviour.Properties properties) {
        super(() -> {
            return BlockEntityType.CHEST;
        }, openSound, closeSound, properties);
        this.weatherState = weatherState;
    }

    @Override
    public boolean chestCanConnectTo(BlockState blockState) {
        return blockState.is(BlockTags.COPPER_CHESTS) && blockState.hasProperty(ChestBlock.TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = super.getStateForPlacement(context);

        return getLeastOxidizedChestOfConnectedBlocks(blockstate, context.getLevel(), context.getClickedPos());
    }

    private static BlockState getLeastOxidizedChestOfConnectedBlocks(BlockState state, Level level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.relative(getConnectedDirection(state)));

        if (!((ChestType) state.getValue(ChestBlock.TYPE)).equals(ChestType.SINGLE)) {
            Block block = state.getBlock();

            if (block instanceof CopperChestBlock) {
                CopperChestBlock copperchestblock = (CopperChestBlock) block;

                block = blockstate1.getBlock();
                if (block instanceof CopperChestBlock) {
                    CopperChestBlock copperchestblock1 = (CopperChestBlock) block;
                    BlockState blockstate2 = state;
                    BlockState blockstate3 = blockstate1;

                    if (copperchestblock.isWaxed() != copperchestblock1.isWaxed()) {
                        blockstate2 = (BlockState) unwaxBlock(copperchestblock, state).orElse(state);
                        blockstate3 = (BlockState) unwaxBlock(copperchestblock1, blockstate1).orElse(blockstate1);
                    }

                    Block block1 = copperchestblock.weatherState.ordinal() <= copperchestblock1.weatherState.ordinal() ? blockstate2.getBlock() : blockstate3.getBlock();

                    return block1.withPropertiesOf(blockstate2);
                }
            }
        }

        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BlockState blockstate2 = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);

        if (this.chestCanConnectTo(neighbourState)) {
            ChestType chesttype = (ChestType) blockstate2.getValue(ChestBlock.TYPE);

            if (!chesttype.equals(ChestType.SINGLE) && getConnectedDirection(blockstate2) == directionToNeighbour) {
                return neighbourState.getBlock().withPropertiesOf(blockstate2);
            }
        }

        return blockstate2;
    }

    private static Optional<BlockState> unwaxBlock(CopperChestBlock copperChestBlock, BlockState state) {
        return !copperChestBlock.isWaxed() ? Optional.of(state) : Optional.ofNullable((Block) ((BiMap) HoneycombItem.WAX_OFF_BY_BLOCK.get()).get(state.getBlock())).map((block) -> {
            return block.withPropertiesOf(state);
        });
    }

    public WeatheringCopper.WeatherState getState() {
        return this.weatherState;
    }

    public static BlockState getFromCopperBlock(Block copperBlock, Direction facing, Level level, BlockPos pos) {
        Map map = CopperChestBlock.COPPER_TO_COPPER_CHEST_MAPPING;
        Block block1 = Blocks.COPPER_CHEST;

        Objects.requireNonNull(block1);
        CopperChestBlock copperchestblock = (CopperChestBlock) ((Supplier) map.getOrDefault(copperBlock, block1::asBlock)).get();
        ChestType chesttype = copperchestblock.getChestType(level, pos, facing);
        BlockState blockstate = (BlockState) ((BlockState) copperchestblock.defaultBlockState().setValue(CopperChestBlock.FACING, facing)).setValue(CopperChestBlock.TYPE, chesttype);

        return getLeastOxidizedChestOfConnectedBlocks(blockstate, level, pos);
    }

    public boolean isWaxed() {
        return true;
    }

    @Override
    public boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
        return oldState.is(BlockTags.COPPER_CHESTS);
    }
}
