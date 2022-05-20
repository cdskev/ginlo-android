// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.theme

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.TypedValue
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil

class CmsThemeContextWrapper(private val base: Context) : ContextWrapper(base) {

    private val resources: Resources
    private val application: Application = base.applicationContext as Application

    init {
        resources = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {

            override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
                with(ScreenDesignUtil.getInstance()) {
                    when (id) {
                        R.color.app_accent -> outValue.data = getAppAccentColor(application)
                        R.color.actionbar_color -> outValue.data = getMainColor(application)
                        R.color.color_primary -> outValue.data = getMainColor(application)
                        R.color.kColorAccent1Contrast -> outValue.data = getMainContrastColor(application)
                        R.color.kColorSecLevelHigh -> outValue.data = getHighColor(application)
                        R.color.kColorSecLevelHighText -> outValue.data = getHighContrastColor(application)
                        R.color.kColorSecLevelMed -> outValue.data = getMediumColor(application)
                        R.color.kColorSecLevelMedText -> outValue.data = getMediumContrastColor(application)
                        R.color.kColorSecLevelLow -> outValue.data = getLowColor(application)
                        R.color.kColorSecLevelLowText -> outValue.data = getLowContrastColor(application)
                        R.color.color_control_activated -> outValue.data = getAppAccentColor(application)
                        else -> super.getValue(id, outValue, resolveRefs)
                    }
                }
            }

            override fun getColor(id: Int): Int {
                with(ScreenDesignUtil.getInstance()) {
                    return when (id) {
                        R.color.app_accent -> getAppAccentColor(application)
                        R.color.actionbar_color -> getMainColor(application)
                        R.color.color_primary -> getMainColor(application)
                        R.color.kColorAccent1Contrast -> getMainContrastColor(application)
                        R.color.kColorSecLevelHigh -> getHighColor(application)
                        R.color.kColorSecLevelHighText -> getHighContrastColor(application)
                        R.color.kColorSecLevelMed -> getMediumColor(application)
                        R.color.kColorSecLevelMedText -> getMediumContrastColor(application)
                        R.color.kColorSecLevelLow -> getLowColor(application)
                        R.color.kColorSecLevelLowText -> getLowContrastColor(application)
                        R.color.color_control_activated -> getAppAccentColor(application)
                        else -> super.getColor(id)
                    }
                }
            }
        }
    }

    override fun getResources(): Resources {
        return resources
    }
}
