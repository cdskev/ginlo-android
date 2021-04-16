// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.content.Context;
import android.util.Base64;
import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil.OnDeriveKeyCompleteListener;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class SaveKeysTask
        extends ConcurrentTask {

    private static final String NO_PASS_KEY_FILE = "noPassKey";

    private static final int MODE_BIOMETRIC = 1;

    private static final int MODE_OTHER = 2;

    private final KeyPair mDeviceKeyPair;
    private final SimsMeApplication mApplication;
    private final String mKeyStoreName;
    private final int mMode;
    private SecretKey mInternalEncryptionKey;
    private String mPassword;
    private boolean mUsePassword;
    private Cipher mEncryptCipher;

    public SaveKeysTask(String keyStoreName,
                        boolean usePassword,
                        KeyPair deviceKeyPair,
                        SecretKey internalEncryptionKey,
                        String password,
                        SimsMeApplication application) {
        super();
        this.mKeyStoreName = keyStoreName;
        this.mUsePassword = usePassword;
        this.mDeviceKeyPair = deviceKeyPair;
        this.mInternalEncryptionKey = internalEncryptionKey;
        this.mPassword = password;
        this.mApplication = application;
        this.mMode = MODE_OTHER;
    }

    /**
     * Use for Biometric Encryption
     */
    public SaveKeysTask(SimsMeApplication application,
                        String keyStoreName,
                        KeyPair deviceKeyPair,
                        Cipher encryptBiometricCipher) {
        super();
        this.mKeyStoreName = keyStoreName;
        this.mDeviceKeyPair = deviceKeyPair;
        this.mApplication = application;
        this.mEncryptCipher = encryptBiometricCipher;
        this.mMode = MODE_BIOMETRIC;
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
            StreamUtil.closeStream(outputStream);
        }
    }

    private static KeyStore saveKeysMemory(
            final SimsMeApplication application,
            final KeyPair deviceKeyPair,
            final SecretKey internalEncryptionKey,
            final boolean usePassword,
            final SecretKey xorKey,
            final SecretKey derivedKey,
            final byte[] usedSalt)
            throws LocalizedException, NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException {
        KeyStore keystore = application.getKeyController().getKeyStore();

        final char[] keystorePass = AppConstants.getKeystorePass();

        if (keystore == null) {
            keystore = SecurityUtil.getKeyStoreInstance("UBER");
            keystore.load(null, keystorePass);
        }

        SecretKey combinedKey = SecurityUtil.xorAESKeys(derivedKey, xorKey);
        IvParameterSpec ivParameterSpec = SecurityUtil.generateIV();

        X509Certificate deviceCert = SecurityUtil.generateCertificate(deviceKeyPair);

        Certificate[] deviceCertChain = new Certificate[]{deviceCert};

        String devicePrivateKeyXML = XMLUtil.getXMLFromPrivateKey(deviceKeyPair.getPrivate());
        byte[] devicePrivateKeyXMLBytes = devicePrivateKeyXML.getBytes(StandardCharsets.UTF_8);
        byte[] devicePrivateKeyXMLBytesEncrypted = SecurityUtil.encryptMessageWithAES(devicePrivateKeyXMLBytes,
                combinedKey, ivParameterSpec);

        byte[] internalEncryptionKeyBytes = internalEncryptionKey.getEncoded();
        byte[] encryptedInternalEncryptionKeyBytes = SecurityUtil.encryptMessageWithRSA(internalEncryptionKeyBytes,
                deviceKeyPair
                        .getPublic());

        SecretKey devicePrivateKeyContainer = SecurityUtil.generateAESKey(devicePrivateKeyXMLBytesEncrypted);
        SecretKey saltContainer = SecurityUtil.generateAESKey(usedSalt);
        SecretKey internalEncryptionKeyContainer = SecurityUtil.generateAESKey(encryptedInternalEncryptionKeyBytes);
        SecretKey ivContainer = SecurityUtil.generateAESKey(ivParameterSpec.getIV());

        if (!usePassword) {
            SecretKey noPassAESKey = SecurityUtil.generateAESKey();
            IvParameterSpec iv = SecurityUtil.generateIV();

            String keyData = Base64.encodeToString(noPassAESKey.getEncoded(), Base64.NO_WRAP);
            String ivData = Base64.encodeToString(iv.getIV(), Base64.NO_WRAP);

            saveKeyFile(application, NO_PASS_KEY_FILE, keyData, ivData);

            byte[] devicePrivateKeyXMLBytesNoPassEncrypted = SecurityUtil.encryptMessageWithAES(devicePrivateKeyXMLBytes,
                    noPassAESKey, iv);
            SecretKey devicePrivateKeyContainerNoPass = SecurityUtil.generateAESKey(devicePrivateKeyXMLBytesNoPassEncrypted);

            keystore.setKeyEntry(KeyController.DEVICE_KEY_PRIVATE_NO_PASS_ALIAS, devicePrivateKeyContainerNoPass,
                    keystorePass, deviceCertChain);
        }

        keystore.setKeyEntry(KeyController.SALT_ALIAS, saltContainer, keystorePass, null);
        keystore.setKeyEntry(KeyController.IV_ALIAS, ivContainer, keystorePass, null);
        keystore.setKeyEntry(KeyController.XOR_KEY_ALIAS, xorKey, keystorePass, null);
        keystore.setKeyEntry(KeyController.DEVICE_KEY_PRIVATE_ALIAS, devicePrivateKeyContainer,
                keystorePass, deviceCertChain);
        keystore.setKeyEntry(KeyController.INTERNAL_ENCRYPTION_KEY_ALIAS, internalEncryptionKeyContainer,
                keystorePass, deviceCertChain);
        keystore.setKeyEntry(KeyController.DEVICE_KEY_PUBLIC_ALIAS, deviceKeyPair.getPublic(),
                keystorePass, null);

        return keystore;
    }

    private static KeyStore saveBiometricKeysMemory(final KeyPair deviceKeyPair,
                                                    final Cipher encryptCipher,
                                                    final SimsMeApplication application,
                                                    final char[] keystorePass)
            throws LocalizedException {
        try {
            final KeyStore keystore = application.getKeyController().getKeyStore();

            if (keystore == null) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "keystore is null");
            }

            String devicePrivateKeyXML = XMLUtil.getXMLFromPrivateKey(deviceKeyPair.getPrivate());
            byte[] devicePrivateKeyXMLBytes = devicePrivateKeyXML.getBytes(StandardCharsets.UTF_8);

            final JsonObject jsonObject = SecurityUtil.encryptDataWithBiometricKeyCipher(devicePrivateKeyXMLBytes, encryptCipher);
            final String devicePrivateKeyXMLBytesBioEncrypted = jsonObject.get("data").getAsString();
            final String iv = jsonObject.get("iv").getAsString();

            final SecretKey devicePrivateKeyBioEncrypted = SecurityUtil.generateAESKey(devicePrivateKeyXMLBytesBioEncrypted.getBytes());
            final SecretKey ivDataBioEncrypted = SecurityUtil.generateAESKey(iv.getBytes());
            keystore.setKeyEntry(KeyController.DEVICE_MASTER_KEY_BIOMETRIC, devicePrivateKeyBioEncrypted, keystorePass, null);
            keystore.setKeyEntry(KeyController.DEVICE_MASTER_KEY_BIOMETRIC_IV, ivDataBioEncrypted, keystorePass, null);

            return keystore;
        } catch (KeyStoreException e) {
            throw new LocalizedException(LocalizedException.ENCRYPT_DATA_FAILED, "saveBiometricKeysMemory", e);
        }
    }

    @Override
    public void run() {
        try {
            super.run();

            switch (mMode) {
                case MODE_BIOMETRIC: {
                    final char[] keystorePass = AppConstants.getKeystorePass();
                    final KeyStore keystore = saveBiometricKeysMemory(mDeviceKeyPair, mEncryptCipher, mApplication, keystorePass);
                    SecurityUtil.saveKeystore(mApplication, mKeyStoreName, keystorePass, keystore);
                    break;
                }
                case MODE_OTHER:
                default: {
                    final SecretKey xorKey = SecurityUtil.generateAESKey();
                    final CountDownLatch latch = new CountDownLatch(1);

                    OnDeriveKeyCompleteListener onDeriveKeyCompleteListener = new OnDeriveKeyCompleteListener() {
                        @Override
                        public void onComplete(SecretKey derivedKey,
                                               byte[] usedSalt) {
                            try {
                                saveKeys(xorKey, derivedKey, usedSalt);
                                latch.countDown();
                            } catch (LocalizedException e) {
                                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                                error();
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onError() {
                            error();
                            latch.countDown();
                        }
                    };

                    SecurityUtil.deriveKeyFromPassword(mPassword, null, onDeriveKeyCompleteListener);

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                        error();
                        return;
                    }
                }
            }

            complete();
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            error();
        }
    }

    @Override
    public Object[] getResults() {
        return null;
    }

    private void saveKeys(final SecretKey xorKey,
                          SecretKey derivedKey,
                          byte[] usedSalt)
            throws LocalizedException {
        try {
            final char[] keystorePass = AppConstants.getKeystorePass();

            KeyStore keystore = saveKeysMemory(mApplication, mDeviceKeyPair, mInternalEncryptionKey, mUsePassword, xorKey, derivedKey, usedSalt);
            SecurityUtil.saveKeystore(mApplication, mKeyStoreName, keystorePass, keystore);
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
            e.printStackTrace();
        }
    }
}
