package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class WoodlandMansionPieces {

    public WoodlandMansionPieces() {}

    public static void generateMansion(StructureTemplateManager structureTemplateManager, BlockPos origin, Rotation rotation, List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, RandomSource random) {
        WoodlandMansionPieces.MansionGrid woodlandmansionpieces_mansiongrid = new WoodlandMansionPieces.MansionGrid(random);
        WoodlandMansionPieces.MansionPiecePlacer woodlandmansionpieces_mansionpieceplacer = new WoodlandMansionPieces.MansionPiecePlacer(structureTemplateManager, random);

        woodlandmansionpieces_mansionpieceplacer.createMansion(origin, rotation, pieces, woodlandmansionpieces_mansiongrid);
    }

    public static class WoodlandMansionPiece extends TemplateStructurePiece {

        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, String templateName, BlockPos position, Rotation rotation) {
            this(structureTemplateManager, templateName, position, rotation, Mirror.NONE);
        }

        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, String templateName, BlockPos position, Rotation rotation, Mirror mirror) {
            super(StructurePieceType.WOODLAND_MANSION_PIECE, 0, structureTemplateManager, makeLocation(templateName), templateName, makeSettings(mirror, rotation), position);
        }

        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(StructurePieceType.WOODLAND_MANSION_PIECE, tag, structureTemplateManager, (identifier) -> {
                return makeSettings((Mirror) tag.read("Mi", Mirror.LEGACY_CODEC).orElseThrow(), (Rotation) tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow());
            });
        }

        @Override
        protected Identifier makeTemplateLocation() {
            return makeLocation(this.templateName);
        }

        private static Identifier makeLocation(String templateName) {
            return Identifier.withDefaultNamespace("woodland_mansion/" + templateName);
        }

        private static StructurePlaceSettings makeSettings(Mirror mirror, Rotation rotation) {
            return (new StructurePlaceSettings()).setIgnoreEntities(true).setRotation(rotation).setMirror(mirror).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
            tag.store("Mi", Mirror.LEGACY_CODEC, this.placeSettings.getMirror());
        }

        @Override
        protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
            if (markerId.startsWith("Chest")) {
                Rotation rotation = this.placeSettings.getRotation();
                BlockState blockstate = Blocks.CHEST.defaultBlockState();

                if ("ChestWest".equals(markerId)) {
                    blockstate = (BlockState) blockstate.setValue(ChestBlock.FACING, rotation.rotate(Direction.WEST));
                } else if ("ChestEast".equals(markerId)) {
                    blockstate = (BlockState) blockstate.setValue(ChestBlock.FACING, rotation.rotate(Direction.EAST));
                } else if ("ChestSouth".equals(markerId)) {
                    blockstate = (BlockState) blockstate.setValue(ChestBlock.FACING, rotation.rotate(Direction.SOUTH));
                } else if ("ChestNorth".equals(markerId)) {
                    blockstate = (BlockState) blockstate.setValue(ChestBlock.FACING, rotation.rotate(Direction.NORTH));
                }

                this.createChest(level, chunkBB, random, position, BuiltInLootTables.WOODLAND_MANSION, blockstate);
            } else {
                List<Mob> list = new ArrayList();

                switch (markerId) {
                    case "Mage":
                        list.add(EntityType.EVOKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
                        break;
                    case "Warrior":
                        list.add(EntityType.VINDICATOR.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
                        break;
                    case "Group of Allays":
                        int i = level.getRandom().nextInt(3) + 1;

                        for (int j = 0; j < i; ++j) {
                            list.add(EntityType.ALLAY.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
                        }
                        break;
                    default:
                        return;
                }

                for (Mob mob : list) {
                    if (mob != null) {
                        mob.setPersistenceRequired();
                        mob.snapTo(position, 0.0F, 0.0F);
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.STRUCTURE, (SpawnGroupData) null);
                        level.addFreshEntityWithPassengers(mob);
                        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }

        }
    }

    private static class PlacementData {

        public Rotation rotation;
        public BlockPos position;
        public String wallType;

        private PlacementData() {}
    }

    private static class MansionPiecePlacer {

        private final StructureTemplateManager structureTemplateManager;
        private final RandomSource random;
        private int startX;
        private int startY;

        public MansionPiecePlacer(StructureTemplateManager structureTemplateManager, RandomSource random) {
            this.structureTemplateManager = structureTemplateManager;
            this.random = random;
        }

        public void createMansion(BlockPos origin, Rotation rotation, List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.MansionGrid mansion) {
            WoodlandMansionPieces.PlacementData woodlandmansionpieces_placementdata = new WoodlandMansionPieces.PlacementData();

            woodlandmansionpieces_placementdata.position = origin;
            woodlandmansionpieces_placementdata.rotation = rotation;
            woodlandmansionpieces_placementdata.wallType = "wall_flat";
            WoodlandMansionPieces.PlacementData woodlandmansionpieces_placementdata1 = new WoodlandMansionPieces.PlacementData();

            this.entrance(pieces, woodlandmansionpieces_placementdata);
            woodlandmansionpieces_placementdata1.position = woodlandmansionpieces_placementdata.position.above(8);
            woodlandmansionpieces_placementdata1.rotation = woodlandmansionpieces_placementdata.rotation;
            woodlandmansionpieces_placementdata1.wallType = "wall_window";
            if (!pieces.isEmpty()) {
                ;
            }

            WoodlandMansionPieces.SimpleGrid woodlandmansionpieces_simplegrid = mansion.baseGrid;
            WoodlandMansionPieces.SimpleGrid woodlandmansionpieces_simplegrid1 = mansion.thirdFloorGrid;

            this.startX = mansion.entranceX + 1;
            this.startY = mansion.entranceY + 1;
            int i = mansion.entranceX + 1;
            int j = mansion.entranceY;

            this.traverseOuterWalls(pieces, woodlandmansionpieces_placementdata, woodlandmansionpieces_simplegrid, Direction.SOUTH, this.startX, this.startY, i, j);
            this.traverseOuterWalls(pieces, woodlandmansionpieces_placementdata1, woodlandmansionpieces_simplegrid, Direction.SOUTH, this.startX, this.startY, i, j);
            WoodlandMansionPieces.PlacementData woodlandmansionpieces_placementdata2 = new WoodlandMansionPieces.PlacementData();

            woodlandmansionpieces_placementdata2.position = woodlandmansionpieces_placementdata.position.above(19);
            woodlandmansionpieces_placementdata2.rotation = woodlandmansionpieces_placementdata.rotation;
            woodlandmansionpieces_placementdata2.wallType = "wall_window";
            boolean flag = false;

            for (int k = 0; k < woodlandmansionpieces_simplegrid1.height && !flag; ++k) {
                for (int l = woodlandmansionpieces_simplegrid1.width - 1; l >= 0 && !flag; --l) {
                    if (WoodlandMansionPieces.MansionGrid.isHouse(woodlandmansionpieces_simplegrid1, l, k)) {
                        woodlandmansionpieces_placementdata2.position = woodlandmansionpieces_placementdata2.position.relative(rotation.rotate(Direction.SOUTH), 8 + (k - this.startY) * 8);
                        woodlandmansionpieces_placementdata2.position = woodlandmansionpieces_placementdata2.position.relative(rotation.rotate(Direction.EAST), (l - this.startX) * 8);
                        this.traverseWallPiece(pieces, woodlandmansionpieces_placementdata2);
                        this.traverseOuterWalls(pieces, woodlandmansionpieces_placementdata2, woodlandmansionpieces_simplegrid1, Direction.SOUTH, l, k, l, k);
                        flag = true;
                    }
                }
            }

            this.createRoof(pieces, origin.above(16), rotation, woodlandmansionpieces_simplegrid, woodlandmansionpieces_simplegrid1);
            this.createRoof(pieces, origin.above(27), rotation, woodlandmansionpieces_simplegrid1, (WoodlandMansionPieces.SimpleGrid) null);
            if (!pieces.isEmpty()) {
                ;
            }

            WoodlandMansionPieces.FloorRoomCollection[] awoodlandmansionpieces_floorroomcollection = new WoodlandMansionPieces.FloorRoomCollection[]{new WoodlandMansionPieces.FirstFloorRoomCollection(), new WoodlandMansionPieces.SecondFloorRoomCollection(), new WoodlandMansionPieces.ThirdFloorRoomCollection()};

            for (int i1 = 0; i1 < 3; ++i1) {
                BlockPos blockpos1 = origin.above(8 * i1 + (i1 == 2 ? 3 : 0));
                WoodlandMansionPieces.SimpleGrid woodlandmansionpieces_simplegrid2 = mansion.floorRooms[i1];
                WoodlandMansionPieces.SimpleGrid woodlandmansionpieces_simplegrid3 = i1 == 2 ? woodlandmansionpieces_simplegrid1 : woodlandmansionpieces_simplegrid;
                String s = i1 == 0 ? "carpet_south_1" : "carpet_south_2";
                String s1 = i1 == 0 ? "carpet_west_1" : "carpet_west_2";

                for (int j1 = 0; j1 < woodlandmansionpieces_simplegrid3.height; ++j1) {
                    for (int k1 = 0; k1 < woodlandmansionpieces_simplegrid3.width; ++k1) {
                        if (woodlandmansionpieces_simplegrid3.get(k1, j1) == 1) {
                            BlockPos blockpos2 = blockpos1.relative(rotation.rotate(Direction.SOUTH), 8 + (j1 - this.startY) * 8);

                            blockpos2 = blockpos2.relative(rotation.rotate(Direction.EAST), (k1 - this.startX) * 8);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "corridor_floor", blockpos2, rotation));
                            if (woodlandmansionpieces_simplegrid3.get(k1, j1 - 1) == 1 || (woodlandmansionpieces_simplegrid2.get(k1, j1 - 1) & 8388608) == 8388608) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "carpet_north", blockpos2.relative(rotation.rotate(Direction.EAST), 1).above(), rotation));
                            }

                            if (woodlandmansionpieces_simplegrid3.get(k1 + 1, j1) == 1 || (woodlandmansionpieces_simplegrid2.get(k1 + 1, j1) & 8388608) == 8388608) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "carpet_east", blockpos2.relative(rotation.rotate(Direction.SOUTH), 1).relative(rotation.rotate(Direction.EAST), 5).above(), rotation));
                            }

                            if (woodlandmansionpieces_simplegrid3.get(k1, j1 + 1) == 1 || (woodlandmansionpieces_simplegrid2.get(k1, j1 + 1) & 8388608) == 8388608) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, s, blockpos2.relative(rotation.rotate(Direction.SOUTH), 5).relative(rotation.rotate(Direction.WEST), 1), rotation));
                            }

                            if (woodlandmansionpieces_simplegrid3.get(k1 - 1, j1) == 1 || (woodlandmansionpieces_simplegrid2.get(k1 - 1, j1) & 8388608) == 8388608) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, s1, blockpos2.relative(rotation.rotate(Direction.WEST), 1).relative(rotation.rotate(Direction.NORTH), 1), rotation));
                            }
                        }
                    }
                }

                String s2 = i1 == 0 ? "indoors_wall_1" : "indoors_wall_2";
                String s3 = i1 == 0 ? "indoors_door_1" : "indoors_door_2";
                List<Direction> list1 = Lists.newArrayList();

                for (int l1 = 0; l1 < woodlandmansionpieces_simplegrid3.height; ++l1) {
                    for (int i2 = 0; i2 < woodlandmansionpieces_simplegrid3.width; ++i2) {
                        boolean flag1 = i1 == 2 && woodlandmansionpieces_simplegrid3.get(i2, l1) == 3;

                        if (woodlandmansionpieces_simplegrid3.get(i2, l1) == 2 || flag1) {
                            int j2 = woodlandmansionpieces_simplegrid2.get(i2, l1);
                            int k2 = j2 & 983040;
                            int l2 = j2 & '\uffff';

                            flag1 = flag1 && (j2 & 8388608) == 8388608;
                            list1.clear();
                            if ((j2 & 2097152) == 2097152) {
                                for (Direction direction : Direction.Plane.HORIZONTAL) {
                                    if (woodlandmansionpieces_simplegrid3.get(i2 + direction.getStepX(), l1 + direction.getStepZ()) == 1) {
                                        list1.add(direction);
                                    }
                                }
                            }

                            Direction direction1 = null;

                            if (!list1.isEmpty()) {
                                direction1 = (Direction) list1.get(this.random.nextInt(list1.size()));
                            } else if ((j2 & 1048576) == 1048576) {
                                direction1 = Direction.UP;
                            }

                            BlockPos blockpos3 = blockpos1.relative(rotation.rotate(Direction.SOUTH), 8 + (l1 - this.startY) * 8);

                            blockpos3 = blockpos3.relative(rotation.rotate(Direction.EAST), -1 + (i2 - this.startX) * 8);
                            if (WoodlandMansionPieces.MansionGrid.isHouse(woodlandmansionpieces_simplegrid3, i2 - 1, l1) && !mansion.isRoomId(woodlandmansionpieces_simplegrid3, i2 - 1, l1, i1, l2)) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, direction1 == Direction.WEST ? s3 : s2, blockpos3, rotation));
                            }

                            if (woodlandmansionpieces_simplegrid3.get(i2 + 1, l1) == 1 && !flag1) {
                                BlockPos blockpos4 = blockpos3.relative(rotation.rotate(Direction.EAST), 8);

                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, direction1 == Direction.EAST ? s3 : s2, blockpos4, rotation));
                            }

                            if (WoodlandMansionPieces.MansionGrid.isHouse(woodlandmansionpieces_simplegrid3, i2, l1 + 1) && !mansion.isRoomId(woodlandmansionpieces_simplegrid3, i2, l1 + 1, i1, l2)) {
                                BlockPos blockpos5 = blockpos3.relative(rotation.rotate(Direction.SOUTH), 7);

                                blockpos5 = blockpos5.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, direction1 == Direction.SOUTH ? s3 : s2, blockpos5, rotation.getRotated(Rotation.CLOCKWISE_90)));
                            }

                            if (woodlandmansionpieces_simplegrid3.get(i2, l1 - 1) == 1 && !flag1) {
                                BlockPos blockpos6 = blockpos3.relative(rotation.rotate(Direction.NORTH), 1);

                                blockpos6 = blockpos6.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, direction1 == Direction.NORTH ? s3 : s2, blockpos6, rotation.getRotated(Rotation.CLOCKWISE_90)));
                            }

                            if (k2 == 65536) {
                                this.addRoom1x1(pieces, blockpos3, rotation, direction1, awoodlandmansionpieces_floorroomcollection[i1]);
                            } else if (k2 == 131072 && direction1 != null) {
                                Direction direction2 = mansion.get1x2RoomDirection(woodlandmansionpieces_simplegrid3, i2, l1, i1, l2);
                                boolean flag2 = (j2 & 4194304) == 4194304;

                                this.addRoom1x2(pieces, blockpos3, rotation, direction2, direction1, awoodlandmansionpieces_floorroomcollection[i1], flag2);
                            } else if (k2 == 262144 && direction1 != null && direction1 != Direction.UP) {
                                Direction direction3 = direction1.getClockWise();

                                if (!mansion.isRoomId(woodlandmansionpieces_simplegrid3, i2 + direction3.getStepX(), l1 + direction3.getStepZ(), i1, l2)) {
                                    direction3 = direction3.getOpposite();
                                }

                                this.addRoom2x2(pieces, blockpos3, rotation, direction3, direction1, awoodlandmansionpieces_floorroomcollection[i1]);
                            } else if (k2 == 262144 && direction1 == Direction.UP) {
                                this.addRoom2x2Secret(pieces, blockpos3, rotation, awoodlandmansionpieces_floorroomcollection[i1]);
                            }
                        }
                    }
                }
            }

        }

        private void traverseOuterWalls(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data, WoodlandMansionPieces.SimpleGrid grid, Direction gridDirection, int startX, int startY, int endX, int endY) {
            int i1 = startX;
            int j1 = startY;
            Direction direction1 = gridDirection;

            do {
                if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, i1 + gridDirection.getStepX(), j1 + gridDirection.getStepZ())) {
                    this.traverseTurn(pieces, data);
                    gridDirection = gridDirection.getClockWise();
                    if (i1 != endX || j1 != endY || direction1 != gridDirection) {
                        this.traverseWallPiece(pieces, data);
                    }
                } else if (WoodlandMansionPieces.MansionGrid.isHouse(grid, i1 + gridDirection.getStepX(), j1 + gridDirection.getStepZ()) && WoodlandMansionPieces.MansionGrid.isHouse(grid, i1 + gridDirection.getStepX() + gridDirection.getCounterClockWise().getStepX(), j1 + gridDirection.getStepZ() + gridDirection.getCounterClockWise().getStepZ())) {
                    this.traverseInnerTurn(pieces, data);
                    i1 += gridDirection.getStepX();
                    j1 += gridDirection.getStepZ();
                    gridDirection = gridDirection.getCounterClockWise();
                } else {
                    i1 += gridDirection.getStepX();
                    j1 += gridDirection.getStepZ();
                    if (i1 != endX || j1 != endY || direction1 != gridDirection) {
                        this.traverseWallPiece(pieces, data);
                    }
                }
            } while (i1 != endX || j1 != endY || direction1 != gridDirection);

        }

        private void createRoof(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos roofOrigin, Rotation rotation, WoodlandMansionPieces.SimpleGrid grid, WoodlandMansionPieces.@Nullable SimpleGrid aboveGrid) {
            for (int i = 0; i < grid.height; ++i) {
                for (int j = 0; j < grid.width; ++j) {
                    BlockPos blockpos1 = roofOrigin.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);

                    blockpos1 = blockpos1.relative(rotation.rotate(Direction.EAST), (j - this.startX) * 8);
                    boolean flag = aboveGrid != null && WoodlandMansionPieces.MansionGrid.isHouse(aboveGrid, j, i);

                    if (WoodlandMansionPieces.MansionGrid.isHouse(grid, j, i) && !flag) {
                        pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof", blockpos1.above(3), rotation));
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j + 1, i)) {
                            BlockPos blockpos2 = blockpos1.relative(rotation.rotate(Direction.EAST), 6);

                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockpos2, rotation));
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j - 1, i)) {
                            BlockPos blockpos3 = blockpos1.relative(rotation.rotate(Direction.EAST), 0);

                            blockpos3 = blockpos3.relative(rotation.rotate(Direction.SOUTH), 7);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockpos3, rotation.getRotated(Rotation.CLOCKWISE_180)));
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j, i - 1)) {
                            BlockPos blockpos4 = blockpos1.relative(rotation.rotate(Direction.WEST), 1);

                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockpos4, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j, i + 1)) {
                            BlockPos blockpos5 = blockpos1.relative(rotation.rotate(Direction.EAST), 6);

                            blockpos5 = blockpos5.relative(rotation.rotate(Direction.SOUTH), 6);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockpos5, rotation.getRotated(Rotation.CLOCKWISE_90)));
                        }
                    }
                }
            }

            if (aboveGrid != null) {
                for (int k = 0; k < grid.height; ++k) {
                    for (int l = 0; l < grid.width; ++l) {
                        BlockPos blockpos6 = roofOrigin.relative(rotation.rotate(Direction.SOUTH), 8 + (k - this.startY) * 8);

                        blockpos6 = blockpos6.relative(rotation.rotate(Direction.EAST), (l - this.startX) * 8);
                        boolean flag1 = WoodlandMansionPieces.MansionGrid.isHouse(aboveGrid, l, k);

                        if (WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k) && flag1) {
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l + 1, k)) {
                                BlockPos blockpos7 = blockpos6.relative(rotation.rotate(Direction.EAST), 7);

                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockpos7, rotation));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l - 1, k)) {
                                BlockPos blockpos8 = blockpos6.relative(rotation.rotate(Direction.WEST), 1);

                                blockpos8 = blockpos8.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockpos8, rotation.getRotated(Rotation.CLOCKWISE_180)));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k - 1)) {
                                BlockPos blockpos9 = blockpos6.relative(rotation.rotate(Direction.WEST), 0);

                                blockpos9 = blockpos9.relative(rotation.rotate(Direction.NORTH), 1);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockpos9, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k + 1)) {
                                BlockPos blockpos10 = blockpos6.relative(rotation.rotate(Direction.EAST), 6);

                                blockpos10 = blockpos10.relative(rotation.rotate(Direction.SOUTH), 7);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockpos10, rotation.getRotated(Rotation.CLOCKWISE_90)));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l + 1, k)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k - 1)) {
                                    BlockPos blockpos11 = blockpos6.relative(rotation.rotate(Direction.EAST), 7);

                                    blockpos11 = blockpos11.relative(rotation.rotate(Direction.NORTH), 2);
                                    pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockpos11, rotation));
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k + 1)) {
                                    BlockPos blockpos12 = blockpos6.relative(rotation.rotate(Direction.EAST), 8);

                                    blockpos12 = blockpos12.relative(rotation.rotate(Direction.SOUTH), 7);
                                    pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockpos12, rotation.getRotated(Rotation.CLOCKWISE_90)));
                                }
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l - 1, k)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k - 1)) {
                                    BlockPos blockpos13 = blockpos6.relative(rotation.rotate(Direction.WEST), 2);

                                    blockpos13 = blockpos13.relative(rotation.rotate(Direction.NORTH), 1);
                                    pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockpos13, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, l, k + 1)) {
                                    BlockPos blockpos14 = blockpos6.relative(rotation.rotate(Direction.WEST), 1);

                                    blockpos14 = blockpos14.relative(rotation.rotate(Direction.SOUTH), 8);
                                    pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockpos14, rotation.getRotated(Rotation.CLOCKWISE_180)));
                                }
                            }
                        }
                    }
                }
            }

            for (int i1 = 0; i1 < grid.height; ++i1) {
                for (int j1 = 0; j1 < grid.width; ++j1) {
                    BlockPos blockpos15 = roofOrigin.relative(rotation.rotate(Direction.SOUTH), 8 + (i1 - this.startY) * 8);

                    blockpos15 = blockpos15.relative(rotation.rotate(Direction.EAST), (j1 - this.startX) * 8);
                    boolean flag2 = aboveGrid != null && WoodlandMansionPieces.MansionGrid.isHouse(aboveGrid, j1, i1);

                    if (WoodlandMansionPieces.MansionGrid.isHouse(grid, j1, i1) && !flag2) {
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j1 + 1, i1)) {
                            BlockPos blockpos16 = blockpos15.relative(rotation.rotate(Direction.EAST), 6);

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j1, i1 + 1)) {
                                BlockPos blockpos17 = blockpos16.relative(rotation.rotate(Direction.SOUTH), 6);

                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockpos17, rotation));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(grid, j1 + 1, i1 + 1)) {
                                BlockPos blockpos18 = blockpos16.relative(rotation.rotate(Direction.SOUTH), 5);

                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockpos18, rotation));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j1, i1 - 1)) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockpos16, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(grid, j1 + 1, i1 - 1)) {
                                BlockPos blockpos19 = blockpos15.relative(rotation.rotate(Direction.EAST), 9);

                                blockpos19 = blockpos19.relative(rotation.rotate(Direction.NORTH), 2);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockpos19, rotation.getRotated(Rotation.CLOCKWISE_90)));
                            }
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j1 - 1, i1)) {
                            BlockPos blockpos20 = blockpos15.relative(rotation.rotate(Direction.EAST), 0);

                            blockpos20 = blockpos20.relative(rotation.rotate(Direction.SOUTH), 0);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j1, i1 + 1)) {
                                BlockPos blockpos21 = blockpos20.relative(rotation.rotate(Direction.SOUTH), 6);

                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockpos21, rotation.getRotated(Rotation.CLOCKWISE_90)));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(grid, j1 - 1, i1 + 1)) {
                                BlockPos blockpos22 = blockpos20.relative(rotation.rotate(Direction.SOUTH), 8);

                                blockpos22 = blockpos22.relative(rotation.rotate(Direction.WEST), 3);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockpos22, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(grid, j1, i1 - 1)) {
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockpos20, rotation.getRotated(Rotation.CLOCKWISE_180)));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(grid, j1 - 1, i1 - 1)) {
                                BlockPos blockpos23 = blockpos20.relative(rotation.rotate(Direction.SOUTH), 1);

                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockpos23, rotation.getRotated(Rotation.CLOCKWISE_180)));
                            }
                        }
                    }
                }
            }

        }

        private void entrance(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            Direction direction = data.rotation.rotate(Direction.WEST);

            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "entrance", data.position.relative(direction, 9), data.rotation));
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 16);
        }

        private void traverseWallPiece(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, data.wallType, data.position.relative(data.rotation.rotate(Direction.EAST), 7), data.rotation));
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 8);
        }

        private void traverseTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), -1);
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "wall_corner", data.position, data.rotation));
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), -7);
            data.position = data.position.relative(data.rotation.rotate(Direction.WEST), -6);
            data.rotation = data.rotation.getRotated(Rotation.CLOCKWISE_90);
        }

        private void traverseInnerTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 6);
            data.position = data.position.relative(data.rotation.rotate(Direction.EAST), 8);
            data.rotation = data.rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
        }

        private void addRoom1x1(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos roomPos, Rotation rotation, Direction doorDir, WoodlandMansionPieces.FloorRoomCollection rooms) {
            Rotation rotation1 = Rotation.NONE;
            String s = rooms.get1x1(this.random);

            if (doorDir != Direction.EAST) {
                if (doorDir == Direction.NORTH) {
                    rotation1 = rotation1.getRotated(Rotation.COUNTERCLOCKWISE_90);
                } else if (doorDir == Direction.WEST) {
                    rotation1 = rotation1.getRotated(Rotation.CLOCKWISE_180);
                } else if (doorDir == Direction.SOUTH) {
                    rotation1 = rotation1.getRotated(Rotation.CLOCKWISE_90);
                } else {
                    s = rooms.get1x1Secret(this.random);
                }
            }

            BlockPos blockpos1 = StructureTemplate.getZeroPositionWithTransform(new BlockPos(1, 0, 0), Mirror.NONE, rotation1, 7, 7);

            rotation1 = rotation1.getRotated(rotation);
            blockpos1 = blockpos1.rotate(rotation);
            BlockPos blockpos2 = roomPos.offset(blockpos1.getX(), 0, blockpos1.getZ());

            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, s, blockpos2, rotation1));
        }

        private void addRoom1x2(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos roomPos, Rotation rotation, Direction roomDir, Direction doorDir, WoodlandMansionPieces.FloorRoomCollection rooms, boolean isStairsRoom) {
            if (doorDir == Direction.EAST && roomDir == Direction.SOUTH) {
                BlockPos blockpos1 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos1, rotation));
            } else if (doorDir == Direction.EAST && roomDir == Direction.NORTH) {
                BlockPos blockpos2 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

                blockpos2 = blockpos2.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos2, rotation, Mirror.LEFT_RIGHT));
            } else if (doorDir == Direction.WEST && roomDir == Direction.NORTH) {
                BlockPos blockpos3 = roomPos.relative(rotation.rotate(Direction.EAST), 7);

                blockpos3 = blockpos3.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos3, rotation.getRotated(Rotation.CLOCKWISE_180)));
            } else if (doorDir == Direction.WEST && roomDir == Direction.SOUTH) {
                BlockPos blockpos4 = roomPos.relative(rotation.rotate(Direction.EAST), 7);

                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos4, rotation, Mirror.FRONT_BACK));
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.EAST) {
                BlockPos blockpos5 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos5, rotation.getRotated(Rotation.CLOCKWISE_90), Mirror.LEFT_RIGHT));
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.WEST) {
                BlockPos blockpos6 = roomPos.relative(rotation.rotate(Direction.EAST), 7);

                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos6, rotation.getRotated(Rotation.CLOCKWISE_90)));
            } else if (doorDir == Direction.NORTH && roomDir == Direction.WEST) {
                BlockPos blockpos7 = roomPos.relative(rotation.rotate(Direction.EAST), 7);

                blockpos7 = blockpos7.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos7, rotation.getRotated(Rotation.CLOCKWISE_90), Mirror.FRONT_BACK));
            } else if (doorDir == Direction.NORTH && roomDir == Direction.EAST) {
                BlockPos blockpos8 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

                blockpos8 = blockpos8.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2SideEntrance(this.random, isStairsRoom), blockpos8, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.NORTH) {
                BlockPos blockpos9 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

                blockpos9 = blockpos9.relative(rotation.rotate(Direction.NORTH), 8);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2FrontEntrance(this.random, isStairsRoom), blockpos9, rotation));
            } else if (doorDir == Direction.NORTH && roomDir == Direction.SOUTH) {
                BlockPos blockpos10 = roomPos.relative(rotation.rotate(Direction.EAST), 7);

                blockpos10 = blockpos10.relative(rotation.rotate(Direction.SOUTH), 14);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2FrontEntrance(this.random, isStairsRoom), blockpos10, rotation.getRotated(Rotation.CLOCKWISE_180)));
            } else if (doorDir == Direction.WEST && roomDir == Direction.EAST) {
                BlockPos blockpos11 = roomPos.relative(rotation.rotate(Direction.EAST), 15);

                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2FrontEntrance(this.random, isStairsRoom), blockpos11, rotation.getRotated(Rotation.CLOCKWISE_90)));
            } else if (doorDir == Direction.EAST && roomDir == Direction.WEST) {
                BlockPos blockpos12 = roomPos.relative(rotation.rotate(Direction.WEST), 7);

                blockpos12 = blockpos12.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2FrontEntrance(this.random, isStairsRoom), blockpos12, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
            } else if (doorDir == Direction.UP && roomDir == Direction.EAST) {
                BlockPos blockpos13 = roomPos.relative(rotation.rotate(Direction.EAST), 15);

                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2Secret(this.random), blockpos13, rotation.getRotated(Rotation.CLOCKWISE_90)));
            } else if (doorDir == Direction.UP && roomDir == Direction.SOUTH) {
                BlockPos blockpos14 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

                blockpos14 = blockpos14.relative(rotation.rotate(Direction.NORTH), 0);
                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get1x2Secret(this.random), blockpos14, rotation));
            }

        }

        private void addRoom2x2(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos roomPos, Rotation rotation, Direction roomDir, Direction doorDir, WoodlandMansionPieces.FloorRoomCollection rooms) {
            int i = 0;
            int j = 0;
            Rotation rotation1 = rotation;
            Mirror mirror = Mirror.NONE;

            if (doorDir == Direction.EAST && roomDir == Direction.SOUTH) {
                i = -7;
            } else if (doorDir == Direction.EAST && roomDir == Direction.NORTH) {
                i = -7;
                j = 6;
                mirror = Mirror.LEFT_RIGHT;
            } else if (doorDir == Direction.NORTH && roomDir == Direction.EAST) {
                i = 1;
                j = 14;
                rotation1 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
            } else if (doorDir == Direction.NORTH && roomDir == Direction.WEST) {
                i = 7;
                j = 14;
                rotation1 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.WEST) {
                i = 7;
                j = -8;
                rotation1 = rotation.getRotated(Rotation.CLOCKWISE_90);
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.EAST) {
                i = 1;
                j = -8;
                rotation1 = rotation.getRotated(Rotation.CLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (doorDir == Direction.WEST && roomDir == Direction.NORTH) {
                i = 15;
                j = 6;
                rotation1 = rotation.getRotated(Rotation.CLOCKWISE_180);
            } else if (doorDir == Direction.WEST && roomDir == Direction.SOUTH) {
                i = 15;
                mirror = Mirror.FRONT_BACK;
            }

            BlockPos blockpos1 = roomPos.relative(rotation.rotate(Direction.EAST), i);

            blockpos1 = blockpos1.relative(rotation.rotate(Direction.SOUTH), j);
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get2x2(this.random), blockpos1, rotation1, mirror));
        }

        private void addRoom2x2Secret(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos roomPos, Rotation rotation, WoodlandMansionPieces.FloorRoomCollection rooms) {
            BlockPos blockpos1 = roomPos.relative(rotation.rotate(Direction.EAST), 1);

            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, rooms.get2x2Secret(this.random), blockpos1, rotation, Mirror.NONE));
        }
    }

    private static class MansionGrid {

        private static final int DEFAULT_SIZE = 11;
        private static final int CLEAR = 0;
        private static final int CORRIDOR = 1;
        private static final int ROOM = 2;
        private static final int START_ROOM = 3;
        private static final int TEST_ROOM = 4;
        private static final int BLOCKED = 5;
        private static final int ROOM_1x1 = 65536;
        private static final int ROOM_1x2 = 131072;
        private static final int ROOM_2x2 = 262144;
        private static final int ROOM_ORIGIN_FLAG = 1048576;
        private static final int ROOM_DOOR_FLAG = 2097152;
        private static final int ROOM_STAIRS_FLAG = 4194304;
        private static final int ROOM_CORRIDOR_FLAG = 8388608;
        private static final int ROOM_TYPE_MASK = 983040;
        private static final int ROOM_ID_MASK = 65535;
        private final RandomSource random;
        private final WoodlandMansionPieces.SimpleGrid baseGrid;
        private final WoodlandMansionPieces.SimpleGrid thirdFloorGrid;
        private final WoodlandMansionPieces.SimpleGrid[] floorRooms;
        private final int entranceX;
        private final int entranceY;

        public MansionGrid(RandomSource random) {
            this.random = random;
            int i = 11;

            this.entranceX = 7;
            this.entranceY = 4;
            this.baseGrid = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.baseGrid.set(this.entranceX, this.entranceY, this.entranceX + 1, this.entranceY + 1, 3);
            this.baseGrid.set(this.entranceX - 1, this.entranceY, this.entranceX - 1, this.entranceY + 1, 2);
            this.baseGrid.set(this.entranceX + 2, this.entranceY - 2, this.entranceX + 3, this.entranceY + 3, 5);
            this.baseGrid.set(this.entranceX + 1, this.entranceY - 2, this.entranceX + 1, this.entranceY - 1, 1);
            this.baseGrid.set(this.entranceX + 1, this.entranceY + 2, this.entranceX + 1, this.entranceY + 3, 1);
            this.baseGrid.set(this.entranceX - 1, this.entranceY - 1, 1);
            this.baseGrid.set(this.entranceX - 1, this.entranceY + 2, 1);
            this.baseGrid.set(0, 0, 11, 1, 5);
            this.baseGrid.set(0, 9, 11, 11, 5);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY - 2, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY + 3, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY - 1, Direction.WEST, 3);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY + 2, Direction.WEST, 3);

            while (this.cleanEdges(this.baseGrid)) {
                ;
            }

            this.floorRooms = new WoodlandMansionPieces.SimpleGrid[3];
            this.floorRooms[0] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.floorRooms[1] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.floorRooms[2] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.identifyRooms(this.baseGrid, this.floorRooms[0]);
            this.identifyRooms(this.baseGrid, this.floorRooms[1]);
            this.floorRooms[0].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.floorRooms[1].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.thirdFloorGrid = new WoodlandMansionPieces.SimpleGrid(this.baseGrid.width, this.baseGrid.height, 5);
            this.setupThirdFloor();
            this.identifyRooms(this.thirdFloorGrid, this.floorRooms[2]);
        }

        public static boolean isHouse(WoodlandMansionPieces.SimpleGrid grid, int x, int y) {
            int k = grid.get(x, y);

            return k == 1 || k == 2 || k == 3 || k == 4;
        }

        public boolean isRoomId(WoodlandMansionPieces.SimpleGrid grid, int x, int y, int floor, int roomId) {
            return (this.floorRooms[floor].get(x, y) & '\uffff') == roomId;
        }

        public @Nullable Direction get1x2RoomDirection(WoodlandMansionPieces.SimpleGrid grid, int x, int y, int floorNum, int roomId) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.isRoomId(grid, x + direction.getStepX(), y + direction.getStepZ(), floorNum, roomId)) {
                    return direction;
                }
            }

            return null;
        }

        private void recursiveCorridor(WoodlandMansionPieces.SimpleGrid grid, int x, int y, Direction heading, int depth) {
            if (depth > 0) {
                grid.set(x, y, 1);
                grid.setif(x + heading.getStepX(), y + heading.getStepZ(), 0, 1);

                for (int l = 0; l < 8; ++l) {
                    Direction direction1 = Direction.from2DDataValue(this.random.nextInt(4));

                    if (direction1 != heading.getOpposite() && (direction1 != Direction.EAST || !this.random.nextBoolean())) {
                        int i1 = x + heading.getStepX();
                        int j1 = y + heading.getStepZ();

                        if (grid.get(i1 + direction1.getStepX(), j1 + direction1.getStepZ()) == 0 && grid.get(i1 + direction1.getStepX() * 2, j1 + direction1.getStepZ() * 2) == 0) {
                            this.recursiveCorridor(grid, x + heading.getStepX() + direction1.getStepX(), y + heading.getStepZ() + direction1.getStepZ(), direction1, depth - 1);
                            break;
                        }
                    }
                }

                Direction direction2 = heading.getClockWise();
                Direction direction3 = heading.getCounterClockWise();

                grid.setif(x + direction2.getStepX(), y + direction2.getStepZ(), 0, 2);
                grid.setif(x + direction3.getStepX(), y + direction3.getStepZ(), 0, 2);
                grid.setif(x + heading.getStepX() + direction2.getStepX(), y + heading.getStepZ() + direction2.getStepZ(), 0, 2);
                grid.setif(x + heading.getStepX() + direction3.getStepX(), y + heading.getStepZ() + direction3.getStepZ(), 0, 2);
                grid.setif(x + heading.getStepX() * 2, y + heading.getStepZ() * 2, 0, 2);
                grid.setif(x + direction2.getStepX() * 2, y + direction2.getStepZ() * 2, 0, 2);
                grid.setif(x + direction3.getStepX() * 2, y + direction3.getStepZ() * 2, 0, 2);
            }
        }

        private boolean cleanEdges(WoodlandMansionPieces.SimpleGrid grid) {
            boolean flag = false;

            for (int i = 0; i < grid.height; ++i) {
                for (int j = 0; j < grid.width; ++j) {
                    if (grid.get(j, i) == 0) {
                        int k = 0;

                        k += isHouse(grid, j + 1, i) ? 1 : 0;
                        k += isHouse(grid, j - 1, i) ? 1 : 0;
                        k += isHouse(grid, j, i + 1) ? 1 : 0;
                        k += isHouse(grid, j, i - 1) ? 1 : 0;
                        if (k >= 3) {
                            grid.set(j, i, 2);
                            flag = true;
                        } else if (k == 2) {
                            int l = 0;

                            l += isHouse(grid, j + 1, i + 1) ? 1 : 0;
                            l += isHouse(grid, j - 1, i + 1) ? 1 : 0;
                            l += isHouse(grid, j + 1, i - 1) ? 1 : 0;
                            l += isHouse(grid, j - 1, i - 1) ? 1 : 0;
                            if (l <= 1) {
                                grid.set(j, i, 2);
                                flag = true;
                            }
                        }
                    }
                }
            }

            return flag;
        }

        private void setupThirdFloor() {
            List<Tuple<Integer, Integer>> list = Lists.newArrayList();
            WoodlandMansionPieces.SimpleGrid woodlandmansionpieces_simplegrid = this.floorRooms[1];

            for (int i = 0; i < this.thirdFloorGrid.height; ++i) {
                for (int j = 0; j < this.thirdFloorGrid.width; ++j) {
                    int k = woodlandmansionpieces_simplegrid.get(j, i);
                    int l = k & 983040;

                    if (l == 131072 && (k & 2097152) == 2097152) {
                        list.add(new Tuple(j, i));
                    }
                }
            }

            if (list.isEmpty()) {
                this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, 5);
            } else {
                Tuple<Integer, Integer> tuple = (Tuple) list.get(this.random.nextInt(list.size()));
                int i1 = woodlandmansionpieces_simplegrid.get((Integer) tuple.getA(), (Integer) tuple.getB());

                woodlandmansionpieces_simplegrid.set((Integer) tuple.getA(), (Integer) tuple.getB(), i1 | 4194304);
                Direction direction = this.get1x2RoomDirection(this.baseGrid, (Integer) tuple.getA(), (Integer) tuple.getB(), 1, i1 & '\uffff');
                int j1 = (Integer) tuple.getA() + direction.getStepX();
                int k1 = (Integer) tuple.getB() + direction.getStepZ();

                for (int l1 = 0; l1 < this.thirdFloorGrid.height; ++l1) {
                    for (int i2 = 0; i2 < this.thirdFloorGrid.width; ++i2) {
                        if (!isHouse(this.baseGrid, i2, l1)) {
                            this.thirdFloorGrid.set(i2, l1, 5);
                        } else if (i2 == (Integer) tuple.getA() && l1 == (Integer) tuple.getB()) {
                            this.thirdFloorGrid.set(i2, l1, 3);
                        } else if (i2 == j1 && l1 == k1) {
                            this.thirdFloorGrid.set(i2, l1, 3);
                            this.floorRooms[2].set(i2, l1, 8388608);
                        }
                    }
                }

                List<Direction> list1 = Lists.newArrayList();

                for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                    if (this.thirdFloorGrid.get(j1 + direction1.getStepX(), k1 + direction1.getStepZ()) == 0) {
                        list1.add(direction1);
                    }
                }

                if (list1.isEmpty()) {
                    this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, 5);
                    woodlandmansionpieces_simplegrid.set((Integer) tuple.getA(), (Integer) tuple.getB(), i1);
                } else {
                    Direction direction2 = (Direction) list1.get(this.random.nextInt(list1.size()));

                    this.recursiveCorridor(this.thirdFloorGrid, j1 + direction2.getStepX(), k1 + direction2.getStepZ(), direction2, 4);

                    while (this.cleanEdges(this.thirdFloorGrid)) {
                        ;
                    }

                }
            }
        }

        private void identifyRooms(WoodlandMansionPieces.SimpleGrid fromGrid, WoodlandMansionPieces.SimpleGrid roomGrid) {
            ObjectArrayList<Tuple<Integer, Integer>> objectarraylist = new ObjectArrayList();

            for (int i = 0; i < fromGrid.height; ++i) {
                for (int j = 0; j < fromGrid.width; ++j) {
                    if (fromGrid.get(j, i) == 2) {
                        objectarraylist.add(new Tuple(j, i));
                    }
                }
            }

            Util.shuffle(objectarraylist, this.random);
            int k = 10;
            ObjectListIterator objectlistiterator = objectarraylist.iterator();

            while (objectlistiterator.hasNext()) {
                Tuple<Integer, Integer> tuple = (Tuple) objectlistiterator.next();
                int l = (Integer) tuple.getA();
                int i1 = (Integer) tuple.getB();

                if (roomGrid.get(l, i1) == 0) {
                    int j1 = l;
                    int k1 = l;
                    int l1 = i1;
                    int i2 = i1;
                    int j2 = 65536;

                    if (roomGrid.get(l + 1, i1) == 0 && roomGrid.get(l, i1 + 1) == 0 && roomGrid.get(l + 1, i1 + 1) == 0 && fromGrid.get(l + 1, i1) == 2 && fromGrid.get(l, i1 + 1) == 2 && fromGrid.get(l + 1, i1 + 1) == 2) {
                        k1 = l + 1;
                        i2 = i1 + 1;
                        j2 = 262144;
                    } else if (roomGrid.get(l - 1, i1) == 0 && roomGrid.get(l, i1 + 1) == 0 && roomGrid.get(l - 1, i1 + 1) == 0 && fromGrid.get(l - 1, i1) == 2 && fromGrid.get(l, i1 + 1) == 2 && fromGrid.get(l - 1, i1 + 1) == 2) {
                        j1 = l - 1;
                        i2 = i1 + 1;
                        j2 = 262144;
                    } else if (roomGrid.get(l - 1, i1) == 0 && roomGrid.get(l, i1 - 1) == 0 && roomGrid.get(l - 1, i1 - 1) == 0 && fromGrid.get(l - 1, i1) == 2 && fromGrid.get(l, i1 - 1) == 2 && fromGrid.get(l - 1, i1 - 1) == 2) {
                        j1 = l - 1;
                        l1 = i1 - 1;
                        j2 = 262144;
                    } else if (roomGrid.get(l + 1, i1) == 0 && fromGrid.get(l + 1, i1) == 2) {
                        k1 = l + 1;
                        j2 = 131072;
                    } else if (roomGrid.get(l, i1 + 1) == 0 && fromGrid.get(l, i1 + 1) == 2) {
                        i2 = i1 + 1;
                        j2 = 131072;
                    } else if (roomGrid.get(l - 1, i1) == 0 && fromGrid.get(l - 1, i1) == 2) {
                        j1 = l - 1;
                        j2 = 131072;
                    } else if (roomGrid.get(l, i1 - 1) == 0 && fromGrid.get(l, i1 - 1) == 2) {
                        l1 = i1 - 1;
                        j2 = 131072;
                    }

                    int k2 = this.random.nextBoolean() ? j1 : k1;
                    int l2 = this.random.nextBoolean() ? l1 : i2;
                    int i3 = 2097152;

                    if (!fromGrid.edgesTo(k2, l2, 1)) {
                        k2 = k2 == j1 ? k1 : j1;
                        l2 = l2 == l1 ? i2 : l1;
                        if (!fromGrid.edgesTo(k2, l2, 1)) {
                            l2 = l2 == l1 ? i2 : l1;
                            if (!fromGrid.edgesTo(k2, l2, 1)) {
                                k2 = k2 == j1 ? k1 : j1;
                                l2 = l2 == l1 ? i2 : l1;
                                if (!fromGrid.edgesTo(k2, l2, 1)) {
                                    i3 = 0;
                                    k2 = j1;
                                    l2 = l1;
                                }
                            }
                        }
                    }

                    for (int j3 = l1; j3 <= i2; ++j3) {
                        for (int k3 = j1; k3 <= k1; ++k3) {
                            if (k3 == k2 && j3 == l2) {
                                roomGrid.set(k3, j3, 1048576 | i3 | j2 | k);
                            } else {
                                roomGrid.set(k3, j3, j2 | k);
                            }
                        }
                    }

                    ++k;
                }
            }

        }
    }

    private static class SimpleGrid {

        private final int[][] grid;
        private final int width;
        private final int height;
        private final int valueIfOutside;

        public SimpleGrid(int width, int height, int valueIfOutside) {
            this.width = width;
            this.height = height;
            this.valueIfOutside = valueIfOutside;
            this.grid = new int[width][height];
        }

        public void set(int x, int y, int value) {
            if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
                this.grid[x][y] = value;
            }

        }

        public void set(int x0, int y0, int x1, int y1, int value) {
            for (int j1 = y0; j1 <= y1; ++j1) {
                for (int k1 = x0; k1 <= x1; ++k1) {
                    this.set(k1, j1, value);
                }
            }

        }

        public int get(int x, int y) {
            return x >= 0 && x < this.width && y >= 0 && y < this.height ? this.grid[x][y] : this.valueIfOutside;
        }

        public void setif(int x, int y, int ifValue, int value) {
            if (this.get(x, y) == ifValue) {
                this.set(x, y, value);
            }

        }

        public boolean edgesTo(int x, int y, int ifValue) {
            return this.get(x - 1, y) == ifValue || this.get(x + 1, y) == ifValue || this.get(x, y + 1) == ifValue || this.get(x, y - 1) == ifValue;
        }
    }

    private abstract static class FloorRoomCollection {

        private FloorRoomCollection() {}

        public abstract String get1x1(RandomSource random);

        public abstract String get1x1Secret(RandomSource random);

        public abstract String get1x2SideEntrance(RandomSource random, boolean isStairsRoom);

        public abstract String get1x2FrontEntrance(RandomSource random, boolean isStairsRoom);

        public abstract String get1x2Secret(RandomSource random);

        public abstract String get2x2(RandomSource random);

        public abstract String get2x2Secret(RandomSource random);
    }

    private static class FirstFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {

        private FirstFloorRoomCollection() {}

        @Override
        public String get1x1(RandomSource random) {
            int i = random.nextInt(5);

            return "1x1_a" + (i + 1);
        }

        @Override
        public String get1x1Secret(RandomSource random) {
            int i = random.nextInt(4);

            return "1x1_as" + (i + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource random, boolean isStairsRoom) {
            int i = random.nextInt(9);

            return "1x2_a" + (i + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource random, boolean isStairsRoom) {
            int i = random.nextInt(5);

            return "1x2_b" + (i + 1);
        }

        @Override
        public String get1x2Secret(RandomSource random) {
            int i = random.nextInt(2);

            return "1x2_s" + (i + 1);
        }

        @Override
        public String get2x2(RandomSource random) {
            int i = random.nextInt(4);

            return "2x2_a" + (i + 1);
        }

        @Override
        public String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    private static class SecondFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {

        private SecondFloorRoomCollection() {}

        @Override
        public String get1x1(RandomSource random) {
            int i = random.nextInt(5);

            return "1x1_b" + (i + 1);
        }

        @Override
        public String get1x1Secret(RandomSource random) {
            int i = random.nextInt(4);

            return "1x1_as" + (i + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource random, boolean isStairsRoom) {
            if (isStairsRoom) {
                return "1x2_c_stairs";
            } else {
                int i = random.nextInt(4);

                return "1x2_c" + (i + 1);
            }
        }

        @Override
        public String get1x2FrontEntrance(RandomSource random, boolean isStairsRoom) {
            if (isStairsRoom) {
                return "1x2_d_stairs";
            } else {
                int i = random.nextInt(5);

                return "1x2_d" + (i + 1);
            }
        }

        @Override
        public String get1x2Secret(RandomSource random) {
            int i = random.nextInt(1);

            return "1x2_se" + (i + 1);
        }

        @Override
        public String get2x2(RandomSource random) {
            int i = random.nextInt(5);

            return "2x2_b" + (i + 1);
        }

        @Override
        public String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    private static class ThirdFloorRoomCollection extends WoodlandMansionPieces.SecondFloorRoomCollection {

        private ThirdFloorRoomCollection() {}
    }
}
