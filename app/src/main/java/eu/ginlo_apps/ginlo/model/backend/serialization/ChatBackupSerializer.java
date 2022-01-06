// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import android.util.Base64;
import com.google.gson.*;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;
import java.util.Date;

/**
 * Created by Florian on 10.05.16.
 */
public class ChatBackupSerializer implements JsonSerializer<Chat> {
    @Override
    public JsonElement serialize(Chat chat, Type type, JsonSerializationContext jsonSerializationContext) {
        try {
            final JsonObject jsonObject = new JsonObject();
            final JsonObject chatJsonObject = new JsonObject();

            String chatGuid = chat.getChatGuid();
            if (StringUtil.isNullOrEmpty(chatGuid)) {
                return null;
            }

            chatJsonObject.addProperty("guid", chatGuid);

            String owner = chat.getOwner();
            if (!StringUtil.isNullOrEmpty(owner)) {
                chatJsonObject.addProperty("owner", owner);
            }

            JsonArray member = chat.getMembers();

            if (member != null && member.size() > 0) {
                chatJsonObject.add("member", member);
            }

            String name = chat.getTitle();
            if (!StringUtil.isNullOrEmpty(name)) {
                chatJsonObject.addProperty("name", name);
            }

            byte[] image = chat.getGroupChatImage();
            if (image != null) {
                String imageBytesBase64 = Base64.encodeToString(image, Base64.NO_WRAP);
                if (!StringUtil.isNullOrEmpty(imageBytesBase64)) {
                    chatJsonObject.addProperty("groupImage", imageBytesBase64);
                }
            }

            String aesKeyBase64 = chat.getChatAESKeyAsBase64();
            if (!StringUtil.isNullOrEmpty(aesKeyBase64)) {
                chatJsonObject.addProperty("aes_key", aesKeyBase64);

                byte[] ivBytes = chat.getIv();

                if (ivBytes != null) {
                    String ivString = Base64.encodeToString(ivBytes, Base64.DEFAULT);
                    if (!StringUtil.isNullOrEmpty(ivString)) {
                        chatJsonObject.addProperty("iv", ivString);
                    }
                }
            }

            Long lmd = chat.getLastChatModifiedDate();
            long lastModifiedDate = lmd != null ? lmd : 0;

            if (lastModifiedDate > 0) {
                chatJsonObject.addProperty("lastModifiedDate", DateUtil.utcStringFromMillis(lastModifiedDate));
            }

            if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION || chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION) {
                chatJsonObject.addProperty("invitedDate",
                        DateUtil.utcStringFromMillis(lastModifiedDate > 0 ? lastModifiedDate : new Date().getTime()));
            }

            setChatObjectKey(jsonObject, chatJsonObject, chat);

            return jsonObject;
        } catch (LocalizedException e) {
            return null;
        }
    }

    private void setChatObjectKey(final JsonObject jsonObjectParent, final JsonObject chatJsonObject, final Chat chat)
            throws LocalizedException {
        int type = chat.getType();
        switch (type) {
            case Chat.TYPE_SINGLE_CHAT:
            case Chat.TYPE_SINGLE_CHAT_INVITATION: {
                jsonObjectParent.add("SingleChatBackup", chatJsonObject);
                break;
            }
            case Chat.TYPE_GROUP_CHAT:
            case Chat.TYPE_GROUP_CHAT_INVITATION: {
                jsonObjectParent.add("ChatRoomBackup", chatJsonObject);
                chatJsonObject.addProperty("type", chat.getRoomType());
                break;
            }
            default: {
                jsonObjectParent.add("", chatJsonObject);
            }
        }
    }
}
