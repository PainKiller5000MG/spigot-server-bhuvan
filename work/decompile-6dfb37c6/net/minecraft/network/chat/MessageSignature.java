package net.minecraft.network.chat;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;
import org.jspecify.annotations.Nullable;

public record MessageSignature(byte[] bytes) {

    public static final Codec<MessageSignature> CODEC = ExtraCodecs.BASE64_STRING.xmap(MessageSignature::new, MessageSignature::bytes);
    public static final int BYTES = 256;

    public MessageSignature {
        Preconditions.checkState(bytes.length == 256, "Invalid message signature size");
    }

    public static MessageSignature read(FriendlyByteBuf input) {
        byte[] abyte = new byte[256];

        input.readBytes(abyte);
        return new MessageSignature(abyte);
    }

    public static void write(FriendlyByteBuf output, MessageSignature signature) {
        output.writeBytes(signature.bytes);
    }

    public boolean verify(SignatureValidator signature, SignatureUpdater updater) {
        return signature.validate(updater, this.bytes);
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(this.bytes);
    }

    public boolean equals(Object o) {
        boolean flag;

        if (this != o) {
            label26:
            {
                if (o instanceof MessageSignature) {
                    MessageSignature messagesignature = (MessageSignature) o;

                    if (Arrays.equals(this.bytes, messagesignature.bytes)) {
                        break label26;
                    }
                }

                flag = false;
                return flag;
            }
        }

        flag = true;
        return flag;
    }

    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }

    public String toString() {
        return Base64.getEncoder().encodeToString(this.bytes);
    }

    public static String describe(@Nullable MessageSignature signature) {
        return signature == null ? "<no signature>" : signature.toString();
    }

    public MessageSignature.Packed pack(MessageSignatureCache cache) {
        int i = cache.pack(this);

        return i != -1 ? new MessageSignature.Packed(i) : new MessageSignature.Packed(this);
    }

    public int checksum() {
        return Arrays.hashCode(this.bytes);
    }

    public static record Packed(int id, @Nullable MessageSignature fullSignature) {

        public static final int FULL_SIGNATURE = -1;

        public Packed(MessageSignature signature) {
            this(-1, signature);
        }

        public Packed(int id) {
            this(id, (MessageSignature) null);
        }

        public static MessageSignature.Packed read(FriendlyByteBuf input) {
            int i = input.readVarInt() - 1;

            return i == -1 ? new MessageSignature.Packed(MessageSignature.read(input)) : new MessageSignature.Packed(i);
        }

        public static void write(FriendlyByteBuf output, MessageSignature.Packed packed) {
            output.writeVarInt(packed.id() + 1);
            if (packed.fullSignature() != null) {
                MessageSignature.write(output, packed.fullSignature());
            }

        }

        public Optional<MessageSignature> unpack(MessageSignatureCache cache) {
            return this.fullSignature != null ? Optional.of(this.fullSignature) : Optional.ofNullable(cache.unpack(this.id));
        }
    }
}
