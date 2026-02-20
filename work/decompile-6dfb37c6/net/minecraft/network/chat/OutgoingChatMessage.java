package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {

    Component content();

    void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound chatType);

    static OutgoingChatMessage create(PlayerChatMessage message) {
        return (OutgoingChatMessage) (message.isSystem() ? new OutgoingChatMessage.Disguised(message.decoratedContent()) : new OutgoingChatMessage.Player(message));
    }

    public static record Player(PlayerChatMessage message) implements OutgoingChatMessage {

        @Override
        public Component content() {
            return this.message.decoratedContent();
        }

        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound chatType) {
            PlayerChatMessage playerchatmessage = this.message.filter(filtered);

            if (!playerchatmessage.isFullyFiltered()) {
                player.connection.sendPlayerChatMessage(playerchatmessage, chatType);
            }

        }
    }

    public static record Disguised(Component content) implements OutgoingChatMessage {

        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound chatType) {
            player.connection.sendDisguisedChatMessage(this.content, chatType);
        }
    }
}
