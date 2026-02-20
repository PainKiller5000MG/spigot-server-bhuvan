package net.minecraft.commands;

import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

public class BrigadierExceptions implements BuiltInExceptionProvider {

    private static final Dynamic2CommandExceptionType DOUBLE_TOO_SMALL = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.double.low", object1, object);
    });
    private static final Dynamic2CommandExceptionType DOUBLE_TOO_BIG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.double.big", object1, object);
    });
    private static final Dynamic2CommandExceptionType FLOAT_TOO_SMALL = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.float.low", object1, object);
    });
    private static final Dynamic2CommandExceptionType FLOAT_TOO_BIG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.float.big", object1, object);
    });
    private static final Dynamic2CommandExceptionType INTEGER_TOO_SMALL = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.integer.low", object1, object);
    });
    private static final Dynamic2CommandExceptionType INTEGER_TOO_BIG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.integer.big", object1, object);
    });
    private static final Dynamic2CommandExceptionType LONG_TOO_SMALL = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.long.low", object1, object);
    });
    private static final Dynamic2CommandExceptionType LONG_TOO_BIG = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatableEscape("argument.long.big", object1, object);
    });
    private static final DynamicCommandExceptionType LITERAL_INCORRECT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("argument.literal.incorrect", object);
    });
    private static final SimpleCommandExceptionType READER_EXPECTED_START_OF_QUOTE = new SimpleCommandExceptionType(Component.translatable("parsing.quote.expected.start"));
    private static final SimpleCommandExceptionType READER_EXPECTED_END_OF_QUOTE = new SimpleCommandExceptionType(Component.translatable("parsing.quote.expected.end"));
    private static final DynamicCommandExceptionType READER_INVALID_ESCAPE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.quote.escape", object);
    });
    private static final DynamicCommandExceptionType READER_INVALID_BOOL = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.bool.invalid", object);
    });
    private static final DynamicCommandExceptionType READER_INVALID_INT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.int.invalid", object);
    });
    private static final SimpleCommandExceptionType READER_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("parsing.int.expected"));
    private static final DynamicCommandExceptionType READER_INVALID_LONG = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.long.invalid", object);
    });
    private static final SimpleCommandExceptionType READER_EXPECTED_LONG = new SimpleCommandExceptionType(Component.translatable("parsing.long.expected"));
    private static final DynamicCommandExceptionType READER_INVALID_DOUBLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.double.invalid", object);
    });
    private static final SimpleCommandExceptionType READER_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("parsing.double.expected"));
    private static final DynamicCommandExceptionType READER_INVALID_FLOAT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.float.invalid", object);
    });
    private static final SimpleCommandExceptionType READER_EXPECTED_FLOAT = new SimpleCommandExceptionType(Component.translatable("parsing.float.expected"));
    private static final SimpleCommandExceptionType READER_EXPECTED_BOOL = new SimpleCommandExceptionType(Component.translatable("parsing.bool.expected"));
    private static final DynamicCommandExceptionType READER_EXPECTED_SYMBOL = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("parsing.expected", object);
    });
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_COMMAND = new SimpleCommandExceptionType(Component.translatable("command.unknown.command"));
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_ARGUMENT = new SimpleCommandExceptionType(Component.translatable("command.unknown.argument"));
    private static final SimpleCommandExceptionType DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR = new SimpleCommandExceptionType(Component.translatable("command.expected.separator"));
    private static final DynamicCommandExceptionType DISPATCHER_PARSE_EXCEPTION = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("command.exception", object);
    });

    public BrigadierExceptions() {}

    public Dynamic2CommandExceptionType doubleTooLow() {
        return BrigadierExceptions.DOUBLE_TOO_SMALL;
    }

    public Dynamic2CommandExceptionType doubleTooHigh() {
        return BrigadierExceptions.DOUBLE_TOO_BIG;
    }

    public Dynamic2CommandExceptionType floatTooLow() {
        return BrigadierExceptions.FLOAT_TOO_SMALL;
    }

    public Dynamic2CommandExceptionType floatTooHigh() {
        return BrigadierExceptions.FLOAT_TOO_BIG;
    }

    public Dynamic2CommandExceptionType integerTooLow() {
        return BrigadierExceptions.INTEGER_TOO_SMALL;
    }

    public Dynamic2CommandExceptionType integerTooHigh() {
        return BrigadierExceptions.INTEGER_TOO_BIG;
    }

    public Dynamic2CommandExceptionType longTooLow() {
        return BrigadierExceptions.LONG_TOO_SMALL;
    }

    public Dynamic2CommandExceptionType longTooHigh() {
        return BrigadierExceptions.LONG_TOO_BIG;
    }

    public DynamicCommandExceptionType literalIncorrect() {
        return BrigadierExceptions.LITERAL_INCORRECT;
    }

    public SimpleCommandExceptionType readerExpectedStartOfQuote() {
        return BrigadierExceptions.READER_EXPECTED_START_OF_QUOTE;
    }

    public SimpleCommandExceptionType readerExpectedEndOfQuote() {
        return BrigadierExceptions.READER_EXPECTED_END_OF_QUOTE;
    }

    public DynamicCommandExceptionType readerInvalidEscape() {
        return BrigadierExceptions.READER_INVALID_ESCAPE;
    }

    public DynamicCommandExceptionType readerInvalidBool() {
        return BrigadierExceptions.READER_INVALID_BOOL;
    }

    public DynamicCommandExceptionType readerInvalidInt() {
        return BrigadierExceptions.READER_INVALID_INT;
    }

    public SimpleCommandExceptionType readerExpectedInt() {
        return BrigadierExceptions.READER_EXPECTED_INT;
    }

    public DynamicCommandExceptionType readerInvalidLong() {
        return BrigadierExceptions.READER_INVALID_LONG;
    }

    public SimpleCommandExceptionType readerExpectedLong() {
        return BrigadierExceptions.READER_EXPECTED_LONG;
    }

    public DynamicCommandExceptionType readerInvalidDouble() {
        return BrigadierExceptions.READER_INVALID_DOUBLE;
    }

    public SimpleCommandExceptionType readerExpectedDouble() {
        return BrigadierExceptions.READER_EXPECTED_DOUBLE;
    }

    public DynamicCommandExceptionType readerInvalidFloat() {
        return BrigadierExceptions.READER_INVALID_FLOAT;
    }

    public SimpleCommandExceptionType readerExpectedFloat() {
        return BrigadierExceptions.READER_EXPECTED_FLOAT;
    }

    public SimpleCommandExceptionType readerExpectedBool() {
        return BrigadierExceptions.READER_EXPECTED_BOOL;
    }

    public DynamicCommandExceptionType readerExpectedSymbol() {
        return BrigadierExceptions.READER_EXPECTED_SYMBOL;
    }

    public SimpleCommandExceptionType dispatcherUnknownCommand() {
        return BrigadierExceptions.DISPATCHER_UNKNOWN_COMMAND;
    }

    public SimpleCommandExceptionType dispatcherUnknownArgument() {
        return BrigadierExceptions.DISPATCHER_UNKNOWN_ARGUMENT;
    }

    public SimpleCommandExceptionType dispatcherExpectedArgumentSeparator() {
        return BrigadierExceptions.DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR;
    }

    public DynamicCommandExceptionType dispatcherParseException() {
        return BrigadierExceptions.DISPATCHER_PARSE_EXCEPTION;
    }
}
