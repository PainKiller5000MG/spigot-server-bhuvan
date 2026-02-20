package net.minecraft.commands.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignableCommand;
import org.jspecify.annotations.Nullable;

public record ArgumentSignatures(List<ArgumentSignatures.Entry> entries) {

    public static final ArgumentSignatures EMPTY = new ArgumentSignatures(List.of());
    private static final int MAX_ARGUMENT_COUNT = 8;
    private static final int MAX_ARGUMENT_NAME_LENGTH = 16;

    public ArgumentSignatures(FriendlyByteBuf input) {
        this((List) input.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 8), ArgumentSignatures.Entry::new));
    }

    public void write(FriendlyByteBuf output) {
        output.writeCollection(this.entries, (friendlybytebuf1, argumentsignatures_entry) -> {
            argumentsignatures_entry.write(friendlybytebuf1);
        });
    }

    public static ArgumentSignatures signCommand(SignableCommand<?> command, ArgumentSignatures.Signer signer) {
        List<ArgumentSignatures.Entry> list = command.arguments().stream().map((signablecommand_argument) -> {
            MessageSignature messagesignature = signer.sign(signablecommand_argument.value());

            return messagesignature != null ? new ArgumentSignatures.Entry(signablecommand_argument.name(), messagesignature) : null;
        }).filter(Objects::nonNull).toList();

        return new ArgumentSignatures(list);
    }

    public static record Entry(String name, MessageSignature signature) {

        public Entry(FriendlyByteBuf input) {
            this(input.readUtf(16), MessageSignature.read(input));
        }

        public void write(FriendlyByteBuf output) {
            output.writeUtf(this.name, 16);
            MessageSignature.write(output, this.signature);
        }
    }

    @FunctionalInterface
    public interface Signer {

        @Nullable
        MessageSignature sign(String content);
    }
}
