package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class JukeboxSongPlayer {

    public static final int PLAY_EVENT_INTERVAL_TICKS = 20;
    private long ticksSinceSongStarted;
    public @Nullable Holder<JukeboxSong> song;
    private final BlockPos blockPos;
    private final JukeboxSongPlayer.OnSongChanged onSongChanged;

    public JukeboxSongPlayer(JukeboxSongPlayer.OnSongChanged onSongChanged, BlockPos blockPos) {
        this.onSongChanged = onSongChanged;
        this.blockPos = blockPos;
    }

    public boolean isPlaying() {
        return this.song != null;
    }

    public @Nullable JukeboxSong getSong() {
        return this.song == null ? null : (JukeboxSong) this.song.value();
    }

    public long getTicksSinceSongStarted() {
        return this.ticksSinceSongStarted;
    }

    public void setSongWithoutPlaying(Holder<JukeboxSong> song, long ticksSinceSongStarted) {
        if (!((JukeboxSong) song.value()).hasFinished(ticksSinceSongStarted)) {
            this.song = song;
            this.ticksSinceSongStarted = ticksSinceSongStarted;
        }
    }

    public void play(LevelAccessor level, Holder<JukeboxSong> song) {
        this.song = song;
        this.ticksSinceSongStarted = 0L;
        int i = level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getId(this.song.value());

        level.levelEvent((Entity) null, 1010, this.blockPos, i);
        this.onSongChanged.notifyChange();
    }

    public void stop(LevelAccessor level, @Nullable BlockState blockState) {
        if (this.song != null) {
            this.song = null;
            this.ticksSinceSongStarted = 0L;
            level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.blockPos, GameEvent.Context.of(blockState));
            level.levelEvent(1011, this.blockPos, 0);
            this.onSongChanged.notifyChange();
        }
    }

    public void tick(LevelAccessor level, @Nullable BlockState blockState) {
        if (this.song != null) {
            if (((JukeboxSong) this.song.value()).hasFinished(this.ticksSinceSongStarted)) {
                this.stop(level, blockState);
            } else {
                if (this.shouldEmitJukeboxPlayingEvent()) {
                    level.gameEvent(GameEvent.JUKEBOX_PLAY, this.blockPos, GameEvent.Context.of(blockState));
                    spawnMusicParticles(level, this.blockPos);
                }

                ++this.ticksSinceSongStarted;
            }
        }
    }

    private boolean shouldEmitJukeboxPlayingEvent() {
        return this.ticksSinceSongStarted % 20L == 0L;
    }

    private static void spawnMusicParticles(LevelAccessor level, BlockPos blockPos) {
        if (level instanceof ServerLevel serverlevel) {
            Vec3 vec3 = Vec3.atBottomCenterOf(blockPos).add(0.0D, (double) 1.2F, 0.0D);
            float f = (float) level.getRandom().nextInt(4) / 24.0F;

            serverlevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, (double) f, 0.0D, 0.0D, 1.0D);
        }

    }

    @FunctionalInterface
    public interface OnSongChanged {

        void notifyChange();
    }
}
