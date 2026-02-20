package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

public class ReloadCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ReloadCommand() {}

    public static void reloadPacks(Collection<String> selectedPacks, CommandSourceStack source) {
        source.getServer().reloadResources(selectedPacks).exceptionally((throwable) -> {
            ReloadCommand.LOGGER.warn("Failed to execute reload", throwable);
            source.sendFailure(Component.translatable("commands.reload.failure"));
            return null;
        });
    }

    private static Collection<String> discoverNewPacks(PackRepository packRepository, WorldData worldData, Collection<String> currentPacks) {
        packRepository.reload();
        Collection<String> collection1 = Lists.newArrayList(currentPacks);
        Collection<String> collection2 = worldData.getDataConfiguration().dataPacks().getDisabled();

        for (String s : packRepository.getAvailableIds()) {
            if (!collection2.contains(s) && !collection1.contains(s)) {
                collection1.add(s);
            }
        }

        return collection1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).executes((commandcontext) -> {
            CommandSourceStack commandsourcestack = (CommandSourceStack) commandcontext.getSource();
            MinecraftServer minecraftserver = commandsourcestack.getServer();
            PackRepository packrepository = minecraftserver.getPackRepository();
            WorldData worlddata = minecraftserver.getWorldData();
            Collection<String> collection = packrepository.getSelectedIds();
            Collection<String> collection1 = discoverNewPacks(packrepository, worlddata, collection);

            commandsourcestack.sendSuccess(() -> {
                return Component.translatable("commands.reload.success");
            }, true);
            reloadPacks(collection1, commandsourcestack);
            return 0;
        }));
    }
}
