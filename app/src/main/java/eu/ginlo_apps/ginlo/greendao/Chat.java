// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

// THIS CODE IS GENERATED BY greenDAO, EDIT ONLY INSIDE THE "KEEP"-SECTIONS

// KEEP INCLUDES - put your custom includes here

import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.AbstractSecureModel;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

// KEEP INCLUDES END

public class Chat
        extends AbstractSecureModel
        implements java.io.Serializable {

    // KEEP FIELDS - put your custom fields here
    public static final int TYPE_SINGLE_CHAT = 0;
    public static final int TYPE_GROUP_CHAT = 1;
    public static final int TYPE_SINGLE_CHAT_INVITATION = 2;
    public static final int TYPE_GROUP_CHAT_INVITATION = 3;
    public static final int TYPE_CHANNEL = 4;

    // The following room types are available to all clients
    public static final String ROOM_TYPE_STD = "ChatRoom";
    public static final String ROOM_TYPE_ANNOUNCEMENT = "AnnouncementRoom";
    // The following room types are managed by the cockpit and available to business clients only
    public static final String ROOM_TYPE_MANAGED = "ManagedRoom";
    public static final String ROOM_TYPE_RESTRICTED = "RestrictedRoom";

    private Long mId;
    private String mChatGuid;
    private Integer mType;
    private byte[] mIv;
    private byte[] mEncryptedData;
    private Long mLastChatModifiedDate;
    private Long mLastMsgId;
    private byte[] mGroupChatImage;
    // KEEP FIELDS END

    public Chat() {
    }

    public Chat(final Long id) {
        this.mId = id;
    }

    public Chat(final Long id,
                final String chatGuid,
                final Integer type,
                final byte[] iv,
                final byte[] encryptedData,
                final Long lastMsgId,
                final Long lastChatModifiedDate) {
        this.mId = id;
        this.mChatGuid = chatGuid;
        this.mType = type;
        if (iv != null) {
            this.mIv = iv.clone();
        }
        if (encryptedData != null) {
            this.mEncryptedData = encryptedData.clone();
        }
        this.mLastMsgId = lastMsgId;
        this.mLastChatModifiedDate = lastChatModifiedDate;
    }

    public Long getLastMsgId() {
        return mLastMsgId;
    }

    /**
     * Bitte diese Methode nicht direkt aufrufen!<br>
     * <p>
     * Es soll die Methode {@link eu.ginlo_apps.ginlo.controller.message.MessageController#setLatestMessageToChat(Message, Chat, boolean)}
     * oder {@link eu.ginlo_apps.ginlo.controller.message.MessageController#setLatestMessageToChat(Message)} genutzt werden.<br>
     * <p>
     * Da hier nur die Message Ids hinterlegt werden, die relevant f??r die Chat??bersicht sind.
     */
    public void setLastMsgId(final Long lastMsgId) {
        this.mLastMsgId = lastMsgId;
    }

    public Long getLastChatModifiedDate() {
        if (mLastChatModifiedDate == null) {
            try {
                final long oldModifiedDate = getLastModifiedDate();
                if (oldModifiedDate > 0) {
                    mLastChatModifiedDate = oldModifiedDate;
                    GreenDAOSecurityLayer.getInstance().set(this, "lastModifiedDate", 0);
                }
            } catch (final LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }

        return mLastChatModifiedDate == null ? (long) 0 : mLastChatModifiedDate;
    }

    public void setLastChatModifiedDate(final Long lastChatModifiedDate) {
        this.mLastChatModifiedDate = lastChatModifiedDate;
    }

    public Boolean getIsRemoved() {
        Boolean isRemoved = null;
        try {
            isRemoved = (Boolean) GreenDAOSecurityLayer.getInstance().get(this, "removed");
        } catch (LocalizedException e) {
            //
        }
        return isRemoved != null ? isRemoved : false;
    }

    // KEEP METHODS - put your custom methods here

    public void setIsRemoved(final boolean isRemoved)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "removed", isRemoved);
    }

    public Boolean getIsReadOnly() {
        Boolean isReadOnly = null;
        try {
            isReadOnly = (Boolean) GreenDAOSecurityLayer.getInstance().get(this, "isReadonly");
        } catch (LocalizedException e) {
            //
        }
        return isReadOnly != null ? isReadOnly : false;
    }

    public void setIsReadOnly(final boolean isReadonly)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "isReadonly", isReadonly);
    }

    public String getRoomType()
            throws LocalizedException {
        final String retVal = (String) GreenDAOSecurityLayer.getInstance().get(this, "roomType");

        return retVal != null ? retVal : ROOM_TYPE_STD;
    }

    public void setRoomType(final String roomType)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "roomType", roomType);
    }

    public long getDate()
            throws LocalizedException {
        final String dateString = (String) GreenDAOSecurityLayer.getInstance().get(this, "date");

        return DateUtil.utcStringToMillis(dateString);
    }

    public String getOwner()
            throws LocalizedException {
        return (String) GreenDAOSecurityLayer.getInstance().get(this, "owner");
    }

    public void setOwner(final String ownerGuid)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "owner", ownerGuid);
    }

    public JsonArray getMembers()
            throws LocalizedException {
        final JsonParser parser = new JsonParser();
        final String jsonArrayString = (String) GreenDAOSecurityLayer.getInstance().get(this, "members");

        if (jsonArrayString == null) {
            return new JsonArray();
        } else {
            return parser.parse(jsonArrayString).getAsJsonArray();
        }
    }

    public void setMembers(final JsonArray members)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "members", members.toString());
    }

    public JsonArray getAdmins()
            throws LocalizedException {
        final JsonParser parser = new JsonParser();
        final String jsonArrayString = (String) GreenDAOSecurityLayer.getInstance().get(this, "admins");

        if (jsonArrayString == null) {
            return new JsonArray();
        } else {
            return parser.parse(jsonArrayString).getAsJsonArray();
        }
    }

    public void setAdmins(final JsonArray admins)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "admins", admins.toString());
    }

    public void setWriters(final String[] writers)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "writers", writers);
    }

    public String getTitle()
            throws LocalizedException {
        return (String) GreenDAOSecurityLayer.getInstance().get(this, "title");
    }

    public void setTitle(final String title)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "title", title);
    }

    public SecretKey getChatAESKey()
            throws LocalizedException {
        SecretKey aesKey = null;
        final String aesKeyString = (String) GreenDAOSecurityLayer.getInstance().get(this, "aesKey");

        if (!StringUtil.isNullOrEmpty(aesKeyString)) {
            aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyString);
        }

        return aesKey;
    }

    public void setChatAESKey(final SecretKey aesKey)
            throws LocalizedException {
        final byte[] aesKeyBytes = aesKey.getEncoded();
        final String aesKeyString = Base64.encodeToString(aesKeyBytes, Base64.DEFAULT);

        GreenDAOSecurityLayer.getInstance().set(this, "aesKey", aesKeyString);
    }

    public String getChatAESKeyAsBase64()
            throws LocalizedException {
        return (String) GreenDAOSecurityLayer.getInstance().get(this, "aesKey");
    }

    public void setChatAESKeyAsBase64(final String aesKeyAsBase64)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "aesKey", aesKeyAsBase64);
    }

    public IvParameterSpec getChatInfoIV()
            throws LocalizedException {
        IvParameterSpec iv = null;
        final String ivString = (String) GreenDAOSecurityLayer.getInstance().get(this, "chatInfoIV");

        if (!StringUtil.isNullOrEmpty(ivString)) {
            final byte[] ivBytes = Base64.decode(ivString, Base64.DEFAULT);

            iv = new IvParameterSpec(ivBytes);
        }

        return iv;
    }

    public void setChatInfoIV(final IvParameterSpec iv)
            throws LocalizedException {
        final byte[] ivBytes = iv.getIV();
        final String ivString = Base64.encodeToString(ivBytes, Base64.DEFAULT);

        GreenDAOSecurityLayer.getInstance().set(this, "chatInfoIV", ivString);
    }

    public String getChatInfoIVAsBase64()
            throws LocalizedException {
        return (String) GreenDAOSecurityLayer.getInstance().get(this, "chatInfoIV");
    }

    public void setChatInfoIVAsBase64(final String ivAsBase64)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "chatInfoIV", ivAsBase64);
    }

    private long getLastModifiedDate()
            throws LocalizedException {
        final Object tmp = GreenDAOSecurityLayer.getInstance().get(this, "lastModifiedDate");

        if (tmp instanceof Long) {
            return (long) tmp;
        }
        return (long) 0;
    }

    public Long getId() {
        synchronized (this) {
            return mId;
        }
    }

    public void setId(final Long id) {
        synchronized (this) {
            this.mId = id;
        }
    }

    public String getChatGuid() {
        synchronized (this) {
            return mChatGuid;
        }
    }

    public void setChatGuid(final String chatGuid) {
        synchronized (this) {
            this.mChatGuid = chatGuid;
        }
    }

    public Integer getType() {
        synchronized (this) {
            return mType;
        }
    }

    public void setType(final Integer type) {
        synchronized (this) {
            this.mType = type;
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

    public void setEncryptedData(final byte[] encryptedData) {
        synchronized (this) {
            if (encryptedData != null) {
                mEncryptedData = encryptedData.clone();
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

    public void setIv(final byte[] iv) {
        synchronized (this) {
            if (iv != null) {
                this.mIv = iv.clone();
            }
        }
    }

    public @Nullable
    byte[] getGroupChatImage() {
        if (mGroupChatImage != null) {
            return mGroupChatImage.clone();
        } else {
            return null;
        }
    }

    /**
     * Wird nicht persistent gescpeichert. Nur zur temporaeren Nutzung.
     */
    public void setGroupChatImage(final byte[] imageBytes) {
        if (imageBytes != null) {
            mGroupChatImage = imageBytes.clone();
        }
    }

    public long getSilentTill() {
        Object tmp = null;
        try {
            tmp = GreenDAOSecurityLayer.getInstance().get(this, "silentTill");
        } catch (LocalizedException e) {
            //
        }

        if (tmp instanceof Long) {
            return (long) tmp;
        }

        return 0L;
    }

    public void setSilentTill(final long silentTill)
            throws LocalizedException {
        GreenDAOSecurityLayer.getInstance().set(this, "silentTill", silentTill);
    }

    // KEEP METHODS END
}
