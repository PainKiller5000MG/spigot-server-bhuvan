package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class StrongholdPieces {

    private static final int SMALL_DOOR_WIDTH = 3;
    private static final int SMALL_DOOR_HEIGHT = 3;
    private static final int MAX_DEPTH = 50;
    private static final int LOWEST_Y_POSITION = 10;
    private static final boolean CHECK_AIR = true;
    public static final int MAGIC_START_Y = 64;
    private static final StrongholdPieces.PieceWeight[] STRONGHOLD_PIECE_WEIGHTS = new StrongholdPieces.PieceWeight[]{new StrongholdPieces.PieceWeight(StrongholdPieces.Straight.class, 40, 0), new StrongholdPieces.PieceWeight(StrongholdPieces.PrisonHall.class, 5, 5), new StrongholdPieces.PieceWeight(StrongholdPieces.LeftTurn.class, 20, 0), new StrongholdPieces.PieceWeight(StrongholdPieces.RightTurn.class, 20, 0), new StrongholdPieces.PieceWeight(StrongholdPieces.RoomCrossing.class, 10, 6), new StrongholdPieces.PieceWeight(StrongholdPieces.StraightStairsDown.class, 5, 5), new StrongholdPieces.PieceWeight(StrongholdPieces.StairsDown.class, 5, 5), new StrongholdPieces.PieceWeight(StrongholdPieces.FiveCrossing.class, 5, 4), new StrongholdPieces.PieceWeight(StrongholdPieces.ChestCorridor.class, 5, 4), new StrongholdPieces.PieceWeight(StrongholdPieces.Library.class, 10, 2) {
                @Override
                public boolean doPlace(int depth) {
                    return super.doPlace(depth) && depth > 4;
                }
            }, new StrongholdPieces.PieceWeight(StrongholdPieces.PortalRoom.class, 20, 1) {
                @Override
                public boolean doPlace(int depth) {
                    return super.doPlace(depth) && depth > 5;
                }
            }};
    private static List<StrongholdPieces.PieceWeight> currentPieces;
    private static @Nullable Class<? extends StrongholdPieces.StrongholdPiece> imposedPiece;
    private static int totalWeight;
    private static final StrongholdPieces.SmoothStoneSelector SMOOTH_STONE_SELECTOR = new StrongholdPieces.SmoothStoneSelector();

    public StrongholdPieces() {}

    public static void resetPieces() {
        StrongholdPieces.currentPieces = Lists.newArrayList();

        for (StrongholdPieces.PieceWeight strongholdpieces_pieceweight : StrongholdPieces.STRONGHOLD_PIECE_WEIGHTS) {
            strongholdpieces_pieceweight.placeCount = 0;
            StrongholdPieces.currentPieces.add(strongholdpieces_pieceweight);
        }

        StrongholdPieces.imposedPiece = null;
    }

    private static boolean updatePieceWeight() {
        boolean flag = false;

        StrongholdPieces.totalWeight = 0;

        for (StrongholdPieces.PieceWeight strongholdpieces_pieceweight : StrongholdPieces.currentPieces) {
            if (strongholdpieces_pieceweight.maxPlaceCount > 0 && strongholdpieces_pieceweight.placeCount < strongholdpieces_pieceweight.maxPlaceCount) {
                flag = true;
            }

            StrongholdPieces.totalWeight += strongholdpieces_pieceweight.weight;
        }

        return flag;
    }

    private static StrongholdPieces.@Nullable StrongholdPiece findAndCreatePieceFactory(Class<? extends StrongholdPieces.StrongholdPiece> pieceClass, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
        StrongholdPieces.StrongholdPiece strongholdpieces_strongholdpiece = null;

        if (pieceClass == StrongholdPieces.Straight.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.Straight.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.PrisonHall.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.PrisonHall.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.LeftTurn.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.LeftTurn.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.RightTurn.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.RightTurn.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.RoomCrossing.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.RoomCrossing.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.StraightStairsDown.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.StraightStairsDown.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.StairsDown.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.StairsDown.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.FiveCrossing.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.FiveCrossing.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.ChestCorridor.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.ChestCorridor.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.Library.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.Library.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (pieceClass == StrongholdPieces.PortalRoom.class) {
            strongholdpieces_strongholdpiece = StrongholdPieces.PortalRoom.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        }

        return strongholdpieces_strongholdpiece;
    }

    private static StrongholdPieces.@Nullable StrongholdPiece generatePieceFromSmallDoor(StrongholdPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
        if (!updatePieceWeight()) {
            return null;
        } else {
            if (StrongholdPieces.imposedPiece != null) {
                StrongholdPieces.StrongholdPiece strongholdpieces_strongholdpiece = findAndCreatePieceFactory(StrongholdPieces.imposedPiece, structurePieceAccessor, random, footX, footY, footZ, direction, depth);

                StrongholdPieces.imposedPiece = null;
                if (strongholdpieces_strongholdpiece != null) {
                    return strongholdpieces_strongholdpiece;
                }
            }

            int i1 = 0;

            while (i1 < 5) {
                ++i1;
                int j1 = random.nextInt(StrongholdPieces.totalWeight);

                for (StrongholdPieces.PieceWeight strongholdpieces_pieceweight : StrongholdPieces.currentPieces) {
                    j1 -= strongholdpieces_pieceweight.weight;
                    if (j1 < 0) {
                        if (!strongholdpieces_pieceweight.doPlace(depth) || strongholdpieces_pieceweight == startPiece.previousPiece) {
                            break;
                        }

                        StrongholdPieces.StrongholdPiece strongholdpieces_strongholdpiece1 = findAndCreatePieceFactory(strongholdpieces_pieceweight.pieceClass, structurePieceAccessor, random, footX, footY, footZ, direction, depth);

                        if (strongholdpieces_strongholdpiece1 != null) {
                            ++strongholdpieces_pieceweight.placeCount;
                            startPiece.previousPiece = strongholdpieces_pieceweight;
                            if (!strongholdpieces_pieceweight.isValid()) {
                                StrongholdPieces.currentPieces.remove(strongholdpieces_pieceweight);
                            }

                            return strongholdpieces_strongholdpiece1;
                        }
                    }
                }
            }

            BoundingBox boundingbox = StrongholdPieces.FillerCorridor.findPieceBox(structurePieceAccessor, random, footX, footY, footZ, direction);

            if (boundingbox != null && boundingbox.minY() > 1) {
                return new StrongholdPieces.FillerCorridor(depth, boundingbox, direction);
            } else {
                return null;
            }
        }
    }

    private static @Nullable StructurePiece generateAndAddPiece(StrongholdPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
        if (depth > 50) {
            return null;
        } else if (Math.abs(footX - startPiece.getBoundingBox().minX()) <= 112 && Math.abs(footZ - startPiece.getBoundingBox().minZ()) <= 112) {
            StructurePiece structurepiece = generatePieceFromSmallDoor(startPiece, structurePieceAccessor, random, footX, footY, footZ, direction, depth + 1);

            if (structurepiece != null) {
                structurePieceAccessor.addPiece(structurepiece);
                startPiece.pendingChildren.add(structurepiece);
            }

            return structurepiece;
        } else {
            return null;
        }
    }

    private static class PieceWeight {

        public final Class<? extends StrongholdPieces.StrongholdPiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;

        public PieceWeight(Class<? extends StrongholdPieces.StrongholdPiece> pieceClass, int weight, int maxPlaceCount) {
            this.pieceClass = pieceClass;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
        }

        public boolean doPlace(int depth) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    private abstract static class StrongholdPiece extends StructurePiece {

        protected StrongholdPieces.StrongholdPiece.SmallDoorType entryDoor;

        protected StrongholdPiece(StructurePieceType type, int genDepth, BoundingBox boundingBox) {
            super(type, genDepth, boundingBox);
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
        }

        public StrongholdPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
            this.entryDoor = (StrongholdPieces.StrongholdPiece.SmallDoorType) tag.read("EntryDoor", StrongholdPieces.StrongholdPiece.SmallDoorType.LEGACY_CODEC).orElseThrow();
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.store("EntryDoor", StrongholdPieces.StrongholdPiece.SmallDoorType.LEGACY_CODEC, this.entryDoor);
        }

        protected void generateSmallDoor(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, StrongholdPieces.StrongholdPiece.SmallDoorType doorType, int footX, int footY, int footZ) {
            switch (doorType.ordinal()) {
                case 0:
                    this.generateBox(level, chunkBB, footX, footY, footZ, footX + 3 - 1, footY + 3 - 1, footZ, StrongholdPieces.StrongholdPiece.CAVE_AIR, StrongholdPieces.StrongholdPiece.CAVE_AIR, false);
                    break;
                case 1:
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX, footY, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 1, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 2, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 2, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 2, footY, footZ, chunkBB);
                    this.placeBlock(level, Blocks.OAK_DOOR.defaultBlockState(), footX + 1, footY, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), footX + 1, footY + 1, footZ, chunkBB);
                    break;
                case 2:
                    this.placeBlock(level, Blocks.CAVE_AIR.defaultBlockState(), footX + 1, footY, footZ, chunkBB);
                    this.placeBlock(level, Blocks.CAVE_AIR.defaultBlockState(), footX + 1, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true), footX, footY, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true), footX, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true), footX, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true), footX + 1, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true), footX + 2, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true), footX + 2, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true), footX + 2, footY, footZ, chunkBB);
                    break;
                case 3:
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX, footY, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 1, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 2, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 2, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), footX + 2, footY, footZ, chunkBB);
                    this.placeBlock(level, Blocks.IRON_DOOR.defaultBlockState(), footX + 1, footY, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), footX + 1, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH), footX + 2, footY + 1, footZ + 1, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH), footX + 2, footY + 1, footZ - 1, chunkBB);
            }

        }

        protected StrongholdPieces.StrongholdPiece.SmallDoorType randomSmallDoor(RandomSource random) {
            int i = random.nextInt(5);

            switch (i) {
                case 0:
                case 1:
                default:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
                case 2:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.WOOD_DOOR;
                case 3:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.GRATES;
                case 4:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.IRON_DOOR;
            }
        }

        protected @Nullable StructurePiece generateSmallDoorChildForward(StrongholdPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int xOff, int yOff) {
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1, direction, this.getGenDepth());
                    case SOUTH:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1, direction, this.getGenDepth());
                    case WEST:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff, direction, this.getGenDepth());
                    case EAST:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff, direction, this.getGenDepth());
                }
            }

            return null;
        }

        protected @Nullable StructurePiece generateSmallDoorChildLeft(StrongholdPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int yOff, int zOff) {
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.WEST, this.getGenDepth());
                    case SOUTH:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.WEST, this.getGenDepth());
                    case WEST:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth());
                    case EAST:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth());
                }
            }

            return null;
        }

        protected @Nullable StructurePiece generateSmallDoorChildRight(StrongholdPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int yOff, int zOff) {
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.EAST, this.getGenDepth());
                    case SOUTH:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.EAST, this.getGenDepth());
                    case WEST:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth());
                    case EAST:
                        return StrongholdPieces.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth());
                }
            }

            return null;
        }

        protected static boolean isOkBox(BoundingBox box) {
            return box.minY() > 10;
        }

        protected static enum SmallDoorType {

            OPENING, WOOD_DOOR, GRATES, IRON_DOOR;

            /** @deprecated */
            @Deprecated
            public static final Codec<StrongholdPieces.StrongholdPiece.SmallDoorType> LEGACY_CODEC = ExtraCodecs.<StrongholdPieces.StrongholdPiece.SmallDoorType>legacyEnum(StrongholdPieces.StrongholdPiece.SmallDoorType::valueOf);

            private SmallDoorType() {}
        }
    }

    public static class FillerCorridor extends StrongholdPieces.StrongholdPiece {

        private final int steps;

        public FillerCorridor(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_FILLER_CORRIDOR, genDepth, boundingBox);
            this.setOrientation(direction);
            this.steps = direction != Direction.NORTH && direction != Direction.SOUTH ? boundingBox.getXSpan() : boundingBox.getZSpan();
        }

        public FillerCorridor(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_FILLER_CORRIDOR, tag);
            this.steps = tag.getIntOr("Steps", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putInt("Steps", this.steps);
        }

        public static @Nullable BoundingBox findPieceBox(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction) {
            int l = 3;
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 4, direction);
            StructurePiece structurepiece = structurePieceAccessor.findCollisionPiece(boundingbox);

            if (structurepiece == null) {
                return null;
            } else {
                if (structurepiece.getBoundingBox().minY() == boundingbox.minY()) {
                    for (int i1 = 2; i1 >= 1; --i1) {
                        boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, i1, direction);
                        if (!structurepiece.getBoundingBox().intersects(boundingbox)) {
                            return BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, i1 + 1, direction);
                        }
                    }
                }

                return null;
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            for (int i = 0; i < this.steps; ++i) {
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 0, 0, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 0, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 2, 0, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 0, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 4, 0, i, chunkBB);

                for (int j = 1; j <= 3; ++j) {
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 0, j, i, chunkBB);
                    this.placeBlock(level, Blocks.CAVE_AIR.defaultBlockState(), 1, j, i, chunkBB);
                    this.placeBlock(level, Blocks.CAVE_AIR.defaultBlockState(), 2, j, i, chunkBB);
                    this.placeBlock(level, Blocks.CAVE_AIR.defaultBlockState(), 3, j, i, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 4, j, i, chunkBB);
                }

                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 0, 4, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 4, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 2, 4, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 4, i, chunkBB);
                this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 4, 4, i, chunkBB);
            }

        }
    }

    public static class StairsDown extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 5;
        private final boolean isSource;

        public StairsDown(StructurePieceType type, int genDepth, int west, int north, Direction direction) {
            super(type, genDepth, makeBoundingBox(west, 64, north, direction, 5, 11, 5));
            this.isSource = true;
            this.setOrientation(direction);
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
        }

        public StairsDown(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_STAIRS_DOWN, genDepth, boundingBox);
            this.isSource = false;
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public StairsDown(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.isSource = tag.getBooleanOr("Source", false);
        }

        public StairsDown(CompoundTag tag) {
            this(StructurePieceType.STRONGHOLD_STAIRS_DOWN, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Source", this.isSource);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            if (this.isSource) {
                StrongholdPieces.imposedPiece = StrongholdPieces.FiveCrossing.class;
            }

            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
        }

        public static StrongholdPieces.@Nullable StairsDown createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -7, 0, 5, 11, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.StairsDown(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 10, 4, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 7, 0);
            this.generateSmallDoor(level, random, chunkBB, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 4);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 2, 6, 1, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 5, 1, chunkBB);
            this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 6, 1, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 5, 2, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 4, 3, chunkBB);
            this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 5, 3, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 2, 4, 3, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 3, 3, chunkBB);
            this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3, 4, 3, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 3, 2, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 2, 1, chunkBB);
            this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3, 3, 1, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 2, 2, 1, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 1, 1, chunkBB);
            this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 2, 1, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 1, 2, chunkBB);
            this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 1, 3, chunkBB);
        }
    }

    public static class StartPiece extends StrongholdPieces.StairsDown {

        public StrongholdPieces.@Nullable PieceWeight previousPiece;
        public StrongholdPieces.@Nullable PortalRoom portalRoomPiece;
        public final List<StructurePiece> pendingChildren = Lists.newArrayList();

        public StartPiece(RandomSource random, int west, int north) {
            super(StructurePieceType.STRONGHOLD_START, 0, west, north, getRandomHorizontalDirection(random));
        }

        public StartPiece(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_START, tag);
        }

        @Override
        public BlockPos getLocatorPosition() {
            return this.portalRoomPiece != null ? this.portalRoomPiece.getLocatorPosition() : super.getLocatorPosition();
        }
    }

    public static class Straight extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 5;
        private static final int DEPTH = 7;
        private final boolean leftChild;
        private final boolean rightChild;

        public Straight(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.leftChild = random.nextInt(2) == 0;
            this.rightChild = random.nextInt(2) == 0;
        }

        public Straight(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT, tag);
            this.leftChild = tag.getBooleanOr("Left", false);
            this.rightChild = tag.getBooleanOr("Right", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Left", this.leftChild);
            tag.putBoolean("Right", this.rightChild);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
            if (this.leftChild) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 2);
            }

            if (this.rightChild) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 2);
            }

        }

        public static StrongholdPieces.@Nullable Straight createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 7, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.Straight(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 6, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            this.generateSmallDoor(level, random, chunkBB, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 6);
            BlockState blockstate = (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST);
            BlockState blockstate1 = (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST);

            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 1, 2, 1, blockstate);
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 3, 2, 1, blockstate1);
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 1, 2, 5, blockstate);
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 3, 2, 5, blockstate1);
            if (this.leftChild) {
                this.generateBox(level, chunkBB, 0, 1, 2, 0, 3, 4, StrongholdPieces.Straight.CAVE_AIR, StrongholdPieces.Straight.CAVE_AIR, false);
            }

            if (this.rightChild) {
                this.generateBox(level, chunkBB, 4, 1, 2, 4, 3, 4, StrongholdPieces.Straight.CAVE_AIR, StrongholdPieces.Straight.CAVE_AIR, false);
            }

        }
    }

    public static class ChestCorridor extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 5;
        private static final int DEPTH = 7;
        private boolean hasPlacedChest;

        public ChestCorridor(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_CHEST_CORRIDOR, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public ChestCorridor(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_CHEST_CORRIDOR, tag);
            this.hasPlacedChest = tag.getBooleanOr("Chest", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Chest", this.hasPlacedChest);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
        }

        public static StrongholdPieces.@Nullable ChestCorridor createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 7, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.ChestCorridor(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 6, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            this.generateSmallDoor(level, random, chunkBB, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 6);
            this.generateBox(level, chunkBB, 3, 1, 2, 3, 1, 4, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), false);
            this.placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 1, 1, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 1, 5, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 2, 2, chunkBB);
            this.placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 2, 4, chunkBB);

            for (int i = 2; i <= 4; ++i) {
                this.placeBlock(level, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2, 1, i, chunkBB);
            }

            if (!this.hasPlacedChest && chunkBB.isInside(this.getWorldPos(3, 2, 3))) {
                this.hasPlacedChest = true;
                this.createChest(level, chunkBB, random, 3, 2, 3, BuiltInLootTables.STRONGHOLD_CORRIDOR);
            }

        }
    }

    public static class StraightStairsDown extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 8;

        public StraightStairsDown(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT_STAIRS_DOWN, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public StraightStairsDown(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT_STAIRS_DOWN, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
        }

        public static StrongholdPieces.@Nullable StraightStairsDown createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -7, 0, 5, 11, 8, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.StraightStairsDown(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 10, 7, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 7, 0);
            this.generateSmallDoor(level, random, chunkBB, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 7);
            BlockState blockstate = (BlockState) Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);

            for (int i = 0; i < 6; ++i) {
                this.placeBlock(level, blockstate, 1, 6 - i, 1 + i, chunkBB);
                this.placeBlock(level, blockstate, 2, 6 - i, 1 + i, chunkBB);
                this.placeBlock(level, blockstate, 3, 6 - i, 1 + i, chunkBB);
                if (i < 5) {
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 1, 5 - i, 1 + i, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 2, 5 - i, 1 + i, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 5 - i, 1 + i, chunkBB);
                }
            }

        }
    }

    public abstract static class Turn extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 5;
        protected static final int HEIGHT = 5;
        protected static final int DEPTH = 5;

        protected Turn(StructurePieceType type, int genDepth, BoundingBox boundingBox) {
            super(type, genDepth, boundingBox);
        }

        public Turn(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }
    }

    public static class LeftTurn extends StrongholdPieces.Turn {

        public LeftTurn(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_LEFT_TURN, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public LeftTurn(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_LEFT_TURN, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            Direction direction = this.getOrientation();

            if (direction != Direction.NORTH && direction != Direction.EAST) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
            } else {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
            }

        }

        public static StrongholdPieces.@Nullable LeftTurn createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.LeftTurn(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 4, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            Direction direction = this.getOrientation();

            if (direction != Direction.NORTH && direction != Direction.EAST) {
                this.generateBox(level, chunkBB, 4, 1, 1, 4, 3, 3, StrongholdPieces.LeftTurn.CAVE_AIR, StrongholdPieces.LeftTurn.CAVE_AIR, false);
            } else {
                this.generateBox(level, chunkBB, 0, 1, 1, 0, 3, 3, StrongholdPieces.LeftTurn.CAVE_AIR, StrongholdPieces.LeftTurn.CAVE_AIR, false);
            }

        }
    }

    public static class RightTurn extends StrongholdPieces.Turn {

        public RightTurn(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_RIGHT_TURN, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public RightTurn(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_RIGHT_TURN, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            Direction direction = this.getOrientation();

            if (direction != Direction.NORTH && direction != Direction.EAST) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
            } else {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
            }

        }

        public static StrongholdPieces.@Nullable RightTurn createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.RightTurn(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 4, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            Direction direction = this.getOrientation();

            if (direction != Direction.NORTH && direction != Direction.EAST) {
                this.generateBox(level, chunkBB, 0, 1, 1, 0, 3, 3, StrongholdPieces.RightTurn.CAVE_AIR, StrongholdPieces.RightTurn.CAVE_AIR, false);
            } else {
                this.generateBox(level, chunkBB, 4, 1, 1, 4, 3, 3, StrongholdPieces.RightTurn.CAVE_AIR, StrongholdPieces.RightTurn.CAVE_AIR, false);
            }

        }
    }

    public static class RoomCrossing extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 11;
        protected static final int HEIGHT = 7;
        protected static final int DEPTH = 11;
        protected final int type;

        public RoomCrossing(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_ROOM_CROSSING, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.type = random.nextInt(5);
        }

        public RoomCrossing(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_ROOM_CROSSING, tag);
            this.type = tag.getIntOr("Type", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putInt("Type", this.type);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 4, 1);
            this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 4);
            this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 4);
        }

        public static StrongholdPieces.@Nullable RoomCrossing createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -4, -1, 0, 11, 7, 11, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.RoomCrossing(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 10, 6, 10, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 4, 1, 0);
            this.generateBox(level, chunkBB, 4, 1, 10, 6, 3, 10, StrongholdPieces.RoomCrossing.CAVE_AIR, StrongholdPieces.RoomCrossing.CAVE_AIR, false);
            this.generateBox(level, chunkBB, 0, 1, 4, 0, 3, 6, StrongholdPieces.RoomCrossing.CAVE_AIR, StrongholdPieces.RoomCrossing.CAVE_AIR, false);
            this.generateBox(level, chunkBB, 10, 1, 4, 10, 3, 6, StrongholdPieces.RoomCrossing.CAVE_AIR, StrongholdPieces.RoomCrossing.CAVE_AIR, false);
            switch (this.type) {
                case 0:
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 5, 1, 5, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 5, 2, 5, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 5, 3, 5, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST), 4, 3, 5, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), 6, 3, 5, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 5, 3, 4, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH), 5, 3, 6, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 4, 1, 4, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 4, 1, 5, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 4, 1, 6, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 6, 1, 4, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 6, 1, 5, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 6, 1, 6, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 5, 1, 4, chunkBB);
                    this.placeBlock(level, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 5, 1, 6, chunkBB);
                    break;
                case 1:
                    for (int i = 0; i < 5; ++i) {
                        this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3, 1, 3 + i, chunkBB);
                        this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 7, 1, 3 + i, chunkBB);
                        this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3 + i, 1, 3, chunkBB);
                        this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 3 + i, 1, 7, chunkBB);
                    }

                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 5, 1, 5, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 5, 2, 5, chunkBB);
                    this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(), 5, 3, 5, chunkBB);
                    this.placeBlock(level, Blocks.WATER.defaultBlockState(), 5, 4, 5, chunkBB);
                    break;
                case 2:
                    for (int j = 1; j <= 9; ++j) {
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 1, 3, j, chunkBB);
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 9, 3, j, chunkBB);
                    }

                    for (int k = 1; k <= 9; ++k) {
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), k, 3, 1, chunkBB);
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), k, 3, 9, chunkBB);
                    }

                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 5, 1, 4, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 5, 1, 6, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 5, 3, 4, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 5, 3, 6, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 4, 1, 5, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 6, 1, 5, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 4, 3, 5, chunkBB);
                    this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 6, 3, 5, chunkBB);

                    for (int l = 1; l <= 3; ++l) {
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 4, l, 4, chunkBB);
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 6, l, 4, chunkBB);
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 4, l, 6, chunkBB);
                        this.placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), 6, l, 6, chunkBB);
                    }

                    this.placeBlock(level, Blocks.WALL_TORCH.defaultBlockState(), 5, 3, 5, chunkBB);

                    for (int i1 = 2; i1 <= 8; ++i1) {
                        this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 2, 3, i1, chunkBB);
                        this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 3, 3, i1, chunkBB);
                        if (i1 <= 3 || i1 >= 7) {
                            this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 4, 3, i1, chunkBB);
                            this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 5, 3, i1, chunkBB);
                            this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 6, 3, i1, chunkBB);
                        }

                        this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 7, 3, i1, chunkBB);
                        this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 8, 3, i1, chunkBB);
                    }

                    BlockState blockstate = (BlockState) Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);

                    this.placeBlock(level, blockstate, 9, 1, 3, chunkBB);
                    this.placeBlock(level, blockstate, 9, 2, 3, chunkBB);
                    this.placeBlock(level, blockstate, 9, 3, 3, chunkBB);
                    this.createChest(level, chunkBB, random, 3, 4, 8, BuiltInLootTables.STRONGHOLD_CROSSING);
            }

        }
    }

    public static class PrisonHall extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 9;
        protected static final int HEIGHT = 5;
        protected static final int DEPTH = 11;

        public PrisonHall(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_PRISON_HALL, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public PrisonHall(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_PRISON_HALL, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 1);
        }

        public static StrongholdPieces.@Nullable PrisonHall createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -1, 0, 9, 5, 11, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.PrisonHall(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 8, 4, 10, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            this.generateBox(level, chunkBB, 1, 1, 10, 3, 3, 10, StrongholdPieces.PrisonHall.CAVE_AIR, StrongholdPieces.PrisonHall.CAVE_AIR, false);
            this.generateBox(level, chunkBB, 4, 1, 1, 4, 3, 1, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 3, 4, 3, 3, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 7, 4, 3, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 9, 4, 3, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);

            for (int i = 1; i <= 3; ++i) {
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, i, 4, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true)).setValue(IronBarsBlock.EAST, true), 4, i, 5, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, i, 6, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true), 5, i, 5, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true), 6, i, 5, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true), 7, i, 5, chunkBB);
            }

            this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, 3, 2, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, 3, 8, chunkBB);
            BlockState blockstate = (BlockState) Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST)).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

            this.placeBlock(level, blockstate, 4, 1, 2, chunkBB);
            this.placeBlock(level, blockstate1, 4, 2, 2, chunkBB);
            this.placeBlock(level, blockstate, 4, 1, 8, chunkBB);
            this.placeBlock(level, blockstate1, 4, 2, 8, chunkBB);
        }
    }

    public static class Library extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 14;
        protected static final int HEIGHT = 6;
        protected static final int TALL_HEIGHT = 11;
        protected static final int DEPTH = 15;
        private final boolean isTall;

        public Library(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_LIBRARY, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.isTall = boundingBox.getYSpan() > 6;
        }

        public Library(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_LIBRARY, tag);
            this.isTall = tag.getBooleanOr("Tall", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Tall", this.isTall);
        }

        public static StrongholdPieces.@Nullable Library createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -4, -1, 0, 14, 11, 15, direction);

            if (!isOkBox(boundingbox) || structurePieceAccessor.findCollisionPiece(boundingbox) != null) {
                boundingbox = BoundingBox.orientBox(footX, footY, footZ, -4, -1, 0, 14, 6, 15, direction);
                if (!isOkBox(boundingbox) || structurePieceAccessor.findCollisionPiece(boundingbox) != null) {
                    return null;
                }
            }

            return new StrongholdPieces.Library(genDepth, random, boundingbox, direction);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            int i = 11;

            if (!this.isTall) {
                i = 6;
            }

            this.generateBox(level, chunkBB, 0, 0, 0, 13, i - 1, 14, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 4, 1, 0);
            this.generateMaybeBox(level, chunkBB, random, 0.07F, 2, 1, 1, 11, 4, 13, Blocks.COBWEB.defaultBlockState(), Blocks.COBWEB.defaultBlockState(), false, false);
            int j = 1;
            int k = 12;

            for (int l = 1; l <= 13; ++l) {
                if ((l - 1) % 4 == 0) {
                    this.generateBox(level, chunkBB, 1, 1, l, 1, 4, l, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                    this.generateBox(level, chunkBB, 12, 1, l, 12, 4, l, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                    this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), 2, 3, l, chunkBB);
                    this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST), 11, 3, l, chunkBB);
                    if (this.isTall) {
                        this.generateBox(level, chunkBB, 1, 6, l, 1, 9, l, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                        this.generateBox(level, chunkBB, 12, 6, l, 12, 9, l, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                    }
                } else {
                    this.generateBox(level, chunkBB, 1, 1, l, 1, 4, l, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                    this.generateBox(level, chunkBB, 12, 1, l, 12, 4, l, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                    if (this.isTall) {
                        this.generateBox(level, chunkBB, 1, 6, l, 1, 9, l, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                        this.generateBox(level, chunkBB, 12, 6, l, 12, 9, l, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                    }
                }
            }

            for (int i1 = 3; i1 < 12; i1 += 2) {
                this.generateBox(level, chunkBB, 3, 1, i1, 4, 3, i1, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 6, 1, i1, 7, 3, i1, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 9, 1, i1, 10, 3, i1, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
            }

            if (this.isTall) {
                this.generateBox(level, chunkBB, 1, 5, 1, 3, 5, 13, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 10, 5, 1, 12, 5, 13, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 4, 5, 1, 9, 5, 2, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 4, 5, 12, 9, 5, 13, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 9, 5, 11, chunkBB);
                this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 8, 5, 11, chunkBB);
                this.placeBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), 9, 5, 10, chunkBB);
                BlockState blockstate = (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
                BlockState blockstate1 = (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

                this.generateBox(level, chunkBB, 3, 6, 3, 3, 6, 11, blockstate1, blockstate1, false);
                this.generateBox(level, chunkBB, 10, 6, 3, 10, 6, 9, blockstate1, blockstate1, false);
                this.generateBox(level, chunkBB, 4, 6, 2, 9, 6, 2, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 4, 6, 12, 7, 6, 12, blockstate, blockstate, false);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 3, 6, 2, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.EAST, true), 3, 6, 12, chunkBB);
                this.placeBlock(level, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.WEST, true), 10, 6, 2, chunkBB);

                for (int j1 = 0; j1 <= 2; ++j1) {
                    this.placeBlock(level, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.WEST, true), 8 + j1, 6, 12 - j1, chunkBB);
                    if (j1 != 2) {
                        this.placeBlock(level, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 8 + j1, 6, 11 - j1, chunkBB);
                    }
                }

                BlockState blockstate2 = (BlockState) Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);

                this.placeBlock(level, blockstate2, 10, 1, 13, chunkBB);
                this.placeBlock(level, blockstate2, 10, 2, 13, chunkBB);
                this.placeBlock(level, blockstate2, 10, 3, 13, chunkBB);
                this.placeBlock(level, blockstate2, 10, 4, 13, chunkBB);
                this.placeBlock(level, blockstate2, 10, 5, 13, chunkBB);
                this.placeBlock(level, blockstate2, 10, 6, 13, chunkBB);
                this.placeBlock(level, blockstate2, 10, 7, 13, chunkBB);
                int k1 = 7;
                int l1 = 7;
                BlockState blockstate3 = (BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true);

                this.placeBlock(level, blockstate3, 6, 9, 7, chunkBB);
                BlockState blockstate4 = (BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true);

                this.placeBlock(level, blockstate4, 7, 9, 7, chunkBB);
                this.placeBlock(level, blockstate3, 6, 8, 7, chunkBB);
                this.placeBlock(level, blockstate4, 7, 8, 7, chunkBB);
                BlockState blockstate5 = (BlockState) ((BlockState) blockstate1.setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);

                this.placeBlock(level, blockstate5, 6, 7, 7, chunkBB);
                this.placeBlock(level, blockstate5, 7, 7, 7, chunkBB);
                this.placeBlock(level, blockstate3, 5, 7, 7, chunkBB);
                this.placeBlock(level, blockstate4, 8, 7, 7, chunkBB);
                this.placeBlock(level, (BlockState) blockstate3.setValue(FenceBlock.NORTH, true), 6, 7, 6, chunkBB);
                this.placeBlock(level, (BlockState) blockstate3.setValue(FenceBlock.SOUTH, true), 6, 7, 8, chunkBB);
                this.placeBlock(level, (BlockState) blockstate4.setValue(FenceBlock.NORTH, true), 7, 7, 6, chunkBB);
                this.placeBlock(level, (BlockState) blockstate4.setValue(FenceBlock.SOUTH, true), 7, 7, 8, chunkBB);
                BlockState blockstate6 = Blocks.TORCH.defaultBlockState();

                this.placeBlock(level, blockstate6, 5, 8, 7, chunkBB);
                this.placeBlock(level, blockstate6, 8, 8, 7, chunkBB);
                this.placeBlock(level, blockstate6, 6, 8, 6, chunkBB);
                this.placeBlock(level, blockstate6, 6, 8, 8, chunkBB);
                this.placeBlock(level, blockstate6, 7, 8, 6, chunkBB);
                this.placeBlock(level, blockstate6, 7, 8, 8, chunkBB);
            }

            this.createChest(level, chunkBB, random, 3, 3, 5, BuiltInLootTables.STRONGHOLD_LIBRARY);
            if (this.isTall) {
                this.placeBlock(level, StrongholdPieces.Library.CAVE_AIR, 12, 9, 1, chunkBB);
                this.createChest(level, chunkBB, random, 12, 8, 1, BuiltInLootTables.STRONGHOLD_LIBRARY);
            }

        }
    }

    public static class FiveCrossing extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 10;
        protected static final int HEIGHT = 9;
        protected static final int DEPTH = 11;
        private final boolean leftLow;
        private final boolean leftHigh;
        private final boolean rightLow;
        private final boolean rightHigh;

        public FiveCrossing(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_FIVE_CROSSING, genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.leftLow = random.nextBoolean();
            this.leftHigh = random.nextBoolean();
            this.rightLow = random.nextBoolean();
            this.rightHigh = random.nextInt(3) > 0;
        }

        public FiveCrossing(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_FIVE_CROSSING, tag);
            this.leftLow = tag.getBooleanOr("leftLow", false);
            this.leftHigh = tag.getBooleanOr("leftHigh", false);
            this.rightLow = tag.getBooleanOr("rightLow", false);
            this.rightHigh = tag.getBooleanOr("rightHigh", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("leftLow", this.leftLow);
            tag.putBoolean("leftHigh", this.leftHigh);
            tag.putBoolean("rightLow", this.rightLow);
            tag.putBoolean("rightHigh", this.rightHigh);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            int i = 3;
            int j = 5;
            Direction direction = this.getOrientation();

            if (direction == Direction.WEST || direction == Direction.NORTH) {
                i = 8 - i;
                j = 8 - j;
            }

            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, 5, 1);
            if (this.leftLow) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, i, 1);
            }

            if (this.leftHigh) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, j, 7);
            }

            if (this.rightLow) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, i, 1);
            }

            if (this.rightHigh) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) startPiece, structurePieceAccessor, random, j, 7);
            }

        }

        public static StrongholdPieces.@Nullable FiveCrossing createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -4, -3, 0, 10, 9, 11, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.FiveCrossing(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 9, 8, 10, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 4, 3, 0);
            if (this.leftLow) {
                this.generateBox(level, chunkBB, 0, 3, 1, 0, 5, 3, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            if (this.rightLow) {
                this.generateBox(level, chunkBB, 9, 3, 1, 9, 5, 3, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            if (this.leftHigh) {
                this.generateBox(level, chunkBB, 0, 5, 7, 0, 7, 9, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            if (this.rightHigh) {
                this.generateBox(level, chunkBB, 9, 5, 7, 9, 7, 9, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            this.generateBox(level, chunkBB, 5, 1, 10, 7, 3, 10, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            this.generateBox(level, chunkBB, 1, 2, 1, 8, 2, 6, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 5, 4, 4, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 8, 1, 5, 8, 4, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 4, 7, 3, 4, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 3, 5, 3, 3, 6, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 3, 4, 3, 3, 4, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 4, 6, 3, 4, 6, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 1, 7, 7, 1, 8, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 5, 1, 9, 7, 1, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 2, 7, 7, 2, 7, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 5, 7, 4, 5, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 7, 8, 5, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 5, 7, 7, 5, 9, (BlockState) Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), (BlockState) Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), false);
            this.placeBlock(level, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 6, 5, 6, chunkBB);
        }
    }

    public static class PortalRoom extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 11;
        protected static final int HEIGHT = 8;
        protected static final int DEPTH = 16;
        private boolean hasPlacedSpawner;

        public PortalRoom(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.STRONGHOLD_PORTAL_ROOM, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public PortalRoom(CompoundTag tag) {
            super(StructurePieceType.STRONGHOLD_PORTAL_ROOM, tag);
            this.hasPlacedSpawner = tag.getBooleanOr("Mob", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Mob", this.hasPlacedSpawner);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            if (startPiece != null) {
                ((StrongholdPieces.StartPiece) startPiece).portalRoomPiece = this;
            }

        }

        public static StrongholdPieces.@Nullable PortalRoom createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -4, -1, 0, 11, 8, 16, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new StrongholdPieces.PortalRoom(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 10, 7, 15, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, StrongholdPieces.StrongholdPiece.SmallDoorType.GRATES, 4, 1, 0);
            int i = 6;

            this.generateBox(level, chunkBB, 1, 6, 1, 1, 6, 14, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 9, 6, 1, 9, 6, 14, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 2, 6, 1, 8, 6, 2, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 2, 6, 14, 8, 6, 14, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 1, 1, 2, 1, 4, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 8, 1, 1, 9, 1, 4, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 1, 1, 1, 1, 3, Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 1, 1, 9, 1, 3, Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 3, 1, 8, 7, 1, 12, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 9, 6, 1, 11, Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true);

            for (int j = 3; j < 14; j += 2) {
                this.generateBox(level, chunkBB, 0, 3, j, 0, 4, j, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 10, 3, j, 10, 4, j, blockstate, blockstate, false);
            }

            for (int k = 2; k < 9; k += 2) {
                this.generateBox(level, chunkBB, k, 3, 15, k, 4, 15, blockstate1, blockstate1, false);
            }

            BlockState blockstate2 = (BlockState) Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);

            this.generateBox(level, chunkBB, 4, 1, 5, 6, 1, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 2, 6, 6, 2, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 3, 7, 6, 3, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);

            for (int l = 4; l <= 6; ++l) {
                this.placeBlock(level, blockstate2, l, 1, 4, chunkBB);
                this.placeBlock(level, blockstate2, l, 2, 5, chunkBB);
                this.placeBlock(level, blockstate2, l, 3, 6, chunkBB);
            }

            BlockState blockstate3 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.NORTH);
            BlockState blockstate4 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.SOUTH);
            BlockState blockstate5 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.EAST);
            BlockState blockstate6 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.WEST);
            boolean flag = true;
            boolean[] aboolean = new boolean[12];

            for (int i1 = 0; i1 < aboolean.length; ++i1) {
                aboolean[i1] = random.nextFloat() > 0.9F;
                flag &= aboolean[i1];
            }

            this.placeBlock(level, (BlockState) blockstate3.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[0]), 4, 3, 8, chunkBB);
            this.placeBlock(level, (BlockState) blockstate3.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[1]), 5, 3, 8, chunkBB);
            this.placeBlock(level, (BlockState) blockstate3.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[2]), 6, 3, 8, chunkBB);
            this.placeBlock(level, (BlockState) blockstate4.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[3]), 4, 3, 12, chunkBB);
            this.placeBlock(level, (BlockState) blockstate4.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[4]), 5, 3, 12, chunkBB);
            this.placeBlock(level, (BlockState) blockstate4.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[5]), 6, 3, 12, chunkBB);
            this.placeBlock(level, (BlockState) blockstate5.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[6]), 3, 3, 9, chunkBB);
            this.placeBlock(level, (BlockState) blockstate5.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[7]), 3, 3, 10, chunkBB);
            this.placeBlock(level, (BlockState) blockstate5.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[8]), 3, 3, 11, chunkBB);
            this.placeBlock(level, (BlockState) blockstate6.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[9]), 7, 3, 9, chunkBB);
            this.placeBlock(level, (BlockState) blockstate6.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[10]), 7, 3, 10, chunkBB);
            this.placeBlock(level, (BlockState) blockstate6.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[11]), 7, 3, 11, chunkBB);
            if (flag) {
                BlockState blockstate7 = Blocks.END_PORTAL.defaultBlockState();

                this.placeBlock(level, blockstate7, 4, 3, 9, chunkBB);
                this.placeBlock(level, blockstate7, 5, 3, 9, chunkBB);
                this.placeBlock(level, blockstate7, 6, 3, 9, chunkBB);
                this.placeBlock(level, blockstate7, 4, 3, 10, chunkBB);
                this.placeBlock(level, blockstate7, 5, 3, 10, chunkBB);
                this.placeBlock(level, blockstate7, 6, 3, 10, chunkBB);
                this.placeBlock(level, blockstate7, 4, 3, 11, chunkBB);
                this.placeBlock(level, blockstate7, 5, 3, 11, chunkBB);
                this.placeBlock(level, blockstate7, 6, 3, 11, chunkBB);
            }

            if (!this.hasPlacedSpawner) {
                BlockPos blockpos1 = this.getWorldPos(5, 3, 6);

                if (chunkBB.isInside(blockpos1)) {
                    this.hasPlacedSpawner = true;
                    level.setBlock(blockpos1, Blocks.SPAWNER.defaultBlockState(), 2);
                    BlockEntity blockentity = level.getBlockEntity(blockpos1);

                    if (blockentity instanceof SpawnerBlockEntity) {
                        SpawnerBlockEntity spawnerblockentity = (SpawnerBlockEntity) blockentity;

                        spawnerblockentity.setEntityId(EntityType.SILVERFISH, random);
                    }
                }
            }

        }
    }

    private static class SmoothStoneSelector extends StructurePiece.BlockSelector {

        private SmoothStoneSelector() {}

        @Override
        public void next(RandomSource random, int worldX, int worldY, int worldZ, boolean isEdge) {
            if (isEdge) {
                float f = random.nextFloat();

                if (f < 0.2F) {
                    this.next = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                } else if (f < 0.5F) {
                    this.next = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                } else if (f < 0.55F) {
                    this.next = Blocks.INFESTED_STONE_BRICKS.defaultBlockState();
                } else {
                    this.next = Blocks.STONE_BRICKS.defaultBlockState();
                }
            } else {
                this.next = Blocks.CAVE_AIR.defaultBlockState();
            }

        }
    }
}
