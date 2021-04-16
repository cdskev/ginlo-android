// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import android.util.Base64;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.action.*;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class PrivateInternalMessageModelActionSerializer
        implements JsonSerializer<Action> {

    @Override
    public JsonElement serialize(Action privateInternalMessageModelAction,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject action = new JsonObject();

        if (privateInternalMessageModelAction instanceof ChangeStatusAction) {
            ChangeStatusAction changeStatusAction = (ChangeStatusAction) privateInternalMessageModelAction;

            action.addProperty(DataContainer.CONTENT, changeStatusAction.status);
            action.addProperty(DataContainer.CONTENT_TYPE, changeStatusAction.name);
        } else if (privateInternalMessageModelAction instanceof ChangeProfileNameAction) {
            ChangeProfileNameAction changeProfileNameAction = (ChangeProfileNameAction) privateInternalMessageModelAction;

            action.addProperty(DataContainer.CONTENT, changeProfileNameAction.profileName);
            action.addProperty(DataContainer.CONTENT_TYPE, changeProfileNameAction.name);
        } else if (privateInternalMessageModelAction instanceof ChangeProfileImageAction) {
            ChangeProfileImageAction changeProfileImageAction = (ChangeProfileImageAction)
                    privateInternalMessageModelAction;

            action.addProperty(DataContainer.CONTENT,
                    Base64.encodeToString(changeProfileImageAction.profileImage, Base64.DEFAULT));
            action.addProperty(DataContainer.CONTENT_TYPE, changeProfileImageAction.name);
        } else if (privateInternalMessageModelAction instanceof ChangeGroupImageAction) {
            ChangeGroupImageAction changeGroupImageAction = (ChangeGroupImageAction) privateInternalMessageModelAction;

            action.addProperty(DataContainer.CONTENT,
                    Base64.encodeToString(changeGroupImageAction.groupImage, Base64.DEFAULT));
            action.addProperty(DataContainer.CONTENT_TYPE, changeGroupImageAction.name);
            action.addProperty(DataContainer.GROUP_GUID, changeGroupImageAction.groupGuid);
        } else if (privateInternalMessageModelAction instanceof ChangeGroupNameAction) {
            ChangeGroupNameAction changeGroupNameAction = (ChangeGroupNameAction) privateInternalMessageModelAction;

            action.addProperty(DataContainer.CONTENT, changeGroupNameAction.groupName);
            action.addProperty(DataContainer.CONTENT_TYPE, changeGroupNameAction.name);
            action.addProperty(DataContainer.GROUP_GUID, changeGroupNameAction.groupGuid);
        } else if (privateInternalMessageModelAction instanceof NewGroupMemberAction) {
            NewGroupMemberAction newGroupMemberAction = (NewGroupMemberAction) privateInternalMessageModelAction;

            action.add(DataContainer.CONTENT, context.serialize(newGroupMemberAction.getGuids(), String[].class));
            action.add(DataContainer.GROUP_MEMBERS, context.serialize(newGroupMemberAction.memberNames, String[].class));
            action.addProperty(DataContainer.CONTENT_TYPE, newGroupMemberAction.name);
            action.addProperty(DataContainer.GROUP_GUID, newGroupMemberAction.getGroupGuid());
        } else if (privateInternalMessageModelAction instanceof RemoveGroupMemberAction) {
            RemoveGroupMemberAction removeGroupMemberAction = (RemoveGroupMemberAction) privateInternalMessageModelAction;

            action.add(DataContainer.CONTENT, context.serialize(removeGroupMemberAction.getGuids(), String[].class));
            action.addProperty(DataContainer.CONTENT_TYPE, removeGroupMemberAction.name);
            action.addProperty(DataContainer.GROUP_GUID, removeGroupMemberAction.getGroupGuid());
        }
        return action;
    }
}
