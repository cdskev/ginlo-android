// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.profile

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.ViewModelProviders
import dagger.android.AndroidInjection
import eu.ginlo_apps.ginlo.AbsenceActivity
import eu.ginlo_apps.ginlo.EnterEmailActivationCodeActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.RegisterEmailActivity
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivity
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.dagger.factory.ViewModelFactory
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.BitmapUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DateUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.FragmentUtil
import eu.ginlo_apps.ginlo.util.KeyboardUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import eu.ginlo_apps.ginlo.view.ClickableEmojiconEditTextview
import kotlinx.android.synthetic.b2b.activity_profile.main_layout
import kotlinx.android.synthetic.b2b.activity_profile.profile_button_extend_licence
import kotlinx.android.synthetic.b2b.activity_profile.profile_button_trial_usage
import kotlinx.android.synthetic.b2b.activity_profile.profile_emailAddressState
import kotlinx.android.synthetic.b2b.activity_profile.profile_email_address_edittext
import kotlinx.android.synthetic.b2b.activity_profile.profile_phoneNumberState
import kotlinx.android.synthetic.b2b.activity_profile.profile_scroll_view_container
import kotlinx.android.synthetic.b2b.activity_profile.profile_status_view
import kotlinx.android.synthetic.b2b.activity_profile.profile_text_view_departenent
import kotlinx.android.synthetic.b2b.activity_profile.profile_text_view_first_name
import kotlinx.android.synthetic.b2b.activity_profile.profile_text_view_last_name
import kotlinx.android.synthetic.b2b.activity_profile.profile_top_warning
import kotlinx.android.synthetic.b2b.activity_profile.profile_top_warning_text
import kotlinx.android.synthetic.b2b.activity_profile.profile_trial_container
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ProfileActivity : ProfileActivityBase() {

    private var hasLicense: Boolean = false

    private var trialUsageDate: Date? = null

    private val mEmailClickListener = ClickableEmojiconEditTextview.DrawableClickListener { target ->
        if (target == ClickableEmojiconEditTextview.DrawableClickListener.DrawablePosition.RIGHT) {
            if (accountController.waitingForEmailConfirmation) {
                val intent = Intent(this@ProfileActivity, EnterEmailActivationCodeActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this@ProfileActivity, RegisterEmailActivity::class.java)
                intent.putExtra(
                    RegisterEmailActivity.EXTRA_PREFILLED_FIRST_NAME,
                    profile_text_view_first_name.text.toString()
                )
                intent.putExtra(
                    RegisterEmailActivity.EXTRA_PREFILLED_LAST_NAME,
                    profile_text_view_last_name.text.toString()
                )
                startActivity(intent)
            }
        }
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    override lateinit var viewModel: ProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(ProfileViewModel::class.java)
    }

    override val onEmojiCheckChangeListener: CompoundButton.OnCheckedChangeListener
        get() = CompoundButton.OnCheckedChangeListener { _, _ ->
            if (addEmojiNicknameButton.isChecked) {
                if (!emojiFragmentVisible) {
                    val handler = Handler()
                    val runnable = Runnable {
                        emojiFragment = EmojiPickerFragment()
                        FragmentUtil.toggleFragment(
                            supportFragmentManager, emojiFragment,
                            R.id.profile_frame_layout_emoji_container, true
                        )
                        emojiFragmentVisible = true
                        rescaleView()
                    }
                    handler.postDelayed(runnable, ANIMATION_DURATION.toLong())
                }

                val editText =
                    if (addEmojiNicknameButton.isChecked) nickNameEditText else statusEditText

                KeyboardUtil.toggleSoftInputKeyboard(this@ProfileActivity, editText, false)
                editText.requestFocus()
                editText.setSelection(editText.length())
            } else {
                FragmentUtil.toggleFragment(
                    supportFragmentManager, emojiFragment,
                    R.id.profile_frame_layout_emoji_container, false
                )
                closeEmojis()
            }
        }

    override fun fillViews() {
        super.fillViews()

        val ownContact = simsMeApplication.contactController.ownContact ?: return

        profile_text_view_last_name.setText(ownContact.lastName.orEmpty())
        profile_text_view_first_name.setText(ownContact.firstName.orEmpty())
        profile_text_view_departenent.setText(ownContact.department.orEmpty())
        nickNameText = ownContact.nickname.orEmpty()
        setStatusText()
        profileTextViewPhoneNumber.setText(ownContact.phoneNumber.orEmpty())
    }

    override fun onResumeActivity() {
        try {
            profile_scroll_view_container.clearFocus()

            val ownContact = simsMeApplication.contactController.ownContact ?: return
            this.ownContact = ownContact

            val phoneNumber = ownContact.phoneNumber

            if (phoneNumber.isNullOrBlank()) {
                profile_phoneNumberState.visibility = View.GONE
                profile_phoneNumberState.text = ""
            } else {
                if (AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM == accountController.pendingPhoneStatus) {
                    profile_phoneNumberState.visibility = View.VISIBLE
                    profile_phoneNumberState.setTextColor(resources.getColor(R.color.kColorSecLevelLow))
                    profile_phoneNumberState.text = getString(R.string.profile_info_email_address_waiting_for_confirm)

                    profile_top_warning.visibility = View.VISIBLE
                    profile_top_warning_text.text = resources.getText(R.string.profile_phone_waiting_for_confirmation)
                    profileTextViewPhoneNumber.setText(accountController.pendingPhoneNumber)
                } else {
                    profile_top_warning.visibility = View.GONE
                    profile_phoneNumberState.visibility = View.GONE

                    profileTextViewPhoneNumber.setText(ownContact.phoneNumber)
                }
            }

            BitmapUtil.getConfiguredStateDrawable(simsMeApplication, ownContact.isAbsent, false)?.let {
                profile_status_view.background = it
            }

            refreshEmailAttributes(ownContact)
            setIdentitiesAttributes(ownContact)

            trialUsageDate = accountController.trialUsage

            val colorUtil = ColorUtil.getInstance()
            val accentColor = colorUtil.getAppAccentColor(simsMeApplication)

            hasLicense = account.hasLicence
            if (!hasLicense && trialUsageDate != null) {
                val sdtF = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

                profile_button_trial_usage.text =
                    String.format(resources.getString(R.string.profile_label_valid_until), sdtF.format(trialUsageDate))
                profile_button_extend_licence.background.colorFilter =
                    PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP)
                profile_button_extend_licence.setTextColor(accentColor)
                profile_button_extend_licence.visibility = View.VISIBLE
                profile_trial_container.visibility = View.VISIBLE
            } else {
                profile_button_extend_licence.visibility = View.GONE
                profile_trial_container.visibility = View.GONE
            }

            if (accountController.isDeviceManaged) {
                val mainContrast80Color = colorUtil.getMainContrast80Color(simsMeApplication)

                getDrawable(R.drawable.ic_lock_black_24dp)?.apply {
                    setColorFilter(mainContrast80Color, PorterDuff.Mode.SRC_ATOP)
                }?.let {
                    profile_text_view_first_name.setCompoundDrawablesWithIntrinsicBounds(null, null, it, null)
                    profile_text_view_last_name.setCompoundDrawablesWithIntrinsicBounds(null, null, it, null)
                    profile_text_view_departenent.setCompoundDrawablesWithIntrinsicBounds(null, null, it, null)
                    profileTextViewPhoneNumber.setCompoundDrawablesWithIntrinsicBounds(null, null, it, null)
                    profile_email_address_edittext.setCompoundDrawablesWithIntrinsicBounds(null, null, it, null)
                }
            }
            setStatusText()
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    private fun setIdentitiesAttributes(ownContact: Contact) {
        try {
            var emailAddress = ownContact.email
            val isWaitingForEmailConfirmation = accountController.waitingForEmailConfirmation
            if (isWaitingForEmailConfirmation) {
                emailAddress = accountController.pendingEmailAddress
            }

            if (emailAddress.isNullOrBlank()) {
                profile_emailAddressState.visibility = View.GONE
                profile_email_address_edittext.setText("")
            } else {
                if (isWaitingForEmailConfirmation) {
                    emailAddress = accountController.pendingEmailAddress
                    profile_emailAddressState.visibility = View.VISIBLE
                    profile_emailAddressState.setTextColor(resources.getColor(R.color.kColorSecLevelLow))
                    profile_emailAddressState.text =
                        resources.getString(R.string.profile_info_email_address_waiting_for_confirm)
                    profile_top_warning.visibility = View.VISIBLE
                    profile_top_warning_text.text = resources.getText(R.string.profile_email_waiting_for_confirmation)
                } else {
                    profile_emailAddressState.visibility = View.VISIBLE
                    profile_emailAddressState.setTextColor(resources.getColor(R.color.kColorSecLevelHigh))
                    profile_emailAddressState.text = resources.getString(R.string.profile_info_email_address_confirmed)
                }
                profile_email_address_edittext.setText(emailAddress)
            }

            val isWaitingForPhoneConfirmation =
                AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM == accountController.pendingPhoneStatus

            var phone = simsMeApplication.contactController.ownContact!!.phoneNumber

            if (phone.isNullOrBlank()) {
                profile_phoneNumberState.visibility = View.GONE
                profileTextViewPhoneNumber.setText("")
            } else {
                if (!isWaitingForPhoneConfirmation) {
                    profile_phoneNumberState.visibility = View.VISIBLE
                    profile_phoneNumberState.setTextColor(resources.getColor(R.color.kColorSecLevelHigh))
                    profile_phoneNumberState.text = resources.getString(R.string.profile_info_email_address_confirmed)
                } else {
                    profile_phoneNumberState.visibility = View.VISIBLE
                    profile_phoneNumberState.setTextColor(resources.getColor(R.color.kColorSecLevelLow))
                    profile_phoneNumberState.text =
                        resources.getString(R.string.profile_info_email_address_waiting_for_confirm)
                    phone = accountController.pendingPhoneNumber
                }

                profileTextViewPhoneNumber.setText(phone)
            }
            if (!isWaitingForEmailConfirmation && !isWaitingForPhoneConfirmation) {
                profile_top_warning_text.visibility = View.GONE
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    private fun refreshEmailAttributes(ownContact: Contact) {
        profile_text_view_last_name.setText(ownContact.lastName.orEmpty())
        profile_text_view_first_name.setText(ownContact.firstName.orEmpty())

        val callback = object : GenericActionListener<Void> {
            override fun onSuccess(unused: Void?) {
                val handler = Handler(this@ProfileActivity.mainLooper)
                val runnable = Runnable { setIdentitiesAttributes(ownContact) }
                handler.post(runnable)
            }

            override fun onFail(message: String?, errorIdent: String?) {
            }
        }
        accountController.loadConfirmedIdentitiesConfig(callback, null)
    }

    fun onVerifyEmailClicked(@Suppress("UNUSED_PARAMETER") unused: View) {
        if (accountController.waitingForEmailConfirmation) {
            val intent = Intent(this, EnterEmailActivationCodeActivity::class.java)
            startActivity(intent)
        }
    }

    fun onExtendLicenceButtonClicked(@Suppress("UNUSED_PARAMETER") unused: View) {
        Intent(this, PurchaseLicenseActivity::class.java)
            .apply {
                putExtra(PurchaseLicenseActivity.EXTRA_DONT_FORWARD_TO_OVERVIEW, true)
            }.let {
                startActivity(it)
            }
    }

    override fun onBackPressed() {
        if (emojiFragmentVisible) {
            addEmojiNicknameButton.isChecked = false
            return
        }

        super.onBackPressed()
    }

    fun handleChoosePictureClick(@Suppress("UNUSED_PARAMETER") unused: View) {
        if (!canPickAvatar()) return

        openBottomSheet(
            R.layout.dialog_choose_picture_layout,
            R.id.profile_activity_fragment_container
        )
        addEmojiNicknameButton.isChecked = false
        if (nickNameEditText.hasFocus()) {
            KeyboardUtil.toggleSoftInputKeyboard(this, nickNameEditText, false)
        }
        if (statusEditText.hasFocus()) {
            KeyboardUtil.toggleSoftInputKeyboard(this, statusEditText, false)
        }
    }

    override fun initEmojiButtonListener() {
        addEmojiNicknameButton.setOnCheckedChangeListener(onEmojiCheckChangeListener)
    }

    override fun rescaleView() {
        val handler = Handler()
        val runnable = Runnable {
            val height = main_layout.measuredHeight - emojiContainer.measuredHeight

            if (addEmojiNicknameButton.isChecked) {
                if (profile_scroll_view_container.scrollY + height < nicknameContainer.bottom || profile_scroll_view_container.scrollY > nicknameContainer.bottom) {
                    profile_scroll_view_container.scrollTo(0, nicknameContainer.bottom - height)
                }
            }
        }
        handler.postDelayed(runnable, 500)
    }

    override fun saveData() {
        try {
            val name = nickNameText

            if (name.trim { it <= ' ' }.isBlank()) {
                DialogBuilderUtil.buildErrorDialog(
                    this,
                    resources.getString(R.string.registration_validation_profileNameIsNotValid)
                ).show()
                return
            }
            if (mBottomSheetOpen) {
                closeBottomSheet(null)
            }

            var lastName: String? = null
            var firstName: String? = null
            var department: String? = null

            if (!accountController.isDeviceManaged) {
                lastName = profile_text_view_last_name.text.toString()
                firstName = profile_text_view_first_name.text.toString()
                department = profile_text_view_departenent.text.toString()
            }

            showIdleDialog(-1)

            simsMeApplication.accountController.updateAccountInfo(
                name,
                statusText,
                imageBytes,
                lastName,
                firstName,
                department, null, null,
                false,
                this
            )

            invalidateOptionsMenu()
            disableElements()
        } catch (e: LocalizedException) {
            LogUtil.w(javaClass.simpleName, "save data failed", e)
        }
    }

    override fun enableElements() {
        super.enableElements()
        try {
            if (!accountController.isDeviceManaged) {
                profile_text_view_first_name.isEnabled = true
                profile_text_view_last_name.isEnabled = true
                profile_text_view_departenent.isEnabled = true
                profile_email_address_edittext.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_keyboard_arrow_right_black_24dp,
                    0
                )
                profile_email_address_edittext.setDrawableClickListener(mEmailClickListener)
            }

            if (!hasLicense && trialUsageDate != null) {
                profile_button_extend_licence.visibility = View.GONE
                profile_trial_container.visibility = View.GONE
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    override fun handleEditStatusTextClick() {
        startActivity(Intent(this, AbsenceActivity::class.java))
    }

    override fun disableElements() {
        super.disableElements()
        try {
            if (!accountController.isDeviceManaged) {
                profile_text_view_first_name.isEnabled = false
                profile_text_view_last_name.isEnabled = false
                profile_text_view_departenent.isEnabled = false
                profile_email_address_edittext.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
            if (!hasLicense && trialUsageDate != null) {
                profile_button_extend_licence.visibility = View.VISIBLE
                profile_trial_container.visibility = View.VISIBLE
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    fun onTopWarningClicked(@Suppress("UNUSED_PARAMETER") unused: View) {
        try {
            if (accountController.pendingPhoneStatus == AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM) {
                startConfirmPhoneActivity()
            }

            if (accountController.waitingForEmailConfirmation) {
                startActivity(Intent(this, EnterEmailActivationCodeActivity::class.java))
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    fun onVerifyPhoneClicked(@Suppress("UNUSED_PARAMETER") v: View) {
        try {
            if (AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM == accountController.pendingPhoneStatus) {
                startConfirmPhoneActivity()
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    private fun startConfirmPhoneActivity() {
        try {
            val intent = Intent(this, ConfirmPhoneActivity::class.java)
            val pendingPhoneNumber = accountController.pendingPhoneNumber
            val waitConfirm =
                AccountController.PENDING_PHONE_STATUS_WAIT_CONFIRM == accountController.pendingPhoneStatus

            if (!pendingPhoneNumber.isNullOrBlank() && waitConfirm) {
                intent.putExtra(ChangePhoneActivity.PREFILLED_PHONENUMBER, pendingPhoneNumber)
            }
            startActivity(intent)
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun setStatusText() {

        val ownContact = simsMeApplication.contactController.ownContact
        val isAbsent = ownContact!!.isAbsent

        if (isAbsent) {
            val absentTime = DateUtil.utcWithoutMillisStringToMillis(ownContact.absenceTimeUtcString)
            if (absentTime == 0L) {
                statusEditText.setText(resources.getString(R.string.peferences_absence_absent))
            } else {
                statusEditText.setText(
                    String.format(
                        resources.getString(R.string.peferences_absence_absent_till),
                        DateUtil.getDateAndTimeStringFromMillis(absentTime)
                    )
                )
            }
        } else {
            statusEditText.setText(resources.getString(R.string.peferences_absence_present))
        }
    }
}
