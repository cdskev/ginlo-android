// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.notobfuscate

import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.ContactsActivity
import eu.ginlo_apps.ginlo.LoginActivity
import eu.ginlo_apps.ginlo.RestoreBackupActivity
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity
import eu.ginlo_apps.ginlo.activity.register.InitProfileActivity
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.util.IDialogHelper
import eu.ginlo_apps.ginlo.util.IManagedConfigUtil

interface IClassUtil {
    val chatOverviewActivityClass: Class<out ChatsOverviewActivity>

    val initProfileActivityClass: Class<out InitProfileActivity>

    val contactsActivityClass: Class<out ContactsActivity>

    val identConfirmActivityClass: Class<out IdentConfirmActivity>

    val loginActivityClass: Class<out LoginActivity>

    val restoreBackupActivityClass: Class<out RestoreBackupActivity>

    val absenceActivityClass: Class<out BaseActivity>

    val companyContactDetailActivity: Class<out BaseActivity>

    fun getActivityAfterIntro(application: SimsMeApplication): Class<*>

    fun getStartActivityClass(application: SimsMeApplication): Class<*>

    fun getManagedConfigUtil(application: SimsMeApplication): IManagedConfigUtil?

    fun getDialogHelper(application: SimsMeApplication): IDialogHelper
}
