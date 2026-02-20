package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

public class SeedCommand {

    public SeedCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean checkPermissions) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("seed").requires(Commands.hasPermission(checkPermissions ? Commands.LEVEL_GAMEMASTERS : Commands.LEVEL_ALL))).executes((commandcontext) -> {
            long i = ((CommandSourceStack) commandcontext.getSource()).getLevel().getSeed();
            Component component = ComponentUtils.copyOnClickText(String.valueOf(i));

            ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                return Component.translatable("commands.seed.success", component);
            }, false);
            return (int) i;
        }));
    }
}
