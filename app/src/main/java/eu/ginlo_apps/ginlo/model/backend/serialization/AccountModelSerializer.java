// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.AccountModel;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class AccountModelSerializer
        implements JsonSerializer<AccountModel> {

    @Override
    public JsonElement serialize(AccountModel accountModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject accountJsonObject = new JsonObject();

        if (!StringUtil.isNullOrEmpty(accountModel.guid)) {
            accountJsonObject.addProperty("guid", accountModel.guid);
        }
        if (!StringUtil.isNullOrEmpty(accountModel.publicKey)) {
            accountJsonObject.addProperty("publicKey", accountModel.publicKey);
        }
        if (!StringUtil.isNullOrEmpty(accountModel.phone)) {
            accountJsonObject.addProperty("phone", accountModel.phone);
        }
        if (!StringUtil.isNullOrEmpty(accountModel.email)) {
            accountJsonObject.addProperty("email", accountModel.email);
        }

        //Backup
        if (!StringUtil.isNullOrEmpty(accountModel.privateKey)) {
            accountJsonObject.addProperty("privateKey", accountModel.privateKey);
        }
        jsonObject.add("Account", accountJsonObject);
        return jsonObject;
    }
}
