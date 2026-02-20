package net.minecraft.server.jsonrpc.security;

import com.mojang.logging.LogUtils;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;

public class JsonRpcSslContextProvider {

    private static final String PASSWORD_ENV_VARIABLE_KEY = "MINECRAFT_MANAGEMENT_TLS_KEYSTORE_PASSWORD";
    private static final String PASSWORD_SYSTEM_PROPERTY_KEY = "management.tls.keystore.password";
    private static final Logger log = LogUtils.getLogger();

    public JsonRpcSslContextProvider() {}

    public static SslContext createFrom(String keystorePath, String keystorePasswordFromServerProperties) throws Exception {
        if (keystorePath.isEmpty()) {
            throw new IllegalArgumentException("TLS is enabled but keystore is not configured");
        } else {
            File file = new File(keystorePath);

            if (file.exists() && file.isFile()) {
                String s2 = getKeystorePassword(keystorePasswordFromServerProperties);

                return loadKeystoreFromPath(file, s2);
            } else {
                throw new IllegalArgumentException("Supplied keystore is not a file or does not exist: '" + keystorePath + "'");
            }
        }
    }

    private static String getKeystorePassword(String keystorePasswordFromServerProperties) {
        String s1 = (String) System.getenv().get("MINECRAFT_MANAGEMENT_TLS_KEYSTORE_PASSWORD");

        if (s1 != null) {
            return s1;
        } else {
            String s2 = System.getProperty("management.tls.keystore.password", (String) null);

            return s2 != null ? s2 : keystorePasswordFromServerProperties;
        }
    }

    private static SslContext loadKeystoreFromPath(File keyStoreFile, String password) throws Exception {
        KeyStore keystore = KeyStore.getInstance("PKCS12");

        try (InputStream inputstream = new FileInputStream(keyStoreFile)) {
            keystore.load(inputstream, password.toCharArray());
        }

        KeyManagerFactory keymanagerfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        keymanagerfactory.init(keystore, password.toCharArray());
        TrustManagerFactory trustmanagerfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        trustmanagerfactory.init(keystore);
        return SslContextBuilder.forServer(keymanagerfactory).trustManager(trustmanagerfactory).build();
    }

    public static void printInstructions() {
        JsonRpcSslContextProvider.log.info("To use TLS for the management server, please follow these steps:");
        JsonRpcSslContextProvider.log.info("1. Set the server property 'management-server-tls-enabled' to 'true' to enable TLS");
        JsonRpcSslContextProvider.log.info("2. Create a keystore file of type PKCS12 containing your server certificate and private key");
        JsonRpcSslContextProvider.log.info("3. Set the server property 'management-server-tls-keystore' to the path of your keystore file");
        JsonRpcSslContextProvider.log.info("4. Set the keystore password via the environment variable 'MINECRAFT_MANAGEMENT_TLS_KEYSTORE_PASSWORD', or system property 'management.tls.keystore.password', or server property 'management-server-tls-keystore-password'");
        JsonRpcSslContextProvider.log.info("5. Restart the server to apply the changes.");
    }
}
