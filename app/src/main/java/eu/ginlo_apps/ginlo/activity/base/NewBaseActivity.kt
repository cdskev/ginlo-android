// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.base

import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.R

abstract class NewBaseActivity : BaseActivity() {
    override fun initToolbar() {
        val viewToolbar = findViewById<Toolbar>(R.id.toolbar) ?: return
        toolbar = viewToolbar
        setSupportActionBar(toolbar)

        titleView = toolbar.findViewById<TextView>(R.id.toolbar_title)
        title = title

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }
}