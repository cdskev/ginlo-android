// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.contract

interface RegisterPhone {
    companion object {
        const val ACTION_PHONE_NUMBER_EDIT_TEXT_DONE = 222
    }

    interface View {
        fun setPrefilledPhonenumber(phoneNumber: String?)
        fun getPhoneText(): String?
        fun getCountryCodeText(): String?
    }
}