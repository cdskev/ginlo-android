// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import dagger.android.AndroidInjection;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.MainActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.RecoverPasswordActivity;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.activity.register.ShowSimsmeIdActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.AccountOnServerWasDeleteListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnMessageReceivedListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BottomSheetGeneric;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.ResultContainer;
import eu.ginlo_apps.ginlo.theme.CmsThemeContextWrapper;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.ImageCache;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.MetricsUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ToolbarColorizeHelper;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.ProgressDialog;
import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

public abstract class BaseActivity
        extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, AccountOnServerWasDeleteListener {

    protected static final int TAKE_PICTURE_RESULT_CODE = 147;
    protected static final int ANIMATION_DURATION = 100;

    private static final String TAG = BaseActivity.class.getSimpleName();

    private static final String CHECK_IS_LOGOUT = "BaseActivity.checkIsLogout";
    private static final int ACTIONBAR_RIGHT_SIDE = R.id.action_bar_right_image_view;
    private static final String SAVED_RESULTS = "BaseActivity.savedResults";
    private static final String SAVED_INSTANCE_STATE = "BaseActivity.savedInstanceState";
    private static final int TOOLBAR_OPTIONS_MENU_ITEM_COUNT = 6;

    protected Animation mAnimationSlideIn;
    protected Animation mAnimationSlideOut;
    protected ResultContainer results;
    protected TextView mSecondaryTitle;
    protected LoginController loginController;
    protected boolean isActivityInForeground;
    private Toolbar mToolbar;
    protected View mToolbarOptionsLayout;
    protected boolean mBottomSheetOpen;
    protected BottomSheetGeneric mBottomSheetFragment;
    protected boolean mBottomSheetMoving = false;
    protected boolean mFinished = false;
    protected OnBottomSheetClosedListener mOnBottomSheetClosedListener;
    protected Intent callerIntent;
    protected Bundle mSaveInstanceState;
    protected boolean mExceptionWasThrownInOnCreate;
    protected boolean mAfterCreate;
    protected boolean mShowToolbarLogo = false;

    protected abstract void onCreateActivity(@Nullable Bundle savedInstanceState);

    protected abstract int getActivityLayout();

    protected abstract void onResumeActivity();

    private SimsMeApplication mApplication;
    private PreferencesController preferencesController;

    private TextView mTitleView;
    private boolean mCheckIsLogout;
    private View mRightImageView;
    private ImageView mProfileImage;
    private ProgressDialog mIdleDialog;
    private boolean mIsOptionsMenuSet = false;
    private PermissionUtil mPermissionUtil;
    private OnMessageReceivedListener mOnMessageReceivedListener;
    private Dialog mOwnAccountWasDeletedDialog;

    private boolean mDarkMode;
    private String mCurrentThemeName;

    @Inject
    public GinloAppLifecycle appLifecycle;

    private void forceLocale() {
        if (BuildConfig.FORCE_GERMAN) {
            getResources().getConfiguration().locale = new Locale("de");
        }
    }

    protected Toolbar getToolbar() {
        return mToolbar;
    }

    protected void setToolbar(Toolbar toolbar) {
        mToolbar = toolbar;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        forceLocale();
        mAfterCreate = true;

        callerIntent = getIntent();
        mSaveInstanceState = callerIntent.getBundleExtra(getSavedInstanceStateKey());

        if (mSaveInstanceState == null) {
            LogUtil.i(TAG, "mSaveInstanceState is null");
            mSaveInstanceState = savedInstanceState;
        }

        loginController = getSimsMeApplication().getLoginController();
        preferencesController = getSimsMeApplication().getPreferencesController();

        // Set UI theme according to preferences - only if there is no company layout!
        // Important: Must also handle theme over to ColorUtil instance before first themed inflates!
        if(ColorUtil.getInstance().hasLayoutModel(getSimsMeApplication())) {
            mCurrentThemeName = BuildConfig.DEFAULT_LIGHT_THEME;
            preferencesController.setDarkmodeEnabled(false);
            preferencesController.setThemeLocked(true);
            LogUtil.i(TAG, "Has layout model - ignore settings and use default theme: " + mCurrentThemeName);
        } else {
            mCurrentThemeName = preferencesController.getThemeName();
            preferencesController.setThemeLocked(false);
            LogUtil.i(TAG, "No layout model - use theme: " + mCurrentThemeName);
        }
        int themID = getResources().getIdentifier(mCurrentThemeName, "style", this.getPackageName());
        if(themID != 0) {
            setTheme(themID);
        }
        ColorUtil.getInstance().setCurrentTheme(getTheme());

        // TODO: DarkMode is obsolete, since we now call themes by name.
        // TODO: Change preference activity, replace darkmode switch with theme selector
        mDarkMode = preferencesController.getDarkmodeEnabled();

        super.onCreate(savedInstanceState);

        if (!RuntimeConfig.isScreenshotEnabled()) {
            getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);
        }

        if (mSaveInstanceState != null) {
            if (mSaveInstanceState.containsKey(SAVED_RESULTS)) {
                results = mSaveInstanceState.getParcelable(SAVED_RESULTS);
            }
        }

        if (getActivityLayout() != -1) {
            View view = ViewExtensionsKt.themedInflate(LayoutInflater.from(this), this, getActivityLayout(), null);
            setContentView(view);
        }

        initToolbar();

        isActivityInForeground = false;
        mCheckIsLogout = callerIntent.getBooleanExtra(CHECK_IS_LOGOUT, true);
        mExceptionWasThrownInOnCreate = isLogout();

        if (mExceptionWasThrownInOnCreate) {
            return;
        }

        onCreateActivity(mSaveInstanceState);
        createOnMessageReceivedListener();
    }

    protected void initToolbar() {
        View toolbar = findViewById(R.id.toolbar);
        if ((!(toolbar instanceof Toolbar))) {
            return;
        }

        mToolbar = (Toolbar) toolbar;
        setSupportActionBar(mToolbar);

        final ActionBar supportActionBar = getSupportActionBar();

        if (supportActionBar == null) {
            return;
        }
        supportActionBar.setDisplayHomeAsUpEnabled(true);
        supportActionBar.setHomeButtonEnabled(true);
        supportActionBar.setDisplayShowHomeEnabled(true);
        supportActionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white_24dp);

        for (int i = 0; i < mToolbar.getChildCount(); ++i) {
            final View view = mToolbar.getChildAt(i);
            if (view instanceof ImageButton) {
                final ImageButton navigationButton = (ImageButton) view;
                if (navigationButton.getDrawable() != null &&
                        navigationButton.getDrawable().equals(mToolbar.getNavigationIcon())) {
                    final int padding = MetricsUtil.dpToPx(this, 12);

                    navigationButton.setPadding(navigationButton.getPaddingLeft(), padding, navigationButton.getPaddingRight(), padding);
                }
            }
        }

        mToolbarOptionsLayout = findViewById(R.id.toolbar_options);
        mTitleView = mToolbar.findViewById(R.id.toolbar_title);
        setTitle(getTitle());
        supportActionBar.setTitle("");

        mRightImageView = mToolbar.findViewById(ACTIONBAR_RIGHT_SIDE);
        mProfileImage = mToolbar.findViewById(R.id.action_bar_image_view_profile_picture);
    }

    protected void colorizeToolbar() {
        if (mToolbar == null) {
            return;
        }
        ToolbarColorizeHelper.colorizeToolbar(mToolbar,
                ColorUtil.getInstance().getMainContrast80Color(getSimsMeApplication()),
                ColorUtil.getInstance().getToolbarColor(getSimsMeApplication()),
                this
        );
    }

    protected void colorizeActivity() {
        colorizeToolbar();
    }

    private void createOnMessageReceivedListener() {
        mOnMessageReceivedListener = new OnMessageReceivedListener() {
            @Override
            public void onMessageReceived(@NonNull Message message) {
                if (message.getPushInfo() == null || (message.getPushInfo().contains("sound") && !message.getPushInfo().contains("nosound"))) {
                    // Android seems not always saving the settings on the server.
                    String chatGuid = null;

                    //PreferencesController preferencesController = getSimsMeApplication().getPreferencesController();

                    if (message.getType() == Message.TYPE_PRIVATE) {
                        chatGuid = message.getFrom();
                    } else if (message.getType() == Message.TYPE_GROUP
                            || message.getType() == Message.TYPE_GROUP_INVITATION
                            || message.getType() == Message.TYPE_CHANNEL) {
                        chatGuid = message.getTo();
                    }
                    boolean playSound = false;
                    if (StringUtil.isNullOrEmpty(chatGuid)) {
                        return;
                    }

                    if (GuidUtil.isChatSingle(chatGuid) && preferencesController.getSoundForSingleChatEnabled()) {
                        playSound = true;
                    } else if (GuidUtil.isChatRoom(chatGuid) && preferencesController.getSoundForGroupChatEnabled()) {
                        playSound = true;
                    } else if (GuidUtil.isChatChannel(chatGuid)
                            && preferencesController.getSoundForChannelChatEnabled()
                            && !getSimsMeApplication().getChannelController().getDisableChannelNotification(chatGuid)) {
                        playSound = true;
                    } else if (GuidUtil.isChatService(chatGuid)
                            && preferencesController.getSoundForServiceChatEnabled()
                            && !getSimsMeApplication().getChannelController().getDisableChannelNotification(chatGuid)) {
                        playSound = true;
                    }

                    if (playSound) {
                        getSimsMeApplication().playMessageReceivedSound();
                    }
                }
            }
        };
    }

    protected boolean isLogout() {
        if (!mCheckIsLogout) {
            return false;
        }

        if (this instanceof LoginActivity) {
            return false;
        }
        LogUtil.i(TAG, "loginController state:" + loginController.getState());
        if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
            return true;
        } else if (loginController.getState().equals(LoginController.STATE_NO_ACCOUNT)) {
            AccountController ac = ((SimsMeApplication) getApplication()).getAccountController();
            if (ac.getAccountState() == Account.ACCOUNT_STATE_NO_ACCOUNT) {
                final Package aPackage = this.getClass().getPackage();
                if (aPackage != null) {
                    String classPackage = aPackage.getName();
                    if (!StringUtil.isNullOrEmpty(classPackage) &&
                            !classPackage.contains("register")) {
                        return true;
                    } else
                        return this.getClass() == RuntimeConfig.getClassUtil().getIdentConfirmActivityClass()
                                || this.getClass() == RuntimeConfig.getClassUtil().getInitProfileActivityClass()
                                || this.getClass() == ShowSimsmeIdActivity.class;
                }
            }
        }
        return false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        getSimsMeApplication().getAccountController().removeAccountOnServerWasDeleteListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            if (results != null) {
                LogUtil.i(TAG, "Save results instance state: " + results);
                outState.putParcelable(SAVED_RESULTS, results);
            }
            super.onSaveInstanceState(outState);
        } catch (IllegalStateException e) {
            LogUtil.e(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((loginController.getState().equals(LoginController.STATE_LOGGED_IN)
                || loginController.getState().equals(LoginController.STATE_NO_ACCOUNT)) && !mExceptionWasThrownInOnCreate) {
            onActivityPostLoginResult(requestCode, resultCode, data);
        } else {
            results = new ResultContainer(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void onActivityPostLoginResult(int requestCode, int resultCode, @Nullable Intent data) {
        results = null;
    }

    @Override
    protected void onStart() {
        super.onStart();

        LogUtil.i("BaseActivity.onStart() loginController state:", loginController.getState());

        if (mExceptionWasThrownInOnCreate) {
            return;
        }
        //TODO: This need a better solution. the base activity should not know who is inheriting from it
        if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)
                && (!(this instanceof LoginActivity) && !(this instanceof RecoverPasswordActivity))) {
            Intent intent = getIntentFromCallerIntent(RuntimeConfig.getClassUtil().getLoginActivityClass());

            intent.putExtra(LoginActivity.EXTRA_NEXT_ACTIVITY, this.getClass().getName());

            startActivity(intent);
        } else if (loginController.getState().equals(LoginController.STATE_NO_ACCOUNT)
                || loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
            callOnActivityPostLoginResult();
        }

        getSimsMeApplication().getAccountController().setAccountOnServerWasDeleteListener(this);
    }

    protected Intent getIntentFromCallerIntent(@NonNull Class<?> cls) {
        if (callerIntent != null) {
            Intent nextIntent = new Intent(callerIntent);
            nextIntent.setClass(this, cls);

            nextIntent.setFlags(0);

            return nextIntent;
        } else {
            return new Intent(this, cls);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mExceptionWasThrownInOnCreate && mAfterCreate) {

            if (loginController.getState().equals(LoginController.STATE_NO_ACCOUNT)) {
                appLifecycle.restartApp();
            } else {
                //start LoginActivity
                Intent intent = getIntentFromCallerIntent(RuntimeConfig.getClassUtil().getLoginActivityClass());

                intent.putExtra(LoginActivity.EXTRA_NEXT_ACTIVITY, this.getClass().getName());

                if (results != null) {
                    if (mSaveInstanceState != null) {
                        mSaveInstanceState.putParcelable(SAVED_RESULTS, results);
                    } else {
                        intent.putExtra(SAVED_RESULTS, results);
                    }
                }

                if (mSaveInstanceState != null) {
                    intent.putExtra(getSavedInstanceStateKey(), mSaveInstanceState);
                }

                startActivity(intent);
            }

            finish();

            return;
        }
        if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)
                && (!(this instanceof LoginActivity))) {
            return;
        }

        if (mOnMessageReceivedListener != null) {
            ((SimsMeApplication) getApplication()).getMessageController().addOnMessageReceivedListener(mOnMessageReceivedListener);
        }

        isActivityInForeground = true;
        colorizeActivity();
        onResumeActivity();
        mAfterCreate = false;
    }

    private String getSavedInstanceStateKey() {
        return SAVED_INSTANCE_STATE + this.getClass().getSimpleName();
    }

    private void callOnActivityPostLoginResult() {
        if (results != null) {
            LogUtil.i(TAG, "callOnActivityPostLoginResult with resultCode " + results.resultCode);
            onActivityPostLoginResult(results.requestCode, results.resultCode, results.data);
            results = null;
        } else {
            LogUtil.i(TAG, "callOnActivityPostLoginResult without results");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mExceptionWasThrownInOnCreate) {
            return;
        }
        isActivityInForeground = false;

        if (mOnMessageReceivedListener != null) {
            ((SimsMeApplication) getApplication()).getMessageController().removeOnOnMessageReceivedListener(mOnMessageReceivedListener);
        }

        onPauseActivity();
    }

    protected void onPauseActivity() {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        setIsMenuVisible(false);
        return true;
    }

    public boolean isActivityInForeground() {
        return isActivityInForeground;
    }

    protected void hideOrShowFragment(Fragment fragment, boolean showFragment, boolean checkBackground) {
        if (mFinished || (checkBackground && !((SimsMeApplication) getApplication()).getAppLifecycleController().isAppInForeground())) {
            return;
        }
        if (fragment != null) {
            try {
                FragmentManager fragmentManager = getSupportFragmentManager();

                if (fragmentManager != null) {
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

                    if (showFragment) {
                        fragmentTransaction.show(fragment);
                    } else {
                        fragmentTransaction.hide(fragment);
                    }
                    fragmentTransaction.commit();
                }
            } catch (IllegalStateException e) {
                LogUtil.w(BaseActivity.class.getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void setTitle(final CharSequence title) {
        scaleTitle();
        if (mTitleView != null) {
            mTitleView.setText(title);
            super.setTitle(title);
        }
    }

    protected void setIsMenuVisible(boolean isVisible) {
        mIsOptionsMenuSet = isVisible;
        scaleTitle();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideToolbarOptions();
        scaleTitle();
    }

    protected void scaleTitle() {
        if (mTitleView == null) {
            return;
        }

        int newWidth = (int) getResources().getDimension(R.dimen.toolbar_title_subtraction);

        if (mIsOptionsMenuSet) {
            newWidth += (int) getResources().getDimension(R.dimen.toolbar_options_icon_width);
        }
        if ((mProfileImage != null) && (mProfileImage.getVisibility() != View.GONE)) {
            newWidth += (int) getResources().getDimension(R.dimen.toolbar_chat_icon_width);
        }

        if ((mRightImageView != null) && (mRightImageView.getVisibility() != View.GONE)) {
            newWidth += (int) getResources().getDimension(R.dimen.toolbar_chat_icon_width);
        }

        mTitleView.setWidth(MetricsUtil.getDisplayMetrics(this).widthPixels - newWidth);

        if (mSecondaryTitle != null) {
            mSecondaryTitle.setWidth(MetricsUtil.getDisplayMetrics(this).widthPixels - newWidth);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackArrowPressed();
        return super.onOptionsItemSelected(item);
    }

    private void onBackArrowPressed() {
        KeyboardUtil.toggleSoftInputKeyboard(this, getCurrentFocus(), false);
        onBackPressed();
    }

    public void onBackArrowPressed(@SuppressWarnings("unused") View unused) {
        onBackArrowPressed();
    }

    @Override
    public void onBackPressed() {
        if (mBottomSheetOpen) {
            closeBottomSheet(null);
            return;
        }
        if (getSimsMeApplication().getAccountController().hasAccountFullState()
                && ((SimsMeApplication) getApplication()).getAppLifecycleController().getActivityStackSize() == 1
                && ((SimsMeApplication) getApplication()).getAppLifecycleController().getTopActivity() instanceof BaseChatActivity) {
            //BUG 36687 - Zurück in Kanal schließt App wenn Kanal per Pushnachricht geöffnet wurde
            appLifecycle.restartFromIntent(
                    new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass())
            );
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        mFinished = true;
        super.finish();
    }

    @Override
    protected void onDestroy() {
        mFinished = true;

        /* KS TEST
        // TODO: Find out where to delete the image cache. This is *not* the right place, because
        // it is responsible for throwing state exceptions e.g. after device sync.
        FragmentManager fm = getSupportFragmentManager();
        if (fm != null) {
            ImageCache.deleteImageCache(fm);
        }
        */
        super.onDestroy();
    }

    protected void setRightActionBarImage(int drawableId,
                                          OnClickListener clickListener,
                                          String contentDescription, int customImageColor) {
        setActionBarImage(ACTIONBAR_RIGHT_SIDE, drawableId, contentDescription, customImageColor);
        setActionBarListener(ACTIONBAR_RIGHT_SIDE, clickListener);
    }

    protected void removeRightActionBarImage() {
        setActionBarImage(BaseActivity.ACTIONBAR_RIGHT_SIDE, -1, null, -1);
    }

    protected void setActionBarImage(int side, int drawableId, String contentDescription, int customImgColor) {
        final ImageView imageView = mToolbar.findViewById(side);

        if (imageView == null) {
            return;
        }

        if (drawableId > -1) {
            imageView.setVisibility(View.VISIBLE);

            imageView.setColorFilter(customImgColor == -1
                    ? ColorUtil.getInstance().getMainContrast80Color(getSimsMeApplication())
                    : customImgColor, PorterDuff.Mode.SRC_ATOP);

            imageView.setImageResource(drawableId);
        } else {
            imageView.setVisibility(View.GONE);
        }
        if (!StringUtil.isNullOrEmpty(contentDescription)) {
            imageView.setContentDescription(contentDescription);
        }
    }

    protected void setActionBarListener(int side,
                                        OnClickListener clickListener) {
        final ImageView imageView = mToolbar.findViewById(side);
        if (imageView != null && clickListener != null) {
            imageView.setOnClickListener(clickListener);
        }
    }

    protected void setTitleView(TextView view) { mTitleView = view; }

    protected TextView getTitleView() {
        return mTitleView;
    }

    public void handleCloseBottomSheetClick(final View view) {
        closeBottomSheet(null);
    }

    protected void closeBottomSheet(final OnBottomSheetClosedListener listener) {
        if (mBottomSheetFragment == null) {
            if (listener != null) {
                listener.onBottomSheetClosed(mBottomSheetOpen);
            }
            mBottomSheetOpen = false;
            return;
        }

        Animation.AnimationListener aniListener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (mBottomSheetFragment != null) {
                    hideOrShowFragment(mBottomSheetFragment, false, true);

                    if (!getSupportFragmentManager().isStateSaved()) {
                        try {
                            getSupportFragmentManager().beginTransaction().remove(mBottomSheetFragment).commit();
                        } catch (IllegalStateException e) {
                            LogUtil.w(BaseActivity.class.getSimpleName(), e.getMessage(), e);
                        }
                    }
                }
                mBottomSheetFragment = null;

                RelativeLayout disableInputLayout = findViewById(R.id.disable_view_overlay);

                if (disableInputLayout != null) {
                    disableInputLayout.setVisibility(View.GONE);
                }
                if (listener != null) {
                    listener.onBottomSheetClosed(mBottomSheetOpen);
                }
                mBottomSheetOpen = false;
                mBottomSheetMoving = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        };

        if ((mBottomSheetFragment != null) && (mBottomSheetFragment.getView() != null)) {
            mBottomSheetFragment.getView().startAnimation(mAnimationSlideOut);
            mAnimationSlideOut.setAnimationListener(aniListener);
            mBottomSheetMoving = true;
        } else {
            mBottomSheetOpen = false;
            mBottomSheetMoving = false;
        }
    }

    private void disableInput() {
        RelativeLayout disableInputLayout = findViewById(R.id.disable_view_overlay);

        if (disableInputLayout != null) {
            disableInputLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void showIdleDialog() {
        showIdleDialog(-1);
    }

    protected void updateIdleDialog(final String text) {
        synchronized (this) {
            if (mIdleDialog != null) {
                mIdleDialog.setTitleText(text);
            }
        }
    }

    public void showIdleDialog(int resourceId) {
        synchronized (this) {
            if (mIdleDialog == null) {
                mIdleDialog = DialogBuilderUtil.buildProgressDialog(this, resourceId);
            }

            if (!this.isFinishing()) {
                mIdleDialog.show();
            }
        }
    }

    public void dismissIdleDialog() {
        synchronized (this) {
            if (mIdleDialog != null) {
                try {
                    mIdleDialog.dismiss();
                } catch (IllegalArgumentException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
            mIdleDialog = null;
        }
    }

    protected void setTrustColor(int contactState) {
        int hexColor;

        if (contactState == Contact.STATE_HIGH_TRUST) {
            hexColor = ColorUtil.getInstance().getHighColor(getSimsMeApplication());
        } else if (contactState == Contact.STATE_MIDDLE_TRUST) {
            hexColor = ColorUtil.getInstance().getMediumColor(getSimsMeApplication());
        } else {
            hexColor = ColorUtil.getInstance().getLowColor(getSimsMeApplication());
        }

        final View divider = findViewById(R.id.trust_state_divider);
        if (divider != null) {
            divider.setBackgroundColor(hexColor);
        }
    }

    protected void openBottomSheet(final int resourceID, final int containerViewId) {
        this.openBottomSheet(resourceID, containerViewId, null);
    }

    protected void openBottomSheet(final int resourceID,
                                   final int containerViewId,
                                   final List<Integer> hiddenViews) {
        openBottomSheet(resourceID, containerViewId, hiddenViews, -1, "");
    }

    protected void openBottomSheet(final int resourceID,
                                   final int containerViewId,
                                   final List<Integer> hiddenViews,
                                   final int bottomSheetTitleViewId,
                                   final String bottomSheetTitle) {
        try {
            mBottomSheetFragment = new BottomSheetGeneric();
            mBottomSheetFragment.setResourceID(resourceID);
            mBottomSheetFragment.setBottomSheetTitle(bottomSheetTitleViewId, bottomSheetTitle);
            if (hiddenViews != null) {
                mBottomSheetFragment.setHiddenViews(hiddenViews);
            }
            getSupportFragmentManager().beginTransaction().replace(containerViewId, mBottomSheetFragment).commit();

            final View fragmentView = findViewById(containerViewId);

            // KS: Must add background color to avoid transparent appearance of BottomSheet
            // TODO: Add style/theme configuration for that to avoid hard coded color setting
            fragmentView.setBackgroundColor(ColorUtil.getInstance().getToolbarColor(mApplication));

            fragmentView.startAnimation(mAnimationSlideIn);
            mBottomSheetOpen = true;
            mBottomSheetMoving = true;
            disableInput();
        } catch (IllegalStateException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    protected void hideToolbarOptions() {
        if (mToolbarOptionsLayout == null) {
            return;
        }
        for (int i = 0; i < TOOLBAR_OPTIONS_MENU_ITEM_COUNT; ++i) {
            final String imageViewId = "toolbar_options_item_" + i;
            final int imageViewResourceId = getResources().getIdentifier(imageViewId, "id", getPackageName());

            final ImageView imageview = mToolbarOptionsLayout.findViewById(imageViewResourceId);
            if (imageview != null) {
                imageview.setVisibility(View.GONE);
            }
        }
        mToolbarOptionsLayout.setVisibility(View.GONE);
        mToolbar.setVisibility(View.VISIBLE);
    }

    protected void checkForFinishOnUrlHandlerStart() throws LocalizedException {
        AccountController accountController = ((SimsMeApplication) getApplication()).getAccountController();

        if (accountController.getAccount() == null) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra(MainActivity.EXTRA_IS_RESTART, true);
            startActivity(intent);
            throw new LocalizedException(LocalizedException.NO_FULL_ACCOUNT);
        } else if (accountController.getAccount().getState() != Account.ACCOUNT_STATE_FULL) {
            // account ist nur temporaer vorhanden d.h. es befindet sich eine activity im stack z.B. identconfirm, identrequest
            throw new LocalizedException(LocalizedException.NO_FULL_ACCOUNT);
        }
    }

    public void requestPermission(final int permission, final int permissionRationaleMsg, @NonNull PermissionUtil.PermissionResultCallback callback) {
        mPermissionUtil = new PermissionUtil(this, callback);
        mPermissionUtil.requestPermission(permission, permissionRationaleMsg);
    }

    protected void setDynamicHeight(final ListView mListView, final int minHeight) {
        final ListAdapter adapter = mListView.getAdapter();
        if (adapter == null) {
            return;
        }
        int height = 0;
        final int desiredWidth = View.MeasureSpec.makeMeasureSpec(mListView.getWidth(), View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < adapter.getCount(); ++i) {
            final View listItem = adapter.getView(i, null, mListView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            height += listItem.getMeasuredHeight();
        }
        final ViewGroup.LayoutParams params = mListView.getLayoutParams();
        params.height = Math.max(minHeight, height) + (mListView.getDividerHeight() * (adapter.getCount() - 1));
        mListView.setLayoutParams(params);
        mListView.requestLayout();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (mPermissionUtil != null) {
            mPermissionUtil.onRequestPermissionsResult(requestCode, grantResults);
            mPermissionUtil = null;
        }
    }

    @Override
    public void onOwnAccountWasDeleteOnServer() {
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                getSimsMeApplication().getAccountController().deleteAccount();
            }
        };

        if (mOwnAccountWasDeletedDialog == null || !mOwnAccountWasDeletedDialog.isShowing()) {
            AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogV7(this,
                    getString(R.string.notification_account_was_deleted),
                    getString(R.string.notification_account_was_deleted_registration_title),
                    getString(R.string.std_ok),
                    null,
                    clickListener,
                    null
            );

            mOwnAccountWasDeletedDialog = dialog.getDialog();
            dialog.show();
        }
    }

    @Override
    protected void attachBaseContext(final Context newBase) {
        if (RuntimeConfig.isBAMandant() && ColorUtil.getInstance().hasLayoutModel((Application) newBase.getApplicationContext())) {
            super.attachBaseContext(new CmsThemeContextWrapper(newBase));
        } else {
            super.attachBaseContext(new ContextWrapper(newBase));
        }
    }

    public SimsMeApplication getSimsMeApplication() {
        if (mApplication == null) {
            mApplication = (SimsMeApplication) getApplication();
        }
        return mApplication;
    }

    public interface OnBottomSheetClosedListener {
        void onBottomSheetClosed(final boolean bottomSheetWasOpen);
    }

    protected class ToolbarOptionsItemModel {
        final int imageId;
        final OnClickListener onClickListener;

        public ToolbarOptionsItemModel(int imageId, OnClickListener onClickListener) {
            this.imageId = imageId;
            this.onClickListener = onClickListener;
        }

        public int getImageId() {
            return imageId;
        }

        public OnClickListener getOnClickListener() {
            return onClickListener;
        }
    }
}
