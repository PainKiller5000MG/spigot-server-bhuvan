package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

public record DebugBrainDump(String name, String profession, int xp, float health, float maxHealth, String inventory, boolean wantsGolem, int angerLevel, List<String> activities, List<String> behaviors, List<String> memories, List<String> gossips, Set<BlockPos> pois, Set<BlockPos> potentialPois) {

    public static final StreamCodec<FriendlyByteBuf, DebugBrainDump> STREAM_CODEC = StreamCodec.<FriendlyByteBuf, DebugBrainDump>of((friendlybytebuf, debugbraindump) -> {
        debugbraindump.write(friendlybytebuf);
    }, DebugBrainDump::new);

    public DebugBrainDump(FriendlyByteBuf input) {
        this(input.readUtf(), input.readUtf(), input.readInt(), input.readFloat(), input.readFloat(), input.readUtf(), input.readBoolean(), input.readInt(), input.readList(FriendlyByteBuf::readUtf), input.readList(FriendlyByteBuf::readUtf), input.readList(FriendlyByteBuf::readUtf), input.readList(FriendlyByteBuf::readUtf), (Set) input.readCollection(HashSet::new, BlockPos.STREAM_CODEC), (Set) input.readCollection(HashSet::new, BlockPos.STREAM_CODEC));
    }

    public void write(FriendlyByteBuf output) {
        output.writeUtf(this.name);
        output.writeUtf(this.profession);
        output.writeInt(this.xp);
        output.writeFloat(this.health);
        output.writeFloat(this.maxHealth);
        output.writeUtf(this.inventory);
        output.writeBoolean(this.wantsGolem);
        output.writeInt(this.angerLevel);
        output.writeCollection(this.activities, FriendlyByteBuf::writeUtf);
        output.writeCollection(this.behaviors, FriendlyByteBuf::writeUtf);
        output.writeCollection(this.memories, FriendlyByteBuf::writeUtf);
        output.writeCollection(this.gossips, FriendlyByteBuf::writeUtf);
        output.writeCollection(this.pois, BlockPos.STREAM_CODEC);
        output.writeCollection(this.potentialPois, BlockPos.STREAM_CODEC);
    }

    public static DebugBrainDump takeBrainDump(ServerLevel serverLevel, LivingEntity entity) {
        String s = DebugEntityNameGenerator.getEntityName((Entity) entity);
        String s1;
        int i;

        if (entity instanceof Villager villager) {
            s1 = villager.getVillagerData().profession().getRegisteredName();
            i = villager.getVillagerXp();
        } else {
            s1 = "";
            i = 0;
        }

        float f = entity.getHealth();
        float f1 = entity.getMaxHealth();
        Brain<?> brain = entity.getBrain();
        long j = entity.level().getGameTime();
        String s2;

        if (entity instanceof InventoryCarrier inventorycarrier) {
            Container container = inventorycarrier.getInventory();

            s2 = container.isEmpty() ? "" : container.toString();
        } else {
            s2 = "";
        }

        boolean flag;
        label36:
        {
            if (entity instanceof Villager villager1) {
                if (villager1.wantsToSpawnGolem(j)) {
                    flag = 1;
                    break label36;
                }
            }

            flag = 0;
        }

        boolean flag1 = (boolean) flag;

        if (entity instanceof Warden warden) {
            flag = warden.getClientAngerLevel();
        } else {
            flag = -1;
        }

        int k = flag;
        List<String> list = brain.getActiveActivities().stream().map(Activity::getName).toList();
        List<String> list1 = brain.getRunningBehaviors().stream().map(BehaviorControl::debugString).toList();
        List<String> list2 = getMemoryDescriptions(serverLevel, entity, j).map((s3) -> {
            return StringUtil.truncateStringIfNecessary(s3, 255, true);
        }).toList();
        Set<BlockPos> set = getKnownBlockPositions(brain, MemoryModuleType.JOB_SITE, MemoryModuleType.HOME, MemoryModuleType.MEETING_POINT);
        Set<BlockPos> set1 = getKnownBlockPositions(brain, MemoryModuleType.POTENTIAL_JOB_SITE);
        List list3;

        if (entity instanceof Villager villager2) {
            list3 = getVillagerGossips(villager2);
        } else {
            list3 = List.of();
        }

        List<String> list4 = list3;

        return new DebugBrainDump(s, s1, i, f, f1, s2, flag1, k, list, list1, list2, list4, set, set1);
    }

    @SafeVarargs
    private static Set<BlockPos> getKnownBlockPositions(Brain<?> brain, MemoryModuleType<GlobalPos>... memories) {
        Stream stream = Stream.of(memories);

        Objects.requireNonNull(brain);
        stream = stream.filter(brain::hasMemoryValue);
        Objects.requireNonNull(brain);
        return (Set) stream.map(brain::getMemory).flatMap(Optional::stream).map(GlobalPos::pos).collect(Collectors.toSet());
    }

    private static List<String> getVillagerGossips(Villager villager) {
        List<String> list = new ArrayList();

        villager.getGossips().getGossipEntries().forEach((uuid, object2intmap) -> {
            String s = DebugEntityNameGenerator.getEntityName(uuid);

            object2intmap.forEach((gossiptype, i) -> {
                list.add(s + ": " + String.valueOf(gossiptype) + ": " + i);
            });
        });
        return list;
    }

    private static Stream<String> getMemoryDescriptions(ServerLevel level, LivingEntity body, long timestamp) {
        return body.getBrain().getMemories().entrySet().stream().map((entry) -> {
            MemoryModuleType<?> memorymoduletype = (MemoryModuleType) entry.getKey();
            Optional<? extends ExpirableValue<?>> optional = (Optional) entry.getValue();

            return getMemoryDescription(level, timestamp, memorymoduletype, optional);
        }).sorted();
    }

    private static String getMemoryDescription(ServerLevel level, long timestamp, MemoryModuleType<?> memoryType, Optional<? extends ExpirableValue<?>> maybeValue) {
        String s;

        if (maybeValue.isPresent()) {
            ExpirableValue<?> expirablevalue = (ExpirableValue) maybeValue.get();
            Object object = expirablevalue.getValue();

            if (memoryType == MemoryModuleType.HEARD_BELL_TIME) {
                long j = timestamp - (Long) object;

                s = j + " ticks ago";
            } else if (expirablevalue.canExpire()) {
                String s1 = getShortDescription(level, object);

                s = s1 + " (ttl: " + expirablevalue.getTimeToLive() + ")";
            } else {
                s = getShortDescription(level, object);
            }
        } else {
            s = "-";
        }

        String s2 = BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(memoryType).getPath();

        return s2 + ": " + s;
    }

    private static String getShortDescription(ServerLevel level, @Nullable Object obj) {
        byte b0 = 0;
        String s;

        //$FF: b0->value
        //0->java/util/UUID
        //1->net/minecraft/world/entity/Entity
        //2->net/minecraft/world/entity/ai/memory/WalkTarget
        //3->net/minecraft/world/entity/ai/behavior/EntityTracker
        //4->net/minecraft/core/GlobalPos
        //5->net/minecraft/world/entity/ai/behavior/BlockPosTracker
        //6->net/minecraft/world/damagesource/DamageSource
        //7->java/util/Collection
        switch (((Class)obj).typeSwitch<invokedynamic>(obj, b0)) {
            case -1:
                s = "-";
                break;
            case 0:
                UUID uuid = (UUID)obj;

                s = getShortDescription(level, level.getEntity(uuid));
                break;
            case 1:
                Entity entity = (Entity)obj;

                s = DebugEntityNameGenerator.getEntityName(entity);
                break;
            case 2:
                WalkTarget walktarget = (WalkTarget)obj;

                s = getShortDescription(level, walktarget.getTarget());
                break;
            case 3:
                EntityTracker entitytracker = (EntityTracker)obj;

                s = getShortDescription(level, entitytracker.getEntity());
                break;
            case 4:
                GlobalPos globalpos = (GlobalPos)obj;

                s = getShortDescription(level, globalpos.pos());
                break;
            case 5:
                BlockPosTracker blockpostracker = (BlockPosTracker)obj;

                s = getShortDescription(level, blockpostracker.currentBlockPosition());
                break;
            case 6:
                DamageSource damagesource = (DamageSource)obj;
                Entity entity1 = damagesource.getEntity();

                s = entity1 == null ? obj.toString() : getShortDescription(level, entity1);
                break;
            case 7:
                Collection<?> collection = (Collection)obj;

                s = "[" + (String)collection.stream().map((object1) -> {
                    return getShortDescription(level, object1);
                }).collect(Collectors.joining(", ")) + "]";
                break;
            default:
                s = obj.toString();
        }

        return s;
    }

    public boolean hasPoi(BlockPos poiPos) {
        return this.pois.contains(poiPos);
    }

    public boolean hasPotentialPoi(BlockPos poiPos) {
        return this.potentialPois.contains(poiPos);
    }
}
