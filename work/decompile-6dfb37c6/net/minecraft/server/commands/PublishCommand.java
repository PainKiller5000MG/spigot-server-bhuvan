package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

public class PublishCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.publish.failed"));
    private static final DynamicCommandExceptionType ERROR_ALREADY_PUBLISHED = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.publish.alreadyPublished", object);
    });

    public PublishCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("publish").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))).executes((commandcontext) -> {
            return publish((CommandSourceStack) commandcontext.getSource(), HttpUtil.getAvailablePort(), false, (GameType) null);
        })).then(((RequiredArgumentBuilder) Commands.argument("allowCommands", BoolArgumentType.bool()).executes((commandcontext) -> {
            return publish((CommandSourceStack) commandcontext.getSource(), HttpUtil.getAvailablePort(), BoolArgumentType.getBool(commandcontext, "allowCommands"), (GameType) null);
        })).then(((RequiredArgumentBuilder) Commands.argument("gamemode", GameModeArgument.gameMode()).executes((commandcontext) -> {
            return publish((CommandSourceStack) commandcontext.getSource(), HttpUtil.getAvailablePort(), BoolArgumentType.getBool(commandcontext, "allowCommands"), GameModeArgument.getGameMode(commandcontext, "gamemode"));
        })).then(Commands.argument("port", IntegerArgumentType.integer(0, 65535)).executes((commandcontext) -> {
            return publish((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "port"), BoolArgumentType.getBool(commandcontext, "allowCommands"), GameModeArgument.getGameMode(commandcontext, "gamemode"));
        })))));
    }

    private static int publish(CommandSourceStack source, int port, boolean allowCommands, @Nullable GameType type) throws CommandSyntaxException {
        if (source.getServer().isPublished()) {
            throw PublishCommand.ERROR_ALREADY_PUBLISHED.create(source.getServer().getPort());
        } else if (!source.getServer().publishServer(type, allowCommands, port)) {
            throw PublishCommand.ERROR_FAILED.create();
        } else {
            source.sendSuccess(() -> {
                return getSuccessMessage(port);
            }, true);
            return port;
        }
    }

    public static MutableComponent getSuccessMessage(int port) {
        Component component = ComponentUtils.copyOnClickText(String.valueOf(port));

        return Component.translatable("commands.publish.started", component);
    }
}
