package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public class TeamCommand {

    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EXISTS = new SimpleCommandExceptionType(Component.translatable("commands.team.add.duplicate"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EMPTY = new SimpleCommandExceptionType(Component.translatable("commands.team.empty.unchanged"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_NAME = new SimpleCommandExceptionType(Component.translatable("commands.team.option.name.unchanged"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_COLOR = new SimpleCommandExceptionType(Component.translatable("commands.team.option.color.unchanged"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.friendlyfire.alreadyEnabled"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.friendlyfire.alreadyDisabled"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.seeFriendlyInvisibles.alreadyEnabled"));
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.seeFriendlyInvisibles.alreadyDisabled"));
    private static final SimpleCommandExceptionType ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.nametagVisibility.unchanged"));
    private static final SimpleCommandExceptionType ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.deathMessageVisibility.unchanged"));
    private static final SimpleCommandExceptionType ERROR_TEAM_COLLISION_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.team.option.collisionRule.unchanged"));

    public TeamCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("team").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(((LiteralArgumentBuilder) Commands.literal("list").executes((commandcontext) -> {
            return listTeams((CommandSourceStack) commandcontext.getSource());
        })).then(Commands.argument("team", TeamArgument.team()).executes((commandcontext) -> {
            return listMembers((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"));
        })))).then(Commands.literal("add").then(((RequiredArgumentBuilder) Commands.argument("team", StringArgumentType.word()).executes((commandcontext) -> {
            return createTeam((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "team"));
        })).then(Commands.argument("displayName", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return createTeam((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "team"), ComponentArgument.getResolvedComponent(commandcontext, "displayName"));
        }))))).then(Commands.literal("remove").then(Commands.argument("team", TeamArgument.team()).executes((commandcontext) -> {
            return deleteTeam((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"));
        })))).then(Commands.literal("empty").then(Commands.argument("team", TeamArgument.team()).executes((commandcontext) -> {
            return emptyTeam((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"));
        })))).then(Commands.literal("join").then(((RequiredArgumentBuilder) Commands.argument("team", TeamArgument.team()).executes((commandcontext) -> {
            return joinTeam((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()));
        })).then(Commands.argument("members", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).executes((commandcontext) -> {
            return joinTeam((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "members"));
        }))))).then(Commands.literal("leave").then(Commands.argument("members", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS).executes((commandcontext) -> {
            return leaveTeam((CommandSourceStack) commandcontext.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(commandcontext, "members"));
        })))).then(Commands.literal("modify").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("team", TeamArgument.team()).then(Commands.literal("displayName").then(Commands.argument("displayName", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return setDisplayName((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), ComponentArgument.getResolvedComponent(commandcontext, "displayName"));
        })))).then(Commands.literal("color").then(Commands.argument("value", ColorArgument.color()).executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), ColorArgument.getColor(commandcontext, "value"));
        })))).then(Commands.literal("friendlyFire").then(Commands.argument("allowed", BoolArgumentType.bool()).executes((commandcontext) -> {
            return setFriendlyFire((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), BoolArgumentType.getBool(commandcontext, "allowed"));
        })))).then(Commands.literal("seeFriendlyInvisibles").then(Commands.argument("allowed", BoolArgumentType.bool()).executes((commandcontext) -> {
            return setFriendlySight((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), BoolArgumentType.getBool(commandcontext, "allowed"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("nametagVisibility").then(Commands.literal("never").executes((commandcontext) -> {
            return setNametagVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.NEVER);
        }))).then(Commands.literal("hideForOtherTeams").executes((commandcontext) -> {
            return setNametagVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        }))).then(Commands.literal("hideForOwnTeam").executes((commandcontext) -> {
            return setNametagVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.HIDE_FOR_OWN_TEAM);
        }))).then(Commands.literal("always").executes((commandcontext) -> {
            return setNametagVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.ALWAYS);
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("deathMessageVisibility").then(Commands.literal("never").executes((commandcontext) -> {
            return setDeathMessageVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.NEVER);
        }))).then(Commands.literal("hideForOtherTeams").executes((commandcontext) -> {
            return setDeathMessageVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        }))).then(Commands.literal("hideForOwnTeam").executes((commandcontext) -> {
            return setDeathMessageVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.HIDE_FOR_OWN_TEAM);
        }))).then(Commands.literal("always").executes((commandcontext) -> {
            return setDeathMessageVisibility((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.Visibility.ALWAYS);
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("collisionRule").then(Commands.literal("never").executes((commandcontext) -> {
            return setCollision((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.CollisionRule.NEVER);
        }))).then(Commands.literal("pushOwnTeam").executes((commandcontext) -> {
            return setCollision((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.CollisionRule.PUSH_OWN_TEAM);
        }))).then(Commands.literal("pushOtherTeams").executes((commandcontext) -> {
            return setCollision((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.CollisionRule.PUSH_OTHER_TEAMS);
        }))).then(Commands.literal("always").executes((commandcontext) -> {
            return setCollision((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), Team.CollisionRule.ALWAYS);
        })))).then(Commands.literal("prefix").then(Commands.argument("prefix", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return setPrefix((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), ComponentArgument.getResolvedComponent(commandcontext, "prefix"));
        })))).then(Commands.literal("suffix").then(Commands.argument("suffix", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return setSuffix((CommandSourceStack) commandcontext.getSource(), TeamArgument.getTeam(commandcontext, "team"), ComponentArgument.getResolvedComponent(commandcontext, "suffix"));
        }))))));
    }

    private static Component getFirstMemberName(Collection<ScoreHolder> members) {
        return ((ScoreHolder) members.iterator().next()).getFeedbackDisplayName();
    }

    private static int leaveTeam(CommandSourceStack source, Collection<ScoreHolder> members) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : members) {
            scoreboard.removePlayerFromTeam(scoreholder.getScoreboardName());
        }

        if (members.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.leave.success.single", getFirstMemberName(members));
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.leave.success.multiple", members.size());
            }, true);
        }

        return members.size();
    }

    private static int joinTeam(CommandSourceStack source, PlayerTeam team, Collection<ScoreHolder> members) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : members) {
            scoreboard.addPlayerToTeam(scoreholder.getScoreboardName(), team);
        }

        if (members.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.join.success.single", getFirstMemberName(members), team.getFormattedDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.join.success.multiple", members.size(), team.getFormattedDisplayName());
            }, true);
        }

        return members.size();
    }

    private static int setNametagVisibility(CommandSourceStack source, PlayerTeam team, Team.Visibility visibility) throws CommandSyntaxException {
        if (team.getNameTagVisibility() == visibility) {
            throw TeamCommand.ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED.create();
        } else {
            team.setNameTagVisibility(visibility);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.nametagVisibility.success", team.getFormattedDisplayName(), visibility.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setDeathMessageVisibility(CommandSourceStack source, PlayerTeam team, Team.Visibility visibility) throws CommandSyntaxException {
        if (team.getDeathMessageVisibility() == visibility) {
            throw TeamCommand.ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED.create();
        } else {
            team.setDeathMessageVisibility(visibility);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.deathMessageVisibility.success", team.getFormattedDisplayName(), visibility.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setCollision(CommandSourceStack source, PlayerTeam team, Team.CollisionRule collision) throws CommandSyntaxException {
        if (team.getCollisionRule() == collision) {
            throw TeamCommand.ERROR_TEAM_COLLISION_UNCHANGED.create();
        } else {
            team.setCollisionRule(collision);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.collisionRule.success", team.getFormattedDisplayName(), collision.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setFriendlySight(CommandSourceStack source, PlayerTeam team, boolean allowed) throws CommandSyntaxException {
        if (team.canSeeFriendlyInvisibles() == allowed) {
            if (allowed) {
                throw TeamCommand.ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED.create();
            } else {
                throw TeamCommand.ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED.create();
            }
        } else {
            team.setSeeFriendlyInvisibles(allowed);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.seeFriendlyInvisibles." + (allowed ? "enabled" : "disabled"), team.getFormattedDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setFriendlyFire(CommandSourceStack source, PlayerTeam team, boolean allowed) throws CommandSyntaxException {
        if (team.isAllowFriendlyFire() == allowed) {
            if (allowed) {
                throw TeamCommand.ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED.create();
            } else {
                throw TeamCommand.ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED.create();
            }
        } else {
            team.setAllowFriendlyFire(allowed);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.friendlyfire." + (allowed ? "enabled" : "disabled"), team.getFormattedDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, PlayerTeam team, Component displayName) throws CommandSyntaxException {
        if (team.getDisplayName().equals(displayName)) {
            throw TeamCommand.ERROR_TEAM_ALREADY_NAME.create();
        } else {
            team.setDisplayName(displayName);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.name.success", team.getFormattedDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setColor(CommandSourceStack source, PlayerTeam team, ChatFormatting color) throws CommandSyntaxException {
        if (team.getColor() == color) {
            throw TeamCommand.ERROR_TEAM_ALREADY_COLOR.create();
        } else {
            team.setColor(color);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.option.color.success", team.getFormattedDisplayName(), color.getName());
            }, true);
            return 0;
        }
    }

    private static int emptyTeam(CommandSourceStack source, PlayerTeam team) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        Collection<String> collection = Lists.newArrayList(team.getPlayers());

        if (collection.isEmpty()) {
            throw TeamCommand.ERROR_TEAM_ALREADY_EMPTY.create();
        } else {
            for (String s : collection) {
                scoreboard.removePlayerFromTeam(s, team);
            }

            source.sendSuccess(() -> {
                return Component.translatable("commands.team.empty.success", collection.size(), team.getFormattedDisplayName());
            }, true);
            return collection.size();
        }
    }

    private static int deleteTeam(CommandSourceStack source, PlayerTeam team) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        scoreboard.removePlayerTeam(team);
        source.sendSuccess(() -> {
            return Component.translatable("commands.team.remove.success", team.getFormattedDisplayName());
        }, true);
        return scoreboard.getPlayerTeams().size();
    }

    private static int createTeam(CommandSourceStack source, String name) throws CommandSyntaxException {
        return createTeam(source, name, Component.literal(name));
    }

    private static int createTeam(CommandSourceStack source, String name, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (scoreboard.getPlayerTeam(name) != null) {
            throw TeamCommand.ERROR_TEAM_ALREADY_EXISTS.create();
        } else {
            PlayerTeam playerteam = scoreboard.addPlayerTeam(name);

            playerteam.setDisplayName(displayName);
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.add.success", playerteam.getFormattedDisplayName());
            }, true);
            return scoreboard.getPlayerTeams().size();
        }
    }

    private static int listMembers(CommandSourceStack source, PlayerTeam team) {
        Collection<String> collection = team.getPlayers();

        if (collection.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.list.members.empty", team.getFormattedDisplayName());
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.list.members.success", team.getFormattedDisplayName(), collection.size(), ComponentUtils.formatList(collection));
            }, false);
        }

        return collection.size();
    }

    private static int listTeams(CommandSourceStack source) {
        Collection<PlayerTeam> collection = source.getServer().getScoreboard().getPlayerTeams();

        if (collection.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.list.teams.empty");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.team.list.teams.success", collection.size(), ComponentUtils.formatList(collection, PlayerTeam::getFormattedDisplayName));
            }, false);
        }

        return collection.size();
    }

    private static int setPrefix(CommandSourceStack source, PlayerTeam team, Component prefix) {
        team.setPlayerPrefix(prefix);
        source.sendSuccess(() -> {
            return Component.translatable("commands.team.option.prefix.success", prefix);
        }, false);
        return 1;
    }

    private static int setSuffix(CommandSourceStack source, PlayerTeam team, Component suffix) {
        team.setPlayerSuffix(suffix);
        source.sendSuccess(() -> {
            return Component.translatable("commands.team.option.suffix.success", suffix);
        }, false);
        return 1;
    }
}
