package net.minecraft.server.rcon.thread;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.rcon.NetworkDataOutputStream;
import net.minecraft.server.rcon.PktUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class QueryThreadGs4 extends GenericThread {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GAME_TYPE = "SMP";
    private static final String GAME_ID = "MINECRAFT";
    private static final long CHALLENGE_CHECK_INTERVAL = 30000L;
    private static final long RESPONSE_CACHE_TIME = 5000L;
    private long lastChallengeCheck;
    private final int port;
    private final int serverPort;
    private final int maxPlayers;
    private final String serverName;
    private final String worldName;
    private DatagramSocket socket;
    private final byte[] buffer = new byte[1460];
    private String hostIp;
    private String serverIp;
    private final Map<SocketAddress, QueryThreadGs4.RequestChallenge> validChallenges;
    private final NetworkDataOutputStream rulesResponse;
    private long lastRulesResponse;
    private final ServerInterface serverInterface;

    private QueryThreadGs4(ServerInterface serverInterface, int port) {
        super("Query Listener");
        this.serverInterface = serverInterface;
        this.port = port;
        this.serverIp = serverInterface.getServerIp();
        this.serverPort = serverInterface.getServerPort();
        this.serverName = serverInterface.getServerName();
        this.maxPlayers = serverInterface.getMaxPlayers();
        this.worldName = serverInterface.getLevelIdName();
        this.lastRulesResponse = 0L;
        this.hostIp = "0.0.0.0";
        if (!this.serverIp.isEmpty() && !this.hostIp.equals(this.serverIp)) {
            this.hostIp = this.serverIp;
        } else {
            this.serverIp = "0.0.0.0";

            try {
                InetAddress inetaddress = InetAddress.getLocalHost();

                this.hostIp = inetaddress.getHostAddress();
            } catch (UnknownHostException unknownhostexception) {
                QueryThreadGs4.LOGGER.warn("Unable to determine local host IP, please set server-ip in server.properties", unknownhostexception);
            }
        }

        this.rulesResponse = new NetworkDataOutputStream(1460);
        this.validChallenges = Maps.newHashMap();
    }

    public static @Nullable QueryThreadGs4 create(ServerInterface serverInterface) {
        int i = serverInterface.getProperties().queryPort;

        if (0 < i && 65535 >= i) {
            QueryThreadGs4 querythreadgs4 = new QueryThreadGs4(serverInterface, i);

            return !querythreadgs4.start() ? null : querythreadgs4;
        } else {
            QueryThreadGs4.LOGGER.warn("Invalid query port {} found in server.properties (queries disabled)", i);
            return null;
        }
    }

    private void sendTo(byte[] data, DatagramPacket src) throws IOException {
        this.socket.send(new DatagramPacket(data, data.length, src.getSocketAddress()));
    }

    private boolean processPacket(DatagramPacket packet) throws IOException {
        byte[] abyte = packet.getData();
        int i = packet.getLength();
        SocketAddress socketaddress = packet.getSocketAddress();

        QueryThreadGs4.LOGGER.debug("Packet len {} [{}]", i, socketaddress);
        if (3 <= i && -2 == abyte[0] && -3 == abyte[1]) {
            QueryThreadGs4.LOGGER.debug("Packet '{}' [{}]", PktUtils.toHexString(abyte[2]), socketaddress);
            switch (abyte[2]) {
                case 0:
                    if (!this.validChallenge(packet)) {
                        QueryThreadGs4.LOGGER.debug("Invalid challenge [{}]", socketaddress);
                        return false;
                    } else if (15 == i) {
                        this.sendTo(this.buildRuleResponse(packet), packet);
                        QueryThreadGs4.LOGGER.debug("Rules [{}]", socketaddress);
                    } else {
                        NetworkDataOutputStream networkdataoutputstream = new NetworkDataOutputStream(1460);

                        networkdataoutputstream.write(0);
                        networkdataoutputstream.writeBytes(this.getIdentBytes(packet.getSocketAddress()));
                        networkdataoutputstream.writeString(this.serverName);
                        networkdataoutputstream.writeString("SMP");
                        networkdataoutputstream.writeString(this.worldName);
                        networkdataoutputstream.writeString(Integer.toString(this.serverInterface.getPlayerCount()));
                        networkdataoutputstream.writeString(Integer.toString(this.maxPlayers));
                        networkdataoutputstream.writeShort((short) this.serverPort);
                        networkdataoutputstream.writeString(this.hostIp);
                        this.sendTo(networkdataoutputstream.toByteArray(), packet);
                        QueryThreadGs4.LOGGER.debug("Status [{}]", socketaddress);
                    }
                default:
                    return true;
                case 9:
                    this.sendChallenge(packet);
                    QueryThreadGs4.LOGGER.debug("Challenge [{}]", socketaddress);
                    return true;
            }
        } else {
            QueryThreadGs4.LOGGER.debug("Invalid packet [{}]", socketaddress);
            return false;
        }
    }

    private byte[] buildRuleResponse(DatagramPacket packet) throws IOException {
        long i = Util.getMillis();

        if (i < this.lastRulesResponse + 5000L) {
            byte[] abyte = this.rulesResponse.toByteArray();
            byte[] abyte1 = this.getIdentBytes(packet.getSocketAddress());

            abyte[1] = abyte1[0];
            abyte[2] = abyte1[1];
            abyte[3] = abyte1[2];
            abyte[4] = abyte1[3];
            return abyte;
        } else {
            this.lastRulesResponse = i;
            this.rulesResponse.reset();
            this.rulesResponse.write(0);
            this.rulesResponse.writeBytes(this.getIdentBytes(packet.getSocketAddress()));
            this.rulesResponse.writeString("splitnum");
            this.rulesResponse.write(128);
            this.rulesResponse.write(0);
            this.rulesResponse.writeString("hostname");
            this.rulesResponse.writeString(this.serverName);
            this.rulesResponse.writeString("gametype");
            this.rulesResponse.writeString("SMP");
            this.rulesResponse.writeString("game_id");
            this.rulesResponse.writeString("MINECRAFT");
            this.rulesResponse.writeString("version");
            this.rulesResponse.writeString(this.serverInterface.getServerVersion());
            this.rulesResponse.writeString("plugins");
            this.rulesResponse.writeString(this.serverInterface.getPluginNames());
            this.rulesResponse.writeString("map");
            this.rulesResponse.writeString(this.worldName);
            this.rulesResponse.writeString("numplayers");
            this.rulesResponse.writeString("" + this.serverInterface.getPlayerCount());
            this.rulesResponse.writeString("maxplayers");
            this.rulesResponse.writeString("" + this.maxPlayers);
            this.rulesResponse.writeString("hostport");
            this.rulesResponse.writeString("" + this.serverPort);
            this.rulesResponse.writeString("hostip");
            this.rulesResponse.writeString(this.hostIp);
            this.rulesResponse.write(0);
            this.rulesResponse.write(1);
            this.rulesResponse.writeString("player_");
            this.rulesResponse.write(0);
            String[] astring = this.serverInterface.getPlayerNames();

            for (String s : astring) {
                this.rulesResponse.writeString(s);
            }

            this.rulesResponse.write(0);
            return this.rulesResponse.toByteArray();
        }
    }

    private byte[] getIdentBytes(SocketAddress src) {
        return ((QueryThreadGs4.RequestChallenge) this.validChallenges.get(src)).getIdentBytes();
    }

    private Boolean validChallenge(DatagramPacket src) {
        SocketAddress socketaddress = src.getSocketAddress();

        if (!this.validChallenges.containsKey(socketaddress)) {
            return false;
        } else {
            byte[] abyte = src.getData();

            return ((QueryThreadGs4.RequestChallenge) this.validChallenges.get(socketaddress)).getChallenge() == PktUtils.intFromNetworkByteArray(abyte, 7, src.getLength());
        }
    }

    private void sendChallenge(DatagramPacket src) throws IOException {
        QueryThreadGs4.RequestChallenge querythreadgs4_requestchallenge = new QueryThreadGs4.RequestChallenge(src);

        this.validChallenges.put(src.getSocketAddress(), querythreadgs4_requestchallenge);
        this.sendTo(querythreadgs4_requestchallenge.getChallengeBytes(), src);
    }

    private void pruneChallenges() {
        if (this.running) {
            long i = Util.getMillis();

            if (i >= this.lastChallengeCheck + 30000L) {
                this.lastChallengeCheck = i;
                this.validChallenges.values().removeIf((querythreadgs4_requestchallenge) -> {
                    return querythreadgs4_requestchallenge.before(i);
                });
            }
        }
    }

    public void run() {
        QueryThreadGs4.LOGGER.info("Query running on {}:{}", this.serverIp, this.port);
        this.lastChallengeCheck = Util.getMillis();
        DatagramPacket datagrampacket = new DatagramPacket(this.buffer, this.buffer.length);

        try {
            while (this.running) {
                try {
                    this.socket.receive(datagrampacket);
                    this.pruneChallenges();
                    this.processPacket(datagrampacket);
                } catch (SocketTimeoutException sockettimeoutexception) {
                    this.pruneChallenges();
                } catch (PortUnreachableException portunreachableexception) {
                    ;
                } catch (IOException ioexception) {
                    this.recoverSocketError(ioexception);
                }
            }
        } finally {
            QueryThreadGs4.LOGGER.debug("closeSocket: {}:{}", this.serverIp, this.port);
            this.socket.close();
        }

    }

    @Override
    public boolean start() {
        return this.running ? true : (!this.initSocket() ? false : super.start());
    }

    private void recoverSocketError(Exception e) {
        if (this.running) {
            QueryThreadGs4.LOGGER.warn("Unexpected exception", e);
            if (!this.initSocket()) {
                QueryThreadGs4.LOGGER.error("Failed to recover from exception, shutting down!");
                this.running = false;
            }

        }
    }

    private boolean initSocket() {
        try {
            this.socket = new DatagramSocket(this.port, InetAddress.getByName(this.serverIp));
            this.socket.setSoTimeout(500);
            return true;
        } catch (Exception exception) {
            QueryThreadGs4.LOGGER.warn("Unable to initialise query system on {}:{}", new Object[]{this.serverIp, this.port, exception});
            return false;
        }
    }

    private static class RequestChallenge {

        private final long time = (new Date()).getTime();
        private final int challenge;
        private final byte[] identBytes;
        private final byte[] challengeBytes;
        private final String ident;

        public RequestChallenge(DatagramPacket src) {
            byte[] abyte = src.getData();

            this.identBytes = new byte[4];
            this.identBytes[0] = abyte[3];
            this.identBytes[1] = abyte[4];
            this.identBytes[2] = abyte[5];
            this.identBytes[3] = abyte[6];
            this.ident = new String(this.identBytes, StandardCharsets.UTF_8);
            this.challenge = RandomSource.create().nextInt(16777216);
            this.challengeBytes = String.format(Locale.ROOT, "\t%s%d\u0000", this.ident, this.challenge).getBytes(StandardCharsets.UTF_8);
        }

        public Boolean before(long time) {
            return this.time < time;
        }

        public int getChallenge() {
            return this.challenge;
        }

        public byte[] getChallengeBytes() {
            return this.challengeBytes;
        }

        public byte[] getIdentBytes() {
            return this.identBytes;
        }

        public String getIdent() {
            return this.ident;
        }
    }
}
