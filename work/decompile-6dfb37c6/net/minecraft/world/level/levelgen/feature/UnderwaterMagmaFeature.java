package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.configurations.UnderwaterMagmaConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class UnderwaterMagmaFeature extends Feature<UnderwaterMagmaConfiguration> {

    public UnderwaterMagmaFeature(Codec<UnderwaterMagmaConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<UnderwaterMagmaConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        UnderwaterMagmaConfiguration underwatermagmaconfiguration = context.config();
        RandomSource randomsource = context.random();
        OptionalInt optionalint = getFloorY(worldgenlevel, blockpos, underwatermagmaconfiguration);

        if (optionalint.isEmpty()) {
            return false;
        } else {
            BlockPos blockpos1 = blockpos.atY(optionalint.getAsInt());
            Vec3i vec3i = new Vec3i(underwatermagmaconfiguration.placementRadiusAroundFloor, underwatermagmaconfiguration.placementRadiusAroundFloor, underwatermagmaconfiguration.placementRadiusAroundFloor);
            BoundingBox boundingbox = BoundingBox.fromCorners(blockpos1.subtract(vec3i), blockpos1.offset(vec3i));

            return BlockPos.betweenClosedStream(boundingbox).filter((blockpos2) -> {
                return randomsource.nextFloat() < underwatermagmaconfiguration.placementProbabilityPerValidPosition;
            }).filter((blockpos2) -> {
                return this.isValidPlacement(worldgenlevel, blockpos2);
            }).mapToInt((blockpos2) -> {
                worldgenlevel.setBlock(blockpos2, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                return 1;
            }).sum() > 0;
        }
    }

    private static OptionalInt getFloorY(WorldGenLevel level, BlockPos origin, UnderwaterMagmaConfiguration config) {
        Predicate<BlockState> predicate = (blockstate) -> {
            return blockstate.is(Blocks.WATER);
        };
        Predicate<BlockState> predicate1 = (blockstate) -> {
            return !blockstate.is(Blocks.WATER);
        };
        Optional<Column> optional = Column.scan(level, origin, config.floorSearchRange, predicate, predicate1);

        return (OptionalInt) optional.map(Column::getFloor).orElseGet(OptionalInt::empty);
    }

    private boolean isValidPlacement(WorldGenLevel level, BlockPos pos) {
        if (!isWaterOrAir(level.getBlockState(pos)) && !this.isVisibleFromOutside(level, pos.below(), Direction.UP)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.isVisibleFromOutside(level, pos.relative(direction), direction.getOpposite())) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean isWaterOrAir(BlockState state) {
        return state.is(Blocks.WATER) || state.isAir();
    }

    private boolean isVisibleFromOutside(LevelAccessor level, BlockPos pos, Direction coveredDirection) {
        BlockState blockstate = level.getBlockState(pos);
        VoxelShape voxelshape = blockstate.getFaceOcclusionShape(coveredDirection);

        return voxelshape == Shapes.empty() || !Block.isShapeFullBlock(voxelshape);
    }
}
