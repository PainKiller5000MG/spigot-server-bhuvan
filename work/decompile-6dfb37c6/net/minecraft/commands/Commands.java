package net.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.AdvancementCommands;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.server.commands.BanIpCommands;
import net.minecraft.server.commands.BanListCommands;
import net.minecraft.server.commands.BanPlayerCommands;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.commands.ChaseCommand;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.commands.DamageCommand;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.commands.DeOpCommands;
import net.minecraft.server.commands.DebugCommand;
import net.minecraft.server.commands.DebugConfigCommand;
import net.minecraft.server.commands.DebugMobSpawningCommand;
import net.minecraft.server.commands.DebugPathCommand;
import net.minecraft.server.commands.DefaultGameModeCommands;
import net.minecraft.server.commands.DialogCommand;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.server.commands.EmoteCommands;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.commands.FetchProfileCommand;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.commands.HelpCommand;
import net.minecraft.server.commands.ItemCommands;
import net.minecraft.server.commands.JfrCommand;
import net.minecraft.server.commands.KickCommand;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.commands.LootCommand;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.commands.OpCommand;
import net.minecraft.server.commands.PardonCommand;
import net.minecraft.server.commands.PardonIpCommand;
import net.minecraft.server.commands.ParticleCommand;
import net.minecraft.server.commands.PerfCommand;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.server.commands.RaidCommand;
import net.minecraft.server.commands.RandomCommand;
import net.minecraft.server.commands.RecipeCommand;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.commands.ReturnCommand;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.server.commands.RotateCommand;
import net.minecraft.server.commands.SaveAllCommand;
import net.minecraft.server.commands.SaveOffCommand;
import net.minecraft.server.commands.SaveOnCommand;
import net.minecraft.server.commands.SayCommand;
import net.minecraft.server.commands.ScheduleCommand;
import net.minecraft.server.commands.ScoreboardCommand;
import net.minecraft.server.commands.SeedCommand;
import net.minecraft.server.commands.ServerPackCommand;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.commands.SetPlayerIdleTimeoutCommand;
import net.minecraft.server.commands.SetSpawnCommand;
import net.minecraft.server.commands.SetWorldSpawnCommand;
import net.minecraft.server.commands.SpawnArmorTrimsCommand;
import net.minecraft.server.commands.SpectateCommand;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.commands.StopCommand;
import net.minecraft.server.commands.StopSoundCommand;
import net.minecraft.server.commands.StopwatchCommand;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.commands.TagCommand;
import net.minecraft.server.commands.TeamCommand;
import net.minecraft.server.commands.TeamMsgCommand;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.server.commands.TickCommand;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.commands.TitleCommand;
import net.minecraft.server.commands.TransferCommand;
import net.minecraft.server.commands.TriggerCommand;
import net.minecraft.server.commands.VersionCommand;
import net.minecraft.server.commands.WardenSpawnTrackerCommand;
import net.minecraft.server.commands.WaypointCommand;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.commands.WhitelistCommand;
import net.minecraft.server.commands.WorldBorderCommand;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionProviderCheck;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Commands {

    public static final String COMMAND_PREFIX = "/";
    private static final ThreadLocal<@Nullable ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT = new ThreadLocal();
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final PermissionCheck LEVEL_ALL = PermissionCheck.AlwaysPass.INSTANCE;
    public static final PermissionCheck LEVEL_MODERATORS = new PermissionCheck.Require(Permissions.COMMANDS_MODERATOR);
    public static final PermissionCheck LEVEL_GAMEMASTERS = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);
    public static final PermissionCheck LEVEL_ADMINS = new PermissionCheck.Require(Permissions.COMMANDS_ADMIN);
    public static final PermissionCheck LEVEL_OWNERS = new PermissionCheck.Require(Permissions.COMMANDS_OWNER);
    private static final ClientboundCommandsPacket.NodeInspector<CommandSourceStack> COMMAND_NODE_INSPECTOR = new ClientboundCommandsPacket.NodeInspector<CommandSourceStack>() {
        private final CommandSourceStack noPermissionSource;

        {
            this.noPermissionSource = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        }

        @Override
        public @Nullable Identifier suggestionId(ArgumentCommandNode<CommandSourceStack, ?> node) {
            SuggestionProvider<CommandSourceStack> suggestionprovider = node.getCustomSuggestions();

            return suggestionprovider != null ? SuggestionProviders.getName(suggestionprovider) : null;
        }

        @Override
        public boolean isExecutable(CommandNode<CommandSourceStack> node) {
            return node.getCommand() != null;
        }

        @Override
        public boolean isRestricted(CommandNode<CommandSourceStack> node) {
            Predicate<CommandSourceStack> predicate = node.getRequirement();

            return !predicate.test(this.noPermissionSource);
        }
    };
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher();

    public Commands(Commands.CommandSelection commandSelection, CommandBuildContext context) {
        AdvancementCommands.register(this.dispatcher);
        AttributeCommand.register(this.dispatcher, context);
        ExecuteCommand.register(this.dispatcher, context);
        BossBarCommands.register(this.dispatcher, context);
        ClearInventoryCommands.register(this.dispatcher, context);
        CloneCommands.register(this.dispatcher, context);
        DamageCommand.register(this.dispatcher, context);
        DataCommands.register(this.dispatcher);
        DataPackCommand.register(this.dispatcher, context);
        DebugCommand.register(this.dispatcher);
        DefaultGameModeCommands.register(this.dispatcher);
        DialogCommand.register(this.dispatcher, context);
        DifficultyCommand.register(this.dispatcher);
        EffectCommands.register(this.dispatcher, context);
        EmoteCommands.register(this.dispatcher);
        EnchantCommand.register(this.dispatcher, context);
        ExperienceCommand.register(this.dispatcher);
        FillCommand.register(this.dispatcher, context);
        FillBiomeCommand.register(this.dispatcher, context);
        ForceLoadCommand.register(this.dispatcher);
        FunctionCommand.register(this.dispatcher);
        GameModeCommand.register(this.dispatcher);
        GameRuleCommand.register(this.dispatcher, context);
        GiveCommand.register(this.dispatcher, context);
        HelpCommand.register(this.dispatcher);
        ItemCommands.register(this.dispatcher, context);
        KickCommand.register(this.dispatcher);
        KillCommand.register(this.dispatcher);
        ListPlayersCommand.register(this.dispatcher);
        LocateCommand.register(this.dispatcher, context);
        LootCommand.register(this.dispatcher, context);
        MsgCommand.register(this.dispatcher);
        ParticleCommand.register(this.dispatcher, context);
        PlaceCommand.register(this.dispatcher);
        PlaySoundCommand.register(this.dispatcher);
        RandomCommand.register(this.dispatcher);
        ReloadCommand.register(this.dispatcher);
        RecipeCommand.register(this.dispatcher);
        FetchProfileCommand.register(this.dispatcher);
        ReturnCommand.register(this.dispatcher);
        RideCommand.register(this.dispatcher);
        RotateCommand.register(this.dispatcher);
        SayCommand.register(this.dispatcher);
        ScheduleCommand.register(this.dispatcher);
        ScoreboardCommand.register(this.dispatcher, context);
        SeedCommand.register(this.dispatcher, commandSelection != Commands.CommandSelection.INTEGRATED);
        VersionCommand.register(this.dispatcher, commandSelection != Commands.CommandSelection.INTEGRATED);
        SetBlockCommand.register(this.dispatcher, context);
        SetSpawnCommand.register(this.dispatcher);
        SetWorldSpawnCommand.register(this.dispatcher);
        SpectateCommand.register(this.dispatcher);
        SpreadPlayersCommand.register(this.dispatcher);
        StopSoundCommand.register(this.dispatcher);
        StopwatchCommand.register(this.dispatcher);
        SummonCommand.register(this.dispatcher, context);
        TagCommand.register(this.dispatcher);
        TeamCommand.register(this.dispatcher, context);
        TeamMsgCommand.register(this.dispatcher);
        TeleportCommand.register(this.dispatcher);
        TellRawCommand.register(this.dispatcher, context);
        TestCommand.register(this.dispatcher, context);
        TickCommand.register(this.dispatcher);
        TimeCommand.register(this.dispatcher);
        TitleCommand.register(this.dispatcher, context);
        TriggerCommand.register(this.dispatcher);
        WaypointCommand.register(this.dispatcher, context);
        WeatherCommand.register(this.dispatcher);
        WorldBorderCommand.register(this.dispatcher);
        if (JvmProfiler.INSTANCE.isAvailable()) {
            JfrCommand.register(this.dispatcher);
        }

        if (SharedConstants.DEBUG_CHASE_COMMAND) {
            ChaseCommand.register(this.dispatcher);
        }

        if (SharedConstants.DEBUG_DEV_COMMANDS || SharedConstants.IS_RUNNING_IN_IDE) {
            RaidCommand.register(this.dispatcher, context);
            DebugPathCommand.register(this.dispatcher);
            DebugMobSpawningCommand.register(this.dispatcher);
            WardenSpawnTrackerCommand.register(this.dispatcher);
            SpawnArmorTrimsCommand.register(this.dispatcher);
            ServerPackCommand.register(this.dispatcher);
            if (commandSelection.includeDedicated) {
                DebugConfigCommand.register(this.dispatcher, context);
            }
        }

        if (commandSelection.includeDedicated) {
            BanIpCommands.register(this.dispatcher);
            BanListCommands.register(this.dispatcher);
            BanPlayerCommands.register(this.dispatcher);
            DeOpCommands.register(this.dispatcher);
            OpCommand.register(this.dispatcher);
            PardonCommand.register(this.dispatcher);
            PardonIpCommand.register(this.dispatcher);
            PerfCommand.register(this.dispatcher);
            SaveAllCommand.register(this.dispatcher);
            SaveOffCommand.register(this.dispatcher);
            SaveOnCommand.register(this.dispatcher);
            SetPlayerIdleTimeoutCommand.register(this.dispatcher);
            StopCommand.register(this.dispatcher);
            TransferCommand.register(this.dispatcher);
            WhitelistCommand.register(this.dispatcher);
        }

        if (commandSelection.includeIntegrated) {
            PublishCommand.register(this.dispatcher);
        }

        this.dispatcher.setConsumer(ExecutionCommandSource.resultConsumer());
    }

    public static <S> ParseResults<S> mapSource(ParseResults<S> parse, UnaryOperator<S> sourceOperator) {
        CommandContextBuilder<S> commandcontextbuilder = parse.getContext();
        CommandContextBuilder<S> commandcontextbuilder1 = commandcontextbuilder.withSource(sourceOperator.apply(commandcontextbuilder.getSource()));

        return new ParseResults(commandcontextbuilder1, parse.getReader(), parse.getExceptions());
    }

    public void performPrefixedCommand(CommandSourceStack sender, String command) {
        command = trimOptionalPrefix(command);
        this.performCommand(this.dispatcher.parse(command, sender), command);
    }

    public static String trimOptionalPrefix(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    public void performCommand(ParseResults<CommandSourceStack> command, String commandString) {
        CommandSourceStack commandsourcestack = (CommandSourceStack) command.getContext().getSource();

        Profiler.get().push(() -> {
            return "/" + commandString;
        });
        ContextChain<CommandSourceStack> contextchain = finishParsing(command, commandString, commandsourcestack);

        try {
            if (contextchain != null) {
                executeCommandInContext(commandsourcestack, (executioncontext) -> {
                    ExecutionContext.queueInitialCommandExecution(executioncontext, commandString, contextchain, commandsourcestack, CommandResultCallback.EMPTY);
                });
            }
        } catch (Exception exception) {
            MutableComponent mutablecomponent = Component.literal(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());

            if (Commands.LOGGER.isDebugEnabled()) {
                Commands.LOGGER.error("Command exception: /{}", commandString, exception);
                StackTraceElement[] astacktraceelement = exception.getStackTrace();

                for (int i = 0; i < Math.min(astacktraceelement.length, 3); ++i) {
                    mutablecomponent.append("\n\n").append(astacktraceelement[i].getMethodName()).append("\n ").append(astacktraceelement[i].getFileName()).append(":").append(String.valueOf(astacktraceelement[i].getLineNumber()));
                }
            }

            commandsourcestack.sendFailure(Component.translatable("command.failed").withStyle((style) -> {
                return style.withHoverEvent(new HoverEvent.ShowText(mutablecomponent));
            }));
            if (SharedConstants.DEBUG_VERBOSE_COMMAND_ERRORS || SharedConstants.IS_RUNNING_IN_IDE) {
                commandsourcestack.sendFailure(Component.literal(Util.describeError(exception)));
                Commands.LOGGER.error("'/{}' threw an exception", commandString, exception);
            }
        } finally {
            Profiler.get().pop();
        }

    }

    private static @Nullable ContextChain<CommandSourceStack> finishParsing(ParseResults<CommandSourceStack> command, String commandString, CommandSourceStack sender) {
        try {
            validateParseResults(command);
            return (ContextChain) ContextChain.tryFlatten(command.getContext().build(commandString)).orElseThrow(() -> {
                return CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(command.getReader());
            });
        } catch (CommandSyntaxException commandsyntaxexception) {
            sender.sendFailure(ComponentUtils.fromMessage(commandsyntaxexception.getRawMessage()));
            if (commandsyntaxexception.getInput() != null && commandsyntaxexception.getCursor() >= 0) {
                int i = Math.min(commandsyntaxexception.getInput().length(), commandsyntaxexception.getCursor());
                MutableComponent mutablecomponent = Component.empty().withStyle(ChatFormatting.GRAY).withStyle((style) -> {
                    return style.withClickEvent(new ClickEvent.SuggestCommand("/" + commandString));
                });

                if (i > 10) {
                    mutablecomponent.append(CommonComponents.ELLIPSIS);
                }

                mutablecomponent.append(commandsyntaxexception.getInput().substring(Math.max(0, i - 10), i));
                if (i < commandsyntaxexception.getInput().length()) {
                    Component component = Component.literal(commandsyntaxexception.getInput().substring(i)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE);

                    mutablecomponent.append(component);
                }

                mutablecomponent.append((Component) Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
                sender.sendFailure(mutablecomponent);
            }

            return null;
        }
    }

    public static void executeCommandInContext(CommandSourceStack context, Consumer<ExecutionContext<CommandSourceStack>> config) {
        ExecutionContext<CommandSourceStack> executioncontext = (ExecutionContext) Commands.CURRENT_EXECUTION_CONTEXT.get();
        boolean flag = executioncontext == null;

        if (flag) {
            GameRules gamerules = context.getLevel().getGameRules();
            int i = Math.max(1, (Integer) gamerules.get(GameRules.MAX_COMMAND_SEQUENCE_LENGTH));
            int j = (Integer) gamerules.get(GameRules.MAX_COMMAND_FORKS);

            try {
                ExecutionContext<CommandSourceStack> executioncontext1 = new ExecutionContext<CommandSourceStack>(i, j, Profiler.get());

                try {
                    Commands.CURRENT_EXECUTION_CONTEXT.set(executioncontext1);
                    config.accept(executioncontext1);
                    executioncontext1.runCommandQueue();
                } catch (Throwable throwable) {
                    try {
                        executioncontext1.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                executioncontext1.close();
            } finally {
                Commands.CURRENT_EXECUTION_CONTEXT.set((Object) null);
            }
        } else {
            config.accept(executioncontext);
        }

    }

    public void sendCommands(ServerPlayer player) {
        Map<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>> map = new HashMap();
        RootCommandNode<CommandSourceStack> rootcommandnode = new RootCommandNode();

        map.put(this.dispatcher.getRoot(), rootcommandnode);
        fillUsableCommands(this.dispatcher.getRoot(), rootcommandnode, player.createCommandSourceStack(), map);
        player.connection.send(new ClientboundCommandsPacket(rootcommandnode, Commands.COMMAND_NODE_INSPECTOR));
    }

    private static <S> void fillUsableCommands(CommandNode<S> source, CommandNode<S> target, S commandFilter, Map<CommandNode<S>, CommandNode<S>> converted) {
        for (CommandNode<S> commandnode2 : source.getChildren()) {
            if (commandnode2.canUse(commandFilter)) {
                ArgumentBuilder<S, ?> argumentbuilder = commandnode2.createBuilder();

                if (argumentbuilder.getRedirect() != null) {
                    argumentbuilder.redirect((CommandNode) converted.get(argumentbuilder.getRedirect()));
                }

                CommandNode<S> commandnode3 = argumentbuilder.build();

                converted.put(commandnode2, commandnode3);
                target.addChild(commandnode3);
                if (!commandnode2.getChildren().isEmpty()) {
                    fillUsableCommands(commandnode2, commandnode3, commandFilter, converted);
                }
            }
        }

    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String literal) {
        return LiteralArgumentBuilder.literal(literal);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static Predicate<String> createValidator(Commands.ParseFunction parser) {
        return (s) -> {
            try {
                parser.parse(new StringReader(s));
                return true;
            } catch (CommandSyntaxException commandsyntaxexception) {
                return false;
            }
        };
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.dispatcher;
    }

    public static <S> void validateParseResults(ParseResults<S> command) throws CommandSyntaxException {
        CommandSyntaxException commandsyntaxexception = getParseException(command);

        if (commandsyntaxexception != null) {
            throw commandsyntaxexception;
        }
    }

    public static <S> @Nullable CommandSyntaxException getParseException(ParseResults<S> parse) {
        return !parse.getReader().canRead() ? null : (parse.getExceptions().size() == 1 ? (CommandSyntaxException) parse.getExceptions().values().iterator().next() : (parse.getContext().getRange().isEmpty() ? CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader()) : CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader())));
    }

    public static CommandBuildContext createValidationContext(final HolderLookup.Provider registries) {
        return new CommandBuildContext() {
            @Override
            public FeatureFlagSet enabledFeatures() {
                return FeatureFlags.REGISTRY.allFlags();
            }

            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return registries.listRegistryKeys();
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return registries.lookup(key).map(this::createLookup);
            }

            private <T> HolderLookup.RegistryLookup.Delegate<T> createLookup(final HolderLookup.RegistryLookup<T> original) {
                return new HolderLookup.RegistryLookup.Delegate<T>() {
                    @Override
                    public HolderLookup.RegistryLookup<T> parent() {
                        return original;
                    }

                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                        return Optional.of(this.getOrThrow(id));
                    }

                    @Override
                    public HolderSet.Named<T> getOrThrow(TagKey<T> id) {
                        Optional<HolderSet.Named<T>> optional = this.parent().get(id);

                        return (HolderSet.Named) optional.orElseGet(() -> {
                            return HolderSet.emptyNamed(this.parent(), id);
                        });
                    }
                };
            }
        };
    }

    public static void validate() {
        CommandBuildContext commandbuildcontext = createValidationContext(VanillaRegistries.createLookup());
        CommandDispatcher<CommandSourceStack> commanddispatcher = (new Commands(Commands.CommandSelection.ALL, commandbuildcontext)).getDispatcher();
        RootCommandNode<CommandSourceStack> rootcommandnode = commanddispatcher.getRoot();

        commanddispatcher.findAmbiguities((commandnode, commandnode1, commandnode2, collection) -> {
            Commands.LOGGER.warn("Ambiguity between arguments {} and {} with inputs: {}", new Object[]{commanddispatcher.getPath(commandnode1), commanddispatcher.getPath(commandnode2), collection});
        });
        Set<ArgumentType<?>> set = ArgumentUtils.findUsedArgumentTypes(rootcommandnode);
        Set<ArgumentType<?>> set1 = (Set) set.stream().filter((argumenttype) -> {
            return !ArgumentTypeInfos.isClassRecognized(argumenttype.getClass());
        }).collect(Collectors.toSet());

        if (!set1.isEmpty()) {
            Commands.LOGGER.warn("Missing type registration for following arguments:\n {}", set1.stream().map((argumenttype) -> {
                return "\t" + String.valueOf(argumenttype);
            }).collect(Collectors.joining(",\n")));
            throw new IllegalStateException("Unregistered argument types");
        }
    }

    public static <T extends PermissionSetSupplier> PermissionProviderCheck<T> hasPermission(PermissionCheck permission) {
        return new PermissionProviderCheck<T>(permission);
    }

    public static CommandSourceStack createCompilationContext(PermissionSet compilationPermissions) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, (ServerLevel) null, compilationPermissions, "", CommonComponents.EMPTY, (MinecraftServer) null, (Entity) null);
    }

    public static enum CommandSelection {

        ALL(true, true), DEDICATED(false, true), INTEGRATED(true, false);

        private final boolean includeIntegrated;
        private final boolean includeDedicated;

        private CommandSelection(boolean includeIntegrated, boolean includeDedicated) {
            this.includeIntegrated = includeIntegrated;
            this.includeDedicated = includeDedicated;
        }
    }

    @FunctionalInterface
    public interface ParseFunction {

        void parse(StringReader value) throws CommandSyntaxException;
    }
}
