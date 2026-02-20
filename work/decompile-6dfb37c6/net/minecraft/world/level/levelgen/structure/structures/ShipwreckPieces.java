package net.minecraft.world.level.levelgen.structure.structures;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class ShipwreckPieces {

    private static final int NUMBER_OF_BLOCKS_ALLOWED_IN_WORLD_GEN_REGION = 32;
    private static final BlockPos PIVOT = new BlockPos(4, 0, 15);
    private static final Identifier[] STRUCTURE_LOCATION_BEACHED = new Identifier[]{Identifier.withDefaultNamespace("shipwreck/with_mast"), Identifier.withDefaultNamespace("shipwreck/sideways_full"), Identifier.withDefaultNamespace("shipwreck/sideways_fronthalf"), Identifier.withDefaultNamespace("shipwreck/sideways_backhalf"), Identifier.withDefaultNamespace("shipwreck/rightsideup_full"), Identifier.withDefaultNamespace("shipwreck/rightsideup_fronthalf"), Identifier.withDefaultNamespace("shipwreck/rightsideup_backhalf"), Identifier.withDefaultNamespace("shipwreck/with_mast_degraded"), Identifier.withDefaultNamespace("shipwreck/rightsideup_full_degraded"), Identifier.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"), Identifier.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")};
    private static final Identifier[] STRUCTURE_LOCATION_OCEAN = new Identifier[]{Identifier.withDefaultNamespace("shipwreck/with_mast"), Identifier.withDefaultNamespace("shipwreck/upsidedown_full"), Identifier.withDefaultNamespace("shipwreck/upsidedown_fronthalf"), Identifier.withDefaultNamespace("shipwreck/upsidedown_backhalf"), Identifier.withDefaultNamespace("shipwreck/sideways_full"), Identifier.withDefaultNamespace("shipwreck/sideways_fronthalf"), Identifier.withDefaultNamespace("shipwreck/sideways_backhalf"), Identifier.withDefaultNamespace("shipwreck/rightsideup_full"), Identifier.withDefaultNamespace("shipwreck/rightsideup_fronthalf"), Identifier.withDefaultNamespace("shipwreck/rightsideup_backhalf"), Identifier.withDefaultNamespace("shipwreck/with_mast_degraded"), Identifier.withDefaultNamespace("shipwreck/upsidedown_full_degraded"), Identifier.withDefaultNamespace("shipwreck/upsidedown_fronthalf_degraded"), Identifier.withDefaultNamespace("shipwreck/upsidedown_backhalf_degraded"), Identifier.withDefaultNamespace("shipwreck/sideways_full_degraded"), Identifier.withDefaultNamespace("shipwreck/sideways_fronthalf_degraded"), Identifier.withDefaultNamespace("shipwreck/sideways_backhalf_degraded"), Identifier.withDefaultNamespace("shipwreck/rightsideup_full_degraded"), Identifier.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"), Identifier.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")};
    private static final Map<String, ResourceKey<LootTable>> MARKERS_TO_LOOT = Map.of("map_chest", BuiltInLootTables.SHIPWRECK_MAP, "treasure_chest", BuiltInLootTables.SHIPWRECK_TREASURE, "supply_chest", BuiltInLootTables.SHIPWRECK_SUPPLY);

    public ShipwreckPieces() {}

    public static ShipwreckPieces.ShipwreckPiece addRandomPiece(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, StructurePieceAccessor structurePieceAccessor, RandomSource random, boolean isBeached) {
        Identifier identifier = (Identifier) Util.getRandom(isBeached ? ShipwreckPieces.STRUCTURE_LOCATION_BEACHED : ShipwreckPieces.STRUCTURE_LOCATION_OCEAN, random);
        ShipwreckPieces.ShipwreckPiece shipwreckpieces_shipwreckpiece = new ShipwreckPieces.ShipwreckPiece(structureTemplateManager, identifier, position, rotation, isBeached);

        structurePieceAccessor.addPiece(shipwreckpieces_shipwreckpiece);
        return shipwreckpieces_shipwreckpiece;
    }

    public static class ShipwreckPiece extends TemplateStructurePiece {

        private final boolean isBeached;

        public ShipwreckPiece(StructureTemplateManager structureTemplateManager, Identifier templateLocation, BlockPos position, Rotation rotation, boolean isBeached) {
            super(StructurePieceType.SHIPWRECK_PIECE, 0, structureTemplateManager, templateLocation, templateLocation.toString(), makeSettings(rotation), position);
            this.isBeached = isBeached;
        }

        public ShipwreckPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(StructurePieceType.SHIPWRECK_PIECE, tag, structureTemplateManager, (identifier) -> {
                return makeSettings((Rotation) tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow());
            });
            this.isBeached = tag.getBooleanOr("isBeached", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("isBeached", this.isBeached);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation) {
            return (new StructurePlaceSettings()).setRotation(rotation).setMirror(Mirror.NONE).setRotationPivot(ShipwreckPieces.PIVOT).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        @Override
        protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
            ResourceKey<LootTable> resourcekey = (ResourceKey) ShipwreckPieces.MARKERS_TO_LOOT.get(markerId);

            if (resourcekey != null) {
                RandomizableContainer.setBlockEntityLootTable(level, random, position.below(), resourcekey);
            }

        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            if (this.isTooBigToFitInWorldGenRegion()) {
                super.postProcess(level, structureManager, generator, random, chunkBB, chunkPos, referencePos);
            } else {
                int i = level.getMaxY() + 1;
                int j = 0;
                Vec3i vec3i = this.template.getSize();
                Heightmap.Types heightmap_types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
                int k = vec3i.getX() * vec3i.getZ();

                if (k == 0) {
                    j = level.getHeight(heightmap_types, this.templatePosition.getX(), this.templatePosition.getZ());
                } else {
                    BlockPos blockpos1 = this.templatePosition.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);

                    for (BlockPos blockpos2 : BlockPos.betweenClosed(this.templatePosition, blockpos1)) {
                        int l = level.getHeight(heightmap_types, blockpos2.getX(), blockpos2.getZ());

                        j += l;
                        i = Math.min(i, l);
                    }

                    j /= k;
                }

                this.adjustPositionHeight(this.isBeached ? this.calculateBeachedPosition(i, random) : j);
                super.postProcess(level, structureManager, generator, random, chunkBB, chunkPos, referencePos);
            }
        }

        public boolean isTooBigToFitInWorldGenRegion() {
            Vec3i vec3i = this.template.getSize();

            return vec3i.getX() > 32 || vec3i.getY() > 32;
        }

        public int calculateBeachedPosition(int minY, RandomSource random) {
            return minY - this.template.getSize().getY() / 2 - random.nextInt(3);
        }

        public void adjustPositionHeight(int newHeight) {
            this.templatePosition = new BlockPos(this.templatePosition.getX(), newHeight, this.templatePosition.getZ());
        }
    }
}
