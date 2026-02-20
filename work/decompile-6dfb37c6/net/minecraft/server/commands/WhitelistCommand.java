package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.entity.player.Player;

public class WhitelistCommand {

    private static final SimpleCommandExceptionType ERROR_ALREADY_ENABLED = new SimpleCommandExceptionType(Component.translatable("commands.whitelist.alreadyOn"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_DISABLED = new SimpleCommandExceptionType(Component.translatable("commands.whitelist.alreadyOff"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_WHITELISTED = new SimpleCommandExceptionType(Component.translatable("commands.whitelist.add.failed"));
    private static final SimpleCommandExceptionType ERROR_NOT_WHITELISTED = new SimpleCommandExceptionType(Component.translatable("commands.whitelist.remove.failed"));

    public WhitelistCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("whitelist").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))).then(Commands.literal("on").executes((commandcontext) -> {
            return enableWhitelist((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("off").executes((commandcontext) -> {
            return disableWhitelist((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("list").executes((commandcontext) -> {
            return showList((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("add").then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((commandcontext, suggestionsbuilder) -> {
            PlayerList playerlist = ((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList();

            return SharedSuggestionProvider.suggest(playerlist.getPlayers().stream().map(Player::nameAndId).filter((nameandid) -> {
                return !playerlist.getWhiteList().isWhiteListed(nameandid);
            }).map(NameAndId::name), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return addPlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"));
        })))).then(Commands.literal("remove").then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((commandcontext, suggestionsbuilder) -> {
            return SharedSuggestionProvider.suggest(((CommandSourceStack) commandcontext.getSource()).getServer().getPlayerList().getWhiteListNames(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return removePlayers((CommandSourceStack) commandcontext.getSource(), GameProfileArgument.getGameProfiles(commandcontext, "targets"));
        })))).then(Commands.literal("reload").executes((commandcontext) -> {
            return reload((CommandSourceStack) commandcontext.getSource());
        })));
    }

    private static int reload(CommandSourceStack source) {
        source.getServer().getPlayerList().reloadWhiteList();
        source.sendSuccess(() -> {
            return Component.translatable("commands.whitelist.reloaded");
        }, true);
        source.getServer().kickUnlistedPlayers();
        return 1;
    }

    private static int addPlayers(CommandSourceStack source, Collection<NameAndId> targets) throws CommandSyntaxException {
        UserWhiteList userwhitelist = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (NameAndId nameandid : targets) {
            if (!userwhitelist.isWhiteListed(nameandid)) {
                UserWhiteListEntry userwhitelistentry = new UserWhiteListEntry(nameandid);

                userwhitelist.add(userwhitelistentry);
                source.sendSuccess(() -> {
                    return Component.translatable("commands.whitelist.add.success", Component.literal(nameandid.name()));
                }, true);
                ++i;
            }
        }

        if (i == 0) {
            throw WhitelistCommand.ERROR_ALREADY_WHITELISTED.create();
        } else {
            return i;
        }
    }

    private static int removePlayers(CommandSourceStack source, Collection<NameAndId> targets) throws CommandSyntaxException {
        UserWhiteList userwhitelist = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (NameAndId nameandid : targets) {
            if (userwhitelist.isWhiteListed(nameandid)) {
                UserWhiteListEntry userwhitelistentry = new UserWhiteListEntry(nameandid);

                userwhitelist.remove((StoredUserEntry) userwhitelistentry);
                source.sendSuccess(() -> {
                    return Component.translatable("commands.whitelist.remove.success", Component.literal(nameandid.name()));
                }, true);
                ++i;
            }
        }

        if (i == 0) {
            throw WhitelistCommand.ERROR_NOT_WHITELISTED.create();
        } else {
            source.getServer().kickUnlistedPlayers();
            return i;
        }
    }

    private static int enableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getServer().isUsingWhitelist()) {
            throw WhitelistCommand.ERROR_ALREADY_ENABLED.create();
        } else {
            source.getServer().setUsingWhitelist(true);
            source.sendSuccess(() -> {
                return Component.translatable("commands.whitelist.enabled");
            }, true);
            source.getServer().kickUnlistedPlayers();
            return 1;
        }
    }

    private static int disableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        if (!source.getServer().isUsingWhitelist()) {
            throw WhitelistCommand.ERROR_ALREADY_DISABLED.create();
        } else {
            source.getServer().setUsingWhitelist(false);
            source.sendSuccess(() -> {
                return Component.translatable("commands.whitelist.disabled");
            }, true);
            return 1;
        }
    }

    private static int showList(CommandSourceStack source) {
        String[] astring = source.getServer().getPlayerList().getWhiteListNames();

        if (astring.length == 0) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.whitelist.none");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.whitelist.list", astring.length, String.join(", ", astring));
            }, false);
        }

        return astring.length;
    }
}
