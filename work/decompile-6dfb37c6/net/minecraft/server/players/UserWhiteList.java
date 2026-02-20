package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class UserWhiteList extends StoredUserList<NameAndId, UserWhiteListEntry> {

    public UserWhiteList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<NameAndId> createEntry(JsonObject object) {
        return new UserWhiteListEntry(object);
    }

    public boolean isWhiteListed(NameAndId user) {
        return this.contains(user);
    }

    public boolean add(UserWhiteListEntry infos) {
        if (super.add(infos)) {
            if (infos.getUser() != null) {
                this.notificationService.playerAddedToAllowlist((NameAndId) infos.getUser());
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean remove(NameAndId user) {
        if (super.remove(user)) {
            this.notificationService.playerRemovedFromAllowlist(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (UserWhiteListEntry userwhitelistentry : this.getEntries()) {
            if (userwhitelistentry.getUser() != null) {
                this.notificationService.playerRemovedFromAllowlist((NameAndId) userwhitelistentry.getUser());
            }
        }

        super.clear();
    }

    @Override
    public String[] getUserList() {
        return (String[]) this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray((i) -> {
            return new String[i];
        });
    }

    protected String getKeyForUser(NameAndId user) {
        return user.id().toString();
    }
}
