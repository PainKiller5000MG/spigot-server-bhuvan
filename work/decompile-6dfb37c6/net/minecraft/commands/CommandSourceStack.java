package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CommandSourceStack implements SharedSuggestionProvider, ExecutionCommandSource<CommandSourceStack> {

    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Component.translatable("permissions.requires.entity"));
    public final CommandSource source;
    private final Vec3 worldPosition;
    private final ServerLevel level;
    private final PermissionSet permissions;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final boolean silent;
    private final @Nullable Entity entity;
    private final CommandResultCallback resultCallback;
    private final EntityAnchorArgument.Anchor anchor;
    private final Vec2 rotation;
    private final CommandSigningContext signingContext;
    private final TaskChainer chatMessageChainer;

    public CommandSourceStack(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level, PermissionSet permissions, String textName, Component displayName, MinecraftServer server, @Nullable Entity entity) {
        this(source, position, rotation, level, permissions, textName, displayName, server, entity, false, CommandResultCallback.EMPTY, EntityAnchorArgument.Anchor.FEET, CommandSigningContext.ANONYMOUS, TaskChainer.immediate(server));
    }

    private CommandSourceStack(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level, PermissionSet permissions, String textName, Component displayName, MinecraftServer server, @Nullable Entity entity, boolean silent, CommandResultCallback resultCallback, EntityAnchorArgument.Anchor anchor, CommandSigningContext signingContext, TaskChainer chatMessageChainer) {
        this.source = source;
        this.worldPosition = position;
        this.level = level;
        this.silent = silent;
        this.entity = entity;
        this.permissions = permissions;
        this.textName = textName;
        this.displayName = displayName;
        this.server = server;
        this.resultCallback = resultCallback;
        this.anchor = anchor;
        this.rotation = rotation;
        this.signingContext = signingContext;
        this.chatMessageChainer = chatMessageChainer;
    }

    public CommandSourceStack withSource(CommandSource source) {
        return this.source == source ? this : new CommandSourceStack(source, this.worldPosition, this.rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withEntity(Entity entity) {
        return this.entity == entity ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissions, entity.getPlainTextName(), entity.getDisplayName(), this.server, entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withPosition(Vec3 pos) {
        return this.worldPosition.equals(pos) ? this : new CommandSourceStack(this.source, pos, this.rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withRotation(Vec2 rotation) {
        return this.rotation.equals(rotation) ? this : new CommandSourceStack(this.source, this.worldPosition, rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    @Override
    public CommandSourceStack withCallback(CommandResultCallback resultCallback) {
        return Objects.equals(this.resultCallback, resultCallback) ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withCallback(CommandResultCallback newCallback, BinaryOperator<CommandResultCallback> combiner) {
        CommandResultCallback commandresultcallback1 = (CommandResultCallback) combiner.apply(this.resultCallback, newCallback);

        return this.withCallback(commandresultcallback1);
    }

    public CommandSourceStack withSuppressedOutput() {
        return !this.silent && !this.source.alwaysAccepts() ? new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, true, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer) : this;
    }

    public CommandSourceStack withPermission(PermissionSet permissions) {
        return permissions == this.permissions ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withMaximumPermission(PermissionSet newPermissions) {
        return this.withPermission(this.permissions.union(newPermissions));
    }

    public CommandSourceStack withAnchor(EntityAnchorArgument.Anchor anchor) {
        return anchor == this.anchor ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, anchor, this.signingContext, this.chatMessageChainer);
    }

    public CommandSourceStack withLevel(ServerLevel level) {
        if (level == this.level) {
            return this;
        } else {
            double d0 = DimensionType.getTeleportationScale(this.level.dimensionType(), level.dimensionType());
            Vec3 vec3 = new Vec3(this.worldPosition.x * d0, this.worldPosition.y, this.worldPosition.z * d0);

            return new CommandSourceStack(this.source, vec3, this.rotation, level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, this.signingContext, this.chatMessageChainer);
        }
    }

    public CommandSourceStack facing(Entity entity, EntityAnchorArgument.Anchor anchor) {
        return this.facing(anchor.apply(entity));
    }

    public CommandSourceStack facing(Vec3 pos) {
        Vec3 vec31 = this.anchor.apply(this);
        double d0 = pos.x - vec31.x;
        double d1 = pos.y - vec31.y;
        double d2 = pos.z - vec31.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float f = Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI))));
        float f1 = Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F);

        return this.withRotation(new Vec2(f, f1));
    }

    public CommandSourceStack withSigningContext(CommandSigningContext signingContext, TaskChainer chatMessageChainer) {
        return signingContext == this.signingContext && chatMessageChainer == this.chatMessageChainer ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissions, this.textName, this.displayName, this.server, this.entity, this.silent, this.resultCallback, this.anchor, signingContext, chatMessageChainer);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTextName() {
        return this.textName;
    }

    @Override
    public PermissionSet permissions() {
        return this.permissions;
    }

    public Vec3 getPosition() {
        return this.worldPosition;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public @Nullable Entity getEntity() {
        return this.entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (this.entity == null) {
            throw CommandSourceStack.ERROR_NOT_ENTITY.create();
        } else {
            return this.entity;
        }
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        Entity entity = this.entity;

        if (entity instanceof ServerPlayer serverplayer) {
            return serverplayer;
        } else {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }
    }

    public @Nullable ServerPlayer getPlayer() {
        Entity entity = this.entity;
        ServerPlayer serverplayer;

        if (entity instanceof ServerPlayer serverplayer1) {
            serverplayer = serverplayer1;
        } else {
            serverplayer = null;
        }

        return serverplayer;
    }

    public boolean isPlayer() {
        return this.entity instanceof ServerPlayer;
    }

    public Vec2 getRotation() {
        return this.rotation;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public EntityAnchorArgument.Anchor getAnchor() {
        return this.anchor;
    }

    public CommandSigningContext getSigningContext() {
        return this.signingContext;
    }

    public TaskChainer getChatMessageChainer() {
        return this.chatMessageChainer;
    }

    public boolean shouldFilterMessageTo(ServerPlayer receiver) {
        ServerPlayer serverplayer1 = this.getPlayer();

        return receiver == serverplayer1 ? false : serverplayer1 != null && serverplayer1.isTextFilteringEnabled() || receiver.isTextFilteringEnabled();
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound chatType) {
        if (!this.silent) {
            ServerPlayer serverplayer = this.getPlayer();

            if (serverplayer != null) {
                serverplayer.sendChatMessage(message, filtered, chatType);
            } else {
                this.source.sendSystemMessage(chatType.decorate(message.content()));
            }

        }
    }

    public void sendSystemMessage(Component message) {
        if (!this.silent) {
            ServerPlayer serverplayer = this.getPlayer();

            if (serverplayer != null) {
                serverplayer.sendSystemMessage(message);
            } else {
                this.source.sendSystemMessage(message);
            }

        }
    }

    public void sendSuccess(Supplier<Component> messageSupplier, boolean broadcast) {
        boolean flag1 = this.source.acceptsSuccess() && !this.silent;
        boolean flag2 = broadcast && this.source.shouldInformAdmins() && !this.silent;

        if (flag1 || flag2) {
            Component component = (Component) messageSupplier.get();

            if (flag1) {
                this.source.sendSystemMessage(component);
            }

            if (flag2) {
                this.broadcastToAdmins(component);
            }

        }
    }

    private void broadcastToAdmins(Component message) {
        Component component1 = Component.translatable("chat.type.admin", this.getDisplayName(), message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        GameRules gamerules = this.level.getGameRules();

        if ((Boolean) gamerules.get(GameRules.SEND_COMMAND_FEEDBACK)) {
            for (ServerPlayer serverplayer : this.server.getPlayerList().getPlayers()) {
                if (serverplayer.commandSource() != this.source && this.server.getPlayerList().isOp(serverplayer.nameAndId())) {
                    serverplayer.sendSystemMessage(component1);
                }
            }
        }

        if (this.source != this.server && (Boolean) gamerules.get(GameRules.LOG_ADMIN_COMMANDS)) {
            this.server.sendSystemMessage(component1);
        }

    }

    public void sendFailure(Component message) {
        if (this.source.acceptsFailure() && !this.silent) {
            this.source.sendSystemMessage(Component.empty().append(message).withStyle(ChatFormatting.RED));
        }

    }

    @Override
    public CommandResultCallback callback() {
        return this.resultCallback;
    }

    @Override
    public Collection<String> getOnlinePlayerNames() {
        return Lists.newArrayList(this.server.getPlayerNames());
    }

    @Override
    public Collection<String> getAllTeams() {
        return this.server.getScoreboard().getTeamNames();
    }

    @Override
    public Stream<Identifier> getAvailableSounds() {
        return BuiltInRegistries.SOUND_EVENT.stream().map(SoundEvent::location);
    }

    @Override
    public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context) {
        return Suggestions.empty();
    }

    @Override
    public CompletableFuture<Suggestions> suggestRegistryElements(ResourceKey<? extends Registry<?>> key, SharedSuggestionProvider.ElementSuggestionType elements, SuggestionsBuilder builder, CommandContext<?> context) {
        if (key == Registries.RECIPE) {
            return SharedSuggestionProvider.suggestResource(this.server.getRecipeManager().getRecipes().stream().map((recipeholder) -> {
                return recipeholder.id().identifier();
            }), builder);
        } else if (key == Registries.ADVANCEMENT) {
            Collection<AdvancementHolder> collection = this.server.getAdvancements().getAllAdvancements();

            return SharedSuggestionProvider.suggestResource(collection.stream().map(AdvancementHolder::id), builder);
        } else {
            return (CompletableFuture) this.getLookup(key).map((holderlookup) -> {
                this.suggestRegistryElements(holderlookup, elements, builder);
                return builder.buildFuture();
            }).orElseGet(Suggestions::empty);
        }
    }

    private Optional<? extends HolderLookup<?>> getLookup(ResourceKey<? extends Registry<?>> key) {
        Optional<? extends Registry<?>> optional = this.registryAccess().lookup(key);

        return optional.isPresent() ? optional : this.server.reloadableRegistries().lookup().lookup(key);
    }

    @Override
    public Set<ResourceKey<Level>> levels() {
        return this.server.levelKeys();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.server.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public CommandDispatcher<CommandSourceStack> dispatcher() {
        return this.getServer().getFunctions().getDispatcher();
    }

    @Override
    public void handleError(CommandExceptionType type, Message message, boolean forked, @Nullable TraceCallbacks tracer) {
        if (tracer != null) {
            tracer.onError(message.getString());
        }

        if (!forked) {
            this.sendFailure(ComponentUtils.fromMessage(message));
        }

    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }
}
