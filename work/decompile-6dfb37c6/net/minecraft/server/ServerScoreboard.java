package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.jspecify.annotations.Nullable;

public class ServerScoreboard extends Scoreboard {

    private final MinecraftServer server;
    private final Set<Objective> trackedObjectives = Sets.newHashSet();
    private boolean dirty;

    public ServerScoreboard(MinecraftServer server) {
        this.server = server;
    }

    public void load(ScoreboardSaveData.Packed data) {
        data.objectives().forEach((objective_packed) -> {
            this.loadObjective(objective_packed);
        });
        data.scores().forEach((scoreboard_packedscore) -> {
            this.loadPlayerScore(scoreboard_packedscore);
        });
        data.displaySlots().forEach((displayslot, s) -> {
            Objective objective = this.getObjective(s);

            this.setDisplayObjective(displayslot, objective);
        });
        data.teams().forEach((playerteam_packed) -> {
            this.loadPlayerTeam(playerteam_packed);
        });
    }

    private ScoreboardSaveData.Packed store() {
        return new ScoreboardSaveData.Packed(this.packObjectives(), this.packPlayerScores(), this.packDisplaySlots(), this.packPlayerTeams());
    }

    @Override
    protected void onScoreChanged(ScoreHolder owner, Objective objective, Score score) {
        super.onScoreChanged(owner, objective, score);
        if (this.trackedObjectives.contains(objective)) {
            this.server.getPlayerList().broadcastAll(new ClientboundSetScorePacket(owner.getScoreboardName(), objective.getName(), score.value(), Optional.ofNullable(score.display()), Optional.ofNullable(score.numberFormat())));
        }

        this.setDirty();
    }

    @Override
    protected void onScoreLockChanged(ScoreHolder owner, Objective objective) {
        super.onScoreLockChanged(owner, objective);
        this.setDirty();
    }

    @Override
    public void onPlayerRemoved(ScoreHolder player) {
        super.onPlayerRemoved(player);
        this.server.getPlayerList().broadcastAll(new ClientboundResetScorePacket(player.getScoreboardName(), (String) null));
        this.setDirty();
    }

    @Override
    public void onPlayerScoreRemoved(ScoreHolder player, Objective objective) {
        super.onPlayerScoreRemoved(player, objective);
        if (this.trackedObjectives.contains(objective)) {
            this.server.getPlayerList().broadcastAll(new ClientboundResetScorePacket(player.getScoreboardName(), objective.getName()));
        }

        this.setDirty();
    }

    @Override
    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        Objective objective1 = this.getDisplayObjective(slot);

        super.setDisplayObjective(slot, objective);
        if (objective1 != objective && objective1 != null) {
            if (this.getObjectiveDisplaySlotCount(objective1) > 0) {
                this.server.getPlayerList().broadcastAll(new ClientboundSetDisplayObjectivePacket(slot, objective));
            } else {
                this.stopTrackingObjective(objective1);
            }
        }

        if (objective != null) {
            if (this.trackedObjectives.contains(objective)) {
                this.server.getPlayerList().broadcastAll(new ClientboundSetDisplayObjectivePacket(slot, objective));
            } else {
                this.startTrackingObjective(objective);
            }
        }

        this.setDirty();
    }

    @Override
    public boolean addPlayerToTeam(String player, PlayerTeam team) {
        if (super.addPlayerToTeam(player, team)) {
            this.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, player, ClientboundSetPlayerTeamPacket.Action.ADD));
            this.updatePlayerWaypoint(player);
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removePlayerFromTeam(String player, PlayerTeam team) {
        super.removePlayerFromTeam(player, team);
        this.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, player, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        this.updatePlayerWaypoint(player);
        this.setDirty();
    }

    @Override
    public void onObjectiveAdded(Objective objective) {
        super.onObjectiveAdded(objective);
        this.setDirty();
    }

    @Override
    public void onObjectiveChanged(Objective objective) {
        super.onObjectiveChanged(objective);
        if (this.trackedObjectives.contains(objective)) {
            this.server.getPlayerList().broadcastAll(new ClientboundSetObjectivePacket(objective, 2));
        }

        this.setDirty();
    }

    @Override
    public void onObjectiveRemoved(Objective objective) {
        super.onObjectiveRemoved(objective);
        if (this.trackedObjectives.contains(objective)) {
            this.stopTrackingObjective(objective);
        }

        this.setDirty();
    }

    @Override
    public void onTeamAdded(PlayerTeam team) {
        super.onTeamAdded(team);
        this.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        this.setDirty();
    }

    @Override
    public void onTeamChanged(PlayerTeam team) {
        super.onTeamChanged(team);
        this.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
        this.updateTeamWaypoints(team);
        this.setDirty();
    }

    @Override
    public void onTeamRemoved(PlayerTeam team) {
        super.onTeamRemoved(team);
        this.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        this.updateTeamWaypoints(team);
        this.setDirty();
    }

    protected void setDirty() {
        this.dirty = true;
    }

    public void storeToSaveDataIfDirty(ScoreboardSaveData saveData) {
        if (this.dirty) {
            this.dirty = false;
            saveData.setData(this.store());
        }

    }

    public List<Packet<?>> getStartTrackingPackets(Objective objective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new ClientboundSetObjectivePacket(objective, 0));

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == objective) {
                list.add(new ClientboundSetDisplayObjectivePacket(displayslot, objective));
            }
        }

        for (PlayerScoreEntry playerscoreentry : this.listPlayerScores(objective)) {
            list.add(new ClientboundSetScorePacket(playerscoreentry.owner(), objective.getName(), playerscoreentry.value(), Optional.ofNullable(playerscoreentry.display()), Optional.ofNullable(playerscoreentry.numberFormatOverride())));
        }

        return list;
    }

    public void startTrackingObjective(Objective objective) {
        List<Packet<?>> list = this.getStartTrackingPackets(objective);

        for (ServerPlayer serverplayer : this.server.getPlayerList().getPlayers()) {
            for (Packet<?> packet : list) {
                serverplayer.connection.send(packet);
            }
        }

        this.trackedObjectives.add(objective);
    }

    public List<Packet<?>> getStopTrackingPackets(Objective objective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new ClientboundSetObjectivePacket(objective, 1));

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == objective) {
                list.add(new ClientboundSetDisplayObjectivePacket(displayslot, objective));
            }
        }

        return list;
    }

    public void stopTrackingObjective(Objective objective) {
        List<Packet<?>> list = this.getStopTrackingPackets(objective);

        for (ServerPlayer serverplayer : this.server.getPlayerList().getPlayers()) {
            for (Packet<?> packet : list) {
                serverplayer.connection.send(packet);
            }
        }

        this.trackedObjectives.remove(objective);
    }

    public int getObjectiveDisplaySlotCount(Objective objective) {
        int i = 0;

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == objective) {
                ++i;
            }
        }

        return i;
    }

    private void updatePlayerWaypoint(String player) {
        ServerPlayer serverplayer = this.server.getPlayerList().getPlayerByName(player);

        if (serverplayer != null) {
            serverplayer.level().getWaypointManager().remakeConnections(serverplayer);
        }

    }

    private void updateTeamWaypoints(PlayerTeam team) {
        for (ServerLevel serverlevel : this.server.getAllLevels()) {
            team.getPlayers().stream().map((s) -> {
                return this.server.getPlayerList().getPlayerByName(s);
            }).filter(Objects::nonNull).forEach((serverplayer) -> {
                serverlevel.getWaypointManager().remakeConnections(serverplayer);
            });
        }

    }
}
