package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

public class FetchProfileCommand {

    public FetchProfileCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands.literal("fetchprofile").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))).then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString()).executes((commandcontext) -> {
            return resolveName((CommandSourceStack) commandcontext.getSource(), StringArgumentType.getString(commandcontext, "name"));
        })))).then(Commands.literal("id").then(Commands.argument("id", UuidArgument.uuid()).executes((commandcontext) -> {
            return resolveId((CommandSourceStack) commandcontext.getSource(), UuidArgument.getUuid(commandcontext, "id"));
        }))));
    }

    private static void reportResolvedProfile(CommandSourceStack sender, GameProfile gameProfile, String messageId, Component argument) {
        ResolvableProfile resolvableprofile = ResolvableProfile.createResolved(gameProfile);

        ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, resolvableprofile).ifSuccess((tag) -> {
            String s1 = tag.toString();
            MutableComponent mutablecomponent = Component.object(new PlayerSprite(resolvableprofile, true));

            ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, mutablecomponent).ifSuccess((tag1) -> {
                String s2 = tag1.toString();

                sender.sendSuccess(() -> {
                    Component component1 = ComponentUtils.formatList(List.of(Component.translatable("commands.fetchprofile.copy_component").withStyle((style) -> {
                        return style.withClickEvent(new ClickEvent.CopyToClipboard(s1));
                    }), Component.translatable("commands.fetchprofile.give_item").withStyle((style) -> {
                        return style.withClickEvent(new ClickEvent.RunCommand("give @s minecraft:player_head[profile=" + s1 + "]"));
                    }), Component.translatable("commands.fetchprofile.summon_mannequin").withStyle((style) -> {
                        return style.withClickEvent(new ClickEvent.RunCommand("summon minecraft:mannequin ~ ~ ~ {profile:" + s1 + "}"));
                    }), Component.translatable("commands.fetchprofile.copy_text", mutablecomponent.withStyle(ChatFormatting.WHITE)).withStyle((style) -> {
                        return style.withClickEvent(new ClickEvent.CopyToClipboard(s2));
                    })), CommonComponents.SPACE, (mutablecomponent1) -> {
                        return ComponentUtils.wrapInSquareBrackets(mutablecomponent1.withStyle(ChatFormatting.GREEN));
                    });

                    return Component.translatable(messageId, argument, component1);
                }, false);
            }).ifError((error) -> {
                sender.sendFailure(Component.translatable("commands.fetchprofile.failed_to_serialize", error.message()));
            });
        }).ifError((error) -> {
            sender.sendFailure(Component.translatable("commands.fetchprofile.failed_to_serialize", error.message()));
        });
    }

    private static int resolveName(CommandSourceStack source, String name) {
        MinecraftServer minecraftserver = source.getServer();
        ProfileResolver profileresolver = minecraftserver.services().profileResolver();

        Util.nonCriticalIoPool().execute(() -> {
            Component component = Component.literal(name);
            Optional<GameProfile> optional = profileresolver.fetchByName(name);

            minecraftserver.execute(() -> {
                optional.ifPresentOrElse((gameprofile) -> {
                    reportResolvedProfile(source, gameprofile, "commands.fetchprofile.name.success", component);
                }, () -> {
                    source.sendFailure(Component.translatable("commands.fetchprofile.name.failure", component));
                });
            });
        });
        return 1;
    }

    private static int resolveId(CommandSourceStack source, UUID id) {
        MinecraftServer minecraftserver = source.getServer();
        ProfileResolver profileresolver = minecraftserver.services().profileResolver();

        Util.nonCriticalIoPool().execute(() -> {
            Component component = Component.translationArg(id);
            Optional<GameProfile> optional = profileresolver.fetchById(id);

            minecraftserver.execute(() -> {
                optional.ifPresentOrElse((gameprofile) -> {
                    reportResolvedProfile(source, gameprofile, "commands.fetchprofile.id.success", component);
                }, () -> {
                    source.sendFailure(Component.translatable("commands.fetchprofile.id.failure", component));
                });
            });
        });
        return 1;
    }
}
