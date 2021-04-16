// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.greendao;

import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.AbstractSecureModel;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;

import java.io.Serializable;

public class Device extends AbstractSecureModel implements Serializable {
    private Long mId;

    private String mGuid;

    private String mAccountGuid;

    private byte[] mEncryptedData;

    private byte[] mIv;

    private String mPublicKey;

    private Boolean mOwnDevice;

    private String mAttributes;

    public Device() {
    }

    public Device(Long id) {
        this.setId(id);
    }

    public Device(Long id,
                  String guid,
                  String accountGuid,
                  byte[] encryptedData,
                  byte[] iv,
                  String publicKey,
                  Boolean ownDevice,
                  String attributes) {
        this.setId(id);
        this.setAccountGuid(accountGuid);
        this.setGuid(guid);
        this.setPublicKey(publicKey);
        this.setOwnDevice(ownDevice);
        this.setAttributes(attributes);

        if (encryptedData != null) {
            this.mEncryptedData = encryptedData.clone();
        }
        if (iv != null) {
            this.mIv = iv.clone();
        }
    }

    public byte[] getEncryptedData() {
        synchronized (this) {
            if (mEncryptedData != null) {
                return mEncryptedData.clone();
            } else {
                return null;
            }
        }
    }

    public void setEncryptedData(byte[] encryptedData) {
        synchronized (this) {
            if (encryptedData != null) {
                this.mEncryptedData = encryptedData.clone();
            }
        }
    }

    public byte[] getIv() {
        synchronized (this) {
            if (mIv != null) {
                return mIv.clone();
            } else {
                return null;
            }
        }
    }

    public void setIv(byte[] iv) {
        synchronized (this) {
            if (iv != null) {
                this.mIv = iv.clone();
            }
        }
    }

    public Long getId() {
        return mId;
    }

    public void setId(Long mId) {
        this.mId = mId;
    }

    public String getGuid() {
        return mGuid;
    }

    public void setGuid(String mGuid) {
        this.mGuid = mGuid;
    }

    public String getAccountGuid() {
        return mAccountGuid;
    }

    public void setAccountGuid(String mAccountGuid) {
        this.mAccountGuid = mAccountGuid;
    }

    public String getPublicKey() {
        return mPublicKey;
    }

    public void setPublicKey(String mPublicKey) {
        this.mPublicKey = mPublicKey;
    }

    public Boolean getOwnDevice() {
        return mOwnDevice;
    }

    public void setOwnDevice(Boolean mOwnDevice) {
        this.mOwnDevice = mOwnDevice;
    }

    public String getAttributes() {
        return mAttributes;
    }

    public void setAttributes(String mAttributes) {
        this.mAttributes = mAttributes;
    }

    public void setSignedDevicePublicKeyFingerprint(String fingerprint)
            throws LocalizedException {
        synchronized (this) {
            GreenDAOSecurityLayer.getInstance().set(this, "signedPubKeyFingerprint", fingerprint);
        }
    }
}
