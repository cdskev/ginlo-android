// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import static eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity.EXTRA_DONT_FORWARD_TO_OVERVIEW;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.contracts.OnGetPurchasedProductsListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnRegisterVoucherListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ViewUtil;
import javax.inject.Inject;

/**
 * Created by SGA on 12.09.2016.
 */
public class EnterLicenceCodeActivity extends BaseActivity {
    private AccountController mAccountController;

    private EditText mText1;
    private EditText mText2;
    private EditText mText3;
    private EditText mText4;

    private boolean mDontForwardIfLicenceIsAboutToExpire;

    @Inject
    public AppConnectivity appConnectivity;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        final SimsMeApplicationBusiness application = (SimsMeApplicationBusiness) getApplicationContext();

        mAccountController = application.getAccountController();

        mText1 = findViewById(R.id.enter_4x4_code_edittext_1);
        mText2 = findViewById(R.id.enter_4x4_code_edittext_2);
        mText3 = findViewById(R.id.enter_4x4_code_edittext_3);
        mText4 = findViewById(R.id.enter_4x4_code_edittext_4);

        mText1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mText2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mText3.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mText4.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        ViewUtil.createTextWatcher(this, null, mText1, mText2, 4);
        ViewUtil.createTextWatcher(this, mText1, mText2, mText3, 4);
        ViewUtil.createTextWatcher(this, mText2, mText3, mText4, 4);
        ViewUtil.createTextWatcher(this, mText3, mText4, null, 4);

        ViewUtil.createOnKeyListener(mText1, mText2);
        ViewUtil.createOnKeyListener(mText2, mText3);
        ViewUtil.createOnKeyListener(mText3, mText4);

        if (mAccountController.getAccount().getState() < Account.ACCOUNT_STATE_FULL) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }
        final Intent intent = getIntent();
        if (intent.getExtras() != null && intent.hasExtra(EXTRA_DONT_FORWARD_TO_OVERVIEW)) {
            mDontForwardIfLicenceIsAboutToExpire = intent.getBooleanExtra(EXTRA_DONT_FORWARD_TO_OVERVIEW, false);
        }
    }

    @Override
    public void onBackPressed() {
        if (mAccountController.getAccount().getState() >= Account.ACCOUNT_STATE_FULL) {
            super.onBackPressed();
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed, Toast.LENGTH_LONG).show();
            return;
        }

        showIdleDialog(R.string.enter_licence_wait_dialog);

        OnGetPurchasedProductsListener onRegisterVoucherListener = new OnGetPurchasedProductsListener() {

            @Override
            public void onGetPurchasedProductsSuccess() {
                try {
                    final Account account = mAccountController.getAccount();
                    if (account.getHasLicence()) {
                        showErrorLayout(false);

                        if (!mDontForwardIfLicenceIsAboutToExpire) {
                            if (account.getState() == Account.ACCOUNT_STATE_FULL) {
                                final Intent intent = new Intent(EnterLicenceCodeActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                                startActivity(intent);
                            } else if (account.getState() == Account.ACCOUNT_STATE_CONFIRMED) {
                                final Intent intent = new Intent(EnterLicenceCodeActivity.this, ShowSimsmeIdActivity.class);
                                startActivity(intent);
                            } else {
                                throw new IllegalStateException("EnterLicenceCodeActivity::GetPurchasedProducts: Account in illegal state. State = " + account.getState());
                            }
                        }
                    } else {
                        showErrorLayout(true);
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(EnterLicenceCodeActivity.this.getClass().getName(), "getHasLicence failed");
                } finally {
                    dismissIdleDialog();
                }
            }

            @Override
            public void onGetPurchasedProductsFail(String errorMessage) {
                DialogBuilderUtil.OnCloseListener onCloseListener;
                if (StringUtil.isEqual(errorMessage, LocalizedException.NO_ACCOUNT_ON_SERVER)) {
                    errorMessage = getResources().getString(R.string.notification_account_was_deleted);
                    onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                        @Override
                        public void onClose(int ref) {
                            KeyboardUtil.toggleSoftInputKeyboard(EnterLicenceCodeActivity.this, getCurrentFocus(), false);
                            getSimsMeApplication().getAccountController().deleteAccount();
                        }
                    };
                } else {
                    onCloseListener = null;
                }

                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(EnterLicenceCodeActivity.this, errorMessage, 0, onCloseListener).show();
            }
        };
        mAccountController.getPurchasedProducts(onRegisterVoucherListener);
        showIdleDialog(R.string.dialog_licence_check);
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_enter_4x4_code;
    }

    @Override
    protected void onResumeActivity() {
    }

    public void handleNextClick(final View view) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed, Toast.LENGTH_LONG).show();
            return;
        }

        final StringBuilder licenceCode = new StringBuilder();
        licenceCode.append(mText1.getText().toString());
        licenceCode.append("-");
        licenceCode.append(mText2.getText().toString());
        licenceCode.append("-");
        licenceCode.append(mText3.getText().toString());
        licenceCode.append("-");
        licenceCode.append(mText4.getText().toString());

        if (licenceCode.length() != 19) {
            DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.enter_licence_warning_code_to_short)).show();
        } else {
            showIdleDialog(R.string.enter_licence_wait_dialog);

            OnRegisterVoucherListener onRegisterVoucherListener = new OnRegisterVoucherListener() {

                @Override
                public void onRegisterVoucherSuccess() {

                    Account account = mAccountController.getAccount();
                    if (account.getState() == Account.ACCOUNT_STATE_FULL) {
                        ((ContactControllerBusiness) getSimsMeApplication().getContactController()).resetTrialVoucherDaysLeft();
                        final Intent intent = new Intent(EnterLicenceCodeActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                        startActivity(intent);
                    } else if (account.getState() == Account.ACCOUNT_STATE_CONFIRMED) {
                        final Intent intent = new Intent(EnterLicenceCodeActivity.this, ShowSimsmeIdActivity.class);
                        startActivity(intent);
                    } else {
                        throw new IllegalStateException("EnterLicenceCodeActivity::GetPurchasedProducts: Account in illegal state. State = " + account.getState());
                    }
                    dismissIdleDialog();
                }

                @Override
                public void onRegisterVoucherFail(String errorMessage) {
                    dismissIdleDialog();
                    final DialogBuilderUtil.OnCloseListener onCloseListener;

                    if (StringUtil.isEqual(errorMessage, getResources().getString(R.string.notification_account_was_deleted))) {
                        // Bug 41511 - SIMSme BA -Anwendung wird nach der Meldung "Account wurde am Server gelöscht" nicht zurückgesetzt
                        onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                            @Override
                            public void onClose(int ref) {
                                mAccountController.deleteAccount();
                            }
                        };
                    } else {
                        onCloseListener = null;
                    }
                    DialogBuilderUtil.buildErrorDialog(EnterLicenceCodeActivity.this, errorMessage, 0, onCloseListener).show();
                }
            };
            mAccountController.registerVoucher(licenceCode.toString().toUpperCase(), onRegisterVoucherListener);
        }
    }

    private void showErrorLayout(final boolean show) {
        final View errorLayout = findViewById(R.id.enter_4x4_code_top_warning);
        if (errorLayout != null) {
            if (show) {
                errorLayout.setVisibility(View.VISIBLE);
            } else {
                errorLayout.setVisibility(View.GONE);
            }
        }
    }
}
