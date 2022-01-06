// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Base64;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import static eu.ginlo_apps.ginlo.exception.LocalizedException.JSON_OBJECT_NULL;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.util.Date;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.x509.X509V3CertificateGenerator;

public class SecurityUtil {
    public static final int AES_KEY_LENGTH = 256;

    public static final int IV_LENGTH = 128;
    public static final String DERIVE_ALGORITHM_SHA_256 = "PBKDF2WithHmacSHA256";
    private static final int RSA_KEY_LENGTH = 2048;
    private static final int ROUNDS_ADMIN_CONSOLE = 8000;
    private static final String SIGNATURE_INSTANCE = "SHA1WithRSA";
    private static final String SIGNATURE_INSTANCE_SHA256 = "SHA256WithRSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1WithRSAEncryption";
    private static final String CN_LOCALHOST = "CN=localhost";
    private static final String DERIVE_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String RANDOM_ALGORITHM = "SHA1PRNG";

    private static final String RSA_GEN_ALGORITHM = "RSA";

    private static final String RSA_CIPHER_ALGORITHM = "RSA/ECB/OAEPWithSHA1AndMGF1Padding";

    private static final String AES_GEN_ALGORITHM = "AES";

    private static final String AES_CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    private static final String AES_CIPHER_ALGORITHM_NO_CBC = "AES";

    private static final String AES_CIPHER_ALGORITHM_GCM = "AES/GCM/NoPadding";
    /**
     * dateiname fuer das recovery-file
     */
    private static final String PW_RECOVERY_FILE_NAME = "key.txt";
    /**
     * dateiname fuer das recovery-file
     */
    private static final String NOTIFICATION_PREVIEW_FILE_NAME = "notification_key.txt";
    private static SecureRandom random;

    private SecurityUtil() {
    }

    @SuppressLint("TrulyRandom")
    private static synchronized SecureRandom getSecureRandomInstance()
            throws LocalizedException {
        if (random == null) {
            try {
                random = SecureRandom.getInstance(RANDOM_ALGORITHM);
            } catch (NoSuchAlgorithmException e) {
                LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
                throw new LocalizedException(LocalizedException.GENERATE_IV_FAILED, e);
            }
        }

        return random;
    }

    public static byte[] encryptMessageWithRSA(byte[] messageBytes,
                                               Key key)
            throws LocalizedException {
        byte[] encrypted;

        try {
            Cipher cipher = getRSACipher(Cipher.ENCRYPT_MODE, key);

            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(messageBytes);
            final int BUFFER_LENGTH = 64;

            while (byteArrayInputStream.available() > 0) {
                byte[] buffer;

                if (byteArrayInputStream.available() > BUFFER_LENGTH) {
                    buffer = new byte[BUFFER_LENGTH];
                } else {
                    buffer = new byte[byteArrayInputStream.available()];
                }

                int read = byteArrayInputStream.read(buffer);
                if (read == -1)
                    throw new LocalizedException(LocalizedException.ENCRYPT_DATA_FAILED, new Exception("Unable to read from input bytes"));

                cipher.update(buffer);
            }

            encrypted = cipher.doFinal();
        } catch (IllegalBlockSizeException | BadPaddingException | IOException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.ENCRYPT_DATA_FAILED, e);
        }

        return encrypted;
    }

    public static String encryptWithAESToBase64String(String decrytedString, Key key)
            throws LocalizedException {
        byte[] encodeBytes = encryptMessageWithAES(decrytedString.getBytes(StandardCharsets.UTF_8),
                key,
                new IvParameterSpec(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        return Base64.encodeToString(encodeBytes, Base64.NO_WRAP);
    }

    public static byte[] encryptStringWithAES(String decrytedString, Key key, IvParameterSpec iv)
            throws LocalizedException {
        return encryptMessageWithAES(decrytedString.getBytes(StandardCharsets.UTF_8), key, iv);
    }

    public static byte[] encryptMessageWithAES(byte[] messageBytes, Key key, IvParameterSpec iv) throws LocalizedException {
        byte[] encrypted;

        try {
            if (iv == null) {
                // Für das Verschlüsseln muss jetzt immer ein IV mitgegeben werden (SME-04-009)
                throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED);
            }
            Cipher cipher = getAESCipher(Cipher.ENCRYPT_MODE, key, iv);

            encrypted = cipher.doFinal(messageBytes);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.ENCRYPT_DATA_FAILED, e);
        }

        return encrypted;
    }

    public static byte[] decryptMessageWithRSA(byte[] encryptedData,
                                               Key key)
            throws LocalizedException {
        byte[] decrypted;

        if (encryptedData == null) {
            return null;
        }

        try {
            Cipher cipher = getRSACipher(Cipher.DECRYPT_MODE, key);

            cipher.init(Cipher.DECRYPT_MODE, key);
            decrypted = cipher.doFinal(encryptedData);
        } catch (InvalidKeyException | DataLengthException | BadPaddingException | IllegalBlockSizeException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, e);
        }
        return decrypted;
    }

    public static String decryptBase64StringWithAES(@NonNull final String encryptedString, @NonNull final Key key)
            throws LocalizedException {
        byte[] encryptedBytes = Base64.decode(encryptedString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        byte[] statusAsBytes = decryptMessageWithAES(encryptedBytes, key);

        return new String(statusAsBytes, StandardCharsets.UTF_8);
    }

    public static String decryptBase64StringWithAES(@NonNull final String encryptedString, @NonNull final Key key, @NonNull final String ivAsBase64)
            throws LocalizedException {
        byte[] ivBytes = Base64.decode(ivAsBase64, Base64.NO_WRAP);

        if (ivBytes == null) {
            throw new LocalizedException(LocalizedException.NO_IV_DATA, "iv bytes null");
        }

        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        return decryptBase64StringWithAES(encryptedString, key, iv);
    }

    public static String decryptBase64StringWithAES(@NonNull final String encryptedString, @NonNull Key key, @NonNull IvParameterSpec iv)
            throws LocalizedException {
        try {
            byte[] encryptedBytes = Base64.decode(encryptedString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            byte[] statusAsBytes = decryptMessageWithAES(encryptedBytes, key, iv);

            return new String(statusAsBytes, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw new LocalizedException(LocalizedException.BASE64_FAILED, e);
        }
    }

    public static byte[] decryptMessageWithAES(byte[] encryptedData,
                                               Key key)
            throws LocalizedException {
        final byte[] ivBytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        return decryptMessageWithAES(encryptedData, key, iv);
    }

    public static byte[] decryptMessageWithAES(byte[] encryptedData,
                                               Key key,
                                               IvParameterSpec iv)
            throws LocalizedException {
        return decryptMessageWithAES(encryptedData, key, iv, true);
    }

    public static byte[] decryptMessageWithAES(byte[] encryptedData,
                                               Key key,
                                               IvParameterSpec iv,
                                               boolean bLogExcpetions)
            throws LocalizedException {
        byte[] decrypted;

        try {
            Cipher cipher = getAESCipher(Cipher.DECRYPT_MODE, key, iv);

            decrypted = cipher.doFinal(encryptedData);
        } catch (IllegalBlockSizeException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, e);
        } catch (BadPaddingException e) {
            // Erwarteter Fehler, wenn das Falsche Passwort eingegeben wurde
            if (bLogExcpetions) {
                LogUtil.w(SecurityUtil.class.getName(), e.getMessage(), e);
            }
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, e);
        } catch (Throwable t) {
            LogUtil.e(SecurityUtil.class.getName(), t.getMessage(), t);
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, t);
        }
        return decrypted;
    }

    public static void decryptFileWithAes(final Key key,
                                          final IvParameterSpec iv,
                                          final File encryptedFile,
                                          final File decryptedFile,
                                          final boolean isIvInFile)
            throws LocalizedException {

        IvParameterSpec ivParameterSpec = iv;

        try (FileInputStream inputStream = new FileInputStream(encryptedFile)) {
            if (isIvInFile) {
                byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
                eu.ginlo_apps.ginlo.util.StreamUtil.safeRead(inputStream, ivBytes, ivBytes.length);
                ivParameterSpec = new IvParameterSpec(ivBytes);
            }

            Cipher cipher = getAESCipher(Cipher.DECRYPT_MODE, key, ivParameterSpec);

            try (BufferedInputStream bufferdCipherInputStream = new BufferedInputStream(inputStream);
                 CipherInputStream cipherIn = new CipherInputStream(bufferdCipherInputStream, cipher);
                 FileOutputStream outputStream = new FileOutputStream(decryptedFile);
                 BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(outputStream)) {
                eu.ginlo_apps.ginlo.util.StreamUtil.copyStreams(cipherIn, bufferedFileOutputStream);
            }
        } catch (FileNotFoundException e) {
            throw new LocalizedException(LocalizedException.LOAD_FILE_FAILED, e);
        } catch (Throwable t) {
            LogUtil.w(SecurityUtil.class.getSimpleName(), t.getMessage(), t);
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, t);
        }
    }

    public static JsonObject generateGCMKey() throws LocalizedException {
        JsonObject rc = new JsonObject();
        String aesKey = Base64.encodeToString(generateRandomData(256 / 8), Base64.NO_WRAP);
        rc.addProperty(JsonConstants.KEY, aesKey);

        String iv = Base64.encodeToString(generateRandomData(128 / 8), Base64.NO_WRAP);
        rc.addProperty(JsonConstants.IV, iv);

        String aad = Base64.encodeToString(generateRandomData(128 / 8), Base64.NO_WRAP);
        rc.addProperty(JsonConstants.AAD, aad);

        return rc;
    }

    public static String decryptStringWithGCM(@NonNull final String encryptedBase64String, @NonNull final JsonObject keyJO)
            throws LocalizedException {
        try {
            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.KEY, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "key value not found");
            }

            String base64KeyString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.KEY, keyJO);

            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.IV, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "iv value not found");
            }

            String base64IvString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.IV, keyJO);

            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.AAD, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "aad value not found");
            }

            String base64AadString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.AAD, keyJO);

            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.AUTH_TAG, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "auth tag not found");
            }

            String base64AuthTagString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.AUTH_TAG, keyJO);

            byte[] aesKeyBytes = Base64.decode(base64KeyString, Base64.NO_WRAP);
            byte[] ivBytes = Base64.decode(base64IvString, Base64.NO_WRAP);
            byte[] authTagBytes = Base64.decode(base64AuthTagString, Base64.NO_WRAP);
            byte[] aadBytes = Base64.decode(base64AadString, Base64.NO_WRAP);
            byte[] encryptedBytes = Base64.decode(encryptedBase64String, Base64.NO_WRAP);

            byte[] encAndAuthBytes = new byte[encryptedBytes.length + authTagBytes.length];
            System.arraycopy(encryptedBytes, 0, encAndAuthBytes, 0, encryptedBytes.length);
            System.arraycopy(authTagBytes, 0, encAndAuthBytes, encryptedBytes.length, authTagBytes.length);

            SecretKey key = generateAESKey(aesKeyBytes);
            GCMParameterSpec spec = new GCMParameterSpec(authTagBytes.length * 8, ivBytes);

            Cipher cipher = Cipher.getInstance(AES_CIPHER_ALGORITHM_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            cipher.updateAAD(aadBytes);
            byte[] decryptedBytes = cipher.doFinal(encAndAuthBytes);//cipher.update(encAndAuthBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, e);
        }
    }

    public static String encryptStringWithGCM(@NonNull final String decryptedBase64String, @NonNull final JsonObject keyJO)
            throws LocalizedException {
        try {
            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.KEY, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "key value not found");
            }

            String base64KeyString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.KEY, keyJO);

            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.IV, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "iv value not found");
            }

            String base64IvString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.IV, keyJO);

            if (!eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(JsonConstants.AAD, keyJO)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "aad value not found");
            }

            String base64AadString = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO(JsonConstants.AAD, keyJO);

            byte[] aesKeyBytes = Base64.decode(base64KeyString, Base64.NO_WRAP);
            byte[] ivBytes = Base64.decode(base64IvString, Base64.NO_WRAP);
            byte[] aadBytes = Base64.decode(base64AadString, Base64.NO_WRAP);
            byte[] decryptedBytes = decryptedBase64String.getBytes(StandardCharsets.UTF_8);

            SecretKey key = generateAESKey(aesKeyBytes);
            GCMParameterSpec spec = new GCMParameterSpec(16 * 8, ivBytes);

            Cipher cipher = Cipher.getInstance(AES_CIPHER_ALGORITHM_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            cipher.updateAAD(aadBytes);
            byte[] encAndAuthBytes = cipher.doFinal(decryptedBytes);

            byte[] encryptedBytes = new byte[encAndAuthBytes.length - 16];
            byte[] authTagBytes = new byte[16];
            System.arraycopy(encAndAuthBytes, 0, encryptedBytes, 0, encryptedBytes.length);
            System.arraycopy(encAndAuthBytes, encryptedBytes.length, authTagBytes, 0, authTagBytes.length);

            String base64AuthTagString = Base64.encodeToString(authTagBytes, Base64.NO_WRAP);
            keyJO.addProperty(JsonConstants.AUTH_TAG, base64AuthTagString);

            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, e);
        }
    }

    public static void encryptFileWithAes(final Key key,
                                          final IvParameterSpec iv,
                                          final boolean saveIvInFile,
                                          final File decryptedFile,
                                          final File encryptedFile)
            throws LocalizedException {
        try (FileOutputStream outputStream = new FileOutputStream(encryptedFile);
             BufferedOutputStream bufferedFileOutputStream = new BufferedOutputStream(outputStream)) {

            if (saveIvInFile && iv != null) {
                outputStream.write(iv.getIV());
            }

            if (iv == null) {
                // Für das Verschlüsseln muss jetzt immer ein IV mitgegeben werden (SME-04-009)
                throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED);
            }

            Cipher cipher = getAESCipher(Cipher.ENCRYPT_MODE, key, iv);

            try (CipherOutputStream cipherOut = new CipherOutputStream(bufferedFileOutputStream, cipher);
                 FileInputStream inputStream = new FileInputStream(decryptedFile);
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
                eu.ginlo_apps.ginlo.util.StreamUtil.copyStreams(bufferedInputStream, cipherOut);
            }
        } catch (FileNotFoundException e) {
            throw new LocalizedException(LocalizedException.LOAD_FILE_FAILED, e);
        } catch (Throwable t) {
            LogUtil.w(SecurityUtil.class.getSimpleName(), t.getMessage(), t);
            throw new LocalizedException(LocalizedException.ENCRYPT_DATA_FAILED, t);
        }
    }

    private static Cipher getAESCipher(int mode, Key key, IvParameterSpec iv) throws LocalizedException {
        Cipher cipher;

        try {
            cipher = (iv == null) ? Cipher.getInstance(AES_CIPHER_ALGORITHM_NO_CBC)
                    : Cipher.getInstance(AES_CIPHER_ALGORITHM);
            cipher.init(mode, key, iv);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_CHIPHER_FAILED, e);
        }

        return cipher;
    }

    private static Cipher getRSACipher(int mode,
                                       Key key)
            throws LocalizedException {
        Cipher cipher;

        try {
            cipher = Cipher.getInstance(RSA_CIPHER_ALGORITHM);
            cipher.init(mode, key);
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_CHIPHER_FAILED, e);
        }

        return cipher;
    }

    public static byte[] generateSalt()
            throws LocalizedException {
        byte[] salt = new byte[8];

        SecureRandom saltGen = getSecureRandomInstance();

        saltGen.nextBytes(salt);

        return salt;
    }

    public static IvParameterSpec generateIV()
            throws LocalizedException {
        byte[] randomBytes = new byte[IV_LENGTH / 8];

        getSecureRandomInstance().nextBytes(randomBytes);

        return new IvParameterSpec(randomBytes);
    }

    public static IvParameterSpec getIvFromBytes(final byte[] bytes) {
        return new IvParameterSpec(bytes);
    }

    public static String getBase64StringFromIV(@NonNull IvParameterSpec iv) {
        byte[] ivBytes = iv.getIV();
        return Base64.encodeToString(ivBytes, Base64.NO_WRAP);
    }

    public static SecretKey getAESKeyFromBase64String(@NonNull String base64String)
            throws LocalizedException {
        try {
            byte[] aesKeyBytes = Base64.decode(base64String, Base64.NO_WRAP);
            return SecurityUtil.generateAESKey(aesKeyBytes);
        } catch (IllegalStateException e) {
            throw new LocalizedException(LocalizedException.BASE64_FAILED, e);
        }
    }

    public static String getBase64StringFromAESKey(@NonNull SecretKey aesKey) {
        byte[] aesKeyBytes = aesKey.getEncoded();
        return Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP);
    }

    public static byte[] getBase64BytesFromAESKey(@NonNull SecretKey aesKey) {
        byte[] aesKeyBytes = aesKey.getEncoded();
        return Base64.encode(aesKeyBytes, Base64.NO_WRAP);
    }

    public static SecretKey generateAESKey()
            throws LocalizedException {
        return generateAESKey(null);
    }

    public static SecretKey generateAESKey(byte[] keyBytes)
            throws LocalizedException {
        SecureRandom random;
        KeyGenerator keyGen;
        SecretKey key;

        try {
            if (keyBytes == null) {
                random = getSecureRandomInstance();
                keyGen = KeyGenerator.getInstance(AES_GEN_ALGORITHM);
                keyGen.init(AES_KEY_LENGTH, random);
                key = keyGen.generateKey();
            } else {
                key = new SecretKeySpec(keyBytes, 0, keyBytes.length, AES_GEN_ALGORITHM);
            }
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_IV_FAILED, e);
        }

        return key;
    }

    public static SecretKey xorAESKeys(SecretKey aesKey1,
                                       SecretKey aesKey2)
            throws LocalizedException {
        byte[] keyBytes1 = aesKey1.getEncoded();
        byte[] keyBytes2 = aesKey2.getEncoded();

        if (keyBytes1.length != keyBytes2.length) {
            throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED, "Key Length not equal!");
        }
        byte[] combinedKeyBytes = new byte[keyBytes1.length];

        for (int i = 0; i < combinedKeyBytes.length; i++) {
            combinedKeyBytes[i] = (byte) (keyBytes1[i] ^ keyBytes2[i]);
        }

        return new SecretKeySpec(combinedKeyBytes, 0, combinedKeyBytes.length, AES_GEN_ALGORITHM);
    }

    public static SecretKey deriveKeyFromPasswordOnSameThread(final String password,
                                                              final byte[] salt,
                                                              final int deriveIterations)
            throws LocalizedException {
        SecretKeyFactory factory;

        try {
            factory = SecretKeyFactory.getInstance(DERIVE_ALGORITHM);

            byte[] usedSalt;

            if (salt == null) {
                usedSalt = generateSalt();
            } else {
                usedSalt = salt.clone();
            }

            int rounds = deriveIterations == -1 ? RuntimeConfig.getDeriveIterations() : deriveIterations;

            KeySpec keyspec = new PBEKeySpec(password.toCharArray(), usedSalt, rounds,
                    AES_KEY_LENGTH);

            return factory.generateSecret(keyspec);
        } catch (Exception e) {
            LogUtil.w(SecurityUtil.class.getName(), e.getMessage(), e);

            throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED, "SecurityUtil.deriveKeyFromPasswordOnSameThread()", e);
        }
    }

    public static void deriveKeyFromPassword(final String password,
                                             final byte[] salt,
                                             final OnDeriveKeyCompleteListener listener
    ) {
        deriveKeyFromPassword(password, salt, listener, RuntimeConfig.getDeriveIterations(), DERIVE_ALGORITHM, true);
    }

    public static void deriveKeyFromPassword(final String password,
                                             final byte[] salt,
                                             final OnDeriveKeyCompleteListener listener,
                                             final int iterations,
                                             final String deriveAlgorythm,
                                             final boolean runAsync) {
        if (runAsync) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        deriveKeyFromPasswordInternally(password, salt, listener, iterations, deriveAlgorythm);
                    } catch (LocalizedException e) {
                        if (listener != null) {
                            listener.onError();
                        }
                    }
                }
            };

            Thread thread = new Thread(runnable);

            thread.start();
        } else {
            try {
                deriveKeyFromPasswordInternally(password, salt, listener, iterations, deriveAlgorythm);
            } catch (LocalizedException e) {
                if (listener != null) {
                    listener.onError();
                }
            }
        }
    }

    public static SecretKey deriveKeyFromPassword(final String password,
                                                  final byte[] salt,
                                                  final int iterations,
                                                  final String deriveAlgorythm)
            throws LocalizedException {
        return deriveKeyFromPasswordInternally(password, salt, null, iterations, deriveAlgorythm);
    }

    private static SecretKey deriveKeyFromPasswordInternally(final String password,
                                                             final byte[] salt,
                                                             final OnDeriveKeyCompleteListener listener,
                                                             final int iterations,
                                                             final String deriveAlgorythm)
            throws LocalizedException {
        SecretKeyFactory factory;
        SecretKey key;

        try {

            byte[] usedSalt;

            if (salt == null) {
                usedSalt = generateSalt();
            } else {
                usedSalt = salt.clone();
            }

            if (eu.ginlo_apps.ginlo.util.StringUtil.isEqual(deriveAlgorythm, DERIVE_ALGORITHM_SHA_256)) {
                // Generate a 128-bit key
                final int outputKeyLength = 256;
                PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SHA256Digest());
                generator.init(password.getBytes(), usedSalt, iterations);
                KeyParameter keyParam = (KeyParameter) generator.generateDerivedMacParameters(outputKeyLength);
                key = new SecretKeySpec(keyParam.getKey(), "AES");
            } else {
                factory = SecretKeyFactory.getInstance(deriveAlgorythm);
                KeySpec keyspec = new PBEKeySpec(password.toCharArray(), usedSalt, iterations,
                        AES_KEY_LENGTH);

                key = factory.generateSecret(keyspec);
            }

            if (listener != null) {
                listener.onComplete(key, usedSalt);
            }

            return key;
        } catch (Exception e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);

            throw new LocalizedException(LocalizedException.DERIVE_KEY_FAILED, e.getMessage(), e);
        }
    }

    public static SecretKey deriveCompanyAesKey(@NonNull final String password, @NonNull final String saltBase64, @Nullable final String diffAesBase64)
            throws LocalizedException {
        try {
            byte[] saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP);
            byte[] usedSalt = saltBytes.clone();

            byte[] passwordBytes = password.getBytes();

            final int outputKeyLength = 256;
            PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SHA256Digest());
            generator.init(passwordBytes, usedSalt, ROUNDS_ADMIN_CONSOLE);

            KeyParameter keyParam = (KeyParameter) generator.generateDerivedMacParameters(outputKeyLength);
            SecretKey key = new SecretKeySpec(keyParam.getKey(), "AES");

            if (!StringUtil.isNullOrEmpty(diffAesBase64)) {
                SecretKey diffKey = getAESKeyFromBase64String(diffAesBase64);
                return xorAESKeys(key, diffKey);
            }

            return key;
        } catch (Exception e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);

            throw new LocalizedException(LocalizedException.DERIVE_KEY_FAILED, e.getMessage(), e);
        }
    }

    @SuppressWarnings("deprecation")
    // TODO:Update to non deprecated version
    public static X509Certificate generateCertificate(KeyPair keyPair)
            throws LocalizedException {
        Date startDate = new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000));
        Date endDate = new Date(System.currentTimeMillis() + (100 * 365 * 24 * 60 * 60 * 1000));
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

        try {
            certGen.setSerialNumber(BigInteger.valueOf(1));
            certGen.setSubjectDN(new X509Principal(CN_LOCALHOST));
            certGen.setIssuerDN(new X509Principal(CN_LOCALHOST));
            certGen.setPublicKey(keyPair.getPublic());
            certGen.setNotBefore(startDate);
            certGen.setNotAfter(endDate);
            certGen.setSignatureAlgorithm(SIGNATURE_ALGORITHM);

            PrivateKey signingKey = keyPair.getPrivate();

            return certGen.generate(signingKey);
        } catch (final CertificateEncodingException | InvalidKeyException | IllegalStateException | NoSuchAlgorithmException | SignatureException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_CERTIFICATE_FAILED, e);
        }
    }

    public static void generateRSAKeyPair(OnGenerateRSAKeyPairCompleteListener listener) {
        final OnGenerateRSAKeyPairCompleteListener listenerRef = listener;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                KeyPair keyPair;

                try {
                    keyPair = generateRSAKeyPair();
                    listenerRef.onComplete(keyPair);
                } catch (LocalizedException e) {
                    LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
                    listenerRef.onError(e);
                }
            }
        };

        Thread thread = new Thread(runnable);

        thread.start();
    }

    public static KeyPair generateRSAKeyPair()
            throws LocalizedException {
        KeyPair keyPair;

        try {
            SecureRandom random = getSecureRandomInstance();
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_GEN_ALGORITHM);

            keyPairGenerator.initialize(RSA_KEY_LENGTH, random);

            keyPair = keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GENERATE_KEY_PAIR_FAILED, e);
        }

        return keyPair;
    }

    public static byte[] signData(PrivateKey key, byte[] data, boolean useSha256) throws LocalizedException {
        try {
            Signature signature;
            if (useSha256) {
                signature = Signature.getInstance(SIGNATURE_INSTANCE_SHA256);
            } else {
                signature = Signature.getInstance(SIGNATURE_INSTANCE);
            }

            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.SIGN_DATA_FAILED, e);
        }
    }

    public static String signDataAndEncodeToBase64String(PrivateKey key,
                                                         byte[] data,
                                                         boolean useSha256)
            throws LocalizedException {
        final byte[] signedData = signData(key, data, useSha256);
        return Base64.encodeToString(signedData, Base64.NO_WRAP);
    }

    public static boolean verifyData(PublicKey key, byte[] signatureData, byte[] data, boolean useSha256) throws LocalizedException {
        if (key == null) {
            return false;
        }

        try {
            Signature signature;
            if (useSha256) {
                signature = Signature.getInstance(SIGNATURE_INSTANCE_SHA256);
            } else {
                signature = Signature.getInstance(SIGNATURE_INSTANCE);
            }

            signature.initVerify(key);
            signature.update(data);
            return signature.verify(signatureData);
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException e) {
            LogUtil.e(SecurityUtil.class.getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.VERIFY_DATA_FAILED, e);
        }
    }

    public static byte[] generateRandomData(int numBytes) throws LocalizedException {
        byte[] randomBytes = new byte[numBytes];
        SecureRandom random = getSecureRandomInstance();

        random.nextBytes(randomBytes);

        return randomBytes;
    }

    public static KeyStore getKeyStoreInstance(String protocol) throws KeyStoreException {
        return KeyStore.getInstance(protocol);
    }

    static MessageDigest getMessageDigestInstance(String protocol) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(protocol);
    }

    static KeyFactory getKeyFactoryInstance(String protocol) throws NoSuchAlgorithmException {
        return KeyFactory.getInstance(protocol);
    }

    public static String readKeyFromDisc(final SecretKey aesKey, final Context context) throws IOException, LocalizedException {
        try (final BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(context.openFileInput(PW_RECOVERY_FILE_NAME)))) {

            final StringBuilder readKey = new StringBuilder();
            do {
                String temp = inputStreamReader.readLine();
                if (temp == null) {
                    break;
                }
                if (readKey.length() > 0) {
                    readKey.append("\n");
                }
                readKey.append(temp);
            } while (true);

            String encodedEncryptedKey = readKey.toString();
            // Neue Syntax ?
            if (encodedEncryptedKey.startsWith("{")) {
                JsonObject dataObject = eu.ginlo_apps.ginlo.util.JsonUtil.getJsonObjectFromString(encodedEncryptedKey);
                // Wurde der Schlüssel nochmals Geräteabhängig verschlüsselt ?
                if (dataObject != null) {

                    if (dataObject.has("innerKey")) {
                        JsonObject innerKey = dataObject.getAsJsonObject("innerKey");
                        try {
                            byte[] decryptedKey = decryptWithAndroidKeystore(innerKey.get("data").getAsString(), innerKey.get("iv").getAsString(), KeyController.RECOVERY_KEY_ALIAS);
                            dataObject = eu.ginlo_apps.ginlo.util.JsonUtil.getJsonObjectFromString(new String(decryptedKey, StandardCharsets.UTF_8));
                        } catch (KeyStoreException | NoSuchPaddingException | NoSuchAlgorithmException | UnrecoverableEntryException |
                                InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException |
                                IOException | CertificateException e) {
                            throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED, e);
                        }
                    }
                    final JsonElement ivJson = dataObject.get("iv");
                    final JsonElement dataJson = dataObject.get("data");
                    if (ivJson != null && dataJson != null) {
                        final String ivData = ivJson.getAsString();
                        final IvParameterSpec iv = getIvFromBytes(Base64.decode(ivData, Base64.NO_WRAP));
                        encodedEncryptedKey = dataJson.getAsString();
                        return decryptBase64StringWithAES(encodedEncryptedKey, aesKey, iv);
                    }
                }
            }

            // wenn try inputStreamReader fehlschlägt
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED);
        }
    }

    public static void deleteKeyFromDisc(final Context context) {
        context.deleteFile(PW_RECOVERY_FILE_NAME);
    }

    public static void writeKeyToDisc(final AESKeyContainer keyContainer, final PrivateKey devicePrivateKey, final Context context)
            throws LocalizedException {

        try (final BufferedWriter outputStreamWriter = new BufferedWriter(new OutputStreamWriter(context.openFileOutput(PW_RECOVERY_FILE_NAME, Context.MODE_PRIVATE)))) {

            final String keyXml = XMLUtil.getXMLFromPrivateKey(devicePrivateKey);
            final byte[] encryptedKeyBytes = SecurityUtil.encryptMessageWithAES(keyXml.getBytes(), keyContainer.getKey(), keyContainer.getIv());
            final String base64Key = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP);
            final String ivData = Base64.encodeToString(keyContainer.getIv().getIV(), Base64.NO_WRAP);
            JsonObject dataObject = new JsonObject();
            dataObject.addProperty("data", base64Key);
            dataObject.addProperty("iv", ivData);
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                JsonObject innerKey = encryptWithAndroidKeystore(dataObject.toString().getBytes(StandardCharsets.UTF_8), KeyController.RECOVERY_KEY_ALIAS);
                dataObject = new JsonObject();
                dataObject.add("innerKey", innerKey);
            }

            outputStreamWriter.write(dataObject.toString());
        } catch (KeyStoreException | NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException |
                IOException | CertificateException e) {
            throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED, e);
        }
    }

    public static byte[] readNotificationKeyFromDisc(final Context context)
            throws IOException, LocalizedException {
        try (final BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(context.openFileInput(NOTIFICATION_PREVIEW_FILE_NAME)))) {

            final StringBuilder readData = new StringBuilder();
            do {
                final String temp = inputStreamReader.readLine();
                if (temp == null) {
                    break;
                }
                if (readData.length() > 0) {
                    readData.append("\n");
                }
                readData.append(temp);
            } while (true);

            final String readDataString = readData.toString();

            final JsonObject dataJson = eu.ginlo_apps.ginlo.util.JsonUtil.getJsonObjectFromString(readDataString);
            if (dataJson != null && dataJson.has("data") && dataJson.has("iv")) {
                try {
                    final String encodedIv = dataJson.get("iv").getAsString();
                    final String encodedEncryptedKey = dataJson.get("data").getAsString();

                    return decryptWithAndroidKeystore(encodedEncryptedKey, encodedIv, KeyController.NOTIFICATION_KEY_ALIAS);
                } catch (KeyStoreException | NoSuchPaddingException | NoSuchAlgorithmException | UnrecoverableEntryException |
                        InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException |
                        IOException | CertificateException e) {
                    throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED, e);
                }
            }
        }
        // wenn try inputStreamReader fehlschlaegt
        throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED);
    }

    public static void writeNotificationKeyToDisc(final SecretKey secretKey, final Context context)
            throws LocalizedException {
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            return;
        }

        try (BufferedWriter outputStreamWriter = new BufferedWriter(new OutputStreamWriter(context.openFileOutput(NOTIFICATION_PREVIEW_FILE_NAME, Context.MODE_PRIVATE)))) {
            final JsonObject encryptedKey = encryptWithAndroidKeystore(secretKey.getEncoded(), KeyController.NOTIFICATION_KEY_ALIAS);
            outputStreamWriter.write(encryptedKey.toString());
        } catch (KeyStoreException | NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException |
                IOException | CertificateException e) {
            throw new LocalizedException(LocalizedException.GENERATE_AES_KEY_FAILED, e);
        }
    }

    public static void deleteNotificationKeyFromDisc(final Context context) {
        context.deleteFile(NOTIFICATION_PREVIEW_FILE_NAME);
    }

    private static JsonObject encryptWithAndroidKeystore(final byte[] data, final String alias)
            throws KeyStoreException, NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            IOException, CertificateException {
        final KeyStore keystoreAndroid = KeyStore.getInstance("AndroidKeyStore");
        keystoreAndroid.load(null);

        // neuen key anlegen
        // Alles mit dem Cryptoprovider von Adnroid, nicht von BouncyCastle, sonst geht das nicht
        final KeyGenerator keyGenerator = KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        final KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build();

        keyGenerator.init(keyGenParameterSpec);
        final SecretKey secretKey = keyGenerator.generateKey();

        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        final byte[] iv = cipher.getIV();

        final byte[] encrypted = cipher.doFinal(data);
        final String encryptedString = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        final String encodedIV = Base64.encodeToString(iv, Base64.NO_WRAP);

        final JsonObject dataObject = new JsonObject();
        dataObject.addProperty("data", encryptedString);
        dataObject.addProperty("iv", encodedIV);

        return dataObject;
    }

    private static byte[] decryptWithAndroidKeystore(String encryptedString, String ivString, String alias)
            throws KeyStoreException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            IOException, CertificateException, UnrecoverableEntryException {
        final KeyStore keystoreAndroid = KeyStore.getInstance("AndroidKeyStore");
        keystoreAndroid.load(null);

        KeyStore.SecretKeyEntry kse = (KeyStore.SecretKeyEntry) keystoreAndroid.getEntry(alias, null);
        if (kse == null) {
            throw new UnrecoverableEntryException();
        }
        SecretKey k = kse.getSecretKey();
        if (k == null) {
            throw new UnrecoverableEntryException();
        }

        final Cipher cipherDecode = Cipher.getInstance("AES/GCM/NoPadding");
        cipherDecode.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, Base64.decode(ivString, Base64.NO_WRAP)));

        return cipherDecode.doFinal(Base64.decode(encryptedString, Base64.NO_WRAP));
    }

    private static SecretKey createKeyForBiometricAuthPre28()
            throws LocalizedException {
        try {
            final KeyGenerator keyGenerator = KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KeyController.DEVICE_MASTER_KEY_BIOMETRIC,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE);

            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)) {
                builder.setInvalidatedByBiometricEnrollment(true);
            }

            final KeyGenParameterSpec keyGenParameterSpec = builder.build();

            keyGenerator.init(keyGenParameterSpec);

            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new LocalizedException(LocalizedException.ANDROID_KEY_STORE_ACTION_FAILED, "createKeyForBiometricAuth", e);
        }
    }

    @NonNull
    public static JsonObject encryptDataWithBiometricKeyCipher(final byte[] data, @NonNull final Cipher encryptCipher)
            throws LocalizedException {
        try {
            final byte[] iv = encryptCipher.getIV();
            final byte[] encrypted = encryptCipher.doFinal(data);

            final String encryptedString = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            final String encodedIV = Base64.encodeToString(iv, Base64.NO_WRAP);

            final JsonObject dataObject = new JsonObject();
            dataObject.addProperty("data", encryptedString);
            dataObject.addProperty("iv", encodedIV);

            return dataObject;
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new LocalizedException(LocalizedException.ENCRYPT_DATA_FAILED, e);
        }
    }

    public static Cipher getEncryptCipherForBiometricAuthKey(final boolean createKeyIfNotExist) throws LocalizedException {
        try {
            SecretKey key = getBiometricAuthKey();

            if (key == null) {
                if (createKeyIfNotExist) {
                    key = createKeyForBiometricAuthPre28();
                } else {
                    throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "key == null");
                }
            }

            Cipher cipher = getAESCipherNoBC();

            cipher.init(Cipher.ENCRYPT_MODE, key);

            return cipher;
        } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | InvalidKeyException | NoSuchPaddingException | KeyStoreException e) {
            if (createKeyIfNotExist && e instanceof KeyPermanentlyInvalidatedException) {
                deleteKeyForBiometricAuth();
                return getEncryptCipherForBiometricAuthKey(true);
            }

            throw new LocalizedException(LocalizedException.ANDROID_KEY_STORE_ACTION_FAILED, "getCipherForBiometricAuthKey()", e);
        }
    }

    public static Cipher getDecryptCipherForBiometricAuthKey(@NonNull final String ivString)
            throws LocalizedException {
        try {
            SecretKey key = getBiometricAuthKey();

            if (key == null) {
                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "key == null");
            }

            Cipher cipher = getAESCipherNoBC();

            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(ivString, Base64.NO_WRAP)));

            return cipher;
        } catch (IOException | CertificateException | NoSuchAlgorithmException |
                UnrecoverableKeyException | InvalidKeyException | NoSuchPaddingException |
                KeyStoreException | InvalidAlgorithmParameterException e) {
            if ("Key permanently invalidated".equals(e.getMessage())) {
                throw new LocalizedException(LocalizedException.ANDROID_BIOMETRIC_KEY_INVALIDATED, "Key permanently invalidated()", e);
            } else {
                throw new LocalizedException(LocalizedException.ANDROID_KEY_STORE_ACTION_FAILED, "getCipherForBiometricAuthKey()", e);
            }
        }
    }

    private static SecretKey getBiometricAuthKey()
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        KeyStore androidKeyStore = getAndroidKeystore();

        return (SecretKey) androidKeyStore.getKey(KeyController.DEVICE_MASTER_KEY_BIOMETRIC, null);
    }

    private static Cipher getAESCipherNoBC()
            throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_GCM + "/"
                + KeyProperties.ENCRYPTION_PADDING_NONE);
    }

    public static void deleteKeyForBiometricAuth()
            throws LocalizedException {
        try {
            KeyStore androidKeyStore = getAndroidKeystore();

            androidKeyStore.deleteEntry(KeyController.DEVICE_MASTER_KEY_BIOMETRIC);
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
            throw new LocalizedException(LocalizedException.ANDROID_KEY_STORE_ACTION_FAILED, "deleteKeyForBiometricAuth()", e);
        }
    }

    private static KeyStore getAndroidKeystore()
            throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        KeyStore androidKeyStore = KeyStore.getInstance("AndroidKeyStore");
        androidKeyStore.load(null);

        return androidKeyStore;
    }

    @NonNull
    public static byte[] getBytesFromKeyStore(@NonNull final KeyStore keyStore, @NonNull final char[] keystorePass, @NonNull final String alias)
            throws LocalizedException {
        final SecretKey key;
        try {
            key = (SecretKey) keyStore.getKey(alias, keystorePass);
            if (key == null) {
                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "key == null");
            }
            return key.getEncoded();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new LocalizedException(LocalizedException.ANDROID_KEY_STORE_ACTION_FAILED);
        }
    }

    public static KeyStore loadKeyStore(@NonNull final Context context, @NonNull final String keyStoreName)
            throws LocalizedException {
        try {
            File keyStoreFileAes = context.getFileStreamPath(keyStoreName + ".aes");
            byte[] keyStoreContent = null;
            if (keyStoreFileAes.exists()) {
                //
                keyStoreContent = loadKeyStoreAndroidKeyStore(keyStoreFileAes);
            }
            if (keyStoreContent == null) {
                File keyStoreFile = context.getFileStreamPath(keyStoreName);

                if (!keyStoreFile.exists()) {
                    return null;
                }

                try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(keyStoreFile));
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    eu.ginlo_apps.ginlo.util.StreamUtil.copyStreams(inputStream, baos);
                    keyStoreContent = baos.toByteArray();
                }
            }

            final KeyStore keystore;

            keystore = SecurityUtil.getKeyStoreInstance("UBER");

            ByteArrayInputStream bis = new ByteArrayInputStream(keyStoreContent);
            keystore.load(bis, AppConstants.getKeystorePass());

            return keystore;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableEntryException |
                NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new LocalizedException(LocalizedException.ANDROID_KEY_STORE_ACTION_FAILED, "loadKeyStore()", e);
        }
    }

    private static byte[] loadKeyStoreAndroidKeyStore(File file)
            throws UnrecoverableEntryException, IOException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, LocalizedException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            eu.ginlo_apps.ginlo.util.StreamUtil.copyStreams(inputStream, baos);

            String fileContent = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            JsonObject fo = JsonUtil.getJsonObjectFromString(fileContent);
            if (fo == null) {
                throw new LocalizedException(JSON_OBJECT_NULL);
            }
            String ivString = fo.get("iv").getAsString();
            String encryptedString = fo.get("key").getAsString();

            return decryptWithAndroidKeystore(encryptedString, ivString, KeyController.DEVICE_MASTER_KEY);
        }
    }

    public static void saveKeystore(@NonNull final Context context, @NonNull final String keyStoreName, @NonNull final char[] keystorePass, @NonNull final KeyStore keystore)
            throws LocalizedException {
        ByteArrayOutputStream outputStream = null;

        try {
            outputStream = new ByteArrayOutputStream();
            keystore.store(outputStream, keystorePass);

            byte[] keystoreData = outputStream.toByteArray();

            //Ticket SIMSME-5110 - Unter HUAWEI - Android 6 gibt es probleme speichern des KeyStores (Absturz beim registrieren)
            if (!Build.DEVICE.equals("generic_x86") && (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && !Build.MANUFACTURER.equals("HUAWEI")) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N))) {
                saveKeysAndroidKeyStore(keyStoreName, keystoreData, context);
                deleteKeyFile(context, keyStoreName);
            } else {
                saveKeysFileSystem(keyStoreName, keystoreData, context);
            }
        } catch (CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException e) {
            throw new LocalizedException(LocalizedException.SAVE_DATA_FAILED, "saveKeystore", e);
        } finally {
            eu.ginlo_apps.ginlo.util.StreamUtil.closeStream(outputStream);
        }
    }

    private static void deleteKeyFile(Context context, String fileName) {
        File keyFile = context.getFileStreamPath(fileName);

        if (keyFile.exists()) {
            keyFile.delete();
        }
    }

    private static void saveKeysAndroidKeyStore(@NonNull final String keyStoreName,
                                                @NonNull final byte[] keystoreData,
                                                @NonNull final Context context)
            throws LocalizedException {
        try {
            JsonObject data = SecurityUtil.encryptWithAndroidKeystore(keystoreData, KeyController.DEVICE_MASTER_KEY);
            saveKeyFile(context, keyStoreName + ".aes", data.get("data").getAsString(), data.get("iv").getAsString());
        } catch (final Throwable e) {
            throw new LocalizedException(LocalizedException.SAVE_DATA_FAILED, "saveKeysAndroidKeyStore", e);
        }
    }

    private static void saveKeyFile(Context context, String filename, String keyData, String ivData)
            throws IOException {
        BufferedOutputStream outputStream = null;

        try {
            JsonObject keyObject = new JsonObject();
            keyObject.addProperty("key", keyData);
            keyObject.addProperty("iv", ivData);

            byte[] keyBytes = keyObject.toString().getBytes(StandardCharsets.UTF_8);
            File keyFile = context.getFileStreamPath(filename);

            if (keyFile.exists()) {
                keyFile.delete();
            }

            outputStream = new BufferedOutputStream(new FileOutputStream(keyFile));
            outputStream.write(keyBytes);
        } finally {
            eu.ginlo_apps.ginlo.util.StreamUtil.closeStream(outputStream);
        }
    }

    private static void saveKeysFileSystem(@NonNull final String keyStoreName, @NonNull final byte[] keystoreData, @NonNull final Context context)
            throws LocalizedException {
        BufferedOutputStream outputStream = null;

        try {
            File keyStoreFile = context.getFileStreamPath(keyStoreName);

            if (keyStoreFile.exists()) {
                keyStoreFile.delete();
            }

            outputStream = new BufferedOutputStream(new FileOutputStream(keyStoreFile));

            outputStream.write(keystoreData);
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.SAVE_DATA_FAILED, "saveKeysFileSystem", e);
        } finally {
            StreamUtil.closeStream(outputStream);
        }
    }

    public interface OnDeriveKeyCompleteListener {
        void onComplete(SecretKey key, byte[] usedSalt);

        void onError();
    }

    public interface OnGenerateRSAKeyPairCompleteListener {
        void onComplete(KeyPair keyPair);

        void onError(LocalizedException e);
    }

    public static class AESKeyContainer {
        final SecretKey mKey;
        final IvParameterSpec mIv;

        public AESKeyContainer(SecretKey key, IvParameterSpec iv) {
            mKey = key;
            mIv = iv;
        }

        public SecretKey getKey() {
            return mKey;
        }

        public IvParameterSpec getIv() {
            return mIv;
        }
    }
}
