package net.minecraft.advancements;

import com.google.common.collect.Lists;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class TreeNodePosition {

    private final AdvancementNode node;
    private final @Nullable TreeNodePosition parent;
    private final @Nullable TreeNodePosition previousSibling;
    private final int childIndex;
    private final List<TreeNodePosition> children = Lists.newArrayList();
    private TreeNodePosition ancestor;
    private @Nullable TreeNodePosition thread;
    private int x;
    private float y;
    private float mod;
    private float change;
    private float shift;

    public TreeNodePosition(AdvancementNode node, @Nullable TreeNodePosition parent, @Nullable TreeNodePosition previousSibling, int childIndex, int depth) {
        if (node.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position an invisible advancement!");
        } else {
            this.node = node;
            this.parent = parent;
            this.previousSibling = previousSibling;
            this.childIndex = childIndex;
            this.ancestor = this;
            this.x = depth;
            this.y = -1.0F;
            TreeNodePosition treenodeposition2 = null;

            for (AdvancementNode advancementnode1 : node.children()) {
                treenodeposition2 = this.addChild(advancementnode1, treenodeposition2);
            }

        }
    }

    private @Nullable TreeNodePosition addChild(AdvancementNode node, @Nullable TreeNodePosition previous) {
        if (node.advancement().display().isPresent()) {
            previous = new TreeNodePosition(node, this, previous, this.children.size() + 1, this.x + 1);
            this.children.add(previous);
        } else {
            for (AdvancementNode advancementnode1 : node.children()) {
                previous = this.addChild(advancementnode1, previous);
            }
        }

        return previous;
    }

    private void firstWalk() {
        if (this.children.isEmpty()) {
            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0F;
            } else {
                this.y = 0.0F;
            }

        } else {
            TreeNodePosition treenodeposition = null;

            for (TreeNodePosition treenodeposition1 : this.children) {
                treenodeposition1.firstWalk();
                treenodeposition = treenodeposition1.apportion(treenodeposition == null ? treenodeposition1 : treenodeposition);
            }

            this.executeShifts();
            float f = (((TreeNodePosition) this.children.get(0)).y + ((TreeNodePosition) this.children.get(this.children.size() - 1)).y) / 2.0F;

            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0F;
                this.mod = this.y - f;
            } else {
                this.y = f;
            }

        }
    }

    private float secondWalk(float modSum, int depth, float min) {
        this.y += modSum;
        this.x = depth;
        if (this.y < min) {
            min = this.y;
        }

        for (TreeNodePosition treenodeposition : this.children) {
            min = treenodeposition.secondWalk(modSum + this.mod, depth + 1, min);
        }

        return min;
    }

    private void thirdWalk(float offset) {
        this.y += offset;

        for (TreeNodePosition treenodeposition : this.children) {
            treenodeposition.thirdWalk(offset);
        }

    }

    private void executeShifts() {
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i = this.children.size() - 1; i >= 0; --i) {
            TreeNodePosition treenodeposition = (TreeNodePosition) this.children.get(i);

            treenodeposition.y += f;
            treenodeposition.mod += f;
            f1 += treenodeposition.change;
            f += treenodeposition.shift + f1;
        }

    }

    private @Nullable TreeNodePosition previousOrThread() {
        return this.thread != null ? this.thread : (!this.children.isEmpty() ? (TreeNodePosition) this.children.get(0) : null);
    }

    private @Nullable TreeNodePosition nextOrThread() {
        return this.thread != null ? this.thread : (!this.children.isEmpty() ? (TreeNodePosition) this.children.get(this.children.size() - 1) : null);
    }

    private TreeNodePosition apportion(TreeNodePosition defaultAncestor) {
        if (this.previousSibling == null) {
            return defaultAncestor;
        } else {
            TreeNodePosition treenodeposition1 = this;
            TreeNodePosition treenodeposition2 = this;
            TreeNodePosition treenodeposition3 = this.previousSibling;
            TreeNodePosition treenodeposition4 = (TreeNodePosition) this.parent.children.get(0);
            float f = this.mod;
            float f1 = this.mod;
            float f2 = treenodeposition3.mod;

            float f3;

            for (f3 = treenodeposition4.mod; treenodeposition3.nextOrThread() != null && treenodeposition1.previousOrThread() != null; f1 += treenodeposition2.mod) {
                treenodeposition3 = treenodeposition3.nextOrThread();
                treenodeposition1 = treenodeposition1.previousOrThread();
                treenodeposition4 = treenodeposition4.previousOrThread();
                treenodeposition2 = treenodeposition2.nextOrThread();
                treenodeposition2.ancestor = this;
                float f4 = treenodeposition3.y + f2 - (treenodeposition1.y + f) + 1.0F;

                if (f4 > 0.0F) {
                    treenodeposition3.getAncestor(this, defaultAncestor).moveSubtree(this, f4);
                    f += f4;
                    f1 += f4;
                }

                f2 += treenodeposition3.mod;
                f += treenodeposition1.mod;
                f3 += treenodeposition4.mod;
            }

            if (treenodeposition3.nextOrThread() != null && treenodeposition2.nextOrThread() == null) {
                treenodeposition2.thread = treenodeposition3.nextOrThread();
                treenodeposition2.mod += f2 - f1;
            } else {
                if (treenodeposition1.previousOrThread() != null && treenodeposition4.previousOrThread() == null) {
                    treenodeposition4.thread = treenodeposition1.previousOrThread();
                    treenodeposition4.mod += f - f3;
                }

                defaultAncestor = this;
            }

            return defaultAncestor;
        }
    }

    private void moveSubtree(TreeNodePosition right, float shift) {
        float f1 = (float) (right.childIndex - this.childIndex);

        if (f1 != 0.0F) {
            right.change -= shift / f1;
            this.change += shift / f1;
        }

        right.shift += shift;
        right.y += shift;
        right.mod += shift;
    }

    private TreeNodePosition getAncestor(TreeNodePosition other, TreeNodePosition defaultAncestor) {
        return this.ancestor != null && other.parent.children.contains(this.ancestor) ? this.ancestor : defaultAncestor;
    }

    private void finalizePosition() {
        this.node.advancement().display().ifPresent((displayinfo) -> {
            displayinfo.setLocation((float) this.x, this.y);
        });
        if (!this.children.isEmpty()) {
            for (TreeNodePosition treenodeposition : this.children) {
                treenodeposition.finalizePosition();
            }
        }

    }

    public static void run(AdvancementNode node) {
        if (node.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position children of an invisible root!");
        } else {
            TreeNodePosition treenodeposition = new TreeNodePosition(node, (TreeNodePosition) null, (TreeNodePosition) null, 1, 0);

            treenodeposition.firstWalk();
            float f = treenodeposition.secondWalk(0.0F, 0, treenodeposition.y);

            if (f < 0.0F) {
                treenodeposition.thirdWalk(-f);
            }

            treenodeposition.finalizePosition();
        }
    }
}
