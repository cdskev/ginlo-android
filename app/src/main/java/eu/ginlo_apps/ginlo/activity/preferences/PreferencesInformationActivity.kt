// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.AboutActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.SupportActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.license.LicensesActivity
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.activity_information.information_aboutSimsme
import kotlinx.android.synthetic.main.activity_information.information_companydetails
import kotlinx.android.synthetic.main.activity_information.information_faq
import kotlinx.android.synthetic.main.activity_information.information_license
import kotlinx.android.synthetic.main.activity_information.information_privacy
import kotlinx.android.synthetic.main.activity_information.information_support
import kotlinx.android.synthetic.main.activity_information.information_termsandcondition

class PreferencesInformationActivity : PreferencesBaseActivity() {

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        information_faq.setOnClickListener { onClick(it) }
        information_aboutSimsme.setOnClickListener { onClick(it) }
        information_privacy.setOnClickListener { onClick(it) }
        information_termsandcondition.setOnClickListener { onClick(it) }
        information_license.setOnClickListener { onClick(it) }
        information_companydetails.setOnClickListener { onClick(it) }
        information_support.setOnClickListener { onClick(it) }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_information
    }

    override fun onResumeActivity() {
    }

    private fun onClick(v: View) {
        when (v.id) {
            R.id.information_faq -> getFaqIntent()
            R.id.information_aboutSimsme -> getAboutIntent()
            R.id.information_privacy -> getPrivacyIntent()
            R.id.information_termsandcondition -> getTermsAndConditionsIntent()
            R.id.information_license -> getLicenseIntent()
            R.id.information_companydetails -> getCompanyDetailsIntent()
            R.id.information_support -> getSupportIntent()
            else -> null
        }?.let {
            startActivity(it)
        }
    }

    private fun getSupportIntent(): Intent =
        Intent(this, SupportActivity::class.java)

    private fun getAboutIntent(): Intent =
        Intent(this, AboutActivity::class.java)

    private fun getPrivacyIntent(): Intent =
        getExternalBrowserActivityIntent(R.string.settings_privacy_url)

    private fun getTermsAndConditionsIntent(): Intent =
        getExternalBrowserActivityIntent(R.string.settings_termsandcondition_url)

    private fun getFaqIntent(): Intent =
        getExternalBrowserActivityIntent(resources.getString(R.string.settings_faq_url))

    private fun getLicenseIntent(): Intent =
        Intent(this, LicensesActivity::class.java)

    // This is actually the legal notice (site notice)
    private fun getCompanyDetailsIntent(): Intent =
        getExternalBrowserActivityIntent(R.string.settings_imprint_url)

    private fun getExternalBrowserActivityIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun getExternalBrowserActivityIntent(urlID: Int): Intent {
        val url = resources.getString(urlID)
        return getExternalBrowserActivityIntent(url)
    }
}
