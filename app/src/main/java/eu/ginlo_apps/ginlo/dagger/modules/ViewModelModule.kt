// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.dagger.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import eu.ginlo_apps.ginlo.activity.preferences.information.viewmodels.SupportViewModel
import eu.ginlo_apps.ginlo.activity.profile.ProfileViewModel
import eu.ginlo_apps.ginlo.dagger.factory.ViewModelFactory

@Module
internal abstract class ViewModelModule {
    @Binds
    internal abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(SupportViewModel::class)
    protected abstract fun supportViewModel(supportViewModel: SupportViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ProfileViewModel::class)
    protected abstract fun profileViewModel(profileViewModel: ProfileViewModel): ViewModel
}