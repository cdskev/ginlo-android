// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chat;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDialog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.ChannelDetailActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChannelController.ChannelIdentifier;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.message.ChannelChatController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.ChannelListModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelSelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.util.ChannelColorUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ToolbarColorizeHelper;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.FloatingActionButton;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.List;

public class ChannelChatActivity
        extends eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity {

    private final static String TAG = ChannelChatActivity.class.getSimpleName();
    private static final long LAST_MESSAGE_ID_INITIAL_VALUE = -2;

    private ImageLoader mImageLoader;
    private ChannelController mChannelController;
    private View mHeader;
    private ImageView mHeaderBgImageView;
    private ImageView mHeaderLabelImageView;
    private ChannelModel mChannelModel;
    private Channel mChannel;
    private ChannelChatController mChatController;
    private ChannelColorUtil mChannelColorUtil;
    private Contact mTempContact;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);

            mClearChatQuestionText = R.string.chat_button_clear_confirm_channel;

            getChatController().addListener(this);

            mChannelController = ((SimsMeApplication) this.getApplication()).getChannelController();
            mChannel = mChannelController.getChannelFromDB(mTargetGuid);

            setTitle(mChannel.getShortDesc());
            setRightActionBarImageVisibility(View.GONE);
            setActionBarAVCImageVisibility(View.GONE);
            preventSelfConversation();

            disableChatinput();

            mLoadMoreView = (LinearLayout) getLayoutInflater().inflate(R.layout.chat_item_load_more_layout, null);
            mLoadMoreView.setOnClickListener(onLoadMoreClickListener);

            mHeader = findViewById(R.id.channel_chat_header);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                mHeader.setVisibility(View.VISIBLE);
            }
            mHeader.setContentDescription(mChannel.getShortDesc());
            // im Header Imageviews ausblenden, bis Images geladen wurden
            mHeaderBgImageView = mHeader.findViewById(R.id.channel_item_background);
            mHeaderLabelImageView = mHeader.findViewById(R.id.channel_item_label);

            final TextView headerLabelTextView = mHeader.findViewById(R.id.channel_text_label);

            // headerLabelTextView.setText(channel.getShortDesc());
            headerLabelTextView.setVisibility(View.GONE);
            mHeaderBgImageView.setVisibility(View.GONE);
            mHeaderLabelImageView.setVisibility(View.GONE);

            mImageLoader = initImageLoader(mChannelController);

            mChannelModel = mChannelController.getChannelModel(mChannel);
            mChannelColorUtil = new ChannelColorUtil(mChannelModel.layout, ChannelChatActivity.this);
            loadChannelImages(mChannel);

            final FloatingActionButton fab = findViewById(R.id.channel_chat_fab);
            final String feedbackContact = mChannel.getFeedbackContact();

            if (mChannel.getIsDeleted() || StringUtil.isNullOrEmpty(feedbackContact)) {
                fab.setVisibility(View.GONE);
            } else {
                createTempContact(feedbackContact);
                if (mChannelColorUtil != null) {
                    final int headColor = mChannelColorUtil.getHeadColor();
                    final int headBkColor = mChannelColorUtil.getHeadBkColor();

                    fab.setColorNormal(headBkColor);
                    fab.setColorPressed(headBkColor);
                    final Drawable drawable = fab.getIconDrawable();
                    drawable.setColorFilter(headColor, PorterDuff.Mode.SRC_ATOP);
                }
                fab.setVisibility(View.VISIBLE);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
            finish();
        }
        mLastMessageId = LAST_MESSAGE_ID_INITIAL_VALUE;
    }

    @Override
    protected void colorizeActivity() {
        ToolbarColorizeHelper.colorizeToolbar(getToolbar(), mChannelColorUtil.getHeadColor(), mChannelColorUtil.getHeadBkColor(), this);
    }

    @Override
    protected Drawable getColorizedDrawableForToolbar(final int drawableId) {
        final Drawable d = getResources().getDrawable(drawableId).mutate();
        d.setColorFilter(mChannelColorUtil.getHeadColor(), PorterDuff.Mode.SRC_ATOP);
        return d;
    }

    protected void showToolbarOptions(final List<ToolbarOptionsItemModel> toolbarOptionsItemModels) {
        super.showToolbarOptions(toolbarOptionsItemModels);
        mToolbarOptionsLayout.setBackgroundColor(mChannelColorUtil.getHeadBkColor());
    }

    protected void hideToolbarOptions() {
        super.hideToolbarOptions();
        mToolbarOptionsLayout.setBackgroundColor(ColorUtil.getInstance().getToolbarColor(getSimsMeApplication()));
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_chat_channel;
    }

    @Override
    protected void onResumeActivity() {
        super.onResumeActivity();

        // bilder aus Kanal weiterleiten - idle-Dioalog schliesst nicht - workarround
        dismissIdleDialog();

        LogUtil.i(TAG, "onResume: " + this);

        if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
            return;
        }

        if (mTargetGuid != null) {
            LogUtil.i(TAG, "Open ChatStream " + mTargetGuid);
            notificationController.ignoreGuid(mTargetGuid);
            notificationController.dismissNotification(NotificationController.MESSAGE_NOTIFICATION_ID);

            if (mChatAdapter == null) {
                mChatAdapter = getChatController().getChatAdapter(this, mTargetGuid);
                mChatAdapter.setShowTimedMessages(mOnlyShowTimed);
                mChatAdapter.setOnLinkClickListener(this);
                if (mChatAdapter.getCount() > 0) {
                    BaseChatItemVO item = mChatAdapter.getItem(mChatAdapter.getCount() - 1);
                    if (item != null) {
                        mLastMessageId = item.messageId;
                    }
                }
            }

            mChatAdapter.setChannelModel(mChannelModel);
            getChatController().setCurrentChatAdapter(mChatAdapter);

            mListView = findViewById(R.id.chat_list_view);
            registerDataObserver();

            final Parcelable state = mListView.onSaveInstanceState();

            mListView.setAdapter(mChatAdapter);
            mListView.onRestoreInstanceState(state);
            mListView.setOnItemClickListener(this);
            mListView.setOnItemLongClickListener(this);

            mListView.setStackFromBottom(false);
        }

        if (loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
            // Chatverlauf laden
            if (getChatController().loadNewestMessagesByGuid()) {
                showProgressIndicator();
            }
            getChatController().markAllUnreadChatMessagesAsRead(mTargetGuid);
        }

        if (mChannel.getIsDeleted()) {
            Toast.makeText(this, R.string.channel_channel_is_disabled, Toast.LENGTH_LONG).show();
        }
        closeBottomSheet(mOnBottomSheetClosedListener);
        ToolbarColorizeHelper.colorizeToolbar(getToolbar(), mChannelColorUtil.getHeadColor(), mChannelColorUtil.getHeadBkColor(), this);

    }

    public void onInfoClicked(final View v) {
        closeSettings();
        final Intent intent = new Intent(ChannelChatActivity.this, ChannelDetailActivity.class);
        intent.putExtra(ChannelDetailActivity.CHANNEL_GUID, mTargetGuid);
        startActivity(intent);
    }

    public void onContactClicked(final View v) {
        showProgressIndicator();
        v.setVisibility(View.GONE);

        final ContactController.OnLoadContactFromServerListener onLoadContactListener = new ContactController.OnLoadContactFromServerListener() {

            @Override
            public void onLoadContactFromServerError(final String message) {
                DialogBuilderUtil.buildErrorDialog(ChannelChatActivity.this, message).show();
                hideProgressIndicator();
                v.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLoadMultipleContactsFromServerComplete(List<Contact> contacts) {
                if (contacts.size() > 0) {
                    contacts = ContactUtil.sortContactsByMandantPriority(contacts, mPreferencesController);
                    final Contact contact = contacts.get(0);

                    if (contact != null) {
                        try {
                            contact.setDisplayName(mTempContact.getDisplayName());
                            contact.setNickname(mTempContact.getNickname());
                        } catch (final LocalizedException le) {
                            LogUtil.w(TAG, le.getMessage(), le);
                            hideProgressIndicator();
                            v.setVisibility(View.VISIBLE);
                        }

                        final ContactController.OnLoadPublicKeyListener onLoadPublicKeyListener = new ContactController.OnLoadPublicKeyListener() {
                            @Override
                            public void onLoadPublicKeyComplete(final Contact contact) {
                                hideProgressIndicator();
                                v.setVisibility(View.VISIBLE);
                                final Intent intent = new Intent(ChannelChatActivity.this, SingleChatActivity.class);

                                intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, contact.getAccountGuid());
                                startActivity(intent);
                            }

                            @Override
                            public void onLoadPublicKeyError(final String message) {
                                DialogBuilderUtil.buildErrorDialog(ChannelChatActivity.this, message).show();
                                hideProgressIndicator();
                                v.setVisibility(View.VISIBLE);
                            }
                        };

                        mContactController.loadPublicKey(contact, onLoadPublicKeyListener);
                    } else {
                        DialogBuilderUtil.buildErrorDialog(ChannelChatActivity.this, getString(R.string.chat_contact_not_found))
                                .show();
                        hideProgressIndicator();
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        };

        mContactController.prepareSingleContactFromServer(mTempContact, onLoadContactListener);
    }

    public void onRecommendClicked(final View v) {
        try {
            String text = mChannel.getSuggestionText();

            if (StringUtil.isNullOrEmpty(text)) {
                return;
            }

            final String encodedUri = URLEncoder.encode(text, "UTF-8");

            text = "simsme://message?text=" + encodedUri;

            final Intent intent = new Intent(this, DistributorChatActivity.class);

            intent.setData(Uri.parse(text));
            startActivity(intent);
        } catch (final UnsupportedEncodingException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        } finally {
            closeSettings();
        }
    }

    public void handleForwardMessageImageClick(final View view) {
        forwardMessage();
    }

    public void onNotificationsClicked(final View v) {
        closeSettings();
        final boolean disabled = mChannelController.getDisableChannelNotification(mTargetGuid);
        final ChannelController.ChannelAsyncLoaderCallback<String> callback = new ChannelController.ChannelAsyncLoaderCallback<String>() {
            @Override
            public void asyncLoaderFinishedWithSuccess(final String result) {
                if (StringUtil.isEqual(mAccountController.getAccount().getAccountGuid(), result)) {
                    hideProgressIndicator();
                }
            }

            @Override
            public void asyncLoaderFinishedWithError(final String errorMessage) {
                DialogBuilderUtil.buildErrorDialog(ChannelChatActivity.this, errorMessage).show();
                hideProgressIndicator();
            }
        };

        mChannelController.setDisableChannelNotification(mTargetGuid, mChannel.getType(), !disabled, callback);

        showProgressIndicator();
    }

    @Override
    public void onChatDataLoaded(final long lastMessageId) {
        if (mLastMessageId == LAST_MESSAGE_ID_INITIAL_VALUE) {
            mLastMessageId = ChatController.NO_MESSAGE_ID_FOUND;
        }
        super.onChatDataLoaded(lastMessageId);
        scrollToEnd();
    }

    public void onUnsubscribeClicked(final View v) {
        closeSettings();

        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
                unSubscribeFromChannel();
            }
        };

        final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
            }
        };

        final String title;
        final String positiveButton;
        final String message;

        if (StringUtil.isEqual(Channel.TYPE_SERVICE, mChannel.getType())) {
            title = getResources().getString(R.string.channel_leave_confirm_title_service);
            positiveButton = getResources().getString(R.string.channel_settings_unsubscribe_service);
            message = getResources().getString(R.string.channel_leave_confirm_service);
        } else {
            title = getResources().getString(R.string.channel_subscribe_button_cancel);
            positiveButton = getResources().getString(R.string.channel_settings_unsubscribe);
            message = getResources().getString(R.string.channel_leave_confirm);
        }

        final String negativeButton = getResources().getString(R.string.std_cancel);

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this,
                message,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

    private void unSubscribeFromChannel() {
        final Dialog waitProgress = DialogBuilderUtil.buildProgressDialog(this,
                R.string.channel_cancel_subscribe_waiting);

        waitProgress.show();

        mChannelController.cancelChannelSubscription(mChannel.getGuid(),
                mChannel.getType(),
                new ChannelController.ChannelAsyncLoaderCallback<String>() {
                    @Override
                    public void asyncLoaderFinishedWithSuccess(final String result) {
                        mChannel.setIsSubscribed(false);
                        mChannelController.updateChannel(mChannel);

                        //Chat loeschen

                        getSimsMeApplication().getChannelChatController().deleteChat(mChannel.getGuid(), true, null);
                        getSimsMeApplication().getChatOverviewController().chatChanged(null, mChannel.getGuid(), null,
                                ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                        waitProgress.dismiss();
                        finish();
                    }

                    @Override
                    public void asyncLoaderFinishedWithError(final String errorMessage) {
                        waitProgress.dismiss();
                        DialogBuilderUtil.buildErrorDialog(ChannelChatActivity.this,
                                getString(R.string.channel_cancel_subscription_error))
                                .show();
                    }
                });
    }

    private void closeSettings() {
        if (mOverflowMenuDialog != null) {
            mOverflowMenuDialog.hide();
        }
    }

    private void createTempContact(String jSonString) {
        try {
            JsonParser parser = new JsonParser();
            JsonObject jSonObj = parser.parse(jSonString).getAsJsonObject();

            mTempContact = new Contact();

            if (jSonObj.has("phoneNumber")) {
                mTempContact.setPhoneNumber(jSonObj.get("phoneNumber").getAsString());
            }

            if (jSonObj.has("nickname")) {
                mTempContact.setFirstName(jSonObj.get("nickname").getAsString());
                mTempContact.setDisplayName(jSonObj.get("nickname").getAsString());
                mTempContact.setNickname(jSonObj.get("nickname").getAsString());
            }
            mTempContact.setIsHidden(false);
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    private void loadChannelImages(final Channel channel/*,final ChannelColorUtil ccu*/) {
        // Bilder async laden
        final ChannelListModel clModel = new ChannelListModel();

        clModel.guid = channel.getGuid();
        clModel.checksum = channel.getChecksum();

        if (mChannelColorUtil != null && mChannelColorUtil.getIbColor() != 0 && !Channel.DHL_SHORT_DESCRIPTION.equals(channel.getShortDesc())) {
            mHeaderBgImageView.setBackgroundColor(mChannelColorUtil.getIbColor());
            mHeaderBgImageView.setVisibility(View.VISIBLE);
        } else {
            final ChannelIdentifier headerBgIdentifier = new ChannelIdentifier(clModel,
                    ChannelController.IMAGE_TYPE_ITEM_BACKGROUND);

            mImageLoader.loadImage(headerBgIdentifier, mHeaderBgImageView);
        }

        if (mChannelColorUtil != null && mChannelColorUtil.getCbColor() != 0 && mBackground != null) {
            mBackground.setImageDrawable(null);
            mBackground.setBackgroundColor(mChannelColorUtil.getCbColor());
            mBackground.setVisibility(View.VISIBLE);
        } else {
            final ChannelIdentifier bgIdentifier = new ChannelIdentifier(clModel,
                    ChannelController.IMAGE_TYPE_CHANNEL_BACKGROUND);

            mImageLoader.loadImage(bgIdentifier, mBackground);
        }
        final ChannelIdentifier headerLabelIdentifier = new ChannelIdentifier(clModel,
                ChannelController.IMAGE_TYPE_PROVIDER_LABEL);

        mImageLoader.loadImage(headerLabelIdentifier, mHeaderLabelImageView);
    }

    @Override
    protected void onPauseActivity() {
        super.onPauseActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ChatController chatController = getChatController();

        if (chatController != null) {
            chatController.removeCurrentChatAdapter(mChatAdapter);
            chatController.removeListener(this);
        }
    }

    private ImageLoader initImageLoader(final ChannelController channelController) {
        // Image Loader zum Laden der ChatoverviewItems Icons
        final ImageLoader imageLoader = new ImageLoader(this, ChatImageController.SIZE_CHAT_OVERVIEW, false) {
            @Override
            protected Bitmap processBitmap(final Object data) {
                try {
                    final ChannelIdentifier ci = (ChannelIdentifier) data;

                    return channelController.loadImage(ci.getClModel(), ci.getType());
                } catch (final LocalizedException e) {
                    LogUtil.w(TAG, "Image can't be loaded.", e);
                    return null;
                }
            }

            @Override
            protected void processBitmapFinished(final Object data, final ImageView imageView) {
                try {
                    final ChannelIdentifier ci = (ChannelIdentifier) data;

                    final String ciType = ci.getType();
                    if (ChannelController.IMAGE_TYPE_CHANNEL_BACKGROUND.equals(ciType)) {
                        mBackground.setVisibility(View.VISIBLE);
                    } else if (ChannelController.IMAGE_TYPE_ITEM_BACKGROUND.equals(ciType)) {
                        mHeaderBgImageView.setVisibility(View.VISIBLE);
                    } else if (ChannelController.IMAGE_TYPE_PROVIDER_LABEL.equals(ciType)) {
                        mHeaderLabelImageView.setVisibility(View.VISIBLE);
                    }
                } catch (NullPointerException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
        };

        imageLoader.setImageFadeIn(false);

        return imageLoader;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mHeader.setVisibility(View.GONE);
        } else {
            mHeader.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
        super.onItemClick(parent, view, position, id);

        if (mBottomSheetOpen) {
            return;
        }

        int offset = hasHeaderView() ? 1 : 0;
        BaseChatItemVO baseChatItemVO = mChatAdapter.getItem(position - offset);

        if (baseChatItemVO instanceof ChannelSelfDestructionChatItemVO) {
            Message message = getChatController().findMessageById(baseChatItemVO.messageId);

            if (message == null) {
                return;
            }

            MessageDestructionParams destructionParams = ((ChannelSelfDestructionChatItemVO) baseChatItemVO).destructionParams;

            if (!message.getIsSentMessage() && (destructionParams.countdown == null)
                    && Calendar.getInstance().getTime().after(destructionParams.date)) {
                getChatController().deleteMessage(message.getGuid(), true, null);

                String errorMessage = getResources().getString(R.string.chats_showText_destroyedLabel);

                DialogBuilderUtil.buildErrorDialog(this, errorMessage);
            } else {
                final String messageGuid = baseChatItemVO.getMessageGuid() != null ? baseChatItemVO.getMessageGuid() : Long.toString(baseChatItemVO.messageId);

                if (isDownloading(messageGuid)) {
                    return;
                }
                AttachmentController.OnAttachmentLoadedListener listener = getAttachmentLoadedListener(messageGuid);
                ChannelSelfDestructionChatItemVO selfDestructionChatItemVO = (ChannelSelfDestructionChatItemVO) baseChatItemVO;

                final ProgressBar progressBar = view.findViewById(R.id.progressBar_download);
                if (progressBar != null) {
                    View channelSdImage = view.findViewById(R.id.chat_item_szf_animation_view);
                    if (channelSdImage != null) {
                        channelSdImage.setVisibility(View.GONE);
                    }
                    HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(position - offset, false);
                    getChatController().getAttachment(selfDestructionChatItemVO.messageId, listener, false, onConnectionDataUpdatedListener);
                } else {
                    getChatController().getAttachment(selfDestructionChatItemVO.messageId, listener, false, null);
                }
            }
        } else if (baseChatItemVO instanceof ChannelChatItemVO) {
            final String messageGuid = baseChatItemVO.getMessageGuid() != null ? baseChatItemVO.getMessageGuid() : Long.toString(baseChatItemVO.messageId);
            if (!isDownloading(messageGuid)) {
                AttachmentController.OnAttachmentLoadedListener listener = getAttachmentLoadedListener(messageGuid);

                final ProgressBar progressBar = view.findViewById(R.id.progressBar_download);
                if (progressBar != null) {
                    HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(position - offset, false);
                    getChatController().getAttachment(baseChatItemVO.messageId, listener, false, onConnectionDataUpdatedListener);
                } else {
                    getChatController().getAttachment(baseChatItemVO.messageId, listener, false, null);
                }
            }
        }
    }

    private AttachmentController.OnAttachmentLoadedListener getAttachmentLoadedListener(final String messageGuid) {
        return new AttachmentController.OnAttachmentLoadedListener() {
            final String mMessageGuid = messageGuid;

            @Override
            public void onBitmapLoaded(File file,
                                       DecryptedMessage decryptedMsg) {
                finishDownloading(mMessageGuid);
                ChannelChatActivity.this.onBitmapLoaded(file, decryptedMsg);
            }

            @Override
            public void onVideoLoaded(File videoFile,
                                      DecryptedMessage decryptedMsg) {
                finishDownloading(mMessageGuid);
                ChannelChatActivity.this.onVideoLoaded(videoFile, decryptedMsg);
            }

            @Override
            public void onAudioLoaded(File audioFile,
                                      DecryptedMessage decryptedMsg) {
                finishDownloading(mMessageGuid);
                ChannelChatActivity.this.onAudioLoaded(audioFile, decryptedMsg);
            }

            @Override
            public void onFileLoaded(File dataFile, DecryptedMessage decryptedMsg) {
                finishDownloading(mMessageGuid);
                ChannelChatActivity.this.onFileLoaded(dataFile, decryptedMsg);
            }

            @Override
            public void onHasNoAttachment(final String message) {
                ChannelChatActivity.this.onHasNoAttachment(message);
            }

            @Override
            public void onHasAttachment(final boolean finishedWork) {
                markDownloading(mMessageGuid);
                ChannelChatActivity.this.onHasAttachment(finishedWork);
            }

            @Override
            public void onLoadedFailed(String message) {
                finishDownloading(mMessageGuid);
                ChannelChatActivity.this.onLoadedFailed(message);
            }
        };
    }

    @Override
    public void onBackArrowPressed() {
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        GinloAppLifecycle ginloAppLifecycle = ((SimsMeApplication) this.getApplication()).getAppLifecycleController();
        if (ginloAppLifecycle != null && ginloAppLifecycle.getActivityStackSize() == 1) {
            final Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected ChatController getChatController() {
        if (mChatController == null) {
            mChatController = getSimsMeApplication().getChannelChatController();
        }
        return mChatController;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (mMenu == null) {
            mMenu = menu;
        }
        getMenuInflater().inflate(R.menu.menu_channel_overflow_dummy, menu);
        setIsMenuVisible(true);

        final MenuItem optionsMenuItem = menu.findItem(R.id.options);
        final View optionsLayout = optionsMenuItem.getActionView();
        final ImageView optionMenuItemIcon = optionsLayout.findViewById(R.id.menu_item_option_icon);

        if (optionMenuItemIcon != null && optionMenuItemIcon.getDrawable() != null && mChannelColorUtil != null) {
            optionMenuItemIcon.getDrawable().setColorFilter(new PorterDuffColorFilter(mChannelColorUtil.getHeadColor(), PorterDuff.Mode.SRC_ATOP));
        }

        return true;
    }

    public void onOptionsMenuClick(final View v) {
        try {
            View menuRoot = ViewExtensionsKt.themedInflate(LayoutInflater.from(this), this, R.layout.menu_overflow_channel, null);

            final TextView itemNotification = menuRoot.findViewById(R.id.menu_chat_notification_item);

            if (mChannel.getIsDeleted()) {
                final TextView itemRecommend = menuRoot.findViewById(R.id.menu_chat_recommend_item);
                itemRecommend.setVisibility(View.GONE);
                itemNotification.setVisibility(View.GONE);
            } else {
                final boolean disableChannelNotification = mChannelController.getDisableChannelNotification(mTargetGuid);

                if (disableChannelNotification) {
                    itemNotification.setText(getResources().getString(R.string.channel_settings_notifications_off));
                    itemNotification.setCompoundDrawablesWithIntrinsicBounds(R.drawable.channels_mute, 0, 0, 0);
                } else {
                    itemNotification.setText(getResources().getString(R.string.channel_settings_notifications));
                    itemNotification.setCompoundDrawablesWithIntrinsicBounds(R.drawable.channels_sound, 0, 0, 0);
                }
            }

            if (mChannelController.isChannelMandatory(mChannel)) {
                final TextView itemUnsubscribe = menuRoot.findViewById(R.id.menu_chat_unsubscribe_item);
                itemUnsubscribe.setVisibility(View.GONE);
            }

            mOverflowMenuDialog = new AppCompatDialog(this);
            mOverflowMenuDialog.setContentView(menuRoot);
            Window dialogWindow = mOverflowMenuDialog.getWindow();
            if (dialogWindow != null) {
                dialogWindow.setGravity(Gravity.END | Gravity.TOP);
                dialogWindow.setBackgroundDrawableResource(R.color.transparent);
            }
            mOverflowMenuDialog.show();
        } catch (final LocalizedException le) {
            LogUtil.w(TAG, le.getMessage(), le);
        }
    }
}
