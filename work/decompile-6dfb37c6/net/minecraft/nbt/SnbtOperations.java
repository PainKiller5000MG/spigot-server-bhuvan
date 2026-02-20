package net.minecraft.nbt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.SuggestionSupplier;
import org.jspecify.annotations.Nullable;

public class SnbtOperations {

    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_STRING_UUID = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_string_uuid")));
    private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_NUMBER_OR_BOOLEAN = DelayedException.create(new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_number_or_boolean")));
    public static final String BUILTIN_TRUE = "true";
    public static final String BUILTIN_FALSE = "false";
    public static final Map<SnbtOperations.BuiltinKey, SnbtOperations.BuiltinOperation> BUILTIN_OPERATIONS = Map.of(new SnbtOperations.BuiltinKey("bool", 1), new SnbtOperations.BuiltinOperation() {
        @Override
        public <T> T run(DynamicOps<T> ops, List<T> arguments, ParseState<StringReader> state) {
            Boolean obool = convert(ops, arguments.getFirst());

            if (obool == null) {
                state.errorCollector().store(state.mark(), SnbtOperations.ERROR_EXPECTED_NUMBER_OR_BOOLEAN);
                return null;
            } else {
                return (T) ops.createBoolean(obool);
            }
        }

        private static <T> @Nullable Boolean convert(DynamicOps<T> ops, T arg) {
            Optional<Boolean> optional = ops.getBooleanValue(arg).result();

            if (optional.isPresent()) {
                return (Boolean) optional.get();
            } else {
                Optional<Number> optional1 = ops.getNumberValue(arg).result();

                return optional1.isPresent() ? ((Number) optional1.get()).doubleValue() != 0.0D : null;
            }
        }
    }, new SnbtOperations.BuiltinKey("uuid", 1), new SnbtOperations.BuiltinOperation() {
        @Override
        public <T> T run(DynamicOps<T> ops, List<T> arguments, ParseState<StringReader> state) {
            Optional<String> optional = ops.getStringValue(arguments.getFirst()).result();

            if (optional.isEmpty()) {
                state.errorCollector().store(state.mark(), SnbtOperations.ERROR_EXPECTED_STRING_UUID);
                return null;
            } else {
                UUID uuid;

                try {
                    uuid = UUID.fromString((String) optional.get());
                } catch (IllegalArgumentException illegalargumentexception) {
                    state.errorCollector().store(state.mark(), SnbtOperations.ERROR_EXPECTED_STRING_UUID);
                    return null;
                }

                return (T) ops.createIntList(IntStream.of(UUIDUtil.uuidToIntArray(uuid)));
            }
        }
    });
    public static final SuggestionSupplier<StringReader> BUILTIN_IDS = new SuggestionSupplier<StringReader>() {
        private final Set<String> keys;

        {
            this.keys = (Set) Stream.concat(Stream.of("false", "true"), SnbtOperations.BUILTIN_OPERATIONS.keySet().stream().map(SnbtOperations.BuiltinKey::id)).collect(Collectors.toSet());
        }

        @Override
        public Stream<String> possibleValues(ParseState<StringReader> state) {
            return this.keys.stream();
        }
    };

    public SnbtOperations() {}

    public static record BuiltinKey(String id, int argCount) {

        public String toString() {
            return this.id + "/" + this.argCount;
        }
    }

    public interface BuiltinOperation {

        <T> @Nullable T run(DynamicOps<T> ops, List<T> arguments, ParseState<StringReader> state);
    }
}
