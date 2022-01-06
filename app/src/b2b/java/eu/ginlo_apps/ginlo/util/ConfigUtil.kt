// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util

object ConfigUtil : ConfigUtilBase() {
    override fun getServerVersionKeys(): Array<String> {
        return SERVER_VERSION_KEYS
    }

    const val SERVER_VERSION_GET_COMPANY = "getCompany"
    const val SERVER_VERSION_GET_COMPANY_LAYOUT = "getCompanyLayout"
    const val SERVER_VERSION_GET_COMPANY_APP_SETTINGS = "getCompanyAppSettings"
    const val SERVER_VERSION_HAS_COMPANY_MANAGEMENT = "hasCompanyManagement"
    const val SERVER_VERSION_LIST_COMPANY_INDEX = "listCompanyIndex"
    const val SERVER_VERSION_GET_CONFIRMED_IDENTITIES = "getConfirmedIdentities"
    const val SERVER_VERSION_GET_PUBLIC_ONLINE_STATE = "getPublicOnlineState"
    const val SERVER_VERSION_GET_MANADANTEN = "getMandanten"
    const val SERVER_VERSION_GET_CONFIGURATION = "getConfiguration"
    const val SERVER_VERSION_GET_CHANNELS = "getChannels"
    const val SERVER_VERSION_GET_SERVICES = "getServices"

    // IF ever the server configuration change, then we have a big problem.
    // see ConcurrentModificationException at PreferenceController.loadServerConfigVersions line 1009 in 2.6 codes
    // Old clients will not be able to handle it.
    private val SERVER_VERSION_KEYS by lazy {
        arrayOf(
            SERVER_VERSION_GET_COMPANY,
            SERVER_VERSION_GET_COMPANY_LAYOUT,
            SERVER_VERSION_GET_MANADANTEN,
            SERVER_VERSION_GET_CONFIGURATION,
            SERVER_VERSION_HAS_COMPANY_MANAGEMENT,
            SERVER_VERSION_GET_COMPANY_APP_SETTINGS,
            SERVER_VERSION_LIST_COMPANY_INDEX,
            SERVER_VERSION_GET_CONFIRMED_IDENTITIES,
            SERVER_VERSION_GET_PUBLIC_ONLINE_STATE
        )
    }
}
