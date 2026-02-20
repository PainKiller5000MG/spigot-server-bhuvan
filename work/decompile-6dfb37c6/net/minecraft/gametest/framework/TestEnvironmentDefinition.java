package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

public interface TestEnvironmentDefinition {

    Codec<TestEnvironmentDefinition> DIRECT_CODEC = BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE.byNameCodec().dispatch(TestEnvironmentDefinition::codec, (mapcodec) -> {
        return mapcodec;
    });
    Codec<Holder<TestEnvironmentDefinition>> CODEC = RegistryFileCodec.<Holder<TestEnvironmentDefinition>>create(Registries.TEST_ENVIRONMENT, TestEnvironmentDefinition.DIRECT_CODEC);

    static MapCodec<? extends TestEnvironmentDefinition> bootstrap(Registry<MapCodec<? extends TestEnvironmentDefinition>> registry) {
        Registry.register(registry, "all_of", TestEnvironmentDefinition.AllOf.CODEC);
        Registry.register(registry, "game_rules", TestEnvironmentDefinition.SetGameRules.CODEC);
        Registry.register(registry, "time_of_day", TestEnvironmentDefinition.TimeOfDay.CODEC);
        Registry.register(registry, "weather", TestEnvironmentDefinition.Weather.CODEC);
        return (MapCodec) Registry.register(registry, "function", TestEnvironmentDefinition.Functions.CODEC);
    }

    void setup(ServerLevel level);

    default void teardown(ServerLevel level) {}

    MapCodec<? extends TestEnvironmentDefinition> codec();

    public static record Weather(TestEnvironmentDefinition.Weather.Type weather) implements TestEnvironmentDefinition {

        public static final MapCodec<TestEnvironmentDefinition.Weather> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(TestEnvironmentDefinition.Weather.Type.CODEC.fieldOf("weather").forGetter(TestEnvironmentDefinition.Weather::weather)).apply(instance, TestEnvironmentDefinition.Weather::new);
        });

        @Override
        public void setup(ServerLevel level) {
            this.weather.apply(level);
        }

        @Override
        public void teardown(ServerLevel level) {
            level.resetWeatherCycle();
        }

        @Override
        public MapCodec<TestEnvironmentDefinition.Weather> codec() {
            return TestEnvironmentDefinition.Weather.CODEC;
        }

        public static enum Type implements StringRepresentable {

            CLEAR("clear", 100000, 0, false, false), RAIN("rain", 0, 100000, true, false), THUNDER("thunder", 0, 100000, true, true);

            public static final Codec<TestEnvironmentDefinition.Weather.Type> CODEC = StringRepresentable.<TestEnvironmentDefinition.Weather.Type>fromEnum(TestEnvironmentDefinition.Weather.Type::values);
            private final String id;
            private final int clearTime;
            private final int rainTime;
            private final boolean raining;
            private final boolean thundering;

            private Type(String id, int clearTime, int rainTime, boolean raining, boolean thundering) {
                this.id = id;
                this.clearTime = clearTime;
                this.rainTime = rainTime;
                this.raining = raining;
                this.thundering = thundering;
            }

            void apply(ServerLevel level) {
                level.setWeatherParameters(this.clearTime, this.rainTime, this.raining, this.thundering);
            }

            @Override
            public String getSerializedName() {
                return this.id;
            }
        }
    }

    public static record TimeOfDay(int time) implements TestEnvironmentDefinition {

        public static final MapCodec<TestEnvironmentDefinition.TimeOfDay> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("time").forGetter(TestEnvironmentDefinition.TimeOfDay::time)).apply(instance, TestEnvironmentDefinition.TimeOfDay::new);
        });

        @Override
        public void setup(ServerLevel level) {
            level.setDayTime((long) this.time);
        }

        @Override
        public MapCodec<TestEnvironmentDefinition.TimeOfDay> codec() {
            return TestEnvironmentDefinition.TimeOfDay.CODEC;
        }
    }

    public static record SetGameRules(GameRuleMap gameRulesMap) implements TestEnvironmentDefinition {

        public static final MapCodec<TestEnvironmentDefinition.SetGameRules> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(GameRuleMap.CODEC.fieldOf("rules").forGetter(TestEnvironmentDefinition.SetGameRules::gameRulesMap)).apply(instance, TestEnvironmentDefinition.SetGameRules::new);
        });

        @Override
        public void setup(ServerLevel level) {
            GameRules gamerules = level.getGameRules();
            MinecraftServer minecraftserver = level.getServer();

            gamerules.setAll(this.gameRulesMap, minecraftserver);
        }

        @Override
        public void teardown(ServerLevel level) {
            this.gameRulesMap.keySet().forEach((gamerule) -> {
                this.resetRule(level, gamerule);
            });
        }

        private <T> void resetRule(ServerLevel level, GameRule<T> gameRule) {
            level.getGameRules().set(gameRule, gameRule.defaultValue(), level.getServer());
        }

        @Override
        public MapCodec<TestEnvironmentDefinition.SetGameRules> codec() {
            return TestEnvironmentDefinition.SetGameRules.CODEC;
        }
    }

    public static record Functions(Optional<Identifier> setupFunction, Optional<Identifier> teardownFunction) implements TestEnvironmentDefinition {

        private static final Logger LOGGER = LogUtils.getLogger();
        public static final MapCodec<TestEnvironmentDefinition.Functions> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Identifier.CODEC.optionalFieldOf("setup").forGetter(TestEnvironmentDefinition.Functions::setupFunction), Identifier.CODEC.optionalFieldOf("teardown").forGetter(TestEnvironmentDefinition.Functions::teardownFunction)).apply(instance, TestEnvironmentDefinition.Functions::new);
        });

        @Override
        public void setup(ServerLevel level) {
            this.setupFunction.ifPresent((identifier) -> {
                run(level, identifier);
            });
        }

        @Override
        public void teardown(ServerLevel level) {
            this.teardownFunction.ifPresent((identifier) -> {
                run(level, identifier);
            });
        }

        private static void run(ServerLevel level, Identifier functionId) {
            MinecraftServer minecraftserver = level.getServer();
            ServerFunctionManager serverfunctionmanager = minecraftserver.getFunctions();
            Optional<CommandFunction<CommandSourceStack>> optional = serverfunctionmanager.get(functionId);

            if (optional.isPresent()) {
                CommandSourceStack commandsourcestack = minecraftserver.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput().withLevel(level);

                serverfunctionmanager.execute((CommandFunction) optional.get(), commandsourcestack);
            } else {
                TestEnvironmentDefinition.Functions.LOGGER.error("Test Batch failed for non-existent function {}", functionId);
            }

        }

        @Override
        public MapCodec<TestEnvironmentDefinition.Functions> codec() {
            return TestEnvironmentDefinition.Functions.CODEC;
        }
    }

    public static record AllOf(List<Holder<TestEnvironmentDefinition>> definitions) implements TestEnvironmentDefinition {

        public static final MapCodec<TestEnvironmentDefinition.AllOf> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(TestEnvironmentDefinition.CODEC.listOf().fieldOf("definitions").forGetter(TestEnvironmentDefinition.AllOf::definitions)).apply(instance, TestEnvironmentDefinition.AllOf::new);
        });

        public AllOf(TestEnvironmentDefinition... defs) {
            this(Arrays.stream(defs).map(Holder::direct).toList());
        }

        @Override
        public void setup(ServerLevel level) {
            this.definitions.forEach((holder) -> {
                ((TestEnvironmentDefinition) holder.value()).setup(level);
            });
        }

        @Override
        public void teardown(ServerLevel level) {
            this.definitions.forEach((holder) -> {
                ((TestEnvironmentDefinition) holder.value()).teardown(level);
            });
        }

        @Override
        public MapCodec<TestEnvironmentDefinition.AllOf> codec() {
            return TestEnvironmentDefinition.AllOf.CODEC;
        }
    }
}
