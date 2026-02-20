package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;

public class GameRulesService {

    public GameRulesService() {}

    public static List<GameRulesService.GameRuleUpdate<?>> get(MinecraftApi minecraftApi) {
        List<GameRulesService.GameRuleUpdate<?>> list = new ArrayList();

        minecraftApi.gameRuleService().getAvailableGameRules().forEach((gamerule) -> {
            addGameRule(minecraftApi, gamerule, list);
        });
        return list;
    }

    private static <T> void addGameRule(MinecraftApi minecraftApi, GameRule<T> gameRule, List<GameRulesService.GameRuleUpdate<?>> rules) {
        T t0 = (T) minecraftApi.gameRuleService().getRuleValue(gameRule);

        rules.add(getTypedRule(minecraftApi, gameRule, Objects.requireNonNull(t0)));
    }

    public static <T> GameRulesService.GameRuleUpdate<T> getTypedRule(MinecraftApi minecraftApi, GameRule<T> gameRule, T value) {
        return minecraftApi.gameRuleService().<T>getTypedRule(gameRule, value);
    }

    public static <T> GameRulesService.GameRuleUpdate<T> update(MinecraftApi minecraftApi, GameRulesService.GameRuleUpdate<T> update, ClientInfo clientInfo) {
        return minecraftApi.gameRuleService().<T>updateGameRule(update, clientInfo);
    }

    public static record GameRuleUpdate<T>(GameRule<T> gameRule, T value) {

        public static final Codec<GameRulesService.GameRuleUpdate<?>> TYPED_CODEC = BuiltInRegistries.GAME_RULE.byNameCodec().dispatch("key", GameRulesService.GameRuleUpdate::gameRule, GameRulesService.GameRuleUpdate::getValueAndTypeCodec);
        public static final Codec<GameRulesService.GameRuleUpdate<?>> CODEC = BuiltInRegistries.GAME_RULE.byNameCodec().dispatch("key", GameRulesService.GameRuleUpdate::gameRule, GameRulesService.GameRuleUpdate::getValueCodec);

        private static <T> MapCodec<? extends GameRulesService.GameRuleUpdate<T>> getValueCodec(GameRule<T> gameRule) {
            return gameRule.valueCodec().fieldOf("value").xmap((object) -> {
                return new GameRulesService.GameRuleUpdate(gameRule, object);
            }, GameRulesService.GameRuleUpdate::value);
        }

        private static <T> MapCodec<? extends GameRulesService.GameRuleUpdate<T>> getValueAndTypeCodec(GameRule<T> gameRule) {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(StringRepresentable.fromEnum(GameRuleType::values).fieldOf("type").forGetter((gamerulesservice_gameruleupdate) -> {
                    return gamerulesservice_gameruleupdate.gameRule.gameRuleType();
                }), gameRule.valueCodec().fieldOf("value").forGetter(GameRulesService.GameRuleUpdate::value)).apply(instance, (gameruletype, object) -> {
                    return getUntypedRule(gameRule, gameruletype, object);
                });
            });
        }

        private static <T> GameRulesService.GameRuleUpdate<T> getUntypedRule(GameRule<T> gameRule, GameRuleType readType, T value) {
            if (gameRule.gameRuleType() != readType) {
                String s = String.valueOf(readType);

                throw new InvalidParameterJsonRpcException("Stated type \"" + s + "\" mismatches with actual type \"" + String.valueOf(gameRule.gameRuleType()) + "\" of gamerule \"" + gameRule.id() + "\"");
            } else {
                return new GameRulesService.GameRuleUpdate<T>(gameRule, value);
            }
        }
    }
}
