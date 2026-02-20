package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public abstract class StructurePiece {

    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected BoundingBox boundingBox;
    private @Nullable Direction orientation;
    private Mirror mirror;
    private Rotation rotation;
    protected int genDepth;
    private final StructurePieceType type;
    private static final Set<Block> SHAPE_CHECK_BLOCKS = ImmutableSet.builder().add(Blocks.NETHER_BRICK_FENCE).add(Blocks.TORCH).add(Blocks.WALL_TORCH).add(Blocks.OAK_FENCE).add(Blocks.SPRUCE_FENCE).add(Blocks.DARK_OAK_FENCE).add(Blocks.PALE_OAK_FENCE).add(Blocks.ACACIA_FENCE).add(Blocks.BIRCH_FENCE).add(Blocks.JUNGLE_FENCE).add(Blocks.LADDER).add(Blocks.IRON_BARS).build();

    protected StructurePiece(StructurePieceType type, int genDepth, BoundingBox boundingBox) {
        this.type = type;
        this.genDepth = genDepth;
        this.boundingBox = boundingBox;
    }

    public StructurePiece(StructurePieceType type, CompoundTag tag) {
        this(type, tag.getIntOr("GD", 0), (BoundingBox) tag.read("BB", BoundingBox.CODEC).orElseThrow());
        int i = tag.getIntOr("O", 0);

        this.setOrientation(i == -1 ? null : Direction.from2DDataValue(i));
    }

    protected static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int width, int height, int depth) {
        return direction.getAxis() == Direction.Axis.Z ? new BoundingBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1) : new BoundingBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
    }

    protected static Direction getRandomHorizontalDirection(RandomSource random) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(random);
    }

    public final CompoundTag createTag(StructurePieceSerializationContext context) {
        CompoundTag compoundtag = new CompoundTag();

        compoundtag.putString("id", BuiltInRegistries.STRUCTURE_PIECE.getKey(this.getType()).toString());
        compoundtag.store("BB", BoundingBox.CODEC, this.boundingBox);
        Direction direction = this.getOrientation();

        compoundtag.putInt("O", direction == null ? -1 : direction.get2DDataValue());
        compoundtag.putInt("GD", this.genDepth);
        this.addAdditionalSaveData(context, compoundtag);
        return compoundtag;
    }

    protected abstract void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag);

    public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {}

    public abstract void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos);

    public BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public int getGenDepth() {
        return this.genDepth;
    }

    public void setGenDepth(int genDepth) {
        this.genDepth = genDepth;
    }

    public boolean isCloseToChunk(ChunkPos pos, int distance) {
        int j = pos.getMinBlockX();
        int k = pos.getMinBlockZ();

        return this.boundingBox.intersects(j - distance, k - distance, j + 15 + distance, k + 15 + distance);
    }

    public BlockPos getLocatorPosition() {
        return new BlockPos(this.boundingBox.getCenter());
    }

    protected BlockPos.MutableBlockPos getWorldPos(int x, int y, int z) {
        return new BlockPos.MutableBlockPos(this.getWorldX(x, z), this.getWorldY(y), this.getWorldZ(x, z));
    }

    protected int getWorldX(int x, int z) {
        Direction direction = this.getOrientation();

        if (direction == null) {
            return x;
        } else {
            switch (direction) {
                case NORTH:
                case SOUTH:
                    return this.boundingBox.minX() + x;
                case WEST:
                    return this.boundingBox.maxX() - z;
                case EAST:
                    return this.boundingBox.minX() + z;
                default:
                    return x;
            }
        }
    }

    protected int getWorldY(int y) {
        return this.getOrientation() == null ? y : y + this.boundingBox.minY();
    }

    protected int getWorldZ(int x, int z) {
        Direction direction = this.getOrientation();

        if (direction == null) {
            return z;
        } else {
            switch (direction) {
                case NORTH:
                    return this.boundingBox.maxZ() - z;
                case SOUTH:
                    return this.boundingBox.minZ() + z;
                case WEST:
                case EAST:
                    return this.boundingBox.minZ() + x;
                default:
                    return z;
            }
        }
    }

    protected void placeBlock(WorldGenLevel level, BlockState blockState, int x, int y, int z, BoundingBox chunkBB) {
        BlockPos blockpos = this.getWorldPos(x, y, z);

        if (chunkBB.isInside(blockpos)) {
            if (this.canBeReplaced(level, x, y, z, chunkBB)) {
                if (this.mirror != Mirror.NONE) {
                    blockState = blockState.mirror(this.mirror);
                }

                if (this.rotation != Rotation.NONE) {
                    blockState = blockState.rotate(this.rotation);
                }

                level.setBlock(blockpos, blockState, 2);
                FluidState fluidstate = level.getFluidState(blockpos);

                if (!fluidstate.isEmpty()) {
                    level.scheduleTick(blockpos, fluidstate.getType(), 0);
                }

                if (StructurePiece.SHAPE_CHECK_BLOCKS.contains(blockState.getBlock())) {
                    level.getChunk(blockpos).markPosForPostprocessing(blockpos);
                }

            }
        }
    }

    protected boolean canBeReplaced(LevelReader level, int x, int y, int z, BoundingBox chunkBB) {
        return true;
    }

    protected BlockState getBlock(BlockGetter level, int x, int y, int z, BoundingBox chunkBB) {
        BlockPos blockpos = this.getWorldPos(x, y, z);

        return !chunkBB.isInside(blockpos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(blockpos);
    }

    protected boolean isInterior(LevelReader level, int x, int y, int z, BoundingBox chunkBB) {
        BlockPos blockpos = this.getWorldPos(x, y + 1, z);

        return !chunkBB.isInside(blockpos) ? false : blockpos.getY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, blockpos.getX(), blockpos.getZ());
    }

    protected void generateAirBox(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int k1 = y0; k1 <= y1; ++k1) {
            for (int l1 = x0; l1 <= x1; ++l1) {
                for (int i2 = z0; i2 <= z1; ++i2) {
                    this.placeBlock(level, Blocks.AIR.defaultBlockState(), l1, k1, i2, chunkBB);
                }
            }
        }

    }

    protected void generateBox(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1, BlockState edgeBlock, BlockState fillBlock, boolean skipAir) {
        for (int k1 = y0; k1 <= y1; ++k1) {
            for (int l1 = x0; l1 <= x1; ++l1) {
                for (int i2 = z0; i2 <= z1; ++i2) {
                    if (!skipAir || !this.getBlock(level, l1, k1, i2, chunkBB).isAir()) {
                        if (k1 != y0 && k1 != y1 && l1 != x0 && l1 != x1 && i2 != z0 && i2 != z1) {
                            this.placeBlock(level, fillBlock, l1, k1, i2, chunkBB);
                        } else {
                            this.placeBlock(level, edgeBlock, l1, k1, i2, chunkBB);
                        }
                    }
                }
            }
        }

    }

    protected void generateBox(WorldGenLevel level, BoundingBox chunkBB, BoundingBox boxBB, BlockState edgeBlock, BlockState fillBlock, boolean skipAir) {
        this.generateBox(level, chunkBB, boxBB.minX(), boxBB.minY(), boxBB.minZ(), boxBB.maxX(), boxBB.maxY(), boxBB.maxZ(), edgeBlock, fillBlock, skipAir);
    }

    protected void generateBox(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1, boolean skipAir, RandomSource random, StructurePiece.BlockSelector selector) {
        for (int k1 = y0; k1 <= y1; ++k1) {
            for (int l1 = x0; l1 <= x1; ++l1) {
                for (int i2 = z0; i2 <= z1; ++i2) {
                    if (!skipAir || !this.getBlock(level, l1, k1, i2, chunkBB).isAir()) {
                        selector.next(random, l1, k1, i2, k1 == y0 || k1 == y1 || l1 == x0 || l1 == x1 || i2 == z0 || i2 == z1);
                        this.placeBlock(level, selector.getNext(), l1, k1, i2, chunkBB);
                    }
                }
            }
        }

    }

    protected void generateBox(WorldGenLevel level, BoundingBox chunkBB, BoundingBox boxBB, boolean skipAir, RandomSource random, StructurePiece.BlockSelector selector) {
        this.generateBox(level, chunkBB, boxBB.minX(), boxBB.minY(), boxBB.minZ(), boxBB.maxX(), boxBB.maxY(), boxBB.maxZ(), skipAir, random, selector);
    }

    protected void generateMaybeBox(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, float probability, int x0, int y0, int z0, int x1, int y1, int z1, BlockState edgeBlock, BlockState fillBlock, boolean skipAir, boolean hasToBeInside) {
        for (int k1 = y0; k1 <= y1; ++k1) {
            for (int l1 = x0; l1 <= x1; ++l1) {
                for (int i2 = z0; i2 <= z1; ++i2) {
                    if (random.nextFloat() <= probability && (!skipAir || !this.getBlock(level, l1, k1, i2, chunkBB).isAir()) && (!hasToBeInside || this.isInterior(level, l1, k1, i2, chunkBB))) {
                        if (k1 != y0 && k1 != y1 && l1 != x0 && l1 != x1 && i2 != z0 && i2 != z1) {
                            this.placeBlock(level, fillBlock, l1, k1, i2, chunkBB);
                        } else {
                            this.placeBlock(level, edgeBlock, l1, k1, i2, chunkBB);
                        }
                    }
                }
            }
        }

    }

    protected void maybeGenerateBlock(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, float probability, int x, int y, int z, BlockState blockState) {
        if (random.nextFloat() < probability) {
            this.placeBlock(level, blockState, x, y, z, chunkBB);
        }

    }

    protected void generateUpperHalfSphere(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1, BlockState fillBlock, boolean skipAir) {
        float f = (float) (x1 - x0 + 1);
        float f1 = (float) (y1 - y0 + 1);
        float f2 = (float) (z1 - z0 + 1);
        float f3 = (float) x0 + f / 2.0F;
        float f4 = (float) z0 + f2 / 2.0F;

        for (int k1 = y0; k1 <= y1; ++k1) {
            float f5 = (float) (k1 - y0) / f1;

            for (int l1 = x0; l1 <= x1; ++l1) {
                float f6 = ((float) l1 - f3) / (f * 0.5F);

                for (int i2 = z0; i2 <= z1; ++i2) {
                    float f7 = ((float) i2 - f4) / (f2 * 0.5F);

                    if (!skipAir || !this.getBlock(level, l1, k1, i2, chunkBB).isAir()) {
                        float f8 = f6 * f6 + f5 * f5 + f7 * f7;

                        if (f8 <= 1.05F) {
                            this.placeBlock(level, fillBlock, l1, k1, i2, chunkBB);
                        }
                    }
                }
            }
        }

    }

    protected void fillColumnDown(WorldGenLevel level, BlockState blockState, int x, int startY, int z, BoundingBox chunkBB) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = this.getWorldPos(x, startY, z);

        if (chunkBB.isInside(blockpos_mutableblockpos)) {
            while (this.isReplaceableByStructures(level.getBlockState(blockpos_mutableblockpos)) && blockpos_mutableblockpos.getY() > level.getMinY() + 1) {
                level.setBlock(blockpos_mutableblockpos, blockState, 2);
                blockpos_mutableblockpos.move(Direction.DOWN);
            }

        }
    }

    protected boolean isReplaceableByStructures(BlockState state) {
        return state.isAir() || state.liquid() || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    protected boolean createChest(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int x, int y, int z, ResourceKey<LootTable> lootTable) {
        return this.createChest(level, chunkBB, random, this.getWorldPos(x, y, z), lootTable, (BlockState) null);
    }

    public static BlockState reorient(BlockGetter level, BlockPos blockPos, BlockState blockState) {
        Direction direction = null;

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = blockPos.relative(direction1);
            BlockState blockstate1 = level.getBlockState(blockpos1);

            if (blockstate1.is(Blocks.CHEST)) {
                return blockState;
            }

            if (blockstate1.isSolidRender()) {
                if (direction != null) {
                    direction = null;
                    break;
                }

                direction = direction1;
            }
        }

        if (direction != null) {
            return (BlockState) blockState.setValue(HorizontalDirectionalBlock.FACING, direction.getOpposite());
        } else {
            Direction direction2 = (Direction) blockState.getValue(HorizontalDirectionalBlock.FACING);
            BlockPos blockpos2 = blockPos.relative(direction2);

            if (level.getBlockState(blockpos2).isSolidRender()) {
                direction2 = direction2.getOpposite();
                blockpos2 = blockPos.relative(direction2);
            }

            if (level.getBlockState(blockpos2).isSolidRender()) {
                direction2 = direction2.getClockWise();
                blockpos2 = blockPos.relative(direction2);
            }

            if (level.getBlockState(blockpos2).isSolidRender()) {
                direction2 = direction2.getOpposite();
                blockPos.relative(direction2);
            }

            return (BlockState) blockState.setValue(HorizontalDirectionalBlock.FACING, direction2);
        }
    }

    protected boolean createChest(ServerLevelAccessor level, BoundingBox chunkBB, RandomSource random, BlockPos pos, ResourceKey<LootTable> lootTable, @Nullable BlockState blockState) {
        if (chunkBB.isInside(pos) && !level.getBlockState(pos).is(Blocks.CHEST)) {
            if (blockState == null) {
                blockState = reorient(level, pos, Blocks.CHEST.defaultBlockState());
            }

            level.setBlock(pos, blockState, 2);
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof ChestBlockEntity) {
                ((ChestBlockEntity) blockentity).setLootTable(lootTable, random.nextLong());
            }

            return true;
        } else {
            return false;
        }
    }

    protected boolean createDispenser(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable) {
        BlockPos blockpos = this.getWorldPos(x, y, z);

        if (chunkBB.isInside(blockpos) && !level.getBlockState(blockpos).is(Blocks.DISPENSER)) {
            this.placeBlock(level, (BlockState) Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing), x, y, z, chunkBB);
            BlockEntity blockentity = level.getBlockEntity(blockpos);

            if (blockentity instanceof DispenserBlockEntity) {
                ((DispenserBlockEntity) blockentity).setLootTable(lootTable, random.nextLong());
            }

            return true;
        } else {
            return false;
        }
    }

    public void move(int dx, int dy, int dz) {
        this.boundingBox.move(dx, dy, dz);
    }

    public static BoundingBox createBoundingBox(Stream<StructurePiece> pieces) {
        Stream stream1 = pieces.map(StructurePiece::getBoundingBox);

        Objects.requireNonNull(stream1);
        return (BoundingBox) BoundingBox.encapsulatingBoxes(stream1::iterator).orElseThrow(() -> {
            return new IllegalStateException("Unable to calculate boundingbox without pieces");
        });
    }

    public static @Nullable StructurePiece findCollisionPiece(List<StructurePiece> pieces, BoundingBox box) {
        for (StructurePiece structurepiece : pieces) {
            if (structurepiece.getBoundingBox().intersects(box)) {
                return structurepiece;
            }
        }

        return null;
    }

    public @Nullable Direction getOrientation() {
        return this.orientation;
    }

    public void setOrientation(@Nullable Direction orientation) {
        this.orientation = orientation;
        if (orientation == null) {
            this.rotation = Rotation.NONE;
            this.mirror = Mirror.NONE;
        } else {
            switch (orientation) {
                case SOUTH:
                    this.mirror = Mirror.LEFT_RIGHT;
                    this.rotation = Rotation.NONE;
                    break;
                case WEST:
                    this.mirror = Mirror.LEFT_RIGHT;
                    this.rotation = Rotation.CLOCKWISE_90;
                    break;
                case EAST:
                    this.mirror = Mirror.NONE;
                    this.rotation = Rotation.CLOCKWISE_90;
                    break;
                default:
                    this.mirror = Mirror.NONE;
                    this.rotation = Rotation.NONE;
            }
        }

    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public StructurePieceType getType() {
        return this.type;
    }

    public abstract static class BlockSelector {

        protected BlockState next;

        public BlockSelector() {
            this.next = Blocks.AIR.defaultBlockState();
        }

        public abstract void next(RandomSource random, int worldX, int worldY, int worldZ, boolean isEdge);

        public BlockState getNext() {
            return this.next;
        }
    }
}
