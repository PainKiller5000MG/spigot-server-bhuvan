package net.minecraft.advancements;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class AdvancementNode {

    private final AdvancementHolder holder;
    private final @Nullable AdvancementNode parent;
    private final Set<AdvancementNode> children = new ReferenceOpenHashSet();

    @VisibleForTesting
    public AdvancementNode(AdvancementHolder holder, @Nullable AdvancementNode parent) {
        this.holder = holder;
        this.parent = parent;
    }

    public Advancement advancement() {
        return this.holder.value();
    }

    public AdvancementHolder holder() {
        return this.holder;
    }

    public @Nullable AdvancementNode parent() {
        return this.parent;
    }

    public AdvancementNode root() {
        return getRoot(this);
    }

    public static AdvancementNode getRoot(AdvancementNode advancement) {
        AdvancementNode advancementnode1 = advancement;

        while (true) {
            AdvancementNode advancementnode2 = advancementnode1.parent();

            if (advancementnode2 == null) {
                return advancementnode1;
            }

            advancementnode1 = advancementnode2;
        }
    }

    public Iterable<AdvancementNode> children() {
        return this.children;
    }

    @VisibleForTesting
    public void addChild(AdvancementNode child) {
        this.children.add(child);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof AdvancementNode) {
                AdvancementNode advancementnode = (AdvancementNode) obj;

                if (this.holder.equals(advancementnode.holder)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.holder.hashCode();
    }

    public String toString() {
        return this.holder.id().toString();
    }
}
