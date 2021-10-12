// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.message.ChannelChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AESKeyDataContainer;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.util.IOSMessageConversionUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;
import org.json.JSONObject;

public class MessageDecryptionController {
    private final SimsMeApplication mApplication;

    // (map)key: RSA verschluesselter key2, value: entschlusselter AES-Key
    private final Map<String, byte[]> mKey2AesKeyMap = new HashMap<>();

    public MessageDecryptionController(final SimsMeApplication application) {
        mApplication = application;
    }


    // KS: Returns null if only key2 is available in key container.
    // Bad as key2 should be the first choice!
    // TODO: Investigate this!
    public @Nullable
    DecryptedMessage decryptMessage(Message message,
                                    boolean returnObjIfMsgHasNoData)
            throws LocalizedException {
        if (message == null || (message.getData() == null && !returnObjIfMsgHasNoData)) {
            return null;
        }

        if (message.isMessageDecrypted() || (message.getData() == null && returnObjIfMsgHasNoData)) {
            return new DecryptedMessage(message);
        }

        if (message.getType() == Message.TYPE_GROUP) {
            AESKeyDataContainer aesKeyDataContainer = getGroupChatAESKeyData(message);

            return decryptMessage(message, aesKeyDataContainer);
        } else if (message.getType() == Message.TYPE_CHANNEL) {
            AESKeyDataContainer aesKeyDataContainer = getChannelChatAESKeyData(message);

            return decryptMessage(message, aesKeyDataContainer);
        } else {
            AESKeyDataContainer aesKeyDataContainer = getSingleChatAESKeyData(message);

            return decryptMessage(message, aesKeyDataContainer);
        }
    }

    private DecryptedMessage decryptMessage(Message message,
                                            AESKeyDataContainer aesKeyDataContainer)
            throws LocalizedException {
        synchronized (this) {
            JsonParser parser = new JsonParser();

            if (aesKeyDataContainer == null) {
                return null;
            }

            byte[] data = (message.getType() == Message.TYPE_GROUP)
                    ? message.getDataEncryptedContent() : message.getData();

            byte[] decryptedData = SecurityUtil.decryptMessageWithAES(data, aesKeyDataContainer.getAesKey(),
                    aesKeyDataContainer.getIv());
            JsonObject decryptedDataContainer = parser.parse(new String(decryptedData, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            message.setDecryptedDataContainer(decryptedDataContainer);
            message.setAesKeyDataContainer(aesKeyDataContainer);

            return new DecryptedMessage(message);
        }
    }

    void clearAesCache() {
        mKey2AesKeyMap.clear();
    }

    private AESKeyDataContainer getSingleChatAESKeyData(Message message)
            throws LocalizedException {
        AESKeyDataContainer aesKeyDataContainer = message.getAesKeyDataContainer();
        KeyController keyController = mApplication.getKeyController();

        if (aesKeyDataContainer != null) {
            return aesKeyDataContainer;
        }

        try {
            byte[] ivBytes;
            byte[] aesKeyBytes;

            String key2 = message.getIsSentMessage() ? message.getEncryptedFromKey2() : message.getEncryptedToKey2();

            //wenn ein key2 vorhanden ist
            if (key2 != null) {
                synchronized (mKey2AesKeyMap) {
                    String iv2 = message.getKey2Iv();

                    if (StringUtil.isNullOrEmpty(iv2)) {
                        return null;
                    }
                    try {
                        ivBytes = Base64.decode(iv2, Base64.NO_WRAP);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }

                    //wenn der RSA-Key schon vorhandne ist, koennen wir den AES-key aus der Map nutzen
                    aesKeyBytes = mKey2AesKeyMap.get(key2);

                    // wenn nicht, packen wir ihn in die map, sofern vorhanden
                    if (aesKeyBytes == null) {
                        byte[] key2bytes = Base64.decode(key2, Base64.NO_WRAP);

                        byte[] decodedDecryptedContainer2Bytes = SecurityUtil.decryptMessageWithRSA(key2bytes, keyController.getUserKeyPair().getPrivate());
                        if (decodedDecryptedContainer2Bytes == null) {
                            return null;
                        }

                        aesKeyBytes = Base64.decode(decodedDecryptedContainer2Bytes, Base64.NO_WRAP);
                        mKey2AesKeyMap.put(key2, aesKeyBytes);
                    }
                }
            } else {
                byte[] decodedEncryptedContainerBytes = message.getIsSentMessage() ? message.getEncryptedFromKey() : message.getEncryptedToKey();
                byte[] decodedDecryptedContainerBytes = SecurityUtil.decryptMessageWithRSA(decodedEncryptedContainerBytes, keyController.getUserKeyPair().getPrivate());

                if (decodedDecryptedContainerBytes == null) {
                    return null;
                }

                String decodedDecryptedContainerString = new String(decodedDecryptedContainerBytes, StandardCharsets.UTF_8);
                JSONObject aesKeyContainer = IOSMessageConversionUtil.convertToJSON(decodedDecryptedContainerString);

                final String FIELD_KEY = "key";
                final String FIELD_IV = "iv";

                if ((!aesKeyContainer.has(FIELD_KEY)) || (!aesKeyContainer.has(FIELD_IV))) {
                    return null;
                }

                aesKeyBytes = Base64.decode(aesKeyContainer.getString(FIELD_KEY), Base64.NO_WRAP);
                ivBytes = Base64.decode(aesKeyContainer.getString(FIELD_IV), Base64.NO_WRAP);

                //TODO wenn neuer key kommt schlaegt entschluesselung fehl
            }

            aesKeyDataContainer = new AESKeyDataContainer(new SecretKeySpec(aesKeyBytes, "AES"),
                    new IvParameterSpec(ivBytes));
        } catch (JSONException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.GET_AES_KEY_DATA_FAILED, e);
        }
        return aesKeyDataContainer;
    }

    private AESKeyDataContainer getGroupChatAESKeyData(Message message)
            throws LocalizedException {
        AESKeyDataContainer aesKeyDataContainer = message.getAesKeyDataContainer();

        if (aesKeyDataContainer == null) {
            GroupChatController groupChatController = mApplication.getGroupChatController();
            final Chat chat = groupChatController.getChatByGuid(MessageDataResolver.getGroupGuidForMessage(message));

            if (chat == null) {
                return null;
            }

            if (chat.getChatAESKey() == null) {
                LogUtil.w(this.getClass().getName(), "For some strange reasons the key of the chat is null. Chat Id=" + chat.getChatGuid());
                return null;
            }

            IvParameterSpec iv = new IvParameterSpec((message.getDataIv() != null) ? message.getDataIv() : chat.getIv());

            aesKeyDataContainer = new AESKeyDataContainer(chat.getChatAESKey(), iv);
        }

        return aesKeyDataContainer;
    }

    private AESKeyDataContainer getChannelChatAESKeyData(Message message)
            throws LocalizedException {
        AESKeyDataContainer aesKeyDataContainer = message.getAesKeyDataContainer();

        if (aesKeyDataContainer == null) {
            ChannelChatController channelChatController = mApplication.getChannelChatController();
            aesKeyDataContainer = channelChatController.getEncryptionData(message.getTo());
        }

        return aesKeyDataContainer;
    }
}
