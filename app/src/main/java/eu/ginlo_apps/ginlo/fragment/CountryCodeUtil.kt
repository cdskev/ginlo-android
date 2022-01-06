// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment

import com.google.i18n.phonenumbers.PhoneNumberUtil
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import java.util.Locale

object CountryCodeUtil {
    private const val DIAL_CODE = "dialCode"
    const val NAME = "name"
    const val CODE = "code"
    const val FULL_DIAL_CODE = "fullDialCode"
    const val GERMANY_COUNTRY_CODE = "DE"

    const val TEST_COUNTRY_CODE = "999"
    const val TEST_COUNTRY_FULL_DIAL_CODE = "+999"
    private const val TEST_COUNTRY_NAME = "TEST"
    private const val TEST_CODE = "TEST"

    fun retrieve(): List<Map<String, String>> {
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val supportedRegions: MutableSet<String> = phoneNumberUtil.supportedRegions

        val testCountryCode: List<Map<String, String>> = if (RuntimeConfig.isTestCountryEnabled()) {
            listOf<Map<String, String>>(
                HashMap<String, String>().apply {
                    put(DIAL_CODE, TEST_COUNTRY_CODE)
                    put(FULL_DIAL_CODE, TEST_COUNTRY_FULL_DIAL_CODE)
                    put(NAME, TEST_COUNTRY_NAME)
                    put(CODE, TEST_CODE)
                }
            )
        } else emptyList()

        return testCountryCode + supportedRegions
            .map {
                val dialCode = phoneNumberUtil.getCountryCodeForRegion(it)
                val localizedCountryName = Locale(it, it).getDisplayCountry(Locale.getDefault())
                mapOf(
                    DIAL_CODE to dialCode.toString(),
                    FULL_DIAL_CODE to "+$dialCode",
                    NAME to localizedCountryName,
                    CODE to it
                )
            }.sortedWith(Comparator<Map<String, String>> { o1: Map<String, String>, o2: Map<String, String> ->
                o1.getValue(NAME).compareTo(o2.getValue(NAME))
            })
    }

    private fun findCountryByCode(code: String, countries: List<Map<String, String>>): Map<String, String>? =
        countries.find { it[CountryCodeUtil.CODE] == code }

    fun findCountryNameByCode(code: String, countries: List<Map<String, String>>): String =
        findCountryByCode(code, countries)?.get(CountryCodeUtil.NAME).orEmpty()
}