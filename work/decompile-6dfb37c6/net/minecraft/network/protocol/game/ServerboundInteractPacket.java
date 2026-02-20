package net.minecraft.network.protocol.game;

import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ServerboundInteractPacket implements Packet<ServerGamePacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ServerboundInteractPacket> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundInteractPacket>codec(ServerboundInteractPacket::write, ServerboundInteractPacket::new);
    private final int entityId;
    private final ServerboundInteractPacket.Action action;
    private final boolean usingSecondaryAction;
    private static final ServerboundInteractPacket.Action ATTACK_ACTION = new ServerboundInteractPacket.Action() {
        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.ATTACK;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onAttack();
        }

        @Override
        public void write(FriendlyByteBuf output) {}
    };

    private ServerboundInteractPacket(int entityId, boolean usingSecondaryAction, ServerboundInteractPacket.Action action) {
        this.entityId = entityId;
        this.action = action;
        this.usingSecondaryAction = usingSecondaryAction;
    }

    public static ServerboundInteractPacket createAttackPacket(Entity entity, boolean usingSecondaryAction) {
        return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, ServerboundInteractPacket.ATTACK_ACTION);
    }

    public static ServerboundInteractPacket createInteractionPacket(Entity entity, boolean usingSecondaryAction, InteractionHand hand) {
        return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, new ServerboundInteractPacket.InteractionAction(hand));
    }

    public static ServerboundInteractPacket createInteractionPacket(Entity entity, boolean usingSecondaryAction, InteractionHand hand, Vec3 location) {
        return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, new ServerboundInteractPacket.InteractionAtLocationAction(hand, location));
    }

    private ServerboundInteractPacket(FriendlyByteBuf input) {
        this.entityId = input.readVarInt();
        ServerboundInteractPacket.ActionType serverboundinteractpacket_actiontype = (ServerboundInteractPacket.ActionType) input.readEnum(ServerboundInteractPacket.ActionType.class);

        this.action = (ServerboundInteractPacket.Action) serverboundinteractpacket_actiontype.reader.apply(input);
        this.usingSecondaryAction = input.readBoolean();
    }

    private void write(FriendlyByteBuf output) {
        output.writeVarInt(this.entityId);
        output.writeEnum(this.action.getType());
        this.action.write(output);
        output.writeBoolean(this.usingSecondaryAction);
    }

    @Override
    public PacketType<ServerboundInteractPacket> type() {
        return GamePacketTypes.SERVERBOUND_INTERACT;
    }

    public void handle(ServerGamePacketListener listener) {
        listener.handleInteract(this);
    }

    public @Nullable Entity getTarget(ServerLevel level) {
        return level.getEntityOrPart(this.entityId);
    }

    public boolean isUsingSecondaryAction() {
        return this.usingSecondaryAction;
    }

    public boolean isWithinRange(ServerPlayer player, AABB aabb, double buffer) {
        return this.action.getType() == ServerboundInteractPacket.ActionType.ATTACK ? player.isWithinAttackRange(aabb, buffer) : player.isWithinEntityInteractionRange(aabb, buffer);
    }

    public void dispatch(ServerboundInteractPacket.Handler handler) {
        this.action.dispatch(handler);
    }

    private static enum ActionType {

        INTERACT(ServerboundInteractPacket.InteractionAction::new), ATTACK((friendlybytebuf) -> {
            return ServerboundInteractPacket.ATTACK_ACTION;
        }), INTERACT_AT(ServerboundInteractPacket.InteractionAtLocationAction::new);

        private final Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader;

        private ActionType(Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader) {
            this.reader = reader;
        }
    }

    private static class InteractionAction implements ServerboundInteractPacket.Action {

        private final InteractionHand hand;

        private InteractionAction(InteractionHand hand) {
            this.hand = hand;
        }

        private InteractionAction(FriendlyByteBuf input) {
            this.hand = (InteractionHand) input.readEnum(InteractionHand.class);
        }

        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.INTERACT;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onInteraction(this.hand);
        }

        @Override
        public void write(FriendlyByteBuf output) {
            output.writeEnum(this.hand);
        }
    }

    private static class InteractionAtLocationAction implements ServerboundInteractPacket.Action {

        private final InteractionHand hand;
        private final Vec3 location;

        private InteractionAtLocationAction(InteractionHand hand, Vec3 location) {
            this.hand = hand;
            this.location = location;
        }

        private InteractionAtLocationAction(FriendlyByteBuf input) {
            this.location = new Vec3((double) input.readFloat(), (double) input.readFloat(), (double) input.readFloat());
            this.hand = (InteractionHand) input.readEnum(InteractionHand.class);
        }

        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.INTERACT_AT;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onInteraction(this.hand, this.location);
        }

        @Override
        public void write(FriendlyByteBuf output) {
            output.writeFloat((float) this.location.x);
            output.writeFloat((float) this.location.y);
            output.writeFloat((float) this.location.z);
            output.writeEnum(this.hand);
        }
    }

    private interface Action {

        ServerboundInteractPacket.ActionType getType();

        void dispatch(ServerboundInteractPacket.Handler handler);

        void write(FriendlyByteBuf output);
    }

    public interface Handler {

        void onInteraction(InteractionHand hand);

        void onInteraction(InteractionHand hand, Vec3 location);

        void onAttack();
    }
}
