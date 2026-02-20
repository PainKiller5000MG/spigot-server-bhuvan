package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class DefendVillageTargetGoal extends TargetGoal {

    private final IronGolem golem;
    private @Nullable LivingEntity potentialTarget;
    private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0D);

    public DefendVillageTargetGoal(IronGolem golem) {
        super(golem, false, true);
        this.golem = golem;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        AABB aabb = this.golem.getBoundingBox().inflate(10.0D, 8.0D, 10.0D);
        ServerLevel serverlevel = getServerLevel((Entity) this.golem);
        List<? extends LivingEntity> list = serverlevel.<LivingEntity>getNearbyEntities(Villager.class, this.attackTargeting, this.golem, aabb);
        List<Player> list1 = serverlevel.getNearbyPlayers(this.attackTargeting, this.golem, aabb);

        for (LivingEntity livingentity : list) {
            Villager villager = (Villager) livingentity;

            for (Player player : list1) {
                int i = villager.getPlayerReputation(player);

                if (i <= -100) {
                    this.potentialTarget = player;
                }
            }
        }

        if (this.potentialTarget == null) {
            return false;
        } else {
            LivingEntity livingentity1 = this.potentialTarget;

            if (livingentity1 instanceof Player) {
                Player player1 = (Player) livingentity1;

                if (player1.isSpectator() || player1.isCreative()) {
                    return false;
                }
            }

            return true;
        }
    }

    @Override
    public void start() {
        this.golem.setTarget(this.potentialTarget);
        super.start();
    }
}
