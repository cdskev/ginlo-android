// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util

object ConfigUtil : ConfigUtilBase() {
    const val SERVER_VERSION_GET_CHANNELS = "getChannels"
    const val SERVER_VERSION_GET_SERVICES = "getServices"
    const val SERVER_VERSION_GET_CONFIGURATION = "getConfiguration"
    const val SERVER_VERSION_GET_MANADANTEN = "getMandanten"

    override fun getServerVersionKeys(): Array<String> {
        return SERVER_VERSION_KEYS
    }

    private val SERVER_VERSION_KEYS by lazy {
        arrayOf(
            SERVER_VERSION_GET_CHANNELS,
            SERVER_VERSION_GET_SERVICES,
            SERVER_VERSION_GET_MANADANTEN,
            SERVER_VERSION_GET_CONFIGURATION
        )
    }
}
