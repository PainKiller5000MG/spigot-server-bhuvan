package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

public class LastSeenMessagesValidator {

    private final int lastSeenCount;
    private final ObjectList<LastSeenTrackedEntry> trackedMessages = new ObjectArrayList();
    private @Nullable MessageSignature lastPendingMessage;

    public LastSeenMessagesValidator(int lastSeenCount) {
        this.lastSeenCount = lastSeenCount;

        for (int j = 0; j < lastSeenCount; ++j) {
            this.trackedMessages.add((Object) null);
        }

    }

    public void addPending(MessageSignature message) {
        if (!message.equals(this.lastPendingMessage)) {
            this.trackedMessages.add(new LastSeenTrackedEntry(message, true));
            this.lastPendingMessage = message;
        }

    }

    public int trackedMessagesCount() {
        return this.trackedMessages.size();
    }

    public void applyOffset(int offset) throws LastSeenMessagesValidator.ValidationException {
        int j = this.trackedMessages.size() - this.lastSeenCount;

        if (offset >= 0 && offset <= j) {
            this.trackedMessages.removeElements(0, offset);
        } else {
            throw new LastSeenMessagesValidator.ValidationException("Advanced last seen window by " + offset + " messages, but expected at most " + j);
        }
    }

    public LastSeenMessages applyUpdate(LastSeenMessages.Update update) throws LastSeenMessagesValidator.ValidationException {
        this.applyOffset(update.offset());
        ObjectList<MessageSignature> objectlist = new ObjectArrayList(update.acknowledged().cardinality());

        if (update.acknowledged().length() > this.lastSeenCount) {
            int i = update.acknowledged().length();

            throw new LastSeenMessagesValidator.ValidationException("Last seen update contained " + i + " messages, but maximum window size is " + this.lastSeenCount);
        } else {
            for (int j = 0; j < this.lastSeenCount; ++j) {
                boolean flag = update.acknowledged().get(j);
                LastSeenTrackedEntry lastseentrackedentry = (LastSeenTrackedEntry) this.trackedMessages.get(j);

                if (flag) {
                    if (lastseentrackedentry == null) {
                        throw new LastSeenMessagesValidator.ValidationException("Last seen update acknowledged unknown or previously ignored message at index " + j);
                    }

                    this.trackedMessages.set(j, lastseentrackedentry.acknowledge());
                    objectlist.add(lastseentrackedentry.signature());
                } else {
                    if (lastseentrackedentry != null && !lastseentrackedentry.pending()) {
                        throw new LastSeenMessagesValidator.ValidationException("Last seen update ignored previously acknowledged message at index " + j + " and signature " + String.valueOf(lastseentrackedentry.signature()));
                    }

                    this.trackedMessages.set(j, (Object) null);
                }
            }

            LastSeenMessages lastseenmessages = new LastSeenMessages(objectlist);

            if (!update.verifyChecksum(lastseenmessages)) {
                throw new LastSeenMessagesValidator.ValidationException("Checksum mismatch on last seen update: the client and server must have desynced");
            } else {
                return lastseenmessages;
            }
        }
    }

    public static class ValidationException extends Exception {

        public ValidationException(String message) {
            super(message);
        }
    }
}
