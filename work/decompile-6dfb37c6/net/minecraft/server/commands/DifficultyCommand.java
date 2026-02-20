package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

public class DifficultyCommand {

    private static final DynamicCommandExceptionType ERROR_ALREADY_DIFFICULT = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.difficulty.failure", object);
    });

    public DifficultyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("difficulty");

        for (Difficulty difficulty : Difficulty.values()) {
            literalargumentbuilder.then(Commands.literal(difficulty.getKey()).executes((commandcontext) -> {
                return setDifficulty((CommandSourceStack) commandcontext.getSource(), difficulty);
            }));
        }

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) literalargumentbuilder.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            Difficulty difficulty1 = ((CommandSourceStack) commandcontext.getSource()).getLevel().getDifficulty();

            ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                return Component.translatable("commands.difficulty.query", difficulty1.getDisplayName());
            }, false);
            return difficulty1.getId();
        }));
    }

    public static int setDifficulty(CommandSourceStack source, Difficulty difficulty) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();

        if (minecraftserver.getWorldData().getDifficulty() == difficulty) {
            throw DifficultyCommand.ERROR_ALREADY_DIFFICULT.create(difficulty.getKey());
        } else {
            minecraftserver.setDifficulty(difficulty, true);
            source.sendSuccess(() -> {
                return Component.translatable("commands.difficulty.success", difficulty.getDisplayName());
            }, true);
            return 0;
        }
    }
}
