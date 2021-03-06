// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity
import eu.ginlo_apps.ginlo.controller.AVChatController
import eu.ginlo_apps.ginlo.controller.ContactController
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.CompanyContact
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil
import eu.ginlo_apps.ginlo.util.CompanyContactUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_department
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_emailaddress
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_firstname
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_lastname
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_mobilenumber
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_simsmeid
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_edit_text_simsmeid_label
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contact_details_simsmeid_container
import kotlinx.android.synthetic.b2b.activity_company_contact_details.company_contacts_details_mask_image_view_profile_image
import kotlinx.android.synthetic.b2b.activity_company_contact_details.trust_state_divider

class CompanyContactDetailActivity : BaseActivity() {

    private val TAG : String = CompanyContactDetailActivity::class.simpleName.toString()
    private val contactControllerBusiness: ContactControllerBusiness by lazy { simsMeApplication.contactController as ContactControllerBusiness }

    private lateinit var contactGuid: String

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        try {
            if (!intent.hasExtra(ContactDetailActivity.EXTRA_CONTACT_GUID)) {
                finish()
                return
            }

            trust_state_divider.setBackgroundColor(ContextCompat.getColor(this, R.color.kColorSecLevelHigh))

            if(intent.hasExtra(ContactDetailActivity.EXTRA_CONTACT_GUID)) {
                contactGuid = intent.getStringExtra(ContactDetailActivity.EXTRA_CONTACT_GUID).toString()
            } else {
                finish()
                return
            }

            val contact = contactControllerBusiness.getCompanyContactWithAccountGuid(contactGuid)

            if (contact == null) {
                finish()
                return
            }

            contactControllerBusiness.addLastUsedCompanyContact(contact)

            val companyContactUtils = CompanyContactUtil.getInstance(simsMeApplication)

            val firstName =
                companyContactUtils.getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_FIRSTNAME)
            val lastName = companyContactUtils.getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_LASTNAME)
            val phone = companyContactUtils.getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_PHONE)
            val email = companyContactUtils.getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_EMAIL)
            val department =
                companyContactUtils.getAttributeFromContact(contact, CompanyContact.COMPANY_CONTACT_DEPARTMENT)

            company_contact_details_edit_text_firstname.setText(firstName)
            company_contact_details_edit_text_lastname.setText(lastName)
            company_contact_details_edit_text_mobilenumber.text = phone
            company_contact_details_edit_text_emailaddress.setText(email)
            company_contact_details_edit_text_department.setText(department)
            company_contact_details_edit_text_simsmeid.text = contact.accountId

            if(!phone.isNullOrBlank()) {
                val countryCode = PhoneNumberUtil.getCountryCodeForPhoneNumber(phone)
                if (countryCode != null && !countryCode.isEmpty())
                    company_contact_details_edit_text_mobilenumber.setLinkTextColor(ScreenDesignUtil.getInstance().getAppAccentColor(simsMeApplication))
            }

            if (!email.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches())
                company_contact_details_edit_text_emailaddress.setLinkTextColor(ScreenDesignUtil.getInstance().getMediumColor(simsMeApplication))

            if (contact.accountId.isNullOrBlank()) {
                company_contact_details_edit_text_simsmeid_label.visibility = View.GONE
                company_contact_details_simsmeid_container.visibility = View.GONE
            }

            var title = ""
            if (!firstName.isNullOrBlank()) {
                title = firstName
            }
            if (!lastName.isNullOrBlank()) {
                title = if (title.isBlank()) lastName else "$title $lastName"
            }

            setTitle(title)
            setContactImage(contact)
            setActionBarAVCImageVisibility(View.VISIBLE)
        } catch (e: LocalizedException) {
            LogUtil.w(javaClass.simpleName, "createActivity failed", e)
            finish()
        }
    }

    fun handleSendMessageClick(@Suppress("UNUSED_PARAMETER") view: View) {
        try {
            val contact = contactControllerBusiness.getContactByGuid(contactGuid)
            val intent = Intent(this@CompanyContactDetailActivity, SingleChatActivity::class.java)
            intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, contactGuid)

            val onLoadPublicKeyListener = object : ContactController.OnLoadPublicKeyListener {
                override fun onLoadPublicKeyComplete(contact: Contact) {
                    dismissIdleDialog()
                    startActivity(intent)
                    finish()
                }

                override fun onLoadPublicKeyError(message: String) {
                    dismissIdleDialog()
                    DialogBuilderUtil.buildErrorDialog(this@CompanyContactDetailActivity, message).show()
                }
            }

            if (contact == null || contact.publicKey.isNullOrBlank()) {
                val companyContact = contactControllerBusiness.getCompanyContactWithAccountGuid(contactGuid)

                if (companyContact != null) {
                    val hiddenContact =
                        contactControllerBusiness.createHiddenContactForCompanyContact(companyContact)
                    hiddenContact.state = Contact.STATE_HIGH_TRUST

                    if (hiddenContact.publicKey.isNullOrBlank() || hiddenContact.simsmeId.isNullOrBlank()) {
                        showIdleDialog(R.string.start_chat_with_company_contact_idle_text)
                        contactControllerBusiness.loadPublicKey(hiddenContact, onLoadPublicKeyListener)
                    } else {
                        startActivity(intent)
                        finish()
                    }
                } else {
                    //der Fall kann eigentlich nicht eintreten, da man ja uebe reinen Company-Contact hierher gekommen ist
                    finish()
                }
            } else {
                contact.state = Contact.STATE_HIGH_TRUST
                startActivity(intent)
                finish()
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    private fun setContactImage(contact: CompanyContact) {
        val diameter = resources.getDimension(R.dimen.contact_details_icon_diameter).toInt()

        imageController.fillViewWithProfileImageByGuid(contact.accountGuid,
        company_contacts_details_mask_image_view_profile_image, diameter, true)
    }

    override fun getActivityLayout(): Int =
        R.layout.activity_company_contact_details

    override fun onResumeActivity() {}

    // Called when the user initiates a call by pressing the "call" button
    fun handleAVCAudioClick(view : View) {
        handleAVCMessageClick(AVChatController.CALL_TYPE_AUDIO_ONLY);
    }

    fun handleAVCMessageClick(callType : Int) {
        if(avChatController == null) {
            return
        }

        avChatController.resetAVC()
        avChatController.rollAndSetNewRoomInfo()

        var myName = "John Doe (unknown)"
        val targetContact = contactControllerBusiness.getCompanyContactWithAccountGuid(contactGuid)

        try {
            myName = contactControllerBusiness.ownContact.nameFromNameAttributes + " (" + contactControllerBusiness.ownContact.simsmeId + ")"
        } catch (e: LocalizedException) {
            e.printStackTrace()
        }

        // Send AVC message
        try {
            simsMeApplication.singleChatController.sendAVC(contactGuid,
                    targetContact.publicKey,
                    avChatController.serializedRoomInfo,
                    null,
                    null,
                    false,
                    null)

            avChatController.setMyName(myName)
            avChatController.setConferenceTopic(myName)
            avChatController.setCallType(callType)
            avChatController.startAVCall(this)

        } catch (e: Exception) {
            LogUtil.e(TAG, "handleAVCMessageClick: Failed to send AVC caller message: " + e.message, e);
            return
        }
    }

}
