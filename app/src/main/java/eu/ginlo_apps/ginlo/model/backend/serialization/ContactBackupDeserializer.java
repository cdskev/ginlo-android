// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * Created by Florian on 08.06.16.
 */
public class ContactBackupDeserializer implements JsonDeserializer<Contact> {

    @Override
    public Contact deserialize(JsonElement jsonElement, Type type,
                               JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        try {
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                return null;
            }

            JsonObject jo = jsonElement.getAsJsonObject();

            if (!jo.has("ContactBackup")) {
                return null;
            }

            JsonObject joContact = jo.getAsJsonObject("ContactBackup");

            String guid = JsonUtil.stringFromJO("guid", joContact);

            if (StringUtil.isNullOrEmpty(guid)) {
                return null;
            }

            String nickname = JsonUtil.stringFromJO("nickname", joContact);

            String phone = JsonUtil.stringFromJO("phone", joContact);

            String publicKey = JsonUtil.stringFromJO("publicKey", joContact);

            String profileKey = JsonUtil.stringFromJO("profileKey", joContact);

            String trustState = JsonUtil.stringFromJO("trustState", joContact);

            String mandant = JsonUtil.stringFromJO("mandant", joContact);

            String accountID = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, joContact);

            String firstName = JsonUtil.stringFromJO(JsonConstants.FIRSTNAME, joContact);
            String name = JsonUtil.stringFromJO(JsonConstants.NAME, joContact);
            String email = JsonUtil.stringFromJO(JsonConstants.EMAIL, joContact);
            String domain = JsonUtil.stringFromJO(JsonConstants.DOMAIN, joContact);
            String department = JsonUtil.stringFromJO(JsonConstants.DEPARTMENT, joContact);
            String classEntryName = JsonUtil.stringFromJO(JsonConstants.CLASS, joContact);

            int state = Contact.STATE_UNSIMSABLE;
            if (guid.equalsIgnoreCase(AppConstants.GUID_SYSTEM_CHAT)) {
                state = Contact.STATE_HIGH_TRUST;
            } else if (!StringUtil.isNullOrEmpty(trustState)) {
                if (trustState.equalsIgnoreCase("low")) {
                    state = Contact.STATE_LOW_TRUST;
                } else if (trustState.equalsIgnoreCase("middle")) {
                    state = Contact.STATE_MIDDLE_TRUST;
                } else if (trustState.equalsIgnoreCase("high")) {
                    state = Contact.STATE_HIGH_TRUST;
                }
            }

            String deletedString = JsonUtil.stringFromJO("deleted", joContact);
            boolean isDeleted = false;
            if (!StringUtil.isNullOrEmpty(deletedString)) {
                isDeleted = deletedString.equalsIgnoreCase("true");
            }

            //hidden true -> fuer spaeteren Kontakte sync besser
            Contact contact = new Contact(null, guid, null, null, null, publicKey, null, null, true,
                    null, false, true, null, null,
                    mandant, null, null, null);

            if (!StringUtil.isNullOrEmpty(nickname)) {
                contact.setNickname(nickname);
            }

            if (!StringUtil.isNullOrEmpty(phone)) {
                contact.setPhoneNumber(phone);
            }

            if (!StringUtil.isNullOrEmpty(profileKey)) {
                contact.setProfileInfoAesKey(profileKey);
            }

            contact.setState(state);
            contact.setIsDeletedHidden(isDeleted);

            if (JsonUtil.hasKey(JsonConstants.CONFIRMED, joContact)) {
                contact.setConfirmedState(joContact);
            } else {
                contact.setIsFirstContact(state > Contact.STATE_LOW_TRUST);
            }

            if (!StringUtil.isNullOrEmpty(accountID)) {
                contact.setSimsmeId(accountID);
            }

            if (!StringUtil.isNullOrEmpty(firstName)) {
                contact.setFirstName(firstName);
            }

            if (!StringUtil.isNullOrEmpty(name)) {
                contact.setLastName(name);
            }

            if (!StringUtil.isNullOrEmpty(email)) {
                contact.setEmail(email);
            }

            if (!StringUtil.isNullOrEmpty(domain)) {
                contact.setDomain(domain);
            }

            if (!StringUtil.isNullOrEmpty(department)) {
                contact.setDepartment(department);
            }

            if (!StringUtil.isNullOrEmpty(classEntryName)) {
                contact.setClassEntryName(classEntryName);
            }

            String value = JsonUtil.stringFromJO(JsonConstants.VISIBLE, joContact);
            contact.setIsHidden(StringUtil.isNullOrEmpty(value) || !StringUtil.isEqual(value, JsonConstants.VALUE_TRUE));

            return contact;
        } catch (LocalizedException e) {
            return null;
        }
    }
}
