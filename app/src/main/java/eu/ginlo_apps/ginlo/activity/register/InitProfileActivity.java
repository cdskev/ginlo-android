// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.register;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.emoji.widget.EmojiAppCompatEditText;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import javax.inject.Inject;

import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.TextExtensionsKt;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.contracts.UpdateAccountInfoCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.router.RouterConstants;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.FragmentUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.view.RoundedImageView;
import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity;

public class InitProfileActivity
        extends NewBaseActivity
        implements EmojiPickerCallback,
        UpdateAccountInfoCallback,
        ContactController.OnLoadContactsListener {
    private static final String EXTRA_PROFILE_NAME = "InitProfileActivity.extraProfileName";

    private static final String EXTRA_FILE_URI = "InitProfileActivity.extraFileUri";
    @Inject
    public Router router;
    private RoundedImageView mProfilePictureImageView;

    private EmojiAppCompatEditText mNameEditText;
    private AccountController mAccountController;
    private File mTakenPhotoFile;
    private byte[] mImageBytes;
    private CheckBox mAddEmojiNicknameButton;
    private boolean mEmojiFragmentVisible;

    private EmojiPickerFragment mEmojiFragment;

    private FrameLayout mEmojiContainer;
    @Nullable
    private EmojiAppCompatEditText mFirstNameEditText;
    @Nullable
    private EmojiAppCompatEditText mLastNameEditText;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mAccountController = ((SimsMeApplication) getApplication()).getAccountController();
        mProfilePictureImageView = findViewById(R.id.init_profile_mask_image_view_profile_picture);

        mNameEditText = findViewById(R.id.init_profile_edit_text_name);

        EditText identInput = findViewById(R.id.init_profile_ident_input);
        TextView identLabel = findViewById(R.id.init_profile_ident_label);
        String phoneNumber = null;
        String email = null;

        try {
            Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
            phoneNumber = ownContact.getPhoneNumber();
            email = ownContact.getEmail();
        } catch (LocalizedException e) {
            LogUtil.w(InitProfileActivity.class.getSimpleName(), e.getIdentifier(), e);
        }

        if (!StringUtil.isNullOrEmpty(phoneNumber)) {
            identInput.setText(phoneNumber);

            configureNameEditFields(false);
        } else if (!StringUtil.isNullOrEmpty(email)) {
            identInput.setText(email);
            identLabel.setText(R.string.label_email_address);

            configureNameEditFields(true);
        }

        final int slideheigth = (int) getResources().getDimension(R.dimen.profile_slideheight);

        mAnimationSlideIn = new TranslateAnimation(0, 0, slideheigth, 0);
        mAnimationSlideOut = new TranslateAnimation(0, 0, 0, slideheigth);

        mAnimationSlideIn.setDuration(ANIMATION_DURATION);
        mAnimationSlideOut.setDuration(ANIMATION_DURATION);

        mEmojiContainer = findViewById(R.id.init_profile_frame_layout_emoji_container);

        DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

        mAnimationSlideIn.setInterpolator(decelerateInterpolator);
        mAnimationSlideOut.setInterpolator(decelerateInterpolator);

        if (savedInstanceState != null) {
            String fileUri = savedInstanceState.getString(EXTRA_FILE_URI);

            if ((fileUri != null) && (mTakenPhotoFile == null)) {
                mTakenPhotoFile = new File(fileUri);
            }

            String profileName = savedInstanceState.getString(EXTRA_PROFILE_NAME);
            if (!StringUtil.isNullOrEmpty(profileName)) {
                mNameEditText.setText(profileName);
            }
        }

        mAddEmojiNicknameButton = findViewById(R.id.init_profile_check_box_add_emoji_nickname);

        initEmojiButtonListener();
        initEmojiFieldListener();

        final TextView simsmeId = findViewById(R.id.init_profile_simsme_id);
        simsmeId.setText(mAccountController.getAccount().getAccountID());

        try {
            final TextView mandantTextView = findViewById(R.id.init_profile_mandant_label);
            final String mandantIdent = RuntimeConfig.getMandant();
            final Mandant mandant = getSimsMeApplication().getPreferencesController().getMandantFromIdent(mandantIdent);
            if (mandant != null) {
                ColorUtil.getInstance().colorizeMandantTextView(getSimsMeApplication(), mandant, mandantTextView, true);
            } else {
                mandantTextView.setVisibility(View.GONE);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(this.getClass().getName(), e.getMessage(), e);
        }
    }

    private void configureNameEditFields(final boolean isIdentEmail) {
        EmojiAppCompatEditText firstNameInput = findViewById(R.id.init_profile_input_first_name);
        View firstNameLabel = findViewById(R.id.init_profile_label_first_name);
        EmojiAppCompatEditText lastNameInput = findViewById(R.id.init_profile_input_last_name);
        View lastNameLabel = findViewById(R.id.init_profile_label_last_name);

        try {
            if (isIdentEmail && !getSimsMeApplication().getAccountController().isDeviceManaged()) {
                if (firstNameInput != null) {
                    firstNameInput.setVisibility(View.VISIBLE);
                    mFirstNameEditText = firstNameInput;
                }
                if (firstNameLabel != null) {
                    firstNameLabel.setVisibility(View.VISIBLE);
                }

                if (lastNameLabel != null) {
                    lastNameLabel.setVisibility(View.VISIBLE);
                }

                if (lastNameInput != null) {
                    lastNameInput.setVisibility(View.VISIBLE);
                    mLastNameEditText = lastNameInput;
                }
            } else {
                if (firstNameInput != null) {
                    firstNameInput.setVisibility(View.GONE);
                }
                if (firstNameLabel != null) {
                    firstNameLabel.setVisibility(View.GONE);
                }

                if (lastNameLabel != null) {
                    lastNameLabel.setVisibility(View.GONE);
                }

                if (lastNameInput != null) {
                    lastNameInput.setVisibility(View.GONE);
                }
            }
        } catch (final LocalizedException e) {
            LogUtil.w(this.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_init_profile;
    }

    @Override
    protected void onResumeActivity() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        mNameEditText.setEnabled(true);
        mAddEmojiNicknameButton.setEnabled(true);
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode,
                                             int resultCode,
                                             Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case RouterConstants.SELECT_GALLARY_RESULT_CODE: {
                    Uri selectedGallaryItem = returnIntent.getData();
                    FileUtil fileUtil = new FileUtil(this);
                    MimeUtil mimeUtil = new MimeUtil(this);

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
                        LogUtil.w(this.getClass().getName(), e.getMessage(), e);
                        Toast.makeText(this, R.string.chats_addAttachments_some_imports_fails, Toast.LENGTH_LONG).show();
                    }
                    break;
                }
                case RouterConstants.ADJUST_PICTURE_RESULT_CODE: {
                    Bitmap bm = returnIntent.getParcelableExtra(CropImageActivity.RETURN_DATA_AS_BITMAP);

                    if (bm != null) {
                        mImageBytes = BitmapUtil.compress(bm, 100);
                        mProfilePictureImageView.setImageBitmap(bm);
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    public void handleChoosePictureClick(View view) {
        closeEmojis();

        if (!mBottomSheetMoving) {
            openBottomSheet(R.layout.dialog_choose_picture_layout, R.id.init_profile_bottom_sheet_container);

            if (mNameEditText.hasFocus()) {
                KeyboardUtil.toggleSoftInputKeyboard(InitProfileActivity.this, mNameEditText, false);
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
                            closeBottomSheet(new OnBottomSheetClosedListener() {
                                @Override
                                public void onBottomSheetClosed(final boolean bottomSheetWasOpen) {
                                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                                    if (intent.resolveActivity(getPackageManager()) != null) {
                                        try {
                                            FileUtil fu = new FileUtil(getSimsMeApplication());
                                            mTakenPhotoFile = fu.createTmpImageFileAddInIntent(intent);
                                            router.startExternalActivityForResult(intent, TAKE_PICTURE_RESULT_CODE);
                                        } catch (LocalizedException e) {
                                            LogUtil.w(this.getClass().getName(), e.getMessage(), e);
                                        }
                                    }
                                    mAddEmojiNicknameButton.setChecked(false);
                                }
                            });
                        }
                    }
                });
    }

    public void handleTakeFromGalleryClick(View view) {
        if (SystemUtil.hasMarshmallow()) {
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

    public void handleNextClick(View view) {
        final String name = mNameEditText.getText().toString().trim();

        if (StringUtil.isNullOrEmpty(name)) {
            DialogBuilderUtil.buildErrorDialog(this,
                    getResources().getString(R.string.registration_validation_profileNameIsNotValid))
                    .show();
        } else if ((mFirstNameEditText != null && StringUtil.isNullOrEmpty(mFirstNameEditText.getText().toString().trim()))
                || (mLastNameEditText != null && StringUtil.isNullOrEmpty(mLastNameEditText.getText().toString().trim()))) {
            DialogBuilderUtil.buildErrorDialog(this,
                    getResources().getString(R.string.register_email_address_alert_name_empty))
                    .show();
        } else {
            String statusText = getString(R.string.settings_statusWorker_firstMessage);
            String firstName = null;
            String lastName = null;

            if (mFirstNameEditText != null) {
                firstName = mFirstNameEditText.getText().toString().trim();
            }

            if (mLastNameEditText != null) {
                lastName = mLastNameEditText.getText().toString().trim();
            }

            mAccountController.updateAccountInfo(mNameEditText.getText().toString(),
                    statusText,
                    mImageBytes,
                    lastName,
                    firstName,
                    null,
                    null,
                    null,
                    true,
                    this);

            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

            if (keyboard != null && getCurrentFocus() != null) {
                keyboard.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }

            showIdleDialog(-1);
        }
    }

    private void closeEmojis() {
        if (mEmojiFragmentVisible) {
            mAddEmojiNicknameButton.setChecked(false);
            FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiFragment,
                    R.id.init_profile_frame_layout_emoji_container, false);
            mEmojiFragmentVisible = false;

            mEmojiContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        /* zurueck ist verboten */
        if (mBottomSheetOpen) {
            closeBottomSheet(null);
        }

        closeEmojis();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        if (mTakenPhotoFile != null) {
            bundle.putString(EXTRA_FILE_URI, mTakenPhotoFile.getPath());
        }

        if (mNameEditText != null) {
            String name = mNameEditText.getText().toString().trim();
            if (!StringUtil.isNullOrEmpty(name)) {
                bundle.putString(EXTRA_PROFILE_NAME, name);
            }
        }

        super.onSaveInstanceState(bundle);
    }

    private void initEmojiFieldListener() {
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAddEmojiNicknameButton.setChecked(false);
            }
        };

        OnFocusChangeListener focusChangeListener = new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v,
                                      boolean hasFocus) {
                if (hasFocus) {
                    mAddEmojiNicknameButton.setChecked(false);
                }
            }
        };

        mNameEditText.setOnFocusChangeListener(focusChangeListener);
        mNameEditText.setOnClickListener(clickListener);

        if (mFirstNameEditText != null) {
            mFirstNameEditText.setOnFocusChangeListener(focusChangeListener);
            mFirstNameEditText.setOnClickListener(clickListener);
        }

        if (mLastNameEditText != null) {
            mLastNameEditText.setOnFocusChangeListener(focusChangeListener);
            mLastNameEditText.setOnClickListener(clickListener);
        }
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

                                KeyboardUtil.toggleSoftInputKeyboard(InitProfileActivity.this, mNameEditText, false);

                                mEmojiFragment = new EmojiPickerFragment();
                                FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiFragment,
                                        R.id.init_profile_frame_layout_emoji_container, true);
                                mEmojiFragmentVisible = true;
                                mEmojiContainer.setVisibility(View.VISIBLE);
                            }
                        };
                        handler.postDelayed(runnable, 100);
                    }
                } else {
                    closeEmojis();
                }

                mNameEditText.setEnabled(false);
                mAddEmojiNicknameButton.setEnabled(false);

                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    public void run() {
                        mNameEditText.setEnabled(true);
                        mAddEmojiNicknameButton.setEnabled(true);
                    }
                };
                handler.postDelayed(runnable, 500);
            }
        };

        mAddEmojiNicknameButton.setOnCheckedChangeListener(listener);
    }

    @Override
    public void onEmojiSelected(@NotNull String unicode) {
        TextExtensionsKt.appendText(mNameEditText, unicode);
    }

    @Override
    public void onBackSpaceSelected() {
        TextExtensionsKt.backspace(mNameEditText);
    }

    @Override
    public void onPauseActivity() {
        super.onPauseActivity();
        if (mEmojiFragmentVisible) {
            mAddEmojiNicknameButton.setChecked(false);
            FragmentUtil.toggleFragment(getSupportFragmentManager(), mEmojiFragment,
                    R.id.init_profile_frame_layout_emoji_container, false);
            mEmojiFragmentVisible = false;
        }
    }

    @Override
    public void updateAccountInfoFinished() {
        this.dismissIdleDialog();

        mAccountController.getAccount().setState(Account.ACCOUNT_STATE_FULL);
        mAccountController.updateAccoutDao();

        DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Start Kontaktsync
                startContactSync();
            }
        };

        DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Keine Kontaktesync
                startNextActivity();
            }
        };

        DialogBuilderUtil.buildResponseDialog(this,
                getString(R.string.sync_phonebook_contacts_request),
                null,
                getString(R.string.general_yes),
                getString(R.string.general_no),
                positiveListener,
                negativeListener
        ).show();
    }

    private void startContactSync() {
        requestPermission(PermissionUtil.PERMISSION_FOR_READ_CONTACTS, R.string.permission_rationale_contacts, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(int permission, boolean permissionGranted) {
                boolean hasPerm = permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS && permissionGranted;
                if (hasPerm) {
                    //setOverlay(false);
                    showIdleDialog(R.string.backup_restore_progress_sync_contacts);
                    final ContactController contactController = getSimsMeApplication().getContactController();
                    contactController.syncContacts(InitProfileActivity.this, false, true);
                } else {
                    startNextActivity();
                }
            }
        });
    }

    void startNextActivity() {
        try {
            final Class<?> classForNextIntent = RuntimeConfig.getClassUtil().getStartActivityClass(getSimsMeApplication());

            Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getLoginActivityClass());

            intent.putExtra(LoginActivity.EXTRA_NEXT_ACTIVITY, classForNextIntent.getName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void updateAccountInfoFailed(String error) {
        this.dismissIdleDialog();
        if (error != null) {
            Toast.makeText(this, R.string.settings_profile_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoadContactsComplete() {
        getSimsMeApplication().getContactController().removeOnLoadContactsListener(this);
        dismissIdleDialog();
        startNextActivity();
    }

    @Override
    public void onLoadContactsCanceled() {
        getSimsMeApplication().getContactController().removeOnLoadContactsListener(this);
        dismissIdleDialog();
    }

    @Override
    public void onLoadContactsError(String message) {
        getSimsMeApplication().getContactController().removeOnLoadContactsListener(this);
        dismissIdleDialog();
        String errorMsg = getResources().getString(R.string.backup_restore_process_failed_contact_sync_failed);
        if (!StringUtil.isNullOrEmpty(message)) {
            errorMsg = errorMsg + "\n\n" + message;
        }
        DialogBuilderUtil.buildErrorDialog(this, errorMsg).show();
    }
}
