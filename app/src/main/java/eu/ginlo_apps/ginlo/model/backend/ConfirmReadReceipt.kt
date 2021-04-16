// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend

import com.google.gson.annotations.SerializedName

class ConfirmReadReceipt(
    @SerializedName("InternalMessage")
    val internalMessage: InternalMessage?
) {
    class InternalMessage(
        @SerializedName("data")
        val messageData: Data?,
        @SerializedName("datesend")
        val dateSend: String?,
        @SerializedName("from")
        val from: String?,
        @SerializedName("guid")
        val messageGuid: String?,
        @SerializedName("pushInfo")
        val pushInfo: String?,
        @SerializedName("to")
        val recipient: String?
    ) {
        class Data(
            @SerializedName("confirmRead-V1")
            val confirmRead: List<String?>?
        )
    }

    val isConfirmRead: Boolean
        get() {
            return internalMessage?.messageData?.confirmRead?.isEmpty() == false
        }
}

