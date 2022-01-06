// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.chat;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.jetbrains.annotations.NotNull;
import eu.ginlo_apps.ginlo.ForwardActivityBase;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.TextExtensionsKt;
import eu.ginlo_apps.ginlo.activity.chat.ChatInputActivity;
import eu.ginlo_apps.ginlo.activity.chat.PreviewActivity;
import eu.ginlo_apps.ginlo.adapter.ChatAdapter;
import eu.ginlo_apps.ginlo.adapter.ContactsAdapter;
import eu.ginlo_apps.ginlo.concurrent.task.ConvertToChatItemVOTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnMessageReceiverChangedListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.ContactMessageInfo;
import eu.ginlo_apps.ginlo.model.backend.MessageReceiverModel;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.FileChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ImageChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.LocationChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VideoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VoiceChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class MessageDetailsActivity extends ChatInputActivity
        implements ContactController.OnContactProfileInfoChangeNotification,
        EmojiPickerCallback,
        OnMessageReceiverChangedListener {
    public final static String MESSAGE_ID = "MessageDetailsActivity.MessageId";
    public final static String CHAT_GUID = "MessageDetailsActivity.ChatGuid";
    public final static String EXTRA_RETURN_TYPE = "MessageDetailsActivity.ExtraReturnType";
    public final static String EXTRA_RETURN_ACTION = "MessageDetailsActivity.ExtraReturnAction";
    public final static String EXTRA_RETURN_ACTION_DELETE_MSG = "MessageDetailsActivity.ExtraDeleteMsg";
    public final static String EXTRA_RETURN_ACTION_FORWARD_MSG = "MessageDetailsActivity.ExtraForwardMSG";
    public final static String EXTRA_RETURN_ACTION_FORWARD_IMAGE = "MessageDetailsActivity.ExtraForwardImage";
    public final static String EXTRA_RETURN_TEXT = "MessageDetailsActivity.ExtraReturnText";
    public static final String EXTRA_RETURN_IMAGE_URIS = "MessageDetailsActivity.ExtraReturnImage";
    public static final String EXTRA_RETURN_IMAGE_TEXTS = "MessageDetailsActivity.ExtraReturnImageTexts";
    public final static String EXTRA_RETURN_IS_PRIORITY = "MessageDetailsActivity.ExtraReturnIsPriority";
    public final static String EXTRA_MIN_CHATITEM_HEIGHT = "MessageDetailsActivity.ExtraMinChatItemHeigt";

    private static final String TAG = MessageDetailsActivity.class.getSimpleName();

    private ListView listViewMessage;
    private ListView listViewContacts;
    private ContactsAdapter mContactsAdapter;
    private ImageLoader mImageLoader;
    private long mMessageId = 0;
    private String mChatGuid;
    private int mTrustState;
    private int mMinChatitemHeight;
    private boolean mContactIsBlocked;
    private Context mContext;

    // return value for image capture intent, since adding it as an extra
    // doesn't work with EXTRA_OUTPUT
    private Uri mTakePhotoUri;

    private AsyncTask<Void, Void, ArrayList<Contact>> mRefreshTask;
    private ProgressBar progressBar;
    @Inject
    Router router;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);

            SimsMeApplication application = getSimsMeApplication();
            mContext = application.getApplicationContext();

            mAccountController = application.getAccountController();
            mContactController = application.getContactController();
            mPreferencesController = application.getPreferencesController();

            mMessageController = application.getMessageController();

            final Intent callerIntent = getIntent();
            if (callerIntent.hasExtra(EXTRA_MIN_CHATITEM_HEIGHT)) {
                mMinChatitemHeight = callerIntent.getIntExtra(EXTRA_MIN_CHATITEM_HEIGHT, 0);
            }

            if (callerIntent.hasExtra(MESSAGE_ID) && callerIntent.hasExtra(CHAT_GUID)) {
                mMessageId = callerIntent.getLongExtra(MESSAGE_ID, 0);
                mChatGuid = callerIntent.getStringExtra(CHAT_GUID);

                fillViews();

                final Message message = mMessageController.getMessageById(mMessageId);
                if (message == null) {
                    //safety check here, if message for whatever reason does not exist , just close the view
                    finish();
                    return;
                }

                final ArrayList<BaseChatItemVO> chatItems = new ArrayList<>();

                mCitatedChatItem = ConvertToChatItemVOTask.getChatItemVO(message, application, application.getGroupChatController(), application.getChannelController(), mTrustState);
                // damit die Methodne der Basisklassen nicht kopiert werden muessen halten wir das Item doppelt
                mMarkedChatItem = mCitatedChatItem;
                chatItems.add(mCitatedChatItem);

                mChatAdapter = new ChatAdapter(this, application,
                        application.getTaskManagerController(),
                        R.layout.chat_item_image_right_layout,
                        chatItems,
                        mChatGuid,
                        mContext);
                listViewMessage.setAdapter(mChatAdapter);
                listViewMessage.setOnItemClickListener(this);
                setDynamicHeight(listViewMessage, mMinChatitemHeight);

                if (!StringUtil.isNullOrEmpty(mChatGuid)) {
                    mContactIsBlocked = false;
                    if (GuidUtil.isChatSingle(mChatGuid)) {
                        Contact mContact = mContactController.getContactByGuid(mChatGuid);
                        if (mContact != null) {
                            mContactIsBlocked = mContact.getIsBlocked() != null ? mContact.getIsBlocked() : mContact.getTempReadonly();
                        }
                    }
                }

                if (!(mMarkedChatItem instanceof SelfDestructionChatItemVO) && !mContactIsBlocked) {
                    mFragmentContainerId = R.id.message_info_fragment_container;
                    mChatInputContainerId = R.id.message_info_chat_input_fragment_placeholder;
                    mChatInputFragment = new ChatInputFragment();
                    getSupportFragmentManager().beginTransaction()
                            .add(mChatInputContainerId, mChatInputFragment).commit();
                    mChatInputFragment.setSimpleUi(false);
                    mChatInputFragment.hideAudioRecord();

                    final int slideheigth_six_lines = (int) getResources().getDimension(R.dimen.chat_slideheight_six_lines);
                    mAnimationSlideIn = new TranslateAnimation(0, 0, slideheigth_six_lines, 0);
                    mAnimationSlideOut = new TranslateAnimation(0, 0, 0, slideheigth_six_lines);

                    // emoji hinzufuegen und hiden, dmait es einen View und damit eine Hoehe hat
                    if (mEmojiconsFragment == null) {
                        mEmojiconsFragment = new EmojiPickerFragment();
                        getSupportFragmentManager().beginTransaction()
                                .add(mFragmentContainerId, mEmojiconsFragment).commit();
                    }
                    hideOrShowFragment(mEmojiconsFragment, false, false);
                }

                final List<ToolbarOptionsItemModel> toolbarOptionsItemModels = new ArrayList<>();

                if (mCitatedChatItem instanceof TextChatItemVO) {
                    toolbarOptionsItemModels.add(createToolbarOptionsForwardModel());
                    toolbarOptionsItemModels.add(createToolbarOptionsCopyModel());
                    toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                } else if ((mCitatedChatItem instanceof ImageChatItemVO) || (mCitatedChatItem instanceof VideoChatItemVO)
                        || (mCitatedChatItem instanceof FileChatItemVO)) {
                    toolbarOptionsItemModels.add(createToolbarOptionsForwardModel());
                    toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                } else if (mCitatedChatItem instanceof VoiceChatItemVO || mCitatedChatItem instanceof SelfDestructionChatItemVO || mCitatedChatItem instanceof LocationChatItemVO) {
                    toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                } else {
                    toolbarOptionsItemModels.add(createToolbarOptionsDeleteModel());
                }

                showToolbarOptions(toolbarOptionsItemModels);

                mMessageController.addOnMessageReceiverChangedListener(this);
            } else {
                finish();
            }
            final int slideheigth_one_line = (int) getResources().getDimension(R.dimen.chat_slideheight_one_line);
            final int slideheigth_two_lines = (int) getResources().getDimension(R.dimen.chat_slideheight_two_lines);
            final int slideheigth_three_lines = (int) getResources().getDimension(R.dimen.chat_slideheight_three_lines);
            final int slideheigth_six_lines = (int) getResources().getDimension(R.dimen.chat_slideheight_six_lines);
            final int slideheigth_seven_lines = (int) getResources().getDimension(R.dimen.chat_slideheight_seven_lines);

            mAnimationSlideInOneLine = new TranslateAnimation(0, 0, slideheigth_one_line, 0);
            mAnimationSlideOutOneLine = new TranslateAnimation(0, 0, 0, slideheigth_one_line);
            mAnimationSlideInTwoLines = new TranslateAnimation(0, 0, slideheigth_two_lines, 0);
            mAnimationSlideOutTwoLines = new TranslateAnimation(0, 0, 0, slideheigth_two_lines);
            mAnimationSlideInThreeLines = new TranslateAnimation(0, 0, slideheigth_three_lines, 0);
            mAnimationSlideOutThreeLines = new TranslateAnimation(0, 0, 0, slideheigth_three_lines);
            mAnimationSlideInSixLines = new TranslateAnimation(0, 0, slideheigth_six_lines, 0);
            mAnimationSlideOutSixLines = new TranslateAnimation(0, 0, 0, slideheigth_six_lines);
            mAnimationSlideInSevenLines = new TranslateAnimation(0, 0, slideheigth_seven_lines, 0);
            mAnimationSlideOutSevenLines = new TranslateAnimation(0, 0, 0, slideheigth_seven_lines);

            mAnimationSlideInOneLine.setDuration(ANIMATION_DURATION);
            mAnimationSlideOutOneLine.setDuration(ANIMATION_DURATION);
            mAnimationSlideInTwoLines.setDuration(ANIMATION_DURATION);
            mAnimationSlideOutTwoLines.setDuration(ANIMATION_DURATION);
            mAnimationSlideInThreeLines.setDuration(ANIMATION_DURATION);
            mAnimationSlideOutThreeLines.setDuration(ANIMATION_DURATION);
            mAnimationSlideInSixLines.setDuration(ANIMATION_DURATION);
            mAnimationSlideOutSixLines.setDuration(ANIMATION_DURATION);
            mAnimationSlideOutSevenLines.setDuration(ANIMATION_DURATION);

            mShowSimpleFab = true;
            resetChatInputFabButton();
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "Error creating activity");
            finish();
        }
    }

    @Override
    public void finish() {
        if (mChatInputFragment != null) {
            KeyboardUtil.toggleSoftInputKeyboard(this, mChatInputFragment.getEditText(), false);
        }
        super.finish();
    }

    private void fillViews() {
        SimsMeApplication application = (SimsMeApplication) getApplication();

        if (mMessageId != 0 && mChatGuid != null) {
            final Message message = mMessageController.getMessageById(mMessageId);
            final Chat chat = getChatController().getChatByGuid(mChatGuid);

            if (message == null || chat == null) {
                finish();
                return;
            }

            try {
                final long dateSent = message.getDateSend() == null ? 0 : message.getDateSend();

                //FIXME groupchatcontroller bei singlechat?!
                final List<Contact> chatMembers = application.getGroupChatController().getChatMembers(chat.getMembers());

                listViewMessage = findViewById(R.id.message_info_listview_message);

                listViewContacts = findViewById(R.id.message_info_listview_contacts);

                progressBar = findViewById(R.id.message_info_progress);

                final View layoutRead = findViewById(R.id.message_info_read_layout);
                final View layoutDownloaded = findViewById(R.id.message_info_downloaded_layout);
                final View layoutSent = findViewById(R.id.message_info_sent_layout);

                mTrustState = Contact.STATE_HIGH_TRUST;

                final Map<String, MessageReceiverModel> receivers = message.getReceivers(getSimsMeApplication());
                if (receivers == null || receivers.size() == 0) {
                    listViewContacts.setVisibility(View.GONE);

                    long dateDownloaded = message.getDateDownloaded() == null ? 0 : message.getDateDownloaded();
                    long dateRead = message.getDateRead() == null ? 0 : message.getDateRead();

                    final String today = DateUtil.getDateStringFromMillis(new Date().getTime());

                    if (dateSent != 0) {
                        layoutSent.setVisibility(View.VISIBLE);
                        final TextView timeText = findViewById(R.id.message_info_textview_sent_time);
                        final TextView dateText = findViewById(R.id.message_info_textview_sent_date);

                        timeText.setText(DateUtil.getTimeStringFromMillis(dateSent));

                        final String date = DateUtil.getDateStringFromMillis(dateSent);
                        if (StringUtil.isEqual(today, date)) {
                            dateText.setText(getResources().getString(R.string.chat_overview_date_today));
                        } else {
                            dateText.setText(date);
                        }
                    } else {
                        layoutSent.setVisibility(View.GONE);
                    }

                    if (dateDownloaded != 0) {
                        layoutDownloaded.setVisibility(View.VISIBLE);
                        final TextView timeText = findViewById(R.id.message_info_textview_downloaded_time);
                        final TextView dateText = findViewById(R.id.message_info_textview_downloaded_date);

                        timeText.setText(DateUtil.getTimeStringFromMillis(dateDownloaded));

                        final String date = DateUtil.getDateStringFromMillis(dateDownloaded);
                        if (StringUtil.isEqual(today, date)) {
                            dateText.setText(getResources().getString(R.string.chat_overview_date_today));
                        } else {
                            dateText.setText(date);
                        }
                    } else {
                        layoutDownloaded.setVisibility(View.GONE);
                    }

                    if (dateRead != 0) {
                        layoutRead.setVisibility(View.VISIBLE);
                        final TextView timeText = findViewById(R.id.message_info_textview_read_time);
                        final TextView dateText = findViewById(R.id.message_info_textview_read_date);

                        timeText.setText(DateUtil.getTimeStringFromMillis(dateRead));

                        final String date = DateUtil.getDateStringFromMillis(dateRead);
                        if (StringUtil.isEqual(today, date)) {
                            dateText.setText(getResources().getString(R.string.chat_overview_date_today));
                        } else {
                            dateText.setText(date);
                        }
                    } else {
                        layoutRead.setVisibility(View.GONE);
                    }
                } else {
                    if (mRefreshTask == null) {
                        layoutRead.setVisibility(View.GONE);
                        layoutDownloaded.setVisibility(View.GONE);
                        layoutSent.setVisibility(View.GONE);

                        progressBar.setVisibility(View.VISIBLE);
                        listViewContacts.setVisibility(View.GONE);

                        mRefreshTask = new AsyncTask<Void, Void, ArrayList<Contact>>() {
                            @Override
                            protected ArrayList<Contact> doInBackground(Void... params) {
                                try {
                                    final Map<String, Boolean> guidsAndStates = new HashMap<>();

                                    for (MessageReceiverModel model : receivers.values()) {
                                        Boolean state;
                                        if (model.dateRead != 0) {
                                            state = true;
                                        } else if (model.dateDownloaded != 0) {
                                            state = false;
                                        } else {
                                            state = null;
                                        }
                                        guidsAndStates.put(model.guid, state);
                                    }

                                    //final String senderguid = accountController.getAccount().getAccountGuid();
                                    final String senderguid = message.getFrom();
                                    List<ContactMessageInfo> contactsSent = new ArrayList<>();
                                    List<ContactMessageInfo> contactsRead = new ArrayList<>();
                                    List<ContactMessageInfo> contactsDelivered = new ArrayList<>();

                                    for (Contact contact : chatMembers) {
                                        String contactGuid = contact.getAccountGuid();
                                        if (StringUtil.isEqual(senderguid, contactGuid)) {
                                            continue;
                                        }

                                        MessageReceiverModel model = receivers.get(contactGuid);

                                        if (model == null) {
                                            boolean isFirst = contactsSent.size() == 0;

                                            contactsSent.add(new ContactMessageInfo(contact, dateSent, 0, 0, isFirst));
                                            continue;
                                        }

                                        long dateRead = model.dateRead;
                                        long dateDownloaded = model.dateDownloaded;

                                        mTrustState = Math.min(mTrustState, contact.getState());
                                        if (guidsAndStates.size() == 0) {
                                            boolean isFirst = contactsSent.size() == 0;

                                            contactsSent.add(new ContactMessageInfo(contact, dateSent, dateDownloaded, dateRead, isFirst));
                                        } else {
                                            final Boolean state = guidsAndStates.get(contactGuid);

                                            if (state == null) {
                                                boolean isFirst = contactsSent.size() == 0;

                                                contactsSent.add(new ContactMessageInfo(contact, dateSent, dateDownloaded, dateRead, isFirst));
                                            } else if (!state) {
                                                boolean isFirst = contactsDelivered.size() == 0;
                                                contactsDelivered.add(new ContactMessageInfo(contact, dateSent, dateDownloaded, dateRead, isFirst));
                                            } else //if (state == true)
                                            {
                                                boolean isFirst = contactsRead.size() == 0;
                                                contactsRead.add(new ContactMessageInfo(contact, dateSent, dateDownloaded, dateRead, isFirst));
                                            }
                                        }
                                    }

                                    final ArrayList<Contact> contacts = new ArrayList<>();
                                    contacts.addAll(contactsRead);
                                    contacts.addAll(contactsDelivered);
                                    contacts.addAll(contactsSent);

                                    return contacts;
                                } catch (LocalizedException e) {
                                    LogUtil.e(TAG, "async task: Error: " + e.getMessage(), e);
                                }

                                return null;
                            }

                            @Override
                            protected void onPostExecute(ArrayList<Contact> result) {
                                try {
                                    if (MessageDetailsActivity.this.isFinishing() || MessageDetailsActivity.this.mFinished) {
                                        return;
                                    }

                                    progressBar.setVisibility(View.GONE);
                                    listViewContacts.setVisibility(View.VISIBLE);

                                    if (result != null) {
                                        mContactsAdapter = new ContactsAdapter(MessageDetailsActivity.this, R.layout.contact_item_message_info_layout, result, false, true);

                                        OnItemClickListener onItemClickListener = new OnItemClickListener() {
                                            @Override
                                            public void onItemClick(AdapterView<?> parent,
                                                                    View view,
                                                                    int position,
                                                                    long id) {
                                                final Contact clickedContact = (Contact) parent.getAdapter().getItem(position);
                                                final Intent intent = mContactController.getOpenContactInfoIntent(MessageDetailsActivity.this, clickedContact);
                                                if (intent != null) {
                                                    startActivity(intent);
                                                }
                                            }
                                        };
                                        listViewContacts.setOnItemClickListener(onItemClickListener);

                                        int diameter = (int) getResources().getDimension(R.dimen.contact_item_single_select_icon_diameter);
                                        if (mImageLoader == null) {
                                            mImageLoader = initImageLoader(((SimsMeApplication) getApplication()).getChatImageController(), diameter);
                                        }

                                        mContactsAdapter.setImageLoader(mImageLoader);
                                        listViewContacts.setAdapter(mContactsAdapter);

                                        setDynamicHeight(listViewContacts, 0);
                                    }
                                } catch (LocalizedException e) {
                                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                                } finally {
                                    mRefreshTask = null;
                                }
                            }
                        };

                        mRefreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    }
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, "Error refreshing activity");
                finish();
            }
        }
    }

    @Override
    public BaseChatItemVO getCitatedChatItem() {
        // return null, damit das Chatitem nicht doppelt (listeview und footer) angezeigt wird
        // mcitatetchatitem muss abe rgesetzt werdne, falls wir ein bild machen, damit in der PreviewActivity der Zitatmodus korrekt ausgefuehrt wird
        return null;
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_message_details;
    }

    @Override
    protected void onResumeActivity() {
        mChatAdapter.notifyDataSetChanged();
    }

    @Override
    protected void hideToolbarOptions() {
        //Hier nicht hiden
    }

    @Override
    public void handleForwardMessageClick(View view) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RETURN_ACTION, EXTRA_RETURN_ACTION_FORWARD_MSG);
        intent.putExtra(ForwardActivityBase.EXTRA_MESSAGE_ID, mMarkedChatItem.messageId);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void handleForwardMessageImageClick(View view) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RETURN_ACTION, EXTRA_RETURN_ACTION_FORWARD_IMAGE);
        intent.putExtra(ForwardActivityBase.EXTRA_MESSAGE_ID, mMarkedChatItem.messageId);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void handleDeleteMessageClick(View view) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RETURN_ACTION, EXTRA_RETURN_ACTION_DELETE_MSG);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void handleMessageInfoClick() {
        //do nothing
    }

    @Override
    public void handleMessageCommentClick() {

    }

    @Override
    public void handleAddAttachmentClick() {
        final List<Integer> disabledCommands = new ArrayList<>();
        disabledCommands.add(R.id.attachment_selection_take_video);
        disabledCommands.add(R.id.attachment_selection_attach_video);
        disabledCommands.add(R.id.attachment_selection_attach_location);
        disabledCommands.add(R.id.attachment_selection_send_contact);

        if (getSimsMeApplication().getPreferencesController().isCameraDisabled()) {
            disabledCommands.add(R.id.attachment_selection_take_foto);
            disabledCommands.add(R.id.attachment_selection_take_video);
        }

        openBottomSheet(R.layout.dialog_attachment_selection_layout, R.id.chat_bottom_sheet_container, disabledCommands);
    }

    @Override
    public void scrollIfLastChatItemIsNotShown() {

    }

    @Override
    public void handleSendVoiceClick(Uri voiceUri) {

    }

    @Override
    public boolean handleSendMessageClick(String text) {

        Intent intent = new Intent();
        intent.putExtra(EXTRA_RETURN_TYPE, MimeType.TEXT_PLAIN);
        intent.putExtra(EXTRA_RETURN_TEXT, mChatInputFragment.getChatInputText());
        intent.putExtra(EXTRA_RETURN_IS_PRIORITY, mIsPriority);

        setResult(RESULT_OK, intent);
        finish();

        return true;
    }

    // KS: AVC - TEST!
    @Override
    public boolean handleAVCMessageClick(int callType) {
        return true;
    }

    public void handleTakePhotoClick(View view) {
        if (getSimsMeApplication().getPreferencesController().isCameraDisabled()) {
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_camera_access_not_allowed), Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera,
                new PermissionUtil.PermissionResultCallback() {
                    @Override
                    public void permissionResult(int permission,
                                                 boolean permissionGranted) {
                        if ((permission == PermissionUtil.PERMISSION_FOR_CAMERA) && permissionGranted) {
                            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                            try {
                                FileUtil fu = new FileUtil(getSimsMeApplication());
                                File takenPhotoFile = fu.createTmpImageFileAddInIntent(intent);
                                mTakePhotoUri = Uri.fromFile(takenPhotoFile);

                                closeBottomSheet(mOnBottomSheetClosedListener);
                                router.startExternalActivityForResult(intent, RouterConstants.TAKE_PHOTO_RESULT_CODE);
                            } catch (LocalizedException e) {
                                LogUtil.w(TAG, e.getMessage(), e);
                            }
                        }
                    }
                });
    }

    public void handleAttachPhotoClick(View view) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    new PermissionUtil.PermissionResultCallback() {
                        @Override
                        public void permissionResult(int permission,
                                                     boolean permissionGranted) {
                            if ((permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE)
                                    && permissionGranted) {
                                startAttachPhotoIntent();
                            }
                        }
                    });
        } else {
            startAttachPhotoIntent();
        }
    }

    private void startAttachPhotoIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        intent.setType("image/*");

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        closeBottomSheet(mOnBottomSheetClosedListener);
        router.startExternalActivityForResult(intent, RouterConstants.SELECT_PHOTO_RESULT_CODE);
    }

    private void handleResult(Intent returnIntent,
                              boolean handleImageResult) {
        FileUtil fileUtil = new FileUtil(this);
        FileUtil.UrisResultContainer resultContainer;

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
            Intent intent = new Intent(this, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.class);

            if (handleImageResult) {
                intent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_PREVIEW_ACTION, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.SELECT_PHOTOS_ACTION);
                intent.putStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS, uris);
                startActivityForResult(intent, RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE);
            }
        }

        if (uris.size() > eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.MAX_MEDIA_ITEMS) {
            Toast.makeText(this, getString(R.string.chats_addAttachments_too_many), Toast.LENGTH_LONG).show();
        } else if (resultContainer.getHasImportError()) {
            Toast.makeText(this, getString(R.string.chats_addAttachments_some_imports_fails), Toast.LENGTH_LONG).show();
        } else if (resultContainer.getHasFileTooLargeError()) {
            Toast.makeText(this, getString(R.string.chats_addAttachment_too_big), Toast.LENGTH_LONG).show();
        }
    }

    public void handleAttachFileClick(View view) {
        closeBottomSheet(mOnBottomSheetClosedListener);

        try {
            Intent openFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
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
    public void smoothScrollToEnd() {

    }

    @Override
    protected void onDestroy() {
        if (mContactController != null) {
            mContactController.unregisterOnContactProfileInfoChangeNotification(this);
        }

        if (mMessageController != null) {
            mMessageController.removeOnMessageReceiverChangedListener(this);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mBottomSheetOpen) {
            closeBottomSheet(null);
        } else if ((mChatInputFragment != null) && (mChatInputFragment.getEmojiEnabled())) {
            mChatInputFragment.showEmojiPicker(false);
            KeyboardUtil.toggleSoftInputKeyboard(this, mChatInputFragment.getEditText(), true);
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private ImageLoader initImageLoader(final ChatImageController chatImageController,
                                        int imageDiameter)
            throws LocalizedException {
        //Image Loader zum Laden der ChatoverviewItems Icons
        ImageLoader imageLoader = new ImageLoader(this, imageDiameter, false) {
            @Override
            protected Bitmap processBitmap(Object data) {
                try {
                    Bitmap returnImage = null;

                    if (data instanceof Contact) {
                        Contact contact = (Contact) data;

                        if (((contact.getIsSimsMeContact() == null) || !contact.getIsSimsMeContact())
                                && (contact.getPhotoUri() != null)) {
                            returnImage = ContactUtil.loadContactPhotoThumbnail(contact.getPhotoUri(), getImageSize(),
                                    MessageDetailsActivity.this);
                        }

                        if (returnImage == null) {
                            if (contact.getAccountGuid() != null) {
                                returnImage = chatImageController.getImageByGuidWithoutCacheing(contact.getAccountGuid(),
                                        getImageSize(), getImageSize());
                            } else {
                                returnImage = mContactController.getFallbackImageByContact(getApplicationContext(), contact
                                );
                            }
                        }

                        if (returnImage == null) {
                            returnImage = chatImageController.getImageByGuidWithoutCacheing(AppConstants.GUID_PROFILE_USER,
                                    getImageSize(), getImageSize());
                        }
                    }

                    return returnImage;
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

        // Add a cache to the image loader
        imageLoader.addImageCache(getSupportFragmentManager(), 0.1f);
        imageLoader.setImageFadeIn(false);
        imageLoader.setLoadingImage(R.drawable.gfx_profil_placeholder);

        chatImageController.addListener(imageLoader);

        return imageLoader;
    }

    @Override
    public void onContactProfilInfoHasChanged(String contactGuid) {

    }

    @Override
    public void onContactProfilImageHasChanged(String contactguid) {

    }

    @Override
    public void onEmojiSelected(@NotNull String unicode) {
        mChatInputFragment.appendText(unicode);
    }

    @Override
    public void onBackSpaceSelected() {
        TextExtensionsKt.backspace(mChatInputFragment.getEditText());
    }

    @Override
    public boolean canSendMedia() {
        return mPreferencesController.canSendMedia();
    }

    public void scrollToOriginalMessage(View v) {
        // wird von der Zelle direkt aufgerufen ...
    }

    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);

        if (resultCode == RESULT_OK) {
            try {
                switch (requestCode) {
                    case RouterConstants.TAKE_PHOTO_RESULT_CODE: {
                        Uri takenPhoto;

                        try {
                            takenPhoto = (new FileUtil(this)).copyFileToInternalDir(mTakePhotoUri);
                        } catch (LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                            return;
                        }

                        ArrayList<String> uris = new ArrayList<>(1);

                        uris.add(takenPhoto.toString());

                        Intent photoIntent = new Intent(this, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.class);

                        photoIntent.putExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_PREVIEW_ACTION, eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.TAKE_PHOTOS_ACTION);
                        photoIntent.putStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS, uris);
                        if (mCitatedChatItem != null) {
                            photoIntent.putExtra(ChatInputActivity.EXTRA_CITATED_MSG_MODEL_ID, mCitatedChatItem.messageId);
                        }
                        startActivityForResult(photoIntent, RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE);

                        break;
                    }
                    case RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE: {
                        ArrayList<String> imageUris = returnIntent.getStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS);
                        ArrayList<String> imageTexts = returnIntent.getStringArrayListExtra(PreviewActivity.EXTRA_TEXTS);

                        Intent intent = new Intent();
                        intent.putExtra(EXTRA_RETURN_TYPE, MimeType.IMAGE_JPEG);
                        if (imageUris != null) {
                            intent.putStringArrayListExtra(EXTRA_RETURN_IMAGE_URIS, imageUris);
                        }
                        if (imageTexts != null) {
                            intent.putExtra(EXTRA_RETURN_IMAGE_TEXTS, imageTexts);
                        }

                        setResult(RESULT_OK, intent);
                        finish();

                        break;
                    }
                    case RouterConstants.SELECT_PHOTO_RESULT_CODE: {
                        handleResult(returnIntent, true);

                        break;
                    }
                    case RouterConstants.SELECT_FILE_RESULT_CODE: {
                        Uri fileUri = returnIntent.getData();

                        Intent intent = new Intent();
                        intent.putExtra(EXTRA_RETURN_TYPE, MimeType.APP_OCTET_STREAM);
                        intent.setData(fileUri);
                        setResult(RESULT_OK, intent);
                        finish();

                        break;
                    }
                    default:
                        throw new LocalizedException("err");
                }
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
            }
        }
    }

    @Override
    public void onMessageReceiverChanged(List<Message> messages) {
        if (messages != null && messages.size() > 0 && mMessageId > 0) {
            for (Message message : messages) {
                if (message.getId() != null && message.getId() == mMessageId) {
                    fillViews();
                    break;
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mEmojiconsFragment != null && mEmojiconsFragment.getView() != null) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mEmojiconsFragment.getView().getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_landscape);
            } else {
                mEmojiconsFragment.getView().getLayoutParams().height = (int) getResources().getDimension(R.dimen.emoji_container_size_portrait);
            }
        }
    }

    @Override
    protected ChatController getChatController() {
        final Message message = mMessageController.getMessageById(mMessageId);

        if (message == null) {
            return getSimsMeApplication().getSingleChatController();
        }

        switch (message.getType()) {
            case Message.TYPE_GROUP:
                return getSimsMeApplication().getGroupChatController();
            case Message.TYPE_PRIVATE:
            default:
                return getSimsMeApplication().getSingleChatController();
        }
    }
}
