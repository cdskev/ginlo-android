// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences.information.license

import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import kotlinx.android.synthetic.main.activity_licence.recyclerview_third_party_licenses

class LicensesActivity : PreferencesBaseActivity() {
    override fun onCreateActivity(savedInstanceState: Bundle?) {
        recyclerview_third_party_licenses.layoutManager = LinearLayoutManager(this)
        recyclerview_third_party_licenses.adapter = LicensesAdapter(this, getLicenses())
        recyclerview_third_party_licenses.setHasFixedSize(true)
        recyclerview_third_party_licenses.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.HORIZONTAL))
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_licence
    }

    override fun onResumeActivity() {}

    private fun getLicenses(): List<LicenseItem> {
        return mutableListOf(
                LicenseItem(getString(R.string.android_logback), getString(R.string.apache_license_2_0)),
                LicenseItem(getString(R.string.cropimage), getString(R.string.license_cropimage)),
                LicenseItem(getString(R.string.dagger), getString(R.string.apache_license_2_0)),
                LicenseItem(getString(R.string.emojicon), getString(R.string.license_emojicon)),
                LicenseItem(getString(R.string.exoplayer), getString(R.string.apache_license_2_0)),
                LicenseItem(getString(R.string.ezvcard), getString(R.string.license_ezvcard)),
                LicenseItem(getString(R.string.fab_speed_dial), getString(R.string.apache_license_2_0)),
                LicenseItem(getString(R.string.greendao), getString(R.string.license_greendao)),
                LicenseItem(getString(R.string.gson), getString(R.string.license_gson)),
                LicenseItem(getString(R.string.jbrycpt), getString(R.string.license_jbrycpt)),
                LicenseItem(getString(R.string.jitsimeet), getString(R.string.license_jitsimeet)),
                LicenseItem(getString(R.string.kotlinx_coroutines), getString(R.string.license_kotlinx_coroutines)),
                LicenseItem(getString(R.string.material_icons), getString(R.string.apache_license_2_0)),
                LicenseItem(getString(R.string.photoview), getString(R.string.apache_license_2_0)),
                LicenseItem(getString(R.string.shortcut_badge), getString(R.string.license_shortcut_badge)),
                LicenseItem(getString(R.string.slf4j), getString(R.string.license_slf4j)),
                LicenseItem(getString(R.string.sqlcipher), getString(R.string.license_sqlcipher)),
                LicenseItem(getString(R.string.zxing_android_embedded), getString(R.string.license_zxing))
        )
    }
}
