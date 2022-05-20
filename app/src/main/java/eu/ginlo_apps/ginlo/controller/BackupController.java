// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.crypto.SecretKey;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.contracts.QueryDatabaseListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.ChatDao;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.AccountModel;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ChatRoomModel;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChatBackupDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChatRoomModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.DeviceModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageDeserializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StorageUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

public class BackupController {
    private static final String TAG = "BackupController";
    private final SimsMeApplication mApplication;
    private final StorageUtil mStorageUtil;

    public BackupController(final SimsMeApplication application) {
        mApplication = application;
        mStorageUtil = new StorageUtil(application);
    }

    void restoreMiniBackup(@NonNull final JsonArray miniBackupJA, @NonNull final String transid, @NonNull final String publicKeySign, @NonNull final String deviceGuid)
            throws LocalizedException {

        Map<String, ChatRoomModel> chatRoomModelMap = null;

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Message.class, new MessageDeserializer(mApplication.getAccountController()));
        gsonBuilder.registerTypeAdapter(Chat.class, new ChatBackupDeserializer());

        final Gson gson = gsonBuilder.create();
        final ChatDao chatDao = mApplication.getChatDao();

        for (int i = 0; i < miniBackupJA.size(); i++) {
            JsonElement je = miniBackupJA.get(i);

            if (je.isJsonObject()) {
                JsonObject jo = je.getAsJsonObject();

                if (jo.has(AppConstants.BACKUP_JSON_ACCOUNT_OBJECT_KEY)) {
                    restoreAccountFromMiniBackup(jo, transid, publicKeySign, deviceGuid);
                }
            } else if (je.isJsonArray()) {
                JsonArray ja = je.getAsJsonArray();

                if (ja.size() > 0) {
                    for (JsonElement jsonElement : ja) {
                        if (!jsonElement.isJsonObject()) {
                            continue;
                        }

                        JsonObject jo = jsonElement.getAsJsonObject();

                        if (jo.has(JsonConstants.BACKUP_CHAT_SINGLE) ||
                                jo.has(JsonConstants.BACKUP_CHAT_GROUP)) {
                            final Chat chat = gson.fromJson(jo, Chat.class);

                            if (chat == null) {
                                continue;
                            }

                            if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION || chat.getType() == Chat.TYPE_GROUP_CHAT) {
                                if (chatRoomModelMap == null) {
                                    chatRoomModelMap = getCurrentRoomInfos();
                                }

                                if (!setChatRoomInfos(chat, chatRoomModelMap, gson)) {
                                    continue;
                                }
                            }

                            chatDao.insert(chat);
                            JsonObject chatJO = jo.has(JsonConstants.BACKUP_CHAT_SINGLE) ? jo.getAsJsonObject(JsonConstants.BACKUP_CHAT_SINGLE) : jo.getAsJsonObject(JsonConstants.BACKUP_CHAT_GROUP);

                            if (chatJO != null) {
                                if (chatJO.has(JsonConstants.MESSAGES)) {
                                    JsonElement msgsJe = chatJO.get(JsonConstants.MESSAGES);
                                    if (msgsJe.isJsonArray()) {
                                        JsonArray messages = msgsJe.getAsJsonArray();
                                        long lastMsgId = restoreChatMessages(messages, 0, gson, null, false);
                                        if (lastMsgId > -1) {
                                            chat.setLastMsgId(lastMsgId);
                                            chatDao.update(chat);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean setChatRoomInfos(Chat chat, final Map<String, ChatRoomModel> chatRoomModelMap, Gson gson)
            throws LocalizedException {
        final String chatGuidTS = chat.getChatGuid();
        final ChatRoomModel model = chatRoomModelMap.get(chatGuidTS);
        if (model != null) {
            //Pruefen ob eigener Account noch Mitglied ist
            if (!StringUtil.isInArray(mApplication.getAccountController().getAccount().getAccountGuid(), model.member, true)
                    && !Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
                return false;
            }

            if (model.admins != null && model.admins.length > 0) {
                chat.setAdmins(gson.toJsonTree(model.admins).getAsJsonArray());
            }

            if (model.member != null && model.member.length > 0) {
                chat.setMembers(gson.toJsonTree(model.member).getAsJsonArray());
            }

            chat.setOwner(model.owner);

            if (!StringUtil.isNullOrEmpty(model.keyIv) && !StringUtil.isNullOrEmpty(model.data)) {
                chat.setChatInfoIVAsBase64(model.keyIv);

                final String jsonData = SecurityUtil.decryptBase64StringWithAES(model.data, chat.getChatAESKey(), chat.getChatInfoIV());

                final JsonObject jsonObject = JsonUtil.getJsonObjectFromString(jsonData);

                if (jsonObject != null) {
                    final String chatName = JsonUtil.stringFromJO("GroupName", jsonObject);

                    if (!StringUtil.isNullOrEmpty(chatName)) {
                        chat.setTitle(chatName);
                    }

                    final String groupImageAsBase64 = JsonUtil.stringFromJO("GroupImage", jsonObject);

                    if (!StringUtil.isNullOrEmpty(groupImageAsBase64)) {
                        final byte[] image = Base64.decode(groupImageAsBase64, Base64.NO_WRAP);

                        if (image != null) {
                            final ChatImageController chatImageController = mApplication.getChatImageController();
                            chatImageController.saveImage(chatGuidTS, image);
                        }
                    }
                }
            } else if (chat.getGroupChatImage() != null) {
                mApplication.getChatImageController().saveImage(chatGuidTS, chat.getGroupChatImage());
            }

            if (model.confirmed && chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
                chat.setType(Chat.TYPE_GROUP_CHAT);
            }

            for (int ii = 0; ii < chat.getMembers().size(); ii++) {
                final String guid = chat.getMembers().get(ii).getAsString();

                // KS: Old/missing contacts/keys should not lead to backup restore failure!
                // Keep chat or remove it? TODO: Check that out - remove it for now!
                //mApplication.getContactController().createContactIfNotExists(guid, null, null, true);
                try {
                    mApplication.getContactController().createContactIfNotExists(guid, null, null, true);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "setChatRoomInfos: Could not createContactIfNotExists() for Guid " + guid + "(" + e.getMessage() + ")");
                    chat.setIsRemoved(true);
                    break;
                }
            }
        } else {
            chat.setIsRemoved(true);
            if (chat.getGroupChatImage() != null) {
                mApplication.getChatImageController().saveImage(chatGuidTS, chat.getGroupChatImage());
            }
        }
        chatRoomModelMap.remove(chatGuidTS);

        return true;
    }

    private void restoreAccountFromMiniBackup(@NonNull final JsonObject accountBackupJO,
                                              @NonNull final String transid,
                                              @NonNull final String publicKeySign,
                                              @NonNull final String deviceGuid)
            throws LocalizedException {
        JsonObject accountJO = accountBackupJO.getAsJsonObject(AppConstants.BACKUP_JSON_ACCOUNT_OBJECT_KEY);
        if (accountJO == null) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "No Account Backup Object");
        }

        //Account einspielen --->
        final AccountModel accountModel = getAccountModelFromJson(accountJO, false);
        final DeviceModel deviceModel = createDeviceModel(accountModel, deviceGuid, publicKeySign);

        final GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(DeviceModel.class, new DeviceModelSerializer());
        Gson gson = gsonBuilder.create();

        JsonElement deviceJO = gson.toJsonTree(deviceModel);
        String deviceJson = deviceJO.toString();

        final ResponseModel rm = new ResponseModel();
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                }
            }
        };
        updateAccountAndDevice(accountModel, deviceModel);
        BackendService.withSyncConnection(mApplication)
                .createAdditionalDevice(accountModel.guid, transid, deviceJson, listener);

        if (rm.isError) {
            String ident = StringUtil.isNullOrEmpty(rm.errorIdent) ? LocalizedException.BACKUP_RESTORE_BACKUP_FAILED : rm.errorIdent;
            throw new LocalizedException(ident, "createAdditionalDevice failed! " + rm.errorMsg);
        }

        mApplication.getLoginController().loginCompleteSuccess(null, null, null);
        mApplication.getAccountController().updateAccountInfoFromServer(true, true);
        mApplication.getAccountController().doActionsAfterRestoreAccountFromBackup(accountModel.accountBackupJO);
        //Account einspielen <----

        // SIMSme System Accopunt laden
        final CountDownLatch latch = new CountDownLatch(1);
        final ContactController.OnSystemChatCreatedListener onSystemChatCreatedListener = new ContactController.OnSystemChatCreatedListener() {
            @Override
            public void onSystemChatCreatedSuccess() {
                latch.countDown();
            }

            @Override
            public void onSystemChatCreatedError(String message) {
                latch.countDown();
            }
        };

        mApplication.getContactController().createSystemChatContact(onSystemChatCreatedListener);

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new LocalizedException(LocalizedException.BACKEND_REQUEST_FAILED);
        }

        if (RuntimeConfig.isBAMandant() && !mApplication.getContactController().existsFtsDatabase()) {
            //
            mApplication.getContactController().createAndFillFtsDB(true);
        }

        // Restliches Verzeichnis laden
        // Verzeichnis laden
        mApplication.getContactController().loadPrivateIndexEntriesSync();
    }

    public AccountModel getAccountModelFromJson(@NonNull final JsonObject buAccountObj, final boolean isAccountBackup)
            throws LocalizedException {
        AccountModel accModel = new AccountModel();

        accModel.accountBackupJO = buAccountObj;
        accModel.guid = JsonUtil.stringFromJO(AppConstants.JSON_KEY_GUID, buAccountObj);

        if (StringUtil.isNullOrEmpty(accModel.guid)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Account has no guid!");
        }

        accModel.nickname = JsonUtil.stringFromJO(AppConstants.BACKUP_JSON_ACCOUNT_NICKNAME_KEY, buAccountObj);
        accModel.phone = JsonUtil.stringFromJO(AppConstants.BACKUP_JSON_ACCOUNT_PHONE_KEY, buAccountObj);

        accModel.profileKey = JsonUtil.stringFromJO(AppConstants.BACKUP_JSON_ACCOUNT_PROFILE_KEY_KEY, buAccountObj);
        accModel.backupPasstoken = JsonUtil.stringFromJO(AppConstants.BACKUP_JSON_ACCOUNT_BACKUP_PASSTOKEN_KEY, buAccountObj);

        if (isAccountBackup && StringUtil.isNullOrEmpty(accModel.backupPasstoken)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Account has Backup no Passtoken!");
        }

        accModel.privateKey = JsonUtil.stringFromJO(AppConstants.BACKUP_JSON_ACCOUNT_PRIVATE_KEY_KEY, buAccountObj);

        if (StringUtil.isNullOrEmpty(accModel.privateKey)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Account has no private key!");
        }

        accModel.publicKey = JsonUtil.stringFromJO(AppConstants.BACKUP_JSON_ACCOUNT_PUBLIC_KEY_KEY, buAccountObj);

        if (StringUtil.isNullOrEmpty(accModel.publicKey)) {
            throw new LocalizedException(LocalizedException.BACKUP_RESTORE_BACKUP_FAILED, "Account has no public key!");
        }

        accModel.accountID = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, buAccountObj);

        return accModel;
    }

    public DeviceModel createDeviceModel(@NonNull final AccountModel accountModel, @Nullable final String deviceGuid, @Nullable final String publicKeySign)
            throws LocalizedException {
        KeyController keyController = mApplication.getKeyController();
        DeviceModel deviceModel = new DeviceModel();

        deviceModel.guid = StringUtil.isNullOrEmpty(deviceGuid) ? GuidUtil.generateDeviceGuid() : deviceGuid;
        deviceModel.accountGuid = accountModel.guid;
        deviceModel.publicKey = XMLUtil.getXMLFromPublicKey(keyController.getDeviceKeyPair().getPublic());

        if (StringUtil.isNullOrEmpty(publicKeySign)) {
            PrivateKey pk;
            try {
                pk = keyController.getUserKeyPair().getPrivate();
            } catch (LocalizedException e) {
                if (!StringUtil.isNullOrEmpty(accountModel.privateKey)) {
                    pk = XMLUtil.getPrivateKeyFromXML(accountModel.privateKey);

                    if (pk == null) {
                        throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Private Key not available", e);
                    }
                } else {
                    throw e;
                }
            }

            deviceModel.pkSign = mApplication.getDeviceController().getSignedDevicePublicKeyFingerprint(deviceModel.publicKey, pk);
        } else {
            deviceModel.pkSign = publicKeySign;
        }

        deviceModel.passtoken = GuidUtil.generatePassToken();
        deviceModel.language = Locale.getDefault().getLanguage();
        deviceModel.appName = AppConstants.getAppName();
        deviceModel.appVersion = AppConstants.getAppVersionName();
        deviceModel.os = AppConstants.OS;
        deviceModel.featureVersion = AppConstants.getAppFeatureVersions();

        return deviceModel;
    }

    public Account updateAccountAndDevice(final AccountModel accountModel, final DeviceModel deviceModel)
            throws LocalizedException {
        AccountController acctrl = mApplication.getAccountController();

        Account account = acctrl.getAccount();

        if (account == null) {
            account = new Account();
        }

        account.setAccountGuid(accountModel.guid);

        // KS: Deprecated
        // account.setPhoneNumber(accountModel.phone);

        account.setName(accountModel.nickname);
        account.setPrivateKey(accountModel.privateKey);
        account.setPublicKey(accountModel.publicKey);
        account.setAccountInfosAesKey(accountModel.profileKey);
        account.setAccountID(accountModel.accountID);
        account.setPasstoken(deviceModel.passtoken);
        account.setDeviceGuid(deviceModel.guid);

        account.setState(StringUtil.isNullOrEmpty(account.getName()) ? Account.ACCOUNT_STATE_CONFIRMED : Account.ACCOUNT_STATE_FULL);

        // Lizenz wird später neu geladen, erstmal können wir davon ausgehen, dass wir eine gültige Lizenz haben, da das koppeln ja ging....
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 1);
        account.setLicenceDate(cal.getTimeInMillis());

        mApplication.getAccountController().saveOrUpdateAccount(account);
        mApplication.getKeyController().reloadUserKeypairFromAccount();
        mApplication.getDeviceController().createDeviceFromModel(deviceModel, true);

        return account;
    }

    public Map<String, ChatRoomModel> getCurrentRoomInfos() throws LocalizedException {
        final Map<String, ChatRoomModel> modelContainer = new HashMap<>();
        final ResponseModel rm = new ResponseModel();

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                } else if (response.jsonArray != null) {
                    //TODO ROOMINFO auswerten
                    final GsonBuilder gsonBuilder = new GsonBuilder();
                    gsonBuilder.registerTypeAdapter(ChatRoomModel.class, new ChatRoomModelDeserializer());
                    Gson gson = gsonBuilder.create();
                    ChatRoomModel[] chatRoomModels = gson.fromJson(response.jsonArray, ChatRoomModel[].class);

                    if (chatRoomModels != null && chatRoomModels.length > 0) {
                        for (ChatRoomModel model : chatRoomModels) {
                            modelContainer.put(model.guid, model);
                        }
                    }
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .getCurrentRoomInfo(listener);

        if (rm.isError) {
            String ident = StringUtil.isNullOrEmpty(rm.errorIdent) ? LocalizedException.BACKUP_RESTORE_BACKUP_FAILED : rm.errorIdent;
            throw new LocalizedException(ident, rm.errorMsg);
        }

        return modelContainer;
    }

    /**
     * @param ja                JsonArray mit messages
     * @param indexStartPos     Position ab wo Messages Objekte im Array sind
     * @param gson              gson
     * @param timedMsgGuids     time message guids
     * @param saveToFolder      save copy in attachment folder
     * @return last message id
     */
    public long restoreChatMessages(JsonArray ja, int indexStartPos, Gson gson, @Nullable List<String> timedMsgGuids, boolean saveToFolder) {
        final SimsMeApplication app = mApplication;
        String accountGuid = app.getAccountController().getAccount().getAccountGuid();
        long lastMsgId = -1;

        MessageDao messageDao = app.getMessageController().getDao();

        for (int i = indexStartPos; i < ja.size(); i++) {
            JsonElement je = ja.get(i);
            if (je.isJsonObject()) {
                Message msg = gson.fromJson(je, Message.class);

                if (msg == null) {
                    continue;
                }

                MessageController.checkAndSetSendMessageProps(msg, accountGuid);

                if (!StringUtil.isNullOrEmpty(msg.getAttachment()) && saveToFolder) {
                    try {
                        copyBase64AttachmentToAttachmentsDir(msg.getAttachment());
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "No Attachment found: " + e.getMessage());
                    }
                }

                //TODO set hidden if msg.getDateSendTimed() is in past
                //msg.getDateSendTimed()

                messageDao.insert(msg);

                if (i + 1 == ja.size()) {
                    lastMsgId = msg.getId();
                }

                if (timedMsgGuids != null) {
                    timedMsgGuids.remove(msg.getGuid());
                }
            }
        }

        return lastMsgId;
    }

    public void copyAttachmentToAttachmentsDirAsBase64(String attachmentGuid)
            throws LocalizedException {
        File backupDir =  mStorageUtil.getCurrentInternalBackupAttachmentDirectory(false);

        String fileName = attachmentGuid.replace(':', '_');
        File attachmentBuFile = new File(backupDir, fileName);

        AttachmentController.saveEncryptedAttachmentFileAsBase64File(attachmentGuid, attachmentBuFile.getAbsolutePath());
    }

    public void copyBase64AttachmentToAttachmentsDir(String attachmentGuid)
            throws LocalizedException {
        File backupAttachmentDir = mStorageUtil.getCurrentInternalBackupAttachmentDirectory(false);

        String fileName = attachmentGuid.replace(':', '_');
        File attachmentBuFile = new File(backupAttachmentDir, fileName);

        AttachmentController.saveBase64FileAsEncryptedAttachment(attachmentGuid, attachmentBuFile.getAbsolutePath());
    }

    public JsonArray createMiniBackup()
            throws LocalizedException {
        JsonArray rc = new JsonArray();

        rc.add(accountBackup(true));
        rc.add(singleChatBackup());
        rc.add(groupChatBackup());

        return rc;
    }

    public JsonObject accountBackup(boolean bMiniBackup)
            throws LocalizedException {
      /*
          {
          "AccountBackup":{ "guid":"0:{043dd956-72e9-43f0-b7b8-568aaf2cde36}", "nickname":"Yves",
          "phone":"+493614407231",
          "publicKey":"<RSAKeyValue><Modulus>pr7lSu0TpcbweaGNnshrqJR4Prx/QEK/LVwkezxCstHONG0oKrNmfFyJINNN psfWwZlVe3JVX03pNMCrWybV6YttIhUaGEPkEATJGQiqvLu00rlJW292cyF8AHuAxOSyYCqD661Er7oE0JuMc+njY+ dQ65t3eXNdhEkyb9Z9xCMwDItMusQfmvFGDnY/s/BIZEClbja0rX1E+Ep2ztOVb0qHX19ZPFTghzL4CkEqhh0m6t0W D/xf4Iwdjpre4IJZ6gQ2zDdIIMVrr/abCV6EvDXiU+PF8tZqIAeQJegZk6BJsW395Rl4MoVDPSULQDBkbbO0GGRjV7Tin 3Ha6+3mEw==</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>",
          "privateKey":"<RSAKeyValue><Modulus>pr7lSu0TpcbweaGNnshrqJR4Prx/QEK/LVwkezxCstHONG0oKrNmfFyJINN NpsfWwZlVe3JVX03pNMCrWybV6YttIhUaGEPkEATJGQiqvLu00rlJW292cyF8AHuAxOSyYCqD661Er7oE0JuMc+njY +dQ65t3eXNdhEkyb9Z9xCMwDItMusQfmvFGDnY/s/BIZEClbja0rX1E+Ep2ztOVb0qHX19ZPFTghzL4CkEqhh0m6t0W D/xf4Iwdjpre4IJZ6gQ2zDdIIMVrr/abCV6EvDXiU+PF8tZqIAeQJegZk6BJsW395Rl4MoVDPSULQDBkbbO0GGRjV7Tin 3Ha6+3mEw==</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>",
          "profileKey":"DDEAE8DFA5DED938B500C6DDEEDB242BF83A1FBEF16D75483F3E69BD8BD61271", "backupPasstoken":"{0fa7ee3c-7367-41a7-82a6-d486263ebfa9}",
          }
          */
        Account account = mApplication.getAccountController().getAccount();

        JsonObject innerData = new JsonObject();

        innerData.addProperty("guid", account.getAccountGuid());

        // Nickname + phone usw.
        innerData.addProperty("nickname", account.getName());

        // KS: Deprecated
        /*
        if (account.getPhoneNumber() != null) {
            innerData.addProperty("phone", account.getPhoneNumber());
        }
         */
        innerData.addProperty("phone", "");

        innerData.addProperty("profileKey", account.getAccountInfosAesKey());

        innerData.addProperty("publicKey", account.getPublicKey());
        innerData.addProperty("accountID", account.getAccountID());
        innerData.addProperty("privateKey", account.getPrivateKey());
        innerData.addProperty("mandant", RuntimeConfig.getMandant());

        if (!bMiniBackup) {
            String backupPasstoken = account.getBackupPasstoken();

            if (StringUtil.isNullOrEmpty(backupPasstoken)) {
                return null;
            }
            innerData.addProperty("backupPasstoken", backupPasstoken);
        }

        final JsonObject managementCompany = account.getManagementCompany();
        if (managementCompany != null && JsonUtil.hasKey("publicKey", managementCompany)) {
            JsonObject miniDict = new JsonObject();
            miniDict.addProperty("companyPublicKey", managementCompany.get("publicKey").getAsString());

            // companyKey übertragen
            SecretKey sk = mApplication.getAccountController().getCompanyAesKey();
            if (sk != null) {
                String companyKey = SecurityUtil.getBase64StringFromAESKey(sk);
                miniDict.addProperty(JsonConstants.COMPANY_KEY, companyKey);
            }

            SecretKey uk = mApplication.getAccountController().getCompanyUserAesKey();
            if (uk != null) {
                String userDataKey = SecurityUtil.getBase64StringFromAESKey(uk);
                miniDict.addProperty(JsonConstants.COMPANY_USER_DATA_KEY, userDataKey);
            }
            miniDict.addProperty("state", managementCompany.get("state").getAsString());
            miniDict.addProperty("guid", managementCompany.get("guid").getAsString());
            miniDict.addProperty("name", managementCompany.get("name").getAsString());

            innerData.add("companyInfo", miniDict);

            Contact ownContact = mApplication.getContactController().getOwnContact();

            if (ownContact != null && !StringUtil.isNullOrEmpty(ownContact.getDomain())) {
                innerData.addProperty("companyDomain", ownContact.getDomain());
            }
        }

        JsonObject accountBackup = new JsonObject();

        accountBackup.add("AccountBackup", innerData);

        return accountBackup;
    }

    private JsonArray contactBackup()
            throws LocalizedException {
        JsonArray rc = new JsonArray();
        ArrayList<Contact> allContacts = mApplication.getContactController().loadSimsMeContacts();

        for (Contact contact : allContacts) {
            JsonObject dataJO = contact.exportPrivateIndexEntryData();

            byte[] imgBytes = mApplication.getChatImageController().loadImage(contact.getAccountGuid());
            if (imgBytes != null && imgBytes.length > 0) {
                String imgBase64 = Base64.encodeToString(imgBytes, Base64.DEFAULT);
                dataJO.addProperty(JsonConstants.IMAGE, imgBase64);
            }
            JsonObject contactBackup = new JsonObject();
            contactBackup.add("ContactBackup", dataJO);
            rc.add(contactBackup);
        }

        return rc;
    }

    private JsonArray singleChatBackup()
            throws LocalizedException {
        JsonArray rc = new JsonArray();
        List<Chat> allChats = mApplication.getSingleChatController().loadAll();

        for (Chat chat : allChats) {
            if (chat.getType() == null) {
                continue;
            }
            if (chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION || chat.getType() == Chat.TYPE_SINGLE_CHAT) {
                JsonObject innerData = new JsonObject();

                LogUtil.d(TAG, "singleChatBackup: Processing " + chat.getChatGuid() + " ...");

                innerData.addProperty("guid", chat.getChatGuid());

                Long lastModified = chat.getLastChatModifiedDate();
                if (lastModified != null) {
                    innerData.addProperty("lastModifiedDate", DateUtil.utcStringFromMillis(lastModified.longValue()));
                }
                if (chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION) {
                    innerData.addProperty("confirmed", "false");
                }
                final JsonArray messages = new JsonArray();
                // Nachrichten Infos sichern
                final CountDownLatch latch = new CountDownLatch(1);

                final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
                    @Override
                    public void onListResult(List<Message> dbMessages) {
                        for (Message dbm : dbMessages) {
                            if(dbm.getGuid() == null) {
                                continue;
                            }

                            JsonObject innerData = new JsonObject();
                            innerData.addProperty("guid", dbm.getGuid());
                            if (dbm.getDateSend() != null) {
                                innerData.addProperty("datesend", DateUtil.utcStringFromMillis(dbm.getDateSend().longValue()));
                            }
                            if (dbm.getDateDownloaded() != null) {
                                innerData.addProperty("datedownloaded", DateUtil.utcStringFromMillis(dbm.getDateDownloaded().longValue()));
                            }
                            if (dbm.getDateRead() != null) {
                                innerData.addProperty("dateread", DateUtil.utcStringFromMillis(dbm.getDateRead().longValue()));
                            }
                            if (dbm.getHasSendError() != null && dbm.getHasSendError().booleanValue()) {
                                innerData.addProperty("sendingFailed", "true");
                            }
                            if (dbm.getIsSignatureValid() != null) {
                                innerData.addProperty("signatureValid", (dbm.getIsSignatureValid()) ? "true" : "false");
                            }

                            if (dbm.getDateSendTimed() != null) {
                                // Timed Message ....
                                innerData.addProperty("dateToSend", DateUtil.utcStringFromMillis(dbm.getDateSendTimed().longValue()));
                            }

                            JsonObject privateMessageBackup = new JsonObject();
                            privateMessageBackup.add("PrivateMessage", innerData);

                            messages.add(privateMessageBackup);
                        }

                        LogUtil.d(TAG, "singleChatBackup: " + dbMessages.size() + " messages processed.");
                        latch.countDown();
                    }

                    @Override
                    public void onUniqueResult(Message message) {

                    }

                    @Override
                    public void onCount(long count) {

                    }
                };

                try {
                    // KS: Only collect messages within persistMessageDays
                    //mApplication.getMessageController().loadAllMessages(chat.getChatGuid(), Message.TYPE_PRIVATE, false, queryDatabaseListener);
                    long now = System.currentTimeMillis();
                    long from = now - mApplication.getPreferencesController().getPersistMessageDays() * 24L * 60L * 60L * 1000L;
                    mApplication.getMessageController().loadMessagesSentFromUntil(chat.getChatGuid(), Message.TYPE_PRIVATE, from, now , queryDatabaseListener);

                    latch.await();
                } catch (Exception e) {
                    LogUtil.e(TAG, "singleChatBackup: Got exception " + e.getMessage());
                    continue;
                }

                innerData.add("messages", messages);

                JsonObject singleChatBackup = new JsonObject();
                singleChatBackup.add("SingleChatBackup", innerData);
                rc.add(singleChatBackup);
            }
        }

        return rc;
    }

    private JsonArray groupChatBackup()
            throws LocalizedException {
        JsonArray rc = new JsonArray();
        List<Chat> allChats = mApplication.getGroupChatController().loadAll();

        for (Chat chat : allChats) {
            if (chat.getType() == null) {
                continue;
            }
            if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION || chat.getType() == Chat.TYPE_GROUP_CHAT) {
                JsonObject innerData = new JsonObject();

                LogUtil.d(TAG, "groupChatBackup: Processing " + chat.getChatGuid() + " ...");

                innerData.addProperty("guid", chat.getChatGuid());
                innerData.addProperty("type", chat.getRoomType());
                innerData.addProperty("owner", chat.getOwner());
                innerData.addProperty("name", chat.getTitle());

                if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
                    Long invitedAt = chat.getLastChatModifiedDate();
                    if (invitedAt != null) {
                        innerData.addProperty("invitedDate", DateUtil.utcStringFromMillis(invitedAt.longValue()));
                    }
                }

                byte[] imgBytes = mApplication.getChatImageController().loadImage(chat.getChatGuid());
                if (imgBytes != null && imgBytes.length > 0) {
                    String imgBase64 = Base64.encodeToString(imgBytes, Base64.DEFAULT);
                    innerData.addProperty("groupImage", imgBase64);
                }

                JsonArray members = chat.getMembers();
                innerData.add("member", members);

                JsonArray admins = chat.getAdmins();
                innerData.add("admins", admins);

                if (chat.getChatAESKey() != null) {
                    innerData.addProperty("aes_key", Base64.encodeToString(chat.getChatAESKey().getEncoded(), Base64.NO_WRAP));

                    if (chat.getChatInfoIV() != null) {
                        innerData.addProperty("iv", Base64.encodeToString(chat.getChatInfoIV().getIV(), Base64.NO_WRAP));
                    }
                }

                Long lastModified = chat.getLastChatModifiedDate();
                if (lastModified != null) {
                    innerData.addProperty("lastModifiedDate", DateUtil.utcStringFromMillis(lastModified.longValue()));
                }
                if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
                    innerData.addProperty("confirmed", "unconfirmed");
                } else {
                    innerData.addProperty("confirmed", "unblocked");
                }

                final JsonArray messages = new JsonArray();
                // Nachrichten Infos sichern
                final CountDownLatch latch = new CountDownLatch(1);

                final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
                    @Override
                    public void onListResult(List<Message> dbMessages) {
                        for (Message dbm : dbMessages) {
                            if(dbm.getGuid() == null) {
                                continue;
                            }

                            JsonObject innerData = new JsonObject();
                            innerData.addProperty("guid", dbm.getGuid());
                            if (dbm.getDateSend() != null) {
                                innerData.addProperty("datesend", DateUtil.utcStringFromMillis(dbm.getDateSend().longValue()));
                            }
                            if (dbm.getDateDownloaded() != null) {
                                innerData.addProperty("datedownloaded", DateUtil.utcStringFromMillis(dbm.getDateDownloaded().longValue()));
                            }
                            if (dbm.getDateRead() != null) {
                                innerData.addProperty("dateread", DateUtil.utcStringFromMillis(dbm.getDateRead().longValue()));
                            }
                            if (dbm.getHasSendError() != null && dbm.getHasSendError().booleanValue()) {
                                innerData.addProperty("sendingFailed", "true");
                            }
                            if (dbm.getIsSignatureValid() != null) {
                                innerData.addProperty("signatureValid", (dbm.getIsSignatureValid()) ? "true" : "false");
                            }
                            JsonElement je = dbm.getElementFromAttributes(AppConstants.MESSAGE_JSON_RECEIVERS);
                            if (je != null) {
                                innerData.add("receiver", je);
                            }

                            /* KS: Is not handled by any other client!
                            if (dbm.isSystemInfo()) {

                                String guid = dbm.getGuid();
                                if (guid == null) {
                                    guid = GuidUtil.generateGuid("104:");
                                }
                                guid += "0";
                                innerData.addProperty("guid", guid);
                                // TODO : Data bei SystemNachrichten mit übertragen -> Verschlüsselung prüfen !

                                // bis dahin
                                continue;
                            }
                             */

                            String clazzName = "GroupMessage";
                            if (dbm.getDateSendTimed() != null) {
                                // Timed Message ....
                                clazzName = "TimedGroupMessage";
                                innerData.addProperty("dateToSend", DateUtil.utcStringFromMillis(dbm.getDateSendTimed().longValue()));
                            }

                            JsonObject privateMessageBackup = new JsonObject();
                            privateMessageBackup.add(clazzName, innerData);

                            messages.add(privateMessageBackup);
                        }

                        LogUtil.d(TAG, "groupChatBackup: " + dbMessages.size() + " messages processed.");
                        latch.countDown();
                    }

                    @Override
                    public void onUniqueResult(Message message) {

                    }

                    @Override
                    public void onCount(long count) {

                    }
                };

                try {
                    // KS: Only collect messages within persistMessageDays
                    //mApplication.getMessageController().loadAllMessages(chat.getChatGuid(), Message.TYPE_GROUP, false, queryDatabaseListener);
                    long now = System.currentTimeMillis();
                    long from = now - mApplication.getPreferencesController().getPersistMessageDays() * 24L * 60L * 60L * 1000L;
                    mApplication.getMessageController().loadMessagesSentFromUntil(chat.getChatGuid(), Message.TYPE_GROUP, from, now , queryDatabaseListener);

                    latch.await();
                } catch (Exception e) {
                    LogUtil.e(TAG, "groupChatBackup: Got exception " + e.getMessage());
                    continue;
                }

                innerData.add("messages", messages);

                JsonObject groupChatBackup = new JsonObject();
                groupChatBackup.add("ChatRoomBackup", innerData);
                rc.add(groupChatBackup);
            }
        }

        return rc;
    }
}
