// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.log

import java.io.File

interface Logger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun error(e: Throwable)
    fun getLog(): File?
}