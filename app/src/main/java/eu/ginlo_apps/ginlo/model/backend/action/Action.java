// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.MimeUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class Action {

    public static final String ACTION_CONFIRM_DOWNLOAD_V1 = "confirmDownloaded-V1";

    public static final String ACTION_CONFIRM_READ_V1 = "confirmRead-V1";

    public static final String ACTION_CHANGE_OWNER_V1 = "changeOwner-V1";

    public static final String ACTION_REMOVE_CHAT_ROOM_V1 = "removeChatRoom-V1";

    public static final String ACTION_CONFIRM_CHAT_DELETED_V1 = "confirmChatDeleted-V1";

    public static final String ACTION_PROFIL_INFO_CHANGED_V1 = "profilInfoChanged-V1";

    public static final String ACTION_GROUP_INFO_CHANGED_V1 = "groupInfoChanged-V1";

    public static final String ACTION_CONFIRM_DELETE_V1 = "confirmDeleted-V1";

    public static final String ACTION_OOO_STATUS = "account/oooStatus";

    public static final String ACTION_UPDATE_ACCOUNT_ID = "account/updateAccountID";

    public static final String ACTION_CONFIRM_MESSAGE_SEND = "ConfirmMessageSend";

    public static final String ACTION_NEW_GROUP_MEMBERS = AppConstants.MODEL_NEW_GROUP_MEMBERS;

    public static final String ACTION_CHANGE_GROUP_IMAGE = AppConstants.GROUP_IMAGE_JPEG;

    public static final String ACTION_CHANGE_GROUP_NAME = AppConstants.MODEL_GROUP_NAME;

    public static final String ACTION_CHANGE_PROFILE_IMAGE = MimeUtil.MIME_TYPE_IMAGE_JPEG;

    public static final String ACTION_CHANGE_PROFILE_NAME = AppConstants.TEXT_NICKNAME;

    public static final String ACTION_CHANGE_STATUS = AppConstants.TEXT_STATUS;

    public static final String ACTION_REMOVE_GROUP_MEMBER = AppConstants.MODEL_REMOVED_GROUP_MEMBERS;

    public static final String ACTION_INVITE_GROUP_MEMBERS = AppConstants.MODEL_INVITE_GROUP_MEMBERS;

    public static final String ACTION_NEW_GROUP_ADMINS = AppConstants.MODEL_NEW_GROUP_ADMINS;

    public static final String ACTION_REVOKE_GROUP_ADMINS = AppConstants.MODEL_REVOKE_GROUP_ADMINS;

    public static final String ACTION_COMPANY_ENCRYPT_INFO = "company/encryptionInfo";

    public static final String ACTION_CONFIGVERSIONS_CHANGED = AppConstants.CONFIG_VERSIONS_V1;

    public static final String ACTION_COMPANY_REQUEST_CONFIRM_PHONE = "company/requestConfirmPhone";

    public static final String ACTION_COMPANY_REQUEST_CONFIRM_EMAIL = "company/requestConfirmationMail";

    public static final String ACTION_REVOKE_PHONE = "model/revokePhone";

    public static final String ACTION_REVOKE_MAIL = "model/revokeEmailAddress";

    public final long internalMessageId = -1;
    public String name;
    private boolean mIsOwnMessage;

    public Action() {
    }

    public void setIsOwnMessage(boolean ownMessage) {
        mIsOwnMessage = ownMessage;
    }

    public boolean isOwnMessage() {
        return mIsOwnMessage;
    }
}
