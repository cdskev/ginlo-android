// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.context;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.database.SQLException;
import android.media.MediaPlayer;
import android.os.Build;

import androidx.emoji.bundled.BundledEmojiCompatConfig;
import androidx.emoji.text.EmojiCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ProcessLifecycleOwner;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.support.HasSupportFragmentInjector;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.billing.GinloBillingImpl;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.BackupController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ClipBoardController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.DeviceController;
import eu.ginlo_apps.ginlo.controller.GCMController;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.InternalMessageController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.MessageDecryptionController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.StatusTextController;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.controller.message.ChannelChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.PrivateInternalMessageController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.dagger.components.DaggerAppComponent;
import eu.ginlo_apps.ginlo.data.preferences.SecurePreferences;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.ChannelDao;
import eu.ginlo_apps.ginlo.greendao.ChatDao;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.greendao.MigratingOpenHelper;
import eu.ginlo_apps.ginlo.greendao.NotificationDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.log.Logger;
import eu.ginlo_apps.ginlo.util.AudioUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import org.greenrobot.greendao.database.Database;
import javax.inject.Inject;
import java.util.ArrayList;

public class SimsMeApplication
        extends Application implements HasActivityInjector, HasSupportFragmentInjector {

    private static final String DB_NAME = "simsme-db";
    private static SimsMeApplication instance;

    protected AccountController mAccountController;
    protected ContactController mContactController;
    protected GroupChatController groupChatController;
    protected ChatOverviewController chatOverviewController;
    protected PreferencesController preferencesController;
    protected DaoSession mDaoSession;
    protected ChatDao mChatDao;
    protected ChannelDao mChannelDao;
    protected NotificationDao mNotificationDao;

    // Controller
    private KeyController keyController;
    private SingleChatController singleChatController;
    private GCMController gcmController;
    private LoginController loginController;
    private MessageController messageController;
    private AVChatController avChatController;
    private MessageDecryptionController messageDecryptionController;
    private ChatImageController chatImageController;
    private PrivateInternalMessageController privateInternalMessageController;
    private InternalMessageController internalMessageController;
    private StatusTextController statusTextController;
    private AttachmentController attachmentController;
    private NotificationController notificationController;
    private TaskManagerController taskManagerController;
    private ClipBoardController clipBoardController;
    private ChannelController channelController;
    private ChannelChatController channelChatController;
    private BackupController mBackupController;
    private DeviceController mDeviceController;

    private Database dataBase;
    private boolean dbHasBeenUpdated;

    private MediaPlayer mReceivedSoundPlayer;
    private android.media.AudioManager mMediaAudioManager;

    public SimsMeApplication() {
        instance = this;
    }

    @Inject
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector;

    @Override
    public AndroidInjector<Activity> activityInjector() {
        return dispatchingAndroidInjector;
    }

    @Inject
    DispatchingAndroidInjector<Fragment> dispatchingFragmentInjector;

    @Override
    public AndroidInjector<Fragment> supportFragmentInjector() {
        return dispatchingFragmentInjector;
    }

    public static SimsMeApplication getInstance() {
        return instance;
    }

    @Inject
    Logger logger;

    @Inject
    GinloAppLifecycle appLifecycle;

    @Inject
    GinloUncaughtExceptionHandler ginloUncaughtExceptionHandler;

    @Inject
    GinloLifecycleObserver ginloLifecycleObserver;

    @Inject
    SecurePreferences securePreferences;

    @Override
    public void onCreate() {
        DaggerAppComponent.builder()
                .application(this)
                .build()
                .inject(this);

        LogUtil.init(logger);

        initControllers();

        mReceivedSoundPlayer = AudioUtil.createMediaPlayer(this, R.raw.read_sound);
        mMediaAudioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);

        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(ginloUncaughtExceptionHandler);

        initSentry();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(ginloLifecycleObserver);

        EmojiCompat.init(new BundledEmojiCompatConfig(this));
    }

    private void initSentry() {
        if (!BuildConfig.DEBUG)
            Sentry.init(getString(R.string.sentry_dsn), new AndroidSentryClientFactory(this));
    }

    public void playMessageReceivedSound() {
        try {
            if (mMediaAudioManager != null && mReceivedSoundPlayer != null && preferencesController.getMessageReceivedSound()) {
                float log1 = AudioUtil.getAudioVolume(mMediaAudioManager);
                mReceivedSoundPlayer.setVolume(log1, log1);
                mReceivedSoundPlayer.start();
            }
        } catch (IllegalStateException e) {
            LogUtil.e(this.getClass().getSimpleName(), e.toString());
        }
    }

    public android.media.AudioManager getMediaAudioManager() {
        return mMediaAudioManager;
    }

    protected void initDaos() {
        final DaoMaster daoMaster = new DaoMaster(dataBase);
        mDaoSession = daoMaster.newSession();

        mChatDao = mDaoSession.getChatDao();
        mChannelDao = mDaoSession.getChannelDao();
        mNotificationDao = mDaoSession.getNotificationDao();
    }

    public void initControllers() {
        ArrayList<SQLException> exceptionList = initDB();

        initDaos();

        getPreferencesController();

        if (exceptionList != null) {
            for (SQLException e : exceptionList) {
                LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        getKeyController();
        getGcmController();
        getAccountController();
        getContactController();
        getSingleChatController();
        getGroupChatController();
        getChatImageController();
        getChannelController();
        getChatOverviewController();
        getPrivateInternalMessageController();
        getLoginController();
        getAttachmentController();
        getChannelChatController();
        getMessageController();
        getAVChatController();
        getInternalMessageController();
        getMessageDecryptionController();
        getStatusTextController();
        getNotificationController();
        getTaskManagerController();
        getClipBoardController();
        getAppLifecycleController();
        getBackupController();

        getMessageController().addListener(getSingleChatController());
        getMessageController().addListener(getGroupChatController());
        getMessageController().addListener(getChannelChatController());
        getMessageController().addListener(getInternalMessageController());
        getMessageController().addListener(getChatOverviewController());
        getMessageController().addListener(getPrivateInternalMessageController());

        if (dbHasBeenUpdated) {
            //accountstatus existiert bei Versionen > 1.4 nicht
            checkAccountState();
        }
    }

    private void checkAccountState() {
        //accountState auch bei alten clients setzen

        Account account = getAccountController().getAccount();

        if (account != null) {
            if (!StringUtil.isNullOrEmpty(account.getName())) {
                account.setState(Account.ACCOUNT_STATE_FULL);
            } else {
                try {
                    if (!StringUtil.isNullOrEmpty(account.getPasstoken())) {
                        account.setState(Account.ACCOUNT_STATE_CONFIRMED);
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                }
            }
            getAccountController().updateAccoutDao();
        }
    }

    public void deleteAll() {
        getKeyController().purgeKeys();
        deleteDatabase(DB_NAME);
        getGcmController().clearGCMValues();
        getChatImageController().resetChatImages();
        getChatImageController().resetBackground();
        getChatImageController().resetChatImages();
        getChatImageController().resetBackground();
        getAttachmentController().clearCache();
        getAttachmentController().deleteAllAttachments();
        getNotificationController().dismissAll();
        getPreferencesController().deleteSharedPreferences();
        resetControllers();

        if (!BuildConfig.DEBUG) {
            SystemUtil.restart(this, 150);
        }
    }

    public void safeDeleteAccount() {

        // Account löschen und alle Controller reseten, die Members halten könnten
        getKeyController().purgeKeys();
        deleteDatabase(DB_NAME);
        getGcmController().clearGCMValues();
        getChatImageController().resetChatImages();
        getChatImageController().resetBackground();
        getChatImageController().resetChatImages();
        getChatImageController().resetBackground();
        getAttachmentController().clearCache();
        getAttachmentController().deleteAllAttachments();
        getNotificationController().dismissAll();
        getPreferencesController().deleteSharedPreferences();

        if (messageController != null) {
            // keine Nachrichten mehr abholen
            messageController.resetTasks();
        }
        keyController = null;
        mAccountController = null;
        mContactController = null;
        singleChatController = null;
        groupChatController = null;
        gcmController = null;
        loginController = null;
        messageController = null;
        avChatController = null;
        messageDecryptionController = null;
        chatImageController = null;
        privateInternalMessageController = null;
        chatOverviewController = null;
        preferencesController = null;
        internalMessageController = null;
        statusTextController = null;
        attachmentController = null;
        notificationController = null;
        taskManagerController = null;
        clipBoardController = null;
        channelController = null;
        channelChatController = null;
        mBackupController = null;

        initControllers();
    }

    public void resetControllers() {
        if (messageController != null) {
            // keine Nachrichten mehr abholen
            messageController.resetTasks();
        }
        keyController = null;
        mAccountController = null;
        mContactController = null;
        singleChatController = null;
        groupChatController = null;
        gcmController = null;
        loginController = null;
        messageController = null;
        avChatController = null;
        messageDecryptionController = null;
        chatImageController = null;
        privateInternalMessageController = null;
        chatOverviewController = null;
        preferencesController = null;
        internalMessageController = null;
        statusTextController = null;
        attachmentController = null;
        notificationController = null;
        taskManagerController = null;
        clipBoardController = null;
        channelController = null;
        channelChatController = null;
        mBackupController = null;
    }

    private ArrayList<SQLException> initDB() {
        MigratingOpenHelper helper = new MigratingOpenHelper(this, DB_NAME, null);

        helper.setWriteAheadLoggingEnabled(true);

        dataBase = helper.getWritableDb();

        //helper.onUpgrade(dataBase, 71, 72);
        dbHasBeenUpdated = helper.getDbHasBeenUpdated();
        return helper.getUpdateExceptions();
    }

    public GinloBillingImpl getGinloBillingImpl() {
        return null;
    }

    public SecurePreferences getSecurePreferences() {
        return securePreferences;
    }

    public KeyController getKeyController() {
        if (keyController == null) {
            keyController = new KeyController(this);
        }

        return keyController;
    }

    public AccountController getAccountController() {
        if (mAccountController == null) {
            mAccountController = new AccountController(this);
        }
        return mAccountController;
    }

    public Database getDataBase() {
        return dataBase;
    }

    public ContactController getContactController() {
        if (mContactController == null) {
            mContactController = new ContactController(this);
        }
        return mContactController;
    }

    public SingleChatController getSingleChatController() {
        if (singleChatController == null) {
            singleChatController = new SingleChatController(this);
        }
        return singleChatController;
    }

    public GCMController getGcmController() {
        if (gcmController == null) {
            gcmController = new GCMController(this);
        }

        return gcmController;
    }

    public LoginController getLoginController() {
        if (loginController == null) {
            loginController = new LoginController(this);
        }
        return loginController;
    }

    public MessageController getMessageController() {
        if (messageController == null) {
            messageController = new MessageController(this);
        }

        return messageController;
    }

    public AVChatController getAVChatController() {

        if(!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            // No AVC earlier than Oreo
            avChatController = null;
        }
        else if (avChatController == null) {
            avChatController = AVChatController.getInstance();
        }

        return avChatController;
    }

    public GroupChatController getGroupChatController() {
        if (groupChatController == null) {
            groupChatController = new GroupChatController(this);
        }
        return groupChatController;
    }

    public MessageDecryptionController getMessageDecryptionController() {
        if (messageDecryptionController == null) {
            messageDecryptionController = new MessageDecryptionController(this);
        }
        return messageDecryptionController;
    }

    public ChatImageController getChatImageController() {
        if (chatImageController == null) {
            chatImageController = new ChatImageController(this);
        }
        return chatImageController;
    }

    public ChatOverviewController getChatOverviewController() {
        if (chatOverviewController == null) {
            chatOverviewController = new ChatOverviewController(this);
        }

        return chatOverviewController;
    }

    public PreferencesController getPreferencesController() {
        if (preferencesController == null) {
            preferencesController = new PreferencesController(this);
        }
        return preferencesController;
    }

    public InternalMessageController getInternalMessageController() {
        if (internalMessageController == null) {
            internalMessageController = new InternalMessageController(this);
        }
        return internalMessageController;
    }

    public PrivateInternalMessageController getPrivateInternalMessageController() {
        if (privateInternalMessageController == null) {
            privateInternalMessageController = new PrivateInternalMessageController(this);
        }
        return privateInternalMessageController;
    }

    public StatusTextController getStatusTextController() {
        if (statusTextController == null) {
            statusTextController = new StatusTextController(this);
        }
        return statusTextController;
    }

    public AttachmentController getAttachmentController() {
        if (attachmentController == null) {
            attachmentController = new AttachmentController(this);
        }
        return attachmentController;
    }

    public NotificationController getNotificationController() {
        if (notificationController == null) {
            notificationController = new NotificationController(this);
        }

        return notificationController;
    }

    public TaskManagerController getTaskManagerController() {
        if (taskManagerController == null) {
            taskManagerController = new TaskManagerController();
        }
        return taskManagerController;
    }

    public ClipBoardController getClipBoardController() {
        if (clipBoardController == null) {
            clipBoardController = new ClipBoardController(this);
        }
        return clipBoardController;
    }

    public GinloAppLifecycle getAppLifecycleController() {
        return appLifecycle;
    }

    public ChannelController getChannelController() {
        if (channelController == null) {
            channelController = new ChannelController(this);
        }
        return channelController;
    }

    public ChannelChatController getChannelChatController() {
        if (channelChatController == null) {
            channelChatController = new ChannelChatController(this);
        }
        return channelChatController;
    }

    public BackupController getBackupController() {
        if (mBackupController == null) {
            mBackupController = new BackupController(this);
        }

        return mBackupController;
    }

    public DeviceController getDeviceController() {
        if (mDeviceController == null) {
            mDeviceController = new DeviceController(this);
        }

        return mDeviceController;
    }

    public ChatDao getChatDao() {
        return mChatDao;
    }

    public ChannelDao getChannelDao() {
        return mChannelDao;
    }
}
