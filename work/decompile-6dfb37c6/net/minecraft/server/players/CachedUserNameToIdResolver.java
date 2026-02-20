package net.minecraft.server.players;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraft.util.StringUtil;
import org.slf4j.Logger;

public class CachedUserNameToIdResolver implements UserNameToIdResolver {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GAMEPROFILES_MRU_LIMIT = 1000;
    private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;
    private boolean resolveOfflineUsers = true;
    private final Map<String, CachedUserNameToIdResolver.GameProfileInfo> profilesByName = new ConcurrentHashMap();
    private final Map<UUID, CachedUserNameToIdResolver.GameProfileInfo> profilesByUUID = new ConcurrentHashMap();
    private final GameProfileRepository profileRepository;
    private final Gson gson = (new GsonBuilder()).create();
    private final File file;
    private final AtomicLong operationCount = new AtomicLong();

    public CachedUserNameToIdResolver(GameProfileRepository profileRepository, File file) {
        this.profileRepository = profileRepository;
        this.file = file;
        Lists.reverse(this.load()).forEach(this::safeAdd);
    }

    private void safeAdd(CachedUserNameToIdResolver.GameProfileInfo profileInfo) {
        NameAndId nameandid = profileInfo.nameAndId();

        profileInfo.setLastAccess(this.getNextOperation());
        this.profilesByName.put(nameandid.name().toLowerCase(Locale.ROOT), profileInfo);
        this.profilesByUUID.put(nameandid.id(), profileInfo);
    }

    private Optional<NameAndId> lookupGameProfile(GameProfileRepository profileRepository, String name) {
        if (!StringUtil.isValidPlayerName(name)) {
            return this.createUnknownProfile(name);
        } else {
            Optional<NameAndId> optional = profileRepository.findProfileByName(name).map(NameAndId::new);

            return optional.isEmpty() ? this.createUnknownProfile(name) : optional;
        }
    }

    private Optional<NameAndId> createUnknownProfile(String name) {
        return this.resolveOfflineUsers ? Optional.of(NameAndId.createOffline(name)) : Optional.empty();
    }

    @Override
    public void resolveOfflineUsers(boolean value) {
        this.resolveOfflineUsers = value;
    }

    @Override
    public void add(NameAndId nameAndId) {
        this.addInternal(nameAndId);
    }

    private CachedUserNameToIdResolver.GameProfileInfo addInternal(NameAndId profile) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);

        calendar.setTime(new Date());
        calendar.add(2, 1);
        Date date = calendar.getTime();
        CachedUserNameToIdResolver.GameProfileInfo cachedusernametoidresolver_gameprofileinfo = new CachedUserNameToIdResolver.GameProfileInfo(profile, date);

        this.safeAdd(cachedusernametoidresolver_gameprofileinfo);
        this.save();
        return cachedusernametoidresolver_gameprofileinfo;
    }

    private long getNextOperation() {
        return this.operationCount.incrementAndGet();
    }

    @Override
    public Optional<NameAndId> get(String name) {
        String s1 = name.toLowerCase(Locale.ROOT);
        CachedUserNameToIdResolver.GameProfileInfo cachedusernametoidresolver_gameprofileinfo = (CachedUserNameToIdResolver.GameProfileInfo) this.profilesByName.get(s1);
        boolean flag = false;

        if (cachedusernametoidresolver_gameprofileinfo != null && (new Date()).getTime() >= cachedusernametoidresolver_gameprofileinfo.expirationDate.getTime()) {
            this.profilesByUUID.remove(cachedusernametoidresolver_gameprofileinfo.nameAndId().id());
            this.profilesByName.remove(cachedusernametoidresolver_gameprofileinfo.nameAndId().name().toLowerCase(Locale.ROOT));
            flag = true;
            cachedusernametoidresolver_gameprofileinfo = null;
        }

        Optional<NameAndId> optional;

        if (cachedusernametoidresolver_gameprofileinfo != null) {
            cachedusernametoidresolver_gameprofileinfo.setLastAccess(this.getNextOperation());
            optional = Optional.of(cachedusernametoidresolver_gameprofileinfo.nameAndId());
        } else {
            Optional<NameAndId> optional1 = this.lookupGameProfile(this.profileRepository, s1);

            if (optional1.isPresent()) {
                optional = Optional.of(this.addInternal((NameAndId) optional1.get()).nameAndId());
                flag = false;
            } else {
                optional = Optional.empty();
            }
        }

        if (flag) {
            this.save();
        }

        return optional;
    }

    @Override
    public Optional<NameAndId> get(UUID id) {
        CachedUserNameToIdResolver.GameProfileInfo cachedusernametoidresolver_gameprofileinfo = (CachedUserNameToIdResolver.GameProfileInfo) this.profilesByUUID.get(id);

        if (cachedusernametoidresolver_gameprofileinfo == null) {
            return Optional.empty();
        } else {
            cachedusernametoidresolver_gameprofileinfo.setLastAccess(this.getNextOperation());
            return Optional.of(cachedusernametoidresolver_gameprofileinfo.nameAndId());
        }
    }

    private static DateFormat createDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    }

    private List<CachedUserNameToIdResolver.GameProfileInfo> load() {
        List<CachedUserNameToIdResolver.GameProfileInfo> list = Lists.newArrayList();

        try (Reader reader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
            JsonArray jsonarray = (JsonArray) this.gson.fromJson(reader, JsonArray.class);

            if (jsonarray == null) {
                return list;
            }

            DateFormat dateformat = createDateFormat();

            jsonarray.forEach((jsonelement) -> {
                Optional optional = readGameProfile(jsonelement, dateformat);

                Objects.requireNonNull(list);
                optional.ifPresent(list::add);
            });
        } catch (FileNotFoundException filenotfoundexception) {
            ;
        } catch (JsonParseException | IOException ioexception) {
            CachedUserNameToIdResolver.LOGGER.warn("Failed to load profile cache {}", this.file, ioexception);
        }

        return list;
    }

    @Override
    public void save() {
        JsonArray jsonarray = new JsonArray();
        DateFormat dateformat = createDateFormat();

        this.getTopMRUProfiles(1000).forEach((cachedusernametoidresolver_gameprofileinfo) -> {
            jsonarray.add(writeGameProfile(cachedusernametoidresolver_gameprofileinfo, dateformat));
        });
        String s = this.gson.toJson(jsonarray);

        try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            writer.write(s);
        } catch (IOException ioexception) {
            ;
        }

    }

    private Stream<CachedUserNameToIdResolver.GameProfileInfo> getTopMRUProfiles(int limit) {
        return ImmutableList.copyOf(this.profilesByUUID.values()).stream().sorted(Comparator.comparing(CachedUserNameToIdResolver.GameProfileInfo::lastAccess).reversed()).limit((long) limit);
    }

    private static JsonElement writeGameProfile(CachedUserNameToIdResolver.GameProfileInfo src, DateFormat dateFormat) {
        JsonObject jsonobject = new JsonObject();

        src.nameAndId().appendTo(jsonobject);
        jsonobject.addProperty("expiresOn", dateFormat.format(src.expirationDate()));
        return jsonobject;
    }

    private static Optional<CachedUserNameToIdResolver.GameProfileInfo> readGameProfile(JsonElement json, DateFormat dateFormat) {
        if (json.isJsonObject()) {
            JsonObject jsonobject = json.getAsJsonObject();
            NameAndId nameandid = NameAndId.fromJson(jsonobject);

            if (nameandid != null) {
                JsonElement jsonelement1 = jsonobject.get("expiresOn");

                if (jsonelement1 != null) {
                    String s = jsonelement1.getAsString();

                    try {
                        Date date = dateFormat.parse(s);

                        return Optional.of(new CachedUserNameToIdResolver.GameProfileInfo(nameandid, date));
                    } catch (ParseException parseexception) {
                        CachedUserNameToIdResolver.LOGGER.warn("Failed to parse date {}", s, parseexception);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static class GameProfileInfo {

        private final NameAndId nameAndId;
        private final Date expirationDate;
        private volatile long lastAccess;

        private GameProfileInfo(NameAndId nameAndId, Date expirationDate) {
            this.nameAndId = nameAndId;
            this.expirationDate = expirationDate;
        }

        public NameAndId nameAndId() {
            return this.nameAndId;
        }

        public Date expirationDate() {
            return this.expirationDate;
        }

        public void setLastAccess(long currentOperation) {
            this.lastAccess = currentOperation;
        }

        public long lastAccess() {
            return this.lastAccess;
        }
    }
}
