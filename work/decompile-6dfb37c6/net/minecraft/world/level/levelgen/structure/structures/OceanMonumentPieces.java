package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class OceanMonumentPieces {

    private OceanMonumentPieces() {}

    protected abstract static class OceanMonumentPiece extends StructurePiece {

        protected static final BlockState BASE_GRAY = Blocks.PRISMARINE.defaultBlockState();
        protected static final BlockState BASE_LIGHT = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        protected static final BlockState BASE_BLACK = Blocks.DARK_PRISMARINE.defaultBlockState();
        protected static final BlockState DOT_DECO_DATA = OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT;
        protected static final BlockState LAMP_BLOCK = Blocks.SEA_LANTERN.defaultBlockState();
        protected static final boolean DO_FILL = true;
        protected static final BlockState FILL_BLOCK = Blocks.WATER.defaultBlockState();
        protected static final Set<Block> FILL_KEEP = ImmutableSet.builder().add(Blocks.ICE).add(Blocks.PACKED_ICE).add(Blocks.BLUE_ICE).add(OceanMonumentPieces.OceanMonumentPiece.FILL_BLOCK.getBlock()).build();
        protected static final int GRIDROOM_WIDTH = 8;
        protected static final int GRIDROOM_DEPTH = 8;
        protected static final int GRIDROOM_HEIGHT = 4;
        protected static final int GRID_WIDTH = 5;
        protected static final int GRID_DEPTH = 5;
        protected static final int GRID_HEIGHT = 3;
        protected static final int GRID_FLOOR_COUNT = 25;
        protected static final int GRID_SIZE = 75;
        protected static final int GRIDROOM_SOURCE_INDEX = getRoomIndex(2, 0, 0);
        protected static final int GRIDROOM_TOP_CONNECT_INDEX = getRoomIndex(2, 2, 0);
        protected static final int GRIDROOM_LEFTWING_CONNECT_INDEX = getRoomIndex(0, 1, 0);
        protected static final int GRIDROOM_RIGHTWING_CONNECT_INDEX = getRoomIndex(4, 1, 0);
        protected static final int LEFTWING_INDEX = 1001;
        protected static final int RIGHTWING_INDEX = 1002;
        protected static final int PENTHOUSE_INDEX = 1003;
        protected OceanMonumentPieces.RoomDefinition roomDefinition;

        protected static int getRoomIndex(int roomX, int roomY, int roomZ) {
            return roomY * 25 + roomZ * 5 + roomX;
        }

        public OceanMonumentPiece(StructurePieceType type, Direction orientation, int genDepth, BoundingBox boundingBox) {
            super(type, genDepth, boundingBox);
            this.setOrientation(orientation);
        }

        protected OceanMonumentPiece(StructurePieceType type, int genDepth, Direction orientation, OceanMonumentPieces.RoomDefinition roomDefinition, int roomWidth, int roomHeight, int roomDepth) {
            super(type, genDepth, makeBoundingBox(orientation, roomDefinition, roomWidth, roomHeight, roomDepth));
            this.setOrientation(orientation);
            this.roomDefinition = roomDefinition;
        }

        private static BoundingBox makeBoundingBox(Direction orientation, OceanMonumentPieces.RoomDefinition roomDefinition, int roomWidth, int roomHeight, int roomDepth) {
            int l = roomDefinition.index;
            int i1 = l % 5;
            int j1 = l / 5 % 5;
            int k1 = l / 25;
            BoundingBox boundingbox = makeBoundingBox(0, 0, 0, orientation, roomWidth * 8, roomHeight * 4, roomDepth * 8);

            switch (orientation) {
                case NORTH:
                    boundingbox.move(i1 * 8, k1 * 4, -(j1 + roomDepth) * 8 + 1);
                    break;
                case SOUTH:
                    boundingbox.move(i1 * 8, k1 * 4, j1 * 8);
                    break;
                case WEST:
                    boundingbox.move(-(j1 + roomDepth) * 8 + 1, k1 * 4, i1 * 8);
                    break;
                case EAST:
                default:
                    boundingbox.move(j1 * 8, k1 * 4, i1 * 8);
            }

            return boundingbox;
        }

        public OceanMonumentPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

        protected void generateWaterBox(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int k1 = y0; k1 <= y1; ++k1) {
                for (int l1 = x0; l1 <= x1; ++l1) {
                    for (int i2 = z0; i2 <= z1; ++i2) {
                        BlockState blockstate = this.getBlock(level, l1, k1, i2, chunkBB);

                        if (!OceanMonumentPieces.OceanMonumentPiece.FILL_KEEP.contains(blockstate.getBlock())) {
                            if (this.getWorldY(k1) >= level.getSeaLevel() && blockstate != OceanMonumentPieces.OceanMonumentPiece.FILL_BLOCK) {
                                this.placeBlock(level, Blocks.AIR.defaultBlockState(), l1, k1, i2, chunkBB);
                            } else {
                                this.placeBlock(level, OceanMonumentPieces.OceanMonumentPiece.FILL_BLOCK, l1, k1, i2, chunkBB);
                            }
                        }
                    }
                }
            }

        }

        protected void generateDefaultFloor(WorldGenLevel level, BoundingBox chunkBB, int xOff, int zOff, boolean downOpening) {
            if (downOpening) {
                this.generateBox(level, chunkBB, xOff + 0, 0, zOff + 0, xOff + 2, 0, zOff + 8 - 1, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 5, 0, zOff + 0, xOff + 8 - 1, 0, zOff + 8 - 1, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 0, xOff + 4, 0, zOff + 2, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 5, xOff + 4, 0, zOff + 8 - 1, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 2, xOff + 4, 0, zOff + 2, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 5, xOff + 4, 0, zOff + 5, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, xOff + 2, 0, zOff + 3, xOff + 2, 0, zOff + 4, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, xOff + 5, 0, zOff + 3, xOff + 5, 0, zOff + 4, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPiece.BASE_LIGHT, false);
            } else {
                this.generateBox(level, chunkBB, xOff + 0, 0, zOff + 0, xOff + 8 - 1, 0, zOff + 8 - 1, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, OceanMonumentPieces.OceanMonumentPiece.BASE_GRAY, false);
            }

        }

        protected void generateBoxOnFillOnly(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1, BlockState targetBlock) {
            for (int k1 = y0; k1 <= y1; ++k1) {
                for (int l1 = x0; l1 <= x1; ++l1) {
                    for (int i2 = z0; i2 <= z1; ++i2) {
                        if (this.getBlock(level, l1, k1, i2, chunkBB) == OceanMonumentPieces.OceanMonumentPiece.FILL_BLOCK) {
                            this.placeBlock(level, targetBlock, l1, k1, i2, chunkBB);
                        }
                    }
                }
            }

        }

        protected boolean chunkIntersects(BoundingBox chunkBB, int x0, int z0, int x1, int z1) {
            int i1 = this.getWorldX(x0, z0);
            int j1 = this.getWorldZ(x0, z0);
            int k1 = this.getWorldX(x1, z1);
            int l1 = this.getWorldZ(x1, z1);

            return chunkBB.intersects(Math.min(i1, k1), Math.min(j1, l1), Math.max(i1, k1), Math.max(j1, l1));
        }

        protected void spawnElder(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z) {
            BlockPos blockpos = this.getWorldPos(x, y, z);

            if (chunkBB.isInside(blockpos)) {
                ElderGuardian elderguardian = EntityType.ELDER_GUARDIAN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);

                if (elderguardian != null) {
                    elderguardian.heal(elderguardian.getMaxHealth());
                    elderguardian.snapTo((double) blockpos.getX() + 0.5D, (double) blockpos.getY(), (double) blockpos.getZ() + 0.5D, 0.0F, 0.0F);
                    elderguardian.finalizeSpawn(level, level.getCurrentDifficultyAt(elderguardian.blockPosition()), EntitySpawnReason.STRUCTURE, (SpawnGroupData) null);
                    level.addFreshEntityWithPassengers(elderguardian);
                }
            }

        }
    }

    public static class MonumentBuilding extends OceanMonumentPieces.OceanMonumentPiece {

        private static final int WIDTH = 58;
        private static final int HEIGHT = 22;
        private static final int DEPTH = 58;
        public static final int BIOME_RANGE_CHECK = 29;
        private static final int TOP_POSITION = 61;
        private OceanMonumentPieces.RoomDefinition sourceRoom;
        private OceanMonumentPieces.RoomDefinition coreRoom;
        private final List<OceanMonumentPieces.OceanMonumentPiece> childPieces = Lists.newArrayList();

        public MonumentBuilding(RandomSource random, int west, int north, Direction direction) {
            super(StructurePieceType.OCEAN_MONUMENT_BUILDING, direction, 0, makeBoundingBox(west, 39, north, direction, 58, 23, 58));
            this.setOrientation(direction);
            List<OceanMonumentPieces.RoomDefinition> list = this.generateRoomGraph(random);

            this.sourceRoom.claimed = true;
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentEntryRoom(direction, this.sourceRoom));
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentCoreRoom(direction, this.coreRoom));
            List<OceanMonumentPieces.MonumentRoomFitter> list1 = Lists.newArrayList();

            list1.add(new OceanMonumentPieces.FitDoubleXYRoom());
            list1.add(new OceanMonumentPieces.FitDoubleYZRoom());
            list1.add(new OceanMonumentPieces.FitDoubleZRoom());
            list1.add(new OceanMonumentPieces.FitDoubleXRoom());
            list1.add(new OceanMonumentPieces.FitDoubleYRoom());
            list1.add(new OceanMonumentPieces.FitSimpleTopRoom());
            list1.add(new OceanMonumentPieces.FitSimpleRoom());

            for (OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition : list) {
                if (!oceanmonumentpieces_roomdefinition.claimed && !oceanmonumentpieces_roomdefinition.isSpecial()) {
                    for (OceanMonumentPieces.MonumentRoomFitter oceanmonumentpieces_monumentroomfitter : list1) {
                        if (oceanmonumentpieces_monumentroomfitter.fits(oceanmonumentpieces_roomdefinition)) {
                            this.childPieces.add(oceanmonumentpieces_monumentroomfitter.create(direction, oceanmonumentpieces_roomdefinition, random));
                            break;
                        }
                    }
                }
            }

            BlockPos blockpos = this.getWorldPos(9, 0, 22);

            for (OceanMonumentPieces.OceanMonumentPiece oceanmonumentpieces_oceanmonumentpiece : this.childPieces) {
                oceanmonumentpieces_oceanmonumentpiece.getBoundingBox().move(blockpos);
            }

            BoundingBox boundingbox = BoundingBox.fromCorners(this.getWorldPos(1, 1, 1), this.getWorldPos(23, 8, 21));
            BoundingBox boundingbox1 = BoundingBox.fromCorners(this.getWorldPos(34, 1, 1), this.getWorldPos(56, 8, 21));
            BoundingBox boundingbox2 = BoundingBox.fromCorners(this.getWorldPos(22, 13, 22), this.getWorldPos(35, 17, 35));
            int k = random.nextInt();

            this.childPieces.add(new OceanMonumentPieces.OceanMonumentWingRoom(direction, boundingbox, k++));
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentWingRoom(direction, boundingbox1, k++));
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentPenthouse(direction, boundingbox2));
        }

        public MonumentBuilding(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_BUILDING, tag);
        }

        private List<OceanMonumentPieces.RoomDefinition> generateRoomGraph(RandomSource random) {
            OceanMonumentPieces.RoomDefinition[] aoceanmonumentpieces_roomdefinition = new OceanMonumentPieces.RoomDefinition[75];

            for (int i = 0; i < 5; ++i) {
                for (int j = 0; j < 4; ++j) {
                    int k = 0;
                    int l = getRoomIndex(i, 0, j);

                    aoceanmonumentpieces_roomdefinition[l] = new OceanMonumentPieces.RoomDefinition(l);
                }
            }

            for (int i1 = 0; i1 < 5; ++i1) {
                for (int j1 = 0; j1 < 4; ++j1) {
                    int k1 = 1;
                    int l1 = getRoomIndex(i1, 1, j1);

                    aoceanmonumentpieces_roomdefinition[l1] = new OceanMonumentPieces.RoomDefinition(l1);
                }
            }

            for (int i2 = 1; i2 < 4; ++i2) {
                for (int j2 = 0; j2 < 2; ++j2) {
                    int k2 = 2;
                    int l2 = getRoomIndex(i2, 2, j2);

                    aoceanmonumentpieces_roomdefinition[l2] = new OceanMonumentPieces.RoomDefinition(l2);
                }
            }

            this.sourceRoom = aoceanmonumentpieces_roomdefinition[OceanMonumentPieces.MonumentBuilding.GRIDROOM_SOURCE_INDEX];

            for (int i3 = 0; i3 < 5; ++i3) {
                for (int j3 = 0; j3 < 5; ++j3) {
                    for (int k3 = 0; k3 < 3; ++k3) {
                        int l3 = getRoomIndex(i3, k3, j3);

                        if (aoceanmonumentpieces_roomdefinition[l3] != null) {
                            for (Direction direction : Direction.values()) {
                                int i4 = i3 + direction.getStepX();
                                int j4 = k3 + direction.getStepY();
                                int k4 = j3 + direction.getStepZ();

                                if (i4 >= 0 && i4 < 5 && k4 >= 0 && k4 < 5 && j4 >= 0 && j4 < 3) {
                                    int l4 = getRoomIndex(i4, j4, k4);

                                    if (aoceanmonumentpieces_roomdefinition[l4] != null) {
                                        if (k4 == j3) {
                                            aoceanmonumentpieces_roomdefinition[l3].setConnection(direction, aoceanmonumentpieces_roomdefinition[l4]);
                                        } else {
                                            aoceanmonumentpieces_roomdefinition[l3].setConnection(direction.getOpposite(), aoceanmonumentpieces_roomdefinition[l4]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition = new OceanMonumentPieces.RoomDefinition(1003);
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = new OceanMonumentPieces.RoomDefinition(1001);
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition2 = new OceanMonumentPieces.RoomDefinition(1002);

            aoceanmonumentpieces_roomdefinition[OceanMonumentPieces.MonumentBuilding.GRIDROOM_TOP_CONNECT_INDEX].setConnection(Direction.UP, oceanmonumentpieces_roomdefinition);
            aoceanmonumentpieces_roomdefinition[OceanMonumentPieces.MonumentBuilding.GRIDROOM_LEFTWING_CONNECT_INDEX].setConnection(Direction.SOUTH, oceanmonumentpieces_roomdefinition1);
            aoceanmonumentpieces_roomdefinition[OceanMonumentPieces.MonumentBuilding.GRIDROOM_RIGHTWING_CONNECT_INDEX].setConnection(Direction.SOUTH, oceanmonumentpieces_roomdefinition2);
            oceanmonumentpieces_roomdefinition.claimed = true;
            oceanmonumentpieces_roomdefinition1.claimed = true;
            oceanmonumentpieces_roomdefinition2.claimed = true;
            this.sourceRoom.isSource = true;
            this.coreRoom = aoceanmonumentpieces_roomdefinition[getRoomIndex(random.nextInt(4), 0, 2)];
            this.coreRoom.claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.NORTH.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].connections[Direction.NORTH.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.UP.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.NORTH.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].connections[Direction.NORTH.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            ObjectArrayList<OceanMonumentPieces.RoomDefinition> objectarraylist = new ObjectArrayList();

            for (OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition3 : aoceanmonumentpieces_roomdefinition) {
                if (oceanmonumentpieces_roomdefinition3 != null) {
                    oceanmonumentpieces_roomdefinition3.updateOpenings();
                    objectarraylist.add(oceanmonumentpieces_roomdefinition3);
                }
            }

            oceanmonumentpieces_roomdefinition.updateOpenings();
            Util.shuffle(objectarraylist, random);
            int i5 = 1;
            ObjectListIterator objectlistiterator = objectarraylist.iterator();

            while (objectlistiterator.hasNext()) {
                OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition4 = (OceanMonumentPieces.RoomDefinition) objectlistiterator.next();
                int j5 = 0;
                int k5 = 0;

                while (j5 < 2 && k5 < 5) {
                    ++k5;
                    int l5 = random.nextInt(6);

                    if (oceanmonumentpieces_roomdefinition4.hasOpening[l5]) {
                        int i6 = Direction.from3DDataValue(l5).getOpposite().get3DDataValue();

                        oceanmonumentpieces_roomdefinition4.hasOpening[l5] = false;
                        oceanmonumentpieces_roomdefinition4.connections[l5].hasOpening[i6] = false;
                        if (oceanmonumentpieces_roomdefinition4.findSource(i5++) && oceanmonumentpieces_roomdefinition4.connections[l5].findSource(i5++)) {
                            ++j5;
                        } else {
                            oceanmonumentpieces_roomdefinition4.hasOpening[l5] = true;
                            oceanmonumentpieces_roomdefinition4.connections[l5].hasOpening[i6] = true;
                        }
                    }
                }
            }

            objectarraylist.add(oceanmonumentpieces_roomdefinition);
            objectarraylist.add(oceanmonumentpieces_roomdefinition1);
            objectarraylist.add(oceanmonumentpieces_roomdefinition2);
            return objectarraylist;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            int i = Math.max(level.getSeaLevel(), 64) - this.boundingBox.minY();

            this.generateWaterBox(level, chunkBB, 0, 0, 0, 58, i, 58);
            this.generateWing(false, 0, level, random, chunkBB);
            this.generateWing(true, 33, level, random, chunkBB);
            this.generateEntranceArchs(level, random, chunkBB);
            this.generateEntranceWall(level, random, chunkBB);
            this.generateRoofPiece(level, random, chunkBB);
            this.generateLowerWall(level, random, chunkBB);
            this.generateMiddleWall(level, random, chunkBB);
            this.generateUpperWall(level, random, chunkBB);

            for (int j = 0; j < 7; ++j) {
                int k = 0;

                while (k < 7) {
                    if (k == 0 && j == 3) {
                        k = 6;
                    }

                    int l = j * 9;
                    int i1 = k * 9;

                    for (int j1 = 0; j1 < 4; ++j1) {
                        for (int k1 = 0; k1 < 4; ++k1) {
                            this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, l + j1, 0, i1 + k1, chunkBB);
                            this.fillColumnDown(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, l + j1, -1, i1 + k1, chunkBB);
                        }
                    }

                    if (j != 0 && j != 6) {
                        k += 6;
                    } else {
                        ++k;
                    }
                }
            }

            for (int l1 = 0; l1 < 5; ++l1) {
                this.generateWaterBox(level, chunkBB, -1 - l1, 0 + l1 * 2, -1 - l1, -1 - l1, 23, 58 + l1);
                this.generateWaterBox(level, chunkBB, 58 + l1, 0 + l1 * 2, -1 - l1, 58 + l1, 23, 58 + l1);
                this.generateWaterBox(level, chunkBB, 0 - l1, 0 + l1 * 2, -1 - l1, 57 + l1, 23, -1 - l1);
                this.generateWaterBox(level, chunkBB, 0 - l1, 0 + l1 * 2, 58 + l1, 57 + l1, 23, 58 + l1);
            }

            for (OceanMonumentPieces.OceanMonumentPiece oceanmonumentpieces_oceanmonumentpiece : this.childPieces) {
                if (oceanmonumentpieces_oceanmonumentpiece.getBoundingBox().intersects(chunkBB)) {
                    oceanmonumentpieces_oceanmonumentpiece.postProcess(level, structureManager, generator, random, chunkBB, chunkPos, referencePos);
                }
            }

        }

        private void generateWing(boolean isFlipped, int xoff, WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            int j = 24;

            if (this.chunkIntersects(chunkBB, xoff, 0, xoff + 23, 20)) {
                this.generateBox(level, chunkBB, xoff + 0, 0, 0, xoff + 24, 0, 20, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, xoff + 0, 1, 0, xoff + 24, 10, 20);

                for (int k = 0; k < 4; ++k) {
                    this.generateBox(level, chunkBB, xoff + k, k + 1, k, xoff + k, k + 1, 20, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + k + 7, k + 5, k + 7, xoff + k + 7, k + 5, 20, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + 17 - k, k + 5, k + 7, xoff + 17 - k, k + 5, 20, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + 24 - k, k + 1, k, xoff + 24 - k, k + 1, 20, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + k + 1, k + 1, k, xoff + 23 - k, k + 1, k, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + k + 8, k + 5, k + 7, xoff + 16 - k, k + 5, k + 7, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, xoff + 4, 4, 4, xoff + 6, 4, 20, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 7, 4, 4, xoff + 17, 4, 6, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 18, 4, 4, xoff + 20, 4, 20, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 11, 8, 11, xoff + 13, 8, 20, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, xoff + 12, 9, 12, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, xoff + 12, 9, 15, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, xoff + 12, 9, 18, chunkBB);
                int l = xoff + (isFlipped ? 19 : 5);
                int i1 = xoff + (isFlipped ? 5 : 19);

                for (int j1 = 20; j1 >= 5; j1 -= 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, l, 5, j1, chunkBB);
                }

                for (int k1 = 19; k1 >= 7; k1 -= 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 5, k1, chunkBB);
                }

                for (int l1 = 0; l1 < 4; ++l1) {
                    int i2 = isFlipped ? xoff + 24 - (17 - l1 * 3) : xoff + 17 - l1 * 3;

                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i2, 5, 5, chunkBB);
                }

                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 5, 5, chunkBB);
                this.generateBox(level, chunkBB, xoff + 11, 1, 12, xoff + 13, 7, 12, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 12, 1, 11, xoff + 12, 7, 13, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
            }

        }

        private void generateEntranceArchs(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 22, 5, 35, 17)) {
                this.generateWaterBox(level, chunkBB, 25, 0, 0, 32, 8, 20);

                for (int i = 0; i < 4; ++i) {
                    this.generateBox(level, chunkBB, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 25, 5, 5 + i * 4, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 26, 6, 5 + i * 4, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.LAMP_BLOCK, 26, 5, 5 + i * 4, chunkBB);
                    this.generateBox(level, chunkBB, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 32, 5, 5 + i * 4, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 31, 6, 5 + i * 4, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.LAMP_BLOCK, 31, 5, 5 + i * 4, chunkBB);
                    this.generateBox(level, chunkBB, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                }
            }

        }

        private void generateEntranceWall(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 15, 20, 42, 21)) {
                this.generateBox(level, chunkBB, 15, 0, 21, 42, 0, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 26, 1, 21, 31, 3, 21);
                this.generateBox(level, chunkBB, 21, 12, 21, 36, 12, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 17, 11, 21, 40, 11, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 16, 10, 21, 41, 10, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 15, 7, 21, 42, 9, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 16, 6, 21, 41, 6, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 17, 5, 21, 40, 5, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 21, 4, 21, 36, 4, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 22, 3, 21, 26, 3, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 31, 3, 21, 35, 3, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 23, 2, 21, 25, 2, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 32, 2, 21, 34, 2, 21, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 28, 4, 20, 29, 4, 21, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 27, 3, 21, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 30, 3, 21, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 26, 2, 21, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 31, 2, 21, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 25, 1, 21, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 32, 1, 21, chunkBB);

                for (int i = 0; i < 7; ++i) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 28 - i, 6 + i, 21, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 29 + i, 6 + i, 21, chunkBB);
                }

                for (int j = 0; j < 4; ++j) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 28 - j, 9 + j, 21, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 29 + j, 9 + j, 21, chunkBB);
                }

                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 28, 12, 21, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 29, 12, 21, chunkBB);

                for (int k = 0; k < 3; ++k) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 22 - k * 2, 8, 21, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 22 - k * 2, 9, 21, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 35 + k * 2, 8, 21, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_BLACK, 35 + k * 2, 9, 21, chunkBB);
                }

                this.generateWaterBox(level, chunkBB, 15, 13, 21, 42, 15, 21);
                this.generateWaterBox(level, chunkBB, 15, 1, 21, 15, 6, 21);
                this.generateWaterBox(level, chunkBB, 16, 1, 21, 16, 5, 21);
                this.generateWaterBox(level, chunkBB, 17, 1, 21, 20, 4, 21);
                this.generateWaterBox(level, chunkBB, 21, 1, 21, 21, 3, 21);
                this.generateWaterBox(level, chunkBB, 22, 1, 21, 22, 2, 21);
                this.generateWaterBox(level, chunkBB, 23, 1, 21, 24, 1, 21);
                this.generateWaterBox(level, chunkBB, 42, 1, 21, 42, 6, 21);
                this.generateWaterBox(level, chunkBB, 41, 1, 21, 41, 5, 21);
                this.generateWaterBox(level, chunkBB, 37, 1, 21, 40, 4, 21);
                this.generateWaterBox(level, chunkBB, 36, 1, 21, 36, 3, 21);
                this.generateWaterBox(level, chunkBB, 33, 1, 21, 34, 1, 21);
                this.generateWaterBox(level, chunkBB, 35, 1, 21, 35, 2, 21);
            }

        }

        private void generateRoofPiece(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 21, 21, 36, 36)) {
                this.generateBox(level, chunkBB, 21, 0, 22, 36, 0, 36, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 21, 1, 22, 36, 23, 36);

                for (int i = 0; i < 4; ++i) {
                    this.generateBox(level, chunkBB, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i, 21 + i, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i, 36 - i, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i, 35 - i, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i, 35 - i, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, 25, 16, 25, 32, 16, 32, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 25, 17, 25, 25, 19, 25, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 32, 17, 25, 32, 19, 25, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 25, 17, 32, 25, 19, 32, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 32, 17, 32, 32, 19, 32, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 26, 20, 26, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 27, 21, 27, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.LAMP_BLOCK, 27, 20, 27, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 26, 20, 31, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 27, 21, 30, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.LAMP_BLOCK, 27, 20, 30, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 31, 20, 31, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 30, 21, 30, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.LAMP_BLOCK, 30, 20, 30, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 31, 20, 26, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, 30, 21, 27, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.LAMP_BLOCK, 30, 20, 27, chunkBB);
                this.generateBox(level, chunkBB, 28, 21, 27, 29, 21, 27, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 27, 21, 28, 27, 21, 29, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 28, 21, 30, 29, 21, 30, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 30, 21, 28, 30, 21, 29, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
            }

        }

        private void generateLowerWall(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 0, 21, 6, 58)) {
                this.generateBox(level, chunkBB, 0, 0, 21, 6, 0, 57, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 0, 1, 21, 6, 7, 57);
                this.generateBox(level, chunkBB, 4, 4, 21, 6, 4, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);

                for (int i = 0; i < 4; ++i) {
                    this.generateBox(level, chunkBB, i, i + 1, 21, i, i + 1, 57 - i, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int j = 23; j < 53; j += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 5, 5, j, chunkBB);
                }

                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 5, 5, 52, chunkBB);

                for (int k = 0; k < 4; ++k) {
                    this.generateBox(level, chunkBB, k, k + 1, 21, k, k + 1, 57 - k, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, 4, 1, 52, 6, 3, 52, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 5, 1, 51, 5, 3, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
            }

            if (this.chunkIntersects(chunkBB, 51, 21, 58, 58)) {
                this.generateBox(level, chunkBB, 51, 0, 21, 57, 0, 57, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 51, 1, 21, 57, 7, 57);
                this.generateBox(level, chunkBB, 51, 4, 21, 53, 4, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);

                for (int l = 0; l < 4; ++l) {
                    this.generateBox(level, chunkBB, 57 - l, l + 1, 21, 57 - l, l + 1, 57 - l, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int i1 = 23; i1 < 53; i1 += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 52, 5, i1, chunkBB);
                }

                this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 52, 5, 52, chunkBB);
                this.generateBox(level, chunkBB, 51, 1, 52, 53, 3, 52, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 52, 1, 51, 52, 3, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
            }

            if (this.chunkIntersects(chunkBB, 0, 51, 57, 57)) {
                this.generateBox(level, chunkBB, 7, 0, 51, 50, 0, 57, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 7, 1, 51, 50, 10, 57);

                for (int j1 = 0; j1 < 4; ++j1) {
                    this.generateBox(level, chunkBB, j1 + 1, j1 + 1, 57 - j1, 56 - j1, j1 + 1, 57 - j1, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }
            }

        }

        private void generateMiddleWall(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 7, 21, 13, 50)) {
                this.generateBox(level, chunkBB, 7, 0, 21, 13, 0, 50, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 7, 1, 21, 13, 10, 50);
                this.generateBox(level, chunkBB, 11, 8, 21, 13, 8, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);

                for (int i = 0; i < 4; ++i) {
                    this.generateBox(level, chunkBB, i + 7, i + 5, 21, i + 7, i + 5, 54, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int j = 21; j <= 45; j += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 12, 9, j, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 44, 21, 50, 54)) {
                this.generateBox(level, chunkBB, 44, 0, 21, 50, 0, 50, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 44, 1, 21, 50, 10, 50);
                this.generateBox(level, chunkBB, 44, 8, 21, 46, 8, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);

                for (int k = 0; k < 4; ++k) {
                    this.generateBox(level, chunkBB, 50 - k, k + 5, 21, 50 - k, k + 5, 54, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int l = 21; l <= 45; l += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 45, 9, l, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 8, 44, 49, 54)) {
                this.generateBox(level, chunkBB, 14, 0, 44, 43, 0, 50, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 14, 1, 44, 43, 10, 50);

                for (int i1 = 12; i1 <= 45; i1 += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 9, 45, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 9, 52, chunkBB);
                    if (i1 == 12 || i1 == 18 || i1 == 24 || i1 == 33 || i1 == 39 || i1 == 45) {
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 9, 47, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 9, 50, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 10, 45, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 10, 46, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 10, 51, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 10, 52, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 11, 47, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 11, 50, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 12, 48, chunkBB);
                        this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, i1, 12, 49, chunkBB);
                    }
                }

                for (int j1 = 0; j1 < 3; ++j1) {
                    this.generateBox(level, chunkBB, 8 + j1, 5 + j1, 54, 49 - j1, 5 + j1, 54, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                }

                this.generateBox(level, chunkBB, 11, 8, 54, 46, 8, 54, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 14, 8, 44, 43, 8, 53, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
            }

        }

        private void generateUpperWall(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 14, 21, 20, 43)) {
                this.generateBox(level, chunkBB, 14, 0, 21, 20, 0, 43, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 14, 1, 22, 20, 14, 43);
                this.generateBox(level, chunkBB, 18, 12, 22, 20, 12, 39, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 18, 12, 21, 20, 12, 21, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);

                for (int i = 0; i < 4; ++i) {
                    this.generateBox(level, chunkBB, i + 14, i + 9, 21, i + 14, i + 9, 43 - i, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int j = 23; j <= 39; j += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 19, 13, j, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 37, 21, 43, 43)) {
                this.generateBox(level, chunkBB, 37, 0, 21, 43, 0, 43, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 37, 1, 22, 43, 14, 43);
                this.generateBox(level, chunkBB, 37, 12, 22, 39, 12, 39, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 37, 12, 21, 39, 12, 21, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);

                for (int k = 0; k < 4; ++k) {
                    this.generateBox(level, chunkBB, 43 - k, k + 9, 21, 43 - k, k + 9, 43 - k, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int l = 23; l <= 39; l += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, 38, 13, l, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 15, 37, 42, 43)) {
                this.generateBox(level, chunkBB, 21, 0, 37, 36, 0, 43, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 21, 1, 37, 36, 14, 43);
                this.generateBox(level, chunkBB, 21, 12, 37, 36, 12, 39, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, OceanMonumentPieces.MonumentBuilding.BASE_GRAY, false);

                for (int i1 = 0; i1 < 4; ++i1) {
                    this.generateBox(level, chunkBB, 15 + i1, i1 + 9, 43 - i1, 42 - i1, i1 + 9, 43 - i1, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, OceanMonumentPieces.MonumentBuilding.BASE_LIGHT, false);
                }

                for (int j1 = 21; j1 <= 36; j1 += 3) {
                    this.placeBlock(level, OceanMonumentPieces.MonumentBuilding.DOT_DECO_DATA, j1, 13, 38, chunkBB);
                }
            }

        }
    }

    public static class OceanMonumentEntryRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentEntryRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_ENTRY_ROOM, 1, orientation, definition, 1, 1, 1);
        }

        public OceanMonumentEntryRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_ENTRY_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 0, 3, 0, 2, 3, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 3, 0, 7, 3, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 1, 2, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 2, 0, 7, 2, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 1, 7, 7, 3, 7, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 2, 3, 0, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 0, 6, 3, 0, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentEntryRoom.BASE_LIGHT, false);
            if (this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
            }

            if (this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 1, 2, 4);
            }

            if (this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 6, 1, 3, 7, 2, 4);
            }

        }
    }

    public static class OceanMonumentSimpleRoom extends OceanMonumentPieces.OceanMonumentPiece {

        private int mainDesign;

        public OceanMonumentSimpleRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_ROOM, 1, orientation, definition, 1, 1, 1);
            this.mainDesign = random.nextInt(3);
        }

        public OceanMonumentSimpleRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (this.roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 6, 4, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY);
            }

            boolean flag = this.mainDesign != 0 && random.nextBoolean() && !this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()] && !this.roomDefinition.hasOpening[Direction.UP.get3DDataValue()] && this.roomDefinition.countOpenings() > 1;

            if (this.mainDesign == 0) {
                this.generateBox(level, chunkBB, 0, 1, 0, 2, 1, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 3, 0, 2, 3, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 1, 2, 0, 2, 2, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 1, 2, 1, chunkBB);
                this.generateBox(level, chunkBB, 5, 1, 0, 7, 1, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 3, 0, 7, 3, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 5, 2, 0, 6, 2, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 6, 2, 1, chunkBB);
                this.generateBox(level, chunkBB, 0, 1, 5, 2, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 3, 5, 2, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 2, 5, 0, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 1, 2, 7, 2, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 1, 2, 6, chunkBB);
                this.generateBox(level, chunkBB, 5, 1, 5, 7, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 3, 5, 7, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 2, 5, 7, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 5, 2, 7, 6, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 6, 2, 6, chunkBB);
                if (this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 3, 3, 0, 4, 3, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 3, 3, 0, 4, 3, 1, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, 2, 0, 4, 2, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 3, 1, 0, 4, 1, 1, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 3, 3, 7, 4, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 3, 3, 6, 4, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, 2, 7, 4, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 3, 1, 6, 4, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 0, 3, 3, 0, 3, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 0, 3, 3, 1, 3, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, 2, 3, 0, 2, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 0, 1, 3, 1, 1, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 7, 3, 3, 7, 3, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 6, 3, 3, 7, 3, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 7, 2, 3, 7, 2, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 6, 1, 3, 7, 1, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }
            } else if (this.mainDesign == 1) {
                this.generateBox(level, chunkBB, 2, 1, 2, 2, 3, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 2, 1, 5, 2, 3, 5, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 1, 5, 5, 3, 5, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 1, 2, 5, 3, 2, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 2, 2, 2, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 2, 2, 5, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 5, 2, 5, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.LAMP_BLOCK, 5, 2, 2, chunkBB);
                this.generateBox(level, chunkBB, 0, 1, 0, 1, 3, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 1, 0, 3, 1, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 7, 1, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 6, 0, 3, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 7, 7, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 6, 7, 3, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 0, 7, 3, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 1, 7, 3, 1, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 1, 2, 0, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 0, 2, 1, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 1, 2, 7, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 0, 2, 6, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 6, 2, 7, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 7, 2, 6, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 6, 2, 0, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, 7, 2, 1, chunkBB);
                if (!this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 1, 3, 0, 6, 3, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 1, 2, 0, 6, 2, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 1, 1, 0, 6, 1, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 1, 3, 7, 6, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 1, 2, 7, 6, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 1, 1, 7, 6, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 0, 3, 1, 0, 3, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, 2, 1, 0, 2, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 0, 1, 1, 0, 1, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 7, 3, 1, 7, 3, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 7, 2, 1, 7, 2, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 7, 1, 1, 7, 1, 6, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                }
            } else if (this.mainDesign == 2) {
                this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 0, 6, 1, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 7, 6, 1, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 1, 2, 0, 6, 2, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 1, 2, 7, 6, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 3, 0, 7, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 3, 0, 6, 3, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 3, 7, 6, 3, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 3, 0, 2, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 7, 1, 3, 7, 2, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 3, 1, 0, 4, 2, 0, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 3, 1, 7, 4, 2, 7, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_BLACK, false);
                if (this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
                }

                if (this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
                }

                if (this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
                }

                if (this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateWaterBox(level, chunkBB, 7, 1, 3, 7, 2, 4);
                }
            }

            if (flag) {
                this.generateBox(level, chunkBB, 3, 1, 3, 4, 1, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 3, 2, 3, 4, 2, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_GRAY, false);
                this.generateBox(level, chunkBB, 3, 3, 3, 4, 3, 4, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleRoom.BASE_LIGHT, false);
            }

        }
    }

    public static class OceanMonumentSimpleTopRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentSimpleTopRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_TOP_ROOM, 1, orientation, definition, 1, 1, 1);
        }

        public OceanMonumentSimpleTopRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_TOP_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (this.roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 6, 4, 6, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_GRAY);
            }

            for (int i = 1; i <= 6; ++i) {
                for (int j = 1; j <= 6; ++j) {
                    if (random.nextInt(3) != 0) {
                        int k = 2 + (random.nextInt(4) == 0 ? 0 : 1);
                        BlockState blockstate = Blocks.WET_SPONGE.defaultBlockState();

                        this.generateBox(level, chunkBB, i, k, j, i, 3, j, blockstate, blockstate, false);
                    }
                }
            }

            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 6, 1, 0, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 7, 6, 1, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 6, 2, 0, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 1, 2, 7, 6, 2, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 3, 0, 7, 3, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 6, 3, 0, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 7, 6, 3, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 1, 3, 0, 2, 4, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 7, 1, 3, 7, 2, 4, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 3, 1, 0, 4, 2, 0, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 3, 1, 7, 4, 2, 7, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentSimpleTopRoom.BASE_BLACK, false);
            if (this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

        }
    }

    public static class OceanMonumentDoubleYRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentDoubleYRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Y_ROOM, 1, orientation, definition, 1, 2, 1);
        }

        public OceanMonumentDoubleYRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Y_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition = this.roomDefinition.connections[Direction.UP.get3DDataValue()];

            if (oceanmonumentpieces_roomdefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 1, 6, 8, 6, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY);
            }

            this.generateBox(level, chunkBB, 0, 4, 0, 0, 4, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 4, 0, 7, 4, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 0, 6, 4, 0, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 7, 6, 4, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 4, 1, 2, 4, 2, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 2, 1, 4, 2, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 4, 1, 5, 4, 2, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 4, 2, 6, 4, 2, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 4, 5, 2, 4, 6, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 5, 1, 4, 5, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 4, 5, 5, 4, 6, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 4, 5, 6, 4, 5, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = this.roomDefinition;

            for (int i = 1; i <= 5; i += 4) {
                int j = 0;

                if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 2, i, j, 2, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 5, i, j, 5, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, i + 2, j, 4, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 0, i, j, 7, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, i + 1, j, 7, i + 1, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, false);
                }

                j = 7;
                if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, 2, i, j, 2, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 5, i, j, 5, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, i + 2, j, 4, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 0, i, j, 7, i + 2, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, i + 1, j, 7, i + 1, j, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, false);
                }

                int k = 0;

                if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, k, i, 2, k, i + 2, 2, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, i, 5, k, i + 2, 5, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, i + 2, 3, k, i + 2, 4, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, k, i, 0, k, i + 2, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, i + 1, 0, k, i + 1, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, false);
                }

                k = 7;
                if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateBox(level, chunkBB, k, i, 2, k, i + 2, 2, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, i, 5, k, i + 2, 5, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, i + 2, 3, k, i + 2, 4, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, k, i, 0, k, i + 2, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, i + 1, 0, k, i + 1, 7, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleYRoom.BASE_GRAY, false);
                }

                oceanmonumentpieces_roomdefinition1 = oceanmonumentpieces_roomdefinition;
            }

        }
    }

    public static class OceanMonumentDoubleXRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentDoubleXRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_X_ROOM, 1, orientation, definition, 2, 1, 1);
        }

        public OceanMonumentDoubleXRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_X_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition = this.roomDefinition.connections[Direction.EAST.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = this.roomDefinition;

            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 8, 0, oceanmonumentpieces_roomdefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, oceanmonumentpieces_roomdefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (oceanmonumentpieces_roomdefinition1.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 7, 4, 6, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY);
            }

            if (oceanmonumentpieces_roomdefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 8, 4, 1, 14, 4, 6, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY);
            }

            this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 15, 3, 0, 15, 3, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 15, 3, 0, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 7, 14, 3, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 15, 2, 0, 15, 2, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 15, 2, 0, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 7, 14, 2, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 15, 1, 0, 15, 1, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 15, 1, 0, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 7, 14, 1, 7, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 0, 10, 1, 4, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 2, 0, 9, 2, 3, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 5, 3, 0, 10, 3, 4, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXRoom.BASE_LIGHT, false);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXRoom.LAMP_BLOCK, 6, 2, 3, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXRoom.LAMP_BLOCK, 9, 2, 3, chunkBB);
            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 0, 12, 2, 0);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 7, 12, 2, 7);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 15, 1, 3, 15, 2, 4);
            }

        }
    }

    public static class OceanMonumentDoubleZRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentDoubleZRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Z_ROOM, 1, orientation, definition, 1, 1, 2);
        }

        public OceanMonumentDoubleZRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Z_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition = this.roomDefinition.connections[Direction.NORTH.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = this.roomDefinition;

            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 8, oceanmonumentpieces_roomdefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, oceanmonumentpieces_roomdefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (oceanmonumentpieces_roomdefinition1.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 6, 4, 7, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY);
            }

            if (oceanmonumentpieces_roomdefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 8, 6, 4, 14, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY);
            }

            this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 3, 0, 7, 3, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 7, 3, 0, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 15, 6, 3, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 7, 2, 0, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 15, 6, 2, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 7, 1, 0, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 15, 6, 1, 15, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 1, 1, 1, 2, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 1, 6, 1, 2, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 1, 1, 3, 2, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 3, 1, 6, 3, 2, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 13, 1, 1, 14, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 13, 6, 1, 14, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 13, 1, 3, 14, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 3, 13, 6, 3, 14, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 6, 2, 3, 6, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 6, 5, 3, 6, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 9, 2, 3, 9, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 9, 5, 3, 9, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 2, 6, 4, 2, 6, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 2, 9, 4, 2, 9, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 2, 7, 2, 2, 8, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 2, 7, 5, 2, 8, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, false);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.LAMP_BLOCK, 2, 2, 5, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.LAMP_BLOCK, 5, 2, 5, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.LAMP_BLOCK, 2, 2, 10, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.LAMP_BLOCK, 5, 2, 10, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, 2, 3, 5, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, 5, 3, 5, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, 2, 3, 10, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleZRoom.BASE_LIGHT, 5, 3, 10, chunkBB);
            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 3, 7, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 15, 4, 2, 15);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 11, 0, 2, 12);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 11, 7, 2, 12);
            }

        }
    }

    public static class OceanMonumentDoubleXYRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentDoubleXYRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_XY_ROOM, 1, orientation, definition, 2, 2, 1);
        }

        public OceanMonumentDoubleXYRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_XY_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition = this.roomDefinition.connections[Direction.EAST.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = this.roomDefinition;
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition2 = oceanmonumentpieces_roomdefinition1.connections[Direction.UP.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition3 = oceanmonumentpieces_roomdefinition.connections[Direction.UP.get3DDataValue()];

            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 8, 0, oceanmonumentpieces_roomdefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, oceanmonumentpieces_roomdefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (oceanmonumentpieces_roomdefinition2.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 1, 7, 8, 6, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_GRAY);
            }

            if (oceanmonumentpieces_roomdefinition3.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 8, 8, 1, 14, 8, 6, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_GRAY);
            }

            for (int i = 1; i <= 7; ++i) {
                BlockState blockstate = OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT;

                if (i == 2 || i == 6) {
                    blockstate = OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_GRAY;
                }

                this.generateBox(level, chunkBB, 0, i, 0, 0, i, 7, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 15, i, 0, 15, i, 7, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 1, i, 0, 15, i, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 1, i, 7, 14, i, 7, blockstate, blockstate, false);
            }

            this.generateBox(level, chunkBB, 2, 1, 3, 2, 7, 4, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 2, 4, 7, 2, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 5, 4, 7, 5, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 1, 3, 13, 7, 4, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 11, 1, 2, 12, 7, 2, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 11, 1, 5, 12, 7, 5, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 3, 5, 3, 4, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 1, 3, 10, 3, 4, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 7, 2, 10, 7, 5, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 5, 2, 5, 7, 2, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 5, 2, 10, 7, 2, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 5, 5, 5, 7, 5, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 5, 5, 10, 7, 5, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, 6, 6, 2, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, 9, 6, 2, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, 6, 6, 5, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, 9, 6, 5, chunkBB);
            this.generateBox(level, chunkBB, 5, 4, 3, 6, 4, 4, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 4, 3, 10, 4, 4, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleXYRoom.BASE_LIGHT, false);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.LAMP_BLOCK, 5, 4, 2, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.LAMP_BLOCK, 5, 4, 5, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.LAMP_BLOCK, 10, 4, 2, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentDoubleXYRoom.LAMP_BLOCK, 10, 4, 5, chunkBB);
            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 0, 12, 2, 0);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 7, 12, 2, 7);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 15, 1, 3, 15, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition2.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 0, 4, 6, 0);
            }

            if (oceanmonumentpieces_roomdefinition2.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 7, 4, 6, 7);
            }

            if (oceanmonumentpieces_roomdefinition2.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 5, 3, 0, 6, 4);
            }

            if (oceanmonumentpieces_roomdefinition3.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 11, 5, 0, 12, 6, 0);
            }

            if (oceanmonumentpieces_roomdefinition3.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 11, 5, 7, 12, 6, 7);
            }

            if (oceanmonumentpieces_roomdefinition3.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 15, 5, 3, 15, 6, 4);
            }

        }
    }

    public static class OceanMonumentDoubleYZRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentDoubleYZRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_YZ_ROOM, 1, orientation, definition, 1, 2, 2);
        }

        public OceanMonumentDoubleYZRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_YZ_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition = this.roomDefinition.connections[Direction.NORTH.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = this.roomDefinition;
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition2 = oceanmonumentpieces_roomdefinition.connections[Direction.UP.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition3 = oceanmonumentpieces_roomdefinition1.connections[Direction.UP.get3DDataValue()];

            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 8, oceanmonumentpieces_roomdefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, oceanmonumentpieces_roomdefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (oceanmonumentpieces_roomdefinition3.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 1, 6, 8, 7, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_GRAY);
            }

            if (oceanmonumentpieces_roomdefinition2.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 8, 6, 8, 14, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_GRAY);
            }

            for (int i = 1; i <= 7; ++i) {
                BlockState blockstate = OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT;

                if (i == 2 || i == 6) {
                    blockstate = OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_GRAY;
                }

                this.generateBox(level, chunkBB, 0, i, 0, 0, i, 15, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 7, i, 0, 7, i, 15, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 1, i, 0, 6, i, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 1, i, 15, 6, i, 15, blockstate, blockstate, false);
            }

            for (int j = 1; j <= 7; ++j) {
                BlockState blockstate1 = OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_BLACK;

                if (j == 2 || j == 6) {
                    blockstate1 = OceanMonumentPieces.OceanMonumentDoubleYZRoom.LAMP_BLOCK;
                }

                this.generateBox(level, chunkBB, 3, j, 7, 4, j, 8, blockstate1, blockstate1, false);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 3, 7, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 15, 4, 2, 15);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 11, 0, 2, 12);
            }

            if (oceanmonumentpieces_roomdefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 11, 7, 2, 12);
            }

            if (oceanmonumentpieces_roomdefinition3.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 0, 4, 6, 0);
            }

            if (oceanmonumentpieces_roomdefinition3.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 7, 5, 3, 7, 6, 4);
                this.generateBox(level, chunkBB, 5, 4, 2, 6, 4, 5, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 2, 6, 3, 2, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 5, 6, 3, 5, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
            }

            if (oceanmonumentpieces_roomdefinition3.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 5, 3, 0, 6, 4);
                this.generateBox(level, chunkBB, 1, 4, 2, 2, 4, 5, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 2, 1, 3, 2, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 5, 1, 3, 5, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
            }

            if (oceanmonumentpieces_roomdefinition2.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 15, 4, 6, 15);
            }

            if (oceanmonumentpieces_roomdefinition2.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 0, 5, 11, 0, 6, 12);
                this.generateBox(level, chunkBB, 1, 4, 10, 2, 4, 13, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 10, 1, 3, 10, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 13, 1, 3, 13, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
            }

            if (oceanmonumentpieces_roomdefinition2.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, chunkBB, 7, 5, 11, 7, 6, 12);
                this.generateBox(level, chunkBB, 5, 4, 10, 6, 4, 13, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 10, 6, 3, 10, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 13, 6, 3, 13, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentDoubleYZRoom.BASE_LIGHT, false);
            }

        }
    }

    public static class OceanMonumentCoreRoom extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentCoreRoom(Direction orientation, OceanMonumentPieces.RoomDefinition definition) {
            super(StructurePieceType.OCEAN_MONUMENT_CORE_ROOM, 1, orientation, definition, 2, 2, 2);
        }

        public OceanMonumentCoreRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_CORE_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 0, 14, 8, 14, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_GRAY);
            int i = 7;
            BlockState blockstate = OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT;

            this.generateBox(level, chunkBB, 0, 7, 0, 0, 7, 15, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 15, 7, 0, 15, 7, 15, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 1, 7, 0, 15, 7, 0, blockstate, blockstate, false);
            this.generateBox(level, chunkBB, 1, 7, 15, 14, 7, 15, blockstate, blockstate, false);

            for (int j = 1; j <= 6; ++j) {
                blockstate = OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT;
                if (j == 2 || j == 6) {
                    blockstate = OceanMonumentPieces.OceanMonumentCoreRoom.BASE_GRAY;
                }

                for (int k = 0; k <= 15; k += 15) {
                    this.generateBox(level, chunkBB, k, j, 0, k, j, 1, blockstate, blockstate, false);
                    this.generateBox(level, chunkBB, k, j, 6, k, j, 9, blockstate, blockstate, false);
                    this.generateBox(level, chunkBB, k, j, 14, k, j, 15, blockstate, blockstate, false);
                }

                this.generateBox(level, chunkBB, 1, j, 0, 1, j, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 6, j, 0, 9, j, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 14, j, 0, 14, j, 0, blockstate, blockstate, false);
                this.generateBox(level, chunkBB, 1, j, 15, 14, j, 15, blockstate, blockstate, false);
            }

            this.generateBox(level, chunkBB, 6, 3, 6, 9, 6, 9, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 7, 4, 7, 8, 5, 8, Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), false);

            for (int l = 3; l <= 6; l += 3) {
                for (int i1 = 6; i1 <= 9; i1 += 3) {
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentCoreRoom.LAMP_BLOCK, i1, l, 6, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentCoreRoom.LAMP_BLOCK, i1, l, 9, chunkBB);
                }
            }

            this.generateBox(level, chunkBB, 5, 1, 6, 5, 2, 6, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 9, 5, 2, 9, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 1, 6, 10, 2, 6, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 1, 9, 10, 2, 9, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 5, 6, 2, 5, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 1, 5, 9, 2, 5, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 10, 6, 2, 10, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 1, 10, 9, 2, 10, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 2, 5, 5, 6, 5, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 2, 10, 5, 6, 10, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 2, 5, 10, 6, 5, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 2, 10, 10, 6, 10, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 7, 1, 5, 7, 6, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 7, 1, 10, 7, 6, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 7, 9, 5, 7, 14, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 7, 9, 10, 7, 14, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 7, 5, 6, 7, 5, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 7, 10, 6, 7, 10, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 7, 5, 14, 7, 5, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 7, 10, 14, 7, 10, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 2, 2, 1, 3, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 2, 3, 1, 2, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 1, 2, 13, 1, 3, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 12, 1, 2, 12, 1, 2, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 12, 2, 1, 13, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 13, 3, 1, 13, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 1, 12, 13, 1, 13, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 12, 1, 13, 12, 1, 13, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentCoreRoom.BASE_LIGHT, false);
        }
    }

    public static class OceanMonumentWingRoom extends OceanMonumentPieces.OceanMonumentPiece {

        private int mainDesign;

        public OceanMonumentWingRoom(Direction orientation, BoundingBox boundingBox, int randomValue) {
            super(StructurePieceType.OCEAN_MONUMENT_WING_ROOM, orientation, 1, boundingBox);
            this.mainDesign = randomValue & 1;
        }

        public OceanMonumentWingRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_WING_ROOM, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (this.mainDesign == 0) {
                for (int i = 0; i < 4; ++i) {
                    this.generateBox(level, chunkBB, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, 7, 0, 6, 15, 0, 16, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 0, 6, 6, 3, 20, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 16, 0, 6, 16, 3, 20, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 7, 7, 1, 20, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 15, 1, 7, 15, 1, 20, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 6, 9, 3, 6, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 13, 1, 6, 15, 3, 6, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 8, 1, 7, 9, 1, 7, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 13, 1, 7, 14, 1, 7, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 9, 0, 5, 13, 0, 5, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 10, 0, 7, 12, 0, 7, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 8, 0, 10, 8, 0, 12, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 14, 0, 10, 14, 0, 12, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, false);

                for (int j = 18; j >= 7; j -= 3) {
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 6, 3, j, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 16, 3, j, chunkBB);
                }

                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 10, 0, 10, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 12, 0, 10, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 10, 0, 12, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 12, 0, 12, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 8, 3, 6, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 14, 3, 6, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 4, 2, 4, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 4, 1, 4, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 4, 0, 4, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 18, 2, 4, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 18, 1, 4, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 18, 0, 4, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 4, 2, 18, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 4, 1, 18, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 4, 0, 18, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 18, 2, 18, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, 18, 1, 18, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 18, 0, 18, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 9, 7, 20, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, 13, 7, 20, chunkBB);
                this.generateBox(level, chunkBB, 6, 0, 21, 7, 4, 21, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 15, 0, 21, 16, 4, 21, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.spawnElder(level, chunkBB, 11, 2, 16);
            } else if (this.mainDesign == 1) {
                this.generateBox(level, chunkBB, 9, 3, 18, 13, 3, 20, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 9, 0, 18, 9, 2, 18, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 13, 0, 18, 13, 2, 18, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                int k = 9;
                int l = 20;
                int i1 = 5;

                for (int j1 = 0; j1 < 2; ++j1) {
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, k, 6, 20, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, k, 5, 20, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, k, 4, 20, chunkBB);
                    k = 13;
                }

                this.generateBox(level, chunkBB, 7, 3, 7, 15, 3, 14, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                k = 10;

                for (int k1 = 0; k1 < 2; ++k1) {
                    this.generateBox(level, chunkBB, k, 0, 10, k, 6, 10, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, 0, 12, k, 6, 12, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, k, 0, 10, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, k, 0, 12, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, k, 4, 10, chunkBB);
                    this.placeBlock(level, OceanMonumentPieces.OceanMonumentWingRoom.LAMP_BLOCK, k, 4, 12, chunkBB);
                    k = 12;
                }

                k = 8;

                for (int l1 = 0; l1 < 2; ++l1) {
                    this.generateBox(level, chunkBB, k, 0, 7, k, 2, 7, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, k, 0, 14, k, 2, 14, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, OceanMonumentPieces.OceanMonumentWingRoom.BASE_LIGHT, false);
                    k = 14;
                }

                this.generateBox(level, chunkBB, 8, 3, 8, 8, 3, 13, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, false);
                this.generateBox(level, chunkBB, 14, 3, 8, 14, 3, 13, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, OceanMonumentPieces.OceanMonumentWingRoom.BASE_BLACK, false);
                this.spawnElder(level, chunkBB, 11, 5, 13);
            }

        }
    }

    public static class OceanMonumentPenthouse extends OceanMonumentPieces.OceanMonumentPiece {

        public OceanMonumentPenthouse(Direction orientation, BoundingBox boundingBox) {
            super(StructurePieceType.OCEAN_MONUMENT_PENTHOUSE, orientation, 1, boundingBox);
        }

        public OceanMonumentPenthouse(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_PENTHOUSE, tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            this.generateBox(level, chunkBB, 2, -1, 2, 11, -1, 11, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, -1, 0, 1, -1, 11, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 12, -1, 0, 13, -1, 11, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 2, -1, 0, 11, -1, 1, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 2, -1, 12, 11, -1, 13, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, OceanMonumentPieces.OceanMonumentPenthouse.BASE_GRAY, false);
            this.generateBox(level, chunkBB, 0, 0, 0, 0, 0, 13, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 0, 0, 13, 0, 13, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 0, 0, 12, 0, 0, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 0, 13, 12, 0, 13, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);

            for (int i = 2; i <= 11; i += 3) {
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.LAMP_BLOCK, 0, 0, i, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.LAMP_BLOCK, 13, 0, i, chunkBB);
                this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.LAMP_BLOCK, i, 0, 0, chunkBB);
            }

            this.generateBox(level, chunkBB, 2, 0, 3, 4, 0, 9, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 0, 3, 11, 0, 9, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 4, 0, 9, 9, 0, 11, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, 5, 0, 8, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, 8, 0, 8, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, 10, 0, 10, chunkBB);
            this.placeBlock(level, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, 3, 0, 10, chunkBB);
            this.generateBox(level, chunkBB, 3, 0, 3, 3, 0, 7, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 10, 0, 3, 10, 0, 7, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, false);
            this.generateBox(level, chunkBB, 6, 0, 10, 7, 0, 10, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, false);
            int j = 3;

            for (int k = 0; k < 2; ++k) {
                for (int l = 2; l <= 8; l += 3) {
                    this.generateBox(level, chunkBB, j, 0, l, j, 2, l, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
                }

                j = 10;
            }

            this.generateBox(level, chunkBB, 5, 0, 10, 5, 2, 10, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 8, 0, 10, 8, 2, 10, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, OceanMonumentPieces.OceanMonumentPenthouse.BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, -1, 7, 7, -1, 8, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, OceanMonumentPieces.OceanMonumentPenthouse.BASE_BLACK, false);
            this.generateWaterBox(level, chunkBB, 6, -1, 3, 7, -1, 4);
            this.spawnElder(level, chunkBB, 6, 1, 6);
        }
    }

    private static class RoomDefinition {

        private final int index;
        private final OceanMonumentPieces.RoomDefinition[] connections = new OceanMonumentPieces.RoomDefinition[6];
        private final boolean[] hasOpening = new boolean[6];
        private boolean claimed;
        private boolean isSource;
        private int scanIndex;

        public RoomDefinition(int roomIndex) {
            this.index = roomIndex;
        }

        public void setConnection(Direction direction, OceanMonumentPieces.RoomDefinition definition) {
            this.connections[direction.get3DDataValue()] = definition;
            definition.connections[direction.getOpposite().get3DDataValue()] = this;
        }

        public void updateOpenings() {
            for (int i = 0; i < 6; ++i) {
                this.hasOpening[i] = this.connections[i] != null;
            }

        }

        public boolean findSource(int scanIndex) {
            if (this.isSource) {
                return true;
            } else {
                this.scanIndex = scanIndex;

                for (int j = 0; j < 6; ++j) {
                    if (this.connections[j] != null && this.hasOpening[j] && this.connections[j].scanIndex != scanIndex && this.connections[j].findSource(scanIndex)) {
                        return true;
                    }
                }

                return false;
            }
        }

        public boolean isSpecial() {
            return this.index >= 75;
        }

        public int countOpenings() {
            int i = 0;

            for (int j = 0; j < 6; ++j) {
                if (this.hasOpening[j]) {
                    ++i;
                }
            }

            return i;
        }
    }

    private static class FitSimpleRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitSimpleRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            return true;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            return new OceanMonumentPieces.OceanMonumentSimpleRoom(orientation, definition, random);
        }
    }

    private static class FitSimpleTopRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitSimpleTopRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            return !definition.hasOpening[Direction.WEST.get3DDataValue()] && !definition.hasOpening[Direction.EAST.get3DDataValue()] && !definition.hasOpening[Direction.NORTH.get3DDataValue()] && !definition.hasOpening[Direction.SOUTH.get3DDataValue()] && !definition.hasOpening[Direction.UP.get3DDataValue()];
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            return new OceanMonumentPieces.OceanMonumentSimpleTopRoom(orientation, definition);
        }
    }

    private static class FitDoubleYRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitDoubleYRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            return definition.hasOpening[Direction.UP.get3DDataValue()] && !definition.connections[Direction.UP.get3DDataValue()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.UP.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleYRoom(orientation, definition);
        }
    }

    private static class FitDoubleXRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitDoubleXRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            return definition.hasOpening[Direction.EAST.get3DDataValue()] && !definition.connections[Direction.EAST.get3DDataValue()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.EAST.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleXRoom(orientation, definition);
        }
    }

    private static class FitDoubleZRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitDoubleZRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            return definition.hasOpening[Direction.NORTH.get3DDataValue()] && !definition.connections[Direction.NORTH.get3DDataValue()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = definition;

            if (!definition.hasOpening[Direction.NORTH.get3DDataValue()] || definition.connections[Direction.NORTH.get3DDataValue()].claimed) {
                oceanmonumentpieces_roomdefinition1 = definition.connections[Direction.SOUTH.get3DDataValue()];
            }

            oceanmonumentpieces_roomdefinition1.claimed = true;
            oceanmonumentpieces_roomdefinition1.connections[Direction.NORTH.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleZRoom(orientation, oceanmonumentpieces_roomdefinition1);
        }
    }

    private static class FitDoubleXYRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitDoubleXYRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            if (definition.hasOpening[Direction.EAST.get3DDataValue()] && !definition.connections[Direction.EAST.get3DDataValue()].claimed && definition.hasOpening[Direction.UP.get3DDataValue()] && !definition.connections[Direction.UP.get3DDataValue()].claimed) {
                OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = definition.connections[Direction.EAST.get3DDataValue()];

                return oceanmonumentpieces_roomdefinition1.hasOpening[Direction.UP.get3DDataValue()] && !oceanmonumentpieces_roomdefinition1.connections[Direction.UP.get3DDataValue()].claimed;
            } else {
                return false;
            }
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.EAST.get3DDataValue()].claimed = true;
            definition.connections[Direction.UP.get3DDataValue()].claimed = true;
            definition.connections[Direction.EAST.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleXYRoom(orientation, definition);
        }
    }

    private static class FitDoubleYZRoom implements OceanMonumentPieces.MonumentRoomFitter {

        private FitDoubleYZRoom() {}

        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition definition) {
            if (definition.hasOpening[Direction.NORTH.get3DDataValue()] && !definition.connections[Direction.NORTH.get3DDataValue()].claimed && definition.hasOpening[Direction.UP.get3DDataValue()] && !definition.connections[Direction.UP.get3DDataValue()].claimed) {
                OceanMonumentPieces.RoomDefinition oceanmonumentpieces_roomdefinition1 = definition.connections[Direction.NORTH.get3DDataValue()];

                return oceanmonumentpieces_roomdefinition1.hasOpening[Direction.UP.get3DDataValue()] && !oceanmonumentpieces_roomdefinition1.connections[Direction.UP.get3DDataValue()].claimed;
            } else {
                return false;
            }
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.NORTH.get3DDataValue()].claimed = true;
            definition.connections[Direction.UP.get3DDataValue()].claimed = true;
            definition.connections[Direction.NORTH.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleYZRoom(orientation, definition);
        }
    }

    private interface MonumentRoomFitter {

        boolean fits(OceanMonumentPieces.RoomDefinition definition);

        OceanMonumentPieces.OceanMonumentPiece create(Direction orientation, OceanMonumentPieces.RoomDefinition definition, RandomSource random);
    }
}
