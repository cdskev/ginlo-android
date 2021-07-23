// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.app.Application;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonStreamParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.ChatDao;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.ContactDao;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.AccountModel;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.backend.ChatRoomModel;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChatBackupDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ContactBackupDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.DeviceModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ToggleSettingsModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.BroadcastNotifier;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ZipUtils;

/**
 * Created by Florian on 25.05.16.
 */
public class RestoreBackupService extends IntentService {
    private static final String TAG = RestoreBackupService.class.getSimpleName();
    private final static String WAKELOCK_TAG = "ginlo:RestoreBackupService";
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    // Defines and instantiates an object for handling status updates.
    private final BroadcastNotifier mBroadcaster = new BroadcastNotifier(this, AppConstants.BROADCAST_RESTORE_BACKUP_ACTION);
    private SimsMeApplication mApplication = null;
    private File mRestoreFile;
    private File mUnzipFolder;
    private FileUtil mFileUtil;
    private SecretKey mBackUpAesKey;
    private String mErrorText;

    /**
     * RestoreBackupService
     */
    public RestoreBackupService() {
        super("RestoreBackupService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Application app = this.getApplication();
        if (app instanceof SimsMeApplication) {
            mApplication = (SimsMeApplication) app;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mUnzipFolder != null) {
            if (mFileUtil.deleteAllFilesInDir(mUnzipFolder)) {
                mUnzipFolder.delete();
                mUnzipFolder = null;
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_STARTED, null, null, null);

        if(mApplication == null) {
            try {
                throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "mApplication == null");
            } catch (LocalizedException e) {
                mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_ERROR, null, null, e);
                LogUtil.w(TAG, "Could not initialize restore. Error: " + e.getMessage(), e);
            }
        }

        PowerManager pm = (PowerManager) mApplication.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG);
        wl.acquire(30*60*1000L /*30 minutes to be sure*/);


        try {
            String restorePath = intent.getStringExtra(AppConstants.INTENT_EXTENDED_DATA_PATH);
            String backupPwd = intent.getStringExtra(AppConstants.INTENT_EXTENDED_DATA_BACKUP_PWD);
            LogUtil.i(TAG, "Starting restore service intent from " + restorePath);

            if (StringUtil.isNullOrEmpty(restorePath) || StringUtil.isNullOrEmpty(backupPwd)) {
                LogUtil.w(TAG, "Starting restore service intent failed with restorePath = " + restorePath + " and/or backupPwd is null or empty");
                return;
            }

            mFileUtil = new FileUtil(mApplication);
            createFolderAndUnzipBackup(restorePath);
            BackupInfoModel infoModel = readBackupInfo();

            //Schluessel von Passwort erstellen
            mBackUpAesKey = generateAesKey(backupPwd, infoModel);

            //erst Account Model aus Backup holen um zu prüfen ob das Passwort(durch Entschlüsselung) stimmt
            AccountModel accModel = readAccountModel();

            //salt prüfen
            if (!isSaltFromBackupAllowed(infoModel.salt)) {
                throw new LocalizedException(LocalizedException.BACKUP_RESTORE_SALTS_NOT_EQUAL, "Backup Salt and Server Salt are not equal.");
            }

            LogUtil.i(TAG, "Restore account ...");
            restoreAccount(accModel);

            boolean restoreOldContacts = restoreContacts();

            if (!restoreOldContacts && ConfigUtil.INSTANCE.syncPrivateIndexToServer()) {
                if (RuntimeConfig.isBAMandant() && !mApplication.getContactController().existsFtsDatabase()) {
                    mApplication.getContactController().createAndFillFtsDB(true);
                }
                mApplication.getContactController().loadPrivateIndexEntriesSync();
            }

            LogUtil.i(TAG, "Restore chats ...");
            restoreChats();

            LogUtil.i(TAG, "Restore channels ...");
            restoreChannels();

            LogUtil.i(TAG, "Restore services ...");
            restoreServices();

            if (ConfigUtil.INSTANCE.syncPrivateIndexToServer()) {
                LogUtil.i(TAG, "Restore blocked contacts ...");
                restoreBlockedContacts();
            }

            LogUtil.i(TAG, "Restore account ...");
            Account account = mApplication.getAccountController().getAccount();
            if (account != null) {
                account.setState(Account.ACCOUNT_STATE_FULL);
                mApplication.getAccountController().saveOrUpdateAccount(account);

                Contact ownContact = mApplication.getContactController().getOwnContact();
                if (ownContact != null) {
                    ownContact.setAccountGuid(account.getAccountGuid());
                    mApplication.getContactController().insertOrUpdateContact(ownContact);
                    mApplication.getContactController().fillOwnContactWithAccountInfos(ownContact);
                } else {
                    final Contact newOwnContact = new Contact();
                    newOwnContact.setAccountGuid(account.getAccountGuid());
                    mApplication.getContactController().insertOrUpdateContact(newOwnContact);
                    mApplication.getContactController().fillOwnContactWithAccountInfos(newOwnContact);
                }

                //Backup eingespielt, d.h. wurde Neuinstalliert, d.h Nachfrage für Profilnamen senden soll nicht angezeigt werden
                mApplication.getPreferencesController().setSendProfileNameSet();
                mApplication.getPreferencesController().setNotificationPreviewEnabled(false, true);
            }

            LogUtil.i(TAG, "Restore old contacts ...");
            if (!restoreOldContacts) {
                //Keine Kontakte --> kein merge
                mApplication.getPreferencesController().setHasOldContactsMerged();
            }

            //Todo: Commenting out this as we are going to remove the google drive and there will not be temporary downloaded files and we don't want to delete local backup file
           /* if (mRestoreFile != null && mRestoreFile.exists()) {
                mRestoreFile.delete();
                mRestoreFile = null;
            }*/

            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_FINISHED, null, restoreOldContacts ? 1 : 0, null);
            LogUtil.i(TAG, "Restore done.");

        } catch (Exception e) {
            LogUtil.e(TAG, "Restore failed. Error: " + e.getMessage(), e);

            //DB loeschen
            try {
                mApplication.getContactController().getDao().deleteAll();
                mApplication.getSingleChatController().getChatDao().deleteAll();
                mApplication.getMessageController().getDao().deleteAll();
                mApplication.getChannelController().getDao().deleteAll();
            } catch (Exception ee) {
                LogUtil.e(TAG, "Restore failed. Exception while deleting local database: " + e.getMessage(), e);
            }

            if (e instanceof LocalizedException
                    && StringUtil.isEqual(((LocalizedException) e).getIdentifier(), LocalizedException.DECRYPT_DATA_FAILED)) {
                LocalizedException ee = new LocalizedException(LocalizedException.BACKUP_RESTORE_WRONG_PW, e);
                mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_ERROR, null, null, ee);
            } else {
                mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_ERROR, null, null, e);
            }
        } finally {
            if(wl.isHeld()) {
                wl.release();
                if(wl.isHeld()) {
                    LogUtil.w(TAG, "RestoreBackupService: Wakelock held!");
                }
            }
        }
    }

    private void restoreChannels() throws LocalizedException {
        final ChannelController channelController = mApplication.getChannelController();

        if (!ConfigUtil.INSTANCE.channelsEnabled()) {
            return;
        }

        File encryptedChannelsFile = new File(mUnzipFolder, AppConstants.BACKUP_FILE_CHANNELS);

        if (!encryptedChannelsFile.exists()) {
            return;
        }

        File decryptedChannelsFile = new File(mUnzipFolder, "channels_decrypted.json");

        SecurityUtil.decryptFileWithAes(mBackUpAesKey, null, encryptedChannelsFile, decryptedChannelsFile, true);

        JsonArray ja = getJsonArrayFromFile(decryptedChannelsFile);

        if (ja == null) {
            return;
        }

        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_CHANNELS_STARTED, null, ja.size(), null);

        final GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ToggleSettingsModel.class, new ToggleSettingsModelSerializer());

        Gson gson = gsonBuilder.create();
        Type stringToggleSettingsMap = new TypeToken<HashMap<String, ToggleSettingsModel>>() {
        }
                .getType();

        JsonArray followChannelsJsonArray = new JsonArray();
        Map<String, String> lastModifiedMap = new HashMap<>();

        for (int i = 0; i < ja.size(); i++) {
            JsonElement je = ja.get(i);

            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_CHANNELS_UPDATE, null, i, null);

            if (!je.isJsonObject()) {
                return;
            }

            JsonObject jsonObject = (JsonObject) je;

            if (!JsonUtil.hasKey("ChannelBackup", jsonObject)) {
                continue;
            }

            JsonObject channelJO = saveChannel(jsonObject, lastModifiedMap, gson, stringToggleSettingsMap);

            if (channelJO != null) {
                followChannelsJsonArray.add(channelJO);
            }
        }

        if (followChannelsJsonArray.size() > 0) {
            final JsonArray followChannelGuidsArray = setFollowedChannels(followChannelsJsonArray, false);

            if (followChannelGuidsArray != null) {
                for (JsonElement guidJE : followChannelGuidsArray) {
                    if (guidJE == null || !guidJE.isJsonPrimitive()) {
                        continue;
                    }

                    Channel channel = channelController.getChannelFromDB(guidJE.getAsString());

                    if (channel == null) {
                        continue;
                    }

                    channel.setIsSubscribed(true);

                    channelController.updateChannel(channel);

                    createChatForChannel(channel, lastModifiedMap.get(channel.getGuid()));
                }
            }
        }
    }

    private JsonObject saveChannel(@NonNull JsonObject jsonObject, Map<String, String> lastModifiedMap, Gson gson, Type stringToggleSettingsMap)
            throws LocalizedException {

        final ChannelController channelController = mApplication.getChannelController();
        JsonObject channelJO = jsonObject.getAsJsonObject("ChannelBackup");

        String guid = JsonUtil.stringFromJO("guid", channelJO);
        if (StringUtil.isNullOrEmpty(guid)) {
            return null;
        }

        String notification = JsonUtil.stringFromJO("notification", channelJO);
        boolean isNotificationDisabled = false;
        if (!StringUtil.isNullOrEmpty(notification)) {
            isNotificationDisabled = StringUtil.isEqual("disabled", notification);
        }

        String lastModifiedDate = JsonUtil.stringFromJO("lastModifiedDate", channelJO);
        if (!StringUtil.isNullOrEmpty(lastModifiedDate)) {
            lastModifiedMap.put(guid, lastModifiedDate);
        }

        ChannelModel channelModel = loadChannelModel(guid);

        if (channelModel == null) {
            return null;
        }

        List<String> idents = null;
        if (JsonUtil.hasKey("@ident", channelJO)) {
            JsonArray identsJA = channelJO.getAsJsonArray("@ident");

            if (identsJA != null && identsJA.size() > 0) {
                idents = new ArrayList<>();
                for (JsonElement identJE : identsJA) {
                    if (!identJE.isJsonPrimitive()) {
                        continue;
                    }
                    idents.add(identJE.getAsString());
                }
            }
        }

        Channel channel = new Channel();

        channel.setGuid(guid);
        channel.setChannelJsonObject(channelModel.channelJsonObject);
        channel.setShortDesc(channelModel.shortDesc);
        channel.setAesKey(channelModel.aesKey);
        channel.setIv(channelModel.iv);
        channel.setShortLinkText(channelModel.shortLinkText);
        channel.setPromotion(channelModel.promotion);
        channel.setExternalUrl(channelModel.externalUrl);
        channel.setSearchText(channelModel.searchText);
        channel.setCategory(channelModel.category);
        channel.setWelcomeText(channelModel.welcomeText);
        channel.setSuggestionText(channelModel.suggestionText);
        channel.setFeedbackContact(channelModel.feedbackContact);
        channel.setChecksum(channelModel.checksum);
        channel.setDisableNotification(isNotificationDisabled);
        channel.setType(GuidUtil.isChatService(guid) ? Channel.TYPE_SERVICE : Channel.TYPE_CHANNEL);

        channelController.loadImage(guid, ChannelController.IMAGE_TYPE_CHANNEL_BACKGROUND, true);
        channelController.loadImage(guid, ChannelController.IMAGE_TYPE_ITEM_BACKGROUND, true);
        channelController.loadImage(guid, ChannelController.IMAGE_TYPE_PROVIDER_ICON, true);
        channelController.loadImage(guid, ChannelController.IMAGE_TYPE_PROVIDER_LABEL, true);

        Map<String, ToggleSettingsModel> filterValues = new HashMap<>();
        if (idents != null && idents.size() > 0) {
            ToggleModel[] toggles = channelModel.toggles;
            if (toggles != null && toggles.length > 0) {
                for (ToggleModel toggle : toggles) {
                    if (StringUtil.isInList(toggle.ident, idents, false)) {
                        filterValues.put(toggle.ident, new ToggleSettingsModel(toggle.filterOn, "on"));
                    }
                }
            }
        }

        String filter = "";
        String filterJson = null;

        if ((filterValues.size() > 0)) {
            //Eingestellte Filter fuer Server
            filter = channelController.getSelectedTogglesFilter(filterValues);

            //Toggle-Einstellungen in json schreiben
            filterJson = gson.toJson(filterValues, stringToggleSettingsMap);
        }

        //Channel in DB aktualiseren
        channel.setFilterJsonObject(filterJson);

        channelController.getDao().insert(channel);

        return getFollowChannelObject(guid, notification != null ? notification : "enabled", filter, GuidUtil.isChatService(guid));
    }

    private void restoreServices() throws LocalizedException {
        ChannelController channelController = mApplication.getChannelController();

        File[] filesInFolder = mUnzipFolder.listFiles();

        ArrayList<File> serviceFiles = new ArrayList<>();

        for (File buFile : filesInFolder) {
            if (!buFile.isFile()) {
                continue;
            }

            String name = buFile.getName();

            if (StringUtil.isNullOrEmpty(name)) {
                continue;
            }

            if (!name.startsWith("22_")) {
                continue;
            }

            serviceFiles.add(buFile);
        }

        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_SERVICES_STARTED, null, serviceFiles.size(), null);

        final GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ToggleSettingsModel.class, new ToggleSettingsModelSerializer());
        gsonBuilder.registerTypeAdapter(Message.class, new MessageDeserializer(mApplication.getAccountController()));

        Gson gson = gsonBuilder.create();
        Type stringToggleSettingsMap = new TypeToken<HashMap<String, ToggleSettingsModel>>() {
        }
                .getType();
        Map<String, String> lastModifiedMap = new HashMap<>();

        for (int i = 0; i < serviceFiles.size(); i++) {
            File buFile = serviceFiles.get(i);

            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_SERVICES_UPDATE, null, i, null);

            String name = buFile.getName();

            File decryptedServiceFile = new File(mUnzipFolder, name + "_decrypted.json");

            SecurityUtil.decryptFileWithAes(mBackUpAesKey, null, buFile, decryptedServiceFile, true);

            JsonArray ja;
            JsonObject jo;

            try {
                ja = getJsonArrayFromFile(decryptedServiceFile);

                jo = (JsonObject) ja.get(0);
            } catch (Exception e) {
                //kein JSON
                LogUtil.w(TAG, e.getMessage(), e);
                continue;
            }

            if (!JsonUtil.hasKey("ChannelBackup", jo)) {
                continue;
            }

            JsonObject channelJO = saveChannel(jo, lastModifiedMap, gson, stringToggleSettingsMap);

            if (channelJO == null) {
                continue;
            }

            JsonArray array = new JsonArray();
            array.add(channelJO);

            JsonArray followServiceGuidsArray = setFollowedChannels(array, true);

            if (followServiceGuidsArray != null && followServiceGuidsArray.size() > 0) {
                JsonElement guidJE = followServiceGuidsArray.get(0);
                if (guidJE == null || !guidJE.isJsonPrimitive()) {
                    continue;
                }

                Channel service = channelController.getChannelFromDB(guidJE.getAsString());

                if (service == null) {
                    continue;
                }

                service.setIsSubscribed(true);

                channelController.updateChannel(service);

                mApplication.getBackupController().restoreChatMessages(ja, 1, gson, null, mUnzipFolder);
            }
        }
    }

    private Chat createChatForChannel(Channel channel, String lastMod)
            throws LocalizedException {
        Chat chat = new Chat();

        byte[] aesKeyBytes = Base64.decode(channel.getAesKey(), Base64.NO_WRAP);

        chat.setChatGuid(channel.getGuid());
        chat.setType(Chat.TYPE_CHANNEL);
        chat.setChatAESKey(new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES"));

        long lastModifiedDate = new Date().getTime();
        if (!StringUtil.isNullOrEmpty(lastMod)) {
            lastModifiedDate = DateUtil.utcStringToMillis(lastMod);
        }

        chat.setLastChatModifiedDate(lastModifiedDate);

        mApplication.getChannelChatController().insertOrUpdateChat(chat);

        return chat;
    }

    private JsonArray setFollowedChannels(@NonNull JsonArray followChannelsArray, boolean isServiceCall)
            throws LocalizedException {
        final List<JsonArray> jsonArrayContainer = new ArrayList<>(1);

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    mErrorText = response.errorMessage;
                } else if (response.jsonArray != null) {
                    jsonArrayContainer.add(response.jsonArray);
                }
            }
        };

        if (isServiceCall) {
            BackendService.withSyncConnection(mApplication)
                    .setFollowedServices(followChannelsArray.toString(), listener);
        } else {
            BackendService.withSyncConnection(mApplication)
                    .setFollowedChannels(followChannelsArray.toString(), listener);
        }

        if (!StringUtil.isNullOrEmpty(mErrorText)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_SERVER_CONNECTION_FAILED, mErrorText);
        }

        return jsonArrayContainer.size() > 0 ? jsonArrayContainer.get(0) : null;
    }

    private JsonObject getFollowChannelObject(@NonNull final String guid, @NonNull final String notification,
                                              @NonNull final String filter, final boolean isService) {
        JsonObject jsonObject = new JsonObject();
        JsonObject channelObject = new JsonObject();

        channelObject.addProperty("guid", guid);
        channelObject.addProperty("notification", notification);
        channelObject.addProperty("filter", filter);

        if (isService) {
            jsonObject.add("Service", channelObject);
        } else {
            jsonObject.add("Channel", channelObject);
        }

        return jsonObject;
    }

    private ChannelModel loadChannelModel(String guid)
            throws LocalizedException {
        final List<ChannelModel> modelContainer = new ArrayList<>(1);
        final ChannelController channelController = mApplication.getChannelController();

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    mErrorText = response.errorMessage;
                } else if (response.jsonArray != null) {
                    //SERVICE
                    for (int i = 0; i < response.jsonArray.size(); ++i) {
                        final JsonObject jobj = response.jsonArray.get(i).getAsJsonObject();

                        if (jobj != null) {
                            try {
                                ChannelModel model = channelController.getChannelModelFromJson(jobj.toString(), Channel.TYPE_SERVICE);

                                if (model != null) {
                                    modelContainer.add(model);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                                mErrorText = e.getMessage();
                            }
                        }
                    }
                } else if (response.jsonObject != null) {
                    try {
                        ChannelModel model = channelController.getChannelModelFromJson(response.jsonObject.toString(), Channel.TYPE_CHANNEL);

                        if (model != null) {
                            modelContainer.add(model);
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                        mErrorText = e.getMessage();
                    }
                }
            }
        };
        if (GuidUtil.isChatService(guid)) {
            BackendService.withSyncConnection(mApplication)
                    .getServiceDetailsBatch(guid, listener);
        } else {
            BackendService.withSyncConnection(mApplication)
                    .getChannelDetails(guid, listener);
        }

        if (!StringUtil.isNullOrEmpty(mErrorText)) {
            // Nicht schoen, wenn vom Server der Channel geladen konnte(kann ja auch geloescht wurden sein)
            // aber deswegen wird der Restore nicht abgebrochen
            LogUtil.e(TAG, mErrorText);
        }

        return modelContainer.size() > 0 ? modelContainer.get(0) : null;
    }

    private void restoreChats() throws LocalizedException {
        final Account account = mApplication.getAccountController().getAccount();
        final String ownAccountGuid = account.getAccountGuid();

        final File[] filesInFolder = mUnzipFolder.listFiles();

        final ArrayList<File> chatFiles = new ArrayList<>();

        for (final File buFile : filesInFolder) {
            if (!buFile.isFile()) {
                continue;
            }

            final String name = buFile.getName();

            if (StringUtil.isNullOrEmpty(name)) {
                continue;
            }

            if (!name.startsWith("0_") && !name.startsWith("7_")) {
                continue;
            }

            chatFiles.add(buFile);
        }

        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_CHATS_STARTED, null, chatFiles.size(), null);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Message.class, new MessageDeserializer(mApplication.getAccountController()));
        gsonBuilder.registerTypeAdapter(Chat.class, new ChatBackupDeserializer());

        final Gson gson = gsonBuilder.create();

        final ChatDao chatDao = mApplication.getChatDao();

        final List<String> timedMsgGuids = mApplication.getMessageController().getTimedMessagesGuids();
        final Map<String, ChatRoomModel> chatRoomModelMap = mApplication.getBackupController().getCurrentRoomInfos();

        for (int i = 0; i < chatFiles.size(); i++) {
            final File buFile = chatFiles.get(i);
            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_CHATS_UPDATE, null, i, null);

            final String name = buFile.getName();

            final File decryptedChatFile = new File(mUnzipFolder, name + "_decrypted.json");

            SecurityUtil.decryptFileWithAes(mBackUpAesKey, null, buFile, decryptedChatFile, true);

            JsonArray ja;
            JsonObject jo;

            try {
                ja = getJsonArrayFromFile(decryptedChatFile);

                jo = (JsonObject) ja.get(0);
            } catch (final Exception e) {
                //kein JSON
                LogUtil.w(TAG, e.getMessage(), e);
                continue;
            }

            final Chat chat = gson.fromJson(jo, Chat.class);

            if (chat == null) {
                continue;
            }

            if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION || chat.getType() == Chat.TYPE_GROUP_CHAT) {
                if (!mApplication.getBackupController().setChatRoomInfos(chat, chatRoomModelMap, gson)) {
                    continue;
                }
            }

            chatDao.insert(chat);

            mApplication.getBackupController().restoreChatMessages(ja, 1, gson, timedMsgGuids, mUnzipFolder);
        }

        // aus Chats austreten, in denen man vor dem Backup noch nicht eingetreten war
        String encodedName = null;
        if (account.getName() != null) {
            encodedName = Base64.encodeToString(account.getName().getBytes(), Base64.NO_WRAP);
        }

        for (final ChatRoomModel chatRoomModel : chatRoomModelMap.values()) {
            if (GuidUtil.isChatRoom(chatRoomModel.guid)) {
                BackendService.withSyncConnection(mApplication)
                        .removeFromRoom(chatRoomModel.guid, ownAccountGuid, encodedName, null);
            }
        }

        if (timedMsgGuids.size() > 0) {
            mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_TIMED_MESSAGES, null, chatFiles.size(), null);
            List<String> attachmentGuids = mApplication.getMessageController().loadTimedMessages(timedMsgGuids);

            if (attachmentGuids != null && attachmentGuids.size() > 0) {
                for (String attachmentGuid : attachmentGuids) {
                    try {
                        copyAttachmentToAttachmentsDir(attachmentGuid);
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "No Attachment found!", e);
                    }
                }
            }
        }
    }

    private void copyAttachmentToAttachmentsDir(String attachmentGuid)
            throws LocalizedException {
        File backupAttachmentDir = new File(mUnzipFolder, AppConstants.BACKUP_ATTACHMENT_DIR);

        if (!backupAttachmentDir.isDirectory()) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Attachment File.class is no Directory");
        }

        String fileName = attachmentGuid.replace(':', '_');
        File attachmentBuFile = new File(backupAttachmentDir, fileName);

        AttachmentController.saveBase64FileAsEncryptedAttachment(attachmentGuid, attachmentBuFile.getAbsolutePath());
    }

    private boolean existsFileForGuid(@NonNull String guid) {
        String fileName = guid.replace(':', '_');
        File backupFile = new File(mUnzipFolder, fileName + AppConstants.JSON_FILE_EXTENSION);

        return backupFile.exists();
    }

    private boolean restoreContacts() throws LocalizedException {
        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_CONTACTS, null, null, null);

        File encryptedContactsFile = new File(mUnzipFolder, AppConstants.BACKUP_FILE_CONTACTS);

        if (!encryptedContactsFile.exists()) {
            //bei neuen Backup > SIMSme Version 2.0.2 gibt es keine Kontakte mehr
            return false;
        }

        File decryptedContactsFile = new File(mUnzipFolder, "contacts_decrypted.json");

        SecurityUtil.decryptFileWithAes(mBackUpAesKey, null, encryptedContactsFile, decryptedContactsFile, true);

        JsonArray buContactsArray = getJsonArrayFromFile(decryptedContactsFile);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Contact.class, new ContactBackupDeserializer());

        Gson gson = gsonBuilder.create();

        Contact[] contacts = gson.fromJson(buContactsArray, Contact[].class);

        String[] blockedContactGuids = getBlockedContacts();

        ContactDao contactDao = mApplication.getContactController().getDao();

        if (contacts != null) {
            boolean restoreOldContacts = true;

            for (Contact contact : contacts) {
                if (contact == null) {
                    continue;
                }

                String guid = contact.getAccountGuid();
                if (blockedContactGuids != null) {
                    for (String blockedGuid : blockedContactGuids) {
                        if (blockedGuid.equalsIgnoreCase(guid)) {
                            contact.setIsBlocked(true);
                            break;
                        }
                    }
                }

                contact.setIsFirstContact(existsFileForGuid(contact.getAccountGuid()));

                if (!StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                    //Contact hat SIMSme ID, daher sind es keine alten Kontakte
                    restoreOldContacts = false;
                }

                contactDao.insert(contact);
            }
            return restoreOldContacts;
        }

        return false;
    }

    private boolean restoreBlockedContacts() throws LocalizedException {
        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_CONTACTS, null, null, null);

        String[] blockedContactGuids = getBlockedContacts();

        if (blockedContactGuids == null) {
            return true;
        }

        for (String blockedContactGuid : blockedContactGuids) {
            mApplication.getContactController().blockContact(blockedContactGuid, true, false, null);
        }

        return true;
    }

    @Nullable
    private String[] getBlockedContacts() throws LocalizedException {
        final List<String[]> guids = new ArrayList<>(1);

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    mErrorText = response.errorMessage;
                } else if (response.jsonArray != null) {
                    final GsonBuilder gsonBuilder = new GsonBuilder();
                    Gson gson = gsonBuilder.create();
                    String[] guidArray = gson.fromJson(response.jsonArray, String[].class);
                    if (guidArray != null && guidArray.length > 0) {
                        guids.add(guidArray);
                    }
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .getBlocked(listener);

        if (!StringUtil.isNullOrEmpty(mErrorText)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_SERVER_CONNECTION_FAILED, mErrorText);
        }

        return guids.size() > 0 ? guids.get(0) : null;
    }

    private void restoreAccount(@NonNull final AccountModel accountModel)
            throws LocalizedException {
        mBroadcaster.broadcastIntentWithState(AppConstants.STATE_ACTION_RESTORE_BACKUP_ACCOUNT, null, null, null);

        final DeviceModel deviceModel = mApplication.getBackupController().createDeviceModel(accountModel, null, null);

        final GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(DeviceModel.class, new DeviceModelSerializer());
        Gson gson = gsonBuilder.create();

        JsonArray jsonArray = new JsonArray();
        jsonArray.add(gson.toJsonTree(deviceModel));
        String deviceJson = jsonArray.toString();

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    mErrorText = response.errorMessage;
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .createDevice(accountModel.guid, accountModel.backupPasstoken, deviceJson, accountModel.phone, listener);

        if (!StringUtil.isNullOrEmpty(mErrorText)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_ACCOUNT_SERVER_CONNECTION_FAILED, mErrorText);
        }

        mApplication.getBackupController().updateAccountAndDevice(accountModel, deviceModel);

        mApplication.getAccountController().updateAccountInfoFromServer(true, true);

        mApplication.getAccountController().doActionsAfterRestoreAccountFromBackup(accountModel.accountBackupJO);
    }

    @NonNull
    private AccountModel readAccountModel() throws LocalizedException {
        File encryptedAccountFile = new File(mUnzipFolder, AppConstants.BACKUP_FILE_ACCOUNT);

        if (!encryptedAccountFile.exists()) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "No Account File found.");
        }

        File decryptedAccountFile = new File(mUnzipFolder, "account_decrypted.json");

        SecurityUtil.decryptFileWithAes(mBackUpAesKey, null, encryptedAccountFile, decryptedAccountFile, true);

        JsonObject buAccountObj = getJsonObjectFromFile(AppConstants.BACKUP_JSON_ACCOUNT_OBJECT_KEY, decryptedAccountFile);

        return mApplication.getBackupController().getAccountModelFromJson(buAccountObj, true);
    }

    @NonNull
    private SecretKey generateAesKey(@NonNull String pwd, @NonNull BackupInfoModel infoModel) throws LocalizedException {
        byte[] salt = Base64.decode(infoModel.pbdkfSalt, Base64.NO_WRAP);
        int rounds = Integer.parseInt(infoModel.pbdkfRounds);

        return SecurityUtil.deriveKeyFromPasswordOnSameThread(pwd, salt, rounds);
    }

    @NonNull
    private BackupInfoModel readBackupInfo() throws LocalizedException {
        try {
            File buInfoFile = new File(mUnzipFolder, AppConstants.BACKUP_FILE_INFO);

            if (!buInfoFile.exists()) {
                throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "No Backup Info File found.");
            }

            JsonObject buInfoObj = getJsonObjectFromFile(AppConstants.BACKUP_JSON_INFO_OBJECT_KEY, buInfoFile);

            BackupInfoModel model = new BackupInfoModel();

            model.version = buInfoObj.get(AppConstants.BACKUP_JSON_INFO_VERSION_KEY).getAsString();
            model.app = buInfoObj.get(AppConstants.BACKUP_JSON_INFO_APP_KEY).getAsString();
            model.salt = buInfoObj.get(AppConstants.BACKUP_JSON_INFO_SALT_KEY).getAsString();
            model.pbdkfSalt = buInfoObj.get(AppConstants.BACKUP_JSON_INFO_PBDKF_SALT_KEY).getAsString();
            model.pbdkfRounds = buInfoObj.get(AppConstants.BACKUP_JSON_INFO_PBDKF_ROUNDS_KEY).getAsString();

            return model;
        } catch (Exception e) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, e.getMessage(), e);
        }
    }

    private JsonObject getJsonObjectFromFile(String jsonObjectKey, File jsonFile) throws LocalizedException {
        try {
            JsonArray ja = getJsonArrayFromFile(jsonFile);

            JsonObject jo = (JsonObject) ja.get(0);

            if (jsonObjectKey == null) {
                return jo;
            } else {
                return (JsonObject) jo.get(jsonObjectKey);
            }
        } catch (Exception e) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, e.getMessage(), e);
        }
    }

    private JsonArray getJsonArrayFromFile(File jsonFile) throws LocalizedException {
        try {
            FileReader fr = new FileReader(jsonFile);
            JsonStreamParser jsonStreamParser = new JsonStreamParser(fr);

            if (!jsonStreamParser.hasNext()) {
                throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "No JSON in File found.");
            }

            JsonElement je = jsonStreamParser.next();

            return je.getAsJsonArray();
        } catch (NullPointerException | FileNotFoundException e) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, e.getMessage(), e);
        }
    }

    private void createFolderAndUnzipBackup(String restorePath) throws LocalizedException {
        mRestoreFile = new File(restorePath);

        if (!mRestoreFile.exists() || !mRestoreFile.isFile()) {
            return;
        }

        mUnzipFolder = new File(mFileUtil.getBackupDirectory(), "Unzipped");

        if (mUnzipFolder.exists()) {
            if (mFileUtil.deleteAllFilesInDir(mUnzipFolder)) {
                if (!mUnzipFolder.delete()) {
                    throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Old Unzip Folder can not be deleted.");
                }
                mUnzipFolder = new File(mFileUtil.getBackupDirectory(), "Unzipped");
            } else {
                throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Old Unzip Folder Files can not be deleted.");
            }
        }

        if (!mUnzipFolder.mkdir()) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "mkdir() Unzip Folder failed.");
        }

        ZipUtils uzu = new ZipUtils(mRestoreFile.getAbsolutePath(), mUnzipFolder.getAbsolutePath());
        uzu.startUnzip();
    }

    private boolean isSaltFromBackupAllowed(final String salt) {
        List<String> saltList = RuntimeConfig.getAllowedBackupSalts();

        if (saltList != null && saltList.size() > 0) {
            return saltList.indexOf(salt) > -1;
        }

        return false;
    }

    private class BackupInfoModel {
        String version;
        String app;
        String salt;
        String pbdkfSalt;
        String pbdkfRounds;
    }
}
