// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.app.Application;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChatBackupSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ContactBackupSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.KeyContainerModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ToggleSettingsModelDeserializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.BroadcastNotifier;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StorageUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;
import eu.ginlo_apps.ginlo.util.ZipUtils;
import org.greenrobot.greendao.query.QueryBuilder;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BackupService extends IntentService {

    private static final String TAG = BackupService.class.getSimpleName();
    private final static String WAKELOCK_TAG = "ginlo:BackupService";
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    private static final int LOAD_MSG_COUNT = 20;
    // Defines and instantiates an object for handling status updates.
    private final BroadcastNotifier mBroadcaster = new BroadcastNotifier(this, AppConstants.BROADCAST_ACTION);
    private SimsMeApplication mApplication = null;
    private NotificationController mNotificationController = null;
    private PreferencesController mPreferencesController = null;
    private FileUtil mFileUtil;
    private StorageUtil mStorageUtil;
    private File mBackupDir;
    private SecretKey mBackUpAesKey;
    //Error Objekt fuer Passtoken Abruf vom Server
    private String mErrorText;
    private String mBackupPasstoken;
    private boolean mSaveMedia;
    private boolean mBackupRunning;

    public BackupService() {
        super("BackupService");

    }

    @Override
    public void onCreate() {
        super.onCreate();

        Application app = this.getApplication();
        if (app instanceof SimsMeApplication) {
            mApplication = (SimsMeApplication) app;
            mNotificationController = mApplication.getNotificationController();
            mPreferencesController = mApplication.getPreferencesController();
        }
        mBackupRunning = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        /* KS: Wrong place for that
        if (mBackupDir != null) {
            if (mFileUtil.deleteAllFilesInDir(mBackupDir)) {
                //noinspection ResultOfMethodCallIgnored
                mBackupDir.delete();
                mBackupDir = null;
            }
        }

         */
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_STARTED, null, null, null);

        if(mApplication == null) {
            LogUtil.e(TAG, "Could not initialize backup. mApplication == null");
            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_ERROR, null, null, null);
            return;
        }

        if(mBackupRunning) {
            LogUtil.w(TAG, "onHandleIntent: Backup already running, exiting!");
            return;
        }

        PowerManager pm = (PowerManager) mApplication.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG);
        wl.acquire(30*60*1000L /*30 minutes to be sure*/);

        mNotificationController.showOngoingServiceNotification(mApplication.getString(R.string.settings_backup_config_create_backup));

        try {
            mStorageUtil = new StorageUtil(mApplication);
            mBackupDir = mStorageUtil.getInternalBackupDirectory(true);
            final Uri zipDestination = mStorageUtil.getBackupDestinationUri();
            if(zipDestination == null) {
                LogUtil.e(TAG, "onHandleIntent: No valid backup destination!");
                mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_ERROR, null, null, null);
                mBackupRunning = false;
                return;
            }

            mSaveMedia = mApplication.getPreferencesController().getSaveMediaInBackup();
            LogUtil.i(TAG, "Starting backup service intent with mSaveMedia = " + mSaveMedia);

            //mFileUtil = new FileUtil(mApplication);

            LogUtil.i(TAG, "Load backup key ...");
            loadBackupKey();

            LogUtil.i(TAG, "Write backup info ...");
            writeBackupInfo();

            LogUtil.i(TAG, "Save account ...");
            saveAccount();

            LogUtil.i(TAG, "Save chats ...");
            saveChats();

            if (!ConfigUtil.INSTANCE.syncPrivateIndexToServer()) {
                LogUtil.i(TAG, "Save contacts ...");
                saveContacts();
            }

            LogUtil.i(TAG, "Save channels ...");
            saveChannels();

            LogUtil.i(TAG, "Save services ...");
            saveServices();

            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_SAVE_BU_FILE, null, null, null);

            LogUtil.i(TAG, "Zipping files to " + zipDestination.toString());
            ZipUtils zu = new ZipUtils(mApplication, mBackupDir.getAbsolutePath(), zipDestination);
            zu.startZip();

            mPreferencesController.setLatestBackupPath(mStorageUtil.getBackupDestinationName(zipDestination));
            mPreferencesController.setLatestBackupFileSize(mStorageUtil.getBackupDestinationSize(zipDestination));
            mPreferencesController.setLatestBackupDate(System.currentTimeMillis());

            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_FINISHED, mStorageUtil.getBackupDestinationName(zipDestination), null, null);
            LogUtil.i(TAG, "Backup done. Saved to " + zipDestination.toString());

        } catch (LocalizedException e) {
            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_ERROR, null, null, e);
            LogUtil.e(TAG, "Backup failed. Error: " + e.getMessage(), e);
        }
        finally {
            mBackupRunning = false;
            mNotificationController.dismissOngoingNotification();
            if(wl.isHeld()) {
                wl.release();
                if(wl.isHeld()) {
                    LogUtil.w(TAG, "BackupService: Wakelock held!");
                }
            }
        }
    }

    private void loadBackupKey() throws LocalizedException {
        String base64KeyBytes = mApplication.getPreferencesController().getBackupKey();
        if(base64KeyBytes == null) {
            LogUtil.e(TAG, "loadBackupKey: BackupKey = null!");
            throw new LocalizedException(LocalizedException.OBJECT_NULL);
        }

        mBackUpAesKey = SecurityUtil.getAESKeyFromBase64String(base64KeyBytes);
    }

    private void writeBackupInfo() throws LocalizedException {
        String salt = mApplication.getPreferencesController().getBackupKeySalt();
        int rounds = mApplication.getPreferencesController().getBackupKeyRounds();

        if (StringUtil.isNullOrEmpty(salt) || rounds == PreferencesController.BACKUP_KEY_ROUNDS_ERROR) {
            throw new LocalizedException(LocalizedException.BACKUP_CREATE_BACKUP_FAILED, "Backup AES Key Salt is null or Rounds < 0");
        }

        File decryptedAccountFile = new File(mBackupDir, AppConstants.BACKUP_FILE_INFO);

        try (PrintWriter writer = new PrintWriter(decryptedAccountFile, "UTF-8");
             JsonWriter jsonWriter = new JsonWriter(writer)) {
            jsonWriter.beginArray();

            jsonWriter.beginObject();

            jsonWriter.name(AppConstants.BACKUP_JSON_INFO_OBJECT_KEY);
            jsonWriter.beginObject();
            jsonWriter.name(AppConstants.BACKUP_JSON_INFO_VERSION_KEY).value("" + AppConstants.BACKUP_VERSION);
            jsonWriter.name(AppConstants.BACKUP_JSON_INFO_APP_KEY).value(BuildConfig.GINLO_APP_NAME);
            jsonWriter.name(AppConstants.BACKUP_JSON_INFO_SALT_KEY).value(BuildConfig.SERVER_SALT);
            jsonWriter.name(AppConstants.BACKUP_JSON_INFO_PBDKF_SALT_KEY).value(salt);
            jsonWriter.name(AppConstants.BACKUP_JSON_INFO_PBDKF_ROUNDS_KEY).value("" + rounds);
            jsonWriter.endObject();

            jsonWriter.endObject();

            jsonWriter.endArray();
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.BACKUP_WRITE_FILE_FAILED, e);
        }
    }

    private void saveAccount() throws LocalizedException {
        if (!mApplication.getAccountController().getAccountLoaded()) {
            throw new LocalizedException(LocalizedException.BACKUP_CREATE_BACKUP_FAILED, "account not loaded");
        }

        Account acc = mApplication.getAccountController().getAccount();
        String backupPasstoken = acc.getBackupPasstoken();

        if (StringUtil.isNullOrEmpty(backupPasstoken)) {
            backupPasstoken = getBackupPasstokenFromServer();
            acc.setBackupPasstoken(backupPasstoken);
        }

        acc.setPublicKey(XMLUtil.getXMLFromPublicKey(mApplication.getKeyController().getUserKeyPair().getPublic()));
        acc.setPrivateKey(XMLUtil.getXMLFromPrivateKey(mApplication.getKeyController().getUserKeyPair().getPrivate()));

        mApplication.getAccountController().saveOrUpdateAccount(acc);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        Gson gson = gsonBuilder.create();

        JsonElement accountInJson = mApplication.getBackupController().accountBackup(false);

        if (accountInJson == null || !accountInJson.isJsonObject()) {
            throw new LocalizedException(LocalizedException.BACKUP_JSON_OBJECT_NULL, "Account JSON Object NULL");
        }

        JsonObject accountObject = accountInJson.getAsJsonObject();

        mApplication.getAccountController().doActionsBeforeAccountIsStoreInBackup(accountObject);

        File decryptedAccountFile = new File(mBackupDir, "account_decrypted.json");

        try (PrintWriter writer = new PrintWriter(decryptedAccountFile);
             JsonWriter jsonWriter = new JsonWriter(writer)) {
            jsonWriter.beginArray();
            gson.toJson(accountObject, jsonWriter);
            jsonWriter.endArray();
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.BACKUP_WRITE_FILE_FAILED, e);
        }

        File encryptedAccountFile = new File(mBackupDir, AppConstants.BACKUP_FILE_ACCOUNT);

        SecurityUtil.encryptFileWithAes(mBackUpAesKey, SecurityUtil.generateIV(), true, decryptedAccountFile, encryptedAccountFile);

        if (!decryptedAccountFile.delete()) {
            throw new LocalizedException(LocalizedException.BACKUP_DELETE_FILE_FAILED, "Can't delete decrypted Accountfile");
        }
    }

    private void saveContacts() throws LocalizedException {
        List<Contact> contacts = mApplication.getContactController().getSimsMeContactsWithPubKey(true);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Contact.class, new ContactBackupSerializer());

        Gson gson = gsonBuilder.create();

        File decryptedContactsFile = new File(mBackupDir, "contacts_decrypted.json");

        try (PrintWriter writer = new PrintWriter(decryptedContactsFile);
             JsonWriter jsonWriter = new JsonWriter(writer)) {
            jsonWriter.beginArray();

            for (Contact contact : contacts) {
                if (contact == null) {
                    continue;
                }

                JsonElement jsonContact = gson.toJsonTree(contact);

                if (jsonContact == null) {
                    throw new LocalizedException(LocalizedException.BACKUP_JSON_OBJECT_NULL, "Contact JSON Object NULL");
                }

                gson.toJson(jsonContact, jsonWriter);
            }

            jsonWriter.endArray();
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.BACKUP_WRITE_FILE_FAILED, e);
        }

        File encryptedContactsFile = new File(mBackupDir, AppConstants.BACKUP_FILE_CONTACTS);

        SecurityUtil.encryptFileWithAes(mBackUpAesKey, SecurityUtil.generateIV(), true, decryptedContactsFile, encryptedContactsFile);

        if (!decryptedContactsFile.delete()) {
            throw new LocalizedException(LocalizedException.BACKUP_DELETE_FILE_FAILED, "Can't delete decrypted Contactfile");
        }
    }

    private void saveChannels() throws LocalizedException {
        List<Channel> channels = mApplication.getChannelController().getSubscribedChannelsFromDB(Channel.TYPE_CHANNEL);
        if (channels == null || channels.isEmpty()) {
            return;
        }

        File decryptedChannelsFile = new File(mBackupDir, "channel_decrypted.json");

        int subscribedChannelIndex = 0;

        try (PrintWriter writer = new PrintWriter(decryptedChannelsFile);
             JsonWriter jsonWriter = new JsonWriter(writer)) {
            jsonWriter.beginArray();

            for (Channel channel : channels) {
                if (!channel.getIsSubscribedSave()) {
                    continue;
                }

                String guid = channel.getGuid();
                if (StringUtil.isNullOrEmpty(guid)) {
                    continue;
                }

                subscribedChannelIndex++;

                writeChannel(jsonWriter, channel);
            }

            jsonWriter.endArray();
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.BACKUP_WRITE_FILE_FAILED, e);
        }

        if (subscribedChannelIndex > 0) {
            File encryptedChannelsFile = new File(mBackupDir, AppConstants.BACKUP_FILE_CHANNELS);

            SecurityUtil.encryptFileWithAes(mBackUpAesKey, SecurityUtil.generateIV(), true, decryptedChannelsFile, encryptedChannelsFile);
        }

        if (!decryptedChannelsFile.delete()) {
            throw new LocalizedException(LocalizedException.BACKUP_DELETE_FILE_FAILED, "Can't delete decrypted Contactfile");
        }
    }

    private void writeChannel(JsonWriter jsonWriter, Channel channel) throws IOException, LocalizedException {
        jsonWriter.beginObject();
        jsonWriter.name("ChannelBackup");
        jsonWriter.beginObject();
        jsonWriter.name("guid").value(channel.getGuid());

        Boolean isNotificationDisabled = channel.getDisableNotification();

        if (isNotificationDisabled != null) {
            jsonWriter.name("notification").value(isNotificationDisabled ? "disabled" : "enabled");
        }

        List<String> idents = getChannelOnToggleIdents(channel);

        if (idents != null && !idents.isEmpty()) {
            jsonWriter.name("@ident");
            jsonWriter.beginArray();

            for (String ident : idents) {
                jsonWriter.value(ident);
            }
            jsonWriter.endArray();
        }

        Chat channelChat = mApplication.getChannelChatController().getChatByGuid(channel.getGuid());

        if (channelChat != null) {
            Long lastModifiedDate = channelChat.getLastChatModifiedDate();

            if (lastModifiedDate != null && lastModifiedDate > 0) {
                jsonWriter.name("lastModifiedDate").value(DateUtil.utcStringFromMillis(lastModifiedDate));
            }
        }

        jsonWriter.endObject();
        jsonWriter.endObject();
    }

    private void saveServices() throws LocalizedException {
        List<Channel> services = mApplication.getChannelController().getSubscribedChannelsFromDB(Channel.TYPE_SERVICE);
        if (services == null || services.isEmpty()) {
            return;
        }

        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_START_SAVE_SERVICES, null, null, null);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(KeyContainerModel.class, new KeyContainerModelSerializer());
        gsonBuilder.registerTypeAdapter(Message.class, new MessageSerializer());
        Gson gson = gsonBuilder.create();

        for (Channel service : services) {
            String guid = service.getGuid();

            if (StringUtil.isNullOrEmpty(guid)) {
                continue;
            }

            if (!service.getIsSubscribedSave()) {
                continue;
            }

            String fileName = guid.replace(':', '_');

            File decryptedServiceFile = new File(mBackupDir, fileName + "_decrypted");

            try (PrintWriter writer = new PrintWriter(decryptedServiceFile);
                 JsonWriter jsonWriter = new JsonWriter(writer)) {

                jsonWriter.beginArray();

                writeChannel(jsonWriter, service);

                Chat chat = mApplication.getChannelChatController().getChatByGuid(guid);

                if (chat != null) {
                    saveMessagesForChat(chat, -1, jsonWriter, gson);
                }

                jsonWriter.endArray();
            } catch (IOException e) {
                throw new LocalizedException(LocalizedException.BACKUP_WRITE_FILE_FAILED, e);
            }
            File encryptedServiceFile = new File(mBackupDir, fileName + "." + AppConstants.JSON_FILE_EXTENSION);

            SecurityUtil.encryptFileWithAes(mBackUpAesKey, SecurityUtil.generateIV(), true, decryptedServiceFile, encryptedServiceFile);

            if (!decryptedServiceFile.delete()) {
                throw new LocalizedException(LocalizedException.BACKUP_DELETE_FILE_FAILED, "Can't delete decrypted Servicefile");
            }
        }
    }

    private void saveChats() throws LocalizedException {
        List<Chat> allChats = mApplication.getSingleChatController().loadAll();

        if (allChats == null || allChats.isEmpty()) {
            return;
        }

        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_START_SAVE_CHATS, null, allChats.size(), null);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(KeyContainerModel.class, new KeyContainerModelSerializer());
        gsonBuilder.registerTypeAdapter(Message.class, new MessageSerializer());
        gsonBuilder.registerTypeAdapter(Chat.class, new ChatBackupSerializer());

        Gson gson = gsonBuilder.create();
        int i = 0;

        for (Chat chat : allChats) {
            i++;
            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_BACKUP_UPDATE_SAVE_CHATS, null, i, null);

            if (chat == null || chat.getType() == null) {
                continue;
            }

            String guid = chat.getChatGuid();

            if (StringUtil.isNullOrEmpty(guid) || !(guid.startsWith(AppConstants.GUID_GROUP_PREFIX) || guid.startsWith(AppConstants.GUID_ACCOUNT_PREFIX))) {
                continue;
            }

            String fileName = guid.replace(':', '_');

            File decryptedChatFile = new File(mBackupDir, fileName + "_decrypted");

            try (PrintWriter writer = new PrintWriter(decryptedChatFile);
                 JsonWriter jsonWriter = new JsonWriter(writer)) {
                jsonWriter.beginArray();

                if (chat.getType() == Chat.TYPE_GROUP_CHAT || chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
                    byte[] groupImage = mApplication.getChatImageController().loadImage(guid);
                    if (groupImage != null) {
                        chat.setGroupChatImage(groupImage);
                    }
                }

                JsonElement jsonChat = gson.toJsonTree(chat);

                if (jsonChat == null) {
                    throw new LocalizedException(LocalizedException.BACKUP_JSON_OBJECT_NULL, "Chat JSON Object NULL");
                }

                gson.toJson(jsonChat, jsonWriter);

                saveMessagesForChat(chat, -1, jsonWriter, gson);

                jsonWriter.endArray();
            } catch (IOException e) {
                throw new LocalizedException(LocalizedException.BACKUP_WRITE_FILE_FAILED, e);
            }

            File encryptedChatFile = new File(mBackupDir, fileName + "." + AppConstants.JSON_FILE_EXTENSION);

            SecurityUtil.encryptFileWithAes(mBackUpAesKey, SecurityUtil.generateIV(), true, decryptedChatFile, encryptedChatFile);

            if (!decryptedChatFile.delete()) {
                throw new LocalizedException(LocalizedException.BACKUP_DELETE_FILE_FAILED, "Can't delete decrypted Chatfile");
            }
        }
    }

    private String getBackupPasstokenFromServer() throws LocalizedException {

        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    mErrorText = response.errorMessage;
                } else {
                    if (response.jsonArray == null || response.jsonArray.size() < 1) {
                        mErrorText = "JSON Object is not an array or empty";
                        return;
                    }

                    JsonElement element = response.jsonArray.get(0);
                    if (element != null && !element.isJsonNull()) {
                        String passtoken = element.getAsString();
                        if (!StringUtil.isNullOrEmpty(passtoken)) {
                            mBackupPasstoken = passtoken;
                        } else {
                            mErrorText = "Passtoken is empty";
                        }
                    } else {
                        mErrorText = "Wrong JSON Element";
                    }
                }
            }
        };
        BackendService.withSyncConnection(mApplication)
                .createBackupPassToken(listener);

        if (!StringUtil.isNullOrEmpty(mErrorText)) {
            throw new LocalizedException(LocalizedException.BACKUP_CREATE_BACKUP_FAILED, mErrorText);
        }

        return mBackupPasstoken;
    }

    private void saveMessagesForChat(Chat chat, long lastMsgId, JsonWriter writer, Gson gson) throws LocalizedException, IOException {
        SingleChatController chatController = mApplication.getSingleChatController();

        List<Message> msgList = loadNextMessages(chat, lastMsgId);
        boolean checkSignature = !GuidUtil.isChatService(chat.getChatGuid());

        if (msgList == null || msgList.isEmpty()) {
            return;
        }

        for (Message msg : msgList) {
            msg.setIsBackUp(true);

            if (checkSignature && msg.getIsSignatureValid() == null && chatController != null) {
                try {
                    //check signature und added das Ergebnis an der Message
                    if (msg.getSignatureSha256() != null) {
                        chatController.checkSignatureSha256(msg);
                    } else {
                        chatController.checkSignature(msg);
                    }
                } catch (LocalizedException e) {
                    //Wenn es ein Ausnahme bei der Signaturpruefung gibt, nicht Backuperstellung fehlschlagen lassen
                    // TODO check why we can ignore the check of the signature.
                }
            }

            JsonElement msgJson = gson.toJsonTree(msg);

            msg.setIsBackUp(false);

            gson.toJson(msgJson, writer);

            //Attachment kopieren
            if (mSaveMedia && !StringUtil.isNullOrEmpty(msg.getAttachment())) {
                mApplication.getBackupController().copyAttachmentToAttachmentsDirAsBase64(msg.getAttachment());
            }
        }

        if (msgList.size() == LOAD_MSG_COUNT) {
            Message msg = msgList.get(msgList.size() - 1);
            saveMessagesForChat(chat, msg.getId(), writer, gson);
        }
    }

    private List<Message> loadNextMessages(Chat chat, long maxLoadedMessagedId) throws LocalizedException {
        List<Message> msgList;
        final MessageDao dao = mApplication.getMessageController().getDao();
        int type = getMsgTypeForChat(chat);

        synchronized (dao) {
            final QueryBuilder<Message> queryBuilder = dao.queryBuilder();

            queryBuilder.where(MessageDao.Properties.Type.eq(type)).whereOr(MessageDao.Properties.From.eq(chat.getChatGuid()), MessageDao.Properties.To.eq(chat.getChatGuid()))
                    .orderAsc(MessageDao.Properties.Id).limit(LOAD_MSG_COUNT);

            if (maxLoadedMessagedId != -1) {
                queryBuilder.where(MessageDao.Properties.Id.gt(maxLoadedMessagedId));
            }
            msgList = queryBuilder.build().forCurrentThread().list();
        }

        return msgList;
    }

    private int getMsgTypeForChat(final Chat chat) {
        final int type;
        switch (chat.getType()) {
            case Chat.TYPE_SINGLE_CHAT:
            case Chat.TYPE_SINGLE_CHAT_INVITATION:
                type = Message.TYPE_PRIVATE;
                break;
            case Chat.TYPE_GROUP_CHAT:
            case Chat.TYPE_GROUP_CHAT_INVITATION:
                type = Message.TYPE_GROUP;
                break;
            case Chat.TYPE_CHANNEL:
                type = Message.TYPE_CHANNEL;
                break;
            default:
                type = -1;
        }
        return type;
    }

    private List<String> getChannelOnToggleIdents(Channel channel) {
        String filter = channel.getFilterJsonObject();

        if (StringUtil.isNullOrEmpty(filter)) {
            return null;
        }

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ToggleSettingsModel.class, new ToggleSettingsModelDeserializer());

        Gson gson = gsonBuilder.create();

        Type stringToggleSettingsMap = new TypeToken<Map<String, ToggleSettingsModel>>() {
        }
                .getType();

        Map<String, ToggleSettingsModel> map = gson.fromJson(channel.getFilterJsonObject(),
                stringToggleSettingsMap);

        List<String> onIdents = new ArrayList<>();

        for (String ident : map.keySet()) {
            ToggleSettingsModel model = map.get(ident);

            if (model != null && StringUtil.isEqual(model.value, "on")) {
                onIdents.add(ident);
            }
        }

        return onIdents;
    }
}
