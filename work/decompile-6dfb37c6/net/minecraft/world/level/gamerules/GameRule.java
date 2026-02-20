package net.minecraft.world.level.gamerules;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;

public final class GameRule<T> implements FeatureElement {

    private final GameRuleCategory category;
    private final GameRuleType gameRuleType;
    private final ArgumentType<T> argument;
    private final GameRules.VisitorCaller<T> visitorCaller;
    private final Codec<T> valueCodec;
    private final ToIntFunction<T> commandResultFunction;
    private final T defaultValue;
    private final FeatureFlagSet requiredFeatures;

    public GameRule(GameRuleCategory category, GameRuleType gameRuleType, ArgumentType<T> argument, GameRules.VisitorCaller<T> visitorCaller, Codec<T> valueCodec, ToIntFunction<T> commandResultFunction, T defaultValue, FeatureFlagSet requiredFeatures) {
        this.category = category;
        this.gameRuleType = gameRuleType;
        this.argument = argument;
        this.visitorCaller = visitorCaller;
        this.valueCodec = valueCodec;
        this.commandResultFunction = commandResultFunction;
        this.defaultValue = defaultValue;
        this.requiredFeatures = requiredFeatures;
    }

    public String toString() {
        return this.id();
    }

    public String id() {
        return this.getIdentifier().toShortString();
    }

    public Identifier getIdentifier() {
        return (Identifier) Objects.requireNonNull(BuiltInRegistries.GAME_RULE.getKey(this));
    }

    public String getDescriptionId() {
        return Util.makeDescriptionId("gamerule", this.getIdentifier());
    }

    public String serialize(T value) {
        return value.toString();
    }

    public DataResult<T> deserialize(String value) {
        try {
            StringReader stringreader = new StringReader(value);
            T t0 = (T) this.argument.parse(stringreader);

            return stringreader.canRead() ? DataResult.error(() -> {
                return "Failed to deserialize; trailing characters";
            }, t0) : DataResult.success(t0);
        } catch (CommandSyntaxException commandsyntaxexception) {
            return DataResult.error(() -> {
                return "Failed to deserialize";
            });
        }
    }

    public Class<T> valueClass() {
        return this.defaultValue.getClass();
    }

    public void callVisitor(GameRuleTypeVisitor visitor) {
        this.visitorCaller.call(visitor, this);
    }

    public int getCommandResult(T value) {
        return this.commandResultFunction.applyAsInt(value);
    }

    public GameRuleCategory category() {
        return this.category;
    }

    public GameRuleType gameRuleType() {
        return this.gameRuleType;
    }

    public ArgumentType<T> argument() {
        return this.argument;
    }

    public Codec<T> valueCodec() {
        return this.valueCodec;
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }
}
