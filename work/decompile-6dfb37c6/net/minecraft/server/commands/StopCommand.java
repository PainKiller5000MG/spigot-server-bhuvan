package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class StopCommand {

    public StopCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("stop").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))).executes((commandcontext) -> {
            ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                return Component.translatable("commands.stop.stopping");
            }, true);
            ((CommandSourceStack) commandcontext.getSource()).getServer().halt(false);
            return 1;
        }));
    }
}
