package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public class PlayerService {

    private static final Component DEFAULT_KICK_MESSAGE = Component.translatable("multiplayer.disconnect.kicked");

    public PlayerService() {}

    public static List<PlayerDto> get(MinecraftApi minecraftApi) {
        return minecraftApi.playerListService().getPlayers().stream().map(PlayerDto::from).toList();
    }

    public static List<PlayerDto> kick(MinecraftApi minecraftApi, List<PlayerService.KickDto> kick, ClientInfo clientInfo) {
        List<PlayerDto> list1 = new ArrayList();

        for (PlayerService.KickDto playerservice_kickdto : kick) {
            ServerPlayer serverplayer = getServerPlayer(minecraftApi, playerservice_kickdto.player());

            if (serverplayer != null) {
                minecraftApi.playerListService().remove(serverplayer, clientInfo);
                serverplayer.connection.disconnect((Component) playerservice_kickdto.message.flatMap(Message::asComponent).orElse(PlayerService.DEFAULT_KICK_MESSAGE));
                list1.add(playerservice_kickdto.player());
            }
        }

        return list1;
    }

    private static @Nullable ServerPlayer getServerPlayer(MinecraftApi minecraftApi, PlayerDto playerDto) {
        return playerDto.id().isPresent() ? minecraftApi.playerListService().getPlayer((UUID) playerDto.id().get()) : (playerDto.name().isPresent() ? minecraftApi.playerListService().getPlayerByName((String) playerDto.name().get()) : null);
    }

    public static record KickDto(PlayerDto player, Optional<Message> message) {

        public static final MapCodec<PlayerService.KickDto> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PlayerDto.CODEC.codec().fieldOf("player").forGetter(PlayerService.KickDto::player), Message.CODEC.optionalFieldOf("message").forGetter(PlayerService.KickDto::message)).apply(instance, PlayerService.KickDto::new);
        });
    }
}
