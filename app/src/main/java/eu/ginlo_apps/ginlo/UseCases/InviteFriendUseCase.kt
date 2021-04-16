// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.UseCases

import android.app.Activity
import android.content.Intent
import eu.ginlo_apps.ginlo.R

class InviteFriendUseCase {

    fun execute(currentActivity : Activity)
    {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, currentActivity.getString(R.string.contacts_smsMessageBody))
        sendIntent.type = "text/plain"

        val shareIntent = Intent.createChooser(sendIntent, null)
        currentActivity.startActivity(shareIntent)
    }
}