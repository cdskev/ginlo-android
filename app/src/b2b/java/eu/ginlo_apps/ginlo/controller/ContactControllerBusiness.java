// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.sqlcipher.Cursor;

import org.greenrobot.greendao.query.QueryBuilder;
import org.greenrobot.greendao.query.WhereCondition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.crypto.SecretKey;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.CompanyContactDetailActivity;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.contracts.OnGetPurchasedProductsListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.CompanyContactDao;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.ContactDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.CompanyContactUtil;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.fts.FtsDatabaseHelper;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

import static eu.ginlo_apps.ginlo.greendao.Contact.CLASS_COMPANY_ENTRY;
import static eu.ginlo_apps.ginlo.greendao.Contact.CLASS_DOMAIN_ENTRY;

public class ContactControllerBusiness
        extends ContactController implements PreferencesController.OnServerVersionChangedListener,
        FtsDatabaseHelper.FtsDatabaseOpenListener {
    public final static int LICENSE_DAYS_LEFT_NO_VALUE = -9999;
    private final static String TAG = "ContactControllerBusiness";
    private final static int CONTACTS_DOWNLOAD_JUNK_SIZE = 500;

    private final CompanyContactDao mCompanyContactDao;

    private CompanyIndexTask mCompanyIndexTask;
    private boolean mStartCompanyIndexTaskAgain;

    private LicenseDaysLeftListener mLicenseDaysLeftListener;
    private TrialVoucherDaysLeftListener mTrialVoucherDaysLeftListener;

    private int mLicenseDaysLeft = LICENSE_DAYS_LEFT_NO_VALUE;
    private int mTrialVoucherDaysLeft = LICENSE_DAYS_LEFT_NO_VALUE;
    private FtsDatabaseHelper mFtsDbHelper;
    private SearchFtsTask mSearchFtsTask;
    private GetAddressInformationTask mGetAddressInformationTask;
    private List<LoadCompanyContactsListener> mGetAddressInformationTaskListeners;

    public ContactControllerBusiness(final SimsMeApplicationBusiness application) {
        super(application);

        mCompanyContactDao = application.getCompanyContactDao();
        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_LIST_COMPANY_INDEX, this);
        mFtsDbHelper = FtsDatabaseHelper.getInstance();
    }

    private CompanyContactDao getCompanyContactDao() {
        return mCompanyContactDao;
    }

    public void getAddressInformation(final LoadCompanyContactsListener getAddressInformationListener, Executor executor) {
        if (mGetAddressInformationTaskListeners == null) {
            mGetAddressInformationTaskListeners = new ArrayList<>();
        }

        if (getAddressInformationListener != null) {
            if (!mGetAddressInformationTaskListeners.contains(getAddressInformationListener)) {
                mGetAddressInformationTaskListeners.add(getAddressInformationListener);
            }
        }

        if (mGetAddressInformationTask != null) {
            return;
        }

        final Handler mainHandler = (Looper.myLooper() == Looper.getMainLooper()) ? new Handler(Looper.getMainLooper()) : null;

        mGetAddressInformationTask = new GetAddressInformationTask(new LoadCompanyContactsListener() {

            @Override
            public void onLoadSuccess() {
                if (mGetAddressInformationTaskListeners != null) {
                    Iterator<LoadCompanyContactsListener> iterator = new ArrayList<>(mGetAddressInformationTaskListeners).iterator();
                    // throws ConcurrentModificationException with foreach because the list is updated while iterating (multithreading b...)
                    // noinspection WhileLoopReplaceableByForEach
                    while (iterator.hasNext()) {
                        iterator.next().onLoadSuccess();
                    }
                    mGetAddressInformationTaskListeners = null;
                }
                mGetAddressInformationTask = null;
            }

            @Override
            public void onLoadFail(String message, String errorIdent) {
                if (mGetAddressInformationTaskListeners != null) {
                    for (LoadCompanyContactsListener listener : mGetAddressInformationTaskListeners) {
                        listener.onLoadFail(message, errorIdent);
                    }
                    mGetAddressInformationTaskListeners = null;
                }
                mGetAddressInformationTask = null;
            }

            @Override
            public void onLoadCompanyContactsSize(final int size) {
                if (mGetAddressInformationTaskListeners == null || mGetAddressInformationTaskListeners.size() < 1) {
                    return;
                }

                if (mainHandler != null) {
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            for (LoadCompanyContactsListener listener : mGetAddressInformationTaskListeners) {
                                listener.onLoadCompanyContactsSize(size);
                            }
                        }
                    };
                    mainHandler.post(myRunnable);
                } else {
                    for (LoadCompanyContactsListener listener : mGetAddressInformationTaskListeners) {
                        listener.onLoadCompanyContactsSize(size);
                    }
                }
            }

            @Override
            public void onLoadCompanyContactsUpdate(final int count) {
                if (mGetAddressInformationTaskListeners == null || mGetAddressInformationTaskListeners.size() < 1) {
                    return;
                }

                if (mainHandler != null) {
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            for (LoadCompanyContactsListener listener : mGetAddressInformationTaskListeners) {
                                listener.onLoadCompanyContactsUpdate(count);
                            }
                        }
                    };
                    mainHandler.post(myRunnable);
                } else {
                    for (LoadCompanyContactsListener listener : mGetAddressInformationTaskListeners) {
                        listener.onLoadCompanyContactsUpdate(count);
                    }
                }
            }
        });

        mGetAddressInformationTask.executeOnExecutor(executor != null ? executor : AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public int countCompanyContacts(final IndexType indexType) {
        synchronized (mCompanyContactDao) {
            return mCompanyContactDao.countEntries(indexType);
        }
    }

    public ArrayList<CompanyContact> getCompanyContactsByGuid(String[] guids, final IndexType indexType) {
        synchronized (mCompanyContactDao) {
            QueryBuilder<CompanyContact> queryBuilder = mCompanyContactDao.queryBuilder();
            if(indexType != null)
            {
                if (IndexType.INDEX_TYPE_COMPANY.equals(indexType)) {
                    queryBuilder.where(CompanyContactDao.Properties.guid.isNotNull());
                } else if (IndexType.INDEX_TYPE_DOMAIN.equals(indexType)) {
                    queryBuilder.where(CompanyContactDao.Properties.guid.isNull());
                }
            }


            if (guids.length <= 0) {
                return new ArrayList<>();
            } else if (guids.length == 1) {
                queryBuilder.where(CompanyContactDao.Properties.AccountGuid.eq(guids[0]));
            } else {
                String[] moreGuids = new String[guids.length - 2];

                System.arraycopy(guids, 2, moreGuids, 0, moreGuids.length);

                WhereCondition[] moreConditions = new WhereCondition[moreGuids.length];

                for (int i = 0; i < moreGuids.length; i++) {
                    moreConditions[i] = CompanyContactDao.Properties.AccountGuid.eq(moreGuids[i]);
                }

                queryBuilder.whereOr(CompanyContactDao.Properties.AccountGuid.eq(guids[0]), CompanyContactDao.Properties.AccountGuid.eq(guids[1]), moreConditions);
            }

            try {
                List<CompanyContact> contacts = queryBuilder.build().forCurrentThread().list();

                if (contacts.size() > 0) {
                    return (ArrayList<CompanyContact>) contacts;
                } else {
                    return new ArrayList<>();
                }
            } catch (IllegalArgumentException e) {
                return new ArrayList<>();
            }
        }
    }

    private CompanyContact getCompanyContactWithAccountGuid(final String guid, final IndexType indexType) {
        List<CompanyContact> contacts = getCompanyContactWithAccountGuidInternally(guid, indexType);

        if (contacts == null || contacts.size() < 1) {
            return null;
        }

        CompanyContact contact = contacts.get(0);
        if (contacts.size() > 1) {
            synchronized (mCompanyContactDao) {
                for (int i = 1; i < contacts.size(); i++) {
                    CompanyContact cd = contacts.get(i);

                    mCompanyContactDao.delete(cd);
                }
            }
        }

        return contact;
    }

    @Override
    public CompanyContact getCompanyContactWithAccountGuid(final String guid) {
        final CompanyContact companyContactWithAccountGuid = getCompanyContactWithAccountGuid(guid, IndexType.INDEX_TYPE_COMPANY);
        if (companyContactWithAccountGuid == null) {
            return getCompanyContactWithAccountGuid(guid, IndexType.INDEX_TYPE_DOMAIN);
        } else {
            return companyContactWithAccountGuid;
        }
    }

    private List<CompanyContact> getCompanyContactWithAccountGuidInternally(final String guid, final IndexType indexType) {
        QueryBuilder<CompanyContact> builder = mCompanyContactDao.queryBuilder();
        final List<CompanyContact> list = builder.where(CompanyContactDao.Properties.AccountGuid.eq(guid)).build().forCurrentThread().list();

        final List<CompanyContact> retVal = new ArrayList<>();
        for (final CompanyContact companyContact : list) {
            if ((CLASS_COMPANY_ENTRY.equals(companyContact.getClassType()) && IndexType.INDEX_TYPE_COMPANY.equals(indexType))
                    || (CLASS_DOMAIN_ENTRY.equals(companyContact.getClassType()) && IndexType.INDEX_TYPE_DOMAIN.equals(indexType))) {
                retVal.add(companyContact);
            }
        }
        return retVal;

    }

    private CompanyContact getCompanyContactWithIndexGuid(final String guid) {
        QueryBuilder<CompanyContact> builder = mCompanyContactDao.queryBuilder();

        List<CompanyContact> contacts = builder.where(CompanyContactDao.Properties.guid.eq(guid)).build().forCurrentThread().list();

        if (contacts == null || contacts.size() < 1) {
            return null;
        }

        CompanyContact contact = contacts.get(0);
        if (contacts.size() > 1) {
            synchronized (mCompanyContactDao) {
                for (int i = 1; i < contacts.size(); i++) {
                    CompanyContact cd = contacts.get(i);

                    mCompanyContactDao.delete(cd);
                }
            }
        }

        return contact;
    }

    @Override
    public void copyCompanyContactInfoToContact(@NonNull final CompanyContact companyContact, @NonNull final Contact contact)
            throws LocalizedException {
        String mail = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_EMAIL);

        if (!StringUtil.isNullOrEmpty(mail)) {
            contact.setEmail(mail);
        }

        String firstname = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_FIRSTNAME);

        if (!StringUtil.isNullOrEmpty(firstname)) {
            contact.setFirstName(firstname);
        }

        String name = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_LASTNAME);

        if (!StringUtil.isNullOrEmpty(name)) {
            contact.setLastName(name);
        }
    }

    private List<CompanyContact> getAllCompanyContacts(final IndexType indexType) {
        QueryBuilder<CompanyContact> builder = mCompanyContactDao.queryBuilder();

        if (IndexType.INDEX_TYPE_COMPANY.equals(indexType)) {
            builder.where(CompanyContactDao.Properties.guid.isNotNull());
        } else if (IndexType.INDEX_TYPE_DOMAIN.equals(indexType)) {
            builder.where(CompanyContactDao.Properties.guid.isNull());
        }

        return builder.build().forCurrentThread().list();
    }

    public List<CompanyContact> getAllCompanyContactsSort(final IndexType indexType) {
        List<CompanyContact> rc = getAllCompanyContacts(indexType);

        Collections.sort(rc, ContactUtil.getCompanyContactListSortComparator(mApplication));
        return rc;
    }

    @Override
    public void deleteAllDomainContacts() {
        synchronized (mCompanyContactDao) {
            final List<CompanyContact> allDomainContacts = getAllCompanyContacts(IndexType.INDEX_TYPE_DOMAIN);

            for (CompanyContact domainContact : allDomainContacts) {
                mCompanyContactDao.delete(domainContact);
            }
            mApplication.getPreferencesController().onDeleteAllCompanyContacts();
        }
    }

    @Override
    public void deleteFtsContact(@NonNull String accountGuid)
            throws LocalizedException {
        FtsDatabaseHelper.getInstance().deleteEntry(FtsDatabaseHelper.COLUMN_ACCOUNT_GUID, accountGuid);
    }

    private void deleteCompanyContact(CompanyContact cc) {
        synchronized (mCompanyContactDao) {
            mCompanyContactDao.delete(cc);
        }
    }

    private void insertOrUpdateCompanyContact(CompanyContact cc) {
        synchronized (mCompanyContactDao) {
            if (cc.getId() == null) {
                mCompanyContactDao.insert(cc);
            } else {
                mCompanyContactDao.update(cc);
            }
        }
    }

    /**
     * Info that the server version for which the listener has registered has changed.
     * After the listener reconciles its data with the server, it must use the method {@link PreferencesController # serverVersionIsUpToDate (String, String)}
     * to get the version saved.
     */
    @Override
    public void onServerVersionChanged(final String serverVersionKey, final String newServerVersion, final Executor executor) {
        if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_LIST_COMPANY_INDEX)) {
            if (!mApplication.getPreferencesController().hasOldContactsMerged()) {
                return;
            }

            loadCompanyIndexAsyncInternally(new LoadCompanyContactsListener() {
                @Override
                public void onLoadSuccess() {
                    mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_LIST_COMPANY_INDEX, newServerVersion);
                }

                @Override
                public void onLoadFail(String message, String errorIdent) {
                }

                @Override
                public void onLoadCompanyContactsSize(int size) {
                }

                @Override
                public void onLoadCompanyContactsUpdate(int count) {
                }
            }, false, executor);
        }
    }

    @Override
    public Intent getOpenContactInfoIntent(final BaseActivity callerActivity, final Contact contact) {
        if (contact == null) {
            return null;
        }

        try {
            if (StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                final CompanyContact companyContact = getCompanyContactWithAccountGuid(contact.getAccountGuid());

                if (companyContact == null) {
                    return null;
                } else {
                    Intent intent = new Intent(callerActivity, CompanyContactDetailActivity.class);
                    intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, contact.getAccountGuid());
                    return intent;
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getSimpleName(), e.toString(), e);
            return null;
        }

        return super.getOpenContactInfoIntent(callerActivity, contact);
    }

    public void setLicenseDaysLeftListener(final LicenseDaysLeftListener licenseDaysLeftListener) {
        mLicenseDaysLeftListener = licenseDaysLeftListener;
    }

    public void removeLicenseDaysLeftListener() {
        mLicenseDaysLeftListener = null;
    }

    public int getLicenseDaysLeft() {
        return mLicenseDaysLeft;
    }

    public void resetLicenseDaysLeft() {
        mLicenseDaysLeft = LICENSE_DAYS_LEFT_NO_VALUE;
    }

    public void setTrialVoucherDaysLeftListener(final TrialVoucherDaysLeftListener listener) {
        mTrialVoucherDaysLeftListener = listener;
    }

    public void removeTrialVoucherDaysLeftListener() {
        mTrialVoucherDaysLeftListener = null;
    }

    public int getTrialVoucherDaysLeft() {
        return mTrialVoucherDaysLeft;
    }

    public void resetTrialVoucherDaysLeft() {
        mTrialVoucherDaysLeft = LICENSE_DAYS_LEFT_NO_VALUE;
    }

    @Override
    public void appWillBeLocked() {
        super.appWillBeLocked();
        mFtsDbHelper.closeFtsDatabase();
    }

    @Override
    protected void startWorkAfterLogin() {
        if (FtsDatabaseHelper.existsFtsDatabase(mApplication)) {
            mFtsDbHelper.openFtsDatabase(this);
        }

        try {
            if (mApplication.getAccountController().isDeviceManaged()) {
                String lastFullCheckDateString = mApplication.getPreferencesController().getLastCompanyIndexFullCheckSyncTimeStamp();
                if (StringUtil.isNullOrEmpty(lastFullCheckDateString)) {
                    mApplication.getPreferencesController().setLastCompanyIndexFullCheckSyncTimeStamp(DateUtil.getDateStringFromLocale());
                } else {
                    if (!isSameDay(lastFullCheckDateString)) {
                        // check the checksum once per day
                        loadCompanyIndexAsyncInternally(new LoadCompanyContactsListener() {
                            @Override
                            public void onLoadSuccess() {
                                mApplication.getPreferencesController().setLastCompanyIndexFullCheckSyncTimeStamp(DateUtil.getDateStringFromLocale());
                            }

                            @Override
                            public void onLoadFail(String message, String errorIdent) {

                            }

                            @Override
                            public void onLoadCompanyContactsSize(int size) {

                            }

                            @Override
                            public void onLoadCompanyContactsUpdate(int count) {

                            }
                        }, true, null);
                    }
                }
            }

        } catch (LocalizedException e) {
            LogUtil.w(TAG, "startWorkAfterLogin()", e);
        }
        checkAccountLicense();
    }

    public void checkAccountLicense() {
        // KS: TODO: This code is obviously wrong here in ContactController
        if (BuildConfig.ACCOUNT_NEEDS_LICENCE) {
            // Check removed because there were messages when the licenses were extended by the backend, that the Android client still did not work.
            final AccountController accountController = mApplication.getAccountController();
            final Account account = accountController.getAccount();

            try {
                // server request for current purchases to see if the license has expired in the meantime
                // if it is already known that the license has expired, the request is obsolete
                LogUtil.i(TAG, "checkAccountLicense: isDeviceManaged = "
                        + accountController.isDeviceManaged()
                        + ", hasLicense = " + account.getHasLicence());

                final OnGetPurchasedProductsListener onGetPurchasedProductsListener = new OnGetPurchasedProductsListener() {

                    @Override
                    public void onGetPurchasedProductsSuccess() {
                        try {
                            if (!account.getHasLicence()) {
                                LogUtil.w(TAG, "checkAccountLicense: No License!");

                                final Activity topAc = mApplication.getAppLifecycleController().getTopActivity();
                                //Class<?> classForNextIntent = SystemUtil.getClassForBuildConfigClassname(BuildConfig.ACTIVITY_AFTER_CONFIRM_ACCOUNT);
                                final Intent intent = new Intent(topAc, PurchaseLicenseActivity.class);
                                topAc.startActivity(intent);
                            } else {
                                final Long licenceDate = accountController.getAccount().getLicenceDate();
                                if (licenceDate != null) {
                                    Calendar calli = Calendar.getInstance();
                                    final long now = calli.getTime().getTime();
                                    calli.setTimeInMillis(licenceDate);
                                    calli.add(Calendar.DATE, - BuildConfig.LICENSE_EXPIRATION_WARNING_DAYS);
                                    final long expireMinusWarningDays = calli.getTime().getTime();

                                    LogUtil.i(TAG, "checkAccountLicense: Has License valid until: " + new Date(licenceDate)
                                            + ", now: " + new Date(now)
                                            + ", expiration warning from: " + new Date(expireMinusWarningDays));

                                    if (now > licenceDate) {
                                        final BaseActivity topAc = (BaseActivity) mApplication.getAppLifecycleController().getTopActivity();
                                        if (topAc != null) {
                                            if (accountController.getAccount().isAutorenewingLicense()) {
                                                LogUtil.i(TAG, "checkAccountLicense: Expired License (autorenewing).");

                                                final Intent intent = new Intent(topAc, PurchaseLicenseActivity.class);
                                                intent.putExtra("PurchaseLicenseActivity.extraDontForwardIfLicenceIsAboutToExpire", true);
                                                topAc.startActivity(intent);

                                            } else {
                                                mLicenseDaysLeft = 0;

                                                if (mLicenseDaysLeftListener != null) {
                                                    mLicenseDaysLeftListener.licenseDaysLeftHasCalculate(mLicenseDaysLeft);
                                                }

                                                LogUtil.w(TAG, "checkAccountLicense: Expired License (non auto renewing)!");

                                                DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        dialog.dismiss();
                                                        //Class<?> classForNextIntent = SystemUtil.getClassForBuildConfigClassname(BuildConfig.ACTIVITY_AFTER_CONFIRM_ACCOUNT);
                                                        final Intent intent = new Intent(topAc, PurchaseLicenseActivity.class);
                                                        intent.putExtra("PurchaseLicenseActivity.extraDontForwardIfLicenceIsAboutToExpire", true);
                                                        topAc.startActivity(intent);
                                                    }
                                                };

                                                AlertDialogWrapper alert = DialogBuilderUtil.buildResponseDialog(topAc,
                                                        mApplication.getString(R.string.dialog_licence_is_expired),
                                                        mApplication.getString(R.string.dialog_licence_is_expired_title),
                                                        mApplication.getString(R.string.dialog_licence_is_about_to_expire_positive),
                                                        null,
                                                        positiveOnClickListener,
                                                        null);

                                                alert.show();
                                            }
                                        }
                                        //} else if ((nowPlus7Day > licenceDate) && (!accountController.getAccount().isAutorenewingLicense())) {
                                        //} else if ((expireMinusWarningDays < now) && (!accountController.getAccount().isAutorenewingLicense())) {
                                    } else if (!accountController.getAccount().isAutorenewingLicense()) {
                                        // 1 day in millis (1000 * 60 * 60 * 24) = 86400000
                                        final float ldl = (licenceDate - now) / 86400000.0f;
                                        // Allow user to work without renewal until end of expiration day.
                                        mLicenseDaysLeft = (ldl >= 0.1f) ? (int) Math.max(ldl, 1) : 0;
                                        LogUtil.i(TAG, "checkAccountLicense: License is not auto renewing. Days left: " + mLicenseDaysLeft + " (" + ldl + ")");

                                        if (mLicenseDaysLeftListener != null && (expireMinusWarningDays < now || BuildConfig.DEBUG)) {
                                            mLicenseDaysLeftListener.licenseDaysLeftHasCalculate(mLicenseDaysLeft);
                                        }
                                        mApplication.getPreferencesController().setPurchaseCheckDate(DateUtil.getDateStringFromLocale());
                                    }
                                    // Expiration Date > LICENSE_EXPIRATION_WARNING_DAYS
                                    else {
                                        mApplication.getPreferencesController().setPurchaseCheckDate(DateUtil.getDateStringFromLocale());
                                    }

                                    ContactControllerBusiness.this.startTimerForCheck();
                                }
                            }
                        } catch (LocalizedException e) {
                            LogUtil.e(TAG, "checkAccountLicense: onGetPurchasedProductsSuccess caught " + e.getMessage());
                        }
                    }

                    @Override
                    public void onGetPurchasedProductsFail(@NotNull String errorMessage) {
                        final BaseActivity topAc = (BaseActivity) mApplication.getAppLifecycleController().getTopActivity();
                        if (topAc != null) {
                            DialogBuilderUtil.buildErrorDialog(topAc, errorMessage).show();
                        }
                    }
                };

                if (accountController.isDeviceManaged() || account.getHasLicence()) {
                    accountController.getPurchasedProducts(onGetPurchasedProductsListener);
                } else {
                    final GenericActionListener<Void> getTrialVoucherInfoListener = new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(final Void object) {
                            try {
                                final int trialDaysLeft = accountController.getTrialDaysLeft();
                                if (trialDaysLeft > -1) {
                                    mTrialVoucherDaysLeft = trialDaysLeft;
                                    if (mTrialVoucherDaysLeftListener != null) {
                                        mTrialVoucherDaysLeftListener.trialVoucherDaysLeftHasCalculate(trialDaysLeft);
                                    }
                                }

                                if (account.getHasLicence()) {
                                    accountController.getPurchasedProducts(onGetPurchasedProductsListener);
                                }

                            } catch (final LocalizedException e) {
                                LogUtil.e(TAG, "checkAccountLicense: getTrialVoucherInfoListener onSuccess caught " + e.getMessage());

                            }
                        }

                        @Override
                        public void onFail(final String message, final String errorIdent) {
                            try {
                                if (account.getHasLicence()) {
                                    accountController.getPurchasedProducts(onGetPurchasedProductsListener);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, "checkAccountLicense: getTrialVoucherInfoListener onFail caught " + e.getMessage());
                            }
                        }
                    };

                    accountController.getTrialVoucherInfo(getTrialVoucherInfoListener);
                }

            } catch (LocalizedException e) {
                LogUtil.e(TAG, "checkAccountLicense: Caught " + e.getMessage());
            }
        } else {
            startTimerForCheck();
        }

    }

    @Override
    public synchronized Contact createContactIfNotExists(String guid,
                                                         String nickname,
                                                         String phoneNumber,
                                                         String simsmeId,
                                                         String tenant,
                                                         String publicKey,
                                                         boolean loadPublicKeyIfNotExists,
                                                         OnLoadPublicKeyListener onLoadPublicKeyListener,
                                                         boolean isHidden)
            throws LocalizedException {

        CompanyContact cc = getCompanyContactWithAccountGuid(guid);

        if (cc != null) {
            Contact contact = getContactForCompanyContact(cc);

            if (contact == null) {
                contact = new Contact();
                setMetaAttributesToContact(contact, cc);
            }

            boolean newChanges = false;

            if (nickname != null && !StringUtil.isEqual(nickname, contact.getNickname())) {
                contact.setNickname(nickname);
                newChanges = true;
            }

            if (phoneNumber != null && !StringUtil.isEqual(phoneNumber, contact.getPhoneNumber())) {
                contact.setPhoneNumber(phoneNumber);
                newChanges = true;
            }

            if (!StringUtil.isNullOrEmpty(simsmeId) && StringUtil.isNullOrEmpty(contact.getSimsmeId())) {
                contact.setSimsmeId(simsmeId);
                newChanges = true;
            }

            if (StringUtil.isNullOrEmpty(contact.getMandant())
                    && !StringUtil.isNullOrEmpty(tenant)) {
                contact.setMandant(tenant);
                newChanges = true;
            }

            boolean attrChanges = saveAttributesToContact(contact, cc);

            if (newChanges && !attrChanges) {
                insertOrUpdateContact(contact);
                updatePrivateIndexEntriesAsync();
            }

            if (newChanges || attrChanges) {
                mApplication.getChatOverviewController().chatChanged(null, contact.getAccountGuid(), null, ChatOverviewController.CHAT_CHANGED_TITLE);
            }

            return contact;
        }

        return super.createContactIfNotExists(guid, nickname, phoneNumber, simsmeId, tenant, publicKey, loadPublicKeyIfNotExists, onLoadPublicKeyListener, isHidden);
    }

    private Contact getContactForCompanyContact(@NonNull final CompanyContact companyContact)
            throws LocalizedException {
        if (StringUtil.isNullOrEmpty(companyContact.getAccountGuid())) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Account Guid is null");
        }

        return getContactByGuid(companyContact.getAccountGuid());
    }

    private void setMetaAttributesToContact(@NonNull Contact contact, @NonNull CompanyContact companyContact)
            throws LocalizedException {
        contact.setClassEntryName(companyContact.getClassType());
        contact.setAccountGuid(companyContact.getAccountGuid());
        contact.setSimsmeId(companyContact.getAccountId());
        contact.setIsHidden(true);
        contact.setIsSimsMeContact(true);
        contact.setMandant(RuntimeConfig.getMandant());
        contact.setIsFirstContact(false);
        contact.setState(Contact.STATE_HIGH_TRUST);

        String publicKey = companyContact.getPublicKey();
        if (!StringUtil.isNullOrEmpty(publicKey)) {
            contact.setPublicKey(publicKey);
        }
    }

    @Override
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
        final boolean hasChanges = super.saveContactInformation(contact, lastName, firstName, phone, email, emailDomain, department, oooStatus, imageBytes, trustState, forceUpdate);

        // Save on change
        if (hasChanges
                // if it's not your own contact
                && !StringUtil.isEqual(Contact.CLASS_OWN_ACCOUNT_ENTRY, contact.getClassEntryName())
                // not if it's a private contact or hidden
                && !(StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, contact.getClassEntryName()) && contact.getIsHidden())
                // when it has a simsmeid
                && !StringUtil.isNullOrEmpty(contact.getSimsmeId())
                // when it has a guid
                && !StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
            saveContactToFtsDatabase(contact, false);
        }
        return hasChanges;
    }

    private boolean saveAttributesToContact(@NonNull Contact contact, @NonNull CompanyContact companyContact)
            throws LocalizedException {
        String firstName = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
        String lastName = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_LASTNAME);
        String email = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_EMAIL);
        String phone = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_PHONE);
        String department = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_DEPARTMENT);

        return saveContactInformation(contact, lastName, firstName, phone, email, null, department, null, null, Contact.STATE_HIGH_TRUST, false);
    }

    public Contact createHiddenContactForCompanyContact(@NonNull final CompanyContact companyContact)
            throws LocalizedException {
        Contact contact = getContactForCompanyContact(companyContact);

        if (contact == null) {
            contact = new Contact();
        }

        setMetaAttributesToContact(contact, companyContact);

        saveAttributesToContact(contact, companyContact);

        return contact;
    }

    public boolean existsFtsDatabase() {
        return FtsDatabaseHelper.existsFtsDatabase(mApplication);
    }

    @Override
    public void openFTSDatabase(final GenericActionListener<Void> listener) {
        mFtsDbHelper.openFtsDatabase(new FtsDatabaseHelper.FtsDatabaseOpenListener() {
            @Override
            public void ftsDatabaseIsOpen() {
                if (listener != null) {
                    listener.onSuccess(null);
                }
            }

            @Override
            public void ftsDatabaseHasError(LocalizedException e) {
                if (e != null) {
                    LogUtil.e(this.getClass().getSimpleName(), "DB open error", e);
                }
                if (listener != null) {
                    listener.onFail("DB open error", e != null ? e.getIdentifier() : LocalizedException.UNKNOWN_ERROR);
                }
            }
        });
    }

    @Override
    public void createAndFillFtsDB(boolean onlyCreate)
            throws LocalizedException {
        final String initialPassword = GuidUtil.generatePassToken();
        mFtsDbHelper.createFtsDatabase(initialPassword);

        String encryptedPassword = mApplication.getKeyController().encryptStringWithDeviceKeyToBase64String(initialPassword);
        mApplication.getPreferencesController().setEncryptedFtsDatabasePassword(encryptedPassword);

        if (!onlyCreate) {
            fillFtsDB(true);
        }
    }

    @Override
    public void fillFtsDB(final boolean isInitialInsert) {
        ArrayList<String> importedGuids = new ArrayList<>();

        QueryBuilder<Contact> queryBuilder = getDao().queryBuilder();

        Account account = mApplication.getAccountController().getAccount();
        if (account != null) {
            queryBuilder.where(ContactDao.Properties.AccountGuid.notEq(account.getAccountGuid()));
        }

        queryBuilder.where(ContactDao.Properties.AccountGuid.isNotNull(), ContactDao.Properties.IsSimsMeContact.eq(true));

        ArrayList<Contact> rc = (ArrayList<Contact>) queryBuilder.build().forCurrentThread().list();

        if (rc != null && rc.size() > 1) {
            boolean silentExceptionHasSend = false;
            for (Contact contact : rc) {
                try {
                    String classEntry = contact.getClassEntryName();
                    if (classEntry == null) {
                        continue;
                    }
                    switch (classEntry) {
                        case Contact.CLASS_PRIVATE_ENTRY: {
                            if (!contact.getIsHidden() && !contact.isDeletedHidden()) {
                                saveContactToFtsDatabase(contact, isInitialInsert);
                                importedGuids.add(contact.getAccountGuid());
                            }
                            break;
                        }
                        case Contact.CLASS_COMPANY_ENTRY:
                        case Contact.CLASS_DOMAIN_ENTRY: {
                            if (!contact.isDeletedHidden()) {
                                saveContactToFtsDatabase(contact, isInitialInsert);
                                importedGuids.add(contact.getAccountGuid());
                            }
                            break;
                        }
                    }
                } catch (LocalizedException e) {
                    if (!silentExceptionHasSend) {
                        LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                        silentExceptionHasSend = true;
                    }
                    LogUtil.w(TAG, "fillFtsDb", e);
                }
            }
        }

        List<CompanyContact> companyContacts = mCompanyContactDao.loadAll();

        if (companyContacts == null || companyContacts.size() < 1) {
            return;
        }

        for (CompanyContact cc : companyContacts) {
            if (cc.getAccountGuid() != null && importedGuids.indexOf(cc.getAccountGuid()) == -1) {
                try {
                    saveCompanyContactToFtsDatabase(cc, isInitialInsert);
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, "fillFtsDb", e);
                }
            }
        }
    }

    @Override
    public void saveContactToFtsDatabase(@NonNull final Contact contact, final boolean isInitialInsert)
            throws LocalizedException {
        if (StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "account guid is null");
        }

        saveContactAttributesToFtsDatabase(contact.getAccountGuid(), contact.getSimsmeId(),
                contact.getFirstName(), contact.getLastName(), contact.getEmail(),
                contact.getPhoneNumber(), contact.getDepartment(),
                contact.getState() != null ? contact.getState() : Contact.STATE_UNSIMSABLE,
                contact.getMandant(),
                contact.getClassEntryName(),
                isInitialInsert);
    }

    private void saveCompanyContactToFtsDatabase(@NonNull final CompanyContact contact, final boolean isInitialInsert)
            throws LocalizedException {
        if (StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "account guid is null");
        }

        String firstName = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
        String lastName = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_LASTNAME);
        String email = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_EMAIL);
        String phone = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_PHONE);
        String department = CompanyContactUtil.getInstance(mApplication).getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_DEPARTMENT);

        saveContactAttributesToFtsDatabase(contact.getAccountGuid(), contact.getAccountId(), firstName,
                lastName, email, phone, department, Contact.STATE_HIGH_TRUST, BuildConfig.SIMSME_MANDANT_BA,
                contact.getClassType(), isInitialInsert);
    }

    private void saveContactAttributesToFtsDatabase(@NonNull final String accountGuid,
                                                    final String simsmeId,
                                                    final String firstName,
                                                    final String lastName,
                                                    final String email,
                                                    final String phone,
                                                    final String department,
                                                    final int trustState,
                                                    final String tenant,
                                                    final String classType,
                                                    final boolean isInitialInsert)
            throws LocalizedException {
        if (mFtsDbHelper == null || !mFtsDbHelper.isDatabaseOpenAndDecrypted()) {
            throw new LocalizedException(LocalizedException.DB_IS_NOT_READY);
        }

        if (!isInitialInsert) {
            boolean haveToDelete;
            try {
                HashMap<String, String> contactValues = mFtsDbHelper.getUniqueEntry(FtsDatabaseHelper.COLUMN_ACCOUNT_GUID, accountGuid, new String[]{FtsDatabaseHelper.COLUMN_NAME});
                haveToDelete = contactValues != null && contactValues.size() > 0;
            } catch (LocalizedException e) {
                if (StringUtil.isEqual(LocalizedException.NO_UNIQUE_RESULT, e.getIdentifier())) {
                    haveToDelete = true;
                } else {
                    throw e;
                }
            }

            if (haveToDelete) {
                mFtsDbHelper.deleteEntry(FtsDatabaseHelper.COLUMN_ACCOUNT_GUID, accountGuid);
            }
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(FtsDatabaseHelper.COLUMN_ACCOUNT_GUID, accountGuid);

        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty(JsonConstants.TRUST_STATE, "" + trustState);

        StringBuilder sb = new StringBuilder();
        if (!StringUtil.isNullOrEmpty(firstName)) {
            sb.append(firstName).append(" ");
            jsonObject.addProperty(JsonConstants.FIRSTNAME, firstName);
            contentValues.put(FtsDatabaseHelper.COLUMN_FIRST_NAME, firstName);
        }
        if (!StringUtil.isNullOrEmpty(lastName)) {
            sb.append(lastName).append(" ");
            jsonObject.addProperty(JsonConstants.LASTNAME, lastName);
            contentValues.put(FtsDatabaseHelper.COLUMN_NAME, lastName);
        }
        if (!StringUtil.isNullOrEmpty(email)) {
            sb.append(email).append(" ");
            jsonObject.addProperty(JsonConstants.EMAIL, email);
        }
        if (!StringUtil.isNullOrEmpty(phone)) {
            sb.append(phone).append(" ");
            jsonObject.addProperty(JsonConstants.PHONE, phone);
        }
        if (!StringUtil.isNullOrEmpty(department)) {
            sb.append(department).append(" ");
            jsonObject.addProperty(JsonConstants.DEPARTMENT, department);
        }
        if (!StringUtil.isNullOrEmpty(simsmeId)) {
            sb.append(simsmeId).append(" ");
            jsonObject.addProperty(JsonConstants.ACCOUNT_ID, simsmeId);
        }

        if (!StringUtil.isNullOrEmpty(tenant)) {
            jsonObject.addProperty(JsonConstants.MANDANT, tenant);
            contentValues.put(FtsDatabaseHelper.COLUMN_MANDANT, tenant);
        }

        if (!StringUtil.isNullOrEmpty(classType)) {
            sb.append(classType);
            jsonObject.addProperty(JsonConstants.CLASS, classType);
            contentValues.put(FtsDatabaseHelper.COLUMN_CLASS_TYPE, classType);
        }

        contentValues.put(FtsDatabaseHelper.COLUMN_JSON_ATTRIBUTES, jsonObject.toString());
        contentValues.put(FtsDatabaseHelper.COLUMN_SEARCH_ATTRIBUTES, sb.toString());

        mFtsDbHelper.insertContact(contentValues);
    }

    public void searchContactsInFtsDb(@NonNull final String query, @NonNull final GenericActionListener<Cursor> searchListener) {
        if (mSearchFtsTask != null) {
            searchListener.onFail("", null);
        }

        mSearchFtsTask = new SearchFtsTask(new GenericActionListener<Cursor>() {
            @Override
            public void onSuccess(Cursor object) {
                mSearchFtsTask = null;
                searchListener.onSuccess(object);
            }

            @Override
            public void onFail(String message, String errorIdent) {
                mSearchFtsTask = null;
                searchListener.onFail(null, errorIdent);
            }
        });
        mSearchFtsTask.executeOnExecutor(SearchFtsTask.THREAD_POOL_EXECUTOR, query, null, null);
    }

    public void addLastUsedCompanyContact(@NonNull final CompanyContact companyContact) {
        mApplication.getPreferencesController().addLastUsedContactGuid(companyContact.getAccountGuid(),
                StringUtil.isEqual(Contact.CLASS_COMPANY_ENTRY, companyContact.getClassType())
                        ? IndexType.INDEX_TYPE_COMPANY : IndexType.INDEX_TYPE_DOMAIN);
    }

    public List<CompanyContact> getLastUsedCompanyContacts(final IndexType indexType) {
        String[] guids = mApplication.getPreferencesController().getLastUsedContactGuids(indexType);

        if (guids != null && guids.length > 0) {
            return getCompanyContactsByGuid(guids, indexType);
        }

        return new ArrayList<>();
    }

    @Override
    public Bitmap getFallbackImageByGuid(Context context,
                                         String contactGuid,
                                         int scaleSize)
            throws LocalizedException {
        Contact contact = getContactByGuid(contactGuid);
        if (contact == null) {
            CompanyContact companyContact = getCompanyContactWithAccountGuid(contactGuid);
            if (companyContact != null) {
                String firstName = CompanyContactUtil.getInstance(this.mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
                String lastNameName = CompanyContactUtil.getInstance(this.mApplication).getAttributeFromContact(companyContact, CompanyContact.COMPANY_CONTACT_LASTNAME);

                Contact tmpContact = new Contact(firstName, lastNameName);

                return getFallbackImageByContact(context, tmpContact);
            }
        }
        return getFallbackImageByContact(context, contact);
    }

    private SimpleArrayMap<String, String> listCompanyIndexFromBackendSync(@Nullable final String ifModifiedSince)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();
        final SimpleArrayMap<String, String> checksumMappingServer = new SimpleArrayMap<>();

        IBackendService.OnBackendResponseListener listIndexResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() <= 0) {
                    return;
                }

                for (JsonElement je : response.jsonArray) {
                    if (je == null || !je.isJsonObject()) {
                        continue;
                    }

                    JsonObject jo = je.getAsJsonObject();

                    String accountGuid = JsonUtil.stringFromJO(JsonConstants.GUID, jo);
                    String checksum = JsonUtil.stringFromJO(JsonConstants.CHECKSUM, jo);

                    if (!StringUtil.isNullOrEmpty(accountGuid) && !StringUtil.isNullOrEmpty(checksum)) {
                        checksumMappingServer.put(accountGuid, checksum);
                    }
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .listCompanyIndex(ifModifiedSince, listIndexResponseListener);

        if (rm.isError) {
            if (rm.responseException != null) {
                throw rm.responseException;
            }

            throw new LocalizedException(StringUtil.isNullOrEmpty(rm.errorIdent) ? LocalizedException.BACKEND_REQUEST_FAILED : rm.errorIdent, rm.errorMsg);
        }

        return checksumMappingServer;
    }

    @Override
    public ResponseModel loadCompanyIndexSync(final LoadCompanyContactsListener listener, boolean checkAllEntries) {
        final ResponseModel rm = new ResponseModel();
        final String ifModifiedSince = checkAllEntries ? null : mApplication.getPreferencesController().getLastCompanyIndexSyncTimeStamp();
        SimpleArrayMap<String, String> checksumMappingServer;

        final List<Boolean> setTimestamp = new ArrayList<>(1);
        setTimestamp.add(0, true);

        try {
            checksumMappingServer = listCompanyIndexFromBackendSync(ifModifiedSince);
        } catch (LocalizedException e) {
            setTimestamp.add(0, false);
            rm.isError = true;
            rm.responseException = e;
            return rm;
        }

        if (checksumMappingServer == null || checksumMappingServer.size() < 1) {
            return rm;
        }

        List<String> guidsToLoad = checkServerContactChecksumWithLocal(checksumMappingServer);

        if (guidsToLoad == null || guidsToLoad.size() < 1) {
            return rm;
        }

        if (listener != null) {
            listener.onLoadCompanyContactsSize(guidsToLoad.size());
        }

        if (guidsToLoad.size() > CONTACTS_DOWNLOAD_JUNK_SIZE) {
            int index = 0;
            do {
                List<String> subList = guidsToLoad.subList(index, guidsToLoad.size() > (index + CONTACTS_DOWNLOAD_JUNK_SIZE) ? index + CONTACTS_DOWNLOAD_JUNK_SIZE : guidsToLoad.size());
                index += CONTACTS_DOWNLOAD_JUNK_SIZE;
                ResponseModel responseModel = loadCompanyIndexGuids(StringUtil.getStringFromCollection(",", subList));

                if (listener != null) {
                    listener.onLoadCompanyContactsUpdate(index);
                }

                if (responseModel.isError) {
                    setTimestamp.add(0, false);
                    rm.isError = responseModel.isError;
                    rm.responseException = responseModel.responseException;
                    rm.errorIdent = responseModel.errorIdent;
                    rm.errorExceptionMessage = responseModel.errorExceptionMessage;
                    rm.errorMsg = responseModel.errorMsg;

                    break;
                }
            }
            while (index < guidsToLoad.size());
        } else {
            ResponseModel responseModel = loadCompanyIndexGuids(StringUtil.getStringFromCollection(",", guidsToLoad));

            if (responseModel.isError) {
                setTimestamp.add(0, false);
                rm.isError = responseModel.isError;
                rm.responseException = responseModel.responseException;
                rm.errorIdent = responseModel.errorIdent;
                rm.errorExceptionMessage = responseModel.errorExceptionMessage;
                rm.errorMsg = responseModel.errorMsg;
            }

            if (listener != null) {
                listener.onLoadCompanyContactsUpdate(guidsToLoad.size());
            }
        }

        if (setTimestamp.get(0)) {
            mApplication.getPreferencesController().setLastCompanyIndexSyncTimeStamp(DateUtil.getCurrentDate());
        }

        return rm;
    }

    private ResponseModel loadCompanyIndexGuids(@NonNull final String guids) {
        final ResponseModel rm = new ResponseModel();
        final List<String> accountsToLoad = new ArrayList<>();

        BackendService.withSyncConnection(mApplication)
                .getCompanyIndexEntries(guids, new IBackendService.OnBackendResponseListener() {
                    @Override
                    public void onBackendResponse(BackendResponse response) {
                        if (response.isError) {
                            rm.setError(response);
                            return;
                        }

                        if (response.jsonArray == null || response.jsonArray.size() <= 0) {
                            return;
                        }

                        SecretKey companyUserKey;
                        String ownGuid = mApplication.getAccountController().getAccount().getAccountGuid();

                        try {
                            companyUserKey = mApplication.getAccountController().getCompanyUserAesKey();
                        } catch (LocalizedException e) {
                            LogUtil.w(getClass().getSimpleName(), "company key not available", e);
                            rm.isError = true;
                            rm.responseException = e;

                            return;
                        }

                        for (JsonElement je : response.jsonArray) {
                            JsonObject entry = JsonUtil.searchJsonObjectRecursive(je, JsonConstants.COMPANY_INDEX_ENTRY);

                            if (entry == null) {
                                continue;
                            }

                            String guid = JsonUtil.stringFromJO(JsonConstants.GUID, entry);
                            String dataChecksum = JsonUtil.stringFromJO(JsonConstants.DATA_CHECKSUM, entry);
                            String data = JsonUtil.stringFromJO(JsonConstants.DATA, entry);
                            String accountGuid = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_GUID, entry);
                            String dateDeleted = JsonUtil.stringFromJO(JsonConstants.DATE_DELETED, entry);
                            String accountId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, entry);

                            if (StringUtil.isNullOrEmpty(guid) || StringUtil.isNullOrEmpty(dataChecksum) || StringUtil.isNullOrEmpty(data)) {
                                continue;
                            }

                            CompanyContact cc = getCompanyContactWithIndexGuid(guid);

                            if (!StringUtil.isNullOrEmpty(dateDeleted)) {
                                if (cc == null) {
                                    continue;
                                } else {
                                    deleteCompanyContact(cc);

                                    try {
                                        deleteFtsContact(cc.getAccountGuid());
                                    } catch (LocalizedException e) {
                                        LogUtil.w(getClass().getSimpleName(), "failed to delete CompanyContact from ftsDatabase", e);
                                        continue;
                                    }

                                    continue;
                                }
                            }

                            if (StringUtil.isNullOrEmpty(accountGuid)) {
                                if (cc != null) {
                                    deleteCompanyContact(cc);

                                    try {
                                        deleteFtsContact(cc.getAccountGuid());
                                    } catch (LocalizedException e) {
                                        LogUtil.w(getClass().getSimpleName(), "failed to delete CompanyContact from ftsDatabase", e);
                                        continue;
                                    }
                                }
                                continue;
                            }

                            if (cc != null) {
                                // most probably the user did register again
                                if (!StringUtil.isEqual(cc.getAccountGuid(), accountGuid)) {
                                    try {
                                        deleteFtsContact(cc.getAccountGuid());
                                    } catch (LocalizedException e) {
                                        LogUtil.w(getClass().getSimpleName(), "failed to delete CompanyContact from ftsDatabase", e);
                                        continue;
                                    }

                                    cc.setPublicKey("");
                                    cc.setAccountGuid("");
                                    cc.setAccountId("");
                                }
                            }

                            JsonObject dataJO = JsonUtil.getJsonObjectFromString(data);
                            if (dataJO == null) {
                                continue;
                            }

                            String ivString = JsonUtil.stringFromJO(JsonConstants.IV, dataJO);
                            String encData = JsonUtil.stringFromJO(JsonConstants.DATA, dataJO);

                            if (StringUtil.isNullOrEmpty(ivString) || StringUtil.isNullOrEmpty(encData)) {
                                continue;
                            }

                            if (cc == null) {
                                cc = new CompanyContact();
                                cc.setGuid(guid);
                            } else {
                                if (!StringUtil.isNullOrEmpty(dataChecksum) && StringUtil.isEqual(dataChecksum, cc.getChecksum())) {
                                    continue;
                                }
                            }

                            cc.setEncryptedData(encData.getBytes());
                            cc.setKeyIv(ivString);
                            cc.setAccountGuid(accountGuid);
                            cc.setChecksum(dataChecksum);
                            if (!StringUtil.isNullOrEmpty(accountId)) {
                                cc.setAccountId(accountId);
                            }

                            try {
                                if (StringUtil.isEqual(ownGuid, accountGuid)) {
                                    Contact ownContact = getOwnContact();
                                    if (ownContact == null) {
                                        fillOwnContactWithAccountInfos(null);
                                        ownContact = getOwnContact();
                                    }

                                    if (ownContact != null) {
                                        saveAttributesToContact(ownContact, cc);
                                    }

                                    continue;
                                }

                                Contact contact = getContactByGuid(accountGuid);
                                if (contact != null) {
                                    boolean hasChanges = false;
                                    if (!contact.getIsHidden()) {
                                        contact.setIsHidden(true);
                                        hasChanges = true;
                                    }

                                    if (!StringUtil.isEqual(CLASS_COMPANY_ENTRY, contact.getClassEntryName())) {
                                        contact.setClassEntryName(CLASS_COMPANY_ENTRY);
                                        hasChanges = true;
                                    }

                                    boolean savedContact = false;
                                    if (companyUserKey != null) {
                                        // save data
                                        savedContact = saveAttributesToContact(contact, cc);
                                    }

                                    if (!savedContact && hasChanges) {
                                        saveContactInformation(contact, null, null, null, null, null, null, null, null, -1, true);
                                    }
                                } else {
                                    if (!StringUtil.isNullOrEmpty(cc.getAccountId())) {
                                        saveCompanyContactToFtsDatabase(cc, false);
                                    }
                                }
                            } catch (LocalizedException e) {
                                LogUtil.w(getClass().getSimpleName(), "Company Data to Private Index failed", e);
                                rm.isError = true;
                                rm.responseException = e;

                                continue;
                            }

                            if (StringUtil.isNullOrEmpty(cc.getAccountId())) {
                                accountsToLoad.add(cc.getAccountGuid());
                            }

                            insertOrUpdateCompanyContact(cc);
                        }
                    }
                });

        if (accountsToLoad.size() > 0) {
            final String guidArray = StringUtil.getStringFromCollection(",", accountsToLoad);
            BackendService.withSyncConnection(mApplication)
                    .getAccountInfoBatch(guidArray, false, false, new IBackendService.OnBackendResponseListener() {
                        @Override
                        public void onBackendResponse(BackendResponse response) {
                            if (response.isError) {
                                rm.setError(response);
                                return;
                            }

                            if ((response.jsonArray == null) || (response.jsonArray.size() < 1)) {
                                return;
                            }

                            for (int i = 0; i < response.jsonArray.size(); i++) {
                                JsonElement jsonElement = response.jsonArray.get(i);
                                JsonObject jsonAccountObject = JsonUtil.searchJsonObjectRecursive(jsonElement, JsonConstants.ACCOUNT);

                                if (jsonAccountObject == null) {
                                    continue;
                                }

                                String guid = JsonUtil.stringFromJO(JsonConstants.GUID, jsonAccountObject);

                                if (StringUtil.isNullOrEmpty(guid)) {
                                    continue;
                                }

                                CompanyContact cc = getCompanyContactWithAccountGuid(guid);

                                if (cc == null) {
                                    continue;
                                }

                                String publicKey = JsonUtil.stringFromJO(JsonConstants.PUBLIC_KEY, jsonAccountObject);

                                if (!StringUtil.isNullOrEmpty(publicKey)) {
                                    cc.setPublicKey(publicKey);
                                }

                                String simsmeId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, jsonAccountObject);

                                if (!StringUtil.isNullOrEmpty(simsmeId)) {
                                    cc.setAccountId(simsmeId);
                                }

                                insertOrUpdateCompanyContact(cc);

                                try {
                                    saveCompanyContactToFtsDatabase(cc, false);
                                } catch (LocalizedException e) {
                                    LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                                }
                            }
                        }
                    });
        }

        return rm;
    }

    private List<String> checkServerContactChecksumWithLocal(@NonNull final SimpleArrayMap<String, String> checksumMappingServer) {
        List<CompanyContact> localContacts = getAllCompanyContacts(IndexType.INDEX_TYPE_COMPANY);
        SimpleArrayMap<String, String> checksumMappingLocal = null;
        if (localContacts != null && localContacts.size() > 0) {
            checksumMappingLocal = new SimpleArrayMap<>(localContacts.size());

            for (CompanyContact cc : localContacts) {
                if (!StringUtil.isNullOrEmpty(cc.getAccountGuid())) {
                    checksumMappingLocal.put(cc.getAccountGuid(), cc.getChecksum());
                }
            }
        }

        List<String> guidsToLoad = new ArrayList<>();

        for (int i = 0; i < checksumMappingServer.size(); i++) {
            String guid = checksumMappingServer.keyAt(i);
            if (StringUtil.isNullOrEmpty(guid)) {
                continue;
            }

            if (checksumMappingLocal == null) {
                guidsToLoad.add(guid);
                continue;
            }

            String serverChecksum = checksumMappingServer.valueAt(i);
            String localChecksum = checksumMappingLocal.get(guid);

            if (!StringUtil.isEqual(serverChecksum, localChecksum)) {
                guidsToLoad.add(guid);
            }
        }

        return guidsToLoad;
    }

    @Override
    public void ftsDatabaseIsOpen() {

    }

    @Override
    public void ftsDatabaseHasError(LocalizedException e) {
        LogUtil.e(TAG, e.getMessage(), e);
    }


    private void loadCompanyIndexAsyncInternally(final LoadCompanyContactsListener listener, boolean checkAllEntries, Executor executor) {
        if (mCompanyIndexTask != null) {
            mStartCompanyIndexTaskAgain = true;

            // It is possible that another thread has started the task already,
            // if we just return without calling onLoadSuccess, the caller could get
            // stuck as in the case of MdmRegisterActivity.step5(). Since the contacts
            // are being loaded already it should be just a fire and forget. It
            // should be safe to move on. We really need to re-design how company index is loaded.
            // This recursive call is very error prone and based on too many assumptions. (RB-848)
            if(listener != null) {
                listener.onLoadSuccess();
            }
            return;
        }

        final Handler mainHandler = (Looper.myLooper() == Looper.getMainLooper()) ? new Handler(Looper.getMainLooper()) : null;

        mCompanyIndexTask = new CompanyIndexTask(mApplication, checkAllEntries, new LoadCompanyContactsListener() {
            @Override
            public void onLoadSuccess() {
                mCompanyIndexTask = null;
                if (mStartCompanyIndexTaskAgain) {
                    mStartCompanyIndexTaskAgain = false;
                    loadCompanyIndexAsync();
                }

                if (listener != null) {
                    listener.onLoadSuccess();
                }
            }

            @Override
            public void onLoadFail(String message, String errorIdent) {
                mCompanyIndexTask = null;
                if (mStartCompanyIndexTaskAgain) {
                    mStartCompanyIndexTaskAgain = false;
                    loadCompanyIndexAsync();
                }

                if (listener != null) {
                    listener.onLoadFail(message, errorIdent);
                }
            }

            @Override
            public void onLoadCompanyContactsSize(final int size) {
                if (listener == null) {
                    return;
                }

                if (mainHandler != null) {
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            listener.onLoadCompanyContactsSize(size);
                        }
                    };
                    mainHandler.post(myRunnable);
                } else {
                    listener.onLoadCompanyContactsSize(size);
                }
            }

            @Override
            public void onLoadCompanyContactsUpdate(final int count) {
                if (listener == null) {
                    return;
                }

                if (mainHandler != null) {
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            listener.onLoadCompanyContactsUpdate(count);
                        }
                    };
                    mainHandler.post(myRunnable);
                } else {
                    listener.onLoadCompanyContactsUpdate(count);
                }
            }
        });

        if (executor != null) {
            mCompanyIndexTask.executeOnExecutor(executor, this);
        } else {
            mCompanyIndexTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, this);
        }
    }

    void loadCompanyIndexAsync() {
        loadCompanyIndexAsyncInternally(null, false, null);
    }

    public void loadCompanyIndexAsync(final LoadCompanyContactsListener listener, @Nullable final Executor executor) {
        loadCompanyIndexAsyncInternally(listener, false, executor);
    }

    /**
     * Need to check on receiving a PN if you already had a chat with the same contact. Ex. if a Company Contact exists
     *
     * @param contactGuid - id of the sender
     * @return true / false - is there a company contact available
     */
    @Override
    public boolean isFirstContact(@NonNull final String contactGuid) {
        CompanyContact cc = getCompanyContactWithAccountGuid(contactGuid);
        if (cc != null) {
            return false;
        }

        return super.isFirstContact(contactGuid);
    }

    public interface LicenseDaysLeftListener {
        void licenseDaysLeftHasCalculate(int daysLeft);
    }

    public interface TrialVoucherDaysLeftListener {
        void trialVoucherDaysLeftHasCalculate(int daysLeft);
    }

    public interface FtsSearchColumnIndex {
        int ROW_ID_CURSOR_POS = 0;
        int ACCOUNT_GUID_CURSOR_POS = 1;
        int JSON_ATTRIBUTES_CURSOR_POS = 2;
        int CLASS_TYPE_CURSOR_POS = 3;
    }

    private static class CompanyIndexTask extends AsyncTask<ContactControllerBusiness, Void, ResponseModel> {
        private final LoadCompanyContactsListener mListener;
        private final SimsMeApplication mApp;
        private final boolean mCheckAllEntries;

        CompanyIndexTask(@NonNull final SimsMeApplication app, boolean checkAllEntries, @NonNull final LoadCompanyContactsListener listener) {
            mListener = listener;
            mApp = app;
            mCheckAllEntries = checkAllEntries;
        }

        @Override
        protected ResponseModel doInBackground(ContactControllerBusiness... controllers) {
            if (controllers == null || controllers.length < 1) {
                return null;
            }
            ContactControllerBusiness controller = controllers[0];
            return controller.loadCompanyIndexSync(mListener, mCheckAllEntries);
        }

        @Override
        protected void onPostExecute(eu.ginlo_apps.ginlo.model.backend.ResponseModel responseModel) {
            if (responseModel == null) {
                mListener.onLoadFail(mApp.getResources().getString(R.string.contact_detail_load_contact_failed), "");
            } else if (responseModel.isError) {
                mListener.onLoadFail(responseModel.errorMsg, responseModel.errorIdent);
            } else {
                mListener.onLoadSuccess();
            }
        }
    }

    private static class SearchFtsTask extends AsyncTask<String, Void, Cursor> {
        final GenericActionListener<Cursor> mListener;
        LocalizedException mException;

        SearchFtsTask(@NonNull final GenericActionListener<Cursor> listener) {
            mListener = listener;
        }

        @Override
        protected Cursor doInBackground(String... strings) {
            if (strings == null || strings.length < 1) {
                mException = new LocalizedException(LocalizedException.NO_DATA_FOUND);
                return null;
            }

            String query = strings[0];

            FtsDatabaseHelper ftsDBHelper = FtsDatabaseHelper.getInstance();

            if (!ftsDBHelper.isDatabaseOpenAndDecrypted()) {
                mException = new LocalizedException(LocalizedException.DB_IS_NOT_READY);
                return null;
            }

            String[] columns = new String[4];
            columns[FtsSearchColumnIndex.ROW_ID_CURSOR_POS] = FtsDatabaseHelper.COLUMN_ROW_ID;
            columns[FtsSearchColumnIndex.ACCOUNT_GUID_CURSOR_POS] = FtsDatabaseHelper.COLUMN_ACCOUNT_GUID;
            columns[FtsSearchColumnIndex.JSON_ATTRIBUTES_CURSOR_POS] = FtsDatabaseHelper.COLUMN_JSON_ATTRIBUTES;
            columns[FtsSearchColumnIndex.CLASS_TYPE_CURSOR_POS] = FtsDatabaseHelper.COLUMN_CLASS_TYPE;

            String contactClassType = null;
            try {
                contactClassType = SimsMeApplication.getInstance().getAccountController().getManagementCompanyIsUserRestricted() ? Contact.CLASS_COMPANY_ENTRY : null;
            } catch (LocalizedException e) {
                LogUtil.w(TAG, "SearchFtsTask", e);
            }

            try {
                return ftsDBHelper.searchEntries(FtsDatabaseHelper.COLUMN_SEARCH_ATTRIBUTES, query, columns, null, FtsDatabaseHelper.COLUMN_NAME, true, contactClassType);
            } catch (LocalizedException e) {
                mException = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            if (mException != null) {
                mListener.onFail(null, mException.getIdentifier());
            } else {
                mListener.onSuccess(cursor);
            }
        }
    }

    private static class GetAddressInformationTask extends AsyncTask<Void, Void, ResponseModel> {
        final LoadCompanyContactsListener listener;

        GetAddressInformationTask(@NonNull final LoadCompanyContactsListener listener) {
            this.listener = listener;
        }

        @Override
        protected ResponseModel doInBackground(Void... params) {
            final SimsMeApplication application = SimsMeApplication.getInstance();
            final ContactControllerBusiness contactControllerBusiness = (ContactControllerBusiness) application.getContactController();
            final ResponseModel rm = new ResponseModel();

            IBackendService.OnBackendResponseListener responseListener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(BackendResponse response) {
                    if (response.isError) {
                        rm.setError(response);
                        return;
                    }

                    if (response.jsonArray == null || response.jsonArray.size() < 1) {
                        return;
                    }

                    try {
                        final String ownAccountGuid = application.getAccountController().getAccount().getAccountGuid();
                        final Map<String, CompanyContact> deletedGuids = new HashMap<>();

                        List<String> changedGuids = new ArrayList<>(response.jsonArray.size());
                        final Map<String, String> guidToChangedChecksumMapping = new HashMap<>();
                        List<CompanyContact> companyContacts = contactControllerBusiness.getAllCompanyContacts(IndexType.INDEX_TYPE_DOMAIN);

                        Map<String, String> guidToOldChecksumMapping = new HashMap<>();

                        for (CompanyContact item : companyContacts) {
                            deletedGuids.put(item.getAccountGuid(), item);
                            guidToOldChecksumMapping.put(item.getAccountGuid(), item.getChecksum());
                        }

                        boolean isDeviceManaged = application.getAccountController().isDeviceManaged();

                        for (JsonElement element : response.jsonArray) {
                            JsonObject jObj = element.getAsJsonObject();
                            if (jObj == null) {
                                continue;
                            }

                            final JsonElement guid = jObj.get("guid");
                            final JsonElement checksum = jObj.get("checksum");

                            if (guid != null && checksum != null) {
                                final String guidString = guid.getAsString();
                                deletedGuids.remove(guidString);

                                if (StringUtil.isEqual(ownAccountGuid, guidString) && !isDeviceManaged) {
                                    continue;
                                }

                                final String checksumString = checksum.getAsString();

                                final String oldChecksum = guidToOldChecksumMapping.get(guidString);

                                if (!StringUtil.isEqual(checksumString, oldChecksum)) {
                                    guidToChangedChecksumMapping.put(guidString, checksumString);
                                    changedGuids.add(guidString);
                                }
                            }
                        }

                        for (final String guid : deletedGuids.keySet()) {
                            final CompanyContact companyContact = deletedGuids.get(guid);

                            contactControllerBusiness.getCompanyContactDao().delete(companyContact);
                            contactControllerBusiness.deleteFtsContact(guid);
                        }

                        listener.onLoadCompanyContactsSize(changedGuids.size());

                        if (changedGuids.size() > CONTACTS_DOWNLOAD_JUNK_SIZE) {
                            int index = 0;
                            do {
                                List<String> subList = changedGuids.subList(index, changedGuids.size() > (index + CONTACTS_DOWNLOAD_JUNK_SIZE) ? index + CONTACTS_DOWNLOAD_JUNK_SIZE : changedGuids.size());
                                index += CONTACTS_DOWNLOAD_JUNK_SIZE;
                                ResponseModel responseModel = getAddressInformationBatch(StringUtil.getStringFromList(",", subList), guidToChangedChecksumMapping);

                                listener.onLoadCompanyContactsUpdate(index);

                                if (responseModel != null && responseModel.isError) {
                                    rm.isError = true;
                                    rm.responseException = responseModel.responseException;
                                    rm.errorIdent = responseModel.errorIdent;
                                    rm.errorExceptionMessage = responseModel.errorExceptionMessage;
                                    rm.errorMsg = responseModel.errorMsg;
                                    break;
                                }
                            }
                            while (index < changedGuids.size());
                        } else {
                            ResponseModel responseModel = getAddressInformationBatch(StringUtil.getStringFromList(",", changedGuids), guidToChangedChecksumMapping);

                            listener.onLoadCompanyContactsUpdate(changedGuids.size());

                            if (responseModel != null && responseModel.isError) {
                                rm.isError = true;
                                rm.responseException = responseModel.responseException;
                                rm.errorIdent = responseModel.errorIdent;
                                rm.errorExceptionMessage = responseModel.errorExceptionMessage;
                                rm.errorMsg = responseModel.errorMsg;
                            }
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    }
                }
            };

            BackendService.withSyncConnection(application)
                    .getAddressInformation(responseListener);

            return rm;
        }

        private ResponseModel getAddressInformationBatch(final String guids, final Map<String, String> guidToChangedChecksumMapping) {
            final SimsMeApplication application = SimsMeApplication.getInstance();
            final ContactControllerBusiness contactControllerBusiness = (ContactControllerBusiness) application.getContactController();
            final ResponseModel rm = new ResponseModel();

            IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                        rm.setError(response);
                        return;
                    }

                    if (response.jsonArray == null || response.jsonArray.size() < 1) {
                        return;
                    }

                    String ownGuid = application.getAccountController().getAccount().getAccountGuid();

                    for (JsonElement element : response.jsonArray) {
                        JsonObject addInfoObj = JsonUtil.searchJsonObjectRecursive(element, "AdressInformation");

                        if (addInfoObj == null) {
                            continue;
                        }

                        String guid = addInfoObj.has("guid") ? addInfoObj.get("guid").getAsString() : null;
                        String keyIv = addInfoObj.has("key-iv") ? addInfoObj.get("key-iv").getAsString() : null;
                        String publicKey = addInfoObj.has("publicKey") ? addInfoObj.get("publicKey").getAsString() : null;
                        String data = addInfoObj.has("data") ? addInfoObj.get("data").getAsString() : null;

                        if (StringUtil.isNullOrEmpty(keyIv)
                                || StringUtil.isNullOrEmpty(publicKey)
                                || StringUtil.isNullOrEmpty(data)) {
                            continue;
                        }

                        if (StringUtil.isEqual(guid, ownGuid)) {
                            try {
                                AccountController accountController = application.getAccountController();
                                CompanyContact companyContact = new CompanyContact(null, guid, null, keyIv, data.getBytes(), publicKey, null, null);
                                SecretKey secretKey = accountController.getDomainAesKey();

                                if (secretKey != null) {
                                    String firstName = companyContact.getEncryptedAttribute(secretKey, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
                                    String lastName = companyContact.getEncryptedAttribute(secretKey, CompanyContact.COMPANY_CONTACT_LASTNAME);
                                    String department = companyContact.getEncryptedAttribute(secretKey, CompanyContact.COMPANY_CONTACT_DEPARTMENT);

                                    Contact ownContact = contactControllerBusiness.getOwnContact();
                                    contactControllerBusiness.saveContactInformation(ownContact, lastName, firstName, null, null, null, department, null, null, -1, false);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                                rm.isError = true;
                                rm.responseException = e;
                                return;
                            }
                        } else {
                            CompanyContact companyContact = contactControllerBusiness.getCompanyContactWithAccountGuid(guid, IndexType.INDEX_TYPE_DOMAIN);
                            if (companyContact == null) {
                                companyContact = new CompanyContact(null, guid, guidToChangedChecksumMapping.get(guid), keyIv, data.getBytes(), publicKey, null, null);
                                contactControllerBusiness.getCompanyContactDao().insert(companyContact);
                                try {
                                    contactControllerBusiness.saveCompanyContactToFtsDatabase(companyContact, false);
                                } catch (LocalizedException e) {
                                    LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                                }
                            } else {
                                companyContact.setAccountGuid(guid);
                                companyContact.setKeyIv(keyIv);
                                companyContact.setEncryptedData(data.getBytes());
                                companyContact.setPublicKey(publicKey);
                                contactControllerBusiness.getCompanyContactDao().update(companyContact);

                                try {
                                    contactControllerBusiness.saveCompanyContactToFtsDatabase(companyContact, false);
                                } catch (LocalizedException e) {
                                    LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                                }
                            }

                            try {
                                Contact mContact = application.getContactController().getContactByGuid(guid);
                                if (mContact != null) {
                                    boolean hasChanges = false;
                                    if (!mContact.getIsHidden()) {
                                        mContact.setIsHidden(true);
                                        hasChanges = true;
                                    }
                                    // no new entry, since there is already a visible contact, that is probably already in fts db
                                    if (!StringUtil.isEqual(CLASS_DOMAIN_ENTRY, mContact.getClassEntryName())) {
                                        mContact.setClassEntryName(CLASS_DOMAIN_ENTRY);
                                        hasChanges = true;
                                    }

                                    boolean savedContact = contactControllerBusiness.saveAttributesToContact(mContact, companyContact);

                                    if (!savedContact && hasChanges) {
                                        contactControllerBusiness.saveContactInformation(mContact, null, null, null, null, null, null, null, null, -1, true);
                                    }
                                }
                            } catch (LocalizedException le) {
                                LogUtil.e(this.getClass().getName(), le.getMessage(), le);
                            }
                        }
                    }
                }
            };

            BackendService.withSyncConnection(application)
                    .getAddressInformationBatch(guids, onBackendResponseListener);

            return rm;
        }

        @Override
        protected void onPostExecute(ResponseModel rm) {
            if (rm != null && rm.isError) {
                listener.onLoadFail(StringUtil.isNullOrEmpty(rm.errorMsg) ? rm.errorMsg : rm.errorExceptionMessage, rm.errorIdent);
            } else {
                listener.onLoadSuccess();
            }
        }
    }

}
