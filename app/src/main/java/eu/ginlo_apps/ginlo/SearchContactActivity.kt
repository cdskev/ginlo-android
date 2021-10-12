// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.os.ConfigurationCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.zxing.integration.android.IntentIntegrator
import dagger.android.AndroidInjection
import eu.ginlo_apps.ginlo.activity.register.IntroBaseActivity
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.fragment.CountryCodeUtil
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.Mandant
import eu.ginlo_apps.ginlo.model.QRCodeModel
import eu.ginlo_apps.ginlo.model.constant.JsonConstants
import eu.ginlo_apps.ginlo.util.*
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import kotlinx.android.synthetic.main.activity_search_contact.*
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.set

class SearchContactActivity : BaseActivity() {

    companion object {
        private const val TAG = "SearchContactActivity"
        private const val TYPE_SPINNER_SIMSMEID = 0
        private const val TYPE_SPINNER_EMAIL = 1
        private const val TYPE_SPINNER_PHONE = 2
        public const val SCAN_CONTACT_RESULT_CODE = 453
    }

    private lateinit var countriesSpinnerAdapter: ArrayAdapter<String>
    private lateinit var locale: Locale
    private var searchType: ContactUtil.SearchType = ContactUtil.SearchType.SIMSME_ID
    private lateinit var tenantList : List<Mandant>
    private var invitationString: String? = ""
    private val ginloNowUtil : GinloNowUtil = GinloNowUtil()

    @Inject
    lateinit var appConnectivity: AppConnectivity

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        val action: String? = intent?.action
        val data: Uri? = intent?.data
        LogUtil.d(TAG, "onCreate: Check for app link call: action=" + action + ", data=" + data.toString())
        val qrm = QRCodeModel.parseQRString(data.toString())
        if(qrm.version == QRCodeModel.TYPE_V3) {
            invitationString = qrm.payload
            // Got data from app link
            // Keep it.
            ginloNowUtil.ginloNowInvitationString = invitationString
            // We directly process an invitation - no need to show activity
            val myView = findViewById<View>(R.id.activity_search_contact)
            myView.visibility = View.GONE
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_search_contact
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        if (isUserRestricted()) {
            finish()
        }

        locale = ConfigurationCompat.getLocales(resources.configuration).get(0)
        tenantList = simsMeApplication.preferencesController.getMandantenList()
        setUpTypeSpinner()
        setUpCountriesSpinner()
        setUpContactScanButton()
        setUpContactCheckButton()
    }

    override fun onNewIntent(intent: Intent?) {
        LogUtil.d(TAG, "onNewIntent: Called!");
        super.onNewIntent(intent)
    }

    override fun onResumeActivity() {

        // Check for pending invitation and process it only once.
        if (invitationString != "" && invitationString != "processed") {
            ginloNowUtil.resetGinloNowInvitation()
            processQRCodePayload(invitationString)
            return
        }

        intent.data?.let { data ->
            val phone = data.getQueryParameter("phone")
            var prefix = data.getQueryParameter("prefix")

            if (!phone.isNullOrEmpty() && !prefix.isNullOrEmpty()) {
                search_contact_edittext_phone_number.setText(phone)
                if (!prefix.startsWith("+")) {
                    prefix = "+$prefix"
                }
                search_contact_edittext_prefix.setText(prefix)

                search_contact_spinner_country_header.visibility = View.GONE
                search_contact_spinner_country.visibility = View.GONE
                search_contact_spinner_country.isEnabled = false
                search_contact_spinner_type.isEnabled = false
            } else {
                val email = data.getQueryParameter("email")
                if (!email.isNullOrEmpty()) {
                    search_contact_spinner_type.setSelection(TYPE_SPINNER_EMAIL)
                    search_contact_edittext_email_id.setText(email)
                    search_contact_spinner_type.isEnabled = false
                } else {
                    val simsMeID = data.getQueryParameter("simsmeid")
                    if (!simsMeID.isNullOrEmpty()) {
                        search_contact_spinner_type.setSelection(TYPE_SPINNER_SIMSMEID)
                        search_contact_edittext_ginlo_id.setText(simsMeID)
                        search_contact_spinner_type.isEnabled = false
                    }
                }
            }
        }
    }

    private fun isUserRestricted(): Boolean {
        try {
            return simsMeApplication.accountController.managementCompanyIsUserRestricted
        } catch (e: LocalizedException) {
            LogUtil.w(TAG, e.message, e)
        }
        return false
    }

    private fun setUpTypeSpinner() {
        val typeAdapter = ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item)
        resources.getTextArray(R.array.account_identifier).map { typeAdapter.add(it) }
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        search_contact_spinner_type.adapter = typeAdapter

        search_contact_spinner_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                LogUtil.d(TAG, "Spinner pos = $pos")
                when (pos) {
                    TYPE_SPINNER_SIMSMEID -> {
                        setPhoneIsVisibleUI(false)
                        setIsEmailVisibleUI(false)
                        setIsIDVisibleUI(true)
                        searchType = ContactUtil.SearchType.SIMSME_ID
                    }
                    TYPE_SPINNER_EMAIL -> {
                        setPhoneIsVisibleUI(false)
                        setIsEmailVisibleUI(true)
                        setIsIDVisibleUI(false)
                        searchType = ContactUtil.SearchType.EMAIL
                    }
                    TYPE_SPINNER_PHONE -> {
                        setPhoneIsVisibleUI(true)
                        setIsEmailVisibleUI(false)
                        setIsIDVisibleUI(false)
                        searchType = ContactUtil.SearchType.PHONE
                    }
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

            private fun setPhoneIsVisibleUI(isVisible: Boolean) {
                search_contact_phone_container.visibility =
                    if (isVisible) {
                        CountryCodeUtil.retrieve().also {
                            setPrefixAccordingToCountrySelection(
                                search_contact_spinner_country.selectedItemPosition,
                                it
                            )
                        }
                        search_contact_edittext_phone_number.setText("")
                        View.VISIBLE
                    } else
                        View.GONE
            }

            private fun setIsEmailVisibleUI(isVisible: Boolean) {
                search_contact_email_id_container.visibility =
                    if (isVisible) {
                        search_contact_edittext_email_id.setText("")
                        View.VISIBLE
                    } else View.GONE
            }

            private fun setIsIDVisibleUI(isVisible: Boolean) {
                search_contact_ginlo_id_container.visibility =
                        if (isVisible) {
                            search_contact_edittext_ginlo_id.setText("")
                            View.VISIBLE
                        } else View.GONE
            }
        }

        search_contact_spinner_type.setSelection(TYPE_SPINNER_SIMSMEID)
    }

    private fun setUpCountriesSpinner() {
        CountryCodeUtil.retrieve().also { countries ->
            initCountries(countries)
            setCurrentCountryCode(countries)

            search_contact_spinner_country.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p2: Long) {
                    setPrefixAccordingToCountrySelection(pos, countries)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        }
    }

    private fun setPrefixAccordingToCountrySelection(pos: Int, countries: List<Map<String, String>>) {
        search_contact_edittext_prefix.setText(countries[pos][CountryCodeUtil.FULL_DIAL_CODE])
    }

    private fun searchAccount(searchText: String, searchType: String) {
        LogUtil.d(TAG, "searchAccount: Search user: $searchText")
        val accountController = simsMeApplication.accountController
        val genericActionListener = createGenericActionListener(searchText, tenantList)
        for (tenant in tenantList) {
            LogUtil.d(TAG, "Found: " + tenant.ident)
            accountController.searchAccount(
                    searchText,
                    searchType,
                    genericActionListener,
                    tenant.salt,
                    tenant.ident
            )
        }
    }

    /**
     * Return from QR scan activity
     */
    override fun onActivityPostLoginResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityPostLoginResult(requestCode, resultCode, data)
        when(requestCode) {
            SCAN_CONTACT_RESULT_CODE -> {
                when(resultCode) {
                    Activity.RESULT_OK -> {
                        val qrCodeString = data?.getStringExtra("SCAN_RESULT")
                        LogUtil.d(TAG, "Got scan result: $qrCodeString")
                        processQRCodePayload(qrCodeString)
                    }
                }
            }
        }
    }

    private fun processQRCodePayload(qrCodeString : String?) {
        if(qrCodeString == null) {
            return
        }

        val qrm = QRCodeModel.parseQRString(qrCodeString)
        if(qrm.version != QRCodeModel.TYPE_V3 && qrm.version != QRCodeModel.TYPE_V2) {
            invitationString = ""
            LogUtil.w(TAG, "processQRCodePayload: Incompatible QR code: " + qrm.payload)
            val intent = Intent(this@SearchContactActivity, NoContactFoundActivity::class.java)
            intent.putExtra(NoContactFoundActivity.SEARCH_TYPE, this@SearchContactActivity.searchType)
            intent.putExtra(NoContactFoundActivity.SEARCH_VALUE, "")
            startActivity(intent)
            return
        }

        if (!appConnectivity.isConnected()) {
            LogUtil.w(TAG, "processQRCodePayload: No Internet connection.")
            Toast.makeText(
                    this,
                    R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            LogUtil.d(TAG, "processQRCodePayload: Initializing search for " + qrm.ginloID)
            invitationString = "processed"
            val searchText = qrm.ginloID
            val jsonSearchType = JsonConstants.SEARCH_TYPE_SIMSME_ID
            searchType = ContactUtil.SearchType.SIMSME_ID
            showIdleDialog()
            searchAccount(searchText, jsonSearchType)
            dismissIdleDialog()

        } catch (e: LocalizedException) {
            LogUtil.w(TAG, "processQRCodePayload: " + e.message, e)
        }
    }

    private fun setUpContactScanButton() {
        search_contact_scan_button.setOnClickListener {
            requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera) { permission, permissionGranted ->
                if (permission == PermissionUtil.PERMISSION_FOR_CAMERA && permissionGranted) {
                    val intentIntegrator = IntentIntegrator(this)
                    val intent = intentIntegrator.createScanIntent()
                    startActivityForResult(intent, SCAN_CONTACT_RESULT_CODE)
                }
            }
        }
    }

    private fun setUpContactCheckButton() {
        search_contact_check_button.setOnClickListener {
            if (!appConnectivity.isConnected()) {
                Toast.makeText(
                    this,
                    R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            try {
                var searchText = ""
                var jsonSearchType = ""
                when (searchType) {
                    ContactUtil.SearchType.PHONE -> {
                        jsonSearchType = JsonConstants.SEARCH_TYPE_PHONE
                        val countryCode = search_contact_edittext_prefix.text.toString()
                        val phoneNumber = search_contact_edittext_phone_number.text.toString()
                        if (countryCode != CountryCodeUtil.TEST_COUNTRY_FULL_DIAL_CODE &&
                            !PhoneNumberUtil.getInstance().isValidNumber(createPhoneNumber(countryCode, phoneNumber))
                        ) {
                            DialogBuilderUtil.buildErrorDialog(this, resources.getString(R.string.service_ERR_0098))
                                .show()
                            return@setOnClickListener
                        }
                        searchText = "$countryCode$phoneNumber"
                    }
                    ContactUtil.SearchType.EMAIL -> {
                        jsonSearchType = JsonConstants.SEARCH_TYPE_EMAIL
                        searchText = search_contact_edittext_email_id.text.toString().toLowerCase()
                        if (searchText.isEmpty()) {
                            DialogBuilderUtil.buildErrorDialog(
                                this,
                                resources.getString(R.string.service_search_contact_empty_mail)
                            ).show()
                            return@setOnClickListener
                        } else if (!StringUtil.isEmailValid(searchText)) {
                            DialogBuilderUtil.buildErrorDialog(
                                this,
                                resources.getString(R.string.register_email_address_alert_email_empty)
                            ).show()
                            return@setOnClickListener
                        }
                    }
                    ContactUtil.SearchType.SIMSME_ID -> {
                        jsonSearchType = JsonConstants.SEARCH_TYPE_SIMSME_ID
                        searchText = search_contact_edittext_ginlo_id.text.toString().toUpperCase()
                        if (searchText.isEmpty()) {
                            DialogBuilderUtil.buildErrorDialog(
                                this,
                                resources.getString(R.string.service_search_contact_empty_simsmeid)
                            ).show()
                            return@setOnClickListener
                        }
                    }
                }

                showIdleDialog()
                searchAccount(searchText, jsonSearchType)
                dismissIdleDialog()

            } catch (e: LocalizedException) {
                LogUtil.w(this@SearchContactActivity.javaClass.simpleName, e.message, e)
            }
        }
    }

    private fun createGenericActionListener(
        searchText: String,
        mandantenList: List<Mandant>
    ): GenericActionListener<Contact> {

        val ownAccountGuid = simsMeApplication.accountController.account.accountGuid
        val foundContacts = HashMap<String, Contact>()

        return object : GenericActionListener<Contact> {
            private var mandantNumber = mandantenList.size
            private fun checkResults() {
                if (mandantNumber <= 0) {
                    dismissIdleDialog()

                    val size = foundContacts.size
                    val contactDetails = HashMap<String, String>()
                    if (searchType == ContactUtil.SearchType.PHONE) {
                        contactDetails[JsonConstants.PHONE] = searchText
                    } else if (searchType == ContactUtil.SearchType.EMAIL) {
                        contactDetails[JsonConstants.EMAIL] = searchText
                    }

                    LogUtil.d(TAG, "GenericActionListener: Enter with foundContacts.size = $size")

                    when (size) {
                        0 -> {
                            val intent = Intent(this@SearchContactActivity, NoContactFoundActivity::class.java)
                            intent.putExtra(NoContactFoundActivity.SEARCH_TYPE, this@SearchContactActivity.searchType)
                            intent.putExtra(NoContactFoundActivity.SEARCH_VALUE, searchText)
                            startActivity(intent)
                        }

                        1 -> {
                            val contact = foundContacts.values.toTypedArray()[0]
                            val cc =
                                simsMeApplication.contactController.getCompanyContactWithAccountGuid(contact.accountGuid)
                            val existingContact =
                                simsMeApplication.contactController.getContactByGuid(contact.accountGuid)
                            if (cc != null || existingContact != null) {
                                val nextActivity =
                                    if (existingContact != null) {
                                        ContactDetailActivity::class.java
                                    } else {
                                        RuntimeConfig.getClassUtil().companyContactDetailActivity
                                    }
                                val intent = Intent(this@SearchContactActivity, nextActivity)
                                intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_GUID, contact.accountGuid)
                                startActivity(intent)
                                finish()
                            } else {
                                val intent = Intent(this@SearchContactActivity, ContactDetailActivity::class.java)
                                if(invitationString == "processed") {
                                    intent.putExtra(ContactDetailActivity.EXTRA_MODE, ContactDetailActivity.MODE_CREATE_GINLO_NOW)
                                    invitationString = ""
                                } else {
                                    intent.putExtra(ContactDetailActivity.EXTRA_MODE, ContactDetailActivity.MODE_CREATE)
                                }
                                contactDetails[JsonConstants.GUID] = contact.accountGuid
                                contactDetails[JsonConstants.ACCOUNT_ID] = contact.simsmeId ?: ""
                                contactDetails[JsonConstants.PUBLIC_KEY] = contact.publicKey
                                contactDetails[JsonConstants.MANDANT] = contact.mandant
                                intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_MAP, contactDetails)
                                startActivity(intent)
                                finish()
                            }
                        }
                        else -> {
                            val intent = Intent(this@SearchContactActivity, ContactsActivity::class.java)
                            intent.putExtra(ContactsActivity.EXTRA_MODE, ContactsActivity.MODE_ADD_CONTACT)
                            val list = ArrayList<Contact>(foundContacts.values)
                            intent.putExtra(ContactsActivity.EXTRA_ADD_CONTACTS_LIST, list)
                            intent.putExtra(ContactDetailActivity.EXTRA_CONTACT_MAP, contactDetails)
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }

            override fun onSuccess(tmpContact: Contact) {
                --mandantNumber
                val tmpContactAccountGuid = tmpContact.accountGuid
                if (ownAccountGuid != tmpContactAccountGuid) {
                    foundContacts[tmpContactAccountGuid] = tmpContact
                }
                checkResults()
            }

            override fun onFail(message: String, errorIdent: String) {
                --mandantNumber
                checkResults()
            }
        }
    }

    private fun createPhoneNumber(countryCode: String, phoneNumber: String): Phonenumber.PhoneNumber {
        val scrubbedCountryCode = if (countryCode.startsWith("+")) countryCode.substring(1) else countryCode

        val countryCodeInt = scrubbedCountryCode.toIntOrNull() ?: 0
        val phoneNumberLong = phoneNumber.toLongOrNull() ?: 0L

        return Phonenumber.PhoneNumber().also {
            it.countryCode = countryCodeInt
            it.nationalNumber = phoneNumberLong
        }
    }

    private fun getIndexOf(countryCode: String, countries: List<Map<String, String>>): Int =
        countriesSpinnerAdapter.getPosition(CountryCodeUtil.findCountryNameByCode(countryCode, countries))

    private fun initCountries(countries: List<Map<String, String>>) {
        countriesSpinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item
        )
        countries.map { countriesSpinnerAdapter.add(it[CountryCodeUtil.NAME]) }
        countriesSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        search_contact_spinner_country.adapter = countriesSpinnerAdapter
    }

    private fun setCurrentCountryCode(countries: List<Map<String, String>>) {
        getIndexOf(countryCode = locale.country, countries = countries).let { idx ->
            if (idx != -1) search_contact_spinner_country.setSelection(idx)
            else search_contact_spinner_country.setSelection(
                getIndexOf(CountryCodeUtil.GERMANY_COUNTRY_CODE, countries)
            )
        }
    }
}