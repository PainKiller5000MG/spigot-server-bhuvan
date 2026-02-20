package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.config.JoinWorldTask;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.network.config.ServerCodeOfConductConfigurationTask;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.flag.FeatureFlags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerConfigurationPacketListenerImpl extends ServerCommonPacketListenerImpl implements ServerConfigurationPacketListener, TickablePacketListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component DISCONNECT_REASON_INVALID_DATA = Component.translatable("multiplayer.disconnect.invalid_player_data");
    private static final Component DISCONNECT_REASON_CONFIGURATION_ERROR = Component.translatable("multiplayer.disconnect.configuration_error");
    private final GameProfile gameProfile;
    private final Queue<ConfigurationTask> configurationTasks = new ConcurrentLinkedQueue();
    private @Nullable ConfigurationTask currentTask;
    private ClientInformation clientInformation;
    private @Nullable SynchronizeRegistriesTask synchronizeRegistriesTask;
    private @Nullable PrepareSpawnTask prepareSpawnTask;

    public ServerConfigurationPacketListenerImpl(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
        super(server, connection, cookie);
        this.gameProfile = cookie.gameProfile();
        this.clientInformation = cookie.clientInformation();
    }

    @Override
    protected GameProfile playerProfile() {
        return this.gameProfile;
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        ServerConfigurationPacketListenerImpl.LOGGER.info("{} ({}) lost connection: {}", new Object[]{this.gameProfile.name(), this.gameProfile.id(), details.reason().getString()});
        if (this.prepareSpawnTask != null) {
            this.prepareSpawnTask.close();
            this.prepareSpawnTask = null;
        }

        super.onDisconnect(details);
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }

    public void startConfiguration() {
        this.send(new ClientboundCustomPayloadPacket(new BrandPayload(this.server.getServerModName())));
        ServerLinks serverlinks = this.server.serverLinks();

        if (!serverlinks.isEmpty()) {
            this.send(new ClientboundServerLinksPacket(serverlinks.untrust()));
        }

        LayeredRegistryAccess<RegistryLayer> layeredregistryaccess = this.server.registries();
        List<KnownPack> list = this.server.getResourceManager().listPacks().flatMap((packresources) -> {
            return packresources.location().knownPackInfo().stream();
        }).toList();

        this.send(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(this.server.getWorldData().enabledFeatures())));
        this.synchronizeRegistriesTask = new SynchronizeRegistriesTask(list, layeredregistryaccess);
        this.configurationTasks.add(this.synchronizeRegistriesTask);
        this.addOptionalTasks();
        this.returnToWorld();
    }

    public void returnToWorld() {
        this.prepareSpawnTask = new PrepareSpawnTask(this.server, new NameAndId(this.gameProfile));
        this.configurationTasks.add(this.prepareSpawnTask);
        this.configurationTasks.add(new JoinWorldTask());
        this.startNextTask();
    }

    private void addOptionalTasks() {
        Map<String, String> map = this.server.getCodeOfConducts();

        if (!map.isEmpty()) {
            this.configurationTasks.add(new ServerCodeOfConductConfigurationTask(() -> {
                String s = (String) map.get(this.clientInformation.language().toLowerCase(Locale.ROOT));

                if (s == null) {
                    s = (String) map.get("en_us");
                }

                if (s == null) {
                    s = (String) map.values().iterator().next();
                }

                return s;
            }));
        }

        this.server.getServerResourcePack().ifPresent((minecraftserver_serverresourcepackinfo) -> {
            this.configurationTasks.add(new ServerResourcePackConfigurationTask(minecraftserver_serverresourcepackinfo));
        });
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packet) {
        this.clientInformation = packet.information();
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        super.handleResourcePackResponse(packet);
        if (packet.action().isTerminal()) {
            this.finishCurrentTask(ServerResourcePackConfigurationTask.TYPE);
        }

    }

    @Override
    public void handleSelectKnownPacks(ServerboundSelectKnownPacks packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server.packetProcessor());
        if (this.synchronizeRegistriesTask == null) {
            throw new IllegalStateException("Unexpected response from client: received pack selection, but no negotiation ongoing");
        } else {
            this.synchronizeRegistriesTask.handleResponse(packet.knownPacks(), this::send);
            this.finishCurrentTask(SynchronizeRegistriesTask.TYPE);
        }
    }

    @Override
    public void handleAcceptCodeOfConduct(ServerboundAcceptCodeOfConductPacket packet) {
        this.finishCurrentTask(ServerCodeOfConductConfigurationTask.TYPE);
    }

    @Override
    public void handleConfigurationFinished(ServerboundFinishConfigurationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server.packetProcessor());
        this.finishCurrentTask(JoinWorldTask.TYPE);
        this.connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess())));

        try {
            PlayerList playerlist = this.server.getPlayerList();

            if (playerlist.getPlayer(this.gameProfile.id()) != null) {
                this.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
                return;
            }

            Component component = playerlist.canPlayerLogin(this.connection.getRemoteAddress(), new NameAndId(this.gameProfile));

            if (component != null) {
                this.disconnect(component);
                return;
            }

            ((PrepareSpawnTask) Objects.requireNonNull(this.prepareSpawnTask)).spawnPlayer(this.connection, this.createCookie(this.clientInformation));
        } catch (Exception exception) {
            ServerConfigurationPacketListenerImpl.LOGGER.error("Couldn't place player in world", exception);
            this.disconnect(ServerConfigurationPacketListenerImpl.DISCONNECT_REASON_INVALID_DATA);
        }

    }

    @Override
    public void tick() {
        this.keepConnectionAlive();
        ConfigurationTask configurationtask = this.currentTask;

        if (configurationtask != null) {
            try {
                if (configurationtask.tick()) {
                    this.finishCurrentTask(configurationtask.type());
                }
            } catch (Exception exception) {
                ServerConfigurationPacketListenerImpl.LOGGER.error("Failed to tick configuration task {}", configurationtask.type(), exception);
                this.disconnect(ServerConfigurationPacketListenerImpl.DISCONNECT_REASON_CONFIGURATION_ERROR);
            }
        }

        if (this.prepareSpawnTask != null) {
            this.prepareSpawnTask.keepAlive();
        }

    }

    private void startNextTask() {
        if (this.currentTask != null) {
            throw new IllegalStateException("Task " + this.currentTask.type().id() + " has not finished yet");
        } else if (this.isAcceptingMessages()) {
            ConfigurationTask configurationtask = (ConfigurationTask) this.configurationTasks.poll();

            if (configurationtask != null) {
                this.currentTask = configurationtask;

                try {
                    configurationtask.start(this::send);
                } catch (Exception exception) {
                    ServerConfigurationPacketListenerImpl.LOGGER.error("Failed to start configuration task {}", configurationtask.type(), exception);
                    this.disconnect(ServerConfigurationPacketListenerImpl.DISCONNECT_REASON_CONFIGURATION_ERROR);
                }
            }

        }
    }

    private void finishCurrentTask(ConfigurationTask.Type taskTypeToFinish) {
        ConfigurationTask.Type configurationtask_type1 = this.currentTask != null ? this.currentTask.type() : null;

        if (!taskTypeToFinish.equals(configurationtask_type1)) {
            String s = String.valueOf(configurationtask_type1);

            throw new IllegalStateException("Unexpected request for task finish, current task: " + s + ", requested: " + String.valueOf(taskTypeToFinish));
        } else {
            this.currentTask = null;
            this.startNextTask();
        }
    }
}
