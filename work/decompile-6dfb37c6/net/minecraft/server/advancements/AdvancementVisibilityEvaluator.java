package net.minecraft.server.advancements;

import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;

public class AdvancementVisibilityEvaluator {

    private static final int VISIBILITY_DEPTH = 2;

    public AdvancementVisibilityEvaluator() {}

    private static AdvancementVisibilityEvaluator.VisibilityRule evaluateVisibilityRule(Advancement advancement, boolean isDone) {
        Optional<DisplayInfo> optional = advancement.display();

        return optional.isEmpty() ? AdvancementVisibilityEvaluator.VisibilityRule.HIDE : (isDone ? AdvancementVisibilityEvaluator.VisibilityRule.SHOW : (((DisplayInfo) optional.get()).isHidden() ? AdvancementVisibilityEvaluator.VisibilityRule.HIDE : AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE));
    }

    private static boolean evaluateVisiblityForUnfinishedNode(Stack<AdvancementVisibilityEvaluator.VisibilityRule> ascendants) {
        for (int i = 0; i <= 2; ++i) {
            AdvancementVisibilityEvaluator.VisibilityRule advancementvisibilityevaluator_visibilityrule = (AdvancementVisibilityEvaluator.VisibilityRule) ascendants.peek(i);

            if (advancementvisibilityevaluator_visibilityrule == AdvancementVisibilityEvaluator.VisibilityRule.SHOW) {
                return true;
            }

            if (advancementvisibilityevaluator_visibilityrule == AdvancementVisibilityEvaluator.VisibilityRule.HIDE) {
                return false;
            }
        }

        return false;
    }

    private static boolean evaluateVisibility(AdvancementNode node, Stack<AdvancementVisibilityEvaluator.VisibilityRule> ascendants, Predicate<AdvancementNode> isDoneTest, AdvancementVisibilityEvaluator.Output output) {
        boolean flag = isDoneTest.test(node);
        AdvancementVisibilityEvaluator.VisibilityRule advancementvisibilityevaluator_visibilityrule = evaluateVisibilityRule(node.advancement(), flag);
        boolean flag1 = flag;

        ascendants.push(advancementvisibilityevaluator_visibilityrule);

        for (AdvancementNode advancementnode1 : node.children()) {
            flag1 |= evaluateVisibility(advancementnode1, ascendants, isDoneTest, output);
        }

        boolean flag2 = flag1 || evaluateVisiblityForUnfinishedNode(ascendants);

        ascendants.pop();
        output.accept(node, flag2);
        return flag1;
    }

    public static void evaluateVisibility(AdvancementNode node, Predicate<AdvancementNode> isDone, AdvancementVisibilityEvaluator.Output output) {
        AdvancementNode advancementnode1 = node.root();
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> stack = new ObjectArrayList();

        for (int i = 0; i <= 2; ++i) {
            stack.push(AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE);
        }

        evaluateVisibility(advancementnode1, stack, isDone, output);
    }

    private static enum VisibilityRule {

        SHOW, HIDE, NO_CHANGE;

        private VisibilityRule() {}
    }

    @FunctionalInterface
    public interface Output {

        void accept(AdvancementNode advancement, boolean visible);
    }
}
