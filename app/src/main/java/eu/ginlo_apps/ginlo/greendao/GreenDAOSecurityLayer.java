// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.AbstractSecureModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.spec.IvParameterSpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class GreenDAOSecurityLayer {
    //~ Static fields/initializers --------------------------------------------------------------------------------------

    private static GreenDAOSecurityLayer mInstance;

    //~ Instance members ------------------------------------------------------------------------------------------------

    private KeyController mKeyController;

    //~ Constructors ----------------------------------------------------------------------------------------------------

    /**
     * [!CONSTR_DESCIRPTION_FOR_GreenDAOSecurityLayer!]
     */
    private GreenDAOSecurityLayer() {
    }

    //~ Methods ---------------------------------------------------------------------------------------------------------

    public static void init(KeyController keyController) {
        //LogUtil.i(GreenDAOSecurityLayer.class.toString(), "init");
        getInstance().mKeyController = keyController;
    }

    public static boolean isInitiated() {
        return ((mInstance != null) && (mInstance.mKeyController != null) && mInstance.mKeyController.getInternalKeyReady());
    }

    public static GreenDAOSecurityLayer getInstance() {
        synchronized (GreenDAOSecurityLayer.class) {
            //LogUtil.i(GreenDAOSecurityLayer.class.toString(), "getInstance");
            if (mInstance == null) {
                mInstance = new GreenDAOSecurityLayer();
            }
            return mInstance;
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Object get(final eu.ginlo_apps.ginlo.greendao.AbstractSecureModel model,
                      final String propertyName)
            throws LocalizedException {
        try {
            synchronized (model) {
                JSONObject json = model.getDataCache();

                if (json.has(propertyName)) {
                    return json.get(propertyName);
                }

                return null;
            }
        } catch (JSONException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.JSON_OBJECT_INVALID, e);
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void set(final eu.ginlo_apps.ginlo.greendao.AbstractSecureModel model,
                    final String propertyName,
                    final Object value)
            throws LocalizedException {
        try {
            synchronized (model) {
                JSONObject decryptedJSON = model.getDataCache();

                decryptedJSON.put(propertyName, value);

                //encryptCache(model, decryptedJSON);
            }
        } catch (JSONException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.JSON_OBJECT_INVALID, e);
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void remove(final eu.ginlo_apps.ginlo.greendao.AbstractSecureModel model,
                       final String propertyName)
            throws LocalizedException {
        synchronized (model) {
            JSONObject decryptedJSON = model.getDataCache();

            decryptedJSON.remove(propertyName);
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void encryptCache(final eu.ginlo_apps.ginlo.greendao.AbstractSecureModel model,
                             final JSONObject json)
            throws LocalizedException {
        if (mKeyController == null) {
            LogUtil.e(this.getClass().getName(), "NO_INIT_ERROR");

            throw new LocalizedException(LocalizedException.NO_INIT_CALLED, "keycontroller is null");
        }

        byte[] ivBytes = model.getIv();
        IvParameterSpec iv;

        if (ivBytes != null) {
            iv = SecurityUtil.getIvFromBytes(ivBytes);
        } else {
            iv = SecurityUtil.generateIV();
            model.setIv(iv.getIV());
        }

        byte[] encryptedData = SecurityUtil.encryptMessageWithAES(json.toString().getBytes(StandardCharsets.UTF_8),
                mKeyController.getInternalEncryptionKey(),
                iv);

        model.setEncryptedData(encryptedData);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public JSONObject decryptModel(eu.ginlo_apps.ginlo.greendao.AbstractSecureModel model)
            throws LocalizedException {
        try {
            //       LogUtil.i(GreenDAOSecurityLayer.class.toString(), "decryptModel");

            if (mKeyController == null) {
                LogUtil.e(this.getClass().getName(), "NO_INIT_ERROR");

                throw new LocalizedException(LocalizedException.NO_INIT_CALLED, "keycontroller is null");
            }

            if ((model.getEncryptedData() == null) || (model.getIv() == null)) {
                    return null;
            }

            byte[] encryptedData = model.getEncryptedData();
            IvParameterSpec iv = new IvParameterSpec(model.getIv());

            byte[] decryptedData = SecurityUtil.decryptMessageWithAES(encryptedData,
                    mKeyController.getInternalEncryptionKey(),
                    iv);

            if (decryptedData == null) {
                return null;
            }

            String decryptedString = new String(decryptedData, StandardCharsets.UTF_8);

            return new JSONObject(decryptedString);
        } catch (JSONException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.JSON_OBJECT_INVALID, e);
        }
    }
}
