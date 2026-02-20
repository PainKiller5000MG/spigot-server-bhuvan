package net.minecraft.server.network.config;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PrepareSpawnTask implements ConfigurationTask {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("prepare_spawn");
    public static final int PREPARE_CHUNK_RADIUS = 3;
    private final MinecraftServer server;
    private final NameAndId nameAndId;
    private final LevelLoadListener loadListener;
    private PrepareSpawnTask.@Nullable State state;

    public PrepareSpawnTask(MinecraftServer server, NameAndId nameAndId) {
        this.server = server;
        this.nameAndId = nameAndId;
        this.loadListener = server.getLevelLoadListener();
    }

    @Override
    public void start(Consumer<Packet<?>> connection) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(PrepareSpawnTask.LOGGER)) {
            Optional<ValueInput> optional = this.server.getPlayerList().loadPlayerData(this.nameAndId).map((compoundtag) -> {
                return TagValueInput.create(problemreporter_scopedcollector, this.server.registryAccess(), compoundtag);
            });
            ServerPlayer.SavedPosition serverplayer_savedposition = (ServerPlayer.SavedPosition) optional.flatMap((valueinput) -> {
                return valueinput.read(ServerPlayer.SavedPosition.MAP_CODEC);
            }).orElse(ServerPlayer.SavedPosition.EMPTY);
            LevelData.RespawnData leveldata_respawndata = this.server.getWorldData().overworldData().getRespawnData();
            Optional optional1 = serverplayer_savedposition.dimension();
            MinecraftServer minecraftserver = this.server;

            Objects.requireNonNull(this.server);
            ServerLevel serverlevel = (ServerLevel) optional1.map(minecraftserver::getLevel).orElseGet(() -> {
                ServerLevel serverlevel1 = this.server.getLevel(leveldata_respawndata.dimension());

                return serverlevel1 != null ? serverlevel1 : this.server.overworld();
            });
            CompletableFuture<Vec3> completablefuture = (CompletableFuture) serverplayer_savedposition.position().map(CompletableFuture::completedFuture).orElseGet(() -> {
                return PlayerSpawnFinder.findSpawn(serverlevel, leveldata_respawndata.pos());
            });
            Vec2 vec2 = (Vec2) serverplayer_savedposition.rotation().orElse(new Vec2(leveldata_respawndata.yaw(), leveldata_respawndata.pitch()));

            this.state = new PrepareSpawnTask.Preparing(serverlevel, completablefuture, vec2);
        }

    }

    @Override
    public boolean tick() {
        PrepareSpawnTask.State preparespawntask_state = this.state;
        byte b0 = 0;
        boolean flag;

        //$FF: b0->value
        //0->net/minecraft/server/network/config/PrepareSpawnTask$Preparing
        //1->net/minecraft/server/network/config/PrepareSpawnTask$Ready
        switch (preparespawntask_state.typeSwitch<invokedynamic>(preparespawntask_state, b0)) {
            case -1:
                flag = false;
                break;
            case 0:
                PrepareSpawnTask.Preparing preparespawntask_preparing = (PrepareSpawnTask.Preparing)preparespawntask_state;
                PrepareSpawnTask.Ready preparespawntask_ready = preparespawntask_preparing.tick();

                if (preparespawntask_ready != null) {
                    this.state = preparespawntask_ready;
                    flag = true;
                } else {
                    flag = false;
                }
                break;
            case 1:
                PrepareSpawnTask.Ready preparespawntask_ready1 = (PrepareSpawnTask.Ready)preparespawntask_state;

                flag = true;
                break;
            default:
                throw new MatchException((String)null, (Throwable)null);
        }

        return flag;
    }

    public ServerPlayer spawnPlayer(Connection connection, CommonListenerCookie cookie) {
        PrepareSpawnTask.State preparespawntask_state = this.state;

        if (preparespawntask_state instanceof PrepareSpawnTask.Ready preparespawntask_ready) {
            return preparespawntask_ready.spawn(connection, cookie);
        } else {
            throw new IllegalStateException("Player spawn was not ready");
        }
    }

    public void keepAlive() {
        PrepareSpawnTask.State preparespawntask_state = this.state;

        if (preparespawntask_state instanceof PrepareSpawnTask.Ready preparespawntask_ready) {
            preparespawntask_ready.keepAlive();
        }

    }

    public void close() {
        PrepareSpawnTask.State preparespawntask_state = this.state;

        if (preparespawntask_state instanceof PrepareSpawnTask.Preparing preparespawntask_preparing) {
            preparespawntask_preparing.cancel();
        }

        this.state = null;
    }

    @Override
    public ConfigurationTask.Type type() {
        return PrepareSpawnTask.TYPE;
    }

    private final class Preparing implements PrepareSpawnTask.State {

        private final ServerLevel spawnLevel;
        private final CompletableFuture<Vec3> spawnPosition;
        private final Vec2 spawnAngle;
        private @Nullable CompletableFuture<?> chunkLoadFuture;
        private final ChunkLoadCounter chunkLoadCounter = new ChunkLoadCounter();

        private Preparing(ServerLevel spawnLevel, CompletableFuture<Vec3> spawnPosition, Vec2 spawnAngle) {
            this.spawnLevel = spawnLevel;
            this.spawnPosition = spawnPosition;
            this.spawnAngle = spawnAngle;
        }

        public void cancel() {
            this.spawnPosition.cancel(false);
        }

        public PrepareSpawnTask.@Nullable Ready tick() {
            if (!this.spawnPosition.isDone()) {
                return null;
            } else {
                Vec3 vec3 = (Vec3) this.spawnPosition.join();

                if (this.chunkLoadFuture == null) {
                    ChunkPos chunkpos = new ChunkPos(BlockPos.containing(vec3));

                    this.chunkLoadCounter.track(this.spawnLevel, () -> {
                        this.chunkLoadFuture = this.spawnLevel.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN, chunkpos, 3);
                    });
                    PrepareSpawnTask.this.loadListener.start(LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS, this.chunkLoadCounter.totalChunks());
                    PrepareSpawnTask.this.loadListener.updateFocus(this.spawnLevel.dimension(), chunkpos);
                }

                PrepareSpawnTask.this.loadListener.update(LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS, this.chunkLoadCounter.readyChunks(), this.chunkLoadCounter.totalChunks());
                if (!this.chunkLoadFuture.isDone()) {
                    return null;
                } else {
                    PrepareSpawnTask.this.loadListener.finish(LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS);
                    return PrepareSpawnTask.this.new Ready(this.spawnLevel, vec3, this.spawnAngle);
                }
            }
        }
    }

    private final class Ready implements PrepareSpawnTask.State {

        private final ServerLevel spawnLevel;
        private final Vec3 spawnPosition;
        private final Vec2 spawnAngle;

        private Ready(ServerLevel spawnLevel, Vec3 spawnPosition, Vec2 spawnAngle) {
            this.spawnLevel = spawnLevel;
            this.spawnPosition = spawnPosition;
            this.spawnAngle = spawnAngle;
        }

        public void keepAlive() {
            this.spawnLevel.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, new ChunkPos(BlockPos.containing(this.spawnPosition)), 3);
        }

        public ServerPlayer spawn(Connection connection, CommonListenerCookie cookie) {
            ChunkPos chunkpos = new ChunkPos(BlockPos.containing(this.spawnPosition));

            this.spawnLevel.waitForEntities(chunkpos, 3);
            ServerPlayer serverplayer = new ServerPlayer(PrepareSpawnTask.this.server, this.spawnLevel, cookie.gameProfile(), cookie.clientInformation());

            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(serverplayer.problemPath(), PrepareSpawnTask.LOGGER)) {
                Optional<ValueInput> optional = PrepareSpawnTask.this.server.getPlayerList().loadPlayerData(PrepareSpawnTask.this.nameAndId).map((compoundtag) -> {
                    return TagValueInput.create(problemreporter_scopedcollector, PrepareSpawnTask.this.server.registryAccess(), compoundtag);
                });

                Objects.requireNonNull(serverplayer);
                optional.ifPresent(serverplayer::load);
                serverplayer.snapTo(this.spawnPosition, this.spawnAngle.x, this.spawnAngle.y);
                PrepareSpawnTask.this.server.getPlayerList().placeNewPlayer(connection, serverplayer, cookie);
                optional.ifPresent((valueinput) -> {
                    serverplayer.loadAndSpawnEnderPearls(valueinput);
                    serverplayer.loadAndSpawnParentVehicle(valueinput);
                });
                return serverplayer;
            }
        }
    }

    private sealed interface State permits PrepareSpawnTask.Preparing, PrepareSpawnTask.Ready {}
}
