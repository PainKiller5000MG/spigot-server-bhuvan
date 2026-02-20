package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.ContextChain;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.FallthroughTask;

public class ReturnCommand {

    public ReturnCommand() {}

    public static <T extends ExecutionCommandSource<T>> void register(CommandDispatcher<T> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) LiteralArgumentBuilder.literal("return").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(RequiredArgumentBuilder.argument("value", IntegerArgumentType.integer()).executes(new ReturnCommand.ReturnValueCustomExecutor()))).then(LiteralArgumentBuilder.literal("fail").executes(new ReturnCommand.ReturnFailCustomExecutor()))).then(LiteralArgumentBuilder.literal("run").forward(dispatcher.getRoot(), new ReturnCommand.ReturnFromCommandCustomModifier(), false)));
    }

    private static class ReturnValueCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {

        private ReturnValueCustomExecutor() {}

        public void run(T sender, ContextChain<T> currentStep, ChainModifiers modifiers, ExecutionControl<T> output) {
            int i = IntegerArgumentType.getInteger(currentStep.getTopContext(), "value");

            sender.callback().onSuccess(i);
            Frame frame = output.currentFrame();

            frame.returnSuccess(i);
            frame.discard();
        }
    }

    private static class ReturnFailCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {

        private ReturnFailCustomExecutor() {}

        public void run(T sender, ContextChain<T> currentStep, ChainModifiers modifiers, ExecutionControl<T> output) {
            sender.callback().onFailure();
            Frame frame = output.currentFrame();

            frame.returnFailure();
            frame.discard();
        }
    }

    private static class ReturnFromCommandCustomModifier<T extends ExecutionCommandSource<T>> implements CustomModifierExecutor.ModifierAdapter<T> {

        private ReturnFromCommandCustomModifier() {}

        public void apply(T originalSource, List<T> currentSources, ContextChain<T> currentStep, ChainModifiers modifiers, ExecutionControl<T> output) {
            if (currentSources.isEmpty()) {
                if (modifiers.isReturn()) {
                    output.queueNext(FallthroughTask.instance());
                }

            } else {
                output.currentFrame().discard();
                ContextChain<T> contextchain1 = currentStep.nextStage();
                String s = contextchain1.getTopContext().getInput();

                output.queueNext(new BuildContexts.Continuation(s, contextchain1, modifiers.setReturn(), originalSource, currentSources));
            }
        }
    }
}
