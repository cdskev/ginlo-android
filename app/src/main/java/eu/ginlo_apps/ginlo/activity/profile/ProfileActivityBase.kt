// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.profile

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.*
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.core.content.res.ResourcesCompat
import eu.ginlo_apps.ginlo.*
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.controller.KeyController
import eu.ginlo_apps.ginlo.controller.contracts.UpdateAccountInfoCallback
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.router.Router
import eu.ginlo_apps.ginlo.router.RouterConstants
import eu.ginlo_apps.ginlo.util.*
import eu.ginlo_apps.ginlo.view.ClickableEmojiconEditTextview
import eu.ginlo_apps.ginlo.view.ClickableEmojiconEditTextview.DrawableClickListener
import eu.ginlo_apps.ginlo.view.RoundedImageView
import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

abstract class ProfileActivityBase : NewBaseActivity(), EmojiPickerCallback,
    UpdateAccountInfoCallback, CoroutineScope {

    companion object {
        private val TAG = ProfileActivityBase::class.java.simpleName
        private const val DELETE_ACCOUNT_RESULT_CODE = 500
        private const val EXTRA_FILE_URI = "ProfileActivity.extraFileUri"
        private const val UPDATE_STATUS_RESULT_CODE = 501
    }

    override val coroutineContext = Dispatchers.Main

    private lateinit var simsmeIdLabelTextView: TextView
    private lateinit var simsmeIdTextView: TextView
    private lateinit var qrCodeHeaderTextView: TextView
    private lateinit var qrCodeDescTextView: TextView
    private lateinit var deleteAccountButton: Button
    private lateinit var simsmeIdContainer: View
    private lateinit var selectImageView: View
    private val keyController: KeyController by lazy { simsMeApplication.keyController }
    private var takenPhotoFile: File? = null
    protected var imageBytes: ByteArray? = null
    protected lateinit var account: Account
    protected var ownContact: Contact? = null
    private lateinit var qrCodeImageView: ImageView
    private lateinit var profileImageView: RoundedImageView
    private lateinit var profileContentContainer: View
    private val phoneClickListener = DrawableClickListener { target ->
        if (target == DrawableClickListener.DrawablePosition.RIGHT) {
            startChangePhoneActivity()
        }
    }

    private var deleteProfileImage = false

    private var isEditMode: Boolean = false

    @Inject
    internal lateinit var router: Router

    protected var emojiFragmentVisible: Boolean = false
    protected val accountController: AccountController by lazy { simsMeApplication.accountController }
    protected lateinit var nickNameEditText: EditText
    protected lateinit var statusEditText: ClickableEmojiconEditTextview
    protected lateinit var profileTextViewPhoneNumber: ClickableEmojiconEditTextview
    protected lateinit var nicknameContainer: View
    protected lateinit var addEmojiNicknameButton: CheckBox
    protected var emojiFragment: EmojiPickerFragment? = null
    protected lateinit var emojiContainer: FrameLayout
    protected abstract val onEmojiCheckChangeListener: OnCheckedChangeListener
    protected abstract var viewModel: ProfileViewModel

    protected var nickNameText: String
        get() = nickNameEditText.text.toString()
        set(value) {
            var text = value
            val maxLength = resources.getInteger(R.integer.profile_and_group_name_max_length)
            if (value.length > maxLength) {
                text = value.substring(0, maxLength)
            }

            nickNameEditText.setText(text)
            nickNameEditText.setSelection(text.length)
        }

    protected val statusText: String
        get() = statusEditText.text?.toString() ?: ""

    protected fun canPickAvatar(): Boolean {
        return !mBottomSheetMoving && isEditMode
    }

    override fun onActivityPostLoginResult(
        requestCode: Int,
        resultCode: Int,
        returnIntent: Intent?
    ) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                DELETE_ACCOUNT_RESULT_CODE -> {
                    val intent = Intent(this, DeleteAccountActivity::class.java)
                    intent.putExtra(DeleteAccountActivity.CHECK_ID, true)
                    startActivity(intent)
                }
                RouterConstants.SELECT_GALLERY_RESULT_CODE -> {
                    val selectedGalleryItem = returnIntent?.data
                    val fileUtil = FileUtil(this)

                    if (!MimeUtil.checkImageUriMimetype(application, selectedGalleryItem)) {
                        Toast.makeText(
                            this,
                            R.string.chats_addAttachment_wrong_format_or_error, Toast.LENGTH_LONG
                        )
                            .show()

                        return
                    }

                    try {
                        val selectedItemIntern = fileUtil.copyFileToInternalDir(selectedGalleryItem)
                        if (selectedItemIntern != null) {
                            router.cropImage(selectedItemIntern.toString())
                        }
                    } catch (e: LocalizedException) {
                        Toast.makeText(
                            this,
                            R.string.chats_addAttachments_some_imports_fails,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                TAKE_PICTURE_RESULT_CODE -> {
                    try {
                        val internalUri =
                            FileUtil(this).copyFileToInternalDir(Uri.fromFile(takenPhotoFile))
                        router.cropImage(FileUtil.checkPath(internalUri.toString()))
                    } catch (e: LocalizedException) {
                        LogUtil.e(TAG, e.message, e)
                        Toast.makeText(
                            this,
                            R.string.chats_addAttachments_some_imports_fails,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                RouterConstants.ADJUST_PICTURE_RESULT_CODE -> {
                    val bm = returnIntent?.getParcelableExtra<Bitmap>(CropImageActivity.RETURN_DATA_AS_BITMAP)
                    if (bm != null) {
                        profileImageView.setImageBitmap(bm)
                        imageBytes = ImageUtil.compress(bm, 100)
                    }
                }
            }
        }
    }

    protected abstract fun initEmojiButtonListener()

    protected abstract fun rescaleView()

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuLayout =
            if (isEditMode) R.menu.menu_profile_activity_save else R.menu.menu_profile_activity_edit
        menuInflater.inflate(menuLayout, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (isEditMode)
            menu?.findItem(R.id.profile_activity_menu_apply)?.applyColorFilter(simsMeApplication)
        else
            menu?.findItem(R.id.profile_activity_menu_edit)?.applyColorFilter(simsMeApplication)

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.profile_activity_menu_apply -> {
                saveData()
                isEditMode = false
                true
            }
            R.id.profile_activity_menu_edit -> {
                editData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        try {
            if (savedInstanceState != null) {
                val fileUri = savedInstanceState.getString(EXTRA_FILE_URI)

                if (fileUri != null && takenPhotoFile == null) {
                    takenPhotoFile = File(fileUri)
                }
            }

            emojiFragmentVisible = false
            val userAccount = accountController.account

            if (userAccount == null) {
                finish()
                return
            }
            account = userAccount

            ownContact = simsMeApplication.contactController.ownContact
            if (ownContact == null) {
                finish()
                return
            }

            emojiContainer = findViewById(R.id.profile_frame_layout_emoji_container)
            qrCodeImageView = findViewById(R.id.profile_image_view_qrcode)
            nicknameContainer = findViewById(R.id.profile_nickname_container)
            nickNameEditText = findViewById(R.id.profile_edit_text_nickname)
            statusEditText = findViewById(R.id.profile_edit_text_status)
            profileTextViewPhoneNumber = findViewById(R.id.profile_text_view_phone_number)
            profileImageView = findViewById(R.id.profile_mask_image_view_profile_image)
            selectImageView = findViewById(R.id.profile_select_image_imageview)
            qrCodeHeaderTextView = findViewById(R.id.settingsQrCodeHeader)
            qrCodeDescTextView = findViewById(R.id.settingsQrCodeDesc)
            deleteAccountButton = findViewById(R.id.start_delete_account_btn)
            simsmeIdContainer = findViewById(R.id.profile_simsme_id_container)
            profileContentContainer = findViewById(R.id.profile_content_container)
            simsmeIdTextView = findViewById(R.id.profile_simsme_id)
            simsmeIdTextView.setOnClickListener {
                router.shareText(simsmeIdTextView.text.toString())
            }
            simsmeIdLabelTextView = findViewById(R.id.profile_simsme_id_label)
            addEmojiNicknameButton = findViewById(R.id.profile_check_box_add_emoji_nickname)

            val tenantTextView = findViewById<TextView>(R.id.profile_mandant_label)
            val tenantIdent = RuntimeConfig.getMandant()
            val tenant = simsMeApplication.preferencesController.getMandantFromIdent(tenantIdent)
            if (tenant != null) {
                ScreenDesignUtil.getInstance()
                    .colorizeMandantTextView(simsMeApplication, tenant, tenantTextView, true)
            } else {
                tenantTextView.visibility = View.GONE
            }

            fillViews()

            // This is wrong here
            //initEditStatusListener()
            initEmojiButtonListener()

            val slideHeight = resources.getDimension(R.dimen.profile_slideheight).toInt()

            mAnimationSlideIn = TranslateAnimation(0f, 0f, slideHeight.toFloat(), 0f)
            mAnimationSlideOut = TranslateAnimation(0f, 0f, 0f, slideHeight.toFloat())

            mAnimationSlideIn.duration = ANIMATION_DURATION.toLong()
            mAnimationSlideOut.duration = ANIMATION_DURATION.toLong()

            val decelerateInterpolator = DecelerateInterpolator()

            mAnimationSlideIn.interpolator = decelerateInterpolator
            mAnimationSlideOut.interpolator = decelerateInterpolator

            setQrCode()
            disableElements()
        } catch (e: LocalizedException) {
            if (e.identifier != LocalizedException.KEY_NOT_AVAILABLE) {
                finish()
            }
            LogUtil.w(TAG, e.message, e)
        }
    }

    protected open fun fillViews() {
        nickNameText = ownContact?.nickname.orEmpty()
        setStatusText()

        imageController.fillViewWithProfileImageByGuid(account.accountGuid, profileImageView, ImageUtil.SIZE_PROFILE_BIG, true)

        if (account.accountID.isNullOrBlank()) {
            simsmeIdContainer.visibility = View.GONE
            simsmeIdLabelTextView.visibility = View.GONE
        } else {
            simsmeIdTextView.text = account.accountID
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_profile
    }

    fun handleDeleteAccount(@Suppress("UNUSED_PARAMETER") unused: View) {
        startDeleteAccountRequest()
    }

    /**
     * If chosen, start activity for users to edit and save their status message
     */
    protected open fun handleEditStatusTextClick() {
        val intent = Intent(this, StatusTextActivity::class.java)
        intent.putExtra(StatusTextActivity.EXTRA_CURRENT_STATUS, statusText)
        startActivityForResult(intent, UPDATE_STATUS_RESULT_CODE)
    }

    fun handleTakePictureClick(@Suppress("UNUSED_PARAMETER") unused: View) {
        requestPermission(
            PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera
        ) { permission, permissionGranted ->
            if (permission == PermissionUtil.PERMISSION_FOR_CAMERA && permissionGranted) {
                closeBottomSheet(object : OnBottomSheetClosedListener {
                    override fun onBottomSheetClosed(bottomSheetWasOpen: Boolean) {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

                        if (intent.resolveActivity(packageManager) != null) {
                            try {
                                val fu = FileUtil(simsMeApplication)
                                takenPhotoFile = fu.createTmpImageFileAddInIntent(intent)
                                router.startExternalActivityForResult(
                                    intent,
                                    TAKE_PICTURE_RESULT_CODE
                                )
                            } catch (e: LocalizedException) {
                                LogUtil.w(TAG, e.message, e)
                            }
                        }
                    }
                })
            }
        }
    }

    fun handleTakeFromGalleryClick(@Suppress("UNUSED_PARAMETER") unused: View) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(
                PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                R.string.permission_rationale_read_external_storage
            ) { permission, permissionGranted ->
                if (permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE && permissionGranted) {
                    closeBottomSheet { router.pickImage() }
                }
            }
        } else {
            closeBottomSheet { router.pickImage() }
        }
    }

    fun handleDeleteProfileImageClick(@Suppress("UNUSED_PARAMETER") unused: View) {
        LogUtil.d(TAG, "handleDeleteProfileImageClick: Called from " + this.localClassName)

        if(ownContact?.accountGuid != account.accountGuid) {
            LogUtil.w(TAG, "handleDeleteProfileImageClick: Allowed only for own contact, not for " + account.accountID)
            return
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(
                    PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage
            ) { permission, permissionGranted ->
                if (permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE && permissionGranted) {
                    closeBottomSheet {
                        deleteProfileImage = true
                        profileImageView.setImageDrawable(ResourcesCompat.getDrawable(application.applicationContext.resources, R.drawable.delete, null))
                    }
                }
            }
        } else {
            closeBottomSheet {
                deleteProfileImage = true
                profileImageView.setImageDrawable(ResourcesCompat.getDrawable(application.applicationContext.resources, R.drawable.delete, null))
            }
        }
    }

    override fun onEmojiSelected(unicode: String) {
        val editText = if (addEmojiNicknameButton.isChecked) nickNameEditText else statusEditText
        editText.appendText(unicode)
    }

    override fun onBackSpaceSelected() {
        val editText = if (addEmojiNicknameButton.isChecked) nickNameEditText else statusEditText
        editText.backspace()
    }

    private fun startDeleteAccountRequest() {
        val intent = Intent(this, RuntimeConfig.getClassUtil().loginActivityClass)
        intent.putExtra(
            LoginActivity.EXTRA_MODE,
            LoginActivity.EXTRA_MODE_CHECK_PW
        )
        startActivityForResult(
            intent,
            DELETE_ACCOUNT_RESULT_CODE
        )
    }

    override fun onBackPressed() {
        if (mBottomSheetOpen) {
            closeBottomSheet(null)
            return
        } else if (isEditMode) {
            cancelEdit()
            return
        }
        super.onBackPressed()
    }

    override fun onBackArrowPressed(unused: View) {
        super.onBackPressed()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        if (takenPhotoFile != null) {
            bundle.putString(EXTRA_FILE_URI, takenPhotoFile?.path)
        }

        super.onSaveInstanceState(bundle)
    }

    protected fun closeEmojis() {
        if (emojiFragmentVisible) {
            addEmojiNicknameButton.isChecked = false
            FragmentUtil.toggleFragment(
                supportFragmentManager, emojiFragment,
                R.id.profile_frame_layout_emoji_container, false
            )
            emojiFragmentVisible = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            emojiContainer.layoutParams.height =
                resources.getDimension(R.dimen.emoji_container_size_landscape).toInt()
        } else {
            emojiContainer.layoutParams.height =
                resources.getDimension(R.dimen.emoji_container_size_portrait).toInt()
        }
        rescaleView()
    }

    override fun updateAccountInfoFinished() {
        this.dismissIdleDialog()
        updateProfileImage(deleteProfileImage)
        updateNickName()
        updateStatus()
    }

    override fun updateAccountInfoFailed(error: String?) {
        this.dismissIdleDialog()
        if (error != null) {
            Toast.makeText(this, R.string.settings_profile_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Enable edit option for user status.
     */
    private fun initEditStatusListener(nullify: Boolean) {
        val clickListener = DrawableClickListener { target ->
            if (target == DrawableClickListener.DrawablePosition.RIGHT) {
                handleEditStatusTextClick()
            }
        }

        if (nullify) {
            statusEditText.setDrawableClickListener(null)
        } else {
            statusEditText.setDrawableClickListener(clickListener)
        }
    }

    private fun setQrCode() {
        if (!keyController.allKeyDataReady) {
            LogUtil.w(TAG, "Key controller data keys not ready.")
            return
        }

        val usersPrivateKey = keyController.userKeyPair.private

        launch {
            try {
                val bitmap = viewModel.generateQrCode(
                    account,
                    MetricsUtil.getDisplayMetrics(this@ProfileActivityBase).widthPixels,
                    MetricsUtil.getDisplayMetrics(this@ProfileActivityBase).heightPixels,
                    usersPrivateKey
                ) ?: return@launch

                withContext(Dispatchers.Main) {
                    qrCodeImageView.setImageBitmap(bitmap)
                }
            } catch (e: LocalizedException) {
                LogUtil.e(TAG, e.message, e)
            }
        }
    }

    private fun editData() {
        isEditMode = true
        invalidateOptionsMenu()
        enableElements()
    }

    private fun cancelEdit() {
        isEditMode = false
        invalidateOptionsMenu()
        disableElements()
        try {
            fillViews()
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }
    }

    private fun updateProfileImage(delete: Boolean) {
        if (delete) {
            // Deletion of profile image requested
            imageController.deleteProfileImage(account.accountGuid)
        }

        imageController.fillViewWithProfileImageByGuid(account.accountGuid, profileImageView, ImageUtil.SIZE_PROFILE_BIG, true);
    }

    private fun updateNickName() {
        nickNameText = try {
            ownContact?.nickname.orEmpty()
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            ""
        }
    }

    private fun updateStatus() {
        try {
            setStatusText()
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }
    }

    protected open fun setStatusText() {
        statusEditText.setText(ownContact?.statusText.orEmpty())
    }

    protected abstract fun saveData()

    protected open fun enableElements() {
        try {
            if (!accountController.isDeviceManaged) {
                profileTextViewPhoneNumber.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_keyboard_arrow_right_black_24dp,
                    0
                )
                profileTextViewPhoneNumber.setDrawableClickListener(phoneClickListener)
            }
        } catch (e: LocalizedException) {
            LogUtil.w(TAG, "enableElements()", e)
        }

        statusEditText.setCompoundDrawablesWithIntrinsicBounds(
                0, 0,
                R.drawable.ic_keyboard_arrow_right_black_24dp, 0
        )
        initEditStatusListener(false)
        // Don't allow editing inline, do it within separate activity
        //statusEditText.isEnabled = true

        selectImageView.visibility = View.VISIBLE
        nickNameEditText.isEnabled = true
        addEmojiNicknameButton.visibility = View.VISIBLE
        emojiContainer.visibility = View.VISIBLE
        simsmeIdLabelTextView.visibility = View.GONE
        simsmeIdContainer.visibility = View.GONE
        qrCodeHeaderTextView.visibility = View.GONE
        qrCodeDescTextView.visibility = View.GONE
        qrCodeImageView.visibility = View.GONE
        deleteAccountButton.visibility = View.GONE
    }

    protected open fun disableElements() {
        try {
            if (!simsMeApplication.accountController.isDeviceManaged) {
                profileTextViewPhoneNumber.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                profileTextViewPhoneNumber.setDrawableClickListener(null)
            }
        } catch (e: LocalizedException) {
            LogUtil.w(TAG, "disableElements()", e)
        }

        statusEditText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        initEditStatusListener(true)
        statusEditText.isEnabled = false

        selectImageView.visibility = View.GONE
        nickNameEditText.isEnabled = false
        addEmojiNicknameButton.visibility = View.GONE
        emojiContainer.visibility = View.GONE
        simsmeIdLabelTextView.visibility = View.VISIBLE
        simsmeIdContainer.visibility = View.VISIBLE
        qrCodeHeaderTextView.visibility = View.VISIBLE
        qrCodeDescTextView.visibility = View.VISIBLE
        qrCodeImageView.visibility = View.VISIBLE
        deleteAccountButton.visibility = View.VISIBLE

        closeEmojis()
        profileContentContainer.requestFocus()
    }

    private fun startChangePhoneActivity() {
        try {
            val intent = Intent(this@ProfileActivityBase, ChangePhoneActivity::class.java)
            val pendingPhoneNumber = accountController.pendingPhoneNumber
            val waitConfirm =
                AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM == accountController.pendingPhoneStatus

            if (!pendingPhoneNumber.isNullOrBlank() && waitConfirm) {
                intent.putExtra(ChangePhoneActivity.PREFILLED_PHONENUMBER, pendingPhoneNumber)
            }
            startActivity(intent)
        } catch (e: LocalizedException) {
            LogUtil.w(TAG, e.message, e)
        }
    }

    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == UPDATE_STATUS_RESULT_CODE && resultCode == Activity.RESULT_OK)
        {
            val newStatus = data?.getCharSequenceExtra(StatusTextActivity.EXTRA_UPDATED_STATUS).toString()
            statusEditText.setText(newStatus)
        }
    }

}
