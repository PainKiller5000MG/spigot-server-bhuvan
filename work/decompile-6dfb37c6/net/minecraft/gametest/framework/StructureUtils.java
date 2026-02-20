package net.minecraft.gametest.framework;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class StructureUtils {

    public static final int DEFAULT_Y_SEARCH_RADIUS = 10;
    public static final String DEFAULT_TEST_STRUCTURES_DIR = "Minecraft.Server/src/test/convertables/data";
    public static Path testStructuresDir = Paths.get("Minecraft.Server/src/test/convertables/data");

    public StructureUtils() {}

    public static Rotation getRotationForRotationSteps(int rotationSteps) {
        switch (rotationSteps) {
            case 0:
                return Rotation.NONE;
            case 1:
                return Rotation.CLOCKWISE_90;
            case 2:
                return Rotation.CLOCKWISE_180;
            case 3:
                return Rotation.COUNTERCLOCKWISE_90;
            default:
                throw new IllegalArgumentException("rotationSteps must be a value from 0-3. Got value " + rotationSteps);
        }
    }

    public static int getRotationStepsForRotation(Rotation rotation) {
        switch (rotation) {
            case NONE:
                return 0;
            case CLOCKWISE_90:
                return 1;
            case CLOCKWISE_180:
                return 2;
            case COUNTERCLOCKWISE_90:
                return 3;
            default:
                throw new IllegalArgumentException("Unknown rotation value, don't know how many steps it represents: " + String.valueOf(rotation));
        }
    }

    public static TestInstanceBlockEntity createNewEmptyTest(Identifier id, BlockPos structurePos, Vec3i size, Rotation rotation, ServerLevel level) {
        BoundingBox boundingbox = getStructureBoundingBox(TestInstanceBlockEntity.getStructurePos(structurePos), size, rotation);

        clearSpaceForStructure(boundingbox, level);
        level.setBlockAndUpdate(structurePos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
        TestInstanceBlockEntity testinstanceblockentity = (TestInstanceBlockEntity) level.getBlockEntity(structurePos);
        ResourceKey<GameTestInstance> resourcekey = ResourceKey.create(Registries.TEST_INSTANCE, id);

        testinstanceblockentity.set(new TestInstanceBlockEntity.Data(Optional.of(resourcekey), size, rotation, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
        return testinstanceblockentity;
    }

    public static void clearSpaceForStructure(BoundingBox structureBoundingBox, ServerLevel level) {
        int i = structureBoundingBox.minY() - 1;

        BlockPos.betweenClosedStream(structureBoundingBox).forEach((blockpos) -> {
            clearBlock(i, blockpos, level);
        });
        level.getBlockTicks().clearArea(structureBoundingBox);
        level.clearBlockEvents(structureBoundingBox);
        AABB aabb = AABB.of(structureBoundingBox);
        List<Entity> list = level.<Entity>getEntitiesOfClass(Entity.class, aabb, (entity) -> {
            return !(entity instanceof Player);
        });

        list.forEach(Entity::discard);
    }

    public static BlockPos getTransformedFarCorner(BlockPos structurePosition, Vec3i size, Rotation rotation) {
        BlockPos blockpos1 = structurePosition.offset(size).offset(-1, -1, -1);

        return StructureTemplate.transform(blockpos1, Mirror.NONE, rotation, structurePosition);
    }

    public static BoundingBox getStructureBoundingBox(BlockPos northWestCorner, Vec3i size, Rotation rotation) {
        BlockPos blockpos1 = getTransformedFarCorner(northWestCorner, size, rotation);
        BoundingBox boundingbox = BoundingBox.fromCorners(northWestCorner, blockpos1);
        int i = Math.min(boundingbox.minX(), boundingbox.maxX());
        int j = Math.min(boundingbox.minZ(), boundingbox.maxZ());

        return boundingbox.move(northWestCorner.getX() - i, 0, northWestCorner.getZ() - j);
    }

    public static Optional<BlockPos> findTestContainingPos(BlockPos pos, int searchRadius, ServerLevel level) {
        return findTestBlocks(pos, searchRadius, level).filter((blockpos1) -> {
            return doesStructureContain(blockpos1, pos, level);
        }).findFirst();
    }

    public static Optional<BlockPos> findNearestTest(BlockPos relativeToPos, int searchRadius, ServerLevel level) {
        Comparator<BlockPos> comparator = Comparator.comparingInt((blockpos1) -> {
            return blockpos1.distManhattan(relativeToPos);
        });

        return findTestBlocks(relativeToPos, searchRadius, level).min(comparator);
    }

    public static Stream<BlockPos> findTestBlocks(BlockPos centerPos, int searchRadius, ServerLevel level) {
        return level.getPoiManager().findAll((holder) -> {
            return holder.is(PoiTypes.TEST_INSTANCE);
        }, (blockpos1) -> {
            return true;
        }, centerPos, searchRadius, PoiManager.Occupancy.ANY).map(BlockPos::immutable);
    }

    public static Stream<BlockPos> lookedAtTestPos(BlockPos pos, Entity camera, ServerLevel level) {
        int i = 250;
        Vec3 vec3 = camera.getEyePosition();
        Vec3 vec31 = vec3.add(camera.getLookAngle().scale(250.0D));
        Stream stream = findTestBlocks(pos, 250, level).map((blockpos1) -> {
            return level.getBlockEntity(blockpos1, BlockEntityType.TEST_INSTANCE_BLOCK);
        }).flatMap(Optional::stream).filter((testinstanceblockentity) -> {
            return testinstanceblockentity.getStructureBounds().clip(vec3, vec31).isPresent();
        }).map(BlockEntity::getBlockPos);

        Objects.requireNonNull(pos);
        return stream.sorted(Comparator.comparing(pos::distSqr)).limit(1L);
    }

    private static void clearBlock(int airIfAboveThisY, BlockPos pos, ServerLevel level) {
        BlockState blockstate;

        if (pos.getY() < airIfAboveThisY) {
            blockstate = Blocks.STONE.defaultBlockState();
        } else {
            blockstate = Blocks.AIR.defaultBlockState();
        }

        BlockInput blockinput = new BlockInput(blockstate, Collections.emptySet(), (CompoundTag) null);

        blockinput.place(level, pos, 818);
        level.updateNeighborsAt(pos, blockstate.getBlock());
    }

    private static boolean doesStructureContain(BlockPos testInstanceBlockPos, BlockPos pos, ServerLevel level) {
        BlockEntity blockentity = level.getBlockEntity(testInstanceBlockPos);

        if (blockentity instanceof TestInstanceBlockEntity testinstanceblockentity) {
            return testinstanceblockentity.getStructureBoundingBox().isInside(pos);
        } else {
            return false;
        }
    }
}
