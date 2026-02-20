package net.minecraft.server.notifications;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public class NotificationManager implements NotificationService {

    private final List<NotificationService> notificationServices = Lists.newArrayList();

    public NotificationManager() {}

    public void registerService(NotificationService notificationService) {
        this.notificationServices.add(notificationService);
    }

    @Override
    public void playerJoined(ServerPlayer player) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerJoined(player);
        });
    }

    @Override
    public void playerLeft(ServerPlayer player) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerLeft(player);
        });
    }

    @Override
    public void serverStarted() {
        this.notificationServices.forEach(NotificationService::serverStarted);
    }

    @Override
    public void serverShuttingDown() {
        this.notificationServices.forEach(NotificationService::serverShuttingDown);
    }

    @Override
    public void serverSaveStarted() {
        this.notificationServices.forEach(NotificationService::serverSaveStarted);
    }

    @Override
    public void serverSaveCompleted() {
        this.notificationServices.forEach(NotificationService::serverSaveCompleted);
    }

    @Override
    public void serverActivityOccured() {
        this.notificationServices.forEach(NotificationService::serverActivityOccured);
    }

    @Override
    public void playerOped(ServerOpListEntry operator) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerOped(operator);
        });
    }

    @Override
    public void playerDeoped(ServerOpListEntry operator) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerDeoped(operator);
        });
    }

    @Override
    public void playerAddedToAllowlist(NameAndId player) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerAddedToAllowlist(player);
        });
    }

    @Override
    public void playerRemovedFromAllowlist(NameAndId player) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerRemovedFromAllowlist(player);
        });
    }

    @Override
    public void ipBanned(IpBanListEntry ban) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.ipBanned(ban);
        });
    }

    @Override
    public void ipUnbanned(String ip) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.ipUnbanned(ip);
        });
    }

    @Override
    public void playerBanned(UserBanListEntry ban) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerBanned(ban);
        });
    }

    @Override
    public void playerUnbanned(NameAndId player) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.playerUnbanned(player);
        });
    }

    @Override
    public <T> void onGameRuleChanged(GameRule<T> gameRule, T value) {
        this.notificationServices.forEach((notificationservice) -> {
            notificationservice.onGameRuleChanged(gameRule, value);
        });
    }

    @Override
    public void statusHeartbeat() {
        this.notificationServices.forEach(NotificationService::statusHeartbeat);
    }
}
