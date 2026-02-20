package net.minecraft.util;

import com.google.common.primitives.Longs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.network.FriendlyByteBuf;

public class Crypt {

    private static final String SYMMETRIC_ALGORITHM = "AES";
    private static final int SYMMETRIC_BITS = 128;
    private static final String ASYMMETRIC_ALGORITHM = "RSA";
    private static final int ASYMMETRIC_BITS = 1024;
    private static final String BYTE_ENCODING = "ISO_8859_1";
    private static final String HASH_ALGORITHM = "SHA-1";
    public static final String SIGNING_ALGORITHM = "SHA256withRSA";
    public static final int SIGNATURE_BYTES = 256;
    private static final String PEM_RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----";
    public static final String RSA_PUBLIC_KEY_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String RSA_PUBLIC_KEY_FOOTER = "-----END RSA PUBLIC KEY-----";
    public static final String MIME_LINE_SEPARATOR = "\n";
    public static final Encoder MIME_ENCODER = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.UTF_8));
    public static final Codec<PublicKey> PUBLIC_KEY_CODEC = Codec.STRING.comapFlatMap((s) -> {
        try {
            return DataResult.success(stringToRsaPublicKey(s));
        } catch (CryptException cryptexception) {
            Objects.requireNonNull(cryptexception);
            return DataResult.error(cryptexception::getMessage);
        }
    }, Crypt::rsaPublicKeyToString);
    public static final Codec<PrivateKey> PRIVATE_KEY_CODEC = Codec.STRING.comapFlatMap((s) -> {
        try {
            return DataResult.success(stringToPemRsaPrivateKey(s));
        } catch (CryptException cryptexception) {
            Objects.requireNonNull(cryptexception);
            return DataResult.error(cryptexception::getMessage);
        }
    }, Crypt::pemRsaPrivateKeyToString);

    public Crypt() {}

    public static SecretKey generateSecretKey() throws CryptException {
        try {
            KeyGenerator keygenerator = KeyGenerator.getInstance("AES");

            keygenerator.init(128);
            return keygenerator.generateKey();
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static KeyPair generateKeyPair() throws CryptException {
        try {
            KeyPairGenerator keypairgenerator = KeyPairGenerator.getInstance("RSA");

            keypairgenerator.initialize(1024);
            return keypairgenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static byte[] digestData(String serverId, PublicKey publicKey, SecretKey sharedKey) throws CryptException {
        try {
            return digestData(serverId.getBytes("ISO_8859_1"), sharedKey.getEncoded(), publicKey.getEncoded());
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    private static byte[] digestData(byte[]... inputs) throws Exception {
        MessageDigest messagedigest = MessageDigest.getInstance("SHA-1");

        for (byte[] abyte1 : inputs) {
            messagedigest.update(abyte1);
        }

        return messagedigest.digest();
    }

    private static <T extends Key> T rsaStringToKey(String input, String header, String footer, Crypt.ByteArrayToKeyFunction<T> byteArrayToKey) throws CryptException {
        int i = input.indexOf(header);

        if (i != -1) {
            i += header.length();
            int j = input.indexOf(footer, i);

            input = input.substring(i, j + 1);
        }

        try {
            return byteArrayToKey.apply(Base64.getMimeDecoder().decode(input));
        } catch (IllegalArgumentException illegalargumentexception) {
            throw new CryptException(illegalargumentexception);
        }
    }

    public static PrivateKey stringToPemRsaPrivateKey(String rsaString) throws CryptException {
        return (PrivateKey) rsaStringToKey(rsaString, "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----", Crypt::byteToPrivateKey);
    }

    public static PublicKey stringToRsaPublicKey(String rsaString) throws CryptException {
        return (PublicKey) rsaStringToKey(rsaString, "-----BEGIN RSA PUBLIC KEY-----", "-----END RSA PUBLIC KEY-----", Crypt::byteToPublicKey);
    }

    public static String rsaPublicKeyToString(PublicKey publicKey) {
        if (!"RSA".equals(publicKey.getAlgorithm())) {
            throw new IllegalArgumentException("Public key must be RSA");
        } else {
            Encoder encoder = Crypt.MIME_ENCODER;

            return "-----BEGIN RSA PUBLIC KEY-----\n" + encoder.encodeToString(publicKey.getEncoded()) + "\n-----END RSA PUBLIC KEY-----\n";
        }
    }

    public static String pemRsaPrivateKeyToString(PrivateKey privateKey) {
        if (!"RSA".equals(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("Private key must be RSA");
        } else {
            Encoder encoder = Crypt.MIME_ENCODER;

            return "-----BEGIN RSA PRIVATE KEY-----\n" + encoder.encodeToString(privateKey.getEncoded()) + "\n-----END RSA PRIVATE KEY-----\n";
        }
    }

    private static PrivateKey byteToPrivateKey(byte[] keyData) throws CryptException {
        try {
            EncodedKeySpec encodedkeyspec = new PKCS8EncodedKeySpec(keyData);
            KeyFactory keyfactory = KeyFactory.getInstance("RSA");

            return keyfactory.generatePrivate(encodedkeyspec);
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static PublicKey byteToPublicKey(byte[] keyData) throws CryptException {
        try {
            EncodedKeySpec encodedkeyspec = new X509EncodedKeySpec(keyData);
            KeyFactory keyfactory = KeyFactory.getInstance("RSA");

            return keyfactory.generatePublic(encodedkeyspec);
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static SecretKey decryptByteToSecretKey(PrivateKey privateKey, byte[] keyData) throws CryptException {
        byte[] abyte1 = decryptUsingKey(privateKey, keyData);

        try {
            return new SecretKeySpec(abyte1, "AES");
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static byte[] encryptUsingKey(Key key, byte[] input) throws CryptException {
        return cipherData(1, key, input);
    }

    public static byte[] decryptUsingKey(Key key, byte[] input) throws CryptException {
        return cipherData(2, key, input);
    }

    private static byte[] cipherData(int cipherOpMode, Key key, byte[] input) throws CryptException {
        try {
            return setupCipher(cipherOpMode, key.getAlgorithm(), key).doFinal(input);
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    private static Cipher setupCipher(int cipherOpMode, String algorithm, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);

        cipher.init(cipherOpMode, key);
        return cipher;
    }

    public static Cipher getCipher(int opMode, Key key) throws CryptException {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");

            cipher.init(opMode, key, new IvParameterSpec(key.getEncoded()));
            return cipher;
        } catch (Exception exception) {
            throw new CryptException(exception);
        }
    }

    public static class SaltSupplier {

        private static final SecureRandom secureRandom = new SecureRandom();

        public SaltSupplier() {}

        public static long getLong() {
            return Crypt.SaltSupplier.secureRandom.nextLong();
        }
    }

    public static record SaltSignaturePair(long salt, byte[] signature) {

        public static final Crypt.SaltSignaturePair EMPTY = new Crypt.SaltSignaturePair(0L, ByteArrays.EMPTY_ARRAY);

        public SaltSignaturePair(FriendlyByteBuf input) {
            this(input.readLong(), input.readByteArray());
        }

        public boolean isValid() {
            return this.signature.length > 0;
        }

        public static void write(FriendlyByteBuf output, Crypt.SaltSignaturePair saltSignaturePair) {
            output.writeLong(saltSignaturePair.salt);
            output.writeByteArray(saltSignaturePair.signature);
        }

        public byte[] saltAsBytes() {
            return Longs.toByteArray(this.salt);
        }
    }

    private interface ByteArrayToKeyFunction<T extends Key> {

        T apply(byte[] input) throws CryptException;
    }
}
