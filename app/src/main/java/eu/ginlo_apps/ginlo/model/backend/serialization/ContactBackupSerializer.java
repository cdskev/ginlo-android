// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * Created by Florian on 18.05.16.
 */
public class ContactBackupSerializer implements JsonSerializer<Contact> {
    @Override
    public JsonElement serialize(Contact contact, Type type, JsonSerializationContext jsonSerializationContext) {
        try {
            final JsonObject jsonObject = new JsonObject();
            final JsonObject contactJsonObject = new JsonObject();

            String guid = contact.getAccountGuid();
            if (StringUtil.isNullOrEmpty(guid)) {
                return null;
            }
            contactJsonObject.addProperty("guid", guid);

            String nickname = contact.getNickname();
            if (!StringUtil.isNullOrEmpty(nickname)) {
                contactJsonObject.addProperty("nickname", nickname);
            }

            String phone = contact.getPhoneNumber();
            if (!StringUtil.isNullOrEmpty(phone)) {
                contactJsonObject.addProperty("phone", phone);
            }

            String publicKey = contact.getPublicKey();
            if (!StringUtil.isNullOrEmpty(publicKey)) {
                contactJsonObject.addProperty("publicKey", publicKey);
            }

            String profileKey = contact.getProfileInfoAesKey();
            if (!StringUtil.isNullOrEmpty(profileKey)) {
                contactJsonObject.addProperty("profileKey", profileKey);
            }

            String mandant = contact.getMandant();
            if (!StringUtil.isNullOrEmpty(mandant)) {
                contactJsonObject.addProperty("mandant", mandant);
            }

            String accountId = contact.getSimsmeId();

            if (!StringUtil.isNullOrEmpty(accountId)) {
                contactJsonObject.addProperty(JsonConstants.ACCOUNT_ID, accountId);
            }

            String firstName = contact.getFirstName();
            if (!StringUtil.isNullOrEmpty(firstName)) {
                contactJsonObject.addProperty(JsonConstants.FIRSTNAME, firstName);
            }

            String name = contact.getLastName();
            if (!StringUtil.isNullOrEmpty(name)) {
                contactJsonObject.addProperty(JsonConstants.NAME, name);
            }

            String email = contact.getEmail();
            if (!StringUtil.isNullOrEmpty(email)) {
                contactJsonObject.addProperty(JsonConstants.EMAIL, email);
            }

            String domain = contact.getDomain();
            if (!StringUtil.isNullOrEmpty(domain)) {
                contactJsonObject.addProperty(JsonConstants.DOMAIN, domain);
            }

            String department = contact.getDepartment();
            if (!StringUtil.isNullOrEmpty(department)) {
                contactJsonObject.addProperty(JsonConstants.DEPARTMENT, department);
            }

            String classEntryName = contact.getClassEntryName();
            if (!StringUtil.isNullOrEmpty(classEntryName)) {
                contactJsonObject.addProperty(JsonConstants.CLASS, classEntryName);
            }

            contactJsonObject.addProperty(JsonConstants.VISIBLE, contact.getIsHidden()
                    ? JsonConstants.VALUE_FALSE : JsonConstants.VALUE_TRUE);

            Integer state = contact.getState();

            if (state != null) {
                final String trustState;
                switch (state) {
                    case Contact.STATE_LOW_TRUST: {
                        trustState = "low";
                        break;
                    }
                    case Contact.STATE_MIDDLE_TRUST: {
                        trustState = "middle";
                        break;
                    }
                    case Contact.STATE_HIGH_TRUST: {
                        trustState = "high";
                        break;
                    }
                    default:
                        trustState = "none";
                        break;
                }

                contactJsonObject.addProperty("trustState", trustState);
            }

            contactJsonObject.addProperty("deleted", contact.isDeletedHidden() ? "true" : "false");

            contact.addConfirmedStateToJO(contactJsonObject);

            jsonObject.add("ContactBackup", contactJsonObject);

            return jsonObject;
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }
}
