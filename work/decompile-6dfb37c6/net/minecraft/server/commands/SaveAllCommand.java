package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class SaveAllCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.save.failed"));

    public SaveAllCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("save-all").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))).executes((commandcontext) -> {
            return saveAll((CommandSourceStack) commandcontext.getSource(), false);
        })).then(Commands.literal("flush").executes((commandcontext) -> {
            return saveAll((CommandSourceStack) commandcontext.getSource(), true);
        })));
    }

    private static int saveAll(CommandSourceStack source, boolean flush) throws CommandSyntaxException {
        source.sendSuccess(() -> {
            return Component.translatable("commands.save.saving");
        }, false);
        MinecraftServer minecraftserver = source.getServer();
        boolean flag1 = minecraftserver.saveEverything(true, flush, true);

        if (!flag1) {
            throw SaveAllCommand.ERROR_FAILED.create();
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.save.success");
            }, true);
            return 1;
        }
    }
}
