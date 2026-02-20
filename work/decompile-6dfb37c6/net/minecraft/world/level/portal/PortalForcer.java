package net.minecraft.world.level.portal;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

public class PortalForcer {

    public static final int TICKET_RADIUS = 3;
    private static final int NETHER_PORTAL_RADIUS = 16;
    private static final int OVERWORLD_PORTAL_RADIUS = 128;
    private static final int FRAME_HEIGHT = 5;
    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_BOX = 3;
    private static final int FRAME_HEIGHT_START = -1;
    private static final int FRAME_HEIGHT_END = 4;
    private static final int FRAME_WIDTH_START = -1;
    private static final int FRAME_WIDTH_END = 3;
    private static final int FRAME_BOX_START = -1;
    private static final int FRAME_BOX_END = 2;
    private static final int NOTHING_FOUND = -1;
    private final ServerLevel level;

    public PortalForcer(ServerLevel level) {
        this.level = level;
    }

    public Optional<BlockPos> findClosestPortalPosition(BlockPos approximateExitPos, boolean toNether, WorldBorder worldBorder) {
        PoiManager poimanager = this.level.getPoiManager();
        int i = toNether ? 16 : 128;

        poimanager.ensureLoadedAndValid(this.level, approximateExitPos, i);
        Stream stream = poimanager.getInSquare((holder) -> {
            return holder.is(PoiTypes.NETHER_PORTAL);
        }, approximateExitPos, i, PoiManager.Occupancy.ANY).map(PoiRecord::getPos);

        Objects.requireNonNull(worldBorder);
        return stream.filter(worldBorder::isWithinBounds).filter((blockpos1) -> {
            return this.level.getBlockState(blockpos1).hasProperty(BlockStateProperties.HORIZONTAL_AXIS);
        }).min(Comparator.comparingDouble((blockpos1) -> {
            return blockpos1.distSqr(approximateExitPos);
        }).thenComparingInt(Vec3i::getY));
    }

    public Optional<BlockUtil.FoundRectangle> createPortal(BlockPos origin, Direction.Axis portalAxis) {
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, portalAxis);
        double d0 = -1.0D;
        BlockPos blockpos1 = null;
        double d1 = -1.0D;
        BlockPos blockpos2 = null;
        WorldBorder worldborder = this.level.getWorldBorder();
        int i = Math.min(this.level.getMaxY(), this.level.getMinY() + this.level.getLogicalHeight() - 1);
        int j = 1;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();

        for (BlockPos.MutableBlockPos blockpos_mutableblockpos1 : BlockPos.spiralAround(origin, 16, Direction.EAST, Direction.SOUTH)) {
            int k = Math.min(i, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockpos_mutableblockpos1.getX(), blockpos_mutableblockpos1.getZ()));

            if (worldborder.isWithinBounds((BlockPos) blockpos_mutableblockpos1) && worldborder.isWithinBounds((BlockPos) blockpos_mutableblockpos1.move(direction, 1))) {
                blockpos_mutableblockpos1.move(direction.getOpposite(), 1);

                for (int l = k; l >= this.level.getMinY(); --l) {
                    blockpos_mutableblockpos1.setY(l);
                    if (this.canPortalReplaceBlock(blockpos_mutableblockpos1)) {
                        int i1;

                        for (i1 = l; l > this.level.getMinY() && this.canPortalReplaceBlock(blockpos_mutableblockpos1.move(Direction.DOWN)); --l) {
                            ;
                        }

                        if (l + 4 <= i) {
                            int j1 = i1 - l;

                            if (j1 <= 0 || j1 >= 3) {
                                blockpos_mutableblockpos1.setY(l);
                                if (this.canHostFrame(blockpos_mutableblockpos1, blockpos_mutableblockpos, direction, 0)) {
                                    double d2 = origin.distSqr(blockpos_mutableblockpos1);

                                    if (this.canHostFrame(blockpos_mutableblockpos1, blockpos_mutableblockpos, direction, -1) && this.canHostFrame(blockpos_mutableblockpos1, blockpos_mutableblockpos, direction, 1) && (d0 == -1.0D || d0 > d2)) {
                                        d0 = d2;
                                        blockpos1 = blockpos_mutableblockpos1.immutable();
                                    }

                                    if (d0 == -1.0D && (d1 == -1.0D || d1 > d2)) {
                                        d1 = d2;
                                        blockpos2 = blockpos_mutableblockpos1.immutable();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (d0 == -1.0D && d1 != -1.0D) {
            blockpos1 = blockpos2;
            d0 = d1;
        }

        if (d0 == -1.0D) {
            int k1 = Math.max(this.level.getMinY() - -1, 70);
            int l1 = i - 9;

            if (l1 < k1) {
                return Optional.empty();
            }

            blockpos1 = (new BlockPos(origin.getX() - direction.getStepX() * 1, Mth.clamp(origin.getY(), k1, l1), origin.getZ() - direction.getStepZ() * 1)).immutable();
            blockpos1 = worldborder.clampToBounds(blockpos1);
            Direction direction1 = direction.getClockWise();

            for (int i2 = -1; i2 < 2; ++i2) {
                for (int j2 = 0; j2 < 2; ++j2) {
                    for (int k2 = -1; k2 < 3; ++k2) {
                        BlockState blockstate = k2 < 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();

                        blockpos_mutableblockpos.setWithOffset(blockpos1, j2 * direction.getStepX() + i2 * direction1.getStepX(), k2, j2 * direction.getStepZ() + i2 * direction1.getStepZ());
                        this.level.setBlockAndUpdate(blockpos_mutableblockpos, blockstate);
                    }
                }
            }
        }

        for (int l2 = -1; l2 < 3; ++l2) {
            for (int i3 = -1; i3 < 4; ++i3) {
                if (l2 == -1 || l2 == 2 || i3 == -1 || i3 == 3) {
                    blockpos_mutableblockpos.setWithOffset(blockpos1, l2 * direction.getStepX(), i3, l2 * direction.getStepZ());
                    this.level.setBlock(blockpos_mutableblockpos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }

        BlockState blockstate1 = (BlockState) Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, portalAxis);

        for (int j3 = 0; j3 < 2; ++j3) {
            for (int k3 = 0; k3 < 3; ++k3) {
                blockpos_mutableblockpos.setWithOffset(blockpos1, j3 * direction.getStepX(), k3, j3 * direction.getStepZ());
                this.level.setBlock(blockpos_mutableblockpos, blockstate1, 18);
            }
        }

        return Optional.of(new BlockUtil.FoundRectangle(blockpos1.immutable(), 2, 3));
    }

    private boolean canPortalReplaceBlock(BlockPos.MutableBlockPos pos) {
        BlockState blockstate = this.level.getBlockState(pos);

        return blockstate.canBeReplaced() && blockstate.getFluidState().isEmpty();
    }

    private boolean canHostFrame(BlockPos origin, BlockPos.MutableBlockPos mutable, Direction direction, int offset) {
        Direction direction1 = direction.getClockWise();

        for (int j = -1; j < 3; ++j) {
            for (int k = -1; k < 4; ++k) {
                mutable.setWithOffset(origin, direction.getStepX() * j + direction1.getStepX() * offset, k, direction.getStepZ() * j + direction1.getStepZ() * offset);
                if (k < 0 && !this.level.getBlockState(mutable).isSolid()) {
                    return false;
                }

                if (k >= 0 && !this.canPortalReplaceBlock(mutable)) {
                    return false;
                }
            }
        }

        return true;
    }
}
