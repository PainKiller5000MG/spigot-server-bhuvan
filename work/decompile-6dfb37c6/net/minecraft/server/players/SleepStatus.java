package net.minecraft.server.players;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class SleepStatus {

    private int activePlayers;
    private int sleepingPlayers;

    public SleepStatus() {}

    public boolean areEnoughSleeping(int sleepPercentageNeeded) {
        return this.sleepingPlayers >= this.sleepersNeeded(sleepPercentageNeeded);
    }

    public boolean areEnoughDeepSleeping(int sleepPercentageNeeded, List<ServerPlayer> players) {
        int j = (int) players.stream().filter(Player::isSleepingLongEnough).count();

        return j >= this.sleepersNeeded(sleepPercentageNeeded);
    }

    public int sleepersNeeded(int sleepPercentageNeeded) {
        return Math.max(1, Mth.ceil((float) (this.activePlayers * sleepPercentageNeeded) / 100.0F));
    }

    public void removeAllSleepers() {
        this.sleepingPlayers = 0;
    }

    public int amountSleeping() {
        return this.sleepingPlayers;
    }

    public boolean update(List<ServerPlayer> players) {
        int i = this.activePlayers;
        int j = this.sleepingPlayers;

        this.activePlayers = 0;
        this.sleepingPlayers = 0;

        for (ServerPlayer serverplayer : players) {
            if (!serverplayer.isSpectator()) {
                ++this.activePlayers;
                if (serverplayer.isSleeping()) {
                    ++this.sleepingPlayers;
                }
            }
        }

        return (j > 0 || this.sleepingPlayers > 0) && (i != this.activePlayers || j != this.sleepingPlayers);
    }
}
