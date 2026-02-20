package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;

public class ServerStateService {

    public ServerStateService() {}

    public static ServerStateService.ServerState status(MinecraftApi minecraftApi) {
        return !minecraftApi.serverStateService().isReady() ? ServerStateService.ServerState.NOT_STARTED : new ServerStateService.ServerState(true, PlayerService.get(minecraftApi), ServerStatus.Version.current());
    }

    public static boolean save(MinecraftApi minecraftApi, boolean flush, ClientInfo clientInfo) {
        return minecraftApi.serverStateService().saveEverything(true, flush, true, clientInfo);
    }

    public static boolean stop(MinecraftApi minecraftApi, ClientInfo clientInfo) {
        minecraftApi.submit(() -> {
            minecraftApi.serverStateService().halt(false, clientInfo);
        });
        return true;
    }

    public static boolean systemMessage(MinecraftApi minecraftApi, ServerStateService.SystemMessage systemMessage, ClientInfo clientInfo) {
        Component component = (Component) systemMessage.message().asComponent().orElse((Object) null);

        if (component == null) {
            return false;
        } else {
            if (systemMessage.receivingPlayers().isPresent()) {
                if (((List) systemMessage.receivingPlayers().get()).isEmpty()) {
                    return false;
                }

                for (PlayerDto playerdto : (List) systemMessage.receivingPlayers().get()) {
                    ServerPlayer serverplayer;

                    if (playerdto.id().isPresent()) {
                        serverplayer = minecraftApi.playerListService().getPlayer((UUID) playerdto.id().get());
                    } else {
                        if (!playerdto.name().isPresent()) {
                            continue;
                        }

                        serverplayer = minecraftApi.playerListService().getPlayerByName((String) playerdto.name().get());
                    }

                    if (serverplayer != null) {
                        serverplayer.sendSystemMessage(component, systemMessage.overlay());
                    }
                }
            } else {
                minecraftApi.serverStateService().broadcastSystemMessage(component, systemMessage.overlay(), clientInfo);
            }

            return true;
        }
    }

    public static record ServerState(boolean started, List<PlayerDto> players, ServerStatus.Version version) {

        public static final Codec<ServerStateService.ServerState> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.BOOL.fieldOf("started").forGetter(ServerStateService.ServerState::started), PlayerDto.CODEC.codec().listOf().lenientOptionalFieldOf("players", List.of()).forGetter(ServerStateService.ServerState::players), ServerStatus.Version.CODEC.fieldOf("version").forGetter(ServerStateService.ServerState::version)).apply(instance, ServerStateService.ServerState::new);
        });
        public static final ServerStateService.ServerState NOT_STARTED = new ServerStateService.ServerState(false, List.of(), ServerStatus.Version.current());
    }

    public static record SystemMessage(Message message, boolean overlay, Optional<List<PlayerDto>> receivingPlayers) {

        public static final Codec<ServerStateService.SystemMessage> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Message.CODEC.fieldOf("message").forGetter(ServerStateService.SystemMessage::message), Codec.BOOL.fieldOf("overlay").forGetter(ServerStateService.SystemMessage::overlay), PlayerDto.CODEC.codec().listOf().lenientOptionalFieldOf("receivingPlayers").forGetter(ServerStateService.SystemMessage::receivingPlayers)).apply(instance, ServerStateService.SystemMessage::new);
        });
    }
}
