// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ConfirmMessageSendModel;
import eu.ginlo_apps.ginlo.model.backend.action.*;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.JsonUtil;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class InternalMessageModelActionDeserializer
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
        Action internalMessageModelAction = null;

        GroupAction groupAction = null;
        JsonObject groupActionJO = null;

        if (jsonObject.has(Action.ACTION_CHANGE_OWNER_V1)) {
            JsonObject container = jsonObject.get(Action.ACTION_CHANGE_OWNER_V1)
                    .getAsJsonObject();

            ChangeOwnerV1Action changeOwnerAction = new ChangeOwnerV1Action();

            changeOwnerAction.name = Action.ACTION_CHANGE_OWNER_V1;
            changeOwnerAction.roomGuid = container.has("roomGuid") ? container.get("roomGuid").getAsString() : null;
            changeOwnerAction.accountGuid = container.has("roomGuid") ? container.get("roomGuid").getAsString() : null;
            internalMessageModelAction = changeOwnerAction;
        } else if (jsonObject.has(Action.ACTION_REMOVE_CHAT_ROOM_V1)) {
            String guid = jsonObject.get(Action.ACTION_REMOVE_CHAT_ROOM_V1)
                    .getAsString();
            RemoveChatRoomV1Action removeChatRoomAction = new RemoveChatRoomV1Action(guid);

            removeChatRoomAction.name = Action.ACTION_REMOVE_CHAT_ROOM_V1;
            internalMessageModelAction = removeChatRoomAction;
        } else if (jsonObject.has(Action.ACTION_CONFIRM_CHAT_DELETED_V1)) {
            String guid = jsonObject.get(Action.ACTION_CONFIRM_CHAT_DELETED_V1)
                    .getAsString();
            ConfirmChatDeletedV1Action removeChatRoomAction = new ConfirmChatDeletedV1Action(guid);

            removeChatRoomAction.name = Action.ACTION_CONFIRM_CHAT_DELETED_V1;
            internalMessageModelAction = removeChatRoomAction;
        } else if (jsonObject.has(Action.ACTION_PROFIL_INFO_CHANGED_V1)) {
            ProfilInfoChangedV1Action profilInfoChangedAction = new ProfilInfoChangedV1Action();

            profilInfoChangedAction.name = Action.ACTION_PROFIL_INFO_CHANGED_V1;
            profilInfoChangedAction.guid = jsonObject.get(Action.ACTION_PROFIL_INFO_CHANGED_V1)
                    .getAsString();
            internalMessageModelAction = profilInfoChangedAction;
        } else if (jsonObject.has(Action.ACTION_GROUP_INFO_CHANGED_V1)) {
            GroupInfoChangedV1Action groupInfoChangedAction = new GroupInfoChangedV1Action();

            groupInfoChangedAction.name = Action.ACTION_GROUP_INFO_CHANGED_V1;
            groupInfoChangedAction.guid = jsonObject.get(Action.ACTION_GROUP_INFO_CHANGED_V1)
                    .getAsString();
            internalMessageModelAction = groupInfoChangedAction;
        } else if (jsonObject.has(Action.ACTION_NEW_GROUP_MEMBERS)) {
            NewGroupMemberAction newGroupMemberAction = new NewGroupMemberAction();

            groupActionJO = jsonObject.getAsJsonObject(Action.ACTION_NEW_GROUP_MEMBERS);
            groupAction = newGroupMemberAction;

            internalMessageModelAction = newGroupMemberAction;
        } else if (jsonObject.has(Action.ACTION_REMOVE_GROUP_MEMBER)) {
            groupAction = new RemoveGroupMemberAction();
            groupActionJO = jsonObject.getAsJsonObject(Action.ACTION_REMOVE_GROUP_MEMBER);
        } else if (jsonObject.has(Action.ACTION_INVITE_GROUP_MEMBERS)) {
            groupAction = new InviteGroupMemberAction();
            groupActionJO = jsonObject.getAsJsonObject(Action.ACTION_INVITE_GROUP_MEMBERS);
        } else if (jsonObject.has(Action.ACTION_NEW_GROUP_ADMINS)) {
            groupAction = new NewGroupAdminAction();
            groupActionJO = jsonObject.getAsJsonObject(Action.ACTION_NEW_GROUP_ADMINS);
        } else if (jsonObject.has(Action.ACTION_REVOKE_GROUP_ADMINS)) {
            groupAction = new RevokeGroupAdminAction();
            groupActionJO = jsonObject.getAsJsonObject(Action.ACTION_REVOKE_GROUP_ADMINS);
        } else if (jsonObject.has(Action.ACTION_CONFIGVERSIONS_CHANGED)) {
            ConfigVersionsChangedAction configVersionsChangedAction = new ConfigVersionsChangedAction();
            configVersionsChangedAction.name = Action.ACTION_CONFIGVERSIONS_CHANGED;
            JsonElement je = JsonUtil.searchJsonElementRecursive(jsonObject, JsonConstants.CONFIG_DETAILS);
            if (je != null && je.isJsonObject()) {
                configVersionsChangedAction.details = je.getAsJsonObject();
            }
            internalMessageModelAction = configVersionsChangedAction;
        } else if (jsonObject.has(Action.ACTION_CONFIRM_DELETE_V1)) {
            String[] guids = context.deserialize(jsonObject.get(Action.ACTION_CONFIRM_DELETE_V1),
                    String[].class);

            internalMessageModelAction = new ConfirmDeletedAction(guids);
        } else if (jsonObject.has(Action.ACTION_CONFIRM_MESSAGE_SEND)) {
            internalMessageModelAction = new ConfirmMessageSendAction(ConfirmMessageSendModel.createFromJson(jsonObject));
        } else if (jsonObject.has(Action.ACTION_REVOKE_PHONE)) {
            internalMessageModelAction = new RevokePhoneAction();
        } else if (jsonObject.has(Action.ACTION_REVOKE_MAIL)) {
            internalMessageModelAction = new RevokeMailAction();
        } else if (jsonObject.has(Action.ACTION_OOO_STATUS)) {
            internalMessageModelAction = new OooStatusAction(jsonObject.get(Action.ACTION_OOO_STATUS).getAsJsonObject());
        } else if (jsonObject.has(Action.ACTION_UPDATE_ACCOUNT_ID)) {
            internalMessageModelAction = new UpdateAccountID();
        } else {
            String name = null;
            if (jsonObject.has(Action.ACTION_CONFIRM_DOWNLOAD_V1)) {
                name = Action.ACTION_CONFIRM_DOWNLOAD_V1;
            } else if (jsonObject.has(Action.ACTION_CONFIRM_READ_V1)) {
                name = Action.ACTION_CONFIRM_READ_V1;
            }

            if (name == null) {
                return null;
            }

            ConfirmV1Action confirmAction = new ConfirmV1Action();

            confirmAction.name = name;

            confirmAction.guids = context.deserialize(jsonObject.get(name),
                    String[].class);

            internalMessageModelAction = confirmAction;
        }

        if (groupAction != null && groupActionJO != null) {
            groupAction.setGroupGuid(JsonUtil.stringFromJO(DataContainer.GROUP_GUID, groupActionJO));
            groupAction.setGuids((String[]) context.deserialize(groupActionJO.get(DataContainer.CONTENT), String[].class));
            groupAction.setSenderGuid(JsonUtil.stringFromJO(DataContainer.SENDER_GUID, groupActionJO));
            groupAction.setNickName(JsonUtil.stringFromJO(DataContainer.GROUP_NICKNAME, groupActionJO));
            internalMessageModelAction = groupAction;
        }

        return internalMessageModelAction;
    }
}
