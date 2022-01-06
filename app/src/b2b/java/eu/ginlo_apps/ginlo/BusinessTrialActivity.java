// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import eu.ginlo_apps.ginlo.activity.register.EnterLicenceCodeActivity;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

/**
 * Created by SGA on 13.06.2017.
 */

public class BusinessTrialActivity extends BaseActivity {
    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_business_trial;
    }

    @Override
    protected void onResumeActivity() {
        //
    }

    /**
     * @param view click view
     */
    public void onEnterCodeClicked(final View view) {
        final Intent intent = new Intent(this, EnterLicenceCodeActivity.class);
        startActivity(intent);
    }

    /**
     * @param view click view
     */
    public void onTestNowClicked(final View view) {
        final GenericActionListener<Long> genericCompanyActionListener = new GenericActionListener<Long>() {
            @Override
            public void onSuccess(final Long usageInMillis) {
                dismissIdleDialog();
                try {
                    if (usageInMillis == null) {
                        final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildErrorDialog(BusinessTrialActivity.this, getResources().getString(R.string.action_failed));
                        alertDialogWrapper.show();
                    } else {
                        AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();
                        accountControllerBusiness.setTrialUsage(usageInMillis);

                        if (accountControllerBusiness.isValidTrial()) {
                            final Intent intent = new Intent(BusinessTrialActivity.this, RuntimeConfig.getClassUtil().getLoginActivityClass());
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildErrorDialog(BusinessTrialActivity.this, getResources().getString(R.string.business_trial_dialog_trial_expired));
                            alertDialogWrapper.show();
                        }
                    }
                } catch (final LocalizedException le) {
                    final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildErrorDialog(BusinessTrialActivity.this, le.getMessage());
                    alertDialogWrapper.show();
                }
            }

            @Override
            public void onFail(final String message, final String errorIdent) {
                dismissIdleDialog();
                if (!StringUtil.isNullOrEmpty(message)) {
                    final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildErrorDialog(BusinessTrialActivity.this, message);
                    alertDialogWrapper.show();
                }
            }
        };

        showIdleDialog();
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();
        accountControllerBusiness.registerTestVoucher(genericCompanyActionListener);

    }

    @Override
    public void onBackPressed() {
        //do nothing, kein zurueck moeglich
    }
}
