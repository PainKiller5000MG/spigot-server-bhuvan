package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;

public class Beardifier implements DensityFunctions.BeardifierOrMarker {

    public static final int BEARD_KERNEL_RADIUS = 12;
    private static final int BEARD_KERNEL_SIZE = 24;
    private static final float[] BEARD_KERNEL = (float[]) Util.make(new float[13824], (afloat) -> {
        for (int i = 0; i < 24; ++i) {
            for (int j = 0; j < 24; ++j) {
                for (int k = 0; k < 24; ++k) {
                    afloat[i * 24 * 24 + j * 24 + k] = (float) computeBeardContribution(j - 12, k - 12, i - 12);
                }
            }
        }

    });
    public static final Beardifier EMPTY = new Beardifier(List.of(), List.of(), (BoundingBox) null);
    private final List<Beardifier.Rigid> pieces;
    private final List<JigsawJunction> junctions;
    private final @Nullable BoundingBox affectedBox;

    public static Beardifier forStructuresInChunk(StructureManager structureManager, ChunkPos chunkPos) {
        List<StructureStart> list = structureManager.startsForStructure(chunkPos, (structure) -> {
            return structure.terrainAdaptation() != TerrainAdjustment.NONE;
        });

        if (list.isEmpty()) {
            return Beardifier.EMPTY;
        } else {
            int i = chunkPos.getMinBlockX();
            int j = chunkPos.getMinBlockZ();
            List<Beardifier.Rigid> list1 = new ArrayList();
            List<JigsawJunction> list2 = new ArrayList();
            BoundingBox boundingbox = null;

            for (StructureStart structurestart : list) {
                TerrainAdjustment terrainadjustment = structurestart.getStructure().terrainAdaptation();

                for (StructurePiece structurepiece : structurestart.getPieces()) {
                    if (structurepiece.isCloseToChunk(chunkPos, 12)) {
                        if (structurepiece instanceof PoolElementStructurePiece) {
                            PoolElementStructurePiece poolelementstructurepiece = (PoolElementStructurePiece) structurepiece;
                            StructureTemplatePool.Projection structuretemplatepool_projection = poolelementstructurepiece.getElement().getProjection();

                            if (structuretemplatepool_projection == StructureTemplatePool.Projection.RIGID) {
                                list1.add(new Beardifier.Rigid(poolelementstructurepiece.getBoundingBox(), terrainadjustment, poolelementstructurepiece.getGroundLevelDelta()));
                                boundingbox = includeBoundingBox(boundingbox, structurepiece.getBoundingBox());
                            }

                            for (JigsawJunction jigsawjunction : poolelementstructurepiece.getJunctions()) {
                                int k = jigsawjunction.getSourceX();
                                int l = jigsawjunction.getSourceZ();

                                if (k > i - 12 && l > j - 12 && k < i + 15 + 12 && l < j + 15 + 12) {
                                    list2.add(jigsawjunction);
                                    BoundingBox boundingbox1 = new BoundingBox(new BlockPos(k, jigsawjunction.getSourceGroundY(), l));

                                    boundingbox = includeBoundingBox(boundingbox, boundingbox1);
                                }
                            }
                        } else {
                            list1.add(new Beardifier.Rigid(structurepiece.getBoundingBox(), terrainadjustment, 0));
                            boundingbox = includeBoundingBox(boundingbox, structurepiece.getBoundingBox());
                        }
                    }
                }
            }

            if (boundingbox == null) {
                return Beardifier.EMPTY;
            } else {
                BoundingBox boundingbox2 = boundingbox.inflatedBy(24);

                return new Beardifier(List.copyOf(list1), List.copyOf(list2), boundingbox2);
            }
        }
    }

    private static BoundingBox includeBoundingBox(@Nullable BoundingBox encompassingBox, BoundingBox newBox) {
        return encompassingBox == null ? newBox : BoundingBox.encapsulating(encompassingBox, newBox);
    }

    @VisibleForTesting
    public Beardifier(List<Beardifier.Rigid> pieces, List<JigsawJunction> junctions, @Nullable BoundingBox affectedBox) {
        this.pieces = pieces;
        this.junctions = junctions;
        this.affectedBox = affectedBox;
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        if (this.affectedBox == null) {
            Arrays.fill(output, 0.0D);
        } else {
            DensityFunctions.BeardifierOrMarker.super.fillArray(output, contextProvider);
        }

    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        if (this.affectedBox == null) {
            return 0.0D;
        } else {
            int i = context.blockX();
            int j = context.blockY();
            int k = context.blockZ();

            if (!this.affectedBox.isInside(i, j, k)) {
                return 0.0D;
            } else {
                double d0 = 0.0D;

                for (Beardifier.Rigid beardifier_rigid : this.pieces) {
                    BoundingBox boundingbox = beardifier_rigid.box();
                    int l = beardifier_rigid.groundLevelDelta();
                    int i1 = Math.max(0, Math.max(boundingbox.minX() - i, i - boundingbox.maxX()));
                    int j1 = Math.max(0, Math.max(boundingbox.minZ() - k, k - boundingbox.maxZ()));
                    int k1 = boundingbox.minY() + l;
                    int l1 = j - k1;
                    int i2;

                    switch (beardifier_rigid.terrainAdjustment()) {
                        case NONE:
                            i2 = 0;
                            break;
                        case BURY:
                        case BEARD_THIN:
                            i2 = l1;
                            break;
                        case BEARD_BOX:
                            i2 = Math.max(0, Math.max(k1 - j, j - boundingbox.maxY()));
                            break;
                        case ENCAPSULATE:
                            i2 = Math.max(0, Math.max(boundingbox.minY() - j, j - boundingbox.maxY()));
                            break;
                        default:
                            throw new MatchException((String) null, (Throwable) null);
                    }

                    int j2 = i2;
                    double d1;

                    switch (beardifier_rigid.terrainAdjustment()) {
                        case NONE:
                            d1 = 0.0D;
                            break;
                        case BURY:
                            d1 = getBuryContribution((double) i1, (double) j2 / 2.0D, (double) j1);
                            break;
                        case BEARD_THIN:
                        case BEARD_BOX:
                            d1 = getBeardContribution(i1, j2, j1, l1) * 0.8D;
                            break;
                        case ENCAPSULATE:
                            d1 = getBuryContribution((double) i1 / 2.0D, (double) j2 / 2.0D, (double) j1 / 2.0D) * 0.8D;
                            break;
                        default:
                            throw new MatchException((String) null, (Throwable) null);
                    }

                    d0 += d1;
                }

                for (JigsawJunction jigsawjunction : this.junctions) {
                    int k2 = i - jigsawjunction.getSourceX();
                    int l2 = j - jigsawjunction.getSourceGroundY();
                    int i3 = k - jigsawjunction.getSourceZ();

                    d0 += getBeardContribution(k2, l2, i3, l2) * 0.4D;
                }

                return d0;
            }
        }
    }

    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    private static double getBuryContribution(double dx, double dy, double dz) {
        double d3 = Mth.length(dx, dy, dz);

        return Mth.clampedMap(d3, 0.0D, 6.0D, 1.0D, 0.0D);
    }

    private static double getBeardContribution(int dx, int dy, int dz, int yToGround) {
        int i1 = dx + 12;
        int j1 = dy + 12;
        int k1 = dz + 12;

        if (isInKernelRange(i1) && isInKernelRange(j1) && isInKernelRange(k1)) {
            double d0 = (double) yToGround + 0.5D;
            double d1 = Mth.lengthSquared((double) dx, d0, (double) dz);
            double d2 = -d0 * Mth.fastInvSqrt(d1 / 2.0D) / 2.0D;

            return d2 * (double) Beardifier.BEARD_KERNEL[k1 * 24 * 24 + i1 * 24 + j1];
        } else {
            return 0.0D;
        }
    }

    private static boolean isInKernelRange(int xi) {
        return xi >= 0 && xi < 24;
    }

    private static double computeBeardContribution(int dx, int dy, int dz) {
        return computeBeardContribution(dx, (double) dy + 0.5D, dz);
    }

    private static double computeBeardContribution(int dx, double dy, int dz) {
        double d1 = Mth.lengthSquared((double) dx, dy, (double) dz);
        double d2 = Math.pow(Math.E, -d1 / 16.0D);

        return d2;
    }

    @VisibleForTesting
    public static record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {

    }
}
