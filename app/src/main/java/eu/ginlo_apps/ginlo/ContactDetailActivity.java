// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Patterns;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import com.google.zxing.integration.android.IntentIntegrator;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.MuteChatActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.OnContactVerifiedListener;
import eu.ginlo_apps.ginlo.controller.ContactController.OnLoadContactsListener;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.RoundedImageView;
import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ContactDetailActivity
        extends BaseActivity
        implements ContactController.OnContactProfileInfoChangeNotification {

    private static final String TAG = ContactDetailActivity.class.getSimpleName();
    private static final String CONTACT_GUID = "ContactDetailActivity.extraContactGuid";
    private static final int MODE_NORMAL = 0;
    private static final int EDIT_CONTACT_RESULT_CODE = 262;
    private static final int SET_SILENT_TILL_RESULT_CODE = 148;
    private static final int SCAN_CONTACT_RESULT_CODE = 453;

    public static final String EXTRA_CONTACT = "ContactDetailActivity.extraContact";
    public static final String EXTRA_CONTACT_MAP = "ContactDetailActivity.extraContactMap";
    public static final String EXTRA_CONTACT_LIST = "ContactDetailActivity.extraContactList";
    public static final String EXTRA_CONTACT_GUID = "ContactDetailActivity.extraContactGuid";
    public static final String EXTRA_MODE = "ContactDetailActivity.extraMode";
    public static final int MODE_CREATE = 1;
    public static final int MODE_NO_SEND_BUTTON = 2;
    public static final int MODE_CREATE_GINLO_NOW = 3;

    @Inject
    public Router router;
    @Inject
    public AppConnectivity appConnectivity;
    private File mTakenPhotoFile;
    private int mMode;
    private boolean deleteProfileImage = false;
    private Contact mContact;
    private Contact mScanContact;
    private EditText mNickNameEditText;
    private EditText mStatusEditText;
    private EditText mFirstNameEditText;
    private EditText mLastNameEditText;
    private EditText mMobileNumberEditText;
    private EditText mEmailAddressEditText;
    private EditText mDepartmentEditText;
    private ViewGroup mSimsmeIdContainer;
    private TextView mSimsmeIdTextView;
    private TextView mNickNameLabel;
    private TextView mStatusLabel;
    private TextView mFirstNameLabel;
    private TextView mLastNameLabel;
    private TextView mMobileNumberLabel;
    private TextView mEmailAddressLabel;
    private TextView mDepartmentLabel;
    private final OnLoadContactsListener onLoadContactsListener = new OnLoadContactsListener() {
        @Override
        public void onLoadContactsComplete() {
            try {
                ContactDetailActivity.this.updateContactDetails();
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                DialogBuilderUtil.buildErrorDialog(ContactDetailActivity.this,
                        getString(R.string.contact_detail_load_contact_failed)).show();
            }
        }

        @Override
        public void onLoadContactsError(final String message) {
            DialogBuilderUtil.buildErrorDialog(ContactDetailActivity.this, message).show();
        }

        @Override
        public void onLoadContactsCanceled() {
        }
    };
    private TextView mSimsmeIdLabel;
    private View mSelectImageView;
    private Button mBlockButton;
    private Button mClearChatButton;
    private Button mScanButton;
    private Button mSendButton;
    private final OnContactVerifiedListener onContactVerifiedListener = new OnContactVerifiedListener() {
        @Override
        public void onContactVerified(final boolean verified) {
            if (verified) {
                if (mContact != null) {
                    getSimsMeApplication().getChatOverviewController().chatChanged(null, mContact.getAccountGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                    setTrustedState();
                }
            } else {
                Toast.makeText(ContactDetailActivity.this, getString(R.string.contacts_error_verifyingContactByQRCodeFailed), Toast.LENGTH_SHORT).show();
            }
        }

        public void onContactVerifiedFailed() {
            Toast.makeText(ContactDetailActivity.this,
                    getString(R.string.contacts_error_verifyingContactByQRCodeFailed), Toast.LENGTH_LONG).show();
        }
    };
    private Button mCreateButton;
    private RoundedImageView mProfileImageView;
    private boolean mIsInEditMode = false;
    private final OnClickListener mOnEditClickListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            mIsInEditMode = true;
            deleteProfileImage = false;
            enableViews();
            setRightActionBarImage(R.drawable.ic_done_white_24dp, mOnEditFinishedClickListener, getResources().getString(R.string.content_description_contact_details_edit_contact), -1);
        }
    };
    private ContactController mContactController;
    private ChatImageController mChatImageController;
    private byte[] mImageBytes;
    private Timer mRefreshTimer;
    private View mContactContentContainer;
    private final OnClickListener mOnEditFinishedClickListener = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            finishEditing(true);
        }
    };

    @Override
    protected void onActivityPostLoginResult(final int requestCode,
                                             final int resultCode,
                                             final Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);
        switch (requestCode) {
            case EDIT_CONTACT_RESULT_CODE: {
                if ((resultCode == RESULT_OK)) {
                    if ((mContact != null) && (mContact.getAccountGuid() != null)) {
                        mChatImageController.removeFromCache(mContact.getAccountGuid());
                    }
                }

                break;
            }
            case SCAN_CONTACT_RESULT_CODE: {
                if (resultCode == RESULT_OK) {
                    final String qrCodeString = returnIntent.getStringExtra("SCAN_RESULT");

                    if (mScanContact != null) {
                        mContactController.verifyContact(mScanContact, qrCodeString, onContactVerifiedListener);
                    }
                    mScanContact = null;
                }
                break;
            }
            case RouterConstants.SELECT_GALLARY_RESULT_CODE: {
                if (returnIntent == null) {
                    break;
                }

                final Uri selectedGallaryItem = returnIntent.getData();
                final FileUtil fileUtil = new FileUtil(this);
                final MimeUtil mimeUtil = new MimeUtil(this);

                if (!mimeUtil.checkImageUriMimetype(getApplication(), selectedGallaryItem)) {
                    Toast.makeText(this, R.string.chats_addAttachment_wrong_format_or_error, Toast.LENGTH_LONG).show();
                    break;
                }

                try {
                    Uri selectedItemIntern = fileUtil.copyFileToInternalDir(selectedGallaryItem);
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
                    Uri internalUri = (new FileUtil(this)).copyFileToInternalDir(Uri.fromFile(mTakenPhotoFile));

                    router.cropImage(internalUri.toString());
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, e.getMessage(), e);
                    Toast.makeText(this, R.string.chats_addAttachments_some_imports_fails, Toast.LENGTH_LONG).show();
                }
                break;
            }
            case RouterConstants.ADJUST_PICTURE_RESULT_CODE: {
                if (returnIntent == null) {
                    break;
                }

                final Bitmap bm = returnIntent.getParcelableExtra(CropImageActivity.RETURN_DATA_AS_BITMAP);
                if (bm != null) {
                    mProfileImageView.setImageBitmap(bm);
                    mImageBytes = BitmapUtil.compress(bm, 100);
                }
                break;
            }
            case SET_SILENT_TILL_RESULT_CODE: {
                try {
                    mContact = mContactController.getContactByGuid(mContact.getAccountGuid());
                } catch (final LocalizedException le) {
                    LogUtil.w(TAG, le.getMessage(), le);
                }
                break;
            }
            default: {
                LogUtil.e(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            final Intent intent = getIntent();

            final int slideheigth_two_lines = (int) getResources().getDimension(R.dimen.chat_slideheight_two_lines);
            mAnimationSlideIn = new TranslateAnimation(0, 0, slideheigth_two_lines, 0);
            mAnimationSlideOut = new TranslateAnimation(0, 0, 0, slideheigth_two_lines);

            mBlockButton = findViewById(R.id.contact_details_button_block);
            Button deleteButton = findViewById(R.id.contact_details_button_delete);

            mClearChatButton = findViewById(R.id.contact_details_button_clear_chat);

            mScanButton = findViewById(R.id.contact_details_button_scan);
            mSendButton = findViewById(R.id.contact_details_button_sendmessage);
            mCreateButton = findViewById(R.id.contact_details_button_create);

            mNickNameLabel = findViewById(R.id.contact_details_edit_text_nickname_label);
            mStatusLabel = findViewById(R.id.contact_details_edit_text_status_label);
            mFirstNameLabel = findViewById(R.id.contact_details_edit_text_firstname_label);
            mLastNameLabel = findViewById(R.id.contact_details_edit_text_lastname_label);
            mMobileNumberLabel = findViewById(R.id.contact_details_edit_text_mobilenumber_label);
            mEmailAddressLabel = findViewById(R.id.contact_details_edit_text_emailaddress_label);
            mDepartmentLabel = findViewById(R.id.contact_details_edit_text_departement_label);
            mSimsmeIdLabel = findViewById(R.id.contact_details_edit_text_simsmeid_label);

            mNickNameEditText = findViewById(R.id.contact_details_edit_text_nickname);
            mStatusEditText = findViewById(R.id.contact_details_edit_text_status);
            mFirstNameEditText = findViewById(R.id.contact_details_edit_text_firstname);
            mLastNameEditText = findViewById(R.id.contact_details_edit_text_lastname);
            mMobileNumberEditText = findViewById(R.id.contact_details_edit_text_mobilenumber);
            mEmailAddressEditText = findViewById(R.id.contact_details_edit_text_emailaddress);
            mDepartmentEditText = findViewById(R.id.contact_details_edit_text_departement);

            mSimsmeIdTextView = findViewById(R.id.contact_details_edit_text_simsmeid);
            mSimsmeIdContainer = findViewById(R.id.contact_details_simsmeid_container);

            mProfileImageView = findViewById(R.id.contacts_details_mask_image_view_profile_image);
            mSelectImageView = findViewById(R.id.contact_details_select_image_overlay);

            mContactContentContainer = findViewById(R.id.contact_content_container);

            mChatImageController = getSimsMeApplication().getChatImageController();
            mContactController = getSimsMeApplication().getContactController();
            final PreferencesController preferencesController = ((SimsMeApplication) getApplication()).getPreferencesController();

            mImageBytes = new byte[]{};
            if (savedInstanceState != null) {
                mMode = savedInstanceState.getInt(EXTRA_MODE);
            } else if (intent.hasExtra(EXTRA_MODE)) {
                mMode = intent.getExtras().getInt(EXTRA_MODE);
            } else {
                mMode = MODE_NORMAL;
            }

            final TextView tenantTextView = findViewById(R.id.contact_details_mandant_label);

            final Contact contactByGuid;

            final HashMap<String, String> contactDetails = SystemUtil.dynamicDownCast(intent.getSerializableExtra(EXTRA_CONTACT_MAP), HashMap.class);

            if (mMode == MODE_CREATE || mMode == MODE_CREATE_GINLO_NOW) {
                mScanButton.setVisibility(View.GONE);
                deleteButton.setVisibility(View.GONE);
                mClearChatButton.setVisibility(View.GONE);

                if (!intent.hasExtra(EXTRA_CONTACT_MAP)) {
                    LogUtil.w(TAG, "MODE_CREATE but no contact given");
                    finish();
                }

                final String contactGuid = contactDetails.get(JsonConstants.GUID);

                final String simsmeId = contactDetails.get(JsonConstants.ACCOUNT_ID);

                if (StringUtil.isNullOrEmpty(simsmeId)) {
                    LogUtil.w(TAG, "MODE_CREATE no SIMSmeID");
                    finish();
                }

                final String pubKey = contactDetails.get(JsonConstants.PUBLIC_KEY);

                if (StringUtil.isNullOrEmpty(pubKey)) {
                    LogUtil.w(TAG, "MODE_CREATE no public key");
                    finish();
                }

                final String mandant = contactDetails.get(JsonConstants.MANDANT);

                if (StringUtil.isNullOrEmpty(mandant)) {
                    LogUtil.w(TAG, "MODE_CREATE no mandant");
                    finish();
                }

                final String phoneNumber = contactDetails.get(JsonConstants.PHONE);
                final String email = contactDetails.get(JsonConstants.EMAIL);

                contactByGuid = mContactController.getContactByGuid(contactGuid);

                if (contactByGuid == null) {
                    mContact = new Contact();

                    mContact.setAccountGuid(contactGuid);
                    mContact.setSimsmeId(simsmeId);
                    mContact.setPublicKey(pubKey);
                    mContact.setMandant(mandant);
                    if (mMode == MODE_CREATE_GINLO_NOW) {
                        mContact.setState(Contact.STATE_HIGH_TRUST);
                    } else {
                        mContact.setState(Contact.STATE_LOW_TRUST);
                    }
                    mContact.setIsSimsMeContact(true);

                    if (!StringUtil.isNullOrEmpty(phoneNumber)) {
                        mContact.setPhoneNumber(phoneNumber);
                    }

                    if (!StringUtil.isNullOrEmpty(email)) {
                        mContact.setEmail(email);
                    }

                    mBlockButton.setVisibility(View.GONE);
                    mScanButton.setVisibility(View.GONE);
                    mSendButton.setVisibility(View.GONE);
                    mCreateButton.setVisibility(View.VISIBLE);
                    tenantTextView.setVisibility(View.GONE);

                } else if (contactByGuid.isDeletedHidden() || contactByGuid.getIsHidden()) {
                    mCreateButton.setVisibility(View.VISIBLE);
                }
                final View silentTill = findViewById(R.id.contact_details_silent_till_container);
                silentTill.setVisibility(View.GONE);
            } else {
                contactByGuid = null;
            }

            if (mMode != MODE_CREATE || mMode != MODE_CREATE_GINLO_NOW || contactByGuid != null) {
                if (mMode == MODE_NO_SEND_BUTTON) {
                    mSendButton.setVisibility(View.GONE);
                }

                List<Contact> contacts = new ArrayList<>();

                if (intent.hasExtra(EXTRA_CONTACT_LIST)) {
                    contacts = SystemUtil.dynamicDownCast(intent.getExtras().getSerializable(EXTRA_CONTACT_LIST), ArrayList.class);
                    contacts = ContactUtil.sortContactsByMandantPriority(contacts, preferencesController);

                    if (contacts.size() > 0) {
                        mContact = contacts.get(0);
                    }
                } else if (intent.hasExtra(EXTRA_CONTACT)) {
                    mContact = SystemUtil.dynamicDownCast(intent.getExtras().getSerializable(EXTRA_CONTACT), Contact.class);
                    contacts.add(mContact);
                } else if (intent.hasExtra(EXTRA_CONTACT_GUID)) {
                    mContact = mContactController.getContactByGuid(intent.getStringExtra(EXTRA_CONTACT_GUID));
                    contacts.add(mContact);
                } else if (contactByGuid != null) {
                    mContact = contactByGuid;
                }

                if ((mContact == null) && (savedInstanceState != null) && (savedInstanceState.getString(CONTACT_GUID) != null)) {
                    mContact = mContactController.getContactByGuid(savedInstanceState.getString(CONTACT_GUID));
                    contacts.add(mContact);
                }

                if (mContact == null) {
                    finish();
                    return;
                }

                if (StringUtil.isEqual(mContact.getClassEntryName(), Contact.CLASS_COMPANY_ENTRY) || StringUtil.isEqual(mContact.getClassEntryName(), Contact.CLASS_DOMAIN_ENTRY)) {
                    deleteButton.setVisibility(View.GONE);
                }

                final SingleChatController singleChatController = getSimsMeApplication().getSingleChatController();

                final Chat chatByGuid = singleChatController.getChatByGuid(mContact.getAccountGuid());
                if (chatByGuid == null) {
                    mClearChatButton.setVisibility(View.GONE);
                }

                if (mMode == MODE_NORMAL) {
                    mClearChatButton.setVisibility(View.GONE);
                }

                if (StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, mContact.getClassEntryName())) {
                    setRightActionBarImage(R.drawable.ic_edit_white_24dp, mOnEditClickListener, getResources().getString(R.string.content_description_contact_details_edit_contact), -1);
                }
                mContactController.registerOnContactProfileInfoChangeNotification(this);
                setContactImage();

                final String mandantIdent = mContact.getMandant();
                final Mandant mandant = preferencesController.getMandantFromIdent(mandantIdent);
                if (mandant != null) {
                    boolean isPrivate = StringUtil.isNullOrEmpty(mContact.getClassEntryName()) || StringUtil.isEqual(mContact.getClassEntryName(), Contact.CLASS_PRIVATE_ENTRY);
                    ColorUtil.getInstance().colorizeMandantTextView(getSimsMeApplication(), mandant, tenantTextView, isPrivate);
                } else {
                    tenantTextView.setVisibility(View.GONE);
                }

                if (!mContact.getIsSimsMeContact()) {
                    // if it's not a simsme contact then it makes no sense to show a function that will not work
                    findViewById(R.id.contact_details_silent_till_container).setVisibility(View.GONE);
                }

                // contact updaten
                if (contactDetails != null) {

                    mIsInEditMode = true;
                    setRightActionBarImage(R.drawable.ic_done_white_24dp, mOnEditFinishedClickListener, getResources().getString(R.string.content_description_contact_details_edit_contact), -1);
                    if (contactDetails.containsKey(JsonConstants.EMAIL)) {
                        mContact.setEmail(contactDetails.get(JsonConstants.EMAIL));
                    } else {
                        mContact.setEmail(null);
                    }

                    if (contactDetails.containsKey(JsonConstants.NICKNAME)) {
                        mContact.setNickname(contactDetails.get(JsonConstants.NICKNAME));
                    } else {
                        mContact.setNickname(null);
                    }

                    if (contactDetails.containsKey(JsonConstants.FIRSTNAME)) {
                        mContact.setFirstName(contactDetails.get(JsonConstants.FIRSTNAME));
                    } else {
                        mContact.setFirstName(null);
                    }

                    if (contactDetails.containsKey(JsonConstants.LASTNAME)) {
                        mContact.setLastName(contactDetails.get(JsonConstants.LASTNAME));
                    } else {
                        mContact.setLastName(null);
                    }

                    if (contactDetails.containsKey(JsonConstants.PHONE)) {
                        mContact.setPhoneNumber(contactDetails.get(JsonConstants.PHONE));
                    } else {
                        mContact.setPhoneNumber(null);
                    }
                }

                if (mContact != null) {
                    mContactController.addLastUsedCompanyContact(mContact);
                }
            }
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    private void updateContactDetails()
            throws LocalizedException {
        final String nickName = mContact.getNickname();
        final String status = mContact.getStatusText();
        final String firstName = mContact.getFirstName();
        final String lastName = mContact.getLastName();
        final String phoneNumber = mContact.getPhoneNumber();
        final String email = mContact.getEmail();
        final String departement = mContact.getDepartment();

        if (!StringUtil.isNullOrEmpty(nickName)) {
            mNickNameEditText.setText(nickName);
            mNickNameEditText.setVisibility(View.VISIBLE);
        } else {
            mNickNameLabel.setVisibility(View.GONE);
            mNickNameEditText.setVisibility(View.GONE);
        }

        if (!StringUtil.isNullOrEmpty(status)) {
            mStatusEditText.setText(status);
            mStatusEditText.setVisibility(View.VISIBLE);
        } else {
            mStatusLabel.setVisibility(View.GONE);
            mStatusEditText.setVisibility(View.GONE);
        }

        if (!StringUtil.isNullOrEmpty(firstName)) {
            mFirstNameEditText.setText(firstName);
            mFirstNameEditText.setVisibility(View.VISIBLE);
        } else {
            mFirstNameLabel.setVisibility(View.GONE);
            mFirstNameEditText.setVisibility(View.GONE);
        }

        if (!StringUtil.isNullOrEmpty(lastName)) {
            mLastNameEditText.setText(lastName);
            mLastNameEditText.setVisibility(View.VISIBLE);
        } else {
            mLastNameLabel.setVisibility(View.GONE);
            mLastNameEditText.setVisibility(View.GONE);
        }

        if (!StringUtil.isNullOrEmpty(phoneNumber)) {
            mMobileNumberEditText.setText(phoneNumber);
            mMobileNumberLabel.setVisibility(View.VISIBLE);
            mMobileNumberEditText.setVisibility(View.VISIBLE);

            String countryCode = PhoneNumberUtil.getCountryCodeForPhoneNumber(phoneNumber);
            if (countryCode != null && !countryCode.isEmpty())
                mMobileNumberEditText.setLinkTextColor(ColorUtil.getInstance().getAppAccentColor(getSimsMeApplication()));

            Linkify.addLinks(mMobileNumberEditText, Linkify.PHONE_NUMBERS);

        } else {
            mMobileNumberLabel.setVisibility(View.GONE);
            mMobileNumberEditText.setVisibility(View.GONE);
        }

        if (!StringUtil.isNullOrEmpty(email)) {
            mEmailAddressEditText.setText(email);
            mEmailAddressEditText.setVisibility(View.VISIBLE);
            if (Patterns.EMAIL_ADDRESS.matcher(email).matches())
                mEmailAddressEditText.setLinkTextColor(ColorUtil.getInstance().getAppAccentColor(getSimsMeApplication()));
            Linkify.addLinks(mEmailAddressEditText, Linkify.EMAIL_ADDRESSES);
        } else {
            mEmailAddressLabel.setVisibility(View.GONE);
            mEmailAddressEditText.setVisibility(View.GONE);
        }

        if (!StringUtil.isNullOrEmpty(departement) && RuntimeConfig.isBAMandant()) {
            mDepartmentEditText.setText(departement);
            mDepartmentLabel.setVisibility(View.VISIBLE);
        } else {
            mDepartmentLabel.setVisibility(View.GONE);
            mDepartmentEditText.setVisibility(View.GONE);
        }
        mSimsmeIdTextView.setText(mContact.getSimsmeId());
    }

    private void enableViews() {
        mFirstNameEditText.setEnabled(true);
        mLastNameEditText.setEnabled(true);
        mMobileNumberEditText.setEnabled(true);


        mEmailAddressEditText.setEnabled(true);
        mDepartmentEditText.setEnabled(true);

        mFirstNameEditText.setVisibility(View.VISIBLE);
        mLastNameEditText.setVisibility(View.VISIBLE);
        mMobileNumberEditText.setVisibility(View.VISIBLE);


        Spannable phoneNumber = (SpannableStringBuilder) mMobileNumberEditText.getText();
        URLSpan[] spans = phoneNumber.getSpans(0, phoneNumber.length(), URLSpan.class);

        for (URLSpan span : spans) {
            phoneNumber.removeSpan(span);
        }
        Spannable email = (SpannableStringBuilder) mEmailAddressEditText.getText();
        URLSpan[] emailSpans = email.getSpans(0, email.length(), URLSpan.class);

        for (URLSpan span : emailSpans) {
            email.removeSpan(span);
        }

        mEmailAddressEditText.setVisibility(View.VISIBLE);
        if (RuntimeConfig.isBAMandant()) {
            mDepartmentEditText.setVisibility(View.VISIBLE);
            mEmailAddressLabel.setVisibility(View.VISIBLE);
            mDepartmentLabel.setVisibility(View.VISIBLE);
        }

        mFirstNameLabel.setVisibility(View.VISIBLE);
        mLastNameLabel.setVisibility(View.VISIBLE);
        mMobileNumberLabel.setVisibility(View.VISIBLE);
        mSimsmeIdLabel.setVisibility(View.GONE);
        mSimsmeIdContainer.setVisibility(View.GONE);

        mSelectImageView.setVisibility(View.VISIBLE);

        if (mMode == MODE_NORMAL) {
            mScanButton.setVisibility(View.GONE);
            mBlockButton.setVisibility(View.GONE);
            mSendButton.setVisibility(View.GONE);
        }
    }

    private void disableViews() {
        mNickNameEditText.setEnabled(false);
        mStatusEditText.setEnabled(false);
        mFirstNameEditText.setEnabled(false);
        mLastNameEditText.setEnabled(false);
        mDepartmentEditText.setEnabled(false);

        mSimsmeIdLabel.setVisibility(View.VISIBLE);
        mSimsmeIdContainer.setVisibility(View.VISIBLE);

        mSelectImageView.setVisibility(View.GONE);

        if (mMode == MODE_NORMAL) {
            mBlockButton.setVisibility(View.VISIBLE);

            try {
                if (mContact.getState() != Contact.STATE_HIGH_TRUST) {
                    mScanButton.setVisibility(View.VISIBLE);
                }
            } catch (LocalizedException e) {
                LogUtil.w(getClass().getSimpleName(), "disableViews: Contact.getState() failed with " + e.getMessage());
            }

            final boolean blocked = mContact.getIsBlocked() != null ? mContact.getIsBlocked() : false;
            if (!blocked) {
                mSendButton.setVisibility(View.VISIBLE);
            }
        }

        mContactContentContainer.requestFocus();

        try {
            // update nutzen, um Sichtbarkeiten korrekt zu setzen
            updateContactDetails();
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_contact_details;
    }

    @Override
    protected void onResumeActivity() {
        try {
            updateContactDetails();

            if (mIsInEditMode) {
                enableViews();
            }

            setTrustedState();

            if(mMode == MODE_CREATE_GINLO_NOW) {
                mMode = MODE_CREATE;
                showGinloNowExplanation();
            }
            if (mMode == MODE_CREATE) {
                setTitle(getResources().getString(R.string.contact_detail_create));
                enableViews();
                setRightActionBarImage(R.drawable.ic_done_white_24dp, mOnEditFinishedClickListener, getResources().getString(R.string.content_description_contact_details_edit_contact), -1);
            } else {
                setTitle(mContact.getName());
                setBlockButtonText();
                setSilentTillTextView();

                if (mContact.getSilentTill() != 0) {
                    mRefreshTimer = new Timer();

                    final TimerTask refreshTask = new TimerTask() {
                        @Override
                        public void run() {
                            new Handler(getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        setSilentTillTextView();
                                    } catch (final LocalizedException le) {
                                        LogUtil.w(TAG, le.getMessage(), le);
                                    }
                                }
                            });
                        }
                    };
                    mRefreshTimer.scheduleAtFixedRate(refreshTask, 0, 5000);
                }
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    private void setSilentTillTextView()
            throws LocalizedException {
        final TextView silentTillTextView = findViewById(R.id.contact_details_silent_till_textview);
        final long now = new Date().getTime();
        final long silentTill = mContact.getSilentTill();
        DateUtil.fillSilentTillTextView(silentTillTextView,
                now,
                silentTill,
                getString(R.string.chat_mute_remaining_short),
                getString(R.string.chat_mute_infinite),
                getString(R.string.chat_mute_off)
        );

        // stop
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
        if (mContactController != null) {
            mContactController.removeOnLoadContactsListener(onLoadContactsListener);
        }
        if (mRefreshTimer != null) {
            mRefreshTimer.cancel();
            mRefreshTimer.purge();
            mRefreshTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (mContactController != null) {
            mContactController.unregisterOnContactProfileInfoChangeNotification(this);
        }

        super.onDestroy();
    }

    // BUG 36403 contact wird nicht refreshed
    @Override
    public void onBackPressed() {
        if (mBottomSheetOpen) {
            closeBottomSheet(null);
        } else if (mIsInEditMode) {
            finishEditing(false);
        } else {
            super.onBackPressed();
            finish();
        }
    }

    // BUG 36403 contact wird nicht refreshed
    @Override
    public void onBackArrowPressed(View unused) {
        this.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(final Bundle bundle) {
        if (mContact != null) {
            bundle.putString(CONTACT_GUID, mContact.getAccountGuid());
            bundle.putInt(EXTRA_MODE, mMode);
        }

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onContactProfilInfoHasChanged(final String contactGuid) {
        if (mContact != null && StringUtil.isEqual(contactGuid, mContact.getAccountGuid())) {
            try {
                updateContactDetails();// listenelement refreshen
            } catch (final LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }
        }
    }

    @Override
    public void onContactProfilImageHasChanged(final String contactguid) {
        if (mContact != null && StringUtil.isEqual(contactguid, mContact.getAccountGuid())) {
            try {

                setContactImage();
            } catch (final LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }
        }
    }

    public void handleChoosePictureClick(final View view) {
        if (!mIsInEditMode) {
            return;
        }

        if (!mBottomSheetMoving) {
            openBottomSheet(R.layout.dialog_choose_picture_layout, R.id.contact_detail_activity_fragment_container);

            final View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                KeyboardUtil.toggleSoftInputKeyboard(ContactDetailActivity.this, currentFocus, false);
            }
        }
    }

    public void handleTakePictureClick(final View view) {
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera,
                new PermissionUtil.PermissionResultCallback() {
                    @Override
                    public void permissionResult(final int permission,
                                                 final boolean permissionGranted) {
                        if ((permission == PermissionUtil.PERMISSION_FOR_CAMERA) && permissionGranted) {
                            closeBottomSheet(new OnBottomSheetClosedListener() {
                                @Override
                                public void onBottomSheetClosed(final boolean bottomSheetWasOpen) {
                                    final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                                    if (intent.resolveActivity(getPackageManager()) != null) {
                                        try {
                                            final FileUtil fu = new FileUtil(getSimsMeApplication());
                                            mTakenPhotoFile = fu.createTmpImageFileAddInIntent(intent);
                                            router.startExternalActivityForResult(intent, TAKE_PICTURE_RESULT_CODE);
                                        } catch (final LocalizedException e) {
                                            LogUtil.w(TAG, e.getMessage(), e);
                                        }
                                    }
                                }
                            });
                        }
                    }
                });
    }

    public void handleTakeFromGalleryClick(final View view) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    new PermissionUtil.PermissionResultCallback() {
                        @Override
                        public void permissionResult(final int permission,
                                                     final boolean permissionGranted) {
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
                                        deleteProfileImage = true;
                                        mProfileImageView.setImageDrawable(ResourcesCompat.getDrawable(getSimsMeApplication().getResources(), R.drawable.delete, null));
                                    }
                                });
                            }
                        }
                    });
        } else {
            closeBottomSheet(new OnBottomSheetClosedListener() {
                @Override
                public void onBottomSheetClosed(final boolean bottomSheetWasOpen) {
                    deleteProfileImage = true;
                    mProfileImageView.setImageDrawable(ResourcesCompat.getDrawable(getSimsMeApplication().getResources(), R.drawable.delete, null));
                }
            });
        }
    }

    public void showGinloNowExplanation() {
        final String title = getResources().getString(R.string.contact_detail_ginlo_now_title);
        final String text = getResources().getString(R.string.contact_detail_ginlo_now_text);
        final String positiveButton = getResources().getString(R.string.std_ok);
        final String negativeButton = getResources().getString(R.string.std_cancel);

        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
            }
        };

        DialogBuilderUtil.buildResponseDialog(this,
                text,
                title,
                positiveButton,
                null,
                null,
                null).show();
    }

    public void handleDeleteClick(final View view) {
        final String title = getResources().getString(R.string.delete_contact_title);
        final String text = getResources().getString(R.string.delete_contact_text);
        final String positiveButton = getResources().getString(R.string.delete_contact_title);
        final String negativeButton = getResources().getString(R.string.std_cancel);

        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
                mContactController.hideContact(mContact.getAccountGuid());
                finish();
            }
        };

        DialogBuilderUtil.buildResponseDialog(this,
                text,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                null).show();
    }

    public void handleScanClick(final View view) {
        mScanContact = mContact;

        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(final int permission, final boolean permissionGranted) {
                if (permission == PermissionUtil.PERMISSION_FOR_CAMERA && permissionGranted) {
                    final IntentIntegrator intentIntegrator = new IntentIntegrator(ContactDetailActivity.this);
                    final Intent intent = intentIntegrator.createScanIntent();

                    startActivityForResult(intent, SCAN_CONTACT_RESULT_CODE);
                }
            }
        });
    }

    public void handleBlockClick(final View view) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        showIdleDialog(-1);

        final boolean blocked = mContact.getIsBlocked() == null || !mContact.getIsBlocked();

        try {
            mContactController.blockContact(mContact.getAccountGuid(), blocked, true, new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    try {
                        mContact = mContactController.getContactByGuid(mContact.getAccountGuid());
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, e.getMessage(), e);
                    } finally {
                        dismissIdleDialog();
                        setBlockButtonText();
                    }
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    dismissIdleDialog();
                }
            });
        } catch (LocalizedException e) {
            dismissIdleDialog();
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    private void setBlockButtonText() {
        if ((mContact.getIsBlocked() == null) || (!mContact.getIsBlocked())) {
            int mContactState;
            try {
                mContactState = mContact.getState();
                mBlockButton.setText(getResources().getString(R.string.contact_detail_block));

                if (mMode != MODE_NO_SEND_BUTTON && mContactState != Contact.STATE_UNSIMSABLE) {
                    mSendButton.setVisibility(View.VISIBLE);
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }
        } else {
            mBlockButton.setText(getResources().getString(R.string.contact_detail_unblock));
            mSendButton.setVisibility(View.GONE);
        }
    }

    public void handleSendMessageClick(final View view) {
        try {
            if (mContact.getState() != Contact.STATE_HIGH_TRUST) {
                final String entryClassName = mContact.getClassEntryName();
                if (StringUtil.isEqual(entryClassName, Contact.CLASS_DOMAIN_ENTRY) || StringUtil.isEqual(entryClassName, Contact.CLASS_COMPANY_ENTRY)) {
                    mContact.setState(Contact.STATE_HIGH_TRUST);
                }
            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
        final Intent intent = new Intent(this, SingleChatActivity.class);
        intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, mContact.getAccountGuid());
        startActivity(intent);
        finish();
    }

    public void handleCreateClick(final View view) {
        finishEditing(true);
    }

    private void setTrustedState() {
        try {
            final View trustedStateDivider = findViewById(R.id.trust_state_divider);

            switch (mContact.getState()) {
                case Contact.STATE_HIGH_TRUST:
                    mScanButton.setVisibility(View.GONE);
                    trustedStateDivider.setBackgroundColor(ColorUtil.getInstance().getHighColor(getSimsMeApplication()));
                    break;
                case Contact.STATE_MIDDLE_TRUST:
                    trustedStateDivider.setBackgroundColor(ColorUtil.getInstance().getMediumColor(getSimsMeApplication()));
                    break;
                case Contact.STATE_LOW_TRUST:
                    trustedStateDivider.setBackgroundColor(ColorUtil.getInstance().getLowColor(getSimsMeApplication()));
                    break;
                case Contact.STATE_UNSIMSABLE: {
                    trustedStateDivider.setVisibility(View.GONE);
                    mSendButton.setVisibility(View.GONE);
                    mScanButton.setVisibility(View.GONE);
                    break;
                }
                default:
                    LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                    break;
            }

            //Setze den Vertrauensstatus von Company- und Domainkontakten auf sehr Hoch
            if (StringUtil.isEqual(mContact.getClassEntryName(), Contact.CLASS_COMPANY_ENTRY) || StringUtil.isEqual(mContact.getClassEntryName(), Contact.CLASS_DOMAIN_ENTRY)) {
                mScanButton.setVisibility(View.GONE);
                trustedStateDivider.setBackgroundColor(ColorUtil.getInstance().getHighColor(getSimsMeApplication()));
            }
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    private void finishEditing(final boolean save) {
        mIsInEditMode = false;
        try {
            if (save) {
                // alte daten laden, um sie dnan vergleichen zu koennen
                Contact contactByGuid = mContactController.getContactByGuid(mContact.getAccountGuid());
                if (contactByGuid == null) {
                    contactByGuid = mContact;
                }

                if (contactByGuid.getIsHidden() && StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, contactByGuid.getClassEntryName())) {
                    contactByGuid.setIsHidden(false);
                }

                if (contactByGuid.isDeletedHidden() && StringUtil.isEqual(Contact.CLASS_PRIVATE_ENTRY, contactByGuid.getClassEntryName())) {
                    contactByGuid.setIsDeletedHidden(false);
                }

                mContactController.saveContactInformation(contactByGuid,
                        mLastNameEditText.getText().toString(),
                        mFirstNameEditText.getText().toString(),
                        PhoneNumberUtil.normalizePhoneNumberNew(getSimsMeApplication(), "", mMobileNumberEditText.getText().toString()),
                        mEmailAddressEditText.getText().toString(),
                        null,
                        mDepartmentEditText.getText().toString(),
                        null,
                        null,
                        -1,
                        false);

                setTitle(contactByGuid.getName());

                // wenn man Komtakte anlegen kommt, muessen die Sichtbarkeiten geprueft werden
                if (mMode == MODE_CREATE || mMode == MODE_CREATE_GINLO_NOW) {
                    mBlockButton.setVisibility(View.VISIBLE);
                    mCreateButton.setVisibility(View.GONE);
                }

                if (Contact.STATE_HIGH_TRUST != contactByGuid.getState()) {
                    mScanButton.setVisibility(View.VISIBLE);
                }

                if(deleteProfileImage) {
                    mChatImageController.deleteImage(contactByGuid.getAccountGuid());
                } else if (mImageBytes.length != 0) {
                    mChatImageController.saveImage(contactByGuid.getAccountGuid(), mImageBytes);
                    //TODO CHECKSUM
                    contactByGuid.setProfileImageChecksum("TODO");
                    mContactController.insertOrUpdateContact(contactByGuid);
                }

                mContact = contactByGuid;

            } else {
                //falls das bild geaendert wurde, aber jetzt doch nicht geaendert weren soll - zuruekcsetzen
                setContactImage();
                deleteProfileImage = false;
            }
        } catch (LocalizedException e) {
            LogUtil.w(getClass().getSimpleName(), "Save Contact failed with " + e.getMessage());
        }
        setRightActionBarImage(R.drawable.ic_edit_white_24dp, mOnEditClickListener, getResources().getString(R.string.content_description_contact_details_save_contact), -1);
        disableViews();
        setBlockButtonText();
    }

    private void setContactImage()
            throws LocalizedException {
        Bitmap contactImage = null;
        int diameter = (int) getResources().getDimension(R.dimen.contact_details_icon_diameter);

        if (((mContact.getIsSimsMeContact() == null) || !mContact.getIsSimsMeContact())
                && (mContact.getPhotoUri() != null)) {
            contactImage = ContactUtil.loadContactPhotoThumbnail(mContact.getPhotoUri(), diameter,
                    this);
        }

        if (contactImage == null) {
            if (mContact.getAccountGuid() != null) {
                contactImage = mChatImageController.getImageByGuidWithoutCacheing(mContact.getAccountGuid(), diameter,
                        diameter);
            } else {
                contactImage = mContactController.getFallbackImageByContact(getSimsMeApplication(), mContact);
            }
        }

        if (contactImage == null) {
            contactImage = mChatImageController.getImageByGuidWithoutCacheing(AppConstants.GUID_PROFILE_USER, diameter,
                    diameter);
        }

        mProfileImageView.setImageBitmap(contactImage);
    }

    public void onMuteClicked(final View view) {
        final Intent intent = new Intent(ContactDetailActivity.this, MuteChatActivity.class);
        intent.putExtra(MuteChatActivity.EXTRA_CHAT_GUID, mContact.getAccountGuid());
        startActivityForResult(intent, SET_SILENT_TILL_RESULT_CODE);
    }

    public void onGinloIdClicked(final View view) {
        router.shareText(mSimsmeIdTextView.getText().toString());
    }

    public void handleClearChatClick(final View view) {

        final SingleChatController singleChatController = getSimsMeApplication().getSingleChatController();
        final Chat chatByGuid = singleChatController.getChatByGuid(mContact.getAccountGuid());

        if (chatByGuid == null) {
            return;
        }

        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
                showIdleDialog();
                singleChatController.clearChat(chatByGuid, new GenericActionListener<Void>() {
                    @Override
                    public void onSuccess(Void object) {
                        dismissIdleDialog();
                        getSimsMeApplication().getChatOverviewController().chatChanged(null, chatByGuid.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_CLEAR_CHAT);
                        mClearChatButton.setEnabled(false);
                    }

                    @Override
                    public void onFail(String message, String errorIdent) {
                        dismissIdleDialog();
                        DialogBuilderUtil.buildErrorDialog(ContactDetailActivity.this, message).show();
                    }
                });
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
        final String message = getResources().getString(R.string.chat_button_clear_confirm);

        final String negativeButton = getResources().getString(R.string.settings_preferences_changeBackground_cancel);

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

}
