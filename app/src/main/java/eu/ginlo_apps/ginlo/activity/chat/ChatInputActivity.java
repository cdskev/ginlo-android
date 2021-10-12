// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chat;

import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialOverlayLayout;
import com.leinardi.android.speeddial.SpeedDialView;

import org.json.JSONException;
import org.json.JSONObject;

import eu.ginlo_apps.ginlo.AVCActivity;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.DestructionActivity;
import eu.ginlo_apps.ginlo.LocationActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewAttachmentActivity;
import eu.ginlo_apps.ginlo.activity.profile.ProfileActivity;
import eu.ginlo_apps.ginlo.adapter.ChatAdapter;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.AudioManager;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeleteTimedMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.fragment.SelfdestructionFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AppGinloControlMessage;
import eu.ginlo_apps.ginlo.model.CitationModel;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.chat.AVChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.FileChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.LocationChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VCardChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VoiceChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.model.param.SendActionContainer;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
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
import eu.ginlo_apps.ginlo.view.MaskImageView;
import ezvcard.VCard;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static eu.ginlo_apps.ginlo.controller.ContactController.ONLINE_STATE_ABSENT;
import static eu.ginlo_apps.ginlo.controller.ContactController.ONLINE_STATE_INVALID;
import static eu.ginlo_apps.ginlo.controller.ContactController.ONLINE_STATE_ONLINE;
import static eu.ginlo_apps.ginlo.controller.ContactController.ONLINE_STATE_WRITING;

public abstract class ChatInputActivity
        extends BaseActivity
        implements AdapterView.OnItemClickListener, AttachmentController.OnAttachmentLoadedListener {
    private final static String TAG = ChatInputActivity.class.getSimpleName();
    private static final int SMALL_DEVICE_DPI_LIMIT = 250;
    private static final int POSITION_FAB_TIME = 0;
    private static final int POSITION_FAB_BOMB = 1;
    private static final int POSITION_FAB_PRIO = 2;
    private final HashMap<String, String> mDownloadProgress = new HashMap<>();

    static final String EXTRA_CITATED_MSG_MODEL_ID = "ChatInputActivity.ExtraCitatedMsgId";

    ChatInputFragment mChatInputFragment;
    SelfdestructionFragment mSelfdestructionFragment;
    // Emojis
    EmojiPickerFragment mEmojiconsFragment;
    // Das aktuell eingeblendete Frame (Emoji oder Selbstzerstoerung)
    Fragment mCurrentFragment;
    Animation mAnimationSlideInSixLines;
    Animation mAnimationSlideOutSixLines;
    int mChatInputContainerId;
    int mFragmentContainerId;
    boolean mIsPriority;
    boolean mShowSimpleFab;
    OnDeleteTimedMessageListener mOnDeleteTimedMessageListener;
    SendActionContainer mActionContainer;
    Animation mAnimationSlideInOneLine;
    Animation mAnimationSlideOutOneLine;
    Animation mAnimationSlideInTwoLines;
    Animation mAnimationSlideOutTwoLines;
    Animation mAnimationSlideInThreeLines;
    Animation mAnimationSlideOutThreeLines;
    Animation mAnimationSlideInSevenLines;
    Animation mAnimationSlideOutSevenLines;
    String mTargetGuid;
    String mPublicKeyXML;
    String mTitle;
    Chat mChat;
    MessageController mMessageController;

    OnSendMessageListener mOnSendMessageListener;

    ChatAdapter mChatAdapter;
    AccountController mAccountController;
    ContactController mContactController;
    AVChatController avChatController;
    PreferencesController mPreferencesController;
    BaseChatItemVO mCitatedChatItem;
    BaseChatItemVO mMarkedChatItem;
    View mClickedView;
    int mClickedIndex;
    Uri mShareFileUri;
    SpeedDialView mSpeedDialView;
    private ContactController.OnlineStateContainer mLastKnownonlineStateContainer;
    private String mFileIntentAction;
    private Uri mVCardFileUri;
    private FileUtil mFileUtil;
    private SpeedDialActionItem mFabTimeItem;
    private SpeedDialActionItem mFabTimeActiveItem;
    private SpeedDialActionItem mFabTimeEnabledItem;
    private SpeedDialActionItem mFabBombItem;
    private SpeedDialActionItem mFabBombActiveItem;
    private SpeedDialActionItem mFabBombEnabledItem;
    private SpeedDialActionItem mFabPrioItem;
    private SpeedDialActionItem mFabPrioEnabledItem;
    @Inject
    public Router router;

    protected abstract ChatController getChatController();

    String getChatTitle() {
        return "";
    }

    public BaseChatItemVO getCitatedChatItem() {
        return mCitatedChatItem;
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        initFabMenu();
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);

        if (requestCode == RouterConstants.SEND_VCARD_RESULT_CODE && mVCardFileUri != null) {
            //Rechte wieder entfernen nach V-CARD Senden
            getFileUtil().deleteFileByUriAndRevokePermission(mVCardFileUri);
        }
    }

    private void initFabMenu() {

        final SimsMeApplication simsMeApplication = getSimsMeApplication();
        final ColorUtil colorUtil = ColorUtil.getInstance();
        final int lowColor = colorUtil.getLowColor(simsMeApplication);
        final int lowContrastColor = colorUtil.getLowContrastColor(simsMeApplication);

        final int fabColor;
        final int fabIconColor;
        if (RuntimeConfig.isBAMandant()) {
            fabColor = colorUtil.getMainContrastColor(simsMeApplication);
            fabIconColor = colorUtil.getMainColor(simsMeApplication);
        } else {
            fabColor = colorUtil.getFabColor(simsMeApplication);
            fabIconColor = colorUtil.getFabIconColor(simsMeApplication);
        }

        mSpeedDialView = findViewById(R.id.chat_input_speed_dial_fab);

        if (mSpeedDialView == null) {
            return;
        }
        mSpeedDialView.setMainFabOpenedBackgroundColor(fabColor);
        mSpeedDialView.setMainFabClosedBackgroundColor(fabColor);
        final FloatingActionButton mainFab = mSpeedDialView.getMainFab();
        mainFab.getDrawable().setColorFilter(fabIconColor, PorterDuff.Mode.SRC_ATOP);
        mainFab.setRippleColor(fabColor);

        mSpeedDialView.setOnChangeListener(new SpeedDialView.OnChangeListener() {
            @Override
            public boolean onMainActionSelected() {
                return false;
            }

            @Override
            public void onToggleChanged(boolean b) {
                mainFab.getDrawable().setColorFilter(fabIconColor, PorterDuff.Mode.SRC_ATOP);
                mChatInputFragment.onMainFabClick(b);
            }
        });

        mFabTimeItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_timed, R.drawable.timed)
                .setFabBackgroundColor(fabColor)
                .setFabImageTintColor(fabIconColor)
                .create();
        mFabTimeActiveItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_timed_active, R.drawable.timed_active)
                .setFabBackgroundColor(lowColor)
                .setFabImageTintColor(lowContrastColor)
                .create();
        mFabTimeEnabledItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_timed_enabled, R.drawable.timed_set)
                .setFabBackgroundColor(lowColor)
                .setFabImageTintColor(lowContrastColor)
                .create();

        mFabBombItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_bomb, R.drawable.sfz)
                .setFabBackgroundColor(fabColor)
                .setFabImageTintColor(fabIconColor)
                .create();
        mFabBombActiveItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_bomb_active, R.drawable.sfz_active)
                .setFabBackgroundColor(lowColor)
                .setFabImageTintColor(lowContrastColor)
                .create();
        mFabBombEnabledItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_bomb_enabled, R.drawable.sfz_set)
                .setFabBackgroundColor(lowColor)
                .setFabImageTintColor(lowContrastColor)
                .create();

        mFabPrioItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_prio, R.drawable.priority)
                .setFabBackgroundColor(fabColor)
                .setFabImageTintColor(fabIconColor)
                .create();
        mFabPrioEnabledItem = new SpeedDialActionItem.Builder(R.id.fab_chat_input_prio_enabled, R.drawable.priority_set)
                .setFabBackgroundColor(lowColor)
                .setFabImageTintColor(lowContrastColor)
                .create();

        resetChatInputFabButton();

        hideChatInputFabButton();

        SpeedDialOverlayLayout overlayLayout = findViewById(R.id.chat_input_speed_dial_overlay);

        mSpeedDialView.setOverlayLayout(overlayLayout);
        mSpeedDialView.setOnActionSelectedListener(new SpeedDialView.OnActionSelectedListener() {
            @Override
            public boolean onActionSelected(SpeedDialActionItem speedDialActionItem) {
                updateFabItems(speedDialActionItem.getId());
                return true;
            }
        });
    }

    public void resetChatInputFabButton() {
        resetChatInputFabButton(false);
    }

    /**
     * @param forceSimpleFab
     */
    public void resetChatInputFabButton(boolean forceSimpleFab) {
        if (mChatInputFragment != null && !forceSimpleFab) {
            forceSimpleFab = mChatInputFragment.isCommenting();
        }

        if (mSpeedDialView != null) {
            if (mShowSimpleFab && !forceSimpleFab) {
                mSpeedDialView.setVisibility(View.GONE);
            } else {
                for (final SpeedDialActionItem speedDialActionItem : mSpeedDialView.getActionItems()) {
                    mSpeedDialView.removeActionItem(speedDialActionItem);
                }
                if (!mShowSimpleFab && !forceSimpleFab) {
                    mSpeedDialView.addActionItem(mFabTimeItem, POSITION_FAB_TIME);
                    mSpeedDialView.addActionItem(mFabBombItem, POSITION_FAB_BOMB);

                    mSpeedDialView.addActionItem(mFabPrioItem, POSITION_FAB_PRIO);
                } else {
                    mSpeedDialView.addActionItem(mFabPrioItem, 0);
                }

                mSpeedDialView.close();
            }
        }
        // TODO pruefen, ob das hier stehen sollte, hat mit dem context "FAB" nix zu tun
        mIsPriority = false;
    }

    public void showChatInputFabButton() {
        if (mSpeedDialView != null) {
            mSpeedDialView.setVisibility(View.VISIBLE);
        }
    }

    public void hideChatInputFabButton() {
        if (mSpeedDialView != null) {
            mSpeedDialView.setVisibility(View.INVISIBLE);
        }
    }

    public void updateFabItems(int pressedItemId) {
        if (mSpeedDialView == null) {
            return;
        }

        if (mChatInputFragment.isCommenting()) {
            switch (pressedItemId) {
                case R.id.fab_chat_input_prio: {
                    mIsPriority = true;
                    mChatInputFragment.handlePriorityButtonClick();
                    mSpeedDialView.replaceActionItem(mFabPrioEnabledItem, 0);

                    break;
                }
                case R.id.fab_chat_input_prio_enabled: {
                    mIsPriority = false;
                    mChatInputFragment.handlePriorityButtonClick();
                    mSpeedDialView.replaceActionItem(mFabPrioItem, 0);
                    break;
                }
                default: {
                    return;
                }
            }
            return;
        }
        ArrayList<SpeedDialActionItem> items = mSpeedDialView.getActionItems();

        SpeedDialActionItem currentTimeItem = null;
        SpeedDialActionItem currentBombItem = null;
        SpeedDialActionItem currentPrioItem;

        if (mShowSimpleFab) {
            currentPrioItem = items.get(0);
        } else {
            if (items.size() > POSITION_FAB_TIME)
                currentTimeItem = items.get(POSITION_FAB_TIME);

            if (items.size() > POSITION_FAB_BOMB)
                currentBombItem = items.get(POSITION_FAB_BOMB);

            if (items.size() > POSITION_FAB_PRIO) {
                currentPrioItem = items.get(POSITION_FAB_PRIO);
            } else {
                currentPrioItem = null;
            }
        }

        switch (pressedItemId) {
            case R.id.fab_chat_input_timed: {
                mChatInputFragment.handleTimerClick();
                if (currentTimeItem != null)
                    mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeActiveItem);

                if (mChatInputFragment.getDestructionEnabled() && currentBombItem != null) {
                    mSpeedDialView.replaceActionItem(currentBombItem, mFabBombEnabledItem);
                }
                break;
            }
            case R.id.fab_chat_input_timed_enabled: {
                final boolean timerEnabled = mChatInputFragment.handleTimerClick();
                if (currentTimeItem != null) {
                    if (timerEnabled) {
                        mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeActiveItem);
                    } else {
                        mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeItem);
                    }
                }
                break;
            }
            case R.id.fab_chat_input_timed_active: {
                mChatInputFragment.handleTimerClick();
                if (currentTimeItem != null)
                    mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeItem);

                if (mChatInputFragment.getDestructionEnabled() && currentBombItem != null) {
                    mSpeedDialView.replaceActionItem(currentBombItem, mFabBombActiveItem);
                }
                break;
            }
            case R.id.fab_chat_input_bomb: {
                mChatInputFragment.handleDestructionClick();
                if (currentBombItem != null)
                    mSpeedDialView.replaceActionItem(currentBombItem, mFabBombActiveItem);

                if (mChatInputFragment.getTimerEnabled() && currentTimeItem != null) {
                    mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeEnabledItem);
                }

                break;
            }
            case R.id.fab_chat_input_bomb_enabled: {
                if (currentBombItem == null) break;

                final boolean destructionEnabled = mChatInputFragment.handleDestructionClick();
                if (destructionEnabled) {
                    mSpeedDialView.replaceActionItem(currentBombItem, mFabBombActiveItem);
                } else {
                    mSpeedDialView.replaceActionItem(currentBombItem, mFabBombItem);
                }
                break;
            }
            case R.id.fab_chat_input_bomb_active: {
                mChatInputFragment.handleDestructionClick();
                if (currentBombItem != null)
                    mSpeedDialView.replaceActionItem(currentBombItem, mFabBombItem);

                if (mChatInputFragment.getTimerEnabled() && currentTimeItem != null) {
                    mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeActiveItem);
                }
                break;
            }
            case R.id.fab_chat_input_prio: {
                mIsPriority = true;
                mChatInputFragment.handlePriorityButtonClick();
                if (currentPrioItem != null)
                    mSpeedDialView.replaceActionItem(currentPrioItem, mFabPrioEnabledItem);

                break;
            }
            case R.id.fab_chat_input_prio_enabled: {
                mIsPriority = false;
                mChatInputFragment.handlePriorityButtonClick();
                if (currentPrioItem != null)
                    mSpeedDialView.replaceActionItem(currentPrioItem, mFabPrioItem);
                break;
            }
            default: {
                // update wird von anderer stelle ausgeloest -> buttons aktualisieren
                if (currentBombItem != null) {
                    if (mChatInputFragment.getDestructionEnabled()) {
                        mSpeedDialView.replaceActionItem(currentBombItem, mFabBombEnabledItem);
                    } else {
                        mSpeedDialView.replaceActionItem(currentBombItem, mFabBombItem);
                    }
                }
                if (currentTimeItem != null) {
                    if (mChatInputFragment.getTimerEnabled()) {
                        mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeEnabledItem);
                    } else {
                        mSpeedDialView.replaceActionItem(currentTimeItem, mFabTimeItem);
                    }
                }
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
        try {

            hideToolbarOptions();
            mClickedView = view;

            if ((mBottomSheetOpen)
                    || ((mChatInputFragment != null) && mChatInputFragment.isRecording())) {
                return;
            }

            final int offset = (hasHeaderView()) ? 1 : 0;
            final int adapterPosition = position - offset;
            mClickedIndex = adapterPosition;

            if (adapterPosition >= mChatAdapter.getCount()) {
                return;
            }

            final BaseChatItemVO baseChatItemVO = mChatAdapter.getItem(adapterPosition);

            if (baseChatItemVO == null) {
                return;
            }

            if (baseChatItemVO.hasSendError) {
                prepareResendMenu(baseChatItemVO);

                return;
            }

            if (!baseChatItemVO.isValid || (!baseChatItemVO.isSendConfirmed() && StringUtil.isEqual(baseChatItemVO.getFromGuid(), mAccountController.getAccount().getAccountGuid()))) {
                return;
            }

            final String messageGuid = baseChatItemVO.getMessageGuid() != null ? baseChatItemVO.getMessageGuid() : Long.toString(baseChatItemVO.messageId);

            AttachmentController.OnAttachmentLoadedListener onAttachmentLoadWrapper = new AttachmentController.OnAttachmentLoadedListener() {
                final String mMessageGuid = messageGuid;

                @Override
                public void onBitmapLoaded(File file,
                                           DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    ChatInputActivity.this.onBitmapLoaded(file, decryptedMsg);
                }

                @Override
                public void onVideoLoaded(File videoFile,
                                          DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    ChatInputActivity.this.onVideoLoaded(videoFile, decryptedMsg);
                }

                @Override
                public void onAudioLoaded(File audioFile,
                                          DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    ChatInputActivity.this.onAudioLoaded(audioFile, decryptedMsg);
                }

                @Override
                public void onFileLoaded(File dataFile, DecryptedMessage decryptedMsg) {
                    finishDownloading(mMessageGuid);
                    ChatInputActivity.this.onFileLoaded(dataFile, decryptedMsg);
                }

                @Override
                public void onHasNoAttachment(final String message) {
                    ChatInputActivity.this.onHasNoAttachment(message);
                }

                @Override
                public void onHasAttachment(final boolean finishedWork) {
                    markDownloading(mMessageGuid);
                    ChatInputActivity.this.onHasAttachment(finishedWork);
                }

                @Override
                public void onLoadedFailed(String message) {
                    finishDownloading(mMessageGuid);
                    ChatInputActivity.this.onLoadedFailed(message);
                }
            };

            if (baseChatItemVO instanceof LocationChatItemVO) {
                LogUtil.d(TAG, "onItemClick: LocationChatItemVO");
                requestPermission(PermissionUtil.PERMISSION_FOR_LOCATION, R.string.permission_rationale_location,
                        new PermissionUtil.PermissionResultCallback() {
                            @Override
                            public void permissionResult(int permission,
                                                         boolean permissionGranted) {
                                if ((permission == PermissionUtil.PERMISSION_FOR_LOCATION) && permissionGranted) {
                                    LocationChatItemVO locationChatItemVO = (LocationChatItemVO) baseChatItemVO;

                                    Intent intent = new Intent(ChatInputActivity.this, LocationActivity.class);

                                    intent.putExtra(LocationActivity.EXTRA_MODE, LocationActivity.MODE_SHOW_LOCATION);
                                    intent.putExtra(LocationActivity.EXTRA_LONGITUDE, locationChatItemVO.longitude);
                                    intent.putExtra(LocationActivity.EXTRA_LATITUDE, locationChatItemVO.latitude);
                                    startActivity(intent);
                                }
                            }
                        });
            } else if (baseChatItemVO instanceof AVChatItemVO) {
                // User clicks on AVC message in the chat. The received message stays active for
                // 7200 seconds (default) to allow answering a call and restart, if kicked out.
                // avChatController is null if no AVC is available
                LogUtil.d(TAG, "onItemClick: AVChatItemVO");
                Message message = getChatController().findMessageById(baseChatItemVO.messageId);
                if (message == null) {
                    return;
                }

                avChatController = getSimsMeApplication().getAVChatController();
                if (avChatController != null) {

                    // Only continue if no avc currently active
                    if (!avChatController.isCallActive()) {
                        // KS: Ignore messages older than AVC_CALL_TIMEOUT
                        long dateSent = message.getDateSend() == null ? 0 : message.getDateSend();
                        if (dateSent + AVChatController.getCallTimeoutMillis() < System.currentTimeMillis()) {
                            LogUtil.i(TAG, "Cannot join expired AVC!");
                            return;
                        }

                        LogUtil.i(TAG, "Join AVC at " + ((AVChatItemVO) baseChatItemVO).room);
                        String[] roomInfo = AVChatController.deserializeRoomInfoMessageString(((AVChatItemVO) baseChatItemVO).room);
                        avChatController.setRoomInfo(roomInfo);

                        String myName = "John Doe (unknown)";
                        try {
                            myName = mContactController.getOwnContact().getNameFromNameAttributes()
                                    + " (" + mContactController.getOwnContact().getSimsmeId() + ")";
                        } catch (LocalizedException e) {
                            e.printStackTrace();
                        }
                        avChatController.setMyName(myName);
                        avChatController.setConferenceTopic(myName);

                        // Tell others that we answer the call ...
                        //avChatController.sendCallAcceptMessage(mTargetGuid, mOnSendMessageListener);
                        avChatController.sendCallAcceptMessage(mTargetGuid, null);

                        // KS: Now start audio-video-call.
                        // TODO: Users may choose between call types
                        avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_ONLY);
                        avChatController.startAVCall(this);
                    } else {
                        LogUtil.i(TAG, "Cannot join AVC - CallStatus is " + avChatController.getCallStatus());
                    }
                }
            } else if (baseChatItemVO instanceof VCardChatItemVO) {
                LogUtil.d(TAG, "onItemClick: VCardChatItemVO");
                if (mPreferencesController.isSendContactsDisabled()) {
                    Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_contact_access_not_allowed), Toast.LENGTH_SHORT).show();
                } else {
                    final VCardChatItemVO vCardChatItemVO = (VCardChatItemVO) baseChatItemVO;

                    if (vCardChatItemVO.accountGuid == null || vCardChatItemVO.accountId == null) {
                        //DialogBuilderUtil.buildErrorDialog(ChatInputActivity.this, getResources().getString(R.string.update_private_index_failed)).show();
                        displayVCard(vCardChatItemVO.vCard);
                    } else {
                        final Contact contactByGuid = mContactController.getContactByGuid(vCardChatItemVO.accountGuid);
                        if (contactByGuid != null) {
                            final String entryClassName = contactByGuid.getClassEntryName();

                            //Prüfung ob es sich um den eigenen Account handelt
                            String ownProfile = mAccountController.getAccount().getAccountGuid();
                            if (contactByGuid.getAccountGuid().equals(ownProfile)) {
                                final Intent intent = new Intent(ChatInputActivity.this, ProfileActivity.class);
                                startActivity(intent);
                                return;
                            }
                            //Prüfen auf Company-Kontakt
                            else if (StringUtil.isEqual(entryClassName, Contact.CLASS_DOMAIN_ENTRY) || StringUtil.isEqual(entryClassName, Contact.CLASS_COMPANY_ENTRY)) {
                                final Intent intent = new Intent(ChatInputActivity.this, RuntimeConfig.getClassUtil().getCompanyContactDetailActivity());
                                intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, contactByGuid.getAccountGuid());
                                startActivity(intent);
                                return;
                            }

                            final Intent intent = new Intent(ChatInputActivity.this, ContactDetailActivity.class);
                            intent.putExtra(ContactDetailActivity.EXTRA_CONTACT, contactByGuid);

                            final HashMap<String, String> contactDetails = new HashMap<>();
                            putContactDetailsTosMap(vCardChatItemVO, contactDetails);
                            intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_MAP, contactDetails);
                            startActivity(intent);
                        } else {
                            showIdleDialog();

                            final ContactController.OnLoadPublicKeyListener listener = new ContactController.OnLoadPublicKeyListener() {

                                @Override
                                public void onLoadPublicKeyComplete(final Contact contact) {
                                    dismissIdleDialog();
                                    final HashMap<String, String> contactDetails = new HashMap<>();

                                    final Intent intent = new Intent(ChatInputActivity.this, ContactDetailActivity.class);
                                    intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, vCardChatItemVO.accountGuid);
                                    intent.putExtra(ContactDetailActivity.EXTRA_MODE, ContactDetailActivity.MODE_CREATE);
                                    contactDetails.put(JsonConstants.GUID, vCardChatItemVO.accountGuid);
                                    contactDetails.put(JsonConstants.ACCOUNT_ID, vCardChatItemVO.accountId);
                                    contactDetails.put(JsonConstants.PUBLIC_KEY, contact.getPublicKey());
                                    contactDetails.put(JsonConstants.MANDANT, contact.getMandant());

                                    putContactDetailsTosMap(vCardChatItemVO, contactDetails);

                                    intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_MAP, contactDetails);

                                    startActivity(intent);
                                }

                                @Override
                                public void onLoadPublicKeyError(final String contactguid) {
                                    dismissIdleDialog();
                                }
                            };

                            mContactController.getAccountInfoForSentContact(vCardChatItemVO.accountGuid, listener);
                        }
                    }
                }
            } else if (baseChatItemVO instanceof SelfDestructionChatItemVO) {
                LogUtil.d(TAG, "onItemClick: SelfDestructionChatItemVO");
                Message message = getChatController().findMessageById(baseChatItemVO.messageId);

                if (message == null) {
                    return;
                }

                MessageDestructionParams destructionParams = ((SelfDestructionChatItemVO) baseChatItemVO).destructionParams;

                if (!message.getIsSentMessage() && (destructionParams.countdown == null)
                        && Calendar.getInstance().getTime().after(destructionParams.date)) {
                    getChatController().deleteMessage(message.getGuid(), true, mOnDeleteTimedMessageListener);
                    final String errorMessage = getResources().getString(R.string.chats_showText_destroyedLabel);
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                } else {
                    SelfDestructionChatItemVO selfDestructionChatItemVO = (SelfDestructionChatItemVO) baseChatItemVO;

                    if (selfDestructionChatItemVO.destructionType == SelfDestructionChatItemVO.TYPE_TEXT) {
                        Intent intent = new Intent(this, DestructionActivity.class);

                        if (!message.getIsSentMessage()) {
                            intent.putExtra(DestructionActivity.EXTRA_DESTRUCTION_PARAMS, destructionParams);
                        }
                        intent.putExtra(DestructionActivity.EXTRA_MESSAGE, selfDestructionChatItemVO.text);
                        intent.putExtra(DestructionActivity.EXTRA_MESSAGE_GUID, message.getGuid());
                        intent.putExtra(DestructionActivity.EXTRA_MESSAGE_TYPE, message.getType());
                        intent.putExtra(DestructionActivity.EXTRA_CHAT_ITEM, selfDestructionChatItemVO);

                        startActivity(intent);
                    } else {
                        //showIdleDialog(R.string.progress_dialog_load_attachment);
                        if (!isDownloading(messageGuid)) {

                            final ProgressBar progressBar = view.findViewById(R.id.progressBar_download);
                            if (progressBar != null) {
                                HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(adapterPosition, message.getIsPriority());

                                getChatController().getAttachment(selfDestructionChatItemVO, onAttachmentLoadWrapper, false, onConnectionDataUpdatedListener);
                                view.setTag("downloading");
                            } else {
                                getChatController().getAttachment(selfDestructionChatItemVO, onAttachmentLoadWrapper, false, null);
                            }
                        }
                    }
                }
            } else if (baseChatItemVO instanceof FileChatItemVO) {
                // Other filetypes
                LogUtil.d(TAG, "onItemClick: FileChatItemVO");
                FileChatItemVO imageChatItemVO = (FileChatItemVO) baseChatItemVO;

                if (!mBottomSheetMoving) {
                    if (mPreferencesController.isOpenInAllowed()) {
                        int bottomSheetLayoutResourceID = R.layout.dialog_chat_context_menu_open_share_layout;
                        mAnimationSlideIn = mAnimationSlideInTwoLines;
                        mAnimationSlideOut = mAnimationSlideOutTwoLines;
                        mMarkedChatItem = baseChatItemVO;
                        openBottomSheet(bottomSheetLayoutResourceID, R.id.chat_bottom_sheet_container);

                    } else {
                        DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.error_mdm_media_access_not_allowed)).show();
                    }
                }
            } else if (baseChatItemVO instanceof AttachmentChatItemVO) {
                // Image and Video
                LogUtil.d(TAG, "onItemClick: AttachmentChatItemVO");
                AttachmentChatItemVO imageChatItemVO = (AttachmentChatItemVO) baseChatItemVO;

                if (!isDownloading(messageGuid)) {
                    final ProgressBar progressBar = view.findViewById(R.id.progressBar_download);
                    if (progressBar != null) {
                        HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(adapterPosition, imageChatItemVO.isPriority);

                        getChatController().getAttachment(imageChatItemVO, onAttachmentLoadWrapper, false, onConnectionDataUpdatedListener);
                        view.setTag("downloading");
                    } else {
                        getChatController().getAttachment(imageChatItemVO, onAttachmentLoadWrapper, false, null);
                    }
                }
            } else if (baseChatItemVO instanceof VoiceChatItemVO) {
                LogUtil.d(TAG, "onItemClick: VoiceChatItemVO");
                VoiceChatItemVO voiceChatItemVO = (VoiceChatItemVO) baseChatItemVO;

                if (!isDownloading(messageGuid)) {
                    final ProgressBar progressBar = view.findViewById(R.id.progressBar_download);
                    if (progressBar != null) {
                        HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(adapterPosition, baseChatItemVO.isPriority);
                        getChatController().getAttachment(voiceChatItemVO, onAttachmentLoadWrapper, false, onConnectionDataUpdatedListener);
                        view.setTag("downloading");
                    } else {
                        getChatController().getAttachment(voiceChatItemVO, onAttachmentLoadWrapper, false, null);
                    }
                }
            } else {
                LogUtil.w(TAG, "onItemClick: baseChatItemVO instanceof ???");
            }
        } catch (final LocalizedException le) {
            LogUtil.w(TAG, le.getMessage());
        }
    }

    private void putContactDetailsTosMap(final VCardChatItemVO vCardChatItemVO,
                                         final HashMap<String, String> contactDetails) {
        if (vCardChatItemVO.vCard.getEmails().size() > 0) {
            final String email = vCardChatItemVO.vCard.getEmails().get(0).getValue();
            if (!StringUtil.isNullOrEmpty(email)) {
                contactDetails.put(JsonConstants.EMAIL, email);
            }
        }

        if (vCardChatItemVO.vCard.getTelephoneNumbers().size() > 0) {
            final String phone = vCardChatItemVO.vCard.getTelephoneNumbers().get(0).getText();
            if (!StringUtil.isNullOrEmpty(phone)) {
                contactDetails.put(JsonConstants.PHONE, phone);
            }
        }

        if (vCardChatItemVO.vCard.getNicknames().size() > 0) {
            final List<String> nickNames = vCardChatItemVO.vCard.getNicknames().get(0).getValues();

            if (nickNames != null && nickNames.size() != 0 && !"null".equals(nickNames.get(0))) {
                contactDetails.put(JsonConstants.NICKNAME, nickNames.get(0).trim());
            }
        }

        if (vCardChatItemVO.vCard.getFormattedName() != null && vCardChatItemVO.vCard.getFormattedName().getValue() != null) {

            final String value = vCardChatItemVO.vCard.getFormattedName().getValue().trim();

            if (!StringUtil.isNullOrEmpty(value) && !" ".equals(value)) {
                final String[] split = value.trim().split(" ");
                final String firstName = split[0];
                contactDetails.put(JsonConstants.FIRSTNAME, firstName);

                if (split.length > 1) {
                    final String lastName = split[split.length - 1];
                    contactDetails.put(JsonConstants.LASTNAME, lastName);
                }
            }
        }
    }

    /**
     * @param vCard vCard die angezeigt werden soll
     */
    private void displayVCard(final VCard vCard) {
        final Intent intent = new Intent();
        try {

            final FileUtil fileUtil = getFileUtil();

            final File vCardFile = fileUtil.createTmpFileForExternalUsage();

            vCard.write(vCardFile);

            mVCardFileUri = fileUtil.getUriForExternalUsageFromFile(vCardFile);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(mVCardFileUri, "text/x-vcard");
            //intent.putExtra(Intent.EXTRA_STREAM, fileUtil.getUriForExternalUsageFromFile(vCardFile));
            //intent.setType("text/x-vcard");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                router.startExternalActivityForResult(intent, RouterConstants.SEND_VCARD_RESULT_CODE);
            } catch (final ActivityNotFoundException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                intent.setDataAndType(fileUtil.getUriForExternalUsageFromFile(vCardFile), "text/vcard");
                router.startExternalActivityForResult(intent, RouterConstants.SEND_VCARD_RESULT_CODE);
            }
        } catch (final IOException | ActivityNotFoundException | LocalizedException le) // ActivityNotFoundException
        {
            LogUtil.w(TAG, le.getMessage());
            DialogBuilderUtil.buildErrorDialog(this, getString(R.string.content_description_contact_details_add_contact_error)).show();
        }
    }

    @Override
    public void onHasNoAttachment(final String message) {
        dismissIdleDialog();
        if ((message != null) && !(message.equals(""))) {
            DialogBuilderUtil.buildErrorDialog(ChatInputActivity.this, message).show();
        }
    }

    @Override
    protected void onPauseActivity() {
        super.onPauseActivity();

        if (mContactController != null) {
            mContactController.stopGetOnlineStateTask();
        }
    }

    @Override
    public void onLoadedFailed(String message) {
        dismissIdleDialog();
        if (isActivityInForeground) {
            DialogBuilderUtil.buildErrorDialog(this, message).show();
        }
        mActionContainer = null;
    }

    @Override
    public void onHasAttachment(boolean finishedWork) {
    }

    @Override
    public void onBitmapLoaded(File bitmapFile,
                               DecryptedMessage decryptedMsg) {
        Intent intent;
        MessageDestructionParams params = decryptedMsg.getMessageDestructionParams();

        if (mActionContainer != null) {
            intent = new Intent(this, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.class);

            ArrayList<String> uris = new ArrayList<>();

            uris.add("file://" + bitmapFile.getAbsolutePath());

            intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS, uris);

            final String attDesc = decryptedMsg.getAttachmentDescription();
            if (!StringUtil.isNullOrEmpty(attDesc)) {
                ArrayList<String> texts = new ArrayList<>();
                texts.add(attDesc);
                intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TEXTS, texts);
            }

            intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_PREVIEW_ACTION, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.SELECT_PHOTOS_ACTION);
            intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_PREVIEW_TITLE, getChatTitle());
            dismissIdleDialog();
            startActivityForResult(intent, RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE);

            mActionContainer = null;
        } else {
            if (ChatInputActivity.this.isActivityInForeground()) {
                if (decryptedMsg.getMessage().getIsSentMessage()) {
                    params = null;
                }
                if (params != null) {
                    intent = new Intent(this, DestructionActivity.class);
                    intent.putExtra(DestructionActivity.EXTRA_BITMAP_URI, bitmapFile.toURI().toASCIIString());
                    intent.putExtra(DestructionActivity.EXTRA_DESTRUCTION_PARAMS, params);
                    intent.putExtra(DestructionActivity.EXTRA_MESSAGE_GUID, decryptedMsg.getMessage().getGuid());
                    intent.putExtra(DestructionActivity.EXTRA_MESSAGE_TYPE, decryptedMsg.getMessage().getType());

                    final String attDesc = decryptedMsg.getAttachmentDescription();
                    if (!StringUtil.isNullOrEmpty(attDesc)) {
                        intent.putExtra(DestructionActivity.EXTRA_ATTACHMENT_DESCRIPTION, attDesc);
                    }
                } else {
                    intent = new Intent(this, ViewAttachmentActivity.class);
                    intent.putExtra(ViewAttachmentActivity.EXTRA_BITMAP_URI, bitmapFile.toURI().toASCIIString());
                    intent.putExtra(ViewAttachmentActivity.EXTRA_ATTACHMENT_GUID, decryptedMsg.getMessage().getAttachment());

                    final String attDesc = decryptedMsg.getAttachmentDescription();
                    if (!StringUtil.isNullOrEmpty(attDesc)) {
                        intent.putExtra(ViewAttachmentActivity.EXTRA_ATTACHMENT_DESCRIPTION, attDesc);
                    }
                }
                startActivity(intent);
            }
            dismissIdleDialog();
        }
    }

    @Override
    public void onVideoLoaded(File videoFile,
                              DecryptedMessage decryptedMsg) {
        Intent intent;
        MessageDestructionParams params = decryptedMsg.getMessageDestructionParams();

        if (mActionContainer != null) {
            intent = new Intent(this, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.class);

            ArrayList<String> uris = new ArrayList<>();
            uris.add("file://" + videoFile.getAbsolutePath());
            intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS, uris);

            final String attDesc = decryptedMsg.getAttachmentDescription();
            if (!StringUtil.isNullOrEmpty(attDesc)) {
                ArrayList<String> texts = new ArrayList<>();
                texts.add(attDesc);
                intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TEXTS, texts);
            }

            intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_PREVIEW_ACTION, PreviewActivity.SELECT_VIDEOS_ACTION);

            dismissIdleDialog();
            startActivityForResult(intent, RouterConstants.GET_VIDEO_WITH_DESTRUCTION_RESULT_CODE);

            mActionContainer = null;
        } else {
            if (ChatInputActivity.this.isActivityInForeground()) {
                if (decryptedMsg.getMessage().getIsSentMessage()) {
                    params = null;
                }
                if (params != null) {
                    intent = new Intent(this, DestructionActivity.class);
                    intent.putExtra(DestructionActivity.EXTRA_VIDEO_URI, videoFile.toURI().toASCIIString());
                    intent.putExtra(DestructionActivity.EXTRA_DESTRUCTION_PARAMS, params);
                    intent.putExtra(DestructionActivity.EXTRA_MESSAGE_GUID, decryptedMsg.getMessage().getGuid());
                    intent.putExtra(DestructionActivity.EXTRA_MESSAGE_TYPE, decryptedMsg.getMessage().getType());

                    final String attDesc = decryptedMsg.getAttachmentDescription();
                    if (!StringUtil.isNullOrEmpty(attDesc)) {
                        intent.putExtra(DestructionActivity.EXTRA_ATTACHMENT_DESCRIPTION, attDesc);
                    }
                } else {

                    intent = new Intent(this, ViewAttachmentActivity.class);
                    intent.putExtra(ViewAttachmentActivity.EXTRA_VIDEO_URI, videoFile.toURI().toASCIIString());
                    intent.putExtra(ViewAttachmentActivity.EXTRA_ATTACHMENT_GUID, decryptedMsg.getMessage().getAttachment());

                    final String attDesc = decryptedMsg.getAttachmentDescription();
                    if (!StringUtil.isNullOrEmpty(attDesc)) {
                        intent.putExtra(ViewAttachmentActivity.EXTRA_ATTACHMENT_DESCRIPTION, attDesc);
                    }
                }
                startActivity(intent);
            }
            dismissIdleDialog();
        }
    }

    @Override
    public void onAudioLoaded(File voiceFile,
                              DecryptedMessage decryptedMsg) {
        Intent intent;
        MessageDestructionParams params = decryptedMsg.getMessageDestructionParams();

        if (decryptedMsg.getMessage().getIsSentMessage()) {
            params = null;
        }
        if (params != null) {
            intent = new Intent(this, DestructionActivity.class);
            intent.putExtra(DestructionActivity.EXTRA_VOICE_URI, voiceFile.toURI().toASCIIString());
            intent.putExtra(DestructionActivity.EXTRA_DESTRUCTION_PARAMS, params);
            intent.putExtra(DestructionActivity.EXTRA_MESSAGE_GUID, decryptedMsg.getMessage().getGuid());
            intent.putExtra(DestructionActivity.EXTRA_MESSAGE_TYPE, decryptedMsg.getMessage().getType());
            dismissIdleDialog();
            startActivity(intent);
        } else {
            if (ChatInputActivity.this.isActivityInForeground()) {
                intent = new Intent(this, ViewAttachmentActivity.class);
                intent.putExtra(ViewAttachmentActivity.EXTRA_VOICE_URI, voiceFile.toURI().toASCIIString());
                intent.putExtra(ViewAttachmentActivity.EXTRA_ATTACHMENT_GUID, decryptedMsg.getMessage().getAttachment());
                startActivity(intent);
            }
            dismissIdleDialog();
        }
        mActionContainer = null;
    }

    @Override
    public void onFileLoaded(File dataFile, DecryptedMessage decryptedMsg) {
        dismissIdleDialog();

        if(dataFile == null || decryptedMsg == null) {
            LogUtil.e(TAG, "onFileLoaded: Got datafile = " + dataFile + " and decryptedMsg = " + decryptedMsg);
            return;
        }

        // KS: Ensure that we have a mime type!
        // This is not nice but effective.
        String tmpMimeType = decryptedMsg.getFileMimetype();
        if(StringUtil.isNullOrEmpty(tmpMimeType)) {
            tmpMimeType = MimeUtil.getMimeTypeFromPath(dataFile.getPath());
            if(StringUtil.isNullOrEmpty(tmpMimeType)) {
                //tmpMimeType = "application/octet-stream";
                tmpMimeType = "text/plain";
            }
        }
        final String mimeType = tmpMimeType;

        if (mActionContainer != null) {
            //Weiterleiten UseCase

            final Uri fileUri = Uri.parse("file://" + dataFile.getAbsolutePath());
            final String filename = decryptedMsg.getFilename();
            String fileSizeString = decryptedMsg.getFileSize();
            long filesize = 0;

            try {
                if (fileSizeString != null) {
                    filesize = Long.parseLong(fileSizeString);
                }
            } catch (final NumberFormatException e) {
                LogUtil.w(TAG, e.getMessage(), e);
            }

            DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        checkChat();
                        getChatController().sendFile(ChatInputActivity.this, mTargetGuid, mPublicKeyXML,
                                fileUri, false, filename, mimeType, mOnSendMessageListener, buildCitationFromSelectedChatItem());
                        closeCommentView();
                        setResult(RESULT_OK);
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                }
            };

            DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    closeCommentView();
                    setResult(RESULT_CANCELED);
                }
            };

            // KS: This is just doing nothing?
            showSendFileDialog(filename, null, filesize, positiveListener, negativeListener);
            mActionContainer = null;

        } else {
            // Share/send use case
            Intent shareIntent = new Intent();
            String action = StringUtil.isNullOrEmpty(mFileIntentAction) ? Intent.ACTION_SEND : mFileIntentAction;
            shareIntent.setAction(action);

            boolean isSendAction = StringUtil.isEqual(action, Intent.ACTION_SEND);

            try {
                mShareFileUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", dataFile);
            } catch (IllegalArgumentException e) {
                LogUtil.e(TAG, "onFileLoaded: Failed to get file.", e);
                return;
            }

            if (mShareFileUri != null) {
                LogUtil.d(TAG, "onFileLoaded: Prepare " + action + " for " + mShareFileUri);
                if (isSendAction) {
                    shareIntent.putExtra(Intent.EXTRA_STREAM, mShareFileUri);
                    shareIntent.setType(mimeType);
                } else {
                    shareIntent.setDataAndType(mShareFileUri, mimeType);
                }

                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, mShareFileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    LogUtil.d(TAG, "onFileLoaded: Request for mimeType " + mimeType + " brought " + resolveInfo);
                }

                Intent externIntent = Intent.createChooser(shareIntent, getResources().getText(R.string.share_file_titel));

                if (shareIntent.resolveActivity(getPackageManager()) != null) {
                    router.startExternalActivityForResult(externIntent, RouterConstants.SEND_FILE_RESULT_CODE);
                } else {
                    LogUtil.i(TAG, "onFileLoaded: Could not locate a matching app for mimeType " + mimeType);
                    String msg = StringUtil.isEqual(action, Intent.ACTION_VIEW) ? getString(R.string.chat_open_file_no_extern_activity) : getString(R.string.chat_share_file_no_extern_activity);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                }
            }
        }
    }

    @Override
    protected int getActivityLayout() {
        return 0;
    }

    @Override
    protected void onResumeActivity() {
        if (mChatInputFragment != null) {
            if (MetricsUtil.getDisplayMetrics(this).xdpi < SMALL_DEVICE_DPI_LIMIT
                    || MetricsUtil.getDisplayMetrics(this).ydpi < SMALL_DEVICE_DPI_LIMIT) {
                mChatInputFragment.getEditText().setMaxLines(3);
            }
        }
    }

    void prepareResendMenu(BaseChatItemVO baseChatItemVO) {
        //do nothing in base
    }

    protected void showToolbarOptions(final List<ToolbarOptionsItemModel> toolbarOptionsItemModels) {
        final int size = toolbarOptionsItemModels.size();

        if (size != 0) {
            if (mToolbarOptionsLayout != null) {
                mToolbarOptionsLayout.setVisibility(View.VISIBLE);
                mToolbarOptionsLayout.setBackgroundColor(ColorUtil.getInstance().getToolbarColor(getSimsMeApplication()));
                getToolbar().setVisibility(View.GONE);
                for (int i = 0; i < size; ++i) {
                    final ToolbarOptionsItemModel model = toolbarOptionsItemModels.get(i);
                    final String imageViewId = "toolbar_options_item_" + i;
                    final int imageViewRecourceId = getResources().getIdentifier(imageViewId, "id", getPackageName());

                    final ImageView imageview = mToolbarOptionsLayout.findViewById(imageViewRecourceId);
                    if (imageview != null) {
                        imageview.setImageDrawable(getColorizedDrawableForToolbar(model.getImageId()));
                        imageview.setOnClickListener(model.getOnClickListener());
                        imageview.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
        if (mToolbarOptionsLayout != null) {
            final ImageView backarrow = mToolbarOptionsLayout.findViewById(R.id.toolbar_options_item_backarrow);
            if (backarrow != null) {
                backarrow.setImageDrawable(getColorizedDrawableForToolbar(R.drawable.ic_arrow_back_white_24dp));
            }
        }
    }

    protected Drawable getColorizedDrawableForToolbar(int drawableId) {
        Drawable d = ContextCompat.getDrawable(this, drawableId);
        if (d != null) {
            d.mutate();
            d.setColorFilter(ColorUtil.getInstance().getMainContrast80Color(getSimsMeApplication()), PorterDuff.Mode.SRC_ATOP);
        }
        return d;
    }

    boolean hasHeaderView() {
        return false;
    }

    public void showSelfdestructionFragment(final boolean mode) {
        showSelfdestructionFragment(mode, null, null);
    }

    /**
     * sd fragment anzeigen
     *
     * @param mode              PICKER_MODE_DESTRUCTION or PICKER_MODE_TIMER
     * @param destructionParams MessageDestructionParams
     * @param timerDate         destruction timerDate
     */
    void showSelfdestructionFragment(final boolean mode, final MessageDestructionParams destructionParams, final Date timerDate) {
        synchronized (this) {
            KeyboardUtil.toggleSoftInputKeyboard(this, mChatInputFragment.getEditText(), false);
            hideOrShowFragment(mEmojiconsFragment, false, true);

            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                public void run() {
                    // TODO remove redundant code
                    if (mSelfdestructionFragment == null) {
                        mSelfdestructionFragment = new SelfdestructionFragment();

                        createAndAddSdListeners();

                        getSupportFragmentManager().beginTransaction()
                                .add(mFragmentContainerId, mSelfdestructionFragment)
                                .commit();

                        View fragmentView = findViewById(mFragmentContainerId);

                        mAnimationSlideIn = mAnimationSlideInSixLines;
                        mAnimationSlideOut = mAnimationSlideOutSixLines;
                        fragmentView.startAnimation(mAnimationSlideIn);
                    } else {
                        hideOrShowFragment(mSelfdestructionFragment, true, true);
                    }
                    mSelfdestructionFragment.setMode(mode);
                    mChatInputFragment.setDestructionViewMode(mode);
                    mCurrentFragment = mSelfdestructionFragment;

                    if (destructionParams != null) {
                        mSelfdestructionFragment.setDestructionParams(destructionParams);
                        mChatInputFragment.handleDestructionClick();
                    }

                    if (timerDate != null) {
                        mSelfdestructionFragment.setTimerDate(timerDate);
                        mChatInputFragment.handleTimerClick();
                    }

                    ChatInputActivity.this.smoothScrollToEnd();
                }
            };
            handler.postDelayed(runnable, ANIMATION_DURATION);
        }
    }

    void showSendFileDialog(final String filename, final String extension, final long fileSize, final DialogInterface.OnClickListener positiveListener, final DialogInterface.OnClickListener negativeListener) {
    }

    void closeCommentView() {
        //do nothing
    }

    CitationModel buildCitationFromSelectedChatItem() {
        return null;
    }

    void checkChat()
            throws LocalizedException {
        if (mChat == null) {
            if (StringUtil.isNullOrEmpty(mTargetGuid)) {
                return;
            }

            mChat = getChatController().getChatOrCreateIfNotExist(mTargetGuid, mTitle);
        }
    }

    private void createAndAddSdListeners() {
        if (mSelfdestructionFragment != null) {
            mSelfdestructionFragment.setOnDestructionValueChangedListener(new SelfdestructionFragment.OnDestructionValueChangedListener() {
                @Override
                public void onDateChanged(final String date) {
                    mChatInputFragment.setDestructionInfoText(date);
                }

                @Override
                public void onPickerChanged(final int value) {
                    String second;
                    if (value < 2) {
                        second = getString(R.string.chats_selfdestruction_countdown_second);
                    } else {
                        second = getString(R.string.chats_selfdestruction_countdown_seconds);
                    }
                    if (mChatInputFragment.getDestructionEnabled()) {
                        mChatInputFragment.setDestructionInfoText(value + " " + second);
                    }
                }
            });

            mSelfdestructionFragment.setOnTimerChangedLister(new SelfdestructionFragment.OnTimerChangedLister() {
                @Override
                public void onDateChanged(final String date) {
                    mChatInputFragment.setTimerInfoText(date);
                }
            });
        }
    }

    HttpBaseTask.OnConnectionDataUpdatedListener createOnConnectionDataUpdatedListener(final int position, final boolean isPriority) {
        return new HttpBaseTask.OnConnectionDataUpdatedListener() {
            private final int mPosition = position;
            private long mFilesize;

            @Override
            public void onConnectionDataUpdated(int value) {

                final ProgressBar progressBar = mChatAdapter.getProgressBarForPosition(mPosition);
                if (progressBar == null) {
                    return;
                }

                // Initial signal that we could not determine a filesize.
                if(mFilesize == -1) {
                    try { // This may be called by other than UI thread!
                        progressBar.setIndeterminate(true);
                    } catch (Exception e) {
                        LogUtil.e(TAG, "mFilesize = -1: " + e.getMessage(), e);
                    } finally {
                        mFilesize = 0;
                    }
                }

                final int percent = mFilesize == 0 ? 0 : (int) (value * 100 / mFilesize);
                if (percent < 100) {
                    if(percent != 0) {
                        try { // This may be called by other than UI thread!
                            progressBar.setProgress(percent);
                        } catch (Exception e) {
                            LogUtil.e(TAG, "setProgress to " + percent + ": " + e.getMessage(), e);
                        }
                    }
                } else {
                    final Handler handler = new Handler(ChatInputActivity.this.getMainLooper());
                    final Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            final String tag = (String) progressBar.getTag();

                            if (StringUtil.isEqual(tag, "file") || StringUtil.isEqual(tag, "destruction") || StringUtil.isEqual(tag, "audio")) {

                                final ViewGroup parent = (ViewGroup) progressBar.getParent();
                                if (parent != null) {
                                    final ImageView imageView = parent.findViewById(R.id.chat_item_data_placeholder_bg);
                                    if (imageView != null) {

                                        final Drawable[] backgrounds = new Drawable[2];
                                        final Resources res = getResources();

                                        final int color;
                                        if (isPriority) {
                                            color = ColorUtil.getInstance().getAlertColor(getSimsMeApplication());
                                        } else {
                                            color = ColorUtil.getInstance().getChatItemColor(getSimsMeApplication());
                                        }

                                        if (StringUtil.isEqual(tag, "destruction")) {
                                            if (imageView instanceof MaskImageView) {

                                                Bitmap before = colorImage(color, res.getDrawable(R.drawable.szf_overlay_not_loaded));
                                                Bitmap after = colorImage(color, res.getDrawable(R.drawable.szf_overlay));

                                                Drawable drawableBefore = new BitmapDrawable(getResources(), before);
                                                Drawable drawableAfter = new BitmapDrawable(getResources(), after);

                                                backgrounds[0] = drawableBefore;
                                                backgrounds[1] = drawableAfter;
                                            } else {
                                                backgrounds[0] = res.getDrawable(R.drawable.szf_overlay_not_loaded);
                                                backgrounds[1] = res.getDrawable(R.drawable.szf_overlay);
                                            }
                                        } else if (StringUtil.isEqual(tag, "audio")) {
                                            if (imageView instanceof MaskImageView) {
                                                Bitmap before = colorImage(color, res.getDrawable(R.drawable.sound_placeholder_not_loaded));
                                                Bitmap after = colorImage(color, res.getDrawable(R.drawable.sound_placeholder));

                                                Drawable drawableBefore = new BitmapDrawable(getResources(), before);
                                                Drawable drawableAfter = new BitmapDrawable(getResources(), after);

                                                backgrounds[0] = drawableBefore;
                                                backgrounds[1] = drawableAfter;
                                            } else {
                                                backgrounds[0] = res.getDrawable(R.drawable.sound_placeholder_not_loaded);
                                                backgrounds[1] = res.getDrawable(R.drawable.sound_placeholder);
                                            }
                                        } else if (StringUtil.isEqual(tag, "file")) {

                                            if (imageView instanceof MaskImageView) {
                                                Bitmap before = colorImage(color, res.getDrawable(R.drawable.data_placeholder_not_loaded));
                                                Bitmap after = colorImage(color, res.getDrawable(R.drawable.data_placeholder));

                                                Drawable drawableBefore = new BitmapDrawable(getResources(), before);
                                                Drawable drawableAfter = new BitmapDrawable(getResources(), after);

                                                backgrounds[0] = drawableBefore;
                                                backgrounds[1] = drawableAfter;
                                            } else {
                                                backgrounds[0] = res.getDrawable(R.drawable.data_placeholder_not_loaded);
                                                backgrounds[1] = res.getDrawable(R.drawable.data_placeholder);
                                            }
                                        }

                                        final TransitionDrawable crossfader = new TransitionDrawable(backgrounds);

                                        imageView.setImageDrawable(crossfader);

                                        crossfader.startTransition(300);

                                        final Animation scaleAni = new ScaleAnimation(0.6f, 1, 0.6f, 1, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                                        scaleAni.setDuration(400);
                                        imageView.startAnimation(scaleAni);

                                        progressBar.setVisibility(View.INVISIBLE);
                                        mChatAdapter.removeProgressBarFromMap(mPosition);
                                    }
                                }
                            } else {
                                progressBar.setIndeterminate(true);
                            }
                        }
                    };
                    handler.post(runnable);
                }
            }

            @Override
            public void setFileSize(long fileSize) {
                this.mFilesize = fileSize;
            }
        };
    }

    public void showEmojiFragment() {
        synchronized (this) {
            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                public void run() {
                    // TODO remove redundant code
                    if (mEmojiconsFragment == null) {
                        mEmojiconsFragment = new EmojiPickerFragment();

                        getSupportFragmentManager().beginTransaction()
                                .add(mFragmentContainerId, mEmojiconsFragment)
                                .commit();

                        final View emojiContainer = findViewById(mFragmentContainerId);

                        emojiContainer.startAnimation(mAnimationSlideIn);
                    } else {
                        hideOrShowFragment(mEmojiconsFragment, true, true);
                    }
                    if ((mEmojiconsFragment.getView() != null) && (mEmojiconsFragment.getView().getLayoutParams() != null)) {
                        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            mEmojiconsFragment.getView().getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_landscape);
                        } else {
                            mEmojiconsFragment.getView().getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_portrait);
                        }
                    }
                    mCurrentFragment = mEmojiconsFragment;

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (mCurrentFragment != null && imm != null) {
                        imm.hideSoftInputFromWindow(mEmojiconsFragment.getView().getWindowToken(), 0);
                    }

                    ChatInputActivity.this.smoothScrollToEnd();
                }
            };
            handler.postDelayed(runnable, 300);
        }
    }

    boolean hideOnlineState() {
        return false;
    }

    public void hideDestructionPicker() {
        synchronized (this) {
            if (mCurrentFragment != null && mCurrentFragment instanceof SelfdestructionFragment) {
                hideOrShowFragment(mCurrentFragment, false, true);
                mCurrentFragment = null;
            }
        }
    }

    public void hideEmojiPicker() {
        synchronized (this) {
            if (mCurrentFragment != null && mCurrentFragment instanceof EmojiPickerFragment) {
                hideOrShowFragment(mCurrentFragment, false, true);
                mCurrentFragment = null;
            }
        }
    }

    public void hideFragment() {
        synchronized (this) {
            if (mCurrentFragment != null) {
                hideOrShowFragment(mCurrentFragment, false, true);

            /*
            if (mCurrentFragment == mSelfdestructionFragment)
            {
               mSelfdestructionFragment = null;
            }
            */

                mCurrentFragment = null;
            }
        }
    }

    boolean isDownloading(String messageId) {
        return mDownloadProgress.containsKey(messageId);
    }

    void markDownloading(String messageId) {
        mDownloadProgress.put(messageId, messageId);
    }

    void finishDownloading(String messageId) {
        mDownloadProgress.remove(messageId);
    }

    ToolbarOptionsItemModel createToolbarOptionsCopyModel() {
        int id = R.drawable.ic_content_copy_white_24dp;
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCopyMessageClick(null);
                hideToolbarOptions();
            }
        };
        return new ToolbarOptionsItemModel(id, listener);
    }

    public void handleCopyMessageClick(final View view) {
        if (mPreferencesController.isCopyPasteDisabled()) {
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_Location_copypaste_not_allowed), Toast.LENGTH_SHORT).show();
            return;
        }
        final String text = ((TextChatItemVO) mMarkedChatItem).message;

        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        final android.content.ClipData clip = android.content.ClipData.newPlainText("ginlo", text);

        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
        Toast.makeText(this, getString(R.string.chats_copy_msg_success), Toast.LENGTH_SHORT).show();
    }

    ToolbarOptionsItemModel createToolbarOptionsForwardModel() {
        final int id = R.drawable.ic_forward_white_24dp;
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleForwardMessageClick(null);
                hideToolbarOptions();
            }
        };
        return new ToolbarOptionsItemModel(id, listener);
    }

    /**
     * handleForwardMessageClick
     *
     * @param view view
     */
    protected abstract void handleForwardMessageClick(View view);

    ToolbarOptionsItemModel createToolbarOptionsForwardPictureModel() {
        final int id = R.drawable.ic_photo_library_white_24dp;
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleForwardMessageImageClick(null);
                hideToolbarOptions();
            }
        };
        return new ToolbarOptionsItemModel(id, listener);
    }

    /**
     * handleForwardMessageImageClick
     *
     * @param view view
     */
    protected abstract void handleForwardMessageImageClick(View view);

    ToolbarOptionsItemModel createToolbarOptionsDeleteModel() {
        final int id = R.drawable.ic_delete_white_24dp;
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleDeleteMessageClick(null);
                hideToolbarOptions();
            }
        };
        return new ToolbarOptionsItemModel(id, listener);
    }

    /**
     * handleDeleteMessageClick
     *
     * @param view view
     */
    protected abstract void handleDeleteMessageClick(View view);

    ToolbarOptionsItemModel createToolbarOptionsInfoModel() {
        final int id = R.drawable.ic_info_outline_white_24dp;
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleMessageInfoClick();
                hideToolbarOptions();
            }
        };
        return new ToolbarOptionsItemModel(id, listener);
    }

    protected abstract void handleMessageInfoClick();

    public void handleShareMessageClick(final View view) {
        doHandleFileAction(Intent.ACTION_SEND);
    }

    public void handleOpenMessageClick(final View view) {
        doHandleFileAction(Intent.ACTION_VIEW);
    }

    private void doHandleFileAction(final String fileIntentAction) {
        if (!mPreferencesController.isOpenInAllowed()) {
            return;
        }
        closeBottomSheet(mOnBottomSheetClosedListener);

        // KS: Always show warning
        mPreferencesController.setHasShowOpenFileWarning(false);

        //Show file export warning
        if (!mPreferencesController.getHasShowOpenFileWarning()) {
            final String posButton = getString(R.string.std_ok);
            final String negButton = getString(R.string.std_cancel);

            final String title = getString(R.string.chat_open_file_warning_title);
            final String message = getString(R.string.chat_open_file_warning_message);

            final DialogInterface.OnClickListener onPosClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    mPreferencesController.setHasShowOpenFileWarning(true);
                    startGetFileAttachment(fileIntentAction);
                }
            };

            DialogBuilderUtil.buildResponseDialogV7(this, message, title, posButton, negButton, onPosClickListener, null).show();
        } else {
            startGetFileAttachment(fileIntentAction);
        }
    }

    private Bitmap colorImage(int color, Drawable mask) {
        final BitmapDrawable tmpDrawable = (BitmapDrawable) mask;

        final ColorDrawable drawable = new ColorDrawable(color);
        final PorterDuffXfermode xferModeDstIn = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

        int height = tmpDrawable.getBitmap().getHeight();
        int width = tmpDrawable.getBitmap().getWidth();

        final Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        final Canvas customCanvas = new Canvas(result);

        drawable.setBounds(0, 0, width, height);

        drawable.draw(customCanvas);

        final Paint paint = new Paint();

        paint.setXfermode(xferModeDstIn);

        customCanvas.drawBitmap(tmpDrawable.getBitmap(), 0.0f, 0.0f, paint);
        return result;
    }


    private void startGetFileAttachment(final String fileIntentAction) {
        if (mMarkedChatItem instanceof FileChatItemVO) {
            mFileIntentAction = fileIntentAction;

                final ProgressBar progressBar;
                if (mClickedView != null) {
                    progressBar = mClickedView.findViewById(R.id.progressBar_download);
                    mClickedView = null;
                } else {
                    progressBar = null;
                }

                if (progressBar != null) {
                    final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener = createOnConnectionDataUpdatedListener(mClickedIndex, mMarkedChatItem.isPriority);
                    getChatController().getAttachment((AttachmentChatItemVO) mMarkedChatItem, ChatInputActivity.this, true, onConnectionDataUpdatedListener);
                } else {
                    getChatController().getAttachment((AttachmentChatItemVO) mMarkedChatItem, ChatInputActivity.this, true, null);
                }
            //}
            mClickedIndex = -1;
        }
    }

    ToolbarOptionsItemModel createToolbarOptionsCommentModel() {
        final int id = R.drawable.ic_reply_white_24dp;
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleMessageCommentClick();
                hideToolbarOptions();
            }
        };
        return new ToolbarOptionsItemModel(id, listener);
    }

    private FileUtil getFileUtil() {
        if (mFileUtil == null) {
            mFileUtil = new FileUtil(this);
        }

        return mFileUtil;
    }

    protected abstract void handleMessageCommentClick();

    public abstract void handleAddAttachmentClick();

    public abstract void scrollIfLastChatItemIsNotShown();

    /**
     * @param voiceUri voiceUri
     */
    public abstract void handleSendVoiceClick(Uri voiceUri);

    /**
     * @param text text
     */
    public abstract boolean handleSendMessageClick(String text);

    /**
     * KS: AVC
     */
    public abstract boolean handleAVCMessageClick(int callType);

    public abstract void smoothScrollToEnd();

    public abstract boolean canSendMedia();

    /**
     * setOnlineStateToTyping
     */
    public void setOnlineStateToTyping() {
        if (mTargetGuid != null
                && mAccountController != null
                && GuidUtil.isChatSingle(mTargetGuid)
                && mPreferencesController.getPublicOnlineState()) {
            mAccountController.setOnlineStateToTyping(mTargetGuid);
        }
    }

    /**
     * setOnlineStateToOnline
     */
    public void setOnlineStateToOnline() {
        if (mTargetGuid != null
                && mAccountController != null
                && mPreferencesController.getPublicOnlineState()) {
            mAccountController.setOnlineStateToOnline(mTargetGuid);
        }
    }

    void getOnlineState() {

        LogUtil.d(TAG, "getOnlineState: getOnlineState for target guid: " + mTargetGuid);

        try {
            if (StringUtil.isNullOrEmpty(mTargetGuid)
                    || !mPreferencesController.getPublicOnlineState()) {
                return;
            }

            final Contact contactByGuid = mContactController.getContactByGuid(mTargetGuid);
            if (contactByGuid == null) {
                return;
            }

            final GenericActionListener<ContactController.OnlineStateContainer> genericActionListener = new GenericActionListener<
                    ContactController.OnlineStateContainer>() {
                @Override
                public void onSuccess(ContactController.OnlineStateContainer onlineStateContainer) {
                    // Letzten Stand merken, falls es neues gibt
                    if (onlineStateContainer != null) {
                        mLastKnownonlineStateContainer = onlineStateContainer;
                    }
                    // Falls es nix neues gibt, (timeout), dann mit dem alten Stand weiterarbeiten
                    if (onlineStateContainer == null) {
                        onlineStateContainer = mLastKnownonlineStateContainer;
                    }
                    final Long contactLastOnlineTime;
                    final String onlineStateString;

                    if (mSecondaryTitle != null) {
                        // contact updaten
                        if (onlineStateContainer != null && !StringUtil.isNullOrEmpty(onlineStateContainer.lastOnline)) {
                            final Long contactStoredLastOnlineTime = contactByGuid.getLastOnlineTime();
                            final long newOnlineTime = DateUtil.utcWithoutMillisStringToMillis(onlineStateContainer.lastOnline);
                            if (contactStoredLastOnlineTime == null || newOnlineTime > contactStoredLastOnlineTime) {
                                contactByGuid.setLastOnlineTime(newOnlineTime);
                            }
                        } else {
                            contactByGuid.setLastOnlineTime(null);
                        }

                        if (onlineStateContainer != null && ONLINE_STATE_ABSENT.equals(onlineStateContainer.state)) {
                            onlineStateString = getResources().getString(R.string.online_state_absent);
                        }
                        // schreibt
                        else if (onlineStateContainer != null && ONLINE_STATE_WRITING.equals(onlineStateContainer.state)) {
                            onlineStateString = getResources().getString(R.string.online_state_writing);
                        }
                        //sendet selbst status online
                        else if (onlineStateContainer != null && ONLINE_STATE_ONLINE.equals(onlineStateContainer.state)) {
                            onlineStateString = getResources().getString(R.string.online_state_online);
                        } else {
                            contactLastOnlineTime = contactByGuid.getLastOnlineTime();
                            if (contactLastOnlineTime == null) {
                                onlineStateString = "";
                            } else {
                                // vor ueber einer Woche online
                                final Calendar calendar = Calendar.getInstance();
                                calendar.set(Calendar.HOUR_OF_DAY, 0);
                                calendar.set(Calendar.MINUTE, 0);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.add(Calendar.HOUR, -7 * 24);
                                final long oneWeekAgoTime = calendar.getTime().getTime();

                                // online vor ueber einer Woche
                                if (contactLastOnlineTime < oneWeekAgoTime) {
                                    onlineStateString = "";
                                } else {
                                    // vor gestern 0:00Uhr online
                                    calendar.add(Calendar.HOUR, 6 * 24);
                                    final long yesterdayTime = calendar.getTime().getTime();

                                    if (contactLastOnlineTime < yesterdayTime) {
                                        final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.", Locale.GERMANY);
                                        final SimpleDateFormat sdfHour = new SimpleDateFormat("H", Locale.GERMANY);
                                        final String dateString = sdf.format(contactLastOnlineTime);
                                        final String hourString = sdfHour.format(contactLastOnlineTime);
                                        onlineStateString = String.format(getResources().getString(R.string.online_state_was_online_at), dateString, hourString);
                                    }
                                    // gestern online
                                    else {
                                        final SimpleDateFormat sdfHour = new SimpleDateFormat("H", Locale.GERMANY);
                                        final SimpleDateFormat sdfMin = new SimpleDateFormat("m", Locale.GERMANY);
                                        String hourString = sdfHour.format(contactLastOnlineTime);
                                        String minString = sdfMin.format(contactLastOnlineTime);
                                        if (hourString.length() == 1) {
                                            hourString = "0" + hourString;
                                        }
                                        if (minString.length() == 1) {
                                            minString = "0" + minString;
                                        }

                                        calendar.add(Calendar.HOUR, 24);
                                        final long todayTime = calendar.getTime().getTime();
                                        if (contactLastOnlineTime < todayTime) {
                                            onlineStateString = String.format(getResources().getString(R.string.online_state_was_online_yesterday), hourString, minString);
                                        }
                                        // heute online
                                        else {
                                            final long now = new Date().getTime();
                                            long seconds = (now - contactLastOnlineTime) / 1000;
                                            final long hours = seconds / 3600;
                                            seconds = seconds % 3600;
                                            final long minutes = seconds / 60;

                                            //heute
                                            if (hours > 0 || minutes > 0 /*|| seconds > 30*/) {
                                                onlineStateString = String.format(getResources().getString(R.string.online_state_was_online_today), hourString, minString);
                                            } else {
                                                onlineStateString = getResources().getString(R.string.online_state_online);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        new Handler(ChatInputActivity.this.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (hideOnlineState()) {
                                    return;
                                }

                                if (!StringUtil.isNullOrEmpty(onlineStateString))// || contactLastOnlineTime != 0)
                                {

                                    final int oldVisibility = mSecondaryTitle.getVisibility();
                                    if (oldVisibility == View.GONE) {

                                        mSecondaryTitle.setVisibility(View.VISIBLE);
                                        mSecondaryTitle.setText("");

                                        final int slideheigth = (int) getResources().getDimension(R.dimen.toolbar_secondary_title_height);

                                        final TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, slideheigth, 0);
                                        final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

                                        translateAnimation.setDuration(ANIMATION_DURATION * 3);
                                        translateAnimation.setInterpolator(decelerateInterpolator);

                                        translateAnimation.setAnimationListener(new Animation.AnimationListener() {
                                            @Override
                                            public void onAnimationStart(final Animation animation) {

                                            }

                                            @Override
                                            public void onAnimationEnd(final Animation animation) {
                                                mSecondaryTitle.setText(onlineStateString);
                                            }

                                            @Override
                                            public void onAnimationRepeat(final Animation animation) {

                                            }
                                        });

                                        getTitleView().startAnimation(translateAnimation);
                                    } else {
                                        mSecondaryTitle.setText(onlineStateString);
                                    }
                                } else {
                                    // TODO ANIMATION
                                    mSecondaryTitle.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                }

                @Override
                public void onFail(final String message,
                                   final String errorIdent) {

                }
            };

            mContactController.getOnlineState(mTargetGuid, ONLINE_STATE_INVALID, genericActionListener, true);
        } catch (final LocalizedException le) {
            LogUtil.w(TAG, le.getMessage(), le);
        }
    }
}
