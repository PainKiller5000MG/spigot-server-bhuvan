package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

public class VersionCommand {

    private static final Component HEADER = Component.translatable("commands.version.header");
    private static final Component STABLE = Component.translatable("commands.version.stable.yes");
    private static final Component UNSTABLE = Component.translatable("commands.version.stable.no");

    public VersionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean checkPermissions) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("version").requires(Commands.hasPermission(checkPermissions ? Commands.LEVEL_GAMEMASTERS : Commands.LEVEL_ALL))).executes((commandcontext) -> {
            CommandSourceStack commandsourcestack = (CommandSourceStack) commandcontext.getSource();

            commandsourcestack.sendSystemMessage(VersionCommand.HEADER);
            Objects.requireNonNull(commandsourcestack);
            dumpVersion(commandsourcestack::sendSystemMessage);
            return 1;
        }));
    }

    public static void dumpVersion(Consumer<Component> output) {
        WorldVersion worldversion = SharedConstants.getCurrentVersion();

        output.accept(Component.translatable("commands.version.id", worldversion.id()));
        output.accept(Component.translatable("commands.version.name", worldversion.name()));
        output.accept(Component.translatable("commands.version.data", worldversion.dataVersion().version()));
        output.accept(Component.translatable("commands.version.series", worldversion.dataVersion().series()));
        Object[] aobject = new Object[]{worldversion.protocolVersion(), null};
        String s = Integer.toHexString(worldversion.protocolVersion());

        aobject[1] = "0x" + s;
        output.accept(Component.translatable("commands.version.protocol", aobject));
        output.accept(Component.translatable("commands.version.build_time", Component.translationArg(worldversion.buildTime())));
        output.accept(Component.translatable("commands.version.pack.resource", worldversion.packVersion(PackType.CLIENT_RESOURCES).toString()));
        output.accept(Component.translatable("commands.version.pack.data", worldversion.packVersion(PackType.SERVER_DATA).toString()));
        output.accept(worldversion.stable() ? VersionCommand.STABLE : VersionCommand.UNSTABLE);
    }
}
