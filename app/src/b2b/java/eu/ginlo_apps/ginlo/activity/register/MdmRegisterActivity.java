// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.SetPasswordActivity;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.contracts.HasCompanyManagementCallback;
import eu.ginlo_apps.ginlo.controller.contracts.OnCreateAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnGetPurchasedProductsListener;
import eu.ginlo_apps.ginlo.controller.contracts.SetAddressInfoListener;
import eu.ginlo_apps.ginlo.controller.contracts.UpdateAccountInfoCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.IManagedConfigUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import java.util.ArrayList;
import java.util.Locale;

public class MdmRegisterActivity extends BaseActivity {

    private Button mButton;

    private ArrayList<ProgressBar> mProgressBars;

    private ArrayList<ImageView> mImageViews;

    private ArrayList<TextView> mTextViews;

    private TextView mSublabelTextView4;

    private TextView mSublabelTextView5;

    private String mFirstName;

    private String mLastName;

    private String mLoginCode;

    private String mEmailAddress;

    private String mPassword;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {

        mButton = findViewById(R.id.mdm_register_button);

        mProgressBars = new ArrayList<>();
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_0));
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_1));
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_2));
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_3));
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_4));
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_5));
        mProgressBars.add((ProgressBar) findViewById(R.id.mdm_register_progressbar_6));

        mImageViews = new ArrayList<>();
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_0));
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_1));
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_2));
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_3));
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_4));
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_5));
        mImageViews.add((ImageView) findViewById(R.id.mdm_register_check_icon_6));

        mTextViews = new ArrayList<>();
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_0));
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_1));
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_2));
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_3));
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_4));
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_5));
        mTextViews.add((TextView) findViewById(R.id.mdm_register_textview_6));

        mSublabelTextView4 = findViewById(R.id.mdm_register_textview_4_sublabel);

        mSublabelTextView5 = findViewById(R.id.mdm_register_textview_5_sublabel);

        step1();
    }

    private ContactControllerBusiness getContactControllerBusiness() {
        return (ContactControllerBusiness) getSimsMeApplication().getContactController();
    }

    private AccountController getAccountControllerBusiness() {
        return getSimsMeApplication().getAccountController();
    }

    private AlertDialogWrapper mAlertDialog;

    private void handleServiceError(final @NonNull String errorMessage) {
        // Auswahl manuelle Registrierung oder neuer Versuch
        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        step1();
                    }
                });

            }
        };

        final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(MdmRegisterActivity.this, PasswordActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });

            }
        };

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getAccountControllerBusiness().resetCreateAccountRegisterPhone();

                getSimsMeApplication().safeDeleteAccount();

                if (mAlertDialog != null && mAlertDialog.getDialog().isShowing()) {
                    mAlertDialog.getDialog().dismiss();
                }
                mAlertDialog = DialogBuilderUtil.buildResponseDialog(MdmRegisterActivity.this, errorMessage,
                        getString(R.string.mdm_error),
                        getString(R.string.registration_automaticRegistration_error_tryAgain),
                        getString(R.string.registration_automaticRegistration_error_tryManual),
                        positiveOnClickListener,
                        negativeOnClickListener);
                mAlertDialog.show();
            }
        });

    }

    private void step1() {
        setActiveItem(0);
        //CreateAccountEx

        final IManagedConfigUtil managedConfigUtil = RuntimeConfig.getClassUtil().getManagedConfigUtil(getSimsMeApplication());
        mFirstName = managedConfigUtil.getFirstName();
        mLastName = managedConfigUtil.getLastName();
        mLoginCode = managedConfigUtil.getLoginCode();
        mEmailAddress = managedConfigUtil.getEmailAddress();
        mEmailAddress = mEmailAddress.toLowerCase(Locale.US);

        // Pasword vorbelegen
        mPassword = StringUtil.generatePassword(16);

        AccountController accountControllerBusiness = getAccountControllerBusiness();

        accountControllerBusiness.createAccountSetPassword(mPassword, false, false);

        final OnCreateAccountListener onCreateAccountListener = new OnCreateAccountListener() {

            @Override
            public void onCreateAccountSuccess() {
                getSimsMeApplication().getLoginController().loginCompleteSuccess(MdmRegisterActivity.this, null, null);
                getAccountControllerBusiness().configureNewAccount();
                step2();
            }

            @Override
            public void onCreateAccountFail(@Nullable String errorMsg, boolean haveToResetRegistration) {
                if (!StringUtil.isNullOrEmpty(errorMsg)) {
                    handleServiceError(errorMsg);
                } else {
                    handleServiceError(getResources().getString(R.string.create_account_failed));
                }
            }
        };

        accountControllerBusiness.handleMdmLogin(mFirstName, mLastName, mLoginCode, mEmailAddress, onCreateAccountListener);
    }

    private void step2() {
        setActiveItem(1);
        //validateMail

        IBackendService.OnBackendResponseListener listenerValidateMail = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.msgException != null) {
                    handleServiceError(response.msgException.getIdent());
                } else {
                    if (response.jsonArray != null
                            && response.jsonArray.size() != 0
                            && !response.jsonArray.get(0).isJsonNull()
                            && mEmailAddress.contains(response.jsonArray.get(0).getAsString())) {

                        String result = response.jsonArray.get(0).getAsString();
                        if (!StringUtil.isNullOrEmpty(result)) {
                            try {

                                final Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
                                ownContact.setDomain(result);
                                ownContact.setEmail(mEmailAddress);
                                ownContact.setFirstName(mFirstName);
                                ownContact.setLastName(mLastName);
                                getContactControllerBusiness().insertOrUpdateContact(ownContact);

                                step2a();
                            } catch (final LocalizedException e) {
                                LogUtil.w(AccountController.class.getSimpleName(), e.getMessage(), e);
                                handleServiceError(e.getIdentifier());
                            }
                        } else {
                            handleServiceError(getText(R.string.service_ERR_9001).toString());
                        }

                    } else {
                        handleServiceError(getText(R.string.service_ERR_9001).toString());
                    }
                }
            }
        };

        BackendService.withAsyncConnection(getSimsMeApplication())
                .validateMail(mEmailAddress, false, listenerValidateMail);
    }

    private void step2a() {
        //setAdressInformation
        SetAddressInfoListener setAddressInfoListener = new SetAddressInfoListener() {
            @Override
            public void onSuccess(String result) {
                step3();
            }

            @Override
            public void onFail(String errorMsg) {
                handleServiceError(errorMsg);
            }
        };
        getAccountControllerBusiness().setAddressInformation(setAddressInfoListener);

    }

    private void step3() {
        setActiveItem(2);
        //checkCompanyManagement

        final HasCompanyManagementCallback hasCompanyManagementCallback = new HasCompanyManagementCallback() {
            @Override
            public void onSuccess(String managementState) {
                if (!StringUtil.isEqual(managementState, AccountController.MC_STATE_ACCOUNT_ACCEPTED)) {
                    handleServiceError(getText(R.string.service_ERR_9002).toString());
                } else {
                    step3a();
                }
            }

            @Override
            public void onFail(String message) {
                handleServiceError(message);
            }
        };

        getAccountControllerBusiness().loadCompanyManagement(hasCompanyManagementCallback);
    }

    private CountDownTimer mCountDownTimer = null;

    private void step3a() {
        //getMessages
        if (mCountDownTimer != null) {
            return;
        }

        getSimsMeApplication().getMessageController().startGetNewMessages(false);
        //warten bis nachricht mit encryption-info zugestellt wurde...

        mCountDownTimer = new CountDownTimer(60000, 1000) {

            public void onTick(final long millisUntilFinished) {
                try {
                    final AccountController accountControllerBusiness = getAccountControllerBusiness();

                    final String salt = accountControllerBusiness.getMcEncryptionSalt();
                    final String seed = accountControllerBusiness.getMcEncryptionSeed();

                    if (!StringUtil.isNullOrEmpty(salt) && !StringUtil.isNullOrEmpty(seed)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onFinish();
                            }
                        });
                        cancel();
                    }

                } catch (final LocalizedException e) {
                    LogUtil.w(AccountController.class.getSimpleName(), e.getMessage(), e);
                }
            }

            public void onFinish() {
                try {
                    mCountDownTimer = null;
                    final AccountController accountControllerBusiness = getAccountControllerBusiness();
                    final String salt = accountControllerBusiness.getMcEncryptionSalt();
                    final String seed = accountControllerBusiness.getMcEncryptionSeed();

                    if (!StringUtil.isNullOrEmpty(salt) && !StringUtil.isNullOrEmpty(seed)) {
                        step4();
                    } else {
                        handleServiceError(getString(R.string.mdm_login_company_encryption_not_loaded));
                    }
                } catch (final LocalizedException e) {
                    LogUtil.w(AccountController.class.getSimpleName(), e.getMessage(), e);
                    handleServiceError(e.getIdentifier());
                }
            }
        };

        mCountDownTimer.start();
    }

    private void step4() {
        setActiveItem(3);
        //getCompanyConfig
        getAccountControllerBusiness().loadCompanyMDMConfig(new GenericActionListener<Void>() {
            @Override
            public void onSuccess(Void object) {
                step4a();
            }

            @Override
            public void onFail(final String message, final String errorIdent) {
                handleServiceError(message);

            }
        }, AsyncTask.THREAD_POOL_EXECUTOR);

    }

    private void step4a() {
        getAccountControllerBusiness().resetLayout();

        //getCompanyLayout

        GenericActionListener genericActionListener = new GenericActionListener<String>() {

            @Override
            public void onSuccess(String s) {
                step4b();
            }

            @Override
            public void onFail(String message, String errorIdent) {
                handleServiceError(message);
            }
        };

        getAccountControllerBusiness().getCompanyLayout(genericActionListener, AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void step4b() {
        //getCompanyLogo

        GenericActionListener genericActionListener = new GenericActionListener<Bitmap>() {

            @Override
            public void onSuccess(Bitmap object) {
                step5();
            }

            @Override
            public void onFail(String message, String errorIdent) {
                handleServiceError(message);
            }
        };

        getAccountControllerBusiness().getCompanyLogo(genericActionListener, AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void step5() {
        setActiveItem(4);
        //listCompanyIndex
        //getCompanyIndexEntries

        ContactController.LoadCompanyContactsListener loadCompanyContactsListener = new ContactController.LoadCompanyContactsListener() {
            int mSize;

            @Override
            public void onLoadSuccess() {
                if (mSublabelTextView4 != null) {
                    mSublabelTextView4.setVisibility(View.GONE);
                }
                step6();
            }

            @Override
            public void onLoadFail(String message, String errorIdent) {
                handleServiceError(message);
            }

            @Override
            public void onLoadCompanyContactsSize(int size) {
                mSize = size;
                if (mSublabelTextView4 != null) {
                    mSublabelTextView4.setVisibility(View.VISIBLE);
                    mSublabelTextView4.setText(0 + " " + getString(R.string.backup_restore_progress_secondary) + " " + size);
                }
            }

            @Override
            public void onLoadCompanyContactsUpdate(int count) {
                if (mSublabelTextView4 != null) {
                    mSublabelTextView4.setVisibility(View.VISIBLE);
                    mSublabelTextView4.setText(count + " " + getString(R.string.backup_restore_progress_secondary) + " " + mSize);
                }
            }
        };

        getContactControllerBusiness().loadCompanyIndexAsync(loadCompanyContactsListener, null);
    }

    private void step6() {
        setActiveItem(5);
        //getAdressInformations
        //getAdressInformationBatch

        ContactController.LoadCompanyContactsListener getAddressInformationsListener = new ContactController.LoadCompanyContactsListener() {
            int mSize;

            @Override
            public void onLoadSuccess() {
                if (mSublabelTextView5 != null) {
                    mSublabelTextView5.setVisibility(View.GONE);
                }
                step7();
            }

            @Override
            public void onLoadFail(String message, String errorIdent) {
                handleServiceError(getString(R.string.service_ERR_9003));
            }

            @Override
            public void onLoadCompanyContactsSize(int size) {
                if (mSublabelTextView5 != null) {
                    mSublabelTextView5.setVisibility(View.VISIBLE);
                    mSublabelTextView5.setText(0 + " " + getString(R.string.backup_restore_progress_secondary) + " " + size);
                }
            }

            @Override
            public void onLoadCompanyContactsUpdate(int count) {
                if (mSublabelTextView5 != null) {
                    mSublabelTextView5.setVisibility(View.VISIBLE);
                    mSublabelTextView5.setText(count + " " + getString(R.string.backup_restore_progress_secondary) + " " + mSize);
                }
            }
        };
        getContactControllerBusiness().getAddressInformation(getAddressInformationsListener, null);
    }

    private void step7() {
        // gekaufte Produkte abrufen
        setActiveItem(6);

        OnGetPurchasedProductsListener listener = new OnGetPurchasedProductsListener() {
            @Override
            public void onGetPurchasedProductsSuccess() {
                step7a();
            }

            @Override
            public void onGetPurchasedProductsFail(String errorMessage) {
                handleServiceError(errorMessage);
            }
        };
        getAccountControllerBusiness().getPurchasedProducts(listener);

    }

    private void step7a() {
        //setProfileInfo
        //setOnlineState usw...

        final UpdateAccountInfoCallback updateAccountInfoCallback = new UpdateAccountInfoCallback() {
            @Override
            public void updateAccountInfoFinished() {
                setActiveItem(7);
                mButton.setEnabled(true);
            }

            @Override
            public void updateAccountInfoFailed(String error) {
                handleServiceError(error);
            }
        };

        AccountController accountControllerBusiness = getAccountControllerBusiness();

        accountControllerBusiness.updateAccountInfo(mFirstName + " " + mLastName,
                null,
                null,
                mLastName,
                mFirstName, null,
                null,
                null,
                true,
                updateAccountInfoCallback
        );

        // keine SIMSme ID mehr anzeigen
        getSimsMeApplication().getPreferencesController().setSimsmeIdShownAtReg();

        accountControllerBusiness.setAccountStateToConfirmed();

        Account a = accountControllerBusiness.getAccount();
        a.setState(Account.ACCOUNT_STATE_FULL);
        accountControllerBusiness.saveOrUpdateAccount(a);

        ScreenDesignUtil.getInstance().reset(getSimsMeApplication());

        // Passwort setzen erzwingen
        if (getSimsMeApplication().getPreferencesController().checkPassword("") != null || getSimsMeApplication().getPreferencesController().isPasswordOnStartRequired()) {
            try {
                getSimsMeApplication().getPreferencesController().setForceNeedToChangePassword(true);
            } catch (LocalizedException le) {
                LogUtil.d(this.getClass().getName(), le.getIdentifier(), le);
            }
        } else {
            try {
                // Passwort Setzen Optional
                getSimsMeApplication().getPreferencesController().setHasSystemGeneratedPasword(true);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final Intent intent = new Intent(MdmRegisterActivity.this, SetPasswordActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);

                    }
                });

            } catch (LocalizedException le) {
                LogUtil.d(this.getClass().getName(), le.getIdentifier(), le);
            }

        }

    }

    private void setActiveItem(final int index) {

        if (index > 0) {
            for (int i = 0; i < index; i++) {
                final ProgressBar lastProgressbar = mProgressBars.get(i);
                final ImageView lastImageView = mImageViews.get(i);
                final TextView lastTextView = mTextViews.get(i);

                final int highColor = getResources().getColor(R.color.kColorSecLevelHigh);
                lastProgressbar.setVisibility(View.GONE);
                lastImageView.setVisibility(View.VISIBLE);
                final Drawable drawable = getResources().getDrawable(R.drawable.icon_process_check);
                lastImageView.setImageDrawable(drawable);
                lastTextView.setTextColor(highColor);
            }
        }

        if (index < 7) {
            for (int i = index + 1; i < 7; i++) {
                final ProgressBar progressBar = mProgressBars.get(i);
                final ImageView imageView = mImageViews.get(i);
                final TextView textView = mTextViews.get(i);

                final int mainContrastColor = getResources().getColor(R.color.mainContrast50);
                progressBar.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageDrawable(getResources().getDrawable(R.drawable.icon_process_upcoming));
                textView.setTextColor(mainContrastColor);

            }

            final ProgressBar actualProgressbar = mProgressBars.get(index);
            final ImageView actualImageView = mImageViews.get(index);
            final TextView actualTextView = mTextViews.get(index);

            final int mainContrastColor = getResources().getColor(R.color.mainContrast);
            actualProgressbar.setVisibility(View.VISIBLE);
            actualImageView.setVisibility(View.GONE);
            actualTextView.setTextColor(mainContrastColor);
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_mdm_register;
    }

    @Override
    protected void onResumeActivity() {

    }

    /**
     * handleNextClick
     *
     * @param view view
     */
    public void handleNextClick(final View view) {
        if (getSimsMeApplication().getPreferencesController().getForceNeedToChangePassword()) {
            final Intent intent = new Intent(MdmRegisterActivity.this, SetPasswordActivity.class);
            intent.putExtra(SetPasswordActivity.EXTRA_MODE, SetPasswordActivity.MODE_FORCE_CHANGE_PW);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);

        } else {
            final Intent intent = new Intent(MdmRegisterActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }

}
