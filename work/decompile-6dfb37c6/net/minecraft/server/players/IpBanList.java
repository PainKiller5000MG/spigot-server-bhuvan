package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.SocketAddress;
import net.minecraft.server.notifications.NotificationService;
import org.jspecify.annotations.Nullable;

public class IpBanList extends StoredUserList<String, IpBanListEntry> {

    public IpBanList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<String> createEntry(JsonObject object) {
        return new IpBanListEntry(object);
    }

    public boolean isBanned(SocketAddress address) {
        String s = this.getIpFromAddress(address);

        return this.contains(s);
    }

    public boolean isBanned(String ip) {
        return this.contains(ip);
    }

    public @Nullable IpBanListEntry get(SocketAddress address) {
        String s = this.getIpFromAddress(address);

        return (IpBanListEntry) this.get((Object) s);
    }

    private String getIpFromAddress(SocketAddress address) {
        String s = address.toString();

        if (s.contains("/")) {
            s = s.substring(s.indexOf(47) + 1);
        }

        if (s.contains(":")) {
            s = s.substring(0, s.indexOf(58));
        }

        return s;
    }

    public boolean add(IpBanListEntry infos) {
        if (super.add(infos)) {
            if (infos.getUser() != null) {
                this.notificationService.ipBanned(infos);
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean remove(String ip) {
        if (super.remove(ip)) {
            this.notificationService.ipUnbanned(ip);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (IpBanListEntry ipbanlistentry : this.getEntries()) {
            if (ipbanlistentry.getUser() != null) {
                this.notificationService.ipUnbanned((String) ipbanlistentry.getUser());
            }
        }

        super.clear();
    }
}
