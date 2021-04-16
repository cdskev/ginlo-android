// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatDialog;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;

public class SystemChatActivity
        extends BaseChatActivity {

    private final static String TAG = SystemChatActivity.class.getSimpleName();
    private Contact contact;
    private SingleChatController mChatController;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);
            LogUtil.i(TAG, "onCreate: " + this + "");

            mTargetGuid = getIntent().getStringExtra(EXTRA_TARGET_GUID);

            getChatController().addListener(this);

            contact = mContactController.getContactByGuid(mTargetGuid);

            if (contact == null) {
                LogUtil.i(TAG, "Load Contact failed " + mTargetGuid);
                onBackPressed();
                return;
            } else {
                setTitle(R.string.chat_system_nickname);
            }

            disableChatinput();

            mLoadMoreView = (LinearLayout) getLayoutInflater().inflate(R.layout.chat_item_load_more_layout, null);
            mLoadMoreView.setOnClickListener(onLoadMoreClickListener);

            setProfilePicture(-1, null, null, -1);
        } catch (LocalizedException e) {
            finish();
        }
    }

    @Override
    protected void onResumeActivity() {
        try {
            super.onResumeActivity();

            LogUtil.i(TAG, "onResume: " + this + "");

            if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
                return;
            }
            init();

            if (mTargetGuid != null) {
                LogUtil.i(TAG, "Open ChatStream " + mTargetGuid);
                notificationController.ignoreGuid(mTargetGuid);
                notificationController.dismissNotification(mTargetGuid);

                if (mChatAdapter == null) {
                    mChatAdapter = getChatController().getChatAdapter(this, mTargetGuid);
                    mChatAdapter.setOnLinkClickListener(this);
                    if (mChatAdapter.getCount() > 0) {
                        BaseChatItemVO item = mChatAdapter.getItem(mChatAdapter.getCount() - 1);
                        if (item != null) {
                            mLastMessageId = item.messageId;
                        }
                    }
                }

                mChatAdapter.setOnLinkClickListener(this);
                getChatController().setCurrentChatAdapter(mChatAdapter);

                mListView = findViewById(R.id.chat_list_view);

                //setOnTouchListenerForBB(mListView);
                registerDataObserver();

                Parcelable state = mListView.onSaveInstanceState();

                mListView.setAdapter(mChatAdapter);
                mListView.onRestoreInstanceState(state);
                mListView.setOnItemClickListener(this);
                mListView.setOnItemLongClickListener(this);
                mListView.setSelection(mChatAdapter.getCount());

                //getChatController().listenForChatUpdatesByGuid(mTargetGuid);

            }

            if (loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
                // Chatverlauf laden
                if (getChatController().loadNewestMessagesByGuid()) {
                    showProgressIndicator();
                }
                getChatController().markAllUnreadChatMessagesAsRead(mTargetGuid);
            }

            closeBottomSheet(null);
        } catch (LocalizedException e) {
            onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (mMenu == null) {
            mMenu = menu;
        }
        getMenuInflater().inflate(R.menu.menu_chats_overflow_dummy, menu);
        setIsMenuVisible(true);
        return true;
    }

    public void onOptionsMenuClick(final View v) {
        View menuRoot = ViewExtensionsKt.themedInflate(LayoutInflater.from(this), this, R.layout.menu_overflow_chat, null);

        mOverflowMenuDialog = new AppCompatDialog(this);
        mOverflowMenuDialog.setContentView(menuRoot);
        Window dialogWindow = mOverflowMenuDialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setGravity(Gravity.END | Gravity.TOP);
            dialogWindow.setBackgroundDrawableResource(R.color.transparent);
        }
        mOverflowMenuDialog.show();
    }

    private void init()
            throws LocalizedException {
        mTitle = "";

        setBackground();

        if (contact != null) {
            mTitle = getResources().getString(R.string.chat_system_nickname);
            mTargetGuid = contact.getAccountGuid();

            mPublicKeyXML = contact.getPublicKey();
        }
    }

    public void handleDeleteChatClick(View view) {
        final Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

        closeBottomSheet(null);
        getChatController().deleteChat(mTargetGuid, true, null);
        startActivity(intent);
    }

    @Override
    protected ChatController getChatController() {
        if (mChatController == null) {
            mChatController = getSimsMeApplication().getSingleChatController();
        }

        return mChatController;
    }
}
