// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.task;

import android.os.AsyncTask;
import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.ContactDao;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.Listener.GenericUpdateListener;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static eu.ginlo_apps.ginlo.controller.AccountController.ACCOUNT_MIGRATION_VERSION;

/**
 * Created by Florian on 27.04.18.
 */

public class MigrationTask extends AsyncTask<Integer, String, LocalizedException> {
    /**
     * Migration 2.2 -> 2.3
     */
    private static final int CONTACT_FIX_MIGRATION_VERSION = 1;
    /**
     * Migration 2.3 -> 2.4
     */
    private static final int DOMAIN_MIGRATION_VERSION = 2;
    /**
     * Migration 2.4 -> 2.5
     */
    private static final int DOMAIN_COMPANY_CONTACTS_GREEN = 3;

    private final SimsMeApplication mApp;
    private final GenericUpdateListener<Integer> mListener;
    private LocalizedException mException;
    private int mCancelStringId = -1;

    public MigrationTask(@NonNull SimsMeApplication application, @NonNull final GenericUpdateListener<Integer> listener) {
        mApp = application;
        mListener = listener;
    }

    /**
     * @param params parameter
     * @return exception
     */
    @Override
    protected LocalizedException doInBackground(Integer... params) {
        if (params == null || params.length < 1) {
            return new LocalizedException(LocalizedException.MIGRATION_FAILED, "no migration version");
        }

        try {
            int migrationVersion = params[0];

            // Prüfen ob inet connection besteht
            if (!BackendService.withSyncConnection(mApp)
                    .isConnected()) {
                // abbrechen
                mCancelStringId = R.string.backendservice_internet_connectionFailed;
            }

            //FTS DB aufbauen
            if (RuntimeConfig.isBAMandant()) {
                if (!mApp.getContactController().existsFtsDatabase()) {
                    //
                    mApp.getContactController().createAndFillFtsDB(false);
                }
                //Fix SIMSME-6789 - Fehler bei der Migration
                else {
                    //DB oeffnen
                    final CountDownLatch latch = new CountDownLatch(1);
                    mApp.getContactController().openFTSDatabase(new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            latch.countDown();
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            mException = new LocalizedException(errorIdent, message);
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        mException = new LocalizedException(LocalizedException.UNKNOWN_ERROR, "latch error", e);
                    }

                    //SIMSME-6789 -> Dadurch wurde FTS nicht befuellt -> also nochmal
                    if (migrationVersion < DOMAIN_COMPANY_CONTACTS_GREEN) {
                        if (mException == null) {
                            mApp.getContactController().fillFtsDB(false);
                        }
                    }
                }
                //
            }

            final ContactController contactController = mApp.getContactController();
            if (!mApp.getPreferencesController().hasOldContactsMerged()) {
                mergeContactsFrom2_0(this);
                mApp.getPreferencesController().setHasOldContactsMerged();
                mApp.getPreferencesController().setMigrationVersion(ACCOUNT_MIGRATION_VERSION);
            }
            //fix Kontakte Merge Problem, Update auf 2.3
            else if (migrationVersion < CONTACT_FIX_MIGRATION_VERSION) {
                publishProgress(mApp.getResources().getString(R.string.migration_task_22_start));
                contactController.fixPrivateIndexMergeProblemSync(new GenericUpdateListener<Void>() {
                    @Override
                    public void onSuccess(Void object) {
                        //
                        mApp.getPreferencesController().setMigrationVersion(CONTACT_FIX_MIGRATION_VERSION);
                    }

                    @Override
                    public void onUpdate(String updateMessage) {
                        publishProgress(updateMessage);
                    }

                    @Override
                    public void onFail(String message, String errorIdent) {
                        mException = new LocalizedException(errorIdent, message);
                    }
                });
            }
            //2.3 -> 2.4 Domain setzen
            if (migrationVersion < DOMAIN_MIGRATION_VERSION) {
                if (RuntimeConfig.isBAMandant()) {
                    final Contact ownContact = contactController.getOwnContact();
                    final String domain = ownContact.getDomain();
                    final String email = ownContact.getEmail();

                    // wenn eine email-adresse gesetzt ist, aber die domain am eigenen contakt leer ist, muss das domain-verzeichnis nachgeladen werden
                    if (StringUtil.isNullOrEmpty(domain) && !StringUtil.isNullOrEmpty(email)) {
                        IBackendService.OnBackendResponseListener listenerValidateMail = new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(BackendResponse response) {
                                if (response.isError) {
                                    ResponseModel rm = new ResponseModel();
                                    rm.setError(response);
                                    if (rm.responseException != null) {
                                        mException = rm.responseException;
                                    } else {
                                        mException = new LocalizedException(LocalizedException.BACKEND_REQUEST_FAILED, "");
                                    }

                                    if (StringUtil.isEqual(LocalizedException.BLACKLISTED_EMAIL_DOMAIN, mException.getIdentifier())) {
                                        mApp.getPreferencesController().setMigrationVersion(DOMAIN_MIGRATION_VERSION);
                                        mException = null;
                                    }
                                } else {
                                    if (response.jsonArray != null
                                            && response.jsonArray.size() != 0
                                            && !response.jsonArray.get(0).isJsonNull()
                                            && email.contains(response.jsonArray.get(0).getAsString())) {
                                        String result = response.jsonArray.get(0).getAsString();
                                        if (!StringUtil.isNullOrEmpty(result)) {
                                            try {

                                                final Contact ownContact = mApp.getContactController().getOwnContact();
                                                ownContact.setDomain(result);
                                                mApp.getContactController().insertOrUpdateContact(ownContact);

                                                mApp.getPreferencesController().setMigrationVersion(DOMAIN_MIGRATION_VERSION);
                                            } catch (final LocalizedException e) {
                                                LogUtil.w(AccountController.class.getSimpleName(), e.getMessage(), e);
                                            }
                                        }
                                    }
                                }
                            }
                        };

                        BackendService.withSyncConnection(mApp)
                                .validateMail(email, true, listenerValidateMail);
                    } else {
                        mApp.getPreferencesController().setMigrationVersion(DOMAIN_MIGRATION_VERSION);
                    }
                } else {
                    mApp.getPreferencesController().setMigrationVersion(DOMAIN_MIGRATION_VERSION);
                }
            }

            //2.4 -> 2.5 - Firmenkontakte grün mit dem man bereits geschrieben hatte
            if (migrationVersion < DOMAIN_COMPANY_CONTACTS_GREEN) {
                if (RuntimeConfig.isBAMandant()) {
                    //final String ownContactGuid = contactController.getOwnContact().getAccountGuid();

                    List<Contact> contactList = contactController.getDao().loadAll();
                    if (contactList.size() > 0) {
                        for (Contact contact : contactList) {
                            if (!contact.isDeletedHidden()) {
                                if (StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_COMPANY_ENTRY) || StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_DOMAIN_ENTRY)) {
                                    contactController.saveContactInformation(contact, null, null, null, null, null, null, null, null, Contact.STATE_HIGH_TRUST, false);
                                }
                            }
                        }
                    }
                    mApp.getPreferencesController().setMigrationVersion(DOMAIN_COMPANY_CONTACTS_GREEN);
                } else {
                    mApp.getPreferencesController().setMigrationVersion(DOMAIN_COMPANY_CONTACTS_GREEN);
                }
            }

            return mException;
        } catch (LocalizedException e) {
            return e;
        }
    }

    @Override
    protected void onPostExecute(LocalizedException e) {
        super.onPostExecute(e);

        if (e == null) {
            mListener.onSuccess(ACCOUNT_MIGRATION_VERSION);
        } else {
            LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
            mListener.onFail("", e.getIdentifier());
        }
    }

    @Override
    protected void onCancelled() {
        String cancelString = null;
        if (mCancelStringId != -1) {
            try {
                cancelString = mApp.getString(mCancelStringId);
            } catch (Exception e) {
                //
            }
        }
        mListener.onFail(cancelString, null);
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        mListener.onUpdate(values != null && values.length > 0 ? values[0] : "");
    }

    private void mergeContactsFrom2_0(@NonNull final MigrationTask task)
            throws LocalizedException {
        task.publishProgress(mApp.getResources().getString(R.string.migration_task_20_start));

        boolean isDeviceManaged = mApp.getAccountController().isDeviceManaged();
        boolean hasCompanyContacts = mApp.getAccountController().hasEmailOrCompanyContacts();
        String ownAccountGuid = mApp.getAccountController().getAccount().getAccountGuid();
        ContactDao contactDao = mApp.getContactController().getDao();
        List<Contact> allContacts = mApp.getContactController().getDao().loadAll();
        List<String> contactGuidsWithoutID = new ArrayList<>();
        boolean foundContactForOwnAccount = false;

        for (Contact contact : allContacts) {
            if (StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
                contactDao.delete(contact);
                continue;
            }

            if (!contact.getIsSimsMeContactSave() && contact.isDeletedHidden()) {
                if (mApp.getSingleChatController().getChatByGuid(contact.getAccountGuid()) == null) {
                    contactDao.delete(contact);
                    continue;
                }
            }

            //Pruefen ob es der eigenene Kontakt ist. Darf eigentlich nicht sein
            if (StringUtil.isEqual(contact.getAccountGuid(), ownAccountGuid)) {
                mApp.getContactController().fillOwnContactWithAccountInfos(contact);
                foundContactForOwnAccount = true;
            }

            //Pruefen ob eine Private Index Class gesetzt ist
            if (StringUtil.isNullOrEmpty(contact.getClassEntryName())) {
                //String classType = Contact.CLASS_PRIVATE_ENTRY;

                //prüfen ob es dafür ein Company Contact gibt
                if (hasCompanyContacts) {
                    final CompanyContact companyContactCompany = mApp.getContactController().getCompanyContactWithAccountGuid(contact.getAccountGuid());

                    if (companyContactCompany != null) {
                        contact.setClassEntryName(companyContactCompany.getClassType());
                    } else {
                        contact.setClassEntryName(Contact.CLASS_PRIVATE_ENTRY);
                    }
                }
            }

            if (StringUtil.isNullOrEmpty(contact.getPrivateIndexGuid())) {
                contact.setPrivateIndexGuid(GuidUtil.generatePrivateIndexGuid());
            }

            //Verweise auf Telefonbuch entfernen
            contact.setHasNewerContact(false);
            contact.setLastKnownRowId((long) 0);
            contact.setLookupKey("");

            contactDao.update(contact);

            if (StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                contactGuidsWithoutID.add(contact.getAccountGuid());
            }
        }

        if (!foundContactForOwnAccount) {
            mApp.getContactController().fillOwnContactWithAccountInfos(null);
        }

        task.publishProgress(mApp.getResources().getString(R.string.migration_task_20_load_contact_infos));

        if (!contactGuidsWithoutID.isEmpty()) {
            mApp.getContactController().loadContactsAccountInfo(contactGuidsWithoutID);
        }

        task.publishProgress(mApp.getResources().getString(R.string.migration_task_20_start_private_index));
        allContacts = contactDao.loadAll();

        if (allContacts != null && allContacts.size() > 0) {
            for (int i = 0; i < allContacts.size(); i++) {
                Contact contact = allContacts.get(i);

                if (contact != null && contact.getId() != null) {
                    contact.setChecksum("");
                    contactDao.update(contact);
                }
            }
        }

        task.publishProgress(mApp.getResources().getString(R.string.migration_task_20_upload_private_index));

        //private index hochladen
        mApp.getContactController().updatePrivateIndexEntriesSync(new GenericUpdateListener<Void>() {
            @Override
            public void onSuccess(Void object) {
                //
            }

            @Override
            public void onUpdate(String updateMessage) {
                if (!StringUtil.isNullOrEmpty(updateMessage)) {
                    task.publishProgress(updateMessage);
                }
            }

            @Override
            public void onFail(String message, String errorIdent) {
                //
            }
        });

        //wenn gemanaged
        if (isDeviceManaged) {
            task.publishProgress(mApp.getResources().getString(R.string.migration_task_20_start_company_index));
            //E-Mail Verzeichnis löschen
            //mApp.getContactController().deleteAllCompanyContacts();

            task.publishProgress(mApp.getResources().getString(R.string.migration_task_20_download_company_index));
            // und Firmenverzeichnis laden
            ResponseModel rm = mApp.getContactController().loadCompanyIndexSync(null, false);

            if (rm.isError) {
                LogUtil.e(this.getClass().getSimpleName(), rm.errorMsg);
            }
        }
    }
}

