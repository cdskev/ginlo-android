// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.model.backend.ChatRoomModel;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChatRoomModelDeserializer
        implements JsonDeserializer<ChatRoomModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ChatRoomModel deserialize(JsonElement jsonElement,
                                     Type type,
                                     JsonDeserializationContext context)
            throws JsonParseException {

        final ChatRoomModel chatRoomModel = new ChatRoomModel();
        final JsonObject jsonObject = jsonElement.getAsJsonObject();
        final JsonObject chatRoomObject;

        if (jsonObject.has(Chat.ROOM_TYPE_STD)) {
            chatRoomObject = jsonObject.get(Chat.ROOM_TYPE_STD).getAsJsonObject();
            chatRoomModel.roomType = Chat.ROOM_TYPE_STD;
            setReadOnly(chatRoomObject, chatRoomModel);
        } else if (jsonObject.has(Chat.ROOM_TYPE_ANNOUNCEMENT)) {
            chatRoomObject = jsonObject.get(Chat.ROOM_TYPE_ANNOUNCEMENT).getAsJsonObject();
            chatRoomModel.roomType = Chat.ROOM_TYPE_ANNOUNCEMENT;
        } else if (jsonObject.has(Chat.ROOM_TYPE_MANAGED)) {
            chatRoomObject = jsonObject.get(Chat.ROOM_TYPE_MANAGED).getAsJsonObject();
            chatRoomModel.roomType = Chat.ROOM_TYPE_MANAGED;
            setReadOnly(chatRoomObject, chatRoomModel);
        } else if (jsonObject.has(Chat.ROOM_TYPE_RESTRICTED)) {
            chatRoomObject = jsonObject.get(Chat.ROOM_TYPE_RESTRICTED).getAsJsonObject();
            chatRoomModel.roomType = Chat.ROOM_TYPE_RESTRICTED;
        } else {
            throw new JsonParseException("ChatRoom element not found");
        }

        // Check for writers, if roomType is entitled for that.
        if(chatRoomModel.roomType.equals(Chat.ROOM_TYPE_ANNOUNCEMENT) || chatRoomModel.roomType.equals(Chat.ROOM_TYPE_RESTRICTED)) {
            if (JsonUtil.hasKey("writers", chatRoomObject)) {
                final JsonArray writersJson = chatRoomObject.get("writers").getAsJsonArray();
                if (writersJson != null) {
                    final int size = writersJson.size();
                    final String[] writers = new String[size];
                    for (int i = 0; i < size; ++i) {
                        final JsonElement writer = writersJson.get(i);
                        final String writerString = writer.getAsString();
                        if (!StringUtil.isNullOrEmpty(writerString)) {
                            writers[i] = writerString;
                        }
                    }
                    chatRoomModel.writers = writers;
                }
            }
        }

        chatRoomModel.guid = chatRoomObject.has("guid") ? chatRoomObject.get("guid").getAsString() : null;
        chatRoomModel.owner = chatRoomObject.has("owner") ? chatRoomObject.get("owner").getAsString() : null;
        chatRoomModel.data = (chatRoomObject.has("data") && (!chatRoomObject.get("data").isJsonNull()))
                ? chatRoomObject.get("data").getAsString() : null;
        chatRoomModel.member = context.deserialize(chatRoomObject.get("member"), String[].class);

        chatRoomModel.maxmember = chatRoomObject.has("maxmember") ? Integer.valueOf(chatRoomObject.get("maxmember")
                .getAsString()) : -1;

        if (JsonUtil.hasKey("confirmed", chatRoomObject)) {
            chatRoomModel.confirmed = chatRoomObject.get("confirmed").getAsBoolean();
        }

        if (JsonUtil.hasKey("admins", chatRoomObject)) {
            chatRoomModel.admins = context.deserialize(chatRoomObject.get("admins"), String[].class);
        }

        if (JsonUtil.hasKey("writers", chatRoomObject)) {
            chatRoomModel.writers = context.deserialize(chatRoomObject.get("writers"), String[].class);
        }

        chatRoomModel.pushSilentTill = JsonUtil.hasKey("pushSilentTill", chatRoomObject) ? chatRoomObject.get("pushSilentTill").getAsString() : null;

        chatRoomModel.keyIv = JsonUtil.stringFromJO("key-iv", chatRoomObject);

        return chatRoomModel;
    }

    private void setReadOnly(final JsonObject chatRoomObject,
                             final ChatRoomModel chatRoomModel) {
        if (JsonUtil.hasKey("readOnly", chatRoomObject)) {
            final int readOnly = chatRoomObject.get("readOnly").getAsInt();
            chatRoomModel.isReadonly = readOnly != 0;
        } else {
            chatRoomModel.isReadonly = false;
        }
    }
}
