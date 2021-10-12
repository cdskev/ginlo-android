// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.constant;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.FeatureVersion;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;

public class AppConstants {
    public static final String OS = "Android " + Build.VERSION.RELEASE;
    public static final String GUID_SYSTEM_CHAT = BuildConfig.GUID_SYSTEM_CHAT;
    public static final String PUBLICKEY_SYSTEM_CHAT = BuildConfig.PUBLICKEY_SYSTEM_CHAT;
    public static final String GUID_PROFILE_USER = "0:{1-2-3}";
    public static final String GUID_PROFILE_GROUP = "0:{1-2-4}";
    public static final String GUID_CHANNEL_PREFIX = "21:";
    public static final String GUID_SERVICE_PREFIX = "22:";
    public static final String GUID_ACCOUNT_PREFIX = "0:";
    public static final String GUID_GROUP_PREFIX = "7:";
    public static final String GUID_MSG_CHANNEL_PREFIX = "110:";
    public static final String MESSAGE_STATE_PREFETCHED_PERSISTENCE = "prefetchedPersistence";
    public static final String MESSAGE_STATE_METADATA_DOWNLOADED = "metadataDownloaded";
    public static final String MESSAGE_STATE_READ = "read";
    public static final String MESSAGE_STATE_DELETED = "deleted";
    public static final String MESSAGE_STATE_ATTACHMENT_DOWNLOADED = "attachmentDownloaded";
    // Defines a custom Intent action
    public static final String BROADCAST_ACTION = "eu.ginlo_apps.ginlo.BROADCAST";
    public static final String BROADCAST_RESTORE_BACKUP_ACTION = "eu.ginlo_apps.ginlo.BROADCAST_RESTORE";
    // Defines the key for the status "extra" in an Intent
    public static final String INTENT_EXTENDED_DATA_STATUS = "eu.ginlo_apps.ginlo.STATUS";
    public static final String INTENT_EXTENDED_DATA_EXTRA = "eu.ginlo_apps.ginlo.EXTRA";
    public static final String INTENT_EXTENDED_DATA_EXCEPTION = "eu.ginlo_apps.ginlo.EXCEPTION";
    public static final String INTENT_EXTENDED_DATA_PATH = "eu.ginlo_apps.ginlo.PATH";
    public static final String INTENT_EXTENDED_DATA_BACKUP_PWD = "eu.ginlo_apps.ginlo.BACKUP_PWD";
    public static final int STATE_ACTION_BACKUP_STARTED = 0;
    public static final int STATE_ACTION_BACKUP_START_SAVE_CHATS = 1;
    public static final int STATE_ACTION_BACKUP_UPDATE_SAVE_CHATS = 2;
    public static final int STATE_ACTION_BACKUP_START_SAVE_SERVICES = 3;
    public static final int STATE_ACTION_BACKUP_SAVE_BU_FILE = 5;
    public static final int STATE_ACTION_BACKUP_ERROR = 10;
    public static final int STATE_ACTION_BACKUP_FINISHED = 15;
    public static final int STATE_ACTION_RESTORE_BACKUP_STARTED = 0;
    public static final int STATE_ACTION_RESTORE_BACKUP_ACCOUNT = 2;
    public static final int STATE_ACTION_RESTORE_BACKUP_CONTACTS = 4;
    public static final int STATE_ACTION_RESTORE_BACKUP_CHATS_STARTED = 6;
    public static final int STATE_ACTION_RESTORE_BACKUP_CHATS_UPDATE = 7;
    public static final int STATE_ACTION_RESTORE_BACKUP_TIMED_MESSAGES = 8;
    public static final int STATE_ACTION_RESTORE_BACKUP_CHANNELS_STARTED = 9;
    public static final int STATE_ACTION_RESTORE_BACKUP_CHANNELS_UPDATE = 10;
    public static final int STATE_ACTION_RESTORE_BACKUP_ERROR = 11;
    public static final int STATE_ACTION_RESTORE_BACKUP_SERVICES_STARTED = 12;
    public static final int STATE_ACTION_RESTORE_BACKUP_SERVICES_UPDATE = 13;
    public static final int STATE_ACTION_RESTORE_BACKUP_FINISHED = 15;
    // Default preferences value for max days that messages are being kept on the backend
    public static final int DEFAULT_PERSIST_MESSAGE_DAYS = 90;
    public static final String JSON_FILE_EXTENSION = "json";
    public static final String JSON_KEY_GUID = "guid";
    public static final int BACKUP_VERSION = 1;
    public static final int BACKUP_MAX_FILES_FOR_MANDANT_IN_DRIVE = 1;
    public static final String BACKUP_FILE_PREFIX = "Backup-";
    public static final String BACKUP_FILE_EXTENSION = "simsbck";
    public static final String RESTORE_BACKUP_FILE_NAME = "restorebackup." + BACKUP_FILE_EXTENSION;
    public static final String BACKUP_ATTACHMENT_DIR = "Attachments";
    public static final String BACKUP_FILE_ACCOUNT = "account." + JSON_FILE_EXTENSION;
    public static final String BACKUP_FILE_CHANNELS = "channels." + JSON_FILE_EXTENSION;
    public static final String BACKUP_FILE_CONTACTS = "contacts." + JSON_FILE_EXTENSION;
    public static final String BACKUP_FILE_INFO = "info." + JSON_FILE_EXTENSION;
    public static final String BACKUP_JSON_INFO_OBJECT_KEY = "BackupInfo";
    public static final String BACKUP_JSON_INFO_VERSION_KEY = "version";
    public static final String BACKUP_JSON_INFO_APP_KEY = "app";
    public static final String BACKUP_JSON_INFO_SALT_KEY = "salt";
    public static final String BACKUP_JSON_INFO_PBDKF_SALT_KEY = "pbdkfSalt";
    public static final String BACKUP_JSON_INFO_PBDKF_ROUNDS_KEY = "pbdkfRounds";
    public static final String BACKUP_JSON_ACCOUNT_OBJECT_KEY = "AccountBackup";
    public static final String BACKUP_JSON_ACCOUNT_NICKNAME_KEY = "nickname";
    public static final String BACKUP_JSON_ACCOUNT_PHONE_KEY = "phone";
    public static final String BACKUP_JSON_ACCOUNT_PROFILE_KEY_KEY = "profileKey";
    public static final String BACKUP_JSON_ACCOUNT_BACKUP_PASSTOKEN_KEY = "backupPasstoken";
    public static final String BACKUP_JSON_ACCOUNT_PRIVATE_KEY_KEY = "privateKey";
    public static final String BACKUP_JSON_ACCOUNT_PUBLIC_KEY_KEY = "publicKey";
    public static final String BACKUP_DRIVE_ITEM_ID = "drive_item_id";
    public static final String LOCAL_BACKUP_ITEM_SIZE = "drive_itme_size";
    public static final String LOCAL_BACKUP_ITEM_MOD_DATE = "drive_item_mod_date";
    public static final String LOCAL_BACKUP_ITEM_NAME = "drive_item_name";
    public static final String LOCAL_BACKUP_FLAVOUR = "default";
    public static final int BACKUP_RESTORE_ACTION_REGISTER_WO_BACKUP = 1;
    public static final int BACKUP_RESTORE_ACTION_BACKUP_SELECTED = 3;
    public static final int BACKUP_RESTORE_ACTION_BACKUP_DESELECTED = 4;
    public static final int BACKUP_RESTORE_ACTION_IMPORT_BACKUP = 6;
    public static final int BACKUP_RESTORE_ACTION_BACKUP_SET_PASSWORD = 7;
    public static final int BACKUP_RESTORE_ACTION_REGISTER_WO_CONFIRM = 8;
    public static final int BACKUP_RESTORE_ACTION_REGISTER_WO_CONFIRM_CANCEL = 9;
    public static final int BACKUP_ACTION_REQUEST_GDRIVE = 1;
    public static final int BACKUP_ACTION_START_BACKUP = 2;
    public static final int BACKUP_ACTION_SET_PASSWORD = 3;
    public static final int BACKUP_ACTION_CHANGE_PASSWORD = 4;
    public static final int BACKUP_ACTION_CHANGE_INTERVAL = 5;
    public static final int BACKUP_ACTION_CHANGE_MEDIA_SETTING = 6;
    public static final int BACKUP_ACTION_CHANGE_NETWORK_SETTING = 9;
    public static final int BACKUP_ACTION_CONFIRM_PASSWORD = 10;
    public static final String ACTION_ARGS_VALUE = "action_value";
    public static final String ACTION_ARGS_VALUE_2 = "action_value_2";
    //Message
    public static final String MESSAGE_JSON_RECEIVERS = "receiver";
    public static final String MESSAGE_JSON_RECEIVER = "Receiver";
    public static final String MODEL_INVITATION = "model/invitation";
    public static final String MODEL_GROUP_NAME = "model/groupname";
    public static final String MODEL_NEW_GROUP_MEMBERS = "model/newmembers";
    public static final String MODEL_INVITE_GROUP_MEMBERS = "model/invitemembers";
    public static final String MODEL_REMOVED_GROUP_MEMBERS = "model/removedmembers";
    public static final String MODEL_NEW_GROUP_ADMINS = "model/newadmins";
    public static final String MODEL_REVOKE_GROUP_ADMINS = "model/revokeadmins";
    public static final String GROUP_IMAGE_JPEG = "groupimage/jpeg";
    public static final String TEXT_STATUS = "text/status";
    public static final String TEXT_NICKNAME = "text/nickname";
    public static final String CONFIG_VERSIONS_V1 = "configVersions-V1";
    // Defines a custom Intent action
    public static final String BROADCAST_COMPANY_ACTION = "eu.ginlo_apps.ginlo.BROADCAST_COMPANY";
    public static final int CONFIGURE_COMPANY_STATE_STARTED = 1;
    public static final int CONFIGURE_COMPANY_STATE_CONNECTING = 2;
    public static final int CONFIGURE_COMPANY_STATE_GET_CONFIG = 3;
    public static final int CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_START = 4;
    public static final int CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_SIZE = 5;
    public static final int CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_UPDATE = 6;
    public static final int CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_START = 7;
    public static final int CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_SIZE = 8;
    public static final int CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_UPDATE = 9;
    public static final int CONFIGURE_COMPANY_STATE_FINISHED = 10;
    public static final int CONFIGURE_COMPANY_STATE_ERROR = 11;
    private static final char[] KEYSTORE_PASS = BuildConfig.APPLICATION_KEYSTORE_PASS.toCharArray();
    private static int APP_VERSION_CODE = 0;
    private static String APP_VERSION_NAME = "";

    private AppConstants() {
    }

    public static int[] getAppFeatureVersions() {
        if (RuntimeConfig.supportMultiDevice()) {
            return new int[]{
                    FeatureVersion.VOICEREC,
                    FeatureVersion.NEW_NICKNAME,
                    FeatureVersion.FILE_MSG,
                    FeatureVersion.BADGE_PUSH_ONLY,
                    FeatureVersion.SEND_PROFILE_NAME,
                    FeatureVersion.NEW_GROUP_FUNCTIONS,
                    FeatureVersion.PUSH_FOR_BUSINESS_GROUPS,
                    FeatureVersion.SUPPORT_MULTI_DEVICE_MANAGEMENT,
                    FeatureVersion.SUPPORT_OOO_STATE,
                    FeatureVersion.SUPPORT_COCKPIT_RSS_MESSAGES,
                    FeatureVersion.SUPPORT_FCM
            };
        } else {
            return new int[]{
                    FeatureVersion.VOICEREC,
                    FeatureVersion.NEW_NICKNAME,
                    FeatureVersion.FILE_MSG,
                    FeatureVersion.BADGE_PUSH_ONLY,
                    FeatureVersion.SEND_PROFILE_NAME,
                    FeatureVersion.NEW_GROUP_FUNCTIONS,
                    FeatureVersion.PUSH_FOR_BUSINESS_GROUPS,
                    FeatureVersion.SUPPORT_OOO_STATE,
                    FeatureVersion.SUPPORT_FCM
            };
        }
    }

    public static String getAppName() {
        return BuildConfig.APP_NAME_INTERNAL;
    }

    public static String getAppVersionName() {
        return APP_VERSION_NAME;
    }

    public static int getAppVersionCode() {
        return APP_VERSION_CODE;
    }

    public static char[] getKeystorePass() {
        return KEYSTORE_PASS.clone();
    }

    public static void gatherData(Context context) {
        getAppVersion(context);
    }

    private static void getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            APP_VERSION_CODE = packageInfo.versionCode;
            APP_VERSION_NAME = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            LogUtil.e(AppConstants.class.getName(), "NameNotFoundException");

            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }
    
    public static int getNewMessageFlag(int messageType) {
        final int returnValue;
        switch (messageType) {
            case eu.ginlo_apps.ginlo.greendao.Message.TYPE_PRIVATE_INTERNAL: {
                returnValue = Message.NewMessagesStates.TYPE_PRIVATE_INTERNAL;
                break;
            }
            case eu.ginlo_apps.ginlo.greendao.Message.TYPE_PRIVATE: {
                returnValue = Message.NewMessagesStates.TYPE_PRIVATE;
                break;
            }
            case eu.ginlo_apps.ginlo.greendao.Message.TYPE_GROUP: {
                returnValue = Message.NewMessagesStates.TYPE_GROUP;
                break;
            }
            case eu.ginlo_apps.ginlo.greendao.Message.TYPE_GROUP_INVITATION: {
                returnValue = Message.NewMessagesStates.TYPE_GROUP_INVITATION;
                break;
            }
            case eu.ginlo_apps.ginlo.greendao.Message.TYPE_CHANNEL: {
                returnValue = Message.NewMessagesStates.TYPE_CHANNEL;
                break;
            }
            case eu.ginlo_apps.ginlo.greendao.Message.TYPE_INTERNAL: {
                returnValue = Message.NewMessagesStates.TYPE_INTERNAL;
                break;
            }
            default:
                returnValue = -1;
        }

        return returnValue;
    }

    public class Message {
        public class NewMessagesStates {
            public static final int TYPE_PRIVATE_INTERNAL = 0x1;
            public static final int TYPE_PRIVATE = 0x1 << 1;
            public static final int TYPE_INTERNAL = 0x1 << 2;
            public static final int TYPE_GROUP = 0x1 << 3;
            public static final int TYPE_GROUP_INVITATION = 0x1 << 4;
            public static final int TYPE_CHANNEL = 0x1 << 5;
        }
    }
}
