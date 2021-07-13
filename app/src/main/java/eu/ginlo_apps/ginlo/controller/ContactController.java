// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;
import org.greenrobot.greendao.query.WhereCondition;
import org.mindrot.jbcrypt.BCrypt;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.LoginController.AppLockLifecycleCallbacks;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks;
import eu.ginlo_apps.ginlo.controller.message.PrivateInternalMessageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.ContactDao;
import eu.ginlo_apps.ginlo.greendao.ContactDao.Properties;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.Listener.GenericUpdateListener;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

public class ContactController
        implements AppLockLifecycleCallbacks, AppLifecycleCallbacks {

    private static final String TAG = ContactController.class.getSimpleName();

    public enum IndexType {
        INDEX_TYPE_COMPANY,
        INDEX_TYPE_DOMAIN
    }

    private static final String[] CONTACT_DETAIL_PROJECTION = {
            ContactsContract.Data.DATA1, //0
            ContactsContract.Data.DATA2, //1
            ContactsContract.Data.DATA3, //2
            ContactsContract.Data.DATA4, //3
            ContactsContract.Data.DATA5, //4
            ContactsContract.Data.DATA6, //5
            ContactsContract.Data.MIMETYPE, //6
            ContactsContract.Data.PHOTO_URI}; //7

    private static final String CONTACT_DETAIL_SELECTION = ContactsContract.Data.CONTACT_ID + " = ? AND ((" +
            ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "') OR (" +
            ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "') OR (" +
            ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'))";

    private static final String[] PROJECTION_ = {ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.MIMETYPE};

    private static final String SELECTION_ =
            "((" + ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "') OR (" +
                    ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'))";

    public static final String ONLINE_STATE_INVALID = "invalid";

    public static final String ONLINE_STATE_ONLINE = "online";

    public static final String ONLINE_STATE_WRITING = "writing";

    public static final String ONLINE_STATE_ABSENT = "absent";

    private static final int DATA1_PHONE_MAIL = 0;
    private static final int DATA2_GIVEN_NAME = 1;
    private static final int DATA3_FAMILY_NAME = 2;
    private static final int DATA4_NORMALIZED_PHONE_PREFIX_NAME = 3;
    private static final int DATA5_MIDDLE_NAME = 4;
    private static final int DATA6_SUFFIX_NAME = 5;
    private static final int MIMETYPE = 6;

    private static final SerialExecutor ONLINE_SERIAL_EXECUTOR = new SerialExecutor();

    private static final SerialExecutor CONTACT_PROFILE_INFO_SERIAL_EXECUTOR = new SerialExecutor();

    private final ContactDao contactDao;

    final SimsMeApplication mApplication;

    private ConcurrentTask mSyncContactsTask;

    private OnlineStateTask mGetOnlineStateTask;

    private LoadContactsListener mSyncContactsListener;

    private final List<OnContactProfileInfoChangeNotification> mOnContactProfileInfoChangeNotificationList;

    private LoadPrivateIndexTask mLoadPrivateIndexEntries;

    private UpdatePrivateIndexTask mUpdatePrivateIndexEntries;

    private boolean mStartUpdatePrivateIndexTaskAgain;

    private Timer mContactTimer;

    private final HashMap<String, JsonObject> mTempDeviceCache;

    private String mOnlineStateGuid;

    public ContactController(final SimsMeApplication application) {
        mTempDeviceCache = new HashMap<>();

        Database db = application.getDataBase();

        DaoMaster daoMaster = new DaoMaster(db);
        DaoSession daoSession = daoMaster.newSession();

        mOnContactProfileInfoChangeNotificationList = new ArrayList<>();

        contactDao = daoSession.getContactDao();

        mApplication = application;

        mApplication.getLoginController().registerAppLockLifecycleCallbacks(this);
        mApplication.getAppLifecycleController().registerAppLifecycleCallbacks(this);
    }

    public interface OnSystemChatCreatedListener {

        void onSystemChatCreatedSuccess();

        void onSystemChatCreatedError(String message);
    }

    public interface OnContactVerifiedListener {
        void onContactVerified(boolean verified);

        void onContactVerifiedFailed();
    }

    public interface OnLoadPublicKeyListener {
        void onLoadPublicKeyComplete(Contact contact);

        void onLoadPublicKeyError(String message);
    }

    public interface OnLoadPublicKeysListener {
        void onLoadPublicKeysComplete(Map<String, String> publicKeysMap);

        void onLoadPublicKeyError(String message);
    }

    public interface OnLoadContactsListener {
        void onLoadContactsComplete();

        void onLoadContactsCanceled();

        void onLoadContactsError(String message);
    }

    public interface OnLoadContactFromServerListener {
        void onLoadContactFromServerError(String message);

        void onLoadMultipleContactsFromServerComplete(List<Contact> contacts);
    }

    public interface OnContactProfileInfoChangeNotification {
        void onContactProfilInfoHasChanged(String contactGuid);

        void onContactProfilImageHasChanged(String contactguid);
    }

    public interface LoadCompanyContactsListener {
        void onLoadSuccess();

        void onLoadFail(final String message, final String errorIdent);

        void onLoadCompanyContactsSize(final int size);

        void onLoadCompanyContactsUpdate(final int count);
    }

    public ContactDao getDao() {
        return contactDao;
    }

    public boolean isSyncingContacts() {
        return mSyncContactsTask != null;
    }

    public void addOnLoadContactsListener(OnLoadContactsListener listener) {
        if (listener == null) {
            return;
        }

        if (mSyncContactsListener == null) {
            mSyncContactsListener = new LoadContactsListener(listener);
        } else {
            mSyncContactsListener.addLoadContactsListener(listener);
        }
    }

    public void removeOnLoadContactsListener(OnLoadContactsListener listener) {
        if (mSyncContactsListener != null) {
            mSyncContactsListener.removeLoadContactsListener(listener);
        }
    }

    public ArrayList<Contact> loadNonSimsMeContacts()
            throws LocalizedException {
        ArrayList<Contact> contacts = new ArrayList<>();

        Map<String, String> dedupedContacts = new HashMap<>();

        final Cursor cursor = mApplication.getContentResolver()
                .query(ContactsContract.Data.CONTENT_URI, PROJECTION_, SELECTION_,
                        null, null);

        if (cursor == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Contacts Crusor is null");
        }

        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {

            final long contactId = cursor.getLong(0);

            String k = Long.toString(contactId);
            dedupedContacts.put(k, k);

            cursor.moveToNext();
        }

        cursor.close();

        for (String contactId : dedupedContacts.keySet()) {
            List<Contact> c = loadContactDetails(contactId);
            if (c != null) {
                contacts.addAll(c);
            }
        }

        Collections.sort(contacts, ContactUtil.getSortComparator(ContactUtil.SORT_ASCENDING));
        return contacts;
    }

    public ArrayList<Contact> loadSimsMeContacts()
            throws LocalizedException {

        return loadContactsFromDatabase(true,
                ContactUtil.getSortComparator(ContactUtil.SORT_ASCENDING));
    }

    public ArrayList<Contact> loadNonBlockedSimsMeContacts()
            throws LocalizedException {
        ArrayList<Contact> contacts = loadContactsFromDatabase(true,
                ContactUtil.getSortComparator(ContactUtil.SORT_ASCENDING));

        ArrayList<Contact> nonBlockedContacts = new ArrayList<>();
        for (Contact contact : contacts) {
            if (contact != null && (contact.getIsBlocked() == null || !contact.getIsBlocked())) {
                nonBlockedContacts.add(contact);
            }
        }

        return nonBlockedContacts;
    }

    public void setStatus(String guid,
                          String status)
            throws LocalizedException {
        Contact contact = getContactByGuid(guid);

        if (contact != null) {
            contact.setStatusText(status);

            synchronized (contactDao) {
                contactDao.update(contact);
            }
        }
    }

    public void setNickname(String guid,
                            String nickname)
            throws LocalizedException {
        Contact contact = getContactByGuid(guid);

        if (contact != null) {
            if (!StringUtil.isEqual(contact.getNickname(), nickname)) {
                contact.setNickname(nickname);
                synchronized (contactDao) {
                    contactDao.update(contact);
                }

                mApplication.getChatOverviewController().chatChanged(null, guid, null, ChatOverviewController.CHAT_CHANGED_TITLE);
            }
        }
    }

    public void setImage(String guid,
                         byte[] imageBytes)
            throws LocalizedException {
        mApplication.getChatImageController().saveImage(guid, imageBytes);
    }

    public void setProfilInfoAesKey(final String guid, final String aesKey,
                                    final DecryptedMessage decryptedMessage)
            throws LocalizedException {
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Contact contact = createContactIfNotExists(guid, decryptedMessage);

                    if (contact != null && StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                        contact.setProfileInfoAesKey(aesKey);

                        SecretKey key = SecurityUtil.getAESKeyFromBase64String(aesKey);

                        if (!StringUtil.isNullOrEmpty(contact.getEncryptedStatusText())) {
                            String encryptedStatus = contact.getEncryptedStatusText();
                            String status = SecurityUtil.decryptBase64StringWithAES(encryptedStatus, key);

                            contact.setStatusText(status);
                        }

                        if (!StringUtil.isNullOrEmpty(contact.getEncryptedNickname())) {
                            String encryptedNickname = contact.getEncryptedNickname();
                            String nickname = SecurityUtil.decryptBase64StringWithAES(encryptedNickname, key);

                            contact.setNickname(nickname);
                        }

                        if (!StringUtil.isNullOrEmpty(contact.getProfileImageChecksum())) {
                            String checksum = contact.getProfileImageChecksum();

                            loadContactProfileImageFromServer(guid, checksum);
                        }

                        synchronized (contactDao) {
                            contactDao.update(contact);
                        }

                        return true;
                    }

                    return false;
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "Failed at setProfilInfoAesKey():" + e.getMessage(), e);

                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);

                if (aBoolean != null && aBoolean) {
                    mApplication.getSingleChatController().clearNameCache();
                    mApplication.getGroupChatController().clearNameCache();
                    mApplication.getChatOverviewController().clearNameCache();

                    notifyOnContactProfileInfoChangeNotificationListener(guid);
                }
            }
        };

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null,
                null, null);
    }

    public void createSystemChatContact(final OnSystemChatCreatedListener onSystemChatCreatedListener) {
        Contact contact;

        try {
            contact = getContactByGuid(AppConstants.GUID_SYSTEM_CHAT);

            if (contact == null) {
                contact = new Contact();
                contact.setAccountGuid(AppConstants.GUID_SYSTEM_CHAT);
                contact.setIsSimsMeContact(true);
                contact.setIsFirstContact(false);
                contact.setState(Contact.STATE_HIGH_TRUST);
                contact.setIsHidden(true);

                synchronized (contactDao) {
                    contactDao.insert(contact);
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            onSystemChatCreatedListener.onSystemChatCreatedError(null);
            return;
        }

        OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
            @Override
            public void onLoadPublicKeyError(String message) {
                onSystemChatCreatedListener.onSystemChatCreatedError(message);
            }

            @Override
            public void onLoadPublicKeyComplete(Contact contact) {
                onSystemChatCreatedListener.onSystemChatCreatedSuccess();
            }
        };
        loadPublicKey(contact, onLoadPublicKeyListener);
    }

    public synchronized Contact createContactIfNotExists(String guid,
                                                         DecryptedMessage decryptedMessage)
            throws LocalizedException {
        return createContactIfNotExists(guid, decryptedMessage, null, true);
    }

    public synchronized Contact createContactIfNotExists(String guid,
                                                         DecryptedMessage decryptedMessage,
                                                         String mandant, boolean isHidden)
            throws LocalizedException {

        AccountController accountController = mApplication.getAccountController();
        final String ownGuid = accountController.getAccount().getAccountGuid();

        if (ownGuid.equals(guid)) {
            return null;
        }

        String nickname;
        String phoneNumber;
        String simsmeID = null;
        String profileKey = null;
        if (decryptedMessage == null
                || decryptedMessage.getMessage() == null
                || decryptedMessage.getMessage().getFrom() == null
                || ownGuid.equals(decryptedMessage.getMessage().getFrom())) {
            nickname = null;
            phoneNumber = null;
        } else {
            nickname = decryptedMessage.getNickName();
            phoneNumber = decryptedMessage.getPhoneNumber();
            simsmeID = decryptedMessage.getSIMSmeID();
            profileKey = decryptedMessage.getProfilKey();
        }

        Contact rc = createContactIfNotExists(guid, nickname, phoneNumber, simsmeID, mandant, isHidden);
        if (StringUtil.isNullOrEmpty(rc.getProfileInfoAesKey()) && !StringUtil.isNullOrEmpty(profileKey)) {
            this.setProfilInfoAesKey(guid, profileKey, null);
        }
        return rc;
    }

    public synchronized Contact createContactIfNotExists(String guid,
                                                         String nickname,
                                                         String phoneNumber,
                                                         String simsmeId,
                                                         String mandant,
                                                         boolean isHidden)
            throws LocalizedException {
        return createContactIfNotExists(guid, nickname, phoneNumber, simsmeId, mandant, null, true, null, isHidden);
    }

    public synchronized Contact createContactIfNotExists(String guid,
                                                         String nickname,
                                                         String phoneNumber,
                                                         String simsmeId,
                                                         String mandant,
                                                         String publicKey,
                                                         boolean loadPublicKeyIfNotExists,
                                                         OnLoadPublicKeyListener onLoadPublicKeyListener,
                                                         boolean isHidden)
            throws LocalizedException {
        Contact contact;

        synchronized (this) {
            AccountController accountController = mApplication.getAccountController();
            final String ownGuid = accountController.getAccount().getAccountGuid();

            if (ownGuid.equals(guid)) {
                return getOwnContact();
            }

            contact = getContactByGuid(guid);

            if (contact == null) {
                contact = createContact(guid, nickname, phoneNumber, simsmeId, mandant, publicKey, loadPublicKeyIfNotExists, onLoadPublicKeyListener, isHidden);
            } else {
                boolean newChanges = false;

                if (nickname != null && !StringUtil.isEqual(nickname, contact.getNickname())) {
                    contact.setNickname(nickname);
                    newChanges = true;
                }

                if (phoneNumber != null && !StringUtil.isEqual(phoneNumber, contact.getPhoneNumber())) {
                    contact.setPhoneNumber(phoneNumber);
                    newChanges = true;
                }
                if (StringUtil.isNullOrEmpty(contact.getMandant())
                        && !StringUtil.isNullOrEmpty(mandant)) {
                    contact.setMandant(mandant);
                    newChanges = true;
                }

                if (!isHidden && contact.getIsHidden() != isHidden) {
                    contact.setIsHidden(isHidden);
                    newChanges = true;
                }

                if (newChanges) {
                    insertOrUpdateContact(contact);
                    mApplication.getChatOverviewController().clearNameCache();
                    mApplication.getGroupChatController().clearNameCache();
                    mApplication.getSingleChatController().clearNameCache();
                    mApplication.getChatOverviewController().chatChanged(null, contact.getAccountGuid(), null, ChatOverviewController.CHAT_CHANGED_TITLE);
                    updatePrivateIndexEntriesAsync();
                }
            }
        }

        return contact;
    }

    private Contact createContact(@NonNull String guid,
                                  String nickname,
                                  String phoneNumber,
                                  String simsmeId,
                                  String mandant,
                                  String publicKey,
                                  boolean loadPublicKeyIfNotExists,
                                  OnLoadPublicKeyListener onLoadPublicKeyListener, boolean isHidden)
            throws LocalizedException {
        Contact contact = new Contact();
        contact.setAccountGuid(guid);
        contact.setIsSimsMeContact(true);
        contact.setIsHidden(isHidden);
        contact.setIsBlocked(false);

        if (!StringUtil.isNullOrEmpty(nickname)) {
            contact.setNickname(nickname);
        }

        if (!StringUtil.isNullOrEmpty(phoneNumber)) {
            contact.setPhoneNumber(phoneNumber);
        }

        if (!StringUtil.isNullOrEmpty(simsmeId)) {
            contact.setSimsmeId(simsmeId);
        }

        if (guid.equals(AppConstants.GUID_SYSTEM_CHAT)) {
            contact.setIsFirstContact(false);
            contact.setState(Contact.STATE_HIGH_TRUST);
        } else {
            contact.setIsFirstContact(true);
            contact.setState(Contact.STATE_LOW_TRUST);
        }

        if (!StringUtil.isNullOrEmpty(mandant)) {
            contact.setMandant(mandant);
        }

        if (StringUtil.isNullOrEmpty(publicKey)) {
            if (loadPublicKeyIfNotExists) {
                loadPublicKey(contact, onLoadPublicKeyListener);
            }
        } else {
            contact.setPublicKey(publicKey);
            if (onLoadPublicKeyListener != null) {
                onLoadPublicKeyListener.onLoadPublicKeyComplete(contact);
            }
        }

        contact.setClassEntryName(Contact.CLASS_PRIVATE_ENTRY);

        insertOrUpdateContact(contact);

        updatePrivateIndexEntriesAsync();

        return contact;
    }

    public String getVCardForContactUri(Activity activity,
                                        Uri contactUri) {
        String vCard = null;

        String[] projection = new String[]{ContactsContract.Contacts.LOOKUP_KEY};

        Cursor cursor = activity.getContentResolver().query(contactUri, projection, null, null, null);

        if (cursor == null) {
            return null;
        }

        try {
            if (cursor.moveToFirst()) {
                String lookupKey = cursor.getString(0);
                try {
                    Uri contactVCardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI,
                            lookupKey);
                    if (SystemUtil.hasNougat()) {
                        try (final InputStream inputStream = activity.getContentResolver().openInputStream(contactVCardUri)) {
                            if (inputStream != null) {
                                byte[] buffer = new byte[inputStream.available()];
                                StreamUtil.safeRead(inputStream, buffer, buffer.length);
                                vCard = new String(buffer, Encoding.UTF8);
                                return vCard;
                            }
                        }
                    } else {
                        try (AssetFileDescriptor assetFileDescriptor = activity.getContentResolver().openAssetFileDescriptor(contactVCardUri, "r")) {
                            if (assetFileDescriptor != null) {
                                try (final FileInputStream fileInputStream = assetFileDescriptor.createInputStream()) {
                                    byte[] buffer = new byte[(int) assetFileDescriptor.getDeclaredLength()];
                                    StreamUtil.safeRead(fileInputStream, buffer, buffer.length);
                                    vCard = new String(buffer, Encoding.UTF8);
                                    return vCard;
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public void insertOrUpdateContact(Contact contact) {
        synchronized (contactDao) {
            if (contact.getId() != null) {
                contactDao.update(contact);
            } else {
                contactDao.insert(contact);
            }
        }
    }

    public void verifyContact(Contact contact,
                              final String qrCodeString,
                              final OnContactVerifiedListener onContactVerifiedListener) {
        if (contact == null) {
            return;
        }

        if (contact.getPublicKey() == null) {
            OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
                @Override
                public void onLoadPublicKeyComplete(Contact contact) {
                    try {
                        onContactVerifiedListener.onContactVerified(processContactVerificationResult(contact, qrCodeString));
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                        onContactVerifiedListener.onContactVerifiedFailed();
                    }
                }

                @Override
                public void onLoadPublicKeyError(String message) {
                    LogUtil.e(TAG, message);
                    onContactVerifiedListener.onContactVerifiedFailed();
                }
            };

            loadPublicKey(contact, onLoadPublicKeyListener);
        } else {
            try {
                onContactVerifiedListener.onContactVerified(processContactVerificationResult(contact, qrCodeString));
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                onContactVerifiedListener.onContactVerifiedFailed();
            }
        }
    }

    public void upgradeTrustLevel(String contactGuid,
                                  int trustLevel)
            throws LocalizedException {
        Contact contact = getContactByGuid(contactGuid);

        upgradeTrustLevel(contact, trustLevel);
    }

    public void setIsFirstContact(Contact contact,
                                  boolean isFirstContact) {
        contact.setIsFirstContact(isFirstContact);
        insertOrUpdateContact(contact);
    }

    public void upgradeTrustLevel(Contact contact,
                                  int trustLevel)
            throws LocalizedException {
        if (contact != null) {
            int currentTrustLevel = (contact.getState() == null) ? Contact.STATE_UNSIMSABLE : contact.getState();

            if (trustLevel > currentTrustLevel) {
                if (trustLevel > Contact.STATE_LOW_TRUST) {
                    contact.setIsFirstContact(false);
                }
                contact.setState(trustLevel);
                contact.setChecksum("");
                insertOrUpdateContact(contact);
                updatePrivateIndexEntriesAsync();
            }
        }
    }

    public void blockContact(String contactGuid, final boolean blocked, final boolean bSyncExternal, final GenericActionListener<Void> actionListener)
            throws LocalizedException {
        final Contact contact = getContactByGuid(contactGuid);

        if (contact != null) {
            if (blocked == (contact.getIsBlocked() == null ? false : contact.getIsBlocked())) {
                if (actionListener != null) {
                    actionListener.onSuccess(null);
                }
                return;
            }

            if (bSyncExternal) {
                IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                    @Override
                    public void onBackendResponse(BackendResponse response) {
                        if (!response.isError) {
                            contact.setIsBlocked(blocked);
                            contact.setChecksum("");
                            insertOrUpdateContact(contact);
                            updatePrivateIndexEntriesAsync();
                            if (actionListener != null) {
                                actionListener.onSuccess(null);
                            }
                        } else {
                            if (actionListener != null) {
                                actionListener.onFail(response.errorMessage, null);
                            }
                        }
                    }
                };
                BackendService.withAsyncConnection(mApplication)
                        .setBlocked(contact.getAccountGuid(), blocked, listener);
            } else {
                contact.setIsBlocked(blocked);
                contact.setChecksum("");
                insertOrUpdateContact(contact);
                updatePrivateIndexEntriesAsync();
            }
        }
    }

    private List<Contact> loadContactDetails(String contactId) {

        String countryCode = mApplication.getAccountController().getAccount().getCountryCode();

        String[] selectionArgs = new String[]{contactId};

        ArrayList<String> phoneNumbers = new ArrayList<>();
        ArrayList<String> emails = new ArrayList<>();
        Contact contactDetails = null;
        try (final Cursor cursor = mApplication.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                CONTACT_DETAIL_PROJECTION,
                CONTACT_DETAIL_SELECTION, selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactDetails = new Contact();

                while (!cursor.isAfterLast()) {
                    final String mimetype = cursor.getString(MIMETYPE);

                    if (StringUtil.isEqual(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, mimetype)) {
                        final String firstName = cursor.getString(DATA2_GIVEN_NAME);

                        if (firstName != null) {
                            contactDetails.setFirstName(firstName);
                        }

                        final String lastName = cursor.getString(DATA3_FAMILY_NAME);

                        if (lastName != null) {
                            contactDetails.setLastName(lastName);
                        }

                        final String middleName = cursor.getString(DATA5_MIDDLE_NAME);

                        if (middleName != null) {
                            contactDetails.setMiddleName(middleName);
                        }

                        final String prefix = cursor.getString(DATA4_NORMALIZED_PHONE_PREFIX_NAME);

                        if (prefix != null) {
                            contactDetails.setNamePrefix(prefix);
                        }

                        final String suffix = cursor.getString(DATA6_SUFFIX_NAME);

                        if (suffix != null) {
                            contactDetails.setNameSuffix(suffix);
                        }
                    } else if (StringUtil.isEqual(Phone.CONTENT_ITEM_TYPE, mimetype)) {
                        String normalized = cursor.getString(DATA4_NORMALIZED_PHONE_PREFIX_NAME);

                        String normalizedPhoneNumber;
                        if (StringUtil.isNullOrEmpty(normalized)) {
                            String phone = cursor.getString(DATA1_PHONE_MAIL);
                            normalizedPhoneNumber = PhoneNumberUtil
                                    .normalizePhoneNumberNew(mApplication, countryCode, phone);
                        } else {

                            normalizedPhoneNumber = normalized;
                        }

                        if (!phoneNumbers.contains(normalizedPhoneNumber)) {
                            phoneNumbers.add(normalizedPhoneNumber);
                        }
                    } else if (StringUtil.isEqual(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, mimetype)) {
                        String email = cursor.getString(DATA1_PHONE_MAIL);
                        if (!emails.contains(email)) {
                            emails.add(email);
                        }
                    }
                    cursor.moveToNext();
                }
            }
            List<Contact> rc = new ArrayList<>();

            if (contactDetails != null) {
                for (String phone : phoneNumbers) {
                    Contact c = contactDetails.cloneContact();
                    c.setPhoneNumber(phone);
                    rc.add(c);
                }

                for (String email : emails) {
                    Contact c = contactDetails.cloneContact();
                    c.setEmail(email);
                    rc.add(c);
                }
            }
            return rc;
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return null;
    }

    public void loadPublicKey(String guid,
                              final OnLoadPublicKeyListener onLoadPublicKeyListener)
            throws LocalizedException {
        Contact contact = getContactByGuid(guid);

        if (contact == null) {
            onLoadPublicKeyListener.onLoadPublicKeyError("No such contact.");
        } else {
            loadPublicKey(contact, onLoadPublicKeyListener);
        }
    }

    public void loadPublicKey(final Contact contact,
                              final OnLoadPublicKeyListener onLoadPublicKeyListener) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    if (onLoadPublicKeyListener != null) {
                        onLoadPublicKeyListener.onLoadPublicKeyError(response.errorMessage);
                    }
                } else if (response.jsonObject != null) {
                    JsonObject accountObject = JsonUtil.searchJsonObjectRecursive(response.jsonObject, JsonConstants.ACCOUNT);

                    if (accountObject == null) {
                        onLoadPublicKeyListener.onLoadPublicKeyError(mApplication.getResources().getString(R.string.unexpected_error_title_next));
                        return;
                    }

                    String publicKey = JsonUtil.stringFromJO(JsonConstants.PUBLIC_KEY, accountObject);

                    if (!StringUtil.isNullOrEmpty(publicKey)) {
                        contact.setPublicKey(publicKey);
                    }

                    try {
                        String simsmeId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, accountObject);
                        if (!StringUtil.isNullOrEmpty(simsmeId)) {
                            contact.setSimsmeId(simsmeId);
                        }

                        if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                            setEncryptedProfileInfosToContact(accountObject, contact);
                        } else {
                            decryptedAndSetProfilInfosToContact(accountObject, contact, true);
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }

                    insertOrUpdateContact(contact);

                    if (onLoadPublicKeyListener != null) {
                        onLoadPublicKeyListener.onLoadPublicKeyComplete(contact);
                    }
                } else {
                    if (onLoadPublicKeyListener != null) {
                        onLoadPublicKeyListener.onLoadPublicKeyError(mApplication.getResources().getString(R.string.unexpected_error_title_next));
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .getAccountInfo(contact.getAccountGuid(), 1, false, true, false, listener);
    }

    public void getTempDeviceInfo(final String accountGuid,
                                  final OnLoadPublicKeyListener onLoadPublicKeyListener)
            throws LocalizedException {
        final Contact contact = getContactByGuid(accountGuid);

        String publicKey = null;
        if (contact == null || contact.getPublicKey() == null) {
            AccountController ownAccountController = mApplication.getAccountController();
            if (ownAccountController.getAccount() != null && ownAccountController.getAccount().getAccountGuid().equals(accountGuid)) {
                publicKey = ownAccountController.getAccount().getPublicKey();
            } else {
                onLoadPublicKeyListener.onLoadPublicKeyError("No such contact.");
            }
        } else {
            publicKey = contact.getPublicKey();
        }

        final PublicKey pubKey = XMLUtil.getPublicKeyFromXML(publicKey);

        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    if (onLoadPublicKeyListener != null) {
                        onLoadPublicKeyListener.onLoadPublicKeyError(response.errorMessage);
                    }
                } else {
                    JsonArray responseArray = response.jsonArray;

                    try {
                        // TempDeviceInfos laden und cachen
                        if (responseArray != null) {
                            for (int i = 0; i < responseArray.size(); i++) {
                                JsonObject entry = responseArray.get(i).getAsJsonObject();
                                JsonObject keyList = entry.get("AccountKeysList").getAsJsonObject();
                                String signature = keyList.get("sig").getAsString();
                                String deviceDataRaw = keyList.get("keys").getAsString();
                                String createdAt = keyList.get("createdAt").getAsString();
                                String nextUpdate = keyList.get("nextUpdate").getAsString();

                                try {
                                    String dataForSig = deviceDataRaw + createdAt + nextUpdate;
                                    // Signature prüfen
                                    if (SecurityUtil.verifyData(pubKey, Base64.decode(signature, Base64.DEFAULT), dataForSig.getBytes("utf-8"), true)) {
                                        JsonArray deviceData = JsonUtil.getJsonArrayFromString(deviceDataRaw);
                                        if (deviceData != null) {
                                            for (int j = 0; j < deviceData.size(); j++) {
                                                JsonObject d = deviceData.get(j).getAsJsonObject();
                                                if (d.has("deviceGuid")) {
                                                    String deviceGuid = d.get("deviceGuid").getAsString();
                                                    mTempDeviceCache.put(deviceGuid, d);
                                                }
                                            }
                                        }
                                    }
                                } catch (IOException ex) {
                                    LogUtil.w(TAG, ex.getMessage(), ex);
                                }
                            }
                        }
                    } catch (LocalizedException le) {
                        LogUtil.e(TAG, le.getIdentifier(), le);
                    }

                    if (onLoadPublicKeyListener != null) {
                        onLoadPublicKeyListener.onLoadPublicKeyComplete(contact);
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .getTempDeviceInfo(accountGuid, listener);
    }

    public void loadPublicKeys(final List<Contact> contacts,
                               final OnLoadPublicKeysListener onLoadPublicKeysListener) {
        if ((contacts == null) || (contacts.size() < 1)) {
            return;
        }

        StringBuilder guidList = new StringBuilder();

        for (Contact contact : contacts) {
            String guid = contact.getAccountGuid();

            if (!StringUtil.isNullOrEmpty(guid)) {
                if (guidList.length() > 0) {
                    guidList.append(",");
                }

                guidList.append(guid);
            }
        }

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (!response.isError) {
                    if (onLoadPublicKeysListener == null) {
                        return;
                    }

                    Map<String, String> publicKeysMap = new HashMap<>();

                    if ((response.jsonArray != null) && (response.jsonArray.size() > 0)) {
                        for (int i = 0; i < response.jsonArray.size(); i++) {
                            JsonElement jsonElement = response.jsonArray.get(i);

                            if (jsonElement.isJsonObject()) {
                                JsonObject jsonObject = jsonElement.getAsJsonObject();

                                if (jsonObject.has(JsonConstants.ACCOUNT)) {
                                    JsonElement jsonAccountElement = jsonObject.get(JsonConstants.ACCOUNT);

                                    if (jsonAccountElement.isJsonObject()) {
                                        JsonObject jsonAccountObject = jsonAccountElement.getAsJsonObject();

                                        if (jsonAccountObject.has("guid") && jsonAccountObject.has(JsonConstants.PUBLIC_KEY)) {
                                            publicKeysMap.put(jsonAccountObject.get("guid").getAsString(),
                                                    jsonAccountObject.get(JsonConstants.PUBLIC_KEY).getAsString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    onLoadPublicKeysListener.onLoadPublicKeysComplete(publicKeysMap);
                } else {
                    if (onLoadPublicKeysListener != null) {
                        onLoadPublicKeysListener.onLoadPublicKeyError(response.errorMessage);
                    }
                }
            }
        };

        if (guidList.length() > 0) {
            BackendService.withSyncConnection(mApplication)
                    .getAccountInfoBatch(guidList.toString(), false, false, listener);
        } else {
            if (onLoadPublicKeysListener != null) {
                onLoadPublicKeysListener.onLoadPublicKeyError(null);
            }
        }
    }

    public void checkPublicKey(final Contact contact,
                               final OnLoadPublicKeyListener onLoadPublicKeyListener) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    if (onLoadPublicKeyListener != null) {
                        if ((response.msgException != null) && !StringUtil.isNullOrEmpty(response.msgException.getIdent())) {
                            onLoadPublicKeyListener.onLoadPublicKeyError(response.msgException.getIdent());
                        } else {
                            onLoadPublicKeyListener.onLoadPublicKeyError(response.errorMessage);
                        }
                    }
                } else {
                    try {
                        JsonObject responseJSON = response.jsonObject;
                        JsonObject accountObject = responseJSON.get(JsonConstants.ACCOUNT).getAsJsonObject();

                        if (StringUtil.isNullOrEmpty(contact.getPublicKey()) || StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey()) || StringUtil.isNullOrEmpty(contact.getSimsmeId())) {

                            if (StringUtil.isNullOrEmpty(contact.getPublicKey())) {
                                String publicKey = accountObject.get(JsonConstants.PUBLIC_KEY).getAsString();

                                contact.setPublicKey(publicKey);
                            }

                            if (StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                                String simsmeId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, accountObject);
                                if (!StringUtil.isNullOrEmpty(simsmeId)) {
                                    contact.setSimsmeId(simsmeId);
                                }
                            }

                            if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                                setEncryptedProfileInfosToContact(accountObject, contact);
                            } else {
                                decryptedAndSetProfilInfosToContact(accountObject, contact, true);
                            }

                            insertOrUpdateContact(contact);
                        }
                        if (accountObject.has("tempDeviceGuid") && accountObject.has("publicKeyTempDevice") && accountObject.has("pkSign256TempDevice")) {
                            //  Signature prüfen
                            String tempDeviceGuid = accountObject.get("tempDeviceGuid").getAsString();
                            String publicKeyTempDevice = accountObject.get("publicKeyTempDevice").getAsString();
                            String pkSign256TempDevice = accountObject.get("pkSign256TempDevice").getAsString();

                            PublicKey pubKey = XMLUtil.getPublicKeyFromXML(contact.getPublicKey());
                            if (SecurityUtil.verifyData(pubKey, Base64.decode(pkSign256TempDevice, Base64.DEFAULT), publicKeyTempDevice.getBytes(StandardCharsets.UTF_8), true)) {
                                contact.setTempDeviceInfo(tempDeviceGuid, publicKeyTempDevice);
                            }
                        } else {
                            contact.removeTempDeviceInfo();
                        }

                        if (accountObject.has("readOnly") && !accountObject.get("readOnly").isJsonNull()) {
                            boolean readOnly = StringUtil.isEqual("1", accountObject.get("readOnly").getAsString());
                            contact.setTempReadonly(readOnly);
                        }

                        if (accountObject.has("pushSilentTill")) {
                            final String pushSilentTill = accountObject.get("pushSilentTill").getAsString();
                            contact.setSilentTill(DateUtil.utcWithoutMillisStringToMillis(pushSilentTill));
                            insertOrUpdateContact(contact);
                        } else {
                            contact.setSilentTill(0);
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }

                    if (onLoadPublicKeyListener != null) {
                        onLoadPublicKeyListener.onLoadPublicKeyComplete(contact);
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .getAccountInfo(contact.getAccountGuid(), 0, false, true, true, listener);
    }

    public void loadContactsAccountInfo(final List<String> contactGuids)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if ((response.jsonArray != null) && (response.jsonArray.size() > 0)) {
                    for (int i = 0; i < response.jsonArray.size(); i++) {
                        JsonElement jsonElement = response.jsonArray.get(i);
                        JsonObject jsonAccountObject = JsonUtil.searchJsonObjectRecursive(jsonElement, JsonConstants.ACCOUNT);

                        if (jsonAccountObject == null) {
                            continue;
                        }

                        String guid = JsonUtil.stringFromJO(JsonConstants.GUID, jsonAccountObject);
                        String publicKey = JsonUtil.stringFromJO(JsonConstants.PUBLIC_KEY, jsonAccountObject);

                        if (!StringUtil.isNullOrEmpty(guid)) {
                            try {
                                Contact contact = getContactByGuid(guid);
                                if (contact != null) {
                                    if (!StringUtil.isNullOrEmpty(publicKey)) {
                                        contact.setPublicKey(publicKey);
                                    }

                                    String simsmeId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, jsonAccountObject);

                                    if (!StringUtil.isNullOrEmpty(simsmeId)) {
                                        contact.setSimsmeId(simsmeId);
                                    }

                                    if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                                        setEncryptedProfileInfosToContact(jsonAccountObject, contact);
                                    } else {
                                        decryptedAndSetProfilInfosToContact(jsonAccountObject,
                                                contact, true);
                                    }

                                    insertOrUpdateContact(contact);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        };

        String guids = StringUtil.getStringFromList(",", contactGuids);
        BackendService.withSyncConnection(mApplication)
                .getAccountInfoBatch(guids, true, false, listener);

        if (rm.isError) {
            throw new LocalizedException(LocalizedException.BACKEND_REQUEST_FAILED, rm.errorMsg + " " + rm.errorIdent);
        }
    }

    /**
     * @param withDeletedContacts include deleted
     */
    public List<Contact> getSimsMeContactsWithPubKey(boolean withDeletedContacts) {
        List<Contact> temp;
        synchronized (contactDao) {
            QueryBuilder<Contact> queryBuilder = contactDao.queryBuilder();

            queryBuilder.where(Properties.PublicKey.isNotNull());

            temp = queryBuilder.build().forCurrentThread().list();
        }
        List<Contact> rc = new ArrayList<>(temp.size());

        if (withDeletedContacts) {
            return temp;
        }

        for (Contact contact : temp) {
            try {
                if (contact.isDeletedHidden()) {
                    continue;
                }
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                continue;
            }
            rc.add(contact);
        }
        return rc;
    }

    /**
     * getGuidsAsStringFromSimsMeContacts
     *
     * @return kommaseparierter String der Guids der SIMSme Kontakte
     */
    public String getGuidsAsStringFromSimsMeContacts() {
        List<Contact> temp;
        synchronized (contactDao) {
            QueryBuilder<Contact> queryBuilder = contactDao.queryBuilder();

            queryBuilder.where(Properties.PublicKey.isNotNull());

            temp = queryBuilder.build().forCurrentThread().list();
        }

        StringBuilder sb = new StringBuilder();

        for (Contact contact : temp) {
            try {
                if (contact.isDeletedHidden()) {
                    continue;
                }
                if (StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
                    continue;
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                continue;
            }
            sb.append(contact.getAccountGuid()).append(",");
        }

        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : null;
    }

    public PublicKey getPublicKeyForContact(String guid)
            throws LocalizedException {
        PublicKey key = null;
        Contact contact = getContactByGuid(guid);

        if ((contact != null) && (contact.getPublicKey() != null)) {
            key = XMLUtil.getPublicKeyFromXML(contact.getPublicKey());
        }

        return key;
    }

    public PublicKey getPublicKeyForContact(String deviceGuid, Date refDate)
            throws LocalizedException {

        JsonObject deviceInfo = mTempDeviceCache.get(deviceGuid);

        if (deviceInfo == null) {
            return null;
        }

        PublicKey key = XMLUtil.getPublicKeyFromXML(deviceInfo.get("pubKey").getAsString());

        String validFrom = deviceInfo.get("validity").getAsJsonObject().get("start").getAsString();
        String validTo = deviceInfo.get("validity").getAsJsonObject().get("end").getAsString();
        if (refDate.before(DateUtil.utcWithoutMillisStringToDate(validFrom))) {
            // Noch nicht gültig
            return null;
        }

        if (refDate.after(DateUtil.utcWithoutMillisStringToDate(validTo))) {
            //  nicht mehr gültig
            return null;
        }

        return key;
    }

    public Contact getContactByGuid(String guid)
            throws LocalizedException {
        if (StringUtil.isNullOrEmpty(guid)) {
            return null;
        }

        List<Contact> contacts = getContactsByGuid(new String[]{guid});

        if (contacts == null) {
            return null;
        }
        if (contacts.size() == 1) {
            return contacts.get(0);
        } else if (contacts.size() > 1) {
            //Wir haben mehrere Kontakte mit der gleichen Account Guid
            Contact returnValue = null;

            for (Contact contact : contacts) {
                //Den besten Kontakt raus suchen
                if (contact == null) {
                    continue;
                }

                //Wenn er auf dem Server bekannt ist und im Telefonbuch
                if (!contact.getIsHidden()) {
                    returnValue = contact;
                }

                if (contact.getIsHidden() && (returnValue == null)) {
                    returnValue = contact;
                }

                if (contact.isDeletedHidden() && (returnValue == null)) {
                    returnValue = contact;
                }

                if (returnValue != null && returnValue.getMandant() == null && contact.getMandant() != null) {
                    returnValue = contact;
                }

                if (!contact.isDeletedHidden()) {
                }
            }

            return returnValue;
        } else {
            return null;
        }
    }

    public synchronized Contact getOwnContact() {
        AccountController accountController = mApplication.getAccountController();
        // KS: Prevent npe
        if (accountController == null) {
            LogUtil.w(TAG, "Ouch! accountController is null in GetOwnContact()!");
            return null;
        }
        List<Contact> contacts = getContactsByGuid(new String[]{accountController.getAccount().getAccountGuid()});

        if (contacts == null || contacts.isEmpty()) {
            try {
                Contact ownContact = new Contact();

                fillOwnContactWithAccountInfosLocally(ownContact);

                insertOrUpdateContact(ownContact);

                return ownContact;
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                return null;
            }
        }

        if (contacts.size() == 1) {
            return contacts.get(0);
        }
        // TODO : Mehr als ein Kontakt --> Aufräumen
        return contacts.get(0);
    }

    private boolean fillOwnContactWithAccountInfosLocally(@NonNull Contact contact)
            throws LocalizedException {
        boolean hasChanges = false;

        if (StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
            contact.setAccountGuid(mApplication.getAccountController().getAccount().getAccountGuid());
            hasChanges = true;
        }

        if (!StringUtil.isEqual(contact.getAccountGuid(), mApplication.getAccountController().getAccount().getAccountGuid())) {
            throw new LocalizedException(LocalizedException.NOT_OWN_CONTACT, "Contact is not own Contact");
        }

        if (!StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, contact.getClassEntryName())) {
            contact.setClassEntryName(Contact.CLASS_OWN_ACCOUNT_ENTRY);
            hasChanges = true;
        }

        String nickname = mApplication.getAccountController().getAccount().getName();
        if (!StringUtil.isNullOrEmpty(nickname)) {
            contact.setNickname(nickname);
            hasChanges = true;
        }

        String phone = mApplication.getAccountController().getAccount().getPhoneNumber();
        if (!StringUtil.isNullOrEmpty(phone)) {
            contact.setPhoneNumber(phone);
            hasChanges = true;
        }

        String accountID = mApplication.getAccountController().getAccount().getAccountID();
        if (!StringUtil.isNullOrEmpty(accountID)) {
            contact.setSimsmeId(accountID);
            hasChanges = true;
        }

        String publicKey = mApplication.getAccountController().getAccount().getPublicKey();
        if (!StringUtil.isNullOrEmpty(publicKey)) {
            contact.setPublicKey(publicKey);
            hasChanges = true;
        }

        String state = mApplication.getAccountController().getAccount().getStatusText();
        if (!StringUtil.isNullOrEmpty(state)) {
            contact.setStatusText(state);
            hasChanges = true;
        }

        if (contact.getState() == null || contact.getState() != Contact.STATE_HIGH_TRUST) {
            contact.setState(Contact.STATE_HIGH_TRUST);
            hasChanges = true;
        }

        String email = mApplication.getAccountController().getEmailAttributePre22(AccountController.EMAIL_ATTRIBUTES_EMAIL);
        if (!StringUtil.isNullOrEmpty(email)) {
            contact.setEmail(email);
            hasChanges = true;
        }

        String name = mApplication.getAccountController().getEmailAttributePre22(AccountController.EMAIL_ATTRIBUTES_LASTNAME_PRE_22);
        if (!StringUtil.isNullOrEmpty(name)) {
            contact.setLastName(name);
            hasChanges = true;
        }

        String firstName = mApplication.getAccountController().getEmailAttributePre22(AccountController.EMAIL_ATTRIBUTES_FIRSTNAME_PRE_22);
        if (!StringUtil.isNullOrEmpty(firstName)) {
            contact.setFirstName(firstName);
            hasChanges = true;
        }

        if (StringUtil.isNullOrEmpty(contact.getDomain())) {
            String domain = mApplication.getAccountController().getEmailDomainPre22();
            if (!StringUtil.isNullOrEmpty(domain)) {
                contact.setDomain(domain);
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    public void fillOwnContactWithAccountInfos(@Nullable Contact contact)
            throws LocalizedException {
        Contact ownContact;
        if (contact == null) {
            ownContact = getOwnContact();
            if (ownContact == null) {
                ownContact = new Contact();
            }
        } else {
            ownContact = contact;
        }

        boolean hasChanges = fillOwnContactWithAccountInfosLocally(ownContact);

        if (hasChanges) {
            insertOrUpdateContact(ownContact);
        }
    }

    public ArrayList<Contact> getContactsByGuid(String[] guids) {
        synchronized (contactDao) {
            QueryBuilder<Contact> queryBuilder = contactDao.queryBuilder();

            if (guids == null || guids.length <= 0) {
                return new ArrayList<>();
            } else if (guids.length == 1) {
                queryBuilder.where(Properties.AccountGuid.eq(guids[0]));
            } else {
                String[] moreGuids = new String[guids.length - 2];

                System.arraycopy(guids, 2, moreGuids, 0, moreGuids.length);

                WhereCondition[] moreConditions = new WhereCondition[moreGuids.length];

                for (int i = 0; i < moreGuids.length; i++) {
                    moreConditions[i] = Properties.AccountGuid.eq(moreGuids[i]);
                }

                queryBuilder.whereOr(Properties.AccountGuid.eq(guids[0]), Properties.AccountGuid.eq(guids[1]), moreConditions);
            }

            //fixme hier ist ein moeglicher crash, wenn build (oder forCurrentThread) kein ergebnis liefert
            //nachstellbar, wenn man "nachricht senden" auf einem nicht-simsme-kontakt aufruft
            try {
                List<Contact> contacts = queryBuilder.build().forCurrentThread().list();

                if (contacts.size() > 0) {
                    return (ArrayList<Contact>) contacts;
                } else {
                    return new ArrayList<>();
                }
            } catch (IllegalArgumentException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                return new ArrayList<>();
            }
        }
    }

    public ArrayList<Contact> getBlockedContacts() {
        synchronized (contactDao) {
            QueryBuilder<Contact> queryBuilder = contactDao.queryBuilder();

            return (ArrayList<Contact>) queryBuilder.where(Properties.IsBlocked.eq(true)).build().forCurrentThread().list();
        }
    }

    public void syncContacts(final OnLoadContactsListener onLoadContactsListener, final boolean mergeOldContacts, final boolean hasPermission) {
        if (onLoadContactsListener != null) {
            LogUtil.i(TAG, "syncContacts start:" + onLoadContactsListener.getClass().getName());
        }

        if (mSyncContactsListener == null) {
            mSyncContactsListener = new LoadContactsListener(onLoadContactsListener);
        } else {
            mSyncContactsListener.addLoadContactsListener(onLoadContactsListener);
        }

        if (mSyncContactsTask == null) {
            LogUtil.i(TAG, "loadContacts syncContactsTask");
            TaskManagerController taskManagerController = mApplication.getTaskManagerController();
            mSyncContactsTask = taskManagerController.getContactTaskManager().executeSyncAllContactsTask(
                    mApplication, mSyncContactsListener, mergeOldContacts, hasPermission);
        }
    }

    /**
     * updateContactProfileInfosFromServer
     *
     * @param guid guid
     */
    public void updateContactProfileInfosFromServer(final String guid) {
        try {
            Contact contact = getContactByGuid(guid);

            if (contact == null) {
                return;
            }

            AsyncHttpTask.AsyncHttpCallback<String> httpCallback = new AsyncHttpTask.AsyncHttpCallback<String>() {
                @Override
                public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                    BackendService.withSyncConnection(mApplication)
                            .getAccountInfo(guid, 1, false, true, false, listener);
                }

                @Override
                public String asyncLoaderServerResponse(BackendResponse response)
                        throws LocalizedException {
                    JsonObject jo = response.jsonObject;

                    if (jo == null || !jo.has(JsonConstants.ACCOUNT)) {
                        return null;
                    }

                    Contact contact = getContactByGuid(guid);
                    JsonObject accountObject = jo.getAsJsonObject(JsonConstants.ACCOUNT);

                    if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                        //erstmal nur die profilsachen speichern
                        setEncryptedProfileInfosToContact(accountObject, contact);
                        insertOrUpdateContact(contact);

                        return "encrypted";
                    } else {
                        //entschlüsseln und speichern
                        decryptedAndSetProfilInfosToContact(accountObject, contact, true);
                        insertOrUpdateContact(contact);

                        return "decrypted";
                    }
                }

                @Override
                public void asyncLoaderFinished(String result) {
                    if (StringUtil.isEqual(result, "decrypted")) {
                        notifyOnContactProfileInfoChangeNotificationListener(guid);
                    }
                }

                @Override
                public void asyncLoaderFailed(String errorMessage) {
                    //
                }
            };

            AsyncHttpTask<String> task = new AsyncHttpTask<>(httpCallback);
            task.executeOnExecutor(CONTACT_PROFILE_INFO_SERIAL_EXECUTOR);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * updateContactProfileInfosFromServer
     *
     * @param guid
     * @param onLoadPublicKeyListener
     */
    public void getAccountInfoForSentContact(final String guid, final OnLoadPublicKeyListener onLoadPublicKeyListener) {

        final AsyncHttpTask.AsyncHttpCallback<Contact> httpCallback = new AsyncHttpTask.AsyncHttpCallback<Contact>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getAccountInfo(guid, 1, true, true, false, listener);
            }

            @Override
            public Contact asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                final JsonObject jo = response.jsonObject;

                if (jo == null || !jo.has(JsonConstants.ACCOUNT)) {
                    return null;
                }

                final Contact contact = new Contact();
                final JsonObject accountObject = jo.getAsJsonObject(JsonConstants.ACCOUNT);

                if (jo.has(JsonConstants.ACCOUNT)) {
                    final JsonObject jsonAccount = jo.get(JsonConstants.ACCOUNT).getAsJsonObject();

                    if (jsonAccount.has(JsonConstants.ACCOUNT_ID)) {
                        contact.setSimsmeId(jsonAccount.get(JsonConstants.ACCOUNT_ID).getAsString());
                    }

                    contact.setAccountGuid(guid);

                    if (jsonAccount.has(JsonConstants.MANDANT)) {
                        contact.setMandant(jsonAccount.get(JsonConstants.MANDANT).getAsString());
                    }

                    if (jsonAccount.has(JsonConstants.PUBLIC_KEY)) {
                        contact.setPublicKey(jsonAccount.get(JsonConstants.PUBLIC_KEY).getAsString());
                    }
                }

                setEncryptedProfileInfosToContact(accountObject, contact);
                return contact;
            }

            @Override
            public void asyncLoaderFinished(final Contact contact) {
                onLoadPublicKeyListener.onLoadPublicKeyComplete(contact);
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                onLoadPublicKeyListener.onLoadPublicKeyError(errorMessage);
            }
        };

        final AsyncHttpTask<Contact> task = new AsyncHttpTask<>(httpCallback);
        task.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    public void setEncryptedProfileInfosToContact(JsonObject accountObject, Contact contact)
            throws LocalizedException {
        if (accountObject.has(JsonConstants.STATUS) && !accountObject.get(JsonConstants.STATUS).isJsonNull()) {
            String encryptedStatus = accountObject.get(JsonConstants.STATUS).getAsString();
            contact.setEncryptedStatusText(encryptedStatus);
        }

        if (accountObject.has(JsonConstants.NICKNAME) && !accountObject.get(JsonConstants.NICKNAME).isJsonNull()) {
            String encryptedNickname = accountObject.get(JsonConstants.NICKNAME).getAsString();
            contact.setEncryptedNickname(encryptedNickname);
        }

        if (accountObject.has(JsonConstants.IMAGE_CHECKSUM) && !accountObject.get(JsonConstants.IMAGE_CHECKSUM).isJsonNull()) {
            String checksum = accountObject.get(JsonConstants.IMAGE_CHECKSUM).getAsString();
            contact.setProfileImageChecksum(checksum);
        }
    }

    private boolean decryptedAndSetProfilInfosToContact(JsonObject accountObject, Contact contact, boolean loadImage)
            throws LocalizedException {
        if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
            return false;
        }

        String aesKeyAsString = contact.getProfileInfoAesKey();
        SecretKey aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyAsString);

        if (accountObject.has(JsonConstants.STATUS) && !accountObject.get(JsonConstants.STATUS).isJsonNull()) {
            String encryptedStatus = accountObject.get(JsonConstants.STATUS).getAsString();
            String status = SecurityUtil.decryptBase64StringWithAES(encryptedStatus, aesKey);

            contact.setStatusText(status);
        }

        if (accountObject.has(JsonConstants.NICKNAME) && !accountObject.get(JsonConstants.NICKNAME).isJsonNull()) {
            String encryptedNickname = accountObject.get(JsonConstants.NICKNAME).getAsString();

            String nickname = SecurityUtil.decryptBase64StringWithAES(encryptedNickname, aesKey);

            contact.setNickname(nickname);

            mApplication.getSingleChatController().clearNameCache();
            mApplication.getGroupChatController().clearNameCache();
            mApplication.getChatOverviewController().clearNameCache();
        }

        if (accountObject.has(JsonConstants.IMAGE_CHECKSUM) && !accountObject.get(JsonConstants.IMAGE_CHECKSUM).isJsonNull()) {
            String checksum = accountObject.get(JsonConstants.IMAGE_CHECKSUM).getAsString();

            if (loadImage) {
                String oldChecksum = contact.getProfileImageChecksum();

                if (!StringUtil.isEqual(oldChecksum, checksum)) {
                    //bild laden
                    loadContactProfileImageFromServer(contact.getAccountGuid(), checksum);
                }
            } else {
                contact.setProfileImageChecksum(checksum);
            }
        }

        return true;
    }

    /**
     * listener registrieren
     *
     * @param listener listener
     */
    public void registerOnContactProfileInfoChangeNotification(OnContactProfileInfoChangeNotification listener) {
        if (listener != null) {
            mOnContactProfileInfoChangeNotificationList.add(listener);
        }
    }

    /**
     * listener entfernen
     *
     * @param listener listener
     */
    public void unregisterOnContactProfileInfoChangeNotification(OnContactProfileInfoChangeNotification listener) {
        if (listener != null) {
            mOnContactProfileInfoChangeNotificationList.remove(listener);
        }
    }

    private void notifyOnContactProfileInfoChangeNotificationListener(String guid) {
        for (OnContactProfileInfoChangeNotification listener : mOnContactProfileInfoChangeNotificationList) {
            listener.onContactProfilInfoHasChanged(guid);
        }
    }

    private void notifyOnContactProfileImageChangeNotificationListener(String guid) {
        for (OnContactProfileInfoChangeNotification listener : mOnContactProfileInfoChangeNotificationList) {
            listener.onContactProfilImageHasChanged(guid);
        }
    }

    /**
     * intent fuer ContactDetailActivity
     *
     * @param callerActivity aufrufende act
     * @param contact        contact
     * @return intent
     */
    public Intent getOpenContactInfoIntent(final BaseActivity callerActivity, final Contact contact) {
        try {
            if (contact == null || StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                return null;
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.toString(), e);
            return null;
        }
        Intent intent = new Intent(callerActivity, ContactDetailActivity.class);
        intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, contact.getAccountGuid());
        return intent;
    }

    private void loadContactProfileImageFromServer(final String contactGuid, final String checksum) {
        AsyncHttpTask.AsyncHttpCallback<byte[]> callback = new AsyncHttpTask.AsyncHttpCallback<byte[]>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getAccountImage(contactGuid, listener);
            }

            @Override
            public byte[] asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    return null;
                }

                String imageBase64String = response.jsonArray.get(0).getAsString();

                if (imageBase64String == null) {
                    return null;
                }
                Contact contact = getContactByGuid(contactGuid);

                if (contact == null) {
                    return null;
                }

                String aesKeyString = contact.getProfileInfoAesKey();
                if (StringUtil.isNullOrEmpty(aesKeyString)) {
                    return null;
                }

                SecretKey aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyString);

                if (aesKey == null) {
                    return null;
                }

                byte[] encryptedBytes = Base64.decode(imageBase64String, Base64.NO_WRAP);
                byte[] decryptedBytes = SecurityUtil.decryptMessageWithAES(encryptedBytes, aesKey);

                return Base64.decode(decryptedBytes, Base64.NO_WRAP);
            }

            @Override
            public void asyncLoaderFinished(byte[] result) {
                try {
                    if (result != null && result.length > 0) {
                        ChatImageController chatImageController = mApplication.getChatImageController();
                        chatImageController.saveImage(contactGuid, result);
                        chatImageController.removeFromCache(contactGuid);

                        Contact contact = getContactByGuid(contactGuid);
                        contact.setProfileImageChecksum(checksum);

                        insertOrUpdateContact(contact);
                        notifyOnContactProfileImageChangeNotificationListener(contactGuid);
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {

            }
        };

        AsyncHttpTask<byte[]> task = new AsyncHttpTask<>(callback);
        task.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    private ArrayList<Contact> loadContactsFromDatabase(boolean onlySimsmeContacts,
                                                        Comparator<Contact> comparator)
            throws LocalizedException {
        ArrayList<Contact> rc;
        synchronized (contactDao) {
            QueryBuilder<Contact> queryBuilder = contactDao.queryBuilder();

            queryBuilder.where(Properties.IsHidden.eq(false));
            Account account = mApplication.getAccountController().getAccount();
            if (account != null) {
                queryBuilder.where(Properties.AccountGuid.notEq(account.getAccountGuid()));
            }

            if (onlySimsmeContacts) {
                queryBuilder.where(Properties.AccountGuid.isNotNull(), Properties.IsSimsMeContact.eq(true));
            }

            rc = (ArrayList<Contact>) queryBuilder.build().forCurrentThread().list();
        }

        // TODO: Maybe optimize later to sql query for performance reasons
        rc = filterDuplicates(rc);

        Collections.sort(rc, comparator);
        return rc;
    }

    public Bitmap getFallbackImageByContact(Context context,
                                            Contact contact)
            throws LocalizedException {

        if(context == null) {
            return null;
        }

        String text = "";
        String name;

        if (contact != null) {
            name = StringUtil.trim(contact.getName());

            if (StringUtil.isNullOrEmpty(name)) {
                return null;
            }
            //filter emojis and trim (Bug 36416)
            name = name.replaceAll("\\p{So}+", "").trim();
            if (name.matches("\\+[0-9 ]+")) {
                String lastNameNumbers = name.substring(1).replaceAll(" ", "");

                if (lastNameNumbers.length() > 1) {
                    text = lastNameNumbers.substring(lastNameNumbers.length() - 2);
                }
            }

            String[] nameSplit = name.split(" ");

            if (nameSplit.length > 1) {
                text = nameSplit[0].substring(0, 1).toUpperCase()
                        + nameSplit[nameSplit.length - 1].substring(0, 1).toUpperCase();
            } else if (nameSplit.length == 1 && !nameSplit[0].equals("")) {
                text = name.substring(0, 1).toUpperCase();
            } else {
                text = "";
            }

            Bitmap bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.gfx_profil_placeholder);
            Bitmap bitmap = bm.copy(Bitmap.Config.ARGB_8888, true);

            Paint paint1 = new Paint();
            ColorFilter filter = new PorterDuffColorFilter(ContactUtil.getColorForName(name), PorterDuff.Mode.SRC_ATOP);
            paint1.setColorFilter(filter);
            paint1.setAlpha(50);
            Canvas canvas1 = new Canvas(bitmap);
            canvas1.drawBitmap(bitmap, 0, 0, paint1);

            final float scale = context.getResources().getDisplayMetrics().density;

            Canvas canvas = new Canvas(bitmap);

            // new antialised Paint
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // text color - #3D3D3D
            paint.setColor(Color.rgb(255, 255, 255));

            // text size in pixels
            paint.setTextSize(75 * scale);

            // draw text to the Canvas center
            Rect bounds = new Rect();

            paint.getTextBounds(text, 0, text.length(), bounds);

            int x = (bitmap.getWidth() - bounds.width()) / 2;
            int y = (bitmap.getHeight() + bounds.height()) / 2;

            canvas.drawText(text, x, y, paint);
            return bitmap;
        }

        return null;
    }

    public Bitmap getFallbackImageByGuid(Context context,
                                         String contactGuid,
                                         int scaleSize)
            throws LocalizedException {
        Contact contact = getContactByGuid(contactGuid);

        return getFallbackImageByContact(context, contact);
    }

    private ArrayList<Contact> filterDuplicates(ArrayList<Contact> contacts)
            throws LocalizedException {
        ArrayList<Contact> filteredContacts = new ArrayList<>();

        for (Contact contact : contacts) {
            if (contact.isDeletedHidden()) {
                continue;
            }

            filteredContacts.add(contact);
        }
        return filteredContacts;
    }

    private boolean processContactVerificationResult(Contact contact,
                                                     String qrCodeString)
            throws LocalizedException {
        try {
            boolean checkV1 = false;
            boolean verified = false;
            try {
                if (!StringUtil.isNullOrEmpty(qrCodeString) && qrCodeString.startsWith("V2")) {
                    int backspaceIndex = qrCodeString.indexOf("\r");
                    if (backspaceIndex < 0) {
                        throw new LocalizedException(LocalizedException.VERIFY_DATA_FAILED, "First Backspace not found");
                    }

                    int backspaceIndex2 = qrCodeString.indexOf("\r", backspaceIndex + 1);

                    if (backspaceIndex2 < 0) {
                        throw new LocalizedException(LocalizedException.VERIFY_DATA_FAILED, "Second Backspace not found");
                    }

                    byte[] checksumContact = ChecksumUtil.getSHA256ChecksumAsBytesForString(contact.getPublicKey());
                    String checksumQrCodeBase64 = qrCodeString.substring(backspaceIndex2 + 1);

                    if (checksumContact != null) {
                        String checksumContactBase64 = Base64.encodeToString(checksumContact, Base64.NO_WRAP);

                        if (StringUtil.isEqual(checksumContactBase64, checksumQrCodeBase64)) {
                            verified = true;
                        }
                    }
                } else {
                    checkV1 = true;
                }
            } catch (LocalizedException e) {
                checkV1 = true;
            }

            if (checkV1) {
                byte[] qrCodeData = Base64.decode(qrCodeString, Base64.DEFAULT);
                byte[] data = contact.getAccountGuid().getBytes(Encoding.UTF8);

                PublicKey key = XMLUtil.getPublicKeyFromXML(contact.getPublicKey());

                if (key == null) {
                    return false;
                }

                verified = SecurityUtil.verifyData(key, qrCodeData, data, false);
            }

            if (verified) {
                upgradeTrustLevel(contact, Contact.STATE_HIGH_TRUST);
            }

            return verified;
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.VERIFY_DATA_FAILED, e);
        }
    }

    public void prepareSingleContactFromServer(final Contact contact,
                                               final OnLoadContactFromServerListener onLoadContactListener) {
        String phoneNumber;

        try {
            phoneNumber = contact.getPhoneNumber();

            //remove +
            if (phoneNumber.startsWith("+")) {
                phoneNumber = phoneNumber.substring(1);
            }
            if (!Pattern.matches("[0-9]+", phoneNumber)) {
                onLoadContactListener.onLoadContactFromServerError(mApplication.getString(R.string.service_ERR_0098));
                return;
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            return;
        }

        getSingleContactFromServer(contact, onLoadContactListener);
    }

    private void getSingleContactFromServer(final Contact contact,
                                            final OnLoadContactFromServerListener onLoadContactListener) {
        try {

            LogUtil.i(TAG, "getSingleContactFromServer start");

            AccountController accountController = mApplication.getAccountController();
            String countryCode = accountController.getAccount().getCountryCode();
            String normalizedPhoneNumber = PhoneNumberUtil.normalizePhoneNumberNew(mApplication,
                    countryCode,
                    contact.getPhoneNumber());

            if (!StringUtil.isEqual(contact.getPhoneNumber(), normalizedPhoneNumber)) {
                contact.setPhoneNumber(normalizedPhoneNumber);
            }

            final List<Mandant> mandantenList = mApplication.getPreferencesController().getMandantenList();

            final ArrayList<Contact> foundContacts = new ArrayList<>();

            if (mandantenList != null) {

                for (int i = 0; i < mandantenList.size(); ++i) {

                    final Mandant mandant = mandantenList.get(i);
                    final int finalI = i;

                    String bcrypt = contact.getBcryptForMandant(mandant.salt);

                    if (StringUtil.isNullOrEmpty(bcrypt)) {
                        bcrypt = BCrypt.hashpw(normalizedPhoneNumber, mandant.salt);
                        bcrypt = bcrypt.substring(mandant.salt.length(), bcrypt.length());
                        contact.setBcryptForSalt(mandant.salt, bcrypt);
                    }

                    JsonArray hashData = new JsonArray();

                    hashData.add(new JsonPrimitive(mandant.salt + bcrypt));

                    if (hashData.size() > 0) {
                        // dieser listener muss leider bei jedem durhclauf neu gebaut werdne, da der mandant mit uebergeben werdne muss
                        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(BackendResponse response) {
                                if (response.isError) {
                                    onLoadContactListener.onLoadContactFromServerError(response.errorMessage);
                                } else {
                                    try {
                                        JsonArray responseArray = response.jsonArray;
                                        int size = responseArray.size();

                                        if (size != 0) {
                                            JsonObject json = responseArray.get(0).getAsJsonObject();
                                            Entry<String, JsonElement> mapEntry = (Entry<String, JsonElement>)
                                                    json.entrySet().toArray()[0];
                                            String accountGuid = mapEntry.getValue().getAsString();
                                            Contact hiddenContact = createContactIfNotExists(accountGuid, contact.getPhoneNumber(), null, null, mandant.ident, true);

                                            // ist null, wenn man nach sich selbst sucht
                                            if (hiddenContact != null) {
                                                if (hiddenContact.getState() == Contact.STATE_UNSIMSABLE) {
                                                    hiddenContact.setState(Contact.STATE_LOW_TRUST);
                                                }

                                                if (StringUtil.isEqual(mandant.ident, RuntimeConfig.getMandant())) {
                                                    foundContacts.add(0, hiddenContact);
                                                } else {
                                                    foundContacts.add(hiddenContact);
                                                }
                                            }
                                        }

                                        //beim letzten durchlauf die kontakte zurueckgeben
                                        if (finalI == mandantenList.size() - 1) {
                                            if (foundContacts.size() > 0) {
                                                onLoadContactListener.onLoadMultipleContactsFromServerComplete(foundContacts);
                                            } else {
                                                String message = mApplication.getString(R.string.chat_contact_not_found);
                                                onLoadContactListener.onLoadContactFromServerError(message);
                                            }
                                        }
                                    } catch (LocalizedException e) {
                                        onLoadContactListener.onLoadContactFromServerError(e.getMessage());
                                        LogUtil.e(TAG, e.getMessage(), e);
                                    }
                                }
                            }
                        };

                        BackendService.withAsyncConnection(mApplication)
                                .getKnownAccounts(hashData, mandant.salt, mandant.ident, JsonConstants.SEARCH_TYPE_PHONE, listener);
                    }
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * Callback Methods Implementations
     */

    @Override
    public void appIsUnlock() {
        AccountController accountController = mApplication.getAccountController();
        if (accountController.hasAccountFullState()) {
            startWorkAfterLogin();
        }
    }

    @Override
    public void appDidEnterForeground() {
        AccountController accountController = mApplication.getAccountController();
        LoginController loginController = mApplication.getLoginController();
        if (loginController.isLoggedIn() && accountController.hasAccountFullState()) {
            startWorkAfterLogin();
        }
    }

    @Override
    public void appGoesToBackGround() {

    }

    void startWorkAfterLogin() {
        startTimerForCheck();
    }

    void startTimerForCheck() {
        // Einmal pro Tag den AccessToken refreshen
        String dateAsString = mApplication.getPreferencesController().getBackgroundAccessTokenDate();

        boolean isSameDay = isSameDay(dateAsString);

        // KS TEST: Renew token everytime!
        if(BuildConfig.DEBUG) {
            LogUtil.d(TAG, "Debug mode detected: Get a new access token everytime!");
            isSameDay = false;
        }

        if (!isSameDay || StringUtil.isNullOrEmpty(ContactController.this.mApplication.getPreferencesController().getFetchInBackgroundAccessToken())) {
            ContactController.this.mApplication.getMessageController().getBackgroundAccessToken(new GenericActionListener<String>() {
                @Override
                public void onSuccess(String object) {
                    mApplication.getPreferencesController().setBackgroundAccessTokenDate(DateUtil.getDateStringFromLocale());
                }

                @Override
                public void onFail(String message, String errorIdent) {

                }
            });
        }

        dateAsString = mApplication.getPreferencesController().getContactSyncDate();
        isSameDay = isSameDay(dateAsString);
        if (!isSameDay) {
            if (mContactTimer != null) {
                return;
            }

            final Runnable contactRunnable = new Runnable() {
                public void run() {
                    try {
                        ContactController.this.checkPrivateIndexContacts(null, true, new GenericActionListener<Void>() {
                            @Override
                            public void onSuccess(Void object) {
                                mApplication.getPreferencesController().setContactSyncDate(DateUtil.getDateStringFromLocale());
                                updatePrivateIndexEntriesAsync();
                            }

                            @Override
                            public void onFail(String message, String errorIdent) {
                                //
                            }
                        });
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "Check Private Index Contacts failed", e);
                    }

                    mContactTimer = null;
                }
            };

            mContactTimer = new Timer();
            final Handler handler = new Handler();

            TimerTask contactTask = new TimerTask() {
                @Override
                public void run() {
                    handler.post(contactRunnable);
                }
            };

            mContactTimer.schedule(contactTask, 10000);
        }
    }

    protected boolean isSameDay(String dateStringToCheck) {
        String today = DateUtil.getDateStringFromLocale();

        return StringUtil.isEqual(dateStringToCheck, today);
    }

    @Override
    public void appWillBeLocked() {
        //den Task Canceln, da er mit ver- bzw. entschlüsseln arbeitet
        if (mSyncContactsTask != null) {
            mSyncContactsTask.cancel();
        }
    }

    /**
     * Overwritten in the B2B version
     */
    public CompanyContact getCompanyContactWithAccountGuid(final String guid) {
        return null;
    }

    /**
     * Overwritten in the B2B version
     */
    public void copyCompanyContactInfoToContact(@NonNull final CompanyContact companyContact, @NonNull final Contact contact)
            throws LocalizedException {
    }

    /**
     * Overwritten in the B2B version
     */
    public void deleteAllDomainContacts() {
    }

    /**
     * Overwritten in the B2B version
     */
    public void deleteFtsContact(String accoundGuid)
            throws LocalizedException {
    }

    public void saveContactToFtsDatabase(@NonNull final Contact contact, final boolean isInitialInsert)
            throws LocalizedException {
    }

    /**
     * Overwritten in the B2B version
     */
    public void createAndFillFtsDB(boolean onlyCreate)
            throws LocalizedException {
    }

    /**
     * Overwritten in the B2B version
     */
    public void fillFtsDB(final boolean isInitialInsert) {
    }

    public boolean existsFtsDatabase() {
        return true;
    }

    /**
     * Overwritten in the B2B version
     */
    public void openFTSDatabase(GenericActionListener<Void> listener) {
    }

    /**
     * Overwritten in the B2B version
     */
    public ResponseModel loadCompanyIndexSync(final LoadCompanyContactsListener listener, boolean checkAllEntries) {
        return null;
    }

    public void loadPrivateIndexEntriesSync() {
        mLoadPrivateIndexEntries = new LoadPrivateIndexTask(contactDao, mApplication, null, new GenericActionListener<ArrayMap<String, String>>() {
            @Override
            public void onSuccess(ArrayMap<String, String> loadedGuids) {
                ArrayMap<String, String> guids = null;
                if (loadedGuids != null) {
                    guids = mApplication.getPreferencesController().removePrivateIndexGuidsToLoad(loadedGuids);
                }

                mLoadPrivateIndexEntries = null;
                if (guids == null) {
                    guids = mApplication.getPreferencesController().getPrivateIndexGuidsToLoad();
                }

                if (guids.size() > 0) {
                    LogUtil.i(TAG, "Load private index again");
                    loadPrivateIndexEntries(null);
                }
            }

            @Override
            public void onFail(String message, String errorIdent) {
                //wird nicht aufgerufen
                mLoadPrivateIndexEntries = null;
            }
        });

        Boolean isError = mLoadPrivateIndexEntries.doInBackground();
        mLoadPrivateIndexEntries.onPostExecute(isError);
    }

    public void loadPrivateIndexEntries(final GenericActionListener<Void> listener) {
        if (mLoadPrivateIndexEntries != null) {
            if (listener != null) {
                listener.onFail(null, null);
            }
            return;
        }

        String timeStamp = mApplication.getPreferencesController().getLastPrivateIndexSyncTimeStamp();
        ArrayMap<String, String> indexGuidsToLoad = null;
        if (!StringUtil.isNullOrEmpty(timeStamp)) {
            indexGuidsToLoad = mApplication.getPreferencesController().getPrivateIndexGuidsToLoad();
        }

        mLoadPrivateIndexEntries = new LoadPrivateIndexTask(contactDao, mApplication, indexGuidsToLoad, new GenericActionListener<ArrayMap<String, String>>() {
            @Override
            public void onSuccess(ArrayMap<String, String> loadedGuids) {
                ArrayMap<String, String> guids = null;
                if (loadedGuids != null) {
                    guids = mApplication.getPreferencesController().removePrivateIndexGuidsToLoad(loadedGuids);
                }

                mLoadPrivateIndexEntries = null;
                if (guids == null) {
                    guids = mApplication.getPreferencesController().getPrivateIndexGuidsToLoad();
                }

                if (guids.size() > 0) {
                    LogUtil.i(TAG, "Load private index again");
                    loadPrivateIndexEntries(listener);
                } else {
                    if (listener != null) {
                        listener.onSuccess(null);
                    }
                }
            }

            @Override
            public void onFail(String message, String errorIdent) {
                //wird nicht aufgerufen
                mLoadPrivateIndexEntries = null;
                if (listener != null) {
                    listener.onFail(message, errorIdent);
                }
            }
        });

        mLoadPrivateIndexEntries.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void updatePrivateIndexEntriesAsync() {
        if (!ConfigUtil.INSTANCE.syncPrivateIndexToServer()) {
            return;
        }

        if (mUpdatePrivateIndexEntries != null) {
            mStartUpdatePrivateIndexTaskAgain = true;
            return;
        }

        mStartUpdatePrivateIndexTaskAgain = false;
        mUpdatePrivateIndexEntries = new UpdatePrivateIndexTask(contactDao, mApplication, new GenericActionListener<Void>() {
            @Override
            public void onSuccess(Void object) {
                mUpdatePrivateIndexEntries = null;

                if (mStartUpdatePrivateIndexTaskAgain) {
                    updatePrivateIndexEntriesAsync();
                }
            }

            @Override
            public void onFail(String message, String errorIdent) {
                Toast.makeText(mApplication, R.string.update_private_index_failed, Toast.LENGTH_LONG).show();
            }
        });

        mUpdatePrivateIndexEntries.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void updatePrivateIndexEntriesSync(GenericUpdateListener<Void> migrationTaskListener)
            throws LocalizedException {
        if (ConfigUtil.INSTANCE.syncPrivateIndexToServer()) {
            updatePrivateIndex(mApplication, contactDao, migrationTaskListener);
        } else {
            if (migrationTaskListener != null) {
                migrationTaskListener.onSuccess(null);
            }
        }
    }

    /**
     * @param contact     Kontakt wo die Daten gespeichert werden sollen
     * @param lastName    Nachname, Null keine Änderung, Leerstring wenn gelöscht
     * @param firstName   Vorname, Null keine Änderung, Leerstring wenn gelöscht
     * @param phone       Telefonnummer, Null keine Änderung, Leerstring wenn gelöscht
     * @param email       E-Mail-Adresse, Null keine Änderung, Leerstring wenn gelöscht
     * @param emailDomain E-Mail Domain
     * @param department  Abteilung, Null keine Änderung, Leerstring wenn gelöscht
     * @param oooStatus   Abwesendheitsstatus
     * @param imageBytes  Kontaktbild, Null keine Änderung
     * @param trustState  Trust state vom Kontakt oder -1 wenn es keine Änderung gibt
     * @param forceUpdate soll der Private Index aktualisiert werden
     * @return true - wenn es Änderungen am Account gab
     */
    public boolean saveContactInformation(@NonNull Contact contact,
                                          @Nullable final String lastName,
                                          @Nullable final String firstName,
                                          @Nullable final String phone,
                                          @Nullable final String email,
                                          @Nullable final String emailDomain,
                                          @Nullable final String department,
                                          @Nullable final String oooStatus,
                                          @Nullable final byte[] imageBytes,
                                          final int trustState,
                                          final boolean forceUpdate)
            throws LocalizedException {
        boolean hasChanges = false;
        if (lastName != null) {
            if (!StringUtil.isEqual(contact.getLastName(), lastName)) {
                hasChanges = true;
                contact.setLastName(lastName);
            }
        }

        if (firstName != null) {
            if (!StringUtil.isEqual(contact.getFirstName(), firstName)) {
                hasChanges = true;
                contact.setFirstName(firstName);
            }
        }

        if (phone != null) {
            if (!StringUtil.isEqual(contact.getPhoneNumber(), phone)) {
                hasChanges = true;
                if (StringUtil.isNullOrEmpty(phone)) {
                    contact.clearPhoneNumber();
                    if (StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, contact.getClassEntryName())) {
                        mApplication.getAccountController().getAccount().setPhoneNumber("");
                        mApplication.getAccountController().saveOrUpdateAccount(mApplication.getAccountController().getAccount());
                    }
                } else {
                    contact.setPhoneNumber(phone);
                    if (StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, contact.getClassEntryName())) {
                        mApplication.getAccountController().getAccount().setPhoneNumber(phone);
                        mApplication.getAccountController().saveOrUpdateAccount(mApplication.getAccountController().getAccount());
                    }
                }
            }
        }

        if (email != null) {
            if (!StringUtil.isEqual(contact.getEmail(), email)) {
                hasChanges = true;
                contact.setEmail(email);
            }
        }

        if (emailDomain != null) {
            if (!StringUtil.isEqual(contact.getDomain(), emailDomain)) {
                hasChanges = true;
                contact.setDomain(emailDomain);
            }
        }

        if (department != null) {
            if (!StringUtil.isEqual(contact.getDepartment(), department)) {
                hasChanges = true;
                contact.setDepartment(department);
            }
        }

        if (oooStatus != null) {
            if (!StringUtil.isEqual(contact.getOooStatus(), oooStatus)) {
                hasChanges = true;
                contact.setOooStatus(oooStatus);
            }
        }

        if (trustState > -1 && (contact.getState() == null || trustState != contact.getState())) {
            hasChanges = true;
            contact.setState(trustState);
        }

        if (imageBytes != null && imageBytes.length > 0) {
            hasChanges = true;
            mApplication.getChatImageController().saveImage(contact.getAccountGuid(), imageBytes);
        }

        if (StringUtil.isNullOrEmpty(contact.getClassEntryName())) {
            contact.setClassEntryName(Contact.CLASS_PRIVATE_ENTRY);
        }
        if (hasChanges || forceUpdate) {
            contact.setChecksum("");

            insertOrUpdateContact(contact);

            updatePrivateIndexEntriesAsync();
        }

        return hasChanges;
    }

    private void updatePrivateIndex(@NonNull final SimsMeApplication application, @NonNull final ContactDao contactDao, @Nullable final GenericUpdateListener<Void> migrationTaskListener)
            throws LocalizedException {
        try {
            final ResponseModel rm = new ResponseModel();
            application.getLoginController().setPKTaskIsRunning(this, true);

            List<Contact> localContacts = getContactsWithEmptyChecksum(contactDao);

            if (localContacts == null || localContacts.size() < 1) {
                return;
            }

            PublicKey pubKey = application.getKeyController().getUserKeyPair().getPublic();
            PrivateKey privKey = application.getKeyController().getUserKeyPair().getPrivate();

            for (int i = 0; i < localContacts.size(); i++) {
                final Contact contact = localContacts.get(i);
                String accountGuid = contact.getAccountGuid();
                if (StringUtil.isNullOrEmpty(accountGuid)) {
                    if (StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_OWN_ACCOUNT_ENTRY)) {
                        accountGuid = application.getAccountController().getAccount().getAccountGuid();
                        contact.setAccountGuid(accountGuid);
                    } else {
                        LogUtil.w(TAG, "UpdatePrivateIndex Account Guid is null");
                        continue;
                    }
                }

                String guid = contact.getPrivateIndexGuid();

                if (StringUtil.isNullOrEmpty(guid)) {
                    guid = GuidUtil.generatePrivateIndexGuid();
                }

                JsonObject jo = new JsonObject();

                jo.addProperty(JsonConstants.GUID, guid);

                SecretKey key = SecurityUtil.generateAESKey();

                byte[] keyBytes = SecurityUtil.getBase64BytesFromAESKey(key);

                byte[] encKeyBytes = SecurityUtil.encryptMessageWithRSA(keyBytes, pubKey);

                jo.addProperty(JsonConstants.KEY_DATA, Base64.encodeToString(encKeyBytes, Base64.NO_WRAP));

                IvParameterSpec iv = SecurityUtil.generateIV();

                jo.addProperty(JsonConstants.KEY_IV, SecurityUtil.getBase64StringFromIV(iv));

                JsonObject dataJO = contact.exportPrivateIndexEntryData();

                byte[] imgBytes = application.getChatImageController().loadImage(contact.getAccountGuid());
                if (imgBytes != null && imgBytes.length > 0) {
                    String imgBase64 = Base64.encodeToString(imgBytes, Base64.DEFAULT);
                    dataJO.addProperty(JsonConstants.IMAGE, imgBase64);
                }

                byte[] encData = SecurityUtil.encryptStringWithAES(dataJO.toString(), key, iv);

                if (encData == null) {
                    LogUtil.w(TAG, "UpdatePrivateIndex encrypted data is null");
                    continue;
                }

                String encBase64Data = Base64.encodeToString(encData, Base64.NO_WRAP);

                if (StringUtil.isNullOrEmpty(encBase64Data)) {
                    continue;
                }

                jo.addProperty(JsonConstants.DATA, encBase64Data);

                String signature = SecurityUtil.signDataAndEncodeToBase64String(privKey, encData, true);

                if (StringUtil.isNullOrEmpty(signature)) {
                    LogUtil.w(TAG, "UpdatePrivateIndex Signature is null");
                    continue;
                }

                jo.addProperty(JsonConstants.SIGNATURE, signature);

                BackendService.withSyncConnection(mApplication)
                        .insUpdPrivateIndexEntry(jo, new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(BackendResponse response) {
                                if (response.isError) {
                                    rm.setError(response);
                                    LogUtil.e(TAG, rm.errorExceptionMessage != null ? rm.errorExceptionMessage : rm.errorMsg);
                                    if (migrationTaskListener != null) {
                                        migrationTaskListener.onUpdate(mApplication.getResources().getString(R.string.insupd_private_index_error));
                                    }
                                } else {
                                    if (response.jsonArray != null && response.jsonArray.size() > 0) {
                                        JsonElement je = response.jsonArray.get(0);
                                        if (je.isJsonPrimitive()) {
                                            contact.setChecksum(je.getAsString());
                                            contactDao.update(contact);
                                        }
                                    }
                                }
                            }
                        });

                if (migrationTaskListener != null) {
                    migrationTaskListener.onUpdate(mApplication.getResources().getString(R.string.insupd_private_index_update, (i + 1), localContacts.size()));
                }
            }
        } finally {
            application.getLoginController().setPKTaskIsRunning(this, false);
        }
    }

    private List<Contact> getContactsWithEmptyChecksum(@NonNull ContactDao contactDao) {
        List<Contact> contacts = null;

        QueryBuilder<Contact> queryBuilder = contactDao.queryBuilder();

        queryBuilder.whereOr(Properties.Checksum.isNull(), Properties.Checksum.eq(""));

        contacts = queryBuilder.build().forCurrentThread().list();

        return contacts;
    }

    private void checkPrivateIndexContacts(@Nullable final List<String> contactGuids, final boolean runAsync, final GenericActionListener<Void> listener)
            throws LocalizedException {
        if (runAsync) {
            new AsyncTask<Void, Void, ResponseModel>() {
                @Override
                protected ResponseModel doInBackground(Void... params) {
                    try {
                        return checkPrivateIndexContactsInternally(contactGuids);
                    } catch (LocalizedException e) {
                        ResponseModel rm = new ResponseModel();
                        rm.isError = true;
                        rm.errorIdent = e.getIdentifier();
                        return rm;
                    }
                }

                @Override
                protected void onPostExecute(ResponseModel responseModel) {
                    if (responseModel != null && responseModel.isError) {
                        if (listener != null) {
                            listener.onFail(null, responseModel.errorIdent);
                        }
                    } else {
                        if (listener != null) {
                            listener.onSuccess(null);
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            ResponseModel rm = checkPrivateIndexContactsInternally(contactGuids);

            if (rm != null && rm.isError) {
                if (listener != null) {
                    listener.onFail(null, rm.errorIdent);
                }
            } else {
                if (listener != null) {
                    listener.onSuccess(null);
                }
            }
        }
    }

    private ResponseModel checkPrivateIndexContactsInternally(@Nullable final List<String> contactGuids)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();
        final List<String> guids = new ArrayList<>();
        if (contactGuids != null) {
            guids.addAll(contactGuids);
        } else {
            List<Contact> allContacts = contactDao.loadAll();

            if (allContacts != null && allContacts.size() > 0) {
                for (Contact contact : allContacts) {
                    String guid = contact.getAccountGuid();
                    if (!StringUtil.isNullOrEmpty(guid)) {
                        String ownAccountGuid = null;
                        Account ownAccount = mApplication.getAccountController().getAccount();
                        if (ownAccount != null) {
                            ownAccountGuid = ownAccount.getAccountGuid();
                        }

                        if (!AppConstants.GUID_SYSTEM_CHAT.equals(guid)
                                && !guid.equals(ownAccountGuid)) {
                            guids.add(contact.getAccountGuid());
                        }
                    }
                }
            }
        }

        if (guids.size() == 0) {
            return rm;
        }

        final String guidArray = StringUtil.getStringFromList(",", guids);
        BackendService.withSyncConnection(mApplication)
                .getAccountInfoBatch(guidArray, false, false, new IBackendService.OnBackendResponseListener() {
                    @Override
                    public void onBackendResponse(BackendResponse response) {
                        if (response.isError) {
                            rm.setError(response);
                            return;
                        }

                        if ((response.jsonArray != null) && (response.jsonArray.size() > 0)) {
                            final ArrayList<String> knownServerGuids = new ArrayList<>(guidArray.length());

                            for (int i = 0; i < response.jsonArray.size(); i++) {
                                JsonElement jsonElement = response.jsonArray.get(i);
                                JsonObject jsonAccountObject = JsonUtil.searchJsonObjectRecursive(jsonElement, JsonConstants.ACCOUNT);

                                if (jsonAccountObject == null) {
                                    continue;
                                }

                                String guid = JsonUtil.stringFromJO(JsonConstants.GUID, jsonAccountObject);

                                if (!StringUtil.isNullOrEmpty(guid)) {
                                    knownServerGuids.add(guid);
                                }
                            }

                            if (guids.size() == knownServerGuids.size()) {
                                return;
                            }

                            for (String guid : guids) {
                                if (!knownServerGuids.contains(guid)) {
                                    LogUtil.e(TAG, "checkPrivateIndexContactsInternally -> Failed to read account info " + guid);
                                    hideDeletedContactLocally(guid);
                                } else {
                                    makeContactVisibleLocally(guid);
                                }
                            }
                        }
                    }
                });

        return rm;
    }

    public void fixPrivateIndexMergeProblemSync(@NonNull final GenericUpdateListener<Void> listener) {

        LoadPrivateIndexTask task = new LoadPrivateIndexTask(contactDao, mApplication, null, new GenericActionListener<ArrayMap<String, String>>() {
            @Override
            public void onSuccess(ArrayMap<String, String> object) {
                listener.onSuccess(null);
            }

            @Override
            public void onFail(String message, String errorIdent) {
                listener.onFail(message, errorIdent);
            }
        });

        task.setFixContactsOnServer(true);
        task.setMigrationListener(listener);
        Boolean isError = task.doInBackground();
        task.onPostExecute(isError);
    }

    public void hideContact(final String contactGuid) {
        try {
            Contact contact = getContactByGuid(contactGuid);

            if (contact == null) {
                return;
            }

            contact.setIsHidden(true);
            deleteFtsContact(contactGuid);

            saveContactInformation(contact, null, null, null, null, null, null, null, null, -1, true);
        } catch (LocalizedException le) {
            LogUtil.e(TAG, le.getMessage(), le);
        }
    }

    private void notifyChatControllerOfRemovedAccount(final Contact contact) {
        try {
            if (contact != null) {
                // nur wenn ein Chat existiert
                if (mApplication.getSingleChatController().getChatByGuid(contact.getAccountGuid()) != null) {
                    final String name = contact.getName();

                    if ((name != null) && (contact.getPublicKey() != null)) {
                        String logMessage = mApplication.getString(R.string.chat_system_message_removeAccountRegistrationAgain,
                                name);

                        mApplication.getSingleChatController()
                                .sendSystemInfo(contact.getAccountGuid(), contact.getPublicKey(), null, null,
                                        logMessage, -1);
                    }
                }
            } else LogUtil.e(TAG, "Null contact. Failed to notify chat controller.");
        } catch (LocalizedException le) {
            LogUtil.e(TAG, String.format("Failed to notifyChatControllerOfRemovedAccount [%s]:$[%s]", contact.getAccountGuid(), le.getMessage()), le);
        }
    }

    private void hideDeletedContactLocally(final String contactGuid) {
        try {
            Contact contact = getContactByGuid(contactGuid);

            if (contact == null) {
                return;
            }

            if (!contact.isDeletedHidden()) {
                notifyChatControllerOfRemovedAccount(contact);
                hideDeletedContactLocally(contact);
            }
        } catch (LocalizedException le) {
            LogUtil.e(TAG, String.format("Failed to hideDeletedContactLocally [%s]:[%s]", contactGuid, le.getMessage()), le);
        }
    }

    public void hideDeletedContactLocally(final Contact contact) {
        try {
            if (contact != null && !contact.isDeletedHidden()) {
                LogUtil.w(TAG, "hideDeletedContactLocally: " + contact.getAccountGuid());

                // Wir schliessen sie aus übersicht aus...
                contact.setIsDeletedHidden(true);
                deleteFtsContact(contact.getAccountGuid());

                // Und können ihm nicht mehr schreiben
                contact.setState(Contact.STATE_UNSIMSABLE);
                contact.setIsSimsMeContact(false);
                contact.setChecksum("");

                synchronized (contactDao) {
                    // Schoen wäre ein Verweis auf den neuen Kontakt im Datenmodell
                    // und eine extra Spalte
                    contactDao.update(contact);
                }
            }
        } catch (LocalizedException le) {
            LogUtil.e(TAG, String.format("Failed to hideDeletedContactLocally:[%s]", le.getMessage()), le);
        }
    }

    /**
     * Attempt to correct contacts that have been messed up by a previous version of the app.
     * IF the contact was "DeletedHidden" previously but now has a public key (as indicated in knownServerGuids)
     * then it indicates that this contact has been erroneously been marked as deletedHidden.
     * <p>
     * I am aware that the state STATE_LOW_TRUST is probably not correct, but once it has been messed up, there is no
     * way for me to recover the data. This is the best I can do.
     *
     * @param contactGuid a contactGuid is that has been checked to match an existing public key.
     */
    public void makeContactVisibleLocally(final String contactGuid) {
        try {
            Contact contact = getContactByGuid(contactGuid);
            if (contact == null) {
                return;
            }

            if (contact.isDeletedHidden()) {
                LogUtil.w(TAG, "makeContactVisibleLocally -> Fix wrong deletedHidden: " + contactGuid);

                contact.setIsDeletedHidden(false);
                saveContactToFtsDatabase(contact, false);
                contact.setState(Contact.STATE_LOW_TRUST);
                contact.setIsSimsMeContact(true);

                synchronized (contactDao) {
                    contactDao.update(contact);
                }
            }
        } catch (LocalizedException le) {
            LogUtil.e(TAG, "Failed to make contact visible locally:" + le.getMessage(), le);
        }

    }

    class LoadContactsListener
            extends ConcurrentTaskListener {

        private final ArrayList<OnLoadContactsListener> mOnLoadContactsListeners;

        LoadContactsListener(OnLoadContactsListener onLoadContactsListener) {
            mOnLoadContactsListeners = new ArrayList<>();

            if (onLoadContactsListener != null) {
                mOnLoadContactsListeners.add(onLoadContactsListener);
            }
        }

        void addLoadContactsListener(OnLoadContactsListener onLoadContactsListener) {
            if ((onLoadContactsListener != null) && !mOnLoadContactsListeners.contains(onLoadContactsListener)) {
                mOnLoadContactsListeners.add(onLoadContactsListener);
            }
        }

        void removeLoadContactsListener(OnLoadContactsListener onLoadContactsListener) {
            if (onLoadContactsListener != null) {
                mOnLoadContactsListeners.remove(onLoadContactsListener);
            }
        }

        @Override
        public void onStateChanged(ConcurrentTask task,
                                   int state) {
            LogUtil.i(TAG, "loadContacts finished State:" + state);
            synchronized (ContactController.this) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    mSyncContactsTask = null;

                    // neue SIMSme Kontakte
                    if (task != null && task.getResults() != null && task.getResults().length != 0) {
                        final List<String> newSIMSmeContactGuids = (List<String>) task.getResults()[0];
                        if (newSIMSmeContactGuids != null && newSIMSmeContactGuids.size() > 0) {
                            String[] contactGuids = new String[newSIMSmeContactGuids.size()];
                            contactGuids = newSIMSmeContactGuids.toArray(contactGuids);
                            List<Contact> newSIMSmeContacts = getContactsByGuid(contactGuids);

                            try {
                                PrivateInternalMessageController privateInternalMessageController = mApplication.getPrivateInternalMessageController();
                                AccountController accountController = mApplication.getAccountController();
                                privateInternalMessageController.broadcastProfileNameChange(newSIMSmeContacts, accountController.getAccount().getName());

                                updatePrivateIndexEntriesAsync();
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                            }
                        }
                    }

                    //Namenscache leeren
                    mApplication.getSingleChatController().clearCache();
                    mApplication.getChatOverviewController().chatChanged(null, null, null, ChatOverviewController.CHAT_CHANGED_TITLE);

                    for (int i = 0; i < mOnLoadContactsListeners.size(); i++) {
                        LogUtil.i(TAG, "loadContacts call:" + mOnLoadContactsListeners.get(i).getClass().getName());
                        mOnLoadContactsListeners.get(i).onLoadContactsComplete();
                    }
                } else if (state == ConcurrentTask.STATE_CANCELED) {
                    mSyncContactsTask = null;
                    for (int i = 0; i < mOnLoadContactsListeners.size(); i++) {
                        LogUtil.i(TAG, "loadContacts call:" + mOnLoadContactsListeners.get(i).getClass().getName());
                        mOnLoadContactsListeners.get(i).onLoadContactsCanceled();
                    }
                } else if (state == ConcurrentTask.STATE_ERROR) {
                    mSyncContactsTask = null;

                    String errorMessage = (String) task.getResults()[0];

                    for (int i = 0; i < mOnLoadContactsListeners.size(); i++) {
                        LogUtil.i(TAG, "loadContacts call:" + mOnLoadContactsListeners.get(i).getClass().getName());
                        mOnLoadContactsListeners.get(i).onLoadContactsError(errorMessage);
                    }
                }
            }
        }
    }

    private class LoadPrivateIndexTask extends AsyncTask<Void, Void, Boolean> {
        private final ContactDao mContactDao;
        private final GenericActionListener<ArrayMap<String, String>> mActionListener;
        private final SimsMeApplication mApplication;
        private final ArrayMap<String, String> mEntryGuidsWithChecksum;
        private ArrayMap<String, String> mEntryGuidsWithChecksumLoaded;
        private List<String> mEntriesForDeletion;
        private final String mOwnAccountGuid;
        private boolean mUpdateOwnAccountEntryOnServer;
        private GenericUpdateListener<Void> mMigrationListener;

        private boolean mFixContactsOnServer = false;

        LoadPrivateIndexTask(@NonNull final ContactDao contactDao,
                             @NonNull SimsMeApplication application,
                             @Nullable ArrayMap<String, String> entryGuidsWithChecksum,
                             @NonNull final GenericActionListener<ArrayMap<String, String>> actionListener) {
            mContactDao = contactDao;
            mActionListener = actionListener;
            mApplication = application;
            mEntryGuidsWithChecksum = entryGuidsWithChecksum;
            mOwnAccountGuid = application.getAccountController().getAccount().getAccountGuid();
        }

        void setFixContactsOnServer(final boolean value) {
            mFixContactsOnServer = value;
        }

        void setMigrationListener(final GenericUpdateListener<Void> listener) {
            mMigrationListener = listener;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                String now = null;
                boolean hasToUploadPrivateIndex = false;
                List<Contact> localContacts = getLocalContacts();
                ResponseModel rm = null;

                if (mEntryGuidsWithChecksum != null) {
                    mEntryGuidsWithChecksumLoaded = new ArrayMap<>(mEntryGuidsWithChecksum.size());
                    List<String> guidsToLoad = new ArrayList<>(mEntryGuidsWithChecksum.size());
                    for (int i = 0; i < mEntryGuidsWithChecksum.size(); i++) {
                        String guid = mEntryGuidsWithChecksum.keyAt(i);
                        if (StringUtil.isNullOrEmpty(guid)) {
                            continue;
                        }

                        if (localContacts == null) {
                            guidsToLoad.add(guid);
                        } else {
                            boolean foundContact = false;
                            for (Contact contact : localContacts) {
                                String privateIndexGuid = contact.getPrivateIndexGuid();
                                if (StringUtil.isNullOrEmpty(privateIndexGuid) ||
                                        !StringUtil.isEqual(guid, privateIndexGuid)) {
                                    continue;
                                }
                                foundContact = true;
                                String localChecksum = contact.getChecksum();

                                if (StringUtil.isNullOrEmpty(localChecksum)) {
                                    hasToUploadPrivateIndex = true;
                                }

                                String serverChecksum = mEntryGuidsWithChecksum.valueAt(i);

                                if (!StringUtil.isEqual(serverChecksum, localChecksum)) {
                                    guidsToLoad.add(guid);
                                } else {
                                    mEntryGuidsWithChecksumLoaded.put(guid, serverChecksum);
                                }
                                break;
                            }

                            if (!foundContact) {
                                guidsToLoad.add(guid);
                            }
                        }
                    }

                    if (!guidsToLoad.isEmpty()) {
                        rm = getPrivateIndexEntriesFromServer(guidsToLoad);
                    }
                } else {
                    now = DateUtil.getCurrentDate();

                    rm = loadPrivateIndexFromServer();
                }

                if (rm == null) {
                    return false;
                }

                if (rm.isError) {
                    return true;
                }

                if (!rm.response.isJsonArray()) {
                    return true;
                }

                PublicKey pubKey = mApplication.getKeyController().getUserKeyPair().getPublic();
                PrivateKey privKey = mApplication.getKeyController().getUserKeyPair().getPrivate();

                JsonArray ja = rm.response.getAsJsonArray();
                List<String> imported = new ArrayList<>(ja.size());
                List<String> guidForFtsDbUpdate = new ArrayList<>(ja.size());

                for (JsonElement je : ja) {
                    try {
                        JsonObject jo = JsonUtil.searchJsonObjectRecursive(je, "PrivateIndexEntry");

                        if (jo == null) {
                            continue;
                        }

                        String guid = JsonUtil.stringFromJO(JsonConstants.GUID, jo);
                        String checksum = JsonUtil.stringFromJO(JsonConstants.DATA_CHECKSUM, jo);
                        String data = JsonUtil.stringFromJO(JsonConstants.DATA, jo);
                        String keyData = JsonUtil.stringFromJO(JsonConstants.KEY_DATA, jo);
                        String keyIv = JsonUtil.stringFromJO(JsonConstants.KEY_IV, jo);
                        String signature = JsonUtil.stringFromJO(JsonConstants.SIGNATURE, jo);
                        String dateDeleted = JsonUtil.stringFromJO(JsonConstants.DATE_DELETED, jo);
                        Long dateModified = valueOf(JsonUtil.stringFromJO(JsonConstants.DATE_MODIFIED, jo));

                        if (!StringUtil.isNullOrEmpty(dateDeleted) || StringUtil.isNullOrEmpty(keyIv)
                                || StringUtil.isNullOrEmpty(signature) || StringUtil.isNullOrEmpty(data)) {
                            addLoadedGuid(guid);
                            continue;
                        }

                        if (!checkSignature(pubKey, data, signature)) {
                            addLoadedGuid(guid);
                            continue;
                        }

                        byte[] keyDataBytes = Base64.decode(keyData, Base64.DEFAULT);
                        byte[] decryptedBase64Bytes = SecurityUtil.decryptMessageWithRSA(keyDataBytes, privKey);
                        byte[] decryptedBytes = Base64.decode(decryptedBase64Bytes, Base64.DEFAULT);
                        SecretKey key = SecurityUtil.generateAESKey(decryptedBytes);

                        byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);
                        byte[] ivBytes = Base64.decode(keyIv, Base64.DEFAULT);
                        byte[] decryptedDataBytes = SecurityUtil.decryptMessageWithAES(dataBytes, key, new IvParameterSpec(ivBytes));

                        String decryptedData = new String(decryptedDataBytes, Encoding.UTF8);

                        JsonObject entryJO = JsonUtil.getJsonObjectFromString(decryptedData);

                        if (entryJO == null) {
                            addLoadedGuid(guid);
                            continue;
                        }

                        String accountGuid = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_GUID, entryJO);
                        String accountID = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, entryJO);

                        if (StringUtil.isNullOrEmpty(accountGuid)) {
                            // WorkAround OwnAccountEntry ohne AccountGuid
                            if (StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, JsonUtil.stringFromJO(JsonConstants.CLASS, entryJO))) {
                                accountGuid = mApplication.getAccountController().getAccount().getAccountGuid();
                                entryJO.addProperty(JsonConstants.ACCOUNT_GUID, accountGuid);
                            } else {
                                addLoadedGuid(guid);
                                continue;
                            }
                        }

                        if (imported.indexOf(accountGuid) > -1) {
                            //Pruefen ob wir mehr als ein OwnAccountEntry haben
                            if (StringUtil.isEqual(mOwnAccountGuid, accountGuid)
                                    && StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, JsonUtil.stringFromJO(JsonConstants.CLASS, entryJO))) {
                                //muss gelöscht werden und eigener Kontakt muss am Server aktualisiert werden
                                mUpdateOwnAccountEntryOnServer = true;
                            }
                        }

                        Contact localContact = getContactByGuid(accountGuid); //localContactsMap != null ? localContactsMap.get(accountGuid) : null;

                        if (localContact == null) {
                            localContact = new Contact();
                            localContact.setAccountGuid(accountGuid);
                            if (localContact.importPrivateIndexEntryData(guid, entryJO)) {
                                guidForFtsDbUpdate.add(accountGuid);
                            }

                            if (StringUtil.isEqual(accountGuid, AppConstants.GUID_SYSTEM_CHAT)) {
                                localContact.setIsHidden(true);
                            }
                            localContact.setTimestamp(dateModified);
                        } else {
                            // KS: Fix problem with npe
                            if (localContact.getTimestamp() == null) {
                                localContact.setTimestamp(dateModified);
                            }
                            // If the dateModified field is there then we check that our local time stamp is smaller before we delete and overwrite.
                            // It is possible that the dateModified is NOT set by an older client or other platform clients
                            if (!StringUtil.isNullOrEmpty(localContact.getChecksum()) &&
                                    (dateModified == null || localContact.getTimestamp() < dateModified)) {
                                //Sicherheitsueberpruefung ob es noch weitere Kontakte mit der eigenen Account Guid gibt
                                if (StringUtil.isEqual(mOwnAccountGuid, accountGuid) && !StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, JsonUtil.stringFromJO(JsonConstants.CLASS, entryJO))) {
                                    addGuidForDeletion(guid);
                                    continue;
                                }

                                String oldEntryGuid = localContact.getPrivateIndexGuid();
                                // Zwei unterschiedliche PrivateIndexGuids
                                if (!StringUtil.isNullOrEmpty(oldEntryGuid) && !StringUtil.isEqual(guid, oldEntryGuid)) {
                                    //alten Eintrag loeschen
                                    addGuidForDeletion(oldEntryGuid);
                                }

                                if (localContact.importPrivateIndexEntryData(guid, entryJO)) {
                                    guidForFtsDbUpdate.add(accountGuid);
                                }
                            }
                        }

                        localContact.setChecksum(checksum);

                        boolean hasProblems = checkForContactProblems(localContact, entryJO);
                        if (hasProblems && !hasToUploadPrivateIndex) {
                            hasToUploadPrivateIndex = true;
                        }

                        boolean updatePicuture = false;

                        String imgData = JsonUtil.stringFromJO(JsonConstants.IMAGE, entryJO);
                        if (!StringUtil.isNullOrEmpty(imgData)) {
                            byte[] imgBytes = Base64.decode(imgData, Base64.DEFAULT);
                            if (imgBytes != null) {
                                mApplication.getChatImageController().saveImage(accountGuid, imgBytes);
                                updatePicuture = true;
                            }
                        }

                        Chat chat = mApplication.getSingleChatController().getChatByGuid(localContact.getAccountGuid());
                        if (chat != null) {
                            if (chat.getType() != null && chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION) {
                                if (localContact.getState() != null && localContact.getState() > Contact.STATE_LOW_TRUST) {
                                    chat.setType(Chat.TYPE_SINGLE_CHAT);
                                    mApplication.getSingleChatController().insertOrUpdateChat(chat);
                                    mApplication.getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                                }
                            }
                            if (updatePicuture) {
                                mApplication.getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_IMAGE);
                            }
                        }

                        imported.add(accountGuid);

                        insertOrUpdateContact(localContact);

                        // TODO : Refresh UI with updated contact info

                        addLoadedGuid(guid);
                    } catch (UnsupportedEncodingException | LocalizedException e) {
                        LogUtil.w(TAG, "Import Private Index Entry", e);
                    }
                }

                //neue und geänderte Kontakte in die Fts DB
                if (RuntimeConfig.isBAMandant() && guidForFtsDbUpdate.size() > 0) {
                    List<Contact> contactsForFts = getContactsByGuid(guidForFtsDbUpdate.toArray(new String[]{}));
                    for (Contact contact : contactsForFts) {
                        saveContactToFtsDatabase(contact, false);
                    }
                }

                if (now != null) {
                    mApplication.getPreferencesController().setLastPrivateIndexSyncTimeStamp(now);
                }

                if (hasToUploadPrivateIndex) {
                    updatePrivateIndex(mApplication, mContactDao, mMigrationListener);
                }

                deleteEntriesOnServer();
            } catch (LocalizedException e) {
                LogUtil.w(TAG, "Load Private Index", e);
                return true;
            }
            return false;
        }

        // Given a string of a date time format, convert to unix epoch timestamp
        // unfortunately the dateformat in the backend is "yyyy-MM-dd HH:mm:ssZ" for PrivateIndexEntry
        // and "yyyy-MM-dd HH:mm:ss.SSSZ" for CompanyIndexEntry. We have to handle both. I have filed a bug against the BE
        // to track this, we should fix it in the near future. (https://collab.hq.brabbler.ag/jira/browse/SIBA-163)
        private Long valueOf(String dateString) {
            try {
                // try the private index entry first
                final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
                return sdf.parse(dateString).getTime();
            } catch (ParseException e) {
                try {
                    // try the business format otherwise
                    // KS: Fix conversion string - seconds are missing
                    // final SimpleDateFormat sdfBusiness = new SimpleDateFormat("yyyy-MM-dd HH:mm:SSSZ");
                    final SimpleDateFormat sdfBusiness = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
                    return sdfBusiness.parse(dateString).getTime();
                } catch (ParseException e2) {
                    LogUtil.e(TAG, "Failed to convert to Long:" + dateString);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean isError) {
            if (isError == null || !isError) {
                if (mUpdateOwnAccountEntryOnServer) {
                    Contact ownConact = mApplication.getContactController().getOwnContact();
                    if (ownConact != null) {
                        try {
                            mApplication.getContactController().saveContactInformation(ownConact,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    -1,
                                    true);
                        } catch (LocalizedException e) {
                            LogUtil.w("Load Private Index", "save own Contact failed");
                        }
                    }
                }

                mActionListener.onSuccess(mEntryGuidsWithChecksumLoaded);
            } else {
                mActionListener.onFail(null, null);
            }
        }

        private boolean checkForContactProblems(@NonNull Contact localContact, JsonObject entryJO)
                throws LocalizedException {
            boolean resetChecksum = false;

            if (localContact.isDeletedHidden()) {
                return false;
            }

            //SIMSme Id prüfen
            String accountId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, entryJO);
            if (StringUtil.isNullOrEmpty(accountId) && !StringUtil.isNullOrEmpty(localContact.getSimsmeId())) {
                resetChecksum = true;
            }

            //Class Entry prüfen
            String className = JsonUtil.stringFromJO(JsonConstants.CLASS, entryJO);
            if (StringUtil.isNullOrEmpty(className) && !StringUtil.isNullOrEmpty(localContact.getClassEntryName())) {
                resetChecksum = true;
            } else if (StringUtil.isEqual(Contact.CLASS_COMPANY_ENTRY, className)) {
                if (mFixContactsOnServer) {
                    CompanyContact cc = getCompanyContactWithAccountGuid(localContact.getAccountGuid());

                    if (cc == null && !localContact.getIsHidden()) {
                        localContact.setClassEntryName(Contact.CLASS_PRIVATE_ENTRY);
                        resetChecksum = true;
                    }
                }
            }

            if (resetChecksum) {
                localContact.setChecksum("");
            }

            return resetChecksum;
        }

        private void addLoadedGuid(String guid) {
            if (mEntryGuidsWithChecksum != null) {
                String cs = mEntryGuidsWithChecksum.get(guid);
                if (!StringUtil.isNullOrEmpty(cs)) {
                    mEntryGuidsWithChecksumLoaded.put(guid, cs);
                }
            }
        }

        private void addGuidForDeletion(String guid) {
            if (mEntriesForDeletion == null) {
                mEntriesForDeletion = new ArrayList<>();
            }

            mEntriesForDeletion.add(guid);
        }

        private List<Contact> getLocalContacts() {
            List<Contact> contacts = null;

            QueryBuilder<Contact> queryBuilder = mContactDao.queryBuilder();

            contacts = queryBuilder.build().forCurrentThread().list();

            return contacts;
        }

        private ResponseModel loadPrivateIndexFromServer()
                throws LocalizedException {
            final ResponseModel rm = new ResponseModel();
            String lastSyncTimeStamp = mApplication.getPreferencesController().getLastPrivateIndexSyncTimeStamp();

            if (mFixContactsOnServer) {
                lastSyncTimeStamp = null;
            }

            BackendService.withSyncConnection(mApplication)
                    .listPrivateIndexEntries(lastSyncTimeStamp, new IBackendService.OnBackendResponseListener() {
                        @Override
                        public void onBackendResponse(BackendResponse response) {
                            if (response.isError) {
                                rm.setError(response);
                            }

                            rm.response = response.jsonArray != null ? response.jsonArray : response.jsonObject;
                        }
                    });

            return rm;
        }

        private ResponseModel getPrivateIndexEntriesFromServer(@NonNull List<String> guidList)
                throws LocalizedException {
            final ResponseModel rm = new ResponseModel();
            String guids = StringUtil.getStringFromList(",", guidList);

            BackendService.withSyncConnection(mApplication)
                    .getPrivateIndexEntries(guids, new IBackendService.OnBackendResponseListener() {
                        @Override
                        public void onBackendResponse(BackendResponse response) {
                            if (response.isError) {
                                rm.setError(response);
                            }

                            rm.response = response.jsonArray != null ? response.jsonArray : response.jsonObject;
                        }
                    });

            return rm;
        }

        private void deleteEntriesOnServer()
                throws LocalizedException {
            if (mEntriesForDeletion == null || mEntriesForDeletion.size() < 1) {
                return;
            }

            String guids = StringUtil.getStringFromList(",", mEntriesForDeletion);

            BackendService.withSyncConnection(mApplication)
                    .deletePrivateIndexEntries(guids, new IBackendService.OnBackendResponseListener() {
                        @Override
                        public void onBackendResponse(BackendResponse response) {
                            if (response.isError) {
                                LogUtil.w("Load Private Index", "Delete Entries failed");
                            }
                        }
                    });
        }

        boolean checkSignature(@NonNull final PublicKey key, @NonNull final String base64Verify, @NonNull final String base64Signature) {
            try {
                byte[] verifyBytes = Base64.decode(base64Verify, Base64.DEFAULT);
                byte[] sigBytes = Base64.decode(base64Signature, Base64.DEFAULT);

                return SecurityUtil.verifyData(key, sigBytes, verifyBytes, true);
            } catch (LocalizedException e) {
                LogUtil.w(TAG, "checkSignature()", e);
                return false;
            }
        }
    }

    public void getOnlineState(final String contactGuid,
                               final String lastOnlineState,
                               final GenericActionListener<OnlineStateContainer> genericActionListener,
                               final boolean firstStart) {
        if (firstStart) {
            mOnlineStateGuid = contactGuid;
        }

        if (StringUtil.isNullOrEmpty(mOnlineStateGuid)) {
            return;
        }

        if (mGetOnlineStateTask != null) {
            if (StringUtil.isEqual(mOnlineStateGuid, mGetOnlineStateTask.getContactGuid())) {
                if (firstStart && !mGetOnlineStateTask.getIsCancel()) {
                    mGetOnlineStateTask.setGenericActionListener(genericActionListener);
                    return;
                }
            } else {
                mGetOnlineStateTask.setIsCancel();
            }
        }

        mGetOnlineStateTask = new OnlineStateTask(mApplication, lastOnlineState, contactGuid,
                genericActionListener);
        mGetOnlineStateTask.executeOnExecutor(ONLINE_SERIAL_EXECUTOR, null,
                null, null);
    }

    public void stopGetOnlineStateTask() {
        // guid zuruecksetzen, task ruft sich nicht mehr erneut auf
        mOnlineStateGuid = null;
        if (mGetOnlineStateTask != null) {
            mGetOnlineStateTask.setIsCancel();
        }
    }

    /**
     * Wird für die PushNotification verwendet, da die Methoden keine Exception werfen dürfen
     * Prüfung ob man mit den Kontakt schon einmal geschrieben hat (wird von Business überschrieben)
     *
     * @param contactGuid
     * @return
     */
    public boolean isFirstContact(final String contactGuid) {
        ArrayList<Contact> contacts = mApplication.getContactController().getContactsByGuid(new String[]{contactGuid});

        if (contacts == null || contacts.size() < 1) {
            return true;
        }

        Contact contact = contacts.get(0);

        return contact.getIsFirstContact();
    }

    public void addLastUsedCompanyContact(@NonNull final Contact contact) {
        try {
            String classEntry = contact.getClassEntryName();
            if (StringUtil.isNullOrEmpty(classEntry) || StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, classEntry)
                    || StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, classEntry)) {
                return;
            }

            mApplication.getPreferencesController().addLastUsedContactGuid(contact.getAccountGuid(),
                    StringUtil.isEqual(Contact.CLASS_COMPANY_ENTRY, contact.getClassEntryName())
                            ? IndexType.INDEX_TYPE_COMPANY : IndexType.INDEX_TYPE_DOMAIN);
        } catch (LocalizedException e) {
            LogUtil.w("ContactController", "addLastUsedCompanyContact", e);
        }
    }

    public static class OnlineStateContainer {
        public final String lastOnline;
        public final String state;

        OnlineStateContainer(final String aLastOnline,
                             final String aState
        ) {
            lastOnline = aLastOnline;
            state = aState;
        }
    }

    static class OnlineStateTask
            extends AsyncTask<Void, Void, Void> {
        private String mLastOnlineState;

        private final String mContactGuid;

        private GenericActionListener<OnlineStateContainer> mGenericActionListener;

        private final SimsMeApplication mApp;

        private String mErrorText;

        private OnlineStateContainer mResult;

        private boolean mIsCancel;

        OnlineStateTask(final SimsMeApplication application,
                        final String lastOnlineState,
                        final String contactGuid,
                        final GenericActionListener<OnlineStateContainer> genericActionListener) {
            mLastOnlineState = lastOnlineState;
            mContactGuid = contactGuid;
            mGenericActionListener = genericActionListener;
            mApp = application;
        }

        private String getContactGuid() {
            return mContactGuid;
        }

        private void setIsCancel() {
            mIsCancel = true;
            mGenericActionListener = null;
            mApp.getContactController().mGetOnlineStateTask = null;
        }

        private boolean getIsCancel() {
            return mIsCancel;
        }

        private void setGenericActionListener(final GenericActionListener<OnlineStateContainer> genericActionListener) {
            mGenericActionListener = genericActionListener;
        }

        private void asyncLoaderFinished(final OnlineStateContainer result) {
            if (mIsCancel) {
                return;
            }

            // task wurde ausgefuert -> null
            mApp.getContactController().mGetOnlineStateTask = null;
            mApp.getContactController().getOnlineState(mContactGuid, mLastOnlineState, mGenericActionListener, false);

            if (mGenericActionListener != null) {
                mGenericActionListener.onSuccess(result);
            }
        }

        private void asyncLoaderFailed(final String errorMessage) {
            if (mIsCancel) {
                return;
            }

            new CountDownTimer(10000, 1000) {

                public void onTick(final long millisUntilFinished) {

                }

                public void onFinish() {
                    // task wurde abgebrochen -> null -> erst hier, damit in den 10s kein zweiter gestartet wird
                    if (!mIsCancel) {
                        mApp.getContactController().mGetOnlineStateTask = null;
                        mApp.getContactController().getOnlineState(mContactGuid, mLastOnlineState, mGenericActionListener, false);
                    }
                }
            }.start();

            if (mGenericActionListener != null) {
                mGenericActionListener.onFail(errorMessage, null);
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (mIsCancel) {
                return null;
            }

            mErrorText = null;

            final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                        mErrorText = response.errorMessage;
                    } else {
                        try {
                            if (response.jsonArray != null && response.jsonArray.size() > 0) {
                                final JsonObject jsonElement = response.jsonArray.get(0).getAsJsonObject();

                                final JsonObject oooStatus = jsonElement.get("oooStatus").getAsJsonObject();
                                final JsonElement serverTime = jsonElement.get("serverTime");
                                final JsonElement lastOnline = jsonElement.get("lastOnline");
                                final JsonElement state = jsonElement.get("state");

                                final String serverTimeString = serverTime != null ? serverTime.getAsString() : null;
                                final String lastOnlineString = lastOnline != null ? lastOnline.getAsString() : null;
                                final String stateString;

                                if (oooStatus != null
                                        && !oooStatus.isJsonNull()
                                        && oooStatus.has(JsonConstants.OOO_STATUS_STATE)
                                        && JsonConstants.OOO_STATUS_STATE_OOO.equals(oooStatus.get(JsonConstants.OOO_STATUS_STATE).getAsString())) {
                                    stateString = ONLINE_STATE_ABSENT;
                                } else {
                                    stateString = state != null ? state.getAsString() : null;
                                }

                                mLastOnlineState = stateString;

                                mResult = new OnlineStateContainer(
                                        lastOnlineString,
                                        stateString);
                            }
                        } catch (Exception e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                            mErrorText = e.getMessage();
                        }
                    }
                }
            };

            BackendService.withSyncConnection(mApp)
                    .getOnlineState(mContactGuid, mLastOnlineState, listener);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (mErrorText != null) {
                asyncLoaderFailed(mErrorText);
            } else {
                asyncLoaderFinished(mResult);
            }
        }
    }

    private static class UpdatePrivateIndexTask extends AsyncTask<Void, Void, Void> {
        private final ContactDao mContactDao;
        private final GenericActionListener<Void> mActionListener;
        private final SimsMeApplication mApplication;
        private LocalizedException mException;

        UpdatePrivateIndexTask(@NonNull final ContactDao contactDao, @NonNull final SimsMeApplication application, @NonNull final GenericActionListener<Void> actionListener) {
            mContactDao = contactDao;
            mActionListener = actionListener;
            mApplication = application;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {

                mApplication.getContactController().updatePrivateIndex(mApplication, mContactDao, null);
            } catch (LocalizedException e) {
                LogUtil.w(TAG, "UpdatePrivateIndex failed", e);
                mException = e;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mException != null) {
                mActionListener.onFail("", mException.getIdentifier());
            } else {
                mActionListener.onSuccess(null);
            }
        }
    }

    public interface GetAddressInformationsListener {
        void onSuccess();

        void onFail();
    }
}
