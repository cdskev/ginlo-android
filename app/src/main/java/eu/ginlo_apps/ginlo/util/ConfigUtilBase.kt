// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util

import eu.ginlo_apps.ginlo.BuildConfig

abstract class ConfigUtilBase {
    abstract fun getServerVersionKeys(): Array<String>

    fun servicesEnabled(): Boolean = BuildConfig.ENABLE_SERVICES

    fun channelsEnabled(): Boolean = BuildConfig.ENABLE_CHANNELS

    fun channelsInviteFriends(): Boolean = BuildConfig.ENABLE_INVITE_FRIENDS

    fun hasMultiDeviceSupport(): Boolean = BuildConfig.MULTI_DEVICE_SUPPORT

    fun syncPrivateIndexToServer(): Boolean = BuildConfig.SYNC_PRIVATE_INDEX_TO_SERVER
}