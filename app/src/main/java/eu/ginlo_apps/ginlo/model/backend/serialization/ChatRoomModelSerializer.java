// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.model.backend.ChatRoomModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChatRoomModelSerializer
        implements JsonSerializer<ChatRoomModel> {

    @Override
    public JsonElement serialize(ChatRoomModel chatRoomModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject chatRoomJsonObject = new JsonObject();

        if (chatRoomModel.guid != null) {
            chatRoomJsonObject.addProperty("guid", chatRoomModel.guid);
        }
        if (chatRoomModel.owner != null) {
            chatRoomJsonObject.addProperty("owner", chatRoomModel.owner);
        }
        if (chatRoomModel.data != null) {
            chatRoomJsonObject.addProperty("data", chatRoomModel.data);
        }
        if (chatRoomModel.member != null) {
            chatRoomJsonObject.add("member", context.serialize(chatRoomModel.member, String[].class));
        }
        if (chatRoomModel.maxmember != 0) {
            chatRoomJsonObject.addProperty("maxmember", chatRoomModel.maxmember);
        }

        if (chatRoomModel.admins != null) {
            chatRoomJsonObject.add("admins", context.serialize(chatRoomModel.admins, String[].class));
        }

        if (chatRoomModel.keyIv != null) {
            chatRoomJsonObject.addProperty("key-iv", chatRoomModel.keyIv);
        }

        if (chatRoomModel.isReadonly) {
            chatRoomJsonObject.addProperty("isReadonly", chatRoomModel.isReadonly);
        }

        if(chatRoomModel.roomType != null && !chatRoomModel.roomType.equals(Chat.ROOM_TYPE_STD)) {
            jsonObject.add(chatRoomModel.roomType, chatRoomJsonObject);
            chatRoomJsonObject.addProperty("roomtype", chatRoomModel.roomType);
        } else {
            jsonObject.add(Chat.ROOM_TYPE_STD, chatRoomJsonObject);
        }
        return jsonObject;
    }
}
