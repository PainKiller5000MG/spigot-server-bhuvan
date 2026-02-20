package net.minecraft.server.waypoints;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.collect.UnmodifiableIterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class ServerWaypointManager implements WaypointManager<WaypointTransmitter> {

    private final Set<WaypointTransmitter> waypoints = new HashSet();
    private final Set<ServerPlayer> players = new HashSet();
    private final Table<ServerPlayer, WaypointTransmitter, WaypointTransmitter.Connection> connections = HashBasedTable.create();

    public ServerWaypointManager() {}

    public void trackWaypoint(WaypointTransmitter waypoint) {
        this.waypoints.add(waypoint);

        for (ServerPlayer serverplayer : this.players) {
            this.createConnection(serverplayer, waypoint);
        }

    }

    public void updateWaypoint(WaypointTransmitter waypoint) {
        if (this.waypoints.contains(waypoint)) {
            Map<ServerPlayer, WaypointTransmitter.Connection> map = Tables.transpose(this.connections).row(waypoint);
            Sets.SetView<ServerPlayer> sets_setview = Sets.difference(this.players, map.keySet());
            UnmodifiableIterator unmodifiableiterator = ImmutableSet.copyOf(map.entrySet()).iterator();

            while (unmodifiableiterator.hasNext()) {
                Map.Entry<ServerPlayer, WaypointTransmitter.Connection> map_entry = (Entry) unmodifiableiterator.next();

                this.updateConnection((ServerPlayer) map_entry.getKey(), waypoint, (WaypointTransmitter.Connection) map_entry.getValue());
            }

            unmodifiableiterator = sets_setview.iterator();

            while (unmodifiableiterator.hasNext()) {
                ServerPlayer serverplayer = (ServerPlayer) unmodifiableiterator.next();

                this.createConnection(serverplayer, waypoint);
            }

        }
    }

    public void untrackWaypoint(WaypointTransmitter waypoint) {
        this.connections.column(waypoint).forEach((serverplayer, waypointtransmitter_connection) -> {
            waypointtransmitter_connection.disconnect();
        });
        Tables.transpose(this.connections).row(waypoint).clear();
        this.waypoints.remove(waypoint);
    }

    public void addPlayer(ServerPlayer player) {
        this.players.add(player);

        for (WaypointTransmitter waypointtransmitter : this.waypoints) {
            this.createConnection(player, waypointtransmitter);
        }

        if (player.isTransmittingWaypoint()) {
            this.trackWaypoint((WaypointTransmitter) player);
        }

    }

    public void updatePlayer(ServerPlayer player) {
        Map<WaypointTransmitter, WaypointTransmitter.Connection> map = this.connections.row(player);
        Sets.SetView<WaypointTransmitter> sets_setview = Sets.difference(this.waypoints, map.keySet());
        UnmodifiableIterator unmodifiableiterator = ImmutableSet.copyOf(map.entrySet()).iterator();

        while (unmodifiableiterator.hasNext()) {
            Map.Entry<WaypointTransmitter, WaypointTransmitter.Connection> map_entry = (Entry) unmodifiableiterator.next();

            this.updateConnection(player, (WaypointTransmitter) map_entry.getKey(), (WaypointTransmitter.Connection) map_entry.getValue());
        }

        unmodifiableiterator = sets_setview.iterator();

        while (unmodifiableiterator.hasNext()) {
            WaypointTransmitter waypointtransmitter = (WaypointTransmitter) unmodifiableiterator.next();

            this.createConnection(player, waypointtransmitter);
        }

    }

    public void removePlayer(ServerPlayer player) {
        this.connections.row(player).values().removeIf((waypointtransmitter_connection) -> {
            waypointtransmitter_connection.disconnect();
            return true;
        });
        this.untrackWaypoint((WaypointTransmitter) player);
        this.players.remove(player);
    }

    public void breakAllConnections() {
        this.connections.values().forEach(WaypointTransmitter.Connection::disconnect);
        this.connections.clear();
    }

    public void remakeConnections(WaypointTransmitter waypoint) {
        for (ServerPlayer serverplayer : this.players) {
            this.createConnection(serverplayer, waypoint);
        }

    }

    public Set<WaypointTransmitter> transmitters() {
        return this.waypoints;
    }

    private static boolean isLocatorBarEnabledFor(ServerPlayer player) {
        return (Boolean) player.level().getGameRules().get(GameRules.LOCATOR_BAR);
    }

    private void createConnection(ServerPlayer player, WaypointTransmitter waypoint) {
        if (player != waypoint) {
            if (isLocatorBarEnabledFor(player)) {
                waypoint.makeWaypointConnectionWith(player).ifPresentOrElse((waypointtransmitter_connection) -> {
                    this.connections.put(player, waypoint, waypointtransmitter_connection);
                    waypointtransmitter_connection.connect();
                }, () -> {
                    WaypointTransmitter.Connection waypointtransmitter_connection = (WaypointTransmitter.Connection) this.connections.remove(player, waypoint);

                    if (waypointtransmitter_connection != null) {
                        waypointtransmitter_connection.disconnect();
                    }

                });
            }
        }
    }

    private void updateConnection(ServerPlayer player, WaypointTransmitter waypoint, WaypointTransmitter.Connection connection) {
        if (player != waypoint) {
            if (isLocatorBarEnabledFor(player)) {
                if (!connection.isBroken()) {
                    connection.update();
                } else {
                    waypoint.makeWaypointConnectionWith(player).ifPresentOrElse((waypointtransmitter_connection1) -> {
                        waypointtransmitter_connection1.connect();
                        this.connections.put(player, waypoint, waypointtransmitter_connection1);
                    }, () -> {
                        connection.disconnect();
                        this.connections.remove(player, waypoint);
                    });
                }
            }
        }
    }
}
