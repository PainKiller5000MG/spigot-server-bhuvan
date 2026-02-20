package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetPlayerIdleTimeoutCommand {

    public SetPlayerIdleTimeoutCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("setidletimeout").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.argument("minutes", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return setIdleTimeout((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "minutes"));
        })));
    }

    private static int setIdleTimeout(CommandSourceStack source, int time) {
        source.getServer().setPlayerIdleTimeout(time);
        if (time > 0) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.setidletimeout.success", time);
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.setidletimeout.success.disabled");
            }, true);
        }

        return time;
    }
}
