// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.chat;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import eu.ginlo_apps.ginlo.ContactsActivity;
import eu.ginlo_apps.ginlo.LocationActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.PreviewActivity;
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity;
import eu.ginlo_apps.ginlo.adapter.ContactsAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.model.param.SendActionContainer;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.UrlHandlerUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.Nickname;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DistributorChatActivity
        extends BaseChatActivity implements ContactsAdapter.ISelectedContacts {
    
    public static final String TAG = DistributorChatActivity.class.getSimpleName();
    
    private static final String CLIPBOARD_KEY = "DISTRIBUTOR_CLIPBOARD_TEXT";
    private static final int RETURN_CODE_DISTRIBUTOR = 0;
    private ArrayList<Contact> mSelectedContacts = new ArrayList<>();
    private ContactsAdapter mContactsAdapter;
    private Toast mNoContactsChosen;
    private int mLayout;
    private OnItemClickListener mRemoveItemListener;
    private OnTouchListener mInputRightButtonOnTouchListener;
    private int mAssetCount = 1;
    private SingleChatController mChatController;
    private boolean mFirstStart = true;
    private OnClickListener mEditContactClickListener;
    private ArrayList<String> mSelectedContactsGuids;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            mNeedsTargetGuid = false;
            super.onCreateActivity(savedInstanceState);

            checkForFinishOnUrlHandlerStart();

            mNoContactsChosen = Toast.makeText(DistributorChatActivity.this,
                    R.string.chat_distributor_select_contacts_first, Toast.LENGTH_LONG);

            getChatController().addListener(this);

            mContactController = ((SimsMeApplication) getApplication()).getContactController();

            preventSelfConversation();

            if (savedInstanceState == null) {
                mChatInputFragment = new ChatInputFragment();
                mEmojiconsFragment = new EmojiPickerFragment();

                getSupportFragmentManager().beginTransaction().add(mChatInputContainerId, mChatInputFragment)
                        .commit();
                getSupportFragmentManager().beginTransaction()
                        .add(mFragmentContainerId, mEmojiconsFragment).commit();
            } else {
                mChatInputFragment = (ChatInputFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                        "chatInputFragment");
                mEmojiconsFragment = (EmojiPickerFragment) getSupportFragmentManager().getFragment(savedInstanceState,
                        "mEmojiconsFragment");
            }
            hideOrShowFragment(mEmojiconsFragment, false, false);
            hideOrShowFragment(mSelfdestructionFragment, false, false);

            mInputRightButtonOnTouchListener = new OnTouchListener() {
                @Override
                public boolean onTouch(final View view,
                                       final MotionEvent event) {
                    showNoContactsChosenToast();
                    return true;
                }
            };

            mEditContactClickListener = new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final ArrayList<String> selectedContactGuids = new ArrayList<>();

                    for (final Contact contact : mSelectedContacts) {
                        // Sanity Check
                        if (contact != null) {
                            selectedContactGuids.add(contact.getAccountGuid());
                        }
                    }

                    final Intent intent = new Intent(DistributorChatActivity.this, RuntimeConfig.getClassUtil().getContactsActivityClass());

                    intent.putExtra(ContactsActivity.EXTRA_MODE, ContactsActivity.MODE_SIMSME_DISTRIBUTOR);
                    intent.putStringArrayListExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS_FROM_GROUP,
                            selectedContactGuids);
                    startActivityForResult(intent, RETURN_CODE_DISTRIBUTOR);
                }
            };
            setRightActionBarImage(R.drawable.ic_group_add_white_24dp, mEditContactClickListener,
                    getResources().getString(R.string.content_description_chat_distributor_add_recipients), -1);

            mLayout = R.layout.contact_item_overview_layout;
            mListView = findViewById(R.id.chat_list_view);
            mContactsAdapter = new ContactsAdapter(this, mLayout, mSelectedContacts, true, false);
            mListView.setAdapter(mContactsAdapter);

            mListView.setStackFromBottom(false);
            mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

            mSelectedContactsGuids = new ArrayList<>();

            final Parcelable state = mListView.onSaveInstanceState();

            mListView.onRestoreInstanceState(state);
            mContactsAdapter.notifyDataSetChanged();

            setProfilePicture(-1, null, null, -1);

            initOnClickListeners();
            mListView.setOnItemClickListener(mRemoveItemListener);
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected void onResumeActivity() {
        try {
            super.onResumeActivity();

            if (loginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
                return;
            }

            init();
            if (mSelectedContacts == null) {
                mSelectedContacts = new ArrayList<>();
            }

            if (mTargetGuid != null) {
                initChatList();
            }

            if (loginController.getState().equals(LoginController.STATE_LOGGED_IN)) {
                if (mTargetGuid != null) {
                    getChatController().loadNewestMessagesByGuid();
                }

                String clipBoardText = clipBoardController.get(CLIPBOARD_KEY);

                if (clipBoardText == null && mTargetGuid != null) {
                    clipBoardText = clipBoardController.get(mTargetGuid);
                }

                if (!StringUtil.isNullOrEmpty(clipBoardText)) {
                    mChatInputFragment.setChatInputText(clipBoardText, false);
                }

                if (mBottomSheetFragment != null && mBottomSheetFragment.getView() != null) {
                    mBottomSheetFragment.getView().startAnimation(mAnimationSlideOut);
                    closeBottomSheet(null);
                }

                String textFromLink = null;

                try {
                    textFromLink = UrlHandlerUtil.INSTANCE.getStringFromIntent(getIntent(), "text=");
                } catch (final UnsupportedEncodingException e) {
                    Toast.makeText(DistributorChatActivity.this, R.string.chat_distributor_url_decoding_failed,
                            Toast.LENGTH_LONG).show();
                    LogUtil.e(TAG, e.getMessage(), e);
                }

                final String textFromLinkFinal = textFromLink;

                if (textFromLink != null) {
                    if ((mChatInputFragment.getChatInputText() != null)
                            && (
                            mChatInputFragment.getChatInputText().isEmpty()
                                    || mChatInputFragment.getChatInputText().equals(textFromLink)
                    )) {
                        /*setstart text, falls die activity noch nicht gezeichnet wurde und settext, falls doch*/
                        mChatInputFragment.setStartText(textFromLink);
                        mChatInputFragment.setChatInputText(textFromLink, false);
                    } else {
                        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog,
                                                final int which) {
                                mChatInputFragment.setChatInputText(textFromLinkFinal, false);
                                dialog.dismiss();
                            }
                        };

                        final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog,
                                                final int which) {
                                dialog.dismiss();
                            }
                        };

                        final String title = getResources().getString(R.string.chat_distributor_overwrite_title);
                        final String positiveButton = getResources().getString(R.string.chat_distributor_overwrite_confirm);
                        final String message = getResources().getString(R.string.chat_distributor_overwrite_message);
                        final String negativeButton = getResources().getString(R.string.chat_distributor_overwrite_decline);

                        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this,
                                message,
                                title,
                                positiveButton,
                                negativeButton,
                                positiveOnClickListener,
                                negativeOnClickListener);

                        dialog.show();
                    }
                }
                checkActionContainer();
            }

            // weiterleitung zur KOntakteauswahl beim ersten Start
            if (mFirstStart) {
                mEditContactClickListener.onClick(null);
                mFirstStart = false;
            }
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected OnSendMessageListener createOnSendMessageListener(final String contactsGuid) {
        return new OnSendMessageListener() {
            private int contactIndex = mSelectedContacts.size() * mAssetCount;

            @Override
            public void onSaveMessageSuccess(final Message message) {
                //
                contactIndex--;
                if (contactIndex < 1) {
                    dismissIdleDialog();
                    checkForFinish();
                }
            }

            @Override
            public void onSendMessageSuccess(final Message message,
                                             final int countNotSendMessages) {
                if (countNotSendMessages > 0 && message != null && mSelectedContacts != null) {
                    final String toGuid = message.getTo();

                    for (final Contact ctr : mSelectedContacts) {
                        if (StringUtil.isEqual(toGuid, ctr.getAccountGuid())) {
                            final String notSendMsg = getString(R.string.chat_message_failed_update);

                            getChatController().sendSystemInfo(ctr.getAccountGuid(), ctr.getPublicKey(), null, null, notSendMsg, -1);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onSendMessageError(final Message message,
                                           final String errorMsg, final String localizedErrorIdentifier) {
                contactIndex--;
                if (contactIndex < 1) {
                    dismissIdleDialog();
                    Toast.makeText(DistributorChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        };
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void initOnClickListeners() {

        mRemoveItemListener = new OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent,
                                    final View view,
                                    final int position,
                                    final long id) {

                final Contact contact = mSelectedContacts.get(position);
                if (contact != null) {
                    mSelectedContactsGuids.add(contact.getAccountGuid());
                }
                mContactsAdapter.notifyDataSetChanged();

                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    public void run() {
                        mSelectedContacts.remove(contact);
                        if (contact != null) {
                            mContactsAdapter.remove(contact);
                            mSelectedContactsGuids.remove(contact.getAccountGuid());
                        }

                        mContactsAdapter.notifyDataSetChanged();
                        setTitleByListSize();
                    }
                };
                handler.postDelayed(runnable, 300);
            }
        };
    }

    private void setTitleByListSize() {
        if ((mSelectedContacts != null) && (mSelectedContacts.size() > 1)) {
            mTitle = getString(R.string.chat_distributor_select_title);
            setTitle(getString(R.string.chat_distributor_select_title));
        } else {
            mTitle = getString(R.string.chat_single_select_title);
            setTitle(getString(R.string.chat_single_select_title));
        }
    }

    private void addItemToSelectedList(final Contact contact) {

        if (!mSelectedContacts.contains(contact)) {
            mSelectedContacts.add(contact);
            // Temp Devices laden ....
            if (!StringUtil.isNullOrEmpty(contact.getAccountGuid())) {
                mContactController.checkPublicKey(contact, null);
            }
        }
        mContactsAdapter = new ContactsAdapter(DistributorChatActivity.this, mLayout, mSelectedContacts,
                true, false);

        mListView.setAdapter(mContactsAdapter);
        mContactsAdapter.notifyDataSetChanged();

        mListView.setOnItemClickListener(mRemoveItemListener);
        setTitleByListSize();
    }

    private void showNoContactsChosenToast() {
        if (!mNoContactsChosen.getView().isShown()) {
            mNoContactsChosen.show();
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle bundle) {
        getSupportFragmentManager().putFragment(bundle, "chatInputFragment", mChatInputFragment);
        getSupportFragmentManager().putFragment(bundle, "mEmojiconsFragment", mEmojiconsFragment);

        super.onSaveInstanceState(bundle);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if ((item.getTitle() != null) && ("Search").contentEquals(item.getTitle())) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPauseActivity() {
        try {
            super.onPauseActivity();

            getIntent().setData(null);

            clipBoardController.put(CLIPBOARD_KEY, mChatInputFragment.getEditText().getText().toString());
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    private void init()
            throws LocalizedException {
        setBackground();
        setTitleByListSize();
    }

    private void initChatList() {
        LogUtil.i(TAG, "Open ChatStream " + mTargetGuid);

        mChatAdapter = getChatController().getChatAdapter(this, mTargetGuid);
        mChatAdapter.setOnLinkClickListener(this);
        getChatController().setCurrentChatAdapter(mChatAdapter);

        mListView = findViewById(R.id.chat_list_view);

        registerDataObserver();
        final Parcelable state = mListView.onSaveInstanceState();

        mListView.setAdapter(mChatAdapter);
        mListView.onRestoreInstanceState(state);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);
        scrollToEnd();
    }

    public void handleDeleteChatClick(final View view) {
        final Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

        closeBottomSheet(null);
        getChatController().deleteChat(mTargetGuid, true, null);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        /* methode ueberschreiben, damit das rechte Icon nicht angezeigt wird*/
        setIsMenuVisible(false);
        return true;
    }

    @Override
    public boolean handleSendMessageClick(final String text) {
        if ((mSelectedContacts == null) || (mSelectedContacts.size() == 0)) {
            showNoContactsChosenToast();
            return false;
        }

        MessageDestructionParams destructionParams;

        try {
            destructionParams = getDestructionParams();
        } catch (final InvalidDateException e) {
            LogUtil.w(TAG, e.getMessage(), e);
            return false;
        }

        showIdleDialog(-1);
        mOnSendMessageListener = createOnSendMessageListener(null);

        int ctr = mSelectedContacts.size();

        while (ctr > 0) {
            --ctr;

            final Contact contact = mSelectedContacts.get(ctr);

            createChatIfNeeded(contact);

            if (mSelfdestructionFragment == null || !mChatInputFragment.getTimerEnabled()) {
                getChatController().sendText(contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(), text,
                        destructionParams, mOnSendMessageListener, null, mIsPriority, null);
            } else {
                final Calendar oneYear = Calendar.getInstance();
                oneYear.add(Calendar.YEAR, 1);

                if (mSelfdestructionFragment.getTimerDate().after(oneYear.getTime())) {
                    final String msg = getResources().getString(R.string.chats_timedmessage_invalid_date2);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                    dismissIdleDialog();
                    mChatInputFragment.setTypingState();
                    return false;
                } else if (!mSelfdestructionFragment.getTimerDate().after(Calendar.getInstance().getTime())) {
                    final String msg = getResources().getString(R.string.chats_selfdestruction_invalid_date);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                    dismissIdleDialog();
                    mChatInputFragment.setTypingState();
                    return false;
                } else if (destructionParams != null
                        && destructionParams.date != null
                        && destructionParams.date.before(mSelfdestructionFragment.getTimerDate())) {
                    final String msg = getResources().getString(R.string.chats_selfdestruction_sddate_before_senddate);
                    DialogBuilderUtil.buildErrorDialog(this, msg).show();
                    dismissIdleDialog();
                    mChatInputFragment.setTypingState();
                    return false;
                } else {
                    getChatController().sendText(contact.getAccountGuid(), contact.getPublicKey(), null, null, text,
                            destructionParams, mOnSendMessageListener, mSelfdestructionFragment.getTimerDate(), mIsPriority, null);
                }
            }
        }
        mChatInputFragment.setOnlineState();
        try {
            mPreferencesController.incNumberOfStartedChats();
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }

        return true;
    }

    public void handleSendVoiceClick(final Uri voiceUri) {
        if ((mSelectedContacts == null) || (mSelectedContacts.size() == 0)) {
            showNoContactsChosenToast();
            return;
        }

        MessageDestructionParams destructionParams;

        try {
            destructionParams = getDestructionParams();
        } catch (final InvalidDateException e) {
            LogUtil.w(TAG, e.getMessage(), e);
            return;
        }

        showIdleDialog(-1);
        mOnSendMessageListener = createOnSendMessageListener(null);

        int ctr = mSelectedContacts.size();

        while (ctr > 0) {
            --ctr;

            final Contact contact = mSelectedContacts.get(ctr);

            createChatIfNeeded(contact);

            if (mSelfdestructionFragment == null || !mChatInputFragment.getTimerEnabled()) {
                getChatController().sendVoice(this, contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(), voiceUri,
                        destructionParams, mOnSendMessageListener, null, mIsPriority);
            } else {
                getChatController().sendVoice(this, contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(), voiceUri,
                        destructionParams, mOnSendMessageListener, mSelfdestructionFragment.getTimerDate(), mIsPriority);
            }
            mChatInputFragment.setOnlineState();
        }
        try {
            mPreferencesController.incNumberOfStartedChats();
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    private void checkForFinish() {
        if (mSelectedContacts.size() == 1) {
            final Intent intent = new Intent(this, SingleChatActivity.class);

            intent.putExtra(BaseChatActivity.EXTRA_TARGET_GUID, mSelectedContacts.get(0).getAccountGuid());

            startActivity(intent);
        }

        finish();
    }

    @Override
    public void handleAddAttachmentClick() {
        if ((mSelectedContacts == null) || (mSelectedContacts.size() == 0)) {
            showNoContactsChosenToast();
        } else {
            super.handleAddAttachmentClick();
        }
    }

    @Override
    protected void onActivityPostLoginResult(final int requestCode,
                                             final int resultCode,
                                             final Intent returnIntent) {
        if (resultCode == RESULT_OK) {
            if (requestCode == RETURN_CODE_DISTRIBUTOR) {
                final String[] selectedContactGuids = returnIntent.getStringArrayExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS);
                final ArrayList<Contact> selectedContacts = mContactController.getContactsByGuid(selectedContactGuids);

                if (selectedContacts.size() == 0 && mSelectedContacts.size() == 0) {
                    finish();
                    return;
                }

                mSelectedContacts.clear();
                for (final Contact selectedContact : selectedContacts) {
                    if (!mSelectedContacts.contains(selectedContact)) {
                        mSelectedContacts.add(selectedContact);
                        // Temp Devices laden ....
                        mContactController.checkPublicKey(selectedContact, null);
                    }
                }

                mContactsAdapter = new ContactsAdapter(this, mLayout, mSelectedContacts,
                        true, false);
                mListView.setAdapter(mContactsAdapter);

                final Parcelable state = mListView.onSaveInstanceState();

                mListView.onRestoreInstanceState(state);
                mContactsAdapter.notifyDataSetChanged();
                results = null;
                return;
            } else {
                switch (requestCode) {
                    case RouterConstants.SELECT_CONTACT_RESULT_CODE: {
                        final Uri contactUri = returnIntent.getData();
                        final String vCard = mContactController.getVCardForContactUri(this, contactUri);

                        if (vCard != null) {
                            int ctr = mSelectedContacts.size();

                            showIdleDialog(-1);
                            mOnSendMessageListener = createOnSendMessageListener(null);

                            while (ctr > 0) {
                                --ctr;

                                final Contact contact = mSelectedContacts.get(ctr);

                                createChatIfNeeded(contact);
                                getChatController().sendVCard(contact.getAccountGuid(),
                                        contact.getPublicKey(),
                                        contact.getTempDeviceGuid(),
                                        contact.getTempDevicePublicKeyXML(),
                                        vCard,
                                        null,
                                        null,
                                        mOnSendMessageListener);
                            }
                            try {
                                mPreferencesController.incNumberOfStartedChats();
                            } catch (final LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                            }
                            results = null;

                            return;
                        }
                        break;
                    }
                    case RouterConstants.SELECT_INTERNAL_CONTACT_RESULT_CODE: {
                        try {
                            final String sendContactGuid = returnIntent.getStringExtra(ContactsActivity.EXTRA_SELECTED_CONTACTS);
                            final Contact sendContact = mContactController.getContactByGuid(sendContactGuid);

                            final VCard vCard = new VCard();

                            final String email = sendContact.getEmail();
                            if (!StringUtil.isNullOrEmpty(email)) {
                                vCard.addEmail(email);
                            }

                            final String phoneNumber = sendContact.getPhoneNumber();
                            if (!StringUtil.isNullOrEmpty(phoneNumber) && phoneNumber.startsWith("+")) {
                                vCard.addTelephoneNumber(phoneNumber);
                            }

                            final String name = sendContact.getName();
                            if (!StringUtil.isNullOrEmpty(name)) {
                                vCard.addFormattedName(new FormattedName(name));
                            }

                            final String nickName = sendContact.getNickname();
                            if (!StringUtil.isNullOrEmpty(name)) {
                                final Nickname nickname = new Nickname();
                                nickname.getValues().add(nickName);
                                vCard.addNickname(new Nickname(nickname));
                            }

                            showIdleDialog(-1);
                            mOnSendMessageListener = createOnSendMessageListener(null);

                            int ctr = mSelectedContacts.size();

                            while (ctr > 0) {
                                --ctr;
                                final Contact contact = mSelectedContacts.get(ctr);

                                createChatIfNeeded(contact);

                                getChatController().sendVCard(contact.getAccountGuid(),
                                        contact.getPublicKey(),
                                        contact.getTempDeviceGuid(),
                                        contact.getTempDevicePublicKeyXML(),
                                        vCard.write(),
                                        contact.getSimsmeId(),
                                        sendContactGuid,
                                        mOnSendMessageListener);
                                mPreferencesController.incNumberOfStartedChats();
                            }
                        } catch (final LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                        }
                        results = null;

                        return;
                    }
                    case RouterConstants.GET_LOCATION_RESULT_CODE: {
                        final byte[] screenshot = returnIntent.getByteArrayExtra(LocationActivity.EXTRA_SCREENSHOT);
                        final double longitude = returnIntent.getDoubleExtra(LocationActivity.EXTRA_LONGITUDE, 0.0);
                        final double latitude = returnIntent.getDoubleExtra(LocationActivity.EXTRA_LATITUDE, 0.0);
                        int ctr = mSelectedContacts.size();

                        showIdleDialog(-1);
                        mOnSendMessageListener = createOnSendMessageListener(null);

                        while (ctr > 0) {
                            --ctr;

                            final Contact contact = mSelectedContacts.get(ctr);

                            createChatIfNeeded(contact);
                            getChatController().sendLocation(contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(), longitude,
                                    latitude, screenshot, mOnSendMessageListener);
                        }
                        try {
                            mPreferencesController.incNumberOfStartedChats();
                        } catch (final LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                        }
                        results = null;

                        return;
                    }
                    case RouterConstants.GET_PHOTO_WITH_DESTRUCTION_RESULT_CODE: {
                        final List<String> imageUris = returnIntent.getStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS);
                        final List<String> imageTexts = returnIntent.getStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TEXTS);
                        final boolean isPriority = returnIntent.getBooleanExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_IS_PRIORITY, false);

                        MessageDestructionParams params = null;
                        Date timerDate = null;

                        if (returnIntent.hasExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_DESTRUCTION_PARAMS)) {
                            params = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_DESTRUCTION_PARAMS), MessageDestructionParams.class);
                        }

                        if (returnIntent.hasExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TIMER)) {
                            timerDate = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TIMER), Date.class);
                        }

                        if ((imageUris == null) || (imageUris.size() < 1)) {
                            return;
                        }

                        mAssetCount = imageUris.size();
                        mOnSendMessageListener = createOnSendMessageListener(null);

                        int ctr = mSelectedContacts.size();

                        showIdleDialog(-1);
                        while (ctr > 0) {
                            --ctr;

                            final Contact contact = mSelectedContacts.get(ctr);

                            for (int i = 0; i < imageUris.size(); ++i) {
                                final String uri = imageUris.get(i);
                                final String description = imageTexts.get(i);
                                createChatIfNeeded(contact);

                                final boolean deleteImage = ctr == 0;
                                getChatController().sendImage(this, contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(),
                                        Uri.parse(uri), description, params, mOnSendMessageListener, timerDate, isPriority, null, deleteImage);
                                try {
                                    mPreferencesController.incNumberOfStartedChats();
                                } catch (final LocalizedException e) {
                                    LogUtil.e(TAG, e.getMessage(), e);
                                }
                            }
                        }
                        results = null;

                        return;
                    }
                    case RouterConstants.GET_VIDEO_WITH_DESTRUCTION_RESULT_CODE: {
                        final List<String> videoUris = returnIntent.getStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_URIS);
                        final List<String> videoTexts = returnIntent.getStringArrayListExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TEXTS);
                        final boolean isPriority = returnIntent.getBooleanExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_IS_PRIORITY, false);
                        MessageDestructionParams params = null;
                        Date timerDate = null;

                        if (returnIntent.hasExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_DESTRUCTION_PARAMS)) {
                            params = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_DESTRUCTION_PARAMS), MessageDestructionParams.class);
                        }
                        if (returnIntent.hasExtra(eu.ginlo_apps.ginlo.activity.chat.PreviewActivity.EXTRA_TIMER)) {
                            timerDate = SystemUtil.dynamicDownCast(returnIntent.getSerializableExtra(PreviewActivity.EXTRA_TIMER), Date.class);
                        }

                        if ((videoUris == null) || (videoUris.size() < 1)) {
                            break;
                        }

                        mAssetCount = videoUris.size();
                        mOnSendMessageListener = createOnSendMessageListener(null);

                        int ctr = mSelectedContacts.size();

                        showIdleDialog(-1);
                        while (ctr > 0) {
                            --ctr;

                            final Contact contact = mSelectedContacts.get(ctr);

                            for (int i = 0; i < videoUris.size(); ++i) {
                                final String videoUri = videoUris.get(i);
                                final String description = videoTexts.get(i);
                                createChatIfNeeded(contact);

                                getChatController().sendVideo(this, contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(),
                                        Uri.parse(videoUri), description, params, mOnSendMessageListener, timerDate, isPriority, ctr == 0);
                            }
                        }
                        results = null;

                        return;
                    }
                    default: {
                        LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                        break;
                    }
                }
            }
        } else {
            if (mSelectedContacts.size() == 0) {
                finish();
                return;
            }
        }

        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);
    }

    protected void checkActionContainer() {
        if (mActionContainer != null) {
            if (mActionContainer.action == SendActionContainer.ACTION_SEND) {
                if (mActionContainer.type == SendActionContainer.TYPE_FILE) {
                    if (mActionContainer.uris != null && mActionContainer.uris.size() > 0) {
                        final Uri fileUri = mActionContainer.uris.get(0);
                        final FileUtil fu = new FileUtil(this);
                        final MimeUtil mu = new MimeUtil(this);
                        final String filename = fu.getFileName(fileUri);

                        final DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                mOnSendMessageListener = createOnSendMessageListener(null);

                                int ctr = mSelectedContacts.size();

                                final String mimeType = mu.getMimeType(fileUri);

                                final Uri tmpUri;

                                try {
                                    tmpUri = fu.copyFileToInternalDir(fileUri);

                                    showIdleDialog(-1);

                                    while (ctr > 0) {
                                        --ctr;

                                        final Contact contact = mSelectedContacts.get(ctr);

                                        if (contact != null) {
                                            getChatController().sendFile(DistributorChatActivity.this, contact.getAccountGuid(), contact.getPublicKey(), contact.getTempDeviceGuid(), contact.getTempDevicePublicKeyXML(),
                                                    tmpUri, false, filename, mimeType, mOnSendMessageListener, null);
                                        }
                                        try {
                                            mPreferencesController.incNumberOfStartedChats();
                                        } catch (final LocalizedException e) {
                                            LogUtil.e(TAG, e.getMessage(), e);
                                        }
                                    }
                                } catch (final LocalizedException e) {
                                    LogUtil.e(TAG, e.getMessage(), e);
                                    Toast.makeText(DistributorChatActivity.this, R.string.chat_message_failed_failed_label, Toast.LENGTH_LONG).show();
                                }
                                mActionContainer = null;
                                results = null;
                            }
                        };

                        long fileSize;
                        try {
                            fileSize = fu.getFileSize(fileUri);
                        } catch (LocalizedException e) {
                            fileSize = 0;
                            LogUtil.e(TAG, "Failed to get file size.", e);
                        }
                        showSendFileDialog(filename, mu.getExtensionForUri(fileUri), fileSize, positiveListener, null);
                    }
                    return;
                }
            }

            super.checkActionContainer();
        }
    }

    @Override
    public void onBackPressed() {
        if ((mChatInputFragment != null) && (mChatInputFragment.getEmojiEnabled())) {
            mChatInputFragment.showEmojiPicker(false);
        } else if (mBottomSheetOpen) {
            closeBottomSheet(mOnBottomSheetClosedListener);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onBackArrowPressed() {
        KeyboardUtil.toggleSoftInputKeyboard(this, getCurrentFocus(), false);
        super.onBackPressed();
    }

    private void createChatIfNeeded(final Contact contact) {
        try {
            Chat chat = getChatController().getChatByGuid(contact.getAccountGuid());

            if (chat == null) {
                final String title = contact.getName();

                chat = new Chat();
                chat.setChatGuid(contact.getAccountGuid());
                chat.setType(Chat.TYPE_SINGLE_CHAT);
                chat.setTitle(title);

                mChatController.insertOrUpdateChat(chat);

                try {
                    mPreferencesController.incNumberOfStartedChats();
                } catch (final LocalizedException e) {
                    LogUtil.w(TAG, e.getMessage(), e);
                }
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected String getChatTitle() {
        return mTitle;
    }

    @Override
    protected ChatController getChatController() {
        if (mChatController == null) {
            mChatController = getSimsMeApplication().getSingleChatController();
        }

        return mChatController;
    }

    @Override
    public void onChatDataChanged(boolean clearImageCache) {

    }

    @Override
    public void addSelectedContactGuid(String contactGuid) {
    }

    @Override
    public void removeSelectedContactGuid(String contactGuid) {
    }

    @Override
    public boolean containsSelectedContactGuid(String contactGuid) {
        return mSelectedContactsGuids != null && mSelectedContactsGuids.contains(contactGuid);
    }
}
