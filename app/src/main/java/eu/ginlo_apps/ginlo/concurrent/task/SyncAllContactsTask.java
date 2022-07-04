// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.task;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.greenrobot.greendao.query.QueryBuilder;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.crypto.SecretKey;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ImageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.ContactDao;
import eu.ginlo_apps.ginlo.greendao.ContactDao.Properties;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.CompanyContactUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class SyncAllContactsTask extends ConcurrentTask {
    private static final String TAG = SyncAllContactsTask.class.getSimpleName();

    private static final String BCRYPT_MAP_FILE = "c_hash_map";

    private static final String[] PROJECTION_ = {ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.MIMETYPE};

    private static final String SELECTION_ =
            "((" + ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "') OR (" +
                    ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'))";

    private static final String SELECTION_REFRESH =
            ContactsContract.Data.CONTACT_ID + " = ? AND ((" +
                    ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "') OR (" +
                    ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "') OR (" +
                    ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'))";

    private static final String[] PROJECTION_REFRESH = {
            ContactsContract.Data.DATA1, //0
            ContactsContract.Data.DATA2, //1
            ContactsContract.Data.DATA3, //2
            ContactsContract.Data.DATA4, //3
            ContactsContract.Data.DATA5, //4
            ContactsContract.Data.DATA6, //5
            ContactsContract.Data.MIMETYPE, //6
            ContactsContract.Data.PHOTO_URI}; //7

    private static final int ID_COLUMN_INDEX = 0;

    private static final int PHOTO_THUMBNAIL_URI = 7;

    private static final int DATA2_GIVEN_NAME = 1;
    private static final int DATA3_FAMILY_NAME = 2;
    private static final int DATA4_NORMALIZED_PHONE_PREFIX_NAME = 3;
    private static final int DATA5_MIDDLE_NAME = 4;
    private static final int DATA6_SUFFIX_NAME = 5;
    private static final int MIMETYPE = 6;

    private final SimsMeApplication mContext;

    private final ContactDao mContactDao;

    private final ContactController mContactController;

    private final ImageController mImageController;
    private final ArrayMap<String, String> mServerSalts;
    private final String mCountryCode;
    private final String mOwnAccountGuid;
    private final List<String> mNewSimsmeContacts = new ArrayList<>();
    private final boolean mMergeOldContacts;
    private final FileUtil mFileUtil;
    private final ArrayMap<String, ContactInfo> mBcryptContactInfoMap;
    private final boolean mHasPhonebookePermission;
    private final List<String> mFtsRefreshGuids;
    private String mErrorString;
    private Map<String, String> mBcryptMap;
    private long mTimestamp;
    private boolean mHasCompanyContacts;
    private boolean mTenantsOnly = false;

    public SyncAllContactsTask(final SimsMeApplication context,
                               boolean mergeOldContacts, final boolean hasPhonebookPermission,
                               boolean syncTenantsOnly) {
        super();

        mContext = context;
        mContactController = mContext.getContactController();
        mImageController = mContext.getImageController();

        mMergeOldContacts = mergeOldContacts;
        mTenantsOnly = syncTenantsOnly;
        mHasPhonebookePermission = hasPhonebookPermission;

        mContactDao = mContactController.getDao();

        mCountryCode = mContext.getAccountController().getAccount().getCountryCode();
        mOwnAccountGuid = mContext.getAccountController().getAccount().getAccountGuid();

        mServerSalts = new ArrayMap<>();

        mBcryptContactInfoMap = new ArrayMap<>();
        mFileUtil = new FileUtil(context);
        mFtsRefreshGuids = new ArrayList<>();

    }

    public SyncAllContactsTask(final SimsMeApplication context,
                               boolean mergeOldContacts, final boolean hasPhonebookPermission) {
        this(context, mergeOldContacts, hasPhonebookPermission, false);
    }

    /**
     * Gibt im Fehlerfall ein Errostring zurück der aber auch Null sein kann. Im
     * Erfolgsfall wird eine Map zurück gegeben, die von allen neuen SimsMe
     * Kontakte die Accountguid als Key und
     */
    @Override
    public Object[] getResults() {
        if (this.getState() == STATE_ERROR) {
            return new Object[]{mErrorString};
        } else if (this.getState() == STATE_COMPLETE) {
            return new Object[]{mNewSimsmeContacts};
        }
        return null;
    }

    @Override
    public void run() {
        super.run();
        try {
            mTimestamp = new Date().getTime();

            if (mContactController.getOwnContact() == null) {
                mContactController.fillOwnContactWithAccountInfos(null);
            }

            mHasCompanyContacts = mContext.getAccountController().hasEmailOrCompanyContacts();

            // Prüfen ob inet connection besteht
            if (!BackendService.withSyncConnection(mContext).isConnected()) {
                // abbrechen
                mErrorString = mContext.getString(R.string.backendservice_internet_connectionFailed);
                error();
            }

            if (mMergeOldContacts) {
                if (this.isCanceled()) {
                    return;
                }

                mergeOldContacts();
            } else {
                Contact ownContact = mContactController.getOwnContact();
                if (ownContact == null || !StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, ownContact.getClassEntryName())) {
                    mContactController.fillOwnContactWithAccountInfos(null);
                }
            }

            if (this.isCanceled()) {
                return;
            }

            synchronizeMandantsWithServer();

            if (this.isCanceled() || mTenantsOnly) {
                return;
            }

            saveBcryptMap();

            //neue und geänderte Kontakte in die Fts DB
            if (RuntimeConfig.isBAMandant() && mFtsRefreshGuids.size() > 0) {
                List<Contact> contactsForFts = mContactController.getContactsByGuid(mFtsRefreshGuids.toArray(new String[]{}));
                for (Contact contact : contactsForFts) {
                    mContactController.saveContactToFtsDatabase(contact, false);
                }
            }

            LogUtil.i(TAG, "SyncAllContactsTask complete!");
            complete();
        } catch (Exception e) {
            LogUtil.e(TAG, e.getMessage(), e);
            error();
        }
    }

    private void mergeOldContacts()
            throws LocalizedException {
        boolean isDeviceManaged = mContext.getAccountController().isDeviceManaged();
        List<Contact> allContacts = mContactDao.loadAll();
        List<String> contactGuidsWithoutID = new ArrayList<>();
        boolean foundContactForOwnAccount = false;

        for (Contact contact : allContacts) {
            if (StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
                mContactDao.delete(contact);
                continue;
            }

            // if the contact has been deleted and there is no chat with the contact then we delete
            if (!contact.getIsSimsMeContactSave() && contact.isDeletedHidden()) {
                if (mContext.getSingleChatController().getChatByGuid(contact.getAccountGuid()) == null) {
                    mContactDao.delete(contact);
                    continue;
                }
            }

            // Check to see if this is my own account. If it is then fill it with info
            if (StringUtil.isEqual(contact.getAccountGuid(), mOwnAccountGuid)) {
                mContactController.fillOwnContactWithAccountInfos(contact);
                foundContactForOwnAccount = true;
            }

            // IF the contact doesn't have a class then we need to test to find out
            // if this contact should be a private, or company class.
            if (StringUtil.isNullOrEmpty(contact.getClassEntryName())) {
                String classType = Contact.CLASS_PRIVATE_ENTRY;

                // CompanyContact can be either COMPANY_ENTRY or DOMAIN_ENTRY
                if (mHasCompanyContacts) {
                    CompanyContact companyContact = mContactController.getCompanyContactWithAccountGuid(contact.getAccountGuid());
                    if (companyContact != null) {
                        if (isDeviceManaged && (StringUtil.isNullOrEmpty(companyContact.getClassType()) || StringUtil.isEqual(companyContact.getClassType(), Contact.CLASS_DOMAIN_ENTRY))) {
                            //wenn Gerät gemanaged wird, werden die Daten des Mail Verzeichnis Kontakts übernommen da dieser im Anschluss gelöscht wird
                            //da ab der 2.1 gemanagede Accounts das Firmenverzeichnis haben

                            boolean isHidden = mContext.getSingleChatController().getChatByGuid(contact.getAccountGuid()) == null;

                            contact.setIsHidden(isHidden);
                            mContext.getContactController().copyCompanyContactInfoToContact(companyContact, contact);
                        } else {
                            classType = companyContact.getClassType();
                            contact.setIsHidden(true);
                        }
                    }
                }
                contact.setClassEntryName(classType);
            }

            if (StringUtil.isNullOrEmpty(contact.getPrivateIndexGuid())) {
                contact.setPrivateIndexGuid(GuidUtil.generatePrivateIndexGuid());
            }

            //Verweise auf Telefonbuch entfernen
            contact.setHasNewerContact(false);
            contact.setLastKnownRowId((long) 0);
            contact.setLookupKey("");

            mContactDao.update(contact);

            if (StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                contactGuidsWithoutID.add(contact.getAccountGuid());
            }
        }

        if (!foundContactForOwnAccount) {
            Contact ownContact = new Contact();
            mContactController.fillOwnContactWithAccountInfos(ownContact);
        }

        if (!contactGuidsWithoutID.isEmpty()) {
            loadPublicKeys(contactGuidsWithoutID, true);
        }

        allContacts = mContactDao.loadAll();

        if (allContacts != null && allContacts.size() > 0) {
            for (int i = 0; i < allContacts.size(); i++) {
                Contact contact = allContacts.get(i);

                if (contact != null && contact.getId() != null) {
                    contact.setChecksum("");
                    mContactDao.update(contact);
                }
            }
        }

        //private index hochladen
        mContactController.updatePrivateIndexEntriesSync(null);

        //wenn gemanaged
        if (isDeviceManaged) {
            //E-Mail Verzeichnis löschen
            //mContactController.deleteAllCompanyContacts();

            // und Firmenverzeichnis laden
            mContactController.loadCompanyIndexSync(null, false);

            Contact ownContact = mContactController.getOwnContact();
            if (ownContact != null) {
                //am eigenen Kontakt die Domain loeschen
                mContactController.saveContactInformation(ownContact,
                        null,
                        null,
                        null,
                        null,
                        "",
                        null,
                        null,
                        null,
                        -1,
                        false);
            }
        }
    }

    private synchronized void synchronizeMandantsWithServer() throws LocalizedException {
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {

            @Override
            public void onBackendResponse(final BackendResponse response) {
                try {
                    if (response.isError) {
                        mErrorString = response.errorMessage;
                        error();
                    } else {
                        final JsonArray responseArray = response.jsonArray;
                        if (responseArray != null && responseArray.size() > 0) {
                            final int responseArraySize = responseArray.size();

                            for (int i = 0; i < responseArraySize; i++) {
                                if (SyncAllContactsTask.this.isCanceled()) {
                                    return;
                                }

                                final JsonObject json = responseArray.get(i).getAsJsonObject();

                                final JsonElement mandantJson = json.get("Mandant");
                                if (mandantJson != null) {
                                    final JsonElement identJson = mandantJson.getAsJsonObject().get("ident");
                                    final JsonElement saltJson = mandantJson.getAsJsonObject().get("salt");

                                    if(identJson != null && saltJson != null) {
                                        final String mandantName = identJson.getAsString();
                                        final String salt = saltJson.getAsString();

                                        if (!StringUtil.isNullOrEmpty(mandantName) && !StringUtil.isNullOrEmpty(salt)) {
                                            mServerSalts.put(mandantName, salt);
                                        }
                                    }
                                }
                            }

                            mContext.getPreferencesController().setMandantenJson(responseArray.toString());

                            if(!mTenantsOnly) {
                                syncTenantContacts();
                            }
                        }
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "synchronizeMandantsWithServer: e:" + e.getMessage(), e);
                    error();
                }
            }
        };

        LogUtil.i(TAG, "synchronizeMandantsWithServer start");

        List<Mandant> mandants = mContext.getPreferencesController().getMandantenList();
        if (mandants == null || mandants.size() < 1) {
            if (!this.isCanceled()) {
                BackendService.withSyncConnection(mContext)
                        .getTenants(listener);
            }
        } else {
            for (Mandant mandant : mandants) {
                if (!StringUtil.isNullOrEmpty(mandant.ident) && !StringUtil.isNullOrEmpty(mandant.salt)) {
                    mServerSalts.put(mandant.ident, mandant.salt);
                }
            }

            if(!mTenantsOnly) {
                syncTenantContacts();
            }
        }
    }

    private void syncTenantContacts()
            throws LocalizedException {
        if (this.isCanceled()) {
            return;
        }

        if (mServerSalts == null || mServerSalts.size() < 1) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "salts are null");
        }

        if (mHasPhonebookePermission) {
            syncAllPhoneContactsWithDB();

            //
            for (final String mandantName : mServerSalts.keySet()) {
                final String salt = mServerSalts.get(mandantName);

                if (StringUtil.isEqual(mandantName, BuildConfig.SIMSME_MANDANT_BA)) {
                    synchronizeSingleMandantWithServer(salt, mandantName, JsonConstants.SEARCH_TYPE_EMAIL);
                }
                synchronizeSingleMandantWithServer(salt, mandantName, JsonConstants.SEARCH_TYPE_PHONE);
            }
        }

        checkContact();
    }

    private synchronized void synchronizeSingleMandantWithServer(final String serverSalt,
                                                                 final String mandant,
                                                                 final String searchType) {
        //TODO bei BA MAILS berücksichtigen
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {

                if (response.isError) {
                    mErrorString = response.errorMessage;
                    error();
                } else {
                    final JsonArray responseArray = response.jsonArray;
                    checkContactsWithServerInfo(responseArray, mandant);
                }
            }
        };

        LogUtil.i(TAG, "synchronizeSingleMandantWithServer start " + mandant);

        final JsonArray hashData = getContactBCrypts(serverSalt, searchType);
        LogUtil.i(TAG, "synchronizeSingleMandantWithServer after crypt " + mandant);
        if (hashData != null && hashData.size() > 0 && !this.isCanceled()) {

            BackendService.withSyncConnection(mContext)
                    .getKnownAccounts(hashData, serverSalt, mandant, searchType, listener);
        }
    }

    private void checkContactsWithServerInfo(JsonArray responseArray, String mandant) {
        try {
            if (responseArray == null) {
                return;
            }

            final ArrayList<String> accountGuidsWithoutPK = new ArrayList<>();

            LogUtil.i(TAG, "checkContactsWithServerInfo got Response:" + responseArray.size());

            for (int i = 0; i < responseArray.size(); i++) {
                if (SyncAllContactsTask.this.isCanceled()) {
                    return;
                }

                final JsonObject jsonObject = responseArray.get(i).getAsJsonObject();

                final Set<Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
                if (entrySet == null) {
                    continue;
                }

                Iterator<Entry<String, JsonElement>> it = entrySet.iterator();
                if (!it.hasNext()) {
                    continue;
                }

                final Entry<String, JsonElement> mapEntry = it.next();

                final String bcryptHash = mapEntry.getKey();//.replace(serverSalt, "");
                final String accountGuid = mapEntry.getValue().getAsString();

                ContactInfo contactInfo = mBcryptContactInfoMap.get(bcryptHash);

                // Get the Contact by GUID from the database
                Contact contact = mContactController.getContactByGuid(accountGuid);

                //prüfen ob es dafür ein Company Contact gibt
                CompanyContact companyContact = null;
                if (mHasCompanyContacts) {
                    companyContact = mContactController.getCompanyContactWithAccountGuid(accountGuid);
                }

                if (contact == null) {
                    contact = new Contact();
                    contact.setAccountGuid(accountGuid);
                    contact.setIsFirstContact(false);
                    contact.setIsSimsMeContact(true);

                    if (companyContact != null) {
                        contact.setClassEntryName(companyContact.getClassType());
                        contact.setIsHidden(true);
                        contact.setState(Contact.STATE_HIGH_TRUST);
                    } else {
                        contact.setClassEntryName(Contact.CLASS_PRIVATE_ENTRY);
                        contact.setIsHidden(false);
                        contact.setState(Contact.STATE_LOW_TRUST);
                    }

                    mNewSimsmeContacts.add(accountGuid);
                } else {
                    if (StringUtil.isEqual(accountGuid, mOwnAccountGuid)) {
                        //Eigenen Kontakt nicht befüllen
                        continue;
                    }

                    if (contact.getIsFirstContact()) {
                        contact.setIsFirstContact(false);
                    }
                }

                if (contactInfo != null) {
                    boolean haveToRefreshFtsDB = false;
                    if (contactInfo.isPhone) {
                        if (!StringUtil.isEqual(contactInfo.value, contact.getPhoneNumber())) {
                            contact.setPhoneNumber(contactInfo.value);
                            haveToRefreshFtsDB = true;
                        }
                    } else {
                        if (!StringUtil.isEqual(contactInfo.value, contact.getEmail())) {
                            contact.setEmail(contactInfo.value);
                            haveToRefreshFtsDB = true;
                        }
                    }

                    //Kontaktinfos aktualisieren
                    if (contact.getTimestamp() == null || contact.getTimestamp() < mTimestamp) {
                        refreshContactInfosFromPhonebook(contact, contactInfo);

                        if (companyContact != null) {
                            try {
                                //werte aus Verzeichnis übernehmen
                                String firstName = CompanyContactUtil.getInstance(mContext).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
                                String lastName = CompanyContactUtil.getInstance(mContext).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_LASTNAME);
                                String department = CompanyContactUtil.getInstance(mContext).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_DEPARTMENT);

                                if (!StringUtil.isNullOrEmpty(firstName) && !StringUtil.isEqual(firstName, contact.getFirstName())) {
                                    contact.setFirstName(firstName);
                                    haveToRefreshFtsDB = true;
                                }
                                if (!StringUtil.isNullOrEmpty(lastName) && !StringUtil.isEqual(lastName, contact.getLastName())) {
                                    contact.setLastName(lastName);
                                    haveToRefreshFtsDB = true;
                                }

                                if (!StringUtil.isNullOrEmpty(department) && !StringUtil.isEqual(department, contact.getDepartment())) {
                                    contact.setDepartment(department);
                                    haveToRefreshFtsDB = true;
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, "Failed to read contact index values: e" + e.getMessage(), e);
                            }
                        }

                        //Da Kontakt im Telefonbuch -> nicht mehr hidden
                        if (contact.getIsHidden() && StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_PRIVATE_ENTRY)) {
                            contact.setIsHidden(false);
                        }

                        contact.setTimestamp(mTimestamp);
                    }

                    if (haveToRefreshFtsDB) {
                        addToFtsRefreshGuids(contact.getAccountGuid());
                    }
                }

                contact.setMandant(mandant);

                insertOrUpdateContact(contact);

                if (StringUtil.isNullOrEmpty(contact.getSimsmeId()) ||
                        StringUtil.isNullOrEmpty(contact.getPublicKey()) ||
                        (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey()) &&
                                StringUtil.isNullOrEmpty(contact.getEncryptedNickname()))) {
                    accountGuidsWithoutPK.add(contact.getAccountGuid());
                }
            }

            if (accountGuidsWithoutPK.size() > 0) {
                loadPublicKeys(accountGuidsWithoutPK, true);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to check contacts with server Info." + e.getMessage(), e);
            error();
        }
    }

    private void insertOrUpdateContact(final Contact contact) {
        synchronized (mContactDao) {
            if (contact.getId() == null) {
                mContactDao.insert(contact);
            } else {
                mContactDao.update(contact);
            }
        }
    }

    /**
     * Check the validity of all contacts in the database except the system account and self. If the
     * contact does NOT have a public key this means, it has been deleted in the BE, and we need to
     * mark it as deleted. Conversely, if the public key is there but the contact has been marked as
     * deletedHidden, then it was done in error. We correct it.
     *
     * @throws LocalizedException
     */
    private void checkContact() throws LocalizedException {

        List<Contact> contacts;
        // Alle prüfen die jetzt nicht angepasst wurden
        synchronized (mContactDao) {
            final QueryBuilder<Contact> queryBuilder = mContactDao.queryBuilder();
            contacts = queryBuilder.where(Properties.AccountGuid.notEq(mOwnAccountGuid),
                    Properties.AccountGuid.notEq(AppConstants.GUID_SYSTEM_CHAT)).build().forCurrentThread().list();
        }

        if ((contacts == null) || (contacts.size() < 1)) {
            return;
        }

        LogUtil.i(TAG, "Check Status of " + contacts.size() + " contacts.");

        final List<String> contactsList = new ArrayList<>();

        for (Contact contact : contacts) {
            String guid = contact.getAccountGuid();
            if (!StringUtil.isNullOrEmpty(guid)) {
                contactsList.add(contact.getAccountGuid());
            }
        }

        if (contactsList.size() > 0) {
            // knownServerGuids is never null, no need to check.
            List<String> knownServerGuids = loadPublicKeys(contactsList, false);

            for (String guid : contactsList) {
                //Wenn Kontakt nicht in der Public Key List drin ist, ist er dem Server nicht bekannt, also auf hiddenDelete setzen
                if ((knownServerGuids == null) || knownServerGuids.size() < 1 || !knownServerGuids.contains(guid)) {
                    hideDeletedContactLocally(guid);
                } else {
                    mContactController.makeContactVisibleLocally(guid);
                }
            }
        }
    }

    private void notifyChatControllerOfRemovedAccount(@NotNull Contact contact) {
        try {
            if (mContext.getSingleChatController().getChatByGuid(contact.getAccountGuid()) == null) {
                return;
            }

            final String name = contact.getName();
            if ((name == null) || contact.getPublicKey() == null) {
                return;
            }

            final String logMessage;
            //Prüfen ob es die Telefonnummer oder E-Mail-Adresse nochmal an einen anderen Account gibt
            if (hasNewerContact(contact.getPhoneNumber(), contact.getEmail())) {
                LogUtil.w(TAG, "notifyChatControllerOfRemovedAccount: hasNewerContact: " + contact.getAccountGuid());
                logMessage = mContext
                        .getString(R.string.chat_system_message_alertAccountRegistrationAgain2,
                                name);
            } else {
                LogUtil.w(TAG, "notifyChatControllerOfRemovedAccount: account has signed out: " + contact.getAccountGuid());
                logMessage = mContext
                        .getString(R.string.chat_system_message_removeAccountRegistrationAgain,
                                name);
            }
            mContext.getSingleChatController()
                    .sendSystemInfo(contact.getAccountGuid(), contact.getPublicKey(), logMessage, -1);

        } catch (LocalizedException le) {
            LogUtil.e(TAG, "Failed to notify chat controller of removed account:" + le.getMessage(), le);
        }
    }

    private void hideDeletedContactLocally(final String contactGuid) {
        try {

            Contact contact = mContactController.getContactByGuid(contactGuid);

            if (contact == null) {
                return;
            }

            if (!contact.isDeletedHidden()) {
                notifyChatControllerOfRemovedAccount(contact);
                mContactController.hideDeletedContactLocally(contact);
            }
        } catch (LocalizedException le) {
            LogUtil.e(TAG, "Failed to hideDeletedContactLocally:" + le.getMessage(), le);
        }
    }

    private boolean hasNewerContact(String phone, String mail) {
        if (mBcryptContactInfoMap == null || mBcryptContactInfoMap.size() < 1) {
            return false;
        }

        boolean searchForPhone = !StringUtil.isNullOrEmpty(phone);
        boolean searchForMail = !StringUtil.isNullOrEmpty(mail);

        if (!searchForPhone && !searchForMail) {
            return false;
        }

        for (int i = 0; i < mBcryptContactInfoMap.size(); i++) {
            ContactInfo ci = mBcryptContactInfoMap.valueAt(i);

            if (ci != null) {
                if (searchForPhone && ci.isPhone) {
                    if (StringUtil.isEqual(phone, ci.value)) {
                        return true;
                    }
                } else if (searchForMail && !ci.isPhone) {
                    if (StringUtil.isEqual(mail, ci.value)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private ArrayList<String> loadPublicKeys(final List<String> contactGuids, final boolean withProfileInfos)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();
        final ArrayList<String> knownServerGuids = new ArrayList<>(contactGuids.size());

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
                                Contact contact = mContactController.getContactByGuid(guid);
                                if (contact != null) {
                                    if (!StringUtil.isNullOrEmpty(publicKey)) {
                                        knownServerGuids.add(guid);
                                        contact.setPublicKey(publicKey);
                                    }

                                    String simsmeId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, jsonAccountObject);

                                    if (!StringUtil.isNullOrEmpty(simsmeId)) {
                                        contact.setSimsmeId(simsmeId);
                                    }

                                    if (withProfileInfos) {
                                        if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                                            mContactController.setEncryptedProfileInfosToContact(
                                                    jsonAccountObject, contact);
                                        } else {
                                            decryptedAndSetProfileInfosToContact(jsonAccountObject,
                                                    contact);
                                        }
                                    }

                                    synchronized (mContactDao) {
                                        mContactDao.update(contact);
                                    }
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
        BackendService.withSyncConnection(mContext)
                .getAccountInfoBatch(guids, withProfileInfos, false, listener);

        if (rm.isError) {
            throw new LocalizedException(LocalizedException.BACKEND_REQUEST_FAILED, rm.errorMsg + " " + rm.errorIdent);
        }

        return knownServerGuids;
    }

    private void decryptedAndSetProfileInfosToContact(final JsonObject accountObject,
                                                      final Contact contact) throws LocalizedException {
        if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
            return;
        }

        String aesKeyAsString = contact.getProfileInfoAesKey();
        SecretKey aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyAsString);

        if (accountObject.has("status") && !accountObject.get("status").isJsonNull()) {
            String encryptedStatus = accountObject.get("status").getAsString();
            String status = SecurityUtil.decryptBase64StringWithAES(encryptedStatus, aesKey);

            contact.setStatusText(status);
        }

        if (accountObject.has("nickname") && !accountObject.get("nickname").isJsonNull()) {
            String encryptedNickname = accountObject.get("nickname").getAsString();

            String nickname = SecurityUtil.decryptBase64StringWithAES(encryptedNickname, aesKey);

            contact.setNickname(nickname);
        }

        if (accountObject.has("image_checksum") && !accountObject.get("image_checksum").isJsonNull()) {
            final String checksum = accountObject.get("image_checksum").getAsString();

            String oldChecksum = contact.getProfileImageChecksum();

            if (!StringUtil.isEqual(oldChecksum, checksum)) {
                final String accountGuid = contact.getAccountGuid();
                //bild laden
                BackendService.withSyncConnection(mContext)
                        .getAccountImage(accountGuid, new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(BackendResponse response) {
                                if (!response.isError) {
                                    try {
                                        if (response.jsonArray == null || response.jsonArray.size() < 1) {
                                            return;
                                        }

                                        String imageBase64String = response.jsonArray.get(0).getAsString();

                                        if (imageBase64String == null) {
                                            return;
                                        }

                                        if(imageBase64String.length() > 0) {
                                            String aesKeyString = contact.getProfileInfoAesKey();
                                            if (StringUtil.isNullOrEmpty(aesKeyString)) {
                                                return;
                                            }

                                            SecretKey aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyString);

                                            if (aesKey == null) {
                                                return;
                                            }

                                            byte[] encryptedBytes = Base64
                                                    .decode(imageBase64String, Base64.NO_WRAP);
                                            byte[] decryptedBytes = SecurityUtil
                                                    .decryptMessageWithAES(encryptedBytes, aesKey);

                                            byte[] decodeBytes = Base64.decode(decryptedBytes, Base64.NO_WRAP);

                                            if (decodeBytes != null && decodeBytes.length > 0) {
                                                mImageController.saveProfileImageRaw(accountGuid, decodeBytes);
                                                contact.setProfileImageChecksum(checksum);
                                            }
                                        } else {
                                            mImageController.deleteProfileImage(accountGuid);
                                            contact.setProfileImageChecksum("TODO");
                                        }
                                    } catch (LocalizedException e) {
                                        LogUtil.e(TAG, e.getMessage(), e);
                                    }
                                }
                            }
                        });
            }
        }

        if (JsonUtil.hasKey("readOnly", accountObject)) {
            final int readOnly = accountObject.get("readOnly").getAsInt();
            if (readOnly == 1) {
                contact.setIsReadOnly(true);
            }
        }
    }

    private JsonArray getContactBCrypts(final String salt, final String searchType) {

        if (mBcryptContactInfoMap == null || mBcryptContactInfoMap.size() < 1) {
            return null;
        }

        boolean isPhoneSearchType = StringUtil.isEqual(JsonConstants.SEARCH_TYPE_PHONE, searchType);

        final JsonArray hashData = new JsonArray();

        for (int i = 0; i < mBcryptContactInfoMap.size(); i++) {
            String bcrypt = mBcryptContactInfoMap.keyAt(i);
            ContactInfo ci = mBcryptContactInfoMap.valueAt(i);

            if (bcrypt != null && bcrypt.startsWith(salt)) {
                if (isPhoneSearchType == ci.isPhone) {
                    LogUtil.d("CONTACT", "b: " + bcrypt + " | " + ci.value);
                    hashData.add(new JsonPrimitive(bcrypt));
                }
            }
        }
        return hashData;
    }

    private void syncAllPhoneContactsWithDB() throws LocalizedException {

        final Cursor cursor = mContext.getContentResolver()
                .query(ContactsContract.Data.CONTENT_URI, PROJECTION_, SELECTION_,
                        null, null);

        if (cursor == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Contacts cursor is null");
        }

        cursor.moveToFirst();

        long count = 0;

        while (!cursor.isAfterLast() && !isCanceled()) {
            final long contactId = cursor.getLong(ID_COLUMN_INDEX);
            final String data = cursor.getString(1);
            final String normalized = cursor.getString(2);
            final String mimetype = cursor.getString(3);

            ContactInfo contactInfo = new ContactInfo();
            contactInfo.contactId = contactId;

            final String valueForBcrypt;
            if (StringUtil.isEqual(Phone.CONTENT_ITEM_TYPE, mimetype)) {
                contactInfo.isPhone = true;
                String normalizedPhoneNumber;
                if (StringUtil.isNullOrEmpty(normalized)) {
                    normalizedPhoneNumber = PhoneNumberUtil
                            .normalizePhoneNumberNew(mContext, mCountryCode, data);
                } else {

                    normalizedPhoneNumber = normalized;
                }

                valueForBcrypt = normalizedPhoneNumber;
            } else if (StringUtil.isEqual(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, mimetype)) {
                contactInfo.isPhone = false;
                valueForBcrypt = data.toLowerCase(Locale.US);
            } else {
                cursor.moveToNext();
                continue;
            }

            contactInfo.value = valueForBcrypt;

            for (int i = 0; i < mServerSalts.size(); i++) {
                String bcryptedValue = getBcryptForValue(mServerSalts.valueAt(i), valueForBcrypt);

                if (!StringUtil.isNullOrEmpty(bcryptedValue)) {
                    mBcryptContactInfoMap.put(bcryptedValue, contactInfo);
                }
            }
            cursor.moveToNext();
        }

        cursor.close();
        saveBcryptMap();

        // Zum Schluss noch alle Löschen die nicht mehr benötigt werden.

        LogUtil.i(TAG, "syncAllPhoneContactsWithDB complete");
    }

    private void refreshContactInfosFromPhonebook(final Contact contact, final ContactInfo contactInfo)
            throws LocalizedException {
        if (this.isCanceled()) {
            return;
        }

        final String[] whereParams = new String[]{String.valueOf(contactInfo.contactId)};

        final Cursor cursor = mContext.getContentResolver()
                .query(ContactsContract.Data.CONTENT_URI, PROJECTION_REFRESH, SELECTION_REFRESH,
                        whereParams, null);
        if (cursor == null) {
            return;
        }

        if (!cursor.moveToFirst()) {
            return;
        }

        while (!cursor.isAfterLast() && !isCanceled()) {
            final String mimetype = cursor.getString(MIMETYPE);

            if (StringUtil.isEqual(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, mimetype)) {
                boolean haveToRefreshFtsDB = false;
                boolean isPrivateContact = StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_PRIVATE_ENTRY);

                final String firstName = cursor.getString(DATA2_GIVEN_NAME);
                String oldFirstName = contact.getFirstName();
                //setzen des Wertes wenn es ein privater Kontakt ist oder der Wert im Kontakt null ist. Wert aus Company Verzeichnis ist priorisiert
                if (isPrivateContact || StringUtil.isNullOrEmpty(oldFirstName)) {
                    if (!StringUtil.isEqual(oldFirstName, firstName)) {
                        contact.setFirstName(firstName);
                        haveToRefreshFtsDB = true;
                    }
                }

                String oldLastName = contact.getLastName();
                final String lastName = cursor.getString(DATA3_FAMILY_NAME);
                //setzen des Wertes wenn es ein privater Kontakt ist oder der Wert im Kontakt null ist. Wert aus Company Verzeichnis ist priorisiert
                if (isPrivateContact || StringUtil.isNullOrEmpty(oldLastName)) {
                    if (!StringUtil.isEqual(oldLastName, lastName)) {
                        contact.setLastName(lastName);
                        haveToRefreshFtsDB = true;
                    }
                }

                if (haveToRefreshFtsDB) {
                    addToFtsRefreshGuids(contact.getAccountGuid());
                }

                if (isPrivateContact) {
                    final String middleName = cursor.getString(DATA5_MIDDLE_NAME);

                    if (middleName != null) {
                        contact.setMiddleName(middleName);
                    }

                    final String prefix = cursor.getString(DATA4_NORMALIZED_PHONE_PREFIX_NAME);

                    if (prefix != null) {
                        contact.setNamePrefix(prefix);
                    }

                    final String suffix = cursor.getString(DATA6_SUFFIX_NAME);

                    if (suffix != null) {
                        contact.setNameSuffix(suffix);
                    }
                }
            } else if (StringUtil.isEqual(Phone.CONTENT_ITEM_TYPE, mimetype)) {
                String photoUri = cursor.getString(PHOTO_THUMBNAIL_URI);

                if (!StringUtil.isNullOrEmpty(photoUri) && StringUtil.isNullOrEmpty(contact.getProfileImageChecksum())) {
                    int profileImageSize = mContext.getResources().getInteger(R.integer.profile_image_size);
                    Bitmap bm = ContactUtil.loadContactPhotoThumbnail(photoUri, profileImageSize, mContext);
                    if (bm != null) {
                        byte[] imgBytes = ImageUtil.compress(bm, 100);
                        if (imgBytes != null) {
                            mImageController.saveProfileImageRaw(contact.getAccountGuid(), imgBytes);
                        }
                    }
                }
            }

            cursor.moveToNext();
        }

        cursor.close();
    }

    private String getBcryptForValue(@NonNull final String salt, @NonNull final String value) {
        if (mBcryptMap == null) {
            Object o = mFileUtil.loadObjectFromFile(BCRYPT_MAP_FILE);
            if (o instanceof HashMap) {
                //noinspection unchecked
                mBcryptMap = (HashMap<String, String>) o;
            } else {
                mBcryptMap = new HashMap<>();
            }
        }

        String key = salt + value;
        if (mBcryptMap.containsKey(key)) {
            return mBcryptMap.get(key);
        }

        String bcrypt = BCrypt.hashpw(value, salt);

        mBcryptMap.put(key, bcrypt);

        return bcrypt;
    }

    private void saveBcryptMap() {
        if (mBcryptMap != null) {
            if (mFileUtil.saveObjectToFile(mBcryptMap, BCRYPT_MAP_FILE)) {
                mBcryptMap = null;
            }
        }
    }

    private void addToFtsRefreshGuids(final String guid) {
        mFtsRefreshGuids.remove(guid);
        mFtsRefreshGuids.add(guid);
    }

    private class ContactInfo {
        long contactId;
        boolean isPhone;
        String value;
    }
}
