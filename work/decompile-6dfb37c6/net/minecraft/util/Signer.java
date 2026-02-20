package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Objects;
import org.slf4j.Logger;

public interface Signer {

    Logger LOGGER = LogUtils.getLogger();

    byte[] sign(SignatureUpdater updater);

    default byte[] sign(byte[] payload) {
        return this.sign((signatureupdater_output) -> {
            signatureupdater_output.update(payload);
        });
    }

    static Signer from(PrivateKey privateKey, String algorithm) {
        return (signatureupdater) -> {
            try {
                Signature signature = Signature.getInstance(algorithm);

                signature.initSign(privateKey);
                Objects.requireNonNull(signature);
                signatureupdater.update(signature::update);
                return signature.sign();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to sign message", exception);
            }
        };
    }
}
