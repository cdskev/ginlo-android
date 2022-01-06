// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register

import android.content.Intent
import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.activity_show_simsme_id.show_simsmeid_simsmeid

class ShowSimsmeIdActivity : NewBaseActivity() {

    override fun onCreateActivity(savedInstanceState: Bundle?) {}

    override fun getActivityLayout(): Int {
        return R.layout.activity_show_simsme_id
    }

    override fun onResumeActivity() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        show_simsmeid_simsmeid.text = simsMeApplication.accountController.account.accountID
    }

    fun handleNextClick(@Suppress("UNUSED_PARAMETER") view: View?) {
        startActivity(Intent(this@ShowSimsmeIdActivity, RuntimeConfig.getClassUtil().initProfileActivityClass))
    }
}
