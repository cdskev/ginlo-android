// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

import eu.ginlo_apps.ginlo.exception.LocalizedException

interface AsyncBackupKeysCallback {
    fun asyncBackupKeysFinished()
    fun asyncBackupKeysFailed(exception: LocalizedException)
}
