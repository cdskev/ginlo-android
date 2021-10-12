// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.profile

import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.ViewModelProviders
import dagger.android.AndroidInjection
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.dagger.factory.ViewModelFactory
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.FragmentUtil
import eu.ginlo_apps.ginlo.util.KeyboardUtil
import kotlinx.android.synthetic.b2c.activity_profile.main_layout
import kotlinx.android.synthetic.b2c.activity_profile.profile_scroll_view_container
import javax.inject.Inject

class ProfileActivity : ProfileActivityBase() {
    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    override lateinit var viewModel: ProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(ProfileViewModel::class.java)
    }

    override val onEmojiCheckChangeListener: CompoundButton.OnCheckedChangeListener
        //get() = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        get() = CompoundButton.OnCheckedChangeListener { _, _ ->
            if (addEmojiNicknameButton.isChecked ) {
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

                val editText = if (addEmojiNicknameButton.isChecked) nickNameEditText else statusEditText

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

    private fun initEmojiFieldListener() {
        val clickListener = View.OnClickListener {
            addEmojiNicknameButton.isChecked = false
            closeEmojis()
        }

        statusEditText.setOnClickListener(clickListener)
        nickNameEditText.setOnClickListener(clickListener)
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        super.onCreateActivity(savedInstanceState)

        initEmojiFieldListener()
    }

    override fun onResumeActivity() {
        val phoneNumber = simsMeApplication.contactController.ownContact?.phoneNumber

        profileTextViewPhoneNumber.visibility = View.VISIBLE
        if (phoneNumber.isNullOrBlank()) {
            profileTextViewPhoneNumber.setText("")
        } else {
            profileTextViewPhoneNumber.setText(phoneNumber)
        }
    }

    override fun onBackPressed() {
        if (emojiFragmentVisible) {
            addEmojiNicknameButton.isChecked = false
            return
        }
        super.onBackPressed()
    }

    override fun initEmojiButtonListener() {
        addEmojiNicknameButton.setOnCheckedChangeListener(onEmojiCheckChangeListener)
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

    override fun saveData() {
        val name = nickNameText

        if (name.trim { it <= ' ' }.isBlank()) {
            DialogBuilderUtil.buildErrorDialog(
                this,
                resources.getString(R.string.registration_validation_profileNameIsNotValid)
            )
                .show()
            return
        }

        if (mBottomSheetOpen) {
            closeBottomSheet(null)
        }

        showIdleDialog(-1)

        accountController.updateAccountInfo(
            name,
            statusText,
            imageBytes, null, null, null, null, null,
            false,
            this@ProfileActivity
        )

        invalidateOptionsMenu()
        disableElements()
    }

    override fun rescaleView() {
        val runnable = Runnable {
            val height = main_layout.measuredHeight - emojiContainer.measuredHeight

            when {
                addEmojiNicknameButton.isChecked -> nicknameContainer
                else -> null
            }?.let {
                if (profile_scroll_view_container.scrollY + height < it.bottom || profile_scroll_view_container.scrollY > it.bottom) {
                    profile_scroll_view_container.scrollTo(0, it.bottom - height)
                }
            }
        }
        Handler().postDelayed(runnable, 500)
    }
}
