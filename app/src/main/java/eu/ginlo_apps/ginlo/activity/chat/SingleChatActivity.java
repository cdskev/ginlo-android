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
import android.widget.Toast;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.MuteChatActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.OnLoadPublicKeyListener;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnAcceptInvitationListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class SingleChatActivity
        extends BaseChatActivity implements ContactController.OnContactProfileInfoChangeNotification {

    private final static String TAG = SingleChatActivity.class.getSimpleName();
    private Contact mContact;
    private OnClickListener mProfileClickListener;
    private SingleChatController mChatController;

    /**
     * wenn ein Kontakt sich vor dem weiterleiten eines Bildes/Videos löscht, muessen wir uns das irgendwo merken
     * im Kontakt selber geht nicht, da er sonst bei der Sync nicht beruecksichtigt wird
     */
    private boolean mContactIsDeleted;

    private AlertDialogWrapper mContactIsBlockedAlert;

    private Timer mRefreshTimer;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);
            LogUtil.i(TAG, "onCreate: " + this + "");

            if (getSimsMeApplication().getPreferencesController().getPublicOnlineState()) {
                mSecondaryTitle = findViewById(R.id.toolbar_secondary_title);
            }

            mOnSendMessageListener = createOnSendMessageListener(mTargetGuid);
            getChatController().addListener(this);

            mContact = mContactController.getContactByGuid(mTargetGuid);
            if (mContact == null) {
                LogUtil.w(TAG, "Load Contact failed for " + mTargetGuid);
                finish();
                return;
            }

            mProfileClickListener = new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final Intent intent = new Intent(SingleChatActivity.this, ContactDetailActivity.class);

                    intent.putExtra(ContactDetailActivity.EXTRA_CONTACT, mContact);
                    intent.putExtra(ContactDetailActivity.EXTRA_MODE, ContactDetailActivity.MODE_NO_SEND_BUTTON);
                    startActivity(intent);
                }
            };
            final View titleContainer = getToolbar().findViewById(R.id.toolbar_title_container);
                titleContainer.setOnClickListener(mProfileClickListener);

            final OnClickListener rightClickListener = new OnClickListener() {
                @Override
                public void onClick(final View v) {
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

            if (mTargetGuid.equals(AppConstants.GUID_SYSTEM_CHAT) || isChatReadOnly()) {
                disableChatinput();
            }

            mContactController.registerOnContactProfileInfoChangeNotification(this);
            createOnChatDataChangedListener();

            //Ausnahme (SIMSME-5324):
            //Chatanfrage wird nicht akzeptiert, wenn man über die Kontakt-Suche bereits ein Chat hatte.
            if (mContact.getIsFirstContact()) {
                Chat contactChat = getChatController().getChatByGuid(mContact.getAccountGuid());
                if (contactChat != null) {
                    //Setze den Typ für den Kontakt
                    contactChat.setType(Chat.TYPE_SINGLE_CHAT);

                    final ChatOverviewController chatOverviewController = ((SimsMeApplication) getApplication()).getChatOverviewController();
                    OnAcceptInvitationListener onAcceptInvitationListener = new OnAcceptInvitationListener() {
                        @Override
                        public void onAcceptSuccess(Chat chat) {
                            chatOverviewController.chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_ACCEPT_CHAT);
                        }

                        @Override
                        public void onAcceptError(String message, boolean chatWasRemoved) {

                        }
                    };
                    getChatController().acceptInvitation(contactChat, onAcceptInvitationListener);
                }
            }
        } catch (final LocalizedException e) {
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
                                setMuteIcon();
                            } catch (final LocalizedException le) {
                                LogUtil.w(TAG, "MuteActivity: " + le.getMessage(), le);
                            }
                        }
                    });
                }
            };
            mRefreshTimer.scheduleAtFixedRate(refreshTask, 0, 5000);

            LogUtil.i(TAG, "onResume: " + this + "");

            if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
                return;
            }
            init();

            if (mTargetGuid != null) {
                // BUG 36403 contact wird nicht refreshed
                mContact = mContactController.getContactByGuid(mTargetGuid);

                LogUtil.i(TAG, "Open ChatStream " + mTargetGuid);
                notificationController.ignoreGuid(mTargetGuid);
                notificationController.dismissNotification(NotificationController.MESSAGE_NOTIFICATION_ID);

                if (mChatAdapter == null) {
                    mChatAdapter = getChatController().getChatAdapter(this, mTargetGuid);
                    mChatAdapter.setShowTimedMessages(mOnlyShowTimed);
                    mChatAdapter.setOnLinkClickListener(this);
                    if (mChatAdapter.getCount() > 0) {
                        //Wenn Adapter gechached im Controller war --> letzte Message id setzen
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

                getOnlineState();

                setMuteIcon();

                getSimsMeApplication().getChatOverviewController().clearNameCache();


                if (loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
                    // Chatverlauf laden
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

                    if ((mChatInputFragment != null) && (mChatInputFragment.getEditText() != null)) {
                        try {
                            final String clipBoardText = clipBoardController.get(mTargetGuid);

                            if (!StringUtil.isNullOrEmpty(clipBoardText)) {
                                mChatInputFragment.setChatInputText(clipBoardText, false);
                            }
                        } catch (final LocalizedException e) {
                            /*bad padding exception beim ersten versuch einer direktnachricht mit unbekanntem kontakt
                             * (es gab kein put vor dem get)
                             * SGA
                             */
                            // NOPMD
                        }
                    }
                }

                Chat chat = getSimsMeApplication().getSingleChatController().getChatByGuid(mTargetGuid);
                if (chat != null) {
                    getSimsMeApplication().getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                    getSimsMeApplication().getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_IMAGE);
                }
            }

            checkActionContainer();
            setActionbarColorFromTrustState();
            if (!mOnlyShowTimed) {
                setTitle(mContact.getName());
            }
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    private void setMuteIcon() throws LocalizedException {

        if ((new Date().getTime() > mContact.getSilentTill())) {
            setProfilePicture(-1, null, "", -1);

            //Reset timer
            if (mRefreshTimer != null) {
                mRefreshTimer.cancel();
                mRefreshTimer.purge();
                mRefreshTimer = null;
            }
        } else {
            final int resourceId = R.drawable.info_stumm_copy;
            final int customImageColor = ColorUtil.getInstance().getLowColor(getSimsMeApplication());

            if (!mOnlyShowTimed) {
                setProfilePicture(resourceId,
                        mProfileClickListener,
                        getResources().getString(R.string.content_description_chat_avatar_single), customImageColor);
            }
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        try {
            if (mContact != null) {
                if (mContactIsBlockedAlert == null) {
                    mContactIsBlockedAlert = DialogBuilderUtil.buildErrorDialog(this, getString(R.string.chat_contact_is_blocked));
                }

                if (mContact.getIsBlocked() != null && mContact.getIsBlocked()) //die IDE zeigt einen Nullpointer, der Dialog wird aber oben gebaut
                {

                    LogUtil.w(TAG, String.format("Contact is blocked. The 1to1 Chat %s will be set to readonly.", mChat.getChatGuid()));

                    if (!mContactIsBlockedAlert.getDialog().isShowing()) {
                        mContactIsBlockedAlert.show();
                        disableChatinput();
                    } else {
                        disableChatinput();
                    }
                } else if (mContact.isDeletedHidden() || mOnlyShowTimed || mContact.getTempReadonly()) {

                    LogUtil.w(TAG, String.format("Contact isDeletedHidden (%b), OnlyShowTimed (%b), TempReadonly (%b). The 1to1 Chat %s will be set to readonly.",
                            mContact.isDeletedHidden(), mOnlyShowTimed, mContact.getTempReadonly(), mChat.getChatGuid()));

                    disableChatinput();
                } else {
                    enableChatinput();
                }

                if (mBottomSheetFragment != null && mBottomSheetFragment.getView() != null) {
                    mBottomSheetFragment.getView().startAnimation(mAnimationSlideOut);
                    closeBottomSheet(null);
                }
            }
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle bundle) {
        if (mChatInputFragment != null) {
            getSupportFragmentManager().putFragment(bundle, "chatInputFragment", mChatInputFragment);
        }

        if (mEmojiconsFragment != null) {
            getSupportFragmentManager().putFragment(bundle, "mEmojiconsFragment", mEmojiconsFragment);
        }

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void setActionbarColorFromTrustState() {
        try {
            if (mContact != null) {
                setTrustColor(mContact.getState());
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onPauseActivity() {

        if (mRefreshTimer != null) {
            mRefreshTimer.cancel();
            mRefreshTimer.purge();
            mRefreshTimer = null;
        }

        super.onPauseActivity();

        try {
            if ((mChatInputFragment != null) && (mChatInputFragment.getEditText() != null)) {
                clipBoardController.put(mTargetGuid, mChatInputFragment.getEditText().getText().toString());
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
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

    private void init() throws LocalizedException {
        mTitle = "";

        setBackground();

        if (mContact != null) {
            mTitle = mContact.getName();
            if (mOnlyShowTimed) {
                setTitle(getString(R.string.chat_timed_messages_item));
            } else {
                setTitle(mContact.getName());
                mTargetGuid = mContact.getAccountGuid();
            }

            if (mContact.getPublicKey() == null) {
                final OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
                    @Override
                    public void onLoadPublicKeyComplete(final Contact contact) {
                        SingleChatActivity.this.mContact = contact;
                        mPublicKeyXML = SingleChatActivity.this.mContact.getPublicKey();
                        if (mChatInputFragment != null) {
                            mChatInputFragment.setInputEnabled(true);
                        }
                        try {
                            setMuteIcon();
                        } catch (final LocalizedException le) {
                            LogUtil.w(TAG, le.getMessage(), le);
                        }
                    }

                    @Override
                    public void onLoadPublicKeyError(final String message) {
                    }
                };

                if (mChatInputFragment != null) {
                    mChatInputFragment.setInputEnabled(false);
                }
                mContactController.loadPublicKey(mContact, onLoadPublicKeyListener);
            } else {
                mPublicKeyXML = mContact.getPublicKey();
                if (mChatInputFragment != null) {
                    mChatInputFragment.setInputEnabled(true);
                }

                // Public Key laden, um festzustellen, ob der Account
                // geloescht wurde
                if (!mContact.isDeletedHidden()) {
                    final OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
                        @Override
                        public void onLoadPublicKeyComplete(final Contact contact) {
                            LogUtil.d(TAG, "Contact exists!");
                            if (contact.getTempReadonly()) {
                                LogUtil.w(TAG, "Contact.getTempReadonly() is true:" + mContact.getAccountGuid());
                                disableChatinput();
                            }
                            try {
                                setMuteIcon();
                            } catch (final LocalizedException le) {
                                LogUtil.w(TAG, le.getMessage(), le);
                            }
                        }

                        @Override
                        public void onLoadPublicKeyError(final String message) {
                            if (LocalizedException.ACCOUNT_UNKNOWN.equals(message)) {
                                mContactController.hideDeletedContactLocally(mContact);
                                mContactController.updatePrivateIndexEntriesAsync();
                                mContactIsDeleted = true;

                                if (isActivityInForeground) {
                                    final AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(SingleChatActivity.this,
                                            getString(R.string.chat_contact_is_deleted));
                                    alert.show();
                                    LogUtil.w(TAG, "onLoadPublicError-> contact deleted: " + mContact.getAccountGuid());
                                    disableChatinput();
                                }
                            }
                        }
                    };

                    mContactController.checkPublicKey(mContact, onLoadPublicKeyListener);
                }
            }

            // gyan Why do we need another check here for unsimsable and only setting the input disable
            // rather than disabling the chat input?
            final int state = mContact.getState();
            if (state == Contact.STATE_UNSIMSABLE) {
                LogUtil.e(TAG, " Contact.state is UNSIMSABLE!:" + mContact.getAccountGuid());
                if (mChatInputFragment != null) {
                     mChatInputFragment.setInputEnabled(false);
                }
            }
        }
    }

    protected boolean isChatReadOnly() {
        try {
            if (mContact == null) {
                LogUtil.w(TAG, String.format("Contact not found. The 1to1 Chat %s will be set to readonly.",
                        mChat == null? "(null)" : mChat.getChatGuid()));
                return true;
            } else if (mContact.getPublicKey() == null) {
                LogUtil.w(TAG, String.format("Contact public key is null. The 1to1 Chat %s will be set to readonly.",
                        mChat == null? "(null)" : mChat.getChatGuid()));
                return true;
            } else if (mContact.isDeletedHidden() || mContact.getTempReadonly()) {
                LogUtil.w(TAG, String.format("Contact is deleted hidden (%b) or tempReadonly (%b). The 1to1 Chat %s will be set to readonly.",
                        mContact.isDeletedHidden(),
                        mContact.getTempReadonly(), mChat == null? "(null)" : mChat.getChatGuid()));
                return true;
            }
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, "isChatReadOnly: " + e.getMessage(), e);
        }

        return false;
    }

    @Override
    public void handleResendClick(final View view) {
        if ((mContact.getIsBlocked() != null) && (mContact.getIsBlocked())) {
            closeBottomSheet(null);
            Toast.makeText(this, R.string.chat_contact_is_blocked_alert, Toast.LENGTH_SHORT).show();
        } else {
            super.handleResendClick(view);
        }
    }

    @Override
    protected boolean isChatBlocked() {
        return mContact.getIsBlocked() != null && mContact.getIsBlocked();
    }

    @Override
    protected void showNonTimedMessages() {
        super.showNonTimedMessages();

        if (isChatBlocked()) {
            LogUtil.e(TAG, "DisableChatInput -> showNonTimedMessages chat is blocked");
            disableChatinput();
        }
    }

    public void handleDeleteChatClick(final View view) {
        if (mOverflowMenuDialog != null) {
            mOverflowMenuDialog.hide();
        }

        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
                getChatController().deleteChat(mTargetGuid, true, mOnDeleteTimedMessageListener);
                if (!StringUtil.isNullOrEmpty(mTargetGuid)) {
                    getSimsMeApplication().getChatOverviewController().chatChanged(null, mTargetGuid, null,
                            ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                }
                //onBackPressed();
            }
        };

        final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
            }
        };

        final String title = getResources().getString(R.string.chats_delete_chat);
        final String positiveButton = getResources().getString(R.string.chats_delete_chat);
        final String message = getResources().getString(R.string.chat_button_delete_confirm);

        final String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

    @Override
    public void onLinkClick(final String link) {
        if (link != null) {
            final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog,
                                    final int which) {
                    final Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(link));
                    router.startExternalActivity(i);
                }
            };

            final String title = getResources().getString(R.string.message_url_action);
            final String cancelButton = getResources().getString(R.string.std_cancel);
            final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, link, title, title, cancelButton,
                    positiveOnClickListener, null);

            dialog.show();
        }
    }

    @Override
    public void onContactProfilInfoHasChanged(final String contactGuid) {
        try {
            if (mContact != null && StringUtil.isEqual(contactGuid, mContact.getAccountGuid())) {
                mTitle = mContact.getName();
                setTitle(mContact.getName());
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "onContactProfilInfoHasChanged()", e);
        }
    }

    @Override
    protected void onActivityPostLoginResult(final int requestCode,
                                             final int resultCode,
                                             final Intent returnIntent) {

        try {
            if (mContactIsDeleted) {
                final AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this, getString(R.string.chat_system_message_removeAccountRegistrationAgain,
                        mContact.getName()));

                alert.show();
            } else {
                super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onContactProfilImageHasChanged(final String contactguid) {
    }

    @Override
    protected String getChatTitle() {
        String result;
        if (mContact == null) {
            result = "";
        } else {
            try {
                result = mContact.getName();
            } catch (final LocalizedException e) {
                result = "";
            }
        }
        return result;
    }

    @Override
    protected ChatController getChatController() {
        if (mChatController == null) {
            mChatController = getSimsMeApplication().getSingleChatController();
        }

        return mChatController;
    }

    @Override
    public boolean hideOnlineState() {
        return mOnlyShowTimed;
    }

    @Override
    protected void firstSendMessageIsSavedInDB() {
        super.firstSendMessageIsSavedInDB();

        if (mContact != null) {
            getSimsMeApplication().getContactController().addLastUsedCompanyContact(mContact);
        }
    }
}
