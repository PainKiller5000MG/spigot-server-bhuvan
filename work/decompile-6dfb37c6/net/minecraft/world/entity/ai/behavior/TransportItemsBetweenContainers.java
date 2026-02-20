package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.function.TriConsumer;
import org.jspecify.annotations.Nullable;

public class TransportItemsBetweenContainers extends Behavior<PathfinderMob> {

    public static final int TARGET_INTERACTION_TIME = 60;
    private static final int VISITED_POSITIONS_MEMORY_TIME = 6000;
    private static final int TRANSPORTED_ITEM_MAX_STACK_SIZE = 16;
    private static final int MAX_VISITED_POSITIONS = 10;
    private static final int MAX_UNREACHABLE_POSITIONS = 50;
    private static final int PASSENGER_MOB_TARGET_SEARCH_DISTANCE = 1;
    private static final int IDLE_COOLDOWN = 140;
    private static final double CLOSE_ENOUGH_TO_START_QUEUING_DISTANCE = 3.0D;
    private static final double CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_DISTANCE = 0.5D;
    private static final double CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_PATH_END_DISTANCE = 1.0D;
    private static final double CLOSE_ENOUGH_TO_CONTINUE_INTERACTING_WITH_TARGET = 2.0D;
    private final float speedModifier;
    private final int horizontalSearchDistance;
    private final int verticalSearchDistance;
    private final Predicate<BlockState> sourceBlockType;
    private final Predicate<BlockState> destinationBlockType;
    private final Predicate<TransportItemsBetweenContainers.TransportItemTarget> shouldQueueForTarget;
    private final Consumer<PathfinderMob> onStartTravelling;
    private final Map<TransportItemsBetweenContainers.ContainerInteractionState, TransportItemsBetweenContainers.OnTargetReachedInteraction> onTargetInteractionActions;
    private TransportItemsBetweenContainers.@Nullable TransportItemTarget target = null;
    private TransportItemsBetweenContainers.TransportItemState state;
    private TransportItemsBetweenContainers.@Nullable ContainerInteractionState interactionState;
    private int ticksSinceReachingTarget;

    public TransportItemsBetweenContainers(float speedModifier, Predicate<BlockState> sourceBlockType, Predicate<BlockState> destinationBlockType, int horizontalSearchDistance, int verticalSearchDistance, Map<TransportItemsBetweenContainers.ContainerInteractionState, TransportItemsBetweenContainers.OnTargetReachedInteraction> onTargetInteractionActions, Consumer<PathfinderMob> onStartTravelling, Predicate<TransportItemsBetweenContainers.TransportItemTarget> shouldQueueForTarget) {
        super(ImmutableMap.of(MemoryModuleType.VISITED_BLOCK_POSITIONS, MemoryStatus.REGISTERED, MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS, MemoryStatus.REGISTERED, MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.sourceBlockType = sourceBlockType;
        this.destinationBlockType = destinationBlockType;
        this.horizontalSearchDistance = horizontalSearchDistance;
        this.verticalSearchDistance = verticalSearchDistance;
        this.onStartTravelling = onStartTravelling;
        this.shouldQueueForTarget = shouldQueueForTarget;
        this.onTargetInteractionActions = onTargetInteractionActions;
        this.state = TransportItemsBetweenContainers.TransportItemState.TRAVELLING;
    }

    protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
        PathNavigation pathnavigation = body.getNavigation();

        if (pathnavigation instanceof GroundPathNavigation groundpathnavigation) {
            groundpathnavigation.setCanPathToTargetsBelowSurface(true);
        }

    }

    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
        return !body.isLeashed();
    }

    protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
        return body.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty() && !body.isPanicking() && !body.isLeashed();
    }

    @Override
    protected boolean timedOut(long timestamp) {
        return false;
    }

    protected void tick(ServerLevel level, PathfinderMob body, long timestamp) {
        boolean flag = this.updateInvalidTarget(level, body);

        if (this.target == null) {
            this.stop(level, body, timestamp);
        } else if (!flag) {
            if (this.state.equals(TransportItemsBetweenContainers.TransportItemState.QUEUING)) {
                this.onQueuingForTarget(this.target, level, body);
            }

            if (this.state.equals(TransportItemsBetweenContainers.TransportItemState.TRAVELLING)) {
                this.onTravelToTarget(this.target, level, body);
            }

            if (this.state.equals(TransportItemsBetweenContainers.TransportItemState.INTERACTING)) {
                this.onReachedTarget(this.target, level, body);
            }

        }
    }

    private boolean updateInvalidTarget(ServerLevel level, PathfinderMob body) {
        if (!this.hasValidTarget(level, body)) {
            this.stopTargetingCurrentTarget(body);
            Optional<TransportItemsBetweenContainers.TransportItemTarget> optional = this.getTransportTarget(level, body);

            if (optional.isPresent()) {
                this.target = (TransportItemsBetweenContainers.TransportItemTarget) optional.get();
                this.onStartTravelling(body);
                this.setVisitedBlockPos(body, level, this.target.pos);
                return true;
            } else {
                this.enterCooldownAfterNoMatchingTargetFound(body);
                return true;
            }
        } else {
            return false;
        }
    }

    private void onQueuingForTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob body) {
        if (!this.isAnotherMobInteractingWithTarget(target, level)) {
            this.resumeTravelling(body);
        }

    }

    protected void onTravelToTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob body) {
        if (this.isWithinTargetDistance(3.0D, target, level, body, this.getCenterPos(body)) && this.isAnotherMobInteractingWithTarget(target, level)) {
            this.startQueuing(body);
        } else if (this.isWithinTargetDistance(getInteractionRange(body), target, level, body, this.getCenterPos(body))) {
            this.startOnReachedTargetInteraction(target, body);
        } else {
            this.walkTowardsTarget(body);
        }

    }

    private Vec3 getCenterPos(PathfinderMob body) {
        return this.setMiddleYPosition(body, body.position());
    }

    protected void onReachedTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob body) {
        if (!this.isWithinTargetDistance(2.0D, target, level, body, this.getCenterPos(body))) {
            this.onStartTravelling(body);
        } else {
            ++this.ticksSinceReachingTarget;
            this.onTargetInteraction(target, body);
            if (this.ticksSinceReachingTarget >= 60) {
                this.doReachedTargetInteraction(body, target.container, this::pickUpItems, (pathfindermob1, container) -> {
                    this.stopTargetingCurrentTarget(body);
                }, this::putDownItem, (pathfindermob1, container) -> {
                    this.stopTargetingCurrentTarget(body);
                });
                this.onStartTravelling(body);
            }
        }

    }

    private void startQueuing(PathfinderMob body) {
        this.stopInPlace(body);
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.QUEUING);
    }

    private void resumeTravelling(PathfinderMob body) {
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.TRAVELLING);
        this.walkTowardsTarget(body);
    }

    private void walkTowardsTarget(PathfinderMob body) {
        if (this.target != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(body, this.target.pos, this.speedModifier, 0);
        }

    }

    private void startOnReachedTargetInteraction(TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob body) {
        this.doReachedTargetInteraction(body, target.container, this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PICKUP_ITEM), this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PICKUP_NO_ITEM), this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PLACE_ITEM), this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PLACE_NO_ITEM));
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.INTERACTING);
    }

    private void onStartTravelling(PathfinderMob body) {
        this.onStartTravelling.accept(body);
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.TRAVELLING);
        this.interactionState = null;
        this.ticksSinceReachingTarget = 0;
    }

    private BiConsumer<PathfinderMob, Container> onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState state) {
        return (pathfindermob, container) -> {
            this.setInteractionState(state);
        };
    }

    private void setTransportingState(TransportItemsBetweenContainers.TransportItemState state) {
        this.state = state;
    }

    private void setInteractionState(TransportItemsBetweenContainers.ContainerInteractionState state) {
        this.interactionState = state;
    }

    private void onTargetInteraction(TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob body) {
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.pos));
        this.stopInPlace(body);
        if (this.interactionState != null) {
            Optional.ofNullable((TransportItemsBetweenContainers.OnTargetReachedInteraction) this.onTargetInteractionActions.get(this.interactionState)).ifPresent((transportitemsbetweencontainers_ontargetreachedinteraction) -> {
                transportitemsbetweencontainers_ontargetreachedinteraction.accept(body, target, this.ticksSinceReachingTarget);
            });
        }

    }

    private void doReachedTargetInteraction(PathfinderMob body, Container container, BiConsumer<PathfinderMob, Container> onPickupSuccess, BiConsumer<PathfinderMob, Container> onPickupFailure, BiConsumer<PathfinderMob, Container> onPlaceSuccess, BiConsumer<PathfinderMob, Container> onPlaceFailure) {
        if (isPickingUpItems(body)) {
            if (matchesGettingItemsRequirement(container)) {
                onPickupSuccess.accept(body, container);
            } else {
                onPickupFailure.accept(body, container);
            }
        } else if (matchesLeavingItemsRequirement(body, container)) {
            onPlaceSuccess.accept(body, container);
        } else {
            onPlaceFailure.accept(body, container);
        }

    }

    private Optional<TransportItemsBetweenContainers.TransportItemTarget> getTransportTarget(ServerLevel level, PathfinderMob body) {
        AABB aabb = this.getTargetSearchArea(body);
        Set<GlobalPos> set = getVisitedPositions(body);
        Set<GlobalPos> set1 = getUnreachablePositions(body);
        List<ChunkPos> list = ChunkPos.rangeClosed(new ChunkPos(body.blockPosition()), Math.floorDiv(this.getHorizontalSearchDistance(body), 16) + 1).toList();
        TransportItemsBetweenContainers.TransportItemTarget transportitemsbetweencontainers_transportitemtarget = null;
        double d0 = (double) Float.MAX_VALUE;

        for (ChunkPos chunkpos : list) {
            LevelChunk levelchunk = level.getChunkSource().getChunkNow(chunkpos.x, chunkpos.z);

            if (levelchunk != null) {
                for (BlockEntity blockentity : levelchunk.getBlockEntities().values()) {
                    if (blockentity instanceof ChestBlockEntity) {
                        ChestBlockEntity chestblockentity = (ChestBlockEntity) blockentity;
                        double d1 = chestblockentity.getBlockPos().distToCenterSqr(body.position());

                        if (d1 < d0) {
                            TransportItemsBetweenContainers.TransportItemTarget transportitemsbetweencontainers_transportitemtarget1 = this.isTargetValidToPick(body, level, chestblockentity, set, set1, aabb);

                            if (transportitemsbetweencontainers_transportitemtarget1 != null) {
                                transportitemsbetweencontainers_transportitemtarget = transportitemsbetweencontainers_transportitemtarget1;
                                d0 = d1;
                            }
                        }
                    }
                }
            }
        }

        return transportitemsbetweencontainers_transportitemtarget == null ? Optional.empty() : Optional.of(transportitemsbetweencontainers_transportitemtarget);
    }

    private TransportItemsBetweenContainers.@Nullable TransportItemTarget isTargetValidToPick(PathfinderMob body, Level level, BlockEntity blockEntity, Set<GlobalPos> visitedPositions, Set<GlobalPos> unreachablePositions, AABB targetBlockSearchArea) {
        BlockPos blockpos = blockEntity.getBlockPos();
        boolean flag = targetBlockSearchArea.contains((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());

        if (!flag) {
            return null;
        } else {
            TransportItemsBetweenContainers.TransportItemTarget transportitemsbetweencontainers_transportitemtarget = TransportItemsBetweenContainers.TransportItemTarget.tryCreatePossibleTarget(blockEntity, level);

            if (transportitemsbetweencontainers_transportitemtarget == null) {
                return null;
            } else {
                boolean flag1 = this.isWantedBlock(body, transportitemsbetweencontainers_transportitemtarget.state) && !this.isPositionAlreadyVisited(visitedPositions, unreachablePositions, transportitemsbetweencontainers_transportitemtarget, level) && !this.isContainerLocked(transportitemsbetweencontainers_transportitemtarget);

                return flag1 ? transportitemsbetweencontainers_transportitemtarget : null;
            }
        }
    }

    private boolean isContainerLocked(TransportItemsBetweenContainers.TransportItemTarget transportItemTarget) {
        BlockEntity blockentity = transportItemTarget.blockEntity;
        boolean flag;

        if (blockentity instanceof BaseContainerBlockEntity basecontainerblockentity) {
            if (basecontainerblockentity.isLocked()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    private boolean hasValidTarget(Level level, PathfinderMob body) {
        boolean flag = this.target != null && this.isWantedBlock(body, this.target.state) && this.targetHasNotChanged(level, this.target);

        if (flag && !this.isTargetBlocked(level, this.target)) {
            if (!this.state.equals(TransportItemsBetweenContainers.TransportItemState.TRAVELLING)) {
                return true;
            }

            if (this.hasValidTravellingPath(level, this.target, body)) {
                return true;
            }

            this.markVisitedBlockPosAsUnreachable(body, level, this.target.pos);
        }

        return false;
    }

    private boolean hasValidTravellingPath(Level level, TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob body) {
        Path path = body.getNavigation().getPath() == null ? body.getNavigation().createPath(target.pos, 0) : body.getNavigation().getPath();
        Vec3 vec3 = this.getPositionToReachTargetFrom(path, body);
        boolean flag = this.isWithinTargetDistance(getInteractionRange(body), target, level, body, vec3);
        boolean flag1 = path == null && !flag;

        return flag1 || this.targetIsReachableFromPosition(level, flag, vec3, target, body);
    }

    private Vec3 getPositionToReachTargetFrom(@Nullable Path path, PathfinderMob body) {
        boolean flag = path == null || path.getEndNode() == null;
        Vec3 vec3 = flag ? body.position() : path.getEndNode().asBlockPos().getBottomCenter();

        return this.setMiddleYPosition(body, vec3);
    }

    private Vec3 setMiddleYPosition(PathfinderMob body, Vec3 pos) {
        return pos.add(0.0D, body.getBoundingBox().getYsize() / 2.0D, 0.0D);
    }

    private boolean isTargetBlocked(Level level, TransportItemsBetweenContainers.TransportItemTarget target) {
        return ChestBlock.isChestBlockedAt(level, target.pos);
    }

    private boolean targetHasNotChanged(Level level, TransportItemsBetweenContainers.TransportItemTarget target) {
        return target.blockEntity.equals(level.getBlockEntity(target.pos));
    }

    private Stream<TransportItemsBetweenContainers.TransportItemTarget> getConnectedTargets(TransportItemsBetweenContainers.TransportItemTarget target, Level level) {
        if (target.state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
            TransportItemsBetweenContainers.TransportItemTarget transportitemsbetweencontainers_transportitemtarget1 = TransportItemsBetweenContainers.TransportItemTarget.tryCreatePossibleTarget(ChestBlock.getConnectedBlockPos(target.pos, target.state), level);

            return transportitemsbetweencontainers_transportitemtarget1 != null ? Stream.of(target, transportitemsbetweencontainers_transportitemtarget1) : Stream.of(target);
        } else {
            return Stream.of(target);
        }
    }

    private AABB getTargetSearchArea(PathfinderMob mob) {
        int i = this.getHorizontalSearchDistance(mob);

        return (new AABB(mob.blockPosition())).inflate((double) i, (double) this.getVerticalSearchDistance(mob), (double) i);
    }

    private int getHorizontalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? 1 : this.horizontalSearchDistance;
    }

    private int getVerticalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? 1 : this.verticalSearchDistance;
    }

    private static Set<GlobalPos> getVisitedPositions(PathfinderMob mob) {
        return (Set) mob.getBrain().getMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS).orElse(Set.of());
    }

    private static Set<GlobalPos> getUnreachablePositions(PathfinderMob mob) {
        return (Set) mob.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS).orElse(Set.of());
    }

    private boolean isPositionAlreadyVisited(Set<GlobalPos> visitedPositions, Set<GlobalPos> unreachablePositions, TransportItemsBetweenContainers.TransportItemTarget target, Level level) {
        return this.getConnectedTargets(target, level).map((transportitemsbetweencontainers_transportitemtarget1) -> {
            return new GlobalPos(level.dimension(), transportitemsbetweencontainers_transportitemtarget1.pos);
        }).anyMatch((globalpos) -> {
            return visitedPositions.contains(globalpos) || unreachablePositions.contains(globalpos);
        });
    }

    private static boolean hasFinishedPath(PathfinderMob body) {
        return body.getNavigation().getPath() != null && body.getNavigation().getPath().isDone();
    }

    protected void setVisitedBlockPos(PathfinderMob body, Level level, BlockPos target) {
        Set<GlobalPos> set = new HashSet(getVisitedPositions(body));

        set.add(new GlobalPos(level.dimension(), target));
        if (set.size() > 10) {
            this.enterCooldownAfterNoMatchingTargetFound(body);
        } else {
            body.getBrain().setMemoryWithExpiry(MemoryModuleType.VISITED_BLOCK_POSITIONS, set, 6000L);
        }

    }

    protected void markVisitedBlockPosAsUnreachable(PathfinderMob body, Level level, BlockPos target) {
        Set<GlobalPos> set = new HashSet(getVisitedPositions(body));

        set.remove(new GlobalPos(level.dimension(), target));
        Set<GlobalPos> set1 = new HashSet(getUnreachablePositions(body));

        set1.add(new GlobalPos(level.dimension(), target));
        if (set1.size() > 50) {
            this.enterCooldownAfterNoMatchingTargetFound(body);
        } else {
            body.getBrain().setMemoryWithExpiry(MemoryModuleType.VISITED_BLOCK_POSITIONS, set, 6000L);
            body.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS, set1, 6000L);
        }

    }

    private boolean isWantedBlock(PathfinderMob mob, BlockState block) {
        return isPickingUpItems(mob) ? this.sourceBlockType.test(block) : this.destinationBlockType.test(block);
    }

    private static double getInteractionRange(PathfinderMob body) {
        return hasFinishedPath(body) ? 1.0D : 0.5D;
    }

    private boolean isWithinTargetDistance(double distance, TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob body, Vec3 fromPos) {
        AABB aabb = body.getBoundingBox();
        AABB aabb1 = AABB.ofSize(fromPos, aabb.getXsize(), aabb.getYsize(), aabb.getZsize());

        return target.state.getCollisionShape(level, target.pos).bounds().inflate(distance, 0.5D, distance).move(target.pos).intersects(aabb1);
    }

    private boolean targetIsReachableFromPosition(Level level, boolean canReachTarget, Vec3 pos, TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob body) {
        return canReachTarget && this.canSeeAnyTargetSide(target, level, body, pos);
    }

    private boolean canSeeAnyTargetSide(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob body, Vec3 eyePosition) {
        Vec3 vec31 = target.pos.getCenter();

        return Direction.stream().map((direction) -> {
            return vec31.add(0.5D * (double) direction.getStepX(), 0.5D * (double) direction.getStepY(), 0.5D * (double) direction.getStepZ());
        }).map((vec32) -> {
            return level.clip(new ClipContext(eyePosition, vec32, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        }).anyMatch((blockhitresult) -> {
            return blockhitresult.getType() == HitResult.Type.BLOCK && blockhitresult.getBlockPos().equals(target.pos);
        });
    }

    private boolean isAnotherMobInteractingWithTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level) {
        return this.getConnectedTargets(target, level).anyMatch(this.shouldQueueForTarget);
    }

    private static boolean isPickingUpItems(PathfinderMob body) {
        return body.getMainHandItem().isEmpty();
    }

    private static boolean matchesGettingItemsRequirement(Container container) {
        return !container.isEmpty();
    }

    private static boolean matchesLeavingItemsRequirement(PathfinderMob body, Container container) {
        return container.isEmpty() || hasItemMatchingHandItem(body, container);
    }

    private static boolean hasItemMatchingHandItem(PathfinderMob body, Container container) {
        ItemStack itemstack = body.getMainHandItem();

        for (ItemStack itemstack1 : container) {
            if (ItemStack.isSameItem(itemstack1, itemstack)) {
                return true;
            }
        }

        return false;
    }

    private void pickUpItems(PathfinderMob body, Container container) {
        body.setItemSlot(EquipmentSlot.MAINHAND, pickupItemFromContainer(container));
        body.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        container.setChanged();
        this.clearMemoriesAfterMatchingTargetFound(body);
    }

    private void putDownItem(PathfinderMob body, Container container) {
        ItemStack itemstack = addItemsToContainer(body, container);

        container.setChanged();
        body.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
        if (itemstack.isEmpty()) {
            this.clearMemoriesAfterMatchingTargetFound(body);
        } else {
            this.stopTargetingCurrentTarget(body);
        }

    }

    private static ItemStack pickupItemFromContainer(Container container) {
        int i = 0;

        for (ItemStack itemstack : container) {
            if (!itemstack.isEmpty()) {
                int j = Math.min(itemstack.getCount(), 16);

                return container.removeItem(i, j);
            }

            ++i;
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack addItemsToContainer(PathfinderMob body, Container container) {
        int i = 0;
        ItemStack itemstack = body.getMainHandItem();

        for (ItemStack itemstack1 : container) {
            if (itemstack1.isEmpty()) {
                container.setItem(i, itemstack);
                return ItemStack.EMPTY;
            }

            if (ItemStack.isSameItemSameComponents(itemstack1, itemstack) && itemstack1.getCount() < itemstack1.getMaxStackSize()) {
                int j = itemstack1.getMaxStackSize() - itemstack1.getCount();
                int k = Math.min(j, itemstack.getCount());

                itemstack1.setCount(itemstack1.getCount() + k);
                itemstack.setCount(itemstack.getCount() - j);
                container.setItem(i, itemstack1);
                if (itemstack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }

            ++i;
        }

        return itemstack;
    }

    protected void stopTargetingCurrentTarget(PathfinderMob body) {
        this.ticksSinceReachingTarget = 0;
        this.target = null;
        body.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    protected void clearMemoriesAfterMatchingTargetFound(PathfinderMob body) {
        this.stopTargetingCurrentTarget(body);
        body.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        body.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
    }

    private void enterCooldownAfterNoMatchingTargetFound(PathfinderMob body) {
        this.stopTargetingCurrentTarget(body);
        body.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 140);
        body.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        body.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
    }

    protected void stop(ServerLevel level, PathfinderMob body, long timestamp) {
        this.onStartTravelling(body);
        PathNavigation pathnavigation = body.getNavigation();

        if (pathnavigation instanceof GroundPathNavigation groundpathnavigation) {
            groundpathnavigation.setCanPathToTargetsBelowSurface(false);
        }

    }

    private void stopInPlace(PathfinderMob mob) {
        mob.getNavigation().stop();
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setSpeed(0.0F);
        mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
    }

    public static enum TransportItemState {

        TRAVELLING, QUEUING, INTERACTING;

        private TransportItemState() {}
    }

    public static enum ContainerInteractionState {

        PICKUP_ITEM, PICKUP_NO_ITEM, PLACE_ITEM, PLACE_NO_ITEM;

        private ContainerInteractionState() {}
    }

    public static record TransportItemTarget(BlockPos pos, Container container, BlockEntity blockEntity, BlockState state) {

        public static TransportItemsBetweenContainers.@Nullable TransportItemTarget tryCreatePossibleTarget(BlockEntity blockEntity, Level level) {
            BlockPos blockpos = blockEntity.getBlockPos();
            BlockState blockstate = blockEntity.getBlockState();
            Container container = getBlockEntityContainer(blockEntity, blockstate, level, blockpos);

            return container != null ? new TransportItemsBetweenContainers.TransportItemTarget(blockpos, container, blockEntity, blockstate) : null;
        }

        public static TransportItemsBetweenContainers.@Nullable TransportItemTarget tryCreatePossibleTarget(BlockPos blockPos, Level level) {
            BlockEntity blockentity = level.getBlockEntity(blockPos);

            return blockentity == null ? null : tryCreatePossibleTarget(blockentity, level);
        }

        private static @Nullable Container getBlockEntityContainer(BlockEntity blockEntity, BlockState blockState, Level level, BlockPos blockPos) {
            Block block = blockState.getBlock();

            if (block instanceof ChestBlock chestblock) {
                return ChestBlock.getContainer(chestblock, blockState, level, blockPos, false);
            } else if (blockEntity instanceof Container container) {
                return container;
            } else {
                return null;
            }
        }
    }

    @FunctionalInterface
    public interface OnTargetReachedInteraction extends TriConsumer<PathfinderMob, TransportItemsBetweenContainers.TransportItemTarget, Integer> {}
}
