package net.minecraft.world.level.block;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StemBlock extends VegetationBlock implements BonemealableBlock {

    public static final MapCodec<StemBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ResourceKey.codec(Registries.BLOCK).fieldOf("fruit").forGetter((stemblock) -> {
            return stemblock.fruit;
        }), ResourceKey.codec(Registries.BLOCK).fieldOf("attached_stem").forGetter((stemblock) -> {
            return stemblock.attachedStem;
        }), ResourceKey.codec(Registries.ITEM).fieldOf("seed").forGetter((stemblock) -> {
            return stemblock.seed;
        }), propertiesCodec()).apply(instance, StemBlock::new);
    });
    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    private static final VoxelShape[] SHAPES = Block.boxes(7, (i) -> {
        return Block.column(2.0D, 0.0D, (double) (2 + i * 2));
    });
    private final ResourceKey<Block> fruit;
    private final ResourceKey<Block> attachedStem;
    private final ResourceKey<Item> seed;

    @Override
    public MapCodec<StemBlock> codec() {
        return StemBlock.CODEC;
    }

    protected StemBlock(ResourceKey<Block> fruit, ResourceKey<Block> attachedStem, ResourceKey<Item> seed, BlockBehaviour.Properties properties) {
        super(properties);
        this.fruit = fruit;
        this.attachedStem = attachedStem;
        this.seed = seed;
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(StemBlock.AGE, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StemBlock.SHAPES[(Integer) state.getValue(StemBlock.AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) >= 9) {
            float f = CropBlock.getGrowthSpeed(this, level, pos);

            if (random.nextInt((int) (25.0F / f) + 1) == 0) {
                int i = (Integer) state.getValue(StemBlock.AGE);

                if (i < 7) {
                    state = (BlockState) state.setValue(StemBlock.AGE, i + 1);
                    level.setBlock(pos, state, 2);
                } else {
                    Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    BlockPos blockpos1 = pos.relative(direction);
                    BlockState blockstate1 = level.getBlockState(blockpos1.below());

                    if (level.getBlockState(blockpos1).isAir() && (blockstate1.is(Blocks.FARMLAND) || blockstate1.is(BlockTags.DIRT))) {
                        Registry<Block> registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
                        Optional<Block> optional = registry.getOptional(this.fruit);
                        Optional<Block> optional1 = registry.getOptional(this.attachedStem);

                        if (optional.isPresent() && optional1.isPresent()) {
                            level.setBlockAndUpdate(blockpos1, ((Block) optional.get()).defaultBlockState());
                            level.setBlockAndUpdate(pos, (BlockState) ((Block) optional1.get()).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction));
                        }
                    }
                }
            }

        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack((ItemLike) DataFixUtils.orElse(level.registryAccess().lookupOrThrow(Registries.ITEM).getOptional(this.seed), this));
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return (Integer) state.getValue(StemBlock.AGE) != 7;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int i = Math.min(7, (Integer) state.getValue(StemBlock.AGE) + Mth.nextInt(level.random, 2, 5));
        BlockState blockstate1 = (BlockState) state.setValue(StemBlock.AGE, i);

        level.setBlock(pos, blockstate1, 2);
        if (i == 7) {
            blockstate1.randomTick(level, pos, level.random);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StemBlock.AGE);
    }
}
