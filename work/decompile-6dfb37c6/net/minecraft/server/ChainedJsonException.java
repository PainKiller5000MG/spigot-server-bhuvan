package net.minecraft.server;

import com.google.common.collect.Lists;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class ChainedJsonException extends IOException {

    private final List<ChainedJsonException.Entry> entries = Lists.newArrayList();
    private final String message;

    public ChainedJsonException(String message) {
        this.entries.add(new ChainedJsonException.Entry());
        this.message = message;
    }

    public ChainedJsonException(String message, Throwable cause) {
        super(cause);
        this.entries.add(new ChainedJsonException.Entry());
        this.message = message;
    }

    public void prependJsonKey(String key) {
        ((ChainedJsonException.Entry) this.entries.get(0)).addJsonKey(key);
    }

    public void setFilenameAndFlush(String filename) {
        ((ChainedJsonException.Entry) this.entries.get(0)).filename = filename;
        this.entries.add(0, new ChainedJsonException.Entry());
    }

    public String getMessage() {
        String s = String.valueOf(this.entries.get(this.entries.size() - 1));

        return "Invalid " + s + ": " + this.message;
    }

    public static ChainedJsonException forException(Exception e) {
        if (e instanceof ChainedJsonException) {
            return (ChainedJsonException) e;
        } else {
            String s = e.getMessage();

            if (e instanceof FileNotFoundException) {
                s = "File not found";
            }

            return new ChainedJsonException(s, e);
        }
    }

    public static class Entry {

        private @Nullable String filename;
        private final List<String> jsonKeys = Lists.newArrayList();

        private Entry() {}

        private void addJsonKey(String name) {
            this.jsonKeys.add(0, name);
        }

        public @Nullable String getFilename() {
            return this.filename;
        }

        public String getJsonKeys() {
            return StringUtils.join(this.jsonKeys, "->");
        }

        public String toString() {
            return this.filename != null ? (this.jsonKeys.isEmpty() ? this.filename : this.filename + " " + this.getJsonKeys()) : (this.jsonKeys.isEmpty() ? "(Unknown file)" : "(Unknown file) " + this.getJsonKeys());
        }
    }
}
