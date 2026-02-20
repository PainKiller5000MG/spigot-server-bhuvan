package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

public class DebugPathCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_MOB = new SimpleCommandExceptionType(Component.literal("Source is not a mob"));
    private static final SimpleCommandExceptionType ERROR_NO_PATH = new SimpleCommandExceptionType(Component.literal("Path not found"));
    private static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Component.literal("Target not reached"));

    public DebugPathCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("debugpath").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.argument("to", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return fillBlocks((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "to"));
        })));
    }

    private static int fillBlocks(CommandSourceStack source, BlockPos target) throws CommandSyntaxException {
        Entity entity = source.getEntity();

        if (!(entity instanceof Mob mob)) {
            throw DebugPathCommand.ERROR_NOT_MOB.create();
        } else {
            PathNavigation pathnavigation = new GroundPathNavigation(mob, source.getLevel());
            Path path = pathnavigation.createPath(target, 0);

            if (path == null) {
                throw DebugPathCommand.ERROR_NO_PATH.create();
            } else if (!path.canReach()) {
                throw DebugPathCommand.ERROR_NOT_COMPLETE.create();
            } else {
                source.sendSuccess(() -> {
                    return Component.literal("Made path");
                }, true);
                return 1;
            }
        }
    }
}
