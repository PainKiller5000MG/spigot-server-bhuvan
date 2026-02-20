package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

public class UpgradeData {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final UpgradeData EMPTY = new UpgradeData(EmptyBlockGetter.INSTANCE);
    private static final String TAG_INDICES = "Indices";
    private static final Direction8[] DIRECTIONS = Direction8.values();
    private static final Codec<List<SavedTick<Block>>> BLOCK_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec().orElse(Blocks.AIR)).listOf();
    private static final Codec<List<SavedTick<Fluid>>> FLUID_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec().orElse(Fluids.EMPTY)).listOf();
    private final EnumSet<Direction8> sides;
    private final List<SavedTick<Block>> neighborBlockTicks;
    private final List<SavedTick<Fluid>> neighborFluidTicks;
    private final int[][] index;
    private static final Map<Block, UpgradeData.BlockFixer> MAP = new IdentityHashMap();
    private static final Set<UpgradeData.BlockFixer> CHUNKY_FIXERS = Sets.newHashSet();

    private UpgradeData(LevelHeightAccessor levelHeightAccessor) {
        this.sides = EnumSet.noneOf(Direction8.class);
        this.neighborBlockTicks = Lists.newArrayList();
        this.neighborFluidTicks = Lists.newArrayList();
        this.index = new int[levelHeightAccessor.getSectionsCount()][];
    }

    public UpgradeData(CompoundTag tag, LevelHeightAccessor levelHeightAccessor) {
        this(levelHeightAccessor);
        tag.getCompound("Indices").ifPresent((compoundtag1) -> {
            for (int i = 0; i < this.index.length; ++i) {
                this.index[i] = (int[]) compoundtag1.getIntArray(String.valueOf(i)).orElse((Object) null);
            }

        });
        int i = tag.getIntOr("Sides", 0);

        for (Direction8 direction8 : Direction8.values()) {
            if ((i & 1 << direction8.ordinal()) != 0) {
                this.sides.add(direction8);
            }
        }

        Optional optional = tag.read("neighbor_block_ticks", UpgradeData.BLOCK_TICKS_CODEC);
        List list = this.neighborBlockTicks;

        Objects.requireNonNull(this.neighborBlockTicks);
        optional.ifPresent(list::addAll);
        optional = tag.read("neighbor_fluid_ticks", UpgradeData.FLUID_TICKS_CODEC);
        list = this.neighborFluidTicks;
        Objects.requireNonNull(this.neighborFluidTicks);
        optional.ifPresent(list::addAll);
    }

    private UpgradeData(UpgradeData source) {
        this.sides = EnumSet.noneOf(Direction8.class);
        this.neighborBlockTicks = Lists.newArrayList();
        this.neighborFluidTicks = Lists.newArrayList();
        this.sides.addAll(source.sides);
        this.neighborBlockTicks.addAll(source.neighborBlockTicks);
        this.neighborFluidTicks.addAll(source.neighborFluidTicks);
        this.index = new int[source.index.length][];

        for (int i = 0; i < source.index.length; ++i) {
            int[] aint = source.index[i];

            this.index[i] = aint != null ? IntArrays.copy(aint) : null;
        }

    }

    public void upgrade(LevelChunk chunk) {
        this.upgradeInside(chunk);

        for (Direction8 direction8 : UpgradeData.DIRECTIONS) {
            upgradeSides(chunk, direction8);
        }

        Level level = chunk.getLevel();

        this.neighborBlockTicks.forEach((savedtick) -> {
            Block block = savedtick.type() == Blocks.AIR ? level.getBlockState(savedtick.pos()).getBlock() : (Block) savedtick.type();

            level.scheduleTick(savedtick.pos(), block, savedtick.delay(), savedtick.priority());
        });
        this.neighborFluidTicks.forEach((savedtick) -> {
            Fluid fluid = savedtick.type() == Fluids.EMPTY ? level.getFluidState(savedtick.pos()).getType() : (Fluid) savedtick.type();

            level.scheduleTick(savedtick.pos(), fluid, savedtick.delay(), savedtick.priority());
        });
        UpgradeData.CHUNKY_FIXERS.forEach((upgradedata_blockfixer) -> {
            upgradedata_blockfixer.processChunk(level);
        });
    }

    private static void upgradeSides(LevelChunk chunk, Direction8 direction8) {
        Level level = chunk.getLevel();

        if (chunk.getUpgradeData().sides.remove(direction8)) {
            Set<Direction> set = direction8.getDirections();
            int i = 0;
            int j = 15;
            boolean flag = set.contains(Direction.EAST);
            boolean flag1 = set.contains(Direction.WEST);
            boolean flag2 = set.contains(Direction.SOUTH);
            boolean flag3 = set.contains(Direction.NORTH);
            boolean flag4 = set.size() == 1;
            ChunkPos chunkpos = chunk.getPos();
            int k = chunkpos.getMinBlockX() + (!flag4 || !flag3 && !flag2 ? (flag1 ? 0 : 15) : 1);
            int l = chunkpos.getMinBlockX() + (!flag4 || !flag3 && !flag2 ? (flag1 ? 0 : 15) : 14);
            int i1 = chunkpos.getMinBlockZ() + (!flag4 || !flag && !flag1 ? (flag3 ? 0 : 15) : 1);
            int j1 = chunkpos.getMinBlockZ() + (!flag4 || !flag && !flag1 ? (flag3 ? 0 : 15) : 14);
            Direction[] adirection = Direction.values();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (BlockPos blockpos : BlockPos.betweenClosed(k, level.getMinY(), i1, l, level.getMaxY(), j1)) {
                BlockState blockstate = level.getBlockState(blockpos);
                BlockState blockstate1 = blockstate;

                for (Direction direction : adirection) {
                    blockpos_mutableblockpos.setWithOffset(blockpos, direction);
                    blockstate1 = updateState(blockstate1, direction, level, blockpos, blockpos_mutableblockpos);
                }

                Block.updateOrDestroy(blockstate, blockstate1, level, blockpos, 18);
            }

        }
    }

    private static BlockState updateState(BlockState state, Direction direction, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return ((UpgradeData.BlockFixer) UpgradeData.MAP.getOrDefault(state.getBlock(), UpgradeData.BlockFixers.DEFAULT)).updateShape(state, direction, level.getBlockState(neighbourPos), level, pos, neighbourPos);
    }

    private void upgradeInside(LevelChunk chunk) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = new BlockPos.MutableBlockPos();
        ChunkPos chunkpos = chunk.getPos();
        LevelAccessor levelaccessor = chunk.getLevel();

        for (int i = 0; i < this.index.length; ++i) {
            LevelChunkSection levelchunksection = chunk.getSection(i);
            int[] aint = this.index[i];

            this.index[i] = null;
            if (aint != null && aint.length > 0) {
                Direction[] adirection = Direction.values();
                PalettedContainer<BlockState> palettedcontainer = levelchunksection.getStates();
                int j = chunk.getSectionYFromSectionIndex(i);
                int k = SectionPos.sectionToBlockCoord(j);

                for (int l : aint) {
                    int i1 = l & 15;
                    int j1 = l >> 8 & 15;
                    int k1 = l >> 4 & 15;

                    blockpos_mutableblockpos.set(chunkpos.getMinBlockX() + i1, k + j1, chunkpos.getMinBlockZ() + k1);
                    BlockState blockstate = palettedcontainer.get(l);
                    BlockState blockstate1 = blockstate;

                    for (Direction direction : adirection) {
                        blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, direction);
                        if (SectionPos.blockToSectionCoord(blockpos_mutableblockpos.getX()) == chunkpos.x && SectionPos.blockToSectionCoord(blockpos_mutableblockpos.getZ()) == chunkpos.z) {
                            blockstate1 = updateState(blockstate1, direction, levelaccessor, blockpos_mutableblockpos, blockpos_mutableblockpos1);
                        }
                    }

                    Block.updateOrDestroy(blockstate, blockstate1, levelaccessor, blockpos_mutableblockpos, 18);
                }
            }
        }

        for (int l1 = 0; l1 < this.index.length; ++l1) {
            if (this.index[l1] != null) {
                UpgradeData.LOGGER.warn("Discarding update data for section {} for chunk ({} {})", new Object[]{levelaccessor.getSectionYFromSectionIndex(l1), chunkpos.x, chunkpos.z});
            }

            this.index[l1] = null;
        }

    }

    public boolean isEmpty() {
        for (int[] aint : this.index) {
            if (aint != null) {
                return false;
            }
        }

        return this.sides.isEmpty();
    }

    public CompoundTag write() {
        CompoundTag compoundtag = new CompoundTag();
        CompoundTag compoundtag1 = new CompoundTag();

        for (int i = 0; i < this.index.length; ++i) {
            String s = String.valueOf(i);

            if (this.index[i] != null && this.index[i].length != 0) {
                compoundtag1.putIntArray(s, this.index[i]);
            }
        }

        if (!compoundtag1.isEmpty()) {
            compoundtag.put("Indices", compoundtag1);
        }

        int j = 0;

        for (Direction8 direction8 : this.sides) {
            j |= 1 << direction8.ordinal();
        }

        compoundtag.putByte("Sides", (byte) j);
        if (!this.neighborBlockTicks.isEmpty()) {
            compoundtag.store("neighbor_block_ticks", UpgradeData.BLOCK_TICKS_CODEC, this.neighborBlockTicks);
        }

        if (!this.neighborFluidTicks.isEmpty()) {
            compoundtag.store("neighbor_fluid_ticks", UpgradeData.FLUID_TICKS_CODEC, this.neighborFluidTicks);
        }

        return compoundtag;
    }

    public UpgradeData copy() {
        return this == UpgradeData.EMPTY ? UpgradeData.EMPTY : new UpgradeData(this);
    }

    public interface BlockFixer {

        BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos);

        default void processChunk(LevelAccessor level) {}
    }

    private static enum BlockFixers implements UpgradeData.BlockFixer {

        BLACKLIST(new Block[]{Blocks.OBSERVER, Blocks.NETHER_PORTAL, Blocks.WHITE_CONCRETE_POWDER, Blocks.ORANGE_CONCRETE_POWDER, Blocks.MAGENTA_CONCRETE_POWDER, Blocks.LIGHT_BLUE_CONCRETE_POWDER, Blocks.YELLOW_CONCRETE_POWDER, Blocks.LIME_CONCRETE_POWDER, Blocks.PINK_CONCRETE_POWDER, Blocks.GRAY_CONCRETE_POWDER, Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.CYAN_CONCRETE_POWDER, Blocks.PURPLE_CONCRETE_POWDER, Blocks.BLUE_CONCRETE_POWDER, Blocks.BROWN_CONCRETE_POWDER, Blocks.GREEN_CONCRETE_POWDER, Blocks.RED_CONCRETE_POWDER, Blocks.BLACK_CONCRETE_POWDER, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL, Blocks.DRAGON_EGG, Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND, Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.ACACIA_SIGN, Blocks.CHERRY_SIGN, Blocks.JUNGLE_SIGN, Blocks.DARK_OAK_SIGN, Blocks.PALE_OAK_SIGN, Blocks.OAK_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN, Blocks.BIRCH_WALL_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN, Blocks.PALE_OAK_WALL_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN, Blocks.ACACIA_HANGING_SIGN, Blocks.JUNGLE_HANGING_SIGN, Blocks.DARK_OAK_HANGING_SIGN, Blocks.PALE_OAK_HANGING_SIGN, Blocks.OAK_WALL_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN, Blocks.JUNGLE_WALL_HANGING_SIGN, Blocks.DARK_OAK_WALL_HANGING_SIGN, Blocks.PALE_OAK_WALL_HANGING_SIGN}) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
                return state;
            }
        },
        DEFAULT(new Block[0]) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
                return state.updateShape(level, level, pos, direction, neighbourPos, level.getBlockState(neighbourPos), level.getRandom());
            }
        },
        CHEST(new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST}) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
                if (neighbour.is(state.getBlock()) && direction.getAxis().isHorizontal() && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE && neighbour.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                    Direction direction1 = (Direction) state.getValue(ChestBlock.FACING);

                    if (direction.getAxis() != direction1.getAxis() && direction1 == neighbour.getValue(ChestBlock.FACING)) {
                        ChestType chesttype = direction == direction1.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;

                        level.setBlock(neighbourPos, (BlockState) neighbour.setValue(ChestBlock.TYPE, chesttype.getOpposite()), 18);
                        if (direction1 == Direction.NORTH || direction1 == Direction.EAST) {
                            BlockEntity blockentity = level.getBlockEntity(pos);
                            BlockEntity blockentity1 = level.getBlockEntity(neighbourPos);

                            if (blockentity instanceof ChestBlockEntity && blockentity1 instanceof ChestBlockEntity) {
                                ChestBlockEntity.swapContents((ChestBlockEntity) blockentity, (ChestBlockEntity) blockentity1);
                            }
                        }

                        return (BlockState) state.setValue(ChestBlock.TYPE, chesttype);
                    }
                }

                return state;
            }
        },
        LEAVES(true, new Block[]{Blocks.ACACIA_LEAVES, Blocks.CHERRY_LEAVES, Blocks.BIRCH_LEAVES, Blocks.PALE_OAK_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES}) {
            private final ThreadLocal<List<ObjectSet<BlockPos>>> queue = ThreadLocal.withInitial(() -> {
                return Lists.newArrayListWithCapacity(7);
            });

            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
                BlockState blockstate2 = state.updateShape(level, level, pos, direction, neighbourPos, level.getBlockState(neighbourPos), level.getRandom());

                if (state != blockstate2) {
                    int i = (Integer) blockstate2.getValue(BlockStateProperties.DISTANCE);
                    List<ObjectSet<BlockPos>> list = (List) this.queue.get();

                    if (list.isEmpty()) {
                        for (int j = 0; j < 7; ++j) {
                            list.add(new ObjectOpenHashSet());
                        }
                    }

                    ((ObjectSet) list.get(i)).add(pos.immutable());
                }

                return state;
            }

            @Override
            public void processChunk(LevelAccessor level) {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
                List<ObjectSet<BlockPos>> list = (List) this.queue.get();

                for (int i = 2; i < list.size(); ++i) {
                    int j = i - 1;
                    ObjectSet<BlockPos> objectset = (ObjectSet) list.get(j);
                    ObjectSet<BlockPos> objectset1 = (ObjectSet) list.get(i);
                    ObjectIterator objectiterator = objectset.iterator();

                    while (objectiterator.hasNext()) {
                        BlockPos blockpos = (BlockPos) objectiterator.next();
                        BlockState blockstate = level.getBlockState(blockpos);

                        if ((Integer) blockstate.getValue(BlockStateProperties.DISTANCE) >= j) {
                            level.setBlock(blockpos, (BlockState) blockstate.setValue(BlockStateProperties.DISTANCE, j), 18);
                            if (i != 7) {
                                for (Direction direction : null.DIRECTIONS) {
                                    blockpos_mutableblockpos.setWithOffset(blockpos, direction);
                                    BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos);

                                    if (blockstate1.hasProperty(BlockStateProperties.DISTANCE) && (Integer) blockstate.getValue(BlockStateProperties.DISTANCE) > i) {
                                        objectset1.add(blockpos_mutableblockpos.immutable());
                                    }
                                }
                            }
                        }
                    }
                }

                list.clear();
            }
        },
        STEM_BLOCK(new Block[]{Blocks.MELON_STEM, Blocks.PUMPKIN_STEM}) {
            @Override
            public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
                if ((Integer) state.getValue(StemBlock.AGE) == 7) {
                    Block block = state.is(Blocks.PUMPKIN_STEM) ? Blocks.PUMPKIN : Blocks.MELON;

                    if (neighbour.is(block)) {
                        return (BlockState) (state.is(Blocks.PUMPKIN_STEM) ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
                    }
                }

                return state;
            }
        };

        public static final Direction[] DIRECTIONS = Direction.values();

        private BlockFixers(Block... blocks) {
            this(false, blocks);
        }

        private BlockFixers(boolean chunky, Block... blocks) {
            for (Block block : blocks) {
                UpgradeData.MAP.put(block, this);
            }

            if (chunky) {
                UpgradeData.CHUNKY_FIXERS.add(this);
            }

        }
    }
}
