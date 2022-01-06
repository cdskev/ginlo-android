// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import android.util.Base64;
import com.google.gson.*;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * Created by Florian on 08.06.16.
 */
public class ChatBackupDeserializer implements JsonDeserializer<Chat> {

    @Override
    public Chat deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        try {
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                return null;
            }

            JsonObject jo = jsonElement.getAsJsonObject();

            int chatType = -1;
            JsonObject chatJO = null;

            String roomType = null;
            if (JsonUtil.hasKey(JsonConstants.BACKUP_CHAT_SINGLE, jo)) {
                chatJO = jo.getAsJsonObject(JsonConstants.BACKUP_CHAT_SINGLE);
                chatType = Chat.TYPE_SINGLE_CHAT;
            } else if (JsonUtil.hasKey(JsonConstants.BACKUP_CHAT_GROUP, jo)) {
                chatJO = jo.getAsJsonObject(JsonConstants.BACKUP_CHAT_GROUP);
                chatType = Chat.TYPE_GROUP_CHAT;
                roomType = JsonUtil.stringFromJO("type", chatJO);
            }

            if (chatType < 0 || chatJO == null) {
                return null;
            }

            String guid = JsonUtil.stringFromJO("guid", chatJO);

            if (StringUtil.isNullOrEmpty(guid)) {
                return null;
            }

            String owner = JsonUtil.stringFromJO("owner", chatJO);
            String name = JsonUtil.stringFromJO("name", chatJO);

            byte[] imageBytes = null;
            String imageBytesAsString = JsonUtil.stringFromJO("groupImage", chatJO);
            if (!StringUtil.isNullOrEmpty(imageBytesAsString)) {
                imageBytes = Base64.decode(imageBytesAsString, Base64.NO_WRAP);
            }

            JsonArray member = null;

            if (JsonUtil.hasKey("member", chatJO)) {
                JsonElement je = chatJO.get("member");
                if (je.isJsonArray()) {
                    member = (JsonArray) je;
                }
            }

            String aesKeyBase64 = JsonUtil.stringFromJO("aes_key", chatJO);
            String iv = JsonUtil.stringFromJO("iv", chatJO);
            if (StringUtil.isEqual(iv, "ivDummy")) {
                iv = null;
            }
            String lastModifiedDate = JsonUtil.stringFromJO("lastModifiedDate", chatJO);
            String invitedDate = JsonUtil.stringFromJO("invitedDate", chatJO);
            boolean isConfirmed = true;

            // Check for pending chat invitations to keep pending state
            if (JsonUtil.hasKey("confirmed", chatJO)) {
                isConfirmed = !StringUtil.isEqual(JsonUtil.stringFromJO("confirmed", chatJO), "false");
            }

            // Only reset chatType to invitation if we don't have managed groups which are mandatory
            if (roomType == null || StringUtil.isEqual(roomType, Chat.ROOM_TYPE_STD)
                    || StringUtil.isEqual(roomType, Chat.ROOM_TYPE_ANNOUNCEMENT)) {
                if (!StringUtil.isNullOrEmpty(invitedDate) || !isConfirmed) {
                    if (chatType == Chat.TYPE_GROUP_CHAT) {
                        chatType = Chat.TYPE_GROUP_CHAT_INVITATION;
                    } else {
                        chatType = Chat.TYPE_SINGLE_CHAT_INVITATION;
                    }
                }
            }

            Chat chat = new Chat(null, guid, chatType, null, null, null, null);

            if (!StringUtil.isNullOrEmpty(owner)) {
                chat.setOwner(owner);
            }

            if (member != null) {
                chat.setMembers(member);
            }

            if (!StringUtil.isNullOrEmpty(aesKeyBase64)) {
                chat.setChatAESKeyAsBase64(aesKeyBase64);

                if (!StringUtil.isNullOrEmpty(iv)) {
                    byte[] ivBytes = Base64.decode(iv, Base64.DEFAULT);
                    chat.setIv(ivBytes);
                }
            }

            if (!StringUtil.isNullOrEmpty(lastModifiedDate)) {
                chat.setLastChatModifiedDate(DateUtil.utcStringToMillis(lastModifiedDate));
            }

            if (!StringUtil.isNullOrEmpty(name)) {
                chat.setTitle(name);
            }

            if (imageBytes != null) {
                chat.setGroupChatImage(imageBytes);
            }

            if (!StringUtil.isNullOrEmpty(roomType)) {
                chat.setRoomType(roomType);
            }

            return chat;
        } catch (LocalizedException e) {
            return null;
        }
    }
}
