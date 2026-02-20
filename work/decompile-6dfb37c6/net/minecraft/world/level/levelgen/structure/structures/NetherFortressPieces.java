package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class NetherFortressPieces {

    private static final int MAX_DEPTH = 30;
    private static final int LOWEST_Y_POSITION = 10;
    public static final int MAGIC_START_Y = 64;
    private static final NetherFortressPieces.PieceWeight[] BRIDGE_PIECE_WEIGHTS = new NetherFortressPieces.PieceWeight[]{new NetherFortressPieces.PieceWeight(NetherFortressPieces.BridgeStraight.class, 30, 0, true), new NetherFortressPieces.PieceWeight(NetherFortressPieces.BridgeCrossing.class, 10, 4), new NetherFortressPieces.PieceWeight(NetherFortressPieces.RoomCrossing.class, 10, 4), new NetherFortressPieces.PieceWeight(NetherFortressPieces.StairsRoom.class, 10, 3), new NetherFortressPieces.PieceWeight(NetherFortressPieces.MonsterThrone.class, 5, 2), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleEntrance.class, 5, 1)};
    private static final NetherFortressPieces.PieceWeight[] CASTLE_PIECE_WEIGHTS = new NetherFortressPieces.PieceWeight[]{new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorPiece.class, 25, 0, true), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorCrossingPiece.class, 15, 5), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorRightTurnPiece.class, 5, 10), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.class, 5, 10), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleCorridorStairsPiece.class, 10, 3, true), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleCorridorTBalconyPiece.class, 7, 2), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleStalkRoom.class, 5, 2)};

    public NetherFortressPieces() {}

    private static NetherFortressPieces.@Nullable NetherBridgePiece findAndCreateBridgePieceFactory(NetherFortressPieces.PieceWeight piece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
        Class<? extends NetherFortressPieces.NetherBridgePiece> oclass = piece.pieceClass;
        NetherFortressPieces.NetherBridgePiece netherfortresspieces_netherbridgepiece = null;

        if (oclass == NetherFortressPieces.BridgeStraight.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.BridgeStraight.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.BridgeCrossing.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.BridgeCrossing.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.RoomCrossing.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.RoomCrossing.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.StairsRoom.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.StairsRoom.createPiece(structurePieceAccessor, footX, footY, footZ, depth, direction);
        } else if (oclass == NetherFortressPieces.MonsterThrone.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.MonsterThrone.createPiece(structurePieceAccessor, footX, footY, footZ, depth, direction);
        } else if (oclass == NetherFortressPieces.CastleEntrance.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleEntrance.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorPiece.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleSmallCorridorPiece.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorRightTurnPiece.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleSmallCorridorRightTurnPiece.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleCorridorStairsPiece.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleCorridorStairsPiece.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleCorridorTBalconyPiece.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleCorridorTBalconyPiece.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorCrossingPiece.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleSmallCorridorCrossingPiece.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        } else if (oclass == NetherFortressPieces.CastleStalkRoom.class) {
            netherfortresspieces_netherbridgepiece = NetherFortressPieces.CastleStalkRoom.createPiece(structurePieceAccessor, footX, footY, footZ, direction, depth);
        }

        return netherfortresspieces_netherbridgepiece;
    }

    private static class PieceWeight {

        public final Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;
        public final boolean allowInRow;

        public PieceWeight(Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass, int weight, int maxPlaceCount, boolean allowInRow) {
            this.pieceClass = pieceClass;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
            this.allowInRow = allowInRow;
        }

        public PieceWeight(Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass, int weight, int maxPlaceCount) {
            this(pieceClass, weight, maxPlaceCount, false);
        }

        public boolean doPlace(int depth) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    private abstract static class NetherBridgePiece extends StructurePiece {

        protected NetherBridgePiece(StructurePieceType type, int genDepth, BoundingBox boundingBox) {
            super(type, genDepth, boundingBox);
        }

        public NetherBridgePiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

        private int updatePieceWeight(List<NetherFortressPieces.PieceWeight> currentPieces) {
            boolean flag = false;
            int i = 0;

            for (NetherFortressPieces.PieceWeight netherfortresspieces_pieceweight : currentPieces) {
                if (netherfortresspieces_pieceweight.maxPlaceCount > 0 && netherfortresspieces_pieceweight.placeCount < netherfortresspieces_pieceweight.maxPlaceCount) {
                    flag = true;
                }

                i += netherfortresspieces_pieceweight.weight;
            }

            return flag ? i : -1;
        }

        private NetherFortressPieces.@Nullable NetherBridgePiece generatePiece(NetherFortressPieces.StartPiece startPiece, List<NetherFortressPieces.PieceWeight> currentPieces, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
            int i1 = this.updatePieceWeight(currentPieces);
            boolean flag = i1 > 0 && depth <= 30;
            int j1 = 0;

            while (j1 < 5 && flag) {
                ++j1;
                int k1 = random.nextInt(i1);

                for (NetherFortressPieces.PieceWeight netherfortresspieces_pieceweight : currentPieces) {
                    k1 -= netherfortresspieces_pieceweight.weight;
                    if (k1 < 0) {
                        if (!netherfortresspieces_pieceweight.doPlace(depth) || netherfortresspieces_pieceweight == startPiece.previousPiece && !netherfortresspieces_pieceweight.allowInRow) {
                            break;
                        }

                        NetherFortressPieces.NetherBridgePiece netherfortresspieces_netherbridgepiece = NetherFortressPieces.findAndCreateBridgePieceFactory(netherfortresspieces_pieceweight, structurePieceAccessor, random, footX, footY, footZ, direction, depth);

                        if (netherfortresspieces_netherbridgepiece != null) {
                            ++netherfortresspieces_pieceweight.placeCount;
                            startPiece.previousPiece = netherfortresspieces_pieceweight;
                            if (!netherfortresspieces_pieceweight.isValid()) {
                                currentPieces.remove(netherfortresspieces_pieceweight);
                            }

                            return netherfortresspieces_netherbridgepiece;
                        }
                    }
                }
            }

            return NetherFortressPieces.BridgeEndFiller.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
        }

        private @Nullable StructurePiece generateAndAddPiece(NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int depth, boolean isCastle) {
            if (Math.abs(footX - startPiece.getBoundingBox().minX()) <= 112 && Math.abs(footZ - startPiece.getBoundingBox().minZ()) <= 112) {
                List<NetherFortressPieces.PieceWeight> list = startPiece.availableBridgePieces;

                if (isCastle) {
                    list = startPiece.availableCastlePieces;
                }

                StructurePiece structurepiece = this.generatePiece(startPiece, list, structurePieceAccessor, random, footX, footY, footZ, direction, depth + 1);

                if (structurepiece != null) {
                    structurePieceAccessor.addPiece(structurepiece);
                    startPiece.pendingChildren.add(structurepiece);
                }

                return structurepiece;
            } else {
                return NetherFortressPieces.BridgeEndFiller.createPiece(structurePieceAccessor, random, footX, footY, footZ, direction, depth);
            }
        }

        protected @Nullable StructurePiece generateChildForward(NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int xOff, int yOff, boolean isCastle) {
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1, direction, this.getGenDepth(), isCastle);
                    case SOUTH:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1, direction, this.getGenDepth(), isCastle);
                    case WEST:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff, direction, this.getGenDepth(), isCastle);
                    case EAST:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff, direction, this.getGenDepth(), isCastle);
                }
            }

            return null;
        }

        protected @Nullable StructurePiece generateChildLeft(NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int yOff, int zOff, boolean isCastle) {
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.WEST, this.getGenDepth(), isCastle);
                    case SOUTH:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.WEST, this.getGenDepth(), isCastle);
                    case WEST:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth(), isCastle);
                    case EAST:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth(), isCastle);
                }
            }

            return null;
        }

        protected @Nullable StructurePiece generateChildRight(NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random, int yOff, int zOff, boolean isCastle) {
            Direction direction = this.getOrientation();

            if (direction != null) {
                switch (direction) {
                    case NORTH:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.EAST, this.getGenDepth(), isCastle);
                    case SOUTH:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff, Direction.EAST, this.getGenDepth(), isCastle);
                    case WEST:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth(), isCastle);
                    case EAST:
                        return this.generateAndAddPiece(startPiece, structurePieceAccessor, random, this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth(), isCastle);
                }
            }

            return null;
        }

        protected static boolean isOkBox(BoundingBox box) {
            return box.minY() > 10;
        }
    }

    public static class StartPiece extends NetherFortressPieces.BridgeCrossing {

        private NetherFortressPieces.@Nullable PieceWeight previousPiece;
        private final List<NetherFortressPieces.PieceWeight> availableBridgePieces = new ArrayList();
        private final List<NetherFortressPieces.PieceWeight> availableCastlePieces = new ArrayList();
        public final List<StructurePiece> pendingChildren = Lists.newArrayList();

        public StartPiece(RandomSource random, int west, int north) {
            super(west, north, getRandomHorizontalDirection(random));

            for (NetherFortressPieces.PieceWeight netherfortresspieces_pieceweight : NetherFortressPieces.BRIDGE_PIECE_WEIGHTS) {
                netherfortresspieces_pieceweight.placeCount = 0;
                this.availableBridgePieces.add(netherfortresspieces_pieceweight);
            }

            for (NetherFortressPieces.PieceWeight netherfortresspieces_pieceweight1 : NetherFortressPieces.CASTLE_PIECE_WEIGHTS) {
                netherfortresspieces_pieceweight1.placeCount = 0;
                this.availableCastlePieces.add(netherfortresspieces_pieceweight1);
            }

        }

        public StartPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_START, tag);
        }
    }

    public static class BridgeStraight extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 19;

        public BridgeStraight(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public BridgeStraight(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 3, false);
        }

        public static NetherFortressPieces.@Nullable BridgeStraight createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -3, 0, 5, 10, 19, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.BridgeStraight(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 3, 0, 4, 4, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 5, 0, 3, 7, 18, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 0, 0, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 5, 0, 4, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 2, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 13, 4, 2, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 0, 15, 4, 1, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 18 - j, chunkBB);
                }
            }

            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);
            BlockState blockstate1 = (BlockState) blockstate.setValue(FenceBlock.EAST, true);
            BlockState blockstate2 = (BlockState) blockstate.setValue(FenceBlock.WEST, true);

            this.generateBox(level, chunkBB, 0, 1, 1, 0, 4, 1, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 0, 3, 4, 0, 4, 4, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 0, 3, 14, 0, 4, 14, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 0, 1, 17, 0, 4, 17, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 4, 1, 1, 4, 4, 1, blockstate2, blockstate2, false);
            this.generateBox(level, chunkBB, 4, 3, 4, 4, 4, 4, blockstate2, blockstate2, false);
            this.generateBox(level, chunkBB, 4, 3, 14, 4, 4, 14, blockstate2, blockstate2, false);
            this.generateBox(level, chunkBB, 4, 1, 17, 4, 4, 17, blockstate2, blockstate2, false);
        }
    }

    public static class BridgeEndFiller extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 8;
        private final int selfSeed;

        public BridgeEndFiller(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_END_FILLER, genDepth, boundingBox);
            this.setOrientation(direction);
            this.selfSeed = random.nextInt();
        }

        public BridgeEndFiller(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_END_FILLER, tag);
            this.selfSeed = tag.getIntOr("Seed", 0);
        }

        public static NetherFortressPieces.@Nullable BridgeEndFiller createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -3, 0, 5, 10, 8, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.BridgeEndFiller(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putInt("Seed", this.selfSeed);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            RandomSource randomsource1 = RandomSource.create((long) this.selfSeed);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 3; j <= 4; ++j) {
                    int k = randomsource1.nextInt(8);

                    this.generateBox(level, chunkBB, i, j, 0, i, j, k, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                }
            }

            int l = randomsource1.nextInt(8);

            this.generateBox(level, chunkBB, 0, 5, 0, 0, 5, l, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            l = randomsource1.nextInt(8);
            this.generateBox(level, chunkBB, 4, 5, 0, 4, 5, l, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i1 = 0; i1 <= 4; ++i1) {
                int j1 = randomsource1.nextInt(5);

                this.generateBox(level, chunkBB, i1, 2, 0, i1, 2, j1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            }

            for (int k1 = 0; k1 <= 4; ++k1) {
                for (int l1 = 0; l1 <= 1; ++l1) {
                    int i2 = randomsource1.nextInt(3);

                    this.generateBox(level, chunkBB, k1, l1, 0, k1, l1, i2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                }
            }

        }
    }

    public static class BridgeCrossing extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 19;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 19;

        public BridgeCrossing(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        protected BridgeCrossing(int west, int north, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, 0, StructurePiece.makeBoundingBox(west, 64, north, direction, 19, 10, 19));
            this.setOrientation(direction);
        }

        protected BridgeCrossing(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        public BridgeCrossing(CompoundTag tag) {
            this(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 8, 3, false);
            this.generateChildLeft((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 3, 8, false);
            this.generateChildRight((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 3, 8, false);
        }

        public static NetherFortressPieces.@Nullable BridgeCrossing createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -8, -3, 0, 19, 10, 19, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.BridgeCrossing(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 7, 3, 0, 11, 4, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 3, 7, 18, 4, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 0, 10, 7, 18, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 8, 18, 7, 10, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 7, 5, 0, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 7, 5, 11, 7, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 11, 5, 0, 11, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 11, 5, 11, 11, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 7, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 11, 5, 7, 18, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 11, 7, 5, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 11, 5, 11, 18, 5, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 7, 2, 0, 11, 2, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 7, 2, 13, 11, 2, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 7, 0, 0, 11, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 7, 0, 15, 11, 1, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 7; i <= 11; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 18 - j, chunkBB);
                }
            }

            this.generateBox(level, chunkBB, 0, 2, 7, 5, 2, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 13, 2, 7, 18, 2, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 0, 7, 3, 1, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 15, 0, 7, 18, 1, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int k = 0; k <= 2; ++k) {
                for (int l = 7; l <= 11; ++l) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), k, -1, l, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), 18 - k, -1, l, chunkBB);
                }
            }

        }
    }

    public static class RoomCrossing extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 7;
        private static final int HEIGHT = 9;
        private static final int DEPTH = 7;

        public RoomCrossing(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_ROOM_CROSSING, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public RoomCrossing(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_ROOM_CROSSING, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 2, 0, false);
            this.generateChildLeft((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, 2, false);
            this.generateChildRight((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, 2, false);
        }

        public static NetherFortressPieces.@Nullable RoomCrossing createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -2, 0, 0, 7, 9, 7, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.RoomCrossing(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 6, 7, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 1, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 6, 1, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 2, 0, 6, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 2, 6, 6, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 6, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 5, 0, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 2, 0, 6, 6, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 2, 5, 6, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(level, chunkBB, 2, 6, 0, 4, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 5, 0, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 2, 6, 6, 4, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 6, 4, 5, 6, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 0, 6, 2, 0, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 2, 0, 5, 4, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 6, 6, 2, 6, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 5, 2, 6, 5, 4, blockstate1, blockstate1, false);

            for (int i = 0; i <= 6; ++i) {
                for (int j = 0; j <= 6; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class StairsRoom extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 7;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 7;

        public StairsRoom(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_STAIRS_ROOM, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public StairsRoom(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_STAIRS_ROOM, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildRight((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 6, 2, false);
        }

        public static NetherFortressPieces.@Nullable StairsRoom createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, int genDepth, Direction direction) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -2, 0, 0, 7, 11, 7, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.StairsRoom(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 6, 10, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 1, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 2, 0, 6, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 1, 0, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 2, 1, 6, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 2, 6, 5, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(level, chunkBB, 0, 3, 2, 0, 5, 4, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 6, 3, 2, 6, 5, 2, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 6, 3, 4, 6, 5, 4, blockstate1, blockstate1, false);
            this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 5, 2, 5, chunkBB);
            this.generateBox(level, chunkBB, 4, 2, 5, 4, 3, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 3, 2, 5, 3, 4, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 2, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 2, 5, 1, 6, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 7, 1, 5, 7, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 8, 2, 6, 8, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 6, 0, 4, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 5, 0, blockstate, blockstate, false);

            for (int i = 0; i <= 6; ++i) {
                for (int j = 0; j <= 6; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class MonsterThrone extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 7;
        private static final int HEIGHT = 8;
        private static final int DEPTH = 9;
        private boolean hasPlacedSpawner;

        public MonsterThrone(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_MONSTER_THRONE, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public MonsterThrone(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_MONSTER_THRONE, tag);
            this.hasPlacedSpawner = tag.getBooleanOr("Mob", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Mob", this.hasPlacedSpawner);
        }

        public static NetherFortressPieces.@Nullable MonsterThrone createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, int genDepth, Direction direction) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -2, 0, 0, 7, 8, 9, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.MonsterThrone(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 2, 0, 6, 7, 7, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 0, 0, 5, 1, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 2, 1, 5, 2, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 3, 2, 5, 3, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 4, 3, 5, 4, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 2, 0, 1, 4, 2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 2, 0, 5, 4, 2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 5, 2, 1, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 5, 2, 5, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 3, 0, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 5, 3, 6, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 5, 8, 5, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.placeBlock(level, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 1, 6, 3, chunkBB);
            this.placeBlock(level, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 5, 6, 3, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)).setValue(FenceBlock.NORTH, true), 0, 6, 3, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.NORTH, true), 6, 6, 3, chunkBB);
            this.generateBox(level, chunkBB, 0, 6, 4, 0, 6, 7, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 6, 6, 4, 6, 6, 7, blockstate1, blockstate1, false);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)).setValue(FenceBlock.SOUTH, true), 0, 6, 8, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.SOUTH, true), 6, 6, 8, chunkBB);
            this.generateBox(level, chunkBB, 1, 6, 8, 5, 6, 8, blockstate, blockstate, false);
            this.placeBlock(level, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 1, 7, 8, chunkBB);
            this.generateBox(level, chunkBB, 2, 7, 8, 4, 7, 8, blockstate, blockstate, false);
            this.placeBlock(level, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 5, 7, 8, chunkBB);
            this.placeBlock(level, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 2, 8, 8, chunkBB);
            this.placeBlock(level, blockstate, 3, 8, 8, chunkBB);
            this.placeBlock(level, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 4, 8, 8, chunkBB);
            if (!this.hasPlacedSpawner) {
                BlockPos blockpos1 = this.getWorldPos(3, 5, 5);

                if (chunkBB.isInside(blockpos1)) {
                    this.hasPlacedSpawner = true;
                    level.setBlock(blockpos1, Blocks.SPAWNER.defaultBlockState(), 2);
                    BlockEntity blockentity = level.getBlockEntity(blockpos1);

                    if (blockentity instanceof SpawnerBlockEntity) {
                        SpawnerBlockEntity spawnerblockentity = (SpawnerBlockEntity) blockentity;

                        spawnerblockentity.setEntityId(EntityType.BLAZE, random);
                    }
                }
            }

            for (int i = 0; i <= 6; ++i) {
                for (int j = 0; j <= 6; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class CastleEntrance extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 13;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 13;

        public CastleEntrance(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_ENTRANCE, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public CastleEntrance(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_ENTRANCE, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 5, 3, true);
        }

        public static NetherFortressPieces.@Nullable CastleEntrance createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -5, -3, 0, 13, 14, 13, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleEntrance(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 8, 0, 7, 8, 0, Blocks.NETHER_BRICK_FENCE.defaultBlockState(), Blocks.NETHER_BRICK_FENCE.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            for (int i = 1; i <= 11; i += 2) {
                this.generateBox(level, chunkBB, i, 10, 0, i, 11, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, i, 10, 12, i, 11, 12, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 0, 10, i, 0, 11, i, blockstate1, blockstate1, false);
                this.generateBox(level, chunkBB, 12, 10, i, 12, 11, i, blockstate1, blockstate1, false);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 0, chunkBB);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 12, chunkBB);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 0, 13, i, chunkBB);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12, 13, i, chunkBB);
                if (i != 11) {
                    this.placeBlock(level, blockstate, i + 1, 13, 0, chunkBB);
                    this.placeBlock(level, blockstate, i + 1, 13, 12, chunkBB);
                    this.placeBlock(level, blockstate1, 0, 13, i + 1, chunkBB);
                    this.placeBlock(level, blockstate1, 12, 13, i + 1, chunkBB);
                }
            }

            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 0, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 12, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 12, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 0, chunkBB);

            for (int j = 3; j <= 9; j += 2) {
                this.generateBox(level, chunkBB, 1, 7, j, 1, 8, j, (BlockState) blockstate1.setValue(FenceBlock.WEST, true), (BlockState) blockstate1.setValue(FenceBlock.WEST, true), false);
                this.generateBox(level, chunkBB, 11, 7, j, 11, 8, j, (BlockState) blockstate1.setValue(FenceBlock.EAST, true), (BlockState) blockstate1.setValue(FenceBlock.EAST, true), false);
            }

            this.generateBox(level, chunkBB, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int k = 4; k <= 8; ++k) {
                for (int l = 0; l <= 2; ++l) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), k, -1, l, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), k, -1, 12 - l, chunkBB);
                }
            }

            for (int i1 = 0; i1 <= 2; ++i1) {
                for (int j1 = 4; j1 <= 8; ++j1) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i1, -1, j1, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12 - i1, -1, j1, chunkBB);
                }
            }

            this.generateBox(level, chunkBB, 5, 5, 5, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 1, 6, 6, 4, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 6, 0, 6, chunkBB);
            this.placeBlock(level, Blocks.LAVA.defaultBlockState(), 6, 5, 6, chunkBB);
            BlockPos blockpos1 = this.getWorldPos(6, 5, 6);

            if (chunkBB.isInside(blockpos1)) {
                level.scheduleTick(blockpos1, (Fluid) Fluids.LAVA, 0);
            }

        }
    }

    public static class CastleStalkRoom extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 13;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 13;

        public CastleStalkRoom(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_STALK_ROOM, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public CastleStalkRoom(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_STALK_ROOM, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 5, 3, true);
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 5, 11, true);
        }

        public static NetherFortressPieces.@Nullable CastleStalkRoom createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -5, -3, 0, 13, 14, 13, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleStalkRoom(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);
            BlockState blockstate2 = (BlockState) blockstate1.setValue(FenceBlock.WEST, true);
            BlockState blockstate3 = (BlockState) blockstate1.setValue(FenceBlock.EAST, true);

            for (int i = 1; i <= 11; i += 2) {
                this.generateBox(level, chunkBB, i, 10, 0, i, 11, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, i, 10, 12, i, 11, 12, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 0, 10, i, 0, 11, i, blockstate1, blockstate1, false);
                this.generateBox(level, chunkBB, 12, 10, i, 12, 11, i, blockstate1, blockstate1, false);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 0, chunkBB);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 12, chunkBB);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 0, 13, i, chunkBB);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12, 13, i, chunkBB);
                if (i != 11) {
                    this.placeBlock(level, blockstate, i + 1, 13, 0, chunkBB);
                    this.placeBlock(level, blockstate, i + 1, 13, 12, chunkBB);
                    this.placeBlock(level, blockstate1, 0, 13, i + 1, chunkBB);
                    this.placeBlock(level, blockstate1, 12, 13, i + 1, chunkBB);
                }
            }

            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 0, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 12, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 12, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 0, chunkBB);

            for (int j = 3; j <= 9; j += 2) {
                this.generateBox(level, chunkBB, 1, 7, j, 1, 8, j, blockstate2, blockstate2, false);
                this.generateBox(level, chunkBB, 11, 7, j, 11, 8, j, blockstate3, blockstate3, false);
            }

            BlockState blockstate4 = (BlockState) Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);

            for (int k = 0; k <= 6; ++k) {
                int l = k + 4;

                for (int i1 = 5; i1 <= 7; ++i1) {
                    this.placeBlock(level, blockstate4, i1, 5 + k, l, chunkBB);
                }

                if (l >= 5 && l <= 8) {
                    this.generateBox(level, chunkBB, 5, 5, l, 7, k + 4, l, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                } else if (l >= 9 && l <= 10) {
                    this.generateBox(level, chunkBB, 5, 8, l, 7, k + 4, l, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                }

                if (k >= 1) {
                    this.generateBox(level, chunkBB, 5, 6 + k, l, 7, 9 + k, l, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
                }
            }

            for (int j1 = 5; j1 <= 7; ++j1) {
                this.placeBlock(level, blockstate4, j1, 12, 11, chunkBB);
            }

            this.generateBox(level, chunkBB, 5, 6, 7, 5, 7, 7, blockstate3, blockstate3, false);
            this.generateBox(level, chunkBB, 7, 6, 7, 7, 7, 7, blockstate2, blockstate2, false);
            this.generateBox(level, chunkBB, 5, 13, 12, 7, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 2, 3, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 9, 3, 5, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 2, 5, 4, 2, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 5, 2, 10, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 5, 9, 10, 5, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 10, 5, 4, 10, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockstate5 = (BlockState) blockstate4.setValue(StairBlock.FACING, Direction.EAST);
            BlockState blockstate6 = (BlockState) blockstate4.setValue(StairBlock.FACING, Direction.WEST);

            this.placeBlock(level, blockstate6, 4, 5, 2, chunkBB);
            this.placeBlock(level, blockstate6, 4, 5, 3, chunkBB);
            this.placeBlock(level, blockstate6, 4, 5, 9, chunkBB);
            this.placeBlock(level, blockstate6, 4, 5, 10, chunkBB);
            this.placeBlock(level, blockstate5, 8, 5, 2, chunkBB);
            this.placeBlock(level, blockstate5, 8, 5, 3, chunkBB);
            this.placeBlock(level, blockstate5, 8, 5, 9, chunkBB);
            this.placeBlock(level, blockstate5, 8, 5, 10, chunkBB);
            this.generateBox(level, chunkBB, 3, 4, 4, 4, 4, 8, Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 4, 4, 9, 4, 8, Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 3, 5, 4, 4, 5, 8, Blocks.NETHER_WART.defaultBlockState(), Blocks.NETHER_WART.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 5, 4, 9, 5, 8, Blocks.NETHER_WART.defaultBlockState(), Blocks.NETHER_WART.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int k1 = 4; k1 <= 8; ++k1) {
                for (int l1 = 0; l1 <= 2; ++l1) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), k1, -1, l1, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), k1, -1, 12 - l1, chunkBB);
                }
            }

            for (int i2 = 0; i2 <= 2; ++i2) {
                for (int j2 = 4; j2 <= 8; ++j2) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i2, -1, j2, chunkBB);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12 - i2, -1, j2, chunkBB);
                }
            }

        }
    }

    public static class CastleSmallCorridorPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;

        public CastleSmallCorridorPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public CastleSmallCorridorPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 0, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorPiece createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorPiece(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 3, 1, 0, 4, 1, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 0, 3, 3, 0, 4, 3, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 4, 3, 1, 4, 4, 1, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 4, 3, 3, 4, 4, 3, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class CastleSmallCorridorCrossingPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;

        public CastleSmallCorridorCrossingPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_CROSSING, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public CastleSmallCorridorCrossingPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_CROSSING, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 0, true);
            this.generateChildLeft((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, 1, true);
            this.generateChildRight((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, 1, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorCrossingPiece createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorCrossingPiece(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 4, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class CastleSmallCorridorRightTurnPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;
        private boolean isNeedingChest;

        public CastleSmallCorridorRightTurnPiece(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_RIGHT_TURN, genDepth, boundingBox);
            this.setOrientation(direction);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        public CastleSmallCorridorRightTurnPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_RIGHT_TURN, tag);
            this.isNeedingChest = tag.getBooleanOr("Chest", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Chest", this.isNeedingChest);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildRight((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, 1, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorRightTurnPiece createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorRightTurnPiece(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 3, 1, 0, 4, 1, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 0, 3, 3, 0, 4, 3, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 3, 4, 1, 4, 4, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 3, 3, 4, 3, 4, 4, blockstate, blockstate, false);
            if (this.isNeedingChest && chunkBB.isInside(this.getWorldPos(1, 2, 3))) {
                this.isNeedingChest = false;
                this.createChest(level, chunkBB, random, 1, 2, 3, BuiltInLootTables.NETHER_BRIDGE);
            }

            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class CastleSmallCorridorLeftTurnPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;
        private boolean isNeedingChest;

        public CastleSmallCorridorLeftTurnPiece(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_LEFT_TURN, genDepth, boundingBox);
            this.setOrientation(direction);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        public CastleSmallCorridorLeftTurnPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_LEFT_TURN, tag);
            this.isNeedingChest = tag.getBooleanOr("Chest", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Chest", this.isNeedingChest);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildLeft((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, 1, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorLeftTurnPiece createPiece(StructurePieceAccessor structurePieceAccessor, RandomSource random, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorLeftTurnPiece(genDepth, random, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 3, 1, 4, 4, 1, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 4, 3, 3, 4, 4, 3, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 4, 3, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 3, 4, 1, 4, 4, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 3, 3, 4, 3, 4, 4, blockstate, blockstate, false);
            if (this.isNeedingChest && chunkBB.isInside(this.getWorldPos(3, 2, 3))) {
                this.isNeedingChest = false;
                this.createChest(level, chunkBB, random, 3, 2, 3, BuiltInLootTables.NETHER_BRIDGE);
            }

            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBB);
                }
            }

        }
    }

    public static class CastleCorridorStairsPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 10;

        public CastleCorridorStairsPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_STAIRS, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public CastleCorridorStairsPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_STAIRS, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 1, 0, true);
        }

        public static NetherFortressPieces.@Nullable CastleCorridorStairsPiece createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -1, -7, 0, 5, 14, 10, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleCorridorStairsPiece(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            BlockState blockstate = (BlockState) Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            for (int i = 0; i <= 9; ++i) {
                int j = Math.max(1, 7 - i);
                int k = Math.min(Math.max(j + 5, 14 - i), 13);
                int l = i;

                this.generateBox(level, chunkBB, 0, 0, i, 4, j, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 1, j + 1, i, 3, k - 1, i, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
                if (i <= 6) {
                    this.placeBlock(level, blockstate, 1, j + 1, i, chunkBB);
                    this.placeBlock(level, blockstate, 2, j + 1, i, chunkBB);
                    this.placeBlock(level, blockstate, 3, j + 1, i, chunkBB);
                }

                this.generateBox(level, chunkBB, 0, k, i, 4, k, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 0, j + 1, i, 0, k - 1, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(level, chunkBB, 4, j + 1, i, 4, k - 1, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                if ((i & 1) == 0) {
                    this.generateBox(level, chunkBB, 0, j + 2, i, 0, j + 3, i, blockstate1, blockstate1, false);
                    this.generateBox(level, chunkBB, 4, j + 2, i, 4, j + 3, i, blockstate1, blockstate1, false);
                }

                for (int i1 = 0; i1 <= 4; ++i1) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i1, -1, l, chunkBB);
                }
            }

        }
    }

    public static class CastleCorridorTBalconyPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 9;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 9;

        public CastleCorridorTBalconyPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_T_BALCONY, genDepth, boundingBox);
            this.setOrientation(direction);
        }

        public CastleCorridorTBalconyPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_T_BALCONY, tag);
        }

        @Override
        public void addChildren(StructurePiece startPiece, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
            int i = 1;
            Direction direction = this.getOrientation();

            if (direction == Direction.WEST || direction == Direction.NORTH) {
                i = 5;
            }

            this.generateChildLeft((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, i, random.nextInt(8) > 0);
            this.generateChildRight((NetherFortressPieces.StartPiece) startPiece, structurePieceAccessor, random, 0, i, random.nextInt(8) > 0);
        }

        public static NetherFortressPieces.@Nullable CastleCorridorTBalconyPiece createPiece(StructurePieceAccessor structurePieceAccessor, int footX, int footY, int footZ, Direction direction, int genDepth) {
            BoundingBox boundingbox = BoundingBox.orientBox(footX, footY, footZ, -3, 0, 0, 9, 7, 9, direction);

            return isOkBox(boundingbox) && structurePieceAccessor.findCollisionPiece(boundingbox) == null ? new NetherFortressPieces.CastleCorridorTBalconyPiece(genDepth, boundingbox, direction) : null;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            BlockState blockstate = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);
            BlockState blockstate1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);

            this.generateBox(level, chunkBB, 0, 0, 0, 8, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 8, 5, 8, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 6, 0, 8, 6, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 0, 2, 0, 2, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 2, 0, 8, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 3, 0, 1, 4, 0, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 7, 3, 0, 7, 4, 0, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 0, 2, 4, 8, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 1, 4, 2, 2, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 1, 4, 7, 2, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 3, 8, 7, 3, 8, blockstate1, blockstate1, false);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)).setValue(FenceBlock.SOUTH, true), 0, 3, 8, chunkBB);
            this.placeBlock(level, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.SOUTH, true), 8, 3, 8, chunkBB);
            this.generateBox(level, chunkBB, 0, 3, 6, 0, 3, 7, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 8, 3, 6, 8, 3, 7, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 0, 3, 4, 0, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 3, 4, 8, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 3, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 3, 5, 7, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 4, 5, 1, 5, 5, blockstate1, blockstate1, false);
            this.generateBox(level, chunkBB, 7, 4, 5, 7, 5, 5, blockstate1, blockstate1, false);

            for (int i = 0; i <= 5; ++i) {
                for (int j = 0; j <= 8; ++j) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), j, -1, i, chunkBB);
                }
            }

        }
    }
}
