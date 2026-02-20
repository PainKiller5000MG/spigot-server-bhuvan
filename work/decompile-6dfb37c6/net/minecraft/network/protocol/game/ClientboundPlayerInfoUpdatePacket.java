package net.minecraft.network.protocol.game;

import com.google.common.base.MoreObjects;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.Optionull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

public class ClientboundPlayerInfoUpdatePacket implements Packet<ClientGamePacketListener> {

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerInfoUpdatePacket> STREAM_CODEC = Packet.<RegistryFriendlyByteBuf, ClientboundPlayerInfoUpdatePacket>codec(ClientboundPlayerInfoUpdatePacket::write, ClientboundPlayerInfoUpdatePacket::new);
    private final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions;
    private final List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    public ClientboundPlayerInfoUpdatePacket(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions, Collection<ServerPlayer> players) {
        this.actions = actions;
        this.entries = players.stream().map(ClientboundPlayerInfoUpdatePacket.Entry::new).toList();
    }

    public ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action action, ServerPlayer player) {
        this.actions = EnumSet.of(action);
        this.entries = List.of(new ClientboundPlayerInfoUpdatePacket.Entry(player));
    }

    public static ClientboundPlayerInfoUpdatePacket createPlayerInitializing(Collection<ServerPlayer> players) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> enumset = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);

        return new ClientboundPlayerInfoUpdatePacket(enumset, players);
    }

    private ClientboundPlayerInfoUpdatePacket(RegistryFriendlyByteBuf input) {
        this.actions = input.<ClientboundPlayerInfoUpdatePacket.Action>readEnumSet(ClientboundPlayerInfoUpdatePacket.Action.class);
        this.entries = input.<ClientboundPlayerInfoUpdatePacket.Entry>readList((friendlybytebuf) -> {
            ClientboundPlayerInfoUpdatePacket.EntryBuilder clientboundplayerinfoupdatepacket_entrybuilder = new ClientboundPlayerInfoUpdatePacket.EntryBuilder(friendlybytebuf.readUUID());

            for (ClientboundPlayerInfoUpdatePacket.Action clientboundplayerinfoupdatepacket_action : this.actions) {
                clientboundplayerinfoupdatepacket_action.reader.read(clientboundplayerinfoupdatepacket_entrybuilder, (RegistryFriendlyByteBuf) friendlybytebuf);
            }

            return clientboundplayerinfoupdatepacket_entrybuilder.build();
        });
    }

    private void write(RegistryFriendlyByteBuf output) {
        output.writeEnumSet(this.actions, ClientboundPlayerInfoUpdatePacket.Action.class);
        output.writeCollection(this.entries, (friendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            friendlybytebuf.writeUUID(clientboundplayerinfoupdatepacket_entry.profileId());

            for (ClientboundPlayerInfoUpdatePacket.Action clientboundplayerinfoupdatepacket_action : this.actions) {
                clientboundplayerinfoupdatepacket_action.writer.write((RegistryFriendlyByteBuf) friendlybytebuf, clientboundplayerinfoupdatepacket_entry);
            }

        });
    }

    @Override
    public PacketType<ClientboundPlayerInfoUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE;
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handlePlayerInfoUpdate(this);
    }

    public EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions() {
        return this.actions;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> entries() {
        return this.entries;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries() {
        return this.actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) ? this.entries : List.of();
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("actions", this.actions).add("entries", this.entries).toString();
    }

    public static enum Action {

        ADD_PLAYER((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            String s = (String) ByteBufCodecs.PLAYER_NAME.decode(registryfriendlybytebuf);
            PropertyMap propertymap = (PropertyMap) ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(registryfriendlybytebuf);

            clientboundplayerinfoupdatepacket_entrybuilder.profile = new GameProfile(clientboundplayerinfoupdatepacket_entrybuilder.profileId, s, propertymap);
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            GameProfile gameprofile = (GameProfile) Objects.requireNonNull(clientboundplayerinfoupdatepacket_entry.profile());

            ByteBufCodecs.PLAYER_NAME.encode(registryfriendlybytebuf, gameprofile.name());
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(registryfriendlybytebuf, gameprofile.properties());
        }), INITIALIZE_CHAT((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.chatSession = (RemoteChatSession.Data) registryfriendlybytebuf.readNullable(RemoteChatSession.Data::read);
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            registryfriendlybytebuf.writeNullable(clientboundplayerinfoupdatepacket_entry.chatSession, RemoteChatSession.Data::write);
        }), UPDATE_GAME_MODE((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.gameMode = GameType.byId(registryfriendlybytebuf.readVarInt());
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            registryfriendlybytebuf.writeVarInt(clientboundplayerinfoupdatepacket_entry.gameMode().getId());
        }), UPDATE_LISTED((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.listed = registryfriendlybytebuf.readBoolean();
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            registryfriendlybytebuf.writeBoolean(clientboundplayerinfoupdatepacket_entry.listed());
        }), UPDATE_LATENCY((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.latency = registryfriendlybytebuf.readVarInt();
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            registryfriendlybytebuf.writeVarInt(clientboundplayerinfoupdatepacket_entry.latency());
        }), UPDATE_DISPLAY_NAME((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.displayName = (Component) FriendlyByteBuf.readNullable(registryfriendlybytebuf, ComponentSerialization.TRUSTED_STREAM_CODEC);
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            FriendlyByteBuf.writeNullable(registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry.displayName(), ComponentSerialization.TRUSTED_STREAM_CODEC);
        }), UPDATE_LIST_ORDER((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.listOrder = registryfriendlybytebuf.readVarInt();
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            registryfriendlybytebuf.writeVarInt(clientboundplayerinfoupdatepacket_entry.listOrder);
        }), UPDATE_HAT((clientboundplayerinfoupdatepacket_entrybuilder, registryfriendlybytebuf) -> {
            clientboundplayerinfoupdatepacket_entrybuilder.showHat = registryfriendlybytebuf.readBoolean();
        }, (registryfriendlybytebuf, clientboundplayerinfoupdatepacket_entry) -> {
            registryfriendlybytebuf.writeBoolean(clientboundplayerinfoupdatepacket_entry.showHat);
        });

        private final ClientboundPlayerInfoUpdatePacket.Action.Reader reader;
        private final ClientboundPlayerInfoUpdatePacket.Action.Writer writer;

        private Action(ClientboundPlayerInfoUpdatePacket.Action.Reader reader, ClientboundPlayerInfoUpdatePacket.Action.Writer writer) {
            this.reader = reader;
            this.writer = writer;
        }

        public interface Reader {

            void read(ClientboundPlayerInfoUpdatePacket.EntryBuilder entry, RegistryFriendlyByteBuf input);
        }

        public interface Writer {

            void write(RegistryFriendlyByteBuf output, ClientboundPlayerInfoUpdatePacket.Entry entry);
        }
    }

    public static record Entry(UUID profileId, @Nullable GameProfile profile, boolean listed, int latency, GameType gameMode, @Nullable Component displayName, boolean showHat, int listOrder, RemoteChatSession.@Nullable Data chatSession) {

        private Entry(ServerPlayer player) {
            this(player.getUUID(), player.getGameProfile(), true, player.connection.latency(), player.gameMode(), player.getTabListDisplayName(), player.isModelPartShown(PlayerModelPart.HAT), player.getTabListOrder(), (RemoteChatSession.Data) Optionull.map(player.getChatSession(), RemoteChatSession::asData));
        }
    }

    private static class EntryBuilder {

        private final UUID profileId;
        private @Nullable GameProfile profile;
        private boolean listed;
        private int latency;
        private GameType gameMode;
        private @Nullable Component displayName;
        private boolean showHat;
        private int listOrder;
        private RemoteChatSession.@Nullable Data chatSession;

        private EntryBuilder(UUID profileId) {
            this.gameMode = GameType.DEFAULT_MODE;
            this.profileId = profileId;
        }

        private ClientboundPlayerInfoUpdatePacket.Entry build() {
            return new ClientboundPlayerInfoUpdatePacket.Entry(this.profileId, this.profile, this.listed, this.latency, this.gameMode, this.displayName, this.showHat, this.listOrder, this.chatSession);
        }
    }
}
