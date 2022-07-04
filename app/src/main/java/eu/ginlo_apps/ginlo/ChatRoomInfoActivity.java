// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import org.jetbrains.annotations.NotNull;

import eu.ginlo_apps.ginlo.activity.chat.GroupChatActivity;
import eu.ginlo_apps.ginlo.activity.profile.ProfileActivity;
import eu.ginlo_apps.ginlo.adapter.ContactsAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.GroupInfoChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnBuildChatRoomListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnRemoveRoomListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSetGroupInfoListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnUpdateGroupMembersListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.FragmentUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.OnSingleClickListener;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.RoundedImageView;
import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity;
import androidx.emoji.widget.EmojiAppCompatEditText;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.inject.Inject;

public class ChatRoomInfoActivity
        extends BaseActivity
        implements OnBuildChatRoomListener,
        OnClickListener,
        View.OnLongClickListener,
        EmojiPickerCallback {

    public static final String TAG = ChatRoomInfoActivity.class.getSimpleName();
    public static final String EXTRA_MODE = "ChatRoomInfoActivity.extraMode";
    public static final String EXTRA_CHAT_GUID = "ChatRoomInfoActivity.extraChat";
    public static final int MODE_CREATE = 0;
    public static final int MODE_INFO = 1;
    public static final int MODE_EDIT = 2;
    public static final int MODE_ADMIN = 3;

    // A new MODE_RESTRICTED for ANNOUNCEMENT_GROUP
    // Adds special restrictions to MODE_INFO
    public static final int MODE_RESTRICTED = 4;

    private static final int REQUEST_CONTACT_SELECT = 0x12;
    private static final int REQUEST_ADMIN_SELECT = 13;
    private static final int REQUEST_REMOVE_CONTACT_SELECT = 14;
    private static final int MAX_SELECTABLE_CONTACT_SIZE = 101;

    private GroupChatController groupChatController;
    private ContactController contactController;
    private PreferencesController preferenceController;
    private AccountController accountController;

    private LinearLayout selectedContactsListContainer;
    private LinearLayout selectedContactsListItemContainer;
    private LinearLayout selectedAdminsListContainer;
    private LinearLayout selectedAdminsListItemContainer;

    private ArrayList<Contact> mSelectedContacts = new ArrayList<>();
    private ArrayList<String> mAdmins = new ArrayList<>();

    private ContactsAdapter selectedContactsAdapter;
    private ContactsAdapter selectedAdminsAdapter;

    private TextView selectedContactsCountTextView;
    private TextView selectedAdminTextView;
    private RoundedImageView mGroupImageView;
    private View groupImageOverlayView;
    private EmojiAppCompatEditText mChatRoomNameEditText;
    private Bitmap groupImageAfterEdit;
    private byte[] imageBytes;
    private int mode;
    private Chat mChat;
    private String mChatGuid;
    private File takenPhotoFile;
    private Button removeButton;
    private Button clearChatButton;
    private LinearLayout addButtonContainer;
    private LinearLayout addAdminButtonContainer;
    private LinearLayout removeButtonContainer;
    private RelativeLayout mMuteChatContainer;
    private RelativeLayout mAnnouncementGroupContainer;
    private boolean mIsAnnouncementGroup = false;
    private TextView addAdminButtonTextView;
    private boolean isRoomDuringCreation;
    private CheckBox mAddEmojiNicknameButton;
    private boolean mEmojiFragmentVisible;
    private EmojiPickerFragment mEmojiFragment;
    private FrameLayout mEmojiContainer;
    private Contact mLongTapContact;
    private GroupInfoChangedListener mGroupInfoChangedListener;
    private boolean mContactDetailsStarted;
    private Timer mRefreshTimer;

    @Inject
    public Router router;

    @Inject
    public AppConnectivity appConnectivity;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        setIsMenuVisible(false);
        return true;
    }

    private void setModeDependentUI()
            throws LocalizedException {

        selectedContactsAdapter = new ContactsAdapter(this, R.layout.contact_item_overview_layout,
                new ArrayList<Contact>(), false, true);

        selectedContactsAdapter.setAccount(accountController.getAccount());

        selectedAdminsAdapter = new ContactsAdapter(this, R.layout.contact_item_overview_layout,
                new ArrayList<Contact>(), false, true);

        if (mChat != null) {
            selectedAdminsAdapter.setGroupChatOwner(mChat.getOwner());
        }

        selectedAdminsAdapter.setAccount(accountController.getAccount());

        configureTitle();
        configureAddMemberButtons();
        configureGroupImageView();
        configureRemoveButton();
        configureClearChatButton();
        configureMuteChatButton();
        configureAnnouncementGroupButton();
        configureRoomNameEditTextView();
        configureRemoveMemberButtons();

        fillSelectedContactsWithChatMembers();

        if ((mode == MODE_CREATE) || (mode == MODE_EDIT) || (mode == MODE_ADMIN)) {
            setRightActionBarImage(R.drawable.ic_done_white_24dp, getRightActionBarClickListener(), getResources().getString(R.string.content_description_groupinfo_appy), -1);
        } else {
            removeRightActionBarImage();
        }
    }

    private String getEnteredName() {
        Editable edt = mChatRoomNameEditText.getText();
        if (edt == null) {
            return null;
        }
        return edt.toString().trim();
    }

    private OnSingleClickListener getRightActionBarClickListener() {

        return new OnSingleClickListener() {
            @Override
            public void onSingleClick() {
                if (StringUtil.isNullOrEmpty(getEnteredName())) {
                    AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(ChatRoomInfoActivity.this,
                            getString(R.string.chat_group_name_empty));

                    alert.show();
                    return;
                }

                if (mode == MODE_EDIT || mode == MODE_ADMIN) {
                    try {
                        if (!updateChatRoom()) {
                            finish();
                        }
                    } catch (LocalizedException e) {
                        Toast.makeText(ChatRoomInfoActivity.this, R.string.settings_save_setting_failed, Toast.LENGTH_LONG)
                                .show();
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                } else {
                    if (!isRoomDuringCreation) {
                        KeyboardUtil.toggleSoftInputKeyboard(getApplicationContext(), mChatRoomNameEditText, false);
                        isRoomDuringCreation = true;

                        String groupName = getEnteredName();

                        String roomType = Chat.ROOM_TYPE_STD;
                        if(mIsAnnouncementGroup) {
                            roomType = Chat.ROOM_TYPE_ANNOUNCEMENT;
                        }
                        List<String> writers = null;

                        showIdleDialog(R.string.progress_dialog_create_group);

                        groupChatController.createGroup(groupName, imageBytes, roomType, mSelectedContacts, mAdmins, writers, ChatRoomInfoActivity.this);
                    }
                }
            }
        };
    }

    private void configureRoomNameEditTextView()
            throws LocalizedException {
        switch (mode) {
            case MODE_RESTRICTED:
            case MODE_INFO:
                mChatRoomNameEditText.setText(mChat.getTitle());
                mChatRoomNameEditText.setEnabled(false);
                mAddEmojiNicknameButton.setVisibility(View.GONE);
                break;
            case MODE_CREATE:
                mChatRoomNameEditText.setEnabled(true);
                break;
            case MODE_ADMIN:
            case MODE_EDIT:
                mChatRoomNameEditText.setText(mChat.getTitle());
                mChatRoomNameEditText.setEnabled(true);
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureAddMemberButtons()
            throws LocalizedException {
        switch (mode) {
            case MODE_ADMIN:
            case MODE_CREATE:
            case MODE_EDIT:
                addButtonContainer.setVisibility(View.VISIBLE);
                addAdminButtonContainer.setVisibility(View.VISIBLE);
                break;
            case MODE_RESTRICTED:
            case MODE_INFO:
                addButtonContainer.setVisibility(View.GONE);
                addAdminButtonContainer.setVisibility(View.GONE);
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureRemoveMemberButtons()
            throws LocalizedException {
        switch (mode) {
            case MODE_ADMIN:
            case MODE_EDIT:
                removeButtonContainer.setVisibility(View.VISIBLE);
                break;
            case MODE_INFO:
            case MODE_RESTRICTED:
            case MODE_CREATE:
                removeButtonContainer.setVisibility(View.GONE);
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureRemoveButton()
            throws LocalizedException {
        switch (mode) {
            case MODE_ADMIN:
                removeButton.setVisibility(View.VISIBLE);
                removeButton.setText(getString(R.string.chat_group_button_leave));
                break;
            case MODE_INFO:
            case MODE_RESTRICTED:
                if (mChat != null && Chat.ROOM_TYPE_MANAGED.equals(mChat.getRoomType())) {
                    removeButton.setVisibility(View.GONE);
                } else {
                    removeButton.setVisibility(View.VISIBLE);
                    removeButton.setText(getString(R.string.chat_group_button_leave));
                }
                break;
            case MODE_CREATE:
                removeButton.setVisibility(View.GONE);

                break;
            case MODE_EDIT:
                removeButton.setVisibility(View.VISIBLE);
                removeButton.setText(getString(R.string.chat_group_button_delete));
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureClearChatButton()
            throws LocalizedException {
        switch (mode) {
            case MODE_RESTRICTED:
            case MODE_INFO: {
                clearChatButton.setEnabled(true);
                clearChatButton.setVisibility(View.VISIBLE);
                RelativeLayout silentTill = findViewById(R.id.chat_room_info_relativeLayout_silent_till);
                silentTill.setVisibility(View.VISIBLE);
                break;
            }
            case MODE_CREATE: {
                clearChatButton.setEnabled(false);
                clearChatButton.setVisibility(View.GONE);
                RelativeLayout silentTill = findViewById(R.id.chat_room_info_relativeLayout_silent_till);
                silentTill.setVisibility(View.GONE);
                break;
            }
            case MODE_ADMIN:
            case MODE_EDIT: {
                clearChatButton.setEnabled(true);
                clearChatButton.setVisibility(View.VISIBLE);
                break;
            }
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureMuteChatButton()
            throws LocalizedException {
        switch (mode) {
            case MODE_RESTRICTED:
            case MODE_INFO:
            case MODE_ADMIN:
            case MODE_EDIT:
                mMuteChatContainer.setVisibility(View.VISIBLE);
                break;
            case MODE_CREATE:
                mMuteChatContainer.setVisibility(View.GONE);
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureAnnouncementGroupButton()
            throws LocalizedException {

        switch (mode) {
            case MODE_RESTRICTED:
            case MODE_INFO:
            case MODE_ADMIN:
            case MODE_EDIT:
                mAnnouncementGroupContainer.setVisibility(View.VISIBLE);
                mAnnouncementGroupContainer.setEnabled(false);
                break;
            case MODE_CREATE:
                if(BuildConfig.ENABLE_CREATION_OF_ANNOUNCEMENT_GROUPS) {
                    mAnnouncementGroupContainer.setVisibility(View.VISIBLE);
                    mAnnouncementGroupContainer.setEnabled(true);
                } else {
                    mAnnouncementGroupContainer.setVisibility(View.GONE);
                    mAnnouncementGroupContainer.setEnabled(false);
                }
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureTitle()
            throws LocalizedException {
        switch (mode) {
            case MODE_RESTRICTED:
            case MODE_INFO:
                setTitle(getString(R.string.chat_group_infoView_title));
                break;
            case MODE_CREATE:
                setTitle(getString(R.string.chat_group_newTitle));
                break;
            case MODE_EDIT:
            case MODE_ADMIN:
                setTitle(getString(R.string.chat_group_administration_title));
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    private void configureGroupImageView()
            throws LocalizedException {
        switch (mode) {
            case MODE_RESTRICTED:
            case MODE_INFO:
                imageController.fillViewWithProfileImageByGuid(mChatGuid, mGroupImageView, ImageUtil.SIZE_GROUP_INFO_BIG, true);
                groupImageOverlayView.setVisibility(View.GONE);
                break;
            case MODE_CREATE: {
                groupImageOverlayView.setVisibility(View.VISIBLE);
                break;
            }
            case MODE_ADMIN:
            case MODE_EDIT:
                imageController.fillViewWithProfileImageByGuid(mChatGuid, mGroupImageView, ImageUtil.SIZE_GROUP_INFO_BIG, true);
                groupImageOverlayView.setVisibility(View.VISIBLE);
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT);
        }
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            groupChatController = ((SimsMeApplication) getApplication()).getGroupChatController();
            contactController = ((SimsMeApplication) getApplication()).getContactController();
            preferenceController = ((SimsMeApplication) getApplication()).getPreferencesController();
            accountController = ((SimsMeApplication) getApplication()).getAccountController();

            mode = getIntent().getIntExtra(EXTRA_MODE, MODE_INFO);
            if (getIntent().hasExtra(EXTRA_CHAT_GUID)) {
                mChatGuid = getIntent().getStringExtra(EXTRA_CHAT_GUID);

                mChat = groupChatController.getChatByGuid(mChatGuid);
                if (mChat == null || Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())) {
                    finish();
                    return;
                }

                // Only owner and admins may see details and change anything
                final TextView announcementGroupStatus = findViewById(R.id.chat_room_info_announcement_group_status);
                if(Chat.ROOM_TYPE_ANNOUNCEMENT.equals((mChat.getRoomType()))) {
                    if(mode == MODE_INFO) {
                        mode = MODE_RESTRICTED;
                    }
                    announcementGroupStatus.setText(getText(R.string.chat_announcement_group_on));
                } else {
                    announcementGroupStatus.setText(getText(R.string.chat_announcement_group_off));
                }
            }

            groupImageOverlayView = findViewById(R.id.chat_room_info_image_view_image_overlay);
            mGroupImageView = findViewById(R.id.chat_room_info_mask_image_view_group_image);
            selectedContactsListContainer = findViewById(R.id.chat_room_info_linear_layout_list_container);
            selectedContactsListItemContainer = findViewById(R.id.chat_room_info_linear_layout_list_item_container);
            selectedAdminsListContainer = findViewById(R.id.chat_room_info_linear_layout_admin_list_container);
            selectedAdminsListItemContainer = findViewById(R.id.chat_room_info_linear_layout_admin_list_item_container);
            selectedContactsCountTextView = findViewById(R.id.chat_room_info_text_view_member_count);
            selectedAdminTextView = findViewById(R.id.chat_room_info_text_view_admin);
            removeButton = findViewById(R.id.chat_room_info_button_remove);
            clearChatButton = findViewById(R.id.chat_room_info_button_clear);
            mMuteChatContainer = findViewById(R.id.chat_room_info_relativeLayout_silent_till);
            mAnnouncementGroupContainer = findViewById(R.id.chat_room_info_announcement_group);

            addButtonContainer = findViewById(R.id.chat_room_info_linear_layout_add_button_container);
            TextView addButtonTextView = findViewById(R.id.chat_room_info_linear_layout_add_button_text_view);

            addAdminButtonContainer = findViewById(R.id.chat_room_info_linear_layout_add_admin_button_container);
            addAdminButtonTextView = findViewById(R.id.chat_room_info_linear_layout_add_admin_button_text_view);

            removeButtonContainer = findViewById(R.id.chat_room_info_linear_layout_remove_button_container);
            TextView removeButtonTextView = findViewById(R.id.chat_room_info_linear_layout_remove_button_text_view);

            if (RuntimeConfig.isBAMandant()) {
                final ScreenDesignUtil screenDesignUtil = ScreenDesignUtil.getInstance();
                final int accentColor = screenDesignUtil.getAppAccentColor(getSimsMeApplication());
                addButtonTextView.setTextColor(accentColor);
                addAdminButtonTextView.setTextColor(accentColor);
                removeButtonTextView.setTextColor(screenDesignUtil.getLowColor(getSimsMeApplication()));

                for (Drawable drawable : addButtonTextView.getCompoundDrawables()) {
                    if (drawable != null) {
                        drawable.setColorFilter(accentColor, Mode.SRC_ATOP);
                    }
                }
                for (Drawable drawable : addAdminButtonTextView.getCompoundDrawables()) {
                    if (drawable != null) {
                        drawable.setColorFilter(accentColor, Mode.SRC_ATOP);
                    }
                }
            }


            mChatRoomNameEditText = findViewById(R.id.chat_room_info_edit_text_room_name);

            isRoomDuringCreation = false;

            final int slideHeight = (int) getResources().getDimension(R.dimen.profile_slideheight);

            mAnimationSlideIn = new TranslateAnimation(0, 0, slideHeight, 0);
            mAnimationSlideOut = new TranslateAnimation(0, 0, 0, slideHeight);

            mAnimationSlideIn.setDuration(ANIMATION_DURATION);
            mAnimationSlideOut.setDuration(ANIMATION_DURATION);

            DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

            mAnimationSlideIn.setInterpolator(decelerateInterpolator);
            mAnimationSlideOut.setInterpolator(decelerateInterpolator);

            mAddEmojiNicknameButton = findViewById(R.id.chat_room_info_check_box_add_emoji_nickname);
            initEmojiButtonListener();
            initEmojiFieldListener();

            setModeDependentUI();

            if(mode != MODE_RESTRICTED) {
                updateCountLabel();
            } else {
                selectedContactsCountTextView.setVisibility(View.GONE);
                // Show admins
                //selectedAdminTextView.setVisibility(View.GONE);
            }

        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_chat_room_info;
    }

    @Override
    protected void onResumeActivity() {
        //check, if device is online and ignore it
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
        }

        try {
            try {
                if (mChat != null) {
                    setTrustColor(getSimsMeApplication().getGroupChatController().getStateForGroupChat(mChat.getChatGuid()));
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }

            mGroupInfoChangedListener = new GroupInfoChangedListener() {

                @Override
                public void onGroupInfoChanged() {
                    try {
                        if (mChat != null) {
                            if (Chat.ROOM_TYPE_RESTRICTED.equals(mChat.getRoomType())) {
                                finish();
                                return;
                            }
                            mChatRoomNameEditText.setText(mChat.getTitle());
                        }

                        imageController.fillViewWithProfileImageByGuid(mChatGuid, mGroupImageView, ImageUtil.SIZE_GROUP_INFO_BIG, false);

                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.toString());
                    }
                }
            };
            groupChatController.addGroupInfoChangedListener(mGroupInfoChangedListener);

            setTimeTextView();

            if (mChat != null && mChat.getSilentTill() > new Date().getTime()) {
                mRefreshTimer = new Timer();

                final TimerTask refreshTask = new TimerTask() {
                    @Override
                    public void run() {
                        new Handler(getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    setTimeTextView();
                                } catch (final LocalizedException le) {
                                    LogUtil.w(TAG, le.getMessage(), le);
                                }
                            }
                        });
                    }
                };
                mRefreshTimer.scheduleAtFixedRate(refreshTask, 0, 5000);
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }

        if (mContactDetailsStarted && selectedContactsAdapter != null && selectedAdminsAdapter != null) {
            mContactDetailsStarted = false;
            refreshListViews();
        }
    }

    private void setTimeTextView()
            throws LocalizedException {
        final TextView silentTillTextView = findViewById(R.id.chat_room_info_silent_till_textview);
        final long now = new Date().getTime();
        final long silentTill;
        if (mChat != null) {
            silentTill = mChat.getSilentTill();
        } else {
            silentTill = 0;
        }
        DateUtil.fillSilentTillTextView(silentTillTextView,
                now,
                silentTill,
                getResources().getString(R.string.chat_mute_remaining_short),
                getString(R.string.chat_mute_infinite),
                getResources().getString(R.string.chat_mute_off)
        );

        if (silentTill - now <= 0) {
            if (mRefreshTimer != null) {
                mRefreshTimer.cancel();
                mRefreshTimer.purge();
                mRefreshTimer = null;
            }
        }
    }

    @Override
    protected void onPauseActivity() {
        super.onPauseActivity();
        groupChatController.removeGroupInfoChangedListener(mGroupInfoChangedListener);
        if (mRefreshTimer != null) {
            mRefreshTimer.cancel();
            mRefreshTimer.purge();
            mRefreshTimer = null;
        }
    }

    public void handleRemoveClick(View view) {
        DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                try {
                    removeGroup();
                    dialog.dismiss();
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    Toast.makeText(ChatRoomInfoActivity.this, R.string.chat_group_delete_failed, Toast.LENGTH_LONG).show();
                }
            }
        };

        DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                dialog.dismiss();
            }
        };

        AlertDialogWrapper alert = DialogBuilderUtil.buildResponseDialog(ChatRoomInfoActivity.this,
                getString(R.string.chat_group_button_delete_confirm),
                getString(R.string.chat_group_button_delete),
                positiveOnClickListener,
                negativeOnClickListener);

        alert.show();
    }

    private void removeGroup()
            throws LocalizedException {
        final String chatGuid = mChat.getChatGuid();
        OnRemoveRoomListener onRemoveRoomMemberListener = new OnRemoveRoomListener() {
            @Override
            public void onRemoveRoomSuccess() {
                getSimsMeApplication().getChatOverviewController().chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                Intent intent = new Intent(ChatRoomInfoActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

                startActivity(intent);
            }

            @Override
            public void onRemoveRoomFail(String message) {
                if (message.contains(LocalizedException.ROOM_UNKNOWN)) {
                    getSimsMeApplication().getChatOverviewController().chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                    final Intent intent = new Intent(ChatRoomInfoActivity.this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

                    startActivity(intent);
                }
                DialogBuilderUtil.buildErrorDialog(ChatRoomInfoActivity.this, message).show();
            }
        };
        groupChatController.removeRoom(mChat.getChatGuid(), onRemoveRoomMemberListener);
    }

    public void handleChoosePictureClick(View view) {
        if (mode == MODE_INFO || mode == MODE_RESTRICTED) {
            return;
        }
        closeEmojis();
        if (!mBottomSheetMoving) {
            openBottomSheet(R.layout.dialog_choose_picture_layout, R.id.chat_room_info_linear_layout_fragment_container);

            if (mChatRoomNameEditText.hasFocus()) {
                KeyboardUtil.toggleSoftInputKeyboard(ChatRoomInfoActivity.this, mChatRoomNameEditText, false);
            }
        }
    }

    public void handleTakePictureClick(View view) {
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera,
                new PermissionUtil.PermissionResultCallback() {
                    @Override
                    public void permissionResult(int permission,
                                                 boolean permissionGranted) {
                        if ((permission == PermissionUtil.PERMISSION_FOR_CAMERA) && permissionGranted) {
                            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                            if (intent.resolveActivity(getPackageManager()) != null) {
                                try {
                                    FileUtil fu = new FileUtil(getSimsMeApplication());
                                    takenPhotoFile = fu.createTmpImageFileAddInIntent(intent);
                                    router.startExternalActivityForResult(intent, TAKE_PICTURE_RESULT_CODE);
                                } catch (LocalizedException e) {
                                    LogUtil.w(TAG, e.getMessage(), e);
                                }
                            }
                            closeBottomSheet(null);
                        }
                    }
                });
    }

    public void handleTakeFromGalleryClick(View view) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    new PermissionUtil.PermissionResultCallback() {
                        @Override
                        public void permissionResult(int permission,
                                                     boolean permissionGranted) {
                            if ((permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE)
                                    && permissionGranted) {
                                closeBottomSheet(new OnBottomSheetClosedListener() {
                                    @Override
                                    public void onBottomSheetClosed(final boolean bottomSheetWasOpen) {
                                        router.pickImage();
                                    }
                                });
                            }
                        }
                    });
        } else {
            closeBottomSheet(new OnBottomSheetClosedListener() {
                @Override
                public void onBottomSheetClosed(final boolean bottomSheetWasOpen) {
                    router.pickImage();
                }
            });
        }
    }

    public void handleDeleteProfileImageClick(View view) {
        LogUtil.d(TAG, "handleDeleteProfileImageClick: Called from " + this.getLocalClassName());
        // Do nothing
    }

    public void handleAddMemberClick(View view) {
        ArrayList<String> selectedContactGuids = new ArrayList<>();

        for (Contact groupContact : mSelectedContacts) {
            selectedContactGuids.add(groupContact.getAccountGuid());
        }

        Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getContactsActivityClass());

        intent.putExtra(ContactsActivity.EXTRA_MODE, ContactsActivity.MODE_SIMSME_GROUP);
        intent.putExtra(ContactsActivity.EXTRA_MAX_SELECTED_CONTACTS_SIZE, MAX_SELECTABLE_CONTACT_SIZE);
        intent.putStringArrayListExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS_FROM_GROUP, selectedContactGuids);

        if (mChat != null) {
            try {
                intent.putExtra(ContactsActivity.EXTRA_GROUP_CHAT_OWNER_GUID, mChat.getOwner());
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }
        }

        startActivityForResult(intent, REQUEST_CONTACT_SELECT);
    }

    public void handleRemoveMemberClick(View view) {
        try {
            ArrayList<String> selectedContactGuids = new ArrayList<>();

            for (Contact groupContact : mSelectedContacts) {
                selectedContactGuids.add(groupContact.getAccountGuid());
            }

            if (mChat != null) {
                selectedContactGuids.remove(mChat.getOwner());
            }

            String ownGuid = accountController.getAccount().getAccountGuid();
            selectedContactGuids.remove(ownGuid);

            Intent intent = new Intent(this, ChatRoomMemberActivity.class);

            intent.putExtra(ChatRoomMemberActivity.EXTRA_MODE, ChatRoomMemberActivity.EXTRA_MODE_REMOVE);
            intent.putStringArrayListExtra(ContactsActivity.EXTRA_GROUP_CONTACTS, selectedContactGuids);

            startActivityForResult(intent, REQUEST_REMOVE_CONTACT_SELECT);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    public void handleAddAdminClick(View view) {
        ArrayList<String> selectedContactGuids = new ArrayList<>();

        String ownGuid = accountController.getAccount().getAccountGuid();
        for (Contact groupContact : mSelectedContacts) {
            if (!StringUtil.isEqual(ownGuid, groupContact.getAccountGuid())) {
                selectedContactGuids.add(groupContact.getAccountGuid());
            }
        }

        if (mChat != null) {
            try {
                selectedContactGuids.remove(mChat.getOwner());
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }
        }

        Intent intent = new Intent(this, ChatRoomMemberActivity.class);

        intent.putExtra(ChatRoomMemberActivity.EXTRA_MODE, ChatRoomMemberActivity.EXTRA_MODE_ADMIN);
        intent.putStringArrayListExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS_FROM_GROUP, mAdmins);
        intent.putStringArrayListExtra(ContactsActivity.EXTRA_GROUP_CONTACTS, selectedContactGuids);

        startActivityForResult(intent, REQUEST_ADMIN_SELECT);
    }

    public void handleRemoveAdminClick(View view) {
        if (mLongTapContact != null && mSelectedContacts != null) {
            mAdmins.remove(mLongTapContact.getAccountGuid());

            addContactsToList((List<Contact>) mSelectedContacts.clone());
        }

        mLongTapContact = null;
        closeBottomSheet(null);
    }

    public void handleAddAsAdminClick(View view) {
        if (mLongTapContact != null && mSelectedContacts != null) {
            if (!mAdmins.contains(mLongTapContact.getAccountGuid())) {
                mAdmins.add(mLongTapContact.getAccountGuid());
            }

            addContactsToList((List<Contact>) mSelectedContacts.clone());
        }

        mLongTapContact = null;
        closeBottomSheet(null);
    }

    public void handleDeleteMemberClick(View view) {
        if (mLongTapContact != null && mSelectedContacts != null) {
            mSelectedContacts.remove(mLongTapContact);
            mAdmins.remove(mLongTapContact.getAccountGuid());
            addContactsToList((List<Contact>) mSelectedContacts.clone());
            try {
                updateCountLabel();
            } catch (final LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }
        }
        closeBottomSheet(null);
    }

    private void updateCountLabel()
            throws LocalizedException {
        int currentMemberCount = mSelectedContacts.size();
        int maxMembers = preferenceController.getMaximumRoomMembers();
        String label = currentMemberCount + " "
                + getResources().getString(R.string.chat_group_label_membersCount) + " " + maxMembers;

        selectedContactsCountTextView.setText(label);
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CONTACT_SELECT: {
                    try {
                        String[] selectedContactGuids = returnIntent.getStringArrayExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS);
                        ArrayList<Contact> contacts = contactController.getContactsByGuid(selectedContactGuids);

                        if (!ownContactInList(contacts)) {
                            Contact contact = contactController.getOwnContact();
                            contacts.add(0, contact);
                        }

                        ArrayList<String> newAdmins = new ArrayList<>();
                        for (Contact contact : contacts) {
                            if (mAdmins.contains(contact.getAccountGuid())) {
                                newAdmins.add(contact.getAccountGuid());
                            }
                        }

                        mAdmins = newAdmins;

                        if (contacts.size() > 0) {
                            removeButtonContainer.setVisibility(View.VISIBLE);
                        }
                        addContactsToList(contacts);
                        updateCountLabel();
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                    break;
                }
                case REQUEST_ADMIN_SELECT: {
                    try {
                        String[] selectedAdminGuids = returnIntent.getStringArrayExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS);

                        if (selectedAdminGuids != null && selectedAdminGuids.length > 0) {
                            mAdmins = new ArrayList<>(Arrays.asList(selectedAdminGuids));

                            if (!mAdmins.contains(accountController.getAccount().getAccountGuid())) {
                                mAdmins.add(accountController.getAccount().getAccountGuid());
                            }
                        } else {
                            mAdmins.clear();
                            mAdmins.add(accountController.getAccount().getAccountGuid());
                        }

                        addContactsToList((List<Contact>) mSelectedContacts.clone());
                        updateCountLabel();
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }

                    break;
                }
                case REQUEST_REMOVE_CONTACT_SELECT: {
                    try {
                        String[] selectedContactGuids = returnIntent.getStringArrayExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS);
                        if (selectedContactGuids != null && selectedContactGuids.length > 0) {
                            ArrayList<Contact> newList = new ArrayList<>();
                            ArrayList<String> selectedGuids = new ArrayList<>(Arrays.asList(selectedContactGuids));
                            for (Contact contact : mSelectedContacts) {
                                if (!selectedGuids.contains(contact.getAccountGuid())) {
                                    newList.add(contact);
                                }
                            }

                            for (String guid : selectedContactGuids) {
                                mAdmins.remove(guid);
                            }

                            addContactsToList(newList);
                            updateCountLabel();
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }

                    break;
                }
                case RouterConstants.SELECT_GALLERY_RESULT_CODE: {
                    Uri selectedGalleryItem = returnIntent.getData();
                    FileUtil fileUtil = new FileUtil(this);

                    if (!MimeUtil.checkImageUriMimetype(getApplication(), selectedGalleryItem)) {
                        Toast.makeText(this, R.string.chats_addAttachment_wrong_format_or_error, Toast.LENGTH_LONG).show();
                        break;
                    }

                    try {
                        Uri selectedItemIntern = fileUtil.copyFileToInternalDir(selectedGalleryItem);
                        if (selectedItemIntern != null) {
                            router.cropImage(selectedItemIntern.toString());
                        }
                    } catch (LocalizedException e) {
                        Toast.makeText(this, R.string.chats_addAttachments_some_imports_fails, Toast.LENGTH_LONG).show();
                    }
                    break;
                }
                case TAKE_PICTURE_RESULT_CODE: {
                    try {
                        if (takenPhotoFile != null) {
                            final Uri internalUri = (new FileUtil(this)).copyFileToInternalDir(Uri.fromFile(takenPhotoFile));
                            router.cropImage(internalUri.toString());
                        }
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, e.getMessage(), e);
                        Toast.makeText(this, R.string.chats_addAttachments_some_imports_fails, Toast.LENGTH_LONG).show();
                    }
                    break;
                }
                case RouterConstants.ADJUST_PICTURE_RESULT_CODE: {
                    final Bitmap bm = returnIntent.getParcelableExtra(CropImageActivity.RETURN_DATA_AS_BITMAP);
                    if (bm != null) {
                        groupImageAfterEdit = bm;
                        mGroupImageView.setImageBitmap(groupImageAfterEdit);
                        imageBytes = ImageUtil.compress(groupImageAfterEdit, 100);
                    }

                    break;
                }
                default:
                    break;
            }
        }
    }

    private boolean ownContactInList(List<Contact> contacts) {
        boolean foundMe = false;
        String ownGuid = accountController.getAccount().getAccountGuid();
        for (Contact admin : contacts) {
            if (StringUtil.isEqual(admin.getAccountGuid(), ownGuid)) {
                foundMe = true;
                break;
            }
        }

        return foundMe;
    }

    private boolean updateChatRoom()
            throws LocalizedException {
        boolean hasChanged = false;
        final List<Contact> chatMembers = groupChatController.getChatMembers(mChat
                .getMembers());
        final List<Contact> addedMembers = getAddedMembers(chatMembers, mSelectedContacts);
        final List<Contact> removedMembers = getRemovedMembers(chatMembers, mSelectedContacts);

        final JsonArray chatAdmins = mChat.getAdmins();
        final List<String> addedAdmins = getAddedMembers(chatAdmins, mAdmins);
        final List<String> removedAdmins = getRemovedMembers(chatAdmins, mAdmins);

        byte[] newImg = null;
        // A new group image has been chosen
        if(groupImageAfterEdit != null) {
            newImg = imageBytes;
        }

        String newName = null;
        if (!StringUtil.isEqual(mChat.getTitle(), getEnteredName())) {
            newName = getEnteredName();
        }

        if (addedMembers.size() > 0 || removedMembers.size() > 0 || addedAdmins.size() > 0 || removedAdmins.size() > 0) {
            hasChanged = true;
            showIdleDialog(-1);
            groupChatController.updateGroupMember(mChat, addedMembers, removedMembers, addedAdmins,
                    removedAdmins, newImg, newName, new OnUpdateGroupMembersListener() {
                        @Override
                        public void onUpdateGroupMembersSuccess(Chat chat) {
                            dismissIdleDialog();
                            finish();
                        }

                        @Override
                        public void onUpdateGroupMembersFailed(String errorMessage) {
                            dismissIdleDialog();
                            String errorMsg = getString(R.string.err_group_update_failed);

                            if (!StringUtil.isNullOrEmpty(errorMessage)) {
                                errorMsg = errorMsg + "\n\n" + errorMessage;
                            }

                            AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(ChatRoomInfoActivity.this, errorMsg);

                            alert.show();
                        }
                    });
        } else if (newImg != null || !StringUtil.isNullOrEmpty(newName)) {
            hasChanged = true;
            showIdleDialog(-1);
            groupChatController.setGroupInfo(mChat, newName, newImg, new OnSetGroupInfoListener() {
                @Override
                public void onSetGroupInfoSuccess(Chat chat) {
                    dismissIdleDialog();
                    finish();
                }

                @Override
                public void onSetGroupInfoFailed(String errorMessage) {
                    dismissIdleDialog();
                    String errorMsg = getString(R.string.err_group_set_info_failed);

                    if (!StringUtil.isNullOrEmpty(errorMessage)) {
                        errorMsg = errorMsg + "\n\n" + errorMessage;
                    }

                    AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(ChatRoomInfoActivity.this, errorMsg);

                    alert.show();
                }
            });
        }
        return hasChanged;
    }

    private @NonNull
    List<Contact> getRemovedMembers(List<Contact> chatMembers, List<Contact> contacts) {
        List<Contact> removedMembers = new ArrayList<>();

        for (Contact contact : chatMembers) {
            if (!contacts.contains(contact)) {
                removedMembers.add(contact);
            }
        }
        return removedMembers;
    }

    private @NonNull
    List<String> getAddedMembers(JsonArray chatMember, List<String> newMembers) {
        List<String> members = new ArrayList<>();

        for (int i = 0; i < chatMember.size(); i++) {
            members.add(chatMember.get(i).getAsString());
        }

        List<String> result = new ArrayList<>();

        for (String newMember : newMembers) {
            if (!members.contains(newMember)) {
                result.add(newMember);
            }
        }

        return result;
    }

    private @NonNull
    List<String> getRemovedMembers(JsonArray chatMember, List<String> newMembers) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < chatMember.size(); i++) {
            String guid = chatMember.get(i).getAsString();

            if (!newMembers.contains(guid)) {
                result.add(guid);
            }
        }

        return result;
    }

    private @NonNull
    List<Contact> getAddedMembers(List<Contact> chatMembers, List<Contact> contacts) {
        List<Contact> addedMembers = new ArrayList<>();

        for (Contact contact : contacts) {
            if (!chatMembers.contains(contact)) {
                addedMembers.add(contact);
            }
        }
        return addedMembers;
    }

    private void fillSelectedContactsWithChatMembers()
            throws LocalizedException {
        List<Contact> chatMembers = null;

        fillAdminsFromChat();

        if (mode == MODE_CREATE) {
            Contact contact = contactController.getOwnContact();
            chatMembers = new ArrayList<>();
            chatMembers.add(contact);
        } else if (mChat != null) {
            chatMembers = groupChatController.getChatMembers(mChat.getMembers());
        }

        if (chatMembers != null) {
            addContactsToList(chatMembers);
        }
    }

    private void fillAdminsFromChat()
            throws LocalizedException {
        if (mode == MODE_CREATE) {
            mAdmins.add(accountController.getAccount().getAccountGuid());
        } else if (mChat != null) {
            JsonArray chatAdmins = mChat.getAdmins();
            for (int i = 0; i < chatAdmins.size(); i++) {
                mAdmins.add(chatAdmins.get(i).getAsString());
            }
        }
    }

    private void addContactsToList(@NonNull List<Contact> contacts) {
        mSelectedContacts.clear();
        selectedContactsAdapter.clear();
        selectedAdminsAdapter.clear();

        mSelectedContacts = new ArrayList<>(ContactUtil.sortContactsByMandantPriority(contacts, preferenceController));

        for (Contact contact : mSelectedContacts) {
            if (mAdmins.contains(contact.getAccountGuid())) {
                if (selectedAdminsAdapter.getPosition(contact) == -1) {
                    selectedAdminsAdapter.add(contact);
                }
            } else if (selectedContactsAdapter.getPosition(contact) == -1) {
                getSimsMeApplication().getContactController().addLastUsedCompanyContact(contact);
                selectedContactsAdapter.add(contact);
            }
        }

        refreshListViews();

        final ScreenDesignUtil screenDesignUtil = ScreenDesignUtil.getInstance();
        final int accentColor = screenDesignUtil.getAppAccentColor(getSimsMeApplication());
        //wenn mehr Nutzer als der Owner in der Gruppe sind
        if (mSelectedContacts.size() > 1) {
            //dann button enable
            addAdminButtonContainer.setEnabled(true);
            addAdminButtonTextView.setEnabled(true);
            addAdminButtonTextView.setTextColor(accentColor);
            addAdminButtonTextView.setAlpha(1.0f);
        }
        //wenn nur group owner in der Gruppe ist
        else {
            //dann Admin verwalten Button disablen
            addAdminButtonContainer.setEnabled(false);
            addAdminButtonTextView.setEnabled(false);
            addAdminButtonTextView.setTextColor(accentColor);
            addAdminButtonTextView.setAlpha(0.3f);
        }
    }

    private void refreshListViews() {
        if(mode != MODE_RESTRICTED) {
            fillContactList(selectedContactsAdapter, selectedContactsListItemContainer, selectedContactsListContainer, false);
        }
        fillContactList(selectedAdminsAdapter, selectedAdminsListItemContainer, selectedAdminsListContainer, true);
    }

    private void fillContactList(ContactsAdapter adapter, LinearLayout listItemContainer, LinearLayout listContainer, boolean isAdminList) {
        listItemContainer.removeAllViews();

        if (adapter.getCount() > 0) {
            listContainer.setVisibility(View.VISIBLE);
        } else {
            listContainer.setVisibility(View.GONE);
        }

        for (int i = 0; i < adapter.getCount(); i++) {
            View itemView = adapter.getView(i, null, listItemContainer);

            itemView.setTag(new String[]{"" + i, isAdminList ? "admin" : "member"});

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            listItemContainer.addView(itemView);
        }
    }

    @Override
    public void onBuildChatRoomSuccess(@NonNull final String chatGuid, @NonNull final String warning) {
        dismissIdleDialog();

        if (!StringUtil.isNullOrEmpty(warning)) {
            DialogBuilderUtil.buildErrorDialog(this, warning, -1, new DialogBuilderUtil.OnCloseListener() {
                @Override
                public void onClose(int ref) {
                    startGroupChatActivity(chatGuid);
                }
            }).show();
        } else {
            startGroupChatActivity(chatGuid);
        }
    }

    private void startGroupChatActivity(final String chatGuid) {
        Intent intent = new Intent(this, GroupChatActivity.class);

        intent.putExtra(GroupChatActivity.EXTRA_TARGET_GUID, chatGuid);
        startActivity(intent);

        finish();

        isRoomDuringCreation = false;
    }

    @Override
    public void onBuildChatRoomFail(@NonNull final String errorDetailMsg) {
        dismissIdleDialog();
        isRoomDuringCreation = false;

        String errorMsg = getString(R.string.err_group_created_failed);

        if (!StringUtil.isNullOrEmpty(errorDetailMsg)) {
            errorMsg = errorMsg + "\n\n" + errorDetailMsg;
        }

        AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(ChatRoomInfoActivity.this, errorMsg);

        alert.show();
    }

    @Override
    public void onClick(View view) {
        final String[] tag = (String[]) view.getTag();
        if (tag == null || tag.length != 2) {
            return;
        }

        final int position = Integer.parseInt(tag[0]);
        final String type = tag[1];

        Contact selectedContact = null;
        if (StringUtil.isEqual(type, "member")) {
            selectedContact = selectedContactsAdapter.getItem(position);
        } else if (StringUtil.isEqual(type, "admin")) {
            selectedContact = selectedAdminsAdapter.getItem(position);
        }

        if (selectedContact == null) {
            return;
        }

        if (accountController.getAccount().getAccountGuid().equals(selectedContact.getAccountGuid())) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            mContactDetailsStarted = true;
        } else {
            final Intent intent = contactController.getOpenContactInfoIntent(this, selectedContact);
            if (intent != null) {
                startActivity(intent);
                mContactDetailsStarted = true;
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        try {
            if (mode == MODE_INFO || mode == MODE_RESTRICTED) {
                return false;
            }
            closeEmojis();

            if (!mBottomSheetMoving) {
                final String[] tag = (String[]) v.getTag();
                if (tag == null || tag.length != 2) {
                    return false;
                }

                final int position = Integer.parseInt(tag[0]);
                final String type = tag[1];

                mLongTapContact = null;
                if (StringUtil.isEqual(type, "member")) {
                    mLongTapContact = selectedContactsAdapter.getItem(position);
                    openBottomSheet(R.layout.dialog_group_context_menu_add_delete_layout, R.id.chat_room_info_linear_layout_fragment_container);
                } else if (StringUtil.isEqual(type, "admin")) {
                    mLongTapContact = selectedAdminsAdapter.getItem(position);
                    String ownGuid = accountController.getAccount().getAccountGuid();
                    // Wenn der Chat noch nicht gespeichert wurde dann ist mChat noch null --> dann ist ownGuid automatisch der Owner
                    if (mLongTapContact != null
                            && !StringUtil.isEqual(mLongTapContact.getAccountGuid(), ownGuid)
                            && (mChat == null || !StringUtil.isEqual(mChat.getOwner(), mLongTapContact.getAccountGuid()))) {
                        openBottomSheet(R.layout.dialog_group_context_menu_remove_admin_layout, R.id.chat_room_info_linear_layout_fragment_container);
                    }
                }

                if (mChatRoomNameEditText.hasFocus()) {
                    KeyboardUtil.toggleSoftInputKeyboard(ChatRoomInfoActivity.this, mChatRoomNameEditText, false);
                }

                return true;
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }

        return false;
    }

    private void initEmojiButtonListener() {
        OnCheckedChangeListener listener = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (mAddEmojiNicknameButton.isChecked()) {
                    if (!mEmojiFragmentVisible) {
                        final Handler handler = new Handler();
                        final Runnable runnable = new Runnable() {
                            public void run() {
                                mEmojiFragment = new EmojiPickerFragment();
                                FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiFragment,
                                        R.id.chat_room_info_frame_layout_emoji_container, true);
                                mEmojiFragmentVisible = true;

                                RelativeLayout mainLayout = findViewById(R.id.main_layout);
                                ScrollView scrollView = findViewById(R.id.main_scrollview);

                                mEmojiContainer = findViewById(R.id.chat_room_info_frame_layout_emoji_container);

                                resizeLayout(scrollView, mainLayout, mEmojiContainer);
                            }
                        };
                        handler.postDelayed(runnable, ANIMATION_DURATION);
                    }

                    KeyboardUtil.toggleSoftInputKeyboard(ChatRoomInfoActivity.this, mChatRoomNameEditText, false);
                } else {
                    closeEmojis();
                }
            }
        };

        mAddEmojiNicknameButton.setOnCheckedChangeListener(listener);
    }

    private void resizeLayout(final View layoutToResize,
                              final View mainLayout,
                              final View occludingLayout) {
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            public void run() {
                layoutToResize.getLayoutParams().height = mainLayout.getMeasuredHeight()
                        - occludingLayout.getMeasuredHeight();
                layoutToResize.requestLayout();
            }
        };
        handler.postDelayed(runnable, 500);
    }

    @Override
    public void onEmojiSelected(@NotNull String unicode) {
        TextExtensionsKt.appendText(mChatRoomNameEditText, unicode);
    }

    @Override
    public void onBackSpaceSelected() {
        TextExtensionsKt.backspace(mChatRoomNameEditText);
    }

    private void initEmojiFieldListener() {
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAddEmojiNicknameButton.setChecked(false);
                closeEmojis();
            }
        };

        OnFocusChangeListener focusChangeListener = new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v,
                                      boolean hasFocus) {
                if (hasFocus) {
                    mAddEmojiNicknameButton.setChecked(false);
                    closeEmojis();
                }
            }
        };

        mChatRoomNameEditText.setOnFocusChangeListener(focusChangeListener);
        mChatRoomNameEditText.setOnClickListener(clickListener);
    }

    private void closeEmojis() {
        /* TODO in base Activity auslagern
         * liste von emoji-buttons
         *
         */
        if (mEmojiFragmentVisible) {
            mAddEmojiNicknameButton.setChecked(false);
            FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiFragment,
                    R.id.init_profile_frame_layout_emoji_container, false);
            mEmojiFragmentVisible = false;

            ScrollView scrollView = findViewById(R.id.main_scrollview);

            scrollView.getLayoutParams().height = -1;
            scrollView.requestLayout();
        }
    }

    @Override
    public void onBackPressed() {
        if (mBottomSheetOpen) {
            closeBottomSheet(null);
            return;
        }
        if (mEmojiFragmentVisible) {
            closeEmojis();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mEmojiContainer != null) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mEmojiContainer.getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_landscape);
            } else {
                mEmojiContainer.getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_portrait);
            }
        }
    }

    public void onMuteClicked(final View view) {
        final Intent intent = new Intent(ChatRoomInfoActivity.this, MuteChatActivity.class);
        intent.putExtra(MuteChatActivity.EXTRA_CHAT_GUID, mChatGuid);
        startActivity(intent);
    }

    public void handleClearChatClick(final View v) {
        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {

                if (mChat != null) {
                    showIdleDialog();
                    getSimsMeApplication().getGroupChatController().clearChat(mChat, new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            dismissIdleDialog();
                            if (!StringUtil.isNullOrEmpty(mChatGuid)) {
                                getSimsMeApplication().getChatOverviewController().chatChanged(null, mChatGuid, null,
                                        ChatOverviewController.CHAT_CHANGED_CLEAR_CHAT);
                            }
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(ChatRoomInfoActivity.this,
                                    message).show();
                        }
                    });
                }
            }
        };

        final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
            }
        };

        final String title = getResources().getString(R.string.chats_clear_chat);
        final String positiveButton = getResources().getString(R.string.chats_clear_chat);
        final String message = getResources().getString(R.string.chat_button_clear_confirm_group);

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

    public void onAnnouncementGroupClicked(View view) {
        final TextView announcementGroupStatus = findViewById(R.id.chat_room_info_announcement_group_status);
        if(mIsAnnouncementGroup) {
            mIsAnnouncementGroup = false;
            announcementGroupStatus.setText(getText(R.string.chat_announcement_group_off));
        } else {
            mIsAnnouncementGroup = true;
            announcementGroupStatus.setText(getText(R.string.chat_announcement_group_on));
        }
    }
}
