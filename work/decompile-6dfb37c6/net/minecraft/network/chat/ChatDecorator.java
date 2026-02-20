package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ChatDecorator {

    ChatDecorator PLAIN = (serverplayer, component) -> {
        return component;
    };

    Component decorate(@Nullable ServerPlayer player, Component plain);
}
