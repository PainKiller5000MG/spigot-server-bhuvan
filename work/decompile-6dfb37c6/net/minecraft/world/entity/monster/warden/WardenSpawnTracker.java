package net.minecraft.world.entity.monster.warden;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WardenSpawnTracker {

    public static final Codec<WardenSpawnTracker> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticks_since_last_warning").orElse(0).forGetter((wardenspawntracker) -> {
            return wardenspawntracker.ticksSinceLastWarning;
        }), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("warning_level").orElse(0).forGetter((wardenspawntracker) -> {
            return wardenspawntracker.warningLevel;
        }), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cooldown_ticks").orElse(0).forGetter((wardenspawntracker) -> {
            return wardenspawntracker.cooldownTicks;
        })).apply(instance, WardenSpawnTracker::new);
    });
    public static final int MAX_WARNING_LEVEL = 4;
    private static final double PLAYER_SEARCH_RADIUS = 16.0D;
    private static final int WARNING_CHECK_DIAMETER = 48;
    private static final int DECREASE_WARNING_LEVEL_EVERY_INTERVAL = 12000;
    private static final int WARNING_LEVEL_INCREASE_COOLDOWN = 200;
    private int ticksSinceLastWarning;
    private int warningLevel;
    private int cooldownTicks;

    public WardenSpawnTracker(int ticksSinceLastWarning, int warningLevel, int cooldownTicks) {
        this.ticksSinceLastWarning = ticksSinceLastWarning;
        this.warningLevel = warningLevel;
        this.cooldownTicks = cooldownTicks;
    }

    public WardenSpawnTracker() {
        this(0, 0, 0);
    }

    public void tick() {
        if (this.ticksSinceLastWarning >= 12000) {
            this.decreaseWarningLevel();
            this.ticksSinceLastWarning = 0;
        } else {
            ++this.ticksSinceLastWarning;
        }

        if (this.cooldownTicks > 0) {
            --this.cooldownTicks;
        }

    }

    public void reset() {
        this.ticksSinceLastWarning = 0;
        this.warningLevel = 0;
        this.cooldownTicks = 0;
    }

    public static OptionalInt tryWarn(ServerLevel level, BlockPos pos, ServerPlayer triggerPlayer) {
        if (hasNearbyWarden(level, pos)) {
            return OptionalInt.empty();
        } else {
            List<ServerPlayer> list = getNearbyPlayers(level, pos);

            if (!list.contains(triggerPlayer)) {
                list.add(triggerPlayer);
            }

            if (list.stream().anyMatch((serverplayer1) -> {
                return (Boolean) serverplayer1.getWardenSpawnTracker().map(WardenSpawnTracker::onCooldown).orElse(false);
            })) {
                return OptionalInt.empty();
            } else {
                Optional<WardenSpawnTracker> optional = list.stream().flatMap((serverplayer1) -> {
                    return serverplayer1.getWardenSpawnTracker().stream();
                }).max(Comparator.comparingInt(WardenSpawnTracker::getWarningLevel));

                if (optional.isPresent()) {
                    WardenSpawnTracker wardenspawntracker = (WardenSpawnTracker) optional.get();

                    wardenspawntracker.increaseWarningLevel();
                    list.forEach((serverplayer1) -> {
                        serverplayer1.getWardenSpawnTracker().ifPresent((wardenspawntracker1) -> {
                            wardenspawntracker1.copyData(wardenspawntracker);
                        });
                    });
                    return OptionalInt.of(wardenspawntracker.warningLevel);
                } else {
                    return OptionalInt.empty();
                }
            }
        }
    }

    private boolean onCooldown() {
        return this.cooldownTicks > 0;
    }

    private static boolean hasNearbyWarden(ServerLevel level, BlockPos pos) {
        AABB aabb = AABB.ofSize(Vec3.atCenterOf(pos), 48.0D, 48.0D, 48.0D);

        return !level.getEntitiesOfClass(Warden.class, aabb).isEmpty();
    }

    private static List<ServerPlayer> getNearbyPlayers(ServerLevel level, BlockPos pos) {
        Vec3 vec3 = Vec3.atCenterOf(pos);

        return level.getPlayers((serverplayer) -> {
            return !serverplayer.isSpectator() && serverplayer.position().closerThan(vec3, 16.0D) && serverplayer.isAlive();
        });
    }

    private void increaseWarningLevel() {
        if (!this.onCooldown()) {
            this.ticksSinceLastWarning = 0;
            this.cooldownTicks = 200;
            this.setWarningLevel(this.getWarningLevel() + 1);
        }

    }

    private void decreaseWarningLevel() {
        this.setWarningLevel(this.getWarningLevel() - 1);
    }

    public void setWarningLevel(int warningLevel) {
        this.warningLevel = Mth.clamp(warningLevel, 0, 4);
    }

    public int getWarningLevel() {
        return this.warningLevel;
    }

    private void copyData(WardenSpawnTracker copyFrom) {
        this.warningLevel = copyFrom.warningLevel;
        this.cooldownTicks = copyFrom.cooldownTicks;
        this.ticksSinceLastWarning = copyFrom.ticksSinceLastWarning;
    }
}
