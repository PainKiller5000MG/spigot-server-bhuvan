package net.minecraft.server.level;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public class ServerBossEvent extends BossEvent {

    private final Set<ServerPlayer> players = Sets.newHashSet();
    private final Set<ServerPlayer> unmodifiablePlayers;
    public boolean visible;

    public ServerBossEvent(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        super(Mth.createInsecureUUID(), name, color, overlay);
        this.unmodifiablePlayers = Collections.unmodifiableSet(this.players);
        this.visible = true;
    }

    @Override
    public void setProgress(float progress) {
        if (progress != this.progress) {
            super.setProgress(progress);
            this.broadcast(ClientboundBossEventPacket::createUpdateProgressPacket);
        }

    }

    @Override
    public void setColor(BossEvent.BossBarColor color) {
        if (color != this.color) {
            super.setColor(color);
            this.broadcast(ClientboundBossEventPacket::createUpdateStylePacket);
        }

    }

    @Override
    public void setOverlay(BossEvent.BossBarOverlay overlay) {
        if (overlay != this.overlay) {
            super.setOverlay(overlay);
            this.broadcast(ClientboundBossEventPacket::createUpdateStylePacket);
        }

    }

    @Override
    public BossEvent setDarkenScreen(boolean darkenScreen) {
        if (darkenScreen != this.darkenScreen) {
            super.setDarkenScreen(darkenScreen);
            this.broadcast(ClientboundBossEventPacket::createUpdatePropertiesPacket);
        }

        return this;
    }

    @Override
    public BossEvent setPlayBossMusic(boolean playBossMusic) {
        if (playBossMusic != this.playBossMusic) {
            super.setPlayBossMusic(playBossMusic);
            this.broadcast(ClientboundBossEventPacket::createUpdatePropertiesPacket);
        }

        return this;
    }

    @Override
    public BossEvent setCreateWorldFog(boolean createWorldFog) {
        if (createWorldFog != this.createWorldFog) {
            super.setCreateWorldFog(createWorldFog);
            this.broadcast(ClientboundBossEventPacket::createUpdatePropertiesPacket);
        }

        return this;
    }

    @Override
    public void setName(Component name) {
        if (!Objects.equal(name, this.name)) {
            super.setName(name);
            this.broadcast(ClientboundBossEventPacket::createUpdateNamePacket);
        }

    }

    public void broadcast(Function<BossEvent, ClientboundBossEventPacket> factory) {
        if (this.visible) {
            ClientboundBossEventPacket clientboundbosseventpacket = (ClientboundBossEventPacket) factory.apply(this);

            for (ServerPlayer serverplayer : this.players) {
                serverplayer.connection.send(clientboundbosseventpacket);
            }
        }

    }

    public void addPlayer(ServerPlayer player) {
        if (this.players.add(player) && this.visible) {
            player.connection.send(ClientboundBossEventPacket.createAddPacket(this));
        }

    }

    public void removePlayer(ServerPlayer player) {
        if (this.players.remove(player) && this.visible) {
            player.connection.send(ClientboundBossEventPacket.createRemovePacket(this.getId()));
        }

    }

    public void removeAllPlayers() {
        if (!this.players.isEmpty()) {
            for (ServerPlayer serverplayer : Lists.newArrayList(this.players)) {
                this.removePlayer(serverplayer);
            }
        }

    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        if (visible != this.visible) {
            this.visible = visible;

            for (ServerPlayer serverplayer : this.players) {
                serverplayer.connection.send(visible ? ClientboundBossEventPacket.createAddPacket(this) : ClientboundBossEventPacket.createRemovePacket(this.getId()));
            }
        }

    }

    public Collection<ServerPlayer> getPlayers() {
        return this.unmodifiablePlayers;
    }
}
