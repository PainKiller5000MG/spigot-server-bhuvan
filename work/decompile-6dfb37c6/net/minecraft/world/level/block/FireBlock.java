package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FireBlock extends BaseFireBlock {

    public static final MapCodec<FireBlock> CODEC = simpleCodec(FireBlock::new);
    public static final int MAX_AGE = 15;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != Direction.DOWN;
    }).collect(Util.toMap());
    private final Function<BlockState, VoxelShape> shapes;
    private static final int IGNITE_INSTANT = 60;
    private static final int IGNITE_EASY = 30;
    private static final int IGNITE_MEDIUM = 15;
    private static final int IGNITE_HARD = 5;
    private static final int BURN_INSTANT = 100;
    private static final int BURN_EASY = 60;
    private static final int BURN_MEDIUM = 20;
    private static final int BURN_HARD = 5;
    public final Object2IntMap<Block> igniteOdds = new Object2IntOpenHashMap();
    private final Object2IntMap<Block> burnOdds = new Object2IntOpenHashMap();

    @Override
    public MapCodec<FireBlock> codec() {
        return FireBlock.CODEC;
    }

    public FireBlock(BlockBehaviour.Properties properties) {
        super(properties, 1.0F);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(FireBlock.AGE, 0)).setValue(FireBlock.NORTH, false)).setValue(FireBlock.EAST, false)).setValue(FireBlock.SOUTH, false)).setValue(FireBlock.WEST, false)).setValue(FireBlock.UP, false));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(16.0D, 0.0D, 1.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape = Shapes.empty();

            for (Map.Entry<Direction, BooleanProperty> map_entry : FireBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                if ((Boolean) blockstate.getValue((Property) map_entry.getValue())) {
                    voxelshape = Shapes.or(voxelshape, (VoxelShape) map.get(map_entry.getKey()));
                }
            }

            return voxelshape.isEmpty() ? FireBlock.SHAPE : voxelshape;
        }, new Property[]{FireBlock.AGE});
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return this.canSurvive(state, level, pos) ? this.getStateWithAge(level, pos, (Integer) state.getValue(FireBlock.AGE)) : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.getStateForPlacement(context.getLevel(), context.getClickedPos());
    }

    protected BlockState getStateForPlacement(BlockGetter level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        BlockState blockstate = level.getBlockState(blockpos1);

        if (!this.canBurn(blockstate) && !blockstate.isFaceSturdy(level, blockpos1, Direction.UP)) {
            BlockState blockstate1 = this.defaultBlockState();

            for (Direction direction : Direction.values()) {
                BooleanProperty booleanproperty = (BooleanProperty) FireBlock.PROPERTY_BY_DIRECTION.get(direction);

                if (booleanproperty != null) {
                    blockstate1 = (BlockState) blockstate1.setValue(booleanproperty, this.canBurn(level.getBlockState(pos.relative(direction))));
                }
            }

            return blockstate1;
        } else {
            return this.defaultBlockState();
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();

        return level.getBlockState(blockpos1).isFaceSturdy(level, blockpos1, Direction.UP) || this.isValidFireLocation(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, (Block) this, getFireTickDelay(level.random));
        if (level.canSpreadFireAround(pos)) {
            if (!state.canSurvive(level, pos)) {
                level.removeBlock(pos, false);
            }

            BlockState blockstate1 = level.getBlockState(pos.below());
            boolean flag = blockstate1.is(level.dimensionType().infiniburn());
            int i = (Integer) state.getValue(FireBlock.AGE);

            if (!flag && level.isRaining() && this.isNearRain(level, pos) && random.nextFloat() < 0.2F + (float) i * 0.03F) {
                level.removeBlock(pos, false);
            } else {
                int j = Math.min(15, i + random.nextInt(3) / 2);

                if (i != j) {
                    state = (BlockState) state.setValue(FireBlock.AGE, j);
                    level.setBlock(pos, state, 260);
                }

                if (!flag) {
                    if (!this.isValidFireLocation(level, pos)) {
                        BlockPos blockpos1 = pos.below();

                        if (!level.getBlockState(blockpos1).isFaceSturdy(level, blockpos1, Direction.UP) || i > 3) {
                            level.removeBlock(pos, false);
                        }

                        return;
                    }

                    if (i == 15 && random.nextInt(4) == 0 && !this.canBurn(level.getBlockState(pos.below()))) {
                        level.removeBlock(pos, false);
                        return;
                    }
                }

                boolean flag1 = (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, pos);
                int k = flag1 ? -50 : 0;

                this.checkBurnOut(level, pos.east(), 300 + k, random, i);
                this.checkBurnOut(level, pos.west(), 300 + k, random, i);
                this.checkBurnOut(level, pos.below(), 250 + k, random, i);
                this.checkBurnOut(level, pos.above(), 250 + k, random, i);
                this.checkBurnOut(level, pos.north(), 300 + k, random, i);
                this.checkBurnOut(level, pos.south(), 300 + k, random, i);
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

                for (int l = -1; l <= 1; ++l) {
                    for (int i1 = -1; i1 <= 1; ++i1) {
                        for (int j1 = -1; j1 <= 4; ++j1) {
                            if (l != 0 || j1 != 0 || i1 != 0) {
                                int k1 = 100;

                                if (j1 > 1) {
                                    k1 += (j1 - 1) * 100;
                                }

                                blockpos_mutableblockpos.setWithOffset(pos, l, j1, i1);
                                int l1 = this.getIgniteOdds(level, blockpos_mutableblockpos);

                                if (l1 > 0) {
                                    int i2 = (l1 + 40 + level.getDifficulty().getId() * 7) / (i + 30);

                                    if (flag1) {
                                        i2 /= 2;
                                    }

                                    if (i2 > 0 && random.nextInt(k1) <= i2 && (!level.isRaining() || !this.isNearRain(level, blockpos_mutableblockpos))) {
                                        int j2 = Math.min(15, i + random.nextInt(5) / 4);

                                        level.setBlock(blockpos_mutableblockpos, this.getStateWithAge(level, blockpos_mutableblockpos, j2), 3);
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    protected boolean isNearRain(Level level, BlockPos testPos) {
        return level.isRainingAt(testPos) || level.isRainingAt(testPos.west()) || level.isRainingAt(testPos.east()) || level.isRainingAt(testPos.north()) || level.isRainingAt(testPos.south());
    }

    private int getBurnOdds(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) state.getValue(BlockStateProperties.WATERLOGGED) ? 0 : this.burnOdds.getInt(state.getBlock());
    }

    private int getIgniteOdds(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) state.getValue(BlockStateProperties.WATERLOGGED) ? 0 : this.igniteOdds.getInt(state.getBlock());
    }

    private void checkBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age) {
        int k = this.getBurnOdds(level.getBlockState(pos));

        if (random.nextInt(chance) < k) {
            BlockState blockstate = level.getBlockState(pos);

            if (random.nextInt(age + 10) < 5 && !level.isRainingAt(pos)) {
                int l = Math.min(age + random.nextInt(5) / 4, 15);

                level.setBlock(pos, this.getStateWithAge(level, pos, l), 3);
            } else {
                level.removeBlock(pos, false);
            }

            Block block = blockstate.getBlock();

            if (block instanceof TntBlock) {
                TntBlock.prime(level, pos);
            }
        }

    }

    private BlockState getStateWithAge(LevelReader level, BlockPos pos, int age) {
        BlockState blockstate = getState(level, pos);

        return blockstate.is(Blocks.FIRE) ? (BlockState) blockstate.setValue(FireBlock.AGE, age) : blockstate;
    }

    private boolean isValidFireLocation(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.canBurn(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }

        return false;
    }

    private int getIgniteOdds(LevelReader level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) {
            return 0;
        } else {
            int i = 0;

            for (Direction direction : Direction.values()) {
                BlockState blockstate = level.getBlockState(pos.relative(direction));

                i = Math.max(this.getIgniteOdds(blockstate), i);
            }

            return i;
        }
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return this.getIgniteOdds(state) > 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, (Block) this, getFireTickDelay(level.random));
    }

    private static int getFireTickDelay(RandomSource random) {
        return 30 + random.nextInt(10);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FireBlock.AGE, FireBlock.NORTH, FireBlock.EAST, FireBlock.SOUTH, FireBlock.WEST, FireBlock.UP);
    }

    public void setFlammable(Block block, int igniteOdds, int burnOdds) {
        this.igniteOdds.put(block, igniteOdds);
        this.burnOdds.put(block, burnOdds);
    }

    public static void bootStrap() {
        FireBlock fireblock = (FireBlock) Blocks.FIRE;

        fireblock.setFlammable(Blocks.OAK_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.SPRUCE_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.BIRCH_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.JUNGLE_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.ACACIA_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.CHERRY_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.DARK_OAK_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.PALE_OAK_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.MANGROVE_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_PLANKS, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_MOSAIC, 5, 20);
        fireblock.setFlammable(Blocks.OAK_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.SPRUCE_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.BIRCH_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.JUNGLE_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.ACACIA_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.CHERRY_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.DARK_OAK_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.PALE_OAK_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.MANGROVE_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_MOSAIC_SLAB, 5, 20);
        fireblock.setFlammable(Blocks.OAK_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.SPRUCE_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.BIRCH_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.JUNGLE_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.ACACIA_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.CHERRY_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.DARK_OAK_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.PALE_OAK_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.MANGROVE_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_FENCE_GATE, 5, 20);
        fireblock.setFlammable(Blocks.OAK_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.SPRUCE_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.BIRCH_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.JUNGLE_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.ACACIA_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.CHERRY_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.DARK_OAK_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.PALE_OAK_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.MANGROVE_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_FENCE, 5, 20);
        fireblock.setFlammable(Blocks.OAK_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.BIRCH_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.SPRUCE_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.JUNGLE_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.ACACIA_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.CHERRY_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.DARK_OAK_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.PALE_OAK_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.MANGROVE_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.BAMBOO_MOSAIC_STAIRS, 5, 20);
        fireblock.setFlammable(Blocks.OAK_LOG, 5, 5);
        fireblock.setFlammable(Blocks.SPRUCE_LOG, 5, 5);
        fireblock.setFlammable(Blocks.BIRCH_LOG, 5, 5);
        fireblock.setFlammable(Blocks.JUNGLE_LOG, 5, 5);
        fireblock.setFlammable(Blocks.ACACIA_LOG, 5, 5);
        fireblock.setFlammable(Blocks.CHERRY_LOG, 5, 5);
        fireblock.setFlammable(Blocks.PALE_OAK_LOG, 5, 5);
        fireblock.setFlammable(Blocks.DARK_OAK_LOG, 5, 5);
        fireblock.setFlammable(Blocks.MANGROVE_LOG, 5, 5);
        fireblock.setFlammable(Blocks.BAMBOO_BLOCK, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_OAK_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_SPRUCE_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_BIRCH_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_JUNGLE_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_ACACIA_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_CHERRY_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_DARK_OAK_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_PALE_OAK_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_MANGROVE_LOG, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_BAMBOO_BLOCK, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_OAK_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_SPRUCE_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_BIRCH_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_JUNGLE_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_ACACIA_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_CHERRY_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_DARK_OAK_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_PALE_OAK_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.STRIPPED_MANGROVE_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.OAK_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.SPRUCE_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.BIRCH_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.JUNGLE_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.ACACIA_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.CHERRY_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.PALE_OAK_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.DARK_OAK_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.MANGROVE_WOOD, 5, 5);
        fireblock.setFlammable(Blocks.MANGROVE_ROOTS, 5, 20);
        fireblock.setFlammable(Blocks.OAK_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.SPRUCE_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.BIRCH_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.JUNGLE_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.ACACIA_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.CHERRY_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.DARK_OAK_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.PALE_OAK_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.MANGROVE_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.BOOKSHELF, 30, 20);
        fireblock.setFlammable(Blocks.TNT, 15, 100);
        fireblock.setFlammable(Blocks.SHORT_GRASS, 60, 100);
        fireblock.setFlammable(Blocks.FERN, 60, 100);
        fireblock.setFlammable(Blocks.DEAD_BUSH, 60, 100);
        fireblock.setFlammable(Blocks.SHORT_DRY_GRASS, 60, 100);
        fireblock.setFlammable(Blocks.TALL_DRY_GRASS, 60, 100);
        fireblock.setFlammable(Blocks.SUNFLOWER, 60, 100);
        fireblock.setFlammable(Blocks.LILAC, 60, 100);
        fireblock.setFlammable(Blocks.ROSE_BUSH, 60, 100);
        fireblock.setFlammable(Blocks.PEONY, 60, 100);
        fireblock.setFlammable(Blocks.TALL_GRASS, 60, 100);
        fireblock.setFlammable(Blocks.LARGE_FERN, 60, 100);
        fireblock.setFlammable(Blocks.DANDELION, 60, 100);
        fireblock.setFlammable(Blocks.POPPY, 60, 100);
        fireblock.setFlammable(Blocks.OPEN_EYEBLOSSOM, 60, 100);
        fireblock.setFlammable(Blocks.CLOSED_EYEBLOSSOM, 60, 100);
        fireblock.setFlammable(Blocks.BLUE_ORCHID, 60, 100);
        fireblock.setFlammable(Blocks.ALLIUM, 60, 100);
        fireblock.setFlammable(Blocks.AZURE_BLUET, 60, 100);
        fireblock.setFlammable(Blocks.RED_TULIP, 60, 100);
        fireblock.setFlammable(Blocks.ORANGE_TULIP, 60, 100);
        fireblock.setFlammable(Blocks.WHITE_TULIP, 60, 100);
        fireblock.setFlammable(Blocks.PINK_TULIP, 60, 100);
        fireblock.setFlammable(Blocks.OXEYE_DAISY, 60, 100);
        fireblock.setFlammable(Blocks.CORNFLOWER, 60, 100);
        fireblock.setFlammable(Blocks.LILY_OF_THE_VALLEY, 60, 100);
        fireblock.setFlammable(Blocks.TORCHFLOWER, 60, 100);
        fireblock.setFlammable(Blocks.PITCHER_PLANT, 60, 100);
        fireblock.setFlammable(Blocks.WITHER_ROSE, 60, 100);
        fireblock.setFlammable(Blocks.PINK_PETALS, 60, 100);
        fireblock.setFlammable(Blocks.WILDFLOWERS, 60, 100);
        fireblock.setFlammable(Blocks.LEAF_LITTER, 60, 100);
        fireblock.setFlammable(Blocks.CACTUS_FLOWER, 60, 100);
        fireblock.setFlammable(Blocks.WHITE_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.ORANGE_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.MAGENTA_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.LIGHT_BLUE_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.YELLOW_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.LIME_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.PINK_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.GRAY_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.LIGHT_GRAY_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.CYAN_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.PURPLE_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.BLUE_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.BROWN_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.GREEN_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.RED_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.BLACK_WOOL, 30, 60);
        fireblock.setFlammable(Blocks.VINE, 15, 100);
        fireblock.setFlammable(Blocks.COAL_BLOCK, 5, 5);
        fireblock.setFlammable(Blocks.HAY_BLOCK, 60, 20);
        fireblock.setFlammable(Blocks.TARGET, 15, 20);
        fireblock.setFlammable(Blocks.WHITE_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.ORANGE_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.MAGENTA_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.LIGHT_BLUE_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.YELLOW_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.LIME_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.PINK_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.GRAY_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.LIGHT_GRAY_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.CYAN_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.PURPLE_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.BLUE_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.BROWN_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.GREEN_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.RED_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.BLACK_CARPET, 60, 20);
        fireblock.setFlammable(Blocks.PALE_MOSS_BLOCK, 5, 100);
        fireblock.setFlammable(Blocks.PALE_MOSS_CARPET, 5, 100);
        fireblock.setFlammable(Blocks.PALE_HANGING_MOSS, 5, 100);
        fireblock.setFlammable(Blocks.DRIED_KELP_BLOCK, 30, 60);
        fireblock.setFlammable(Blocks.BAMBOO, 60, 60);
        fireblock.setFlammable(Blocks.SCAFFOLDING, 60, 60);
        fireblock.setFlammable(Blocks.LECTERN, 30, 20);
        fireblock.setFlammable(Blocks.COMPOSTER, 5, 20);
        fireblock.setFlammable(Blocks.SWEET_BERRY_BUSH, 60, 100);
        fireblock.setFlammable(Blocks.BEEHIVE, 5, 20);
        fireblock.setFlammable(Blocks.BEE_NEST, 30, 20);
        fireblock.setFlammable(Blocks.AZALEA_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.FLOWERING_AZALEA_LEAVES, 30, 60);
        fireblock.setFlammable(Blocks.CAVE_VINES, 15, 60);
        fireblock.setFlammable(Blocks.CAVE_VINES_PLANT, 15, 60);
        fireblock.setFlammable(Blocks.SPORE_BLOSSOM, 60, 100);
        fireblock.setFlammable(Blocks.AZALEA, 30, 60);
        fireblock.setFlammable(Blocks.FLOWERING_AZALEA, 30, 60);
        fireblock.setFlammable(Blocks.BIG_DRIPLEAF, 60, 100);
        fireblock.setFlammable(Blocks.BIG_DRIPLEAF_STEM, 60, 100);
        fireblock.setFlammable(Blocks.SMALL_DRIPLEAF, 60, 100);
        fireblock.setFlammable(Blocks.HANGING_ROOTS, 30, 60);
        fireblock.setFlammable(Blocks.GLOW_LICHEN, 15, 100);
        fireblock.setFlammable(Blocks.FIREFLY_BUSH, 60, 100);
        fireblock.setFlammable(Blocks.BUSH, 60, 100);
        fireblock.setFlammable(Blocks.ACACIA_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.BAMBOO_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.BIRCH_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.CHERRY_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.DARK_OAK_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.JUNGLE_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.MANGROVE_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.OAK_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.PALE_OAK_SHELF, 30, 20);
        fireblock.setFlammable(Blocks.SPRUCE_SHELF, 30, 20);
    }
}
