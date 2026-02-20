package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class OldUsersConverter {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final File OLD_IPBANLIST = new File("banned-ips.txt");
    public static final File OLD_USERBANLIST = new File("banned-players.txt");
    public static final File OLD_OPLIST = new File("ops.txt");
    public static final File OLD_WHITELIST = new File("white-list.txt");

    public OldUsersConverter() {}

    static List<String> readOldListFormat(File file, Map<String, String[]> userMap) throws IOException {
        List<String> list = Files.readLines(file, StandardCharsets.UTF_8);

        for (String s : list) {
            s = s.trim();
            if (!s.startsWith("#") && !s.isEmpty()) {
                String[] astring = s.split("\\|");

                userMap.put(astring[0].toLowerCase(Locale.ROOT), astring);
            }
        }

        return list;
    }

    private static void lookupPlayers(MinecraftServer server, Collection<String> names, ProfileLookupCallback callback) {
        String[] astring = (String[]) names.stream().filter((s) -> {
            return !StringUtil.isNullOrEmpty(s);
        }).toArray((i) -> {
            return new String[i];
        });

        if (server.usesAuthentication()) {
            server.services().profileRepository().findProfilesByNames(astring, callback);
        } else {
            for (String s : astring) {
                callback.onProfileLookupSucceeded(s, UUIDUtil.createOfflinePlayerUUID(s));
            }
        }

    }

    public static boolean convertUserBanlist(final MinecraftServer server) {
        final UserBanList userbanlist = new UserBanList(PlayerList.USERBANLIST_FILE, new EmptyNotificationService());

        if (OldUsersConverter.OLD_USERBANLIST.exists() && OldUsersConverter.OLD_USERBANLIST.isFile()) {
            if (userbanlist.getFile().exists()) {
                try {
                    userbanlist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", userbanlist.getFile().getName(), ioexception);
                }
            }

            try {
                final Map<String, String[]> map = Maps.newHashMap();

                readOldListFormat(OldUsersConverter.OLD_USERBANLIST, map);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameandid = new NameAndId(profileId, profileName);

                        server.services().nameToIdCache().add(nameandid);
                        String[] astring = (String[]) map.get(nameandid.name().toLowerCase(Locale.ROOT));

                        if (astring == null) {
                            OldUsersConverter.LOGGER.warn("Could not convert user banlist entry for {}", nameandid.name());
                            throw new OldUsersConverter.ConversionError("Profile not in the conversionlist");
                        } else {
                            Date date = astring.length > 1 ? OldUsersConverter.parseDate(astring[1], (Date) null) : null;
                            String s1 = astring.length > 2 ? astring[2] : null;
                            Date date1 = astring.length > 3 ? OldUsersConverter.parseDate(astring[3], (Date) null) : null;
                            String s2 = astring.length > 4 ? astring[4] : null;

                            userbanlist.add(new UserBanListEntry(nameandid, date, s1, date1, s2));
                        }
                    }

                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user banlist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };

                lookupPlayers(server, map.keySet(), profilelookupcallback);
                userbanlist.save();
                renameOldFile(OldUsersConverter.OLD_USERBANLIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not read old user banlist to convert it!", ioexception1);
                return false;
            } catch (OldUsersConverter.ConversionError oldusersconverter_conversionerror) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", oldusersconverter_conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertIpBanlist(MinecraftServer server) {
        IpBanList ipbanlist = new IpBanList(PlayerList.IPBANLIST_FILE, new EmptyNotificationService());

        if (OldUsersConverter.OLD_IPBANLIST.exists() && OldUsersConverter.OLD_IPBANLIST.isFile()) {
            if (ipbanlist.getFile().exists()) {
                try {
                    ipbanlist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", ipbanlist.getFile().getName(), ioexception);
                }
            }

            try {
                Map<String, String[]> map = Maps.newHashMap();

                readOldListFormat(OldUsersConverter.OLD_IPBANLIST, map);

                for (String s : map.keySet()) {
                    String[] astring = (String[]) map.get(s);
                    Date date = astring.length > 1 ? parseDate(astring[1], (Date) null) : null;
                    String s1 = astring.length > 2 ? astring[2] : null;
                    Date date1 = astring.length > 3 ? parseDate(astring[3], (Date) null) : null;
                    String s2 = astring.length > 4 ? astring[4] : null;

                    ipbanlist.add(new IpBanListEntry(s, date, s1, date1, s2));
                }

                ipbanlist.save();
                renameOldFile(OldUsersConverter.OLD_IPBANLIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not parse old ip banlist to convert it!", ioexception1);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertOpsList(final MinecraftServer server) {
        final ServerOpList serveroplist = new ServerOpList(PlayerList.OPLIST_FILE, new EmptyNotificationService());

        if (OldUsersConverter.OLD_OPLIST.exists() && OldUsersConverter.OLD_OPLIST.isFile()) {
            if (serveroplist.getFile().exists()) {
                try {
                    serveroplist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", serveroplist.getFile().getName(), ioexception);
                }
            }

            try {
                List<String> list = Files.readLines(OldUsersConverter.OLD_OPLIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameandid = new NameAndId(profileId, profileName);

                        server.services().nameToIdCache().add(nameandid);
                        serveroplist.add(new ServerOpListEntry(nameandid, server.operatorUserPermissions(), false));
                    }

                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup oplist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };

                lookupPlayers(server, list, profilelookupcallback);
                serveroplist.save();
                renameOldFile(OldUsersConverter.OLD_OPLIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not read old oplist to convert it!", ioexception1);
                return false;
            } catch (OldUsersConverter.ConversionError oldusersconverter_conversionerror) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", oldusersconverter_conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertWhiteList(final MinecraftServer server) {
        final UserWhiteList userwhitelist = new UserWhiteList(PlayerList.WHITELIST_FILE, new EmptyNotificationService());

        if (OldUsersConverter.OLD_WHITELIST.exists() && OldUsersConverter.OLD_WHITELIST.isFile()) {
            if (userwhitelist.getFile().exists()) {
                try {
                    userwhitelist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", userwhitelist.getFile().getName(), ioexception);
                }
            }

            try {
                List<String> list = Files.readLines(OldUsersConverter.OLD_WHITELIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameandid = new NameAndId(profileId, profileName);

                        server.services().nameToIdCache().add(nameandid);
                        userwhitelist.add(new UserWhiteListEntry(nameandid));
                    }

                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", profileName, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }
                };

                lookupPlayers(server, list, profilelookupcallback);
                userwhitelist.save();
                renameOldFile(OldUsersConverter.OLD_WHITELIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not read old whitelist to convert it!", ioexception1);
                return false;
            } catch (OldUsersConverter.ConversionError oldusersconverter_conversionerror) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", oldusersconverter_conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    public static @Nullable UUID convertMobOwnerIfNecessary(final MinecraftServer server, String owner) {
        if (!StringUtil.isNullOrEmpty(owner) && owner.length() <= 16) {
            Optional<UUID> optional = server.services().nameToIdCache().get(owner).map(NameAndId::id);

            if (optional.isPresent()) {
                return (UUID) optional.get();
            } else if (!server.isSingleplayer() && server.usesAuthentication()) {
                final List<NameAndId> list = new ArrayList();
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameandid = new NameAndId(profileId, profileName);

                        server.services().nameToIdCache().add(nameandid);
                        list.add(nameandid);
                    }

                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", profileName, exception);
                    }
                };

                lookupPlayers(server, Lists.newArrayList(new String[]{owner}), profilelookupcallback);
                return !list.isEmpty() ? ((NameAndId) list.getFirst()).id() : null;
            } else {
                return UUIDUtil.createOfflinePlayerUUID(owner);
            }
        } else {
            try {
                return UUID.fromString(owner);
            } catch (IllegalArgumentException illegalargumentexception) {
                return null;
            }
        }
    }

    public static boolean convertPlayers(final DedicatedServer server) {
        final File file = getWorldPlayersDirectory(server);
        final File file1 = new File(file.getParentFile(), "playerdata");
        final File file2 = new File(file.getParentFile(), "unknownplayers");

        if (file.exists() && file.isDirectory()) {
            File[] afile = file.listFiles();
            List<String> list = Lists.newArrayList();

            for (File file3 : afile) {
                String s = file3.getName();

                if (s.toLowerCase(Locale.ROOT).endsWith(".dat")) {
                    String s1 = s.substring(0, s.length() - ".dat".length());

                    if (!s1.isEmpty()) {
                        list.add(s1);
                    }
                }
            }

            try {
                final String[] astring = (String[]) list.toArray(new String[list.size()]);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(String profileName, UUID profileId) {
                        NameAndId nameandid = new NameAndId(profileId, profileName);

                        server.services().nameToIdCache().add(nameandid);
                        this.movePlayerFile(file1, this.getFileNameForProfile(profileName), profileId.toString());
                    }

                    public void onProfileLookupFailed(String profileName, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user uuid for {}", profileName, exception);
                        if (exception instanceof ProfileNotFoundException) {
                            String s3 = this.getFileNameForProfile(profileName);

                            this.movePlayerFile(file2, s3, s3);
                        } else {
                            throw new OldUsersConverter.ConversionError("Could not request user " + profileName + " from backend systems", exception);
                        }
                    }

                    private void movePlayerFile(File directory, String oldName, String newName) {
                        File file5 = new File(file, oldName + ".dat");
                        File file6 = new File(directory, newName + ".dat");

                        OldUsersConverter.ensureDirectoryExists(directory);
                        if (!file5.renameTo(file6)) {
                            throw new OldUsersConverter.ConversionError("Could not convert file for " + oldName);
                        }
                    }

                    private String getFileNameForProfile(String profileName) {
                        String s3 = null;

                        for (String s4 : astring) {
                            if (s4 != null && s4.equalsIgnoreCase(profileName)) {
                                s3 = s4;
                                break;
                            }
                        }

                        if (s3 == null) {
                            throw new OldUsersConverter.ConversionError("Could not find the filename for " + profileName + " anymore");
                        } else {
                            return s3;
                        }
                    }
                };

                lookupPlayers(server, Lists.newArrayList(astring), profilelookupcallback);
                return true;
            } catch (OldUsersConverter.ConversionError oldusersconverter_conversionerror) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", oldusersconverter_conversionerror);
                return false;
            }
        } else {
            return true;
        }
    }

    private static void ensureDirectoryExists(File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new OldUsersConverter.ConversionError("Can't create directory " + directory.getName() + " in world save directory.");
            }
        } else if (!directory.mkdirs()) {
            throw new OldUsersConverter.ConversionError("Can't create directory " + directory.getName() + " in world save directory.");
        }
    }

    public static boolean serverReadyAfterUserconversion(MinecraftServer server) {
        boolean flag = areOldUserlistsRemoved();

        flag = flag && areOldPlayersConverted(server);
        return flag;
    }

    private static boolean areOldUserlistsRemoved() {
        boolean flag = false;

        if (OldUsersConverter.OLD_USERBANLIST.exists() && OldUsersConverter.OLD_USERBANLIST.isFile()) {
            flag = true;
        }

        boolean flag1 = false;

        if (OldUsersConverter.OLD_IPBANLIST.exists() && OldUsersConverter.OLD_IPBANLIST.isFile()) {
            flag1 = true;
        }

        boolean flag2 = false;

        if (OldUsersConverter.OLD_OPLIST.exists() && OldUsersConverter.OLD_OPLIST.isFile()) {
            flag2 = true;
        }

        boolean flag3 = false;

        if (OldUsersConverter.OLD_WHITELIST.exists() && OldUsersConverter.OLD_WHITELIST.isFile()) {
            flag3 = true;
        }

        if (!flag && !flag1 && !flag2 && !flag3) {
            return true;
        } else {
            OldUsersConverter.LOGGER.warn("**** FAILED TO START THE SERVER AFTER ACCOUNT CONVERSION!");
            OldUsersConverter.LOGGER.warn("** please remove the following files and restart the server:");
            if (flag) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_USERBANLIST.getName());
            }

            if (flag1) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_IPBANLIST.getName());
            }

            if (flag2) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_OPLIST.getName());
            }

            if (flag3) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_WHITELIST.getName());
            }

            return false;
        }
    }

    private static boolean areOldPlayersConverted(MinecraftServer server) {
        File file = getWorldPlayersDirectory(server);

        if (!file.exists() || !file.isDirectory() || file.list().length <= 0 && file.delete()) {
            return true;
        } else {
            OldUsersConverter.LOGGER.warn("**** DETECTED OLD PLAYER DIRECTORY IN THE WORLD SAVE");
            OldUsersConverter.LOGGER.warn("**** THIS USUALLY HAPPENS WHEN THE AUTOMATIC CONVERSION FAILED IN SOME WAY");
            OldUsersConverter.LOGGER.warn("** please restart the server and if the problem persists, remove the directory '{}'", file.getPath());
            return false;
        }
    }

    private static File getWorldPlayersDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.PLAYER_OLD_DATA_DIR).toFile();
    }

    private static void renameOldFile(File file) {
        File file1 = new File(file.getName() + ".converted");

        file.renameTo(file1);
    }

    private static Date parseDate(String dateString, Date defaultValue) {
        Date date1;

        try {
            date1 = BanListEntry.DATE_FORMAT.parse(dateString);
        } catch (ParseException parseexception) {
            date1 = defaultValue;
        }

        return date1;
    }

    private static class ConversionError extends RuntimeException {

        private ConversionError(String message, Throwable cause) {
            super(message, cause);
        }

        private ConversionError(String message) {
            super(message);
        }
    }
}
