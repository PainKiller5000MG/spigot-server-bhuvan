package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfileResults;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class DebugCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.debug.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.debug.alreadyRunning"));
    private static final SimpleCommandExceptionType NO_RECURSIVE_TRACES = new SimpleCommandExceptionType(Component.translatable("commands.debug.function.noRecursion"));
    private static final SimpleCommandExceptionType NO_RETURN_RUN = new SimpleCommandExceptionType(Component.translatable("commands.debug.function.noReturnRun"));

    public DebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("debug").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.literal("start").executes((commandcontext) -> {
            return start((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("stop").executes((commandcontext) -> {
            return stop((CommandSourceStack) commandcontext.getSource());
        }))).then(((LiteralArgumentBuilder) Commands.literal("function").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.argument("name", FunctionArgument.functions()).suggests(FunctionCommand.SUGGEST_FUNCTION).executes(new DebugCommand.TraceCustomExecutor()))));
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();

        if (minecraftserver.isTimeProfilerRunning()) {
            throw DebugCommand.ERROR_ALREADY_RUNNING.create();
        } else {
            minecraftserver.startTimeProfiler();
            source.sendSuccess(() -> {
                return Component.translatable("commands.debug.started");
            }, true);
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();

        if (!minecraftserver.isTimeProfilerRunning()) {
            throw DebugCommand.ERROR_NOT_RUNNING.create();
        } else {
            ProfileResults profileresults = minecraftserver.stopTimeProfiler();
            double d0 = (double) profileresults.getNanoDuration() / (double) TimeUtil.NANOSECONDS_PER_SECOND;
            double d1 = (double) profileresults.getTickDuration() / d0;

            source.sendSuccess(() -> {
                return Component.translatable("commands.debug.stopped", String.format(Locale.ROOT, "%.2f", d0), profileresults.getTickDuration(), String.format(Locale.ROOT, "%.2f", d1));
            }, true);
            return (int) d1;
        }
    }

    private static class TraceCustomExecutor extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack> implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {

        private TraceCustomExecutor() {}

        public void runGuarded(CommandSourceStack source, ContextChain<CommandSourceStack> currentStep, ChainModifiers modifiers, ExecutionControl<CommandSourceStack> context) throws CommandSyntaxException {
            if (modifiers.isReturn()) {
                throw DebugCommand.NO_RETURN_RUN.create();
            } else if (context.tracer() != null) {
                throw DebugCommand.NO_RECURSIVE_TRACES.create();
            } else {
                CommandContext<CommandSourceStack> commandcontext = currentStep.getTopContext();
                Collection<CommandFunction<CommandSourceStack>> collection = FunctionArgument.getFunctions(commandcontext, "name");
                MinecraftServer minecraftserver = source.getServer();
                String s = "debug-trace-" + Util.getFilenameFormattedDateTime() + ".txt";
                CommandDispatcher<CommandSourceStack> commanddispatcher = source.getServer().getFunctions().getDispatcher();
                int i = 0;

                try {
                    Path path = minecraftserver.getFile("debug");

                    Files.createDirectories(path);
                    final PrintWriter printwriter = new PrintWriter(Files.newBufferedWriter(path.resolve(s), StandardCharsets.UTF_8));
                    DebugCommand.Tracer debugcommand_tracer = new DebugCommand.Tracer(printwriter);

                    context.tracer(debugcommand_tracer);

                    for (final CommandFunction<CommandSourceStack> commandfunction : collection) {
                        try {
                            CommandSourceStack commandsourcestack1 = source.withSource(debugcommand_tracer).withMaximumPermission(LevelBasedPermissionSet.GAMEMASTER);
                            InstantiatedFunction<CommandSourceStack> instantiatedfunction = commandfunction.instantiate((CompoundTag) null, commanddispatcher);

                            context.queueNext((new CallFunction<CommandSourceStack>(instantiatedfunction, CommandResultCallback.EMPTY, false) {
                                public void execute(CommandSourceStack sender, ExecutionContext<CommandSourceStack> context, Frame frame) {
                                    printwriter.println(commandfunction.id());
                                    super.execute(sender, context, frame);
                                }
                            }).bind(commandsourcestack1));
                            i += instantiatedfunction.entries().size();
                        } catch (FunctionInstantiationException functioninstantiationexception) {
                            source.sendFailure(functioninstantiationexception.messageComponent());
                        }
                    }
                } catch (IOException | UncheckedIOException uncheckedioexception) {
                    DebugCommand.LOGGER.warn("Tracing failed", uncheckedioexception);
                    source.sendFailure(Component.translatable("commands.debug.function.traceFailed"));
                }

                context.queueNext((executioncontext, frame) -> {
                    if (collection.size() == 1) {
                        source.sendSuccess(() -> {
                            return Component.translatable("commands.debug.function.success.single", i, Component.translationArg(((CommandFunction) collection.iterator().next()).id()), s);
                        }, true);
                    } else {
                        source.sendSuccess(() -> {
                            return Component.translatable("commands.debug.function.success.multiple", i, collection.size(), s);
                        }, true);
                    }

                });
            }
        }
    }

    private static class Tracer implements CommandSource, TraceCallbacks {

        public static final int INDENT_OFFSET = 1;
        private final PrintWriter output;
        private int lastIndent;
        private boolean waitingForResult;

        private Tracer(PrintWriter output) {
            this.output = output;
        }

        private void indentAndSave(int value) {
            this.printIndent(value);
            this.lastIndent = value;
        }

        private void printIndent(int value) {
            for (int j = 0; j < value + 1; ++j) {
                this.output.write("    ");
            }

        }

        private void newLine() {
            if (this.waitingForResult) {
                this.output.println();
                this.waitingForResult = false;
            }

        }

        @Override
        public void onCommand(int depth, String command) {
            this.newLine();
            this.indentAndSave(depth);
            this.output.print("[C] ");
            this.output.print(command);
            this.waitingForResult = true;
        }

        @Override
        public void onReturn(int depth, String command, int result) {
            if (this.waitingForResult) {
                this.output.print(" -> ");
                this.output.println(result);
                this.waitingForResult = false;
            } else {
                this.indentAndSave(depth);
                this.output.print("[R = ");
                this.output.print(result);
                this.output.print("] ");
                this.output.println(command);
            }

        }

        @Override
        public void onCall(int depth, Identifier function, int size) {
            this.newLine();
            this.indentAndSave(depth);
            this.output.print("[F] ");
            this.output.print(function);
            this.output.print(" size=");
            this.output.println(size);
        }

        @Override
        public void onError(String message) {
            this.newLine();
            this.indentAndSave(this.lastIndent + 1);
            this.output.print("[E] ");
            this.output.print(message);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.newLine();
            this.printIndent(this.lastIndent + 1);
            this.output.print("[M] ");
            this.output.println(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean alwaysAccepts() {
            return true;
        }

        @Override
        public void close() {
            IOUtils.closeQuietly(this.output);
        }
    }
}
