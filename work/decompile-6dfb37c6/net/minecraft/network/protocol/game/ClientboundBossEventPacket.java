package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.BossEvent;

public class ClientboundBossEventPacket implements Packet<ClientGamePacketListener> {

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBossEventPacket> STREAM_CODEC = Packet.<RegistryFriendlyByteBuf, ClientboundBossEventPacket>codec(ClientboundBossEventPacket::write, ClientboundBossEventPacket::new);
    private static final int FLAG_DARKEN = 1;
    private static final int FLAG_MUSIC = 2;
    private static final int FLAG_FOG = 4;
    private final UUID id;
    private final ClientboundBossEventPacket.Operation operation;
    private static final ClientboundBossEventPacket.Operation REMOVE_OPERATION = new ClientboundBossEventPacket.Operation() {
        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.REMOVE;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.remove(id);
        }

        @Override
        public void write(RegistryFriendlyByteBuf output) {}
    };

    private ClientboundBossEventPacket(UUID id, ClientboundBossEventPacket.Operation operation) {
        this.id = id;
        this.operation = operation;
    }

    private ClientboundBossEventPacket(RegistryFriendlyByteBuf input) {
        this.id = input.readUUID();
        ClientboundBossEventPacket.OperationType clientboundbosseventpacket_operationtype = (ClientboundBossEventPacket.OperationType) input.readEnum(ClientboundBossEventPacket.OperationType.class);

        this.operation = clientboundbosseventpacket_operationtype.reader.decode(input);
    }

    public static ClientboundBossEventPacket createAddPacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.AddOperation(event));
    }

    public static ClientboundBossEventPacket createRemovePacket(UUID id) {
        return new ClientboundBossEventPacket(id, ClientboundBossEventPacket.REMOVE_OPERATION);
    }

    public static ClientboundBossEventPacket createUpdateProgressPacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdateProgressOperation(event.getProgress()));
    }

    public static ClientboundBossEventPacket createUpdateNamePacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdateNameOperation(event.getName()));
    }

    public static ClientboundBossEventPacket createUpdateStylePacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdateStyleOperation(event.getColor(), event.getOverlay()));
    }

    public static ClientboundBossEventPacket createUpdatePropertiesPacket(BossEvent event) {
        return new ClientboundBossEventPacket(event.getId(), new ClientboundBossEventPacket.UpdatePropertiesOperation(event.shouldDarkenScreen(), event.shouldPlayBossMusic(), event.shouldCreateWorldFog()));
    }

    private void write(RegistryFriendlyByteBuf output) {
        output.writeUUID(this.id);
        output.writeEnum(this.operation.getType());
        this.operation.write(output);
    }

    private static int encodeProperties(boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
        int i = 0;

        if (darkenScreen) {
            i |= 1;
        }

        if (playMusic) {
            i |= 2;
        }

        if (createWorldFog) {
            i |= 4;
        }

        return i;
    }

    @Override
    public PacketType<ClientboundBossEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BOSS_EVENT;
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleBossUpdate(this);
    }

    public void dispatch(ClientboundBossEventPacket.Handler handler) {
        this.operation.dispatch(this.id, handler);
    }

    private static enum OperationType {

        ADD(ClientboundBossEventPacket.AddOperation::new), REMOVE((registryfriendlybytebuf) -> {
            return ClientboundBossEventPacket.REMOVE_OPERATION;
        }), UPDATE_PROGRESS(ClientboundBossEventPacket.UpdateProgressOperation::new), UPDATE_NAME(ClientboundBossEventPacket.UpdateNameOperation::new), UPDATE_STYLE(ClientboundBossEventPacket.UpdateStyleOperation::new), UPDATE_PROPERTIES(ClientboundBossEventPacket.UpdatePropertiesOperation::new);

        private final StreamDecoder<RegistryFriendlyByteBuf, ClientboundBossEventPacket.Operation> reader;

        private OperationType(StreamDecoder<RegistryFriendlyByteBuf, ClientboundBossEventPacket.Operation> reader) {
            this.reader = reader;
        }
    }

    public interface Handler {

        default void add(UUID id, Component name, float progress, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {}

        default void remove(UUID id) {}

        default void updateProgress(UUID id, float progress) {}

        default void updateName(UUID id, Component name) {}

        default void updateStyle(UUID id, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {}

        default void updateProperties(UUID id, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {}
    }

    private static class AddOperation implements ClientboundBossEventPacket.Operation {

        private final Component name;
        private final float progress;
        private final BossEvent.BossBarColor color;
        private final BossEvent.BossBarOverlay overlay;
        private final boolean darkenScreen;
        private final boolean playMusic;
        private final boolean createWorldFog;

        private AddOperation(BossEvent event) {
            this.name = event.getName();
            this.progress = event.getProgress();
            this.color = event.getColor();
            this.overlay = event.getOverlay();
            this.darkenScreen = event.shouldDarkenScreen();
            this.playMusic = event.shouldPlayBossMusic();
            this.createWorldFog = event.shouldCreateWorldFog();
        }

        private AddOperation(RegistryFriendlyByteBuf input) {
            this.name = (Component) ComponentSerialization.TRUSTED_STREAM_CODEC.decode(input);
            this.progress = input.readFloat();
            this.color = (BossEvent.BossBarColor) input.readEnum(BossEvent.BossBarColor.class);
            this.overlay = (BossEvent.BossBarOverlay) input.readEnum(BossEvent.BossBarOverlay.class);
            int i = input.readUnsignedByte();

            this.darkenScreen = (i & 1) > 0;
            this.playMusic = (i & 2) > 0;
            this.createWorldFog = (i & 4) > 0;
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.ADD;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.add(id, this.name, this.progress, this.color, this.overlay, this.darkenScreen, this.playMusic, this.createWorldFog);
        }

        @Override
        public void write(RegistryFriendlyByteBuf output) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(output, this.name);
            output.writeFloat(this.progress);
            output.writeEnum(this.color);
            output.writeEnum(this.overlay);
            output.writeByte(ClientboundBossEventPacket.encodeProperties(this.darkenScreen, this.playMusic, this.createWorldFog));
        }
    }

    private static record UpdateProgressOperation(float progress) implements ClientboundBossEventPacket.Operation {

        private UpdateProgressOperation(RegistryFriendlyByteBuf input) {
            this(input.readFloat());
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_PROGRESS;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateProgress(id, this.progress);
        }

        @Override
        public void write(RegistryFriendlyByteBuf output) {
            output.writeFloat(this.progress);
        }
    }

    private static record UpdateNameOperation(Component name) implements ClientboundBossEventPacket.Operation {

        private UpdateNameOperation(RegistryFriendlyByteBuf input) {
            this((Component) ComponentSerialization.TRUSTED_STREAM_CODEC.decode(input));
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_NAME;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateName(id, this.name);
        }

        @Override
        public void write(RegistryFriendlyByteBuf output) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(output, this.name);
        }
    }

    private static class UpdateStyleOperation implements ClientboundBossEventPacket.Operation {

        private final BossEvent.BossBarColor color;
        private final BossEvent.BossBarOverlay overlay;

        private UpdateStyleOperation(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
            this.color = color;
            this.overlay = overlay;
        }

        private UpdateStyleOperation(RegistryFriendlyByteBuf input) {
            this.color = (BossEvent.BossBarColor) input.readEnum(BossEvent.BossBarColor.class);
            this.overlay = (BossEvent.BossBarOverlay) input.readEnum(BossEvent.BossBarOverlay.class);
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_STYLE;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateStyle(id, this.color, this.overlay);
        }

        @Override
        public void write(RegistryFriendlyByteBuf output) {
            output.writeEnum(this.color);
            output.writeEnum(this.overlay);
        }
    }

    private static class UpdatePropertiesOperation implements ClientboundBossEventPacket.Operation {

        private final boolean darkenScreen;
        private final boolean playMusic;
        private final boolean createWorldFog;

        private UpdatePropertiesOperation(boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
            this.darkenScreen = darkenScreen;
            this.playMusic = playMusic;
            this.createWorldFog = createWorldFog;
        }

        private UpdatePropertiesOperation(RegistryFriendlyByteBuf input) {
            int i = input.readUnsignedByte();

            this.darkenScreen = (i & 1) > 0;
            this.playMusic = (i & 2) > 0;
            this.createWorldFog = (i & 4) > 0;
        }

        @Override
        public ClientboundBossEventPacket.OperationType getType() {
            return ClientboundBossEventPacket.OperationType.UPDATE_PROPERTIES;
        }

        @Override
        public void dispatch(UUID id, ClientboundBossEventPacket.Handler handler) {
            handler.updateProperties(id, this.darkenScreen, this.playMusic, this.createWorldFog);
        }

        @Override
        public void write(RegistryFriendlyByteBuf output) {
            output.writeByte(ClientboundBossEventPacket.encodeProperties(this.darkenScreen, this.playMusic, this.createWorldFog));
        }
    }

    private interface Operation {

        ClientboundBossEventPacket.OperationType getType();

        void dispatch(UUID id, ClientboundBossEventPacket.Handler handler);

        void write(RegistryFriendlyByteBuf output);
    }
}
