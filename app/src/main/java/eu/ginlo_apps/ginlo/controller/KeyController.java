// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import androidx.annotation.NonNull;
import android.util.Base64;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil.OnGenerateRSAKeyPairCompleteListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class KeyController
        implements AppLifecycleCallbacks {
    public static final String DEVICE_KEY_PRIVATE_ALIAS = "devicePriv";

    public static final String DEVICE_KEY_PRIVATE_NO_PASS_ALIAS = "devicePrivNoPass";

    public static final String USER_KEY_PRIVATE_ALIAS = "userPriv";

    public static final String USER_KEY_AES_ALIAS = "userAESPriv";

    public static final String DEVICE_KEY_PUBLIC_ALIAS = "devicePub";

    public static final String USER_KEY_PUBLIC_ALIAS = "userPub";

    public static final String INTERNAL_ENCRYPTION_KEY_ALIAS = "internalEncKey";

    public static final String XOR_KEY_ALIAS = "xorKey";

    public static final String SALT_ALIAS = "salt";

    public static final String IV_ALIAS = "iv";
    /**
     * Alias im Android Keystore für den KeyStore
     */
    public static final String DEVICE_MASTER_KEY = "MasterKey";
    /**
     * fuer bviometrische authentification
     */
    public static final String DEVICE_MASTER_KEY_BIOMETRIC = "MasterKeyBiometric_v2";
    /**
     * fuer bviometrische authentification
     */
    public static final String DEVICE_MASTER_KEY_BIOMETRIC_IV = "MasterKeyBiometricIV";
    /**
     * Alias im Android Keystore für den Recovery Key
     */
    public static final String RECOVERY_KEY_ALIAS = "RecoveryKey";
    /**
     * Alias im Android Keystore für den Notification Key
     */
    public static final String NOTIFICATION_KEY_ALIAS = "NotificationKey";
    private static final String KEYSTORE_NAME = "secrets.keystore";
    private static final String NO_PASS_KEY_FILE = "noPassKey";

    private final SimsMeApplication application;
    private KeyPair deviceKeyPair;
    private KeyPair userKeyPair;
    private SecretKey internalEncryptionKey;
    private OnKeyPairsInitiatedListener keyPairsInitiatedListener;

    public KeyController(final SimsMeApplication application) {
        this.application = application;

        this.application.getAppLifecycleController().registerAppLifecycleCallbacks(this);
    }

    boolean deleteNoPassKeyFile() {
        File keyFile = application.getFileStreamPath(NO_PASS_KEY_FILE);

        if (keyFile.exists()) {
            return keyFile.delete();
        }
        return false;
    }

    /**
     * Internal AES Key und Device Key Pair werden in Keystore gespeichert.
     * User Key Pair(Account Key Pair) wird in der DB im Account abgelegt.
     * Dafuer muss der Account in der DB angelegt sein, sonst schlaegt das Speichern fehl!
     */
    void saveKeys(final String password,
                  final OnKeysSavedListener keysSavedListener) {
        ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(ConcurrentTask task,
                                       int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    if (userKeyPair == null || application.getAccountController().saveAccountKeyPair(userKeyPair)) {
                        keysSavedListener.onKeysSaveComplete();
                    } else {
                        keysSavedListener.onKeysSaveFailed();
                    }
                } else if (state == ConcurrentTask.STATE_ERROR) {
                    keysSavedListener.onKeysSaveFailed();
                }
            }
        };
        TaskManagerController taskManagerController = application.getTaskManagerController();
        taskManagerController.getKeyTaskManager().executeSaveKeysTask(KEYSTORE_NAME,
                application.getPreferencesController().getPasswordEnabled(),
                deviceKeyPair, internalEncryptionKey,
                password, application, listener);
    }

    void saveBiometricKeys(final Cipher encryptCipher, final OnKeysSavedListener keysSavedListener) {
        ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(ConcurrentTask task,
                                       int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    keysSavedListener.onKeysSaveComplete();
                } else if (state == ConcurrentTask.STATE_ERROR) {
                    keysSavedListener.onKeysSaveFailed();
                }
            }
        };

        TaskManagerController taskManagerController = application.getTaskManagerController();
        taskManagerController.getKeyTaskManager().executeSaveBiometricKeysTask(KEYSTORE_NAME, deviceKeyPair, application, encryptCipher, listener);
    }

    void loadKeysWithRecoveryKey(final OnKeysLoadedListener keysLoadedListener, final String recoveryDevicePrivateKeyXMLs) {
        TaskManagerController taskManagerController = application.getTaskManagerController();

        taskManagerController.getKeyTaskManager().executeLoadKeysRecoveryTask(application,
                getKeyTaskListener(keysLoadedListener), recoveryDevicePrivateKeyXMLs);
    }

    void loadKeysWithBiometricCipher(final Cipher decryptCipher,
                                     final OnKeysLoadedListener keysLoadedListener) {
        TaskManagerController taskManagerController = application.getTaskManagerController();

        taskManagerController.getKeyTaskManager().executeLoadKeysBiometricTask(application,
                getKeyTaskListener(keysLoadedListener), decryptCipher);
    }

    void loadKeysWithoutPassword(final OnKeysLoadedListener keysLoadedListener) {
        if (getAllKeyDataReady()) {
            keysLoadedListener.onKeysLoadedComplete();
            return;
        }

        TaskManagerController taskManagerController = application.getTaskManagerController();

        taskManagerController.getKeyTaskManager().executeLoadKeysNoPassTask(application,
                getKeyTaskListener(keysLoadedListener));
    }

    void loadKeysWithPassword(final String password,
                              final boolean checkPassword,
                              final OnKeysLoadedListener keysLoadedListener) {
        LogUtil.i("ClearKeysService", "loadKeys()");

        if ((!checkPassword) && (getAllKeyDataReady() && !application.getPreferencesController().getPasswordEnabled())) {
            keysLoadedListener.onKeysLoadedComplete();
            return;
        }

        boolean usePassword = application.getPreferencesController().getPasswordEnabled() || checkPassword;

        if (usePassword && password == null) {
            LogUtil.e(this.getClass().getSimpleName(), "Password ist nil !");
            keysLoadedListener.onKeysLoadedFailed(true);
            return;
        }

        TaskManagerController taskManagerController = application.getTaskManagerController();

        taskManagerController.getKeyTaskManager().executeLoadKeysTask(password, application,
                getKeyTaskListener(keysLoadedListener));
    }

    private ConcurrentTaskListener getKeyTaskListener(final OnKeysLoadedListener keysLoadedListener) {
        return new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(ConcurrentTask task,
                                       int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    try {
                        KeyController.this.deviceKeyPair = (KeyPair) task.getResults()[1];
                        KeyController.this.internalEncryptionKey = (SecretKey) task.getResults()[2];

                        GreenDAOSecurityLayer.init(KeyController.this);

                        Object o = task.getResults()[0];

                        if (o != null) {
                            KeyController.this.userKeyPair = (KeyPair) o;

                            if (application.getAccountController().saveAccountKeyPair(KeyController.this.userKeyPair)) {
                                final ConcurrentTaskListener deleteListener = new ConcurrentTaskListener() {
                                    @Override
                                    public void onStateChanged(ConcurrentTask task, int state) {
                                        if (state == ConcurrentTask.STATE_ERROR) {
                                            //Schade
                                            LogUtil.w(KeyController.class.getSimpleName(), "Delete user key pair failed!");
                                        }
                                    }
                                };

                                TaskManagerController taskManagerController = application.getTaskManagerController();
                                taskManagerController.getKeyTaskManager().executeDeleteUserKeyPairTask(application, deleteListener);
                            }
                        } else {
                            KeyController.this.userKeyPair = application.getAccountController().getAccountKeyPair();
                        }
                        keysLoadedListener.onKeysLoadedComplete();
                    } catch (LocalizedException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                        keysLoadedListener.onKeysLoadedFailed(false);
                    }
                } else if (state == ConcurrentTask.STATE_ERROR) {
                    boolean incorrectCredentials = (Boolean) task.getResults()[0];

                    keysLoadedListener.onKeysLoadedFailed(incorrectCredentials);
                }
            }
        };
    }

    void deleteBiometricKeyFromKeystore(final ConcurrentTaskListener deleteListener) {

        TaskManagerController taskManagerController = application.getTaskManagerController();
        taskManagerController.getKeyTaskManager().executeDeleteBiometricKeyTask(application,
                deleteListener);
    }

    public void clearKeys() {
        LogUtil.i("ClearKeysService", "clearKeys");

        userKeyPair = null;
        deviceKeyPair = null;
        internalEncryptionKey = null;
    }

    public void purgeKeys() {
        File keystoreFile = application.getFileStreamPath(KEYSTORE_NAME);

        if ((keystoreFile != null) && keystoreFile.exists()) {
            if (!keystoreFile.delete()) {
                LogUtil.w("KeyController", "Cannot delete key store file");
            }
        }

        File keystoreFileAes = application.getFileStreamPath(KEYSTORE_NAME + ".aes");

        if ((keystoreFileAes != null) && keystoreFileAes.exists()) {
            if (!keystoreFileAes.delete()) {
                LogUtil.w("KeyController", "Cannot delete key store aes file");
            }
        }
    }

    private synchronized boolean getKeyPairsInitiated() {
        return ((deviceKeyPair != null) && (userKeyPair != null));
    }

    public synchronized boolean getAllKeyDataReady() {
        return getKeyPairsInitiated() && (internalEncryptionKey != null);
    }

    public synchronized boolean getInternalKeyReady() {
        return internalEncryptionKey != null;
    }

    public synchronized void loadInternalEncryptionKeyForNotificationPreview()
            throws LocalizedException, IOException {
        if (internalEncryptionKey == null) {
            final byte[] keyString = SecurityUtil.readNotificationKeyFromDisc(application);
            internalEncryptionKey = new SecretKeySpec(keyString, 0, keyString.length, "AES");
        }
    }

    public KeyPair getDeviceKeyPair()
            throws LocalizedException {
        if (deviceKeyPair == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "DeviceKeyPair not ready yet.");
        }

        return deviceKeyPair;
    }

    public KeyPair getUserKeyPair()
            throws LocalizedException {
        if (userKeyPair == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "UserKeyPair not ready yet.");
        }

        return userKeyPair;
    }

    public void reloadUserKeypairFromAccount()
            throws LocalizedException {
        userKeyPair = application.getAccountController().getAccountKeyPair();
    }

    public SecretKey getInternalEncryptionKey()
            throws LocalizedException {
        if (internalEncryptionKey == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "InternalEncryptionKey not ready yet.");
        }

        return internalEncryptionKey;
    }

    public Cipher getDecryptCipherFromBiometricKey()
            throws LocalizedException {
        byte[] ivBytes = SecurityUtil.getBytesFromKeyStore(getKeyStore(), AppConstants.getKeystorePass(), DEVICE_MASTER_KEY_BIOMETRIC_IV);
        final String ivDataBase64 = new String(ivBytes, StandardCharsets.UTF_8);

        return SecurityUtil.getDecryptCipherForBiometricAuthKey(ivDataBase64);
    }

    void initKeys(OnKeyPairsInitiatedListener listener) {
        this.keyPairsInitiatedListener = listener;

        try {
            LogUtil.i(this.getClass().getName(), "Creating keys ...");

            internalEncryptionKey = SecurityUtil.generateAESKey();

            OnGenerateRSAKeyPairCompleteListener deviceKeyListener = new OnGenerateRSAKeyPairCompleteListener() {
                @Override
                public void onComplete(KeyPair keyPair) {
                    deviceKeyPair = keyPair;
                    checkIfInitComplete();
                }

                @Override
                public void onError(LocalizedException e) {
                    callOnKeyPairsInitiatedFailedListener();
                }
            };

            OnGenerateRSAKeyPairCompleteListener userKeyListener = new OnGenerateRSAKeyPairCompleteListener() {
                @Override
                public void onComplete(KeyPair keyPair) {
                    userKeyPair = keyPair;
                    checkIfInitComplete();
                }

                @Override
                public void onError(LocalizedException e) {
                    callOnKeyPairsInitiatedFailedListener();
                }
            };

            SecurityUtil.generateRSAKeyPair(deviceKeyListener);
            SecurityUtil.generateRSAKeyPair(userKeyListener);
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            callOnKeyPairsInitiatedFailedListener();
        }
    }

    boolean createDeviceKeysSync()
            throws LocalizedException {
        LogUtil.i(this.getClass().getName(), "Creating device keys ...");

        internalEncryptionKey = SecurityUtil.generateAESKey();
        deviceKeyPair = SecurityUtil.generateRSAKeyPair();

        return true;
    }

    private synchronized void checkIfInitComplete() {
        if (getKeyPairsInitiated()) {
            callOnKeyPairsInitiatedListener();
        }
    }

    private void callOnKeyPairsInitiatedListener() {
        if (keyPairsInitiatedListener != null) {
            keyPairsInitiatedListener.onKeyPairsInitiated();
        }
    }

    private void callOnKeyPairsInitiatedFailedListener() {
        if (keyPairsInitiatedListener != null) {
            keyPairsInitiatedListener.onKeyPairsInitiatedFailed();
        }
    }

    public KeyStore getKeyStore()
            throws LocalizedException {
        return SecurityUtil.loadKeyStore(application, KEYSTORE_NAME);
    }

    @Override
    public void appDidEnterForeground() {
    }

    @Override
    public void appGoesToBackGround() {
        TaskManagerController taskManagerController = application.getTaskManagerController();
        taskManagerController.getKeyTaskManager().cancelAllTasks();
    }

    public String decryptBase64StringWithDevicePrivateKey(@NonNull final String encryptedBase64String)
            throws LocalizedException {
        if (deviceKeyPair == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE);
        }

        byte[] encryptedValue = Base64.decode(encryptedBase64String, Base64.NO_WRAP);

        byte[] decryptedValue = SecurityUtil.decryptMessageWithRSA(encryptedValue, deviceKeyPair.getPrivate());

        return new String(decryptedValue, StandardCharsets.UTF_8);
    }

    String encryptStringWithDeviceKeyToBase64String(@NonNull final String decryptedString)
            throws LocalizedException {
        if (deviceKeyPair == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE);
        }

        byte[] encryptedValue = SecurityUtil.encryptMessageWithRSA(decryptedString.getBytes(StandardCharsets.UTF_8), deviceKeyPair.getPublic());

        return Base64.encodeToString(encryptedValue, Base64.NO_WRAP);
    }

    public interface OnKeyPairsInitiatedListener {
        void onKeyPairsInitiated();

        void onKeyPairsInitiatedFailed();
    }

    public interface OnKeysSavedListener {

        void onKeysSaveComplete();

        void onKeysSaveFailed();
    }

    public interface OnKeysLoadedListener {

        void onKeysLoadedComplete();

        void onKeysLoadedFailed(boolean incorrectCredentials);
    }
}
