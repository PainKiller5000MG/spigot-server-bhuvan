package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final File file;
    private final Map<String, V> map = Maps.newHashMap();
    protected final NotificationService notificationService;

    public StoredUserList(File file, NotificationService notificationService) {
        this.file = file;
        this.notificationService = notificationService;
    }

    public File getFile() {
        return this.file;
    }

    public boolean add(V infos) {
        String s = this.getKeyForUser(((StoredUserEntry) infos).getUser());
        V v1 = (V) (this.map.get(s));

        if (infos.equals(v1)) {
            return false;
        } else {
            this.map.put(s, infos);

            try {
                this.save();
            } catch (IOException ioexception) {
                StoredUserList.LOGGER.warn("Could not save the list after adding a user.", ioexception);
            }

            return true;
        }
    }

    public @Nullable V get(K user) {
        this.removeExpired();
        return (V) (this.map.get(this.getKeyForUser(user)));
    }

    public boolean remove(K user) {
        V v0 = (V) (this.map.remove(this.getKeyForUser(user)));

        if (v0 == null) {
            return false;
        } else {
            try {
                this.save();
            } catch (IOException ioexception) {
                StoredUserList.LOGGER.warn("Could not save the list after removing a user.", ioexception);
            }

            return true;
        }
    }

    public boolean remove(StoredUserEntry<K> infos) {
        return this.remove(Objects.requireNonNull(infos.getUser()));
    }

    public void clear() {
        this.map.clear();

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after removing a user.", ioexception);
        }

    }

    public String[] getUserList() {
        return (String[]) this.map.keySet().toArray(new String[0]);
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    protected String getKeyForUser(K user) {
        return user.toString();
    }

    protected boolean contains(K user) {
        return this.map.containsKey(this.getKeyForUser(user));
    }

    private void removeExpired() {
        List<K> list = Lists.newArrayList();

        for (V v0 : this.map.values()) {
            if (v0.hasExpired()) {
                list.add(((StoredUserEntry) v0).getUser());
            }
        }

        for (K k0 : list) {
            this.map.remove(this.getKeyForUser(k0));
        }

    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject object);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        JsonArray jsonarray = new JsonArray();
        Stream stream = this.map.values().stream().map((storeduserentry) -> {
            JsonObject jsonobject = new JsonObject();

            Objects.requireNonNull(storeduserentry);
            return (JsonObject) Util.make(jsonobject, storeduserentry::serialize);
        });

        Objects.requireNonNull(jsonarray);
        stream.forEach(jsonarray::add);

        try (BufferedWriter bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            StoredUserList.GSON.toJson(jsonarray, StoredUserList.GSON.newJsonWriter(bufferedwriter));
        }

    }

    public void load() throws IOException {
        if (this.file.exists()) {
            try (BufferedReader bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                this.map.clear();
                JsonArray jsonarray = (JsonArray) StoredUserList.GSON.fromJson(bufferedreader, JsonArray.class);

                if (jsonarray == null) {
                    return;
                }

                for (JsonElement jsonelement : jsonarray) {
                    JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "entry");
                    StoredUserEntry<K> storeduserentry = this.createEntry(jsonobject);

                    if (storeduserentry.getUser() != null) {
                        this.map.put(this.getKeyForUser(storeduserentry.getUser()), storeduserentry);
                    }
                }
            }

        }
    }
}
