// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.util.MessageDaoHelper;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MessageDeserializer implements JsonDeserializer<Message> {
    private final AccountController accountController;

    public MessageDeserializer(final AccountController accountController) {
        super();
        this.accountController = accountController;
    }

    @Override
    public Message deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        if (jsonElement == null || !jsonElement.isJsonObject()) {
            return null;
        }

        JsonObject jo = jsonElement.getAsJsonObject();
        Set<Map.Entry<String, JsonElement>> entries = jo.entrySet();

        if (entries == null || entries.isEmpty()) {
            return null;
        }

        Iterator<Map.Entry<String, JsonElement>> it = entries.iterator();
        if (!it.hasNext()) {
            return null;
        }

        Map.Entry<String, JsonElement> messageEntry = it.next();

        String key = messageEntry.getKey();

        if (StringUtil.isNullOrEmpty(key)) {
            return null;
        }

        int msgType = MessageDaoHelper.getMessageType(key);

        JsonElement je = messageEntry.getValue();

        if (je == null || !je.isJsonObject()) {
            return null;
        }

        JsonObject msgJO = (JsonObject) je;

        Message message = new Message();

        MessageDaoHelper.setMessageAttributes(message, msgJO, msgType, accountController.getAccount().getAccountGuid());

        return message;
    }
}
