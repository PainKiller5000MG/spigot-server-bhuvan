package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TradeWithVillager extends Behavior<Villager> {

    private Set<Item> trades = ImmutableSet.of();

    public TradeWithVillager() {
        super(ImmutableMap.of(MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return BehaviorUtils.targetIsValid(body.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return this.checkExtraStartConditions(level, body);
    }

    protected void start(ServerLevel level, Villager myBody, long timestamp) {
        Villager villager1 = (Villager) myBody.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();

        BehaviorUtils.lockGazeAndWalkToEachOther(myBody, villager1, 0.5F, 2);
        this.trades = figureOutWhatIAmWillingToTrade(myBody, villager1);
    }

    protected void tick(ServerLevel level, Villager body, long timestamp) {
        Villager villager1 = (Villager) body.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();

        if (body.distanceToSqr((Entity) villager1) <= 5.0D) {
            BehaviorUtils.lockGazeAndWalkToEachOther(body, villager1, 0.5F, 2);
            body.gossip(level, villager1, timestamp);
            boolean flag = body.getVillagerData().profession().is(VillagerProfession.FARMER);

            if (body.hasExcessFood() && (flag || villager1.wantsMoreFood())) {
                throwHalfStack(body, Villager.FOOD_POINTS.keySet(), villager1);
            }

            if (flag && body.getInventory().countItem(Items.WHEAT) > Items.WHEAT.getDefaultMaxStackSize() / 2) {
                throwHalfStack(body, ImmutableSet.of(Items.WHEAT), villager1);
            }

            if (!this.trades.isEmpty() && body.getInventory().hasAnyOf(this.trades)) {
                throwHalfStack(body, this.trades, villager1);
            }

        }
    }

    protected void stop(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    private static Set<Item> figureOutWhatIAmWillingToTrade(Villager myBody, Villager target) {
        ImmutableSet<Item> immutableset = ((VillagerProfession) target.getVillagerData().profession().value()).requestedItems();
        ImmutableSet<Item> immutableset1 = ((VillagerProfession) myBody.getVillagerData().profession().value()).requestedItems();

        return (Set) immutableset.stream().filter((item) -> {
            return !immutableset1.contains(item);
        }).collect(Collectors.toSet());
    }

    private static void throwHalfStack(Villager villager, Set<Item> items, LivingEntity target) {
        SimpleContainer simplecontainer = villager.getInventory();
        ItemStack itemstack = ItemStack.EMPTY;
        int i = 0;

        while (i < simplecontainer.getContainerSize()) {
            ItemStack itemstack1;
            Item item;
            int j;
            label28:
            {
                itemstack1 = simplecontainer.getItem(i);
                if (!itemstack1.isEmpty()) {
                    item = itemstack1.getItem();
                    if (items.contains(item)) {
                        if (itemstack1.getCount() > itemstack1.getMaxStackSize() / 2) {
                            j = itemstack1.getCount() / 2;
                            break label28;
                        }

                        if (itemstack1.getCount() > 24) {
                            j = itemstack1.getCount() - 24;
                            break label28;
                        }
                    }
                }

                ++i;
                continue;
            }

            itemstack1.shrink(j);
            itemstack = new ItemStack(item, j);
            break;
        }

        if (!itemstack.isEmpty()) {
            BehaviorUtils.throwItem(villager, itemstack, target.position());
        }

    }
}
