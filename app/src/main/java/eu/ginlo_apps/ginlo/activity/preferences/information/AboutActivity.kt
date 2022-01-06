// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences.information

import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.util.ColorUtil

class AboutActivity : PreferencesBaseActivity() {
    override fun onResumeActivity() {
        findViewById<View>(R.id.about_view_high_trust)?.apply {
            setBackgroundColor(ColorUtil.getInstance().getHighColor(simsMeApplication))
        }
        findViewById<View>(R.id.about_view_medium_trust)?.apply {
            setBackgroundColor(ColorUtil.getInstance().getMediumColor(simsMeApplication))
        }
        findViewById<View>(R.id.about_view_low_trust)?.apply {
            setBackgroundColor(ColorUtil.getInstance().getLowColor(simsMeApplication))
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_about
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {}
}
