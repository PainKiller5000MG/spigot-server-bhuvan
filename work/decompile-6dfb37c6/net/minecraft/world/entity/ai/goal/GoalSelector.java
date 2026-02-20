package net.minecraft.world.entity.ai.goal;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class GoalSelector {

    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override
        public boolean canUse() {
            return false;
        }
    }) {
        @Override
        public boolean isRunning() {
            return false;
        }
    };
    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap(Goal.Flag.class);
    private final Set<WrappedGoal> availableGoals = new ObjectLinkedOpenHashSet();
    private final EnumSet<Goal.Flag> disabledFlags = EnumSet.noneOf(Goal.Flag.class);

    public GoalSelector() {}

    public void addGoal(int prio, Goal goal) {
        this.availableGoals.add(new WrappedGoal(prio, goal));
    }

    public void removeAllGoals(Predicate<Goal> predicate) {
        this.availableGoals.removeIf((wrappedgoal) -> {
            return predicate.test(wrappedgoal.getGoal());
        });
    }

    public void removeGoal(Goal toRemove) {
        for (WrappedGoal wrappedgoal : this.availableGoals) {
            if (wrappedgoal.getGoal() == toRemove && wrappedgoal.isRunning()) {
                wrappedgoal.stop();
            }
        }

        this.availableGoals.removeIf((wrappedgoal1) -> {
            return wrappedgoal1.getGoal() == toRemove;
        });
    }

    private static boolean goalContainsAnyFlags(WrappedGoal goal, EnumSet<Goal.Flag> disabledFlags) {
        for (Goal.Flag goal_flag : goal.getFlags()) {
            if (disabledFlags.contains(goal_flag)) {
                return true;
            }
        }

        return false;
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> lockedFlags) {
        for (Goal.Flag goal_flag : goal.getFlags()) {
            if (!((WrappedGoal) lockedFlags.getOrDefault(goal_flag, GoalSelector.NO_GOAL)).canBeReplacedBy(goal)) {
                return false;
            }
        }

        return true;
    }

    public void tick() {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("goalCleanup");

        for (WrappedGoal wrappedgoal : this.availableGoals) {
            if (wrappedgoal.isRunning() && (goalContainsAnyFlags(wrappedgoal, this.disabledFlags) || !wrappedgoal.canContinueToUse())) {
                wrappedgoal.stop();
            }
        }

        this.lockedFlags.entrySet().removeIf((entry) -> {
            return !((WrappedGoal) entry.getValue()).isRunning();
        });
        profilerfiller.pop();
        profilerfiller.push("goalUpdate");

        for (WrappedGoal wrappedgoal1 : this.availableGoals) {
            if (!wrappedgoal1.isRunning() && !goalContainsAnyFlags(wrappedgoal1, this.disabledFlags) && goalCanBeReplacedForAllFlags(wrappedgoal1, this.lockedFlags) && wrappedgoal1.canUse()) {
                for (Goal.Flag goal_flag : wrappedgoal1.getFlags()) {
                    WrappedGoal wrappedgoal2 = (WrappedGoal) this.lockedFlags.getOrDefault(goal_flag, GoalSelector.NO_GOAL);

                    wrappedgoal2.stop();
                    this.lockedFlags.put(goal_flag, wrappedgoal1);
                }

                wrappedgoal1.start();
            }
        }

        profilerfiller.pop();
        this.tickRunningGoals(true);
    }

    public void tickRunningGoals(boolean forceTickAllRunningGoals) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("goalTick");

        for (WrappedGoal wrappedgoal : this.availableGoals) {
            if (wrappedgoal.isRunning() && (forceTickAllRunningGoals || wrappedgoal.requiresUpdateEveryTick())) {
                wrappedgoal.tick();
            }
        }

        profilerfiller.pop();
    }

    public Set<WrappedGoal> getAvailableGoals() {
        return this.availableGoals;
    }

    public void disableControlFlag(Goal.Flag flag) {
        this.disabledFlags.add(flag);
    }

    public void enableControlFlag(Goal.Flag flag) {
        this.disabledFlags.remove(flag);
    }

    public void setControlFlag(Goal.Flag flag, boolean enabled) {
        if (enabled) {
            this.enableControlFlag(flag);
        } else {
            this.disableControlFlag(flag);
        }

    }
}
