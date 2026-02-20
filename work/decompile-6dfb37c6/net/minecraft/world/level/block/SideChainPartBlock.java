package net.minecraft.world.level.block;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SideChainPart;

public interface SideChainPartBlock {

    SideChainPart getSideChainPart(BlockState state);

    BlockState setSideChainPart(BlockState state, SideChainPart newPart);

    Direction getFacing(BlockState state);

    boolean isConnectable(BlockState state);

    int getMaxChainLength();

    default List<BlockPos> getAllBlocksConnectedTo(LevelAccessor level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        if (!this.isConnectable(blockstate)) {
            return List.of();
        } else {
            SideChainPartBlock.Neighbors sidechainpartblock_neighbors = this.getNeighbors(level, pos, this.getFacing(blockstate));
            List<BlockPos> list = new LinkedList();

            list.add(pos);
            Objects.requireNonNull(sidechainpartblock_neighbors);
            IntFunction intfunction = sidechainpartblock_neighbors::left;
            SideChainPart sidechainpart = SideChainPart.LEFT;

            Objects.requireNonNull(list);
            this.addBlocksConnectingTowards(intfunction, sidechainpart, list::addFirst);
            Objects.requireNonNull(sidechainpartblock_neighbors);
            intfunction = sidechainpartblock_neighbors::right;
            sidechainpart = SideChainPart.RIGHT;
            Objects.requireNonNull(list);
            this.addBlocksConnectingTowards(intfunction, sidechainpart, list::addLast);
            return list;
        }
    }

    private void addBlocksConnectingTowards(IntFunction<SideChainPartBlock.Neighbor> getNeighbor, SideChainPart endPart, Consumer<BlockPos> accumulator) {
        for (int i = 1; i < this.getMaxChainLength(); ++i) {
            SideChainPartBlock.Neighbor sidechainpartblock_neighbor = (SideChainPartBlock.Neighbor) getNeighbor.apply(i);

            if (sidechainpartblock_neighbor.connectsTowards(endPart)) {
                accumulator.accept(sidechainpartblock_neighbor.pos());
            }

            if (sidechainpartblock_neighbor.isUnconnectableOrChainEnd()) {
                break;
            }
        }

    }

    default void updateNeighborsAfterPoweringDown(LevelAccessor level, BlockPos pos, BlockState state) {
        SideChainPartBlock.Neighbors sidechainpartblock_neighbors = this.getNeighbors(level, pos, this.getFacing(state));

        sidechainpartblock_neighbors.left().disconnectFromRight();
        sidechainpartblock_neighbors.right().disconnectFromLeft();
    }

    default void updateSelfAndNeighborsOnPoweringUp(LevelAccessor level, BlockPos pos, BlockState state, BlockState oldState) {
        if (this.isConnectable(state)) {
            if (!this.isBeingUpdatedByNeighbor(state, oldState)) {
                SideChainPartBlock.Neighbors sidechainpartblock_neighbors = this.getNeighbors(level, pos, this.getFacing(state));
                SideChainPart sidechainpart = SideChainPart.UNCONNECTED;
                int i = sidechainpartblock_neighbors.left().isConnectable() ? this.getAllBlocksConnectedTo(level, sidechainpartblock_neighbors.left().pos()).size() : 0;
                int j = sidechainpartblock_neighbors.right().isConnectable() ? this.getAllBlocksConnectedTo(level, sidechainpartblock_neighbors.right().pos()).size() : 0;
                int k = 1;

                if (this.canConnect(i, k)) {
                    sidechainpart = sidechainpart.whenConnectedToTheLeft();
                    sidechainpartblock_neighbors.left().connectToTheRight();
                    k += i;
                }

                if (this.canConnect(j, k)) {
                    sidechainpart = sidechainpart.whenConnectedToTheRight();
                    sidechainpartblock_neighbors.right().connectToTheLeft();
                }

                this.setPart(level, pos, sidechainpart);
            }
        }
    }

    private boolean canConnect(int newBlocksToConnectTo, int currentChainLength) {
        return newBlocksToConnectTo > 0 && currentChainLength + newBlocksToConnectTo <= this.getMaxChainLength();
    }

    private boolean isBeingUpdatedByNeighbor(BlockState state, BlockState oldState) {
        boolean flag = this.getSideChainPart(state).isConnected();
        boolean flag1 = this.isConnectable(oldState) && this.getSideChainPart(oldState).isConnected();

        return flag || flag1;
    }

    private SideChainPartBlock.Neighbors getNeighbors(LevelAccessor level, BlockPos center, Direction facing) {
        return new SideChainPartBlock.Neighbors(this, level, facing, center, new HashMap());
    }

    private void setPart(LevelAccessor level, BlockPos pos, SideChainPart newPart) {
        BlockState blockstate = level.getBlockState(pos);

        if (this.getSideChainPart(blockstate) != newPart) {
            level.setBlock(pos, this.setSideChainPart(blockstate, newPart), 3);
        }

    }

    public static record Neighbors(SideChainPartBlock block, LevelAccessor level, Direction facing, BlockPos center, Map<BlockPos, SideChainPartBlock.Neighbor> cache) {

        private boolean isConnectableToThisBlock(BlockState neighbor) {
            return this.block.isConnectable(neighbor) && this.block.getFacing(neighbor) == this.facing;
        }

        private SideChainPartBlock.Neighbor createNewNeighbor(BlockPos pos) {
            BlockState blockstate = this.level.getBlockState(pos);
            SideChainPart sidechainpart = this.isConnectableToThisBlock(blockstate) ? this.block.getSideChainPart(blockstate) : null;

            return (SideChainPartBlock.Neighbor) (sidechainpart == null ? new SideChainPartBlock.EmptyNeighbor(pos) : new SideChainPartBlock.SideChainNeighbor(this.level, this.block, pos, sidechainpart));
        }

        private SideChainPartBlock.Neighbor getOrCreateNeighbor(Direction dir, Integer steps) {
            return (SideChainPartBlock.Neighbor) this.cache.computeIfAbsent(this.center.relative(dir, steps), this::createNewNeighbor);
        }

        public SideChainPartBlock.Neighbor left(int steps) {
            return this.getOrCreateNeighbor(this.facing.getClockWise(), steps);
        }

        public SideChainPartBlock.Neighbor right(int steps) {
            return this.getOrCreateNeighbor(this.facing.getCounterClockWise(), steps);
        }

        public SideChainPartBlock.Neighbor left() {
            return this.left(1);
        }

        public SideChainPartBlock.Neighbor right() {
            return this.right(1);
        }
    }

    public sealed interface Neighbor permits SideChainPartBlock.EmptyNeighbor, SideChainPartBlock.SideChainNeighbor {

        BlockPos pos();

        boolean isConnectable();

        boolean isUnconnectableOrChainEnd();

        boolean connectsTowards(SideChainPart endPart);

        default void connectToTheRight() {}

        default void connectToTheLeft() {}

        default void disconnectFromRight() {}

        default void disconnectFromLeft() {}
    }

    public static record EmptyNeighbor(BlockPos pos) implements SideChainPartBlock.Neighbor {

        @Override
        public boolean isConnectable() {
            return false;
        }

        @Override
        public boolean isUnconnectableOrChainEnd() {
            return true;
        }

        @Override
        public boolean connectsTowards(SideChainPart endPart) {
            return false;
        }
    }

    public static record SideChainNeighbor(LevelAccessor level, SideChainPartBlock block, BlockPos pos, SideChainPart part) implements SideChainPartBlock.Neighbor {

        @Override
        public boolean isConnectable() {
            return true;
        }

        @Override
        public boolean isUnconnectableOrChainEnd() {
            return this.part.isChainEnd();
        }

        @Override
        public boolean connectsTowards(SideChainPart endPart) {
            return this.part.isConnectionTowards(endPart);
        }

        @Override
        public void connectToTheRight() {
            this.block.setPart(this.level, this.pos, this.part.whenConnectedToTheRight());
        }

        @Override
        public void connectToTheLeft() {
            this.block.setPart(this.level, this.pos, this.part.whenConnectedToTheLeft());
        }

        @Override
        public void disconnectFromRight() {
            this.block.setPart(this.level, this.pos, this.part.whenDisconnectedFromTheRight());
        }

        @Override
        public void disconnectFromLeft() {
            this.block.setPart(this.level, this.pos, this.part.whenDisconnectedFromTheLeft());
        }
    }
}
