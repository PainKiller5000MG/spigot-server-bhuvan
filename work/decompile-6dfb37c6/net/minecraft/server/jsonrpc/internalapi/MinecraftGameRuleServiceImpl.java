package net.minecraft.server.jsonrpc.internalapi;

import java.util.stream.Stream;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class MinecraftGameRuleServiceImpl implements MinecraftGameRuleService {

    private final DedicatedServer server;
    private final GameRules gameRules;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftGameRuleServiceImpl(DedicatedServer server, JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.gameRules = server.getWorldData().getGameRules();
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public <T> GameRulesService.GameRuleUpdate<T> updateGameRule(GameRulesService.GameRuleUpdate<T> update, ClientInfo clientInfo) {
        GameRule<T> gamerule = update.gameRule();
        T t0 = (T) this.gameRules.get(gamerule);
        T t1 = update.value();

        this.gameRules.set(gamerule, t1, this.server);
        this.jsonrpcLogger.log(clientInfo, "Game rule '{}' updated from '{}' to '{}'", gamerule.id(), gamerule.serialize(t0), gamerule.serialize(t1));
        return update;
    }

    @Override
    public <T> GameRulesService.GameRuleUpdate<T> getTypedRule(GameRule<T> gameRule, T value) {
        return new GameRulesService.GameRuleUpdate<T>(gameRule, value);
    }

    @Override
    public Stream<GameRule<?>> getAvailableGameRules() {
        return this.gameRules.availableRules();
    }

    @Override
    public <T> T getRuleValue(GameRule<T> gameRule) {
        return (T) this.gameRules.get(gameRule);
    }
}
