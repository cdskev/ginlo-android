// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentRequestActivity;
import eu.ginlo_apps.ginlo.activity.register.device.DeviceLoginActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.fragment.BasePasswordFragment;
import eu.ginlo_apps.ginlo.fragment.PasswordFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

public class PasswordActivity
        extends NewBaseActivity
        implements OnPageChangeListener {

    private final static String TAG = PasswordActivity.class.getSimpleName();
    private final static int REGISTER_TYPE_CREATE_ACCOUNT = 1;
    public final static int REGISTER_TYPE_COUPLE_DEVICE = 2;
    public final static String REGISTER_TYPE = "RegisterType";

    private String mPassw1;
    private boolean isSimplePassword;
    private AccountController mAccountController;
    private boolean mPwEnabled = false;
    private ViewPager pager;
    private PasswordPagerAdapter pagerAdapter;
    private String mPassw2;
    private PasswordFragment mSetPasswordFragment;
    private PasswordFragment mConfirmPasswordFragment;
    private PreferencesController mPreferencesController;
    private int mRegisterType;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mRegisterType = getIntent().getIntExtra(REGISTER_TYPE, REGISTER_TYPE_CREATE_ACCOUNT);

        mAccountController = ((SimsMeApplication) getApplication()).getAccountController();
        mPreferencesController = ((SimsMeApplication) getApplication()).getPreferencesController();

        int pagerStupidPwFragmentPosition = 1;

        if (savedInstanceState != null) {
            LogUtil.d(TAG, "onCreateActivity: Have savedInstanceState: " + savedInstanceState.toString());
            isSimplePassword = savedInstanceState.getBoolean("isSimplePassword");
            mPwEnabled = savedInstanceState.getBoolean("mPwEnabled");

            if (isSimplePassword) {
                pagerStupidPwFragmentPosition = 0;
            }

            mSetPasswordFragment = (PasswordFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                    "mSetPasswordFragment");

            mConfirmPasswordFragment = (PasswordFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                    "mConfirmPasswordFragment");
        } else {
            LogUtil.d(TAG, "onCreateActivity: Have savedInstanceState: null");
            mSetPasswordFragment = new PasswordFragment();
            mSetPasswordFragment.setMode(PasswordFragment.SET_MODE);

            mConfirmPasswordFragment = new PasswordFragment();
            mConfirmPasswordFragment.setMode(PasswordFragment.CONFIRM_MODE);
        }

        final SwitchCompat enablePwSwitch = findViewById(R.id.switch_enable_password);
        if (enablePwSwitch != null) {
            enablePwSwitch.setChecked(mPwEnabled);
        }

        isSimplePassword = pagerStupidPwFragmentPosition == 0;

        OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = ((SwitchCompat) v).isChecked() ? 0 : 1;
                isSimplePassword = ((SwitchCompat) v).isChecked();

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
        pager.addOnPageChangeListener(this);
        pager.setOffscreenPageLimit(3);
        pagerAdapter = new PasswordPagerAdapter(getSupportFragmentManager(), mSetPasswordFragment,
                mConfirmPasswordFragment);
        pager.setAdapter(pagerAdapter);

        mPassw1 = "1";
        mPassw2 = "2";
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_set_password;
    }

    private void onEnablePwSwitchClicked(View v) {
        mPwEnabled = ((SwitchCompat) v).isChecked();
    }

    @Override
    protected void onResumeActivity() {

        //
        boolean bSimplePasswordAllowed = mPreferencesController.canUseSimplePassword();
        mSetPasswordFragment.setSimplePasswordAllowed(bSimplePasswordAllowed);

        final Handler handler = new Handler();

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mSetPasswordFragment.openKeyboard();
            }
        };
        handler.postDelayed(runnable, NumberConstants.INT_50);

        if ((mPassw1 == null) && (mPassw2 == null) && (pager != null) && (pager.getCurrentItem() > 0)) {
            onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean("isSimplePassword", isSimplePassword);

        getSupportFragmentManager().putFragment(bundle, "mSetPasswordFragment", mSetPasswordFragment);
        getSupportFragmentManager().putFragment(bundle, "mConfirmPasswordFragment", mConfirmPasswordFragment);
        bundle.putBoolean("mPwEnabled", mPwEnabled);

        super.onSaveInstanceState(bundle);
    }

    public void handleNextClick(View view) {
        int currentItem = pager.getCurrentItem();

        if (!checkPasswordInput(currentItem)) {
            return;
        }

        currentItem++;

        if (currentItem < pagerAdapter.getCount()) {
            pager.setCurrentItem(currentItem, true);

            if (currentItem == 1) {
                if (mPreferencesController.isPasswordOnStartRequired()) {
                    mPwEnabled = true;
                } else {

                    final SwitchCompat enablePwSwitch = findViewById(R.id.switch_enable_password);
                    enablePwSwitch.setVisibility(View.VISIBLE);
                    KeyboardUtil.toggleSoftInputKeyboard(this, getCurrentFocus(), false);

                    mConfirmPasswordFragment.openKeyboard();

                    enablePwSwitch.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onEnablePwSwitchClicked(enablePwSwitch);
                        }
                    });
                }
            }
        } else {
            if ((currentItem > 1) && !StringUtil.isEqual(mPassw1, mPassw2)) {
                resetPasswordInputsForCurrentItem();

                AlertDialogWrapper dialog = DialogBuilderUtil.buildErrorDialog(this,
                        getResources().getString(R.string.registration_validation_passwordDoesNotMatch));

                dialog.show();
            } else {
                createAccount();
            }
        }
    }

    void createAccount() {
        Intent intent;
        LogUtil.d(TAG, "createAccount: mRegisterType = " + mRegisterType);
        if (mRegisterType == REGISTER_TYPE_CREATE_ACCOUNT) {
            intent = new Intent(PasswordActivity.this, IdentRequestActivity.class);
            mAccountController.createAccountSetPassword(mPassw1, isSimplePassword, mPwEnabled);
        } else {
            intent = new Intent(PasswordActivity.this, DeviceLoginActivity.class);
            mAccountController.coupleDeviceSetPassword(mPassw1, isSimplePassword, mPwEnabled);
        }
        clearPasswords();
        LogUtil.d(TAG, "createAccount: startActivity " + intent.getComponent().getShortClassName());
        startActivity(intent);
    }

    private boolean checkPasswordInput(int currentItem) {
        Fragment fragment = pagerAdapter.getItem(currentItem);

        if (fragment instanceof PasswordFragment) {
            if (currentItem == 0) {
                if (isSimplePassword) {
                    mPassw1 = getPassword(currentItem);
                    if ((mPassw1 == null) || (mPassw1.length() < 4)) {
                        AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                                getString(R.string.registration_validation_pinIsTooShort));

                        alert.show();
                        return false;
                    }
                } else {
                    mPassw1 = getPassword(currentItem);
                    if ((mPassw1 == null) || StringUtil.isNullOrEmpty(mPassw1)) {
                        AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                                getString(R.string.registration_validation_passwordNotSet));

                        alert.show();
                        return false;
                    }
                }
                String errorMsg = mPreferencesController.checkPassword(mPassw1);
                if (errorMsg != null) {
                    AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this, errorMsg);

                    alert.show();
                    return false;
                }
            } else if (currentItem == 1) {
                mPassw2 = getPassword(currentItem);

                if (!StringUtil.isEqual(mPassw1, mPassw2)) {
                    resetPasswordInputsForCurrentItem();

                    AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                            getString(R.string.registration_validation_passwordDoesNotMatch));

                    alert.show();
                    return false;
                }
            }
        }
        return true;
    }

    private String getPassword(int currentItem) {
        Fragment fragment = pagerAdapter.getItem(currentItem);

        if (fragment instanceof PasswordFragment) {
            String pw = null;
            BasePasswordFragment childFragment = ((PasswordFragment) fragment).getPasswordFragment();

            if (childFragment != null) {
                pw = childFragment.getPassword();

            }
            return pw;
        }
        return null;
    }

    private void clearPasswords() {
        mPassw1 = null;
        mPassw2 = null;
        resetPasswordInputs();
    }

    @Override
    public void onPageSelected(int position) {
        //
    }

    @Override
    public void onPageScrolled(int position,
                               float positionOffset,
                               int positionOffsetPixels) {
        if (position > 0 && !checkPasswordInput(0)) {
            pager.setCurrentItem(0, true);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_SETTLING) {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(this.getCurrentFocus(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (pager.getCurrentItem() > 0) {
            navigateBack();
        } else {
            super.onBackPressed();
        }
    }

    private void navigateBack() {
        clearPasswords();

        int currentItem = pager.getCurrentItem() - 1;

        currentItem = (currentItem < 0) ? 0 : currentItem;
        pager.setCurrentItem(currentItem, true);
    }

    private void resetPasswordInputsForCurrentItem() {
        Fragment fragment = pagerAdapter.getItem(pager.getCurrentItem());

        if (fragment instanceof PasswordFragment) {
            ((PasswordFragment) fragment).clearInput();
        }
    }

    private void resetPasswordInputs() {
        LogUtil.i(TAG, "resetting simple pw inputs");

        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            Fragment fragment = pagerAdapter.getItem(i);
            BasePasswordFragment pwFragment = ((PasswordFragment) fragment).getPasswordFragment();

            pwFragment.clearInput();
        }
    }

    class PasswordPagerAdapter
            extends FragmentStatePagerAdapter {

        private final Fragment[] pages = new Fragment[2];

        PasswordPagerAdapter(FragmentManager fm,
                             PasswordFragment setPasswordFragment,
                             PasswordFragment confirmPasswordFragment) {
            super(fm);

            pages[0] = setPasswordFragment;
            pages[1] = confirmPasswordFragment;
        }

        @Override
        public Fragment getItem(int position) {
            return pages[position];
        }

        @Override
        public int getCount() {
            return pages.length;
        }
    }
}
