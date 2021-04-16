// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences.information

import android.os.Bundle
import androidx.lifecycle.ViewModelProviders
import dagger.android.AndroidInjection
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.activity.preferences.information.viewmodels.SupportViewModel
import eu.ginlo_apps.ginlo.dagger.factory.ViewModelFactory
import eu.ginlo_apps.ginlo.log.Logger
import eu.ginlo_apps.ginlo.setAsUnderlinedText
import eu.ginlo_apps.ginlo.setThrottledClick
import kotlinx.android.synthetic.main.activity_support.send_log_file_link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class SupportActivity : PreferencesBaseActivity(), CoroutineScope {

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    @Inject
    internal lateinit var logger: Logger

    private lateinit var supportViewModel: SupportViewModel

    override val coroutineContext = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)

        super.onCreate(savedInstanceState)
        supportViewModel = ViewModelProviders.of(this, viewModelFactory).get(SupportViewModel::class.java)

        send_log_file_link.setAsUnderlinedText(getString(R.string.settings_support_logs_send))
        send_log_file_link.setThrottledClick { onSendLogClick() }
    }

    override fun getActivityLayout(): Int = R.layout.activity_support

    override fun onResumeActivity() {
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {}

    private fun onSendLogClick() {
        try {
            launch {
                supportViewModel.sendLogFile(
                    getString(R.string.settings_support_customerCare_email),
                    getString(R.string.settings_support_logs_send_emailSubjectPrivate),
                    getString(R.string.settings_support_logs_send_appChooser)
                )
            }
        } catch (e: Throwable) {
            logger.error(e)
            // TODO: display something to the user
        }
    }
}