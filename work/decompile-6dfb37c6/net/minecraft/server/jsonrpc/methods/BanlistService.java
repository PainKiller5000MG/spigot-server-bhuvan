package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class BanlistService {

    private static final String BAN_SOURCE = "Management server";

    public BanlistService() {}

    public static List<BanlistService.UserBanDto> get(MinecraftApi minecraftApi) {
        return minecraftApi.banListService().getUserBanEntries().stream().filter((userbanlistentry) -> {
            return userbanlistentry.getUser() != null;
        }).map(BanlistService.UserBan::from).map(BanlistService.UserBanDto::from).toList();
    }

    public static List<BanlistService.UserBanDto> add(MinecraftApi minecraftApi, List<BanlistService.UserBanDto> bans, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<BanlistService.UserBan>>> list1 = bans.stream().map((banlistservice_userbandto) -> {
            return minecraftApi.playerListService().getUser(banlistservice_userbandto.player().id(), banlistservice_userbandto.player().name()).thenApply((optional) -> {
                Objects.requireNonNull(banlistservice_userbandto);
                return optional.map(banlistservice_userbandto::toUserBan);
            });
        }).toList();

        for (Optional<BanlistService.UserBan> optional : (List) Util.sequence(list1).join()) {
            if (!optional.isEmpty()) {
                BanlistService.UserBan banlistservice_userban = (BanlistService.UserBan) optional.get();

                minecraftApi.banListService().addUserBan(banlistservice_userban.toBanEntry(), clientInfo);
                ServerPlayer serverplayer = minecraftApi.playerListService().getPlayer(((BanlistService.UserBan) optional.get()).player().id());

                if (serverplayer != null) {
                    serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.banned"));
                }
            }
        }

        return get(minecraftApi);
    }

    public static List<BanlistService.UserBanDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
        minecraftApi.banListService().clearUserBans(clientInfo);
        return get(minecraftApi);
    }

    public static List<BanlistService.UserBanDto> remove(MinecraftApi minecraftApi, List<PlayerDto> remove, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> list1 = remove.stream().map((playerdto) -> {
            return minecraftApi.playerListService().getUser(playerdto.id(), playerdto.name());
        }).toList();

        for (Optional<NameAndId> optional : (List) Util.sequence(list1).join()) {
            if (!optional.isEmpty()) {
                minecraftApi.banListService().removeUserBan((NameAndId) optional.get(), clientInfo);
            }
        }

        return get(minecraftApi);
    }

    public static List<BanlistService.UserBanDto> set(MinecraftApi minecraftApi, List<BanlistService.UserBanDto> bans, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<BanlistService.UserBan>>> list1 = bans.stream().map((banlistservice_userbandto) -> {
            return minecraftApi.playerListService().getUser(banlistservice_userbandto.player().id(), banlistservice_userbandto.player().name()).thenApply((optional) -> {
                Objects.requireNonNull(banlistservice_userbandto);
                return optional.map(banlistservice_userbandto::toUserBan);
            });
        }).toList();
        Set<BanlistService.UserBan> set = (Set) ((List) Util.sequence(list1).join()).stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<BanlistService.UserBan> set1 = (Set) minecraftApi.banListService().getUserBanEntries().stream().filter((userbanlistentry) -> {
            return userbanlistentry.getUser() != null;
        }).map(BanlistService.UserBan::from).collect(Collectors.toSet());

        set1.stream().filter((banlistservice_userban) -> {
            return !set.contains(banlistservice_userban);
        }).forEach((banlistservice_userban) -> {
            minecraftApi.banListService().removeUserBan(banlistservice_userban.player(), clientInfo);
        });
        set.stream().filter((banlistservice_userban) -> {
            return !set1.contains(banlistservice_userban);
        }).forEach((banlistservice_userban) -> {
            minecraftApi.banListService().addUserBan(banlistservice_userban.toBanEntry(), clientInfo);
            ServerPlayer serverplayer = minecraftApi.playerListService().getPlayer(banlistservice_userban.player().id());

            if (serverplayer != null) {
                serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.banned"));
            }

        });
        return get(minecraftApi);
    }

    public static record UserBanDto(PlayerDto player, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {

        public static final MapCodec<BanlistService.UserBanDto> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PlayerDto.CODEC.codec().fieldOf("player").forGetter(BanlistService.UserBanDto::player), Codec.STRING.optionalFieldOf("reason").forGetter(BanlistService.UserBanDto::reason), Codec.STRING.optionalFieldOf("source").forGetter(BanlistService.UserBanDto::source), ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(BanlistService.UserBanDto::expires)).apply(instance, BanlistService.UserBanDto::new);
        });

        private static BanlistService.UserBanDto from(BanlistService.UserBan ban) {
            return new BanlistService.UserBanDto(PlayerDto.from(ban.player()), Optional.ofNullable(ban.reason()), Optional.of(ban.source()), ban.expires());
        }

        public static BanlistService.UserBanDto from(UserBanListEntry entry) {
            return from(BanlistService.UserBan.from(entry));
        }

        private BanlistService.UserBan toUserBan(NameAndId nameAndId) {
            return new BanlistService.UserBan(nameAndId, (String) this.reason().orElse((Object) null), (String) this.source().orElse("Management server"), this.expires());
        }
    }

    private static record UserBan(NameAndId player, @Nullable String reason, String source, Optional<Instant> expires) {

        private static BanlistService.UserBan from(UserBanListEntry entry) {
            return new BanlistService.UserBan((NameAndId) Objects.requireNonNull((NameAndId) entry.getUser()), entry.getReason(), entry.getSource(), Optional.ofNullable(entry.getExpires()).map(Date::toInstant));
        }

        private UserBanListEntry toBanEntry() {
            return new UserBanListEntry(new NameAndId(this.player().id(), this.player().name()), (Date) null, this.source(), (Date) this.expires().map(Date::from).orElse((Object) null), this.reason());
        }
    }
}
