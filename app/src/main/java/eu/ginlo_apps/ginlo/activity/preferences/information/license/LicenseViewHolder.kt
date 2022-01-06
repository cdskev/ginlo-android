// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences.information.license

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.license_item.view.body_license
import kotlinx.android.synthetic.main.license_item.view.title

class LicenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun setLicense(licenseItem: LicenseItem) {
        itemView.title.text = licenseItem.title
        itemView.body_license.text = licenseItem.licenseBody
    }
}