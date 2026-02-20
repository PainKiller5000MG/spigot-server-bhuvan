package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    public static final String EXPIRES_NEVER = "forever";
    protected final Date created;
    protected final String source;
    protected final @Nullable Date expires;
    protected final @Nullable String reason;

    public BanListEntry(@Nullable T user, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason) {
        super(user);
        this.created = created == null ? new Date() : created;
        this.source = source == null ? "(Unknown)" : source;
        this.expires = expires;
        this.reason = reason;
    }

    protected BanListEntry(@Nullable T user, JsonObject object) {
        super(user);

        Date date;

        try {
            date = object.has("created") ? BanListEntry.DATE_FORMAT.parse(object.get("created").getAsString()) : new Date();
        } catch (ParseException parseexception) {
            date = new Date();
        }

        this.created = date;
        this.source = object.has("source") ? object.get("source").getAsString() : "(Unknown)";

        Date date1;

        try {
            date1 = object.has("expires") ? BanListEntry.DATE_FORMAT.parse(object.get("expires").getAsString()) : null;
        } catch (ParseException parseexception1) {
            date1 = null;
        }

        this.expires = date1;
        this.reason = object.has("reason") ? object.get("reason").getAsString() : null;
    }

    public Date getCreated() {
        return this.created;
    }

    public String getSource() {
        return this.source;
    }

    public @Nullable Date getExpires() {
        return this.expires;
    }

    public @Nullable String getReason() {
        return this.reason;
    }

    public Component getReasonMessage() {
        String s = this.getReason();

        return s == null ? Component.translatable("multiplayer.disconnect.banned.reason.default") : Component.literal(s);
    }

    public abstract Component getDisplayName();

    @Override
    boolean hasExpired() {
        return this.expires == null ? false : this.expires.before(new Date());
    }

    @Override
    protected void serialize(JsonObject object) {
        object.addProperty("created", BanListEntry.DATE_FORMAT.format(this.created));
        object.addProperty("source", this.source);
        object.addProperty("expires", this.expires == null ? "forever" : BanListEntry.DATE_FORMAT.format(this.expires));
        object.addProperty("reason", this.reason);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            BanListEntry<?> banlistentry = (BanListEntry) o;

            return Objects.equals(this.source, banlistentry.source) && Objects.equals(this.expires, banlistentry.expires) && Objects.equals(this.reason, banlistentry.reason) && Objects.equals(this.getUser(), banlistentry.getUser());
        } else {
            return false;
        }
    }
}
