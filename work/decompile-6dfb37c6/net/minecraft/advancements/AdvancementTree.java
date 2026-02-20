package net.minecraft.advancements;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AdvancementTree {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Identifier, AdvancementNode> nodes = new Object2ObjectOpenHashMap();
    private final Set<AdvancementNode> roots = new ObjectLinkedOpenHashSet();
    private final Set<AdvancementNode> tasks = new ObjectLinkedOpenHashSet();
    private AdvancementTree.@Nullable Listener listener;

    public AdvancementTree() {}

    private void remove(AdvancementNode node) {
        for (AdvancementNode advancementnode1 : node.children()) {
            this.remove(advancementnode1);
        }

        AdvancementTree.LOGGER.info("Forgot about advancement {}", node.holder());
        this.nodes.remove(node.holder().id());
        if (node.parent() == null) {
            this.roots.remove(node);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementRoot(node);
            }
        } else {
            this.tasks.remove(node);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementTask(node);
            }
        }

    }

    public void remove(Set<Identifier> ids) {
        for (Identifier identifier : ids) {
            AdvancementNode advancementnode = (AdvancementNode) this.nodes.get(identifier);

            if (advancementnode == null) {
                AdvancementTree.LOGGER.warn("Told to remove advancement {} but I don't know what that is", identifier);
            } else {
                this.remove(advancementnode);
            }
        }

    }

    public void addAll(Collection<AdvancementHolder> advancements) {
        List<AdvancementHolder> list = new ArrayList(advancements);

        while (!((List) list).isEmpty()) {
            if (!list.removeIf(this::tryInsert)) {
                AdvancementTree.LOGGER.error("Couldn't load advancements: {}", list);
                break;
            }
        }

        AdvancementTree.LOGGER.info("Loaded {} advancements", this.nodes.size());
    }

    private boolean tryInsert(AdvancementHolder holder) {
        Optional<Identifier> optional = holder.value().parent();
        Map map = this.nodes;

        Objects.requireNonNull(this.nodes);
        AdvancementNode advancementnode = (AdvancementNode) optional.map(map::get).orElse((Object) null);

        if (advancementnode == null && optional.isPresent()) {
            return false;
        } else {
            AdvancementNode advancementnode1 = new AdvancementNode(holder, advancementnode);

            if (advancementnode != null) {
                advancementnode.addChild(advancementnode1);
            }

            this.nodes.put(holder.id(), advancementnode1);
            if (advancementnode == null) {
                this.roots.add(advancementnode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementRoot(advancementnode1);
                }
            } else {
                this.tasks.add(advancementnode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementTask(advancementnode1);
                }
            }

            return true;
        }
    }

    public void clear() {
        this.nodes.clear();
        this.roots.clear();
        this.tasks.clear();
        if (this.listener != null) {
            this.listener.onAdvancementsCleared();
        }

    }

    public Iterable<AdvancementNode> roots() {
        return this.roots;
    }

    public Collection<AdvancementNode> nodes() {
        return this.nodes.values();
    }

    public @Nullable AdvancementNode get(Identifier id) {
        return (AdvancementNode) this.nodes.get(id);
    }

    public @Nullable AdvancementNode get(AdvancementHolder advancement) {
        return (AdvancementNode) this.nodes.get(advancement.id());
    }

    public void setListener(AdvancementTree.@Nullable Listener listener) {
        this.listener = listener;
        if (listener != null) {
            for (AdvancementNode advancementnode : this.roots) {
                listener.onAddAdvancementRoot(advancementnode);
            }

            for (AdvancementNode advancementnode1 : this.tasks) {
                listener.onAddAdvancementTask(advancementnode1);
            }
        }

    }

    public interface Listener {

        void onAddAdvancementRoot(AdvancementNode root);

        void onRemoveAdvancementRoot(AdvancementNode root);

        void onAddAdvancementTask(AdvancementNode task);

        void onRemoveAdvancementTask(AdvancementNode task);

        void onAdvancementsCleared();
    }
}
