package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class KillCommand {

    public KillCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("kill").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            return kill((CommandSourceStack) commandcontext.getSource(), ImmutableList.of(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()));
        })).then(Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return kill((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"));
        })));
    }

    private static int kill(CommandSourceStack source, Collection<? extends Entity> victims) {
        for (Entity entity : victims) {
            entity.kill(source.getLevel());
        }

        if (victims.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.kill.success.single", ((Entity) victims.iterator().next()).getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.kill.success.multiple", victims.size());
            }, true);
        }

        return victims.size();
    }
}
