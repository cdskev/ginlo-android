// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.preferences.base

import android.widget.CompoundButton
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity

abstract class PreferencesBaseActivity: NewBaseActivity() {
    /**
     * wenn ein witch programmatisch gesetzt gesetzt wird, wird der Listener ausgeloest udn die Funktionalitaet getriggert.
     * das wird mit diesem Flag verhindert
     */
    protected var settingsSwitch: Boolean = false

    protected fun setCompoundButtonWithoutTriggeringListener(compoundButton: CompoundButton?, value: Boolean) {
        if (compoundButton != null) {
            settingsSwitch = true
            compoundButton.isChecked = value
            settingsSwitch = false
        }
    }
}