package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public record PlayerChatMessage(SignedMessageLink link, @Nullable MessageSignature signature, SignedMessageBody signedBody, @Nullable Component unsignedContent, FilterMask filterMask) {

    public static final MapCodec<PlayerChatMessage> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(SignedMessageLink.CODEC.fieldOf("link").forGetter(PlayerChatMessage::link), MessageSignature.CODEC.optionalFieldOf("signature").forGetter((playerchatmessage) -> {
            return Optional.ofNullable(playerchatmessage.signature);
        }), SignedMessageBody.MAP_CODEC.forGetter(PlayerChatMessage::signedBody), ComponentSerialization.CODEC.optionalFieldOf("unsigned_content").forGetter((playerchatmessage) -> {
            return Optional.ofNullable(playerchatmessage.unsignedContent);
        }), FilterMask.CODEC.optionalFieldOf("filter_mask", FilterMask.PASS_THROUGH).forGetter(PlayerChatMessage::filterMask)).apply(instance, (signedmessagelink, optional, signedmessagebody, optional1, filtermask) -> {
            return new PlayerChatMessage(signedmessagelink, (MessageSignature) optional.orElse((Object) null), signedmessagebody, (Component) optional1.orElse((Object) null), filtermask);
        });
    });
    private static final UUID SYSTEM_SENDER = Util.NIL_UUID;
    public static final Duration MESSAGE_EXPIRES_AFTER_SERVER = Duration.ofMinutes(5L);
    public static final Duration MESSAGE_EXPIRES_AFTER_CLIENT = PlayerChatMessage.MESSAGE_EXPIRES_AFTER_SERVER.plus(Duration.ofMinutes(2L));

    public static PlayerChatMessage system(String content) {
        return unsigned(PlayerChatMessage.SYSTEM_SENDER, content);
    }

    public static PlayerChatMessage unsigned(UUID sender, String content) {
        SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(content);
        SignedMessageLink signedmessagelink = SignedMessageLink.unsigned(sender);

        return new PlayerChatMessage(signedmessagelink, (MessageSignature) null, signedmessagebody, (Component) null, FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage withUnsignedContent(Component content) {
        Component component1 = !content.equals(Component.literal(this.signedContent())) ? content : null;

        return new PlayerChatMessage(this.link, this.signature, this.signedBody, component1, this.filterMask);
    }

    public PlayerChatMessage removeUnsignedContent() {
        return this.unsignedContent != null ? new PlayerChatMessage(this.link, this.signature, this.signedBody, (Component) null, this.filterMask) : this;
    }

    public PlayerChatMessage filter(FilterMask filterMask) {
        return this.filterMask.equals(filterMask) ? this : new PlayerChatMessage(this.link, this.signature, this.signedBody, this.unsignedContent, filterMask);
    }

    public PlayerChatMessage filter(boolean filtered) {
        return this.filter(filtered ? this.filterMask : FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage removeSignature() {
        SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(this.signedContent());
        SignedMessageLink signedmessagelink = SignedMessageLink.unsigned(this.sender());

        return new PlayerChatMessage(signedmessagelink, (MessageSignature) null, signedmessagebody, this.unsignedContent, this.filterMask);
    }

    public static void updateSignature(SignatureUpdater.Output output, SignedMessageLink link, SignedMessageBody body) throws SignatureException {
        output.update(Ints.toByteArray(1));
        link.updateSignature(output);
        body.updateSignature(output);
    }

    public boolean verify(SignatureValidator signatureValidator) {
        return this.signature != null && this.signature.verify(signatureValidator, (signatureupdater_output) -> {
            updateSignature(signatureupdater_output, this.link, this.signedBody);
        });
    }

    public String signedContent() {
        return this.signedBody.content();
    }

    public Component decoratedContent() {
        return (Component) Objects.requireNonNullElseGet(this.unsignedContent, () -> {
            return Component.literal(this.signedContent());
        });
    }

    public Instant timeStamp() {
        return this.signedBody.timeStamp();
    }

    public long salt() {
        return this.signedBody.salt();
    }

    public boolean hasExpiredServer(Instant now) {
        return now.isAfter(this.timeStamp().plus(PlayerChatMessage.MESSAGE_EXPIRES_AFTER_SERVER));
    }

    public boolean hasExpiredClient(Instant now) {
        return now.isAfter(this.timeStamp().plus(PlayerChatMessage.MESSAGE_EXPIRES_AFTER_CLIENT));
    }

    public UUID sender() {
        return this.link.sender();
    }

    public boolean isSystem() {
        return this.sender().equals(PlayerChatMessage.SYSTEM_SENDER);
    }

    public boolean hasSignature() {
        return this.signature != null;
    }

    public boolean hasSignatureFrom(UUID profileId) {
        return this.hasSignature() && this.link.sender().equals(profileId);
    }

    public boolean isFullyFiltered() {
        return this.filterMask.isFullyFiltered();
    }

    public static String describeSigned(PlayerChatMessage message) {
        String s = message.signedBody.content();

        return "'" + s + "' @ " + String.valueOf(message.signedBody.timeStamp()) + "\n - From: " + String.valueOf(message.link.sender()) + "/" + String.valueOf(message.link.sessionId()) + ", message #" + message.link.index() + "\n - Salt: " + message.signedBody.salt() + "\n - Signature: " + MessageSignature.describe(message.signature) + "\n - Last Seen: [\n" + (String) message.signedBody.lastSeen().entries().stream().map((messagesignature) -> {
            return "     " + MessageSignature.describe(messagesignature) + "\n";
        }).collect(Collectors.joining()) + " ]\n";
    }
}
