// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences.information.license

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.themedInflater

class LicensesAdapter(private val context: Context, private val licenses: List<LicenseItem>) :
    RecyclerView.Adapter<LicenseViewHolder>() {
    private val inflater: LayoutInflater by lazy { LayoutInflater.from(context).themedInflater(context) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LicenseViewHolder {
        return LicenseViewHolder(inflater.inflate(R.layout.license_item, parent, false))
    }

    override fun getItemCount(): Int = licenses.count()

    override fun onBindViewHolder(holder: LicenseViewHolder, position: Int) {
        holder.setLicense(licenses[position])
    }
}