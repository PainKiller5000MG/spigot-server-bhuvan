package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public class MineshaftPieces {

    private static final int DEFAULT_SHAFT_WIDTH = 3;
    private static final int DEFAULT_SHAFT_HEIGHT = 3;
    private static final int DEFAULT_SHAFT_LENGTH = 5;
    private static final int MAX_PILLAR_HEIGHT = 20;
    private static final int MAX_CHAIN_HEIGHT = 50;
    private static final int MAX_DEPTH = 8;
    public static final int MAGIC_START_Y = 50;

    public MineshaftPieces() {}

    private static MineshaftPieces.@Nullable MineShaftPiece createRandomShaftPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth, MineshaftStructure.Type type) {
        int i1 = random.nextInt(100);

        if (i1 >= 80) {
            BoundingBox boundingbox = MineshaftPieces.MineShaftCrossing.findCrossing(structurePieceAccessor, random, footX, footY, footZ, direction);

            if (boundingbox != null) {
                return new MineshaftPieces.MineShaftCrossing(genDepth, boundingbox, direction, type);
            }
        } else if (i1 >= 70) {
            BoundingBox boundingbox1 = MineshaftPieces.MineShaftStairs.findStairs(structurePieceAccessor, random, footX, footY, footZ, direction);

            if (boundingbox1 != null) {
                return new MineshaftPieces.MineShaftStairs(genDepth, boundingbox1, direction, type);
            }
        } else {
            BoundingBox boundingbox2 = MineshaftPieces.MineShaftCorridor.findCorridorSize(structurePieceAccessor, random, footX, footY, footZ, direction);

            if (boundingbox2 != null) {
                return new MineshaftPieces.MineShaftCorridor(genDepth, random, boundingbox2, direction, type);
            }
        }

        return null;
    }

    private static MineshaftPieces.@Nullable MineShaftPiece generateAndAddPiece(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
        if (depth > 8) {
            return null;
        } else if (Math.abs(footX - startPiece.getBoundingBox().minX()) <= 80 && Math.abs(footZ - startPiece.getBoundingBox().minZ()) <= 80) {
            MineshaftStructure.Type mineshaftstructure_type = ((MineshaftPieces.MineShaftPiece) startPiece).type;
            MineshaftPieces.MineShaftPiece mineshaftpieces_mineshaftpiece = createRandomShaftPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth + 1, mineshaftstructure_type);

            if (mineshaftpieces_mineshaftpiece != null) {
                structurePieceAccessor.addPiece(mineshaftpieces_mineshaftpiece);
                mineshaftpieces_mineshaftpiece.addChildren(startPiece, structurePieceAccessor, random);
            }

            return mineshaftpieces_mineshaftpiece;
        } else {
            return null;
        }
    }

    private abstract static class MineShaftPiece extends StructurePiece {

        protected MineshaftStructure.Type type;

        public MineShaftPiece(StructurePieceType pieceType, int genDepth, MineshaftStructure.Type type, BoundingBox boundingBox) {
            super(pieceType, genDepth, boundingBox);
            this.type = type;
        }

        public MineShaftPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.type = MineshaftStructure.Type.byId(tag.getIntOr("MST", 0));
        }

        @Override
        protected boolean canBeReplaced(LevelReader level, int x, int y, int z, BoundingBox chunkBB) {
            BlockState blockstate = this.getBlock(level, x, y, z, chunkBB);

            return !blockstate.is(this.type.getPlanksState().getBlock()) && !blockstate.is(this.type.getWoodState().getBlock()) && !blockstate.is(this.type.getFenceState().getBlock()) && !blockstate.is(Blocks.IRON_CHAIN);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("MST", this.type.ordinal());
        }

        protected boolean isSupportingBox(BlockGetter level, BoundingBox chunkBB, int x0, int x1, int y1, int z0) {
            for (int i1 = x0; i1 <= x1; ++i1) {
                if (this.getBlock(level, i1, y1 + 1, z0, chunkBB).isAir()) {
                    return false;
                }
            }

            return true;
        }

        protected boolean isInInvalidLocation(LevelAccessor level, BoundingBox chunkBB) {
            int i = Math.max(this.boundingBox.minX() - 1, chunkBB.minX());
            int j = Math.max(this.boundingBox.minY() - 1, chunkBB.minY());
            int k = Math.max(this.boundingBox.minZ() - 1, chunkBB.minZ());
            int l = Math.min(this.boundingBox.maxX() + 1, chunkBB.maxX());
            int i1 = Math.min(this.boundingBox.maxY() + 1, chunkBB.maxY());
            int j1 = Math.min(this.boundingBox.maxZ() + 1, chunkBB.maxZ());
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos((i + l) / 2, (j + i1) / 2, (k + j1) / 2);

            if (level.getBiome(blockpos_mutableblockpos).is(BiomeTags.MINESHAFT_BLOCKING)) {
                return true;
            } else {
                for (int k1 = i; k1 <= l; ++k1) {
                    for (int l1 = k; l1 <= j1; ++l1) {
                        if (level.getBlockState(blockpos_mutableblockpos.set(k1, j, l1)).liquid()) {
                            return true;
                        }

                        if (level.getBlockState(blockpos_mutableblockpos.set(k1, i1, l1)).liquid()) {
                            return true;
                        }
                    }
                }

                for (int i2 = i; i2 <= l; ++i2) {
                    for (int j2 = j; j2 <= i1; ++j2) {
                        if (level.getBlockState(blockpos_mutableblockpos.set(i2, j2, k)).liquid()) {
                            return true;
                        }

                        if (level.getBlockState(blockpos_mutableblockpos.set(i2, j2, j1)).liquid()) {
                            return true;
                        }
                    }
                }

                for (int k2 = k; k2 <= j1; ++k2) {
                    for (int l2 = j; l2 <= i1; ++l2) {
                        if (level.getBlockState(blockpos_mutableblockpos.set(i, l2, k2)).liquid()) {
                            return true;
                        }

                        if (level.getBlockState(blockpos_mutableblockpos.set(l, l2, k2)).liquid()) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        protected void setPlanksBlock(WorldGenLevel level, BoundingBox chunkBB, BlockState planksBlock, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, chunkBB)) {
                BlockPos blockpos = this.getWorldPos(x, y, z);
                BlockState blockstate1 = level.getBlockState(blockpos);

                if (!blockstate1.isFaceSturdy(level, blockpos, Direction.UP)) {
                    level.setBlock(blockpos, planksBlock, 2);
                }

            }
        }
    }

    public static class MineShaftRoom extends MineshaftPieces.MineShaftPiece {

        private final List<BoundingBox> childEntranceBoxes = Lists.newLinkedList();

        public MineShaftRoom(int genDepth, RandomSource random, int west, int north, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_ROOM, genDepth, type, new BoundingBox(west, 50, north, west + 7 + random.nextInt(6), 54 + random.nextInt(6), north + 7 + random.nextInt(6)));
            this.type = type;
        }

        public MineShaftRoom(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_ROOM, tag);
            this.childEntranceBoxes.addAll((Collection) tag.read("Entrances", BoundingBox.CODEC.listOf()).orElse(List.of()));
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            int i = this.getGenDepth();
            int j = this.boundingBox.getYSpan() - 3 - 1;

            if (j <= 0) {
                j = 1;
            }

            int k;

            for (k = 0; k < this.boundingBox.getXSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getXSpan());
                if (k + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineshaftpieces_mineshaftpiece = MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + k, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.minZ() - 1, Direction.NORTH, i);

                if (mineshaftpieces_mineshaftpiece != null) {
                    BoundingBox boundingbox = mineshaftpieces_mineshaftpiece.getBoundingBox();

                    this.childEntranceBoxes.add(new BoundingBox(boundingbox.minX(), boundingbox.minY(), this.boundingBox.minZ(), boundingbox.maxX(), boundingbox.maxY(), this.boundingBox.minZ() + 1));
                }
            }

            for (k = 0; k < this.boundingBox.getXSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getXSpan());
                if (k + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineshaftpieces_mineshaftpiece1 = MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + k, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.maxZ() + 1, Direction.SOUTH, i);

                if (mineshaftpieces_mineshaftpiece1 != null) {
                    BoundingBox boundingbox1 = mineshaftpieces_mineshaftpiece1.getBoundingBox();

                    this.childEntranceBoxes.add(new BoundingBox(boundingbox1.minX(), boundingbox1.minY(), this.boundingBox.maxZ() - 1, boundingbox1.maxX(), boundingbox1.maxY(), this.boundingBox.maxZ()));
                }
            }

            for (k = 0; k < this.boundingBox.getZSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getZSpan());
                if (k + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineshaftpieces_mineshaftpiece2 = MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.minZ() + k, Direction.WEST, i);

                if (mineshaftpieces_mineshaftpiece2 != null) {
                    BoundingBox boundingbox2 = mineshaftpieces_mineshaftpiece2.getBoundingBox();

                    this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.minX(), boundingbox2.minY(), boundingbox2.minZ(), this.boundingBox.minX() + 1, boundingbox2.maxY(), boundingbox2.maxZ()));
                }
            }

            for (k = 0; k < this.boundingBox.getZSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getZSpan());
                if (k + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                StructurePiece structurepiece1 = MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.minZ() + k, Direction.EAST, i);

                if (structurepiece1 != null) {
                    BoundingBox boundingbox3 = structurepiece1.getBoundingBox();

                    this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.maxX() - 1, boundingbox3.minY(), boundingbox3.minZ(), this.boundingBox.maxX(), boundingbox3.maxY(), boundingbox3.maxZ()));
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (!this.isInInvalidLocation(level, chunkBB)) {
                this.generateBox(level, chunkBB, this.boundingBox.minX(), this.boundingBox.minY() + 1, this.boundingBox.minZ(), this.boundingBox.maxX(), Math.min(this.boundingBox.minY() + 3, this.boundingBox.maxY()), this.boundingBox.maxZ(), MineshaftPieces.MineShaftRoom.CAVE_AIR, MineshaftPieces.MineShaftRoom.CAVE_AIR, false);

                for (BoundingBox boundingbox1 : this.childEntranceBoxes) {
                    this.generateBox(level, chunkBB, boundingbox1.minX(), boundingbox1.maxY() - 2, boundingbox1.minZ(), boundingbox1.maxX(), boundingbox1.maxY(), boundingbox1.maxZ(), MineshaftPieces.MineShaftRoom.CAVE_AIR, MineshaftPieces.MineShaftRoom.CAVE_AIR, false);
                }

                this.generateUpperHalfSphere(level, chunkBB, this.boundingBox.minX(), this.boundingBox.minY() + 4, this.boundingBox.minZ(), this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ(), MineshaftPieces.MineShaftRoom.CAVE_AIR, false);
            }
        }

        @Override
        public void move(int dx, int dy, int dz) {
            super.move(dx, dy, dz);

            for (BoundingBox boundingbox : this.childEntranceBoxes) {
                boundingbox.move(dx, dy, dz);
            }

        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Entrances", BoundingBox.CODEC.listOf(), this.childEntranceBoxes);
        }
    }

    public static class MineShaftCorridor extends MineshaftPieces.MineShaftPiece {

        private final boolean hasRails;
        private final boolean spiderCorridor;
        private boolean hasPlacedSpider;
        private final int numSections;

        public MineShaftCorridor(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, tag);
            this.hasRails = tag.getBooleanOr("hr", false);
            this.spiderCorridor = tag.getBooleanOr("sc", false);
            this.hasPlacedSpider = tag.getBooleanOr("hps", false);
            this.numSections = tag.getIntOr("Num", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("hr", this.hasRails);
            tag.putBoolean("sc", this.spiderCorridor);
            tag.putBoolean("hps", this.hasPlacedSpider);
            tag.putInt("Num", this.numSections);
        }

        public MineShaftCorridor(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, genDepth, type, boundingBox);
            this.setOrientation(direction);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !this.hasRails && random.nextInt(23) == 0;
            if (this.getOrientation().getAxis() == Direction.Axis.Z) {
                this.numSections = boundingBox.getZSpan() / 5;
            } else {
                this.numSections = boundingBox.getXSpan() / 5;
            }

        }

        public static @Nullable BoundingBox findCorridorSize(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction) {
            for (int l = random.nextInt(3) + 2; l > 0; --l) {
                int i1 = l * 5;
                BoundingBox boundingbox;

                switch (direction) {
                    case NORTH:
                    default:
                        boundingbox = new BoundingBox(0, 0, -(i1 - 1), 2, 2, 0);
                        break;
                    case SOUTH:
                        boundingbox = new BoundingBox(0, 0, 0, 2, 2, i1 - 1);
                        break;
                    case WEST:
                        boundingbox = new BoundingBox(-(i1 - 1), 0, 0, 0, 2, 2);
                        break;
                    case EAST:
                        boundingbox = new BoundingBox(0, 0, 0, i1 - 1, 2, 2);
                }

                boundingbox.move(footX, footY, footZ);
                if (structurePieceAccessor.findCollisionPiece(boundingbox) == null) {
                    return boundingbox;
                }
            }

            return null;
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            int i = this.getGenDepth();
            int j = random.nextInt(4);
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                    default:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ() - 1, direction, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), Direction.WEST, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), Direction.EAST, i);
                        }
                        break;
                    case SOUTH:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() + 1, direction, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() - 3, Direction.WEST, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() - 3, Direction.EAST, i);
                        }
                        break;
                    case WEST:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), direction, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                        }
                        break;
                    case EAST:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), direction, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() - 3, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() - 3, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                        }
                }
            }

            if (i < 8) {
                if (direction != Direction.NORTH && direction != Direction.SOUTH) {
                    for (int k = this.boundingBox.minX() + 3; k + 3 <= this.boundingBox.maxX(); k += 5) {
                        int l = random.nextInt(5);

                        if (l == 0) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, k, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i + 1);
                        } else if (l == 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, k, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i + 1);
                        }
                    }
                } else {
                    for (int i1 = this.boundingBox.minZ() + 3; i1 + 3 <= this.boundingBox.maxZ(); i1 += 5) {
                        int j1 = random.nextInt(5);

                        if (j1 == 0) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), i1, Direction.WEST, i + 1);
                        } else if (j1 == 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), i1, Direction.EAST, i + 1);
                        }
                    }
                }
            }

        }

        @Override
        protected boolean createChest(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int x, int y, int z, ResourceKey<LootTable> lootTable) {
            BlockPos blockpos = this.getWorldPos(x, y, z);

            if (chunkBB.isInside(blockpos) && level.getBlockState(blockpos).isAir() && !level.getBlockState(blockpos.below()).isAir()) {
                BlockState blockstate = (BlockState) Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, random.nextBoolean() ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);

                this.placeBlock(level, blockstate, x, y, z, chunkBB);
                MinecartChest minecartchest = EntityType.CHEST_MINECART.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);

                if (minecartchest != null) {
                    minecartchest.setInitialPos((double) blockpos.getX() + 0.5D, (double) blockpos.getY() + 0.5D, (double) blockpos.getZ() + 0.5D);
                    minecartchest.setLootTable(lootTable, random.nextLong());
                    level.addFreshEntity(minecartchest);
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (!this.isInInvalidLocation(level, chunkBB)) {
                int i = 0;
                int j = 2;
                int k = 0;
                int l = 2;
                int i1 = this.numSections * 5 - 1;
                BlockState blockstate = this.type.getPlanksState();

                this.generateBox(level, chunkBB, 0, 0, 0, 2, 1, i1, MineshaftPieces.MineShaftCorridor.CAVE_AIR, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                this.generateMaybeBox(level, chunkBB, random, 0.8F, 0, 2, 0, 2, 2, i1, MineshaftPieces.MineShaftCorridor.CAVE_AIR, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false, false);
                if (this.spiderCorridor) {
                    this.generateMaybeBox(level, chunkBB, random, 0.6F, 0, 0, 0, 2, 1, i1, Blocks.COBWEB.defaultBlockState(), MineshaftPieces.MineShaftCorridor.CAVE_AIR, false, true);
                }

                for (int j1 = 0; j1 < this.numSections; ++j1) {
                    int k1 = 2 + j1 * 5;

                    this.placeSupport(level, chunkBB, 0, 0, k1, 2, 2, random);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 0, 2, k1 - 1);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 2, 2, k1 - 1);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 0, 2, k1 + 1);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 2, 2, k1 + 1);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 0, 2, k1 - 2);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 2, 2, k1 - 2);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 0, 2, k1 + 2);
                    this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 2, 2, k1 + 2);
                    if (random.nextInt(100) == 0) {
                        this.createChest(level, chunkBB, random, 2, 0, k1 - 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (random.nextInt(100) == 0) {
                        this.createChest(level, chunkBB, random, 0, 0, k1 + 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (this.spiderCorridor && !this.hasPlacedSpider) {
                        int l1 = 1;
                        int i2 = k1 - 1 + random.nextInt(3);
                        BlockPos blockpos1 = this.getWorldPos(1, 0, i2);

                        if (chunkBB.isInside(blockpos1) && this.isInterior(level, 1, 0, i2, chunkBB)) {
                            this.hasPlacedSpider = true;
                            level.setBlock(blockpos1, Blocks.SPAWNER.defaultBlockState(), 2);
                            BlockEntity blockentity = level.getBlockEntity(blockpos1);

                            if (blockentity instanceof SpawnerBlockEntity) {
                                SpawnerBlockEntity spawnerblockentity = (SpawnerBlockEntity) blockentity;

                                spawnerblockentity.setEntityId(EntityType.CAVE_SPIDER, random);
                            }
                        }
                    }
                }

                for (int j2 = 0; j2 <= 2; ++j2) {
                    for (int k2 = 0; k2 <= i1; ++k2) {
                        this.setPlanksBlock(level, chunkBB, blockstate, j2, -1, k2);
                    }
                }

                int l2 = 2;

                this.placeDoubleLowerOrUpperSupport(level, chunkBB, 0, -1, 2);
                if (this.numSections > 1) {
                    int i3 = i1 - 2;

                    this.placeDoubleLowerOrUpperSupport(level, chunkBB, 0, -1, i3);
                }

                if (this.hasRails) {
                    BlockState blockstate1 = (BlockState) Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH);

                    for (int j3 = 0; j3 <= i1; ++j3) {
                        BlockState blockstate2 = this.getBlock(level, 1, -1, j3, chunkBB);

                        if (!blockstate2.isAir() && blockstate2.isSolidRender()) {
                            float f = this.isInterior(level, 1, 0, j3, chunkBB) ? 0.7F : 0.9F;

                            this.maybeGenerateBlock(level, chunkBB, random, f, 1, 0, j3, blockstate1);
                        }
                    }
                }

            }
        }

        private void placeDoubleLowerOrUpperSupport(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z) {
            BlockState blockstate = this.type.getWoodState();
            BlockState blockstate1 = this.type.getPlanksState();

            if (this.getBlock(level, x, y, z, chunkBB).is(blockstate1.getBlock())) {
                this.fillPillarDownOrChainUp(level, blockstate, x, y, z, chunkBB);
            }

            if (this.getBlock(level, x + 2, y, z, chunkBB).is(blockstate1.getBlock())) {
                this.fillPillarDownOrChainUp(level, blockstate, x + 2, y, z, chunkBB);
            }

        }

        @Override
        protected void fillColumnDown(WorldGenLevel level, BlockState columnState, int x, int startY, int z, BoundingBox chunkBB) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = this.getWorldPos(x, startY, z);

            if (chunkBB.isInside(blockpos_mutableblockpos)) {
                int l = blockpos_mutableblockpos.getY();

                while (this.isReplaceableByStructures(level.getBlockState(blockpos_mutableblockpos)) && blockpos_mutableblockpos.getY() > level.getMinY() + 1) {
                    blockpos_mutableblockpos.move(Direction.DOWN);
                }

                if (this.canPlaceColumnOnTopOf(level, blockpos_mutableblockpos, level.getBlockState(blockpos_mutableblockpos))) {
                    while (blockpos_mutableblockpos.getY() < l) {
                        blockpos_mutableblockpos.move(Direction.UP);
                        level.setBlock(blockpos_mutableblockpos, columnState, 2);
                    }

                }
            }
        }

        protected void fillPillarDownOrChainUp(WorldGenLevel level, BlockState pillarState, int x, int y, int z, BoundingBox chunkBB) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = this.getWorldPos(x, y, z);

            if (chunkBB.isInside(blockpos_mutableblockpos)) {
                int l = blockpos_mutableblockpos.getY();
                int i1 = 1;
                boolean flag = true;

                for (boolean flag1 = true; flag || flag1; ++i1) {
                    if (flag) {
                        blockpos_mutableblockpos.setY(l - i1);
                        BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos);
                        boolean flag2 = this.isReplaceableByStructures(blockstate1) && !blockstate1.is(Blocks.LAVA);

                        if (!flag2 && this.canPlaceColumnOnTopOf(level, blockpos_mutableblockpos, blockstate1)) {
                            fillColumnBetween(level, pillarState, blockpos_mutableblockpos, l - i1 + 1, l);
                            return;
                        }

                        flag = i1 <= 20 && flag2 && blockpos_mutableblockpos.getY() > level.getMinY() + 1;
                    }

                    if (flag1) {
                        blockpos_mutableblockpos.setY(l + i1);
                        BlockState blockstate2 = level.getBlockState(blockpos_mutableblockpos);
                        boolean flag3 = this.isReplaceableByStructures(blockstate2);

                        if (!flag3 && this.canHangChainBelow(level, blockpos_mutableblockpos, blockstate2)) {
                            level.setBlock(blockpos_mutableblockpos.setY(l + 1), this.type.getFenceState(), 2);
                            fillColumnBetween(level, Blocks.IRON_CHAIN.defaultBlockState(), blockpos_mutableblockpos, l + 2, l + i1);
                            return;
                        }

                        flag1 = i1 <= 50 && flag3 && blockpos_mutableblockpos.getY() < level.getMaxY();
                    }
                }

            }
        }

        private static void fillColumnBetween(WorldGenLevel level, BlockState pillarState, BlockPos.MutableBlockPos pos, int bottomInclusive, int topExclusive) {
            for (int k = bottomInclusive; k < topExclusive; ++k) {
                level.setBlock(pos.setY(k), pillarState, 2);
            }

        }

        private boolean canPlaceColumnOnTopOf(LevelReader level, BlockPos posBelow, BlockState stateBelow) {
            return stateBelow.isFaceSturdy(level, posBelow, Direction.UP);
        }

        private boolean canHangChainBelow(LevelReader level, BlockPos posAbove, BlockState stateAbove) {
            return Block.canSupportCenter(level, posAbove, Direction.DOWN) && !(stateAbove.getBlock() instanceof FallingBlock);
        }

        private void placeSupport(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z, int y1, int x1, RandomSource random) {
            if (this.isSupportingBox(level, chunkBB, x0, x1, y1, z)) {
                BlockState blockstate = this.type.getPlanksState();
                BlockState blockstate1 = this.type.getFenceState();

                this.generateBox(level, chunkBB, x0, y0, z, x0, y1 - 1, z, (BlockState) blockstate1.setValue(FenceBlock.WEST, true), MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                this.generateBox(level, chunkBB, x1, y0, z, x1, y1 - 1, z, (BlockState) blockstate1.setValue(FenceBlock.EAST, true), MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                if (random.nextInt(4) == 0) {
                    this.generateBox(level, chunkBB, x0, y1, z, x0, y1, z, blockstate, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                    this.generateBox(level, chunkBB, x1, y1, z, x1, y1, z, blockstate, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                } else {
                    this.generateBox(level, chunkBB, x0, y1, z, x1, y1, z, blockstate, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                    this.maybeGenerateBlock(level, chunkBB, random, 0.05F, x0 + 1, y1, z - 1, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
                    this.maybeGenerateBlock(level, chunkBB, random, 0.05F, x0 + 1, y1, z + 1, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
                }

            }
        }

        private void maybePlaceCobWeb(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, float probability, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, chunkBB) && random.nextFloat() < probability && this.hasSturdyNeighbours(level, chunkBB, x, y, z, 2)) {
                this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), x, y, z, chunkBB);
            }

        }

        private boolean hasSturdyNeighbours(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, int count) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = this.getWorldPos(x, y, z);
            int i1 = 0;

            for (Direction direction : Direction.values()) {
                blockpos_mutableblockpos.move(direction);
                if (chunkBB.isInside(blockpos_mutableblockpos) && level.getBlockState(blockpos_mutableblockpos).isFaceSturdy(level, blockpos_mutableblockpos, direction.getOpposite())) {
                    ++i1;
                    if (i1 >= count) {
                        return true;
                    }
                }

                blockpos_mutableblockpos.move(direction.getOpposite());
            }

            return false;
        }
    }

    public static class MineShaftCrossing extends MineshaftPieces.MineShaftPiece {

        private final Direction direction;
        private final boolean isTwoFloored;

        public MineShaftCrossing(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, tag);
            this.isTwoFloored = tag.getBooleanOr("tf", false);
            this.direction = (Direction) tag.read("D", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("tf", this.isTwoFloored);
            tag.store("D", Direction.LEGACY_ID_CODEC_2D, this.direction);
        }

        public MineShaftCrossing(int genDepth, BoundingBox boundingBox, @Nullable Direction direction, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, genDepth, type, boundingBox);
            this.direction = direction;
            this.isTwoFloored = boundingBox.getYSpan() > 3;
        }

        public static @Nullable BoundingBox findCrossing(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction) {
            int l;

            if (random.nextInt(4) == 0) {
                l = 6;
            } else {
                l = 2;
            }

            BoundingBox boundingbox;

            switch (direction) {
                case NORTH:
                default:
                    boundingbox = new BoundingBox(-1, 0, -4, 3, l, 0);
                    break;
                case SOUTH:
                    boundingbox = new BoundingBox(-1, 0, 0, 3, l, 4);
                    break;
                case WEST:
                    boundingbox = new BoundingBox(-4, 0, -1, 0, l, 3);
                    break;
                case EAST:
                    boundingbox = new BoundingBox(0, 0, -1, 4, l, 3);
            }

            boundingbox.move(footX, footY, footZ);
            return structurePieceAccessor.findCollisionPiece(boundingbox) != null ? null : boundingbox;
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            int i = this.getGenDepth();

            switch (this.direction) {
                case NORTH:
                default:
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i);
                    break;
                case SOUTH:
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i);
                    break;
                case WEST:
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i);
                    break;
                case EAST:
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i);
            }

            if (this.isTwoFloored) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() - 1, Direction.NORTH, i);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1, Direction.WEST, i);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1, Direction.EAST, i);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (!this.isInInvalidLocation(level, chunkBB)) {
                BlockState blockstate = this.type.getPlanksState();

                if (this.isTwoFloored) {
                    this.generateBox(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), this.boundingBox.maxX() - 1, this.boundingBox.minY() + 3 - 1, this.boundingBox.maxZ(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(level, chunkBB, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxX(), this.boundingBox.minY() + 3 - 1, this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.maxY() - 2, this.boundingBox.minZ(), this.boundingBox.maxX() - 1, this.boundingBox.maxY(), this.boundingBox.maxZ(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(level, chunkBB, this.boundingBox.minX(), this.boundingBox.maxY() - 2, this.boundingBox.minZ() + 1, this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3, this.boundingBox.minZ() + 1, this.boundingBox.maxX() - 1, this.boundingBox.minY() + 3, this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                } else {
                    this.generateBox(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), this.boundingBox.maxX() - 1, this.boundingBox.maxY(), this.boundingBox.maxZ(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(level, chunkBB, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                }

                this.placeSupportPillar(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, chunkBB, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, chunkBB, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                int i = this.boundingBox.minY() - 1;

                for (int j = this.boundingBox.minX(); j <= this.boundingBox.maxX(); ++j) {
                    for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); ++k) {
                        this.setPlanksBlock(level, chunkBB, blockstate, j, i, k);
                    }
                }

            }
        }

        private void placeSupportPillar(WorldGenLevel level, BoundingBox chunkBB, int x, int y0, int z, int y1) {
            if (!this.getBlock(level, x, y1 + 1, z, chunkBB).isAir()) {
                this.generateBox(level, chunkBB, x, y0, z, x, y1, z, this.type.getPlanksState(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
            }

        }
    }

    public static class MineShaftStairs extends MineshaftPieces.MineShaftPiece {

        public MineShaftStairs(int genDepth, BoundingBox boundingBox, Direction direction, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, genDepth, type, boundingBox);
            this.setOrientation(direction);
        }

        public MineShaftStairs(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, tag);
        }

        public static @Nullable BoundingBox findStairs(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction) {
            BoundingBox boundingbox;

            switch (direction) {
                case NORTH:
                default:
                    boundingbox = new BoundingBox(0, -5, -8, 2, 2, 0);
                    break;
                case SOUTH:
                    boundingbox = new BoundingBox(0, -5, 0, 2, 2, 8);
                    break;
                case WEST:
                    boundingbox = new BoundingBox(-8, -5, 0, 0, 2, 2);
                    break;
                case EAST:
                    boundingbox = new BoundingBox(0, -5, 0, 8, 2, 2);
            }

            boundingbox.move(footX, footY, footZ);
            return structurePieceAccessor.findCollisionPiece(boundingbox) != null ? null : boundingbox;
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            int i = this.getGenDepth();
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                    default:
                        MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                        break;
                    case SOUTH:
                        MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                        break;
                    case WEST:
                        MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.WEST, i);
                        break;
                    case EAST:
                        MineshaftPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.EAST, i);
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (!this.isInInvalidLocation(level, chunkBB)) {
                this.generateBox(level, chunkBB, 0, 5, 0, 2, 7, 1, MineshaftPieces.MineShaftStairs.CAVE_AIR, MineshaftPieces.MineShaftStairs.CAVE_AIR, false);
                this.generateBox(level, chunkBB, 0, 0, 7, 2, 2, 8, MineshaftPieces.MineShaftStairs.CAVE_AIR, MineshaftPieces.MineShaftStairs.CAVE_AIR, false);

                for (int i = 0; i < 5; ++i) {
                    this.generateBox(level, chunkBB, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, MineshaftPieces.MineShaftStairs.CAVE_AIR, MineshaftPieces.MineShaftStairs.CAVE_AIR, false);
                }

            }
        }
    }
}
