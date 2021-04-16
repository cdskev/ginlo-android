// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import android.util.Base64;
import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.action.*;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.JsonUtil;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class PrivateInternalMessageModelActionDeserializer
        implements JsonDeserializer<Action> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public Action deserialize(JsonElement jsonElement,
                              Type type,
                              JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (jsonObject == null) {
            return null;
        }

        Action privateInternalMessageModelAction = null;

        String action = JsonUtil.stringFromJO(DataContainer.CONTENT_TYPE, jsonObject);

        if (action == null) {
            action = JsonUtil.stringFromJO(DataContainer.CONTENT_TYPE_2, jsonObject);

            if (action == null) {
                return null;
            }
        }

        switch (action) {
            case Action.ACTION_NEW_GROUP_MEMBERS:
                NewGroupMemberAction newGroupMemberAction = new NewGroupMemberAction();

                newGroupMemberAction.name = action;
                newGroupMemberAction.setGroupGuid(JsonUtil.stringFromJO(DataContainer.GROUP_GUID, jsonObject));
                newGroupMemberAction.setGuids((String[]) context.deserialize(jsonObject.get(DataContainer.CONTENT), String[].class));

                if (JsonUtil.hasKey(DataContainer.GROUP_MEMBERS, jsonObject)) {
                    newGroupMemberAction.memberNames = context.deserialize(jsonObject.get(DataContainer.GROUP_MEMBERS),
                            String[].class);
                }

                privateInternalMessageModelAction = newGroupMemberAction;
                break;
            case Action.ACTION_CHANGE_GROUP_IMAGE: {
                ChangeGroupImageAction changeGroupImageAction = new ChangeGroupImageAction();

                changeGroupImageAction.name = action;
                changeGroupImageAction.groupGuid = JsonUtil.stringFromJO(DataContainer.GROUP_GUID, jsonObject);

                String base64String = JsonUtil.stringFromJO(DataContainer.CONTENT, jsonObject);

                changeGroupImageAction.groupImage = (base64String != null) ? Base64.decode(base64String, Base64.DEFAULT)
                        : null;
                privateInternalMessageModelAction = changeGroupImageAction;
                break;
            }
            case Action.ACTION_CHANGE_GROUP_NAME:
                ChangeGroupNameAction changeGroupNameAction = new ChangeGroupNameAction();

                changeGroupNameAction.name = action;
                changeGroupNameAction.groupGuid = JsonUtil.stringFromJO(DataContainer.GROUP_GUID, jsonObject);
                changeGroupNameAction.groupName = JsonUtil.stringFromJO(DataContainer.CONTENT, jsonObject);
                privateInternalMessageModelAction = changeGroupNameAction;
                break;
            case Action.ACTION_CHANGE_PROFILE_IMAGE: {
                ChangeProfileImageAction changeProfileImage = new ChangeProfileImageAction();

                changeProfileImage.name = action;

                String base64String = JsonUtil.stringFromJO(DataContainer.CONTENT, jsonObject);

                changeProfileImage.profileImage = (base64String != null) ? Base64.decode(base64String, Base64.DEFAULT)
                        : null;
                privateInternalMessageModelAction = changeProfileImage;
                break;
            }
            case Action.ACTION_CHANGE_PROFILE_NAME:
                ChangeProfileNameAction changeProfileNameAction = new ChangeProfileNameAction();

                changeProfileNameAction.name = action;
                changeProfileNameAction.profileName = JsonUtil.stringFromJO(DataContainer.CONTENT, jsonObject);
                privateInternalMessageModelAction = changeProfileNameAction;
                break;
            case Action.ACTION_CHANGE_STATUS:
                ChangeStatusAction changeStatusAction = new ChangeStatusAction();

                changeStatusAction.name = action;
                changeStatusAction.status = JsonUtil.stringFromJO(DataContainer.CONTENT, jsonObject);
                privateInternalMessageModelAction = changeStatusAction;
                break;
            case Action.ACTION_REMOVE_GROUP_MEMBER:
                RemoveGroupMemberAction removeGroupMemberAction = new RemoveGroupMemberAction();

                removeGroupMemberAction.name = action;
                removeGroupMemberAction.setGroupGuid(JsonUtil.stringFromJO(DataContainer.GROUP_GUID, jsonObject));
                removeGroupMemberAction.setGuids((String[]) context.deserialize(jsonObject.get(DataContainer.CONTENT), String[].class));
                privateInternalMessageModelAction = removeGroupMemberAction;
                break;
            // Business Client
            case Action.ACTION_COMPANY_ENCRYPT_INFO:
                CompanyEncryptInfoAction companyEncryptInfoAction = new CompanyEncryptInfoAction();

                companyEncryptInfoAction.name = action;
                companyEncryptInfoAction.companyEncryptionSalt = JsonUtil.stringFromJO(JsonConstants.COMPANY_ENCRYPT_SALT, jsonObject);
                companyEncryptInfoAction.companyEncryptionSeed = JsonUtil.stringFromJO(JsonConstants.COMPANY_ENCRYPT_SEED, jsonObject);
                companyEncryptInfoAction.companyEncryptionDiff = JsonUtil.stringFromJO(JsonConstants.COMPANY_ENCRYPTION_DIFF, jsonObject);
                companyEncryptInfoAction.companyEncryptionPart = JsonUtil.stringFromJO(JsonConstants.COMPANY_ENCRYPTION_PARTS, jsonObject);

                privateInternalMessageModelAction = companyEncryptInfoAction;
                break;
            case Action.ACTION_COMPANY_REQUEST_CONFIRM_PHONE:
                RequestConfirmPhoneAction requestConfirmPhoneAction = new RequestConfirmPhoneAction();
                requestConfirmPhoneAction.requestPhone = JsonUtil.stringFromJO(JsonConstants.REQUEST_PHONE, jsonObject);
                privateInternalMessageModelAction = requestConfirmPhoneAction;
                break;
            case Action.ACTION_COMPANY_REQUEST_CONFIRM_EMAIL:
                RequestConfirmEmailAction requestConfirmEmailAction = new RequestConfirmEmailAction();
                requestConfirmEmailAction.requestEmail = JsonUtil.stringFromJO(JsonConstants.REQUEST_EMAIL, jsonObject);
                privateInternalMessageModelAction = requestConfirmEmailAction;
                break;
            default:
                return null;
        }
        return privateInternalMessageModelAction;
    }
}
