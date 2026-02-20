package net.minecraft.util;

import com.mojang.authlib.yggdrasil.ServicesKeyInfo;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface SignatureValidator {

    SignatureValidator NO_VALIDATION = (signatureupdater, abyte) -> {
        return true;
    };
    Logger LOGGER = LogUtils.getLogger();

    boolean validate(SignatureUpdater updater, byte[] signature);

    default boolean validate(byte[] payload, byte[] signature) {
        return this.validate((signatureupdater_output) -> {
            signatureupdater_output.update(payload);
        }, signature);
    }

    private static boolean verifySignature(SignatureUpdater updater, byte[] signature, Signature verifier) throws SignatureException {
        Objects.requireNonNull(verifier);
        updater.update(verifier::update);
        return verifier.verify(signature);
    }

    static SignatureValidator from(PublicKey publicKey, String algorithm) {
        return (signatureupdater, abyte) -> {
            try {
                Signature signature = Signature.getInstance(algorithm);

                signature.initVerify(publicKey);
                return verifySignature(signatureupdater, abyte, signature);
            } catch (Exception exception) {
                SignatureValidator.LOGGER.error("Failed to verify signature", exception);
                return false;
            }
        };
    }

    static @Nullable SignatureValidator from(ServicesKeySet keySet, ServicesKeyType type) {
        Collection<ServicesKeyInfo> collection = keySet.keys(type);

        return collection.isEmpty() ? null : (signatureupdater, abyte) -> {
            return collection.stream().anyMatch((serviceskeyinfo) -> {
                Signature signature = serviceskeyinfo.signature();

                try {
                    return verifySignature(signatureupdater, abyte, signature);
                } catch (SignatureException signatureexception) {
                    SignatureValidator.LOGGER.error("Failed to verify Services signature", signatureexception);
                    return false;
                }
            });
        };
    }
}
