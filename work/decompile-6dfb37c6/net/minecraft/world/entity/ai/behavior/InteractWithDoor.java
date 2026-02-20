package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Sets;
import com.mojang.datafixers.kinds.OptionalBox.Mu;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class InteractWithDoor {

    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 20;
    private static final double SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN = 3.0D;
    private static final double MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS = 2.0D;

    public InteractWithDoor() {}

    public static BehaviorControl<LivingEntity> create() {
        MutableObject<Node> mutableobject = new MutableObject();
        MutableInt mutableint = new MutableInt(0);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.PATH), behaviorbuilder_instance.registered(MemoryModuleType.DOORS_TO_CLOSE), behaviorbuilder_instance.registered(MemoryModuleType.NEAREST_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, livingentity, i) -> {
                    Path path = (Path) behaviorbuilder_instance.get(memoryaccessor);
                    Optional<Set<GlobalPos>> optional = behaviorbuilder_instance.<Set<GlobalPos>>tryGet(memoryaccessor1);

                    if (!path.notStarted() && !path.isDone()) {
                        if (Objects.equals(mutableobject.get(), path.getNextNode())) {
                            mutableint.setValue(20);
                        } else if (mutableint.decrementAndGet() > 0) {
                            return false;
                        }

                        mutableobject.setValue(path.getNextNode());
                        Node node = path.getPreviousNode();
                        Node node1 = path.getNextNode();
                        BlockPos blockpos = node.asBlockPos();
                        BlockState blockstate = serverlevel.getBlockState(blockpos);

                        if (blockstate.is(BlockTags.MOB_INTERACTABLE_DOORS, (blockbehaviour_blockstatebase) -> {
                            return blockbehaviour_blockstatebase.getBlock() instanceof DoorBlock;
                        })) {
                            DoorBlock doorblock = (DoorBlock) blockstate.getBlock();

                            if (!doorblock.isOpen(blockstate)) {
                                doorblock.setOpen(livingentity, serverlevel, blockstate, blockpos, true);
                            }

                            optional = rememberDoorToClose(memoryaccessor1, optional, serverlevel, blockpos);
                        }

                        BlockPos blockpos1 = node1.asBlockPos();
                        BlockState blockstate1 = serverlevel.getBlockState(blockpos1);

                        if (blockstate1.is(BlockTags.MOB_INTERACTABLE_DOORS, (blockbehaviour_blockstatebase) -> {
                            return blockbehaviour_blockstatebase.getBlock() instanceof DoorBlock;
                        })) {
                            DoorBlock doorblock1 = (DoorBlock) blockstate1.getBlock();

                            if (!doorblock1.isOpen(blockstate1)) {
                                doorblock1.setOpen(livingentity, serverlevel, blockstate1, blockpos1, true);
                                optional = rememberDoorToClose(memoryaccessor1, optional, serverlevel, blockpos1);
                            }
                        }

                        optional.ifPresent((set) -> {
                            closeDoorsThatIHaveOpenedOrPassedThrough(serverlevel, livingentity, node, node1, set, behaviorbuilder_instance.tryGet(memoryaccessor2));
                        });
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }

    public static void closeDoorsThatIHaveOpenedOrPassedThrough(ServerLevel level, LivingEntity body, @Nullable Node movingFromNode, @Nullable Node movingToNode, Set<GlobalPos> doors, Optional<List<LivingEntity>> nearestEntities) {
        Iterator<GlobalPos> iterator = doors.iterator();

        while (iterator.hasNext()) {
            GlobalPos globalpos = (GlobalPos) iterator.next();
            BlockPos blockpos = globalpos.pos();

            if ((movingFromNode == null || !movingFromNode.asBlockPos().equals(blockpos)) && (movingToNode == null || !movingToNode.asBlockPos().equals(blockpos))) {
                if (isDoorTooFarAway(level, body, globalpos)) {
                    iterator.remove();
                } else {
                    BlockState blockstate = level.getBlockState(blockpos);

                    if (!blockstate.is(BlockTags.MOB_INTERACTABLE_DOORS, (blockbehaviour_blockstatebase) -> {
                        return blockbehaviour_blockstatebase.getBlock() instanceof DoorBlock;
                    })) {
                        iterator.remove();
                    } else {
                        DoorBlock doorblock = (DoorBlock) blockstate.getBlock();

                        if (!doorblock.isOpen(blockstate)) {
                            iterator.remove();
                        } else if (areOtherMobsComingThroughDoor(body, blockpos, nearestEntities)) {
                            iterator.remove();
                        } else {
                            doorblock.setOpen(body, level, blockstate, blockpos, false);
                            iterator.remove();
                        }
                    }
                }
            }
        }

    }

    private static boolean areOtherMobsComingThroughDoor(LivingEntity body, BlockPos doorPos, Optional<List<LivingEntity>> nearestEntities) {
        return nearestEntities.isEmpty() ? false : ((List) nearestEntities.get()).stream().filter((livingentity1) -> {
            return livingentity1.getType() == body.getType();
        }).filter((livingentity1) -> {
            return doorPos.closerToCenterThan(livingentity1.position(), 2.0D);
        }).anyMatch((livingentity1) -> {
            return isMobComingThroughDoor(livingentity1.getBrain(), doorPos);
        });
    }

    private static boolean isMobComingThroughDoor(Brain<?> otherBrain, BlockPos doorPos) {
        if (!otherBrain.hasMemoryValue(MemoryModuleType.PATH)) {
            return false;
        } else {
            Path path = (Path) otherBrain.getMemory(MemoryModuleType.PATH).get();

            if (path.isDone()) {
                return false;
            } else {
                Node node = path.getPreviousNode();

                if (node == null) {
                    return false;
                } else {
                    Node node1 = path.getNextNode();

                    return doorPos.equals(node.asBlockPos()) || doorPos.equals(node1.asBlockPos());
                }
            }
        }
    }

    private static boolean isDoorTooFarAway(ServerLevel level, LivingEntity body, GlobalPos doorGlobalPos) {
        return doorGlobalPos.dimension() != level.dimension() || !doorGlobalPos.pos().closerToCenterThan(body.position(), 3.0D);
    }

    private static Optional<Set<GlobalPos>> rememberDoorToClose(MemoryAccessor<Mu, Set<GlobalPos>> doorsMemory, Optional<Set<GlobalPos>> doors, ServerLevel level, BlockPos doorPos) {
        GlobalPos globalpos = GlobalPos.of(level.dimension(), doorPos);

        return Optional.of((Set) doors.map((set) -> {
            set.add(globalpos);
            return set;
        }).orElseGet(() -> {
            Set<GlobalPos> set = Sets.newHashSet(new GlobalPos[]{globalpos});

            doorsMemory.set(set);
            return set;
        }));
    }
}
