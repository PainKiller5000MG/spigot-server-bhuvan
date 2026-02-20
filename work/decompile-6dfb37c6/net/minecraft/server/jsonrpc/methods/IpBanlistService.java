package net.minecraft.server.jsonrpc.methods;

import com.google.common.net.InetAddresses;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public class IpBanlistService {

    private static final String BAN_SOURCE = "Management server";

    public IpBanlistService() {}

    public static List<IpBanlistService.IpBanDto> get(MinecraftApi minecraftApi) {
        return minecraftApi.banListService().getIpBanEntries().stream().map(IpBanlistService.IpBan::from).map(IpBanlistService.IpBanDto::from).toList();
    }

    public static List<IpBanlistService.IpBanDto> add(MinecraftApi minecraftApi, List<IpBanlistService.IncomingIpBanDto> bans, ClientInfo clientInfo) {
        bans.stream().map((ipbanlistservice_incomingipbandto) -> {
            return banIp(minecraftApi, ipbanlistservice_incomingipbandto, clientInfo);
        }).flatMap(Collection::stream).forEach((serverplayer) -> {
            serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.ip_banned"));
        });
        return get(minecraftApi);
    }

    private static List<ServerPlayer> banIp(MinecraftApi minecraftApi, IpBanlistService.IncomingIpBanDto ban, ClientInfo clientInfo) {
        IpBanlistService.IpBan ipbanlistservice_ipban = ban.toIpBan();

        if (ipbanlistservice_ipban != null) {
            return banIp(minecraftApi, ipbanlistservice_ipban, clientInfo);
        } else {
            if (ban.player().isPresent()) {
                Optional<ServerPlayer> optional = minecraftApi.playerListService().getPlayer(((PlayerDto) ban.player().get()).id(), ((PlayerDto) ban.player().get()).name());

                if (optional.isPresent()) {
                    return banIp(minecraftApi, ban.toIpBan((ServerPlayer) optional.get()), clientInfo);
                }
            }

            return List.of();
        }
    }

    private static List<ServerPlayer> banIp(MinecraftApi minecraftApi, IpBanlistService.IpBan ban, ClientInfo clientInfo) {
        minecraftApi.banListService().addIpBan(ban.toIpBanEntry(), clientInfo);
        return minecraftApi.playerListService().getPlayersWithAddress(ban.ip());
    }

    public static List<IpBanlistService.IpBanDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
        minecraftApi.banListService().clearIpBans(clientInfo);
        return get(minecraftApi);
    }

    public static List<IpBanlistService.IpBanDto> remove(MinecraftApi minecraftApi, List<String> ban, ClientInfo clientInfo) {
        ban.forEach((s) -> {
            minecraftApi.banListService().removeIpBan(s, clientInfo);
        });
        return get(minecraftApi);
    }

    public static List<IpBanlistService.IpBanDto> set(MinecraftApi minecraftApi, List<IpBanlistService.IpBanDto> ips, ClientInfo clientInfo) {
        Set<IpBanlistService.IpBan> set = (Set) ips.stream().filter((ipbanlistservice_ipbandto) -> {
            return InetAddresses.isInetAddress(ipbanlistservice_ipbandto.ip());
        }).map(IpBanlistService.IpBanDto::toIpBan).collect(Collectors.toSet());
        Set<IpBanlistService.IpBan> set1 = (Set) minecraftApi.banListService().getIpBanEntries().stream().map(IpBanlistService.IpBan::from).collect(Collectors.toSet());

        set1.stream().filter((ipbanlistservice_ipban) -> {
            return !set.contains(ipbanlistservice_ipban);
        }).forEach((ipbanlistservice_ipban) -> {
            minecraftApi.banListService().removeIpBan(ipbanlistservice_ipban.ip(), clientInfo);
        });
        set.stream().filter((ipbanlistservice_ipban) -> {
            return !set1.contains(ipbanlistservice_ipban);
        }).forEach((ipbanlistservice_ipban) -> {
            minecraftApi.banListService().addIpBan(ipbanlistservice_ipban.toIpBanEntry(), clientInfo);
        });
        set.stream().filter((ipbanlistservice_ipban) -> {
            return !set1.contains(ipbanlistservice_ipban);
        }).flatMap((ipbanlistservice_ipban) -> {
            return minecraftApi.playerListService().getPlayersWithAddress(ipbanlistservice_ipban.ip()).stream();
        }).forEach((serverplayer) -> {
            serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.ip_banned"));
        });
        return get(minecraftApi);
    }

    public static record IncomingIpBanDto(Optional<PlayerDto> player, Optional<String> ip, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {

        public static final MapCodec<IpBanlistService.IncomingIpBanDto> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PlayerDto.CODEC.codec().optionalFieldOf("player").forGetter(IpBanlistService.IncomingIpBanDto::player), Codec.STRING.optionalFieldOf("ip").forGetter(IpBanlistService.IncomingIpBanDto::ip), Codec.STRING.optionalFieldOf("reason").forGetter(IpBanlistService.IncomingIpBanDto::reason), Codec.STRING.optionalFieldOf("source").forGetter(IpBanlistService.IncomingIpBanDto::source), ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(IpBanlistService.IncomingIpBanDto::expires)).apply(instance, IpBanlistService.IncomingIpBanDto::new);
        });

        private IpBanlistService.IpBan toIpBan(ServerPlayer player) {
            return new IpBanlistService.IpBan(player.getIpAddress(), (String) this.reason().orElse((Object) null), (String) this.source().orElse("Management server"), this.expires());
        }

        private IpBanlistService.@Nullable IpBan toIpBan() {
            return !this.ip().isEmpty() && InetAddresses.isInetAddress((String) this.ip().get()) ? new IpBanlistService.IpBan((String) this.ip().get(), (String) this.reason().orElse((Object) null), (String) this.source().orElse("Management server"), this.expires()) : null;
        }
    }

    public static record IpBanDto(String ip, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {

        public static final MapCodec<IpBanlistService.IpBanDto> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.STRING.fieldOf("ip").forGetter(IpBanlistService.IpBanDto::ip), Codec.STRING.optionalFieldOf("reason").forGetter(IpBanlistService.IpBanDto::reason), Codec.STRING.optionalFieldOf("source").forGetter(IpBanlistService.IpBanDto::source), ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(IpBanlistService.IpBanDto::expires)).apply(instance, IpBanlistService.IpBanDto::new);
        });

        private static IpBanlistService.IpBanDto from(IpBanlistService.IpBan ban) {
            return new IpBanlistService.IpBanDto(ban.ip(), Optional.ofNullable(ban.reason()), Optional.of(ban.source()), ban.expires());
        }

        public static IpBanlistService.IpBanDto from(IpBanListEntry ban) {
            return from(IpBanlistService.IpBan.from(ban));
        }

        private IpBanlistService.IpBan toIpBan() {
            return new IpBanlistService.IpBan(this.ip(), (String) this.reason().orElse((Object) null), (String) this.source().orElse("Management server"), this.expires());
        }
    }

    private static record IpBan(String ip, @Nullable String reason, String source, Optional<Instant> expires) {

        private static IpBanlistService.IpBan from(IpBanListEntry entry) {
            return new IpBanlistService.IpBan((String) Objects.requireNonNull((String) entry.getUser()), entry.getReason(), entry.getSource(), Optional.ofNullable(entry.getExpires()).map(Date::toInstant));
        }

        private IpBanListEntry toIpBanEntry() {
            return new IpBanListEntry(this.ip(), (Date) null, this.source(), (Date) this.expires().map(Date::from).orElse((Object) null), this.reason());
        }
    }
}
