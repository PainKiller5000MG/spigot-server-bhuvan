package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

public class DebugMobSpawningCommand {

    public DebugMobSpawningCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = (LiteralArgumentBuilder) Commands.literal("debugmobspawning").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        for (MobCategory mobcategory : MobCategory.values()) {
            literalargumentbuilder.then(Commands.literal(mobcategory.getName()).then(Commands.argument("at", BlockPosArgument.blockPos()).executes((commandcontext) -> {
                return spawnMobs((CommandSourceStack) commandcontext.getSource(), mobcategory, BlockPosArgument.getLoadedBlockPos(commandcontext, "at"));
            })));
        }

        dispatcher.register(literalargumentbuilder);
    }

    private static int spawnMobs(CommandSourceStack source, MobCategory mobCategory, BlockPos at) {
        NaturalSpawner.spawnCategoryForPosition(mobCategory, source.getLevel(), at);
        return 1;
    }
}
