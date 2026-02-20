package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;

public class JfrCommand {

    private static final SimpleCommandExceptionType START_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.jfr.start.failed"));
    private static final DynamicCommandExceptionType DUMP_FAILED = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.jfr.dump.failed", object);
    });

    private JfrCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("jfr").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))).then(Commands.literal("start").executes((commandcontext) -> {
            return startJfr((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("stop").executes((commandcontext) -> {
            return stopJfr((CommandSourceStack) commandcontext.getSource());
        })));
    }

    private static int startJfr(CommandSourceStack source) throws CommandSyntaxException {
        Environment environment = Environment.from(source.getServer());

        if (!JvmProfiler.INSTANCE.start(environment)) {
            throw JfrCommand.START_FAILED.create();
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.jfr.started");
            }, false);
            return 1;
        }
    }

    private static int stopJfr(CommandSourceStack source) throws CommandSyntaxException {
        try {
            Path path = Paths.get(".").relativize(JvmProfiler.INSTANCE.stop().normalize());
            Path path1 = source.getServer().isPublished() && !SharedConstants.IS_RUNNING_IN_IDE ? path : path.toAbsolutePath();
            Component component = Component.literal(path.toString()).withStyle(ChatFormatting.UNDERLINE).withStyle((style) -> {
                return style.withClickEvent(new ClickEvent.CopyToClipboard(path1.toString())).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")));
            });

            source.sendSuccess(() -> {
                return Component.translatable("commands.jfr.stopped", component);
            }, false);
            return 1;
        } catch (Throwable throwable) {
            throw JfrCommand.DUMP_FAILED.create(throwable.getMessage());
        }
    }
}
