// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Base64;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ClipBoardController {

    private static final String PREF_NAME = "ClipBoardController.clipBoard";

    private final SharedPreferences sharedPrefs;

    private final SimsMeApplication mApplication;

    /**
     * ClipBoardController
     *
     * @param application
     */
    public ClipBoardController(final SimsMeApplication application) {
        mApplication = application;
        sharedPrefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void put(String key,
                    String value)
            throws LocalizedException {
        eu.ginlo_apps.ginlo.controller.KeyController keyController = mApplication.getKeyController();
        if (!keyController.getAllKeyDataReady()) {
            return;
        }

        SecretKey internalKey = keyController.getInternalEncryptionKey();
        Editor editor = sharedPrefs.edit();
        IvParameterSpec iv = SecurityUtil.generateIV();
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = iv.getIV();
        byte[] encryptedValueBytes = SecurityUtil.encryptMessageWithAES(valueBytes, internalKey, iv);
        byte[] containerBytes = new byte[ivBytes.length + encryptedValueBytes.length];

        System.arraycopy(ivBytes, 0, containerBytes, 0, ivBytes.length);
        System.arraycopy(encryptedValueBytes, 0, containerBytes, ivBytes.length, encryptedValueBytes.length);

        String encodedEncryptedValue = Base64.encodeToString(containerBytes, Base64.DEFAULT);

        editor.putString(key, encodedEncryptedValue);
        editor.commit();
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public String get(String key)
            throws LocalizedException {
        String value = null;
        String encodedEncryptedValue = sharedPrefs.getString(key, null);

        if (encodedEncryptedValue != null) {
            try {
                KeyController keyController = mApplication.getKeyController();
                SecretKey internalKey = keyController.getInternalEncryptionKey();
                byte[] containerBytes = Base64.decode(encodedEncryptedValue, Base64.DEFAULT);
                byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
                byte[] encryptedValueBytes = new byte[containerBytes.length - ivBytes.length];

                System.arraycopy(containerBytes, 0, ivBytes, 0, ivBytes.length);
                System.arraycopy(containerBytes, ivBytes.length, encryptedValueBytes, 0, encryptedValueBytes.length);

                IvParameterSpec iv = new IvParameterSpec(ivBytes);
                byte[] decryptedValueBytes = SecurityUtil.decryptMessageWithAES(encryptedValueBytes, internalKey,
                        iv);

                value = new String(decryptedValueBytes, StandardCharsets.UTF_8);
            } catch (NullPointerException | NegativeArraySizeException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                throw new LocalizedException(LocalizedException.CLIPBOARD_GET_FAILED, e);
            }
        }
        return value;
    }
}
