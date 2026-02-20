package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.LargeDripstoneConfiguration;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LargeDripstoneFeature extends Feature<LargeDripstoneConfiguration> {

    public LargeDripstoneFeature(Codec<LargeDripstoneConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<LargeDripstoneConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        LargeDripstoneConfiguration largedripstoneconfiguration = context.config();
        RandomSource randomsource = context.random();

        if (!DripstoneUtils.isEmptyOrWater(worldgenlevel, blockpos)) {
            return false;
        } else {
            Optional<Column> optional = Column.scan(worldgenlevel, blockpos, largedripstoneconfiguration.floorToCeilingSearchRange, DripstoneUtils::isEmptyOrWater, DripstoneUtils::isDripstoneBaseOrLava);

            if (!optional.isEmpty() && optional.get() instanceof Column.Range) {
                Column.Range column_range = (Column.Range) optional.get();

                if (column_range.height() < 4) {
                    return false;
                } else {
                    int i = (int) ((float) column_range.height() * largedripstoneconfiguration.maxColumnRadiusToCaveHeightRatio);
                    int j = Mth.clamp(i, largedripstoneconfiguration.columnRadius.getMinValue(), largedripstoneconfiguration.columnRadius.getMaxValue());
                    int k = Mth.randomBetweenInclusive(randomsource, largedripstoneconfiguration.columnRadius.getMinValue(), j);
                    LargeDripstoneFeature.LargeDripstone largedripstonefeature_largedripstone = makeDripstone(blockpos.atY(column_range.ceiling() - 1), false, randomsource, k, largedripstoneconfiguration.stalactiteBluntness, largedripstoneconfiguration.heightScale);
                    LargeDripstoneFeature.LargeDripstone largedripstonefeature_largedripstone1 = makeDripstone(blockpos.atY(column_range.floor() + 1), true, randomsource, k, largedripstoneconfiguration.stalagmiteBluntness, largedripstoneconfiguration.heightScale);
                    LargeDripstoneFeature.WindOffsetter largedripstonefeature_windoffsetter;

                    if (largedripstonefeature_largedripstone.isSuitableForWind(largedripstoneconfiguration) && largedripstonefeature_largedripstone1.isSuitableForWind(largedripstoneconfiguration)) {
                        largedripstonefeature_windoffsetter = new LargeDripstoneFeature.WindOffsetter(blockpos.getY(), randomsource, largedripstoneconfiguration.windSpeed);
                    } else {
                        largedripstonefeature_windoffsetter = LargeDripstoneFeature.WindOffsetter.noWind();
                    }

                    boolean flag = largedripstonefeature_largedripstone.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(worldgenlevel, largedripstonefeature_windoffsetter);
                    boolean flag1 = largedripstonefeature_largedripstone1.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(worldgenlevel, largedripstonefeature_windoffsetter);

                    if (flag) {
                        largedripstonefeature_largedripstone.placeBlocks(worldgenlevel, randomsource, largedripstonefeature_windoffsetter);
                    }

                    if (flag1) {
                        largedripstonefeature_largedripstone1.placeBlocks(worldgenlevel, randomsource, largedripstonefeature_windoffsetter);
                    }

                    if (SharedConstants.DEBUG_LARGE_DRIPSTONE) {
                        this.placeDebugMarkers(worldgenlevel, blockpos, column_range, largedripstonefeature_windoffsetter);
                    }

                    return true;
                }
            } else {
                return false;
            }
        }
    }

    private static LargeDripstoneFeature.LargeDripstone makeDripstone(BlockPos root, boolean pointingUp, RandomSource random, int radius, FloatProvider bluntness, FloatProvider heightScale) {
        return new LargeDripstoneFeature.LargeDripstone(root, pointingUp, radius, (double) bluntness.sample(random), (double) heightScale.sample(random));
    }

    private void placeDebugMarkers(WorldGenLevel level, BlockPos origin, Column.Range range, LargeDripstoneFeature.WindOffsetter wind) {
        level.setBlock(wind.offset(origin.atY(range.ceiling() - 1)), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        level.setBlock(wind.offset(origin.atY(range.floor() + 1)), Blocks.GOLD_BLOCK.defaultBlockState(), 2);

        for (BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.atY(range.floor() + 2).mutable(); blockpos_mutableblockpos.getY() < range.ceiling() - 1; blockpos_mutableblockpos.move(Direction.UP)) {
            BlockPos blockpos1 = wind.offset(blockpos_mutableblockpos);

            if (DripstoneUtils.isEmptyOrWater(level, blockpos1) || level.getBlockState(blockpos1).is(Blocks.DRIPSTONE_BLOCK)) {
                level.setBlock(blockpos1, Blocks.CREEPER_HEAD.defaultBlockState(), 2);
            }
        }

    }

    private static final class LargeDripstone {

        private BlockPos root;
        private final boolean pointingUp;
        private int radius;
        private final double bluntness;
        private final double scale;

        private LargeDripstone(BlockPos root, boolean pointingUp, int radius, double bluntness, double scale) {
            this.root = root;
            this.pointingUp = pointingUp;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        private int getHeight() {
            return this.getHeightAtRadius(0.0F);
        }

        private int getMinY() {
            return this.pointingUp ? this.root.getY() : this.root.getY() - this.getHeight();
        }

        private int getMaxY() {
            return !this.pointingUp ? this.root.getY() : this.root.getY() + this.getHeight();
        }

        private boolean moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(WorldGenLevel level, LargeDripstoneFeature.WindOffsetter wind) {
            while (this.radius > 1) {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = this.root.mutable();
                int i = Math.min(10, this.getHeight());

                for (int j = 0; j < i; ++j) {
                    if (level.getBlockState(blockpos_mutableblockpos).is(Blocks.LAVA)) {
                        return false;
                    }

                    if (DripstoneUtils.isCircleMostlyEmbeddedInStone(level, wind.offset(blockpos_mutableblockpos), this.radius)) {
                        this.root = blockpos_mutableblockpos;
                        return true;
                    }

                    blockpos_mutableblockpos.move(this.pointingUp ? Direction.DOWN : Direction.UP);
                }

                this.radius /= 2;
            }

            return false;
        }

        private int getHeightAtRadius(float checkRadius) {
            return (int) DripstoneUtils.getDripstoneHeight((double) checkRadius, (double) this.radius, this.scale, this.bluntness);
        }

        private void placeBlocks(WorldGenLevel level, RandomSource random, LargeDripstoneFeature.WindOffsetter wind) {
            for (int i = -this.radius; i <= this.radius; ++i) {
                for (int j = -this.radius; j <= this.radius; ++j) {
                    float f = Mth.sqrt((float) (i * i + j * j));

                    if (f <= (float) this.radius) {
                        int k = this.getHeightAtRadius(f);

                        if (k > 0) {
                            if ((double) random.nextFloat() < 0.2D) {
                                k = (int) ((float) k * Mth.randomBetween(random, 0.8F, 1.0F));
                            }

                            BlockPos.MutableBlockPos blockpos_mutableblockpos = this.root.offset(i, 0, j).mutable();
                            boolean flag = false;
                            int l = this.pointingUp ? level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockpos_mutableblockpos.getX(), blockpos_mutableblockpos.getZ()) : Integer.MAX_VALUE;

                            for (int i1 = 0; i1 < k && blockpos_mutableblockpos.getY() < l; ++i1) {
                                BlockPos blockpos = wind.offset(blockpos_mutableblockpos);

                                if (DripstoneUtils.isEmptyOrWaterOrLava(level, blockpos)) {
                                    flag = true;
                                    Block block = SharedConstants.DEBUG_LARGE_DRIPSTONE ? Blocks.GLASS : Blocks.DRIPSTONE_BLOCK;

                                    level.setBlock(blockpos, block.defaultBlockState(), 2);
                                } else if (flag && level.getBlockState(blockpos).is(BlockTags.BASE_STONE_OVERWORLD)) {
                                    break;
                                }

                                blockpos_mutableblockpos.move(this.pointingUp ? Direction.UP : Direction.DOWN);
                            }
                        }
                    }
                }
            }

        }

        private boolean isSuitableForWind(LargeDripstoneConfiguration config) {
            return this.radius >= config.minRadiusForWind && this.bluntness >= (double) config.minBluntnessForWind;
        }
    }

    private static final class WindOffsetter {

        private final int originY;
        private final @Nullable Vec3 windSpeed;

        private WindOffsetter(int originY, RandomSource random, FloatProvider windSpeedRange) {
            this.originY = originY;
            float f = windSpeedRange.sample(random);
            float f1 = Mth.randomBetween(random, 0.0F, (float) Math.PI);

            this.windSpeed = new Vec3((double) (Mth.cos((double) f1) * f), 0.0D, (double) (Mth.sin((double) f1) * f));
        }

        private WindOffsetter() {
            this.originY = 0;
            this.windSpeed = null;
        }

        private static LargeDripstoneFeature.WindOffsetter noWind() {
            return new LargeDripstoneFeature.WindOffsetter();
        }

        private BlockPos offset(BlockPos pos) {
            if (this.windSpeed == null) {
                return pos;
            } else {
                int i = this.originY - pos.getY();
                Vec3 vec3 = this.windSpeed.scale((double) i);

                return pos.offset(Mth.floor(vec3.x), 0, Mth.floor(vec3.z));
            }
        }
    }
}
