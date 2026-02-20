package net.minecraft.world.level.redstone;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.jspecify.annotations.Nullable;

public class ExperimentalRedstoneWireEvaluator extends RedstoneWireEvaluator {

    private final Deque<BlockPos> wiresToTurnOff = new ArrayDeque();
    private final Deque<BlockPos> wiresToTurnOn = new ArrayDeque();
    private final Object2IntMap<BlockPos> updatedWires = new Object2IntLinkedOpenHashMap();

    public ExperimentalRedstoneWireEvaluator(RedStoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(Level level, BlockPos initialPos, BlockState ignored, @Nullable Orientation orientation, boolean shapeUpdateWiresAroundInitialPosition) {
        Orientation orientation1 = getInitialOrientation(level, orientation);

        this.calculateCurrentChanges(level, initialPos, orientation1);
        ObjectIterator<Object2IntMap.Entry<BlockPos>> objectiterator = this.updatedWires.object2IntEntrySet().iterator();

        for (boolean flag1 = true; objectiterator.hasNext(); flag1 = false) {
            Object2IntMap.Entry<BlockPos> object2intmap_entry = (Entry) objectiterator.next();
            BlockPos blockpos1 = (BlockPos) object2intmap_entry.getKey();
            int i = object2intmap_entry.getIntValue();
            int j = unpackPower(i);
            BlockState blockstate1 = level.getBlockState(blockpos1);

            if (blockstate1.is(this.wireBlock) && !((Integer) blockstate1.getValue(RedStoneWireBlock.POWER)).equals(j)) {
                int k = 2;

                if (!shapeUpdateWiresAroundInitialPosition || !flag1) {
                    k |= 128;
                }

                level.setBlock(blockpos1, (BlockState) blockstate1.setValue(RedStoneWireBlock.POWER, j), k);
            } else {
                objectiterator.remove();
            }
        }

        this.causeNeighborUpdates(level);
    }

    private void causeNeighborUpdates(Level level) {
        this.updatedWires.forEach((blockpos, i) -> {
            Orientation orientation = unpackOrientation(i);
            BlockState blockstate = level.getBlockState(blockpos);

            for (Direction direction : orientation.getDirections()) {
                if (isConnected(blockstate, direction)) {
                    BlockPos blockpos1 = blockpos.relative(direction);
                    BlockState blockstate1 = level.getBlockState(blockpos1);
                    Orientation orientation1 = orientation.withFrontPreserveUp(direction);

                    level.neighborChanged(blockstate1, blockpos1, this.wireBlock, orientation1, false);
                    if (blockstate1.isRedstoneConductor(level, blockpos1)) {
                        for (Direction direction1 : orientation1.getDirections()) {
                            if (direction1 != direction.getOpposite()) {
                                level.neighborChanged(blockpos1.relative(direction1), this.wireBlock, orientation1.withFrontPreserveUp(direction1));
                            }
                        }
                    }
                }
            }

        });
        if (level instanceof ServerLevel serverlevel) {
            if (serverlevel.debugSynchronizers().hasAnySubscriberFor(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS)) {
                this.updatedWires.forEach((blockpos, i) -> {
                    serverlevel.debugSynchronizers().sendBlockValue(blockpos, DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS, unpackOrientation(i));
                });
            }
        }

    }

    private static boolean isConnected(BlockState state, Direction direction) {
        EnumProperty<RedstoneSide> enumproperty = (EnumProperty) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction);

        return enumproperty == null ? direction == Direction.DOWN : ((RedstoneSide) state.getValue(enumproperty)).isConnected();
    }

    private static Orientation getInitialOrientation(Level level, @Nullable Orientation incomingOrigination) {
        Orientation orientation1;

        if (incomingOrigination != null) {
            orientation1 = incomingOrigination;
        } else {
            orientation1 = Orientation.random(level.random);
        }

        return orientation1.withUp(Direction.UP).withSideBias(Orientation.SideBias.LEFT);
    }

    private void calculateCurrentChanges(Level level, BlockPos initialPosition, Orientation initialOrientation) {
        BlockState blockstate = level.getBlockState(initialPosition);

        if (blockstate.is(this.wireBlock)) {
            this.setPower(initialPosition, (Integer) blockstate.getValue(RedStoneWireBlock.POWER), initialOrientation);
            this.wiresToTurnOff.add(initialPosition);
        } else {
            this.propagateChangeToNeighbors(level, initialPosition, 0, initialOrientation, true);
        }

        BlockPos blockpos1;
        Orientation orientation1;
        int i;
        int j;
        int k;

        for (; !this.wiresToTurnOff.isEmpty(); this.propagateChangeToNeighbors(level, blockpos1, k, orientation1, i > j)) {
            blockpos1 = (BlockPos) this.wiresToTurnOff.removeFirst();
            int l = this.updatedWires.getInt(blockpos1);

            orientation1 = unpackOrientation(l);
            i = unpackPower(l);
            int i1 = this.getBlockSignal(level, blockpos1);
            int j1 = this.getIncomingWireSignal(level, blockpos1);

            j = Math.max(i1, j1);
            if (j < i) {
                if (i1 > 0 && !this.wiresToTurnOn.contains(blockpos1)) {
                    this.wiresToTurnOn.add(blockpos1);
                }

                k = 0;
            } else {
                k = j;
            }

            if (k != i) {
                this.setPower(blockpos1, k, orientation1);
            }
        }

        int k1;

        for (; !this.wiresToTurnOn.isEmpty(); this.propagateChangeToNeighbors(level, blockpos1, k1, orientation2, false)) {
            blockpos1 = (BlockPos) this.wiresToTurnOn.removeFirst();
            int l1 = this.updatedWires.getInt(blockpos1);
            int i2 = unpackPower(l1);

            i = this.getBlockSignal(level, blockpos1);
            int j2 = this.getIncomingWireSignal(level, blockpos1);

            k1 = Math.max(i, j2);
            orientation2 = unpackOrientation(l1);
            if (k1 > i2) {
                this.setPower(blockpos1, k1, orientation2);
            } else if (k1 < i2) {
                throw new IllegalStateException("Turning off wire while trying to turn it on. Should not happen.");
            }
        }

    }

    private static int packOrientationAndPower(Orientation orientation, int power) {
        return orientation.getIndex() << 4 | power;
    }

    private static Orientation unpackOrientation(int packed) {
        return Orientation.fromIndex(packed >> 4);
    }

    private static int unpackPower(int packed) {
        return packed & 15;
    }

    private void setPower(BlockPos pos, int newPower, Orientation orientation) {
        this.updatedWires.compute(pos, (blockpos1, integer) -> {
            return integer == null ? packOrientationAndPower(orientation, newPower) : packOrientationAndPower(unpackOrientation(integer), newPower);
        });
    }

    private void propagateChangeToNeighbors(Level level, BlockPos pos, int newPower, Orientation orientation, boolean allowTurningOff) {
        for (Direction direction : orientation.getHorizontalDirections()) {
            BlockPos blockpos1 = pos.relative(direction);

            this.enqueueNeighborWire(level, blockpos1, newPower, orientation.withFront(direction), allowTurningOff);
        }

        for (Direction direction1 : orientation.getVerticalDirections()) {
            BlockPos blockpos2 = pos.relative(direction1);
            boolean flag1 = level.getBlockState(blockpos2).isRedstoneConductor(level, blockpos2);

            for (Direction direction2 : orientation.getHorizontalDirections()) {
                BlockPos blockpos3 = pos.relative(direction2);

                if (direction1 == Direction.UP && !flag1) {
                    BlockPos blockpos4 = blockpos2.relative(direction2);

                    this.enqueueNeighborWire(level, blockpos4, newPower, orientation.withFront(direction2), allowTurningOff);
                } else if (direction1 == Direction.DOWN && !level.getBlockState(blockpos3).isRedstoneConductor(level, blockpos3)) {
                    BlockPos blockpos5 = blockpos2.relative(direction2);

                    this.enqueueNeighborWire(level, blockpos5, newPower, orientation.withFront(direction2), allowTurningOff);
                }
            }
        }

    }

    private void enqueueNeighborWire(Level level, BlockPos pos, int newFromPower, Orientation orientation, boolean allowTurningOff) {
        BlockState blockstate = level.getBlockState(pos);

        if (blockstate.is(this.wireBlock)) {
            int j = this.getWireSignal(pos, blockstate);

            if (j < newFromPower - 1 && !this.wiresToTurnOn.contains(pos)) {
                this.wiresToTurnOn.add(pos);
                this.setPower(pos, j, orientation);
            }

            if (allowTurningOff && j > newFromPower && !this.wiresToTurnOff.contains(pos)) {
                this.wiresToTurnOff.add(pos);
                this.setPower(pos, j, orientation);
            }
        }

    }

    @Override
    protected int getWireSignal(BlockPos pos, BlockState state) {
        int i = this.updatedWires.getOrDefault(pos, -1);

        return i != -1 ? unpackPower(i) : super.getWireSignal(pos, state);
    }
}
