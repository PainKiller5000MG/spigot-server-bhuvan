package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface CommandFunction<T> {

    Identifier id();

    InstantiatedFunction<T> instantiate(@Nullable CompoundTag arguments, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException;

    private static boolean shouldConcatenateNextLine(CharSequence line) {
        int i = line.length();

        return i > 0 && line.charAt(i - 1) == '\\';
    }

    static <T extends ExecutionCommandSource<T>> CommandFunction<T> fromLines(Identifier id, CommandDispatcher<T> dispatcher, T compilationContext, List<String> lines) {
        FunctionBuilder<T> functionbuilder = new FunctionBuilder<T>();

        for (int i = 0; i < lines.size(); ++i) {
            int j = i + 1;
            String s = ((String) lines.get(i)).trim();
            String s1;

            if (shouldConcatenateNextLine(s)) {
                StringBuilder stringbuilder = new StringBuilder(s);

                do {
                    ++i;
                    if (i == lines.size()) {
                        throw new IllegalArgumentException("Line continuation at end of file");
                    }

                    stringbuilder.deleteCharAt(stringbuilder.length() - 1);
                    String s2 = ((String) lines.get(i)).trim();

                    stringbuilder.append(s2);
                    checkCommandLineLength(stringbuilder);
                } while (shouldConcatenateNextLine(stringbuilder));

                s1 = stringbuilder.toString();
            } else {
                s1 = s;
            }

            checkCommandLineLength(s1);
            StringReader stringreader = new StringReader(s1);

            if (stringreader.canRead() && stringreader.peek() != '#') {
                if (stringreader.peek() == '/') {
                    stringreader.skip();
                    if (stringreader.peek() == '/') {
                        throw new IllegalArgumentException("Unknown or invalid command '" + s1 + "' on line " + j + " (if you intended to make a comment, use '#' not '//')");
                    }

                    String s3 = stringreader.readUnquotedString();

                    throw new IllegalArgumentException("Unknown or invalid command '" + s1 + "' on line " + j + " (did you mean '" + s3 + "'? Do not use a preceding forwards slash.)");
                }

                if (stringreader.peek() == '$') {
                    functionbuilder.addMacro(s1.substring(1), j, compilationContext);
                } else {
                    try {
                        functionbuilder.addCommand(parseCommand(dispatcher, compilationContext, stringreader));
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        throw new IllegalArgumentException("Whilst parsing command on line " + j + ": " + commandsyntaxexception.getMessage());
                    }
                }
            }
        }

        return functionbuilder.build(id);
    }

    static void checkCommandLineLength(CharSequence line) {
        if (line.length() > 2000000) {
            CharSequence charsequence1 = line.subSequence(0, Math.min(512, 2000000));
            int i = line.length();

            throw new IllegalStateException("Command too long: " + i + " characters, contents: " + String.valueOf(charsequence1) + "...");
        }
    }

    static <T extends ExecutionCommandSource<T>> UnboundEntryAction<T> parseCommand(CommandDispatcher<T> dispatcher, T compilationContext, StringReader input) throws CommandSyntaxException {
        ParseResults<T> parseresults = dispatcher.parse(input, compilationContext);

        Commands.validateParseResults(parseresults);
        Optional<ContextChain<T>> optional = ContextChain.tryFlatten(parseresults.getContext().build(input.getString()));

        if (optional.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseresults.getReader());
        } else {
            return new BuildContexts.Unbound<T>(input.getString(), (ContextChain) optional.get());
        }
    }
}
