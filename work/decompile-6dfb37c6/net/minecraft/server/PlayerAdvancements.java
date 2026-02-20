package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PlayerAdvancements {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final PlayerList playerList;
    private final Path playerSavePath;
    private AdvancementTree tree;
    private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap();
    private final Set<AdvancementHolder> visible = new HashSet();
    private final Set<AdvancementHolder> progressChanged = new HashSet();
    private final Set<AdvancementNode> rootsToUpdate = new HashSet();
    private ServerPlayer player;
    private @Nullable AdvancementHolder lastSelectedTab;
    private boolean isFirstPacket = true;
    private final Codec<PlayerAdvancements.Data> codec;

    public PlayerAdvancements(DataFixer dataFixer, PlayerList playerList, ServerAdvancementManager manager, Path playerSavePath, ServerPlayer player) {
        this.playerList = playerList;
        this.playerSavePath = playerSavePath;
        this.player = player;
        this.tree = manager.tree();
        int i = 1343;

        this.codec = DataFixTypes.ADVANCEMENTS.<PlayerAdvancements.Data>wrapCodec(PlayerAdvancements.Data.CODEC, dataFixer, 1343);
        this.load(manager);
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    public void stopListening() {
        for (CriterionTrigger<?> criteriontrigger : BuiltInRegistries.TRIGGER_TYPES) {
            criteriontrigger.removePlayerListeners(this);
        }

    }

    public void reload(ServerAdvancementManager manager) {
        this.stopListening();
        this.progress.clear();
        this.visible.clear();
        this.rootsToUpdate.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.lastSelectedTab = null;
        this.tree = manager.tree();
        this.load(manager);
    }

    private void registerListeners(ServerAdvancementManager manager) {
        for (AdvancementHolder advancementholder : manager.getAllAdvancements()) {
            this.registerListeners(advancementholder);
        }

    }

    private void checkForAutomaticTriggers(ServerAdvancementManager manager) {
        for (AdvancementHolder advancementholder : manager.getAllAdvancements()) {
            Advancement advancement = advancementholder.value();

            if (advancement.criteria().isEmpty()) {
                this.award(advancementholder, "");
                advancement.rewards().grant(this.player);
            }
        }

    }

    private void load(ServerAdvancementManager manager) {
        if (Files.isRegularFile(this.playerSavePath, new LinkOption[0])) {
            try (Reader reader = Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8)) {
                JsonElement jsonelement = StrictJsonParser.parse(reader);
                PlayerAdvancements.Data playeradvancements_data = (PlayerAdvancements.Data) this.codec.parse(JsonOps.INSTANCE, jsonelement).getOrThrow(JsonParseException::new);

                this.applyFrom(manager, playeradvancements_data);
            } catch (JsonIOException | IOException ioexception) {
                PlayerAdvancements.LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, ioexception);
            } catch (JsonParseException jsonparseexception) {
                PlayerAdvancements.LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, jsonparseexception);
            }
        }

        this.checkForAutomaticTriggers(manager);
        this.registerListeners(manager);
    }

    public void save() {
        JsonElement jsonelement = (JsonElement) this.codec.encodeStart(JsonOps.INSTANCE, this.asData()).getOrThrow();

        try {
            FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());

            try (Writer writer = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
                PlayerAdvancements.GSON.toJson(jsonelement, PlayerAdvancements.GSON.newJsonWriter(writer));
            }
        } catch (JsonIOException | IOException ioexception) {
            PlayerAdvancements.LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, ioexception);
        }

    }

    private void applyFrom(ServerAdvancementManager manager, PlayerAdvancements.Data data) {
        data.forEach((identifier, advancementprogress) -> {
            AdvancementHolder advancementholder = manager.get(identifier);

            if (advancementholder == null) {
                PlayerAdvancements.LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", identifier, this.playerSavePath);
            } else {
                this.startProgress(advancementholder, advancementprogress);
                this.progressChanged.add(advancementholder);
                this.markForVisibilityUpdate(advancementholder);
            }
        });
    }

    private PlayerAdvancements.Data asData() {
        Map<Identifier, AdvancementProgress> map = new LinkedHashMap();

        this.progress.forEach((advancementholder, advancementprogress) -> {
            if (advancementprogress.hasProgress()) {
                map.put(advancementholder.id(), advancementprogress);
            }

        });
        return new PlayerAdvancements.Data(map);
    }

    public boolean award(AdvancementHolder holder, String criterion) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(holder);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.grantProgress(criterion)) {
            this.unregisterListeners(holder);
            this.progressChanged.add(holder);
            flag = true;
            if (!flag1 && advancementprogress.isDone()) {
                holder.value().rewards().grant(this.player);
                holder.value().display().ifPresent((displayinfo) -> {
                    if (displayinfo.shouldAnnounceChat() && (Boolean) this.player.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
                        this.playerList.broadcastSystemMessage(displayinfo.getType().createAnnouncement(holder, this.player), false);
                    }

                });
            }
        }

        if (!flag1 && advancementprogress.isDone()) {
            this.markForVisibilityUpdate(holder);
        }

        return flag;
    }

    public boolean revoke(AdvancementHolder advancement, String criterion) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.revokeProgress(criterion)) {
            this.registerListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
        }

        if (flag1 && !advancementprogress.isDone()) {
            this.markForVisibilityUpdate(advancement);
        }

        return flag;
    }

    private void markForVisibilityUpdate(AdvancementHolder advancement) {
        AdvancementNode advancementnode = this.tree.get(advancement);

        if (advancementnode != null) {
            this.rootsToUpdate.add(advancementnode.root());
        }

    }

    private void registerListeners(AdvancementHolder holder) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(holder);

        if (!advancementprogress.isDone()) {
            for (Map.Entry<String, Criterion<?>> map_entry : holder.value().criteria().entrySet()) {
                CriterionProgress criterionprogress = advancementprogress.getCriterion((String) map_entry.getKey());

                if (criterionprogress != null && !criterionprogress.isDone()) {
                    this.registerListener(holder, (String) map_entry.getKey(), (Criterion) map_entry.getValue());
                }
            }

        }
    }

    private <T extends CriterionTriggerInstance> void registerListener(AdvancementHolder holder, String key, Criterion<T> criterion) {
        criterion.trigger().addPlayerListener(this, new CriterionTrigger.Listener(criterion.triggerInstance(), holder, key));
    }

    private void unregisterListeners(AdvancementHolder holder) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(holder);

        for (Map.Entry<String, Criterion<?>> map_entry : holder.value().criteria().entrySet()) {
            CriterionProgress criterionprogress = advancementprogress.getCriterion((String) map_entry.getKey());

            if (criterionprogress != null && (criterionprogress.isDone() || advancementprogress.isDone())) {
                this.removeListener(holder, (String) map_entry.getKey(), (Criterion) map_entry.getValue());
            }
        }

    }

    private <T extends CriterionTriggerInstance> void removeListener(AdvancementHolder holder, String key, Criterion<T> criterion) {
        criterion.trigger().removePlayerListener(this, new CriterionTrigger.Listener(criterion.triggerInstance(), holder, key));
    }

    public void flushDirty(ServerPlayer player, boolean showAdvancements) {
        if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
            Map<Identifier, AdvancementProgress> map = new HashMap();
            Set<AdvancementHolder> set = new HashSet();
            Set<Identifier> set1 = new HashSet();

            for (AdvancementNode advancementnode : this.rootsToUpdate) {
                this.updateTreeVisibility(advancementnode, set, set1);
            }

            this.rootsToUpdate.clear();

            for (AdvancementHolder advancementholder : this.progressChanged) {
                if (this.visible.contains(advancementholder)) {
                    map.put(advancementholder.id(), (AdvancementProgress) this.progress.get(advancementholder));
                }
            }

            this.progressChanged.clear();
            if (!map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
                player.connection.send(new ClientboundUpdateAdvancementsPacket(this.isFirstPacket, set, set1, map, showAdvancements));
            }
        }

        this.isFirstPacket = false;
    }

    public void setSelectedTab(@Nullable AdvancementHolder holder) {
        AdvancementHolder advancementholder1 = this.lastSelectedTab;

        if (holder != null && holder.value().isRoot() && holder.value().display().isPresent()) {
            this.lastSelectedTab = holder;
        } else {
            this.lastSelectedTab = null;
        }

        if (advancementholder1 != this.lastSelectedTab) {
            this.player.connection.send(new ClientboundSelectAdvancementsTabPacket(this.lastSelectedTab == null ? null : this.lastSelectedTab.id()));
        }

    }

    public AdvancementProgress getOrStartProgress(AdvancementHolder advancement) {
        AdvancementProgress advancementprogress = (AdvancementProgress) this.progress.get(advancement);

        if (advancementprogress == null) {
            advancementprogress = new AdvancementProgress();
            this.startProgress(advancement, advancementprogress);
        }

        return advancementprogress;
    }

    private void startProgress(AdvancementHolder holder, AdvancementProgress progress) {
        progress.update(holder.value().requirements());
        this.progress.put(holder, progress);
    }

    private void updateTreeVisibility(AdvancementNode root, Set<AdvancementHolder> added, Set<Identifier> removed) {
        AdvancementVisibilityEvaluator.evaluateVisibility(root, (advancementnode1) -> {
            return this.getOrStartProgress(advancementnode1.holder()).isDone();
        }, (advancementnode1, flag) -> {
            AdvancementHolder advancementholder = advancementnode1.holder();

            if (flag) {
                if (this.visible.add(advancementholder)) {
                    added.add(advancementholder);
                    if (this.progress.containsKey(advancementholder)) {
                        this.progressChanged.add(advancementholder);
                    }
                }
            } else if (this.visible.remove(advancementholder)) {
                removed.add(advancementholder.id());
            }

        });
    }

    private static record Data(Map<Identifier, AdvancementProgress> map) {

        public static final Codec<PlayerAdvancements.Data> CODEC = Codec.unboundedMap(Identifier.CODEC, AdvancementProgress.CODEC).xmap(PlayerAdvancements.Data::new, PlayerAdvancements.Data::map);

        public void forEach(BiConsumer<Identifier, AdvancementProgress> consumer) {
            this.map.entrySet().stream().sorted(Entry.comparingByValue()).forEach((entry) -> {
                consumer.accept((Identifier) entry.getKey(), (AdvancementProgress) entry.getValue());
            });
        }
    }
}
