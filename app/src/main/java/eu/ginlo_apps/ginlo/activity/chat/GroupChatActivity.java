// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chat;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import eu.ginlo_apps.ginlo.ChatRoomInfoActivity;
import eu.ginlo_apps.ginlo.MuteChatActivity;
import eu.ginlo_apps.ginlo.OnLinkClickListener;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.GroupInfoChangedListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class GroupChatActivity
        extends BaseChatActivity
        implements OnLinkClickListener, ContactController.OnContactProfileInfoChangeNotification {

    private final static String TAG = GroupChatActivity.class.getSimpleName();

    private OnClickListener mOnRightActionBarClickListener;
    private GroupChatController mGroupChatController;
    private Timer mRefreshTimer;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);

            mClearChatQuestionText = R.string.chat_button_clear_confirm_group;

            LogUtil.i(TAG, "onCreate: " + this);

            mOnSendMessageListener = createOnSendMessageListener(mTargetGuid);
            getChatController().addListener(this);

            if (mChat == null) {
                LogUtil.w(TAG, "Load Chat failed " + mTargetGuid);
                finish();
                return;
            }

            mOnRightActionBarClickListener = new OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        if (mChat.getIsRemoved() != null && mChat.getIsRemoved()) {
                            // Chat wurde während der Anzeige geändert
                            return;
                        }

                        final Intent intent;
                        if (Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())) {
                            intent = new Intent(GroupChatActivity.this, MuteChatActivity.class);
                            intent.putExtra(MuteChatActivity.EXTRA_CHAT_GUID, mTargetGuid);
                        } else {
                            intent = new Intent(GroupChatActivity.this, ChatRoomInfoActivity.class);

                            intent.putExtra(ChatRoomInfoActivity.EXTRA_CHAT_GUID, mChat.getChatGuid());

                            String owner = mChat.getOwner();

                            if ((owner != null) && owner.equals(mAccountController.getAccount().getAccountGuid())) {
                                intent.putExtra(ChatRoomInfoActivity.EXTRA_MODE, ChatRoomInfoActivity.MODE_EDIT);
                            } else if (((GroupChatController) getChatController()).amIGroupAdmin(mChat)) {
                                intent.putExtra(ChatRoomInfoActivity.EXTRA_MODE, ChatRoomInfoActivity.MODE_ADMIN);
                            } else {
                                intent.putExtra(ChatRoomInfoActivity.EXTRA_MODE, ChatRoomInfoActivity.MODE_INFO);
                            }
                        }

                        startActivity(intent);
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                }
            };
            final View titleContainer = getToolbar().findViewById(R.id.toolbar_title_container);
            titleContainer.setOnClickListener(mOnRightActionBarClickListener);

            final OnClickListener rightClickListener = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleTimedMessages();
                }
            };
            setRightActionBarImage(R.drawable.chat_timed_white, rightClickListener, getResources().getString(R.string.content_description_chat_timed), -1);

            setRightActionBarImageVisibility(View.GONE);
            mTimedCounterView.setVisibility(View.GONE);
            createOnDeleteTimedMessageListener();
            createOnTimedMessagesDeliveredListener();
            preventSelfConversation();

            if (savedInstanceState != null) {
                try {
                    mChatInputFragment = (ChatInputFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                            "chatInputFragment");
                    mEmojiconsFragment = (EmojiPickerFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                            "mEmojiconsFragment");
                } catch (Exception e) {
                    LogUtil.w(TAG, "onCreateActivity: " + e.getMessage(), e);
                }
            }

            if (mChatInputFragment == null) {
                mChatInputFragment = new ChatInputFragment();
                mChatInputFragment.setShowKeyboardAfterClosingDestructionPicker(true);
                getSupportFragmentManager().beginTransaction()
                        .add(mChatInputContainerId, mChatInputFragment).commit();
            }

            if (mEmojiconsFragment == null) {
                mEmojiconsFragment = new EmojiPickerFragment();
                getSupportFragmentManager().beginTransaction()
                        .add(mFragmentContainerId, mEmojiconsFragment).commit();
            }

            hideOrShowFragment(mEmojiconsFragment, false, false);
            hideOrShowFragment(mSelfdestructionFragment, false, false);

            mLoadMoreView = (LinearLayout) getLayoutInflater().inflate(R.layout.chat_item_load_more_layout, null);
            mLoadMoreView.setOnClickListener(onLoadMoreClickListener);

            if (isChatReadOnly()) {
                disableChatinput();
                setProfilePictureVisibility(View.GONE);
                mChatInputFragment.showKeyboard(false);
            }

            mContactController.registerOnContactProfileInfoChangeNotification(this);
            createOnChatDataChangedListener();

            // KS: ???
            if (mChat.getChatInfoIV() != null && !mAccountController.getManagementCompanyIsUserRestricted()) {
                // Ticket SIMSME-5300 es wird getROom benoeigt, um pushSilentTill auslesne zu koennen
                mGroupChatController.getAndUpdateRoomInfo(mChat.getChatGuid());
            } else {
                mGroupChatController.getAndUpdateRoomInfo(mChat.getChatGuid());
            }

        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected void onResumeActivity() {
        try {
            super.onResumeActivity();

            mRefreshTimer = new Timer();

            final TimerTask refreshTask = new TimerTask() {
                @Override
                public void run() {
                    new Handler(getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (!mChat.getIsRemoved()) {
                                    setMuteIcon();
                                }
                            } catch (final LocalizedException le) {
                                LogUtil.w(TAG, le.getMessage(), le);
                            }
                        }
                    });
                }
            };
            mRefreshTimer.scheduleAtFixedRate(refreshTask, 0, 5000);

            LogUtil.i(TAG, "onResume: " + this + "");
            if (mChat != null) {
                if (!mChat.getIsRemoved()) {
                    setMuteIcon();
                }
            }

            if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
                return;
            }
            init();

            if (mTargetGuid != null) {
                LogUtil.i(TAG, "Open ChatStream " + mTargetGuid);
                notificationController.ignoreGuid(mTargetGuid);
                notificationController.dismissNotification(NotificationController.MESSAGE_NOTIFICATION_ID);

                if (mChatAdapter == null) {
                    mChatAdapter = getChatController().getChatAdapter(this, mTargetGuid);
                    mChatAdapter.setOnLinkClickListener(this);
                    mChatAdapter.setShowTimedMessages(mOnlyShowTimed);
                    if (mChatAdapter.getCount() > 0) {
                        BaseChatItemVO item = mChatAdapter.getItem(mChatAdapter.getCount() - 1);
                        if (item != null) {
                            mLastMessageId = item.messageId;
                        }
                    }
                }
                getChatController().setCurrentChatAdapter(mChatAdapter);
                getChatController().countTimedMessages(mOnTimedMessagesListener);

                mListView = findViewById(R.id.chat_list_view);
                registerDataObserver();

                final Parcelable state = mListView.onSaveInstanceState();
                if (mListView.getAdapter() == null) {
                    mListView.setAdapter(mChatAdapter);
                }
                if (state != null) {
                    mListView.onRestoreInstanceState(state);
                }

                mListView.setOnItemClickListener(this);
                mListView.setOnItemLongClickListener(this);
            }

            if (loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
                if (!mOnlyShowTimed && getChatController().loadNewestMessagesByGuid()) {
                    showProgressIndicator();
                } else if (mOnlyShowTimed) {
                    mChatAdapter.clear();
                    if (mChatAdapter != null) {
                        mChatAdapter.setShowTimedMessages(mOnlyShowTimed);
                    }
                    showProgressIndicator();
                    getChatController().getTimedMessages();
                }
                getChatController().countTimedMessages(mOnTimedMessagesListener);
                getChatController().markAllUnreadChatMessagesAsRead(mTargetGuid);

                if (mChatInputFragment.getEditText() != null) {
                    try {
                        String clipBoardText = clipBoardController.get(mTargetGuid);
                        if (!StringUtil.isNullOrEmpty(clipBoardText)) {
                            mChatInputFragment.setChatInputText(clipBoardText, false);
                        }
                    } catch (LocalizedException e) {
                        //FIXME nach dme Backupherstellen kann hier eventuell eine badpaddingexception auftreten
                        // Quickfix fuer 1.8.1: try catch
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                }
            }
            if (mBottomSheetFragment != null) {
                mBottomSheetFragment.getView().startAnimation(mAnimationSlideOut);
                closeBottomSheet(null);
            }

            Chat chat = getSimsMeApplication().getGroupChatController().getChatByGuid(mTargetGuid);
            if (chat != null) {
                getSimsMeApplication().getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                getSimsMeApplication().getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_IMAGE);
            }

            checkActionContainer();
            setActionbarColorFromTrustState();

            GroupInfoChangedListener groupInfoChangedListener = new GroupInfoChangedListener() {
                @Override
                public void onGroupInfoChanged() {
                    try {
                        mChat = mGroupChatController.getChatByGuid(mTargetGuid);
                        if (mChat != null) {
                            mTitle = mChat.getTitle();

                            setTitle(mTitle);
                            setMuteIcon();

                            if (isChatReadOnly()) {
                                disableChatinput();
                                mChatInputFragment.showKeyboard(false);
                            }
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                }
            };
            mGroupChatController.addGroupInfoChangedListener(groupInfoChangedListener);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    /**
     * @param visibility use View.visibility
     */
    protected void setProfilePictureVisibility(int visibility) {
        try {

            if (!Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())) {
                super.setProfilePictureVisibility(visibility);
            }
        } catch (final LocalizedException e) {
            //do nothing
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        getSupportFragmentManager().putFragment(bundle, "chatInputFragment", mChatInputFragment);
        getSupportFragmentManager().putFragment(bundle, "mEmojiconsFragment", mEmojiconsFragment);

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onChatDataChanged(boolean clearImageCache) {
        super.onChatDataChanged(clearImageCache);
        if (isChatReadOnly()) {
            mChatInputFragment.setInputEnabled(false);

            if (isActivityInForeground) {
                disableChatinput();
                setProfilePictureVisibility(View.GONE);
            }
        } else if (mChatInputDisabled) {
            if (!mOnlyShowTimed) {
                mChatInputFragment.setInputEnabled(true);

                if (isActivityInForeground) {
                    enableChatinput();
                }
            }
        }
    }

    @Override
    public void onChatDataLoaded(long lastMessageId) {
        super.onChatDataLoaded(lastMessageId);
        if (mChat != null) {
            if (isChatReadOnly()) {
                mChatInputFragment.setInputEnabled(false);
                if (isActivityInForeground) {
                    disableChatinput();
                    try {
                        if (mChat.getIsRemoved()) {
                            setProfilePictureVisibility(View.GONE);
                        }
                    } catch (final LocalizedException le) {
                        LogUtil.e(TAG, le.getMessage(), le);
                    }
                }
            }
        } else {
            //SIMSME-3572
            LogUtil.i(TAG, "Load Chat failed " + mTargetGuid);
            onBackPressed();
        }
    }

    @Override
    protected void onPauseActivity() {

        //Reset timer
        if (mRefreshTimer != null) {
            mRefreshTimer.cancel();
            mRefreshTimer.purge();
            mRefreshTimer = null;
        }

        super.onPauseActivity();

        try {
            if (mChatInputFragment != null && mChatInputFragment.getEditText() != null && mTargetGuid != null) {
                clipBoardController.put(mTargetGuid, mChatInputFragment.getEditText().getText().toString());
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        if (mContactController != null) {
            mContactController.unregisterOnContactProfileInfoChangeNotification(this);
        }
        getChatController().removeCurrentChatAdapter(mChatAdapter);
        super.onDestroy();
    }

    private void init()
            throws LocalizedException {
        mTitle = "";
        setBackground();

        if (mChat != null) {
            mTitle = mChat.getTitle();

            /* Update title, if navigation from Group-Settings and Name changed (Bug 33002) */
            setTitle(mTitle);
            mTargetGuid = mChat.getChatGuid();
            mPublicKeyXML = null;
            mChatInputFragment.setInputEnabled(true);
        }
    }

    protected boolean isChatReadOnly() {
        try {
            if ((mChat.getIsRemoved() != null && mChat.getIsRemoved())
                    || (mChat.getIsReadOnly() != null && mChat.getIsReadOnly())) {
                LogUtil.w(TAG, String.format("Chat IsRemoved (%b) or Chat IsReadonly (%b). The Group Chat %s will be set to readonly.",
                        (mChat.getIsRemoved() != null && mChat.getIsRemoved()), (mChat.getIsReadOnly() != null && mChat.getIsReadOnly()), mChat.getChatGuid()));
                return true;
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        return false;
    }


    @Override
    public void onLinkClick(final String link) {
        if (link != null) {

            DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(link));
                    router.startExternalActivity(i);
                }
            };

            String title = getResources().getString(R.string.message_url_action);
            String cancelButton = getResources().getString(R.string.std_cancel);
            AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, link, title, title, cancelButton,
                    positiveOnClickListener, null);

            dialog.show();
        }
    }

    private void setMuteIcon() throws LocalizedException {
        if (new Date().getTime() > mChat.getSilentTill()) {
            setProfilePicture(-1, null, "", -1);

            //Reset timer
            if (mRefreshTimer != null) {
                mRefreshTimer.cancel();
                mRefreshTimer.purge();
                mRefreshTimer = null;
            }
        } else {
            final int resourceId = R.drawable.info_stumm_copy;
            final int customColor = ColorUtil.getInstance().getLowColor(getSimsMeApplication());

            if (!mOnlyShowTimed) {
                setProfilePicture(resourceId,
                        mOnRightActionBarClickListener,
                        getResources().getString(R.string.content_description_chat_avatar_group), customColor);
            }
        }
    }

    @Override
    public void setActionbarColorFromTrustState() {
        if ((getChatController() == null) || (mChat == null)) {
            return;
        }

        try {
            setTrustColor(((GroupChatController) getChatController()).getStateForGroupChat(mChat.getChatGuid()));
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onContactProfilInfoHasChanged(String contactGuid) {
        try {
            if (mChat != null && mChat.getMembers() != null) {
                String jsonString = mChat.getMembers().toString();
                if (jsonString.contains(contactGuid)) {
                    if (mChatAdapter != null) {
                        mChatAdapter.notifyDataSetChanged();
                    }
                }
            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onContactProfilImageHasChanged(String contactguid) {
        try {
            if (mChat != null && mChat.getMembers() != null) {
                String jsonString = mChat.getMembers().toString();
                if (jsonString.contains(contactguid)) {
                    if (mChatAdapter != null) {
                        mChatAdapter.notifyDataSetChanged();
                    }
                }
            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, "onContactProfilInfoHasChanged()", e);
        }
    }

    @Override
    protected String getChatTitle() {
        String result;
        if (mChat == null) {
            result = "";
        } else {
            try {
                result = mChat.getTitle();
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                result = "";
            }
        }
        return result;
    }

    @Override
    protected ChatController getChatController() {
        if (mGroupChatController == null) {
            mGroupChatController = getSimsMeApplication().getGroupChatController();
        }

        return mGroupChatController;
    }
}
