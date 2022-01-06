// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences.information.viewmodels

import androidx.lifecycle.ViewModel
import eu.ginlo_apps.ginlo.log.Logger
import eu.ginlo_apps.ginlo.router.Router
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

class SupportViewModel @Inject constructor(
    private val router: Router,
    private val logger: Logger
) : ViewModel() {

    private val viewModelJobs = Job()

    suspend fun sendLogFile(sendTo: String, subject: String, chooserTitle: String) {
        router.sendLog(sendTo, subject, getLogFile(), chooserTitle)
    }

    private suspend fun getLogFile(): File =
        withContext(Dispatchers.Default + viewModelJobs) {
            logger.getLog() ?: throw FileNotFoundException("Log file not found")
        }

    override fun onCleared() {
        super.onCleared()

        viewModelJobs.cancel()
    }
}