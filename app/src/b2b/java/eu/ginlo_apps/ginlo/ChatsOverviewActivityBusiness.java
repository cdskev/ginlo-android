// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;

import java.io.File;

import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity;
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.contracts.AcceptOrDeclineCompanyManagementCallback;
import eu.ginlo_apps.ginlo.controller.contracts.OnCompanyLayoutChangeListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnCompanyLogoChangeListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ConfigureProgressViewHelper;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.DialogHelperBusiness;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

import static eu.ginlo_apps.ginlo.controller.ContactControllerBusiness.LICENSE_DAYS_LEFT_NO_VALUE;

public class ChatsOverviewActivityBusiness extends ChatsOverviewActivity implements
        OnCompanyLayoutChangeListener,
        OnCompanyLogoChangeListener,
        ContactControllerBusiness.LicenseDaysLeftListener,
        ContactControllerBusiness.TrialVoucherDaysLeftListener {

    private final static String TAG = ChatsOverviewActivityBusiness.class.getSimpleName();
    private Dialog mVerifyEmailDialog;
    private Dialog mManagementDialog;
    private int mLicenseDaysLeft = LICENSE_DAYS_LEFT_NO_VALUE;
    private boolean mFirstRun = true;
    private boolean mActvityWasStart;
    private boolean mNoticeCancelPressed;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        mShowToolbarLogo = true;
        super.onCreateActivity(savedInstanceState);
        final AccountController accountControllerBusiness = mAccountController;

        accountControllerBusiness.registerOnCompanyLayoutChangeListener(this);
        accountControllerBusiness.setOnCompanyLogoChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        final AccountController accountControllerBusiness = mAccountController;
        if (accountControllerBusiness != null)
            accountControllerBusiness.deregisterOnCompanyLayoutChangeListener(this);

        super.onDestroy();
    }

    private void checkDeactivateOooDialog() {
        if (mVerifyEmailDialog != null && mVerifyEmailDialog.isShowing()) {
            return;
        }

        try {
            final Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
            if (ownContact.isAbsent()) {

                final String message = getResources().getString(R.string.chats_overview_absence_alert);
                final String title = getResources().getString(R.string.chats_overview_absence_alert_title);
                final String positiveButton = getResources().getString(R.string.chats_overview_absence_alert_deactivate);
                final String negativeButton = getResources().getString(R.string.std_cancel);

                final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog,
                                        final int which) {
                        dialog.dismiss();

                        final Intent intent = new Intent(ChatsOverviewActivityBusiness.this, AbsenceActivity.class);
                        startActivity(intent);
                    }
                };

                final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog,
                                        final int which) {
                        dialog.dismiss();
                    }
                };

                DialogBuilderUtil.buildResponseDialog(this,
                        message,
                        true,
                        title,
                        positiveButton,
                        negativeButton,
                        positiveOnClickListener,
                        negativeOnClickListener).show();
            }
        } catch (final LocalizedException e) {
            LogUtil.w(ChatsOverviewActivityBusiness.TAG, e.getMessage(), e);
        }
    }

    private void checkEmailRequestState()
            throws LocalizedException {

        if (AccountController.PENDING_EMAIL_STATUS_WAIT_REQUEST.equals(mAccountController.getPendingEmailStatus())) {
            final String pendingEmailAddress = mAccountController.getPendingEmailAddress();

            if (!StringUtil.isNullOrEmpty(pendingEmailAddress)) {

                final String title = getResources().getString(R.string.pending_email_alert_request_title);
                final String text = getResources().getString(R.string.pending_email_alert_request_text);
                final String yes = getResources().getString(R.string.pending_email_alert_request_request);
                final String no = getResources().getString(R.string.pending_email_alert_request_abort);

                final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {

                        final Intent intent = new Intent(ChatsOverviewActivityBusiness.this, RegisterEmailActivity.class);
                        intent.putExtra(RegisterEmailActivity.EXTRA_PREFILLED_EMAIL_ADDRESS, pendingEmailAddress);

                        try {
                            Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
                            intent.putExtra(RegisterEmailActivity.EXTRA_PREFILLED_FIRST_NAME, ownContact.getFirstName());
                            intent.putExtra(RegisterEmailActivity.EXTRA_PREFILLED_LAST_NAME, ownContact.getLastName());

                        } catch (LocalizedException le) {
                            LogUtil.w(ChatsOverviewActivityBusiness.TAG, le.getMessage(), le);
                        }

                        startActivity(intent);
                    }
                };

                final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        try {
                            mAccountController.unsetPendingEmailStatus(true);
                        } catch (final LocalizedException e) {
                            LogUtil.w(ChatsOverviewActivityBusiness.TAG, e.getMessage(), e);
                        }
                    }
                };

                if (mAccountController.isDeviceManaged()) {
                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivityBusiness.this, text, title, yes, positiveClickListener).show();
                } else {
                    DialogBuilderUtil.buildResponseDialog(ChatsOverviewActivityBusiness.this, text, title, yes, no, positiveClickListener, negativeClickListener).show();
                }
            }
        }
    }

    private void checkEmailConfirmState()
            throws LocalizedException {
        if (AccountController.PENDING_EMAIL_STATUS_WAIT_CONFIRM.equals(mAccountController.getPendingEmailStatus())) {
            if (mVerifyEmailDialog == null) {
                final String title = getResources().getString(R.string.pending_email_alert_verify_title);
                final String text = getResources().getString(R.string.pending_email_alert_verify_text);
                final String yes = getResources().getString(R.string.pending_email_alert_verify_verify);
                final String no = getResources().getString(R.string.pending_email_alert_later);

                final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final Intent intent = new Intent(ChatsOverviewActivityBusiness.this, EnterEmailActivationCodeActivity.class);
                        startActivity(intent);
                        mVerifyEmailDialog = null;
                    }
                };

                final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        mVerifyEmailDialog = null;
                    }
                };

                final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildResponseDialog(ChatsOverviewActivityBusiness.this, text, title, yes, no, positiveClickListener, negativeClickListener);
                alertDialogWrapper.show();
                mVerifyEmailDialog = alertDialogWrapper.getDialog();
            } else if (!mVerifyEmailDialog.isShowing()) {
                mVerifyEmailDialog.show();
            }
        }
    }

    @Override
    protected void onResumeActivity() {
        super.onResumeActivity();
        colorizeToolbar();

        final ContactControllerBusiness contactController = (ContactControllerBusiness) getSimsMeApplication().getContactController();

        contactController.setTrialVoucherDaysLeftListener(this);
        LogUtil.i(TAG, "contactController: trial voucher days left: " + contactController.getTrialVoucherDaysLeft());
        licenseDaysLeftHasCalculate(contactController.getTrialVoucherDaysLeft());

        final int licenseDaysLeft = contactController.getLicenseDaysLeft();
        LogUtil.i(TAG, "contactController: license days left: " + licenseDaysLeft);

        if (licenseDaysLeft < BuildConfig.LICENSE_EXPIRATION_WARNING_DAYS) {
            contactController.setLicenseDaysLeftListener(this);
            licenseDaysLeftHasCalculate(licenseDaysLeft);
        }

        mActvityWasStart = false;

        try {
            if (mAccountController.haveToShowManagementRequest()) {
                showCompanyRequest();
            }
            checkEmailRequestState();
            checkEmailConfirmState();
            if (mFirstRun) {
                checkDeactivateOooDialog();
            }

            mFirstRun = false;
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!mActvityWasStart) {
            //fuehrt zur erneuten Anzeige des Linzenzhinweis, wenn die App aus dem Hintergrund geholt wird
            mNoticeCancelPressed = false;
            mLicenseDaysLeft = LICENSE_DAYS_LEFT_NO_VALUE;
        }
    }

    @Override
    protected void onPauseActivity() {
        super.onPauseActivity();
        ((ContactControllerBusiness) getSimsMeApplication().getContactController()).removeLicenseDaysLeftListener();
        ((ContactControllerBusiness) getSimsMeApplication().getContactController()).removeTrialVoucherDaysLeftListener();
    }

    protected void colorizeToolbar() {
        super.colorizeToolbar();
        if (getToolbar() != null && mShowToolbarLogo) {
            final FileUtil fileUtil = new FileUtil(getSimsMeApplication());
            final File mediaDir = fileUtil.getInternalMediaDir();
            if (mediaDir != null) {
                final String path = mediaDir.getPath() + "/" + AccountController.COMPANY_LOGO_FILENAME;
                if (!StringUtil.isNullOrEmpty(path) && new File(path).exists()) {
                    final Drawable toolbarLogo = Drawable.createFromPath(path);
                    setActionBarLogo(toolbarLogo);

                }
            }
        }
    }


    private void setActionBarLogo(Drawable toolbarLogo) {
        if (getToolbar() == null) return;
        final ImageView logoImageView = getToolbar().findViewById(R.id.toolbar_logo);
        if (logoImageView == null) {
            return;
        }
        if (toolbarLogo != null) {
            logoImageView.setImageDrawable(toolbarLogo);
            logoImageView.setVisibility(View.VISIBLE);
            setTitle(null);
        } else {
            logoImageView.setVisibility(View.GONE);
            setTitle(R.string.chats_title_chats);
        }
    }

    private void startNextScreenForRequiredManagementState(String managementState) {
        String firstName = null, lastName = null;
        try {
            Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
            firstName = ownContact.getFirstName();
            lastName = ownContact.getLastName();
        } catch (LocalizedException ex) {
            LogUtil.w(TAG, ex.getMessage(), ex);
        }
        router.startNextScreenForRequiredManagementState(managementState, firstName, lastName);
    }

    private void acceptOrDeclineCompanyManagement(final boolean accept) {
        showIdleDialog();

        mAccountController.acceptOrDeclineCompanyManagement(accept, new AcceptOrDeclineCompanyManagementCallback() {
            @Override
            public void onSuccess(final String mcState) {
                dismissIdleDialog();
                if (mAccountController.isManagementStateRequired(mcState)) {
                    startNextScreenForRequiredManagementState(mcState);
                } else {
                    ConfigureProgressViewHelper vh = new ConfigureProgressViewHelper(
                            ChatsOverviewActivityBusiness.this, new ConfigureProgressViewHelper.ConfigureProgressListener() {
                        @Override
                        public void onFinish() {
                            recreate();
                        }

                        @Override
                        public void onError(String errorMsg, String detailErrorMsg) {
                            DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                                @Override
                                public void onClose(int ref) {
                                    recreate();
                                }
                            };
                            String text = errorMsg + "\n" + detailErrorMsg;
                            DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivityBusiness.this, text, -1, onCloseListener).show();
                        }
                    });
                    mAccountController.startConfigureCompanyAccount(vh);
                }

                try {
                    mAccountController.resetTrialUsage();
                } catch (final LocalizedException e) {
                    LogUtil.w(TAG, e.getMessage(), e);
                }
            }

            @Override
            public void onFail(final String message) {
                dismissIdleDialog();

                String error = message;
                if (StringUtil.isNullOrEmpty(error)) {
                    error = getString(R.string.service_tryAgainLater);
                }

                DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivityBusiness.this, error);
            }
        });
    }

    @Override
    public void onCompanyLayoutChanged() {
        recreate();
        try {
            initFabMenu();
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }

    }

    @Override
    public void onCompanyLogoChanged(Bitmap img) {
        if (img == null) {
            setActionBarLogo(null);
        } else {
            setActionBarLogo(new BitmapDrawable(getResources(), img));
        }
    }

    private void showCompanyRequest() {
        if (mManagementDialog == null) {
            final DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    showIdleDialog();
                    acceptOrDeclineCompanyManagement(true);
                    mManagementDialog = null;
                }
            };

            final DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    showIdleDialog();
                    acceptOrDeclineCompanyManagement(false);
                    mManagementDialog = null;

                }
            };

            final AlertDialogWrapper managementRequestDialog = ((DialogHelperBusiness) DialogHelperBusiness.getInstance(getSimsMeApplication())).
                    getManagementRequestDialog(ChatsOverviewActivityBusiness.this, positiveListener, negativeListener);

            managementRequestDialog.show();
            mManagementDialog = managementRequestDialog.getDialog();

        } else if (!mManagementDialog.isShowing()) {
            mManagementDialog.show();
        }
    }

    @Override
    public void licenseDaysLeftHasCalculate(int daysLeft) {
        if (mActvityWasStart && mNoticeCancelPressed) {
            return;
        }

        if (mLicenseDaysLeft != daysLeft && daysLeft != LICENSE_DAYS_LEFT_NO_VALUE) {
            mLicenseDaysLeft = daysLeft;
            View noticeView = findViewById(R.id.chat_overview_notice_view_layout);

            if (mLicenseDaysLeft != 0) {
                if (noticeView != null) {
                    TextView infoTV = noticeView.findViewById(R.id.chat_overview_notice_tv);
                    int value = mLicenseDaysLeft < 0 ? 0 : mLicenseDaysLeft;
                    infoTV.setText(getString(R.string.chats_overview_warning_license, String.valueOf(value)));

                    noticeView.setVisibility(View.VISIBLE);
                }
            } else {
                try {
                    if (!mAccountController.haveToShowManagementRequest() || mManagementDialog == null || !mManagementDialog.isShowing()) {
                        final Intent intent = new Intent(this, PurchaseLicenseActivity.class);
                        intent.putExtra(PurchaseLicenseActivity.EXTRA_DONT_FORWARD_TO_OVERVIEW, true);
                        startActivity(intent);
                    }
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, "licenseDaysLeftHasCalculate", e);
                }
            }

        }
    }

    @Override
    public void onCloseButtonNoticeLayoutClick(View v) {
        View noticeView = findViewById(R.id.chat_overview_notice_view_layout);

        if (noticeView != null) {
            noticeView.setVisibility(View.GONE);
            mNoticeCancelPressed = true;
        }
    }

    @Override
    public void onButtonNoticeLayoutClick(View v) {
        onCloseButtonNoticeLayoutClick(null);
        final Intent intent = new Intent(this, PurchaseLicenseActivity.class);
        intent.putExtra(PurchaseLicenseActivity.EXTRA_DONT_FORWARD_TO_OVERVIEW, true);
        startActivity(intent);
    }

    @Override
    public void trialVoucherDaysLeftHasCalculate(int daysLeft) {
        licenseDaysLeftHasCalculate(daysLeft);
    }

    @Override
    public void startActivityForResult(final Intent intent,
                                       final int requestCode) {
        mActvityWasStart = true;
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    protected String getOwnStatusText(@NonNull Contact ownContact) {
        String absentText = "";
        try {
            absentText = ownContact.isAbsent() ? getString(R.string.peferences_absence_absent) :
                    getString(R.string.peferences_absence_present);
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }

        return absentText;
    }
}
