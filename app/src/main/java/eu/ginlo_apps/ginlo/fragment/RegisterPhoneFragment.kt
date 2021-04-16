// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.os.ConfigurationCompat
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.contract.RegisterPhone
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.fragment_login_phone.intro_ident_request_phone_textfield
import kotlinx.android.synthetic.main.fragment_login_phone.intro_ident_request_spinner_country_select
import kotlinx.android.synthetic.main.fragment_login_phone.intro_ident_request_spinner_country_select_label
import kotlinx.android.synthetic.main.fragment_login_phone.intro_ident_request_text_view_country_code
import java.util.Locale

class RegisterPhoneFragment : BaseFragment(), RegisterPhone.View {
    companion object {
        private const val SAVE_INSTANCE_KEY_PHONE = "SIMSME_PHONE_KEY"
        private const val SAVE_INSTANCE_KEY_COUNTRY_INDEX = "SIMSME_COUNTRY_KEY"
    }

    private var phoneNumber: String? = null
    private var selectedCountryIndex: Int = -1
    private lateinit var locale: Locale
    private lateinit var countriesSpinnerAdapter: ArrayAdapter<String>

    // this is leftover from previous implementation.
    // there is no guarantee that the caller set this before the views are created so we are storing it.
    // A bit sloppy, but to fix it requires major restructuring of calling activities so I am leaving it in.
    private var prefilledPhoneNumber: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_login_phone, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CountryCodeUtil.retrieve().also {
            initCountries(it)
            initState(savedInstanceState, it)
            initListeners(it)
        }

        prefilledPhoneNumber?.apply { setPrefilledPhonenumber(this) }
    }

    private fun initListeners(countries: List<Map<String, String>>) {
        intro_ident_request_spinner_country_select.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p2: Long) {
                    selectedCountryIndex = pos
                    intro_ident_request_text_view_country_code.setText(countries[pos][CountryCodeUtil.FULL_DIAL_CODE])
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }

        intro_ident_request_phone_textfield.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                val args = Bundle()
                args.putString(AppConstants.ACTION_ARGS_VALUE, textView.text.toString())
                getCountryCodeText()?.apply {
                    args.putString(AppConstants.ACTION_ARGS_VALUE_2, this)
                }

                handleAction(RegisterPhone.ACTION_PHONE_NUMBER_EDIT_TEXT_DONE, args)
            }
            true
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        locale = ConfigurationCompat.getLocales(resources.configuration).get(0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        phoneNumber?.apply { outState.putString(SAVE_INSTANCE_KEY_PHONE, phoneNumber) }
        selectedCountryIndex.apply { outState.putInt(SAVE_INSTANCE_KEY_COUNTRY_INDEX, selectedCountryIndex) }
    }

    private fun initState(savedInstanceState: Bundle?, countries: List<Map<String, String>>) {
        if (savedInstanceState != null) {
            val previousCountryIndex = savedInstanceState.getInt(SAVE_INSTANCE_KEY_COUNTRY_INDEX)
            if (previousCountryIndex > -1) {
                countries[previousCountryIndex].apply {
                    intro_ident_request_text_view_country_code.setText(this[CountryCodeUtil.FULL_DIAL_CODE])
                    intro_ident_request_spinner_country_select.setSelection(previousCountryIndex)
                }
            }
            intro_ident_request_phone_textfield.setText(savedInstanceState.getString(SAVE_INSTANCE_KEY_PHONE))
        } else {
            setCurrentCountryCode(countries)
        }
    }

    private fun initCountries(countries: List<Map<String, String>>) {
        countriesSpinnerAdapter = ArrayAdapter(
            context!!,
            android.R.layout.simple_spinner_item
        )
        countries.forEach { countriesSpinnerAdapter.add(it[CountryCodeUtil.NAME]) }
        countriesSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        intro_ident_request_spinner_country_select.adapter = countriesSpinnerAdapter
    }

    private fun setCurrentCountryCode(countries: List<Map<String, String>>) {
        // when test country code enabled select that one by default first
        if (RuntimeConfig.isTestCountryEnabled()) {
            intro_ident_request_spinner_country_select.setSelection(
                getIndexOf(CountryCodeUtil.TEST_COUNTRY_CODE, countries)
            )
        } else {
            getIndexOf(countryCode = locale.country, countries = countries).let {
                if (it != -1) intro_ident_request_spinner_country_select.setSelection(it)
                else intro_ident_request_spinner_country_select.setSelection(
                    getIndexOf(CountryCodeUtil.GERMANY_COUNTRY_CODE, countries)
                )
            }
        }
    }

    private fun getIndexOf(countryCode: String, countries: List<Map<String, String>>): Int =
        countriesSpinnerAdapter.getPosition(CountryCodeUtil.findCountryNameByCode(countryCode, countries))

    override fun setPrefilledPhonenumber(phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) return

        prefilledPhoneNumber = phoneNumber
        intro_ident_request_spinner_country_select?.visibility = View.GONE
        intro_ident_request_spinner_country_select_label?.visibility = View.GONE
        intro_ident_request_text_view_country_code?.visibility = View.GONE

        if (simsmeApplication.accountController.isDeviceManaged) {
            intro_ident_request_phone_textfield?.isEnabled = false
        }
        intro_ident_request_phone_textfield?.setText(prefilledPhoneNumber)
    }

    override fun getPhoneText(): String? =
        intro_ident_request_phone_textfield?.text?.toString()

    override fun getCountryCodeText(): String? =
        intro_ident_request_text_view_country_code?.text?.toString()
}