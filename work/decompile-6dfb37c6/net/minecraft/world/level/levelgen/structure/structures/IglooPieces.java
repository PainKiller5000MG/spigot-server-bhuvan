package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class IglooPieces {

    public static final int GENERATION_HEIGHT = 90;
    private static final Identifier STRUCTURE_LOCATION_IGLOO = Identifier.withDefaultNamespace("igloo/top");
    private static final Identifier STRUCTURE_LOCATION_LADDER = Identifier.withDefaultNamespace("igloo/middle");
    private static final Identifier STRUCTURE_LOCATION_LABORATORY = Identifier.withDefaultNamespace("igloo/bottom");
    private static final Map<Identifier, BlockPos> PIVOTS = ImmutableMap.of(IglooPieces.STRUCTURE_LOCATION_IGLOO, new BlockPos(3, 5, 5), IglooPieces.STRUCTURE_LOCATION_LADDER, new BlockPos(1, 3, 1), IglooPieces.STRUCTURE_LOCATION_LABORATORY, new BlockPos(3, 6, 7));
    private static final Map<Identifier, BlockPos> OFFSETS = ImmutableMap.of(IglooPieces.STRUCTURE_LOCATION_IGLOO, BlockPos.ZERO, IglooPieces.STRUCTURE_LOCATION_LADDER, new BlockPos(2, -3, 4), IglooPieces.STRUCTURE_LOCATION_LABORATORY, new BlockPos(0, -3, -2));

    public IglooPieces() {}

    public static void addPieces(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, StructurePieceAccessor structurePieceAccessor, RandomSource random) {
        if (random.nextDouble() < 0.5D) {
            int i = random.nextInt(8) + 4;

            structurePieceAccessor.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, IglooPieces.STRUCTURE_LOCATION_LABORATORY, position, rotation, i * 3));

            for (int j = 0; j < i - 1; ++j) {
                structurePieceAccessor.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, IglooPieces.STRUCTURE_LOCATION_LADDER, position, rotation, j * 3));
            }
        }

        structurePieceAccessor.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, IglooPieces.STRUCTURE_LOCATION_IGLOO, position, rotation, 0));
    }

    public static class IglooPiece extends TemplateStructurePiece {

        public IglooPiece(StructureTemplateManager structureTemplateManager, Identifier templateLocation, BlockPos position, Rotation rotation, int depth) {
            super(StructurePieceType.IGLOO, 0, structureTemplateManager, templateLocation, templateLocation.toString(), makeSettings(rotation, templateLocation), makePosition(templateLocation, position, depth));
        }

        public IglooPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(StructurePieceType.IGLOO, tag, structureTemplateManager, (identifier) -> {
                return makeSettings((Rotation) tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow(), identifier);
            });
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, Identifier templateLocation) {
            return (new StructurePlaceSettings()).setRotation(rotation).setMirror(Mirror.NONE).setRotationPivot((BlockPos) IglooPieces.PIVOTS.get(templateLocation)).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        }

        private static BlockPos makePosition(Identifier templateLocation, BlockPos position, int depth) {
            return position.offset((Vec3i) IglooPieces.OFFSETS.get(templateLocation)).below(depth);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        }

        @Override
        protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
            if ("chest".equals(markerId)) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
                BlockEntity blockentity = level.getBlockEntity(position.below());

                if (blockentity instanceof ChestBlockEntity) {
                    ((ChestBlockEntity) blockentity).setLootTable(BuiltInLootTables.IGLOO_CHEST, random.nextLong());
                }

            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            Identifier identifier = Identifier.parse(this.templateName);
            StructurePlaceSettings structureplacesettings = makeSettings(this.placeSettings.getRotation(), identifier);
            BlockPos blockpos1 = (BlockPos) IglooPieces.OFFSETS.get(identifier);
            BlockPos blockpos2 = this.templatePosition.offset(StructureTemplate.calculateRelativePosition(structureplacesettings, new BlockPos(3 - blockpos1.getX(), 0, -blockpos1.getZ())));
            int i = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockpos2.getX(), blockpos2.getZ());
            BlockPos blockpos3 = this.templatePosition;

            this.templatePosition = this.templatePosition.offset(0, i - 90 - 1, 0);
            super.postProcess(level, structureManager, generator, random, chunkBB, chunkPos, referencePos);
            if (identifier.equals(IglooPieces.STRUCTURE_LOCATION_IGLOO)) {
                BlockPos blockpos4 = this.templatePosition.offset(StructureTemplate.calculateRelativePosition(structureplacesettings, new BlockPos(3, 0, 5)));
                BlockState blockstate = level.getBlockState(blockpos4.below());

                if (!blockstate.isAir() && !blockstate.is(Blocks.LADDER)) {
                    level.setBlock(blockpos4, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                }
            }

            this.templatePosition = blockpos3;
        }
    }
}
