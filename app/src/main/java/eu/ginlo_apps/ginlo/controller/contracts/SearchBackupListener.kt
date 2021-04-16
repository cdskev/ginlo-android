// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

import android.os.Bundle
import java.util.ArrayList

interface SearchBackupListener {
    fun onSearchBackupFinish(foundedBackups: ArrayList<Bundle>)
    fun onSearchBackupFailed(message: String?)
}
