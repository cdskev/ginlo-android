// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.chat;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.CameraActivity;
import eu.ginlo_apps.ginlo.ContactsActivity;
import eu.ginlo_apps.ginlo.ForwardActivity;
import eu.ginlo_apps.ginlo.ForwardActivityBase;
import eu.ginlo_apps.ginlo.LocationActivity;
import eu.ginlo_apps.ginlo.LocationActivityOSM;
import eu.ginlo_apps.ginlo.OnLinkClickListener;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.TextExtensionsKt;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask.OnConnectionDataUpdatedListener;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.AttachmentController.OnAttachmentLoadedListener;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnChatDataChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeleteTimedMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnTimedMessagesDeliveredListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.QueryDatabaseListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.SelfdestructionFragment.DatePickerFragment;
import eu.ginlo_apps.ginlo.fragment.SelfdestructionFragment.TimePickerFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.CitationModel;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.chat.AVChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AppGinloControlChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelSelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.FileChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ImageChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.LocationChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SystemInfoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VCardChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VideoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VoiceChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.model.param.SendActionContainer;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil.OnCloseListener;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MetricsUtil;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.SimsmeSwipeRefreshLayout;
import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.Nickname;
import javax.inject.Inject;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public abstract class BaseChatActivity
        extends ChatInputActivity
        implements OnChatDataChangedListener,
        EmojiPickerCallback,
        OnItemLongClickListener,
        OnLinkClickListener {

    public static final String EXTRA_TARGET_GUID = "ChatActivity.targetGuidExtra";
    public static final String EXTRA_FORWARD_MESSAGE_ID = "ChatActivity.forwardMessageExtra";
    public static final String EXTRA_FORWARD_CHANNELMESSAGE_IS_IMAGE = "ChatActivity.forwardChannelMessageIsImageExtra";
    public static final String EXTRA_FORWARD_CHANNELMESSAGE_IS_TEXT = "ChatActivity.forwardChannelMessageIsImageText";

    private static final String TAG = BaseChatActivity.class.getSimpleName();
    private static final int LAST_MESSAGE_COUNT = 20;
    private static final int FORWARD_REQUEST_CODE = 111;

    private final ChatDataSetObserver mObserver = new ChatDataSetObserver();
    protected ListView mListView;
    protected LinearLayout mLoadMoreView;
    protected SimsmeSwipeRefreshLayout mSwipeLayout;
    protected Menu mMenu;
    protected TextView mTimedCounterView;
    protected QueryDatabaseListener mOnTimedMessagesListener;
    protected Dialog mOverflowMenuDialog;
    boolean mChatInputDisabled;

    //
    Long mLastMessageId = ChatController.NO_MESSAGE_ID_FOUND;
    ImageView mBackground;
    int mClearChatQuestionText;
    boolean mOnlyShowTimed;
    /**
     * chat-Typ benoetigt eine Target-Guid
     */
    boolean mNeedsTargetGuid = true;
    private AttachmentController mAttachmentController;
    private BaseChatItemVO mResendItem;
    private int mOldLoadCount;
    protected final OnClickListener onLoadMoreClickListener = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            showProgressIndicator();
            mOldLoadCount = mChatAdapter.getCount();
            getChatController().loadMoreMessages(LAST_MESSAGE_COUNT);
        }
    };
    private int mLastChangeCount;
    /**
     * return value for image capture intent, since adding it as an extra
     * doesn't work with EXTRA_OUTPUT
     */
    private Uri mTakePhotoUri;
    private Animation mSlideOutAcc;
    private Animation mNewTimedMsgAnimation;
    private Toast mNewMessagesToast;
    private boolean mPlayTimdMessagesAnimation;
    private OnTimedMessagesDeliveredListener mOnTimedMessagesDeliveredListener;
    private AlertDialogWrapper mSendMessageDialog;
    private List<OnChatDataChangedListener> mOnChatDataChangedListeners;
    private AlertDialogWrapper mSendFileDialog;
    @Inject
    public Router router;

    public void cancelMessageComment(final View v) {
        closeCommentView();
    }

    @Override
    protected void closeCommentView() {
        if (mChatInputFragment != null && mCitatedChatItem != null) {
            mChatInputFragment.closeCommentView();
            mCitatedChatItem = null;
        }
    }

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        super.onCreateActivity(savedInstanceState);

        mMessageController = mApplication.getMessageController();
        mContactController = mApplication.getContactController();
        mAccountController = mApplication.getAccountController();
        mPreferencesController = preferencesController;
        mAttachmentController = mApplication.getAttachmentController();

        mClearChatQuestionText = R.string.chat_button_clear_confirm;

        mChatInputContainerId = R.id.chat_input_fragment_placeholder;
        mFragmentContainerId = R.id.linear_layout_fragment_container;

        final int slideheigthOneLine = (int) getResources().getDimension(R.dimen.chat_slideheight_one_line);
        final int slideheigthTwoLines = (int) getResources().getDimension(R.dimen.chat_slideheight_two_lines);
        final int slideheigthThreeLines = (int) getResources().getDimension(R.dimen.chat_slideheight_three_lines);
        final int slideheigthSixLines = (int) getResources().getDimension(R.dimen.chat_slideheight_six_lines);
        final int slideheigthSevenLines = (int) getResources().getDimension(R.dimen.chat_slideheight_seven_lines);

        mAnimationSlideInOneLine = new TranslateAnimation(0, 0, slideheigthOneLine, 0);
        mAnimationSlideOutOneLine = new TranslateAnimation(0, 0, 0, slideheigthOneLine);
        mAnimationSlideInTwoLines = new TranslateAnimation(0, 0, slideheigthTwoLines, 0);
        mAnimationSlideOutTwoLines = new TranslateAnimation(0, 0, 0, slideheigthTwoLines);
        mAnimationSlideInThreeLines = new TranslateAnimation(0, 0, slideheigthThreeLines, 0);
        mAnimationSlideOutThreeLines = new TranslateAnimation(0, 0, 0, slideheigthThreeLines);
        mAnimationSlideInSixLines = new TranslateAnimation(0, 0, slideheigthSixLines, 0);
        mAnimationSlideOutSixLines = new TranslateAnimation(0, 0, 0, slideheigthSixLines);
        mAnimationSlideInSevenLines = new TranslateAnimation(0, 0, slideheigthSevenLines, 0);
        mAnimationSlideOutSevenLines = new TranslateAnimation(0, 0, 0, slideheigthSevenLines);

        mAnimationSlideInOneLine.setDuration(ANIMATION_DURATION);
        mAnimationSlideOutOneLine.setDuration(ANIMATION_DURATION);
        mAnimationSlideInTwoLines.setDuration(ANIMATION_DURATION);
        mAnimationSlideOutTwoLines.setDuration(ANIMATION_DURATION);
        mAnimationSlideInThreeLines.setDuration(ANIMATION_DURATION);
        mAnimationSlideOutThreeLines.setDuration(ANIMATION_DURATION);
        mAnimationSlideInSixLines.setDuration(ANIMATION_DURATION);
        mAnimationSlideOutSixLines.setDuration(ANIMATION_DURATION);
        mAnimationSlideOutSevenLines.setDuration(ANIMATION_DURATION);

        mNewTimedMsgAnimation = new ScaleAnimation(0.8f, 1.2f, 0.8f, 1.2f);

        mSlideOutAcc = new TranslateAnimation(0, 0, 0, slideheigthTwoLines);
        mSlideOutAcc.setFillAfter(true);

        mNewTimedMsgAnimation.setDuration(200);

        final AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();
        final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

        mAnimationSlideInOneLine.setInterpolator(decelerateInterpolator);
        mAnimationSlideOutOneLine.setInterpolator(decelerateInterpolator);
        mSlideOutAcc.setInterpolator(accelerateInterpolator);
        mAnimationSlideInTwoLines.setInterpolator(decelerateInterpolator);
        mAnimationSlideOutTwoLines.setInterpolator(decelerateInterpolator);
        mAnimationSlideInSixLines.setInterpolator(decelerateInterpolator);
        mAnimationSlideOutSixLines.setInterpolator(decelerateInterpolator);
        mAnimationSlideOutSevenLines.setInterpolator(decelerateInterpolator);

        mSwipeLayout = findViewById(R.id.swipe_refresh_chat);
        mSwipeLayout.setEnabled(false);

        mBackground = findViewById(R.id.chat_bg);

        mTimedCounterView = findViewById(R.id.action_bar_right_image_view_counter);

        mOnChatDataChangedListeners = new ArrayList<>();

        if (savedInstanceState != null) {
            if (savedInstanceState.getString("mTakePhotoUri") != null) {
                mTakePhotoUri = Uri.parse(savedInstanceState.getString("mTakePhotoUri"));
            }
            mTargetGuid = savedInstanceState.getString("mTargetGuid");
            mPublicKeyXML = savedInstanceState.getString("mPublicKeyXML");
        }

        if (mNeedsTargetGuid && mTargetGuid == null) {
            if (getIntent().getStringExtra(EXTRA_TARGET_GUID) != null) {
                mTargetGuid = getIntent().getStringExtra(EXTRA_TARGET_GUID);
                mChat = getChatController().getChatByGuid(mTargetGuid);
                // Know where we are
                notificationController.setCurrentChatGuid(mTargetGuid);
            } else {
                LogUtil.e(TAG, "targetGuid is null");
                finish();
                return;
            }
        }

        mOnBottomSheetClosedListener = bottomSheetWasOpen -> {
            if (!mChatInputDisabled && (mChatInputFragment != null)) {
                hideOrShowFragment(mChatInputFragment, true, true);
                mChatInputFragment.requestFocusForInput();
            }
            if ((mAnimationSlideOut != null) && (mBottomSheetFragment != null) && mBottomSheetFragment.getView() != null) {
                mBottomSheetFragment.getView().startAnimation(mAnimationSlideOut);
            }
        };
        checkIntentForAction(null, false);
    }

    void createOnChatDataChangedListener() {
        //listener wird nur im single udn gruppenchat benutzt, also auch nur dort angestoszen
        mOnTimedMessagesListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(@NonNull final List<Message> messages) {

            }

            @Override
            public void onUniqueResult(@NonNull final Message message) {

            }

            @Override
            public void onCount(final long numberOfTimedMessages) {

                if (numberOfTimedMessages != -1) {
                    if (numberOfTimedMessages > 0 && !mOnlyShowTimed) {
                        final String ctr;
                        try {
                            if (numberOfTimedMessages > 99) {
                                ctr = "99+";
                            } else {
                                ctr = Long.toString(numberOfTimedMessages);
                            }
                            mTimedCounterView.setText(ctr);
                            setRightActionBarImageVisibility(View.VISIBLE);
                            mTimedCounterView.setVisibility(View.VISIBLE);

                            if (mPlayTimdMessagesAnimation) {
                                mTimedCounterView.startAnimation(mNewTimedMsgAnimation);
                                mPlayTimdMessagesAnimation = false;
                            }
                        } catch (final NumberFormatException e) {
                            LogUtil.e(TAG, "createOnChatDataChangedListener: onCount returned " + e.getMessage());
                        }
                    } else {
                        setRightActionBarImageVisibility(View.GONE);
                        mTimedCounterView.setVisibility(View.GONE);
                    }
                }
                scaleTitle();
            }
        };
    }

    void createOnDeleteTimedMessageListener() {
        mOnDeleteTimedMessageListener = new OnDeleteTimedMessageListener() {
            @Override
            public void onDeleteMessageError(@NonNull final String message) {
                if (!StringUtil.isNullOrEmpty(message)) {
                    DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, message).show();
                }
            }

            @Override
            public void onDeleteAllMessagesSuccess(@NonNull final String chatGuid) {
                //wird nur aufgerufen, wenn ein chat komplett geloescht wurde
                finish();
            }

            @Override
            public void onDeleteSingleMessageSuccess(@NonNull final String chatGuid) {
                if (!StringUtil.isNullOrEmpty(chatGuid)) {
                    getSimsMeApplication().getChatOverviewController().chatChanged(null, chatGuid, null,
                            ChatOverviewController.CHAT_CHANGED_MSG_DELETED);
                }
            }
        };
    }

    void createOnTimedMessagesDeliveredListener() {
        // refresh, wnen eine getimte nachricht ausgeliefert wurde und man sich im selben chat befindet
        mOnTimedMessagesDeliveredListener = chatGuids -> {
            final Handler handler = new Handler(getMainLooper());
            final Runnable runnable = () -> {
                filterTimedMessages(mOnlyShowTimed);
                getChatController().countTimedMessages(mOnTimedMessagesListener);
            };
            handler.post(runnable);
        };
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_chat;
    }

    @Override
    protected void onResumeActivity() {
        super.onResumeActivity();

        // Know where we are
        notificationController.setCurrentChatGuid(mTargetGuid);

        hideToolbarOptions();
        if (mOnTimedMessagesDeliveredListener != null) {
            mMessageController.addTimedMessagedDeliveredListener(mOnTimedMessagesDeliveredListener);
        }

        // after colorize activity
        final int lowColor = ScreenDesignUtil.getInstance().getLowColor(getSimsMeApplication());
        final int lowContrastColor = ScreenDesignUtil.getInstance().getLowContrastColor(getSimsMeApplication());

        mTimedCounterView.getBackground().setColorFilter(lowColor, PorterDuff.Mode.SRC_ATOP);
        mTimedCounterView.setTextColor(lowContrastColor);

        final ImageView timedIcon = getRightActionbarImage();
        if (timedIcon != null && mTargetGuid != null) {
            timedIcon.getDrawable().setColorFilter(lowColor, PorterDuff.Mode.SRC_ATOP);
        }
    }

    private ImageView getRightActionbarImage() {
        return getToolbar().findViewById(ACTIONBAR_RIGHT_SIDE_VIEW);
    }

    @Override
    protected void closeBottomSheet(final OnBottomSheetClosedListener listener) {
        super.closeBottomSheet(listener);
        if (!mChatInputDisabled && (mChatInputFragment != null) && (mChatInputFragment.getView() != null)) {
            mChatInputFragment.setOnlineState();
            mChatInputFragment.getView().startAnimation(mAnimationSlideOutOneLine);
        }
    }

    void disableChatinput() {
        if (mChatInputFragment != null) {
            if (mChatInputFragment.isDestructionViewShown()) {
                mChatInputFragment.showDestructionPicker(false, false, false);
            }
            mChatInputFragment.showKeyboard(false);
            hideOrShowFragment(mChatInputFragment, false, true);
        }

        mChatInputDisabled = true;
    }

    void enableChatinput() {
        if (mChatInputFragment != null) {
            hideOrShowFragment(mChatInputFragment, true, true);
            mChatInputFragment.getEditText().requestFocus();
        }
        mChatInputDisabled = false;
    }

    @Override
    public void handleAddAttachmentClick() {
        synchronized (this) {
            if (mChatInputFragment == null || !canSendMedia()) {
                return;
            }
            mChatInputFragment.showEmojiPicker(false);
            mChatInputFragment.showKeyboard(false);
            mChatInputFragment.setOnlineState();

            if (!mBottomSheetMoving) {
                mAnimationSlideIn = mAnimationSlideInSevenLines;
                mAnimationSlideOut = mAnimationSlideOutSevenLines;

                final List<Integer> disabledCommands = new ArrayList<>();
                if (mCitatedChatItem != null) {
                    disabledCommands.add(R.id.attachment_selection_take_video);
                    disabledCommands.add(R.id.attachment_selection_attach_video);
                    disabledCommands.add(R.id.attachment_selection_attach_location);
                    disabledCommands.add(R.id.attachment_selection_send_contact);
                } else if (mChatInputFragment.getTimerEnabled()
                        || mChatInputFragment.getDestructionEnabled()
                        || !StringUtil.isNullOrEmpty(mChatInputFragment.getChatInputText())) {
                    disabledCommands.add(R.id.attachment_selection_attach_location);
                    disabledCommands.add(R.id.attachment_selection_send_contact);
                    disabledCommands.add(R.id.attachment_selection_attach_file);
                }

                if (!canSendMedia()) {
                    disabledCommands.add(R.id.attachment_selection_attach_foto);
                    disabledCommands.add(R.id.attachment_selection_attach_video);
                    disabledCommands.add(R.id.attachment_selection_attach_file);
                }

                if (getSimsMeApplication().getPreferencesController().isCameraDisabled()) {
                    disabledCommands.add(R.id.attachment_selection_take_foto);
                    disabledCommands.add(R.id.attachment_selection_take_video);
                }

                if (getSimsMeApplication().getPreferencesController().isSendContactsDisabled()) {
                    disabledCommands.add(R.id.attachment_selection_send_contact);
                }

                if (getSimsMeApplication().getPreferencesController().isLocationDisabled()) {
                    disabledCommands.add(R.id.attachment_selection_attach_location);
                }

                openBottomSheet(R.layout.dialog_attachment_selection_layout, R.id.chat_bottom_sheet_container, disabledCommands);
            }
        }
    }

    // KS: The '+' menu
    @Override
    protected void openBottomSheet(final int resourceID,
                                   final int containerViewId,
                                   final List<Integer> hiddenViews) {
        mAnimationSlideOutSixLines.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(final Animation animation) {
            }

            @Override
            public void onAnimationEnd(final Animation animation) {
                hideOrShowFragment(mCurrentFragment, false, true);
                BaseChatActivity.super.openBottomSheet(resourceID, containerViewId, hiddenViews);
                mAnimationSlideOutSixLines.setAnimationListener(null);
            }

            @Override
            public void onAnimationRepeat(final Animation animation) {
            }
        });
        if ((mCurrentFragment != null) && (mCurrentFragment.getView() != null)) {
            mCurrentFragment.getView().startAnimation(mAnimationSlideOutSixLines);

            if (!mChatInputDisabled && (mChatInputFragment != null) && (mChatInputFragment.getView() != null)) {
                mChatInputFragment.getView().startAnimation(mAnimationSlideOutSixLines);
            }
            if (mCurrentFragment.equals(mSelfdestructionFragment) && mChatInputFragment != null) {
                mChatInputFragment.showDestructionPicker(false, false);
            }
        }

        //wenn keine der Animationen ausgefuehrt werdne kann, weil alles null ist, dann trotzdem das bottomsheet oeffnen
        else {
            BaseChatActivity.super.openBottomSheet(resourceID, containerViewId, hiddenViews);
            mAnimationSlideOutSixLines.setAnimationListener(null);

            if (!mChatInputDisabled && (mChatInputFragment != null) && (mChatInputFragment.getView() != null)) {
                mChatInputFragment.getView().startAnimation(mSlideOutAcc);
            }
        }
    }

    boolean isChatBlocked() {
        return false;
    }

    boolean isChatReadOnly() {
        return false;
    }

    public void handleMessageInfoClick() {
        final Intent intent = new Intent(this, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID, mMarkedChatItem.messageId);
        intent.putExtra(MessageDetailsActivity.CHAT_GUID, mTargetGuid);

        /* Bug 43557 - Darstellungsfehler in Gelesen-Protokoll bei langen Nachrichten
         * automatisch einfuegte Zeilenumbrueche werden beim Bestimmen der Chatitem-Hoehe nicht beruecksichtigt
         * anstatt eine kompizierte Messung durhczufuehren wird hier einfach die Hoehe aus diesem View mitgegeben
         * + 32 dp fuer das Datum
         *
         */
        final Integer positionFromMessageId = mChatAdapter.getPositionFromMessageId(mMarkedChatItem.messageId);
        final View originalView = mChatAdapter.getView(positionFromMessageId);
        if (originalView != null) {
            final TextView dateView = originalView.findViewById(R.id.chat_item_text_view_date_only);
            final int dateViewHeight;
            if (dateView != null && dateView.getVisibility() == View.VISIBLE) {
                dateViewHeight = 0;
            } else {
                dateViewHeight = MetricsUtil.dpToPx(this, 32);
            }
            intent.putExtra(MessageDetailsActivity.EXTRA_MIN_CHATITEM_HEIGHT, originalView.getHeight() + dateViewHeight);
        }
        startActivityForResult(intent, RouterConstants.CITATE_MESSAGE_RESULT_CODE);
        mCitatedChatItem = mMarkedChatItem;
    }

    public void handleMessageCommentClick() {
        String roomType = Chat.ROOM_TYPE_STD;
        try {
            if(mChat == null) {
                throw new LocalizedException("mChat is null!");
            }
            roomType = mChat.getRoomType();
        } catch (LocalizedException e) {
            LogUtil.w(TAG, "handleMessageCommentClick: Could not get roomType: " + e.getMessage());
        }

        if(!roomType.equals(Chat.ROOM_TYPE_ANNOUNCEMENT)) {
            mCitatedChatItem = mMarkedChatItem;
            mChatInputFragment.showCommentView(mMarkedChatItem, true);
        } else {
            // In announcement group, we may reply directly to the sender of the message.
            // Enter single chat dialog.
            if (mMarkedChatItem == null) {
                return;
            }

            // Just to be sure. Normally it should not happen to see messages from ourselves in
            // announcement group.
            final String fromGuid = mMarkedChatItem.getFromGuid();
            if(!fromGuid.equals(mAccountController.getAccount().getAccountGuid())) {
                final Intent intent = new Intent(this, SingleChatActivity.class);
                intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, fromGuid);
                startActivity(intent);
            }
        }
    }

    public void handleDeleteMessageClick(final View view) {
        getChatController().deleteMessage(mMarkedChatItem, mOnDeleteTimedMessageListener);
        if (!StringUtil.isNullOrEmpty(mTargetGuid)) {
            getSimsMeApplication().getChatOverviewController().chatChanged(null, mTargetGuid, null,
                    ChatOverviewController.CHAT_CHANGED_MSG_DELETED);
        }
    }

    public void handleForwardMessageClick(final View view) {
        forwardMessage(!(mMarkedChatItem instanceof AttachmentChatItemVO));
    }

    public void handleForwardMessageImageClick(final View view) {
        forwardMessage();
    }

    private void filterTimedMessages(final boolean onlyShowTimedMessages) {
        mChatAdapter.clear();
        closeBottomSheet(null);

        if (onlyShowTimedMessages) {
            if (mChatAdapter != null) {
                mChatAdapter.setShowTimedMessages(onlyShowTimedMessages);
            }
            showProgressIndicator();
            getChatController().getTimedMessages();
        } else {
            if (mChatAdapter != null) {
                mChatAdapter.setShowTimedMessages(onlyShowTimedMessages);
            }
            if (getChatController().loadNewestMessagesByGuid()) {
                showProgressIndicator();
            }
        }
    }

    void forwardMessage() {
        forwardMessage(false);
    }

    void forwardMessage(final boolean forwardTextForCombinedChannelMessage) {
        //SIMSME-5766 - mMarkedChatItem kann schon vorher auf 'null' gesetzt sein (Siehe Bugbeschreibung)
        if (mMarkedChatItem == null) {
            return;
        }
        final Long messageId = mMarkedChatItem.messageId;
        // ACHTUNG mMarkedChatItem wird nach dem aufruf diese Methode auf NULL gesetzt und steht im listener nicht mehr zur Verfuegung
        final Message message = getChatController().findMessageById(messageId);

        if (message == null) {
            return;
        }

        showIdleDialog();

        final Intent intent = new Intent(this, ForwardActivity.class);
        intent.putExtra(ForwardActivityBase.EXTRA_MESSAGE_ID, messageId);
        intent.putExtra(ForwardActivityBase.EXTRA_STARTED_INTERNALLY, true);

        if (!forwardTextForCombinedChannelMessage && !StringUtil.isNullOrEmpty(message.getAttachment())) {
            if (message.getType() == Message.TYPE_CHANNEL) {
                intent.putExtra(ForwardActivityBase.EXTRA_FORWARD_CHANNELMESSAGE_IS_IMAGE, true);
            }

            final String messageGuid = message.getGuid() != null ? message.getGuid() : Long.toString(message.getId());

            final OnAttachmentLoadedListener onAttachmentLoadedListener = new OnAttachmentLoadedListener() {
                private final String mMessageGuid = messageGuid;

                @Override
                public void onBitmapLoaded(final File file,
                                           final DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    if (!messageId.equals(decryptedMsg.getMessage().getId())) {
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, getResources().getString(R.string.chat_open_failed_image)).show();
                        dismissIdleDialog();
                        return;
                    }
                    handleForwardMessageAttachmentLoaded(intent);
                }

                @Override
                public void onVideoLoaded(final File videoFile,
                                          final DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    if (!messageId.equals(decryptedMsg.getMessage().getId())) {
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, getResources().getString(R.string.chat_open_failed_video)).show();
                        dismissIdleDialog();
                        return;
                    }
                    handleForwardMessageAttachmentLoaded(intent);
                }

                @Override
                public void onAudioLoaded(final File audioFile,
                                          final DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    if (!messageId.equals(decryptedMsg.getMessage().getId())) {
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, getResources().getString(R.string.chat_open_failed_audio)).show();
                        dismissIdleDialog();
                        return;
                    }
                    handleForwardMessageAttachmentLoaded(intent);
                }

                @Override
                public void onFileLoaded(final File dataFile, final DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    if (!messageId.equals(decryptedMsg.getMessage().getId())) {
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, getResources().getString(R.string.chat_open_failed_file)).show();
                        dismissIdleDialog();
                        return;
                    }
                    handleForwardMessageAttachmentLoaded(intent);
                }

                @Override
                public void onHasNoAttachment(final String message) {
                    dismissIdleDialog();
                    if (!StringUtil.isNullOrEmpty(message)) {
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, message).show();
                    }
                }

                @Override
                public void onHasAttachment(final boolean finishedWork) {
                    markDownloading(mMessageGuid);
                    if (finishedWork) {
                        showIdleDialog(R.string.progress_dialog_load_attachment);
                    }
                }

                @Override
                public void onLoadedFailed(final String message) {
                    finishDownloading(mMessageGuid);
                    dismissIdleDialog();
                    closeBottomSheet(mOnBottomSheetClosedListener);
                    if (isActivityInForeground) {
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this, message).show();
                    }
                }
            };

            final ProgressBar progressBar;
            if (mClickedView != null) {
                progressBar = mClickedView.findViewById(R.id.progressBar_download);
                mClickedView.setTag("downloading");
                unHightlightLastItem();
                mClickedView = null;
            } else {
                progressBar = null;
            }

            if (!isDownloading(messageGuid)) {
                if (progressBar != null) {
                    final OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(mClickedIndex,
                            message.getIsPriority());
                    getChatController().getAttachment(messageId, onAttachmentLoadedListener, false, onConnectionDataUpdatedListener);
                } else {
                    getChatController().getAttachment(messageId, onAttachmentLoadedListener, false, null);
                }
            }
            mClickedIndex = -1;
        } else {
            dismissIdleDialog();

            if (forwardTextForCombinedChannelMessage) {
                if (message.getType() == Message.TYPE_CHANNEL) {
                    intent.putExtra(ForwardActivityBase.EXTRA_FORWARD_CHANNELMESSAGE_IS_TEXT, true);
                }
            }

            if (mOnlyShowTimed) {
                toggleTimedMessages();
            }
            startActivityForResult(intent, FORWARD_REQUEST_CODE);
        }
    }

    /**
     * handleForwardMessageAttachmentLoaded
     *
     * @param intent intent
     */
    private void handleForwardMessageAttachmentLoaded(final Intent intent) {
        dismissIdleDialog();
        if (mOnlyShowTimed) {
            toggleTimedMessages();
        }
        startActivityForResult(intent, FORWARD_REQUEST_CODE);
    }

    @Deprecated
    public void handleForwardMessageClickDistributor(final View view) {
        try {
            String text = ((TextChatItemVO) mMarkedChatItem).message;
            final String encodedUri = URLEncoder.encode(text, "UTF-8");

            text = "simsme://message?text=" + encodedUri;

            final Intent intent = new Intent(this, DistributorChatActivity.class);

            intent.setData(Uri.parse(text));
            startActivity(intent);
        } catch (final UnsupportedEncodingException e) {
            LogUtil.e(TAG, "handleForwardMessageClickDistributor: Caught " + e.getMessage());
            Toast.makeText(this, getString(R.string.chats_forward_message_error), Toast.LENGTH_SHORT).show();
        }
    }

    public void handleAVCAudioClick(View view) {
        handleAVCMessageClick(AVChatController.CALL_TYPE_AUDIO_ONLY);
    }

    public void handleAVCVideoClick(View view) {
        handleAVCMessageClick(AVChatController.CALL_TYPE_AUDIO_VIDEO);
    }

    // Called when the user initiates a call by pressing the "call" button
    @Override
    public boolean handleAVCMessageClick(int callType) {
        if(avChatController == null) {
            return false;
        }

        avChatController.resetAVC();
        avChatController.rollAndSetNewRoomInfo();

        String myName = "John Doe (unknown)";
        try {
            myName = mContactController.getOwnContact().getNameFromNameAttributes() + " (" + mContactController.getOwnContact().getSimsmeId() + ")";
        } catch (LocalizedException e) {
            e.printStackTrace();
        }

        // Send AVC message
        try {
            checkChat();
            getChatController().sendAVC(mTargetGuid,
                    mPublicKeyXML,
                    avChatController.getSerializedRoomInfo(),
                    mOnSendMessageListener,
                    null,
                    mIsPriority,
                    buildCitationFromSelectedChatItem());
            closeCommentView();

            if (mChatInputFragment != null) {
                mChatInputFragment.setOnlineState();
            }
            setOnlineStateToOnline();

        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "handleAVCMessageClick: Caught " + e.getMessage());
            return false;
        }

        avChatController.setMyName(myName);
        avChatController.setConferenceTopic(myName);
        avChatController.setCallType(callType);
        avChatController.startAVCall(this);
        return true;
    }

    @Override
    public boolean handleSendMessageClick(final String text) {
        MessageDestructionParams destructionParams;

        try {
            destructionParams = getDestructionParams();
        } catch (final InvalidDateException e) {
            LogUtil.e(TAG, "handleSendMessageClick: Caught " + e.getMessage());
            mChatInputFragment.setTypingState();
            return false;
        }

        try {
            if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                mPreferencesController.incNumberOfStartedChats();
            }

            if (mSelfdestructionFragment == null || !mChatInputFragment.getTimerEnabled()) {
                checkChat();
                getChatController().sendText(mTargetGuid, mPublicKeyXML, text, destructionParams, mOnSendMessageListener,
                        null, mIsPriority, buildCitationFromSelectedChatItem());

                closeCommentView();
            } else {
                final Calendar oneYear = Calendar.getInstance();
                oneYear.add(Calendar.YEAR, 1);

                if (mSelfdestructionFragment.getTimerDate().after(oneYear.getTime())) {
                    final String msg = getResources().getString(R.string.chats_timedmessage_invalid_date2);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                    mChatInputFragment.setTypingState();
                    return false;
                } else if (!mSelfdestructionFragment.getTimerDate().after(Calendar.getInstance().getTime())) {
                    final String msg = getResources().getString(R.string.chats_selfdestruction_invalid_date);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                    mChatInputFragment.setTypingState();
                    return false;
                } else if (destructionParams != null
                        && destructionParams.date != null
                        && destructionParams.date.before(mSelfdestructionFragment.getTimerDate())) {
                    final String msg = getResources().getString(R.string.chats_selfdestruction_sddate_before_senddate);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                    mChatInputFragment.setTypingState();
                    return false;
                } else {
                    checkChat();
                    getChatController().sendText(mTargetGuid,
                            mPublicKeyXML,
                            text,
                            destructionParams,
                            mOnSendMessageListener,
                            mSelfdestructionFragment.getTimerDate(),
                            mIsPriority,
                            buildCitationFromSelectedChatItem());
                    mPlayTimdMessagesAnimation = true;
                    mChatInputFragment.showKeyboard(true);
                    mChatInputFragment.closeDestructionPicker(true);
                    setResult(RESULT_OK);
                }
            }
            if (mChatInputFragment != null) {
                mChatInputFragment.setOnlineState();
            }
            setOnlineStateToOnline();
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "handleSendMessageClick: Caught " + e.getMessage());
            return false;
        }

        return true;
    }

    @Override
    protected CitationModel buildCitationFromSelectedChatItem() {
        if (mCitatedChatItem == null) {
            return null;
        }

        String msgGuid = mCitatedChatItem.getMessageGuid();

        if (StringUtil.isNullOrEmpty(msgGuid)) {
            final Message message = mMessageController.getMessageById(mCitatedChatItem.messageId);
            if (message != null && !StringUtil.isNullOrEmpty(message.getGuid())) {
                msgGuid = message.getGuid();
            }
        }

        final String contentType;
        final String contentDesc;
        final String toGuid = mCitatedChatItem.getToGuid();
        final String nickname = mCitatedChatItem.name;
        final Long datesend = mCitatedChatItem.getDateSend();
        final String fromGuid = mCitatedChatItem.getFromGuid();
        final String text;
        final Bitmap previewImage;

        if (mCitatedChatItem instanceof ChannelChatItemVO) {
            ChannelChatItemVO cci = (ChannelChatItemVO) mCitatedChatItem;
            text = cci.messageHeader + "\n" + cci.messageContent;
            previewImage = null;
            contentType = MimeType.TEXT_RSS;
            contentDesc = null;
        } else if (mCitatedChatItem instanceof TextChatItemVO) {
            text = ((TextChatItemVO) mCitatedChatItem).message;
            previewImage = null;
            contentType = MimeType.TEXT_PLAIN;
            contentDesc = null;
        } else if (mCitatedChatItem instanceof VCardChatItemVO) {
            text = null;
            previewImage = null;
            contentType = MimeType.TEXT_V_CARD;
            contentDesc = ((VCardChatItemVO) mCitatedChatItem).displayInfo;
        } else if (mCitatedChatItem instanceof LocationChatItemVO) {
            text = null;
            previewImage = ((LocationChatItemVO) mCitatedChatItem).image;
            contentType = MimeType.MODEL_LOCATION;
            contentDesc = null;
        } else if (mCitatedChatItem instanceof ImageChatItemVO) {
            final ImageChatItemVO castedItem = (ImageChatItemVO) mCitatedChatItem;

            if (!StringUtil.isNullOrEmpty(castedItem.attachmentDesc)) {
                contentDesc = castedItem.attachmentDesc;
                text = null;
            } else {
                text = null;
                contentDesc = null;
            }
            previewImage = castedItem.image;
            contentType = MimeType.IMAGE_JPEG;
        } else if (mCitatedChatItem instanceof VideoChatItemVO) {
            final VideoChatItemVO castedItem = (VideoChatItemVO) mCitatedChatItem;

            if (!StringUtil.isNullOrEmpty(castedItem.attachmentDesc)) {
                contentDesc = castedItem.attachmentDesc;
                text = null;
            } else {
                text = null;
                contentDesc = null;
            }
            previewImage = castedItem.image;
            contentType = MimeType.VIDEO_MPEG;
        } else if (mCitatedChatItem instanceof VoiceChatItemVO) {
            text = null;
            previewImage = null;
            contentType = MimeType.AUDIO_MPEG;
            contentDesc = null;
        } else if (mCitatedChatItem instanceof AppGinloControlChatItemVO) {
            // Just for fun ...
            text = ((AppGinloControlChatItemVO) mCitatedChatItem).message;
            previewImage = null;
            contentType = MimeType.TEXT_PLAIN;
            contentDesc = null;
        } else if (mCitatedChatItem instanceof AVChatItemVO) {
            // KS: TODO: AVC forwarding (?) - Do it for now.
            text = null;
            previewImage = null;
            contentType = MimeType.TEXT_V_CALL;
            contentDesc = null;
        } else if (mCitatedChatItem instanceof FileChatItemVO) {
            text = ((FileChatItemVO) mCitatedChatItem).fileName;
            previewImage = null;
            contentType = MimeType.APP_OCTET_STREAM;
            contentDesc = null;
        } else {
            text = null;
            previewImage = null;
            contentType = MimeType.TEXT_PLAIN;
            contentDesc = null;
        }

        return new CitationModel(msgGuid
                , contentType
                , contentDesc
                , toGuid
                , nickname
                , datesend
                , fromGuid
                , text
                , previewImage
        );
    }

    public void handleResendClick(final View view) {
        if (mResendItem != null) {
            getChatController().checkForResend(mResendItem.messageId, mOnSendMessageListener);
        }
        closeBottomSheet(mOnBottomSheetClosedListener);
    }

    @Override
    public void handleSendVoiceClick(final Uri voiceUri) {
        MessageDestructionParams destructionParams;

        try {
            destructionParams = getDestructionParams();
        } catch (final InvalidDateException e) {
            LogUtil.e(TAG, "handleSendVoiceClick: Caught " + e.getMessage());
            mChatInputFragment.showAudioPreviewUI();
            return;
        }

        try {
            if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                mPreferencesController.incNumberOfStartedChats();
            }

            if (mSelfdestructionFragment == null || !mChatInputFragment.getTimerEnabled()) {
                checkChat();
                getChatController().sendVoice(this, mTargetGuid, mPublicKeyXML, voiceUri, destructionParams, mOnSendMessageListener, null, mIsPriority);
                mChatInputFragment.closeDestructionPicker(true);
            } else {
                final Calendar oneYear = Calendar.getInstance();
                oneYear.add(Calendar.YEAR, 1);

                if (mSelfdestructionFragment.getTimerDate().after(oneYear.getTime())) {
                    final String msg = getResources().getString(R.string.chats_timedmessage_invalid_date2);
                    DialogBuilderUtil.buildErrorDialog(this, msg, 0,
                            unused -> mChatInputFragment.showAudioPreviewUI()).show();
                    return;
                }
                if (!mSelfdestructionFragment.getTimerDate().after(Calendar.getInstance().getTime())) {
                    final String msg = getResources().getString(R.string.chats_selfdestruction_invalid_date);
                    DialogBuilderUtil.buildErrorDialog(this, msg, 0,
                            unused -> mChatInputFragment.showAudioPreviewUI()).show();
                    return;
                } else if (destructionParams != null
                        && destructionParams.date != null
                        && destructionParams.date.before(mSelfdestructionFragment.getTimerDate())) {
                    final String msg = getResources().getString(R.string.chats_selfdestruction_sddate_before_senddate);
                    DialogBuilderUtil.buildErrorDialog(this, msg, 0,
                            unused -> mChatInputFragment.showAudioPreviewUI()).show();
                    return;
                } else {
                    checkChat();
                    getChatController().sendVoice(this, mTargetGuid, mPublicKeyXML, voiceUri, destructionParams,
                            mOnSendMessageListener, mSelfdestructionFragment.getTimerDate(), mIsPriority);
                }
            }
            setResult(RESULT_OK);

            if (mChatInputFragment != null) {
                mChatInputFragment.setOnlineState();
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "handleSendVoiceClick: Caught " + e.getMessage());
        }
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    public void handleTakePhotoClick(final View view) {
        LogUtil.d(TAG, "handleTakePhotoClick: " + view);
        if (getSimsMeApplication().getPreferencesController().isCameraDisabled()) {
            LogUtil.i(TAG, "handleTakePhotoClick: disabled by policy!");
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_camera_access_not_allowed), Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera,
                (permission, permissionGranted) -> {
                    if ((permission == PermissionUtil.PERMISSION_FOR_CAMERA) && permissionGranted) {
                        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        // KS:Always null on some devices since SDK 30 ???
                        //if (intent.resolveActivity(getPackageManager()) != null) {
                            try {
                                final FileUtil fu = new FileUtil(getSimsMeApplication());
                                final File takenPhotoFile = fu.createTmpImageFileAddInIntent(intent);
                                mTakePhotoUri = Uri.fromFile(takenPhotoFile);
                                closeBottomSheet(mOnBottomSheetClosedListener);

                                router.startExternalActivityForResult(intent, RouterConstants.TAKE_PHOTO_RESULT_CODE);
                            } catch (final Exception e) {
                                LogUtil.w(TAG, "handleTakePhotoClick: permissionResult returned with " + e.getMessage());
                            }
                        //}
                    }
                });
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    public void handleTakeVideoClick(final View view) {
        if (getSimsMeApplication().getPreferencesController().isCameraDisabled()) {
            LogUtil.i(TAG, "handleTakeVideoClick: disabled by policy!");
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_camera_access_not_allowed), Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermission(PermissionUtil.PERMISSION_FOR_VIDEO, R.string.permission_rationale_camera,
                (permission, permissionGranted) -> {
                    if ((permission == PermissionUtil.PERMISSION_FOR_VIDEO) && permissionGranted) {
                        final Intent intent = new Intent(BaseChatActivity.this, CameraActivity.class);

                        closeBottomSheet(mOnBottomSheetClosedListener);
                        try {
                            startActivityForResult(intent, RouterConstants.TAKE_VIDEO_RESULT_CODE);
                        } catch (final Exception e) {
                            LogUtil.w(TAG, "handleTakeVideoClick: startActivityForResult returned with " + e.getMessage());
                        }
                    }
                });
    }

    // OnClick of menu in resend dialog (dialog_resend_layout.xml)
    public void handleResendCancelSendClick(final View view) {
        // Nachricht aus der View loeschen
        if (mResendItem != null) {
            getChatController().deleteMessage(mResendItem, mOnDeleteTimedMessageListener);
        }

        // Doialog Schliessen
        closeBottomSheet(mOnBottomSheetClosedListener);
    }

    @Override
    public void handleCloseBottomSheetClick(final View view) {
        closeBottomSheet(mOnBottomSheetClosedListener);
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    //@SuppressLint("NewApi")
    public void handleAttachPhotoClick(final View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    (permission, permissionGranted) -> {
                        if ((permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE)
                                && permissionGranted) {
                            startAttachPhotoIntent();
                        }
                    });
        } else {
            startAttachPhotoIntent();
        }
    }

    private void startAttachPhotoIntent() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        intent.setType("image/*");

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        closeBottomSheet(mOnBottomSheetClosedListener);
        router.startExternalActivityForResult(intent, RouterConstants.SELECT_PHOTO_RESULT_CODE);
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    public void handleAttachVideoClick(final View view) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    (permission, permissionGranted) -> {
                        if ((permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE)
                                && permissionGranted) {
                            startAttachVideoIntent();
                        }
                    });
        } else {
            startAttachVideoIntent();
        }
    }

    private void startAttachVideoIntent() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        intent.setType("video/*");

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        closeBottomSheet(mOnBottomSheetClosedListener);
        router.startExternalActivityForResult(intent, RouterConstants.SELECT_VIDEO_RESULT_CODE);
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    public void handleAttachLocationClick(final View view) {
        requestPermission(PermissionUtil.PERMISSION_FOR_LOCATION, R.string.permission_rationale_location,
                (permission, permissionGranted) -> {
                    if ((permission == PermissionUtil.PERMISSION_FOR_LOCATION) && permissionGranted) {

                        if(mApplication.havePlayServices(BaseChatActivity.this) && !preferencesController.getOsmEnabled()) {
                            final Intent intent = new Intent(BaseChatActivity.this, LocationActivity.class);
                            intent.putExtra(LocationActivity.EXTRA_MODE, LocationActivity.MODE_GET_LOCATION);
                            closeBottomSheet(mOnBottomSheetClosedListener);
                            try {
                                startActivityForResult(intent, RouterConstants.GET_LOCATION_RESULT_CODE);
                            } catch (final Exception e) {
                                LogUtil.w(TAG, "handleAttachLocationClick: startActivityForResult returned with " + e.getMessage());
                            }
                        } else {
                            final Intent intent = new Intent(BaseChatActivity.this, LocationActivityOSM.class);
                            intent.putExtra(LocationActivityOSM.EXTRA_MODE, LocationActivityOSM.MODE_GET_LOCATION);
                            closeBottomSheet(mOnBottomSheetClosedListener);
                            try {
                                startActivityForResult(intent, RouterConstants.GET_LOCATION_RESULT_CODE);
                            } catch (final Exception e) {
                                LogUtil.w(TAG, "handleAttachLocationClick: startActivityForResult returned with " + e.getMessage());
                            }
                        }
                    }
                });
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    public void handleAttachContactClick(final View view) {
        final String title = getResources().getString(R.string.send_contact_title);
        final String text = getResources().getString(R.string.send_contact_text);
        final String extern = getResources().getString(R.string.send_contact_extern);
        final String intern = getResources().getString(R.string.send_contact_intern);

        DialogInterface.OnClickListener externOnClickListener = (dialog, which) -> requestPermission(PermissionUtil.PERMISSION_FOR_READ_CONTACTS, R.string.permission_rationale_contacts,
                (permission, permissionGranted) -> {
                    if ((permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS) && permissionGranted) {
                        final Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);

                        closeBottomSheet(mOnBottomSheetClosedListener);
                        router.startExternalActivityForResult(intent, RouterConstants.SELECT_CONTACT_RESULT_CODE);
                    }
                });

        DialogInterface.OnClickListener internOnClickListener = (dialog, which) -> {
            final Intent intent = new Intent(BaseChatActivity.this, RuntimeConfig.getClassUtil().getContactsActivityClass());
            intent.putExtra(ContactsActivity.EXTRA_MODE, ContactsActivity.MODE_SEND_CONTACT);

            closeBottomSheet(mOnBottomSheetClosedListener);
            router.startExternalActivityForResult(intent, RouterConstants.SELECT_INTERNAL_CONTACT_RESULT_CODE);
        };
        AlertDialogWrapper attachContactDialog = DialogBuilderUtil.buildResponseDialog(this,
                text,
                title,
                extern,
                intern,
                externOnClickListener,
                internOnClickListener);

        //noinspection ConstantConditions //angeblich soll der Dialog null sein koennen
        attachContactDialog.getDialog().setCancelable(true);
        attachContactDialog.getDialog().setCanceledOnTouchOutside(true);
        attachContactDialog.show();
    }

    // OnClick of menu in attachment dialog (dialog_attachment_selection_layout.xml)
    /**
     * handleAttachFileClick
     *
     * @param view view
     */
    public void handleAttachFileClick(final View view) {
        closeBottomSheet(mOnBottomSheetClosedListener);

        try {
            final Intent openFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openFile.addCategory(Intent.CATEGORY_OPENABLE);
            openFile.setType("*/*");
            openFile.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/*", "image/*", "video/*", "audio/*", "text/*"});

            router.startExternalActivityForResult(openFile, RouterConstants.SELECT_FILE_RESULT_CODE);
        } catch (final ActivityNotFoundException e) {
            LogUtil.e(TAG, "handleAttachFileClick: Could not locate an app for selecting a file: " + e.getMessage());
            Toast.makeText(this, R.string.chat_file_open_error_no_app_to_pick_found, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected boolean hasHeaderView() {
        return mListView.getHeaderViewsCount() > 0;
    }

    protected void setProfilePicture(int drawableId,
                                     OnClickListener clickListener,
                                     String contentDescription, final Integer customImageColor) {
        setActionBarImage(ACTIONBAR_RIGHT_SIDE_PROFILE_VIEW, drawableId, contentDescription, customImageColor);
        setActionBarListener(ACTIONBAR_RIGHT_SIDE_PROFILE_VIEW, clickListener);
    }

    /**
     * @throws InvalidDateException [!EXC_DESCRIPTION!]
     */
    MessageDestructionParams getDestructionParams()
            throws InvalidDateException {
        MessageDestructionParams destructionParams = null;

        if (mSelfdestructionFragment != null && mChatInputFragment != null && mChatInputFragment.getDestructionEnabled()) {
            destructionParams = mSelfdestructionFragment.getDestructionConfiguration();
            if ((destructionParams.date != null) && destructionParams.date.before(Calendar.getInstance().getTime())) {
                final String msg = getResources().getString(R.string.chats_selfdestruction_invalid_date);

                DialogBuilderUtil.buildErrorDialog(this, msg).show();
                throw new InvalidDateException();
            }
        }

        return destructionParams;
    }

    /**
     * scrollIfLastChatItemIsNotShown
     */
    @Override
    public void scrollIfLastChatItemIsNotShown() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> {
            if(mListView == null) return;
            final int lastLastPosition = mListView.getLastVisiblePosition() + 1;
            if (lastLastPosition != mListView.getAdapter().getCount()) {
                scrollToEnd();
            }
        };

        final Runnable runnable2 = this::scrollToEnd;
        handler.postDelayed(runnable, 200);
        handler.postDelayed(runnable2, 700);
    }

    @Override
    protected void onActivityPostLoginResult(final int requestCode,
                                             final int resultCode,
                                             final Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);

        if (resultCode == RESULT_OK) {
            try {
                switch (requestCode) {
                    case RouterConstants.TAKE_VIDEO_RESULT_CODE: {
                        final String path = returnIntent.getStringExtra("data");
                        final Uri recordedVideo = Uri.fromFile(new File(path));

                        final ArrayList<String> uris = new ArrayList<>(1);

                        uris.add(recordedVideo.toString());

                        final Intent intentVideo = new Intent(this, PreviewActivity.class);

                        intentVideo.putExtra(PreviewActivity.EXTRA_PREVIEW_ACTION, PreviewActivity.TAKE_VIDEOS_ACTION);
                        intentVideo.putExtra(PreviewActivity.EXTRA_PREVIEW_TITLE, getChatTitle());
                        addInputTextToPreviewIntent(intentVideo, uris);
                        clearChatInputAndClipboard();
                        intentVideo.putStringArrayListExtra(PreviewActivity.EXTRA_URIS, uris);
                        if (mCitatedChatItem != null) {
                            intentVideo.putExtra(ChatInputActivity.EXTRA_CITATED_MSG_MODEL_ID, mCitatedChatItem.messageId);
                        }
                        addDestructionParamsAndTimerToIntent(intentVideo);
                        startActivityForResult(intentVideo,RouterConstants.GET_VIDEO_WITH_DESTRUCTION_RESULT_CODE);

                        break;
                    }
                    case RouterConstants.TAKE_PHOTO_RESULT_CODE: {
                        Uri takenPhoto;

                        try {
                            takenPhoto = (new FileUtil(this)).copyFileToInternalDir(mTakePhotoUri);
                        } catch (final LocalizedException e) {
                            LogUtil.w(TAG, "onActivityPostLoginResult: TAKE_PHOTO_RESULT_CODE returned: " + LocalizedException.UNDEFINED_ARGUMENT);
                            return;
                        }

                        final ArrayList<String> uris = new ArrayList<>(1);

                        uris.add(takenPhoto.toString());

                        final Intent photoIntent = new Intent(this, PreviewActivity.class);

                        photoIntent.putExtra(PreviewActivity.EXTRA_PREVIEW_ACTION, PreviewActivity.TAKE_PHOTOS_ACTION);
                        photoIntent.putExtra(PreviewActivity.EXTRA_PREVIEW_TITLE, getChatTitle());
                        addInputTextToPreviewIntent(photoIntent, uris);
                        clearChatInputAndClipboard();
                        photoIntent.putStringArrayListExtra(PreviewActivity.EXTRA_URIS, uris);
                        if (mCitatedChatItem != null) {
                            photoIntent.putExtra(ChatInputActivity.EXTRA_CITATED_MSG_MODEL_ID, mCitatedChatItem.messageId);
                        }
                        addDestructionParamsAndTimerToIntent(photoIntent);
                        startActivityForResult(photoIntent, RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE);

                        break;
                    }
                    case RouterConstants.SELECT_PHOTO_RESULT_CODE: {
                        handleResult(returnIntent, true);

                        break;
                    }
                    case RouterConstants.SELECT_VIDEO_RESULT_CODE: {
                        handleResult(returnIntent, false);

                        break;
                    }
                    case RouterConstants.SELECT_CONTACT_RESULT_CODE: {
                        final Uri contactUri = returnIntent.getData();
                        final String vCard = mContactController.getVCardForContactUri(this, contactUri);

                        if (vCard != null) {
                            checkChat();
                            if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                                mPreferencesController.incNumberOfStartedChats();
                            }
                            getChatController().sendVCard(mTargetGuid,
                                    mPublicKeyXML,
                                    vCard,
                                    null,
                                    null,
                                    mOnSendMessageListener);
                            setResult(RESULT_OK);
                        }
                        break;
                    }
                    case RouterConstants.SELECT_INTERNAL_CONTACT_RESULT_CODE: {
                        final String contactGuid = returnIntent.getStringExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS);
                        final Contact contact = mContactController.getContactByGuid(contactGuid);

                        final VCard vCard = new VCard();

                        final String email = contact.getEmail();
                        if (!StringUtil.isNullOrEmpty(email)) {
                            vCard.addEmail(email);
                        }

                        final String phoneNumber = contact.getPhoneNumber();
                        if (!StringUtil.isNullOrEmpty(phoneNumber) && phoneNumber.startsWith("+")) {
                            vCard.addTelephoneNumber(phoneNumber);
                        }

                        final String name = contact.getName();
                        if (!StringUtil.isNullOrEmpty(name)) {
                            vCard.addFormattedName(new FormattedName(name));
                        }

                        final String nickName = contact.getNickname();
                        if (!StringUtil.isNullOrEmpty(name)) {
                            final Nickname nickname = new Nickname();
                            nickname.getValues().add(nickName);
                            vCard.addNickname(new Nickname(nickname));
                        }

                        checkChat();
                        if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                            mPreferencesController.incNumberOfStartedChats();
                        }
                        getChatController().sendVCard(mTargetGuid,
                                mPublicKeyXML,
                                vCard.write(),
                                contact.getSimsmeId(),
                                contactGuid,
                                mOnSendMessageListener);
                        setResult(RESULT_OK);
                        break;
                    }
                    case RouterConstants.SELECT_FILE_RESULT_CODE: {
                        mActionContainer = null;

                        final Uri fileUri = returnIntent.getData();
                        final MimeUtil mu = new MimeUtil(this);
                        final String mimeType = mu.getMimeType(fileUri);

                        //Simulieren einer SendAction
                        final Intent actionIntent = new Intent(Intent.ACTION_SEND);
                        actionIntent.setType(StringUtil.isNullOrEmpty(mimeType) ? MimeType.APP_OCTET_STREAM : mimeType);
                        actionIntent.putExtra(Intent.EXTRA_STREAM, fileUri);

                        //Die Action wird dann im onResume mittels checkActionContainer gestartet
                        checkIntentForAction(actionIntent, true);
                        break;
                    }
                    case RouterConstants.GET_LOCATION_RESULT_CODE: {
                        final byte[] screenshot = returnIntent.getByteArrayExtra(LocationActivity.EXTRA_SCREENSHOT);
                        final double longitude = returnIntent.getDoubleExtra(LocationActivity.EXTRA_LONGITUDE, 0.0);
                        final double latitude = returnIntent.getDoubleExtra(LocationActivity.EXTRA_LATITUDE, 0.0);

                        checkChat();
                        if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                            mPreferencesController.incNumberOfStartedChats();
                        }
                        getChatController().sendLocation(mTargetGuid, mPublicKeyXML, longitude, latitude, screenshot,
                                mOnSendMessageListener);
                        setResult(RESULT_OK);
                        break;
                    }
                    case RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE: {
                        final List<String> imageUris = returnIntent.getStringArrayListExtra(PreviewActivity.EXTRA_URIS);
                        final List<String> imageTexts = returnIntent.getStringArrayListExtra(PreviewActivity.EXTRA_TEXTS);
                        final boolean isPriority = returnIntent.getBooleanExtra(PreviewActivity.EXTRA_IS_PRIORITY, false);

                        MessageDestructionParams params = null;
                        Date timerDate = null;

                        if (returnIntent.hasExtra(PreviewActivity.EXTRA_DESTRUCTION_PARAMS)) {
                            params = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(PreviewActivity.EXTRA_DESTRUCTION_PARAMS), MessageDestructionParams.class);
                        }

                        if (returnIntent.hasExtra(PreviewActivity.EXTRA_TIMER)) {
                            timerDate = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(PreviewActivity.EXTRA_TIMER), Date.class);
                        }

                        if ((imageUris == null) || (imageUris.size() < 1)) {
                            break;
                        }

                        for (int i = 0; i < imageUris.size(); ++i) {
                            checkChat();
                            if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                                mPreferencesController.incNumberOfStartedChats();
                            }
                            getChatController().sendImage(this, mTargetGuid, mPublicKeyXML, Uri.parse(imageUris.get(i)), imageTexts.get(i), params,
                                    mOnSendMessageListener/*, ocdul*/, timerDate, isPriority, buildCitationFromSelectedChatItem(), true);
                            mPlayTimdMessagesAnimation = true;
                        }
                        closeCommentView();
                        if (mChatInputFragment != null) {
                            mChatInputFragment.closeDestructionPicker(true);
                        }
                        setResult(RESULT_OK);

                        break;
                    }
                    case RouterConstants.GET_VIDEO_WITH_DESTRUCTION_RESULT_CODE: {
                        final List<String> videoUris = returnIntent.getStringArrayListExtra(PreviewActivity.EXTRA_URIS);
                        final List<String> videoTexts = returnIntent.getStringArrayListExtra(PreviewActivity.EXTRA_TEXTS);
                        final boolean isPriority = returnIntent.getBooleanExtra(PreviewActivity.EXTRA_IS_PRIORITY, false);
                        Date timerDate = null;
                        MessageDestructionParams params = null;

                        if (returnIntent.hasExtra(PreviewActivity.EXTRA_DESTRUCTION_PARAMS)) {
                            params = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(PreviewActivity.EXTRA_DESTRUCTION_PARAMS), MessageDestructionParams.class);
                        }

                        if (returnIntent.hasExtra(PreviewActivity.EXTRA_TIMER)) {
                            timerDate = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(PreviewActivity.EXTRA_TIMER), Date.class);
                        }

                        if ((videoUris == null) || (videoUris.size() < 1)) {
                            break;
                        }

                        for (int i = 0; i < videoUris.size(); ++i) {
                            checkChat();
                            if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                                mPreferencesController.incNumberOfStartedChats();
                            }
                            getChatController().sendVideo(this, mTargetGuid, mPublicKeyXML, Uri.parse(videoUris.get(i)), videoTexts.get(i), params,
                                    mOnSendMessageListener, timerDate, isPriority, true);
                        }
                        closeCommentView();
                        if (mChatInputFragment != null) {
                            mChatInputFragment.closeDestructionPicker(true);
                        }
                        setResult(RESULT_OK);
                        break;
                    }
                    case RouterConstants.CITATE_MESSAGE_RESULT_CODE: {

                        final String returnAction = returnIntent.getStringExtra(MessageDetailsActivity.EXTRA_RETURN_ACTION);

                        if (!StringUtil.isNullOrEmpty(returnAction)) {
                            switch (returnAction) {
                                case MessageDetailsActivity.EXTRA_RETURN_ACTION_DELETE_MSG:
                                    getChatController().deleteMessage(mCitatedChatItem, mOnDeleteTimedMessageListener);
                                    break;

                                case MessageDetailsActivity.EXTRA_RETURN_ACTION_FORWARD_MSG:
                                    mMarkedChatItem = mCitatedChatItem;
                                    forwardMessage();
                                    break;

                                case MessageDetailsActivity.EXTRA_RETURN_ACTION_FORWARD_IMAGE:
                                    mMarkedChatItem = mCitatedChatItem;
                                    forwardMessage(true);
                                    break;
                                default:
                                    LogUtil.w(TAG, "onActivityPostLoginResult: returnAction: " + LocalizedException.UNDEFINED_ARGUMENT);
                                    return;
                            }
                            mCitatedChatItem = null;
                        } else {
                            final String returnType = returnIntent.getStringExtra(MessageDetailsActivity.EXTRA_RETURN_TYPE);
                            final boolean isPriority = returnIntent.getBooleanExtra(MessageDetailsActivity.EXTRA_RETURN_IS_PRIORITY, false);
                            if (StringUtil.isEqual(returnType, MimeType.TEXT_PLAIN)) {
                                final String text = returnIntent.getStringExtra(MessageDetailsActivity.EXTRA_RETURN_TEXT);

                                if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                                    mPreferencesController.incNumberOfStartedChats();
                                }
                                getChatController().sendText(mTargetGuid, mPublicKeyXML, text, null, mOnSendMessageListener,
                                        null, isPriority, buildCitationFromSelectedChatItem());

                                mCitatedChatItem = null;
                            } else if (StringUtil.isEqual(returnType, MimeType.IMAGE_JPEG)) {
                                final List<String> imageUris = returnIntent.getStringArrayListExtra(MessageDetailsActivity.EXTRA_RETURN_IMAGE_URIS);
                                final List<String> imageTexts = returnIntent.getStringArrayListExtra(MessageDetailsActivity.EXTRA_RETURN_IMAGE_TEXTS);

                                final MessageDestructionParams params = null;
                                final Date timerDate = null;

                                for (int i = 0; i < imageUris.size(); ++i) {
                                    checkChat();
                                    if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                                        mPreferencesController.incNumberOfStartedChats();
                                    }
                                    getChatController().sendImage(this, mTargetGuid, mPublicKeyXML, Uri.parse(imageUris.get(i)), imageTexts.get(i), params,
                                            mOnSendMessageListener/*, ocdul*/, timerDate, isPriority, buildCitationFromSelectedChatItem(), true);
                                    mPlayTimdMessagesAnimation = true;
                                }
                                mCitatedChatItem = null;
                            } else if (StringUtil.isEqual(returnType, MimeType.APP_OCTET_STREAM)) {
                                final Uri fileUri = returnIntent.getData();

                                final MimeUtil mu = new MimeUtil(this);
                                final String mimeType = mu.getMimeType(fileUri);

                                //Simulieren einer SendAction
                                final Intent actionIntent = new Intent(Intent.ACTION_SEND);
                                actionIntent.setType(StringUtil.isNullOrEmpty(mimeType) ? MimeType.APP_OCTET_STREAM : mimeType);
                                actionIntent.putExtra(Intent.EXTRA_STREAM, fileUri);

                                //Die Action wird dann im onResume mittels checkActionContainer gestartet
                                checkIntentForAction(actionIntent, true);
                            }
                        }
                        break;
                    }
                    case FORWARD_REQUEST_CODE: {
                        if (mOnlyShowTimed && getChatController() != null && mChat != null) {
                            getChatController().clearAdapter(mChat.getChatGuid());
                        }

                        finish();
                        break;
                    }
                    default:
                        LogUtil.w(TAG, "onActivityPostLoginResult: requestCode: " + LocalizedException.UNDEFINED_ARGUMENT);
                        return;
                }

            } catch (final LocalizedException e) {
                LogUtil.w(TAG, "onActivityPostLoginResult: Caught " + e.getMessage());
                return;
            }
        } else {
            if (requestCode == RouterConstants.CITATE_MESSAGE_RESULT_CODE) {
                mCitatedChatItem = null;
            }
        }

        if (requestCode == RouterConstants.SEND_FILE_RESULT_CODE && mShareFileUri != null) {
            //Rechte wieder entfernen nach Share einer Datei
            revokeUriPermission(mShareFileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mAttachmentController.deleteShareFiles();
        }
    }

    /**
     * @param intent intent
     */
    private void addDestructionParamsAndTimerToIntent(final Intent intent) {
        if (mSelfdestructionFragment != null) {
            if (mChatInputFragment.getDestructionEnabled()) {
                try {
                    final MessageDestructionParams destructionParams = getDestructionParams();
                    intent.putExtra(PreviewActivity.EXTRA_DESTRUCTION_PARAMS, destructionParams);
                } catch (final InvalidDateException e) {
                    LogUtil.w(TAG, "addDestructionParamsAndTimerToIntent: Caught " + e.getMessage());
                }
            }
            if (mChatInputFragment.getTimerEnabled()) {
                intent.putExtra(PreviewActivity.EXTRA_TIMER, mSelfdestructionFragment.getTimerDate());
            }
        }
    }

    private void handleResult(final Intent returnIntent,
                              final boolean handleImageResult) {
        final FileUtil fileUtil = new FileUtil(this);
        final FileUtil.UrisResultContainer resultContainer;

        if (handleImageResult) {
            resultContainer = fileUtil.getUrisFromImageActionIntent(returnIntent);
        } else {
            resultContainer = fileUtil.getUrisFromVideoActionIntent(returnIntent);
        }

        final ArrayList<String> uris = resultContainer.getUris();
        if (resultContainer.getHasImportError() && ((uris == null) || (uris.size() < 1))) {
            Toast.makeText(this, R.string.chats_addAttachment_wrong_format_or_error, Toast.LENGTH_LONG).show();
            return;
        }

        if (uris.size() > 0) {
            final Intent intent = new Intent(this, PreviewActivity.class);
            intent.putExtra(PreviewActivity.EXTRA_PREVIEW_TITLE, getChatTitle());
            intent.putStringArrayListExtra(PreviewActivity.EXTRA_URIS, uris);
            addInputTextToPreviewIntent(intent, uris);
            clearChatInputAndClipboard();
            addDestructionParamsAndTimerToIntent(intent);
            if (handleImageResult) {
                intent.putExtra(PreviewActivity.EXTRA_PREVIEW_ACTION, PreviewActivity.SELECT_PHOTOS_ACTION);
                startActivityForResult(intent, RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE);
            } else {
                intent.putExtra(PreviewActivity.EXTRA_PREVIEW_ACTION, PreviewActivity.SELECT_VIDEOS_ACTION);
                startActivityForResult(intent, RouterConstants.GET_VIDEO_WITH_DESTRUCTION_RESULT_CODE);
            }
        }

        if (uris.size() > PreviewActivity.MAX_MEDIA_ITEMS) {
            Toast.makeText(this, getString(R.string.chats_addAttachments_too_many), Toast.LENGTH_LONG).show();
        } else if (resultContainer.getHasImportError()) {
            Toast.makeText(this, getString(R.string.chats_addAttachments_some_imports_fails), Toast.LENGTH_LONG).show();
        } else if (resultContainer.getHasFileTooLargeError()) {
            Toast.makeText(this, getString(R.string.chats_addAttachment_too_big), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Text an PreviewActivity uebergeben
     */
    private void addInputTextToPreviewIntent(final Intent intent, final List<String> uris) {
        if (mChatInputFragment != null) {
            final String inputText = mChatInputFragment.getChatInputText();
            if (!StringUtil.isNullOrEmpty(inputText)) {
                // nur fuer das letzte Element den inputText mitgeben
                final ArrayList<String> texts = new ArrayList<>();
                final int size = uris.size();
                for (int i = 0; i < size - 1; ++i) {
                    texts.add("");
                }
                texts.add(inputText);
                intent.putExtra(PreviewActivity.EXTRA_TEXTS, texts);
            }
        }
    }

    /**
     * chatinput clearen
     */
    private void clearChatInputAndClipboard() {
        try {
            if (mChatInputFragment != null) {
                mChatInputFragment.clearText();
            }
            clipBoardController.put(mTargetGuid, "");
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "clearChatInputAndClipboard: Caught " + e.getMessage());
        }
    }

    /**
     * zur zitierten Nachricht scrollen
     *
     * @param v view
     */
    public void scrollToOriginalMessage(final View v) {
        // message id der original msg rausfinden -> guid zu id umbauen im adapter
        // gucken, ob erste msg-id < message id
        // wenn ja scrollen
        // wnen nein loadmessages bis zur id mit listener
        final String originalMessageGuid = (String) v.getTag();
        scrollToMessageViaGuid(originalMessageGuid, true);
    }

    /**
     * ueber die Guid zu einer Nachricht scrollen
     *
     * @param originalMessageGuid originalMessageGuid
     * @param hightlight          soll die Nachricht nach dem scrollvorgang blinken ?
     */
    private void scrollToMessageViaGuid(final String originalMessageGuid, final boolean hightlight) {
        if (originalMessageGuid != null) {
            final Message origionalMessage = mMessageController.getMessageByGuid(originalMessageGuid);

            if (origionalMessage != null) {
                final Long originalMessageId = origionalMessage.getId();
                if (originalMessageId == null) {
                    LogUtil.w(TAG, "originalMessageId is null");
                } else {
                    final BaseChatItemVO firstChatItem = mChatAdapter.getItem(0);
                    if (firstChatItem != null) {
                        if (firstChatItem.messageId <= originalMessageId) {
                            scrollToOriginalMessageHelper(originalMessageId, hightlight);
                        } else {
                            showIdleDialog(-1);

                            final OnChatDataChangedListener onChatDataChangedListener = new OnChatDataChangedListener() {

                                @Override
                                public void onChatDataChanged(final boolean clearImageCache) {

                                }

                                @Override
                                public void onChatDataLoaded(final long lastMessageId) {
                                    dismissIdleDialog();
                                    scrollToOriginalMessageHelper(originalMessageId, hightlight);
                                }
                            };
                            getChatController().loadMessagesFromIdToId(originalMessageId, firstChatItem.messageId, onChatDataChangedListener);
                        }
                    }
                }
            }
        }
    }

    private void scrollToOriginalMessageHelper(final Long originalMessagegId, final boolean hightlight) {

        final Integer position = mChatAdapter.getPositionFromMessageId(originalMessagegId);

        if (position != null) {
            hideToolbarOptions();
            if (hightlight) {
                final Runnable highlightRunnable = () -> {
                    final View originalView = mChatAdapter.getView(position);
                    highlightChatitem(originalView);
                };

                //unhighlight aber mit schliessen der toolbaroptions, falls der user inzwischen schon ein weiteres element markiert hat
                final Runnable unHighlightRunnable = this::hideToolbarOptions;
                final Handler handler = new Handler();

                handler.postDelayed(highlightRunnable, 500);
                handler.postDelayed(unHighlightRunnable, 2000);
            }
            mListView.setSelection(position);
        }
    }

    void scrollToEnd() {
        if (isActivityInForeground()) {
            if ((mListView != null) && (mChatAdapter != null)) {
                if (mListView.getHeaderViewsCount() > 0) {
                    mListView.setSelection(mChatAdapter.getCount() - 1 + mListView.getHeaderViewsCount());
                } else {
                    mListView.setSelection(mChatAdapter.getCount() - 1);
                }
            }
        }
    }

    @Override
    public void smoothScrollToEnd() {
        if (isActivityInForeground()) {
            if ((mListView != null) && (mChatAdapter != null)) {
                mListView.smoothScrollToPosition(mChatAdapter.getCount());

                /* Bug 44226 - Chatverlauf springt beim senden längerer Nachrichten
                 * urspuenglich gebaut fuer:
                 * Bug 35425 - Navigation von Detail-Bildansicht zu Chatansicht führt zum Verlust der Scrollposition
                 * da das Scrollingmitlerweile anders funktioniert, soltle der Handle rnicht mehr benoetigt werden
                 * erstmal nur auskommentiert
                 */
            }
        }
    }

    @Override
    public void onChatDataLoaded(final long lastMessageId) {
        if (mLastMessageId < lastMessageId
                && ChatController.NO_MESSAGE_ID_FOUND != lastMessageId
                && ChatController.NO_MESSAGE_ID_FOUND != mLastMessageId) {
            if (mChatAdapter != null && mChatAdapter.getCount() > 0) {
                final BaseChatItemVO lastItem = mChatAdapter.getItem(mChatAdapter.getCount() - 1);
                if (lastItem != null && lastItem.direction == BaseChatItemVO.DIRECTION_LEFT) {
                    final int lastVisPos = mListView.getLastVisiblePosition() - mListView.getHeaderViewsCount();
                    BaseChatItemVO lastVisibleItem = null;

                    if (lastVisPos > -1) {
                        final int pos = (lastVisPos < mChatAdapter.getCount()) ? lastVisPos : (mChatAdapter.getCount() - 1);
                        lastVisibleItem = mChatAdapter.getItem(pos);
                    }

                    if (lastVisibleItem != null
                            && lastVisibleItem.messageId < mLastMessageId
                            && (mListView.getFirstVisiblePosition() > 0 || lastVisPos < mChatAdapter.getCount())) {
                        if (mNewMessagesToast == null) {
                            mNewMessagesToast = Toast.makeText(BaseChatActivity.this, R.string.chat_notification_new_message, Toast.LENGTH_SHORT);
                        }

                        if (mNewMessagesToast.getView() != null && mNewMessagesToast.getView().getWindowVisibility() != View.VISIBLE) {
                            mNewMessagesToast.show();
                        }
                    } else {
                        scrollToEnd();
                    }
                } else {
                    scrollToEnd();
                }
            } else {
                scrollToEnd();
            }
        }

        if (!mOnlyShowTimed) {
            if (mLastMessageId == ChatController.NO_MESSAGE_ID_FOUND) {
                if (mChatAdapter != null && mChatAdapter.getCount() > 0) {
                    final BaseChatItemVO item = mChatAdapter.getItem(mChatAdapter.getCount() - 1);
                    if (item != null/* && item.direction == BaseChatItemVO.DIRECTION_LEFT*/) {
                        mLastMessageId = item.messageId;
                    }
                }
            } else if (lastMessageId != ChatController.NO_MESSAGE_ID_FOUND && lastMessageId > mLastMessageId) {
                mLastMessageId = lastMessageId;
            }
        }

        hideProgressIndicator();

        if (mOldLoadCount != 0) {
            mListView.setSelection(mChatAdapter.getCount() - mOldLoadCount - 1);
            mOldLoadCount = 0;
        }

        if (mOnlyShowTimed) {
            scrollToEnd();
        } else {
            checkLoadMoreView();
        }

        for (final OnChatDataChangedListener onChatDataChangedListener : mOnChatDataChangedListeners) {
            onChatDataChangedListener.onChatDataLoaded(lastMessageId);
        }
    }

    @Override
    protected void prepareResendMenu(final BaseChatItemVO baseChatItemVO) {
        if (!mBottomSheetMoving) {
            mAnimationSlideIn = mAnimationSlideInTwoLines;
            mAnimationSlideOut = mAnimationSlideOutTwoLines;
            openBottomSheet(R.layout.dialog_resend_layout, R.id.chat_bottom_sheet_container);
        }

        if (mChatInputFragment != null) {
            mChatInputFragment.showKeyboard(false);
        }
        mResendItem = baseChatItemVO;
    }

    @Override
    public boolean onItemLongClick(final AdapterView<?> parent,
                                   final View view,
                                   final int position,
                                   final long id) {
        hideToolbarOptions();
        unHightlightLastItem();
        mClickedView = view;

        if ((mBottomSheetOpen)
                || ((mChatInputFragment != null) && mChatInputFragment.isRecording())) {
            return true;
        }

        final int offset = hasHeaderView() ? 1 : 0;
        final int adapterPosition = position - offset;
        mClickedIndex = adapterPosition;

        if (mChatAdapter == null) {
            return false;
        }

        if (adapterPosition >= mChatAdapter.getCount()) {
            return true;
        }

        final BaseChatItemVO baseChatItemVO = mChatAdapter.getItem(adapterPosition);

        if (baseChatItemVO == null) {
            return false;
        }

        baseChatItemVO.setSelected(true);

        mMarkedChatItem = baseChatItemVO;

        final boolean isSystemChat = !StringUtil.isNullOrEmpty(baseChatItemVO.getFromGuid()) && GuidUtil.isSystemChat(baseChatItemVO.getFromGuid());
        final boolean isMyMessage = mMessageController.isSentByMe(mMarkedChatItem.getFromGuid());

        final List<ToolbarOptionsItemModel> toolbarOptionsItemModels = new ArrayList<>();

        boolean isReadonly;
        try {
            if (mChat != null) {
                isReadonly = mChat.getIsReadOnly();
            } else {
                isReadonly = true;
            }

            if (mMarkedChatItem instanceof ChannelChatItemVO) {
                if (!(mMarkedChatItem instanceof ChannelSelfDestructionChatItemVO)) {
                    if (!StringUtil.isNullOrEmpty(((ChannelChatItemVO) mMarkedChatItem).messageContent)) {
                        toolbarOptionsItemModels.add(createToolbarOptionsForwardModel());
                    }

                    if (((ChannelChatItemVO) mMarkedChatItem).image != null && canSendMedia()) {
                        toolbarOptionsItemModels.add(createToolbarOptionsForwardPictureModel());
                    }

                    //SGA: ja die if-anweisung ist doppelt, aber die reiehenfolge soll erhalten bleiben
                    if (!StringUtil.isNullOrEmpty(((ChannelChatItemVO) mMarkedChatItem).messageContent) && !getSimsMeApplication().getPreferencesController().isCopyPasteDisabled()) {
                        toolbarOptionsItemModels.add(createToolbarOptionsCopyModel());
                    }
                }

                toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
            } else if (mMarkedChatItem instanceof TextChatItemVO) {
                toolbarOptionsItemModels.add(createToolbarOptionsForwardModel());
                if (!getSimsMeApplication().getPreferencesController().isCopyPasteDisabled()) {
                    toolbarOptionsItemModels.add(createToolbarOptionsCopyModel());
                }
                toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                if (!isSystemChat
                        && !mOnlyShowTimed
                        && mChat != null
                        && !isReadonly
                        && !Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())
                        && isMyMessage
                ) {
                    toolbarOptionsItemModels.add(createToolbarOptionsInfoModel());
                }
            } else if ((mMarkedChatItem instanceof ImageChatItemVO) || (mMarkedChatItem instanceof VideoChatItemVO)
                    || (mMarkedChatItem instanceof FileChatItemVO)) {
                if (canSendMedia()) {
                    toolbarOptionsItemModels.add(createToolbarOptionsForwardModel());
                }
                toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                if (!isSystemChat
                        && !mOnlyShowTimed
                        && mChat != null
                        && !isReadonly
                        && !Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())
                        && isMyMessage
                ) {
                    toolbarOptionsItemModels.add(createToolbarOptionsInfoModel());
                }
            } else if (mMarkedChatItem instanceof VoiceChatItemVO
                    || mMarkedChatItem instanceof SelfDestructionChatItemVO
                    || mMarkedChatItem instanceof LocationChatItemVO) {
                toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                if (!isSystemChat
                        && !mOnlyShowTimed
                        && mChat != null
                        && !isReadonly
                        && !Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())
                        && !Chat.ROOM_TYPE_MANAGED.equals(mChat.getRoomType())
                        && isMyMessage
                ) {
                    toolbarOptionsItemModels.add(createToolbarOptionsInfoModel());
                }
            } else if (mMarkedChatItem instanceof VCardChatItemVO) {
                toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                if (mChat != null
                        && !isReadonly
                        && !Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())
                        && !Chat.ROOM_TYPE_MANAGED.equals(mChat.getRoomType())
                        && isMyMessage
                ) {
                    toolbarOptionsItemModels.add(createToolbarOptionsInfoModel());
                }
            } else {
                toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
            }

            if (mChatInputFragment != null
                    && !(mMarkedChatItem instanceof SelfDestructionChatItemVO)
                    && !(mMarkedChatItem instanceof SystemInfoChatItemVO)
                    && !mOnlyShowTimed
                    && !mChatInputFragment.getDestructionEnabled()
                    && !mChatInputFragment.getTimerEnabled()
            ) {
                toolbarOptionsItemModels.add(createToolbarOptionsCommentModel());
            }
        } catch (final LocalizedException e) {
            // wenn hier eine LE auftritt
            LogUtil.w(TAG, "onItemLongClick: Caught " + e.getMessage());
        }
        showToolbarOptions(toolbarOptionsItemModels);
        highlightChatitem(mClickedView);
        return true;
    }

    private void highlightChatitem(final View clickedView) {
        if (clickedView != null) {
            View selectionOverlay = clickedView.findViewById(R.id.chat_item_selection_overlay);

            // FIXME die setMovementMethod faengt die touches auf dem text ab
            // TODO eventuell die movement methode ueberschreiben
            if (selectionOverlay == null && clickedView instanceof TextView) {
                ViewParent viewParent = clickedView.getParent();

                for (int i = 0; i < 5; ++i) {
                    if (viewParent instanceof View) {
                        final View parentAsView = (View) viewParent;
                        final String tag = (String) parentAsView.getTag();
                        if (StringUtil.isEqual(tag, "chat_item_selection_overlay")) {
                            selectionOverlay = parentAsView;
                            mClickedView = (View) parentAsView.getParent();
                            break;
                        }
                    }
                    viewParent = viewParent.getParent();
                }
            } else {
                mClickedView = clickedView;
            }

            if (selectionOverlay != null) {
                selectionOverlay.setBackgroundColor(ScreenDesignUtil.getInstance().getAppAccentColor(getSimsMeApplication()));
            }
        }
    }

    private void unHightlightLastItem() {
        if (mClickedView != null) {
            if(mMarkedChatItem != null) {
                mMarkedChatItem.setSelected(false);
                mMarkedChatItem = null;
            }
            final View selectionOverlay = mClickedView.findViewById(R.id.chat_item_selection_overlay);
            if (selectionOverlay != null) {
                selectionOverlay.setBackgroundColor(ScreenDesignUtil.getInstance().getTransparentColor(getSimsMeApplication()));
            }
        }
    }

    @Override
    protected void hideToolbarOptions() {
        super.hideToolbarOptions();
        unHightlightLastItem();
    }

    @Override
    public void onEmojiSelected(@NotNull String unicode) {
        // KS: Only append Emoji at the end of the line? No!
        //mChatInputFragment.appendText(unicode);
        mChatInputFragment.insertText(unicode);
    }

    @Override
    public void onBackSpaceSelected() {
        TextExtensionsKt.backspace(mChatInputFragment.getEditText());
    }

    @Override
    protected void onPauseActivity() {
        super.onPauseActivity();
        unregisterDataObserver();

        //zuruecksetzen
        mActionContainer = null;

        if (mSendFileDialog != null) {
            //noinspection ConstantConditions //angeblich soll der Dialog null sein koennen
            mSendFileDialog.getDialog().cancel();
        }

        if (mOnTimedMessagesDeliveredListener != null) {
            mMessageController.removeTimedMessagedDeliveredListener(mOnTimedMessagesDeliveredListener);
        }

        // KS: Re-enable notifications which were set to be ignored in subclass instances to avoid
        // sending notifications to a chat the user currently visits.
        // (SingleChatActivity, GroupChatActivity, ChannelChatActivity, SystemChatActivity).
        notificationController.ignoreGuid(null);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    void setBackground()
            throws LocalizedException {
        final Bitmap background = chatImageController.getBackground();

        if (background != null) {
            mBackground.setImageBitmap(background);
            mBackground.setVisibility(View.VISIBLE);
        } else {
            mBackground.setVisibility(View.GONE);
        }
    }

    protected void firstSendMessageIsSavedInDB() {
        //
    }

    OnSendMessageListener createOnSendMessageListener(final String contactsGuid) {
        return new OnSendMessageListener() {
            boolean isFirstMsg = true;

            @Override
            public void onSaveMessageSuccess(final Message message) {
                if (isFirstMsg) {
                    isFirstMsg = false;
                    firstSendMessageIsSavedInDB();
                }
                if (message.getDateSendTimed() == null) {
                    getChatController().addSendMessage(contactsGuid, message);
                } else {
                    getChatController().countTimedMessages(mOnTimedMessagesListener);
                }
            }

            @Override
            public void onSendMessageSuccess(final Message message,
                                             final int countNotSendMessages) {
                if (countNotSendMessages > 0) {
                    final String notSendMsg = (getChatController() instanceof SingleChatController)
                            ? getString(R.string.chat_message_failed_update)
                            : getString(R.string.chat_group_oldversion, countNotSendMessages);

                    getChatController().sendSystemInfo(contactsGuid, mPublicKeyXML, notSendMsg, -1);
                }

                mApplication.playMessageSendSound();

                setActionbarColorFromTrustState();
                smoothScrollToEnd();
            }

            @Override
            public void onSendMessageError(final Message message,
                                           final String aErrorMsg,
                                           final String localizedErrorIdentifier) {
                String errorMsg = aErrorMsg;
                if (errorMsg == null) {
                    errorMsg = BaseChatActivity.this.getResources().getString(R.string.chat_message_failed_failed_label);
                } else if (errorMsg.equals(BaseChatActivity.this.getResources().getString(R.string.service_ERR_0007))
                        || errorMsg.equals(BaseChatActivity.this.getResources().getString(R.string.service_ERR_0079))) {
                    disableChatinput();
                }

                if (BaseChatActivity.this.isActivityInForeground) {
                    if (mSendMessageDialog == null) {
                        mSendMessageDialog = RuntimeConfig.getClassUtil().getDialogHelper(getSimsMeApplication())
                                .getMessageSendErrorDialog(BaseChatActivity.this, errorMsg, localizedErrorIdentifier);
                    } else {
                        mSendMessageDialog.setMessage(errorMsg);
                    }

                    if (mSendMessageDialog.getDialog() != null && !mSendMessageDialog.getDialog().isShowing()) {
                        mSendMessageDialog.show();
                    }
                }
                smoothScrollToEnd();
            }
        };
    }

    protected void setActionbarColorFromTrustState() {
    }

    protected void preventSelfConversation() {
        final OnCloseListener onCloseListener = ref -> finish();

        if ((mTargetGuid != null) && mTargetGuid.equals(mAccountController.getAccount().getAccountGuid())) {
            DialogBuilderUtil.buildErrorDialog(this, getString(R.string.chat_sendmessage_ownGuid), 0, onCloseListener)
                    .show();
        }
    }

    protected void registerDataObserver() {
        mLastChangeCount = 0;
        synchronized (mObserver) {
            if (!mObserver.isRegistered()) {
                mChatAdapter.registerDataSetObserver(mObserver);
                mObserver.markAsRegistered(true);
            }
        }
    }

    private void unregisterDataObserver() {
        if (mChatAdapter != null) {
            mLastChangeCount = 0;
            synchronized (mObserver) {
                if (mObserver.isRegistered()) {
                    mChatAdapter.unregisterDataSetObserver(mObserver);
                }
                mObserver.markAsRegistered(false);
            }
        }
    }

    protected void showProgressIndicator() {
        if (mSwipeLayout != null) {
            mSwipeLayout.setRefreshing(true);
        }
    }

    protected void hideProgressIndicator() {
        if (mSwipeLayout != null) {
            mSwipeLayout.setRefreshing(false);
        }
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mMenu != null) {
            mMenu.close();
        }
        if (mEmojiconsFragment != null && mEmojiconsFragment.getView() != null && mEmojiconsFragment.getView().getLayoutParams() != null) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mEmojiconsFragment.getView().getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_landscape);
            } else {
                mEmojiconsFragment.getView().getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_portrait);
            }
        }
        hideToolbarOptions();
    }

    public void onBackArrowPressed() {

//      if (mToolbarOptionsLayout != null && mToolbarOptionsLayout.getVisibility() == View.VISIBLE)
//      {
//         hideToolbarOptions();
//      }
        // wenn man im Modus "getimte Nachrichten anzeigen" ist -> Modus wechseln
        if (mOnlyShowTimed) {
            toggleTimedMessages();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onBackPressed() {
        // Achtung, Reihenfolge beachten!

        //FAB-Menue offen
        if (mSpeedDialView != null && mSpeedDialView.isOpen()) {
            mSpeedDialView.close();
        } else if (mToolbarOptionsLayout != null && mToolbarOptionsLayout.getVisibility() == View.VISIBLE) {
            hideToolbarOptions();
        }
        // Bottomsheet offen? -> schlieszen
        else if (mBottomSheetOpen) {
            closeBottomSheet(mOnBottomSheetClosedListener);
        } else if (mChatInputFragment != null && mChatInputFragment.isCommenting()) {
            closeCommentView();
        }
        // wenn man im Modus "getimte Nachrichten anzeigen" ist -> Modus wechseln
        else if (mOnlyShowTimed) {
            toggleTimedMessages();
        }
        // Emoji-Tastatur offen? -> schlieszen
        else if ((mChatInputFragment != null) && (mChatInputFragment.getEmojiEnabled())) {
            mChatInputFragment.showEmojiPicker(false);
            KeyboardUtil.toggleSoftInputKeyboard(this, mChatInputFragment.getEditText(), true);
        }
        // SZ-Fragment offen? -> schlieszen
        else if ((mChatInputFragment != null) && (mChatInputFragment.isDestructionViewShown())) {
            mChatInputFragment.closeDestructionPicker(true);
        } else {
            super.onBackPressed();
        }
    }

    ////

    public void showDatePickerDialog(final View view) {
        if (mSelfdestructionFragment != null) {
            final DatePickerFragment newFragment = new DatePickerFragment();

            newFragment.setFragment(mSelfdestructionFragment);
            newFragment.show(getFragmentManager(), "datePicker");
        }
    }

    public void showTimePickerDialog(final View view) {
        if (mSelfdestructionFragment != null) {
            final TimePickerFragment newFragment = new TimePickerFragment();

            newFragment.setFragment(mSelfdestructionFragment);
            newFragment.show(getFragmentManager(), "timePicker");
        }
    }

    @Override
    protected void onDestroy() {
        if (getChatController() != null) {
            getChatController().removeListener(this);
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(final Bundle bundle) {
        if (mTakePhotoUri != null) {
            bundle.putString("mTakePhotoUri", mTakePhotoUri.toString());
        }
        if (mTargetGuid != null) {
            bundle.putString("mTargetGuid", mTargetGuid);
        }

        if (mPublicKeyXML != null) {
            bundle.putString("mPublicKeyXML", mPublicKeyXML);
        }

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onLinkClick(final String link) {
        if (link != null) {
            final Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(link));
            router.startExternalActivity(i);
        }
    }

    private void checkIntentForAction(final Intent returnIntent, boolean forceFileHandling) {
        final Intent intent = returnIntent != null ? returnIntent : getIntent();
        final String action = intent.getAction();
        if (!StringUtil.isNullOrEmpty(action) && (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action))) {
            try {
                mActionContainer = new FileUtil(this).checkFileSendActionIntent(intent, forceFileHandling);

                if (!StringUtil.isNullOrEmpty(mActionContainer.displayMessage)) {
                    Toast.makeText(this, mActionContainer.displayMessage, Toast.LENGTH_LONG).show();
                }
            } catch (final LocalizedException e) {
                LogUtil.w(TAG, "checkIntentForAction: Caught " + e.getMessage());
                final String identifier = e.getIdentifier();
                if (!StringUtil.isNullOrEmpty(identifier)) {
                    if (LocalizedException.NO_ACTION_SEND.equals(identifier) || LocalizedException.NO_DATA_FOUND.equals(identifier)) {
                        Toast.makeText(this, R.string.chat_share_file_infos_are_missing, Toast.LENGTH_LONG).show();
                    } else if (LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED.equals(identifier)) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        } else {
            final long forwardMessageId = getIntent().getLongExtra(EXTRA_FORWARD_MESSAGE_ID, -1);

            if (forwardMessageId == -1) {
                return;
            }
            mActionContainer = new SendActionContainer();
            mActionContainer.action = SendActionContainer.ACTION_FORWARD;
            mActionContainer.forwardMessageId = forwardMessageId;
            mActionContainer.forwardChannelMessageIsImage = getIntent().getBooleanExtra(EXTRA_FORWARD_CHANNELMESSAGE_IS_IMAGE,
                    false);
            mActionContainer.forwardChannelMessageIsText = getIntent().getBooleanExtra(EXTRA_FORWARD_CHANNELMESSAGE_IS_TEXT,
                    false);
        }
    }

    void checkActionContainer() {
        if (mActionContainer != null) {
            switch (mActionContainer.action) {
                case SendActionContainer.ACTION_SEND: {
                    switch (mActionContainer.type) {
                        case SendActionContainer.TYPE_TXT: {
                            LogUtil.d(TAG, "checkActionContainer: Processing SendActionContainer.TYPE_TEXT.");
                            mChatInputFragment.setChatInputText(mActionContainer.text, false);
                            mActionContainer = null;
                            break;
                        }
                        case SendActionContainer.TYPE_IMAGE: {
                            LogUtil.d(TAG, "checkActionContainer: Processing SendActionContainer.TYPE_IMAGE.");
                            final ArrayList<Uri> uris = mActionContainer.uris;
                            final ArrayList<String> uriStrings = new ArrayList<>(uris.size());

                            for (final Uri uri : uris) {
                                uriStrings.add(uri.toString());
                            }

                            startPreviewActivityForResult(RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE, PreviewActivity.SELECT_PHOTOS_ACTION, uriStrings, false);
                            mActionContainer = null;
                            break;
                        }
                        case SendActionContainer.TYPE_VIDEO: {
                            LogUtil.d(TAG, "checkActionContainer: Processing SendActionContainer.TYPE_VIDEO.");
                            final ArrayList<Uri> uris = mActionContainer.uris;
                            final ArrayList<String> uriStrings = new ArrayList<>(uris.size());

                            for (final Uri uri : uris) {
                                uriStrings.add(uri.toString());
                            }

                            startPreviewActivityForResult(RouterConstants.GET_VIDEO_WITH_DESTRUCTION_RESULT_CODE, PreviewActivity.SELECT_VIDEOS_ACTION, uriStrings, false);
                            mActionContainer = null;
                            break;
                        }
                        case SendActionContainer.TYPE_FILE: {
                            LogUtil.d(TAG, "checkActionContainer: Processing SendActionContainer.TYPE_FILE.");
                            if (mActionContainer.uris != null && mActionContainer.uris.size() > 0) {
                                final Uri fileUri = mActionContainer.uris.get(0);
                                final DialogInterface.OnClickListener positiveListener = (dialog, which) -> {
                                    // Action may take a while. Remove dialog immediately to signal user that we are on our way.
                                    dialog.dismiss();
                                    closeCommentView();
                                    try {
                                        final Uri fileUri1 = mActionContainer.uris.get(0);

                                        checkChat();
                                        if (mChatAdapter != null && mChatAdapter.getCount() == 0) {
                                            mPreferencesController.incNumberOfStartedChats();
                                        }
                                        getChatController().sendFile(BaseChatActivity.this, mTargetGuid, mPublicKeyXML,
                                                fileUri1, true, null, null, mOnSendMessageListener, buildCitationFromSelectedChatItem());
                                        mActionContainer = null;
                                        setResult(RESULT_OK);
                                    } catch (final LocalizedException e) {
                                        LogUtil.w(TAG, "checkActionContainer: SendActionContainer.TYPE_FILE caught " + e.getMessage());
                                    }
                                };

                                final DialogInterface.OnClickListener negativeListener = (dialog, which) -> {
                                    closeCommentView();
                                    setResult(RESULT_CANCELED);
                                };

                                final FileUtil fu = new FileUtil(this);
                                final MimeUtil mu = new MimeUtil(this);
                                final String filename = fu.getFileName(fileUri);
                                long fileSize;
                                try {
                                    fileSize = fu.getFileSize(fileUri);
                                } catch (LocalizedException e) {
                                    fileSize = 0;
                                    LogUtil.w(TAG, "Failed to get file size." + e.getMessage());
                                }

                                showSendFileDialog(filename, mu.getExtensionForUri(fileUri), fileSize, positiveListener, negativeListener);
                            }
                            break;
                        }
                        default: {
                            mActionContainer = null;
                            break;
                        }
                    }
                    break;
                }
                case SendActionContainer.ACTION_FORWARD: {
                    LogUtil.d(TAG, "checkActionContainer: Processing SendActionContainer.ACTION_FORWARD.");
                    final Message message = getChatController().findMessageById(mActionContainer.forwardMessageId);

                    if (message != null) {
                        if (!mActionContainer.forwardChannelMessageIsText
                                && (mActionContainer.forwardChannelMessageIsImage || !StringUtil.isNullOrEmpty(message.getAttachment()))
                        ) {
                            getChatController().getAttachment(message.getId(), this, false, null);
                        } else {
                            final DecryptedMessage decMessage = messageDecryptionController.decryptMessage(message, false);
                            try {
                                if (decMessage != null && (StringUtil.isEqual(decMessage.getContentType(), MimeType.TEXT_PLAIN) || StringUtil.isEqual(decMessage.getContentType(), MimeType.TEXT_RSS)) && mChatInputFragment != null) {
                                    mChatInputFragment.setChatInputText(decMessage.getText(), false);
                                } else {
                                    Toast.makeText(this, getString(R.string.chats_forward_message_error), Toast.LENGTH_SHORT).show();
                                }
                            } catch (LocalizedException e) {
                                LogUtil.w(TAG, "checkActionContainer: SendActionContainer.ACTION_FORWARD caught " + e.getMessage());
                            }
                            mActionContainer = null;
                        }
                    }
                    break;
                }
                default: {
                    LogUtil.w(TAG, "checkActionContainer: Unknown action: " + mActionContainer.action);
                    mActionContainer = null;
                    break;
                }
            }
        }
    }

    /**
     * Create and show a dialog box for starting or cancelling a file attachment
     * @param filename
     * @param extension
     * @param fileSize
     * @param positiveListener
     * @param negativeListener
     */
    @Override
    protected void showSendFileDialog(final String filename,
                                      final String extension,
                                      final long fileSize,
                                      final DialogInterface.OnClickListener positiveListener,
                                      final DialogInterface.OnClickListener negativeListener) {
        if (!StringUtil.isNullOrEmpty(filename)) {
            final String sendButton = getResources().getString(R.string.general_send);
            final String cancelButton = getResources().getString(R.string.general_no_thank);

            final StringBuilder sb = new StringBuilder(getResources().getString(R.string.send_action_file_send_hint));

            sb.append("<br/>");
            sb.append("<br/>");
            sb.append(filename);

            if (filename.lastIndexOf('.') < 0) {
                if (!StringUtil.isNullOrEmpty(extension)) {
                    sb.append(".");
                    sb.append(extension);
                }
            }

            if (fileSize > 0) {
                sb.append("(");
                sb.append(StringUtil.getReadableByteCount(fileSize));
                sb.append(")");
            }

            if (!StringUtil.isNullOrEmpty(mTitle)) {
                sb.append("<br/>");
                sb.append(getResources().getString(R.string.send_action_file_send_message));
                sb.append(" <b>");
                // Recipient
                sb.append(mTitle);
                sb.append("</b>");
            }

            final Spanned message = Html.fromHtml(sb.toString());

            mSendFileDialog = DialogBuilderUtil.buildResponseDialogV7(this,
                    null,
                    getResources().getString(R.string.send_action_file_send_title),
                    sendButton,
                    cancelButton,
                    positiveListener,
                    negativeListener);

            mSendFileDialog.setMessage(message);
            mSendFileDialog.show();
        }
    }

    private void startPreviewActivityForResult(final int resultCode,
                                               final int previewAction,
                                               final ArrayList<String> uris,
                                               final boolean showAddButton) {
        final Intent intent = new Intent(this, PreviewActivity.class);

        intent.putExtra(PreviewActivity.EXTRA_URIS, uris);
        intent.putExtra(PreviewActivity.EXTRA_PREVIEW_ACTION, previewAction);
        intent.putExtra(PreviewActivity.EXTRA_SHOW_ADD_BUTTON, showAddButton);

        startActivityForResult(intent, resultCode);
    }

    /**
     * laedt und filtert die Nachrichten (nur getimte oder alle)
     */
    void toggleTimedMessages() {
        if (mOnlyShowTimed) {
            mOnlyShowTimed = false;
            if (mChatAdapter != null) {
                mChatAdapter.setShowTimedMessages(false);
            }
            showNonTimedMessages();
            getChatController().countTimedMessages(mOnTimedMessagesListener);
            // wenn man zwischen den getimten und normalen
            // wechselt und dann zurueck zu normalen wechselt udn hochscrollt,
            // gibt es einen darstellungsfehler, wenn man remove headerview nutzt
            // daher der umweg ueber die visibility

            if (mChatInputFragment.getChatInputText() != null && mChatInputFragment.getChatInputText().length() <= 0) {
                hideChatInputFabButton();
            } else {
                showChatInputFabButton();
            }
        } else {
            mOnlyShowTimed = true;
            if (mChatAdapter != null) {
                mChatAdapter.setShowTimedMessages(true);
            }
            showOnlyTimedMessages();
            mLoadMoreView.setVisibility(View.GONE);

            mSpeedDialView.setVisibility(View.GONE);
            mSpeedDialView.close();
        }
    }

    private void showOnlyTimedMessages() {
        filterTimedMessages(true);
        disableChatinput();
        setProfilePictureVisibility(View.GONE);
        setTitle(getString(R.string.chat_timed_messages_item));
        setRightActionBarImageVisibility(View.GONE);
        mTimedCounterView.setVisibility(View.GONE);
        if (mMenu != null) {
            mMenu.clear();
        }
    }

    void showNonTimedMessages() {
        filterTimedMessages(false);
        enableChatinput();
        setProfilePictureVisibility(View.VISIBLE);
        setTitle(mTitle);
        setRightActionBarImageVisibility(View.VISIBLE);
        mTimedCounterView.setVisibility(View.VISIBLE);

        onCreateOptionsMenu(mMenu);
        //selection merken
        final OnChatDataChangedListener onChatDataChangedListener = new OnChatDataChangedListener() {
            @Override
            public void onChatDataChanged(final boolean clearImageCache) {
            }

            @Override
            public void onChatDataLoaded(final long lastMessageId) {
                mOnChatDataChangedListeners.remove(this);
                scrollToOriginalMessageHelper(mLastMessageId, false);
            }
        };
        mOnChatDataChangedListeners.add(onChatDataChangedListener);
    }

    private void checkLoadMoreView() {
        if (mChatAdapter != null) {
            final long msgCount = getChatController().getChatMessagesCount(mTargetGuid, false);

            if (msgCount > mChatAdapter.getCount()) {
                if (!mOnlyShowTimed) {
                    mLoadMoreView.setVisibility(View.VISIBLE);
                }
                if (!hasHeaderView()) {
                    mListView.setAdapter(null);
                    if (!mOnlyShowTimed) {
                        mListView.addHeaderView(mLoadMoreView);
                    }
                    mListView.setAdapter(mChatAdapter);
                }
            } else if (hasHeaderView() && (msgCount <= mChatAdapter.getCount())) {
                mListView.removeHeaderView(mLoadMoreView);
            }
        }
    }

    @Override
    public boolean canSendMedia() {
        if (mPreferencesController != null) {
            return mPreferencesController.canSendMedia();
        } else if (getSimsMeApplication() != null) {
            return getSimsMeApplication().getPreferencesController().canSendMedia();
        } else {
            return false;
        }
    }

    @Override
    public void setTitle(final CharSequence title) {
        if (mOnlyShowTimed) {
            super.setTitle(getString(R.string.chat_timed_messages_item));
        } else {
            super.setTitle(title);
        }
    }

    public void handleClearChatClick(final View v) {
        if (mOverflowMenuDialog != null) {
            mOverflowMenuDialog.hide();
        }
        final DialogInterface.OnClickListener positiveOnClickListener = (dialog, which) -> {
            final Chat chat = getChatController().getChatByGuid(mTargetGuid);

            if (chat != null) {
                showIdleDialog();
                getChatController().clearChat(chat, new GenericActionListener<Void>() {

                    @Override
                    public void onSuccess(Void object) {
                        dismissIdleDialog();
                        mLastMessageId = ChatController.NO_MESSAGE_ID_FOUND;

                        if (hasHeaderView()) {
                            mListView.removeHeaderView(mLoadMoreView);
                        }

                        if (!StringUtil.isNullOrEmpty(mTargetGuid)) {
                            getSimsMeApplication().getChatOverviewController().chatChanged(null, mTargetGuid, null,
                                    ChatOverviewController.CHAT_CHANGED_CLEAR_CHAT);
                        }
                    }

                    @Override
                    public void onFail(String message, String errorIdent) {
                        dismissIdleDialog();
                        DialogBuilderUtil.buildErrorDialog(BaseChatActivity.this,
                                message).show();
                    }
                });
            }
        };
        final DialogInterface.OnClickListener negativeOnClickListener = (dialog, which) -> {
        };

        final String title = getResources().getString(R.string.chats_clear_chat);
        final String positiveButton = getResources().getString(R.string.chats_clear_chat);
        final String message = getResources().getString(mClearChatQuestionText);

        final String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this,
                message,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

    @Override
    public void onChatDataChanged(final boolean clearImageCache) {
    }

    static class InvalidDateException
            extends Exception {
        private static final long serialVersionUID = -8517774871137413532L;
    }

    private class ChatDataSetObserver
            extends DataSetObserver {

        private boolean mIsRegistered = false;

        private boolean isRegistered() {
            return mIsRegistered;
        }

        private void markAsRegistered(final boolean isRegistered) {
            mIsRegistered = isRegistered;
        }

        @Override
        public void onChanged() {
            super.onChanged();

            if ((mListView == null) || (mChatAdapter == null) || (mChatAdapter.getCount() == 0)
                    || (mChatAdapter.getCount() == mLastChangeCount)) {
                return;
            }

            synchronized (mListView) {
                checkLoadMoreView();
            }
            mLastChangeCount = mChatAdapter.getCount();
        }
    }
}
