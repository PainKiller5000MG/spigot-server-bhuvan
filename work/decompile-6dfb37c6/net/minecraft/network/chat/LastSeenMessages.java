package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.Codec;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SignatureUpdater;

public record LastSeenMessages(List<MessageSignature> entries) {

    public static final Codec<LastSeenMessages> CODEC = MessageSignature.CODEC.listOf().xmap(LastSeenMessages::new, LastSeenMessages::entries);
    public static LastSeenMessages EMPTY = new LastSeenMessages(List.of());
    public static final int LAST_SEEN_MESSAGES_MAX_LENGTH = 20;

    public void updateSignature(SignatureUpdater.Output output) throws SignatureException {
        output.update(Ints.toByteArray(this.entries.size()));

        for (MessageSignature messagesignature : this.entries) {
            output.update(messagesignature.bytes());
        }

    }

    public LastSeenMessages.Packed pack(MessageSignatureCache cache) {
        return new LastSeenMessages.Packed(this.entries.stream().map((messagesignature) -> {
            return messagesignature.pack(cache);
        }).toList());
    }

    public byte computeChecksum() {
        int i = 1;

        for (MessageSignature messagesignature : this.entries) {
            i = 31 * i + messagesignature.checksum();
        }

        byte b0 = (byte) i;

        return b0 == 0 ? 1 : b0;
    }

    public static record Packed(List<MessageSignature.Packed> entries) {

        public static final LastSeenMessages.Packed EMPTY = new LastSeenMessages.Packed(List.of());

        public Packed(FriendlyByteBuf input) {
            this((List) input.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 20), MessageSignature.Packed::read));
        }

        public void write(FriendlyByteBuf output) {
            output.writeCollection(this.entries, MessageSignature.Packed::write);
        }

        public Optional<LastSeenMessages> unpack(MessageSignatureCache cache) {
            List<MessageSignature> list = new ArrayList(this.entries.size());

            for (MessageSignature.Packed messagesignature_packed : this.entries) {
                Optional<MessageSignature> optional = messagesignature_packed.unpack(cache);

                if (optional.isEmpty()) {
                    return Optional.empty();
                }

                list.add((MessageSignature) optional.get());
            }

            return Optional.of(new LastSeenMessages(list));
        }
    }

    public static record Update(int offset, BitSet acknowledged, byte checksum) {

        public static final byte IGNORE_CHECKSUM = 0;

        public Update(FriendlyByteBuf input) {
            this(input.readVarInt(), input.readFixedBitSet(20), input.readByte());
        }

        public void write(FriendlyByteBuf output) {
            output.writeVarInt(this.offset);
            output.writeFixedBitSet(this.acknowledged, 20);
            output.writeByte(this.checksum);
        }

        public boolean verifyChecksum(LastSeenMessages lastSeen) {
            return this.checksum == 0 || this.checksum == lastSeen.computeChecksum();
        }
    }
}
