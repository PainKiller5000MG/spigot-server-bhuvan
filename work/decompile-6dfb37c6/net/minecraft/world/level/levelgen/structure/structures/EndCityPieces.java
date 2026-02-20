package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class EndCityPieces {

    private static final int MAX_GEN_DEPTH = 8;
    private static final EndCityPieces.SectionGenerator HOUSE_TOWER_GENERATOR = new EndCityPieces.SectionGenerator() {
        @Override
        public void init() {}

        @Override
        public boolean generate(StructureTemplateManager structureTemplateManager, int genDepth, EndCityPieces.EndCityPiece parent, BlockPos offset, List<StructurePiece> pieces, RandomSource random) {
            if (genDepth > 8) {
                return false;
            } else {
                Rotation rotation = parent.placeSettings().getRotation();
                EndCityPieces.EndCityPiece endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, parent, offset, "base_floor", rotation, true));
                int j = random.nextInt(3);

                if (j == 0) {
                    EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 4, -1), "base_roof", rotation, true));
                } else if (j == 1) {
                    endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 0, -1), "second_floor_2", rotation, false));
                    endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 8, -1), "second_roof", rotation, false));
                    EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.TOWER_GENERATOR, genDepth + 1, endcitypieces_endcitypiece1, (BlockPos) null, pieces, random);
                } else if (j == 2) {
                    endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 0, -1), "second_floor_2", rotation, false));
                    endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 4, -1), "third_floor_2", rotation, false));
                    endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 8, -1), "third_roof", rotation, true));
                    EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.TOWER_GENERATOR, genDepth + 1, endcitypieces_endcitypiece1, (BlockPos) null, pieces, random);
                }

                return true;
            }
        }
    };
    private static final List<Tuple<Rotation, BlockPos>> TOWER_BRIDGES = Lists.newArrayList(new Tuple[]{new Tuple(Rotation.NONE, new BlockPos(1, -1, 0)), new Tuple(Rotation.CLOCKWISE_90, new BlockPos(6, -1, 1)), new Tuple(Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, -1, 5)), new Tuple(Rotation.CLOCKWISE_180, new BlockPos(5, -1, 6))});
    private static final EndCityPieces.SectionGenerator TOWER_GENERATOR = new EndCityPieces.SectionGenerator() {
        @Override
        public void init() {}

        @Override
        public boolean generate(StructureTemplateManager structureTemplateManager, int genDepth, EndCityPieces.EndCityPiece parent, BlockPos offset, List<StructurePiece> pieces, RandomSource random) {
            Rotation rotation = parent.placeSettings().getRotation();
            EndCityPieces.EndCityPiece endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, parent, new BlockPos(3 + random.nextInt(2), -3, 3 + random.nextInt(2)), "tower_base", rotation, true));

            endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, 7, 0), "tower_piece", rotation, true));
            EndCityPieces.EndCityPiece endcitypieces_endcitypiece2 = random.nextInt(3) == 0 ? endcitypieces_endcitypiece1 : null;
            int j = 1 + random.nextInt(3);

            for (int k = 0; k < j; ++k) {
                endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, 4, 0), "tower_piece", rotation, true));
                if (k < j - 1 && random.nextBoolean()) {
                    endcitypieces_endcitypiece2 = endcitypieces_endcitypiece1;
                }
            }

            if (endcitypieces_endcitypiece2 != null) {
                for (Tuple<Rotation, BlockPos> tuple : EndCityPieces.TOWER_BRIDGES) {
                    if (random.nextBoolean()) {
                        EndCityPieces.EndCityPiece endcitypieces_endcitypiece3 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece2, tuple.getB(), "bridge_end", rotation.getRotated(tuple.getA()), true));

                        EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.TOWER_BRIDGE_GENERATOR, genDepth + 1, endcitypieces_endcitypiece3, (BlockPos) null, pieces, random);
                    }
                }

                EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 4, -1), "tower_top", rotation, true));
            } else {
                if (genDepth != 7) {
                    return EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.FAT_TOWER_GENERATOR, genDepth + 1, endcitypieces_endcitypiece1, (BlockPos) null, pieces, random);
                }

                EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-1, 4, -1), "tower_top", rotation, true));
            }

            return true;
        }
    };
    private static final EndCityPieces.SectionGenerator TOWER_BRIDGE_GENERATOR = new EndCityPieces.SectionGenerator() {
        public boolean shipCreated;

        @Override
        public void init() {
            this.shipCreated = false;
        }

        @Override
        public boolean generate(StructureTemplateManager structureTemplateManager, int genDepth, EndCityPieces.EndCityPiece parent, BlockPos offset, List<StructurePiece> pieces, RandomSource random) {
            Rotation rotation = parent.placeSettings().getRotation();
            int j = random.nextInt(4) + 1;
            EndCityPieces.EndCityPiece endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, parent, new BlockPos(0, 0, -4), "bridge_piece", rotation, true));

            endcitypieces_endcitypiece1.setGenDepth(-1);
            int k = 0;

            for (int l = 0; l < j; ++l) {
                if (random.nextBoolean()) {
                    endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, k, -4), "bridge_piece", rotation, true));
                    k = 0;
                } else {
                    if (random.nextBoolean()) {
                        endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, k, -4), "bridge_steep_stairs", rotation, true));
                    } else {
                        endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, k, -8), "bridge_gentle_stairs", rotation, true));
                    }

                    k = 4;
                }
            }

            if (!this.shipCreated && random.nextInt(10 - genDepth) == 0) {
                EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-8 + random.nextInt(8), k, -70 + random.nextInt(10)), "ship", rotation, true));
                this.shipCreated = true;
            } else if (!EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.HOUSE_TOWER_GENERATOR, genDepth + 1, endcitypieces_endcitypiece1, new BlockPos(-3, k + 1, -11), pieces, random)) {
                return false;
            }

            endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(4, k, 0), "bridge_end", rotation.getRotated(Rotation.CLOCKWISE_180), true));
            endcitypieces_endcitypiece1.setGenDepth(-1);
            return true;
        }
    };
    private static final List<Tuple<Rotation, BlockPos>> FAT_TOWER_BRIDGES = Lists.newArrayList(new Tuple[]{new Tuple(Rotation.NONE, new BlockPos(4, -1, 0)), new Tuple(Rotation.CLOCKWISE_90, new BlockPos(12, -1, 4)), new Tuple(Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, -1, 8)), new Tuple(Rotation.CLOCKWISE_180, new BlockPos(8, -1, 12))});
    private static final EndCityPieces.SectionGenerator FAT_TOWER_GENERATOR = new EndCityPieces.SectionGenerator() {
        @Override
        public void init() {}

        @Override
        public boolean generate(StructureTemplateManager structureTemplateManager, int genDepth, EndCityPieces.EndCityPiece parent, BlockPos offset, List<StructurePiece> pieces, RandomSource random) {
            Rotation rotation = parent.placeSettings().getRotation();
            EndCityPieces.EndCityPiece endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, parent, new BlockPos(-3, 4, -3), "fat_tower_base", rotation, true));

            endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, 4, 0), "fat_tower_middle", rotation, true));

            for (int j = 0; j < 2 && random.nextInt(3) != 0; ++j) {
                endcitypieces_endcitypiece1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(0, 8, 0), "fat_tower_middle", rotation, true));

                for (Tuple<Rotation, BlockPos> tuple : EndCityPieces.FAT_TOWER_BRIDGES) {
                    if (random.nextBoolean()) {
                        EndCityPieces.EndCityPiece endcitypieces_endcitypiece2 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, tuple.getB(), "bridge_end", rotation.getRotated(tuple.getA()), true));

                        EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.TOWER_BRIDGE_GENERATOR, genDepth + 1, endcitypieces_endcitypiece2, (BlockPos) null, pieces, random);
                    }
                }
            }

            EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_endcitypiece1, new BlockPos(-2, 8, -2), "fat_tower_top", rotation, true));
            return true;
        }
    };

    public EndCityPieces() {}

    private static EndCityPieces.EndCityPiece addPiece(StructureTemplateManager structureTemplateManager, EndCityPieces.EndCityPiece parent, BlockPos offset, String templateName, Rotation rotation, boolean overwrite) {
        EndCityPieces.EndCityPiece endcitypieces_endcitypiece1 = new EndCityPieces.EndCityPiece(structureTemplateManager, templateName, parent.templatePosition(), rotation, overwrite);
        BlockPos blockpos1 = parent.template().calculateConnectedPosition(parent.placeSettings(), offset, endcitypieces_endcitypiece1.placeSettings(), BlockPos.ZERO);

        endcitypieces_endcitypiece1.move(blockpos1.getX(), blockpos1.getY(), blockpos1.getZ());
        return endcitypieces_endcitypiece1;
    }

    public static void startHouseTower(StructureTemplateManager structureTemplateManager, BlockPos origin, Rotation rotation, List<StructurePiece> pieces, RandomSource random) {
        EndCityPieces.FAT_TOWER_GENERATOR.init();
        EndCityPieces.HOUSE_TOWER_GENERATOR.init();
        EndCityPieces.TOWER_BRIDGE_GENERATOR.init();
        EndCityPieces.TOWER_GENERATOR.init();
        EndCityPieces.EndCityPiece endcitypieces_endcitypiece = addHelper(pieces, new EndCityPieces.EndCityPiece(structureTemplateManager, "base_floor", origin, rotation, true));

        endcitypieces_endcitypiece = addHelper(pieces, addPiece(structureTemplateManager, endcitypieces_endcitypiece, new BlockPos(-1, 0, -1), "second_floor_1", rotation, false));
        endcitypieces_endcitypiece = addHelper(pieces, addPiece(structureTemplateManager, endcitypieces_endcitypiece, new BlockPos(-1, 4, -1), "third_floor_1", rotation, false));
        endcitypieces_endcitypiece = addHelper(pieces, addPiece(structureTemplateManager, endcitypieces_endcitypiece, new BlockPos(-1, 8, -1), "third_roof", rotation, true));
        recursiveChildren(structureTemplateManager, EndCityPieces.TOWER_GENERATOR, 1, endcitypieces_endcitypiece, (BlockPos) null, pieces, random);
    }

    private static EndCityPieces.EndCityPiece addHelper(List<StructurePiece> pieces, EndCityPieces.EndCityPiece piece) {
        pieces.add(piece);
        return piece;
    }

    private static boolean recursiveChildren(StructureTemplateManager structureTemplateManager, EndCityPieces.SectionGenerator generator, int genDepth, EndCityPieces.EndCityPiece parent, BlockPos offset, List<StructurePiece> pieces, RandomSource random) {
        if (genDepth > 8) {
            return false;
        } else {
            List<StructurePiece> list1 = Lists.newArrayList();

            if (generator.generate(structureTemplateManager, genDepth, parent, offset, list1, random)) {
                boolean flag = false;
                int j = random.nextInt();

                for (StructurePiece structurepiece : list1) {
                    structurepiece.setGenDepth(j);
                    StructurePiece structurepiece1 = StructurePiece.findCollisionPiece(pieces, structurepiece.getBoundingBox());

                    if (structurepiece1 != null && structurepiece1.getGenDepth() != parent.getGenDepth()) {
                        flag = true;
                        break;
                    }
                }

                if (!flag) {
                    pieces.addAll(list1);
                    return true;
                }
            }

            return false;
        }
    }

    public static class EndCityPiece extends TemplateStructurePiece {

        public EndCityPiece(StructureTemplateManager structureTemplateManager, String templateName, BlockPos position, Rotation rotation, boolean overwrite) {
            super(StructurePieceType.END_CITY_PIECE, 0, structureTemplateManager, makeIdentifier(templateName), templateName, makeSettings(overwrite, rotation), position);
        }

        public EndCityPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(StructurePieceType.END_CITY_PIECE, tag, structureTemplateManager, (identifier) -> {
                return makeSettings(tag.getBooleanOr("OW", false), (Rotation) tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow());
            });
        }

        private static StructurePlaceSettings makeSettings(boolean overwrite, Rotation rotation) {
            BlockIgnoreProcessor blockignoreprocessor = overwrite ? BlockIgnoreProcessor.STRUCTURE_BLOCK : BlockIgnoreProcessor.STRUCTURE_AND_AIR;

            return (new StructurePlaceSettings()).setIgnoreEntities(true).addProcessor(blockignoreprocessor).setRotation(rotation);
        }

        @Override
        protected Identifier makeTemplateLocation() {
            return makeIdentifier(this.templateName);
        }

        private static Identifier makeIdentifier(String templateName) {
            return Identifier.withDefaultNamespace("end_city/" + templateName);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
            tag.putBoolean("OW", this.placeSettings.getProcessors().get(0) == BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
            if (markerId.startsWith("Chest")) {
                BlockPos blockpos1 = position.below();

                if (chunkBB.isInside(blockpos1)) {
                    RandomizableContainer.setBlockEntityLootTable(level, random, blockpos1, BuiltInLootTables.END_CITY_TREASURE);
                }
            } else if (chunkBB.isInside(position) && Level.isInSpawnableBounds(position)) {
                if (markerId.startsWith("Sentry")) {
                    Shulker shulker = EntityType.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);

                    if (shulker != null) {
                        shulker.setPos((double) position.getX() + 0.5D, (double) position.getY(), (double) position.getZ() + 0.5D);
                        level.addFreshEntity(shulker);
                    }
                } else if (markerId.startsWith("Elytra")) {
                    ItemFrame itemframe = new ItemFrame(level.getLevel(), position, this.placeSettings.getRotation().rotate(Direction.SOUTH));

                    itemframe.setItem(new ItemStack(Items.ELYTRA), false);
                    level.addFreshEntity(itemframe);
                }
            }

        }
    }

    private interface SectionGenerator {

        void init();

        boolean generate(StructureTemplateManager structureTemplateManager, int genDepth, EndCityPieces.EndCityPiece parent, BlockPos offset, List<StructurePiece> pieces, RandomSource random);
    }
}
