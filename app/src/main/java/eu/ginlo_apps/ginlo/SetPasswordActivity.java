// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.adapter.SetPasswordPagerAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.LoginController.PasswordChangeListener;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BasePasswordFragment;
import eu.ginlo_apps.ginlo.fragment.ComplexPasswordFragment;
import eu.ginlo_apps.ginlo.fragment.PasswordFragment;
import eu.ginlo_apps.ginlo.fragment.SimplePasswordFragment;
import eu.ginlo_apps.ginlo.greendao.Preference;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SetPasswordActivity
        extends BaseActivity {

    public static final String EXTRA_MODE = "SetPasswordActivity.mMode";
    public static final String MODE_FORCE_CHANGE_PW = "SetPasswordActivity.mMode.force";
    public static final String FORCE_PW_VALITATION_DIALOG = "SetPasswordActivity.mMode.force.dialog";
    private static final int SET_PASSWORD_INDEX = 0;
    private static final int CONFIRM_PASSWORD_INDEX = 1;

    private ViewPager pager;

    private SetPasswordPagerAdapter setPasswordPageAdapter;

    private boolean isSimplePassword;

    private String mPassw1;

    private String mPassw2;

    private boolean backActivated;

    private PreferencesController preferenceController;

    private AccountController accountController;
    private final PasswordChangeListener onPasswordChangedListener = new PasswordChangeListener() {
        @Override
        public void onPasswordChangeSuccess(String newPassword) {
            dismissIdleDialog();
            backActivated = true;
            try {
                if (isSimplePassword) {
                    preferenceController.setPasswordType(Preference.TYPE_PASSWORD_SIMPLE);
                } else {
                    preferenceController.setPasswordType(Preference.TYPE_PASSWORD_COMPLEX);
                }

                preferenceController.onPasswordChanged(newPassword);
            } catch (LocalizedException le) {
                LogUtil.e(this.getClass().getName(), le.getIdentifier(), le);
            }

            clearPasswords();
            setResult(RESULT_OK);

            if (getSimsMeApplication().getAppLifecycleController().getActivityStackSize() <= 1) {
                Intent nextIntent = new Intent(SetPasswordActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

                nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                startActivity(nextIntent);
            }
            finish();
        }

        @Override
        public void onPasswordChangeFail() {
            dismissIdleDialog();
            backActivated = true;
            clearPasswords();
            DialogBuilderUtil.buildErrorDialog(SetPasswordActivity.this,
                    getResources().getString(R.string.settings_password_changePassword_failed)).show();
        }
    };
    private PasswordFragment mSetPasswordFragment;
    private PasswordFragment mConfirmPasswordFragment;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        final Intent intent = getIntent();
        if (intent != null) {
            final String stringExtra = intent.getStringExtra(EXTRA_MODE);

            if (MODE_FORCE_CHANGE_PW.equals(stringExtra)) {
                mAfterCreate = true;

                final ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.hide();
                }
                backActivated = false;
            } else {
                backActivated = true;
            }
        }
        preferenceController = ((SimsMeApplication) getApplication()).getPreferencesController();
        accountController = ((SimsMeApplication) getApplication()).getAccountController();

        if (preferenceController.getHasSystemGeneratedPasword()) {
            setTitle(R.string.settings_password_setInitialPassword_title);
        }

        int pagerStupidPwFragmentPosition = 1;

        if (savedInstanceState != null) {
            isSimplePassword = savedInstanceState.getBoolean("isSimplePassword");
            if (isSimplePassword) {
                pagerStupidPwFragmentPosition = 0;
            }

            mSetPasswordFragment = (PasswordFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                    "mSetPasswordFragment");

            mConfirmPasswordFragment = (PasswordFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                    "mConfirmPasswordFragment");
        } else {
            mSetPasswordFragment = new PasswordFragment();
            mSetPasswordFragment.setMode(PasswordFragment.SET_MODE);

            mConfirmPasswordFragment = new PasswordFragment();
            mConfirmPasswordFragment.setMode(PasswordFragment.CONFIRM_MODE);
        }

        isSimplePassword = pagerStupidPwFragmentPosition == 0;

        OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = 0;

                position = ((androidx.appcompat.widget.SwitchCompat) v).isChecked() ? 0 : 1;
                isSimplePassword = ((androidx.appcompat.widget.SwitchCompat) v).isChecked();

                mSetPasswordFragment.setFragmentPosition(position);
                mConfirmPasswordFragment.setFragmentPosition(position);
                mSetPasswordFragment.updateFragment();
                mConfirmPasswordFragment.updateFragment();
                mSetPasswordFragment.openKeyboard();
            }
        };

        /* position der Fragmentliste initial setzen (die bestimmt, welches Fragment angezeigt wird (altlast)) */
        mSetPasswordFragment.setFragmentPosition(pagerStupidPwFragmentPosition);
        mConfirmPasswordFragment.setFragmentPosition(pagerStupidPwFragmentPosition);
        mSetPasswordFragment.updateFragment();
        mConfirmPasswordFragment.updateFragment();

        mSetPasswordFragment.setOnClickListener(onClickListener);

        pager = findViewById(R.id.set_password_pager);
        setPasswordPageAdapter = new SetPasswordPagerAdapter(getSupportFragmentManager(), mSetPasswordFragment,
                mConfirmPasswordFragment);
        pager.setAdapter(setPasswordPageAdapter);

        OnTouchListener onTouchListener = new OnTouchListener() {
            @Override
            public boolean onTouch(View v,
                                   MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        pager.requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        pager.requestDisallowInterceptTouchEvent(false);
                        break;
                    default:
                        //do nothing
                        break;
                }
                return true;
            }
        };
        pager.setOnTouchListener(onTouchListener);
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_set_password;
    }

    @Override
    protected void onResumeActivity() {
        if (mAfterCreate) {
            final Intent intent = getIntent();
            if (intent != null) {
                final Boolean forceValidatePwDialog = intent.getBooleanExtra(FORCE_PW_VALITATION_DIALOG, false);

                if (forceValidatePwDialog) {
                    DialogBuilderUtil.buildErrorDialog(this, getSimsMeApplication().getString(R.string.registration_validation_pwd_verification_fails)).show();
                    mAfterCreate = false;
                }
            }
        }
        //
        boolean bSimplePasswordAllowed = preferenceController.canUseSimplePassword();
        mSetPasswordFragment.setSimplePasswordAllowed(bSimplePasswordAllowed);
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean("isSimplePassword", isSimplePassword);

        getSupportFragmentManager().putFragment(bundle, "mSetPasswordFragment", mSetPasswordFragment);
        getSupportFragmentManager().putFragment(bundle, "mConfirmPasswordFragment", mConfirmPasswordFragment);

        super.onSaveInstanceState(bundle);
    }

    public void handleNextClick(View view) {
        int currentItem = pager.getCurrentItem();

        if (currentItem == SET_PASSWORD_INDEX) {
            mPassw1 = getPassword();
        } else if (currentItem == CONFIRM_PASSWORD_INDEX) {
            mPassw2 = getPassword();
        }

        if (StringUtil.isNullOrEmpty(mPassw1) && !StringUtil.isNullOrEmpty(mPassw2)) {
            DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                @Override
                public void onClose(int ref) {
                    finish();
                }
            };

            DialogBuilderUtil.buildErrorDialog(this, this.getString(R.string.settings_password_changePassword_failed), 0, onCloseListener).show();
            return;
        }

        if (isSimplePassword && (mPassw1.length() < 4)) {
            DialogBuilderUtil.buildErrorDialog(this, this.getString(R.string.registration_validation_pinIsTooShort))
                    .show();
            return;
        } else if (!isSimplePassword && (mPassw1.length() == 0)) {
            DialogBuilderUtil.buildErrorDialog(this, this.getString(R.string.registration_validation_passwordNotSet))
                    .show();
            return;
        }

        String error = preferenceController.checkPassword(mPassw1);
        if (error != null) {
            DialogBuilderUtil.buildErrorDialog(this, error)
                    .show();
            return;
        }

        currentItem++;
        if (currentItem < setPasswordPageAdapter.getCount()) {
            pager.setCurrentItem(currentItem);
        } else {
            if (!StringUtil.isEqual(mPassw1, mPassw2)) {
                DialogBuilderUtil.buildErrorDialog(this,
                        this.getString(R.string.registration_validation_passwordDoesNotMatch))
                        .show();
            } else {
                showIdleDialog(R.string.progress_dialog_change_password);
                backActivated = false;

                loginController.changePassword(mPassw2, onPasswordChangedListener);
            }
        }
    }

    public void handleSkipSetPassword(View view) {
        finish();
    }

    private void clearPasswords() {
        accountController.clearPassword();

        mPassw1 = null;
        mPassw2 = null;
    }

    private void resetPasswordInputs() {
        LogUtil.i(this.getClass().getName(), "resetting simple pw inputs");

        for (int i = 0; i < setPasswordPageAdapter.getCount(); i++) {
            Fragment fragment = setPasswordPageAdapter.getItem(i);
            BasePasswordFragment pwFragment = ((PasswordFragment) fragment).getPasswordFragment();

            pwFragment.clearInput();
        }
    }

    @Override
    public void onBackPressed() {
        LogUtil.i(this.getClass().getName(), "onBackPressed()");

        if (pager.getCurrentItem() == CONFIRM_PASSWORD_INDEX) {
            pager.setCurrentItem(SET_PASSWORD_INDEX);
            mPassw1 = "";

            resetPasswordInputs();

            return;
        }

        if (backActivated) {
            super.onBackPressed();
        }

        setResult(RESULT_CANCELED);
    }

    private String getPassword() {
        Fragment fragment = setPasswordPageAdapter.getItem(pager.getCurrentItem());

        if (fragment instanceof PasswordFragment) {
            BasePasswordFragment childFragment = ((PasswordFragment) fragment).getPasswordFragment();

            if (childFragment instanceof SimplePasswordFragment) {
                isSimplePassword = true;
                return childFragment.getPassword();
            } else if (childFragment instanceof ComplexPasswordFragment) {
                isSimplePassword = false;
                return childFragment.getPassword();
            }
        }
        return null;
    }
}
