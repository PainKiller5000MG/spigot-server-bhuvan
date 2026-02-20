package net.minecraft.server.jsonrpc.methods;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.util.Util;

public class AllowlistService {

    public AllowlistService() {}

    public static List<PlayerDto> get(MinecraftApi minecraftApi) {
        return minecraftApi.allowListService().getEntries().stream().filter((userwhitelistentry) -> {
            return userwhitelistentry.getUser() != null;
        }).map((userwhitelistentry) -> {
            return PlayerDto.from((NameAndId) userwhitelistentry.getUser());
        }).toList();
    }

    public static List<PlayerDto> add(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> list1 = playerDtos.stream().map((playerdto) -> {
            return minecraftApi.playerListService().getUser(playerdto.id(), playerdto.name());
        }).toList();

        for (Optional<NameAndId> optional : (List) Util.sequence(list1).join()) {
            optional.ifPresent((nameandid) -> {
                minecraftApi.allowListService().add(new UserWhiteListEntry(nameandid), clientInfo);
            });
        }

        return get(minecraftApi);
    }

    public static List<PlayerDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
        minecraftApi.allowListService().clear(clientInfo);
        return get(minecraftApi);
    }

    public static List<PlayerDto> remove(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> list1 = playerDtos.stream().map((playerdto) -> {
            return minecraftApi.playerListService().getUser(playerdto.id(), playerdto.name());
        }).toList();

        for (Optional<NameAndId> optional : (List) Util.sequence(list1).join()) {
            optional.ifPresent((nameandid) -> {
                minecraftApi.allowListService().remove(nameandid, clientInfo);
            });
        }

        minecraftApi.allowListService().kickUnlistedPlayers(clientInfo);
        return get(minecraftApi);
    }

    public static List<PlayerDto> set(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> list1 = playerDtos.stream().map((playerdto) -> {
            return minecraftApi.playerListService().getUser(playerdto.id(), playerdto.name());
        }).toList();
        Set<NameAndId> set = (Set) ((List) Util.sequence(list1).join()).stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<NameAndId> set1 = (Set) minecraftApi.allowListService().getEntries().stream().map(StoredUserEntry::getUser).collect(Collectors.toSet());

        set1.stream().filter((nameandid) -> {
            return !set.contains(nameandid);
        }).forEach((nameandid) -> {
            minecraftApi.allowListService().remove(nameandid, clientInfo);
        });
        set.stream().filter((nameandid) -> {
            return !set1.contains(nameandid);
        }).forEach((nameandid) -> {
            minecraftApi.allowListService().add(new UserWhiteListEntry(nameandid), clientInfo);
        });
        minecraftApi.allowListService().kickUnlistedPlayers(clientInfo);
        return get(minecraftApi);
    }
}
