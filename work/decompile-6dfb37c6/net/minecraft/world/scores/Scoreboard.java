package net.minecraft.world.scores;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Scoreboard {

    public static final String HIDDEN_SCORE_PREFIX = "#";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Object2ObjectMap<String, Objective> objectivesByName = new Object2ObjectOpenHashMap(16, 0.5F);
    private final Reference2ObjectMap<ObjectiveCriteria, List<Objective>> objectivesByCriteria = new Reference2ObjectOpenHashMap();
    private final Map<String, PlayerScores> playerScores = new Object2ObjectOpenHashMap(16, 0.5F);
    private final Map<DisplaySlot, Objective> displayObjectives = new EnumMap(DisplaySlot.class);
    private final Object2ObjectMap<String, PlayerTeam> teamsByName = new Object2ObjectOpenHashMap();
    private final Object2ObjectMap<String, PlayerTeam> teamsByPlayer = new Object2ObjectOpenHashMap();

    public Scoreboard() {}

    public @Nullable Objective getObjective(@Nullable String name) {
        return (Objective) this.objectivesByName.get(name);
    }

    public Objective addObjective(String name, ObjectiveCriteria criteria, Component displayName, ObjectiveCriteria.RenderType renderType, boolean displayAutoUpdate, @Nullable NumberFormat numberFormat) {
        if (this.objectivesByName.containsKey(name)) {
            throw new IllegalArgumentException("An objective with the name '" + name + "' already exists!");
        } else {
            Objective objective = new Objective(this, name, criteria, displayName, renderType, displayAutoUpdate, numberFormat);

            ((List) this.objectivesByCriteria.computeIfAbsent(criteria, (object) -> {
                return Lists.newArrayList();
            })).add(objective);
            this.objectivesByName.put(name, objective);
            this.onObjectiveAdded(objective);
            return objective;
        }
    }

    public final void forAllObjectives(ObjectiveCriteria criteria, ScoreHolder name, Consumer<ScoreAccess> operation) {
        ((List) this.objectivesByCriteria.getOrDefault(criteria, Collections.emptyList())).forEach((objective) -> {
            operation.accept(this.getOrCreatePlayerScore(name, objective, true));
        });
    }

    private PlayerScores getOrCreatePlayerInfo(String name) {
        return (PlayerScores) this.playerScores.computeIfAbsent(name, (s1) -> {
            return new PlayerScores();
        });
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder holder, Objective objective) {
        return this.getOrCreatePlayerScore(holder, objective, false);
    }

    public ScoreAccess getOrCreatePlayerScore(final ScoreHolder scoreHolder, final Objective objective, boolean forceWritable) {
        final boolean flag1 = forceWritable || !objective.getCriteria().isReadOnly();
        PlayerScores playerscores = this.getOrCreatePlayerInfo(scoreHolder.getScoreboardName());
        final MutableBoolean mutableboolean = new MutableBoolean();
        final Score score = playerscores.getOrCreate(objective, (score1) -> {
            mutableboolean.setTrue();
        });

        return new ScoreAccess() {
            @Override
            public int get() {
                return score.value();
            }

            @Override
            public void set(int value) {
                if (!flag1) {
                    throw new IllegalStateException("Cannot modify read-only score");
                } else {
                    boolean flag2 = mutableboolean.isTrue();

                    if (objective.displayAutoUpdate()) {
                        Component component = scoreHolder.getDisplayName();

                        if (component != null && !component.equals(score.display())) {
                            score.display(component);
                            flag2 = true;
                        }
                    }

                    if (value != score.value()) {
                        score.value(value);
                        flag2 = true;
                    }

                    if (flag2) {
                        this.sendScoreToPlayers();
                    }

                }
            }

            @Override
            public @Nullable Component display() {
                return score.display();
            }

            @Override
            public void display(@Nullable Component display) {
                if (mutableboolean.isTrue() || !Objects.equals(display, score.display())) {
                    score.display(display);
                    this.sendScoreToPlayers();
                }

            }

            @Override
            public void numberFormatOverride(@Nullable NumberFormat numberFormat) {
                score.numberFormat(numberFormat);
                this.sendScoreToPlayers();
            }

            @Override
            public boolean locked() {
                return score.isLocked();
            }

            @Override
            public void unlock() {
                this.setLocked(false);
            }

            @Override
            public void lock() {
                this.setLocked(true);
            }

            private void setLocked(boolean locked) {
                score.setLocked(locked);
                if (mutableboolean.isTrue()) {
                    this.sendScoreToPlayers();
                }

                Scoreboard.this.onScoreLockChanged(scoreHolder, objective);
            }

            private void sendScoreToPlayers() {
                Scoreboard.this.onScoreChanged(scoreHolder, objective, score);
                mutableboolean.setFalse();
            }
        };
    }

    public @Nullable ReadOnlyScoreInfo getPlayerScoreInfo(ScoreHolder name, Objective objective) {
        PlayerScores playerscores = (PlayerScores) this.playerScores.get(name.getScoreboardName());

        return playerscores != null ? playerscores.get(objective) : null;
    }

    public Collection<PlayerScoreEntry> listPlayerScores(Objective objective) {
        List<PlayerScoreEntry> list = new ArrayList();

        this.playerScores.forEach((s, playerscores) -> {
            Score score = playerscores.get(objective);

            if (score != null) {
                list.add(new PlayerScoreEntry(s, score.value(), score.display(), score.numberFormat()));
            }

        });
        return list;
    }

    public Collection<Objective> getObjectives() {
        return this.objectivesByName.values();
    }

    public Collection<String> getObjectiveNames() {
        return this.objectivesByName.keySet();
    }

    public Collection<ScoreHolder> getTrackedPlayers() {
        return this.playerScores.keySet().stream().map(ScoreHolder::forNameOnly).toList();
    }

    public void resetAllPlayerScores(ScoreHolder player) {
        PlayerScores playerscores = (PlayerScores) this.playerScores.remove(player.getScoreboardName());

        if (playerscores != null) {
            this.onPlayerRemoved(player);
        }

    }

    public void resetSinglePlayerScore(ScoreHolder player, Objective objective) {
        PlayerScores playerscores = (PlayerScores) this.playerScores.get(player.getScoreboardName());

        if (playerscores != null) {
            boolean flag = playerscores.remove(objective);

            if (!playerscores.hasScores()) {
                PlayerScores playerscores1 = (PlayerScores) this.playerScores.remove(player.getScoreboardName());

                if (playerscores1 != null) {
                    this.onPlayerRemoved(player);
                }
            } else if (flag) {
                this.onPlayerScoreRemoved(player, objective);
            }
        }

    }

    public Object2IntMap<Objective> listPlayerScores(ScoreHolder player) {
        PlayerScores playerscores = (PlayerScores) this.playerScores.get(player.getScoreboardName());

        return playerscores != null ? playerscores.listScores() : Object2IntMaps.emptyMap();
    }

    public void removeObjective(Objective objective) {
        this.objectivesByName.remove(objective.getName());

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == objective) {
                this.setDisplayObjective(displayslot, (Objective) null);
            }
        }

        List<Objective> list = (List) this.objectivesByCriteria.get(objective.getCriteria());

        if (list != null) {
            list.remove(objective);
        }

        for (PlayerScores playerscores : this.playerScores.values()) {
            playerscores.remove(objective);
        }

        this.onObjectiveRemoved(objective);
    }

    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        this.displayObjectives.put(slot, objective);
    }

    public @Nullable Objective getDisplayObjective(DisplaySlot slot) {
        return (Objective) this.displayObjectives.get(slot);
    }

    public @Nullable PlayerTeam getPlayerTeam(String name) {
        return (PlayerTeam) this.teamsByName.get(name);
    }

    public PlayerTeam addPlayerTeam(String name) {
        PlayerTeam playerteam = this.getPlayerTeam(name);

        if (playerteam != null) {
            Scoreboard.LOGGER.warn("Requested creation of existing team '{}'", name);
            return playerteam;
        } else {
            playerteam = new PlayerTeam(this, name);
            this.teamsByName.put(name, playerteam);
            this.onTeamAdded(playerteam);
            return playerteam;
        }
    }

    public void removePlayerTeam(PlayerTeam team) {
        this.teamsByName.remove(team.getName());

        for (String s : team.getPlayers()) {
            this.teamsByPlayer.remove(s);
        }

        this.onTeamRemoved(team);
    }

    public boolean addPlayerToTeam(String player, PlayerTeam team) {
        if (this.getPlayersTeam(player) != null) {
            this.removePlayerFromTeam(player);
        }

        this.teamsByPlayer.put(player, team);
        return team.getPlayers().add(player);
    }

    public boolean removePlayerFromTeam(String player) {
        PlayerTeam playerteam = this.getPlayersTeam(player);

        if (playerteam != null) {
            this.removePlayerFromTeam(player, playerteam);
            return true;
        } else {
            return false;
        }
    }

    public void removePlayerFromTeam(String player, PlayerTeam team) {
        if (this.getPlayersTeam(player) != team) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + team.getName() + "'.");
        } else {
            this.teamsByPlayer.remove(player);
            team.getPlayers().remove(player);
        }
    }

    public Collection<String> getTeamNames() {
        return this.teamsByName.keySet();
    }

    public Collection<PlayerTeam> getPlayerTeams() {
        return this.teamsByName.values();
    }

    public @Nullable PlayerTeam getPlayersTeam(String name) {
        return (PlayerTeam) this.teamsByPlayer.get(name);
    }

    public void onObjectiveAdded(Objective objective) {}

    public void onObjectiveChanged(Objective objective) {}

    public void onObjectiveRemoved(Objective objective) {}

    protected void onScoreChanged(ScoreHolder owner, Objective objective, Score score) {}

    protected void onScoreLockChanged(ScoreHolder owner, Objective objective) {}

    public void onPlayerRemoved(ScoreHolder player) {}

    public void onPlayerScoreRemoved(ScoreHolder player, Objective objective) {}

    public void onTeamAdded(PlayerTeam team) {}

    public void onTeamChanged(PlayerTeam team) {}

    public void onTeamRemoved(PlayerTeam team) {}

    public void entityRemoved(Entity entity) {
        if (!(entity instanceof Player) && !entity.isAlive()) {
            this.resetAllPlayerScores(entity);
            this.removePlayerFromTeam(entity.getScoreboardName());
        }
    }

    protected List<Scoreboard.PackedScore> packPlayerScores() {
        return this.playerScores.entrySet().stream().flatMap((entry) -> {
            String s = (String) entry.getKey();

            return ((PlayerScores) entry.getValue()).listRawScores().entrySet().stream().map((entry1) -> {
                return new Scoreboard.PackedScore(s, ((Objective) entry1.getKey()).getName(), ((Score) entry1.getValue()).pack());
            });
        }).toList();
    }

    protected void loadPlayerScore(Scoreboard.PackedScore score) {
        Objective objective = this.getObjective(score.objective);

        if (objective == null) {
            Scoreboard.LOGGER.error("Unknown objective {} for name {}, ignoring", score.objective, score.owner);
        } else {
            this.getOrCreatePlayerInfo(score.owner).setScore(objective, new Score(score.score));
        }
    }

    protected List<PlayerTeam.Packed> packPlayerTeams() {
        return this.getPlayerTeams().stream().map(PlayerTeam::pack).toList();
    }

    protected void loadPlayerTeam(PlayerTeam.Packed packed) {
        PlayerTeam playerteam = this.addPlayerTeam(packed.name());
        Optional optional = packed.displayName();

        Objects.requireNonNull(playerteam);
        optional.ifPresent(playerteam::setDisplayName);
        optional = packed.color();
        Objects.requireNonNull(playerteam);
        optional.ifPresent(playerteam::setColor);
        playerteam.setAllowFriendlyFire(packed.allowFriendlyFire());
        playerteam.setSeeFriendlyInvisibles(packed.seeFriendlyInvisibles());
        playerteam.setPlayerPrefix(packed.memberNamePrefix());
        playerteam.setPlayerSuffix(packed.memberNameSuffix());
        playerteam.setNameTagVisibility(packed.nameTagVisibility());
        playerteam.setDeathMessageVisibility(packed.deathMessageVisibility());
        playerteam.setCollisionRule(packed.collisionRule());

        for (String s : packed.players()) {
            this.addPlayerToTeam(s, playerteam);
        }

    }

    protected List<Objective.Packed> packObjectives() {
        return this.getObjectives().stream().map(Objective::pack).toList();
    }

    protected void loadObjective(Objective.Packed objective) {
        this.addObjective(objective.name(), objective.criteria(), objective.displayName(), objective.renderType(), objective.displayAutoUpdate(), (NumberFormat) objective.numberFormat().orElse((Object) null));
    }

    protected Map<DisplaySlot, String> packDisplaySlots() {
        Map<DisplaySlot, String> map = new EnumMap(DisplaySlot.class);

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            Objective objective = this.getDisplayObjective(displayslot);

            if (objective != null) {
                map.put(displayslot, objective.getName());
            }
        }

        return map;
    }

    public static record PackedScore(String owner, String objective, Score.Packed score) {

        public static final Codec<Scoreboard.PackedScore> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.STRING.fieldOf("Name").forGetter(Scoreboard.PackedScore::owner), Codec.STRING.fieldOf("Objective").forGetter(Scoreboard.PackedScore::objective), Score.Packed.MAP_CODEC.forGetter(Scoreboard.PackedScore::score)).apply(instance, Scoreboard.PackedScore::new);
        });
    }
}
