// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.util.Base64;
import androidx.annotation.NonNull;
import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import static eu.ginlo_apps.ginlo.exception.LocalizedException.JSON_OBJECT_NULL;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class KeysTask
        extends ConcurrentTask {

    public static final int JOB_MODE_LOAD_KEYS = 1;
    public static final int JOB_MODE_DELETE_USER_KEYPAIR = 2;
    public static final int JOB_MODE_RECOVER_KEY = 3;
    public static final int JOB_MODE_DELETE_BIOMETRIC = 4;
    public static final int JOB_MODE_LOAD_KEYS_BIOMETRIC = 5;
    public static final int JOB_MODE_LOAD_KEYS_NO_PASS = 6;
    private static final String NO_PASS_KEY_FILE = "noPassKey";

    private final SimsMeApplication mApplication;
    private final int mJob;
    private KeyPair userKeyPair;
    private KeyPair deviceKeyPair;
    private SecretKey internalEncryptionKey;
    private String password;
    private String mRecoveryDevicePrivateKeyXML;
    private Cipher mDecryptCipher;

    /**
     * Use for {@link #JOB_MODE_DELETE_BIOMETRIC}
     */
    public KeysTask(final SimsMeApplication application, final Cipher decryptCipher, int taskJob) {
        super();

        this.mJob = taskJob;
        this.mDecryptCipher = decryptCipher;
        this.mApplication = application;
    }

    /**
     * Use for {@link #JOB_MODE_LOAD_KEYS_NO_PASS}, {@link #JOB_MODE_DELETE_BIOMETRIC}, {@link #JOB_MODE_DELETE_USER_KEYPAIR}
     */
    public KeysTask(final SimsMeApplication application, int taskJob) {
        super();

        this.mJob = taskJob;
        this.mApplication = application;
    }

    /**
     * Use for {@link #JOB_MODE_LOAD_KEYS}
     */
    public KeysTask(final SimsMeApplication application, final String password, int taskJob) {
        super();

        this.mJob = taskJob;
        this.password = password;
        this.mApplication = application;
    }

    /**
     * Use for {@link #JOB_MODE_RECOVER_KEY}
     */
    public KeysTask(final SimsMeApplication application, int taskJob, final String recoveryDevicePrivateKeyXML) {
        super();

        this.mJob = taskJob;
        this.mRecoveryDevicePrivateKeyXML = recoveryDevicePrivateKeyXML;
        this.mApplication = application;
    }

    @Override
    public void run() {
        super.run();

        try {
            final KeyStore keystore = mApplication.getKeyController().getKeyStore();

            if (keystore == null) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "keystore is null");
            }

            switch (mJob) {
                case JOB_MODE_LOAD_KEYS: {
                    if (password == null) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "password is null");
                    }
                    loadKeysWithPass(keystore, password);
                    break;
                }
                case JOB_MODE_LOAD_KEYS_BIOMETRIC: {
                    if (mDecryptCipher == null) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "cipher is null");
                    }
                    loadKeysBiometric(mDecryptCipher, keystore);
                    break;
                }
                case JOB_MODE_LOAD_KEYS_NO_PASS: {
                    loadKeysNoPass(keystore);
                    break;
                }
                case JOB_MODE_DELETE_USER_KEYPAIR: {
                    deleteUserKeyPair(keystore);
                    break;
                }
                case JOB_MODE_RECOVER_KEY: {
                    if (mRecoveryDevicePrivateKeyXML == null) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "RecoverKey: recoveryKey is null");
                    }
                    loadKeysRecovery(keystore, mRecoveryDevicePrivateKeyXML);
                    break;
                }
                case JOB_MODE_DELETE_BIOMETRIC: {
                    keystore.deleteEntry(KeyController.DEVICE_MASTER_KEY_BIOMETRIC);
                    keystore.deleteEntry(KeyController.DEVICE_MASTER_KEY_BIOMETRIC_IV);
                    SecurityUtil.deleteKeyForBiometricAuth();
                }
            }
            complete();
        } catch (KeyStoreException | LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            error();
        }
    }

    @Override
    public Object[] getResults() {
        if (isError()) {
            return new Object[]{false};
        } else if (isComplete()) {
            return new Object[]
                    {
                            userKeyPair,
                            deviceKeyPair,
                            internalEncryptionKey
                    };
        }

        return null;
    }

    private SecurityUtil.AESKeyContainer loadKeyFile()
            throws LocalizedException, IOException {
        SecretKey key;
        IvParameterSpec iv = null;
        FileInputStream fileInputStream = null;

        try {
            File keyFile = mApplication.getFileStreamPath(NO_PASS_KEY_FILE);

            fileInputStream = new FileInputStream(keyFile);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamUtil.copyStreams(fileInputStream, baos);

            byte[] fileContent = baos.toByteArray();
            // Version ohne IV
            if (fileContent.length == SecurityUtil.AES_KEY_LENGTH / 8) {
                key = SecurityUtil.generateAESKey(fileContent);
            } else {
                String encodedData = new String(fileContent, StandardCharsets.UTF_8);
                JsonObject decode = JsonUtil.getJsonObjectFromString(encodedData);
                if (decode == null) {
                    throw new LocalizedException(JSON_OBJECT_NULL);
                }
                String keyString = decode.get("key").getAsString();
                key = SecurityUtil.getAESKeyFromBase64String(keyString);
                String ivString = decode.get("iv").getAsString();
                iv = SecurityUtil.getIvFromBytes(Base64.decode(ivString, Base64.NO_WRAP));
            }
        } finally {
            StreamUtil.closeStream(fileInputStream);
        }

        return new SecurityUtil.AESKeyContainer(key, iv);
    }

    private void loadKeysWithPass(@NonNull final KeyStore keyStore, @NonNull final String password)
            throws LocalizedException {
        try {

            final char[] keystorePass = AppConstants.getKeystorePass();
            byte[] saltContainerEncoded = SecurityUtil.getBytesFromKeyStore(keyStore, keystorePass, KeyController.SALT_ALIAS);

            SecretKey key = SecurityUtil.deriveKeyFromPasswordOnSameThread(password, saltContainerEncoded, -1);
            SecretKey ivContainer = (SecretKey) keyStore.getKey(KeyController.IV_ALIAS,
                    AppConstants.getKeystorePass());

            IvParameterSpec iv = null;
            if (ivContainer != null) {
                iv = SecurityUtil.getIvFromBytes(ivContainer.getEncoded());
            }

            SecurityUtil.AESKeyContainer passwordKey = new SecurityUtil.AESKeyContainer(key, iv);

            SecretKey xorKey = (SecretKey) keyStore.getKey(KeyController.XOR_KEY_ALIAS, keystorePass);

            SecretKey aesKey = SecurityUtil.xorAESKeys(passwordKey.getKey(), xorKey);

            byte[] devicePrivateKeyXMLBytesEncrypted = SecurityUtil.getBytesFromKeyStore(keyStore, keystorePass, KeyController.DEVICE_KEY_PRIVATE_ALIAS);

            iv = passwordKey.getIv();

            byte[] devicePrivateKeyXMLBytes = SecurityUtil.decryptMessageWithAES(devicePrivateKeyXMLBytesEncrypted, aesKey,
                    iv, false);

            decryptKeysWithDevicePrivateKeyBytes(devicePrivateKeyXMLBytes, keyStore, keystorePass);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            e.printStackTrace();
        }
    }

    private void loadKeysNoPass(@NonNull final KeyStore keyStore)
            throws LocalizedException {
        try {
            SecurityUtil.AESKeyContainer deviceAESKey = loadKeyFile();

            SecretKey aesKey = deviceAESKey.getKey();
            IvParameterSpec iv = deviceAESKey.getIv();

            final char[] keystorePass = AppConstants.getKeystorePass();
            byte[] devicePrivateKeyXMLBytesEncrypted = SecurityUtil.getBytesFromKeyStore(keyStore, keystorePass, KeyController.DEVICE_KEY_PRIVATE_NO_PASS_ALIAS);
            byte[] devicePrivateKeyXMLBytes = SecurityUtil.decryptMessageWithAES(devicePrivateKeyXMLBytesEncrypted, aesKey,
                    iv, false);

            decryptKeysWithDevicePrivateKeyBytes(devicePrivateKeyXMLBytes, keyStore, keystorePass);
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, "loadKeysNoPass", e);
        }
    }

    private void loadKeysBiometric(@NonNull final Cipher cipher, @NonNull final KeyStore keyStore)
            throws LocalizedException {
        try {
            final char[] keystorePass = AppConstants.getKeystorePass();
            final byte[] devicePrivateKeyBioEncryptedEncoded = SecurityUtil.getBytesFromKeyStore(keyStore, keystorePass, KeyController.DEVICE_MASTER_KEY_BIOMETRIC);
            final String devicePrivateKeyBioEncryptedEncodedString = new String(devicePrivateKeyBioEncryptedEncoded, StandardCharsets.UTF_8);

            byte[] devicePrivateKeyXMLBytes = cipher.doFinal(Base64.decode(devicePrivateKeyBioEncryptedEncodedString, Base64.NO_WRAP));

            if (devicePrivateKeyXMLBytes == null) {
                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Device private key bytes is null");
            }

            decryptKeysWithDevicePrivateKeyBytes(devicePrivateKeyXMLBytes, keyStore, keystorePass);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, "loadKeysBiometric()", e);
        }
    }

    private void decryptKeysWithDevicePrivateKeyBytes(@NonNull final byte[] devicePrivateKeyXMLBytes, @NonNull final KeyStore keyStore, @NonNull final char[] keystorePass)
            throws LocalizedException {
        String devicePrivateKeyXML = new String(devicePrivateKeyXMLBytes, StandardCharsets.UTF_8);
        PrivateKey devicePrivateKey = XMLUtil.getPrivateKeyFromXML(devicePrivateKeyXML);

        if (devicePrivateKey == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "device private key is null");
        }

        decryptKeysWithDevicePrivateKey(devicePrivateKey, keyStore, keystorePass);
    }

    private void decryptKeysWithDevicePrivateKey(@NonNull final PrivateKey devicePrivateKey, @NonNull final KeyStore keyStore, @NonNull final char[] keystorePass)
            throws LocalizedException {
        try {
            SecretKey userPrivateKeyContainer = (SecretKey) keyStore.getKey(KeyController.USER_KEY_PRIVATE_ALIAS, keystorePass);
            SecretKey userAESKeyContaier = (SecretKey) keyStore.getKey(KeyController.USER_KEY_AES_ALIAS, keystorePass);
            SecretKey internalEncryptionKeyContainer = (SecretKey) keyStore.getKey(KeyController.INTERNAL_ENCRYPTION_KEY_ALIAS, keystorePass);
            PublicKey devicePublicKey = (PublicKey) keyStore.getKey(KeyController.DEVICE_KEY_PUBLIC_ALIAS, keystorePass);
            PublicKey userPublicKey = (PublicKey) keyStore.getKey(KeyController.USER_KEY_PUBLIC_ALIAS, keystorePass);

            byte[] encryptedInternalEncryptionKeyBytes = internalEncryptionKeyContainer.getEncoded();
            byte[] internalEncryptionKeyBytes = SecurityUtil.decryptMessageWithRSA(encryptedInternalEncryptionKeyBytes,
                    devicePrivateKey);

            if (userPrivateKeyContainer != null && userPublicKey != null && userAESKeyContaier != null) {
                byte[] userAESKeyBytes = SecurityUtil.decryptMessageWithRSA(userAESKeyContaier
                                .getEncoded(),
                        devicePrivateKey);
                SecretKey userAESKey = SecurityUtil.generateAESKey(userAESKeyBytes);

                byte[] userPrivateKeyXMLBytesEncrypted = userPrivateKeyContainer.getEncoded();
                byte[] userPrivateKeyXMLBytes = SecurityUtil.decryptMessageWithAES(userPrivateKeyXMLBytesEncrypted,
                        userAESKey, null);
                String userPrivateKeyXML = new String(userPrivateKeyXMLBytes, StandardCharsets.UTF_8);
                PrivateKey userPrivateKey = XMLUtil.getPrivateKeyFromXML(userPrivateKeyXML);

                userKeyPair = new KeyPair(userPublicKey, userPrivateKey);
            }

            deviceKeyPair = new KeyPair(devicePublicKey, devicePrivateKey);

            internalEncryptionKey = SecurityUtil.generateAESKey(internalEncryptionKeyBytes);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new LocalizedException(LocalizedException.DECRYPT_DATA_FAILED, e);
        }
    }

    private void loadKeysRecovery(@NonNull final KeyStore keystore, @NonNull final String devicePrivateKeyXML)
            throws LocalizedException {
        final char[] keystorePass = AppConstants.getKeystorePass();
        PrivateKey devicePrivateKey = XMLUtil.getPrivateKeyFromXML(devicePrivateKeyXML);

        if (devicePrivateKey == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "device private key is null");
        }
        decryptKeysWithDevicePrivateKey(devicePrivateKey, keystore, keystorePass);
    }

    private void deleteUserKeyPair(final KeyStore keystore) throws KeyStoreException {
        keystore.deleteEntry(KeyController.USER_KEY_PRIVATE_ALIAS);
        keystore.deleteEntry(KeyController.USER_KEY_PUBLIC_ALIAS);
        keystore.deleteEntry(KeyController.USER_KEY_AES_ALIAS);
    }
}
