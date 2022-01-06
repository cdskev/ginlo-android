// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller.message;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Base64;
import com.google.gson.*;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.OnLoadPublicKeyListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.SignatureModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.SignatureModelDeserializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public abstract class MessageDataResolver {

    private static final int CHECK_SIGNATURE_TIMEOUT = 3;

    protected final SimsMeApplication mApplication;
    private final Gson gson;
    private HashMap<String, String> nameCache;

    protected MessageDataResolver(final SimsMeApplication application) {
        this.nameCache = new HashMap<>();

        this.mApplication = application;

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(SignatureModel.class, new SignatureModelDeserializer());
        gson = gsonBuilder.create();
    }

    public static String getGroupGuidForMessage(Message message) {

        return ((message.getType() == Message.TYPE_GROUP) || (message.getType() == Message.TYPE_CHANNEL))
                ? message.getTo() : null;
    }

    private static String getGuidForPrivateMessage(Message message) {
        if (message == null) {
            return null;
        }

        return ((message.getIsSentMessage() != null) && (message.getIsSentMessage())) ? message.getTo()
                : message.getFrom();
    }

    /**
     * Gets Chat Guid from Message
     *
     * @param message The message
     * @return The Guid of the Chat
     */
    public static String getGuidForMessage(Message message) {
        if (message == null) {
            return null;
        }

        final String guid;

        switch (message.getType()) {
            case Message.TYPE_CHANNEL:
            case Message.TYPE_GROUP: {
                guid = getGroupGuidForMessage(message);
                break;
            }
            case Message.TYPE_PRIVATE: {
                guid = getGuidForPrivateMessage(message);
                break;
            }
            default:
                guid = null;
        }

        return guid;
    }

    protected void clearCache() {
        clearNameCache();
    }

    /**
     * clearNameCache
     */
    public void clearNameCache() {
        this.nameCache = new HashMap<>();
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public String getTitleForMessage(DecryptedMessage decryptedMsg)
            throws LocalizedException {
        String titleText = "";

        if ((decryptedMsg != null) && (decryptedMsg.getMessage().getType() == Message.TYPE_PRIVATE)) {
            titleText = getNameForMessage(decryptedMsg, true);
        }
        return titleText;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public String getTitleForChat(String guid)
            throws LocalizedException {
        return getChatTitle(guid, null);
    }

    public String getSignatureFailedText() {
        return mApplication.getResources().getString(R.string.chat_encryption_signatureIsInvalid);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public String getPreviewTextForMessage(DecryptedMessage decryptedMsg, boolean prependNickname)
            throws LocalizedException {
        return getPreviewTextForMessage(decryptedMsg, true, prependNickname);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public String getPreviewTextForMessage(DecryptedMessage decryptedMsg, boolean checkSignature, boolean prependNickname)
            throws LocalizedException {
        String previewText = "";

        if (decryptedMsg == null) {
            return previewText;
        }

        boolean isSentMessage = (decryptedMsg.getMessage().getIsSentMessage() != null)
                ? decryptedMsg.getMessage().getIsSentMessage() : false;
        Resources resources = mApplication.getResources();
        String contentType = decryptedMsg.getContentType();

        if (checkSignature) {
            boolean valid;
            if (decryptedMsg.getMessage() != null && decryptedMsg.getMessage().getSignatureSha256() != null) {
                valid = checkSignatureSha256(decryptedMsg.getMessage());
            } else {
                valid = checkSignature(decryptedMsg.getMessage());
            }

            if (!valid) {
                throw new LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, "Check Message signature failed.");
            }
        }

        String nickname = decryptedMsg.getNickName();
        if (decryptedMsg.getMessageDestructionParams() != null) {
            previewText = mApplication.getResources().getString(R.string.chat_selfdestruction_preview);
        } else if (!StringUtil.isNullOrEmpty(contentType)) {
            switch (contentType) {
                case MimeType.TEXT_PLAIN:
                    previewText = decryptedMsg.getText();
                    break;
                case MimeType.TEXT_RSS:
                    previewText = decryptedMsg.getText();
                    nickname = null;
                    break;
                case MimeType.IMAGE_JPEG:
                    previewText = isSentMessage ? resources.getString(R.string.chat_overview_preview_imageSent)
                            : resources.getString(R.string.chat_overview_preview_imageReceived);
                    break;
                case MimeType.VIDEO_MPEG:
                    previewText = isSentMessage ? resources.getString(R.string.chat_overview_preview_videoSent)
                            : resources.getString(R.string.chat_overview_preview_videoReceived);
                    break;
                case MimeType.AUDIO_MPEG:
                    previewText = isSentMessage ? resources.getString(R.string.chat_overview_preview_VoiceSent)
                            : resources.getString(R.string.chat_overview_preview_VoiceReceived);
                    break;
                case MimeType.MODEL_LOCATION:
                    previewText = isSentMessage ? resources.getString(R.string.chat_overview_preview_locationSent)
                            : resources.getString(R.string.chat_overview_preview_locationReceived);
                    break;
                case MimeType.TEXT_V_CARD:
                    previewText = isSentMessage ? resources.getString(R.string.chat_overview_preview_contactSent)
                            : resources.getString(R.string.chat_overview_preview_contactReceived);
                    break;
                case MimeType.APP_OCTET_STREAM:
                    previewText = isSentMessage ? resources.getString(R.string.chat_overview_preview_file_sent)
                            : resources.getString(R.string.chat_overview_preview_file_received);
                    break;
                default:
                    break;
            }
        }
        if (!StringUtil.isNullOrEmpty(nickname) && prependNickname && !StringUtil.isNullOrEmpty(previewText)) {
            return nickname + ": " + previewText;
        }
        return previewText;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public String getNameForMessage(DecryptedMessage message)
            throws LocalizedException {
        return getNameForMessage(message, false);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private String getNameForMessage(DecryptedMessage decryptedMsg,
                                     boolean ignoreOwnName)
            throws LocalizedException {
        if ((decryptedMsg == null) || (decryptedMsg.getMessage() == null)) {
            return null;
        }

        if ((decryptedMsg.getMessage().getIsSentMessage()) && (!ignoreOwnName)) {
            AccountController accountController = mApplication.getAccountController();
            return accountController.getAccount().getName();
        }

        String guid = getGuidForMessage(decryptedMsg.getMessage());

        if (guid == null) {
            if (decryptedMsg.getMessage().getType() == Message.TYPE_CHANNEL) {
                guid = decryptedMsg.getMessage().getTo();
            } else {
                return null;
            }
        }

        if (decryptedMsg.getMessage().getType() == Message.TYPE_GROUP && !StringUtil.isNullOrEmpty(decryptedMsg.getMessage().getFrom())) {
            String name = nameCache.get(decryptedMsg.getMessage().getFrom());
            if (StringUtil.isNullOrEmpty(name)) {
                ContactController contactController = mApplication.getContactController();
                Contact contact = contactController.createContactIfNotExists(decryptedMsg.getMessage().getFrom(), decryptedMsg);

                if (contact != null) {
                    name = contact.getName();

                    if (!StringUtil.isNullOrEmpty(name)) {
                        nameCache.put(decryptedMsg.getMessage().getFrom(), name);
                    }
                }
            }

            return name;
        } else {
            return getChatTitle(guid, decryptedMsg);
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private String getChatTitle(String guid,
                                DecryptedMessage decryptedMsg)
            throws LocalizedException {
        String name = nameCache.get(guid);

        if (StringUtil.isNullOrEmpty(name)) {
            ContactController contactController = mApplication.getContactController();
            if (GuidUtil.isChatChannel(guid)) {
                ChannelController channelController = mApplication.getChannelController();
                Channel channel = channelController.getChannelFromDB(guid);

                name = channel.getShortDesc();

                if (!StringUtil.isNullOrEmpty(name)) {
                    nameCache.put(guid, name);
                }

                return name;
            }

            Contact contact = contactController.getContactByGuid(guid);

            if (guid.equals(AppConstants.GUID_SYSTEM_CHAT)) {
                name = mApplication.getString(R.string.chat_system_nickname);
                nameCache.put(guid, name);
            } else if (contact == null) {
                if (decryptedMsg != null) {
                    name = (decryptedMsg.getNickName() != null) ? decryptedMsg.getNickName()
                            : decryptedMsg.getMessage().getFrom();
                }
            } else {
                name = contact.getName();

                if (StringUtil.isNullOrEmpty(name)) {
                    name = contact.getNickname();
                }

                /* Wenn die Kontakt Telefonnummer oder Nickname nicht bekannt ist, wird sie erfasst */
                if ((decryptedMsg != null) && (!isSentMessage(decryptedMsg.getMessage()))) {
                    try {
                        boolean saveContact = false;

                        final String phoneNumber = contact.getPhoneNumber();
                        if (StringUtil.isNullOrEmpty(phoneNumber) && !StringUtil.isNullOrEmpty(decryptedMsg.getPhoneNumber())) {
                            saveContact = true;
                            contact.setPhoneNumber(decryptedMsg.getPhoneNumber());
                        }

                        if (StringUtil.isNullOrEmpty(contact.getSimsmeId()) && !StringUtil.isNullOrEmpty(decryptedMsg.getSIMSmeID())) {
                            saveContact = true;
                            contact.setSimsmeId(decryptedMsg.getSIMSmeID());
                        }

                        if (StringUtil.isNullOrEmpty(contact.getNickname())) {
                            saveContact = true;
                            contact.setNickname(decryptedMsg.getNickName());
                        }

                        if (saveContact) {
                            contactController.insertOrUpdateContact(contact);

                            //nochmal setzen, da ja jetzt neue daten vorhanden sein sollten
                            name = contact.getName();
                        }

                        if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())
                                && !StringUtil.isNullOrEmpty(decryptedMsg.getProfilKey())) {
                            contactController.setProfilInfoAesKey(contact.getAccountGuid(), decryptedMsg.getProfilKey(), decryptedMsg);
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    }
                }

                if (!StringUtil.isNullOrEmpty(name)) {
                    nameCache.put(guid, name);
                }
            }
        }

        return name;
    }

    private boolean isSentMessage(Message msg) {
        return (msg.getIsSentMessage() != null) && (msg.getIsSentMessage());
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public int getStateForContact(String contactGuid)
            throws LocalizedException {
        if (contactGuid.equals(AppConstants.GUID_SYSTEM_CHAT)) {
            return Contact.STATE_SIMSME_SYSTEM;
        }

        ContactController contactController = mApplication.getContactController();
        Contact contact = contactController.getContactByGuid(contactGuid);

        if ((contact != null) && (contact.getState() != null)) {
            return contact.getState();
        } else {
            return Contact.STATE_LOW_TRUST;
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public int getStateForMessage(Message message)
            throws LocalizedException {
        if (message.getIsSentMessage()) {
            return Contact.STATE_UNSIMSABLE;
        } else {
            String guid = message.getFrom();

            if (guid.equals(AppConstants.GUID_SYSTEM_CHAT)) {
                return Contact.STATE_SIMSME_SYSTEM;
            }

            ContactController contactController = mApplication.getContactController();
            Contact contact = contactController.getContactByGuid(guid);

            if ((contact != null) && (contact.getState() != null)) {
                return contact.getState();
            } else {
                return Contact.STATE_LOW_TRUST;
            }
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Bitmap getProfileImageForMessage(Message message)
            throws LocalizedException {
        return getProfileImageForMessage(message.getFrom());
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Bitmap getProfileImageForMessage(final String guid)
            throws LocalizedException {
        ChatImageController chatImageController = mApplication.getChatImageController();
        return chatImageController.getImageByGuid(guid, ChatImageController.SIZE_CHAT);
    }

    /**
     * Check signature und added das Ergebnis an der Message
     *
     * @param message
     * @return
     * @throws LocalizedException
     */
    public boolean checkSignature(Message message) throws LocalizedException {
        if (message == null) {
            throw new LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, "No message");
        }

        if (message.getIsSignatureValid() != null && message.getIsSignatureValid()) {
            return true;
        }

        if (StringUtil.isEqual(message.getServerMimeType(), MimeType.TEXT_RSS)) {
            final JsonObject decryptedDataContainer = message.getDecryptedDataContainer();
            if (decryptedDataContainer != null) {
                final JsonElement contentType = decryptedDataContainer.get("Content-Type");
                if (contentType != null && contentType.isJsonPrimitive() && StringUtil.isEqual(contentType.getAsString(), "text/rss")) {
                    // RSS-Nachrichten vom Cockpit haben keine Signature
                    message.setIsSignatureValid(true);
                    mApplication.getMessageController().saveMessage(message);
                    return true;
                }
            }
        }

        if (Message.TYPE_GROUP_INVITATION == message.getType()) {
            final JsonObject decryptedDataContainer = message.getDecryptedDataContainer();
            if (decryptedDataContainer != null && JsonUtil.hasKey("Content-Type", decryptedDataContainer)) {
                final JsonElement contentType = decryptedDataContainer.get("Content-Type");
                if (contentType.getAsString().endsWith("+encrypted")) {
                    // gemanagte und restricted  Einladungen haben keine Signatur
                    message.setIsSignatureValid(true);
                    mApplication.getMessageController().saveMessage(message);
                    return true;
                }
            }
        }

        try {
            SignatureModel signatureModel = gson.fromJson(new String(message.getSignature(), StandardCharsets.UTF_8), SignatureModel.class);

            boolean matchingHashes = signatureModel.checkMessage(message, false);

            String conatString = signatureModel.getCombinedHashes();

            byte[] originalSignatureBytes = Base64.decode(signatureModel.getSignature(), Base64.DEFAULT);
            byte[] calculatedSignatureBytes = conatString.getBytes(StandardCharsets.UTF_8);

            ContactController contactController = mApplication.getContactController();

            PublicKey normalKey = null;
            PublicKey systemChatKey = XMLUtil.getPublicKeyFromXML(AppConstants.PUBLICKEY_SYSTEM_CHAT);

            AccountController accountController = mApplication.getAccountController();
            if (message.getFrom().equals(accountController.getAccount().getAccountGuid())
                    || message.getFrom().equals(AppConstants.GUID_SYSTEM_CHAT)) {
                normalKey = mApplication.getKeyController().getUserKeyPair().getPublic();
            } else {
                normalKey = contactController.getPublicKeyForContact(message.getFrom());
                systemChatKey = null;

                if (normalKey == null) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
                        @Override
                        public void onLoadPublicKeyError(String message) {
                            latch.countDown();
                        }

                        @Override
                        public void onLoadPublicKeyComplete(Contact contact) {
                            latch.countDown();
                        }
                    };
                    contactController.loadPublicKey(message.getFrom(), onLoadPublicKeyListener);
                    if (!latch.await(CHECK_SIGNATURE_TIMEOUT, TimeUnit.SECONDS)) {
                        return true;
                    }
                    normalKey = contactController.getPublicKeyForContact(message.getFrom());
                }
            }

            boolean verifyUser = SecurityUtil.verifyData(normalKey, originalSignatureBytes,
                    calculatedSignatureBytes, false);

            if (normalKey == null) {
                // Nutzer hat sich gelöscht, keine Möglichkeit mehr die Signatur zu prüfen
                verifyUser = true;
            }
            boolean verifySystemChat = SecurityUtil.verifyData(systemChatKey, originalSignatureBytes,
                    calculatedSignatureBytes, false);

            boolean isSignatureValid = matchingHashes && (verifyUser || verifySystemChat);

            message.setIsSignatureValid(isSignatureValid);

            mApplication.getMessageController().saveMessage(message);

            return isSignatureValid;
        } catch (JsonSyntaxException | NullPointerException | InterruptedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, e);
        }
    }

    /**
     * Check signature und added das Ergebnis an der Message
     *
     * @param message
     * @return
     * @throws LocalizedException
     */
    public boolean checkSignatureSha256(Message message) throws LocalizedException {
        if (message == null) {
            throw new LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, "No message");
        }
        if (message.getSignatureSha256() == null) {
            return false;
        }

        if (message.getIsSignatureValid() != null && message.getIsSignatureValid()) {
            return true;
        }

        try {
            String signature = null;
            signature = new String(message.getSignatureSha256(), StandardCharsets.UTF_8);
            SignatureModel signatureModel = gson.fromJson(signature, SignatureModel.class);

            boolean matchingHashes = signatureModel.checkMessage(message, true);
            String conatString = signatureModel.getCombinedHashes();
            byte[] originalSignatureBytes = Base64.decode(signatureModel.getSignature(), Base64.DEFAULT);

            if ((originalSignatureBytes == null) || (originalSignatureBytes.length == 0)) {
                throw new LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, "No signature");
            }

            byte[] calculatedSignatureBytes = conatString.getBytes(StandardCharsets.UTF_8);

            ContactController contactController = mApplication.getContactController();

            PublicKey normalKey = null;
            PublicKey systemChatKey = null;
            AccountController accountController = mApplication.getAccountController();

            boolean verifyUser = false;

            if (message.getFrom().equals(accountController.getAccount().getAccountGuid())
                    || message.getFrom().equals(AppConstants.GUID_SYSTEM_CHAT)) {
                normalKey = mApplication.getKeyController().getUserKeyPair().getPublic();
                verifyUser = SecurityUtil.verifyData(normalKey, originalSignatureBytes, calculatedSignatureBytes, true);
                systemChatKey = contactController.getPublicKeyForContact(AppConstants.GUID_SYSTEM_CHAT);
            } else {
                normalKey = contactController.getPublicKeyForContact(message.getFrom());

                if (normalKey == null) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
                        @Override
                        public void onLoadPublicKeyError(String message) {
                            latch.countDown();
                        }

                        @Override
                        public void onLoadPublicKeyComplete(Contact contact) {
                            latch.countDown();
                        }
                    };
                    contactController.loadPublicKey(message.getFrom(), onLoadPublicKeyListener);
                    if (!latch.await(CHECK_SIGNATURE_TIMEOUT, TimeUnit.SECONDS)) {
                        return true;
                    }
                    normalKey = contactController.getPublicKeyForContact(message.getFrom());
                }
                if (normalKey != null) {
                    verifyUser = SecurityUtil.verifyData(normalKey, originalSignatureBytes, calculatedSignatureBytes, true);
                } else {
                    // Nutzer hat sich abgemeldet --> keine Chance mehr zu prüfen
                    return true;
                }
            }

            boolean verifySystemChat = SecurityUtil.verifyData(systemChatKey, originalSignatureBytes,
                    calculatedSignatureBytes, true);

            boolean isSignatureValid = matchingHashes && (verifyUser || verifySystemChat);

            // bei einer unverifizierten sha256-signatur duerfen wir valid nicht auf false setzen
            message.setIsSignatureValid(isSignatureValid);

            mApplication.getMessageController().saveMessage(message);

            return isSignatureValid;
        } catch (JsonSyntaxException | NullPointerException | InterruptedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, e);
        }
    }

    public String getText(int resId) {
        return mApplication.getText(resId).toString();
    }
}
