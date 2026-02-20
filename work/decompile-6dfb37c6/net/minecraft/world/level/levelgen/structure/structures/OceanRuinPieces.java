package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendLoot;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class OceanRuinPieces {

    private static final StructureProcessor WARM_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(Blocks.SAND, Blocks.SUSPICIOUS_SAND, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY);
    private static final StructureProcessor COLD_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY);
    private static final Identifier[] WARM_RUINS = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/warm_1"), Identifier.withDefaultNamespace("underwater_ruin/warm_2"), Identifier.withDefaultNamespace("underwater_ruin/warm_3"), Identifier.withDefaultNamespace("underwater_ruin/warm_4"), Identifier.withDefaultNamespace("underwater_ruin/warm_5"), Identifier.withDefaultNamespace("underwater_ruin/warm_6"), Identifier.withDefaultNamespace("underwater_ruin/warm_7"), Identifier.withDefaultNamespace("underwater_ruin/warm_8")};
    private static final Identifier[] RUINS_BRICK = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/brick_1"), Identifier.withDefaultNamespace("underwater_ruin/brick_2"), Identifier.withDefaultNamespace("underwater_ruin/brick_3"), Identifier.withDefaultNamespace("underwater_ruin/brick_4"), Identifier.withDefaultNamespace("underwater_ruin/brick_5"), Identifier.withDefaultNamespace("underwater_ruin/brick_6"), Identifier.withDefaultNamespace("underwater_ruin/brick_7"), Identifier.withDefaultNamespace("underwater_ruin/brick_8")};
    private static final Identifier[] RUINS_CRACKED = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/cracked_1"), Identifier.withDefaultNamespace("underwater_ruin/cracked_2"), Identifier.withDefaultNamespace("underwater_ruin/cracked_3"), Identifier.withDefaultNamespace("underwater_ruin/cracked_4"), Identifier.withDefaultNamespace("underwater_ruin/cracked_5"), Identifier.withDefaultNamespace("underwater_ruin/cracked_6"), Identifier.withDefaultNamespace("underwater_ruin/cracked_7"), Identifier.withDefaultNamespace("underwater_ruin/cracked_8")};
    private static final Identifier[] RUINS_MOSSY = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/mossy_1"), Identifier.withDefaultNamespace("underwater_ruin/mossy_2"), Identifier.withDefaultNamespace("underwater_ruin/mossy_3"), Identifier.withDefaultNamespace("underwater_ruin/mossy_4"), Identifier.withDefaultNamespace("underwater_ruin/mossy_5"), Identifier.withDefaultNamespace("underwater_ruin/mossy_6"), Identifier.withDefaultNamespace("underwater_ruin/mossy_7"), Identifier.withDefaultNamespace("underwater_ruin/mossy_8")};
    private static final Identifier[] BIG_RUINS_BRICK = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/big_brick_1"), Identifier.withDefaultNamespace("underwater_ruin/big_brick_2"), Identifier.withDefaultNamespace("underwater_ruin/big_brick_3"), Identifier.withDefaultNamespace("underwater_ruin/big_brick_8")};
    private static final Identifier[] BIG_RUINS_MOSSY = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/big_mossy_1"), Identifier.withDefaultNamespace("underwater_ruin/big_mossy_2"), Identifier.withDefaultNamespace("underwater_ruin/big_mossy_3"), Identifier.withDefaultNamespace("underwater_ruin/big_mossy_8")};
    private static final Identifier[] BIG_RUINS_CRACKED = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/big_cracked_1"), Identifier.withDefaultNamespace("underwater_ruin/big_cracked_2"), Identifier.withDefaultNamespace("underwater_ruin/big_cracked_3"), Identifier.withDefaultNamespace("underwater_ruin/big_cracked_8")};
    private static final Identifier[] BIG_WARM_RUINS = new Identifier[]{Identifier.withDefaultNamespace("underwater_ruin/big_warm_4"), Identifier.withDefaultNamespace("underwater_ruin/big_warm_5"), Identifier.withDefaultNamespace("underwater_ruin/big_warm_6"), Identifier.withDefaultNamespace("underwater_ruin/big_warm_7")};

    public OceanRuinPieces() {}

    private static StructureProcessor archyRuleProcessor(Block candidateBlock, Block replacementBlock, ResourceKey<LootTable> lootTable) {
        return new CappedProcessor(new RuleProcessor(List.of(new ProcessorRule(new BlockMatchTest(candidateBlock), AlwaysTrueTest.INSTANCE, PosAlwaysTrueTest.INSTANCE, replacementBlock.defaultBlockState(), new AppendLoot(lootTable)))), ConstantInt.of(5));
    }

    private static Identifier getSmallWarmRuin(RandomSource random) {
        return (Identifier) Util.getRandom(OceanRuinPieces.WARM_RUINS, random);
    }

    private static Identifier getBigWarmRuin(RandomSource random) {
        return (Identifier) Util.getRandom(OceanRuinPieces.BIG_WARM_RUINS, random);
    }

    public static void addPieces(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, StructurePieceAccessor structurePieceAccessor, RandomSource random, OceanRuinStructure structure) {
        boolean flag = random.nextFloat() <= structure.largeProbability;
        float f = flag ? 0.9F : 0.8F;

        addPiece(structureTemplateManager, position, rotation, structurePieceAccessor, random, structure, flag, f);
        if (flag && random.nextFloat() <= structure.clusterProbability) {
            addClusterRuins(structureTemplateManager, random, rotation, position, structure, structurePieceAccessor);
        }

    }

    private static void addClusterRuins(StructureTemplateManager structureTemplateManager, RandomSource random, Rotation rotation, BlockPos p, OceanRuinStructure structure, StructurePieceAccessor structurePieceAccessor) {
        BlockPos blockpos1 = new BlockPos(p.getX(), 90, p.getZ());
        BlockPos blockpos2 = StructureTemplate.transform(new BlockPos(15, 0, 15), Mirror.NONE, rotation, BlockPos.ZERO).offset(blockpos1);
        BoundingBox boundingbox = BoundingBox.fromCorners(blockpos1, blockpos2);
        BlockPos blockpos3 = new BlockPos(Math.min(blockpos1.getX(), blockpos2.getX()), blockpos1.getY(), Math.min(blockpos1.getZ(), blockpos2.getZ()));
        List<BlockPos> list = allPositions(random, blockpos3);
        int i = Mth.nextInt(random, 4, 8);

        for (int j = 0; j < i; ++j) {
            if (!list.isEmpty()) {
                int k = random.nextInt(list.size());
                BlockPos blockpos4 = (BlockPos) list.remove(k);
                Rotation rotation1 = Rotation.getRandom(random);
                BlockPos blockpos5 = StructureTemplate.transform(new BlockPos(5, 0, 6), Mirror.NONE, rotation1, BlockPos.ZERO).offset(blockpos4);
                BoundingBox boundingbox1 = BoundingBox.fromCorners(blockpos4, blockpos5);

                if (!boundingbox1.intersects(boundingbox)) {
                    addPiece(structureTemplateManager, blockpos4, rotation1, structurePieceAccessor, random, structure, false, 0.8F);
                }
            }
        }

    }

    private static List<BlockPos> allPositions(RandomSource random, BlockPos origin) {
        List<BlockPos> list = Lists.newArrayList();

        list.add(origin.offset(-16 + Mth.nextInt(random, 1, 8), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(origin.offset(-16 + Mth.nextInt(random, 1, 8), 0, Mth.nextInt(random, 1, 7)));
        list.add(origin.offset(-16 + Mth.nextInt(random, 1, 8), 0, -16 + Mth.nextInt(random, 4, 8)));
        list.add(origin.offset(Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(origin.offset(Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 6)));
        list.add(origin.offset(16 + Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 3, 8)));
        list.add(origin.offset(16 + Mth.nextInt(random, 1, 7), 0, Mth.nextInt(random, 1, 7)));
        list.add(origin.offset(16 + Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 8)));
        return list;
    }

    private static void addPiece(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, StructurePieceAccessor structurePieceAccessor, RandomSource random, OceanRuinStructure structure, boolean isLarge, float baseIntegrity) {
        switch (structure.biomeTemp) {
            case WARM:
            default:
                Identifier identifier = isLarge ? getBigWarmRuin(random) : getSmallWarmRuin(random);

                structurePieceAccessor.addPiece(new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, identifier, position, rotation, baseIntegrity, structure.biomeTemp, isLarge));
                break;
            case COLD:
                Identifier[] aidentifier = isLarge ? OceanRuinPieces.BIG_RUINS_BRICK : OceanRuinPieces.RUINS_BRICK;
                Identifier[] aidentifier1 = isLarge ? OceanRuinPieces.BIG_RUINS_CRACKED : OceanRuinPieces.RUINS_CRACKED;
                Identifier[] aidentifier2 = isLarge ? OceanRuinPieces.BIG_RUINS_MOSSY : OceanRuinPieces.RUINS_MOSSY;
                int i = random.nextInt(aidentifier.length);

                structurePieceAccessor.addPiece(new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, aidentifier[i], position, rotation, baseIntegrity, structure.biomeTemp, isLarge));
                structurePieceAccessor.addPiece(new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, aidentifier1[i], position, rotation, 0.7F, structure.biomeTemp, isLarge));
                structurePieceAccessor.addPiece(new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, aidentifier2[i], position, rotation, 0.5F, structure.biomeTemp, isLarge));
        }

    }

    public static class OceanRuinPiece extends TemplateStructurePiece {

        private final OceanRuinStructure.Type biomeType;
        private final float integrity;
        private final boolean isLarge;

        public OceanRuinPiece(StructureTemplateManager structureTemplateManager, Identifier templateLocation, BlockPos position, Rotation rotation, float integrity, OceanRuinStructure.Type biomeType, boolean isLarge) {
            super(StructurePieceType.OCEAN_RUIN, 0, structureTemplateManager, templateLocation, templateLocation.toString(), makeSettings(rotation, integrity, biomeType), position);
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = isLarge;
        }

        private OceanRuinPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag, Rotation rotation, float integrity, OceanRuinStructure.Type biomeType, boolean isLarge) {
            super(StructurePieceType.OCEAN_RUIN, tag, structureTemplateManager, (identifier) -> {
                return makeSettings(rotation, integrity, biomeType);
            });
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = isLarge;
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, float integrity, OceanRuinStructure.Type biomeType) {
            StructureProcessor structureprocessor = biomeType == OceanRuinStructure.Type.COLD ? OceanRuinPieces.COLD_SUSPICIOUS_BLOCK_PROCESSOR : OceanRuinPieces.WARM_SUSPICIOUS_BLOCK_PROCESSOR;

            return (new StructurePlaceSettings()).setRotation(rotation).setMirror(Mirror.NONE).addProcessor(new BlockRotProcessor(integrity)).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR).addProcessor(structureprocessor);
        }

        public static OceanRuinPieces.OceanRuinPiece create(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            Rotation rotation = (Rotation) tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow();
            float f = tag.getFloatOr("Integrity", 0.0F);
            OceanRuinStructure.Type oceanruinstructure_type = (OceanRuinStructure.Type) tag.read("BiomeType", OceanRuinStructure.Type.LEGACY_CODEC).orElseThrow();
            boolean flag = tag.getBooleanOr("IsLarge", false);

            return new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, tag, rotation, f, oceanruinstructure_type, flag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
            tag.putFloat("Integrity", this.integrity);
            tag.store("BiomeType", OceanRuinStructure.Type.LEGACY_CODEC, this.biomeType);
            tag.putBoolean("IsLarge", this.isLarge);
        }

        @Override
        protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
            if ("chest".equals(markerId)) {
                level.setBlock(position, (BlockState) Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, level.getFluidState(position).is(FluidTags.WATER)), 2);
                BlockEntity blockentity = level.getBlockEntity(position);

                if (blockentity instanceof ChestBlockEntity) {
                    ((ChestBlockEntity) blockentity).setLootTable(this.isLarge ? BuiltInLootTables.UNDERWATER_RUIN_BIG : BuiltInLootTables.UNDERWATER_RUIN_SMALL, random.nextLong());
                }
            } else if ("drowned".equals(markerId)) {
                Drowned drowned = EntityType.DROWNED.create(level.getLevel(), EntitySpawnReason.STRUCTURE);

                if (drowned != null) {
                    drowned.setPersistenceRequired();
                    drowned.snapTo(position, 0.0F, 0.0F);
                    drowned.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.STRUCTURE, (SpawnGroupData) null);
                    level.addFreshEntityWithPassengers(drowned);
                    if (position.getY() > level.getSeaLevel()) {
                        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
                    } else {
                        level.setBlock(position, Blocks.WATER.defaultBlockState(), 2);
                    }
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            int i = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.templatePosition.getX(), this.templatePosition.getZ());

            this.templatePosition = new BlockPos(this.templatePosition.getX(), i, this.templatePosition.getZ());
            BlockPos blockpos1 = StructureTemplate.transform(new BlockPos(this.template.getSize().getX() - 1, 0, this.template.getSize().getZ() - 1), Mirror.NONE, this.placeSettings.getRotation(), BlockPos.ZERO).offset(this.templatePosition);

            this.templatePosition = new BlockPos(this.templatePosition.getX(), this.getHeight(this.templatePosition, level, blockpos1), this.templatePosition.getZ());
            super.postProcess(level, structureManager, generator, random, chunkBB, chunkPos, referencePos);
        }

        private int getHeight(BlockPos pos, BlockGetter level, BlockPos corner) {
            int i = pos.getY();
            int j = 512;
            int k = i - 1;
            int l = 0;

            for (BlockPos blockpos2 : BlockPos.betweenClosed(pos, corner)) {
                int i1 = blockpos2.getX();
                int j1 = blockpos2.getZ();
                int k1 = pos.getY() - 1;
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(i1, k1, j1);
                BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

                for (FluidState fluidstate = level.getFluidState(blockpos_mutableblockpos); (blockstate.isAir() || fluidstate.is(FluidTags.WATER) || blockstate.is(BlockTags.ICE)) && k1 > level.getMinY() + 1; fluidstate = level.getFluidState(blockpos_mutableblockpos)) {
                    --k1;
                    blockpos_mutableblockpos.set(i1, k1, j1);
                    blockstate = level.getBlockState(blockpos_mutableblockpos);
                }

                j = Math.min(j, k1);
                if (k1 < k - 2) {
                    ++l;
                }
            }

            int l1 = Math.abs(pos.getX() - corner.getX());

            if (k - j > 2 && l > l1 - 2) {
                i = j + 1;
            }

            return i;
        }
    }
}
