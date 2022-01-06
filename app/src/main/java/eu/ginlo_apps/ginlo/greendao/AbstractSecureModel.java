// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import org.json.JSONObject;

abstract class AbstractSecureModel {
    private final String mUniqueId;

    private JSONObject mDataJSONCache;

    AbstractSecureModel() {
        this.mUniqueId = GuidUtil.generateGuid("");
    }

    public abstract byte[] getEncryptedData();

    public abstract void setEncryptedData(byte[] encryptedData);

    public abstract byte[] getIv();

    public abstract void setIv(byte[] mIv);

    void afterLoad() {
    }

    void afterInit() {
    }

    @Override
    public int hashCode() {
        return mUniqueId.hashCode();
    }

    /**
     * Eqausl
     *
     * @param other
     * @return
     */
    @Override
    public boolean equals(Object other) {
        return (this == other);
    }

    void beforeSafe() {
        if (this.mDataJSONCache != null) {
            // while encrypting the cache no other operations are allowed
            synchronized (this) {
                try {
                    GreenDAOSecurityLayer.getInstance().encryptCache(this, this.mDataJSONCache);
                    this.mDataJSONCache = null;
                } catch (Throwable e) {
                    // discard all changes
                    LogUtil.w("SECURE MODEL", "beforeSafe()", e);
                }
            }
        }
    }

    @NonNull
    JSONObject getDataCache()
            throws LocalizedException {
        if (this.mDataJSONCache == null) {
            this.mDataJSONCache = GreenDAOSecurityLayer.getInstance().decryptModel(this);

            if (this.mDataJSONCache == null && this.getEncryptedData() != null) {
                throw new LocalizedException("Something wrong happened on decrypting the encrypted property bag.");
            }

            if (this.mDataJSONCache == null) {
                this.mDataJSONCache = new JSONObject();
            }
        }

        return this.mDataJSONCache;
    }
}
