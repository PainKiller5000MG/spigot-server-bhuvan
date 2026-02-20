package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerAdvancementManager extends SimpleJsonResourceReloadListener<Advancement> {

    private static final Logger LOGGER = LogUtils.getLogger();
    public Map<Identifier, AdvancementHolder> advancements = Map.of();
    private AdvancementTree tree = new AdvancementTree();
    private final HolderLookup.Provider registries;

    public ServerAdvancementManager(HolderLookup.Provider registries) {
        super(registries, Advancement.CODEC, Registries.ADVANCEMENT);
        this.registries = registries;
    }

    protected void apply(Map<Identifier, Advancement> preparations, ResourceManager manager, ProfilerFiller profiler) {
        ImmutableMap.Builder<Identifier, AdvancementHolder> immutablemap_builder = ImmutableMap.builder();

        preparations.forEach((identifier, advancement) -> {
            this.validate(identifier, advancement);
            immutablemap_builder.put(identifier, new AdvancementHolder(identifier, advancement));
        });
        this.advancements = immutablemap_builder.buildOrThrow();
        AdvancementTree advancementtree = new AdvancementTree();

        advancementtree.addAll(this.advancements.values());

        for (AdvancementNode advancementnode : advancementtree.roots()) {
            if (advancementnode.holder().value().display().isPresent()) {
                TreeNodePosition.run(advancementnode);
            }
        }

        this.tree = advancementtree;
    }

    private void validate(Identifier id, Advancement advancement) {
        ProblemReporter.Collector problemreporter_collector = new ProblemReporter.Collector();

        advancement.validate(problemreporter_collector, this.registries);
        if (!problemreporter_collector.isEmpty()) {
            ServerAdvancementManager.LOGGER.warn("Found validation problems in advancement {}: \n{}", id, problemreporter_collector.getReport());
        }

    }

    public @Nullable AdvancementHolder get(Identifier id) {
        return (AdvancementHolder) this.advancements.get(id);
    }

    public AdvancementTree tree() {
        return this.tree;
    }

    public Collection<AdvancementHolder> getAllAdvancements() {
        return this.advancements.values();
    }
}
