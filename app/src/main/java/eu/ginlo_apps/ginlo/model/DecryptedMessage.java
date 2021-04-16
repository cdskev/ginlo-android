// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model;

import android.graphics.Bitmap;
import android.location.Location;
import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.util.AudioUtil;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import ezvcard.Ezvcard;
import ezvcard.VCard;

public class DecryptedMessage {
    public static final int ATTACHMENT_ENCODING_VERSION_DEFAULT = 0;
    public static final int ATTACHMENT_ENCODING_VERSION_1 = 1;

    private static final String TAG = DecryptedMessage.class.getSimpleName();
    private static final String EXCEPTION_PREFIX = "Can't use this method with Content-Type: ";

    private JsonObject mDecryptedDataContainer;
    private String text;
    private String avcRoom;
    private String appGinloControl;
    private String attachmentDescription;
    private VCard vCard;
    private String nickName;
    private String phoneNumber;
    private String simsmeID;
    private String groupGuid;
    private String groupName;
    private Bitmap image;
    private byte[] groupImage;
    private Location location;
    private MessageDestructionParams messageDestructionParams;
    private String contentType;
    private Message mMessage;
    private String mSection;
    private String mProfilKey;
    private String mFilename;
    private String mFileMimetype;
    private String mFilesize;
    private String mAttachmentEncodingVersion;
    private String mRssTitle;

    public DecryptedMessage(final Message msg) {
        if (msg == null) {
            throw new NullPointerException("Message Object is null");
        }

        mMessage = msg;
        mDecryptedDataContainer = msg.getDecryptedDataContainer();
    }

    public Message getMessage() {
        return mMessage;
    }

    public JsonObject getDecryptedDataContainer()
            throws LocalizedException {
        if (mDecryptedDataContainer == null) {
            LogUtil.w(DecryptedMessage.class.getSimpleName(), "data container is null");

            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "data container is null");
        }

        return mDecryptedDataContainer;
    }

    private boolean isNotDecrypted() {
        return !mMessage.isMessageDecrypted();
    }

    @Nullable
    public MessageDestructionParams getMessageDestructionParams() {
        synchronized (this) {
            try {
                if (messageDestructionParams == null) {
                    if (JsonUtil.hasKey(DataContainer.DESTRUCTION_COUNTDOWN, getDecryptedDataContainer())) {
                        try {
                            messageDestructionParams = new MessageDestructionParams(getDecryptedDataContainer().get(DataContainer.DESTRUCTION_COUNTDOWN)
                                    .getAsInt(), null);
                        } catch (NumberFormatException nfe) {
                            LogUtil.e(this.getClass().getName(), nfe.getMessage(), nfe);
                        }
                    } else if (JsonUtil.hasKey(DataContainer.DESTRUCTION_DATE, getDecryptedDataContainer())) {
                        messageDestructionParams = new MessageDestructionParams(null,
                                DateUtil.utcStringToDate(getDecryptedDataContainer()
                                        .get(DataContainer.DESTRUCTION_DATE)
                                        .getAsString()));
                    }
                    if (messageDestructionParams != null) {
                        if ((messageDestructionParams.date == null) && (messageDestructionParams.countdown == null)) {
                            messageDestructionParams = null;
                            return null;
                        }
                    }
                }
                return messageDestructionParams;
            } catch (LocalizedException e) {
                return null;
            }
        }
    }

    @Nullable
    public String getContentType() {
        synchronized (this) {
            try {
                if (contentType == null) {
                    if (JsonUtil.hasKey(DataContainer.CONTENT_TYPE, getDecryptedDataContainer())) {
                        contentType = getDecryptedDataContainer().get(DataContainer.CONTENT_TYPE).getAsString();
                    }
                }
                return contentType;
            } catch (LocalizedException e) {
                return null;
            }
        }
    }

    public String getText()
            throws LocalizedException {
        if (getContentType() != null && !getContentType().equals(MimeType.TEXT_PLAIN) && !getContentType().equals(MimeType.TEXT_RSS)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if (text == null) {
                    if (getContentType().equals(MimeType.TEXT_PLAIN)) {
                        if (JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                            text = getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                        }
                    }
                    if (getContentType().equals(MimeType.TEXT_RSS)) {
                        text = getRssTitle() + "\n" + getRssText();
                    }
                }

                return text;
            } catch (UnsupportedOperationException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }

    // KS: appControl
    public String getAppGinloControl()
            throws LocalizedException {
        if (getContentType() != null && !getContentType().equals(MimeType.APP_GINLO_CONTROL)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if (appGinloControl == null) {
                    if (JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                        appGinloControl = getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                    }
                }

                return appGinloControl;
            } catch (UnsupportedOperationException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }


    // KS: AVC
    public String getAVCRoom()
            throws LocalizedException {
        if (getContentType() != null && !getContentType().equals(MimeType.TEXT_V_CALL)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if (avcRoom == null) {
                       if (JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                         avcRoom = getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                       }
                }

                return avcRoom;
            } catch (UnsupportedOperationException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }

    public String getRssTitle()
            throws LocalizedException {
        if (getContentType() != null && !getContentType().equals(MimeType.TEXT_RSS)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if (mRssTitle == null) {
                    if (JsonUtil.hasKey("data", getDecryptedDataContainer())) {
                        String container = getDecryptedDataContainer().get("data").getAsString();
                        JsonObject o = JsonUtil.getJsonObjectFromString(container);
                        mRssTitle = o.getAsJsonPrimitive("title").getAsString();
                    }
                }

                return mRssTitle;
            } catch (UnsupportedOperationException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }

    public String getRssText()
            throws LocalizedException {
        if (getContentType() != null && !getContentType().equals(MimeType.TEXT_RSS)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if (text == null) {
                    if (JsonUtil.hasKey("data", getDecryptedDataContainer())) {
                        String container = getDecryptedDataContainer().get("data").getAsString();
                        JsonObject o = JsonUtil.getJsonObjectFromString(container);
                        text = o.getAsJsonPrimitive("text").getAsString();
                        if (o.has("link")) {
                            String link = o.getAsJsonPrimitive("link").getAsString();
                            text += "\n" + link;
                        }
                    }
                }

                return text;
            } catch (UnsupportedOperationException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }

    public String getAttachmentDescription() {
        if (!StringUtil.isEqual(getContentType(), MimeType.IMAGE_JPEG) && !StringUtil.isEqual(getContentType(), MimeType.VIDEO_MPEG)) {
            return null;
        }

        synchronized (this) {
            try {
                if (attachmentDescription == null) {
                    if (JsonUtil.hasKey(DataContainer.CONTENT_DESC, getDecryptedDataContainer())) {
                        attachmentDescription = getDecryptedDataContainer().get(DataContainer.CONTENT_DESC).getAsString();
                    }
                }

                return attachmentDescription;
            } catch (UnsupportedOperationException | LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }

    /**
     * Only use for Channel Message
     *
     * @return Channel Section Name
     */
    public String getSection() {
        synchronized (this) {
            try {
                if (mSection == null) {
                    if (JsonUtil.hasKey(DataContainer.SECTION, getDecryptedDataContainer())) {
                        mSection = getDecryptedDataContainer().get(DataContainer.SECTION).getAsString();
                    }
                }

                return mSection;
            } catch (UnsupportedOperationException | LocalizedException e) {
                LogUtil.w(this.getClass().getName(), e.getMessage(), e);

                return "";
            }
        }
    }

    /**
     * Only use for Channel Message
     *
     * @return Channel Preview Image
     */
    @Nullable
    public Bitmap getChannelImagePreview() {
        synchronized (this) {
            try {
                Bitmap channelPreviewImage = null;

                if (JsonUtil.hasKey(DataContainer.CHANNEL_PREVIEW_IMAGE, getDecryptedDataContainer())) {
                    JsonElement previewImageContainer = getDecryptedDataContainer().get(DataContainer.CHANNEL_PREVIEW_IMAGE);

                    if ((previewImageContainer != null) && previewImageContainer.isJsonObject()
                            && ((JsonObject) previewImageContainer).has(DataContainer.CONTENT)) {
                        final String base64PreviewImageString = ((JsonObject) previewImageContainer).get(DataContainer.CONTENT)
                                .getAsString();
                        final byte[] previewImageBytes = Base64.decode(base64PreviewImageString, Base64.DEFAULT);

                        channelPreviewImage = BitmapUtil.decodeByteArray(previewImageBytes);
                    }
                }

                return channelPreviewImage;
            } catch (LocalizedException e) {
                LogUtil.w(this.getClass().getName(), e.getMessage(), e);
                return null;
            }
        }
    }

    @Nullable
    public JsonElement getCitation() {
        synchronized (this) {
            try {
                if (JsonUtil.hasKey(DataContainer.CITATION, getDecryptedDataContainer())) {
                    return getDecryptedDataContainer().get(DataContainer.CITATION);
                }
                return null;
            } catch (LocalizedException e) {
                return null;
            }
        }
    }

    @Nullable
    public String getContentRaw() {
        synchronized (this) {
            try {
                if (JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                    return getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                }
                return null;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public Bitmap getPreviewImage() {
        if (isNotDecrypted()) {
            return null;
        }
        if (!StringUtil.isEqual(getContentType(), MimeType.IMAGE_JPEG) && !StringUtil.isEqual(getContentType(), MimeType.VIDEO_MPEG)
                && !StringUtil.isEqual(getContentType(), MimeType.AUDIO_MPEG)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if ((image == null) && JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                    if (getContentType().equals(MimeType.AUDIO_MPEG)) {
                        image = AudioUtil.getWaveformFromLevels();
                    } else {
                        final String base64PreviewImageString = getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                        if (!StringUtil.isNullOrEmpty(base64PreviewImageString)) {
                            try {
                                final byte[] previewImageBytes = Base64.decode(base64PreviewImageString, Base64.DEFAULT);

                                image = BitmapUtil.decodeByteArray(previewImageBytes);
                            } catch (Exception e) {
                                LogUtil.w(this.getClass().getSimpleName(), "getPreviewImage()", e);
                            }
                        }
                    }
                }
            } catch (LocalizedException e) {
                return null;
            }

            return image;
        }
    }

    @Nullable
    public Location getLocation() {
        if (isNotDecrypted()) {
            return null;
        }

        if (!StringUtil.isEqual(getContentType(), MimeType.MODEL_LOCATION)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if ((location == null) && JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                    JsonObject contentContainer;
                    String contentContainerString;
                    final JsonParser parser = new JsonParser();

                    final Location tempLocation = new Location("ginlo Attachment");

                    contentContainerString = getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                    contentContainer = parser.parse(contentContainerString).getAsJsonObject();
                    tempLocation.setLongitude(contentContainer.get(DataContainer.LOCATION_CONTAINER_LONGITUDE).getAsDouble());
                    tempLocation.setLatitude(contentContainer.get(DataContainer.LOCATION_CONTAINER_LATITUDE).getAsDouble());
                    location = tempLocation;
                }
                return location;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public Bitmap getLocationImage() {
        if (isNotDecrypted()) {
            return null;
        }

        if (!StringUtil.isEqual(getContentType(), MimeType.MODEL_LOCATION)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if ((image == null) && JsonUtil.hasKey(DataContainer.CONTENT, getDecryptedDataContainer())) {
                    JsonObject contentContainer;
                    String contentContainerString;
                    final JsonParser parser = new JsonParser();

                    contentContainerString = getDecryptedDataContainer().get(DataContainer.CONTENT).getAsString();
                    contentContainer = parser.parse(contentContainerString).getAsJsonObject();
                    image = BitmapUtil.decodeByteArray(Base64.decode(contentContainer.get(DataContainer.LOCATION_CONTAINER_PREVIEW)
                            .getAsString(), Base64.DEFAULT));
                }
                return image;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getNickName() {
        synchronized (this) {
            try {
                if (nickName == null) {
                    nickName = JsonUtil.stringFromJO(DataContainer.NICKNAME, getDecryptedDataContainer());
                }

                return nickName;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getPhoneNumber() {
        synchronized (this) {
            try {
                if (phoneNumber == null) {
                    String phone = JsonUtil.stringFromJO(DataContainer.PHONE, getDecryptedDataContainer());
                    if (PhoneNumberUtil.isNormalizedPhoneNumber(phone)) {
                        phoneNumber = phone;
                    }
                }

                return phoneNumber;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getSIMSmeID() {
        synchronized (this) {
            try {
                if (simsmeID == null) {
                    //Ab 2.2 steht in dem Feld PHONE die SIMSme ID drin
                    String id = JsonUtil.stringFromJO(DataContainer.PHONE, getDecryptedDataContainer());
                    if (!StringUtil.isNullOrEmpty(id) && !PhoneNumberUtil.isNormalizedPhoneNumber(id)
                            && id.length() == 8) {
                        simsmeID = id;
                    }
                }

                return simsmeID;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getGroupGuid() {
        synchronized (this) {
            try {
                if (groupGuid == null) {
                    groupGuid = JsonUtil.stringFromJO(DataContainer.GROUP_GUID, getDecryptedDataContainer());
                }
                return groupGuid;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getGroupName() {
        synchronized (this) {
            try {
                if (groupName == null) {
                    groupName = JsonUtil.stringFromJO(DataContainer.GROUP_NAME, getDecryptedDataContainer());
                }
                return groupName;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public byte[] getGroupImage() {
        synchronized (this) {
            try {
                if (groupImage == null) {
                    final String base64ImageString = JsonUtil.stringFromJO(DataContainer.GROUP_IMAGE, getDecryptedDataContainer());

                    if (!StringUtil.isNullOrEmpty(base64ImageString)) {
                        groupImage = Base64.decode(base64ImageString, Base64.DEFAULT);
                    }
                }
                return groupImage;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public VCard getVCard() {
        if (isNotDecrypted()) {
            return null;
        }

        if (!StringUtil.isEqual(getContentType(), MimeType.TEXT_V_CARD)) {
            throwMimeTypeException();
        }

        synchronized (this) {
            try {
                if (vCard == null) {
                    final String vCardString = JsonUtil.stringFromJO(DataContainer.CONTENT, getDecryptedDataContainer());
                    if (!StringUtil.isNullOrEmpty(vCardString)) {
                        vCard = Ezvcard.parse(vCardString).first();
                    }
                }

                return vCard;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getProfilKey() {
        synchronized (this) {
            try {
                if (mProfilKey == null) {
                    mProfilKey = JsonUtil.stringFromJO(DataContainer.PROFIL_KEY, getDecryptedDataContainer());
                }

                return mProfilKey;
            } catch (UnsupportedOperationException | LocalizedException e) {
                LogUtil.w(this.getClass().getName(), e.getMessage(), e);

                return null;
            }
        }
    }

    @Nullable
    public String getFilename() {
        if (isNotDecrypted()) {
            return null;
        }

        synchronized (this) {
            try {
                if (mFilename == null) {
                    if (!StringUtil.isEqual(getContentType(), MimeType.APP_OCTET_STREAM)) {
                        throwMimeTypeException();
                    }

                    mFilename = JsonUtil.stringFromJO(DataContainer.FILE_NAME, getDecryptedDataContainer());
                }

                return mFilename;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getFileMimetype() {
        if (isNotDecrypted()) {
            return null;
        }

        synchronized (this) {
            try {
                if (mFileMimetype == null) {
                    if (!StringUtil.isEqual(getContentType(), MimeType.APP_OCTET_STREAM)) {
                        throwMimeTypeException();
                    }
                    mFileMimetype = JsonUtil.stringFromJO(DataContainer.FILE_TYPE, getDecryptedDataContainer());
                }

                return mFileMimetype;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    @Nullable
    public String getFileSize() {
        if (isNotDecrypted()) {
            return null;
        }

        synchronized (this) {
            try {
                if (mFilesize == null) {
                    if (!StringUtil.isEqual(getContentType(), MimeType.APP_OCTET_STREAM)) {
                        throwMimeTypeException();
                    }
                    mFilesize = JsonUtil.stringFromJO(DataContainer.FILE_SIZE, getDecryptedDataContainer());
                }

                return mFilesize;
            } catch (LocalizedException e) {

                return null;
            }
        }
    }

    public int getAttachmentEncodingVersion() {
        if (isNotDecrypted()) {
            return -1;
        }

        synchronized (this) {
            try {
                int returnValue = ATTACHMENT_ENCODING_VERSION_DEFAULT;

                if (mAttachmentEncodingVersion == null) {
                    mAttachmentEncodingVersion = JsonUtil.stringFromJO(DataContainer.ENCODING_VERSION, getDecryptedDataContainer());
                }

                if (mAttachmentEncodingVersion != null) {
                    try {
                        returnValue = Integer.parseInt(mAttachmentEncodingVersion);
                    } catch (NumberFormatException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    }
                }

                return returnValue;
            } catch (LocalizedException e) {

                return -1;
            }
        }
    }

    private void throwMimeTypeException() {
        throw new RuntimeException(EXCEPTION_PREFIX + contentType);
    }
}
