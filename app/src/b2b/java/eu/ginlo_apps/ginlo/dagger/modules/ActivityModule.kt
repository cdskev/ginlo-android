// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.dagger.modules

import dagger.Module
import dagger.android.ContributesAndroidInjector
import eu.ginlo_apps.ginlo.AbsenceActivity
import eu.ginlo_apps.ginlo.BusinessTrialActivity
import eu.ginlo_apps.ginlo.ChatsOverviewActivityBusiness
import eu.ginlo_apps.ginlo.CompanyContactDetailActivity
import eu.ginlo_apps.ginlo.ContactsActivityBusiness
import eu.ginlo_apps.ginlo.EnterEmailActivationCodeActivity
import eu.ginlo_apps.ginlo.LoginActivityBusiness
import eu.ginlo_apps.ginlo.RecoverPasswordActivityBusiness
import eu.ginlo_apps.ginlo.RegisterEmailActivity
import eu.ginlo_apps.ginlo.RestoreBackupActivityBusiness
import eu.ginlo_apps.ginlo.activity.register.EnterLicenceCodeActivity
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivityBusiness
import eu.ginlo_apps.ginlo.activity.register.InitProfileActivityBusiness
import eu.ginlo_apps.ginlo.activity.register.MdmRegisterActivity
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivityBusiness

@Suppress("unused")
@Module
abstract class ActivityModule : ActivityModuleBase() {
    @ContributesAndroidInjector
    abstract fun contributeEnterEmailActivationCodeActivity(): EnterEmailActivationCodeActivity

    @ContributesAndroidInjector
    abstract fun contributeRestoreBackupActivityBusiness(): RestoreBackupActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeIdentConfirmActivityBusiness(): IdentConfirmActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeConfirmPhoneActivityBusiness(): ConfirmPhoneActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeEnterLicenceCodeActivity(): EnterLicenceCodeActivity

    @ContributesAndroidInjector
    abstract fun contributePurchaseLicenseActivity(): PurchaseLicenseActivity

    @ContributesAndroidInjector
    abstract fun contributeRecoverPasswordActivityBusiness(): RecoverPasswordActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeLoginActivityBusiness(): LoginActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeChatsOverviewActivityBusiness(): ChatsOverviewActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeInitProfileActivityBusiness(): InitProfileActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeRegisterEmailActivity(): RegisterEmailActivity

    @ContributesAndroidInjector
    abstract fun contributeBusinessTrialActivity(): BusinessTrialActivity

    @ContributesAndroidInjector
    abstract fun contributeContactsActivityBusiness(): ContactsActivityBusiness

    @ContributesAndroidInjector
    abstract fun contributeCompanyContactDetailActivity(): CompanyContactDetailActivity

    @ContributesAndroidInjector
    abstract fun contributeAbsenceActivity(): AbsenceActivity

    @ContributesAndroidInjector
    abstract fun contributeMdmRegisterActivity(): MdmRegisterActivity
}
