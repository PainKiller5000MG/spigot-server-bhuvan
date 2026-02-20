package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class BossBarCommands {

    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.bossbar.create.failed", object);
    });
    private static final DynamicCommandExceptionType ERROR_DOESNT_EXIST = new DynamicCommandExceptionType((object) -> {
        return Component.translatableEscape("commands.bossbar.unknown", object);
    });
    private static final SimpleCommandExceptionType ERROR_NO_PLAYER_CHANGE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.players.unchanged"));
    private static final SimpleCommandExceptionType ERROR_NO_NAME_CHANGE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.name.unchanged"));
    private static final SimpleCommandExceptionType ERROR_NO_COLOR_CHANGE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.color.unchanged"));
    private static final SimpleCommandExceptionType ERROR_NO_STYLE_CHANGE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.style.unchanged"));
    private static final SimpleCommandExceptionType ERROR_NO_VALUE_CHANGE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.value.unchanged"));
    private static final SimpleCommandExceptionType ERROR_NO_MAX_CHANGE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.max.unchanged"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_HIDDEN = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.visibility.unchanged.hidden"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_VISIBLE = new SimpleCommandExceptionType(Component.translatable("commands.bossbar.set.visibility.unchanged.visible"));
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_BAR = (commandcontext, suggestionsbuilder) -> {
        return SharedSuggestionProvider.suggestResource(((CommandSourceStack) commandcontext.getSource()).getServer().getCustomBossEvents().getIds(), suggestionsbuilder);
    };

    public BossBarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("bossbar").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("add").then(Commands.argument("id", IdentifierArgument.id()).then(Commands.argument("name", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return createBar((CommandSourceStack) commandcontext.getSource(), IdentifierArgument.getId(commandcontext, "id"), ComponentArgument.getResolvedComponent(commandcontext, "name"));
        }))))).then(Commands.literal("remove").then(Commands.argument("id", IdentifierArgument.id()).suggests(BossBarCommands.SUGGEST_BOSS_BAR).executes((commandcontext) -> {
            return removeBar((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext));
        })))).then(Commands.literal("list").executes((commandcontext) -> {
            return listBars((CommandSourceStack) commandcontext.getSource());
        }))).then(Commands.literal("set").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("id", IdentifierArgument.id()).suggests(BossBarCommands.SUGGEST_BOSS_BAR).then(Commands.literal("name").then(Commands.argument("name", ComponentArgument.textComponent(context)).executes((commandcontext) -> {
            return setName((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), ComponentArgument.getResolvedComponent(commandcontext, "name"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("color").then(Commands.literal("pink").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.PINK);
        }))).then(Commands.literal("blue").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.BLUE);
        }))).then(Commands.literal("red").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.RED);
        }))).then(Commands.literal("green").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.GREEN);
        }))).then(Commands.literal("yellow").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.YELLOW);
        }))).then(Commands.literal("purple").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.PURPLE);
        }))).then(Commands.literal("white").executes((commandcontext) -> {
            return setColor((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarColor.WHITE);
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("style").then(Commands.literal("progress").executes((commandcontext) -> {
            return setStyle((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarOverlay.PROGRESS);
        }))).then(Commands.literal("notched_6").executes((commandcontext) -> {
            return setStyle((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarOverlay.NOTCHED_6);
        }))).then(Commands.literal("notched_10").executes((commandcontext) -> {
            return setStyle((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarOverlay.NOTCHED_10);
        }))).then(Commands.literal("notched_12").executes((commandcontext) -> {
            return setStyle((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarOverlay.NOTCHED_12);
        }))).then(Commands.literal("notched_20").executes((commandcontext) -> {
            return setStyle((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BossEvent.BossBarOverlay.NOTCHED_20);
        })))).then(Commands.literal("value").then(Commands.argument("value", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return setValue((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), IntegerArgumentType.getInteger(commandcontext, "value"));
        })))).then(Commands.literal("max").then(Commands.argument("max", IntegerArgumentType.integer(1)).executes((commandcontext) -> {
            return setMax((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), IntegerArgumentType.getInteger(commandcontext, "max"));
        })))).then(Commands.literal("visible").then(Commands.argument("visible", BoolArgumentType.bool()).executes((commandcontext) -> {
            return setVisible((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), BoolArgumentType.getBool(commandcontext, "visible"));
        })))).then(((LiteralArgumentBuilder) Commands.literal("players").executes((commandcontext) -> {
            return setPlayers((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), Collections.emptyList());
        })).then(Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return setPlayers((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext), EntityArgument.getOptionalPlayers(commandcontext, "targets"));
        })))))).then(Commands.literal("get").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) Commands.argument("id", IdentifierArgument.id()).suggests(BossBarCommands.SUGGEST_BOSS_BAR).then(Commands.literal("value").executes((commandcontext) -> {
            return getValue((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext));
        }))).then(Commands.literal("max").executes((commandcontext) -> {
            return getMax((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext));
        }))).then(Commands.literal("visible").executes((commandcontext) -> {
            return getVisible((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext));
        }))).then(Commands.literal("players").executes((commandcontext) -> {
            return getPlayers((CommandSourceStack) commandcontext.getSource(), getBossBar(commandcontext));
        })))));
    }

    private static int getValue(CommandSourceStack source, CustomBossEvent bossBar) {
        source.sendSuccess(() -> {
            return Component.translatable("commands.bossbar.get.value", bossBar.getDisplayName(), bossBar.getValue());
        }, true);
        return bossBar.getValue();
    }

    private static int getMax(CommandSourceStack source, CustomBossEvent bossBar) {
        source.sendSuccess(() -> {
            return Component.translatable("commands.bossbar.get.max", bossBar.getDisplayName(), bossBar.getMax());
        }, true);
        return bossBar.getMax();
    }

    private static int getVisible(CommandSourceStack source, CustomBossEvent bossBar) {
        if (bossBar.isVisible()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.get.visible.visible", bossBar.getDisplayName());
            }, true);
            return 1;
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.get.visible.hidden", bossBar.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int getPlayers(CommandSourceStack source, CustomBossEvent bossBar) {
        if (bossBar.getPlayers().isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.get.players.none", bossBar.getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.get.players.some", bossBar.getDisplayName(), bossBar.getPlayers().size(), ComponentUtils.formatList(bossBar.getPlayers(), Player::getDisplayName));
            }, true);
        }

        return bossBar.getPlayers().size();
    }

    private static int setVisible(CommandSourceStack source, CustomBossEvent bossBar, boolean visible) throws CommandSyntaxException {
        if (bossBar.isVisible() == visible) {
            if (visible) {
                throw BossBarCommands.ERROR_ALREADY_VISIBLE.create();
            } else {
                throw BossBarCommands.ERROR_ALREADY_HIDDEN.create();
            }
        } else {
            bossBar.setVisible(visible);
            if (visible) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.bossbar.set.visible.success.visible", bossBar.getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.bossbar.set.visible.success.hidden", bossBar.getDisplayName());
                }, true);
            }

            return 0;
        }
    }

    private static int setValue(CommandSourceStack source, CustomBossEvent bossBar, int value) throws CommandSyntaxException {
        if (bossBar.getValue() == value) {
            throw BossBarCommands.ERROR_NO_VALUE_CHANGE.create();
        } else {
            bossBar.setValue(value);
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.set.value.success", bossBar.getDisplayName(), value);
            }, true);
            return value;
        }
    }

    private static int setMax(CommandSourceStack source, CustomBossEvent bossBar, int value) throws CommandSyntaxException {
        if (bossBar.getMax() == value) {
            throw BossBarCommands.ERROR_NO_MAX_CHANGE.create();
        } else {
            bossBar.setMax(value);
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.set.max.success", bossBar.getDisplayName(), value);
            }, true);
            return value;
        }
    }

    private static int setColor(CommandSourceStack source, CustomBossEvent bossBar, BossEvent.BossBarColor color) throws CommandSyntaxException {
        if (bossBar.getColor().equals(color)) {
            throw BossBarCommands.ERROR_NO_COLOR_CHANGE.create();
        } else {
            bossBar.setColor(color);
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.set.color.success", bossBar.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setStyle(CommandSourceStack source, CustomBossEvent bossBar, BossEvent.BossBarOverlay style) throws CommandSyntaxException {
        if (bossBar.getOverlay().equals(style)) {
            throw BossBarCommands.ERROR_NO_STYLE_CHANGE.create();
        } else {
            bossBar.setOverlay(style);
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.set.style.success", bossBar.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setName(CommandSourceStack source, CustomBossEvent bossBar, Component name) throws CommandSyntaxException {
        Component component1 = ComponentUtils.updateForEntity(source, name, (Entity) null, 0);

        if (bossBar.getName().equals(component1)) {
            throw BossBarCommands.ERROR_NO_NAME_CHANGE.create();
        } else {
            bossBar.setName(component1);
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.set.name.success", bossBar.getDisplayName());
            }, true);
            return 0;
        }
    }

    private static int setPlayers(CommandSourceStack source, CustomBossEvent bossBar, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        boolean flag = bossBar.setPlayers(targets);

        if (!flag) {
            throw BossBarCommands.ERROR_NO_PLAYER_CHANGE.create();
        } else {
            if (bossBar.getPlayers().isEmpty()) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.bossbar.set.players.success.none", bossBar.getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.bossbar.set.players.success.some", bossBar.getDisplayName(), targets.size(), ComponentUtils.formatList(targets, Player::getDisplayName));
                }, true);
            }

            return bossBar.getPlayers().size();
        }
    }

    private static int listBars(CommandSourceStack source) {
        Collection<CustomBossEvent> collection = source.getServer().getCustomBossEvents().getEvents();

        if (collection.isEmpty()) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.list.bars.none");
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.list.bars.some", collection.size(), ComponentUtils.formatList(collection, CustomBossEvent::getDisplayName));
            }, false);
        }

        return collection.size();
    }

    private static int createBar(CommandSourceStack source, Identifier id, Component name) throws CommandSyntaxException {
        CustomBossEvents custombossevents = source.getServer().getCustomBossEvents();

        if (custombossevents.get(id) != null) {
            throw BossBarCommands.ERROR_ALREADY_EXISTS.create(id.toString());
        } else {
            CustomBossEvent custombossevent = custombossevents.create(id, ComponentUtils.updateForEntity(source, name, (Entity) null, 0));

            source.sendSuccess(() -> {
                return Component.translatable("commands.bossbar.create.success", custombossevent.getDisplayName());
            }, true);
            return custombossevents.getEvents().size();
        }
    }

    private static int removeBar(CommandSourceStack source, CustomBossEvent bossBar) {
        CustomBossEvents custombossevents = source.getServer().getCustomBossEvents();

        bossBar.removeAllPlayers();
        custombossevents.remove(bossBar);
        source.sendSuccess(() -> {
            return Component.translatable("commands.bossbar.remove.success", bossBar.getDisplayName());
        }, true);
        return custombossevents.getEvents().size();
    }

    public static CustomBossEvent getBossBar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier identifier = IdentifierArgument.getId(context, "id");
        CustomBossEvent custombossevent = ((CommandSourceStack) context.getSource()).getServer().getCustomBossEvents().get(identifier);

        if (custombossevent == null) {
            throw BossBarCommands.ERROR_DOESNT_EXIST.create(identifier.toString());
        } else {
            return custombossevent;
        }
    }
}
