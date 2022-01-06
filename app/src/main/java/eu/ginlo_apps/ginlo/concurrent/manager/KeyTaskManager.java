// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.KeysTask;
import eu.ginlo_apps.ginlo.concurrent.task.SaveKeysTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyPair;

public class KeyTaskManager
        extends TaskManager {

    public void executeLoadKeysTask(final String password,
                                    final SimsMeApplication context,
                                    final ConcurrentTaskListener listener) {
        ConcurrentTask task = new KeysTask(context, password, KeysTask.JOB_MODE_LOAD_KEYS);

        task.addListener(listener);
        execute(task);
    }

    public void executeLoadKeysBiometricTask(final SimsMeApplication context,
                                             final ConcurrentTaskListener listener,
                                             final Cipher decryptCipher) {
        ConcurrentTask task = new KeysTask(context, decryptCipher, KeysTask.JOB_MODE_LOAD_KEYS_BIOMETRIC);

        task.addListener(listener);
        execute(task);
    }

    public void executeLoadKeysNoPassTask(final SimsMeApplication context,
                                          final ConcurrentTaskListener listener) {
        ConcurrentTask task = new KeysTask(context, KeysTask.JOB_MODE_LOAD_KEYS_NO_PASS);

        task.addListener(listener);
        execute(task);
    }

    public void executeLoadKeysRecoveryTask(SimsMeApplication context,
                                            ConcurrentTaskListener listener,
                                            String recoveryDevicePrivateKeyXML) {
        ConcurrentTask task = new KeysTask(context, KeysTask.JOB_MODE_RECOVER_KEY, recoveryDevicePrivateKeyXML);

        task.addListener(listener);
        execute(task);
    }

    public void executeDeleteUserKeyPairTask(SimsMeApplication context,
                                             ConcurrentTaskListener listener) {
        ConcurrentTask task = new KeysTask(context, KeysTask.JOB_MODE_DELETE_USER_KEYPAIR);

        task.addListener(listener);
        execute(task);
    }

    public void executeDeleteBiometricKeyTask(SimsMeApplication context,
                                              ConcurrentTaskListener listener) {
        ConcurrentTask task = new KeysTask(context, KeysTask.JOB_MODE_DELETE_BIOMETRIC);

        task.addListener(listener);
        execute(task);
    }

    public void executeSaveKeysTask(String keyStoreName,
                                    boolean usePassword,
                                    KeyPair deviceKeyPair,
                                    SecretKey internalEncryptionKey,
                                    String password,
                                    SimsMeApplication application,
                                    ConcurrentTaskListener listener) {
        ConcurrentTask task = new SaveKeysTask(keyStoreName, usePassword, deviceKeyPair,
                internalEncryptionKey, password, application);

        task.addListener(listener);
        execute(task);
    }

    public void executeSaveBiometricKeysTask(String keyStoreName,
                                             KeyPair deviceKeyPair,
                                             SimsMeApplication application,
                                             Cipher encryptBiometricCipher,
                                             ConcurrentTaskListener listener) {
        ConcurrentTask task = new SaveKeysTask(application, keyStoreName, deviceKeyPair, encryptBiometricCipher);

        task.addListener(listener);
        execute(task);
    }
}
