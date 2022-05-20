// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;

import android.app.Application;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
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
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.activity.register.ShowSimsmeIdActivity;
import eu.ginlo_apps.ginlo.billing.GinloBillingImpl;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ClipBoardController;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.MessageDecryptionController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
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
import eu.ginlo_apps.ginlo.service.GinloOngoingService;
import eu.ginlo_apps.ginlo.theme.CmsThemeContextWrapper;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
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
    private static final String SAVED_RESULTS = "BaseActivity.savedResults";
    private static final String SAVED_INSTANCE_STATE = "BaseActivity.savedInstanceState";
    private static final int TOOLBAR_OPTIONS_MENU_ITEM_COUNT = 6;

    protected final int ACTIONBAR_RIGHT_SIDE_PROFILE_VIEW = R.id.action_bar_image_view_profile_picture;
    protected final int ACTIONBAR_RIGHT_SIDE_CONTAINER = R.id.action_bar_right_image_view_container;
    protected final int ACTIONBAR_RIGHT_SIDE_VIEW = R.id.action_bar_right_image_view;
    protected final int ACTIONBAR_MIDDLE_CONTAINER = R.id.action_bar_middle_image_view_container;
    protected final int ACTIONBAR_MIDDLE_VIEW = R.id.action_bar_middle_image_view;

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

    protected SimsMeApplication mApplication;
    protected PreferencesController preferencesController;
    protected NotificationController notificationController;
    protected MessageDecryptionController messageDecryptionController;
    protected AVChatController avChatController;
    protected ChatImageController chatImageController;
    protected ClipBoardController clipBoardController;
    protected GinloBillingImpl ginloBillingImpl = null;

    protected boolean ginloOngoingServiceRunning = false;

    private TextView mTitleView;
    private boolean mCheckIsLogout;
    private View mRightImageView;
    private View mMiddleImageView;
    private ImageView mProfileImage;
    private ProgressDialog mIdleDialog;
    private boolean mIsOptionsMenuSet = false;
    private PermissionUtil mPermissionUtil;
    private OnMessageReceivedListener mOnMessageReceivedListener;
    private Dialog mOwnAccountWasDeletedDialog;

    private String mCurrentThemeName;
    private String mCurrentThemeMode;

    @Inject
    public GinloAppLifecycle appLifecycle;

    private void forceLocale() {
        if (BuildConfig.FORCE_GERMAN) {
            getResources().getConfiguration().locale = new Locale("de");
        }
    }

    protected void startGinloOngoingService() {
        // Start GinloOngoingService if applicable
        if (BuildConfig.HAVE_GINLO_ONGOING_SERVICE) {
            if (!ginloOngoingServiceRunning) {
                preferencesController.setGinloOngoingServiceEnabled(true);
                GinloOngoingService.launch(this);
                ginloOngoingServiceRunning = true;
            }
        } else {
            LogUtil.i(TAG, " onCreate: GinloOngoingService is disabled by BuildConfig." );
        }
    }

    protected void stopGinloOngoingService() {
        if (BuildConfig.HAVE_GINLO_ONGOING_SERVICE) {
            if (ginloOngoingServiceRunning) {
                preferencesController.setGinloOngoingServiceEnabled(false);
                GinloOngoingService.abort(this);
                ginloOngoingServiceRunning = false;
            }
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
            LogUtil.d(TAG, "mSaveInstanceState is null");
            mSaveInstanceState = savedInstanceState;
        }

        ScreenDesignUtil.getInstance().initDisplayMetrics(getSimsMeApplication());

        loginController = getSimsMeApplication().getLoginController();
        preferencesController = getSimsMeApplication().getPreferencesController();
        notificationController = getSimsMeApplication().getNotificationController();
        messageDecryptionController = getSimsMeApplication().getMessageDecryptionController();
        avChatController = getSimsMeApplication().getAVChatController();
        chatImageController = getSimsMeApplication().getChatImageController();
        clipBoardController = getSimsMeApplication().getClipBoardController();

        if (!RuntimeConfig.isB2c()) {
            // If b2b instantiate in-app billing implementation
            ginloBillingImpl = getSimsMeApplication().getGinloBillingImpl();
        }

        super.onCreate(savedInstanceState);

        // Check for device night mode and initialize configured theme
        int currentNightMode = this.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        setAppTheme(currentNightMode);

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

    protected void setAppTheme(int currentNightMode) {
        // Set UI theme according to preferences - only if there is no company layout!
        // Important: Must also handle theme over to ScreenDesignUtil instance before first themed inflates!
        if(ScreenDesignUtil.getInstance().hasLayoutModel(getSimsMeApplication())) {
            mCurrentThemeName = preferencesController.getThemeName();
            if(!mCurrentThemeName.startsWith("GinloDefault")) {
                mCurrentThemeName = BuildConfig.DEFAULT_THEME;
            }
            mCurrentThemeMode = BuildConfig.DEFAULT_THEME_MODE;
            preferencesController.setThemeColorSettingLocked(true);
            LogUtil.i(TAG, "setCurrentTheme: We have a cockpit layout model - ignore color/darkmode settings: " + mCurrentThemeName + mCurrentThemeMode);
        } else {
            mCurrentThemeName = preferencesController.getThemeName();
            mCurrentThemeMode = preferencesController.getThemeMode();
            preferencesController.setThemeColorSettingLocked(false);

            if(PreferencesController.THEME_MODE_AUTO.equals(mCurrentThemeMode)) {
                // We are in THEME_MODE_AUTO. Set the appropriate theme mode
                if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    // Night mode is active, we're using dark theme
                    LogUtil.i(TAG, "setCurrentTheme: Auto-switching to theme mode DARK.");
                    mCurrentThemeMode = PreferencesController.THEME_MODE_DARK;
                } else {
                    // Night mode is not active, we're using the light theme
                    mCurrentThemeMode = PreferencesController.THEME_MODE_LIGHT;
                    LogUtil.i(TAG, "setCurrentTheme: Auto-switching to theme mode LIGHT.");
                }
            }
            LogUtil.i(TAG, "setCurrentTheme: " + mCurrentThemeName + mCurrentThemeMode);
        }

        int themID = getResources().getIdentifier(mCurrentThemeName + mCurrentThemeMode, "style", this.getPackageName());
        if(themID != 0) {
            setTheme(themID);
        }
        ScreenDesignUtil.getInstance().setCurrentTheme(getTheme());
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

        mRightImageView = mToolbar.findViewById(ACTIONBAR_RIGHT_SIDE_VIEW);
        mMiddleImageView = mToolbar.findViewById(ACTIONBAR_MIDDLE_VIEW);
        mProfileImage = mToolbar.findViewById(R.id.action_bar_image_view_profile_picture);
    }

    protected void colorizeToolbar() {
        if (mToolbar == null) {
            return;
        }
        ToolbarColorizeHelper.colorizeToolbar(mToolbar,
                ScreenDesignUtil.getInstance().getMainContrast80Color(getSimsMeApplication()),
                ScreenDesignUtil.getInstance().getToolbarColor(getSimsMeApplication()),
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
        LogUtil.d(TAG, "isLogout: loginController state: " + loginController.getState());
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
                LogUtil.d(TAG, "Save results instance state: " + results);
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

        LogUtil.d(TAG, "onStart: loginController state: " + loginController.getState());

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

        LogUtil.d(TAG, "onResume: ------------------------> hasThemeChanged = " + preferencesController.hasThemeChanged());


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
            LogUtil.d(TAG, "callOnActivityPostLoginResult with resultCode " + results.resultCode);
            onActivityPostLoginResult(results.requestCode, results.resultCode, results.data);
            results = null;
        } else {
            LogUtil.d(TAG, "callOnActivityPostLoginResult without results");
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
        LogUtil.d(TAG, "onConfigurationChanged: -------------------------->" + newConfig);
        preferencesController.setThemeChanged(true);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        setAppTheme(currentNightMode);

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
        setActionBarImage(ACTIONBAR_RIGHT_SIDE_VIEW, drawableId, contentDescription, customImageColor);
        setActionBarListener(ACTIONBAR_RIGHT_SIDE_VIEW, clickListener);
    }

    protected void removeRightActionBarImage() {
        setActionBarImage(ACTIONBAR_RIGHT_SIDE_VIEW, -1, null, -1);
    }

    protected void setActionBarImage(int side, int drawableId, String contentDescription, int customImgColor) {
        final ImageView imageView = mToolbar.findViewById(side);

        if (imageView == null) {
            return;
        }

        if (drawableId > -1) {
            imageView.setVisibility(View.VISIBLE);

            imageView.setColorFilter(customImgColor == -1
                    ? ScreenDesignUtil.getInstance().getMainContrast80Color(getSimsMeApplication())
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

    protected void setProfilePictureVisibility(int visibility) {
        final ImageView imageView = getToolbar().findViewById(R.id.action_bar_image_view_profile_picture);
        if (imageView != null) {
            imageView.setVisibility(visibility);
        }
    }

    protected void setRightActionBarImageVisibility(int visibility) {
        final View container = getToolbar().findViewById(ACTIONBAR_RIGHT_SIDE_CONTAINER);
        final View view = getToolbar().findViewById(ACTIONBAR_RIGHT_SIDE_VIEW);
        if (view != null) {
            view.setVisibility(visibility);
        }
        if (container != null) {
            container.setVisibility(visibility);
        }
    }

    protected void setMiddleActionBarImageVisibility(int visibility) {

        final View container = getToolbar().findViewById(ACTIONBAR_MIDDLE_CONTAINER);
        final View view = getToolbar().findViewById(ACTIONBAR_MIDDLE_VIEW);
        if (view != null) {
            view.setVisibility(visibility);
        }
        if (container != null) {
            container.setVisibility(visibility);
        }
    }

    protected void setActionBarAVCImageVisibility(int visibility) {

        if (avChatController == null || StringUtil.isNullOrEmpty(BuildConfig.GINLO_AVC_SERVER_URL)) {
            if(visibility != View.GONE)
            return;
        }
        setMiddleActionBarImageVisibility(visibility);
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
            hexColor = ScreenDesignUtil.getInstance().getHighColor(getSimsMeApplication());
        } else if (contactState == Contact.STATE_MIDDLE_TRUST) {
            hexColor = ScreenDesignUtil.getInstance().getMediumColor(getSimsMeApplication());
        } else {
            hexColor = ScreenDesignUtil.getInstance().getLowColor(getSimsMeApplication());
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
            fragmentView.setBackgroundColor(ScreenDesignUtil.getInstance().getToolbarColor(mApplication));

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

    public void requestBatteryWhitelisting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                LogUtil.i(TAG, "requestBatteryWhitelisting: No play services - app is battery whitelisted.");
            } else {
                LogUtil.i(TAG, "requestBatteryWhitelisting: No play services - app is not battery whitelisted.");
                LogUtil.i(TAG, "requestBatteryWhitelisting: Trying to ask user ...");
                Intent i = new Intent();
                i.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    LogUtil.e(TAG, "requestBatteryWhitelisting: Caught: " + e.getMessage());
                }
            }
        }
    }

    protected void setDynamicHeight(final ListView listView, final int minHeight) {
        final ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            LogUtil.d(TAG, "setDynamicHeight: No adapter.");
            return;
        }
        int height = 0;
        final int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < adapter.getCount(); i++) {
            final View listItem = adapter.getView(i, null, listView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            height += listItem.getMeasuredHeight();
        }
        final ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = Math.max(minHeight, height) + (listView.getDividerHeight() * (adapter.getCount() - 1));
        LogUtil.d(TAG, "setDynamicHeight: Calculated height = " + params.height);
        listView.setLayoutParams(params);
        listView.requestLayout();
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
        if (RuntimeConfig.isBAMandant() && ScreenDesignUtil.getInstance().hasLayoutModel((Application) newBase.getApplicationContext())) {
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
