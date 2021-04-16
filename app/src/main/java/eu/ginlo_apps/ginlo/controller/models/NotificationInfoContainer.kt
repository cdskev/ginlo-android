// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.models

import android.graphics.Bitmap

class NotificationInfoContainer(
    val senderGuid: String,
    val image: Bitmap?,
    val name: String?,
    var lastMessage: String?,
    val shortLinkText: String?
)