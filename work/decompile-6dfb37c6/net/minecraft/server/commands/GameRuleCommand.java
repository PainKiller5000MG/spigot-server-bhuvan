package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;

public class GameRuleCommand {

    public GameRuleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        final LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = (LiteralArgumentBuilder) Commands.literal("gamerule").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        (new GameRules(context.enabledFeatures())).visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public <T> void visit(GameRule<T> gameRule) {
                LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder1 = Commands.literal(gameRule.id());
                LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder2 = Commands.literal(gameRule.getIdentifier().toString());

                ((LiteralArgumentBuilder) literalargumentbuilder.then(GameRuleCommand.buildRuleArguments(gameRule, literalargumentbuilder1))).then(GameRuleCommand.buildRuleArguments(gameRule, literalargumentbuilder2));
            }
        });
        dispatcher.register(literalargumentbuilder);
    }

    private static <T> LiteralArgumentBuilder<CommandSourceStack> buildRuleArguments(GameRule<T> gameRule, LiteralArgumentBuilder<CommandSourceStack> ruleLiteral) {
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder) ruleLiteral.executes((commandcontext) -> {
            return queryRule((CommandSourceStack) commandcontext.getSource(), gameRule);
        })).then(Commands.argument("value", gameRule.argument()).executes((commandcontext) -> {
            return setRule(commandcontext, gameRule);
        }));
    }

    private static <T> int setRule(CommandContext<CommandSourceStack> context, GameRule<T> gameRule) {
        CommandSourceStack commandsourcestack = (CommandSourceStack) context.getSource();
        T t0 = (T) context.getArgument("value", gameRule.valueClass());

        commandsourcestack.getLevel().getGameRules().set(gameRule, t0, ((CommandSourceStack) context.getSource()).getServer());
        commandsourcestack.sendSuccess(() -> {
            return Component.translatable("commands.gamerule.set", gameRule.id(), gameRule.serialize(t0));
        }, true);
        return gameRule.getCommandResult(t0);
    }

    private static <T> int queryRule(CommandSourceStack source, GameRule<T> gameRule) {
        T t0 = (T) source.getLevel().getGameRules().get(gameRule);

        source.sendSuccess(() -> {
            return Component.translatable("commands.gamerule.query", gameRule.id(), gameRule.serialize(t0));
        }, false);
        return gameRule.getCommandResult(t0);
    }
}
