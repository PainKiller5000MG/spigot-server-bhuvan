package net.minecraft.server.rcon.thread;

import com.mojang.logging.LogUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.rcon.PktUtils;
import org.slf4j.Logger;

public class RconClient extends GenericThread {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_RESPONSE_VALUE = 0;
    private static final int SERVERDATA_AUTH_RESPONSE = 2;
    private static final int SERVERDATA_AUTH_FAILURE = -1;
    private boolean authed;
    private final Socket client;
    private final byte[] buf = new byte[1460];
    private final String rconPassword;
    private final ServerInterface serverInterface;

    RconClient(ServerInterface serverInterface, String rconPassword, Socket socket) {
        super("RCON Client " + String.valueOf(socket.getInetAddress()));
        this.serverInterface = serverInterface;
        this.client = socket;

        try {
            this.client.setSoTimeout(0);
        } catch (Exception exception) {
            this.running = false;
        }

        this.rconPassword = rconPassword;
    }

    public void run() {
        try {
            try {
                while (this.running) {
                    BufferedInputStream bufferedinputstream = new BufferedInputStream(this.client.getInputStream());
                    int i = bufferedinputstream.read(this.buf, 0, 1460);

                    if (10 > i) {
                        return;
                    }

                    int j = 0;
                    int k = PktUtils.intFromByteArray(this.buf, 0, i);

                    if (k != i - 4) {
                        return;
                    }

                    j += 4;
                    int l = PktUtils.intFromByteArray(this.buf, j, i);

                    j += 4;
                    int i1 = PktUtils.intFromByteArray(this.buf, j);

                    j += 4;
                    switch (i1) {
                        case 2:
                            if (this.authed) {
                                String s = PktUtils.stringFromByteArray(this.buf, j, i);

                                try {
                                    this.sendCmdResponse(l, this.serverInterface.runCommand(s));
                                } catch (Exception exception) {
                                    this.sendCmdResponse(l, "Error executing: " + s + " (" + exception.getMessage() + ")");
                                }
                                break;
                            }

                            this.sendAuthFailure();
                            break;
                        case 3:
                            String s1 = PktUtils.stringFromByteArray(this.buf, j, i);
                            int j1 = j + s1.length();

                            if (!s1.isEmpty() && s1.equals(this.rconPassword)) {
                                this.authed = true;
                                this.send(l, 2, "");
                                break;
                            }

                            this.authed = false;
                            this.sendAuthFailure();
                            break;
                        default:
                            this.sendCmdResponse(l, String.format(Locale.ROOT, "Unknown request %s", Integer.toHexString(i1)));
                    }
                }

                return;
            } catch (IOException ioexception) {
                ;
            } catch (Exception exception1) {
                RconClient.LOGGER.error("Exception whilst parsing RCON input", exception1);
            }

        } finally {
            this.closeSocket();
            RconClient.LOGGER.info("Thread {} shutting down", this.name);
            this.running = false;
        }
    }

    private void send(int requestid, int cmd, String str) throws IOException {
        ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream(1248);
        DataOutputStream dataoutputstream = new DataOutputStream(bytearrayoutputstream);
        byte[] abyte = str.getBytes(StandardCharsets.UTF_8);

        dataoutputstream.writeInt(Integer.reverseBytes(abyte.length + 10));
        dataoutputstream.writeInt(Integer.reverseBytes(requestid));
        dataoutputstream.writeInt(Integer.reverseBytes(cmd));
        dataoutputstream.write(abyte);
        dataoutputstream.write(0);
        dataoutputstream.write(0);
        this.client.getOutputStream().write(bytearrayoutputstream.toByteArray());
    }

    private void sendAuthFailure() throws IOException {
        this.send(-1, 2, "");
    }

    private void sendCmdResponse(int requestid, String response) throws IOException {
        int j = response.length();

        do {
            int k = 4096 <= j ? 4096 : j;

            this.send(requestid, 0, response.substring(0, k));
            response = response.substring(k);
            j = response.length();
        } while (0 != j);

    }

    @Override
    public void stop() {
        this.running = false;
        this.closeSocket();
        super.stop();
    }

    private void closeSocket() {
        try {
            this.client.close();
        } catch (IOException ioexception) {
            RconClient.LOGGER.warn("Failed to close socket", ioexception);
        }

    }
}
