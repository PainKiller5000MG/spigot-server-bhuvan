package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.util.Util;

public class OperatorService {

    public OperatorService() {}

    public static List<OperatorService.OperatorDto> get(MinecraftApi minecraftApi) {
        return minecraftApi.operatorListService().getEntries().stream().filter((serveroplistentry) -> {
            return serveroplistentry.getUser() != null;
        }).map(OperatorService.OperatorDto::from).toList();
    }

    public static List<OperatorService.OperatorDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
        minecraftApi.operatorListService().clear(clientInfo);
        return get(minecraftApi);
    }

    public static List<OperatorService.OperatorDto> remove(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> list1 = playerDtos.stream().map((playerdto) -> {
            return minecraftApi.playerListService().getUser(playerdto.id(), playerdto.name());
        }).toList();

        for (Optional<NameAndId> optional : (List) Util.sequence(list1).join()) {
            optional.ifPresent((nameandid) -> {
                minecraftApi.operatorListService().deop(nameandid, clientInfo);
            });
        }

        return get(minecraftApi);
    }

    public static List<OperatorService.OperatorDto> add(MinecraftApi minecraftApi, List<OperatorService.OperatorDto> operators, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<OperatorService.Op>>> list1 = operators.stream().map((operatorservice_operatordto) -> {
            return minecraftApi.playerListService().getUser(operatorservice_operatordto.player().id(), operatorservice_operatordto.player().name()).thenApply((optional) -> {
                return optional.map((nameandid) -> {
                    return new OperatorService.Op(nameandid, operatorservice_operatordto.permissionLevel(), operatorservice_operatordto.bypassesPlayerLimit());
                });
            });
        }).toList();

        for (Optional<OperatorService.Op> optional : (List) Util.sequence(list1).join()) {
            optional.ifPresent((operatorservice_op) -> {
                minecraftApi.operatorListService().op(operatorservice_op.user(), operatorservice_op.permissionLevel(), operatorservice_op.bypassesPlayerLimit(), clientInfo);
            });
        }

        return get(minecraftApi);
    }

    public static List<OperatorService.OperatorDto> set(MinecraftApi minecraftApi, List<OperatorService.OperatorDto> operators, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<OperatorService.Op>>> list1 = operators.stream().map((operatorservice_operatordto) -> {
            return minecraftApi.playerListService().getUser(operatorservice_operatordto.player().id(), operatorservice_operatordto.player().name()).thenApply((optional) -> {
                return optional.map((nameandid) -> {
                    return new OperatorService.Op(nameandid, operatorservice_operatordto.permissionLevel(), operatorservice_operatordto.bypassesPlayerLimit());
                });
            });
        }).toList();
        Set<OperatorService.Op> set = (Set) ((List) Util.sequence(list1).join()).stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<OperatorService.Op> set1 = (Set) minecraftApi.operatorListService().getEntries().stream().filter((serveroplistentry) -> {
            return serveroplistentry.getUser() != null;
        }).map((serveroplistentry) -> {
            return new OperatorService.Op((NameAndId) serveroplistentry.getUser(), Optional.of(serveroplistentry.permissions().level()), Optional.of(serveroplistentry.getBypassesPlayerLimit()));
        }).collect(Collectors.toSet());

        set1.stream().filter((operatorservice_op) -> {
            return !set.contains(operatorservice_op);
        }).forEach((operatorservice_op) -> {
            minecraftApi.operatorListService().deop(operatorservice_op.user(), clientInfo);
        });
        set.stream().filter((operatorservice_op) -> {
            return !set1.contains(operatorservice_op);
        }).forEach((operatorservice_op) -> {
            minecraftApi.operatorListService().op(operatorservice_op.user(), operatorservice_op.permissionLevel(), operatorservice_op.bypassesPlayerLimit(), clientInfo);
        });
        return get(minecraftApi);
    }

    public static record OperatorDto(PlayerDto player, Optional<PermissionLevel> permissionLevel, Optional<Boolean> bypassesPlayerLimit) {

        public static final MapCodec<OperatorService.OperatorDto> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(PlayerDto.CODEC.codec().fieldOf("player").forGetter(OperatorService.OperatorDto::player), PermissionLevel.INT_CODEC.optionalFieldOf("permissionLevel").forGetter(OperatorService.OperatorDto::permissionLevel), Codec.BOOL.optionalFieldOf("bypassesPlayerLimit").forGetter(OperatorService.OperatorDto::bypassesPlayerLimit)).apply(instance, OperatorService.OperatorDto::new);
        });

        public static OperatorService.OperatorDto from(ServerOpListEntry serverOpListEntry) {
            return new OperatorService.OperatorDto(PlayerDto.from((NameAndId) Objects.requireNonNull((NameAndId) serverOpListEntry.getUser())), Optional.of(serverOpListEntry.permissions().level()), Optional.of(serverOpListEntry.getBypassesPlayerLimit()));
        }
    }

    static record Op(NameAndId user, Optional<PermissionLevel> permissionLevel, Optional<Boolean> bypassesPlayerLimit) {

    }
}
