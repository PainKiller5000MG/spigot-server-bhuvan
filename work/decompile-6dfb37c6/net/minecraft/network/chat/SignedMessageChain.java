package net.minecraft.network.chat;

import com.mojang.logging.LogUtils;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Signer;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SignedMessageChain {

    private static final Logger LOGGER = LogUtils.getLogger();
    private @Nullable SignedMessageLink nextLink;
    private Instant lastTimeStamp;

    public SignedMessageChain(UUID profileId, UUID sessionId) {
        this.lastTimeStamp = Instant.EPOCH;
        this.nextLink = SignedMessageLink.root(profileId, sessionId);
    }

    public SignedMessageChain.Encoder encoder(Signer signer) {
        return (signedmessagebody) -> {
            SignedMessageLink signedmessagelink = this.nextLink;

            if (signedmessagelink == null) {
                return null;
            } else {
                this.nextLink = signedmessagelink.advance();
                return new MessageSignature(signer.sign((signatureupdater_output) -> {
                    PlayerChatMessage.updateSignature(signatureupdater_output, signedmessagelink, signedmessagebody);
                }));
            }
        };
    }

    public SignedMessageChain.Decoder decoder(final ProfilePublicKey profilePublicKey) {
        final SignatureValidator signaturevalidator = profilePublicKey.createSignatureValidator();

        return new SignedMessageChain.Decoder() {
            @Override
            public PlayerChatMessage unpack(@Nullable MessageSignature signature, SignedMessageBody body) throws SignedMessageChain.DecodeException {
                if (signature == null) {
                    throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.MISSING_PROFILE_KEY);
                } else if (profilePublicKey.data().hasExpired()) {
                    throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.EXPIRED_PROFILE_KEY);
                } else {
                    SignedMessageLink signedmessagelink = SignedMessageChain.this.nextLink;

                    if (signedmessagelink == null) {
                        throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.CHAIN_BROKEN);
                    } else if (body.timeStamp().isBefore(SignedMessageChain.this.lastTimeStamp)) {
                        this.setChainBroken();
                        throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.OUT_OF_ORDER_CHAT);
                    } else {
                        SignedMessageChain.this.lastTimeStamp = body.timeStamp();
                        PlayerChatMessage playerchatmessage = new PlayerChatMessage(signedmessagelink, signature, body, (Component) null, FilterMask.PASS_THROUGH);

                        if (!playerchatmessage.verify(signaturevalidator)) {
                            this.setChainBroken();
                            throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.INVALID_SIGNATURE);
                        } else {
                            if (playerchatmessage.hasExpiredServer(Instant.now())) {
                                SignedMessageChain.LOGGER.warn("Received expired chat: '{}'. Is the client/server system time unsynchronized?", body.content());
                            }

                            SignedMessageChain.this.nextLink = signedmessagelink.advance();
                            return playerchatmessage;
                        }
                    }
                }
            }

            @Override
            public void setChainBroken() {
                SignedMessageChain.this.nextLink = null;
            }
        };
    }

    @FunctionalInterface
    public interface Encoder {

        SignedMessageChain.Encoder UNSIGNED = (signedmessagebody) -> {
            return null;
        };

        @Nullable
        MessageSignature pack(SignedMessageBody body);
    }

    @FunctionalInterface
    public interface Decoder {

        static SignedMessageChain.Decoder unsigned(UUID profileId, BooleanSupplier enforcesSecureChat) {
            return (messagesignature, signedmessagebody) -> {
                if (enforcesSecureChat.getAsBoolean()) {
                    throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.MISSING_PROFILE_KEY);
                } else {
                    return PlayerChatMessage.unsigned(profileId, signedmessagebody.content());
                }
            };
        }

        PlayerChatMessage unpack(@Nullable MessageSignature signature, SignedMessageBody body) throws SignedMessageChain.DecodeException;

        default void setChainBroken() {}
    }

    public static class DecodeException extends ThrowingComponent {

        private static final Component MISSING_PROFILE_KEY = Component.translatable("chat.disabled.missingProfileKey");
        private static final Component CHAIN_BROKEN = Component.translatable("chat.disabled.chain_broken");
        private static final Component EXPIRED_PROFILE_KEY = Component.translatable("chat.disabled.expiredProfileKey");
        private static final Component INVALID_SIGNATURE = Component.translatable("chat.disabled.invalid_signature");
        private static final Component OUT_OF_ORDER_CHAT = Component.translatable("chat.disabled.out_of_order_chat");

        public DecodeException(Component component) {
            super(component);
        }
    }
}
