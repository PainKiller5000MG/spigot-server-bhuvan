package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public abstract class ContainerOpenersCounter {

    private static final int CHECK_TICK_DELAY = 5;
    private int openCount;
    private double maxInteractionRange;

    public ContainerOpenersCounter() {}

    protected abstract void onOpen(Level level, BlockPos pos, BlockState blockState);

    protected abstract void onClose(Level level, BlockPos pos, BlockState blockState);

    protected abstract void openerCountChanged(Level level, BlockPos pos, BlockState blockState, int previous, int current);

    public abstract boolean isOwnContainer(Player player);

    public void incrementOpeners(LivingEntity entity, Level level, BlockPos pos, BlockState blockState, double maxInteractionRange) {
        int i = this.openCount++;

        if (i == 0) {
            this.onOpen(level, pos, blockState);
            level.gameEvent(entity, (Holder) GameEvent.CONTAINER_OPEN, pos);
            scheduleRecheck(level, pos, blockState);
        }

        this.openerCountChanged(level, pos, blockState, i, this.openCount);
        this.maxInteractionRange = Math.max(maxInteractionRange, this.maxInteractionRange);
    }

    public void decrementOpeners(LivingEntity entity, Level level, BlockPos pos, BlockState blockState) {
        int i = this.openCount--;

        if (this.openCount == 0) {
            this.onClose(level, pos, blockState);
            level.gameEvent(entity, (Holder) GameEvent.CONTAINER_CLOSE, pos);
            this.maxInteractionRange = 0.0D;
        }

        this.openerCountChanged(level, pos, blockState, i, this.openCount);
    }

    public List<ContainerUser> getEntitiesWithContainerOpen(Level level, BlockPos pos) {
        double d0 = this.maxInteractionRange + 4.0D;
        AABB aabb = (new AABB(pos)).inflate(d0);

        return (List) level.getEntities((Entity) null, aabb, (entity) -> {
            return this.hasContainerOpen(entity, pos);
        }).stream().map((entity) -> {
            return (ContainerUser) entity;
        }).collect(Collectors.toList());
    }

    private boolean hasContainerOpen(Entity entity, BlockPos blockPos) {
        if (entity instanceof ContainerUser containeruser) {
            if (!containeruser.getLivingEntity().isSpectator()) {
                return containeruser.hasContainerOpen(this, blockPos);
            }
        }

        return false;
    }

    public void recheckOpeners(Level level, BlockPos pos, BlockState blockState) {
        List<ContainerUser> list = this.getEntitiesWithContainerOpen(level, pos);

        this.maxInteractionRange = 0.0D;

        for (ContainerUser containeruser : list) {
            this.maxInteractionRange = Math.max(containeruser.getContainerInteractionRange(), this.maxInteractionRange);
        }

        int i = list.size();
        int j = this.openCount;

        if (j != i) {
            boolean flag = i != 0;
            boolean flag1 = j != 0;

            if (flag && !flag1) {
                this.onOpen(level, pos, blockState);
                level.gameEvent((Entity) null, (Holder) GameEvent.CONTAINER_OPEN, pos);
            } else if (!flag) {
                this.onClose(level, pos, blockState);
                level.gameEvent((Entity) null, (Holder) GameEvent.CONTAINER_CLOSE, pos);
            }

            this.openCount = i;
        }

        this.openerCountChanged(level, pos, blockState, j, i);
        if (i > 0) {
            scheduleRecheck(level, pos, blockState);
        }

    }

    public int getOpenerCount() {
        return this.openCount;
    }

    private static void scheduleRecheck(Level level, BlockPos blockPos, BlockState blockState) {
        level.scheduleTick(blockPos, blockState.getBlock(), 5);
    }
}
