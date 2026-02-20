package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.FileUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class PlayerList {

    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z", Locale.ROOT);
    private final MinecraftServer server;
    public final List<ServerPlayer> players = Lists.newArrayList();
    private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
    private final UserBanList bans;
    private final IpBanList ipBans;
    private final ServerOpList ops;
    private final UserWhiteList whitelist;
    private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
    private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
    public final PlayerDataStorage playerIo;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private int sendAllPlayerInfoIn;

    public PlayerList(MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo, NotificationService notificationService) {
        this.server = server;
        this.registries = registries;
        this.playerIo = playerIo;
        this.whitelist = new UserWhiteList(PlayerList.WHITELIST_FILE, notificationService);
        this.ops = new ServerOpList(PlayerList.OPLIST_FILE, notificationService);
        this.bans = new UserBanList(PlayerList.USERBANLIST_FILE, notificationService);
        this.ipBans = new IpBanList(PlayerList.IPBANLIST_FILE, notificationService);
    }

    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        NameAndId nameandid = player.nameAndId();
        UserNameToIdResolver usernametoidresolver = this.server.services().nameToIdCache();
        Optional<NameAndId> optional = usernametoidresolver.get(nameandid.id());
        String s = (String) optional.map(NameAndId::name).orElse(nameandid.name());

        usernametoidresolver.add(nameandid);
        ServerLevel serverlevel = player.level();
        String s1 = connection.getLoggableAddress(this.server.logIPs());

        PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", new Object[]{player.getPlainTextName(), s1, player.getId(), player.getX(), player.getY(), player.getZ()});
        LevelData leveldata = serverlevel.getLevelData();
        ServerGamePacketListenerImpl servergamepacketlistenerimpl = new ServerGamePacketListenerImpl(this.server, connection, player, cookie);

        connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess()), servergamepacketlistenerimpl), servergamepacketlistenerimpl);
        servergamepacketlistenerimpl.suspendFlushing();
        GameRules gamerules = serverlevel.getGameRules();
        boolean flag = (Boolean) gamerules.get(GameRules.IMMEDIATE_RESPAWN);
        boolean flag1 = (Boolean) gamerules.get(GameRules.REDUCED_DEBUG_INFO);
        boolean flag2 = (Boolean) gamerules.get(GameRules.LIMITED_CRAFTING);

        servergamepacketlistenerimpl.send(new ClientboundLoginPacket(player.getId(), leveldata.isHardcore(), this.server.levelKeys(), this.getMaxPlayers(), this.getViewDistance(), this.getSimulationDistance(), flag1, !flag, flag2, player.createCommonSpawnInfo(serverlevel), this.server.enforceSecureProfile()));
        servergamepacketlistenerimpl.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        servergamepacketlistenerimpl.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        servergamepacketlistenerimpl.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        RecipeManager recipemanager = this.server.getRecipeManager();

        servergamepacketlistenerimpl.send(new ClientboundUpdateRecipesPacket(recipemanager.getSynchronizedItemProperties(), recipemanager.getSynchronizedStonecutterRecipes()));
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(serverlevel.getScoreboard(), player);
        this.server.invalidateStatus();
        MutableComponent mutablecomponent;

        if (player.getGameProfile().name().equalsIgnoreCase(s)) {
            mutablecomponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            mutablecomponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), s);
        }

        this.broadcastSystemMessage(mutablecomponent.withStyle(ChatFormatting.YELLOW), false);
        servergamepacketlistenerimpl.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus serverstatus = this.server.getStatus();

        if (serverstatus != null && !cookie.transferred()) {
            player.sendServerStatus(serverstatus);
        }

        player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players));
        this.players.add(player);
        this.playersByUUID.put(player.getUUID(), player);
        this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        this.sendLevelInfo(player, serverlevel);
        serverlevel.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player);
        this.sendActivePlayerEffects(player);
        player.initInventoryMenu();
        this.server.notificationManager().playerJoined(player);
        servergamepacketlistenerimpl.resumeFlushing();
    }

    public void updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player) {
        Set<Objective> set = Sets.newHashSet();

        for (PlayerTeam playerteam : scoreboard.getPlayerTeams()) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerteam, true));
        }

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            Objective objective = scoreboard.getDisplayObjective(displayslot);

            if (objective != null && !set.contains(objective)) {
                for (Packet<?> packet : scoreboard.getStartTrackingPackets(objective)) {
                    player.connection.send(packet);
                }

                set.add(objective);
            }
        }

    }

    public void addWorldborderListener(final ServerLevel level) {
        level.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onSetSize(WorldBorder border, double newSize) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(border), level.dimension());
            }

            @Override
            public void onLerpSize(WorldBorder border, double fromSize, double targetSize, long ticks, long gameTime) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(border), level.dimension());
            }

            @Override
            public void onSetCenter(WorldBorder border, double x, double z) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(border), level.dimension());
            }

            @Override
            public void onSetWarningTime(WorldBorder border, int time) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), level.dimension());
            }

            @Override
            public void onSetWarningBlocks(WorldBorder border, int blocks) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), level.dimension());
            }

            @Override
            public void onSetDamagePerBlock(WorldBorder border, double damagePerBlock) {}

            @Override
            public void onSetSafeZone(WorldBorder border, double safeZone) {}
        });
    }

    public Optional<CompoundTag> loadPlayerData(NameAndId nameAndId) {
        CompoundTag compoundtag = this.server.getWorldData().getLoadedPlayerTag();

        if (this.server.isSingleplayerOwner(nameAndId) && compoundtag != null) {
            PlayerList.LOGGER.debug("loading single player");
            return Optional.of(compoundtag);
        } else {
            return this.playerIo.load(nameAndId);
        }
    }

    protected void save(ServerPlayer player) {
        this.playerIo.save(player);
        ServerStatsCounter serverstatscounter = (ServerStatsCounter) this.stats.get(player.getUUID());

        if (serverstatscounter != null) {
            serverstatscounter.save();
        }

        PlayerAdvancements playeradvancements = (PlayerAdvancements) this.advancements.get(player.getUUID());

        if (playeradvancements != null) {
            playeradvancements.save();
        }

    }

    public void remove(ServerPlayer player) {
        ServerLevel serverlevel = player.level();

        player.awardStat(Stats.LEAVE_GAME);
        this.save(player);
        if (player.isPassenger()) {
            Entity entity = player.getRootVehicle();

            if (entity.hasExactlyOnePlayerPassenger()) {
                PlayerList.LOGGER.debug("Removing player mount");
                player.stopRiding();
                entity.getPassengersAndSelf().forEach((entity1) -> {
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                });
            }
        }

        player.unRide();

        for (ThrownEnderpearl thrownenderpearl : player.getEnderPearls()) {
            thrownenderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }

        serverlevel.removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        player.getAdvancements().stopListening();
        this.players.remove(player);
        this.server.getCustomBossEvents().onPlayerDisconnect(player);
        UUID uuid = player.getUUID();
        ServerPlayer serverplayer1 = (ServerPlayer) this.playersByUUID.get(uuid);

        if (serverplayer1 == player) {
            this.playersByUUID.remove(uuid);
            this.stats.remove(uuid);
            this.advancements.remove(uuid);
            this.server.notificationManager().playerLeft(player);
        }

        this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
    }

    public @Nullable Component canPlayerLogin(SocketAddress address, NameAndId nameAndId) {
        if (this.bans.isBanned(nameAndId)) {
            UserBanListEntry userbanlistentry = (UserBanListEntry) this.bans.get(nameAndId);
            MutableComponent mutablecomponent = Component.translatable("multiplayer.disconnect.banned.reason", userbanlistentry.getReasonMessage());

            if (userbanlistentry.getExpires() != null) {
                mutablecomponent.append((Component) Component.translatable("multiplayer.disconnect.banned.expiration", PlayerList.BAN_DATE_FORMAT.format(userbanlistentry.getExpires())));
            }

            return mutablecomponent;
        } else if (!this.isWhiteListed(nameAndId)) {
            return Component.translatable("multiplayer.disconnect.not_whitelisted");
        } else if (this.ipBans.isBanned(address)) {
            IpBanListEntry ipbanlistentry = this.ipBans.get(address);
            MutableComponent mutablecomponent1 = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipbanlistentry.getReasonMessage());

            if (ipbanlistentry.getExpires() != null) {
                mutablecomponent1.append((Component) Component.translatable("multiplayer.disconnect.banned_ip.expiration", PlayerList.BAN_DATE_FORMAT.format(ipbanlistentry.getExpires())));
            }

            return mutablecomponent1;
        } else {
            return this.players.size() >= this.getMaxPlayers() && !this.canBypassPlayerLimit(nameAndId) ? Component.translatable("multiplayer.disconnect.server_full") : null;
        }
    }

    public boolean disconnectAllPlayersWithProfile(UUID playerId) {
        Set<ServerPlayer> set = Sets.newIdentityHashSet();

        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.getUUID().equals(playerId)) {
                set.add(serverplayer);
            }
        }

        ServerPlayer serverplayer1 = (ServerPlayer) this.playersByUUID.get(playerId);

        if (serverplayer1 != null) {
            set.add(serverplayer1);
        }

        for (ServerPlayer serverplayer2 : set) {
            serverplayer2.connection.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        return !set.isEmpty();
    }

    public ServerPlayer respawn(ServerPlayer serverPlayer, boolean keepAllPlayerData, Entity.RemovalReason removalReason) {
        TeleportTransition teleporttransition = serverPlayer.findRespawnPositionAndUseSpawnBlock(!keepAllPlayerData, TeleportTransition.DO_NOTHING);

        this.players.remove(serverPlayer);
        serverPlayer.level().removePlayerImmediately(serverPlayer, removalReason);
        ServerLevel serverlevel = teleporttransition.newLevel();
        ServerPlayer serverplayer1 = new ServerPlayer(this.server, serverlevel, serverPlayer.getGameProfile(), serverPlayer.clientInformation());

        serverplayer1.connection = serverPlayer.connection;
        serverplayer1.restoreFrom(serverPlayer, keepAllPlayerData);
        serverplayer1.setId(serverPlayer.getId());
        serverplayer1.setMainArm(serverPlayer.getMainArm());
        if (!teleporttransition.missingRespawnBlock()) {
            serverplayer1.copyRespawnPosition(serverPlayer);
        }

        for (String s : serverPlayer.getTags()) {
            serverplayer1.addTag(s);
        }

        Vec3 vec3 = teleporttransition.position();

        serverplayer1.snapTo(vec3.x, vec3.y, vec3.z, teleporttransition.yRot(), teleporttransition.xRot());
        if (teleporttransition.missingRespawnBlock()) {
            serverplayer1.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
        }

        byte b0 = (byte) (keepAllPlayerData ? 1 : 0);
        ServerLevel serverlevel1 = serverplayer1.level();
        LevelData leveldata = serverlevel1.getLevelData();

        serverplayer1.connection.send(new ClientboundRespawnPacket(serverplayer1.createCommonSpawnInfo(serverlevel1), b0));
        serverplayer1.connection.teleport(serverplayer1.getX(), serverplayer1.getY(), serverplayer1.getZ(), serverplayer1.getYRot(), serverplayer1.getXRot());
        serverplayer1.connection.send(new ClientboundSetDefaultSpawnPositionPacket(serverlevel.getRespawnData()));
        serverplayer1.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        serverplayer1.connection.send(new ClientboundSetExperiencePacket(serverplayer1.experienceProgress, serverplayer1.totalExperience, serverplayer1.experienceLevel));
        this.sendActivePlayerEffects(serverplayer1);
        this.sendLevelInfo(serverplayer1, serverlevel);
        this.sendPlayerPermissionLevel(serverplayer1);
        serverlevel.addRespawnedPlayer(serverplayer1);
        this.players.add(serverplayer1);
        this.playersByUUID.put(serverplayer1.getUUID(), serverplayer1);
        serverplayer1.initInventoryMenu();
        serverplayer1.setHealth(serverplayer1.getHealth());
        ServerPlayer.RespawnConfig serverplayer_respawnconfig = serverplayer1.getRespawnConfig();

        if (!keepAllPlayerData && serverplayer_respawnconfig != null) {
            LevelData.RespawnData leveldata_respawndata = serverplayer_respawnconfig.respawnData();
            ServerLevel serverlevel2 = this.server.getLevel(leveldata_respawndata.dimension());

            if (serverlevel2 != null) {
                BlockPos blockpos = leveldata_respawndata.pos();
                BlockState blockstate = serverlevel2.getBlockState(blockpos);

                if (blockstate.is(Blocks.RESPAWN_ANCHOR)) {
                    serverplayer1.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, (double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), 1.0F, 1.0F, serverlevel.getRandom().nextLong()));
                }
            }
        }

        return serverplayer1;
    }

    public void sendActivePlayerEffects(ServerPlayer player) {
        this.sendActiveEffects(player, player.connection);
    }

    public void sendActiveEffects(LivingEntity livingEntity, ServerGamePacketListenerImpl connection) {
        for (MobEffectInstance mobeffectinstance : livingEntity.getActiveEffects()) {
            connection.send(new ClientboundUpdateMobEffectPacket(livingEntity.getId(), mobeffectinstance, false));
        }

    }

    public void sendPlayerPermissionLevel(ServerPlayer player) {
        LevelBasedPermissionSet levelbasedpermissionset = this.server.getProfilePermissions(player.nameAndId());

        this.sendPlayerPermissionLevel(player, levelbasedpermissionset);
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            this.broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), this.players));
            this.sendAllPlayerInfoIn = 0;
        }

    }

    public void broadcastAll(Packet<?> packet) {
        for (ServerPlayer serverplayer : this.players) {
            serverplayer.connection.send(packet);
        }

    }

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> dimension) {
        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.level().dimension() == dimension) {
                serverplayer.connection.send(packet);
            }
        }

    }

    public void broadcastSystemToTeam(Player player, Component message) {
        Team team = player.getTeam();

        if (team != null) {
            for (String s : team.getPlayers()) {
                ServerPlayer serverplayer = this.getPlayerByName(s);

                if (serverplayer != null && serverplayer != player) {
                    serverplayer.sendSystemMessage(message);
                }
            }

        }
    }

    public void broadcastSystemToAllExceptTeam(Player player, Component message) {
        Team team = player.getTeam();

        if (team == null) {
            this.broadcastSystemMessage(message, false);
        } else {
            for (int i = 0; i < this.players.size(); ++i) {
                ServerPlayer serverplayer = (ServerPlayer) this.players.get(i);

                if (serverplayer.getTeam() != team) {
                    serverplayer.sendSystemMessage(message);
                }
            }

        }
    }

    public String[] getPlayerNamesArray() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            astring[i] = ((ServerPlayer) this.players.get(i)).getGameProfile().name();
        }

        return astring;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(NameAndId nameAndId) {
        this.op(nameAndId, Optional.empty(), Optional.empty());
    }

    public void op(NameAndId nameAndId, Optional<LevelBasedPermissionSet> permissions, Optional<Boolean> canBypassPlayerLimit) {
        this.ops.add(new ServerOpListEntry(nameAndId, (LevelBasedPermissionSet) permissions.orElse(this.server.operatorUserPermissions()), (Boolean) canBypassPlayerLimit.orElse(this.ops.canBypassPlayerLimit(nameAndId))));
        ServerPlayer serverplayer = this.getPlayer(nameAndId.id());

        if (serverplayer != null) {
            this.sendPlayerPermissionLevel(serverplayer);
        }

    }

    public void deop(NameAndId nameAndId) {
        if (this.ops.remove(nameAndId)) {
            ServerPlayer serverplayer = this.getPlayer(nameAndId.id());

            if (serverplayer != null) {
                this.sendPlayerPermissionLevel(serverplayer);
            }
        }

    }

    private void sendPlayerPermissionLevel(ServerPlayer player, LevelBasedPermissionSet permissions) {
        if (player.connection != null) {
            byte b0;

            switch (permissions.level()) {
                case ALL:
                    b0 = 24;
                    break;
                case MODERATORS:
                    b0 = 25;
                    break;
                case GAMEMASTERS:
                    b0 = 26;
                    break;
                case ADMINS:
                    b0 = 27;
                    break;
                case OWNERS:
                    b0 = 28;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            byte b1 = b0;

            player.connection.send(new ClientboundEntityEventPacket(player, b1));
        }

        this.server.getCommands().sendCommands(player);
    }

    public boolean isWhiteListed(NameAndId nameAndId) {
        return !this.isUsingWhitelist() || this.ops.contains(nameAndId) || this.whitelist.contains(nameAndId);
    }

    public boolean isOp(NameAndId nameAndId) {
        return this.ops.contains(nameAndId) || this.server.isSingleplayerOwner(nameAndId) && this.server.getWorldData().isAllowCommands() || this.allowCommandsForAllPlayers;
    }

    public @Nullable ServerPlayer getPlayerByName(String name) {
        int i = this.players.size();

        for (int j = 0; j < i; ++j) {
            ServerPlayer serverplayer = (ServerPlayer) this.players.get(j);

            if (serverplayer.getGameProfile().name().equalsIgnoreCase(name)) {
                return serverplayer;
            }
        }

        return null;
    }

    public void broadcast(@Nullable Player except, double x, double y, double z, double range, ResourceKey<Level> dimension, Packet<?> packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer serverplayer = (ServerPlayer) this.players.get(i);

            if (serverplayer != except && serverplayer.level().dimension() == dimension) {
                double d4 = x - serverplayer.getX();
                double d5 = y - serverplayer.getY();
                double d6 = z - serverplayer.getZ();

                if (d4 * d4 + d5 * d5 + d6 * d6 < range * range) {
                    serverplayer.connection.send(packet);
                }
            }
        }

    }

    public void saveAll() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.save((ServerPlayer) this.players.get(i));
        }

    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {}

    public void sendLevelInfo(ServerPlayer player, ServerLevel level) {
        WorldBorder worldborder = level.getWorldBorder();

        player.connection.send(new ClientboundInitializeBorderPacket(worldborder));
        player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), (Boolean) level.getGameRules().get(GameRules.ADVANCE_TIME)));
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getRespawnData()));
        if (level.isRaining()) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1.0F)));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1.0F)));
        }

        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(player);
    }

    public void sendAllPlayerInfo(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        player.resetSentInfo();
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.server.getMaxPlayers();
    }

    public boolean isUsingWhitelist() {
        return this.server.isUsingWhitelist();
    }

    public List<ServerPlayer> getPlayersWithAddress(String ip) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.getIpAddress().equals(ip)) {
                list.add(serverplayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public @Nullable CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean allowCommands) {
        this.allowCommandsForAllPlayers = allowCommands;
    }

    public void removeAll() {
        for (int i = 0; i < this.players.size(); ++i) {
            ((ServerPlayer) this.players.get(i)).connection.disconnect((Component) Component.translatable("multiplayer.disconnect.server_shutdown"));
        }

    }

    public void broadcastSystemMessage(Component message, boolean overlay) {
        this.broadcastSystemMessage(message, (serverplayer) -> {
            return message;
        }, overlay);
    }

    public void broadcastSystemMessage(Component message, Function<ServerPlayer, Component> playerMessages, boolean overlay) {
        this.server.sendSystemMessage(message);

        for (ServerPlayer serverplayer : this.players) {
            Component component1 = (Component) playerMessages.apply(serverplayer);

            if (component1 != null) {
                serverplayer.sendSystemMessage(component1, overlay);
            }
        }

    }

    public void broadcastChatMessage(PlayerChatMessage message, CommandSourceStack sender, ChatType.Bound chatType) {
        Objects.requireNonNull(sender);
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender.getPlayer(), chatType);
    }

    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound chatType) {
        Objects.requireNonNull(sender);
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender, chatType);
    }

    private void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> isFiltered, @Nullable ServerPlayer senderPlayer, ChatType.Bound chatType) {
        boolean flag = this.verifyChatTrusted(message);

        this.server.logChatMessage(message.decoratedContent(), chatType, flag ? null : "Not Secure");
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(message);
        boolean flag1 = false;

        for (ServerPlayer serverplayer1 : this.players) {
            boolean flag2 = isFiltered.test(serverplayer1);

            serverplayer1.sendChatMessage(outgoingchatmessage, flag2, chatType);
            flag1 |= flag2 && message.isFullyFiltered();
        }

        if (flag1 && senderPlayer != null) {
            senderPlayer.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }

    }

    private boolean verifyChatTrusted(PlayerChatMessage message) {
        return message.hasSignature() && !message.hasExpiredServer(Instant.now());
    }

    public ServerStatsCounter getPlayerStats(Player player) {
        GameProfile gameprofile = player.getGameProfile();

        return (ServerStatsCounter) this.stats.computeIfAbsent(gameprofile.id(), (uuid) -> {
            Path path = this.locateStatsFile(gameprofile);

            return new ServerStatsCounter(this.server, path);
        });
    }

    private Path locateStatsFile(GameProfile gameProfile) {
        Path path = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        Path path1 = path.resolve(String.valueOf(gameProfile.id()) + ".json");

        if (Files.exists(path1, new LinkOption[0])) {
            return path1;
        } else {
            String s = gameProfile.name() + ".json";

            if (FileUtil.isValidPathSegment(s)) {
                Path path2 = path.resolve(s);

                if (Files.isRegularFile(path2, new LinkOption[0])) {
                    try {
                        return Files.move(path2, path1);
                    } catch (IOException ioexception) {
                        PlayerList.LOGGER.warn("Failed to copy file {} to {}", s, path1);
                        return path2;
                    }
                }
            }

            return path1;
        }
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAdvancements playeradvancements = (PlayerAdvancements) this.advancements.get(uuid);

        if (playeradvancements == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(String.valueOf(uuid) + ".json");

            playeradvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, player);
            this.advancements.put(uuid, playeradvancements);
        }

        playeradvancements.setPlayer(player);
        return playeradvancements;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance));

        for (ServerLevel serverlevel : this.server.getAllLevels()) {
            serverlevel.getChunkSource().setViewDistance(viewDistance);
        }

    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
        this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance));

        for (ServerLevel serverlevel : this.server.getAllLevels()) {
            serverlevel.getChunkSource().setSimulationDistance(simulationDistance);
        }

    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    public @Nullable ServerPlayer getPlayer(UUID uuid) {
        return (ServerPlayer) this.playersByUUID.get(uuid);
    }

    public @Nullable ServerPlayer getPlayer(String playerName) {
        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.getGameProfile().name().equalsIgnoreCase(playerName)) {
                return serverplayer;
            }
        }

        return null;
    }

    public boolean canBypassPlayerLimit(NameAndId nameAndId) {
        return false;
    }

    public void reloadResources() {
        for (PlayerAdvancements playeradvancements : this.advancements.values()) {
            playeradvancements.reload(this.server.getAdvancements());
        }

        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        RecipeManager recipemanager = this.server.getRecipeManager();
        ClientboundUpdateRecipesPacket clientboundupdaterecipespacket = new ClientboundUpdateRecipesPacket(recipemanager.getSynchronizedItemProperties(), recipemanager.getSynchronizedStonecutterRecipes());

        for (ServerPlayer serverplayer : this.players) {
            serverplayer.connection.send(clientboundupdaterecipespacket);
            serverplayer.getRecipeBook().sendInitialRecipeBook(serverplayer);
        }

    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
