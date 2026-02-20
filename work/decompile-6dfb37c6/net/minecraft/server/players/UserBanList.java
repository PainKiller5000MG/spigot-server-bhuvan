package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class UserBanList extends StoredUserList<NameAndId, UserBanListEntry> {

    public UserBanList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<NameAndId> createEntry(JsonObject object) {
        return new UserBanListEntry(object);
    }

    public boolean isBanned(NameAndId user) {
        return this.contains(user);
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

    public boolean add(UserBanListEntry infos) {
        if (super.add(infos)) {
            if (infos.getUser() != null) {
                this.notificationService.playerBanned(infos);
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean remove(NameAndId user) {
        if (super.remove(user)) {
            this.notificationService.playerUnbanned(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (UserBanListEntry userbanlistentry : this.getEntries()) {
            if (userbanlistentry.getUser() != null) {
                this.notificationService.playerUnbanned((NameAndId) userbanlistentry.getUser());
            }
        }

        super.clear();
    }
}
