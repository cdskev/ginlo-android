// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialOverlayLayout;
import com.leinardi.android.speeddial.SpeedDialView;

import eu.ginlo_apps.ginlo.ChannelListActivity;
import eu.ginlo_apps.ginlo.ChatRoomInfoActivity;
import eu.ginlo_apps.ginlo.ContactsActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.SearchContactActivity;
import eu.ginlo_apps.ginlo.UseCases.InviteFriendUseCase;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.ChannelChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.DistributorChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.GroupChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.SystemChatActivity;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsAdapter;
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick;
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick;
import eu.ginlo_apps.ginlo.activity.device.DevicesOverviewActivity;
import eu.ginlo_apps.ginlo.activity.preferences.PreferencesOverviewActivity;
import eu.ginlo_apps.ginlo.activity.profile.ProfileActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivity;
import eu.ginlo_apps.ginlo.adapter.DrawerListAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChannelController.ChannelAsyncLoaderCallback;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.OnLoadContactsListener;
import eu.ginlo_apps.ginlo.controller.GCMController;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.ServiceController;
import eu.ginlo_apps.ginlo.controller.message.ChannelChatController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnAcceptInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnChatDataChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeclineInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeleteTimedMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnRemoveRoomListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnTimedMessagesDeliveredListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.drawer.DrawerListItemVO;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.ImageCache;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.SimsmeSwipeRefreshLayout;
import org.jetbrains.annotations.NotNull;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ChatsOverviewActivity
        extends NewBaseActivity
        implements OnChatDataChangedListener,
        ContactController.OnContactProfileInfoChangeNotification,
        OnSendMessageListener,
        ServiceController.ServicesChangedListener,
        OnChatItemClick,
        OnChatItemLongClick {

    private static final String EXTRA_TOGGLE_MAP = "ChatsOverviewActivity.extraToggleMap";
    private static final String TAG = ChatsOverviewActivity.class.getSimpleName();

    /**
     * Wenn Activty ueber onNewIntent Aufgerufen wird und der Wert im Intent vorhanden ist wird die Chatliste hochgescrollt
     */
    public static final String EXTRA_SCROLL_UP = "ChatsOverviewActivity.extraScrollUp";

    private static final int NUMBER_OF_CHATS_FOR_INVITE_DIALOG = 2;
    protected AccountController mAccountController;

    private ChatsAdapter chatsAdapter;
    private ImageLoader mImageLoader;
    private DrawerListAdapter mDrawerListAdapter;
    private RecyclerView chatsRecycleView;
    private DividerItemDecoration dividerItemDecoration;
    private Animation mAnimationZoomIn;
    private Animation mAnimationZoomOut;
    private AlertDialog contextMenuDialog;
    private ContactController contactController;
    private GroupChatController groupChatController;
    private SingleChatController singleChatController;
    private ChannelController channelController;
    private ServiceController mServiceController;
    private ChannelChatController channelChatController;
    private ChatOverviewController chatOverviewController;

    @Inject
    public Router router;

    @Inject
    public AppConnectivity appConnectivity;

    private final OnDeleteTimedMessageListener mOnDeleteTimedMessageListener = new OnDeleteTimedMessageListener() {
        @Override
        public void onDeleteMessageError(@NotNull final String errorMessage) {
            if (!StringUtil.isNullOrEmpty(errorMessage)) {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, errorMessage).show();
            }
        }

        @Override
        public void onDeleteAllMessagesSuccess(@NotNull final String chatGuid) {
            //wird nur aufgerufen, wenn ein chat komplett geloescht wurde
            dismissIdleDialog();
            chatOverviewController.chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
            chatOverviewController.filterMessages();
        }

        @Override
        public void onDeleteSingleMessageSuccess(final String chatGuid) {
            if (!StringUtil.isNullOrEmpty(chatGuid)) {
                dismissIdleDialog();
                chatOverviewController.chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_MSG_DELETED);
            }
        }
    };

    private ChatImageController mChatImageController;
    private MessageController mMessageController;
    private NotificationController notificationController;
    private PreferencesController mPreferencesController;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerListView;
    private RelativeLayout mDrawerView;
    private androidx.appcompat.app.ActionBarDrawerToggle mDrawerToggle;
    private Chat mMarkedChat;
    private SimsmeSwipeRefreshLayout mSwipeLayout;
    private AlertDialogWrapper mInviteFriendsDialog;
    private ListRefreshedListener mListRefreshedListener;
    private OnLoadContactsListener initialContactsListener;
    private OnTimedMessagesDeliveredListener mOnTimedMessagesDeliveredListener;
    private int mFirstVisibleItemIndex = 0;
    private boolean mShowServices;
    private boolean mFirstTouch;
    private Dialog mVerifyPhoneDialog;
    private SpeedDialView mSpeedDialView;
    private String mMdmConfigKey = null;
    InviteFriendUseCase inviteFriendUseCase = new InviteFriendUseCase();

    public void onFabPressed(View v) {
        startNewSingleChat();
    }

    private void startNewSingleChat() {
        Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getContactsActivityClass());
        intent.putExtra(ContactsActivity.EXTRA_MODE, ContactsActivity.MODE_SIMSME_SINGLE);
        startActivity(intent);
    }

    private void startNewGroupChat() {
        Intent intent = new Intent(this, ChatRoomInfoActivity.class);
        intent.putExtra(ChatRoomInfoActivity.EXTRA_MODE, ChatRoomInfoActivity.MODE_CREATE);
        startActivity(intent);
    }

    private void createNewContact() {
        final Intent intent = new Intent(this, SearchContactActivity.class);
        startActivity(intent);
    }

    private void addNewChannel() {
        Intent intent = new Intent(this, ChannelListActivity.class);
        startActivity(intent);
    }

    private void startNewDirectChat() {
        Intent intent = new Intent(this, DistributorChatActivity.class);

        startActivity(intent);
    }

    private void startInviteFriendsActivity() {
        inviteFriendUseCase.execute(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            closeBottomSheet(mOnBottomSheetClosedListener);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initContactsListener() {
        initialContactsListener = new OnLoadContactsListener() {
            @Override
            public void onLoadContactsComplete() {
                initialContactsListener = null;
                LogUtil.i(TAG, "onLoadContactsComplete");

                mSwipeLayout.setRefreshing(false);
                mPreferencesController.setHasOldContactsMerged();
                mPreferencesController.checkRecoveryCodeToBeSet(true);
                dismissIdleDialog();
            }

            @Override
            public void onLoadContactsError(String message) {
                initialContactsListener = null;

                LogUtil.i(TAG, "onLoadContactsError");

                dismissIdleDialog();
                if ((message != null) && (message.length() > 0)) {
                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, message).show();
                }
            }

            @Override
            public void onLoadContactsCanceled() {
                initialContactsListener = null;

                LogUtil.i(TAG, "onLoadContactsCanceled");

                dismissIdleDialog();
            }
        };
    }

    void initChatList() {
        chatsAdapter = new ChatsAdapter(this, mImageLoader, getSimsMeApplication().getSingleChatController(), getWindowManager().getDefaultDisplay(), new ArrayList<BaseChatOverviewItemVO>(), this, this);
        dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        dividerItemDecoration.setDrawable(Objects.requireNonNull(ContextCompat.getDrawable(this, R.drawable.line_divider)));

        chatsRecycleView = findViewById(R.id.chat_overview_list_view);
        chatsRecycleView.setAdapter(chatsAdapter);
        chatsRecycleView.setLayoutManager(new ChatsOverviewLayoutManager(this));
        chatsRecycleView.addItemDecoration(dividerItemDecoration);
        chatsRecycleView.setSelected(false);

        chatOverviewController.setAdapter(chatsAdapter);
    }

    private class ChatsOverviewLayoutManager extends LinearLayoutManager {

        ChatsOverviewLayoutManager(Context context) {
            super(context);
        }

        @Override
        public void onScrollStateChanged(int state) {
            super.onScrollStateChanged(state);

            if(state == RecyclerView.SCROLL_STATE_DRAGGING) {
                // Stop pending notifications immediately, if user interacts with view
                // TODO: Look for a better alternative to SCROLL_STATE_DRAGGING
                notificationController.dismissAll();
            } else if (state == RecyclerView.SCROLL_STATE_IDLE) {
                int tmp = findFirstVisibleItemPosition();

                /*
                if (mFirstVisibleItemIndex > 2) {
                    if (tmp <= 2) {
                        notificationController.dismissAll();
                    }
                }

                 */

                mFirstVisibleItemIndex = tmp;

                if (mFirstVisibleItemIndex > 0) {
                    notificationController.toggleIgnoreAll(false);
                } else {
                    notificationController.toggleIgnoreAll(true);
                }

            }
        }
    }

    protected String getOwnStatusText(@NonNull Contact ownContact) {
        String statusText = "";
        try {
            statusText = ownContact.getStatusText();
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
        return statusText;
    }

    protected String getOwnNickName(@NonNull Contact ownContact) {
        String nickNameText = "";
        try {
            nickNameText = ownContact.getNickname();
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
        return nickNameText;
    }

    private List<DrawerListItemVO> createListForDrawer() {
        if (mAccountController.getAccount() == null) {
            finish();
            return null; // NSOSONAR
        }

        final Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
        boolean isAbsent = false;
        final String statusText = getOwnStatusText(ownContact);
        final String nameText = getOwnNickName(ownContact);

        try {
            isAbsent = ownContact.isAbsent();
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }

        List<DrawerListItemVO> rc = new ArrayList<>();

        rc.add(new DrawerListItemVO(nameText,
                statusText,
                ProfileActivity.class,
                -1,
                getResources().getString(R.string.content_description_chatsoverview_profile), isAbsent));

        rc.add(new DrawerListItemVO(getText(R.string.chats_title_chats).toString(),
                getText(R.string.chats_title_chats).toString(),
                null,
                R.drawable.single_chats,
                null,
                false));

        if (ConfigUtil.INSTANCE.channelsEnabled()) {
            rc.add(new DrawerListItemVO(getText(R.string.chat_overview_menu_show_channels).toString(),
                    null,
                    ChannelListActivity.class,
                    R.drawable.channels,
                    getText(R.string.chat_overview_menu_show_channels).toString(),
                    false));
        }

        rc.add(new DrawerListItemVO(getText(R.string.contacts_overViewViewControllerTitle).toString(),
                getText(R.string.settings_aboutSimsme_secureTextLabel).toString(),
                RuntimeConfig.getClassUtil().getContactsActivityClass(),
                R.drawable.contact,
                null,
                false));

        if (ConfigUtil.INSTANCE.hasMultiDeviceSupport()) {
            rc.add(new DrawerListItemVO(getText(R.string.devices_title).toString(),
                    getText(R.string.devices_administrations_title).toString(),
                    DevicesOverviewActivity.class,
                    R.drawable.devices,
                    null,
                    false));
        }

        rc.add(new DrawerListItemVO(getText(R.string.settings_settingsTitle).toString(),
                "", // NSOSONAR
                PreferencesOverviewActivity.class,
                R.drawable.settings,
                null,
                false));

        return rc;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    private void selectItem(int position) {
        if (position > -1 && position < mDrawerListView.getAdapter().getCount()) {
            mDrawerListView.setItemChecked(position, true);
            final DrawerListItemVO item = (DrawerListItemVO) mDrawerListView.getAdapter().getItem(position);
            final Class nextActivity = item.getNextActivity();

            if (nextActivity != null) {
                final Intent intent = new Intent(this, nextActivity);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mSpeedDialView != null && mSpeedDialView.isOpen()) {
            mSpeedDialView.close();
        } else if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
        } else if (mBottomSheetOpen) {
            closeBottomSheet(mOnBottomSheetClosedListener);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setAllowEnterTransitionOverlap(false);
        getWindow().setAllowReturnTransitionOverlap(false);
        getWindow().setEnterTransition(null);
    }

    @Override
    public void setTitle(CharSequence title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            setTitle(getString(R.string.chats_title_chats));
            final Intent ownIntent = getIntent();

            mAccountController = ((SimsMeApplication) getApplication()).getAccountController();
            singleChatController = ((SimsMeApplication) getApplication()).getSingleChatController();
            groupChatController = ((SimsMeApplication) getApplication()).getGroupChatController();
            channelController = ((SimsMeApplication) getApplication()).getChannelController();
            mServiceController = ((SimsMeApplication) getApplication()).getServiceController();
            channelChatController = ((SimsMeApplication) getApplication()).getChannelChatController();
            chatOverviewController = ((SimsMeApplication) getApplication()).getChatOverviewController();
            contactController = ((SimsMeApplication) getApplication()).getContactController();
            notificationController = ((SimsMeApplication) getApplication()).getNotificationController();
            mChatImageController = ((SimsMeApplication) getApplication()).getChatImageController();
            mPreferencesController = ((SimsMeApplication) getApplication()).getPreferencesController();

            checkForUnsubscribedServices();

            initFabMenu();

            //Detect DoubleTap SIMSME-6610
            mFirstTouch = true;

            mSwipeLayout = findViewById(R.id.swipe_refresh_chatoverview);
            mSwipeLayout.setEnabled(false);

            //Navigation Menue
            mDrawerLayout = findViewById(R.id.activity_chats_overview_drawerlayout);

            mDrawerListView = findViewById(R.id.left_drawer_list);

            mDrawerListView.setOnItemClickListener(new DrawerItemClickListener());

            mDrawerView = findViewById(R.id.left_drawer);

            mDrawerToggle = new androidx.appcompat.app.ActionBarDrawerToggle(this,
                    mDrawerLayout,
                    R.string.adjust_picture_rotate,
                    R.string.adjust_picture_rotate) {
                public void onDrawerClosed(final View view) {
                    final int position = mDrawerListView.getCheckedItemPosition();

                    mDrawerListView.setItemChecked(position, false);
                    selectItem(position);
                }

                public void onDrawerOpened(final View drawerView) {
                }
            };

            if (ownIntent.getExtras() != null && ownIntent.hasExtra(EXTRA_TOGGLE_MAP)) {
                HashMap<String, ToggleSettingsModel> filterValues = SystemUtil.dynamicDownCast(ownIntent.getSerializableExtra(EXTRA_TOGGLE_MAP), HashMap.class);
                if (filterValues != null) {
                    channelController.setRecommendedChannelFilterValues(filterValues);
                }
            }

            //keine notification erst wenn gescrollt wird
            notificationController.toggleIgnoreAll(true);

            // Set the drawer toggle as the DrawerListener
            mDrawerLayout.addDrawerListener(mDrawerToggle);

            registerFcm();

            // BUG 39526
            //mDontCreateMessageReceivedListener = true;

            //initialisiert mImageLoader
            mImageLoader = initImageLoader(mChatImageController, channelController);

            //initialisiert chatList
            initChatList();

            chatOverviewController.addListener(this);

            if (!mPreferencesController.hasOldContactsMerged()) {
                requestPermission(PermissionUtil.PERMISSION_FOR_READ_CONTACTS, R.string.permission_rationale_contacts, new PermissionUtil.PermissionResultCallback() {
                    @Override
                    public void permissionResult(int permission, boolean permissionGranted) {

                        boolean hasPerm = permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS && permissionGranted;
                        if (hasPerm) {
                            showIdleDialog(R.string.chat_overview_wait_hint_loading2);
                        }

                        boolean isMerge = !mPreferencesController.hasOldContactsMerged();

                        initContactsListener();
                        contactController.syncContacts(initialContactsListener, isMerge, hasPerm);
                    }
                });
            }

            if (loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
                mSwipeLayout.setRefreshing(true);
                chatOverviewController.loadChatOverviewItems();
            }

            final int slideheigth = (int) getResources().getDimension(R.dimen.chatoverview_slideheight);

            mAnimationSlideIn = new TranslateAnimation(0, 0, slideheigth, 0);
            mAnimationSlideOut = new TranslateAnimation(0, 0, 0, slideheigth);

            mAnimationSlideIn.setDuration(ANIMATION_DURATION);
            mAnimationSlideOut.setDuration(ANIMATION_DURATION);

            DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

            mAnimationSlideIn.setInterpolator(decelerateInterpolator);
            mAnimationSlideOut.setInterpolator(decelerateInterpolator);

            final float pivotValue = 0.5f;
            mAnimationZoomIn = new ScaleAnimation(0, 1, 0, 1, Animation.RELATIVE_TO_SELF, pivotValue, Animation.RELATIVE_TO_SELF, pivotValue);
            mAnimationZoomOut = new ScaleAnimation(1, 0, 1, 0, Animation.RELATIVE_TO_SELF, pivotValue, Animation.RELATIVE_TO_SELF, pivotValue);

            mAnimationZoomIn.setFillAfter(true);
            mAnimationZoomOut.setFillAfter(true);

            mAnimationZoomIn.setDuration(100);
            mAnimationZoomOut.setDuration(ANIMATION_DURATION);

            if (!appConnectivity.isConnected()) {
                Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG).show();
            }

            mListRefreshedListener = new ListRefreshedListener() {
                @Override
                public void onListRefreshed(final List<BaseChatOverviewItemVO> messages) {
                    addChatListItems(messages);
                }
            };

            chatOverviewController.setListRefreshedListener(mListRefreshedListener);
            contactController.registerOnContactProfileInfoChangeNotification(this);

            mMessageController = getSimsMeApplication().getMessageController();
            mMessageController.registerChatOverviewActivityAsListener(this);

            mOnTimedMessagesDeliveredListener = new OnTimedMessagesDeliveredListener() {
                @Override
                public void timedMessageDelivered(final List<String> chatGuids) {
                    final Handler handler = new Handler(getMainLooper());
                    final Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (chatGuids != null && chatGuids.size() > 0) {
                                chatOverviewController.chatChanged(chatGuids, null, null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                            }
                        }
                    };
                    handler.post(runnable);
                }
            };

            mMessageController.addTimedMessagedDeliveredListener(mOnTimedMessagesDeliveredListener);

            mServiceController.addServiceChangedListener(this);

            mPreferencesController.checkRecoveryCodeToBeSet(false);
            mPreferencesController.checkPublicOnlineStateSet();

        } catch (final LocalizedException le) {
            LogUtil.w(TAG, le.getMessage(), le);
        }
    }

    private ImageLoader initImageLoader(final ChatImageController chatImageController,
                                        final ChannelController channelController) {
        //Image Loader zum Laden der ChatoverviewItems Icons
        ImageLoader imageLoader = new ImageLoader(this, ChatImageController.SIZE_CHAT_OVERVIEW, false) {
            @Override
            protected Bitmap processBitmap(Object data) {
                try {
                    if (data instanceof ChannelController.ChannelIdentifier) {
                        ChannelController.ChannelIdentifier values = (ChannelController.ChannelIdentifier) data;

                        if ((values.getGuid() != null) && (values.getType() != null)) {
                            return channelController.loadImage(values.getGuid(), values.getType());
                        }
                    } else {
                        // This gets called in a background thread
                        return chatImageController.getImageByGuidWithoutCacheing((String) data, getImageSize());
                    }

                    return null;
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, "Image can't be loaded.", e);
                    return null;
                }
            }

            @Override
            protected void processBitmapFinished(Object data, ImageView imageView) {
                //Nothing to do
            }
        };

        imageLoader.addImageCache(getSupportFragmentManager(), 0.1f);
        imageLoader.setImageFadeIn(false);
        chatImageController.addListener(imageLoader);

        return imageLoader;
    }

    @Override
    protected void onDestroy() {
        if (contactController != null) {
            contactController.unregisterOnContactProfileInfoChangeNotification(this);
        }

        if (mMessageController != null) {
            mMessageController.unregisterChatOverviewActivityAsListener();
            mMessageController.removeTimedMessagedDeliveredListener(mOnTimedMessagesDeliveredListener);
        }

        if (mServiceController != null) {
            mServiceController.removeServiceChangedListener(this);
        }

        if (chatOverviewController != null) {
            chatOverviewController.removeListener(this);
        }

        super.onDestroy();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_chats_overview;
    }

    @Override
    public void onServicesChanged(boolean hasUnsubscribedServices) {
        if (!ConfigUtil.INSTANCE.servicesEnabled()) {
            return;
        }

        if (hasUnsubscribedServices) {
            if (!mShowServices) {
                mShowServices = true;
            }
            chatOverviewController.loadChatOverviewItems();
        }
    }

    @Override
    protected void onResumeActivity() {

        //Detect DoubleTap SIMSME-6610
        mFirstTouch = true;

        if (mAccountController.getAccount() == null) {
            //sonst crash beim account loeschen, da hier zu dieser activity zurueckgekehrt wird
            finish();
            return;
        }

        boolean tc = mPreferencesController.hasThemeChanged();
        if(tc) {
            LogUtil.d(TAG, "UI Theme change detected: " + mPreferencesController.getThemeName());
            mPreferencesController.setThemeChanged(false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recreate();
                }
            });
            return;
        }


        if (mAfterCreate) {
            //check if push preview is enabled and key is available
            checkPushPreview();
        }

        // Eigenes TempDevice laden
        mAccountController.fetchOwnTempDevice();

        mDrawerListAdapter = new DrawerListAdapter(this, getSimsMeApplication(), R.layout.drawer_list_item, createListForDrawer(),
                mAccountController, mChatImageController);
        mDrawerListView.setAdapter(mDrawerListAdapter);
        mDrawerListAdapter.notifyDataSetChanged();

        //TODO verbessern
        chatOverviewController.setAdapter(chatsAdapter);
        chatOverviewController.setListRefreshedListener(mListRefreshedListener);
        chatOverviewController.setMode(ChatOverviewController.MODE_OVERVIEW);
        chatOverviewController.filterMessages();

        if (mFirstVisibleItemIndex > 2) {
            notificationController.toggleIgnoreAll(false);
        } else {
            notificationController.toggleIgnoreAll(true);
        }

        try {
            if (ConfigUtil.INSTANCE.channelsInviteFriends() && mPreferencesController.getNumberOfStartedChats() == NUMBER_OF_CHATS_FOR_INVITE_DIALOG) {
                if (createInviteFriendsPopup()) {
                    //inc number of chats, to prevent the dialog to be shown a second time
                    mPreferencesController.incNumberOfStartedChats();
                }
            }

            mOnBottomSheetClosedListener = new OnBottomSheetClosedListener() {
                @Override
                public void onBottomSheetClosed(final boolean bottomSheetWasOpen) {
                    if (bottomSheetWasOpen) {
                        mSpeedDialView.startAnimation(mAnimationZoomIn);
                    }
                }
            };

            closeBottomSheet(mOnBottomSheetClosedListener);

            if ((initialContactsListener != null) && contactController.isSyncingContacts()) {
                //mSwipeLayout.setRefreshing(true);
                contactController.addOnLoadContactsListener(initialContactsListener);
            }

            if (!mPreferencesController.getIsSendProfileNameSet()) {
                //Dialog wurde noch nicht angezeigt
                mPreferencesController.setSendProfileName(false);

                DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mPreferencesController.setSendProfileName(true);
                            mPreferencesController.setSendProfileNameSet();
                        } catch (LocalizedException e) {
                            LogUtil.w(TAG, e.getMessage(), e);
                        }
                    }
                };

                DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mPreferencesController.setSendProfileName(false);
                            mPreferencesController.setSendProfileNameSet();
                        } catch (LocalizedException e) {
                            LogUtil.w(TAG, e.getMessage(), e);
                        }
                    }
                };

                DialogBuilderUtil.buildResponseDialog(this,
                        getString(R.string.chats_overview_dialog_send_profle_name_text),
                        getString(R.string.chats_overview_dialog_send_profle_name_title),
                        getString(R.string.chats_overview_dialog_send_profle_name_positive),
                        getString(R.string.chats_overview_dialog_send_profle_name_negative),
                        positiveListener,
                        negativeListener
                ).show();
            }
            checkForUnsubscribedServices();

            //gucken, ob die Telefonnummer verifiziert werden muss
            checkPhoneRequestState();
            checkPhoneConfirmState();

            // Wenn sich die AppConfig geändert hat, dann Menü neu bauen
            if (RuntimeConfig.isBAMandant()) {
                if (mMdmConfigKey == null || !StringUtil.isEqual(mMdmConfigKey, RuntimeConfig.getClassUtil().getManagedConfigUtil(getSimsMeApplication()).getVersionKey())) {
                    initFabMenu();
                    mMdmConfigKey = RuntimeConfig.getClassUtil().getManagedConfigUtil(getSimsMeApplication()).getVersionKey();
                }
            }

            chatOverviewController.startCheckContactsOnlineTask();
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    protected void initFabMenu()
            throws LocalizedException {
        final SimsMeApplication simsMeApplication = getSimsMeApplication();

        final ColorUtil colorUtil = ColorUtil.getInstance();
        final int fabOverviewColor = colorUtil.getFabOverviewColor(simsMeApplication);
        final int fabOverviewIconColor = colorUtil.getFabIconOverviewColor(simsMeApplication);

        final int fabColor;
        final int fabIconColor;

        if (RuntimeConfig.isBAMandant()) {
            fabColor = colorUtil.getMainContrastColor(simsMeApplication);
            fabIconColor = colorUtil.getMainColor(simsMeApplication);
        } else {
            fabColor = colorUtil.getFabColor(simsMeApplication);
            fabIconColor = colorUtil.getFabIconColor(simsMeApplication);
        }

        if (mSpeedDialView != null) {
            mSpeedDialView.clearActionItems();
            mSpeedDialView = null;
        }
        mSpeedDialView = findViewById(R.id.speedDial);
        mSpeedDialView.setMainFabOpenedBackgroundColor(fabOverviewColor);
        mSpeedDialView.setMainFabClosedBackgroundColor(fabOverviewColor);
        final FloatingActionButton mainFab = mSpeedDialView.getMainFab();
        mainFab.getDrawable().setColorFilter(fabOverviewIconColor, PorterDuff.Mode.SRC_ATOP);
        mainFab.setRippleColor(fabOverviewColor);
        mSpeedDialView.setOnChangeListener(new SpeedDialView.OnChangeListener() {
            @Override
            public boolean onMainActionSelected() {
                return false;
            }

            @Override
            public void onToggleChanged(boolean b) {
                mainFab.getDrawable().setColorFilter(fabOverviewIconColor, PorterDuff.Mode.SRC_ATOP);
            }
        });
        final SpeedDialOverlayLayout overlayLayout = findViewById(R.id.overlay);

        mSpeedDialView.setOverlayLayout(overlayLayout);
        mSpeedDialView.setOnActionSelectedListener(new SpeedDialView.OnActionSelectedListener() {
            @Override
            public boolean onActionSelected(SpeedDialActionItem speedDialActionItem) {
                switch (speedDialActionItem.getId()) {
                    case R.id.fab_chatsoverview_new_chat: {
                        startNewSingleChat();
                        return false;
                    }
                    case R.id.fab_chatsoverview_new_group: {
                        startNewGroupChat();
                        return false;
                    }
                    case R.id.fab_chatsoverview_new_contact: {
                        createNewContact();
                        return false;
                    }
                    case R.id.fab_chatsoverview_new_direct_chat: {
                        startNewDirectChat();
                        return false;
                    }
                    case R.id.fab_chatsoverview_new_channel: {
                        addNewChannel();
                        return false;
                    }
                    case R.id.fab_chatsoverview_invite_friends: {
                        startInviteFriendsActivity();
                        return false;
                    }
                    default: {
                        return true;
                    }
                }
            }
        });

        if (!RuntimeConfig.isBAMandant()) {
            mSpeedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_invite_friends, R.drawable.nav_add)
                            .setFabBackgroundColor(fabColor)
                            .setFabImageTintColor(fabIconColor)
                            .setLabel(getString(R.string.chat_overview_invite_friends_popup_accept))
                            .setLabelColor(fabIconColor)
                            .setLabelBackgroundColor(fabColor)
                            .create()
            );
        }

        if (!mAccountController.getManagementCompanyIsUserRestricted()) {
            mSpeedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_new_contact, R.drawable.contact)
                            .setFabBackgroundColor(fabColor)
                            .setFabImageTintColor(fabIconColor)
                            .setLabel(getString(R.string.chat_overview_new_contact))
                            .setLabelColor(fabIconColor)
                            .setLabelBackgroundColor(fabColor)
                            .create()
            );
        }

        if (ConfigUtil.INSTANCE.servicesEnabled() && mShowServices) {
            mSpeedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_new_service, R.drawable.notification)
                            .setFabBackgroundColor(fabColor)
                            .setFabImageTintColor(fabIconColor)
                            .setLabel(getString(R.string.chat_overview_new_service))
                            .setLabelColor(fabIconColor)
                            .setLabelBackgroundColor(fabColor)
                            .create()
            );
        }

        if (ConfigUtil.INSTANCE.channelsEnabled()) {
            mSpeedDialView.addActionItem(
                    new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_new_channel, R.drawable.channels)
                            .setFabBackgroundColor(fabColor)
                            .setFabImageTintColor(fabIconColor)
                            .setLabel(getString(R.string.chat_overview_new_channel))
                            .setLabelColor(fabIconColor)
                            .setLabelBackgroundColor(fabColor)
                            .create()
            );
        }

        mSpeedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_new_group, R.drawable.groups)
                        .setFabBackgroundColor(fabColor)
                        .setFabImageTintColor(fabIconColor)
                        .setLabel(getString(R.string.chat_group_newTitle))
                        .setLabelColor(fabIconColor)
                        .setLabelBackgroundColor(fabColor)
                        .create()
        );

        mSpeedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_new_direct_chat, R.drawable.verteiler)
                        .setFabBackgroundColor(fabColor)
                        .setFabImageTintColor(fabIconColor)
                        .setLabel(getString(R.string.chat_overview_new_distributor))
                        .setLabelColor(fabIconColor)
                        .setLabelBackgroundColor(fabColor)
                        .create()
        );

        mSpeedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_chatsoverview_new_chat, R.drawable.single_chats)
                        .setFabBackgroundColor(fabColor)
                        .setFabImageTintColor(fabIconColor)
                        .setLabel(getString(R.string.chat_overview_new_chat))
                        .setLabelColor(fabIconColor)
                        .setLabelBackgroundColor(fabColor)
                        .create()
        );
    }

    private void checkPhoneRequestState()
            throws LocalizedException {
        if (AccountController.PENDING_PHONE_STATUS_WAIT_REQUEST.equals(mAccountController.getPendingPhoneStatus())) {
            final String pendingPhoneNumber = mAccountController.getPendingPhoneNumber();

            if (!StringUtil.isNullOrEmpty(pendingPhoneNumber)) {

                final String title = getResources().getString(R.string.pending_phone_alert_request_title);
                final String text = getResources().getString(R.string.pending_phone_alert_request_text);
                final String yes = getResources().getString(R.string.pending_phone_alert_request_request);
                final String no = getResources().getString(R.string.pending_phone_alert_request_abort);

                final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {

                        final Intent intent = new Intent(ChatsOverviewActivity.this, ChangePhoneActivity.class);
                        intent.putExtra(ChangePhoneActivity.PREFILLED_PHONENUMBER, pendingPhoneNumber);
                        startActivity(intent);
                    }
                };

                final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        try {
                            mAccountController.unsetPendingPhoneStatus();
                        } catch (final LocalizedException e) {
                            LogUtil.w(TAG, e.getMessage(), e);
                        }
                    }
                };

                if (mAccountController.isDeviceManaged()) {
                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, text, title, yes, positiveClickListener).show();
                } else {
                    DialogBuilderUtil.buildResponseDialog(ChatsOverviewActivity.this, text, title, yes, no, positiveClickListener, negativeClickListener).show();
                }
            }
        }
    }

    private void checkPhoneConfirmState()
            throws LocalizedException {
        if (AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM.equals(mAccountController.getPendingPhoneStatus())) {
            if (mVerifyPhoneDialog == null) {
                final String title = getResources().getString(R.string.pending_phone_alert_verify_title);
                final String text = getResources().getString(R.string.pending_phone_alert_verify_text);
                final String yes = getResources().getString(R.string.pending_phone_alert_verify_verify);
                final String no = getResources().getString(R.string.pending_phone_alert_later);

                final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final Intent intent = new Intent(ChatsOverviewActivity.this, ConfirmPhoneActivity.class);
                        startActivity(intent);
                        mVerifyPhoneDialog = null;
                    }
                };

                final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        mVerifyPhoneDialog = null;
                    }
                };

                final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildResponseDialog(ChatsOverviewActivity.this, text, title, yes, no, positiveClickListener, negativeClickListener);
                alertDialogWrapper.show();
                mVerifyPhoneDialog = alertDialogWrapper.getDialog();
            } else if (!mVerifyPhoneDialog.isShowing()) {
                mVerifyPhoneDialog.show();
            }
        }
    }

    private void checkPushPreview() {
        //Preview ist default an, aber Key wurde nicht erzeugt
        if (SystemUtil.hasMarshmallow() && !getSimsMeApplication().getPreferencesController().hasNotificationPreviewSetting() &&
                !getSimsMeApplication().getPreferencesController().isNotificationPreviewDisabledByAdmin()) {
            final String title = getResources().getString(R.string.notification_preview_askafterinstall_title);
            final String text = getResources().getString(R.string.notification_preview_askafterinstall_hint);
            final String yes = getResources().getString(R.string.notification_preview_askafterinstall_now);
            final String no = getResources().getString(R.string.notification_preview_askafterinstall_later);

            final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    try {
                        getSimsMeApplication().getPreferencesController().setNotificationPreviewEnabled(true, true);
                    } catch (LocalizedException e) {
                        LogUtil.w(ChatsOverviewActivity.class.getSimpleName(), "checkPushPreview", e);
                    }
                }
            };

            final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    try {
                        getSimsMeApplication().getPreferencesController().setNotificationPreviewEnabled(false, true);
                    } catch (LocalizedException e) {
                        LogUtil.w(ChatsOverviewActivity.class.getSimpleName(), "checkPushPreview", e);
                    }
                }
            };

            final AlertDialogWrapper alertDialogWrapper = DialogBuilderUtil.buildResponseDialog(ChatsOverviewActivity.this, text, title, yes, no, positiveClickListener, negativeClickListener);
            alertDialogWrapper.show();
        }
    }

    // KS: TODO: This method is wrong here!
    private void registerFcm() {
        GCMController gcmController = getSimsMeApplication().getGcmController();

        if ((gcmController.checkPlayServices(this))
                && getSimsMeApplication().getLoginController().getState().equals(LoginController.STATE_LOGGED_IN)) {

            gcmController.registerForGCM(new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    LogUtil.i(TAG, "Successfully registered with FCM.");
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    String error = message;
                    if (StringUtil.isNullOrEmpty(error)) {
                        error = getString(R.string.gcm_notifcation_registration_failed);
                    }

                    if (!StringUtil.isNullOrEmpty(errorIdent)) {
                        error = error + "\n(" + errorIdent + ")";
                    }
                    LogUtil.w(TAG, "A problem occurred while registering with FCM: " + error);
                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, error);
                }
            });
        }
    }

    @Override
    protected void onPauseActivity() {
        mSwipeLayout.setRefreshing(false);
        contactController.removeOnLoadContactsListener(initialContactsListener);
        notificationController.toggleIgnoreAll(false);
        super.onPauseActivity();
    }

    @Override
    public void handleCloseBottomSheetClick(final View view) {
        closeBottomSheet(mOnBottomSheetClosedListener);
        if (contextMenuDialog != null) {
            contextMenuDialog.dismiss();
            contextMenuDialog = null;
        }
    }

    public void handleClearChatClick(View view) {
        closeBottomSheet(mOnBottomSheetClosedListener);
        if (mMarkedChat == null) {
            return;
        }

        if (mMarkedChat.getType() == Chat.TYPE_SINGLE_CHAT) {
            DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {

                    showIdleDialog();
                    singleChatController.clearChat(mMarkedChat, new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            dismissIdleDialog();
                            chatOverviewController.chatChanged(null, mMarkedChat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_CLEAR_CHAT);
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this,
                                    message).show();
                        }
                    });
                }
            };

            DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                }
            };

            String title = getResources().getString(R.string.chats_clear_chat);
            String positiveButton = getResources().getString(R.string.chats_clear_chat);
            String message = getResources().getString(R.string.chat_button_clear_confirm);

            String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

            final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                    title,
                    positiveButton,
                    negativeButton,
                    positiveOnClickListener,
                    negativeOnClickListener);

            dialog.show();
        } else if (mMarkedChat.getType() == Chat.TYPE_GROUP_CHAT) {
            DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                    showIdleDialog();
                    groupChatController.clearChat(mMarkedChat, new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            dismissIdleDialog();
                            chatOverviewController.chatChanged(null, mMarkedChat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_CLEAR_CHAT);
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, message).show();
                        }
                    });
                }
            };

            DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                }
            };

            String title = getResources().getString(R.string.chats_clear_chat);
            String positiveButton = getResources().getString(R.string.chats_clear_chat);
            String message = getResources().getString(R.string.chat_button_clear_confirm_group);

            String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

            final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                    title,
                    positiveButton,
                    negativeButton,
                    positiveOnClickListener,
                    negativeOnClickListener);

            dialog.show();
        } else if (mMarkedChat.getType() == Chat.TYPE_CHANNEL) {
            DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                    showIdleDialog();
                    channelChatController.clearChat(mMarkedChat, new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            dismissIdleDialog();
                            chatOverviewController.chatChanged(null, mMarkedChat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_CLEAR_CHAT);
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this,
                                    message).show();
                        }
                    });
                }
            };

            DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                }
            };

            String title = getResources().getString(R.string.chats_clear_chat);
            String positiveButton = getResources().getString(R.string.chats_clear_chat);
            String message = getResources().getString(R.string.chat_button_clear_confirm_channel);

            String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

            final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                    title,
                    positiveButton,
                    negativeButton,
                    positiveOnClickListener,
                    negativeOnClickListener);

            dialog.show();
        }

        if (contextMenuDialog != null) {
            contextMenuDialog.dismiss();
            contextMenuDialog = null;
        }
    }

    public void handleDeleteChatClick(View view) {
        closeBottomSheet(mOnBottomSheetClosedListener);
        if (mMarkedChat == null) {
            return;
        }
        if ((mMarkedChat.getType() == Chat.TYPE_SINGLE_CHAT)
                || (mMarkedChat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION)) {
            final Chat lMarkedChat = mMarkedChat;

            DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                    showIdleDialog();
                    singleChatController.deleteChat(lMarkedChat.getChatGuid(), true, mOnDeleteTimedMessageListener);
                }
            };

            DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                }
            };

            String title = getResources().getString(R.string.chats_delete_chat);
            String positiveButton = getResources().getString(R.string.chats_delete_chat);
            String message = getResources().getString(R.string.chat_button_delete_confirm);

            String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

            final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                    title,
                    positiveButton,
                    negativeButton,
                    positiveOnClickListener,
                    negativeOnClickListener);

            dialog.show();
        } else if (mMarkedChat.getType() == Chat.TYPE_GROUP_CHAT) {
            try {
                final String owner = mMarkedChat.getOwner();
                final String chatGuid = mMarkedChat.getChatGuid();

                DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        OnRemoveRoomListener onRemoveRoomListener = new OnRemoveRoomListener() {
                            @Override
                            public void onRemoveRoomSuccess() {
                                chatOverviewController.chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                                chatOverviewController.filterMessages();
                            }

                            @Override
                            public void onRemoveRoomFail(String message) {
                                if (message.contains(LocalizedException.ROOM_UNKNOWN)) {
                                    //Raum wurde nicht mehr gefunden - eventuell bereits vom Server gelöscht
                                    chatOverviewController.chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                                    chatOverviewController.filterMessages();
                                }
                                DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, message).show();
                            }
                        };

                        try {
                            groupChatController.removeRoom(chatGuid, onRemoveRoomListener);
                        } catch (LocalizedException e) {
                            LogUtil.w(TAG, e.getMessage(), e);
                            DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this,
                                    getString(R.string.chat_group_delete_failed)).show();
                        }
                    }
                };

                DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                    }
                };

                String message;
                String positiveButton;
                String title;

                if (Chat.ROOM_TYPE_RESTRICTED.equals(mMarkedChat.getRoomType())) {
                    message = getResources().getString(R.string.chat_button_delete_confirm);
                    positiveButton = getResources().getString(R.string.chats_delete_chat);
                    title = getResources().getString(R.string.chats_delete_chat);
                } else if (Chat.ROOM_TYPE_MANAGED.equals(mMarkedChat.getRoomType())) {
                    message = getResources().getString(R.string.chat_group_button_delete_confirm);
                    positiveButton = getResources().getString(R.string.chat_group_button_delete);
                    title = getResources().getString(R.string.chat_group_button_delete);
                } else {

                    if (owner != null && owner.equals(mAccountController.getAccount().getAccountGuid())) {
                        message = getResources().getString(R.string.chat_group_leave_confirm_admin);
                        positiveButton = getResources().getString(R.string.chat_group_button_delete);
                        title = getResources().getString(R.string.chat_group_button_delete);
                    } else {
                        message = getResources().getString(R.string.chat_group_leave_confirm);
                        positiveButton = getResources().getString(R.string.chat_group_button_leave);
                        title = getResources().getString(R.string.chat_group_button_leave);
                    }
                }

                String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

                final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message, title,
                        positiveButton, negativeButton,
                        positiveOnClickListener,
                        negativeOnClickListener);

                dialog.show();
            } catch (NotFoundException | LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
            }
        } else if (mMarkedChat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
            showIdleDialog(-1);

            OnDeclineInvitationListener onDeclineInvitationListener = new OnDeclineInvitationListener() {
                @Override
                public void onDeclineSuccess(Chat chat) {
                    chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                    dismissIdleDialog();
                    chatOverviewController.filterMessages();
                }

                @Override
                public void onDeclineError(Chat chat,
                                           String message,
                                           boolean chatWasRemoved) {
                    dismissIdleDialog();

                    if (chatWasRemoved) {
                        chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                    }

                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, message).show();
                }
            };

            groupChatController.declineInvitation(mMarkedChat, onDeclineInvitationListener);
        } else if (mMarkedChat.getType() == Chat.TYPE_CHANNEL) {
            final Dialog idleDialog = DialogBuilderUtil.buildProgressDialog(this, -1);
            final Channel channel = channelController.getChannelFromDB(mMarkedChat.getChatGuid());

            if (channel != null) {
                DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        idleDialog.show();

                        channelController.cancelChannelSubscription(channel.getGuid(),
                                channel.getType(),
                                new ChannelAsyncLoaderCallback<String>() {
                                    @Override
                                    public void asyncLoaderFinishedWithSuccess(String result) {
                                        channel.setIsSubscribed(false);
                                        channelController.updateChannel(channel);

                                        channelChatController.deleteChat(channel.getGuid(), true, null);

                                        if (StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
                                            checkForUnsubscribedServices();
                                        }
                                        chatOverviewController.chatChanged(null, channel.getGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                                        idleDialog.dismiss();
                                        chatOverviewController.filterMessages();
                                    }

                                    @Override
                                    public void asyncLoaderFinishedWithError(String message) {
                                        chatOverviewController.chatChanged(null, channel.getGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                                        idleDialog.dismiss();
                                        DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this,
                                                message).show();
                                    }
                                });
                    }
                };

                DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                    }
                };

                String title;
                String positiveButton;
                String message;
                String negativeButton = getResources().getString(R.string.std_cancel);
                if (StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
                    title = getResources().getString(R.string.channel_leave_confirm_title_service);
                    positiveButton = getResources().getString(R.string.channel_settings_unsubscribe_service);
                    message = getResources().getString(R.string.channel_leave_confirm_service);
                } else {
                    title = getResources().getString(R.string.channel_subscribe_button_cancel);
                    positiveButton = getResources().getString(R.string.channel_settings_unsubscribe);
                    message = getResources().getString(R.string.channel_leave_confirm);
                }

                AlertDialogWrapper alert = DialogBuilderUtil.buildResponseDialog(this, message,
                        title,
                        positiveButton,
                        negativeButton,
                        positiveOnClickListener,
                        negativeOnClickListener);

                alert.show();
            }
        }
        mMarkedChat = null;
        if (contextMenuDialog != null) {
            contextMenuDialog.dismiss();
            contextMenuDialog = null;
        }
    }

    public void handleDeclineClick(View view) {
        if (mBottomSheetOpen) {
            return;
        }

        final Chat chat = (Chat) view.getTag();
        final ChatController chatController = getChatControllerForChat(chat);

        showIdleDialog(-1);

        final OnDeclineInvitationListener onDeclineInvitationListener = new OnDeclineInvitationListener() {
            @Override
            public void onDeclineSuccess(Chat chat) {
                chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                dismissIdleDialog();
            }

            @Override
            public void onDeclineError(Chat chat,
                                       String message,
                                       boolean chatWasRemoved) {
                if (chatWasRemoved) {
                    chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                }
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, message).show();
            }
        };

        DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                if (chatController != null) {
                    showIdleDialog(-1);
                    chatController.declineInvitation(chat, onDeclineInvitationListener);
                } else {
                    dismissIdleDialog();
                }
            }
        };

        DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                dismissIdleDialog();
            }
        };

        String message;

        if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
            message = getResources().getString(R.string.chat_decline_invitation_text_group);
        } else {
            message = getResources().getString(R.string.chat_decline_invitation_text);
        }

        String title = getResources().getString(R.string.chat_decline_invitation_title);
        String positiveButton = getResources().getString(R.string.general_yes);
        String negativeButton = getResources().getString(R.string.general_no);

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message, title, positiveButton,
                negativeButton, positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

    public void handleAcceptClick(View view) {
        if (mBottomSheetOpen) {
            return;
        }

        final Chat chat = (Chat) view.getTag();
        ChatController chatController = getChatControllerForChat(chat);

        showIdleDialog(-1);

        if (chatController != null) {
            OnAcceptInvitationListener onAcceptInvitationListener = new OnAcceptInvitationListener() {
                @Override
                public void onAcceptSuccess(Chat chat) {
                    chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_ACCEPT_CHAT);
                    openChat(chat);
                    ChatsOverviewActivity.this.dismissIdleDialog();
                }

                @Override
                public void onAcceptError(String message, boolean chatWasRemoved) {
                    ChatsOverviewActivity.this.dismissIdleDialog();

                    if (chatWasRemoved) {
                        chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                    }

                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, message).show();
                }
            };
            chatController.acceptInvitation(chat, onAcceptInvitationListener);
        } else {
            dismissIdleDialog();
        }
    }

    public void handleExportChatClick(final View view) {
        if (SystemUtil.hasMarshmallow()) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    new PermissionUtil.PermissionResultCallback() {
                        @Override
                        public void permissionResult(int permission,
                                                     boolean permissionGranted) {
                            if ((permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE)
                                    && permissionGranted) {
                                exportChat();
                            }
                        }
                    });
        } else {
            exportChat();
        }
    }

    private void exportChat() {
        if (mMarkedChat == null) {
            return;
        }
        if (mMarkedChat.getType() != Chat.TYPE_SINGLE_CHAT && mMarkedChat.getType() != Chat.TYPE_GROUP_CHAT) {
            return;
        }

        ChatOverviewController.OnChatExportedListener listener = new ChatOverviewController.OnChatExportedListener() {
            @Override
            public void onChatExportSuccess(File file) {
                dismissIdleDialog();

                if(!router.shareFile(file, getResources().getText(R.string.share_file_titel).toString())){
                    String msg = getString(R.string.chat_open_file_no_extern_activity);
                    DialogBuilderUtil.buildErrorDialog(ChatsOverviewActivity.this, msg).show();
                }
            }

            @Override
            public void onChatExportFail(final String message) {
                Toast.makeText(ChatsOverviewActivity.this, message, Toast.LENGTH_LONG).show();
                dismissIdleDialog();
            }
        };

        chatOverviewController.exportChat(this, mMarkedChat, listener);
        showIdleDialog(-1);
        closeBottomSheet(mOnBottomSheetClosedListener);
    }

    private void checkForUnsubscribedServices() {
        try {
            final boolean hasUnsubscribedServices = mServiceController.hasUnsubscribedServices();

            if (mShowServices != hasUnsubscribedServices) {
                mShowServices = hasUnsubscribedServices;
                initFabMenu();
            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onChatDataChanged(final boolean clearImageCache) {
        checkForUnsubscribedServices();

        LogUtil.i(TAG, "onChatDataChanged");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (chatsAdapter != null) {
                    if (clearImageCache) {
                        clearImageLoader();
                    }
                    chatsAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public void onChatDataLoaded(long lastMessageId) {
        LogUtil.i(TAG, "onChatDataLoaded");

        mSwipeLayout.setRefreshing(false);
    }

    @Override
    public void onClick(@NotNull BaseChatOverviewItemVO item) {
        if (mBottomSheetOpen) {
            return;
        }

        //Detect DoubleTap SIMSME-6610
        if (mFirstTouch) {
            openChat(item.chat);
        }
    }

    @Override
    public boolean onLongClick(@NotNull BaseChatOverviewItemVO item) {
        if (mBottomSheetOpen) {
            return true;
        }

        mMarkedChat = item.chat;

        final List<Integer> disabledCommands = new ArrayList<>();

        int bottomSheetLayoutResourceID;

        if (mMarkedChat.getType() == Chat.TYPE_GROUP_CHAT) {
            try {
                String owner = mMarkedChat.getOwner();

                if (Chat.ROOM_TYPE_MANAGED.equals(mMarkedChat.getRoomType())) {
                    if (mMarkedChat.getIsRemoved() == null || !mMarkedChat.getIsRemoved()) {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_channel_clear_only_layout;
                    } else {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_delete_chat_group_owner_layout;
                    }
                } else if (Chat.ROOM_TYPE_RESTRICTED.equals(mMarkedChat.getRoomType())) {
                    if (mMarkedChat.getIsRemoved() == null || !mMarkedChat.getIsRemoved()) {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_channel_clear_only_layout;
                    } else {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_delete_chat_layout;
                    }
                } else if (owner != null) {
                    if (owner.equals(mAccountController.getAccount().getAccountGuid())) {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_delete_chat_group_owner_layout;
                    } else {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_delete_chat_group_member_layout;
                    }
                } else {
                    bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_delete_chat_layout;
                }
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                bottomSheetLayoutResourceID = -1;
            }
        } else if (mMarkedChat.getType() == Chat.TYPE_SINGLE_CHAT) {
            if (mMarkedChat.getChatGuid().equals(AppConstants.GUID_SYSTEM_CHAT)) {
                bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_systemchat_layout;
            } else {
                bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_delete_chat_layout;
            }
        } else if (mMarkedChat.getType() == Chat.TYPE_CHANNEL) {
            try {
                final Channel channel = channelController.getChannelFromDB(mMarkedChat.getChatGuid());
                if (channel != null && channelController.isChannelMandatory(channel)) {
                    bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_channel_clear_only_layout;
                } else {
                    if (channel != null && StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_service_unsubscribe_layout;
                    } else {
                        bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_channel_unsubscribe_layout;
                    }
                }
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_channel_unsubscribe_layout;
            }
        } else {
            bottomSheetLayoutResourceID = -1;
        }
        if (!getSimsMeApplication().getPreferencesController().isExportEnabled()) {
            disabledCommands.add(R.id.chat_overview_export_chat);
        }

        openBottomSheet(bottomSheetLayoutResourceID, R.id.chats_overview_fragment_container, disabledCommands, R.id.chat_overview_conversation_title, item.getTitle());

        mSpeedDialView.startAnimation(mAnimationZoomOut);
        return true;
    }

    private void openChat(Chat chat) {

        if (chat == null || chat.getType() == null) {
            return;
        }

        if (chat.getType() == Chat.TYPE_SINGLE_CHAT) {
            try {
                Contact contact = contactController.getContactByGuid(chat.getChatGuid());

                if ((contact == null) || StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
                    return;
                }

                //Detect DoubleTap SIMSME-6610
                mFirstTouch = false;

                if (contact.getAccountGuid().equals(AppConstants.GUID_SYSTEM_CHAT)) {
                    Intent intent = new Intent(this, SystemChatActivity.class);

                    intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, contact.getAccountGuid());
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, SingleChatActivity.class);

                    intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, contact.getAccountGuid());
                    startActivity(intent);
                }
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
            }
        } else if (chat.getType() == Chat.TYPE_GROUP_CHAT) {
            //Detect DoubleTap SIMSME-6610
            mFirstTouch = false;

            Intent intent = new Intent(this, GroupChatActivity.class);

            intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, chat.getChatGuid());
            startActivity(intent);
        } else if (chat.getType() == Chat.TYPE_CHANNEL) {
            //Detect DoubleTap SIMSME-6610
            mFirstTouch = false;

            Intent intent = new Intent(this, ChannelChatActivity.class);

            intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, chat.getChatGuid());
            startActivity(intent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (chatsRecycleView != null && intent.hasExtra(EXTRA_SCROLL_UP)) {
            chatsRecycleView.smoothScrollToPosition(0);
        }
    }

    private void clearImageLoader() {
        try {
            FragmentManager fm = getSupportFragmentManager();
            if (mImageLoader != null && fm != null) {
                ImageCache.deleteImageCache(fm);
                mImageLoader.addImageCache(fm, 0.1f);
            }
        } catch (IllegalStateException e) {
            LogUtil.e(TAG, "clearImageLoader()", e);
        }
    }

    @Override
    public void onContactProfilInfoHasChanged(String contactGuid) {
        if (chatOverviewController != null) {
            chatOverviewController.chatChanged(null, contactGuid, null, ChatOverviewController.CHAT_CHANGED_TITLE);
        }
    }

    @Override
    public void onContactProfilImageHasChanged(String contactguid) {
        onChatDataChanged(true);
    }

    @Override
    public void onSaveMessageSuccess(Message message) {
        chatOverviewController.chatChanged(null, null, message, ChatOverviewController.CHAT_CHANGED_NEW_SEND_MSG);
    }

    @Override
    public void onSendMessageSuccess(Message message, int countNotSendMessages) {
        //wird momentan mittels onMessageChanged in ChatsoverviewController gehandelt
    }

    @Override
    public void onSendMessageError(Message message, String errorMessage, String localizedErrorIdentifier) {
        //wird momentan mittels onMessageChanged in ChatsoverviewController gehandelt
    }

    private ChatController getChatControllerForChat(Chat chat) {
        if ((chat.getType() == Chat.TYPE_SINGLE_CHAT) || (chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION)) {
            return singleChatController;
        } else if ((chat.getType() == Chat.TYPE_GROUP_CHAT) || (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION)) {
            return groupChatController;
        }

        return null;
    }

    private boolean createInviteFriendsPopup() {
        if (((mInviteFriendsDialog != null) && mInviteFriendsDialog.getDialog().isShowing()) || !this.isActivityInForeground
                || this.isFinishing()) {
            return false;
        }

        String title = getResources().getString(R.string.chat_overview_invite_friends_popup_title_second);
        String message = getResources().getString(R.string.chat_overview_invite_friends_popup_text_second);

        String positiveButton = getResources().getString(R.string.chat_overview_invite_friends_popup_accept);
        String negativeButton = getResources().getString(R.string.general_no_thank);

        DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                inviteFriendUseCase.execute(ChatsOverviewActivity.this);

                dialog.dismiss();
            }
        };

        DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                dialog.dismiss();
            }
        };

        mInviteFriendsDialog = DialogBuilderUtil.buildResponseDialog(this, message, title, positiveButton, negativeButton,
                positiveOnClickListener, negativeOnClickListener);
        mInviteFriendsDialog.show();

        return true;
    }

    protected void addChatListItems(final List<BaseChatOverviewItemVO> rc) {
        // wird noch durch fcdp ueberschrieben
    }

    public void onCloseButtonNoticeLayoutClick(View v) {

    }

    public void onButtonNoticeLayoutClick(View v) {

    }

    public interface ListRefreshedListener {
        void onListRefreshed(final List<BaseChatOverviewItemVO> messages);
    }

    private class DrawerItemClickListener
            implements ListView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            mDrawerListView.setItemChecked(position, true);

            mDrawerLayout.closeDrawer(mDrawerView);
        }
    }
}
